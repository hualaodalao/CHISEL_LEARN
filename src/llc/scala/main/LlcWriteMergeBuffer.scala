import chisel3._
import chisel3.util._

/** Write request bundle for the Write Merge Buffer input. */
class WmBufWriteReq(cfg: LlcConfig) extends Bundle {
  val address = UInt(cfg.addressWidth.W)
  val beatIdx = UInt(log2Ceil(cfg.beatsPerLine).W)
  val data    = UInt(cfg.dataWidth.W)
  val strb    = UInt(cfg.bytePerBeat.W)
  val id      = UInt(cfg.idWidth.W)
  val last    = Bool() // last beat of an AW transaction → increment pendingResps
}

/** B response info emitted by the Write Merge Buffer after a flush completes. */
class WmBufBResp(cfg: LlcConfig) extends Bundle {
  val id    = UInt(cfg.idWidth.W)
  val count = UInt(log2Ceil(cfg.beatsPerLine + 1).W) // number of B responses to send
  val beats = UInt(log2Ceil(cfg.beatsPerLine + 1).W) // pipeline beats flushed
}

/** Write Merge Buffer for LLC.
  *
  * Merges multiple writes to the same cache line before flushing to pipeline,
  * reducing data-array write operations and improving throughput.
  *
  * Flush triggers:
  *   (a) The last beat of a write burst has been accepted
  *   (b) Buffer full and new non-matching write arrives (evict oldest)
  *   (c) Read snoop hit (RAW hazard avoidance)
  *
  * An entry waiting to be flushed no longer merges new beats, so merging only
  * happens within one burst.
  */
class LlcWriteMergeBuffer(cfg: LlcConfig) extends Module {
  val io = IO(new Bundle {
    // Write request input (from AXI4 write front-end)
    val writeIn = slave(Stream(new WmBufWriteReq(cfg)))

    // Pipeline request output (flushed writes)
    val pipeOut = master(Stream(new LlcReq(cfg)))

    // B response info, accepted by the top level before any beat of the entry
    val bRespOut = master(Stream(new WmBufBResp(cfg)))

    // Snoop for RAW hazard detection (from read path)
    val snoop = new Bundle {
      val address  = Input(UInt(cfg.addressWidth.W))
      val hit      = Output(Bool())
      val flushReq = Input(Bool())  // request flush of matching entry
      val flushDone = Output(Bool()) // flush of snooped entry completed
    }

    // Status
    val empty    = Output(Bool())
    val flushing = Output(Bool())

    // Performance event: pulses when a write is merged into an existing entry
    val mergeEvent = Output(Bool())
  })

  // Line-aligned address mask: zero out offset bits
  val lineMask = (~((BigInt(1) << cfg.offsetBits) - 1) &
    ((BigInt(1) << cfg.addressWidth) - 1)).U(cfg.addressWidth.W)

  val numEntries = cfg.wmBufEntries

