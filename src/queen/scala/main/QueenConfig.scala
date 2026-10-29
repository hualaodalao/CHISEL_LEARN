
import chisel3._
import chisel3.util._

case class QueenConfig(
    val slotDepth : Int = 8,
    val pcWidth : Int = 32,
    val instWidth : Int = 32,
    val xlen: Int = 32,
    val instructionLanes: Int = 4,
    val gemmQueueDepth: Int = 8,
    val dmaQueueDepth: Int = 8,
    val schedQueueDepth: Int = 8
) {
    require(instructionLanes >= 1)
    require(gemmQueueDepth >= 1)
    require(dmaQueueDepth >= 1)
    require(schedQueueDepth >= 1)
}
