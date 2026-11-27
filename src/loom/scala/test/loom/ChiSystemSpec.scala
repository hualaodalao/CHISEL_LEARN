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

  // System boundary ports are MixedVec[Data] (per-free-slot widths, HW-16);
  // these tests use fabric-width chi slots throughout.
  private def rnOf(m: ChiSystemTop, k: Int): ChiRnPort = m.io.rn(k).asInstanceOf[ChiRnPort]
  private def snOf(m: ChiSystemTop, k: Int): ChiSnPort = m.io.sn(k).asInstanceOf[ChiSnPort]

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
    rnOf(sys, 0) <> io.rn
    LmWire.link(snOf(sys, 0).req, src.io.req)
    LmWire.link(src.io.dat, snOf(sys, 0).dat)
    snOf(sys, 0).rsp.valid := false.B; snOf(sys, 0).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    snOf(sys, 0).rxDat.ready := true.B
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
    rnOf(sys, 0) <> io.rn
    LmWire.link(snOf(sys, 0).req, sink.io.req)
    LmWire.link(sink.io.rsp, snOf(sys, 0).rsp)
    LmWire.link(snOf(sys, 0).rxDat, sink.io.dat)
    snOf(sys, 0).dat.valid := false.B; snOf(sys, 0).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
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

  private class ReadMoNet(outstanding: Int) extends Module {
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val rdData = Input(UInt(512.W))
    })
    val sys = Module(new ChiSystemTop(Seq(sub("a"), sub("b")),
      Seq(ChiBridgeLink(0, 0, 1, 0, win, outstanding = outstanding))))
    val src = Module(new ChiReadSource(c))
    rnOf(sys, 0) <> io.rn
    LmWire.link(snOf(sys, 0).req, src.io.req)
    LmWire.link(src.io.dat, snOf(sys, 0).dat)
    snOf(sys, 0).rsp.valid := false.B; snOf(sys, 0).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    snOf(sys, 0).rxDat.ready := true.B
    src.io.rdData := io.rdData
  }

  it should "pass ChiBridgeLink.outstanding through to the bridge (HW-17)" in {
    // A depth-1 bridge serializes; depth-2 lets a second REQ be accepted before
    // the first read returns, which is observable on the system boundary.
    simulate(new ReadMoNet(2)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(false.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      for (t <- Seq(7, 9)) {
        dut.io.rn.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
        dut.io.rn.req.bits.addr.poke(0x1000L.U)
        dut.io.rn.req.bits.txnId.poke(t.U); dut.io.rn.req.bits.srcId.poke(0.U)
        dut.io.rn.req.valid.poke(true.B)
        var w = 0
        while (!dut.io.rn.req.ready.peek().litToBoolean && w < 80) { dut.clock.step(); w += 1 }
        dut.io.rn.req.ready.expect(true.B)
        dut.clock.step()
      }
      dut.io.rn.req.valid.poke(false.B)
      dut.io.rn.dat.ready.poke(true.B)
      val nFlits = ChiWidths.flitCount(64, c.datWidth)
      val word = (BigInt(0xAAAA) << 256) | BigInt(0xBBBB)
      val cnt = scala.collection.mutable.Map[Int, Int]()
      val acc = scala.collection.mutable.Map[Int, BigInt]()
      var n = 0
      while (cnt.values.sum < 2 * nFlits && n < 300) {
        if (dut.io.rn.dat.valid.peek().litToBoolean) {
          val t = dut.io.rn.dat.bits.txnId.peek().litValue.toInt
          val id = dut.io.rn.dat.bits.dataId.peek().litValue.toInt
          cnt(t) = cnt.getOrElse(t, 0) + 1
          acc(t) = acc.getOrElse(t, BigInt(0)) | (dut.io.rn.dat.bits.data.peek().litValue << (id * 128))
        }
        dut.clock.step(); n += 1
      }
      assert(cnt.getOrElse(7, 0) == nFlits && cnt.getOrElse(9, 0) == nFlits, s"per-txn flits $cnt")
      assert(acc(7) == word && acc(9) == word, s"per-txn words ${acc.map { case (k, v) => k -> v.toString(16) }}")
    }
  }

  // Two subsystems on different root clocks joined by an async bridge (HW-15).
  private class AsyncReadNet extends Module {
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val rdData = Input(UInt(512.W))
      val subClk = Input(Bool())
      val subReset = Input(Bool())
    })
    val sys = Module(new ChiSystemTop(Seq(sub("a"), sub("b")),
      Seq(ChiBridgeLink(0, 0, 1, 0, win, asyncFifoDepth = 4)), subDomain = Seq(0, 1)))
    // The to-side memory lives in subsystem b's clock domain.
    val src = withClockAndReset(io.subClk.asClock, io.subReset.asAsyncReset) {
      Module(new ChiReadSource(c))
    }
    sys.io.subClk(0) := io.subClk
    sys.io.subReset(0) := io.subReset
    rnOf(sys, 0) <> io.rn
    LmWire.link(snOf(sys, 0).req, src.io.req)
    LmWire.link(src.io.dat, snOf(sys, 0).dat)
    snOf(sys, 0).rsp.valid := false.B; snOf(sys, 0).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    snOf(sys, 0).rxDat.ready := true.B
    src.io.rdData := io.rdData
  }

  it should "cross an async bridge between two subsystem root clocks (HW-15)" in {
    simulate(new AsyncReadNet) { dut =>
      dut.io.subReset.poke(true.B); dut.reset.poke(true.B)
      // Toggle the remote clock during reset so its domain sees reset edges.
      for (i <- 0 until 4) { dut.io.subClk.poke((i % 2 == 0).B); dut.clock.step() }
      dut.reset.poke(false.B); dut.io.subReset.poke(false.B)
      val word = (BigInt(0xAAAA) << 256) | BigInt(0xBBBB)
      dut.io.rdData.poke(word.U)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(true.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      dut.io.rn.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.rn.req.bits.addr.poke(0x1000L.U)
      dut.io.rn.req.bits.txnId.poke(7.U); dut.io.rn.req.bits.srcId.poke(0.U)
      dut.io.rn.req.valid.poke(true.B)
      val nFlits = ChiWidths.flitCount(64, c.datWidth)
      var acc = BigInt(0); var got = 0; var n = 0; var clk = false
      while (got < nFlits && n < 800) {
        if (dut.io.rn.dat.valid.peek().litToBoolean) {
          val id = dut.io.rn.dat.bits.dataId.peek().litValue.toInt
          acc |= dut.io.rn.dat.bits.data.peek().litValue << (id * 128)
          dut.io.rn.dat.bits.txnId.expect(7.U)
          got += 1
        }
        dut.io.subClk.poke(clk.B); clk = !clk
        dut.clock.step(); n += 1
      }
      assert(got == nFlits, s"expected $nFlits flits, got $got")
      assert(acc == word, s"async system read ${acc.toString(16)} != ${word.toString(16)}")
    }
  }

  // Test-local adapted subsystem top: the free SN node runs at 128b while the
  // fabric is 256b, with a DAT gearbox at that node boundary (HW-16 hook).
  private class AdaptedSnNet(pp: ChiNetworkParams) extends ChiNetworkLike(pp) {
    private val cf = pp.cfg
    private val cn = cf.copy(datWidth = 128)
    override val io = IO(new ChiNetworkIO {
      val rn = MixedVec(pp.rn.map(_ => Flipped(new ChiRnPort(cf)): Data))
      val sn = MixedVec(pp.sn.map(_ => Flipped(new ChiSnPort(cn)): Data))
      val domClk = Vec(pp.nDomain - 1, Input(Bool()))
      val domReset = Vec(pp.nDomain - 1, Input(Bool()))
    })
    private val net = Module(new ChiNetworkTop(pp))
    for (i <- pp.rn.indices) io.rn(i) <> net.io.rn(i)
    for (j <- pp.sn.indices) {
      val ext = io.sn(j).asInstanceOf[ChiSnPort]
      val fab = net.io.sn(j)
      LmWire.link(fab.req, ext.req)
      LmWire.link(ext.rsp, fab.rsp)
      val up = Module(new ChiDatWidth(cn, cf))
      LmWire.link(ext.dat, up.io.in); LmWire.link(up.io.out, fab.dat)
      val dn = Module(new ChiDatWidth(cf, cn))
      LmWire.link(fab.rxDat, dn.io.in); LmWire.link(dn.io.out, ext.rxDat)
    }
    for (d <- 0 until pp.nDomain - 1) {
      net.io.domClk(d) := io.domClk(d); net.io.domReset(d) := io.domReset(d)
    }
  }

  private class AdaptedReadNet extends Module {
    val cn = ChiConfig(datWidth = 128)
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val rdData = Input(UInt(512.W))
    })
    private val subB = sub("b").copy(adapt = Seq(ChiAdapt(1, 128, c.datWidth, 0, mismatch = true)))
    val sys = Module(new ChiSystemTop(Seq(sub("a"), subB), bridges,
      mkNet = p => if (p.adapt.nonEmpty) Module(new AdaptedSnNet(p)) else Module(new ChiNetworkUniform(p))))
    val src = Module(new ChiReadSource(cn))
    rnOf(sys, 0) <> io.rn
    private val mem = sys.io.sn(0).asInstanceOf[ChiSnPort]
    LmWire.link(mem.req, src.io.req)
    LmWire.link(src.io.dat, mem.dat)
    mem.rsp.valid := false.B; mem.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(cn))
    mem.rxDat.ready := true.B
    src.io.rdData := io.rdData
  }

  it should "honor a custom mkNet factory and expose the adapted node width (HW-16)" in {
    simulate(new AdaptedReadNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      val word = (BigInt(0xAAAA) << 256) | BigInt(0xBBBB)
      dut.io.rdData.poke(word.U)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(true.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      dut.io.rn.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.rn.req.bits.addr.poke(0x1000L.U)
      dut.io.rn.req.bits.txnId.poke(7.U); dut.io.rn.req.bits.srcId.poke(0.U)
      dut.io.rn.req.valid.poke(true.B)
      val nFlits = ChiWidths.flitCount(64, c.datWidth)
      var acc = BigInt(0); var got = 0; var n = 0
      while (got < nFlits && n < 400) {
        if (dut.io.rn.dat.valid.peek().litToBoolean) {
          val id = dut.io.rn.dat.bits.dataId.peek().litValue.toInt
          acc |= dut.io.rn.dat.bits.data.peek().litValue << (id * 128)
          dut.io.rn.dat.bits.txnId.expect(7.U)
          got += 1
        }
        dut.clock.step(); n += 1
      }
      assert(got == nFlits, s"expected $nFlits fabric flits, got $got")
      assert(acc == word, s"adapted subsystem read ${acc.toString(16)} != ${word.toString(16)}")
    }
  }
}