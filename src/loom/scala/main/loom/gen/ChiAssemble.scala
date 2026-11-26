package loom.gen

import scala.collection.mutable

import loom.chi.{
  ChiConfig, ChiNetLink, ChiNetNode, ChiNetRouter, ChiNetworkParams, ChiNode, ChiNodeKind,
  ChiNodeIds, ChiParams, ChiPartitionParams, ChiSamEntry
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
      nHalf = 2
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
          throw Ir.IrException(s"node '${n.id}' attaches to unknown router '${n.attach}'")), 0)
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
      ChiNetLink(id, from, to, linkPort(from, id), linkPort(to, id), l.pipe)
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

    val routers = routerNames.indices.map { k =>
      val route = Array.fill(maxNodes)(0)
      for (nid <- 0 until maxNodes if nodeRouter.contains(nid)) {
        val owner = nodeRouter(nid)
        route(nid) =
          if (owner == k) nodeLocalPort(nid)
          else firstHop(k, owner).map(id => linkPort(k, id)).getOrElse(0)
      }
      val linkOfPort = incident(k).map(id => id)
      val outCut = Seq.fill(localOf(k).size)(false) ++ incident(k).map { id =>
        val l = graphLinks(id)
        val peer = if (l.from == routerNames(k)) l.to else l.from
        datelines.contains(s"${routerNames(k)}->$peer")
      }
      ChiNetRouter(k, routerNames(k), localOf(k).size, incident(k).size, route.toSeq, linkOfPort, outCut)
    }

    ChiNetworkParams(v2.meta.name, cfg, rnFinal, snFinal, routers, links,
      snpEnable = v2.chi.dvmEnable, queueDepth = cfg.lcreditDepth, hn = hnFinal, mn = mnFinal)
  }

  /** Partition assembly (HW-06): router members map to full-network indices;
    * node members follow their attach router inside ChiPartitionParams.
    */
  def partitionParams(v2: IrV2, full: ChiNetworkParams): Seq[(PartitionSpec, ChiPartitionParams)] = {
    // ChiPartitionTop has no MN/DVM logic and drops hn-i placeholders (HW-08);
    // refuse rather than silently emit a partitioned fabric without them.
    val unsupported = v2.nodes.filter(n =>
      n.kind == "rn-d" || n.kind == "mn" || n.kind == "hn-i")
    if (v2.partitions.nonEmpty && unsupported.nonEmpty)
      throw Ir.IrException(
        s"partitioned emission with ${unsupported.map(n => s"${n.kind} '${n.id}'").mkString(", ")} " +
          "requires HW-08 (ChiPartitionTop MN/DVM wiring); use a flat config or remove these nodes")
    val idxOf = full.routers.map(r => r.name -> r.index).toMap
    v2.partitions.map { spec =>
      val members = spec.members.filter(idxOf.contains).map(idxOf)
      (spec, ChiPartitionParams(spec.name, full, members, spec.boundaryPipe))
    }
  }
}