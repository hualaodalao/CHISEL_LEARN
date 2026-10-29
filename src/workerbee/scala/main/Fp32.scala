/** FP32 基础运算（简化 IEEE-754，支持正常数 + 零，不处理 NaN/Inf/非规格化） */

import chisel3._
import chisel3.util._

object Fp32 {

  def sign(x: UInt): Bool = x(31)
  def exp(x: UInt): UInt = x(30, 23)
  def mant(x: UInt): UInt = x(22, 0)

  def pack(s: Bool, e: UInt, m: UInt): UInt = Cat(s, e(7, 0), m(22, 0))

  /** bf16 (16-bit) → fp32：高 16 位直接拼接，低 16 位补零 */
  def bf16ToFp32(x: UInt): UInt = Cat(x(15, 0), 0.U(16.W))

  /** fp16 (1+5+10, bias=15) → fp32 (1+8+23, bias=127)；e5==0 ⟹ ±0（零与非规格化统一 FTZ）。 */
  def fp16ToFp32(x: UInt): UInt = {
    val s = x(15)
    val e5 = x(14, 10)
    val m10 = x(9, 0)
    val e8 = Mux(e5 === 0.U, 0.U(8.W), (e5 +& 112.U)(7, 0)) // -15+127=112
    val m23 = Cat(m10, 0.U(13.W))
    val isZero = (e5 === 0.U) && (m10 === 0.U)
    Mux(isZero, Cat(s, 0.U(31.W)), Cat(s, e8, m23))
  }

