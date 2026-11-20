import chisel3._
import chisel3.util._

/** FpAddLazy3 — C 方案三槽全 lazy-40 带符号累加器（纯组合）
  *
  * 把 FpAddNarrow 的五槽 {C,L0,L1,L2,L3} 收为三槽 {C,S1,S2}：MX4 运行期 S1=块内整数前端
  *   Mx4BlockInt 的 lazy-40 块和、S2=0；MX8 运行期 S1=lazy40(r0)、S2=lazy40(r1)；BF16 运行期
  *   S1=lazy40(r0)、S2=0。三个输入均已是 lazy-40（fp32 乘积槽的打包在宿主 WbFusionMacFp 完成），
  *   故三槽统一走 unpackC 式解包（mant31>>CSh），无需 FpAddNarrow 的 fp32 unpackProd 与激活掩码：
  *   掩零由宿主喂 40'h0 实现，零槽 eEff=0 且 magW=0 自然不贡献、也不抬高公共指数 E。
  *
  * 契约与 FpAddNarrow 同族（逐条继承其头注）：解包→E=max(非零槽 expEff)→floor 对齐
  *   a_i=magW_i>>clamp(E−eEff_i,W)（shift≥W ⟹ term-drop）→带符号平衡树→k 档溢出收回→
  *   mant31 高 W 位对齐、低 LowSh 位结构性 0→expOut=clamp(E+k,254)→magFull==0 输出真零。
  *   差异仅两处：|S| ≤ 3×(2^W−1) < 2^(W+2) ⟹ MagW=W+2、SInt(W+3)；kGrow∈{0,1,2}（只探
  *   magFull 的 {W, W+1} 两位）。窗口 W、相对误差界 2^−(W−1)、expOut 钳 254、真零判据全部沿用。
  *
  * 与 FpAddNarrow 的逐位等价（C 方案非 MX4 族零 diff 的依据）：MX8（is4x=0,is2x=1，L2/L3 掩零）
  *   与 BF16（is4x=is2x=0，L1/L2/L3 掩零）下，FpAddNarrow 的激活槽恰为 3/2 个，其 |S|<2^(W+2)
  *   ⟹ magFull(W+2)=0 ⟹ 五槽 kGrow 探测退化为与三槽相同的 {W,W+1} ⟹ 对齐、求和、k 档、打包
  *   逐位一致。前提是宿主把 fp32 乘积 p 打包成 lazy40 = {p(31),p(30,23),Cat(1,p(22,0)),7'b0}
  *   （含隐含 1），其 unpackC 的 magW=mant31>>CSh 与 FpAddNarrow.unpackProd 的 mag24>>(24−W) 同值。
  *   MX4 走整数前端块和，与五槽数值相等但非逐位（lazy 切分位置不同，见 pe_array_ce_plan §6.4 P9）。
  *
  * lazy-40: [39]sign [38:31]exp(biased-127,不规格化,允许 leading zeros) [30:0]mant31；
  *   value = mant31 × 2^(exp−157)；真零判据 exp==0 && mant31==0。
  * 参数 W 同 FpAddNarrow（默认 19，硬下界 8、上界 28）；cEffW≥40，低 40 bit 为 lazy-40。
  */

object FpAddLazy3 {
  val DefaultW: Int = 19
  val DefaultRelErrShift: Int = 18
  /** lazy-40 指数域饱和上界 = 254（不是 255，255 是 fp32 Inf/NaN 保留域）。 */
  val ExpSatMax: Int = 254
}

class FpAddLazy3(cEffW: Int = 40, W: Int = FpAddLazy3.DefaultW) extends Module {
  require(cEffW >= 40, s"FpAddLazy3: 延迟规格化需要 cEffW($cEffW) >= 40")
  require(W >= 8, s"FpAddLazy3: W($W) < 8 会截断 mxfp8 乘积的 8 位有效尾数（硬下界）")
  require(W <= 28, s"FpAddLazy3: W($W) > 28 超出设计上限（mant31 高 W 位对齐仅要求 W <= 31，取 28 留余量）")

