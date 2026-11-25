package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** Multi-router CHI network top. Each router is a four-class ChiRouter4 with
  * per-VC weighted scheduling; router-router links carry the VC sideband so the
  * dateline half propagates across hops. RN/SN/MN nodes attach to local ports
  * through inject/eject adapters (pure flit <-> VC transport). Routing is by
  * TgtID (class-independent table).
  */
class ChiNetworkTop(p: ChiNetworkParams) extends Module {
  private val c = p.cfg
  private val qm = new ChiQosMap(c)
  private val vw = c.vcWidth

  val io = IO(new Bundle {
    val rn = Vec(p.nRn, Flipped(new ChiRnPort(c)))
    val sn = Vec(p.nSn, Flipped(new ChiSnPort(c)))
  })

  private def inject[T <: Data](gen: T, src: DecoupledIO[T], dst: DecoupledIO[ChiVcBundle[T]], qosOf: T => UInt): Unit = {
    val a = Module(new ChiVcInject(gen, qm, qosOf))
    LmWire.link(src, a.io.in); LmWire.link(a.io.out, dst)
  }
  private def eject[T <: Data](gen: T, src: DecoupledIO[ChiVcBundle[T]], dst: DecoupledIO[T]): Unit = {
    val a = Module(new ChiVcEject(gen, vw))
    LmWire.link(src, a.io.in); LmWire.link(a.io.out, dst)
  }
  private def vcReq = new ChiVcBundle(new ChiReqFlit(c), vw)
  private def vcRsp = new ChiVcBundle(new ChiRspFlit(c), vw)
  private def vcSnp = new ChiVcBundle(new ChiSnpFlit(c), vw)
  private def vcDat = new ChiVcBundle(new ChiDatFlit(c), vw)

  private val routers = p.routers.map { r =>
    Module(new ChiRouter4(c, r.nPort, r.route, r.route, r.route, r.route,
      depth = p.queueDepth, snpEnable = p.snpEnable, cutOut = r.outCut))
  }

  private val rnIndex = p.rn.zipWithIndex.map { case (n, i) => n.nodeId -> i }.toMap
  private val snIndex = p.sn.zipWithIndex.map { case (n, i) => n.nodeId -> i }.toMap
  private val localNodes = p.routers.map { r =>
    p.allNodes.filter(_.router == r.index).sortBy(_.localPort)
  }

  for (k <- p.routers.indices) {
    val rt = routers(k)
    for (pIdx <- localNodes(k).indices) {
      val n = localNodes(k)(pIdx)
      val port = rt.io.port(pIdx)
      if (ChiNodeKind.isRequest(n.kind)) {
        val i = rnIndex(n.nodeId)
        inject(new ChiReqFlit(c), io.rn(i).req, port.rxReq, (f: ChiReqFlit) => f.qos)
        eject(new ChiRspFlit(c), port.txRsp, io.rn(i).rsp)
        eject(new ChiDatFlit(c), port.txDat, io.rn(i).dat)
        eject(new ChiSnpFlit(c), port.txSnp, io.rn(i).snp)
        inject(new ChiRspFlit(c), io.rn(i).txRsp, port.rxRsp, (f: ChiRspFlit) => f.qos)
        inject(new ChiDatFlit(c), io.rn(i).txDat, port.rxDat, (f: ChiDatFlit) => f.qos)
        port.rxSnp.valid := false.B; port.rxSnp.bits := 0.U.asTypeOf(vcSnp)
        port.txReq.ready := true.B
      } else if (ChiNodeKind.isSubordinate(n.kind)) {
        val j = snIndex(n.nodeId)
        eject(new ChiReqFlit(c), port.txReq, io.sn(j).req)
        inject(new ChiRspFlit(c), io.sn(j).rsp, port.rxRsp, (f: ChiRspFlit) => f.qos)
        inject(new ChiDatFlit(c), io.sn(j).dat, port.rxDat, (f: ChiDatFlit) => f.qos)
        port.rxReq.valid := false.B; port.rxReq.bits := 0.U.asTypeOf(vcReq)
        port.rxSnp.valid := false.B; port.rxSnp.bits := 0.U.asTypeOf(vcSnp)
        port.txRsp.ready := true.B; port.txDat.ready := true.B; port.txSnp.ready := true.B
      } else if (n.kind == ChiNodeKind.Mn) {
        val targets = p.rn.filter(_.kind == ChiNodeKind.RnD).map(_.nodeId)
        if (targets.nonEmpty) {
          val mn = Module(new ChiDvmMn(c, n.nodeId, targets))
          eject(new ChiReqFlit(c), port.txReq, mn.io.req)
          inject(new ChiRspFlit(c), mn.io.rsp, port.rxRsp, (f: ChiRspFlit) => f.qos)
          eject(new ChiDatFlit(c), port.txDat, mn.io.dat)
          inject(new ChiSnpFlit(c), mn.io.snp, port.rxSnp, (f: ChiSnpFlit) => f.qos)
          eject(new ChiRspFlit(c), port.txRsp, mn.io.snpResp)
          port.rxReq.valid := false.B; port.rxReq.bits := 0.U.asTypeOf(vcReq)
          port.rxDat.valid := false.B; port.rxDat.bits := 0.U.asTypeOf(vcDat)
          port.txSnp.ready := true.B
        } else {
          port.rxReq.valid := false.B; port.rxReq.bits := 0.U.asTypeOf(vcReq)
          port.rxRsp.valid := false.B; port.rxRsp.bits := 0.U.asTypeOf(vcRsp)
          port.rxSnp.valid := false.B; port.rxSnp.bits := 0.U.asTypeOf(vcSnp)
          port.rxDat.valid := false.B; port.rxDat.bits := 0.U.asTypeOf(vcDat)
          port.txReq.ready := true.B; port.txRsp.ready := true.B
          port.txSnp.ready := true.B; port.txDat.ready := true.B
        }
      } else {
        // HN-I placeholder / unknown kind: no logic, safe tie-off.
        port.rxReq.valid := false.B; port.rxReq.bits := 0.U.asTypeOf(vcReq)
        port.rxRsp.valid := false.B; port.rxRsp.bits := 0.U.asTypeOf(vcRsp)
        port.rxSnp.valid := false.B; port.rxSnp.bits := 0.U.asTypeOf(vcSnp)
        port.rxDat.valid := false.B; port.rxDat.bits := 0.U.asTypeOf(vcDat)
        port.txReq.ready := true.B; port.txRsp.ready := true.B
        port.txSnp.ready := true.B; port.txDat.ready := true.B
      }
    }
  }

  // Router-router links: cross-connect TX -> RX (VC sideband preserved).
  for (l <- p.links) {
    val a = routers(l.fromRouter).io.port(l.fromPort)
    val b = routers(l.toRouter).io.port(l.toPort)
    LmWire.link(a.txReq, b.rxReq); LmWire.link(b.txReq, a.rxReq)
    LmWire.link(a.txRsp, b.rxRsp); LmWire.link(b.txRsp, a.rxRsp)
    LmWire.link(a.txDat, b.rxDat); LmWire.link(b.txDat, a.rxDat)
    LmWire.link(a.txSnp, b.rxSnp); LmWire.link(b.txSnp, a.rxSnp)
  }
}