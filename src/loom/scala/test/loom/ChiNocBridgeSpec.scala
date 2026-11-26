package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.rtl.LmWire
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiNocBridgeSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val c = ChiConfig()
  private val windows = Seq((0x1000L, 0x1000L))

  private def tieToRspSn(to: ChiRnPort, c: ChiConfig): Unit = {
    to.rsp.valid := false.B; to.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    to.dat.valid := false.B; to.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    to.snp.valid := false.B; to.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    to.txRsp.ready := true.B; to.txDat.ready := true.B
  }

  private class ReadNet extends Module {
    val io = IO(new Bundle {
      val from = new ChiSnPort(c)
      val rdData = Input(UInt(512.W))
    })
    private val br = Module(new ChiNocBridge(c, windows, 2, 0))
    private val src = Module(new ChiReadSource(c))
    br.io.dstClk := false.B; br.io.dstReset := false.B
    LmWire.link(io.from.req, br.io.from.req)
    LmWire.link(br.io.from.rsp, io.from.rsp)
    LmWire.link(br.io.from.dat, io.from.dat)
    LmWire.link(io.from.rxDat, br.io.from.rxDat)
    LmWire.link(br.io.to.req, src.io.req)
    LmWire.link(src.io.dat, br.io.to.dat)
    br.io.to.rsp.valid := false.B; br.io.to.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    br.io.to.snp.valid := false.B; br.io.to.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    br.io.to.txRsp.ready := true.B; br.io.to.txDat.ready := true.B
    src.io.rdData := io.rdData
  }

  private class WriteNet extends Module {
    val io = IO(new Bundle {
      val from = new ChiSnPort(c)
      val wrData = Decoupled(UInt(512.W))
    })
    private val br = Module(new ChiNocBridge(c, windows, 2, 0))
    private val sink = Module(new ChiWriteSink(c, 2, 3))
    br.io.dstClk := false.B; br.io.dstReset := false.B
    LmWire.link(io.from.req, br.io.from.req)
    LmWire.link(br.io.from.rsp, io.from.rsp)
    LmWire.link(br.io.from.dat, io.from.dat)
    LmWire.link(io.from.rxDat, br.io.from.rxDat)
    LmWire.link(br.io.to.req, sink.io.req)
    LmWire.link(sink.io.rsp, br.io.to.rsp)
    LmWire.link(br.io.to.txDat, sink.io.dat)
    br.io.to.dat.valid := false.B; br.io.to.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    br.io.to.snp.valid := false.B; br.io.to.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    br.io.to.txRsp.ready := true.B
    LmWire.link(sink.io.wrDone, io.wrData)
  }

  "ChiNocBridge read" should "proxy ReadNoSnp across NoCs and restore source TxnID (HW-11)" in {
    simulate(new ReadNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(true.B)
      dut.io.from.rxDat.valid.poke(false.B)
      dut.io.from.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.from.req.bits.addr.poke(0x1000L.U)
      dut.io.from.req.bits.txnId.poke(7.U); dut.io.from.req.bits.srcId.poke(0.U)
      dut.io.from.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.from.dat.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.from.req.valid.poke(false.B)
      dut.io.from.dat.valid.expect(true.B)
      dut.io.from.dat.bits.opcode.expect(ChiOp.DatCompData.U)
      dut.io.from.dat.bits.data.expect(0xBBBBL.U)
      dut.io.from.dat.bits.txnId.expect(7.U)
      dut.io.from.dat.bits.tgtId.expect(0.U)
    }
  }

  "ChiNocBridge write" should "proxy DBID pull write across NoCs with data integrity (HW-11)" in {
    simulate(new WriteNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(true.B)
      dut.io.wrData.ready.poke(true.B)
      dut.io.from.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
      dut.io.from.req.bits.addr.poke(0x1000L.U)
      dut.io.from.req.bits.txnId.poke(5.U); dut.io.from.req.bits.srcId.poke(0.U)
      dut.io.from.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.from.rsp.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.from.req.valid.poke(false.B)
      dut.io.from.rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.io.from.rsp.bits.txnId.expect(5.U)
      dut.clock.step()
      dut.io.from.rxDat.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
      dut.io.from.rxDat.bits.txnId.poke(5.U)
      dut.io.from.rxDat.bits.dataId.poke(0.U); dut.io.from.rxDat.bits.data.poke(0x1111L.U)
      dut.io.from.rxDat.valid.poke(true.B); dut.clock.step()
      dut.io.from.rxDat.bits.dataId.poke((c.datWidth / 128).U); dut.io.from.rxDat.bits.data.poke(0x2222L.U)
      dut.clock.step(); dut.io.from.rxDat.valid.poke(false.B)
      n = 0
      while (!dut.io.wrData.valid.peek().litToBoolean && n < 80) { dut.clock.step(); n += 1 }
      dut.io.wrData.valid.expect(true.B)
      dut.io.wrData.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
      n = 0
      while (!(dut.io.from.rsp.valid.peek().litToBoolean && dut.io.from.rsp.bits.opcode.peek().litValue.toInt == ChiOp.RspComp) && n < 40) { dut.clock.step(); n += 1 }
      dut.io.from.rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.from.rsp.bits.txnId.expect(5.U)
    }
  }

  "ChiNocBridge window miss" should "answer NDERR locally, not forward (HW-11)" in {
    simulate(new ReadNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke(0.U)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(true.B)
      dut.io.from.rxDat.valid.poke(false.B)
      dut.io.from.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.from.req.bits.addr.poke(0x9000L.U) // outside window
      dut.io.from.req.bits.txnId.poke(9.U); dut.io.from.req.bits.srcId.poke(0.U)
      dut.io.from.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.from.rsp.valid.peek().litToBoolean && n < 40) { dut.clock.step(); n += 1 }
      dut.io.from.rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.from.rsp.bits.respErr.expect(ChiRespErr.NDERR.U)
      dut.io.from.rsp.bits.txnId.expect(9.U)
      dut.io.from.dat.valid.expect(false.B)
    }
  }

  private class WidthReadNet extends Module {
    val ci = ChiConfig(datWidth = 128)
    val co = ChiConfig(datWidth = 256)
    val io = IO(new Bundle {
      val from = new ChiSnPort(ci)
      val rdData = Input(UInt(512.W))
    })
    private val br = Module(new ChiNocBridge(ci, windows, 2, 0, 0, 256))
    private val src = Module(new ChiReadSource(co))
    br.io.dstClk := false.B; br.io.dstReset := false.B
    LmWire.link(io.from.req, br.io.from.req)
    LmWire.link(br.io.from.rsp, io.from.rsp)
    LmWire.link(br.io.from.dat, io.from.dat)
    LmWire.link(io.from.rxDat, br.io.from.rxDat)
    LmWire.link(br.io.to.req, src.io.req)
    LmWire.link(src.io.dat, br.io.to.dat)
    br.io.to.rsp.valid := false.B; br.io.to.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(co))
    br.io.to.snp.valid := false.B; br.io.to.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(co))
    br.io.to.txRsp.ready := true.B; br.io.to.txDat.ready := true.B
    src.io.rdData := io.rdData
  }

  "ChiNocBridge width" should "gearbox DAT 256->128 across NoCs bit-exact (HW-11/W12)" in {
    simulate(new WidthReadNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      val word = (BigInt(0xAAAA) << 384) | (BigInt(0xBBBB) << 256) | (BigInt(0xCCCC) << 128) | BigInt(0xDDDD)
      dut.io.rdData.poke(word.U)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(true.B)
      dut.io.from.rxDat.valid.poke(false.B)
      dut.io.from.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.from.req.bits.addr.poke(0x1000L.U)
      dut.io.from.req.bits.txnId.poke(7.U); dut.io.from.req.bits.srcId.poke(0.U)
      dut.io.from.req.valid.poke(true.B)
      var out = BigInt(0); var got = 0; var n = 0
      val nOut = ChiWidths.flitCount(64, 128)
      while (got < nOut && n < 200) {
        if (dut.io.from.dat.valid.peek().litToBoolean) {
          val d = dut.io.from.dat.bits.data.peek().litValue
          val id = dut.io.from.dat.bits.dataId.peek().litValue.toInt
          out |= d << (id * 128)
          dut.io.from.dat.bits.txnId.expect(7.U)
          got += 1
        }
        dut.clock.step(); n += 1
      }
      assert(got == nOut, s"expected $nOut flits, got $got")
      assert(out == word, s"width mismatch: got ${out.toString(16)} want ${word.toString(16)}")
    }
  }

  private class AsyncReadNet extends Module {
    val io = IO(new Bundle {
      val from = new ChiSnPort(c)
      val rdData = Input(UInt(512.W))
      val dstClk = Input(Bool())
      val dstReset = Input(Bool())
    })
    private val br = Module(new ChiNocBridge(c, windows, 2, 0, 4))
    br.io.dstClk := io.dstClk; br.io.dstReset := io.dstReset
    private val src = withClockAndReset(io.dstClk.asClock, io.dstReset.asAsyncReset) {
      Module(new ChiReadSource(c))
    }
    LmWire.link(io.from.req, br.io.from.req)
    LmWire.link(br.io.from.rsp, io.from.rsp)
    LmWire.link(br.io.from.dat, io.from.dat)
    LmWire.link(io.from.rxDat, br.io.from.rxDat)
    LmWire.link(br.io.to.req, src.io.req)
    LmWire.link(src.io.dat, br.io.to.dat)
    br.io.to.rsp.valid := false.B; br.io.to.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    br.io.to.snp.valid := false.B; br.io.to.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    br.io.to.txRsp.ready := true.B; br.io.to.txDat.ready := true.B
    src.io.rdData := io.rdData
  }

  "ChiNocBridge async" should "cross a dual-clock bridge read with bounded credit (HW-11/W12)" in {
    simulate(new AsyncReadNet) { dut =>
      dut.io.dstReset.poke(true.B); dut.reset.poke(true.B)
      for (i <- 0 until 4) { dut.io.dstClk.poke((i % 2 == 0).B); dut.clock.step() }
      dut.reset.poke(false.B); dut.io.dstReset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(true.B)
      dut.io.from.rxDat.valid.poke(false.B)
      dut.io.from.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.from.req.bits.addr.poke(0x1000L.U)
      dut.io.from.req.bits.txnId.poke(7.U); dut.io.from.req.bits.srcId.poke(0.U)
      dut.io.from.req.valid.poke(true.B)
      var dst = false; var n = 0
      while (!dut.io.from.dat.valid.peek().litToBoolean && n < 400) {
        dut.io.dstClk.poke(dst.B); dut.clock.step(); dst = !dst; n += 1
      }
      dut.io.from.dat.valid.expect(true.B)
      dut.io.from.dat.bits.data.expect(0xBBBBL.U)
      dut.io.from.dat.bits.txnId.expect(7.U)
    }
  }
}