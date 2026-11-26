package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmPipeSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private def init(dut: LmPipe[UInt]): Unit = {
    dut.io.in.valid.poke(false.B)
    dut.io.in.bits.poke(0.U)
    dut.io.out.ready.poke(true.B)
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  behavior of "LmPipe"

  it should "carry a stream for 0 to 4 stages" in {
    for (stages <- 0 to 4) {
      simulate(new LmPipe(UInt(32.W), stages)) { dut =>
        init(dut)
        var sent = 0
        var got = Vector.empty[BigInt]
        while (got.size < 10) {
          if (sent < 10) {
            dut.io.in.valid.poke(true.B)
            dut.io.in.bits.poke(sent.U)
          } else {
            dut.io.in.valid.poke(false.B)
          }
          val accept = dut.io.in.ready.peek().litValue == 1
          if (dut.io.out.valid.peek().litValue == 1 && dut.io.out.ready.peek().litValue == 1) {
            got :+= dut.io.out.bits.peek().litValue
          }
          dut.clock.step()
          if (accept && sent < 10) sent += 1
        }
        got shouldBe (0 until 10).map(i => BigInt(i))
      }
    }
  }

  it should "be transparent under output backpressure" in {
    simulate(new LmPipe(UInt(32.W), 2)) { dut =>
      init(dut)
      var sent = 0
      var got = Vector.empty[BigInt]
      var cycle = 0
      while (got.size < 10) {
        dut.io.out.ready.poke((cycle % 3 != 0).B)
        if (sent < 10) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.poke(sent.U)
        } else {
          dut.io.in.valid.poke(false.B)
        }
        val accept = dut.io.in.ready.peek().litValue == 1
        if (dut.io.out.valid.peek().litValue == 1 && dut.io.out.ready.peek().litValue == 1) {
          got :+= dut.io.out.bits.peek().litValue
        }
        dut.clock.step()
        if (accept && sent < 10) sent += 1
        cycle += 1
      }
      got shouldBe (0 until 10).map(i => BigInt(i))
    }
  }
}
