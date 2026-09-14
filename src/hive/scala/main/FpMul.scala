/** 纯组合浮点乘法器组件（融合浮点乘法器的三个独立构建块）
  *
  * 三个模块均为【纯乘法器】（不含累加），输出 fp32 乘积。输入格式因通路而异：
  *   · FpMulFp16Bf16 — 满宽 24×24 尾数阵列（fp16/bf16 通路）：输入为 fp32 格式
  *                     （外部完成 fp16/bf16 → fp32 转换），逐字复制 Fp32.mul
  *   · FpMulMx8      — sigW=4 窄化阵列（mxfp8 E4M3/E5M2 通路）：输入为【最小中间
  *                     表示】sign(1)/exp(8)/mant(3)，转换（含 scale 加法与偏置迁移）
  *                     由外部完成
  *   · FpMulMx4      — sigW=2 窄化阵列（mxfp4 E2M1 通路）：输入为【最小中间表示】
  *                     sign(1)/exp(8)/mant(1)，转换由外部完成
  *
  * 乘法逻辑一律内联实现（不调用 Fp32.mul / 不例化 HiveMacFpMx），
  * 便于外部自由修改与融合。风格与 HiveMacFpMx 一致：extends Module，纯组合逻辑。
  */

import chisel3._
import chisel3.util._

/** FpMulFp16Bf16 — 仅支持 fp16/bf16 的满宽纯组合乘法器
  *
  * 输入 a/b 为 fp32 格式（外部由 fp16/bf16 转换而来，尾数 23 位全部有效），
  * 故必须使用满宽 24×24 尾数乘法阵列（含隐含 1）。
  * 逻辑逐字复制自 Fp32.mul（简化 IEEE-754：正常数 + 零，不处理 NaN/Inf/非规格化）。
  */
class FpMulFp16Bf16 extends Module {
  val io = IO(new Bundle {
    val a    = Input(UInt(32.W))       // fp32 multiplicand（外部由 fp16/bf16 转换而来）
    val b    = Input(UInt(32.W))       // fp32 multiplier
    val rnd  = Input(RoundingMode())   // 舍入模式
    val prod = Output(UInt(32.W))      // fp32 乘积
  })

  // 解包 sign / exponent / mantissa
  val sA = io.a(31); val eA = io.a(30, 23); val mA = io.a(22, 0)
  val sB = io.b(31); val eB = io.b(30, 23); val mB = io.b(22, 0)

  val sOut = sA ^ sB
  // 隐含 1：mantissa 加 1.0 → 24-bit，满宽 24×24 阵列 → 48-bit 积
  val mA24  = Cat(1.U(1.W), mA)
  val mB24  = Cat(1.U(1.W), mB)
  val mProd = mA24 * mB24 // 48-bit

  // 指数相加 - bias(127)；尾数积 >= 2（mProd(47)=1）需右移 1 位，指数相应 +1
  val eSum = eA +& eB // 9-bit to avoid overflow
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

  // 规格化：mProd[47] 为 1 时右移 1
  val mNorm = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  // 舍入位（被截断的最高位）
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))

  // 舍入逻辑（同 Fp32.applyRounding，作用于 23-bit mantissa）
  // RNE/RMM: guard=1 → +1（简化，未处理 tie-to-even）；RTZ: 截断；
  // RDN: 负数且 guard=1 → +1；RUP: 正数且 guard=1 → +1
  val g        = guardBit.asBool
  val roundUp  = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOut && g,
      Mux(io.rnd === RoundingMode.RUP, !sOut && g, g)))
  val mRounded = Mux(roundUp, mNorm +& 1.U, mNorm)(22, 0)

  // 任一操作数为零 → 结果为零
  val aZero = (eA === 0.U) && (mA === 0.U)
  val bZero = (eB === 0.U) && (mB === 0.U)
  io.prod := Mux(aZero || bZero, 0.U(32.W), Cat(sOut, eOut, mRounded))
}

/** FpMulMx8 — 仅支持 mxfp8（E4M3/E5M2）的窄化纯组合乘法器
  *
  * 输入为【最小中间表示】sign(1)/exp(8)/mant(3)，由外部完成 e4m3/e5m2 元素 +
  * E8M0 scale → fp32 的转换后拆出：exp 已完成 scale 加法与偏置迁移（对应 fp32
  * 指数域），mant 为 fp32 尾数高 3 位（对应 fp32 mantissa[22:20]）。E4M3 尾数
  * 3 位 / E5M2 尾数 2 位，加隐含 1 后有效位 ≤ 4（=1+mantW）。
  * 满宽积 mA24*mB24 = (aSig<<20)*(bSig<<20) = (aSig*bSig)<<40：
  * 用 sigW=4 窄积零扩展回 48-bit mProd 布局，与满宽逐位相等（非饱和域 bit-exact，
  * 依据见 HiveMacFpMx 头注）。guardBit 落在恒 0 低位 ⇒ 结构性恒 0 ⇒ 舍入退化为恒等，
  * io.rnd 仅为接口兼容性保留。
  * 乘法逻辑提取自 HiveMacFpMx（sigW=4）的上游乘积阶段，纯组合。
  *
  * 警示：禁止把本模块当作 FpMulFp16Bf16 的替身用于非 MX/宽尾数通路，
  * 否则会静默截断尾数低位算错。
  */
class FpMulMx8 extends Module {
  val io = IO(new Bundle {
    val aSign = Input(UInt(1.W))       // 符号位
    val aExp  = Input(UInt(8.W))       // 指数（已完成 scale 加法与偏置迁移）
    val aMant = Input(UInt(3.W))       // 尾数高 3 位（对应 fp32 mantissa[22:20]）
    val bSign = Input(UInt(1.W))
    val bExp  = Input(UInt(8.W))
    val bMant = Input(UInt(3.W))
    val rnd   = Input(RoundingMode())  // 舍入模式（MX 域 guardBit 结构性恒 0，保留接口兼容性）
    val prod  = Output(UInt(32.W))     // fp32 乘积
  })

