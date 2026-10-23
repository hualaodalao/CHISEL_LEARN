import chisel3._
import chisel3.util._

class FbBytePacket(dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val bytes = UInt(log2Ceil(dataWidth / 8 + 1).W)
  val last = Bool()
}

object FbFloatConvert {
  private def fp16ToFp32(x: UInt): UInt = {
    val sign = x(15)
    val exp = x(14, 10)
    val man = x(9, 0)
    val newExp = Mux(exp === 0.U, 0.U(8.W),
                 Mux(exp === 31.U, 255.U(8.W),
                 (exp +& 112.U)(7, 0)))
    val newMan = Cat(man, 0.U(13.W))
    Mux(exp === 0.U && man === 0.U, Cat(sign, 0.U(31.W)),
        Cat(sign, newExp, newMan))
  }

  private def bf16ToFp32(x: UInt): UInt = Cat(x, 0.U(16.W))

  private def fp32ToFp16(x: UInt): UInt = {
    val sign = x(31)
    val exp = x(30, 23)
    val man = x(22, 0)
    val roundBit = man(12)
    val sticky = man(11, 0).orR
    val manTop = man(22, 13)
    val roundUp = roundBit && (sticky || manTop(0))
    val manRnd = manTop +& roundUp
    val expCarry = manRnd(10)
    val manFinal = Mux(expCarry, 0.U(10.W), manRnd(9, 0))
    val newExp = (exp +& expCarry) - 112.U
    val overflow = exp > 142.U || (exp === 142.U && expCarry)
    val underflow = exp < 113.U
    val isZero = exp === 0.U
    val isInfNan = exp === 255.U
    Mux(isZero || underflow, Cat(sign, 0.U(15.W)),
    Mux(isInfNan && man === 0.U, Cat(sign, "b11111".U(5.W), 0.U(10.W)),
    Mux(isInfNan, Cat(sign, "b11111".U(5.W), 1.U(1.W), man(21, 13)),
    Mux(overflow, Cat(sign, "b11111".U(5.W), 0.U(10.W)),
        Cat(sign, newExp(4, 0), manFinal)))))
  }

  private def fp32ToBf16(x: UInt): UInt = {
    val roundBit = x(15)
    val sticky = x(14, 0).orR
    val lsb = x(16)
    val roundUp = roundBit && (sticky || lsb)
    val upper = x(31, 16)
    (upper +& roundUp)(15, 0)
  }

  def apply(x: UInt, src: FbDataFmt.Type, dst: FbDataFmt.Type): UInt = {
    val raw = x.pad(32)(31, 0)
    val fp32 = MuxLookup(src, raw)(Seq(
      FbDataFmt.FP32 -> raw,
      FbDataFmt.FP16 -> fp16ToFp32(raw(15, 0)),
      FbDataFmt.BF16 -> bf16ToFp32(raw(15, 0))
    ))
    val converted = MuxLookup(dst, fp32)(Seq(
      FbDataFmt.FP32 -> fp32,
      FbDataFmt.FP16 -> fp32ToFp16(fp32).pad(32),
      FbDataFmt.BF16 -> fp32ToBf16(fp32).pad(32)
    ))
    Mux(src === dst || !FbDataFmt.isFloat(src) || !FbDataFmt.isFloat(dst), raw, converted)
  }
}

class FbFormatConverter(dataWidth: Int, pipeStages: Int = 1) extends Module {
  require(dataWidth >= 32 && dataWidth % 32 == 0, s"dataWidth($dataWidth) must be >= 32 and multiple of 32")

