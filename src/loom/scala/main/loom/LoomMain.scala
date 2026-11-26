package loom

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest

import _root_.circt.stage.ChiselStage
import loom.chi.{ChiAxiNetworkTop, ChiNetworkTop, ChiPartitionStub, ChiPartitionTop, ChiSystemTop, ChiWrapperTop}
import loom.gen.{ChiAdaptedNetworkTop, ChiAssemble}
import loom.graph.FabricGraph
import loom.ir.{Canonical, Ir, IrV2, JArr, JBool, JObj, JStr, JValue, Json, Upgrade}
import loom.report.{Reports, Sram, TbGen}

object LoomMain {
  // GEN-20: 2.0.0 with the single CHI generation path (GEN-02).
  private val ToolVersion = "2.0.0"

  private[loom] final case class Validated(
      ir: IrV2,
      hash: String,
      gates: Vector[(String, Boolean)],
      errors: List[String],
      warnings: List[String],
      upgraded: Boolean
  )

  def main(args: Array[String]): Unit = args.toList match {
    case "check" :: rest => runCheck(rest)
    case "estimate" :: rest => runEstimate(rest)
    case "emit" :: rest => runEmit(rest)
    case "emit-chi" :: rest => runEmitChi(rest)
    case "formal" :: rest => runFormal(rest)
    case "diff" :: rest => runDiff(rest)
    case "serve" :: rest => runServe(rest)
    case "version" :: _ =>
      println(s"loom $ToolVersion (schema loom/v2, loom/v1 auto-upgrade)")
    case _ => usage()
  }

  private def usage(): Unit = {
    println("usage:")
    println("  loom check    --config <file> [--out <dir>]")
    println("  loom estimate --config <file> --out <dir>")
    println("  loom emit     --config <file> --out <dir> [--cache] [--formal]")
    println("  loom emit-chi (deprecated alias of emit; removed next version)")
    println("  loom formal   --config <file> --out <dir>")
    println("  loom diff     --a <file|dir> --b <file|dir>")
    println("  loom serve    --dir <dir> [--port <n>] [--examples <dir>] [--work <dir>]")
    println("  loom version")
    sys.exit(2)
  }

  private def opt(xs: List[String], key: String): Option[String] =
    xs.sliding(2).collectFirst { case List(`key`, v) => v }

  private def must(xs: List[String], key: String): String =
    opt(xs, key).getOrElse { usage(); sys.exit(2) }

  private def has(xs: List[String], key: String): Boolean = xs.contains(key)

  private def readConfig(path: String): JValue =
    Json.parse(Files.readString(Paths.get(path)))

  private def writeJson(dir: String, file: String, v: JValue): Unit = {
    val d = Paths.get(dir)
    Files.createDirectories(d)
    Files.writeString(d.resolve(file), Canonical.pretty(v) + "\n")
  }

  private def sha256(path: Path): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(Files.readAllBytes(path))
    md.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  private def filesUnder(root: Path, skip: Path => Boolean): Seq[Path] = {
    import scala.jdk.CollectionConverters._
    if (!Files.exists(root)) Seq.empty
    else Files.walk(root).iterator().asScala
      .filter(Files.isRegularFile(_))
      .filterNot(skip)
      .toSeq
      .sortBy(p => root.relativize(p).toString.replace('\\', '/'))
  }

  /** GEN-01 single validation path: schema dispatch (loom/v1 -> Ir.fromJson +
    * Upgrade.v1ToV2 with MIG-07 warnings; loom/v2 -> IrV2.fromJson), then
    * ChecksV2 gates. hash = IrV2.hash of the normalized v2 object (GEN-06),
    * so the chi block is included and equivalent v1/v2 spellings agree.
    */
  private[loom] def validateV2(root: JValue): Validated = {
    val schema = root match {
      case o: JObj => o.get("schema").collect { case JStr(s) => s }
      case _ => None
    }
    val (v2, upgradeWarnings) = schema match {
      case Some("loom/v1") =>
        val up =
          try Upgrade.v1ToV2(Ir.fromJson(root))
          catch {
            case e: Ir.IrException =>
              println(s"ERROR: ${e.getMessage}")
              sys.exit(3)
          }
        (up.ir, up.warnings.toList)
      case Some("loom/v2") =>
        val v2 =
          try IrV2.fromJson(root)
          catch {
            case e: Ir.IrException =>
              println(s"ERROR: ${e.getMessage}")
              sys.exit(3)
          }
        (v2, Nil)
      case other =>
        println(s"ERROR: C0: [CHK-01] schema must be 'loom/v2' (or 'loom/v1' for upgrade), got '${other.getOrElse("")}'")
        sys.exit(3)
    }
    val res = loom.check.ChecksV2.run(v2)
    Validated(
      ir = v2,
      hash = IrV2.hash(v2),
      gates = res.gates,
      errors = res.errors,
      warnings = upgradeWarnings ++ Reports.timingWarningsV2(v2),
      upgraded = schema.contains("loom/v1")
    )
  }

