import chisel3._
import chisel3.util._
import chisel3.dontTouch

object QueenISAC0  {
    val CUSTOM0 = 0xb
    val FUNC_2RD = 0x0
    val FUNC_1RD = 0x1
    val FUNC_1RS = 0x2
    val FUNC_TAG = 0x3

    val SCHE = 0x0
    val GEMM = 0x1
    val DMA  = 0x2
    
}

object QueenISAC1  {
    val CUSTOM1 = 0x2b
    val SCHE = 0x0
    val GEMM = 0x1
    val DMA  = 0x2
    val TAG  = 0x3
}
