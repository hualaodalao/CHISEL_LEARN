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
      Files.copy(src, tgt, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      println(s"[$tag] FST waveform copied to $traceTarget")
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
