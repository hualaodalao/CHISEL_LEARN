/** MX 解码层单元测试（Phase 0：统一 MX 解码路径 —— mxfp4/E2M1 打通 + fp8 位级非回归）。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 本文件锁死的是什么
  * ═══════════════════════════════════════════════════════════════════════════
  * Phase 0 把 `Fp32.mxToFp32` 从「仅 8-bit MX」泛化为「8-bit(mxfp8) + 4-bit(mxfp4)」双档，
  * 新增两个【编译期】形参 noInfNan / subnormalFtz，并给 `HiveCvtOpMx` 加编译期门控 hasMx4。
  * 泛化的硬门槛是双向的：
  *   (A) 新增能力正确 —— mxfp4 E2M1 的 8 个幅值 {0,±0.5,±1,±1.5,±2,±3,±4,±6} × 2^(scale-127)
  *       必须被逐位正确解码（尤其 e=0/m=1=±0.5 不得 FTZ、0x7=±6.0 不得被当 inf/nan 饱和）；
  *   (B) 既有能力零回归 —— E4M3/E5M2 在改动后必须【逐位不变】。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 三源互不依赖的对拍拓扑（避免单源自证）
  * ═══════════════════════════════════════════════════════════════════════════
  *   源 1【DUT】  HiveCvtOpMx → Fp32.mxE2M1ToFp32 / mxE4M3ToFp32 / mxE5M2ToFp32（被测硬件）
  *   源 2【金锚】 HiveSimCommon.mxToFloat 族（与硬件同批泛化的软件参考模型）
  *   源 3【语义锚】本文件内 e2m1ExpectedBits / legacyMxFp32Bits —— 前者由
  *       MxFormatParams.E2M1 + IEEE 次正规语义【正向推导】E2M1 的 fp32 位模式（张量语义推导，
  *       非 RTL 拟合）；后者是【改动前】Fp32.mxToFp32 算法的独立逐位复刻，作为 fp8
  *       「改动后 == 改动前」的位级判据。
  * 每个用例至少让 DUT 与两个独立源同时对上；金锚与语义锚之间也互相对账。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 覆盖清单
  * ═══════════════════════════════════════════════════════════════════════════
  *  1. E2M1 全 16 编码 × 全 256 E8M0 scale 穷举（4096 向量）：DUT 位模式 == 语义锚位模式，
  *     且 DUT 数值 == 金锚 == 语义锚数值；覆盖 underflow 边界（scale 0/1 → ±0）、中性 127、
  *     overflow 边界（scale 253..255 → ±fp32Max）。
  *  2. E2M1 同一 DUT 内 fp8 两路交叉回归 + io.in 高 4 位 don't-care + 消费拍 inf/nan
  *     守护对 E2M1 恒不触发（0x7/0xF = ±6.0 是最大有限值，consumeValid=true 不得 $fatal）。
  *  3. fp8 生产路径（hasMx4=false 默认实例）E4M3/E5M2 全 256 编码 × 16 代表性 scale：
  *     DUT 位模式 == 改动前 legacy 模型位模式 == 金锚数值（bit-exact 非回归）。
  *  4. 金锚自身非回归（纯软件穷举，无仿真）：mxToFloat 在 fp8 默认实参下
  *     256 编码 × 256 scale × 2 格式（131072 向量）逐值 == legacy 模型。
  *  5. 编译期门控 dormant 自证（emitCHIRRTL/emitSystemVerilog，无仿真）：
  *     hasMx4 默认 false ⟹ RTL 与显式 false 逐字相同、且不含 E2M1 解码结构；
  *     hasMx4=true ⟹ 含 E2M1 选择子；HiveWorkUnit(supportedFmts 含 MXE2M1) 可 elaboration。
  *
  * 运行：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  *        sbt -J-Xmx6G "testOnly HiveMxDecodeSpec"
  */

import chisel3._
import chisel3.simulator.scalatest._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.lang.Float.intBitsToFloat

class HiveMxDecodeSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  private val TAG = "MxDecode-P0"

  // ==========================================================================
  // 源 3a：改动前 Fp32.mxToFp32 的独立【逐位】复刻（fp8 非回归判据）
  // ==========================================================================

  /** 泛化前算法的字面复刻：sign=x(7)、isZero||isSub→±0（FTZ）、isInfNan 原判据、
    * expSigned=e+scale-bias、<1→±0、>254 或 inf/nan→±fp32Max(0xFE/0x7FFFFF)、
    * 否则 Cat(s, expSigned(7,0), m<<(23-mantW))。分支序与硬件原式一致。
    * 与 HiveSimCommon 金锚互不依赖，用于「改动后硬件 == 改动前行为」的位级对账。 */
  private def legacyMxFp32Bits(x: Int, scale: Int, expW: Int, mantW: Int, bias: Int,
                               nanNeedMantAllOne: Boolean): Long = {
    require(1 + expW + mantW == 8, s"legacy 模型仅覆盖 fp8：1+$expW+$mantW")
    val eMax = (1 << expW) - 1
    val s = (x >>> 7) & 1
    val e = (x >>> mantW) & eMax
    val m = x & ((1 << mantW) - 1)
    val isZero = (e == 0) && (m == 0)
    val isSub = (e == 0) && (m != 0)
    val isInfNan = if (nanNeedMantAllOne) (e == eMax && m == ((1 << mantW) - 1)) else (e == eMax)
    val expSigned = e + (scale & 0xFF) - bias
    val signBits = (s.toLong & 1L) << 31
    val fp32Zero = signBits
    val fp32Max = signBits | Fp32MaxBits
    val fp32Normal = signBits | ((expSigned.toLong & 0xFFL) << 23) | ((m.toLong << (23 - mantW)) & 0x7FFFFFL)
    if (isZero || isSub) fp32Zero
    else if (isInfNan || expSigned > 254) fp32Max
    else if (expSigned < 1) fp32Zero
    else fp32Normal
  }

  /** fp32 最大有限值位模式（0x7F7FFFFF）：由 Float.MAX_VALUE 推导，非裸字面量 */
  private val Fp32MaxBits: Long =
    java.lang.Float.floatToRawIntBits(java.lang.Float.MAX_VALUE).toLong & 0x7FFFFFFFL

  // ==========================================================================
  // 源 3b：mxfp4 E2M1 的【正向语义】锚（由 MxFormatParams.E2M1 推导，禁裸格式字面量）
  // ==========================================================================

  private val p4 = MxFormatParams.E2M1

  /** E2M1 4-bit 码的幅值（不含 scale），按 IEEE 语义正向推导：
    *   e>0     : (1 + m/2^mantW) × 2^(e-bias)
    *   e=0,m=0 : 0
    *   e=0,m!=0: m/2^mantW × 2^(1-bias)   ← 次正规语义；E2M1(mantW=1,m=1) 下恰为 2^(-bias)=0.5
    * 得到权威幅值域 {0, 0.5, 1, 1.5, 2, 3, 4, 6}（0x7 = 6.0 为最大有限值，无 inf/nan）。 */
  private def e2m1Mag(code3: Int): Double = {
    val e = (code3 >>> p4.mantW) & ((1 << p4.expW) - 1)
    val m = code3 & ((1 << p4.mantW) - 1)
    if (e == 0) (m.toDouble / (1 << p4.mantW)) * math.pow(2.0, (1 - p4.bias).toDouble)
    else (1.0 + m.toDouble / (1 << p4.mantW)) * math.pow(2.0, (e - p4.bias).toDouble)
  }

  /** E2M1 码 → (isZero, Δ, mant23)：真值 = ±(1 + mant23/2^23) × 2^Δ，Δ 为相对 2^(scale-127)
    * 的指数增量，故 fp32 exp 字段 = scale + Δ。
    *   e>0      : Δ = e - bias，mant23 = m << (23 - mantW)（隐含 1.m）
    *   e=0,m=0  : 零（忽略 scale）
    *   e=0,m!=0 : 一等公民值 = 2^(-bias)（mantW=1/m=1 的唯一非零次正规恰为 2 的幂）
    *              ⟹ Δ = -bias、mant23 = 0（隐含 1.0）——【不得 FTZ】 */
  private def e2m1Decode(code4: Int): (Boolean, Int, Long) = {
    val c = code4 & ((1 << (p4.expW + p4.mantW + 1)) - 1)
    val e = (c >>> p4.mantW) & ((1 << p4.expW) - 1)
    val m = c & ((1 << p4.mantW) - 1)
    if (e == 0 && m == 0) (true, 0, 0L)
    else if (e == 0) {
      require(m == 1 && p4.mantW == 1, "本语义锚仅覆盖 E2M1：唯一非零次正规必须恰为 2^(-bias)")
      (false, -p4.bias, 0L)
    } else (false, e - p4.bias, (m.toLong << (23 - p4.mantW)) & 0x7FFFFFL)
  }

  /** E2M1 码 × E8M0 scale → 期望 fp32 位模式（语义正向推导）。
    * 沿用全仓统一 clamp 纪律（fp32 无次正规产出）：exp 字段 <1 → ±0（保号 FTZ）、
    * >254 → ±fp32Max（保号饱和）。E2M1 无 inf/nan 编码 ⟹ 不存在 inf/nan 饱和分支。 */
  private def e2m1ExpectedBits(code4: Int, scale: Int): Long = {
    val signBits = (((code4 >>> (p4.expW + p4.mantW)) & 1).toLong) << 31
    val (isZero, delta, mant23) = e2m1Decode(code4)
    if (isZero) signBits
    else {
      val expField = (scale & 0xFF) + delta
      if (expField < 1) signBits
      else if (expField > 254) signBits | Fp32MaxBits
      else signBits | (expField.toLong << 23) | mant23
    }
  }

  /** 语义锚的 Double 视图（与硬件位模式一一对应；±0 在 Double 域相等） */
  private def e2m1ExpectedValue(code4: Int, scale: Int): Double =
    intBitsToFloat(e2m1ExpectedBits(code4, scale).toInt).toDouble

  /** 语义锚的独立数值视图：±幅值 × 2^(scale-127)（用于与位模式视图互证） */
  private def e2m1MagScaled(code4: Int, scale: Int): Double = {
    val mag = e2m1Mag(code4 & ((1 << (p4.expW + p4.mantW)) - 1))
    val v = mag * math.pow(2.0, ((scale & 0xFF) - 127).toDouble)
    if (((code4 >>> (p4.expW + p4.mantW)) & 1) == 1) -v else v
  }

  // ==========================================================================
  // DUT 激励
  // ==========================================================================

  /** 复位序列：CvtOpMx 为纯组合，但其 inf/nan 断言是时序断言（需 clock/reset 有效沿） */
  private def resetSeq(dut: HiveCvtOpMx): Unit = {
    dut.reset.poke(true.B)
    dut.io.in.poke(0.U)
    dut.io.scale.poke(127.U)
    dut.io.fmt.poke(DataFormat.MXE4M3)
    dut.io.consumeValid.poke(false.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
    dut.clock.step()
  }

  /** 单向量：poke 元素/scale/fmt（+消费有效）后读回 io.out 的 32-bit 位模式。
    * @param step 是否在读取前推进一拍（仅在需要让时序断言在该沿被评估时为 true；
    *             数据通路纯组合，poke→peek 即已 settle，默认不 step 以压缩仿真时长） */
  private def cvtBits(dut: HiveCvtOpMx, code: Int, scale: Int, fmt: DataFormat.Type,
                      consumeValid: Boolean = false, step: Boolean = false): Long = {
    dut.io.in.poke(code.U)
    dut.io.scale.poke(scale.U)
    dut.io.fmt.poke(fmt)
    dut.io.consumeValid.poke(consumeValid.B)
    if (step) dut.clock.step()
    (dut.io.out.peek().litValue & 0xFFFFFFFFL).toLong
  }

  /** 代表性 E8M0 scale 集：underflow 边界（0/1/2/3）、中性 127、±1 邻域、
    * 大 scale 与 overflow 边界（252..255）。16 值。 */
  private val scalesRep: Seq[Int] = Seq(0, 1, 2, 3, 64, 126, 127, 128, 129, 140, 200, 250, 252, 253, 254, 255)

  /** fp8 交叉回归用的精简 scale 集（8 值） */
  private val scalesCross: Seq[Int] = Seq(0, 1, 7, 127, 128, 200, 254, 255)

  /** 失配收集器：一次性看全部失配点（而非首个即抛），但只保留前 16 条详情 */
  private final class Mismatch {
    private val buf = scala.collection.mutable.ArrayBuffer[String]()
    private var n = 0
    def add(msg: String): Unit = { n += 1; if (buf.size < 16) buf += msg }
    def count: Int = n
    def report(tag: String): String = s"$tag 失配 $n 处：\n" + buf.mkString("\n")
  }

  // ==========================================================================
  behavior of "Fp32.mxE2M1ToFp32 / HiveCvtOpMx(hasMx4=true) — mxfp4 解码 (Phase 0)"

  it should "穷举 E2M1 全 16 编码 × 全 256 scale：DUT 位模式 == 语义锚，DUT 数值 == 金锚" in {
    val bad = new Mismatch
    var vectors = 0
    simulate(new HiveCvtOpMx(hasMx4 = true)) { dut =>
      resetSeq(dut)

      // 组合通路自证：poke→peek（不 step）与 poke→step→peek 结果必须一致
      for ((c, sc) <- Seq((0x0, 127), (0x1, 127), (0x7, 130), (0xF, 3))) {
        val a = cvtBits(dut, c, sc, DataFormat.MXE2M1)
        val b = cvtBits(dut, c, sc, DataFormat.MXE2M1, step = true)
        if (a != b) bad.add(f"[settle] code=0x$c%X scale=$sc noStep=0x$a%08X step=0x$b%08X")
      }

      // 幅值域权威锚（语义表，与 spec {0,±0.5,±1,±1.5,±2,±3,±4,±6} 对账）
      val magTab = (0 until 8).map(e2m1Mag)
      if (magTab != Seq(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0))
        bad.add(s"[语义表] E2M1 幅值域推导异常：$magTab")

      for (code <- 0 until 16; scale <- 0 until 256) {
        vectors += 1
        val hw = cvtBits(dut, code, scale, DataFormat.MXE2M1)
        val expBits = e2m1ExpectedBits(code, scale)
        val hwVal = intBitsToFloat(hw.toInt).toDouble
        val golden = HiveSimCommon.mxE2M1ToFloat(code, scale)
        val anchor = e2m1ExpectedValue(code, scale)
        val clue = f"[E2M1 code=0x$code%X scale=$scale%3d]"
        // ① DUT 位模式 == 语义锚位模式（bit-exact）
        if (hw != expBits) bad.add(f"$clue bits hw=0x$hw%08X anchor=0x$expBits%08X")
        // ② DUT 数值 == 金锚（同源泛化）
        if (!(hwVal == golden)) bad.add(f"$clue golden hw=$hwVal anchor=$golden")
        // ③ 金锚 == 语义锚（两个独立软件源互证）
        if (!(golden == anchor)) bad.add(f"$clue anchor-vs-golden golden=$golden anchor=$anchor")
        // ④ 位模式视图 == 幅值×2^(scale-127) 视图（未 clamp 时；clamp 区另行核对）
        val expField = (scale & 0xFF) + e2m1Decode(code)._2
        val clamped = e2m1Decode(code)._1 == false && (expField < 1 || expField > 254)
        if (!clamped && !(hwVal == e2m1MagScaled(code, scale)))
          bad.add(f"$clue mag hw=$hwVal mag-scaled=${e2m1MagScaled(code, scale)}")
        // ⑤ 符号位保号：DUT bit31 必须恒等于码的 bit3（含 ±0 / 饱和 / FTZ 路径）
        if (((hw >>> 31) & 1L) != ((code >>> (p4.expW + p4.mantW)) & 1).toLong)
          bad.add(f"$clue sign hw=0x$hw%08X code=0x$code%X")
      }
    }
    bad.count should be(0)
    println(f"[$TAG] E2M1 穷举通过：$vectors%d 向量（16 编码 × 256 scale），失配 ${bad.count}%d")
  }

  it should "E2M1：io.in 高 4 位 don't-care + 消费拍 inf/nan 守护对 E2M1 恒不触发 + 同 DUT fp8 交叉回归" in {
    val bad = new Mismatch
    var vectors = 0
    simulate(new HiveCvtOpMx(hasMx4 = true)) { dut =>
      resetSeq(dut)

      // ① io.in 保持 8.W，E2M1 仅消费低 4 位（bit3=sign）⟹ 高 4 位必须 don't-care
      for (scale <- Seq(127, 130, 120, 200); code4 <- Seq(0x0, 0x1, 0x7, 0x8, 0x9, 0xF)) {
        val base = cvtBits(dut, code4, scale, DataFormat.MXE2M1)
        vectors += 1
        if (base != e2m1ExpectedBits(code4, scale))
          bad.add(f"[hibit-base] code=0x$code4%X scale=$scale hw=0x$base%08X")
        for (hi <- Seq(0x10, 0x20, 0x40, 0x80, 0xF0, 0xA0)) {
          vectors += 1
          val got = cvtBits(dut, code4 | hi, scale, DataFormat.MXE2M1)
          if (got != base) bad.add(f"[hibit] code=0x${code4 | hi}%02X scale=$scale got=0x$got%08X base=0x$base%08X")
        }
      }

      // ② 消费拍（consumeValid=true，守护使能）下 E2M1 全 16 码均不得 $fatal：
      //    E2M1 无 inf/nan 编码，0x7/0xF = ±6.0 是最大有限值。守护若误判会在此杀死仿真。
      for (code <- 0 until 16; scale <- Seq(127, 200, 255)) {
        vectors += 1
        val hw = cvtBits(dut, code, scale, DataFormat.MXE2M1, consumeValid = true, step = true)
        if (hw != e2m1ExpectedBits(code, scale))
          bad.add(f"[guard-on] code=0x$code%X scale=$scale hw=0x$hw%08X")
      }

      // ③ 同一 hasMx4=true DUT 上 fp8 两路必须仍 == 改动前 legacy 模型（第三路 Mux 不扰动 fp8）
      for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2);
           code <- 0 until 256; scale <- scalesCross) {
        vectors += 1
        val isE5 = fmt == DataFormat.MXE5M2
        val legacy = if (isE5) legacyMxFp32Bits(code, scale, 5, 2, MxFormatParams.E5M2.bias, nanNeedMantAllOne = false)
                     else legacyMxFp32Bits(code, scale, MxFormatParams.E4M3.expW, MxFormatParams.E4M3.mantW,
                                           MxFormatParams.E4M3.bias, nanNeedMantAllOne = true)
        // fp8 inf/nan 编码在消费拍会 $fatal（既有语义，非本次改动）⟹ 交叉回归用非消费拍
        val hw = cvtBits(dut, code, scale, fmt, consumeValid = false)
        if (hw != legacy)
          bad.add(f"[fp8-cross] fmt=$fmt code=0x$code%02X scale=$scale hw=0x$hw%08X legacy=0x$legacy%08X")
      }

      // ④ 非消费拍下 fp8 的 inf/nan 编码仍必须饱和到 ±fp32Max（既有语义未被守护改动破坏）
      for ((fmt, code) <- Seq((DataFormat.MXE4M3, 0x7F), (DataFormat.MXE4M3, 0xFF),
                              (DataFormat.MXE5M2, 0x7C), (DataFormat.MXE5M2, 0xFC));
           scale <- Seq(127, 200)) {
        vectors += 1
        val hw = cvtBits(dut, code, scale, fmt)
        val sgn = ((code >>> 7) & 1).toLong << 31
        if (hw != (sgn | Fp32MaxBits))
          bad.add(f"[fp8-infnan] fmt=$fmt code=0x$code%02X scale=$scale hw=0x$hw%08X")
      }
    }
    bad.count should be(0)
    println(f"[$TAG] E2M1 高位/守护 + fp8 交叉回归通过：$vectors%d 向量，失配 ${bad.count}%d")
  }

  // ==========================================================================
  behavior of "Fp32.mxE4M3ToFp32 / mxE5M2ToFp32 — fp8 位级非回归 (Phase 0 硬门槛)"

  it should "生产路径 hasMx4=false：E4M3/E5M2 全 256 编码 × 16 scale 逐位 == 改动前 legacy 模型 == 金锚" in {
    val bad = new Mismatch
    var vectors = 0
    // 默认实例（= Phase 0 阶段全部生产实例化点的形态）：hasMx4 缺省 false
    simulate(new HiveCvtOpMx()) { dut =>
      resetSeq(dut)
      for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2);
           code <- 0 until 256; scale <- scalesRep) {
        vectors += 1
        val isE5 = fmt == DataFormat.MXE5M2
        val (expW, mantW, bias, nanAll) =
          if (isE5) (MxFormatParams.E5M2.expW, MxFormatParams.E5M2.mantW, MxFormatParams.E5M2.bias, false)
          else (MxFormatParams.E4M3.expW, MxFormatParams.E4M3.mantW, MxFormatParams.E4M3.bias, true)
        val hw = cvtBits(dut, code, scale, fmt)
        val legacy = legacyMxFp32Bits(code, scale, expW, mantW, bias, nanAll)
        val golden = if (isE5) HiveSimCommon.mxE5M2ToFloat(code, scale)
                     else HiveSimCommon.mxE4M3ToFloat(code, scale)
        val clue = f"[fp8 fmt=$fmt code=0x$code%02X scale=$scale%3d]"
        // ① DUT 位模式 == 改动前算法位模式（bit-exact 非回归）
        if (hw != legacy) bad.add(f"$clue bits hw=0x$hw%08X legacy=0x$legacy%08X")
        // ② DUT 数值 == 金锚（改动后金锚仍与硬件同源）
        if (!(intBitsToFloat(hw.toInt).toDouble == golden))
          bad.add(f"$clue golden hw=${intBitsToFloat(hw.toInt).toDouble} anchor=$golden")
      }
    }
    bad.count should be(0)
    println(f"[$TAG] fp8 生产路径非回归通过：$vectors%d 向量（2 格式 × 256 编码 × ${scalesRep.size} scale），失配 ${bad.count}%d")
  }

  it should "金锚自身非回归（纯软件穷举）：mxToFloat fp8 默认实参 256×256×2 逐值 == legacy 模型" in {
    val bad = new Mismatch
    var vectors = 0
    for (fmt <- Seq(DataFormat.MXE4M3, DataFormat.MXE5M2); code <- 0 until 256; scale <- 0 until 256) {
      vectors += 1
      val isE5 = fmt == DataFormat.MXE5M2
      val (expW, mantW, bias, nanAll) =
        if (isE5) (MxFormatParams.E5M2.expW, MxFormatParams.E5M2.mantW, MxFormatParams.E5M2.bias, false)
        else (MxFormatParams.E4M3.expW, MxFormatParams.E4M3.mantW, MxFormatParams.E4M3.bias, true)
      val golden = if (isE5) HiveSimCommon.mxE5M2ToFloat(code, scale)
                   else HiveSimCommon.mxE4M3ToFloat(code, scale)
      val legacyVal = intBitsToFloat(legacyMxFp32Bits(code, scale, expW, mantW, bias, nanAll).toInt).toDouble
      if (!(golden == legacyVal))
        bad.add(f"[anchor-drift] fmt=$fmt code=0x$code%02X scale=$scale golden=$golden legacy=$legacyVal")
      // 显式实参与默认实参必须等价（noInfNan=false / subnormalFtz=true）
      val explicit = HiveSimCommon.mxToFloat(code, scale, expW, mantW, bias, nanAll,
        noInfNan = false, subnormalFtz = true)
      if (!(explicit == golden))
        bad.add(f"[anchor-default] fmt=$fmt code=0x$code%02X scale=$scale explicit=$explicit golden=$golden")
    }
    bad.count should be(0)
    println(f"[$TAG] 金锚非回归通过：$vectors%d 向量（纯软件，无仿真），失配 ${bad.count}%d")
  }

  it should "E2M1 金锚 == 语义锚（纯软件穷举 16×256）：同批泛化未偏离张量语义" in {
    val bad = new Mismatch
    var vectors = 0
    for (code <- 0 until 16; scale <- 0 until 256) {
      vectors += 1
      val golden = HiveSimCommon.mxE2M1ToFloat(code, scale)
      val anchor = e2m1ExpectedValue(code, scale)
      val magView = e2m1MagScaled(code, scale)
      val expField = (scale & 0xFF) + e2m1Decode(code)._2
      val clamped = !e2m1Decode(code)._1 && (expField < 1 || expField > 254)
      if (!(golden == anchor))
        bad.add(f"[e2m1-anchor] code=0x$code%X scale=$scale golden=$golden anchor=$anchor")
      if (!clamped && !(golden == magView))
        bad.add(f"[e2m1-mag] code=0x$code%X scale=$scale golden=$golden mag=$magView")
      // 幅值域硬锚：scale=127（中性）时 16 码必须恰为 {±0,±0.5,±1,±1.5,±2,±3,±4,±6}
      if (scale == 127) {
        val expect = if (code < 8) Seq(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0)(code)
                     else -Seq(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0)(code - 8)
        if (!(golden == expect)) bad.add(f"[e2m1-domain] code=0x$code%X golden=$golden expect=$expect")
      }
    }
    bad.count should be(0)
    println(f"[$TAG] E2M1 金锚对账通过：$vectors%d 向量（16 编码 × 256 scale，纯软件），失配 ${bad.count}%d")
  }

  // ==========================================================================
  behavior of "HiveCvtOpMx.hasMx4 编译期门控 — Phase 0 dormant 自证"

  /** CvtOpMx 内 `DataFormat.isMx4(io.fmt)` 的 CHIRRTL 形态（枚举编码 + 最小位宽派生，非裸字面量）。
    * 【端口名限定是必需的】HiveWorkUnit 层的 `DataFormat.isMx(io.aFmt)`（HiveEnum L9）本就含
    * MXE2M1 比较、早于本次改动既有，故 WorkUnit 级门控自证必须锚在 `io.fmt` 上。 */
  private val mx4SelChirrtl = {
    val code = DataFormat.MXE2M1.litValue
    s"eq(io.fmt, UInt<${code.bitLength}>(0h${code.toString(16)}))"
  }
  /** E2M1 独有的 exp 位域抽取 x(expW+mantW-1, mantW) = x(2,1)；fp8 两档为 x(6,3)/x(6,2) */
  private val e2m1ExpField = s"bits(io.in, ${p4.expW + p4.mantW - 1}, ${p4.mantW})"
  /** E2M1 独有的 sign 位抽取 x(expW+mantW, ·) = x(3,3)；fp8 为 x(7,7) */
  private val e2m1SignBit = s"bits(io.in, ${p4.expW + p4.mantW}, ${p4.expW + p4.mantW})"
  /** E2M1 独有的尾数左对齐补零宽度 23-mantW = 22；fp8 两档为 20/21 */
  private val e2m1MantPad = s"UInt<${23 - p4.mantW}>"

  it should "hasMx4 缺省 false：CHIRRTL/RTL 与显式 false 逐字相同，且不含任何 E2M1 解码结构" in {
    val firOff = ChiselStage.emitCHIRRTL(new HiveCvtOpMx())
    val firOffExplicit = ChiselStage.emitCHIRRTL(new HiveCvtOpMx(hasMx4 = false))
    val firOn = ChiselStage.emitCHIRRTL(new HiveCvtOpMx(hasMx4 = true))

    // ① 缺省实参 == 显式 false（默认值语义；Phase 0 全部生产实例化点走此形态）
    firOff should be(firOffExplicit)
    // ② dormant：默认实例的 CHIRRTL 中 4 个 E2M1 独有结构标记必须【全部不存在】
    for (marker <- Seq(mx4SelChirrtl, e2m1ExpField, e2m1SignBit))
      withClue(s"dormant 标记 '$marker' ") { firOff should not include marker }
    firOff should not include e2m1MantPad
    // ③ 激活：hasMx4=true 时上述标记必须【全部出现】，且 CHIRRTL 结构确实不同
    for (marker <- Seq(mx4SelChirrtl, e2m1ExpField, e2m1SignBit, e2m1MantPad))
      withClue(s"激活标记 '$marker' ") { firOn should include(marker) }
    firOn should not be firOff
    // ④ 发射级自证：SystemVerilog 亦随门控变化（Phase 4 激活路径可发射）
    val svOff = ChiselStage.emitSystemVerilog(new HiveCvtOpMx())
    val svOn = ChiselStage.emitSystemVerilog(new HiveCvtOpMx(hasMx4 = true))
    svOn should not be svOff
    println(f"[$TAG] 门控自证：dormant CHIRRTL ${firOff.length}%d 字符 / 激活 ${firOn.length}%d 字符")
  }

  it should "HiveWorkUnit：supportedFmts 不含 MXE2M1 时 hasMx4=false（CvtOpMx dormant）；含 MXE2M1 时可 elaboration" in {
    // Phase 0 生产形态：supportedFmts 仅 MXE4M3/MXE5M2 ⟹ hasMx4=false ⟹ 4 个 HiveCvtOpMx
    // 与改动前逐位一致（该结论由 verilog/verilog_mx 零 diff 独立复核，此处做发射级冒烟）
    val wuFp8 = ChiselStage.emitCHIRRTL(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2)))
    for (marker <- Seq(mx4SelChirrtl, e2m1ExpField, e2m1SignBit))
      withClue(s"WorkUnit dormant 标记 '$marker' ") { wuFp8 should not include marker }

    // Phase 4 前置就绪性：加入 MXE2M1 后 hasMx4=true，第三路解码必须被 elaboration 出来。
    // 注意 hasMx 谓词【未】改（Phase 1 范围）：本配置 hasMx 由 MXE4M3/MXE5M2 满足。
    val wuFp4 = ChiselStage.emitCHIRRTL(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)))
    for (marker <- Seq(mx4SelChirrtl, e2m1ExpField, e2m1SignBit))
      withClue(s"WorkUnit 激活标记 '$marker' ") { wuFp4 should include(marker) }
    wuFp4 should not be wuFp8
    // SystemVerilog 可发射（含 4 个 hasMx4=true 的 CvtOpMx）
    val svFp4 = ChiselStage.emitSystemVerilog(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)))
    svFp4 should include("module HiveWorkUnit")
  }
}
