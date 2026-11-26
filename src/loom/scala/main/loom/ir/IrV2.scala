package loom.ir

import java.nio.file.{Files, Path}

import scala.collection.mutable

/** Per-node QoS bandwidth regulator declaration (IR-75–IR-78, UP-02).
  * Consumed by HW-10 (SEM-18): ChiQosRegulator token bucket at RN injection.
  * bandwidth is MB/s, saturation is the burst allowance in bytes.
  */
final case class QosGenSpec(kind: String, bandwidth: Int, saturation: Int)

/** loom/v2 node (IR-13–IR-21). One CHI port: NodeID + REQ/RSP/DAT (+SNP).
  * qosGen/adaptFifoDepth (IR-75–IR-79, v1.3.0) are appended with defaults so
  * existing positional constructions stay source-compatible.
  */
final case class NodeV2(
    id: String,
    kind: String,
    nodeId: Option[Int],
    clock: String,
    attach: String,
    protocol: String,
    dataWidth: Int,
    outstanding: Int,
    qos: Int,
    qosGen: Option[QosGenSpec] = None,
    adaptFifoDepth: Option[Int] = None
)

/** SAM window (IR-22–IR-24). */
final case class SamWindowV2(base: Long, size: Long, tgtNode: String)

/** QoS priority segment -> class (IR-26–IR-28). */
final case class QosMappingV2(qosMin: Int, qosMax: Int, className: String)

/** QoS class scheduling parameters (IR-30–IR-34), maxPacketBeats retired. */
final case class QosClassV2(name: String, vcs: Int, depth: Int, arbitration: String, weight: Int)

final case class QosV2(mapping: Seq[QosMappingV2], classes: Seq[QosClassV2])

/** CHI configuration block (IR-35–IR-45); defaults mirror chi/ChiConfig. */
final case class ChiBlockV2(
    datWidth: Int = 256,
    txnIdWidth: Int = 8,
    nodeIdWidth: Int = 7,
    addrWidth: Int = 48,
    outstanding: Int = 64,
    atomicEnable: Boolean = true,
    atomicEntries: Int = 4,
    dvmEnable: Boolean = true,
    portMode: String = "axi4",
    pcrdTypes: Int = 1,
    lcreditDepth: Int = 4
)

/** Router (IR-46–IR-53); switch block retired (MIG-06). */
final case class RouterV2(
    name: String,
    clock: String,
    in: Option[Int],
    out: Option[Int],
    pos: Option[(Int, Int)],
    routing: String
)

/** Explicit route / connectivity constraint (IR-70–IR-74, UP-01). `path` is
  * the RN->SN router sequence (empty for forbid-only entries); `forbid` is a
  * validation-only declaration (SEM-17): the pair must be topologically
  * unreachable (CHK-51), it never filters the generated SAM/routes.
  */
final case class RouteSpec(from: String, to: String, path: Seq[String], forbid: Boolean)

/** Fabric link (IR-54–IR-63); direction retired (HW-07, always bidirectional). */
final case class LinkV2(
    from: String,
    to: String,
    width: Int,
    cdc: String,
    pipe: Int,
    asyncFifoDepth: Int,
    dateline: Boolean,
    channels: Seq[String],
    lcreditDepth: Int
)

/** One independent subsystem NoC (IR-80–IR-87, UP-07/SEM-20). NodeID space,
  * SAM, chi configuration and clock domains are local to the subsystem;
  * omitted clockDomains/qos/chi blocks fall back to the top-level values at
  * parse time, and an omitted sam is derived per subsystem SN order (SEM-06).
  * Hierarchical emission is pending HW-11 (ChiNocBridge); check/estimate/API
  * consume the projection `IrV2.projectSubsystem`.
  */
final case class SubsystemSpec(
    name: String,
    clockDomains: Seq[ClockDomain],
    nodes: Seq[NodeV2],
    sam: Seq[SamWindowV2],
    qos: QosV2,
    chi: ChiBlockV2,
    routers: Seq[RouterV2],
    links: Seq[LinkV2],
    timing: TimingSpec)

