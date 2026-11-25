package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiDvmSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  "ChiDvmMsg" should "expose the full message family" in {
    ChiDvmMsg.families shouldBe Seq("TLBI", "BPI", "VIC", "SYNC")
    ChiDvmMsg.isSync("SYNC") shouldBe true
    ChiDvmMsg.isSync("TLBI") shouldBe false
    ChiDvmMsg.tlbiSubtypes should contain("VA")
  }

  "ChiDvmMn" should "serialize SnpDVMOp, collect SnpResp by SrcID, then Comp" in {
    val c = ChiConfig()
    simulate(new ChiDvmMn(c, mnNodeId = 3, targetNodeIds = Seq(1, 2))) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.req.valid.poke(false.B); dut.io.rsp.ready.poke(true.B)
      dut.io.dat.valid.poke(false.B)
      dut.io.snp.ready.poke(true.B)
      dut.io.snpResp.valid.poke(false.B)

      dut.io.req.bits.opcode.poke(ChiOp.DVMOp.U)
      dut.io.req.bits.txnId.poke(4.U); dut.io.req.bits.srcId.poke(0.U)
      dut.io.req.valid.poke(true.B); dut.clock.step(); dut.io.req.valid.poke(false.B)

      dut.io.rsp.valid.expect(true.B)
      dut.io.rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.clock.step() // grant -> payload

      dut.io.dat.bits.data.poke(0x1000L.U)
      dut.io.dat.valid.poke(true.B); dut.clock.step(); dut.io.dat.valid.poke(false.B)

      // Serialized broadcast: target 1 then target 2.
      dut.io.snp.valid.expect(true.B)
      dut.io.snp.bits.opcode.expect(ChiOp.SnpDVMOp.U)
      dut.io.snp.bits.tgtId.expect(1.U)
      dut.clock.step()
      dut.io.snp.bits.tgtId.expect(2.U)
      dut.clock.step() // both sent -> collect
      dut.io.snp.valid.expect(false.B)

      // Collect SnpResp by SrcID (one per target).
      dut.io.snpResp.bits.opcode.poke(ChiOp.RspSnpResp.U)
      dut.io.snpResp.bits.srcId.poke(1.U)
      dut.io.snpResp.valid.poke(true.B); dut.clock.step()
      dut.io.snpResp.bits.srcId.poke(2.U); dut.clock.step()
      dut.io.snpResp.valid.poke(false.B)

      dut.io.rsp.valid.expect(true.B)
      dut.io.rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.rsp.bits.txnId.expect(4.U)
      dut.clock.step()
      dut.io.rsp.valid.expect(false.B)
    }
  }
}