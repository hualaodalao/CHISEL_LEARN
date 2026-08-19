import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.lang.Float.{floatToIntBits, intBitsToFloat}

/** Wrapper Module for testing Fp32.add (which is a def, not a Module) */
class Fp32AddWrapper extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(32.W))
    val b   = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })
  io.out := Fp32.add(io.a, io.b)
}

class Fp32Test extends AnyFlatSpec with Matchers {

  behavior of "Fp32.add (Far/Near dual-path)"

  /** Helper: convert Float to UInt-compatible BigInt */
  private def f2b(f: Float): BigInt = BigInt(floatToIntBits(f).toLong & 0xFFFFFFFFL)

  /** Helper: convert BigInt (UInt bits) to Float */
  private def b2f(b: BigInt): Float = intBitsToFloat(b.toInt)

  /** Run a single add test case, return (expected, actual) */
  private def testAdd(a: Float, b: Float, tolerance: Float = 0.0f): (Float, Float) = {
    var result: BigInt = BigInt(0)
    simulate(new Fp32AddWrapper) { dut =>
      dut.io.a.poke(f2b(a).U)
      dut.io.b.poke(f2b(b).U)
      dut.clock.step(1)
      result = dut.io.out.peek().litValue
    }
    val expected = a + b
    val actual = b2f(result)
    (expected, actual)
  }

  // ==================== 同号加法（正+正）Far path ====================
  it should "compute 1.0 + 1.0 = 2.0 (same sign, far path)" in {
    val (expected, actual) = testAdd(1.0f, 1.0f)
    actual shouldBe expected
  }

  it should "compute 1.5 + 0.5 = 2.0 (same sign, near path)" in {
    val (expected, actual) = testAdd(1.5f, 0.5f)
    actual shouldBe expected
  }

  it should "compute 100.0 + 200.0 = 300.0 (same sign, far path)" in {
    val (expected, actual) = testAdd(100.0f, 200.0f)
    actual shouldBe expected
  }

  // ==================== 异号减法（正+负）====================
  it should "compute 3.0 + (-1.0) = 2.0 (different sign, far path)" in {
    val (expected, actual) = testAdd(3.0f, -1.0f)
    actual shouldBe expected
  }

  it should "compute 5.5 + (-2.25) = 3.25 (different sign, far path)" in {
    val (expected, actual) = testAdd(5.5f, -2.25f)
    actual shouldBe expected
  }

  // ==================== 大 cancellation ====================
  it should "compute 1.0 + (-1.0) = 0.0 (full cancellation)" in {
    val (expected, actual) = testAdd(1.0f, -1.0f)
    actual shouldBe 0.0f
  }

  it should "compute -7.0 + 7.0 = 0.0 (full cancellation, reversed)" in {
    val (expected, actual) = testAdd(-7.0f, 7.0f)
    actual shouldBe 0.0f
  }

  it should "compute 1.0 + (-0.9375) near cancellation (near path)" in {
    val (expected, actual) = testAdd(1.0f, -0.9375f)
    actual shouldBe expected
  }

  // ==================== 零操作数 ====================
  it should "compute 0.0 + 3.14 = 3.14 (zero + x = x)" in {
    val (expected, actual) = testAdd(0.0f, 3.14f)
    actual shouldBe expected
  }

  it should "compute 3.14 + 0.0 = 3.14 (x + zero = x)" in {
    val (expected, actual) = testAdd(3.14f, 0.0f)
    actual shouldBe expected
  }

  it should "compute 0.0 + 0.0 = 0.0 (zero + zero)" in {
    val (expected, actual) = testAdd(0.0f, 0.0f)
    actual shouldBe 0.0f
  }

  // ==================== 大指数差（Far path 验证）====================
  it should "compute 1.0 + 2^-20 (large exp diff, far path)" in {
    val small = Math.pow(2.0, -20.0).toFloat  // ~9.5e-7
    val (expected, actual) = testAdd(1.0f, small)
    // 由于精度限制，结果应接近 1.0（或恰好 1.0 + eps）
    val err = Math.abs(actual - expected)
    err should be <= Math.ulp(1.0f)
  }

  it should "compute 1024.0 + 0.001 (large exp diff, far path)" in {
    val (expected, actual) = testAdd(1024.0f, 0.001f)
    val err = Math.abs(actual - expected)
    err should be <= Math.ulp(1024.0f)
  }

  // ==================== 小指数差（Near path 验证）====================
  it should "compute 1.0 + (-0.5) = 0.5 (expDiff=1, near path subtraction)" in {
    val (expected, actual) = testAdd(1.0f, -0.5f)
    actual shouldBe expected
  }

  it should "compute 2.0 + (-1.0) = 1.0 (expDiff=1, near path subtraction)" in {
    val (expected, actual) = testAdd(2.0f, -1.0f)
    actual shouldBe expected
  }

  it should "compute 1.0 + 0.75 = 1.75 (expDiff=0, near path addition)" in {
    val (expected, actual) = testAdd(1.0f, 0.75f)
    actual shouldBe expected
  }

  it should "compute 1.25 + 1.5 = 2.75 (expDiff=0, near path addition with carry)" in {
    val (expected, actual) = testAdd(1.25f, 1.5f)
    actual shouldBe expected
  }

  // ==================== 负数结果 ====================
  it should "compute -3.0 + 1.0 = -2.0 (negative result)" in {
    val (expected, actual) = testAdd(-3.0f, 1.0f)
    actual shouldBe expected
  }

  it should "compute -2.5 + (-1.5) = -4.0 (both negative)" in {
    val (expected, actual) = testAdd(-2.5f, -1.5f)
    actual shouldBe expected
  }
}
