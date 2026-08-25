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
