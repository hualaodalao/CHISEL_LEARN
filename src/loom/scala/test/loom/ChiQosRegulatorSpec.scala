package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiQosRegulatorSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val c = ChiConfig() // datWidth 256 => 32 bytes/flit

  private def drain(bytesPerCycle: Double, sat: Int, n: Int): (Seq[Int], Int) = {
    var got = scala.collection.mutable.ArrayBuffer.empty[Int]
    var cyc = 0
    simulate(new ChiQosRegulator(c, bytesPerCycle, sat)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out.ready.poke(true.B); dut.io.in.valid.poke(true.B)
      var sent = 0
      while (got.length < n && cyc < 800) {
        dut.io.in.bits.txnId.poke(sent.U)
        val inFire = dut.io.in.ready.peek().litToBoolean
        if (dut.io.out.valid.peek().litToBoolean)
          got += dut.io.out.bits.txnId.peek().litValue.toInt
        dut.clock.step(); cyc += 1
        if (inFire) sent += 1
      }
      sent shouldBe n // no flit dropped: every accepted flit is forwarded
    }
    (got.toSeq, cyc)
  }

  "ChiQosRegulator fixed rate" should "converge to bytesPerCycle and forward in order (HW-10)" in {
    // 8 B/cycle / 32 B per flit = 1 flit per 4 cycles.
    val (got, cyc) = drain(8.0, 0, 16)
    got shouldBe (0 until 16)
    cyc should be >= 56 // rate-limited (~4 cycles/flit), not free-running
    cyc should be <= 80
  }

  it should "scale with the declared rate" in {
    // 32 B/cycle = 1 flit/cycle: 16 flits drain much faster than the 8 B/cycle case.
    val (gotFast, cycFast) = drain(32.0, 0, 16)
    gotFast shouldBe (0 until 16)
    val (_, cycSlow) = drain(8.0, 0, 16)
    cycFast should be < cycSlow
    cycFast should be <= 24
  }

  it should "allow a burst up to saturationBytes then settle to rate" in {
    // saturation 128 B = 4 flits of burst allowance; still no drop, in order.
    val (got, _) = drain(8.0, 128, 24)
    got shouldBe (0 until 24)
  }
}