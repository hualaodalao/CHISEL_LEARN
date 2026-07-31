import chisel3._
import chisel3.util._


case class DecompressedInstruction() extends Bundle{
    val inst = UInt(32.W)
    val illegal = Bool()
}

/**
 * Implement a RISC-V RVC to regular instruction hardware decompressor
 */
object RvcDecompressor{

    
    def apply(i: UInt, rvf : Boolean, rvd : Boolean, xlen : Int): DecompressedInstruction = {
        val ret = Wire(DecompressedInstruction())
        ret.inst := 0.U
        ret.illegal := false.B
        ret
    }
}