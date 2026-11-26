package loom.chi

import chisel3._
import chisel3.util._

/** S2 skeleton CHI fabric top.
  *
  * Exposes request-node and subordinate-node CHI ports and moves address
  * decode to a per-RN SAM (observable on `tgtId`). Channel transport, link
  * credits and routing are filled by S3/S4; until then the channel internals
  * are safely tied off so the port skeleton emits deterministically.
  */
class ChiTop(p: ChiParams) extends Module {
  private val c = p.cfg

  val io = IO(new Bundle {
    val rn = Vec(p.nRn, Flipped(new ChiRnPort(c)))
    val sn = Vec(p.nSn, Flipped(new ChiSnPort(c)))
    val tgtId = Output(Vec(p.nRn, UInt(c.nodeIdWidth.W)))
  })

  for (i <- 0 until p.nRn) {
    io.tgtId(i) := ChiSamDecode(io.rn(i).req.bits.addr, c.addrWidth, p.sam, c.nodeIdWidth)
    io.rn(i).req.ready := true.B
    io.rn(i).rsp.valid := false.B
    io.rn(i).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    io.rn(i).dat.valid := false.B
    io.rn(i).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    io.rn(i).snp.valid := false.B
    io.rn(i).snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    io.rn(i).txRsp.ready := true.B
    io.rn(i).txDat.ready := true.B
  }

  for (j <- 0 until p.nSn) {
    io.sn(j).req.valid := false.B
    io.sn(j).req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
    io.sn(j).rsp.ready := true.B
    io.sn(j).dat.ready := true.B
    io.sn(j).rxDat.valid := false.B
    io.sn(j).rxDat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
  }
}