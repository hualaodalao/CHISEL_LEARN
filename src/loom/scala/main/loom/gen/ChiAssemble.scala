package loom.gen

import scala.collection.mutable

import loom.chi.{
  ChiAdapt, ChiConfig, ChiNetLink, ChiNetNode, ChiNetRouter, ChiNetworkParams, ChiNode, ChiNodeKind,
  ChiNodeIds, ChiParams, ChiPartitionParams, ChiQosGen, ChiSamEntry
}
import loom.graph.FabricGraph
import loom.ir.{Ir, IrV2, LinkV2, PartitionSpec}

/** GEN-side parameter assembly: normalized IrV2 -> CHI elaboration models.
  * Hardware keeps its v1 `fromIr` entry for legacy tests; GEN assembles the
  * case classes directly so explicit NodeID overrides and multi-window SAM
  * work without the legacy 1:1 constraints (HW-01/HW-02 closed).
  */
object ChiAssemble {
  def config(v2: IrV2): ChiConfig = {
    def threshold(className: String): Int =
      v2.qos.mapping.find(_.className == className).map(_.qosMin).getOrElse(
        throw Ir.IrException(s"qos.mapping has no segment for class '$className'"))
    val weightOf = v2.qos.classes.map(c => c.name -> c.weight).toMap
    ChiConfig(
      datWidth = v2.chi.datWidth,
      txnIdWidth = v2.chi.txnIdWidth,
      outstanding = v2.chi.outstanding,
      nodeIdWidth = v2.chi.nodeIdWidth,
      addrWidth = v2.chi.addrWidth,
      atomicEnable = v2.chi.atomicEnable,
      atomicEntries = v2.chi.atomicEntries,
      dvmEnable = v2.chi.dvmEnable,
      portMode = v2.chi.portMode,
      pcrdTypes = v2.chi.pcrdTypes,
      lcreditDepth = v2.chi.lcreditDepth,
      qosCtlThreshold = threshold("ctl"),
      qosLatThreshold = threshold("lat"),
      qosWeights = Seq("ctl", "lat", "bulk").map(weightOf),
      // AQ-02/GEN-22 (v1.2.0): nHalf is topology-derived — a dateline cut
      // needs both VC halves, an acyclic topology only half 0. vcWidth
      // follows via ChiConfig. FabricGraph reports invalid configs through
      // `errors` (datelines empty ⇒ nHalf=1); the catch keeps config() total
      // for direct unit calls on unvalidated IR.
      nHalf = {
        val hasDateline =
          try FabricGraph.fromV2(v2).datelines.nonEmpty
          catch { case _: Throwable => false }
        if (hasDateline) 2 else 1
      }
    )
  }

  private def kindOf(k: String): ChiNodeKind.Value = k match {
    case "rn-i" => ChiNodeKind.RnI
    case "rn-d" => ChiNodeKind.RnD
    case "sn-f" => ChiNodeKind.SnF
    case "sn-i" => ChiNodeKind.SnI
    // HW-01 closed: ChiNetworkTop wires MN through ChiDvmMn and gives HN-I a
    // port + routing placeholder (phase-1 ruling), so GEN populates both.
    case "hn-i" => ChiNodeKind.HnI
    case "mn"   => ChiNodeKind.Mn
    case other  =>
      throw Ir.IrException(
        s"node kind '$other' is not supported (hn-f is a reserved placeholder, CHK-05)")
  }

  /** SEM-02/SEM-03 + AQ-01: RN -> SN -> HN -> MN segments, declaration order
    * within a segment, explicit overrides reserved first (ChiNodeIds).
    */
  def nodeIds(v2: IrV2): Seq[Int] =
    ChiNodeIds.assign(v2.nodes.map(n => kindOf(n.kind)), v2.nodes.map(_.nodeId), v2.chi.nodeIdWidth)

  def chiParams(v2: IrV2): ChiParams = {
    val ids = nodeIds(v2)
    val idOf = v2.nodes.map(_.id).zip(ids).toMap
    val nodes = v2.nodes.zip(ids).map { case (n, id) => ChiNode(n.id, kindOf(n.kind), id, n.clock) }
    val rn = nodes.filter(n => ChiNodeKind.isRequest(n.kind))
    val sn = nodes.filter(n => ChiNodeKind.isSubordinate(n.kind))
    val sam = v2.sam.map(w =>
      ChiSamEntry(w.base, w.size, idOf.getOrElse(w.tgtNode,
        throw Ir.IrException(s"sam tgtNode '${w.tgtNode}' is not a declared node"))))
    ChiParams(v2.meta.name, config(v2), rn, sn, sam)
  }

