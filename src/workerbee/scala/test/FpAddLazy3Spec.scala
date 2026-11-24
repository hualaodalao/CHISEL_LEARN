/** FpAddLazy3Spec — C 方案三槽全 lazy-40 累加器精度 + 与 FpAddNarrow 逐位对拍
  *
  * 两条独立证据：
  *   ① 直接金锚：Dyadic 精确真值 + 整数规格模型（J0 实现忠实 / J1 无损 / J2 相对阈 / J3 绝对界），
  *      全程整数算出，禁拟合 RTL。覆盖 k∈{0,1,2}、expOut 钳 254、完全相消、零槽、term-drop、前导零。
  *   ② 逐位对拍：同一 simulate 内例化 FpAddLazy3 与 FpAddNarrow，宿主把 fp32 乘积打包成 lazy-40
  *      喂三槽，FpAddNarrow 走 MX8(is2x=1)/BF16(is2x=0) 掩零模式 ⟹ 两者输出必须【逐位相等】。
  *      这是 C 方案非 MX4 族零 diff 的依据。对拍对任意 fp32/lazy 位
  *      模式成立（结构等价，非精度依赖），故用全随机位模式 + 结构化边界。
  * 纯组合：poke 后 step(1) 再 peek（与 FpAddNarrowSpec 同约定）。
  */

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.util.Random

class FpAddLazy3Spec extends AnyFlatSpec with Matchers with ChiselSim {

  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  // Dyadic：value = m × 2^e，规范形（m 奇或零）⟹ 加/减/乘封闭无舍入，结构相等即数学相等
  final class Dyadic(val m: BigInt, val e: Int) {
    def isZero: Boolean = m.signum == 0
    def signum: Int = m.signum
    def abs: Dyadic = if (m.signum < 0) Dyadic(-m, e) else this
    def unary_- : Dyadic = Dyadic(-m, e)
    def +(o: Dyadic): Dyadic =
      if (isZero) o else if (o.isZero) this
      else { val k = math.min(e, o.e); Dyadic(m * (BigInt(1) << (e - k)) + o.m * (BigInt(1) << (o.e - k)), k) }
    def -(o: Dyadic): Dyadic = this + (-o)
    def shl(n: Int): Dyadic = Dyadic(m, e + n)
    def leAbs(o: Dyadic): Boolean =
      if (isZero) true else if (o.isZero) false
      else { val k = math.min(e, o.e); (m.abs << (e - k)) <= (o.m.abs << (o.e - k)) }
    override def toString: String = if (isZero) "0" else s"$m*2^$e"
  }
  object Dyadic {
    val Zero: Dyadic = new Dyadic(BigInt(0), 0)
    def apply(m: BigInt, e: Int): Dyadic = {
      if (m.signum == 0) Zero
      else { var mm = m.abs; var ee = e
        while (mm > 1 && !mm.testBit(0)) { mm >>= 1; ee += 1 }
        new Dyadic(if (m.signum < 0) -mm else mm, ee) }
    }
  }

  private def lazy40ToDyadic(raw: BigInt): Dyadic = {
    val s = ((raw >> 39) & 1).toInt
    val e = ((raw >> 31) & 0xFF).toInt
    val m31 = raw & ((BigInt(1) << 31) - 1)
    if (e == 0 && m31 == 0) Dyadic.Zero
    else Dyadic(BigInt(if (s == 1) -1 else 1) * m31, e - 157)
  }
  private def dyadicToLazy40(v: Dyadic, lz: Int = 0): BigInt = {
    if (v.isZero) return BigInt(0)
    val s = if (v.signum < 0) 1 else 0
    val mag = v.m.abs; val bl = mag.bitLength
    require(bl + lz <= 31, s"mant31 装不下 bl=$bl lz=$lz")
    val mant31 = mag << (30 - lz - (bl - 1))
    val expField = v.e + 126 + lz + bl
    require(expField >= 1 && expField <= 255, s"expField=$expField 越域")
    (BigInt(s) << 39) | (BigInt(expField) << 31) | mant31
  }
  private def cRaw(s: Int, e: Int, m31: BigInt): BigInt =
    (BigInt(s) << 39) | (BigInt(e) << 31) | m31

