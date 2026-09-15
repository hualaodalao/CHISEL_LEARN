/** FP32 基础运算（简化 IEEE-754，支持正常数 + 零，不处理 NaN/Inf/非规格化） */

import chisel3._
import chisel3.util._

object Fp32 {

  /** 从 fp32 (32-bit UInt) 提取 sign / exponent / mantissa */
  def sign(x: UInt): Bool = x(31)
  def exp(x: UInt): UInt = x(30, 23)
  def mant(x: UInt): UInt = x(22, 0)

  /** 组装 fp32 */
  def pack(s: Bool, e: UInt, m: UInt): UInt = Cat(s, e(7, 0), m(22, 0))

  /** bf16 (16-bit) → fp32：高 16 位直接拼接，低 16 位补零 */
  def bf16ToFp32(x: UInt): UInt = Cat(x(15, 0), 0.U(16.W))

  /** fp16 (16-bit) → fp32
    * fp16: sign(1) + exp(5) + mant(10), bias=15
    * fp32: sign(1) + exp(8) + mant(23), bias=127
    */
  def fp16ToFp32(x: UInt): UInt = {
    val s = x(15)
    val e5 = x(14, 10)
    val m10 = x(9, 0)
    // 零 / 非规格化简化处理：e5==0 时输出 ±0
    val e8 = Mux(e5 === 0.U, 0.U(8.W), (e5 +& 112.U)(7, 0)) // -15+127=112
    val m23 = Cat(m10, 0.U(13.W))
    val isZero = (e5 === 0.U) && (m10 === 0.U)
    Mux(isZero, Cat(s, 0.U(31.W)), Cat(s, e8, m23))
  }

