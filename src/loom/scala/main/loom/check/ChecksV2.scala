package loom.check

import scala.collection.mutable.ListBuffer

import loom.chi.{ChiGates, ChiWidths}
import loom.gen.ChiAssemble
import loom.ir.{AddressMap, Ir, IrV2, V2Defaults}

/** loom/v2 gates (ir_gen_spec §6, story S2): C0/C1/C2 + G4/G6/G8.
  *
  * Pure functions over the normalized IrV2. Retired raw keys (`direction`,
  * `switch`) are detected through `IrV2.unknown`, which the parser fills with
  * the unrecognized field paths (MIG-06). G1 (CHK-35–39) is delegated to
  * ChiGates.runV2 (S3); the partition gates (CHK-44–48, S4) run after G8; the
  * retired G7 key stays present and constant true for one version (GEN-17).
  *
  * Every message follows §6.1: `<gate>: [CHK-xx] <body>`.
  */
object ChecksV2 {
  final case class Result(gates: Vector[(String, Boolean)], errors: List[String])

  private val NamePattern = "[A-Za-z0-9_.-]+".r
  private val RequestKinds = Set("rn-i", "rn-d")
  private val SubordinateKinds = Set("sn-f", "sn-i")
  private val Kinds = RequestKinds ++ SubordinateKinds ++ Set("hn-i", "mn")
  private val Protocols = Set("axi4", "chi")
  private val PortModes = Set("axi4", "chi", "both")
  private val NodeDataWidths = Seq(32, 64, 128, 256, 512, 1024)
  private val DatWidths = Seq(128, 256, 512)
  private val TxnIdWidths = Seq(8, 12)
  private val NodeIdWidths = Seq(7, 11)
  private val AddrWidths = Seq(32, 40, 48)
  private val ChannelNames = Set("req", "rsp", "dat", "snp")
  private val RequiredChannels = Seq("req", "rsp", "dat")
  private val ClassNames = Seq("ctl", "lat", "bulk")

  private val LinkPath = """\$\.fabric\.links\[(\d+)\]\.(.+)""".r
  private val RouterPath = """\$\.fabric\.routers\[(\d+)\]\.(.+)""".r

  private def pow2(n: Long): Boolean = n > 0 && (n & (n - 1)) == 0

  private def duplicates(xs: Seq[String]): Seq[String] =
    xs.groupBy(identity).collect { case (k, v) if v.size > 1 => k }.toSeq.sorted

  /** NodeID space; an out-of-domain width falls back to the default tier so
    * CHK-06 stays meaningful alongside the CHK-13 error (spec §6.3 N-C0-c).
    */
  private def nodeIdSpace(width: Int): Int =
    if (NodeIdWidths.contains(width)) 1 << width else 1 << 7

  private def txnIdSpace(width: Int): Int =
    if (TxnIdWidths.contains(width)) 1 << width else 1 << 8

  private def addrSpace(width: Int): Int =
    if (AddrWidths.contains(width)) width else 48

  /** SEM-02/SEM-03 + AQ-01: segment order RN -> SN -> HN -> MN, declaration
    * order within a segment; explicit overrides are reserved first and the
    * auto allocator skips them. Returns (node index -> assigned id).
    */
  private def assignNodeIds(v2: IrV2): Seq[Int] = {
    def seg(kind: String): Int =
      if (RequestKinds.contains(kind)) 0
      else if (SubordinateKinds.contains(kind)) 1
      else if (kind == "hn-i") 2
      else 3
    val used = v2.nodes.flatMap(_.nodeId).toSet
    val auto = Iterator.from(0).filterNot(used.contains)
    val result = Array.fill(v2.nodes.length)(-1)
    v2.nodes.zipWithIndex.foreach { case (n, i) => n.nodeId.foreach(id => result(i) = id) }
    v2.nodes.zipWithIndex.sortBy { case (n, i) => (seg(n.kind), i) }.foreach { case (n, i) =>
      if (n.nodeId.isEmpty) result(i) = auto.next()
    }
    result.toSeq
  }

