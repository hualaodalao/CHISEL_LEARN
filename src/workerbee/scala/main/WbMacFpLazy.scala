/** HiveMacFpLazy — 宽累加器 + 延迟规格化 浮点 MAC
  *
  * 设计策略：累加路径（io.acc）跳过 LZC + 可变左移规格化，仅处理
  * 上溢（加法进位），从而节省面积；规格化仅在最终读出（io.out）时执行一次。
  *
  * 40-bit 宽累加器格式：
  *   [39]     sign (1-bit)
  *   [38:31]  exponent (8-bit, fp32 bias=127)
  *   [30:0]   mantissa (31-bit): 宽于 fp32 的 24-bit，
  *            允许 MSB 不在 bit 30（cancellation 后有 leading zeros）
  *            低位有 guard bits（对齐移位时保留精度）
  *
  * 纯组合逻辑（无寄存器）。
  */

import chisel3._
import chisel3.util._

class HiveMacFpLazy extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(32.W))   // fp32 multiplicand
    val b   = Input(UInt(32.W))   // fp32 multiplier
    val c   = Input(UInt(40.W))   // 宽累加器输入（前一次累加结果）
    val rnd = Input(RoundingMode())
    val acc = Output(UInt(40.W))  // 宽累加器输出（写回寄存器用）
    val out = Output(UInt(32.W))  // 规格化 fp32 输出（最终读出用）
  })

  // ========================================================================
  // 1. 乘法：复用 Fp32.mul 得到标准 fp32 乘积（32-bit）
  // ========================================================================
  val p = Fp32.mul(io.a, io.b, io.rnd)

  // ========================================================================
  // 2. 解包累加器 c
  // ========================================================================
  val cSign = io.c(39)
  val cExp  = io.c(38, 31) // 8-bit
  val cMant = io.c(30, 0)  // 31-bit

  // ========================================================================
  // 3. 解包乘积 p（已规格化 fp32，隐含 1 在最高位）
  // ========================================================================
  val pSign = Fp32.sign(p)
  val pExp  = Fp32.exp(p)   // 8-bit
  // 24-bit fp32 mantissa 扩展到 31-bit: {1, mant23, 7'b0}
  val pMant31 = Cat(1.U(1.W), Fp32.mant(p), 0.U(7.W)) // 31-bit

  // ========================================================================
  // 4. 对齐（桶形移位器，clamp 到 31 位）
  // ========================================================================
  // 有符号指数差
  val pExpExt = Cat(0.U(1.W), pExp) // 9-bit
  val cExpExt = Cat(0.U(1.W), cExp) // 9-bit
  val expDiffSigned = pExpExt.asSInt - cExpExt.asSInt // 10-bit signed

  val pGtC = expDiffSigned > 0.S
  val cGtP = expDiffSigned < 0.S

  // 移位量 clamp 到 31
  val absDiff = Mux(pGtC, expDiffSigned.asUInt, (-expDiffSigned).asUInt)(7, 0)
  val shiftAmt = Mux(absDiff > 31.U, 31.U(5.W), absDiff(4, 0))

  // 对齐后的尾数
  val pMantAligned = Wire(UInt(31.W))
  val cMantAligned = Wire(UInt(31.W))
  val baseExp      = Wire(UInt(8.W))

  when(pGtC) {
    // pExp > cExp: c 右移, baseExp = pExp
    pMantAligned := pMant31
    cMantAligned := Mux(absDiff > 31.U, 0.U(31.W), (cMant >> shiftAmt)(30, 0))
    baseExp      := pExp
  }.elsewhen(cGtP) {
    // cExp > pExp: p 右移, baseExp = cExp
    pMantAligned := Mux(absDiff > 31.U, 0.U(31.W), (pMant31 >> shiftAmt)(30, 0))
    cMantAligned := cMant
    baseExp      := cExp
  }.otherwise {
    // 相等：不移位
    pMantAligned := pMant31
    cMantAligned := cMant
    baseExp      := pExp
  }

  // ========================================================================
  // 5. 加/减法
  // ========================================================================
  val effectSub = pSign ^ cSign

  // 确定大/小数（当 baseExp == pExp 时以对齐后尾数比较大小）
  val pIsLarger = pGtC || (!cGtP && (pMantAligned >= cMantAligned))
  val largeMant = Mux(pIsLarger, pMantAligned, cMantAligned)
  val smallMant = Mux(pIsLarger, cMantAligned, pMantAligned)
  val sOut      = Mux(pIsLarger, pSign, cSign)

  // 32-bit 结果（31-bit + 1 carry bit）
  val addResult = largeMant +& smallMant // 32-bit
  val subResult = largeMant - smallMant  // 31-bit (large >= small, no borrow)

  val result = Mux(effectSub, Cat(0.U(1.W), subResult), addResult) // 32-bit

  // ========================================================================
  // 6. 仅处理上溢（关键简化：不做 LZC，不做可变左移！）
  // ========================================================================
  val accMant = Wire(UInt(31.W))
  val accExp  = Wire(UInt(8.W))

  when(result(31)) {
    // 加法进位溢出：右移 1 位
    accMant := result(31, 1)
    accExp  := baseExp + 1.U
  }.otherwise {
    accMant := result(30, 0)
    accExp  := baseExp
  }

  // ========================================================================
  // 7. 组装 acc 输出
  // ========================================================================
  // 零值处理
  val aZero = (Fp32.exp(io.a) === 0.U) && (Fp32.mant(io.a) === 0.U)
  val bZero = (Fp32.exp(io.b) === 0.U) && (Fp32.mant(io.b) === 0.U)
  val pZero = aZero || bZero
  val cZero = (cExp === 0.U) && (cMant === 0.U)

  val accNormal = Cat(sOut, accExp, accMant)

  io.acc := Mux(pZero, io.c,
    Mux(cZero, Cat(pSign, pExp, pMant31),
      accNormal))

  // ========================================================================
  // 8. 规格化输出逻辑（io.out 路径）：对 io.c 做一次完整规格化
  // ========================================================================
  val lzc = PriorityEncoder(Reverse(cMant)) // 从 MSB 开始找第一个 1

  val normed = (cMant << lzc)(30, 0)        // 左移规格化，MSB 到 bit 30

  // bit 30 是隐含 1（不输出），bit 29 down to bit 7 = 23 bits
  val outMant23 = normed(29, 7)
  val guardBit  = normed(6)
  val stickyBits = normed(5, 0).orR

  // 指数修正：每左移 1 位 exponent 减 1
  val lzc8 = Cat(0.U((8 - lzc.getWidth).W), lzc) // 扩展到 8-bit
  val outExp = Mux(cExp > lzc8, (cExp - lzc8)(7, 0), 0.U(8.W))

  // 内联舍入逻辑（与 Fp32.applyRounding 相同）
  val g = guardBit | stickyBits
  val roundUp = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, cSign & g,
      Mux(io.rnd === RoundingMode.RUP, !cSign & g,
        g))) // RNE / RMM: guard=1 → +1
  val mRounded = Mux(roundUp, outMant23 +& 1.U, outMant23)(22, 0)

  // 最终 fp32 输出
  io.out := Mux(cZero, 0.U(32.W), Cat(cSign, outExp, mRounded))
}
