package loom

import java.nio.file.{Files, Path, Paths}

import _root_.circt.stage.ChiselStage
import loom.ir.{Ir, Json}
import loom.rtl.{FabricParams, LmTop}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class EmitSpec extends AnyFlatSpec with Matchers {
  private val text = Files.readString(Paths.get("src/loom/examples/minimal_4x4.json"))
  private val params = FabricParams.fromIr(Ir.fromJson(Json.parse(text)))

  "Loom emission" should "produce identical RTL for the same IR" in {
    val d1 = Files.createTempDirectory("loom-emit-a")
    val d2 = Files.createTempDirectory("loom-emit-b")
    ChiselStage.emitSystemVerilogFile(new LmTop(params), Array("--target-dir", d1.toString))
    ChiselStage.emitSystemVerilogFile(new LmTop(params), Array("--target-dir", d2.toString))

    // The CLI writes the reports; here we verify the RTL side determinism.
    def sv(dir: Path): Map[String, String] =
      Files
        .list(dir)
        .iterator()
        .asScala
        .filter(_.getFileName.toString.endsWith(".sv"))
        .map(f => f.getFileName.toString -> Files.readString(f))
        .toMap

    val a = sv(d1)
    val b = sv(d2)
    a should not be empty
    a shouldBe b
    a.keys.exists(_.contains("LmTop")) shouldBe true
  }
}
