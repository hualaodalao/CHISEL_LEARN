import chisel3._
import chisel3.util._

class QueenDsaCommand(val cfg: QueenConfig) extends Bundle {
    val inst = UInt(32.W)
    val rs1 = UInt(cfg.xlen.W)
    val rs2 = UInt(cfg.xlen.W)
}

class QueenDsaDecoder(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val inst = Vec(cfg.instructionLanes, Flipped(Decoupled(new QueenIssue(cfg))))
        // 与 scalar core 的 reg_read 总线同形：每 lane 两个槽，[2i]=rs1、[2i+1]=rs2。
        val regRead = Vec(2 * cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val gemm = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
        val dma = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
        val scheduler = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
        val trap = Valid(new QueenTrap(cfg))
        val qrdUsed = Input(UInt(log2Ceil(cfg.qrdDepth + 1).W))
        val qrdAlloc = Vec(cfg.instructionLanes, Valid(new QueenQrdEntry(cfg)))
        val flushing = Input(Bool())
        val flushStart = Input(Bool())
        val idle = Output(Bool())
    })

    private val GEMM = 0
    private val DMA = 1
    private val SCHED = 3

    private def hasLane(packet: Vec[Valid[QueenDsaCommand]], endpoint: Int): Bool =
        packet.map(lane => lane.valid && lane.bits.inst(31, 30) === endpoint.U).reduce(_ || _)

    private def selectLanes(packet: Vec[Valid[QueenDsaCommand]], endpoint: Int):
        Vec[Valid[QueenDsaCommand]] = VecInit(packet.map { lane =>
        val out = Wire(Valid(new QueenDsaCommand(cfg)))
        val hit = lane.valid && lane.bits.inst(31, 30) === endpoint.U
        out.valid := hit
        out.bits := Mux(hit, lane.bits, 0.U.asTypeOf(chiselTypeOf(lane.bits)))
        out
    })

    private val pending = RegInit(VecInit(Seq.fill(cfg.instructionLanes)(false.B)))
    private val pendingInst = Reg(Vec(cfg.instructionLanes, UInt(32.W)))
    private val batch = Wire(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))

    for (i <- 0 until cfg.instructionLanes) {
        pending(i) := io.inst(i).fire && !io.flushing
        when(io.inst(i).fire && !io.flushing) {
            pendingInst(i) := io.inst(i).bits.inst
        }
        batch(i).valid := pending(i)
        batch(i).bits.inst := Mux(pending(i), pendingInst(i), 0.U)
        val r1 = io.regRead(2 * i)
        val r2 = io.regRead(2 * i + 1)
        batch(i).bits.rs1 := Mux(pending(i) && r1.valid, r1.bits, 0.U)
        batch(i).bits.rs2 := Mux(pending(i) && r2.valid, r2.bits, 0.U)
    }

    // GEMM 与 DMA 各持一条私有 1R1W FIFO，存储可推断 SRAM；一侧停住只钉住自己的队列。
    // Queue 的 useSyncReadMem 靠前瞻读地址让 SRAM 自身的输出寄存直接充当出口，
    // 不需要额外出口寄存即维持每拍一条，故出口延迟与组合读版本相同。
    // WriteFirst 是空队列同拍写入即可读出的前提，由库内固定，不可改。
    private val fifoDepth = cfg.cmdRingDepth
    private val gemmFifo = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))),
        fifoDepth, useSyncReadMem = true, hasFlush = true))
    private val dmaFifo = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))),
        fifoDepth, useSyncReadMem = true, hasFlush = true))

    private val gemmEnqValid = hasLane(batch, GEMM) && !io.flushing
    private val dmaEnqValid = hasLane(batch, DMA) && !io.flushing
    gemmFifo.io.enq.valid := gemmEnqValid
    gemmFifo.io.enq.bits := selectLanes(batch, GEMM)
    dmaFifo.io.enq.valid := dmaEnqValid
    dmaFifo.io.enq.bits := selectLanes(batch, DMA)
    gemmFifo.io.flush.get := io.flushStart
    dmaFifo.io.flush.get := io.flushStart
    io.gemm <> gemmFifo.io.deq
    io.dma <> dmaFifo.io.deq

    // 记入本拍入队、抵扣本拍出队，使本拍释放的槽能被本拍接受的命令预约。
    private val gemmUsed = gemmFifo.io.count - io.gemm.fire.asUInt
    private val dmaUsed = dmaFifo.io.count - io.dma.fire.asUInt
    private val gemmReady = (gemmUsed +& gemmEnqValid.asUInt) < fifoDepth.U
    private val dmaReady = (dmaUsed +& dmaEnqValid.asUInt) < fifoDepth.U

    // SCHED never waits, so it bypasses the command FIFOs and keeps a single entry.
    private val schedValid = RegInit(false.B)
    private val schedBits = Reg(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
    io.scheduler.valid := schedValid
    io.scheduler.bits := schedBits
    private val schedEnq = hasLane(batch, SCHED) && !io.flushing
    when(io.scheduler.fire) { schedValid := false.B }
    when(schedEnq) {
        schedValid := true.B
        schedBits := selectLanes(batch, SCHED)
    }
    when(io.flushing) { schedValid := false.B }
    private val schedUsed = schedValid.asUInt +& schedEnq.asUInt - io.scheduler.fire.asUInt
    private val schedReady = schedUsed < 1.U

    // Capacity is reserved at accept time because the next-cycle operands cannot stall.
    // Legality is decided before accept: an illegal lane never fires, and every lane at or
    // after the first illegal one is held back so program order stays intact.
    private val gemmOpen = RegInit(false.B)
    private val dmaOpen = RegInit(false.B)

    private def trapCause(inst: UInt, gOpen: Bool, dOpen: Bool): UInt = {
        val funct3 = inst(14, 12)
        val endpoint = inst(31, 30)
        val cmd = inst(29, 26)
        val aux = inst(11, 7)
        val isCtrl = funct3 === 3.U
        val schedOnly = cmd === 3.U || cmd === 4.U || cmd === 6.U || cmd === 8.U || cmd === 10.U
        val gemmDmaOnly = cmd === 0.U || cmd === 9.U
        val noSched = cmd === 1.U || cmd === 2.U
        val auxZero = cmd === 0.U || cmd === 5.U || cmd === 6.U || cmd === 9.U || cmd === 10.U
        val auxHiZero = cmd === 2.U || cmd === 3.U
        val bit25Zero = cmd =/= 4.U
        val cause = Wire(UInt(cfg.trapCauseWidth.W))
        cause := QueenTrapCause.none.U
        when(inst(6, 0) =/= 0x0b.U) {
            cause := QueenTrapCause.opcode.U
        }.elsewhen(funct3 > 3.U) {
            cause := QueenTrapCause.funct3.U
        }.elsewhen(isCtrl && (cmd === 7.U || cmd >= 11.U)) {
            cause := QueenTrapCause.cmd.U
        }.elsewhen(endpoint === 2.U || (isCtrl && ((schedOnly && endpoint =/= SCHED.U) ||
            (gemmDmaOnly && endpoint =/= GEMM.U && endpoint =/= DMA.U) ||
            (noSched && endpoint === SCHED.U)))) {
            // endpoint=2 是 VPU，由 scalar decoder 直发，进 Queen 就没有队列可入。
            cause := QueenTrapCause.endpoint.U
        }.elsewhen((funct3 === 0.U && inst(10, 7) =/= 0.U) ||
            (funct3 === 1.U && aux =/= 0.U) ||
            (funct3 === 2.U && inst(19, 15) =/= 0.U) ||
            (isCtrl && ((auxZero && aux =/= 0.U) ||
                (auxHiZero && aux(4, 3) =/= 0.U) ||
                (cmd === 8.U && aux(4, 2) =/= 0.U)))) {
            cause := QueenTrapCause.reserved.U
        }.elsewhen(funct3 === 0.U && Cat(inst(11), inst(29, 25)) === 63.U) {
            cause := QueenTrapCause.regBase.U
        }.elsewhen(isCtrl && bit25Zero && inst(25)) {
            cause := QueenTrapCause.bit25.U
        }.elsewhen(isCtrl && cmd === 9.U && !Mux(endpoint === GEMM.U, gOpen, dOpen)) {
            cause := QueenTrapCause.emptyRun.U
        }
        cause
    }

    private val illegal = Wire(Vec(cfg.instructionLanes, UInt(cfg.trapCauseWidth.W)))
    private val blocked = Wire(Vec(cfg.instructionLanes, Bool()))
    private val ready = Wire(Vec(cfg.instructionLanes, Bool()))
    private var gOpen: Bool = gemmOpen
    private var dOpen: Bool = dmaOpen
    // QRD 槽在 accept 时分配，不是在派发时：否则排在 WAIT 后面的读会被后发的读超车。
    private var qrdCnt = 0.U(log2Ceil(cfg.instructionLanes + 1).W)
    for (i <- 0 until cfg.instructionLanes) {
        val inst = io.inst(i).bits.inst
        illegal(i) := trapCause(inst, gOpen, dOpen)
        blocked(i) := (0 to i).map(j => io.inst(j).valid && illegal(j) =/= 0.U).reduce(_ || _)
        val endpoint = inst(31, 30)
        val isQwr = inst(14, 12) === 0.U || inst(14, 12) === 1.U
        val isRun = inst(14, 12) === 3.U && inst(29, 26) === 9.U
        val isQrd = inst(14, 12) === 2.U
        val resourceReady = io.flushing ||
            (endpoint === GEMM.U) && gemmReady ||
            (endpoint === DMA.U) && dmaReady ||
            (endpoint === SCHED.U) && schedReady
        val qrdRoom = (io.qrdUsed +& qrdCnt +& isQrd.asUInt) <= cfg.qrdDepth.U
        ready(i) := resourceReady && !blocked(i) && (!isQrd || qrdRoom)
        val fire = io.inst(i).valid && ready(i)
        qrdCnt = qrdCnt + Mux(fire && isQrd, 1.U, 0.U)
        io.qrdAlloc(i).valid := fire && isQrd
        io.qrdAlloc(i).bits.pc := io.inst(i).bits.pc
        io.qrdAlloc(i).bits.rdIndex := inst(11, 7)
        io.qrdAlloc(i).bits.source := Mux(endpoint === SCHED.U, 2.U, endpoint)
        io.qrdAlloc(i).bits.dummy := io.flushing
        // openGroup folds in lane order so a QWR and a RUN in the same packet stay ordered.
        gOpen = Mux(endpoint === GEMM.U,
            Mux(fire && isQwr, true.B, Mux(fire && isRun, false.B, gOpen)), gOpen)
        dOpen = Mux(endpoint === DMA.U,
            Mux(fire && isQwr, true.B, Mux(fire && isRun, false.B, dOpen)), dOpen)
    }
    gemmOpen := Mux(io.flushing, false.B, gOpen)
    dmaOpen := Mux(io.flushing, false.B, dOpen)

    private val illegalValid = VecInit((0 until cfg.instructionLanes).map(i =>
        io.inst(i).valid && illegal(i) =/= 0.U))
    private val firstIllegal = PriorityEncoder(illegalValid)
    io.trap.valid := illegalValid.asUInt.orR
    io.trap.bits.pc := io.inst(firstIllegal).bits.pc
    io.trap.bits.inst := io.inst(firstIllegal).bits.inst
    io.trap.bits.cause := illegal(firstIllegal)

    for (i <- 0 until cfg.instructionLanes) {
        io.inst(i).ready := ready(i)
    }

    io.idle := gemmFifo.io.count === 0.U && dmaFifo.io.count === 0.U &&
        !schedValid && !pending.asUInt.orR
}