/** Inter-NoC bridge declaration (IR-88–IR-93, UP-07). The bridge is a
  * dependency-terminating endpoint attached to local ports of fromRouter/
  * toRouter (not a router); `windows` are the from-side address windows
  * forwarded into the to subsystem (base, size). HW-11 consumes the
  * assembled ChiAssemble.BridgeParams.
  */
final case class NocLinkSpec(
    from: String,
    to: String,
    fromRouter: String,
    toRouter: String,
    windows: Seq[(Long, Long)],
    cdc: String,
    asyncFifoDepth: Int,
    dvmForward: Boolean,
    outstanding: Int = 1)

/** Normalized loom/v2 object: every optional field filled per GEN-03.
  * `unknown` collects unrecognized field paths for the C0 unknown-key gate.
  * subsystems/nocLinks (IR-80–IR-93, v1.5.0) are appended with defaults so
  * existing positional constructions stay source-compatible.
  */
final case class IrV2(
    meta: Meta,
    clockDomains: Seq[ClockDomain],
    nodes: Seq[NodeV2],
    sam: Seq[SamWindowV2],
    qos: QosV2,
    chi: ChiBlockV2,
    routers: Seq[RouterV2],
    links: Seq[LinkV2],
    timing: TimingSpec,
    partitions: Seq[PartitionSpec],
    routes: Seq[RouteSpec] = Nil,
    unknown: Seq[String] = Nil,
    subsystems: Seq[SubsystemSpec] = Nil,
    nocLinks: Seq[NocLinkSpec] = Nil)

object V2Defaults {
  val QosMapping: Seq[QosMappingV2] = Seq(
    QosMappingV2(8, 15, "ctl"),
    QosMappingV2(4, 7, "lat"),
    QosMappingV2(0, 3, "bulk")
  )

  val QosClasses: Seq[QosClassV2] =
    LoomDefaults.DefaultQos.map(c => QosClassV2(c.name, c.vcs, c.depth, c.arbitration, c.weight))

  /** MIG-04: v1 class name -> 4-bit priority landing in the default segment. */
  def qosPriority(className: String): Int = className match {
    case "ctl"  => 8
    case "lat"  => 4
    case "bulk" => 0
    case other  => throw Ir.IrException(s"unknown qos class '$other'")
  }

  def protocolOf(portMode: String): String = portMode match {
    case "chi" => "chi"
    case _     => "axi4"
  }

  def channelsOf(dvmEnable: Boolean): Seq[String] =
    if (dvmEnable) Seq("req", "rsp", "dat", "snp") else Seq("req", "rsp", "dat")
}

object IrV2 {
  import Ir.{asObj, arrOf, field, str, int, num, optStr, optInt, optLong, optBool}

  def load(path: Path): IrV2 = fromJson(Json.parse(Files.readString(path)))