  // ======== Per-entry state ========
  val entryValid      = RegInit(VecInit(Seq.fill(numEntries)(false.B)))
  val entryAddr       = Reg(Vec(numEntries, UInt(cfg.addressWidth.W)))
  val entryData       = Reg(Vec(numEntries, Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))))
  val entryStrb       = Reg(Vec(numEntries, Vec(cfg.beatsPerLine, UInt(cfg.bytePerBeat.W))))
  val entryBeatValids = RegInit(VecInit(Seq.fill(numEntries)(
    VecInit(Seq.fill(cfg.beatsPerLine)(false.B)))))
  val entryId         = Reg(Vec(numEntries, UInt(cfg.idWidth.W)))
  // Set by the last beat of a burst: the entry is flushed and merges nothing more
  val entryClosed     = RegInit(VecInit(Seq.fill(numEntries)(false.B)))
  val entryPendResps  = RegInit(VecInit(Seq.fill(numEntries)(0.U(log2Ceil(cfg.beatsPerLine + 1).W))))

  // ======== Flush FSM ========
  val sIdle :: sFlush :: sBResp :: Nil = Enum(3)
  val state        = RegInit(sIdle)
  val flushIdx     = Reg(UInt(log2Ceil(numEntries).W))
  val flushBeatIdx = Reg(UInt(log2Ceil(cfg.beatsPerLine).W))
  val flushIsSnoop = Reg(Bool()) // whether current flush was triggered by snoop
  val bInfoSent   = RegInit(false.B)

  // ======== Combinational match/free/full detection ========
  val writeInAddr = io.writeIn.payload.address & lineMask

  // Address match for write merging
  val matchVec = VecInit((0 until numEntries).map(i =>
    entryValid(i) && !entryClosed(i) && (entryAddr(i) === writeInAddr)))
  val matchHit = matchVec.asUInt.orR
  val matchIdx = PriorityEncoder(matchVec.asUInt)

  // Free entry detection
  val freeVec = VecInit((0 until numEntries).map(i => !entryValid(i)))
  val hasFree = freeVec.asUInt.orR
  val freeIdx = PriorityEncoder(freeVec.asUInt)

  val closedVec = VecInit((0 until numEntries).map(i => entryValid(i) && entryClosed(i)))
  val hasClosed = closedVec.asUInt.orR
  val closedIdx = PriorityEncoder(closedVec.asUInt)

  // Buffer full (no free entries and no match for current write)
  val bufferFull = !hasFree && !matchHit

  // ======== Snoop logic ========
  val snoopAddr = io.snoop.address & lineMask
  val snoopMatchVec = VecInit((0 until numEntries).map(i =>
    entryValid(i) && (entryAddr(i) === snoopAddr)))
  val snoopHit = snoopMatchVec.asUInt.orR
  val snoopIdx = PriorityEncoder(snoopMatchVec.asUInt)
  io.snoop.hit := snoopHit

  // ======== Status outputs ========
  io.empty    := !entryValid.asUInt.orR
  io.flushing := (state =/= sIdle)

  // ======== Default output drivers ========
  io.writeIn.ready    := false.B
  io.pipeOut.valid    := false.B
  io.pipeOut.payload  := DontCare
  io.bRespOut.valid   := false.B
  io.bRespOut.payload    := DontCare
  io.snoop.flushDone  := false.B
  io.mergeEvent       := false.B

  // ======== Byte-level data merge helper ========
  def byteMerge(oldData: UInt, newData: UInt, newStrb: UInt): UInt = {
    val merged = Wire(Vec(cfg.bytePerBeat, UInt(8.W)))
    for (b <- 0 until cfg.bytePerBeat) {
      merged(b) := Mux(newStrb(b), newData(b * 8 + 7, b * 8), oldData(b * 8 + 7, b * 8))
    }
    merged.asUInt
  }

  // ======== Main FSM ========
  switch(state) {
    is(sIdle) {
      // Priority: (1) snoop flush request, (2) closed entry, (3) accept writes
      when(io.snoop.flushReq) {
        when(snoopHit) {
          // Snoop-triggered flush
          state        := sFlush
          flushIdx     := snoopIdx
          flushBeatIdx := 0.U
          flushIsSnoop := true.B
        }.otherwise {
          // The entry left in an auto flush, which already reached the pipeline
          io.snoop.flushDone := true.B
        }
      }.elsewhen(hasClosed) {
        state        := sFlush
        flushIdx     := closedIdx
        flushBeatIdx := 0.U
        flushIsSnoop := false.B
      }.elsewhen(bufferFull && io.writeIn.valid) {
        // Buffer full, evict first valid entry (oldest by index priority)
        state        := sFlush
        flushIdx     := PriorityEncoder(entryValid.asUInt)
        flushBeatIdx := 0.U
        flushIsSnoop := false.B
      }.otherwise {
        // Accept new writes (only when not full, or matching existing entry)
        io.writeIn.ready := !bufferFull || matchHit

        when(io.writeIn.fire) {
          val beat = io.writeIn.payload.beatIdx
          val data = io.writeIn.payload.data
          val strb = io.writeIn.payload.strb
          val id   = io.writeIn.payload.id
          val last = io.writeIn.payload.last

          when(matchHit) {
            // Merge into existing entry
            val idx = matchIdx
            entryData(idx)(beat) := byteMerge(entryData(idx)(beat), data, strb)
            entryStrb(idx)(beat) := entryStrb(idx)(beat) | strb
            entryBeatValids(idx)(beat) := true.B
            entryPendResps(idx) := entryPendResps(idx) + last
            entryClosed(idx)   := entryClosed(idx) || last
            io.mergeEvent := true.B
          }.otherwise {
            // Allocate new entry
            val idx = freeIdx
            entryValid(idx) := true.B
            entryAddr(idx)  := writeInAddr
            entryId(idx)    := id
            entryPendResps(idx) := last
            entryClosed(idx)   := last
            // Initialize all beats
            for (b <- 0 until cfg.beatsPerLine) {
              entryData(idx)(b)       := 0.U
              entryStrb(idx)(b)       := 0.U
              entryBeatValids(idx)(b) := false.B
            }
            // Write current beat
            entryData(idx)(beat)       := data
            entryStrb(idx)(beat)       := strb
            entryBeatValids(idx)(beat) := true.B
          }
        }
      }
    }

    is(sFlush) {
      // Send beats with valid data to pipeline
      val curBeatValid = entryBeatValids(flushIdx)(flushBeatIdx)
      val isLastBeat   = (flushBeatIdx === (cfg.beatsPerLine - 1).U)

      // The top level counts pipeline write responses against this info, so it
      // must be accepted before any beat of the entry is sent.
      val infoDone = bInfoSent || io.bRespOut.fire
      when(!bInfoSent) {
        io.bRespOut.valid      := true.B
        io.bRespOut.payload.id     := entryId(flushIdx)
        io.bRespOut.payload.count  := entryPendResps(flushIdx)
        io.bRespOut.payload.beats  := PopCount(entryBeatValids(flushIdx).asUInt)
        when(io.bRespOut.ready) {
          bInfoSent := true.B
          entryPendResps(flushIdx) := 0.U
        }
      }

      when(infoDone && curBeatValid) {
        // Drive pipeline output for this beat
        io.pipeOut.valid              := true.B
        io.pipeOut.payload.isWrite    := true.B
        io.pipeOut.payload.address    := entryAddr(flushIdx)
        io.pipeOut.payload.id         := entryId(flushIdx)
        io.pipeOut.payload.writeData  := entryData(flushIdx)(flushBeatIdx)
        io.pipeOut.payload.writeStrb  := entryStrb(flushIdx)(flushBeatIdx)
        io.pipeOut.payload.beatIdx    := flushBeatIdx
        io.pipeOut.payload.singleBeat := false.B

        when(io.pipeOut.fire) {
          when(isLastBeat) {
            // Done flushing all beats
            state := sBResp
            entryValid(flushIdx) := false.B
            entryClosed(flushIdx) := false.B
            entryBeatValids(flushIdx) := VecInit(Seq.fill(cfg.beatsPerLine)(false.B))
          }.otherwise {
            flushBeatIdx := flushBeatIdx + 1.U
          }
        }
      }.elsewhen(infoDone) {
        // Skip this beat (no valid data)
        when(isLastBeat) {
          state := sBResp
          entryValid(flushIdx) := false.B
          entryClosed(flushIdx) := false.B
          entryBeatValids(flushIdx) := VecInit(Seq.fill(cfg.beatsPerLine)(false.B))
        }.otherwise {
          flushBeatIdx := flushBeatIdx + 1.U
        }
      }
    }

    is(sBResp) {
      bInfoSent := false.B
      io.snoop.flushDone := flushIsSnoop
      state := sIdle
    }
  }
}
