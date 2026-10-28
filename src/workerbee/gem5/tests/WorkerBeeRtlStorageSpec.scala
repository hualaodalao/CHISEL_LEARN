import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

class WbRtlCalcProbe(cfg: WorkerBeeConfig) extends Module {
  val core = Module(new WorkerBeeCalcConfig(cfg))
  val io = IO(chiselTypeOf(core.io))
  io <> core.io
  val state = IO(Output(UInt(2.W)))
  state := BoringUtils.tapAndRead(core.state)
}

class WbRtlCvtProbe(cfg: WorkerBeeConfig) extends Module {
  val fifo = Module(new StreamFifo(UInt(cfg.cExtW.W), cfg.cBufferDepth))
  val cvt = Module(new WbOutCvt(cfg))
  val io = IO(new Bundle {
    val push = Flipped(Decoupled(UInt(cfg.cExtW.W)))
    val gate = Input(Bool())
    val outFmt = Input(DataFormat())
    val outReady = Input(Bool())
    val scaleReady = Input(Bool())
    val occupancy = Output(UInt(log2Ceil(cfg.cBufferDepth + 1).W))
    val rawPop = Output(Bool())
    val inReady = Output(Bool())
    val outValid = Output(Bool())
    val outData = Output(UInt((cfg.totalN * 32).W))
    val scaleValid = Output(Bool())
  })
  fifo.io.push.valid := io.push.valid
  fifo.io.push.payload := io.push.bits
  io.push.ready := fifo.io.push.ready
  fifo.io.flush := false.B
  cvt.io.in.valid := fifo.io.pop.valid && io.gate
  cvt.io.in.bits := fifo.io.pop.payload
  fifo.io.pop.ready := io.gate && cvt.io.in.ready
  cvt.io.outFmt := io.outFmt
  cvt.io.rnd := RoundingMode.RNE
  cvt.io.blkSel := MXBlockSize.M16
  cvt.io.nEff := cfg.totalN.U
  cvt.io.out.ready := io.outReady
  cvt.io.scaleOut.ready := io.scaleReady
  cvt.io.rqM.get := VecInit(Seq.fill(cfg.totalN)(0.U(32.W)))
  cvt.io.rqEn.get := false.B
  cvt.io.rqShift.get := 0.U
  cvt.io.rqZp.get := 0.S
  cvt.io.reluOn.get := false.B
  io.occupancy := fifo.io.occupancy
  io.rawPop := fifo.io.pop.fire
  io.inReady := cvt.io.in.ready
  io.outValid := cvt.io.out.valid
  io.outData := cvt.io.out.bits
  io.scaleValid := cvt.io.scaleOut.valid
}

class WorkerBeeRtlStorageSpec extends RtlEdgeSuite {
  behavior of "WorkerBee RTL storage"

