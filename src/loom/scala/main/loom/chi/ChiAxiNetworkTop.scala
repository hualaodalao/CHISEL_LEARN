package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** AXI4 master-facing RN boundary port (single 64B burst, ID-tagged). */
class ChiAxiRnPort(c: ChiConfig) extends Bundle {
  val ar = Flipped(Decoupled(new ChiAxiAddr(c)))
  val r = Decoupled(new ChiAxiR(c))
  val aw = Flipped(Decoupled(new ChiAxiAddr(c)))
  val w = Flipped(Decoupled(new ChiAxiW(c)))
  val b = Decoupled(new ChiAxiB(c))
}

/** AXI4 slave-facing SN boundary port: the SN bridge is an AXI master, so the
  * top drives ar/aw/w and receives r/b. */
class ChiAxiSnPort(c: ChiConfig) extends Bundle {
  val ar = Decoupled(new ChiAxiAddr(c))
  val r = Flipped(Decoupled(new ChiAxiR(c)))
  val aw = Decoupled(new ChiAxiAddr(c))
  val w = Decoupled(new ChiAxiW(c))
  val b = Flipped(Decoupled(new ChiAxiB(c)))
}

/** Per-node mixed-protocol CHI network top (HW-09/A2/A3). Wraps ChiNetworkTop
  * and splits each node's boundary by its protocol into separate port vectors:
  * axi4 nodes attach bridges (RN read/write bridges share REQ via RR arbitration;
  * SN uses the CHI->AXI reverse bridge), chi nodes pass native flits through.
  * Only active ports are exposed (no unused inputs to tie). `rnAxiMap`/`snAxiMap`
  * give the global node index of each AXI port; chi ports follow the same order.
  * Bridges emit pure flits; VC scheduling stays inside the fabric.
  */
class ChiAxiNetworkTop(p: ChiNetworkParams) extends Module {
  private val c = p.cfg
  val rnAxiIdx: Seq[Int] = p.rn.zipWithIndex.filter(_._1.protocol == "axi4").map(_._2)
  val rnChiIdx: Seq[Int] = p.rn.zipWithIndex.filter(_._1.protocol != "axi4").map(_._2)
  val snAxiIdx: Seq[Int] = p.sn.zipWithIndex.filter(_._1.protocol == "axi4").map(_._2)
  val snChiIdx: Seq[Int] = p.sn.zipWithIndex.filter(_._1.protocol != "axi4").map(_._2)

  val io = IO(new Bundle {
    val rnAxi = Vec(rnAxiIdx.length, new ChiAxiRnPort(c))
    val rnChi = Vec(rnChiIdx.length, Flipped(new ChiRnPort(c)))
    val snAxi = Vec(snAxiIdx.length, new ChiAxiSnPort(c))
    val snChi = Vec(snChiIdx.length, Flipped(new ChiSnPort(c)))
  })

  private val net = Module(new ChiNetworkTop(p))

  for ((ni, k) <- rnAxiIdx.zipWithIndex) {
    val rn = net.io.rn(ni)
    val a = io.rnAxi(k)
    val rb = Module(new ChiAxiReadBridge(c))
    val wb = Module(new ChiAxiWriteBridge(c))
    LmWire.link(a.ar, rb.io.ar)
    LmWire.link(rb.io.r, a.r)
    LmWire.link(a.aw, wb.io.aw)
    LmWire.link(a.w, wb.io.w)
    LmWire.link(wb.io.b, a.b)
    val arb = Module(new Arbiter(new ChiReqFlit(c), 2))
    LmWire.link(rb.io.req, arb.io.in(0))
    LmWire.link(wb.io.req, arb.io.in(1))
    LmWire.link(arb.io.out, rn.req)
    LmWire.link(rn.rsp, wb.io.rsp)
    LmWire.link(rn.dat, rb.io.dat)
    LmWire.link(wb.io.dat, rn.txDat)
    rn.snp.ready := true.B
    rn.txRsp.valid := false.B; rn.txRsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
  }
  for ((ni, k) <- rnChiIdx.zipWithIndex) {
    val rn = net.io.rn(ni)
    val ch = io.rnChi(k)
    LmWire.link(ch.req, rn.req)
    LmWire.link(rn.rsp, ch.rsp)
    LmWire.link(rn.dat, ch.dat)
    LmWire.link(rn.snp, ch.snp)
    LmWire.link(ch.txRsp, rn.txRsp)
    LmWire.link(ch.txDat, rn.txDat)
  }

  for ((nj, k) <- snAxiIdx.zipWithIndex) {
    val sn = net.io.sn(nj)
    val a = io.snAxi(k)
    val br = Module(new ChiAxiSnBridge(c, p.sn(nj).nodeId))
    LmWire.link(sn.req, br.io.req)
    LmWire.link(br.io.rsp, sn.rsp)
    LmWire.link(br.io.dat, sn.dat)
    LmWire.link(sn.rxDat, br.io.rxDat)
    LmWire.link(br.io.ar, a.ar)
    LmWire.link(a.r, br.io.r)
    LmWire.link(br.io.aw, a.aw)
    LmWire.link(br.io.w, a.w)
    LmWire.link(a.b, br.io.b)
  }
  for ((nj, k) <- snChiIdx.zipWithIndex) {
    val sn = net.io.sn(nj)
    val ch = io.snChi(k)
    LmWire.link(sn.req, ch.req)
    LmWire.link(ch.rsp, sn.rsp)
    LmWire.link(ch.dat, sn.dat)
    LmWire.link(sn.rxDat, ch.rxDat)
  }
}