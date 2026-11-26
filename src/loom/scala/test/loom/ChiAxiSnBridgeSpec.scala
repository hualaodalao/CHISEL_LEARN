package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.rtl.LmWire
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiAxiSnBridgeSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  // Minimal AXI slave: read returns rdData beats, write collects W beats.
  private class MemSlave(c: ChiConfig) extends Module {
    private val nFlits = ChiWidths.flitCount(64, c.datWidth)
    private val bw = math.max(1, log2Ceil(nFlits))
    val io = IO(new Bundle {
      val ar = Flipped(Decoupled(new ChiAxiAddr(c)))
      val r = Decoupled(new ChiAxiR(c))
      val aw = Flipped(Decoupled(new ChiAxiAddr(c)))
      val w = Flipped(Decoupled(new ChiAxiW(c)))
      val b = Decoupled(new ChiAxiB(c))
      val rdData = Input(UInt(512.W))
      val wrData = Decoupled(UInt(512.W))
    })
    private val sIdle :: sRd :: sWrC :: sWrB :: Nil = Enum(4)
    private val st = RegInit(sIdle)
    private val id = Reg(UInt(c.txnIdWidth.W))
    private val buf = RegInit(0.U(512.W))
    private val beat = RegInit(0.U(bw.W))
    io.ar.ready := st === sIdle
    io.aw.ready := st === sIdle && !io.ar.valid
    io.r.valid := st === sRd
    io.r.bits.data := (io.rdData >> (beat * c.datWidth.U))(c.datWidth - 1, 0)
    io.r.bits.id := id; io.r.bits.resp := 0.U; io.r.bits.last := beat === (nFlits - 1).U
    io.w.ready := st === sWrC
    io.b.valid := st === sWrB
    io.b.bits.id := id; io.b.bits.resp := 0.U
    io.wrData.valid := st === sWrB
    io.wrData.bits := buf
    switch(st) {
      is(sIdle) {
        when(io.ar.fire) { id := io.ar.bits.id; beat := 0.U; st := sRd }
        .elsewhen(io.aw.fire) { id := io.aw.bits.id; beat := 0.U; buf := 0.U; st := sWrC }
      }
      is(sRd) { when(io.r.fire) { when(beat === (nFlits - 1).U) { st := sIdle }; beat := beat + 1.U } }
      is(sWrC) {
        when(io.w.fire) {
          buf := buf | (io.w.bits.data.pad(512) << (beat * c.datWidth.U))
          when(io.w.bits.last) { st := sWrB }
          beat := beat + 1.U
        }
      }
      is(sWrB) { when(io.b.fire && io.wrData.fire) { st := sIdle } }
    }
  }

  private class SnLoop(c: ChiConfig) extends Module {
    val io = IO(new Bundle {
      val req = Flipped(Decoupled(new ChiReqFlit(c)))
      val rsp = Decoupled(new ChiRspFlit(c))
      val dat = Decoupled(new ChiDatFlit(c))
      val rxDat = Flipped(Decoupled(new ChiDatFlit(c)))
      val rdData = Input(UInt(512.W))
      val wrData = Decoupled(UInt(512.W))
    })
    private val br = Module(new ChiAxiSnBridge(c, 2))
    private val mem = Module(new MemSlave(c))
    LmWire.link(io.req, br.io.req)
    LmWire.link(br.io.rsp, io.rsp)
    LmWire.link(br.io.dat, io.dat)
    LmWire.link(io.rxDat, br.io.rxDat)
    LmWire.link(br.io.ar, mem.io.ar)
    LmWire.link(mem.io.r, br.io.r)
    LmWire.link(br.io.aw, mem.io.aw)
    LmWire.link(br.io.w, mem.io.w)
    LmWire.link(mem.io.b, br.io.b)
    mem.io.rdData := io.rdData
    LmWire.link(mem.io.wrData, io.wrData)
  }

  private val c = ChiConfig()
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)

  "SN reverse bridge read" should "turn CHI ReadNoSnp into AXI AR then CompData (A2)" in {
    simulate(new SnLoop(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.rsp.ready.poke(true.B); dut.io.dat.ready.poke(true.B)
      dut.io.rxDat.valid.poke(false.B)
      dut.io.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.req.bits.txnId.poke(3.U); dut.io.req.bits.srcId.poke(0.U); dut.io.req.bits.addr.poke(0.U)
      dut.io.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.dat.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.req.valid.poke(false.B)
      dut.io.dat.valid.expect(true.B)
      dut.io.dat.bits.opcode.expect(ChiOp.DatCompData.U)
      dut.io.dat.bits.data.expect(0xBBBBL.U)
      dut.io.dat.bits.txnId.expect(3.U)
      var beats = 1
      while (beats < nFlits) { dut.clock.step(); beats += 1 }
      dut.io.dat.bits.data.expect(0xAAAAL.U)
    }
  }

  "SN reverse bridge write" should "DBIDResp then collect CHI write data -> AXI W -> Comp (A2)" in {
    simulate(new SnLoop(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rsp.ready.poke(true.B); dut.io.dat.ready.poke(true.B)
      dut.io.wrData.ready.poke(true.B)
      dut.io.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
      dut.io.req.bits.txnId.poke(6.U); dut.io.req.bits.srcId.poke(0.U); dut.io.req.bits.addr.poke(0.U)
      dut.io.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.rsp.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.req.valid.poke(false.B)
      dut.io.rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.clock.step()
      // Send nFlits write-data flits (0x1111 low, 0x2222 high).
      dut.io.rxDat.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
      dut.io.rxDat.bits.txnId.poke(6.U)
      dut.io.rxDat.bits.dataId.poke(0.U)
      dut.io.rxDat.bits.data.poke(0x1111L.U)
      dut.io.rxDat.valid.poke(true.B); dut.clock.step()
      dut.io.rxDat.bits.dataId.poke((c.datWidth / 128).U)
      dut.io.rxDat.bits.data.poke(0x2222L.U)
      dut.clock.step(); dut.io.rxDat.valid.poke(false.B)
      n = 0
      while (!dut.io.wrData.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.wrData.valid.expect(true.B)
      dut.io.wrData.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
    }
  }
}