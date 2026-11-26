package loom

import java.nio.file.{Files, Paths}

import loom.check.Checks
import loom.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IrSpec extends AnyFlatSpec with Matchers {
  private val examplePath = Paths.get("src/loom/testdata/v1/minimal_2x2.json")
  private def exampleText: String = Files.readString(examplePath)
  private def exampleJson: JValue = Json.parse(exampleText)

  "Canonical" should "ignore key order and whitespace" in {
    val a = Json.parse("""{"b":1,"a":[true,null,"x"]}""")
    val b = Json.parse(""" { "a" : [ true , null , "x" ] , "b" : 1 } """)
    Canonical.render(a) shouldBe Canonical.render(b)
  }

  it should "produce a stable hash" in {
    Canonical.hash(exampleJson) shouldBe Canonical.hash(Json.parse(exampleText))
    Canonical.hash(Json.parse("""{"a":1}""")) should not be Canonical.hash(Json.parse("""{"a":2}"""))
  }

  "Checks" should "accept the example" in {
    Checks.run(exampleJson) shouldBe List.empty
  }

  it should "reject duplicate endpoint ids" in {
    val bad = Json.parse(exampleText.replaceFirst("\"id\"\\s*:\\s*\"m1\"", "\"id\": \"m0\""))
    Checks.run(bad).exists(_.contains("duplicate endpoint id")) shouldBe true
  }

  it should "reject a master attached to a slave network" in {
    val bad = Json.parse(
      exampleText.replaceFirst("\"attach\"\\s*:\\s*\"mnet0\"", "\"attach\": \"snet0\"")
    )
    Checks.run(bad).exists(_.contains("is not a master network")) shouldBe true
  }

  it should "reject an undeclared clock" in {
    val bad = Json.parse(exampleText.replaceFirst("\"clock\"\\s*:\\s*\"core\"", "\"clock\": \"missing\""))
    Checks.run(bad).exists(_.contains("is not declared")) shouldBe true
  }

  "C1 checks" should "accept the example and reject duplicate links" in {
    val ir = Ir.fromJson(exampleJson)
    Checks.runC1(ir) shouldBe List.empty
    val dup = ir.copy(links = ir.links :+ ir.links.head)
    Checks.runC1(dup).exists(_.contains("duplicate link")) shouldBe true
  }

  it should "reject a sync link across clock domains" in {
    val ir = Ir.fromJson(exampleJson)
    val slow = ir.copy(
      clockDomains = ir.clockDomains :+ ClockDomain("slow", 500),
      routers = ir.routers.map(_.copy(clock = "slow"))
    )
    Checks.runC1(slow).exists(_.contains("crosses clock domains")) shouldBe true
  }

  "Address map" should "derive 4 KB windows in slave order when undeclared" in {
    val ir = Ir.fromJson(exampleJson)
    val wins = AddressMap.windows(ir.endpoints.filter(_.role == "slave"))
    wins.map(_.base) shouldBe wins.indices.map(_.toLong * AddressMap.DefaultSize).toSeq
    wins.map(_.size).distinct shouldBe Seq(AddressMap.DefaultSize)
  }

  it should "accept explicit non-overlapping windows and reject overlap" in {
    val ir = Ir.fromJson(exampleJson)
    val mapped = ir.copy(endpoints = ir.endpoints.map { e =>
      if (e.role != "slave") e
      else {
        val i = ir.endpoints.filter(x => x.role == "slave").indexOf(e)
        e.copy(base = 0x8000L + i * 0x4000L, size = 0x4000L)
      }
    })
    Checks.runIr(mapped) shouldBe List.empty
    Checks.runC1(mapped) shouldBe List.empty
    val overlapped = mapped.copy(endpoints = mapped.endpoints.map { e =>
      if (e.id == "s1") e.copy(base = 0x8000L) else e
    })
    Checks.runC1(overlapped).exists(_.contains("overlap")) shouldBe true
  }

  it should "reject mixed declarations and misaligned windows" in {
    val ir = Ir.fromJson(exampleJson)
    val mixed = ir.copy(endpoints = ir.endpoints.map { e =>
      if (e.id == "s0") e.copy(base = 0x10000L, size = 0x10000L) else e
    })
    Checks.runIr(mixed).exists(_.contains("all slaves must declare")) shouldBe true

    val misaligned = ir.copy(endpoints = ir.endpoints.map { e =>
      if (e.role != "slave") e
      else e.copy(base = 0x1000L, size = 0x10000L)
    })
    Checks.runIr(misaligned).exists(_.contains("aligned to size")) shouldBe true
  }

  "C2 checks" should "accept the example and reject duplicate classes" in {
    val ir = Ir.fromJson(exampleJson)
    Checks.runC2(ir) shouldBe List.empty
    val dup = ir.copy(qosClasses = ir.qosClasses :+ ir.qosClasses.head)
    Checks.runC2(dup).exists(_.contains("duplicate qos class")) shouldBe true
  }

  it should "reject an invalid class weight" in {
    val ir = Ir.fromJson(exampleJson)
    val bad = ir.copy(qosClasses = ir.qosClasses.map(c =>
      if (c.name == "bulk") c.copy(weight = 0) else c))
    Checks.runC2(bad).exists(_.contains("weight must be in [1,64]")) shouldBe true
  }

  it should "reject an endpoint without a class definition" in {
    val ir = Ir.fromJson(exampleJson)
    val missing = ir.copy(qosClasses = ir.qosClasses.filterNot(_.name == "bulk"))
    val errs = Checks.runC2(missing)
    errs.exists(_.contains("must be exactly ctl,lat,bulk")) shouldBe true
    errs.exists(_.contains("has no class definition")) shouldBe true
  }

  "G6 checks" should "reject a class whose VC depth cannot hold one packet" in {
    val ir = Ir.fromJson(exampleJson)
    val bad = ir.copy(qosClasses = ir.qosClasses.map(c =>
      if (c.name == "bulk") c.copy(depth = 2, maxPacketBeats = 4) else c))
    Checks.runG6(bad).exists(_.contains("LM-G6: qos class 'bulk' depth=2")) shouldBe true
  }

  it should "reject a master whose credits cannot hold one packet" in {
    val ir = Ir.fromJson(exampleJson)
    val bad = ir.copy(endpoints = ir.endpoints.map { e =>
      if (e.role == "master") e.copy(outstanding = 1) else e
    })
    Checks.runG6(bad).exists(_.contains("LM-G6: endpoint")) shouldBe true
  }

  "G7 checks" should "reject a multi-beat ctl class" in {
    val ir = Ir.fromJson(exampleJson)
    val bad = ir.copy(qosClasses = ir.qosClasses.map(c =>
      if (c.name == "ctl") c.copy(maxPacketBeats = 2) else c))
    Checks.runG7(bad) shouldBe List("LM-G7: ctl maxPacketBeats must be 1, got 2")
  }

  "G8 checks" should "reject an unsupported arbitration policy" in {
    val ir = Ir.fromJson(exampleJson)
    val bad = ir.copy(qosClasses = ir.qosClasses.map(c =>
      if (c.name == "bulk") c.copy(arbitration = "sp") else c))
    Checks.runG8(bad).exists(_.contains("LM-G8")) shouldBe true
  }

  "C0 switch checks" should "accept banked only with a dividing power-of-two bank count" in {
    val text = Files.readString(Paths.get("src/loom/testdata/v1/minimal_4x4.json"))
    def withSwitch(kind: String, banks: Int): JValue =
      Json.parse(text.replaceAll(
        "\"kind\"\\s*:\\s*\"flat\"\\s*,\\s*\"banks\"\\s*:\\s*1",
        s"\"kind\": \"$kind\", \"banks\": $banks"))

    Checks.run(withSwitch("banked", 2)) shouldBe List.empty
    Checks.run(withSwitch("banked", 3)).exists(_.contains("power-of-two")) shouldBe true
    Checks.run(withSwitch("banked", 8)).exists(_.contains("divisible by banks")) shouldBe true
  }

  "C0 AXI ID width" should "accept 1..8 and reject anything else" in {
    val text = Files.readString(Paths.get("src/loom/testdata/v1/minimal_4x4.json"))
    Ir.fromJson(Json.parse(text)).axiIdWidth shouldBe 4
    Checks.run(Json.parse(text)) shouldBe List.empty

    val wide = text.replace("\"meta\"", "\"axiIdWidth\": 8, \"meta\"")
    Ir.fromJson(Json.parse(wide)).axiIdWidth shouldBe 8
    Checks.run(Json.parse(wide)) shouldBe List.empty

    Checks.run(Json.parse(text.replace("\"meta\"", "\"axiIdWidth\": 9, \"meta\"")))
      .exists(_.contains("axiIdWidth")) shouldBe true
    Checks.run(Json.parse(text.replace("\"meta\"", "\"axiIdWidth\": 0, \"meta\"")))
      .exists(_.contains("axiIdWidth")) shouldBe true
  }

  "S1-S3 checks" should "reject unassigned and duplicate partition members" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/chain_split.json"))))
    Checks.runS1(ir) shouldBe List.empty

    val missing = ir.copy(partitions = ir.partitions.map { p =>
      if (p.name == "back") p.copy(members = p.members.filterNot(_ == "r3")) else p
    })
    Checks.runS1(missing).exists(_.contains("is not assigned to any partition")) shouldBe true

    val dup = ir.copy(partitions = ir.partitions.map { p =>
      if (p.name == "back") p.copy(members = p.members :+ "r1") else p
    })
    Checks.runS1(dup).exists(_.contains("is in both")) shouldBe true
  }

  it should "keep one clock per partition and spend async links on real boundaries" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/chain_split_async.json"))))
    Checks.runS1(ir) shouldBe List.empty
    Checks.runS2(ir) shouldBe List.empty
    Checks.runS3(ir) shouldBe List.empty

    val wrongClock = ir.copy(partitions = ir.partitions.map { p =>
      if (p.name == "back") p.copy(clock = "core") else p
    })
    Checks.runS1(wrongClock).exists(_.contains("runs on clock")) shouldBe true

    val pointless = ir.copy(links = ir.links.map { l =>
      if (l.from == "r0") l.copy(cdc = "async", asyncFifoDepth = 4) else l
    })
    Checks.runS1(pointless).exists(_.contains("joins clock domain")) shouldBe true
  }

  it should "reject cutting an attachment link and a router-less partition" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/chain_split.json"))))
    val split = ir.copy(partitions = Seq(
      PartitionSpec("a", "core", Seq("mnet0", "r1", "r2", "r3")),
      PartitionSpec("b", "core", Seq("r0", "snet0"))
    ))
    Checks.runS1(split).exists(_.contains("LM-S1")) shouldBe true

    val noRouter = ir.copy(partitions = Seq(
      PartitionSpec("a", "core", Seq("mnet0")),
      PartitionSpec("b", "core", Seq("snet0", "r0", "r1", "r2", "r3"))
    ))
    Checks.runS2(noRouter).exists(_.contains("has no router")) shouldBe true
  }

  // ---- loom/v2 schema + upgrade (ir_gen_spec S1: IR-01–IR-69, MIG-01–MIG-09) ----

  private val v2ExamplePath = Paths.get("src/loom/examples/ring_4.json")
  private def v2Example: IrV2 = IrV2.load(v2ExamplePath)

  private val upgradeExamples = Seq(
    "minimal_2x2", "minimal_4x4", "minimal_4x4_banked", "minimal_8x8",
    "ring_3_bidir", "ring_4", "chain_4r", "tree_3r", "mesh_2x2_xy",
    "chain_split", "chain_split_async")

  behavior of "IrV2"

  it should "parse the ring_4 v2 example with explicit fields (IR-01–IR-69)" in {
    val v = v2Example
    v.unknown shouldBe Nil
    v.nodes.map(_.id) shouldBe Seq("m0", "m1", "s0", "s1")
    v.chi.dvmEnable shouldBe false
    v.chi.addrWidth shouldBe 32
    v.sam.map(w => (w.base, w.size, w.tgtNode)) shouldBe
      Seq((0L, 4096L, "s0"), (4096L, 4096L, "s1"))
    v.links.find(l => l.from == "r3" && l.to == "r0").get.dateline shouldBe true
    v.links.foreach(_.channels shouldBe Seq("req", "rsp", "dat"))
    v.nodes.find(_.id == "m1").get.qos shouldBe 4
  }

  it should "fill defaults and derive SAM when blocks are omitted (GEN-03, IR-05)" in {
    val minimal = Json.parse(
      """{ "schema": "loom/v2", "meta": { "name": "min" },
        |  "clockDomains": [ { "id": "c", "freqMHz": 100 } ],
        |  "nodes": [
        |    { "id": "m0", "kind": "rn-i", "clock": "c", "attach": "r0" },
        |    { "id": "s0", "kind": "sn-f", "clock": "c", "attach": "r0" } ],
        |  "fabric": { "routers": [ { "name": "r0", "clock": "c" } ],
        |              "links": [ { "from": "r0", "to": "r0" } ] } }""".stripMargin)
    val v = IrV2.fromJson(minimal)
    v.chi shouldBe ChiBlockV2()
    v.sam shouldBe Seq(SamWindowV2(0L, 4096L, "s0"))
    v.qos.mapping shouldBe V2Defaults.QosMapping
    v.qos.classes shouldBe V2Defaults.QosClasses
    val l = v.links.head
    l.width shouldBe 256
    l.channels shouldBe Seq("req", "rsp", "dat", "snp")
    l.lcreditDepth shouldBe 4
    val m0 = v.nodes.head
    m0.protocol shouldBe "axi4"
    m0.dataWidth shouldBe 256
    m0.outstanding shouldBe 64
    m0.qos shouldBe 0
  }

  it should "collect unknown and retired keys for the C0 gate (MIG-06)" in {
    val text = Files.readString(v2ExamplePath)
    val withUnknown = Json.parse(text.replaceFirst("\"meta\"", "\"bogus\": 1, \"meta\""))
    IrV2.fromJson(withUnknown).unknown should contain("$.bogus")
    val withDirection = Json.parse(text.replaceFirst(
      "\"from\": \"r0\", \"to\": \"r1\"", "\"from\": \"r0\", \"to\": \"r1\", \"direction\": \"forward\""))
    IrV2.fromJson(withDirection).unknown should contain("$.fabric.links[4].direction")
    val withSwitch = Json.parse(text.replaceFirst(
      "\"name\": \"r0\", \"clock\": \"core\"",
      "\"name\": \"r0\", \"clock\": \"core\", \"switch\": { \"kind\": \"flat\", \"banks\": 1 }"))
    IrV2.fromJson(withSwitch).unknown should contain("$.fabric.routers[0].switch")
  }

  it should "reject a v1 schema and missing required fields (IR-01/IR-04)" in {
    val v1 = Json.parse("""{ "schema": "loom/v1" }""")
    an[Ir.IrException] should be thrownBy IrV2.fromJson(v1)
    val noNodes = Json.parse(
      """{ "schema": "loom/v2", "meta": { "name": "x" },
        |  "clockDomains": [ { "id": "c", "freqMHz": 1 } ] }""".stripMargin)
    an[Ir.IrException] should be thrownBy IrV2.fromJson(noNodes)
  }

  it should "round-trip through the normalized rendering with a stable hash (GEN-06)" in {
    val v = v2Example
    val rendered = IrV2.toJson(v)
    IrV2.fromJson(rendered) shouldBe v
    IrV2.hash(v) shouldBe Canonical.hash(rendered)
    IrV2.hash(IrV2.fromJson(Json.parse(Files.readString(v2ExamplePath)))) shouldBe IrV2.hash(v)
  }

  behavior of "Upgrade.v1ToV2"

  it should "preserve ChiParams elaboration across all v1 examples (MIG-01)" in {
    upgradeExamples.foreach { name =>
      val ir = Ir.fromJson(Json.parse(
        Files.readString(Paths.get(s"src/loom/testdata/v1/$name.json"))))
      val legacy = loom.chi.ChiParams.fromIr(ir, loom.chi.ChiConfig.fromIr(ir))
      val upgraded = loom.gen.ChiAssemble.chiParams(Upgrade.v1ToV2(ir).ir)
      withClue(s"example $name: ") {
        upgraded.rnNodes.map(n => (n.id, n.nodeId, n.clock)) shouldBe
          legacy.rnNodes.map(n => (n.id, n.nodeId, n.clock))
        upgraded.snNodes.map(n => (n.id, n.nodeId, n.clock)) shouldBe
          legacy.snNodes.map(n => (n.id, n.nodeId, n.clock))
        upgraded.sam.map(e => (e.base, e.size, e.tgtId)) shouldBe
          legacy.sam.map(e => (e.base, e.size, e.tgtId))
        // MIG-01 scope is nodes/NodeID/SAM; portMode converges from the v1
        // protocol set (IR-43) while legacy ChiConfig.fromIr keeps 'axi4'.
        // nHalf is topology-derived since ir_gen_spec v1.2.0 (AQ-02/GEN-22:
        // dateline ⇒ 2, else 1) while read-only legacy fromIr keeps default 2.
        upgraded.cfg.copy(portMode = legacy.cfg.portMode, nHalf = legacy.cfg.nHalf) shouldBe legacy.cfg
      }
    }
  }

  it should "converge portMode from the v1 protocol set (MIG-02, IR-43)" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/minimal_2x2.json"))))
    Upgrade.v1ToV2(ir).ir.chi.portMode shouldBe "chi" // all native
    val mixed = ir.copy(endpoints = ir.endpoints.map(e =>
      if (e.id == "m0") e.copy(protocol = "axi4") else e))
    Upgrade.v1ToV2(mixed).ir.chi.portMode shouldBe "both"
    val allAxi = ir.copy(endpoints = ir.endpoints.map(_.copy(protocol = "axi4")))
    Upgrade.v1ToV2(allAxi).ir.chi.portMode shouldBe "axi4"
  }

  it should "emit the fixed MIG-07 warnings verbatim for ring_4" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/ring_4.json"))))
    val ws = Upgrade.v1ToV2(ir).warnings
    ws.head shouldBe Upgrade.SchemaWarning
    ws should contain(Upgrade.linkWidthWarning("mnet0", "r0", 128, 256))
    ws should contain(Upgrade.directionWarning("r0", "r1", "forward"))
    ws.count(_.startsWith("LM-W-DEPRECATED: link")) shouldBe 12 // 8 width + 4 direction
    ws.size shouldBe 13
  }

  it should "warn on banked switch and non-standard addrWidth (MIG-06/09)" in {
    val banked = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/minimal_4x4_banked.json"))))
    Upgrade.v1ToV2(banked).warnings.exists(_.contains("switch block is retired")) shouldBe true
    val wide = banked.copy(endpoints = banked.endpoints.map(e => e.copy(addrWidth = 64)))
    val ws = Upgrade.v1ToV2(wide).warnings
    ws should contain(Upgrade.addrWidthWarning(wide.endpoints.head.id, 64))
    Upgrade.v1ToV2(wide).ir.chi.addrWidth shouldBe 48
  }

  it should "reject retired protocols that cannot be upgraded (MIG-06)" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/minimal_2x2.json"))))
    val bad = ir.copy(endpoints = ir.endpoints.map(e =>
      if (e.id == "m0") e.copy(protocol = "axilite") else e))
    val ex = the[Ir.IrException] thrownBy Upgrade.v1ToV2(bad)
    ex.getMessage should include("retired")
  }

  it should "expand a shared mnet into per-node access links (MIG-05)" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/minimal_2x2.json"))))
    val shared = ir.copy(endpoints = ir.endpoints.map(e =>
      if (e.id == "m1") e.copy(attach = "mnet0") else e))
    val v2 = Upgrade.v1ToV2(shared).ir
    val access = v2.links.filter(l => l.from == "m0" || l.from == "m1")
    access.size shouldBe 2
    access.map(_.to).distinct shouldBe Seq("r0")
    v2.links.exists(_.from == "mnet1") shouldBe false
  }

  it should "upgrade explicit slave windows into sam and drop maxPacketBeats (MIG-03/04)" in {
    val ir = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/minimal_2x2.json"))))
    val explicit = ir.copy(endpoints = ir.endpoints.map { e =>
      if (e.id == "s0") e.copy(base = 0x10000L, size = 4096L)
      else if (e.id == "s1") e.copy(base = 0x20000L, size = 8192L)
      else e
    })
    val v2 = Upgrade.v1ToV2(explicit).ir
    v2.sam.map(w => (w.base, w.size, w.tgtNode)) shouldBe
      Seq((0x10000L, 4096L, "s0"), (0x20000L, 8192L, "s1"))
    v2.qos.classes.map(_.name) shouldBe Seq("ctl", "lat", "bulk")
    v2.nodes.find(_.id == "m0").get.qos shouldBe 0
  }

  // ---- UP-01 routes[] (IR-70–IR-74, §3.12 rendering rule) ----

  behavior of "IrV2 routes (UP-01)"

  it should "parse routes entries with defaults and collect unknown route keys (IR-70–74)" in {
    val text = Files.readString(v2ExamplePath)
    val withRoutes = text.replaceFirst("\"meta\"",
      "\"routes\": [ { \"from\": \"m0\", \"to\": \"s0\", \"path\": [\"r0\", \"r3\", \"r2\"] }," +
      " { \"from\": \"m1\", \"to\": \"s1\", \"forbid\": true } ], \"meta\"")
    val v = IrV2.fromJson(Json.parse(withRoutes))
    v.routes shouldBe Seq(
      RouteSpec("m0", "s0", Seq("r0", "r3", "r2"), false),
      RouteSpec("m1", "s1", Nil, true))
    v.unknown shouldBe Nil
    val withBogus = text.replaceFirst("\"meta\"",
      "\"routes\": [ { \"from\": \"m0\", \"to\": \"s0\", \"bogus\": 1 } ], \"meta\"")
    IrV2.fromJson(Json.parse(withBogus)).unknown should contain("$.routes[0].bogus")
    v2Example.routes shouldBe Nil // omitted block defaults to empty
  }

  it should "round-trip routes through the normalized rendering with a stable hash" in {
    val v = v2Example.copy(routes = Seq(
      RouteSpec("m0", "s0", Seq("r0", "r3", "r2"), false),
      RouteSpec("m1", "s1", Nil, true)))
    val rendered = IrV2.toJson(v)
    IrV2.fromJson(rendered) shouldBe v
    IrV2.hash(v) shouldBe Canonical.hash(rendered)
    IrV2.hash(IrV2.fromJson(rendered)) shouldBe IrV2.hash(v)
  }

  it should "omit an empty routes block so existing hash baselines stay unchanged (§3.12)" in {
    val v = v2Example
    val rendered = IrV2.toJson(v)
    rendered.asInstanceOf[JObj].get("routes") shouldBe None
    val withEmpty = Json.parse(Files.readString(v2ExamplePath)
      .replaceFirst("\"meta\"", "\"routes\": [], \"meta\""))
    val ve = IrV2.fromJson(withEmpty)
    ve.routes shouldBe Nil
    IrV2.hash(ve) shouldBe IrV2.hash(v)
    IrV2.toJson(ve) shouldBe rendered
    // forbid-only entries render without a path key (IR-73)
    val f = v.copy(routes = Seq(RouteSpec("m1", "s1", Nil, true)))
    IrV2.toJson(f).asInstanceOf[JObj].get("routes") match {
      case Some(JArr(items)) =>
        items.head.asInstanceOf[JObj].get("path") shouldBe None
        items.head.asInstanceOf[JObj].get("forbid") shouldBe Some(JBool(true))
      case other => fail(s"unexpected routes rendering: $other")
    }
  }

  // ---- S8: node.qosGen / node.adaptFifoDepth (IR-75–IR-79, UP-02/UP-08) ----

  behavior of "IrV2 qosGen/adaptFifoDepth (UP-02/UP-08)"

  it should "parse qosGen with required kind/bandwidth and defaulted saturation (IR-75–78)" in {
    val text = Files.readString(v2ExamplePath)
    val withQos = text
      .replaceFirst("\"attach\": \"r0\"",
        "\"attach\": \"r0\", \"qosGen\": { \"kind\": \"limiter\", \"bandwidth\": 1000, \"saturation\": 4096 }")
      .replaceFirst("\"attach\": \"r2\"",
        "\"attach\": \"r2\", \"qosGen\": { \"kind\": \"fixed\", \"bandwidth\": 500 }")
      .replaceFirst("\"attach\": \"r1\"", "\"attach\": \"r1\", \"adaptFifoDepth\": 8")
    val v = IrV2.fromJson(Json.parse(withQos))
    v.unknown shouldBe Nil
    v.nodes.find(_.id == "m0").get.qosGen shouldBe Some(QosGenSpec("limiter", 1000, 4096))
    v.nodes.find(_.id == "s0").get.qosGen shouldBe Some(QosGenSpec("fixed", 500, 0))
    v.nodes.find(_.id == "m1").get.adaptFifoDepth shouldBe Some(8)
    v.nodes.find(_.id == "s1").get.qosGen shouldBe None
    v.nodes.find(_.id == "s1").get.adaptFifoDepth shouldBe None
    // unknown qosGen subkeys are collected for the C0 unknown-key gate
    val withBogus = text.replaceFirst("\"attach\": \"r0\"",
      "\"attach\": \"r0\", \"qosGen\": { \"kind\": \"fixed\", \"bandwidth\": 1, \"bogus\": 2 }")
    IrV2.fromJson(Json.parse(withBogus)).unknown should contain("$.nodes[0].qosGen.bogus")
  }

  it should "round-trip qosGen/adaptFifoDepth through the normalized rendering with a stable hash" in {
    val v0 = v2Example
    val v = v0.copy(nodes = v0.nodes.map { n =>
      if (n.id == "m0") n.copy(qosGen = Some(QosGenSpec("regulator", 2000, 1024)), adaptFifoDepth = Some(16))
      else n
    })
    val rendered = IrV2.toJson(v)
    IrV2.fromJson(rendered) shouldBe v
    IrV2.hash(v) shouldBe Canonical.hash(rendered)
    IrV2.hash(IrV2.fromJson(rendered)) shouldBe IrV2.hash(v)
    // a declared qosGen renders all three subkeys (saturation explicit, GEN-03)
    val m0 = rendered.asInstanceOf[JObj].get("nodes").get.asInstanceOf[JArr].items.head
      .asInstanceOf[JObj].get("qosGen").get.asInstanceOf[JObj]
    m0.get("kind") shouldBe Some(JStr("regulator"))
    m0.get("saturation") shouldBe Some(JNum(BigDecimal(1024)))
  }

  it should "omit absent qosGen/adaptFifoDepth keys so the ring_4 hash baseline stays unchanged (§3.13/§3.14)" in {
    val v = v2Example
    val rendered = IrV2.toJson(v)
    rendered.asInstanceOf[JObj].get("nodes").get.asInstanceOf[JArr].items.foreach { n =>
      n.asInstanceOf[JObj].get("qosGen") shouldBe None
      n.asInstanceOf[JObj].get("adaptFifoDepth") shouldBe None
    }
    // explicit default-depth declaration is a real IR change (hash moves);
    // absence keeps the pre-v1.3.0 baseline byte-for-byte.
    IrV2.hash(v) shouldBe IrV2.hash(IrV2.fromJson(Json.parse(Files.readString(v2ExamplePath))))
    val withDepth = v.copy(nodes = v.nodes.map(n =>
      if (n.id == "m0") n.copy(adaptFifoDepth = Some(4)) else n))
    IrV2.hash(withDepth) should not be IrV2.hash(v)
  }
}
