package loom

import java.nio.file.{Files, Path, Paths}

import _root_.circt.stage.ChiselStage
import loom.chi._
import loom.ir.{Ir, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class ChiSpec extends AnyFlatSpec with Matchers {
  private val text = Files.readString(Paths.get("src/loom/examples/minimal_4x4.json"))
  private val ir = Ir.fromJson(Json.parse(text))

  "ChiConfig" should "accept the ruled defaults" in {
    val c = ChiConfig()
    c.datWidth shouldBe 256
    c.txnIdWidth shouldBe 8
    c.outstanding shouldBe 64
    c.beWidth shouldBe 32
  }

  it should "reject non-standard DAT widths (no 64b)" in {
    an[IllegalArgumentException] should be thrownBy ChiConfig(datWidth = 64)
    an[IllegalArgumentException] should be thrownBy ChiConfig(datWidth = 1024)
  }

  it should "reject out-of-range fields" in {
    an[IllegalArgumentException] should be thrownBy ChiConfig(txnIdWidth = 4)
    an[IllegalArgumentException] should be thrownBy ChiConfig(lcreditDepth = 16)
    an[IllegalArgumentException] should be thrownBy ChiConfig(portMode = "native")
  }

  "ChiOpcode" should "classify supported vs reserved disjointly" in {
    ChiOpcode.reqSupported should contain("ReadNoSnp")
    ChiOpcode.reqSupported should contain("AtomicSwap")
    ChiOpcode.reqSupported should contain("DVMOp")
    ChiOpcode.atomicAlu.length shouldBe 8
    ChiOpcode.reqSupported.count(_.startsWith("AtomicStore")) shouldBe 8
    ChiOpcode.reqSupported.count(_.startsWith("AtomicLoad")) shouldBe 8
    ChiOpcode.snpSupported shouldBe Seq("SnpDVMOp")
    ChiOpcode.reserved.foreach { op =>
      ChiOpcode.reqSupported should not contain op
      ChiOpcode.rspSupported should not contain op
    }
    ChiOpcode.isSupported(ChiChannel.Req, "ReadNoSnp") shouldBe true
    ChiOpcode.isSupported(ChiChannel.Req, "ReadShared") shouldBe false
    ChiOpcode.isSupported(ChiChannel.Snp, "SnpDVMOp") shouldBe true
    ChiOpcode.isSupported(ChiChannel.Snp, "SnpShared") shouldBe false
  }

  "ChiSize/ChiWidths" should "encode the 64B transaction constants" in {
    ChiSize.encode(64) shouldBe 6
    ChiSize.decode(6) shouldBe 64
    an[IllegalArgumentException] should be thrownBy ChiSize.encode(128)
    ChiWidths.flitCount(64, 256) shouldBe 2
    ChiWidths.flitCount(64, 512) shouldBe 1
    ChiWidths.flitCount(64, 128) shouldBe 4
  }

  "ChiParams" should "map masters to RN and slaves to SN with a SAM" in {
    val p = ChiParams.fromIr(ir)
    val masters = ir.endpoints.count(_.role == "master")
    val slaves = ir.endpoints.count(_.role == "slave")
    p.nRn shouldBe masters
    p.nSn shouldBe slaves
    p.rnNodes.forall(_.kind == ChiNodeKind.RnI) shouldBe true
    p.snNodes.forall(_.kind == ChiNodeKind.SnF) shouldBe true
    val ids = (p.rnNodes ++ p.snNodes).map(_.nodeId)
    ids.distinct.length shouldBe ids.length
    p.sam.length shouldBe slaves
    p.sam.map(_.tgtId) shouldBe p.snNodes.map(_.nodeId)
  }

  "ChiParams" should "accept multiple SAM windows mapping to one SN (HW-02)" in {
    val rn = Seq(ChiNode("m0", ChiNodeKind.RnI, 0, "core"))
    val sn = Seq(ChiNode("s0", ChiNodeKind.SnF, 1, "core"))
    val sam = Seq(ChiSamEntry(0x0L, 0x1000L, 1), ChiSamEntry(0x2000L, 0x1000L, 1))
    val p = ChiParams("multiwin", ChiConfig(), rn, sn, sam)
    p.sam.length shouldBe 2
    p.sam.map(_.tgtId).distinct shouldBe Seq(1)
  }

  "ChiNodeIds" should "assign RN->SN->HN->MN segments with override priority (HW-01)" in {
    import ChiNodeKind._
    val kinds = Seq(Mn, SnF, RnI, HnI, RnD, SnI)
    val ids = ChiNodeIds.assign(kinds, Seq.fill(6)(None), 7)
    // declaration order within segment: RnI(2)->0, RnD(4)->1; SnF(1)->2, SnI(5)->3; HnI(3)->4; Mn(0)->5
    ids shouldBe Seq(5, 2, 0, 4, 1, 3)
    // explicit override wins and is skipped by auto allocation
    val ov = ChiNodeIds.assign(Seq(RnI, RnI, SnF), Seq(Some(9), None, None), 7)
    ov shouldBe Seq(9, 0, 1)
    an[IllegalArgumentException] should be thrownBy ChiNodeIds.assign(Seq(RnI, RnI), Seq(Some(3), Some(3)), 7)
    an[IllegalArgumentException] should be thrownBy ChiNodeIds.assign(Seq(RnI), Seq(Some(128)), 7)
  }

  "ChiTop emission" should "be deterministic and expose CHI ports" in {
    val p = ChiParams.fromIr(ir)
    val d1 = Files.createTempDirectory("loom-chi-a")
    val d2 = Files.createTempDirectory("loom-chi-b")
    ChiselStage.emitSystemVerilogFile(new ChiTop(p), Array("--target-dir", d1.toString))
    ChiselStage.emitSystemVerilogFile(new ChiTop(p), Array("--target-dir", d2.toString))

    def sv(dir: Path): Map[String, String] =
      Files.list(dir).iterator().asScala
        .filter(_.getFileName.toString.endsWith(".sv"))
        .map(f => f.getFileName.toString -> Files.readString(f)).toMap

    val a = sv(d1)
    a should not be empty
    a shouldBe sv(d2)
    a.keys.exists(_.contains("ChiTop")) shouldBe true
    val top = a.collectFirst { case (n, s) if n.contains("ChiTop") => s }.get
    top should include("rn_0_req_valid")
    top should include("sn_0_req_ready")
    top should include("tgtId")
  }
}