  /** C0: schema, fields, ranges and references (CHK-01–CHK-23 + unknown keys). */
  def runC0(v2: IrV2): List[String] = {
    val errs = ListBuffer.empty[String]
    val clockIds = v2.clockDomains.map(_.id)
    val routerNames = v2.routers.map(_.name)
    val routerByName = v2.routers.map(r => r.name -> r).toMap
    val nodeById = v2.nodes.map(n => n.id -> n).toMap
    val maxNodes = nodeIdSpace(v2.chi.nodeIdWidth)
    val txnCap = txnIdSpace(v2.chi.txnIdWidth)
    val addrWidth = addrSpace(v2.chi.addrWidth)

    // CHK-01
    if (v2.meta.schema != "loom/v2")
      errs += s"C0: [CHK-01] schema must be 'loom/v2' (or 'loom/v1' for upgrade), got '${v2.meta.schema}'"

    // CHK-02
    if (!NamePattern.matches(v2.meta.name))
      errs += s"C0: [CHK-02] meta.name '${v2.meta.name}' must match [A-Za-z0-9_.-]+"

    // CHK-03
    duplicates(clockIds).foreach(id => errs += s"C0: [CHK-03] clockDomain '$id' duplicate id")
    v2.clockDomains.foreach { c =>
      if (c.freqMHz <= 0) errs += s"C0: [CHK-03] clockDomain '${c.id}' freqMHz must be > 0"
    }

    // CHK-04
    duplicates(v2.nodes.map(_.id) ++ routerNames).foreach(n =>
      errs += s"C0: [CHK-04] duplicate node/router name '$n'")
    (v2.nodes.map(_.id) ++ routerNames).distinct.foreach { n =>
      if (!NamePattern.matches(n))
        errs += s"C0: [CHK-04] node/router name '$n' must match [A-Za-z0-9_.-]+"
    }

    // CHK-05
    v2.nodes.foreach { n =>
      if (!Kinds.contains(n.kind))
        errs += s"C0: [CHK-05] node '${n.id}' kind '${n.kind}' invalid; hn-f is a reserved placeholder"
    }

    // CHK-06 (SEM-02/03): explicit range + uniqueness, then assigned range.
    val seenIds = scala.collection.mutable.Set.empty[Int]
    v2.nodes.foreach { n =>
      n.nodeId.foreach { id =>
        if (id < 0 || id >= maxNodes)
          errs += s"C0: [CHK-06] node '${n.id}' nodeId $id out of range [0,${maxNodes - 1}]"
        if (!seenIds.add(id))
          errs += s"C0: [CHK-06] node '${n.id}' duplicate nodeId $id"
      }
    }
    val assigned = assignNodeIds(v2)
    v2.nodes.zip(assigned).foreach { case (n, id) =>
      if (n.nodeId.isEmpty && id >= maxNodes)
        errs += s"C0: [CHK-06] node '${n.id}' nodeId $id out of range [0,${maxNodes - 1}]"
    }

    // CHK-07
    v2.nodes.foreach { n =>
      if (!clockIds.contains(n.clock))
        errs += s"C0: [CHK-07] node '${n.id}' clock '${n.clock}' is not declared"
    }

    // CHK-08
    v2.nodes.foreach { n =>
      if (!Protocols.contains(n.protocol))
        errs += s"C0: [CHK-08] node '${n.id}' protocol '${n.protocol}' must be axi4 or chi"
    }

    // CHK-09
    v2.nodes.foreach { n =>
      if (!NodeDataWidths.contains(n.dataWidth))
        errs += s"C0: [CHK-09] node '${n.id}' dataWidth ${n.dataWidth} must be one of ${NodeDataWidths.mkString(",")}"
    }

    // CHK-10
    v2.nodes.foreach { n =>
      if (n.outstanding < 1 || n.outstanding > txnCap)
        errs += s"C0: [CHK-10] node '${n.id}' outstanding ${n.outstanding} must be in [1,$txnCap]"
    }

    // CHK-11
    v2.nodes.foreach { n =>
      if (n.qos < 0 || n.qos > 15)
        errs += s"C0: [CHK-11] node '${n.id}' qos ${n.qos} must be in [0,15]"
    }

    // CHK-12
    v2.nodes.foreach { n =>
      if (!routerNames.contains(n.attach))
        errs += s"C0: [CHK-12] node '${n.id}' attach '${n.attach}' is not a declared router"
    }

    // CHK-13 (ENC-01/04/05/06 golden anchor: the ChiConfig require sets)
    def widthCheck(field: String, value: Int, allowed: Seq[Int]): Unit =
      if (!allowed.contains(value))
        errs += s"C0: [CHK-13] chi.$field must be one of ${allowed.mkString(",")}, got $value"
    widthCheck("datWidth", v2.chi.datWidth, DatWidths)
    widthCheck("txnIdWidth", v2.chi.txnIdWidth, TxnIdWidths)
    widthCheck("nodeIdWidth", v2.chi.nodeIdWidth, NodeIdWidths)
    widthCheck("addrWidth", v2.chi.addrWidth, AddrWidths)

    // CHK-14
    def rangeCheck(field: String, value: Int, lo: Int, hi: Option[Int]): Unit = {
      val range = hi.fold(s"[$lo,inf)")(h => s"[$lo,$h]")
      if (value < lo || hi.exists(value > _))
        errs += s"C0: [CHK-14] chi.$field must be in $range, got $value"
    }
    rangeCheck("outstanding", v2.chi.outstanding, 1, None)
    rangeCheck("atomicEntries", v2.chi.atomicEntries, 1, None)
    rangeCheck("pcrdTypes", v2.chi.pcrdTypes, 1, Some(16))
    rangeCheck("lcreditDepth", v2.chi.lcreditDepth, 1, Some(15))

    // CHK-15
    if (!PortModes.contains(v2.chi.portMode))
      errs += s"C0: [CHK-15] chi.portMode '${v2.chi.portMode}' unknown"

    // CHK-16 (window math per AddressMap; explicit/derived mixing is already
    // impossible after GEN-03 normalization: sam is either explicit or derived).
    v2.sam.foreach { w =>
      val p = s"C0: [CHK-16] sam entry '${w.tgtNode}'"
      if (w.base < 0) errs += s"$p base ${w.base} must be >= 0"
      if (!pow2(w.size)) errs += s"$p size ${w.size} must be a power of two"
      if (w.size < AddressMap.DefaultSize)
        errs += s"$p size ${w.size} must be >= ${AddressMap.DefaultSize}"
      if (w.size > 0 && w.base >= 0 && w.base % w.size != 0)
        errs += s"$p base ${w.base} must be aligned to size ${w.size}"
      if (w.base >= 0 && w.size > 0 &&
        BigInt(w.base) + BigInt(w.size) > (BigInt(1) << addrWidth))
        errs += s"$p base ${w.base} + size ${w.size} exceeds addrWidth=$addrWidth"
    }

    // CHK-17
    v2.sam.foreach { w =>
      if (!nodeById.get(w.tgtNode).exists(n => SubordinateKinds.contains(n.kind)))
        errs += s"C0: [CHK-17] sam tgtNode '${w.tgtNode}' is not a subordinate node"
    }

    // CHK-18
    v2.links.foreach { l =>
      val p = s"C0: [CHK-18] link '${l.from}->${l.to}' channels"
      if (l.channels.isEmpty) errs += s"$p must not be empty"
      l.channels.distinct.foreach { c =>
        if (!ChannelNames.contains(c)) errs += s"$p unknown channel '$c'"
      }
      duplicates(l.channels).foreach(c => errs += s"$p duplicate channel '$c'")
      RequiredChannels.foreach { c =>
        if (!l.channels.contains(c)) errs += s"$p missing required channel '$c'"
      }
    }

    // CHK-19 (SEM-07; §6.2 rule table + AQ-01③ variant: rn-d needs exactly
    // one mn; more than one mn is never legal)
    if (!v2.chi.dvmEnable &&
      (v2.links.exists(_.channels.contains("snp")) ||
        v2.nodes.exists(n => n.kind == "rn-d" || n.kind == "mn")))
      errs += "C0: [CHK-19] snp/rn-d/mn requires chi.dvmEnable=true"
    val mnCount = v2.nodes.count(_.kind == "mn")
    if (mnCount > 1)
      errs += "C0: [CHK-19] at most one mn node allowed"
    if (v2.nodes.exists(_.kind == "rn-d") && mnCount != 1)
      errs += "C0: [CHK-19] rn-d requires exactly one mn node"

    // CHK-20 (direction retirement detected via the unknown-key paths)
    val retiredDirection = v2.unknown.collect {
      case LinkPath(i, "direction") => i.toInt
    }.toSet
    v2.links.zipWithIndex.foreach { case (l, i) =>
      val p = s"C0: [CHK-20] link '${l.from}->${l.to}'"
      if (!DatWidths.contains(l.width))
        errs += s"$p width ${l.width} must be one of ${DatWidths.mkString(",")}"
      else if (l.width != v2.chi.datWidth)
        errs += s"$p width ${l.width} must equal chi.datWidth ${v2.chi.datWidth}"
      if (l.pipe < 0 || l.pipe > 8) errs += s"$p pipe ${l.pipe} must be in [0,8]"
      if (l.cdc != "sync" && l.cdc != "async") errs += s"$p cdc '${l.cdc}' must be sync or async"
      if (retiredDirection.contains(i))
        errs += s"$p direction is retired (MIG-06); CHI links are always bidirectional"
      if (l.cdc == "async" && l.asyncFifoDepth < 2)
        errs += s"$p asyncFifoDepth ${l.asyncFifoDepth} must be >= 2 for cdc async"
      if (l.lcreditDepth < 1 || l.lcreditDepth > 15)
        errs += s"$p lcreditDepth ${l.lcreditDepth} must be in [1,15]"
    }

    // CHK-21 (switch retirement detected via the unknown-key paths)
    val retiredSwitch = v2.unknown.collect {
      case RouterPath(i, "switch") => i.toInt
    }.toSet
    v2.routers.zipWithIndex.foreach { case (r, i) =>
      val p = s"C0: [CHK-21] router '${r.name}'"
      r.in.foreach(v => if (v < 1 || v > 64) errs += s"$p in $v must be in [1,64]")
      r.out.foreach(v => if (v < 1 || v > 64) errs += s"$p out $v must be in [1,64]")
      if (!clockIds.contains(r.clock)) errs += s"$p clock '${r.clock}' is not declared"
      if (retiredSwitch.contains(i))
        errs += s"$p switch block is retired (MIG-06); CHI router is flat"
      if (r.routing != "any" && r.routing != "xy")
        errs += s"$p routing '${r.routing}' must be any or xy"
      if (r.routing == "xy" && r.pos.isEmpty) errs += s"$p xy routing requires pos {x,y}"
    }
    v2.links.foreach { l =>
      (routerByName.get(l.from), routerByName.get(l.to)) match {
        case (Some(a), Some(b)) if a.routing == "xy" || b.routing == "xy" =>
          val owner = if (a.routing == "xy") a else b
          val p = s"C0: [CHK-21] router '${owner.name}'"
          (a.pos, b.pos) match {
            case (Some((ax, ay)), Some((bx, by))) =>
              val dist = math.abs(ax - bx) + math.abs(ay - by)
              if (dist != 1)
                errs += s"$p xy mesh link '${l.from}->${l.to}' must connect Manhattan neighbors, got distance $dist"
            case _ =>
              errs += s"$p xy mesh link '${l.from}->${l.to}' requires pos on both routers"
          }
        case _ =>
      }
    }

    // CHK-22
    if (v2.timing.maxFanin < 1 || v2.timing.maxFanin > 64)
      errs += "C0: [CHK-22] fabric.timing.maxFanin must be in [1,64]"
    if (v2.timing.maxCombDepth < 1 || v2.timing.maxCombDepth > 256)
      errs += "C0: [CHK-22] fabric.timing.maxCombDepth must be in [1,256]"

    // CHK-23
    if (!v2.nodes.exists(n => RequestKinds.contains(n.kind)) ||
      !v2.nodes.exists(n => SubordinateKinds.contains(n.kind)))
      errs += "C0: [CHK-23] at least one request node and one subordinate node are required"

    // Remaining unknown keys keep the S1 generic C0 error (spec §3: unknown
    // fields must be rejected; retired direction/switch already mapped above).
    v2.unknown.foreach {
      case LinkPath(_, "direction") =>
      case RouterPath(_, "switch") =>
      case p => errs += s"C0: unknown field '$p'"
    }

    errs.toList
  }

