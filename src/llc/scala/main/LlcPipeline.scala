import chisel3._
import chisel3.util._

// Unified request bundle for the LLC pipeline
class LlcReq(cfg: LlcConfig) extends Bundle {
  val isWrite   = Bool()
  val address   = UInt(cfg.addressWidth.W)
  val id        = UInt(cfg.idWidth.W)
  val writeData = UInt(cfg.dataWidth.W)
  val writeStrb = UInt(cfg.bytePerBeat.W)
  val beatIdx   = UInt(log2Ceil(cfg.beatsPerLine).W)
}

// Response bundle from the LLC pipeline
class LlcResp(cfg: LlcConfig) extends Bundle {
  val isWrite = Bool()
  val id      = UInt(cfg.idWidth.W)
  val data    = Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))
}

/** LLC 3-stage pipeline.
  *
  * Instantiates TagArray, DataArray, PLRU, WayPartition, and MSHR internally.
  * Exposes only the unified request/response interface and MSHR external ports
  * (writeback, refill) for the top module to wire to the AXI4 master backend.
  */
class LlcPipeline(cfg: LlcConfig) extends Module {
  val io = IO(new Bundle {
    // Unified request from top module (arbitrated read/write)
    val reqIn = slave(Stream(new LlcReq(cfg)))
    // Response to top module
    val respOut = master(Stream(new LlcResp(cfg)))

    // MSHR writeback interface (top wires to WbBuffer)
    val wbReq  = Output(Valid(new MshrWbReq(cfg)))
    val wbDone = Input(Bool())

    // MSHR refill interface (top wires to AXI master AR/R)
    val refillReq   = Output(Valid(new MshrRefillReq(cfg)))
    val refillReady = Input(Bool())
    val refillData  = Flipped(Valid(new MshrRefillData(cfg)))

    // Performance event pulses (to LlcPerfCounters). Each is asserted for one
    // cycle when the corresponding event occurs in stage S2 / refill path.
    val perfEvents = Output(new Bundle {
      val readHit        = Bool()
      val readMiss       = Bool()
      val writeHit       = Bool()
      val writeMiss      = Bool()
      val dirtyEvict     = Bool()
      val cleanEvict     = Bool()
      val mshrFullStall  = Bool()
      val refillComplete = Bool()
    })

    // ======== Flush / Invalidate control ========
    // Rising-edge start request from the top-level ctrl register.
    val flushStart        = Input(Bool())
    // true => invalidate semantics (clear valid); false => clean-flush (keep valid, clear dirty).
    val flushIsInvalidate = Input(Bool())
    // High while the flush/invalidate walk FSM is active.
    val flushBusy         = Output(Bool())
    // One-cycle pulse when a flush completes and the WbBuffer has drained.
    val flushDone         = Output(Bool())
    // WbBuffer status inputs used by the flush FSM to spin/drain.
    val wbBufEmpty        = Input(Bool())
    val wbBufEnqReady     = Input(Bool())
  })

  // ======== Sub-module instantiation ========
  val tagArray     = Module(new LlcTagArray(cfg))
  val dataArray    = Module(new LlcDataArray(cfg))
  val plru         = Module(new LlcPlru(cfg))
  val wayPartition = Module(new LlcWayPartition(cfg))
  val mshr         = Module(new LlcMshr(cfg))

  // ======== Connect MSHR external ports ========
  io.wbReq := mshr.io.wbReq
  mshr.io.wbDone := io.wbDone
  io.refillReq := mshr.io.refillReq
  mshr.io.refillReady := io.refillReady
  mshr.io.refillData := io.refillData

  // ======== Refill completion ========
  val refillComplete    = mshr.io.complete.valid
  val refillCompleteReq = mshr.io.complete.bits

  // ======== Early response from MSHR (Critical-Word-First) ========
  val earlyRespValid = mshr.io.earlyResp.valid
  val earlyRespBits  = mshr.io.earlyResp.bits

  // ======== Response output register ========
  val respValidReg = RegInit(false.B)
  val respDataReg  = Reg(new LlcResp(cfg))
  io.respOut.valid   := respValidReg
  io.respOut.payload := respDataReg
  when(io.respOut.fire) { respValidReg := false.B }
  val canPushResp = !respValidReg || io.respOut.fire

