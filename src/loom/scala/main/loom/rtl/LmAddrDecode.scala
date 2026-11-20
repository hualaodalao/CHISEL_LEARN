package loom.rtl

import chisel3._
import chisel3.util._

/** Window-comparison address decoder.
  *
  * The destination is the index of the slave whose `[base, base+size)` window
  * contains the address. Windows are non-overlapping (C1) and may be any
  * count; an unmapped address decodes to slave 0.
  */
object LmAddrDecode {
  def apply(addr: UInt, addrWidth: Int, base: Seq[Long], size: Seq[Long], outWidth: Int): UInt = {
    val dst = Wire(UInt(outWidth.W))
    dst := 0.U
    for (j <- base.indices) {
      val lo = base(j).U(addrWidth.W)
      val hi = (base(j) + size(j) - 1).U(addrWidth.W)
      when(addr >= lo && addr <= hi) {
        dst := j.U(outWidth.W)
      }
    }
    dst
  }
}