  /** Router-to-router links in the GEN-05 deterministic order; the position
    * in this sequence is the global ChiNetLink id.
    */
  def routerLinks(v2: IrV2): Seq[LinkV2] = {
    val routerNames = v2.routers.map(_.name).toSet
    v2.links
      .filter(l => routerNames.contains(l.from) && routerNames.contains(l.to))
      .sortBy(l => (l.from, l.to))
  }

  /** Global link ids of the async cuts (cdc=async). After CHK-45/46/48 these
    * are exactly the partition boundary crossings; the wrapper instantiates
    * one ChiAsyncLink per channel there (HW-04).
    */
  def asyncCuts(v2: IrV2): Set[Int] =
    routerLinks(v2).zipWithIndex.collect { case (l, i) if l.cdc == "async" => i }.toSet

  /** GEN-01 stage 7: assemble ChiNetworkParams from the normalized v2 IR,
    * mirroring ChiNetworkParams.fromIr's algorithm (local-port layout, BFS
    * TgtID route tables, link port numbering) with IrV2 semantics:
    *  - NodeIDs via ChiNodeIds (explicit overrides honored, SEM-02/03);
    *  - node.attach addresses the router directly (MIG-05);
    *  - hn/mn nodes populated (HW-01); multi-window SAM legal (HW-02);
    *  - dateline half-flip flags derived from FabricGraph.fromV2 and passed
    *    per output port (AQ-02②: GEN derives, hardware consumes).
    */
  def networkParams(v2: IrV2): ChiNetworkParams = {
    val cfg = config(v2)
    val ids = nodeIds(v2)
    val routerNames = v2.routers.map(_.name).sorted
    val routerIndex = routerNames.zipWithIndex.toMap
    require(routerNames.nonEmpty, "need at least one router")

    val all = v2.nodes.zip(ids).map { case (n, id) =>
      ChiNetNode(id, kindOf(n.kind),
        routerIndex.getOrElse(n.attach,
          throw Ir.IrException(s"node '${n.id}' attaches to unknown router '${n.attach}'")), 0,
        protocol = n.protocol)
    }

    // Local port order: RN group then SN (then HN/MN) by NodeID (GEN-05).
    val localOf = mutable.Map.empty[Int, mutable.ArrayBuffer[ChiNetNode]]
    routerNames.indices.foreach(k => localOf(k) = mutable.ArrayBuffer.empty)
    all.sortBy(_.nodeId).foreach(n => localOf(n.router) += n)
    val withLocalPort = all.map(n => n.copy(localPort = localOf(n.router).indexOf(n)))
    val rnFinal = withLocalPort.filter(n => ChiNodeKind.isRequest(n.kind)).sortBy(_.nodeId)
    val snFinal = withLocalPort.filter(n => ChiNodeKind.isSubordinate(n.kind)).sortBy(_.nodeId)
    val hnFinal = withLocalPort.filter(n => n.kind == ChiNodeKind.HnI).sortBy(_.nodeId)
    val mnFinal = withLocalPort.filter(n => n.kind == ChiNodeKind.Mn).sortBy(_.nodeId)

    val graphLinks = routerLinks(v2)
    val outLinksOf = routerNames.indices.map(k => graphLinks.indices.filter(l => graphLinks(l).from == routerNames(k)).toSeq)
    val inLinksOf = routerNames.indices.map(k => graphLinks.indices.filter(l => graphLinks(l).to == routerNames(k)).toSeq)
    val incident = routerNames.indices.map(k => (outLinksOf(k) ++ inLinksOf(k)).distinct.sorted)

    // Link port index = nLocal + position among incident links.
    def linkPort(k: Int, linkId: Int): Int = localOf(k).size + incident(k).indexOf(linkId)

    val links = graphLinks.zipWithIndex.map { case (l, id) =>
      val from = routerIndex(l.from); val to = routerIndex(l.to)
      // SEM-14/HW-14: cdc=async fills the link-level async FIFO depth (the
      // flat ChiNetworkTop inserts one ChiAsyncLink per channel/direction);
      // sync links stay direct (depth 0).
      ChiNetLink(id, from, to, linkPort(from, id), linkPort(to, id), l.pipe,
        asyncFifoDepth = if (l.cdc == "async") l.asyncFifoDepth else 0)
    }

    // Router adjacency for BFS (bidirectional links, SEM-13).
    val adj = routerNames.indices.map { k =>
      incident(k).map { id =>
        val l = graphLinks(id)
        val other = if (routerIndex(l.from) == k) routerIndex(l.to) else routerIndex(l.from)
        (other, id)
      }.sortBy(_._1)
    }
    def firstHop(from: Int, to: Int): Option[Int] = {
      if (from == to) return None
      val seen = mutable.Set(from)
      val queue = mutable.Queue.empty[(Int, Int)]
      adj(from).foreach { case (o, id) => seen += o; queue.enqueue((o, id)) }
      while (queue.nonEmpty) {
        val (node, first) = queue.dequeue()
        if (node == to) return Some(first)
        adj(node).foreach { case (o, id) => if (!seen.contains(o)) { seen += o; queue.enqueue((o, first)) } }
      }
      None
    }

    val nodeRouter = withLocalPort.map(n => n.nodeId -> n.router).toMap
    val nodeLocalPort = withLocalPort.map(n => n.nodeId -> n.localPort).toMap
    val maxNodes = cfg.maxNodes

    // Dateline cuts per (router, outgoing direction): the hardware flips the
    // VC half 0->1 only on marked output ports (AQ-02②).
    val datelines = FabricGraph.fromV2(v2).datelines.toSet

    val routeTables = routerNames.indices.map { k =>
      val route = Array.fill(maxNodes)(0)
      for (nid <- 0 until maxNodes if nodeRouter.contains(nid)) {
        val owner = nodeRouter(nid)
        route(nid) =
          if (owner == k) nodeLocalPort(nid)
          else firstHop(k, owner).map(id => linkPort(k, id)).getOrElse(0)
      }
      route
    }

    // GEN-21/SEM-17 (UP-01): explicit paths override the BFS fill. Forward:
    // each path router p_i (i < last) routes the target SN via p_i -> p_i+1;
    // reverse: each p_i (i > 0) routes the source RN via p_i -> p_i-1 (the
    // reversed path). Endpoint local-port entries stay as BFS set them.
    // forbid entries never touch the route tables: since v1.2.0 they execute
    // through the per-source SAM below (CHK-51 retired).
    val idOf = v2.nodes.map(_.id).zip(ids).toMap
    def hopLink(a: Int, b: Int): Int =
      graphLinks.indices.find { id =>
        val l = graphLinks(id)
        val f = routerIndex(l.from); val t = routerIndex(l.to)
        (f == a && t == b) || (f == b && t == a)
      }.getOrElse(throw Ir.IrException(
        s"explicit route hop '${routerNames(a)}'->'${routerNames(b)}' has no link (CHK-50)"))
    v2.routes.filter(r => !r.forbid && r.path.nonEmpty).foreach { r =>
      val pathIdx = r.path.map(p => routerIndex.getOrElse(p,
        throw Ir.IrException(s"explicit route path router '$p' is not declared (CHK-49)")))
      val toId = idOf.getOrElse(r.to, throw Ir.IrException(s"route to '${r.to}' is not a declared node"))
      val fromId = idOf.getOrElse(r.from, throw Ir.IrException(s"route from '${r.from}' is not a declared node"))
      pathIdx.indices.foreach { i =>
        if (i < pathIdx.length - 1)
          routeTables(pathIdx(i))(toId) = linkPort(pathIdx(i), hopLink(pathIdx(i), pathIdx(i + 1)))
        if (i > 0)
          routeTables(pathIdx(i))(fromId) = linkPort(pathIdx(i), hopLink(pathIdx(i), pathIdx(i - 1)))
      }
    }

    val routers = routerNames.indices.map { k =>
      val linkOfPort = incident(k).map(id => id)
      val outCut = Seq.fill(localOf(k).size)(false) ++ incident(k).map { id =>
        val l = graphLinks(id)
        val peer = if (l.from == routerNames(k)) l.to else l.from
        datelines.contains(s"${routerNames(k)}->$peer")
      }
      ChiNetRouter(k, routerNames(k), localOf(k).size, incident(k).size, routeTables(k).toSeq, linkOfPort, outCut)
    }

    // GEN-21/SEM-05 (HW-13/A4): the global SAM window table (the chiParams
    // entries: window base/size + target NodeID) drives addr-based decode at
    // the RN entry with NDERR on miss. When forbid pairs exist (SEM-17
    // v1.2.0: executed, CHK-51 retired) each RN gets its own table in rnFinal
    // order — the global windows minus those targeting an SN forbidden for
    // that RN; an RN without forbids keeps the full global table. No forbids
    // ⇒ samByRn stays empty and the hardware uses the global sam.
    val sam = v2.sam.map(w => ChiSamEntry(w.base, w.size,
      idOf.getOrElse(w.tgtNode,
        throw Ir.IrException(s"sam tgtNode '${w.tgtNode}' is not a declared node"))))
    val nameOfId = v2.nodes.map(_.id).zip(ids).map { case (n, i) => i -> n }.toMap
    val forbiddenTargets: Map[String, Set[Int]] =
      v2.routes.filter(_.forbid).groupBy(_.from).map { case (rn, rs) =>
        rn -> rs.map(r => idOf.getOrElse(r.to,
          throw Ir.IrException(s"route to '${r.to}' is not a declared node"))).toSet
      }
    val samByRn: Seq[Seq[ChiSamEntry]] =
      if (forbiddenTargets.isEmpty) Seq.empty
      else rnFinal.map { n =>
        val banned = forbiddenTargets.getOrElse(nameOfId(n.nodeId), Set.empty[Int])
        sam.filterNot(e => banned.contains(e.tgtId))
      }

    // SEM-14/HW-14 clock-domain mapping (deterministic): router.clock maps to
    // its DECLARATION INDEX in v2.clockDomains — domain 0 = clockDomains.head
    // (the top module clock), domain k = clockDomains(k). routerDomain follows
    // the GEN-05 router order (name-sorted). ChiNetworkTop clocks each router
    // withClockAndReset and exposes io.domClk/domReset for domains 1..n-1;
    // local nodes must attach to domain-0 routers (CHK-54, HW-14 first version).
    val domainOfClock = v2.clockDomains.map(_.id).zipWithIndex.toMap
    val clockOfRouter = v2.routers.map(r => r.name -> r.clock).toMap
    val routerDomain = routerNames.map { n =>
      val clk = clockOfRouter(n)
      domainOfClock.getOrElse(clk,
        throw Ir.IrException(s"router '$n' clock '$clk' is not declared"))
    }

    ChiNetworkParams(v2.meta.name, cfg, rnFinal, snFinal, routers, links,
      snpEnable = v2.chi.dvmEnable, queueDepth = cfg.lcreditDepth, hn = hnFinal, mn = mnFinal,
      sam = sam, samByRn = samByRn,
      routerDomain = routerDomain,
      // HW-10/SEM-18: mirror the GEN declaration params into the chi-side
      // case classes; ChiNetworkTop strings the token-bucket regulator into
      // the RN injection path by nodeId.
      qosGen = qosGenParams(v2).map(q => ChiQosGen(q.nodeId, q.kind, q.bytesPerCycle, q.saturationBytes)),
      // HW-12/SEM-19: mirror the adaptation rows; GEN instantiates ChiNiAdapt
      // at mismatched chi-node DAT boundaries (ChiAdaptedNetworkTop).
      adapt = adaptParams(v2).map(a => ChiAdapt(a.nodeId, a.dataWidth, a.datWidth, a.fifoDepth, a.mismatch)))
  }

