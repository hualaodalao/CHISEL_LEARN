/** FpAddNarrow — MX + BF16 统一窄窗口五槽带符号累加器（纯组合）
  *
  * I/O 与 FpAdd 逐端口一致（port-compatible，非语义等价），由编译期参数 useNarrowAdd 选择。
  * 白名单护栏 supportedFmts ⊆ {MXE4M3, MXE5M2, MXE2M1, BF16}，含 BF16 时另要求 narrowW ≥ 16。
  * 决定窗内是否有损的是乘积的【有效位数】而非格式名：MX 乘积 mag24 有效位 ≤ 1+3+3 = 7 ⟹ 零丢失；
  * FpMulBf16Mx 的 BF16 乘积 mant23 低 8 位结构性 0 ⟹ 有效位 16 位 ⟹ W ≥ 16 零丢失；经
  * FpMulFp16Bf16 fp32 路的乘积 mant23 全 23 位有效 ⟹ 丢低 (24−W) 位，该路无精度承诺。
  *
  * lazy-40 格式（与 FpAdd / Fp32.fpNormalize40to32 一致）：[39] sign；[38:31] exponent（8-bit
  *   biased-127，不规格化）；[30:0] mantissa（31-bit，允许 leading zeros）。
  *   value = (−1)^sign × mant31 × 2^(exp − 157) ⟹ radix：mant31 bit30 ↔ 2^(exp−127)。
  *   真零判据 exp==0 && mant31==0。fp32 乘积槽：value = mag24 × 2^(exp − 150)，mag24 = Cat(1, mant23)。
  *
  * 五槽 = { C=cReg, L0=prodFp16, L1=prodFp8, L2=prodFp4_0, L3=prodFp4_1 }：
  *   1) 解包 → (sign, expEff(9-bit), magW(W-bit))；零槽/未激活槽 expEff=0 且 magW=0。乘积槽
  *      magW = mag24 >> (24−W)，cReg 槽 magW = mant31 >> (31−W)（同 radix，天然吸收 leading
  *      zeros，无需 LZC）⟹ 窗口 bit(W−1) ↔ 2^(exp−127)。
  *   2) 公共指数 E = max(激活且非零槽的 expEff)。
  *   3) 对齐 a_i = magW_i >> clamp(E − expEff_i, W)；shift ≥ W ⟹ term-drop。窗外低位直接丢弃
  *      （不做 sticky/round）= 唯一精度让步点。
  *   4) S = Σ (sign_i ? −a_i : +a_i)，显式平衡树；|S| ≤ 5×(2^W−1) < 2^(W+3) ⟹ SInt(W+4)。
  *   5) sign = (S<0)；magFull = |S|；k = max(0, bitLength(magFull) − W) ∈ [0,3]（只探 magFull 的
  *      {W, W+1, W+2} 三位）；mant31 = Cat(magFull >> k, 0^(31−W))；expOut = clamp(E + k, 254)；
  *      magFull == 0 ⟹ 40'h0 真零（否则 exp≠0 的「指数非零零」会漏判下游 isZero）。
  *
  * (D1) k 必须是【实际】增长量：lazy-40 不做 LZC ⟹ expField 必须反映最高有效位真实位置。若无条件
  *   预留 3 位使 expOut 恒为 E+3，MAC 环路回灌下 E 每拍 +3 ⟹ 约 W/3 拍后乘积 lane 全被移出窗口
  *   （累加器失忆）。k=0（MX 稳态最常见）⟹ expOut = E，环路 E 不膨胀，cReg 再入零损失；代价是
  *   k ≥ 1 时丢 magFull 低 k 位（≤ 2^k−1 窗口 LSB）。
  * (D2) mant31 取高 W 位对齐 ⟹ expOut = E+k，仅 E ≥ 252 需钳位。若改低位零扩展则 expOut =
  *   E+k−(31−W)，W=19 时 E ≤ 138 即 expOut < 0 → 8-bit 域绕回成大指数（in-spec MX 的 E 常在
  *   120~200，绕回高发）。
  *
  * 精度契约。窗口 LSB = 2^(E−126−W)；两处同向截断：(a) 对齐为 floor ⟹ 每槽误差 < 1 窗口 LSB，
  * n_lossy = 实际丢失 ≥1 bit 的激活槽数；(b) 溢出收回误差 ≤ 2^k−1 窗口 LSB。
  *   ① 绝对界（无条件，含相消域）：|diff| ≤ (n_lossy + 2^k − 1) × 2^(E−126−W)。唯一无条件承诺。
  *   ② 相对界（仅良态域）：relErr ≤ 2^−(W−1)，充分条件 |exact| ≥ (n_lossy + 2^k − 1) × 2^(W−1)
  *      × 窗口LSB。W=19 ⟹ 2^−18；W=16 ⟹ 2^−15；W=22 ⟹ 2^−21。
  *   ③ 相消域：|exact| 小到若干窗口 LSB 时 relErr 可任意大；加大 W 只下移门槛，只能由 ① 兜底。
  *
  * n_lossy = 0 的充分条件（MX 稳态，逐槽）：
  *   · mxfp8：mProd=(aSig·bSig)<<40、aSig/bSig ≤ 4-bit ⟹ mant23 有效位仅 [22:16]、[15:0] 结构性 0
  *     ⟹ shift ≤ W−8 无损；MX8 双 lane 共享 scaleA/scaleB ⟹ 槽间指数差 ≤ ~16。
  *   · mxfp4：P=(2+m1ₐ)(2+m1_b) ∈ {4,6,9} ⟹ mant23 ∈ {0x000000,0x400000,0x100000}，有效位仅
  *     {23,22,20} ⟹ shift ≤ W−4 无损；同 block 四 lane 指数差 ≤ 7 ≪ W ⟹ 恒无损。这也是 4 个 fp4
  *     积可直接进树、不需中间 LZC 的依据（块内和 ≤ 13 bit ≤ W+3，整数算术精确）。
  *   · cReg：(a) 自产回灌 mant31 低 (31−W) 位结构性 0 ⟹ 再入完全无损；(b) fp32BitsTo40 装入的
  *     MX-可表示值丢 mant23[4:0]，而 MX8 乘积 mant23[15:0]=0、MX4 乘积 mant23[19:0]=0 ⟹ 丢的 5 位
  *     恒 0 ⟹ 无损；(c) 满精度 fp32 初值 ⟹ n_lossy = 1，逐拍不放大（K 拍累积 ≲ K × 最大项 ×
  *     2^−(W−1)）。
  *   完全相消（S==0）⟹ 输出 40'h0（+0），与 FpAdd 的 ±0 → +0 归一化后语义一致。
  *
  * 三模式与激活掩码（优先级 is4x > is2x）：MX4 激活 {C,L0,L1,L2,L3}；MX8 激活 {C,L0,L1}，L2/L3
  *   掩零；BF16 激活 {C,L0}（语义 = cReg + prodFp16，W ≥ 16 时该槽对齐前零丢失，唯一例外是乘积
  *   饱和码 Cat(sign,254,0x7FFFFF) 的 mant23 低 8 位非 0 ⟹ W=19 丢其低 5 位）。
  *   掩零必须同时清 expEff，否则无关槽的指数会抬高 E ⟹ 真实槽整体下移 ⟹ 精度崩塌。
  *
  * out-of-spec 域（不做数值承诺；优雅降级而非 X 传播）：
  *   (E1) fp32 subnormal 乘积：与 FpAdd 一致不特判 ⟹ expEff=0 参与 max、对齐 shift = E。E ≥ W ⟹
  *        整槽 term-drop；E < W ⟹ 部分透过窗口，可产出 expOut=0 && mant31≠0 的非规格 lazy-40 码。
  *   (E2) cReg 的 exp==0 && mant31!=0 违反 lazy-40 不变量，按 expEff=0 处理，降级同 (E1)。
  *   (E3) expOut 用 10-bit 算完后饱和钳位到 254：不是 8-bit 绕回，也不是 255。fp32 的 eField==255
  *        是 Inf/NaN 保留域，而本模块与 FpAdd 一样只判零、不判 Inf ⟹ 钳 255 会让下游
  *        fpNormalize40to32 归一化出 NaN；钳 254 ⟹ 恒为有限值。触发域 E ≥ 252 ⟺ 真值 ≥ 2^125。
  *   (E4) lane0 饱和域：元素饱和后 mant23 可填满 23 bit ⟹ 丢低 (24−W) 位；该域 a 侧已饱和失真
  *        （相对误差 ~33%），额外损失被淹没。
  *
  * 参数：W（对齐窗口宽度，编译期 Int，默认 19）是唯一精度/面积旋钮，可扫 {16,19,22}。硬下界 8：
  *   mxfp8 乘积有效位 8 bit（含隐含 1）。硬上界 28：mant31 = Cat(magTop(W 位), 0^(31−W)) 仅要求
  *   W ≤ 31，取 28 留 3 位余量。cEffW：与 FpAdd 同义（≥40，低 40 bit 为 lazy-40，高位零填充）。
  */

