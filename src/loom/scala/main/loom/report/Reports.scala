package loom.report

import loom.check.ChecksV2
import loom.chi.{ChiBridgeLink, ChiNetNode, ChiNetworkParams, ChiNodeKind, ChiPartitionParams, ChiWidths}
import loom.gen.ChiAssemble
import loom.ir._

/** Deterministic report documents written next to emitted RTL.
  * No timestamps and no absolute paths: the same IR must yield the same bytes.
  */
object Reports {
  def timingWarnings(ir: Ir): List[String] =
    ir.routers.toList.flatMap { r =>
      if (r.in > ir.timing.maxFanin || r.out > ir.timing.maxFanin) {
        val need = math.max(r.in, r.out)
        val groups = math.max(1, (need + ir.timing.maxFanin - 1) / ir.timing.maxFanin)
        val suggested = math.max(2, nextPow2(groups))
        List(
          s"LM-W-TIMING router ${r.name}: in=${r.in}, out=${r.out}, maxFanin=${ir.timing.maxFanin}; " +
            s"suggested banks=$suggested (config unchanged)"
        )
      } else Nil
    }

  /** Effective slave windows: explicit base/size when declared, otherwise
    * one 4 KB window per slave in endpoint order.
    */
  def addressMapJson(ir: Ir): JValue = {
    val slaves = ir.endpoints.filter(_.role == "slave")
    val explicit = AddressMap.declared(slaves)
    val wins = AddressMap.windows(slaves)
    val items = wins.map { w =>
      JObj(Vector(
        "id" -> JStr(w.id),
        "index" -> JNum(BigDecimal(w.index)),
        "base" -> JStr(f"0x${w.base}%x"),
        "size" -> JNum(BigDecimal(w.size)),
        "end" -> JStr(f"0x${w.end}%x")
      ))
    }
    JObj(Vector(
      "explicit" -> JBool(explicit),
      "routeShift" -> JNum(BigDecimal(LoomDefaults.RouteShift)),
      "stride" -> JNum(BigDecimal(AddressMap.DefaultSize)),
      "slaves" -> JArr(items.toVector)
    ))
  }

  def checksJson(
      ir: Ir,
      configHash: String,
      errors: List[String],
      warnings: List[String],
      gates: Vector[(String, Boolean)]
  ): JValue = {
    val gateFields: Vector[(String, JValue)] = gates.map { case (k, v) => (k, JBool(v): JValue) }
    JObj(
      Vector(
        "schema" -> JStr("loom/checks/v1"),
        "name" -> JStr(ir.meta.name),
        "configHash" -> JStr(configHash),
        "ok" -> JBool(errors.isEmpty),
        "errors" -> JArr(errors.map(JStr(_)).toVector),
        "warnings" -> JArr(warnings.map(JStr(_)).toVector),
        "gates" -> JObj(gateFields)
      )
    )
  }

  def estimateJson(ir: Ir, configHash: String): JValue = {
    val clockOf: Map[String, String] =
      (ir.mnets ++ ir.snets).map(n => n.name -> n.clock).toMap ++
        ir.routers.map(r => r.name -> r.clock).toMap
    val freqOf: Map[String, Double] = ir.clockDomains.map(c => c.id -> c.freqMHz).toMap
    def capacityGBps(width: Int, node: String): Double = {
      val freq = clockOf.get(node).flatMap(freqOf.get).getOrElse(0.0)
      width / 8.0 * freq / 1000.0
    }

    val linkObjs = ir.links.map { l =>
      JObj(
        Vector(
          "from" -> JStr(l.from),
          "to" -> JStr(l.to),
          "width" -> JNum(BigDecimal(l.width)),
          "cdc" -> JStr(l.cdc),
          "pipe" -> JNum(BigDecimal(l.pipe)),
          "direction" -> JStr(l.direction),
          "dateline" -> JBool(l.dateline),
          "capacityGBps" -> rounded(capacityGBps(l.width, l.from))
        )
      )
    }

    val routerObjs = ir.routers.map { r =>
      JObj(
        Vector(
          "name" -> JStr(r.name),
          "in" -> JNum(BigDecimal(r.in)),
          "out" -> JNum(BigDecimal(r.out)),
          "switch" -> JStr(r.switchKind),
          "banks" -> JNum(BigDecimal(r.banks)),
          "crosspoints" -> JNum(BigDecimal(r.in * r.out))
        )
      )
    }

    val crosspoints = ir.routers.map(r => r.in * r.out).sum
    val totalPipe = ir.links.map(_.pipe).sum
    val maxCapacity =
      if (ir.links.isEmpty) 0.0 else ir.links.map(l => capacityGBps(l.width, l.from)).max
    val bankStages = ir.routers.count(_.switchKind == "banked")
    val latencyProxy = 2 * math.max(1, ir.routers.length) + totalPipe + bankStages

    // Memory that is large enough to become a macro is priced by the bit and
    // kept out of the logic proxy; smaller queues stay register-based.
    val sramShapes = sramNeedsOf(ir).filter { case (d, w) => d * w >= Sram.ThresholdBits }
    val sramBits = sramShapes.map { case (d, w) => d * w }.sum
    val sramArea = sramBits * Sram.AreaPerBitUm2

    // nangate45 fit from 2x2 / 4x4 flat and 4x4 banked emissions (S18).
    val areaFixed = 16694.5
    val areaPerCrosspoint = 3264.5
    val areaPerBankedRouter = 4290.0
    val areaProxy = areaFixed + areaPerCrosspoint * crosspoints + areaPerBankedRouter * bankStages
    val areaTerms = Seq(
      "crosspoints" -> areaPerCrosspoint * crosspoints,
      "fixed" -> areaFixed,
      "banked" -> areaPerBankedRouter * bankStages
    ).sortBy(-_._2)

    JObj(
      Vector(
        "schema" -> JStr("loom/estimate/v1"),
        "name" -> JStr(ir.meta.name),
        "configHash" -> JStr(configHash),
        "links" -> JArr(linkObjs.toVector),
        "routers" -> JArr(routerObjs.toVector),
        "totals" -> JObj(
          Vector(
            "routers" -> JNum(BigDecimal(ir.routers.length)),
            "links" -> JNum(BigDecimal(ir.links.length)),
            "crosspoints" -> JNum(BigDecimal(crosspoints)),
            "maxLinkCapacityGBps" -> rounded(maxCapacity),
            "latencyProxyCycles" -> JNum(BigDecimal(latencyProxy)),
            "areaProxyUm2" -> rounded(areaProxy),
            "sramBits" -> JNum(BigDecimal(sramBits)),
            "sramBlocks" -> JNum(BigDecimal(sramShapes.distinct.size)),
            "sramAreaProxyUm2" -> rounded(sramArea),
            "totalAreaProxyUm2" -> rounded(areaProxy + sramArea)
          )
        ),
        "assumptions" -> JArr(
          Vector(
            "capacity = width/8 * clockMHz / 1000 GB/s per direction",
            "latency proxy = 2 cycles per router + 1 cycle per pipe stage + 1 cycle per banked router; excludes arbitration and queueing",
            "crosspoints = in * out; banked keeps crosspoints and adds one register stage per output",
            "area proxy = 16694.5 + 3264.5*crosspoints + 4290*bankedRouters um^2, nangate45 yosys fit (no SRAM/clock tree/wiring)",
            s"SRAM proxy = ${Sram.AreaPerBitUm2} um^2/bit placeholder over memories with depth*width >= ${Sram.ThresholdBits}; not calibrated",
            "estimate only; not signoff"
          ).map(JStr(_))
        ),
        "areaSensitivity" -> JArr(areaTerms.map { case (k, v) =>
          JObj(Vector("term" -> JStr(k), "um2" -> rounded(v)))
        }.toVector),
        "warnings" -> JArr(timingWarnings(ir).map(JStr(_)).toVector)
      )
    )
  }

