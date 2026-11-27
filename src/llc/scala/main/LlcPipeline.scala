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
    val wbDone = Input(Valid(UInt(cfg.mshrIdxBits.W)))

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
    // One-cycle pulse when a flush completes and every write-back it produced
    // has been acknowledged downstream.
    val flushDone         = Output(Bool())
    // WbBuffer status inputs used by the flush FSM to spin/drain.
    val wbBufEmpty        = Input(Bool())
    val wbBufEnqReady     = Input(Bool())
    // The top-level write FSM holds nothing: no write-back is waiting for its B
    val wbIdle            = Input(Bool())
    // Write-merge buffer is empty, and the top-level write port holds no accepted
    // write burst (neither AW-only nor unsent W beats)
    val wmBufEmpty        = Input(Bool())
    val wrPortIdle        = Input(Bool())
  })

  // ======== Sub-module instantiation ========
  val tagArray     = Module(new LlcTagArray(cfg))
  val dataArray    = Module(new LlcDataArray(cfg))
  val plru         = Module(new LlcPlru(cfg))
  val wayPartition = Module(new LlcWayPartition(cfg))
  val mshr         = Module(new LlcMshr(cfg))

  // ======== Connect MSHR external ports ========
  io.wbReq := mshr.io.wbReq
  mshr.io.wbReady := io.wbBufEnqReady
  mshr.io.wbDone := io.wbDone
  io.refillReq := mshr.io.refillReq
  mshr.io.refillReady := io.refillReady
  mshr.io.refillData := io.refillData

  // ======== MSHR in-flight locations ========
  // A way an MSHR entry is refilling still carries the old line's tag; selecting
  // it as a victim would overwrite the incoming line.
  def mshrBusy(set: UInt, way: UInt): Bool =
    mshr.io.inflight.map(e => e.valid && e.bits.set === set && e.bits.way === way).reduce(_ || _)

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

  // An early response is taken only when the response slot is free.
  mshr.io.earlyRespReady := canPushResp

  // ======== Early response generation (Critical-Word-First) ========
  // Only on a handshake: an early response the slot cannot take is not sent here
  // and is answered by the refill completion response instead.
  when(earlyRespValid) {
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
  val replayValid  = RegInit(false.B)
  val replayReq    = Reg(new LlcReq(cfg))
  val replay2Valid = RegInit(false.B)
  val replay2Req   = Reg(new LlcReq(cfg))
  val replayPend = replayValid || replay2Valid
  val replayHead = Mux(replayValid, replayReq, replay2Req)

  // ======== Stall / control signals (forward declarations) ========
  val s2Stall       = Wire(Bool())
  val refillBusy    = refillComplete
  val pipelineStall = refillBusy || s2Stall || !canPushResp

  // Flush FSM active flag (assigned in the flush section below). While active,
  // the normal pipeline is quiesced and must not accept new requests.
  val flushActive = Wire(Bool())
  // The walk states proper: flushActive minus the quiesce state, which still lets
  // accepted requests drain through the pipeline
  val flushWalking = Wire(Bool())

  // S2 leaves this cycle (responds, allocates or is replayed); a held S2 keeps
  // its data array read alive
  val s2Done = Wire(Bool())

  // ======== Default port connections ========
  // Tag array defaults
  tagArray.io.write.enable := false.B
  tagArray.io.write.set    := 0.U
  tagArray.io.write.way    := 0.U
  tagArray.io.write.tag    := 0.U
  tagArray.io.write.valid  := false.B
  tagArray.io.write.dirty  := false.B

  // Data array defaults
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
  // S0 source: replay has priority over new requests
  val s0Fire = (io.reqIn.fire || replayPend) && !pipelineStall && !s1Valid && !flushWalking
  val s0Req  = Mux(replayPend, replayHead, io.reqIn.payload)
  val s0Set  = s0Req.address(cfg.offsetBits + cfg.setBits - 1, cfg.offsetBits)
  val s0Tag  = s0Req.address(cfg.addressWidth - 1, cfg.offsetBits + cfg.setBits)

  // When a replay fires, the second slot moves up
  when(replayPend && !pipelineStall && !s1Valid) {
    when(replayValid) {
      replayValid := replay2Valid
      replayReq   := replay2Req
    }.otherwise {
      replay2Valid := false.B
    }
  }
  // Accept new when no replay and not stalled, and the flush walk is not running
  io.reqIn.ready := !pipelineStall && !s1Valid && !replayPend && !flushWalking

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

  // The tag and PLRU arrays are sync-read SRAMs whose output is valid for one
  // cycle only. S0 issues both reads so S1 has the data; one read per lookup is
  // enough because a request enters S1 only in a cycle without stall and is
  // either replayed or gone by the end of the next cycle.
  tagArray.io.read.enable := s0Fire
  tagArray.io.read.set    := s0Set
  plru.io.query.enable    := s0Fire
  plru.io.query.set       := s0Set

  // ================================================================
  // Stage S1: Tag Lookup + Data Read Issue
  // ================================================================
  val s1Tags   = tagArray.io.readResp.tags
  val s1Valids = tagArray.io.readResp.valids
  val s1Dirtys = tagArray.io.readResp.dirtys

  // Ways of this set that an MSHR entry is refilling
  val s1BusyMask = mshr.io.inflight.map { e =>
    Mux(e.valid && e.bits.set === s1Set, UIntToOH(e.bits.way, cfg.ways), 0.U(cfg.ways.W))
  }.reduce(_ | _)

  // A busy way's tag is invalid; a hit on it comes from a tag read that preceded
  // the invalidation at allocation and must be treated as a miss.
  val hitVec  = VecInit((0 until cfg.ways).map(w =>
    s1Valids(w) && !s1BusyMask(w) && (s1Tags(w) === s1Tag)))
  val hit     = hitVec.asUInt.orR
  val hitWay  = OHToUInt(hitVec.asUInt)

  val plruVictim = plru.io.queryResp.victimWay

  // Way partition
  wayPartition.io.partitionId := (if (cfg.wayPartitions > 1)
    s1Req.id(log2Ceil(cfg.wayPartitions) - 1, 0) else 0.U)
  wayPartition.io.plruVictim      := plruVictim
  wayPartition.io.wayValids       := s1Valids
  wayPartition.io.busyMask        := s1BusyMask
  wayPartition.io.wayPartitionMap := wayPartMap
  val victimWay = wayPartition.io.victimWay

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
  // Victim was being refilled at lookup time: its tag/dirty and the data read are
  // valid only if it has been refilled since, so the request must look up again
  val s2VictimWasBusy = RegInit(false.B)

  // Issue data array read: hit → read hitWay; miss → read victimWay (for eviction data).
  // An invalid victim has nothing to evict, so a miss on it needs no read.
  // The SRAM output is valid for one cycle only, so S2 re-issues its read while
  // it is held (stalled, or a hit waiting for a refill completion)
  val dataReadWay = Mux(hit, hitWay, victimWay)
  val dataNeeded = hit || s1Valids(victimWay)
  val s1DataRead = s1Valid && !pipelineStall && dataNeeded
  val s2DataRead = s2Valid && !s2Done && (s2Hit || s2VictimValid)
  dataArray.io.read.enable := s1DataRead || s2DataRead
  dataArray.io.read.set    := Mux(s1DataRead, s1Set, s2Set)
  dataArray.io.read.way    := Mux(s1DataRead, dataReadWay, Mux(s2Hit, s2HitWay, s2VictimWay))

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
    s2VictimWasBusy := mshrBusy(s1Set, victimWay)
    s1Valid       := false.B // S1 consumed
  }

  // A lookup held in S1 or S2 is stale once a refill rewrites tag and data: the
  // line it missed on may now be resident, so the request must look up again.
  // S2 goes first because it can be the request that waited on this refill.
  when(refillComplete) {
    val s2OnRefill = s2Valid && !s2Hit &&
      (s2Set === refillCompleteReq.set) && (s2Tag === refillCompleteReq.tag)
    val s2TakesHead = s2OnRefill && !replayValid
    when(s2OnRefill) {
      when(!replayValid) {
        replayValid := true.B
        replayReq   := s2Req
      }.otherwise {
        replay2Valid := true.B
        replay2Req   := s2Req
      }
      s2Valid := false.B
    }
    when(s1Valid) {
      when(!s2TakesHead) {
        replayValid := true.B
        replayReq   := s1Req
      }.otherwise {
        replay2Valid := true.B
        replay2Req   := s1Req
      }
      s1Valid := false.B
    }
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
  val s2VictimBusy   = s2Miss && mshrBusy(s2Set, s2VictimWay)
  // An early response takes the response slot this cycle; a hit in S2 then holds
  // the pipeline for one cycle so S1 does not overwrite it before it responds.
  val s2HitDefer = s2Valid && s2Hit && earlyRespValid
  s2Stall := (s2Valid && !s2Hit && (s2MshrFull || s2MshrConflict || s2VictimBusy)) || s2HitDefer

  when(s2Miss && !s2VictimBusy && s2VictimWasBusy && !refillBusy) {
    when(!replayValid) {
      replayValid := true.B
      replayReq   := s2Req
    }.otherwise {
      replay2Valid := true.B
      replay2Req   := s2Req
    }
    s2Valid := false.B
  }

  // ---- Hit path ----
  // The victim was busy at lookup time and is free again, or the refill that
  // completed this cycle may have brought this very line: look up again
  val s2Replay = (s2Miss && !s2VictimBusy && s2VictimWasBusy && !refillBusy) ||
                 (refillComplete && s2Valid && !s2Hit &&
                  (s2Set === refillCompleteReq.set) && (s2Tag === refillCompleteReq.tag))
  val s2HitFire = s2Valid && s2Hit && !refillBusy && canPushResp && !s2HitDefer
  when(s2HitFire) {
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
  val allocFire = s2Valid && !s2Hit && !mshrAddrHit && mshr.io.allocReady && !refillBusy &&
                  !s2VictimBusy && !s2VictimWasBusy
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

  // The victim way holds the old line until refill completion, so its tag is
  // invalidated here: a later access to the old line must miss and refill it
  // elsewhere instead of writing data that the refill would overwrite.
  when(allocFire && s2VictimValid) {
    tagArray.io.write.enable := true.B
    tagArray.io.write.set     := s2Set
    tagArray.io.write.way     := s2VictimWay
    tagArray.io.write.tag     := s2VictimTag
    tagArray.io.write.valid   := false.B
    tagArray.io.write.dirty   := false.B
  }

  when(allocFire) {
    s2Valid := false.B // consumed
  }
  s2Done := s2HitFire || allocFire || s2Replay

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
  val sFlushIdle :: sQuiesce :: sReadTag :: sWaitTag :: sClassify :: sReadData :: sWaitData :: sEnqWb :: sWriteTag :: sDrain :: sDone :: Nil = Enum(11)

  val flushState = RegInit(sFlushIdle)
  val flushInv   = RegInit(false.B)
  val curSet     = RegInit(0.U(cfg.setBits.W))
  val curWay     = RegInit(0.U(log2Ceil(cfg.ways).W))

  // Latched tag read result for the current set, kept while its ways are resolved.
  val latchedTags  = Reg(Vec(cfg.ways, UInt(cfg.tagBits.W)))
  val latchedDirtys = Reg(UInt(cfg.ways.W))
  // Ways of the current set still needing an action. Dirty lines are written
  // back in both modes; clean lines only lose their valid bit under invalidate.
  val walkNeed = RegInit(0.U(cfg.ways.W))
  // Latched line data for the way currently being written back.
  val flushLineData = Reg(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))

  flushActive  := flushState =/= sFlushIdle
  flushWalking := flushActive && flushState =/= sQuiesce

  // Quiesce condition: pipeline, MSHR and write-merge buffer drained and no write
  // burst held by the top level, so the walk sees every accepted write and the
  // flush FSM has exclusive use of the arrays and wbReq. Requests that were
  // accepted before the trigger keep draining while waiting here.
  val flushQuiesced = !s1Valid && !s2Valid && !replayPend && mshr.io.allIdle &&
                      io.wmBufEmpty && io.wrPortIdle

  // While the flush FSM owns wbReq, keep it deasserted unless we are enqueueing
  // (MSHR is idle once the walk starts, so this simple override is safe).
  // No MSHR entry exists during the walk, so the owner of these writebacks
  // is never matched by wbDone
  when(flushWalking) {
    io.wbReq.valid        := false.B
    io.wbReq.bits.address := 0.U
    io.wbReq.bits.data    := VecInit(Seq.fill(cfg.beatsPerLine)(0.U(cfg.dataWidth.W)))
    io.wbReq.bits.owner   := 0.U
  }

  // The tag read data is valid in sWaitTag only; every later resolution of the
  // set works on the latched state
  val rdNeed = tagArray.io.readResp.valids.asUInt &
               (tagArray.io.readResp.dirtys.asUInt | Fill(cfg.ways, flushInv))
  val walkNeedNow  = Mux(flushState === sWaitTag, rdNeed, walkNeed)
  val walkWay      = PriorityEncoder(walkNeedNow)
  val walkWayDirty = Mux(flushState === sWaitTag,
                         tagArray.io.readResp.dirtys.asUInt, latchedDirtys)(walkWay)

  // Jump to the lowest way still needing an action; a set with none left is done
  def resolveWays(): Unit = {
    when(walkNeedNow.orR) {
      curWay := walkWay
      flushState := Mux(walkWayDirty, sReadData, sWriteTag)
    }.elsewhen(curSet === (cfg.sets - 1).U) {
      flushState := sDrain
    }.otherwise {
      curSet     := curSet + 1.U
      flushState := sReadTag
    }
  }

  switch(flushState) {
    is(sFlushIdle) {
      when(io.flushStart) {
        flushInv   := io.flushIsInvalidate
        curSet     := 0.U
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
      latchedTags  := tagArray.io.readResp.tags
      latchedDirtys := tagArray.io.readResp.dirtys.asUInt
      walkNeed     := rdNeed
      resolveWays()
    }
    is(sClassify) {
      resolveWays()
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
      io.wbReq.bits.owner   := 0.U
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
      walkNeed := walkNeed & ~UIntToOH(curWay, cfg.ways)
      flushState := sClassify
    }
    is(sDrain) {
      // The write-back buffer being empty is not enough: its last entry sits in
      // the top-level FSM until the downstream B arrives.
      when(io.wbBufEmpty && io.wbIdle) { flushState := sDone }
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
  val hitFire = s2Valid && s2Hit && !refillBusy && canPushResp && !s2HitDefer
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
