package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiAxiBridgeSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private class ReadLoop(c: ChiConfig) extends Module {
    val io = IO(new Bundle {
      val ar = Flipped(Decoupled(new ChiAxiAddr(c)))
      val r = Decoupled(new ChiAxiR(c))
      val rdData = Input(UInt(512.W))
    })
    private val b = Module(new ChiAxiReadBridge(c))
    private val s = Module(new ChiReadSource(c))
    io.ar <> b.io.ar; b.io.r <> io.r
    s.io.req <> b.io.req; b.io.dat <> s.io.dat; s.io.rdData := io.rdData
  }

  private class WriteLoop(c: ChiConfig) extends Module {
    val io = IO(new Bundle {
      val aw = Flipped(Decoupled(new ChiAxiAddr(c)))
      val w = Flipped(Decoupled(new ChiAxiW(c)))
      val b = Decoupled(new ChiAxiB(c))
      val wrData = Decoupled(UInt(512.W))
    })
    private val b = Module(new ChiAxiWriteBridge(c))
    private val s = Module(new ChiWriteSink(c, 2))
    io.aw <> b.io.aw; io.w <> b.io.w; b.io.b <> io.b
    s.io.req <> b.io.req; b.io.rsp <> s.io.rsp; s.io.dat <> b.io.dat
    s.io.wrDone <> io.wrData
  }

  "AXI read bridge loopback" should "round-trip a 64B read through CHI" in {
    val c = ChiConfig()
    simulate(new ReadLoop(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.r.ready.poke(false.B)
      dut.io.ar.bits.id.poke(9.U); dut.io.ar.bits.addr.poke(0.U); dut.io.ar.bits.len.poke(1.U)
      dut.io.ar.valid.poke(true.B); dut.clock.step(); dut.io.ar.valid.poke(false.B)
      dut.clock.step(6)
      dut.io.r.valid.expect(true.B)
      dut.io.r.bits.data.expect(0xBBBBL.U); dut.io.r.bits.last.expect(false.B)
      dut.io.r.bits.id.expect(9.U)
      dut.io.r.ready.poke(true.B); dut.clock.step()
      dut.io.r.bits.data.expect(0xAAAAL.U); dut.io.r.bits.last.expect(true.B)
      dut.clock.step()
      dut.io.r.valid.expect(false.B)
    }
  }

  "AXI write bridge loopback" should "round-trip a 64B write through the DBID pull model" in {
    val c = ChiConfig()
    simulate(new WriteLoop(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.b.ready.poke(false.B); dut.io.wrData.ready.poke(false.B)
      dut.io.aw.bits.id.poke(5.U); dut.io.aw.bits.addr.poke(0.U); dut.io.aw.bits.len.poke(1.U)
      dut.io.aw.valid.poke(true.B)
      dut.io.w.bits.data.poke(0x1111L.U); dut.io.w.bits.strb.poke(((1L << 32) - 1).U)
      dut.io.w.bits.last.poke(false.B); dut.io.w.valid.poke(true.B)
      dut.clock.step(); dut.io.aw.valid.poke(false.B) // AW accepted -> W collect
      dut.clock.step() // W beat0 (0x1111) collected
      dut.io.w.bits.data.poke(0x2222L.U); dut.io.w.bits.last.poke(true.B)
      dut.clock.step(); dut.io.w.valid.poke(false.B) // W beat1 (last) -> REQ
      dut.clock.step(8)
      dut.io.b.valid.expect(true.B); dut.io.b.bits.id.expect(5.U)
      dut.io.wrData.valid.expect(true.B)
      dut.io.wrData.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
    }
  }
}