  /** MX（microscaling）元素 + E8M0 scale → fp32 通用转换核（fp8 / mxfp4 双档）。
    *
    * E8M0 scale 是纯指数（bias=127），"乘 scale" 等价于对 fp32 的 exp 字段做加法：
    *   true_exp = (elem_exp - elem_bias) + (scale - 127)
    *   exp_fp32 = true_exp + 127 = elem_exp - elem_bias + scale
    * 即 scale 几乎零成本并入指数，尾数不变。
    *
    * clamp / 特殊值（用户已定语义）：
    *   - zero（exp==0 && mant==0）          → 输出 ±0（忽略 scale）
    *   - subnormal（exp==0 && mant!=0）      → 见 subnormalFtz：
    *       true  （fp8 E4M3/E5M2）flush-to-zero，输出 ±0（既有行为，逐位不变）；
    *       false （mxfp4 E2M1）    e=0/m=1 是【一等公民值 ±0.5】而非需 FTZ 的次正规，
    *                               按 ±2^(e+scale-bias-127) 正常解码（详见下方 fp32Sub）
    *   - 下溢（exp_fp32 < 1）                → flush-to-zero，输出 ±0
    *   - 上溢（exp_fp32 > 254）或 inf/nan    → saturate 到 fp32 max（保持 sign）
    *   inf/nan 的 assert 守护放在调用方（原为 HiveWorkUnit.scala 中的 HiveCvtOpMx，已随
    *   FusionMac 重构删除；现由 FpCvt.scala 的 FpCvtMx8ToMid / FpCvtMx8ToFp32 承接），
    *   仅对被选中的格式断言，避免另一路径组合逻辑的误报
    *
    * 【编译期泛化不变量】noInfNan / subnormalFtz 均为 Scala 层 Boolean 形参，经 Scala `if`
    * 在 elaboration 期展开，不生成任何运行期选择子。fp8 默认实参组合
    * （noInfNan=false, subnormalFtz=true）下每个表达式与泛化前【逐字一致】
    * （sign 位 x(expW+mantW) 在 expW+mantW==7 时即 x(7)），故 E4M3/E5M2 硬件 bit-exact 非回归。
    *
    * @param x                 MX 元素（fp8：8-bit，bit7=sign；mxfp4：8-bit 端口取低 4 位，
    *                          bit3=sign，高 4 位由软件保证为 0）
    * @param scale             8-bit E8M0 scale（bias=127）
    * @param expW/mantW/bias   元素指数位宽 / 尾数位宽 / 指数 bias
    * @param nanNeedMantAllOne true=仅 exp 全1 且 mant 全1 视为特殊值（E4M3 NaN，
    *                          exp 全1 且 mant 非全1 仍为正常最大数）；
    *                          false=exp 全1 即特殊值（E5M2 inf/nan）
    * @param noInfNan          编译期：true=该格式【无 inf/nan 编码】（mxfp4 E2M1：最大码
    *                          0x7 = ±6.0 是最大有限值，若沿用 exp 全1 判据会被误当 inf/nan
    *                          饱和）⟹ isInfNan 折叠为 false.B；false=保持原判据（fp8）
    * @param subnormalFtz      编译期：true=exp==0&&mant!=0 flush-to-zero（fp8 既有行为）；
    *                          false=按次正规真值解码（mxfp4 E2M1 的 ±0.5）
    */
  private def mxToFp32(x: UInt, scale: UInt, expW: Int, mantW: Int, bias: Int,
                       nanNeedMantAllOne: Boolean,
                       noInfNan: Boolean = false,
                       subnormalFtz: Boolean = true): UInt = {
    require(1 + expW + mantW == 8 || 1 + expW + mantW == 4,
      s"MX 元素必须为 8-bit（mxfp8）或 4-bit（mxfp4）：1+$expW+$mantW")
    val s = x(expW + mantW)              // 符号位（fp8: x(7)；mxfp4 E2M1: x(3)）
    val e = x(mantW + expW - 1, mantW)   // exp 字段（expW 位）
    val m = x(mantW - 1, 0)              // mant 字段（mantW 位）

    val isZero   = (e === 0.U) && (m === 0.U)
    val isSub    = (e === 0.U) && (m =/= 0.U)                 // subnormal（见 subnormalFtz）
    val isInfNan = if (noInfNan) false.B
                   else if (nanNeedMantAllOne) (e.andR && m.andR) else e.andR

    // 尾数左对齐到 fp32 的 23-bit（高位放元素尾数，低位补零）
    // mantW=1（E2M1）时 = Cat(m(1位), 0.U(22.W)) → 23 位，位宽自洽
    val m23 = Cat(m, 0.U((23 - mantW).W))

    // exp_fp32 = e + scale - bias（有符号计算以检测上/下溢）
    val expSum    = e +& scale                 // 无符号，最多 9 位
    val expSigned = expSum.zext - bias.S       // 有符号
    val underflow = expSigned < 1.S            // exp 字段 < 1 → flush-to-zero
    val overflow  = expSigned > 254.S          // exp 字段 > 254 → saturate

    val fp32Zero   = Cat(s, 0.U(31.W))
    val fp32Max    = Cat(s, 0xFE.U(8.W), ((1 << 23) - 1).U(23.W))  // 保持 sign 的 fp32 max normal
    val fp32Normal = Cat(s, expSigned.asUInt(7, 0), m23)

    if (subnormalFtz) {
      // fp8（E4M3/E5M2）原路径：表达式与泛化前逐字一致
      Mux(isZero || isSub, fp32Zero,
        Mux(isInfNan || overflow, fp32Max,
          Mux(underflow, fp32Zero, fp32Normal)))
    } else {
      // mxfp4 E2M1（subnormalFtz=false）：e=0/m=1 解码为 ±0.5 × 2^(scale-127)。
      //   通用推导：次正规真值 = m/2^mantW × 2^(1-bias) × 2^(scale-127)；mantW=1/m=1 时
      //   = 2^(-bias) × 2^(scale-127) = 2^(scale-bias-127) ⟹ fp32 exp 字段 = scale-bias，
      //   尾数 = 0（隐含 1.0）。而 e=0 时 expSigned = e+scale-bias = scale-bias，恰同值，
      //   故 fp32Sub 复用 expSigned —— 与计划正文的中性特例 Cat(s,(scale-1),0)（bias=1）
      //   逐位等价，且【无裸字面量】、天然继承下方 underflow/overflow clamp：
      //   scale<2 时 expSigned<1 → ±0（正确 FTZ，避免裸 (scale-1) 在 scale=0 处 8-bit
      //   绕回成 0xFF=±inf）；scale=255 时 expSigned=254 未越 254 上界 → 不饱和。
      //   scale 对块内所有元素（含 e=0/m=1）生效，故通用式必须应用 scale（非 126 常量）。
      val fp32Sub = Cat(s, expSigned.asUInt(7, 0), 0.U(23.W))
      Mux(isZero, fp32Zero,
        Mux(isInfNan || overflow, fp32Max,
          Mux(underflow, fp32Zero,
            Mux(isSub, fp32Sub, fp32Normal))))
    }
  }

  /** MX E4M3（{sign(1),exp(4),mant(3)}，bias=7）+ E8M0 scale → fp32 */
  def mxE4M3ToFp32(x: UInt, scale: UInt): UInt =
    mxToFp32(x, scale, expW = 4, mantW = 3, bias = 7,  nanNeedMantAllOne = true)

  /** MX E5M2（{sign(1),exp(5),mant(2)}，bias=15）+ E8M0 scale → fp32 */
  def mxE5M2ToFp32(x: UInt, scale: UInt): UInt =
    mxToFp32(x, scale, expW = 5, mantW = 2, bias = 15, nanNeedMantAllOne = false)

