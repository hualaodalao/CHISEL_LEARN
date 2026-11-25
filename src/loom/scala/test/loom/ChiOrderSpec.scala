package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiOrderSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  "ChiOrderRule" should "force RequestOrder on Device and no order on Normal" in {
    ChiOrderRule.orderFor(ChiMemAttr.deviceNRnE) shouldBe ChiOrder.RequestOrder
    ChiOrderRule.orderFor(ChiMemAttr.normalNonCacheable) shouldBe ChiOrder.NoOrder
  }

  "ChiOrderCtrl" should "issue ReadReceipt and close only on CompAck" in {
    val c = ChiConfig()
    simulate(new ChiOrderCtrl(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.req.valid.poke(false.B); dut.io.readReceipt.ready.poke(true.B)
      dut.io.comp.poke(false.B); dut.io.compAck.valid.poke(false.B)

      dut.io.req.bits.txnId.poke(3.U); dut.io.req.bits.srcId.poke(0.U)
      dut.io.req.bits.memAttr.poke(ChiMemAttr.deviceNRnE.U)
      dut.io.req.valid.poke(true.B); dut.clock.step(); dut.io.req.valid.poke(false.B)

      dut.io.readReceipt.valid.expect(true.B)
      dut.io.readReceipt.bits.opcode.expect(ChiOp.RspReadReceipt.U)
      dut.io.readReceipt.bits.txnId.expect(3.U)
      dut.clock.step() // receipt accepted -> wait Comp

      dut.io.done.expect(false.B)
      dut.io.comp.poke(true.B); dut.clock.step(); dut.io.comp.poke(false.B)
      dut.io.done.expect(false.B) // Comp alone must not close

      dut.io.compAck.valid.poke(true.B); dut.io.compAck.bits.txnId.poke(3.U)
      dut.io.done.expect(true.B) // CompAck closes
      dut.clock.step(); dut.io.compAck.valid.poke(false.B)
      dut.io.done.expect(false.B)
    }
  }
}