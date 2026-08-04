/** ForagerBee 格式转换模块。
  *
  * 在 dataQueue.deq 与 bus.wr 之间执行原位数据格式转换。
  * 支持 FP32/FP16/BF16/INT8 互转，带 Rate Adapter 处理压缩/扩展。
  *
  * V1 简化策略：
  *   - FP32↔FP16、FP32↔BF16：纯位操作（截断/扩展 + round-to-nearest-even）
  *   - FP16↔BF16：经 FP32 中转，纯位操作
  *   - FP32↔INT8：shift-based 量化/反量化（scale 低 5 位作 shift 量）
  *   - INT8↔FP16/BF16：经 FP32 中转
  */

import chisel3._
import chisel3.util._

class FbFormatConverter(dataWidth: Int, pipeStages: Int = 1) extends Module {
  require(dataWidth >= 32 && dataWidth % 32 == 0, s"dataWidth($dataWidth) must be >= 32 and multiple of 32")

  val io = IO(new Bundle {
    val in        = Flipped(Decoupled(UInt(dataWidth.W)))
    val out       = Decoupled(UInt(dataWidth.W))
    val cfg_srcFmt    = Input(UInt(3.W))
    val cfg_dstFmt    = Input(UInt(3.W))
    val cfg_scale     = Input(UInt(32.W))
    val cfg_zeroPoint = Input(UInt(32.W))
    val cfg_enable    = Input(Bool())
    val flush         = Input(Bool())
  })

  // --- Format constants (matching FbDataFmt) ---
  private val FMT_FP32 = 0.U(3.W)
  private val FMT_FP16 = 1.U(3.W)
  private val FMT_BF16 = 2.U(3.W)
  private val FMT_INT8 = 3.U(3.W)

  // --- Derived constants ---
  private val maxElems = dataWidth / 8  // max elements per beat (INT8 case)
  private val halfW    = dataWidth / 2
  private val quarterW = dataWidth / 4

  // --- Element bit width from format ---
  private def elemBitsOf(fmt: UInt): UInt = MuxLookup(fmt, 32.U(6.W))(Seq(
    FMT_FP32 -> 32.U, FMT_FP16 -> 16.U, FMT_BF16 -> 16.U, FMT_INT8 -> 8.U
  ))

  // --- Effective bypass: disabled OR same-format ---
  private val effectiveBypass = !io.cfg_enable || (io.cfg_srcFmt === io.cfg_dstFmt)

  // ==========================================================================
  // Conversion helper functions (pure combinational, bit-level)
  // ==========================================================================

  /** FP16 (16 bits) → FP32 (32 bits) */
  private def fp16ToFp32(x: UInt): UInt = {
    val sign = x(15)
    val exp  = x(14, 10) // 5 bits, bias=15
    val man  = x(9, 0)   // 10 bits
    val newExp = Mux(exp === 0.U, 0.U(8.W),
                 Mux(exp === 31.U, 255.U(8.W),
                 (exp +& 112.U)(7, 0))) // rebias: +127-15=+112
    val newMan = Cat(man, 0.U(13.W))
    Mux(exp === 0.U && man === 0.U, Cat(sign, 0.U(31.W)),
        Cat(sign, newExp, newMan))
  }

  /** BF16 (16 bits) → FP32 (32 bits): simply pad 16 zeros */
  private def bf16ToFp32(x: UInt): UInt = Cat(x, 0.U(16.W))

