package loom.rtl

import loom.ir.{Ir, LoomDefaults}
import loom.graph.FabricGraph
import scala.collection.mutable

/** One router-to-router link in the elaborated network. */
final case class NetLinkSpec(
    id: Int,
    fromRouter: Int,
    toRouter: Int,
    fromPort: Int,
    toPort: Int,
    pipe: Int,
    async: Boolean,
    asyncFifoDepth: Int,
    width: Int,
    forward: Boolean = false
)

/** Router port layout and route tables.
  *
  * Input ports are ordered: masters attached to this router, then incoming
  * router links. Output ports are ordered: slaves served by this router,
  * then outgoing router links. Both orders are deterministic.
  */
final case class NetRouterSpec(
    index: Int,
    name: String,
    nIn: Int,
    nOut: Int,
    inMaster: Seq[Int],
    inLink: Seq[Int],
    outSlave: Seq[Int],
    outLink: Seq[Int],
    routeSlave: Seq[Int],
    routeMaster: Seq[Int],
    banks: Int = 1,
    outCut: Seq[Boolean] = Nil,
    routeMasterFwd: Seq[Int] = Nil
)

/** Elaborated multi-router network. */
final case class NetworkParams(
    name: String,
    nMaster: Int,
    nSlave: Int,
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    tagWidth: Int,
    slaveBase: Seq[Long],
    slaveSize: Seq[Long],
    masterMnet: Seq[Int],
    nMnet: Int,
    slaveSnet: Seq[Int],
    nSnet: Int,
    masterReturnSlots: Seq[Int],
    slaveRespSlots: Seq[Int],
    masterClass: Seq[Int],
    classWeights: Seq[Int],
    classDepth: Seq[Int],
    classMaxPacket: Seq[Int],
    routers: Seq[NetRouterSpec],
    links: Seq[NetLinkSpec],
    masterRouter: Seq[Int],
    masterPort: Seq[Int],
    slaveRouter: Seq[Int],
    slavePort: Seq[Int]
) {
  require(masterReturnSlots.length == nMaster)
  require(slaveRespSlots.length == nSlave)
  require(slaveBase.length == nSlave)
  require(slaveSize.length == nSlave)
  require(masterMnet.length == nMaster)
  require(slaveSnet.length == nSlave)
  require(masterClass.length == nMaster)
  require(masterRouter.length == nMaster)
  require(masterPort.length == nMaster)
  require(slaveRouter.length == nSlave)
  require(slavePort.length == nSlave)
  require(classDepth.length == classWeights.length)
  require(classMaxPacket.length == classWeights.length)

  val slaveBits: Int = math.max(1, log2Ceil(nSlave))
  val maxReturnSlots: Int = masterReturnSlots.max
  val maxRespSlots: Int = slaveRespSlots.max

  private def log2Ceil(n: Int): Int =
    if (n <= 1) 0 else 32 - Integer.numberOfLeadingZeros(n - 1)
}

