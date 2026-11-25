package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiDvmNetworkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val c = ChiConfig()
  // One router, two RN-D (NodeID 0/1) + one MN (NodeID 2), all local ports.
  private val rn0 = ChiNetNode(0, ChiNodeKind.RnD, 0, 0)
  private val rn1 = ChiNetNode(1, ChiNodeKind.RnD, 0, 1)
  private val mn = ChiNetNode(2, ChiNodeKind.Mn, 0, 2)
  private val route = Seq.tabulate(c.maxNodes)(id => if (id < 3) id else 0)
  private val r0 = ChiNetRouter(0, "r0", nLocal = 3, nLink = 0, route, Seq.empty)
  private val p = ChiNetworkParams("dvmnet", c, Seq(rn0, rn1), Seq.empty, Seq(r0),
    Seq.empty, snpEnable = true, queueDepth = 2, hn = Seq.empty, mn = Seq(mn))

  private def init(dut: ChiNetworkTop): Unit = {
    for (i <- 0 until p.nRn) {
      dut.io.rn(i).req.valid.poke(false.B)
      dut.io.rn(i).rsp.ready.poke(true.B)
      dut.io.rn(i).dat.ready.poke(true.B)
      dut.io.rn(i).snp.ready.poke(true.B)
      dut.io.rn(i).txRsp.valid.poke(false.B)
      dut.io.rn(i).txDat.valid.poke(false.B)
    }
    dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
  }

  private def waitUntil(dut: ChiNetworkTop)(cond: => Bool): Unit = {
    var n = 0
    while (!cond.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
    cond.expect(true.B)
  }

  "ChiNetworkTop with MN" should "run DVM end-to-end: DVMOp -> broadcast -> SnpResp -> Comp" in {
    simulate(new ChiNetworkTop(p)) { dut =>
      init(dut)
      // RN-D 0 issues DVMOp to MN (NodeID 2).
      dut.io.rn(0).req.bits.opcode.poke(ChiOp.DVMOp.U)
      dut.io.rn(0).req.bits.tgtId.poke(2.U)
      dut.io.rn(0).req.bits.txnId.poke(4.U)
      dut.io.rn(0).req.bits.srcId.poke(0.U)
      dut.io.rn(0).req.valid.poke(true.B)
      waitUntil(dut)(dut.io.rn(0).rsp.valid)
      dut.io.rn(0).rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.io.rn(0).req.valid.poke(false.B)
      dut.clock.step() // consume DBIDResp -> MN payload

      // Payload DAT to MN.
      dut.io.rn(0).txDat.bits.data.poke(0x1000L.U)
      dut.io.rn(0).txDat.bits.tgtId.poke(2.U)
      dut.io.rn(0).txDat.bits.txnId.poke(4.U)
      dut.io.rn(0).txDat.valid.poke(true.B)
      dut.clock.step(2)
      dut.io.rn(0).txDat.valid.poke(false.B)

      // MN serializes SnpDVMOp to RN-D 0 then RN-D 1.
      waitUntil(dut)(dut.io.rn(0).snp.valid)
      dut.io.rn(0).snp.bits.opcode.expect(ChiOp.SnpDVMOp.U)
      dut.clock.step()
      waitUntil(dut)(dut.io.rn(1).snp.valid)
      dut.io.rn(1).snp.bits.tgtId.expect(1.U)
      dut.clock.step() // both broadcast -> collect

      // RN-D 0/1 return SnpResp to MN.
      dut.io.rn(0).txRsp.bits.opcode.poke(ChiOp.RspSnpResp.U)
      dut.io.rn(0).txRsp.bits.srcId.poke(0.U)
      dut.io.rn(0).txRsp.bits.tgtId.poke(2.U)
      dut.io.rn(0).txRsp.valid.poke(true.B)
      dut.io.rn(1).txRsp.bits.opcode.poke(ChiOp.RspSnpResp.U)
      dut.io.rn(1).txRsp.bits.srcId.poke(1.U)
      dut.io.rn(1).txRsp.bits.tgtId.poke(2.U)
      dut.io.rn(1).txRsp.valid.poke(true.B)
      waitUntil(dut)(dut.io.rn(0).rsp.valid)
      dut.io.rn(0).txRsp.valid.poke(false.B)
      dut.io.rn(1).txRsp.valid.poke(false.B)
      dut.io.rn(0).rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.rn(0).rsp.bits.txnId.expect(4.U)
    }
  }
}