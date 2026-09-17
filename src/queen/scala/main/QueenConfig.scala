
import chisel3._
import chisel3.util._

case class QueenConfig(
    val slotDepth : Int = 8,
    val pcWidth : Int = 32,
    val instWidth : Int = 32,
    val xlen: Int = 32
) {

}
