import chisel3._
import chisel3.util._

/** Local byte realignment path; start is accepted only while idle (not an abort).
  * FbBytePacket is shared with the byte-aware format converter.
  */
class FbByteReader(cfg: ForagerBeeConfig) extends Module {
  private val dw = cfg.dataWidth
  private val BB = cfg.beatBytes
  private val addrW = cfg.addressWidth
  private val byteW = log2Ceil(BB + 1)
  private val countW = math.max(25, log2Ceil(BB) + 2)
  private val bufferW = log2Ceil(2 * BB + 1)
  private val depth = cfg.resolvedDataQueueDepth
  private val creditW = math.max(1, log2Ceil(depth + 1))
  require(dw % 8 == 0, "FbByteReader requires whole-byte beats")

  val io = IO(new Bundle {
    val start = Input(Bool())
    val srcAddr = Input(UInt(addrW.W))
    val byteCount = Input(UInt(24.W))
    val out = Decoupled(new FbBytePacket(dw))
    val bus = master(new FbStreamBus(dw, addrW))
    val busy = Output(Bool())
    val done = Output(Bool())
    val err = Output(Bool())
  })

  val busy = RegInit(false.B)
  val done = RegInit(false.B)
  val err = RegInit(false.B)
  val begin = io.start && !busy
  val address = RegInit(0.U(addrW.W))
  val physicalRemaining = RegInit(0.U(countW.W))
  val inputRemaining = RegInit(0.U(24.W))
  val outputRemaining = RegInit(0.U(24.W))
  val skip = RegInit(0.U(byteW.W))
  val buffer = RegInit(0.U((2 * dw).W))
  val buffered = RegInit(0.U(bufferW.W))

  // Credit counts free entries minus ALL reserved, not-yet-returned beats.
  // A pending request's length is registered, so credit returns cannot change it.
  val credit = RegInit(depth.U(creditW.W))
  val requestValid = RegInit(false.B)
  val requestBeats = RegInit(0.U(9.W))
  val readActive = RegInit(false.B)
  val receiveRemaining = RegInit(0.U(9.W))
  val physicalQueue = withReset(reset.asBool || begin) {
    Module(new Queue(UInt(dw.W), depth))
  }

  io.busy := busy
  io.done := done
  io.err := err
  io.bus.wr.valid := false.B
  io.bus.wr.payload := 0.U.asTypeOf(io.bus.wr.payload)
  io.bus.wrResp.ready := false.B
  io.bus.rd.valid := busy && requestValid
  io.bus.rd.payload.addr := address
  io.bus.rd.payload.len := requestBeats - 1.U

  // Also support a slave returning its first beat on the request handshake.
  val acceptingRead = busy && (readActive || io.bus.rd.fire)
  physicalQueue.io.enq.valid := acceptingRead && io.bus.rdData.valid
  physicalQueue.io.enq.bits := io.bus.rdData.payload.data
  io.bus.rdData.ready := acceptingRead && physicalQueue.io.enq.ready

  val outputBytes = Wire(UInt(byteW.W))
  outputBytes := Mux(outputRemaining < BB.U, outputRemaining, BB.U)
  io.out.valid := busy && outputRemaining =/= 0.U && buffered >= outputBytes
  io.out.bits.bytes := outputBytes
  io.out.bits.last := outputRemaining <= BB.U
  val outputMask = VecInit((0 until BB).map(i => Fill(8, i.U < outputBytes))).asUInt
  io.out.bits.data := buffer(dw - 1, 0) & outputMask

  // Never append while presenting an output: payload stays fixed under stalls,
  // and append/pop are mutually exclusive, with no last-connect data loss.
  physicalQueue.io.deq.ready := busy && !io.out.valid &&
    inputRemaining =/= 0.U && buffered <= BB.U
  val availableBytes = BB.U(byteW.W) - skip
  val take = Wire(UInt(byteW.W))
  take := Mux(inputRemaining < availableBytes, inputRemaining, availableBytes)
  val takeMask = VecInit((0 until BB).map(i => Fill(8, i.U < take))).asUInt
  val extracted = (physicalQueue.io.deq.bits >> (skip << 3)) & takeMask
  val creditLimit = Mux(credit < cfg.maxBurstLen.U, credit, cfg.maxBurstLen.U(9.W))
  val nextBeats = Mux(physicalRemaining < creditLimit, physicalRemaining, creditLimit)
  val expectedReceive = Mux(readActive, receiveRemaining, requestBeats)
  val offset = io.srcAddr % BB.U
  val totalPhysical = (io.byteCount.pad(countW) + offset + (BB - 1).U) / BB.U

