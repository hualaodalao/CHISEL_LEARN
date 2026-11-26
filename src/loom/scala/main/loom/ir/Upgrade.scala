package loom.ir

import scala.collection.mutable

/** v1 -> v2 auto-upgrade (MIG-01–MIG-09). In-memory only; the input file is
  * never rewritten. Warnings use the fixed MIG-07 strings (tests assert them
  * verbatim).
  */
object Upgrade {
  final case class Result(ir: IrV2, warnings: Seq[String])

  val SchemaWarning: String =
    "LM-W-DEPRECATED: schema 'loom/v1' is deprecated and auto-upgraded to 'loom/v2'; " +
      "MNet/SNet, burst/beat and native/axilite/tlul protocols are retired (see doc/ir_gen_spec.md MIG-06)"

  def addrWidthWarning(id: String, value: Int): String =
    s"LM-W-DEPRECATED: endpoint '$id' addrWidth=$value is not a CHI standard width; using chi.addrWidth=48"

  def linkWidthWarning(from: String, to: String, value: Int, datWidth: Int): String =
    s"LM-W-DEPRECATED: link '$from->$to' width=$value is not chi.datWidth=$datWidth; using $datWidth"

  def switchWarning(router: String): String =
    s"LM-W-DEPRECATED: router '$router' switch block is retired (CHI router is flat with registered outputs); discarded"

  def directionWarning(from: String, to: String, value: String): String =
    s"LM-W-DEPRECATED: link '$from->$to' direction='$value' is retired (CHI links are always bidirectional); discarded"

  def v1ToV2(ir: Ir): Result = {
    val warnings = mutable.ArrayBuffer.empty[String]
    warnings += SchemaWarning

    val routerNames = ir.routers.map(_.name).toSet
    val mnetNames = ir.mnets.map(_.name).toSet
    val snetNames = ir.snets.map(_.name).toSet
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")
    if (masters.isEmpty || slaves.isEmpty)
      throw Ir.IrException("upgrade needs at least one master and one slave")

    def attachRouter(e: Endpoint): String = {
      if (routerNames.contains(e.attach)) e.attach
      else if (mnetNames.contains(e.attach)) {
        val ls = ir.links.filter(_.from == e.attach)
        if (ls.length != 1) throw Ir.IrException(s"mnet '${e.attach}' must have exactly one outgoing link")
        ls.head.to
      } else if (snetNames.contains(e.attach)) {
        val ls = ir.links.filter(_.to == e.attach)
        if (ls.length != 1) throw Ir.IrException(s"snet '${e.attach}' must have exactly one incoming link")
        ls.head.from
      } else throw Ir.IrException(s"endpoint '${e.id}' attaches to unknown network '${e.attach}'")
    }

    // MIG-09: first master addrWidth, folded to a CHI standard width.
    val rawAddr = masters.head.addrWidth
    val addrWidth = if (Set(32, 40, 48).contains(rawAddr)) rawAddr else 48
    ir.endpoints.foreach { e =>
      if (e.addrWidth != addrWidth) warnings += addrWidthWarning(e.id, e.addrWidth)
    }

    // MIG-02/IR-43: portMode converges from the endpoint protocol set.
    val protocols = ir.endpoints.map { e =>
      e.protocol match {
        case "native" => "chi"
        case "axi4"   => "axi4"
        case other    =>
          throw Ir.IrException(
            s"endpoint '${e.id}' protocol '$other' is retired and cannot be auto-upgraded; use axi4 or chi (MIG-06)")
      }
    }
    val portMode =
      if (protocols.forall(_ == "chi")) "chi"
      else if (protocols.forall(_ == "axi4")) "axi4"
      else "both"

    val chi = ChiBlockV2(
      addrWidth = addrWidth,
      outstanding = math.max(64, ir.endpoints.map(_.outstanding).max),
      portMode = portMode
    )

    val nodes = ir.endpoints.zip(protocols).map { case (e, protocol) =>
      val kind = e.role match {
        case "master" => "rn-i"
        case "slave"  => "sn-f"
        case other    => throw Ir.IrException(s"endpoint '${e.id}' role '$other' cannot be upgraded")
      }
      NodeV2(e.id, kind, None, e.clock, attachRouter(e), protocol, e.dataWidth, e.outstanding,
        V2Defaults.qosPriority(e.qos))
    }

    // MIG-03: all slaves explicit -> sam entries; none -> derived 4KB stride.
    val explicit = slaves.count(_.base >= 0)
    val sam =
      if (explicit == slaves.length)
        slaves.map(e => SamWindowV2(e.base, e.size, e.id))
      else if (explicit == 0)
        slaves.zipWithIndex.map { case (e, i) =>
          SamWindowV2(i.toLong * AddressMap.DefaultSize, AddressMap.DefaultSize, e.id)
        }
      else throw Ir.IrException("mixed explicit/implicit slave base/size cannot be upgraded")

    // MIG-05: mnet/snet access links expand to one node<->router link per
    // attached endpoint (v1 shared ports become per-node CHI ports).
    val links = mutable.ArrayBuffer.empty[LinkV2]
    ir.links.foreach { l =>
      val isFromNet = mnetNames.contains(l.from)
      val isToNet = snetNames.contains(l.to)
      val froms: Seq[String] =
        if (isFromNet) ir.endpoints.filter(_.attach == l.from).map(_.id) else Seq(l.from)
      val tos: Seq[String] =
        if (isToNet) ir.endpoints.filter(_.attach == l.to).map(_.id) else Seq(l.to)
      if (l.width != chi.datWidth)
        warnings += linkWidthWarning(l.from, l.to, l.width, chi.datWidth)
      if (l.direction == "forward")
        warnings += directionWarning(l.from, l.to, l.direction)
      for (f <- froms; t <- tos) {
        links += LinkV2(f, t, chi.datWidth, l.cdc, l.pipe, l.asyncFifoDepth, l.dateline,
          V2Defaults.channelsOf(chi.dvmEnable), chi.lcreditDepth)
      }
    }

    val routers = ir.routers.map { r =>
      if (r.switchKind == "banked" || r.banks > 1) warnings += switchWarning(r.name)
      // v1 in/out counts were net-based; v2 port layout is node-based and
      // derived, so the stale declarations are dropped (IR-48/49 default).
      RouterV2(r.name, r.clock, None, None, r.pos, r.routing)
    }

    val netToNodes: String => Seq[String] = name =>
      ir.endpoints.filter(_.attach == name).map(_.id)
    val partitions = ir.partitions.map { p =>
      val members = p.members.flatMap { m =>
        if (mnetNames.contains(m) || snetNames.contains(m)) netToNodes(m) else Seq(m)
      }
      p.copy(members = members)
    }

    val classes =
      if (ir.qosClasses.isEmpty) V2Defaults.QosClasses
      else ir.qosClasses.map(c => QosClassV2(c.name, c.vcs, c.depth, c.arbitration, c.weight))

    val v2 = IrV2(ir.meta.copy(schema = "loom/v2"), ir.clockDomains, nodes, sam,
      QosV2(V2Defaults.QosMapping, classes), chi, routers, links.toSeq, ir.timing, partitions)
    Result(v2, warnings.toSeq)
  }
}