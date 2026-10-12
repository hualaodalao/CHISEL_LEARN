

import chisel3._
import chisel3.util._
import chisel3.experimental.prefix


class Queen(val cfg: QueenConfig) extends Module {
    val io = IO(new QueenCpuInterface(cfg))

    io.req.ready := true.B
    io.rsp.valid := RegNext(io.req.valid)
    io.rsp.bits.rd := RegNext(io.req.bits.rs1)


    val decoder1 = prefix("decoder1") (new QueenCpuDecode(cfg))
    decoder1.cpu_req := io.req.bits
    val decoder2 = prefix("decoder2") (new QueenCpuDecode(cfg))
    decoder2.cpu_req := io.req.bits

}

