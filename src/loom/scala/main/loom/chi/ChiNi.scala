package loom.chi

import chisel3._
import chisel3.util._

/** NI error gate (HW-05). A request whose address misses the SAM is answered
  * locally with Comp(RespErr=NDERR) and never enters the fabric; a hit passes
  * through to the fabric unchanged. `hit` comes from ChiSamDecode.withHit.
  */
class ChiNiError(c: ChiConfig) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ChiReqFlit(c)))
    val hit = Input(Bool())
    val fwd = Decoupled(new ChiReqFlit(c))
    val rsp = Decoupled(new ChiRspFlit(c))
  })

  private val sIdle :: sComp :: Nil = Enum(2)
  private val st = RegInit(sIdle)
  private val reqTxn = Reg(UInt(c.txnIdWidth.W))
  private val reqSrc = Reg(UInt(c.nodeIdWidth.W))

  io.fwd.valid := io.req.valid && io.hit
  io.fwd.bits := io.req.bits
  io.req.ready := Mux(io.hit, io.fwd.ready, st === sIdle)

  io.rsp.valid := false.B
  io.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))

  when(io.req.fire && !io.hit) {
    reqTxn := io.req.bits.txnId
    reqSrc := io.req.bits.srcId
    st := sComp
  }

  switch(st) {
    is(sComp) {
      io.rsp.valid := true.B
      io.rsp.bits.opcode := ChiOp.RspComp.U
      io.rsp.bits.respErr := ChiRespErr.NDERR.U
      io.rsp.bits.txnId := reqTxn
      io.rsp.bits.tgtId := reqSrc
      when(io.rsp.fire) { st := sIdle }
    }
  }
}