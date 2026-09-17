

import chisel3._
import chisel3.util._

class Queen(val cfg: QueenConfig) extends Module {
    val io = IO(new QueenCpuInterface(cfg))

    io.req.ready := true.B
    io.rsp.valid := RegNext(io.req.valid)
    io.rsp.bits.rd := RegNext(io.req.bits.rs1)


    val decoder = new QueenISAFieldDecode(cfg)
    decoder.cpu_req := io.req.bits
    val decoder2 = new QueenISAFieldDecode(cfg)
    decoder2.cpu_req := io.req.bits

}