  private val MagW      = W + 2            // |S| ≤ 3×(2^W−1) < 2^(W+2)
  private val LowSh     = 31 - W           // mant31 = Cat(magTop, 0^LowSh)
  private val ShiftBits = log2Up(W + 1)    // 对齐移位量 0..W 的位宽
  private val CSh       = LowSh            // 输出左移档 == 再入右移档 ⟹ 自产 cReg 零损失
  require((1 << ShiftBits) > W, s"FpAddLazy3: ShiftBits($ShiftBits) 装不下 W($W)")

  val io = IO(new Bundle {
    val c   = Input(UInt(cEffW.W))   // 累加器（lazy-40）
    val s1  = Input(UInt(cEffW.W))   // 槽1（lazy-40）
    val s2  = Input(UInt(cEffW.W))   // 槽2（lazy-40；MX4/BF16 喂 0）
    val sum = Output(UInt(cEffW.W))
  })

  // 全 lazy-40 解包：零槽（exp==0 && mant31==0）eEff=0 且 magW=0 ⟹ 不参与 max、不贡献求和。
  private def unpackLazy(v: UInt): (Bool, UInt, UInt) = {
    val raw  = v(39, 0)
    val s    = raw(39)
    val e    = raw(38, 31)
    val m31  = raw(30, 0)
    val isZero = (e === 0.U) && (m31 === 0.U)
    val magW = m31 >> CSh
    val eEff = Mux(isZero, 0.U(9.W), Cat(0.U(1.W), e))
    val mEff = magW & Fill(W, ~isZero)
    (s, eEff, mEff)
  }

  private val (sC, eC, mC) = unpackLazy(io.c)
  private val (sA, eA, mA) = unpackLazy(io.s1)
  private val (sB, eB, mB) = unpackLazy(io.s2)

  // 公共指数 E = max(非零槽 expEff)（9-bit 无符号，2 级 max）
  private val eMaxCA = Mux(eC >= eA, eC, eA)
  private val E      = Mux(eMaxCA >= eB, eMaxCA, eB)

  // 窄窗口对齐：a_i = magW_i >> clamp(E − eEff_i, W)；shift ≥ W ⟹ term-drop
  private def alignWin(magW: UInt, eEff: UInt): UInt = {
    val sRaw   = E - eEff
    val sClamp = Mux(sRaw > W.U, W.U(ShiftBits.W), sRaw(ShiftBits - 1, 0))
    magW >> sClamp
  }
  private val aC = alignWin(mC, eC)
  private val aA = alignWin(mA, eA)
  private val aB = alignWin(mB, eB)

  // 带符号平衡树：f_i ∈ ±(2^W−1) ⟹ SInt(W+1)；tA SInt(W+2)；S SInt(W+3)，|S|<2^(W+2) 无溢出
  private def signedWin(a: UInt, s: Bool): SInt = Mux(s, -a.zext, a.zext)
  private val fC = signedWin(aC, sC)
  private val fA = signedWin(aA, sA)
  private val fB = signedWin(aB, sB)
  private val tA = fC +& fA
  private val S  = tA +& fB

  // 输出装配 lazy-40：kGrow 只探 magFull 的 {W, W+1} 两位（∈{0,1,2}）；mant31 高 W 位对齐、
  //   低 LowSh 位结构性 0 ⟹ 自产 cReg 再入零损失；expOut=clamp(E+kGrow,254) 绝不 8-bit 绕回；
  //   magFull==0 ⟹ 40'h0 真零（下游 isZero 判据依赖 exp==0 && mant31==0）。
  private val signOut  = S < 0.S
  private val magFull  = Mux(signOut, -S, S).asUInt(MagW - 1, 0)   // UInt(MagW.W)
  private val kGrow    = Mux(magFull(MagW - 1), 2.U(2.W),
                         Mux(magFull(MagW - 2), 1.U(2.W), 0.U(2.W)))
  private val magTop   = (magFull >> kGrow)(W - 1, 0)
  private val mant31   = Cat(magTop, 0.U(LowSh.W))
  private val expRaw   = E +& kGrow
  private val expOut   = Mux(expRaw > FpAddLazy3.ExpSatMax.U,
                             FpAddLazy3.ExpSatMax.U(8.W), expRaw(7, 0))
  private val sumZero  = magFull === 0.U
  private val result40 = Mux(sumZero, 0.U(40.W), Cat(signOut.asUInt, expOut, mant31))

  io.sum := (if (cEffW == 40) result40 else Cat(0.U((cEffW - 40).W), result40))
}