  /** GEN-21 (UP-01): one RN x SN reachability row derived from the FINAL
    * route tables (BFS + explicit overrides). `path` lists the router names
    * traversed for a reachable pair; unreachable pairs carry an empty path.
    */
  final case class ConnectivityRow(from: String, to: String, reachable: Boolean, path: Seq[String])

  /** Walk the assembled TgtID route tables from each RN's attach router;
    * bounded by the router count, a revisited router (loop) ⇒ unreachable.
    */
  def connectivity(v2: IrV2, params: ChiNetworkParams): Seq[ConnectivityRow] = {
    val routerOf = params.routers.map(r => r.index -> r).toMap
    val all = params.rn ++ params.sn ++ params.hn ++ params.mn
    val nameOfId = v2.nodes.map(_.id).zip(nodeIds(v2)).map { case (n, i) => i -> n }.toMap
    val netOf = all.map(n => nameOfId(n.nodeId) -> n).toMap
    // Local-port occupants per router: NodeID order (GEN-05 local layout).
    val localOccupant: Map[(Int, Int), Int] =
      all.groupBy(_.router).flatMap { case (k, ns) =>
        ns.sortBy(_.nodeId).zipWithIndex.map { case (n, p) => (k, p) -> n.nodeId }
      }
    def walk(src: ChiNetNode, dst: ChiNetNode): Option[Seq[String]] = {
      var cur = src.router
      val trail = mutable.ArrayBuffer.empty[String]
      val seen = mutable.Set.empty[Int]
      var steps = 0
      while (steps <= params.routers.size) {
        if (!seen.add(cur)) return None // loop ⇒ unreachable
        val r = routerOf(cur)
        trail += r.name
        val port = r.route(dst.nodeId)
        if (port < 0 || port >= r.nPort) return None
        if (port < r.nLocal)
          return if (localOccupant.get((cur, port)).contains(dst.nodeId)) Some(trail.toSeq) else None
        val linkId = r.linkOfPort(port - r.nLocal)
        val linkOpt = params.links.find(_.id == linkId)
        if (linkOpt.isEmpty) return None
        val link = linkOpt.get
        cur = if (link.fromRouter == cur) link.toRouter else link.fromRouter
        steps += 1
      }
      None
    }
    val rns = v2.nodes.filter(n => ChiNodeKind.isRequest(kindOf(n.kind)))
    val sns = v2.nodes.filter(n => ChiNodeKind.isSubordinate(kindOf(n.kind)))
    // SEM-17 (v1.2.0): forbid pairs execute at the source NI (per-source SAM
    // miss ⇒ NDERR, never enters the fabric), so they report unreachable with
    // an empty path even when the topology connects them.
    val forbiddenPairs = v2.routes.filter(_.forbid).map(r => (r.from, r.to)).toSet
    for (rn <- rns; sn <- sns) yield {
      val path =
        if (forbiddenPairs.contains((rn.id, sn.id))) None
        else walk(netOf(rn.id), netOf(sn.id))
      ConnectivityRow(rn.id, sn.id, path.isDefined, path.getOrElse(Nil))
    }
  }