import chisel3._
import chisel3.util._

object FpAddNarrow {
  /** 默认窄对齐窗口宽度（位）；配套相对误差阈值 = 2^−(W−1) = 2^−18。 */
  val DefaultW: Int = 19
  val DefaultRelErrShift: Int = 18
  /** lazy-40 指数域饱和上界 = 254（不是 255，见头注 (E3)）；模块与规格模型共用此常量。 */
  val ExpSatMax: Int = 254
}

class FpAddNarrow(cEffW: Int = 40, W: Int = FpAddNarrow.DefaultW) extends Module {
  require(cEffW >= 40, s"FpAddNarrow: 延迟规格化需要 cEffW($cEffW) >= 40")
  require(W >= 8, s"FpAddNarrow: W($W) < 8 会截断 mxfp8 乘积的 8 位有效尾数（硬下界）")
  require(W <= 28, s"FpAddNarrow: W($W) > 28 超出设计上限（mant31 高 W 位对齐仅要求 W <= 31，取 28 留余量）")

  // ---- 编译期派生常量（全部为 Scala 层，不生成运行期选择子）----
  private val MagW      = W + 3            // |S| 有效位宽：|S| ≤ 5×(2^W−1) < 2^(W+3)
  private val LowSh     = 31 - W           // mant31 = Cat(magTop, 0^LowSh)
  private val ShiftBits = log2Up(W + 1)    // 对齐移位量 0..W 的位宽
  private val CSh       = LowSh            // ★ 同一常量：输出左移档 == cReg 再入右移档 ⟹ 自产 cReg 零损失
  require((1 << ShiftBits) > W, s"FpAddNarrow: ShiftBits($ShiftBits) 装不下 W($W)")

