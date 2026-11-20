package loom

import java.nio.file.{Files, Paths}

import loom.ir._
import loom.report.{Reports, Sram}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ReportSpec extends AnyFlatSpec with Matchers {
  private val root = Json.parse(Files.readString(Paths.get("src/loom/examples/minimal_4x4.json")))
  private val ir = Ir.fromJson(root)
  private val hash = Canonical.hash(root)

  "Reports" should "be byte-stable across renders" in {
    Canonical.pretty(Reports.estimateJson(ir, hash)) shouldBe
      Canonical.pretty(Reports.estimateJson(ir, hash))
    Canonical.pretty(Reports.checksJson(ir, hash, Nil, Nil,
      Vector("C0" -> true, "C1" -> true, "C2" -> true))) should include("\"ok\": true")
  }

  it should "report flat 4x4 crosspoints and per-link capacity" in {
    val e = Canonical.pretty(Reports.estimateJson(ir, hash))
    e should include("\"crosspoints\": 16")
    e should include("\"capacityGBps\": 16")
    e should include("\"maxLinkCapacityGBps\": 16")
    e should include("\"latencyProxyCycles\": 2")
  }

  it should "price large queues as SRAM and keep them out of the logic proxy" in {
    val shapes = Reports.sramNeedsOf(ir)
    shapes.nonEmpty shouldBe true
    val big = shapes.filter { case (d, w) => d * w >= Sram.ThresholdBits }
    big.nonEmpty shouldBe true

    val e = Canonical.pretty(Reports.estimateJson(ir, hash))
    e should include(s"\"sramBits\": ${big.map { case (d, w) => d * w }.sum}")
    e should include("\"sramBlocks\":")
    e should include("\"sramAreaProxyUm2\":")
    e should include("\"totalAreaProxyUm2\":")
  }

  it should "generate a behavioral SRAM block for a required shape" in {
    val sv = Sram.singlePort(16, 176)
    sv should include("module LmSramSp_16x176")
    sv should include("input  wire [3:0] addr")
    sv should include("reg [175:0] mem [0:15]")
  }

  it should "write a stable artifact manifest" in {    val m1 = Canonical.pretty(Reports.manifestJson("demo", hash, "0.4.0",
      Seq("b.sv" -> "02", "a.sv" -> "01")))
    val m2 = Canonical.pretty(Reports.manifestJson("demo", hash, "0.4.0",
      Seq("a.sv" -> "01", "b.sv" -> "02")))
    m1 shouldBe m2
    m1 should include("\"schema\": \"loom/manifest/v1\"")
    m1.indexOf("a.sv") should be < m1.indexOf("b.sv")
  }

  it should "render a signoff with gates and a formal placeholder" in {
    val md = Reports.signoffMarkdown(ir, hash,
      Vector("C0" -> true, "G1" -> false), List("LM-W-TIMING demo"), (12, 5, 1), None)
    md should include("| C0 | PASS |")
    md should include("| G1 | FAIL |")
    md should include("LM-W-TIMING demo")
    md should include("loom formal")
    md should include("RTL files: 12")
  }

  it should "warn on fan-in over budget without changing the config" in {
    val tight = ir.copy(timing = TimingSpec(maxFanin = 2, maxCombDepth = 12))
    val ws = Reports.timingWarnings(tight)
    ws.exists(_.contains("LM-W-TIMING")) shouldBe true
    ws.exists(_.contains("suggested banks=2")) shouldBe true
    tight.routers.head.in shouldBe 4
    tight.routers.head.banks shouldBe 1
  }
}
