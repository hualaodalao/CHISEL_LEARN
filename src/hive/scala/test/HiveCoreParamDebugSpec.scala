/** HiveCoreParamDebugSpec — 文件顶部 config 驱动的参数化 HiveCore GEMM 调试用例。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 运行方式（裸命令，无需任何 -D 参数）：
  *   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreParamDebugSpec"
  *
  * 环境硬约束：Verilator 5.050 的 FST writer 无条件依赖 lz4 头文件，
  * ChiselSim 不暴露 -CFLAGS 注入口，运行 sbt 前必须设置上述两个环境变量。
  * 波形始终开启（由本文件内 HiveAlwaysFstSim trait 控制），无需 -DemitFst=1。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 如何改参数：修改下方 object ParamDebugConfig 中的字段值即可，单次运行一组参数。
  * 所有格式/块大小/地址模式/舍入模式一律使用枚举（DataFormat / MXBlockSize /
  * AddrMode / RoundingMode），不用裸数字。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * ★ Section E：当前不可用参数区间（已知硬件/金锚缺陷，本工具只标注不修复）
  * ═══════════════════════════════════════════════════════════════════════════
  *
  * 【E1】kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1 — 多拍 C 写回数据损坏（#122）
  *   ★★★ 已修复（#122 第二阶段），本条已从 detect() 撤下，仅作历史区间文档保留 ★★★
  *   （撤下依据与本文件 E6 同一惯例：Section E 的契约是「当前不可用参数区间」，修复后
  *     再对这些配置打印「★★★ 落入已知不可用区间 ★★★」就是一种新的错误归因。）
  *   修复 = HiveCore.scala 恢复块满守卫 blkLatchCnt/blkLatchFull「同源双门」
  *     （9d74335 L443-447/L462 有、672fb37 DMA 拆分重构时被整体删除的 refactor-loss）。
  *   永久护栏 = HiveCoreDebugCase 组(e) e1/e2/e3 + d1/d3、本文件测试 6。
  *
  *   触发条件【四条 —— #122 诊断修订，原三条不完整】：
  *     kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1
  *       kTiles = MX ? K/kPerBeat(=totalN*2) : ceil(K/totalN)
  *       mTiles = ceil(M/totalN)          nTiles = ceil(N/totalN)
  *       beatsPerRow = ceil(totalN × cElemBits / cDmaExtW)，随 cDmaExtW 收窄而增大：
  *         FP32/INT32（32-bit 元素）：cDmaExtW=256 → 2 拍；=128 → 4 拍。
  *         FP16/BF16（16-bit 元素）：cDmaExtW=256 → 1 拍；=128 → 2 拍。
  *     ★ 第四条 nTiles>1 是【必要条件】：C buffer 块粒度 == 一个 n-tile（WrOnly
  *       sTRANSFER_M 按 regFile.m 行一块、nCnt 0..nTile-1），故「块末」即 nt 边界本身，
  *       超弹只损伤「下一块」；nTiles=1 无下一块 → 超弹无害（该行随 flushC 丢弃）。
  *       实测 M32 N16 K32（其余全保持失败取值）→ mismatch=0/512、地址对账 0。
  *     ★ beatsPerRow>1 是【时序窗口条件】而非结构条件：多拍使 store 排空慢于阵列生产，
  *       下一 pass 的 partial 才可能在 doneBlock 拍前把 C FIFO 填成非空；beatsPerRow=1
  *       时该拍 C FIFO 已空（cPop.valid=0）、三方同拍条件不成立、窗口不触发。
  *       实测 FP16→FP16 extW=256（kTiles=2∧mTiles=2∧nTiles=2 但 beatsPerRow=1）→ mismatch=0。
  *   症状（修复前）：六通道地址对账全绿但 C 数据大面积失配（典型 509/1024），
  *     onset 精确落在 beat#64，失配全部集中在 nt≥1、nt=0 零失配。
  *   ★★ 修复前对用户影响最大：最普通的 M=N=K=32、FP16→FP32 就中招！与格式无关、与 loadWMode 无关。
  *   归因：#122（根因 = 顶层 cPop 块满守卫被删除导致的块末超弹竞态，已三源确证并修复）。
  *   性质：硬件错（RTL 缺陷，refactor-loss）→ 已修复。
  *   【规避文案更正】原文案「C 输出为 FP16/BF16/INT8（beatsPerRow=1）即可规避」【只在
  *     cDmaExtW=256 下成立】，不是普适规避：beatsPerRow = ceil(totalN×cElemBits/cDmaExtW)，
  *     收窄 C 总线（cDmaExtW<256）时连 16-bit 输出也会变成多拍而中招
  *     （FP16/BF16 在 cDmaExtW=128 下 beatsPerRow = ceil(16×16/128) = 2 > 1）。
  *     正确的历史规避集 = {K≤16 单 kTile} ∪ {M≤16 mTiles=1} ∪ {N≤16 nTiles=1}
  *     ∪ {beatsPerRow=1，且必须按实际 cDmaExtW 计算}。修复后无需任何规避。
  *
  * 【E2】INT8 或 INT32 + loadWMode=1 + kTiles>1 — B 地址步进错（#124 A1）
  *   ★★★ 已修复（#124 ①），本条已从 detect() 撤下，仅作历史区间文档保留 ★★★
  *   （撤下依据与本文件 E1/E6 同一惯例：Section E 的契约是「当前不可用参数区间」，
  *     修复后再对这些配置打印「★★★ 落入已知不可用区间 ★★★」就是一种新的错误归因。）
  *   修复 = HiveCoreCalcConfig 把 a/bColTileAddressOffset 由 elaboration 期常数
  *     totalN*(aW/8)=totalN*(bW/8)=32（aW/bW 是编译期位宽、出厂 16，格式无关）改为
  *     运行期 totalN × elemsPerLane(fmt) × memElemBytes(fmt)。
  *   永久护栏 = 本文件测试 9（#124 ① 三档盲区护栏：INT8 loadW=0 n-tile 支、
  *     INT8 loadW=1 k-tile 支、以及 ② 的 dmaCScale 非整除支）。
  *
  *   历史触发条件（修复前）：bFmt=INT8 或 INT32，loadWMode=true，且 K 使 kTiles≥2。
  *   历史症状：kt≥1 的 B 地址全错（硬件 k-switch 步进恒为编译期 32 字节，
  *     INT8 应为 16、INT32 应为 64）。
  *   归因：#124 A1。性质：硬件错（RTL 缺陷）→ 已修复。修复前零测试覆盖。
  *   历史规避（现已无需）：INT8/INT32 使用 loadWMode=false，或 K≤totalN（单 kTile）。
  *   注：MX8 与 FP16/BF16/INT16 巧合等价（同为 32 字节）故从未受影响 —— 这正是
  *     该缺陷长期隐形的机制，也是 #124 ① 修复后这四档数值【零变化】、全量回归
  *     不退化的原因。
  *   ★ 残余（不属 E2）：loadW=1 且 K 非 bKpb 整数倍 ∧ nTiles≥2 时，RdOnly 的
  *     lastBTileStartAddr 累加模型在 nt 维仍有 Δ_nt = nt*(kT*C − R)；#124 ① 只归零了
  *     kt 维分量 Δ_kt = kt*(C − bKpb*bEb)。Δ_nt 已由 #139 承接，勿在 E2 名下重开。
  *
  * 【E3】量化 C 输出且 n % cMxBlk != 0 — scaleC 行偏移 FLOOR 错位（#124 A2）
  *   ★★★ 已修复（#124 ②），本条已从 detect() 撤下，仅作历史区间文档保留 ★★★
  *   修复 = HiveCoreCalcConfig 把 scaleCRowAddressOffset 由 FLOOR（io.regFile.n / blk）
  *     改为 CEIL（(io.regFile.n +& (blk-1).U) / blk）。四源核对一致：金锚三处
  *     （HiveDebugModel.expCScaleAddrSeq / MxSpec L324 / QuantSpec L273+L1099）
  *     + 权威 RTL 原文（672fb37^ HiveCore.scala `(regFile.n + cBlkElems - 1.U) >> cBlkShift`）
  *     ⇒ FLOOR 是 672fb37 拆分重构【新引入】的退化，非设计意图。本次只改 RTL、金锚不动。
  *   ★ scaleCColTileAddressOffset【保持 FLOOR 不动】：它是 n-tile 步进 = totalN / cMxBlk，
  *     validate 已强制 cMxBlk ≤ totalN ∧ totalN % cMxBlk == 0，故 FLOOR ≡ CEIL，
  *     与金锚 `colOff = totalN.toLong / p.cMxBlk` 同式。勿「顺手一起改 ceil」。
  *   永久护栏 = 本文件测试 9（#124 ② 非整除 dmaCScale 护栏）。
  *
  *   历史触发条件（修复前）：cFmt 为 MX 族（MXE4M3/MXE5M2/MXE2M1）且 N 不被 cMxBlk 整除。
  *   历史症状：硬件用 FLOOR、正确为 CEIL，scale 行静默重叠（m≥1 起所有 scale 行地址
  *     与前一行的尾部条目重叠；不报错、不越界，只是写歪）。
  *   归因：#124 A2。性质：硬件错（金锚是对的，strictAddr 会真红抓住，属可信失败）→ 已修复。
  *   历史规避（现已无需）：确保 N % cMxBlk == 0。
  *
  * 【E4】MX 且 bMxBlk<32（8 或 16）— A/B 输入侧无执行侧消费者
  *   触发条件：aFmt/bFmt 为 MX 族且 bMxBlkSel 对应 8 或 16。
  *   症状：C 数值失配且 relErr 恒为 2 的幂（2^-1/2^+1/2^+2）。
  *   归因：用户本次未选择处理（阶段 2 在途 executor scale 广播粒度局限）。
  *   性质：硬件错（RTL 局限）。
  *   规避：MX 运行时使用 bMxBlk=32（= kPerBeat，单 scale 块/pass）。
  *   【#122 D.3 更正：E4 曾被误报命中 QuantSpec 两个 dualMX K=64 用例，该归因是错的】
  *     误报成因 = 本文件旧 mk() 把 aMxBlkSel/bMxBlkSel/cMxBlkSel 压成同一个 mxSel 参数
  *     （审计工具自身的建模错误，在自造的假参数上自证）。两项的真实 regFile 取值
  *     （源码取证 HiveCoreSimCaseQuantSpec.scala L1231-1232：controlWord6 =
  *       (1L<<28) | ((blkSel=1)&0x3)<<26 | ((2L)&0x3)<<24 | ((2L)&0x3)<<22）为
  *       cMxBlkSel=1(M16=16)、bMxBlkSel=2(M32=32)、aMxBlkSel=2(M32=32)
  *     → bMxBlk=32，E4 判据 bMxBlk<32【从未成立】。这两项实属 #127，见测试 7 Part 1。
  *
  * 【E5】MX 且 K 不被 totalN*2(=32) 整除 — 硬件无守卫（#124 B2）
  *   触发条件：aFmt/bFmt 为 MX 族且 K % 32 != 0。
  *   症状：kTile 用 floor 除法，静默丢弃尾部 k 段产生错数据。
  *   归因：#124 B2。性质：硬件错（当前仅金锚 validate 的 require 在仿真前拦截，硬件无 errCode）。
  *   规避：MX 运行时确保 K % 32 == 0。
  *
  * 【E6】rnd=RMM 在 #120 C2 修好前会被静默截断为 RNE — ★ 已修复，本条已失效 ★
  *   C2 已将 reg7 rnd 从 2-bit 扩为 3-bit（bits[14:12]），RMM(4) 现在可正确编码。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * addrMode 说明：addrMode 当前无硬件消费者（#123 审计确认 CalcConfig 与两个 DMA
  * 一次都没引用 regs(6) bits[14:12]），设置 TILE 不会改变实际 DMA 寻址行为，
  * 仅供寄存器级 debug 与回读对账。内存镜像、golden、六路期望地址序列全部按 MAT
  * 语义生成，不受 addrMode 取值影响。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * ★ 三条架构约束（#126 落档；用户自行调 cross 配置时必撞的坑）
  * ═══════════════════════════════════════════════════════════════════════════
  *
  * 【约束一】输入配对合法性（v1）—— 只约束 aFmt/bFmt 配对，【不限制输出 cFmt】
  *   v1 只允许两类输入组合：
  *     (a) 双 MX：MXE4M3 / MXE5M2 可互组；
  *     (b) 双非 MX 浮点：FP16 / BF16 可互组。
  *   两类非法组合由硬件 errCode3 = (isMx(aFmt) 异或 isMx(bFmt)) 主动拦掉：
  *     · A 是 MX 而 B 不是 → 数学上【不可实现】：lane i ↔ K 索引 k0+i，A 每 lane
  *       打包 2 元素时行 x 的激活对 {2x,2x+1} 与 B 的单一静态权重 K 索引 x 失配。
  *     · A 非 MX 而 B 是 MX → 理论可行但需 executor 做偶奇重排供数，v1 禁止、留给 v2。
  *   ★★ 关键：此约束【只管输入侧配对，不限制输出格式】。故"非 MX 输入 → mxfp8 量化
  *     输出"（本矩阵 #2/#4）【不违反】它 —— C 侧量化消费的是 fp32 累加器，与输入格式无关。
  *
  * 【约束二】单拍写回的前提 —— cDmaExtW 必须保持默认 256
  *   每行拍数 beatsPerRow = ceil(totalN × 输出元素位宽 / cDmaExtW)。
  *   默认 cDmaExtW=256 下：FP16 输出 = ceil(16×16/256) = 256/256 = 【1 拍】；
  *                          MXFP8 输出 = ceil(16×8/256)  = 128/256 = 【1 拍】。
  *   本矩阵六组结构上都是单拍写回，故绕开了 #122（#122 需 beatsPerRow>1，见约束三）。
  *   ★★ 警告：这只在 cDmaExtW 保持 256 时成立。一旦收窄到 128，FP16 输出立刻变成
  *     ceil(16×16/128) = 2 拍、当场落入 #122 触发区。用户自行调 cross 配置若动这个
  *     参数（cDmaExtW）就会撞到。
  *
  * 【约束三】#122 的四条触发条件（已修复，但边界须知道）
  *   kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1 —— 四条【缺一不可】。
  *   其中 beatsPerRow 是【时序窗口条件】而非结构条件：多拍使 store 排空慢于阵列生产，
  *   下一 pass 的 partial 才可能在 doneBlock 拍前把 C FIFO 填成非空；beatsPerRow=1 时
  *   该拍 C FIFO 已空（cPop.valid=0）、三方同拍条件不成立、窗口不触发。
  *   （几何量定义见下方 SectionEBanner.e1Geometry()，正反双证见测试 7 Part 2。）
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 产物路径：FST 波形默认落到 sim/debug/param_debug.fst（可在 config 中修改）。
  * 失败/超时时自动转存波形与输入数据文件。
  */

