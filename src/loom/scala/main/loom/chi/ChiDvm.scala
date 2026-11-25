package loom.chi

import chisel3._
import chisel3.util._

/** DVM message families (full set, D3). SYNC guarantees prior DVM completion. */
object ChiDvmMsg {
  val families: Seq[String] = Seq("TLBI", "BPI", "VIC", "SYNC")
  val tlbiSubtypes: Seq[String] = Seq("VMALL", "VMALLS1", "ASID", "VA", "VAA", "IPAS2", "IPA")
  def isSync(family: String): Boolean = family == "SYNC"
}

/** Miscellaneous Node (MN) DVM engine, network-facing (single SNP TX / single
  * SnpResp RX). DVMOp(REQ) -> DBIDResp -> payload(DAT) -> serialize SnpDVMOp to
  * each RN-D (TgtID = target NodeID) -> collect SnpResp by SrcID -> Comp.
  * Broadcast walks targets one flit at a time; collect marks a per-target mask
  * keyed by SrcID so partial progress is preserved under backpressure.
  */
class ChiDvmMn(c: ChiConfig, mnNodeId: Int, targetNodeIds: Seq[Int]) extends Module {
  require(targetNodeIds.nonEmpty, "DVM needs at least one RN-D target")
  private val nT = targetNodeIds.length
  private val bw = math.max(1, log2Ceil(nT))
  private val targetVec = VecInit(targetNodeIds.map(_.U(c.nodeIdWidth.W)))

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ChiReqFlit(c)))
    val rsp = Decoupled(new ChiRspFlit(c))
    val dat = Flipped(Decoupled(new ChiDatFlit(c)))
    val snp = Decoupled(new ChiSnpFlit(c))
    val snpResp = Flipped(Decoupled(new ChiRspFlit(c)))
  })

  private val sIdle :: sGrant :: sPayload :: sBroadcast :: sCollect :: sComp :: Nil = Enum(6)
  private val st = RegInit(sIdle)
  private val reqTxn = Reg(UInt(c.txnIdWidth.W))
  private val reqSrc = Reg(UInt(c.nodeIdWidth.W))
  private val dbid = Reg(UInt(c.txnIdWidth.W))
  private val snpAddr = Reg(UInt(c.addrWidth.W))
  private val bIdx = RegInit(0.U(bw.W))
  private val collected = RegInit(VecInit(Seq.fill(nT)(false.B)))

  io.req.ready := st === sIdle
  io.dat.ready := st === sPayload
  io.rsp.valid := false.B
  io.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))

  io.snp.valid := st === sBroadcast
  io.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
  io.snp.bits.opcode := ChiOp.SnpDVMOp.U
  io.snp.bits.addr := snpAddr
  io.snp.bits.srcId := mnNodeId.U
  io.snp.bits.tgtId := targetVec(bIdx)

  io.snpResp.ready := st === sCollect

  private val collNext = Wire(Vec(nT, Bool()))
  for (p <- 0 until nT) {
    collNext(p) := collected(p) || (io.snpResp.fire && io.snpResp.bits.srcId === targetVec(p))
  }

  switch(st) {
    is(sIdle) {
      when(io.req.fire) {
        reqTxn := io.req.bits.txnId
        reqSrc := io.req.bits.srcId
        dbid := io.req.bits.txnId
        bIdx := 0.U
        collected.foreach(_ := false.B)
        st := sGrant
      }
    }
    is(sGrant) {
      io.rsp.valid := true.B
      io.rsp.bits.opcode := ChiOp.RspDBIDResp.U
      io.rsp.bits.txnId := reqTxn
      io.rsp.bits.dbid := dbid
      io.rsp.bits.tgtId := reqSrc
      io.rsp.bits.srcId := mnNodeId.U
      when(io.rsp.fire) { st := sPayload }
    }
    is(sPayload) {
      when(io.dat.fire) {
        snpAddr := io.dat.bits.data(c.addrWidth - 1, 0)
        st := sBroadcast
      }
    }
    is(sBroadcast) {
      when(io.snp.fire) {
        when(bIdx === (nT - 1).U) { st := sCollect }
        bIdx := bIdx + 1.U
      }
    }
    is(sCollect) {
      collected := collNext
      when(collNext.asUInt.andR) { st := sComp }
    }
    is(sComp) {
      io.rsp.valid := true.B
      io.rsp.bits.opcode := ChiOp.RspComp.U
      io.rsp.bits.txnId := reqTxn
      io.rsp.bits.tgtId := reqSrc
      io.rsp.bits.srcId := mnNodeId.U
      when(io.rsp.fire) { st := sIdle }
    }
  }
}