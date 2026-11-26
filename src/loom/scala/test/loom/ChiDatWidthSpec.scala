package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiDatWidthSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private class Net(inW: Int, outW: Int) extends Module {
    val ci = ChiConfig(datWidth = inW)
    val co = ChiConfig(datWidth = outW)
    val io = IO(new Bundle {
      val in = Flipped(Decoupled(new ChiDatFlit(ci)))
      val out = Decoupled(new ChiDatFlit(co))
    })
    val dut = Module(new ChiDatWidth(ci, co))
    io.in <> dut.io.in
    dut.io.out <> io.out
  }

  private def convert(inW: Int, outW: Int, word: BigInt): Unit = {
    val ci = ChiConfig(datWidth = inW)
    val nIn = ChiWidths.flitCount(64, inW)
    val idIn = inW / 128
    val nOut = ChiWidths.flitCount(64, outW)
    simulate(new Net(inW, outW)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out.ready.poke(true.B)
      dut.io.in.valid.poke(false.B)
      var outWord = BigInt(0)
      var got = 0
      var sent = 0
      var n = 0
      while (got < nOut && n < 400) {
        if (sent < nIn) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.data.poke(((word >> (sent * inW)) & ((BigInt(1) << inW) - 1)).U)
          dut.io.in.bits.dataId.poke((sent * idIn).U)
          dut.io.in.bits.opcode.poke(ChiOp.DatCompData.U)
          dut.io.in.bits.txnId.poke(3.U); dut.io.in.bits.tgtId.poke(1.U)
        } else {
          dut.io.in.valid.poke(false.B)
        }
        if (dut.io.in.ready.peek().litToBoolean && sent < nIn) sent += 1
        if (dut.io.out.valid.peek().litToBoolean) {
          val d = dut.io.out.bits.data.peek().litValue
          val id = dut.io.out.bits.dataId.peek().litValue.toInt
          outWord |= d << (id * 128)
          dut.io.out.bits.txnId.expect(3.U)
          dut.io.out.bits.tgtId.expect(1.U)
          got += 1
        }
        dut.clock.step(); n += 1
      }
      assert(got == nOut, s"expected $nOut output flits, got $got")
      assert(outWord == word, s"$inW->$outW mismatch: got ${outWord.toString(16)} want ${word.toString(16)}")
    }
  }

  "ChiDatWidth" should "upsize 128->256 bit-exact" in {
    convert(128, 256, BigInt("deadbeef0123456789abcdef00112233", 16) << 256 | BigInt("cafebabedeadbeef0123456789abcdef", 16))
  }
  it should "downsize 256->128 bit-exact" in {
    convert(256, 128, BigInt("deadbeef0123456789abcdef00112233", 16) << 256 | BigInt("cafebabedeadbeef0123456789abcdef", 16))
  }
  it should "downsize 512->128 bit-exact" in {
    convert(512, 128, (BigInt(0xAAAA) << 384) | (BigInt(0xBBBB) << 256) | (BigInt(0xCCCC) << 128) | BigInt(0xDDDD))
  }
  it should "upsize 128->512 bit-exact" in {
    convert(128, 512, (BigInt(0xAAAA) << 384) | (BigInt(0xBBBB) << 256) | (BigInt(0xCCCC) << 128) | BigInt(0xDDDD))
  }
}