  /** GEN-17 checks.json caliber (S5): loom/checks/v1 keys + G1 four-key gates;
    * the S2 interim `hash` key stays one version (MIG-10). Warnings carry the
    * MIG-07 upgrade deprecations.
    */
  private def checksJsonV2(v: Validated): JValue =
    Reports.checksJsonV2(v.ir, v.hash, v.errors, v.warnings, v.gates)

  private[loom] def runCheck(xs: List[String]): Unit = {
    val cfg = must(xs, "--config")
    val v = validateV2(readConfig(cfg))
    opt(xs, "--out").foreach { dir =>
      writeJson(dir, "checks.json", checksJsonV2(v))
      writeJson(dir, "graph.json", FabricGraph.fromV2(v.ir).toJson)
    }
    if (v.errors.nonEmpty) {
      v.errors.foreach(e => println(s"ERROR: $e"))
      sys.exit(3)
    }
    println(s"OK name=${v.ir.meta.name} hash=${v.hash} warnings=${v.warnings.size}")
    v.warnings.foreach(w => println(s"WARN: $w"))
  }

  private[loom] def runEstimate(xs: List[String]): Unit = {
    val cfg = must(xs, "--config")
    val out = must(xs, "--out")
    val v = validateV2(readConfig(cfg))
    if (v.errors.nonEmpty) {
      v.errors.foreach(e => println(s"ERROR: $e"))
      sys.exit(3)
    }
    writeJson(out, "estimate.json", Reports.estimateJsonV2(v.ir, v.hash))
    println(s"estimate written to $out/estimate.json hash=${v.hash} warnings=${v.warnings.size}")
    v.warnings.foreach(w => println(s"WARN: $w"))
  }

  private def manifestMatches(out: String, hash: String): Boolean = {
    val p = Paths.get(out, "manifest.json")
    if (!Files.exists(p)) false
    else Json.parse(Files.readString(p)) match {
      case o: JObj =>
        val m = o.fields.toMap
        val hashOk = m.get("configHash").contains(JStr(hash))
        val verOk = m.get("version").contains(JStr(ToolVersion))
        val files = m.get("files").collect { case JArr(xs) => xs }.getOrElse(Vector.empty)
        hashOk && verOk && files.nonEmpty && files.forall {
          case JObj(g) =>
            val f = g.toMap
            (for {
              rel <- f.get("path").collect { case JStr(s) => s }
              h <- f.get("sha256").collect { case JStr(s) => s }
            } yield {
              val fp = Paths.get(out, rel)
              Files.exists(fp) && sha256(fp) == h
            }).getOrElse(false)
          case _ => false
        }
      case _ => false
    }
  }

  /** GEN-01 stage 8 / GEN-02: the single CHI emission path. Flat networks emit
    * ChiNetworkTop (single router included, HW-03), or ChiAxiNetworkTop when
    * any node.protocol=axi4 (GEN-22); partitioned configs emit ChiWrapperTop
    * under tops/, one ChiPartitionTop + ports.json per partition and a
    * ChiPartitionStub under partitions/<name>/stub (HW-06, MN/DVM parity per
    * HW-08). Async cuts (cdc=async boundary links) insert ChiAsyncLink per
    * channel (HW-04); flat async cuts are consumed by the multi-domain
    * ChiNetworkTop (HW-14, domClk/domReset exposed as top inputs). When a
    * chi-protocol node's DAT width mismatches the fabric flit width, the
    * GEN-owned ChiAdaptedNetworkTop wraps the network with ChiNiAdapt
    * gearboxes (HW-12 §9.3, GEN-24).
    */
  /** GEN-25a emit guard, pure for testability (sys.exit is not trap-able on
    * modern JDKs). HW-11 closed the hierarchical guard; the remaining v1
    * limit is async bridges: ChiSystemTop hardwires the bridge dstClk to
    * io.domClk(0), which is empty on all-single-domain systems and is not
    * generally the to-side clock (HW-15). Some(message) ⇒ refuse emission;
    * check/estimate still accept async declarations.
    */
  private[loom] def hierarchicalEmitGuard(v2: IrV2): Option[String] =
    if (v2.nocLinks.exists(_.asyncFifoDepth >= 2))
      Some("ERROR: async inter-NoC bridge emission requires HW-15 (ChiSystemTop dstClk wiring); v1 emits sync bridges only, check/estimate accept async declarations")
    else None

