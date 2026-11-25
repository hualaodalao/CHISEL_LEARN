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

/** QRD 写回：按程序序一次一条。Queen 不设标量退休端口——QWR/QCTRL 在 inst.fire 即退休，
  * QRD 的结果只由本通路表达；rd=x0 与读错误不写 GPR，出队是 Queen 内部动作。 */
class QueenAsyncRd(val cfg: QueenConfig) extends Bundle {
    val addr = UInt(5.W)
    val data = UInt(cfg.xlen.W)
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
}
