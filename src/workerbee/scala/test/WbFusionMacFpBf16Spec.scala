import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

// revised = BF16-enabled lane0 (FpMulBf16Mx); baseline = MX-only class default (FpMulMx8 lane0).
class WbFusionMacFpBf16SpecHarness(aW: Int = 16, bW: Int = 16, cW: Int = 40) extends Module {
  private val cw = math.max(cW, bW)
  val io = IO(new Bundle {
    val a = Input(UInt(aW.W)); val b = Input(UInt(bW.W)); val cReg = Input(UInt(cw.W))
    val aFmt = Input(DataFormat()); val bFmt = Input(DataFormat())
    val scaleA = Input(UInt(8.W)); val scaleB = Input(UInt(8.W))
    val rnd = Input(RoundingMode()); val consumeValid = Input(Bool())
    val sum = Output(UInt(cw.W)); val original = Output(UInt(cw.W))
  })
  val revised = Module(new WbFusionMacFp(aW, bW, cW,
    supportedFmts = WbFusionMacFpBf16SpecHarness.Bf16Fmts, narrowW = 19))
  val baseline = Module(new WbFusionMacFp(aW = aW, bW = bW, cW = cW))
  val mxPair = RegNext(DataFormat.isMx(io.aFmt) && DataFormat.isMx(io.bFmt))
  revised.io.a := io.a; revised.io.b := io.b; revised.io.cReg := io.cReg
  revised.io.aFmt := io.aFmt; revised.io.bFmt := io.bFmt
  revised.io.scaleA := io.scaleA; revised.io.scaleB := io.scaleB
  revised.io.rnd := io.rnd; revised.io.consumeValid := io.consumeValid
  baseline.io.a := io.a; baseline.io.b := io.b; baseline.io.cReg := io.cReg
  baseline.io.aFmt := io.aFmt; baseline.io.bFmt := io.bFmt
  baseline.io.scaleA := io.scaleA; baseline.io.scaleB := io.scaleB
  baseline.io.rnd := io.rnd; baseline.io.consumeValid := io.consumeValid && mxPair
  io.sum := revised.io.sum; io.original := baseline.io.sum
}

object WbFusionMacFpBf16SpecHarness {
  val Bf16Fmts: Set[DataFormat.Type] = Set(DataFormat.MXE4M3, DataFormat.MXE5M2,
    DataFormat.MXE2M1, DataFormat.BF16)
}

