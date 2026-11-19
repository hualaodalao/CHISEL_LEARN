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

/** QRD 写回：按程序序一次一条，与 qrdRetire 同拍有效。 */
class QueenAsyncRd(val cfg: QueenConfig) extends Bundle {
    val addr = UInt(5.W)
    val data = UInt(cfg.xlen.W)
}

/** QRD 退休：读数据写回（rd=x0 时 rdValid=0）或读错误转同步异常。 */
class QueenRetire(val cfg: QueenConfig) extends Bundle {
    val pc = UInt(cfg.pcWidth.W)
    val rdValid = Bool()
    val rdIndex = UInt(5.W)
    val trap = Bool()
    val trapCause = UInt(cfg.trapCauseWidth.W)
}

object QueenTrapCause {
    val none = 0
    val opcode = 1
    val funct3 = 2
    val cmd = 3
    val endpoint = 4
    val reserved = 5
    val regBase = 6
    val bit25 = 7
    val emptyRun = 8
    val rdError = 9
}