  // ======== Early response generation (Critical-Word-First) ========
  // When MSHR earlyResp fires and respOut slot is available, send single-beat response
  // Priority: early resp can preempt when canPushResp (no S2 hit conflict during refill)
  when(earlyRespValid && canPushResp) {
    respValidReg        := true.B
    respDataReg.isWrite := false.B  // early resp is always for read misses
    respDataReg.id      := earlyRespBits.id
    // Fill data vector: only the target beat has meaningful data, others are zero-padded
    for (i <- 0 until cfg.beatsPerLine) {
      respDataReg.data(i) := Mux(i.U === earlyRespBits.beatIdx,
        earlyRespBits.data, 0.U)
    }
  }

  // ======== Pipeline valid registers ========
  val s1Valid = RegInit(false.B)
  val s2Valid = RegInit(false.B)

  // ======== Replay buffer (for refill-during-S1 hazard) ========
  val replayValid = RegInit(false.B)
  val replayReq   = Reg(new LlcReq(cfg))

  // ======== Stall / control signals (forward declarations) ========
  val s2Stall       = Wire(Bool())
  val refillBusy    = refillComplete
  val pipelineStall = refillBusy || s2Stall || !canPushResp

  // Flush FSM active flag (assigned in the flush section below). While active,
  // the normal pipeline is quiesced and must not accept new requests.
  val flushActive = Wire(Bool())

  // ======== Default port connections ========
  // Tag array defaults
  tagArray.io.read.enable  := false.B
  tagArray.io.read.set     := 0.U
  tagArray.io.write.enable := false.B
  tagArray.io.write.set    := 0.U
  tagArray.io.write.way    := 0.U
  tagArray.io.write.tag    := 0.U
  tagArray.io.write.valid  := false.B
  tagArray.io.write.dirty  := false.B

  // Data array defaults
  dataArray.io.read.enable     := false.B
  dataArray.io.read.set        := 0.U
  dataArray.io.read.way        := 0.U
  dataArray.io.write.enable    := false.B
  dataArray.io.write.set       := 0.U
  dataArray.io.write.way       := 0.U
  dataArray.io.write.beatIdx   := 0.U
  dataArray.io.write.data      := 0.U
  dataArray.io.write.strb      := 0.U
  dataArray.io.writeLine.enable := false.B
  dataArray.io.writeLine.set   := 0.U
  dataArray.io.writeLine.way   := 0.U
  dataArray.io.writeLine.data  := VecInit(Seq.fill(cfg.beatsPerLine)(0.U(cfg.dataWidth.W)))

  // PLRU defaults
  plru.io.query.set      := 0.U
  plru.io.update.enable  := false.B
  plru.io.update.set     := 0.U
  plru.io.update.way     := 0.U

  // Way partition defaults
  val wayPartMap = VecInit(Seq.tabulate(cfg.ways)(w =>
    (w / cfg.waysPerPartition).U(log2Ceil(cfg.wayPartitions).max(1).W)
  ))
  wayPartition.io.partitionId     := 0.U
  wayPartition.io.plruVictim      := 0.U
  wayPartition.io.wayValids       := VecInit(Seq.fill(cfg.ways)(false.B))
  wayPartition.io.wayPartitionMap := wayPartMap

  // MSHR defaults
  mshr.io.allocReq.valid             := false.B
  mshr.io.allocReq.bits              := 0.U.asTypeOf(new MshrAllocReq(cfg))
  mshr.io.addrMatch.query            := 0.U

  // ================================================================
  // Stage S0: Request Accept
  // ================================================================
  val s0CanAccept = !pipelineStall && !s1Valid && !replayValid
  io.reqIn.ready := s0CanAccept

  // S0 source: replay has priority over new requests
  val s0Fire = (io.reqIn.fire || replayValid) && !pipelineStall && !s1Valid && !flushActive
  val s0Req  = Mux(replayValid, replayReq, io.reqIn.payload)
  val s0Set  = s0Req.address(cfg.offsetBits + cfg.setBits - 1, cfg.offsetBits)
  val s0Tag  = s0Req.address(cfg.addressWidth - 1, cfg.offsetBits + cfg.setBits)

  // When replay fires, clear replay buffer
  when(replayValid && !pipelineStall && !s1Valid) {
    replayValid := false.B
  }
  // Accept new when no replay and not stalled, and no flush in progress
  io.reqIn.ready := !pipelineStall && !s1Valid && !replayValid && !flushActive

  // Issue tag array read in S0
  when(s0Fire) {
    tagArray.io.read.enable := true.B
    tagArray.io.read.set    := s0Set
  }

  // S0 → S1 pipeline register
  val s1Req = Reg(new LlcReq(cfg))
  val s1Set = Reg(UInt(cfg.setBits.W))
  val s1Tag = Reg(UInt(cfg.tagBits.W))

