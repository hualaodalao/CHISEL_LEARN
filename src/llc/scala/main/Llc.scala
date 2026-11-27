import chisel3._
import chisel3.util._

/** Last Level Cache top module.
  *
  * Provides AXI4 slave port (cpu) and AXI4 master port (mem).
  * Internally instantiates LlcPipeline, LlcWbBuffer, and LlcWriteMergeBuffer.
  *
  * Responsibilities:
  *   - AXI4 slave front-end: accept AR/AW+W, convert to unified LlcReq
  *   - Write Merge Buffer: combine writes to same cache line before pipeline
  *   - AXI4 slave responses: generate R burst / B responses
  *   - AXI4 master back-end: refill AR+R, writeback AW+W+B
  */
class Llc(cfg: LlcConfig) extends Module {
  val io = IO(new Bundle {
    val cpu  = slave(Axi4(cfg.axiSlaveConfig))
    val mem  = master(Axi4(cfg.axiMasterConfig))
    val ctrl = slave(AxiLite4(cfg.axiLiteConfig))
  })

  // ======== Sub-modules ========
  val pipeline = Module(new LlcPipeline(cfg))
  val wbBuffer = Module(new LlcWbBuffer(cfg))
  val wmBuf    = Module(new LlcWriteMergeBuffer(cfg))
  val perfCounters = Module(new LlcPerfCounters(cfg))

  // ======== Connect MSHR writeback → WbBuffer ========
  // Note: pipeline.io.wbReq is sourced either from the MSHR (normal eviction) or
  // from the flush FSM (clean-flush/invalidate); the flush FSM internally
  // arbitrates, so the top level always drives WbBuffer from this single port.
  wbBuffer.io.enq.valid        := pipeline.io.wbReq.valid
  wbBuffer.io.enq.bits.address := pipeline.io.wbReq.bits.address
  wbBuffer.io.enq.bits.data    := pipeline.io.wbReq.bits.data
  wbBuffer.io.enq.bits.owner   := pipeline.io.wbReq.bits.owner

  // Feed WbBuffer status back so the flush FSM can spin on enqReady and drain.
  pipeline.io.wbBufEmpty    := wbBuffer.io.empty
  pipeline.io.wbBufEnqReady := wbBuffer.io.enqReady
  pipeline.io.wmBufEmpty    := wmBuf.io.empty