  /** Partition assembly (HW-06): router members map to full-network indices;
    * node members follow their attach router inside ChiPartitionParams.
    * HW-08 closed (v1.2.0/GEN-22): ChiPartitionTop has MN/DVM parity with the
    * network top, so rn-d/mn/hn-i members are legal — the former guard is
    * lifted.
    */
  def partitionParams(v2: IrV2, full: ChiNetworkParams): Seq[(PartitionSpec, ChiPartitionParams)] = {
    val idxOf = full.routers.map(r => r.name -> r.index).toMap
    v2.partitions.map { spec =>
      val members = spec.members.filter(idxOf.contains).map(idxOf)
      (spec, ChiPartitionParams(spec.name, full, members, spec.boundaryPipe))
    }
  }

  /** GEN-23 (UP-02, SEM-18): assembled qosGen parameters, consumed by HW-10
    * (ChiQosRegulator token bucket at the RN injection path, wave4). Scope:
    * regulation at RN injection only — no end-to-end bandwidth guarantee.
    */
  final case class QosGenParams(nodeId: Int, kind: String, bytesPerCycle: Double, saturationBytes: Int)

  /** GEN-24 (UP-08, SEM-19): frozen NIU store&fwd adaptation parameters,
    * consumed by HW-12 (ChiNiWidth gearbox + ChiNiAdapt, wave4). GEN
    * instantiates ChiNiAdapt at mismatched chi-node DAT boundaries; AXI
    * boundary width adaptation stays with the bridge layer (PS §5 F8).
    */
  final case class AdaptParams(
      nodeId: Int,
      dataWidth: Int,
      datWidth: Int,
      clock: String,
      routerClock: String,
      fifoDepth: Int,
      mismatch: Boolean)

