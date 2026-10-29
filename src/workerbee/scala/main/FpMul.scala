/** 纯组合浮点乘法器组件（不含累加），输出 fp32 乘积；输入格式因通路而异：
  *   FpMulBf16Mx   — BF16/MX 共享 8×8 阵列，输入 sign/exp/mant(7)，MX 尾数左对齐
  *   FpMulFp16Bf16 — 满宽 24×24 尾数阵列，输入 fp32（外部完成 fp16/bf16 → fp32）
  *   FpMulMx8      — sigW=4 窄化阵列（mxfp8 E4M3/E5M2），输入最小中间表示 1/8/3
  *   FpMulMx4      — sigW=2 窄化阵列（mxfp4 E2M1），输入最小中间表示 1/8/1
  */

import chisel3._
import chisel3.util._

/** FpMulFp16Bf16 — 仅支持 fp16/bf16 的满宽纯组合乘法器。输入 a/b 为 fp32（尾数 23 位全部
  * 有效）⟹ 必须用满宽 24×24 尾数阵列（含隐含 1）。简化 IEEE-754：正常数 + 零，不处理
  * NaN/Inf/非规格化。
  */
class FpMulFp16Bf16 extends Module {
  val io = IO(new Bundle {
    val a    = Input(UInt(32.W))
    val b    = Input(UInt(32.W))
    val rnd  = Input(RoundingMode())
    val prod = Output(UInt(32.W))
  })

  val sA = io.a(31); val eA = io.a(30, 23); val mA = io.a(22, 0)
  val sB = io.b(31); val eB = io.b(30, 23); val mB = io.b(22, 0)

  val sOut = sA ^ sB
  val mA24  = Cat(1.U(1.W), mA)
  val mB24  = Cat(1.U(1.W), mB)
  val mProd = mA24 * mB24

  // 指数相加 − bias(127)；尾数积 ≥ 2（mProd(47)=1）需右移 1 位、指数 +1
  val eSum = eA +& eB
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

  val mNorm = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))

  // RNE/RMM 简化为 guard=1 → +1（不处理 tie-to-even）；RTZ 截断；RDN/RUP 仅同号 guard=1 → +1
  val g        = guardBit.asBool
  val roundUp  = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOut && g,
      Mux(io.rnd === RoundingMode.RUP, !sOut && g, g)))
  val mRounded = Mux(roundUp, mNorm +& 1.U, mNorm)(22, 0)

  val aZero = (eA === 0.U) && (mA === 0.U)
  val bZero = (eB === 0.U) && (mB === 0.U)
  io.prod := Mux(aZero || bZero, 0.U(32.W), Cat(sOut, eOut, mRounded))
}

/** FpMulMx8 — 仅支持 mxfp8（E4M3/E5M2）的窄化纯组合乘法器。输入为【最小中间表示】
  * sign(1)/exp(8)/mant(3)：exp 已完成 scale 加法与偏置迁移（对应 fp32 指数域），mant 为
  * fp32 mantissa[22:20]。E4M3 尾数 3 位 / E5M2 尾数 2 位，加隐含 1 后有效位 ≤ 4。满宽积
  * mA24*mB24 = (aSig<<20)*(bSig<<20) = (aSig*bSig)<<40 ⟹ sigW=4 窄积零扩展回 48-bit 布局与
  * 满宽逐位相等（非饱和域 bit-exact）；guardBit 落在结构性恒 0 的低位 ⇒ 舍入退化为恒等，
  * io.rnd 仅为接口兼容保留。警示：用于非 MX/宽尾数通路会静默截断尾数低位算错。
  */
class FpMulMx8 extends Module {
  val io = IO(new Bundle {
    val aSign = Input(UInt(1.W))
    val aExp  = Input(UInt(8.W))       // 指数（已完成 scale 加法与偏置迁移）
    val aMant = Input(UInt(3.W))
    val bSign = Input(UInt(1.W))
    val bExp  = Input(UInt(8.W))
    val bMant = Input(UInt(3.W))
    val rnd   = Input(RoundingMode())  // MX 域 guardBit 结构性恒 0，仅接口兼容
    val prod  = Output(UInt(32.W))
  })

  val sigW = 4

  val sOut = (io.aSign ^ io.bSign).asBool
  val aSig = Cat(1.U(1.W), io.aMant)
  val bSig = Cat(1.U(1.W), io.bMant)
  // 窄积零扩展回 48-bit mProd 布局：低 48−2*sigW 位恒 0，等价满宽 mA24*mB24
  val mProd = (aSig * bSig) << (48 - 2 * sigW)

  val eSum = io.aExp +& io.bExp
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