  private val beatBytes = dataWidth / 8
  private val bytesWidth = log2Ceil(beatBytes + 1)
  private val maxElems = dataWidth / 16
  private val maxExpandBits = maxElems * 32

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(UInt(dataWidth.W)))
    val inBytes = Input(UInt(bytesWidth.W))
    val inLast = Input(Bool())
    val out = Decoupled(UInt(dataWidth.W))
    val outBytes = Output(UInt(bytesWidth.W))
    val outLast = Output(Bool())
    val cfg_srcFmt = Input(FbDataFmt())
    val cfg_dstFmt = Input(FbDataFmt())
    val cfg_enable = Input(Bool())
    val flush = Input(Bool())
  })

  private val effectiveBypass = !io.cfg_enable || io.cfg_srcFmt === io.cfg_dstFmt ||
    !FbDataFmt.isFloat(io.cfg_srcFmt) || !FbDataFmt.isFloat(io.cfg_dstFmt)

  val srcElemBits = FbDataFmt.elemBits(io.cfg_srcFmt)
  val dstElemBits = FbDataFmt.elemBits(io.cfg_dstFmt)
  val isCompression = srcElemBits > dstElemBits
  val isExpansion = srcElemBits < dstElemBits
  val srcElems = Mux(srcElemBits === 32.U, io.inBytes >> 2, io.inBytes >> 1)

  val dstVec = Wire(Vec(maxElems, UInt(32.W)))
  for (i <- 0 until maxElems) {
    val raw32 = if (i < dataWidth / 32) io.in.bits((i + 1) * 32 - 1, i * 32) else 0.U(32.W)
    val raw16 = io.in.bits((i + 1) * 16 - 1, i * 16)
    val raw = Mux(srcElemBits === 32.U, raw32, raw16.pad(32))
    dstVec(i) := Mux(i.U < srcElems, FbFloatConvert(raw, io.cfg_srcFmt, io.cfg_dstFmt), 0.U(32.W))
  }

  val packedAt32 = Cat((0 until maxElems).reverse.map(i => dstVec(i)))
  val packedAt16 = Cat((0 until maxElems).reverse.map(i => dstVec(i)(15, 0)))
  val convertedWide = Mux(dstElemBits === 32.U, packedAt32, packedAt16.pad(maxExpandBits))
  val convertedBytes = Wire(UInt(log2Ceil(2 * beatBytes + 1).W))
  convertedBytes := Mux(isCompression, io.inBytes >> 1,
                    Mux(isExpansion, io.inBytes << 1, io.inBytes))

  val bufferedData = RegInit(0.U(maxExpandBits.W))
  val bufferedBytes = RegInit(0.U(log2Ceil(2 * beatBytes + 1).W))
  val bufferedLast = RegInit(false.B)

  when(effectiveBypass) {
    io.out.valid := io.in.valid && !io.flush
    io.out.bits := io.in.bits
    io.outBytes := io.inBytes
    io.outLast := io.inLast
    io.in.ready := io.out.ready && !io.flush
  } .elsewhen(isCompression || isExpansion) {
    io.out.valid := !io.flush && (bufferedBytes >= beatBytes.U || bufferedLast ||
      (isExpansion && bufferedBytes =/= 0.U))
    io.out.bits := bufferedData(dataWidth - 1, 0)
    io.outBytes := Mux(bufferedBytes >= beatBytes.U, beatBytes.U, bufferedBytes)
    io.outLast := bufferedLast && bufferedBytes <= beatBytes.U
    io.in.ready := !io.flush && !bufferedLast &&
      Mux(isExpansion, bufferedBytes === 0.U, bufferedBytes < beatBytes.U)

    when(io.in.fire) {
      bufferedData := bufferedData | (convertedWide << (bufferedBytes << 3))(maxExpandBits - 1, 0)
      bufferedBytes := bufferedBytes + convertedBytes
      bufferedLast := io.inLast
    }
    when(io.out.fire) {
      when(bufferedBytes <= beatBytes.U) {
        bufferedData := 0.U
        bufferedBytes := 0.U
        bufferedLast := false.B
      } .otherwise {
        bufferedData := bufferedData >> dataWidth
        bufferedBytes := bufferedBytes - beatBytes.U
      }
    }
  } .otherwise {
    io.out.valid := io.in.valid && !io.flush
    io.out.bits := convertedWide(dataWidth - 1, 0)
    io.outBytes := io.inBytes
    io.outLast := io.inLast
    io.in.ready := io.out.ready && !io.flush
  }

  when(io.flush) {
    bufferedData := 0.U
    bufferedBytes := 0.U
    bufferedLast := false.B
  }
}
