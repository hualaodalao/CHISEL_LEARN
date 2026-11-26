package loom.ir

import java.nio.file.{Files, Path}

import scala.collection.mutable

/** loom/v2 node (IR-13–IR-21). One CHI port: NodeID + REQ/RSP/DAT (+SNP). */
final case class NodeV2(
    id: String,
    kind: String,
    nodeId: Option[Int],
    clock: String,
    attach: String,
    protocol: String,
    dataWidth: Int,
    outstanding: Int,
    qos: Int
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

/** Normalized loom/v2 object: every optional field filled per GEN-03.
  * `unknown` collects unrecognized field paths for the C0 unknown-key gate.
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
    unknown: Seq[String] = Nil
)

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

    val rootObj = asObj(root, "$")
    keys(rootObj, "$", Set("schema", "meta", "clockDomains", "nodes", "sam", "qos", "chi",
      "fabric", "partitions"))
    val schema = str(rootObj, "schema", "$")
    if (schema != "loom/v2")
      throw Ir.IrException(s"$$ schema must be 'loom/v2', got '$schema' (use Upgrade.v1ToV2 for loom/v1)")

    val metaObj = asObj(field(rootObj, "meta", "$"), "$.meta")
    keys(metaObj, "$.meta", Set("name"))
    val meta = Meta(str(metaObj, "name", "$.meta"), schema)

    val clocks = arrOf(field(rootObj, "clockDomains", "$"), "$.clockDomains").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.clockDomains[$i]"
        val o = asObj(v, p)
        keys(o, p, Set("id", "freqMHz"))
        ClockDomain(str(o, "id", p), num(o, "freqMHz", p))
    }

    val chiObj = rootObj.get("chi") match {
      case Some(o: JObj) => o
      case Some(_) => throw Ir.IrException("$.chi must be an object")
      case None => JObj(Vector.empty)
    }
    keys(chiObj, "$.chi", Set("datWidth", "txnIdWidth", "nodeIdWidth", "addrWidth", "outstanding",
      "atomicEnable", "atomicEntries", "dvmEnable", "portMode", "pcrdTypes", "lcreditDepth"))
    val chi = ChiBlockV2(
      datWidth = optInt(chiObj, "datWidth", 256),
      txnIdWidth = optInt(chiObj, "txnIdWidth", 8),
      nodeIdWidth = optInt(chiObj, "nodeIdWidth", 7),
      addrWidth = optInt(chiObj, "addrWidth", 48),
      outstanding = optInt(chiObj, "outstanding", 64),
      atomicEnable = optBool(chiObj, "atomicEnable", true),
      atomicEntries = optInt(chiObj, "atomicEntries", 4),
      dvmEnable = optBool(chiObj, "dvmEnable", true),
      portMode = optStr(chiObj, "portMode", "axi4"),
      pcrdTypes = optInt(chiObj, "pcrdTypes", 1),
      lcreditDepth = optInt(chiObj, "lcreditDepth", 4)
    )

    val nodes = arrOf(field(rootObj, "nodes", "$"), "$.nodes").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.nodes[$i]"
        val o = asObj(v, p)
        keys(o, p, Set("id", "kind", "nodeId", "clock", "attach", "protocol", "dataWidth",
          "outstanding", "qos"))
        NodeV2(
          id = str(o, "id", p),
          kind = str(o, "kind", p),
          nodeId = o.get("nodeId").map(_ => int(o, "nodeId", p)),
          clock = str(o, "clock", p),
          attach = str(o, "attach", p),
          protocol = optStr(o, "protocol", V2Defaults.protocolOf(chi.portMode)),
          dataWidth = optInt(o, "dataWidth", chi.datWidth),
          outstanding = optInt(o, "outstanding", chi.outstanding),
          qos = optInt(o, "qos", 0)
        )
    }

    val snIds = nodes.filter(n => n.kind == "sn-f" || n.kind == "sn-i").map(_.id)
    val sam = rootObj.get("sam") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.sam[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("base", "size", "tgtNode"))
          SamWindowV2(optLong(o, "base", 0L), optLong(o, "size", AddressMap.DefaultSize),
            str(o, "tgtNode", p))
        }
      case Some(_) => throw Ir.IrException("$.sam must be an array")
      case None =>
        snIds.zipWithIndex.map { case (id, i) =>
          SamWindowV2(i.toLong * AddressMap.DefaultSize, AddressMap.DefaultSize, id)
        }
    }

    val qosObj = rootObj.get("qos") match {
      case Some(o: JObj) => o
      case Some(_) => throw Ir.IrException("$.qos must be an object")
      case None => JObj(Vector.empty)
    }
    keys(qosObj, "$.qos", Set("mapping", "classes"))
    val mapping = qosObj.get("mapping") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.qos.mapping[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("qosMin", "qosMax", "class"))
          QosMappingV2(int(o, "qosMin", p), int(o, "qosMax", p), str(o, "class", p))
        }
      case Some(_) => throw Ir.IrException("$.qos.mapping must be an array")
      case None => V2Defaults.QosMapping
    }
    val classes = qosObj.get("classes") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.qos.classes[$i]"
          val o = asObj(v, p)
          keys(o, p, Set("name", "vcs", "depth", "arbitration", "weight"))
          QosClassV2(
            name = str(o, "name", p),
            vcs = optInt(o, "vcs", 1),
            depth = optInt(o, "depth", 8),
            arbitration = optStr(o, "arbitration", "wrr"),
            weight = optInt(o, "weight", 1)
          )
        }
      case Some(_) => throw Ir.IrException("$.qos.classes must be an array")
      case None => V2Defaults.QosClasses
    }

    val fab = asObj(field(rootObj, "fabric", "$"), "$.fabric")
    keys(fab, "$.fabric", Set("routers", "links", "timing"))

    val routers = arrOf(field(fab, "routers", "$.fabric"), "$.fabric.routers").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.fabric.routers[$i]"
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

    val links = arrOf(field(fab, "links", "$.fabric"), "$.fabric.links").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.fabric.links[$i]"
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

    val timing = fab.get("timing") match {
      case Some(o: JObj) =>
        keys(o, "$.fabric.timing", Set("maxFanin", "maxCombDepth"))
        TimingSpec(optInt(o, "maxFanin", 8), optInt(o, "maxCombDepth", 12))
      case Some(_) => throw Ir.IrException("$.fabric.timing must be an object")
      case None => TimingSpec(8, 12)
    }

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

    IrV2(meta, clocks, nodes, sam, QosV2(mapping, classes), chi, routers, links, timing,
      partitions, unknown.toSeq)
  }

  /** Fully-explicit normalized rendering (GEN-03/GEN-06): hash and canonical
    * reports consume this, never the raw input root.
    */
  def toJson(v: IrV2): JValue = {
    def s(x: String): JValue = JStr(x)
    def n(x: Long): JValue = JNum(BigDecimal(x))
    def b(x: Boolean): JValue = JBool(x)
    def obj(fs: (String, JValue)*): JValue = JObj(fs.toVector)
    def arr(xs: Seq[JValue]): JValue = JArr(xs.toVector)

    val nodesJ = v.nodes.map { x =>
      val base = Vector(
        "id" -> s(x.id), "kind" -> s(x.kind), "clock" -> s(x.clock), "attach" -> s(x.attach),
        "protocol" -> s(x.protocol), "dataWidth" -> n(x.dataWidth),
        "outstanding" -> n(x.outstanding), "qos" -> n(x.qos))
      JObj(x.nodeId.fold(base)(id => base :+ ("nodeId" -> n(id))))
    }
    val samJ = v.sam.map(w => obj("base" -> n(w.base), "size" -> n(w.size), "tgtNode" -> s(w.tgtNode)))
    val mappingJ = v.qos.mapping.map(m =>
      obj("qosMin" -> n(m.qosMin), "qosMax" -> n(m.qosMax), "class" -> s(m.className)))
    val classesJ = v.qos.classes.map(c =>
      obj("name" -> s(c.name), "vcs" -> n(c.vcs), "depth" -> n(c.depth),
        "arbitration" -> s(c.arbitration), "weight" -> n(c.weight)))
    val chiJ = obj(
      "datWidth" -> n(v.chi.datWidth), "txnIdWidth" -> n(v.chi.txnIdWidth),
      "nodeIdWidth" -> n(v.chi.nodeIdWidth), "addrWidth" -> n(v.chi.addrWidth),
      "outstanding" -> n(v.chi.outstanding), "atomicEnable" -> b(v.chi.atomicEnable),
      "atomicEntries" -> n(v.chi.atomicEntries), "dvmEnable" -> b(v.chi.dvmEnable),
      "portMode" -> s(v.chi.portMode), "pcrdTypes" -> n(v.chi.pcrdTypes),
      "lcreditDepth" -> n(v.chi.lcreditDepth))
    val routersJ = v.routers.map { r =>
      val base = Vector("name" -> s(r.name), "clock" -> s(r.clock), "routing" -> s(r.routing))
      val withIn = r.in.fold(base)(x => base :+ ("in" -> n(x)))
      val withOut = r.out.fold(withIn)(x => withIn :+ ("out" -> n(x)))
      JObj(r.pos.fold(withOut) { case (x, y) => withOut :+ ("pos" -> obj("x" -> n(x), "y" -> n(y))) })
    }
    val linksJ = v.links.map(l => obj(
      "from" -> s(l.from), "to" -> s(l.to), "width" -> n(l.width), "cdc" -> s(l.cdc),
      "pipe" -> n(l.pipe), "asyncFifoDepth" -> n(l.asyncFifoDepth), "dateline" -> b(l.dateline),
      "channels" -> arr(l.channels.map(s)), "lcreditDepth" -> n(l.lcreditDepth)))
    val partsJ = v.partitions.map(p => obj(
      "name" -> s(p.name), "clock" -> s(p.clock), "members" -> arr(p.members.map(s)),
      "boundaryPipe" -> n(p.boundaryPipe)))

    obj(
      "schema" -> s("loom/v2"),
      "meta" -> obj("name" -> s(v.meta.name)),
      "clockDomains" -> arr(v.clockDomains.map(c => obj("id" -> s(c.id), "freqMHz" -> JNum(BigDecimal(c.freqMHz))))),
      "nodes" -> arr(nodesJ),
      "sam" -> arr(samJ),
      "qos" -> obj("mapping" -> arr(mappingJ), "classes" -> arr(classesJ)),
      "chi" -> chiJ,
      "fabric" -> obj(
        "routers" -> arr(routersJ),
        "links" -> arr(linksJ),
        "timing" -> obj("maxFanin" -> n(v.timing.maxFanin), "maxCombDepth" -> n(v.timing.maxCombDepth))),
      "partitions" -> arr(partsJ)
    )
  }

  def hash(v: IrV2): String = Canonical.hash(toJson(v))
}