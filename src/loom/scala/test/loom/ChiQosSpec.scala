package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiQosSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  "ChiQosMap" should "generalize vc = class*nHalf + half with derived vcWidth" in {
    val qm = new ChiQosMap(ChiConfig())
    qm.classOf(12) shouldBe 0; qm.classOf(5) shouldBe 1; qm.classOf(1) shouldBe 2
    qm.nVc shouldBe 6; qm.vcWidth shouldBe 3
    qm.vcOf(12, 0) shouldBe 0; qm.vcOf(12, 1) shouldBe 1
    qm.vcOf(5, 0) shouldBe 2; qm.vcOf(1, 1) shouldBe 5
    qm.weightOf(0) shouldBe 4; qm.weightOf(2) shouldBe 1
  }

  it should "parameterize nHalf (not hardcoded x2) and thresholds" in {
    val q4 = new ChiQosMap(ChiConfig(nHalf = 4))
    q4.nVc shouldBe 12; q4.vcWidth shouldBe 4
    q4.vcOf(12, 3) shouldBe 3; q4.vcOf(1, 0) shouldBe 8
    val qt = new ChiQosMap(ChiConfig(qosCtlThreshold = 10, qosLatThreshold = 5))
    qt.classOf(10) shouldBe 0; qt.classOf(9) shouldBe 1; qt.classOf(5) shouldBe 1; qt.classOf(4) shouldBe 2
  }

  "ChiConfig QoS" should "reject invalid weights/thresholds/nHalf" in {
    an[IllegalArgumentException] should be thrownBy ChiConfig(qosWeights = Seq(4, 2))
    an[IllegalArgumentException] should be thrownBy ChiConfig(qosWeights = Seq(4, 0, 1))
    an[IllegalArgumentException] should be thrownBy ChiConfig(qosCtlThreshold = 4, qosLatThreshold = 8)
    an[IllegalArgumentException] should be thrownBy ChiConfig(nHalf = 0)
  }

  private class QFlit extends Bundle {
    val tgt = UInt(2.W)
    val payload = UInt(8.W)
  }

  "ChiChannelRouter cut" should "flip dateline half 0->1 on a cut output, never back" in {
    simulate(new ChiChannelRouter(new QFlit, 1, 1, Seq(0, 0, 0, 0), 2,
      (f: QFlit) => f.tgt, new ChiQosMap(ChiConfig()), cutOut = Seq(true))) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out(0).ready.poke(true.B)
      dut.io.in(0).bits.vc.poke(0.U) // ctl half0
      dut.io.in(0).bits.flit.tgt.poke(0.U)
      dut.io.in(0).valid.poke(true.B)
      var n = 0
      while (!dut.io.out(0).valid.peek().litToBoolean && n < 20) { dut.clock.step(); n += 1 }
      dut.io.out(0).valid.expect(true.B)
      dut.io.out(0).bits.vc.expect(1.U) // ctl half1
    }
  }

  it should "leave half unchanged on a non-cut output" in {
    simulate(new ChiChannelRouter(new QFlit, 1, 1, Seq(0, 0, 0, 0), 2,
      (f: QFlit) => f.tgt, new ChiQosMap(ChiConfig()), cutOut = Seq(false))) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out(0).ready.poke(true.B)
      dut.io.in(0).bits.vc.poke(0.U)
      dut.io.in(0).bits.flit.tgt.poke(0.U)
      dut.io.in(0).valid.poke(true.B)
      var n = 0
      while (!dut.io.out(0).valid.peek().litToBoolean && n < 20) { dut.clock.step(); n += 1 }
      dut.io.out(0).bits.vc.expect(0.U)
    }
  }

  "ChiChannelRouter WRR" should "give ctl class more grants than bulk (weight 4:1)" in {
    simulate(new ChiChannelRouter(new QFlit, 2, 1, Seq(0, 0, 0, 0), 4,
      (f: QFlit) => f.tgt, new ChiQosMap(ChiConfig()))) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out(0).ready.poke(true.B)
      dut.io.in(0).bits.vc.poke(0.U) // ctl
      dut.io.in(0).bits.flit.tgt.poke(0.U)
      dut.io.in(0).valid.poke(true.B)
      dut.io.in(1).bits.vc.poke(4.U) // bulk (class2 * nHalf2 + 0)
      dut.io.in(1).bits.flit.tgt.poke(0.U)
      dut.io.in(1).valid.poke(true.B)
      var ctl = 0; var bulk = 0
      for (_ <- 0 until 60) {
        if (dut.io.out(0).valid.peek().litToBoolean) {
          val v = dut.io.out(0).bits.vc.peek().litValue.toInt
          if (v / 2 == 0) ctl += 1 else if (v / 2 == 2) bulk += 1
        }
        dut.clock.step()
      }
      ctl should be > bulk * 2
      bulk should be > 0
    }
  }
}