  private def rounded(x: Double): JNum =
    JNum(BigDecimal(x).setScale(3, BigDecimal.RoundingMode.HALF_UP))

  // MIG-11 (S6): the v1 partition portsJson moved to
  // legacy/main/loom/report/ReportsV1.scala with the four-channel fabric;
  // the CHI caliber replacement is portsJsonV2 below.

  /** Artifact manifest: relative path plus SHA-256 for every emitted file. */
  def manifestJson(
      name: String,
      configHash: String,
      version: String,
      files: Seq[(String, String)]
  ): JValue =
    JObj(Vector(
      "schema" -> JStr("loom/manifest/v1"),
      "name" -> JStr(name),
      "version" -> JStr(version),
      "configHash" -> JStr(configHash),
      "files" -> JArr(files.sortBy(_._1).map { case (p, h) =>
        JObj(Vector("path" -> JStr(p), "sha256" -> JStr(h)))
      }.toVector)
    ))

  def formalJson(
      name: String,
      configHash: String,
      checks: Seq[(String, String, Boolean, String)]
  ): JValue = {
    // (model, property, passed, log)
    JObj(Vector(
      "schema" -> JStr("loom/formal/v1"),
      "name" -> JStr(name),
      "configHash" -> JStr(configHash),
      "engine" -> JStr("yosys-sat"),
      "ok" -> JBool(checks.forall(_._3)),
      "checks" -> JArr(checks.map { case (model, prop, ok, log) =>
        JObj(Vector(
          "model" -> JStr(model),
          "property" -> JStr(prop),
          "result" -> JStr(if (ok) "pass" else "fail"),
          "log" -> JStr(log)
        ))
      }.toVector),
      "limitations" -> JArr(Vector(
        "bounded model checking over the control-token model, not the full RTL",
        "proves safety and bounded progress, not liveness under arbitrary traffic",
        "P5 uses a dedicated 8-way round-robin model; P6/P8 a response-slot model; P7 a CDC credit model",
        "G9 uses a monotone 0-2 stage pointer-synchronizer model; physical CDC FIFOs are covered by the LmCdcSpec simulation"
      ).map(JStr(_)))
    ))
  }

  def signoffMarkdown(
      ir: Ir,
      configHash: String,
      gates: Vector[(String, Boolean)],
      warnings: List[String],
      artifactCounts: (Int, Int, Int),
      formal: Option[JValue]
  ): String = {
    val (rtlCount, assertCount, tbCount) = artifactCounts
    val gateRows = gates.map { case (g, ok) =>
      s"| $g | ${if (ok) "PASS" else "FAIL"} |"
    }
    val warnBlock =
      if (warnings.isEmpty) "- none"
      else warnings.map(w => s"- $w").mkString("\n")
    val formalBlock = formal match {
      case Some(JObj(fields)) =>
        val checks = fields.toMap.get("checks").collect { case JArr(xs) => xs }.getOrElse(Vector.empty)
        val rows = checks.map {
          case JObj(f) =>
            val m = f.toMap
            def s(k: String) = m.get(k).collect { case JStr(x) => x }.getOrElse("?")
            s"| ${s("model")} | ${s("property")} | ${s("result")} |"
          case _ => "| ? | ? | ? |"
        }
        ("| model | property | result |\n|---|---|---|\n" + rows.mkString("\n")) +
          "\n\nSee formal/formal.json and formal/*.log."
      case _ =>
        "Not run. Execute `loom formal --config <cfg> --out <dir>` to add bounded proofs."
    }

    val lines = Seq(
      s"# Loom Signoff - ${ir.meta.name}",
      "",
      s"Config hash: `$configHash`",
      "Schema: `loom/v1`",
      "",
      "## Static gates",
      "",
      "| gate | result |",
      "|---|---|"
    ) ++ gateRows ++ Seq(
      "",
      "## Warnings",
      "",
      warnBlock,
      "",
      "## Artifacts",
      "",
      s"- RTL files: $rtlCount",
      s"- Assertion files: $assertCount",
      s"- Testbench skeletons: $tbCount",
      "",
      "## Formal (bounded)",
      "",
      formalBlock,
      "",
      "## Signoff checklist",
      "",
      "- [x] Static gates evaluated (table above)",
      "- [x] Artifacts hashed in manifest.json",
      "- [x] TB skeleton and assertion list emitted",
      "- [ ] Bounded formal proofs attached (run `loom formal`)",
      "- [ ] Area proxy reviewed against the target budget",
      "- [ ] Integration-level simulation signed by the owner",
      "",
      "## Limitations",
      "",
      "- The estimate is a planning proxy, not a timing or area signoff.",
      "- Formal results cover the abstract token models; simulation covers the RTL.",
      "- Any IR change invalidates this report: re-run `loom emit`."
    )
    lines.mkString("\n") + "\n"
  }

  private def nextPow2(n: Int): Int = {
    var p = 1
    while (p < n) p <<= 1
    p
  }

  private def log2Ceil(n: Int): Int =
    if (n <= 1) 0 else 32 - Integer.numberOfLeadingZeros(n - 1)

  /** Memory shapes the fabric instantiates: one VC queue per (router input,
    * class) and one FIFO per async-link direction. Widths mirror the RTL
    * bundles, so the bit count is the storage the emitter must find a macro
    * for.
    */
  def sramNeedsOf(ir: Ir): Vector[(Int, Int)] = {
    val masters = ir.endpoints.filter(_.role == "master")
    val idW = math.max(1, log2Ceil(masters.length))
    val tagW = ir.axiIdWidth
    val dataW = masters.headOption.map(_.dataWidth).getOrElse(32)
    val addrW = masters.headOption.map(_.addrWidth).getOrElse(32)
    val classes = if (ir.qosClasses.isEmpty) LoomDefaults.DefaultQos else ir.qosClasses
    val qosW = math.max(1, log2Ceil(classes.length))
    val reqW = addrW + 1 + 8 + idW + tagW + dataW + qosW
    val rspW = idW + tagW + dataW + 1

    val vc = ir.routers.flatMap(r =>
      (0 until r.in).flatMap(_ => classes.map(c => (c.depth, reqW))))
    val cdc = ir.links.filter(_.cdc == "async").flatMap { l =>
      Seq((l.asyncFifoDepth, reqW), (l.asyncFifoDepth, rspW))
    }
    (vc ++ cdc).toVector
  }

