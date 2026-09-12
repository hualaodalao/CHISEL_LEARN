/** HiveWorkUnit 级 MX 计算通路单测（Phase 2：fp4 quad-lane 打通 + fp8 dual-lane 位级非回归）。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 本文件锁死的是什么
  * ═══════════════════════════════════════════════════════════════════════════
  * Phase 2 在 HiveWorkUnit 的 MX 块新增了【与 fp8 dual-lane 物理隔离】的 fp4 quad-lane
  * 独立编译期分支（hasMx4 门控）：4 lane（elemBits=4）、8×HiveCvtOpMx(hasMx4=true)、
  * 4 级 HiveMacFpMx 左结合级联（k 升序、低 sublane 先加），运行期由
  * Mux(DataFormat.isMx4(io.aFmt), quadResult, dualLaneMx()) 选择。本文件从【张量语义】
  * 正向验证该通路，并证明 fp8 dual-lane 逐值非回归。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 金锚纪律（禁 RTL 拟合）
  * ═══════════════════════════════════════════════════════════════════════════
  *  fp4 金锚 = HiveSimCommon.mxE2M1ToFloat 解码 4 对 nibble + 【与硬件同款 lazy-40
  *  左结合累加顺序】（cReg + a0*b0 + a1*b1 + a2*b2 + a3*b3，lane0=io.a(3,0) 先）。
  *  crafted 元素取 E2M1 精确域：E2M1 每元素 ≤2 有效位 ⟹ 每乘积 ≤4 有效位，恰被
  *  HiveMacFpMx 窄化乘法器（sigW=4）与 31-bit lazy 尾数【无损】表示；crafted scale
  *  使指数展布 << 27 ⟹ 级联对齐不丢位 ⟹ Double 左结合和即硬件精确结果。故
  *  swFpNormalize40to32(io.sum) 必须逐位 == floatToRawIntBits(金锚和)。每个向量额外
  *  自证金锚和在 fp32 域精确（toFloat 往返无损），排除偶然舍入把测试变成拟合。
  *
  *  fp8 回归双保险：① DUT-vs-DUT —— hasMx4=false 配置（结构 ≡ 改动前，已由
  *  verilog/verilog_mx normalized-diff=0 独立复核）与 hasMx4=true 配置对【同一批】
  *  MXE4M3/MXE5M2 激励产出【逐位相同】的 io.sum（覆盖饱和/舍入全路径，与精确性无关）；
  *  ② 语义金锚 —— crafted 精确 E4M3/E5M2 向量下 io.sum == dual-lane 左结合张量语义和。
  *
  * 运行：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  *        sbt -J-Xmx6G "testOnly HiveWorkUnitMxSpec"
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.lang.Float.{floatToRawIntBits, intBitsToFloat}

class HiveWorkUnitMxSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  private val TAG = "WorkUnitMx-P2"

  // ==========================================================================
  // DUT 构造 / 激励 helper
  // ==========================================================================

  /** fp4：16-bit lane 打包 4 个 nibble（lane0=bits(3,0) 先加 … lane3=bits(15,12) 后加） */
  private def packFp4(n: Seq[Int]): Int = {
    require(n.size == 4)
    (n(0) & 0xF) | ((n(1) & 0xF) << 4) | ((n(2) & 0xF) << 8) | ((n(3) & 0xF) << 12)
  }

  /** fp8：16-bit lane 打包 2 个字节（lo=bits(7,0)=偶 k 先加，hi=bits(15,8)=奇 k 后加） */
  private def packFp8(lo: Int, hi: Int): Int = (lo & 0xFF) | ((hi & 0xFF) << 8)

  /** cReg（lazy-40 槽）：由精确 fp32 值经 HiveSimCommon.fp32BitsTo40 落槽 */
  private def cReg40(v: Double): BigInt = HiveSimCommon.fp32BitsTo40(floatToRawIntBits(v.toFloat) & 0xFFFFFFFFL)

  private def resetSeq(dut: HiveWorkUnit, fmt: DataFormat.Type): Unit = {
    dut.reset.poke(true.B)
    dut.io.a.poke(0.U); dut.io.b.poke(0.U); dut.io.cReg.poke(0.U)
    dut.io.aFmt.poke(fmt); dut.io.bFmt.poke(fmt)
    dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
    dut.io.rnd.poke(RoundingMode.RNE)
    dut.io.consumeValid.poke(false.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
    dut.clock.step()
  }

  /** 单向量：poke 全端口后读回 io.sum（cEffW=40，纯组合，poke→peek 即 settle）。
    * consumeValid 恒 false ⟹ HiveCvtOpMx 的 inf/nan 时序断言不评估（fp8 饱和码安全）。 */
  private def driveSum(dut: HiveWorkUnit, a: Int, b: Int, cReg: BigInt,
                       fmt: DataFormat.Type, scaleA: Int, scaleB: Int): BigInt = {
    dut.io.a.poke(a.U)
    dut.io.b.poke(b.U)
    dut.io.cReg.poke(cReg.U)
    dut.io.aFmt.poke(fmt); dut.io.bFmt.poke(fmt)
    dut.io.scaleA.poke(scaleA.U); dut.io.scaleB.poke(scaleB.U)
    dut.io.consumeValid.poke(false.B)
    dut.io.sum.peek().litValue
  }

  /** 失配收集器：一次性看全部失配点，只保留前 16 条详情 */
  private final class Mismatch {
    private val buf = scala.collection.mutable.ArrayBuffer[String]()
    private var n = 0
    def add(msg: String): Unit = { n += 1; if (buf.size < 16) buf += msg }
    def count: Int = n
  }

  // ==========================================================================
  // 金锚（张量语义 + 硬件同款左结合 lazy-40 顺序；crafted 值保证 Double 精确）
  // ==========================================================================

  /** fp4 quad-lane 金锚：cReg + Σ_{i=0..3} a_i*b_i（lane0 先加，左结合）。
    * a_i=mxE2M1ToFloat(aNib_i, scaleA)、b_i=mxE2M1ToFloat(bNib_i, scaleB)。 */
  private def goldenFp4(aNib: Seq[Int], bNib: Seq[Int], scaleA: Int, scaleB: Int, cRegVal: Double): Double = {
    var acc = cRegVal
    for (i <- 0 until 4) {
      val ai = HiveSimCommon.mxE2M1ToFloat(aNib(i), scaleA)
      val bi = HiveSimCommon.mxE2M1ToFloat(bNib(i), scaleB)
      acc = acc + ai * bi
    }
    acc
  }

  /** fp8 dual-lane 金锚：cReg + a_lo*b_lo + a_hi*b_hi（偶 k=低字节先加，左结合）。 */
  private def goldenFp8(aLo: Int, aHi: Int, bLo: Int, bHi: Int, fmt: DataFormat.Type,
                        scaleA: Int, scaleB: Int, cRegVal: Double): Double = {
    def dec(c: Int, sc: Int): Double =
      if (fmt == DataFormat.MXE5M2) HiveSimCommon.mxE5M2ToFloat(c, sc)
      else HiveSimCommon.mxE4M3ToFloat(c, sc)
    val pLo = dec(aLo, scaleA) * dec(bLo, scaleB)
    val pHi = dec(aHi, scaleA) * dec(bHi, scaleB)
    cRegVal + pLo + pHi
  }

  /** 金锚 Double → 期望 io.sum 经 swFpNormalize40to32 归一化后的 fp32 bits（-0/0 归 +0）。 */
  private def expectFp32Bits(sum: Double): Long =
    if (sum == 0.0) 0L else (floatToRawIntBits(sum.toFloat) & 0xFFFFFFFFL)

  // ==========================================================================
  // crafted 精确域激励集
  // ==========================================================================

  /** fp4 nibble 集：覆盖 0/正负幅值域 {±0.5,±1,±1.5,±2,±3,±4,±6} 与全零/单非零/对消。 */
  private val fp4NibSets: Seq[Seq[Int]] = Seq(
    Seq(0, 1, 2, 3), Seq(7, 6, 5, 4), Seq(1, 3, 5, 7), Seq(8, 9, 10, 11),
    Seq(0xF, 0xE, 0xD, 0xC), Seq(2, 4, 6, 0), Seq(1, 1, 1, 1), Seq(7, 8, 7, 8),
    Seq(0, 0, 0, 7), Seq(3, 12, 5, 10)
  )
  /** crafted E8M0 scale 对（net 指数展布小 ⟹ lazy-40 对齐无损、fp32 无溢出/下溢）。 */
  private val scalePairs: Seq[(Int, Int)] = Seq((127, 127), (124, 130), (130, 124), (126, 128))
  /** cReg 精确 fp32 值集（含 0 → 首级 cZero 直通路径）。 */
  private val cRegVals: Seq[Double] = Seq(0.0, 1.0, -2.0, 0.5, 10.0)

  // ==========================================================================
  behavior of "HiveWorkUnit fp4 quad-lane — Phase 2 张量语义 (左结合 lazy-40)"

  it should "MXE2M1：io.sum == 4-lane 左结合张量语义累加（金锚正向推导，禁 RTL 拟合）" in {
    val bad = new Mismatch
    var vectors = 0
    // supportedFmts 含 MXE2M1 ⟹ hasMx4=true ⟹ quad-lane 分支被 elaboration；
    // 同时含 MXE4M3/MXE5M2 ⟹ hasMx=true ⟹ io.sum 的 Mux(isMx, sumMx, baseSel) 路由生效。
    simulate(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
        supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1))) { dut =>
      resetSeq(dut, DataFormat.MXE2M1)
      for (aNib <- fp4NibSets; bNib <- fp4NibSets;
           (sa, sb) <- scalePairs; cRegVal <- cRegVals) {
        vectors += 1
        val a = packFp4(aNib); val b = packFp4(bNib)
        val golden = goldenFp4(aNib, bNib, sa, sb, cRegVal)
        // 自证：金锚和在 fp32 域精确（toFloat 往返无损）⟹ 比对不是拟合、是精确判据
        require(golden.toFloat.toDouble == golden,
          f"[$TAG] 金锚非精确，crafted 域被破坏：$golden (aNib=$aNib bNib=$bNib sa=$sa sb=$sb cReg=$cRegVal)")
        val sumRaw = driveSum(dut, a, b, cReg40(cRegVal), DataFormat.MXE2M1, sa, sb)
        val actual = HiveSimCommon.swFpNormalize40to32(sumRaw)
        val expect = expectFp32Bits(golden)
        if (actual != expect)
          bad.add(f"[fp4] a=0x$a%04X b=0x$b%04X sa=$sa sb=$sb cReg=$cRegVal " +
            f"sumRaw=0x${sumRaw.toString(16)} actual=0x$actual%08X(${intBitsToFloat(actual.toInt)}) " +
            f"expect=0x$expect%08X(${intBitsToFloat(expect.toInt)})")
      }
    }
    bad.count should be(0)
    println(f"[$TAG] fp4 quad-lane 语义通过：$vectors%d 向量（10×10 nibble 对 × ${scalePairs.size} scale × ${cRegVals.size} cReg），失配 ${bad.count}%d")
  }

  it should "MXE2M1：零乘积/单 lane/对消 边界 —— quad-lane 级联语义正确" in {
    val bad = new Mismatch
    var vectors = 0
    // 定向边界：全零积（cReg 直通）、仅 lane_k 非零（验证 k 升序、低 sublane 先加）、
    // 正负完全对消（结果 ±0 → 归一化 +0）。
    val cases: Seq[(Seq[Int], Seq[Int], Double)] = Seq(
      (Seq(0, 0, 0, 0), Seq(7, 7, 7, 7), 5.0),   // 全零 a ⟹ sum = cReg = 5.0
      (Seq(2, 0, 0, 0), Seq(4, 7, 7, 7), 0.0),   // 仅 lane0：1.0*2.0 = 2.0
      (Seq(0, 4, 0, 0), Seq(7, 3, 7, 7), 0.0),   // 仅 lane1：2.0*1.5 = 3.0
      (Seq(0, 0, 5, 0), Seq(7, 7, 2, 7), 0.0),   // 仅 lane2：3.0*1.0 = 3.0
      (Seq(0, 0, 0, 6), Seq(7, 7, 7, 2), 0.0),   // 仅 lane3：4.0*1.0 = 4.0
      (Seq(7, 0xF, 0, 0), Seq(2, 2, 0, 0), 0.0), // 6*1 + (-6)*1 = 0（对消）
      (Seq(1, 2, 3, 4), Seq(1, 2, 3, 4), -100.0) // cReg 主导 + 混合积
    )
    simulate(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
        supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1))) { dut =>
      resetSeq(dut, DataFormat.MXE2M1)
      for ((aNib, bNib, cRegVal) <- cases) {
        vectors += 1
        val golden = goldenFp4(aNib, bNib, 127, 127, cRegVal)
        require(golden.toFloat.toDouble == golden, s"[$TAG] 边界金锚非精确：$golden")
        val sumRaw = driveSum(dut, packFp4(aNib), packFp4(bNib), cReg40(cRegVal),
          DataFormat.MXE2M1, 127, 127)
        val actual = HiveSimCommon.swFpNormalize40to32(sumRaw)
        val expect = expectFp32Bits(golden)
        if (actual != expect)
          bad.add(f"[fp4-edge] aNib=$aNib bNib=$bNib cReg=$cRegVal actual=0x$actual%08X expect=0x$expect%08X")
      }
    }
    bad.count should be(0)
    println(f"[$TAG] fp4 quad-lane 边界通过：$vectors%d 向量，失配 ${bad.count}%d")
  }

  // ==========================================================================
  behavior of "HiveWorkUnit fp8 dual-lane — Phase 2 位级非回归 (硬门槛)"

  it should "MXE4M3/MXE5M2：hasMx4=true 与 hasMx4=false 两 DUT 对同一批激励 io.sum 逐位相同" in {
    // 广谱激励（含饱和/舍入/次正规路径，与精确性无关）：只比对两 DUT 的 raw io.sum BigInt。
    val vecs = scala.collection.mutable.ArrayBuffer[(Int, Int, BigInt, DataFormat.Type, Int, Int)]()
    for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2);
         aLo <- Seq(0x00, 0x38, 0x3C, 0x40, 0x7E, 0x7F, 0x2A, 0x55, 0xC0, 0xFF);
         aHi <- Seq(0x00, 0x38, 0x40, 0x7E, 0x30, 0x80);
         sc <- Seq(127, 130, 120)) {
      val bLo = (aLo ^ 0x11) & 0xFF; val bHi = (aHi ^ 0x07) & 0xFF
      vecs += ((packFp8(aLo, aHi), packFp8(bLo, bHi), cReg40(4.0), fmt, sc, (254 - sc) & 0xFF))
    }
    val sumsFp8Only = scala.collection.mutable.ArrayBuffer[BigInt]()
    val sumsFp4Cap = scala.collection.mutable.ArrayBuffer[BigInt]()
    // DUT-A：hasMx4=false（结构 ≡ 改动前 dual-lane）
    simulate(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
        supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2))) { dut =>
      resetSeq(dut, DataFormat.MXE4M3)
      for ((a, b, c, fmt, sa, sb) <- vecs) sumsFp8Only += driveSum(dut, a, b, c, fmt, sa, sb)
    }
    // DUT-B：hasMx4=true（新增 quad-lane 分支 + Mux，fp8 拍仍走 dual-lane）
    simulate(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
        supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1))) { dut =>
      resetSeq(dut, DataFormat.MXE4M3)
      for ((a, b, c, fmt, sa, sb) <- vecs) sumsFp4Cap += driveSum(dut, a, b, c, fmt, sa, sb)
    }
    sumsFp8Only.size should be(vecs.size)
    sumsFp4Cap.size should be(vecs.size)
    val diffIdx = (0 until vecs.size).filter(i => sumsFp8Only(i) != sumsFp4Cap(i))
    withClue(s"fp8 位级回归失配 ${diffIdx.size} 处（前 8）：" +
      diffIdx.take(8).map(i => s"\n  vec#${i} ${vecs(i)} fp8=0x${sumsFp8Only(i).toString(16)} fp4cap=0x${sumsFp4Cap(i).toString(16)}").mkString) {
      diffIdx should be(empty)
    }
    println(f"[$TAG] fp8 DUT-vs-DUT 位级回归通过：${vecs.size}%d 向量逐位相同，失配 ${diffIdx.size}%d")
  }

  it should "MXE4M3/MXE5M2：crafted 精确域下 io.sum == dual-lane 左结合张量语义金锚" in {
    val bad = new Mismatch
    var vectors = 0
    // crafted E4M3/E5M2 字节（小幅值、短尾数 ⟹ 乘积在窄化乘法器/lazy-40 中精确）。
    // E4M3: 0x30=0.5 0x38=1.0 0x3C=1.5 0x40=2.0；E5M2: 0x38=0.5 0x3C=1.0 0x3E=1.5 0x40=2.0。
    val codesByFmt: Map[DataFormat.Type, Seq[Int]] = Map(
      DataFormat.MXE4M3 -> Seq(0x00, 0x30, 0x38, 0x3C, 0x40, 0xB8, 0xBC, 0xC0),
      DataFormat.MXE5M2 -> Seq(0x00, 0x38, 0x3C, 0x3E, 0x40, 0xB8, 0xBC, 0xC0)
    )
    simulate(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
        supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1))) { dut =>
      for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2)) {
        resetSeq(dut, fmt)
        val codes = codesByFmt(fmt)
        for (aLo <- codes; aHi <- codes; bLo <- codes.take(4); bHi <- codes.take(4);
             cRegVal <- Seq(0.0, 3.0, -1.5)) {
          vectors += 1
          val golden = goldenFp8(aLo, aHi, bLo, bHi, fmt, 127, 127, cRegVal)
          require(golden.toFloat.toDouble == golden,
            s"[$TAG] fp8 金锚非精确：$golden fmt=$fmt ($aLo,$aHi,$bLo,$bHi) cReg=$cRegVal")
          val sumRaw = driveSum(dut, packFp8(aLo, aHi), packFp8(bLo, bHi),
            cReg40(cRegVal), fmt, 127, 127)
          val actual = HiveSimCommon.swFpNormalize40to32(sumRaw)
          val expect = expectFp32Bits(golden)
          if (actual != expect)
            bad.add(f"[fp8-sem] fmt=$fmt a=($aLo,$aHi) b=($bLo,$bHi) cReg=$cRegVal " +
              f"actual=0x$actual%08X(${intBitsToFloat(actual.toInt)}) expect=0x$expect%08X(${intBitsToFloat(expect.toInt)})")
        }
      }
    }
    bad.count should be(0)
    println(f"[$TAG] fp8 dual-lane 语义金锚通过：$vectors%d 向量，失配 ${bad.count}%d")
  }
}