  /** GEN-25a: ChiSystemTop instantiates ChiNetworkTop directly, so the flat
    * path's ChiAdaptedNetworkTop gearboxes have no hierarchical equivalent
    * yet (HW-16). Refuse emission rather than silently drop node width
    * adaptation; matched-width subsystems are unaffected.
    */
  private[loom] def hierarchicalAdaptGuard(v2: IrV2): Option[String] =
    if (v2.subsystems.exists(s =>
      ChiAdaptedNetworkTop.needed(ChiAssemble.networkParams(IrV2.projectSubsystem(v2, s)))))
      Some("ERROR: node width adaptation inside subsystems requires HW-16 (ChiSystemTop adapt integration); v1 hierarchical emission supports matched-width chi nodes only")
    else None

  private[loom] def runEmit(xs: List[String]): Unit = {
    val cfg = must(xs, "--config")
    val out = must(xs, "--out")
    val v = validateV2(readConfig(cfg))

    if (v.errors.nonEmpty) {
      writeJson(out, "checks.json", checksJsonV2(v))
      v.errors.foreach(e => println(s"ERROR: $e"))
      sys.exit(3)
    }

    // GEN-25a (UP-07 phase 2): sync hierarchical systems emit ChiSystemTop;
    // async nocLinks stay guarded until HW-15 fixes the dstClk wiring.
    hierarchicalEmitGuard(v.ir).foreach { msg =>
      println(msg)
      sys.exit(3)
    }
    hierarchicalAdaptGuard(v.ir).foreach { msg =>
      println(msg)
      sys.exit(3)
    }

    if (has(xs, "--cache") && manifestMatches(out, v.hash)) {
      println(s"up to date: $out (hash=${v.hash})")
      return
    }

    Files.createDirectories(Paths.get(out))

    if (v.ir.subsystems.nonEmpty) {
      emitSystem(xs, out, v)
      return
    }

    val net =
      try ChiAssemble.networkParams(v.ir)
      catch {
        case e: Throwable =>
          println(s"ERROR: ${e.getMessage}")
          sys.exit(3)
      }
    val asyncCuts = ChiAssemble.asyncCuts(v.ir)

    // Async cuts on partition boundaries go to the wrapper; otherwise the
    // flat ChiNetworkTop consumes them itself (HW-14: routerDomain clocks +
    // ChiAsyncLink per crossing link, domClk/domReset exposed as inputs).
    // GEN-22 (HW-09/A3): any axi4 node selects the per-node mixed-protocol
    // ChiAxiNetworkTop (rnAxi/rnChi/snAxi/snChi split by node protocol);
    // an all-chi fabric keeps the native ChiNetworkTop. GEN-24 (HW-12 §9.3):
    // width-mismatched chi DAT boundaries select the GEN-owned adaptation
    // wrapper around either inner top.
    val emitFlat = v.ir.partitions.isEmpty || asyncCuts.isEmpty
    val anyAxi = v.ir.nodes.exists(_.protocol == "axi4")
    if (emitFlat && anyAxi && net.nDomain > 1) {
      println("ERROR: flat multi-clock fabric with axi4 nodes needs ChiAxiNetworkTop " +
        "domClk/domReset exposure (pending); use partitions for async cuts or a single clock domain")
      sys.exit(3)
    }
    val adapted = emitFlat && ChiAdaptedNetworkTop.needed(net)
    val flatTopName =
      if (adapted) "ChiAdaptedNetworkTop"
      else if (anyAxi) "ChiAxiNetworkTop"
      else "ChiNetworkTop"
    if (emitFlat)
      ChiselStage.emitSystemVerilogFile(
        if (adapted) new ChiAdaptedNetworkTop(net)
        else if (anyAxi) new ChiAxiNetworkTop(net)
        else new ChiNetworkTop(net),
        Array("--target-dir", out))

    writeJson(out, "checks.json", checksJsonV2(v.copy(errors = Nil)))
    writeJson(out, "estimate.json", Reports.estimateJsonV2(v.ir, v.hash))
    writeJson(out, "graph.json", FabricGraph.fromV2(v.ir).toJson)
    writeJson(out, "chi_nodes.json", Reports.chiNodesJsonV2(v.ir, net))

    if (v.ir.partitions.nonEmpty) {
      val pps =
        try ChiAssemble.partitionParams(v.ir, net)
        catch {
          case e: Throwable =>
            println(s"ERROR: ${e.getMessage}")
            sys.exit(3)
        }
      val topsDir = Paths.get(out, "tops")
      Files.createDirectories(topsDir)
      ChiselStage.emitSystemVerilogFile(
        new ChiWrapperTop(net, pps.map(_._2), asyncCuts), Array("--target-dir", topsDir.toString))
      pps.foreach { case (spec, pp) =>
        val pdir = Paths.get(out, "partitions", spec.name)
        val stubDir = pdir.resolve("stub")
        Files.createDirectories(pdir)
        Files.createDirectories(stubDir)
        ChiselStage.emitSystemVerilogFile(
          new ChiPartitionTop(pp), Array("--target-dir", pdir.toString))
        writeJson(pdir.toString, "ports.json", Reports.portsJsonV2(v.ir, net, pp, spec, v.hash))
        ChiselStage.emitSystemVerilogFile(
          new ChiPartitionStub(pp), Array("--target-dir", stubDir.toString))
      }
      println(s"emitted ${v.ir.partitions.size} partition top(s), wrapper and stubs")
    }

    writeSramBlocks(out, v.ir)

    val flatTop = if (emitFlat) Some(flatTopName) else None
    val extraTops =
      if (v.ir.partitions.nonEmpty && flatTop.nonEmpty) Seq("ChiWrapperTop") else Nil
    writeTestbenches(out, flatTop.getOrElse("ChiWrapperTop"), extraTops, v.ir.chi)
    writeAssertionList(out)
    val formal = if (has(xs, "--formal")) Some(runFormalModels(out, v)) else None
    writeSignoff(out, v, formal)
    writeManifest(out, v)

    println(s"emitted ${net.name} (${net.nRn} RN x ${net.nSn} SN) to $out")
    println(s"hash=${v.hash} datWidth=${net.cfg.datWidth} warnings=${v.warnings.size}")
    v.warnings.foreach(w => println(s"WARN: $w"))
  }