  // ==========================================================================
  // loom/v2 report caliber (S4/S5, GEN-17). Additive functions only: the v1
  // functions above stay for legacy consumers until the next major version
  // (MIG-10).
  // ==========================================================================

  /** Derived router port counts (IR-48/49): nLocal + router-facing links per
    * direction; links are always bidirectional (SEM-13).
    */
  def derivedPortsV2(v2: IrV2, router: String): (Int, Int) = {
    val routerNames = v2.routers.map(_.name).toSet
    val nLocal = v2.nodes.count(_.attach == router)
    val in = nLocal + v2.links.count(l => l.to == router && routerNames.contains(l.from))
    val out = nLocal + v2.links.count(l => l.from == router && routerNames.contains(l.to))
    (in, out)
  }

  /** LM-W-TIMING wording preserved (GEN-18) over derived v2 port counts. */
  def timingWarningsV2(v2: IrV2): List[String] =
    v2.routers.toList.flatMap { r =>
      val (in, out) = derivedPortsV2(v2, r.name)
      if (in > v2.timing.maxFanin || out > v2.timing.maxFanin) {
        val need = math.max(in, out)
        val groups = math.max(1, (need + v2.timing.maxFanin - 1) / v2.timing.maxFanin)
        val suggested = math.max(2, nextPow2(groups))
        List(
          s"LM-W-TIMING router ${r.name}: in=$in, out=$out, maxFanin=${v2.timing.maxFanin}; " +
            s"suggested banks=$suggested (config unchanged)"
        )
      } else Nil
    }

  /** Generated channel classes (SEM-07/D11): snp only when DVM is generated. */
  def channelsV2(v2: IrV2): Seq[String] =
    Seq("req", "rsp", "dat") ++ (if (v2.chi.dvmEnable) Seq("snp") else Nil)

  /** chi configuration snapshot plus the ENC-01/05 derived quantities. */
  def chiSnapshotJson(v2: IrV2): JValue = {
    val c = v2.chi
    JObj(Vector(
      "datWidth" -> JNum(BigDecimal(c.datWidth)),
      "txnIdWidth" -> JNum(BigDecimal(c.txnIdWidth)),
      "nodeIdWidth" -> JNum(BigDecimal(c.nodeIdWidth)),
      "addrWidth" -> JNum(BigDecimal(c.addrWidth)),
      "outstanding" -> JNum(BigDecimal(c.outstanding)),
      "atomicEnable" -> JBool(c.atomicEnable),
      "atomicEntries" -> JNum(BigDecimal(c.atomicEntries)),
      "dvmEnable" -> JBool(c.dvmEnable),
      "portMode" -> JStr(c.portMode),
      "pcrdTypes" -> JNum(BigDecimal(c.pcrdTypes)),
      "lcreditDepth" -> JNum(BigDecimal(c.lcreditDepth)),
      "beWidth" -> JNum(BigDecimal(ChiWidths.beWidth(c.datWidth))),
      "flitCount64B" -> JNum(BigDecimal(ChiWidths.flitCount(64, c.datWidth))),
      "maxNodes" -> JNum(BigDecimal(1 << c.nodeIdWidth))
    ))
  }

  private def kindName(k: ChiNodeKind.Value): String = k match {
    case ChiNodeKind.RnI => "rn-i"
    case ChiNodeKind.RnD => "rn-d"
    case ChiNodeKind.SnF => "sn-f"
    case ChiNodeKind.SnI => "sn-i"
    case ChiNodeKind.HnI => "hn-i"
    case ChiNodeKind.Mn  => "mn"
    case other           => other.toString
  }

  /** CHI flit widths derived from the IR tiers; mirrors chi/ChiFlit.scala. */
  private def flitWidthsV2(v2: IrV2): Seq[(String, Int)] = {
    val c = v2.chi
    val nw = c.nodeIdWidth; val tw = c.txnIdWidth; val aw = c.addrWidth
    val base = Seq(
      "req" -> (27 + 3 * nw + 2 * tw + aw),
      "rsp" -> (18 + 2 * nw + 2 * tw),
      "dat" -> (15 + 3 * nw + tw + ChiWidths.beWidth(c.datWidth) + c.datWidth))
    if (c.dvmEnable) base :+ ("snp" -> (9 + 3 * nw + 2 * tw + aw)) else base
  }

  /** v2 SRAM needs: one queue per (router input port, class, VC half) at the
    * configured L-Credit depth, plus per-direction async FIFOs. Depths are
    * rounded up to powers of two for macro planning (Sram.singlePort).
    */
  def sramNeedsOfV2(v2: IrV2): Vector[(Int, Int)] = {
    val vcWidth = log2Ceil(3 * 2) // nClasses * nHalf (SEM-08, ring dateline)
    val widths = flitWidthsV2(v2)
    val depth = nextPow2(math.max(2, v2.chi.lcreditDepth))
    val vc = v2.routers.flatMap { r =>
      val (in, _) = derivedPortsV2(v2, r.name)
      (0 until in).flatMap(_ => widths.flatMap { case (_, w) =>
        Seq.fill(2)((depth, w + vcWidth))
      })
    }
    val cdc = v2.links.filter(_.cdc == "async").flatMap { l =>
      val d = nextPow2(math.max(2, l.asyncFifoDepth))
      widths.flatMap { case (_, w) => Seq((d, w + vcWidth), (d, w + vcWidth)) }
    }
    (vc ++ cdc).toVector
  }

  /** Shared node/SAM tables (GEN-05 ordering: nodes by NodeID, SAM by base).
    * Used by estimateJsonV2 and the IR-only chi_nodes rendering.
    */
  private def nodeSamTablesV2(v2: IrV2, ids: Seq[Int]): (JValue, JValue) = {
    val idOf = v2.nodes.map(_.id).zip(ids).toMap
    def nodeObj(n: NodeV2): JValue =
      JObj(Vector(
        "id" -> JStr(n.id), "kind" -> JStr(n.kind),
        "nodeId" -> JNum(BigDecimal(idOf(n.id))),
        "clock" -> JStr(n.clock), "attach" -> JStr(n.attach)))
    val nodesBySeg = v2.nodes.zip(ids).sortBy(_._2).map(_._1)
    val nodeTable = JObj(Vector(
      "rn" -> JArr(nodesBySeg.filter(n => n.kind == "rn-i" || n.kind == "rn-d").map(nodeObj).toVector),
      "sn" -> JArr(nodesBySeg.filter(n => n.kind == "sn-f" || n.kind == "sn-i").map(nodeObj).toVector),
      "hn" -> JArr(nodesBySeg.filter(_.kind == "hn-i").map(nodeObj).toVector),
      "mn" -> JArr(nodesBySeg.filter(_.kind == "mn").map(nodeObj).toVector)))
    val samObjs = v2.sam.sortBy(_.base).map { w =>
      JObj(Vector(
        "base" -> JNum(BigDecimal(w.base)), "size" -> JNum(BigDecimal(w.size)),
        "tgtNode" -> JStr(w.tgtNode), "tgtId" -> JNum(BigDecimal(idOf.getOrElse(w.tgtNode, 0)))))
    }
    (nodeTable, JArr(samObjs.toVector))
  }