  // 整数规格模型（镜像 FpAddLazy3 头注算法，不跑 RTL）：三 lazy-40 槽 → 期望 raw + 精度分析量
  private final case class M3(modelRaw: BigInt, exact: Dyadic, eCommon: Int, lsb: Dyadic,
                              nLossy: Int, nDrop: Int, magFull: BigInt, k: Int, expOut: Int,
                              absBound: Dyadic, mustRel: Boolean, lossless: Boolean, clamped: Boolean)
  private def model3(c: BigInt, s1: BigInt, s2: BigInt, w: Int): M3 = {
    val relShift = w - 1
    val slots = Seq(c, s1, s2).map { raw =>
      val sg = ((raw >> 39) & 1).toInt
      val e = ((raw >> 31) & 0xFF).toInt
      val m31 = raw & ((BigInt(1) << 31) - 1)
      val z = (e == 0) && (m31.signum == 0)
      (sg, e, m31, z)
    }
    val live = slots.filterNot(_._4)
    val eCommon = if (live.isEmpty) 0 else live.map(_._2).max
    val lsb = Dyadic(BigInt(1), eCommon - 126 - w)
    var nLossy = 0; var nDrop = 0; var signedSum = BigInt(0)
    live.foreach { case (sg, e, m31, _) =>
      val sh = math.min(math.max(eCommon - e, 0), w)
      val ts = (31 - w) + sh
      val a = m31 >> ts
      if (ts > 0 && (m31 & ((BigInt(1) << ts) - 1)).signum != 0) nLossy += 1
      if (eCommon - e >= w) nDrop += 1
      signedSum += (if (sg == 1) -a else a)
    }
    val exact = live.foldLeft(Dyadic.Zero)((acc, s) => acc + lazy40ToDyadic(
      (BigInt(s._1) << 39) | (BigInt(s._2) << 31) | s._3))
    val magFull = signedSum.abs
    val k = math.max(0, magFull.bitLength - w)
    val magTop = magFull >> k
    val expOutRaw = eCommon + k
    val clamped = expOutRaw > FpAddLazy3.ExpSatMax
    val expOut = if (clamped) FpAddLazy3.ExpSatMax else expOutRaw
    val modelRaw =
      if (magFull.signum == 0) BigInt(0)
      else (BigInt(if (signedSum.signum < 0) 1 else 0) << 39) | (BigInt(expOut) << 31) | (magTop << (31 - w))
    val truncMax = (BigInt(1) << k) - 1
    val absBound = Dyadic(BigInt(nLossy) + truncMax, lsb.e)
    // 钳位域（E+k>254）属 out-of-spec(E3)：expOut 饱和改变真值，绝对/相对界均不适用 ⟹ 只验 J0+守卫
    val mustRel = !clamped && (BigInt(nLossy) + truncMax) * (BigInt(1) << relShift) <= (magFull - BigInt(nLossy))
    val lossless = (nLossy == 0) && (k == 0) && !clamped
    M3(modelRaw, exact, eCommon, lsb, nLossy, nDrop, magFull, k, expOut, absBound, mustRel, lossless, clamped)
  }