  val mNorm    = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))
  val g        = guardBit.asBool
  val roundUp  = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOut && g,
      Mux(io.rnd === RoundingMode.RUP, !sOut && g, g)))
  val mRounded = Mux(roundUp, mNorm +& 1.U, mNorm)(22, 0)

  val aZero = (io.aExp === 0.U) && (io.aMant === 0.U)
  val bZero = (io.bExp === 0.U) && (io.bMant === 0.U)
  io.prod := Mux(aZero || bZero, 0.U(32.W), Cat(sOut, eOut, mRounded))
}

class FpMulBf16Mx extends Module {
  val io = IO(new Bundle {
    val aSign = Input(UInt(1.W))
    val aExp = Input(UInt(8.W))
    val aMant = Input(UInt(7.W))
    val bSign = Input(UInt(1.W))
    val bExp = Input(UInt(8.W))
    val bMant = Input(UInt(7.W))
    val isBf16 = Input(Bool())
    val prod = Output(UInt(32.W))
  })

  val sigA = Cat(1.U(1.W), io.aMant)
  val sigB = Cat(1.U(1.W), io.bMant)
  val sigProduct = sigA * sigB
  val normalize = sigProduct(15)
  val mantissa = Mux(normalize,
    Cat(sigProduct(14, 0), 0.U(8.W)), Cat(sigProduct(13, 0), 0.U(9.W)))
  val expAdjusted = (io.aExp +& io.bExp) + normalize.asUInt
  val expProduct = Mux(expAdjusted >= 127.U, (expAdjusted - 127.U)(7, 0), 0.U(8.W))
  val signProduct = (io.aSign ^ io.bSign).asBool
  val mxZero = (io.aExp === 0.U && io.aMant === 0.U) ||
    (io.bExp === 0.U && io.bMant === 0.U)
  val mxProduct = Mux(mxZero, 0.U(32.W), Cat(signProduct, expProduct, mantissa))

  // BF16 uses FTZ and finite saturation; MX retains the existing exponent mapping.
  val bf16Zero = io.aExp === 0.U || io.bExp === 0.U || expAdjusted <= 127.U
  val bf16Overflow = expAdjusted > 381.U
  val bf16Product = Mux(bf16Zero, 0.U(32.W),
    Mux(bf16Overflow, Cat(signProduct, 254.U(8.W), "h7fffff".U(23.W)),
      Cat(signProduct, expProduct, mantissa)))
  io.prod := Mux(io.isBf16, bf16Product, mxProduct)
}

/** FpMulMx4 — 仅支持 mxfp4（E2M1）的窄化纯组合乘法器。输入为【最小中间表示】
  * sign(1)/exp(8)/mant(1)：exp 已完成 scale 加法与偏置迁移，mant 为 fp32 mantissa[22]。
  * mantW=1 加隐含 1 后有效位 ≤ 2 ⟹ sigW=2，代数恒等 (8+4m)(8+4m')<<40 == (2+m)(2+m')<<44
  * 对 m∈{0,1} 逐位相等（bit-exact）；guardBit 结构性恒 0 ⇒ 舍入退化为恒等，io.rnd 仅为接口
  * 兼容保留。警示：用于 E2M1 以外的任何格式（含 fp8）会静默截断尾数算错。
  */
class FpMulMx4 extends Module {
  val io = IO(new Bundle {
    val aSign = Input(UInt(1.W))
    val aExp  = Input(UInt(8.W))       // 指数（已完成 scale 加法与偏置迁移）
    val aMant = Input(UInt(1.W))
    val bSign = Input(UInt(1.W))
    val bExp  = Input(UInt(8.W))
    val bMant = Input(UInt(1.W))
    val rnd   = Input(RoundingMode())  // MX 域 guardBit 结构性恒 0，仅接口兼容
    val prod  = Output(UInt(32.W))
  })

  val sigW = 2

  val sOut = (io.aSign ^ io.bSign).asBool
  val aSig = Cat(1.U(1.W), io.aMant)
  val bSig = Cat(1.U(1.W), io.bMant)
  // 窄积零扩展回 48-bit mProd 布局：低 48−2*sigW 位恒 0，等价满宽 mA24*mB24
  val mProd = (aSig * bSig) << (48 - 2 * sigW)

  val eSum = io.aExp +& io.bExp
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

  val mNorm    = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))
  val g        = guardBit.asBool
  val roundUp  = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOut && g,
      Mux(io.rnd === RoundingMode.RUP, !sOut && g, g)))
  val mRounded = Mux(roundUp, mNorm +& 1.U, mNorm)(22, 0)

  val aZero = (io.aExp === 0.U) && (io.aMant === 0.U)
  val bZero = (io.bExp === 0.U) && (io.bMant === 0.U)
  io.prod := Mux(aZero || bZero, 0.U(32.W), Cat(sOut, eOut, mRounded))
}