  // I/O：与 FpAdd 逐端口一致（port-compatible；语义差异见头注）
  val io = IO(new Bundle {
    val is4x = Input(Bool())
    val is2x = Input(Bool())
    val cReg      = Input(UInt(cEffW.W))   // 累加器（lazy-40: sign(1)|exp(8)|mant(31)）
    val prodFp16  = Input(UInt(32.W))      // lane0 乘积槽（fp32）
    val prodFp8   = Input(UInt(32.W))      // lane1（MX4 模式下为 nibble1 积）
    val prodFp4_0 = Input(UInt(32.W))      // lane2
    val prodFp4_1 = Input(UInt(32.W))      // lane3
    val sum       = Output(UInt(cEffW.W))  // 累加结果（lazy-40）
  })

  // 槽解包 → (sign, expEff(9-bit), magW(W-bit))；expEff = 0 且 magW = 0 ⟺ 该槽不参与。
  // 掩零必须同时清指数，否则陈旧指数会抬高公共指数 E ⟹ 真实槽整体下移 ⟹ 精度崩塌。
  private def unpackProd(p: UInt, active: Bool): (Bool, UInt, UInt) = {
    val s = p(31)
    val e = p(30, 23)
    val m = p(22, 0)
    val isZero = (e === 0.U) && (m === 0.U)   // 与 FpAdd/FpMul 同判据：不特判 subnormal（见 (E1)）
    val valid  = active && !isZero
    val mag24  = Cat(1.U(1.W), m)             // 隐含 1 在 bit23
    // 常量移位（纯连线）：窗口 bit(W−1) ↔ 2^(exp−127)
    val magW   = if (W >= 24) mag24 << (W - 24) else mag24 >> (24 - W)
    val eEff   = Mux(valid, Cat(0.U(1.W), e), 0.U(9.W))
    val mEff   = magW & Fill(W, valid)
    (s, eEff, mEff)
  }

  private def unpackC(cIn: UInt): (Bool, UInt, UInt) = {
    val cRaw  = cIn(39, 0)
    val s     = cRaw(39)
    val e     = cRaw(38, 31)
    val m31   = cRaw(30, 0)                   // 可能带 leading zeros（lazy 语义，无需 LZC）
    val isZero = (e === 0.U) && (m31 === 0.U)
    val magW   = m31 >> CSh                   // 同 radix（bit30 ↔ 2^(exp−127)）对齐到 W 窗口
    val eEff   = Mux(isZero, 0.U(9.W), Cat(0.U(1.W), e))
    val mEff   = magW & Fill(W, ~isZero)
    (s, eEff, mEff)
  }

  // ---- 激活掩码（三模式；优先级 is4x > is2x，见头注）----
  private val actL1 = io.is2x || io.is4x
  private val actL2 = io.is4x
  private val actL3 = io.is4x

