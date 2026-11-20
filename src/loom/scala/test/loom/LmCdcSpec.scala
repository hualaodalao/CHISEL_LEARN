package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class LmCdcSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private def initFifo(dut: LmAsyncFifo): Unit = {
    dut.io.push.valid.poke(false.B)
    dut.io.push.bits.poke(0.U)
    dut.io.pop.ready.poke(true.B)
    dut.io.dstClk.poke(false.B)
    dut.io.dstReset.poke(true.B)
    dut.reset.poke(true.B)
    dut.clock.step(3)
    for (_ <- 0 until 3) {
      dut.io.dstClk.poke(true.B)
      dut.clock.step()
      dut.io.dstClk.poke(false.B)
      dut.clock.step()
    }
    dut.io.dstReset.poke(false.B)
    dut.reset.poke(false.B)
  }

  private def initCredit(dut: LmCdcCredit): Unit = {
    dut.io.alloc.poke(false.B)
    dut.io.release.poke(false.B)
    dut.io.dstClk.poke(false.B)
    dut.io.dstReset.poke(true.B)
    dut.reset.poke(true.B)
    dut.clock.step(3)
    for (_ <- 0 until 3) {
      dut.io.dstClk.poke(true.B)
      dut.clock.step()
      dut.io.dstClk.poke(false.B)
      dut.clock.step()
    }
    dut.io.dstReset.poke(false.B)
    dut.reset.poke(false.B)
  }

  behavior of "LmAsyncFifo and LmCdcCredit"

  it should "carry 1000 items across a 2:1 clock ratio without loss or reordering" in {
    val n = 1000
    val q = mutable.Queue.empty[BigInt]

    simulate(new LmAsyncFifo(32, 8)) { dut =>
      initFifo(dut)
      var sent = 0
      var recv = 0
      var iter = 0

      def srcStep(): Unit = {
        if (sent < n) {
          dut.io.push.valid.poke(true.B)
          dut.io.push.bits.poke(sent.U)
        } else {
          dut.io.push.valid.poke(false.B)
        }
        val ready = dut.io.push.ready.peek().litValue == 1
        dut.clock.step()
        dut.io.push.valid.poke(false.B)
        if (sent < n && ready) {
          q.enqueue(BigInt(sent))
          sent += 1
        }
      }

      def dstStep(): Unit = {
        dut.io.push.valid.poke(false.B)
        dut.io.dstClk.poke(true.B)
        if (dut.io.pop.valid.peek().litValue == 1) {
          q.nonEmpty shouldBe true
          dut.io.pop.bits.peek().litValue shouldBe q.dequeue()
          recv += 1
        }
        dut.clock.step()
        dut.io.dstClk.poke(false.B)
        dut.clock.step()
      }

      while (recv < n) {
        srcStep()
        if (iter % 2 == 0) dstStep()
        iter += 1
        iter should be < 20000
      }
      sent shouldBe n
      recv shouldBe n
      q shouldBe empty
    }
  }

  it should "keep CDC credit conservative across 1000 returns" in {
    val depth = 8
    simulate(new LmCdcCredit(depth)) { dut =>
      initCredit(dut)
      var outstanding = 0
      var released = 0
      var allocs = 0

      def srcStep(): Unit = {
        val can = dut.io.canAlloc.peek().litValue == 1
        dut.io.alloc.poke(can.B)
        dut.clock.step()
        dut.io.alloc.poke(false.B)
        if (can) { outstanding += 1; allocs += 1 }
        dut.io.used.peek().litValue.toInt should be <= depth
      }

      def releaseStep(release: Boolean): Unit = {
        dut.io.alloc.poke(false.B)
        dut.io.release.poke(release.B)
        dut.io.dstClk.poke(true.B)
        dut.clock.step()
        dut.io.dstClk.poke(false.B)
        dut.io.release.poke(false.B)
        dut.clock.step()
        if (release) { outstanding -= 1; released += 1 }
      }

      var iter = 0
      while (released < 1000 && iter < 20000) {
        srcStep()
        if (iter % 2 == 0) releaseStep(outstanding > 0)
        iter += 1
      }
      while (outstanding > 0) releaseStep(true)
      for (_ <- 0 until 10) releaseStep(false)

      released should be >= 1000
      allocs shouldBe released
      dut.io.used.peek().litValue shouldBe 0
    }
  }

  it should "move 1000 items through the async link without a credit overdraw" in {
    val n = 1000
    val depth = 8
    val q = mutable.Queue.empty[BigInt]

    simulate(new LmAsyncLink(32, depth)) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.bits.poke(0.U)
      dut.io.out.ready.poke(true.B)
      dut.io.dstClk.poke(false.B)
      dut.io.dstReset.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(3)
      for (_ <- 0 until 3) {
        dut.io.dstClk.poke(true.B); dut.clock.step()
        dut.io.dstClk.poke(false.B); dut.clock.step()
      }
      dut.io.dstReset.poke(false.B)
      dut.reset.poke(false.B)

      var sent = 0
      var recv = 0
      var iter = 0

      def srcStep(): Unit = {
        if (sent < n) {
          dut.io.in.valid.poke(true.B)
          dut.io.in.bits.poke(sent.U)
        } else {
          dut.io.in.valid.poke(false.B)
        }
        val ready = dut.io.in.ready.peek().litValue == 1
        dut.clock.step()
        dut.io.in.valid.poke(false.B)
        if (sent < n && ready) { q.enqueue(BigInt(sent)); sent += 1 }
        dut.io.creditUsed.peek().litValue.toInt should be <= depth
      }

      def dstStep(): Unit = {
        dut.io.in.valid.poke(false.B)
        dut.io.dstClk.poke(true.B)
        if (dut.io.out.valid.peek().litValue == 1) {
          q.nonEmpty shouldBe true
          dut.io.out.bits.peek().litValue shouldBe q.dequeue()
          recv += 1
        }
        dut.clock.step()
        dut.io.dstClk.poke(false.B)
        dut.clock.step()
      }

      while (recv < n) {
        srcStep()
        if (iter % 2 == 0) dstStep()
        iter += 1
        iter should be < 20000
      }
      for (_ <- 0 until 6) dstStep()

      recv shouldBe n
      q shouldBe empty
      dut.io.creditUsed.peek().litValue shouldBe 0
    }
  }

  it should "fire the P7 assertion on a forced over-allocation" in {
    intercept[Throwable] {
      simulate(new LmCdcCredit(2)) { dut =>
        initCredit(dut)
        dut.io.alloc.poke(true.B)
        for (_ <- 0 until 8) dut.clock.step()
      }
    }
  }
}