  /** GEN-23 (UP-02): additive estimate/API qosGen rows — the raw declaration
    * caliber (bandwidth MB/s, saturation bytes). Empty when no node declares
    * qosGen. Consumed by HW-10 (SEM-18): ChiQosRegulator token buckets at RN
    * injection; rows keep the declaration caliber for the report.
    */
  def qosGenJsonV2(v2: IrV2): JValue = {
    val ids = ChiAssemble.nodeIds(v2)
    JArr(v2.nodes.zip(ids).sortBy(_._2).flatMap { case (n, _) =>
      n.qosGen.map { q =>
        JObj(Vector(
          "node" -> JStr(n.id),
          "kind" -> JStr(q.kind),
          "bandwidth" -> JNum(BigDecimal(q.bandwidth)),
          "saturation" -> JNum(BigDecimal(q.saturation))))
      }
    }.toVector)
  }

  /** GEN-24 (UP-08): additive estimate/API adapt rows from the assembled
    * AdaptParams (mismatch list + explicit depth freezes, SEM-19). Empty
    * when nothing is declared/mismatched. Width mismatches at chi-node DAT
    * tiers (128/256/512) are consumed by HW-12 via the GEN wrapper
    * ChiAdaptedNetworkTop (ChiNiAdapt gearboxes); frequency-mismatch
    * node-boundary CDC is pending.
    */
  def adaptJsonV2(v2: IrV2): JValue = {
    val nameOfId = v2.nodes.map(_.id).zip(ChiAssemble.nodeIds(v2)).map { case (n, i) => i -> n }.toMap
    JArr(ChiAssemble.adaptParams(v2).map { p =>
      JObj(Vector(
        "node" -> JStr(nameOfId(p.nodeId)),
        "dataWidth" -> JNum(BigDecimal(p.dataWidth)),
        "datWidth" -> JNum(BigDecimal(p.datWidth)),
        "clock" -> JStr(p.clock),
        "routerClock" -> JStr(p.routerClock),
        "fifoDepth" -> JNum(BigDecimal(p.fifoDepth)),
        "mismatch" -> JBool(p.mismatch)))
    }.toVector)
  }

  /** GEN-25 (UP-07): additive subsystem summary rows — per-subsystem node/
    * router/SAM counts plus the chi snapshot of the independent projection
    * (datWidth tiers may differ; the HW-11 bridge adapts).
    */
  def subsystemsJsonV2(v2: IrV2): JValue =
    JArr(v2.subsystems.map { s =>
      JObj(Vector(
        "name" -> JStr(s.name),
        "nodes" -> JNum(BigDecimal(s.nodes.size)),
        "routers" -> JNum(BigDecimal(s.routers.size)),
        "sam" -> JNum(BigDecimal(s.sam.size)),
        "chi" -> chiSnapshotJson(IrV2.projectSubsystem(v2, s))))
    }.toVector)

  /** GEN-25 (UP-07): additive gatesBySubsystem key — subsystem name → the
    * full per-subsystem gate vector (ChecksV2.run on the projection). The
    * top-level G1 keys are the AND over these results (SEM-20).
    */
  def gatesBySubsystemJsonV2(v2: IrV2): JValue =
    JObj(ChecksV2.gatesBySubsystem(v2).map { case (name, gates) =>
      name -> (JObj(gates.map { case (k, ok) => (k, JBool(ok): JValue) }): JValue)
    }.toVector)

