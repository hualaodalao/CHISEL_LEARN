import chisel3._
import chisel3.util._
import chisel3.dontTouch

/*
S-Type No-Resp post inst
inst[31:25] : funct7 7bits   0: rs1+rs2; 1: rs1 only
inst[26:25] : dsa    2bits   00: sche; 01: gemm ; 10: dma
inst[24:20] : rs2    5bits
inst[19:15] : rs1    5bits
inst[14:12] : funct3 3bits   00: S typoe
inst[11:7]  : imm    5bits   NA
inst[6:0]   : opcode 001011 custom-0


R-Type Has-Resp nonpost inst for scheduler
inst[31:25] : funct7 7bits  0:rs1+rs2; 1:rs1 only
inst[24:20] : rs2    5bits
inst[19:15] : rs1    5bits
inst[14:12] : funct3 3bits   000: sche; 001: gemm ; 010: dma
inst[11:7]  : rd     5bits   
inst[6:0]   : opcode 001011 custom-0

U-Type Load 
inst[31:25] : imm    20bits  
inst[24:15] : rsv.   10bits
inst[14:12] : func3  3bits   000:sche; 001: gemm ; 010: dma
inst[11:7]  : rd     5bits
inst[6:0]   : opcode 001011 custom-0
*/

class QueenISAStype(val cfg: QueenConfig) extends Bundle {
    val opcode = UInt(7.W)
    val imm    = UInt(5.W)
    


    
    

}

class QueenISARtype(val cfg: QueenConfig){
}
class QueenISAUtype(val cfg: QueenConfig){
}
