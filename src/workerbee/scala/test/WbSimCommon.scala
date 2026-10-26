/** WorkerBee 仿真测试公共工具：FP16 位模式转换、矩阵文件落盘、
  * FP32 bit 解析与 FST 波形拷贝。由 WorkerBeeSimCase 与
  * WorkerBeeSimCaseLoadWSpec 共用（自两者逐字等价抽取，
  * 差异处以功能更全的一份为准）。
  * 格式编码统一经 WbEnum.DataFormat 枚举派生常量驱动（见 object 开头
  * 常量区）；【任务104】fmt 相关公开 API 入参已由 Int 编码迁移为
  * DataFormat.Type 枚举（避免枚举再改编号后调用点静默错位）；控制字
  * 位域打包等仍需整型的场景由调用方以 fmt.litValue.toInt 收敛。
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest.HasCliOptions
import java.io._
import java.nio.file._

object WbSimCommon {

  // ========== DataFormat 枚举派生常量（格式编码唯一源） ==========
  // OutFormat/DataFormat 已合并为单一 DataFormat 枚举（src/workerbee/scala/main/
  // WbEnum.scala），成员声明即隐式 id 顺延：FP32=0 FP16=1 BF16=2 MXE4M3=3
  // MXE5M2=4 MXE2M1=5 INT32=6 INT16=7 INT8=8 INT4=9 FP8E4M3=10 FP8E5M2=11。
  // 【P2 纯 FP8】FP8 两码点不进下方任何 Fmt 分档集合：本组集合服务【输出格式】
  // 域（cOutElemBits 等），而 cFmt=FP8 被硬件 errCode3 链尾臂运行期拒绝 ⟹
  // 该域永不合法接收 FP8，落默认臂（32-bit 档）即原 reserved 语义。本 object 的全部
  // 格式编码比较/分档均经此常量区驱动，不再出现裸数字。
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

  // 格式族集合（Scala 级，与 WbEnum 谓词语义对齐，用于元素位宽/字节分档）：
  //   4B 族 = is4B = {FP32,INT32}；2B 族 = is2B ∪ {INT16}（INT16 输出同为
  //     2B/元素，与 WbOutCvt 选段 packedInt16 同档；is2B 谓词仅覆盖输入侧
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
    // 【退化位宽守护，勿删】bitsPerElem<=0 时 (bitsPerElem+3)/4 == 0，下面那行会拼出格式串
    //   "%00X"：java.util.Formatter 把 "00" 当成两个 '0' flag，抛
    //   java.util.DuplicateFormatFlagsException: Flags = '0'（实测复现）。触发者是亚字节族
    //   （MXE2M1/INT4）按 bytes 口径传 memElemBytes×8 = 0×8 = 0 的调用点，崩在【失败落盘】
    //   路径上，会把真实失配原因整条盖掉、只剩一个看不出所以然的 Formatter 异常。
    //   兜底为 1 位：bitsPerElem>=1 时 math.max(1, …) 是恒等变换 ⟹ 所有正常用例的
    //   零填充位宽、大写 hex、空格分隔、行序【逐字不变】，本守护只消除崩溃、不改输出语义。
    val hexDigits = math.max(1, (bitsPerElem + 3) / 4)
    val mask = (1L << bitsPerElem) - 1
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => String.format(s"%0${hexDigits}X", Long.box(v.toLong & mask))).mkString(" "))
    }
    pw.close()
  }

  /** 将 BigInt 矩阵写入文本文件 */
  def writeBigIntMatrixHex(path: String, matrix: Array[Array[BigInt]], bitsPerElem: Int): Unit = {
    // 【退化位宽守护】与上方 writeMatrixHex 同源：亚字节族(MXE2M1/INT4) 按 bytes 口径传入时
    //   bitsPerElem=0 ⟹ (bitsPerElem+3)/4=0。writeMatrixHex 会拼出非法格式串 "%00X" 抛
    //   java.util.DuplicateFormatFlagsException；本函数走 padTo(hexDigits,'0')，hexDigits=0 则
    //   退化为不补零（输出失去固定位宽）。兜底为 1 位：bitsPerElem>=1 时恒等、正常用例输出不变。
    val hexDigits = math.max(1, (bitsPerElem + 3) / 4)
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
    * @param traceTarget 目标文件路径（如 sim/workerbee_sim.fst）
    * @param tag         日志前缀（如 WorkerBeeSimCase）
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

  // 寄存器地址与解码由接口契约定义，STATUS 不再承载完成/错误信息。
  val M_REG_ADDR = 0x00
  val NK_REG_ADDR = 0x01
  val A_REG_ADDR = 0x02
  val B_REG_ADDR = 0x03
  val C_REG_ADDR = 0x04
  val CONTROL_REG_ADDR = 0x05
  val SCALE_A_REG_ADDR = 0x06
  val SCALE_B_REG_ADDR = 0x07
  val SCALE_C_REG_ADDR = 0x08
  val BIAS_REG_ADDR = 0x09
  val RQM_REG_ADDR = 0x0A
  val RQ_PARAM_REG_ADDR = 0x0B
  val START_REG_ADDR = 0x10
  val STATUS_REG_ADDR = 0x1E
  val IRQ_RAW_REG_ADDR = 0x30
  val IRQ_MASK_REG_ADDR = 0x31
  val IRQ_MASKED_REG_ADDR = 0x32
  val IRQ_CLEAR_REG_ADDR = 0x33
  val ERROR_CODE_REG_ADDR = 0x34
  val IRQ_ALL = 0x7FF
  val POLL_PERIOD: Int = 16

  final case class WbStatusBits(busy: Boolean, inFlight: Boolean, progress: Int)
  def decodeStatus(data: BigInt): WbStatusBits = WbStatusBits(
    busy = data.testBit(0), inFlight = data.testBit(2),
    progress = ((data >> 16) & 0xFFFF).toInt
  )

  /** IRQ_RAW 独立解码；config code 必须另读 ERROR_CODE，不拼接伪 STATUS。 */
  final case class WbIrqBits(raw: BigInt) {
    def done: Boolean = raw.testBit(0)
    def configError: Boolean = raw.testBit(1)
    def dmaErrors: BigInt = (raw >> 2) & 0xFF
    def writeConflict: Boolean = raw.testBit(10)
    def anyError: Boolean = (raw & 0x7FE) != 0
  }
  def decodeIrq(data: BigInt): WbIrqBits = WbIrqBits(data)

  // ========== MX (MXFP8 / MXFP4) 转换 helper ==========
  // 逐字镜像 Fp32.mxToFp32（src/workerbee/scala/main/Fp32.scala）的数值语义，
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
    * 从而 B 权重经 loadW 走 a 链时也不会触发 MX 转换（原 WbCvtOpMx，已随 FusionMac
    * 重构删除、现由 FpCvt 的 mx8 cvt 承接）的 aFmt inf/nan 断言。
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
  // 与 WbOutCvt 硬件（roundUp16 / fp32ToFp16Rnd / fp32ToBf16Rnd）及
  // Fp32.fpNormalize40to32 逐位同构，作为 WbOutCvtSpec 与 outFmt 仿真用例的
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
  // 与硬件 P0-P2 新架构逐拍同源（WorkerBee calcConfig 单式 + WorkerBeeDmaWrOnly
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
  // 编码 = WbEnum.DataFormat 枚举 litValue（OutFormat/DataFormat 已合并为单一
  //   枚举，成员声明即隐式 id：0=FP32 1=FP16 2=BF16 3=MXE4M3 4=MXE5M2 5=MXE2M1
  //   6=INT32 7=INT16 8=INT8 9=INT4）；未知编码不在任何格式族集合 → 32-bit 档回落。

  /** outFmt（DataFormat 枚举）→ 元素位宽（32/16/8/4），与硬件
    * WorkerBeeCalcConfig cWriteBytes 谓词链（is4B/is2B/is1B/otherwise）同源 */
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
    *   RTL（WorkerBeeCalcConfig）：cBeatAddrStep = if (cWriteBytes > cDmaBytes) cDmaBytes
    *     else cWriteBytes，其中 cWriteBytes = totalN×cEb（半字节族 = totalN/2）、
    *     cDmaBytes = cDmaExtW/8。
    *   ★ 结论：在【beats>1 的全部档位】两式恒等；1B 与半字节族数值分歧，但该分歧
    *     【可证明永不可观测】。不得据此「对齐」数值（对齐会在缩位档引入真错）。
    *   证明：beats = ceil(totalN×elemBits / cDmaExtW) > 1 ⟺ totalN×elemBits > cDmaExtW
    *     ⟺ cWriteBytes > cDmaBytes（cWriteBytes = totalN×elemBits/8、cDmaBytes =
    *     cDmaExtW/8，同乘 8）。故 beats>1 时 RTL 取 cDmaBytes = cDmaExtW/8，而金锚在
    *     整字节族满足 elemBits = 8×cEb，(cDmaExtW/elemBits)×cEb = cDmaExtW/8，两式相等。
    *     反向：WorkerBeeConfig 强制 cDmaExtW ∈ [totalN×8, totalN×32]，故 elemBits<=8 时
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
    * 布局推导，【禁止】以 WorkerBeeDmaWrOnly 的 RTL 行为为拟合目标。推导如下——
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
    * WorkerBeeDmaScaleWr 同构），本式随之回到张量语义绝对式。下方两条 require 是本式
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
    //   可达性：WbDebugModel.validate 只要求 m>0，故 `-Dm=1 -Dn=32` 是合法配置。
    //   ★ 声明：m==1 不在本护栏覆盖域内。要覆盖 nt=2 的二阶项必须同时满足 m>=2 且
    //     nTiles>=3（永久护栏用例 = WorkerBeeDebugCase 组(f)：m=32, N=48）。
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

  // ========== (#166) AXI byte lane 契约：C 写回逐拍 (addr, data, strb) lane 级审计 ==========
  // 【为何需要】旧 TB 口径把 dmaC 写回日志记成 (addr, data) 二元组、且解析器【完全忽略
  //   addr】：data 一律按「laneOffset=0」解释。这正是 main 侧「strb 已按 addrShiftBytes 左移、
  //   wdata 直连低位未同源移位」缺陷能长期潜伏的原因 —— TB 与 RTL 犯了同一个错（都假设
  //   数据在总线字低位），于是互相背书、恒真假绿。
  // 【新口径】① 日志升级为 (addr, data, strb) 三元组；② addr 推出期望 lane 窗口，与 strb 做
  //   【双向】对账（addr→strb 期望、strb→addr 一致性，不符即报错而不静默放行）；③ 元素级
  //   解析先按 laneOffset 反移位、再按 strb 逐字节门控：只有被选通的 lane 参与数据比对，
  //   未选通 lane 不做数据断言但【必须为 0】（依据见 auditCBeatLanes 第 (3) 条）。
  // 【金锚铁律】本段全部期望值（cLaneOffset / cLanesPerBeat / cExpStrb）均由【张量语义 +
  //   AXI byte lane 契约】独立推导，不读 RTL 的 cDmaDmaBeatTar 档位、不以 RTL 行为为拟合目标。

  /** dmaC write-back single-beat log triple: (addr, data, strb). [#166] Old format was
    * (addr, data) pair -- strb was never visible to TB; the triple promotes strb to
    * first-class citizen, enabling lane-level cross-check.
    * scaleC channel (dmaCScale) is NOT in this contract's scope: its port width = 1 byte
    * (rowW = cScaleRowW = totalN/2 bits => totalN=16 gives rowW=8 bits=1 byte) => the
    * lane window degenerates to a single lane, shift is always 0, data/strb mismatch is
    * structurally impossible. It is not "exempt from the contract" -- the contract
    * trivially degenerates on a single-lane port. (#170-W3: corrected from the old
    * rationale "strb is constant full-select" which was factually wrong for totalN>=32.) */
  type CWriteBeat = (Long, BigInt, BigInt)

  /** 总线字字节数 = 一个 beat 覆盖的 byte lane 数 */
  def cDmaBytes(cDmaExtW: Int): Int = cDmaExtW / 8

  /** 本拍在总线字内的起始 byte lane 偏移 = addr mod dmaBytes（AXI 契约，纯地址推导，
    * 与 main 侧 addrShiftBytes 同式但独立书写：金锚不得引用 RTL 中间量） */
  def cLaneOffset(addr: Long, cDmaExtW: Int): Int =
    (addr % cDmaBytes(cDmaExtW)).toInt

  /** 本拍【应当】被选通的 byte lane 数（张量语义推导）：
    *   一个 n-tile 行共占 cWriteBytes = ceil(totalN×elemBits/8) 字节；
    *   行内第 beatInRow 拍覆盖字节区间 [beatInRow×dmaBytes, cWriteBytes) ∩ 一个总线字
    *   ⟹ lanes = clamp(cWriteBytes - beatInRow×dmaBytes, 0, dmaBytes)。
    * 单拍满档（beats=1）与 beats>1 的均分档均与上式一致；beatInRow 默认 0。
    * 【备注】若将来出现 cWriteBytes 不是 dmaBytes 整数倍的配置（当前 totalN∈{16,32} ×
    *   elemBits∈{4,8,16,32} 下 cWriteBytes∈{8,16,32,64,128}，永不部分占用），本式会给出
    *   比 RTL 固定档位更窄的末拍 lanes ⟹ 报 STRB-MISMATCH，那是【既有「strb 档位不按
    *   N 尾数细化」缺陷族的真阳性】，不得反向拟合金锚去迁就它。 */
  def cLanesPerBeat(outFmt: DataFormat.Type, totalN: Int, cDmaExtW: Int,
                    beatInRow: Int = 0): Int = {
    val nb = cDmaBytes(cDmaExtW)
    val cWriteBytes = (totalN * cOutElemBits(outFmt) + 7) / 8
    math.max(0, math.min(cWriteBytes - beatInRow * nb, nb))
  }

  /** 由 addr（+ 拍内序号）推出的期望 strb：从 laneOffset 起连续 lanes 位置 1，其余置 0。
    * 这就是 AXI byte lane 契约的完整形式表达：wstrb 第 i 位 ↔ wdata[8i+7:8i]。 */
  def cExpStrb(addr: Long, outFmt: DataFormat.Type, totalN: Int, cDmaExtW: Int,
               beatInRow: Int = 0): BigInt = {
    val nb = cDmaBytes(cDmaExtW)
    val off = cLaneOffset(addr, cDmaExtW)
    val lanes = cLanesPerBeat(outFmt, totalN, cDmaExtW, beatInRow)
    (((BigInt(1) << lanes) - 1) << off) & ((BigInt(1) << nb) - 1)
  }

  /** Single-beat lane-level audit. Returns violation messages (empty = all green).
    * All messages are ASCII English.
    *
    * PRECONDITION (W9): The caller MUST independently hard-assert that `addr` equals
    *   the golden-anchor expected address for this beat index (e.g. via expCAddrSeq).
    *   This function derives lane offsets from the supplied `addr`; if addr itself is
    *   wrong, the derived expectations are meaningless. The function does NOT validate
    *   addr correctness -- that is the caller's obligation.
    *
    * Four violation kinds (each with its RTL-independent derivation):
    *  (1) STRB-OVERFLOW: laneOffset + lanes > dmaBytes => strb left-shift truncated
    *      by bus-word width, data high bits pushed out. This beat necessarily loses
    *      data. Belongs to the existing "strb granularity does not refine by N
    *      remainder" defect family (#166 scope-out), but must not silently pass.
    *      (W8) OVERFLOW takes priority over MISMATCH -- they are mutually exclusive:
    *      when overflow occurs, the expected strb is itself unrepresentable in
    *      dmaBytes bits, so MISMATCH is a tautological consequence, not independent
    *      information.
    *  (2) STRB-MISMATCH: addr-derived expected lane window differs from actual strb
    *      bit-by-bit. Bidirectional cross-check: catches both "strb not shifted"
    *      (lanes stuck at lane0) and "strb shifted by wrong amount".
    *      Only reported when OVERFLOW does NOT fire (W8 mutual exclusion).
    *  (3) UNSEL-NONZERO: non-zero byte on an unselected lane. Derivation (tensor
    *      semantics, not RTL fitting): dense row carries only low totalN*elemBits
    *      bits of data, everything above is zero-fill (elemsToDenseRow contract,
    *      i.e. the four-suite shared golden row model); left-shift by laneOffset
    *      pads low bits with zero => byte lanes outside the selected window are
    *      always 0. This is a falsifiable semantic inference, not RTL fitting.
    *      Per task spec: unselected lanes are NOT data-asserted (no golden content
    *      comparison), but ARE confirmed zero.
    *  (4) SEL-BYTE-MISMATCH: only active when caller provides golden expected data
    *      (expData); compares selected byte lanes byte-by-byte. Unselected lanes
    *      do not participate.
    * Each kind reports at most capPerKind detail messages per beat (prevents log
    * explosion); excess is summarized by count.
    *
    * NOTE (W8): `violationCount` (the size of the returned Seq) serves ONLY as a
    *   red/green threshold, NOT as a quantitative metric of "how many independent
    *   defects exist". A single misconfigured beat can produce multiple messages
    *   across kinds; conversely, OVERFLOW suppresses MISMATCH to avoid double-count. */
  def auditCBeatLanes(beatIdx: Int, addr: Long, data: BigInt, strb: BigInt,
                      outFmt: DataFormat.Type, totalN: Int, cDmaExtW: Int,
                      beatInRow: Int = 0, expData: Option[BigInt] = None,
                      capPerKind: Int = 4): Seq[String] = {
    val nb = cDmaBytes(cDmaExtW)
    val off = cLaneOffset(addr, cDmaExtW)
    val lanes = cLanesPerBeat(outFmt, totalN, cDmaExtW, beatInRow)
    val exp = cExpStrb(addr, outFmt, totalN, cDmaExtW, beatInRow)
    val bad = scala.collection.mutable.ArrayBuffer[String]()
    if (off + lanes > nb)
      bad += f"STRB-OVERFLOW beat#$beatIdx addr=0x$addr%X laneOff=$off lanes=$lanes dmaBytes=$nb"
    else if (strb != exp)
      bad += f"STRB-MISMATCH beat#$beatIdx addr=0x$addr%X laneOff=$off lanes=$lanes " +
             f"beatInRow=$beatInRow strb=0x$strb%X exp=0x$exp%X"
    var nUnsel = 0; var nSel = 0
    for (b <- 0 until nb) {
      val sel = strb.testBit(b)
      val hwB = (data >> (8 * b)) & 0xFF
      if (!sel) {
        if (hwB != 0) {
          nUnsel += 1
          if (nUnsel <= capPerKind)
            bad += f"UNSEL-NONZERO beat#$beatIdx addr=0x$addr%X lane=$b byte=0x$hwB%02X strb=0x$strb%X"
        }
      } else expData.foreach { e =>
        val exB = (e >> (8 * b)) & 0xFF
        if (hwB != exB) {
          nSel += 1
          if (nSel <= capPerKind)
            bad += f"SEL-BYTE-MISMATCH beat#$beatIdx addr=0x$addr%X lane=$b hw=0x$hwB%02X exp=0x$exB%02X"
        }
      }
    }
    if (nUnsel > capPerKind) bad += s"UNSEL-NONZERO beat#$beatIdx total=$nUnsel lanes (only first $capPerKind listed)"
    if (nSel > capPerKind) bad += s"SEL-BYTE-MISMATCH beat#$beatIdx total=$nSel lanes (only first $capPerKind listed)"
    bad.toSeq
  }

  /** (#166) 密集拍解析 + lane 级审计的汇总结果。
    * @param matrix        还原出的 m×n 元素位模式矩阵（仅选通 lane 写入）
    * @param violations    全部 lane 级违规消息（空 = 全绿）
    * @param beats         实收拍数
    * @param lanesSelected 被 strb 选通的 byte lane 总数（参与数据比对）
    * @param lanesExempt   未选通的 byte lane 总数（不做数据断言，但已校验其为 0）
    * @param elemsWritten  实际写入矩阵的元素数
    * @param elemsUnsel    因 strb 未选通而豁免的元素数（#166 前会被当成有效数据误写）
    * @param elemsPad      因 globalCol >= n（列 padding）而豁免的元素数 */
  final case class DenseCParse(matrix: Array[Array[Long]], violations: Seq[String],
                               beats: Int, lanesSelected: Int, lanesExempt: Int,
                               elemsWritten: Int, elemsUnsel: Int, elemsPad: Int) {
    /** lane 级违规条数（供调用方直接折入 mismatch 计数） */
    def violationCount: Int = violations.size
    /** 一行式统计摘要（英文 ASCII，供各 suite println） */
    def laneSummary: String =
      s"beats=$beats lanesSel=$lanesSelected lanesExempt=$lanesExempt " +
      s"elemsWritten=$elemsWritten elemsUnselExempt=$elemsUnsel elemsPadExempt=$elemsPad " +
      s"laneViolations=$violationCount"
  }

  /** 256-bit 密集拍 → cResult 元素矩阵公共解析器（四套件共用，杜绝复制粘贴共模错误）。
    *
    * 【#166 升级：strb 逐字节校验口径】输入 = dmaC 写回日志三元组 (addr, data, strb)，
    * 按硬件发出顺序（必为 nt 外 × m 中 × 行内拍内）。每拍处理三步：
    *   ① lane 级审计 auditCBeatLanes（addr↔strb 双向对账 + 未选通 lane 零填充核查，
    *     expDataAt 给出时再加选通 lane 的逐字节数据比对）；
    *   ② 【反移位】aligned = data >> (laneOffset×8)，把本拍数据还原成「laneOffset=0」口径
    *     的密集段 —— 这正是 main 侧左移的逆运算，使元素提取下标与金锚 denseRowBeat 同口径；
    *   ③ 【strb 门控】元素 i 的基字节 lane（绝对）= laneOffset + (elemBits×i)/8；该 lane 未被
    *     选通 ⟹ 本拍不承载该元素，【跳过不写】（计入 elemsUnsel）。
    * 列映射仍为 globalCol = nt×totalN + beatInRow×(cDmaExtW/elemBits) + i；半字节族
    * （MXE2M1/INT4）取低 nibble（低列号低 nibble）；列 ≥ N（padding）不写。
    * 返回原始元素位模式（fp32=32-bit bits、16-bit 族=16-bit、8-bit 族=量化码/字节、
    * 半字节族=4-bit 码），数值解释归各套件既有对拍逻辑（v1 数值锚布局无关）。
    *
    * 【附带修掉的潜伏粗糙】旧实现只按 `globalCol < n` 门控、不按 tile 边界门控：
    * MXE2M1（elemsPerBeat=64 > totalN=16）下 nt=0 那拍的 i=16..31 会写到属于 nt=1 的列，
    * 靠后续 nt=1 的拍覆盖而侥幸正确。新的 strb 门控（lanes=8 ⟹ 仅 i=0..15 被选通）
    * 从语义上直接拦掉了越 tile 写入。
    *
    * @param expBeats  Expected beat count (= nTiles*m*beatsPerRow); checked as BEAT-COUNT
    * @param expElems  (#170-W6) Expected number of elements written to the matrix (= m*n
    *                  for a fully covered dense output). When provided and elemsWritten
    *                  does not match, an ELEMS-WRITTEN violation is appended. This closes
    *                  the gap where #166 treated elemsWritten as diagnostic-only while
    *                  build/166 cited it as evidence against "fewer writes -> green".
    * @param expDataAt Golden expected data function (beatIdx -> expected data with lane
    *                  offset already applied); enables selected-lane byte-wise comparison
    *
    * PRECONDITION (W9): The `addr` field of each CWriteBeat in `log` MUST have been
    *   independently hard-asserted against the golden expected address sequence (e.g.
    *   expCAddrSeq) by the caller BEFORE or ALONGSIDE calling this function. The internal
    *   reverse-shift `data >> (laneOffset * 8)` derives laneOffset from addr; if addr is
    *   wrong, the entire element extraction is silently corrupted.
    *
    * Row-internal beat index (beatInRow) is derived internally from beatIdx
    * (% (m*beatsPerRow) then % beatsPerRow), consistent with caller traversal order. */
  def parseDenseCWriteLogChecked(log: Seq[CWriteBeat], outFmt: DataFormat.Type, m: Int, n: Int,
                                 totalN: Int, cDmaExtW: Int, expBeats: Option[Int] = None,
                                 expDataAt: Option[Int => BigInt] = None,
                                 expElems: Option[Int] = None): DenseCParse = {
    val elemBits = cOutElemBits(outFmt)
    val beatsPerRow = cBeatsPerRow(outFmt, totalN, cDmaExtW)
    val elemsPerBeat = cDmaExtW / elemBits
    val nb = cDmaBytes(cDmaExtW)
    val mask = (BigInt(1) << elemBits) - 1
    val nTiles = (n + totalN - 1) / totalN
    val result = Array.fill(m, n)(0L)
    val bad = scala.collection.mutable.ArrayBuffer[String]()
    var lanesSel = 0; var lanesEx = 0
    var elemsW = 0; var elemsUnsel = 0; var elemsPad = 0
    var beatIdx = 0
    for ((addr, data, strb) <- log) {
      val inBlock = beatIdx % (m * beatsPerRow)
      val beatInRow = inBlock % beatsPerRow
      bad ++= auditCBeatLanes(beatIdx, addr, data, strb, outFmt, totalN, cDmaExtW,
                              beatInRow, expDataAt.map(f => f(beatIdx)))
      val off = cLaneOffset(addr, cDmaExtW)
      for (b <- 0 until nb) if (strb.testBit(b)) lanesSel += 1 else lanesEx += 1
      // ② 反移位：与 main 侧 `data << (addrShiftBytes << 3)` 互逆
      val aligned = data >> (off * 8)
      val nt = beatIdx / (m * beatsPerRow)
      val row = inBlock / beatsPerRow
      for (i <- 0 until elemsPerBeat) {
        val globalCol = nt * totalN + beatInRow * elemsPerBeat + i
        // ③ strb 门控：元素 i 的基字节 lane（绝对下标）
        val sel = strb.testBit(off + (elemBits * i) / 8)
        if (!sel) elemsUnsel += 1
        else if (globalCol < n) {
          result(row)(globalCol) = ((aligned >> (elemBits * i)) & mask).toLong
          elemsW += 1
        } else elemsPad += 1
      }
      beatIdx += 1
    }
    expBeats.foreach { eb =>
      if (log.size != eb)
        bad += s"BEAT-COUNT hw=${log.size} exp=$eb (nTiles=$nTiles m=$m beatsPerRow=$beatsPerRow)"
    }
    // (#170-W6) Element count assertion: elemsWritten must equal m*n for a fully
    //   covered dense output. Catches "fewer writes -> green" (e.g. strb gating
    //   silently skipping elements that should have been written).
    expElems.foreach { ee =>
      if (elemsW != ee)
        bad += s"ELEMS-WRITTEN hw=$elemsW exp=$ee (m=$m n=$n; elemsUnsel=$elemsUnsel elemsPad=$elemsPad)"
    }
    DenseCParse(result, bad.toSeq, log.size, lanesSel, lanesEx, elemsW, elemsUnsel, elemsPad)
  }

  /** 兼容口径：只要矩阵、不要审计汇总。
    * 【#166 注意】本函数仍会跑完整 lane 审计（内部走 Checked）但【丢弃违规消息】。
    * 新代码请直接用 parseDenseCWriteLogChecked 并把 violations 折入失败判定，
    * 否则 lane 级缺陷会重蹈「静默放行」覆辙。 */
  def parseDenseCWriteLog(log: Seq[CWriteBeat], outFmt: DataFormat.Type, m: Int, n: Int,
                          totalN: Int, cDmaExtW: Int): Array[Array[Long]] =
    parseDenseCWriteLogChecked(log, outFmt, m, n, totalN, cDmaExtW).matrix

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

  /** 期望发拍数据（【#166】已引入按地址的 lane 偏移语义）：
    *   ① 密集行按总线位宽右移取段（与 WrOnly 发拍表达式同源：
    *      seg = lineData >> (beatCnt×cDmaExtW) 取低 cDmaExtW 位）；
    *   ② 再按 laneOffsetBytes ×8 左移，把本拍数据放到它在总线字内【应在的 byte lane】上
    *      —— 与 main 侧 `data << (addrShiftBytes << 3)` 及 strb `<< addrShiftBytes` 同源。
    * 中间量位宽：seg 为 cDmaExtW 位，左移最多 (dmaBytes-1)×8 = cDmaExtW-8 位 ⟹ 展宽到
    *   2×cDmaExtW-8 位，故末尾显式 & 掩码截回 cDmaExtW 位（与 RTL 的 (cDmaExtW-1,0) 同义）。
    * 【laneOffsetBytes 必须由金锚期望地址推出】调用方应传
    *   WbSimCommon.cLaneOffset(expCAddrSeq(beatIdx), cDmaExtW)，【不得】传硬件实收 addr
    *   —— 后者会把金锚退化成 RTL 的镜像（金锚铁律）。 */
  def denseRowBeat(row: BigInt, beatInRow: Int, cDmaExtW: Int, laneOffsetBytes: Int): BigInt = {
    val wMask = (BigInt(1) << cDmaExtW) - 1
    val seg = (row >> (beatInRow * cDmaExtW)) & wMask
    (seg << (laneOffsetBytes * 8)) & wMask
  }

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
  // {MXE4M3, MXE5M2, MXE2M1}（与 WbOutCvt isQuantFmt = DataFormat.isMx 同源）。

  /** 量化格式参数表：复用 WbEnum.MxFormatParams 三单例（字段语义与边界数值
    * 注释统一收敛于 WbEnum.scala，逐字段镜像既有内联字面量；此处仅按 fmt
    * 编码选表，杜绝双源漂移）：
    *   E4M3：maxFinite=448=1.75×2^8，NaN 需 exp&mant 全1（0x7F），饱和 0x7E，
    *         maxMantVal=1.75（mant 全1 是 NaN 不可用，尾数域比较门限=0.75→0x600000）；
    *         舍入落 NaN 码改写为饱和。
    *   E5M2：maxFinite=57344=1.75×2^15，exp 全1 为 inf/nan，饱和 0x7B，
    *         nan 规范编码取 exp 全1 + 尾数高位置位（0x7E，同 fp16 规范 nan 取法）。
    *   E2M1：maxFinite=6.0=1.5×2^2，无 inf/nan 编码（exp 全1 仍为正常数），
    *         nan 输入裁定饱和（0x7），maxMantVal=1.5（尾数域比较门限=0.5→0x400000）。
    * maxNormalExp（swQuantElem 溢出判据）与 maxCodeExp 共用单字段（三格式取值
    * 恰一致：E4M3=15/E5M2=30/E2M1=3，见 WbEnum.MxFormatParams 注释）。 */
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
    *        传入，与 RTL 侧 WbOutCvt.quantElem 的同名形参【一一对称】。
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
    // (#151 F4) OCP MX 次正规产出臂（仅 E2M1）——与 RTL WbOutCvt.quantElem 的
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
    // (#151 F4-b) 定向舍入【深下溢】进位臂（仅 E2M1）——与 RTL WbOutCvt.quantElem 的
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
    // (#151 F4) 次正规产出开关：仅 MXE2M1 打开，与 RTL 侧 WbOutCvt 中「q2Seq 是唯一传
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

/** 命令握手及无额外 step 的轮询助手。DMA 主循环可独立轮询 STATUS 或 IRQ_RAW。
  * START 不清 RAW：每次任务先 W1C 清除，再取消 mask，然后发 START。
  */
trait WbStatusPollApi extends chisel3.simulator.PeekPokeAPI {
  def pokeRegCommand(cmd: Stream[WorkerBeeCmd], op: WorkerBeeOp.Type,
                     r0: Int, r1: Int = 0, d0: BigInt = 0, d1: BigInt = 0,
                     pc: BigInt = 0, tag: Int = 0): Unit = {
    cmd.valid.poke(true.B)
    cmd.payload.op.poke(op)
    cmd.payload.reg0Addr.poke(r0.U)
    cmd.payload.reg1Addr.poke(r1.U)
    cmd.payload.data0.poke(d0.U)
    cmd.payload.data1.poke(d1.U)
    cmd.payload.pc.poke(pc.U)
    cmd.payload.tag.poke(tag.U)
  }

  /** 完整事务：采样有效响应后才消费，避免读回落在 resp.fire 后一拍。 */
  def transactReg(clock: Clock, cmd: Stream[WorkerBeeCmd], resp: Stream[WorkerBeeResp],
                  op: WorkerBeeOp.Type, r0: Int, r1: Int = 0,
                  d0: BigInt = 0, d1: BigInt = 0, expectedErr: Boolean = false): BigInt = {
    resp.ready.poke(true.B)
    pokeRegCommand(cmd, op, r0, r1, d0, d1)
    var waited = 0
    while (!cmd.ready.peek().litToBoolean && waited < 100) { clock.step(); waited += 1 }
    assert(cmd.ready.peek().litToBoolean, "command handshake timeout")
    clock.step()
    cmd.valid.poke(false.B)
    waited = 0
    while (!resp.valid.peek().litToBoolean && waited < 100) { clock.step(); waited += 1 }
    assert(resp.valid.peek().litToBoolean, "response timeout")
    assert(resp.payload.err.peek().litToBoolean == expectedErr, s"unexpected response error at address $r0")
    val data = resp.payload.data.peek().litValue
    clock.step()
    data
  }

  def readRegister(clock: Clock, cmd: Stream[WorkerBeeCmd], resp: Stream[WorkerBeeResp], addr: Int): BigInt =
    transactReg(clock, cmd, resp, WorkerBeeOp.REG_READ, addr)

  def prepareStart(clock: Clock, cmd: Stream[WorkerBeeCmd], resp: Stream[WorkerBeeResp]): Unit = {
    transactReg(clock, cmd, resp, WorkerBeeOp.REG_WRITE1, WbSimCommon.IRQ_CLEAR_REG_ADDR,
      d0 = WbSimCommon.IRQ_ALL)
    transactReg(clock, cmd, resp, WorkerBeeOp.REG_WRITE1, WbSimCommon.IRQ_MASK_REG_ADDR, d0 = 0)
  }

  /** 只走到 START 写 fire，后续拍由 DMA 主循环服务，避免漏掉首个 DMA 请求。 */
  def launchStart(clock: Clock, cmd: Stream[WorkerBeeCmd]): Unit = {
    pokeRegCommand(cmd, WorkerBeeOp.REG_WRITE1, WbSimCommon.START_REG_ADDR, d0 = 1)
    var waited = 0
    while (!cmd.ready.peek().litToBoolean && waited < 100) { clock.step(); waited += 1 }
    assert(cmd.ready.peek().litToBoolean, "START handshake timeout")
    clock.step()
    cmd.valid.poke(false.B)
  }

  def pokeStatusRead(cmd: Stream[WorkerBeeCmd]): Unit =
    pokeRegCommand(cmd, WorkerBeeOp.REG_READ, WbSimCommon.STATUS_REG_ADDR)

  def pokeIrqRead(cmd: Stream[WorkerBeeCmd]): Unit =
    pokeRegCommand(cmd, WorkerBeeOp.REG_READ, WbSimCommon.IRQ_RAW_REG_ADDR)

  private def pollRegisterStep(cmd: Stream[WorkerBeeCmd], resp: Stream[WorkerBeeResp],
                               pollSt: Int, pollCool: Int, addr: Int): (Int, Int, Option[BigInt]) = {
    if (pollSt == 0) {
      pokeRegCommand(cmd, WorkerBeeOp.REG_READ, addr)
      if (cmd.ready.peek().litToBoolean) (1, 0, None) else (0, 0, None)
    } else if (pollSt == 1) {
      cmd.valid.poke(false.B)
      if (resp.valid.peek().litToBoolean) {
        assert(!resp.payload.err.peek().litToBoolean, s"poll read failed at address $addr")
        (2, WbSimCommon.POLL_PERIOD, Some(resp.payload.data.peek().litValue))
      } else (1, 0, None)
    } else {
      cmd.valid.poke(false.B)
      val cool = pollCool - 1
      if (cool <= 0) (0, 0, None) else (2, cool, None)
    }
  }

  def statusPollStep(cmd: Stream[WorkerBeeCmd], resp: Stream[WorkerBeeResp],
                     pollSt: Int, pollCool: Int): (Int, Int, Option[WbSimCommon.WbStatusBits]) = {
    val (st, cool, data) = pollRegisterStep(cmd, resp, pollSt, pollCool, WbSimCommon.STATUS_REG_ADDR)
    (st, cool, data.map(WbSimCommon.decodeStatus))
  }

  def irqPollStep(cmd: Stream[WorkerBeeCmd], resp: Stream[WorkerBeeResp],
                  pollSt: Int, pollCool: Int): (Int, Int, Option[WbSimCommon.WbIrqBits]) = {
    val (st, cool, data) = pollRegisterStep(cmd, resp, pollSt, pollCool, WbSimCommon.IRQ_RAW_REG_ADDR)
    (st, cool, data.map(WbSimCommon.decodeIrq))
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
trait WbFstSim { this: HasCliOptions =>

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
  * 与 WbFstSim 的差异：WbFstSim 需显式传 -DemitFst=1 才 dump；本 trait
  * 无条件 enableWavesAtTimeZero=true，仍注册 emitFst 键仅为命令行兼容。
  *
  * 迁移记录（taskId=143）：原定义于 WorkerBeeParamDebugSpec.scala（该文件已被
  * 用户删除），因 WorkerBeeQuickSpec 仍依赖，逐字迁移至本公共文件；原文件
  * HEAD 版本见 git show HEAD:src/workerbee/scala/test/WorkerBeeParamDebugSpec.scala。
  */
trait WbAlwaysFstSim { this: HasCliOptions =>
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