  /** GEN-25a (UP-07 phase 2): hierarchical emission — one ChiSystemTop over
    * the SEM-20a augmented subsystem params (synthetic bridge endpoints are
    * consumed internally, freeRn/freeSn aggregate the rest). Reports follow
    * the flat caliber: per-subsystem chi_nodes under subsystems/<name>/, a
    * system ports.json (bridge slots excluded), manifest/signoff/tb as usual.
    * The estimate subsystems echo stays the DECLARED IR (no synthetics).
    */
  private def emitSystem(xs: List[String], out: String, v: Validated): Unit = {
    val (nets, bridges) =
      try ChiAssemble.systemParams(v.ir)
      catch {
        case e: Throwable =>
          println(s"ERROR: ${e.getMessage}")
          sys.exit(3)
      }
    // ChiSystemTop requires a uniform ChiConfig (CHK-60 covers the chi block;
    // topology-derived cfg fields such as nHalf surface here as a clear error).
    try ChiselStage.emitSystemVerilogFile(
      new ChiSystemTop(nets, bridges), Array("--target-dir", out))
    catch {
      case e: Throwable =>
        println(s"ERROR: ${e.getMessage}")
        sys.exit(3)
    }

    writeJson(out, "checks.json", checksJsonV2(v.copy(errors = Nil)))
    writeJson(out, "estimate.json", Reports.estimateJsonV2(v.ir, v.hash))
    writeJson(out, "graph.json", FabricGraph.fromV2(v.ir).toJson)
    val projs = ChiAssemble.systemProjections(v.ir)
    v.ir.subsystems.zipWithIndex.foreach { case (s, i) =>
      val dir = Paths.get(out, "subsystems", s.name)
      Files.createDirectories(dir)
      writeJson(dir.toString, "chi_nodes.json", Reports.chiNodesJsonV2(projs(i), nets(i)))
    }
    writeJson(out, "ports.json", Reports.systemPortsJsonV2(v.ir, projs, nets, bridges, v.hash))

    writeTestbenches(out, "ChiSystemTop", Nil, v.ir.chi)
    writeAssertionList(out)
    val formal = if (has(xs, "--formal")) Some(runFormalModels(out, v)) else None
    writeSignoff(out, v, formal)
    writeManifest(out, v)

    println(s"emitted ${v.ir.meta.name} (${nets.size} subsystem(s), ${bridges.size} inter-NoC bridge(s)) to $out")
    println(s"hash=${v.hash} datWidth=${nets.head.cfg.datWidth} warnings=${v.warnings.size}")
    v.warnings.foreach(w => println(s"WARN: $w"))
  }

  /** Behavioral SRAM macros for planning. The emitted fabric RTL keeps its
    * inferred memories and nothing is rewritten to use these files.
    */
  private def writeSramBlocks(out: String, ir: IrV2): Unit = {
    val shapes = Reports.sramNeedsOfV2(ir)
      .filter { case (d, w) => d * w >= Sram.ThresholdBits }
      .distinct
      .sortBy(s => (s._1, s._2))
    if (shapes.isEmpty) return
    val dir = Paths.get(out, "sram")
    Files.createDirectories(dir)
    shapes.foreach { case (d, w) =>
      Files.writeString(dir.resolve(Sram.fileName(d, w)), Sram.singlePort(d, w))
    }
    val list = shapes.map { case (d, w) => Sram.fileName(d, w) }.sorted
    Files.writeString(dir.resolve("filelist.f"), list.mkString("\n") + "\n")
    println(s"emitted ${shapes.size} SRAM block(s) to $dir")
  }

