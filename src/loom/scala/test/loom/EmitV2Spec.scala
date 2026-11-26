package loom

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters._

import loom.ir.{JBool, JNum, JObj, JStr, Json, Upgrade}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** S4 single-path CLI emission (GEN-01/02/07/08/09/12, MIG-07/11/13).
  * Calls the internal command helpers directly so a successful run never
  * touches sys.exit; stdout is captured through Console.
  */
class EmitV2Spec extends AnyFlatSpec with Matchers {
  private val v2Cfg = "src/loom/examples/ring_4.json"

  private def capture(f: => Unit): String = {
    val buf = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(buf, true, "UTF-8"))(f)
    buf.toString("UTF-8")
  }

  private def filesRec(dir: Path): Map[String, Seq[Byte]] =
    Files.walk(dir).iterator().asScala
      .filter(Files.isRegularFile(_))
      .map(p => dir.relativize(p).toString.replace('\\', '/') -> Files.readAllBytes(p).toSeq)
      .toMap

  behavior of "loom emit (v2 single CHI path, GEN-02)"

  it should "emit ChiNetworkTop SystemVerilog for ring_4 (v2) with the v2 report set" in {
    val out = Files.createTempDirectory("loom-emitv2-ring")
    val log = capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", out.toString)))
    val top = out.resolve("ChiNetworkTop.sv")
    Files.exists(top) shouldBe true
    Files.readString(top) should include("module ChiNetworkTop")
    Seq("checks.json", "estimate.json", "graph.json", "chi_nodes.json",
      "manifest.json", "signoff.md").foreach(f => Files.exists(out.resolve(f)) shouldBe true)
    log should include("emitted ring_4 (2 RN x 2 SN)")
    // GEN-09/GEN-20: manifest carries irSchema/chi and ToolVersion 2.0.0.
    val manifest = Json.parse(Files.readString(out.resolve("manifest.json"))).asInstanceOf[JObj]
    manifest.get("irSchema") shouldBe Some(JStr("loom/v2"))
    manifest.get("version") shouldBe Some(JStr("2.0.0"))
    manifest.get("chi").isDefined shouldBe true
    // GEN-06: configHash is the normalized v2 hash (chi block included).
    val v2 = loom.ir.IrV2.load(Paths.get(v2Cfg))
    manifest.get("configHash") shouldBe Some(JStr(loom.ir.IrV2.hash(v2)))
  }

  it should "be byte-identical across two emissions of the same config (CG2, GEN-07)" in {
    val d1 = Files.createTempDirectory("loom-emitv2-cg2-a")
    val d2 = Files.createTempDirectory("loom-emitv2-cg2-b")
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", d1.toString)))
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", d2.toString)))
    val a = filesRec(d1)
    val b = filesRec(d2)
    a should not be empty
    a.keySet shouldBe b.keySet
    a.foreach { case (rel, bytes) =>
      withClue(s"file $rel: ") { bytes shouldBe b(rel) }
    }
  }

  it should "auto-upgrade a loom/v1 config and emit successfully (MIG-01/07)" in {
    val out = Files.createTempDirectory("loom-emitv2-v1")
    val log = capture(LoomMain.runEmit(List(
      "--config", "src/loom/testdata/v1/ring_4.json", "--out", out.toString)))
    Files.exists(out.resolve("ChiNetworkTop.sv")) shouldBe true
    log should include(s"WARN: ${Upgrade.SchemaWarning}")
    val checks = Json.parse(Files.readString(out.resolve("checks.json"))).asInstanceOf[JObj]
    checks.get("ok") shouldBe Some(JBool(true))
  }

  it should "emit wrapper, partition tops and stubs for a partitioned config (HW-06)" in {
    val out = Files.createTempDirectory("loom-emitv2-part")
    val log = capture(LoomMain.runEmit(List(
      "--config", "src/loom/testdata/v1/chain_split.json", "--out", out.toString)))
    Files.exists(out.resolve("tops/ChiWrapperTop.sv")) shouldBe true
    Seq("front", "back").foreach { p =>
      Files.exists(out.resolve(s"partitions/$p/ChiPartitionTop.sv")) shouldBe true
      Files.exists(out.resolve(s"partitions/$p/ports.json")) shouldBe true
      Files.exists(out.resolve(s"partitions/$p/stub/ChiPartitionStub.sv")) shouldBe true
    }
    // sync cuts: the flat single-clock top is emitted alongside the wrapper.
    Files.exists(out.resolve("ChiNetworkTop.sv")) shouldBe true
    log should include("emitted 2 partition top(s), wrapper and stubs")
  }

  it should "skip the flat top and wire async cuts on the wrapper (HW-04)" in {
    val out = Files.createTempDirectory("loom-emitv2-async")
    capture(LoomMain.runEmit(List(
      "--config", "src/loom/testdata/v1/chain_split_async.json", "--out", out.toString)))
    val wrapper = out.resolve("tops/ChiWrapperTop.sv")
    Files.exists(wrapper) shouldBe true
    Files.readString(wrapper) should include("ChiAsyncLink")
    Files.exists(out.resolve("ChiNetworkTop.sv")) shouldBe false
  }

  it should "print the exact deprecation warning for the emit-chi alias (MIG-07)" in {
    val out = Files.createTempDirectory("loom-emitv2-alias")
    val log = capture(LoomMain.runEmitChi(List("--config", v2Cfg, "--out", out.toString)))
    log.linesIterator.toSeq.head shouldBe
      "WARN: LM-W-DEPRECATED: command 'emit-chi' is deprecated; use 'emit' (alias removed next version)"
    Files.exists(out.resolve("ChiNetworkTop.sv")) shouldBe true
  }

  it should "hit the manifest cache on a second --cache emit (GEN-08)" in {
    val out = Files.createTempDirectory("loom-emitv2-cache")
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", out.toString, "--cache")))
    val before = filesRec(out)
    val log = capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", out.toString, "--cache")))
    log should include(s"up to date: $out (hash=")
    filesRec(out) shouldBe before
  }

  behavior of "loom check (single validateV2 path, GEN-12)"

  it should "check an upgraded v1 config and record the deprecation warnings" in {
    val out = Files.createTempDirectory("loom-checkv2")
    val log = capture(LoomMain.runCheck(List(
      "--config", "src/loom/testdata/v1/ring_4.json", "--out", out.toString)))
    log should include("OK name=ring_4")
    log should include(s"WARN: ${Upgrade.SchemaWarning}")
    val checks = Json.parse(Files.readString(out.resolve("checks.json"))).asInstanceOf[JObj]
    checks.get("ok") shouldBe Some(JBool(true))
    Files.exists(out.resolve("graph.json")) shouldBe true
  }

  // HW-08 closed (ir_gen_spec v1.2.0/GEN-22): ChiPartitionTop has MN/DVM
  // parity, so the former guard test is inverted — partitioned configs with
  // rn-d/mn/hn-i now emit.
  it should "emit a partitioned config with an MN node (HW-08 parity, guard lifted)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg))
    val v2 = base.copy(
      chi = base.chi.copy(dvmEnable = true),
      nodes = base.nodes :+ loom.ir.NodeV2("mn0", "mn", None, "core", "r0", "chi", 256, 64, 0),
      partitions = Seq(
        loom.ir.PartitionSpec("p0", "core", Seq("m0", "r0", "mn0")),
        loom.ir.PartitionSpec("p1", "core", Seq("m1", "r1", "r2", "r3", "s0", "s1"))))
    val full = loom.gen.ChiAssemble.networkParams(v2)
    loom.gen.ChiAssemble.partitionParams(v2, full).length shouldBe 2
    val cfgFile = Files.createTempFile("loom-emitv2-mn", ".json")
    Files.writeString(cfgFile, loom.ir.Canonical.pretty(loom.ir.IrV2.toJson(v2)))
    val out = Files.createTempDirectory("loom-emitv2-mn-out")
    val log = capture(LoomMain.runEmit(List("--config", cfgFile.toString, "--out", out.toString)))
    Files.exists(out.resolve("tops/ChiWrapperTop.sv")) shouldBe true
    Seq("p0", "p1").foreach { p =>
      Files.exists(out.resolve(s"partitions/$p/ChiPartitionTop.sv")) shouldBe true
      Files.exists(out.resolve(s"partitions/$p/ports.json")) shouldBe true
    }
    log should include("emitted 2 partition top(s), wrapper and stubs")
  }

  // ---- UP-01 explicit route assembly (GEN-21/SEM-17) ----

  behavior of "ChiAssemble explicit routes (GEN-21/SEM-17, UP-01)"

  private def routeBase: loom.ir.IrV2 = loom.ir.IrV2.load(Paths.get(v2Cfg))

  /** Port of the router-facing link between two routers, from the assembled
    * params themselves (no hardcoded port layout).
    */
  private def portAt(p: loom.chi.ChiNetworkParams, router: String, peer: String): Int = {
    val nameOf = p.routers.map(r => r.index -> r.name).toMap
    val l = p.links.find(l => Set(nameOf(l.fromRouter), nameOf(l.toRouter)) == Set(router, peer)).get
    if (nameOf(l.fromRouter) == router) l.fromPort else l.toPort
  }

  it should "override BFS route tables along an explicit path in both directions" in {
    val base = routeBase
    val cp = loom.gen.ChiAssemble.chiParams(base)
    val m0 = cp.rnNodes.find(_.id == "m0").get.nodeId
    val s0 = cp.snNodes.find(_.id == "s0").get.nodeId
    val s1 = cp.snNodes.find(_.id == "s1").get.nodeId
    val bfs = loom.gen.ChiAssemble.networkParams(base)
    // explicit path takes the long way around the ring: r0 -> r3 -> r2
    val routed = loom.gen.ChiAssemble.networkParams(base.copy(
      routes = Seq(loom.ir.RouteSpec("m0", "s0", Seq("r0", "r3", "r2"), false))))
    def route(p: loom.chi.ChiNetworkParams, router: String, nid: Int): Int =
      p.routers.find(_.name == router).get.route(nid)

    // BFS baseline: r0 reaches s0 via r1 (shortest, tie-break by router index).
    route(bfs, "r0", s0) shouldBe portAt(bfs, "r0", "r1")
    // forward override: r0 now routes s0 via the r3->r0 link.
    route(routed, "r0", s0) shouldBe portAt(routed, "r0", "r3")
    route(routed, "r0", s0) should not be route(bfs, "r0", s0)
    // reverse auto-reversal: r2 routes m0 via r3 (BFS used r1), r3 via r0.
    route(bfs, "r2", m0) shouldBe portAt(bfs, "r2", "r1")
    route(routed, "r2", m0) shouldBe portAt(routed, "r2", "r3")
    route(routed, "r3", m0) shouldBe portAt(routed, "r3", "r0")
    // endpoint local-port entries stay as BFS set them.
    route(routed, "r2", s0) shouldBe route(bfs, "r2", s0)
    route(routed, "r0", m0) shouldBe route(bfs, "r0", m0)
    // untouched routers and targets keep their BFS entries.
    route(routed, "r1", s0) shouldBe route(bfs, "r1", s0)
    route(routed, "r0", s1) shouldBe route(bfs, "r0", s1)
    // forbid never touches the route tables (executed via samByRn since
    // v1.2.0): tables identical to BFS.
    val forbidden = loom.gen.ChiAssemble.networkParams(base.copy(
      routes = Seq(loom.ir.RouteSpec("m0", "s0", Nil, true))))
    forbidden.routers.map(_.route) shouldBe bfs.routers.map(_.route)
  }

  it should "derive the RN x SN connectivity matrix from the final route tables" in {
    val base = routeBase
    val rows = loom.gen.ChiAssemble.connectivity(base, loom.gen.ChiAssemble.networkParams(base))
    rows.map(r => (r.from, r.to)) shouldBe
      Seq(("m0", "s0"), ("m0", "s1"), ("m1", "s0"), ("m1", "s1"))
    rows.forall(_.reachable) shouldBe true
    rows.find(r => r.from == "m0" && r.to == "s0").get.path shouldBe Seq("r0", "r1", "r2")
    rows.find(r => r.from == "m0" && r.to == "s1").get.path shouldBe Seq("r0", "r3")

    // explicit override reroutes the walk along the declared path
    val routed = base.copy(routes = Seq(loom.ir.RouteSpec("m0", "s0", Seq("r0", "r3", "r2"), false)))
    val rows2 = loom.gen.ChiAssemble.connectivity(routed, loom.gen.ChiAssemble.networkParams(routed))
    rows2.find(r => r.from == "m0" && r.to == "s0").get.path shouldBe Seq("r0", "r3", "r2")
    rows2.forall(_.reachable) shouldBe true

    // disconnected topology: unreachable with an empty path, no throw/loop
    val disc = base.copy(links = base.links.filterNot(l =>
      (l.from == "r1" && l.to == "r2") || (l.from == "r3" && l.to == "r0")))
    val rows3 = loom.gen.ChiAssemble.connectivity(disc, loom.gen.ChiAssemble.networkParams(disc))
    val m0s0 = rows3.find(r => r.from == "m0" && r.to == "s0").get
    m0s0.reachable shouldBe false
    m0s0.path shouldBe Nil
    rows3.find(r => r.from == "m1" && r.to == "s1").get.reachable shouldBe false
    rows3.find(r => r.from == "m0" && r.to == "s1").get.reachable shouldBe false
  }

  // ---- S7: hardware waves 1-3 consumption (GEN-21/GEN-22, v1.2.0) ----

  behavior of "loom emit GEN-22 (protocol dispatch, per-source SAM, nHalf)"

  private def writeCfg(v2: loom.ir.IrV2): String = {
    val f = Files.createTempFile("loom-emitv2-gen22", ".json")
    Files.writeString(f, loom.ir.Canonical.pretty(loom.ir.IrV2.toJson(v2)))
    f.toString
  }

  private def bridgesOf(out: Path): (BigDecimal, BigDecimal) = {
    val est = Json.parse(Files.readString(out.resolve("estimate.json"))).asInstanceOf[JObj]
    val b = est.get("bridges").get.asInstanceOf[JObj]
    (b.get("rnAxi").collect { case JNum(n) => n }.get,
      b.get("snAxi").collect { case JNum(n) => n }.get)
  }

  it should "dispatch to ChiAxiNetworkTop for an all-axi4 ring (GEN-22)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg))
    val v2 = base.copy(
      chi = base.chi.copy(portMode = "axi4"),
      nodes = base.nodes.map(_.copy(protocol = "axi4")))
    val out = Files.createTempDirectory("loom-emitv2-allaxi")
    val log = capture(LoomMain.runEmit(List("--config", writeCfg(v2), "--out", out.toString)))
    val top = out.resolve("ChiAxiNetworkTop.sv")
    Files.exists(top) shouldBe true
    Files.readString(top) should include("module ChiAxiNetworkTop")
    // child modules emit as separate files; the dispatch is proven by the
    // tb skeleton targeting the selected top (GEN-17 tb row)
    Files.exists(out.resolve("tb/ChiAxiNetworkTop_tb.sv")) shouldBe true
    Files.exists(out.resolve("tb/ChiNetworkTop_tb.sv")) shouldBe false
    log should include("emitted ring_4 (2 RN x 2 SN)")
    bridgesOf(out) shouldBe ((BigDecimal(2), BigDecimal(2)))
    val est = Files.readString(out.resolve("estimate.json"))
    est should include("no PPA claim")
  }

  it should "split active ports per node protocol on a mixed chi/axi4 ring (GEN-22)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg))
    val v2 = base.copy(
      chi = base.chi.copy(portMode = "both"),
      nodes = base.nodes.map(n => if (n.id == "m0") n.copy(protocol = "axi4") else n))
    val net = loom.gen.ChiAssemble.networkParams(v2)
    net.rn.count(_.protocol == "axi4") shouldBe 1
    net.rn.count(_.protocol == "chi") shouldBe 1
    net.sn.count(_.protocol == "axi4") shouldBe 0
    net.sn.count(_.protocol == "chi") shouldBe 2
    val out = Files.createTempDirectory("loom-emitv2-mixed")
    capture(LoomMain.runEmit(List("--config", writeCfg(v2), "--out", out.toString)))
    val sv = Files.readString(out.resolve("ChiAxiNetworkTop.sv"))
    // one axi4 RN port (m0), one chi RN port (m1), two chi SN ports, no SN bridge
    sv should include("io_rnAxi_0_ar_valid")
    sv should include("io_rnChi_0_req_valid")
    sv should not include "io_rnAxi_1"
    sv should not include "io_rnChi_1"
    sv should include("io_snChi_1_req_ready")
    sv should not include "io_snAxi"
    bridgesOf(out) shouldBe ((BigDecimal(1), BigDecimal(0)))
  }

  it should "assemble samByRn per source for forbid pairs and keep the global sam (GEN-21/SEM-17)" in {
    val base = routeBase
    val cp = loom.gen.ChiAssemble.chiParams(base)
    val m0Id = cp.rnNodes.find(_.id == "m0").get.nodeId
    val s0Id = cp.snNodes.find(_.id == "s0").get.nodeId
    val plain = loom.gen.ChiAssemble.networkParams(base)
    plain.sam shouldBe cp.sam // global window table always assembled (SEM-05)
    plain.samByRn shouldBe empty
    // reachable forbid on the connected ring is legal since v1.2.0
    val forbidden = loom.gen.ChiAssemble.networkParams(base.copy(
      routes = Seq(loom.ir.RouteSpec("m0", "s0", Nil, true))))
    forbidden.sam shouldBe plain.sam
    forbidden.samByRn.length shouldBe plain.nRn
    val tables = forbidden.rn.zip(forbidden.samByRn).toMap
    tables(forbidden.rn.find(_.nodeId == m0Id).get) shouldBe
      plain.sam.filterNot(_.tgtId == s0Id)
    tables(forbidden.rn.find(_.nodeId == m0Id).get).exists(_.tgtId == s0Id) shouldBe false
    // m1 has no forbids: full global table
    tables(forbidden.rn.find(_.nodeId != m0Id).get) shouldBe plain.sam
  }

  it should "derive nHalf from the topology: ring dateline 2, chain 1 (AQ-02/GEN-22)" in {
    val ring = loom.ir.IrV2.load(Paths.get(v2Cfg))
    loom.gen.ChiAssemble.config(ring).nHalf shouldBe 2
    loom.gen.ChiAssemble.config(ring).vcWidth shouldBe 3
    val chain = loom.ir.IrV2.load(Paths.get("src/loom/examples/chain_4r.json"))
    loom.gen.ChiAssemble.config(chain).nHalf shouldBe 1
    loom.gen.ChiAssemble.config(chain).vcWidth shouldBe 2
  }

  // ---- S8: qosGen/adapt parameter assembly (GEN-23/GEN-24, UP-02/UP-08) ----

  behavior of "ChiAssemble qosGen/adapt params (GEN-23/GEN-24, S8)"

  it should "convert qosGen bandwidth to bytesPerCycle at the node clock (SEM-18 golden)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg)) // core = 1000 MHz
    loom.gen.ChiAssemble.qosGenParams(base) shouldBe Nil // nothing declared
    val v = base.copy(nodes = base.nodes.map { n =>
      if (n.id == "m0") n.copy(qosGen = Some(loom.ir.QosGenSpec("limiter", 1000, 4096)))
      else if (n.id == "m1") n.copy(qosGen = Some(loom.ir.QosGenSpec("fixed", 500, 0)))
      else n
    })
    // 1000 MB/s / 1000 MHz = 1.0 B/cycle; 500 MB/s / 1000 MHz = 0.5 B/cycle
    loom.gen.ChiAssemble.qosGenParams(v) shouldBe Seq(
      loom.gen.ChiAssemble.QosGenParams(0, "limiter", 1.0, 4096),
      loom.gen.ChiAssemble.QosGenParams(1, "fixed", 0.5, 0))
    // a slower node clock scales the same declaration: 1000 MB/s / 500 MHz = 2.0
    val slow = v.copy(
      clockDomains = v.clockDomains :+ loom.ir.ClockDomain("slow", 500),
      nodes = v.nodes.map(n => if (n.id == "m0") n.copy(clock = "slow") else n))
    loom.gen.ChiAssemble.qosGenParams(slow).head.bytesPerCycle shouldBe 2.0
  }

  it should "detect width mismatch and freeze the default FIFO depth 4 (SEM-19)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg)) // node dataWidth 128, chi.datWidth 256
    val rows = loom.gen.ChiAssemble.adaptParams(base)
    rows.length shouldBe 4
    rows.foreach { p =>
      p.mismatch shouldBe true
      p.dataWidth shouldBe 128
      p.datWidth shouldBe 256
      p.fifoDepth shouldBe 4 // IR-79 default lives in assembly, not the IR
      p.clock shouldBe "core"
      p.routerClock shouldBe "core"
    }
    // explicit declaration overrides the frozen depth
    val declared = base.copy(nodes = base.nodes.map(n =>
      if (n.id == "m0") n.copy(adaptFifoDepth = Some(8)) else n))
    loom.gen.ChiAssemble.adaptParams(declared).find(_.nodeId == 0).get.fifoDepth shouldBe 8
  }

  it should "detect clock mismatch against the attach router and stay empty when matched (SEM-19)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg))
    val matched = base.copy(nodes = base.nodes.map(_.copy(dataWidth = 256)))
    loom.gen.ChiAssemble.adaptParams(matched) shouldBe Nil // no mismatch, no declaration
    // frequency mismatch only: m0 runs on slow while its attach router r0 stays core
    val clockMismatch = matched.copy(
      clockDomains = matched.clockDomains :+ loom.ir.ClockDomain("slow", 500),
      nodes = matched.nodes.map(n => if (n.id == "m0") n.copy(clock = "slow") else n))
    val rows = loom.gen.ChiAssemble.adaptParams(clockMismatch)
    rows.length shouldBe 1
    rows.head.nodeId shouldBe 0
    rows.head.mismatch shouldBe true
    rows.head.clock shouldBe "slow"
    rows.head.routerClock shouldBe "core"
    rows.head.dataWidth shouldBe rows.head.datWidth
    // a matched node with an explicit depth freeze is still reported (mismatch=false)
    val frozen = matched.copy(nodes = matched.nodes.map(n =>
      if (n.id == "m1") n.copy(adaptFifoDepth = Some(16)) else n))
    val frozenRows = loom.gen.ChiAssemble.adaptParams(frozen)
    frozenRows.length shouldBe 1
    frozenRows.head.nodeId shouldBe 1
    frozenRows.head.mismatch shouldBe false
    frozenRows.head.fifoDepth shouldBe 16
  }

  it should "carry qosGen/adapt estimate rows with consumed HW-10/HW-12 assumptions (GEN-23/24)" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg))
    val v = base.copy(nodes = base.nodes.map(n =>
      if (n.id == "m0") n.copy(qosGen = Some(loom.ir.QosGenSpec("limiter", 1000, 4096)),
        adaptFifoDepth = Some(8))
      else n))
    val out = Files.createTempDirectory("loom-emitv2-s8")
    capture(LoomMain.runEmit(List("--config", writeCfg(v), "--out", out.toString)))
    val est = Json.parse(Files.readString(out.resolve("estimate.json"))).asInstanceOf[JObj]
    val qos = est.get("qosGen").get.asInstanceOf[loom.ir.JArr].items
    qos.length shouldBe 1
    val q0 = qos.head.asInstanceOf[JObj]
    q0.get("node") shouldBe Some(JStr("m0"))
    q0.get("kind") shouldBe Some(JStr("limiter"))
    q0.get("bandwidth") shouldBe Some(JNum(BigDecimal(1000)))
    q0.get("saturation") shouldBe Some(JNum(BigDecimal(4096)))
    val adapt = est.get("adapt").get.asInstanceOf[loom.ir.JArr].items.map(_.asInstanceOf[JObj])
    adapt.length shouldBe 4 // ring_4 nodes are 128-bit on a 256-bit DAT fabric
    val m0 = adapt.find(_.get("node").contains(JStr("m0"))).get
    m0.get("fifoDepth") shouldBe Some(JNum(BigDecimal(8)))
    m0.get("mismatch") shouldBe Some(JBool(true))
    val assumptions = est.get("assumptions").get.asInstanceOf[loom.ir.JArr].items
      .collect { case JStr(s) => s }
    assumptions.exists(s => s.contains("HW-10") && s.contains("RN-injection")) shouldBe true
    assumptions.exists(s => s.contains("HW-12") && s.contains("ChiNiAdapt")) shouldBe true
    // baseline without declarations: qosGen empty, existing keys untouched
    val plainOut = Files.createTempDirectory("loom-emitv2-s8-plain")
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", plainOut.toString)))
    val plain = Json.parse(Files.readString(plainOut.resolve("estimate.json"))).asInstanceOf[JObj]
    plain.get("qosGen") shouldBe Some(loom.ir.JArr(Vector.empty))
    plain.get("bridges").isDefined shouldBe true
    plain.get("totals").isDefined shouldBe true
  }

  // ---- S9: hardware wave4 consumption (SEM-14/18/19, GEN-23/24, CHK-54) ----

  behavior of "loom emit wave4 (HW-14 flat async, HW-10 qosGen, HW-12 adapt wrapper)"

  /** Same fixture as CheckV2Spec: chain_4r with r1/r2 on a second domain and
    * async boundary links; nodes stay on domain-0 routers r0/r3.
    */
  private def dualClockChain: loom.ir.IrV2 = {
    val v = loom.ir.IrV2.load(Paths.get("src/loom/examples/chain_4r.json"))
    v.copy(
      clockDomains = v.clockDomains :+ loom.ir.ClockDomain("io", 500),
      routers = v.routers.map(r =>
        if (r.name == "r1" || r.name == "r2") r.copy(clock = "io") else r),
      links = v.links.map(l =>
        if ((l.from == "r0" && l.to == "r1") || (l.from == "r2" && l.to == "r3"))
          l.copy(cdc = "async", asyncFifoDepth = 4) else l))
  }

  it should "assemble routerDomain by clockDomains declaration order and async depths (SEM-14)" in {
    val net = loom.gen.ChiAssemble.networkParams(dualClockChain)
    // GEN-05 name-sorted routers r0,r1,r2,r3; core=0 (head), io=1
    net.routerDomain shouldBe Seq(0, 1, 1, 0)
    net.nDomain shouldBe 2
    net.hasAsync shouldBe true
    val nameOf = net.routers.map(r => r.index -> r.name).toMap
    val byPair = net.links.map(l => (nameOf(l.fromRouter), nameOf(l.toRouter)) -> l).toMap
    byPair(("r0", "r1")).asyncFifoDepth shouldBe 4
    byPair(("r2", "r3")).asyncFifoDepth shouldBe 4
    byPair(("r1", "r2")).asyncFifoDepth shouldBe 0 // sync inside domain io
    // single-clock baseline: all routers on domain 0, no async
    val plain = loom.gen.ChiAssemble.networkParams(routeBase)
    plain.routerDomain shouldBe Seq(0, 0, 0, 0)
    plain.links.foreach(_.asyncFifoDepth shouldBe 0)
  }

  it should "emit a flat dual-clock ChiNetworkTop with domClk inputs and async links (HW-14)" in {
    val v0 = dualClockChain
    val v = v0.copy(nodes = v0.nodes.map(_.copy(dataWidth = 256))) // matched: no wrapper
    val out = Files.createTempDirectory("loom-emitv2-flatasync")
    val log = capture(LoomMain.runEmit(List("--config", writeCfg(v), "--out", out.toString)))
    log should not include "ERROR"
    val top = out.resolve("ChiNetworkTop.sv")
    Files.exists(top) shouldBe true
    val sv = Files.readString(top)
    sv should include("io_domClk_0")
    sv should include("io_domReset_0")
    sv should include("ChiAsyncLink")
    Files.exists(out.resolve("tb/ChiNetworkTop_tb.sv")) shouldBe true
    // estimate carries the integrator-supplied clocking note (nDomain > 1)
    val est = Json.parse(Files.readString(out.resolve("estimate.json"))).asInstanceOf[JObj]
    val assumptions = est.get("assumptions").get.asInstanceOf[loom.ir.JArr].items
      .collect { case JStr(s) => s }
    assumptions.exists(s => s.contains("clocking = 2 router clock domains") &&
      s.contains("domClk/domReset")) shouldBe true
  }

  it should "map qosGen declarations into ChiNetworkParams.qosGen (SEM-18/HW-10)" in {
    val base = routeBase
    loom.gen.ChiAssemble.networkParams(base).qosGen shouldBe Nil
    val v = base.copy(nodes = base.nodes.map(n =>
      if (n.id == "m0") n.copy(qosGen = Some(loom.ir.QosGenSpec("limiter", 1000, 4096))) else n))
    loom.gen.ChiAssemble.networkParams(v).qosGen shouldBe
      Seq(loom.chi.ChiQosGen(0, "limiter", 1.0, 4096))
  }

  it should "map adapt rows into ChiNetworkParams.adapt and select the GEN wrapper (SEM-19/HW-12)" in {
    val net = loom.gen.ChiAssemble.networkParams(routeBase) // 128-bit nodes, 256-bit fabric
    net.adapt.length shouldBe 4
    net.adapt.foreach { a =>
      a.dataWidth shouldBe 128
      a.datWidth shouldBe 256
      a.fifoDepth shouldBe 4
      a.mismatch shouldBe true
    }
    loom.gen.ChiAdaptedNetworkTop.needed(net) shouldBe true
    // matched fabric: no adaptation, native top stays
    val matched = routeBase.copy(nodes = routeBase.nodes.map(_.copy(dataWidth = 256)))
    loom.gen.ChiAdaptedNetworkTop.needed(loom.gen.ChiAssemble.networkParams(matched)) shouldBe false
    // axi4 nodes keep bridge-layer adaptation: no double adapt
    val allAxi = routeBase.copy(
      chi = routeBase.chi.copy(portMode = "axi4"),
      nodes = routeBase.nodes.map(_.copy(protocol = "axi4")))
    loom.gen.ChiAdaptedNetworkTop.needed(loom.gen.ChiAssemble.networkParams(allAxi)) shouldBe false
  }

  it should "emit ChiAdaptedNetworkTop with ChiNiAdapt for width-mismatched chi nodes (HW-12 §9.3)" in {
    val out = Files.createTempDirectory("loom-emitv2-adapt")
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", out.toString)))
    val top = out.resolve("ChiAdaptedNetworkTop.sv")
    Files.exists(top) shouldBe true
    val sv = Files.readString(top)
    sv should include("module ChiAdaptedNetworkTop")
    sv should include("ChiNiAdapt")
    // inner network still emitted as a child file; tb targets the wrapper
    Files.exists(out.resolve("ChiNetworkTop.sv")) shouldBe true
    Files.exists(out.resolve("tb/ChiAdaptedNetworkTop_tb.sv")) shouldBe true
    Files.exists(out.resolve("tb/ChiNetworkTop_tb.sv")) shouldBe false
  }

  it should "forward domClk through the adaptation wrapper on a dual-clock chain (HW-14 + HW-12)" in {
    val out = Files.createTempDirectory("loom-emitv2-adapt-async")
    capture(LoomMain.runEmit(List("--config", writeCfg(dualClockChain), "--out", out.toString)))
    val top = out.resolve("ChiAdaptedNetworkTop.sv")
    Files.exists(top) shouldBe true
    val sv = Files.readString(top)
    sv should include("io_domClk_0")
    sv should include("ChiNiAdapt")
    Files.readString(out.resolve("ChiNetworkTop.sv")) should include("ChiAsyncLink")
  }

  // ---- UP-07 GEN-25: subsystem/bridge assembly + hierarchical emit guard ----

  behavior of "loom hierarchical multi-NoC (GEN-25, UP-07)"

  private def subChain(name: String, rn: String, sn: String, r0: String, r1: String,
      samBase: Long): loom.ir.SubsystemSpec = {
    val v = loom.ir.IrV2.load(Paths.get(v2Cfg))
    loom.ir.SubsystemSpec(name, v.clockDomains,
      Seq(loom.ir.NodeV2(rn, "rn-i", None, "core", r0, "chi", 256, 64, 0),
        loom.ir.NodeV2(sn, "sn-f", None, "core", r1, "chi", 256, 64, 0)),
      Seq(loom.ir.SamWindowV2(samBase, 4096L, sn)),
      v.qos, v.chi,
      Seq(loom.ir.RouterV2(r0, "core", None, None, None, "any"),
        loom.ir.RouterV2(r1, "core", None, None, None, "any")),
      Seq(loom.ir.LinkV2(rn, r0, 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4),
        loom.ir.LinkV2(r0, r1, 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4),
        loom.ir.LinkV2(r1, sn, 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4)),
      v.timing)
  }

  private def hierCfg: loom.ir.IrV2 = {
    val v = loom.ir.IrV2.load(Paths.get(v2Cfg))
    v.copy(meta = v.meta.copy(name = "hier_ok"),
      nodes = Nil, sam = Nil, routers = Nil, links = Nil,
      subsystems = Seq(subChain("A", "m0", "s0", "rA0", "rA1", 0L),
        subChain("B", "m1", "s1", "rB0", "rB1", 65536L)),
      nocLinks = Seq(loom.ir.NocLinkSpec("A", "B", "rA0", "rB1",
        Seq((65536L, 4096L)), "sync", 0, false)))
  }

  it should "guard hierarchical emission with the HW-11 error; flat still emits" in {
    // Guard is a pure function (sys.exit cannot be trapped on modern JDKs).
    LoomMain.hierarchicalEmitGuard(hierCfg) shouldBe Some(
      "ERROR: hierarchical emission requires HW-11 (ChiNocBridge); check/estimate available")
    LoomMain.hierarchicalEmitGuard(loom.ir.IrV2.load(Paths.get(v2Cfg))) shouldBe None
    // flat configs keep emitting through the same command
    val flatOut = Files.createTempDirectory("loom-emitv2-hier-flat")
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", flatOut.toString)))
    Files.exists(flatOut.resolve("ChiNetworkTop.sv")) shouldBe true
  }

  it should "assemble subsystemParams projections and BridgeParams for HW-11 (GEN-25)" in {
    val v = hierCfg
    val ps = loom.gen.ChiAssemble.subsystemParams(v)
    ps.map(_._1) shouldBe Seq("A", "B")
    ps.foreach { case (name, net) =>
      net.name shouldBe name
      net.nRn shouldBe 1
      net.nSn shouldBe 1
      net.routers.length shouldBe 2
    }
    ps.head._2.sam.map(e => (e.base, e.size)) shouldBe Seq((0L, 4096L))
    ps(1)._2.sam.map(e => (e.base, e.size)) shouldBe Seq((65536L, 4096L))
    loom.gen.ChiAssemble.bridgeParams(v) shouldBe Seq(
      loom.gen.ChiAssemble.BridgeParams("A", "B", "rA0", "rB1",
        Seq((65536L, 4096L)), 0, false, 256, 256))
    // flat configs assemble nothing hierarchical
    loom.gen.ChiAssemble.subsystemParams(loom.ir.IrV2.load(Paths.get(v2Cfg))) shouldBe Nil
    loom.gen.ChiAssemble.bridgeParams(loom.ir.IrV2.load(Paths.get(v2Cfg))) shouldBe Nil
  }

  it should "check and estimate a hierarchical config with additive GEN-25 keys" in {
    val cfgFile = Files.createTempFile("loom-emitv2-hier-chk", ".json")
    Files.writeString(cfgFile, loom.ir.Canonical.pretty(loom.ir.IrV2.toJson(hierCfg)))
    val out = Files.createTempDirectory("loom-emitv2-hier-chk-out")
    val log = capture(LoomMain.runCheck(List("--config", cfgFile.toString, "--out", out.toString)))
    log should include("OK name=hier_ok")
    val checks = Json.parse(Files.readString(out.resolve("checks.json"))).asInstanceOf[JObj]
    checks.get("ok") shouldBe Some(JBool(true))
    val bySub = checks.get("gatesBySubsystem").get.asInstanceOf[JObj]
    bySub.get("A").get.asInstanceOf[JObj].get("G1-REQ") shouldBe Some(JBool(true))
    bySub.get("B").get.asInstanceOf[JObj].get("G1-DAT") shouldBe Some(JBool(true))
    capture(LoomMain.runEstimate(List("--config", cfgFile.toString, "--out", out.toString)))
    val est = Json.parse(Files.readString(out.resolve("estimate.json"))).asInstanceOf[JObj]
    val subs = est.get("subsystems").get.asInstanceOf[loom.ir.JArr].items.map(_.asInstanceOf[JObj])
    subs.length shouldBe 2
    subs.head.get("name") shouldBe Some(JStr("A"))
    subs.head.get("nodes") shouldBe Some(JNum(BigDecimal(2)))
    subs.head.get("routers") shouldBe Some(JNum(BigDecimal(2)))
    subs.head.get("sam") shouldBe Some(JNum(BigDecimal(1)))
    subs.head.get("chi").get.asInstanceOf[JObj].get("datWidth") shouldBe Some(JNum(BigDecimal(256)))
    est.get("nocLinks").get.asInstanceOf[loom.ir.JArr].items.length shouldBe 1
    est.get("bridges").get.asInstanceOf[JObj].get("noc") shouldBe Some(JNum(BigDecimal(1)))
    est.get("gatesBySubsystem").isDefined shouldBe true
    // flat estimate baseline: none of the hierarchical keys appear
    val flatOut = Files.createTempDirectory("loom-emitv2-hier-est-flat")
    capture(LoomMain.runEstimate(List("--config", v2Cfg, "--out", flatOut.toString)))
    val flat = Json.parse(Files.readString(flatOut.resolve("estimate.json"))).asInstanceOf[JObj]
    flat.get("subsystems") shouldBe None
    flat.get("nocLinks") shouldBe None
    flat.get("gatesBySubsystem") shouldBe None
    flat.get("bridges").get.asInstanceOf[JObj].get("noc") shouldBe None
  }
}