package loom.chi

import chisel3._
import chisel3.util._

/** Coherency reservation (D4: port placeholders only).
  *
  * Cache-state Resp encodings and the Snp* coherency opcode space are reserved
  * but NOT implemented. The usage gate rejects any reserved/unsupported
  * opcode in phase 1. RN-F/HN-F port shapes exist so a future coherency story
  * can attach without reworking the channel model; the shared SNP channel is
  * the same one DVM uses.
  */
object ChiReserved {
  // Cache-state Resp[2:0] encodings (reserved).
  val RespI: Int = 0x0
  val RespSC: Int = 0x1
  val RespUC: Int = 0x2
  val RespUD: Int = 0x3
  val RespSD: Int = 0x4

  // Reserved Snp* coherency opcode space (encoding placeholders).
  val snpReserved: Map[String, Int] = Map(
    "SnpShared" -> 0x01, "SnpUnique" -> 0x07, "SnpCleanInvalid" -> 0x09,
    "SnpMakeInvalid" -> 0x0a, "SnpOnce" -> 0x0b
  )

  /** Phase-1 usage gate: reserved or unsupported opcodes are rejected. */
  def checkOpcode(channel: ChiChannel.Value, name: String): Option[String] = {
    if (ChiOpcode.isSupported(channel, name)) None
    else if (ChiOpcode.reserved.contains(name))
      Some(s"CHI-GATE: opcode '$name' is reserved (coherency not implemented in phase 1)")
    else
      Some(s"CHI-GATE: opcode '$name' is unsupported on channel $channel")
  }
}

/** RN-F port placeholder: same shape as RN-I plus the SNP RX that coherency
  * needs (already present for RN-D/DVM). No cache logic is implemented.
  */
class ChiRnFPort(c: ChiConfig) extends Bundle {
  val req = Decoupled(new ChiReqFlit(c))
  val rsp = Flipped(Decoupled(new ChiRspFlit(c)))
  val dat = Flipped(Decoupled(new ChiDatFlit(c)))
  val snp = Flipped(Decoupled(new ChiSnpFlit(c)))
}

/** HN-F port placeholder: receives REQ, sends RSP/DAT/SNP, receives SnpResp
  * and CopyBack data. Serialization/snoop-filter logic is NOT implemented.
  */
class ChiHnFPort(c: ChiConfig) extends Bundle {
  val req = Flipped(Decoupled(new ChiReqFlit(c)))
  val rsp = Decoupled(new ChiRspFlit(c))
  val dat = Decoupled(new ChiDatFlit(c))
  val snp = Decoupled(new ChiSnpFlit(c))
  val rxRsp = Flipped(Decoupled(new ChiRspFlit(c)))
  val rxDat = Flipped(Decoupled(new ChiDatFlit(c)))
}