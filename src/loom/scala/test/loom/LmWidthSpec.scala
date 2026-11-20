package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmWidthSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private def init(dut: LmWidthAdapter): Unit = {
    dut.io.in.valid.poke(false.B)
    dut.io.in.bits.data.poke(0.U)
    dut.io.in.bits.last.poke(false.B)
    dut.io.out.ready.poke(true.B)
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  behavior of "LmWidthAdapter"

  it should "downsize 256 to 128 with two beats per input" in {
    val n = 10
    val mask128 = (BigInt(1) << 128) - 1
    val words = (0 until n).map(i =>
      (BigInt(i + 1) << 128) | (BigInt(0x1000) + i))
    val expected = words.zipWithIndex.flatMap { case (w, i) =>
      Seq((w & mask128, false), ((w >> 128) & mask128, i == n - 1))
    }

    simulate(new LmWidthAdapter(256, 128)) { dut =>
      init(dut)
      var sent = 0
      var got = Vector.empty[(BigInt, Boolean)]
      while (got.size < 2 * n) {
        if (sent < n) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(words(sent).U)
          dut.io.in.bits.last.poke((sent == n - 1).B)
        } else {
          dut.io.in.valid.poke(false.B)
        }
        val accept = dut.io.in.ready.peek().litValue == 1
        if (dut.io.out.valid.peek().litValue == 1 && dut.io.out.ready.peek().litValue == 1) {
          got :+= (dut.io.out.bits.data.peek().litValue,
            dut.io.out.bits.last.peek().litValue == 1)
        }
        dut.clock.step()
        if (accept && sent < n) sent += 1
      }
      got shouldBe expected
    }
  }

  it should "upsize 128 to 256 by packing two beats" in {
    val n = 20
    val words = (0 until n).map(i => BigInt(i + 1))
    val expected = (0 until n / 2).map { k =>
      (BigInt(k) == n / 2 - 1,
        (words(2 * k + 1) << 128) | words(2 * k))
    }

    simulate(new LmWidthAdapter(128, 256)) { dut =>
      init(dut)
      var sent = 0
      var got = Vector.empty[(Boolean, BigInt)]
      while (got.size < n / 2) {
        if (sent < n) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(words(sent).U)
          dut.io.in.bits.last.poke((sent == n - 1).B)
        } else {
          dut.io.in.valid.poke(false.B)
        }
        val accept = dut.io.in.ready.peek().litValue == 1
        if (dut.io.out.valid.peek().litValue == 1 && dut.io.out.ready.peek().litValue == 1) {
          got :+= (dut.io.out.bits.last.peek().litValue == 1,
            dut.io.out.bits.data.peek().litValue)
        }
        dut.clock.step()
        if (accept && sent < n) sent += 1
      }
      got shouldBe expected
    }
  }

  it should "not lose beats under output backpressure" in {
    val n = 6
    val mask128 = (BigInt(1) << 128) - 1
    val words = (0 until n).map(i => (BigInt(i + 7) << 128) | BigInt(i))
    val expected = words.zipWithIndex.flatMap { case (w, i) =>
      Seq((w & mask128, false), ((w >> 128) & mask128, i == n - 1))
    }

    simulate(new LmWidthAdapter(256, 128)) { dut =>
      init(dut)
      var sent = 0
      var got = Vector.empty[(BigInt, Boolean)]
      var cycle = 0
      while (got.size < 2 * n) {
        dut.io.out.ready.poke((cycle % 3 != 0).B)
        if (sent < n) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(words(sent).U)
          dut.io.in.bits.last.poke((sent == n - 1).B)
        } else {
          dut.io.in.valid.poke(false.B)
        }
        val accept = dut.io.in.ready.peek().litValue == 1
        if (dut.io.out.valid.peek().litValue == 1 && dut.io.out.ready.peek().litValue == 1) {
          got :+= (dut.io.out.bits.data.peek().litValue,
            dut.io.out.bits.last.peek().litValue == 1)
        }
        dut.clock.step()
        if (accept && sent < n) sent += 1
        cycle += 1
      }
      got shouldBe expected
    }
  }
}
