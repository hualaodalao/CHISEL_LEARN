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

    // 指数相加 - bias(127)
    val eSum = eA +& eB // 9-bit to avoid overflow
    val eOut = Mux(eSum >= 127.U, (eSum - 127.U)(7, 0), 0.U(8.W))

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

  /** fp32 加法（简化版：正常数 + 零，不处理特殊值）
    * @param rnd 舍入模式
    */
  def add(a: UInt, b: UInt, rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {
    val sA = sign(a); val eA = exp(a); val mA = mant(a)
    val sB = sign(b); val eB = exp(b); val mB = mant(b)

    val aZero = (eA === 0.U) && (mA === 0.U)
    val bZero = (eB === 0.U) && (mB === 0.U)

    // 对齐指数：小指数向大指数对齐
    val eMax = Mux(eA >= eB, eA, eB)
    val shiftA = Mux(eA >= eB, 0.U(5.W), (eB - eA)(4, 0))
    val shiftB = Mux(eB >= eA, 0.U(5.W), (eA - eB)(4, 0))

    // 25-bit mantissa (1 implicit + 23 + 1 guard)
    val mA25 = Cat(1.U(1.W), mA, 0.U(1.W)) >> shiftA
    val mB25 = Cat(1.U(1.W), mB, 0.U(1.W)) >> shiftB

    // 26-bit signed mantissa (1 guard + 25)
    val mA26 = Cat(0.U(1.W), mA25).asSInt
    val mB26 = Cat(0.U(1.W), mB25).asSInt
    val mA_signed = Mux(sA, -mA26, mA26)
    val mB_signed = Mux(sB, -mB26, mB26)
    val mSum = mA_signed +& mB_signed // 27-bit SInt

    val sOut = mSum(26)
    val mAbs = Mux(mSum(26), (-mSum)(25, 0), mSum(25, 0))

    // 简化规格化：找最高有效位
    val leadingZero = PriorityEncoder(mAbs.asBools.reverse)
    val mNorm = (mAbs << leadingZero)(24, 2) // 23-bit mantissa（去掉隐含 1）
    val guardBit = (mAbs << leadingZero)(1) // 被截断的最高位
    val mRounded = applyRounding(mNorm, guardBit, sOut, rnd)
    val eOut = Mux(eMax > leadingZero, eMax - leadingZero, 0.U(8.W))

    val sumZero = (mAbs === 0.U)
    Mux(aZero, b,
      Mux(bZero, a,
        Mux(sumZero, 0.U(32.W),
          pack(sOut, eOut, mRounded))))
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
