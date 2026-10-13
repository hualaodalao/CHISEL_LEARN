/** HiveCoreQuickSpec — 「一个函数跑一组参数」的极简 HiveCore GEMM 调试用例。
  * 用法：只改下方【唯一一处 ★★★ runCase(...) 调用】，然后重跑裸命令（不带任何 -D）：
  *   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreQuickSpec"
  * CPATH/LIBRARY_PATH 是硬需求（Verilator 5.050 FST writer 依赖 lz4 头）。波形始终开启（复用
  * HiveCoreParamDebugSpec.scala 的 HiveAlwaysFstSim trait）；本文件不注册任何 -D 键，避开 ChiselSim
  * HasCliOptions 白名单坑。只做薄封装：内存镜像 / 六通道期望地址 / golden / compare 全复用 HiveDebugModel，
  * STATUS 轮询复用 HiveStatusPollApi，供数范式与 HiveCoreDebugCase.runDebug 同构（那两个 runner 都是
  * private、无法跨文件复用，故按同一范式在此收敛成单个函数）。
  *
  * ══ 三条架构约束（自己调 cross 配置必撞）══
  * 【一】v1 合法【输入配对】只有两类：双 MX（MXE4M3/MXE5M2 互组）、双非 MX 浮点（FP16/BF16 互组）。A=MX×B=非MX
  *   数学不可实现；A=非MX×B=MX 需 executor 偶奇重排、v1 禁止留 v2；两类非法组合硬件用 errCode3 拦。★ 只管输入
  *   侧、【不限制输出 cFmt】——「非 MX 输入 → mxfp8 量化输出」合法（C 侧量化消费 fp32 累加器）。
  * 【二】MX 输入强制 loadWMode=1（否则 errCode4）。故 loadW 默认 None = 按 isMx(aFmt)||isMx(bFmt) 自动推导。
  * 【三】addrMode 当前【全 RTL 零消费者】：只写 reg5[14:12] + 回读对账、不参与 golden；内存镜像与六路期望地址
  *   序列一律按 MAT 语义生成，传 TILE 不改变任何数值结果（运行时会打印这句提示，别在波形里白找）。
  *   (#164) 寄存器重排后 addrMode 的宿主由旧 reg6 迁到 reg5（位号 14/13/12 不变）。
  *
  * ══ errCode 实测更正（与常见 errCode 表不符，以 RTL 为准）══
  * HiveCoreCalcConfig 把 a/b/cMxBlockSize 三违约【合并】为单一 errMXLargerThanKLoadNum、errCode 恒置 0，故【errCode
  * 1 与 2 永不可达】（QuantSpec「blk32 over totalN」旧期望 2 实得 0 的红项根因，#124；已按 RTL 口径把该用例期望
  * 改为 0）；且五个 when 平级 last-connect、后写覆盖前写 → 真实优先级 6>5>4>3>0。
  * 【P0-2 去双源】金锚 errCode 镜像已收敛为单一真源 HiveDebugModel.hwErrCode（顺序 if 后写覆盖，逐位镜像 RTL）；
  * QuickSpec.hwErrCode 与 HiveDebugModel.precheckConfigErr 均委托它，不再有"两份互不一致的软件镜像"。
  * [Fix-7/P0-1, exists only when cfg.hasMx4=true] fp4 support appended two
  * hasMx4-gated branches to the errComb block, both AFTER the fp8 branches, so
  * last-connect gives them higher priority: MX4xMX8 mix -> errCode3 (Fix-4,
  * code 3 reused), and fp4 K not divisible by kPerBeat(=64) -> errCode8 (P0-1).
  * Source order (front to back): code7(capacity) -> code10(cAddr) -> code8(K) -> mix code3,
  * hence with hasMx4=true the full truth order is
  *   3(MX4xMX8 mix) > 8(K not divisible) > 10(cAddr alignment) > 7(capacity, RTL-only)
  *     > 5 > 4 > 3(isAMx^isBMx) > 0
  * (#178 UPDATE) errCode 11 (int/non-int cross-boundary) inserted after code-8, before
  * Fix-4. Source order: ...code8(K) -> code11(cross) -> mix code3. Full truth order:
  *   3(Fix-4) > 11(cross-boundary) > 8(K) > 10(cAddr) > 7(capacity) > 5 > 4 > 3(xor) > 0
  * (the two code3 predicates are mutually exclusive and share one code).
  * Note: code 6 (MXE2M1 output N odd) is commented out in RTL but still mirrored
  * by the golden model -- known divergence (see #169 report).
  * (#146) A third branch used to sit last -- errCode7 "fp4 blk != 64". It has
  * been DELETED: MXBlockSize was narrowed to the 2-level enum {sel=0 -> 16,
  * sel=1 -> 32}, so `getBlockSize() =/= 64.U` was a TAUTOLOGY and every fp4 run
  * was rejected at runtime unconditionally and silently (the golden model
  * mirrored the same tautology, so RTL and anchor agreed and nothing ever went
  * red). fp4 now accepts blk in {16, 32} exactly like fp8, errCode 8 is the only
  * remaining fp4 geometry guard. CODE 7 IS NOT FREE: the CAPACITY guard
  * (m > cBufferDepth - totalN) still drives errCodeComb := 7.U and is LIVE in all
  * three emitted netlists. New checks are allocated from 12 upwards (code 9 is
  * reserved for v2 W4A8, code 11 is LIVE: int/non-int cross-boundary #177/#178);
  * codes 1/2 are never revived.
  * (#150→#155) QuickSpec 的 cfg 现在【不再显式传 supportedFmts】，回落 HiveCoreConfig
  * 默认集——因为 #155 MJ1 已把 MXE2M1 升格为【默认必选】，默认集本身就是 7 种格式。
  * (#66 订正：上行「7 种格式」已过时) 默认集现为 5 种 {INT8, INT4, MXE4M3, MXE5M2,
  * MXE2M1}——FP16/BF16/INT16 已被用户裁定砍除，故【hasFp=false】。本 suite 不受影响、
  * 实测仍绿：现役唯一用例是 MXE2M1×MXE2M1【输入】→ BF16【输出】，而 16-bit 输出走
  * HiveOutCvt/FpCvt 的 40→32→16 两级舍入链，【不依赖 hasFp】。但下方那段
  * 「为什么必须保持 hasFp/hasInt 为真」的论证前提已不成立，读时请连同其后的 (#66) 订正一起看。
  * （#150 当时是显式写「默认 6 种 + MXE2M1」，那时默认集还不含 MXE2M1；见下方 cfg 的
  * 论证注释与全字段对齐表。）⟹ hasMx4=true 保持不变，这两支【真的会 elaborate】，
  * 完整优先级 3(mix) > 8 > 10 > 7(capacity, RTL-only) > 5 > 4 > 3(xor) > 0 对本 suite 生效。#150 之前 QuickSpec
  * (#178 UPDATE) 完整优先级现为 3(Fix-4) > 11(cross) > 8 > 10 > 7 > 5 > 4 > 3(xor) > 0。
  * 用的默认 cfg 不含 MXE2M1（hasMx4=false），那两支恒不 elaborate、优先级退回 6>5>4>3>0；
  * 实测（build/150_fp4_firstrun.log）打开后两支仍不【触发】——因为 fp4 主用例
  * a/b 同为 MXE2M1（不混配）且 K=64 被 64 整除，体检结论仍是「配置合法」走正向比对。
  * 注：MX4×MX8 混配(W4A8)数学/OCP 合法，真数据通路支持列 v2；errCode3 是【运行期可诊断】守护（非永久拒绝，
  * 将来支持只需删守护 when + 补 W4A8 数据通路，aFmt/bFmt 已是独立 4-bit 字段、软件接口无需重构）。
  *
  * ══ 历史缺陷留痕（#133 已修复，本段为知识保留，原「已知不可用区间」段已删）══
  * dmaC 写回地址曾在多 tile 边界出错（sNEXT_N 三角累积增量式）：nt≥1 块首址实得
  * base+(M-1)*rowOff+colOff（FP32/M=32/N=32 下 0x200FC0）、正确值 base+colOff（0x200040）。
  * 当时金锚 cAddrSeqDense 是同一缺陷 RTL 的闭式反向拟合，逐拍地址硬断言退化为「RTL 与镜像
  * 自比」恒真假绿、全部既有套件放行。★ 正确触发条件【nTiles≥2 且 m≥2】，与 mTiles 无关——
  * 本段原文「凡 mTiles>1 或 nTiles>1」是错误表述：C 地址序列不含 mTiles 参数、WrOnly 也无
  * mTile 循环（反例：M=32/N=16 → mTiles=2 但 nTiles=1，修复前后都绿）。
  * 【#136-KS1 补正：m≥2 是必要条件，不可省】m==1 ∧ nTiles==2 是三角化回退的【可证明
  * 盲区】——三角式一阶项 nt×(m−1)×rowOff 在 m==1 恒为 0，二阶项系数 nt×(nt+1)/2 在
  * nt=1 恰为 1（与绝对式 nt 的系数在 nt=1 也恰为 1，两者只在 nt≥2 才分歧：3 vs 2），
  * 故 nTiles==2 时两式产出逐拍完全相同的序列，任何不变式都区分不了。金锚
  * cAddrSeqDense 的两条 require 因此覆盖不到 -Dm=1 -Dn=32（合法配置）。
  * 现状：RTL 侧 sNEXT_N 已改「基址+轮偏移」线性模型（curAddr := io.regFile.cAddr + colAddr，
  * 与 HiveCoreDmaScaleWr 同构）；金锚侧 cAddrSeqDense 已改张量语义绝对式
  * base + nt*cColOff + row*cRowOff + b*cBeatAddrStep，并内置两条 require 自证（nTiles≥2/≥3
  * 块首拍不变式，【金锚构造期】即炸——即 Scala 测试体求值、调用该函数之时，早于 simulate，
  * 既非 scalac 编译期亦非 Chisel elaboration 期；任何三角化回退都会被拦截）。永久护栏用例见
  * HiveCoreDebugCase 组(f)：M32 N48 K16 FP16→FP32（nTiles=3、strictAddr 硬编码 true）。
  * 产物：波形 sim/debug/quick.fst；失败/超时另落 sim/debug/quick_fail.txt|_A.hex|_B.hex（sim/ 已 gitignore）。 */
