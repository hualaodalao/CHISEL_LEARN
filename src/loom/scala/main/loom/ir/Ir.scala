package loom.ir

import java.nio.file.{Files, Path}

final case class Meta(name: String, schema: String)
final case class ClockDomain(id: String, freqMHz: Double)
final case class Endpoint(
    id: String,
    role: String,
    protocol: String,
    dataWidth: Int,
    addrWidth: Int,
    outstanding: Int,
    clock: String,
    qos: String,
    attach: String,
    base: Long = -1L,
    size: Long = 0L
)
final case class FabricNode(name: String, clock: String)
final case class RouterSpec(
    name: String,
    in: Int,
    out: Int,
    clock: String,
    switchKind: String,
    banks: Int,
    pos: Option[(Int, Int)] = None,
    routing: String = "any"
)
final case class LinkSpec(
    from: String,
    to: String,
    width: Int,
    cdc: String,
    pipe: Int,
    asyncFifoDepth: Int,
    direction: String = "bidirectional",
    dateline: Boolean = false
)
final case class TimingSpec(maxFanin: Int, maxCombDepth: Int)
final case class QosClass(
    name: String,
    vcs: Int,
    depth: Int,
    arbitration: String,
    weight: Int,
    maxPacketBeats: Int = 8
)
final case class PartitionSpec(
    name: String,
    clock: String,
    members: Seq[String],
    boundaryPipe: Int = 1
)

final case class Ir(
    meta: Meta,
    clockDomains: Seq[ClockDomain],
    endpoints: Seq[Endpoint],
    mnets: Seq[FabricNode],
    snets: Seq[FabricNode],
    routers: Seq[RouterSpec],
    links: Seq[LinkSpec],
    timing: TimingSpec,
    qosClasses: Seq[QosClass],
    partitions: Seq[PartitionSpec] = Nil,
    axiIdWidth: Int = LoomDefaults.AxiIdWidth
)

object Ir {
  final case class IrException(msg: String) extends Exception(msg)

  def load(path: Path): Ir = fromJson(Json.parse(Files.readString(path)))

  def fromJson(root: JValue): Ir = {
    val rootObj = asObj(root, "$")
    val schema = str(rootObj, "schema", "$")
    val metaObj = asObj(field(rootObj, "meta", "$"), "$.meta")
    val meta = Meta(name = str(metaObj, "name", "$.meta"), schema = schema)

    val clocks = arrOf(field(rootObj, "clockDomains", "$"), "$.clockDomains").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.clockDomains[$i]"
        val o = asObj(v, p)
        ClockDomain(str(o, "id", p), num(o, "freqMHz", p))
    }