  // 窄化乘法器有效位宽：1 位隐含 + E4M3 的 3 位尾数（E5M2 的 2 位亦被覆盖）
  val sigW = 4

  val sOut = (io.aSign ^ io.bSign).asBool
  // aSig/bSig = 隐含 1 + 中间表示尾数（E4M3 3 位 / E5M2 2 位，已覆盖）
  val aSig = Cat(1.U(1.W), io.aMant) // sigW=4 → 4-bit
  val bSig = Cat(1.U(1.W), io.bMant) // 4-bit
  // sigW×sigW 窄积零扩展回 48-bit mProd 布局（低 48-2*sigW 位恒 0，等价满宽 mA24*mB24）
  val mProd = (aSig * bSig) << (48 - 2 * sigW) // <<40, 48-bit

  // 指数相加 - bias；尾数积 >=2（mProd(47)=1）时 +1（与 Fp32.mul 逐字同源）
  val eSum = io.aExp +& io.bExp
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

  // 规格化 + 舍入位：guardBit 落在恒 0 低位 ⇒ 恒 0 ⇒ 舍入退化为恒等（逻辑照搬保留）
  val mNorm    = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))
  val g        = guardBit.asBool
  val roundUp  = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOut && g,
      Mux(io.rnd === RoundingMode.RUP, !sOut && g, g)))
  val mRounded = Mux(roundUp, mNorm +& 1.U, mNorm)(22, 0)

  // 任一操作数为零 → 结果为零
  val aZero = (io.aExp === 0.U) && (io.aMant === 0.U)
  val bZero = (io.bExp === 0.U) && (io.bMant === 0.U)
  io.prod := Mux(aZero || bZero, 0.U(32.W), Cat(sOut, eOut, mRounded))
}

/** FpMulMx4 — 仅支持 mxfp4（E2M1）的窄化纯组合乘法器
  *
  * 输入为【最小中间表示】sign(1)/exp(8)/mant(1)，由外部完成 e2m1 元素 + E8M0
  * scale → fp32 的转换后拆出：exp 已完成 scale 加法与偏置迁移（对应 fp32 指数域），
  * mant 为 fp32 尾数高 1 位（对应 fp32 mantissa[22]）。E2M1 mantW=1，仅 bit22 有效，
  * 加隐含 1 后有效位 ≤ 2。sigW=4 阵列 3/4 恒零浪费，
  * 故窄化到 sigW=2：代数恒等 (8+4m)(8+4m')<<40 == (2+m)(2+m')<<44 对 m∈{0,1}
  * 逐位相等（bit-exact 依据见 HiveMacFpMx sigW=2 变体头注）。
  * guardBit 结构性恒 0 ⇒ 舍入退化为恒等，io.rnd 仅为接口兼容性保留。
  * 乘法逻辑提取自 HiveMacFpMx（sigW=2）的上游乘积阶段，纯组合。
  *
  * 警示：禁止把本模块用于 E2M1 以外的任何格式（含 fp8），否则尾数被静默截断算错。
  */
class FpMulMx4 extends Module {
  val io = IO(new Bundle {
    val aSign = Input(UInt(1.W))       // 符号位
    val aExp  = Input(UInt(8.W))       // 指数（已完成 scale 加法与偏置迁移）
    val aMant = Input(UInt(1.W))       // 尾数高 1 位（对应 fp32 mantissa[22]）
    val bSign = Input(UInt(1.W))
    val bExp  = Input(UInt(8.W))
    val bMant = Input(UInt(1.W))
    val rnd   = Input(RoundingMode())  // 舍入模式（MX 域 guardBit 结构性恒 0，保留接口兼容性）
    val prod  = Output(UInt(32.W))     // fp32 乘积
  })

  // 窄化乘法器有效位宽：1 位隐含 + E2M1 的 1 位尾数
  val sigW = 2

  val sOut = (io.aSign ^ io.bSign).asBool
  // aSig/bSig = 隐含 1 + 中间表示尾数（E2M1 仅 1 位）
  val aSig = Cat(1.U(1.W), io.aMant) // sigW=2 → 2-bit
  val bSig = Cat(1.U(1.W), io.bMant) // 2-bit
  // sigW×sigW 窄积零扩展回 48-bit mProd 布局（低 48-2*sigW 位恒 0，等价满宽 mA24*mB24）
  val mProd = (aSig * bSig) << (48 - 2 * sigW) // <<44, 48-bit

  // 指数相加 - bias；尾数积 >=2（mProd(47)=1）时 +1（与 Fp32.mul 逐字同源）
  val eSum = io.aExp +& io.bExp
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))

  // 规格化 + 舍入位：guardBit 落在恒 0 低位 ⇒ 恒 0 ⇒ 舍入退化为恒等（逻辑照搬保留）
  val mNorm    = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))
  val g        = guardBit.asBool
  val roundUp  = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOut && g,
      Mux(io.rnd === RoundingMode.RUP, !sOut && g, g)))
  val mRounded = Mux(roundUp, mNorm +& 1.U, mNorm)(22, 0)

  // 任一操作数为零 → 结果为零
  val aZero = (io.aExp === 0.U) && (io.aMant === 0.U)
  val bZero = (io.bExp === 0.U) && (io.bMant === 0.U)
  io.prod := Mux(aZero || bZero, 0.U(32.W), Cat(sOut, eOut, mRounded))
}
