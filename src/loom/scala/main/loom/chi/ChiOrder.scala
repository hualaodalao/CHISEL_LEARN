package loom.chi

import chisel3._
import chisel3.util._

/** Ordering rule selection (D9): Normal memory is unordered; Device memory
  * forces Request Order. OWO is reserved for future support.
  */
object ChiOrderRule {
  def orderFor(memAttr: Int): Int =
    if (ChiMemAttr.device(memAttr)) ChiOrder.RequestOrder else ChiOrder.NoOrder
}

/** Ordered-request closure (Device RequestOrder).
  *
  * Accepts one ordered request, issues ReadReceipt, waits for the completion
  * (Comp), then holds the tracker open until CompAck arrives. Comp alone does
  * NOT close the tracker — only CompAck does, which is the CHI ExpCompAck
  * closure semantics.
  */
class ChiOrderCtrl(c: ChiConfig) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ChiReqFlit(c)))
    val readReceipt = Decoupled(new ChiRspFlit(c))
    val comp = Input(Bool())
    val compAck = Flipped(Decoupled(new ChiRspFlit(c)))
    val done = Output(Bool())
  })

  private val sIdle :: sReceipt :: sWaitComp :: sWaitAck :: Nil = Enum(4)
  private val st = RegInit(sIdle)
  private val reqTxn = Reg(UInt(c.txnIdWidth.W))
  private val reqSrc = Reg(UInt(c.nodeIdWidth.W))

  io.req.ready := st === sIdle
  io.readReceipt.valid := st === sReceipt
  io.readReceipt.bits := 0.U.asTypeOf(new ChiRspFlit(c))
  io.readReceipt.bits.opcode := ChiOp.RspReadReceipt.U
  io.readReceipt.bits.txnId := reqTxn
  io.readReceipt.bits.tgtId := reqSrc
  io.compAck.ready := st === sWaitAck
  io.done := st === sWaitAck && io.compAck.fire

  switch(st) {
    is(sIdle) {
      when(io.req.fire) {
        reqTxn := io.req.bits.txnId
        reqSrc := io.req.bits.srcId
        st := sReceipt
      }
    }
    is(sReceipt) { when(io.readReceipt.fire) { st := sWaitComp } }
    is(sWaitComp) { when(io.comp) { st := sWaitAck } }
    is(sWaitAck) { when(io.compAck.fire) { st := sIdle } }
  }
}