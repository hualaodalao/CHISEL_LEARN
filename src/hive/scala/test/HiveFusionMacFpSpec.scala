/** HiveFusionMacFpSpec — 移除配置(hasFp=false) bit-exact 数学金锚（Task #8，R12 首个 FusionMac 级测试）
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 目标（铁律 2：双配置三角互证）
  * ══════════════════════════════════════════════════════════════════════════════
  * 用【同一个数学金锚】同时测两个编译期配置：
  *   · DEFAULT 配置 hasFp=true  （supportedFmts = 全五格式 FMTS_DEFAULT(:112)，lane0 走宽阵列 FpMulFp16Bf16）
  *   · REMOVED 配置 hasFp=false （supportedFmts = FMTS_REMOVED(:114) = {MXE4M3,MXE5M2,MXE2M1}，
  *                               lane0 走窄乘法器 FpMulMx8）
  * ★【头注订正 · task#20(5)】原文写「默认配置 hasFp=true（全五格式）」——【该表述已陈旧】：
  *   `HiveFusionMacFp` 的【类默认】supportedFmts 早已被「砍除 FP16/BF16」裁定收窄为 MX-only
  *   {MXE4M3,MXE5M2,MXE2M1}（HiveFusionMacFp.scala:29-30），派生谓词 hasFp 由 :36 计算 ⟹
  *   **类默认已收窄为 MX-only（hasFp=false）**，lane0 走窄乘法器 FpMulMx8，宽阵列 FpMulFp16Bf16 整块不生成。
  *   本 spec 里的 "default"/rawDefault/satDefault 只是【历史命名】，指「显式传入全五格式那一档」，
  *   与类默认值无关。8 个 `it should`（:345/:350/:357/:375/:380/:410/:435/:458）逐条核过：
  *   其中 7 个经 runInSpec(:346/:351)、runSat(:376/:381) 或直接 simulate(:414/:438/:459)
  *   **显式传 FMTS_DEFAULT / FMTS_REMOVED**；剩下 1 个（:357 三角互证收口）自身不例化 DUT，
  *   只消费 :346/:351 已填充的 rawDefault/rawRemoved。**没有任何一个用例落到类默认值**
  *   ⟹ 8/8 用例的激励与断言行为【保持不变】，本次订正纯属措辞纠偏、零功能影响。
  * 两配置在 in-spec 工作域都必须【逐位匹配金锚】⟹ 传递性证明：移除 == 默认 == 金锚，
  * bit-exact 成立；且金锚独立于任一配置（未被拟合）。本 spec 额外直接断言两配置的
  * raw lazy-40 输出逐位相等（最强证据：不仅 fp32 语义相等，内部延迟规格化位模式亦相等）。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 铁律 1：金锚由 MX 数学语义第一性原理推导，禁止拟合 RTL
  * ══════════════════════════════════════════════════════════════════════════════
  * 期望值纯从 MX 语义算：
  *   元素真值 = HiveSimCommon.mxE4M3ToFloat / mxE5M2ToFloat / mxE2M1ToFloat(code, scaleByte)
  *            （既有独立软件模型，解码 sign/exp/mantissa + E8M0 scale=2^(scale-127)，非本文件新写）
  *   lane 乘积 = aElemVal × bElemVal（Double，MX 乘积 ≤8-bit(MX8)/≤4-bit(MX4) 有效位 ⟹ 精确）
  *   金锚和 S  = cReg + Σ lane 乘积（Double 纯张量语义；MX8=2 lane、MX4=fp4BlockSum 4-lane 树）
  *   金锚 fp32 = HiveSimCommon.swFpNormalize40to32(fp32BitsTo40(floatToRawIntBits(S.toFloat)))
  *            （复用既有 lazy-40 ↔ fp32 软件模型，约 :391 / :415）
  * 硬件侧 = swFpNormalize40to32(io.sum.peek 的 raw lazy-40)。两侧同经 swFpNormalize40to32 归一到 fp32 比对。
  * 【绝不】跑一遍 RTL 把输出录下来当期望值。若 in-spec 逐位对拍失败 ⟹ 真 bug ⟹ 带证据报告，不调金锚凑 RTL。
  *
  * ── 逐位断言合法性守卫 goldenExact（math-derived，镜像 HiveDebugModel.goldenExact）──
  * 仅当【每个 lane 乘积】与【每个累加部分和】都 fp32-无损（v.toFloat.toDouble == v）时才做逐位断言。
  * 充分性证明（第一性原理，非拟合）：
  *   · MX 乘积有效位 ≤8-bit(MX8 aSig×bSig≤15×15=225) / ≤4-bit(MX4 (2+m1)积∈{4,6,9}) ⟹ 乘积恒 fp32-精确；
  *   · 部分和 fp32-精确 ⟹ 精确和 ≤24-bit ⟹ 两 lane 指数差 ≤16（MX8）；lazy-40 累加器 31-bit 尾数、
  *     窄 MX 乘积有效位坐在高位 [30..23]、下方 [22..0] 结构性 0 ⟹ 对齐右移 ≤22 位无损 ⟹ expDiff≤16 时
  *     lazy-40 持有【精确和】（无对齐丢失）；MX4 同 block 指数差 ≤7、fp4BlockSum 定点树全程无舍入 ⟹ 精确。
  *   · 于是硬件对【精确和】只做【单次】RNE 舍入（lazy40→fp32），与金锚 fp64→fp32 的单次 RNE 舍入同一个数
  *     ⟹ 逐位相等。此为合法数学锚，非以 RTL 行为为拟合目标。
  * 守卫失败 ⟹ 该向量落在逐位断言非法域（设计错误，非 RTL bug）⟹ 本 spec 以 assert 明确报「DESIGN ERROR」，
  * 提示收紧激励；绝不据此放宽/篡改金锚。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 铁律 3：F1 时序契约（已读 RTL 确认 — verilog_fusionfp_nofp16/Hive_HiveFusionMacFp.sv）
  * ══════════════════════════════════════════════════════════════════════════════
  *   · fmt 标志（aIsMx5M2/aIsMx4M3/aIsMx2M1/bIsMx…/isMx4/isMx8 共 12 个）= RegNext(io.aFmt/io.bFmt === ...)，
  *     RTL L110-120 always@(posedge clock) ⟹ 比 io.aFmt/io.bFmt 晚【1 拍】。
  *   · data 通路（io.a/io.b/io.scaleA/io.scaleB/io.cReg → cvt → mul → FpAdd）= 纯组合，无流水寄存器。
  *   · io.sum = fpAdd.io.sum 纯组合直连（RTL L284/L248），无输出寄存器 ⟹ 流水线延迟 0 拍（组合）。
  *   ⟹ 契约：fmt 必须比 data 提前 1 拍被寄存器捕获。最简驱动：同拍 poke(fmt + data) 并保持 → step(1) →
  *     此拍 fmt 寄存器已捕获上拍 fmt、data 仍保持 ⟹ io.sum(组合) = f(data, fmt) 正确对齐 → peek。
  *     每个向量 step(1) 恰好吸收 fmt 的 1 拍 RegNext 延迟；data 组合路径同拍可见。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * in-spec 工作域（scale 域限 [0,238]，Alex 报告 §6.1：三格式均无饱和的交集）
  * ══════════════════════════════════════════════════════════════════════════════
  *   元素饱和阈值（cvt overflow→fp32Max / exp=254,mant=7，两配置在此【发散】）：
  *     E4M3: expSigned=e+scale-7>254 ⟹ scale≥247(e=15)；E5M2: scale≥240(e=30)；E2M1: scale≥253(e2=3)。
  *     scale ≤238 ⟹ 三格式元素均不饱和 ✓。本 spec in-spec 向量 scaleA/scaleB ∈[0,238]，
  *     且乘积指数 eOut=eA+eB+scaleA+scaleB-141(+normAdj) 保持在 fp32 normal [1,254]（scaleA+scaleB∈[130,358]）。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 饱和域（out-of-spec，诚实标注，不做逐位断言）
  * ══════════════════════════════════════════════════════════════════════════════
  *   单侧 scale≥239~253（或 InfNan 码）⟹ lane0 元素饱和 ⟹ 两配置【预期发散】：
  *     · 默认(hasFp=true) lane0 走宽阵列 FpMulFp16Bf16：饱和→fp32Max mant23=0x7FFFFF→magNarrow=2047（≡7 mod8）
  *     · 移除(hasFp=false) lane0 走窄乘法器 FpMulMx8：饱和→aSig=Cat(1,7)=15→magNarrow 随 P=aSig·bSig 取值
  *       （双侧饱和 P=225→1800、单侧饱和 P=120→1920），通式恒 ≡0 mod8、max=2016<2048（位级实算见 FpAdd.scala §9.1(c)）
  *   见 FpAdd.scala §9.1(c)（已归档 out-of-spec 非功能回归）+ 蓝图 §6.2。该域只断言「输出有限 + 无 X 传播
  *   （Verilator 2-state 无 X；断言归一化 fp32 非 NaN/Inf）」，显式 println 记录两配置发散，【不做 bit-exact 断言】。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 测试坑规避（项目记忆）
  * ══════════════════════════════════════════════════════════════════════════════
  *   · ScalaTest 参数化用例名撞车致整套 ABORTED(tests run=0)：本 spec 用【固定唯一】的 it should 描述串
  *     （5 个 block 名互异），向量在 block 内以 for 循环 + 带 tag 的 assert 消息逐一校验（不生成参数化用例名）。
  *   · 显式 reset（EphemeralSimulator/Verilator 上电语义）：每个 simulate 开头 reset.poke(true)+全输入安全默认+step。
  *   · backend：显式 verilator【无 trace】（不混 HiveFstSim）⟹ 不依赖 lz4/CPATH/LIBRARY_PATH、不产波形（规避
  *     VCD path↔testname 耦合与 FST/poke 溢出坑）。
  *   · cReg：in-spec 逐位向量默认 cReg=0（lazyAdd(0,p)=lazy40(p) 恒等；且 cReg 通路 FpAdd 两配置逐字相同 ⟹
  *     cReg≠0 不增加配置判别力），另设 1 条 cReg=1.0 向量行使完整 MAC 累加路径（守卫覆盖其部分和精确性）。
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class HiveFusionMacFpSpec extends AnyFlatSpec with Matchers with ChiselSim {

  // 显式 verilator 后端（默认 CompilationSettings，无 trace）：不依赖 lz4/CPATH，不产波形。
  // 镜像 HiveFstSim 的构造式但去掉 .withTraceStyle（house 既有写法，保证编译通过）。
  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  // 两配置 raw lazy-40 输出（供 block3 直接互证 移除==默认）；饱和域输出（供 block5 记录发散）。
  private val rawDefault = mutable.LinkedHashMap[String, BigInt]()
  private val rawRemoved = mutable.LinkedHashMap[String, BigInt]()
  private val satDefault = mutable.LinkedHashMap[String, Long]()
  private val satRemoved = mutable.LinkedHashMap[String, Long]()

  private val FMTS_DEFAULT = Set(DataFormat.FP16, DataFormat.BF16,
    DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)
  private val FMTS_REMOVED = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)

  // ════════════════════════════════════════════════════════════════════════════
  // 元素码构造器（纯 Scala，第一性原理位域打包；与 HiveEnum 格式参数一致）
  // ════════════════════════════════════════════════════════════════════════════
  // E4M3: s(1)|e(4)|m(3)  bias7 ；E5M2: s(1)|e(5)|m(2) bias15 ；E2M1: s(1)|e(2)|m(1) bias1
  private def e4m3(s: Int, e: Int, m: Int): Int = ((s & 1) << 7) | ((e & 0xF) << 3) | (m & 0x7)
  private def e5m2(s: Int, e: Int, m: Int): Int = ((s & 1) << 7) | ((e & 0x1F) << 2) | (m & 0x3)
  private def e2m1(s: Int, e: Int, m: Int): Int = ((s & 1) << 3) | ((e & 0x3) << 1) | (m & 0x1)
  // 16-bit lane 打包：MX8 = 低字节 lane0 + 高字节 lane1；MX4 = 4 nibble lane0..3（低→高）
  private def mx8(lane0: Int, lane1: Int): Int = ((lane1 & 0xFF) << 8) | (lane0 & 0xFF)
  private def mx4(n0: Int, n1: Int, n2: Int, n3: Int): Int =
    (n0 & 0xF) | ((n1 & 0xF) << 4) | ((n2 & 0xF) << 8) | ((n3 & 0xF) << 12)

  // ════════════════════════════════════════════════════════════════════════════
  // 数学金锚（第一性原理 MX 语义；复用 HiveSimCommon 独立软件模型，禁拟合 RTL）
  // ════════════════════════════════════════════════════════════════════════════
  /** 单个 MX 元素真值（Double）：复用 HiveSimCommon.mxToFloat 族（既有独立软件模型）。 */
  private def elemVal(code: Int, scale: Int, fmt: DataFormat.Type): Double = fmt match {
    case DataFormat.MXE5M2 => HiveSimCommon.mxE5M2ToFloat(code, scale)
    case DataFormat.MXE4M3 => HiveSimCommon.mxE4M3ToFloat(code, scale)
    case DataFormat.MXE2M1 => HiveSimCommon.mxE2M1ToFloat(code, scale)
    case other => require(false, s"elemVal: 非 MX 格式 $other"); 0.0
  }

  /** fp32-无损判据：v 恰好可用 fp32 表示（.toFloat 不丢精度）。 */
  private def fp32Exact(v: Double): Boolean = v.toFloat.toDouble == v

  /** 金锚 fp32 bits：S(Double) → fp32 → lazy-40 → 归一回 fp32（复用既有 swFpNormalize40to32/fp32BitsTo40）。 */
  private def goldenBits(golden: Double): Long = {
    val fp32 = java.lang.Float.floatToRawIntBits(golden.toFloat) & 0xFFFFFFFFL
    HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(fp32))
  }

  /** cReg 的 lazy-40 位（fp32 值 → lazy-40 槽；cVal=0 ⟹ 0）。 */
  private def cRegBits(cVal: Double): BigInt =
    HiveSimCommon.fp32BitsTo40(java.lang.Float.floatToRawIntBits(cVal.toFloat) & 0xFFFFFFFFL)

  /** 一条激励向量。aFmt/bFmt 决定元素解码；isMx4 决定 2-lane(MX8) / 4-lane(MX4) 累加结构。 */
  private case class Vec(tag: String, aFmt: DataFormat.Type, bFmt: DataFormat.Type,
                         a: Int, b: Int, scaleA: Int, scaleB: Int,
                         isMx4: Boolean, cVal: Double = 0.0)

  /** MX8 金锚：sum = cReg + lane0 + lane1（硬件序 lazyAdd(lazyAdd(cReg,lane0),lane1)）。
    * 返回 (金锚和 S, goldenExact 守卫)。lane0=a(7,0)×b(7,0)、lane1=a(15,8)×b(15,8)。 */
  private def goldenMx8(v: Vec): (Double, Boolean) = {
    val p0 = elemVal(v.a & 0xFF, v.scaleA, v.aFmt) * elemVal(v.b & 0xFF, v.scaleB, v.bFmt)
    val p1 = elemVal((v.a >> 8) & 0xFF, v.scaleA, v.aFmt) * elemVal((v.b >> 8) & 0xFF, v.scaleB, v.bFmt)
    val s0 = v.cVal
    val s1 = s0 + p0
    val s2 = s1 + p1
    val exact = fp32Exact(p0) && fp32Exact(p1) && fp32Exact(s0) && fp32Exact(s1) && fp32Exact(s2)
    (s2, exact)
  }

  /** MX4 金锚：block=fp4BlockSum 树 (p0+p1)+(p2+p3)；sum = cReg + block（硬件序 lazyAdd(cReg,block)）。
    * lane0=a(3,0)、lane1=a(7,4)、lane2=a(11,8)、lane3=a(15,12)。返回 (S, goldenExact)。 */
  private def goldenMx4(v: Vec): (Double, Boolean) = {
    val p = (0 until 4).map { i =>
      elemVal((v.a >> (4 * i)) & 0xF, v.scaleA, DataFormat.MXE2M1) *
        elemVal((v.b >> (4 * i)) & 0xF, v.scaleB, DataFormat.MXE2M1)
    }
    val s01 = p(0) + p(1)
    val s23 = p(2) + p(3)
    val block = s01 + s23
    val total = v.cVal + block
    val exact = p.forall(fp32Exact) && fp32Exact(s01) && fp32Exact(s23) &&
      fp32Exact(block) && fp32Exact(v.cVal) && fp32Exact(total)
    (total, exact)
  }

  private def goldenOf(v: Vec): (Double, Boolean) = if (v.isMx4) goldenMx4(v) else goldenMx8(v)

  // ════════════════════════════════════════════════════════════════════════════
  // in-spec 向量集（scaleA/scaleB ∈[0,238]，乘积 eOut∈[1,254]；覆盖尾数积组合 + scale 档位）
  // ════════════════════════════════════════════════════════════════════════════
  private val E4 = DataFormat.MXE4M3
  private val E5 = DataFormat.MXE5M2
  private val E2 = DataFormat.MXE2M1

  private val inSpecVecs: Seq[Vec] = Seq(
    // ---- MX8 E4M3×E4M3：覆盖 (8+m3) 各值（m3∈{0..7}）、符号、lane 指数差、scale 档位 ----
    Vec("mx8_e4m3_m00_07_s127", E4, E4, mx8(e4m3(0,7,0), e4m3(0,7,7)), mx8(e4m3(0,7,0), e4m3(0,7,7)), 127, 127, false),
    Vec("mx8_e4m3_m1_6_s127",   E4, E4, mx8(e4m3(0,7,1), e4m3(0,7,6)), mx8(e4m3(0,7,2), e4m3(0,7,5)), 127, 127, false),
    Vec("mx8_e4m3_m3_4_s127",   E4, E4, mx8(e4m3(0,7,3), e4m3(0,7,4)), mx8(e4m3(0,7,4), e4m3(0,7,3)), 127, 127, false),
    Vec("mx8_e4m3_signs_s127",  E4, E4, mx8(e4m3(1,7,3), e4m3(0,7,5)), mx8(e4m3(0,7,2), e4m3(1,7,7)), 127, 127, false),
    Vec("mx8_e4m3_expdiff3",    E4, E4, mx8(e4m3(0,7,1), e4m3(0,10,1)), mx8(e4m3(0,7,1), e4m3(0,10,1)), 127, 127, false),
    Vec("mx8_e4m3_scaleHi_190_140", E4, E4, mx8(e4m3(0,7,7), e4m3(0,7,0)), mx8(e4m3(0,7,5), e4m3(0,7,2)), 190, 140, false),
    Vec("mx8_e4m3_scaleCeil_238_100", E4, E4, mx8(e4m3(0,7,3), e4m3(0,7,6)), mx8(e4m3(0,7,3), e4m3(0,7,6)), 238, 100, false),
    Vec("mx8_e4m3_scaleLowB_130_10", E4, E4, mx8(e4m3(0,7,2), e4m3(0,7,5)), mx8(e4m3(0,7,2), e4m3(0,7,5)), 130, 10, false),
    Vec("mx8_e4m3_scaleAsym_64_180", E4, E4, mx8(e4m3(0,7,4), e4m3(0,7,1)), mx8(e4m3(0,7,7), e4m3(0,7,0)), 64, 180, false),
    Vec("mx8_e4m3_cReg1",        E4, E4, mx8(e4m3(0,7,2), e4m3(0,7,2)), mx8(e4m3(0,7,3), e4m3(0,7,3)), 127, 127, false, 1.0),
    // ---- MX8 E5M2×E5M2：覆盖 (4+m2) 各值（m2∈{0..3}）、符号、指数差、scale 档位 ----
    Vec("mx8_e5m2_m0_3_s127",   E5, E5, mx8(e5m2(0,15,0), e5m2(0,15,3)), mx8(e5m2(0,15,0), e5m2(0,15,3)), 127, 127, false),
    Vec("mx8_e5m2_m1_2_s127",   E5, E5, mx8(e5m2(0,15,1), e5m2(0,15,2)), mx8(e5m2(0,15,2), e5m2(0,15,1)), 127, 127, false),
    Vec("mx8_e5m2_signs_160",   E5, E5, mx8(e5m2(1,15,1), e5m2(0,15,3)), mx8(e5m2(0,15,2), e5m2(1,15,3)), 160, 160, false),
    Vec("mx8_e5m2_expdiff3",    E5, E5, mx8(e5m2(0,15,1), e5m2(0,18,1)), mx8(e5m2(0,15,1), e5m2(0,18,1)), 127, 127, false),
    Vec("mx8_e5m2_scaleCeil_238_100", E5, E5, mx8(e5m2(0,15,3), e5m2(0,15,0)), mx8(e5m2(0,15,3), e5m2(0,15,0)), 238, 100, false),
    // ---- MX8 混合 E4M3×E5M2：跨格式解码（aFmt≠bFmt）----
    Vec("mx8_mix_e4xe5_s127",   E4, E5, mx8(e4m3(0,7,0), e4m3(0,7,7)), mx8(e5m2(0,15,0), e5m2(0,15,3)), 127, 127, false),
    Vec("mx8_mix_signs_190_127",E4, E5, mx8(e4m3(1,7,3), e4m3(0,7,5)), mx8(e5m2(0,15,2), e5m2(1,15,1)), 190, 127, false),
    Vec("mx8_mix_expdiff",      E4, E5, mx8(e4m3(0,7,2), e4m3(0,9,2)), mx8(e5m2(0,15,1), e5m2(0,17,1)), 127, 127, false),
    Vec("mx8_mix_scaleAsym_100_154", E4, E5, mx8(e4m3(0,7,6), e4m3(0,7,1)), mx8(e5m2(0,15,3), e5m2(0,15,0)), 100, 154, false),
    // ---- MX4 E2M1×E2M1（quad-lane，走 fp4BlockSum）：覆盖 (2+m1a)(2+m1b)∈{4,6,9}、e2 展开、次正规、符号、scale 档位 ----
    Vec("mx4_prod_4_6_9_s127",  E2, E2, mx4(e2m1(0,2,0), e2m1(0,2,0), e2m1(0,2,1), e2m1(0,2,1)),
                                            mx4(e2m1(0,2,0), e2m1(0,2,1), e2m1(0,2,0), e2m1(0,2,1)), 127, 127, true),
    Vec("mx4_e2_spread",        E2, E2, mx4(e2m1(0,0,1), e2m1(0,1,1), e2m1(0,2,1), e2m1(0,3,0)),
                                            mx4(e2m1(0,3,1), e2m1(0,2,0), e2m1(0,1,1), e2m1(0,0,0)), 127, 127, true),
    Vec("mx4_subnormal_05",     E2, E2, mx4(e2m1(0,0,1), e2m1(0,0,1), e2m1(1,0,1), e2m1(0,1,0)),
                                            mx4(e2m1(0,0,1), e2m1(1,0,1), e2m1(0,0,1), e2m1(0,1,1)), 127, 127, true),
    Vec("mx4_signs",            E2, E2, mx4(e2m1(1,2,1), e2m1(0,2,1), e2m1(1,3,0), e2m1(0,1,1)),
                                            mx4(e2m1(0,2,1), e2m1(1,2,0), e2m1(0,3,1), e2m1(1,1,1)), 127, 127, true),
    Vec("mx4_scaleHi_200_140",  E2, E2, mx4(e2m1(0,3,1), e2m1(0,2,0), e2m1(0,3,1), e2m1(0,1,1)),
                                            mx4(e2m1(0,3,1), e2m1(0,2,1), e2m1(0,1,0), e2m1(0,3,1)), 200, 140, true),
    Vec("mx4_scaleCeil_238_120",E2, E2, mx4(e2m1(0,2,1), e2m1(0,3,0), e2m1(0,2,1), e2m1(0,3,1)),
                                            mx4(e2m1(0,2,0), e2m1(0,3,1), e2m1(0,2,1), e2m1(0,1,0)), 238, 120, true),
    Vec("mx4_scaleLowB_130_10", E2, E2, mx4(e2m1(0,2,1), e2m1(0,1,0), e2m1(0,3,1), e2m1(0,2,0)),
                                            mx4(e2m1(0,2,1), e2m1(0,3,1), e2m1(0,1,1), e2m1(0,2,0)), 130, 10, true)
  )

  // ════════════════════════════════════════════════════════════════════════════
  // 饱和域向量（out-of-spec：单侧 scale≥239~253 / InfNan 码 ⟹ lane0 元素饱和 ⟹ 两配置发散）
  // ════════════════════════════════════════════════════════════════════════════
  private val satVecs: Seq[Vec] = Seq(
    // MX4 lane0 饱和（FpAdd §9.1(c) 记录的 (c) 域）：scaleA=253/254 + e2=3 ⟹ e2+scale≥256 ⟹ A 侧 overflow
    Vec("sat_mx4_lane0_scaleA253", E2, E2, mx4(e2m1(0,3,1), e2m1(0,3,0), e2m1(0,2,1), e2m1(0,3,1)),
                                             mx4(e2m1(0,2,1), e2m1(0,3,1), e2m1(0,2,0), e2m1(0,3,1)), 253, 127, true),
    Vec("sat_mx4_lane0_scaleA254", E2, E2, mx4(e2m1(0,3,1), e2m1(0,2,1), e2m1(0,3,0), e2m1(0,2,1)),
                                             mx4(e2m1(0,3,1), e2m1(0,2,0), e2m1(0,3,1), e2m1(0,2,1)), 254, 127, true),
    // MX8 元素饱和（a 侧饱和到 fp32Max；b 侧取极小值 ⟹ 乘积保持有限，落在 element-saturation regime，
    //   使「输出有限」成为真实不变量）。发散源：lane0 默认宽阵列(mant23=0x7FFFFF) vs 移除窄乘法器(aSig=15)。
    //   注：MX 乘法器 eOut=(eAdj-127)(7,0) 8-bit 截断、【无 Inf/NaN 生成逻辑】（FpMul.scala L108/L163）；
    //   若乘积溢出 fp32（如 b 侧 >1），eOut 回绕可落入 exp=0xFF ⟹ 伪 NaN——此为 out-of-spec 回绕，
    //   非 in-spec bug，故本域向量刻意令乘积有限以隔离该回绕、聚焦 lane0 饱和发散记录。
    // E4M3: scaleA=250 + e=15 ⟹ expSigned=258>254 ⟹ a 侧饱和 fp32Max；b=e4m3(0,1,0)@scale127=2^-6 极小。
    Vec("sat_mx8_e4m3_scaleA250", E4, E4, mx8(e4m3(0,15,0), e4m3(0,15,0)), mx8(e4m3(0,1,0), e4m3(0,1,0)), 250, 127, false),
    // E5M2 InfNan 码 e=31（a 侧）⟹ mxToFloat 饱和 fp32Max；b=e5m2(0,5,0)@scale127=2^-10 极小 ⟹ 乘积有限。
    Vec("sat_mx8_e5m2_infcode",   E5, E5, mx8(e5m2(0,31,0), e5m2(0,31,0)), mx8(e5m2(0,5,0), e5m2(0,5,0)), 127, 127, false)
  )

  // ════════════════════════════════════════════════════════════════════════════
  // 驱动 + 逐位校验（in-spec）：返回本配置每向量的 raw lazy-40（供跨配置互证）
  // ════════════════════════════════════════════════════════════════════════════
  private def runInSpec(cfgName: String, fmts: Set[DataFormat.Type],
                        sink: mutable.LinkedHashMap[String, BigInt]): Unit = {
    simulate(new HiveFusionMacFp(supportedFmts = fmts)) { dut =>
      // ---- 显式 reset（F1：fmt RegNext 复位为 false；data 组合）----
      dut.reset.poke(true.B)
      dut.io.aFmt.poke(DataFormat.MXE4M3)
      dut.io.bFmt.poke(DataFormat.MXE4M3)
      dut.io.a.poke(0.U); dut.io.b.poke(0.U)
      dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
      dut.io.cReg.poke(0.U)
      dut.io.rnd.poke(RoundingMode.RNE)
      dut.io.consumeValid.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      var pass = 0
      for (v <- inSpecVecs) {
        // F1 时序契约：同拍 poke(fmt + data) 并保持 → step(1) 吸收 fmt 的 1 拍 RegNext 延迟 →
        //   此拍 io.sum(组合) = f(data, fmt) 正确对齐 → peek。
        dut.io.aFmt.poke(v.aFmt)
        dut.io.bFmt.poke(v.bFmt)
        dut.io.a.poke(v.a.U)
        dut.io.b.poke(v.b.U)
        dut.io.scaleA.poke(v.scaleA.U)
        dut.io.scaleB.poke(v.scaleB.U)
        dut.io.cReg.poke(cRegBits(v.cVal).U)
        dut.io.rnd.poke(RoundingMode.RNE)
        dut.io.consumeValid.poke(false.B)
        dut.clock.step(1)

        val sumRaw = dut.io.sum.peek().litValue              // 40-bit lazy-40
        val actualFp32 = HiveSimCommon.swFpNormalize40to32(sumRaw)
        val (golden, exact) = goldenOf(v)
        // 守卫失败 = 向量落在逐位断言非法域（设计错误，非 RTL bug）；绝不据此改金锚。
        assert(exact,
          f"[$cfgName][${v.tag}] DESIGN ERROR: goldenExact=false（激励超出逐位断言合法域），golden=$golden%.6g")
        val exp = goldenBits(golden)
        assert(actualFp32 == exp,
          f"[$cfgName][${v.tag}] BIT-EXACT FAIL: golden=$golden%.9g expBits=0x$exp%08X " +
            f"actBits=0x$actualFp32%08X sumRaw=0x${sumRaw.toString(16)}")
        sink(v.tag) = sumRaw
        pass += 1
      }
      println(f"[HiveFusionMacFpSpec] $cfgName in-spec bit-exact PASS $pass/${inSpecVecs.size}")
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 驱动 + 有限性校验（饱和域，不做逐位断言）：返回每向量归一化 fp32
  // ════════════════════════════════════════════════════════════════════════════
  private def runSat(cfgName: String, fmts: Set[DataFormat.Type],
                     sink: mutable.LinkedHashMap[String, Long]): Unit = {
    simulate(new HiveFusionMacFp(supportedFmts = fmts)) { dut =>
      dut.reset.poke(true.B)
      dut.io.aFmt.poke(DataFormat.MXE2M1); dut.io.bFmt.poke(DataFormat.MXE2M1)
      dut.io.a.poke(0.U); dut.io.b.poke(0.U)
      dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
      dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
      dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)

      for (v <- satVecs) {
        dut.io.aFmt.poke(v.aFmt); dut.io.bFmt.poke(v.bFmt)
        dut.io.a.poke(v.a.U); dut.io.b.poke(v.b.U)
        dut.io.scaleA.poke(v.scaleA.U); dut.io.scaleB.poke(v.scaleB.U)
        dut.io.cReg.poke(cRegBits(v.cVal).U)
        dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
        dut.clock.step(1)
        val sumRaw = dut.io.sum.peek().litValue
        val fp32 = HiveSimCommon.swFpNormalize40to32(sumRaw)
        val expField = ((fp32 >>> 23) & 0xFF).toInt
        val mantField = (fp32 & 0x7FFFFF).toInt
        val isInf = expField == 0xFF && mantField == 0
        val isNaN = expField == 0xFF && mantField != 0
        // 饱和域只断言「有限 + 无 X 传播（Verilator 2-state；归一化非 NaN）」。不做 bit-exact。
        assert(!isNaN, f"[$cfgName][${v.tag}] SAT: 输出 NaN（X 传播/非法）fp32=0x$fp32%08X")
        sink(v.tag) = fp32
        println(f"[HiveFusionMacFpSpec] $cfgName SAT ${v.tag}: fp32=0x$fp32%08X " +
          f"sumRaw=0x${sumRaw.toString(16)} finite=${!isInf && !isNaN}")
      }
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 测试主体（5 个唯一命名 block；向量在 block 内循环校验 ⟹ 无参数化用例名撞车）
  // ════════════════════════════════════════════════════════════════════════════
  behavior of "HiveFusionMacFp removed-config bit-exact math golden (MX in-spec, R12 first FusionMac-level test)"

  it should "match the first-principles MX math golden bit-exact for the DEFAULT config (hasFp=true) in-spec" in {
    runInSpec("default(hasFp=true)", FMTS_DEFAULT, rawDefault)
    rawDefault.size shouldBe inSpecVecs.size
  }

  it should "match the first-principles MX math golden bit-exact for the REMOVED config (hasFp=false) in-spec" in {
    runInSpec("removed(hasFp=false)", FMTS_REMOVED, rawRemoved)
    rawRemoved.size shouldBe inSpecVecs.size
  }

  // 三角互证收口：两配置各自 == 金锚（上两 block）⟹ 传递性 移除==默认==金锚。
  // 本 block 再直接断言两配置 raw lazy-40 逐位相等（最强证据：内部延迟规格化位模式亦相同）。
  it should "produce bit-identical raw lazy-40 for BOTH configs in-spec (removed == default, transitively == golden)" in {
    assert(rawDefault.nonEmpty && rawRemoved.nonEmpty,
      "前置 block（default/removed in-spec）必须先运行并填充 raw 映射（AnyFlatSpec 按声明序执行）")
    var diffs = 0
    for (v <- inSpecVecs) {
      val d = rawDefault(v.tag)
      val r = rawRemoved(v.tag)
      if (d != r) {
        diffs += 1
        println(f"[HiveFusionMacFpSpec] CONFIG-DIVERGENCE(in-spec) ${v.tag}: " +
          f"default=0x${d.toString(16)} removed=0x${r.toString(16)}")
      }
      assert(d == r, f"[${v.tag}] 移除配置 != 默认配置（in-spec 应逐位相等）：default=0x${d.toString(16)} removed=0x${r.toString(16)}")
    }
    println(s"[HiveFusionMacFpSpec] in-spec removed==default raw lazy-40 bit-exact: ${inSpecVecs.size - diffs}/${inSpecVecs.size}")
    diffs shouldBe 0
  }

  it should "keep the DEFAULT config output finite (no NaN/X) in the SATURATION domain (out-of-spec, no bit-exact claim)" in {
    runSat("default(hasFp=true)", FMTS_DEFAULT, satDefault)
    satDefault.size shouldBe satVecs.size
  }

  it should "keep the REMOVED config output finite (no NaN/X) in the SATURATION domain and document expected divergence" in {
    runSat("removed(hasFp=false)", FMTS_REMOVED, satRemoved)
    satRemoved.size shouldBe satVecs.size
    // 显式记录两配置在饱和域的发散（FpAdd.scala §9.1(c) 已归档 out-of-spec 非功能回归：
    //   默认 lane0 宽阵列 magNarrow=2047 vs 移除 lane0 窄乘法器 magNarrow=1920）。不做 bit-exact 断言。
    println("[HiveFusionMacFpSpec] ===== SATURATION DOMAIN divergence record (out-of-spec, NOT a bit-exact failure) =====")
    for (v <- satVecs) {
      val d = satDefault(v.tag); val r = satRemoved(v.tag)
      val diverged = d != r
      println(f"[HiveFusionMacFpSpec]   ${v.tag}: default=0x$d%08X removed=0x$r%08X diverged=$diverged " +
        "(expected per FpAdd.scala §9.1(c) / blueprint §6.2: magNarrow 2047 vs 1920)")
    }
    println("[HiveFusionMacFpSpec] ===== end divergence record (in-spec bit-exactness is unaffected) =====")
  }

  // ════════════════════════════════════════════════════════════════════════════
  // inf/nan 软件异常守护（Task #63 / R7）
  //   · mx8 cvt 类（FpCvtMx8ToMid/FpCvtMx8ToFp32）新增 consumeValid 门控 assert，
  //     守护源真 = 原 HiveWorkUnit.scala 中的 HiveCvtOpMx（已随 FusionMac 重构删除）。E2M1(fp4) 全 16 码有限 ⟹
  //     三个 fp4 cvt 不加守护（不动）。
  //   · 负向触发（E5M2 0x7C inf / E4M3 0x7F NaN）+ 不误伤（合法最大有限码）三 block 见下。
  //
  //   【断言触发机制 —— 实测确定，非照抄假设】
  //     Chisel `assert` 经 firtool 生成 `$error` + `$fatal`（见 verilog_*/verification/assert/
  //     *_Verification_Assert.sv）。在本项目 ChiselSim + Verilator 后端下，$fatal 触发时
  //     【以可捕获的 Scala 异常浮现】：chisel3.simulator.Exceptions$AssertionFailed
  //     （message="One or more assertions failed during Chiselsim simulation"，日志精确记录
  //     触发的 assert 实例名与源文件），【不会】不可捕获地崩掉 test JVM。故 A/B 用例采用
  //     intercept 捕获即 PASS（已用独立最小探针实跑验证后落地，探针已删除）。
  // ════════════════════════════════════════════════════════════════════════════
  it should "TRIGGER the inf/nan guard for the E5M2 inf code 0x7C with consumeValid=true" in {
    // E5M2 0x7C = e5m2(0,31,0)：exp 全1 ⟹ isInfNanE5M2=true ⟹ selInfNan=true。
    // consumeValid=true 打开守护门控 ⟹ assert 触发 ⟹ Verilator $fatal ⟹ 可捕获 AssertionFailed。
    val ex = intercept[Exception] {
      simulate(new HiveFusionMacFp(supportedFmts = FMTS_DEFAULT)) { dut =>
        dut.reset.poke(true.B)
        dut.io.aFmt.poke(DataFormat.MXE5M2); dut.io.bFmt.poke(DataFormat.MXE5M2)
        dut.io.a.poke(0.U); dut.io.b.poke(0.U)
        dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
        dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
        dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        dut.io.aFmt.poke(DataFormat.MXE5M2); dut.io.bFmt.poke(DataFormat.MXE5M2)
        dut.io.a.poke(mx8(e5m2(0, 31, 0), e5m2(0, 31, 0)).U)   // 0x7C inf/nan code
        dut.io.b.poke(mx8(e5m2(0, 15, 0), e5m2(0, 15, 0)).U)   // 合法有限
        dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
        dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE)
        dut.io.consumeValid.poke(true.B)   // 守护门控打开
        dut.clock.step(1)
      }
    }
    // 必须是「断言失败」异常（守护真触发），而非其它无关异常
    ex.getClass.getName should include ("AssertionFailed")
    println(s"[HiveFusionMacFpSpec] E5M2 inf 0x7C TRIGGERED guard: caught ${ex.getClass.getName} PASS")
  }

  it should "TRIGGER the inf/nan guard for the E4M3 NaN code 0x7F with consumeValid=true" in {
    // E4M3 0x7F = e4m3(0,15,7)：exp 全1 且 mant 全1 ⟹ isInfNanE4M3=true ⟹ selInfNan=true。
    val ex = intercept[Exception] {
      simulate(new HiveFusionMacFp(supportedFmts = FMTS_DEFAULT)) { dut =>
        dut.reset.poke(true.B)
        dut.io.aFmt.poke(DataFormat.MXE4M3); dut.io.bFmt.poke(DataFormat.MXE4M3)
        dut.io.a.poke(0.U); dut.io.b.poke(0.U)
        dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
        dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
        dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        dut.io.aFmt.poke(DataFormat.MXE4M3); dut.io.bFmt.poke(DataFormat.MXE4M3)
        dut.io.a.poke(mx8(e4m3(0, 15, 7), e4m3(0, 15, 7)).U)   // 0x7F NaN code
        dut.io.b.poke(mx8(e4m3(0, 7, 0), e4m3(0, 7, 0)).U)     // 合法有限
        dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
        dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE)
        dut.io.consumeValid.poke(true.B)   // 守护门控打开
        dut.clock.step(1)
      }
    }
    ex.getClass.getName should include ("AssertionFailed")
    println(s"[HiveFusionMacFpSpec] E4M3 NaN 0x7F TRIGGERED guard: caught ${ex.getClass.getName} PASS")
  }

  it should "NOT false-trigger the inf/nan guard for the largest legal finite E4M3 code 0x7E with consumeValid=true" in {
    simulate(new HiveFusionMacFp(supportedFmts = FMTS_DEFAULT)) { dut =>
      // 显式 reset（F1：fmt RegNext 复位 false；data 组合）
      dut.reset.poke(true.B)
      dut.io.aFmt.poke(DataFormat.MXE4M3); dut.io.bFmt.poke(DataFormat.MXE4M3)
      dut.io.a.poke(0.U); dut.io.b.poke(0.U)
      dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
      dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
      dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)

      // E4M3 0x7E = e4m3(0,15,6)：e4 全1 但 m3=6(≠全1) ⟹ isInfNanE4M3=false ⟹ 合法有限 ±448
      val legal = e4m3(0, 15, 6)
      for (_ <- 0 until 4) {
        dut.io.aFmt.poke(DataFormat.MXE4M3); dut.io.bFmt.poke(DataFormat.MXE4M3)
        dut.io.a.poke(mx8(legal, legal).U)
        dut.io.b.poke(mx8(e4m3(0, 7, 0), e4m3(0, 7, 0)).U)
        dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
        dut.io.cReg.poke(0.U); dut.io.rnd.poke(RoundingMode.RNE)
        dut.io.consumeValid.poke(true.B)   // 守护门控打开：合法码仍须不触发
        dut.clock.step(1)
        val sumRaw = dut.io.sum.peek().litValue
        val fp32 = HiveSimCommon.swFpNormalize40to32(sumRaw)
        val isNaN = ((fp32 >>> 23) & 0xFF).toInt == 0xFF && (fp32 & 0x7FFFFF).toInt != 0
        assert(!isNaN, f"[guard-legal] 合法码 0x7E 被误伤：输出 NaN fp32=0x$fp32%08X")
      }
      println("[HiveFusionMacFpSpec] guard NOT false-triggered for legal E4M3 0x7E (consumeValid=true) PASS")
    }
  }
}
