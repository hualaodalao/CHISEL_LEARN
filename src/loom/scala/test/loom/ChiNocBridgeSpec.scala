package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.rtl.LmWire
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

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

  // Multi-outstanding to-side read source: accepts REQs into a FIFO while
  // emitting previous words, so the bridge can hold several IDs in flight.
  private class MultiReadSource(depth: Int) extends Module {
    private val nFlits = ChiWidths.flitCount(64, c.datWidth)
    private val bw = math.max(1, log2Ceil(nFlits))
    private val idStep = c.datWidth / 128
    val io = IO(new Bundle {
      val req = Flipped(Decoupled(new ChiReqFlit(c)))
      val dat = Decoupled(new ChiDatFlit(c))
      val rdData = Input(UInt(512.W))
    })
    private val q = Module(new Queue(new Bundle {
      val txn = UInt(c.txnIdWidth.W); val src = UInt(c.nodeIdWidth.W)
    }, depth))
    q.io.enq.valid := io.req.valid
    q.io.enq.bits.txn := io.req.bits.txnId
    q.io.enq.bits.src := io.req.bits.srcId
    io.req.ready := q.io.enq.ready
    private val curTxn = Reg(UInt(c.txnIdWidth.W))
    private val curSrc = Reg(UInt(c.nodeIdWidth.W))
    private val beat = RegInit(0.U(bw.W))
    private val busy = RegInit(false.B)
    q.io.deq.ready := !busy
    when(q.io.deq.fire) { curTxn := q.io.deq.bits.txn; curSrc := q.io.deq.bits.src; busy := true.B; beat := 0.U }
    io.dat.valid := busy
    io.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    io.dat.bits.opcode := ChiOp.DatCompData.U
    io.dat.bits.txnId := curTxn
    io.dat.bits.tgtId := curSrc
    io.dat.bits.dataId := beat * idStep.U
    io.dat.bits.data := (io.rdData >> (beat * c.datWidth.U))(c.datWidth - 1, 0)
    io.dat.bits.be := ((1L << c.beWidth) - 1).U
    when(io.dat.fire) { when(beat === (nFlits - 1).U) { busy := false.B }; beat := beat + 1.U }
  }

  private class ReadMultiNet(outstanding: Int) extends Module {
    val io = IO(new Bundle {
      val from = new ChiSnPort(c)
      val rdData = Input(UInt(512.W))
    })
    private val br = Module(new ChiNocBridge(c, windows, 2, 0, 0, 0, false, outstanding))
    private val src = Module(new MultiReadSource(outstanding))
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

  "ChiNocBridge multi-read" should "track 3 concurrent reads and restore each source TxnID (HW-11/W14)" in {
    simulate(new ReadMultiNet(4)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      val word = (BigInt(0xAAAA) << 256) | BigInt(0xBBBB)
      dut.io.rdData.poke(word.U)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(false.B)
      dut.io.from.rxDat.valid.poke(false.B)
      val txns = Seq(7, 9, 11)
      for (t <- txns) {
        dut.io.from.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
        dut.io.from.req.bits.addr.poke(0x1000L.U)
        dut.io.from.req.bits.txnId.poke(t.U); dut.io.from.req.bits.srcId.poke(0.U)
        dut.io.from.req.valid.poke(true.B)
        var w = 0
        while (!dut.io.from.req.ready.peek().litToBoolean && w < 60) { dut.clock.step(); w += 1 }
        dut.clock.step()
      }
      dut.io.from.req.valid.poke(false.B)
      dut.io.from.dat.ready.poke(true.B)
      val nFlits = ChiWidths.flitCount(64, c.datWidth)
      val acc = mutable.Map[Int, BigInt]()
      val cnt = mutable.Map[Int, Int]()
      var n = 0; var total = 0
      while (total < txns.size * nFlits && n < 400) {
        if (dut.io.from.dat.valid.peek().litToBoolean) {
          val t = dut.io.from.dat.bits.txnId.peek().litValue.toInt
          val d = dut.io.from.dat.bits.data.peek().litValue
          val id = dut.io.from.dat.bits.dataId.peek().litValue.toInt
          acc(t) = acc.getOrElse(t, BigInt(0)) | (d << (id * 128))
          cnt(t) = cnt.getOrElse(t, 0) + 1
          total += 1
        }
        dut.clock.step(); n += 1
      }
      assert(total == txns.size * nFlits, s"expected ${txns.size * nFlits} flits, got $total")
      for (t <- txns) {
        assert(cnt.getOrElse(t, 0) == nFlits, s"txn $t flit count ${cnt.getOrElse(t, 0)}")
        assert(acc(t) == word, s"txn $t data ${acc(t).toString(16)} != ${word.toString(16)}")
      }
    }
  }

  it should "backpressure REQ intake when the ID pool is exhausted (HW-11/W14)" in {
    simulate(new ReadMultiNet(2)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke(0xBBBBL.U)
      dut.io.from.rsp.ready.poke(true.B)
      dut.io.from.dat.ready.poke(false.B) // stall read data so IDs stay allocated
      dut.io.from.rxDat.valid.poke(false.B)
      // Two reads fill the depth-2 pool.
      for (t <- Seq(7, 9)) {
        dut.io.from.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
        dut.io.from.req.bits.addr.poke(0x1000L.U)
        dut.io.from.req.bits.txnId.poke(t.U); dut.io.from.req.bits.srcId.poke(0.U)
        dut.io.from.req.valid.poke(true.B)
        var w = 0
        while (!dut.io.from.req.ready.peek().litToBoolean && w < 60) { dut.clock.step(); w += 1 }
        dut.clock.step()
      }
      // Third read must be refused while the pool is full.
      dut.io.from.req.bits.txnId.poke(11.U)
      dut.io.from.req.valid.poke(true.B)
      dut.clock.step(4)
      dut.io.from.req.ready.expect(false.B)
      // Release the stall: reads drain, pool frees, intake recovers.
      dut.io.from.dat.ready.poke(true.B)
      var n = 0
      while (!dut.io.from.req.ready.peek().litToBoolean && n < 200) { dut.clock.step(); n += 1 }
      dut.io.from.req.ready.expect(true.B)
    }
  }

  // Multi-outstanding to-side write sink: allocates a DBID per REQ, returns
  // DBIDResp/Comp via RR arbitration, reassembles write data per DBID.
  private class MultiWriteSink(depth: Int, nodeId: Int) extends Module {
    private val nFlits = ChiWidths.flitCount(64, c.datWidth)
    private val bw = math.max(1, log2Ceil(nFlits))
    private val lastId = (nFlits - 1) * (c.datWidth / 128)
    val io = IO(new Bundle {
      val req = Flipped(Decoupled(new ChiReqFlit(c)))
      val rsp = Decoupled(new ChiRspFlit(c))
      val dat = Flipped(Decoupled(new ChiDatFlit(c)))
      val wrDone = Decoupled(new Bundle { val txn = UInt(c.txnIdWidth.W); val data = UInt(512.W) })
    })
    private val pool = Module(new ChiIdPool(depth, c.txnIdWidth))
    private val sGrant :: sData :: sComp :: Nil = Enum(3)
    private class Ctx extends Bundle {
      val valid = Bool(); val reqTxn = UInt(c.txnIdWidth.W); val reqSrc = UInt(c.nodeIdWidth.W)
      val state = UInt(2.W); val buf = UInt(512.W)
    }
    private val ctx = RegInit(VecInit(Seq.fill(depth)(0.U.asTypeOf(new Ctx))))
    io.req.ready := pool.io.canAlloc
    pool.io.alloc := io.req.fire
    when(io.req.fire) {
      val e = ctx(pool.io.allocId)
      e.valid := true.B; e.reqTxn := io.req.bits.txnId; e.reqSrc := io.req.bits.srcId
      e.state := sGrant; e.buf := 0.U
    }
    private val dCtx = ctx(io.dat.bits.txnId)
    io.dat.ready := dCtx.valid && dCtx.state === sData
    when(io.dat.fire) {
      dCtx.buf := dCtx.buf | (io.dat.bits.data.pad(512) << (io.dat.bits.dataId * 128.U))
      when(io.dat.bits.dataId === lastId.U) { dCtx.state := sComp }
    }
    private val arb = Module(new Arbiter(new ChiRspFlit(c), depth))
    for (i <- 0 until depth) {
      val e = ctx(i)
      arb.io.in(i).valid := e.valid && (e.state === sGrant || e.state === sComp)
      arb.io.in(i).bits := 0.U.asTypeOf(new ChiRspFlit(c))
      arb.io.in(i).bits.srcId := nodeId.U
      arb.io.in(i).bits.txnId := e.reqTxn
      arb.io.in(i).bits.tgtId := e.reqSrc
      arb.io.in(i).bits.dbid := i.U
      arb.io.in(i).bits.opcode := Mux(e.state === sGrant, ChiOp.RspDBIDResp.U, ChiOp.RspComp.U)
    }
    LmWire.link(arb.io.out, io.rsp)
    private val doneQ = Module(new Queue(new Bundle { val txn = UInt(c.txnIdWidth.W); val data = UInt(512.W) }, depth))
    private val ch = arb.io.chosen
    private val compFire = arb.io.out.fire && ctx(ch).state === sComp
    doneQ.io.enq.valid := compFire
    doneQ.io.enq.bits.txn := ctx(ch).reqTxn
    doneQ.io.enq.bits.data := ctx(ch).buf
    LmWire.link(doneQ.io.deq, io.wrDone)
    pool.io.release := compFire
    pool.io.releaseId := ch
    when(arb.io.out.fire) {
      val e = ctx(ch)
      when(e.state === sGrant) { e.state := sData }.otherwise { e.valid := false.B }
    }
  }

  private class WriteMultiNet(outstanding: Int) extends Module {
    val io = IO(new Bundle {
      val from = new ChiSnPort(c)
      val wrDone = Decoupled(new Bundle { val txn = UInt(c.txnIdWidth.W); val data = UInt(512.W) })
    })
    private val br = Module(new ChiNocBridge(c, windows, 2, 0, 0, 0, false, outstanding))
    private val sink = Module(new MultiWriteSink(outstanding, 3))
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
    LmWire.link(sink.io.wrDone, io.wrDone)
  }

  "ChiNocBridge multi-write" should "route 2 concurrent DBID-pull writes by {src,TxnID} CAM with integrity (HW-11/W15)" in {
    simulate(new WriteMultiNet(4)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(true.B)
      dut.io.wrDone.ready.poke(true.B)
      dut.io.from.rxDat.valid.poke(false.B)
      val wordA = (BigInt(0x2222) << 256) | BigInt(0x1111)
      val wordB = (BigInt(0x4444) << 256) | BigInt(0x3333)
      val idStep = c.datWidth / 128
      // Issue two write REQs (fromTxn 5 and 8).
      for (t <- Seq(5, 8)) {
        dut.io.from.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
        dut.io.from.req.bits.addr.poke(0x1000L.U)
        dut.io.from.req.bits.txnId.poke(t.U); dut.io.from.req.bits.srcId.poke(0.U)
        dut.io.from.req.valid.poke(true.B)
        var w = 0
        while (!dut.io.from.req.ready.peek().litToBoolean && w < 60) { dut.clock.step(); w += 1 }
        dut.clock.step()
      }
      dut.io.from.req.valid.poke(false.B)
      // Collect both DBIDResps.
      val dbids = mutable.Set[Int]()
      var n = 0
      while (dbids.size < 2 && n < 200) {
        if (dut.io.from.rsp.valid.peek().litToBoolean &&
            dut.io.from.rsp.bits.opcode.peek().litValue.toInt == ChiOp.RspDBIDResp) {
          dbids += dut.io.from.rsp.bits.txnId.peek().litValue.toInt
        }
        dut.clock.step(); n += 1
      }
      assert(dbids == mutable.Set(5, 8), s"DBIDResp txns $dbids")
      // Hold completions while sending data so an early-finishing write is not
      // missed before the collection loop.
      dut.io.from.rsp.ready.poke(false.B); dut.io.wrDone.ready.poke(false.B)
      // Send write data for each fromTxn (CAM must route to the right DBID).
      for ((t, word) <- Seq((5, wordA), (8, wordB))) {
        for ((id, part) <- Seq((0, word & ((BigInt(1) << c.datWidth) - 1)),
                               (idStep, (word >> c.datWidth) & ((BigInt(1) << c.datWidth) - 1)))) {
          dut.io.from.rxDat.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
          dut.io.from.rxDat.bits.txnId.poke(t.U); dut.io.from.rxDat.bits.srcId.poke(0.U)
          dut.io.from.rxDat.bits.dataId.poke(id.U); dut.io.from.rxDat.bits.data.poke(part.U)
          dut.io.from.rxDat.valid.poke(true.B)
          var w = 0
          while (!dut.io.from.rxDat.ready.peek().litToBoolean && w < 80) { dut.clock.step(); w += 1 }
          dut.clock.step()
        }
      }
      dut.io.from.rxDat.valid.poke(false.B)
      dut.io.from.rsp.ready.poke(true.B); dut.io.wrDone.ready.poke(true.B)
      // Collect both completed words and both Comps.
      val words = mutable.Set[BigInt]()
      val comps = mutable.Set[Int]()
      n = 0
      while ((words.size < 2 || comps.size < 2) && n < 300) {
        if (dut.io.wrDone.valid.peek().litToBoolean) words += dut.io.wrDone.bits.data.peek().litValue
        if (dut.io.from.rsp.valid.peek().litToBoolean &&
            dut.io.from.rsp.bits.opcode.peek().litValue.toInt == ChiOp.RspComp) {
          comps += dut.io.from.rsp.bits.txnId.peek().litValue.toInt
        }
        dut.clock.step(); n += 1
      }
      assert(words == mutable.Set(wordA, wordB), s"write words ${words.map(_.toString(16))}")
      assert(comps == mutable.Set(5, 8), s"Comp txns $comps")
    }
  }

  // Mixed multi-outstanding slave: reads return CompData, writes use DBID pull;
  // shares the RSP arbiter and ID pool so read/write releases arbitrate together.
  private class MixedSlave(depth: Int, nodeId: Int) extends Module {
    private val nFlits = ChiWidths.flitCount(64, c.datWidth)
    private val bw = math.max(1, log2Ceil(nFlits))
    private val idStep = c.datWidth / 128
    private val lastId = (nFlits - 1) * idStep
    val io = IO(new Bundle {
      val req = Flipped(Decoupled(new ChiReqFlit(c)))
      val rsp = Decoupled(new ChiRspFlit(c))
      val dat = Decoupled(new ChiDatFlit(c))      // read data TX
      val rxDat = Flipped(Decoupled(new ChiDatFlit(c))) // write data RX
      val rdData = Input(UInt(512.W))
      val wrDone = Decoupled(new Bundle { val txn = UInt(c.txnIdWidth.W); val data = UInt(512.W) })
    })
    private val pool = Module(new ChiIdPool(depth, c.txnIdWidth))
    private val sRdData :: sWrGrant :: sWrData :: sWrComp :: Nil = Enum(4)
    private class Ctx extends Bundle {
      val valid = Bool(); val reqTxn = UInt(c.txnIdWidth.W); val reqSrc = UInt(c.nodeIdWidth.W)
      val state = UInt(2.W); val buf = UInt(512.W); val beat = UInt(bw.W)
    }
    private val ctx = RegInit(VecInit(Seq.fill(depth)(0.U.asTypeOf(new Ctx))))
    io.req.ready := pool.io.canAlloc
    pool.io.alloc := io.req.fire
    when(io.req.fire) {
      val e = ctx(pool.io.allocId)
      val wr = io.req.bits.opcode === ChiOp.WriteNoSnpFull.U
      e.valid := true.B; e.reqTxn := io.req.bits.txnId; e.reqSrc := io.req.bits.srcId
      e.state := Mux(wr, sWrGrant, sRdData); e.buf := 0.U; e.beat := 0.U
    }
    // Read data TX: RR among contexts in sRdData.
    private val rdArb = Module(new Arbiter(UInt(log2Ceil(depth).W), depth))
    for (i <- 0 until depth) {
      rdArb.io.in(i).valid := ctx(i).valid && ctx(i).state === sRdData
      rdArb.io.in(i).bits := i.U
    }
    private val rdCh = rdArb.io.out.bits
    private val rdCtx = ctx(rdCh)
    io.dat.valid := rdArb.io.out.valid
    io.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    io.dat.bits.opcode := ChiOp.DatCompData.U
    io.dat.bits.txnId := rdCtx.reqTxn
    io.dat.bits.tgtId := rdCtx.reqSrc
    io.dat.bits.dataId := rdCtx.beat * idStep.U
    io.dat.bits.data := (io.rdData >> (rdCtx.beat * c.datWidth.U))(c.datWidth - 1, 0)
    io.dat.bits.be := ((1L << c.beWidth) - 1).U
    rdArb.io.out.ready := io.dat.ready
    private val rdLast = io.dat.fire && rdCtx.beat === (nFlits - 1).U
    when(io.dat.fire) { rdCtx.beat := rdCtx.beat + 1.U }
    when(rdLast) { rdCtx.valid := false.B }
    // Write data RX by DBID.
    private val dCtx = ctx(io.rxDat.bits.txnId)
    io.rxDat.ready := dCtx.valid && dCtx.state === sWrData
    when(io.rxDat.fire) {
      dCtx.buf := dCtx.buf | (io.rxDat.bits.data.pad(512) << (io.rxDat.bits.dataId * 128.U))
      when(io.rxDat.bits.dataId === lastId.U) { dCtx.state := sWrComp }
    }
    // RSP arbiter: DBIDResp (sWrGrant) / Comp (sWrComp).
    private val rspArb = Module(new Arbiter(new ChiRspFlit(c), depth))
    for (i <- 0 until depth) {
      val e = ctx(i)
      rspArb.io.in(i).valid := e.valid && (e.state === sWrGrant || e.state === sWrComp)
      rspArb.io.in(i).bits := 0.U.asTypeOf(new ChiRspFlit(c))
      rspArb.io.in(i).bits.srcId := nodeId.U
      rspArb.io.in(i).bits.txnId := e.reqTxn
      rspArb.io.in(i).bits.tgtId := e.reqSrc
      rspArb.io.in(i).bits.dbid := i.U
      rspArb.io.in(i).bits.opcode := Mux(e.state === sWrGrant, ChiOp.RspDBIDResp.U, ChiOp.RspComp.U)
    }
    LmWire.link(rspArb.io.out, io.rsp)
    private val doneQ = Module(new Queue(new Bundle { val txn = UInt(c.txnIdWidth.W); val data = UInt(512.W) }, depth))
    private val rspCh = rspArb.io.chosen
    private val compFire = rspArb.io.out.fire && ctx(rspCh).state === sWrComp
    doneQ.io.enq.valid := compFire
    doneQ.io.enq.bits.txn := ctx(rspCh).reqTxn
    doneQ.io.enq.bits.data := ctx(rspCh).buf
    LmWire.link(doneQ.io.deq, io.wrDone)
    when(rspArb.io.out.fire) {
      val e = ctx(rspCh)
      when(e.state === sWrGrant) { e.state := sWrData }.otherwise { e.valid := false.B }
    }
    // Release: read-last-flit or write-Comp, RR into the single pool port.
    private val relArb = Module(new Arbiter(UInt(c.txnIdWidth.W), 2))
    relArb.io.in(0).valid := rdLast; relArb.io.in(0).bits := rdCh
    relArb.io.in(1).valid := compFire; relArb.io.in(1).bits := rspCh
    relArb.io.out.ready := true.B
    pool.io.release := relArb.io.out.fire
    pool.io.releaseId := relArb.io.out.bits
  }

  private class MixedNet(outstanding: Int) extends Module {
    val io = IO(new Bundle {
      val from = new ChiSnPort(c)
      val rdData = Input(UInt(512.W))
      val wrDone = Decoupled(new Bundle { val txn = UInt(c.txnIdWidth.W); val data = UInt(512.W) })
    })
    private val br = Module(new ChiNocBridge(c, windows, 2, 0, 0, 0, false, outstanding))
    private val sl = Module(new MixedSlave(outstanding, 3))
    br.io.dstClk := false.B; br.io.dstReset := false.B
    LmWire.link(io.from.req, br.io.from.req)
    LmWire.link(br.io.from.rsp, io.from.rsp)
    LmWire.link(br.io.from.dat, io.from.dat)
    LmWire.link(io.from.rxDat, br.io.from.rxDat)
    LmWire.link(br.io.to.req, sl.io.req)
    LmWire.link(sl.io.rsp, br.io.to.rsp)
    LmWire.link(sl.io.dat, br.io.to.dat)
    LmWire.link(br.io.to.txDat, sl.io.rxDat)
    br.io.to.snp.valid := false.B; br.io.to.snp.bits := 0.U.asTypeOf(new ChiSnpFlit(c))
    br.io.to.txRsp.ready := true.B
    sl.io.rdData := io.rdData
    LmWire.link(sl.io.wrDone, io.wrDone)
  }

  "ChiNocBridge mixed" should "service a concurrent read and write with shared release arbitration (HW-11/W15)" in {
    simulate(new MixedNet(4)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      val rdWord = (BigInt(0xAAAA) << 256) | BigInt(0xBBBB)
      val wrWord = (BigInt(0x4444) << 256) | BigInt(0x3333)
      val idStep = c.datWidth / 128
      dut.io.rdData.poke(rdWord.U)
      dut.io.from.rsp.ready.poke(true.B); dut.io.from.dat.ready.poke(false.B)
      dut.io.wrDone.ready.poke(false.B); dut.io.from.rxDat.valid.poke(false.B)
      // Issue a read (txn 7) and a write (txn 8).
      for ((t, op) <- Seq((7, ChiOp.ReadNoSnp), (8, ChiOp.WriteNoSnpFull))) {
        dut.io.from.req.bits.opcode.poke(op.U)
        dut.io.from.req.bits.addr.poke(0x1000L.U)
        dut.io.from.req.bits.txnId.poke(t.U); dut.io.from.req.bits.srcId.poke(0.U)
        dut.io.from.req.valid.poke(true.B)
        var w = 0
        while (!dut.io.from.req.ready.peek().litToBoolean && w < 60) { dut.clock.step(); w += 1 }
        dut.clock.step()
      }
      dut.io.from.req.valid.poke(false.B)
      // Wait for the write DBIDResp (txn 8), then send its data.
      var n = 0
      while (!(dut.io.from.rsp.valid.peek().litToBoolean &&
               dut.io.from.rsp.bits.opcode.peek().litValue.toInt == ChiOp.RspDBIDResp &&
               dut.io.from.rsp.bits.txnId.peek().litValue.toInt == 8) && n < 200) { dut.clock.step(); n += 1 }
      dut.clock.step()
      for ((id, part) <- Seq((0, wrWord & ((BigInt(1) << c.datWidth) - 1)),
                             (idStep, (wrWord >> c.datWidth) & ((BigInt(1) << c.datWidth) - 1)))) {
        dut.io.from.rxDat.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
        dut.io.from.rxDat.bits.txnId.poke(8.U); dut.io.from.rxDat.bits.srcId.poke(0.U)
        dut.io.from.rxDat.bits.dataId.poke(id.U); dut.io.from.rxDat.bits.data.poke(part.U)
        dut.io.from.rxDat.valid.poke(true.B)
        var w = 0
        while (!dut.io.from.rxDat.ready.peek().litToBoolean && w < 80) { dut.clock.step(); w += 1 }
        dut.clock.step()
      }
      dut.io.from.rxDat.valid.poke(false.B)
      // Collect read data (txn 7), write word, and the write Comp (txn 8).
      dut.io.from.dat.ready.poke(true.B); dut.io.wrDone.ready.poke(true.B)
      var rdAcc = BigInt(0); var rdCnt = 0; var gotWr = false; var gotComp = false
      n = 0
      while ((rdCnt < ChiWidths.flitCount(64, c.datWidth) || !gotWr || !gotComp) && n < 400) {
        if (dut.io.from.dat.valid.peek().litToBoolean) {
          val t = dut.io.from.dat.bits.txnId.peek().litValue.toInt
          assert(t == 7, s"read data txn $t")
          val id = dut.io.from.dat.bits.dataId.peek().litValue.toInt
          rdAcc |= dut.io.from.dat.bits.data.peek().litValue << (id * 128); rdCnt += 1
        }
        if (dut.io.wrDone.valid.peek().litToBoolean) {
          assert(dut.io.wrDone.bits.data.peek().litValue == wrWord, "write word")
          gotWr = true
        }
        if (dut.io.from.rsp.valid.peek().litToBoolean &&
            dut.io.from.rsp.bits.opcode.peek().litValue.toInt == ChiOp.RspComp &&
            dut.io.from.rsp.bits.txnId.peek().litValue.toInt == 8) gotComp = true
        dut.clock.step(); n += 1
      }
      assert(rdAcc == rdWord, s"read word ${rdAcc.toString(16)}")
      assert(gotWr && gotComp, s"write completion wr=$gotWr comp=$gotComp")
    }
  }
}