  when(s0Fire) {
    s1Valid := true.B
    s1Req   := s0Req
    s1Set   := s0Set
    s1Tag   := s0Tag
  }

  // ================================================================
  // Stage S1: Tag Lookup + Data Read Issue
  // ================================================================
  val s1Tags   = tagArray.io.readResp.tags
  val s1Valids = tagArray.io.readResp.valids
  val s1Dirtys = tagArray.io.readResp.dirtys

  // Tag compare
  val hitVec  = VecInit((0 until cfg.ways).map(w => s1Valids(w) && (s1Tags(w) === s1Tag)))
  val hit     = hitVec.asUInt.orR
  val hitWay  = OHToUInt(hitVec.asUInt)

  // PLRU query
  plru.io.query.set := s1Set
  val plruVictim = plru.io.queryResp.victimWay

  // Way partition
  wayPartition.io.partitionId := (if (cfg.wayPartitions > 1)
    s1Req.id(log2Ceil(cfg.wayPartitions) - 1, 0) else 0.U)
  wayPartition.io.plruVictim      := plruVictim
  wayPartition.io.wayValids       := s1Valids
  wayPartition.io.wayPartitionMap := wayPartMap
  val victimWay = wayPartition.io.victimWay

  // Issue data array read: hit → read hitWay; miss → read victimWay (for eviction data)
  val dataReadWay = Mux(hit, hitWay, victimWay)
  when(s1Valid && !pipelineStall) {
    dataArray.io.read.enable := true.B
    dataArray.io.read.set    := s1Set
    dataArray.io.read.way    := dataReadWay
  }

  // S1 → S2 pipeline register
  val s2Req        = Reg(new LlcReq(cfg))
  val s2Set        = Reg(UInt(cfg.setBits.W))
  val s2Tag        = Reg(UInt(cfg.tagBits.W))
  val s2Hit        = RegInit(false.B)
  val s2HitWay     = Reg(UInt(log2Ceil(cfg.ways).W))
  val s2VictimWay  = Reg(UInt(log2Ceil(cfg.ways).W))
  val s2VictimValid = Reg(Bool())
  val s2VictimDirty = Reg(Bool())
  val s2VictimTag  = Reg(UInt(cfg.tagBits.W))

  when(s1Valid && !pipelineStall) {
    s2Valid       := true.B
    s2Req         := s1Req
    s2Set         := s1Set
    s2Tag         := s1Tag
    s2Hit         := hit
    s2HitWay      := hitWay
    s2VictimWay   := victimWay
    s2VictimValid := s1Valids(victimWay)
    s2VictimDirty := s1Dirtys(victimWay)
    s2VictimTag   := s1Tags(victimWay)
    s1Valid       := false.B // S1 consumed
  }

  // Flush S1 on refill collision (same-set hazard prevention)
  when(refillComplete && s1Valid) {
    replayValid := true.B
    replayReq   := s1Req
    s1Valid     := false.B
  }

  // ================================================================
  // Stage S2: Hit/Miss Resolution
  // ================================================================
  val s2DataResp = dataArray.io.readResp.data

  // MSHR address match check (hazard: same line already pending)
  val lineMask = (~((1 << cfg.offsetBits) - 1).U(cfg.addressWidth.W)).asUInt
  mshr.io.addrMatch.query := s2Req.address & lineMask
  val mshrAddrHit = mshr.io.addrMatch.hit

  // Stall conditions
  val s2Miss         = s2Valid && !s2Hit
  val s2MshrConflict = s2Miss && mshrAddrHit
  val s2MshrFull     = s2Miss && !mshr.io.allocReady && !mshrAddrHit
  s2Stall := s2Valid && !s2Hit && (s2MshrFull || s2MshrConflict)

  // ---- Hit path ----
  when(s2Valid && s2Hit && !refillBusy && canPushResp) {
    when(s2Req.isWrite) {
      // Write hit: update data array (single beat)
      dataArray.io.write.enable  := true.B
      dataArray.io.write.set     := s2Set
      dataArray.io.write.way     := s2HitWay
      dataArray.io.write.beatIdx := s2Req.beatIdx
      dataArray.io.write.data    := s2Req.writeData
      dataArray.io.write.strb    := s2Req.writeStrb

      // Mark dirty in tag array
      tagArray.io.write.enable := true.B
      tagArray.io.write.set    := s2Set
      tagArray.io.write.way    := s2HitWay
      tagArray.io.write.tag    := s2Tag
      tagArray.io.write.valid  := true.B
      tagArray.io.write.dirty  := true.B
    }

    // Generate response
    respValidReg     := true.B
    respDataReg.isWrite := s2Req.isWrite
    respDataReg.id      := s2Req.id
    respDataReg.data    := s2DataResp

    // Update PLRU
    plru.io.update.enable := true.B
    plru.io.update.set    := s2Set
    plru.io.update.way    := s2HitWay

    // Consume S2
    s2Valid := false.B
  }

