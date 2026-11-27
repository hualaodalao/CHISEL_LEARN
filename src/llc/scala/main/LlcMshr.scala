import chisel3._
import chisel3.util._

// MSHR allocation request bundle
class MshrAllocReq(cfg: LlcConfig) extends Bundle {
  val address      = UInt(cfg.addressWidth.W)
  val set          = UInt(cfg.setBits.W)
  val way          = UInt(log2Ceil(cfg.ways).W)
  val dirtyEvict   = Bool()
  val evictTag     = UInt(cfg.tagBits.W)
  val evictData    = Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))
  val reqId        = UInt(cfg.idWidth.W)
  val reqType      = Bool() // false=read, true=write
  val writeData    = UInt(cfg.dataWidth.W)
  val writeStrb    = UInt((cfg.dataWidth / 8).W)
  val writeBeatIdx = UInt(log2Ceil(cfg.beatsPerLine).W)
  // The request needs its target beat only
  val singleBeat = Bool()
}

// Writeback request bundle
class MshrWbReq(cfg: LlcConfig) extends Bundle {
  val address = UInt(cfg.addressWidth.W)
  val data    = Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))
  // The entry that owns the writeback; it comes back with wbDone
  val owner   = UInt(cfg.mshrIdxBits.W)
}

// Refill request bundle
class MshrRefillReq(cfg: LlcConfig) extends Bundle {
  val address = UInt(cfg.addressWidth.W)
  val id      = UInt(cfg.idWidth.W)
}

// Refill data bundle
class MshrRefillData(cfg: LlcConfig) extends Bundle {
  val data = UInt(cfg.dataWidth.W)
  val last = Bool()
}

// Early response bundle (single-beat early restart for read misses)
class MshrEarlyResp(cfg: LlcConfig) extends Bundle {
  val id      = UInt(cfg.idWidth.W)
  val data    = UInt(cfg.dataWidth.W)  // single beat data
  val beatIdx = UInt(log2Ceil(cfg.beatsPerLine).W)
}

// Completion notification bundle
class MshrCompleteReq(cfg: LlcConfig) extends Bundle {
  val set           = UInt(cfg.setBits.W)
  val way           = UInt(log2Ceil(cfg.ways).W)
  val tag           = UInt(cfg.tagBits.W)
  val data          = Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))
  val dirty         = Bool() // true if write-allocate (merged write)
  val reqId         = UInt(cfg.idWidth.W)
  val reqType       = Bool()
  val earlyRespDone = Bool() // true if early response already sent (skip re-send)
}

// Location an MSHR entry is refilling
class MshrInflight(cfg: LlcConfig) extends Bundle {
  val set = UInt(cfg.setBits.W)
  val way = UInt(log2Ceil(cfg.ways).W)
}

// MSHR IO
class LlcMshrIO(cfg: LlcConfig) extends Bundle {
  // Allocate interface (from pipeline on miss)
  val allocReq   = Flipped(Valid(new MshrAllocReq(cfg)))
  val allocReady = Output(Bool())

  // Writeback request to downstream (AXI4 master AW+W)
  val wbReq    = Valid(new MshrWbReq(cfg))
  val wbReady  = Input(Bool()) // the write-back buffer can take wbReq this cycle
  val wbDone   = Input(Valid(UInt(cfg.mshrIdxBits.W)))

  // Refill request to downstream (AXI4 master AR)
  val refillReq   = Valid(new MshrRefillReq(cfg))
  val refillReady = Input(Bool())

  // Refill data from downstream (AXI4 master R)
  val refillData = Flipped(Valid(new MshrRefillData(cfg)))

  // Completion notification (to pipeline - write to tag/data array)
  val complete = Valid(new MshrCompleteReq(cfg))

  // Early response output (critical-word-first for read misses)
  val earlyResp      = Output(Valid(new MshrEarlyResp(cfg)))
  val earlyRespReady = Input(Bool()) // the pipeline can take earlyResp this cycle