  /** FP32 (32 bits) → FP16 (16 bits) with round-to-nearest-even */
  private def fp32ToFp16(x: UInt): UInt = {
    val sign = x(31)
    val exp  = x(30, 23)
    val man  = x(22, 0)
    // Round: keep top 10 mantissa bits
    val roundBit = man(12)
    val sticky   = man(11, 0).orR
    val manTop   = man(22, 13)
    val roundUp  = roundBit && (sticky || manTop(0))
    val manRnd   = manTop +& roundUp // 11 bits
    val expCarry = manRnd(10)
    val manFinal = Mux(expCarry, 0.U(10.W), manRnd(9, 0))
    val newExp   = (exp +& expCarry) - 112.U // rebias 127→15
    // Overflow/underflow
    val overflow  = exp > 142.U || (exp === 142.U && expCarry)
    val underflow = exp < 113.U
    val isZero    = exp === 0.U
    val isInfNan  = exp === 255.U
    Mux(isZero || underflow, Cat(sign, 0.U(15.W)),
    Mux(isInfNan && man === 0.U, Cat(sign, "b11111".U(5.W), 0.U(10.W)),
    Mux(isInfNan, Cat(sign, "b11111".U(5.W), 1.U(1.W), man(21, 13)), // NaN preserve payload
    Mux(overflow, Cat(sign, "b11111".U(5.W), 0.U(10.W)),
        Cat(sign, newExp(4, 0), manFinal)))))
  }

  /** FP32 (32 bits) → BF16 (16 bits) with rounding */
  private def fp32ToBf16(x: UInt): UInt = {
    val roundBit = x(15)
    val sticky   = x(14, 0).orR
    val lsb      = x(16)
    val roundUp  = roundBit && (sticky || lsb)
    val upper    = x(31, 16)
    // Handle overflow: if upper == 0x7F7F and roundUp → becomes inf (0x7F80)
    (upper +& roundUp)(15, 0)
  }

  /** Signed integer (up to 32 bits) → FP32. Input is signed 2's complement. */
  private def intToFp32(x: UInt, width: Int): UInt = {
    val sign   = x(width - 1)
    val absVal = Mux(sign, (~x(width - 1, 0) + 1.U)(width - 1, 0), x(width - 1, 0))
    val isZero = absVal === 0.U
    // Log2 gives position of highest set bit (0-indexed from LSB)
    val leadPos = Log2(absVal)
    val exp     = Mux(isZero, 0.U(8.W), (127.U +& leadPos)(7, 0))
    // Mantissa: remove implicit 1, left-align to 23 bits
    // Shift absVal left by (23 - leadPos) to align leading 1 at bit 23, then take bits [22:0]
    val shiftL  = 23.U - leadPos
    val shiftR  = leadPos - 23.U
    val aligned = Mux(leadPos <= 23.U,
                      (absVal << shiftL)(31, 0),
                      (absVal >> shiftR)(31, 0))
    val man     = aligned(22, 0)
    Mux(isZero, 0.U(32.W), Cat(sign, exp, man))
  }

  /** FP32 → INT8 (shift-based quantization).
    * q = round(value / 2^shift) + zeroPoint, clamped to [-128, 127].
    * scale[4:0] = shift amount, zeroPoint[7:0] = integer ZP. */
  private def fp32ToInt8(x: UInt, scale: UInt, zp: UInt): UInt = {
    val shiftAmt = scale(4, 0)
    val zpInt    = zp(7, 0).asSInt.pad(16)
    val sign     = x(31)
    val exp      = x(30, 23)
    val man      = Cat(1.U(1.W), x(22, 0)) // 24 bits with implicit 1
    val isZero   = exp === 0.U
    // Effective exponent after scale: adjusted_exp = exp - 127 - shift
    // Integer part = man >> (23 - (exp - 127 - shift)) = man >> (150 + shift - exp)
    val totalShift = 150.U +& shiftAmt - exp // right shift for mantissa→integer
    val absInt   = Mux(isZero || exp < (127.U - 23.U), 0.U(24.W),
                   Mux(totalShift >= 24.U, 0.U(24.W),
                       (man >> totalShift)(23, 0)))
    // Apply sign → signed value
    val signedVal = Mux(sign, (~absInt +& 1.U)(15, 0).asSInt, absInt(15, 0).asSInt)
    // Add zero point
    val withZp = signedVal +& zpInt
    // Clamp to [-128, 127]
    val clamped = Mux(withZp > 127.S, 127.S(9.W), Mux(withZp < (-128).S, (-128).S(9.W), withZp))
    clamped(7, 0).asUInt
  }

