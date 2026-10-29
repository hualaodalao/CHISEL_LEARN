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

    private val gemmFifo = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))), cfg.gemmQueueDepth))
    private val dmaFifo = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))), cfg.dmaQueueDepth))
    private val schedFifo = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))), cfg.schedQueueDepth))

    io.gemm <> gemmFifo.io.deq
    io.dma <> dmaFifo.io.deq
    io.scheduler <> schedFifo.io.deq

    private val pending = RegInit(VecInit(Seq.fill(cfg.instructionLanes)(false.B)))
    private val pendingInst = Reg(Vec(cfg.instructionLanes, UInt(32.W)))
    private val batch = Wire(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
    // Already accepted inst still returns rs when downstream ready is withdrawn.
    private val batchBuffer = Module(new Queue(
        Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))), 1, pipe = true, flow = true))
    private val allReady = gemmFifo.io.enq.ready && dmaFifo.io.enq.ready && schedFifo.io.enq.ready

    for (i <- 0 until cfg.instructionLanes) {
        pending(i) := io.inst(i).fire
        when(io.inst(i).fire) {
            pendingInst(i) := io.inst(i).bits
        }
        batch(i).valid := pending(i)
        batch(i).bits.inst := Mux(pending(i), pendingInst(i), 0.U)
        batch(i).bits.rs1 := Mux(pending(i) && io.rs1(i).valid, io.rs1(i).bits, 0.U)
        batch(i).bits.rs2 := Mux(pending(i) && io.rs2(i).valid, io.rs2(i).bits, 0.U)
        io.inst(i).ready := allReady
    }

    batchBuffer.io.enq.valid := batch.map(_.valid).reduce(_ || _)
    batchBuffer.io.enq.bits := batch
    batchBuffer.io.deq.ready := allReady
    assert(!batchBuffer.io.enq.valid || batchBuffer.io.enq.ready)

    for ((endpoint, fifo) <- Seq(0 -> gemmFifo, 1 -> dmaFifo, 3 -> schedFifo)) {
        fifo.io.enq.bits := batchBuffer.io.deq.bits
        for (i <- 0 until cfg.instructionLanes) {
            val entry = batchBuffer.io.deq.bits(i)
            fifo.io.enq.bits(i).valid := entry.valid && entry.bits.inst(31, 30) === endpoint.U
        }
        fifo.io.enq.valid := batchBuffer.io.deq.valid && allReady &&
            fifo.io.enq.bits.map(_.valid).reduce(_ || _)
    }
}
