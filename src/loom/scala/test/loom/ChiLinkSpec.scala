package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiLinkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  behavior of "ChiTxCredit"

  it should "grant, exhaust and resume flit credits" in {
    simulate(new ChiTxCredit(4)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.lcrdv.poke(false.B); dut.io.send.poke(false.B)
      dut.io.canSend.expect(false.B); dut.io.count.expect(0.U)

      dut.io.lcrdv.poke(true.B); dut.clock.step(3); dut.io.lcrdv.poke(false.B)
      dut.io.count.expect(3.U); dut.io.canSend.expect(true.B)

      dut.io.send.poke(true.B); dut.clock.step(3); dut.io.send.poke(false.B)
      dut.io.count.expect(0.U); dut.io.canSend.expect(false.B)

      dut.io.lcrdv.poke(true.B); dut.clock.step(); dut.io.lcrdv.poke(false.B)
      dut.io.count.expect(1.U); dut.io.canSend.expect(true.B)
    }
  }

  behavior of "ChiLinkFsm"

  it should "walk STOP->ACTIVATE->RUN->DEACTIVATE->STOP" in {
    simulate(new ChiLinkFsm) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.txSactive.poke(false.B); dut.io.rxSactive.poke(false.B)
      dut.io.txAck.poke(false.B); dut.io.rxReq.poke(false.B)
      dut.io.state.expect(0.U); dut.io.run.expect(false.B); dut.io.txReq.expect(false.B)

      dut.io.txSactive.poke(true.B); dut.clock.step()
      dut.io.state.expect(1.U); dut.io.txReq.expect(true.B); dut.io.rxAck.expect(true.B)

      dut.io.txAck.poke(true.B); dut.io.rxReq.poke(true.B); dut.clock.step()
      dut.io.state.expect(2.U); dut.io.run.expect(true.B)

      dut.io.txSactive.poke(false.B); dut.clock.step()
      dut.io.state.expect(3.U); dut.io.run.expect(false.B); dut.io.txReq.expect(false.B)

      dut.io.txAck.poke(false.B); dut.io.rxReq.poke(false.B); dut.clock.step()
      dut.io.state.expect(0.U)
    }
  }

  behavior of "ChiTxLink"

  it should "pass flits only in RUN with a held credit" in {
    simulate(new ChiTxLink(UInt(8.W), 4)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.enq.valid.poke(false.B); dut.io.deq.ready.poke(true.B)
      dut.io.lcrdv.poke(false.B)
      dut.io.txSactive.poke(false.B); dut.io.rxSactive.poke(false.B)
      dut.io.txAck.poke(false.B); dut.io.rxReq.poke(false.B)

      // STOP blocks even a valid flit.
      dut.io.enq.valid.poke(true.B); dut.io.enq.bits.poke(0xAB.U)
      dut.clock.step()
      dut.io.deq.valid.expect(false.B); dut.io.run.expect(false.B)
      dut.io.enq.valid.poke(false.B)

      // Activate to RUN.
      dut.io.txSactive.poke(true.B); dut.clock.step()
      dut.io.txAck.poke(true.B); dut.io.rxReq.poke(true.B); dut.clock.step()
      dut.io.run.expect(true.B)
      dut.io.deq.valid.expect(false.B) // no credit yet

      // Grant two credits while idle.
      dut.io.lcrdv.poke(true.B); dut.clock.step(2); dut.io.lcrdv.poke(false.B)
      dut.io.creditCount.expect(2.U)

      // Now the flit crosses and consumes one credit.
      dut.io.enq.valid.poke(true.B); dut.io.enq.bits.poke(0xAB.U)
      dut.io.deq.valid.expect(true.B); dut.io.deq.bits.expect(0xAB.U)
      dut.clock.step()
      dut.io.creditCount.expect(1.U)
    }
  }

  behavior of "ChiAsyncLink"

  it should "cross flits between clock domains in order with bounded credit" in {
    simulate(new ChiAsyncLink(UInt(8.W), 4)) { dut =>
      dut.io.dstReset.poke(true.B)
      dut.reset.poke(true.B)
      // Toggle dstClk during reset so the destination-domain registers get a
      // reset edge (withClockAndReset needs a dst clock edge to clear).
      for (i <- 0 until 4) { dut.io.dstClk.poke((i % 2 == 0).B); dut.clock.step() }
      dut.reset.poke(false.B)
      dut.io.dstReset.poke(false.B)
      dut.io.in.valid.poke(true.B); dut.io.out.ready.poke(true.B)

      var sent = 0
      var dst = false
      val got = scala.collection.mutable.ArrayBuffer.empty[Int]
      for (_ <- 0 until 300 if got.length < 8) {
        dut.io.dstClk.poke(dst.B)
        dut.io.in.bits.poke(sent.U)
        val inFire = dut.io.in.ready.peek().litToBoolean && sent < 8
        // Pop fires on the dst rising edge (dst==true this step); sample once.
        if (dst && dut.io.out.valid.peek().litToBoolean)
          got += dut.io.out.bits.peek().litValue.toInt
        dut.io.creditUsed.peek().litValue.toInt should be <= 4
        dut.clock.step()
        if (inFire) sent += 1
        dst = !dst
      }
      got.toSeq shouldBe (0 until 8)
    }
  }
}