  /** SEM-18 conversion: bytesPerCycle = bandwidth(MB/s) / freqMHz of the
    * node clock (1 MB/s ÷ 1 MHz = 1 B/cycle). Only nodes declaring qosGen
    * appear; NodeID order (GEN-05).
    */
  def qosGenParams(v2: IrV2): Seq[QosGenParams] = {
    val ids = nodeIds(v2)
    val freqOf = v2.clockDomains.map(c => c.id -> c.freqMHz).toMap
    v2.nodes.zip(ids).sortBy(_._2).flatMap { case (n, id) =>
      n.qosGen.map { q =>
        val freqMHz = freqOf.getOrElse(n.clock,
          throw Ir.IrException(s"node '${n.id}' clock '${n.clock}' is not declared"))
        QosGenParams(id, q.kind, q.bandwidth.toDouble / freqMHz, q.saturation)
      }
    }
  }

  /** SEM-19 mismatch list + frozen FIFO depths: a node needs store&fwd
    * adaptation when dataWidth != chi.datWidth or node.clock != attach
    * router.clock. Rows cover every mismatched node plus any node with an
    * explicit adaptFifoDepth freeze; the IR-79 default 4 is applied here
    * (never in the IR rendering). Empty when nothing is declared/mismatched.
    */
  def adaptParams(v2: IrV2): Seq[AdaptParams] = {
    val ids = nodeIds(v2)
    val routerClock = v2.routers.map(r => r.name -> r.clock).toMap
    v2.nodes.zip(ids).sortBy(_._2).flatMap { case (n, id) =>
      val rc = routerClock.getOrElse(n.attach,
        throw Ir.IrException(s"node '${n.id}' attach '${n.attach}' is not a declared router"))
      val mismatch = n.dataWidth != v2.chi.datWidth || n.clock != rc
      if (mismatch || n.adaptFifoDepth.isDefined)
        Some(AdaptParams(id, n.dataWidth, v2.chi.datWidth, n.clock, rc,
          n.adaptFifoDepth.getOrElse(4), mismatch))
      else None
    }
  }