  /** C1: fabric graph structural consistency (CHK-24–CHK-31). */
  def runC1(v2: IrV2): List[String] = {
    val errs = ListBuffer.empty[String]
    val routerNames = v2.routers.map(_.name).toSet
    val nodeById = v2.nodes.map(n => n.id -> n).toMap
    val clockOf: Map[String, String] =
      v2.routers.map(r => r.name -> r.clock).toMap ++ v2.nodes.map(n => n.id -> n.clock)

    // CHK-24
    v2.links.foreach { l =>
      val p = s"C1: [CHK-24] link '${l.from}->${l.to}'"
      Seq(l.from, l.to).foreach { e =>
        if (!routerNames.contains(e) && !nodeById.contains(e))
          errs += s"$p unknown endpoint '$e'"
      }
      nodeById.get(l.from).foreach { n =>
        if (SubordinateKinds.contains(n.kind))
          errs += s"$p subordinate node '${n.id}' must not appear as link source"
        if (n.attach != l.to)
          errs += s"$p node '${n.id}' attaches to '${n.attach}', not '${l.to}'"
      }
      nodeById.get(l.to).foreach { n =>
        if (RequestKinds.contains(n.kind))
          errs += s"$p request node '${n.id}' must not appear as link target"
        if (n.attach != l.from)
          errs += s"$p node '${n.id}' attaches to '${n.attach}', not '${l.from}'"
      }
    }

    // CHK-25
    v2.links.map(l => (l.from, l.to)).groupBy(identity)
      .collect { case (k, ls) if ls.size > 1 => k }.toSeq.sortBy(identity).foreach {
        case (f, t) => errs += s"C1: [CHK-25] duplicate link '$f' -> '$t'"
      }

    // CHK-26
    v2.nodes.foreach { n =>
      val attached =
        if (RequestKinds.contains(n.kind))
          v2.links.exists(l => l.from == n.id && l.to == n.attach)
        else if (SubordinateKinds.contains(n.kind))
          v2.links.exists(l => l.to == n.id && l.from == n.attach)
        else true // hn-i/mn attachment elaboration is pending OQ-01
      if (!attached)
        errs += s"C1: [CHK-26] node '${n.id}' has no attachment link to router '${n.attach}'"
    }

    // CHK-27
    v2.routers.foreach { r =>
      if (!v2.links.exists(l => l.from == r.name || l.to == r.name))
        errs += s"C1: [CHK-27] router '${r.name}' is not referenced by any link"
    }

    // CHK-28
    v2.links.foreach { l =>
      (clockOf.get(l.from), clockOf.get(l.to)) match {
        case (Some(a), Some(b)) if l.cdc == "sync" && a != b =>
          errs += s"C1: [CHK-28] sync link '${l.from}'($a) -> '${l.to}'($b) crosses clock domains"
        case _ =>
      }
    }

    // CHK-29 (window math per AddressMap: sorted sweep, a.base+a.size > b.base)
    val wins = v2.sam.zipWithIndex.sortBy { case (w, i) => (w.base, i) }.map(_._1)
    wins.sliding(2).foreach {
      case Seq(a, b) if a.base + a.size > b.base =>
        errs += s"C1: [CHK-29] sam windows of '${a.tgtNode}' and '${b.tgtNode}' overlap"
      case _ =>
    }

    // CHK-30 (multi-window per SN is legal, OQ-02 GEN-side assembly)
    v2.nodes.filter(n => SubordinateKinds.contains(n.kind)).foreach { n =>
      if (!v2.sam.exists(_.tgtNode == n.id))
        errs += s"C1: [CHK-30] subordinate node '${n.id}' has no SAM window"
    }

    // CHK-31 (derived ports: nLocal + router-facing link count per direction;
    // links are always bidirectional, SEM-13, one port pair per link)
    v2.routers.foreach { r =>
      val nLocal = v2.nodes.count(_.attach == r.name)
      val derivedIn = nLocal + v2.links.count(l => l.to == r.name && routerNames.contains(l.from))
      val derivedOut = nLocal + v2.links.count(l => l.from == r.name && routerNames.contains(l.to))
      r.in.foreach(v =>
        if (v != derivedIn) errs += s"C1: [CHK-31] router '${r.name}' in=$v does not match derived $derivedIn")
      r.out.foreach(v =>
        if (v != derivedOut) errs += s"C1: [CHK-31] router '${r.name}' out=$v does not match derived $derivedOut")
    }

    errs.toList
  }

