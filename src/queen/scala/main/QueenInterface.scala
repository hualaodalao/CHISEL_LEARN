

import chisel3._
import chisel3.util._
import chisel3.dontTouch


/*

#2rs inst （写DSA寄存器，无Resp）
inst[31:30] : dsaT    2bits 00:sche 01:gemm 10:dma 11:dma
inst[29:25] : rs1Addr 5bits 
inst[24:20] : rs2     5bits #cpu only - for queen is xlen bits register val
inst[19:15] : rs1     5bits #cpu only - for queen is xlen bits register val
inst[14:14] : macrolast 1bit
inst[13:12] : funct3  3bits   00: 2rs ; 01: 1rs ;  10: 1rd;  11: 1rd
inst[11:7]  : rs2Addr 5bits   
inst[6:0]   : opcode  001011 custom-0

#1rs inst （写DSA寄存器，无Resp， 带tag）
inst[31:30] : dsaT    2bits 00:sche 01:gemm 10:dma 11:dma
inst[29:25] : rs1Addr 5bits 
inst[24:20] : tagH    5bits 
inst[19:15] : rs1     5bits #cpu only - for queen is xlen bits register val
inst[14:14] : macrolast 1bit
inst[13:12] : funct3  3bits   00: 2rs ; 01: 1rs ;  10: 1rd;  11: 1rd
inst[11:7]  : tagL    5bits   
inst[6:0]   : opcode  001011 custom-0

#1rd inst (读DSA寄存器，有Resp)
inst[31:30] : dsaT    2bits 00:sche 01:gemm 10:dma 11:dma
inst[29:25] : rdAddrH 5bits 
inst[24:20] : rdAddrL 5bits 
inst[19:15] : rdH     5bits #cpu only - for queen is xlen bits register val
inst[14:14] : macrolast 1bit # onlyf for 2rs/1rs, macrolast trigger dsa to run
inst[13:12] : funct3  3bits   00: 2rs ; 01: 1rs ;  10: 1rd;  11: 1rd
inst[11:7]  : rdL      5bits   
inst[6:0]   : opcode  001011 custom-0
*/




class QueenCpuReq(val cfg: QueenConfig) extends Bundle {
    val dsaT    = Input(UInt(2.W))
    val funct3  = Input(UInt(3.W))
    val rs1Addr = Input(UInt(5.W))
    val rs2Addr = Input(UInt(5.W))
    val rdAddr  = Input(UInt(10.W))
    val rs1   = Input(UInt(cfg.xlen.W))
    val rs2   = Input(UInt(cfg.xlen.W))
    val pc    = Input(UInt(cfg.pcWidth.W))
    val tag  = Input(UInt(10.W))
    val macrolast = Input(Bool())
}

class QueenCpuResp(val cfg: QueenConfig) extends Bundle {
    val rd   = Output(UInt(cfg.xlen.W))
}

class QueenCpuInterface(val cfg: QueenConfig) extends Bundle {
    val req = Flipped(Decoupled(new QueenCpuReq(cfg))) 
    val rsp = Decoupled(new QueenCpuResp(cfg))
}



class QueenCpuDecode(val cfg: QueenConfig){
    val cpu_req = Wire(new QueenCpuReq(cfg))

    val D_isSche = Wire(Bool()); dontTouch(D_isSche)
    val D_isGemm = Wire(Bool()); dontTouch(D_isGemm)
    val D_isDma = Wire(Bool()); dontTouch(D_isDma)
    val D_is2rs = Wire(Bool()); dontTouch(D_is2rs)
    val D_is1rs = Wire(Bool()); dontTouch(D_is1rs)
    val D_is1rd = Wire(Bool()); dontTouch(D_is1rd)     
    val D_hasTag = Wire(Bool()); dontTouch(D_hasTag)
    val D_macrolast = Wire(Bool()); dontTouch(D_macrolast)
    
    val I_dsaT    = cpu_req.dsaT
    val I_funct3  = cpu_req.funct3
    val I_rs1Addr = cpu_req.rs1Addr
    D_isSche := (I_dsaT === "b00".U)
    D_isGemm := (I_dsaT === "b01".U)
    D_isDma  := (I_dsaT === "b10".U) | (I_dsaT === "b11".U)
    D_is2rs  := (I_funct3(1,0) === "b00".U)
    D_is1rs  := (I_funct3(1,0) === "b01".U)
    D_is1rd  := (I_funct3(1,0) === "b10".U) | (I_funct3(1,0) === "b11".U)
    D_hasTag := D_is1rs
    D_macrolast := cpu_req.macrolast
}