import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._
import scala.collection.mutable

class HiveCoreQuickSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveAlwaysFstSim with HiveStatusPollApi {
  import HiveDebugModel._
  private val TAG = "QUICK"; private val SUITE = "HiveCoreQuickSpec"

  // ══════════════════════════════════════════════════════════════════════════
  // ★★★ 你只需要改这一处调用，改完重跑：
  //   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreQuickSpec"
  // loadW/seed/strictAddr/maxCycles/wave 都有默认值，不写就是默认（见 runCase 签名）。
  // 默认这组 M=N=K=32 FP16→FP16 的 C 数据是 1024/1024 逐位匹配的。【#131 收尾裁定（选项 A）保持本默认
  // 调用不动】：它曾在金锚未对齐期于 dmaC 地址对账上报假红（nTiles=2 触发三角累积缺陷），#133 金锚对齐后
  // 恢复全绿——它是仓库里唯一每天可见的该类缺陷信号灯，勿改成 16（单 tile 会失去信号价值）。
  //
  // 【#136-KS7 几何 vs N 依赖性速查（改上方任一参数前先读）】三条通道的地址几何【全部】
  // 随 N 变化，不存在「B 权重与 N 无关」这类捷径：
  //   · B 通道：行步进 bRowAddressOffset 在 loadWMode=0（垂直加载）分支 = regFile.n×bEb
  //     （FP16 → n×2），【正比于 N】；列步进 bColTileAddressOffset = totalN×elemsPerLane(bFmt)×bEb
  //     与 N 无关（★#124 ① 起为运行期量；FP16 → totalN×1×2 = 32，与旧式 totalN×(bW/8) 同值），
  //     但轮数 nTilesB = ceil(N/totalN) 直接由 N 决定 ⇒ B 总拍数 = nTilesB × bKTiles × totalN
  //     随 N 变。loadWMode=1 分支的 bRowAddressOffset 改按 K 派生（见 HiveCoreCalcConfig），
  //     此时「正比于 N」不成立、但 nTilesB 仍随 N 变，故结论不变。
  //   · A 通道：aRowAddressOffset = regFile.k×aEb 与 N 无关（★同为运行期量，FP16 → k×2），但总拍数 =
  //     nTiles × aKTiles × M，其中 nTiles = ceil(N/totalN) ⇒ 仍随 N 变。
  //   · C 通道：cRowAddressOffset = N×cEb【正比于 N】，nTiles 亦随 N 变。
  //   ⇒ 单独改 n 会同时改动 A/B/C 三条序列与三组拍数，不能当单变量对照用；要做单变量
  //     对照请参考 HiveCoreDebugCase 的 guard122/guard133 组（各自明示了变量与非变量）。
  // 【#136 实测记录（前提矛盾项，供后续核对）】#134 评审 KS7 指认本文件此处原文含
  //   「B 权重与 N 无关」一句，#136 实施时全仓 grep（含 "与 N 无关"/"与N无关"/"N 无关"）
  //   【0 命中】，且本文件未被 git 跟踪（不在 HEAD 中）故无法查历史版本。据此判断该句
  //   要么已在 #131/#133 期间被改写掉、要么评审引用有误。上方速查表按裁定的【意图】
  //   （给出正确的 N 依赖性表述）落地，不针对任何现存错误原文。
  // ══════════════════════════════════════════════════════════════════════════
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.MXE2M1,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT)

  // ══════════════════════════════════════════════════════════════════════════
  // 【P1-2 W4A8 订正（2026-10-13）】A=MX8 × B=MX4 正向混配五用例。B DMA 走 stretch：
  //   逻辑拍 = 16B fp4 半字片（一个阵列 K 窗口的一行）→ 32B 对齐单读 → DMA 内半字
  //   选择 → LUT 精确加宽成 E4M3 阵列拍；scaleBDma 全量 kTile 窗口、每拍一个相邻
  //   scale 字（blk16 时含源块 {2w,2w+1} 字节对）。金锚按张量语义独立推导（窗口
  //   口径序列见 HiveDebugModel expBAddrSeq/expScaleBAddrSeq 混配分支）；硬件守护见
  //   HiveCoreCalcConfig（混配 K%64 或 bAddr%32 → errCode 8，反向混配 → 3）。
  //   命名带 caseTag ⟹ ScalaTest 名互不撞车（DuplicateTestNameException 防护）。
  // ══════════════════════════════════════════════════════════════════════════
  // ① 正向混配 K=64：aBlk=32（=A 侧 kPerBeat，1 scale/kTile）、bBlk=16
  //   （窗口 w 的 scale 字含源块 {2w,2w+1} 字节对 ⟹ Executor idx=lane/8 双字节
  //   切片可观测；K=64 ⟹ kTile=2 偶数、末窗读恰落张量界内）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_blk16.fst", caseTag = " [P1-2 W4A8 E4M3xE2M1 blk32/16]")

  // ② 正向混配 K=128：多 kTile 轮（kTile=4，覆盖 k/n 双切换）+ bBlk=32
  //   （每窗口 1 个 scale 字节，idx 恒 0，检验单字节路径）。
  runCase(m = 32, n = 32, k = 128, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 32, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_blk32.fst", caseTag = " [P1-2 W4A8 E5M2xE2M1 K128 blk32/32]")

  // ③ 负向：反向混配 A=MX4 × B=MX8 → errCode 3（数据通路未支持，Fix-4 收窄分支）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE2M1, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_rev_err3.fst", caseTag = " [P1-2 W4A8 neg reverse-mix errCode3]")

  // ④ 负向：正向混配 K=32（窗口粒度 32 过 A 侧 MX8 检查，但 K%64 守护仍拒——
  //   kTile=1 奇数会使末窗 32B 单读越出张量界 16B）→ errCode 8。
  runCase(m = 32, n = 32, k = 32, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_k32_err8.fst", caseTag = " [P1-2 W4A8 neg K32 errCode8]")

  // ⑤ 负向：正向混配 bAddr 未 32B 对齐（stretch 半字拍片单读契约被破坏——
  //   奇半字拍的 32B 单读将越出张量界）→ errCode 8（订正新增守护）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, bBase = B_BASE + 16,
          wave = "sim/debug/quick_w4a8_baddr_err8.fst", caseTag = " [P1-2 W4A8 neg bAddr misalign errCode8]")

  /** 编译期配置（totalN=16；cDmaExtW 默认 256；缓冲深度 2048；scaleBufferDepth=64）。
    * (#155 MJ1) supportedFmts 现在【不再显式传入】，回落 HiveCoreConfig 的默认集。
    *   MXE2M1 现在是默认必选，不再需要显式加。
    *   历史：#150 曾把它显式写成「默认 6 种 + MXE2M1」，因为当时默认集不含 MXE2M1、
    *   hasMx4=false，fp4 硬件（quad-lane unpack、4x sigW=2 HiveMacFpMx、HiveCvtOpMx 的
    *   isMx4 臂——HiveMacFpMx/HiveCvtOpMx 均为原 HiveWorkUnit.scala 子模块、已随 FusionMac
    *   重构删除；bits-based colTileAddressOffset、errCode8、mix errCode3、kTiles=K/64）
    *   在本 DUT 里【根本不存在】，用户手改的 fp4 主用例会被 HiveDebugModel 的 Fix-9
    *   cfg-aware require 拦下。本轮 MXE2M1 升格为默认必选后，那段显式传参与默认集
    *   【逐元素相同】，继续显式写等于维护一份会漂移的副本，故删除。
    * 【全字段逐一对齐（改前 vs 改后，共 12 个字段）】
    *   arrayN 8=8 / clusterM 2=2 / aW 16=16 / bW 16=16 / cW 40=40 /
    *   supportedFmts {FP16,BF16,INT16,INT8,MXE4M3,MXE5M2,MXE2M1} == 默认集（逐元素相同）/
    *   (#178 订正：上行是 #155 时的历史快照。当前默认集已收窄为 5 种
    *   {INT8,INT4,MXE4M3,MXE5M2,MXE2M1}——FP16/BF16/INT16 已砍除，hasFp=false。见 :48 订正。)/
    *   aBufferDepth 2048=2048 / bBufferDepth 2048=2048 / cBufferDepth 2048=2048 /
    *   addrWidth 32=32（两侧都走默认）/ scaleBufferDepth 64=64（★未动，cfgMxFp4 才用 256）/
    *   cDmaExtW 256=256（两侧都走默认）。
    *   ⟹ 改前 cfg == 改后 cfg（case class 结构相等），派生量随之逐一相同：
    *   totalN=16、hasFp=true、hasInt=true、hasMx=true、hasMx8=true、hasMx4=true、
    *   c1Merge=hasFp&&hasMx=true、cEffW=40、cScaleRowW=8。DUT 逐位不变。
    * 【为什么必须保持 hasFp/hasInt 为真（沿用 #150 的论证，仍然有效）】
    *   - hasFp 掉假会删掉整条浮点通路，HiveWorkUnit 的 baseSel 退化为 (false,false) => io.cReg，
    *     FP16 主用例会【静默】输出累加器输入而非乘加结果（不报错）。上方类头那句
    *     「默认 M=N=K=32 FP16->FP16 逐位匹配 1024/1024」是用户每天要用的旋钮。
    *   - c1Merge = hasFp && hasMx 掉假会让共享的 macShared:HiveMacFp（原 HiveWorkUnit.scala
    *     子模块，已随 FusionMac 重构删除）消失，fp8 dual-lane
    *     MAC 结构随之改变，「对 fp8 additive / 数值恒等」的说法即告失效。
    *   故本 cfg 绝不改用 HiveCoreElaborate.cfgMxFp4 那种 MX-only 三格式集。
    * 【(#66) 订正：上方这段论证的【前提已被现实推翻】，原文按仓库惯例保留不删，但结论不再适用】
    *   用户已裁定砍除 FP16/BF16，HiveCoreConfig.supportedFmts 默认集现为 5 种
    *   {INT8, INT4, MXE4M3, MXE5M2, MXE2M1} ⟹ 本 cfg（不显式传 supportedFmts）
    *   【现在就运行在 hasFp=false 下】，c1Merge = hasFp && hasMx 亦已为 false。
    *   即上方「hasFp 掉假会…」所预言的后果不是假想，而是已实测发生：
    *   HiveCoreSimCase（9 条 FP16 输入用例）与 HiveCoreSimCaseLoadWSpec（1 条）
    *   全部静默输出 C 全零而变红（hw=0.000000 / maxRelErr=1.00e+00 /
    *   strict anchor 0 was not equal to 31743，STATUS.err 不置位），这两个 suite 的
    *   FP16 用例已在 #66 整批移除（详见各自文件头的「#66 移除记录」）。
    *   本 suite 之所以【不受影响、仍绿】：现役唯一用例（见上方 runCase）早已不是
    *   FP16 输入，而是 MXE2M1×MXE2M1 → BF16【输出】；16-bit 输出走 HiveOutCvt/FpCvt
    *   的 40→32→16 两级舍入链，不依赖 hasFp。故上方「FP16 主用例是用户每天要用的
    *   旋钮」「绝不改用 MX-only 三格式集」两句均已作废：默认集本身已不含 FP16/BF16，
    *   QuickSpec 也不再有任何 FP16 输入用例可被 hasFp 掉假所伤。
    * 【两条 require】require(!hasMx4 || hasMx8) 与本轮新增的 require(!hasMx8 || hasMx4)
    *   合起来钉死 hasMx4 <=> hasMx8；本 cfg 两者皆真 ⟹ 都满足。 */
  private val cfg = HiveCoreConfig(arrayN = 8, clusterM = 2, aW = 16, bW = 16, cW = 40,
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048, scaleBufferDepth = 64)
  // 套件级舍入模式：★ 主用例与两条历史永久用例都不传 rnd ⟹ 全部取这里的 RNE（逐字不变）。
  //   (#155 m3) 自本轮起它同时是 runCase 的 per-case `rnd` 形参的【默认值来源】；要单独换
  //   某一条用例的舍入模式，请在那条 runCase 调用里传 `rnd = RoundingMode.RDN/RUP/...`，
  //   不要再改这一行（改这一行会同时改动所有未显式传 rnd 的用例）。
  // ★★【#155 实测踩坑，务必保留 def 不要改回 val】本文件里 ★ 主用例的 runCase 调用位于
  //   第 109 行，而本声明位于其后（Scala 类体按书写顺序求值）。默认参数表达式是【在调用点
  //   即时求值】的（编译器生成 runCase$default$N，在 :109 那一行就调用它），而 runCase 的
  //   函数体是延迟到 `it should ... in {}` 执行时才跑。若这里写 `private val rnd`，则 :109
  //   求值默认参数时该字段【尚未初始化 = null】，实测抛：
  //     java.lang.NullPointerException: Cannot invoke
  //       "chisel3.ChiselEnum$Type.litValue()" because "rnd$1" is null
  //     at HiveCoreQuickSpec.$anonfun$runCase$2(HiveCoreQuickSpec.scala:194)
  //   且只有 ★ 主用例（写在声明之前）会中招，#146/#151/#155 四条（写在声明之后）全绿 ⟹
  //   这是一个极易被误判为「主用例坏了」的前向引用初始化序陷阱。改成 def 后没有存储字段、
  //   不受类体求值顺序影响，调用即返回枚举成员（RoundingMode.RNE 是 object 成员，与本类
  //   构造顺序无关），语义与「套件级常量」完全一致。
  private def rnd: RoundingMode.Type = RoundingMode.RNE
  /** blk(用户面 Int) → MXBlockSize sel：表逐条由枚举成员构造，blk 取定义式 (16 << sel)，不写 sel 字面量（枚举重排也不错位）。
    * 【#144】MXBlockSize 已收窄为 {M16=0, M32=1}，故基档由 8 改 16：16<<0=16→sel0、16<<1=32→sel1，
    *   与 HiveDebugModel.DebugParams.aMxBlk/bMxBlk/cMxBlk 的 16<<sel 反解码逐位对齐。 */
  private val blkToSel: Map[Int, Int] = Seq(MXBlockSize.M16, MXBlockSize.M32)
    .map(e => (16 << e.litValue.toInt) -> e.litValue.toInt).toMap
  private def selOf(w: String, blk: Int): Int = blkToSel.getOrElse(blk, throw new IllegalArgumentException(
    s"[$TAG] $w=$blk 非法：MX 块大小只能取 ${blkToSel.keys.toSeq.sorted.mkString(" / ")}"))

  /** 唯一入口：注册一个 ScalaTest 用例。改顶部调用的参数、重跑即可。
    * (#155 m3) 新增 per-case `rnd` 形参，默认取套件级 `private def rnd`（RNE）。
    *   ★测试名【不含 rnd】（`it should` 那行只由 m/n/k/三格式/三 blk/addrMode/caseTag 组成），
    *   故加这个形参后既有三条用例的 ScalaTest 名【逐字不变】：不触碰 52 行归一化基线、
    *   不触碰用户手改的 ★ 主用例行、也不会因改名而让 copyWave 的 testHint/工作目录漂移。
    *   默认参数表达式 `= rnd` 求值于外层类作用域（Scala 把默认值编译成
    *   `runCase$default$N` 方法，形参名在其中不可见）⟹ 它引用的是套件级成员而非形参自身。
    *   ★但该默认值是在【调用点即时求值】的（不像函数体那样延迟到 `in {}` 执行时），而本文件
    *   的 ★ 主用例写在套件级声明之前 ⟹ 套件级 `rnd` 必须是 `def` 而不能是 `val`，否则
    *   主用例会拿到未初始化的 null 字段。详见下方 `private def rnd` 处的踩坑记录。 */
  def runCase(m: Int, n: Int, k: Int, aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
              aBlk: Int, bBlk: Int, cBlk: Int, addrMode: AddrMode.Type,
              loadW: Option[Int] = None,   // None = 按 isMx(aFmt)||isMx(bFmt) 自动推导（MX 强制 1，约束二）
              seed: Long = 1234L,          // 激励随机种子（固定可复现）
              strictAddr: Boolean = true,  // true = 地址对账失配判失败（仍是收集-后断言，一次看全所有失配点）
              maxCycles: Long = 20000L,    // 仿真拍数上限（量化输出内部再抬到 >= 20000）
              wave: String = "sim/debug/quick.fst",
              // (#155 m3) per-case 舍入模式。默认 = 套件级 RNE（下方 `private def rnd`）。
              //   进 DebugParams.rnd（litValue.toInt）⟹ 写入 reg5 bits[18:16]（(#164) 前为 regs(7)
              //   bits[14:12]），RTL 与金锚同源。
              rnd: RoundingMode.Type = rnd,
              // 【P1-2 W4A8】per-case B 张量基址（进 reg3=bAddr）。默认 = HiveDebugModel.B_BASE；
              //   仅负例 ⑤ 用它造 bAddr%32!=0 的失配。★测试名【不含 bBase】⟹ 既有用例名逐字不变
              //   （B_BASE 是 object HiveDebugModel 的 val，无 #155 那种类体前向引用初始化序陷阱）。
              bBase: Long = B_BASE,
              // (#148) caseTag is appended to the ScalaTest test NAME. It exists for exactly one
              //   reason: two runCase calls with identical (m,n,k,aFmt,bFmt,cFmt,blks,addrMode)
              //   build the SAME test name, and ScalaTest rejects that while the suite is being
              //   CONSTRUCTED -- org.scalatest.Engine.registerTest ends with
              //     if (atomic.get.testsMap.keySet.contains(testName))
              //       throw new DuplicateTestNameException(testName, ...)
              //   (scalatest 3.2.19, Engine.scala:669-674). testName = getTestName(testText,
              //   currentBranch) depends ONLY on the `it should "<text>"` string, so the collision
              //   is decided before any require/simulate/compare ever runs. sbt prints it as
              //     HiveCoreQuickSpec *** ABORTED ***
              //       Duplicate test name: should run ... (blk 16/16/16, ...) (HiveCoreQuickSpec.scala:<the `it should` line>)
              //     Total number of tests run: 0 / Suites: completed 0, aborted 1
              //   i.e. ABORTED, not FAILED: an exception, not an assertion, and not a golden-model
              //   disagreement. The same collision also lands on the svsim working directory, which
              //   is derived from the test name (build/chiselsim/HiveCoreQuickSpec/should-run-M-32-
              //   N-32-K-64-DataFormat-4-MXE5M2--x-...-blk-16-16-16--addrMode-AddrMode-0-MAT--), so
              //   making the name unique repairs both layers with one change.
              //   Default "" keeps the ★ primary call above at its exact historical title.
              caseTag: String = ""): Unit = {
    val lw = loadW.getOrElse(if (isMxAny(aFmt) || isMxAny(bFmt)) 1 else 0)
    it should s"run M=$m N=$n K=$k $aFmt x $bFmt -> $cFmt (blk $aBlk/$bBlk/$cBlk, addrMode=$addrMode)$caseTag" in {
      runQuick(DebugParams(m = m, n = n, k = k, aFmt = aFmt, bFmt = bFmt, cFmt = cFmt, loadWMode = lw != 0,
        aMxBlkSel = selOf("aBlk", aBlk), bMxBlkSel = selOf("bBlk", bBlk), cMxBlkSel = selOf("cBlk", cBlk),
        addrModeSel = addrMode.litValue.toInt, rnd = rnd.litValue.toInt, seed = seed, strictAddr = strictAddr,
        maxCycles = maxCycles.toInt, waveTarget = wave, testHint = s"run-M-$m-N-$n-K-$k", bBase = bBase)) }
  }


  // ══ ① 运行前配置体检：镜像 HiveCoreCalcConfig 的 errComb/errCodeComb 裸块（RTL 真实口径）══
  private def kLoadNum(f: DataFormat.Type): Int = if (isMx8(f)) cfg.totalN * 2 else cfg.totalN * 4  // 镜像 getKLoadNum
  /** Some(code) = 硬件会在 EXECUTE 后直接置 STATUS.err、不进计算。
    * 【P0-2 去双源】原本此处有一份 QuickSpec 私有的 hwErrCode 镜像，与 HiveDebugModel.precheckConfigErr
    * 互不一致（后者分列 Some(0)/Some(1)/Some(2)、缺 errCode6、优先级 else-if 链首最高）。现已把权威
    * 镜像提升为 HiveDebugModel.hwErrCode（单一真源，顺序 if 后写覆盖，逐位镜像 RTL last-connect 序），
    * 本 def 与 precheckConfigErr 均委托它 ⟹ 两份镜像合一、永不再分叉。
    * Truth order: 3(MX4xMX8 mix) > 11(int/non-int cross) > 8(K not divisible)
    *   > 10(cAddr alignment) > 7(capacity, RTL-only) > 5 > 4 > 3(isAMx^isBMx) > 0.
    *   (#146) The former leading `7(fp4 blk!=64)` term is
    *   GONE -- errCode 7 was deleted in hardware and in HiveDebugModel.hwErrCode
    *   (it had degenerated into a tautology once MXBlockSize became a 2-level
    *   {16,32} enum). Code 7 is NOT free: the CAPACITY guard (m > cBufferDepth
    *   - totalN) still drives errCodeComb := 7.U and is LIVE in all three netlists;
    *   it is explained below via case 7.
    *   (#169/#170) errCode 10 added: cAddr not dmaBytes-aligned.
    * (#150→#155) cfg 不再显式传 supportedFmts，但默认集已含 MXE2M1 ⟹ cfg.hasMx4=true
    *   不变，混配3/8 两支【已 elaborate】（#150 之前的默认 cfg 下 hasMx4=false、
    *   两支恒不 elaborate）；
    *   但对本 suite 的【五个】用例仍全部【不触发】（#155 m3 起由 3 条增至 5 条）：
    *     · fp4 主用例（K=64）与 #155 RDN / #155 RUP 两条（同为 K=64）：a/b 同为 MXE2M1
    *       ⟹ 不混配（errCode3 假），K=64 被 kLoadNum=64 整除 ⟹ errCode8 假；
    *     · #151 K128 fp4：a/b 同为 MXE2M1，K=128 % 64 = 0 ⟹ 两支同假；
    *     · #146 fp8 永久用例：a/b 同为 MXE5M2 ⟹ 不混配，且 hasMx4 门下的 fp4 专属
    *       errCode8 对 fp8 不适用。
    *   舍入模式（rnd）不是 errCode 0/3/4/5/6/8 中任何一支的输入 ⟹ RDN/RUP 两条不会
    *   把体检结论推向负向。实测五条体检结论均为「配置合法」→ 走正向 golden 比对。 */
  private def hwErrCode(p: DebugParams, cAddr: Long = C_BASE): Option[Int] =
    HiveDebugModel.hwErrCode(p, cfg.totalN, cfg.hasMx4, cAddr, cfg.cDmaExtW)

  /** 体检命中时的人类可读原因 + 建议值（取值全部由 cfg/枚举推导，不写死） */
  private def errExplain(p: DebugParams, c: Int): String = c match {
    case 0 => s"MX 块大小超过硬件单拍可装载的 K 元素数 —— aBlk=${p.aMxBlk}（上限 kLoadNum(aFmt=${p.aFmt})=${kLoadNum(p.aFmt)}）、" +
      s"bBlk=${p.bMxBlk}（上限 ${kLoadNum(p.bFmt)}）、cBlk=${p.cMxBlk}（上限 cNWriteNum=totalN=${cfg.totalN}）。 建议：输入侧 aBlk/bBlk " +
      s"取 ${kLoadNum(p.aFmt)}（= kPerBeat，一个 kTile 恰好一个 scale 块）；输出侧 cBlk 只能取 <= totalN 且整除 totalN 的值 → " +
      s"${blkToSel.keys.toSeq.sorted.filter(b => b <= cfg.totalN && cfg.totalN % b == 0).mkString(" 或 ")}（totalN=${cfg.totalN} 下 cBlk=32/64 必被拒）"
    case 3 => s"输入侧 MX 与非 MX 混配（isAMx XOR isBMx）、或【反向】MX 混配 A=MX4×B=MX8 —— isMx(aFmt=${p.aFmt})=${isMxAny(p.aFmt)} XOR isMx(bFmt=${p.bFmt})=${isMxAny(p.bFmt)}；" +
      s"isMx4(aFmt)=${p.aFmt == DataFormat.MXE2M1} AND NOT isMx4(bFmt)=${p.bFmt == DataFormat.MXE2M1}（Fix-4 收窄后只拒反向混配）。 " +
      "A=MX×B=非MX 数学不可实现（一侧有 scale、另一侧无）；反向混配 A=MX4×B=MX8 数据通路未支持" +
      "（quad/dual lane 选择子只看 aFmt，b 侧解码接 bFmt 会静默算错，故设 errCode 运行期可诊断守护，非永久拒绝）。 " +
      "【P1-2 W4A8】正向混配 A=MX8×B=MX4 已合法（B DMA stretch：16B 半字拍片 + LUT 加宽 E4M3；K/bAddr 守护见 errCode 8），不再触发本码。 " +
      "建议：aFmt/bFmt 改成同族（E4M3/E5M2 互组，或 fp4 E2M1×E2M1）、正向混配、或双非 MX 浮点；本约束不限制输出 cFmt"
    case 4 => s"MX 输入（aFmt=${p.aFmt}、bFmt=${p.bFmt}）必须 loadWMode=1（B 按 N×K 转置存放），当前为 0。 建议：loadW = Some(1) 或留 None 自动推导"
    case 5 => s"FP32 只能作【输出】格式、不能作 A/B 输入（aFmt=${p.aFmt}、bFmt=${p.bFmt}）。 建议：输入改用受支持的输入格式 INT8/INT4/MXE4M3/MXE5M2/MXE2M1（FP16/BF16 已从 supportedFmts 砍除，不能再作输入）"
    case 6 => s"mxfp4(MXE2M1) 输出要求 N 为偶数（半字节打包），当前 N=${p.n}。 建议：N 改偶数，或 cFmt 换 MXE4M3/MXE5M2"
    // (#146) The original case 7 ("fp4 forces mxBlockSize=64") was removed with
    //   that hardware branch. However code 7 is STILL LIVE: the CAPACITY guard
    //   (m > cBufferDepth - totalN) drives errCodeComb := 7.U in all three netlists.
    //   (#173-F4) Restoring case 7 with the correct capacity-exceeded explanation.
    case 7 => s"C buffer capacity exceeded: m=${p.m} > cBufferDepth(${cfg.cBufferDepth}) - totalN(${cfg.totalN}) = ${cfg.cBufferDepth - cfg.totalN}. Suggestion: reduce m or increase cBufferDepth."
    case 8 => s"fp4(E2M1) 输入要求 K 被 kPerBeat(=totalN×4=${cfg.totalN * 4}) 整除 —— 当前 K=${p.k}、aFmt=${p.aFmt}、bFmt=${p.bFmt}。 " +
      s"触发支路：aFmt=MX4（阵列 pass 粒度=${cfg.totalN * 4}）、aFmt=MX8 × bFmt=MX4 混配（B 侧 fp4 源字粒度=${cfg.totalN * 4}，" +
      s"比 A 侧 ${cfg.totalN * 2} 更粗，bKTile 地板除丢尾）、或对应 INT8/INT4 支路。 " +
      s"不整除会让 Executor kTiles 地板除静默丢尾（或 K<kPerBeat 时 16-bit 回绕跑飞）。 " +
      s"建议：K 取 ${cfg.totalN * 4} 的整数倍（一个 kTile = 一个 64-K scale 块 = 单 pass）"
    case 10 => s"C base address not ${cfg.cDmaExtW / 8}-byte aligned (cAddr % dmaBytes != 0). " +
      s"dmaBytes = cDmaExtW/8 = ${cfg.cDmaExtW / 8}. AXI byte-lane contract requires the write " +
      s"address to be bus-word aligned; otherwise shifted strb+data cross a bus-word boundary " +
      s"and silently lose bytes. Suggestion: align cAddr to a multiple of ${cfg.cDmaExtW / 8}."
    // (#178) errCode 11: int/non-int cross-boundary guard (V5-only XOR, #177 RTL).
    case 11 => s"Integer/non-integer format cross-boundary: runIsInt(aFmt=${p.aFmt})=" +
      s"${!isFloatFmt(p.aFmt) && !isMxAny(p.aFmt)} XOR cIsIntOut(cFmt=${p.cFmt})=${isIntFmt(p.cFmt)}. " +
      "The runtime computation type (integer vs float/MX) must match the output format type; " +
      "crossing this boundary produces semantically wrong output bits. " +
      "Suggestion: use integer output (INT32/INT8/INT4) with integer input, " +
      "or float/MX output (FP32/FP16/BF16/MX*) with float/MX input."
    case _ => s"Unrecognized errCode $c -- not registered in errExplain (possibly a new code not yet synced)"
  }
  /** ② 打印体检结论 + 生效配置摘要 + 派生几何（一次打全，便于对着波形看） */
  private def printHealth(p: DebugParams, predicted: Option[Int], plan: Plan): Unit = {
    println(s"[$TAG] ═════════════ HiveCoreQuickSpec 配置体检 ═════════════")
    // (#155 m3) rnd 的打印源从套件级常量改为按 p.rnd 反查枚举成员：加入 per-case rnd 形参后，
    //   这行必须反映【本用例实际生效】的舍入模式，否则 RDN/RUP 用例会打出
    //   "rnd=RoundingMode(0=RNE)(sel=2)" 这种自相矛盾的行（诊断现场直接失真）。
    //   对既有三条用例（p.rnd 全为 0）打印文本【逐字节不变】：
    //   RoundingMode.all(0) 与 RoundingMode.RNE 是同一个枚举成员对象 ⟹ toString 相同
    //   （实测均为 "RoundingMode(0=RNE)"）。
    println(s"[$TAG] 生效配置: ${p.banner} rnd=${RoundingMode.all(p.rnd)}(sel=${p.rnd}) strictAddr=${p.strictAddr}")
    println(s"[$TAG] 派生几何: totalN=${cfg.totalN} cDmaExtW=${cfg.cDmaExtW} aExtW=${cfg.aExtW} bExtW=${cfg.bExtW} " +
      s"kTiles=${plan.aKTiles} mTiles=${(p.m + cfg.totalN - 1) / cfg.totalN} nTiles=${plan.nTiles} beatsPerRow=${plan.beatsPerRow}；" +
      s"期望 beat A=${plan.expAAddrSeq.size} B=${plan.expBAddrSeq.size} C=${plan.expCAddrSeq.size} sA=${plan.expScaleAAddrSeq.size} " +
      s"sB=${plan.expScaleBAddrSeq.size} sC=${plan.expCScaleAddrSeq.size}；内存镜像字节=${plan.memByteCount}")
    if (p.addrModeSel != AddrMode.MAT.litValue.toInt) println(s"[$TAG] ⚠ addrMode=TILE 已写入 reg5[14:12] 并回读对账通过；" +
      "当前 RTL 无消费者，不影响数值结果（内存镜像 / golden / 六路期望地址一律按 MAT 语义生成）")
    predicted match {
      case None => println(s"[$TAG] 体检结论：配置合法，硬件不会置 config-err → 走正向 golden 逐元素比对 ✓")
      case Some(c) => println(s"[$TAG] ★★★ 体检不通过：硬件将在 EXECUTE 后直接置 STATUS.err（errCode=$c），不进计算 ★★★\n" +
        s"[$TAG]   原因：${errExplain(p, c)}\n[$TAG]   处置：仍然继续跑（负向路径）并硬断言硬件确实以 errCode=$c 拒绝、留下波形") }
    val model = precheckConfigErr(p, cfg.totalN, cfg.hasMx4)   // 金锚口径与 RTL 口径不一致时给出 #124 提示
    if (model != predicted) println(s"[$TAG] 注：HiveDebugModel.precheckConfigErr 预测=$model 与本处 RTL 口径=$predicted 不一致" +
      "（#124：errCode 0/1/2 被 RTL 合并为 0，真实优先级是 6>5>4>3>0 的 last-connect 序）")
  }
  /** ⑥ 失败/超时落盘关键数据文件（波形由 finally 的 copyWave 负责） */
  private def dumpFailFiles(p: DebugParams, plan: Plan, addrBad: Seq[String], cmpLines: Seq[String]): Unit = {
    val b = "sim/debug/quick_fail"; val pw = new java.io.PrintWriter(new java.io.File(s"$b.txt"))
    pw.println(p.banner); pw.println("--- 地址对账失配 ---"); addrBad.foreach(pw.println)
    pw.println("--- C 元素失配 ---"); cmpLines.foreach(pw.println); pw.close()
    // 位宽实参由 bytes 口径 (aEb*8) 改为 bits 口径 aEbits（HiveDebugModel Phase 3 的单一真源；
    //   memElemBytes 按其自身注释只保留给「整字节 storeElem 与语义查询」，不用于位宽推导）：
    //   字节对齐族 aEbits == aEb*8 逐值恒等（FP32/INT32 4↔32、FP16/BF16/INT16 2↔16、
    //   INT8/MX8 1↔8）⟹ 这些格式落盘文本【逐字不变】；亚字节族 fp4/INT4 的 aEb=0 会传成
    //   0 位（mask=(1<<0)-1=0 把码字全抹成 0，且曾让 writeMatrixHex 拼出非法格式串 "%00X"
    //   崩在本行、盖掉真实失配原因），改 aEbits=4 后按 nibble 码字正常落盘。
    HiveSimCommon.writeMatrixHex(s"${b}_A.hex", plan.aCode.map(_.map(_.toInt)), plan.aEbits)
    HiveSimCommon.writeMatrixHex(s"${b}_B.hex", plan.bCode.map(_.map(_.toInt)), plan.bEbits)
    println(s"[$TAG] FAIL 数据文件已落盘: $b.txt / ${b}_A.hex / ${b}_B.hex") }

  // ══ 主运行器：③ 镜像+golden → ④ 仿真+六通道收集式地址对账 → ⑤ 逐元素比对 → ⑥⑦ 波形/回读 → ⑧ 收尾 ══
  private def runQuick(p: DebugParams): Unit = {
    val predicted = hwErrCode(p)                     // ① 体检。负向路径不跑金锚 validate：硬件不进计算，
    if (predicted.isEmpty) validate(p, cfg.totalN, cfg.hasMx4)   //   且 validate 的 require 会先于仿真抛掉、用户看不到 errCode
    val plan = new Plan(p, cfg)
    plan.prepare()      // ③ 内存镜像（loadW=1 时 B 为 N×K 转置，HiveDebugModel 已实现）
    printHealth(p, predicted, plan)                  // ② 摘要
    val maxCycles = if (isMxAny(p.cFmt)) math.max(p.maxCycles, 20000) else p.maxCycles
    // 【#166】cLog 升为 (addr,data,strb) 三元组：dmaC 带 wstrb 且已按地址移位，只有
    //   同时收下 strb 才能做 lane 级逐字节对账。sLog（scaleC 通道）保持二元组：
    //   其端口宽度 = 1 字节 (rowW=8 @ totalN=16) => lane 窗口退化为单 lane、移位恒 0,
    //   data 不移位, 不在 AXI lane 偏移契约范围内。(NOT "strb is constant full-select"
    //   -- that old rationale only holds coincidentally when rowW=8; see build/166 ss9.4.)
    val cLog = mutable.ArrayBuffer[HiveSimCommon.CWriteBeat](); val sLog = mutable.ArrayBuffer[(Long, BigInt)]()
    val addrBad = mutable.ArrayBuffer[String](); var addrBadCnt = 0; var cycle = 0; var regRbMsg = ""; var passed = false
    var regRbOk = false; var done = false; var errSeen = false; var errCode = -1; var cmp: Option[plan.CmpSummary] = None
    try {
      simulate(new HiveCore(cfg)) { dut =>
        // ④ 地址对账只收集不抛（strictAddr 决定跑完后是否判失败），一次能看到全部失配点
        def chk(ch: String, i: Int, hw: Long, exp: Seq[Long]): Unit = if (i < exp.size && hw != exp(i)) {
          addrBadCnt += 1
          if (addrBad.size < 16) addrBad += f"$ch beat#$i hw=0x$hw%X exp=0x${exp(i)}%X delta=${hw - exp(i)}%d" }
        /** 读通道统一驱动：req.valid 拍按【请求地址】从字节镜像供数（内存保真，绝不按 beat 序号发数）+ 地址对账 */
        def rdCh(chan: String, cnt: Int, exp: Seq[Long], vld: => Boolean, adr: => Long,
                 rsp: (Boolean, BigInt) => Unit, at: Long => BigInt): Int =
          if (vld && cnt < exp.size) { val a = adr; chk(chan, cnt, a, exp); rsp(true, at(a)); cnt + 1 }
          else { rsp(false, BigInt(0)); cnt }
        // ---- Reset：六个 DMA 通道全置 idle（req.ready=1、rsp.valid=0）----
        Seq(dut.io.dmaA.rsp, dut.io.dmaB.rsp).foreach { r => r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaScaleA.get.rsp, dut.io.dmaScaleB.get.rsp).foreach { r => r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaC.rsp, dut.io.dmaCScale.get.rsp).foreach { r => r.valid.poke(false.B); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaA.req, dut.io.dmaB.req, dut.io.dmaC.req, dut.io.dmaScaleA.get.req, dut.io.dmaScaleB.get.req,
          dut.io.dmaCScale.get.req).foreach(_.ready.poke(true.B))
        dut.io.resp.ready.poke(true.B); dut.io.cmd.valid.poke(false.B)
        dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        // ---- 命令通道 helper：REG_WRITE1/2 写寄存器；REG_READ 回读（tail=回读时 resp.data 的 peek 时机）----
        def cmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long, tail: Int = 2): Unit = {
          dut.io.cmd.valid.poke(true.B); dut.io.cmd.payload.op.poke(op)
          dut.io.cmd.payload.reg0Addr.poke(r0.U); dut.io.cmd.payload.reg1Addr.poke(r1.U)
          dut.io.cmd.payload.data0.poke(d0.U); dut.io.cmd.payload.data1.poke(d1.U)
          dut.clock.step(); dut.io.cmd.valid.poke(false.B); dut.clock.step(tail) }
        def readReg(a: Int): BigInt = { cmd(HiveCoreOp.REG_READ, a, 0, 0L, 0L, tail = 1)
          val d = dut.io.resp.payload.data.peek().litValue; dut.clock.step(); d }
        // ---- 配置寄存器（(#164) 新排布：槽位由 12 个压到 8 个有效 = 0..5, 8..11）：
        //      reg0=m  reg1=n|k<<16（低 16 位 n、高 16 位 k，读回 Cat(k,n)）
        //      reg2=aAddr  reg3=bAddr  reg4=cAddr  reg5=控制字（见 HiveDebugModel.controlWord5）
        //      reg8=STATUS 只读  reg9/0A=scaleA/B 基址  regB=scaleC 基址
        //      ★旧 reg6/reg7 已删除：main 侧 wregs 无 is(6)/is(7) 分支 ⟹ 写为 no-op、读回 0。
        //        故本处【不再有】写 reg6/reg7 那一条，回读对账也从 readReg(0x06)/(0x07) 迁到 readReg(0x05)。 ----
        cmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, p.m, p.n.toLong | (p.k.toLong << 16))
        // 【P1-2 W4A8】基址改取 p.aBase/p.bBase（与 Plan.prepare 内存镜像同源，DebugParams
        //   默认即 A_BASE/B_BASE ⟹ 既有用例写入值逐位不变）；负例 ⑤ 借此把 bAddr%32!=0 送进守护。
        cmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, p.aBase, p.bBase)
        cmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, C_BASE, p.controlWord5)
        cmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SA_BASE, SB_BASE); cmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SC_BASE, 0L)
        // ⑦ addrMode / 格式字回读对账：addrMode 无硬件消费者，只能靠回读证明「确实写进去了」
        //   (#164) 重排后控制字全部收敛到 reg5 单寄存器 ⟹ 一次回读即可覆盖 loadWMode/三 MxBlk/
        //   warmup/warmdown/rnd/三 addrMode/三 fmt 共 19 个位域；断言强度【不降反升】（旧写法
        //   两次回读只覆盖 reg6+reg7，新写法一次回读覆盖同一批字段的超集）。regRbOk 仍是硬断言。
        val rb5 = readReg(0x05).toLong
        regRbOk = rb5 == p.controlWord5
        regRbMsg = f"reg5 wr=0x${p.controlWord5}%08X rd=0x$rb5%08X (addrMode at reg5[14:12], rnd at reg5[18:16])"
        println(s"[$TAG] 寄存器回读对账：$regRbMsg → ${if (regRbOk) "一致 ✓" else "不一致 ✗"}")
        // ---- EXECUTE ----
        dut.io.cmd.valid.poke(true.B); dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
        dut.io.cmd.payload.reg0Addr.poke(0.U); dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U); dut.io.cmd.payload.data1.poke(0.U); dut.clock.step(); dut.io.cmd.valid.poke(false.B)
        var aS = 0; var bS = 0; var saS = 0; var sbS = 0; var cB = 0; var csB = 0; var pollSt = 0; var pollCool = 0
        def rdA(v: Boolean, d: BigInt): Unit = { dut.io.dmaA.rsp.valid.poke(v.B); dut.io.dmaA.rsp.payload.data.poke(d.U) }
        def rdB(v: Boolean, d: BigInt): Unit = { dut.io.dmaB.rsp.valid.poke(v.B); dut.io.dmaB.rsp.payload.data.poke(d.U) }
        def rdSA(v: Boolean, d: BigInt): Unit = { dut.io.dmaScaleA.get.rsp.valid.poke(v.B); dut.io.dmaScaleA.get.rsp.payload.data.poke(d.U) }
        def rdSB(v: Boolean, d: BigInt): Unit = { dut.io.dmaScaleB.get.rsp.valid.poke(v.B); dut.io.dmaScaleB.get.rsp.payload.data.poke(d.U) }
        while (!done && !errSeen && cycle < maxCycles) {
          aS = rdCh("dmaA", aS, plan.expAAddrSeq, dut.io.dmaA.req.valid.peek().litToBoolean,
            dut.io.dmaA.req.payload.addr.peek().litValue.toLong, rdA, plan.beatAtA)
          bS = rdCh("dmaB", bS, plan.expBAddrSeq, dut.io.dmaB.req.valid.peek().litToBoolean,
            dut.io.dmaB.req.payload.addr.peek().litValue.toLong, rdB, plan.beatAtB)
          saS = rdCh("dmaScaleA", saS, plan.expScaleAAddrSeq, dut.io.dmaScaleA.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong, rdSA, plan.wordAt)
          sbS = rdCh("dmaScaleB", sbS, plan.expScaleBAddrSeq, dut.io.dmaScaleB.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong, rdSB, plan.wordAt)
          /** 写通道统一驱动：req.valid 拍记录 (addr,data) + 地址对账（scaleC 传空序列即自动跳过对账） */
          def wrCh(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                   ack: Boolean => Unit, log: mutable.ArrayBuffer[(Long, BigInt)]): Int =
            if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da)); ack(true); i + 1 } else { ack(false); i }
          /** 【#166】dmaC 专用写通道驱动：在 wrCh 基础上额外 peek wstrb 并记入三元组日志，
            * 使 HiveSimCommon.auditCBeatLanes 的 lane 级逐字节对账拿得到选通位。
            * 旧口径从不 peek strb —— 那正是「strb 已移位、wdata 未同源移位」在 TB 侧
            * 长期不可见的原因。 */
          def wrChC(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                    st: => BigInt, ack: Boolean => Unit,
                    log: mutable.ArrayBuffer[HiveSimCommon.CWriteBeat]): Int =
            if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da, st)); ack(true); i + 1 } else { ack(false); i }
          cB = wrChC("dmaC", cB, plan.expCAddrSeq, dut.io.dmaC.req.valid.peek().litToBoolean,
            dut.io.dmaC.req.payload.addr.peek().litValue.toLong, dut.io.dmaC.req.payload.data.peek().litValue,
            dut.io.dmaC.req.payload.strb.peek().litValue,
            v => dut.io.dmaC.rsp.valid.poke(v.B), cLog)
          csB = wrCh("dmaCScale", csB, if (isMxAny(p.cFmt)) plan.expCScaleAddrSeq else Seq.empty,
            dut.io.dmaCScale.get.req.valid.peek().litToBoolean, dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong,
            dut.io.dmaCScale.get.req.payload.data.peek().litValue, v => dut.io.dmaCScale.get.rsp.valid.poke(v.B), sLog)
          // STATUS(reg8) 轮询：bit0=busy bit1=done bit2=err bits[6:3]=errCode bits[31:16]=progress
          val (ps, pc, st) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool); pollSt = ps; pollCool = pc
          st.foreach { s =>
            if (s.err) { errSeen = true; errCode = s.errCode; println(f"[$TAG cycle=$cycle%5d] STATUS.err=1 errCode=${s.errCode}") }
            else if (s.done) { done = true; println(f"[$TAG cycle=$cycle%5d] STATUS.done=1 A=$aS B=$bS C=$cB sA=$saS sB=$sbS sC=$csB") } }
          dut.clock.step(); cycle += 1
        }
        dut.io.dmaC.rsp.valid.poke(false.B); dut.io.dmaCScale.get.rsp.valid.poke(false.B); dut.io.cmd.valid.poke(false.B)
        println(s"[$TAG] 运行结束: cycles=$cycle/$maxCycles done=$done err=$errSeen(errCode=$errCode)；beat A=$aS/${plan.expAAddrSeq.size} " +
          s"B=$bS/${plan.expBAddrSeq.size} C=$cB/${plan.expCAddrSeq.size} sA=$saS/${plan.expScaleAAddrSeq.size} sB=$sbS/${plan.expScaleBAddrSeq.size} sC=$csB/${plan.expCScaleAddrSeq.size}")
        println(s"[$TAG] 六通道地址对账不一致拍数=$addrBadCnt（strictAddr=${p.strictAddr}）")
        addrBad.foreach(s => println(s"[$TAG]   ADDR-DIFF $s"))
        // ⑤ golden 逐元素比对；负向路径下硬件根本不该进计算，只核对 errCode
        if (predicted.isDefined) { passed = errSeen && errCode == predicted.get && !done && regRbOk
          println(s"[$TAG] 负向路径：体检预测 errCode=${predicted.get}，实测 err=$errSeen errCode=$errCode done=$done → ${if (passed) "符合预测 ✓" else "与预测不符 ✗"}") }
        else if (!done) println(s"[$TAG] 超时：$maxCycles 拍内未观测到 STATUS.done")
        else { val c = plan.compare(cLog.toSeq, sLog.toSeq); cmp = Some(c)
          println(f"[$TAG] C compare: elemMismatch=${c.mismatch}/${c.elems} laneViolations=${c.laneViolations} " +
            f"maxRelErr=${c.maxRelErr}%.3e maxAbsErr=${c.maxAbsErr}%.3e")
          c.details.zipWithIndex.foreach { case (s, i) => println(s"[$TAG]   ${if (i == 0) "first mismatch" else "MISMATCH"} $s") }
          passed = !errSeen && regRbOk && c.mismatch == 0 && c.laneViolations == 0 && (addrBadCnt == 0 || !p.strictAddr) }
      }
    } finally {
      // ⑥ 波形：成功也 dump；失败/超时同样走这里（simulate 块退出后 verilator 才 flush trace.fst）
      copyWave(SUITE, p.testHint, p.waveTarget)
      val wb = try Files.size(Paths.get(p.waveTarget)) catch { case _: Throwable => -1L }
      if (passed) println(s"[$TAG] PASS: " + cmp.map(c => s"${c.elems - c.mismatch}/${c.elems} elements matched, laneViolations=${c.laneViolations}")   // ⑧ 收尾行
        .getOrElse(s"negative path errCode=${errCode} hit prediction, hardware did not compute") +
        s", addr check all green (mismatch $addrBadCnt beats), reg readback OK, waveform ${p.waveTarget} = $wb bytes")
      else {
        val first = cmp.flatMap(_.details.headOption).getOrElse(if (!done) s"超时未 done（cycles=$cycle/$maxCycles）" else s"errSeen=$errSeen errCode=$errCode regRbOk=$regRbOk")
        println(s"[$TAG] FAIL: elemMismatch=${cmp.map(_.mismatch).getOrElse(-1)}/${cmp.map(_.elems).getOrElse(p.m * p.n)} " +
          s"laneViolations=${cmp.map(_.laneViolations).getOrElse(-1)}, " +
          s"addr mismatch $addrBadCnt beats, first: $first, waveform ${p.waveTarget} ($wb bytes)")
        dumpFailFiles(p, plan, addrBad.toSeq, cmp.map(_.details).getOrElse(Seq.empty)) }
    }
    // 硬断言放在 finally 之后：确保波形与数据文件已落盘再报红
    withClue(s"[$TAG] 寄存器回读对账（$regRbMsg）：") { regRbOk should be(true) }
    if (predicted.isDefined)
      withClue(s"[$TAG] 体检预测硬件应以 errCode=${predicted.get} 拒绝（RTL 把 blk 三违约合并为 0，见文件头更正段）：") {
        (errSeen, errCode, done) should be((true, predicted.get, false)) }
    else {
      withClue(s"[$TAG] 正向路径不应出现 STATUS.err、须在 maxCycles=$maxCycles 内观测到 done：") { (errSeen, done) should be((false, true)) }
      withClue(s"[$TAG] 六通道逐拍地址对账（语义模型 vs 硬件）：") {
        if (p.strictAddr) addrBadCnt should be(0) else println(s"[$TAG] strictAddr=false → 失配 $addrBadCnt 拍只报告不判失败") }
      withClue(s"[$TAG] C element-level golden compare (no tolerance relaxation, no anchor change): ") {
        cmp.map(_.mismatch) should be(Some(0)) }
      withClue(s"[$TAG] C lane-level AXI byte-lane audit (laneViolations): ") {
        cmp.map(_.laneViolations) should be(Some(0)) } }
  }
}