  when(begin) {
    busy := io.byteCount =/= 0.U
    done := io.byteCount === 0.U
    err := false.B
    address := io.srcAddr - offset
    physicalRemaining := Mux(io.byteCount === 0.U, 0.U, totalPhysical)
    inputRemaining := io.byteCount
    outputRemaining := io.byteCount
    skip := offset
    buffer := 0.U
    buffered := 0.U
    credit := depth.U
    requestValid := false.B
    requestBeats := 0.U
    readActive := false.B
    receiveRemaining := 0.U
  }.elsewhen(busy) {
    when(!requestValid && !readActive && physicalRemaining =/= 0.U && credit =/= 0.U) {
      requestValid := true.B
      requestBeats := nextBeats
    }
    when(io.bus.rd.fire) {
      requestValid := false.B
      address := address + requestBeats * BB.U
      physicalRemaining := physicalRemaining - requestBeats
      readActive := true.B
      receiveRemaining := requestBeats
    }
    when(io.bus.rdData.fire) {
      receiveRemaining := expectedReceive - 1.U
      readActive := expectedReceive =/= 1.U
      err := err || io.bus.rdData.payload.err
    }
    // Reserve on rd.fire, return credit on physical dequeue; both can coincide.
    credit := (credit +& physicalQueue.io.deq.fire.asUInt) -
      Mux(io.bus.rd.fire, requestBeats, 0.U)
    when(physicalQueue.io.deq.fire) {
      buffer := buffer | (extracted.pad(2 * dw) << (buffered << 3))
      buffered := buffered + take
      inputRemaining := inputRemaining - take
      skip := 0.U
    }
    when(io.out.fire) {
      buffer := buffer >> (outputBytes << 3)
      buffered := buffered - outputBytes
      outputRemaining := outputRemaining - outputBytes
      when(io.out.bits.last) {
        busy := false.B
        done := true.B
      }
    }
  }
}

/** Byte packer with a two-beat data/strobe reservoir and bounded write bursts.
  * Completed-burst credit is counted at EOF, including EOF/response coincidence.
  */
class FbByteWriter(cfg: ForagerBeeConfig) extends Module {
  private val dw = cfg.dataWidth
  private val BB = cfg.beatBytes
  private val addrW = cfg.addressWidth
  private val byteW = log2Ceil(BB + 1)
  private val countW = math.max(25, log2Ceil(BB) + 2)
  private val bufferW = log2Ceil(2 * BB + 1)
  private val outstandingW = math.max(1, log2Ceil(cfg.writeOutstandingDepth + 1))
  require(dw % 8 == 0, "FbByteWriter requires whole-byte beats")

  val io = IO(new Bundle {
    val start = Input(Bool())
    val dstAddr = Input(UInt(addrW.W))
    val byteCount = Input(UInt(24.W))
    val in = Flipped(Decoupled(new FbBytePacket(dw)))
    val bus = master(new FbStreamBus(dw, addrW))
    val busy = Output(Bool())
    val done = Output(Bool())
    val err = Output(Bool())
  })

  val busy = RegInit(false.B)
  val done = RegInit(false.B)
  val err = RegInit(false.B)
  val begin = io.start && !busy
  val address = RegInit(0.U(addrW.W))
  val physicalRemaining = RegInit(0.U(countW.W))
  val inputRemaining = RegInit(0.U(24.W))
  val buffer = RegInit(0.U((2 * dw).W))
  val strobes = RegInit(0.U((2 * BB).W))
  // Includes the initial, unwritable destination prefix.
  val buffered = RegInit(0.U(bufferW.W))
  val burstValid = RegInit(false.B)
  val burstFirst = RegInit(false.B)
  val burstBeats = RegInit(0.U(9.W))
  val burstRemaining = RegInit(0.U(9.W))
  val outstanding = RegInit(0.U(outstandingW.W))