  /** C2: QoS mapping completeness (CHK-32–CHK-34). */
  def runC2(v2: IrV2): List[String] = {
    val errs = ListBuffer.empty[String]
    val classNames = v2.qos.classes.map(_.name)

    // CHK-32
    val coverage = Array.fill(16)(0)
    v2.qos.mapping.foreach { m =>
      val p = s"C2: [CHK-32] qos.mapping"
      val at = s"at [${m.qosMin},${m.qosMax}]"
      if (m.qosMin < 0 || m.qosMin > 15 || m.qosMax < 0 || m.qosMax > 15)
        errs += s"$p out of range [0,15] $at"
      if (m.qosMin > m.qosMax) errs += s"$p qosMin > qosMax $at"
      if (!classNames.contains(m.className))
        errs += s"$p class '${m.className}' is not declared $at"
      if (m.qosMin <= m.qosMax && m.qosMin >= 0 && m.qosMax <= 15)
        (m.qosMin to m.qosMax).foreach(i => coverage(i) += 1)
    }
    def runs(pred: Int => Boolean): Seq[(Int, Int)] = {
      val out = ListBuffer.empty[(Int, Int)]
      var i = 0
      while (i < 16) {
        if (pred(coverage(i))) {
          val lo = i
          while (i < 16 && pred(coverage(i))) i += 1
          out += ((lo, i - 1))
        } else i += 1
      }
      out.toSeq
    }
    runs(_ == 0).foreach { case (lo, hi) => errs += s"C2: [CHK-32] qos.mapping gap at [$lo,$hi]" }
    runs(_ > 1).foreach { case (lo, hi) => errs += s"C2: [CHK-32] qos.mapping overlap at [$lo,$hi]" }

    // CHK-33
    duplicates(classNames).foreach(n => errs += s"C2: [CHK-33] qos class '$n' duplicate name")
    ClassNames.foreach { n =>
      if (!classNames.contains(n))
        errs += s"C2: [CHK-33] qos class '$n' is missing; classes must be exactly ctl,lat,bulk"
    }
    classNames.distinct.foreach { n =>
      if (!ClassNames.contains(n))
        errs += s"C2: [CHK-33] qos class '$n' is not a supported class"
    }
    v2.qos.classes.foreach { c =>
      val p = s"C2: [CHK-33] qos class '${c.name}'"
      if (c.vcs < 1 || c.vcs > 8) errs += s"$p vcs must be in [1,8]"
      if (c.depth < 1 || c.depth > 64) errs += s"$p depth must be in [1,64]"
      if (c.weight < 1 || c.weight > 64) errs += s"$p weight must be in [1,64]"
    }

    // CHK-34 (OQ-06 ruling: first version locks the implemented 8/4 thresholds)
    if (v2.qos.mapping.map(m => (m.qosMin, m.qosMax, m.className)).toSet !=
      V2Defaults.QosMapping.map(m => (m.qosMin, m.qosMax, m.className)).toSet)
      errs += "C2: [CHK-34] qos.mapping deviates from the implemented ChiQosMap thresholds (8/4); custom thresholds not yet supported"

    errs.toList
  }

