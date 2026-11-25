package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiAtomicSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  "ChiAtomicOp.ref" should "match width-aware golden anchors" in {
    ChiAtomicOp.ref("Add", 0xFF, 1, 8) shouldBe 0
    ChiAtomicOp.ref("Smax", 0xFF, 1, 8) shouldBe 1
    ChiAtomicOp.ref("Smin", 0xFF, 1, 8) shouldBe 0xFF
    ChiAtomicOp.ref("Umax", 0xFF, 1, 8) shouldBe 0xFF
    ChiAtomicOp.ref("Umin", 0xFF, 1, 8) shouldBe 1
    ChiAtomicOp.ref("Clr", 0xFF, 0x0F, 8) shouldBe 0xF0
    ChiAtomicOp.ref("Set", 0xF0, 0x0F, 8) shouldBe 0xFF
    ChiAtomicOp.ref("Eor", 0xFF, 0x0F, 8) shouldBe 0xF0
  }

  "ChiAtomicAlu" should "compute all 8 ops against the reference" in {
    simulate(new ChiAtomicAlu(8)) { dut =>
      for ((op, idx) <- ChiAtomicOp.ops.zipWithIndex) {
        dut.io.op.poke(idx.U)
        dut.io.a.poke(0xF0.U)
        dut.io.b.poke(0x0F.U)
        dut.io.y.expect(ChiAtomicOp.ref(op, 0xF0, 0x0F, 8).U)
      }
    }
  }

  "ChiAtomicEngine" should "RMW returning the old value and reject Device" in {
    simulate(new ChiAtomicEngine(32)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.cmd.bits.op.poke(ChiAtomicOp.code("Add").U)
      dut.io.cmd.bits.device.poke(false.B)

      dut.io.cmd.bits.operand.poke(5.U)
      dut.io.cmd.valid.poke(true.B); dut.clock.step(); dut.io.cmd.valid.poke(false.B)
      dut.io.retValid.expect(true.B)
      dut.io.retVal.expect(0.U); dut.io.respErr.expect(ChiRespErr.OK.U)
      dut.io.memWord.expect(5.U)

      dut.io.cmd.bits.operand.poke(3.U)
      dut.io.cmd.valid.poke(true.B); dut.clock.step(); dut.io.cmd.valid.poke(false.B)
      dut.io.retVal.expect(5.U); dut.io.memWord.expect(8.U)

      dut.io.cmd.bits.device.poke(true.B); dut.io.cmd.bits.operand.poke(100.U)
      dut.io.cmd.valid.poke(true.B); dut.clock.step(); dut.io.cmd.valid.poke(false.B)
      dut.io.respErr.expect(ChiRespErr.NDERR.U)
      dut.io.memWord.expect(8.U) // Device atomic leaves memory unchanged
    }
  }
}