    val endpoints = arrOf(field(rootObj, "endpoints", "$"), "$.endpoints").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.endpoints[$i]"
        val o = asObj(v, p)
        Endpoint(
          id = str(o, "id", p),
          role = str(o, "role", p),
          protocol = optStr(o, "protocol", "native"),
          dataWidth = int(o, "dataWidth", p),
          addrWidth = int(o, "addrWidth", p),
          outstanding = optInt(o, "outstanding", 1),
          clock = str(o, "clock", p),
          qos = optStr(o, "qos", "bulk"),
          attach = str(o, "attach", p),
          base = optLong(o, "base", -1L),
          size = optLong(o, "size", 0L)
        )
    }

    val fab = asObj(field(rootObj, "fabric", "$"), "$.fabric")
    val mnets = nodes(fab, "mnet", "$.fabric")
    val snets = nodes(fab, "snet", "$.fabric")

    val routers = arrOf(field(fab, "routers", "$.fabric"), "$.fabric.routers").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.fabric.routers[$i]"
        val o = asObj(v, p)
        val sw = o.get("switch") match {
          case Some(s: JObj) => s
          case Some(_) => throw IrException(s"$p.switch must be an object")
          case None => JObj(Vector.empty)
        }
        val pos = o.get("pos") match {
          case Some(x: JObj) => Some((int(x, "x", s"$p.pos"), int(x, "y", s"$p.pos")))
          case Some(_) => throw IrException(s"$p.pos must be an object")
          case None => None
        }
        RouterSpec(
          name = str(o, "name", p),
          in = int(o, "in", p),
          out = int(o, "out", p),
          clock = str(o, "clock", p),
          switchKind = optStr(sw, "kind", "flat"),
          banks = optInt(sw, "banks", 1),
          pos = pos,
          routing = optStr(o, "routing", "any")
        )
    }

    val links = arrOf(field(fab, "links", "$.fabric"), "$.fabric.links").zipWithIndex.map {
      case (v, i) =>
        val p = s"$$.fabric.links[$i]"
        val o = asObj(v, p)
        val cdc = optStr(o, "cdc", "sync")
        LinkSpec(
          from = str(o, "from", p),
          to = str(o, "to", p),
          width = int(o, "width", p),
          cdc = cdc,
          pipe = optInt(o, "pipe", 0),
          asyncFifoDepth = optInt(o, "asyncFifoDepth", if (cdc == "async") 2 else 0),
          direction = optStr(o, "direction", "bidirectional"),
          dateline = optBool(o, "dateline", false)
        )
    }

    val timing = fab.get("timing") match {
      case Some(o: JObj) =>
        TimingSpec(optInt(o, "maxFanin", 8), optInt(o, "maxCombDepth", 12))
      case Some(_) => throw IrException("$.fabric.timing must be an object")
      case None => TimingSpec(8, 12)
    }

    val qosClasses = rootObj.get("qos") match {
      case Some(q: JObj) =>
        q.get("classes") match {
          case Some(a: JArr) =>
            a.items.zipWithIndex.map { case (v, i) =>
              val p = s"$$.qos.classes[$i]"
              val o = asObj(v, p)
              val name = str(o, "name", p)
              QosClass(
                name = name,
                vcs = optInt(o, "vcs", 1),
                depth = optInt(o, "depth", 8),
                arbitration = optStr(o, "arbitration", "wrr"),
                weight = optInt(o, "weight", 1),
                maxPacketBeats = optInt(o, "maxPacketBeats", LoomDefaults.defaultMaxPacket(name))
              )
            }
          case Some(_) => throw IrException("$.qos.classes must be an array")
          case None => Vector.empty
        }
      case Some(_) => throw IrException("$.qos must be an object")
      case None => Vector.empty
    }

    val partitions = rootObj.get("partitions") match {
      case Some(a: JArr) =>
        a.items.zipWithIndex.map { case (v, i) =>
          val p = s"$$.partitions[$i]"
          val o = asObj(v, p)
          val members = arrOf(field(o, "members", p), s"$p.members").map {
            case JStr(s) => s
            case _ => throw IrException(s"$p.members must contain strings")
          }
          PartitionSpec(
            name = str(o, "name", p),
            clock = str(o, "clock", p),
            members = members,
            boundaryPipe = optInt(o, "boundaryPipe", 1)
          )
        }
      case Some(_) => throw IrException("$.partitions must be an array")
      case None => Vector.empty
    }

    Ir(meta, clocks, endpoints, mnets, snets, routers, links, timing, qosClasses, partitions,
      optInt(rootObj, "axiIdWidth", LoomDefaults.AxiIdWidth))
  }

  private def nodes(fab: JObj, key: String, path: String): Seq[FabricNode] =
    arrOf(field(fab, key, path), s"$path.$key").zipWithIndex.map { case (v, i) =>
      val p = s"$path.$key[$i]"
      val o = asObj(v, p)
      FabricNode(str(o, "name", p), str(o, "clock", p))
    }

  private[ir] def asObj(v: JValue, path: String): JObj = v match {
    case o: JObj => o
    case _ => throw IrException(s"$path must be an object")
  }

  private[ir] def arrOf(v: JValue, path: String): Vector[JValue] = v match {
    case a: JArr => a.items
    case _ => throw IrException(s"$path must be an array")
  }

  private[ir] def field(o: JObj, key: String, path: String): JValue =
    o.get(key).getOrElse(throw IrException(s"$path.$key is required"))

  private[ir] def str(o: JObj, key: String, path: String): String = field(o, key, path) match {
    case JStr(s) => s
    case _ => throw IrException(s"$path.$key must be a string")
  }

  private[ir] def int(o: JObj, key: String, path: String): Int = field(o, key, path) match {
    case JNum(n) if n.isValidInt => n.toInt
    case _ => throw IrException(s"$path.$key must be an integer")
  }

  private[ir] def num(o: JObj, key: String, path: String): Double = field(o, key, path) match {
    case JNum(n) => n.toDouble
    case _ => throw IrException(s"$path.$key must be a number")
  }

  private[ir] def optStr(o: JObj, key: String, default: String): String = o.get(key) match {
    case None => default
    case Some(JStr(s)) => s
    case Some(_) => throw IrException(s"$$.$key must be a string")
  }

  private[ir] def optInt(o: JObj, key: String, default: Int): Int = o.get(key) match {
    case None => default
    case Some(JNum(n)) if n.isValidInt => n.toInt
    case Some(_) => throw IrException(s"$$.$key must be an integer")
  }

  private[ir] def optLong(o: JObj, key: String, default: Long): Long = o.get(key) match {
    case None => default
    case Some(JNum(n)) if n.isValidLong => n.toLong
    case Some(_) => throw IrException(s"$$.$key must be an integer")
  }

  private[ir] def optBool(o: JObj, key: String, default: Boolean): Boolean = o.get(key) match {
    case None => default
    case Some(JBool(b)) => b
    case Some(_) => throw IrException(s"$$.$key must be a boolean")
  }
}