  /** G4: port form vs bridge capability (CHK-40). */
  def runG4(v2: IrV2): List[String] =
    v2.nodes.toList.flatMap { n =>
      val mismatch =
        if (!Protocols.contains(n.protocol)) true // retired native/axilite/tlul
        else v2.chi.portMode != "both" && n.protocol != v2.chi.portMode
      if (mismatch)
        List(s"LM-G4: [CHK-40] node '${n.id}' protocol '${n.protocol}' incompatible with chi.portMode '${v2.chi.portMode}'")
      else Nil
    }

  /** CHK-41 consistency check against the ENC-01 protocol formula
    * `ceil(64 / (datWidth/8))` (128→4, 256→2, 512→1). Exposed so tests can
    * inject a tampered elaboration/report value (§6.8 N-G6).
    */
  def flitCountError(datWidth: Int, flitCount: Int): Option[String] = {
    if (datWidth < 8) return None // out-of-domain width is CHK-13's job
    val perFlit = datWidth / 8
    val expected = (64 + perFlit - 1) / perFlit
    if (flitCount != expected)
      Some(s"LM-G6: [CHK-41] DAT flit count $flitCount inconsistent with datWidth=$datWidth (expected $expected)")
    else None
  }

  /** G6: CHI capacity consistency (CHK-41/CHK-42). */
  def runG6(v2: IrV2): List[String] = {
    val errs = ListBuffer.empty[String]
    // Elaborated 64B DAT flit count via the GEN assembly path; ChiWidths is
    // the same anchor the CHI engines use (ChiAxiBridge/ChiTransaction).
    val elaboratedWidth =
      try ChiAssemble.config(v2).datWidth
      catch { case _: Ir.IrException => v2.chi.datWidth }
    flitCountError(elaboratedWidth, ChiWidths.flitCount(64, elaboratedWidth)).foreach(errs += _)

    val cap = txnIdSpace(v2.chi.txnIdWidth)
    if (v2.chi.outstanding > cap)
      errs += s"LM-G6: [CHK-42] chi.outstanding ${v2.chi.outstanding} exceeds TxnID space 2^${v2.chi.txnIdWidth}=$cap"
    errs.toList
  }

