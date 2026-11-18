
import chisel3._
import chisel3.util._

case class QueenConfig(
    val slotDepth : Int = 8,
    val pcWidth : Int = 32,
    val instWidth : Int = 32,
    val xlen: Int = 32,
    val instructionLanes: Int = 4,
    val nativeLanes: Int = 0,
    val cmdRingDepth: Int = 64,
    val trapCauseWidth: Int = 8
) {
    require(instructionLanes >= 1)
    require(cmdRingDepth >= 2)
    require(trapCauseWidth >= 4)
    require(nativeLanes >= 0)
    require(nativeWidth <= instructionLanes,
        s"QueenConfig: nativeLanes($nativeLanes) 不得超过 instructionLanes($instructionLanes)")

    /** 原生配置命令出口并行度；0 表示与上游指令 lane 数对齐。 */
    def nativeWidth: Int = if (nativeLanes == 0) instructionLanes else nativeLanes
}