  def fromJson(root: JValue): IrV2 = {
    val unknown = mutable.ArrayBuffer.empty[String]
    def keys(o: JObj, path: String, allowed: Set[String]): Unit =
      o.fields.foreach { case (k, _) => if (!allowed.contains(k)) unknown += s"$path.$k" }

    // ---- Shared block parsers (UP-07 refactor): subsystem entries reuse the
    // exact top-level node/sam/qos/chi/fabric logic; omitted subsystem blocks
    // fall back to the top-level values (IR-82/85/86), sam derives per
    // subsystem SN order (IR-84/SEM-06).
    def objBlock(parent: JObj, key: String, path: String): JObj = parent.get(key) match {
      case Some(o: JObj) => o
      case Some(_) => throw Ir.IrException(s"$path.$key must be an object")
      case None => JObj(Vector.empty)
    }

    def clocksOf(items: Vector[JValue], path: String): Seq[ClockDomain] =
      items.zipWithIndex.map { case (v, i) =>
        val p = s"$path[$i]"
        val o = asObj(v, p)
        keys(o, p, Set("id", "freqMHz"))
        ClockDomain(str(o, "id", p), num(o, "freqMHz", p))
      }

    def chiOf(o: JObj, path: String, dflt: ChiBlockV2): ChiBlockV2 = {
      keys(o, path, Set("datWidth", "txnIdWidth", "nodeIdWidth", "addrWidth", "outstanding",
        "atomicEnable", "atomicEntries", "dvmEnable", "portMode", "pcrdTypes", "lcreditDepth"))
      ChiBlockV2(
        datWidth = optInt(o, "datWidth", dflt.datWidth),
        txnIdWidth = optInt(o, "txnIdWidth", dflt.txnIdWidth),
        nodeIdWidth = optInt(o, "nodeIdWidth", dflt.nodeIdWidth),
        addrWidth = optInt(o, "addrWidth", dflt.addrWidth),
        outstanding = optInt(o, "outstanding", dflt.outstanding),
        atomicEnable = optBool(o, "atomicEnable", dflt.atomicEnable),
        atomicEntries = optInt(o, "atomicEntries", dflt.atomicEntries),
        dvmEnable = optBool(o, "dvmEnable", dflt.dvmEnable),
        portMode = optStr(o, "portMode", dflt.portMode),
        pcrdTypes = optInt(o, "pcrdTypes", dflt.pcrdTypes),
        lcreditDepth = optInt(o, "lcreditDepth", dflt.lcreditDepth)
      )
    }

    def nodesOf(items: Vector[JValue], path: String, chi: ChiBlockV2): Seq[NodeV2] =
      items.zipWithIndex.map { case (v, i) =>
        val p = s"$path[$i]"
        val o = asObj(v, p)
        keys(o, p, Set("id", "kind", "nodeId", "clock", "attach", "protocol", "dataWidth",
          "outstanding", "qos", "qosGen", "adaptFifoDepth"))
        // IR-75–IR-78 (UP-02): optional qosGen declaration; saturation
        // defaults to 0, unknown subkeys go through the C0 unknown-key gate.
        val qosGen = o.get("qosGen") match {
          case Some(q: JObj) =>
            val qp = s"$p.qosGen"
            keys(q, qp, Set("kind", "bandwidth", "saturation"))
            Some(QosGenSpec(str(q, "kind", qp), int(q, "bandwidth", qp),
              optInt(q, "saturation", 0)))
          case Some(_) => throw Ir.IrException(s"$p.qosGen must be an object")
          case None => None
        }
        NodeV2(
          id = str(o, "id", p),
          kind = str(o, "kind", p),
          nodeId = o.get("nodeId").map(_ => int(o, "nodeId", p)),
          clock = str(o, "clock", p),
          attach = str(o, "attach", p),
          protocol = optStr(o, "protocol", V2Defaults.protocolOf(chi.portMode)),
          dataWidth = optInt(o, "dataWidth", chi.datWidth),
          outstanding = optInt(o, "outstanding", chi.outstanding),
          qos = optInt(o, "qos", 0),
          qosGen = qosGen,
          // IR-79 (UP-08): the default depth 4 lives in assembly/report only;
          // an omitted key stays None so the normalized rendering omits it.
          adaptFifoDepth = o.get("adaptFifoDepth").map(_ => int(o, "adaptFifoDepth", p))
        )
      }

    def samOf(parent: JObj, path: String, nodes: Seq[NodeV2]): Seq[SamWindowV2] = {
      val snIds = nodes.filter(n => n.kind == "sn-f" || n.kind == "sn-i").map(_.id)
      parent.get("sam") match {
        case Some(a: JArr) =>
          a.items.zipWithIndex.map { case (v, i) =>
            val p = s"$path.sam[$i]"
            val o = asObj(v, p)
            keys(o, p, Set("base", "size", "tgtNode"))
            SamWindowV2(optLong(o, "base", 0L), optLong(o, "size", AddressMap.DefaultSize),
              str(o, "tgtNode", p))
          }
        case Some(_) => throw Ir.IrException(s"$path.sam must be an array")
        case None =>
          snIds.zipWithIndex.map { case (id, i) =>
            SamWindowV2(i.toLong * AddressMap.DefaultSize, AddressMap.DefaultSize, id)
          }
      }
    }

    def qosOf(o: JObj, path: String): QosV2 = {
      keys(o, path, Set("mapping", "classes"))
      val mapping = o.get("mapping") match {
        case Some(a: JArr) =>
          a.items.zipWithIndex.map { case (v, i) =>
            val p = s"$path.mapping[$i]"
            val mo = asObj(v, p)
            keys(mo, p, Set("qosMin", "qosMax", "class"))
            QosMappingV2(int(mo, "qosMin", p), int(mo, "qosMax", p), str(mo, "class", p))
          }
        case Some(_) => throw Ir.IrException(s"$path.mapping must be an array")
        case None => V2Defaults.QosMapping
      }
      val classes = o.get("classes") match {
        case Some(a: JArr) =>
          a.items.zipWithIndex.map { case (v, i) =>
            val p = s"$path.classes[$i]"
            val co = asObj(v, p)
            keys(co, p, Set("name", "vcs", "depth", "arbitration", "weight"))
            QosClassV2(
              name = str(co, "name", p),
              vcs = optInt(co, "vcs", 1),
              depth = optInt(co, "depth", 8),
              arbitration = optStr(co, "arbitration", "wrr"),
              weight = optInt(co, "weight", 1)
            )
          }
        case Some(_) => throw Ir.IrException(s"$path.classes must be an array")
        case None => V2Defaults.QosClasses
      }
      QosV2(mapping, classes)
    }

    def routersOf(items: Vector[JValue], path: String): Seq[RouterV2] =
      items.zipWithIndex.map { case (v, i) =>
        val p = s"$path[$i]"
        val o = asObj(v, p)
        keys(o, p, Set("name", "clock", "in", "out", "pos", "routing"))
        val pos = o.get("pos") match {
          case Some(x: JObj) =>
            keys(x, s"$p.pos", Set("x", "y"))
            Some((int(x, "x", s"$p.pos"), int(x, "y", s"$p.pos")))
          case Some(_) => throw Ir.IrException(s"$p.pos must be an object")
          case None => None
        }
        RouterV2(
          name = str(o, "name", p),
          clock = str(o, "clock", p),
          in = o.get("in").map(_ => int(o, "in", p)),
          out = o.get("out").map(_ => int(o, "out", p)),
          pos = pos,
          routing = optStr(o, "routing", "any")
        )
      }

    def linksOf(items: Vector[JValue], path: String, chi: ChiBlockV2): Seq[LinkV2] =
      items.zipWithIndex.map { case (v, i) =>
        val p = s"$path[$i]"
        val o = asObj(v, p)
        keys(o, p, Set("from", "to", "width", "cdc", "pipe", "asyncFifoDepth", "dateline",
          "channels", "lcreditDepth"))
        val cdc = optStr(o, "cdc", "sync")
        val channels = o.get("channels") match {
          case Some(a: JArr) => a.items.map {
            case JStr(s) => s
            case _ => throw Ir.IrException(s"$p.channels must contain strings")
          }
          case Some(_) => throw Ir.IrException(s"$p.channels must be an array")
          case None => V2Defaults.channelsOf(chi.dvmEnable)
        }
        LinkV2(
          from = str(o, "from", p),
          to = str(o, "to", p),
          width = optInt(o, "width", chi.datWidth),
          cdc = cdc,
          pipe = optInt(o, "pipe", 0),
          asyncFifoDepth = optInt(o, "asyncFifoDepth", if (cdc == "async") 2 else 0),
          dateline = optBool(o, "dateline", false),
          channels = channels,
          lcreditDepth = optInt(o, "lcreditDepth", chi.lcreditDepth)
        )
      }

    def timingOf(fab: JObj, path: String): TimingSpec = fab.get("timing") match {
      case Some(o: JObj) =>
        keys(o, s"$path.timing", Set("maxFanin", "maxCombDepth"))
        TimingSpec(optInt(o, "maxFanin", 8), optInt(o, "maxCombDepth", 12))
      case Some(_) => throw Ir.IrException(s"$path.timing must be an object")
      case None => TimingSpec(8, 12)
    }

    val rootObj = asObj(root, "$")
    keys(rootObj, "$", Set("schema", "meta", "clockDomains", "nodes", "sam", "qos", "chi",
      "fabric", "partitions", "routes", "subsystems", "nocLinks"))
    val schema = str(rootObj, "schema", "$")
    if (schema != "loom/v2")
      throw Ir.IrException(s"$$ schema must be 'loom/v2', got '$schema' (use Upgrade.v1ToV2 for loom/v1)")

    val metaObj = asObj(field(rootObj, "meta", "$"), "$.meta")
    keys(metaObj, "$.meta", Set("name"))
    val meta = Meta(str(metaObj, "name", "$.meta"), schema)

    val clocks = clocksOf(
      arrOf(field(rootObj, "clockDomains", "$"), "$.clockDomains"), "$.clockDomains")
    val chi = chiOf(objBlock(rootObj, "chi", "$"), "$.chi", ChiBlockV2())

    // IR-80 (UP-07): a non-empty subsystems[] replaces the top-level fabric;
    // nodes/fabric become optional (CHK-55 forbids them alongside subsystems).
    val hierarchical = rootObj.get("subsystems").exists {
      case a: JArr => a.items.nonEmpty
      case _ => false
    }
    val nodes =
      if (rootObj.get("nodes").isEmpty && hierarchical) Nil
      else nodesOf(arrOf(field(rootObj, "nodes", "$"), "$.nodes"), "$.nodes", chi)
    val sam = samOf(rootObj, "$", nodes)
    val qos = qosOf(objBlock(rootObj, "qos", "$"), "$.qos")

    val fabOpt = rootObj.get("fabric") match {
      case None if hierarchical => None
      case _ => Some(asObj(field(rootObj, "fabric", "$"), "$.fabric"))
    }
    fabOpt.foreach(f => keys(f, "$.fabric", Set("routers", "links", "timing")))
    val routers = fabOpt.fold(Seq.empty[RouterV2])(f =>
      routersOf(arrOf(field(f, "routers", "$.fabric"), "$.fabric.routers"), "$.fabric.routers"))
    val links = fabOpt.fold(Seq.empty[LinkV2])(f =>
      linksOf(arrOf(field(f, "links", "$.fabric"), "$.fabric.links"), "$.fabric.links", chi))
    val timing = fabOpt.fold(TimingSpec(8, 12))(f => timingOf(f, "$.fabric"))

    val partitions = rootObj.get("partitions") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.partitions[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("name", "clock", "members", "boundaryPipe"))
          val members = arrOf(field(o, "members", p), s"$p.members").map {
            case JStr(s) => s
            case _ => throw Ir.IrException(s"$p.members must contain strings")
          }
          PartitionSpec(str(o, "name", p), str(o, "clock", p), members,
            optInt(o, "boundaryPipe", 1))
        }
      case Some(_) => throw Ir.IrException("$.partitions must be an array")
      case None => Vector.empty
    }

    // IR-70–IR-74 (UP-01): optional explicit routes / forbidden pairs.
    val routes = rootObj.get("routes") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.routes[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("from", "to", "path", "forbid"))
          val path = o.get("path") match {
            case Some(pa: JArr) => pa.items.map {
              case JStr(s) => s
              case _ => throw Ir.IrException(s"$p.path must contain strings")
            }
            case Some(_) => throw Ir.IrException(s"$p.path must be an array")
            case None => Vector.empty
          }
          RouteSpec(str(o, "from", p), str(o, "to", p), path, optBool(o, "forbid", false))
        }
      case Some(_) => throw Ir.IrException("$.routes must be an array")
      case None => Vector.empty
    }

    // IR-80–IR-87 (UP-07): subsystem NoCs; defaults resolved against the
    // top-level blocks so the projection is a self-contained flat IrV2.
    val subsystems = rootObj.get("subsystems") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.subsystems[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("name", "clockDomains", "nodes", "sam", "qos", "chi", "fabric"))
          val sClocks = o.get("clockDomains") match {
            case Some(_) => clocksOf(
              arrOf(field(o, "clockDomains", p), s"$p.clockDomains"), s"$p.clockDomains")
            case None => clocks
          }
          val sChi = chiOf(objBlock(o, "chi", p), s"$p.chi", chi)
          val sNodes = nodesOf(arrOf(field(o, "nodes", p), s"$p.nodes"), s"$p.nodes", sChi)
          val sSam = samOf(o, p, sNodes)
          val sQos = o.get("qos") match {
            case Some(_) => qosOf(objBlock(o, "qos", p), s"$p.qos")
            case None => qos
          }
          val sFab = asObj(field(o, "fabric", p), s"$p.fabric")
          keys(sFab, s"$p.fabric", Set("routers", "links", "timing"))
          val sRouters = routersOf(
            arrOf(field(sFab, "routers", s"$p.fabric"), s"$p.fabric.routers"), s"$p.fabric.routers")
          val sLinks = linksOf(
            arrOf(field(sFab, "links", s"$p.fabric"), s"$p.fabric.links"), s"$p.fabric.links", sChi)
          SubsystemSpec(str(o, "name", p), sClocks, sNodes, sSam, sQos, sChi,
            sRouters, sLinks, timingOf(sFab, s"$p.fabric"))
        }
      case Some(_) => throw Ir.IrException("$.subsystems must be an array")
      case None => Nil
    }

    // IR-88–IR-93 (UP-07): inter-NoC bridge declarations; windows are
    // {base,size} pairs, cdc/asyncFifoDepth mirror IR-57/59, dvmForward
    // defaults to false (each NoC stays DVM-autonomous, SEM-20).
    val nocLinks = rootObj.get("nocLinks") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.nocLinks[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("from", "to", "fromRouter", "toRouter", "windows", "cdc",
            "asyncFifoDepth", "dvmForward", "outstanding"))
          val windows = arrOf(field(o, "windows", p), s"$p.windows").zipWithIndex.map {
            case (wv, j) =>
              val wp = s"$p.windows[$j]"
              val wo = asObj(wv, wp)
              keys(wo, wp, Set("base", "size"))
              (optLong(wo, "base", 0L), optLong(wo, "size", AddressMap.DefaultSize))
          }
          val cdc = optStr(o, "cdc", "sync")
          NocLinkSpec(
            from = str(o, "from", p),
            to = str(o, "to", p),
            fromRouter = str(o, "fromRouter", p),
            toRouter = str(o, "toRouter", p),
            windows = windows,
            cdc = cdc,
            asyncFifoDepth = optInt(o, "asyncFifoDepth", if (cdc == "async") 2 else 0),
            dvmForward = optBool(o, "dvmForward", false),
            outstanding = optInt(o, "outstanding", 1))
        }
      case Some(_) => throw Ir.IrException("$.nocLinks must be an array")
      case None => Nil
    }

    IrV2(meta, clocks, nodes, sam, qos, chi, routers, links, timing,
      partitions, routes, unknown.toSeq, subsystems, nocLinks)
  }

  /** GEN-25/SEM-20 (UP-07): project one subsystem into an independent flat
    * IrV2 (meta.name = subsystem name; defaults already resolved by the
    * parser; partitions/routes empty per the phase-1 CHK-58 limit). The full
    * ChecksV2/ChiGates/ChiAssemble pipeline runs on the projection unchanged.
    */
  def projectSubsystem(v2: IrV2, s: SubsystemSpec): IrV2 =
    IrV2(Meta(s.name, v2.meta.schema), s.clockDomains, s.nodes, s.sam, s.qos, s.chi,
      s.routers, s.links, s.timing, partitions = Nil)

  /** Normalized route entry rendering (IR-70–74): `path` is omitted when
    * empty (forbid-only entries); `forbid` is always explicit (GEN-03).
    */
  def routeJson(r: RouteSpec): JValue = {
    val base = Vector("from" -> JStr(r.from), "to" -> JStr(r.to))
    val withPath =
      if (r.path.nonEmpty) base :+ ("path" -> JArr(r.path.map(JStr(_)).toVector)) else base
    JObj(withPath :+ ("forbid" -> JBool(r.forbid)))
  }

  /** Normalized nocLink entry rendering (IR-88–94): windows are {base,size}
    * objects; cdc/asyncFifoDepth/dvmForward are always explicit (GEN-03);
    * outstanding renders only when ≠1 so default bridges keep their hash.
    */
  def nocLinkJson(l: NocLinkSpec): JValue =
    JObj(Vector(
      "from" -> JStr(l.from), "to" -> JStr(l.to),
      "fromRouter" -> JStr(l.fromRouter), "toRouter" -> JStr(l.toRouter),
      "windows" -> JArr(l.windows.map { case (b, sz) =>
        JObj(Vector("base" -> JNum(BigDecimal(b)), "size" -> JNum(BigDecimal(sz))))
      }.toVector),
      "cdc" -> JStr(l.cdc),
      "asyncFifoDepth" -> JNum(BigDecimal(l.asyncFifoDepth)),
      "dvmForward" -> JBool(l.dvmForward)) ++
      (if (l.outstanding != 1) Vector("outstanding" -> JNum(BigDecimal(l.outstanding)))
       else Vector.empty))

  /** Fully-explicit normalized rendering (GEN-03/GEN-06): hash and canonical
    * reports consume this, never the raw input root.
    */
  def toJson(v: IrV2): JValue = {
    def s(x: String): JValue = JStr(x)
    def n(x: Long): JValue = JNum(BigDecimal(x))
    def b(x: Boolean): JValue = JBool(x)
    def obj(fs: (String, JValue)*): JValue = JObj(fs.toVector)
    def arr(xs: Seq[JValue]): JValue = JArr(xs.toVector)

    // Shared block renderers (UP-07 refactor): subsystem entries render with
    // the exact same normalized shapes as the top-level blocks.
    def nodeJson(x: NodeV2): JValue = {
      val base = Vector(
        "id" -> s(x.id), "kind" -> s(x.kind), "clock" -> s(x.clock), "attach" -> s(x.attach),
        "protocol" -> s(x.protocol), "dataWidth" -> n(x.dataWidth),
        "outstanding" -> n(x.outstanding), "qos" -> n(x.qos))
      val withId = x.nodeId.fold(base)(id => base :+ ("nodeId" -> n(id)))
      // §3.13/§3.14 rendering rule: qosGen/adaptFifoDepth are omitted when
      // absent (same conditional rule as nodeId/routes) so pre-v1.3.0 hash
      // baselines stay identical; the adaptFifoDepth default 4 is an
      // assembly/report caliber, never rendered.
      val withQosGen = x.qosGen.fold(withId)(q => withId :+ ("qosGen" ->
        obj("kind" -> s(q.kind), "bandwidth" -> n(q.bandwidth), "saturation" -> n(q.saturation))))
      JObj(x.adaptFifoDepth.fold(withQosGen)(d => withQosGen :+ ("adaptFifoDepth" -> n(d))))
    }
    def samJson(w: SamWindowV2): JValue =
      obj("base" -> n(w.base), "size" -> n(w.size), "tgtNode" -> s(w.tgtNode))
    def clocksJson(cs: Seq[ClockDomain]): JValue =
      arr(cs.map(c => obj("id" -> s(c.id), "freqMHz" -> JNum(BigDecimal(c.freqMHz)))))
    def qosJson(q: QosV2): JValue = obj(
      "mapping" -> arr(q.mapping.map(m =>
        obj("qosMin" -> n(m.qosMin), "qosMax" -> n(m.qosMax), "class" -> s(m.className)))),
      "classes" -> arr(q.classes.map(c =>
        obj("name" -> s(c.name), "vcs" -> n(c.vcs), "depth" -> n(c.depth),
          "arbitration" -> s(c.arbitration), "weight" -> n(c.weight)))))
    def chiJson(c: ChiBlockV2): JValue = obj(
      "datWidth" -> n(c.datWidth), "txnIdWidth" -> n(c.txnIdWidth),
      "nodeIdWidth" -> n(c.nodeIdWidth), "addrWidth" -> n(c.addrWidth),
      "outstanding" -> n(c.outstanding), "atomicEnable" -> b(c.atomicEnable),
      "atomicEntries" -> n(c.atomicEntries), "dvmEnable" -> b(c.dvmEnable),
      "portMode" -> s(c.portMode), "pcrdTypes" -> n(c.pcrdTypes),
      "lcreditDepth" -> n(c.lcreditDepth))
    def routerJson(r: RouterV2): JValue = {
      val base = Vector("name" -> s(r.name), "clock" -> s(r.clock), "routing" -> s(r.routing))
      val withIn = r.in.fold(base)(x => base :+ ("in" -> n(x)))
      val withOut = r.out.fold(withIn)(x => withIn :+ ("out" -> n(x)))
      JObj(r.pos.fold(withOut) { case (x, y) => withOut :+ ("pos" -> obj("x" -> n(x), "y" -> n(y))) })
    }
    def linkJson(l: LinkV2): JValue = obj(
      "from" -> s(l.from), "to" -> s(l.to), "width" -> n(l.width), "cdc" -> s(l.cdc),
      "pipe" -> n(l.pipe), "asyncFifoDepth" -> n(l.asyncFifoDepth), "dateline" -> b(l.dateline),
      "channels" -> arr(l.channels.map(s)), "lcreditDepth" -> n(l.lcreditDepth))
    def timingJson(t: TimingSpec): JValue =
      obj("maxFanin" -> n(t.maxFanin), "maxCombDepth" -> n(t.maxCombDepth))
    def fabricJson(routers: Seq[RouterV2], links: Seq[LinkV2], t: TimingSpec): JValue =
      obj("routers" -> arr(routers.map(routerJson)), "links" -> arr(links.map(linkJson)),
        "timing" -> timingJson(t))
    def subsystemJson(x: SubsystemSpec): JValue = obj(
      "name" -> s(x.name),
      "clockDomains" -> clocksJson(x.clockDomains),
      "nodes" -> arr(x.nodes.map(nodeJson)),
      "sam" -> arr(x.sam.map(samJson)),
      "qos" -> qosJson(x.qos),
      "chi" -> chiJson(x.chi),
      "fabric" -> fabricJson(x.routers, x.links, x.timing))

    val partsJ = v.partitions.map(p => obj(
      "name" -> s(p.name), "clock" -> s(p.clock), "members" -> arr(p.members.map(s)),
      "boundaryPipe" -> n(p.boundaryPipe)))

    val base = Vector(
      "schema" -> s("loom/v2"),
      "meta" -> obj("name" -> s(v.meta.name)),
      "clockDomains" -> clocksJson(v.clockDomains),
      "nodes" -> arr(v.nodes.map(nodeJson)),
      "sam" -> arr(v.sam.map(samJson)),
      "qos" -> qosJson(v.qos),
      "chi" -> chiJson(v.chi),
      "fabric" -> fabricJson(v.routers, v.links, v.timing),
      "partitions" -> arr(partsJ)
    )
    // §3.12 rendering rule: an empty routes block is omitted entirely (same
    // conditional rule as nodeId) so pre-UP-01 hash baselines stay identical.
    val withRoutes =
      if (v.routes.isEmpty) base else base :+ ("routes" -> arr(v.routes.map(routeJson)))
    // §3.15 rendering rule (UP-07): empty subsystems/nocLinks blocks are
    // omitted so pre-v1.5.0 hash baselines stay identical.
    val withSubs =
      if (v.subsystems.isEmpty) withRoutes
      else withRoutes :+ ("subsystems" -> arr(v.subsystems.map(subsystemJson)))
    JObj(if (v.nocLinks.isEmpty) withSubs
      else withSubs :+ ("nocLinks" -> arr(v.nocLinks.map(nocLinkJson))))
  }

  def hash(v: IrV2): String = Canonical.hash(toJson(v))
}