  /** MX E2M1（mxfp4，{sign(1),exp(2),mant(1)}）+ E8M0 scale → fp32。
    * 幅值域 = {0, 0.5, 1, 1.5, 2, 3, 4, 6}（最大有限值 6.0 = 码 0x7）。
    * 格式参数一律引 MxFormatParams.E2M1（禁裸字面量）：
    *   - noInfNan=true：E2M1 无 inf/nan 编码，0x7 = ±6.0 必须按最大有限值解码；
    *   - subnormalFtz=false：e=0/m=1 = ±0.5 是一等公民值，不得 flush-to-zero；
    *   - nanNeedMantAllOne 在 noInfNan=true 下为 don't-care（isInfNan 已折叠 false.B），
    *     传 false 与 E5M2 风格一致。
    * x 为 8-bit 端口、仅低 4 位有效（高 4 位由软件保证为 0）。
    */
  def mxE2M1ToFp32(x: UInt, scale: UInt): UInt =
    mxToFp32(x, scale, expW = MxFormatParams.E2M1.expW, mantW = MxFormatParams.E2M1.mantW,
      bias = MxFormatParams.E2M1.bias, nanNeedMantAllOne = false,
      noInfNan = true, subnormalFtz = false)

  /** fp32 乘法（简化版：正常数 + 零）
    * @param rnd 舍入模式（当前实现 RNE/RTZ，其余退化为 RTZ）
    */
  def mul(a: UInt, b: UInt, rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {
    val sA = sign(a); val eA = exp(a); val mA = mant(a)
    val sB = sign(b); val eB = exp(b); val mB = mant(b)

    val sOut = sA ^ sB
    // 隐含 1：mantissa 加 1.0 → 24-bit
    val mA24 = Cat(1.U(1.W), mA)
    val mB24 = Cat(1.U(1.W), mB)
    val mProd = mA24 * mB24 // 48-bit

    // 指数相加 - bias(127)；尾数积 >= 2（mProd(47)=1）需右移 1 位，
    // 指数必须相应 +1，否则结果恰好为真值的一半（旧实现漏掉该补偿）
    val eSum = eA +& eB // 9-bit to avoid overflow
    val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
    val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

    // 规格化：mProd[47] 为 1 时右移 1
    val mNorm = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
    // 舍入位（被截断的最高位）
    val guardBit = Mux(mProd(47), mProd(23), mProd(22))
    val mRounded = applyRounding(mNorm, guardBit, sOut, rnd)

    // 任一操作数为零 → 结果为零
    val aZero = (eA === 0.U) && (mA === 0.U)
    val bZero = (eB === 0.U) && (mB === 0.U)
    Mux(aZero || bZero, 0.U(32.W), pack(sOut, eOut, mRounded))
  }

  /** fp32 加法 — 单路径 P0+P2 架构
    * P0: 消除冗余补码转换（确定大/小数后无符号加/减，仅 1 次操作）
    * P2: 截断对齐移位（桶形移位器 clamp 到 25，超出部分收集为 sticky）
    * @param rnd 舍入模式
    */
  def add(a: UInt, b: UInt, rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {
    val sA = sign(a); val eA = exp(a); val mA = mant(a)
    val sB = sign(b); val eB = exp(b); val mB = mant(b)

    val aZero = (eA === 0.U) && (mA === 0.U)
    val bZero = (eB === 0.U) && (mB === 0.U)

    // 有效减法判断：异号为减法
    val effectSub = sA ^ sB

    // P0: 确定大/小操作数（先比指数，指数相同比尾数），结果符号 = 大数符号
    val aGtB = (eA > eB) || ((eA === eB) && (mA >= mB))
    val eLarge = Mux(aGtB, eA, eB)
    val eSmall = Mux(aGtB, eB, eA)
    val mLargeMant = Mux(aGtB, mA, mB) // 23-bit
    val mSmallMant = Mux(aGtB, mB, mA) // 23-bit
    val sOut = Mux(aGtB, sA, sB)

    // 指数差
    val expDiff = eLarge - eSmall // 8-bit

    // P2: 截断移位——桶形移位器只覆盖 0~25（5 级），超出全部进 sticky
    val shiftAmt = Mux(expDiff > 25.U, 25.U(5.W), expDiff(4, 0))

    // 26-bit 工作格式: {overflow(1), implicit_1(1), mantissa(23), guard(1)}
    val mLargeW = Cat(0.U(1.W), 1.U(1.W), mLargeMant, 0.U(1.W))
    val mSmallW = Cat(0.U(1.W), 1.U(1.W), mSmallMant, 0.U(1.W))

    // 桶形移位器对齐小操作数
    val mSmallAligned = (mSmallW >> shiftAmt)(25, 0)
    // Sticky: 移出位是否含 1
    val stickyMask = (1.U(26.W) << shiftAmt) - 1.U
    val sticky = Mux(expDiff > 25.U, true.B, (mSmallW & stickyMask).orR)

    // P0: 无符号加/减（大 - 小 保证无借位）
    val addResult = mLargeW +& mSmallAligned // 27-bit
    val subResult = mLargeW - mSmallAligned  // 26-bit

    // 规格化 + 舍入位提取
    val resMant23 = WireDefault(0.U(23.W))
    val resGuard  = WireDefault(false.B)
    val resExp    = WireDefault(0.U(8.W))

    when(effectSub) {
      // 有效减法：LZC + 可变左移
      val lzc = PriorityEncoder(Reverse(subResult))
      val normed = (subResult << lzc)(25, 0)
      resMant23 := normed(24, 2)
      resGuard  := normed(1).asBool | normed(0).asBool | sticky
      resExp    := Mux(eLarge + 1.U > lzc, (eLarge + 1.U - lzc)(7, 0), 0.U(8.W))
    }.otherwise {
      // 有效加法
      when(addResult(25)) {
        // 溢出 1 bit：隐含 1 上移到 bit 25，右移 1，指数 +1
        resMant23 := addResult(24, 2)
        resGuard  := addResult(1).asBool | addResult(0).asBool | sticky
        resExp    := eLarge + 1.U
      }.otherwise {
        // 正常：隐含 1 在 bit 24
        resMant23 := addResult(23, 1)
        resGuard  := addResult(0).asBool | sticky
        resExp    := eLarge
      }
    }

    // 舍入
    val mRounded = applyRounding(resMant23, resGuard, sOut, rnd)

    // 结果为零：有效减法且尾数完全抵消
    val resultIsZero = effectSub && (expDiff === 0.U) && (mLargeMant === mSmallMant)

    // 边界处理
    Mux(aZero, b,
      Mux(bZero, a,
        Mux(resultIsZero, 0.U(32.W),
          pack(sOut, resExp, mRounded))))
  }

  /** 40-bit 延迟规格化格式 → 32-bit fp32 规格化
    * 输入格式：{sign(1), exp(8), mantissa(31)}，mantissa 可能有 leading zeros
    * 输出：标准 IEEE-754 fp32（sign + 8-bit exp + 23-bit mant，隐含1已去除）
    * 舍入固定为 RNE（DMA 写出时使用，简化实现）
    */
  def fpNormalize40to32(raw: UInt): UInt = {
    val s    = raw(39)
    val e    = raw(38, 31)
    val m31  = raw(30, 0)
    val isZero = (e === 0.U) && (m31 === 0.U)

    // LZC + 左移规格化
    val lzc = PriorityEncoder(Reverse(m31))
    val normed = (m31 << lzc)(30, 0)

    // 提取 23-bit mantissa (bit 30 是隐含 1，取 bit 29 down to 7)
    val mant23 = normed(29, 7)
    val guard  = normed(6)
    val stickyBits = normed(5, 0).orR

    // 简化舍入 (RNE: guard && (sticky || lsb) → +1，简化为 guard|sticky)
    val roundUp = guard & (stickyBits | mant23(0))
    // 舍入进位处理（修复留痕）：mant23 全 1（0x7FFFFF）且 roundUp 时，
    // 旧实现直接截取 (mant23 +& 1)(22,0) 丢失第 23 位进位且指数未 +1，
    // 结果差 2 倍。现进位时尾数归零、指数 +1。
    val mSum      = mant23 +& roundUp        // 24-bit（bit23 = 尾数进位）
    val mantCarry = mSum(23)
    val mRounded  = mSum(22, 0)

    // 指数修正：减去 leading zeros 数量，再加舍入进位；
    // 中间量用 9 位防溢出（255+1），最终截 8 位落 fp32 exp 字段
    val eBase = Mux(e > lzc, e - lzc, 0.U(8.W))
    val eOut  = (eBase +& mantCarry)(7, 0)

    Mux(isZero, 0.U(32.W), Cat(s, eOut, mRounded))
  }

  /** 舍入逻辑（作用于 23-bit mantissa）
    * RNE: guard=1 时 +1（简化，未处理 tie-to-even）
    * RTZ: 截断（不加）
    * RDN: 负数且 guard=1 时 +1
    * RUP: 正数且 guard=1 时 +1
    * RMM: guard=1 时 +1
    */
  private def applyRounding(mant23: UInt, guard: UInt, sign: Bool, rnd: RoundingMode.Type): UInt = {
    val g = guard.asBool
    val roundUp = Mux(rnd === RoundingMode.RTZ, false.B,
      Mux(rnd === RoundingMode.RDN, sign && g,
        Mux(rnd === RoundingMode.RUP, !sign && g,
          g))) // RNE / RMM: guard=1 → +1
    Mux(roundUp, mant23 +& 1.U, mant23)(22, 0)
  }
}