  it should "fifo_boundary" in {
    simulate(new WorkerBeeScratchpad(cfg)) { dut =>
      val channels = Seq(
        ("A", dut.io.aPush, dut.io.aPop, Some(dut.io.aOccupancy)),
        ("B", dut.io.bPush, dut.io.bPop, Some(dut.io.bOccupancy)),
        ("C", dut.io.cPush, dut.io.cPop, Some(dut.io.cOccupancy)),
        ("scaleA", dut.io.scaleAPush.get, dut.io.scaleAPop.get, None),
        ("scaleB", dut.io.scaleBPush.get, dut.io.scaleBPop.get, Some(dut.io.scaleBOccupancy.get)))
      channels.foreach { case (_, push, pop, _) => push.valid.poke(false.B); push.payload.poke(0.U); pop.ready.poke(false.B) }
      dut.io.flushA.poke(false.B); dut.io.flushB.poke(false.B); dut.io.flushC.poke(false.B)
      dut.io.flushScaleA.get.poke(false.B); dut.io.flushScaleB.get.poke(false.B)
      dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B)
      var cycle = 0
      for ((name, push, pop, occupancy) <- channels) {
        def tick(): Unit = {
          val fields = Seq("push_valid" -> push.valid, "push_ready" -> push.ready,
            "pop_valid" -> pop.valid, "pop_ready" -> pop.ready, "pop_data" -> pop.payload) ++
            occupancy.toSeq.map("occupancy" -> _)
          sample(s"fifo_$name", cycle, fields: _*)
          dut.clock.step(); cycle += 1
        }
        push.valid.poke(true.B); push.payload.poke(11.U); pop.ready.poke(true.B)
        push.ready.expect(true.B); pop.valid.expect(false.B); tick()
        occupancy.foreach(_.expect(1.U)); pop.valid.expect(true.B); pop.payload.expect(11.U)
        push.payload.poke(12.U); tick()
        occupancy.foreach(_.expect(1.U)); pop.payload.expect(12.U)
        push.valid.poke(false.B); tick()
        pop.valid.expect(false.B); occupancy.foreach(_.expect(0.U))
        pop.ready.poke(false.B); push.valid.poke(true.B)
        for (i <- 0 until 64) { push.payload.poke((100 + i).U); push.ready.expect(true.B); tick() }
        occupancy.foreach(_.expect(64.U)); push.ready.expect(false.B)
        push.payload.poke(999.U); pop.ready.poke(true.B)
        pop.payload.expect(100.U); push.ready.expect(false.B); tick()
        occupancy.foreach(_.expect(63.U)); push.ready.expect(true.B)
        push.valid.poke(false.B)
        for (i <- 1 until 64) { pop.valid.expect(true.B); pop.payload.expect((100 + i).U); tick() }
        pop.valid.expect(false.B); occupancy.foreach(_.expect(0.U)); pop.ready.poke(false.B)
      }
      dut.io.cPush.valid.poke(true.B); dut.io.cPush.payload.poke(77.U)
      dut.io.flushC.poke(true.B)
      sample("fifo_flush", cycle, "push_ready" -> dut.io.cPush.ready, "occupancy" -> dut.io.cOccupancy)
      dut.clock.step()
      dut.io.cPush.valid.poke(false.B); dut.io.flushC.poke(false.B)
      dut.io.cOccupancy.expect(0.U); dut.io.cPop.valid.expect(false.B)
      sample("fifo_flush", cycle + 1, "occupancy" -> dut.io.cOccupancy, "pop_valid" -> dut.io.cPop.valid)
    }
  }

  it should "calc_config_edges" in {
    simulate(new WbRtlCalcProbe(cfg)) { dut =>
      clearInputs(dut.io.regFile)
      dut.io.regFile.aFmt.poke(DataFormat.INT8); dut.io.regFile.bFmt.poke(DataFormat.INT8)
      dut.io.regFile.cFmt.poke(DataFormat.INT32)
      dut.io.regFile.m.poke(1.U); dut.io.regFile.n.poke(16.U); dut.io.regFile.k.poke(32.U)
      dut.io.startIn.poke(false.B)
      dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B)
      var cycle = 0
      def tick(): Unit = {
        sample("calc_config_edges", cycle, "state" -> dut.state, "start_in" -> dut.io.startIn,
          "start_out" -> dut.io.startOut, "error" -> dut.io.err,
          "error_pulse" -> dut.io.errPluse, "error_code" -> dut.io.errCode)
        dut.clock.step(); cycle += 1
      }
      for (invalid <- Seq(false, true, false)) {
        dut.io.regFile.m.poke((if (invalid) 49 else 1).U)
        dut.io.startIn.poke(true.B); dut.io.startOut.expect(false.B); tick()
        dut.io.startIn.poke(false.B)
        dut.state.expect(1.U); dut.io.startOut.expect(false.B); dut.io.err.expect(false.B); tick()
        dut.state.expect(2.U); dut.io.startOut.expect((!invalid).B)
        dut.io.err.expect(invalid.B); dut.io.errPluse.expect(invalid.B)
        dut.io.errCode.expect((if (invalid) 7 else 0).U)
        if (!invalid) {
          dut.io.calcConfig.mTile.expect(1.U); dut.io.calcConfig.nTile.expect(1.U)
          dut.io.calcConfig.kTile.expect(1.U); dut.io.calcConfig.cDmaBufBeatTar.expect(1.U)
        }
        tick(); dut.state.expect(3.U); dut.io.startOut.expect(false.B); dut.io.errPluse.expect(false.B)
        tick(); dut.state.expect(3.U); dut.io.err.expect(invalid.B)
      }
      tick()
    }
  }

  it should "cvt_release_edges" in {
    simulate(new WbRtlCvtProbe(cfg)) { dut =>
      dut.io.push.valid.poke(false.B); dut.io.push.bits.poke(0.U)
      dut.io.gate.poke(false.B); dut.io.outReady.poke(false.B); dut.io.scaleReady.poke(false.B)
      dut.io.outFmt.poke(DataFormat.INT32)
      dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B)
      var cycle = 0
      def tick(): Unit = {
        sample("cvt_release_edges", cycle, "occupancy" -> dut.io.occupancy,
          "raw_pop" -> dut.io.rawPop, "in_ready" -> dut.io.inReady,
          "out_valid" -> dut.io.outValid, "out_ready" -> dut.io.outReady,
          "out_data" -> dut.io.outData, "scale_valid" -> dut.io.scaleValid,
          "scale_ready" -> dut.io.scaleReady)
        dut.clock.step(); cycle += 1
      }
      def row(value: Int): BigInt = (0 until cfg.totalN).foldLeft(BigInt(0))((bits, i) => bits | (BigInt(value) << (i * cfg.cEffW)))
      def packed(value: Int): BigInt = (0 until cfg.totalN).foldLeft(BigInt(0))((bits, i) => bits | (BigInt(value) << (i * 32)))
      dut.io.push.valid.poke(true.B); dut.io.push.bits.poke(row(17).U); tick()
      dut.io.push.bits.poke(row(23).U); tick(); dut.io.push.valid.poke(false.B)
      dut.io.occupancy.expect(2.U); dut.io.outValid.expect(false.B)
      dut.io.gate.poke(true.B); dut.io.rawPop.expect(true.B); tick()
      dut.io.occupancy.expect(1.U); dut.io.outValid.expect(true.B); dut.io.outData.expect(packed(17).U)
      dut.io.rawPop.expect(false.B); dut.io.inReady.expect(false.B)
      dut.io.outFmt.poke(DataFormat.MXE4M3)
      for (_ <- 0 until 3) { dut.io.outData.expect(packed(17).U); dut.io.scaleValid.expect(false.B); tick() }
      dut.io.outFmt.poke(DataFormat.INT32); dut.io.outReady.poke(true.B)
      dut.io.rawPop.expect(true.B); dut.io.inReady.expect(true.B); tick()
      dut.io.occupancy.expect(0.U); dut.io.outValid.expect(true.B); dut.io.outData.expect(packed(23).U)
      tick(); dut.io.outValid.expect(false.B)
      for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)) {
        dut.io.outReady.poke(false.B); dut.io.scaleReady.poke(false.B); dut.io.outFmt.poke(fmt)
        dut.io.push.valid.poke(true.B); dut.io.push.bits.poke(0.U); tick()
        dut.io.push.valid.poke(false.B); dut.io.rawPop.expect(true.B); tick()
        dut.io.occupancy.expect(0.U); dut.io.outValid.expect(true.B); dut.io.inReady.expect(false.B)
        dut.io.scaleReady.poke(true.B)
        for (_ <- 0 until 2) { dut.io.scaleValid.expect(false.B); tick() }
        dut.io.scaleReady.poke(false.B); dut.io.outReady.poke(true.B)
        dut.io.scaleValid.expect(true.B); dut.io.inReady.expect(false.B); tick()
        dut.io.outValid.expect(false.B); dut.io.scaleValid.expect(true.B); dut.io.inReady.expect(false.B)
        tick(); dut.io.scaleReady.poke(true.B); dut.io.inReady.expect(true.B); tick()
        dut.io.outValid.expect(false.B); dut.io.scaleValid.expect(false.B)
        dut.io.outReady.poke(false.B); dut.io.scaleReady.poke(false.B)
        dut.io.push.valid.poke(true.B); tick(); dut.io.push.valid.poke(false.B); tick()
        dut.io.outReady.poke(true.B); dut.io.scaleReady.poke(true.B)
        dut.io.inReady.expect(true.B); dut.io.scaleValid.expect(true.B); tick()
        dut.io.outValid.expect(false.B); dut.io.scaleValid.expect(false.B)
      }
      tick()
    }
  }
}
