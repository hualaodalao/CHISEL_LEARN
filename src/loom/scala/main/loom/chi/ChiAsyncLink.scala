package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmAsyncLink

/** Per-channel CHI flit CDC link (HW-04). Wraps the proven v1 LmAsyncLink
  * (gray-coded async FIFO + conservative cross-domain credit pool) for a CHI
  * flit type. The source domain pushes flits; the destination domain (dstClk)
  * pops them. Credit returns are delayed by CDC sync, so the source can never
  * over-issue: in-flight flits counted at the source stay <= depth. One instance
  * per channel class (REQ/RSP/SNP/DAT) on an async link.
  */
class ChiAsyncLink[T <: Data](gen: T, depth: Int) extends Module {
  require(depth >= 2 && (depth & (depth - 1)) == 0, "depth must be a power of two >= 2")
  private val w = gen.getWidth

  val io = IO(new Bundle {
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
    val in = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
    val creditUsed = Output(UInt(16.W))
  })

  private val link = Module(new LmAsyncLink(w, depth))
  link.io.dstClk := io.dstClk
  link.io.dstReset := io.dstReset
  link.io.in.valid := io.in.valid
  link.io.in.bits := io.in.bits.asUInt
  io.in.ready := link.io.in.ready
  io.out.valid := link.io.out.valid
  io.out.bits := link.io.out.bits.asTypeOf(gen)
  link.io.out.ready := io.out.ready
  io.creditUsed := link.io.creditUsed
}