  /** MX（microscaling）元素 + E8M0 scale → fp32 通用转换核（fp8 / mxfp4 双档）。
    *
    * E8M0 scale 是纯指数（bias=127），乘 scale 等价于对 fp32 的 exp 字段做加法，尾数不变：
    *   exp_fp32 = (elem_exp − elem_bias) + (scale − 127) + 127 = elem_exp − elem_bias + scale。
    *
    * clamp / 特殊值语义：zero（exp==0 && mant==0）→ ±0（忽略 scale）；subnormal（exp==0 &&
    *   mant!=0）见 subnormalFtz；下溢（exp_fp32 < 1）→ FTZ 输出 ±0；上溢（exp_fp32 > 254）或
    *   inf/nan → saturate 到 fp32 max（保持 sign）。inf/nan 的 assert 守护在调用方
    *   （FpCvt.scala 的 FpCvtMx8ToMid / FpCvtMx8ToFp32），仅对被选中的格式断言。
    *
    * noInfNan / subnormalFtz 为 Scala 层 Boolean 形参，经 `if` 在 elaboration 期展开，不生成
    * 任何运行期选择子。
    *
    * @param x                 MX 元素（fp8：8-bit，bit7=sign；mxfp4：8-bit 端口取低 4 位，
    *                          bit3=sign，高 4 位由软件保证为 0）
    * @param scale             8-bit E8M0 scale（bias=127）
    * @param nanNeedMantAllOne true=仅 exp 全1 且 mant 全1 为特殊值（E4M3 NaN）；false=exp 全1
    *                          即特殊值（E5M2 inf/nan）
    * @param noInfNan          true=该格式无 inf/nan 编码（mxfp4 E2M1 最大码 0x7 = ±6.0 是最大
    *                          有限值，沿用 exp 全1 判据会被误饱和）⟹ isInfNan 折叠 false.B
    * @param subnormalFtz      true=exp==0&&mant!=0 flush-to-zero（fp8）；false=按次正规真值解码
    *                          （mxfp4 E2M1 的 ±0.5）
    */
  private def mxToFp32(x: UInt, scale: UInt, expW: Int, mantW: Int, bias: Int,
                       nanNeedMantAllOne: Boolean,
                       noInfNan: Boolean = false,
                       subnormalFtz: Boolean = true): UInt = {
    require(1 + expW + mantW == 8 || 1 + expW + mantW == 4,
      s"MX 元素必须为 8-bit（mxfp8）或 4-bit（mxfp4）：1+$expW+$mantW")
    val s = x(expW + mantW)
    val e = x(mantW + expW - 1, mantW)
    val m = x(mantW - 1, 0)

    val isZero   = (e === 0.U) && (m === 0.U)
    val isSub    = (e === 0.U) && (m =/= 0.U)                 // subnormal（见 subnormalFtz）
    val isInfNan = if (noInfNan) false.B
                   else if (nanNeedMantAllOne) (e.andR && m.andR) else e.andR

    val m23 = Cat(m, 0.U((23 - mantW).W))

    // exp_fp32 = e + scale − bias（有符号计算以检测上/下溢）
    val expSum    = e +& scale
    val expSigned = expSum.zext - bias.S
    val underflow = expSigned < 1.S            // exp 字段 < 1 → flush-to-zero
    val overflow  = expSigned > 254.S          // exp 字段 > 254 → saturate

    val fp32Zero   = Cat(s, 0.U(31.W))
    val fp32Max    = Cat(s, 0xFE.U(8.W), ((1 << 23) - 1).U(23.W))  // 保持 sign 的 fp32 max normal
    val fp32Normal = Cat(s, expSigned.asUInt(7, 0), m23)

    if (subnormalFtz) {
      Mux(isZero || isSub, fp32Zero,
        Mux(isInfNan || overflow, fp32Max,
          Mux(underflow, fp32Zero, fp32Normal)))
    } else {
      // mxfp4 E2M1（subnormalFtz=false）：e=0/m=1 是一等公民值 ±0.5，真值 = 2^(scale−bias−127)
      //   ⟹ fp32 exp 字段 = scale−bias、尾数 = 0，与 e=0 时的 expSigned 同值，故 fp32Sub 复用
      //   expSigned 并继承 clamp：scale<2 → ±0（FTZ，避免裸 (scale−1) 在 scale=0 处 8-bit 绕回
      //   成 0xFF=±inf）；scale=255 → expSigned=254 不饱和。scale 对块内所有元素（含 e=0/m=1）
      //   生效，故必须应用 scale 而非常量 126。
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

  /** MX E2M1（mxfp4，{sign(1),exp(2),mant(1)}）+ E8M0 scale → fp32。幅值域 =
    * {0, 0.5, 1, 1.5, 2, 3, 4, 6}（最大有限值 6.0 = 码 0x7）。格式参数一律引 MxFormatParams.E2M1
    * （禁裸字面量）：noInfNan=true（0x7 必须按最大有限值解码）、subnormalFtz=false（e=0/m=1 =
    * ±0.5 不得 FTZ）；nanNeedMantAllOne 为 don't-care。x 为 8-bit 端口、仅低 4 位有效。
    */
  def mxE2M1ToFp32(x: UInt, scale: UInt): UInt =
    mxToFp32(x, scale, expW = MxFormatParams.E2M1.expW, mantW = MxFormatParams.E2M1.mantW,
      bias = MxFormatParams.E2M1.bias, nanNeedMantAllOne = false,
      noInfNan = true, subnormalFtz = false)

  /** fp32 乘法（正常数 + 零）；rnd 实现 RNE/RTZ，其余退化为 RTZ。 */
  def mul(a: UInt, b: UInt, rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {
    val sA = sign(a); val eA = exp(a); val mA = mant(a)
    val sB = sign(b); val eB = exp(b); val mB = mant(b)

    val sOut = sA ^ sB
    val mA24 = Cat(1.U(1.W), mA)
    val mB24 = Cat(1.U(1.W), mB)
    val mProd = mA24 * mB24

    // 指数相加 − bias(127)；尾数积 ≥ 2（mProd(47)=1）需右移 1 位、指数 +1
    val eSum = eA +& eB
    val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
    val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

    val mNorm = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
    val guardBit = Mux(mProd(47), mProd(23), mProd(22))
    val mRounded = applyRounding(mNorm, guardBit, sOut, rnd)

    val aZero = (eA === 0.U) && (mA === 0.U)
    val bZero = (eB === 0.U) && (mB === 0.U)
    Mux(aZero || bZero, 0.U(32.W), pack(sOut, eOut, mRounded))
  }

  /** fp32 加法 — 单路径：确定大/小数后做一次无符号加/减；对齐桶形移位器 clamp 到 25，
    * 超出部分收集为 sticky。
    */
  def add(a: UInt, b: UInt, rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {
    val sA = sign(a); val eA = exp(a); val mA = mant(a)
    val sB = sign(b); val eB = exp(b); val mB = mant(b)

    val aZero = (eA === 0.U) && (mA === 0.U)
    val bZero = (eB === 0.U) && (mB === 0.U)

    val effectSub = sA ^ sB

    // 确定大/小操作数（先比指数，指数相同比尾数），结果符号 = 大数符号
    val aGtB = (eA > eB) || ((eA === eB) && (mA >= mB))
    val eLarge = Mux(aGtB, eA, eB)
    val eSmall = Mux(aGtB, eB, eA)
    val mLargeMant = Mux(aGtB, mA, mB)
    val mSmallMant = Mux(aGtB, mB, mA)
    val sOut = Mux(aGtB, sA, sB)

    val expDiff = eLarge - eSmall

    // 桶形移位器只覆盖 0~25（5 级），超出全部进 sticky
    val shiftAmt = Mux(expDiff > 25.U, 25.U(5.W), expDiff(4, 0))

    // 26-bit 工作格式: {overflow(1), implicit_1(1), mantissa(23), guard(1)}
    val mLargeW = Cat(0.U(1.W), 1.U(1.W), mLargeMant, 0.U(1.W))
    val mSmallW = Cat(0.U(1.W), 1.U(1.W), mSmallMant, 0.U(1.W))

    val mSmallAligned = (mSmallW >> shiftAmt)(25, 0)
    val stickyMask = (1.U(26.W) << shiftAmt) - 1.U
    val sticky = Mux(expDiff > 25.U, true.B, (mSmallW & stickyMask).orR)

    // 无符号加/减（大 − 小 保证无借位）
    val addResult = mLargeW +& mSmallAligned
    val subResult = mLargeW - mSmallAligned

    val resMant23 = WireDefault(0.U(23.W))
    val resGuard  = WireDefault(false.B)
    val resExp    = WireDefault(0.U(8.W))

    when(effectSub) {
      val lzc = PriorityEncoder(Reverse(subResult))
      val normed = (subResult << lzc)(25, 0)
      resMant23 := normed(24, 2)
      resGuard  := normed(1).asBool | normed(0).asBool | sticky
      resExp    := Mux(eLarge + 1.U > lzc, (eLarge + 1.U - lzc)(7, 0), 0.U(8.W))
    }.otherwise {
      when(addResult(25)) {
        resMant23 := addResult(24, 2)
        resGuard  := addResult(1).asBool | addResult(0).asBool | sticky
        resExp    := eLarge + 1.U
      }.otherwise {
        resMant23 := addResult(23, 1)
        resGuard  := addResult(0).asBool | sticky
        resExp    := eLarge
      }
    }

    val mRounded = applyRounding(resMant23, resGuard, sOut, rnd)

    // 结果为零：有效减法且尾数完全抵消
    val resultIsZero = effectSub && (expDiff === 0.U) && (mLargeMant === mSmallMant)

    Mux(aZero, b,
      Mux(bZero, a,
        Mux(resultIsZero, 0.U(32.W),
          pack(sOut, resExp, mRounded))))
  }

  /** lazy-40 {sign(1), exp(8), mant(31)}（mant 可能带 leading zeros）→ fp32（1+8+23，隐含 1
    * 已去除）；舍入固定 RNE。
    */
  def fpNormalize40to32(raw: UInt): UInt = {
    val s    = raw(39)
    val e    = raw(38, 31)
    val m31  = raw(30, 0)
    val isZero = (e === 0.U) && (m31 === 0.U)

    val lzc = PriorityEncoder(Reverse(m31))
    val normed = (m31 << lzc)(30, 0)

    // bit30 是隐含 1 ⟹ mantissa 取 bit[29:7]
    val mant23 = normed(29, 7)
    val guard  = normed(6)
    val stickyBits = normed(5, 0).orR

    val roundUp = guard & (stickyBits | mant23(0))
    // 尾数进位（mant23 全 1 且 roundUp）：尾数归零、指数 +1
    val mSum      = mant23 +& roundUp
    val mantCarry = mSum(23)
    val mRounded  = mSum(22, 0)

    // 指数修正：减 leading zeros 再加舍入进位；中间量 9 位防溢出，最终截 8 位
    val eBase = Mux(e > lzc, e - lzc, 0.U(8.W))
    val eOut  = (eBase +& mantCarry)(7, 0)

    Mux(isZero, 0.U(32.W), Cat(s, eOut, mRounded))
  }

  /** 23-bit mantissa 舍入：RTZ 截断；RDN 负数且 guard=1 则 +1；RUP 正数且 guard=1 则 +1；
    * RNE/RMM guard=1 则 +1（RNE 为简化实现，未处理 tie-to-even）。
    */
  private def applyRounding(mant23: UInt, guard: UInt, sign: Bool, rnd: RoundingMode.Type): UInt = {
    val g = guard.asBool
    val roundUp = Mux(rnd === RoundingMode.RTZ, false.B,
      Mux(rnd === RoundingMode.RDN, sign && g,
        Mux(rnd === RoundingMode.RUP, !sign && g,
          g)))
    Mux(roundUp, mant23 +& 1.U, mant23)(22, 0)
  }
}
