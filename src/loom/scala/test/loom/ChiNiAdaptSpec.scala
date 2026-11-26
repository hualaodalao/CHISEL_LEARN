package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiNiAdaptSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  // Drive input beats (data,last), collect output beats; out.ready held true.
  private def convert(inW: Int, outW: Int, in: Seq[(BigInt, Boolean)], nOut: Int): Seq[(BigInt, Boolean)] = {
    val got = scala.collection.mutable.ArrayBuffer.empty[(BigInt, Boolean)]
    simulate(new ChiNiWidth(inW, outW)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out.ready.poke(true.B)
      var idx = 0; var cyc = 0
      while (got.length < nOut && cyc < 400) {
        if (idx < in.length) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(in(idx)._1.U)
          dut.io.in.bits.last.poke(in(idx)._2.B)
        } else dut.io.in.valid.poke(false.B)
        if (dut.io.out.valid.peek().litToBoolean)
          got += ((dut.io.out.bits.data.peek().litValue, dut.io.out.bits.last.peek().litToBoolean))
        val inFire = idx < in.length && dut.io.in.ready.peek().litToBoolean
        dut.clock.step(); cyc += 1
        if (inFire) idx += 1
      }
    }
    got.toSeq
  }

  "ChiNiWidth downsize 256->128" should "split a wide beat low-first, last on final (HW-12)" in {
    val out = convert(256, 128, Seq((BigInt(0xAAAA) << 128 | BigInt(0xBBBB), true)), 2)
    out shouldBe Seq((BigInt(0xBBBB), false), (BigInt(0xAAAA), true))
  }

  "ChiNiWidth upsize 128->256" should "pack two narrow beats low-first into one wide beat" in {
    val out = convert(128, 256, Seq((BigInt(0xBBBB), false), (BigInt(0xAAAA), true)), 1)
    out shouldBe Seq((BigInt(0xAAAA) << 128 | BigInt(0xBBBB), true))
  }

  "ChiNiWidth downsize 512->128" should "split into four beats bit-exact" in {
    val word = BigInt(0x1111) << 384 | BigInt(0x2222) << 256 | BigInt(0x3333) << 128 | BigInt(0x4444)
    val out = convert(512, 128, Seq((word, true)), 4)
    out shouldBe Seq((BigInt(0x4444), false), (BigInt(0x3333), false),
      (BigInt(0x2222), false), (BigInt(0x1111), true))
  }

  "ChiNiWidth upsize 128->512" should "pack four narrow beats bit-exact" in {
    val word = BigInt(0x1111) << 384 | BigInt(0x2222) << 256 | BigInt(0x3333) << 128 | BigInt(0x4444)
    val out = convert(128, 512, Seq((BigInt(0x4444), false), (BigInt(0x3333), false),
      (BigInt(0x2222), false), (BigInt(0x1111), true)), 1)
    out shouldBe Seq((word, true))
  }

  "ChiNiWidth matched 256->256" should "pass through with last preserved" in {
    val out = convert(256, 256, Seq((BigInt(0x1234), false), (BigInt(0x5678), true)), 2)
    out shouldBe Seq((BigInt(0x1234), false), (BigInt(0x5678), true))
  }
}