  /** GEN-17 estimate row: CHI caliber. Capacity is the DAT flit line rate
    * (`datWidth/8 * MHz/1000 GB/s`); node/SAM tables, flit widths, three-level
    * flow-control parameters and the generated channel classes are reported.
    * The area fit stays the v1 four-channel calibration until CHI PPA (D10).
    */
  def estimateJsonV2(v2: IrV2, configHash: String): JValue = {
    val clockOf: Map[String, String] =
      v2.routers.map(r => r.name -> r.clock).toMap ++ v2.nodes.map(n => n.id -> n.clock)
    val freqOf: Map[String, Double] = v2.clockDomains.map(c => c.id -> c.freqMHz).toMap
    val datWidth = v2.chi.datWidth
    def capacityGBps(node: String): Double = {
      val freq = clockOf.get(node).flatMap(freqOf.get).getOrElse(0.0)
      datWidth / 8.0 * freq / 1000.0
    }

    val linkObjs = v2.links.map { l =>
      JObj(Vector(
        "from" -> JStr(l.from),
        "to" -> JStr(l.to),
        "width" -> JNum(BigDecimal(l.width)),
        "cdc" -> JStr(l.cdc),
        "pipe" -> JNum(BigDecimal(l.pipe)),
        "direction" -> JStr("bidirectional"),
        "dateline" -> JBool(l.dateline),
        "channels" -> JArr(l.channels.map(JStr(_)).toVector),
        "lcreditDepth" -> JNum(BigDecimal(l.lcreditDepth)),
        "capacityGBps" -> rounded(capacityGBps(l.from))
      ))
    }

    val ports = v2.routers.map(r => r.name -> derivedPortsV2(v2, r.name)).toMap
    val routerObjs = v2.routers.map { r =>
      val (in, out) = ports(r.name)
      JObj(Vector(
        "name" -> JStr(r.name),
        "in" -> JNum(BigDecimal(in)),
        "out" -> JNum(BigDecimal(out)),
        "switch" -> JStr("flat"),
        "banks" -> JNum(BigDecimal(1)),
        "crosspoints" -> JNum(BigDecimal(in * out))
      ))
    }

    val (nodeTable, samJ) = nodeSamTablesV2(v2, ChiAssemble.nodeIds(v2))
    // GEN-22 (HW-09/A3): additive bridge proxy counts — one AXI read/write
    // bridge pair per axi4 RN, one CHI->AXI reverse bridge per axi4 SN.
    // Counts only, no PPA claim (caliber recorded in assumptions).
    val rnAxiCount = v2.nodes.count(n =>
      (n.kind == "rn-i" || n.kind == "rn-d") && n.protocol == "axi4")
    val snAxiCount = v2.nodes.count(n =>
      (n.kind == "sn-f" || n.kind == "sn-i") && n.protocol == "axi4")
    val flowControl = JObj(Vector(
      "pcrdTypes" -> JNum(BigDecimal(v2.chi.pcrdTypes)),
      "outstanding" -> JNum(BigDecimal(v2.chi.outstanding)),
      "txnIdWidth" -> JNum(BigDecimal(v2.chi.txnIdWidth)),
      "txnIdSpace" -> JNum(BigDecimal(1 << v2.chi.txnIdWidth)),
      "dbidWidth" -> JNum(BigDecimal(v2.chi.txnIdWidth)),
      "lcreditDepth" -> JNum(BigDecimal(v2.chi.lcreditDepth))))

    val crosspoints = ports.values.map { case (in, out) => in * out }.sum
    val totalPipe = v2.links.map(_.pipe).sum
    val maxCapacity = if (v2.links.isEmpty) 0.0 else v2.links.map(l => capacityGBps(l.from)).max
    val latencyProxy = 2 * math.max(1, v2.routers.length) + totalPipe

    val sramShapes = sramNeedsOfV2(v2).filter { case (d, w) => d * w >= Sram.ThresholdBits }
    val sramBits = sramShapes.map { case (d, w) => d * w }.sum
    val sramArea = sramBits * Sram.AreaPerBitUm2

    // Same nangate45 fit as v1; banked routers are retired (CHI router flat).
    val areaFixed = 16694.5
    val areaPerCrosspoint = 3264.5
    val areaProxy = areaFixed + areaPerCrosspoint * crosspoints
    val areaTerms = Seq(
      "crosspoints" -> areaPerCrosspoint * crosspoints,
      "fixed" -> areaFixed
    ).sortBy(-_._2)

    // SEM-14 (HW-14): domain index = clockDomains declaration order; domain 0
    // is the top module clock. Multi-domain flat tops expose domClk/domReset
    // as inputs — the integrator supplies them, GEN emits no clock wrapper.
    val domainOfClock = v2.clockDomains.map(_.id).zipWithIndex.toMap
    val usedDomains = v2.routers.flatMap(r => domainOfClock.get(r.clock)).distinct
    val clockingNotes =
      if (usedDomains.size > 1) Vector(
        s"clocking = ${usedDomains.size} router clock domains (index = clockDomains declaration order; domain 0 '${v2.clockDomains.headOption.map(_.id).getOrElse("")}' is the top module clock); domClk/domReset are exposed top-level inputs supplied by the integrator (no GEN clock wrapper); cdc=async links insert ChiAsyncLink (SEM-14/HW-14); local nodes stay on domain-0 routers (CHK-54)")
      else Vector.empty

    // GEN-25 (UP-07): additive hierarchical keys, present only when
    // subsystems are declared so flat estimate baselines stay byte-identical.
    val hierFields =
      if (v2.subsystems.isEmpty) Vector.empty[(String, JValue)]
      else Vector(
        "subsystems" -> subsystemsJsonV2(v2),
        "nocLinks" -> JArr(v2.nocLinks.map(IrV2.nocLinkJson).toVector),
        "gatesBySubsystem" -> gatesBySubsystemJsonV2(v2))
    val hierNotes =
      if (v2.subsystems.isEmpty) Vector.empty[String]
      else Vector(
        "subsystems = independent NoCs (SEM-20: local NodeID/SAM/chi/clock domains); nocLinks = " +
          "dependency-terminating bridge endpoints, bridges.noc is a count-only proxy; " +
          "hierarchical emission emits ChiSystemTop (HW-11/GEN-25a): each bridge is a " +
          "single-outstanding terminating proxy, v1 requires a uniform ChiConfig across " +
          "subsystems (CHK-60) and emits sync bridges only (async emission pending HW-15)")

    JObj(Vector(
      "schema" -> JStr("loom/estimate/v1"),
      "name" -> JStr(v2.meta.name),
      "configHash" -> JStr(configHash),
      "links" -> JArr(linkObjs.toVector),
      "routers" -> JArr(routerObjs.toVector),
      "nodes" -> nodeTable,
      "sam" -> samJ,
      "chi" -> chiSnapshotJson(v2),
      "flowControl" -> flowControl,
      "channels" -> JArr(channelsV2(v2).map(JStr(_)).toVector),
      "bridges" -> JObj(Vector(
        "rnAxi" -> JNum(BigDecimal(rnAxiCount)),
        "snAxi" -> JNum(BigDecimal(snAxiCount))) ++
        // GEN-25 (UP-07): nocLink bridge count, additive only when declared.
        (if (v2.nocLinks.isEmpty) Vector.empty[(String, JValue)]
         else Vector("noc" -> JNum(BigDecimal(v2.nocLinks.size))))),
      // GEN-23/GEN-24 (wave4): declaration caliber rows; qosGen drives RN
      // token buckets (HW-10) and chi-node width mismatches drive the GEN
      // ChiNiAdapt wrapper (HW-12). Empty arrays when nothing is declared.
      "qosGen" -> qosGenJsonV2(v2),
      "adapt" -> adaptJsonV2(v2),
      "totals" -> JObj(Vector(
        "routers" -> JNum(BigDecimal(v2.routers.length)),
        "links" -> JNum(BigDecimal(v2.links.length)),
        "crosspoints" -> JNum(BigDecimal(crosspoints)),
        "maxLinkCapacityGBps" -> rounded(maxCapacity),
        "latencyProxyCycles" -> JNum(BigDecimal(latencyProxy)),
        "areaProxyUm2" -> rounded(areaProxy),
        "sramBits" -> JNum(BigDecimal(sramBits)),
        "sramBlocks" -> JNum(BigDecimal(sramShapes.distinct.size)),
        "sramAreaProxyUm2" -> rounded(sramArea),
        "totalAreaProxyUm2" -> rounded(areaProxy + sramArea),
        "datWidth" -> JNum(BigDecimal(datWidth)),
        "txnIdWidth" -> JNum(BigDecimal(v2.chi.txnIdWidth)),
        "nodeIdWidth" -> JNum(BigDecimal(v2.chi.nodeIdWidth)),
        "addrWidth" -> JNum(BigDecimal(v2.chi.addrWidth)),
        "flitCount64B" -> JNum(BigDecimal(ChiWidths.flitCount(64, datWidth))),
        "maxNodes" -> JNum(BigDecimal(1 << v2.chi.nodeIdWidth))
      )),
      "assumptions" -> JArr((Vector(
        "capacity = datWidth/8 * clockMHz / 1000 GB/s per direction (DAT flit line rate; REQ/RSP/SNP are narrow flits)",
        "latency proxy = 2 cycles per router + 1 cycle per pipe stage; excludes arbitration and queueing",
        "crosspoints = derivedIn * derivedOut; CHI router is flat with one register stage per output",
        "area proxy = 16694.5 + 3264.5*crosspoints um^2, v1 four-channel nangate45 calibration; CHI calibration pending PPA (D10)",
        s"SRAM proxy = ${Sram.AreaPerBitUm2} um^2/bit placeholder over memories with depth*width >= ${Sram.ThresholdBits}; not calibrated",
        "bridges{rnAxi,snAxi} = proxy counts of AXI4 boundary bridges per node protocol (GEN-22); count-only, no PPA claim",
        "qosGen = active RN-injection token-bucket regulation (SEM-18/HW-10: bytesPerCycle = bandwidth MB/s / node freqMHz; ChiQosRegulator at RN entry after SAM/NI); scope: regulation at RN injection only, fabric arbitration unchanged",
        "adapt = active NIU DAT width adaptation (SEM-19/HW-12: mismatch = dataWidth != chi.datWidth or node.clock != attach router.clock; fifoDepth default 4); ChiNiAdapt gearboxes inserted by the GEN wrapper ChiAdaptedNetworkTop at mismatched chi-node DAT boundaries (128/256/512 tiers); AXI nodes keep bridge-layer adaptation; frequency-mismatch node CDC pending",
        "estimate only; not signoff"
      ) ++ clockingNotes ++ hierNotes).map(JStr(_))),
      "areaSensitivity" -> JArr(areaTerms.map { case (k, v) =>
        JObj(Vector("term" -> JStr(k), "um2" -> rounded(v)))
      }.toVector),
      "warnings" -> JArr(timingWarningsV2(v2).map(JStr(_)).toVector)
    ) ++ hierFields)
  }

