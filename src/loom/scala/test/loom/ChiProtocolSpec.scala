package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiProtocolSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  behavior of "ChiIdPool (TxnID / DBID)"

  it should "exhaust IDs, backpressure, and recycle a released ID" in {
    simulate(new ChiIdPool(4, 8)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.alloc.poke(false.B); dut.io.release.poke(false.B)
      dut.io.canAlloc.expect(true.B); dut.io.outstanding.expect(0.U)
      dut.io.allocId.expect(0.U)

      dut.io.alloc.poke(true.B); dut.clock.step(4); dut.io.alloc.poke(false.B)
      dut.io.outstanding.expect(4.U); dut.io.canAlloc.expect(false.B)

      dut.io.release.poke(true.B); dut.io.releaseId.poke(1.U)
      dut.clock.step(); dut.io.release.poke(false.B)
      dut.io.outstanding.expect(3.U); dut.io.canAlloc.expect(true.B)

      dut.io.alloc.poke(true.B)
      dut.io.allocId.expect(1.U)
      dut.clock.step(); dut.io.alloc.poke(false.B)
      dut.io.outstanding.expect(4.U)
    }
  }

  behavior of "ChiRetryCtrl (P-Credit)"

  it should "RetryAck when full and PCrdGrant on release" in {
    simulate(new ChiRetryCtrl(2)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.reqAllowRetry.poke(false.B); dut.io.release.poke(false.B)
      dut.io.outstanding.expect(0.U)

      dut.io.reqAllowRetry.poke(true.B); dut.clock.step(2)
      dut.io.outstanding.expect(2.U)
      dut.io.accept.expect(false.B); dut.io.retryAck.expect(true.B)
      dut.clock.step() // owed grant registered

      dut.io.reqAllowRetry.poke(false.B); dut.io.release.poke(true.B)
      dut.io.pcrdGrant.expect(true.B)
      dut.clock.step(); dut.io.release.poke(false.B)
      dut.io.outstanding.expect(1.U)
    }
  }
}