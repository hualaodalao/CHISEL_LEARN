/** HiveCore 仿真测试公共工具：FP16 位模式转换、矩阵文件落盘、
  * FP32 bit 解析与 FST 波形拷贝。由 HiveCoreSimCase 与
  * HiveCoreSimCaseLoadWSpec 共用（自两者逐字等价抽取，
  * 差异处以功能更全的一份为准）。
  */

import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest.HasCliOptions
import java.io._
import java.nio.file._

object HiveSimCommon {

  // ========== FP16 位模式转换 ==========

  /** 将 Float 转换为 FP16 (IEEE 754 half-precision) 的 16-bit 整数表示 */
  def floatToFp16(f: Float): Int = {
    val bits = java.lang.Float.floatToIntBits(f)
    val sign = (bits >>> 31) & 1
    val exp = (bits >>> 23) & 0xFF
    val mant = bits & 0x7FFFFF

    if (exp == 0xFF) {
      // Inf / NaN
      (sign << 15) | (31 << 10) | (if (mant != 0) 0x200 else 0)
    } else if (exp == 0) {
      // Zero / FP32 denorm → FP16 zero
      (sign << 15)
    } else {
      val newExp = exp - 127 + 15
      if (newExp >= 31) {
        // Overflow → Inf
        (sign << 15) | (31 << 10)
      } else if (newExp <= 0) {
        // Underflow → zero (simplified, no denorm handling)
        (sign << 15)
      } else {
        val fp16Mant = (mant + 0x1000) >> 13 // round to nearest
        if (fp16Mant >= 0x400) {
          // mantissa overflow after rounding, increment exponent
          (sign << 15) | ((newExp + 1) << 10) | 0
        } else {
          (sign << 15) | (newExp << 10) | fp16Mant
        }
      }
    }
  }

  /** 将 FP16 的 16-bit 整数表示转换回 Float */
  def fp16ToFloat(h: Int): Float = {
    val sign = (h >>> 15) & 1
    val exp = (h >>> 10) & 0x1F
    val mant = h & 0x3FF

    val value = if (exp == 0) {
      // Denorm or zero
      math.pow(2, -14).toFloat * (mant.toFloat / 1024.0f)
    } else if (exp == 31) {
      // Inf / NaN
      if (mant == 0) Float.PositiveInfinity else Float.NaN
    } else {
      math.pow(2, exp - 15).toFloat * (1.0f + mant.toFloat / 1024.0f)
    }
    if (sign == 1) -value else value
  }

  /** FP32 原始 bit（低 32 位）转 Float（数值校验与浮点文件输出共用） */
  def fp32BitsToFloat(bits: Long): Float =
    java.lang.Float.intBitsToFloat((bits & 0xFFFFFFFFL).toInt)

  // ========== 矩阵文件落盘 ==========

