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
    Files.readString(Paths.get("src/loom/testdata/v1/chain_4r.json"))))
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
        dut.io.sn(j).rxDat.ready.poke(true.B)
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

  "ChiPartitionTop with MN" should "run DVM end-to-end inside a partition (HW-08)" in {
    val c = ChiConfig()
    val rn0 = ChiNetNode(0, ChiNodeKind.RnD, 0, 0)
    val rn1 = ChiNetNode(1, ChiNodeKind.RnD, 0, 1)
    val mn = ChiNetNode(2, ChiNodeKind.Mn, 0, 2)
    val route = Seq.tabulate(c.maxNodes)(id => if (id < 3) id else 0)
    val r0 = ChiNetRouter(0, "r0", nLocal = 3, nLink = 0, route, Seq.empty)
    val net = ChiNetworkParams("dvmPart", c, Seq(rn0, rn1), Seq.empty, Seq(r0),
      Seq.empty, snpEnable = true, queueDepth = 2, hn = Seq.empty, mn = Seq(mn))
    val pp = ChiPartitionParams("P", net, Seq(0), pipe = 1)
    simulate(new ChiPartitionTop(pp)) { dut =>
      for (i <- 0 until 2) {
        dut.io.rn(i).req.valid.poke(false.B)
        dut.io.rn(i).rsp.ready.poke(true.B)
        dut.io.rn(i).dat.ready.poke(true.B)
        dut.io.rn(i).snp.ready.poke(true.B)
        dut.io.rn(i).txRsp.valid.poke(false.B)
        dut.io.rn(i).txDat.valid.poke(false.B)
      }
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)

      dut.io.rn(0).req.bits.opcode.poke(ChiOp.DVMOp.U)
      dut.io.rn(0).req.bits.tgtId.poke(2.U)
      dut.io.rn(0).req.bits.txnId.poke(4.U)
      dut.io.rn(0).req.bits.srcId.poke(0.U)
      dut.io.rn(0).req.valid.poke(true.B)
      var n = 0
      while (!dut.io.rn(0).rsp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.rn(0).rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.io.rn(0).req.valid.poke(false.B)
      dut.clock.step()

      dut.io.rn(0).txDat.bits.data.poke(0x1000L.U)
      dut.io.rn(0).txDat.bits.tgtId.poke(2.U)
      dut.io.rn(0).txDat.bits.txnId.poke(4.U)
      dut.io.rn(0).txDat.valid.poke(true.B)
      dut.clock.step(2)
      dut.io.rn(0).txDat.valid.poke(false.B)

      n = 0
      while (!dut.io.rn(0).snp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.rn(0).snp.bits.opcode.expect(ChiOp.SnpDVMOp.U)
      dut.clock.step()
      n = 0
      while (!dut.io.rn(1).snp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.clock.step()

      dut.io.rn(0).txRsp.bits.opcode.poke(ChiOp.RspSnpResp.U)
      dut.io.rn(0).txRsp.bits.srcId.poke(0.U)
      dut.io.rn(0).txRsp.bits.tgtId.poke(2.U)
      dut.io.rn(0).txRsp.valid.poke(true.B)
      dut.io.rn(1).txRsp.bits.opcode.poke(ChiOp.RspSnpResp.U)
      dut.io.rn(1).txRsp.bits.srcId.poke(1.U)
      dut.io.rn(1).txRsp.bits.tgtId.poke(2.U)
      dut.io.rn(1).txRsp.valid.poke(true.B)
      n = 0
      while (!dut.io.rn(0).rsp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.rn(0).txRsp.valid.poke(false.B)
      dut.io.rn(1).txRsp.valid.poke(false.B)
      dut.io.rn(0).rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.rn(0).rsp.bits.txnId.expect(4.U)
    }
  }
}