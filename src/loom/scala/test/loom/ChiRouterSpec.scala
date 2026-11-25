package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiRouterSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private class TstFlit extends Bundle {
    val tgt = UInt(2.W)
    val payload = UInt(8.W)
  }

  private def route = Seq(0, 1, 0, 1)

  private def waitValid(dut: ChiChannelRouter[TstFlit])(cond: => Bool): Unit = {
    var n = 0
    while (!cond.peek().litToBoolean && n < 20) { dut.clock.step(); n += 1 }
    cond.expect(true.B)
  }

  behavior of "ChiChannelRouter"

  it should "route flits to the output named by TgtID" in {
    simulate(new ChiChannelRouter(new TstFlit, 2, 2, route, 2, (f: TstFlit) => f.tgt)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out(0).ready.poke(true.B); dut.io.out(1).ready.poke(true.B)

      dut.io.in(0).bits.vc.poke(0.U)
      dut.io.in(0).bits.flit.tgt.poke(1.U); dut.io.in(0).bits.flit.payload.poke(0xA1.U)
      dut.io.in(0).valid.poke(true.B)
      dut.io.in(1).bits.vc.poke(0.U)
      dut.io.in(1).bits.flit.tgt.poke(0.U); dut.io.in(1).bits.flit.payload.poke(0xB0.U)
      dut.io.in(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.in(0).valid.poke(false.B); dut.io.in(1).valid.poke(false.B)

      waitValid(dut)(dut.io.out(1).valid)
      dut.io.out(1).bits.flit.payload.expect(0xA1.U)
      waitValid(dut)(dut.io.out(0).valid)
      dut.io.out(0).bits.flit.payload.expect(0xB0.U)
    }
  }

  it should "backpressure a blocked output without stalling the other" in {
    simulate(new ChiChannelRouter(new TstFlit, 2, 2, route, 2, (f: TstFlit) => f.tgt)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.out(0).ready.poke(true.B); dut.io.out(1).ready.poke(false.B)

      dut.io.in(0).bits.vc.poke(0.U)
      dut.io.in(0).bits.flit.tgt.poke(1.U); dut.io.in(0).bits.flit.payload.poke(0xA1.U)
      dut.io.in(0).valid.poke(true.B)
      dut.io.in(1).bits.vc.poke(0.U)
      dut.io.in(1).bits.flit.tgt.poke(0.U); dut.io.in(1).bits.flit.payload.poke(0xB0.U)
      dut.io.in(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.in(0).valid.poke(false.B); dut.io.in(1).valid.poke(false.B)

      // out1 blocked: out0 still drains (out1 flit stages at its output pipe).
      waitValid(dut)(dut.io.out(0).valid)
      dut.io.out(0).bits.flit.payload.expect(0xB0.U)

      // Release out1: the held flit now crosses.
      dut.io.out(1).ready.poke(true.B)
      waitValid(dut)(dut.io.out(1).valid)
      dut.io.out(1).bits.flit.payload.expect(0xA1.U)
    }
  }

  private def rt(pairs: (Int, Int)*): Seq[Int] = {
    val a = Array.fill(128)(0)
    pairs.foreach { case (t, p) => a(t) = p }
    a.toSeq
  }

  behavior of "ChiRouter4"

  it should "switch REQ forward and DAT backward independently by TgtID" in {
    val c = ChiConfig()
    val reqRoute = rt(2 -> 2, 3 -> 3)
    val rspRoute = rt(0 -> 0, 1 -> 1)
    val datRoute = rt(0 -> 0, 1 -> 1)
    simulate(new ChiRouter4(c, 4, reqRoute, rspRoute, rt(), datRoute, 2, snpEnable = false)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      for (p <- 0 until 4) {
        dut.io.port(p).rxReq.valid.poke(false.B)
        dut.io.port(p).rxRsp.valid.poke(false.B)
        dut.io.port(p).rxSnp.valid.poke(false.B)
        dut.io.port(p).rxDat.valid.poke(false.B)
        dut.io.port(p).txReq.ready.poke(true.B)
        dut.io.port(p).txRsp.ready.poke(true.B)
        dut.io.port(p).txSnp.ready.poke(true.B)
        dut.io.port(p).txDat.ready.poke(true.B)
      }

      dut.io.port(0).rxReq.bits.vc.poke(0.U)
      dut.io.port(0).rxReq.bits.flit.tgtId.poke(2.U)
      dut.io.port(0).rxReq.bits.flit.txnId.poke(5.U)
      dut.io.port(0).rxReq.valid.poke(true.B)
      dut.io.port(2).rxDat.bits.vc.poke(0.U)
      dut.io.port(2).rxDat.bits.flit.tgtId.poke(0.U)
      dut.io.port(2).rxDat.bits.flit.data.poke(0xDEADBEEFL.U)
      dut.io.port(2).rxDat.valid.poke(true.B)
      dut.clock.step()
      dut.io.port(0).rxReq.valid.poke(false.B)
      dut.io.port(2).rxDat.valid.poke(false.B)

      var n = 0
      while (!dut.io.port(2).txReq.valid.peek().litToBoolean && n < 20) { dut.clock.step(); n += 1 }
      dut.io.port(2).txReq.valid.expect(true.B)
      dut.io.port(2).txReq.bits.flit.txnId.expect(5.U)
      n = 0
      while (!dut.io.port(0).txDat.valid.peek().litToBoolean && n < 20) { dut.clock.step(); n += 1 }
      dut.io.port(0).txDat.valid.expect(true.B)
      dut.io.port(0).txDat.bits.flit.data.expect(0xDEADBEEFL.U)
      dut.io.port(0).txSnp.valid.expect(false.B)
    }
  }
}