  /** GEN-17 tb row (S5): CHI stimulus skeletons via TbGen.testbenchV2; flit
    * counts and ID widths come from the chi block (ENC-01/04/05).
    */
  private def writeTestbenches(out: String, top: String, extraTops: Seq[String], chi: loom.ir.ChiBlockV2): Unit = {
    val tbDir = Paths.get(out, "tb")
    Files.createDirectories(tbDir)
    // A partitioned config emits its wrapper under tops/, which can be the
    // primary top when the flat network is skipped.
    val topSv =
      if (Files.exists(Paths.get(out, s"$top.sv"))) Paths.get(out, s"$top.sv")
      else Paths.get(out, "tops", s"$top.sv")
    val candidates = Seq(top -> topSv) ++
      extraTops.map(t => t -> Paths.get(out, "tops", s"$t.sv"))
    candidates.foreach { case (name, sv) =>
      if (Files.exists(sv)) {
        val text = Files.readString(sv)
        Files.writeString(tbDir.resolve(s"${name}_tb.sv"), TbGen.testbenchV2(name, text, chi))
      }
    }
  }

  private def writeAssertionList(out: String): Unit = {
    val root = Paths.get(out)
    val files = filesUnder(root.resolve("verification"), _ => false)
      .map(p => root.relativize(p).toString.replace('\\', '/'))
    Files.createDirectories(root.resolve("tb"))
    Files.writeString(root.resolve("tb").resolve("assertions.f"),
      (files.mkString("\n") + (if (files.nonEmpty) "\n" else "")))
  }

  private def artifactCounts(out: String): (Int, Int, Int) = {
    val root = Paths.get(out)
    val all = filesUnder(root, p => {
      val rel = root.relativize(p).toString.replace('\\', '/')
      rel == "manifest.json" || rel.startsWith("formal/")
    })
    val sv = all.filter(_.getFileName.toString.endsWith(".sv"))
    val (verif, rest) = sv.partition { p =>
      root.relativize(p).toString.replace('\\', '/').startsWith("verification/")
    }
    val (tb, rtl) = rest.partition { p =>
      root.relativize(p).toString.replace('\\', '/').startsWith("tb/")
    }
    (rtl.size, verif.size, tb.size)
  }

  private def writeSignoff(out: String, v: Validated, formal: Option[JValue]): Unit = {
    val md = Reports.signoffMarkdownV2(v.ir, v.hash, v.gates, v.warnings,
      artifactCounts(out), formal)
    Files.writeString(Paths.get(out, "signoff.md"), md)
  }

  private def writeManifest(out: String, v: Validated): Unit = {
    val root = Paths.get(out)
    val files = filesUnder(root, p => {
      val rel = root.relativize(p).toString.replace('\\', '/')
      rel == "manifest.json" || rel.startsWith("formal/")
    }).map(p => root.relativize(p).toString.replace('\\', '/') -> sha256(p))
    writeJson(out, "manifest.json",
      Reports.manifestJsonV2(v.ir.meta.name, v.hash, ToolVersion, files,
        Reports.chiSnapshotJson(v.ir)))
  }