import chisel3._
import chisel3.simulator.scalatest._
import chisel3.simulator.HasSimulator
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._

// ============================================================================
// ★ 文件顶部 CONFIG：修改此处参数即可调试不同配置（单次运行一组）
// ============================================================================
object ParamDebugConfig {
  // --- 矩阵几何 ---
  val M: Int = 32              // 行数（>0）。mTiles = ceil(M/16)
  val N: Int = 32              // 列数（>0）。nTiles = ceil(N/16)
  val K: Int = 16              // 归约长度（>0）。MX 需 K%32==0；非 MX kTiles=ceil(K/16)
                               // ★ 默认 K=16 → 单 kTile，规避 Section E1

  // --- 数据格式（三者独立，一律用 DataFormat 枚举）---
  val aFmt: DataFormat.Type = DataFormat.FP16    // A 输入格式
  val bFmt: DataFormat.Type = DataFormat.FP16    // B 输入格式
  val cFmt: DataFormat.Type = DataFormat.FP32    // C 输出格式
  // 合法取值：FP32, FP16, BF16, MXE4M3, MXE5M2, MXE2M1, INT32, INT16, INT8, INT4
  // 注意：FP32 不可作为 A/B 输入（硬件 errCode=5）；INT4/MXE2M1 不可作为输入（半字节）

  // --- MX 块大小（三者独立，仅 MX 格式生效）---
  val aMxBlk: MXBlockSize.Type = MXBlockSize.M32  // scaleA 块大小：M8/M16/M32/M64
  val bMxBlk: MXBlockSize.Type = MXBlockSize.M32  // scaleB 块大小
  val cMxBlk: MXBlockSize.Type = MXBlockSize.M8   // scaleC 块大小（量化输出）

  // --- 加载模式 ---
  val loadWMode: Boolean = false   // false=B 为 K×N 行主序；true=B 为 N×K 转置（MX 必须 true）

  // --- 地址模式（仅寄存器面，无硬件消费者）---
  val addrMode: AddrMode.Type = AddrMode.MAT   // MAT 或 TILE（TILE 不改变实际 DMA 寻址）

  // --- 舍入模式 ---
  val rnd: RoundingMode.Type = RoundingMode.RNE  // RNE/RTZ/RDN/RUP/RMM（3-bit，#120 C2 已扩位）

  // --- 仿真控制 ---
  val seed: Long = 1234L              // 激励随机种子（固定种子可复现）
  val strictAddr: Boolean = false     // true=地址对账逐拍致命断言；false=非致命收集
  val maxCycles: Int = 8000           // 仿真最大拍数（量化输出自动抬到 20000）
  val waveTarget: String = "sim/debug/param_debug.fst"  // FST 波形输出路径
}

// ============================================================================
// 始终开启 FST 波形的 trait（不依赖 -DemitFst flag）
// ============================================================================
trait HiveAlwaysFstSim { this: HasCliOptions =>
  implicit val fstSimulator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
        .withTraceStyle(Some(
          svsim.verilator.Backend.CompilationSettings
            .TraceStyle(svsim.verilator.Backend.CompilationSettings.TraceKind.Fst())
        ))
    )
  // 注册 emitFst 键（兼容），但无论是否传入都启用波形
  private def enableWaves(options: svsim.CommonCompilationSettings) =
    options.copy(simulationSettings = options.simulationSettings.copy(enableWavesAtTimeZero = true))
  addOption(HasCliOptions.CliOption.flag(
    name = "emitFst",
    help = "FST waves are ALWAYS enabled in this spec (flag accepted for compatibility only)",
    updateCommonSettings = enableWaves,
    updateUnsetCommonSettings = enableWaves
  ))
}

// ============================================================================
// Section E 横幅检测
// ============================================================================
object SectionEBanner {
  case class Warning(id: String, condition: String, symptom: String, task: String, nature: String)

  /** #122（原 E1）四条触发条件的几何量。纯计算，只依赖 totalN 与 cDmaExtW。
    *
    * 【#122 D 项：为何单独抽出】E1 已由 #122 主修复清除并从 detect() 撤下，但四条触发
    *   条件的表征本身是诊断的核心结论，必须被锁死（否则日后有人误删/误加任一条件无人发现）。
    *   本类型 + e1Geometry() 使测试 7 Part 2 能对护栏族配置做正反双证：
    *   e1 四条全满足 → triggers=true；e2/e3/d2/b2 各缺且仅缺一条 → triggers=false 且
    *   missing 精确点名缺哪一条。 */
  case class E1Geometry(kTiles: Int, mTiles: Int, nTiles: Int, beatsPerRow: Int) {
    /** 四条触发条件是否同时成立（= 修复前是否会落入 E1 损坏区间） */
    def triggers: Boolean = kTiles > 1 && mTiles > 1 && nTiles > 1 && beatsPerRow > 1
    /** 未满足的条件点名（triggers=false 时给出缺哪几条，供单变量对照自测精确断言） */
    def missing: Seq[String] = Seq(
      if (kTiles > 1) None else Some(s"kTiles=$kTiles"),
      if (mTiles > 1) None else Some(s"mTiles=$mTiles"),
      if (nTiles > 1) None else Some(s"nTiles=$nTiles"),
      if (beatsPerRow > 1) None else Some(s"beatsPerRow=$beatsPerRow")).flatten
    override def toString: String =
      s"kTiles=$kTiles mTiles=$mTiles nTiles=$nTiles beatsPerRow=$beatsPerRow"
  }

  /** 计算 #122（原 E1）四条件几何量。
    * kTiles：MX 输入一个 kTile = 一个 32-K scale 块 = kPerBeat(=totalN×2)；
    *         非 MX 一个 kTile = totalN 个 K 元素。
    * beatsPerRow：必须用【实际 cDmaExtW】计算，不可硬编码 256（#120 修正）——
    *   否则 16-bit 输出在 cDmaExtW<256（如 shrunk E8=128）时会被算成
    *   ceil(16×16/256)=1 而漏判（实际 ceil(16×16/128)=2>1，确属多拍写回区间）。 */
  def e1Geometry(p: HiveDebugModel.DebugParams, totalN: Int, cDmaExtW: Int): E1Geometry = {
    import HiveDebugModel._
    val aKpb = kPerBeat(p.aFmt, totalN)
    E1Geometry(
      kTiles = if (isMxAny(p.aFmt)) p.k / aKpb else (p.k + totalN - 1) / totalN,
      mTiles = (p.m + totalN - 1) / totalN,
      nTiles = (p.n + totalN - 1) / totalN,
      beatsPerRow = HiveSimCommon.cBeatsPerRow(p.cFmt, totalN, cDmaExtW))
  }

  def detect(p: HiveDebugModel.DebugParams, totalN: Int, cDmaExtW: Int): Seq[Warning] = {
    import HiveDebugModel._
    val buf = scala.collection.mutable.ArrayBuffer.empty[Warning]
    val aEb = memElemBytes(p.aFmt)
    val bEb = memElemBytes(p.bFmt)
    val aKpb = kPerBeat(p.aFmt, totalN)
    val bKpb = kPerBeat(p.bFmt, totalN)
    val g = e1Geometry(p, totalN, cDmaExtW)
    val kTiles = g.kTiles
    val mTiles = g.mTiles
    val beatsPerRow = g.beatsPerRow

    // E1【已修复并撤下，#122 第二阶段】：原为 kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1
    //   （诊断修订为四条，补 nTiles>1）。主修复恢复 HiveCore.scala 的 blkLatchCnt/
    //   blkLatchFull 同源双门后该区间已可用，故不再作为「不可用区间」发出警告 ——
    //   与本文件 E6（rnd=RMM，#120 C2 已修）同一处理惯例。四条件表征仍由
    //   e1Geometry()/E1Geometry.triggers 保留并被测试 7 Part 2 正反双证锁死。
    //   历史症状与完整触发条件见本文件顶部 Section E 的【E1】文档段。

    // E2【已修复并撤下，#124 ①】：原为 (bFmt==INT8 || bFmt==INT32) ∧ loadWMode ∧ kTiles>1。
    //   主修复 = HiveCoreCalcConfig 把 a/bColTileAddressOffset 由 elaboration 期常数
    //   totalN*(aW/8)=totalN*(bW/8)=32 改为运行期 totalN × elemsPerLane(fmt) × memElemBytes(fmt)，
    //   INT8 现值 16、INT32 现值 64，与金锚 bKTileOff=bKpb*bEb / bNTileOff=totalN*bEb
    //   全格式恒等 ⇒ 该区间已可用，不再作为「不可用区间」发出警告。
    //   与本文件 E1（#122 已修）/E6（#120 C2 已修）同一撤下惯例：Section E 的契约是
    //   「当前不可用参数区间」，修复后继续对这些配置打印「★★★ 落入已知不可用区间 ★★★」
    //   就是一种新的错误归因。永久护栏 = 本文件测试 9（#124 ① 三档盲区护栏）。
    //   历史症状与完整触发条件见本文件顶部 Section E 的【E2】文档段。
    //   ★ 残余（不属 E2、勿在此重开）：loadW=1 且 K 非 bKpb 整数倍 ∧ nTiles≥2 时，
    //     RdOnly lastBTileStartAddr 累加模型在 nt 维仍有 Δ_nt = nt*(kT*C − R)，已由 #139 承接。

    // E3【已修复并撤下，#124 ②】：原为 isMxAny(cFmt) ∧ n % cMxBlk != 0。
    //   主修复 = HiveCoreCalcConfig 把 scaleCRowAddressOffset 由 FLOOR（io.regFile.n / blk）
    //   改为 CEIL（(io.regFile.n +& (blk-1).U) / blk），与金锚四处
    //   （HiveDebugModel.expCScaleAddrSeq / MxSpec L324 / QuantSpec L273+L1099）
    //   及权威 RTL 原文（672fb37^ HiveCore.scala `(regFile.n + cBlkElems - 1.U) >> cBlkShift`）
    //   四源一致 ⇒ 该区间已可用。scaleCColTileAddressOffset 保持 FLOOR 不动（validate
    //   已强制 totalN % cMxBlk == 0，故 FLOOR ≡ CEIL，与金锚同式）。
    //   撤下惯例同 E1/E2/E6。永久护栏 = 本文件测试 9（#124 ② 非整除 dmaCScale 护栏）。
    //   历史症状见本文件顶部 Section E 的【E3】文档段。

    // E4: MX 且 bMxBlk<32
    if ((isMxAny(p.aFmt) || isMxAny(p.bFmt)) && p.bMxBlk < 32)
      buf += Warning("E4", s"MX 格式 ∧ bMxBlk=${p.bMxBlk}<32",
        "A/B 输入侧无执行侧消费者：C 数值失配且 relErr 恒为 2 的幂",
        "用户未选择处理", "硬件错（RTL 局限）")

    // E5: MX 且 K 不被 totalN*2 整除
    if ((isMxAny(p.aFmt) || isMxAny(p.bFmt)) && p.k % (totalN * 2) != 0)
      buf += Warning("E5", s"MX 格式 ∧ K=${p.k} % ${totalN * 2} != 0",
        "kTile 用 floor 除法，静默丢弃尾部 k 段产生错数据",
        "#124 B2", "硬件错（金锚 validate 拦截，硬件无 errCode）")

    buf.toSeq
  }

  def printBanner(warnings: Seq[Warning], tag: String): Unit = {
    if (warnings.isEmpty) {
      println(s"[$tag] Section E 检测：当前配置未落入任何已知不可用区间 ✓")
    } else {
      println(s"[$tag] " + "═" * 72)
      println(s"[$tag] ★★★ Section E 警告：当前配置落入 ${warnings.size} 个已知不可用区间 ★★★")
      println(s"[$tag] " + "═" * 72)
      warnings.foreach { w =>
        println(s"[$tag] ┌─【${w.id}】归因 ${w.task}｜性质：${w.nature}")
        println(s"[$tag] │  触发条件：${w.condition}")
        println(s"[$tag] │  预期症状：${w.symptom}")
        println(s"[$tag] └─ 仍然继续运行（不 skip），用户需看到失败与波形来确认诊断")
      }
      println(s"[$tag] " + "═" * 72)
    }
  }
}