  /** GEN-17 chi_nodes.json: rn/sn/hn/mn tables with attach/localPort, SAM with
    * tgtNode, and the snpEnable flag.
    */
  def chiNodesJsonV2(v2: IrV2, net: ChiNetworkParams): JValue = {
    val ids = ChiAssemble.nodeIds(v2)
    val idOfString = v2.nodes.map(_.id).zip(ids).toMap
    val nodeById = v2.nodes.zip(ids).map { case (n, id) => id -> n }.toMap
    val routerName = net.routers.map(r => r.index -> r.name).toMap
    def nodeObj(n: ChiNetNode): JValue = {
      val src = nodeById(n.nodeId)
      JObj(Vector(
        "id" -> JStr(src.id), "kind" -> JStr(kindName(n.kind)),
        "nodeId" -> JNum(BigDecimal(n.nodeId)), "clock" -> JStr(src.clock),
        "attach" -> JStr(routerName(n.router)), "localPort" -> JNum(BigDecimal(n.localPort))))
    }
    val samObjs = v2.sam.map { w =>
      JObj(Vector(
        "base" -> JNum(BigDecimal(w.base)), "size" -> JNum(BigDecimal(w.size)),
        "tgtNode" -> JStr(w.tgtNode),
        "tgtId" -> JNum(BigDecimal(idOfString.getOrElse(w.tgtNode, 0)))))
    }
    JObj(Vector(
      "name" -> JStr(net.name),
      "datWidth" -> JNum(BigDecimal(net.cfg.datWidth)),
      "rn" -> JArr(net.rn.map(nodeObj).toVector),
      "sn" -> JArr(net.sn.map(nodeObj).toVector),
      "hn" -> JArr(net.hn.map(nodeObj).toVector),
      "mn" -> JArr(net.mn.map(nodeObj).toVector),
      "sam" -> JArr(samObjs.toVector),
      "snpEnable" -> JBool(net.snpEnable)
    ))
  }

  /** IR-only chi_nodes rendering (no elaboration): the API fallback when
    * ChiNetworkParams cannot be assembled. localPort is elaboration-owned and
    * simply absent here; every other GEN-17 chi_nodes key is present.
    */
  def chiNodesJsonV2(v2: IrV2): JValue = {
    val ids =
      try ChiAssemble.nodeIds(v2)
      catch { case _: Throwable => v2.nodes.indices }
    val (nodeTable, samJ) = nodeSamTablesV2(v2, ids)
    val nodeFields = nodeTable match {
      case JObj(fs) => fs
      case _        => Vector.empty[(String, JValue)]
    }
    JObj(Vector(
      "name" -> JStr(v2.meta.name),
      "datWidth" -> JNum(BigDecimal(v2.chi.datWidth))) ++ nodeFields ++ Vector(
      "sam" -> samJ,
      "snpEnable" -> JBool(v2.chi.dvmEnable)))
  }

  /** GEN-15 addressMap row / GEN-17: the address map IS the SAM. The legacy
    * v1 keys (explicit/routeShift/stride/slaves[]{id,index,base,size,end})
    * stay derivable for one version (MIG-10, the GUI reads them unchanged);
    * the additive `sam[]{base,size,tgtNode,tgtId}` carries the CHI caliber.
    * `explicit` reflects whether the input declared windows (v2 `sam` key /
    * v1 slave base/size) as opposed to the derived 4 KB stride layout.
    */
  def addressMapJsonV2(v2: IrV2, explicit: Boolean): JValue = {
    val ids =
      try ChiAssemble.nodeIds(v2)
      catch { case _: Throwable => v2.nodes.indices }
    val idOf = v2.nodes.map(_.id).zip(ids).toMap
    val wins = v2.sam.sortBy(_.base)
    val slaveItems = wins.zipWithIndex.map { case (w, i) =>
      JObj(Vector(
        "id" -> JStr(w.tgtNode),
        "index" -> JNum(BigDecimal(i)),
        "base" -> JStr(f"0x${w.base}%x"),
        "size" -> JNum(BigDecimal(w.size)),
        "end" -> JStr(f"0x${w.base + w.size - 1}%x")
      ))
    }
    val samItems = wins.map { w =>
      JObj(Vector(
        "base" -> JNum(BigDecimal(w.base)),
        "size" -> JNum(BigDecimal(w.size)),
        "tgtNode" -> JStr(w.tgtNode),
        "tgtId" -> JNum(BigDecimal(idOf.getOrElse(w.tgtNode, 0)))
      ))
    }
    JObj(Vector(
      "explicit" -> JBool(explicit),
      "routeShift" -> JNum(BigDecimal(LoomDefaults.RouteShift)),
      "stride" -> JNum(BigDecimal(AddressMap.DefaultSize)),
      "slaves" -> JArr(slaveItems.toVector),
      "sam" -> JArr(samItems.toVector)
    ))
  }

