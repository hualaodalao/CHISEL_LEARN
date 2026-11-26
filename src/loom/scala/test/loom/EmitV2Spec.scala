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

  it should "carry declaration-only qosGen/adapt estimate rows with HW-10/HW-12 assumptions (GEN-23/24)" in {
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
    assumptions.exists(s => s.contains("HW-10") && s.contains("no RTL effect")) shouldBe true
    assumptions.exists(s => s.contains("HW-12") && s.contains("no RTL effect")) shouldBe true
    // baseline without declarations: qosGen empty, existing keys untouched
    val plainOut = Files.createTempDirectory("loom-emitv2-s8-plain")
    capture(LoomMain.runEmit(List("--config", v2Cfg, "--out", plainOut.toString)))
    val plain = Json.parse(Files.readString(plainOut.resolve("estimate.json"))).asInstanceOf[JObj]
    plain.get("qosGen") shouldBe Some(loom.ir.JArr(Vector.empty))
    plain.get("bridges").isDefined shouldBe true
    plain.get("totals").isDefined shouldBe true
  }
}