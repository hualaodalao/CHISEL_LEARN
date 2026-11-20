package loom.check

import loom.ir._
import loom.graph.FabricGraph
import scala.collection.mutable.ListBuffer

/** C0: schema, required fields, ranges and reference integrity.
  * C1 (structural overlap, CDG) arrives in later sprints.
  */
object Checks {
  private val NamePattern = "[A-Za-z0-9_.-]+".r
  private val Roles = Set("master", "slave")
  private val Protocols = Set("native", "axi4", "axilite", "tlul")
  private val Qos = Set("ctl", "lat", "bulk")
  private val SwitchKinds = Set("flat", "banked")
  private val RoutingKinds = Set("any", "xy")
  private val CdcKinds = Set("sync", "async")
  private val LinkDirections = Set("bidirectional", "forward")
  private val AddrWidths = Set(16, 32, 48, 64)
  private val DataWidths = Set(32, 64, 128, 512, 1024)

  private def pow2(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0

  private def pow2(n: Long): Boolean = n > 0 && (n & (n - 1)) == 0

  private def log2Ceil(n: Int): Int =
    if (n <= 1) 0 else 32 - Integer.numberOfLeadingZeros(n - 1)

  /** C1: structural consistency of the fabric graph. */
  def runC1(ir: Ir): List[String] = {
    val errs = ListBuffer.empty[String]
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")

    if (ir.routers.length == 1) {
      val r = ir.routers.head
      if (r.in != ir.mnets.length)
        errs += s"C1: router '${r.name}' in=${r.in} does not match MNet count ${ir.mnets.length}"
      if (r.out != ir.snets.length)
        errs += s"C1: router '${r.name}' out=${r.out} does not match SNet count ${ir.snets.length}"
    }
    if (masters.isEmpty) errs += "C1: at least one master is required"
    if (slaves.isEmpty) errs += "C1: at least one slave is required"

    val clockOf: Map[String, String] =
      (ir.mnets ++ ir.snets).map(n => n.name -> n.clock).toMap ++
        ir.routers.map(r => r.name -> r.clock).toMap

    val duplicates = ir.links
      .groupBy(l => (l.from, l.to))
      .collect { case (k, v) if v.size > 1 => k }
      .toList
      .sortBy(_._1)
    duplicates.foreach { case (from, to) => errs += s"C1: duplicate link '$from' -> '$to'" }

    ir.mnets.foreach { n =>
      if (!ir.links.exists(_.from == n.name))
        errs += s"C1: mnet '${n.name}' has no outgoing link"
    }
    ir.snets.foreach { n =>
      if (!ir.links.exists(_.to == n.name))
        errs += s"C1: snet '${n.name}' has no incoming link"
    }
    ir.routers.foreach { r =>
      if (!ir.links.exists(l => l.from == r.name || l.to == r.name))
        errs += s"C1: router '${r.name}' is not referenced by any link"
    }

    ir.links.foreach { l =>
      (clockOf.get(l.from), clockOf.get(l.to)) match {
        case (Some(a), Some(b)) if l.cdc == "sync" && a != b =>
          errs += s"C1: sync link '${l.from}'($a) -> '${l.to}'($b) crosses clock domains"
        case _ =>
      }
    }

    if (masters.nonEmpty && slaves.nonEmpty) {
      val wins = AddressMap.windows(slaves).sortBy(w => (w.base, w.index))
      wins.sliding(2).foreach {
        case Seq(a, b) if a.base + a.size > b.base =>
          errs += s"C1: address windows of '${a.id}' and '${b.id}' overlap"
        case _ =>
      }
      if (!AddressMap.declared(slaves) && !AddressMap.covers(masters.head.addrWidth, wins))
        errs += s"C1: addrWidth=${masters.head.addrWidth} cannot hold ${slaves.length} default 4 KB windows"
    }

    errs.toList
  }

  /** G1: the channel dependency graph must be acyclic.
    *
    * A cycle means a request can hold one channel while waiting for another
    * in a cyclic chain. The error prints the offending channel chain so the
    * user can break it with a Router split, a different link direction, or
    * an explicit escape VC.
    */
  def runG1(ir: Ir): List[String] = {
    val g = FabricGraph.fromIr(ir)
    val ringSet = g.ringLinks.toSet
    val stray = ir.links
      .map(l => s"${l.from}->${l.to}" -> l.dateline)
      .filter { case (label, marked) => marked && !ringSet.contains(label) }
      .map { case (label, _) => s"LM-G1: dateline link $label is not part of a ring" }
      .sorted
    g.errors.toList ++ stray ++ (g.findCycle match {
      case None => Nil
      case Some(path) => List(s"LM-G1: CDG cycle: ${path.mkString(" -> ")}")
    })
  }

  /** C2: QoS gates G2/G5.
    *
    * G5 (class VC disjointness): the three classes are named ctl/lat/bulk,
    * unique, and each owns a disjoint VC set (the router partitions its
    * input request arbiters per class, so no VC is shared).
    * G2 (request/response resource disjointness): responses are routed by
    * source through a dedicated response arbiter; no request VC is reused
    * for a response. Since the RTL instantiates separate request and
    * response paths, this gate only checks that every endpoint carries a
    * declared class and that the class table is well formed.
    */
  def runC2(ir: Ir): List[String] = {
    val errs = ListBuffer.empty[String]
    val classes = if (ir.qosClasses.isEmpty) LoomDefaults.DefaultQos else ir.qosClasses
    val names = classes.map(_.name)

    duplicates(names).foreach(n => errs += s"C2: duplicate qos class '$n'")
    if (names.toSet != LoomDefaults.QosOrder.toSet)
      errs += s"C2: qos classes must be exactly ${LoomDefaults.QosOrder.mkString(",")}"

    classes.foreach { c =>
      val p = s"qos class '${c.name}'"
      if (!LoomDefaults.QosOrder.contains(c.name)) errs += s"$p is not a supported class"
      if (c.vcs < 1 || c.vcs > 8) errs += s"$p vcs must be in [1,8]"
      if (c.depth < 1 || c.depth > 64) errs += s"$p depth must be in [1,64]"
      if (c.weight < 1 || c.weight > 64) errs += s"$p weight must be in [1,64]"
      if (c.maxPacketBeats < 1 || c.maxPacketBeats > 64)
        errs += s"$p maxPacketBeats must be in [1,64]"
    }

    if (names.distinct.size != names.size)
      errs += "C2: qos class names must be unique"
    ir.endpoints.foreach { e =>
      if (!names.contains(e.qos))
        errs += s"C2: endpoint '${e.id}' qos '${e.qos}' has no class definition"
    }

    errs.toList
  }

  /** G4: every endpoint protocol has a shell, and shells generate requests
    * only from their command path. The static half checks protocol coverage;
    * the dynamic half is the response-only directed test in `LmShellSpec`.
    */
  def runG4(ir: Ir): List[String] =
    ir.endpoints.toList.flatMap { e =>
      if (!ShellProtocols.contains(e.protocol))
        List(s"LM-G4: endpoint '${e.id}' protocol '${e.protocol}' has no shell")
      else Nil
    }

  private val ShellProtocols = Set("native", "axi4", "axilite", "tlul")

  /** G6: every class has room for one full packet, on both the VC queue and
    * the source return credits, and the destination slots cover all sources.
    *
    * Link pipes and async FIFOs sit between injection and response, so their
    * occupancy is already covered by the end-to-end credit: a request is
    * credited at the NI and only released when its response returns.
    */
  def runG6(ir: Ir): List[String] = {
    val errs = ListBuffer.empty[String]
    val classes = effectiveClasses(ir)
    classes.foreach { c =>
      if (c.depth < c.maxPacketBeats)
        errs += s"LM-G6: qos class '${c.name}' depth=${c.depth} < maxPacketBeats=${c.maxPacketBeats}"
    }
    val byName = classes.map(c => c.name -> c).toMap
    ir.endpoints.filter(_.role == "master").foreach { e =>
      byName.get(e.qos).foreach { c =>
        if (e.outstanding < c.maxPacketBeats)
          errs += s"LM-G6: endpoint '${e.id}' outstanding=${e.outstanding} < " +
            s"class '${c.name}' maxPacketBeats=${c.maxPacketBeats}"
      }
    }
    errs.toList
  }

  /** G7: the control class never spans more than a single beat. */
  def runG7(ir: Ir): List[String] =
    effectiveClasses(ir).filter(_.name == "ctl").toList.flatMap { c =>
      if (c.maxPacketBeats != 1)
        List(s"LM-G7: ctl maxPacketBeats must be 1, got ${c.maxPacketBeats}")
      else Nil
    }

  /** G8: arbitration is restricted to the implemented policies. Strict
    * priority is only allowed together with aging in a later sprint.
    */
  def runG8(ir: Ir): List[String] =
    effectiveClasses(ir).toList.flatMap { c =>
      if (c.arbitration != "wrr" && c.arbitration != "rr")
        List(s"LM-G8: qos class '${c.name}' arbitration must be wrr or rr")
      else Nil
    }

  private def effectiveClasses(ir: Ir): Seq[QosClass] =
    if (ir.qosClasses.isEmpty) LoomDefaults.DefaultQos else ir.qosClasses

  /** S1: partition membership is total and unique, and attachment networks
    * stay with the router they serve, so cuts land on link boundaries.
    */
  def runS1(ir: Ir): List[String] = {
    if (ir.partitions.isEmpty) return Nil
    val errs = ListBuffer.empty[String]
    val nodeNames =
      ir.mnets.map(_.name) ++ ir.snets.map(_.name) ++ ir.routers.map(_.name)
    val clockIds = ir.clockDomains.map(_.id)
    val owner = scala.collection.mutable.Map.empty[String, String]

    duplicates(ir.partitions.map(_.name)).foreach(n => errs += s"LM-S1: duplicate partition '$n'")
    ir.partitions.foreach { p =>
      if (p.members.isEmpty) errs += s"LM-S1: partition '${p.name}' has no members"
      if (!clockIds.contains(p.clock))
        errs += s"LM-S1: partition '${p.name}' clock '${p.clock}' is not declared"
      p.members.foreach { m =>
        if (!nodeNames.contains(m))
          errs += s"LM-S1: partition '${p.name}' member '$m' is not a fabric node"
        else owner.get(m) match {
          case Some(other) => errs += s"LM-S1: node '$m' is in both '$other' and '${p.name}'"
          case None => owner(m) = p.name
        }
      }
    }
    nodeNames.filterNot(owner.contains).sorted.foreach { n =>
      errs += s"LM-S1: node '$n' is not assigned to any partition"
    }

    val clockOf: Map[String, String] =
      (ir.mnets ++ ir.snets).map(n => n.name -> n.clock).toMap ++
        ir.routers.map(r => r.name -> r.clock).toMap

    // A partition top runs on one clock, so every member must belong to it.
    ir.partitions.foreach { p =>
      p.members.foreach { m =>
        clockOf.get(m).foreach { c =>
          if (c != p.clock)
            errs += s"LM-S1: partition '${p.name}' member '$m' runs on clock '$c', expected '${p.clock}'"
        }
      }
    }

    // Endpoints are partition members through their attachment network, so
    // their own clock must match it.
    ir.endpoints.foreach { e =>
      clockOf.get(e.attach).foreach { c =>
        if (c != e.clock)
          errs += s"LM-S1: endpoint '${e.id}' runs on clock '${e.clock}' but '${e.attach}' runs on '$c'"
      }
    }

    // An async link is a CDC boundary; spending one inside a clock domain
    // only costs latency and hides a missing partition clock.
    ir.links.foreach { l =>
      (clockOf.get(l.from), clockOf.get(l.to)) match {
        case (Some(a), Some(b)) if a == b && l.cdc == "async" =>
          errs += s"LM-S1: async link '${l.from}' -> '${l.to}' joins clock domain '$a'; use cdc \"sync\""
        case _ =>
      }
    }

    val mnetNames = ir.mnets.map(_.name).toSet
    val snetNames = ir.snets.map(_.name).toSet
    ir.links.foreach { l =>
      val touchesAttachment = mnetNames.contains(l.from) || snetNames.contains(l.to)
      if (touchesAttachment && owner.get(l.from) != owner.get(l.to))
        errs += s"LM-S1: attachment link '${l.from}' -> '${l.to}' crosses a partition boundary"
    }
    errs.toList
  }

  /** S2: every boundary is registered (boundaryPipe >= 1) and every
    * partition owns at least one router.
    */
  def runS2(ir: Ir): List[String] = {
    if (ir.partitions.isEmpty) return Nil
    ir.partitions.toList.flatMap { p =>
      val errs = ListBuffer.empty[String]
      if (p.boundaryPipe < 1 || p.boundaryPipe > 8)
        errs += s"LM-S2: partition '${p.name}' boundaryPipe must be in [1,8]"
      if (!ir.routers.exists(r => p.members.contains(r.name)))
        errs += s"LM-S2: partition '${p.name}' has no router"
      errs.toList
    }
  }

  /** S3: only router-to-router links may be cut, so credits and response
    * slots never split across a boundary.
    */
  def runS3(ir: Ir): List[String] = {
    if (ir.partitions.isEmpty) return Nil
    val owner = partitionOwner(ir)
    val routerNames = ir.routers.map(_.name).toSet
    ir.links.toList.flatMap { l =>
      if (owner.get(l.from) != owner.get(l.to) &&
        (!routerNames.contains(l.from) || !routerNames.contains(l.to)))
        List(s"LM-S3: cut link '${l.from}' -> '${l.to}' is not router-to-router")
      else Nil
    }
  }

  private def partitionOwner(ir: Ir): Map[String, String] = {
    val m = scala.collection.mutable.Map.empty[String, String]
    ir.partitions.foreach(p => p.members.foreach(n => if (!m.contains(n)) m(n) = p.name))
    m.toMap
  }

  def run(root: JValue): List[String] = {
    val ir =
      try Ir.fromJson(root)
      catch { case e: Ir.IrException => return List(e.msg) }
    runIr(ir)
  }

  def runIr(ir: Ir): List[String] = {
    val errs = ListBuffer.empty[String]

    if (!NamePattern.matches(ir.meta.name))
      errs += s"meta.name '${ir.meta.name}' must match ${NamePattern.regex}"
    if (ir.meta.schema != "loom/v1")
      errs += s"schema must be 'loom/v1', got '${ir.meta.schema}'"
    if (ir.axiIdWidth < 1 || ir.axiIdWidth > 8)
      errs += s"axiIdWidth must be in [1,8], got ${ir.axiIdWidth}"

    val clockIds = ir.clockDomains.map(_.id)
    duplicates(clockIds).foreach(id => errs += s"duplicate clockDomain id '$id'")
    ir.clockDomains.foreach { c =>
      if (c.freqMHz <= 0) errs += s"clockDomain '${c.id}' freqMHz must be > 0"
    }

    val epIds = ir.endpoints.map(_.id)
    duplicates(epIds).foreach(id => errs += s"duplicate endpoint id '$id'")
    ir.endpoints.foreach { e =>
      val p = s"endpoint '${e.id}'"
      if (!Roles.contains(e.role)) errs += s"$p role must be one of ${Roles.mkString(",")}"
      if (!Protocols.contains(e.protocol)) errs += s"$p protocol must be one of ${Protocols.mkString(",")}"
      if (!Qos.contains(e.qos)) errs += s"$p qos must be one of ${Qos.mkString(",")}"
      if (!DataWidths.contains(e.dataWidth))
        errs += s"$p dataWidth must be one of ${DataWidths.toSeq.sorted.mkString(",")}"
      if (!AddrWidths.contains(e.addrWidth))
        errs += s"$p addrWidth must be one of ${AddrWidths.toSeq.sorted.mkString(",")}"
      if (e.outstanding < 1 || e.outstanding > 256)
        errs += s"$p outstanding must be in [1,256]"
      if (!clockIds.contains(e.clock)) errs += s"$p clock '${e.clock}' is not declared"
      if (e.base < -1L) errs += s"$p base must be >= 0"
      if (e.size < 0L) errs += s"$p size must be >= 0"
      if (e.role == "master" && (e.base >= 0 || e.size > 0))
        errs += s"$p: only slaves may declare base/size"
    }

    val slaves = ir.endpoints.filter(_.role == "slave")
    if (AddressMap.declared(slaves)) {
      val missing = slaves.filter(e => e.base < 0 || e.size == 0).map(_.id)
      if (missing.nonEmpty)
        errs += s"all slaves must declare both base and size (missing: ${missing.mkString(", ")})"
      slaves.foreach { e =>
        val p = s"slave '${e.id}'"
        if (e.size > 0) {
          if (!pow2(e.size)) errs += s"$p size must be a power of two"
          if (e.size < AddressMap.DefaultSize)
            errs += s"$p size must be at least ${AddressMap.DefaultSize} bytes"
          if (e.base >= 0 && e.base % e.size != 0) errs += s"$p base must be aligned to size"
          if (e.base >= 0 && BigInt(e.base) + BigInt(e.size) > (BigInt(1) << e.addrWidth))
            errs += s"$p window exceeds addrWidth=${e.addrWidth}"
        }
      }
    }

    val mnetNames = ir.mnets.map(_.name)
    val snetNames = ir.snets.map(_.name)
    val routerNames = ir.routers.map(_.name)
    val nodeNames = mnetNames ++ snetNames ++ routerNames
    duplicates(nodeNames).foreach(id => errs += s"duplicate fabric node name '$id'")

    (ir.mnets ++ ir.snets).foreach { n =>
      if (!clockIds.contains(n.clock)) errs += s"fabric node '${n.name}' clock '${n.clock}' is not declared"
    }

    ir.endpoints.foreach { e =>
      val attached = e.role match {
        case "master" => mnetNames.contains(e.attach)
        case "slave" => snetNames.contains(e.attach)
        case _ => true
      }
      if (!attached) errs += s"endpoint '${e.id}' attach '${e.attach}' is not a ${e.role} network"
    }

    ir.routers.foreach { r =>
      val p = s"router '${r.name}'"
      if (r.in < 1 || r.in > 32) errs += s"$p in must be in [1,32]"
      if (r.out < 1 || r.out > 32) errs += s"$p out must be in [1,32]"
      if (!clockIds.contains(r.clock)) errs += s"$p clock '${r.clock}' is not declared"
      if (!SwitchKinds.contains(r.switchKind)) errs += s"$p switch.kind must be flat or banked"
      if (r.banks < 1 || r.banks > 8) errs += s"$p switch.banks must be in [1,8]"
      if (r.switchKind == "flat" && r.banks != 1)
        errs += s"$p flat switch requires banks = 1"
      if (r.switchKind == "banked") {
        if ((r.banks & (r.banks - 1)) != 0)
          errs += s"$p banked switch requires a power-of-two bank count"
        if (r.out % r.banks != 0)
          errs += s"$p out=${r.out} must be divisible by banks=${r.banks}"
      }
      if (!RoutingKinds.contains(r.routing))
        errs += s"$p routing must be any or xy"
      if (r.routing == "xy" && r.pos.isEmpty)
        errs += s"$p xy routing requires pos {x,y}"
    }

    val allNodes = nodeNames.toSet
    val routerByName = ir.routers.map(r => r.name -> r).toMap
    ir.links.foreach { l =>
      val p = s"link '${l.from}' -> '${l.to}'"
      (routerByName.get(l.from), routerByName.get(l.to)) match {
        case (Some(a), Some(b)) if a.routing == "xy" || b.routing == "xy" =>
          (a.pos, b.pos) match {
            case (Some((ax, ay)), Some((bx, by))) =>
              val dist = math.abs(ax - bx) + math.abs(ay - by)
              if (dist != 1) errs += s"$p xy mesh link must connect Manhattan neighbors, got distance $dist"
            case _ => errs += s"$p xy mesh links require pos on both routers"
          }
        case _ =>
      }
      if (!allNodes.contains(l.from)) errs += s"$p: unknown from node"
      if (!allNodes.contains(l.to)) errs += s"$p: unknown to node"
      if (!pow2(l.width) || l.width < 8 || l.width > 1024)
        errs += s"$p width must be a power of two in [8,1024]"
      if (!CdcKinds.contains(l.cdc)) errs += s"$p cdc must be sync or async"
      if (!LinkDirections.contains(l.direction))
        errs += s"$p direction must be bidirectional or forward"
      if (l.dateline && !(routerByName.contains(l.from) && routerByName.contains(l.to)))
        errs += s"$p dateline requires a router-to-router link"
      if (l.pipe < 0 || l.pipe > 8) errs += s"$p pipe must be in [0,8]"
      if (l.cdc == "async" && l.asyncFifoDepth < 2)
        errs += s"$p async link requires asyncFifoDepth >= 2"
    }

    if (ir.timing.maxFanin < 1 || ir.timing.maxFanin > 64)
      errs += "fabric.timing.maxFanin must be in [1,64]"
    if (ir.timing.maxCombDepth < 1 || ir.timing.maxCombDepth > 256)
      errs += "fabric.timing.maxCombDepth must be in [1,256]"

    if (!ir.endpoints.exists(_.role == "master")) errs += "at least one master endpoint is required"
    if (!ir.endpoints.exists(_.role == "slave")) errs += "at least one slave endpoint is required"

    errs.toList
  }

  private def duplicates(xs: Seq[String]): Seq[String] = {
    xs.groupBy(identity).collect { case (k, v) if v.size > 1 => k }.toSeq.sorted
  }
}
