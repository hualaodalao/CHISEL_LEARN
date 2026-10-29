/** WbFusionMacIntSpec -- INT8 dual-lane / INT4 quad-lane unit test
  *
  * Golden reference: pure Scala BigInt computation (no WbDebugModel dependency).
  * Timing contract: fmt uses RegNext => step(1) after poke to settle.
  * Overflow semantics: truncation wrap-around (two's complement, no clamping).
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class WbFusionMacIntSpec extends AnyFlatSpec with Matchers with ChiselSim {

  // Plain verilator backend (no trace, no lz4 dependency)
  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  private val cEffW = 40

  // Pure Scala golden helpers

  /** Sign-extend a `bits`-wide value (given as BigInt, unsigned interpretation) to signed BigInt. */
  private def sext(value: BigInt, bits: Int): BigInt = {
    val mask = (BigInt(1) << bits) - 1
    val v = value & mask
    if (v.testBit(bits - 1)) v - (BigInt(1) << bits) else v
  }

  /** Truncate a signed BigInt to `bits`-wide two's complement (wrap-around), return signed. */
  private def truncSInt(value: BigInt, bits: Int): BigInt = {
    val mask = (BigInt(1) << bits) - 1
    val v = value & mask
    if (v.testBit(bits - 1)) v - (BigInt(1) << bits) else v
  }

  /** Truncate a signed BigInt result to cEffW-bit two's complement (wrap-around), return unsigned. */
  private def truncToCEffW(value: BigInt): BigInt = {
    val mask = (BigInt(1) << cEffW) - 1
    value & mask
  }

  private def asSignedCEffW(raw: BigInt): BigInt = sext(raw, cEffW)

  /** INT8 golden: lane0 = a(7,0)*b(7,0), lane1 = a(15,8)*b(15,8), + cReg(signed).
    * Models Chisel SInt width semantics: r_i are SInt(16.W); the left-associative + chain
    * yields SInt(max(lhs,rhs).W) at each step (no width growth for SInt.+).
    * Steps: SInt(16)+SInt(16)->SInt(16), then SInt(16)+SInt(40)->SInt(40). */
  private def goldenInt8(a: Int, b: Int, cRegRaw: BigInt): BigInt = {
    val aLo = sext(BigInt(a & 0xFF), 8)
    val aHi = sext(BigInt((a >> 8) & 0xFF), 8)
    val bLo = sext(BigInt(b & 0xFF), 8)
    val bHi = sext(BigInt((b >> 8) & 0xFF), 8)
    val r0 = aLo * bLo   // always fits SInt(16)
    val r1 = aHi * bHi   // always fits SInt(16)
    val r2 = BigInt(0)   // INT8 mode: lanes 2,3 gated to zero
    val r3 = BigInt(0)
    // Left-associative addition with SInt(16.W) intermediate truncation
    val s1 = truncSInt(r0 + r1, 16)
    val s2 = truncSInt(s1 + r2, 16)
    val s3 = truncSInt(s2 + r3, 16)
    // Final: SInt(16) + SInt(40) -> SInt(40)
    val cSigned = asSignedCEffW(cRegRaw)
    val sumFull = truncSInt(s3 + cSigned, cEffW)
    truncToCEffW(sumFull)
  }

  /** INT4 golden: 4 nibbles each sext(4->8) then multiply, sum + cReg(signed).
    * Same Chisel SInt width semantics: r_i SInt(16.W), left-assoc + stays at SInt(16.W),
    * final + cReg expands to SInt(40.W). (INT4 products max 64, sum max 256 -- no 16-bit overflow.) */
  private def goldenInt4(a: Int, b: Int, cRegRaw: BigInt): BigInt = {
    val r = (0 until 4).map { i =>
      val nibA = sext(BigInt((a >> (4 * i)) & 0xF), 4)
      val nibB = sext(BigInt((b >> (4 * i)) & 0xF), 4)
      nibA * nibB
    }
    val s1 = truncSInt(r(0) + r(1), 16)
    val s2 = truncSInt(s1 + r(2), 16)
    val s3 = truncSInt(s2 + r(3), 16)
    val cSigned = asSignedCEffW(cRegRaw)
    val sumFull = truncSInt(s3 + cSigned, cEffW)
    truncToCEffW(sumFull)
  }

  private def toU8(v: Int): Int = v & 0xFF

  /** Pack two int8 values into a 16-bit lane: lane0=lo, lane1=hi. */
  private def pack8(lo: Int, hi: Int): Int = toU8(lo) | (toU8(hi) << 8)

  private def toU4(v: Int): Int = v & 0xF

  /** Pack four int4 values into a 16-bit lane. */
  private def pack4(n0: Int, n1: Int, n2: Int, n3: Int): Int =
    toU4(n0) | (toU4(n1) << 4) | (toU4(n2) << 8) | (toU4(n3) << 12)

  private def cRegPoke(signedVal: BigInt): BigInt = truncToCEffW(signedVal)

  behavior of "WbFusionMacInt INT8 dual-lane"

  it should "compute INT8 dual-lane MAC correctly for boundary and random vectors" in {
    val rng = new Random(42)

    // Structured boundary vectors: (aLo, aHi, bLo, bHi, cRegSigned)
    val boundaryVecs: Seq[(Int, Int, Int, Int, BigInt)] = Seq(
      (127, 127, 127, 127, BigInt(0)),        // max positive
      (-128, -128, -128, -128, BigInt(0)),     // max negative * max negative
      (-128, 127, 127, -128, BigInt(0)),       // cross signs
      (0, 0, 0, 0, BigInt(0)),                 // all zero
      (1, 1, 1, 1, BigInt(0)),                 // identity
      (-1, -1, -1, -1, BigInt(0)),             // (-1)*(-1) = 1
      (127, -128, -128, 127, BigInt(0)),       // mixed extremes
      (100, -50, 60, -30, BigInt(0)),          // arbitrary
      (0, 127, 0, -128, BigInt(0)),            // one lane zero
      (-128, 0, 127, 0, BigInt(0)),            // other lane zero
      // cReg non-zero tests
      (10, 20, 30, 40, BigInt(1000)),          // positive accumulate
      (-10, -20, -30, -40, BigInt(-1000)),     // negative accumulate
      (127, 127, 127, 127, BigInt("549755813887")), // near max cEffW
      (50, -50, 50, -50, BigInt(-2500)),       // cancel out
      (1, 2, 3, 4, BigInt(999999))             // large cReg
    )

    // Generate random vectors to reach >= 20 total
    val randomVecs: Seq[(Int, Int, Int, Int, BigInt)] = (0 until 10).map { _ =>
      val aLo = rng.nextInt(256) - 128
      val aHi = rng.nextInt(256) - 128
      val bLo = rng.nextInt(256) - 128
      val bHi = rng.nextInt(256) - 128
      val cVal = BigInt(rng.nextInt(2000001)) - BigInt(1000000)
      (aLo, aHi, bLo, bHi, cVal)
    }

    val allVecs = boundaryVecs ++ randomVecs
    assert(allVecs.size >= 20, s"Need >= 20 vectors, got ${allVecs.size}")

    simulate(new WbFusionMacInt()) { dut =>
      // Reset
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.INT8)
      dut.io.bFmt.poke(DataFormat.INT8)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      var pass = 0
      for (((aLo, aHi, bLo, bHi, cSigned), idx) <- allVecs.zipWithIndex) {
        val a = pack8(aLo, aHi)
        val b = pack8(bLo, bHi)
        val cRaw = cRegPoke(cSigned)

        dut.io.aFmt.poke(DataFormat.INT8)
        dut.io.bFmt.poke(DataFormat.INT8)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.cReg.poke(cRaw.U)
        dut.clock.step(1)

        val actual = dut.io.sum.peek().litValue
        val expected = goldenInt8(a, b, cRaw)
        assert(actual == expected,
          f"[INT8 vec#$idx] FAIL: a=0x$a%04X b=0x$b%04X cReg=$cSigned " +
            f"expected=0x${expected.toString(16)} actual=0x${actual.toString(16)}")
        pass += 1
      }
      println(s"[WbFusionMacIntSpec] INT8 dual-lane PASS $pass/${allVecs.size}")
    }
  }

  behavior of "WbFusionMacInt INT4 quad-lane"

  it should "compute INT4 quad-lane MAC correctly for boundary and random vectors" in {
    val rng = new Random(123)

    // Structured boundary vectors: (n0a,n1a,n2a,n3a, n0b,n1b,n2b,n3b, cRegSigned)
    val boundaryVecs: Seq[(Seq[Int], Seq[Int], BigInt)] = Seq(
      (Seq(7, 7, 7, 7), Seq(7, 7, 7, 7), BigInt(0)),         // max positive
      (Seq(-8, -8, -8, -8), Seq(-8, -8, -8, -8), BigInt(0)),  // max neg * max neg
      (Seq(-8, 7, -8, 7), Seq(7, -8, 7, -8), BigInt(0)),      // cross signs
      (Seq(0, 0, 0, 0), Seq(0, 0, 0, 0), BigInt(0)),          // all zero
      (Seq(1, -1, 1, -1), Seq(1, -1, 1, -1), BigInt(0)),      // identity-like
      (Seq(-8, -8, 7, 7), Seq(7, 7, -8, -8), BigInt(0)),      // mixed extremes
      (Seq(3, -4, 5, -6), Seq(2, -3, 4, -5), BigInt(0)),      // arbitrary
      (Seq(0, 7, -8, 1), Seq(0, -8, 7, 1), BigInt(0)),        // partial zero
      // cReg non-zero
      (Seq(7, 7, 7, 7), Seq(7, 7, 7, 7), BigInt(5000)),        // positive acc
      (Seq(-8, -8, -8, -8), Seq(-8, -8, -8, -8), BigInt(-5000)), // negative acc
      (Seq(5, -3, 2, -7), Seq(4, -6, 1, -2), BigInt(123456)), // large cReg
      (Seq(1, 2, 3, 4), Seq(-1, -2, -3, -4), BigInt(0)),       // cancel products
      (Seq(-8, 0, 0, 0), Seq(7, 0, 0, 0), BigInt(0)),          // single lane active
      (Seq(0, 0, 0, -8), Seq(0, 0, 0, 7), BigInt(99)),         // single lane + cReg
      (Seq(7, -8, 7, -8), Seq(-8, 7, -8, 7), BigInt(-1000000)) // extremes + neg cReg
    )

    // Random vectors to reach >= 20
    val randomVecs: Seq[(Seq[Int], Seq[Int], BigInt)] = (0 until 10).map { _ =>
      val aNibs = Seq.fill(4)(rng.nextInt(16) - 8)
      val bNibs = Seq.fill(4)(rng.nextInt(16) - 8)
      val cVal = BigInt(rng.nextInt(200001)) - BigInt(100000)
      (aNibs, bNibs, cVal)
    }

    val allVecs = boundaryVecs ++ randomVecs
    assert(allVecs.size >= 20, s"Need >= 20 vectors, got ${allVecs.size}")

    simulate(new WbFusionMacInt()) { dut =>
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.INT4)
      dut.io.bFmt.poke(DataFormat.INT4)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      var pass = 0
      for (((aNibs, bNibs, cSigned), idx) <- allVecs.zipWithIndex) {
        val a = pack4(aNibs(0), aNibs(1), aNibs(2), aNibs(3))
        val b = pack4(bNibs(0), bNibs(1), bNibs(2), bNibs(3))
        val cRaw = cRegPoke(cSigned)

        dut.io.aFmt.poke(DataFormat.INT4)
        dut.io.bFmt.poke(DataFormat.INT4)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.cReg.poke(cRaw.U)
        dut.clock.step(1)

        val actual = dut.io.sum.peek().litValue
        val expected = goldenInt4(a, b, cRaw)
        assert(actual == expected,
          f"[INT4 vec#$idx] FAIL: a=0x$a%04X b=0x$b%04X cReg=$cSigned " +
            f"expected=0x${expected.toString(16)} actual=0x${actual.toString(16)}")
        pass += 1
      }
      println(s"[WbFusionMacIntSpec] INT4 quad-lane PASS $pass/${allVecs.size}")
    }
  }

  behavior of "WbFusionMacInt truncation wrap-around"

  it should "wrap around correctly when result overflows cEffW=40 bits" in {
    // 2^39 - 1 = max positive for 40-bit signed
    val maxPos40 = (BigInt(1) << 39) - 1
    // -2^39 = min negative for 40-bit signed
    val minNeg40 = -(BigInt(1) << 39)

    val wrapVecs: Seq[(Int, Int, BigInt, String)] = Seq(
      // INT8: cReg near max, large positive product causes overflow
      (pack8(127, 127), pack8(127, 127), cRegPoke(maxPos40), "int8_overflow_pos"),
      // INT8: cReg near min, large negative product causes underflow
      (pack8(-128, -128), pack8(127, 127), cRegPoke(minNeg40), "int8_overflow_neg"),
      // INT8: cReg = max, product = 1 => wraps to min+1
      (pack8(1, 0), pack8(1, 0), cRegPoke(maxPos40), "int8_wrap_to_min"),
      // INT8: cReg = min, product = -1 => wraps to max
      (pack8(-1, 0), pack8(1, 0), cRegPoke(minNeg40), "int8_wrap_to_max")
    )

    simulate(new WbFusionMacInt()) { dut =>
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.INT8)
      dut.io.bFmt.poke(DataFormat.INT8)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      for ((a, b, cRaw, tag) <- wrapVecs) {
        dut.io.aFmt.poke(DataFormat.INT8)
        dut.io.bFmt.poke(DataFormat.INT8)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.cReg.poke(cRaw.U)
        dut.clock.step(1)

        val actual = dut.io.sum.peek().litValue
        val expected = goldenInt8(a, b, cRaw)
        assert(actual == expected,
          f"[wrap $tag] FAIL: a=0x${a.toHexString} b=0x${b.toHexString} " +
            f"expected=0x${expected.toString(16)} actual=0x${actual.toString(16)}")
        println(f"[WbFusionMacIntSpec] wrap $tag PASS: sum=0x${actual.toString(16)}")
      }
    }

    // INT4 overflow test in separate simulate block
    val wrapVecs4: Seq[(Int, Int, BigInt, String)] = Seq(
      (pack4(7, 7, 7, 7), pack4(7, 7, 7, 7), cRegPoke(maxPos40), "int4_overflow_pos"),
      (pack4(-8, -8, -8, -8), pack4(-8, -8, -8, -8), cRegPoke(maxPos40), "int4_overflow_pos2"),
      (pack4(-8, -8, -8, -8), pack4(7, 7, 7, 7), cRegPoke(minNeg40), "int4_overflow_neg")
    )

    simulate(new WbFusionMacInt()) { dut =>
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.INT4)
      dut.io.bFmt.poke(DataFormat.INT4)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      for ((a, b, cRaw, tag) <- wrapVecs4) {
        dut.io.aFmt.poke(DataFormat.INT4)
        dut.io.bFmt.poke(DataFormat.INT4)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.cReg.poke(cRaw.U)
        dut.clock.step(1)

        val actual = dut.io.sum.peek().litValue
        val expected = goldenInt4(a, b, cRaw)
        assert(actual == expected,
          f"[wrap $tag] FAIL: a=0x${a.toHexString} b=0x${b.toHexString} " +
            f"expected=0x${expected.toString(16)} actual=0x${actual.toString(16)}")
        println(f"[WbFusionMacIntSpec] wrap $tag PASS: sum=0x${actual.toString(16)}")
      }
    }
  }

  behavior of "WbFusionMacInt require guards"

  it should "REJECT empty supportedFmts at elaboration time" in {
    val ex = intercept[IllegalArgumentException] {
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacInt(16, 16, 40, Set.empty)
      )
    }
    ex.getMessage should include("at least one INT format required")
    println(s"[WbFusionMacIntSpec] require guard (empty fmts) PASS: ${ex.getMessage.take(80)}")
  }

  it should "REJECT aW < 16 at elaboration time" in {
    val ex = intercept[IllegalArgumentException] {
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacInt(8, 16, 40, Set(DataFormat.INT8))
      )
    }
    ex.getMessage should include("16-bit operands")
    println(s"[WbFusionMacIntSpec] require guard (aW<16) PASS: ${ex.getMessage.take(80)}")
  }

  it should "REJECT bW < 16 at elaboration time" in {
    val ex = intercept[IllegalArgumentException] {
      _root_.circt.stage.ChiselStage.emitSystemVerilog(
        new WbFusionMacInt(16, 8, 40, Set(DataFormat.INT4))
      )
    }
    ex.getMessage should include("16-bit operands")
    println(s"[WbFusionMacIntSpec] require guard (bW<16) PASS: ${ex.getMessage.take(80)}")
  }

  behavior of "WbFusionMacInt single-format configurations"

  it should "work correctly with INT8-only configuration (supportedFmts = Set(INT8))" in {
    val rng = new Random(77)
    val vecs = (0 until 10).map { _ =>
      val aLo = rng.nextInt(256) - 128
      val aHi = rng.nextInt(256) - 128
      val bLo = rng.nextInt(256) - 128
      val bHi = rng.nextInt(256) - 128
      val cVal = BigInt(rng.nextInt(100001)) - BigInt(50000)
      (pack8(aLo, aHi), pack8(bLo, bHi), cRegPoke(cVal))
    }

    simulate(new WbFusionMacInt(supportedFmts = Set(DataFormat.INT8))) { dut =>
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.INT8)
      dut.io.bFmt.poke(DataFormat.INT8)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      var pass = 0
      for ((a, b, cRaw) <- vecs) {
        dut.io.aFmt.poke(DataFormat.INT8)
        dut.io.bFmt.poke(DataFormat.INT8)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.cReg.poke(cRaw.U)
        dut.clock.step(1)

        val actual = dut.io.sum.peek().litValue
        val expected = goldenInt8(a, b, cRaw)
        assert(actual == expected,
          f"[INT8-only] FAIL: a=0x${a.toHexString} b=0x${b.toHexString} " +
            f"expected=0x${expected.toString(16)} actual=0x${actual.toString(16)}")
        pass += 1
      }
      println(s"[WbFusionMacIntSpec] INT8-only config PASS $pass/${vecs.size}")
    }
  }

  it should "work correctly with INT4-only configuration (supportedFmts = Set(INT4))" in {
    val rng = new Random(88)
    val vecs = (0 until 10).map { _ =>
      val aNibs = Seq.fill(4)(rng.nextInt(16) - 8)
      val bNibs = Seq.fill(4)(rng.nextInt(16) - 8)
      val cVal = BigInt(rng.nextInt(100001)) - BigInt(50000)
      (pack4(aNibs(0), aNibs(1), aNibs(2), aNibs(3)),
       pack4(bNibs(0), bNibs(1), bNibs(2), bNibs(3)),
       cRegPoke(cVal))
    }

    simulate(new WbFusionMacInt(supportedFmts = Set(DataFormat.INT4))) { dut =>
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.INT4)
      dut.io.bFmt.poke(DataFormat.INT4)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      var pass = 0
      for ((a, b, cRaw) <- vecs) {
        dut.io.aFmt.poke(DataFormat.INT4)
        dut.io.bFmt.poke(DataFormat.INT4)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.cReg.poke(cRaw.U)
        dut.clock.step(1)

        val actual = dut.io.sum.peek().litValue
        val expected = goldenInt4(a, b, cRaw)
        assert(actual == expected,
          f"[INT4-only] FAIL: a=0x${a.toHexString} b=0x${b.toHexString} " +
            f"expected=0x${expected.toString(16)} actual=0x${actual.toString(16)}")
        pass += 1
      }
      println(s"[WbFusionMacIntSpec] INT4-only config PASS $pass/${vecs.size}")
    }
  }
}
