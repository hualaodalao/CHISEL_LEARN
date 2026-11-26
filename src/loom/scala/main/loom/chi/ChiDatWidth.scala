package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** DAT-flit width converter (HW-11/W12). Store&fwd a full 64B word: collect
  * nFlitsIn input flits (data placed at DataID*128 bit offset), then emit
  * nFlitsOut output flits slicing the word at the output width, recomputing
  * DataID. Opcode/TxnID/TgtID/SrcID/Resp are preserved from the first flit.
  * Matched widths pass through. Bit-exact, no flit dropped.
  */
class ChiDatWidth(inCfg: ChiConfig, outCfg: ChiConfig) extends Module {
  private val inW = inCfg.datWidth
  private val outW = outCfg.datWidth
  private val nIn = ChiWidths.flitCount(64, inW)
  private val nOut = ChiWidths.flitCount(64, outW)
  private val bwIn = math.max(1, log2Ceil(nIn))
  private val bwOut = math.max(1, log2Ceil(nOut))
  private val idStepOut = outW / 128

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new ChiDatFlit(inCfg)))
    val out = Decoupled(new ChiDatFlit(outCfg))
  })

  if (inW == outW) {
    io.out.valid := io.in.valid
    io.out.bits := io.in.bits.asTypeOf(new ChiDatFlit(outCfg))
    io.in.ready := io.out.ready
  } else {
    val sCollect :: sEmit :: Nil = Enum(2)
    val st = RegInit(sCollect)
    val buf = RegInit(0.U(512.W))
    val meta = Reg(new ChiDatFlit(outCfg))
    val cntIn = RegInit(0.U(bwIn.W))
    val beat = RegInit(0.U(bwOut.W))

    io.in.ready := st === sCollect
    io.out.valid := st === sEmit
    io.out.bits := meta
    io.out.bits.data := (buf >> (beat * outW.U))(outW - 1, 0)
    io.out.bits.dataId := beat * idStepOut.U
    io.out.bits.be := ((1L << outCfg.beWidth) - 1).U

    when(io.in.fire) {
      buf := buf | (io.in.bits.data.pad(512) << (io.in.bits.dataId * 128.U))
      when(cntIn === 0.U) {
        meta.qos := io.in.bits.qos
        meta.tgtId := io.in.bits.tgtId
        meta.srcId := io.in.bits.srcId
        meta.txnId := io.in.bits.txnId
        meta.opcode := io.in.bits.opcode
        meta.respErr := io.in.bits.respErr
        meta.resp := io.in.bits.resp
        meta.homeNid := io.in.bits.homeNid
      }
      when(cntIn === (nIn - 1).U) { cntIn := 0.U; beat := 0.U; st := sEmit }
        .otherwise { cntIn := cntIn + 1.U }
    }
    when(io.out.fire) {
      when(beat === (nOut - 1).U) { buf := 0.U; st := sCollect }
      beat := beat + 1.U
    }
  }
}