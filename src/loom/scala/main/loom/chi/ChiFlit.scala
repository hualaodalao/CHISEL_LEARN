package loom.chi

import chisel3._
import chisel3.util._

/** REQ channel flit (target spec §3). Opcode is 7 bits in CHI. */
class ChiReqFlit(c: ChiConfig) extends Bundle {
  val qos = UInt(4.W)
  val tgtId = UInt(c.nodeIdWidth.W)
  val srcId = UInt(c.nodeIdWidth.W)
  val txnId = UInt(c.txnIdWidth.W)
  val opcode = UInt(7.W)
  val addr = UInt(c.addrWidth.W)
  val size = UInt(3.W)
  val memAttr = UInt(4.W)
  val order = UInt(2.W)
  val allowRetry = Bool()
  val pcrdType = UInt(4.W)
  val expCompAck = Bool()
  val returnNid = UInt(c.nodeIdWidth.W)
  val returnTxnId = UInt(c.txnIdWidth.W)
  val ns = Bool()
}

/** RSP channel flit: completion / grant / retry / receipt messages. */
class ChiRspFlit(c: ChiConfig) extends Bundle {
  val qos = UInt(4.W)
  val tgtId = UInt(c.nodeIdWidth.W)
  val srcId = UInt(c.nodeIdWidth.W)
  val txnId = UInt(c.txnIdWidth.W)
  val opcode = UInt(5.W)
  val dbid = UInt(c.txnIdWidth.W)
  val respErr = UInt(2.W)
  val resp = UInt(3.W)
  val pcrdType = UInt(4.W)
}

/** DAT channel flit: read data, write data and DVM payload share this class. */
class ChiDatFlit(c: ChiConfig) extends Bundle {
  val qos = UInt(4.W)
  val tgtId = UInt(c.nodeIdWidth.W)
  val srcId = UInt(c.nodeIdWidth.W)
  val txnId = UInt(c.txnIdWidth.W)
  val opcode = UInt(4.W)
  val dataId = UInt(ChiWidths.dataIdWidth.W)
  val be = UInt(c.beWidth.W)
  val data = UInt(c.datWidth.W)
  val respErr = UInt(2.W)
  val resp = UInt(3.W)
  val homeNid = UInt(c.nodeIdWidth.W)
}

/** SNP channel flit: only SnpDVMOp is supported in phase 1. */
class ChiSnpFlit(c: ChiConfig) extends Bundle {
  val qos = UInt(4.W)
  val tgtId = UInt(c.nodeIdWidth.W)
  val srcId = UInt(c.nodeIdWidth.W)
  val txnId = UInt(c.txnIdWidth.W)
  val opcode = UInt(5.W)
  val addr = UInt(c.addrWidth.W)
  val fwdNid = UInt(c.nodeIdWidth.W)
  val fwdTxnId = UInt(c.txnIdWidth.W)
}

/** Request-node facing port: TX REQ/RSP/DAT, RX RSP/DAT/SNP. TX RSP carries
  * CompAck/SnpResp; TX DAT carries write data. */
class ChiRnPort(c: ChiConfig) extends Bundle {
  val req = Decoupled(new ChiReqFlit(c))
  val rsp = Flipped(Decoupled(new ChiRspFlit(c)))
  val dat = Flipped(Decoupled(new ChiDatFlit(c)))
  val snp = Flipped(Decoupled(new ChiSnpFlit(c)))
  val txRsp = Decoupled(new ChiRspFlit(c))
  val txDat = Decoupled(new ChiDatFlit(c))
}

/** Subordinate-node facing port: RX REQ + RX DAT (write data), TX RSP + TX DAT
  * (read data). DAT is split by direction: `dat` is SN->RN read data, `rxDat`
  * is RN->SN write data delivered by the fabric. */
class ChiSnPort(c: ChiConfig) extends Bundle {
  val req = Flipped(Decoupled(new ChiReqFlit(c)))
  val rsp = Decoupled(new ChiRspFlit(c))
  val dat = Decoupled(new ChiDatFlit(c))
  val rxDat = Flipped(Decoupled(new ChiDatFlit(c)))
}