  /** G8: arbitration policy (CHK-43). */
  def runG8(v2: IrV2): List[String] =
    v2.qos.classes.toList.flatMap { c =>
      if (c.arbitration != "wrr" && c.arbitration != "rr")
        List(s"LM-G8: [CHK-43] qos class '${c.name}' arbitration must be wrr or rr")
      else Nil
    }

  /** S1: partition membership, clocks and async edges (CHK-44–CHK-46).
    * Trivially green when no partitions are declared.
    */
  def runS1(v2: IrV2): List[String] = {
    if (v2.partitions.isEmpty) return Nil
    val errs = ListBuffer.empty[String]
    val clockIds = v2.clockDomains.map(_.id)
    val routerNames = v2.routers.map(_.name).toSet
    val nodeById = v2.nodes.map(n => n.id -> n).toMap
    val fabricObjects = (v2.nodes.map(_.id) ++ v2.routers.map(_.name)).toSet
    val clockOf: Map[String, String] =
      v2.routers.map(r => r.name -> r.clock).toMap ++ v2.nodes.map(n => n.id -> n.clock)

    // CHK-44: names, members, clocks, references, coverage.
    duplicates(v2.partitions.map(_.name)).foreach(p =>
      errs += s"LM-S1: [CHK-44] partition '$p' duplicate name")
    val partOf = scala.collection.mutable.Map.empty[String, String]
    v2.partitions.foreach { p =>
      if (p.members.isEmpty)
        errs += s"LM-S1: [CHK-44] partition '${p.name}' members must not be empty"
      if (!clockIds.contains(p.clock))
        errs += s"LM-S1: [CHK-44] partition '${p.name}' clock '${p.clock}' is not declared"
      duplicates(p.members).foreach(m =>
        errs += s"LM-S1: [CHK-44] partition '${p.name}' member '$m' is listed twice")
      p.members.distinct.foreach { m =>
        if (!fabricObjects.contains(m))
          errs += s"LM-S1: [CHK-44] partition '${p.name}' member '$m' is not a fabric object"
        else if (partOf.contains(m))
          errs += s"LM-S1: [CHK-44] partition '${p.name}' member '$m' is already assigned to partition '${partOf(m)}'"
        else partOf(m) = p.name
      }
    }
    fabricObjects.toSeq.sorted.filterNot(partOf.contains).foreach { o =>
      errs += s"LM-S1: [CHK-44] partition '${v2.partitions.head.name}' fabric object '$o' is not assigned to any partition"
    }

    // CHK-45: member clock equals partition clock; node stays with its attach
    // router (replaces the v1 attachment-link rule).
    v2.partitions.foreach { p =>
      p.members.distinct.foreach { m =>
        clockOf.get(m).foreach { c =>
          if (c != p.clock) {
            val what = if (routerNames.contains(m)) "router" else "node"
            errs += s"LM-S1: [CHK-45] $what '$m' runs on clock '$c', expected '${p.clock}'"
          }
        }
      }
    }
    v2.nodes.foreach { n =>
      for (np <- partOf.get(n.id); rp <- partOf.get(n.attach) if np != rp)
        errs += s"LM-S1: [CHK-45] node '${n.id}' crosses partition boundary"
    }

    // CHK-46: async edges must join different clock domains (SEM-14).
    v2.links.foreach { l =>
      (clockOf.get(l.from), clockOf.get(l.to)) match {
        case (Some(a), Some(b)) if l.cdc == "async" && a == b =>
          errs += s"""LM-S1: [CHK-46] async link '${l.from}' -> '${l.to}' joins clock domain '$a'; use cdc "sync""""
        case _ =>
      }
    }
    errs.toList
  }