  /** GEN-17 checks.json row: the loom/checks/v1 keys (name/configHash/ok/
    * errors/warnings/gates) over the v2 gate set (G1-REQ/RSP/DAT/SNP, retired
    * G7 constant true). The S2 interim `hash` key stays one version (MIG-10).
    */
  def checksJsonV2(
      v2: IrV2,
      configHash: String,
      errors: List[String],
      warnings: List[String],
      gates: Vector[(String, Boolean)]
  ): JValue = {
    val base = Vector(
      "schema" -> JStr("loom/checks/v1"),
      "name" -> JStr(v2.meta.name),
      "configHash" -> JStr(configHash),
      "hash" -> JStr(configHash),
      "ok" -> JBool(errors.isEmpty),
      "errors" -> JArr(errors.map(JStr(_)).toVector),
      "warnings" -> JArr(warnings.map(JStr(_)).toVector),
      "gates" -> JObj(gates.map { case (k, v) => (k, JBool(v): JValue) })
    )
    // GEN-25 (UP-07): additive per-subsystem gate vectors, present only when
    // subsystems are declared (flat checks.json baselines stay unchanged).
    JObj(if (v2.subsystems.isEmpty) base
      else base :+ ("gatesBySubsystem" -> gatesBySubsystemJsonV2(v2)))
  }

  /** GEN-17 ports.json (partition): v1 keys preserved (masters/slaves map to
    * RN/SN), plus nodes[] and boundary channels/lcreditDepth.
    */
  def portsJsonV2(
      v2: IrV2,
      net: ChiNetworkParams,
      pp: ChiPartitionParams,
      spec: PartitionSpec,
      configHash: String
  ): JValue = {
    val ids = ChiAssemble.nodeIds(v2)
    val nodeById = v2.nodes.zip(ids).map { case (n, id) => id -> n }.toMap
    val routerName = net.routers.map(r => r.index -> r.name).toMap
    val routerClock = v2.routers.map(r => r.name -> r.clock).toMap
    val globalLinks = ChiAssemble.routerLinks(v2)

    def nodeObj(n: ChiNetNode): JValue =
      JObj(Vector(
        "id" -> JStr(nodeById(n.nodeId).id),
        "kind" -> JStr(kindName(n.kind)),
        "nodeId" -> JNum(BigDecimal(n.nodeId)),
        "router" -> JStr(routerName(n.router)),
        "port" -> JNum(BigDecimal(n.localPort))))

    def epObj(n: ChiNetNode): JValue =
      JObj(Vector(
        "id" -> JStr(nodeById(n.nodeId).id),
        "router" -> JStr(routerName(n.router)),
        "port" -> JNum(BigDecimal(n.localPort))))

    val partitionHash = Canonical.hash(JObj(Vector(
      "name" -> JStr(spec.name), "clock" -> JStr(spec.clock),
      "members" -> JArr(spec.members.map(JStr(_)).toVector),
      "boundaryPipe" -> JNum(BigDecimal(spec.boundaryPipe)))))

    val outIds = pp.outLinks.map(_.id).toSet
    val boundaryObjs = pp.boundary.map { l =>
      val dir = if (outIds.contains(l.id)) "out" else "in"
      val local = if (dir == "out") l.fromRouter else l.toRouter
      val peer = if (dir == "out") l.toRouter else l.fromRouter
      val src = globalLinks(l.id)
      JObj(Vector(
        "link" -> JNum(BigDecimal(l.id)),
        "direction" -> JStr(dir),
        "localRouter" -> JStr(routerName(local)),
        "peerRouter" -> JStr(routerName(peer)),
        "localPort" -> JNum(BigDecimal(if (dir == "out") l.fromPort else l.toPort)),
        "peerPort" -> JNum(BigDecimal(if (dir == "out") l.toPort else l.fromPort)),
        "width" -> JNum(BigDecimal(net.cfg.datWidth)),
        "pipe" -> JNum(BigDecimal(l.pipe)),
        "localClock" -> JStr(spec.clock),
        "peerClock" -> JStr(routerClock.getOrElse(routerName(peer), "?")),
        "async" -> JBool(src.cdc == "async"),
        "channels" -> JArr(src.channels.map(JStr(_)).toVector),
        "lcreditDepth" -> JNum(BigDecimal(src.lcreditDepth))
      ))
    }

    // SEM-14 (HW-14): when the flat fabric spans multiple clock domains,
    // note the exposed domClk/domReset inputs (integrator-supplied; domain
    // index = clockDomains declaration order, domain 0 = top module clock).
    val clockingField =
      if (net.nDomain > 1) Vector(
        "clocking" -> JStr(s"flat fabric spans ${net.nDomain} clock domains (index = clockDomains declaration order, domain 0 = top module clock); domClk/domReset are exposed top-level inputs supplied by the integrator, no GEN clock wrapper (SEM-14/HW-14)"))
      else Vector.empty

    JObj(Vector(
      "schema" -> JStr("loom/ports/v1"),
      "name" -> JStr(v2.meta.name),
      "partition" -> JStr(pp.name),
      "configHash" -> JStr(configHash),
      "partitionHash" -> JStr(partitionHash),
      "clock" -> JStr(spec.clock),
      "boundaryPipe" -> JNum(BigDecimal(pp.pipe)),
      "masters" -> JArr(pp.rn.map(epObj).toVector),
      "slaves" -> JArr(pp.sn.map(epObj).toVector),
      "nodes" -> JArr(pp.nodes.sortBy(_.nodeId).map(nodeObj).toVector),
      "boundary" -> JArr(boundaryObjs.toVector)
    ) ++ clockingField)
  }

