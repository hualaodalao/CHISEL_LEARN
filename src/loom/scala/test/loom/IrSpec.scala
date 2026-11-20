package loom

import java.nio.file.{Files, Paths}

import loom.check.Checks
import loom.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IrSpec extends AnyFlatSpec with Matchers {
  private val examplePath = Paths.get("src/loom/examples/minimal_2x2.json")
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
    val text = Files.readString(Paths.get("src/loom/examples/minimal_4x4.json"))
    def withSwitch(kind: String, banks: Int): JValue =
      Json.parse(text.replaceAll(
        "\"kind\"\\s*:\\s*\"flat\"\\s*,\\s*\"banks\"\\s*:\\s*1",
        s"\"kind\": \"$kind\", \"banks\": $banks"))

    Checks.run(withSwitch("banked", 2)) shouldBe List.empty
    Checks.run(withSwitch("banked", 3)).exists(_.contains("power-of-two")) shouldBe true
    Checks.run(withSwitch("banked", 8)).exists(_.contains("divisible by banks")) shouldBe true
  }

  "C0 AXI ID width" should "accept 1..8 and reject anything else" in {
    val text = Files.readString(Paths.get("src/loom/examples/minimal_4x4.json"))
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
      Files.readString(Paths.get("src/loom/examples/chain_split.json"))))
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
      Files.readString(Paths.get("src/loom/examples/chain_split_async.json"))))
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
      Files.readString(Paths.get("src/loom/examples/chain_split.json"))))
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
}