  /** Bounded proofs over the control-token models. The models are independent
    * of the configuration, so results are reused across networks.
    */
  private def runFormalModels(out: String, v: Validated): JValue = {
    val dir = Paths.get(out, "formal")
    Files.createDirectories(dir)
    val models = Seq(
      ("token_8x8", "src/loom/formal/token_8x8.sv", 12, "P1-P3 + route safety"),
      ("token_rr8", "src/loom/formal/token_rr8.sv", 20, "P5 bounded progress"),
      ("token_response", "src/loom/formal/token_response.sv", 12, "P6/P8 no loss, exact-once"),
      ("token_cdc", "src/loom/formal/token_cdc.sv", 12, "P7 CDC credit"),
      ("token_cdc_buf", "src/loom/formal/token_cdc_buf.sv", 12, "G9 async boundary buffer"),
      ("token_chi_lcredit", "src/loom/formal/token_chi_lcredit.sv", 12, "CHI L-Credit conservation"),
      ("token_chi_dbid", "src/loom/formal/token_chi_dbid.sv", 12, "CHI DBID exact-once"),
      ("token_chi_pcrd", "src/loom/formal/token_chi_pcrd.sv", 12, "CHI P-Credit retry no-loss"),
      ("token_chi_dvm", "src/loom/formal/token_chi_dvm.sv", 12, "CHI DVM collect completeness"),
      ("token_chi_async_credit", "src/loom/formal/token_chi_async_credit.sv", 12, "CHI async link cross-domain credit"),
      ("token_chi_noc_bridge", "src/loom/formal/token_chi_noc_bridge.sv", 12, "CHI inter-NoC bridge cross-domain deadlock freedom")
    )
    val checks = models.map { case (top, src, depth, prop) =>
      val log = dir.resolve(s"${top}.log")
      val ok =
        try {
          val pb = new ProcessBuilder(
            "yosys", "-p",
            s"read_verilog -formal -sv $src; prep -top $top; async2sync; memory; chformal -lower; " +
              s"sat -seq $depth -set-init-zero -prove-asserts")
          pb.redirectErrorStream(true)
          pb.redirectOutput(log.toFile)
          pb.start().waitFor() == 0 &&
            Files.readString(log).contains("SAT proof finished - no model found: SUCCESS")
        } catch {
          case e: Throwable =>
            Files.writeString(log, s"launch failed: ${e.getMessage}\n")
            false
        }
      println(s"formal $top ($prop, depth $depth): ${if (ok) "PASS" else "FAIL"}")
      (top, prop, ok, s"formal/${top}.log")
    }
    val j = Reports.formalJsonV2(v.ir.meta.name, v.hash, checks)
    Files.writeString(dir.resolve("formal.json"), Canonical.pretty(j) + "\n")
    j
  }

  private[loom] def runFormal(xs: List[String]): Unit = {
    val cfg = must(xs, "--config")
    val out = must(xs, "--out")
    val v = validateV2(readConfig(cfg))
    if (v.errors.nonEmpty) {
      v.errors.foreach(e => println(s"ERROR: $e"))
      sys.exit(3)
    }
    Files.createDirectories(Paths.get(out))
    val j = runFormalModels(out, v)
    if (Files.exists(Paths.get(out, "checks.json"))) {
      writeSignoff(out, v, Some(j))
      writeManifest(out, v)
    }
    val ok = j match {
      case o: JObj => o.fields.toMap.get("ok").contains(JBool(true))
      case _ => false
    }
    if (!ok) sys.exit(4)
  }

  /** CG5: compare the report baseline of two emitted directories. */
  private def diffReports(a: Path, b: Path): Unit = {
    val names = Seq("checks.json", "estimate.json", "graph.json")
    val missing = names.filter(n => !Files.exists(a.resolve(n)) || !Files.exists(b.resolve(n)))
    if (missing.nonEmpty) {
      missing.foreach(n => println(s"ERROR missing $n in one of the directories"))
      sys.exit(3)
    }
    val diffs = names.filter { n =>
      Canonical.render(Json.parse(Files.readString(a.resolve(n)))) !=
        Canonical.render(Json.parse(Files.readString(b.resolve(n))))
    }
    if (diffs.isEmpty) {
      println(s"MATCH reports ${names.mkString(", ")}")
    } else {
      diffs.foreach(n => println(s"DIFF report $n"))
      sys.exit(4)
    }
  }

  private[loom] def runDiff(xs: List[String]): Unit = {
    val aPath = Paths.get(must(xs, "--a"))
    val bPath = Paths.get(must(xs, "--b"))
    if (Files.isDirectory(aPath) && Files.isDirectory(bPath)) {
      diffReports(aPath, bPath)
      return
    }

    val a = validateV2(readConfig(aPath.toString))
    val b = validateV2(readConfig(bPath.toString))
    if (a.errors.nonEmpty || b.errors.nonEmpty) {
      (a.errors ++ b.errors).foreach(e => println(s"ERROR: $e"))
      sys.exit(3)
    }

    // GEN-12 file mode: nodes (kind:id:nodeId) / routers / links contract
    // summaries; the v1 endpoints summary is retired.
    def nodes(v: Validated) = {
      val ids = ChiAssemble.nodeIds(v.ir)
      v.ir.nodes.zip(ids).map { case (n, id) => s"${n.kind}:${n.id}:$id" }.sorted
    }
    def routers(v: Validated) =
      v.ir.routers.map { r =>
        val (in, out) = Reports.derivedPortsV2(v.ir, r.name)
        s"${r.name}:${in}x${out}:${r.routing}"
      }.sorted
    def links(v: Validated) =
      v.ir.links.map(l => s"${l.from}->${l.to}:${l.width}:${l.cdc}:${l.pipe}").sorted

    val diffs = Seq(
      "nodes" -> (nodes(a), nodes(b)),
      "routers" -> (routers(a), routers(b)),
      "links" -> (links(a), links(b))
    ).collect { case (k, (l, r)) if l != r => k }

    val pa = a.ir.partitions.map(_.name)
    val pb = b.ir.partitions.map(_.name)
    if (pa != pb) println(s"info: partitions differ A=$pa B=$pb")

    if (diffs.isEmpty) {
      println(s"MATCH network contract (A hash=${a.hash} B hash=${b.hash})")
      if (a.hash != b.hash) println("note: hashes differ (partitioning or formatting)")
    } else {
      diffs.foreach { d =>
        println(s"DIFF $d")
        d match {
          case "nodes" =>
            println(s"  A: ${nodes(a).mkString(", ")}")
            println(s"  B: ${nodes(b).mkString(", ")}")
          case "routers" =>
            println(s"  A: ${routers(a).mkString(", ")}")
            println(s"  B: ${routers(b).mkString(", ")}")
          case _ =>
            println(s"  A: ${links(a).mkString(", ")}")
            println(s"  B: ${links(b).mkString(", ")}")
        }
      }
      sys.exit(4)
    }
  }