  private def judge3(tag: String, raw: BigInt, a: M3, w: Int): Unit = {
    assert((raw >> 40).signum == 0, s"[$tag] 输出超 40 位 0x${raw.toString(16)}")
    val eOut = ((raw >> 31) & 0xFF).toInt
    val mOut = raw & ((BigInt(1) << 31) - 1)
    assert(eOut <= FpAddLazy3.ExpSatMax, s"[$tag] eOut=$eOut > 254 ⟹ 下游归一化成 NaN")
    assert(!(eOut == 0 && mOut.signum != 0), s"[$tag] lazy-40 不变量违背 exp==0 但 mant31!=0")
    val j0 = raw == a.modelRaw
    assert(j0, s"[$tag] J0 违背：RTL=0x${raw.toString(16)} 模型=0x${a.modelRaw.toString(16)} " +
      s"(E=${a.eCommon} k=${a.k} |S|=${a.magFull} nLossy=${a.nLossy})")
    if (a.clamped) return   // 钳位域 out-of-spec(E3)：只验 J0 + 上方结构守卫，不做金锚精度裁决
    val got = lazy40ToDyadic(raw)
    val diff = got - a.exact
    val j1 = diff.isZero
    val j2 = diff.shl(w - 1).leAbs(a.exact)
    val j3 = diff.abs.leAbs(a.absBound)
    assert(!a.lossless || j1, s"[$tag] A2 违背：解析无损但 diff=$diff≠0")
    assert(!a.mustRel || j2, s"[$tag] A3 违背：充分条件成立但相对误差超 2^-${w - 1}")
    assert(j2 || j3, s"[$tag] J2/J3 双双不成立：diff=$diff exact=${a.exact} absBound=${a.absBound}")
  }

  private val EB = 140
  private def msb(w: Int): BigInt = BigInt(1) << (w - 1)
  private def fullWinM31(w: Int): BigInt = ((BigInt(1) << w) - 1) << (31 - w)

  // 结构化向量（三 lazy-40 槽），覆盖 k0/k1/k2、钳位、相消、零、drop、前导零
  private def structVecs(w: Int): Seq[(String, BigInt, BigInt, BigInt)] = {
    val b = mutable.ArrayBuffer[(String, BigInt, BigInt, BigInt)]()
    val selfC = cRaw(0, EB, msb(w) << (31 - w))                 // 自产形态 cReg（低 31−W 位 0）
    val pEB = dyadicToLazy40(Dyadic(msb(w), EB - 126 - w))      // 窗口 MSB 幅值、expField=EB 的乘积槽
    b += (("N1", BigInt(0), pEB, pEB))                          // 两槽同指数 ⟹ |S|=2^W ⟹ k=1
    b += (("N2", selfC, pEB, dyadicToLazy40(Dyadic(BigInt(3) << (w - 4), EB - 126 - w))))
    b += (("K0", BigInt(0), pEB, BigInt(0)))                    // 单槽 ⟹ k=0 透传
    b += (("K1", BigInt(0), pEB, pEB))                          // k=1
    b += (("K2", cRaw(0, EB, fullWinM31(w)),
      dyadicToLazy40(Dyadic((BigInt(1) << w) - 1, EB - 126 - w)),
      dyadicToLazy40(Dyadic((BigInt(1) << w) - 1, EB - 126 - w))))  // 三满窗口 ⟹ |S|=3(2^W−1) ⟹ k=2
    b += (("X1", BigInt(0), pEB, cRaw(1, EB, msb(w) << (31 - w))))  // 完全相消 ⟹ 真零
    b += (("X2", cRaw(1, EB, fullWinM31(w)),
      dyadicToLazy40(Dyadic((BigInt(1) << w) - 1, EB - 126 - w)), BigInt(0)))  // 满幅相消
    b += (("Z1", BigInt(0), BigInt(0), BigInt(0)))              // 全零
    b += (("Z2", BigInt(0), BigInt(0), pEB))                    // 仅 s2 供数（s1 零槽）
    b += (("Z3", selfC, cRaw(0, 250, fullWinM31(w)), BigInt(0))) // s1 高指数但…验 E 抬升与 drop
    b += (("DR1", selfC, dyadicToLazy40(Dyadic(BigInt(1), EB - 126 - w - (w + 2))),
      dyadicToLazy40(Dyadic(BigInt(1), EB - 126 - w - (w + 2)))))  // 两槽 term-drop
    b += (("LZ1", dyadicToLazy40(Dyadic(BigInt(3), -1), lz = 5), pEB,
      dyadicToLazy40(Dyadic(BigInt(5), EB - 129 - w))))          // cReg 前导零
    b += (("CLAMP", cRaw(0, 254, fullWinM31(w)), cRaw(0, 254, fullWinM31(w)),
      cRaw(0, 254, fullWinM31(w))))                             // E=254,k=2 ⟹ expOut 钳 254
    b += (("NEG", cRaw(1, EB, fullWinM31(w)), cRaw(1, EB - 1, msb(w) << (31 - w)),
      dyadicToLazy40(Dyadic(BigInt(7), EB - 127 - w))))         // 异号混合
    b.toSeq
  }

