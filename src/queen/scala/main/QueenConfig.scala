
import chisel3._
import chisel3.util._

case class QueenConfig(
    val pcWidth : Int = 32,
    val xlen: Int = 32,
    val instructionLanes: Int = 4,
    val nativeLanes: Int = 0,
    // GEMM/DMA 各一条私有命令 FIFO，此深度是**每端点**的槽数，不共享。
    val cmdRingDepth: Int = 16,
    val trapCauseWidth: Int = 8,
    val faultCodeWidth: Int = 8,
    val faultKindWidth: Int = 3,
    val eventGroupCount: Int = 32,
    val timelineCount: Int = 8,
    val qrdDepth: Int = 16,
    val memDepth: Int = 8,
    val flushQuietCycles: Int = 8
) {
    require(xlen == 32, s"QueenConfig: 数据通路固定 32 位，xlen($xlen) 须为 32")
    require(instructionLanes >= 1)
    require(qrdDepth >= 1)
    require(memDepth >= 1)
    require(flushQuietCycles >= 1)
    require(cmdRingDepth >= 2)
    require(trapCauseWidth >= 4)
    require(faultCodeWidth >= 1)
    // 当前定义 kind 0..4，至少要装得下 4。
    require(faultKindWidth >= 3,
        s"QueenConfig: faultKindWidth($faultKindWidth) 须 >= 3 以容纳 kind 0..4")
    require(eventGroupCount == 32, "QueenConfig: hwSignal.group 为 5-bit，事件组固定 32")
    require(timelineCount == 8, "QueenConfig: hwSignal.timeline 为 3-bit，时间线固定 8")
    require(nativeLanes >= 0)
    require(nativeWidth <= instructionLanes,
        s"QueenConfig: nativeLanes($nativeLanes) 不得超过 instructionLanes($instructionLanes)")

    /** 原生配置命令出口并行度；0 表示与上游指令 lane 数对齐。 */
    def nativeWidth: Int = if (nativeLanes == 0) instructionLanes else nativeLanes
}
