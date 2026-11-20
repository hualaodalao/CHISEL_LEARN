package loom.rtl

import chisel3._
import chisel3.util._

/** Gray-code and pointer helpers shared by the CDC building blocks. */
object LoomCdc {
  def binToGray(b: UInt): UInt = b ^ (b >> 1)

  def grayToBin(g: UInt): UInt = {
    val n = g.getWidth
    val bits = Wire(Vec(n, Bool()))
    bits(n - 1) := g(n - 1)
    for (i <- (n - 2) to 0 by -1) bits(i) := bits(i + 1) ^ g(i)
    bits.asUInt
  }
}

/** Dual-clock FIFO with gray-coded pointers.
  *
  * The write side runs on the implicit clock/reset; the read side runs on
  * `io.dstClk` / `io.dstReset`. The storage is a small register file with a
  * combinational read (a distributed-RAM spike); a dual-port SRAM macro can
  * replace it without changing the interface.
  */
class LmAsyncFifo(dataWidth: Int, depth: Int) extends Module {
  require(dataWidth >= 1, "dataWidth must be >= 1")
  require(depth >= 2 && (depth & (depth - 1)) == 0, "depth must be a power of two >= 2")

  private val aw = log2Ceil(depth)
  private val pw = aw + 1

  val io = IO(new Bundle {
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
    val push = Flipped(Decoupled(UInt(dataWidth.W)))
    val pop = Decoupled(UInt(dataWidth.W))
  })

  private val dstClock = io.dstClk.asClock

  private val mem = RegInit(VecInit(Seq.fill(depth)(0.U(dataWidth.W))))

  // Write domain.
  private val wptrBin = RegInit(0.U(pw.W))
  private val wptrGray = RegInit(0.U(pw.W))
  private val rptrGraySync1 = RegInit(0.U(pw.W))
  private val rptrGraySync2 = RegInit(0.U(pw.W))

  // Read domain.
  private val rptrBin = withClockAndReset(dstClock, io.dstReset) { RegInit(0.U(pw.W)) }
  private val rptrGray = withClockAndReset(dstClock, io.dstReset) { RegInit(0.U(pw.W)) }
  private val wptrGraySync1 = withClockAndReset(dstClock, io.dstReset) { RegInit(0.U(pw.W)) }
  private val wptrGraySync2 = withClockAndReset(dstClock, io.dstReset) { RegInit(0.U(pw.W)) }

  private val rptrBinSync = LoomCdc.grayToBin(rptrGraySync2)
  private val used = wptrBin - rptrBinSync
  private val full = used === depth.U

  io.push.ready := !full
  when(io.push.fire) {
    mem(wptrBin(aw - 1, 0)) := io.push.bits
    wptrBin := wptrBin + 1.U
    wptrGray := LoomCdc.binToGray(wptrBin + 1.U)
  }
  rptrGraySync1 := rptrGray
  rptrGraySync2 := rptrGraySync1

  private val wptrBinSync = LoomCdc.grayToBin(wptrGraySync2)
  private val empty = rptrBin === wptrBinSync

  io.pop.valid := !empty
  io.pop.bits := mem(rptrBin(aw - 1, 0))

  withClockAndReset(dstClock, io.dstReset) {
    wptrGraySync1 := wptrGray
    wptrGraySync2 := wptrGraySync1
    when(io.pop.fire) {
      rptrBin := rptrBin + 1.U
      rptrGray := LoomCdc.binToGray(rptrBin + 1.U)
    }
  }

  assert(used <= depth.U, "LM-P3: async fifo occupancy exceeds depth")
}

/** Credit pool whose returns cross a clock domain.
  *
  * Allocation happens in the source domain; every release increments a
  * gray-coded counter in the destination domain, which is synchronized back
  * with two flops. The source therefore sees a conservative (delayed)
  * credit count and can never over-issue. Release is expected to be gated
  * by the data path (e.g. a FIFO pop), so this block asserts only the
  * source-side P7 obligations.
  */
class LmCdcCredit(depth: Int) extends Module {
  require(depth >= 1, "credit depth must be >= 1")

  // The epoch must not wrap twice within the 2FF return-sync latency.
  private val cw = 16

  val io = IO(new Bundle {
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
    val alloc = Input(Bool())
    val canAlloc = Output(Bool())
    val used = Output(UInt(cw.W))
    val release = Input(Bool())
  })

  private val dstClock = io.dstClk.asClock
  private val allocBin = RegInit(0.U(cw.W))
  private val relBin = withClockAndReset(dstClock, io.dstReset) { RegInit(0.U(cw.W)) }
  private val relGray = withClockAndReset(dstClock, io.dstReset) { RegInit(0.U(cw.W)) }
  private val relGraySync1 = RegInit(0.U(cw.W))
  private val relGraySync2 = RegInit(0.U(cw.W))
  private val relBinSync = LoomCdc.grayToBin(relGraySync2)
  private val used = allocBin - relBinSync

  relGraySync1 := relGray
  relGraySync2 := relGraySync1

  withClockAndReset(dstClock, io.dstReset) {
    when(io.release) {
      relBin := relBin + 1.U
      relGray := LoomCdc.binToGray(relBin + 1.U)
    }
  }

  io.used := used
  io.canAlloc := used < depth.U
  when(io.alloc) { allocBin := allocBin + 1.U }

  assert(!io.alloc || io.canAlloc, "LM-P7: CDC credit over-alloc")
  assert(used <= depth.U, "LM-P7: CDC credit outstanding exceeds depth")
}

/** CDC link core: an async FIFO gated by its own conservative credit pool.
  *
  * The FIFO ready check and the credit check are intentionally redundant:
  * the FIFO guarantees storage, the credit pool proves that in-flight data
  * counted on the source side can never exceed the configured depth even
  * when the return counter is delayed by CDC synchronization.
  */
class LmAsyncLink(dataWidth: Int, depth: Int) extends Module {
  require(depth >= 2 && (depth & (depth - 1)) == 0, "depth must be a power of two >= 2")

  val io = IO(new Bundle {
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
    val in = Flipped(Decoupled(UInt(dataWidth.W)))
    val out = Decoupled(UInt(dataWidth.W))
    val creditUsed = Output(UInt(16.W))
  })

  private val fifo = Module(new LmAsyncFifo(dataWidth, depth))
  private val credit = Module(new LmCdcCredit(depth))

  fifo.io.dstClk := io.dstClk
  fifo.io.dstReset := io.dstReset
  credit.io.dstClk := io.dstClk
  credit.io.dstReset := io.dstReset

  fifo.io.push.valid := io.in.valid && credit.io.canAlloc
  fifo.io.push.bits := io.in.bits
  io.in.ready := fifo.io.push.ready && credit.io.canAlloc

  io.out.valid := fifo.io.pop.valid
  io.out.bits := fifo.io.pop.bits
  fifo.io.pop.ready := io.out.ready

  credit.io.alloc := fifo.io.push.fire
  credit.io.release := fifo.io.pop.fire
  io.creditUsed := credit.io.used
}

