package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** A data beat with an end-of-transaction marker (NIU adaptation unit). */
class ChiNiBeat(val w: Int) extends Bundle {
  val data = UInt(w.W)
  val last = Bool()
}

/** NIU store&fwd width adapter (HW-12/SEM-19). Converts a beat stream between
  * nodeWidth and fabric datWidth for any power-of-two ratio via a shift buffer:
  * downsize splits one wide beat into inW/outW narrow beats (low first); upsize
  * packs outW/inW narrow beats into one wide beat (low first). `last` rides the
  * final output beat of the transaction. Bit-exact, no beat dropped. Matched
  * widths (inW==outW) pass through with zero overhead.
  */
class ChiNiWidth(inW: Int, outW: Int) extends Module {
  require(inW >= 8 && outW >= 8, "widths must be >= 8")
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new ChiNiBeat(inW)))
    val out = Decoupled(new ChiNiBeat(outW))
  })

  if (inW == outW) {
    LmWire.link(io.in, io.out)
  } else if (inW > outW) {
    require(inW % outW == 0, "downsize ratio must be integer")
    val ratio = inW / outW
    val cntW = math.max(1, log2Ceil(ratio))
    val buf = Reg(UInt(inW.W))
    val cnt = RegInit(0.U(cntW.W))
    val lastR = Reg(Bool())
    val busy = RegInit(false.B)
    io.out.valid := busy
    io.out.bits.data := (buf >> (cnt * outW.U))(outW - 1, 0)
    io.out.bits.last := lastR && cnt === (ratio - 1).U
    io.in.ready := !busy
    when(io.in.fire) { buf := io.in.bits.data; lastR := io.in.bits.last; cnt := 0.U; busy := true.B }
    when(io.out.fire) {
      when(cnt === (ratio - 1).U) { busy := false.B }
      cnt := cnt + 1.U
    }
  } else {
    require(outW % inW == 0, "upsize ratio must be integer")
    val ratio = outW / inW
    val cntW = math.max(1, log2Ceil(ratio))
    val buf = RegInit(0.U(outW.W))
    val cnt = RegInit(0.U(cntW.W))
    io.out.valid := cnt === (ratio - 1).U && io.in.valid
    io.out.bits.data := (buf | (io.in.bits.data.pad(outW) << (cnt * inW.U)))(outW - 1, 0)
    io.out.bits.last := io.in.bits.last
    io.in.ready := io.out.ready || cnt =/= (ratio - 1).U
    when(io.in.fire) {
      when(cnt === (ratio - 1).U) { buf := 0.U; cnt := 0.U }
      .otherwise {
        buf := (buf | (io.in.bits.data.pad(outW) << (cnt * inW.U)))(outW - 1, 0)
        cnt := cnt + 1.U
      }
    }
  }
}

/** NIU rate adaptation wrapper (HW-12): width store&fwd (ChiNiWidth) followed
  * by an optional clock-domain crossing FIFO (depth fifoDepth) for frequency
  * mismatch. mismatch=false (matched width and clock) is a zero-overhead pass
  * through. GEN instantiates this at a mismatched node's DAT boundary; AXI
  * boundary width adaptation stays with the bridge layer (PS §5 F8).
  */
class ChiNiAdapt(inW: Int, outW: Int, fifoDepth: Int, async: Boolean) extends Module {
  require(fifoDepth >= 2 && (fifoDepth & (fifoDepth - 1)) == 0, "fifoDepth must be power of two >= 2")
  val io = IO(new Bundle {
    val dstClk = Input(Bool())
    val dstReset = Input(Bool())
    val in = Flipped(Decoupled(new ChiNiBeat(inW)))
    val out = Decoupled(new ChiNiBeat(outW))
  })
  private val width = Module(new ChiNiWidth(inW, outW))
  LmWire.link(io.in, width.io.in)
  if (async) {
    val fifo = Module(new ChiAsyncLink(new ChiNiBeat(outW), fifoDepth))
    fifo.io.dstClk := io.dstClk; fifo.io.dstReset := io.dstReset
    LmWire.link(width.io.out, fifo.io.in)
    LmWire.link(fifo.io.out, io.out)
  } else {
    LmWire.link(width.io.out, io.out)
  }
}