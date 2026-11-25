package loom.chi

import chisel3._
import chisel3.util._

/** Sender-side flit L-Credit counter (CHI link layer).
  *
  * The receiver grants one credit per cycle on `lcrdv`; the sender consumes
  * one per flit on `send`. A flit may only be sent while a credit is held.
  * The count is bounded by the receiver buffer depth (<= 15 per channel).
  */
class ChiTxCredit(maxDepth: Int) extends Module {
  require(maxDepth >= 1 && maxDepth <= 15, "L-Credit depth must be in [1,15]")
  private val w = math.max(1, log2Ceil(maxDepth + 1))

  val io = IO(new Bundle {
    val lcrdv = Input(Bool())
    val send = Input(Bool())
    val canSend = Output(Bool())
    val count = Output(UInt(w.W))
  })

  private val cnt = RegInit(0.U(w.W))
  io.canSend := cnt =/= 0.U
  io.count := cnt

  when(io.lcrdv && !io.send) { cnt := cnt + 1.U }
    .elsewhen(io.send && !io.lcrdv) { cnt := cnt - 1.U }

  assert(!(io.lcrdv && cnt === maxDepth.U), "CHI L-Credit overflow")
  assert(!(io.send && cnt === 0.U), "CHI L-Credit underflow")
}

/** CHI link activation FSM (combined TX/RX view).
  *
  * STOP -> ACTIVATE -> RUN -> DEACTIVATE -> STOP. Activity (either direction)
  * wakes the link; both handshake sides must acknowledge to enter RUN; the
  * link sleeps only when both directions are idle and acknowledged down.
  */
class ChiLinkFsm extends Module {
  val io = IO(new Bundle {
    val txSactive = Input(Bool())
    val rxSactive = Input(Bool())
    val txAck = Input(Bool())
    val rxReq = Input(Bool())
    val txReq = Output(Bool())
    val rxAck = Output(Bool())
    val run = Output(Bool())
    val state = Output(UInt(2.W))
  })

  private val sStop = 0.U(2.W)
  private val sActivate = 1.U(2.W)
  private val sRun = 2.U(2.W)
  private val sDeactivate = 3.U(2.W)
  private val st = RegInit(sStop)

  io.state := st
  io.txReq := st === sActivate || st === sRun
  io.rxAck := st === sActivate || st === sRun
  io.run := st === sRun

  switch(st) {
    is(sStop) { when(io.txSactive || io.rxSactive) { st := sActivate } }
    is(sActivate) { when(io.txAck && io.rxReq) { st := sRun } }
    is(sRun) { when(!io.txSactive && !io.rxSactive) { st := sDeactivate } }
    is(sDeactivate) { when(!io.txAck && !io.rxReq) { st := sStop } }
  }
}

/** Credit-gated TX link for one CHI channel.
  *
  * A flit crosses only while the link is in RUN and a credit is held; the
  * activation FSM and credit counter are exposed for observation. RX-side
  * credit grant and routing integration land with the network layer (S4).
  */
class ChiTxLink[T <: Data](gen: T, depth: Int) extends Module {
  private val w = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(gen.cloneType))
    val deq = Decoupled(gen.cloneType)
    val lcrdv = Input(Bool())
    val txSactive = Input(Bool())
    val rxSactive = Input(Bool())
    val txAck = Input(Bool())
    val rxReq = Input(Bool())
    val txReq = Output(Bool())
    val rxAck = Output(Bool())
    val run = Output(Bool())
    val creditCount = Output(UInt(w.W))
  })

  private val fsm = Module(new ChiLinkFsm)
  fsm.io.txSactive := io.txSactive
  fsm.io.rxSactive := io.rxSactive
  fsm.io.txAck := io.txAck
  fsm.io.rxReq := io.rxReq

  private val cred = Module(new ChiTxCredit(depth))
  cred.io.lcrdv := io.lcrdv
  cred.io.send := io.deq.fire

  private val ok = fsm.io.run && cred.io.canSend
  io.deq.valid := io.enq.valid && ok
  io.deq.bits := io.enq.bits
  io.enq.ready := io.deq.ready && ok

  io.txReq := fsm.io.txReq
  io.rxAck := fsm.io.rxAck
  io.run := fsm.io.run
  io.creditCount := cred.io.count
}