package loom

import java.nio.file.{Files, Path}

import _root_.circt.stage.ChiselStage
import chisel3._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class ChiReservedSpec extends AnyFlatSpec with Matchers {
  "ChiReserved usage gate" should "pass supported and reject reserved/unsupported opcodes" in {
    ChiReserved.checkOpcode(ChiChannel.Req, "ReadNoSnp") shouldBe None
    ChiReserved.checkOpcode(ChiChannel.Snp, "SnpDVMOp") shouldBe None
    ChiReserved.checkOpcode(ChiChannel.Req, "ReadShared") shouldBe defined
    ChiReserved.checkOpcode(ChiChannel.Snp, "SnpShared") shouldBe defined
    ChiReserved.checkOpcode(ChiChannel.Req, "WriteBack") shouldBe defined
  }

  it should "reserve cache-state encodings and Snp* opcode space" in {
    Set(ChiReserved.RespI, ChiReserved.RespSC, ChiReserved.RespUC,
      ChiReserved.RespUD, ChiReserved.RespSD).size shouldBe 5
    ChiReserved.snpReserved.keySet should contain("SnpShared")
    ChiReserved.snpReserved.keySet should contain("SnpUnique")
  }

  private class Placeholder(c: ChiConfig) extends Module {
    val io = IO(new Bundle {
      val rnf = new ChiRnFPort(c)
      val hnf = new ChiHnFPort(c)
    })
    io.rnf.req.valid := false.B; io.rnf.req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
    io.rnf.rsp.ready := true.B; io.rnf.dat.ready := true.B; io.rnf.snp.ready := true.B
    io.hnf.req.ready := true.B
    io.hnf.rsp.valid := false.B; io.hnf.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    io.hnf.dat.valid := false.B; io.hnf.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    io.hnf.snp.valid := false.B; io.hnf.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    io.hnf.rxRsp.ready := true.B; io.hnf.rxDat.ready := true.B
  }

  "RN-F/HN-F port placeholders" should "elaborate and emit deterministically" in {
    val c = ChiConfig()
    val d1 = Files.createTempDirectory("loom-rsv-a")
    val d2 = Files.createTempDirectory("loom-rsv-b")
    ChiselStage.emitSystemVerilogFile(new Placeholder(c), Array("--target-dir", d1.toString))
    ChiselStage.emitSystemVerilogFile(new Placeholder(c), Array("--target-dir", d2.toString))
    def sv(dir: Path): Map[String, String] =
      Files.list(dir).iterator().asScala
        .filter(_.getFileName.toString.endsWith(".sv"))
        .map(f => f.getFileName.toString -> Files.readString(f)).toMap
    val a = sv(d1)
    a should not be empty
    a shouldBe sv(d2)
  }
}