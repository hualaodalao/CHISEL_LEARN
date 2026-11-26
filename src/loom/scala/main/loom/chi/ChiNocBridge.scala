package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** Inter-NoC bridge protocol proxy (HW-11/SEM-20). Terminating endpoint: from
  * side a subordinate (ChiSnPort) to the from-NoC, to side a request node
  * (ChiRnPort) into the to-NoC. Decodes forwarding windows (to-NoC SAM aliases,
  * address not remapped), re-originates REQ with independent to-side TxnID/
  * NodeID, merges responses back to the from source. Read: ReadNoSnp->CompData.
  * Write: WriteNoSnp->DBIDResp->write data (DBID pull)->Comp. Window miss ->
  * NDERR locally. Crossing cuts the inter-NoC dependency ring (G1 per subsystem).
  *
  * W12: asyncFifoDepth>=2 inserts a per-channel ChiAsyncLink on every to-side
  * channel (FSM stays in the from clock domain; io.to runs in dstClk domain),
  * with store&fwd buffering so one side's backpressure does not stall the other.
  * asyncFifoDepth=0 wires directly (W11 sync behavior).
  */
class ChiNocBridge(c: ChiConfig, windows: Seq[(Long, Long)], fromNodeId: Int, toNodeId: Int,
                   asyncFifoDepth: Int = 0, toDatWidth: Int = 0, dvmForward: Boolean = false) extends Module {
  private val toW = if (toDatWidth == 0) c.datWidth else toDatWidth
  private val toCfg = if (toW == c.datWidth) c else c.copy(datWidth = toW)
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)
  private val bw = math.max(1, log2Ceil(nFlits))
  private val async = asyncFifoDepth >= 2
  if (async) require((asyncFifoDepth & (asyncFifoDepth - 1)) == 0, "asyncFifoDepth must be power of two")
  // v1 DVM policy: each NoC's MN is autonomous; DVMOp does not cross the bridge
  // (the from-side SN port has no SNP injection path). Cross-bridge SnpDVMOp
  // forwarding + remote SnpResp collection (dvmForward=true) is a follow-up.
  require(!dvmForward, "dvmForward=true (cross-bridge DVM) is a follow-up; v1 keeps per-NoC MN autonomy")

  val io = IO(new Bundle {
    val from = new ChiSnPort(c)
    val to = new ChiRnPort(toCfg)
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
  })

  private val sIdle :: sFwdReq :: sRdData :: sWrDbid :: sWrData :: sWrComp :: sMiss :: Nil = Enum(7)
  private val st = RegInit(sIdle)
  private val fromTxn = Reg(UInt(c.txnIdWidth.W))
  private val fromSrc = Reg(UInt(c.nodeIdWidth.W))
  private val reqBits = Reg(new ChiReqFlit(c))
  private val isWrite = Reg(Bool())
  private val toDbid = Reg(UInt(c.txnIdWidth.W))
  private val toTgt = Reg(UInt(c.nodeIdWidth.W))
  private val beat = RegInit(0.U(bw.W))

  // FSM-facing to-side wires (from clock domain); async links cross to io.to.
  private val toReq = Wire(Decoupled(new ChiReqFlit(c)))
  private val toRsp = Wire(Decoupled(new ChiRspFlit(c)))
  private val toDat = Wire(Decoupled(new ChiDatFlit(c)))
  private val toTxDat = Wire(Decoupled(new ChiDatFlit(c)))
  private val toSnp = Wire(Decoupled(new ChiSnpFlit(c)))
  private val toTxRsp = Wire(Decoupled(new ChiRspFlit(c)))

  private def crossTx[T <: Data](gen: T, src: DecoupledIO[T], dst: DecoupledIO[T]): Unit = {
    if (async) {
      val al = Module(new ChiAsyncLink(gen, asyncFifoDepth))
      al.io.dstClk := io.dstClk; al.io.dstReset := io.dstReset
      LmWire.link(src, al.io.in); LmWire.link(al.io.out, dst)
    } else LmWire.link(src, dst)
  }
  // RX crosses from io.to (dstClk domain) back into the from clock: instantiate
  // the async link in the dst domain, its destination is the from clock.
  private val fromClk = clock.asBool
  private val fromRst = reset.asBool
  private def crossRx[T <: Data](gen: T, src: DecoupledIO[T], dst: DecoupledIO[T]): Unit = {
    if (async) {
      withClockAndReset(io.dstClk.asClock, io.dstReset.asAsyncReset) {
        val al = Module(new ChiAsyncLink(gen, asyncFifoDepth))
        al.io.dstClk := fromClk; al.io.dstReset := fromRst
        LmWire.link(src, al.io.in); LmWire.link(al.io.out, dst)
      }
    } else LmWire.link(src, dst)
  }
  crossTx(new ChiReqFlit(c), toReq, io.to.req)
  crossTx(new ChiRspFlit(c), toTxRsp, io.to.txRsp)
  crossRx(new ChiRspFlit(c), io.to.rsp, toRsp)
  crossRx(new ChiSnpFlit(c), io.to.snp, toSnp)

  // DAT paths carry a width gearbox between the from-side FSM (c) and the
  // to-side fabric (toCfg); matched widths pass through. RX read data crosses
  // back into the from clock first, then converts; TX write data converts in
  // the from clock, then crosses out.
  private val toDatRaw = Wire(Decoupled(new ChiDatFlit(toCfg)))
  crossRx(new ChiDatFlit(toCfg), io.to.dat, toDatRaw)
  private val rdConv = Module(new ChiDatWidth(toCfg, c))
  LmWire.link(toDatRaw, rdConv.io.in); LmWire.link(rdConv.io.out, toDat)

  private val toTxDatRaw = Wire(Decoupled(new ChiDatFlit(toCfg)))
  private val wrConv = Module(new ChiDatWidth(c, toCfg))
  LmWire.link(toTxDat, wrConv.io.in); LmWire.link(wrConv.io.out, toTxDatRaw)
  crossTx(new ChiDatFlit(toCfg), toTxDatRaw, io.to.txDat)

  private val addr = io.from.req.bits.addr
  private val hit = windows.map { case (b, s) => addr >= b.U && addr <= (b + s - 1).U }.reduceOption(_ || _).getOrElse(false.B)

  io.from.req.ready := st === sIdle
  toReq.valid := st === sFwdReq
  toReq.bits := reqBits
  toReq.bits.txnId := 0.U
  toReq.bits.srcId := toNodeId.U

  io.from.dat.valid := st === sRdData && toDat.valid
  io.from.dat.bits := toDat.bits
  io.from.dat.bits.txnId := fromTxn
  io.from.dat.bits.tgtId := fromSrc
  toDat.ready := st === sRdData && io.from.dat.ready

  io.from.rxDat.ready := st === sWrData
  toTxDat.valid := st === sWrData && io.from.rxDat.valid
  toTxDat.bits := io.from.rxDat.bits
  toTxDat.bits.txnId := toDbid
  toTxDat.bits.tgtId := toTgt

  private val rspFwd = (st === sWrDbid || st === sWrComp) && toRsp.valid
  io.from.rsp.valid := rspFwd || st === sMiss
  io.from.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
  io.from.rsp.bits.txnId := fromTxn
  io.from.rsp.bits.tgtId := fromSrc
  io.from.rsp.bits.srcId := fromNodeId.U
  io.from.rsp.bits.dbid := fromTxn
  io.from.rsp.bits.opcode := Mux(st === sWrDbid, ChiOp.RspDBIDResp.U, ChiOp.RspComp.U)
  io.from.rsp.bits.respErr := Mux(st === sMiss, ChiRespErr.NDERR.U, ChiRespErr.OK.U)
  toRsp.ready := (st === sWrDbid || st === sWrComp) && io.from.rsp.ready

  toTxRsp.valid := false.B; toTxRsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
  toSnp.ready := true.B

  switch(st) {
    is(sIdle) {
      when(io.from.req.fire) {
        fromTxn := io.from.req.bits.txnId
        fromSrc := io.from.req.bits.srcId
        reqBits := io.from.req.bits
        isWrite := io.from.req.bits.opcode === ChiOp.WriteNoSnpFull.U
        beat := 0.U
        st := Mux(hit, sFwdReq, sMiss)
      }
    }
    is(sFwdReq) { when(toReq.fire) { beat := 0.U; st := Mux(isWrite, sWrDbid, sRdData) } }
    is(sRdData) {
      when(io.from.dat.fire) {
        when(beat === (nFlits - 1).U) { st := sIdle }
        beat := beat + 1.U
      }
    }
    is(sWrDbid) {
      when(io.from.rsp.fire) { toDbid := toRsp.bits.dbid; toTgt := toRsp.bits.srcId; beat := 0.U; st := sWrData }
    }
    is(sWrData) {
      when(toTxDat.fire) {
        when(beat === (nFlits - 1).U) { st := sWrComp }
        beat := beat + 1.U
      }
    }
    is(sWrComp) { when(io.from.rsp.fire) { st := sIdle } }
    is(sMiss) { when(io.from.rsp.fire) { st := sIdle } }
  }
}