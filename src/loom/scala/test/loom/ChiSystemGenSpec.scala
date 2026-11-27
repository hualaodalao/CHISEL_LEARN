package loom

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import loom.gen.ChiAssemble
import loom.ir._
import loom.rtl.LmWire
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** GEN-25a golden sim (UP-07 phase 2): mirrors ChiSystemSpec, but the system
  * params come from a declared loom/v2 IR through ChiAssemble.systemParams
  * (SEM-20a synthetic bridge endpoints), proving IR -> assembly -> hardware
  * end-to-end. Subsystem A forwards window 0x2000 to subsystem B over one
  * sync nocLink; the free RN port of A reads/writes the SN of B.
  */
class ChiSystemGenSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val chi = ChiBlockV2(datWidth = 256, txnIdWidth = 8, nodeIdWidth = 7,
    addrWidth = 32, outstanding = 64, atomicEnable = true, atomicEntries = 4,
    dvmEnable = false, portMode = "chi", pcrdTypes = 1, lcreditDepth = 4)

  /** One-router subsystem: RN local port 0, SN local port 1 (mirrors the
    * ChiSystemSpec geometry), one 4 KB SAM window at samBase. */
  private def sub(name: String, rn: String, sn: String, router: String,
      samBase: Long): SubsystemSpec =
    SubsystemSpec(name, Seq(ClockDomain("core", 1000)),
      Seq(NodeV2(rn, "rn-i", None, "core", router, "chi", 256, 64, 0),
        NodeV2(sn, "sn-f", None, "core", router, "chi", 256, 64, 0)),
      Seq(SamWindowV2(samBase, 4096L, sn)),
      QosV2(V2Defaults.QosMapping, V2Defaults.QosClasses), chi,
      Seq(RouterV2(router, "core", None, None, None, "any")),
      Seq(LinkV2(rn, router, 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4),
        LinkV2(router, sn, 256, "sync", 0, 0, false, Seq("req", "rsp", "dat"), 4)),
      TimingSpec(8, 12))

  private val ir: IrV2 = IrV2(
    Meta("gen_hier", "loom/v2"), Seq(ClockDomain("core", 1000)),
    Nil, Nil, QosV2(V2Defaults.QosMapping, V2Defaults.QosClasses), chi,
    Nil, Nil, TimingSpec(8, 12), partitions = Nil,
    subsystems = Seq(sub("A", "mA", "sA", "rA0", 0x1000L),
      sub("B", "mB", "sB", "rB0", 0x2000L)),
    nocLinks = Seq(NocLinkSpec("A", "B", "rA0", "rB0",
      Seq((0x2000L, 0x1000L)), "sync", 0, false)))

  private val (nets, bridges) = ChiAssemble.systemParams(ir)
  private val c = nets.head.cfg
  // Bridge SN is A's synthetic slot (nodeId 2 after mA=0/sA=1); the write
  // data flits target it from the free RN port. B's SN nodeId shifts past
  // the synthetic bridge RN (mB=0, bridge RN=1, sB=2) — look it up, never
  // assume insertion order.
  private val bridgeSnId = nets(0).sn(bridges.head.fromSn).nodeId
  private val sinkNodeId = nets(1).sn.head.nodeId

  /** Idle the endpoints the stimulus does not use: A's local SN (freeSn 0)
    * and B's free RN (freeRn 1). freeRn 0 = mA, freeSn 1 = sB. */
  private def idleUnused(sys: ChiSystemTop): Unit = {
    sys.io.sn(0).req.ready := false.B
    sys.io.sn(0).rsp.valid := false.B
    sys.io.sn(0).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    sys.io.sn(0).dat.valid := false.B
    sys.io.sn(0).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    sys.io.sn(0).rxDat.ready := false.B
    sys.io.rn(1).req.valid := false.B
    sys.io.rn(1).req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
    sys.io.rn(1).txRsp.valid := false.B
    sys.io.rn(1).txRsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    sys.io.rn(1).txDat.valid := false.B
    sys.io.rn(1).txDat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    sys.io.rn(1).rsp.ready := false.B
    sys.io.rn(1).dat.ready := false.B
    sys.io.rn(1).snp.ready := false.B
  }

  private class ReadNet extends Module {
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val rdData = Input(UInt(512.W))
    })
    val sys = Module(new ChiSystemTop(nets, bridges))
    val src = Module(new ChiReadSource(c))
    sys.io.rn(0) <> io.rn
    LmWire.link(sys.io.sn(1).req, src.io.req)
    LmWire.link(src.io.dat, sys.io.sn(1).dat)
    sys.io.sn(1).rsp.valid := false.B
    sys.io.sn(1).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    sys.io.sn(1).rxDat.ready := true.B
    idleUnused(sys)
    src.io.rdData := io.rdData
  }

  "ChiAssemble.systemParams + ChiSystemTop" should
      "proxy a cross-bridge ReadNoSnp from IR-declared subsystems (GEN-25a golden)" in {
    // the IR is gate-clean and the bridge mapping is the SEM-20a contract
    loom.check.ChecksV2.run(ir).errors shouldBe Nil
    bridges shouldBe Seq(ChiBridgeLink(0, 1, 1, 1, Seq((0x2000L, 0x1000L)), 0, 0, false))
    simulate(new ReadNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(true.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      dut.io.rn.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.rn.req.bits.addr.poke(0x2000L.U)
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
    val sys = Module(new ChiSystemTop(nets, bridges))
    val sink = Module(new ChiWriteSink(c, 2, sinkNodeId))
    sys.io.rn(0) <> io.rn
    LmWire.link(sys.io.sn(1).req, sink.io.req)
    LmWire.link(sink.io.rsp, sys.io.sn(1).rsp)
    LmWire.link(sys.io.sn(1).rxDat, sink.io.dat)
    sys.io.sn(1).dat.valid := false.B
    sys.io.sn(1).dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
    idleUnused(sys)
    LmWire.link(sink.io.wrDone, io.wrData)
  }

  it should "proxy a DBID pull write across the IR-assembled bridge with data integrity (GEN-25a golden)" in {
    simulate(new WriteNet) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.rn.rsp.ready.poke(true.B); dut.io.rn.dat.ready.poke(true.B)
      dut.io.rn.snp.ready.poke(true.B)
      dut.io.rn.txRsp.valid.poke(false.B); dut.io.rn.txDat.valid.poke(false.B)
      dut.io.wrData.ready.poke(true.B)
      dut.io.rn.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
      dut.io.rn.req.bits.addr.poke(0x2000L.U)
      dut.io.rn.req.bits.txnId.poke(5.U); dut.io.rn.req.bits.srcId.poke(0.U)
      dut.io.rn.req.valid.poke(true.B)
      var n = 0
      while (!dut.io.rn.rsp.valid.peek().litToBoolean && n < 200) { dut.clock.step(); n += 1 }
      dut.io.rn.req.valid.poke(false.B)
      dut.io.rn.rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.io.rn.rsp.bits.txnId.expect(5.U)
      dut.clock.step()
      // RN sends write data targeting the synthetic bridge SN slot.
      dut.io.rn.txDat.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
      dut.io.rn.txDat.bits.txnId.poke(5.U); dut.io.rn.txDat.bits.tgtId.poke(bridgeSnId.U)
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

  // ---- S12 (GEN-25b/SEM-20b): IR-derived async cross-root-clock golden ----

  /** B on its own root clock io@500 (A stays core@1000): every B node/router
    * moves to io so the projection stays domain-0 clean. */
  private def subIo(name: String, rn: String, sn: String, router: String,
      samBase: Long): SubsystemSpec = {
    val s = sub(name, rn, sn, router, samBase)
    s.copy(clockDomains = Seq(ClockDomain("io", 500)),
      nodes = s.nodes.map(_.copy(clock = "io")),
      routers = s.routers.map(_.copy(clock = "io")))
  }

  private val irAsync: IrV2 = ir.copy(
    meta = ir.meta.copy(name = "gen_hier_async"),
    subsystems = Seq(ir.subsystems.head, subIo("B", "mB", "sB", "rB0", 0x2000L)),
    nocLinks = Seq(NocLinkSpec("A", "B", "rA0", "rB0",
      Seq((0x2000L, 0x1000L)), "async", 4, false)))

  private val (netsAsync, bridgesAsync) = ChiAssemble.systemParams(irAsync)

  /** Mirrors ChiSystemSpec's HW-15 case: the remote storage lives in B's
    * root clock domain, driven through the system subClk/subReset inputs. */
  private class AsyncReadNet extends Module {
    val io = IO(new Bundle {
      val rn = Flipped(new ChiRnPort(c))
      val rdData = Input(UInt(512.W))
      val subClk = Input(Bool())
      val subReset = Input(Bool())
    })
    val sys = Module(new ChiSystemTop(netsAsync, bridgesAsync, ChiAssemble.subDomains(irAsync)))
    val src = withClockAndReset(io.subClk.asClock, io.subReset.asAsyncReset) {
      Module(new ChiReadSource(c))
    }
    sys.io.subClk(0) := io.subClk
    sys.io.subReset(0) := io.subReset
    sys.io.rn(0) <> io.rn
    LmWire.link(sys.io.sn(1).req, src.io.req)
    LmWire.link(src.io.dat, sys.io.sn(1).dat)
    sys.io.sn(1).rsp.valid := false.B
    sys.io.sn(1).rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
    sys.io.sn(1).rxDat.ready := true.B
    idleUnused(sys)
    src.io.rdData := io.rdData
  }

  it should "cross an async bridge between IR-derived root clocks bit-exact (GEN-25b golden, HW-15)" in {
    loom.check.ChecksV2.run(irAsync).errors shouldBe Nil
    ChiAssemble.subDomains(irAsync) shouldBe Seq(0, 1)
    bridgesAsync.head.asyncFifoDepth shouldBe 4
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
      dut.io.rn.req.bits.addr.poke(0x2000L.U)
      dut.io.rn.req.bits.txnId.poke(7.U); dut.io.rn.req.bits.srcId.poke(0.U)
      dut.io.rn.req.valid.poke(true.B)
      val nFlits = ChiWidths.flitCount(64, c.datWidth)
      var acc = BigInt(0); var got = 0; var n = 0; var clk = false
      while (got < nFlits && n < 800) {
        if (dut.io.rn.dat.valid.peek().litToBoolean) {
          dut.io.rn.dat.bits.opcode.expect(ChiOp.DatCompData.U)
          val id = dut.io.rn.dat.bits.dataId.peek().litValue.toInt
          acc |= dut.io.rn.dat.bits.data.peek().litValue << (id * 128)
          dut.io.rn.dat.bits.txnId.expect(7.U)
          got += 1
        }
        dut.io.subClk.poke(clk.B); clk = !clk
        dut.clock.step(); n += 1
      }
      assert(got == nFlits, s"expected $nFlits flits, got $got")
      assert(acc == word, s"async IR golden read ${acc.toString(16)} != ${word.toString(16)}")
    }
  }
}