object NetworkParams {
  private def pow2(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0
  private def log2Ceil(n: Int): Int =
    if (n <= 1) 0 else 32 - Integer.numberOfLeadingZeros(n - 1)

  def fromIr(ir: Ir): NetworkParams = {
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")
    require(ir.routers.length >= 2, "NetworkParams requires at least two routers")
    require(masters.nonEmpty && slaves.nonEmpty, "at least one master and one slave are required")

    val dataWidth = masters.head.dataWidth
    require((masters ++ slaves).forall(_.dataWidth == dataWidth), "all endpoints must share dataWidth")
    val addrWidth = masters.head.addrWidth
    require((masters ++ slaves).forall(_.addrWidth == addrWidth), "all endpoints must share addrWidth")

    val routerNames = ir.routers.map(_.name).sorted
    val routerIndex = routerNames.zipWithIndex.toMap
    val specByName = ir.routers.map(r => r.name -> r).toMap

    val mnetNames = ir.mnets.map(_.name).toSet
    val snetNames = ir.snets.map(_.name).toSet

    def outLinkOfMnet(mnet: String) = {
      val ls = ir.links.filter(_.from == mnet)
      require(ls.length == 1, s"mnet '$mnet' must have exactly one outgoing link")
      require(routerNames.contains(ls.head.to), s"mnet '$mnet' must connect to a router")
      ls.head
    }
    def inLinkOfSnet(snet: String) = {
      val ls = ir.links.filter(_.to == snet)
      require(ls.length == 1, s"snet '$snet' must have exactly one incoming link")
      require(routerNames.contains(ls.head.from), s"snet '$snet' must be fed by a router")
      ls.head
    }

    require(ir.mnets.forall(n => mnetNames.contains(n.name)))
    require(ir.snets.forall(n => snetNames.contains(n.name)))
    val masterRouter = masters.map { e =>
      require(mnetNames.contains(e.attach), s"master '${e.id}' attaches to unknown mnet '${e.attach}'")
      routerIndex(outLinkOfMnet(e.attach).to)
    }
    val slaveRouter = slaves.map { e =>
      require(snetNames.contains(e.attach), s"slave '${e.id}' attaches to unknown snet '${e.attach}'")
      routerIndex(inLinkOfSnet(e.attach).from)
    }

    // Router-to-router links, sorted deterministically by (from, to).
    val graphLinks = ir.links
      .filter(l => routerNames.contains(l.from) && routerNames.contains(l.to))
      .sortBy(l => (l.from, l.to))

    require(graphLinks.nonEmpty || ir.routers.length < 2, "multi-router fabric needs router links")
    val cutLabels = FabricGraph.fromIr(ir).datelines.toSet

    val inMastersOf = routerNames.indices.map(k => masters.indices.filter(i => masterRouter(i) == k).toSeq)
    val outSlavesOf = routerNames.indices.map(k => slaves.indices.filter(j => slaveRouter(j) == k).toSeq)
    val inLinksOf = routerNames.indices.map(k => graphLinks.indices.filter(l => graphLinks(l).to == routerNames(k)).toSeq)
    val outLinksOf = routerNames.indices.map(k => graphLinks.indices.filter(l => graphLinks(l).from == routerNames(k)).toSeq)

    val masterPort = masters.indices.map { i =>
      inMastersOf(masterRouter(i)).indexOf(i)
    }
    val slavePort = slaves.indices.map { j =>
      outSlavesOf(slaveRouter(j)).indexOf(j)
    }

    def outPortOfLink(k: Int, linkId: Int): Int =
      outSlavesOf(k).size + outLinksOf(k).indexOf(linkId)
    def inPortOfLink(k: Int, linkId: Int): Int =
      inMastersOf(k).size + inLinksOf(k).indexOf(linkId)

    case class Adj(to: Int, linkId: Int)
    val forward = routerNames.indices.map { k =>
      outLinksOf(k).map(l => Adj(routerIndex(graphLinks(l).to), l))
        .sortBy(a => (routerNames(a.to), a.linkId))
    }
    val backward = routerNames.indices.map { k =>
      inLinksOf(k).map(l => Adj(routerIndex(graphLinks(l).from), l))
        .sortBy(a => (routerNames(a.to), a.linkId))
    }

    /** Response hop: bidirectional links run responses backwards, forward
      * links run them in the request direction. */
    case class RAdj(to: Int, linkId: Int, forward: Boolean)
    val respAdj = routerNames.indices.map { k =>
      graphLinks.indices.flatMap { id =>
        val l = graphLinks(id)
        val forward = l.direction == "forward"
        val to = if (forward) routerIndex(l.to) else routerIndex(l.from)
        if (routerIndex(if (forward) l.from else l.to) == k) Some(RAdj(to, id, forward)) else None
      }.sortBy(a => (routerNames(a.to), a.linkId)).toSeq
    }

    def firstRespHop(from: Int, to: Int): Option[RAdj] = {
      if (from == to) return None
      val seen = mutable.Set(from)
      val queue = mutable.Queue.empty[(Int, RAdj)]
      respAdj(from).foreach { a =>
        if (!seen.contains(a.to)) { seen += a.to; queue.enqueue((a.to, a)) }
      }
      while (queue.nonEmpty) {
        val (node, first) = queue.dequeue()
        if (node == to) return Some(first)
        respAdj(node).foreach { a =>
          if (!seen.contains(a.to)) { seen += a.to; queue.enqueue((a.to, first)) }
        }
      }
      None
    }

    val coords: Map[Int, (Int, Int)] =
      routerNames.indices.flatMap(k => specByName(routerNames(k)).pos.map(p => k -> p)).toMap
    require(routerNames.indices.forall(k => specByName(routerNames(k)).routing != "xy" || coords.contains(k)),
      "xy routing requires pos {x,y} on every xy router")

    /** XY / YX first hop toward a destination router, over an adjacency list. */
    def xyHop(from: Int, to: Int, xFirst: Boolean, adj: Int => Seq[Adj]): Option[Int] = {
      if (from == to) Some(-1)
      else {
        val (x0, y0) = coords(from)
        val (x1, y1) = coords(to)
        def find(dx: Int, dy: Int): Option[Int] =
          adj(from).find(a => coords.get(a.to).contains((x0 + dx, y0 + dy))).map(_.linkId)
        val sx = Integer.signum(x1 - x0)
        val sy = Integer.signum(y1 - y0)
        if (xFirst) { if (sx != 0) find(sx, 0) else find(0, sy) }
        else { if (sy != 0) find(0, sy) else find(sx, 0) }
      }
    }

    /** First hop (link id) on a shortest path, or None when unreachable. */
    def firstHop(from: Int, to: Int, adj: Seq[Seq[Adj]]): Option[Int] = {
      if (from == to) return Some(-1)
      val seen = mutable.Set(from)
      val queue = mutable.Queue.empty[(Int, Int)]
      adj(from).foreach(a => { seen += a.to; queue.enqueue((a.to, a.linkId)) })
      while (queue.nonEmpty) {
        val (node, first) = queue.dequeue()
        if (node == to) return Some(first)
        adj(node).foreach { a =>
          if (!seen.contains(a.to)) {
            seen += a.to
            queue.enqueue((a.to, first))
          }
        }
      }
      None
    }

    val routers = routerNames.indices.map { k =>
      val inM = inMastersOf(k)
      val inL = inLinksOf(k)
      val outS = outSlavesOf(k)
      val outL = outLinksOf(k)
      val hasMasters = inM.nonEmpty
      val hasSlaves = outS.nonEmpty

      // A master attached to this router can target any slave, so such a
      // router must reach every slave. Leaf routers only see traffic for
      // their local subtree; unreachable entries are never selected.
      val routeSlave = slaves.indices.map { s =>
        val owner = slaveRouter(s)
        if (owner == k) outS.indexOf(s)
        else {
          val hop =
            if (specByName(routerNames(k)).routing == "xy") xyHop(k, owner, xFirst = true, forward)
            else firstHop(k, owner, forward)
          hop match {
            case Some(link) => outPortOfLink(k, link)
            case None if hasMasters =>
              throw new IllegalArgumentException(
                s"master attached to '${routerNames(k)}' cannot reach slave $s")
            case None => 0
          }
        }
      }.toSeq

      val allBidir = graphLinks.forall(_.direction != "forward")
      def respHop(owner: Int): Option[RAdj] =
        if (allBidir && specByName(routerNames(k)).routing == "xy") {
          xyHop(k, owner, xFirst = false, backward)
            .map(id => RAdj(routerIndex(graphLinks(id).from), id, forward = false))
        } else firstRespHop(k, owner)

      val routeMaster = masters.indices.map { m =>
        val owner = masterRouter(m)
        if (owner == k) inM.indexOf(m)
        else respHop(owner) match {
          case Some(r) if !r.forward => inPortOfLink(k, r.linkId)
          case Some(_) => -1
          case None if hasSlaves =>
            throw new IllegalArgumentException(
              s"slave attached to '${routerNames(k)}' cannot reach master $m")
          case None => -1
        }
      }.toSeq

      val routeMasterFwd = masters.indices.map { m =>
        val owner = masterRouter(m)
        if (owner == k) -1
        else respHop(owner) match {
          case Some(r) if r.forward => outPortOfLink(k, r.linkId)
          case _ => -1
        }
      }.toSeq
      val inMaster = Vector.tabulate(inM.size + inL.size)(i => if (i < inM.size) inM(i) else -1)
      val inLink = Vector.tabulate(inM.size + inL.size)(i => if (i < inM.size) -1 else inL(i - inM.size))
      val outSlave = Vector.tabulate(outS.size + outL.size)(j => if (j < outS.size) outS(j) else -1)
      val outLink = Vector.tabulate(outS.size + outL.size)(j => if (j < outS.size) -1 else outL(j - outS.size))
      val outCut = outL.map { id =>
        val l = graphLinks(id)
        cutLabels.contains(s"${l.from}->${l.to}")
      }
      NetRouterSpec(k, routerNames(k), inM.size + inL.size, outS.size + outL.size,
        inMaster, inLink, outSlave, outLink, routeSlave, routeMaster,
        banks = specByName(routerNames(k)).banks, outCut = outCut,
        routeMasterFwd = routeMasterFwd)
    }
    routers.foreach { s =>
      require(s.banks >= 1 && s.banks <= 8 && (s.banks & (s.banks - 1)) == 0,
        s"router '${s.name}' banks=${s.banks} must be a power of two in [1,8]")
      require(s.nOut % s.banks == 0,
        s"router '${s.name}' has ${s.nOut} outputs, not divisible by ${s.banks} banks")
    }

    val links = graphLinks.zipWithIndex.map { case (l, id) =>
      val from = routerIndex(l.from)
      val to = routerIndex(l.to)
      NetLinkSpec(id, from, to, outPortOfLink(from, id), inPortOfLink(to, id),
        l.pipe, l.cdc == "async", l.asyncFifoDepth, l.width, l.direction == "forward")
    }

    val classes = if (ir.qosClasses.isEmpty) LoomDefaults.DefaultQos else ir.qosClasses
    val weightOf = classes.map(c => c.name -> c.weight).toMap
    require(weightOf.keySet == LoomDefaults.QosOrder.toSet, "qos classes must be exactly ctl/lat/bulk")
    val classWeights = LoomDefaults.QosOrder.map(weightOf)
    val classOf = classes.map(c => c.name -> c).toMap
    val ordered = LoomDefaults.QosOrder.map(classOf)
    val masterClass = masters.map(e => LoomDefaults.QosOrder.indexOf(e.qos))
    require(masterClass.forall(_ >= 0), "endpoint qos must be one of ctl/lat/bulk")
    require(ordered.forall(c => c.depth >= c.maxPacketBeats), "G6: class depth must cover one packet")
    masters.foreach { e =>
      require(e.outstanding >= classOf(e.qos).maxPacketBeats,
        s"G6: master '${e.id}' outstanding=${e.outstanding} < maxPacketBeats")
    }

    val totalOutstanding = masters.map(_.outstanding).sum
    val windows = loom.ir.AddressMap.windows(slaves)
    NetworkParams(
      name = ir.meta.name,
      nMaster = masters.length,
      nSlave = slaves.length,
      addrWidth = addrWidth,
      dataWidth = dataWidth,
      idWidth = math.max(1, log2Ceil(masters.length)),
      tagWidth = ir.axiIdWidth,
      slaveBase = windows.map(_.base),
      slaveSize = windows.map(_.size),
      masterMnet = masters.map(e => ir.mnets.indexWhere(_.name == e.attach)),
      nMnet = ir.mnets.length,
      slaveSnet = slaves.map(e => ir.snets.indexWhere(_.name == e.attach)),
      nSnet = ir.snets.length,
      masterReturnSlots = masters.map(_.outstanding),
      slaveRespSlots = slaves.map(_ => totalOutstanding),
      masterClass = masterClass,
      classWeights = classWeights,
      classDepth = ordered.map(_.depth),
      classMaxPacket = ordered.map(_.maxPacketBeats),
      routers = routers,
      links = links,
      masterRouter = masterRouter,
      masterPort = masterPort,
      slaveRouter = slaveRouter,
      slavePort = slavePort
    )
  }
}
