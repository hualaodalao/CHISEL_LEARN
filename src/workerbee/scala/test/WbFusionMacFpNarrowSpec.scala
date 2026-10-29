/** WbFusionMacFpNarrowSpec — narrow 配置（hasFp=false + useNarrowAdd=true）的系统级测试
  *
  * 目标：验证 WbFusionMacFp(supportedFmts=MXONLY, useNarrowAdd=true) 配置
  *   1. 饱和域输出非 NaN（lazy-40 expField 钳到 ExpSatMax=254 而非 255）
  *   2. MX4 in-spec【lane fraction-sum 无损域】bit-exact。该域不是 MX4 全域：无损的只是同 block 四 lane
  *      的乘积和（共享 scaleA/scaleB ⟹ expField 跨度 ≤7 ≤ W−4，且 MX4 乘积 mant23 低 20 位结构性 0
  *      ⟹ 对齐 ts ≤ 20 ⟹ lane 侧 nLossy = 0）；cReg 槽不受该论证保护（满精度 fp32 装入，或真值远小于
  *      lane 主导项而被 E 挤出窗口 term-drop）⟹ 那类向量 nLossy ≥ 1、diff ≠ 0，只落契约 ① 绝对界
  *      （相消域由 ③ 兜底）。本 spec 的 mx4Vecs 能逐位断言是因其 cReg ∈ {0, 1.0}（fp32-精确、mant31
  *      低 12 位为 0）—— 激励受限的结论，不是 MX4 模式的全域性质；反例见本文件 n_mx8_cReg_spread 与
  *      FpAddNarrowSpec 的 DR3/G2b/G3b/LZ2/ML2。
  *   3. MX8 in-spec（goldenExact=true 向量）bit-exact（有效位 ≤9 bit << W=19 ⟹ nLossy=0 ⟹ 精确）
  *   4. MX8 宽指数跨度向量在契约内（绝对界无条件 + 良态域相对界 2^-(W-1)）
  *   5. require 护栏：useNarrowAdd=true + 白名单外格式 → elaboration 期 Scala 异常
  *
  * 金锚来源（禁拟合 RTL）：载体 = Dyadic(m:BigInt, e:Int) = m×2^e，与 FpAddNarrowSpec 同源同纪律。
  *   block4 要在相消域用金锚裁决绝对界，而相消域 |exact| 可比最大项小十几个数量级；Double（53-bit
  *   尾数）构造 exact = cReg + Σ lane 时自身就会舍入 ⟹ 金锚被污染、兜底判定失去意义（既可能误判
  *   FAIL，也可能因金锚与 RTL 同向舍入而假 PASS）。Dyadic 加/减/乘封闭无舍入 ⟹ 判定式退化为纯 BigInt
  *   整数比较，零浮点参与。
  * · 元素真值 mxElemDyadic 的解码分支序与 WbSimCommon.mxToFloat 逐条同源（expSigned = e+scale−bias、
  *   fp8 subnormalFtz、E2M1 subnormal=±0.5 一等公民、underflow clamp）；格式参数一律引
  *   WbEnum.MxFormatParams 单例，禁裸字面量。
  * · lane 乘积 = aElemVal × bElemVal（Dyadic 乘；MX8 ≤8-bit / MX4 ≤4-bit 有效位 ⟹ 恒 fp32-可表示）
  *   → dyadicToFp32Bits 编码，与上游 FpMulMx8/FpMulMx4 的 in-spec 输出逐位同值。
  * · 金锚和 exact = cReg + Σ lane 乘积；金锚 fp32 = swFpNormalize40to32(fp32BitsTo40(
  *   dyadicToFp32RNE(exact)))，只在最后一步做单次 RNE 舍入，全程不经 Double 中转。
  * · 误差判定（纯整数）：相对界 |diff|×2^(W−1) ≤ |exact|；绝对界 |diff| ≤ (nLossy+2^k−1)×windowLsb。
  *
  * 精度契约（三段式，与 FpAddNarrow.scala 头注 / FpAddNarrowSpec 同源）
  * ① 绝对界 |diff| ≤ (nLossy + 2^k − 1) × 2^(E−126−W) —— 无条件恒成立
  * ② 相对界 relErr ≤ 2^-(W-1) —— 仅良态域：充分条件 |exact| ≥ (nLossy+2^k−1)×2^(W−1)×windowLsb
  * ③ 相消域 —— 任意有限 W 都无相对界，由 ① 兜底
  *
  * 验证策略（按三段契约逐向量真实归类，不做「若干条过一条即过」的放水）：
  *   · goldenExact=true 向量（部分和 fp32-精确 + 指数跨度 << W）⟹ nLossy=0, k=0 ⟹ 可做 bit-exact 断言
  *   · 宽指数跨度向量（block4）⟹ 软件侧从激励独立重算 E / windowLsb / nLossy / k / absBound（与
  *     FpAddNarrowSpec.analyze 同构，全程不读 RTL），再对每一条：(i) 无条件断言契约 ①；(ii) 按解析量
  *     归入恰好一条路径（J1 nLossy==0 ∧ k==0 ⟹ diff==0；J2 ② 充分条件成立 ⟹ |diff|×2^(W−1) ≤ |exact|；
  *     J3 其余 ⟹ 由 (i) 的绝对界兜底，不声称相对界）；(iii) 汇总断言 nJ1+nJ2+nJ3 == 向量总数。
  *   · 饱和域向量 ⟹ out-of-spec，只验 non-NaN + lazy-40 expField ≤ ExpSatMax(254)，不做金锚裁决
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class WbFusionMacFpNarrowSpec extends AnyFlatSpec with Matchers with ChiselSim {

  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  private val FMTS_MXONLY = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)
  private val W = FpAddNarrow.DefaultW   // 19
  /** 契约 ② 的相对阈值 2^−(W−1) = 2^−18，仅供报告打印：判定一律走 Dyadic 整数式
    * `diff.shl(W-1).leAbs(exact)`（用 Double 比较时相消域里两端双双被舍入 ⟹ 阈值判定本身就是噪声）。 */
  private val REL_THRESHOLD = math.pow(2, -(W - 1))  // 2^-18（report-only）

  private def e4m3(s: Int, e: Int, m: Int): Int = ((s & 1) << 7) | ((e & 0xF) << 3) | (m & 0x7)
  private def e5m2(s: Int, e: Int, m: Int): Int = ((s & 1) << 7) | ((e & 0x1F) << 2) | (m & 0x3)
  private def e2m1(s: Int, e: Int, m: Int): Int = ((s & 1) << 3) | ((e & 0x3) << 1) | (m & 0x1)
  private def mx8(lane0: Int, lane1: Int): Int = ((lane1 & 0xFF) << 8) | (lane0 & 0xFF)
  private def mx4(n0: Int, n1: Int, n2: Int, n3: Int): Int =
    (n0 & 0xF) | ((n1 & 0xF) << 4) | ((n2 & 0xF) << 8) | ((n3 & 0xF) << 12)

  // Dyadic：精确二进制有理数 value = m × 2^e（金锚载体，加/减/乘封闭无舍入）。与 FpAddNarrowSpec 内
  //   的同名载体同构（那边是 private inner class 无法跨 spec 复用）。
  /** 不变量（由 Dyadic.apply 唯一入口保证，禁裸 new）：m 为奇数或 m == 0 且 e == 0 ⟹ 规范形唯一
    * ⟹ 结构相等 (m,e) 即数学相等。 */
  final class Dyadic(val m: BigInt, val e: Int) {
    def isZero: Boolean = m.signum == 0
    def signum: Int = m.signum
    def abs: Dyadic = if (m.signum < 0) Dyadic(-m, e) else this
    def unary_- : Dyadic = Dyadic(-m, e)
    def +(o: Dyadic): Dyadic =
      if (isZero) o
      else if (o.isZero) this
      else {
        val k = math.min(e, o.e)
        Dyadic(m * (BigInt(1) << (e - k)) + o.m * (BigInt(1) << (o.e - k)), k)
      }
    def -(o: Dyadic): Dyadic = this + (-o)
    def *(o: Dyadic): Dyadic = if (isZero || o.isZero) Dyadic.Zero else Dyadic(m * o.m, e + o.e)
    def shl(n: Int): Dyadic = Dyadic(m, e + n)
    def leAbs(o: Dyadic): Boolean =
      if (isZero) true
      else if (o.isZero) false
      else { val k = math.min(e, o.e); (m.abs << (e - k)) <= (o.m.abs << (o.e - k)) }
    def ltAbs(o: Dyadic): Boolean =
      if (o.isZero) false
      else if (isZero) true
      else { val k = math.min(e, o.e); (m.abs << (e - k)) < (o.m.abs << (o.e - k)) }
    /** 仅供报告打印（不参与任何判定）；|值| 超 Double 域时给 Inf，可接受。 */
    def toDouble: Double = if (isZero) 0.0 else m.toDouble * math.pow(2.0, e.toDouble)
    override def equals(o: Any): Boolean = o match {
      case d: Dyadic => m == d.m && e == d.e
      case _ => false
    }
    override def hashCode: Int = m.hashCode * 31 + e
    override def toString: String = if (isZero) "0" else f"$m*2^$e(=${toDouble}%.9g)"
  }
  object Dyadic {
    val Zero: Dyadic = new Dyadic(BigInt(0), 0)
    def apply(m: BigInt, e: Int): Dyadic =
      if (m.signum == 0) Zero
      else {
        var mm = m.abs; var ee = e
        while (mm > 1 && !mm.testBit(0)) { mm >>= 1; ee += 1 }
        new Dyadic(if (m.signum < 0) -mm else mm, ee)
      }
  }

  // 数学金锚（第一性原理 MX 语义，全程精确整数；禁拟合 RTL）
  /** 三格式解码旗标（与 WbSimCommon.mxToFloat 调用点逐条同源）：E4M3 nanNeedMantAllOne=true +
    * subnormalFtz=true；E5M2 nanNeedMantAllOne=false + subnormalFtz=true；E2M1 noInfNan=true +
    * subnormalFtz=false（e=0/m=1 = ±0.5 一等公民）。 */
  private final case class MxElemFmt(p: MxFormatParams, nanNeedMantAllOne: Boolean,
                                     noInfNan: Boolean, subnormalFtz: Boolean)
  private val FmtE4M3 = MxElemFmt(MxFormatParams.E4M3, nanNeedMantAllOne = true,  noInfNan = false, subnormalFtz = true)
  private val FmtE5M2 = MxElemFmt(MxFormatParams.E5M2, nanNeedMantAllOne = false, noInfNan = false, subnormalFtz = true)
  private val FmtE2M1 = MxElemFmt(MxFormatParams.E2M1, nanNeedMantAllOne = false, noInfNan = true,  subnormalFtz = false)
  private def fmtOf(d: DataFormat.Type): MxElemFmt =
    if (d == DataFormat.MXE4M3) FmtE4M3
    else if (d == DataFormat.MXE5M2) FmtE5M2
    else if (d == DataFormat.MXE2M1) FmtE2M1
    else { require(false, s"fmtOf: 非 MX 格式 $d（本 spec 只覆盖 MX-only 域）"); FmtE2M1 }

  /** MX 元素精确真值，分支序逐条对齐 WbSimCommon.mxToFloat：normal = ±(2^mantW + m) ×
    * 2^(expSigned−127−mantW)；E2M1 subnormal = ±1 × 2^(expSigned−127)（不乘 (1+m/2)）；fp8 e==0 /
    * expSigned < 1 ⟹ ±0；inf/nan 码或 expSigned > 254 ⟹ require 拒绝（这类向量只出现在 satVecs，
    * 走 block3 的 non-NaN 结构断言，不进金锚裁决）。 */
  private def mxElemDyadic(code: Int, scale: Int, f: MxElemFmt): Dyadic = {
    val p = f.p
    val b = code & 0xFF
    val s = (b >> (p.expW + p.mantW)) & 1
    val e = (b >> p.mantW) & ((1 << p.expW) - 1)
    val m = b & ((1 << p.mantW) - 1)
    val sgn = if (s == 1) -1 else 1
    val eMax = (1 << p.expW) - 1
    val mMax = (1 << p.mantW) - 1
    val isZero = (e == 0) && (m == 0)
    val isSub  = (e == 0) && (m != 0)
    val isInfNan = if (f.noInfNan) false
                   else if (f.nanNeedMantAllOne) (e == eMax && m == mMax) else (e == eMax)
    val expSigned = e + (scale & 0xFF) - p.bias
    require(!isInfNan, s"mxElemDyadic: 码 0x${b.toHexString} 是 inf/nan（out-of-spec），in-spec 金锚不覆盖")
    require(expSigned <= 254, s"mxElemDyadic: expSigned=$expSigned > 254 ⟹ 元素饱和（out-of-spec），请收紧 scale")
    if (isZero) Dyadic.Zero
    else if (expSigned < 1) Dyadic.Zero
    else if (isSub && f.subnormalFtz) Dyadic.Zero
    else if (isSub) Dyadic(BigInt(sgn), expSigned - 127)
    else Dyadic(BigInt(sgn) * ((BigInt(1) << p.mantW) + BigInt(m)), expSigned - 127 - p.mantW)
  }

  /** 精确真值 → fp32 bits（要求 ≤24-bit 有效位且 expField ∈ [1,254]，即【无舍入】可表示）。
    * MX 乘积恒满足（MX8 ≤8-bit、MX4 ≤4-bit 有效位）；require 失败 = 激励越域，非 RTL 问题。 */
  private def dyadicToFp32Bits(v: Dyadic): Long = {
    if (v.isZero) return 0L
    val s = if (v.signum < 0) 1L else 0L
    val mag = v.m.abs
    val bl = mag.bitLength
    require(bl <= 24, s"dyadicToFp32Bits: 有效位 $bl > 24 ⟹ 非 fp32 精确可表示（激励设计错误）v=$v")
    val expField = bl - 1 + v.e + 127
    require(expField >= 1 && expField <= 254,
      s"dyadicToFp32Bits: expField=$expField 越 fp32 normal 域 [1,254]（饱和/subnormal 属 out-of-spec）v=$v")
    val mant23 = ((mag << (24 - bl)) & BigInt(0x7FFFFF)).toLong
    ((s << 31) | (expField.toLong << 23) | mant23) & 0xFFFFFFFFL
  }

  /** 精确真值 → fp32 bits，单次 RNE 舍入（全程 BigInt，不经 Double 中转：若先塞 53-bit Double 再塞
    * 24-bit fp32，相消域两步舍入会污染金锚）。goldenExact=true 的向量金锚本就 fp32-精确 ⟹ 退化恒等。 */
  private def dyadicToFp32RNE(v: Dyadic): Long = {
    if (v.isZero) return 0L
    val s = if (v.signum < 0) 1L else 0L
    val mag = v.m.abs
    val bl = mag.bitLength
    val shift = bl - 24                       // >0 需右移舍入；≤0 左移无损
    var sig = mag
    var msbExp = bl - 1                       // sig 的 bit23 ↔ 2^(msbExp + v.e)
    if (shift > 0) {
      val trunc = mag >> shift
      val rem   = mag & ((BigInt(1) << shift) - 1)
      val half  = BigInt(1) << (shift - 1)
      val c     = rem.compare(half)
      sig = if (c > 0 || (c == 0 && trunc.testBit(0))) trunc + 1 else trunc   // RNE：>half 进位，=half 取偶
      if (sig.bitLength > 24) { sig >>= 1; msbExp += 1 }                      // 舍入进位 11..1 → 100..0
    } else if (shift < 0) {
      sig = mag << (-shift)
    }
    val expField = msbExp + v.e + 127
    require(expField >= 1 && expField <= 254,
      s"dyadicToFp32RNE: expField=$expField 越 fp32 normal 域 [1,254]（subnormal/饱和属 out-of-spec）v=$v")
    val mant23 = (sig & BigInt(0x7FFFFF)).toLong
    ((s << 31) | (expField.toLong << 23) | mant23) & 0xFFFFFFFFL
  }

  /** lazy-40 bits → 精确真值。radix：value = mant31 × 2^(exp−157)（bit30 ↔ 2^(exp−127)）；
    * 真零判据 exp==0 && mant31==0（与 WbSimCommon.fp32BitsTo40 / swFpNormalize40to32 同约定）。 */
  private def lazy40ToDyadic(raw: BigInt): Dyadic = {
    val s = ((raw >> 39) & 1).toInt
    val e = ((raw >> 31) & 0xFF).toInt
    val m31 = raw & ((BigInt(1) << 31) - 1)
    if (e == 0 && m31.signum == 0) Dyadic.Zero else Dyadic(BigInt(if (s == 1) -1 else 1) * m31, e - 157)
  }

  /** fp32-无损判据（精确整数版）：零，或有效位 ≤24 且 expField ∈ [1,254] ⟹ 恰为某 fp32 normal 真值。 */
  private def fp32Exact(v: Dyadic): Boolean =
    v.isZero || {
      val bl = v.m.abs.bitLength
      val ef = bl - 1 + v.e + 127
      bl <= 24 && ef >= 1 && ef <= 254
    }

  private def goldenBits(golden: Dyadic): Long =
    WbSimCommon.swFpNormalize40to32(WbSimCommon.fp32BitsTo40(dyadicToFp32RNE(golden)))

  private def cRegBits(cVal: Dyadic): BigInt =
    WbSimCommon.fp32BitsTo40(dyadicToFp32RNE(cVal))

  /** cReg 槽的实际驱动真值 = 由驱动位反解，而非未舍入的 v.cVal：DUT 收到的是 fp32BitsTo40(
    * dyadicToFp32RNE(cVal))，金锚/模型若参照 cVal 就形成双源不一致。goldenOf 与 analyzeNarrow 一律走
    * 本函数，保证 assert(mdl.exact == golden) 同源自洽。 */
  private def cExactOf(cVal: Dyadic): Dyadic = lazy40ToDyadic(cRegBits(cVal))

  private case class Vec(tag: String, aFmt: DataFormat.Type, bFmt: DataFormat.Type,
                         a: Int, b: Int, scaleA: Int, scaleB: Int,
                         isMx4: Boolean, cVal: Dyadic = Dyadic.Zero)

  /** 五槽 lane 乘积精确真值（与上游 FpMulMx8/FpMulMx4 的 in-spec 输出逐位同值）。MX4 = 4 个 nibble
    * （a[3:0]/a[7:4]/a[11:8]/a[15:12] → r0/r1/r2/r3）；MX8 = 2 个 byte（a[7:0] → r0、a[15:8] → r1，
    * r2/r3 被 Mux(aIsMx2M1,…) 掩零）。端口映射逐字读自 WbFusionMacFp.scala 的 mulL0/fp8Mul/fp4Mul。 */
  private def laneProducts(v: Vec): Seq[Dyadic] =
    if (v.isMx4) (0 until 4).map { i =>
      mxElemDyadic((v.a >> (4 * i)) & 0xF, v.scaleA, FmtE2M1) *
        mxElemDyadic((v.b >> (4 * i)) & 0xF, v.scaleB, FmtE2M1)
    }
    else Seq(
      mxElemDyadic(v.a & 0xFF, v.scaleA, fmtOf(v.aFmt)) *
        mxElemDyadic(v.b & 0xFF, v.scaleB, fmtOf(v.bFmt)),
      mxElemDyadic((v.a >> 8) & 0xFF, v.scaleA, fmtOf(v.aFmt)) *
        mxElemDyadic((v.b >> 8) & 0xFF, v.scaleB, fmtOf(v.bFmt)),
      Dyadic.Zero, Dyadic.Zero)

  /** lane 是否真正供数（= FpAddNarrow 的 actL1/actL2/actL3 激活掩码，is4x 优先于 is2x）。 */
  private def laneActive(v: Vec): Seq[Boolean] =
    if (v.isMx4) Seq(true, true, true, true) else Seq(true, true, false, false)

  private def goldenMx8(v: Vec): (Dyadic, Boolean) = {
    val p = laneProducts(v)
    // s0 用【实际驱动真值】cExactOf(v.cVal)（= 由 cRegBits 反解），与 DUT 收到的位同源。
    val s0 = cExactOf(v.cVal); val s1 = s0 + p(0); val s2 = s1 + p(1)
    val exact = fp32Exact(p(0)) && fp32Exact(p(1)) && fp32Exact(s0) && fp32Exact(s1) && fp32Exact(s2)
    (s2, exact)
  }

  private def goldenMx4(v: Vec): (Dyadic, Boolean) = {
    val p = laneProducts(v)
    // cReg 项用实际驱动真值 cExactOf(v.cVal)，消除「DUT 收 RNE(cVal)、金锚参照 cVal」双源。
    val cExact = cExactOf(v.cVal)
    val s01 = p(0) + p(1); val s23 = p(2) + p(3); val block = s01 + s23; val total = cExact + block
    val exact = p.forall(fp32Exact) && fp32Exact(s01) && fp32Exact(s23) &&
      fp32Exact(block) && fp32Exact(cExact) && fp32Exact(total)
    (total, exact)
  }

  private def goldenOf(v: Vec): (Dyadic, Boolean) = if (v.isMx4) goldenMx4(v) else goldenMx8(v)

  // FpAddNarrow 系统级解析模型：从激励独立重算 E / windowLsb / nLossy / k / absBound 以校验契约 ①③，
  // 与 FpAddNarrowSpec.analyze 同构（同一嵌套 floor 恒等式），全程不读 RTL。
  /** eCommon = E = 激活且非零槽 expField 的 max；lsb = windowLsb = 2^(E−126−W)；nLossy = 丢失 ≥1 bit
    * 的激活槽数（对齐 floor + term-drop 都计入）；nDrop = 整槽移出窗口（E − e ≥ W）的槽数；kGrow =
    * max(0, bitLength(|S|) − W)；absBound = (nLossy + 2^k − 1) × windowLsb（无条件成立）；mustRel =
    * 契约 ② 充分条件成立（⟹ 良态域，相对界必须达标）；lossless ⟹ 必须 diff == 0。 */
  private final case class NarrowModel(exact: Dyadic, eCommon: Int, lsb: Dyadic,
                                       nLossy: Int, nDrop: Int, magFull: BigInt, kGrow: Int,
                                       absBound: Dyadic, mustRel: Boolean, lossless: Boolean,
                                       expClamped: Boolean)

  private def analyzeNarrow(v: Vec, w: Int): NarrowModel = {
    val prods = laneProducts(v)
    val act   = laneActive(v)
    // 槽位模式：prod 槽 = fp32 位（fieldW=24，mag=Cat(1,mant23)）；cReg 槽 = lazy-40（fieldW=31，mag=mant31）
    val cBits = cRegBits(v.cVal)
    val cE = ((cBits >> 31) & 0xFF).toInt
    val cM = cBits & ((BigInt(1) << 31) - 1)
    val cS = ((cBits >> 39) & 1).toInt
    val cZero = (cE == 0) && (cM.signum == 0)
    // cReg 槽真值由实际驱动位反解，与 goldenMx8/goldenMx4 的 cExactOf 同源 ⟹ mdl.exact == golden 自洽。
    val cExact = lazy40ToDyadic(cBits)
    // (active, isZero, signBit, eField, mag, fieldW, 精确真值)
    type SlotM = (Boolean, Boolean, Int, Int, BigInt, Int, Dyadic)
    val cSlot: SlotM = (true, cZero, cS, cE, cM, 31, cExact)
    val pSlots: Seq[SlotM] = (0 until 4).map { i =>
      val bits = if (prods(i).isZero) 0L else dyadicToFp32Bits(prods(i))
      val e = ((bits >> 23) & 0xFF).toInt
      val m = (bits & 0x7FFFFF).toInt
      val sg = ((bits >> 31) & 1).toInt
      val z = (e == 0) && (m == 0)
      (act(i), z, sg, e, (BigInt(1) << 23) | BigInt(m), 24, prods(i))
    }
    val live = (cSlot +: pSlots).filter(s => s._1 && !s._2)
    val eCommon = if (live.isEmpty) 0 else live.map(_._4).max
    val lsb = Dyadic(BigInt(1), eCommon - 126 - w)

    var nLossy = 0
    var nDrop = 0
    var signedSum = BigInt(0)
    live.foreach { s =>
      val sh = math.min(math.max(eCommon - s._4, 0), w)
      val ts = (s._6 - w) + sh
      val a = s._5 >> ts
      // ts 可为负时 (1<<ts)−1 退化为全 1 ⟹ 误判 lossy、nLossy 虚增 ⟹ 用 lost=max(0,ts)。当前仅以
      //   w=W=19 调用（ts 恒 ≥0），此为防御性硬化，杜绝未来放宽 w 时的静默记账漏洞。
      val lost = math.max(0, ts)
      if (lost > 0 && (s._5 & ((BigInt(1) << lost) - 1)).signum != 0) nLossy += 1
      if (eCommon - s._4 >= w) nDrop += 1
      signedSum += (if (s._3 == 1) -a else a)
    }
    val exact = live.foldLeft(Dyadic.Zero)((acc, s) => acc + s._7)

    val magFull = signedSum.abs
    val k = math.max(0, magFull.bitLength - w)
    val expClamped = (eCommon + k) > FpAddNarrow.ExpSatMax
    val truncMax = (BigInt(1) << k) - 1                       // |S| mod 2^k 的上界（截断误差 ≤ 此值 × lsb）
    val absBound = Dyadic(BigInt(nLossy) + truncMax, lsb.e)   // 契约 ①：|diff| ≤ 此值（无条件）
    // 契约 ② 充分条件（纯整数）：absBound × 2^(W−1) ≤ |exact| ⟹ relErr ≤ 2^−(W−1)。成立 ⟹ 本向量属
    //   良态域，相对界是必须达标的硬承诺，不是可选路径。
    val mustRel = absBound.shl(w - 1).leAbs(exact)
    val lossless = (nLossy == 0) && (k == 0) && !expClamped
    NarrowModel(exact, eCommon, lsb, nLossy, nDrop, magFull, k, absBound, mustRel, lossless, expClamped)
  }

  // MX4 in-spec 向量（bit-exact）。逐位断言的前提两条缺一不可：(a) lane 侧无损 —— 四 lane 共享
  //   scaleA/scaleB ⟹ expField 跨度 ≤7 << W=19，且 MX4 乘积 mant23 低 20 位结构性 0 ⟹ ts ≤ 20；
  //   (b) cReg 侧无损 —— 本组 cReg ∈ {0, C_ONE=1.0} ⟹ mant31 低 31−W=12 位为 0 且 E − cE < W。
  // 这是激励受限的结论，非 MX4 全域性质：cReg 换满尾数 fp32 或被 E 挤出窗口即 diff ≠ 0（①/③ 兜底），
  //   反例见本文件 n_mx8_cReg_spread 与 FpAddNarrowSpec 的 DR3/G2b/G3b/LZ2/ML2。
  private val E2 = DataFormat.MXE2M1
  private val E4 = DataFormat.MXE4M3
  private val E5 = DataFormat.MXE5M2
  /** cReg 初值 1.0 的精确载体：1.0 = 1×2^0 是 fp32-精确值 ⟹ mant31 低 31−W=12 位结构性为 0 ⟹ 常量
    * 提取 (m31 >> CSh) 零损失。但这不等于 cReg 槽在整个窄窗口无损：若 E 比 cReg 的 expField 高出 ≥ W
    * （n_mx8_cReg_spread：E=154 vs cE=127，差 27 ≥ 19），动态对齐仍把 cReg 整槽挤出窗口 ⟹ nLossy ≥ 1
    * ⟹ 只能由契约 ① 兜底 —— 这也是 bit-exact 只能限定在 lane fraction-sum 无损域的原因。 */
  private val C_ONE: Dyadic = Dyadic(BigInt(1), 0)

  private val mx4Vecs: Seq[Vec] = Seq(
    Vec("n_mx4_prod_4_6_9", E2, E2,
      mx4(e2m1(0,2,0), e2m1(0,2,0), e2m1(0,2,1), e2m1(0,2,1)),
      mx4(e2m1(0,2,0), e2m1(0,2,1), e2m1(0,2,0), e2m1(0,2,1)), 127, 127, true),
    Vec("n_mx4_e2_spread", E2, E2,
      mx4(e2m1(0,0,1), e2m1(0,1,1), e2m1(0,2,1), e2m1(0,3,0)),
      mx4(e2m1(0,3,1), e2m1(0,2,0), e2m1(0,1,1), e2m1(0,0,0)), 127, 127, true),
    Vec("n_mx4_subnormal", E2, E2,
      mx4(e2m1(0,0,1), e2m1(0,0,1), e2m1(1,0,1), e2m1(0,1,0)),
      mx4(e2m1(0,0,1), e2m1(1,0,1), e2m1(0,0,1), e2m1(0,1,1)), 127, 127, true),
    Vec("n_mx4_signs", E2, E2,
      mx4(e2m1(1,2,1), e2m1(0,2,1), e2m1(1,3,0), e2m1(0,1,1)),
      mx4(e2m1(0,2,1), e2m1(1,2,0), e2m1(0,3,1), e2m1(1,1,1)), 127, 127, true),
    Vec("n_mx4_scaleHi", E2, E2,
      mx4(e2m1(0,3,1), e2m1(0,2,0), e2m1(0,3,1), e2m1(0,1,1)),
      mx4(e2m1(0,3,1), e2m1(0,2,1), e2m1(0,1,0), e2m1(0,3,1)), 200, 140, true),
    Vec("n_mx4_scaleCeil", E2, E2,
      mx4(e2m1(0,2,1), e2m1(0,3,0), e2m1(0,2,1), e2m1(0,3,1)),
      mx4(e2m1(0,2,0), e2m1(0,3,1), e2m1(0,2,1), e2m1(0,1,0)), 238, 120, true),
    Vec("n_mx4_cReg1", E2, E2,
      mx4(e2m1(0,2,1), e2m1(0,3,0), e2m1(0,1,1), e2m1(0,2,0)),
      mx4(e2m1(0,3,1), e2m1(0,2,1), e2m1(0,3,0), e2m1(0,1,1)), 127, 127, true, C_ONE)
  )

  // MX8 in-spec 向量（bit-exact：有效位 ≤9 << W=19 且指数跨度小 → nLossy=0）
  private val mx8ExactVecs: Seq[Vec] = Seq(
    Vec("n_mx8_e4m3_m00_07", E4, E4,
      mx8(e4m3(0,7,0), e4m3(0,7,7)), mx8(e4m3(0,7,0), e4m3(0,7,7)), 127, 127, false),
    Vec("n_mx8_e4m3_signs", E4, E4,
      mx8(e4m3(1,7,3), e4m3(0,7,5)), mx8(e4m3(0,7,2), e4m3(1,7,7)), 127, 127, false),
    Vec("n_mx8_e4m3_expdiff3", E4, E4,
      mx8(e4m3(0,7,1), e4m3(0,10,1)), mx8(e4m3(0,7,1), e4m3(0,10,1)), 127, 127, false),
    Vec("n_mx8_e4m3_cReg1", E4, E4,
      mx8(e4m3(0,7,2), e4m3(0,7,2)), mx8(e4m3(0,7,3), e4m3(0,7,3)), 127, 127, false, C_ONE),
    Vec("n_mx8_e5m2_m0_3", E5, E5,
      mx8(e5m2(0,15,0), e5m2(0,15,3)), mx8(e5m2(0,15,0), e5m2(0,15,3)), 127, 127, false),
    Vec("n_mx8_e5m2_signs", E5, E5,
      mx8(e5m2(1,15,1), e5m2(0,15,3)), mx8(e5m2(0,15,2), e5m2(1,15,3)), 160, 160, false),
    Vec("n_mx8_mix_e4xe5", E4, E5,
      mx8(e4m3(0,7,0), e4m3(0,7,7)), mx8(e5m2(0,15,0), e5m2(0,15,3)), 127, 127, false),
    Vec("n_mx8_scaleHi", E4, E4,
      mx8(e4m3(0,7,7), e4m3(0,7,0)), mx8(e4m3(0,7,5), e4m3(0,7,2)), 190, 140, false),
    Vec("n_mx8_scaleCeil", E4, E4,
      mx8(e4m3(0,7,3), e4m3(0,7,6)), mx8(e4m3(0,7,3), e4m3(0,7,6)), 238, 100, false)
  )

  // MX8 宽指数跨度向量（契约验证：nLossy > 0 → 用相对误差 / 绝对界判定）
  // 构造：lane0 高指数 E5M2(e=30) × E5M2(e=30) vs lane1 低指数 E5M2(e=2) × E5M2(e=2)
  //   → 乘积 expField 跨度 ≈ (30-15)+(30-15) - ((2-15)+(2-15)) = 30+26 = 56 >> W=19
  //   → lane1 完全 term-drop → nLossy ≥ 1 → 验证良态域相对界 or 绝对界兜底
  private val mx8SpreadVecs: Seq[Vec] = Seq(
    // 宽跨度：lane0 exp 远大于 lane1 → lane1 被 term-drop（良态：大项主导，relErr 很小）
    Vec("n_mx8_spread30", E5, E5,
      mx8(e5m2(0,30,3), e5m2(0,2,0)), mx8(e5m2(0,30,3), e5m2(0,2,0)), 127, 127, false),
    Vec("n_mx8_spread20", E5, E5,
      mx8(e5m2(0,25,3), e5m2(0,5,0)), mx8(e5m2(0,25,3), e5m2(0,5,0)), 127, 127, false),
    Vec("n_mx8_spread15", E4, E4,
      mx8(e4m3(0,14,7), e4m3(0,1,0)), mx8(e4m3(0,14,7), e4m3(0,1,0)), 127, 127, false),
    // 中等跨度（恰在 W 边界附近）：可能 nLossy=1 → relErr 接近但不超 2^-(W-1)
    Vec("n_mx8_spread10", E5, E5,
      mx8(e5m2(0,20,3), e5m2(0,10,0)), mx8(e5m2(0,20,3), e5m2(0,10,0)), 127, 127, false),
    // 异号相消域：lane0 正大 + lane1 负大 → |exact| 很小 → 条件数放大（契约 ③ 兜底）
    Vec("n_mx8_cancel", E4, E4,
      mx8(e4m3(0,10,7), e4m3(1,10,7)), mx8(e4m3(0,10,7), e4m3(0,10,6)), 127, 127, false),
    // cReg 非零 + 宽跨度（3 活跃槽）：E=154 而 cReg expField=127 ⟹ 差 27 ≥ W=19 ⟹ cReg 整槽 term-drop
    //   （nLossy=2：cReg + lane1）⟹ 契约 ② 充分条件不成立 ⟹ 落 J3 绝对界兜底。这是「系统级并非全域
    //   bit-exact」的活体反例：同 block 内 MX8 lane 侧无损，输出仍 diff ≠ 0。
    Vec("n_mx8_cReg_spread", E5, E5,
      mx8(e5m2(0,28,3), e5m2(0,3,0)), mx8(e5m2(0,28,3), e5m2(0,3,0)), 127, 127, false, C_ONE)
  )

  // 饱和域向量（out-of-spec：验证输出非 NaN + lazy-40 expField ≤ 254）
  private val satVecs: Seq[Vec] = Seq(
    Vec("n_sat_mx4_scaleA253", E2, E2,
      mx4(e2m1(0,3,1), e2m1(0,3,0), e2m1(0,2,1), e2m1(0,3,1)),
      mx4(e2m1(0,2,1), e2m1(0,3,1), e2m1(0,2,0), e2m1(0,3,1)), 253, 127, true),
    Vec("n_sat_mx4_scaleA254", E2, E2,
      mx4(e2m1(0,3,1), e2m1(0,2,1), e2m1(0,3,0), e2m1(0,2,1)),
      mx4(e2m1(0,3,1), e2m1(0,2,0), e2m1(0,3,1), e2m1(0,2,1)), 254, 127, true),
    Vec("n_sat_mx8_e4m3_scaleA250", E4, E4,
      mx8(e4m3(0,15,0), e4m3(0,15,0)), mx8(e4m3(0,1,0), e4m3(0,1,0)), 250, 127, false),
    Vec("n_sat_mx8_e5m2_infcode", E5, E5,
      mx8(e5m2(0,31,0), e5m2(0,31,0)), mx8(e5m2(0,5,0), e5m2(0,5,0)), 127, 127, false),
    // 极端饱和：scaleA=253 + MX8 → 多槽同时饱和
    Vec("n_sat_mx8_extreme", E4, E4,
      mx8(e4m3(0,15,6), e4m3(0,15,6)), mx8(e4m3(0,15,6), e4m3(0,15,6)), 253, 253, false)
  )

  private def driveVec(dut: WbFusionMacFp, v: Vec): BigInt = {
    dut.io.aFmt.poke(v.aFmt)
    dut.io.bFmt.poke(v.bFmt)
    dut.io.a.poke(v.a.U)
    dut.io.b.poke(v.b.U)
    dut.io.scaleA.poke(v.scaleA.U)
    dut.io.scaleB.poke(v.scaleB.U)
    dut.io.cReg.poke(cRegBits(v.cVal).U)
    dut.io.rnd.poke(RoundingMode.RNE)
    dut.io.consumeValid.poke(false.B)
    dut.clock.step(1)
    dut.io.sum.peek().litValue
  }

  private def resetDut(dut: WbFusionMacFp): Unit = {
    dut.reset.poke(true.B)
    dut.io.aFmt.poke(DataFormat.MXE4M3); dut.io.bFmt.poke(DataFormat.MXE4M3)
    dut.io.a.poke(0.U); dut.io.b.poke(0.U)
    dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
    dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
    dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
  }

  behavior of "WbFusionMacFp narrow config (useNarrowAdd=true, FpAddNarrow W=19) system-level verification"

  it should "produce bit-exact output for MX4 in-spec vectors with lossless cReg slot (lane exponent spread ≤7 << W AND cReg in {0,1.0} => nLossy=0; NOT an MX4 whole-domain claim)" in {
    simulate(new WbFusionMacFp(supportedFmts = FMTS_MXONLY, useNarrowAdd = true)) { dut =>
      resetDut(dut)
      var pass = 0
      for (v <- mx4Vecs) {
        val sumRaw = driveVec(dut, v)
        val actualFp32 = WbSimCommon.swFpNormalize40to32(sumRaw)
        val (golden, exact) = goldenOf(v)
        assert(exact, s"[${v.tag}] DESIGN ERROR: goldenExact=false")
        val exp = goldenBits(golden)
        assert(actualFp32 == exp,
          f"[${v.tag}] MX4 BIT-EXACT FAIL: golden=${golden.toString} exp=0x$exp%08X act=0x$actualFp32%08X raw=0x${sumRaw.toString(16)}")
        // 额外验证：lazy-40 expField ≤ ExpSatMax（无条件不变量）
        val eOut = ((sumRaw >> 31) & 0xFF).toInt
        assert(eOut <= FpAddNarrow.ExpSatMax,
          s"[${v.tag}] 无条件不变量违背：eOut=$eOut > ExpSatMax=${FpAddNarrow.ExpSatMax}")
        pass += 1
      }
      println(s"[NarrowSpec] MX4 in-spec bit-exact PASS $pass/${mx4Vecs.size}")
    }
  }

  it should "produce bit-exact output for MX8 in-spec vectors with goldenExact=true (effective bits ≤9 << W)" in {
    simulate(new WbFusionMacFp(supportedFmts = FMTS_MXONLY, useNarrowAdd = true)) { dut =>
      resetDut(dut)
      var pass = 0
      for (v <- mx8ExactVecs) {
        val sumRaw = driveVec(dut, v)
        val actualFp32 = WbSimCommon.swFpNormalize40to32(sumRaw)
        val (golden, exact) = goldenOf(v)
        assert(exact, s"[${v.tag}] DESIGN ERROR: goldenExact=false")
        val exp = goldenBits(golden)
        assert(actualFp32 == exp,
          f"[${v.tag}] MX8 BIT-EXACT FAIL: golden=${golden.toString} exp=0x$exp%08X act=0x$actualFp32%08X raw=0x${sumRaw.toString(16)}")
        val eOut = ((sumRaw >> 31) & 0xFF).toInt
        assert(eOut <= FpAddNarrow.ExpSatMax,
          s"[${v.tag}] 无条件不变量违背：eOut=$eOut > ExpSatMax=${FpAddNarrow.ExpSatMax}")
        pass += 1
      }
      println(s"[NarrowSpec] MX8 in-spec (goldenExact) bit-exact PASS $pass/${mx8ExactVecs.size}")
    }
  }

  it should "keep saturation domain output finite (non-NaN) with lazy-40 expField ≤ ExpSatMax=254 (task#18 A bug fix verification)" in {
    simulate(new WbFusionMacFp(supportedFmts = FMTS_MXONLY, useNarrowAdd = true)) { dut =>
      resetDut(dut)
      var pass = 0
      for (v <- satVecs) {
        val sumRaw = driveVec(dut, v)
        val fp32 = WbSimCommon.swFpNormalize40to32(sumRaw)
        val expField = ((fp32 >>> 23) & 0xFF).toInt
        val mantField = (fp32 & 0x7FFFFF).toInt
        val isNaN = expField == 0xFF && mantField != 0
        val isInf = expField == 0xFF && mantField == 0
        // lazy-40 层面的不变量
        val eOut40 = ((sumRaw >> 31) & 0xFF).toInt
        // 核心验证：expOut=255 + mant31≠0 会归一化成 NaN，钳到 254 才保证有限
        assert(!isNaN,
          f"[${v.tag}] ★ SATURATION BUG NOT FIXED: output NaN! fp32=0x$fp32%08X sumRaw=0x${sumRaw.toString(16)}")
        assert(eOut40 <= FpAddNarrow.ExpSatMax,
          s"[${v.tag}] ★ 无条件不变量违背：lazy-40 eOut=$eOut40 > ExpSatMax=${FpAddNarrow.ExpSatMax}")
        println(f"[NarrowSpec] SAT ${v.tag}: fp32=0x$fp32%08X raw=0x${sumRaw.toString(16)} " +
          f"eOut40=$eOut40 finite=${!isInf && !isNaN} PASS")
        pass += 1
      }
      println(s"[NarrowSpec] Saturation domain non-NaN PASS $pass/${satVecs.size} (task#18 A bug fix verified)")
    }
  }

  it should "enforce the three-part precision contract on MX8 wide-spread vectors with per-vector true classification (J1 exact / J2 rel-bound / J3 abs-bound fallback)" in {
    simulate(new WbFusionMacFp(supportedFmts = FMTS_MXONLY, useNarrowAdd = true)) { dut =>
      resetDut(dut)
      var nJ1 = 0; var nJ2 = 0; var nJ3 = 0
      val rows = mutable.ArrayBuffer.empty[String]
      for (v <- mx8SpreadVecs) {
        val sumRaw      = driveVec(dut, v)
        val actualFp32  = WbSimCommon.swFpNormalize40to32(sumRaw)
        val (golden, _) = goldenOf(v)
        val eOut40      = ((sumRaw >> 31) & 0xFF).toInt

        // ── 无条件不变量（与三段归类正交）──
        assert(eOut40 <= FpAddNarrow.ExpSatMax,
          s"[${v.tag}] 无条件不变量违背：eOut=$eOut40 > ExpSatMax=${FpAddNarrow.ExpSatMax}")
        val expField  = ((actualFp32 >>> 23) & 0xFF).toInt
        val mantField = (actualFp32 & 0x7FFFFF).toInt
        assert(!(expField == 0xFF && mantField != 0),
          f"[${v.tag}] 输出 NaN：fp32=0x$actualFp32%08X")

        // ── 解析模型：从【激励】独立重算 E / windowLsb / nLossy / nDrop / k / absBound（全程不读 RTL）──
        val mdl = analyzeNarrow(v, W)
        // J0 模型自洽：逐槽真值和与张量语义和必须结构相等（两条独立路径互验，防模型自身写错）
        assert(mdl.exact == golden,
          s"[${v.tag}] J0 模型不自洽：analyzeNarrow.exact=${mdl.exact} != goldenOf=$golden")
        // 契约 ① 的推导前提是 expOut = E + k 未被钳位（钳到 254 会改变输出量级）；本组全为 in-spec 非饱和向量
        assert(!mdl.expClamped,
          s"[${v.tag}] E=${mdl.eCommon} + k=${mdl.kGrow} > ExpSatMax=${FpAddNarrow.ExpSatMax} " +
            "⟹ 属饱和域，契约 ① 不适用（该向量应移入 satVecs）")

        // RTL 输出的【精确】真值（lazy-40 → Dyadic，零浮点）与精确误差
        val got  = lazy40ToDyadic(sumRaw)
        val diff = got - mdl.exact

        // ── (i) 契约 ① 无条件绝对界：|diff| ≤ absBound = (nLossy + 2^k − 1) × 2^(E−126−W) ──
        assert(diff.abs.leAbs(mdl.absBound),
          s"[${v.tag}] ★ 契约 ① 违背：|diff|=${diff.abs} > absBound=${mdl.absBound} " +
            s"(E=${mdl.eCommon} W=$W windowLsb=${mdl.lsb} nLossy=${mdl.nLossy} nDrop=${mdl.nDrop} " +
            s"k=${mdl.kGrow} got=$got exact=${mdl.exact})")

        // ── (ii) 逐向量归入【恰好一条】路径，并施加该路径的强断言 ──
        val path =
          if (mdl.lossless) {
            // J1 精确域：nLossy==0 ∧ k==0 ∧ 未钳位 ⟹ 窄窗口零信息损失 ⟹ 语义要求 diff == 0
            assert(diff.isZero,
              s"[${v.tag}] ★ J1 违背：nLossy=0 ∧ k=0 但 diff=$diff != 0（got=$got exact=${mdl.exact}）")
            nJ1 += 1; "J1-exact"
          } else if (mdl.mustRel) {
            // J2 良态域：absBound × 2^(W−1) ≤ |exact| ⟹ 相对界是硬承诺，必须实测达标（整数等价式）
            assert(diff.shl(W - 1).abs.leAbs(mdl.exact),
              s"[${v.tag}] ★ 契约 ② 违背：|diff|×2^${W - 1}=${diff.shl(W - 1).abs} > |exact|=${mdl.exact.abs} " +
                s"⟹ relErr > 2^-${W - 1}（=$REL_THRESHOLD），而本向量属良态域（absBound=${mdl.absBound}）")
            nJ2 += 1; "J2-rel"
          } else {
            // J3 相消/多损域：契约 ③ —— 任意有限 W 都无相对界，只由上面 (i) 的绝对界兜底（不声称相对界）
            nJ3 += 1; "J3-abs"
          }

        val relDisp =
          if (mdl.exact.isZero) "n/a(exact=0)"
          else f"${diff.toDouble / mdl.exact.toDouble}%.3e"   // 仅报告用；判定不经此路
        rows += s"[NarrowSpec] SPREAD ${v.tag} path=$path E=${mdl.eCommon} " +
          s"nLossy=${mdl.nLossy} nDrop=${mdl.nDrop} k=${mdl.kGrow} windowLsb=${mdl.lsb} " +
          s"|diff|=${diff.abs} absBound=${mdl.absBound} relErr=$relDisp"
      }
      rows.foreach(println)
      println(s"[NarrowSpec] MX8 wide-spread 三段契约：J1(exact)=$nJ1 J2(rel≤2^-${W - 1}=$REL_THRESHOLD)=$nJ2 " +
        s"J3(abs-bound 兜底)=$nJ3 total=${mx8SpreadVecs.size}")
      // ── (iii) 汇总：每条向量都被【真实归类】──
      (nJ1 + nJ2 + nJ3) shouldBe mx8SpreadVecs.size
      // 激励意图自检：本组必须同时覆盖 J2 与 J3，否则向量退化（全部 lossless ⟹ 契约 ③ 无人验证）。
      nJ2 should be > 0
      nJ3 should be > 0
    }
  }

  it should "pin the class-default supportedFmts to MX-only (D1: emitted RTL carries no fp16/bf16 wide array FpMulFp16Bf16)" in {
    // 钉死类默认契约：不显式传 supportedFmts 即 MX-only（hasFp=false）⟹ 发射的 RTL 不含 fp16/bf16
    //   宽阵列（FpMulFp16Bf16 / FpCvtFp16ToFp32 / FpCvtBf16ToFp32），且含 lane0 专属窄乘法器 FpMulMx8。
    val rtlDefault = _root_.circt.stage.ChiselStage.emitSystemVerilog(new WbFusionMacFp())
    rtlDefault should not include "FpMulFp16Bf16"
    rtlDefault should not include "FpCvtFp16ToFp32"
    rtlDefault should not include "FpCvtBf16ToFp32"
    withClue("类默认 MX-only 应例化 lane0 窄乘法器 FpMulMx8（hasFp=false 指纹）：") {
      rtlDefault should include("FpMulMx8")
    }
    // 反证（判据有判别力、非恒真）：显式传全五格式 ⟹ hasFp=true ⟹ 宽阵列必须出现。此处 pin
    //   useNarrowAdd=false，因为全五格式 + narrow 会撞护栏 require，而本段意图与加法器选型正交。
    val rtlFull5 = _root_.circt.stage.ChiselStage.emitSystemVerilog(
      new WbFusionMacFp(supportedFmts = Set(DataFormat.FP16, DataFormat.BF16,
        DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1), useNarrowAdd = false))
    withClue("全五格式（hasFp=true）本应例化宽阵列 FpMulFp16Bf16（否则上方 not include 判据无意义）：") {
      rtlFull5 should include("FpMulFp16Bf16")
    }
    println("[NarrowSpec] D1 class-default MX-only pin PASS: default RTL has FpMulMx8 & no FpMulFp16Bf16; " +
      "full-5 RTL has FpMulFp16Bf16 (判据有判别力)")
  }

  it should "REJECT useNarrowAdd=true with FP16 in supportedFmts at elaboration time (require guard)" in {
    // 护栏验证：useNarrowAdd=true + 越界格式 → elaboration 期 Scala IllegalArgumentException
    val ex = intercept[IllegalArgumentException] {
      // 触发 elaboration（ChiselStage 会在构造时检查 require）
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacFp(
          supportedFmts = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
          useNarrowAdd = true
        )
      )
    }
    // 护栏判据是【白名单】`supportedFmts ⊆ MX(MXE4M3/MXE5M2/MXE2M1) + BF16`，期望子串与
    //   WbFusionMacFp 的 require 文案单一真源对齐。BF16 可入白名单的依据：FpMulBf16Mx 的 mant23
    //   低 8 位结构性为零 ⟹ mag24 有效位只占 [23:8]，W=19 窗口 mag24>>5 保留全部有效位，最小无损
    //   W=16。⟹ 本用例的拒绝原因只剩 FP16；MX+BF16 的【放行】断言见下一个用例。
    ex.getMessage should include("FpAddNarrow 仅支持 MX(MXE4M3/MXE5M2/MXE2M1) + BF16")
    println(s"[NarrowSpec] require guard (FP16 out-of-whitelist) PASS: ${ex.getMessage.take(90)}...")

    // 负向用例：含 INT 的格式集（hasFp 仍为 false）必须被白名单【拒绝】—— 「白名单堵住 INT/FP32
    //   集绕过」的活体证据（仅看 hasFp 的判据会放行）。
    val ex2 = intercept[IllegalArgumentException] {
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacFp(
          supportedFmts = Set(DataFormat.INT8, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
          useNarrowAdd = true
        )
      )
    }
    ex2.getMessage should include("FpAddNarrow 仅支持 MX(MXE4M3/MXE5M2/MXE2M1) + BF16")
    println(s"[NarrowSpec] require guard (INT8 bypass) PASS: ${ex2.getMessage.take(90)}...")
  }

  it should "ACCEPT useNarrowAdd=true with MX + BF16 (whitelist widened; BF16 lane0 fits the W=19 window)" in {
    // 白名单含 BF16 的活体证据（与上一用例的两条拒绝断言互补）：MX + BF16 必须 elaborate 成功，
    //   且 lane0 走窄路 FpMulBf16Mx（不是 fp32 宽阵列 FpMulFp16Bf16）。无损性依据见上一用例注释；
    //   W=15 会被 narrowW>=16 的窗口 require 拒绝 ⟹ 同时钉死「默认 W=19 可用」。
    val rtl = _root_.circt.stage.ChiselStage.emitSystemVerilog(
      new WbFusionMacFp(
        supportedFmts = Set(DataFormat.BF16, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
        useNarrowAdd = true
      )
    )
    withClue("MX+BF16 窄路应例化 BF16 专属窄乘法器 FpMulBf16Mx：") {
      rtl should include("FpMulBf16Mx")
    }
    withClue("MX+BF16 窄路不得退化成 fp32 宽阵列（那是 hasFp16 分支）：") {
      rtl should not include "FpMulFp16Bf16"
    }
    withClue("窄加法器仍须是 FpAddNarrow（非 FpAdd）：") {
      rtl should include("FpAddNarrow")
    }
    println("[NarrowSpec] whitelist widening PASS: MX+BF16 accepted, lane0=FpMulBf16Mx, adder=FpAddNarrow")

    // 窗口下界守护：narrowW=15 < 16 必须拒（BF16 mag24 有效位 16 位，W=16 为最小无损窗口）。
    val exW = intercept[IllegalArgumentException] {
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacFp(
          supportedFmts = Set(DataFormat.BF16, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
          useNarrowAdd = true,
          narrowW = 15
        )
      )
    }
    println(s"[NarrowSpec] BF16 narrowW>=16 window guard PASS: ${exW.getMessage.take(120)}...")
  }
}

