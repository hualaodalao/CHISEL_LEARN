/** HiveCore 仿真测试公共工具：FP16 位模式转换、矩阵文件落盘、
  * FP32 bit 解析与 FST 波形拷贝。由 HiveCoreSimCase 与
  * HiveCoreSimCaseLoadWSpec 共用（自两者逐字等价抽取，
  * 差异处以功能更全的一份为准）。
  * 格式编码统一经 HiveEnum.DataFormat 枚举派生常量驱动（见 object 开头
  * 常量区）；【任务104】fmt 相关公开 API 入参已由 Int 编码迁移为
  * DataFormat.Type 枚举（避免枚举再改编号后调用点静默错位）；控制字
  * 位域打包等仍需整型的场景由调用方以 fmt.litValue.toInt 收敛。
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest.HasCliOptions
import java.io._
import java.nio.file._

object HiveSimCommon {

  // ========== DataFormat 枚举派生常量（格式编码唯一源） ==========
  // OutFormat/DataFormat 已合并为单一 DataFormat 枚举（src/hive/scala/main/
  // HiveEnum.scala），成员声明即隐式 id 顺延：FP32=0 FP16=1 BF16=2 MXE4M3=3
  // MXE5M2=4 MXE2M1=5 INT32=6 INT16=7 INT8=8 INT4=9。本 object 的全部格式
  // 编码比较/分档均经此常量区驱动，不再出现裸数字。
  private val FmtFp32   = DataFormat.FP32.litValue.toInt
  private val FmtFp16   = DataFormat.FP16.litValue.toInt
  private val FmtBf16   = DataFormat.BF16.litValue.toInt
  private val FmtMxE4M3 = DataFormat.MXE4M3.litValue.toInt
  private val FmtMxE5M2 = DataFormat.MXE5M2.litValue.toInt
  private val FmtMxE2M1 = DataFormat.MXE2M1.litValue.toInt
  private val FmtInt32  = DataFormat.INT32.litValue.toInt
  private val FmtInt16  = DataFormat.INT16.litValue.toInt
  private val FmtInt8   = DataFormat.INT8.litValue.toInt
  private val FmtInt4   = DataFormat.INT4.litValue.toInt

  // 格式族集合（Scala 级，与 HiveEnum 谓词语义对齐，用于元素位宽/字节分档）：
  //   4B 族 = is4B = {FP32,INT32}；2B 族 = is2B ∪ {INT16}（INT16 输出同为
  //     2B/元素，与 HiveOutCvt 选段 packedInt16 同档；is2B 谓词仅覆盖输入侧
  //     浮点 2B 族）；1B 族 = is1B = {INT8,MXE4M3,MXE5M2}；
  //     半字节族 = isHalfB = {INT4,MXE2M1}。
  // 未知编码不在任何集合 → 默认臂 32-bit 档回落（原 reserved 默认臂语义不变）。
  private val Fmt4B    = Set(FmtFp32, FmtInt32)
  private val Fmt2B    = Set(FmtFp16, FmtBf16, FmtInt16)
  private val Fmt1B    = Set(FmtInt8, FmtMxE4M3, FmtMxE5M2)
  private val FmtHalfB = Set(FmtInt4, FmtMxE2M1)

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

  // ========== STATUS 寄存器轮询契约（EXECUTE 立即响应语义下的完成判定） ==========
  // 【任务101】EXECUTE 命令已改为立即返回响应（respData=0、respErr=0，不代表完成），
  // 测试侧完成判定统一改为 REG_READ 轮询 STATUS 寄存器：
  //   - 读地址 0x08：HiveCore REG_READ 分支 Mux(addr === 0x08.U, statusReg, ...)
  //     （addr 截 4 位，0x08 不变；读命令地址携带在 cmd.payload.reg0Addr）。
  //   - statusReg 位布局（HiveCore.scala Cat(progress, 0.U(9.W), errCode, errFlag,
  //     doneFlag, executor.io.busy)，高位在前）：
  //       bit0=busy（executor 组合）
  //       bit1=done（doneFlag 粘滞位：executor.io.done 拍置 1，写 regs(7) bit0=1 清除）
  //       bit2=err（errFlag：executor.io.done 拍置 calcConfig.err||dmaErr，startPulse 拍清）
  //       bit[6:3]=errCode（4-bit，calcConfig 锁存）
  //       bit[15:7]=0（保留）
  //       bit[31:16]=progress（16-bit，已完成 tile 数）
  //   - cmd/resp 握手：cmd.ready = !respValid（上一响应未消费则反压）；cmd.fire
  //     当拍写 respValid/respData 寄存器、次拍 resp.valid=1；resp.ready 恒高场景
  //     下 fire 次拍恰好消费一次。

  /** STATUS 寄存器 REG_READ 地址 */
  val STATUS_REG_ADDR: Int = 0x08

  /** 轮询周期（拍）：一轮 = 发起/等 fire（1~2 拍）+ 读拍 1 拍 + 冷却，减少命令流量 */
  val POLL_PERIOD: Int = 16

  /** STATUS 寄存器解码结果（位布局见上方注释） */
  final case class HiveStatusBits(busy: Boolean, done: Boolean, err: Boolean,
                                  errCode: Int, progress: Int)

  /** REG_READ(0x08) 读回的 32-bit statusReg 解码（按 HiveCore.scala 当前位布局） */
  def decodeStatus(data: BigInt): HiveStatusBits = HiveStatusBits(
    busy = (data & 0x1L) != 0,
    done = ((data >> 1) & 0x1L) != 0,
    err = ((data >> 2) & 0x1L) != 0,
    errCode = ((data >> 3) & 0xFL).toInt,
    progress = ((data >> 16) & 0xFFFFL).toInt
  )

  // ========== MX (MXFP8 / MXFP4) 转换 helper ==========
  // 逐字镜像 Fp32.mxToFp32（src/hive/scala/main/Fp32.scala）的数值语义，
  // 作为 P5 MX 用例 golden GEMM 的软件参考模型。任何偏差都会导致 golden 与
  // 硬件失配，故本实现必须与硬件同源同语义（subnormal→0、inf/nan/overflow→
  // fp32Max 保号、underflow→0、normal=sign*2^(e+scale-bias-127)*(1+m/2^mantW)）。
  // 【Phase 0 同批泛化】硬件侧新增 noInfNan/subnormalFtz 两个编译期形参以纳入
  // mxfp4 E2M1，本金锚【同批】镜像同款泛化（同语义、同默认实参），禁止以 RTL
  // 行为反推拟合；fp8 默认实参组合下每一分支与泛化前逐字一致。

  /** MX 元素 + E8M0 scale（8-bit 纯指数，bias 127）→ Double。
    * 与 Fp32.mxToFp32 完全一致：exp_fp32 = e + scale - bias（scale 的 127 与
    * fp32 的 127 在 fp32Normal 里抵消）；saturate 到 fp32 max normal（0x7F7FFFFF）。
    * @param nanNeedMantAllOne E4M3=true（e 全1 且 m 全1 才 NaN），E5M2=false（e 全1 即 inf/nan）
    * @param noInfNan     编译期：true=该格式【无 inf/nan 编码】（mxfp4 E2M1：最大码
    *                     0x7 = ±6.0 是最大有限值）⟹ isInfNan 恒 false；false=保持原判据（fp8）
    * @param subnormalFtz 编译期：true=e==0&&m!=0 flush-to-zero（fp8 既有行为）；
    *                     false=e==0&&m!=0 按次正规真值 ±2^(scale-bias-127) 解码
    *                     （E2M1 的 e=0/m=1 = ±0.5，一等公民值）
    */
  def mxToFloat(elem: Int, scaleByte: Int, expW: Int, mantW: Int, bias: Int,
                nanNeedMantAllOne: Boolean,
                noInfNan: Boolean = false, subnormalFtz: Boolean = true): Double = {
    require(1 + expW + mantW == 8 || 1 + expW + mantW == 4,
      s"MX 元素必须为 8-bit（mxfp8）或 4-bit（mxfp4）：1+$expW+$mantW")
    val b = elem & 0xFF
    val s = (b >>> (expW + mantW)) & 1     // 符号位（fp8: bit7；mxfp4 E2M1: bit3）
    val e = (b >>> mantW) & ((1 << expW) - 1)
    val m = b & ((1 << mantW) - 1)
    val sign = if (s == 1) -1.0 else 1.0
    val eMax = (1 << expW) - 1
    val isZero = (e == 0) && (m == 0)
    val isSub  = (e == 0) && (m != 0)                       // subnormal（见 subnormalFtz）
    val isInfNan = if (noInfNan) false
                   else if (nanNeedMantAllOne) (e == eMax && m == ((1 << mantW) - 1)) else (e == eMax)
    val expSigned = e + (scaleByte & 0xFF) - bias           // = fp32 exp 字段
    // fp32Max = intBitsToFloat(0x7F7FFFFF) ≈ 3.4028235e38（保号），与硬件 fp32Max 一致
    val fp32Max = java.lang.Float.intBitsToFloat(0x7F7FFFFF).toDouble
    if (subnormalFtz) {
      // fp8（E4M3/E5M2）原路径：分支序与表达式与泛化前逐字一致
      if (isZero || isSub) 0.0
      else if (isInfNan || expSigned > 254) sign * fp32Max
      else if (expSigned < 1) 0.0
      else sign * math.pow(2.0, (expSigned - 127).toDouble) * (1.0 + m.toDouble / (1 << mantW))
    } else {
      // mxfp4 E2M1：isSub（e=0/m=1）解码为 ±2^(expSigned-127) = ±2^(scale-bias-127)
      //   = ±0.5 × 2^(scale-127)（bias=1、mantW=1、m=1），尾数隐含 1.0 ⟹ 不乘 (1+m/2^mantW)。
      //   与硬件 fp32Sub = Cat(s, expSigned(7,0), 0.U(23.W)) 同源：同享 expSigned<1 → ±0 的
      //   underflow clamp（scale<2 时正确 FTZ，而非裸 (scale-1) 在 scale=0 绕回成 ±inf）。
      if (isZero) 0.0
      else if (isInfNan || expSigned > 254) sign * fp32Max
      else if (expSigned < 1) 0.0
      else if (isSub) sign * math.pow(2.0, (expSigned - 127).toDouble)
      else sign * math.pow(2.0, (expSigned - 127).toDouble) * (1.0 + m.toDouble / (1 << mantW))
    }
  }

  /** MX E4M3（exp4/mant3/bias7，NaN 需 exp&mant 全1）+ scale → Double */
  def mxE4M3ToFloat(elem: Int, scaleByte: Int): Double =
    mxToFloat(elem, scaleByte, expW = 4, mantW = 3, bias = 7,  nanNeedMantAllOne = true)

  /** MX E5M2（exp5/mant2/bias15，inf/nan 为 exp 全1）+ scale → Double */
  def mxE5M2ToFloat(elem: Int, scaleByte: Int): Double =
    mxToFloat(elem, scaleByte, expW = 5, mantW = 2, bias = 15, nanNeedMantAllOne = false)

  /** MX E2M1（mxfp4，exp2/mant1/bias1）+ scale → Double。
    * 【金锚纪律】与硬件 Fp32.mxE2M1ToFp32 同批泛化、逐位同源：格式参数一律引
    * MxFormatParams.E2M1（禁裸字面量），noInfNan=true（无 inf/nan 编码，0x7=±6.0 为
    * 最大有限值）、subnormalFtz=false（e=0/m=1 = ±0.5 是一等公民值，不得 FTZ）。
    * 幅值域 = {0, ±0.5, ±1, ±1.5, ±2, ±3, ±4, ±6}；元素真值 = 幅值 × 2^(scale-127)。
    * @param elem 元素码；仅低 4 位有效（bit3=sign），高 4 位由软件保证为 0（与硬件同约定）
    */
  def mxE2M1ToFloat(elem: Int, scaleByte: Int): Double =
    mxToFloat(elem, scaleByte,
      expW = MxFormatParams.E2M1.expW, mantW = MxFormatParams.E2M1.mantW,
      bias = MxFormatParams.E2M1.bias, nanNeedMantAllOne = false,
      noInfNan = true, subnormalFtz = false)

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

  // ========== P3 C 写回密集布局公共模型（256-bit 密集拍，四套件共用） ==========
  // 与硬件 P0-P2 新架构逐拍同源（HiveCore calcConfig 单式 + HiveCoreDmaWrOnly
  // 运行期拍数解码）：
  //   元素位宽档（DataFormat 编码，格式族集合见 object 开头常量区）：
  //     FP32/INT32/未知回落=32、FP16/BF16/INT16=16、MXE4M3/MXE5M2/INT8=8、
  //     MXE2M1/INT4=4（半字节族）；
  //   beatsPerRow = ceil(totalN×elemBits/cDmaExtW)（256 默认 × totalN=16 下 ∈ {1,2}，
  //     半字节族恒 1 拍低 64 位有效、半/四分之一占用高位零填充，禁止双行合拍）；
  //   发拍数据 = 密集行 >> (beatCnt×cDmaExtW) 取低 cDmaExtW 位（拍内低列号在低位）；
  //   地址：cRowOff = n×outElemBytes（半字节族 = n>>1）、cColOff = totalN×outElemBytes
  //     （半字节族 = totalN>>1）、cBeatAddrStep = (cDmaExtW/elemBits)×elemBytes
  //     （半字节族 = cDmaExtW/8）；块间跳转按张量语义绝对式 = nt×cColOff（#133：
  //     旧「沿用 cDma sNEXT_N 三角累积闭式（金锚背书）」表述已废止——三角累积是当时
  //     缺陷 RTL 的行为、金锚不得以其为拟合目标；RTL 与金锚现同为线性/绝对模型）。
  // 编码 = HiveEnum.DataFormat 枚举 litValue（OutFormat/DataFormat 已合并为单一
  //   枚举，成员声明即隐式 id：0=FP32 1=FP16 2=BF16 3=MXE4M3 4=MXE5M2 5=MXE2M1
  //   6=INT32 7=INT16 8=INT8 9=INT4）；未知编码不在任何格式族集合 → 32-bit 档回落。

  /** outFmt（DataFormat 枚举）→ 元素位宽（32/16/8/4），与硬件
    * HiveCoreCalcConfig cWriteBytes 谓词链（is4B/is2B/is1B/otherwise）同源 */
  def cOutElemBits(outFmt: DataFormat.Type): Int = {
    val f = outFmt.litValue.toInt
    if (Fmt4B.contains(f)) 32
    else if (Fmt2B.contains(f)) 16
    else if (Fmt1B.contains(f)) 8
    else if (FmtHalfB.contains(f)) 4
    else 32   // 未知编码回落（原 reserved 默认臂语义不变）
  }

  /** outFmt → 输出元素字节数（地址步进档：4/2/1；半字节族返回 0 表示走专用分支） */
  def cOutElemBytes(outFmt: DataFormat.Type): Int = {
    val f = outFmt.litValue.toInt
    if (Fmt4B.contains(f)) 4
    else if (Fmt2B.contains(f)) 2
    else if (Fmt1B.contains(f)) 1
    else if (FmtHalfB.contains(f)) 0
    else 4      // 未知编码回落（原 reserved 默认臂语义不变）
  }

  /** 每行拍数 = ceil(totalN×elemBits/cDmaExtW)，与 WrOnly beatsPerRowEff 同源 */
  def cBeatsPerRow(outFmt: DataFormat.Type, totalN: Int, cDmaExtW: Int): Int =
    (totalN * cOutElemBits(outFmt) + cDmaExtW - 1) / cDmaExtW

  /** 拍内地址步进（字节）。
    *
    * 【#136-KS5 精确化订正：原「与顶层 calcConfig.cBeatAddrStep 同源」的无条件声明不成立】
    *   金锚本式：半字节族 = cDmaExtW/8；其余 = (cDmaExtW/elemBits)×elemBytes。
    *   RTL（HiveCoreCalcConfig）：cBeatAddrStep = if (cWriteBytes > cDmaBytes) cDmaBytes
    *     else cWriteBytes，其中 cWriteBytes = totalN×cEb（半字节族 = totalN/2）、
    *     cDmaBytes = cDmaExtW/8。
    *   ★ 结论：在【beats>1 的全部档位】两式恒等；1B 与半字节族数值分歧，但该分歧
    *     【可证明永不可观测】。不得据此「对齐」数值（对齐会在缩位档引入真错）。
    *   证明：beats = ceil(totalN×elemBits / cDmaExtW) > 1 ⟺ totalN×elemBits > cDmaExtW
    *     ⟺ cWriteBytes > cDmaBytes（cWriteBytes = totalN×elemBits/8、cDmaBytes =
    *     cDmaExtW/8，同乘 8）。故 beats>1 时 RTL 取 cDmaBytes = cDmaExtW/8，而金锚在
    *     整字节族满足 elemBits = 8×cEb，(cDmaExtW/elemBits)×cEb = cDmaExtW/8，两式相等。
    *     反向：HiveCoreConfig 强制 cDmaExtW ∈ [totalN×8, totalN×32]，故 elemBits<=8 时
    *     totalN×elemBits <= totalN×8 <= cDmaExtW ⇒ beats≡1 ⇒ 行内拍下标 b≡0 ⇒
    *     b×step 项恒为 0，step 从不进入任何地址（数值分歧不可观测）。
    *   分歧实例（仅记录，勿改）：cDmaExtW=256 的 1B 档金锚 32 vs RTL cWriteBytes=16；
    *     cDmaExtW=512 的 2B 档金锚 64 vs RTL 32。cDmaExtW∈{128,512} 结论不变。
    *   ★ 若将来放宽 cDmaExtW 下界（< totalN×8）或让 1B 族出现 beats>1，上述不可观测性
    *     立即失效，本式必须与 RTL 同时重推 —— 届时【先改 RTL 再改金锚】，禁止反向拟合。 */
  def cBeatAddrStep(outFmt: DataFormat.Type, cDmaExtW: Int): Long = {
    val f = outFmt.litValue.toInt
    if (FmtHalfB.contains(f)) (cDmaExtW / 8).toLong
    else (cDmaExtW.toLong / cOutElemBits(outFmt)) * cOutElemBytes(outFmt)
  }

  /** C 写回期望地址序列（密集布局 + 拍内细分，四套件共用唯一推演源）：
    * 遍历序 nt 外 × m 中 × 行内拍内；地址由【张量语义绝对式】推导：
    *   addr(nt,m,b) = base + nt×cColOff + m×cRowOff + b×cBeatAddrStep
    *
    * 【规范性声明（#133，勿删）】本函数是金锚（oracle）：期望值只允许由 C 的张量语义
    * 布局推导，【禁止】以 HiveCoreDmaWrOnly 的 RTL 行为为拟合目标。推导如下——
    * C 为 M×N row-major，元素 cOutElemBytes 字节（半字节族两列打包一字节，故行/列
    * 步进各减半）：addr(C[i][j]) = base + j×cEb + i×(N×cEb)。写回按 n-tile 分块，
    * 第 nt 块的首列 j = nt×totalN ⇒ 块列偏移 = nt×cColOff（cColOff = totalN×cEb）；
    * 块内第 m 行 i = m ⇒ 行偏移 = m×cRowOff（cRowOff = N×cEb）；行内第 b 拍再叠
    * b×cBeatAddrStep。三项互不耦合、无轮间累积项。
    *
    * 【历史缺陷留痕（勿回退）】本式曾写成三角累积闭式
    *   base + nt×(M−1)×cRowOff + nt×(nt+1)/2×cColOff + m×cRowOff + b×step
    * —— 那是对当时缺陷 RTL（sNEXT_N 用 `curAddr := curAddr + colAddr` 自增累加）的
    * 反向拟合，使所有 nTiles≥2 用例的 dmaC 逐拍地址硬断言退化为「RTL 与其镜像自比」
    * 而恒真假绿（FP32/N=32 下 nt=1 首址手算得 base+0xFC0，正确值应为 base+0x40）。
    * RTL 侧已改为 `curAddr := io.regFile.cAddr + colAddr`（基址 + 轮偏移线性模型，与
    * HiveCoreDmaScaleWr 同构），本式随之回到张量语义绝对式。下方两条 require 是本式
    * 的自证不变式：任何三角化回退都会在【金锚构造期】立刻炸掉（即 Scala 测试体求值、
    * 调用本函数的时刻，早于 simulate；既非 scalac 编译期亦非 Chisel elaboration 期），
    * 【不得注释掉】。其证伪边界（m==1 ∧ nTiles==2 的可证明盲区）见下方 require 段注释。
    */
  def cAddrSeqDense(cBase: Long, m: Int, n: Int, totalN: Int, outFmt: DataFormat.Type,
                    cDmaExtW: Int): Seq[Long] = {
    val f = outFmt.litValue.toInt
    val nTiles = (n + totalN - 1) / totalN
    val beats = cBeatsPerRow(outFmt, totalN, cDmaExtW)
    val (rowOff, colOff) =
      if (FmtHalfB.contains(f)) ((n.toLong >> 1), (totalN.toLong >> 1))
      else {
        val eb = cOutElemBytes(outFmt).toLong
        (n.toLong * eb, totalN.toLong * eb)
      }
    val step = cBeatAddrStep(outFmt, cDmaExtW)
    val buf = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until nTiles; row <- 0 until m; b <- 0 until beats)
      buf += cBase + nt * colOff + row * rowOff + b * step
    // ---- 自证不变式（金锚构造期即炸，早于 simulate；严禁注释掉、严禁改成 assert 关掉）----
    // 三角式的二阶项 nt×(nt−1)/2×cColOff 在 nt=1 恒为 0，故 nt=1 首拍对三角化回退
    // 不敏感；必须同时钉住 nt=1 与 nt=2 两个块首拍，才能拦住「只修对第一轮」的假修复。
    //
    // 【#136-KS1 证伪边界：m==1 ∧ nTiles==2 是三角化回退的「可证明盲区」】
    //   机理：三角式 = base + nt×(m−1)×rowOff + nt×(nt+1)/2×colOff + m×rowOff + b×step。
    //   m==1 时一阶项 nt×(m−1)×rowOff 恒为 0；二阶项系数 nt×(nt+1)/2 在 nt=1 恰为 1，
    //   与绝对式的 nt 系数在 nt=1 也恰为 1（两者仅在 nt>=2 才分歧：三角 3 vs 绝对 2）。
    //   而 nTiles==2 ⇒ nt 只取 {0,1} ⇒ 两式产出【逐拍完全相同】的序列，不存在任何
    //   可区分它们的不变式（provably blind，不是「暂未想到反例」）。
    //   可达性：HiveDebugModel.validate 只要求 m>0，故 `-Dm=1 -Dn=32` 是合法配置。
    //   ★ 声明：m==1 不在本护栏覆盖域内。要覆盖 nt=2 的二阶项必须同时满足 m>=2 且
    //     nTiles>=3（永久护栏用例 = HiveCoreDebugCase 组(f)：m=32, N=48）。
    //   ★ nt=2 那条 require 的守卫【不得】为了覆盖 m==1 而放宽到 nTiles>=2：其下标
    //     buf(2*m*beats) 在 m==1 ∧ nTiles==2 时 = 2*beats，而序列长度恰为
    //     nTiles*m*beats = 2*beats ⇒ IndexOutOfBoundsException，把「盲区」变成「崩溃」，
    //     无任何诊断价值。
    //   ★ m==1 情形改由 nt=1 那条的 `colOff > 0` 合取项兜底（下标安全性已核：
    //     nTiles>=2 ⇒ 长度 nTiles*m*beats >= 2*m*beats > m*beats，恒在界内）。
    // 【#136-CS1 报错文案中性化】原文案把成因锁死为「三角累积回退」。若将来改动的是
    //   遍历序或下标口径（而非闭式本身），也会以同一文案报错、导致成因误标，故补中性提示。
    if (nTiles >= 2)
      require(buf(m * beats) == cBase + colOff && colOff > 0,
        f"cAddrSeqDense 自证失败：nt=1 块首拍应为 base+cColOff=0x${cBase + colOff}%X，" +
        f"实得 0x${buf(m * beats)}%X（m=$m n=$n totalN=$totalN outFmt=$outFmt beats=$beats " +
        f"colOff=$colOff）" +
        "——可能成因（逐条排除）：(a) 期望式已被三角累积回退污染（禁止以 RTL 行为为拟合目标）；" +
        "(b) 遍历序/下标口径被改动，请核对循环头 nt 外 × row 中 × b 内 是否仍成立、" +
        "以及块首拍下标 m*beats 是否仍等于一个 n-tile 块的拍数；" +
        "(c) colOff<=0（布局派生退化，会让等式塌缩成 buf(m*beats)==base 的恒等式 → 假绿）")
    if (nTiles >= 3)
      require(buf(2 * m * beats) == cBase + 2 * colOff,
        f"cAddrSeqDense 自证失败：nt=2 块首拍应为 base+2*cColOff=0x${cBase + 2 * colOff}%X，" +
        f"实得 0x${buf(2 * m * beats)}%X（m=$m n=$n totalN=$totalN outFmt=$outFmt beats=$beats）" +
        "——可能成因（逐条排除）：(a) 期望式已被三角累积回退污染（nt=2 才能暴露二阶项 " +
        "nt*(nt+1)/2 与绝对式 nt 的分歧：3 vs 2），禁止以 RTL 行为为拟合目标；" +
        "(b) 遍历序/下标口径被改动，请核对循环头 nt 外 × row 中 × b 内 是否仍成立、" +
        "以及块首拍下标 2*m*beats 是否仍等于两个 n-tile 块的拍数")
    buf.toSeq
  }

  /** 256-bit 密集拍 → cResult 元素矩阵公共解析器（四套件共用，杜绝复制粘贴共模错误）。
    *
    * 输入 = dmaC 写回日志（按硬件发出顺序 (addr, data)，顺序必为 nt 外 × m 中 ×
    * 行内拍内；地址对账由各套件独立硬断言，本解析器只按序还原）。每拍按格式取
    * elemBits 位元素、拍内低列号在低位；拍间/折行顺序还原为 (row, col) 矩阵：
    *   globalCol = nt×totalN + beatInRow×(cDmaExtW/elemBits) + i
    * 半字节族（MXE2M1/INT4）取低 nibble（低列号低 nibble）；列 ≥ N（padding）不写。
    * 返回原始元素位模式（fp32=32-bit bits、16-bit 族=16-bit、8-bit 族=量化码/字节、
    * 半字节族=4-bit 码），数值解释归各套件既有对拍逻辑（v1 数值锚布局无关）。
    */
  def parseDenseCWriteLog(log: Seq[(Long, BigInt)], outFmt: DataFormat.Type, m: Int, n: Int,
                          totalN: Int, cDmaExtW: Int): Array[Array[Long]] = {
    val elemBits = cOutElemBits(outFmt)
    val beatsPerRow = cBeatsPerRow(outFmt, totalN, cDmaExtW)
    val elemsPerBeat = cDmaExtW / elemBits
    val mask = (BigInt(1) << elemBits) - 1
    val result = Array.fill(m, n)(0L)
    var beatIdx = 0
    for ((_, data) <- log) {
      val nt = beatIdx / (m * beatsPerRow)
      val inBlock = beatIdx % (m * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      for (i <- 0 until elemsPerBeat) {
        val globalCol = nt * totalN + beatInRow * elemsPerBeat + i
        if (globalCol < n)
          result(row)(globalCol) = ((data >> (elemBits * i)) & mask).toLong
      }
      beatIdx += 1
    }
    result
  }

  /** 密集行（宽 totalN×32，低列号在低位，无效高位段填零）→ 逐元素位模式序列。
    * 供「期望数据硬断言」与「行级对拍」共用：按 outFmt 选段取低 totalN×elemBits 位。 */
  def denseRowToElems(row: BigInt, outFmt: DataFormat.Type, totalN: Int): Seq[Long] = {
    val elemBits = cOutElemBits(outFmt)
    val mask = (BigInt(1) << elemBits) - 1
    (0 until totalN).map(i => ((row >> (elemBits * i)) & mask).toLong)
  }

  /** 逐元素位模式序列 → 密集行（低列号在低位，高位零填充），与上一函数互逆 */
  def elemsToDenseRow(elems: Seq[Long], outFmt: DataFormat.Type, totalN: Int): BigInt = {
    val elemBits = cOutElemBits(outFmt)
    val mask = (BigInt(1) << elemBits) - 1
    var row = BigInt(0)
    for (i <- 0 until totalN)
      row |= (BigInt(elems(i) & mask.toLong) << (elemBits * i))
    row
  }

  /** 期望发拍数据：密集行按总线位宽右移取段（与 WrOnly 发拍表达式逐字同源：
    * data = lineData >> (beatCnt×cDmaExtW) 取低 cDmaExtW 位） */
  def denseRowBeat(row: BigInt, beatInRow: Int, cDmaExtW: Int): BigInt =
    (row >> (beatInRow * cDmaExtW)) & ((BigInt(1) << cDmaExtW) - 1)

  /** fp32 bit 模式 → Float（数值对拍便捷函数，等价 fp32BitsToFloat） */
  def bitsToFp32(bits: Long): Float = fp32BitsToFloat(bits)

  // ========== P3a 软件参考模型：MX 量化（共享 E8M0 scale + 目标格式舍入） ==========
  // 独立推导期望（不抄硬件实现结构）：量化语义由「块共享 scale 约束式」出发重建：
  //   amax = 1.f × 2^(E-127)，约束 amax ≤ maxFinite × 2^(s-127)，其中
  //   maxFinite = maxMantVal × 2^(maxCodeExp-bias)；展开得 s = E_eff + bias - maxCodeExp
  //   （fp32 偏置指数已含 127，与 E8M0 bias 抵消）；元素侧 ÷2^(s-127) 即指数减。
  // 舍入判定复用上方 swRoundUp（5 模式、RMM=guard，与硬件同源同链：
  // 40→32 固定简化 RNE 规格化 + 量化运行期 rnd，两级舍入链）。
  // fmt 编码 = DataFormat 枚举 litValue，量化白名单 = isMx 族
  // {MXE4M3, MXE5M2, MXE2M1}（与 HiveOutCvt isQuantFmt = DataFormat.isMx 同源）。

  /** 量化格式参数表：复用 HiveEnum.MxFormatParams 三单例（字段语义与边界数值
    * 注释统一收敛于 HiveEnum.scala，逐字段镜像既有内联字面量；此处仅按 fmt
    * 编码选表，杜绝双源漂移）：
    *   E4M3：maxFinite=448=1.75×2^8，NaN 需 exp&mant 全1（0x7F），饱和 0x7E，
    *         maxMantVal=1.75（mant 全1 是 NaN 不可用，尾数域比较门限=0.75→0x600000）；
    *         舍入落 NaN 码改写为饱和。
    *   E5M2：maxFinite=57344=1.75×2^15，exp 全1 为 inf/nan，饱和 0x7B，
    *         nan 规范编码取 exp 全1 + 尾数高位置位（0x7E，同 fp16 规范 nan 取法）。
    *   E2M1：maxFinite=6.0=1.5×2^2，无 inf/nan 编码（exp 全1 仍为正常数），
    *         nan 输入裁定饱和（0x7），maxMantVal=1.5（尾数域比较门限=0.5→0x400000）。
    * maxNormalExp（swQuantElem 溢出判据）与 maxCodeExp 共用单字段（三格式取值
    * 恰一致：E4M3=15/E5M2=30/E2M1=3，见 HiveEnum.MxFormatParams 注释）。 */
  private def quantFmtParams(fmt: DataFormat.Type): MxFormatParams = fmt.litValue.toInt match {
    case FmtMxE4M3 => MxFormatParams.E4M3
    case FmtMxE5M2 => MxFormatParams.E5M2
    case FmtMxE2M1 => MxFormatParams.E2M1
    case _ => throw new IllegalArgumentException(
      s"swQuantBlock: 非法量化格式 fmt=$fmt（需 MXE4M3/MXE5M2/MXE2M1 = " +
        s"$FmtMxE4M3/$FmtMxE5M2/$FmtMxE2M1）")
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
    * inf→饱和、nan→目标编码（E2M1 无 nan 编码裁定饱和）、零→±0。
    *
    * @param emitSub (#151 F4) 是否产出【次正规码】。true 仅在 fmt=MXE2M1 时由 swQuantBlock
    *        传入，与 RTL 侧 HiveOutCvt.quantElem 的同名形参【一一对称】。
    *        OCP MX 规范把 E2M1 的 (e=0,m=1) 定义为 0.m × 2^(1-bias) = ±0.5，是合法可编码值
    *        （E2M1 值集 ±{0,0.5,1,1.5,2,3,4,6}）；原实现 eTgt<1 一律冲刷保号零 ⟹ 码 0x1/0x9
    *        永不发射。E4M3/E5M2 是否同步放开属独立待裁项，本轮【不扩大范围】。 */
  def swQuantElem(bits: Long, s: Int, rnd: Int, expW: Int, mantW: Int, bias: Int,
                  maxNormalExp: Int, satCode: Int, nanCode: Int,
                  nanToSat: Boolean, fixNanCode: Boolean,
                  emitSub: Boolean = false): Int = {
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
    // ----------------------------------------------------------------------
    // (#151 F4) OCP MX 次正规产出臂（仅 E2M1）——与 RTL HiveOutCvt.quantElem 的
    //   subKeep/subGuard/subSticky/subUp/subRnd/subCarry/subCode 逐行对称。
    //   设 y = |x|/S = 1.f × 2^(eTgt-1)，f = 0.m（23 位）。次正规区覆盖两个 binade：
    //     eTgt =  0 → y ∈ [0.5, 1.0)  网格 {0.5, 1.0}  中点 0.75   shift = 1
    //     eTgt = -1 → y ∈ [0.25, 0.5) 网格 {0,   0.5}  中点 0.25   shift = 2
    //     eTgt ≤ -2 → y < 0.25       幅值对称模式(RNE/RTZ/RMM) → ±0；定向模式见下方 F4-b 臂
    //   shift=1: keep = 1(原隐含位), guard = m 的 bit22,   sticky = m 的 bit21..0 是否非零
    //   shift=2: keep = 0,           guard = 1(原隐含位),  sticky = m 是否非零
    //   进位（keep+up == 2）⟹ 溢出到最小正规值 1.0 = 码 (e=1, m=0)。
    //   swRoundUp 与正规臂【同一函数】⟹ RNE/RTZ/RDN/RUP/RMM 五模式自动一致、无双源。
    // ----------------------------------------------------------------------
    if (emitSub && (eTgt == 0 || eTgt == -1)) {
      require(expW == 2 && mantW == 1,
        "swQuantElem(emitSub=true): the subnormal arm is derived for E2M1 ONLY " +
        "(keep/guard/sticky shift amounts hard-code mantW==1 and a 2-binade subnormal " +
        s"region). Got expW=$expW mantW=$mantW. Enabling subnormal output for E4M3/E5M2 " +
        "needs its own derivation and is a separate, still-open decision.")
      val isSh1  = eTgt == 0
      val keep   = if (isSh1) 1 else 0
      val guard  = if (isSh1) ((m >>> 22) & 1) else 1
      val sticky = if (isSh1) (if ((m & 0x3FFFFF) != 0) 1 else 0) else (if (m != 0) 1 else 0)
      val up     = if (swRoundUp(rnd, guard, sticky, keep & 1, sg == 1)) 1 else 0
      val subRnd = keep + up
      if (subRnd >= 2) return signPart | (1 << mantW)         // 进位 → 码 (e=1,m=0) = 1.0
      return signPart | subRnd                                // 码 (e=0,m=subRnd)：0 → ±0，1 → ±0.5
    }
    // ----------------------------------------------------------------------
    // (#151 F4-b) 定向舍入【深下溢】进位臂（仅 E2M1）——与 RTL HiveOutCvt.quantElem 的
    //   dirAway/deepSel/deepCode 逐行对称。IEEE-754 roundTowardNegative/roundTowardPositive
    //   的定义是「取不超过 / 不低于 x 的【最接近】可表示值」，它不是幅值截断 ⟹ 对任意非零 x，
    //   只要定向把幅值推离零，结果就必须是最小非零可表示值 ±0.5 = 码 (e=0,m=1)，不是保号零：
    //     RDN(2) 且 sg=1（x<0）→ -0.5·S      RUP(3) 且 sg=0（x>0）→ +0.5·S
    //   RNE/RTZ/RMM 三个幅值对称模式在 y'<0.25 时归零是正确的（0 比 0.5 更近 / 截断 /
    //   最近-ties-away）⟹ dirAway=false ⟹ 本臂不介入，码流与本臂加入前【逐位相同】。
    //   注：此处 e==0(±0/fp32 次正规) 与 e==255(inf/nan) 已在上方提前返回 ⟹ 到达本臂的
    //   必为非零有限 fp32 正规值，与 RTL 侧 !isZero && !isInf && !isNan 的守卫等价。
    // ----------------------------------------------------------------------
    if (emitSub && eTgt <= -2) {
      require(expW == 2 && mantW == 1,
        "swQuantElem(emitSub=true) deep-underflow directed-rounding arm: derived for E2M1 " +
        "ONLY (min nonzero magnitude hard-coded as code (e=0,m=1)=0.5). Got expW=$expW " +
        s"mantW=$mantW. Enabling subnormal output for E4M3/E5M2 is a separate open decision.")
      val dirAway = rnd match {
        case 2 => sg == 1        // RDN：向 -inf，仅负数被推离零
        case 3 => sg == 0        // RUP：向 +inf，仅正数被推离零
        case _ => false          // RNE(0)/RTZ(1)/RMM(4)：幅值对称，深下溢归零正确
      }
      if (dirAway) return signPart | 1                        // 码 (e=0,m=1) = ±0.5
    }
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
    * 返回 (逐元素量化码, 每 8 元素组 1 字节的 scale 序列)。blkSel：0=16/1=32
    * （【#144】MXBlockSize 收窄后新枚举 {M16=0, M32=1}；旧 0=8/1=16/2=32/3=64 已废）；
    * 列 ≥ nEff 为 padding：不进 amax、元素码输出 0。 */
  def swQuantBlock(normFp32s: Seq[Long], rnd: Int, fmt: DataFormat.Type, blkSel: Int, nEff: Int): (Seq[Int], Seq[Int]) = {
    val p = quantFmtParams(fmt)
    // maxNormalExp 与 maxCodeExp 共用 MxFormatParams 单字段（三格式取值恰一致）
    val (expW, mantW, bias, maxCodeExp, maxMant23, maxNormalExp, satCode, nanCode, nanToSat, fixNan) =
      (p.expW, p.mantW, p.bias, p.maxCodeExp, p.maxMant23.toLong, p.maxCodeExp,
        p.satCode, p.nanCode, p.nanToSat, p.fixNanCode)
    val n = normFp32s.size
    require(n % 8 == 0, s"swQuantBlock: 行元素数($n) 必须为 8 的整数倍")
    // 幅值 = 符号位清零（非负 fp32 位模式无符号比较即幅值序）；padding 列掩 0 不进 amax
    val mags = normFp32s.zipWithIndex.map { case (b, i) => if (i < nEff) (b & 0x7FFFFFFFL) else 0L }
    def maxMag(s: Seq[Long]): Long = s.reduce((a, b) => if (a >= b) a else b)
    val groups = mags.grouped(8).map(maxMag).toSeq            // 8 元素组 amax
    def merge(s: Seq[Long]): Seq[Long] =
      s.grouped(2).map(p => if (p.size == 2) maxMag(p) else p.head).toSeq
    val l16 = merge(groups); val l32 = merge(l16); val l64 = merge(l32)
    // 【#144】blkSel 新枚举：0→block16(l16)、1→block32(l32)。旧 0=8(groups)/3=64(l64) 档已随枚举删除；
    //   groups/l64 仍保留供 merge 链构造（l16=merge(groups)），不再作为可选块档。
    def amaxOf(i: Int): Long = blkSel match {
      case 0 => l16(i / 16)
      case _ => l32(i / 32)
    }
    def scaleOf(amax: Long): Int = swBlockScale(amax, maxCodeExp, bias, maxMant23)
    // (#151 F4) 次正规产出开关：仅 MXE2M1 打开，与 RTL 侧 HiveOutCvt 中「q2Seq 是唯一传
    //   emitSub=true 的例化点」严格对称。E4M3/E5M2 恒 false ⟹ 金锚这两路的码流与 #151
    //   之前【逐位相同】。本行是 swQuantBlock 在 #151 的唯一新增；blkSel/amax 层级/
    //   scaleBytes 分组/padding 掩码语义一字未动。
    val emitSubFmt: Boolean = fmt.litValue.toInt == FmtMxE2M1
    val codes = normFp32s.zipWithIndex.map { case (b, i) =>
      if (i >= nEff) 0                                        // padding 列输出 0（不参与量化）
      else swQuantElem(b, scaleOf(amaxOf(i)), rnd,
        expW, mantW, bias, maxNormalExp, satCode, nanCode, nanToSat, fixNan,
        emitSub = emitSubFmt)
    }
    val scaleBytes = (0 until n / 8).map { g =>
      // 【#144】每 8 元素组 g 输出 1 字节 scale，取所属块的 amax：block16→l16(g/2)、block32→l32(g/4)。
      val amax = blkSel match {
        case 0 => l16(g / 2)
        case _ => l32(g / 4)
      }
      scaleOf(amax)
    }
    (codes, scaleBytes)
  }
}

/** STATUS 轮询 poke/peek 助手 trait：与 ChiselSim 一起混入测试类。
  *
  * poke/peek 语法经 PeekPokeAPI（ChiselSim 的父 trait）线性化共享——本 trait
  * 同样 extends PeekPokeAPI，与 ChiselSim 混入同一 spec 时线性化合并为同一
  * 实例，无隐式歧义。提供两类原语：
  *   - pokeStatusRead：poke 一次 REG_READ(STATUS) 读命令（不 step、不管 fire，
  *     供跨拍状态机发起拍使用）；
  *   - statusPollStep：单拍轮询状态机推进（每主循环拍调用一次，函数式返回
  *     新状态与本次读到的 STATUS——不额外 step，主循环的 DMA 供数 poke 每拍
  *     由循环体照常维护，轮询不干扰供数节奏）。
  * 状态机语义（三态）：
  *   pollSt=0 发起/HOLD：poke 读命令；cmd.ready = !respValid（EXECUTE 立即
  *     响应至多占线 1 拍），ready 拍即 fire → 次拍进入读拍；未 fire 则保持。
  *   pollSt=1 读拍：cmd.valid 用后拉低；fire 次拍 resp.valid=1（锁存 fire 拍
  *     statusReg 快照），resp.ready 恒高下恰好消费一次，返回解码结果。
  *   pollSt=2 冷却：计数 POLL_PERIOD 拍后回发起态（减少命令流量）。
  * done/err 均为粘滞位（新 EXECUTE 拍才清除），晚读不漏。
  */
trait HiveStatusPollApi extends chisel3.simulator.PeekPokeAPI {

  /** poke 一次 REG_READ(STATUS) 读命令（全字段 poke，与 sendCmd 同范式） */
  def pokeStatusRead(cmd: Stream[HiveCoreCmd]): Unit = {
    cmd.valid.poke(true.B)
    cmd.payload.op.poke(HiveCoreOp.REG_READ)
    cmd.payload.reg0Addr.poke(HiveSimCommon.STATUS_REG_ADDR.U)
    cmd.payload.reg1Addr.poke(0.U)
    cmd.payload.data0.poke(0.U)
    cmd.payload.data1.poke(0.U)
  }

  /** 单拍轮询状态机推进：返回 (新 pollSt, 新 pollCool, 本次读到的 STATUS)。
    * 调用方在 DMA 主循环体内每拍调用一次（循环体末尾 step 前），状态变量
    * pollSt/pollCool 由调用方持有。读到 STATUS 的拍返回 Some(...)，其余 None。 */
  def statusPollStep(cmd: Stream[HiveCoreCmd],
                     resp: Stream[HiveCoreResp],
                     pollSt: Int, pollCool: Int): (Int, Int, Option[HiveSimCommon.HiveStatusBits]) = {
    if (pollSt == 0) {
      pokeStatusRead(cmd)
      // cmd.ready = !respValid：EXECUTE 立即响应占线时本拍不 fire，
      // cmd.valid 粘滞 1，下拍重复 poke（幂等）直至 fire
      if (cmd.ready.peek().litToBoolean) (1, 0, None) else (0, 0, None)
    } else if (pollSt == 1) {
      // fire 已在上拍末边沿发生：本拍 resp.valid=1（respData 为 fire 拍锁存的
      // statusReg 快照）；读后拉低 cmd.valid（用后拉低，防命令残留）
      cmd.valid.poke(false.B)
      if (resp.valid.peek().litToBoolean)
        (2, HiveSimCommon.POLL_PERIOD,
          Some(HiveSimCommon.decodeStatus(resp.payload.data.peek().litValue)))
      else
        (1, 0, None)
    } else {
      val cool = pollCool - 1
      if (cool <= 0) (0, 0, None) else (2, cool, None)
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

/** 始终开启 FST 波形的 trait（不依赖 -DemitFst flag）。
  *
  * 与 HiveFstSim 的差异：HiveFstSim 需显式传 -DemitFst=1 才 dump；本 trait
  * 无条件 enableWavesAtTimeZero=true，仍注册 emitFst 键仅为命令行兼容。
  *
  * 迁移记录（taskId=143）：原定义于 HiveCoreParamDebugSpec.scala（该文件已被
  * 用户删除），因 HiveCoreQuickSpec 仍依赖，逐字迁移至本公共文件；原文件
  * HEAD 版本见 git show HEAD:src/hive/scala/test/HiveCoreParamDebugSpec.scala。
  */
trait HiveAlwaysFstSim { this: HasCliOptions =>
  implicit val fstSimulator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
        .withTraceStyle(Some(
          svsim.verilator.Backend.CompilationSettings
            .TraceStyle(svsim.verilator.Backend.CompilationSettings.TraceKind.Fst())
        ))
    )
  // 注册 emitFst 键（兼容），但无论是否传入都启用波形
  private def enableWaves(options: svsim.CommonCompilationSettings) =
    options.copy(simulationSettings = options.simulationSettings.copy(enableWavesAtTimeZero = true))
  addOption(HasCliOptions.CliOption.flag(
    name = "emitFst",
    help = "FST waves are ALWAYS enabled in this spec (flag accepted for compatibility only)",
    updateCommonSettings = enableWaves,
    updateUnsetCommonSettings = enableWaves
  ))
}
