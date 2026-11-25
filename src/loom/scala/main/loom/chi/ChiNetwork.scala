package loom.chi

import loom.ir.Ir
import scala.collection.mutable

/** QoS class -> internal VC mapping, parameterized by ChiConfig (HQ-01/AQ-02).
  * CHI flits carry QoS[4]; thresholds split it into ctl/lat/bulk classes. The
  * VC number generalizes to `vc = classIndex*nHalf + half` where nHalf is
  * topology-derived (GEN passes it; ring dateline = 2, future mesh may differ).
  * vcWidth = log2Ceil(nClasses*nHalf). Weights/thresholds come from cfg (GEN
  * assembles them from the IR qos block, the single source of truth).
  */
class ChiQosMap(c: ChiConfig) {
  val classes: Seq[String] = Seq("ctl", "lat", "bulk")
  val nHalf: Int = c.nHalf
  val nVc: Int = c.nVc
  val vcWidth: Int = c.vcWidth
  val ctlThreshold: Int = c.qosCtlThreshold
  val latThreshold: Int = c.qosLatThreshold
  def classOf(qos: Int): Int =
    if (qos >= c.qosCtlThreshold) 0 else if (qos >= c.qosLatThreshold) 1 else 2
  def vcOf(qos: Int, half: Int): Int = classOf(qos) * nHalf + half
  def weightOf(cls: Int): Int = c.qosWeights(cls)
}

/** One router-to-router link in the CHI network. */
final case class ChiNetLink(id: Int, fromRouter: Int, toRouter: Int, fromPort: Int, toPort: Int, pipe: Int)

/** A node attached to a router local port. */
final case class ChiNetNode(nodeId: Int, kind: ChiNodeKind.Value, router: Int, localPort: Int)

/** Per-router port layout and a TgtID -> port route table (class-independent). */
final case class ChiNetRouter(index: Int, name: String, nLocal: Int, nLink: Int, route: Seq[Int], linkOfPort: Seq[Int], outCut: Seq[Boolean] = Seq.empty) {
  def nPort: Int = nLocal + nLink
}

final case class ChiNetworkParams(
    name: String,
    cfg: ChiConfig,
    rn: Seq[ChiNetNode],
    sn: Seq[ChiNetNode],
    routers: Seq[ChiNetRouter],
    links: Seq[ChiNetLink],
    snpEnable: Boolean,
    queueDepth: Int = 2,
    hn: Seq[ChiNetNode] = Seq.empty,
    mn: Seq[ChiNetNode] = Seq.empty
) {
  def nRn: Int = rn.length
  def nSn: Int = sn.length
  def nHn: Int = hn.length
  def nMn: Int = mn.length
  def allNodes: Seq[ChiNetNode] = rn ++ sn ++ hn ++ mn
}

object ChiNetworkParams {
  def fromIr(ir: Ir, cfg: ChiConfig = ChiConfig(), snpEnable: Boolean = true): ChiNetworkParams = {
    val base = ChiParams.fromIr(ir, cfg)
    val routerNames = ir.routers.map(_.name).sorted
    val routerIndex = routerNames.zipWithIndex.toMap
    require(routerNames.length >= 1, "need at least one router")

    val mnetNames = ir.mnets.map(_.name).toSet
    val snetNames = ir.snets.map(_.name).toSet
    def mnetRouter(m: String): Int = {
      val ls = ir.links.filter(_.from == m)
      require(ls.length == 1, s"mnet '$m' needs one outgoing link")
      routerIndex(ls.head.to)
    }
    def snetRouter(s: String): Int = {
      val ls = ir.links.filter(_.to == s)
      require(ls.length == 1, s"snet '$s' needs one incoming link")
      routerIndex(ls.head.from)
    }

    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")
    val rn = base.rnNodes.zip(masters).map { case (n, e) =>
      require(mnetNames.contains(e.attach), s"master '${e.id}' attaches to unknown mnet")
      ChiNetNode(n.nodeId, n.kind, mnetRouter(e.attach), 0)
    }
    val sn = base.snNodes.zip(slaves).map { case (n, e) =>
      require(snetNames.contains(e.attach), s"slave '${e.id}' attaches to unknown snet")
      ChiNetNode(n.nodeId, n.kind, snetRouter(e.attach), 0)
    }

    // Local port order: RN nodes then SN nodes, by NodeID.
    val localOf = mutable.Map.empty[Int, mutable.ArrayBuffer[ChiNetNode]]
    routerNames.indices.foreach(k => localOf(k) = mutable.ArrayBuffer.empty)
    (rn ++ sn).sortBy(_.nodeId).foreach { n => localOf(n.router) += n }
    val withLocalPort = (rn ++ sn).map { n =>
      n.copy(localPort = localOf(n.router).indexOf(n))
    }
    val rnFinal = withLocalPort.filter(n => ChiNodeKind.isRequest(n.kind)).sortBy(_.nodeId)
    val snFinal = withLocalPort.filter(n => ChiNodeKind.isSubordinate(n.kind)).sortBy(_.nodeId)

    val graphLinks = ir.links
      .filter(l => routerNames.contains(l.from) && routerNames.contains(l.to))
      .sortBy(l => (l.from, l.to))
    val outLinksOf = routerNames.indices.map(k => graphLinks.indices.filter(l => graphLinks(l).from == routerNames(k)).toSeq)
    val inLinksOf = routerNames.indices.map(k => graphLinks.indices.filter(l => graphLinks(l).to == routerNames(k)).toSeq)
    val incident = routerNames.indices.map(k => (outLinksOf(k) ++ inLinksOf(k)).distinct.sorted)

    // Link port index = nLocal + position among incident links.
    def linkPort(k: Int, linkId: Int): Int = localOf(k).size + incident(k).indexOf(linkId)

    val links = graphLinks.zipWithIndex.map { case (l, id) =>
      val from = routerIndex(l.from); val to = routerIndex(l.to)
      ChiNetLink(id, from, to, linkPort(from, id), linkPort(to, id), l.pipe)
    }

    // Router adjacency for BFS (bidirectional links).
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

    val nodeRouter = (rnFinal ++ snFinal).map(n => n.nodeId -> n.router).toMap
    val nodeLocalPort = (rnFinal ++ snFinal).map(n => n.nodeId -> n.localPort).toMap
    val maxNodes = cfg.maxNodes

    val routers = routerNames.indices.map { k =>
      val route = Array.fill(maxNodes)(0)
      for (nid <- 0 until maxNodes if nodeRouter.contains(nid)) {
        val owner = nodeRouter(nid)
        route(nid) =
          if (owner == k) nodeLocalPort(nid)
          else firstHop(k, owner).map(id => linkPort(k, id)).getOrElse(0)
      }
      val linkOfPort = incident(k).map(id => id)
      ChiNetRouter(k, routerNames(k), localOf(k).size, incident(k).size, route.toSeq, linkOfPort)
    }

    ChiNetworkParams(ir.meta.name, cfg, rnFinal, snFinal, routers, links, snpEnable, queueDepth = cfg.lcreditDepth)
  }
}