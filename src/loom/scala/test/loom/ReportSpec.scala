package loom

import java.nio.file.{Files, Paths}

import loom.check.ChecksV2
import loom.gen.ChiAssemble
import loom.ir._
import loom.report.{Reports, Sram}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ReportSpec extends AnyFlatSpec with Matchers {
  private val root = Json.parse(Files.readString(Paths.get("src/loom/testdata/v1/minimal_4x4.json")))
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

  // ==========================================================================
  // loom/v2 report caliber (S5, GEN-17). The v1 block above stays green: the
  // v1 functions remain for legacy consumers until the next major (MIG-10).
  // ==========================================================================
  private val v2 = IrV2.load(Paths.get("src/loom/examples/ring_4.json"))
  private val v2Hash = IrV2.hash(v2)

  behavior of "Reports (loom/v2, GEN-17)"

  it should "render the SAM address map with the legacy keys preserved (GEN-15 addressMap row, MIG-10)" in {
    val m = Canonical.pretty(Reports.addressMapJsonV2(v2, explicit = true))
    m should include("\"explicit\": true")
    m should include("\"routeShift\": 12")
    m should include("\"stride\": 4096")
    m should include("\"slaves\"")
    m should include("\"base\": \"0x0\"")
    m should include("\"end\": \"0xfff\"")
    // sam[]{base,size,tgtNode,tgtId}: NodeIDs per SEM-02 (s0=2, s1=3).
    m should include("\"tgtNode\": \"s0\"")
    m should include("\"tgtId\": 2")
    m should include("\"tgtNode\": \"s1\"")
    m should include("\"tgtId\": 3")
    Canonical.pretty(Reports.addressMapJsonV2(v2, explicit = true)) shouldBe m
  }

  it should "keep the loom/checks/v1 keys with the G1 four-key gates (GEN-17 checks row, GEN-11)" in {
    val res = ChecksV2.run(v2)
    val j = Canonical.pretty(Reports.checksJsonV2(v2, v2Hash, res.errors, Nil, res.gates))
    j should include("\"schema\": \"loom/checks/v1\"")
    j should include("\"configHash\"")
    j should include("\"G1-REQ\": true")
    j should include("\"G1-RSP\": true")
    j should include("\"G1-DAT\": true")
    j should include("\"G1-SNP\": true")
    j should include("\"G7\": true") // retired gate key stays one version
    j should include("\"ok\": true")
  }

  it should "report DAT width, flit count and three-level flow control (GEN-17 estimate row, ENC-01)" in {
    val e = Canonical.pretty(Reports.estimateJsonV2(v2, v2Hash))
    e should include("\"datWidth\": 256")
    e should include("\"flitCount64B\": 2")
    e should include("\"maxNodes\": 128")
    e should include("\"lcreditDepth\": 4")
    e should include("\"flowControl\"")
    e should include("\"txnIdSpace\": 256")
    e should include("\"channels\"")
  }

  it should "report chi_nodes with attach/localPort, tgtNode and snpEnable (GEN-17 chi_nodes row)" in {
    val net = ChiAssemble.networkParams(v2)
    val j = Canonical.pretty(Reports.chiNodesJsonV2(v2, net))
    j should include("\"attach\": \"r0\"")
    j should include("\"localPort\": 0")
    j should include("\"tgtNode\": \"s0\"")
    j should include("\"snpEnable\": false")
    j should include("\"hn\"")
    j should include("\"mn\"")
    // IR-only fallback keeps every key except the elaboration-owned localPort.
    val fallback = Canonical.pretty(Reports.chiNodesJsonV2(v2))
    fallback should include("\"tgtNode\": \"s0\"")
    fallback should include("\"snpEnable\": false")
    fallback should not include "\"localPort\""
  }

  it should "keep manifest v1 keys and add irSchema/chi (GEN-09/GEN-17 manifest row, MIG-10)" in {
    val m = Canonical.pretty(Reports.manifestJsonV2("ring_4", v2Hash, "2.0.0",
      Seq("a.sv" -> "01"), Reports.chiSnapshotJson(v2)))
    m should include("\"schema\": \"loom/manifest/v1\"")
    m should include("\"configHash\"")
    m should include("\"files\"")
    m should include("\"irSchema\": \"loom/v2\"")
    m should include("\"datWidth\": 256")
  }

  it should "add the D11 channel-class limitation to the formal report (GEN-17 formal row)" in {
    val j = Canonical.pretty(Reports.formalJsonV2("ring_4", v2Hash,
      Seq(("token_chi_lcredit", "CHI L-Credit conservation", true, "formal/token_chi_lcredit.log"))))
    j should include("\"schema\": \"loom/formal/v1\"")
    j should include("generated channel classes (D11)")
    j should include("bounded model checking over the control-token model")
  }

  it should "render signoff with Schema loom/v2, G1 keys and node/SAM tables (GEN-17 signoff row)" in {
    val gates = Vector("G1-REQ" -> true, "G1-RSP" -> true, "G1-DAT" -> true, "G1-SNP" -> true)
    val md = Reports.signoffMarkdownV2(v2, v2Hash, gates, Nil, (3, 0, 1), None)
    md should include("Schema: `loom/v2`")
    md should include("| G1-REQ | PASS |")
    md should include("| G1-SNP | PASS |")
    md should include("## Nodes")
    md should include("| m0 | rn-i | 0 | r0 |")
    md should include("## SAM")
    md should include("| 0x0 | 4096 | s0 | 2 |")
  }

  it should "keep ports masters/slaves and add nodes/channels/lcreditDepth (GEN-17 ports row)" in {
    val upgraded = Upgrade.v1ToV2(Ir.load(Paths.get("src/loom/testdata/v1/chain_split.json"))).ir
    val net = ChiAssemble.networkParams(upgraded)
    val (spec, pp) = ChiAssemble.partitionParams(upgraded, net).head
    val j = Canonical.pretty(Reports.portsJsonV2(upgraded, net, pp, spec, IrV2.hash(upgraded)))
    j should include("\"schema\": \"loom/ports/v1\"")
    j should include("\"masters\"")
    j should include("\"slaves\"")
    j should include("\"nodes\"")
    j should include("\"channels\"")
    j should include("\"lcreditDepth\"")
    j should include("\"partitionHash\"")
  }
}
