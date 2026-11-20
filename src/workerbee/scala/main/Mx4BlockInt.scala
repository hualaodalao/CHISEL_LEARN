import chisel3._
import chisel3.util._

/** Mx4BlockInt — MXFP4(E2M1) 块内整数前端（纯组合，输出 lazy-40 块和）
  *
  * 直接从原始 E2M1 nibble 算四积块和，不经 fp32 乘积域。元素归一化整数
  *   n = value / 2^(scale−128) ∈ {0,1,2,3,4,6,8,12}；P<<sh 形式单积 I = 4·n_a·n_b ≤ 576，
  *   四积块和 |bsum| ≤ 2304 ⟹ 13-bit 带符号精确（无 fp32 对齐截断）。
  * 打包 lazy-40：mant31 = mag12<<19、exp = eSum−120（eSum = scaleA+scaleB），
  *   value = mag12 × 2^(eSum−258) = (Σ ±n_a·n_b) × 2^(eSum−256) = 张量真值。
  *
  * 三个边界域显式处理（与 FpCvtMx4ToMid 元素语义同源）：
  *   · subnormal 等效尾数：maE = ma & (e2!=0)（e2=0 时隐含 1 不计，n=ma）；
  *   · underflow 冲刷：e2+scale < 2 ⟹ 该元素保号零；
  *   · tiny 守卫：eSum < 121 ⟹ exp<1，整块冲刷真零。
  *
  * 溢出域（eRaw = eSum−120 > 254，真值超 lazy-40 可表示范围）饱和到有限 fp32max：
  *   打包 {sgn, 254, 31'h7FFFFF80}，经 Fp32.fpNormalize40to32(RNE) 恰归一化为 {s,254,0x7FFFFF}。
  *   尾数必须用 0x7FFFFF80（bit6=0 ⟹ roundUp=0）；全 1 的 0x7FFFFFFF 会 RNE 进位成 exp255=Inf，
  *   而 Inf 注入累加器会被 unpackC 当 eEff=255/mag=0 抬高公共指数 ⟹ 其余槽全 term-drop 塌成真零。
  *   饱和码是有限 lazy 值，非 sticky（回灌后异号相加可 desaturate）；该域签核量化流不可达。
  *
  * lazy-40: [39]sign [38:31]exp(biased-127,不规格化,允许 leading zeros) [30:0]mant31；
  *   value = mant31 × 2^(exp−157)；真零判据 exp==0 && mant31==0。
  * nibble lane 序与 WbFusionMacFp / WbDebugModel 同源：lane i = a(4i+3,4i)，k=4j+i。
  */
class Mx4BlockInt extends Module {
  val io = IO(new Bundle {
    val a      = Input(UInt(16.W))   // 4 nibble: a(3,0)=lane0 … a(15,12)=lane3
    val b      = Input(UInt(16.W))
    val scaleA = Input(UInt(8.W))
    val scaleB = Input(UInt(8.W))
    val blk    = Output(UInt(40.W))  // lazy-40 块和
  })

  // 每 lane：P=(2+maE)(2+mbE)∈{4,6,9}，I=P<<(e2a+e2b)=4·n_a·n_b；零/underflow 冲刷 + 符号
  private val laneI = Wire(Vec(4, UInt(10.W)))
  private val laneS = Wire(Vec(4, Bool()))
  for (i <- 0 until 4) {
    val aa = io.a(4 * i + 3, 4 * i)
    val bb = io.b(4 * i + 3, 4 * i)
    val e2a = aa(2, 1); val e2b = bb(2, 1)
    val ma = aa(0); val mb = bb(0)
    val maE = ma & (e2a =/= 0.U)
    val mbE = mb & (e2b =/= 0.U)
    val P = 4.U(4.W) + Cat(0.U(2.W), maE, 0.U(1.W)) + Cat(0.U(2.W), mbE, 0.U(1.W)) +
              Cat(0.U(3.W), maE & mbE)
    val sh = e2a +& e2b                                   // UInt(3.W)，≤6
    val mag = (P.pad(10) << sh)(9, 0)                     // ≤576，10-bit
    val za = (e2a === 0.U) && (ma === 0.U)
    val zb = (e2b === 0.U) && (mb === 0.U)
    val ua = (e2a +& io.scaleA) < 2.U
    val ub = (e2b +& io.scaleB) < 2.U
    laneI(i) := Mux(za || zb || ua || ub, 0.U(10.W), mag)
    laneS(i) := aa(3) ^ bb(3)
  }

  // 带符号平衡树：f_i ∈ ±576 ⟹ SInt(11)；t0/t1 SInt(12)；bsum SInt(13)，|bsum|≤2304 无溢出
  private def signedI(i: Int): SInt = Mux(laneS(i), -laneI(i).zext, laneI(i).zext)
  private val t0 = signedI(0) +& signedI(1)
  private val t1 = signedI(2) +& signedI(3)
  private val bsum = t0 +& t1

  private val sgn = bsum < 0.S
  private val magFull = Mux(sgn, -bsum, bsum).asUInt(12, 0)   // UInt(13.W)，≤2304
  private val mag12 = magFull(11, 0)                          // bit12 恒 0
  private val eSum = io.scaleA +& io.scaleB                   // UInt(9.W)，≤508
  private val eRaw = eSum.pad(10) - 120.U(10.W)               // UInt(10.W)
  private val ovf = eRaw > 254.U(10.W)
  private val tiny = eSum < 121.U(9.W)

  private val satCode = Cat(sgn.asUInt, 254.U(8.W), 0x7FFFFF80.U(31.W))
  private val normCode = Cat(sgn.asUInt, eRaw(7, 0), mag12, 0.U(19.W))
  io.blk := Mux(tiny || magFull === 0.U, 0.U(40.W), Mux(ovf, satCode, normCode))
}