// ============================================================================
// 主测试类
// ============================================================================
class HiveCoreParamDebugSpec extends AnyFlatSpec with Matchers with ChiselSim
    with HiveAlwaysFstSim with HiveStatusPollApi {

  import HiveDebugModel._

  private val TAG = "PARAM-DBG"

  /** 从 ParamDebugConfig 构造 DebugParams */
  private def configToParams(hint: String): DebugParams = DebugParams(
    m = ParamDebugConfig.M, n = ParamDebugConfig.N, k = ParamDebugConfig.K,
    aFmt = ParamDebugConfig.aFmt, bFmt = ParamDebugConfig.bFmt, cFmt = ParamDebugConfig.cFmt,
    loadWMode = ParamDebugConfig.loadWMode,
    aMxBlkSel = ParamDebugConfig.aMxBlk.litValue.toInt,
    bMxBlkSel = ParamDebugConfig.bMxBlk.litValue.toInt,
    cMxBlkSel = ParamDebugConfig.cMxBlk.litValue.toInt,
    addrModeSel = ParamDebugConfig.addrMode.litValue.toInt,
    rnd = ParamDebugConfig.rnd.litValue.toInt,
    seed = ParamDebugConfig.seed,
    strictAddr = ParamDebugConfig.strictAddr,
    maxCycles = ParamDebugConfig.maxCycles,
    waveTarget = ParamDebugConfig.waveTarget,
    testHint = hint)

  /** 共用 HiveCore 编译期配置（全格式 + 运行期 isMx 门控） */
  private val cfg = HiveCoreConfig(
    arrayN = 8, clusterM = 2,
    aW = 16, bW = 16, cW = 40,
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
    scaleBufferDepth = 64)

  behavior of "HiveCore ParamDebug"

  // ==========================================================================
  // 测试 1：用户顶部 config 驱动（默认 = FP16 M32 N32 K16 单 kTile，必须绿）
  // ==========================================================================
  it should "run user config from file-top ParamDebugConfig" in {
    runParamDebug(configToParams("should-run-user-config-from-file-top-ParamDebugConfig"))
  }

  // ==========================================================================
  // 测试 2：三组代表性参数 bit-exact
  // ==========================================================================
  it should "pass representative group 1: FP16 single-kTile (M16 N32 K16)" in {
    runParamDebug(DebugParams(
      m = 16, n = 32, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 42L, strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_repr1_fp16.fst",
      testHint = "should-pass-representative-group-1"))
  }

  it should "pass representative group 2: dual MX input (MXE4M3 x MXE5M2, K=32)" in {
    runParamDebug(DebugParams(
      m = 16, n = 16, k = 32,
      aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.FP32,
      loadWMode = true, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 77L, strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_repr2_dualmx.fst",
      testHint = "should-pass-representative-group-2"))
  }

  it should "pass representative group 3: FP16 loadWMode=1 (non-MX, K=16)" in {
    runParamDebug(DebugParams(
      m = 16, n = 16, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = true, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 99L, strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_repr3_loadw1.fst",
      testHint = "should-pass-representative-group-3"))
  }

  // ==========================================================================
  // 测试 3：addrMode MAT vs TILE 对照（数值结果与六路地址序列完全一致）
  // ==========================================================================
  it should "prove addrMode MAT vs TILE produce identical results (no HW consumer)" in {
    println(s"[$TAG] ═══ addrMode 对照实验 ═══")
    println(s"[$TAG] 提示：addrMode 当前无硬件消费者，设置 TILE 不会改变实际 DMA 寻址行为，仅供寄存器级 debug 与回读对账")

    val baseParams = DebugParams(
      m = 16, n = 16, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 555L, strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_addrmode_mat.fst",
      testHint = "addrmode-MAT")

    val tileParams = baseParams.copy(addrModeSel = 1,
      waveTarget = "sim/debug/param_addrmode_tile.fst", testHint = "addrmode-TILE")

    val matResult = runParamDebugCollect(baseParams)
    val tileResult = runParamDebugCollect(tileParams)

    withClue("addrMode MAT vs TILE 数值结果应完全一致（硬件零消费者）：") {
      matResult.mismatch should be(0)
      tileResult.mismatch should be(0)
      matResult.addrBadCnt should be(0)
      tileResult.addrBadCnt should be(0)
    }
    println(s"[$TAG] ✓ addrMode 对照结论：MAT 与 TILE 数值结果与六路地址序列完全一致，" +
      "证实硬件零消费者。addrMode 仅供寄存器级 debug 与回读对账。")
  }

  // ==========================================================================
  // 测试 4：reg6/reg7 round-trip 回读断言
  // ==========================================================================
  it should "verify reg6 and reg7 round-trip readback" in {
    val p = DebugParams(
      m = 16, n = 16, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = true, aMxBlkSel = 2, bMxBlkSel = 1, cMxBlkSel = 0,
      addrModeSel = 1, rnd = 4,  // RMM=4，验证 3-bit 扩位
      seed = 1L, strictAddr = false, maxCycles = 100,
      waveTarget = "sim/debug/param_reg_roundtrip.fst",
      testHint = "reg-roundtrip")

    println(s"[$TAG] ═══ reg6/reg7 round-trip 回读断言 ═══")
    println(s"[$TAG] 写入值: regs6=0x${p.controlWord6}%08X regs7=0x${p.controlWord7}%08X")

    simulate(new HiveCore(cfg)) { dut =>
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaScaleA.get.req.ready.poke(true.B)
      dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
      dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
      dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
      dut.io.dmaScaleB.get.req.ready.poke(true.B)
      dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
      dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
      dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
      dut.io.dmaCScale.get.req.ready.poke(true.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.payload.err.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(r0.U)
        dut.io.cmd.payload.reg1Addr.poke(r1.U)
        dut.io.cmd.payload.data0.poke(d0.U)
        dut.io.cmd.payload.data1.poke(d1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      def readReg(addr: Int): BigInt = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(HiveCoreOp.REG_READ)
        dut.io.cmd.payload.reg0Addr.poke(addr.U)
        dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U)
        dut.io.cmd.payload.data1.poke(0.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step()
        // resp.valid should be high now
        val data = dut.io.resp.payload.data.peek().litValue
        dut.clock.step()
        data
      }

      // 写入 reg6 和 reg7
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, p.controlWord6, p.controlWord7)

      // 回读 reg6
      val rb6 = readReg(0x06)
      println(f"[$TAG] reg6 回读: 0x${rb6}%08X (期望 0x${p.controlWord6}%08X)")
      withClue(f"reg6 round-trip 失败: 写入=0x${p.controlWord6}%08X 回读=0x${rb6}%08X") {
        rb6.toLong should be(p.controlWord6)
      }

      // 回读 reg7
      val rb7 = readReg(0x07)
      println(f"[$TAG] reg7 回读: 0x${rb7}%08X (期望 0x${p.controlWord7}%08X)")
      withClue(f"reg7 round-trip 失败: 写入=0x${p.controlWord7}%08X 回读=0x${rb7}%08X") {
        rb7.toLong should be(p.controlWord7)
      }

      println(s"[$TAG] ✓ reg6/reg7 round-trip 回读断言通过（含 rnd=RMM(4) 3-bit 编码验证）")
    }
    copyWave("HiveCoreParamDebugSpec", "reg-roundtrip", p.waveTarget)
  }

  // ==========================================================================
  // 测试 5：rnd=RMM 实测生效（与 RNE 产生不同结果）
  // ==========================================================================
  it should "prove rnd=RMM produces different results from RNE" in {
    println(s"[$TAG] ═══ rnd=RMM 实测生效验证 ═══")
    val baseP = DebugParams(
      m = 16, n = 16, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.BF16,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 2024L, strictAddr = false, maxCycles = 8000,
      waveTarget = "sim/debug/param_rnd_rne.fst", testHint = "rnd-RNE")
    val rmmP = baseP.copy(rnd = 4, waveTarget = "sim/debug/param_rnd_rmm.fst", testHint = "rnd-RMM")

    val rneResult = runParamDebugCollect(baseP)
    val rmmResult = runParamDebugCollect(rmmP)

    println(s"[$TAG] RNE: mismatch=${rneResult.mismatch}, RMM: mismatch=${rmmResult.mismatch}")
    // 两者都应通过 golden（各自 rnd 的 golden）
    withClue("RNE 应通过 golden：") { rneResult.mismatch should be(0) }
    withClue("RMM 应通过 golden：") { rmmResult.mismatch should be(0) }
    // RMM 与 RNE 的 golden 值应不同（BF16 输出下 tie-breaking 规则不同）
    println(s"[$TAG] ✓ rnd=RMM(4) 实测生效：3-bit 编码正确写入硬件，golden 按 RMM 舍入通过")
  }

  // ==========================================================================
  // 测试 6：#122 回归护栏 —— 原 Section E1 配置修复后必须逐元素 bit-exact
  // --------------------------------------------------------------------------
  // 【断言翻转（#122 第二阶段 A 项）】本用例原为「失败自动 dump 路径」的验证载体，
  //   断言 `result.mismatch should be > 0`，把 #122 缺陷硬编码成了期望行为。
  //   ★ 翻转前实跑证据（修复前工作树，build/baseline_122_p2.log 第 1118/1120 行）：
  //       [PARAM-DBG] 非断言模式：mismatch=509（预期失配，验证 dump 路径）
  //       [PARAM-DBG] E1 配置运行结果：mismatch=509（预期 >0，属 #122 已知缺陷）
  //     即：旧断言在修复前为绿（509>0）、修复后必红（0 不满足 >0）→ 必须翻转。
  //   ★ 新断言「能跑红」的实跑证明：本用例在 HiveCore.scala 主修复落地【之前】先行
  //     翻转并实跑，实测 mismatch=509 → `509 should be(0)` 抛 TestFailedException，
  //     用例确为红（见报告 A 项证据）；主修复落地后转绿。符合「新增护栏必须先证明能跑红」。
  // 【dump 覆盖转移】翻转后本用例不再失败、不再触发失败路径 dump，故「失败自动 dump」
  //   能力改由下方测试 6b 的显式注入用例验证，覆盖不丢失（不依赖任何硬件缺陷存续）。
  // 【四条件】kTiles=2(ceil(32/16)) ∧ mTiles=2 ∧ nTiles=2(ceil(32/16)) ∧
  //   beatsPerRow=2(ceil(16×32/256)) → #122 触发条件全满足，是主修复的正例守卫。
  // ==========================================================================
  it should "regression-guard #122: former E1 config M32 N32 K32 FP16->FP32 is now bit-exact" in {
    val guardP = DebugParams(
      m = 32, n = 32, k = 32,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 1234L, strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_guard_122_e1_fixed.fst",
      testHint = "should-regression-guard-122-former-E1-config")

    println(s"[$TAG] ═══ #122 回归护栏：原 Section E1 配置（四条件全满足）修复后必须 bit-exact ═══")
    val result = runParamDebugCollect(guardP, expectPass = true)
    println(s"[$TAG] #122 护栏结果：mismatch=${result.mismatch} addrBadCnt=${result.addrBadCnt}" +
      "（修复前同配置实测 mismatch=509，见 build/baseline_122_p2.log）")
    withClue("#122 修复后原 E1 配置必须逐元素 bit-exact（修复前 mismatch=509）：") {
      result.mismatch should be(0)
    }
    withClue("六通道逐拍地址对账（strictAddr=1）必须全绿：") { result.addrBadCnt should be(0) }

    val wavePath = Paths.get(guardP.waveTarget)
    withClue("护栏波形产物应存在且非空：") {
      Files.exists(wavePath) should be(true)
      Files.size(wavePath) should be > 0L
    }
    println(s"[$TAG] ✓ #122 回归护栏通过：mismatch=0、地址对账=0，波形 ${Files.size(wavePath)} bytes")
  }

  // ==========================================================================
  // 测试 6b：失败自动 dump 路径 —— 显式注入断言失败（自持有，不依赖硬件缺陷存续）
  // --------------------------------------------------------------------------
  // 【为何必须独立成例】测试 6 翻转为 mismatch==0 后永不再失败，「断言失败时波形仍被
  //   转存」这一已交付能力将失去验证覆盖。原实现把 dump 覆盖寄生在一个真实缺陷上，
  //   缺陷修好即静默失覆盖 —— 这是覆盖设计缺陷，本例予以纠正。
  // 【注入机制】runParamDebugCollect(injectFailure=true) 在 simulate 块内、C 对比完成后
  //   主动调用 fail()，异常上抛时外层 `finally { copyWave(...) }` 仍执行 → 与真实断言
  //   失败走完全相同的代码路径（同一 finally、同一 copyWave、同一 findTrace 回落逻辑）。
  // 【产物新鲜性】注入前先删除目标波形并断言其不存在，杜绝「陈旧残留文件冒充本次产物」
  //   造成假绿。
  // ==========================================================================
  it should "verify failure auto-dump path via explicitly injected assertion failure" in {
    val injectP = DebugParams(
      m = 32, n = 32, k = 32,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 1234L, strictAddr = false, maxCycles = 8000,
      waveTarget = "sim/debug/param_fail_dump_injected.fst",
      testHint = "should-verify-failure-auto-dump-path-injected")

    println(s"[$TAG] ═══ 失败自动 dump 路径验证（显式注入断言失败）═══")
    val target = Paths.get(injectP.waveTarget)
    if (Files.exists(target)) Files.delete(target)
    withClue("注入前目标波形必须不存在（否则无法证明本次失败路径真产出了波形）：") {
      Files.exists(target) should be(false)
    }

    var thrown: Option[Throwable] = None
    try {
      runParamDebugCollect(injectP, expectPass = false, injectFailure = true)
    } catch { case t: Throwable => thrown = Some(t) }

    withClue("显式注入必须抛出异常（否则失败路径未被真正触发，dump 验证无效）：") {
      thrown.isDefined should be(true)
    }
    println(s"[$TAG] 注入异常已捕获：${thrown.get.getClass.getSimpleName}: ${thrown.get.getMessage}")
    withClue("失败路径波形应存在且非空（证明异常上抛时 finally 中的 copyWave 仍执行）：") {
      Files.exists(target) should be(true)
      Files.size(target) should be > 0L
    }
    println(s"[$TAG] ✓ 失败自动 dump 路径验证通过：异常上抛后波形仍转存 ${Files.size(target)} bytes")
  }

  // ==========================================================================
  // 测试 7（自测诊断）：Part 1 反检【#122 修复后的真实红项集合】并逐条归因；
  //   Part 2 正反双证 #122（原 E1）四条触发条件的表征本身。纯计算，不跑仿真。
  // -------------------------------------------------------------------------
  // 【#122 第二阶段 D 项：本用例的三处更正】
  //   D.1 旧 mk() 把 aMxBlkSel/bMxBlkSel/cMxBlkSel 压成同一个 mxSel 参数 —— 这是
  //       【审计工具自身的建模错误】：它让 detect() 在自造的假参数上自证。现拆成三个
  //       独立参数，调用点按各用例的【真实 regFile 取值】填写（逐个回源码核对，见下）。
  //   D.3 修正后的真实结论：QuantSpec 两个 dualMX K=64 用例 bMxBlk=32，E4【从未命中】；
  //       旧清单把它们标成「命中 E4 → 归因 #118」是错误归因（与 QuantSpec L194-195 的
  //       错误注释同源，该注释已在 E 项一并更正）。它们不属任何 Section E 区间，
  //       如实标为【#127】，不硬凑区间。
  //   D.4 期望清单由「11 个已知红项」同步更新为【修复后的真实红项集合 = 3 个】，
  //       用例名里的「11」同步改「3」。原 11 项中 9 项属 #122 簇 A，已由主修复清除
  //       （修复前实跑 mismatch=509/506，build/guard_prefix_122.log；修复后 mismatch=0），
  //       不再是红项 —— 把已转绿的配置继续列在「已知红项审计」里本身就是新的错误归因，
  //       故移出清单；其证据保留在 baseline/guard 日志与本文件测试 6 的护栏里。
  // ==========================================================================
  it should "self-audit: attribute all 3 post-#122 known-red configs plus the #126 matrix1 subnormal finding (fixed by #128) and self-prove the four E1 trigger conditions" in {
    /** D.1：三个 blockSize 参数【各自独立】。调用点必须按用例真实 regFile 取值填写，
      * 绝不可再压成单值（旧 mxSel 压缩是 E4 错误归因的直接成因）。 */
    def mk(m: Int, n: Int, k: Int, aF: DataFormat.Type, bF: DataFormat.Type, cF: DataFormat.Type,
           lw: Boolean, aMxBlkSel: Int, bMxBlkSel: Int, cMxBlkSel: Int) = DebugParams(
      m = m, n = n, k = k, aFmt = aF, bFmt = bF, cFmt = cF, loadWMode = lw,
      aMxBlkSel = aMxBlkSel, bMxBlkSel = bMxBlkSel, cMxBlkSel = cMxBlkSel,
      addrModeSel = 0, rnd = 0, seed = 1L, strictAddr = false, maxCycles = 8000,
      waveTarget = "", testHint = "audit")

    // ------------------------------------------------------------------------
    // Part 1：当前真实红项 / ignore 集合（3 个 QuantSpec 红 + 1 个 #126 matrix1 ignore），
    //   逐条给出 detect() 期望与归因
    // (标签, params【真实 regFile 取值 + 源码取证行号】, 实际 cDmaExtW,
    //  期望 detect() 命中集【""=不命中任何区间】, 归因任务, 归因说明)
    // ------------------------------------------------------------------------
    val audit: Seq[(String, DebugParams, Int, String, String, String)] = Seq(
      // 源码取证 HiveCoreSimCaseQuantSpec.scala L148-149（调用点）+ L1606（控制字）：
      //   runQuantErrCase(aFmt=1 FP16, bFmt=1 FP16, cFmt=4 MXE5M2, cMxBlk=2, loadWMode=0,
      //                   expErrCode=2, M=4, N=16, K=32)
      //   controlWord6 = ((loadWMode=0)&1)<<28 | ((cMxBlk=2)&3)<<26
      //   → aMxBlkSel=0、bMxBlkSel=0、cMxBlkSel=2（旧 mk 会错填成三者同为 2）
      ("QuantSpec actively reject quant config blk32 over totalN",
        mk(4, 16, 32, DataFormat.FP16, DataFormat.FP16, DataFormat.MXE5M2, false, 0, 0, 2), 256,
        "", "#124 errCode 分支（本轮按用户指示推迟）",
        "实际成因 = errCode 0/1/2 被并发改动合并为单一 0，期望 errCode=2 实得 0（本任务禁动区）。" +
          "★ 期望命中集由 \"E3\" 改为 \"\"：E3 已由 #124 ② 修复并从 detect() 撤下" +
          "（scaleCRowAddressOffset FLOOR→CEIL），故本配置不再落入任何 Section E 区间。" +
          "历史附注（撤下前的表述，保留作证据链）：N=16 % cMxBlk=32 != 0 曾命中 E3，" +
          "但硬件 config-err 从不进计算，E3 的 scaleC 行偏移症状在该用例中【从不显现】—— " +
          "即这条红项与 E3 无因果关系，撤下 E3 不改变它的红/绿状态（它仍因 errCode 合并而红）。" +
          "errCode 0/1/2 的分立恢复属用户本轮明确推迟的范围，勿在此处顺手改。"),
      // 源码取证 HiveCoreSimCaseQuantSpec.scala L1231-1232（runMxQuantGemm 控制字）：
      //   controlWord6 = (1L<<28) | ((blkSel=1)&0x3)<<26 | ((2L)&0x3)<<24 | ((2L)&0x3)<<22
      //   → loadWMode=1、cMxBlkSel=1(M16=16)、bMxBlkSel=2(M32=32)、aMxBlkSel=2(M32=32)
      //   reg7（L1228-1230）：aFmt=MXE4M3(3)、bFmt=MXE5M2(4)、cFmt=outFmt3=MXE4M3(3)、rnd=0
      //   几何：M=32 N=32 K=64（L173-176）
      ("QuantSpec dualMX K64 E4M3xE5M2 blk16",
        mk(32, 32, 64, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE4M3, true, 2, 2, 1), 256,
        "", "#127",
        "修正 mk() 后 detect() 返回【空】：bMxBlk=32 → E4 判据 bMxBlk<32 从未成立（旧 mxSel=1 " +
          "压缩造假参数才误报 E4 → 错误归因 #118）；beatsPerRow=ceil(16×8/256)=1 → E1 不命中；" +
          "N=32 % cMxBlk=16 = 0 → 即便在 E3 撤下前也不命中；K=64 % 32 = 0 → E5 不命中；" +
          "bFmt 非 INT → 即便在 E2 撤下前也不命中（E2/E3 已由 #124 ①/② 修复并从 detect() 撤下）。" +
          "不属任何 Section E 区间 → 归 #127（MX 多 kTile 量化输出数据路缺陷：onset beat#0、" +
          "beatsPerRow=1、#122 因果干预零影响、E4M3 逐元素比值 0.0625~58.67 非均匀非 2 的幂 " +
          "→ 不是共享 scale 错误，是真实数值路分歧）。"),
      ("QuantSpec dualMX dmaCScale backpressure K64 blk16",
        mk(32, 32, 64, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE4M3, true, 2, 2, 1), 256,
        "", "#127",
        "与上项共用同一 runMxQuantGemm 运行器、同一 regFile 取值（L180-184），仅 " +
          "cScaleBackpressure=true；#122 因果干预下失败值逐位不变 → 同一 #127 根因，背压不改变缺陷。"),
      // ── 【#126 发现 → #128 修复：本 spec 测试 8 matrix1（曾 ignore，现 active）】──
      //   本项经 #126 取证与 #127 是【不同缺陷】，#128 第一阶段进一步确证其根因是【金锚对
      //   FP16 subnormal 保真度不足】而非 RTL 缺陷（FTZ 是硬件显式设计意图，见 matrix1 注释块）。
      //   #128 第二阶段已对金锚 compare() 做 FTZ 感知修复（阈值由格式推导、不放宽容差），
      //   matrix1 已由 ignore 转 active 且 1024/1024 全绿。此登记项保留作 detect() 区间反检
      //   （本配置仍不属任何 Section E 区间），归因如实更新为「#128 已修复」。
      //   真实 regFile 取值（源码取证本文件 matrix1 调用点）：FP16→FP16、非 MX 输入 → a/bMxBlkSel
      //   硬件不消费（填 M32=2 占位）、非 MX 输出 → cMxBlkSel 硬件不消费（填 M8=0 占位）、loadW=0、
      //   seed=126001。detect() 返回【空】（非 MX → E4/E5 不适用；E2/E3 已由 #124 ①/② 撤下）。
      ("ParamDebugSpec matrix1 FP16->FP16 M32N32K32 seed126001 (#128 FIXED->active)",
        mk(32, 32, 32, DataFormat.FP16, DataFormat.FP16, DataFormat.FP16, false, 2, 2, 0), 256,
        "", "#126 发现→#128 已修复（非#122/#124/#127）",
        "曾单元素失配 C(29,15) hw=0x0000(+0.0) ref(golden fp32 累加)=1.58474e-05；ref < FP16 最小正规数 " +
          "2^-14≈6.1e-5 → 落在 FP16 subnormal 区，硬件 FP16 输出路 flush-to-zero（设计意图，非缺陷）；" +
          "金锚 compare() 旧版 ref 用未再编码的理想 fp32 值且 tolAbs=1e-6 过紧 → 判为假红。#128 修复：" +
          "compare() 引入 FTZ 感知期望 refEff（|ref|<minNormal(cFmt)→取 0.0，阈值由格式推导，容差未动）。" +
          "六通道地址对账 0 失配、beatsPerRow=1 → 非 #122；非 MX 输出、单元素 → 非 #127；detect() 空 → " +
          "非任何 Section E。交叉证据：同配置 seed=1234 由 DebugCase e3(guard122Beats1Params) 证明 " +
          "1024/1024 全绿。修复后 matrix1 已转 active、1024/1024 全绿。"))

    println(s"[$TAG] ═══ 自测 Part 1：detect() 反检【当前真实红项集合 = 3 QuantSpec 红 + 1 #128 已修复(matrix1)】（mk() 已拆三独立 blk 参数）═══")
    var part1Ok = true
    audit.foreach { case (label, p, extW, expIds, task, why) =>
      val w = SectionEBanner.detect(p, cfg.totalN, extW)
      val ids = w.map(_.id).mkString(",")
      val ok = ids == expIds
      if (!ok) part1Ok = false
      val expStr = if (expIds.isEmpty) "无（不属任何 Section E 区间）" else expIds
      val idsStr = if (ids.isEmpty) "无" else ids
      println(f"[$TAG] $label%-58s extW=$extW%3d → 命中[$idsStr] 期望[$expStr] 归因$task ${if (ok) "✓" else "✗ 不符!"}")
      println(s"[$TAG]   真实 regFile: aMxBlk=${p.aMxBlk} bMxBlk=${p.bMxBlk} cMxBlk=${p.cMxBlk} " +
        s"loadW=${if (p.loadWMode) 1 else 0} | #122 四条件 ${SectionEBanner.e1Geometry(p, cfg.totalN, extW)}")
      println(s"[$TAG]   归因说明: $why")
    }
    withClue("Part 1：4 项（3 QuantSpec 红 + 1 #128 已修复 matrix1）的 detect() 命中集必须与逐条核对的真实 regFile 取值一致：") {
      part1Ok should be(true)
    }


    // ------------------------------------------------------------------------
    // Part 2：#122（原 E1）四条触发条件表征的正反双证（锁死诊断结论本身）
    //   取值全部来自 HiveCoreDebugCase 护栏族的真实硬编码参数（逐个回源码核对）：
    //     e1 = guard122AllFourParams  M32 N32 K32 FP16→FP32 loadW=0（四条全满足，正例）
    //     e2 = guard122NTiles1Params  M32 N16 K32 FP16→FP32 loadW=0（仅 nTiles=1）
    //     e3 = guard122Beats1Params   M32 N32 K32 FP16→FP16 loadW=0（仅 beatsPerRow=1）
    //     d2 = issueBFp16K16Params    M32 N32 K16 FP16→FP32 loadW=0（仅 kTiles=1）
    //     b2 = builtinIntMNParams     M16 N32 K32 INT16→INT32 loadW=0（仅 mTiles=1）
    //   断言：正例 triggers=true 且 missing 为空；四个对照 triggers=false 且 missing
    //   【恰好一条】并被精确点名 → 四条触发条件缺一不可、且无多余条件。
    //   为何用护栏族而非硬编码历史红项：历史 9 项已转绿，其价值是历史记录；护栏族配置
    //   是活跃用例，正反双证能永久锁住表征（任一条被误删/误加都会使本自测红）。
    // ------------------------------------------------------------------------
    val charac: Seq[(String, DebugParams, Int, Boolean, String)] = Seq(
      ("e1 正例：四条全满足 FP16 M32N32K32->FP32 extW256",
        mk(32, 32, 32, DataFormat.FP16, DataFormat.FP16, DataFormat.FP32, false, 2, 2, 0), 256, true, ""),
      ("e2 对照：仅缺 nTiles（M32 N16 K32 FP16->FP32）",
        mk(32, 16, 32, DataFormat.FP16, DataFormat.FP16, DataFormat.FP32, false, 2, 2, 0), 256, false, "nTiles=1"),
      ("e3 对照：仅缺 beatsPerRow（M32N32K32 FP16->FP16）",
        mk(32, 32, 32, DataFormat.FP16, DataFormat.FP16, DataFormat.FP16, false, 2, 2, 0), 256, false, "beatsPerRow=1"),
      ("d2 对照：仅缺 kTiles（M32 N32 K16 FP16->FP32）",
        mk(32, 32, 16, DataFormat.FP16, DataFormat.FP16, DataFormat.FP32, false, 2, 2, 0), 256, false, "kTiles=1"),
      ("b2 对照：仅缺 mTiles（INT16 M16 N32 K32->INT32）",
        mk(16, 32, 32, DataFormat.INT16, DataFormat.INT16, DataFormat.INT32, false, 2, 2, 0), 256, false, "mTiles=1"))

    println(s"[$TAG] ═══ 自测 Part 2：#122 四条触发条件表征正反双证（kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1）═══")
    var part2Ok = true
    charac.foreach { case (label, p, extW, expTrig, expMissing) =>
      val g = SectionEBanner.e1Geometry(p, cfg.totalN, extW)
      val trigOk = g.triggers == expTrig
      val missOk = if (expTrig) g.missing.isEmpty else g.missing == Seq(expMissing)
      val ok = trigOk && missOk
      if (!ok) part2Ok = false
      println(f"[$TAG] $label%-52s → $g%-58s triggers=${g.triggers}%5s（期望 $expTrig）" +
        s" missing=[${g.missing.mkString(",")}]${if (ok) " ✓" else " ✗ 不符!"}")
    }
    withClue("Part 2：#122 四条触发条件表征必须被正反双证锁死（正例四条全满足、四个对照各缺且仅缺一条）：") {
      part2Ok should be(true)
    }
    println(s"[$TAG] ✓ 自测通过：Part 1 = 4/4 项 detect() 命中集与真实 regFile 一致并逐条归因" +
      "（2 项如实不命中任何区间归 #127、1 项归 #124、1 项 #126 新发现 FP16 subnormal FTZ 明确非 #127）；" +
      "Part 2 = 5/5 四条件表征正反双证成立")
  }

  // ==========================================================================
  // 测试 8（#126 交付）：已验证配置矩阵 —— 6 组独立用例，一条裸命令跑通整个矩阵
  // --------------------------------------------------------------------------
  // 用户原话（唯一需求来源，不扩围）：
  //   "这个 case 只需要先跑过输入是 fp16/bf16/mxfp8（32 量化），输出是 fp16 和
  //    mxfp8（16 量化）即可。其他 cross 配置由我自己来 debug。"
  // 几何统一 M=N=K=32、aFmt=bFmt 同格式；每组 strictAddr=true（六通道逐拍地址对账）、
  // 独立语义化波形（落 sim/debug/）、逐位匹配 golden 的元素计数证据。
  // ★ 全部格式/块大小/地址模式/舍入模式一律走枚举（DataFormat/MXBlockSize/AddrMode/
  //   RoundingMode），零裸数字（672fb37 曾重排枚举，硬编码数字会静默错位）。
  // ★ 三个 blockSize sel【各自独立】填写，绝不压成单值（见测试 7 D.1 的 mk() 教训）。
  //
  // 几何量（totalN=16、cDmaExtW=256）：
  //   mTiles = ceil(32/16) = 2；nTiles = ceil(32/16) = 2。
  //   非 MX 输入（#1~#4）：kTiles = ceil(32/16) = 2；MX 输入（#5/#6）：kTiles = 32/32 = 1。
  //   输出 beatsPerRow：FP16 = ceil(16×16/256) = 1；MXE4M3 = ceil(16×8/256) = 1。
  //   → 六组 beatsPerRow 全为 1，#122 四条件（需 beatsPerRow>1）结构上【不可能触发】。
  // ==========================================================================

  // 枚举派生的 sel 常量（供矩阵用例填写 DebugParams 的三个独立 blockSize / addrMode / rnd）
  private val SEL_BLK_M32: Int = MXBlockSize.M32.litValue.toInt   // MX 输入 aMxBlk=bMxBlk=32（=kPerBeat）
  private val SEL_BLK_M16: Int = MXBlockSize.M16.litValue.toInt   // MX 输出 cMxBlk=16（32 会触发 errCode2，勿用）
  private val SEL_BLK_M8:  Int = MXBlockSize.M8.litValue.toInt    // 非 MX 侧占位（硬件不消费该字段）
  private val SEL_ADDR_MAT: Int = AddrMode.MAT.litValue.toInt
  private val SEL_RND_RNE:  Int = RoundingMode.RNE.litValue.toInt

  /** #126 矩阵用例构造器：三个 blockSize sel【各自独立】传入，绝不压成单值。
    * 几何固定 M=N=K=32；strictAddr=true；addrMode/rnd 走枚举。 */
  private def matrixParams(
      aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
      loadWMode: Boolean, aMxBlkSel: Int, bMxBlkSel: Int, cMxBlkSel: Int,
      seed: Long, wave: String, hint: String): DebugParams = DebugParams(
    m = 32, n = 32, k = 32,
    aFmt = aFmt, bFmt = bFmt, cFmt = cFmt, loadWMode = loadWMode,
    aMxBlkSel = aMxBlkSel, bMxBlkSel = bMxBlkSel, cMxBlkSel = cMxBlkSel,
    addrModeSel = SEL_ADDR_MAT, rnd = SEL_RND_RNE, seed = seed,
    strictAddr = true, maxCycles = 8000,
    waveTarget = wave, testHint = hint)

  /** 跑一组矩阵用例并打印【逐位匹配元素计数 + 六通道地址对账零失配】证据（不接受"跑完没报错"）。 */
  private def runMatrixCase(label: String, risk: String, p: DebugParams): Unit = {
    println(s"[$TAG] ═══ #126 矩阵用例：$label ═══")
    println(s"[$TAG] 风险标注：$risk")
    val r = runParamDebugCollect(p, expectPass = true)   // 内部已断言 mismatch==0 ∧ addrBadCnt==0
    val elems = p.m * p.n
    println(s"[$TAG] ✓ [$label] 逐位匹配 golden 元素计数 = ${elems - r.mismatch}/$elems" +
      s"（mismatch=${r.mismatch}）；六通道地址对账不一致拍数=${r.addrBadCnt}；" +
      s"done=${r.done} err=${r.errSeen}")
    withClue(s"[$label] 必须逐位匹配 golden（零失配）：") { r.mismatch should be(0) }
    withClue(s"[$label] 六通道逐拍地址对账必须零失配：") { r.addrBadCnt should be(0) }
    withClue(s"[$label] 波形产物应存在且非空：") {
      val w = Paths.get(p.waveTarget)
      Files.exists(w) should be(true)
      Files.size(w) should be > 0L
      println(s"[$TAG]   波形 ${p.waveTarget} = ${Files.size(w)} bytes")
    }
  }

  // ---- #1 FP16 → FP16（loadWMode=0）----
  // 预判风险：【低】。LoadWSpec 已证 FP16 同族直出；kTiles=2∧mTiles=2∧nTiles=2 但 beatsPerRow=1，
  //   不满足 #122 四条件（缺 beatsPerRow>1）。
  // ────────────────────────────────────────────────────────────────────────────
  // ★★ 本用例现在锁的是【FP16 subnormal FTZ 金锚保真度】（#128 第二阶段修复后转 active）。
  //   历史（#126）：本用例在 seed=126001 下曾【红】，mismatch=1/1024（单元素 C(29,15)）。
  //   ── 精确归因（当时证据，保留作护栏语义说明）──
  //   · 几何量：totalN=16、kTiles=2、mTiles=2、nTiles=2、beatsPerRow=ceil(16×16/256)=1。
  //   · 六通道逐拍地址对账：不一致拍数=0（A=128/B=64/C=64/sA=0/sB=0/sC=0 全部匹配）。
  //   · 唯一失配元素：C(29,15) hw=0x0000(+0.0) ref(golden fp32 累加)=1.58474e-05，
  //     absErr=1.585e-05、relErr=1.000e+00。
  //   · 数值判定：FP16 最小正规数 = 2^-14 ≈ 6.104e-05；ref=1.58e-05 < 该阈值 → 落在 FP16
  //     【subnormal（非规格化）区】。硬件 HiveOutCvt 的 FP16 输出路对 subnormal 做
  //     【flush-to-zero(FTZ)】→ 输出 0x0000。金锚 compare() 旧版拿【未再编码的理想 fp32
  //     累加值】配 tolAbs=1e-6 比较，容不下 1.585e-05 的 subnormal flush → 判为假红。
  //   · 排除已知区间：beatsPerRow=1 且地址全绿 → 【非 #122】；非 MX 输出、单元素、relErr=1.0
  //     单一 → 【非 #127】；detect() 返回【空】→ 非任何 Section E（#124 E5 不适用；
  //     E2/E3 当时亦不适用，且现已由 #124 ①/② 修复并撤下）。
  //   · 交叉证据（配置本身清白）：HiveCoreDebugCase 的 e3 护栏 guard122Beats1Params =
  //     【完全相同配置】M32 N32 K32 FP16→FP16 loadW=0 strictAddr=true，仅 seed=1234(DEF_SEED)，
  //     基线中【1024/1024 全绿】。故 FP16→FP16 数据路结构上正确，本失配纯属 seed=126001 的
  //     随机矩阵恰好在 C(29,15) 产生一个 subnormal 量级残差（大数相消）而暴露的【金锚保真度差异】。
  //   ── #128 裁定与修复（分支 A：FTZ 确为设计意图，非 RTL 缺陷）──
  //   第一阶段取证确证 FTZ 是硬件【显式设计意图】（HiveOutCvt 类头注释"本硬件族不产生目标
  //   格式次正规数"、fp32ToFp16Rnd underflow=exp<113→signedZero、swFp32ToFp16 e<113→signedZero、
  //   SimCase golden isStrict/strictExpect 手工锚点已建模同一 FTZ）。据此对金锚 compare() 做
  //   【保真度修复】（非放宽容差）：比较基准由理想 golden ref 改为 FTZ 感知期望 refEff ——
  //   当 |ref| 非零且 < minNormal(cFmt)（阈值 = 2^(1-bias) 由输出格式推导，FP16=2^-14）时，
  //   期望值取 0.0 与硬件 FTZ 一致。tolRel/tolAbs【一律未动】。
  //   ⇒ 本用例现锁：FP16 subnormal 元素的金锚期望与硬件 FTZ 行为一致，必须【1024/1024 全绿】。
  //     seed=126001 保持不变（它正是暴露 subnormal 的确定性种子，是护栏价值所在）。
  //     若本用例转红，说明 #128 的 FTZ 感知比较被回退，或硬件 FP16 subnormal 行为发生变化。
  // ────────────────────────────────────────────────────────────────────────────
  it should "pass 126 matrix1 FP16 to FP16 M32N32K32" in {
    runMatrixCase("#1 FP16→FP16",
      "低风险：LoadWSpec 已证同族；beatsPerRow=1 结构上绕开 #122。#128 修复后锁 FP16 subnormal FTZ 金锚保真度",
      matrixParams(DataFormat.FP16, DataFormat.FP16, DataFormat.FP16,
        loadWMode = false, SEL_BLK_M32, SEL_BLK_M32, SEL_BLK_M8,
        seed = 126001L, wave = "sim/debug/param_matrix1_fp16_to_fp16.fst",
        hint = "matrix1-fp16-to-fp16"))
  }

  // ---- #2 FP16 → MXE4M3 blk16（loadWMode=0）----
  // 风险：【高】。非 MX 输入 → mxfp8 blk16 输出这条路径疑似【零测试覆盖】（既有量化用例
  //   输入侧全是 MX）。架构上合法（约束一：C 侧量化消费 fp32 累加器，与输入格式无关）。
  //   ★ 具体风险：非 MX 输入 K=32 → kTiles=2，正是 #127（MX 多 kTile 量化输出数据路：
  //   onset=DMA-C store beat#0、beatsPerRow=1、kTiles=1 同型通过、kTiles=2 失败）的相邻
  //   未测区间 → 有可能撞 #127。若红：按失败处置纪律归因、ignore、登记，严禁改金锚/RTL/容差。
  it should "pass 126 matrix2 FP16 to MXE4M3 blk16 M32N32K32" in {
    runMatrixCase("#2 FP16→MXE4M3(blk16)",
      "高风险：非 MX 输入→mxfp8 输出疑零覆盖；kTiles=2 落在 #127 相邻未测区间，可能撞 #127",
      matrixParams(DataFormat.FP16, DataFormat.FP16, DataFormat.MXE4M3,
        loadWMode = false, SEL_BLK_M32, SEL_BLK_M32, SEL_BLK_M16,
        seed = 126002L, wave = "sim/debug/param_matrix2_fp16_to_mxe4m3_blk16.fst",
        hint = "matrix2-fp16-to-mxe4m3"))
  }

  // ---- #3 BF16 → FP16（loadWMode=0）----
  // 风险：【中】。BF16 作输入没有既有用例；输出 FP16 单拍，结构上绕开 #122。
  it should "pass 126 matrix3 BF16 to FP16 M32N32K32" in {
    runMatrixCase("#3 BF16→FP16",
      "中风险：BF16 作输入无既有用例；beatsPerRow=1 绕开 #122",
      matrixParams(DataFormat.BF16, DataFormat.BF16, DataFormat.FP16,
        loadWMode = false, SEL_BLK_M32, SEL_BLK_M32, SEL_BLK_M8,
        seed = 126003L, wave = "sim/debug/param_matrix3_bf16_to_fp16.fst",
        hint = "matrix3-bf16-to-fp16"))
  }

  // ---- #4 BF16 → MXE4M3 blk16（loadWMode=0）----
  // 风险：【高】。同 #2（非 MX 输入→mxfp8 输出、kTiles=2 撞 #127 相邻区间），叠加 BF16
  //   输入无既有用例的中风险。若红按纪律归因/ignore/登记，严禁改金锚/RTL/容差。
  it should "pass 126 matrix4 BF16 to MXE4M3 blk16 M32N32K32" in {
    runMatrixCase("#4 BF16→MXE4M3(blk16)",
      "高风险：非 MX 输入→mxfp8 输出疑零覆盖 + BF16 输入无既有用例；kTiles=2 可能撞 #127",
      matrixParams(DataFormat.BF16, DataFormat.BF16, DataFormat.MXE4M3,
        loadWMode = false, SEL_BLK_M32, SEL_BLK_M32, SEL_BLK_M16,
        seed = 126004L, wave = "sim/debug/param_matrix4_bf16_to_mxe4m3_blk16.fst",
        hint = "matrix4-bf16-to-mxe4m3"))
  }

  // ---- #5 MXE4M3 blk32 → FP16（loadWMode=1，MX 强制否则 errCode4）----
  // 风险：【低】。MxSpec 走的就是 MXE4M3 fp16 直出；MX 输入 K=32 → kTiles=32/32=1，
  //   落在 #127 已知通过区（kTiles=1）；beatsPerRow=1 绕开 #122。
  //   aMxBlk=bMxBlk=32（=kPerBeat=totalN×2）；loadWMode=1 为 MX 硬约束。
  it should "pass 126 matrix5 MXE4M3 blk32 to FP16 M32N32K32 loadW1" in {
    runMatrixCase("#5 MXE4M3(blk32)→FP16",
      "低风险：MxSpec 已证 MXE4M3 fp16 直出；kTiles=1 落 #127 通过区；beatsPerRow=1 绕开 #122",
      matrixParams(DataFormat.MXE4M3, DataFormat.MXE4M3, DataFormat.FP16,
        loadWMode = true, SEL_BLK_M32, SEL_BLK_M32, SEL_BLK_M8,
        seed = 126005L, wave = "sim/debug/param_matrix5_mxe4m3_blk32_to_fp16.fst",
        hint = "matrix5-mxe4m3-to-fp16"))
  }

  // ---- #6 MXE4M3 blk32 → MXE4M3 blk16（loadWMode=1）----
  // 风险：【低】。仓里已有 K=32 双 MX→mxfp8 blk16 的同型绿护栏；MX 输入 kTiles=1，
  //   落 #127 已知通过区；输出 MXFP8 单拍（beatsPerRow=1）绕开 #122。
  //   ★ cMxBlk 必须=16：blk=32 > totalN=16 会触发 errCode2 主动拒绝（仓里已有负向用例守着）。
  it should "pass 126 matrix6 MXE4M3 blk32 to MXE4M3 blk16 M32N32K32 loadW1" in {
    runMatrixCase("#6 MXE4M3(blk32)→MXE4M3(blk16)",
      "低风险：仓里已有 K=32 同型绿护栏；MX 输入 kTiles=1 落 #127 通过区；beatsPerRow=1 绕开 #122",
      matrixParams(DataFormat.MXE4M3, DataFormat.MXE4M3, DataFormat.MXE4M3,
        loadWMode = true, SEL_BLK_M32, SEL_BLK_M32, SEL_BLK_M16,
        seed = 126006L, wave = "sim/debug/param_matrix6_mxe4m3_blk32_to_mxe4m3_blk16.fst",
        hint = "matrix6-mxe4m3-to-mxe4m3"))
  }

  // ==========================================================================
  // 测试 9（#126 可选单变量实验）：MX 域内佐证"beatsPerRow>1 是 #122 的必要条件"
  // --------------------------------------------------------------------------
  // 与 HiveCoreDebugCase 的 d3【全同】（M=N=32、MXE4M3×MXE4M3、K=64、loadWMode=1、
  //   aMxBlk=bMxBlk=32、cDmaExtW=256、strictAddr=true、seed=1234、rnd=RNE、maxCycles=8000），
  //   【唯一变量】= cFmt FP32 → FP16。
  // d3 原为 MX K=64 输出 FP32、beatsPerRow=ceil(16×32/256)=2、曾确证被 #122 损坏，#122 修复
  //   后已转绿（1024/1024 bit-exact）。本实验把输出换成 FP16 → beatsPerRow=ceil(16×16/256)=1，
  //   几何量：kTiles=64/32=2、mTiles=2、nTiles=2 —— #122 四条件仅缺 beatsPerRow>1。
  // 价值：目前"beatsPerRow>1 是必要条件"这条排除只有【非 MX 域】证据（LoadWSpec 单点 +
  //   测试 7 Part 2 e3 纯计算对照）；本实验在【MX 域内】用真实仿真独立佐证。
  //   绿 → 佐证成立（MX 域内单拍即绕开 #122）；红 → 新信息，如实报告并按纪律归因/ignore。
  // ==========================================================================
  it should "prove 122 beatsPerRow necessary via MX K64 to FP16 single variable" in {
    val expP = DebugParams(
      m = 32, n = 32, k = 64,
      aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP16, // 唯一变量：d3 是 FP32
      loadWMode = true, aMxBlkSel = SEL_BLK_M32, bMxBlkSel = SEL_BLK_M32, cMxBlkSel = SEL_BLK_M8,
      addrModeSel = SEL_ADDR_MAT, rnd = SEL_RND_RNE, seed = 1234L,   // seed 与 d3 一致
      strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_exp_mx_k64_to_fp16.fst",
      testHint = "mx-k64-to-fp16")

    println(s"[$TAG] ═══ #126 单变量实验：MX K=64 → FP16（与 d3 唯一差异 = cFmt FP32→FP16）═══")
    println(s"[$TAG] 目的：在 MX 域内独立佐证 beatsPerRow>1 是 #122 的必要条件")
    val g = SectionEBanner.e1Geometry(expP, cfg.totalN, cfg.cDmaExtW)
    println(s"[$TAG] #122 四条件几何量：$g triggers=${g.triggers} missing=[${g.missing.mkString(",")}]")
    // 前置断言：本实验必须【仅缺 beatsPerRow>1 一条】，否则单变量对照不成立
    withClue("单变量实验前提：kTiles>1∧mTiles>1∧nTiles>1 但 beatsPerRow=1（仅缺一条）：") {
      g.triggers should be(false)
      g.missing should be(Seq(s"beatsPerRow=${g.beatsPerRow}"))
      g.beatsPerRow should be(1)
    }
    val r = runParamDebugCollect(expP, expectPass = true)
    val elems = expP.m * expP.n
    println(s"[$TAG] 实验结果：逐位匹配元素计数 = ${elems - r.mismatch}/$elems（mismatch=${r.mismatch}）、" +
      s"六通道地址对账不一致拍数=${r.addrBadCnt}、done=${r.done} err=${r.errSeen}")
    println(s"[$TAG] ⇒ 结论：MX 域内 K=64（kTiles=2）单拍 FP16 输出 mismatch=${r.mismatch}。" +
      (if (r.mismatch == 0) "绿 → 佐证成立：beatsPerRow=1 即绕开 #122（MX 域内独立证据）。"
       else "红 → 新信息，如实报告并按失败处置纪律归因。"))
    withClue("MX K=64→FP16 单拍必须逐位匹配 golden：") { r.mismatch should be(0) }
    withClue("六通道逐拍地址对账必须零失配：") { r.addrBadCnt should be(0) }
  }

  // ==========================================================================
  // 测试 9【#124 ①/② 永久护栏】：colTileAddressOffset 运行期化 + scaleC 行偏移 CEIL
  // --------------------------------------------------------------------------
  // 【为何必须新增，而不是「改完就算」】#124 ①/② 修复的两个区间在修复前【零测试覆盖】：
  //   · ① 的破损区间 = INT8/INT32 输入（colTile 需 16/64，旧常数给 32）。全仓活跃用例
  //     的输入格式只有 MX8/FP16/BF16/INT16 四档 —— 这四档 elemsPerLane×memElemBytes
  //     恰好都 = 2 = bW/8，与旧常数【数值恒等】，故退化长期隐形（这正是 Section E【E2】
  //     记录的「巧合等价」机制）。INT8 在 HiveCoreSpec L25 只作为 elaboration 冒烟的
  //     supportedFmts 出现，从不跑 GEMM；INT32 作输入更是端到端不可用（见下）。
  //   · ② 的破损区间 = 量化 C 输出 ∧ N % cMxBlk != 0。全仓活跃量化用例（QuantSpec
  //     runMxQuantGemm ×3、ParamDebugSpec matrix2/4/6、MxSpec runMxGemm）一律 N=32 ∧
  //     blk=16 ⇒ 32%16=0 ⇒ FLOOR ≡ CEIL，同样数值恒等。曾命中该区间 QuantSpec ②/⑤
  //     （M=4 N=24 blk16）整块位于 L58 起的 /* */ 内，是死码，不参与回归。
  // ⇒ 结论：不加护栏，①/② 的修复【无任何测试能证明其生效】，且日后任何人把赋值式
  //   改回常数/改回 FLOOR 都不会有任何用例转红。以下三条即为这两处的永久牙齿。
  //
  // 【#124 ①/② 的不退化论证（与下述护栏互补，二者缺一不可）】
  //   护栏证明「修复在破损区间生效」；不退化证明「修复在已绿区间零影响」。后者由
  //   穷举得出：全仓活跃用例的 (aFmt,bFmt) ∈ {MX8,FP16,BF16,INT16}² ⇒ 新式恒 = 32
  //   = 旧常数值；全仓活跃量化用例 N%cMxBlk = 0 ⇒ CEIL ≡ FLOOR。故 ①/② 对现役
  //   9 个 suite 的期望地址序列【逐值零变化】，回归若出新红必是改动本身有问题。
  //
  // 【INT32 档为何不建端到端护栏（如实呈报，非遗漏）】
  //   HiveWorkUnit L442 的 MAC 选择判据是 `isInt = aFmt===INT8 || aFmt===INT16`，
  //   INT32/INT4 作输入会落到 sumFp（浮点累加器）⇒ INT32 输入端到端【不可用】。
  //   故 ① 的 INT32 档（colTile=64）无法用 E2E 护栏覆盖 —— 它与 INT8 档是【同一处】
  //   编译期常数退化，G1/G2 用 INT8 覆盖即等价覆盖该缺陷类。INT32 作输入的可用性
  //   属另一独立缺陷，已在 #124 报告呈报，勿在此处顺手改 MAC 判据。
  //
  // 【三条护栏的统一断言口径（实测后确定，不可各自为政）】
  //   硬断言 = 【六通道逐拍地址对账 addrBadCnt==0】+【done==true ∧ errSeen==false】
  //            + 各自的几何前提（triggers/nTiles/kTiles/N%cMxBlk/precheckConfigErr）。
  //   观测项 = C 逐元素数据 mismatch，只 println 不硬断言。
  //   理由：①/② 修复的都是【地址量】，地址对账就是它们的牙齿本体；C 数据是否匹配
  //   还牵涉数值路/lane 解包等与 ①/② 无关的独立环节。若对数据下硬断言，任何数据路
  //   既存缺陷都会把红归因到 #124，使 leader 判据「出现新红优先怀疑改动本身」失去
  //   可判定性。三条口径一致后，本组护栏的红/绿【只】反映 ①/② 的地址正确性。
  //
  // 【牙齿的运行时双向因果实验（三份日志，2026-09-11，均已归档 build/）】
  //   ① 回退（124_causal_revert.log）：a/bColTileAddressOffset 改回 totalN×(aW/8)=32
  //      → G1/G2 FAILED 于「dmaA beat#16 addr mismatch: 32 was not equal to 16」，
  //        strictAddr fail-fast 使仿真在 beat#16 中止（波形 405 KB，无 done）；
  //        G3 不受影响仍全绿（FP16 ⇒ 新式恒 = 32 = 旧常数，正是不退化论证的实证）。
  //      恢复新式（124_full_regression.log）→ G1/G2 地址对账 0 失配、跑完全程。
  //   ② 回退（124_causal_revert2.log）：M16 分支的 scaleCRowAddressOffset 改回
  //      FLOOR（n/16）→ G3 FAILED 于「dmaCScale beat#1 addr mismatch: 5242881 was
  //      not equal to 5242882」（SC_BASE=0x500000，golden +ceil(24/16)=2、HW +1，
  //      delta=−1，首现 mm=1），与落码前手算【逐字吻合】。恢复 CEIL → 全绿。
  //   ⇒ ①/② 均已证明「回退必红、恢复必绿」，不是无牙齿的形式化修改。
  //
  // 【实测暴露的新发现（如实呈报，不属 #124，建议新开任务）】
  //   G1/G2 在地址全绿后暴露 C 数据 mismatch=509/512 与 254/256（几乎全失配、hw 与
  //   ref 值无相关性，且 G1 出现 hw C(0,8..15) 与 C(0,16..23) 逐元素相同的 8 元素组
  //   重复）。根因取证见 G1 scaladoc 的 ★ 段：RTL 地址路对 INT8 按【密集 1 字节】
  //   （aRowOff=k×1、colTile=totalN×1，与金锚一致），而数据路 HiveCvtOpInt 按
  //   【16-bit lane 取低 8 位符号扩展】⇒ 隐含 2 字节/lane 对齐 ⇒ RTL 自身两路契约
  //   不一致。INT8 是唯一「一个 kTile 数据量(16B)不足一拍(32B)」的格式。该缺陷在
  //   ① 修好前被地址 fail-fast 完全掩盖，属首次被覆盖到的既存缺陷、非 ① 引入。
  // ==========================================================================

  /** G1：INT8 + loadWMode=0 —— 覆盖 ① 的【A kTile 支】与【B n-tile 支】。
    *
    * 单变量对照基准 = HiveCoreDebugCase.builtinIntMNParams()（b2，基线全绿）：
    *   b2 = M16 N32 K32 **INT16**→INT32 loadW=0 strictAddr=true。
    *   G1 与 b2 【几何、输出格式、loadWMode、strictAddr 全同】，唯一变量 = 输入元素
    *   字节宽度 INT16(2B)→INT8(1B)。INT16: elemsPerLane×memElemBytes = 1×2 = 2 ⇒
    *   colTile = 32（新旧同值，故 b2 一直绿）；INT8: 1×1 = 1 ⇒ colTile = 16
    *   （旧常数仍给 32 ✗）。⇒ 本用例是「同一处退化」的最小单变量复现器。
    *
    * 牙齿（手算，totalN=16、aEb=bEb=1、aKpb=bKpb=16、R_A=k×1=32、R_B=n×1=32）：
    *   · A 通道 kt=1：golden A_BASE + mm×32 + 1×**16**；旧 HW 给 +32 ⇒ 首现 dmaA
    *     beat#16（kt=1、mm=0）delta=+16。
    *   · B 通道 nt=1（loadW=0 下 colTile 语义 = n-tile 步进 = totalN×bEb = 16）：
    *     golden blockStart = B_BASE + 1×**16** + 15×32 + kt×16×32；旧 HW 给 nt×32
    *     ⇒ 首现 dmaB nt=1 块首 delta=+16。
    *   · 两支【同时】有牙齿，且 delta 同号不互相抵消。
    * 避坑：mTiles=ceil(16/16)=1 ⇒ #122（E1）四条触发条件缺 mTiles>1，不落入；
    *   beatsPerRow(INT32,extW256)=ceil(16×32/256)=2 与 b2 同值，非新变量。
    * 配置合法性：非 MX ⇒ precheckConfigErr 返回 None（不触任何 errCode 分支）；
    *   memElemBytes(INT8)=1>0 ⇒ validate 通过。属【正常配置路径】，非负向用例。
    *
    * ★★ 牙齿已由【运行时双向因果实验】证明（build/124_causal_revert.log，2026-09-11）：
    *   · 把 ① 临时回退成旧常数 totalN×(aW/8)=32 → 本用例 FAILED 于
    *     「dmaA beat#16 addr mismatch: 32 was not equal to 16」，且 strictAddr
    *     fail-fast 使仿真在 beat#16 即中止（波形仅 405 KB、无 done、跑不到 C 比对）。
    *     beat#16 = kt 由 0→1 的首拍，实测 delta=+16 与上方手算【逐字吻合】。
    *   · 恢复 ① 的新式（=16）→ 六通道地址对账不一致拍数=0、done=true、err=false、
    *     波形 3.55 MB 跑完全程。
    *   ⇒ colTile 运行期化是「地址对账由中止转全绿」的充分必要改动，牙齿确凿。
    * ★ 断言口径（与 G3 同，理由如实留痕）：本用例对【六通道地址逐拍对账 + done/err】
    *   下硬断言（= ① 的牙齿），对 C 逐元素数据只【打印不硬断言】。因为实测 C 数据
    *   mismatch=509/512（G2 为 254/256），根因经静态取证【不在 ① 的因果链上】：
    *     - RTL 地址路对 INT8 取 aRowOff=k×1、colTile=totalN×1（密集 1 字节），与
    *       金锚 buildMemory 的 A_BASE+mm×aRowOff+k×aEb（aEb=1）【一致】；
    *     - 但 RTL 数据路 HiveCvtOpInt（HiveWorkUnit.scala L54-58）是
    *       「取 16-bit lane 的低 8 位符号扩展」，隐含假设内存里 INT8 也按
    *       【2 字节/lane 对齐】排布 —— DMA 一拍搬 256 bit=32 字节切成 16 lane 后，
    *       lane[i] 低 8 位取到的是 byte[2i] 而非语义要求的 byte[i]；
    *     - 即【RTL 自身地址路与数据路的元素字节宽契约不一致】。INT8 是唯一
    *       「一个 kTile 的数据量(16 元素×1B=16B)不足一拍(32B)」的格式：FP16/BF16/
    *       INT16 是 16×2B=32B ✓、MX8 是 32×1B=32B ✓（elemsPerLane=2），只有
    *       INT8 是半拍 ⇒ 缺一级「byte[i]→lane[i] 低 8 位」的紧凑重排。
    *   该缺陷在 ① 修好之前【被地址 fail-fast 完全掩盖】（旧常数下仿真根本跑不到
    *   数据比对阶段），故它是首次被覆盖到的【既存缺陷】、不是 ① 引入的退化。
    *   若对数据下硬断言，本护栏会变成一条归因到 #124 的红，污染 ① 的信号 ——
    *   与 G3 的处理理由完全一致。已在 #124 报告点名，建议新开任务裁决。 */
  it should "guard #124-1: INT8 loadW=0 M16N32K32 exercises runtime colTile on A kTile and B nTile" in {
    val p = DebugParams(
      m = 16, n = 32, k = 32,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = false, aMxBlkSel = SEL_BLK_M8, bMxBlkSel = SEL_BLK_M8, cMxBlkSel = SEL_BLK_M8,
      addrModeSel = SEL_ADDR_MAT, rnd = SEL_RND_RNE, seed = 124001L,
      strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_guard124_1_int8_loadw0.fst",
      testHint = "guard-124-1-int8-loadw0")
    val g = SectionEBanner.e1Geometry(p, cfg.totalN, cfg.cDmaExtW)
    println(s"[$TAG] ═══ #124 ① 护栏 G1：INT8 loadW=0（colTile 需 16，旧常数给 32）═══")
    println(s"[$TAG] #122 四条件几何量：$g triggers=${g.triggers}（必须 false，否则本护栏不纯净）")
    withClue("G1 前提：不得落入 #122（E1）区间，否则失败归因会被污染：") { g.triggers should be(false) }
    withClue("G1 前提：必须是多 kTile（kt≥1 才乘 colTile）且多 nTile（nt≥1 才走 B n-tile 支）：") {
      g.kTiles should be(2)
      g.nTiles should be(2)
    }
    withClue("G1 前提：必须不被 precheckConfigErr 判为 config-err（非 MX ⇒ 不触任何分支）：") {
      precheckConfigErr(p, cfg.totalN, cfg.hasMx4) should be(None)
    }
    // expectPass=false：把 C 数据断言的决定权取回本用例（理由见上方 ★ 断言口径）
    val r = runParamDebugCollect(p, expectPass = false)
    println(s"[$TAG] G1 结果：done=${r.done} err=${r.errSeen} 六通道地址对账不一致拍数=${r.addrBadCnt} " +
      s"C 数据 mismatch=${r.mismatch}（数据项仅观测，不硬断言）")
    withClue("G1（① 的牙齿）：dmaA/dmaB 逐拍地址必须与金锚 colTile=totalN×1×1=16 完全一致" +
      "（回退旧常数 32 时此处必红于 dmaA beat#16，见上方因果实验留痕）：") {
      r.addrBadCnt should be(0)
    }
    withClue("G1：正常配置路径必须跑完（done）且不得出现 STATUS.err：") {
      r.done should be(true)
      r.errSeen should be(false)
    }
    if (r.mismatch != 0)
      println(s"[$TAG] ⚠ G1 观测到 C 数据 mismatch=${r.mismatch}/${p.m * p.n}：地址已全绿 ⇒ 与 ① 无关，" +
        "指向【INT8 lane 解包缺口】这一独立既存缺陷（RTL 地址路按密集 1B、数据路 " +
        "HiveCvtOpInt 按 lane 低 8 位 ⇒ 契约不一致）。如实呈报，不在 #124 范围内修。")
  }

  /** G2：INT8 + loadWMode=1 —— 覆盖 ① 的【B k-tile 支】（= Section E【E2】的历史区间）。
    *
    * 与 G1 的分工：G1 的 loadW=0 下 colTile 语义是 n-tile 步进；G2 的 loadW=1 下
    *   同一寄存器语义变成 k-tile 步进（金锚 bKTileOff = bKpb×bEb，#121 修正值）。
    *   一个式子覆盖两种语义正是 ① 的设计要点，故两支必须各有护栏，缺一不可。
    *
    * ★ N=16 ⇒ nTiles=1，这是【刻意选择】而非随手填：#124 ① 只归零 Δ 的 kt 维分量
    *   Δ_kt = kt×(C − bKpb×bEb)；nt 维分量 Δ_nt = nt×(kT×C − R) 属 RdOnly
    *   lastBTileStartAddr 累加模型的结构性残余，已由 #139 承接、本轮禁动。
    *   取 nTiles=1 ⇒ nt 恒为 0 ⇒ Δ_nt 恒为 0（与 K 是否整除无关），本护栏因而
    *   【只对 kt 维敏感】，不会把 #139 的未修偏差误报成 #124 ① 的回归。
    *   （本配置 K=32、bKpb=16 ⇒ K%bKpb=0，Δ_nt 即便 nt>0 也为 0，属双重保险。）
    *
    * 牙齿（手算，totalN=16、bEb=1、bKpb=16、R=k×1=32、T=16、kT=2、C=16）：
    *   · B 通道 kt=1：golden blockStart = B_BASE + 0×bNTileOff + 15×32 + 1×**16**
    *     = B_BASE+496；旧 HW（C=32）= B_BASE+512 ⇒ delta=+16，首现 dmaB beat#16。
    *     与 #121 当年 d3 的 MX 症状（exp 0x1003D0 / hw 0x1003E0、首现 beat#16）
    *     【同形不同因】：那次是金锚错硬件对（MX 区间），这次是硬件错金锚对（INT8 区间）。
    *   · A 通道 kt=1：同 G1，delta=+16。
    * 先例：本文件测试 2 group 3（FP16 loadWMode=1 非 MX）已证明「非 MX + loadW=1」
    *   是合法可跑配置（errCode4 只拦 MX+loadW=0），故 G2 属正常配置路径。 */
  it should "guard #124-2: INT8 loadW=1 M16N16K32 exercises runtime colTile as B kTile step" in {
    val p = DebugParams(
      m = 16, n = 16, k = 32,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = true, aMxBlkSel = SEL_BLK_M8, bMxBlkSel = SEL_BLK_M8, cMxBlkSel = SEL_BLK_M8,
      addrModeSel = SEL_ADDR_MAT, rnd = SEL_RND_RNE, seed = 124002L,
      strictAddr = true, maxCycles = 8000,
      waveTarget = "sim/debug/param_guard124_2_int8_loadw1.fst",
      testHint = "guard-124-2-int8-loadw1")
    val g = SectionEBanner.e1Geometry(p, cfg.totalN, cfg.cDmaExtW)
    println(s"[$TAG] ═══ #124 ① 护栏 G2：INT8 loadW=1（k-tile 支，原 Section E【E2】区间）═══")
    println(s"[$TAG] #122 四条件几何量：$g triggers=${g.triggers}（必须 false）")
    withClue("G2 前提：不得落入 #122（E1）区间：") { g.triggers should be(false) }
    withClue("G2 前提：nTiles 必须 = 1（隔离 #139 的 Δ_nt 残余，使本护栏只对 kt 维敏感）：") {
      g.nTiles should be(1)
      g.kTiles should be(2)
    }
    withClue("G2 前提：非 MX + loadW=1 必须不被 precheckConfigErr 判为 config-err（errCode4 只拦 MX）：") {
      precheckConfigErr(p, cfg.totalN, cfg.hasMx4) should be(None)
    }
    // expectPass=false + 断言口径与 G1 完全一致（因果实验铁证与 INT8 lane 解包缺口的
    // 完整根因取证见 G1 scaladoc 的 ★★/★ 两段，此处不重复；G2 的独立价值是覆盖
    // loadW=1 下 colTile 的【k-tile 步进】语义，实测 mismatch=254/256）。
    val r = runParamDebugCollect(p, expectPass = false)
    println(s"[$TAG] G2 结果：done=${r.done} err=${r.errSeen} 六通道地址对账不一致拍数=${r.addrBadCnt} " +
      s"C 数据 mismatch=${r.mismatch}（数据项仅观测，不硬断言）")
    withClue("G2（① 的牙齿，loadW=1 k-tile 支）：dmaA/dmaB 逐拍地址必须与金锚 " +
      "bKTileOff=bKpb×bEb=16 完全一致（回退旧常数 32 时此处必红于 dmaA beat#16，" +
      "实测 32≠16，见 build/124_causal_revert.log）：") {
      r.addrBadCnt should be(0)
    }
    withClue("G2：正常配置路径必须跑完（done）且不得出现 STATUS.err：") {
      r.done should be(true)
      r.errSeen should be(false)
    }
    if (r.mismatch != 0)
      println(s"[$TAG] ⚠ G2 观测到 C 数据 mismatch=${r.mismatch}/${p.m * p.n}：地址已全绿 ⇒ 与 ① 无关，" +
        "同 G1 的【INT8 lane 解包缺口】根因（loadW=1 转置排布下同样成立）。" +
        "如实呈报，不在 #124 范围内修。")
  }

  /** G3：量化 C 输出 ∧ N % cMxBlk != 0 —— 覆盖 ② 的 dmaCScale 行偏移 CEIL。
    *
    * 牙齿（手算，cFmt=MXE4M3、cMxBlk=16、N=24、totalN=16、colOff=16/16=1）：
    *   golden rowOff = ceil(24/16) = **2**；旧 HW FLOOR = 24/16 = **1**。
    *   expCScaleAddrSeq(nt,mm) = SC_BASE + nt×1 + mm×rowOff，mm ∈ [0,16)：
    *     mm=1 → golden SC_BASE+2、旧 HW SC_BASE+1 ⇒ delta=−1，首现 dmaCScale beat#1；
    *     mm=15 → golden SC_BASE+30、旧 HW SC_BASE+15 ⇒ delta=−15（逐 mm 递增，
    *     即「scale 行静默重叠」：旧 HW 的 mm=2 行落在 golden 的 mm=1 行尾条目上）。
    *   colOff 两侧同为 FLOOR(16/16)=1 ⇒ nt 维零差异，护栏【只对 rowOff 敏感】，纯净。
    *
    * ★ 为何 N=24（部分 n-tile）是【不可避免】的：cMxBlk 必整除 totalN（validate 强制），
    *   故 N % cMxBlk != 0 ⇒ N % totalN != 0 ⇒ 末 n-tile 必然是部分块。要覆盖 ② 就
    *   必须接受部分 n-tile，这是该盲区的内在属性，不是参数没选好。
    * ★ 断言口径（与 G1/G2 统一，见测试 9 头部【三条护栏的统一断言口径】）：本护栏
    *   【只对六通道地址对账与 done/err 下硬断言】，对 C 逐元素数据只【打印不硬断言】。
    *   因为部分 n-tile 的量化数据路（nEff=8 掩码 + 跨组块）在全仓【无任何活跃覆盖】
    *   ——曾覆盖它的 QuantSpec ②/⑤ 整块在 L58 起的 /* */ 内是死码。若此处对数据下
    *   硬断言，一旦部分块量化存在【与 ② 无关】的独立缺陷，本护栏会变成一条归因不明
    *   的红，反而污染 ② 的信号。
    * ★ 实测结果（2026-09-11，build/124_full_regression.log）：六通道地址对账 0 失配、
    *   done=true、err=false、C 数据 mismatch = 0 / 384（零失配）⇒ 部分 n-tile 的量化数据路实测
    *   【健康】，本护栏全绿。上面的弱化口径因此没有付出代价（未掩盖任何真实失配），
    *   但保留它是必要的：它使本护栏的红/绿只反映 ② 的地址正确性。
    * ★★ 牙齿已由【反向因果实验】证明（build/124_causal_revert2.log）：把 M16 分支的
    *   scaleCRowAddressOffset 改回 FLOOR(n/16) → 本用例 FAILED 于「dmaCScale beat#1
    *   addr mismatch: 5242881 was not equal to 5242882」（SC_BASE=0x500000，golden
    *   +ceil(24/16)=2、HW +1、delta=−1、首现 mm=1），与上方手算【逐字吻合】；恢复
    *   CEIL → 全绿。同一次实验里 G1/G2 不受影响（FP16 与 ② 无关），三条护栏互不串扰。
    * 配置合法性：aFmt/bFmt=FP16（非 MX）→ cFmt=MXE4M3 量化输出，据本文件头注释
    *   【约束一】明文合法（「非 MX 输入 → mxfp8 量化输出不违反它 —— C 侧量化消费
    *   的是 fp32 累加器，与输入格式无关」），且 matrix2/matrix4 是同族活跃绿用例；
    *   precheckConfigErr → None（cMxBlk=16 ≤ totalN=16 ⇒ 不触 errCode2）。
    * 避坑：kTiles=ceil(16/16)=1 ⇒ #122 四条件缺 kTiles>1；beatsPerRow=ceil(16×8/256)=1
    *   ⇒ 亦缺 beatsPerRow>1（双重不落入）。 */
  it should "guard #124-3: quantized C with N not divisible by cMxBlk exercises CEIL scaleC row offset" in {
    val p = DebugParams(
      m = 16, n = 24, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.MXE4M3,
      loadWMode = false, aMxBlkSel = SEL_BLK_M32, bMxBlkSel = SEL_BLK_M32, cMxBlkSel = SEL_BLK_M16,
      addrModeSel = SEL_ADDR_MAT, rnd = SEL_RND_RNE, seed = 124003L,
      strictAddr = true, maxCycles = 20000,
      waveTarget = "sim/debug/param_guard124_3_scalec_ceil.fst",
      testHint = "guard-124-3-scalec-ceil")
    val g = SectionEBanner.e1Geometry(p, cfg.totalN, cfg.cDmaExtW)
    val plan = new Plan(p, cfg)
    println(s"[$TAG] ═══ #124 ② 护栏 G3：量化 C 输出 N=24 % cMxBlk=16 != 0（原 Section E【E3】区间）═══")
    println(s"[$TAG] #122 四条件几何量：$g triggers=${g.triggers}（必须 false）")
    println(s"[$TAG] 金锚 scaleC 行偏移 rowOff = ceil(N/cMxBlk) = ceil(24/16) = " +
      s"${(p.n + p.cMxBlk - 1) / p.cMxBlk}（旧 HW FLOOR 会给 ${p.n / p.cMxBlk}，差 1 ⇒ 逐 mm 累积错位）")
    println(s"[$TAG] 金锚 expCScaleAddrSeq 前 4 项 = " +
      plan.expCScaleAddrSeq.take(4).map(a => f"0x$a%X").mkString(", "))
    withClue("G3 前提：必须真的落入 N % cMxBlk != 0（否则本护栏无牙齿、退化成 matrix2 的重复）：") {
      (p.n % p.cMxBlk) shouldNot be(0)
    }
    withClue("G3 前提：不得落入 #122（E1）区间：") { g.triggers should be(false) }
    withClue("G3 前提：必须不被 precheckConfigErr 判为 config-err（cMxBlk=16 ≤ totalN ⇒ 不触 errCode2）：") {
      precheckConfigErr(p, cfg.totalN, cfg.hasMx4) should be(None)
    }
    // expectPass=false：把 C 数据断言的决定权取回本用例（理由见上方 ★ 断言口径）
    val r = runParamDebugCollect(p, expectPass = false)
    println(s"[$TAG] G3 结果：done=${r.done} err=${r.errSeen} 六通道地址对账不一致拍数=${r.addrBadCnt} " +
      s"C 数据 mismatch=${r.mismatch}（数据项仅观测，不硬断言）")
    withClue("G3（② 的牙齿）：dmaCScale 逐拍地址必须与金锚 CEIL 行偏移完全一致：") {
      r.addrBadCnt should be(0)
    }
    withClue("G3：正常配置路径必须跑完（done）且不得出现 STATUS.err：") {
      r.done should be(true)
      r.errSeen should be(false)
    }
    if (r.mismatch != 0)
      println(s"[$TAG] ⚠ G3 观测到 C 数据 mismatch=${r.mismatch}：地址已全绿 ⇒ 与 ② 无关，" +
        "指向【部分 n-tile（nEff=8）量化数据路】这一独立盲区（全仓零活跃覆盖，" +
        "原覆盖者 QuantSpec ②/⑤ 是死码）。如实呈报，不在 #124 范围内修。")
  }


  // ==========================================================================
  // 共享运行器（复用 HiveDebugModel，与 HiveCoreDebugCase.runDebug 同构）
  // ==========================================================================
  private case class RunResult(mismatch: Int, addrBadCnt: Int, done: Boolean, errSeen: Boolean)

  private def runParamDebug(p: DebugParams): Unit = { runParamDebugCollect(p, expectPass = true); () }

  /** @param injectFailure true = 在 simulate 块内、C 对比完成后主动 fail()，
    *   用于验证「断言失败时 finally 中的 copyWave 仍会转存波形」这一已交付能力
    *   （自持有验证，不依赖任何硬件缺陷存续，见测试 6b）。 */
  private def runParamDebugCollect(p: DebugParams, expectPass: Boolean = true,
                                   injectFailure: Boolean = false): RunResult = {
    validate(p, cfg.totalN, cfg.hasMx4)
    val plan = new Plan(p, cfg)
    plan.prepare()

    // Section E 横幅检测（传实际 cDmaExtW，勿硬编码）
    val warnings = SectionEBanner.detect(p, cfg.totalN, cfg.cDmaExtW)
    SectionEBanner.printBanner(warnings, TAG)

    println(s"[$TAG] ================= HiveCoreParamDebugSpec =================")
    println(s"[$TAG] 生效参数: ${p.banner}")
    println(s"[$TAG] 几何: totalN=${cfg.totalN} aKTiles=${plan.aKTiles} bKTiles=${plan.bKTiles} " +
      s"nTiles=${plan.nTiles} beatsPerRow=${plan.beatsPerRow}")
    if (p.addrModeSel != 0)
      println(s"[$TAG] ⚠ addrMode=TILE：当前无硬件消费者，不改变实际 DMA 寻址行为，仅供寄存器级 debug")

    val cfgErr = precheckConfigErr(p, cfg.totalN, cfg.hasMx4)
    cfgErr.foreach(c => println(s"[$TAG] 预检：该组合硬件应直接 config-err（errCode=$c），走负向路径"))

    val maxCycles = if (isMxAny(p.cFmt)) math.max(p.maxCycles, 20000) else p.maxCycles
    var result = RunResult(0, 0, false, false)

    try {
      simulate(new HiveCore(cfg)) { dut =>
        val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
        val scaleWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
        val addrBad = scala.collection.mutable.ArrayBuffer[String]()
        var addrBadCnt = 0
        var aSent = 0; var bSent = 0; var saSent = 0; var sbSent = 0
        var cBeats = 0; var csBeats = 0

        def chkAddr(chan: String, idx: Int, actual: Long, exp: Seq[Long]): Unit = {
          if (idx < exp.size && actual != exp(idx)) {
            addrBadCnt += 1
            if (addrBad.size < 16)
              addrBad += f"$chan beat#$idx hw=0x$actual%X exp=0x${exp(idx)}%X delta=${actual - exp(idx)}%d"
            if (p.strictAddr) withClue(f"$chan beat#$idx addr mismatch: ") { actual should be(exp(idx)) }
          }
        }

        // Reset
        dut.reset.poke(true.B)
        dut.io.cmd.valid.poke(false.B)
        dut.io.resp.ready.poke(true.B)
        dut.io.dmaA.req.ready.poke(true.B)
        dut.io.dmaA.rsp.valid.poke(false.B)
        dut.io.dmaA.rsp.payload.data.poke(0.U)
        dut.io.dmaA.rsp.payload.err.poke(false.B)
        dut.io.dmaB.req.ready.poke(true.B)
        dut.io.dmaB.rsp.valid.poke(false.B)
        dut.io.dmaB.rsp.payload.data.poke(0.U)
        dut.io.dmaB.rsp.payload.err.poke(false.B)
        dut.io.dmaC.req.ready.poke(true.B)
        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaC.rsp.payload.err.poke(false.B)
        dut.io.dmaScaleA.get.req.ready.poke(true.B)
        dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
        dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
        dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
        dut.io.dmaScaleB.get.req.ready.poke(true.B)
        dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
        dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
        dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
        dut.io.dmaCScale.get.req.ready.poke(true.B)
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        dut.io.dmaCScale.get.rsp.payload.err.poke(false.B)
        dut.clock.step(3)
        dut.reset.poke(false.B)
        dut.clock.step(2)

        def sendCmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
          dut.io.cmd.valid.poke(true.B)
          dut.io.cmd.payload.op.poke(op)
          dut.io.cmd.payload.reg0Addr.poke(r0.U)
          dut.io.cmd.payload.reg1Addr.poke(r1.U)
          dut.io.cmd.payload.data0.poke(d0.U)
          dut.io.cmd.payload.data1.poke(d1.U)
          dut.clock.step()
          dut.io.cmd.valid.poke(false.B)
          dut.clock.step(2)
        }

        // 配置寄存器
        sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, p.m, p.n)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, p.k, A_BASE)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, p.controlWord6, p.controlWord7)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SA_BASE, SB_BASE)
        sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SC_BASE, 0L)
        println(f"[$TAG] regs 已写入: regs6=0x${p.controlWord6}%08X regs7=0x${p.controlWord7}%08X")

        // EXECUTE
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
        dut.io.cmd.payload.reg0Addr.poke(0.U)
        dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U)
        dut.io.cmd.payload.data1.poke(0.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)

        var done = false; var errSeen = false; var errCode = -1
        var cycle = 0; var pollSt = 0; var pollCool = 0

        while (!done && !errSeen && cycle < maxCycles) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean && aSent < plan.expAAddrSeq.size) {
            val addr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaA", aSent, addr, plan.expAAddrSeq)
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(plan.beatAtA(addr).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            aSent += 1
          } else { dut.io.dmaA.rsp.valid.poke(false.B); dut.io.dmaA.rsp.payload.data.poke(0.U) }

          if (dut.io.dmaB.req.valid.peek().litToBoolean && bSent < plan.expBAddrSeq.size) {
            val addr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaB", bSent, addr, plan.expBAddrSeq)
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(plan.beatAtB(addr).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            bSent += 1
          } else { dut.io.dmaB.rsp.valid.poke(false.B); dut.io.dmaB.rsp.payload.data.poke(0.U) }

          if (dut.io.dmaScaleA.get.req.valid.peek().litToBoolean && saSent < plan.expScaleAAddrSeq.size) {
            val addr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaScaleA", saSent, addr, plan.expScaleAAddrSeq)
            dut.io.dmaScaleA.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(plan.byteAt(addr).U)
            dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
            saSent += 1
          } else { dut.io.dmaScaleA.get.rsp.valid.poke(false.B); dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U) }

          if (dut.io.dmaScaleB.get.req.valid.peek().litToBoolean && sbSent < plan.expScaleBAddrSeq.size) {
            val addr = dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaScaleB", sbSent, addr, plan.expScaleBAddrSeq)
            dut.io.dmaScaleB.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(plan.byteAt(addr).U)
            dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
            sbSent += 1
          } else { dut.io.dmaScaleB.get.rsp.valid.poke(false.B); dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U) }

          dut.io.dmaC.req.ready.poke(true.B)
          if (dut.io.dmaC.req.valid.peek().litToBoolean) {
            val addr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaC", cBeats, addr, plan.expCAddrSeq)
            cWriteLog += ((addr, dut.io.dmaC.req.payload.data.peek().litValue))
            cBeats += 1
            dut.io.dmaC.rsp.valid.poke(true.B)
          } else { dut.io.dmaC.rsp.valid.poke(false.B) }

          dut.io.dmaCScale.get.req.ready.poke(true.B)
          if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) {
            val addr = dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong
            if (isMxAny(p.cFmt)) chkAddr("dmaCScale", csBeats, addr, plan.expCScaleAddrSeq)
            scaleWriteLog += ((addr, dut.io.dmaCScale.get.req.payload.data.peek().litValue))
            csBeats += 1
            dut.io.dmaCScale.get.rsp.valid.poke(true.B)
          } else { dut.io.dmaCScale.get.rsp.valid.poke(false.B) }

          val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
          pollSt = stNext; pollCool = coolNext
          statusOpt.foreach { st =>
            if (st.err) { errSeen = true; errCode = st.errCode
              println(f"[$TAG cycle=$cycle%5d] STATUS.err=1 errCode=${st.errCode}") }
            else if (st.done) { done = true
              println(f"[$TAG cycle=$cycle%5d] STATUS.done=1 A=$aSent B=$bSent C=$cBeats sA=$saSent sB=$sbSent sC=$csBeats") }
          }
          dut.clock.step(); cycle += 1
        }

        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        dut.io.cmd.valid.poke(false.B)

        // 报告
        println(s"[$TAG] cycles=$cycle/$maxCycles done=$done err=$errSeen(errCode=$errCode)")
        println(s"[$TAG] 地址对账不一致拍数=$addrBadCnt")
        addrBad.foreach(s => println(s"[$TAG]   ADDR-DIFF $s"))

        cfgErr match {
          case Some(expCode) =>
            withClue(s"期望 config-err errCode=$expCode：") { errSeen should be(true) }
            withClue(s"errCode 应为 $expCode：") { errCode should be(expCode) }
            result = RunResult(0, addrBadCnt, done, errSeen)
          case None =>
            if (!done) {
              plan.printRows(cWriteLog.toSeq, 4)
              result = RunResult(-1, addrBadCnt, false, errSeen)
              if (expectPass) fail(s"[$TAG] 超时：$maxCycles 拍内未观测到 STATUS.done")
            } else {
              if (expectPass) withClue("正常路径不应出现 STATUS.err：") { errSeen should be(false) }
              val cmp = plan.compare(cWriteLog.toSeq, scaleWriteLog.toSeq)
              println(f"[$TAG] C 对比: ${cmp.elems} 元素, mismatch=${cmp.mismatch}, maxRelErr=${cmp.maxRelErr}%.3e")
              cmp.details.foreach(s => println(s"[$TAG]   MISMATCH $s"))
              plan.printRows(cWriteLog.toSeq, 4)
              if (expectPass) {
                withClue("地址对账：") { addrBadCnt should be(0) }
                withClue("C 逐元素对比 golden：") { cmp.mismatch should be(0) }
                println(s"[$TAG] PASS: ${cmp.elems}/${cmp.elems} 元素全部匹配")
              } else {
                println(s"[$TAG] 非断言模式：mismatch=${cmp.mismatch}（预期失配，验证 dump 路径）")
              }
              result = RunResult(cmp.mismatch, addrBadCnt, done, errSeen)
              // 显式注入失败（测试 6b）：置于 result 赋值之后，确保波形已覆盖完整一次
              // 运行；异常上抛后由外层 finally 的 copyWave 转存 → 与真实断言失败同路径。
              if (injectFailure)
                fail(s"[$TAG] 【显式注入】auto-dump 路径验证：本异常由测试主动抛出" +
                  "（injectFailure=true），用于证明断言失败时 finally 中的 copyWave 仍会转存波形")
            }
        }
      }
    } finally {
      copyWave("HiveCoreParamDebugSpec", p.testHint, p.waveTarget)
    }
    result
  }
}
