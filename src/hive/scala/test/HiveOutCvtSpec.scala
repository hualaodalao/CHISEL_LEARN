/** HiveOutCvt 单元测试（#129 恢复 672fb37 删除的全量覆盖 + #128 subnormal 定向护栏合并）。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 为什么本文件存在（refactor-loss 取证留痕 · #129）
  * ═══════════════════════════════════════════════════════════════════════════
  * 旧 HiveOutCvtSpec（590 行 / 16 用例）在 672fb37「debug mx」（DMA 拆分重构）被【整体
  * 删除】，致 HiveOutCvt 自该 commit 起失去全部单测覆盖 —— 这是本仓第 7 处 refactor-loss
  * （此前专项审计只扫五类代码形态模式，"整个测试文件被删"不在清单里，故漏掉）。
  * #128 先补回 2 个 subnormal 定向护栏；#129 从 672fb37^ 取回旧 spec 原文，迁移到当前
  * DataFormat 枚举后【恢复其全部 16 项覆盖】，与 #128 的 2 个护栏合并为本单一 spec（18 用例）。
  * #137 追加 INT4（DataFormat.INT4=9，半字节整数档）4 项覆盖 ⟹ 现 22 用例。
  *
  * ★ 编码漂移陷阱（#128 已确证，#129 逐处迁移）：旧 spec 用【裸整数】编码 outFmt/blkSel，
  *   与当前 HiveEnum.DataFormat 枚举整体漂移。直接复活会把 E4M3 用例 poke 成 E5M2。
  *   本文件【全文零裸数字】，一律用枚举符号。迁移前后取值对照：
  *     ┌──────────┬───────────────┬──────────────────────────┐
  *     │ 语义     │ 旧裸整数编码  │ 当前枚举符号 (litValue)  │
  *     ├──────────┼───────────────┼──────────────────────────┤
  *     │ fp32     │ 0             │ DataFormat.FP32   (0)    │
  *     │ fp16     │ 1             │ DataFormat.FP16   (1)    │
  *     │ bf16     │ 2             │ DataFormat.BF16   (2)    │
  *     │ E4M3     │ 4  ★漂移      │ DataFormat.MXE4M3 (3)    │
  *     │ E5M2     │ 5  ★漂移      │ DataFormat.MXE5M2 (4)    │
  *     │ E2M1     │ 6  ★漂移      │ DataFormat.MXE2M1 (5)    │
  *     │ int32    │ 8  ★漂移      │ DataFormat.INT32  (6)    │
  *     │ int16    │ 9  ★漂移      │ DataFormat.INT16  (7)    │
  *     │ int8     │ 10 ★漂移      │ DataFormat.INT8   (8)    │
  *     │ int4     │ 无（旧无此档）│ DataFormat.INT4   (9)    │  ← #137 起纳入覆盖
  *     │ blk 8/16/32/64 │ 0/1/2/3 │ MXBlockSize.M8/M16/M32/M64 (0/1/2/3) │
  *     └──────────┴───────────────┴──────────────────────────┘
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 覆盖维度（旧 16 项 + #128 护栏 2 项 + #137 INT4 4 项 = 22 用例）
  * ═══════════════════════════════════════════════════════════════════════════
  *  - fp16/bf16/fp32 通路：位模式向量 × 5 舍入模式逐位对拍 swFp32ToFp16/swFp32ToBf16/
  *    swFpNormalize40to32；饱和/溢出/FTZ/inf-nan 锚点；进位边界；RMM tie 定向；
  *  - 量化支路：5 舍入 × {E4M3,E5M2,E2M1} × {blk8,blk16} 随机向量位级对拍 swQuantBlock；
  *    448/446 饱和边界；全零块哨兵 / FTZ / inf-nan；nEff 掩码；blk32/64 生存性；
  *  - 整数族 int32/int16/int8 截断；out.ready 反压（行保持）；量化多拍持有（scale 恰一次）；
  *  - (#137) int4 半字节整数档 4 项：槽低 4 位硬截断 + 高位填零；lane→nibble 排列序
  *    （逐 lane + 与 MXE2M1 臂 packed4 字节组装逐字对账 + 反序陷阱定向）；4-bit 极值
  *    与超范围【绕回】（非钳位，派生自 int8 臂「截断取低位、不饱和」）+ 5 舍入模式输出
  *    恒同（整数支路不消费 rnd）；反压行保持 / in.ready 门控 / scaleOut.valid 恒 0
  *    （INT4 ∉ DataFormat.isMx ⟹ 无块 scale 语义、不产生 scaleC）；
  *  - (#128) FP16 subnormal FTZ 定向护栏 + MXE4M3 块相对 subnormal FTZ 定向护栏。
  *
  * 【握手化测试契约】所有用例经三 Stream 激励：driveRow（poke in.valid/bits 与
  * outFmt/rnd/blkSel/nEff，等 in.fire 后 step 一拍锁存）→ recvRow（拉 out.ready，
  * 量化行另拉 scaleOut.ready，等 out.valid 后 peek，step 一拍提交 fire）。
  *
  * 运行：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveOutCvtSpec"
  * （#129 起纳入常设 8-suite 全量回归。）
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.lang.Float.{floatToIntBits, intBitsToFloat}

// 后端选择留痕：不用 EphemeralSimulator——当前环境（Chisel 7.13 + Verilator 5.050）下
// EphemeralSimulator 默认路径无法产出仿真二进制，改用与 HiveCoreSimCase 同源的
// ChiselSim + HiveFstSim（verilator 显式配置，已验证可跑）。
class HiveOutCvtSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  // 测试用小配置：arrayN=8×clusterM=2 → totalN=16；cEffW 自动推导为 40（含浮点格式）
  private val cfg = HiveCoreConfig(arrayN = 8, clusterM = 2)

  // ==========================================================================
  // 公共激励 / 向量
  // ==========================================================================

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

  /** fp32 bits 序列 → 一行 40 位槽（元素 0 在低位） */
  private def buildLine(bitsSeq: Seq[Long]): BigInt = {
    var line = BigInt(0)
    for ((bits, i) <- bitsSeq.zipWithIndex) {
      line |= HiveSimCommon.fp32BitsTo40(bits) << (cfg.cEffW * i)
    }
    line
  }

  /** 驱动一行进 in：poke 数据与全部配置输入（枚举字面量），等 in.ready 后 step 一拍提交
    * in.fire（行 + 配置原子锁存）；结束时 deassert in.valid。 */
  private def driveRow(dut: HiveOutCvt, line: BigInt, outFmt: DataFormat.Type, rnd: RoundingMode.Type,
                       blkSel: MXBlockSize.Type = MXBlockSize.M8, nEff: Int = cfg.totalN): Unit = {
    dut.io.in.valid.poke(true.B)
    dut.io.in.bits.poke(line.U)
    dut.io.outFmt.poke(outFmt)
    dut.io.rnd.poke(rnd)
    dut.io.blkSel.poke(blkSel)
    dut.io.nEff.poke(nEff.U)
    var guard = 0
    while (!dut.io.in.ready.peek().litToBoolean) {
      dut.clock.step()
      guard += 1
      assert(guard < 64, "driveRow: in.ready 长期未拉起（DUT 死锁）")
    }
    dut.clock.step()   // 本沿提交 in.fire → 行寄存器锁存
    dut.io.in.valid.poke(false.B)
  }

  /** 消费一行：拉 out.ready（量化行另拉 scaleOut.ready），等 out.valid 后 peek
    * out.bits（及量化行 scaleOut.bits），step 一拍提交 fire，随后撤 ready。
    * 顺带断言握手契约：量化行 scaleOut.valid 与 out.valid 同拍；非量化行恒 0。 */
  private def recvRow(dut: HiveOutCvt, quant: Boolean): (BigInt, BigInt) = {
    dut.io.out.ready.poke(true.B)
    if (quant) dut.io.scaleOut.ready.poke(true.B)
    var guard = 0
    while (!dut.io.out.valid.peek().litToBoolean) {
      dut.clock.step()
      guard += 1
      assert(guard < 64, "recvRow: out.valid 长期未拉起（DUT 死锁）")
    }
    if (quant) {
      assert(dut.io.scaleOut.valid.peek().litToBoolean, "量化行 scaleOut.valid 应与 out.valid 同拍")
    } else {
      assert(!dut.io.scaleOut.valid.peek().litToBoolean, "非量化行 scaleOut.valid 必须恒 0")
    }
    val data = dut.io.out.bits.peek().litValue
    val scale = if (quant) dut.io.scaleOut.bits.peek().litValue else BigInt(0)
    dut.clock.step()   // 本沿提交 out.fire（量化行同拍 scaleOut.fire）
    dut.io.out.ready.poke(false.B)
    if (quant) dut.io.scaleOut.ready.poke(false.B)
    (data, scale)
  }

  // ==========================================================================
  // 浮点通路（fp32 → fp16 / bf16 / fp32 密集行转换）
  // ==========================================================================
  behavior of "HiveOutCvt (fp32 -> fp16/bf16/fp32 密集行转换)"

  /** 单轮对拍：给定 outFmt（FP16 / BF16）与 rnd（0..4），逐位对拍软件参考模型 */
  private def checkPacked16(outFmt: DataFormat.Type, rnd: Int): Unit = {
    val vec = testVectors
    val padded = vec ++ Seq.fill((cfg.totalN - vec.size % cfg.totalN) % cfg.totalN)(0L)
    val chunks = padded.grouped(cfg.totalN).toSeq
    var chunkIdx = 0
    simulate(new HiveOutCvt(cfg)) { dut =>
      for (chunk <- chunks) {
        val line = buildLine(chunk)
        driveRow(dut, line, outFmt, rndLits(rnd))
        val (got, _) = recvRow(dut, quant = false)   // fp16/bf16 选段 = out 低 totalN×16 位
        for ((bits, i) <- chunk.zipWithIndex) {
          // 硬件管线：40 位槽 → 40→32 规格化（固定简化 RNE）→ 32→16（rnd 5 模式），
          // 期望值必须同管线重建，不能直取原始 fp32 bits
          val normBits = HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(bits))
          val expect =
            if (outFmt == DataFormat.FP16) HiveSimCommon.swFp32ToFp16(normBits, rnd)
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
    for (rnd <- 0 to 4) checkPacked16(outFmt = DataFormat.FP16, rnd = rnd)
  }

  it should "bf16 通路与软件参考模型逐位一致（5 种舍入模式）" in {
    for (rnd <- 0 to 4) checkPacked16(outFmt = DataFormat.BF16, rnd = rnd)
  }

  it should "fp32 通路（out 全宽选段）与 fpNormalize40to32 软件模型逐位一致" in {
    val vec = testVectors
    val padded = vec ++ Seq.fill((cfg.totalN - vec.size % cfg.totalN) % cfg.totalN)(0L)
    val chunks = padded.grouped(cfg.totalN).toSeq
    simulate(new HiveOutCvt(cfg)) { dut =>
      for (chunk <- chunks) {
        val line = buildLine(chunk)
        driveRow(dut, line, outFmt = DataFormat.FP32, rnd = RoundingMode.RNE)
        val (got, _) = recvRow(dut, quant = false)
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
        driveRow(dut, HiveSimCommon.fp32BitsTo40(bits), outFmt = DataFormat.FP16, rnd = RoundingMode.RNE)
        val (got, _) = recvRow(dut, quant = false)
        (got & 0xFFFF).toInt
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
      def pokeRaw40(raw: BigInt): BigInt = {
        driveRow(dut, raw, outFmt = DataFormat.FP32, rnd = RoundingMode.RNE)
        val (got, _) = recvRow(dut, quant = false)
        got & BigInt(0xFFFFFFFFL)
      }
      // 构造 m31=0x7FFFFFFF（隐含 1 + 尾数位全 1）：lzc=0，规格化后
      // mant23=0x7FFFFF 全 1、guard=1、sticky=1 → 舍入必进位。
      // 正：e=140 → 2^14 = 0x46800000（exp=141，mant=0）
      pokeRaw40((BigInt(140) << 31) | BigInt(0x7FFFFFFF)) shouldBe BigInt(0x46800000L)
      // 负：sign=1 → 0xC6800000
      pokeRaw40((BigInt(1) << 39) | (BigInt(140) << 31) | BigInt(0x7FFFFFFF)) shouldBe BigInt(0xC6800000L)
      // 反向控制组：尾数低 7 位全 0（guard=0/sticky=0 → 不进位），逐位透传：exp=140、mant=0x7FFFFE
      pokeRaw40((BigInt(140) << 31) | BigInt(0x7FFFFF00)) shouldBe BigInt(0x467FFFFEL)
    }
  }

  it should "RMM tie/非 tie 定向用例：仅 tie（guard=1，不论 sticky）进位（独立期望值）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      def pokeOne(bits: Long, outFmt: DataFormat.Type, rnd: RoundingMode.Type): Int = {
        driveRow(dut, HiveSimCommon.fp32BitsTo40(bits), outFmt, rnd)
        val (got, _) = recvRow(dut, quant = false)
        (got & 0xFFFF).toInt
      }
      val tie     = 0x3F801000L   // 1 + 2^-11：guard=1/sticky=0 → 恰为 tie
      val nonTie1 = 0x3F801001L   // guard=1/sticky=1（非 tie，但 guard=1）
      val nonTie0 = 0x3F800001L   // guard=0/sticky=1（非 tie）
      // RMM（ties to Max Magnitude）：tie → 进位；guard=1 亦进位；仅 sticky 不进位。
      pokeOne(tie, DataFormat.FP16, RoundingMode.RMM) shouldBe 0x3C01
      pokeOne(nonTie1, DataFormat.FP16, RoundingMode.RMM) shouldBe 0x3C01
      pokeOne(nonTie0, DataFormat.FP16, RoundingMode.RMM) shouldBe 0x3C00
      // 交叉对照（独立期望）：RNE 严格 tie-to-even；RTZ 恒截断；RUP 正数增大幅值
      pokeOne(tie, DataFormat.FP16, RoundingMode.RNE) shouldBe 0x3C00
      pokeOne(tie, DataFormat.FP16, RoundingMode.RTZ) shouldBe 0x3C00
      pokeOne(tie, DataFormat.FP16, RoundingMode.RUP) shouldBe 0x3C01
      pokeOne(nonTie0, DataFormat.FP16, RoundingMode.RUP) shouldBe 0x3C01
      // bf16：guard=x(15)，sticky=x(14,0)，lsb=x(16)；tie 构造 1 + 2^-8 = 0x3F808000
      val bfTie = 0x3F808000L
      pokeOne(bfTie, DataFormat.BF16, RoundingMode.RMM) shouldBe 0x3F81   // tie 进位 → 1+2^-7
      pokeOne(bfTie, DataFormat.BF16, RoundingMode.RNE) shouldBe 0x3F80   // lsb=0 tie-to-even 截断
      pokeOne(bfTie, DataFormat.BF16, RoundingMode.RTZ) shouldBe 0x3F80
      pokeOne(bfTie, DataFormat.BF16, RoundingMode.RUP) shouldBe 0x3F81
    }
  }

  // ==========================================================================
  // 量化支路（P3a: fp32 → MX 块共享 scale 量化）
  // ==========================================================================
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

  /** 单次握手对拍：元素码（out 低位选段）+ scaleOut 全量比对。
    * fmt：MXE4M3/MXE5M2/MXE2M1（枚举）；blkSel：MXBlockSize 枚举（内部取 litValue 作 swQuantBlock 的 Int）。 */
  private def pokeQuant(dut: HiveOutCvt, fmt: DataFormat.Type, rnd: Int, blkSel: MXBlockSize.Type,
                        nEff: Int, floats: Seq[Float], clueTag: String): Unit = {
    val blkInt = blkSel.litValue.toInt
    val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
    val line = buildLine(bitsSeq)
    driveRow(dut, line, fmt, rndLits(rnd), blkSel, nEff)
    val (gotRow, gotScale) = recvRow(dut, quant = true)
    // 同两级舍入链重建：40 位槽 → 40→32 固定简化 RNE 规格化 → 块量化期望
    val normSeq = bitsSeq.map(b => HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(b)))
    val (codes, scaleBytes) = HiveSimCommon.swQuantBlock(normSeq, rnd, fmt, blkInt, nEff)
    if (fmt == DataFormat.MXE2M1) {
      for (i <- 0 until cfg.totalN) {
        val actual = ((gotRow >> (4 * i)) & 0xF).toInt
        withClue(s"$clueTag elem=$i val=${floats(i)}: ") { actual shouldBe codes(i) }
      }
    } else {
      for (i <- 0 until cfg.totalN) {
        val actual = ((gotRow >> (8 * i)) & 0xFF).toInt
        withClue(s"$clueTag elem=$i val=${floats(i)}: ") { actual shouldBe codes(i) }
      }
    }
    for (g <- 0 until cfg.totalN / 8) {
      val actual = ((gotScale >> (8 * g)) & 0xFF).toInt
      withClue(s"$clueTag scaleGroup=$g: ") { actual shouldBe scaleBytes(g) }
    }
  }

  it should "5 种舍入 × E4M3/E5M2/E2M1 × blk8/blk16 随机向量位级对拍" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1);
           rnd <- 0 to 4;
           blk <- Seq(MXBlockSize.M8, MXBlockSize.M16)) {
        val fi = fmt.litValue.toInt
        val bi = blk.litValue.toInt
        pokeQuant(dut, fmt, rnd, blk, cfg.totalN,
          randomQuantFloats(seed = fi * 10000 + rnd * 100 + bi),
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
      val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
      val line = buildLine(bitsSeq)
      def runAt(rnd: Int): (BigInt, BigInt) = {
        driveRow(dut, line, outFmt = DataFormat.MXE4M3, rnd = rndLits(rnd), blkSel = MXBlockSize.M8)
        recvRow(dut, quant = true)
      }
      for (rnd <- 0 to 4) {
        val (row, scale) = runAt(rnd)
        withClue(s"scale rnd=$rnd: ") { (scale & 0xFF).toInt shouldBe 127 }
        withClue(s"448 rnd=$rnd: ") { ((row >> 0) & 0xFF).toInt shouldBe 0x7E }
      }
      def codeAt(rnd: Int, i: Int): Int = {
        val (row, _) = runAt(rnd)
        ((row >> (8 * i)) & 0xFF).toInt
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
      def runRow(floats: Seq[Float], fmt: DataFormat.Type, blkSel: MXBlockSize.Type, rnd: Int): (BigInt, BigInt) = {
        val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
        val line = buildLine(bitsSeq)
        driveRow(dut, line, fmt, rndLits(rnd), blkSel)
        recvRow(dut, quant = true)
      }
      // 全零块：两 8 元素组均全零 → scale 字节 0x00 哨兵、元素码全 ±0（独立期望）
      val (pz, sz) = runRow(Seq.fill(cfg.totalN)(0.0f), fmt = DataFormat.MXE4M3, blkSel = MXBlockSize.M8, rnd = 0)
      pz shouldBe BigInt(0)
      sz shouldBe BigInt(0)
      // FTZ 边界：块 [1.0, -1e-30, ...]，scale 由 1.0 定（127+7-15=119），
      // -1e-30（指数字段≈28）量化后低于最小正规格化 → 保号 -0（0x80，独立期望）
      val ftzRow = Seq(1.0f, -1e-30f) ++ Seq.fill(cfg.totalN - 2)(0.0f)
      val (pf, sf) = runRow(ftzRow, fmt = DataFormat.MXE4M3, blkSel = MXBlockSize.M8, rnd = 0)
      ((pf >> 0) & 0xFF).toInt shouldBe 0x78          // 1.0/2^-8=256=2^8 → (exp15,mant0)=0x78
      ((pf >> 8) & 0xFF).toInt shouldBe 0x80          // -1e-30 → FTZ 保号负零
      (sf & 0xFF).toInt shouldBe 119                  // scale = 127+7-15
      // inf 输入：块 scale 钳制 254；inf → 饱和最大有限值（保号），其余元素下溢归零
      val infRow = Seq(Float.PositiveInfinity, Float.NegativeInfinity, 3.0f) ++ Seq.fill(cfg.totalN - 3)(0.0f)
      val (pi, si) = runRow(infRow, fmt = DataFormat.MXE4M3, blkSel = MXBlockSize.M8, rnd = 0)
      ((pi >> 0) & 0xFF).toInt shouldBe 0x7E          // +inf → +448 饱和
      ((pi >> 8) & 0xFF).toInt shouldBe 0xFE          // -inf → -448 饱和（保号）
      ((pi >> 16) & 0xFF).toInt shouldBe 0x00         // 3.0 相对 2^127 因子下溢 → +0
      (si & 0xFF).toInt shouldBe 254
      // nan 映射：E4M3 → 0x7F（exp&mant 全1）；E5M2 → 0x7E（exp 全1 规范尾数）；
      // E2M1 无 nan 编码 → 裁定饱和 0x7（out 低位选段低 nibble 验证）
      val nanRow = Seq(Float.NaN) ++ Seq.fill(cfg.totalN - 1)(0.0f)
      val (pn4, _) = runRow(nanRow, fmt = DataFormat.MXE4M3, blkSel = MXBlockSize.M8, rnd = 0)
      (pn4 & 0xFF).toInt shouldBe 0x7F
      val (pn5, _) = runRow(nanRow, fmt = DataFormat.MXE5M2, blkSel = MXBlockSize.M8, rnd = 0)
      (pn5 & 0xFF).toInt shouldBe 0x7E
      // E2M1：看 out 低 totalN×4 选段（低列号低 nibble）；同时块 amax=inf → scale=254
      val (pn2, sn2) = runRow(nanRow, fmt = DataFormat.MXE2M1, blkSel = MXBlockSize.M8, rnd = 0)
      (pn2 & 0xF).toInt shouldBe 0x7
      (sn2 & 0xFF).toInt shouldBe 254
    }
  }

  it should "nEff 掩码：padding 列不进 amax、元素码输出 0（blk8/blk16）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // 大值 1e10 全部放在列 8..15（padding 区）：nEff=8 时被掩，
      // 组 1 scale 应为全零块哨兵 0；组 0 scale 仅由小值决定（1.0 → 119）。
      val floats = Seq.fill(8)(1.0f) ++ Seq.fill(8)(1e10f)
      pokeQuant(dut, fmt = DataFormat.MXE4M3, rnd = 0, blkSel = MXBlockSize.M8, nEff = 8, floats, clueTag = "[mask-blk8]")
      pokeQuant(dut, fmt = DataFormat.MXE4M3, rnd = 0, blkSel = MXBlockSize.M16, nEff = 8, floats, clueTag = "[mask-blk16]")
      pokeQuant(dut, fmt = DataFormat.MXE2M1, rnd = 0, blkSel = MXBlockSize.M8, nEff = 8, floats, clueTag = "[mask-e2m1]")
    }
  }

  it should "blkSel=32/64 在 totalN=16 下不炸（硬件行为不定，仅生存性检查）" in {
    // totalN=16 < 32/64：块界超出阵列，运行期由 config-err 拦截非法配置；
    // 本用例仅断言硬件不抛异常、输出可采样（不对值做要求）。
    simulate(new HiveOutCvt(cfg)) { dut =>
      val floats = randomQuantFloats(seed = 77)
      val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
      val line = buildLine(bitsSeq)
      for (blk <- Seq(MXBlockSize.M32, MXBlockSize.M64)) {
        driveRow(dut, line, outFmt = DataFormat.MXE4M3, rnd = RoundingMode.RNE, blkSel = blk)
        recvRow(dut, quant = true)   // 可采样即通过（不断言值）
      }
    }
  }

  // ==========================================================================
  // 整数族截断支路（槽低位截取、不饱和）+ out.ready 反压（行保持）
  // ==========================================================================
  behavior of "HiveOutCvt 整数族与握手反压 (P1)"

  it should "int32 截断：槽低 32 位直取（高非零位丢弃、不饱和）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      val slots = (0 until cfg.totalN).map(i => (BigInt(0xA5) << 32) | BigInt(0x12345678L + i))
      var line = BigInt(0)
      for ((v, i) <- slots.zipWithIndex) line |= v << (cfg.cEffW * i)
      driveRow(dut, line, outFmt = DataFormat.INT32, rnd = RoundingMode.RNE)
      val (got, _) = recvRow(dut, quant = false)
      for (i <- 0 until cfg.totalN) {
        val expect = slots(i) & 0xFFFFFFFFL
        withClue(s"elem=$i: ") { ((got >> (32 * i)) & 0xFFFFFFFFL) shouldBe expect }
      }
    }
  }

  it should "int16 截断：槽低 16 位直取，有效段以上高位填零" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      val slots = (0 until cfg.totalN).map(i => (BigInt(0xA5B4C3) << 16) | BigInt(0xD2E1 + i))
      var line = BigInt(0)
      for ((v, i) <- slots.zipWithIndex) line |= v << (cfg.cEffW * i)
      driveRow(dut, line, outFmt = DataFormat.INT16, rnd = RoundingMode.RNE)
      val (got, _) = recvRow(dut, quant = false)
      for (i <- 0 until cfg.totalN) {
        val expect = slots(i) & 0xFFFF
        withClue(s"elem=$i: ") { ((got >> (16 * i)) & 0xFFFF) shouldBe expect }
      }
      (got >> (cfg.totalN * 16)) shouldBe BigInt(0)
    }
  }

  it should "int8 截断：槽低 8 位直取，有效段以上高位填零" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      val slots = (0 until cfg.totalN).map(i => (BigInt("A5B4C3D2", 16) << 8) | BigInt(0xE1 + i))
      var line = BigInt(0)
      for ((v, i) <- slots.zipWithIndex) line |= v << (cfg.cEffW * i)
      driveRow(dut, line, outFmt = DataFormat.INT8, rnd = RoundingMode.RNE)
      val (got, _) = recvRow(dut, quant = false)
      for (i <- 0 until cfg.totalN) {
        val expect = slots(i) & 0xFF
        withClue(s"elem=$i: ") { ((got >> (8 * i)) & 0xFF) shouldBe expect }
      }
      (got >> (cfg.totalN * 8)) shouldBe BigInt(0)
    }
  }

  // --------------------------------------------------------------------------
  // (#137) INT4 半字节整数档（DataFormat.INT4 = 9）单元覆盖。
  //   语义派生自 INT8 臂（HiveOutCvt 内「#137 INT4 语义派生留痕 ①..⑥」）：
  //   INT8 臂 = 槽低 8 位【硬截断】、无饱和钳位、rnd 不参与、不做 nEff 掩码、
  //   lane i 落 byte i（lane0 最低位）。INT4 按 8→4 同构收窄，故本组用例锁死的是
  //   【截断绕回 mod 16】而非【钳位到 4-bit 有符号范围 [-8,7]】——钳位在整数族
  //   四档（INT32/INT16/INT8/INT4）中均不存在，属自创语义，不得引入。
  //   金锚同构佐证：HiveDebugModel.decodeElem(INT4) = sext(bits, 4)（纯二补码符号
  //   扩展、无饱和分支），与本组期望值逐条一致。
  // --------------------------------------------------------------------------

  /** INT4 行构造：lane i 放 32-bit 二补码位模式于 40 位槽低 32 位，槽高 8 位塞
    * 非零垃圾 0xA5（int32Seq 取 slot(31,0) ⟹ 垃圾必须被丢弃，同时证明硬截断）。 */
  private def buildInt4Line(i32Seq: Seq[Int]): BigInt = {
    var line = BigInt(0)
    for ((v, i) <- i32Seq.zipWithIndex) {
      val slot = (BigInt(0xA5) << 32) | (BigInt(v.toLong) & BigInt(0xFFFFFFFFL))
      line |= slot << (cfg.cEffW * i)
    }
    line
  }

  /** INT4 有效段掩码（低 totalN×4 位） */
  private val int4SegMask: BigInt = (BigInt(1) << (cfg.totalN * 4)) - 1

  it should "int4 截断：槽低 4 位直取（高非零位丢弃），有效段以上高位填零" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // 与 int8 用例同构：低 4 位 = 0x0..0xF（16 lane 互异，兼作排列序哨兵），
      // 槽内 bits[35:4] 全为非零垃圾 0xA5B4C3D2 ⟹ 若未硬截断则必然失配。
      val slots = (0 until cfg.totalN).map(i => (BigInt("A5B4C3D2", 16) << 4) | BigInt(i))
      var line = BigInt(0)
      for ((v, i) <- slots.zipWithIndex) line |= v << (cfg.cEffW * i)
      driveRow(dut, line, outFmt = DataFormat.INT4, rnd = RoundingMode.RNE)
      // quant=false：recvRow 内部断言 scaleOut.valid 恒 0（INT4 ∉ isMx ⟹ 无 scaleC）
      val (got, _) = recvRow(dut, quant = false)
      for (i <- 0 until cfg.totalN) {
        val expect = slots(i) & 0xF
        withClue(s"int4 elem=$i: ") { ((got >> (4 * i)) & 0xF) shouldBe expect }
      }
      (got >> (cfg.totalN * 4)) shouldBe BigInt(0)   // 与 int8/int16 用例同强度的高位填零断言
    }
  }

  it should "int4 lane->nibble 排列序：lane i 落 bits[4i+3:4i]，与 MXE2M1 臂字节组装逐字对账" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // 置换型激励 nib(i) = (5i+3) mod 16：16 个互异 nibble，任何反序/奇偶交错错位均被捕获
      val nib = (0 until cfg.totalN).map(i => (5 * i + 3) & 0xF)
      nib.toSet.size shouldBe cfg.totalN             // 前置自检：激励互异，否则排列序不可判
      val slots = nib.map(v => (BigInt("A5B4C3", 16) << 4) | BigInt(v))
      var line = BigInt(0)
      for ((v, i) <- slots.zipWithIndex) line |= v << (cfg.cEffW * i)
      driveRow(dut, line, outFmt = DataFormat.INT4, rnd = RoundingMode.RNE)
      val (got, _) = recvRow(dut, quant = false)

      // ① 逐 lane：lane i → nibble i（lane0 在最低位，与 int8 臂 Cat(int32Seq.reverse) 同序）
      for (i <- 0 until cfg.totalN) {
        withClue(s"lane=$i expect nibble=0x${nib(i).toHexString}: ") {
          ((got >> (4 * i)) & 0xF).toInt shouldBe nib(i)
        }
      }
      // ② 与 MXE2M1 臂 packed4 的既有约定逐字节对账（packed4 原文：
      //    Cat((0 until totalN/2).reverse.map{ p => Cat(hi=q2Seq(2p+1), lo=q2Seq(2p)) })）
      //    ⟹ byte p = (elem(2p+1) << 4) | elem(2p)：偶 lane 低 nibble、奇 lane 高 nibble。
      for (p <- 0 until cfg.totalN / 2) {
        val expectByte = (nib(2 * p + 1) << 4) | nib(2 * p)
        withClue(s"bytePair=$p (lane ${2 * p}=lo nibble, lane ${2 * p + 1}=hi nibble): ") {
          ((got >> (8 * p)) & 0xFF).toInt shouldBe expectByte
        }
      }
      // ③ 反序陷阱定向：整段不得等于 lane 反序打包（证明未误用非 reverse 的 Cat 序）
      var revPack = BigInt(0)
      for (i <- 0 until cfg.totalN) revPack |= BigInt(nib(cfg.totalN - 1 - i)) << (4 * i)
      withClue("int4 segment must NOT be lane-reversed packing: ") {
        (got & int4SegMask) shouldNot be(revPack)
      }
    }
  }

  it should "int4 边界与舍入：4-bit 极值精确通过、超范围绕回（非钳位）、5 舍入模式输出恒同" in {
    // 期望值派生自 INT8 臂的截断语义（无饱和、无舍入）：
    //   +8 → 0x8（绕回为 -8；钳位语义会给 0x7）；-9 → 0x7（绕回为 +7；钳位语义会给 0x8）。
    // 这两条正是「绕回 vs 钳位」的判据锚点，任何一侧改语义都会在本用例红。
    val cases: Seq[(Int, Int, String)] = Seq(
      (0,            0x0, "zero"),
      (7,            0x7, "int4 positive max +7 (in range)"),
      (-8,           0x8, "int4 negative min -8 (in range, two's complement code 0x8)"),
      (-1,           0xF, "negative one -> 0xF (sign bit = bit3 kept as-is)"),
      (8,            0x8, "upper overflow +8 WRAPS to 0x8 (-8), NOT clamped to 0x7"),
      (-9,           0x7, "lower overflow -9 WRAPS to 0x7 (+7), NOT clamped to 0x8"),
      (15,           0xF, "+15 wraps to 0xF (-1)"),
      (16,           0x0, "+16 wraps to 0x0"),
      (-16,          0x0, "-16 wraps to 0x0 (no negative zero in two's complement)"),
      (1234,         0x2, "1234 = 0x4D2 -> low nibble 0x2"),
      (-1234,        0xE, "-1234 = 0xFFFFFB2E -> low nibble 0xE (= -2)"),
      (Int.MaxValue, 0xF, "int32 max 0x7FFFFFFF -> 0xF"),
      (Int.MinValue, 0x0, "int32 min 0x80000000 -> 0x0")
    )
    cases.size should be <= (cfg.totalN)             // 前置自检：一行装得下全部 case
    simulate(new HiveOutCvt(cfg)) { dut =>
      // ① 逐 case 单 lane 定向（lane0 = 待测值，其余 lane = 0）
      for ((v, expCode, tag) <- cases) {
        driveRow(dut, buildInt4Line(Seq(v)), outFmt = DataFormat.INT4, rnd = RoundingMode.RNE)
        val (got, _) = recvRow(dut, quant = false)
        withClue(s"int4 [$tag] value=$v: ") { (got & 0xF).toInt shouldBe expCode }
        withClue(s"int4 [$tag] upper lanes must stay 0: ") { (got >> 4) shouldBe BigInt(0) }
      }
      // ② rnd 无关性：同一行（lane i = case i）在 5 种舍入模式下输出必须逐位相同，
      //    且等于独立重建的期望行（整数支路不消费 rndReg，roundUp16/roundUpN 不参与）
      val rowLine = buildInt4Line(cases.map(_._1))
      var expectRow = BigInt(0)
      for ((c, i) <- cases.zipWithIndex) expectRow |= BigInt(c._2) << (4 * i)
      for (rnd <- rndLits) {
        driveRow(dut, rowLine, outFmt = DataFormat.INT4, rnd = rnd)
        val (got, _) = recvRow(dut, quant = false)
        withClue(s"int4 rnd=$rnd row must be bit-identical to independent expectation: ") {
          got shouldBe expectRow
        }
      }
    }
  }

  it should "int4 握手：out.ready 反压行保持、in.ready 门控同既有档位、scaleOut.valid 恒 0（无 scaleC）" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // 行 A / 行 B：两组互异 nibble 置换（独立重建期望，不经金锚）
      val nibA = (0 until cfg.totalN).map(i => (i * 3 + 1) & 0xF)
      val nibB = (0 until cfg.totalN).map(i => (15 - i) & 0xF)
      val lineA = buildInt4Line(nibA)
      val lineB = buildInt4Line(nibB)
      var expA = BigInt(0)
      var expB = BigInt(0)
      for ((v, i) <- nibA.zipWithIndex) expA |= BigInt(v) << (4 * i)
      for ((v, i) <- nibB.zipWithIndex) expB |= BigInt(v) << (4 * i)

      // --- 行 A 锁存，out 侧反压；scaleOut.ready 主动拉起（INT4 ∉ isMx ⟹ valid 仍须恒 0）---
      dut.io.out.ready.poke(false.B)
      dut.io.scaleOut.ready.poke(true.B)
      driveRow(dut, lineA, outFmt = DataFormat.INT4, rnd = RoundingMode.RNE)
      dut.io.out.valid.peek().litToBoolean shouldBe true
      withClue("int4 row must NOT produce scaleC (scaleOut.valid): ") {
        dut.io.scaleOut.valid.peek().litToBoolean shouldBe false
      }
      (dut.io.out.bits.peek().litValue & int4SegMask) shouldBe expA
      // 反压保持 5 拍：行数据稳定不变、scaleOut.valid 仍恒 0
      dut.clock.step(5)
      dut.io.out.valid.peek().litToBoolean shouldBe true
      dut.io.scaleOut.valid.peek().litToBoolean shouldBe false
      (dut.io.out.bits.peek().litValue & int4SegMask) shouldBe expA

      // --- 反压期呈递新行 B：in.ready 必须为 0（行寄存器持 A），A 不被覆盖 ---
      dut.io.in.valid.poke(true.B)
      dut.io.in.bits.poke(lineB.U)
      dut.io.outFmt.poke(DataFormat.INT4)
      dut.io.rnd.poke(RoundingMode.RNE)
      dut.io.in.ready.peek().litToBoolean shouldBe false
      dut.clock.step(2)
      dut.io.in.ready.peek().litToBoolean shouldBe false
      (dut.io.out.bits.peek().litValue & int4SegMask) shouldBe expA   // 防提前覆盖

      // --- 解除反压：本沿 A 提交（out.fire）；非量化路径无需等 scaleOut.fire ⟹ 立即释放
      //     且背靠背直通锁存 B（与 FP16 反压用例同结构）---
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.out.ready.poke(false.B)
      dut.io.in.valid.poke(false.B)

      // --- 行 B 输出正确（行序未乱、未丢行）---
      dut.io.out.valid.peek().litToBoolean shouldBe true
      (dut.io.out.bits.peek().litValue & int4SegMask) shouldBe expB
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.out.ready.poke(false.B)
      // B 提交后行寄存器空闲：in.ready 恢复、out.valid 撤、scaleOut.valid 恒 0
      dut.io.in.ready.peek().litToBoolean shouldBe true
      dut.io.out.valid.peek().litToBoolean shouldBe false
      dut.io.scaleOut.valid.peek().litToBoolean shouldBe false
      dut.io.scaleOut.ready.poke(false.B)
    }
  }

  it should "out.ready 反压：行数据保持、行寄存器不被新行提前覆盖" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      val bitsA = floatToIntBits(1.5f).toLong & 0xFFFFFFFFL
      val bitsB = floatToIntBits(-2.75f).toLong & 0xFFFFFFFFL
      val lineA = HiveSimCommon.fp32BitsTo40(bitsA)
      val lineB = HiveSimCommon.fp32BitsTo40(bitsB)
      // 期望经同链重建：40→32 固定简化 RNE + 32→16 RNE
      val expA = HiveSimCommon.swFp32ToFp16(HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(bitsA)), 0)
      val expB = HiveSimCommon.swFp32ToFp16(HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(bitsB)), 0)

      // --- 行 A 锁存，out 侧反压（out.ready 延迟若干拍） ---
      dut.io.out.ready.poke(false.B)
      driveRow(dut, lineA, outFmt = DataFormat.FP16, rnd = RoundingMode.RNE)
      dut.io.out.valid.peek().litToBoolean shouldBe true
      (dut.io.out.bits.peek().litValue & 0xFFFF).toInt shouldBe expA
      // 反压保持 5 拍：行数据必须稳定不变
      dut.clock.step(5)
      dut.io.out.valid.peek().litToBoolean shouldBe true
      (dut.io.out.bits.peek().litValue & 0xFFFF).toInt shouldBe expA

      // --- 反压期间呈递新行 B：in.ready 必须为 0（行寄存器持 A），A 不被覆盖 ---
      dut.io.in.valid.poke(true.B)
      dut.io.in.bits.poke(lineB.U)
      dut.io.outFmt.poke(DataFormat.FP16)
      dut.io.rnd.poke(RoundingMode.RNE)
      dut.io.in.ready.peek().litToBoolean shouldBe false
      dut.clock.step(2)
      dut.io.in.ready.peek().litToBoolean shouldBe false
      (dut.io.out.bits.peek().litValue & 0xFFFF).toInt shouldBe expA   // 防提前覆盖

      // --- 解除反压：本沿 A 提交（out.fire）且背靠背直通锁存 B ---
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.out.ready.poke(false.B)
      dut.io.in.valid.poke(false.B)

      // --- 行 B 输出正确（行序未乱、未丢行） ---
      dut.io.out.valid.peek().litToBoolean shouldBe true
      (dut.io.out.bits.peek().litValue & 0xFFFF).toInt shouldBe expB
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.out.ready.poke(false.B)
      // B 提交后行寄存器空闲：in.ready 恢复
      dut.io.in.ready.peek().litToBoolean shouldBe true
      dut.io.out.valid.peek().litToBoolean shouldBe false
    }
  }

  it should "量化多拍持有：out.ready 延迟 + scaleOut.ready 间歇，每行 scale 恰提交一次" in {
    // 量化行双通道背压场景：
    //  ① out.ready 延迟若干拍：行数据保持、元素码与软件模型一致；
    //  ② out 先交付而 scaleOut.ready 压制：进入 dataDone 子态（out.valid 撤、
    //     scaleOut.valid 保持，行不释放）；
    //  ③ scaleOut.ready 间歇（每 3 拍拉起 1 拍）：每行 scale 恰交付一次（scaleSent
    //     锁存防重复 push），交付后行释放、in.ready 恢复，后续间歇不得再现 valid。
    simulate(new HiveOutCvt(cfg)) { dut =>
      dut.io.out.ready.poke(false.B)
      dut.io.scaleOut.ready.poke(false.B)
      var totalScaleFires = 0
      for (rowIdx <- 0 until 4) {
        val floats = randomQuantFloats(seed = 500 + rowIdx)
        val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
        val line = buildLine(bitsSeq)
        // 同两级舍入链重建期望：40→32 固定简化 RNE + 块量化（E4M3 blk8 RNE）
        val normSeq = bitsSeq.map(b => HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(b)))
        val (codes, scaleBytes) = HiveSimCommon.swQuantBlock(normSeq,
          RoundingMode.RNE.litValue.toInt, DataFormat.MXE4M3, MXBlockSize.M8.litValue.toInt, cfg.totalN)
        var expScale = BigInt(0)
        for ((sb, g) <- scaleBytes.zipWithIndex) expScale |= BigInt(sb & 0xFF) << (8 * g)

        // --- in.fire 锁存本行（out/scaleOut 均反压中） ---
        driveRow(dut, line, outFmt = DataFormat.MXE4M3, rnd = RoundingMode.RNE, blkSel = MXBlockSize.M8)
        dut.io.out.valid.peek().litToBoolean shouldBe true

        // --- out.ready 延迟：保持反压 3 拍，行数据必须稳定且逐元素正确 ---
        dut.clock.step(3)
        dut.io.out.valid.peek().litToBoolean shouldBe true
        val heldRow = dut.io.out.bits.peek().litValue
        for (i <- 0 until cfg.totalN) {
          withClue(s"row=$rowIdx held elem=$i: ") { ((heldRow >> (8 * i)) & 0xFF).toInt shouldBe codes(i) }
        }

        // --- 拉起 out.ready（scaleOut.ready 保持 0）：本沿 out.fire → dataDone 子态 ---
        dut.io.out.ready.poke(true.B)
        dut.clock.step()                       // 本沿提交 out.fire
        dut.io.out.ready.poke(false.B)
        dut.io.out.valid.peek().litToBoolean shouldBe false       // 数据已发
        dut.io.scaleOut.valid.peek().litToBoolean shouldBe true   // scale 待交，持行不释放
        // 压制 2 拍：行不得提前释放（in.ready 保持 0）
        dut.clock.step(2)
        dut.io.scaleOut.valid.peek().litToBoolean shouldBe true
        dut.io.in.ready.peek().litToBoolean shouldBe false

        // --- scaleOut.ready 间歇（每 3 拍拉起 1 拍）：计数 fire，恰一次 ---
        var rowScaleFires = 0
        var guard = 0
        while (rowScaleFires < 1 && guard < 30) {
          val readyNow = guard % 3 == 2
          dut.io.scaleOut.ready.poke(readyNow.B)
          if (readyNow && dut.io.scaleOut.valid.peek().litToBoolean) {
            dut.io.scaleOut.bits.peek().litValue shouldBe expScale
            rowScaleFires += 1                 // 本沿提交 scaleOut.fire
          }
          dut.clock.step()
          guard += 1
        }
        dut.io.scaleOut.ready.poke(false.B)
        withClue(s"row=$rowIdx scale fire 次数: ") { rowScaleFires shouldBe 1 }
        totalScaleFires += rowScaleFires

        // --- 释放后继续间歇 2 轮：不得重复交付（valid 恒 0），行寄存器空闲 ---
        for (g <- 0 until 6) {
          dut.io.scaleOut.ready.poke((g % 3 == 2).B)
          dut.io.scaleOut.valid.peek().litToBoolean shouldBe false
          dut.clock.step()
        }
        dut.io.scaleOut.ready.poke(false.B)
        dut.io.in.ready.peek().litToBoolean shouldBe true
      }
      totalScaleFires shouldBe 4               // 4 行 scale fire 总数 = 行数（无重复、无丢失）
    }
  }

  // ==========================================================================
  // (#128) subnormal / FTZ 定向护栏（确定性构造，绝不靠随机 seed 撞 subnormal）
  //   与 HiveDebugModel.compare() 的 FTZ bit-exact 判据（#129 收紧）同源：
  //   锁死「硬件对 subnormal 显式 FTZ 到保号零码」这一分支 A 权威证据。
  // ==========================================================================
  behavior of "HiveOutCvt subnormal FTZ (#128 directed guardrail)"

  // ---------- (A) FP16 输出 subnormal FTZ ----------
  it should "flush FP16 subnormal to zero deterministically (HW == golden swFp32ToFp16)" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // FP16 最小正规数 = 2^-14 ≈ 6.104e-5：
      //   3e-5 / -3e-5 / 1e-7 / 2^-15 → subnormal（指数域 <113）→ 硬件 FTZ 保号 ±0；
      //   2^-14（最小正规数本身，指数域恰 113）→ 保留正规码 0x0400；1.0 → 对照不冲零。
      val minNormalF = math.pow(2.0, -14).toFloat     // 6.1035e-5，正规边界
      val halfMinF   = math.pow(2.0, -15).toFloat     // 3.0518e-5，subnormal
      val floats = Seq(3e-5f, -3e-5f, 1e-7f, minNormalF, halfMinF, -minNormalF, 1.0f, 0.0f) ++
        Seq.fill(cfg.totalN - 8)(0.0f)
      val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
      driveRow(dut, buildLine(bitsSeq), DataFormat.FP16, RoundingMode.RNE, MXBlockSize.M8, cfg.totalN)
      val (got, _) = recvRow(dut, quant = false)

      // ① 逐元素对拍金锚（swFp32ToFp16 含 FTZ，与硬件 fp32ToFp16Rnd 逐位同构）
      for (i <- 0 until cfg.totalN) {
        val normBits = HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(bitsSeq(i)))
        val golden = HiveSimCommon.swFp32ToFp16(normBits, 0)
        val actual = ((got >> (16 * i)) & 0xFFFF).toInt
        withClue(s"FP16 elem=$i val=${floats(i)}: 硬件输出码必须与金锚 swFp32ToFp16 逐位一致：") {
          actual shouldBe golden
        }
      }
      // ② 独立硬锚点（不依赖金锚，锁死 subnormal→FTZ 语义与阈值边界）
      withClue("3e-5 (< 2^-14) 必须 FTZ 到 +0 (0x0000)：") { (got & 0xFFFF).toInt shouldBe 0x0000 }
      withClue("-3e-5 必须 FTZ 到 -0 (0x8000，保号)：") { ((got >> 16) & 0xFFFF).toInt shouldBe 0x8000 }
      withClue("1e-7 必须 FTZ 到 +0 (0x0000)：") { ((got >> 32) & 0xFFFF).toInt shouldBe 0x0000 }
      withClue("2^-14（最小正规数，非 subnormal）必须保留为正规码 0x0400：") {
        ((got >> 48) & 0xFFFF).toInt shouldBe 0x0400
      }
      withClue("2^-15（subnormal）必须 FTZ 到 +0 (0x0000)：") { ((got >> 64) & 0xFFFF).toInt shouldBe 0x0000 }
      withClue("-2^-14（负最小正规数）必须保留为 0x8400（不冲零）：") {
        ((got >> 80) & 0xFFFF).toInt shouldBe 0x8400
      }
      withClue("1.0（正规对照）必须保留为 0x3C00（不冲零）：") { ((got >> 96) & 0xFFFF).toInt shouldBe 0x3C00 }
    }
  }

  // ---------- (B) MXE4M3 输出 block-relative subnormal FTZ ----------
  it should "flush MXE4M3 block-relative subnormal to zero deterministically (HW == golden swQuantBlock)" in {
    simulate(new HiveOutCvt(cfg)) { dut =>
      // E4M3: expW=4/mantW=3/bias=7/maxCodeExp=15。块 amax 由 1.0 定：
      //   scale = e(1.0)+bias-maxCodeExp = 127+7-15 = 119 → 量化因子 2^(119-127) = 2^-8。
      //   swQuantElem FTZ 判据 eTgt = e(v)+bias-s < 1 ⟺ e(v) < 113 ⟺ |v| < 2^-14。
      // -1e-5 / +1e-5（|v| ≈ 2^-16.6 < 2^-14）→ FTZ 保号 ±0（码 0x80 / 0x00）；
      // -1e-30（远低阈值）→ FTZ 保号负零 0x80；1.0 → 0x78；-1.0 → 0xF8。
      val floats = Seq(1.0f, -1e-5f, 1e-5f, -1e-30f, -1.0f) ++ Seq.fill(cfg.totalN - 5)(0.0f)
      val bitsSeq = floats.map(f => floatToIntBits(f).toLong & 0xFFFFFFFFL)
      driveRow(dut, buildLine(bitsSeq), DataFormat.MXE4M3, RoundingMode.RNE, MXBlockSize.M8, cfg.totalN)
      val (got, scale) = recvRow(dut, quant = true)

      // ① 逐元素 + scale 对拍金锚（swQuantBlock 同两级舍入链，含 FTZ，与硬件 quantElem 同构）
      val normSeq = bitsSeq.map(b => HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(b)))
      val (codes, scaleBytes) = HiveSimCommon.swQuantBlock(normSeq,
        RoundingMode.RNE.litValue.toInt, DataFormat.MXE4M3, MXBlockSize.M8.litValue.toInt, cfg.totalN)
      for (i <- 0 until cfg.totalN) {
        val actual = ((got >> (8 * i)) & 0xFF).toInt
        withClue(s"MXE4M3 elem=$i val=${floats(i)}: 硬件量化码必须与金锚 swQuantBlock 逐位一致：") {
          actual shouldBe codes(i)
        }
      }
      for (g <- 0 until cfg.totalN / 8) {
        val actual = ((scale >> (8 * g)) & 0xFF).toInt
        withClue(s"MXE4M3 scaleGroup=$g: 硬件 scale 必须与金锚一致：") { actual shouldBe scaleBytes(g) }
      }
      // ② 独立硬锚点（不依赖金锚，锁死 block-relative subnormal→FTZ 语义）
      withClue("组0 scale 必须 = 119（amax=1.0 推导，因子 2^-8）：") { (scale & 0xFF).toInt shouldBe 119 }
      withClue("1.0 → 0x78（256=2^8, exp15 mant0）：") { (got & 0xFF).toInt shouldBe 0x78 }
      withClue("-1e-5（块内 subnormal）→ FTZ 保号负零 0x80：") { ((got >> 8) & 0xFF).toInt shouldBe 0x80 }
      withClue("+1e-5（块内 subnormal）→ FTZ 正零 0x00：") { ((got >> 16) & 0xFF).toInt shouldBe 0x00 }
      withClue("-1e-30（远低阈值）→ FTZ 保号负零 0x80：") { ((got >> 24) & 0xFF).toInt shouldBe 0x80 }
      withClue("-1.0 → 0xF8（保号正规，对照不冲零）：") { ((got >> 32) & 0xFF).toInt shouldBe 0xF8 }
    }
  }
}
