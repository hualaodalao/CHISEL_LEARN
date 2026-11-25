package loom

import java.nio.file.{Files, Paths}

import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.ir.{Ir, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.jdk.CollectionConverters._

class ChiPartitionSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(Json.parse(
    Files.readString(Paths.get("src/loom/examples/chain_4r.json"))))
  private val full = ChiNetworkParams.fromIr(ir)
  private val ppA = ChiPartitionParams("A", full, Seq(0, 1), pipe = 1)
  private val ppB = ChiPartitionParams("B", full, Seq(2, 3), pipe = 1)

  "ChiPartitionParams" should "split routers and expose the cut boundary link" in {
    ppA.routers.map(_.index) shouldBe Seq(0, 1)
    ppB.routers.map(_.index) shouldBe Seq(2, 3)
    ppA.boundary.length shouldBe 1
    ppB.boundary.length shouldBe 1
    ppA.boundary.head.id shouldBe ppB.boundary.head.id
    ppA.rn.length shouldBe 2 // m0/m1 at r0
    ppB.sn.length shouldBe 2 // s0/s1 at r3
  }

  "ChiWrapperTop" should "route REQ across the partition boundary RN->SN" in {
    simulate(new ChiWrapperTop(full, Seq(ppA, ppB))) { dut =>
      for (i <- 0 until full.nRn) {
        dut.io.rn(i).req.valid.poke(false.B)
        dut.io.rn(i).rsp.ready.poke(true.B)
        dut.io.rn(i).dat.ready.poke(true.B)
        dut.io.rn(i).snp.ready.poke(true.B)
        dut.io.rn(i).txRsp.valid.poke(false.B)
        dut.io.rn(i).txDat.valid.poke(false.B)
      }
      for (j <- 0 until full.nSn) {
        dut.io.sn(j).req.ready.poke(true.B)
        dut.io.sn(j).rsp.valid.poke(false.B)
        dut.io.sn(j).dat.valid.poke(false.B)
      }
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)

      dut.io.rn(0).req.bits.tgtId.poke(2.U)
      dut.io.rn(0).req.bits.txnId.poke(11.U)
      dut.io.rn(0).req.valid.poke(true.B)
      var n = 0
      while (!dut.io.sn(0).req.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.sn(0).req.valid.expect(true.B)
      dut.io.sn(0).req.bits.txnId.expect(11.U)
    }
  }

  "ChiPartitionStub" should "elaborate deterministically with the partition port contract" in {
    val d1 = Files.createTempDirectory("loom-chi-stub-a")
    val d2 = Files.createTempDirectory("loom-chi-stub-b")
    ChiselStage.emitSystemVerilogFile(new ChiPartitionStub(ppA), Array("--target-dir", d1.toString))
    ChiselStage.emitSystemVerilogFile(new ChiPartitionStub(ppA), Array("--target-dir", d2.toString))
    def sv(dir: java.nio.file.Path): Map[String, String] =
      Files.list(dir).iterator().asScala
        .filter(_.getFileName.toString.endsWith(".sv"))
        .map(f => f.getFileName.toString -> Files.readString(f)).toMap
    val a = sv(d1)
    a should not be empty
    a shouldBe sv(d2)
    a.keys.exists(_.contains("ChiPartitionStub")) shouldBe true
  }
}