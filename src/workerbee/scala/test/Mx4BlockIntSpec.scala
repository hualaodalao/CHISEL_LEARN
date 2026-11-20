/** Mx4BlockIntSpec — MXFP4 块内整数前端穷举金锚
  *
  * 金锚由张量语义独立推导：elem_n 解码 → 精确整数积和 s = Σ±n_a·n_b → 按 lazy-40 radix
  *   打包（mag12=|4s|、exp=eSum−120）。禁止以 RTL 的 P<<sh 行为拟合。
  * 纯组合模块：poke 后直接 peek，无需 step。
  * 覆盖：穷举 256 单 lane nibble 对 × scale 域 + 多 lane 块和（结构化/随机）+ 边界域显式锚。
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class Mx4BlockIntSpec extends AnyFlatSpec with Matchers with ChiselSim {

  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  /** E2M1 元素归一化整数 n = value/2^(scale−128)：e2=0 → n=m（subnormal/zero）；否则 (2+m)·2^(e2−1)。 */
  private def elemN(code: Int): BigInt = {
    val e2 = (code >> 1) & 3
    val m = code & 1
    if (e2 == 0) BigInt(m) else BigInt(2 + m) << (e2 - 1)
  }

  /** 张量语义金锚：返回期望 lazy-40 码（40-bit 无符号 BigInt）。 */
  private def goldenBlk(a: Seq[Int], b: Seq[Int], sa: Int, sb: Int): BigInt = {
    var s = BigInt(0)
    for (i <- 0 until 4) {
      val aa = a(i); val bb = b(i)
      val e2a = (aa >> 1) & 3; val e2b = (bb >> 1) & 3
      if ((e2a + sa) >= 2 && (e2b + sb) >= 2) {   // underflow 冲刷（与元素保号零一致）
        val term = elemN(aa) * elemN(bb)
        val sgn = ((aa >> 3) ^ (bb >> 3)) & 1
        s += (if (sgn == 1) -term else term)
      }
    }
    val eSum = sa + sb
    val eRaw = eSum - 120
    val tiny = eSum < 121
    val ovf = eRaw > 254
    val sgn = if (s < 0) 1 else 0
    val mag12 = s.abs * 4                          // |bsum| = |4s| ≤ 2304 < 4096
    if (tiny || s == 0) BigInt(0)
    else if (ovf) (BigInt(sgn) << 39) | (BigInt(254) << 31) | BigInt(0x7FFFFF80)
    else (BigInt(sgn) << 39) | (BigInt(eRaw & 0xFF) << 31) | (mag12 << 19)
  }

  private def packQuad(n: Seq[Int]): Int =
    (n(0) & 0xF) | ((n(1) & 0xF) << 4) | ((n(2) & 0xF) << 8) | ((n(3) & 0xF) << 12)

  private val scales = Seq(0, 1, 2, 120, 121, 128, 200, 253, 254)

  behavior of "Mx4BlockInt exhaustive single-lane nibble pairs"

  it should "match tensor golden for all 256 nibble pairs across scale domains" in {
    var checked = 0
    simulate(new Mx4BlockInt()) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      for (a0 <- 0 until 16; b0 <- 0 until 16; sa <- scales; sb <- scales) {
        val a = Seq(a0, 0, 0, 0); val b = Seq(b0, 0, 0, 0)
        dut.io.a.poke(packQuad(a).U)
        dut.io.b.poke(packQuad(b).U)
        dut.io.scaleA.poke(sa.U)
        dut.io.scaleB.poke(sb.U)
        val actual = dut.io.blk.peek().litValue
        val expected = goldenBlk(a, b, sa, sb)
        assert(actual == expected,
          s"[单lane] 失配 a0=$a0 b0=$b0 sa=$sa sb=$sb 期望=0x${expected.toString(16)} 实际=0x${actual.toString(16)}")
        checked += 1
      }
    }
    println(s"[Mx4BlockIntSpec] 穷举单lane PASS $checked 例")
    assert(checked == 256 * scales.size * scales.size)
  }

  behavior of "Mx4BlockInt multi-lane block sum"

  it should "match tensor golden for structured and random full quads" in {
    val rng = new Random(20261120)
    val structured: Seq[Seq[Int]] = Seq(
      Seq(0, 0, 0, 0), Seq(7, 7, 7, 7), Seq(0xF, 0xF, 0xF, 0xF),
      Seq(1, 1, 1, 1), Seq(0xA, 0x1, 0xE, 0xA), Seq(0x3, 0x5, 0x9, 0xB),
      Seq(0x2, 0x4, 0x6, 0x8), Seq(7, 0xF, 1, 0), Seq(0xF, 7, 0xF, 7),
      Seq(1, 2, 3, 4), Seq(0xC, 0xC, 0xC, 0xC), Seq(7, 1, 0xF, 2)
    )
    var checked = 0
    simulate(new Mx4BlockInt()) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      def check(a: Seq[Int], b: Seq[Int], sa: Int, sb: Int, tag: String): Unit = {
        dut.io.a.poke(packQuad(a).U)
        dut.io.b.poke(packQuad(b).U)
        dut.io.scaleA.poke(sa.U)
        dut.io.scaleB.poke(sb.U)
        val actual = dut.io.blk.peek().litValue
        val expected = goldenBlk(a, b, sa, sb)
        assert(actual == expected,
          s"[$tag] 失配 a=$a b=$b sa=$sa sb=$sb 期望=0x${expected.toString(16)} 实际=0x${actual.toString(16)}")
        checked += 1
      }

      for (a <- structured; b <- structured; sa <- scales; sb <- scales)
        check(a, b, sa, sb, "结构化")
      for (_ <- 0 until 800) {
        val a = Seq.fill(4)(rng.nextInt(16))
        val b = Seq.fill(4)(rng.nextInt(16))
        val sa = rng.nextInt(255); val sb = rng.nextInt(255)
        check(a, b, sa, sb, "随机")
      }
    }
    println(s"[Mx4BlockIntSpec] 多lane块和 PASS $checked 例")
  }

  behavior of "Mx4BlockInt boundary domain anchors"

  it should "saturate to fp32max on overflow, flush true zero on tiny/underflow" in {
    simulate(new Mx4BlockInt()) { dut =>
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      def run(a: Seq[Int], b: Seq[Int], sa: Int, sb: Int): BigInt = {
        dut.io.a.poke(packQuad(a).U)
        dut.io.b.poke(packQuad(b).U)
        dut.io.scaleA.poke(sa.U)
        dut.io.scaleB.poke(sb.U)
        dut.io.blk.peek().litValue
      }

      // 溢出：n=12 四 lane 同号，eSum=400 ⟹ eRaw=280>254 ⟹ +fp32max 码 {0,254,0x7FFFFF80}
      val ovfPos = run(Seq(7, 7, 7, 7), Seq(7, 7, 7, 7), 200, 200)
      assert(ovfPos == ((BigInt(254) << 31) | BigInt(0x7FFFFF80)),
        s"溢出正饱和码错 0x${ovfPos.toString(16)}")
      // 溢出负：a 全 0xF（sign1,n=12）× b 全 0x7（sign0,n=12）⟹ 负 fp32max
      val ovfNeg = run(Seq(0xF, 0xF, 0xF, 0xF), Seq(7, 7, 7, 7), 254, 254)
      assert(ovfNeg == ((BigInt(1) << 39) | (BigInt(254) << 31) | BigInt(0x7FFFFF80)),
        s"溢出负饱和码错 0x${ovfNeg.toString(16)}")
      // tiny：eSum=120<121 ⟹ 真零
      assert(run(Seq(7, 7, 7, 7), Seq(7, 7, 7, 7), 60, 60) == BigInt(0), "tiny 域应真零")
      // underflow：sa=0，e2=1 元素 e2+sa=1<2 ⟹ 冲刷
      assert(run(Seq(2, 0, 0, 0), Seq(2, 0, 0, 0), 0, 128) == BigInt(0), "underflow 应冲刷真零")
      // subnormal：a0=b0=0x1（e2=0,m=1,n=1），sa=sb=128 ⟹ s=1，非零
      val sub = run(Seq(1, 0, 0, 0), Seq(1, 0, 0, 0), 128, 128)
      assert(sub == goldenBlk(Seq(1, 0, 0, 0), Seq(1, 0, 0, 0), 128, 128) && sub != BigInt(0),
        s"subnormal n=1 应非零 0x${sub.toString(16)}")
      // 全零块
      assert(run(Seq(0, 0, 0, 0), Seq(7, 7, 7, 7), 128, 128) == BigInt(0), "全零块应真零")
      // 完全相消：lane0 正积 + lane1 负积等幅 ⟹ s=0 真零
      val cancel = run(Seq(7, 0xF, 0, 0), Seq(7, 7, 0, 0), 128, 128)
      assert(cancel == BigInt(0), s"完全相消应真零 0x${cancel.toString(16)}")
      println("[Mx4BlockIntSpec] 边界域锚 PASS（溢出±fp32max / tiny / underflow / subnormal / 全零 / 相消）")
    }
  }
}