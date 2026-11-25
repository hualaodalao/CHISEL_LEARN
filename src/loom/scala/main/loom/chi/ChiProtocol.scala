package loom.chi

import chisel3._
import chisel3.util._

/** Free-list ID pool, reused for TxnID tracking (requester) and DBID data
  * buffer allocation (subordinate). IDs 0..depth-1 start free; `alloc` pops
  * one, `release` returns a specific ID. Allocation blocks when exhausted,
  * which is the DBID-full / outstanding-full backpressure point.
  */
class ChiIdPool(depth: Int, idWidth: Int) extends Module {
  require(depth >= 1, "pool depth must be >= 1")
  require(depth <= (1 << idWidth), "pool depth exceeds ID space")
  private val aw = math.max(1, log2Ceil(depth))
  private val cw = log2Ceil(depth + 1)

  val io = IO(new Bundle {
    val alloc = Input(Bool())
    val release = Input(Bool())
    val releaseId = Input(UInt(idWidth.W))
    val canAlloc = Output(Bool())
    val allocId = Output(UInt(idWidth.W))
    val outstanding = Output(UInt(cw.W))
  })

  private val mem = RegInit(VecInit(Seq.tabulate(depth)(i => i.U(idWidth.W))))
  private val head = RegInit(0.U(aw.W))
  private val tail = RegInit(0.U(aw.W))
  private val freeCnt = RegInit(depth.U(cw.W))

  io.canAlloc := freeCnt =/= 0.U
  io.allocId := mem(head)
  io.outstanding := depth.U(cw.W) - freeCnt

  private val allocFire = io.alloc && io.canAlloc
  private def wrap(x: UInt): UInt = Mux(x >= depth.U, x - depth.U, x)(aw - 1, 0)

  when(allocFire) { head := wrap(head +& 1.U) }
  when(io.release) { mem(tail) := io.releaseId; tail := wrap(tail +& 1.U) }
  freeCnt := freeCnt - allocFire.asUInt + io.release.asUInt

  assert(!(io.alloc && !io.canAlloc), "CHI IdPool: alloc without a free ID")
  assert(!(io.release && freeCnt === depth.U(cw.W)), "CHI IdPool: release when full")
}

/** P-Credit admission control (subordinate/Home side).
  *
  * A request with AllowRetry=1 is accepted while a transaction slot is free,
  * otherwise RetryAck is issued and a grant is owed. When a slot is released
  * and a grant is owed, PCrdGrant is issued so the requester may re-send with
  * AllowRetry=0. `outstanding` tracks occupied slots.
  */
class ChiRetryCtrl(depth: Int) extends Module {
  require(depth >= 1, "retry depth must be >= 1")
  private val cw = log2Ceil(depth + 1)

  val io = IO(new Bundle {
    val reqAllowRetry = Input(Bool())
    val release = Input(Bool())
    val accept = Output(Bool())
    val retryAck = Output(Bool())
    val pcrdGrant = Output(Bool())
    val outstanding = Output(UInt(cw.W))
  })

  private val occ = RegInit(0.U(cw.W))
  private val pend = RegInit(0.U(cw.W))
  private val hasRoom = occ < depth.U(cw.W)

  io.accept := io.reqAllowRetry && hasRoom
  io.retryAck := io.reqAllowRetry && !hasRoom
  io.pcrdGrant := io.release && pend =/= 0.U
  io.outstanding := occ

  occ := occ + io.accept.asUInt - io.release.asUInt
  pend := pend + io.retryAck.asUInt - io.pcrdGrant.asUInt

  assert(!(io.release && occ === 0.U), "CHI RetryCtrl: release while empty")
}