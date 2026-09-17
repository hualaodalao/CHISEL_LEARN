

import chisel3._
import chisel3.util._
import chisel3.dontTouch

class QueenCpuReq(val cfg: QueenConfig) extends Bundle {
    val isa   = Input(UInt(cfg.instWidth.W))
    val rs1   = Input(UInt(cfg.xlen.W))
    val rs2   = Input(UInt(cfg.xlen.W))
    val pc    = Input(UInt(cfg.pcWidth.W)    )
}

class QueenCpuResp(val cfg: QueenConfig) extends Bundle {
    val rd   = Output(UInt(cfg.xlen.W))
}

class QueenCpuInterface(val cfg: QueenConfig) extends Bundle {
    val req = Flipped(Decoupled(new QueenCpuReq(cfg))) 
    val rsp = Decoupled(new QueenCpuResp(cfg))
}

class QueenISAFieldDecode(val cfg: QueenConfig) {
    val cpu_req = Wire(new QueenCpuReq(cfg))

    val is_r_type = Wire(Bool());dontTouch(is_r_type)
    is_r_type := cpu_req.isa(6) & cpu_req.isa(5) & ~cpu_req.isa(7)

}
