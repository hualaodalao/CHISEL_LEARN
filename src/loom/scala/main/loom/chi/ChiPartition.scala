package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.{LmWire, LmPipe}

/** One partition of a CHI network: a subset of routers (sharing one clock),
  * their local nodes and internal links. Router-to-router links that leave or
  * enter the subset become registered boundary ports (HW-06). Route tables are
  * inherited from the full network, so a router routing to a remote node simply
  * drives the boundary port at that link position.
  */
final case class ChiPartitionParams(
    name: String,
    full: ChiNetworkParams,
    members: Seq[Int],
    pipe: Int
) {
  require(members.nonEmpty, s"partition '$name' has no router")
  require(pipe >= 1, "boundary pipe must be >= 1 stage")
  private val memberSet = members.toSet
  val cfg: ChiConfig = full.cfg
  val routers: Seq[ChiNetRouter] = full.routers.filter(r => memberSet.contains(r.index))
  val nodes: Seq[ChiNetNode] = full.allNodes.filter(n => memberSet.contains(n.router))
  val internalLinks: Seq[ChiNetLink] =
    full.links.filter(l => memberSet.contains(l.fromRouter) && memberSet.contains(l.toRouter))
  val outLinks: Seq[ChiNetLink] =
    full.links.filter(l => memberSet.contains(l.fromRouter) && !memberSet.contains(l.toRouter))
  val inLinks: Seq[ChiNetLink] =
    full.links.filter(l => !memberSet.contains(l.fromRouter) && memberSet.contains(l.toRouter))
  val boundary: Seq[ChiNetLink] = (outLinks ++ inLinks).sortBy(_.id)
  def rn: Seq[ChiNetNode] = nodes.filter(n => ChiNodeKind.isRequest(n.kind))
  def sn: Seq[ChiNetNode] = nodes.filter(n => ChiNodeKind.isSubordinate(n.kind))
  def snpEnable: Boolean = full.snpEnable
  def queueDepth: Int = full.queueDepth
  /** Local router port index for a boundary link (the endpoint inside this partition). */
  def boundaryPort(l: ChiNetLink): (Int, Int) =
    if (memberSet.contains(l.fromRouter)) (l.fromRouter, l.fromPort)
    else (l.toRouter, l.toPort)
}

/** Real partition top: local node ports + registered boundary CHI ports. */
class ChiPartitionTop(pp: ChiPartitionParams) extends Module {
  private val c = pp.cfg
  private val qm = new ChiQosMap(c)
  private val vw = c.vcWidth

  val io = IO(new Bundle {
    val rn = Vec(pp.rn.length, Flipped(new ChiRnPort(c)))
    val sn = Vec(pp.sn.length, Flipped(new ChiSnPort(c)))
    val boundary = Vec(pp.boundary.length, new ChiRouterPort(c))
  })

  private def inject[T <: Data](gen: T, src: DecoupledIO[T], dst: DecoupledIO[ChiVcBundle[T]], qosOf: T => UInt): Unit = {
    val a = Module(new ChiVcInject(gen, qm, qosOf)); LmWire.link(src, a.io.in); LmWire.link(a.io.out, dst)
  }
  private def eject[T <: Data](gen: T, src: DecoupledIO[ChiVcBundle[T]], dst: DecoupledIO[T]): Unit = {
    val a = Module(new ChiVcEject(gen, vw)); LmWire.link(src, a.io.in); LmWire.link(a.io.out, dst)
  }
  private def vcReq = new ChiVcBundle(new ChiReqFlit(c), vw)
  private def vcRsp = new ChiVcBundle(new ChiRspFlit(c), vw)
  private def vcSnp = new ChiVcBundle(new ChiSnpFlit(c), vw)
  private def vcDat = new ChiVcBundle(new ChiDatFlit(c), vw)

  private val routerOf = pp.routers.map { r =>
    r.index -> Module(new ChiRouter4(c, r.nPort, r.route, r.route, r.route, r.route,
      depth = pp.queueDepth, snpEnable = pp.snpEnable, cutOut = r.outCut))
  }.toMap
  private val rnIndex = pp.rn.zipWithIndex.map { case (n, i) => n.nodeId -> i }.toMap
  private val snIndex = pp.sn.zipWithIndex.map { case (n, i) => n.nodeId -> i }.toMap
  private val localNodes = pp.routers.map { r =>
    pp.nodes.filter(_.router == r.index).sortBy(_.localPort)
  }