  /** INT8 → FP32 (shift-based dequantization).
    * value = (int8 - zeroPoint) << shift, then convert to FP32.
    * scale[4:0] = shift amount, zeroPoint[7:0] = integer ZP. */
  private def int8ToFp32(x: UInt, scale: UInt, zp: UInt): UInt = {
    val shiftAmt = scale(4, 0)
    val xSigned  = x(7, 0).asSInt
    val zpSigned = zp(7, 0).asSInt
    val diff     = (xSigned -& zpSigned).pad(16) // 16-bit signed
    val shifted  = (diff.asUInt << shiftAmt)(31, 0) // left shift, treat as 32-bit signed
    intToFp32(shifted, 32)
  }

  // ==========================================================================
  // Main conversion logic: unpack → convert via FP32 intermediate → pack
  // ==========================================================================

  // Determine elements per beat for source format
  val srcElemBits = elemBitsOf(io.cfg_srcFmt)
  val dstElemBits = elemBitsOf(io.cfg_dstFmt)

  // Compute conversion ratio (only 1, 2, 4 possible)
  val isCompression = srcElemBits > dstElemBits
  val isExpansion   = srcElemBits < dstElemBits
  val compRatio = Mux(srcElemBits === 32.U && dstElemBits === 8.U, 4.U(3.W),
                  Mux(srcElemBits === 32.U && dstElemBits === 16.U, 2.U(3.W),
                  Mux(srcElemBits === 16.U && dstElemBits === 8.U, 2.U(3.W),
                  1.U(3.W))))
  val expRatio  = Mux(dstElemBits === 32.U && srcElemBits === 8.U, 4.U(3.W),
                  Mux(dstElemBits === 32.U && srcElemBits === 16.U, 2.U(3.W),
                  Mux(dstElemBits === 16.U && srcElemBits === 8.U, 2.U(3.W),
                  1.U(3.W))))

  // --- Step 1: Extract source elements and convert each to FP32 intermediate ---
  val fp32Vec = Wire(Vec(maxElems, UInt(32.W)))
  for (i <- 0 until maxElems) {
    val raw32 = if (i < dataWidth / 32) io.in.bits((i + 1) * 32 - 1, i * 32) else 0.U(32.W)
    val raw16 = if (i < dataWidth / 16) io.in.bits((i + 1) * 16 - 1, i * 16) else 0.U(16.W)
    val raw8  = io.in.bits((i + 1) * 8 - 1, i * 8)

    fp32Vec(i) := MuxLookup(io.cfg_srcFmt, raw32)(Seq(
      FMT_FP32 -> raw32,
      FMT_FP16 -> fp16ToFp32(raw16),
      FMT_BF16 -> bf16ToFp32(raw16),
      FMT_INT8 -> int8ToFp32(raw8, io.cfg_scale, io.cfg_zeroPoint)
    ))
  }

  // --- Step 2: Convert each FP32 intermediate to destination format ---
  val dstVec = Wire(Vec(maxElems, UInt(32.W)))
  for (i <- 0 until maxElems) {
    dstVec(i) := MuxLookup(io.cfg_dstFmt, fp32Vec(i))(Seq(
      FMT_FP32 -> fp32Vec(i),
      FMT_FP16 -> fp32ToFp16(fp32Vec(i)).pad(32),
      FMT_BF16 -> fp32ToBf16(fp32Vec(i)).pad(32),
      FMT_INT8 -> fp32ToInt8(fp32Vec(i), io.cfg_scale, io.cfg_zeroPoint).pad(32)
    ))
  }

  // --- Step 3: Pack converted elements at destination element width ---
  // Pack at various granularities (all compile-time widths, mux at runtime)
  val packedAt32 = Cat((0 until maxElems).reverse.map(i => dstVec(i)(31, 0)))     // maxElems*32 bits
  val packedAt16 = Cat((0 until maxElems).reverse.map(i => dstVec(i)(15, 0)))     // maxElems*16 bits
  val packedAt8  = Cat((0 until maxElems).reverse.map(i => dstVec(i)(7, 0)))      // maxElems*8 bits

