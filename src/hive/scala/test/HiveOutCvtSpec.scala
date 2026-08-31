/** HiveOutCvt 单元测试（P2：精度转换模块对拍软件参考模型）。
  *
  * 覆盖维度：
  *  - 位模式向量：±0、±1、1.5、-2.75、3.14159、1234.5678、0.001、65504/65520
  *    （饱和边界）、100000/-70000（溢出饱和）、2^-14 附近（下溢边界）、
  *    3e-5/1e-7（FTZ）、tie 舍入构造值、±inf、±NaN；
  *  - 5 种舍入模式 ×（fp16 / bf16）逐位对拍 HiveSimCommon.swFp32ToFp16/swFp32ToBf16；
  *  - packed32 对拍 HiveSimCommon.swFpNormalize40to32（fp32 通路与现状内联
  *    规格化语义一致性）。
  *
  * 输入构造：fp32 bit → fp32BitsTo40 → 40-bit 槽（与硬件累加器落槽格式一致），
  * 按 totalN 个一组 chunk poke lineIn。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.lang.Float.{floatToIntBits, intBitsToFloat}

// 后端选择留痕：不用 EphemeralSimulator——当前环境（Chisel 7.13 + Verilator 5.050）下
// EphemeralSimulator 默认路径无法产出仿真二进制（simulation-log 缺失），
// 改用与 HiveCoreSimCase/LoadWSpec 同源的 ChiselSim + HiveFstSim（verilator 显式配置，
// 已验证可跑）。
class HiveOutCvtSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  // 测试用小配置：arrayN=8×clusterM=2 → totalN=16；cEffW 自动推导为 40（含浮点格式）
  private val cfg = HiveCoreConfig(arrayN = 8, clusterM = 2)

  behavior of "HiveOutCvt (fp32 -> fp16/bf16/fp32 密集行转换)"

  /** fp32 位模式向量：覆盖正/负/零、饱和、次正规边界、inf/nan、tie 舍入 */
  private def testVectors: Seq[Long] = {
    val floats: Seq[Float] = Seq(
      0.0f, -0.0f, 1.0f, -1.0f, 1.5f, -2.75f, 3.14159f, 1234.5678f,
      0.001f, -0.001f, 65504.0f, -65504.0f, 65520.0f, 100000.0f, -70000.0f,
      math.pow(2.0, -14).toFloat,          // fp16 最小正规格化 2^-14
      (1.0 + 0.5 / 1024).toFloat * math.pow(2.0, -14).toFloat, // 2^-14 附近 tie
      3e-5f, -3e-5f, 1e-7f,                // 下溢 FTZ
      Float.PositiveInfinity, Float.NegativeInfinity,
      Float.NaN, -Float.NaN,
      // tie 舍入构造：尾数低 13 位恰为 1000...0（guard=1,sticky=0）
      intBitsToFloat(0x3F801000),          // 1.0 + guard tie（lsb=0 → RNE 截断）
      intBitsToFloat(0x3F803000),          // 1.0 + guard+sticky（RNE 进位）
      intBitsToFloat(0x3F802000)           // guard=1 且保留位含 1（lsb 影响）
    )
    floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
  }

  /** rnd 编码 → 枚举字面量（与 RoundingMode ChiselEnum 顺序同源） */
  private val rndLits: Seq[RoundingMode.Type] =
    Seq(RoundingMode.RNE, RoundingMode.RTZ, RoundingMode.RDN, RoundingMode.RUP, RoundingMode.RMM)

  /** 单轮对拍：给定 outFmt（1=fp16，2=bf16）与 rnd（0..4），逐位对拍 */
  private def checkPacked16(outFmt: Int, rnd: Int): Unit = {
    val vec = testVectors
    // 按 totalN 分组，最后一组补 0 值槽凑齐
    val padded = vec ++ Seq.fill((cfg.totalN - vec.size % cfg.totalN) % cfg.totalN)(0L)
    val chunks = padded.grouped(cfg.totalN).toSeq
    var chunkIdx = 0
    simulate(new HiveOutCvt(cfg)) { dut =>
      for (chunk <- chunks) {
        // 构造 lineIn：元素 0 在低位（槽 0 = bit[0..39]）
        var line = BigInt(0)
        for ((bits, i) <- chunk.zipWithIndex) {
          line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * i)
        }
        dut.io.lineIn.poke(line.U)
        dut.io.outFmt.poke(outFmt.U)
        dut.io.rnd.poke(rndLits(rnd))
        val got = dut.io.packed16.peek().litValue
        for ((bits, i) <- chunk.zipWithIndex) {
          // 硬件管线：40 位槽 → 40→32 规格化（固定简化 RNE，零值归绝对 0 不保号）
          // → 32→16（rnd 5 模式），期望值必须同管线重建，不能直取原始 fp32 bits
          val normBits = HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(bits))
          val expect =
            if (outFmt == 1) HiveSimCommon.swFp32ToFp16(normBits, rnd)
            else HiveSimCommon.swFp32ToBf16(normBits, rnd)
          val actual = ((got >> (16 * i)) & 0xFFFF).toInt
          withClue(s"chunk=$chunkIdx elem=$i val=${intBitsToFloat(bits.toInt)} rnd=$rnd: ") {
            actual shouldBe expect
          }
        }
        chunkIdx += 1
      }
    }
  }

  it should "fp16 通路与软件参考模型逐位一致（5 种舍入模式）" in {
    for (rnd <- 0 to 4) checkPacked16(outFmt = 1, rnd = rnd)
  }

  it should "bf16 通路与软件参考模型逐位一致（5 种舍入模式）" in {
    for (rnd <- 0 to 4) checkPacked16(outFmt = 2, rnd = rnd)
  }

  it should "fp32 通路（packed32）与 fpNormalize40to32 软件模型逐位一致" in {
    val vec = testVectors
    val padded = vec ++ Seq.fill((cfg.totalN - vec.size % cfg.totalN) % cfg.totalN)(0L)
    val chunks = padded.grouped(cfg.totalN).toSeq
    simulate(new HiveOutCvt(cfg)) { dut =>
      for (chunk <- chunks) {
        var line = BigInt(0)
        for ((bits, i) <- chunk.zipWithIndex) {
          line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * i)
        }
        dut.io.lineIn.poke(line.U)
        dut.io.outFmt.poke(0.U)
        dut.io.rnd.poke(RoundingMode.RNE)
        val got = dut.io.packed32.peek().litValue
        for ((bits, i) <- chunk.zipWithIndex) {
          val expect = HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(bits))
          val actual = (got >> (32 * i)) & 0xFFFFFFFFL
          withClue(s"elem=$i val=${intBitsToFloat(bits.toInt)}: ") {
            actual shouldBe expect
          }
        }
      }
    }
  }

  it should "饱和/溢出/FTZ 锚点值硬检查（fp16 RNE）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      def pokeOne(bits: Long): Int = {
        dut.io.lineIn.poke(HiveSimCommon.fp32BitsTo40(bits).U)
        dut.io.outFmt.poke(1.U)
        dut.io.rnd.poke(RoundingMode.RNE)
        (dut.io.packed16.peek().litValue & 0xFFFF).toInt
      }
      pokeOne(floatToIntBits(100000.0f).toLong & 0xFFFFFFFFL) shouldBe 0x7BFF   // +65504 饱和
      pokeOne(floatToIntBits(-70000.0f).toLong & 0xFFFFFFFFL) shouldBe 0xFBFF   // -65504 饱和
      pokeOne(floatToIntBits(3e-5f).toLong & 0xFFFFFFFFL) shouldBe 0x0000       // FTZ 正零
      pokeOne(floatToIntBits(-3e-5f).toLong & 0xFFFFFFFFL) shouldBe 0x8000      // FTZ 负零（保号）
      pokeOne(floatToIntBits(0.0f).toLong & 0xFFFFFFFFL) shouldBe 0x0000        // 正零
      pokeOne(floatToIntBits(Float.PositiveInfinity).toLong & 0xFFFFFFFFL) shouldBe 0x7C00
      pokeOne(floatToIntBits(Float.NaN).toLong & 0xFFFFFFFFL) shouldBe 0x7E00   // 规范 nan
    }
  }

  it should "fpNormalize40to32 舍入进位：尾数全 1 进位 → 指数 +1、尾数归零（独立期望值）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      dut.io.outFmt.poke(0.U)
      dut.io.rnd.poke(RoundingMode.RNE)
      def pokeRaw40(raw: BigInt): BigInt = {
        dut.io.lineIn.poke(raw.U)
        dut.io.packed32.peek().litValue & BigInt(0xFFFFFFFFL)
      }
      // 构造 m31=0x7FFFFFFF（隐含 1 + 尾数位全 1）：lzc=0，规格化后
      // mant23=0x7FFFFF 全 1、guard=1、sticky=1 → 舍入必进位。
      // 独立期望（不拄实现）：真值 ≈ (2-2^-30)×2^(e-127) 舍入后恰为 2^(e-126)，
      // 故输出指数 = e+1、尾数归零（旧实现丢进位时输出为 0.5×真值，即 e 不变 mant=0）。
      // 正：e=140 → 2^14 = 0x46800000（exp=141，mant=0）
      pokeRaw40((BigInt(140) << 31) | BigInt(0x7FFFFFFF)) shouldBe BigInt(0x46800000L)
      // 负：sign=1 → 0xC6800000
      pokeRaw40((BigInt(1) << 39) | (BigInt(140) << 31) | BigInt(0x7FFFFFFF)) shouldBe BigInt(0xC6800000L)
      // 反向控制组：尾数低 7 位全 0（guard=0/sticky=0 → 不进位），
      // mant23=0x7FFFFE 未进位 → 逐位透传：exp=140、mant=0x7FFFFE
      pokeRaw40((BigInt(140) << 31) | BigInt(0x7FFFFF00)) shouldBe BigInt(0x467FFFFEL)
    }
  }

  it should "RMM tie/非 tie 定向用例：仅 tie（guard=1，不论 sticky）进位（独立期望值）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      def pokeOne(bits: Long, outFmt: Int, rnd: RoundingMode.Type): Int = {
        dut.io.lineIn.poke(HiveSimCommon.fp32BitsTo40(bits).U)
        dut.io.outFmt.poke(outFmt.U)
        dut.io.rnd.poke(rnd)
        (dut.io.packed16.peek().litValue & 0xFFFF).toInt
      }
      // 构造值经 40→32 规格化无损（截断位均低于 bit7，guard/sticky 恒 0）。
      // fp16：保留 man(22,13)，guard=man(12)，sticky=man(11,0)
      val tie     = 0x3F801000L   // 1 + 2^-11：guard=1/sticky=0 → 恰为 tie（1.000488 介于 1.0 与 1+2^-10 正中）
      val nonTie1 = 0x3F801001L   // guard=1/sticky=1（非 tie，但 guard=1）
      val nonTie0 = 0x3F800001L   // guard=0/sticky=1（非 tie）
      // RMM（ties to Max Magnitude）：tie → 进位 1+2^-10=0x3C01；guard=1 亦进位；仅 sticky 不进位。
      // （旧实现 guard|sticky 会把 nonTie0 误进位为 0x3C01，本用例可鉴别）
      pokeOne(tie, 1, RoundingMode.RMM) shouldBe 0x3C01
      pokeOne(nonTie1, 1, RoundingMode.RMM) shouldBe 0x3C01
      pokeOne(nonTie0, 1, RoundingMode.RMM) shouldBe 0x3C00
      // 交叉对照（独立期望）：RNE 严格 tie-to-even（lsb=0 → 截断）；RTZ 恒截断；RUP 正数增大幅值
      pokeOne(tie, 1, RoundingMode.RNE) shouldBe 0x3C00
      pokeOne(tie, 1, RoundingMode.RTZ) shouldBe 0x3C00
      pokeOne(tie, 1, RoundingMode.RUP) shouldBe 0x3C01
      pokeOne(nonTie0, 1, RoundingMode.RUP) shouldBe 0x3C01
      // bf16：guard=x(15)，sticky=x(14,0)，lsb=x(16)；tie 构造 1 + 2^-8 = 0x3F808000
      val bfTie = 0x3F808000L
      pokeOne(bfTie, 2, RoundingMode.RMM) shouldBe 0x3F81   // tie 进位 → 1+2^-7
      pokeOne(bfTie, 2, RoundingMode.RNE) shouldBe 0x3F80   // lsb=0 tie-to-even 截断
      pokeOne(bfTie, 2, RoundingMode.RTZ) shouldBe 0x3F80
      pokeOne(bfTie, 2, RoundingMode.RUP) shouldBe 0x3F81
    }
  }
}
