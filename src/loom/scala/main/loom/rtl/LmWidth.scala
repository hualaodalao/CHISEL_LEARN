package loom.rtl

import chisel3._
import chisel3.util._

/** A single data beat with an end-of-transaction marker. */
class LmBeat(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val last = Bool()
}

/** 2:1 or 1:2 beat-width adapter.
  *
  * The input beat count of a transaction must be even when upsizing; the
  * adapter asserts on an odd beat count instead of silently dropping `last`.
  */
class LmWidthAdapter(inW: Int, outW: Int) extends Module {
  require(inW != outW, "widths must differ")
  require(inW == 2 * outW || outW == 2 * inW, "only 2:1 and 1:2 adaptation is supported")

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new LmBeat(inW)))
    val out = Decoupled(new LmBeat(outW))
  })

  if (inW > outW) {
    // Downsize: one wide beat becomes low half then high half.
    val phase = RegInit(false.B)
    val hold = Reg(UInt(outW.W))
    val holdLast = Reg(Bool())

    io.out.valid := io.in.valid || phase
    io.out.bits.data := Mux(phase, hold, io.in.bits.data(outW - 1, 0))
    io.out.bits.last := Mux(phase, holdLast, false.B)
    io.in.ready := io.out.ready && !phase

    when(io.in.ready && io.in.valid) {
      hold := io.in.bits.data(inW - 1, outW)
      holdLast := io.in.bits.last
      phase := true.B
    }
    when(phase && io.out.ready) { phase := false.B }
  } else {
    // Upsize: two narrow beats become one wide beat.
    val have = RegInit(false.B)
    val hold = Reg(UInt(inW.W))

    io.out.valid := have && io.in.valid
    io.out.bits.data := Cat(io.in.bits.data, hold)
    io.out.bits.last := io.in.bits.last
    io.in.ready := Mux(have, io.out.ready, true.B)

    when(io.in.fire) {
      when(!have) {
        assert(!io.in.bits.last, "LM-P4: upsize sees an odd beat count")
        hold := io.in.bits.data
        have := true.B
      }.otherwise {
        have := false.B
      }
    }
  }
}
