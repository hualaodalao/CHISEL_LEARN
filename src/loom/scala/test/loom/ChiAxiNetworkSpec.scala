package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.ir.{Ir, Json}
import loom.rtl.LmWire
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiAxiNetworkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(Json.parse(
    Files.readString(Paths.get("src/loom/testdata/v1/chain_4r.json"))))
  private val p = ChiNetworkParams.fromIr(ir)
  // RN axi4 boundary, SN native chi (ReadNet/WriteNet attach CHI SN helpers).
  private val pRnAxi = p.copy(rn = p.rn.map(_.copy(protocol = "axi4")))
  // Mixed: RN0/SN0 axi4, RN1/SN1 chi (A3).
  private val pMixed = p.copy(
    rn = Seq(p.rn(0).copy(protocol = "axi4"), p.rn(1).copy(protocol = "chi")),
    sn = Seq(p.sn(0).copy(protocol = "axi4"), p.sn(1).copy(protocol = "chi")))

  private def tieRnAxi(a: ChiAxiRnPort, c: ChiConfig): Unit = {
    a.ar.valid := false.B; a.ar.bits := 0.U.asTypeOf(new ChiAxiAddr(c))
    a.aw.valid := false.B; a.aw.bits := 0.U.asTypeOf(new ChiAxiAddr(c))
    a.w.valid := false.B; a.w.bits := 0.U.asTypeOf(new ChiAxiW(c))
    a.r.ready := true.B; a.b.ready := true.B
  }
  private def tieRnChi(ch: ChiRnPort, c: ChiConfig): Unit = {
    ch.req.valid := false.B; ch.req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
    ch.txRsp.valid := false.B; ch.txRsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    ch.txDat.valid := false.B; ch.txDat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    ch.rsp.ready := true.B; ch.dat.ready := true.B; ch.snp.ready := true.B
  }
  private def tieSnChi(sn: ChiSnPort, c: ChiConfig): Unit = {
    sn.req.ready := true.B
    sn.rsp.valid := false.B; sn.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    sn.dat.valid := false.B; sn.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    sn.rxDat.ready := true.B
  }

  private class ReadNet(pp: ChiNetworkParams) extends Module {
    private val c = pp.cfg
    val io = IO(new Bundle {
      val ar = Flipped(Decoupled(new ChiAxiAddr(c)))
      val r = Decoupled(new ChiAxiR(c))
      val rdData = Input(UInt(512.W))
    })
    private val net = Module(new ChiAxiNetworkTop(pp))
    private val src = Module(new ChiReadSource(c))
    tieRnAxi(net.io.rnAxi(0), c)
    LmWire.link(io.ar, net.io.rnAxi(0).ar)
    LmWire.link(net.io.rnAxi(0).r, io.r)
    tieRnAxi(net.io.rnAxi(1), c)
    LmWire.link(net.io.snChi(0).req, src.io.req)
    LmWire.link(src.io.dat, net.io.snChi(0).dat)
    net.io.snChi(0).rsp.valid := false.B; net.io.snChi(0).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    net.io.snChi(0).rxDat.ready := true.B
    src.io.rdData := io.rdData
    tieSnChi(net.io.snChi(1), c)
  }

  private class WriteNet(pp: ChiNetworkParams) extends Module {
    private val c = pp.cfg
    val io = IO(new Bundle {
      val aw = Flipped(Decoupled(new ChiAxiAddr(c)))
      val w = Flipped(Decoupled(new ChiAxiW(c)))
      val b = Decoupled(new ChiAxiB(c))
      val wrData = Decoupled(UInt(512.W))
    })
    private val net = Module(new ChiAxiNetworkTop(pp))
    private val sink = Module(new ChiWriteSink(c, 2, 2))
    tieRnAxi(net.io.rnAxi(0), c)
    LmWire.link(io.aw, net.io.rnAxi(0).aw)
    LmWire.link(io.w, net.io.rnAxi(0).w)
    LmWire.link(net.io.rnAxi(0).b, io.b)
    tieRnAxi(net.io.rnAxi(1), c)
    LmWire.link(net.io.snChi(0).req, sink.io.req)
    LmWire.link(sink.io.rsp, net.io.snChi(0).rsp)
    LmWire.link(net.io.snChi(0).rxDat, sink.io.dat)
    net.io.snChi(0).dat.valid := false.B; net.io.snChi(0).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    LmWire.link(sink.io.wrDone, io.wrData)
    tieSnChi(net.io.snChi(1), c)
  }

  // Minimal AXI slave for the mixed end-to-end path (SN0 axi4).
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

  private class MixedNet(pp: ChiNetworkParams) extends Module {
    private val c = pp.cfg
    val io = IO(new Bundle {
      val ar = Flipped(Decoupled(new ChiAxiAddr(c)))
      val r = Decoupled(new ChiAxiR(c))
      val aw = Flipped(Decoupled(new ChiAxiAddr(c)))
      val w = Flipped(Decoupled(new ChiAxiW(c)))
      val b = Decoupled(new ChiAxiB(c))
      val rdData = Input(UInt(512.W))
      val wrData = Decoupled(UInt(512.W))
    })
    private val net = Module(new ChiAxiNetworkTop(pp))
    private val mem = Module(new MemSlave(c))
    tieRnAxi(net.io.rnAxi(0), c)
    LmWire.link(io.ar, net.io.rnAxi(0).ar)
    LmWire.link(net.io.rnAxi(0).r, io.r)
    LmWire.link(io.aw, net.io.rnAxi(0).aw)
    LmWire.link(io.w, net.io.rnAxi(0).w)
    LmWire.link(net.io.rnAxi(0).b, io.b)
    tieRnChi(net.io.rnChi(0), c)
    LmWire.link(net.io.snAxi(0).ar, mem.io.ar)
    LmWire.link(mem.io.r, net.io.snAxi(0).r)
    LmWire.link(net.io.snAxi(0).aw, mem.io.aw)
    LmWire.link(net.io.snAxi(0).w, mem.io.w)
    LmWire.link(mem.io.b, net.io.snAxi(0).b)
    mem.io.rdData := io.rdData
    LmWire.link(mem.io.wrData, io.wrData)
    tieSnChi(net.io.snChi(0), c)
  }

  "AXI-boundary network read" should "round-trip AR->fabric->SN CompData->R (HW-09)" in {
    simulate(new ReadNet(pRnAxi)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.r.ready.poke(false.B)
      dut.io.ar.bits.id.poke(9.U); dut.io.ar.bits.addr.poke(0.U); dut.io.ar.bits.len.poke(1.U)
      dut.io.ar.valid.poke(true.B); dut.clock.step(); dut.io.ar.valid.poke(false.B)
      var n = 0
      while (!dut.io.r.valid.peek().litToBoolean && n < 120) { dut.clock.step(); n += 1 }
      dut.io.r.valid.expect(true.B)
      dut.io.r.bits.data.expect(0xBBBBL.U); dut.io.r.bits.last.expect(false.B)
      dut.io.r.bits.id.expect(9.U)
      dut.io.r.ready.poke(true.B); dut.clock.step()
      dut.io.r.bits.data.expect(0xAAAAL.U); dut.io.r.bits.last.expect(true.B)
    }
  }

  "AXI-boundary network write" should "round-trip AW/W->fabric->SN DBID pull->B (HW-09)" in {
    simulate(new WriteNet(pRnAxi)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.b.ready.poke(false.B); dut.io.wrData.ready.poke(false.B)
      dut.io.aw.bits.id.poke(5.U); dut.io.aw.bits.addr.poke(0.U); dut.io.aw.bits.len.poke(1.U)
      dut.io.aw.valid.poke(true.B)
      dut.io.w.bits.data.poke(0x1111L.U); dut.io.w.bits.strb.poke(((1L << 32) - 1).U)
      dut.io.w.bits.last.poke(false.B); dut.io.w.valid.poke(true.B)
      dut.clock.step(); dut.io.aw.valid.poke(false.B)
      dut.clock.step()
      dut.io.w.bits.data.poke(0x2222L.U); dut.io.w.bits.last.poke(true.B)
      dut.clock.step(); dut.io.w.valid.poke(false.B)
      var n = 0
      while (!dut.io.b.valid.peek().litToBoolean && n < 120) { dut.clock.step(); n += 1 }
      dut.io.b.valid.expect(true.B); dut.io.b.bits.id.expect(5.U)
      dut.io.b.bits.resp.expect(ChiRespErr.OK.U)
      dut.io.wrData.valid.expect(true.B)
      dut.io.wrData.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
    }
  }

  "Mixed-protocol network (RN0/SN0 axi4, RN1/SN1 chi)" should "read AXI master -> fabric -> AXI slave (A2/A3)" in {
    simulate(new MixedNet(pMixed)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.r.ready.poke(true.B); dut.io.b.ready.poke(true.B); dut.io.wrData.ready.poke(true.B)
      dut.io.aw.valid.poke(false.B); dut.io.w.valid.poke(false.B)
      dut.io.ar.bits.id.poke(7.U); dut.io.ar.bits.addr.poke(0.U); dut.io.ar.bits.len.poke(1.U)
      dut.io.ar.valid.poke(true.B); dut.clock.step(); dut.io.ar.valid.poke(false.B)
      var n = 0
      while (!dut.io.r.valid.peek().litToBoolean && n < 160) { dut.clock.step(); n += 1 }
      dut.io.r.valid.expect(true.B)
      dut.io.r.bits.data.expect(0xBBBBL.U); dut.io.r.bits.id.expect(7.U)
      n = 0
      while (!dut.io.r.bits.last.peek().litToBoolean && n < 8) { dut.clock.step(); n += 1 }
      dut.io.r.bits.data.expect(0xAAAAL.U); dut.io.r.bits.last.expect(true.B)
    }
  }

  it should "write AXI master -> fabric -> AXI slave with data integrity (A2/A3)" in {
    simulate(new MixedNet(pMixed)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.r.ready.poke(true.B); dut.io.b.ready.poke(true.B); dut.io.wrData.ready.poke(true.B)
      dut.io.ar.valid.poke(false.B)
      dut.io.aw.bits.id.poke(8.U); dut.io.aw.bits.addr.poke(0.U); dut.io.aw.bits.len.poke(1.U)
      dut.io.aw.valid.poke(true.B)
      dut.io.w.bits.data.poke(0x1111L.U); dut.io.w.bits.strb.poke(((1L << 32) - 1).U)
      dut.io.w.bits.last.poke(false.B); dut.io.w.valid.poke(true.B)
      dut.clock.step(); dut.io.aw.valid.poke(false.B)
      dut.clock.step()
      dut.io.w.bits.data.poke(0x2222L.U); dut.io.w.bits.last.poke(true.B)
      dut.clock.step(); dut.io.w.valid.poke(false.B)
      var n = 0
      while (!dut.io.wrData.valid.peek().litToBoolean && n < 160) { dut.clock.step(); n += 1 }
      dut.io.wrData.valid.expect(true.B)
      dut.io.wrData.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
      n = 0
      while (!dut.io.b.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.b.valid.expect(true.B); dut.io.b.bits.id.expect(8.U)
    }
  }
}