  // ======== Read path state machine ========
  val sRdIdle :: sRdSnoop :: sRdFlushWait :: sRdPending :: sRdBurst :: Nil = Enum(5)
  val rdState   = RegInit(sRdIdle)
  val rdAddr    = Reg(UInt(cfg.addressWidth.W))
  val rdId      = Reg(UInt(cfg.idWidth.W))
  val rdLen     = Reg(UInt(8.W))
  val rdLegal   = RegInit(true.B)
  val rdData    = Reg(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))
  // Wide enough to count an illegal burst of up to 256 beats
  val rdBeatCnt = RegInit(0.U(8.W))
  val rdReqValid = RegInit(false.B)
  val beatIdxBits = log2Ceil(cfg.beatsPerLine)
  val rdBeatInLine = rdAddr(cfg.offsetBits - 1, log2Ceil(cfg.bytePerBeat))

  // ======== Write path state machine ========
  // Simplified: collect AW+W, then send beats to Write Merge Buffer
  val sWrIdle :: sWrCollect :: sWrSend :: sWrDrain :: Nil = Enum(4)
  val wrState    = RegInit(sWrIdle)
  val wrAddr     = Reg(UInt(cfg.addressWidth.W))
  val wrId       = Reg(UInt(cfg.idWidth.W))
  val wrLen      = Reg(UInt(8.W))
  val wrBuf      = Reg(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))
  val wrStrbBuf  = Reg(Vec(cfg.beatsPerLine, UInt(cfg.bytePerBeat.W)))
  val wrBeatRecv = RegInit(0.U(log2Ceil(cfg.beatsPerLine + 1).W))
  val wrSendIdx  = RegInit(0.U(log2Ceil(cfg.beatsPerLine + 1).W))
  val wrAwValid  = RegInit(false.B)
  val wrDrainCnt = RegInit(0.U(8.W))

  // A burst reaches the cache only when it is INCR, full data width, beat aligned
  // and contained in one cache line. Anything else is answered with SLVERR without
  // touching the cache: a read returns len+1 zero beats, a write takes all its W
  // beats. An illegal write's B waits for the B of every earlier write.
  def burstLegal(addr: UInt, len: UInt, size: UInt, burst: UInt): Bool = {
    val beatOff = addr(cfg.offsetBits - 1, log2Ceil(cfg.bytePerBeat))
    (burst === Axi4.burst.INCR) && (size === log2Ceil(cfg.bytePerBeat).U) &&
      (addr(log2Ceil(cfg.bytePerBeat) - 1, 0) === 0.U) &&
      ((beatOff +& len) < cfg.beatsPerLine.U)
  }
  val arLegal = burstLegal(io.cpu.ar.payload.addr, io.cpu.ar.payload.len.get,
    io.cpu.ar.payload.size.get, io.cpu.ar.payload.burst.get)
  val awLegal = burstLegal(io.cpu.aw.payload.addr, io.cpu.aw.payload.len.get,
    io.cpu.aw.payload.size.get, io.cpu.aw.payload.burst.get)

  // ======== B Response Tracking ========
  // One entry per write burst that left the merge buffer. The entry stays at the
  // head until the pipeline has answered all its beats and its B is taken.
  val bFifoDepth = cfg.bFifoDepth
  val bPtrBits    = log2Ceil(bFifoDepth)
  val bCntBits    = log2Ceil(cfg.beatsPerLine + 1)
  val bFifoId     = Reg(Vec(bFifoDepth, UInt(cfg.idWidth.W)))
  val bFifoCount  = Reg(Vec(bFifoDepth, UInt(bCntBits.W)))
  val bFifoBeats  = RegInit(VecInit(Seq.fill(bFifoDepth)(0.U(bCntBits.W))))
  val bFifoHead   = RegInit(0.U(bPtrBits.W))
  val bFifoTail   = RegInit(0.U(bPtrBits.W))
  val bFifoNum    = RegInit(0.U(log2Ceil(bFifoDepth + 1).W))
  val bFifoFull   = bFifoNum === bFifoDepth.U
  val bFifoEmpty  = bFifoNum === 0.U

  // Beats are answered in burst order, so a write response retires the oldest
  // entry still waiting for beats; a later entry then keeps its own count while
  // the B of an earlier one is blocked on b_ready.
  val bFifoBeatsByAge = Wire(Vec(bFifoDepth, UInt(bCntBits.W)))
  for (d <- 0 until bFifoDepth) {
    bFifoBeatsByAge(d) := bFifoBeats((bFifoHead + d.U)(bPtrBits - 1, 0))
  }
  val bTrackAny = bFifoBeatsByAge.map(_ =/= 0.U).reduce(_ || _)
  val bTrackIdx = bFifoHead + PriorityEncoder(bFifoBeatsByAge.map(_ =/= 0.U))

  // B of an illegal write. It goes out only after the B of every earlier write:
  // an earlier write is either still in the merge buffer or in the FIFO.
  val bErrValid = RegInit(false.B)
  val bErrId    = Reg(UInt(cfg.idWidth.W))
  val bErrFire  = bErrValid && wmBuf.io.empty && bFifoEmpty
  when(bErrFire) { bErrValid := false.B }

  // ======== AXI4 Slave Front-end: AR channel ========
  // No new transaction starts once a flush is pending: its walk must not wait on it
  io.cpu.ar.ready := (rdState === sRdIdle) && !pipeline.io.flushBusy
  when(io.cpu.ar.fire) {
    rdLegal := arLegal
    rdAddr  := io.cpu.ar.payload.addr
    rdId    := io.cpu.ar.payload.id.get
    rdLen   := io.cpu.ar.payload.len.get
    rdState := Mux(arLegal, sRdSnoop, sRdBurst)
  }

  // ======== AXI4 Slave Front-end: AW channel ========
  io.cpu.aw.ready := (wrState === sWrIdle) && !wrAwValid && !pipeline.io.flushBusy
  pipeline.io.wrPortIdle := (wrState === sWrIdle) && !wrAwValid
  // Beats the slave took before the address turned out illegal still count toward
  // the illegal burst, whose remaining beats sWrDrain only discards.
  val wrIllegalAw = io.cpu.aw.fire && !awLegal
  when(io.cpu.aw.fire) {
    wrAddr := io.cpu.aw.payload.addr
    wrId   := io.cpu.aw.payload.id.get
    wrLen  := io.cpu.aw.payload.len.get
    when(awLegal) {
      wrAwValid := true.B
    }.otherwise {
      val taken = wrBeatRecv + io.cpu.w.fire.asUInt
      wrBeatRecv := 0.U
      when(taken === io.cpu.aw.payload.len.get +& 1.U) {
        wrState   := sWrIdle
        bErrValid := true.B
        bErrId    := io.cpu.aw.payload.id.get
      }.otherwise {
        wrState    := sWrDrain
        wrDrainCnt := taken
      }
    }
  }

  // ======== AXI4 Slave Front-end: W channel ========
  io.cpu.w.ready := ((wrState === sWrIdle || wrState === sWrCollect) &&
                     (wrBeatRecv < cfg.beatsPerLine.U)) || (wrState === sWrDrain)
  when(io.cpu.w.fire && !wrIllegalAw && wrState =/= sWrDrain) {
    wrBuf(wrBeatRecv)     := io.cpu.w.payload.data
    wrStrbBuf(wrBeatRecv) := io.cpu.w.payload.strb.getOrElse(Fill(cfg.bytePerBeat, 1.U(1.W)))
    wrBeatRecv := wrBeatRecv + 1.U
  }

  // Write state transitions
  switch(wrState) {
    is(sWrIdle) {
      when(wrAwValid && wrBeatRecv === (wrLen +& 1.U)) {
        wrState   := sWrSend
        wrSendIdx := 0.U
      }.elsewhen(wrAwValid || wrBeatRecv > 0.U) {
        wrState := sWrCollect
      }
    }
    is(sWrCollect) {
      when(wrAwValid && wrBeatRecv === (wrLen +& 1.U)) {
        wrState   := sWrSend
        wrSendIdx := 0.U
      }
    }
    is(sWrSend) {
      // Send beats to Write Merge Buffer (handled below)
    }
    is(sWrDrain) {
      when(io.cpu.w.fire) {
        when(wrDrainCnt === wrLen) {
          wrState    := sWrIdle
          bErrValid  := true.B
          bErrId     := wrId
        }.otherwise {
          wrDrainCnt := wrDrainCnt + 1.U
        }
      }
    }
  }

  // ======== Write path → Write Merge Buffer ========
  val wrWmBufReq = Wire(new WmBufWriteReq(cfg))
  wrWmBufReq.address := wrAddr
  wrWmBufReq.beatIdx := (wrAddr >> log2Ceil(cfg.bytePerBeat))(beatIdxBits - 1, 0) +
                        wrSendIdx(beatIdxBits - 1, 0)
  wrWmBufReq.data    := wrBuf(wrSendIdx)
  wrWmBufReq.strb    := wrStrbBuf(wrSendIdx)
  wrWmBufReq.id      := wrId
  wrWmBufReq.last    := (wrSendIdx === wrLen)

  wmBuf.io.writeIn.valid   := (wrState === sWrSend) && (wrSendIdx <= wrLen)
  wmBuf.io.writeIn.payload := wrWmBufReq

  // Advance write send index when wmBuf accepts
  when(wmBuf.io.writeIn.fire) {
    wrSendIdx := wrSendIdx + 1.U
    when(wrSendIdx === wrLen) {
      // All beats sent to merge buffer, write FSM done
      wrState   := sWrIdle
      wrAwValid := false.B
      wrBeatRecv := 0.U
    }
  }

  // ======== Read path: Snoop check for RAW hazard ========
  wmBuf.io.snoop.address := rdAddr
  val snoopFlushReq = RegInit(false.B)
  wmBuf.io.snoop.flushReq := snoopFlushReq

  switch(rdState) {
    is(sRdSnoop) {
      when(wmBuf.io.snoop.hit) {
        // RAW hazard: flush matching entry before reading
        snoopFlushReq := true.B
        rdState := sRdFlushWait
      }.otherwise {
        // No conflict, proceed to issue read
        rdReqValid := true.B
        rdState := sRdPending
      }
    }
    is(sRdFlushWait) {
      when(wmBuf.io.snoop.flushDone) {
        snoopFlushReq := false.B
        rdReqValid := true.B
        rdState := sRdPending
      }
    }
    is(sRdPending) {
      // Waiting for pipeline to accept read and then respond
    }
    is(sRdBurst) {
      // Sending R burst (handled below)
    }
  }

  // ======== Pipeline arbitration (wmBuf write flush has priority) ========
  val rdPipeReq = Wire(new LlcReq(cfg))
  rdPipeReq.isWrite   := false.B
  rdPipeReq.address   := rdAddr
  rdPipeReq.id        := rdId
  rdPipeReq.writeData := 0.U
  rdPipeReq.writeStrb := 0.U
  rdPipeReq.beatIdx   := rdBeatInLine
  rdPipeReq.singleBeat := rdLen === 0.U

  val rdWantsPipe = rdReqValid && (rdState === sRdPending)
  val wrWantsPipe = wmBuf.io.pipeOut.valid

  // Write (from wmBuf flush) has priority over reads
  pipeline.io.reqIn.valid   := false.B
  pipeline.io.reqIn.payload := DontCare
  wmBuf.io.pipeOut.ready    := false.B

  when(wrWantsPipe) {
    pipeline.io.reqIn.valid   := true.B
    pipeline.io.reqIn.payload := wmBuf.io.pipeOut.payload
    wmBuf.io.pipeOut.ready    := pipeline.io.reqIn.ready
  }.elsewhen(rdWantsPipe) {
    pipeline.io.reqIn.valid   := true.B
    pipeline.io.reqIn.payload := rdPipeReq
  }

  // Handle pipeline acceptance of read
  when(pipeline.io.reqIn.fire && rdWantsPipe && !wrWantsPipe) {
    rdReqValid := false.B
  }

  // ======== Pipeline response routing ========
  pipeline.io.respOut.ready := true.B

  when(pipeline.io.respOut.fire) {
    when(!pipeline.io.respOut.payload.isWrite) {
      // Read response: capture data for R burst
      rdData    := pipeline.io.respOut.payload.data
      rdBeatCnt := 0.U
      rdState   := sRdBurst
    }.otherwise {
      // Write response: one fewer beat the oldest waiting B entry counts on
      when(bTrackAny) {
        bFifoBeats(bTrackIdx) := bFifoBeats(bTrackIdx) - 1.U
      }
    }
  }

  // ======== B Response FIFO: enqueue from wmBuf ========
  // The merge buffer holds the entry until the info is taken, so the FIFO
  // cannot overflow
  wmBuf.io.bRespOut.ready := !bFifoFull
  val bPush = wmBuf.io.bRespOut.fire
  val bPop = io.cpu.b.fire && !bErrFire && bFifoCount(bFifoHead) === 1.U
  when(bPush) {
    bFifoId(bFifoTail)    := wmBuf.io.bRespOut.payload.id
    bFifoCount(bFifoTail) := wmBuf.io.bRespOut.payload.count
    bFifoBeats(bFifoTail) := wmBuf.io.bRespOut.payload.beats
    bFifoTail := bFifoTail + 1.U
  }
  when(bPush =/= bPop) {
    bFifoNum := Mux(bPush, bFifoNum + 1.U, bFifoNum - 1.U)
  }

  // ======== AXI4 Slave Response: R channel ========
  // An illegal read returns len+1 zero beats with SLVERR
  io.cpu.r.valid := (rdState === sRdBurst)
  io.cpu.r.payload.data := Mux(rdLegal, rdData((rdBeatInLine + rdBeatCnt)(beatIdxBits - 1, 0)), 0.U(cfg.dataWidth.W))
  io.cpu.r.payload.id.foreach(_ := rdId)
  io.cpu.r.payload.resp.foreach(_ := Mux(rdLegal, Axi4.resp.OKAY, Axi4.resp.SLVERR))
  io.cpu.r.payload.last.foreach(_ := (rdBeatCnt === rdLen))
  io.cpu.r.payload.user.foreach(_ := 0.U)

  when(io.cpu.r.fire) {
    rdBeatCnt := rdBeatCnt + 1.U
    when(rdBeatCnt === rdLen) {
      rdState   := sRdIdle
      rdBeatCnt := 0.U
    }
  }

  // ======== AXI4 Slave Response: B channel ========
  val bReady = bErrFire || (!bFifoEmpty && bFifoBeats(bFifoHead) === 0.U && bFifoCount(bFifoHead) =/= 0.U)
  io.cpu.b.valid := bReady
  io.cpu.b.payload.id.foreach(_ := Mux(bErrFire, bErrId, bFifoId(bFifoHead)))
  io.cpu.b.payload.resp.foreach(_ := Mux(bErrFire, Axi4.resp.SLVERR, Axi4.resp.OKAY))
  io.cpu.b.payload.user.foreach(_ := 0.U)

  when(io.cpu.b.fire && !bErrFire) {
    bFifoCount(bFifoHead) := bFifoCount(bFifoHead) - 1.U
    when(bPop) {
      bFifoHead := bFifoHead + 1.U
      // A freed slot must read as having no beats waiting
      bFifoBeats(bFifoHead) := 0.U
    }
  }

  // ======== AXI4 Master Back-end: Writeback (AW + W + B) ========
  val sMemWrIdle :: sMemWrAW :: sMemWrData :: sMemWrResp :: Nil = Enum(4)
  val memWrState   = RegInit(sMemWrIdle)

  pipeline.io.wbIdle := (memWrState === sMemWrIdle)
  val memWrAddr    = Reg(UInt(cfg.addressWidth.W))
  val memWrData    = Reg(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))
  val memWrOwner   = Reg(UInt(cfg.mshrIdxBits.W))
  val memWrBeatCnt = RegInit(0.U(log2Ceil(cfg.beatsPerLine + 1).W))

  wbBuffer.io.deqReady := false.B

  switch(memWrState) {
    is(sMemWrIdle) {
      when(wbBuffer.io.deq.valid) {
        memWrAddr     := wbBuffer.io.deq.bits.address
        memWrData     := wbBuffer.io.deq.bits.data
        memWrOwner    := wbBuffer.io.deq.bits.owner
        wbBuffer.io.deqReady := true.B
        memWrState    := sMemWrAW
        memWrBeatCnt  := 0.U
      }
    }
    is(sMemWrAW) {
      when(io.mem.aw.fire) {
        memWrState := sMemWrData
      }
    }
    is(sMemWrData) {
      when(io.mem.w.fire) {
        memWrBeatCnt := memWrBeatCnt + 1.U
        when(memWrBeatCnt === (cfg.beatsPerLine - 1).U) {
          memWrState := sMemWrResp
        }
      }
    }
    is(sMemWrResp) {
      when(io.mem.b.fire) {
        memWrState := sMemWrIdle
      }
    }
  }

  // Signal wbDone to MSHR/pipeline when writeback B response received
  pipeline.io.wbDone.valid := (memWrState === sMemWrResp) && io.mem.b.fire
  pipeline.io.wbDone.bits  := memWrOwner

  // Drive mem AW
  io.mem.aw.valid := (memWrState === sMemWrAW)
  io.mem.aw.payload.addr := memWrAddr
  io.mem.aw.payload.id.foreach(_ := 0.U)
  io.mem.aw.payload.len.foreach(_ := (cfg.beatsPerLine - 1).U)
  io.mem.aw.payload.size.foreach(_ := log2Ceil(cfg.bytePerBeat).U)
  io.mem.aw.payload.burst.foreach(_ := Axi4.burst.INCR)
  io.mem.aw.payload.lock.foreach(_ := 0.U)
  io.mem.aw.payload.cache.foreach(_ := 0.U)
  io.mem.aw.payload.region.foreach(_ := 0.U)
  io.mem.aw.payload.qos.foreach(_ := 0.U)
  io.mem.aw.payload.prot.foreach(_ := 0.U)
  io.mem.aw.payload.user.foreach(_ := 0.U)
  io.mem.aw.payload.allStrb.foreach(_ := false.B)

  // Drive mem W
  io.mem.w.valid := (memWrState === sMemWrData)
  io.mem.w.payload.data := memWrData(memWrBeatCnt)
  io.mem.w.payload.strb.foreach(_ := Fill(cfg.bytePerBeat, 1.U(1.W)))
  io.mem.w.payload.last.foreach(_ := (memWrBeatCnt === (cfg.beatsPerLine - 1).U))
  io.mem.w.payload.user.foreach(_ := 0.U)
  io.mem.w.payload.id.foreach(_ := 0.U)

  // Accept mem B
  io.mem.b.ready := (memWrState === sMemWrResp)

  // ======== AXI4 Master Back-end: Refill (AR + R) ========
  val sMemRdIdle :: sMemRdAR :: sMemRdData :: Nil = Enum(3)
  val memRdState   = RegInit(sMemRdIdle)
  val memRdAddr    = Reg(UInt(cfg.addressWidth.W))
  val memRdId      = Reg(UInt(cfg.idWidth.W))
  // A dirty line whose writeback has not been acknowledged downstream is not in
  // memory yet: the entry still in the write-back buffer and the one the write
  // state machine holds (through its B) both count, so the refill of that line waits.
  val lineMask = (~((BigInt(1) << cfg.offsetBits) - 1) &
                   ((BigInt(1) << cfg.addressWidth) - 1)).U(cfg.addressWidth.W)
  wbBuffer.io.snoop.query := memRdAddr
  val wbLinePending = ((memWrState =/= sMemWrIdle) && ((memWrAddr & lineMask) === (memRdAddr & lineMask))) ||
                      wbBuffer.io.snoop.hit

  // Combinational so the MSHR enters its receive state on the same edge the AR fires;
  // a registered flag makes it one cycle late and the first downstream beat is dropped.
  val refillReady  = (memRdState === sMemRdAR) && io.mem.ar.ready && !wbLinePending

  pipeline.io.refillReady := refillReady
  // The MSHR keeps the request it first presented on the port until the AR is
  // taken; the address sent is the one latched when the request appeared
  assert(!(memRdState === sMemRdAR && io.mem.ar.fire) || (io.mem.ar.payload.addr === pipeline.io.refillReq.bits.address),
    "refill AR address does not match the MSHR request")

  switch(memRdState) {
    is(sMemRdIdle) {
      when(pipeline.io.refillReq.valid) {
        memRdAddr  := pipeline.io.refillReq.bits.address
        memRdId    := pipeline.io.refillReq.bits.id
        memRdState := sMemRdAR
      }
    }
    is(sMemRdAR) {
      when(io.mem.ar.fire) {
        memRdState := sMemRdData
      }
    }
    is(sMemRdData) {
      when(io.mem.r.fire && io.mem.r.payload.last.getOrElse(true.B)) {
        memRdState := sMemRdIdle
      }
    }
  }

  // Drive mem AR
  io.mem.ar.valid := (memRdState === sMemRdAR) && !wbLinePending
  io.mem.ar.payload.addr := memRdAddr
  io.mem.ar.payload.id.foreach(_ := memRdId)
  io.mem.ar.payload.len.foreach(_ := (cfg.beatsPerLine - 1).U)
  io.mem.ar.payload.size.foreach(_ := log2Ceil(cfg.bytePerBeat).U)
  io.mem.ar.payload.burst.foreach(_ := Axi4.burst.INCR)
  io.mem.ar.payload.lock.foreach(_ := 0.U)
  io.mem.ar.payload.cache.foreach(_ := 0.U)
  io.mem.ar.payload.region.foreach(_ := 0.U)
  io.mem.ar.payload.qos.foreach(_ := 0.U)
  io.mem.ar.payload.prot.foreach(_ := 0.U)
  io.mem.ar.payload.user.foreach(_ := 0.U)

  // Receive mem R and forward to pipeline refillData
  io.mem.r.ready := (memRdState === sMemRdData)
  pipeline.io.refillData.valid     := io.mem.r.fire
  pipeline.io.refillData.bits.data := io.mem.r.payload.data
  pipeline.io.refillData.bits.last := io.mem.r.payload.last.getOrElse(true.B)

  // ======== Performance Counters Wiring ========
  perfCounters.io.events.readHit        := pipeline.io.perfEvents.readHit
  perfCounters.io.events.readMiss       := pipeline.io.perfEvents.readMiss
  perfCounters.io.events.writeHit       := pipeline.io.perfEvents.writeHit
  perfCounters.io.events.writeMiss      := pipeline.io.perfEvents.writeMiss
  perfCounters.io.events.dirtyEvict     := pipeline.io.perfEvents.dirtyEvict
  perfCounters.io.events.cleanEvict     := pipeline.io.perfEvents.cleanEvict
  perfCounters.io.events.mshrFullStall  := pipeline.io.perfEvents.mshrFullStall
  perfCounters.io.events.wbBufFullStall := wbBuffer.io.full
  perfCounters.io.events.writeMerge     := wmBuf.io.mergeEvent
  perfCounters.io.events.refillComplete := pipeline.io.perfEvents.refillComplete

  // ======== AxiLite4 Control / Status Register Interface ========
  val busif = AxiLite4BusInterface(io.ctrl, SizeMapping(0x0, 256))

  // 0x00: LLC_CTRL (R/W)  bit0=enable(reserved) bit1=flush bit2=invalidate
  val REG_CTRL = busif.newReg("LLC control register")
  val ctrlEnable     = REG_CTRL.field(Bool(), AccessType.RW, 0, "enable (reserved)")
  val ctrlFlush      = REG_CTRL.field(Bool(), AccessType.RW, 0, "flush (clean-flush all dirty lines)")
  val ctrlInvalidate = REG_CTRL.field(Bool(), AccessType.RW, 0, "invalidate (writeback dirty then clear valid)")

  // Flush trigger: rising edge of (flush || invalidate) starts a walk, but only
  // when the pipeline flush FSM is idle. Software writes 1 to the bit to start
  // and polls STATUS for completion; to re-trigger, clear the bit then set it
  // again. Invalidate takes precedence when both bits are set.
  val ctrlFlushReq     = ctrlFlush || ctrlInvalidate
  val ctrlFlushReqPrev = RegNext(ctrlFlushReq, false.B)
  val flushStartPulse  = ctrlFlushReq && !ctrlFlushReqPrev && !pipeline.io.flushBusy
  pipeline.io.flushStart        := flushStartPulse
  pipeline.io.flushIsInvalidate := ctrlInvalidate

  // Sticky "flush done" flag: set when the FSM reports completion, cleared when
  // a new flush is started.
  val flushDoneSticky = RegInit(false.B)
  when(pipeline.io.flushDone) { flushDoneSticky := true.B }
  when(flushStartPulse)       { flushDoneSticky := false.B }

  // 0x04: STATUS (RO)  bit0=wbBufEmpty bit1=wbBufFull bit2=mshrFull bit3=wmBufFlushing
  //                    bit4=flushBusy bit5=flushDone(sticky)
  val REG_STATUS = busif.newReg("LLC status register")
  val statusWbEmpty    = REG_STATUS.field(Bool(), AccessType.RO, "wb buffer empty")
  val statusWbFull     = REG_STATUS.field(Bool(), AccessType.RO, "wb buffer full")
  val statusMshrFull   = REG_STATUS.field(Bool(), AccessType.RO, "MSHR full")
  val statusWmFlushing = REG_STATUS.field(Bool(), AccessType.RO, "write-merge buffer flushing")
  val statusFlushBusy  = REG_STATUS.field(Bool(), AccessType.RO, "flush/invalidate in progress")
  val statusFlushDone  = REG_STATUS.field(Bool(), AccessType.RO, "flush/invalidate complete (sticky)")
  statusWbEmpty    := wbBuffer.io.empty
  statusWbFull     := wbBuffer.io.full
  statusMshrFull   := pipeline.io.perfEvents.mshrFullStall
  statusWmFlushing := wmBuf.io.flushing
  statusFlushBusy  := pipeline.io.flushBusy
  statusFlushDone  := flushDoneSticky

  // 0x08: WAY_PARTITION_CFG (R/W) 16 bits
  val REG_WAYPART = busif.newReg("Way partition config")
  val wayPartCfg = REG_WAYPART.field(UInt(16.W), AccessType.RW, 0, "way partition map")

  // 0x0C: reserved
  val REG_RSVD = busif.newReg("reserved")
  REG_RSVD.field(UInt(32.W), AccessType.NA)

  // 0x10 - 0x8F: PERF_CNT (RO) 16 counters x 2 registers each (lo/hi 32-bit)
  for (i <- 0 until 16) {
    val regLo = busif.newReg(s"perf counter $i low")
    val cntLo = regLo.field(UInt(32.W), AccessType.RO, s"counter $i [31:0]")
    cntLo := perfCounters.io.counters(i)(31, 0)

    val regHi = busif.newReg(s"perf counter $i high")
    val cntHi = regHi.field(UInt(32.W), AccessType.RO, s"counter $i [63:32]")
    cntHi := perfCounters.io.counters(i)(63, 32)
  }

  // 0x90: PERF_CNT_CLR (W1P - write-1-pulse to clear all counters)
  val REG_CLR = busif.newReg("perf counter clear")
  val cntClrPulse = REG_CLR.field(Bool(), AccessType.W1P, 0, "write 1 to clear all perf counters")
  perfCounters.io.clear := cntClrPulse

  busif.finish()

  // Suppress unused ctrl register outputs to avoid Chisel lint warnings.
  // ctrlFlush/ctrlInvalidate are now genuinely consumed by the flush FSM, so
  // only ctrlEnable (still reserved) and wayPartCfg need dontTouch.
  dontTouch(ctrlEnable)
  dontTouch(wayPartCfg)
}
