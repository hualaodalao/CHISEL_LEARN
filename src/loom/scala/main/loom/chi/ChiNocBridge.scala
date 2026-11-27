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
  * channel (control stays in the from clock domain; io.to runs in dstClk domain),
  * with store&fwd buffering so one side's backpressure does not stall the other.
  * asyncFifoDepth=0 wires directly (W11 sync behavior).
  *
  * W14/W15: outstanding>1 tracks multiple in-flight transactions in a context
  * table indexed by the to-side TxnID (ChiIdPool free-list). REQ intake forwards
  * combinationally and allocates an ID; read DAT / to.rsp match by to TxnID;
  * write data matches by {fromSrc,fromTxn} CAM; completions arbitrate (RR) onto
  * from.rsp. outstanding=1 degenerates to single-transaction behavior. v1
  * multi-outstanding requires matched DAT width (per-txn gearbox reassembly for
  * mixed width is a follow-up).
  */
class ChiNocBridge(c: ChiConfig, windows: Seq[(Long, Long)], fromNodeId: Int, toNodeId: Int,
                   asyncFifoDepth: Int = 0, toDatWidth: Int = 0, dvmForward: Boolean = false,
                   outstanding: Int = 1) extends Module {
  private val toW = if (toDatWidth == 0) c.datWidth else toDatWidth
  private val toCfg = if (toW == c.datWidth) c else c.copy(datWidth = toW)
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)
  private val idStep = c.datWidth / 128
  private val lastDataId = (nFlits - 1) * idStep
  private val async = asyncFifoDepth >= 2
  if (async) require((asyncFifoDepth & (asyncFifoDepth - 1)) == 0, "asyncFifoDepth must be power of two")
  // v1 DVM policy: each NoC's MN is autonomous; DVMOp does not cross the bridge
  // (the from-side SN port has no SNP injection path). Cross-bridge SnpDVMOp
  // forwarding + remote SnpResp collection (dvmForward=true) is a follow-up.
  require(!dvmForward, "dvmForward=true (cross-bridge DVM) is a follow-up; v1 keeps per-NoC MN autonomy")
  require(outstanding >= 1 && outstanding <= (1 << c.txnIdWidth),
    s"outstanding must be in [1, ${1 << c.txnIdWidth}], got $outstanding")
  require(outstanding == 1 || toW == c.datWidth,
    "multi-outstanding (outstanding>1) requires matched DAT width; mixed-width multi-outstanding is a follow-up")

  val io = IO(new Bundle {
    val from = new ChiSnPort(c)
    val to = new ChiRnPort(toCfg)
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
  })

  // Per-transaction context, indexed by to-side TxnID (= pool allocId).
  private val sRdData :: sWrDbid :: sWrDbidFwd :: sWrData :: sWrCompWait :: sWrCompFwd :: Nil = Enum(6)
  private class Ctx extends Bundle {
    val valid = Bool()
    val fromTxn = UInt(c.txnIdWidth.W)
    val fromSrc = UInt(c.nodeIdWidth.W)
    val isWrite = Bool()
    val toTgt = UInt(c.nodeIdWidth.W)
    val toDbid = UInt(c.txnIdWidth.W)
    val state = UInt(3.W)
  }
  private val ctx = RegInit(VecInit(Seq.fill(outstanding)(0.U.asTypeOf(new Ctx))))
  private val pool = Module(new ChiIdPool(outstanding, c.txnIdWidth))
  pool.io.alloc := false.B
  pool.io.release := false.B
  pool.io.releaseId := 0.U

  // Window-miss completion (does not occupy a pool slot).
  private val missPend = RegInit(false.B)
  private val missTxn = Reg(UInt(c.txnIdWidth.W))
  private val missSrc = Reg(UInt(c.nodeIdWidth.W))

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
  private val reqIsWr = io.from.req.bits.opcode === ChiOp.WriteNoSnpFull.U

  // REQ intake: hit forwards combinationally and allocates a to-side TxnID;
  // miss is answered locally (NDERR) without occupying a pool slot.
  private val reqFireHit = io.from.req.valid && hit && pool.io.canAlloc && toReq.ready
  private val reqFireMiss = io.from.req.valid && !hit && !missPend
  io.from.req.ready := Mux(hit, pool.io.canAlloc && toReq.ready, !missPend)
  toReq.valid := io.from.req.valid && hit && pool.io.canAlloc
  toReq.bits := io.from.req.bits
  toReq.bits.txnId := pool.io.allocId
  toReq.bits.srcId := toNodeId.U
  pool.io.alloc := reqFireHit
  when(reqFireHit) {
    val e = ctx(pool.io.allocId)
    e.valid := true.B
    e.fromTxn := io.from.req.bits.txnId
    e.fromSrc := io.from.req.bits.srcId
    e.isWrite := reqIsWr
    e.state := Mux(reqIsWr, sWrDbid, sRdData)
    e.toTgt := 0.U; e.toDbid := 0.U
  }
  when(reqFireMiss) { missPend := true.B; missTxn := io.from.req.bits.txnId; missSrc := io.from.req.bits.srcId }

  // Read data: match by to-side TxnID, remap back to the from source, forward
  // flit-by-flit (matched width needs no reassembly); last flit releases the ID.
  private val rdCtx = ctx(toDat.bits.txnId)
  private val rdOk = toDat.valid && rdCtx.valid && rdCtx.state === sRdData
  io.from.dat.valid := rdOk
  io.from.dat.bits := toDat.bits
  io.from.dat.bits.txnId := rdCtx.fromTxn
  io.from.dat.bits.tgtId := rdCtx.fromSrc
  toDat.ready := rdOk && io.from.dat.ready

  // to-side RSP: DBIDResp opens the write-data phase; Comp closes the transaction.
  private val rspCtx = ctx(toRsp.bits.txnId)
  private val isDbid = toRsp.bits.opcode === ChiOp.RspDBIDResp.U
  private val isComp = toRsp.bits.opcode === ChiOp.RspComp.U
  private val rspAccept = toRsp.valid && ((rspCtx.state === sWrDbid && isDbid) || (rspCtx.state === sWrCompWait && isComp))
  toRsp.ready := rspAccept
  when(rspAccept) {
    when(isDbid) { rspCtx.toDbid := toRsp.bits.dbid; rspCtx.toTgt := toRsp.bits.srcId; rspCtx.state := sWrDbidFwd }
      .otherwise { rspCtx.state := sWrCompFwd }
  }

  // Write data: CAM match {fromSrc,fromTxn} to the in-flight write context,
  // re-tag with the to-side DBID/TgtID and forward; last flit awaits Comp.
  private val rxSrc = io.from.rxDat.bits.srcId
  private val rxTxn = io.from.rxDat.bits.txnId
  private val wrHits = VecInit(ctx.map(e => e.valid && e.state === sWrData && e.fromSrc === rxSrc && e.fromTxn === rxTxn))
  private val wrIdx = PriorityEncoder(wrHits.asUInt)
  private val wrFound = wrHits.asUInt.orR
  private val wrCtx = ctx(wrIdx)
  io.from.rxDat.ready := wrFound && toTxDat.ready
  toTxDat.valid := io.from.rxDat.valid && wrFound
  toTxDat.bits := io.from.rxDat.bits
  toTxDat.bits.txnId := wrCtx.toDbid
  toTxDat.bits.tgtId := wrCtx.toTgt
  when(toTxDat.fire && io.from.rxDat.bits.dataId === lastDataId.U) { wrCtx.state := sWrCompWait }

  // Completion arbitration (RR): per-context DBIDResp/Comp plus the miss NDERR.
  private val rspArb = Module(new Arbiter(new ChiRspFlit(c), outstanding + 1))
  for (i <- 0 until outstanding) {
    val e = ctx(i)
    rspArb.io.in(i).valid := e.valid && (e.state === sWrDbidFwd || e.state === sWrCompFwd)
    rspArb.io.in(i).bits := 0.U.asTypeOf(new ChiRspFlit(c))
    rspArb.io.in(i).bits.txnId := e.fromTxn
    rspArb.io.in(i).bits.tgtId := e.fromSrc
    rspArb.io.in(i).bits.srcId := fromNodeId.U
    rspArb.io.in(i).bits.dbid := e.fromTxn
    rspArb.io.in(i).bits.opcode := Mux(e.state === sWrDbidFwd, ChiOp.RspDBIDResp.U, ChiOp.RspComp.U)
    rspArb.io.in(i).bits.respErr := ChiRespErr.OK.U
  }
  rspArb.io.in(outstanding).valid := missPend
  rspArb.io.in(outstanding).bits := 0.U.asTypeOf(new ChiRspFlit(c))
  rspArb.io.in(outstanding).bits.txnId := missTxn
  rspArb.io.in(outstanding).bits.tgtId := missSrc
  rspArb.io.in(outstanding).bits.srcId := fromNodeId.U
  rspArb.io.in(outstanding).bits.dbid := missTxn
  rspArb.io.in(outstanding).bits.opcode := ChiOp.RspComp.U
  rspArb.io.in(outstanding).bits.respErr := ChiRespErr.NDERR.U
  LmWire.link(rspArb.io.out, io.from.rsp)

  private val rspCh = rspArb.io.chosen
  private val wrCompFire = rspArb.io.out.fire && rspCh =/= outstanding.U && ctx(rspCh).state === sWrCompFwd
  when(rspArb.io.out.fire) {
    when(rspCh === outstanding.U) { missPend := false.B }
      .otherwise {
        val e = ctx(rspCh)
        when(e.state === sWrDbidFwd) { e.state := sWrData }
          .otherwise { e.valid := false.B }
      }
  }

  // ID release: read-last-flit and write-Comp each enqueue; RR drains to the
  // single-release pool port. Queue depth = outstanding bounds pending releases.
  private val rdQ = Module(new Queue(UInt(c.txnIdWidth.W), outstanding))
  rdQ.io.enq.valid := toDat.fire && toDat.bits.dataId === lastDataId.U
  rdQ.io.enq.bits := toDat.bits.txnId
  private val wrQ = Module(new Queue(UInt(c.txnIdWidth.W), outstanding))
  wrQ.io.enq.valid := wrCompFire
  wrQ.io.enq.bits := rspCh
  private val relArb = Module(new Arbiter(UInt(c.txnIdWidth.W), 2))
  LmWire.link(rdQ.io.deq, relArb.io.in(0))
  LmWire.link(wrQ.io.deq, relArb.io.in(1))
  relArb.io.out.ready := true.B
  pool.io.release := relArb.io.out.fire
  pool.io.releaseId := relArb.io.out.bits

  toTxRsp.valid := false.B; toTxRsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
  toSnp.ready := true.B
}