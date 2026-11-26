package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.ir.{Ir, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiNetworkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(Json.parse(
    Files.readString(Paths.get("src/loom/testdata/v1/chain_4r.json"))))

  private val ir1 = Ir.fromJson(Json.parse(
    Files.readString(Paths.get("src/loom/testdata/v1/minimal_2x2.json"))))

  "ChiNetworkParams" should "place RN at r0 and SN at r3 with BFS routes" in {
    val p = ChiNetworkParams.fromIr(ir)
    p.nRn shouldBe 2
    p.nSn shouldBe 2
    p.rn.foreach(_.router shouldBe 0)
    p.sn.foreach(_.router shouldBe 3)
    p.routers.length shouldBe 4
    // r0 routes SN NodeIDs (2/3) to its link port toward r1.
    val r0 = p.routers.head
    r0.route(2) shouldBe r0.nLocal
    r0.route(3) shouldBe r0.nLocal
    // r3 routes SN NodeIDs to local ports.
    val r3 = p.routers(3)
    r3.route(2) should be < r3.nLocal
  }

  "ChiGates" should "pass G1 per class on a chain and conditionally include SNP" in {
    val res = ChiGates.run(ir, snpEnable = true)
    res.errors shouldBe empty
    res.gates.toMap.apply("G1-REQ") shouldBe true
    res.gates.toMap.contains("G1-SNP") shouldBe true
    val noSnp = ChiGates.run(ir, snpEnable = false)
    noSnp.gates.toMap.apply("G1-SNP") shouldBe true
  }

  private def init(dut: ChiNetworkTop, p: ChiNetworkParams): Unit = {
    for (i <- 0 until p.nRn) {
      dut.io.rn(i).req.valid.poke(false.B)
      dut.io.rn(i).rsp.ready.poke(true.B)
      dut.io.rn(i).dat.ready.poke(true.B)
      dut.io.rn(i).snp.ready.poke(true.B)
      dut.io.rn(i).txRsp.valid.poke(false.B)
      dut.io.rn(i).txDat.valid.poke(false.B)
    }
    for (j <- 0 until p.nSn) {
      dut.io.sn(j).req.ready.poke(true.B)
      dut.io.sn(j).rsp.valid.poke(false.B)
      dut.io.sn(j).dat.valid.poke(false.B)
      dut.io.sn(j).rxDat.ready.poke(true.B)
    }
    dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
  }

  "ChiNetworkTop" should "route REQ forward RN->SN across the chain" in {
    val p = ChiNetworkParams.fromIr(ir)
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut, p)
      dut.io.rn(0).req.bits.tgtId.poke(2.U)
      dut.io.rn(0).req.bits.txnId.poke(7.U)
      dut.io.rn(0).req.valid.poke(true.B)
      var n = 0
      while (!dut.io.sn(0).req.valid.peek().litToBoolean && n < 60) { dut.clock.step(); n += 1 }
      dut.io.sn(0).req.valid.expect(true.B)
      dut.io.sn(0).req.bits.txnId.expect(7.U)
    }
  }

  it should "route DAT backward SN->RN across the chain" in {
    val p = ChiNetworkParams.fromIr(ir)
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut, p)
      dut.io.sn(0).dat.bits.tgtId.poke(0.U)
      dut.io.sn(0).dat.bits.data.poke(0xCAFEL.U)
      dut.io.sn(0).dat.valid.poke(true.B)
      var n = 0
      while (!dut.io.rn(0).dat.valid.peek().litToBoolean && n < 60) { dut.clock.step(); n += 1 }
      dut.io.rn(0).dat.valid.expect(true.B)
      dut.io.rn(0).dat.bits.data.expect(0xCAFEL.U)
    }
  }

  "ChiNetworkParams single router" should "elaborate one router with configurable queue depth (HW-03/07)" in {
    val p = ChiNetworkParams.fromIr(ir1)
    p.routers.length shouldBe 1
    p.nRn shouldBe 2
    p.nSn shouldBe 2
    p.queueDepth shouldBe ChiConfig().lcreditDepth
    p.routers.head.nLink shouldBe 0
  }

  it should "route REQ within a single router RN->SN" in {
    val p = ChiNetworkParams.fromIr(ir1)
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut, p)
      dut.io.rn(0).req.bits.tgtId.poke(2.U)
      dut.io.rn(0).req.bits.txnId.poke(9.U)
      dut.io.rn(0).req.valid.poke(true.B)
      dut.clock.step(4)
      dut.io.sn(0).req.valid.expect(true.B)
      dut.io.sn(0).req.bits.txnId.expect(9.U)
    }
  }

  "ChiNetworkTop with SAM" should "answer an unmapped address NDERR locally (HW-13/HW-05)" in {
    val p = ChiNetworkParams.fromIr(ir)
    p.sam should not be empty
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut, p)
      // 0x100000 lies outside both 4K slave windows -> SAM miss.
      dut.io.rn(0).req.bits.addr.poke(0x100000L.U)
      dut.io.rn(0).req.bits.txnId.poke(5.U)
      dut.io.rn(0).req.bits.srcId.poke(0.U)
      dut.io.rn(0).req.valid.poke(true.B)
      var n = 0
      while (!dut.io.rn(0).rsp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.rn(0).rsp.valid.expect(true.B)
      dut.io.rn(0).rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.rn(0).rsp.bits.respErr.expect(ChiRespErr.NDERR.U)
      dut.io.rn(0).rsp.bits.txnId.expect(5.U)
      // The miss must never reach the fabric.
      dut.io.sn(0).req.valid.expect(false.B)
      dut.io.sn(1).req.valid.expect(false.B)
    }
  }

  it should "decode a mapped address to the SAM target TgtID (HW-13)" in {
    val p = ChiNetworkParams.fromIr(ir)
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut, p)
      // Second 4K window maps to sn1 (NodeID 3); requester drives no TgtID.
      dut.io.rn(0).req.bits.addr.poke(0x1000L.U)
      dut.io.rn(0).req.bits.tgtId.poke(0.U)
      dut.io.rn(0).req.bits.txnId.poke(8.U)
      dut.io.rn(0).req.valid.poke(true.B)
      var n = 0
      while (!dut.io.sn(1).req.valid.peek().litToBoolean && n < 60) { dut.clock.step(); n += 1 }
      dut.io.sn(1).req.valid.expect(true.B)
      dut.io.sn(1).req.bits.txnId.expect(8.U)
    }
  }

  "ChiNetworkTop with per-RN SAM" should "filter per source and NDERR forbid pairs (A4)" in {
    val base = ChiNetworkParams.fromIr(ir)
    // RN0: 0x1000 -> sn1(3); RN1: 0x1000 -> sn0(2), 0x2000 unmapped (forbid).
    val p = base.copy(samByRn = Seq(
      Seq(ChiSamEntry(0x1000L, 0x1000L, 3)),
      Seq(ChiSamEntry(0x1000L, 0x1000L, 2))))
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut, p)
      dut.io.rn(0).req.bits.addr.poke(0x1000L.U)
      dut.io.rn(0).req.bits.txnId.poke(20.U); dut.io.rn(0).req.bits.srcId.poke(0.U)
      dut.io.rn(0).req.valid.poke(true.B)
      var n = 0
      while (!dut.io.sn(1).req.valid.peek().litToBoolean && n < 60) { dut.clock.step(); n += 1 }
      dut.io.sn(1).req.valid.expect(true.B); dut.io.sn(1).req.bits.txnId.expect(20.U)
      dut.io.rn(0).req.valid.poke(false.B); dut.clock.step()

      dut.io.rn(1).req.bits.addr.poke(0x1000L.U)
      dut.io.rn(1).req.bits.txnId.poke(21.U); dut.io.rn(1).req.bits.srcId.poke(1.U)
      dut.io.rn(1).req.valid.poke(true.B)
      n = 0
      while (!dut.io.sn(0).req.valid.peek().litToBoolean && n < 60) { dut.clock.step(); n += 1 }
      dut.io.sn(0).req.valid.expect(true.B); dut.io.sn(0).req.bits.txnId.expect(21.U)
      dut.io.rn(1).req.valid.poke(false.B); dut.clock.step()

      // Same address 0x2000 is forbidden for RN1 (unmapped) -> local NDERR.
      dut.io.rn(1).req.bits.addr.poke(0x2000L.U)
      dut.io.rn(1).req.bits.txnId.poke(22.U)
      dut.io.rn(1).req.valid.poke(true.B)
      n = 0
      while (!dut.io.rn(1).rsp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.rn(1).rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.rn(1).rsp.bits.respErr.expect(ChiRespErr.NDERR.U)
      dut.io.rn(1).rsp.bits.txnId.expect(22.U)
    }
  }
}