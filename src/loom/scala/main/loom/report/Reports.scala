package loom.report

import loom.ir._
import loom.rtl.{NetworkParams, PartitionParams}

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

  /** Stable port contract for one partition. Names and ordering are derived
    * from the IR only, so the file is reproducible across emissions.
    */
  def portsJson(
      ir: Ir,
      net: NetworkParams,
      pp: PartitionParams,
      configHash: String
  ): JValue = {
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")

    def epObj(id: String, router: String, port: Int): JValue =
      JObj(Vector(
        "id" -> JStr(id),
        "router" -> JStr(router),
        "port" -> JNum(BigDecimal(port))
      ))

    def boundaryEntry(l: loom.rtl.NetLinkSpec, dir: String): (Int, JValue) = {
      val local = if (dir == "out") l.fromRouter else l.toRouter
      val peer = if (dir == "out") l.toRouter else l.fromRouter
      val peerClock = ir.routers.find(_.name == net.routers(peer).name).map(_.clock).getOrElse("?")
      l.id -> JObj(Vector(
        "link" -> JNum(BigDecimal(l.id)),
        "direction" -> JStr(dir),
        "localRouter" -> JStr(net.routers(local).name),
        "peerRouter" -> JStr(net.routers(peer).name),
        "localPort" -> JNum(BigDecimal(if (dir == "out") l.fromPort else l.toPort)),
        "peerPort" -> JNum(BigDecimal(if (dir == "out") l.toPort else l.fromPort)),
        "width" -> JNum(BigDecimal(l.width)),
        "pipe" -> JNum(BigDecimal(l.pipe)),
        "localClock" -> JStr(pp.clock),
        "peerClock" -> JStr(peerClock),
        "async" -> JBool(l.async)
      ))
    }

    val boundaryObjs = (pp.outLinks.map(boundaryEntry(_, "out")) ++
      pp.inLinks.map(boundaryEntry(_, "in")))
      .sortBy(_._1)
      .map(_._2)

    JObj(Vector(
      "schema" -> JStr("loom/ports/v1"),
      "name" -> JStr(ir.meta.name),
      "partition" -> JStr(pp.name),
      "configHash" -> JStr(configHash),
      "partitionHash" -> JStr(pp.hash),
      "clock" -> JStr(pp.clock),
      "boundaryPipe" -> JNum(BigDecimal(pp.pipe)),
      "masters" -> JArr(pp.masters.map { i =>
        epObj(masters(i).id, net.routers(net.masterRouter(i)).name, net.masterPort(i))
      }.toVector),
      "slaves" -> JArr(pp.slaves.map { j =>
        epObj(slaves(j).id, net.routers(net.slaveRouter(j)).name, net.slavePort(j))
      }.toVector),
      "boundary" -> JArr(boundaryObjs.toVector)
    ))
  }

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
}
