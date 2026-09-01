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

  // ============================================================================
  // P3a 量化支路：5 种舍入 × {E4M3, E5M2, E2M1} × {blk=8, blk=16} 位级对拍 +
  // 饱和/全零块/FTZ/inf-nan/nEff 掩码锚点。期望值由 HiveSimCommon.swQuantBlock
  // 独立推导（同两级舍入链：40→32 固定简化 RNE + 量化运行期 rnd）。
  // ============================================================================

  behavior of "HiveOutCvt 量化支路 (P3a: fp32 → MX 块共享 scale 量化)"

  /** 随机 fp32 向量：幅值跨多个数量级（指数字段 105..140），混入零，
    * 保证块内 amax/scale 推导与舍入/截断/下溢各分支均被充分激励。 */
  private def randomQuantFloats(seed: Int): Seq[Float] = {
    val rng = new scala.util.Random(seed)
    (0 until cfg.totalN).map { _ =>
      rng.nextInt(12) match {
        case 0 => 0.0f
        case _ =>
          val e = rng.nextInt(36) + 105
          val bits = ((if (rng.nextBoolean()) 1L else 0L) << 31) | (e.toLong << 23) |
            (rng.nextInt(1 << 23).toLong & 0x7FFFFFL)
          intBitsToFloat(bits.toInt)
      }
    }
  }

  /** 单次 poke + 逐位对拍：元素码（packed8/packed4）+ scaleRow 全量比对。
    * fmt：4=E4M3 5=E5M2 6=E2M1；blkSel：0=8/1=16/2=32/3=64。 */
  private def pokeQuant(dut: HiveOutCvt, fmt: Int, rnd: Int, blkSel: Int, nEff: Int,
                        floats: Seq[Float], clueTag: String): Unit = {
    val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
    var line = BigInt(0)
    for ((bits, i) <- bitsSeq.zipWithIndex) {
      line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * i)
    }
    dut.io.lineIn.poke(line.U)
    dut.io.outFmt.poke(fmt.U)
    dut.io.blkSel.poke(blkSel.U)
    dut.io.nEff.poke(nEff.U)
    dut.io.rnd.poke(rndLits(rnd))
    // 同两级舍入链重建：40 位槽 → 40→32 固定简化 RNE 规格化 → 块量化期望
    val normSeq = bitsSeq.map(b => HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(b)))
    val (codes, scaleBytes) = HiveSimCommon.swQuantBlock(normSeq, rnd, fmt, blkSel, nEff)
    if (fmt == 6) {
      val got4 = dut.io.packed4.peek().litValue
      for (i <- 0 until cfg.totalN) {
        val actual = ((got4 >> (4 * i)) & 0xF).toInt
        withClue(s"$clueTag elem=$i val=${floats(i)}: ") { actual shouldBe codes(i) }
      }
    } else {
      val got8 = dut.io.packed8.peek().litValue
      for (i <- 0 until cfg.totalN) {
        val actual = ((got8 >> (8 * i)) & 0xFF).toInt
        withClue(s"$clueTag elem=$i val=${floats(i)}: ") { actual shouldBe codes(i) }
      }
    }
    val gotScale = dut.io.scaleRow.peek().litValue
    for (g <- 0 until cfg.totalN / 8) {
      val actual = ((gotScale >> (8 * g)) & 0xFF).toInt
      withClue(s"$clueTag scaleGroup=$g: ") { actual shouldBe scaleBytes(g) }
    }
  }

  it should "5 种舍入 × E4M3/E5M2/E2M1 × blk8/blk16 随机向量位级对拍" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      for (fmt <- Seq(4, 5, 6); rnd <- 0 to 4; blk <- Seq(0, 1)) {
        pokeQuant(dut, fmt, rnd, blk, cfg.totalN,
          randomQuantFloats(seed = fmt * 10000 + rnd * 100 + blk),
          clueTag = s"[fmt=$fmt rnd=$rnd blk=$blk]")
      }
    }
  }

  it should "饱和/舍入边界锚点：448/446 块（独立期望值，5 模式定向）" in {
    // 独立推导：块 amax=448（1.75×2^8，尾数恰等门限不提指数）→ scale=135+7-15=127。
    // 448 → (exp15,mant6)=0x7E 精确；446=1.742×2^8：mantTop=5、guard=1、sticky=1、
    // lsb=1 → RNE/RUP/RMM 进位 → 0x7E；RTZ/RDN 截断 → 0x7D。
    simulate(new HiveOutCvt(cfg)) { dut =>
      val floats = Seq(448.0f, 446.0f) ++ Seq.fill(cfg.totalN - 2)(0.0f)
      def codeAt(rnd: Int, i: Int): Int = {
        val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
        var line = BigInt(0)
        for ((bits, j) <- bitsSeq.zipWithIndex) line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * j)
        dut.io.lineIn.poke(line.U)
        dut.io.outFmt.poke(4.U)
        dut.io.blkSel.poke(0.U)
        dut.io.nEff.poke(cfg.totalN.U)
        dut.io.rnd.poke(rndLits(rnd))
        ((dut.io.packed8.peek().litValue >> (8 * i)) & 0xFF).toInt
      }
      def scaleAt(rnd: Int): Int = {
        val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
        var line = BigInt(0)
        for ((bits, j) <- bitsSeq.zipWithIndex) line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * j)
        dut.io.lineIn.poke(line.U)
        dut.io.outFmt.poke(4.U)
        dut.io.blkSel.poke(0.U)
        dut.io.nEff.poke(cfg.totalN.U)
        dut.io.rnd.poke(rndLits(rnd))
        (dut.io.scaleRow.peek().litValue & 0xFF).toInt
      }
      for (rnd <- 0 to 4) {
        withClue(s"scale rnd=$rnd: ") { scaleAt(rnd) shouldBe 127 }
        withClue(s"448 rnd=$rnd: ") { codeAt(rnd, 0) shouldBe 0x7E }
      }
      codeAt(0, 1) shouldBe 0x7E   // RNE：guard&sticky → 进位
      codeAt(1, 1) shouldBe 0x7D   // RTZ：恒截断
      codeAt(2, 1) shouldBe 0x7D   // RDN：正数不增大幅值
      codeAt(3, 1) shouldBe 0x7E   // RUP：正数增大幅值
      codeAt(4, 1) shouldBe 0x7E   // RMM：guard=1 → 进位
    }
  }

  it should "全零块哨兵/FTZ 边界/inf-nan 映射锚点（三格式）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      def runRow(floats: Seq[Float], fmt: Int, blkSel: Int, rnd: Int): (BigInt, BigInt) = {
        val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
        var line = BigInt(0)
        for ((bits, j) <- bitsSeq.zipWithIndex) line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * j)
        dut.io.lineIn.poke(line.U)
        dut.io.outFmt.poke(fmt.U)
        dut.io.blkSel.poke(blkSel.U)
        dut.io.nEff.poke(cfg.totalN.U)
        dut.io.rnd.poke(rndLits(rnd))
        val p8 = dut.io.packed8.peek().litValue
        val sc = dut.io.scaleRow.peek().litValue
        (p8, sc)
      }
      // 全零块：两 8 元素组均全零 → scale 字节 0x00 哨兵、元素码全 ±0（独立期望）
      val (pz, sz) = runRow(Seq.fill(cfg.totalN)(0.0f), fmt = 4, blkSel = 0, rnd = 0)
      pz shouldBe BigInt(0)
      sz shouldBe BigInt(0)
      // FTZ 边界：块 [1.0, -1e-30, ...]，scale 由 1.0 定（127+7-15=119），
      // -1e-30（指数字段≈28）量化后低于最小正规格化 → 保号 -0（0x80，独立期望）
      val ftzRow = Seq(1.0f, -1e-30f) ++ Seq.fill(cfg.totalN - 2)(0.0f)
      val (pf, sf) = runRow(ftzRow, fmt = 4, blkSel = 0, rnd = 0)
      ((pf >> 0) & 0xFF).toInt shouldBe 0x78          // 1.0/2^-8=256=2^8 → (exp15,mant0)=0x78（独立期望）
      ((pf >> 8) & 0xFF).toInt shouldBe 0x80          // -1e-30 → FTZ 保号负零（独立期望）
      (sf & 0xFF).toInt shouldBe 119                  // scale = 127+7-15（独立推导）
      // inf 输入：块 scale 钳制 254；inf → 饱和最大有限值（保号），其余元素下溢归零（独立期望）
      val infRow = Seq(Float.PositiveInfinity, Float.NegativeInfinity, 3.0f) ++ Seq.fill(cfg.totalN - 3)(0.0f)
      val (pi, si) = runRow(infRow, fmt = 4, blkSel = 0, rnd = 0)
      ((pi >> 0) & 0xFF).toInt shouldBe 0x7E          // +inf → +448 饱和
      ((pi >> 8) & 0xFF).toInt shouldBe 0xFE          // -inf → -448 饱和（保号）
      ((pi >> 16) & 0xFF).toInt shouldBe 0x00         // 3.0 相对 2^127 因子下溢 → +0
      (si & 0xFF).toInt shouldBe 254
      // nan 映射：E4M3 → 0x7F（exp&mant 全1）；E5M2 → 0x7E（exp 全1 规范尾数）；
      // E2M1 无 nan 编码 → 裁定饱和 0x7（packed4 低 nibble 验证）
      val nanRow = Seq(Float.NaN) ++ Seq.fill(cfg.totalN - 1)(0.0f)
      val (pn4, _) = runRow(nanRow, fmt = 4, blkSel = 0, rnd = 0)
      (pn4 & 0xFF).toInt shouldBe 0x7F
      val (pn5, _) = runRow(nanRow, fmt = 5, blkSel = 0, rnd = 0)
      (pn5 & 0xFF).toInt shouldBe 0x7E
      // E2M1：看 packed4（低列号低 nibble）；同时块 amax=inf → scale=254
      val bitsSeq = nanRow.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
      var line = BigInt(0)
      for ((bits, j) <- bitsSeq.zipWithIndex) line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * j)
      dut.io.lineIn.poke(line.U)
      dut.io.outFmt.poke(6.U)
      dut.io.blkSel.poke(0.U)
      dut.io.nEff.poke(cfg.totalN.U)
      dut.io.rnd.poke(RoundingMode.RNE)
      (dut.io.packed4.peek().litValue & 0xF).toInt shouldBe 0x7
      (dut.io.scaleRow.peek().litValue & 0xFF).toInt shouldBe 254
    }
  }

  it should "nEff 掩码：padding 列不进 amax、元素码输出 0（blk8/blk16）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // 大值 1e10 全部放在列 8..15（padding 区）：nEff=8 时被掩，
      // 组 1 scale 应为全零块哨兵 0；组 0 scale 仅由小值决定（独立期望：
      // 1.0 → 127+7-15=119）。blk16 时合并 amax = max(组 0, 0) = 组 0，
      // 两字节均为 119，但列 8..15 元素码仍为 0（不参与量化产出）。
      val floats = Seq.fill(8)(1.0f) ++ Seq.fill(8)(1e10f)
      pokeQuant(dut, fmt = 4, rnd = 0, blkSel = 0, nEff = 8, floats, clueTag = "[mask-blk8]")
      pokeQuant(dut, fmt = 4, rnd = 0, blkSel = 1, nEff = 8, floats, clueTag = "[mask-blk16]")
      pokeQuant(dut, fmt = 6, rnd = 0, blkSel = 0, nEff = 8, floats, clueTag = "[mask-e2m1]")
    }
  }

  it should "blkSel=32/64 在 totalN=16 下不炸（硬件行为不定，仅生存性检查）" in {
    // totalN=16 < 32/64：块界超出阵列，运行期由 P3b config-err 拦截非法配置；
    // 本用例仅断言硬件不抛异常、输出可采样（不对值做要求）。
    simulate(new HiveOutCvt(cfg)) { dut =>
      val floats = randomQuantFloats(seed = 77)
      val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
      var line = BigInt(0)
      for ((bits, j) <- bitsSeq.zipWithIndex) line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * j)
      dut.io.lineIn.poke(line.U)
      dut.io.outFmt.poke(4.U)
      dut.io.nEff.poke(cfg.totalN.U)
      dut.io.rnd.poke(RoundingMode.RNE)
      for (blk <- Seq(2, 3)) {
        dut.io.blkSel.poke(blk.U)
        dut.io.packed8.peek()   // 可采样即通过（不断言值）
        dut.io.scaleRow.peek()
      }
    }
  }
}