  /** GUI plus static preview of emitted artifacts. The browser only edits IR;
    * gates and estimates come from the same `LoomApi` the CLI uses.
    */
  private def runServe(xs: List[String]): Unit = {
    import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
    import java.net.InetSocketAddress
    import java.nio.charset.StandardCharsets

    val dir = Paths.get(must(xs, "--dir")).toAbsolutePath.normalize()
    val port = opt(xs, "--port").map(_.toInt).getOrElse(8000)
    val examples = Paths.get(opt(xs, "--examples").getOrElse("src/loom/examples"))
      .toAbsolutePath.normalize()
    require(Files.isDirectory(dir), s"not a directory: $dir")

    val draftName = "loom_draft.json"
    val stateFile = Paths.get(System.getProperty("user.home"), ".loom", "serve_workspace")
    def rememberedWorkspace: Option[Path] =
      if (Files.isRegularFile(stateFile)) {
        val p = new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8).trim
        if (p.nonEmpty) Some(Paths.get(p).toAbsolutePath.normalize()) else None
      } else None
    def rememberWorkspace(p: Path): Unit = {
      Files.createDirectories(stateFile.getParent)
      Files.write(stateFile, p.toString.getBytes(StandardCharsets.UTF_8))
    }
    val workspace = new java.util.concurrent.atomic.AtomicReference[Option[Path]](
      opt(xs, "--work").map(p => Paths.get(p).toAbsolutePath.normalize()).orElse(rememberedWorkspace))
    workspace.get().foreach { p =>
      require(!Files.exists(p) || Files.isDirectory(p), s"workspace is not a directory: $p")
      Files.createDirectories(p)
    }

    val assets = Set("index.html", "app.js", "model.mjs", "api.mjs", "style.css")

    def asset(name: String): Option[Array[Byte]] = {
      val live = Paths.get("src/loom/gui", name)
      if (Files.isRegularFile(live)) Some(Files.readAllBytes(live))
      else Option(getClass.getResourceAsStream("/" + name)).map { in =>
        try in.readAllBytes() finally in.close()
      }
    }

    def json(ex: HttpExchange, code: Int, body: String): Unit =
      send(ex, code, "application/json", body.getBytes(StandardCharsets.UTF_8))

    def quoteJson(s: String): String =
      "\"" + s.flatMap {
        case '"' => "\\\""
        case '\\' => "\\\\"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      } + "\""

    def contentType(name: String): String = name.split('.').lastOption.getOrElse("") match {
      case "html" => "text/html; charset=utf-8"
      case "json" => "application/json"
      case "js" | "mjs" => "text/javascript; charset=utf-8"
      case "css" => "text/css; charset=utf-8"
      case "md" => "text/markdown; charset=utf-8"
      case "sv" | "f" | "tcl" => "text/plain; charset=utf-8"
      case _ => "application/octet-stream"
    }