  for (r <- pp.routers) {
    val rt = routerOf(r.index)
    val ln = pp.nodes.filter(_.router == r.index).sortBy(_.localPort)
    for (pIdx <- ln.indices) {
      val n = ln(pIdx)
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
        eject(new ChiDatFlit(c), port.txDat, io.sn(j).rxDat)
        port.rxReq.valid := false.B; port.rxReq.bits := 0.U.asTypeOf(vcReq)
        port.rxSnp.valid := false.B; port.rxSnp.bits := 0.U.asTypeOf(vcSnp)
        port.txRsp.ready := true.B; port.txSnp.ready := true.B
      } else if (n.kind == ChiNodeKind.Mn) {
        val targets = pp.full.rn.filter(_.kind == ChiNodeKind.RnD).map(_.nodeId)
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

  for (l <- pp.internalLinks) {
    val a = routerOf(l.fromRouter).io.port(l.fromPort)
    val b = routerOf(l.toRouter).io.port(l.toPort)
    LmWire.link(a.txReq, b.rxReq); LmWire.link(b.txReq, a.rxReq)
    LmWire.link(a.txRsp, b.rxRsp); LmWire.link(b.txRsp, a.rxRsp)
    LmWire.link(a.txDat, b.rxDat); LmWire.link(b.txDat, a.rxDat)
    LmWire.link(a.txSnp, b.rxSnp); LmWire.link(b.txSnp, a.rxSnp)
  }

  // Boundary links: register every channel with >= 1 pipe stage to the port.
  for ((l, bi) <- pp.boundary.zipWithIndex) {
    val (ri, pi) = pp.boundaryPort(l)
    val rp = routerOf(ri).io.port(pi)
    val bp = io.boundary(bi)
    def pipeTx[T <: Data](gen: T, src: DecoupledIO[ChiVcBundle[T]], dst: DecoupledIO[ChiVcBundle[T]]): Unit = {
      val p = Module(new LmPipe(new ChiVcBundle(gen, vw), pp.pipe)); LmWire.link(src, p.io.in); LmWire.link(p.io.out, dst)
    }
    pipeTx(new ChiReqFlit(c), rp.txReq, bp.txReq); pipeTx(new ChiReqFlit(c), bp.rxReq, rp.rxReq)
    pipeTx(new ChiRspFlit(c), rp.txRsp, bp.txRsp); pipeTx(new ChiRspFlit(c), bp.rxRsp, rp.rxRsp)
    pipeTx(new ChiDatFlit(c), rp.txDat, bp.txDat); pipeTx(new ChiDatFlit(c), bp.rxDat, rp.rxDat)
    pipeTx(new ChiSnpFlit(c), rp.txSnp, bp.txSnp); pipeTx(new ChiSnpFlit(c), bp.rxSnp, rp.rxSnp)
  }
}

/** Peer placeholder with the same port contract as a real partition: node ports
  * sink/source nothing, boundary ports are safely tied off. Used to elaborate or
  * simulate one partition in isolation.
  */
class ChiPartitionStub(pp: ChiPartitionParams) extends Module {
  private val c = pp.cfg
  private val vw = c.vcWidth
  val io = IO(new Bundle {
    val rn = Vec(pp.rn.length, Flipped(new ChiRnPort(c)))
    val sn = Vec(pp.sn.length, Flipped(new ChiSnPort(c)))
    val boundary = Vec(pp.boundary.length, new ChiRouterPort(c))
  })
  for (i <- 0 until pp.rn.length) {
    io.rn(i).req.ready := true.B
    io.rn(i).rsp.valid := false.B; io.rn(i).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    io.rn(i).dat.valid := false.B; io.rn(i).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    io.rn(i).snp.valid := false.B; io.rn(i).snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    io.rn(i).txRsp.ready := true.B; io.rn(i).txDat.ready := true.B
  }
  for (j <- 0 until pp.sn.length) {
    io.sn(j).req.valid := false.B; io.sn(j).req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
    io.sn(j).rsp.ready := true.B; io.sn(j).dat.ready := true.B
    io.sn(j).rxDat.valid := false.B; io.sn(j).rxDat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
  }
  for (b <- 0 until pp.boundary.length) {
    val bp = io.boundary(b)
    bp.rxReq.ready := true.B
    bp.rxRsp.ready := true.B
    bp.rxSnp.ready := true.B
    bp.rxDat.ready := true.B
    bp.txReq.valid := false.B; bp.txReq.bits := 0.U.asTypeOf(new ChiVcBundle(new ChiReqFlit(c), vw))
    bp.txRsp.valid := false.B; bp.txRsp.bits := 0.U.asTypeOf(new ChiVcBundle(new ChiRspFlit(c), vw))
    bp.txSnp.valid := false.B; bp.txSnp.bits := 0.U.asTypeOf(new ChiVcBundle(new ChiSnpFlit(c), vw))
    bp.txDat.valid := false.B; bp.txDat.bits := 0.U.asTypeOf(new ChiVcBundle(new ChiDatFlit(c), vw))
  }
}

/** Reconnect partitions into the full network by cross-connecting matching
  * boundary ports. Async boundary cuts insert one ChiAsyncLink per channel
  * (HW-04); sync cuts wire directly. Boundary links are matched by global id.
  */
class ChiWrapperTop(full: ChiNetworkParams, parts: Seq[ChiPartitionParams], asyncCuts: Set[Int] = Set.empty) extends Module {
  private val c = full.cfg
  val io = IO(new Bundle {
    val rn = Vec(full.nRn, Flipped(new ChiRnPort(c)))
    val sn = Vec(full.nSn, Flipped(new ChiSnPort(c)))
  })

  private val tops = parts.map(pp => Module(new ChiPartitionTop(pp)))
  private val rnIndex = full.rn.zipWithIndex.map { case (n, i) => n.nodeId -> i }.toMap
  private val snIndex = full.sn.zipWithIndex.map { case (n, i) => n.nodeId -> i }.toMap

  // Attach local node ports to the partition that owns them.
  for ((pp, ti) <- parts.zipWithIndex) {
    val t = tops(ti)
    for ((n, li) <- pp.rn.zipWithIndex) {
      val g = rnIndex(n.nodeId)
      LmWire.link(io.rn(g).req, t.io.rn(li).req)
      LmWire.link(t.io.rn(li).rsp, io.rn(g).rsp)
      LmWire.link(t.io.rn(li).dat, io.rn(g).dat)
      LmWire.link(t.io.rn(li).snp, io.rn(g).snp)
      LmWire.link(io.rn(g).txRsp, t.io.rn(li).txRsp)
      LmWire.link(io.rn(g).txDat, t.io.rn(li).txDat)
    }
    for ((n, li) <- pp.sn.zipWithIndex) {
      val g = snIndex(n.nodeId)
      LmWire.link(t.io.sn(li).req, io.sn(g).req)
      LmWire.link(io.sn(g).rsp, t.io.sn(li).rsp)
      LmWire.link(io.sn(g).dat, t.io.sn(li).dat)
      LmWire.link(t.io.sn(li).rxDat, io.sn(g).rxDat)
    }
  }

  // Match boundary ports by global link id and cross-connect.
  private val done = scala.collection.mutable.Set.empty[Int]
  for (l <- full.links) {
    // A boundary link appears in exactly two partitions (out side / in side).
    val owners = parts.zipWithIndex.flatMap { case (pp, ti) =>
      pp.boundary.zipWithIndex.filter(_._1.id == l.id).map { case (_, bi) => (ti, bi) }
    }
    if (owners.length == 2 && !done.contains(l.id)) {
      done += l.id
      val (ta, ba) = owners.head
      val (tb, bb) = owners(1)
      val pa = tops(ta).io.boundary(ba)
      val pb = tops(tb).io.boundary(bb)
      if (asyncCuts.contains(l.id)) {
        def async[T <: Data](gen: T, src: DecoupledIO[ChiVcBundle[T]], dst: DecoupledIO[ChiVcBundle[T]]): Unit = {
          val al = Module(new ChiAsyncLink(new ChiVcBundle(gen, c.vcWidth), 4))
          al.io.dstClk := clock.asBool; al.io.dstReset := reset.asBool
          LmWire.link(src, al.io.in); LmWire.link(al.io.out, dst)
        }
        async(new ChiReqFlit(c), pa.txReq, pb.rxReq); async(new ChiReqFlit(c), pb.txReq, pa.rxReq)
        async(new ChiRspFlit(c), pa.txRsp, pb.rxRsp); async(new ChiRspFlit(c), pb.txRsp, pa.rxRsp)
        async(new ChiDatFlit(c), pa.txDat, pb.rxDat); async(new ChiDatFlit(c), pb.txDat, pa.rxDat)
        async(new ChiSnpFlit(c), pa.txSnp, pb.rxSnp); async(new ChiSnpFlit(c), pb.txSnp, pa.rxSnp)
      } else {
        LmWire.link(pa.txReq, pb.rxReq); LmWire.link(pb.txReq, pa.rxReq)
        LmWire.link(pa.txRsp, pb.rxRsp); LmWire.link(pb.txRsp, pa.rxRsp)
        LmWire.link(pa.txDat, pb.rxDat); LmWire.link(pb.txDat, pa.rxDat)
        LmWire.link(pa.txSnp, pb.rxSnp); LmWire.link(pb.txSnp, pa.rxSnp)
      }
    }
  }
}