  /** 将浮点矩阵写入文本文件（每行空格分隔，%.2f 格式） */
  def writeMatrixFloat(path: String, matrix: Array[Array[Float]]): Unit = {
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => f"$v%.2f").mkString(" "))
    }
    pw.close()
  }

  /** 将 Long 矩阵写入文本文件（每行空格分隔，十进制） */
  def writeMatrixLong(path: String, matrix: Array[Array[Long]]): Unit = {
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(_.toString).mkString(" "))
    }
    pw.close()
  }

  /** 将矩阵写入文本文件（每行空格分隔的 hex 值） */
  def writeMatrixHex(path: String, matrix: Array[Array[Int]], bitsPerElem: Int): Unit = {
    val hexDigits = (bitsPerElem + 3) / 4
    val mask = (1L << bitsPerElem) - 1
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => String.format(s"%0${hexDigits}X", Long.box(v.toLong & mask))).mkString(" "))
    }
    pw.close()
  }

  /** 将 BigInt 矩阵写入文本文件 */
  def writeBigIntMatrixHex(path: String, matrix: Array[Array[BigInt]], bitsPerElem: Int): Unit = {
    val hexDigits = (bitsPerElem + 3) / 4
    val mask = (BigInt(1) << bitsPerElem) - 1
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => (v & mask).toString(16).toUpperCase.reverse.padTo(hexDigits, '0').reverse).mkString(" "))
    }
    pw.close()
  }

  /** 从 BigInt 中提取指定位域 */
  def extractBits(value: BigInt, hi: Int, lo: Int): BigInt = {
    (value >> lo) & ((BigInt(1) << (hi - lo + 1)) - 1)
  }

  // ========== 波形拷贝（FST） ==========

  /** 将 ChiselSim 生成的 trace.fst 复制到目标路径（源存在则覆盖复制，
    * 否则仅打印 NOTE 不 fail，沿用 copyVcd 时代既有约定）。
    * 硬门禁：源文件存在但为 0 字节时直接 fail（波形产物不完整即视为
    * 仿真异常，不再仅靠流程约定）。
    * 运行方式：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
    * sbt "testOnly *<Suite> -- -DemitFst=1"（-D 放 `--` 之后）。
    * FST 源位于 build/chiselsim/&lt;Suite&gt;/&lt;behavior&gt;/&lt;test&gt;/workdir-verilator/trace.fst
    * （Suite/behavior/test 名中空格转 `-`）。
    *
    * @param traceSource ChiselSim 生成的 trace.fst 相对路径
    * @param traceTarget 目标文件路径（如 sim/hivecore_sim.fst）
    * @param tag         日志前缀（如 HiveCoreSimCase）
    */
  def copyTrace(traceSource: String, traceTarget: String, tag: String): Unit = {
    val src = Paths.get(traceSource)
    val tgt = Paths.get(traceTarget)
    if (Files.exists(src)) {
      val srcSize = Files.size(src)
      assert(srcSize > 0, s"[$tag] FST source is 0 bytes (empty trace): $traceSource")
      Files.copy(src, tgt, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      println(s"[$tag] FST waveform copied to $traceTarget ($srcSize bytes)")
    } else {
      println(s"[$tag] NOTE: FST not found at $traceSource (run with -DemitFst=1 to generate)")
    }
  }

  // ========== MX (MXFP8) 转换 helper ==========
  // 逐字镜像 Fp32.mxToFp32（src/hive/scala/main/Fp32.scala L56-91）的数值语义，
  // 作为 P5 MX 用例 golden GEMM 的软件参考模型。任何偏差都会导致 golden 与
  // 硬件失配，故本实现必须与硬件同源同语义（subnormal→0、inf/nan/overflow→
  // fp32Max 保号、underflow→0、normal=sign*2^(e+scale-bias-127)*(1+m/2^mantW)）。

  /** MX 元素（8-bit）+ E8M0 scale（8-bit 纯指数，bias 127）→ Double。
    * 与 Fp32.mxToFp32 完全一致：exp_fp32 = e + scale - bias（scale 的 127 与
    * fp32 的 127 在 fp32Normal 里抵消）；saturate 到 fp32 max normal（0x7F7FFFFF）。
    * @param nanNeedMantAllOne E4M3=true（e 全1 且 m 全1 才 NaN），E5M2=false（e 全1 即 inf/nan）
    */
  def mxToFloat(elem: Int, scaleByte: Int, expW: Int, mantW: Int, bias: Int,
                nanNeedMantAllOne: Boolean): Double = {
    require(1 + expW + mantW == 8, s"MX 元素必须为 8-bit：1+$expW+$mantW")
    val b = elem & 0xFF
    val s = (b >>> 7) & 1
    val e = (b >>> mantW) & ((1 << expW) - 1)
    val m = b & ((1 << mantW) - 1)
    val sign = if (s == 1) -1.0 else 1.0
    val eMax = (1 << expW) - 1
    val isZero = (e == 0) && (m == 0)
    val isSub  = (e == 0) && (m != 0)                       // subnormal → flush-to-zero
    val isInfNan = if (nanNeedMantAllOne) (e == eMax && m == ((1 << mantW) - 1)) else (e == eMax)
    val expSigned = e + (scaleByte & 0xFF) - bias           // = fp32 exp 字段
    // fp32Max = intBitsToFloat(0x7F7FFFFF) ≈ 3.4028235e38（保号），与硬件 fp32Max 一致
    val fp32Max = java.lang.Float.intBitsToFloat(0x7F7FFFFF).toDouble
    if (isZero || isSub) 0.0
    else if (isInfNan || expSigned > 254) sign * fp32Max
    else if (expSigned < 1) 0.0
    else sign * math.pow(2.0, (expSigned - 127).toDouble) * (1.0 + m.toDouble / (1 << mantW))
  }

  /** MX E4M3（exp4/mant3/bias7，NaN 需 exp&mant 全1）+ scale → Double */
  def mxE4M3ToFloat(elem: Int, scaleByte: Int): Double =
    mxToFloat(elem, scaleByte, expW = 4, mantW = 3, bias = 7,  nanNeedMantAllOne = true)

  /** MX E5M2（exp5/mant2/bias15，inf/nan 为 exp 全1）+ scale → Double */
  def mxE5M2ToFloat(elem: Int, scaleByte: Int): Double =
    mxToFloat(elem, scaleByte, expW = 5, mantW = 2, bias = 15, nanNeedMantAllOne = false)

  /** E8M0 scale 字节构造：factor = 2^exp，byte = 127 + exp（bias 127） */
  def mxScaleByte(exp: Int): Int = 127 + exp

  /** 暴力编码：在 [0,256) 内挑选「非 inf/nan」的 MX 字节，使 mxToFloat(byte, 127)
    * （即 scale-free 内蕴值）最接近 f。返回的字节表示 f 的量化值；配合独立的
    * E8M0 scale，真实值 = mxToFloat(byte, scaleByte)。inf/nan 编码被排除，
    * 从而 B 权重经 loadW 走 a 链时也不会触发 HiveCvtOpMx 的 aFmt inf/nan 断言。
    */
  private def floatToMx(f: Double, expW: Int, mantW: Int, bias: Int,
                        nanNeedMantAllOne: Boolean): Int = {
    val eMax = (1 << expW) - 1
    var best = 0
    var bestErr = Double.MaxValue
    var b = 0
    while (b < 256) {
      val e = (b >>> mantW) & ((1 << expW) - 1)
      val m = b & ((1 << mantW) - 1)
      val isInfNan = if (nanNeedMantAllOne) (e == eMax && m == ((1 << mantW) - 1)) else (e == eMax)
      if (!isInfNan) {
        val v = mxToFloat(b, 127, expW, mantW, bias, nanNeedMantAllOne)
        val err = math.abs(v - f)
        // 相等误差时偏好正号/较小幅值字节：确定性挑选，避免 ±0 抖动
        if (err < bestErr) { bestErr = err; best = b }
      }
      b += 1
    }
    best
  }

  /** float → E4M3 元素字节（scale-free，即 scale=127；inf/nan 编码被排除） */
  def floatToMxE4M3(f: Double): Int = floatToMx(f, expW = 4, mantW = 3, bias = 7,  nanNeedMantAllOne = true)

  /** float → E5M2 元素字节（scale-free，即 scale=127；inf/nan 编码被排除） */
  def floatToMxE5M2(f: Double): Int = floatToMx(f, expW = 5, mantW = 2, bias = 15, nanNeedMantAllOne = false)

  // ========== P2 软件参考模型：40→32 规格化 + fp32→fp16/bf16 降精度 ==========
  // 与 HiveOutCvt 硬件（roundUp16 / fp32ToFp16Rnd / fp32ToBf16Rnd）及
  // Fp32.fpNormalize40to32 逐位同构，作为 HiveOutCvtSpec 与 outFmt 仿真用例的
  // golden 对拍模型。rnd 编码与 RoundingMode ChiselEnum 同源：
  //   0=RNE 1=RTZ 2=RDN 3=RUP 4=RMM

  /** 32→16 截断进位判定（与硬件 roundUp16 逐位同构） */
  def swRoundUp(rnd: Int, guard: Int, sticky: Int, lsb: Int, neg: Boolean): Boolean = rnd match {
    case 0 => guard == 1 && (sticky == 1 || lsb == 1)   // RNE：严格 tie-to-even
    case 1 => false                                     // RTZ：恒截断
    case 2 => neg && (guard == 1 || sticky == 1)        // RDN：负数增大幅值
    case 3 => !neg && (guard == 1 || sticky == 1)       // RUP：正数增大幅值
    case 4 => guard == 1                                // RMM：ties to Max Magnitude，仅 tie（guard=1，不论 sticky）进位
  }

  /** fp32 bits → fp16 16-bit（与硬件 fp32ToFp16Rnd 逐位同构：
    * 溢出饱和 ±65504、下溢/零 FTZ 保号、inf/nan 直接映射） */
  def swFp32ToFp16(bits: Long, rnd: Int): Int = {
    val b = bits & 0xFFFFFFFFL
    val s = ((b >>> 31) & 1).toInt
    val e = ((b >>> 23) & 0xFF).toInt
    val m = (b & 0x7FFFFF).toInt
    val signedZero = s << 15
    val fp16Max = (s << 15) | (30 << 10) | 0x3FF        // ±65504
    val fp16Inf = (s << 15) | (31 << 10)
    val fp16Nan = (s << 15) | (31 << 10) | 0x200
    if (e < 113) signedZero                             // 零/次正规/下溢 → FTZ（保号）
    else if (e == 255) (if (m == 0) fp16Inf else fp16Nan)
    else {
      val mantTop = (m >>> 13) & 0x3FF
      val guard = (m >>> 12) & 1
      val sticky = if ((m & 0xFFF) != 0) 1 else 0
      val up = if (swRoundUp(rnd, guard, sticky, mantTop & 1, s == 1)) 1 else 0
      val manRnd = mantTop + up
      val carry = manRnd >>> 10
      val manFin = if (carry == 1) 0 else manRnd & 0x3FF
      val overflow = e > 142 || (e == 142 && carry == 1)
      if (overflow) fp16Max
      else (s << 15) | (((e + carry - 112) & 0x1F) << 10) | manFin
    }
  }

  /** fp32 bits → bf16 16-bit（与硬件 fp32ToBf16Rnd 逐位同构：
    * 高 16 位直取 + 舍入进位；inf→inf、nan→规范 nan(0x7FA0|sign)） */
  def swFp32ToBf16(bits: Long, rnd: Int): Int = {
    val b = bits & 0xFFFFFFFFL
    val s = ((b >>> 31) & 1).toInt
    val e = ((b >>> 23) & 0xFF).toInt
    val m = (b & 0x7FFFFF).toInt
    if (e == 0) s << 15
    else if (e == 255) {
      if (m == 0) (s << 15) | 0x7F80                    // inf
      else (s << 15) | 0x7FA0                           // 规范 nan（mant 非零）
    } else {
      val guard = ((b >>> 15) & 1).toInt
      val sticky = if ((b & 0x7FFF) != 0) 1 else 0
      val lsb = ((b >>> 16) & 1).toInt
      val up = if (swRoundUp(rnd, guard, sticky, lsb, s == 1)) 1 else 0
      (((b >>> 16).toInt) + up) & 0xFFFF
    }
  }

  /** 40-bit 延迟规格化 {sign,exp8,mant31} → fp32 bits
    * （Fp32.fpNormalize40to32 软件模型：LZC+左移、简化 RNE=guard&(sticky|lsb)、
    *   e<=lzc 时指数饱和为 0、isZero(e==0&&m==0) 返回绝对 0） */
  def swFpNormalize40to32(raw: BigInt): Long = {
    val s = ((raw >> 39) & 1).toInt
    val e = ((raw >> 31) & 0xFF).toInt
    val m31 = raw & ((BigInt(1) << 31) - 1)
    if (e == 0 && m31 == 0) return 0L                   // 硬件 isZero → 绝对 0（无符号）
    val lzc = 30 - (m31.bitLength - 1)                  // PriorityEncoder(Reverse(m31))
    val normed = (m31 << lzc) & ((BigInt(1) << 31) - 1)
    val mant23 = ((normed >> 7) & ((BigInt(1) << 23) - 1)).toInt
    val guard = ((normed >> 6) & 1).toInt
    val sticky = if ((normed & 0x3F) != 0) 1 else 0
    val roundUp = guard == 1 && (sticky == 1 || (mant23 & 1) == 1)
    // 舍入进位与硬件同构（修复留痕）：mant23 全 1 且进位时尾数归零、指数 +1，
    // 中间量 9 位防溢出、最终截 8 位（对应 Fp32.fpNormalize40to32 的 mantCarry/eOut）
    val mSum = mant23 + (if (roundUp) 1 else 0)
    val mantCarry = mSum >>> 23
    val mRounded = mSum & 0x7FFFFF
    val eBase = if (e > lzc) e - lzc else 0
    val eOut = (eBase + mantCarry) & 0xFF
    ((s.toLong << 31) | (eOut.toLong << 23) | mRounded.toLong) & 0xFFFFFFFFL
  }

  /** fp32 bits → 40-bit 延迟规格化槽（{sign,exp8,隐含1<<30,左移7 的 mant23}；
    * e==0 的 ±0/次正规在 40 位域中归零——硬件 fpNormalize40to32 的 isZero 分支
    * 返回绝对 0（不保号），软件模型同语义，故 -0.0 落槽后重建为 +0） */
  def fp32BitsTo40(bits: Long): BigInt = {
    val b = bits & 0xFFFFFFFFL
    val s = (b >>> 31) & 1
    val e = (b >>> 23) & 0xFF
    val m = b & 0x7FFFFF
    if (e == 0) BigInt(0)
    else (BigInt(s) << 39) | (BigInt(e) << 31) | (BigInt(1) << 30) | (BigInt(m) << 7)
  }

  // ========== P3a 软件参考模型：MX 量化（共享 E8M0 scale + 目标格式舍入） ==========
  // 独立推导期望（不抄硬件实现结构）：量化语义由「块共享 scale 约束式」出发重建：
  //   amax = 1.f × 2^(E-127)，约束 amax ≤ maxFinite × 2^(s-127)，其中
  //   maxFinite = maxMantVal × 2^(maxCodeExp-bias)；展开得 s = E_eff + bias - maxCodeExp
  //   （fp32 偏置指数已含 127，与 E8M0 bias 抵消）；元素侧 ÷2^(s-127) 即指数减。
  // 舍入判定复用上方 swRoundUp（5 模式、RMM=guard，与硬件同源同链：
  // 40→32 固定简化 RNE 规格化 + 量化运行期 rnd，两级舍入链）。
  // fmt 编码与 outFmt 3-bit 同源：4=MXE4M3、5=MXE5M2、6=MXE2M1。

  /** 量化格式参数表：(expW, mantW, bias, maxCodeExp, maxMant23, maxNormalExp,
    * satCode, nanCode, nanToSat, fixNanCode)，由 OCP MX 字段定义独立推导：
    *   E4M3：maxFinite=448=1.75×2^8，NaN 需 exp&mant 全1（0x7F），饱和 0x7E，
    *         maxMantVal=1.75（mant 全1 是 NaN 不可用，尾数域比较门限=0.75→0x600000）；
    *         舍入落 NaN 码改写为饱和。
    *   E5M2：maxFinite=57344=1.75×2^15，exp 全1 为 inf/nan，饱和 0x7B，
    *         nan 规范编码取 exp 全1 + 尾数高位置位（0x7E，同 fp16 规范 nan 取法）。
    *   E2M1：maxFinite=6.0=1.5×2^2，无 inf/nan 编码（exp 全1 仍为正常数），
    *         nan 输入裁定饱和（0x7），maxMantVal=1.5（尾数域比较门限=0.5→0x400000）。 */
  private def quantFmtParams(fmt: Int): (Int, Int, Int, Int, Long, Int, Int, Int, Boolean, Boolean) = fmt match {
    case 4 => (4, 3, 7,  15, 0x600000L, 15, 0x7E, 0x7F, false, true)
    case 5 => (5, 2, 15, 30, 0x600000L, 30, 0x7B, 0x7E, false, false)
    case 6 => (2, 1, 1,  3,  0x400000L, 3,  0x07, 0x07, true,  false)
    case _ => throw new IllegalArgumentException(s"swQuantBlock: 非法量化格式 fmt=$fmt（需 4/5/6）")
  }

  /** 块共享 E8M0 scale：满足 amax ≤ maxFinite × 2^(s-127) 的最小指数。
    * 全零块（含掩码后无有效元素）→ 0x00 哨兵；amax=inf → 254 钳制；
    * s<1（块幅值极小）→ 钳制 1。 */
  def swBlockScale(amaxBits: Long, maxCodeExp: Int, bias: Int, maxMant23: Long): Int = {
    val amax = amaxBits & 0x7FFFFFFFL
    if (amax == 0L) return 0                                  // 全零块哨兵（反量化侧门控）
    val e = ((amax >>> 23) & 0xFF).toInt
    if (e == 255) return 254                                  // amax 为 inf → 钳制 254
    val m = amax & 0x7FFFFFL
    val eEff = e + (if (m > maxMant23) 1 else 0)              // 1.f > maxMantVal → 指数提 1
    val s = eEff + bias - maxCodeExp
    if (s < 1) 1 else if (s > 254) 254 else s
  }

  /** 单元素量化：fp32 ÷ 2^(s-127)（指数减）→ 舍入到目标格式（边界表镜像反量化侧取逆）：
    * 溢出饱和最大有限值（保号）、低于最小正规格化保号 ±0（无次正规）、
    * inf→饱和、nan→目标编码（E2M1 无 nan 编码裁定饱和）、零→±0。 */
  def swQuantElem(bits: Long, s: Int, rnd: Int, expW: Int, mantW: Int, bias: Int,
                  maxNormalExp: Int, satCode: Int, nanCode: Int,
                  nanToSat: Boolean, fixNanCode: Boolean): Int = {
    val b = bits & 0xFFFFFFFFL
    val sg = ((b >>> 31) & 1).toInt
    val e = ((b >>> 23) & 0xFF).toInt
    val m = (b & 0x7FFFFF).toInt
    val tailBits = expW + mantW
    val signPart = sg << tailBits
    if (e == 0) return signPart                               // ±0 / fp32 次正规 → FTZ 保号 ±0
    if (e == 255) {
      if (m == 0) return signPart | satCode                   // inf → 饱和最大有限值（保号）
      return signPart | (if (nanToSat) satCode else nanCode)  // nan → 目标编码（E2M1 裁定饱和）
    }
    val eTgt = e + bias - s                                   // ÷2^(s-127) ⟺ 目标偏置指数 = e + bias - s
    if (eTgt < 1) return signPart                             // 低于最小正规格化 → ±0（无次正规产出）
    val drop = 23 - mantW
    val mantTop = (m >>> drop) & ((1 << mantW) - 1)
    val guard = (m >>> (drop - 1)) & 1
    val sticky = if ((m & ((1L << (drop - 1)) - 1)) != 0) 1 else 0
    val up = if (swRoundUp(rnd, guard, sticky, mantTop & 1, sg == 1)) 1 else 0
    var mRnd = mantTop + up
    var eRnd = eTgt
    if (mRnd >= (1 << mantW)) { mRnd = 0; eRnd += 1 }         // 尾数进位 → 指数 +1（同硬件 +&/carry 模式）
    if (eRnd > maxNormalExp) return signPart | satCode        // 舍入后溢出 → 饱和（保号）
    if (fixNanCode && eRnd == maxNormalExp && mRnd == (1 << mantW) - 1)
      return signPart | satCode                               // 舍入落 NaN 码（仅 E4M3）→ 改写为饱和值 448
    signPart | (eRnd << mantW) | mRnd
  }

  /** 整行量化参考：输入为已经 40→32 规格化的 fp32 bits 行（与硬件两级舍入链同链），
    * 返回 (逐元素量化码, 每 8 元素组 1 字节的 scale 序列)。blkSel：0=8/1=16/2=32/3=64；
    * 列 ≥ nEff 为 padding：不进 amax、元素码输出 0。 */
  def swQuantBlock(normFp32s: Seq[Long], rnd: Int, fmt: Int, blkSel: Int, nEff: Int): (Seq[Int], Seq[Int]) = {
    val (expW, mantW, bias, maxCodeExp, maxMant23, maxNormalExp, satCode, nanCode, nanToSat, fixNan) =
      quantFmtParams(fmt)
    val n = normFp32s.size
    require(n % 8 == 0, s"swQuantBlock: 行元素数($n) 必须为 8 的整数倍")
    // 幅值 = 符号位清零（非负 fp32 位模式无符号比较即幅值序）；padding 列掩 0 不进 amax
    val mags = normFp32s.zipWithIndex.map { case (b, i) => if (i < nEff) (b & 0x7FFFFFFFL) else 0L }
    def maxMag(s: Seq[Long]): Long = s.reduce((a, b) => if (a >= b) a else b)
    val groups = mags.grouped(8).map(maxMag).toSeq            // 8 元素组 amax
    def merge(s: Seq[Long]): Seq[Long] =
      s.grouped(2).map(p => if (p.size == 2) maxMag(p) else p.head).toSeq
    val l16 = merge(groups); val l32 = merge(l16); val l64 = merge(l32)
    def amaxOf(i: Int): Long = blkSel match {
      case 0 => groups(i / 8)
      case 1 => l16(i / 16)
      case 2 => l32(i / 32)
      case _ => l64(i / 64)
    }
    def scaleOf(amax: Long): Int = swBlockScale(amax, maxCodeExp, bias, maxMant23)
    val codes = normFp32s.zipWithIndex.map { case (b, i) =>
      if (i >= nEff) 0                                        // padding 列输出 0（不参与量化）
      else swQuantElem(b, scaleOf(amaxOf(i)), rnd,
        expW, mantW, bias, maxNormalExp, satCode, nanCode, nanToSat, fixNan)
    }
    val scaleBytes = (0 until n / 8).map { g =>
      val amax = blkSel match {
        case 0 => groups(g)
        case 1 => l16(g / 2)
        case 2 => l32(g / 4)
        case _ => l64(g / 8)
      }
      scaleOf(amax)
    }
    (codes, scaleBytes)
  }
}