  // ---- Miss path: allocate MSHR ----
  val allocFire = s2Valid && !s2Hit && !mshrAddrHit && mshr.io.allocReady && !refillBusy
  mshr.io.allocReq.valid              := allocFire
  mshr.io.allocReq.bits.address       := s2Req.address & lineMask
  mshr.io.allocReq.bits.set           := s2Set
  mshr.io.allocReq.bits.way           := s2VictimWay
  mshr.io.allocReq.bits.dirtyEvict    := s2VictimValid && s2VictimDirty
  mshr.io.allocReq.bits.evictTag      := s2VictimTag
  mshr.io.allocReq.bits.evictData     := s2DataResp
  mshr.io.allocReq.bits.reqId         := s2Req.id
  mshr.io.allocReq.bits.reqType       := s2Req.isWrite
  mshr.io.allocReq.bits.writeData     := s2Req.writeData
  mshr.io.allocReq.bits.writeStrb     := s2Req.writeStrb
  mshr.io.allocReq.bits.writeBeatIdx  := s2Req.beatIdx

  when(allocFire) {
    s2Valid := false.B // consumed
  }

  // ---- Refill completion path ----
  when(refillComplete) {
    // Write refilled line into data array
    dataArray.io.writeLine.enable := true.B
    dataArray.io.writeLine.set    := refillCompleteReq.set
    dataArray.io.writeLine.way    := refillCompleteReq.way
    dataArray.io.writeLine.data   := refillCompleteReq.data

    // Update tag array
    tagArray.io.write.enable := true.B
    tagArray.io.write.set    := refillCompleteReq.set
    tagArray.io.write.way    := refillCompleteReq.way
    tagArray.io.write.tag    := refillCompleteReq.tag
    tagArray.io.write.valid  := true.B
    tagArray.io.write.dirty  := refillCompleteReq.dirty

    // Update PLRU
    plru.io.update.enable := true.B
    plru.io.update.set    := refillCompleteReq.set
    plru.io.update.way    := refillCompleteReq.way

    // Generate response for the original requester ONLY if early response was NOT already sent
    // For read misses with earlyRespDone=true, response already delivered; skip to avoid duplicate
    when(!refillCompleteReq.earlyRespDone) {
      respValidReg        := true.B
      respDataReg.isWrite := refillCompleteReq.reqType
      respDataReg.id      := refillCompleteReq.reqId
      respDataReg.data    := refillCompleteReq.data
    }
  }

  // ================================================================
  // Flush / Invalidate walk FSM
  // ================================================================
  // Shared walk over ALL sets x ways. Difference between flush and invalidate
  // is only the final tag write:
  //   - clean-flush : dirty+valid lines are written back then marked clean
  //                   (dirty=0, valid=1); clean lines are skipped.
  //   - invalidate  : dirty+valid lines are written back then valid=0;
  //                   clean+valid lines are directly set valid=0.
  // Both clear the dirty bit. The generated writebacks reuse io.wbReq and thus
  // flow through the same WbBuffer -> AXI master path as MSHR evictions.
  val sFlushIdle :: sQuiesce :: sReadTag :: sWaitTag :: sClassify :: sReadData :: sWaitData :: sEnqWb :: sWriteTag :: sAdvance :: sDrain :: sDone :: Nil = Enum(12)

  val flushState = RegInit(sFlushIdle)
  val flushInv   = RegInit(false.B)
  val curSet     = RegInit(0.U(cfg.setBits.W))
  val curWay     = RegInit(0.U(log2Ceil(cfg.ways).W))

