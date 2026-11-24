import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QueenFlushCtrlSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private case class Obs(devReq: Boolean, cpuAck: Boolean, flushing: Boolean,
                           start: Boolean, clearState: Boolean, idle: Boolean)

    private class Driver(dut: QueenFlushCtrl) {
        dut.io.trig.poke(false.B)
        dut.io.cpuReq.poke(false.B)
        dut.io.devAck.foreach(_.poke(false.B))
        dut.io.drained.poke(false.B)
        dut.io.disturb.poke(false.B)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def step(trig: Boolean = false, cpuReq: Boolean = false, ack: Boolean = false,
                 drained: Boolean = false, disturb: Boolean = false): Obs = {
            dut.io.trig.poke(trig.B)
            dut.io.cpuReq.poke(cpuReq.B)
            dut.io.devAck(0).poke(ack.B)
            dut.io.devAck(1).poke(ack.B)
            dut.io.drained.poke(drained.B)
            dut.io.disturb.poke(disturb.B)
            val o = Obs(dut.io.devReq(0).peek().litToBoolean && dut.io.devReq(1).peek().litToBoolean,
                dut.io.cpuAck.peek().litToBoolean, dut.io.flushing.peek().litToBoolean,
                dut.io.start.peek().litToBoolean, dut.io.clearState.peek().litToBoolean,
                dut.io.idle.peek().litToBoolean)
            dut.clock.step()
            o
        }

        /** 走到静默窗的第一拍（状态已是 sQuiet，计数刚装载）。 */
        def toQuiet(cpuReq: Boolean = false): Unit = {
            step(trig = !cpuReq, cpuReq = cpuReq)
            step(cpuReq = cpuReq)
            step(cpuReq = cpuReq, ack = true)
            step(cpuReq = cpuReq, ack = true)
            step(cpuReq = cpuReq)
            step(cpuReq = cpuReq, drained = true)
        }
    }

    behavior of "QueenFlushCtrl"

    it should "walk the device four-phase handshake then the quiet window" in {
        simulate(new QueenFlushCtrl(3)) { dut =>
            val d = new Driver(dut)
            d.step().idle shouldBe true
            val o1 = d.step(trig = true)
            o1.start shouldBe true
            o1.flushing shouldBe false
            val o2 = d.step()
            o2.devReq shouldBe true
            o2.flushing shouldBe true
            // ack 未齐前 req 不得落下。
            d.step().devReq shouldBe true
            val o3 = d.step(ack = true)
            o3.devReq shouldBe true
            // req 已落下但 ack 还高，仍停在 reqDown。
            val o4 = d.step(ack = true)
            o4.devReq shouldBe false
            o4.flushing shouldBe true
            d.step().flushing shouldBe true
            d.step(drained = true)
            d.step(drained = true).clearState shouldBe false
            d.step(drained = true).clearState shouldBe false
            d.step(drained = true).clearState shouldBe false
            val o5 = d.step(drained = true)
            o5.clearState shouldBe true
            o5.cpuAck shouldBe false
            val o6 = d.step(drained = true)
            o6.cpuAck shouldBe true
            o6.flushing shouldBe true
            // 内部触发没有 sys req 可等，ack 只有一拍。
            val o7 = d.step()
            o7.cpuAck shouldBe false
            o7.idle shouldBe true
            o7.flushing shouldBe false
        }
    }

    it should "stay in flush for as long as the sys flushReq is high" in {
        simulate(new QueenFlushCtrl(2)) { dut =>
            val d = new Driver(dut)
            d.toQuiet(cpuReq = true)
            d.step(cpuReq = true, drained = true)
            d.step(cpuReq = true, drained = true)
            d.step(cpuReq = true, drained = true)
            val a = d.step(cpuReq = true, drained = true)
            a.cpuAck shouldBe true
            // sys 要收齐所有模块的 ack 才落 req，Queen 必须一直停在 flush 态。
            for (_ <- 0 until 20) {
                val o = d.step(cpuReq = true, drained = true)
                o.cpuAck shouldBe true
                o.flushing shouldBe true
                o.idle shouldBe false
                o.devReq shouldBe false
            }
            d.step(cpuReq = false, drained = true).cpuAck shouldBe true
            val g = d.step()
            g.cpuAck shouldBe false
            g.flushing shouldBe false
            g.idle shouldBe true
        }
    }

    it should "restart the quiet window on any disturbance" in {
        simulate(new QueenFlushCtrl(3)) { dut =>
            val d = new Driver(dut)
            d.toQuiet()
            d.step(drained = true).cpuAck shouldBe false
            // 静默窗内 CPU 又发指令：重新计数，不得提前回 ack。
            d.step(drained = true, disturb = true).cpuAck shouldBe false
            d.step(drained = true).cpuAck shouldBe false
            d.step(drained = true).cpuAck shouldBe false
            d.step(drained = true).cpuAck shouldBe false
            d.step(drained = true).clearState shouldBe true
            d.step(drained = true).cpuAck shouldBe true
        }
    }

    it should "fall back to drain when the quiet window loses drained" in {
        simulate(new QueenFlushCtrl(2)) { dut =>
            val d = new Driver(dut)
            d.toQuiet()
            d.step(drained = true)
            // 迟到 completion 让排空条件失效：退回 sDrain 而不是继续计数。
            d.step(drained = false).cpuAck shouldBe false
            d.step(drained = false).cpuAck shouldBe false
            d.step(drained = true)
            d.step(drained = true).cpuAck shouldBe false
            d.step(drained = true).cpuAck shouldBe false
            d.step(drained = true).clearState shouldBe true
            d.step(drained = true).cpuAck shouldBe true
        }
    }

    it should "not start a second flush while one is in progress" in {
        simulate(new QueenFlushCtrl(2)) { dut =>
            val d = new Driver(dut)
            d.toQuiet(cpuReq = true)
            for (_ <- 0 until 8) {
                val o = d.step(trig = true, cpuReq = true, drained = true)
                o.start shouldBe false
            }
        }
    }
}