/** ChiselSim FST 波形扩展 trait：与 ChiselSim 一起混入测试类。
  *
  *  - 预置 verilator 后端 traceStyle = TraceStyle(TraceKind.Fst())：harness
  *    编译加 `--trace-fst`，svsim 自动注入 `enableFstTracingSupport`
  *    Verilog 预处理 define（见 svsim.verilator.Backend.defineFlags），
  *    产物同位 workdir-verilator/trace.fst；
  *  - 仿 Cli.EmitVcd 语义用 addOption 注册 `-DemitFst=[1,true]` flag，
  *    仅置 simulationSettings.enableWavesAtTimeZero=true：不带开关时波形
  *    关闭、行为与迁移前 VCD 默认一致（EmitVcd 的 updateBackendSettings
  *    在 traceStyle 已设时不覆盖，故与 -DemitVcd 共存亦不冲突）；
  *    注意语义漂移：混入本 trait 后 `-DemitVcd=1` 仍会开启波形 dump，
  *    但产物为 trace.fst（非 .vcd），请改用 `-DemitFst=1`；
  *  - 环境硬约束：Verilator 5.050 的 FST writer 无条件依赖 lz4 头文件，
  *    ChiselSim 不暴露 -CFLAGS 注入口，运行 sbt 前必须设置
  *    CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib，
  *    否则 harness 编译报 `'lz4.h' file not found`。
  */
trait HiveFstSim { this: HasCliOptions =>

  /** 预置 FST traceStyle 的 verilator simulator（覆盖 HasSimulator
    * companion 的低优先级 default implicit）。
    */
  implicit val fstSimulator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
        .withTraceStyle(
          Some(
            svsim.verilator.Backend.CompilationSettings
              .TraceStyle(svsim.verilator.Backend.CompilationSettings.TraceKind.Fst())
          )
        )
    )

  addOption(
    HasCliOptions.CliOption.flag(
      name = "emitFst",
      help = "start dumping FST waves at time zero (harness is always compiled with --trace-fst)",
      updateCommonSettings = (options) =>
        options.copy(
          simulationSettings = options.simulationSettings.copy(
            enableWavesAtTimeZero = true
          )
        )
    )
  )
}