  behavior of "FpAddLazy3 direct golden (J0 model fidelity + J1/J2/J3 precision)"

  it should "match the integer spec model and precision contract for structured and random lazy-40 triples at W=19" in {
    val w = FpAddLazy3.DefaultW
    val rng = new Random(319)
    var nStruct = 0; var nRand = 0
    val kHist = mutable.SortedMap[Int, Int]()
    simulate(new FpAddLazy3(40, w)) { dut =>
      dut.reset.poke(true.B)
      dut.io.c.poke(0.U); dut.io.s1.poke(0.U); dut.io.s2.poke(0.U)
      dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
      def drive(c: BigInt, s1: BigInt, s2: BigInt): BigInt = {
        dut.io.c.poke(c.U(40.W)); dut.io.s1.poke(s1.U(40.W)); dut.io.s2.poke(s2.U(40.W))
        dut.clock.step(1); dut.io.sum.peek().litValue
      }
      for ((tag, c, s1, s2) <- structVecs(w)) {
        val raw = drive(c, s1, s2); val a = model3(c, s1, s2, w)
        judge3(tag, raw, a, w); kHist(a.k) = kHist.getOrElse(a.k, 0) + 1; nStruct += 1
      }
      for (i <- 0 until 1500) {
        // 随机 lazy-40：exp 偏置在 EB 附近 ±40 以覆盖对齐/drop/k 档，mant31 随机（含前导零形态）
        def randSlot(): BigInt = {
          val e = math.max(1, math.min(254, EB + rng.nextInt(81) - 40))
          val m31 = BigInt(rng.nextInt(1 << 20)) << rng.nextInt(12)
          cRaw(rng.nextInt(2), e, m31 & ((BigInt(1) << 31) - 1))
        }
        val c = if (i % 3 == 0) BigInt(0) else randSlot()
        val s1 = randSlot(); val s2 = if (i % 5 == 0) BigInt(0) else randSlot()
        val raw = drive(c, s1, s2); val a = model3(c, s1, s2, w)
        judge3(s"R$i", raw, a, w); kHist(a.k) = kHist.getOrElse(a.k, 0) + 1; nRand += 1
      }
    }
    println(s"[FpAddLazy3Spec] 直接金锚 PASS 结构化=$nStruct 随机=$nRand；k 档直方图=${kHist.toSeq.mkString(",")}")
    Seq(0, 1, 2).foreach(k => withClue(s"k=$k 未覆盖：") { kHist.getOrElse(k, 0) should be > 0 })
  }

