import chisel3._
import chisel3.util._

class QueenDsaCommand(val cfg: QueenConfig) extends Bundle {
    val inst = UInt(32.W)
    val rs1 = UInt(cfg.xlen.W)
    val rs2 = UInt(cfg.xlen.W)
}

class QueenDsaDecoder(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val inst = Vec(cfg.instructionLanes, Flipped(Decoupled(UInt(32.W))))
        val rs1 = Vec(cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val rs2 = Vec(cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val gemm = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
        val dma = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
        val scheduler = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
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
        pending(i) := io.inst(i).fire
        when(io.inst(i).fire) {
            pendingInst(i) := io.inst(i).bits
        }
        batch(i).valid := pending(i)
        batch(i).bits.inst := Mux(pending(i), pendingInst(i), 0.U)
        batch(i).bits.rs1 := Mux(pending(i) && io.rs1(i).valid, io.rs1(i).bits, 0.U)
        batch(i).bits.rs2 := Mux(pending(i) && io.rs2(i).valid, io.rs2(i).bits, 0.U)
    }

    // GEMM and DMA share one command ring so that a stalled consumer only pins the
    // slots it still owns instead of a private FIFO's head.
    private val ringDepth = cfg.cmdRingDepth
    private val ringIdxW = log2Ceil(ringDepth)
    private val ptrW = ringIdxW + 2
    private val ring = Mem(ringDepth, Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
    private val writePtr = RegInit(0.U(ptrW.W))
    private val gemmCursor = RegInit(0.U(ptrW.W))
    private val dmaCursor = RegInit(0.U(ptrW.W))

    private val gemmPresent = gemmCursor =/= writePtr
    private val dmaPresent = dmaCursor =/= writePtr
    private val gemmHead = ring(gemmCursor % ringDepth.U)
    private val dmaHead = ring(dmaCursor % ringDepth.U)
    private val gemmHas = hasLane(gemmHead, GEMM)
    private val dmaHas = hasLane(dmaHead, DMA)

    io.gemm.valid := gemmPresent && gemmHas
    io.gemm.bits := selectLanes(gemmHead, GEMM)
    io.dma.valid := dmaPresent && dmaHas
    io.dma.bits := selectLanes(dmaHead, DMA)

    private val gemmAdvance = gemmPresent && (!gemmHas || io.gemm.fire)
    private val dmaAdvance = dmaPresent && (!dmaHas || io.dma.fire)
    when(gemmAdvance) { gemmCursor := (gemmCursor + 1.U)(ptrW - 1, 0) }
    when(dmaAdvance) { dmaCursor := (dmaCursor + 1.U)(ptrW - 1, 0) }

    private val ringEnqValid = hasLane(batch, GEMM) || hasLane(batch, DMA)
    private val half = (BigInt(1) << (ptrW - 1)).U(ptrW.W)
    private val gemmNext = gemmCursor + gemmAdvance.asUInt
    private val dmaNext = dmaCursor + dmaAdvance.asUInt
    // The slot is released once every consumer has retired it.
    private val nextCommit = Mux((gemmNext - dmaNext) < half, dmaNext, gemmNext)
    // Charge this cycle's enqueue and credit this cycle's release so that a slot
    // freed by a dequeue can be reserved by a command accepted in the same cycle.
    private val ringUsed = (writePtr - nextCommit)(ptrW - 1, 0)
    private val ringHasRoom = ringUsed < ringDepth.U
    private val ringReady = (ringUsed +& ringEnqValid.asUInt) < ringDepth.U

    assert(!ringEnqValid || ringHasRoom)
    when(ringEnqValid) {
        ring(writePtr % ringDepth.U) := batch
        writePtr := (writePtr + 1.U)(ptrW - 1, 0)
    }

    // SCHED never waits, so it bypasses the ring and keeps a single entry.
    private val schedStage = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))), 1))
    io.scheduler <> schedStage.io.deq
    schedStage.io.enq.valid := hasLane(batch, SCHED)
    schedStage.io.enq.bits := selectLanes(batch, SCHED)
    private val schedUsed = schedStage.io.count +& schedStage.io.enq.valid.asUInt -
        schedStage.io.deq.fire.asUInt
    private val schedReady = schedUsed < 1.U

    // Capacity is reserved at accept time because the next-cycle operands cannot stall.
    for (i <- 0 until cfg.instructionLanes) {
        val endpoint = io.inst(i).bits(31, 30)
        io.inst(i).ready := ((endpoint === GEMM.U) || (endpoint === DMA.U)) && ringReady ||
            (endpoint === SCHED.U) && schedReady
    }
}