  /** GEN-25 (UP-07, SEM-20): each subsystem projects to an independent flat
    * IrV2 (meta.name = subsystem name; clockDomains/qos/chi defaults already
    * resolved by the parser; partitions/routes empty per phase-1 CHK-58) and
    * goes through the existing networkParams path unchanged. Hierarchical
    * emission is pending HW-11; check/estimate/API consume these params.
    */
  def subsystemParams(v2: IrV2): Seq[(String, ChiNetworkParams)] =
    v2.subsystems.map(s => s.name -> networkParams(IrV2.projectSubsystem(v2, s)))

  /** GEN-25 (UP-07): frozen inter-NoC bridge parameters for HW-11
    * (ChiNocBridge): protocol proxy (TxnID/NodeID remap), window forwarding,
    * cross-clock/width adaptation, store&fwd flow-control isolation and the
    * DVM policy. fromDatWidth/toDatWidth are the two subsystems' chi.datWidth
    * tiers (they may differ; the bridge adapts).
    */
  final case class BridgeParams(
      fromSub: String,
      toSub: String,
      fromRouter: String,
      toRouter: String,
      windows: Seq[(Long, Long)],
      asyncFifoDepth: Int,
      dvmForward: Boolean,
      fromDatWidth: Int,
      toDatWidth: Int)

  def bridgeParams(v2: IrV2): Seq[BridgeParams] = {
    val subByName = v2.subsystems.map(s => s.name -> s).toMap
    v2.nocLinks.map { l =>
      val f = subByName.getOrElse(l.from,
        throw Ir.IrException(s"nocLink from '${l.from}' is not a declared subsystem (CHK-55)"))
      val t = subByName.getOrElse(l.to,
        throw Ir.IrException(s"nocLink to '${l.to}' is not a declared subsystem (CHK-55)"))
      BridgeParams(l.from, l.to, l.fromRouter, l.toRouter, l.windows,
        l.asyncFifoDepth, l.dvmForward, f.chi.datWidth, t.chi.datWidth)
    }
  }
}