  // 对拍 wrapper：fp32 乘积打包 lazy-40 喂 FpAddLazy3，FpAddNarrow 走掩零模式
  private class CrossCheck(w: Int) extends Module {
    val io = IO(new Bundle {
      val cReg = Input(UInt(40.W))
      val p0   = Input(UInt(32.W))
      val p1   = Input(UInt(32.W))
      val is2x = Input(Bool())
      val lazy3Sum  = Output(UInt(40.W))
      val narrowSum = Output(UInt(40.W))
    })
    def pack(p: UInt): UInt = {
      val e = p(30, 23); val m = p(22, 0)
      val isZero = (e === 0.U) && (m === 0.U)
      Mux(isZero, 0.U(40.W), Cat(p(31), e, 1.U(1.W), m, 0.U(7.W)))
    }
    val l3 = Module(new FpAddLazy3(40, w))
    val na = Module(new FpAddNarrow(40, w))
    l3.io.c := io.cReg
    l3.io.s1 := pack(io.p0)
    l3.io.s2 := Mux(io.is2x, pack(io.p1), 0.U(40.W))
    na.io.cReg := io.cReg
    na.io.is4x := false.B
    na.io.is2x := io.is2x
    na.io.prodFp16 := io.p0
    na.io.prodFp8 := io.p1
    na.io.prodFp4_0 := 0.U
    na.io.prodFp4_1 := 0.U
    io.lazy3Sum := l3.io.sum
    io.narrowSum := na.io.sum
  }

  behavior of "FpAddLazy3 bit-exact cross-check vs FpAddNarrow (MX8/BF16 masked modes)"

  it should "be bit-identical to FpAddNarrow for MX8 (is2x=1) and BF16 (is2x=0) across W=19/16/22" in {
    val rng = new Random(2026)
    for (w <- Seq(19, 16, 22)) {
      var n = 0
      simulate(new CrossCheck(w)) { dut =>
        dut.reset.poke(true.B)
        dut.io.cReg.poke(0.U); dut.io.p0.poke(0.U); dut.io.p1.poke(0.U); dut.io.is2x.poke(false.B)
        dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        def check(c: BigInt, p0: Long, p1: Long, is2x: Boolean, tag: String): Unit = {
          dut.io.cReg.poke(c.U(40.W))
          dut.io.p0.poke((p0 & 0xFFFFFFFFL).U(32.W))
          dut.io.p1.poke((p1 & 0xFFFFFFFFL).U(32.W))
          dut.io.is2x.poke(is2x.B)
          dut.clock.step(1)
          val a = dut.io.lazy3Sum.peek().litValue
          val b = dut.io.narrowSum.peek().litValue
          assert(a == b, s"[W=$w][$tag] 逐位失配 lazy3=0x${a.toString(16)} narrow=0x${b.toString(16)} " +
            s"c=0x${c.toString(16)} p0=0x${p0.toHexString} p1=0x${p1.toHexString} is2x=$is2x")
          n += 1
        }
        // 结构化边界：零、满尾数、高指数钳位、异号、subnormal/inf 位模式（结构等价对任意位模式成立）
        val edges = Seq(0L, 0x80000000L, 0x7F7FFFFFL, 0xFF7FFFFFL, 0x00000001L, 0x007FFFFFL,
          (EB.toLong << 23), (EB.toLong << 23) | 0x7FFFFFL, (254L << 23) | 0x7FFFFFL)
        for (p0 <- edges; p1 <- edges; is2x <- Seq(true, false)) {
          val c = if (is2x) cRaw(0, EB, fullWinM31(w)) else BigInt(0)
          check(c, p0, p1, is2x, "edge")
        }
        // 全随机位模式（含 out-of-spec fp32/lazy）⟹ 强结构等价证据
        for (i <- 0 until 1200) {
          val c = BigInt(40, rng)
          val p0 = rng.nextInt(0x7FFFFFFF).toLong | (if (rng.nextBoolean()) 0x80000000L else 0L)
          val p1 = rng.nextInt(0x7FFFFFFF).toLong | (if (rng.nextBoolean()) 0x80000000L else 0L)
          check(c, p0, p1, rng.nextBoolean(), s"rand$i")
        }
      }
      println(s"[FpAddLazy3Spec] W=$w 逐位对拍 PASS $n 例（MX8 三槽 + BF16 两槽掩零）")
    }
  }
}