  /** S2: partition boundary shape (CHK-47). */
  def runS2(v2: IrV2): List[String] = {
    if (v2.partitions.isEmpty) return Nil
    val errs = ListBuffer.empty[String]
    val routerNames = v2.routers.map(_.name).toSet
    v2.partitions.foreach { p =>
      if (p.boundaryPipe < 1 || p.boundaryPipe > 8)
        errs += s"LM-S2: [CHK-47] partition '${p.name}' boundaryPipe must be in [1,8]"
      if (!p.members.exists(routerNames.contains))
        errs += s"LM-S2: [CHK-47] partition '${p.name}' has no router"
    }
    errs.toList
  }

  /** S3: cut edges must be router-to-router (CHK-48). */
  def runS3(v2: IrV2): List[String] = {
    if (v2.partitions.isEmpty) return Nil
    val errs = ListBuffer.empty[String]
    val routerNames = v2.routers.map(_.name).toSet
    val partOf: Map[String, String] =
      v2.partitions.flatMap(p => p.members.map(_ -> p.name)).toMap
    v2.links.foreach { l =>
      (partOf.get(l.from), partOf.get(l.to)) match {
        case (Some(a), Some(b)) if a != b &&
          !(routerNames.contains(l.from) && routerNames.contains(l.to)) =>
          errs += s"LM-S3: [CHK-48] cut link '${l.from}' -> '${l.to}' is not router-to-router"
        case _ =>
      }
    }
    errs.toList
  }