class WbFusionMacFpBf16Spec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default)
  private val W = 19
  private val E2 = DataFormat.MXE2M1; private val E4 = DataFormat.MXE4M3
  private val E5 = DataFormat.MXE5M2; private val BF = DataFormat.BF16
  private val mxFormats = Seq(E4, E5, E2)
  private val rounds = Seq(RoundingMode.RNE, RoundingMode.RTZ, RoundingMode.RDN,
    RoundingMode.RUP, RoundingMode.RMM)
  private val mask31 = (BigInt(1) << 31) - 1
  private val mask40 = (BigInt(1) << 40) - 1

  // Exact dyadic arithmetic; no Float/Double, RTL-derived goldens, or shared software models.
  private case class Dy(n: BigInt, p: Int) {
    def +(b: Dy): Dy = {
      val e = math.min(p, b.p)
      Dy((n << (p - e)) + (b.n << (b.p - e)), e)
    }
    def unary_- : Dy = Dy(-n, p)
    def -(b: Dy): Dy = this + (-b)
    def *(b: Dy): Dy = Dy(n * b.n, p + b.p)
    def leAbs(b: Dy): Boolean = {
      val e = math.min(p, b.p)
      (n.abs << (p - e)) <= (b.n.abs << (b.p - e))
    }
    def expField: Int = { require(n != 0); n.abs.bitLength - 1 + p + 127 }
  }
  private val zero = Dy(0, 0)
  private def bf(s: Int, e: Int, m: Int): Int = (s << 15) | (e << 7) | m
  private def bfValue(bits: BigInt): Dy = {
    val v = bits.toInt & 0xffff; val e = (v >> 7) & 255
    require(e != 255, "BF16 golden requires finite operands")
    if (e == 0) zero else Dy(BigInt(if ((v & 0x8000) != 0) -1 else 1) * (128 + (v & 127)), e - 134)
  }
  private val fp32Max = Dy((BigInt(1) << 24) - 1, 104)
  private def bfProduct(a: BigInt, b: BigInt): Dy = {
    val p = bfValue(a) * bfValue(b)
    if (p.n == 0 || p.expField < 1) zero
    else if (p.expField > 254) { if (p.n < 0) -fp32Max else fp32Max }
    else p
  }
  private def lazyValue(raw: BigInt): Dy =
    Dy((if (raw.testBit(39)) -1 else 1) * (raw & mask31), ((raw >> 31) & 255).toInt - 157)
  private def lazyPack(s: Int, e: Int, m: BigInt): BigInt = {
    require(e >= 1 && e <= 254 && m > 0 && m <= mask31)
    (BigInt(s) << 39) | (BigInt(e) << 31) | m
  }
  private def lazyExact(v: Dy): BigInt = {
    if (v.n == 0) BigInt(0)
    else {
      val trailing = v.n.abs.bigInteger.getLowestSetBit
      val m = v.n.abs >> trailing; val bl = m.bitLength
      require(bl <= 31)
      lazyPack(if (v.n < 0) 1 else 0, v.expField, m << (31 - bl))
    }
  }
  private def bound(c: BigInt, p: Dy): Dy = {
    val cE = if ((c & mask40) == 0 || lazyValue(c).n == 0) 0 else ((c >> 31) & 255).toInt
    val e = math.max(cE, if (p.n == 0) 0 else p.expField)
    require(e <= 253, "absolute-error tests must exclude accumulator exponent saturation")
    Dy(9, e - 126 - W) // Two alignment truncations plus at most seven growth LSBs.
  }
  private def checkRaw(raw: BigInt): Unit = {
    (raw >> 40) shouldBe BigInt(0)
    ((raw >> 31) & 255).toInt should be <= 254
    (raw & ((BigInt(1) << (31 - W)) - 1)) shouldBe BigInt(0)
    if ((raw & mask31) == 0) raw shouldBe BigInt(0)
  }
  private def checkExact(raw: BigInt, expected: Dy): Unit = {
    checkRaw(raw)
    (lazyValue(raw) - expected).n shouldBe BigInt(0)
  }
  private case class Vec(tag: String, a: BigInt, b: BigInt, c: BigInt = 0,
    af: DataFormat.Type = DataFormat.BF16, bf: DataFormat.Type = DataFormat.BF16,
    sa: Int = 127, sb: Int = 127, rnd: RoundingMode.Type = RoundingMode.RNE)

  private def stageFmt(d: WbFusionMacFpBf16SpecHarness, af: DataFormat.Type, bf: DataFormat.Type): Unit = {
    d.io.consumeValid.poke(false.B); d.io.a.poke(0.U); d.io.b.poke(0.U); d.io.cReg.poke(0.U)
    d.io.aFmt.poke(af); d.io.bFmt.poke(bf); d.clock.step()
  }
  private def resetDut(d: WbFusionMacFpBf16SpecHarness): Unit = {
    d.reset.poke(true.B); d.io.consumeValid.poke(false.B)
    d.io.aFmt.poke(BF); d.io.bFmt.poke(BF)
    d.io.a.poke(0.U); d.io.b.poke(0.U); d.io.cReg.poke(0.U)
    d.io.scaleA.poke(127.U); d.io.scaleB.poke(127.U); d.io.rnd.poke(RoundingMode.RNE)
    d.clock.step(3); d.reset.poke(false.B); d.clock.step(2)
  }
  private def sample(d: WbFusionMacFpBf16SpecHarness, v: Vec, valid: Boolean = true): (BigInt, BigInt) = {
    d.io.a.poke(v.a.U); d.io.b.poke(v.b.U); d.io.cReg.poke(v.c.U)
    d.io.scaleA.poke(v.sa.U); d.io.scaleB.poke(v.sb.U); d.io.rnd.poke(v.rnd)
    d.io.consumeValid.poke(valid.B)
    val result = (d.io.sum.peek().litValue, d.io.original.peek().litValue)
    d.clock.step() // Exercise assertions at the consuming edge, not merely combinational peeks.
    result
  }
  private def drive(d: WbFusionMacFpBf16SpecHarness, v: Vec): (BigInt, BigInt) = {
    stageFmt(d, v.af, v.bf); sample(d, v)
  }

  private def params(f: DataFormat.Type): MxFormatParams =
    if (f == E4) MxFormatParams.E4M3 else if (f == E5) MxFormatParams.E5M2 else MxFormatParams.E2M1
  private def mxCode(s: Int, e: Int, m: Int, f: DataFormat.Type): Int = {
    val p = params(f); (s << (p.expW + p.mantW)) | (e << p.mantW) | m
  }
  private def mxElem(code: Int, scale: Int, f: DataFormat.Type): Dy = {
    val p = params(f); val e = (code >> p.mantW) & ((1 << p.expW) - 1)
    val m = code & ((1 << p.mantW) - 1); val ef = e + scale - p.bias
    require(f == E2 || (if (f == E5) e != 31 else !(e == 15 && m == 7)))
    require(ef <= 254)
    val sign = if (((code >> (p.expW + p.mantW)) & 1) == 0) 1 else -1
    if (ef < 1 || (e == 0 && (m == 0 || f != E2))) zero
    else if (e == 0) Dy(sign, scale - 128) // E2M1 0.5 times the E8M0 scale.
    else Dy(BigInt(sign) * ((1 << p.mantW) + m), e - p.bias - p.mantW + scale - 127)
  }
  private def mxGolden(v: Vec): Dy = {
    val lanes = if (v.af == E2 && v.bf == E2) 4 else 2
    def elem(bits: BigInt, lane: Int, f: DataFormat.Type, scale: Int): Dy = {
      val stride = if (f == E2) 4 else 8
      mxElem(((bits >> (stride * lane)) & ((1 << stride) - 1)).toInt, scale, f)
    }
    (0 until lanes).foldLeft(lazyValue(v.c)) { (acc, lane) =>
      acc + elem(v.a, lane, v.af, v.sa) * elem(v.b, lane, v.bf, v.sb)
    }
  }
  private def semanticWord(f: DataFormat.Type, flip: Int): Int = {
    val p = params(f); val count = if (f == E2) 4 else 2; val stride = if (f == E2) 4 else 8
    (0 until count).map { i =>
      val e = if (f == E2) i else p.bias + i
      val m = if (f == E2) 1 else (i + 1) & ((1 << p.mantW) - 1)
      mxCode((i + flip) & 1, e, m, f) << (stride * i)
    }.foldLeft(0)(_ | _)
  }
  private def legalWord(r: Random, f: DataFormat.Type): Int = {
    def legal(code: Int): Boolean = f == E2 ||
      (if (f == E5) ((code >> 2) & 31) != 31 else (code & 127) != 127)
    val stride = if (f == E2) 4 else 8; val count = 16 / stride
    var word = 0; var safe = false
    while (!safe) {
      word = (0 until count).map { i =>
        var code = r.nextInt(1 << stride)
        while (!legal(code)) code = r.nextInt(1 << stride)
        code << (stride * i)
      }.foldLeft(0)(_ | _)
      // The MX-only baseline's unused MX8 converters are unconditionally guarded even for MX4.
      safe = f != E2 || ((word & 127) != 127 && ((word >> 8) & 127) != 127)
    }
    word
  }

  behavior of "WbFusionMacFp BF16 lane0 independent finite BF16 and MX W19 contract"

  it should "produce exact standalone BF16 products and share the multiplier with MX mantissas" in {
    simulate(new FpMulBf16Mx()) { d =>
      d.reset.poke(true.B); d.io.isBf16.poke(true.B)
      d.io.aSign.poke(0.U); d.io.aExp.poke(0.U); d.io.aMant.poke(0.U)
      d.io.bSign.poke(0.U); d.io.bExp.poke(0.U); d.io.bMant.poke(0.U)
      d.clock.step(); d.reset.poke(false.B)
      def checkProduct(sa: Int, ea: Int, ma: Int, sb: Int, eb: Int, mb: Int,
                       isBf16: Boolean, expected: Dy): Unit = {
        d.io.aSign.poke(sa.U); d.io.aExp.poke(ea.U); d.io.aMant.poke(ma.U)
        d.io.bSign.poke(sb.U); d.io.bExp.poke(eb.U); d.io.bMant.poke(mb.U)
        d.io.isBf16.poke(isBf16.B)
        val bits = if (expected.n == 0) BigInt(0) else {
          val significand = expected.n.abs << (24 - expected.n.abs.bitLength)
          (BigInt(if (expected.n < 0) 1 else 0) << 31) |
            (BigInt(expected.expField) << 23) | (significand - (BigInt(1) << 23))
        }
        d.io.prod.expect(bits.U); d.clock.step()
      }
      for (ma <- 0 until 128; mb <- 0 until 128; sa <- 0 to 1; sb <- 0 to 1) {
        withClue(s"standalone BF16 ma=$ma mb=$mb sign=$sa/$sb: ") {
          checkProduct(sa, 127, ma, sb, 127, mb, true,
            bfValue(bf(sa, 127, ma)) * bfValue(bf(sb, 127, mb)))
        }
      }
      val exponents = Seq(0, 1, 2, 125, 126, 127, 128, 253, 254)
      for (ea <- exponents; eb <- exponents; ma <- Seq(0, 127); mb <- Seq(0, 127);
           sa <- 0 to 1; sb <- 0 to 1) {
        withClue(s"standalone BF16 edge e=$ea/$eb m=$ma/$mb sign=$sa/$sb: ") {
          checkProduct(sa, ea, ma, sb, eb, mb, true, bfProduct(bf(sa, ea, ma), bf(sb, eb, mb)))
        }
      }
      for (ma <- 0 until 8; mb <- 0 until 8; sa <- 0 to 1; sb <- 0 to 1) {
        withClue(s"standalone MX ma=$ma mb=$mb sign=$sa/$sb: ") {
          val expected = Dy(BigInt(if (sa == sb) 1 else -1) * (8 + ma) * (8 + mb), -6)
          checkProduct(sa, 127, ma << 4, sb, 127, mb << 4, false, expected)
        }
      }
    }
  }

  it should "exhaust all 128x128 BF16 mantissas with all four operand sign pairs at C zero" in {
    simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
      resetDut(d)
      for (ma <- 0 until 128; mb <- 0 until 128; sa <- 0 to 1; sb <- 0 to 1) {
        withClue(s"BF16 exhaustive ma=$ma mb=$mb sign=$sa/$sb: ") {
          val v = Vec("exhaustive", bf(sa, 127, ma), bf(sb, 127, mb))
          checkExact(sample(d, v)._1, bfValue(v.a) * bfValue(v.b))
        }
      }
    }
  }

  it should "honor BF16 FTZ and finite product saturation independently of scale and rounding" in {
    val edges = Seq(
      Vec("positive-zero", 0, bf(0, 254, 127)), Vec("negative-zero", 0x8000, bf(1, 254, 127)),
      Vec("subnormal-a", 0x007f, bf(0, 254, 127)), Vec("subnormal-b", bf(0, 254, 127), 0x807f),
      Vec("both-subnormal", 1, 0x8001), Vec("normal-min", bf(0, 1, 0), bf(0, 127, 0)),
      Vec("normal-max-exp", bf(1, 254, 127), bf(0, 127, 0)),
      Vec("underflow-no-normalize", bf(0, 1, 0), bf(0, 126, 0)),
      Vec("normalize-rescues-min", bf(1, 1, 127), bf(0, 126, 127)),
      Vec("underflow-even-normalized", bf(0, 1, 127), bf(0, 125, 127)),
      Vec("normalize-reaches-max", bf(0, 253, 127), bf(0, 127, 127)),
      Vec("normalize-overflows", bf(0, 254, 127), bf(0, 127, 127)),
      Vec("overflow-without-normalize", bf(1, 254, 0), bf(0, 128, 0)),
      Vec("extreme-overflow", bf(1, 254, 127), bf(1, 254, 127)),
      Vec("positive-saturation-before-C", bf(0, 254, 127), bf(0, 254, 127), lazyExact(-fp32Max)),
      Vec("negative-saturation-before-C", bf(1, 254, 127), bf(0, 254, 127), lazyExact(fp32Max)))
    simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
      resetDut(d)
      for (v <- edges; rnd <- rounds; scales <- Seq((0, 255), (255, 0), (127, 127), (19, 203))) {
        withClue(s"${v.tag} rnd=$rnd scales=$scales: ") {
          val product = bfProduct(v.a, v.b)
          val expected = if (v.c != 0) lazyValue(v.c) + product
          else if (product.n.abs == fp32Max.n && product.p == fp32Max.p)
            Dy((if (product.n < 0) -1 else 1) * ((BigInt(1) << W) - 1), 254 - 126 - W)
          else product
          checkExact(sample(d, v.copy(sa = scales._1, sb = scales._2, rnd = rnd))._1, expected)
        }
      }
    }
  }

  it should "bound nonzero-C cancellation and wide spans and retain independent feedback-chain goldens" in {
    simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
      resetDut(d)
      val one = bf(0, 127, 0); val prod = bfProduct(bf(0, 127, 127), bf(0, 127, 127))
      val cancellation = lazyExact(-prod)
      val directed = Seq(
        (Vec("C-plus-one", one, one, lazyExact(Dy(1, 0))), true),
        (Vec("complete-cancel", bf(0, 127, 127), bf(0, 127, 127), cancellation), true),
        (Vec("near-cancel-plus-raw-lsb", bf(0, 127, 127), bf(0, 127, 127), cancellation + 1), false),
        (Vec("near-cancel-minus-raw-lsb", bf(0, 127, 127), bf(0, 127, 127), cancellation - 1), false),
        (Vec("exact-small-residual", one, one, lazyExact(Dy(-127, -7))), true),
        (Vec("C-dominates-220-exponents", bf(1, 20, 17), one, lazyPack(0, 240, mask31)), false),
        (Vec("product-dominates-C", bf(0, 220, 123), one, lazyPack(1, 1, mask31)), false),
        (Vec("lazy-leading-zeros", one, one, lazyPack(1, 150, 12345)), false),
        (Vec("FTZ-preserves-exact-C", 0x807f, one, lazyExact(Dy(3, -2))), true))
      val r = new Random(0xbf1619L)
      val random = (0 until 512).map { i =>
        (Vec(s"random-C-$i", bf(r.nextInt(2), 80 + r.nextInt(96), r.nextInt(128)),
          bf(r.nextInt(2), 80 + r.nextInt(96), r.nextInt(128)),
          lazyPack(r.nextInt(2), 1 + r.nextInt(240), BigInt(31, r).max(BigInt(1)))), false)
      }
      for ((v, exactDomain) <- directed ++ random) withClue(s"${v.tag}: ") {
        val p = bfProduct(v.a, v.b); val exact = lazyValue(v.c) + p
        val out = sample(d, v)._1; checkRaw(out)
        (lazyValue(out) - exact).leAbs(bound(v.c, p)) shouldBe true
        if (exactDomain) checkExact(out, exact)
      }
      var feedback = lazyExact(Dy(1, 0)); var ideal = Dy(1, 0); var accumulatedBound = zero
      for (i <- 0 until 256) withClue(s"feedback-step-$i: ") {
        val b = if (i < 128) bf(if (i < 64) 0 else 1, 127, 0)
          else bf(i & 1, 110 + (i % 33), (i * 37) & 127)
        val v = Vec(s"feedback-$i", one, b, feedback); val p = bfProduct(v.a, v.b)
        val stepBound = bound(v.c, p); val localExact = lazyValue(v.c) + p
        ideal = ideal + p; accumulatedBound = accumulatedBound + stepBound
        val out = sample(d, v)._1; checkRaw(out)
        (lazyValue(out) - localExact).leAbs(stepBound) shouldBe true
        (lazyValue(out) - ideal).leAbs(accumulatedBound) shouldBe true
        if (i < 128) checkExact(out, ideal)
        feedback = out
      }
    }
  }

  it should "regress all nine MX format pairs bit-for-bit against the MX-only default config" in {
    simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
      resetDut(d); val r = new Random(0x4d5819L)
      for (af <- mxFormats; bf <- mxFormats) {
        stageFmt(d, af, bf)
        for (i <- 0 until 192) withClue(s"MX regression $af x $bf case=$i: ") {
          val v = Vec(s"MX-$i", legalWord(r, af), legalWord(r, bf),
            lazyPack(r.nextInt(2), 1 + r.nextInt(254), BigInt(31, r).max(BigInt(1))),
            af, bf, r.nextInt(256), r.nextInt(256), rounds(i % rounds.size))
          val (out, original) = sample(d, v)
          checkRaw(out); out shouldBe original
        }
      }
    }
  }

  it should "check independent MX element semantics including 0.5 and both MX8-MX4 lane orientations" in {
    simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
      resetDut(d)
      for (af <- mxFormats; bf <- mxFormats; flip <- 0 to 1; scales <- Seq((127, 127), (130, 124), (126, 128))) {
        val v = Vec(s"semantic-$af-$bf-$flip-$scales", semanticWord(af, flip), semanticWord(bf, 1),
          lazyExact(Dy(1, 0)), af, bf, scales._1, scales._2)
        withClue(s"${v.tag}: ") {
          val (out, original) = drive(d, v); val golden = mxGolden(v)
          checkExact(out, golden); checkExact(original, golden)
        }
      }
      withClue("four lanes of MX4 0.5 squared = 1: ") {
        val v = Vec("all-half", 0x1111, 0x1111, af = E2, bf = E2)
        (mxGolden(v) - Dy(1, 0)).n shouldBe BigInt(0)
        val (out, original) = drive(d, v)
        checkExact(out, Dy(1, 0)); checkExact(original, Dy(1, 0))
      }
    }
  }

  it should "use captured rather than current formats and not misclassify BF16 bytes as MX NaNs" in {
    simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
      resetDut(d)
      val vectors = Seq(Vec("BF-high-7f", 0x7f01, bf(0, 1, 0)),
        Vec("BF-high-ff", 0xff01, bf(0, 1, 127)), Vec("BF-high-7c", 0x7c01, bf(0, 7, 0)),
        Vec("BF-low-7f", bf(0, 126, 127), bf(0, 127, 0)),
        Vec("switch-E5-E2", 0x3c3c, 0x1111, af = E5, bf = E2),
        Vec("switch-E2-E4", 0x1111, 0x3838, af = E2, bf = E4),
        Vec("switch-quad", 0x1111, 0x1111, af = E2, bf = E2))
      for (i <- 0 until 35) {
        val v = vectors(i % vectors.size); val next = vectors((i + 1) % vectors.size)
        withClue(s"format-pipeline-$i ${v.tag} next=${next.tag}: ") {
          stageFmt(d, v.af, v.bf)
          d.io.aFmt.poke(next.af); d.io.bFmt.poke(next.bf)
          val out = sample(d, v)._1
          checkExact(out, if (v.af == BF) bfProduct(v.a, v.b) else mxGolden(v))
        }
      }
      for (v <- Seq(Vec("idle-mixed", 0x7f80, 0xffff, af = BF, bf = E4),
        Vec("idle-inf", 0x7f80, 0xffc1), Vec("idle-nan", 0x7fc1, 0x7f80))) {
        withClue(s"${v.tag} consumeValid=false: ") {
          stageFmt(d, v.af, v.bf); sample(d, v, valid = false)
        }
      }
    }
  }

  private val rejectedPairs = for (mx <- mxFormats; reverse <- Seq(false, true)) yield
    Vec(s"BF16-MX-$mx-reverse-$reverse", 0, 0, af = if (reverse) mx else BF, bf = if (reverse) BF else mx)
  private val rejectedFiniteCodes = for (code <- Seq(0x7f80, 0xff80, 0x7fc1, 0xff81); side <- Seq("a", "b")) yield
    Vec(s"BF16-special-${code.toHexString}-$side", if (side == "a") code else bf(0, 127, 0),
      if (side == "b") code else bf(0, 127, 0))
  for (v <- rejectedPairs ++ rejectedFiniteCodes) {
    it should s"reject ${v.tag} at a consuming clock edge" in {
      withClue(s"${v.tag}: ") {
        val ex = intercept[Exception] {
          simulate(new WbFusionMacFpBf16SpecHarness()) { d =>
            resetDut(d); stageFmt(d, v.af, v.bf)
            sample(d, v, valid = false); sample(d, v)
          }
        }
        ex.getClass.getName should include("AssertionFailed")
      }
    }
  }

  it should "enforce BF16 parameter guards and accept legal endpoints and wide lazy ports" in {
    val fmts = WbFusionMacFpBf16SpecHarness.Bf16Fmts
    for ((c, w, message) <- Seq((39, 19, "cEffW(39)"), (40, 15, "BF16 requires narrowW"),
      (40, 7, "BF16 requires narrowW"), (40, 29, "W(29) > 28")))
      withClue(s"parameter guard cW=$c W=$w: ") {
        val ex = intercept[IllegalArgumentException] {
          _root_.circt.stage.ChiselStage.emitSystemVerilog(
            new WbFusionMacFp(16, 16, c, fmts, narrowW = w))
        }
        ex.getMessage should include(message)
      }
    withClue("FP16 is outside the FpAddNarrow whitelist: ") {
      val ex = intercept[IllegalArgumentException] {
        _root_.circt.stage.ChiselStage.emitSystemVerilog(
          new WbFusionMacFp(supportedFmts = fmts + DataFormat.FP16))
      }
      ex.getMessage should include("FpAddNarrow")
    }
    for (w <- Seq(16, 28)) withClue(s"legal narrowW endpoint $w: ") {
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacFp(supportedFmts = fmts, narrowW = w)) should not be empty
    }
    simulate(new WbFusionMacFpBf16SpecHarness(aW = 24, bW = 48, cW = 8)) { d =>
      resetDut(d)
      withClue("cEffW=max(cW,bW), high input bits ignored and high output bits zero: ") {
        d.io.a.getWidth shouldBe 24; d.io.b.getWidth shouldBe 48; d.io.cReg.getWidth shouldBe 48
        val v = Vec("wide-ports", (BigInt(0xab) << 16) | bf(0, 127, 64),
          (BigInt("deadbeef", 16) << 16) | bf(1, 127, 0),
          (BigInt(0xa5) << 40) | lazyExact(Dy(2, 0)))
        checkExact(sample(d, v)._1, Dy(1, -1))
      }
    }
  }
}
