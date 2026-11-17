import chisel3._
import chisel3.util._

class QueenIssue(val cfg: QueenConfig) extends Bundle {
    val pc = UInt(cfg.pcWidth.W)
    val inst = UInt(32.W)
}

class QueenTrap(val cfg: QueenConfig) extends Bundle {
    val pc = UInt(cfg.pcWidth.W)
    val inst = UInt(32.W)
    val cause = UInt(cfg.trapCauseWidth.W)
}

object QueenTrapCause {
    val none = 0
    val opcode = 1
    val funct3 = 2
    val cmd = 3
    val endpoint = 4
    val reserved = 5
    val regBase = 6
    val notifyBit = 7
    val emptyRun = 8
}

class QueenCpuLaneInterface(val cfg: QueenConfig) extends Bundle {
    val inst = Flipped(Decoupled(UInt(32.W)))
    val rs1 = Flipped(Valid(UInt(cfg.xlen.W)))
    val rs2 = Flipped(Valid(UInt(cfg.xlen.W)))
    val rd = Decoupled(UInt(cfg.xlen.W))
}

class QueenCpuMultiLaneInterface(val cfg: QueenConfig) extends Bundle {
    val lanes = Vec(cfg.instructionLanes, new QueenCpuLaneInterface(cfg))
}