  /** Aggregate per §6.1: C0 → C1 → G1 → C2 → G4 → G6 → G8 → S1 → S2 → S3.
    * C0 failure terminates immediately; the other gates collect all errors.
    * G1 (CHK-35–39) goes through ChiGates.runV2, the sole v2 entry (GEN-11).
    * G7 is retired but its key stays constant true for one version (GEN-17).
    */
  def run(v2: IrV2): Result = {
    val c0 = runC0(v2)
    if (c0.nonEmpty) return Result(Vector("C0" -> false), c0)
    val c1 = runC1(v2)
    val g1 = ChiGates.runV2(v2)
    val c2 = runC2(v2)
    val g4 = runG4(v2)
    val g6 = runG6(v2)
    val g8 = runG8(v2)
    val s1 = runS1(v2)
    val s2 = runS2(v2)
    val s3 = runS3(v2)
    Result(
      gates = Vector(
        "C0" -> true,
        "C1" -> c1.isEmpty
      ) ++ g1.gates ++ Vector(
        "C2" -> c2.isEmpty,
        "G4" -> g4.isEmpty,
        "G6" -> g6.isEmpty,
        "G7" -> true,
        "G8" -> g8.isEmpty,
        "S1" -> s1.isEmpty,
        "S2" -> s2.isEmpty,
        "S3" -> s3.isEmpty
      ),
      errors = c1 ++ g1.errors ++ c2 ++ g4 ++ g6 ++ g8 ++ s1 ++ s2 ++ s3
    )
  }
}