  // Latched tag read result for the current set (read once per set, iterate ways).
  val latchedTags   = Reg(Vec(cfg.ways, UInt(cfg.tagBits.W)))
  val latchedValids = Reg(Vec(cfg.ways, Bool()))
  val latchedDirtys = Reg(Vec(cfg.ways, Bool()))
  // Latched line data for the way currently being written back.
  val flushLineData = Reg(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))

  flushActive := flushState =/= sFlushIdle

  // Quiesce condition: normal pipeline drained and MSHR fully idle (no in-flight
  // refill/writeback), so the flush FSM has exclusive use of the arrays and wbReq.
  val flushQuiesced = !s1Valid && !s2Valid && !replayValid && mshr.io.allIdle

  // While the flush FSM owns wbReq, keep it deasserted unless we are enqueueing
  // (MSHR is idle during flush, so this simple override is safe).
  when(flushActive) {
    io.wbReq.valid        := false.B
    io.wbReq.bits.address := 0.U
    io.wbReq.bits.data    := VecInit(Seq.fill(cfg.beatsPerLine)(0.U(cfg.dataWidth.W)))
  }

  switch(flushState) {
    is(sFlushIdle) {
      when(io.flushStart) {
        flushInv   := io.flushIsInvalidate
        curSet     := 0.U
        curWay     := 0.U
        flushState := sQuiesce
      }
    }
    is(sQuiesce) {
      when(flushQuiesced) { flushState := sReadTag }
    }
    is(sReadTag) {
      tagArray.io.read.enable := true.B
      tagArray.io.read.set    := curSet
      flushState := sWaitTag
    }
    is(sWaitTag) {
      // Tag SyncReadMem result valid this cycle; latch all ways for the set.
      latchedTags   := tagArray.io.readResp.tags
      latchedValids := tagArray.io.readResp.valids
      latchedDirtys := tagArray.io.readResp.dirtys
      flushState := sClassify
    }
    is(sClassify) {
      val needWb = latchedValids(curWay) && latchedDirtys(curWay)
      when(needWb) {
        flushState := sReadData
      }.elsewhen(flushInv && latchedValids(curWay)) {
        // Clean+valid line under invalidate: clear valid directly, no writeback.
        flushState := sWriteTag
      }.otherwise {
        // Clean line under flush, or already-invalid line: nothing to do.
        flushState := sAdvance
      }
    }
    is(sReadData) {
      dataArray.io.read.enable := true.B
      dataArray.io.read.set    := curSet
      dataArray.io.read.way    := curWay
      flushState := sWaitData
    }
    is(sWaitData) {
      // Data readResp valid this cycle (way already selected via RegNext internally).
      flushLineData := dataArray.io.readResp.data
      flushState := sEnqWb
    }
    is(sEnqWb) {
      io.wbReq.valid        := true.B
      io.wbReq.bits.address := Cat(latchedTags(curWay), curSet, 0.U(cfg.offsetBits.W))
      io.wbReq.bits.data    := flushLineData
      when(io.wbBufEnqReady) {
        flushState := sWriteTag
      }
    }
    is(sWriteTag) {
      tagArray.io.write.enable := true.B
      tagArray.io.write.set    := curSet
      tagArray.io.write.way    := curWay
      tagArray.io.write.tag    := latchedTags(curWay)
      tagArray.io.write.valid  := !flushInv // flush keeps line resident; invalidate clears
      tagArray.io.write.dirty  := false.B   // both clear the dirty bit
      flushState := sAdvance
    }
    is(sAdvance) {
      when(curWay === (cfg.ways - 1).U) {
        curWay := 0.U
        when(curSet === (cfg.sets - 1).U) {
          flushState := sDrain
        }.otherwise {
          curSet     := curSet + 1.U
          flushState := sReadTag
        }
      }.otherwise {
        // Same set, next way: reuse latched tags (no re-read needed).
        curWay     := curWay + 1.U
        flushState := sClassify
      }
    }
    is(sDrain) {
      // Wait until every generated writeback has reached DRAM.
      when(io.wbBufEmpty) { flushState := sDone }
    }
    is(sDone) {
      flushState := sFlushIdle
    }
  }

  io.flushBusy := flushActive
  io.flushDone := (flushState === sDone)

  // ================================================================
  // Performance event generation
  // ================================================================
  // Hit path fires when a hit is resolved and a response slot is available.
  val hitFire = s2Valid && s2Hit && !refillBusy && canPushResp
  io.perfEvents.readHit        := hitFire && !s2Req.isWrite
  io.perfEvents.writeHit       := hitFire && s2Req.isWrite
  // Miss path fires when an MSHR entry is successfully allocated.
  io.perfEvents.readMiss       := allocFire && !s2Req.isWrite
  io.perfEvents.writeMiss      := allocFire && s2Req.isWrite
  // Eviction classification (only meaningful when a valid line is replaced).
  io.perfEvents.dirtyEvict     := allocFire && s2VictimValid && s2VictimDirty
  io.perfEvents.cleanEvict     := allocFire && s2VictimValid && !s2VictimDirty
  // Per-cycle stall because MSHR is full (miss cannot allocate).
  io.perfEvents.mshrFullStall  := s2MshrFull
  // Refill completion (one pulse per completed line refill).
  io.perfEvents.refillComplete := refillComplete
}
