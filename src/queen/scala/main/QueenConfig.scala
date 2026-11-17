
import chisel3._
import chisel3.util._

case class QueenConfig(
    val slotDepth : Int = 8,
    val pcWidth : Int = 32,
    val instWidth : Int = 32,
    val xlen: Int = 32,
    val instructionLanes: Int = 4,
    val cmdRingDepth: Int = 64,
    val trapCauseWidth: Int = 8,
    val cfgCreditWidth: Int = 4
) {
    require(instructionLanes >= 1)
    require(cmdRingDepth >= 2)
    require(trapCauseWidth >= 4)
    require(cfgCreditWidth >= 1)
}
