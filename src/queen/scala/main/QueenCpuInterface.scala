import chisel3._
import chisel3.util._


class QueenCpuLaneInterface(val cfg: QueenConfig) extends Bundle {
    val inst = Flipped(Decoupled(UInt(32.W)))
    val rs1 = Flipped(Valid(UInt(cfg.xlen.W)))
    val rs2 = Flipped(Valid(UInt(cfg.xlen.W)))
    val rd = Decoupled(UInt(cfg.xlen.W))
}

class QueenCpuMultiLaneInterface(val cfg: QueenConfig) extends Bundle {
    val lanes = Vec(cfg.instructionLanes, new QueenCpuLaneInterface(cfg))
}