  /** GEN-25a (UP-07 phase 2): system-level ports.json for the hierarchical
    * ChiSystemTop emission. freeRn/freeSn mirror the hardware aggregation
    * order (subsystem declaration order, NodeID-sorted vectors) and exclude
    * the bridge-consumed slots; bridges[] carries the ChiBridgeLink index
    * mapping plus the synthetic endpoint names. domClk/domReset follow the
    * SEM-14 convention: flattened per-subsystem extra domains, integrator-
    * supplied inputs, no GEN clock wrapper.
    */
  def systemPortsJsonV2(
      v2: IrV2,
      projs: Seq[IrV2],
      nets: Seq[ChiNetworkParams],
      bridges: Seq[ChiBridgeLink],
      configHash: String
  ): JValue = {
    val usedSn = bridges.map(b => (b.fromSub, b.fromSn)).toSet
    val usedRn = bridges.map(b => (b.toSub, b.toRn)).toSet
    val nameOfId = projs.map { p =>
      p.nodes.map(_.id).zip(ChiAssemble.nodeIds(p)).map { case (n, i) => i -> n }.toMap
    }
    def epObj(s: Int, n: ChiNetNode): JValue = {
      val routerName = nets(s).routers.map(r => r.index -> r.name).toMap
      JObj(Vector(
        "subsystem" -> JStr(v2.subsystems(s).name),
        "id" -> JStr(nameOfId(s).getOrElse(n.nodeId, "?")),
        "kind" -> JStr(kindName(n.kind)),
        "nodeId" -> JNum(BigDecimal(n.nodeId)),
        "router" -> JStr(routerName(n.router)),
        "port" -> JNum(BigDecimal(n.localPort))))
    }
    val freeRn = for (s <- nets.indices; i <- nets(s).rn.indices if !usedRn((s, i)))
      yield epObj(s, nets(s).rn(i))
    val freeSn = for (s <- nets.indices; j <- nets(s).sn.indices if !usedSn((s, j)))
      yield epObj(s, nets(s).sn(j))
    val bridgeObjs = bridges.map { b =>
      JObj(Vector(
        "fromSub" -> JStr(v2.subsystems(b.fromSub).name),
        "fromSn" -> JNum(BigDecimal(b.fromSn)),
        "fromNode" -> JStr(nameOfId(b.fromSub).getOrElse(nets(b.fromSub).sn(b.fromSn).nodeId, "?")),
        "toSub" -> JStr(v2.subsystems(b.toSub).name),
        "toRn" -> JNum(BigDecimal(b.toRn)),
        "toNode" -> JStr(nameOfId(b.toSub).getOrElse(nets(b.toSub).rn(b.toRn).nodeId, "?")),
        "windows" -> JArr(b.windows.map { case (base, size) =>
          JObj(Vector("base" -> JNum(BigDecimal(base)), "size" -> JNum(BigDecimal(size))))
        }.toVector),
        "asyncFifoDepth" -> JNum(BigDecimal(b.asyncFifoDepth)),
        "dvmForward" -> JBool(b.dvmForward)))
    }
    val nDomExtra = nets.map(_.nDomain - 1).sum
    val clockingField =
      if (nDomExtra > 0) Vector(
        "clocking" -> JStr(s"system exposes $nDomExtra domClk/domReset input pair(s) (per-subsystem domains 1..n-1 flattened in subsystem declaration order; domain 0 of each subsystem is its top module clock); integrator-supplied, no GEN clock wrapper (SEM-14/HW-14)"))
      else Vector.empty
    JObj(Vector(
      "schema" -> JStr("loom/ports/v1"),
      "name" -> JStr(v2.meta.name),
      "configHash" -> JStr(configHash),
      "top" -> JStr("ChiSystemTop"),
      "freeRn" -> JArr(freeRn.toVector),
      "freeSn" -> JArr(freeSn.toVector),
      "bridges" -> JArr(bridgeObjs.toVector)
    ) ++ clockingField)
  }

  /** GEN-09: manifest plus the additive irSchema/chi keys. */
  def manifestJsonV2(
      name: String,
      configHash: String,
      version: String,
      files: Seq[(String, String)],
      chi: JValue
  ): JValue =
    manifestJson(name, configHash, version, files) match {
      case JObj(fs) => JObj(fs ++ Vector("irSchema" -> JStr("loom/v2"), "chi" -> chi))
      case other    => other
    }

  /** GEN-17/GEN-19: formal report plus the D11 channel-class limitation. */
  def formalJsonV2(
      name: String,
      configHash: String,
      checks: Seq[(String, String, Boolean, String)]
  ): JValue =
    formalJson(name, configHash, checks) match {
      case JObj(fs) => JObj(fs.map {
        case ("limitations", JArr(xs)) =>
          "limitations" -> JArr(xs :+ JStr("proofs cover only the generated channel classes (D11)"))
        case other => other
      })
      case other => other
    }

  /** GEN-17 signoff: Schema loom/v2, G1 four-key gate table, node/SAM summary. */
  def signoffMarkdownV2(
      v2: IrV2,
      configHash: String,
      gates: Vector[(String, Boolean)],
      warnings: List[String],
      artifactCounts: (Int, Int, Int),
      formal: Option[JValue]
  ): String = {
    val (rtlCount, assertCount, tbCount) = artifactCounts
    val gateRows = gates.map { case (g, ok) => s"| $g | ${if (ok) "PASS" else "FAIL"} |" }
    val warnBlock =
      if (warnings.isEmpty) "- none"
      else warnings.map(w => s"- $w").mkString("\n")
    val formalBlock = formal match {
      case Some(JObj(fields)) =>
        val checks = fields.toMap.get("checks").collect { case JArr(xs) => xs }.getOrElse(Vector.empty)
        val rows = checks.map {
          case JObj(f) =>
            val m = f.toMap
            def s(k: String) = m.get(k).collect { case JStr(x) => x }.getOrElse("?")
            s"| ${s("model")} | ${s("property")} | ${s("result")} |"
          case _ => "| ? | ? | ? |"
        }
        ("| model | property | result |\n|---|---|---|\n" + rows.mkString("\n")) +
          "\n\nSee formal/formal.json and formal/*.log."
      case _ =>
        "Not run. Execute `loom formal --config <cfg> --out <dir>` to add bounded proofs."
    }
    val ids = ChiAssemble.nodeIds(v2)
    val idOf = v2.nodes.map(_.id).zip(ids).toMap
    val nodeRows = v2.nodes.zip(ids).sortBy(_._2).map { case (n, id) =>
      s"| ${n.id} | ${n.kind} | $id | ${n.attach} |"
    }
    val samRows = v2.sam.sortBy(_.base).map { w =>
      s"| 0x${w.base.toHexString} | ${w.size} | ${w.tgtNode} | ${idOf.getOrElse(w.tgtNode, "?")} |"
    }

    val lines = Seq(
      s"# Loom Signoff - ${v2.meta.name}",
      "",
      s"Config hash: `$configHash`",
      "Schema: `loom/v2`",
      "",
      "## Static gates",
      "",
      "| gate | result |",
      "|---|---|"
    ) ++ gateRows ++ Seq(
      "",
      "## Nodes",
      "",
      "| id | kind | nodeId | attach |",
      "|---|---|---|---|"
    ) ++ nodeRows ++ Seq(
      "",
      "## SAM",
      "",
      "| base | size | tgtNode | tgtId |",
      "|---|---|---|---|"
    ) ++ samRows ++ Seq(
      "",
      "## Warnings",
      "",
      warnBlock,
      "",
      "## Artifacts",
      "",
      s"- RTL files: $rtlCount",
      s"- Assertion files: $assertCount",
      s"- Testbench skeletons: $tbCount",
      "",
      "## Formal (bounded)",
      "",
      formalBlock,
      "",
      "## Signoff checklist",
      "",
      "- [x] Static gates evaluated (table above)",
      "- [x] Artifacts hashed in manifest.json",
      "- [x] TB skeleton and assertion list emitted",
      "- [ ] Bounded formal proofs attached (run `loom formal`)",
      "- [ ] Area proxy reviewed against the target budget",
      "- [ ] Integration-level simulation signed by the owner",
      "",
      "## Limitations",
      "",
      "- The estimate is a planning proxy, not a timing or area signoff.",
      "- Formal results cover the abstract token models; simulation covers the RTL.",
      "- Any IR change invalidates this report: re-run `loom emit`."
    )
    lines.mkString("\n") + "\n"
  }
}