    def send(ex: HttpExchange, code: Int, ct: String, body: Array[Byte]): Unit = {
      ex.getResponseHeaders.add("Content-Type", ct)
      ex.sendResponseHeaders(code, body.length)
      val os = ex.getResponseBody
      os.write(body)
      os.close()
    }

    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", new HttpHandler {
      def handle(ex: HttpExchange): Unit = {
        val path = ex.getRequestURI.getPath
        val rel = path.stripPrefix("/")
        val method = ex.getRequestMethod

        if (method == "POST" && path == "/api/evaluate") {
          val body = new String(ex.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          json(ex, 200, LoomApi.evaluateText(body))
        } else if (path == "/api/workspace") {
          if (method == "POST") {
            val raw = new String(ex.getRequestBody.readAllBytes(), StandardCharsets.UTF_8).trim
            val p = Paths.get(raw).toAbsolutePath.normalize()
            if (raw.isEmpty || !p.isAbsolute || p.getParent == null) {
              json(ex, 400, "{\"error\":\"workspace must be an absolute directory path\"}\n")
            } else if (Files.exists(p) && !Files.isDirectory(p)) {
              json(ex, 400, "{\"error\":\"path exists and is not a directory\"}\n")
            } else {
              Files.createDirectories(p)
              workspace.set(Some(p))
              rememberWorkspace(p)
              val hasDraft = Files.isRegularFile(p.resolve(draftName))
              json(ex, 200, s"{\"workspace\":${quoteJson(p.toString)},\"draft\":$hasDraft}\n")
            }
          } else {
            val ws = workspace.get()
            val hasDraft = ws.exists(p => Files.isRegularFile(p.resolve(draftName)))
            val body = ws match {
              case Some(p) => s"{\"workspace\":${quoteJson(p.toString)},\"draft\":$hasDraft}\n"
              case None => "{\"workspace\":null,\"draft\":false}\n"
            }
            json(ex, 200, body)
          }
        } else if (path == "/api/draft") {
          workspace.get() match {
            case None =>
              json(ex, 409, "{\"error\":\"workspace not set\"}\n")
            case Some(p) =>
              val draft = p.resolve(draftName)
              if (method == "POST") {
                val body = ex.getRequestBody.readAllBytes()
                val tmp = p.resolve(draftName + ".tmp")
                Files.write(tmp, body)
                Files.move(tmp, draft,
                  java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                  java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                json(ex, 200, "{\"saved\":true}\n")
              } else if (Files.isRegularFile(draft)) {
                send(ex, 200, "application/json", Files.readAllBytes(draft))
              } else {
                json(ex, 404, "{\"error\":\"no draft\"}\n")
              }
          }
        } else if (path == "/api/examples") {
          import scala.jdk.CollectionConverters._
          val names =
            if (!Files.isDirectory(examples)) Seq.empty
            else Files.list(examples).iterator().asScala
              .filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".json"))
              .map(_.getFileName.toString).toSeq.sorted
          json(ex, 200, names.map(n => "\"" + n + "\"").mkString("[", ",", "]") + "\n")
        } else if (path == "/api/example") {
          val name = Option(ex.getRequestURI.getQuery)
            .flatMap(_.split('&').collectFirst { case q if q.startsWith("name=") => q.stripPrefix("name=") })
            .getOrElse("")
          val target = examples.resolve(name).normalize()
          if (!name.matches("[A-Za-z0-9_.-]+\\.json") || !target.startsWith(examples) || !Files.isRegularFile(target)) {
            json(ex, 404, "{\"error\":\"example not found\"}\n")
          } else {
            send(ex, 200, "application/json", Files.readAllBytes(target))
          }
        } else if (path == "/" || assets.contains(rel)) {
          val name = if (rel == "/" || rel.isEmpty) "index.html" else rel
          asset(name) match {
            case Some(bytes) => send(ex, 200, contentType(name), bytes)
            case None => send(ex, 404, "text/plain", s"missing asset $name".getBytes(StandardCharsets.UTF_8))
          }
        } else {
          val target = dir.resolve(rel).normalize()
          if (!target.startsWith(dir)) {
            send(ex, 403, "text/plain", "forbidden".getBytes(StandardCharsets.UTF_8))
          } else if (Files.isDirectory(target)) {
            val entries = filesUnder(target, _ => false).map(p => dir.relativize(p).toString)
            send(ex, 200, "text/plain", (rel + "\n" + entries.mkString("\n") + "\n").getBytes(StandardCharsets.UTF_8))
          } else if (Files.isRegularFile(target)) {
            send(ex, 200, contentType(target.getFileName.toString), Files.readAllBytes(target))
          } else {
            send(ex, 404, "text/plain", s"not found: $rel".getBytes(StandardCharsets.UTF_8))
          }
        }
      }
    })
    server.start()
    val wsNote = workspace.get().map(p => s", workspace $p").getOrElse("")
    println(s"serving $dir at http://localhost:$port/ (Ctrl-C to stop)$wsNote")
    new java.util.concurrent.CountDownLatch(1).await()
  }

  /** MIG-07: emit-chi is a deprecated alias of emit, kept for one version.
    * The warning string is fixed (tests assert it verbatim).
    */
  private[loom] def runEmitChi(xs: List[String]): Unit = {
    println("WARN: LM-W-DEPRECATED: command 'emit-chi' is deprecated; use 'emit' (alias removed next version)")
    runEmit(xs)
  }

  // MIG-11/GEN-12 (S6): the `blocks` command (legacy CDC/width building-block
  // emission) is retired together with the four-channel fabric. The CDC
  // primitives remain compiled in loom/rtl/LmCdc.scala for ChiAsyncLink; the
  // width adapter lives in src/loom/legacy/ for one version.
}