  io.busy := busy
  io.done := done
  io.err := err
  io.bus.rd.valid := false.B
  io.bus.rd.payload := 0.U.asTypeOf(io.bus.rd.payload)
  io.bus.rdData.ready := false.B

  val hasCredit = outstanding < cfg.writeOutstandingDepth.U
  val haveBeat = buffered >= BB.U || (inputRemaining === 0.U && buffered =/= 0.U)
  io.bus.wr.valid := busy && burstValid && haveBeat && (!burstFirst || hasCredit)
  io.bus.wr.payload := 0.U.asTypeOf(io.bus.wr.payload)
  io.bus.wr.payload.addr := address
  io.bus.wr.payload.len := burstBeats - 1.U
  io.bus.wr.payload.data := buffer(dw - 1, 0)
  io.bus.wr.payload.strb := strobes(BB - 1, 0)
  io.bus.wr.payload.sof := burstFirst
  io.bus.wr.payload.eof := burstRemaining === 1.U
  val eofFire = io.bus.wr.fire && io.bus.wr.payload.eof
  // No response credit for an unfinished burst. Older completed bursts may
  // respond during a newer burst; zero-latency EOF responses are legal too.
  io.bus.wrResp.ready := busy && (outstanding =/= 0.U || eofFire)
  val responseFire = io.bus.wrResp.fire

  // Appending and popping never coincide; a stalled write freezes the reservoir.
  io.in.ready := busy && inputRemaining =/= 0.U && buffered <= BB.U && !io.bus.wr.valid
  val boundedBytes = Mux(io.in.bits.bytes > BB.U, BB.U(byteW.W), io.in.bits.bytes)
  val take = Wire(UInt(byteW.W))
  take := Mux(inputRemaining < boundedBytes, inputRemaining, boundedBytes)
  val takeStrobes = VecInit((0 until BB).map(i => i.U < take)).asUInt
  val takeMask = VecInit((0 until BB).map(i => Fill(8, i.U < take))).asUInt
  val nextBeats = Mux(physicalRemaining < cfg.maxBurstLen.U,
    physicalRemaining, cfg.maxBurstLen.U(9.W))
  val offset = io.dstAddr % BB.U
  val totalPhysical = (io.byteCount.pad(countW) + offset + (BB - 1).U) / BB.U

  when(begin) {
    busy := io.byteCount =/= 0.U
    done := io.byteCount === 0.U
    err := false.B
    address := io.dstAddr - offset
    physicalRemaining := Mux(io.byteCount === 0.U, 0.U, totalPhysical)
    inputRemaining := io.byteCount
    buffer := 0.U
    strobes := 0.U
    buffered := offset
    burstValid := false.B
    burstFirst := false.B
    burstBeats := 0.U
    burstRemaining := 0.U
    outstanding := 0.U
  }.elsewhen(busy) {
    when(!burstValid && physicalRemaining =/= 0.U && hasCredit) {
      burstValid := true.B
      burstFirst := true.B
      burstBeats := nextBeats
      burstRemaining := nextBeats
    }
    when(io.in.fire) {
      buffer := buffer | ((io.in.bits.data & takeMask).pad(2 * dw) << (buffered << 3))
      strobes := strobes | (takeStrobes.pad(2 * BB) << buffered)
      buffered := buffered + take
      inputRemaining := inputRemaining - take
    }
    when(io.bus.wr.fire) {
      buffer := buffer >> dw
      strobes := strobes >> BB
      buffered := Mux(buffered > BB.U, buffered - BB.U, 0.U)
      address := address + BB.U
      physicalRemaining := physicalRemaining - 1.U
      burstFirst := false.B
      burstRemaining := burstRemaining - 1.U
      when(eofFire) { burstValid := false.B }
    }
    when(eofFire =/= responseFire) {
      outstanding := Mux(eofFire, outstanding + 1.U, outstanding - 1.U)
    }
    when(responseFire) { err := err || io.bus.wrResp.payload.err }
    when(inputRemaining === 0.U && physicalRemaining === 0.U &&
      !burstValid && outstanding === 0.U) {
      busy := false.B
      done := true.B
    }
  }
}
