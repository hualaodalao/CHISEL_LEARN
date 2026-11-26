package loom

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.file.{Files, Path, Paths}

import scala.jdk.CollectionConverters._

import loom.ir.{JBool, JObj, JStr, Json, Upgrade}
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

  it should "refuse partitioned emission with DVM nodes until HW-08" in {
    val base = loom.ir.IrV2.load(Paths.get(v2Cfg))
    val v2 = base.copy(
      chi = base.chi.copy(dvmEnable = true),
      nodes = base.nodes :+ loom.ir.NodeV2("mn0", "mn", None, "core", "r0", "chi", 256, 64, 0),
      partitions = Seq(
        loom.ir.PartitionSpec("p0", "core", Seq("m0", "r0", "r1", "mn0")),
        loom.ir.PartitionSpec("p1", "core", Seq("m1", "r2", "r3", "s0", "s1"))))
    val full = loom.gen.ChiAssemble.networkParams(v2)
    val ex = the[loom.ir.Ir.IrException] thrownBy loom.gen.ChiAssemble.partitionParams(v2, full)
    ex.getMessage should include("HW-08")
  }
}