  private val (sC, eC, mC) = unpackC(io.cReg)
  private val (s0, e0, m0) = unpackProd(io.prodFp16,  true.B)   // L0 恒激活
  private val (s1, e1, m1) = unpackProd(io.prodFp8,   actL1)
  private val (s2, e2, m2) = unpackProd(io.prodFp4_0, actL2)
  private val (s3, e3, m3) = unpackProd(io.prodFp4_1, actL3)

  // 公共指数 E = max(激活且非零槽的 expEff)（2 级并行 max 树，9-bit 无符号）
  private val eMaxC0 = Mux(eC >= e0, eC, e0)
  private val eMax12 = Mux(e1 >= e2, e1, e2)
  private val eMaxA  = Mux(eMaxC0 >= eMax12, eMaxC0, eMax12)
  private val E      = Mux(eMaxA >= e3, eMaxA, e3)              // UInt(9.W)

  // 窄窗口对齐：a_i = magW_i >> clamp(E − expEff_i, W)；shift ≥ W ⟹ term-drop
  // （Chisel 动态 >> 保持位宽，移位量 ≥ 位宽时 Verilog 语义给出 0，无需额外归零 mux）
  private def alignWin(magW: UInt, eEff: UInt): UInt = {
    val sRaw   = E - eEff                                        // UInt(9.W)，E 为 max ⟹ 非负
    val sClamp = Mux(sRaw > W.U, W.U(ShiftBits.W), sRaw(ShiftBits - 1, 0))
    magW >> sClamp                                               // UInt(W.W)
  }

  private val aC = alignWin(mC, eC)
  private val a0 = alignWin(m0, e0)
  private val a1 = alignWin(m1, e1)
  private val a2 = alignWin(m2, e2)
  private val a3 = alignWin(m3, e3)

  // 带符号求和树（显式平衡）：F_i ∈ [−(2^W−1), 2^W−1] ⟹ SInt(W+1)；+& 逐层扩位
  // W+2 → W+3 → W+4；|S| ≤ 5×(2^W−1) < 2^(W+3) ⟹ SInt(W+4) 无溢出。
  private def signedWin(a: UInt, s: Bool): SInt = Mux(s, -a.zext, a.zext)   // SInt(W+1)

  private val fC = signedWin(aC, sC)
  private val f0 = signedWin(a0, s0)
  private val f1 = signedWin(a1, s1)
  private val f2 = signedWin(a2, s2)
  private val f3 = signedWin(a3, s3)

  private val tA = fC +& f0        // SInt(W+2)
  private val tB = f1 +& f2        // SInt(W+2)
  private val tC = tA +& tB        // SInt(W+3)
  private val S  = tC +& f3        // SInt(W+4)

  // 输出装配 lazy-40 = {sign, expOut, mant31}（见头注 (D1)/(D2)/(E3)）：kGrow 只探测 magFull 的
  // {W, W+1, W+2} 三个固定位（相消产生的低位前导零仍不规格化 ⟹ lazy 语义保持）；mant31 高 W 位
  // 对齐、低 LowSh 位结构性 0 ⟹ 自产 cReg 再入零损失；expOut = clamp(E + kGrow, 254) 绝不 8-bit
  // 绕回；magFull == 0 ⟹ 40'h0 真零（下游 isZero 判据依赖 exp==0 && mant31==0）。
  private val signOut  = S < 0.S
  private val magFull  = Mux(signOut, -S, S).asUInt(MagW - 1, 0)   // UInt(MagW.W)
  private val kGrow    = Mux(magFull(MagW - 1), 3.U(2.W),
                     Mux(magFull(MagW - 2), 2.U(2.W),
                     Mux(magFull(MagW - 3), 1.U(2.W), 0.U(2.W))))
  private val magTop   = (magFull >> kGrow)(W - 1, 0)              // UInt(W.W)
  private val mant31   = Cat(magTop, 0.U(LowSh.W))                 // UInt(31.W)
  private val expRaw   = E +& kGrow                                // UInt(10.W)：≤ 255+3 = 258
  private val expOut   = Mux(expRaw > FpAddNarrow.ExpSatMax.U,
                             FpAddNarrow.ExpSatMax.U(8.W), expRaw(7, 0))
  private val sumZero  = magFull === 0.U
  private val result40 = Mux(sumZero, 0.U(40.W), Cat(signOut.asUInt, expOut, mant31))

  io.sum := (if (cEffW == 40) result40 else Cat(0.U((cEffW - 40).W), result40))
}
