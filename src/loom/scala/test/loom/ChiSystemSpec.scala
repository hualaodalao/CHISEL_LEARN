package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.rtl.LmWire
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiSystemSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val c = ChiConfig()
  private val win = Seq((0x1000L, 0x1000L))

  // Each subsystem: one router, RN nodeId0 at local port0, SN nodeId1 at port1.
  private def sub(name: String): ChiNetworkParams = ChiNetworkParams(
    name = name, cfg = c,
    rn = Seq(ChiNetNode(0, ChiNodeKind.RnI, 0, 0)),
    sn = Seq(ChiNetNode(1, ChiNodeKind.SnF, 0, 1)),
    routers = Seq(ChiNetRouter(0, s"${name}0", 2, 0, Seq(0, 1), Seq.empty)),
    links = Seq.empty, snpEnable = false,
    sam = Seq(ChiSamEntry(0x1000L, 0x1000L, 1)))

  private val bridges = Seq(ChiBridgeLink(0, 0, 1, 0, win))

  private class ReadNet extends Module {
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val rdData = Input(UInt(512.W))
    })
    val sys = Module(new ChiSystemTop(Seq(sub("a"), sub("b")), bridges))
    val src = Module(new ChiReadSource(c))
    sys.io.rn(0) <> io.rn
    LmWire.link(sys.io.sn(0).req, src.io.req)
    LmWire.link(src.io.dat, sys.io.sn(0).dat)
    sys.io.sn(0).rsp.valid := false.B; sys.io.sn(0).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    sys.io.sn(0).rxDat.ready := true.B
    src.io.rdData := io.rdData
  }

  "ChiSystemTop" should "proxy a read across two subsystems via the bridge (HW-11/W13)" in {
    simulate(new ReadNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(true.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      dut.io.rn.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.rn.req.bits.addr.poke(0x1000L.U)
      dut.io.rn.req.bits.txnId.poke(7.U); dut.io.rn.req.bits.srcId.poke(0.U)
      dut.io.rn.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.rn.dat.valid.peek().litToBoolean && n < 200) { dut.clock.step(); n += 1 }
      dut.io.rn.req.valid.poke(false.B)
      dut.io.rn.dat.valid.expect(true.B)
      dut.io.rn.dat.bits.opcode.expect(ChiOp.DatCompData.U)
      dut.io.rn.dat.bits.data.expect(0xBBBBL.U)
      dut.io.rn.dat.bits.txnId.expect(7.U)
    }
  }

  private class WriteNet extends Module {
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val wrData = Decoupled(UInt(512.W))
    })
    val sys = Module(new ChiSystemTop(Seq(sub("a"), sub("b")), bridges))
    val sink = Module(new ChiWriteSink(c, 2, 1))
    sys.io.rn(0) <> io.rn
    LmWire.link(sys.io.sn(0).req, sink.io.req)
    LmWire.link(sink.io.rsp, sys.io.sn(0).rsp)
    LmWire.link(sys.io.sn(0).rxDat, sink.io.dat)
    sys.io.sn(0).dat.valid := false.B; sys.io.sn(0).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    LmWire.link(sink.io.wrDone, io.wrData)
  }

  it should "proxy a DBID pull write across two subsystems with data integrity (HW-11/W13)" in {
    simulate(new WriteNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(true.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      dut.io.wrData.ready.poke(true.B)
      dut.io.rn.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
      dut.io.rn.req.bits.addr.poke(0x1000L.U)
      dut.io.rn.req.bits.txnId.poke(5.U); dut.io.rn.req.bits.srcId.poke(0.U)
      dut.io.rn.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.rn.rsp.valid.peek().litToBoolean && n < 200) { dut.clock.step(); n += 1 }
      dut.io.rn.req.valid.poke(false.B)
      dut.io.rn.rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.io.rn.rsp.bits.txnId.expect(5.U)
      dut.clock.step()
      // RN sends write data on txDat; fabric delivers to SN rxDat.
      dut.io.rn.txDat.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
      dut.io.rn.txDat.bits.txnId.poke(5.U); dut.io.rn.txDat.bits.tgtId.poke(1.U)
      dut.io.rn.txDat.bits.dataId.poke(0.U); dut.io.rn.txDat.bits.data.poke(0x1111L.U)
      dut.io.rn.txDat.valid.poke(true.B); dut.clock.step()
      dut.io.rn.txDat.bits.dataId.poke((c.datWidth / 128).U); dut.io.rn.txDat.bits.data.poke(0x2222L.U)
      dut.clock.step(); dut.io.rn.txDat.valid.poke(false.B)
      n = 0
      while (!dut.io.wrData.valid.peek().litToBoolean && n < 200) { dut.clock.step(); n += 1 }
      dut.io.wrData.valid.expect(true.B)
      dut.io.wrData.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
    }
  }
}