  // Status
  val full = Output(Bool())
  // All entries idle (no valid in-flight miss/writeback/refill); used by flush FSM quiesce
  val allIdle = Output(Bool())

  // Set/way each valid entry is refilling
  val inflight = Output(Vec(cfg.mshrEntries, Valid(new MshrInflight(cfg))))

  // Check if address is already pending (for same-line merging)
  val addrMatch = new Bundle {
    val query = Input(UInt(cfg.addressWidth.W))
    val hit   = Output(Bool())
  }
}

// MSHR state encoding
object MshrState {
  val sIdle       = 0.U(3.W)
  val sWbSend     = 1.U(3.W)
  val sWbWait     = 2.U(3.W)
  val sRefillSend = 3.U(3.W)
  val sRefillRecv = 4.U(3.W)
  val sDone       = 5.U(3.W)
}

class LlcMshr(cfg: LlcConfig) extends Module {
  val io = IO(new LlcMshrIO(cfg))

  val numEntries = cfg.mshrEntries

  // ---- MSHR entry registers ----
  val valid       = RegInit(VecInit(Seq.fill(numEntries)(false.B)))
  val address     = Reg(Vec(numEntries, UInt(cfg.addressWidth.W)))
  val set         = Reg(Vec(numEntries, UInt(cfg.setBits.W)))
  val way         = Reg(Vec(numEntries, UInt(log2Ceil(cfg.ways).W)))
  val state       = RegInit(VecInit(Seq.fill(numEntries)(MshrState.sIdle)))
  val dirtyEvict  = Reg(Vec(numEntries, Bool()))
  val evictTag    = Reg(Vec(numEntries, UInt(cfg.tagBits.W)))
  val evictData   = Reg(Vec(numEntries, Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))))
  val refillData  = Reg(Vec(numEntries, Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))))
  val beatCounter = RegInit(VecInit(Seq.fill(numEntries)(0.U(log2Ceil(cfg.beatsPerLine + 1).W))))
  val reqId       = Reg(Vec(numEntries, UInt(cfg.idWidth.W)))
  val reqType     = Reg(Vec(numEntries, Bool()))
  val writeData   = Reg(Vec(numEntries, UInt(cfg.dataWidth.W)))
  val writeStrb   = Reg(Vec(numEntries, UInt((cfg.dataWidth / 8).W)))
  val writeBeatIdx = Reg(Vec(numEntries, UInt(log2Ceil(cfg.beatsPerLine).W)))
  val singleBeat   = Reg(Vec(numEntries, Bool()))
  val earlyRespSent = RegInit(VecInit(Seq.fill(numEntries)(false.B)))

  // ---- Allocation logic (first-free) ----
  val freeVec  = valid.map(!_)
  val freeIdx  = PriorityEncoder(freeVec)
  val hasFree  = freeVec.reduce(_ || _)

  io.allocReady := hasFree
  io.full       := !hasFree
  // All entries free/idle => no in-flight miss, writeback, or refill
  io.allIdle    := !valid.reduce(_ || _)
  for (i <- 0 until numEntries) {
    io.inflight(i).valid    := valid(i)
    io.inflight(i).bits.set := set(i)
    io.inflight(i).bits.way := way(i)
  }

  // Allocate on valid request when we have space
  when(io.allocReq.valid && hasFree) {
    val idx = freeIdx
    valid(idx)       := true.B
    address(idx)     := io.allocReq.bits.address
    set(idx)         := io.allocReq.bits.set
    way(idx)         := io.allocReq.bits.way
    dirtyEvict(idx)  := io.allocReq.bits.dirtyEvict
    evictTag(idx)    := io.allocReq.bits.evictTag
    evictData(idx)   := io.allocReq.bits.evictData
    reqId(idx)       := io.allocReq.bits.reqId
    reqType(idx)     := io.allocReq.bits.reqType
    writeData(idx)   := io.allocReq.bits.writeData
    writeStrb(idx)   := io.allocReq.bits.writeStrb
    writeBeatIdx(idx) := io.allocReq.bits.writeBeatIdx
    singleBeat(idx)   := io.allocReq.bits.singleBeat
    beatCounter(idx) := 0.U
    earlyRespSent(idx) := false.B

    // Determine initial state based on whether we need writeback
    when(io.allocReq.bits.dirtyEvict) {
      state(idx) := MshrState.sWbSend
    }.otherwise {
      state(idx) := MshrState.sRefillSend
    }
  }

  // ---- Arbitration: select one active entry for wb/refill ----
  // Priority encoder among entries needing writeback
  val wbPendingVec = (0 until numEntries).map(i => valid(i) && state(i) === MshrState.sWbSend)
  val wbSelValid   = wbPendingVec.reduce(_ || _)
  val wbSelIdx     = PriorityEncoder(wbPendingVec)

  // Priority encoder among entries needing refill
  val refillPendingVec = (0 until numEntries).map(i => valid(i) && state(i) === MshrState.sRefillSend)
  val refillSelValid   = refillPendingVec.reduce(_ || _)
  // The top level latches the address of the request it first sees and holds
  // the AR until downstream takes it, so the request must not change until then
  val refillHoldIdx  = Reg(UInt(log2Ceil(numEntries).W))
  val refillHold     = RegInit(false.B)
  val refillHeld     = refillHold && valid(refillHoldIdx) && state(refillHoldIdx) === MshrState.sRefillSend
  val refillSelIdx   = Mux(refillHeld, refillHoldIdx, PriorityEncoder(refillPendingVec))

  // Priority encoder among entries receiving refill data
  val refillRecvVec = (0 until numEntries).map(i => valid(i) && state(i) === MshrState.sRefillRecv)
  val refillRecvValid = refillRecvVec.reduce(_ || _)
  val refillRecvIdx   = PriorityEncoder(refillRecvVec)

  // Priority encoder among done entries
  val doneVec   = (0 until numEntries).map(i => valid(i) && state(i) === MshrState.sDone)
  val doneValid = doneVec.reduce(_ || _)
  val doneIdx   = PriorityEncoder(doneVec)

  // ---- Writeback request output ----
  // Construct writeback address from evictTag + set + zero offset
  val wbAddress = Cat(evictTag(wbSelIdx), set(wbSelIdx), 0.U(cfg.offsetBits.W))

  io.wbReq.valid        := wbSelValid
  io.wbReq.bits.address := wbAddress
  io.wbReq.bits.data    := evictData(wbSelIdx)
  io.wbReq.bits.owner   := wbSelIdx

  // The request stays on the port until the write-back buffer takes it; the
  // entry then waits for the write-back to complete (wbDone).
  when(wbSelValid && io.wbReady) {
    state(wbSelIdx) := MshrState.sWbWait
  }

  // ---- Writeback done handling ----
  // Only the entry that owns the finished writeback resumes; several entries
  // can wait at once and the lowest-numbered one need not be first
  when(io.wbDone.valid) {
    for (i <- 0 until numEntries) {
      when(valid(i) && state(i) === MshrState.sWbWait && io.wbDone.bits === i.U) {
        state(i) := MshrState.sRefillSend
      }
    }
  }

  // ---- Refill request output ----
  io.refillReq.valid        := refillSelValid
  io.refillReq.bits.address := address(refillSelIdx)
  io.refillReq.bits.id      := reqId(refillSelIdx)

  // Transition: sRefillSend -> sRefillRecv when downstream accepts AR
  when(refillSelValid && io.refillReady) {
    state(refillSelIdx)       := MshrState.sRefillRecv
    beatCounter(refillSelIdx) := 0.U
    refillHold := false.B
  }.elsewhen(refillSelValid) {
    refillHold    := true.B
    refillHoldIdx := refillSelIdx
  }

  // ---- Early response output (Critical-Word-First) ----
  // Only for single-beat read misses: when the target beat arrives during
  // refill and the pipeline takes it, fire earlyResp. If it cannot be taken,
  // completion sends the whole line instead; a longer read always needs the
  // whole line and is answered at completion only.
  val earlyRespFire = io.earlyRespReady && io.refillData.valid && refillRecvValid &&
    !reqType(refillRecvIdx) && // read miss only
    singleBeat(refillRecvIdx) &&
    !earlyRespSent(refillRecvIdx) &&
    (beatCounter(refillRecvIdx) === writeBeatIdx(refillRecvIdx))

  io.earlyResp.valid         := earlyRespFire
  io.earlyResp.bits.id       := reqId(refillRecvIdx)
  io.earlyResp.bits.data     := io.refillData.bits.data
  io.earlyResp.bits.beatIdx  := writeBeatIdx(refillRecvIdx)

  // ---- Refill data reception ----
  when(io.refillData.valid && refillRecvValid) {
    val idx = refillRecvIdx
    refillData(idx)(beatCounter(idx)) := io.refillData.bits.data
    beatCounter(idx) := beatCounter(idx) + 1.U

    // Mark early response as sent when it fires
    when(earlyRespFire) {
      earlyRespSent(idx) := true.B
    }

    when(io.refillData.bits.last) {
      state(idx) := MshrState.sDone
    }
  }

  // ---- Completion output (Done state) ----
  // Extract tag from the miss address
  val doneTag = address(doneIdx)(cfg.addressWidth - 1, cfg.setBits + cfg.offsetBits)

  // Merge write data into refill data for write-allocate
  val mergedData = Wire(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))
  for (beat <- 0 until cfg.beatsPerLine) {
    when(reqType(doneIdx) && writeBeatIdx(doneIdx) === beat.U) {
      // Byte-level merge using strobe
      val originalBytes = Wire(Vec(cfg.dataWidth / 8, UInt(8.W)))
      val writeBytes    = Wire(Vec(cfg.dataWidth / 8, UInt(8.W)))
      val resultBytes   = Wire(Vec(cfg.dataWidth / 8, UInt(8.W)))
      for (b <- 0 until cfg.dataWidth / 8) {
        originalBytes(b) := refillData(doneIdx)(beat)((b + 1) * 8 - 1, b * 8)
        writeBytes(b)    := writeData(doneIdx)((b + 1) * 8 - 1, b * 8)
        resultBytes(b)   := Mux(writeStrb(doneIdx)(b), writeBytes(b), originalBytes(b))
      }
      mergedData(beat) := resultBytes.asUInt
    }.otherwise {
      mergedData(beat) := refillData(doneIdx)(beat)
    }
  }

  io.complete.valid         := doneValid
  io.complete.bits.set      := set(doneIdx)
  io.complete.bits.way      := way(doneIdx)
  io.complete.bits.tag      := doneTag
  io.complete.bits.data     := mergedData
  io.complete.bits.dirty    := reqType(doneIdx)
  io.complete.bits.reqId    := reqId(doneIdx)
  io.complete.bits.reqType       := reqType(doneIdx)
  io.complete.bits.earlyRespDone := earlyRespSent(doneIdx)

  // When completion is output, deallocate the entry
  when(doneValid) {
    valid(doneIdx) := false.B
    state(doneIdx) := MshrState.sIdle
  }

  // ---- Address match (for same-line merging / hazard detection) ----
  val lineMask     = (~((1 << cfg.offsetBits) - 1).U(cfg.addressWidth.W)).asUInt
  val queryAligned = io.addrMatch.query & lineMask
  val matchVec = (0 until numEntries).map { i =>
    valid(i) && (address(i) & lineMask) === queryAligned
  }
  io.addrMatch.hit := matchVec.reduce(_ || _)
}