  // Select packed data based on dst format
  // Width of valid data per input beat = srcElemsPerBeat * dstElemBits
  // For expansion: valid > dataWidth; for compression: valid < dataWidth
  private val maxExpandBits = maxElems * 32 // 512 for dataWidth=128
  val convertedWide = Wire(UInt(maxExpandBits.W))
  convertedWide := MuxLookup(io.cfg_dstFmt, packedAt32)(Seq(
    FMT_FP32 -> packedAt32,
    FMT_FP16 -> packedAt16.pad(maxExpandBits),
    FMT_BF16 -> packedAt16.pad(maxExpandBits),
    FMT_INT8 -> packedAt8.pad(maxExpandBits)
  ))

  // ==========================================================================
  // Rate Adapter: accumulate (compression) / expand (expansion) / passthrough
  // ==========================================================================

  val accumReg   = RegInit(0.U(dataWidth.W))
  val accumCount = RegInit(0.U(2.W))

  val expandReg   = RegInit(0.U(maxExpandBits.W))
  val expandCount = RegInit(0.U(2.W))
  val expanding   = RegInit(false.B)

  // Flush resets all rate adapter state
  when(io.flush) {
    accumCount := 0.U
    expandCount := 0.U
    expanding := false.B
  }

  // --- Output logic ---
  when(effectiveBypass) {
    // Bypass: direct connection
    io.out.valid := io.in.valid
    io.out.bits  := io.in.bits
    io.in.ready  := io.out.ready
  } .elsewhen(isCompression) {
    // Compression: accumulate multiple input beats → 1 output beat
    val isLast = accumCount === (compRatio - 1.U)

    // Output valid only when we have all beats accumulated (last beat arrives)
    io.out.valid := io.in.valid && isLast
    io.in.ready  := Mux(isLast, io.out.ready, true.B)

    // Build output beat from accumulated data + current conversion
    val chunkBits = Wire(UInt(dataWidth.W))
    chunkBits := MuxLookup(compRatio, convertedWide(dataWidth - 1, 0))(Seq(
      2.U -> Cat(convertedWide(halfW - 1, 0), accumReg(halfW - 1, 0)),
      4.U -> Cat(convertedWide(quarterW - 1, 0), accumReg(3 * quarterW - 1, 0))
    ))
    io.out.bits := chunkBits

    when(io.in.fire) {
      when(isLast) {
        accumCount := 0.U
        accumReg := 0.U
      } .otherwise {
        // Store current chunk into accumReg at appropriate position
        when(compRatio === 2.U) {
          // ratio=2: accumCount can only be 0 here (since isLast handles 1)
          accumReg := convertedWide(halfW - 1, 0).pad(dataWidth)
        } .otherwise { // compRatio === 4.U
          switch(accumCount) {
            is(0.U) { accumReg := convertedWide(quarterW - 1, 0).pad(dataWidth) }
            is(1.U) { accumReg := Cat(0.U(halfW.W), convertedWide(quarterW - 1, 0), accumReg(quarterW - 1, 0)) }
            is(2.U) { accumReg := Cat(0.U(quarterW.W), convertedWide(quarterW - 1, 0), accumReg(halfW - 1, 0)) }
          }
        }
        accumCount := accumCount + 1.U
      }
    }
  } .elsewhen(isExpansion) {
    // Expansion: 1 input beat → multiple output beats
    io.in.ready := !expanding

    when(!expanding) {
      // Can accept new input
      io.out.valid := false.B
      io.out.bits  := 0.U
      when(io.in.fire) {
        expandReg := convertedWide
        expandCount := 0.U
        expanding := true.B
      }
    } .otherwise {
      // Outputting expanded beats
      io.out.valid := true.B
      io.out.bits := MuxLookup(expandCount, expandReg(dataWidth - 1, 0))(
        (0 until 4).map(i => i.U -> expandReg((i + 1) * dataWidth - 1, i * dataWidth))
      )
      when(io.out.fire) {
        when(expandCount === (expRatio - 1.U)) {
          expanding := false.B
          expandCount := 0.U
        } .otherwise {
          expandCount := expandCount + 1.U
        }
      }
    }
  } .otherwise {
    // 1:1 (shouldn't happen if effectiveBypass is correct, but safety fallback)
    io.out.valid := io.in.valid
    io.out.bits  := convertedWide(dataWidth - 1, 0)
    io.in.ready  := io.out.ready
  }
}
