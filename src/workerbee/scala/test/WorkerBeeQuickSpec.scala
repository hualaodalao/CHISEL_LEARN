/** WorkerBeeQuickSpec — 「一个 runCase 跑一组参数」的极简 WorkerBee GEMM 端到端用例集。
  * 用法：改某条 runCase(...) 的参数，然后重跑裸命令（不带任何 -D）：
  *   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly WorkerBeeQuickSpec"
  * CPATH/LIBRARY_PATH 是硬需求（Verilator 5.050 FST writer 依赖 lz4 头）。波形始终开启（复用
  * WorkerBeeParamDebugSpec.scala 的 WbAlwaysFstSim trait）；本文件不注册任何 -D 键——自定义 -D
  * 须经 ChiselSim HasCliOptions addOption 注册才在白名单内。内存镜像 / 六通道期望地址 / golden /
  * compare 全复用 WbDebugModel，STATUS 轮询复用 WbStatusPollApi。
  *
  * ══ 格式配对约束 ══
  * 合法【输入配对】只有两类：双 MX（MXE4M3/MXE5M2 互组）、双非 MX 浮点；A=MX×B=非MX 数学不可
  *   实现，非法组合由 errCode3 拦。★ 只管输入侧、【不限制输出 cFmt】——「非 MX 输入 → mxfp8
  *   量化输出」合法（C 侧量化消费 fp32 累加器）。
  * MX 输入强制 loadWMode=1（否则 errCode4）；loadW 默认 None = 按 isMx(aFmt)||isMx(bFmt) 推导。
  * addrMode 全 RTL 零消费者：只写 reg5[14:12] + 回读对账、不参与 golden；内存镜像与六路期望地址
  *   一律按 MAT 语义生成，传 TILE 不改变任何数值结果。
  *
  * ══ errCode 口径（以 RTL 为准，与常见 errCode 表不符）══
  * a/b/cMxBlockSize 三违约被【合并】为单一 errMXLargerThanKLoadNum、errCode 恒置 0 ⟹【errCode 1
  * 与 2 永不可达】。各 when 平级 last-connect、后写覆盖前写，真实优先级为
  *   3(反向混配) > 11(int/非int 跨界) > 8(K 不整除) > 13(requant) > 12(biasAddr 对齐)
  *     > 10(cAddr 对齐) > 7(容量，RTL-only) > 5 > 4 > 3(isAMx^isBMx) > 0
  * 两个 code3 谓词互斥、共用一码；code 9 保留给 v2 W4A8；errCode 7 的现役判据只有【容量】守护
  * （m > cBufferDepth - totalN）；errCode 6（MXE2M1 输出 N 为奇数）在 RTL 中被注释掉、金锚仍镜像
  * ⟹ 已知分歧。金锚 errCode 镜像的单一真源是 WbDebugModel.hwErrCode。
  *
  * 产物：波形 sim/debug/quick.fst；失败/超时另落 sim/debug/quick_fail.txt|_A.hex|_B.hex（sim/ 已 gitignore）。 */
import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._
import scala.collection.mutable

class WorkerBeeQuickSpec extends AnyFlatSpec with Matchers with ChiselSim with WbAlwaysFstSim with WbStatusPollApi {
  import WbDebugModel._
  private val TAG = "QUICK"; private val SUITE = "WorkerBeeQuickSpec"

  // ══════════════════════════════════════════════════════════════════════════
  // 主用例：改这一处调用的参数即可；loadW/seed/strictAddr/maxCycles/wave 都有默认值，
  //   不写就是默认（见 runCase 签名）。
  // 【几何 vs N 依赖性】三条通道的地址几何【全部】随 N 变化：B 行步进在 loadWMode=0 分支
  //   = n×bEb、C 行步进 = N×cEb；三条通道的轮数 nTiles = ceil(N/totalN) 也由 N 决定
  //   ⇒ 单独改 n 会同时改动 A/B/C 三条地址序列与三组拍数，不能当单变量对照用。
  // ══════════════════════════════════════════════════════════════════════════
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.MXE2M1,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT)

  // ══════════════════════════════════════════════════════════════════════════
  // MX W4A8：A=MX8 × B=MX4 正向混配五用例。B DMA 走 stretch：逻辑拍 = 16B fp4 半字片
  //   （一个阵列 K 窗口的一行）→ 32B 对齐单读 → DMA 内半字选择 → LUT 精确加宽成
  //   E4M3 阵列拍；scaleBDma 全量 kTile 窗口、每拍一个相邻 scale 字。金锚见
  //   WbDebugModel expBAddrSeq/expScaleBAddrSeq 混配分支；硬件守护见
  //   WorkerBeeCalcConfig（混配 K%64 或 bAddr%32 → errCode 8，反向混配 → 3）。
  //   ★ 每条都必须带 caseTag 使 ScalaTest 名互不撞车（否则 suite 构造期 abort）。
  // ══════════════════════════════════════════════════════════════════════════
  // ① 正向混配 K=64：aBlk=32（=A 侧 kPerBeat，1 scale/kTile）、bBlk=16（窗口 w 的 scale 字含源块
  //   {2w,2w+1} 字节对 ⟹ Executor idx=lane/8 双字节切片可观测）；K=64 ⟹ kTile=2 偶数、末窗读落界内。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_blk16.fst", caseTag = " [P1-2 W4A8 E4M3xE2M1 blk32/16]")

  // ② 正向混配 K=128：多 kTile 轮（kTile=4，覆盖 k/n 双切换）+ bBlk=32
  //   （每窗口 1 个 scale 字节，idx 恒 0，检验单字节路径）。
  runCase(m = 32, n = 32, k = 128, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 32, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_blk32.fst", caseTag = " [P1-2 W4A8 E5M2xE2M1 K128 blk32/32]")

  // ③ 负向：反向混配 A=MX4 × B=MX8 → errCode 3（数据通路未支持）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE2M1, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_rev_err3.fst", caseTag = " [P1-2 W4A8 neg reverse-mix errCode3]")

  // ④ 负向：正向混配 K=32（窗口粒度 32 过 A 侧 MX8 检查，但 K%64 守护仍拒——
  //   kTile=1 奇数会使末窗 32B 单读越出张量界 16B）→ errCode 8。
  runCase(m = 32, n = 32, k = 32, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          wave = "sim/debug/quick_w4a8_k32_err8.fst", caseTag = " [P1-2 W4A8 neg K32 errCode8]")

  // ⑤ 负向：正向混配 bAddr 未 32B 对齐（stretch 半字拍片单读契约被破坏——
  //   奇半字拍的 32B 单读将越出张量界）→ errCode 8。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
          aBlk = 32, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, bBase = B_BASE + 16,
          wave = "sim/debug/quick_w4a8_baddr_err8.fst", caseTag = " [P1-2 W4A8 neg bAddr misalign errCode8]")

  // ══════════════════════════════════════════════════════════════════════════
  // fused bias：C = bias(N) + A·B，四用例。硬件 reg5[30]=biasOn + reg9=biasAddr；bias 取数
  //   盗用 dmaScaleA 端口（顶层 drain-then-steal 仲裁），executor sLOAD_A 的 pass-0 psum 零基底
  //   换 biasLazy（INT 运行 sext32、fp/MX 运行 fp32→lazy40）。金锚：golden 起始累加值
  //   = biasVal(nn)（WbDebugModel，独立 biasRng 激励流）。n=32 ⟹ nTiles=2：每条正向用例都覆盖
  //   「execute 取 nt=0」与「sNEXT_NK_TILE 推进取 nt=1」两个取数时机（biasRegs 跨 K pass/M 行
  //   驻留复用）。
  // ══════════════════════════════════════════════════════════════════════════
  // ⑥ INT8×INT8 + bias → INT32：INT 路基底注入（sext32 进 40b psum 链）；dmaScaleA 端口全程
  //   只有 bias 流量（INT 运行 scaleA DMA 不启动）⟹ 同时覆盖「无 MX 输入时端口盗用不惊扰
  //   任何 scale 通路」。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          biasOn = true, wave = "sim/debug/quick_bias_int8.fst", caseTag = " [P0-ep1 bias INT8xINT8->INT32]")

  // ⑦ MX8×MX8 + bias → FP32：fp 路 fp32→lazy40 基底 + 【关键仲裁用例】——dmaScaleA 端口
  //   上 scaleA（lockstep 预取）与 bias（每 N-tile 16 字）真实交错，双流拆分对账逐拍
  //   验证 drain-then-steal 不丢 scaleA 请求、不误路由 rsp。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          biasOn = true, wave = "sim/debug/quick_bias_mx_fp32.fst", caseTag = " [P0-ep1 bias MXE5M2xFP32]")

  // ⑧ MX8×MX8 + bias → 量化输出 E4M3：bias 烧进累加器后再走 amax→E8M0 量化链——
  //   期望 scale/期望码（OCP 独立推导）与 C 写回比对全部含 bias，dmaCScale 通道同步覆盖。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.MXE4M3,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          biasOn = true, wave = "sim/debug/quick_bias_mx_quant.fst", caseTag = " [P0-ep1 bias MXE5M2xE4M3 quant]")

  // ⑨ 负向：biasBase 非 4B 对齐（BIAS_BASE+2）→ errCode 12（对齐臂，last-connect 序
  //   8 > 12 > 10；本例其余配置合法 ⟹ 只命中 12）。硬件须在 EXECUTE 后直接拒绝、
  //   不进计算（负向路径不跑 validate，绕过金锚 require）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          biasOn = true, biasBase = BIAS_BASE + 2,
          wave = "sim/debug/quick_bias_err12.fst", caseTag = " [P0-ep1 neg biasAddr misalign errCode12]")

  // ══════════════════════════════════════════════════════════════════════════
  // fused requant：量化整数 epilogue 六用例
  //   y = sat_fmt(round_half_up(relu(acc) × M[n] >> shift) + zp)，acc 含 bias 基底。
  //   硬件：reg5[29]=requantOn、reg5[15]=reluOn、reg10=rqMBase、reg11=[31:16]zp+[5:0]shift；
  //   计算独立成 WbRequant 模块（16-lane 并行），OutCvt int32Seq 源级 ReLU clamp
  //   （INT 族专属消费域）。M 取数复用 dmaScaleA 端口（顶层两阶段 epilogue FSM：
  //   WAIT 排空 → FETCH_BIAS → FETCH_M 连续盗用），TB 侧按地址域拆三流对账。
  //   金锚：WbDebugModel goldenC requant 链（Long 整数算术）+ 独立 rqMRng 激励
  //   （定向饱和角点：巨乘子/零/小正/中/小负乘子 lane 混合）。
  // ══════════════════════════════════════════════════════════════════════════
  // ⑩ INT8×INT8 + bias + requant → INT8：epilogue-1×epilogue-2 全叠加。shift=17/zp=5：
  //   小正乘子 lane（|acc·M|≤~9e5≈2^19.8）商落 [-128,127] 值域内（round-half-up 进位
  //   边界 + zp 正偏移可见），巨乘子 lane（2^30/0x30000000）全饱和 ±128，零乘子 lane
  //   输出 clamp(zp)=5。dmaScaleA 端口 bias+rqM 双流连续取数（FETCH→FETCH_M 无重排空）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT8,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          biasOn = true, requantOn = true, rqShift = 17, rqZp = 5,
          wave = "sim/debug/quick_rq_int8_bias.fst", caseTag = " [P0-ep2 requant INT8xINT8+bias->INT8]")

  // ⑪ INT8×INT8 + requant + ReLU → INT8：shift=13/zp=0（ReLU 语义干净：M≥0 lane 全取
  //   正小乘子）。负累加器 clamp 到 0 可观测（典型 |acc·M|>>13 ∈ [0,110] 值域内、负值
  //   仅来自负 acc ⟹ clamp 前后差值直接进输出码），巨乘子 lane 饱和 +127。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT8,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          requantOn = true, reluOn = true, rqShift = 13, rqZp = 0,
          wave = "sim/debug/quick_rq_relu_int8.fst", caseTag = " [P0-ep2 requant+relu INT8xINT8->INT8]")

  // ⑫ INT8×INT8 + requant → INT4：窄饱和域 [-8,7] + 负 zp（-3，覆盖 zp 符号位通路）。
  //   shift=17：小正乘子 lane 商落 [-8,7] 内/贴边（±8/±7 饱和角点密集命中），巨乘子
  //   lane 双侧饱和，零乘子 lane 输出 clamp(-3)=-3。INT4 半字节打包（与 MXE2M1 输出
  //   同 4-bit 打包机制，但值域换成饱和后的 requant 码）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT4,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          requantOn = true, rqShift = 17, rqZp = -3,
          wave = "sim/debug/quick_rq_int4.fst", caseTag = " [P0-ep2 requant INT8xINT8->INT4]")

  // ⑬ 负向：requantOn ∧ cFmt=FP32（∉{INT8,INT4}）→ errCode 13 第一臂。配置取 MX 输入
  //   （runIsInt=false = cIsIntOut=false）避开 errCode 11 先命中；K=64/kPerBeat=32 整除
  //   避开 8；rqMBase 对齐避开 13 第二臂 ⟹ 只命中 13。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.MXE5M2, bFmt = DataFormat.MXE5M2, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT,
          requantOn = true, rqShift = 17, rqZp = 5,
          wave = "sim/debug/quick_rq_err13_fmt.fst", caseTag = " [P0-ep2 neg cFmt non-INT errCode13]")

  // ⑭ 负向：rqMBase 非 4B 对齐（RQM_BASE+2）→ errCode 13 第二臂（对齐臂）。INT8×INT8→INT8
  //   合法配置（cFmt∈{INT8,INT4} 避开 13 第一臂、runIsInt=cIsIntOut 避开 11、K=64 整除
  //   避开 8）⟹ 只命中 13。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT8,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          requantOn = true, rqMBase = RQM_BASE + 2, rqShift = 17, rqZp = 5,
          wave = "sim/debug/quick_rq_err13_align.fst", caseTag = " [P0-ep2 neg rqMBase misalign errCode13]")

  // ⑮ ReLU-only（requantOn=0）：INT 族源级 clamp 的非 requant 消费臂 —— INT32 输出全宽
  //   32-bit 落码，负累加器 clamp 0 直接可见；无 requant/M/bias/DMA 取数副作用
  //   （dmaScaleA 端口全程静默），纯验证 reluOn 乘 INT 输出档的 clamp 数据通路。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          reluOn = true,
          wave = "sim/debug/quick_relu_int32.fst", caseTag = " [P0-ep2 relu-only INT8xINT8->INT32]")

  // ══════════════════════════════════════════════════════════════════════════
  // INT W4A8：A=INT8 × B=INT4 正向混配六用例。与 MX W4A8 同款 B DMA stretch 路：
  //   逻辑拍 = 16B INT4 半字片（32 nibble = 一个阵列 K 窗口的一行）→ 32B 对齐单读 →
  //   DMA 内半字选择 → 顶层符号扩展 Cat(Fill(4,nib(3)),nib(2,0)) 成 32B INT8 阵列拍
  //   （bFmtIn 重映射 INT4→INT8 ⟹ MAC 走纯 INT8 双 lane 基线）；无 scale 通路
  //   （INT 族不取 scale，dmaScaleB 全程静默）。金锚见 WbDebugModel expBAddrSeq 混配
  //   分支（窗口步进 aKpb×4/8=16B、K/aKpb 个窗口 × totalN 行降序、物理读 = 对齐单读）；
  //   硬件守护见 WorkerBeeCalcConfig（混配 loadW=0 / K%64 / bAddr%32 → errCode 8，
  //   反向混配 → 3）。
  // ══════════════════════════════════════════════════════════════════════════
  // ⑯ 正向混配 K=64 → INT32：kTile=2（多窗口轮转）+ nTiles=2（nt 切换）。
  //    ★ loadW 必须显式 Some(1)：None 对 INT 格式自动推导为 0，混配下触发
  //    errCode 8 loadW 臂（stretch 是转置布局专属机制）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_intw4a8_k64.fst", caseTag = " [P1-2 INT W4A8 INT8xINT4->INT32]")

  // ⑰ 正向混配 + bias + requant → INT8：bias + requant 全叠加在混配累加器上
  //    （rqShift/rqZp 与 ⑩ 同参数对照）；混配下 dmaScaleA 端口只有 bias+rqM 双流、
  //    无 scaleA 流量（INT 输入）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT4, cFmt = DataFormat.INT8,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          biasOn = true, requantOn = true, rqShift = 17, rqZp = 5,
          wave = "sim/debug/quick_intw4a8_rq.fst", caseTag = " [P1-2 INT W4A8 requant INT8xINT4+bias->INT8]")

  // ⑱ 负向：反向混配 A=INT4 × B=INT8 → errCode 3（K 窗口 = aKLoadNum=64 元素，
  //    B 每行窗口切片 64B 超拍宽 32B 两倍、须 push 连推 2 拍，几何不可达）。链尾最高
  //    优先级，覆盖 8/11 等一切低码。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT4, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_intw4a8_rev_err3.fst", caseTag = " [P1-2 INT W4A8 neg reverse-mix errCode3]")

  // ⑲ 负向：正向混配 K=32（过 aFmt=INT8 纯格式 K%32 检查，但混配 K%64 守护仍拒——
  //    kTile=1 奇数会使末窗口 32B 单读越出张量界 16B）→ errCode 8。
  runCase(m = 32, n = 32, k = 32, aFmt = DataFormat.INT8, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_intw4a8_k32_err8.fst", caseTag = " [P1-2 INT W4A8 neg K32 errCode8]")

  // ⑳ 负向：正向混配 bAddr 未 32B 对齐（B_BASE+16）→ errCode 8（半字拍片单读契约，
  //    L mod 32 ∉ {0,16} 时奇半字拍的 32B 单读越出张量界）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), bBase = B_BASE + 16,
          wave = "sim/debug/quick_intw4a8_baddr_err8.fst", caseTag = " [P1-2 INT W4A8 neg bAddr misalign errCode8]")

  // ㉑ 负向：正向混配 loadW=0 → errCode 8（loadW 臂，INT 专属）：stretch 半字切片
  //    取转置布局一行内连续 32 个 K 元素，loadW=0 的 K×N 行主序里同列 K 元素跨行
  //    步进、几何不可达。纯 INT8/INT4 两种布局均合法，本臂仅混配命中；
  //    K/bAddr 均合法 ⟹ 只命中 loadW 臂。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.INT8, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(0),
          wave = "sim/debug/quick_intw4a8_loadw0_err8.fst", caseTag = " [P1-2 INT W4A8 neg loadW0 errCode8]")

  // ══════════════════════════════════════════════════════════════════════════
  // 纯 FP8（per-tensor scale）九用例。硬件路线 = remap：WorkerBee 把 FP8E4M3/FP8E5M2
  //   重映射成 MXE4M3/MXE5M2 喂阵列（走 MX8×MX8 基线），WorkerBeeExecutor 在
  //   scaleA/scaleB 注入点恒注入 127（E8M0 2^0=1.0 恒等）——per-tensor scale 由软件
  //   量化 pre-pass 折进权重，硬件不取 scale DMA（FP8 不在 isMxAny ⟹ scale DMA start
  //   门控不启动，金锚侧 scaleA/B 数组与期望地址序列自动退空）。
  //   几何：FP8 进 is1B 族（elemsPerLane=2 ⟹ kPerBeat=totalN×2=32、双 lane 打包、
  //   bColTile 运行期步进 32B）。
  //   ★ loadW 必须显式 Some(1)：None 按 isMxAny 自动推导为 0 → errCode4（FP8 强制
  //     转置布局，镜像 MX8 run-mode 约束）。
  // ══════════════════════════════════════════════════════════════════════════
  // ㉒ 正向基线：FP8E4M3×FP8E4M3 K=64 → FP32。kTile=2（多 K 窗口轮转）+ nTiles=2
  //    （nt 切换）；金锚 = MX 模型 scale≡127（effScale 对 !isMxAny 恒 127，解码/
  //    golden 全复用 MX 元素编解码函数）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E4M3, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_e4m3.fst", caseTag = " [P2 FP8 E4M3xE4M3->FP32]")

  // ㉓ 正向异构：FP8E4M3×FP8E5M2 K=64 → FP32。两侧 remap 独立解码（E4M3 乘积精度
  //    × E5M2 宽动态域），验证 remap 对两种 FP8 码点的独立处理（FP8 族内互组合法，
  //    类比 MX 的 E4M3×E5M2 互组用例）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E5M2, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_hetero.fst", caseTag = " [P2 FP8 E4M3xE5M2->FP32]")

  // ㉔ 正向 + bias → FP32：fp 路 bias 基底注入（fp32→lazy40，biasRunIsInt 对 FP8
  //    取 false）。dmaScaleA 端口全程只有 bias 流量（FP8 非 MX ⟹ scaleA DMA 不启动）
  //    ——与用例 ⑥ 同范式，跑后拆流对账 scaleA 流 = 空。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E5M2, bFmt = DataFormat.FP8E5M2, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          biasOn = true, wave = "sim/debug/quick_fp8_bias.fst", caseTag = " [P2 FP8 E5M2xE5M2+bias->FP32]")

  // ㉕ 正向量化输出：FP8E4M3×FP8E4M3 → MXE4M3 块量化输出（blk16）。「非 MX 输入 →
  //    MX 量化输出」合法（C 侧量化消费 fp32 累加器）——amax→E8M0→E4M3 全链
  //    + dmaCScale 通道流量。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E4M3, cFmt = DataFormat.MXE4M3,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_quant.fst", caseTag = " [P2 FP8 E4M3xE4M3->MXE4M3 quant]")

  // ㉖ 负向：K=48（% kPerBeat=32 ≠ 0）→ errCode 8。FP8 双 lane 每拍推进 32 个 K，
  //    Executor kTiles 地板除静默丢尾（kTile=1.5），运行期拒绝。
  runCase(m = 32, n = 32, k = 48, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E4M3, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_k48_err8.fst", caseTag = " [P2 FP8 neg K48 errCode8]")

  // ㉗ 负向：loadW=0 → errCode 4（FP8 专属臂，hasFp8 门控）：FP8 remap 走 MX8 数据
  //    通路，run-mode 约束（B 转置存放）继承。None 自动推导对 FP8 会取 0（isMxAny
  //    不含 FP8），本例显式 Some(0) 构造。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E4M3, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(0),
          wave = "sim/debug/quick_fp8_loadw0_err4.fst", caseTag = " [P2 FP8 neg loadW0 errCode4]")

  // ㉘ 负向：FP8×INT8 跨族混配 → errCode 3（链尾 FP8 XOR 臂）：remap 只改 FP8 码点，
  //    INT8 侧解码落 INT 支路，FusionMacFp 谓词不命中会静默算错。K=64 整除（避 8）、
  //    loadW=1（避 4）、cFmt=FP32（runIsInt=false=cIsIntOut，避 11）⟹ 只命中链尾 XOR 臂。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.INT8, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_x_int8_err3.fst", caseTag = " [P2 FP8 neg cross-family FP8xINT8 errCode3]")

  // ㉙ 负向：cFmt=FP8E4M3 输出 → errCode 3（链尾 cFmt 臂）：FP8 输出编码无通路
  //    （OutCvt 量化输出是 MX 块 scale 语义；未拒会静默落 fp32 直出支路、写出
  //    错误位宽）。合法输出 = FP32 或 MX 量化。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E4M3, cFmt = DataFormat.FP8E4M3,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_out_err3.fst", caseTag = " [P2 FP8 neg cFmt FP8 errCode3]")

  // ㉚ 负向：FP8 输入 + INT32 输出 → errCode 11（跨界臂）：runIsInt 对 FP8 取 false
  //    （fp 累加族，不补 !isFp8Fmt 会把 FP8 误判为整数族）XOR cIsIntOut(INT32)=true。
  //    FP8 输入的合法输出 = FP32 或 MX 量化（fp 族），INT 输出须配 INT 输入。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.FP8E4M3, bFmt = DataFormat.FP8E4M3, cFmt = DataFormat.INT32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_fp8_int32out_err11.fst", caseTag = " [P2 FP8 neg INT32 output errCode11]")

  // ══════════════════════════════════════════════════════════════════════════
  // BF16 输入格式（默认启用）六用例。硬件路线 = 专用窄乘法器，不走 FP8 那种 remap：
  //   WbFusionMacFp lane0 的第三条分支（hasBf16 && !hasFp16）例化 FpMulBf16Mx，与 MX8
  //   复用同一条乘积槽 L0；WbWorkUnit 的 io.sum 选择链含 BF16 臂（优先级
  //   MX > BF16 > INT > cReg，缺这一臂时 BF16 乘积会被 io.cReg 兜底覆写 ⟹ 通路静默死）。
  //   几何：BF16 非打包、elemsPerLane=1 ⟹ kPerBeat = totalN×1 = 16，每拍 16 个 K、
  //   一 lane 一元素（对照 MX8 双 lane / MX4 四 lane）。
  //   ★ 与 FP8 的三处关键差异，用例参数据此而定：
  //     ① errCode 8 的除数是 16（不是 FP8 的 32）⟹ 负例必须取 K%16≠0；K=48 对 BF16
  //        是【合法】的（48%16=0），照抄 FP8 的 K=48 会得到一条假负例，故本组取 K=40。
  //     ② BF16 是【合法输出编码】（WbOutCvt packed16 直出臂）⟹ 有 cFmt=BF16 正向用例，
  //        与 FP8（cFmt=FP8 被 errCode3 拒）相反。
  //     ③ 非打包几何下 Executor kTiles 取 ceil（非地板除）⟹ K%16≠0 不是静默丢尾，
  //        而是多出一个半填充尾 kTile 把越界 K 元素真算进去；K=0 则 0-1 回绕成 65535。
  //        两种失效模式都由 errCode 8 的「%≠0 || ==0」双判据覆盖。
  //   ★ loadW 两种模式对 BF16 【均合法】：㉛㉜㉝ 取 Some(1) 水平装载、㉟ 取 Some(0)
  //     垂直装载；None 对 BF16 自动推导为 0（BF16 不在 isMxAny），也是合法配置。
  // ══════════════════════════════════════════════════════════════════════════
  // ㉛ 正向基线：BF16×BF16 K=64 → FP32。kTiles = 64/16 = 4（多 K 窗口轮转）+ nTiles=2。
  //    金锚侧 BF16 解码 = fp32BitsToFloat(bits << 16)（BF16 就是 fp32 的高 16 位），
  //    effScale 对非 MX 恒 127 ⟹ 复用 MX 金锚链；tolRel=1e-2（cFmt=FP32 非量化）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.BF16, bFmt = DataFormat.BF16, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_bf16_fp32.fst", caseTag = " [BF16 BF16xBF16->FP32]")

  // ㉜ 正向 BF16 输出：BF16×BF16 K=64 → BF16（WbOutCvt.scala packed16 直出臂）。
  //    输出链 lazy40 → fp32 → BF16 两级舍入，金锚 swFp32ToBf16(norm, rnd)；
  //    WbDebugModel 对 cFmt=BF16 用 tolRel=2e-2（8 位有效位的单步舍入上界）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.BF16, bFmt = DataFormat.BF16, cFmt = DataFormat.BF16,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_bf16_bf16out.fst", caseTag = " [BF16 BF16xBF16->BF16]")

  // ㉝ 正向 + bias → FP32：fp 路 bias 基底注入（biasRunIsInt 对 BF16 取 false，走
  //    fp32→lazy40 基底）。BF16 非 MX ⟹ scaleA DMA 不启动，dmaScaleA 端口全程只有
  //    bias 流量（跑后拆流对账 scaleA 流 = 空），与 FP8 用例 ㉔ 同范式。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.BF16, bFmt = DataFormat.BF16, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          biasOn = true, wave = "sim/debug/quick_bf16_bias.fst", caseTag = " [BF16 BF16xBF16+bias->FP32]")

  // ㉞ 负向：K=40（% kPerBeat=16 ≠ 0）→ errCode 8（BF16 专属臂，hasBf16 门控）。
  //    ★ 不能取 FP8 那条 K=48：48%16=0 对 BF16 合法。40 = 2.5 个 kTile ⟹ ceil 后多出
  //    半填充尾拍，越界 K 元素被真实 DMA 读入并累加（不是丢尾，是算多）。
  //    loadW=1、cFmt=FP32 ⟹ 本例只命中 errCode 8 这一臂。
  runCase(m = 32, n = 32, k = 40, aFmt = DataFormat.BF16, bFmt = DataFormat.BF16, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_bf16_k40_err8.fst", caseTag = " [BF16 neg K40 errCode8]")

  // ㉟ 正向：loadW=0（垂直装载）BF16×BF16 K=64 → FP32。与 ㉛ 的唯一差异就是 loadW：
  //    ㉛ 水平（权重经 a 链移位 + loadWLock 广播锁存），本例垂直（权重经 psumIn 下沉
  //    + loadVLock 锁存），两条路对 wReg 位透明；金锚侧 B 布局自动切到 K×N 行主序
  //    （bRowOff=n×2、bNTileOff=totalN×2）。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.BF16, bFmt = DataFormat.BF16, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(0),
          wave = "sim/debug/quick_bf16_loadw0.fst", caseTag = " [BF16 loadW0 vertical->FP32]")

  // ㊱ 负向：BF16×INT8 跨族混配 → errCode 3（链尾 BF16 单边异或臂）。这一臂封堵的是
  //    既有码位的真空：isMxAny(BF16)=false ⟹ 裸 isAMx^isBMx 不触发；errCode 11 的
  //    runIsInt 对 BF16 取 false（非整数族）⟹ 跨界臂不触发；BF16 的 code8/code4
  //    只看 aFmt、混配可满足。漏网后唯一拦截点是 FusionMacFp 消费沿的仿真期 assert
  //    （综合后消失）⟹ 硅上静默算错（lane0 按 BF16 解 16-bit 字，对侧是 INT 双元素打包）。
  //    ★ 本臂【不】连带拒 cFmt=BF16（BF16 输出合法，见 ㉜）——与 FP8 臂的 cFmt 子句相反。
  runCase(m = 32, n = 32, k = 64, aFmt = DataFormat.BF16, bFmt = DataFormat.INT8, cFmt = DataFormat.FP32,
          aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1),
          wave = "sim/debug/quick_bf16_x_int8_err3.fst", caseTag = " [BF16 neg cross-family BF16xINT8 errCode3]")

  /** 编译期配置（totalN=16；cDmaExtW 默认 256；缓冲深度 2048；scaleBufferDepth=64）。
    * supportedFmts 不显式传入、回落 WorkerBeeConfig 默认集（含 MXE2M1 ⟹ hasMx4=true，
    *   fp4 的 quad-lane 与混配 errCode 3/8 两支真的会 elaborate）。
    * 不要改用 WorkerBeeElaborate.cfgMxFp4 那种 MX-only 三格式集：hasFp 掉假会删掉整条
    *   浮点通路（WbWorkUnit 的 baseSel 退化为 (false,false) => io.cReg，浮点输入用例
    *   会【静默】输出累加器输入而非乘加结果、不报错）。
    * 派生量：totalN=16、cEffW=40、cScaleRowW=8。
    * 【两条 require】require(!hasMx4 || hasMx8) 与 require(!hasMx8 || hasMx4) 合起来钉死
    *   hasMx4 <=> hasMx8；本 cfg 两者皆真。 */
  private val cfg = WorkerBeeConfig(arrayN = 8, clusterM = 2, aW = 16, bW = 16, cW = 40,
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048, scaleBufferDepth = 64)
  // 套件级舍入模式，同时是 runCase 的 per-case `rnd` 形参的默认值来源；要单独换某一条
  //   用例的舍入模式，请在那条 runCase 调用里传 `rnd = RoundingMode.RDN/RUP/...`，
  //   不要改这一行（改这一行会同时改动所有未显式传 rnd 的用例）。
  // ★★【务必保留 def 不要改回 val】本文件的 runCase 调用写在本声明【之前】（Scala 类体
  //   按书写顺序求值）。默认参数表达式是在【调用点即时求值】的（编译器生成
  //   runCase$default$N，在 runCase 那一行就调用它），而 runCase 的函数体延迟到
  //   `it should ... in {}` 执行时才跑。若这里写 `private val rnd`，求值默认参数时该字段
  //   尚未初始化 = null，会抛：
  //     java.lang.NullPointerException: Cannot invoke
  //       "chisel3.ChiselEnum$Type.litValue()" because "rnd$1" is null
  //   且只有写在声明之前的用例会中招，极易被误判为「那条用例坏了」。改成 def 后没有存储
  //   字段、不受类体求值顺序影响，语义与「套件级常量」一致。
  private def rnd: RoundingMode.Type = RoundingMode.RNE
  /** blk(用户面 Int) → MXBlockSize sel：表逐条由枚举成员构造，blk 取定义式 (16 << sel)，不写 sel 字面量（枚举重排也不错位）。
    * MXBlockSize 为 {M16=0, M32=1}：16<<0=16→sel0、16<<1=32→sel1，
    *   与 WbDebugModel.DebugParams.aMxBlk/bMxBlk/cMxBlk 的 16<<sel 反解码逐位对齐。 */
  private val blkToSel: Map[Int, Int] = Seq(MXBlockSize.M16, MXBlockSize.M32)
    .map(e => (16 << e.litValue.toInt) -> e.litValue.toInt).toMap
  private def selOf(w: String, blk: Int): Int = blkToSel.getOrElse(blk, throw new IllegalArgumentException(
    s"[$TAG] $w=$blk 非法：MX 块大小只能取 ${blkToSel.keys.toSeq.sorted.mkString(" / ")}"))

  /** 唯一入口：注册一个 ScalaTest 用例。改顶部调用的参数、重跑即可。
    * per-case `rnd` 形参默认取套件级 `private def rnd`（RNE）；测试名【不含 rnd】
    *   （`it should` 那行只由 m/n/k/三格式/三 blk/addrMode/caseTag 组成）。
    * 默认参数表达式 `= rnd` 求值于外层类作用域（Scala 把默认值编译成
    *   `runCase$default$N` 方法，形参名在其中不可见）⟹ 它引用的是套件级成员而非形参自身。
    * ★该默认值是在【调用点即时求值】的（不像函数体那样延迟到 `in {}` 执行时），而本文件的
    *   runCase 调用写在套件级声明之前 ⟹ 套件级 `rnd` 必须是 `def` 而不能是 `val`，否则
    *   用例会拿到未初始化的 null 字段。详见下方 `private def rnd` 处的说明。 */
  def runCase(m: Int, n: Int, k: Int, aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
              aBlk: Int, bBlk: Int, cBlk: Int, addrMode: AddrMode.Type,
              loadW: Option[Int] = None,   // None = 按 isMx(aFmt)||isMx(bFmt) 自动推导（MX 强制 1）
              seed: Long = 1234L,          // 激励随机种子（固定可复现）
              strictAddr: Boolean = true,  // true = 地址对账失配判失败（仍是收集-后断言，一次看全所有失配点）
              maxCycles: Long = 20000L,    // 仿真拍数上限（量化输出内部再抬到 >= 20000）
              wave: String = "sim/debug/quick.fst",
              // per-case 舍入模式。默认 = 套件级 RNE（下方 `private def rnd`）。
              //   进 DebugParams.rnd（litValue.toInt）⟹ 写入 reg5 bits[18:16]，RTL 与金锚同源。
              rnd: RoundingMode.Type = rnd,
              // per-case B 张量基址（进 reg3=bAddr）。默认 = WbDebugModel.B_BASE；
              //   负例用它造 bAddr%32!=0 的失配。测试名不含 bBase。
              bBase: Long = B_BASE,
              // fused bias 开关与 bias 向量基址（进 reg5[30]=biasOn / reg9=biasAddr）。
              //   biasOn=true：金锚 golden 起始累加值 = biasVal(nn)，硬件 pass-0 psum 基底注入
              //   （sLOAD_A 零填充位换 bias）；TB 侧 dmaScaleA 通道「全量服务 + 跑后双流拆分
              //   对账」（bias 与 scaleA 共端口，drain-then-steal 交错）。测试名不含 bias 参数
              //   ⟹ bias 用例须带 caseTag 区名（DuplicateTestNameException 防撞车纪律）。
              biasOn: Boolean = false,
              biasBase: Long = BIAS_BASE,
              // fused requant / ReLU 开关与参数（进 reg5[29]=requantOn /
              //   reg5[15]=reluOn / reg10=rqMBase / reg11=[31:16]zp+[5:0]shift）。
              //   requantOn=true：golden = 量化后整数 sat(round_half_up(relu(acc)×M>>s)+zp)
              //   （WbDebugModel goldenC requant 链，独立 rqMRng 激励流）；硬件 WbRequant
              //   独立模块 16-lane 并行。reluOn 单独开启（不 requant）也消费于 INT 族输出
              //   （int32Seq 源级 clamp）。TB：dmaScaleA 通道拆【三】流对账（bias / rqM /
              //   scaleA）。测试名不含 requant 参数 ⟹ requant 用例须带 caseTag。
              requantOn: Boolean = false,
              reluOn: Boolean = false,
              rqMBase: Long = RQM_BASE,
              rqShift: Int = 0,
              rqZp: Int = 0,
              // caseTag is appended to the ScalaTest test NAME. It exists for exactly one
              //   reason: two runCase calls with identical (m,n,k,aFmt,bFmt,cFmt,blks,addrMode)
              //   build the SAME test name, and ScalaTest rejects that while the suite is being
              //   CONSTRUCTED -- org.scalatest.Engine.registerTest ends with
              //     if (atomic.get.testsMap.keySet.contains(testName))
              //       throw new DuplicateTestNameException(testName, ...)
              //   (scalatest 3.2.19, Engine.scala:669-674). testName = getTestName(testText,
              //   currentBranch) depends ONLY on the `it should "<text>"` string, so the collision
              //   is decided before any require/simulate/compare ever runs. sbt reports it as
              //   ABORTED (0 tests run), not FAILED. The same collision also lands on the svsim
              //   working directory, which is derived from the test name, so making the name
              //   unique repairs both layers with one change.
              caseTag: String = ""): Unit = {
    val lw = loadW.getOrElse(if (isMxAny(aFmt) || isMxAny(bFmt)) 1 else 0)
    it should s"run M=$m N=$n K=$k $aFmt x $bFmt -> $cFmt (blk $aBlk/$bBlk/$cBlk, addrMode=$addrMode)$caseTag" in {
      runQuick(DebugParams(m = m, n = n, k = k, aFmt = aFmt, bFmt = bFmt, cFmt = cFmt, loadWMode = lw != 0,
        aMxBlkSel = selOf("aBlk", aBlk), bMxBlkSel = selOf("bBlk", bBlk), cMxBlkSel = selOf("cBlk", cBlk),
        addrModeSel = addrMode.litValue.toInt, rnd = rnd.litValue.toInt, seed = seed, strictAddr = strictAddr,
        maxCycles = maxCycles.toInt, waveTarget = wave, testHint = s"run-M-$m-N-$n-K-$k", bBase = bBase,
        biasOn = biasOn, biasBase = biasBase,
        requantOn = requantOn, reluOn = reluOn, rqMBase = rqMBase, rqShift = rqShift, rqZp = rqZp)) }
  }


  // ══ ① 运行前配置体检：镜像 WorkerBeeCalcConfig 的 errComb/errCodeComb 裸块（RTL 真实口径）══
  // kLoadNum 的 INT8/FP8 两臂取 is1B 语义（与 WbDebugModel 同源）；本 def 只用于
  //   errExplain 的提示文本，口径错了会让 INT/FP8 负例打印失真的 kLoadNum/建议值。
  private def kLoadNum(f: DataFormat.Type): Int = if (isMx8(f) || f == DataFormat.INT8 || isFp8Fmt(f)) cfg.totalN * 2 else cfg.totalN * 4  // 镜像 getKLoadNum
  /** Some(code) = 硬件会在 START 后直接置 IRQ_RAW error、不进计算。
    * 权威镜像是 WbDebugModel.hwErrCode（单一真源，顺序 if 后写覆盖，逐位镜像 RTL
    * last-connect 序），本 def 与 precheckConfigErr 均委托它。
    * Truth order: 3(MX4xMX8 mix) > 11(int/non-int cross) > 8(K not divisible)
    *   > 13(requant) > 12(biasAddr) > 10(cAddr alignment) > 7(capacity, RTL-only)
    *   > 5 > 4 > 3(isAMx^isBMx) > 0.
    * errCode 7 的现役判据只有容量守护（m > cBufferDepth - totalN），见下方 case 7。
    * cfg.hasMx4=true ⟹ 混配 3 / fp4 K 整除 8 两支真的会 elaborate。
    * 舍入模式（rnd）不是任何一支 errCode 的输入。 */
  private def hwErrCode(p: DebugParams, cAddr: Long = C_BASE): Option[Int] =
    WbDebugModel.hwErrCode(p, cfg.totalN, cfg.hasMx4, cAddr, cfg.cDmaExtW)

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
      "【P1-2 INT W4A8】正向混配 A=INT8×B=INT4 已合法（同款 stretch：16B 半字拍片 + 符号扩展 INT8）；" +
      "【反向】混配 A=INT4×B=INT8 仍拒（B 每行窗口切片 64B 超拍宽 32B 两倍、须 push 连推 2 拍，几何不可达），本码最高优先级覆盖。 " +
      "【P2 纯 FP8】FP8 跨族混配（isFp8(aFmt) XOR isFp8(bFmt)，如 FP8×INT8——remap 只改 FP8 码点、异侧解码落错支路静默算错）" +
      "与 FP8 输出编码（cFmt=FP8E4M3/E5M2——OutCvt 量化输出是 MX 块 scale 语义、FP8 输出无通路，会静默落 fp32 直出支路）" +
      "同由链尾 errCode3 臂拒绝（最高优先级）。 " +
      // 链尾最后一臂：BF16 单边异或，只覆盖 BF16×INT（BF16×MX 走裸 isAMx^isBMx 臂、
      //   BF16×FP8 走 FP8 异配臂）。★ 与 FP8 臂的差异：不连带拒 cFmt=BF16 —— BF16 是
      //   合法输出编码（WbOutCvt packed16 直出臂），照抄 FP8 的 cFmt 子句会误拒可用通路。
      s"【BF16】BF16 跨族混配（(aFmt===BF16) XOR (bFmt===BF16)，如 BF16×INT8——lane0 按 BF16 解 16-bit 单元素、" +
      s"对侧 INT 是双元素打包，乘积槽语义错位静默算错）由链尾最后一臂拒绝：当前 (aFmt=${p.aFmt} 是 BF16)=" +
      s"${p.aFmt == DataFormat.BF16} XOR (bFmt=${p.bFmt} 是 BF16)=${p.bFmt == DataFormat.BF16}。" +
      "BF16【输出】不受此臂限制（cFmt=BF16 合法）。 " +
      "建议：aFmt/bFmt 改成同族（E4M3/E5M2 互组，或 fp4 E2M1×E2M1，或 BF16×BF16）、正向混配、或双非 MX 浮点；本约束不限制输出 cFmt（但 cFmt 不得为 FP8）"
    case 4 => s"MX 输入（aFmt=${p.aFmt}、bFmt=${p.bFmt}）或纯 FP8 输入必须 loadWMode=1，当前为 0。 " +
      "【两者理由不同，勿混为一谈】MX：scale 须按 K 维切分 + B 须 N×K 转置存放；FP8：remap 走 MX8 数据通路、run-mode 约束继承。 " +
      "【BF16 不在本码范围】2026-10-29 裁定删去 BF16 臂：BF16 无 scale、非打包，MX 的两条理由均不适用，" +
      "且两条装载路径对 wReg 位透明 ⟹ loadW=0/1 对 BF16 均合法（垂直装载正向用例见 ㉟）。 " +
      "建议：loadW = Some(1)（FP8 不在 isMxAny 内，None 会自动取 0，须显式 Some(1)）"
    case 5 => s"FP32 只能作【输出】格式、不能作 A/B 输入（aFmt=${p.aFmt}、bFmt=${p.bFmt}）。 建议：输入改用受支持的输入格式 INT8/INT4/MXE4M3/MXE5M2/MXE2M1/FP8E4M3/FP8E5M2/BF16（BF16 已于全引擎集成时恢复为默认输入格式；FP16 仍未恢复，与 BF16 独立）"
    case 6 => s"mxfp4(MXE2M1) 输出要求 N 为偶数（半字节打包），当前 N=${p.n}。 建议：N 改偶数，或 cFmt 换 MXE4M3/MXE5M2"
    case 7 => s"C buffer capacity exceeded: m=${p.m} > cBufferDepth(${cfg.cBufferDepth}) - totalN(${cfg.totalN}) = ${cfg.cBufferDepth - cfg.totalN}. Suggestion: reduce m or increase cBufferDepth."
    case 8 => s"fp4(E2M1) 输入要求 K 被 kPerBeat(=totalN×4=${cfg.totalN * 4}) 整除 —— 当前 K=${p.k}、aFmt=${p.aFmt}、bFmt=${p.bFmt}。 " +
      s"触发支路：aFmt=MX4（阵列 pass 粒度=${cfg.totalN * 4}）、aFmt=MX8 × bFmt=MX4 混配（B 侧 fp4 源字粒度=${cfg.totalN * 4}，" +
      s"比 A 侧 ${cfg.totalN * 2} 更粗，bKTile 地板除丢尾）、或对应 INT8/INT4 支路。 " +
      s"【P1-2 INT W4A8】混配 A=INT8×B=INT4 另要求 loadW=1（stretch 半字切片取转置布局一行内连续 K 元素，" +
      s"loadW=0 的 K×N 行主序几何不可达；纯 INT 两种布局均合法）+ bAddr 32B 对齐（半字拍片单读契约）。 " +
      s"不整除会让 Executor kTiles 地板除静默丢尾（或 K<kPerBeat 时 16-bit 回绕跑飞）。 " +
      s"建议：K 取 ${cfg.totalN * 4} 的整数倍（一个 kTile = 一个 64-K scale 块 = 单 pass）。 " +
      // BF16 专属臂的除数不同：BF16 非打包、elemsPerLane=1 ⟹ kPerBeat = totalN = 16
      //   （MX8/FP8 是 32、MX4/INT4 是 64）。失效模式也不同：非打包几何下 Executor kTiles
      //   取 ceil 而非地板除，K%16≠0 是「多出半填充尾 kTile、越界元素被读入累加」；
      //   K=0 则 kTiles := 0-1 在 16-bit 上回绕成 65535。同一臂以「% ≠ 0 || == 0」覆盖两者。
      s"【BF16】aFmt=BF16 的除数是 kPerBeat=totalN×1=${cfg.totalN}（非打包、一 lane 一元素），" +
      s"当前 K=${p.k} % ${cfg.totalN} = ${if (cfg.totalN != 0) p.k % cfg.totalN else 0}，且另拒 K=0。" +
      s"注意 K=48 对 BF16 是【合法】的（48%16=0），不要照抄 FP8 的 K=48 负例。建议：K 取 ${cfg.totalN} 的非零整数倍"
    case 10 => s"C base address not ${cfg.cDmaExtW / 8}-byte aligned (cAddr % dmaBytes != 0). " +
      s"dmaBytes = cDmaExtW/8 = ${cfg.cDmaExtW / 8}. AXI byte-lane contract requires the write " +
      s"address to be bus-word aligned; otherwise shifted strb+data cross a bus-word boundary " +
      s"and silently lose bytes. Suggestion: align cAddr to a multiple of ${cfg.cDmaExtW / 8}."
    case 11 => s"Integer/non-integer format cross-boundary: runIsInt(aFmt=${p.aFmt})=" +
      s"${!isFloatFmt(p.aFmt) && !isMxAny(p.aFmt)} XOR cIsIntOut(cFmt=${p.cFmt})=${isIntFmt(p.cFmt)}. " +
      "The runtime computation type (integer vs float/MX) must match the output format type; " +
      "crossing this boundary produces semantically wrong output bits. " +
      "Suggestion: use integer output (INT32/INT8/INT4) with integer input, " +
      "or float/MX output (FP32/FP16/BF16/MX*) with float/MX input."
    case 12 => s"biasOn=1 requires biasAddr (reg9) 4-byte aligned: biasBase=0x${p.biasBase.toHexString}, " +
      s"biasBase % 4 = ${p.biasBase % 4}. The bias fetch drives word (4B) reads directly on the dmaScaleA " +
      "port; a misaligned base makes every word read cross a word boundary and silently corrupts the " +
      "injected psum base. Suggestion: keep biasBase a multiple of 4 (default BIAS_BASE is aligned)."
    // errCode 13 两臂（均无门控）：cFmt 非 INT8/INT4，或 rqMBase 非 4B 对齐。
    case 13 =>
      val fmtArm = p.requantOn && !(p.cFmt == DataFormat.INT8 || p.cFmt == DataFormat.INT4)
      val alignArm = p.requantOn && p.rqMBase % 4L != 0
      (fmtArm, alignArm) match {
        case (true, _) =>
          s"requantOn=1 requires cFmt INT8/INT4: cFmt=${p.cFmt}. The requant chain (WbRequant) only " +
            "defines two saturation domains (INT8 [-128,127] / INT4 [-8,7]); other output formats have " +
            "no defined encoding for the requanted value. Suggestion: set cFmt = INT8 or INT4, or clear " +
            "requantOn (reg5[29])."
        case _ =>
          s"requantOn=1 requires rqMBase (reg10) 4-byte aligned: rqMBase=0x${p.rqMBase.toHexString}, " +
            s"rqMBase % 4 = ${p.rqMBase % 4}. The M-vector fetch reuses the dmaScaleA 32-bit port word " +
            "reads (same two-phase fetcher as bias); a misaligned base makes every word read cross a " +
            "word boundary and silently corrupts the per-channel multipliers. " +
            "Suggestion: keep rqMBase a multiple of 4 (default RQM_BASE is aligned)."
      }
    case _ => s"Unrecognized errCode $c -- not registered in errExplain (possibly a new code not yet synced)"
  }
  /** ② 打印体检结论 + 生效配置摘要 + 派生几何（一次打全，便于对着波形看） */
  private def printHealth(p: DebugParams, predicted: Option[Int], plan: Plan): Unit = {
    println(s"[$TAG] ═════════════ WorkerBeeQuickSpec 配置体检 ═════════════")
    // rnd 打印按 p.rnd 反查枚举成员，必须反映本用例实际生效的舍入模式
    //   （否则 RDN/RUP 用例会打出自相矛盾的诊断行）。
    println(s"[$TAG] 生效配置: ${p.banner} rnd=${RoundingMode.all(p.rnd)}(sel=${p.rnd}) strictAddr=${p.strictAddr}")
    println(s"[$TAG] 派生几何: totalN=${cfg.totalN} cDmaExtW=${cfg.cDmaExtW} aExtW=${cfg.aExtW} bExtW=${cfg.bExtW} " +
      s"kTiles=${plan.aKTiles} mTiles=${(p.m + cfg.totalN - 1) / cfg.totalN} nTiles=${plan.nTiles} beatsPerRow=${plan.beatsPerRow}；" +
      s"期望 beat A=${plan.expAAddrSeq.size} B=${plan.expBAddrSeq.size} C=${plan.expCAddrSeq.size} sA=${plan.expScaleAAddrSeq.size} " +
      s"sB=${plan.expScaleBAddrSeq.size} sC=${plan.expCScaleAddrSeq.size}" +
      (if (p.biasOn) s" bias=${plan.expBiasAddrSeq.size}" else "") +
      (if (p.requantOn) s" rqM=${plan.expRqMAddrSeq.size}" else "") + s"；内存镜像字节=${plan.memByteCount}")
    if (p.addrModeSel != AddrMode.MAT.litValue.toInt) println(s"[$TAG] ⚠ addrMode=TILE 已写入 reg5[14:12] 并回读对账通过；" +
      "当前 RTL 无消费者，不影响数值结果（内存镜像 / golden / 六路期望地址一律按 MAT 语义生成）")
    // reluOn 消费域 = INT 族输出（int32Seq 源级 clamp）；fp/MX 输出对它是零消费者位，如实提示不判失败。
    if (p.reluOn && !isIntFmt(p.cFmt)) println(s"[$TAG] ⚠ reluOn=1 但 cFmt=${p.cFmt} 非 INT 族输出：clamp 无消费者，" +
      "数值结果与 reluOn=0 逐位相同（金锚同口径不 clamp）")
    predicted match {
      case None => println(s"[$TAG] 体检结论：配置合法，硬件不会置 config-err → 走正向 golden 逐元素比对 ✓")
      case Some(c) => println(s"[$TAG] ★★★ 体检不通过：硬件将在 START 后直接置 IRQ_RAW error（errCode=$c），不进计算 ★★★\n" +
        s"[$TAG]   原因：${errExplain(p, c)}\n[$TAG]   处置：仍然继续跑（负向路径）并硬断言硬件确实以 errCode=$c 拒绝、留下波形") }
    val model = precheckConfigErr(p, cfg.totalN, cfg.hasMx4)
    if (model != predicted) println(s"[$TAG] 注：WbDebugModel.precheckConfigErr 预测=$model 与本处 RTL 口径=$predicted 不一致" +
      "（#124：errCode 0/1/2 被 RTL 合并为 0，真实优先级是 6>5>4>3>0 的 last-connect 序）")
  }
  /** ⑥ 失败/超时落盘关键数据文件（波形由 finally 的 copyWave 负责） */
  private def dumpFailFiles(p: DebugParams, plan: Plan, addrBad: Seq[String], cmpLines: Seq[String]): Unit = {
    val b = "sim/debug/quick_fail"; val pw = new java.io.PrintWriter(new java.io.File(s"$b.txt"))
    pw.println(p.banner); pw.println("--- 地址对账失配 ---"); addrBad.foreach(pw.println)
    pw.println("--- C 元素失配 ---"); cmpLines.foreach(pw.println); pw.close()
    // 位宽实参取 bits 口径 aEbits（WbDebugModel 的单一真源）：亚字节族 fp4/INT4 的
    //   aEb=0 会传成 0 位，mask=(1<<0)-1=0 把码字全抹成 0；aEbits=4 才能按 nibble 落盘。
    WbSimCommon.writeMatrixHex(s"${b}_A.hex", plan.aCode.map(_.map(_.toInt)), plan.aEbits)
    WbSimCommon.writeMatrixHex(s"${b}_B.hex", plan.bCode.map(_.map(_.toInt)), plan.bEbits)
    println(s"[$TAG] FAIL 数据文件已落盘: $b.txt / ${b}_A.hex / ${b}_B.hex") }

  // ══ 主运行器：③ 镜像+golden → ④ 仿真+六通道收集式地址对账 → ⑤ 逐元素比对 → ⑥⑦ 波形/回读 → ⑧ 收尾 ══
  private def runQuick(p: DebugParams): Unit = {
    val predicted = hwErrCode(p)                     // ① 体检。负向路径不跑金锚 validate：硬件不进计算，
    if (predicted.isEmpty) validate(p, cfg.totalN, cfg.hasMx4)   //   且 validate 的 require 会先于仿真抛掉、用户看不到 errCode
    val plan = new Plan(p, cfg)
    plan.prepare()      // ③ 内存镜像（loadW=1 时 B 为 N×K 转置，WbDebugModel 已实现）
    printHealth(p, predicted, plan)                  // ② 摘要
    val maxCycles = if (isMxAny(p.cFmt)) math.max(p.maxCycles, 20000) else p.maxCycles
    // cLog 为 (addr,data,strb) 三元组：dmaC 带 wstrb 且已按地址移位，必须同时收下 strb
    //   才能做 lane 级逐字节对账。sLog（scaleC 通道）保持二元组：端口宽 1 字节
    //   (rowW=8 @ totalN=16) ⟹ lane 窗口退化为单 lane、移位恒 0，不在 lane 偏移契约内。
    val cLog = mutable.ArrayBuffer[WbSimCommon.CWriteBeat](); val sLog = mutable.ArrayBuffer[(Long, BigInt)]()
    val addrBad = mutable.ArrayBuffer[String](); var addrBadCnt = 0; var cycle = 0; var regRbMsg = ""; var passed = false
    var regRbOk = false; var done = false; var errSeen = false; var errCode = -1; var cmp: Option[plan.CmpSummary] = None
    // perf 采样累加器（供跑后打印与硬断言）
    val perfNames = Seq("dmaA", "dmaB", "dmaC", "scaleA", "scaleB", "scaleC",
      "array", "stallA", "stallB", "epilogue", "busy")
    val perfCnt = Array.fill(perfNames.size)(0)
    try {
      simulate(new WorkerBee(cfg)) { dut =>
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
        dut.io.resp.ready.poke(true.B); dut.io.cmd.foreach(_.valid.poke(false.B))
        dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        // ---- 命令 helper：等待真实 fire/response，再消费响应 ----
        def cmd(op: WorkerBeeOp.Type, r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
          transactReg(dut.clock, dut.io.cmd(0), dut.io.resp, op, r0, r1, BigInt(d0), BigInt(d1))
        }
        def readReg(a: Int): BigInt = readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, a)
        // 配置索引 0..11 紧排：M/NK/A/B/C/CONTROL/ScaleA/ScaleB/ScaleC/Bias/RqM/RqParam。
        // STATUS 独立移到 0x1E，IRQ 寄存器为 0x30..0x34。
        cmd(WorkerBeeOp.REG_WRITE2, 0x00, 0x01, p.m, p.n.toLong | (p.k.toLong << 16))
        // 基址取 p.aBase/p.bBase（与 Plan.prepare 内存镜像同源）；负例 ⑤ 借此把 bAddr%32!=0 送进守护。
        cmd(WorkerBeeOp.REG_WRITE2, 0x02, 0x03, p.aBase, p.bBase)
        cmd(WorkerBeeOp.REG_WRITE2, 0x04, 0x05, C_BASE, p.controlWord5)
        cmd(WorkerBeeOp.REG_WRITE2, WbSimCommon.SCALE_A_REG_ADDR, WbSimCommon.SCALE_B_REG_ADDR, SA_BASE, SB_BASE); cmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.SCALE_C_REG_ADDR, 0x00, SC_BASE, 0L)
        // reg9 = biasAddr（biasOn=false 时不写：硬件默认 0、该通路不激活）。
        if (p.biasOn) cmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.BIAS_REG_ADDR, 0x00, p.biasBase, 0L)
        // reg10 = rqMBase、reg11 = [31:16]zp + [5:0]shift（requantOn=false 时不写；
        //   reluOn 位在 reg5[15] 已随控制字写入，无独立槽位）。
        if (p.requantOn) {
          cmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.RQM_REG_ADDR, 0x00, p.rqMBase, 0L)
          cmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.RQ_PARAM_REG_ADDR, 0x00, p.controlWord14, 0L) }
        // ⑦ addrMode / 格式字回读对账：addrMode 无硬件消费者，只能靠回读证明「确实写进去了」。
        //   控制字全部收敛到 reg5 单寄存器 ⟹ 一次回读即覆盖 loadWMode/三 MxBlk/warmup/
        //   warmdown/rnd/三 addrMode/三 fmt 共 19 个位域。regRbOk 是硬断言。
        val rb5 = readReg(0x05).toLong
        regRbOk = rb5 == p.controlWord5
        regRbMsg = f"reg5 wr=0x${p.controlWord5}%08X rd=0x$rb5%08X (addrMode at reg5[14:12], rnd at reg5[18:16])"
        // reg9（biasAddr）回读对账：biasOn 位本身已由 reg5 bit30 覆盖，此处只补 biasAddr 槽位。
        if (p.biasOn) {
          val rb12 = readReg(WbSimCommon.BIAS_REG_ADDR).toLong
          val rb12Ok = rb12 == (p.biasBase & 0xFFFFFFFFL)
          regRbOk = regRbOk && rb12Ok
          regRbMsg += f"; reg9 wr=0x${p.biasBase & 0xFFFFFFFFL}%08X rd=0x$rb12%08X" }
        // reg10/reg11 回读对账：requantOn/reluOn 位本身已由 reg5 bit29/bit15 覆盖，此处补
        //   rqMBase 与 zp/shift 槽位；reg11 回读 = Cat(rqZp.asUInt, 10'b0, rqShift)，负 zp 以补码位型对账。
        if (p.requantOn) {
          val rb13 = readReg(WbSimCommon.RQM_REG_ADDR).toLong
          val rb14 = readReg(WbSimCommon.RQ_PARAM_REG_ADDR).toLong
          val rb13Ok = rb13 == (p.rqMBase & 0xFFFFFFFFL)
          val rb14Ok = rb14 == (p.controlWord14 & 0xFFFFFFFFL)
          regRbOk = regRbOk && rb13Ok && rb14Ok
          regRbMsg += f"; reg10 wr=0x${p.rqMBase & 0xFFFFFFFFL}%08X rd=0x$rb13%08X; " +
            f"reg11 wr=0x${p.controlWord14 & 0xFFFFFFFFL}%08X rd=0x$rb14%08X" }
        println(s"[$TAG] 寄存器回读对账：$regRbMsg → ${if (regRbOk) "一致 ✓" else "不一致 ✗"}")
        // START 不清历史中断；每次任务显式 W1C 清 RAW 并 unmask。
        prepareStart(dut.clock, dut.io.cmd(0), dut.io.resp)
        launchStart(dut.clock, dut.io.cmd(0))
        var aS = 0; var bS = 0; var saS = 0; var sbS = 0; var cB = 0; var csB = 0; var pollSt = 0; var pollCool = 0
        // TB 代行外部 PMU：主循环逐拍采样 io.perf 各电平累加（采样窗 = EXECUTE 起至 done/err/超时）
        def perfPeek: Seq[Boolean] = Seq(
          dut.io.perf.dmaA.peek().litToBoolean, dut.io.perf.dmaB.peek().litToBoolean,
          dut.io.perf.dmaC.peek().litToBoolean, dut.io.perf.scaleA.peek().litToBoolean,
          dut.io.perf.scaleB.peek().litToBoolean, dut.io.perf.scaleC.peek().litToBoolean,
          dut.io.perf.array.peek().litToBoolean, dut.io.perf.stallA.peek().litToBoolean,
          dut.io.perf.stallB.peek().litToBoolean, dut.io.perf.epilogue.peek().litToBoolean,
          dut.io.perf.busy.peek().litToBoolean)
        // dmaScaleA 通道地址全量记录：biasOn 时该端口被 drain-then-steal 仲裁复用
        //   （bias FSM 与 scaleA DMA 交错发请求，交错序取决于硬件 FSM 时序、金锚不模拟），
        //   逐拍位置对账不成立 ⟹ 跑后按地址域拆流对账（见 while 循环后的 seqCmp）。
        val saAddrLog = mutable.ArrayBuffer[Long]()
        def rdA(v: Boolean, d: BigInt): Unit = { dut.io.dmaA.rsp.valid.poke(v.B); dut.io.dmaA.rsp.payload.data.poke(d.U) }
        def rdB(v: Boolean, d: BigInt): Unit = { dut.io.dmaB.rsp.valid.poke(v.B); dut.io.dmaB.rsp.payload.data.poke(d.U) }
        def rdSA(v: Boolean, d: BigInt): Unit = { dut.io.dmaScaleA.get.rsp.valid.poke(v.B); dut.io.dmaScaleA.get.rsp.payload.data.poke(d.U) }
        def rdSB(v: Boolean, d: BigInt): Unit = { dut.io.dmaScaleB.get.rsp.valid.poke(v.B); dut.io.dmaScaleB.get.rsp.payload.data.poke(d.U) }
        while (!done && !errSeen && cycle < maxCycles) {
          aS = rdCh("dmaA", aS, plan.expAAddrSeq, dut.io.dmaA.req.valid.peek().litToBoolean,
            dut.io.dmaA.req.payload.addr.peek().litValue.toLong, rdA, plan.beatAtA)
          bS = rdCh("dmaB", bS, plan.expBAddrSeq, dut.io.dmaB.req.valid.peek().litToBoolean,
            dut.io.dmaB.req.payload.addr.peek().litValue.toLong, rdB, plan.beatAtB)
          // dmaScaleA 供数仍是「req.valid 拍按请求地址从内存镜像取数」（wordAt），但不以
          //   超期望拍数断供：bias 流量与 scaleA 流量共用本端口，超拍/错拍由跑后拆流对账暴露。
          if (dut.io.dmaScaleA.get.req.valid.peek().litToBoolean) {
            val saAddr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
            saAddrLog += saAddr; rdSA(true, plan.wordAt(saAddr)); saS += 1
          } else rdSA(false, BigInt(0))
          sbS = rdCh("dmaScaleB", sbS, plan.expScaleBAddrSeq, dut.io.dmaScaleB.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong, rdSB, plan.wordAt)
          /** 写通道统一驱动：req.valid 拍记录 (addr,data) + 地址对账（scaleC 传空序列即自动跳过对账） */
          def wrCh(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                   ack: Boolean => Unit, log: mutable.ArrayBuffer[(Long, BigInt)]): Int =
            if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da)); ack(true); i + 1 } else { ack(false); i }
          /** dmaC 专用写通道驱动：在 wrCh 基础上额外 peek wstrb 并记入三元组日志，
            * 使 WbSimCommon.auditCBeatLanes 的 lane 级逐字节对账拿得到选通位。 */
          def wrChC(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                    st: => BigInt, ack: Boolean => Unit,
                    log: mutable.ArrayBuffer[WbSimCommon.CWriteBeat]): Int =
            if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da, st)); ack(true); i + 1 } else { ack(false); i }
          cB = wrChC("dmaC", cB, plan.expCAddrSeq, dut.io.dmaC.req.valid.peek().litToBoolean,
            dut.io.dmaC.req.payload.addr.peek().litValue.toLong, dut.io.dmaC.req.payload.data.peek().litValue,
            dut.io.dmaC.req.payload.strb.peek().litValue,
            v => dut.io.dmaC.rsp.valid.poke(v.B), cLog)
          csB = wrCh("dmaCScale", csB, if (isMxAny(p.cFmt)) plan.expCScaleAddrSeq else Seq.empty,
            dut.io.dmaCScale.get.req.valid.peek().litToBoolean, dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong,
            dut.io.dmaCScale.get.req.payload.data.peek().litValue, v => dut.io.dmaCScale.get.rsp.valid.poke(v.B), sLog)
          val (ps, pc, st) = irqPollStep(dut.io.cmd(0), dut.io.resp, pollSt, pollCool); pollSt = ps; pollCool = pc
          st.foreach { s =>
            errSeen = s.anyError
            done = s.done
          }
          perfPeek.zipWithIndex.foreach { case (b, i) => if (b) perfCnt(i) += 1 }
          dut.clock.step(); cycle += 1
        }
        dut.io.dmaC.rsp.valid.poke(false.B); dut.io.dmaCScale.get.rsp.valid.poke(false.B); dut.io.cmd.foreach(_.valid.poke(false.B))
        if (errSeen) errCode = readReg(WbSimCommon.ERROR_CODE_REG_ADDR).toInt
        if (predicted.isDefined && errSeen) {
          readReg(WbSimCommon.IRQ_RAW_REG_ADDR) should be(BigInt(2))
          dut.io.status.inFlight.expect(false.B)
        } else if (done && !errSeen) dut.io.status.inFlight.expect(false.B)
        // dmaScaleA 跑后拆流对账（仅正向路径：负向路径硬件不进计算、无期望流量）。
        //   bias 域 [biasBase, +nTiles*totalN*4) 与 rqM 域 [rqMBase, +nTiles*totalN*4) 内的
        //   请求分别归 bias/rqM 流，其余归 scaleA 流（三域两两不相交：SA/BIAS/RQM_BASE 为
        //   独立 64KB 段）；各自与 expBias/expRqM/expScaleAAddrSeq 保序逐拍比对，失配进
        //   addrBad/addrBadCnt。biasOn=requantOn=false 时不拆流、整流对账。
        val saBiasHw: Seq[Long] = {
          val lo = p.biasBase; val hi = p.biasBase + plan.nTiles.toLong * cfg.totalN * 4
          if (p.biasOn) saAddrLog.toSeq.filter(a => a >= lo && a < hi) else Seq.empty }
        val saRqMHw: Seq[Long] = {
          val lo = p.rqMBase; val hi = p.rqMBase + plan.nTiles.toLong * cfg.totalN * 4
          if (p.requantOn) saAddrLog.toSeq.filter(a => a >= lo && a < hi) else Seq.empty }
        val saScaleHw: Seq[Long] =
          if (p.biasOn || p.requantOn) saAddrLog.toSeq.filterNot(a => saBiasHw.contains(a) || saRqMHw.contains(a))
          else saAddrLog.toSeq
        def seqCmp(ch: String, hw: Seq[Long], exp: Seq[Long]): Unit = {
          val n = math.max(hw.size, exp.size); var i = 0
          while (i < n) {
            val h = if (i < hw.size) hw(i) else -1L; val e = if (i < exp.size) exp(i) else -1L
            if (h != e) { addrBadCnt += 1
              if (addrBad.size < 16) addrBad += f"$ch beat#$i hw=${if (h < 0) "MISSING" else f"0x$h%X"} exp=${if (e < 0) "MISSING" else f"0x$e%X"}" }
            i += 1 } }
        if (predicted.isEmpty) {
          seqCmp("dmaScaleA/scaleA", saScaleHw, plan.expScaleAAddrSeq)
          if (p.biasOn) seqCmp("dmaScaleA/bias", saBiasHw, plan.expBiasAddrSeq)
          if (p.requantOn) seqCmp("dmaScaleA/rqM", saRqMHw, plan.expRqMAddrSeq) }
        println(s"[$TAG] 运行结束: cycles=$cycle/$maxCycles done=$done err=$errSeen(errCode=$errCode)；beat A=$aS/${plan.expAAddrSeq.size} " +
          s"B=$bS/${plan.expBAddrSeq.size} C=$cB/${plan.expCAddrSeq.size} sA=${saScaleHw.size}/${plan.expScaleAAddrSeq.size}" +
          (if (p.biasOn) s" bias=${saBiasHw.size}/${plan.expBiasAddrSeq.size}" else "") +
          (if (p.requantOn) s" rqM=${saRqMHw.size}/${plan.expRqMAddrSeq.size}" else "") +
          s" sB=$sbS/${plan.expScaleBAddrSeq.size} sC=$csB/${plan.expCScaleAddrSeq.size}")
        println(s"[$TAG] 六通道地址对账不一致拍数=$addrBadCnt（strictAddr=${p.strictAddr}）")
        addrBad.foreach(s => println(s"[$TAG]   ADDR-DIFF $s"))
        // 跑后 PMU 汇报：各观测电平的采样拍数与占空比（分母 = 主循环拍数 cycle）
        println(s"[$TAG] perf 采样(cycles=$cycle)：" + perfNames.zip(perfCnt).map { case (n, c) =>
          f"$n=$c%5d(${c * 100.0 / math.max(cycle, 1)}%.1f%%)" }.mkString(" "))
        // ⑤ golden 逐元素比对；负向路径下硬件根本不该进计算，只核对 errCode
        if (predicted.isDefined) { passed = errSeen && errCode == predicted.get && !done && regRbOk
          println(s"[$TAG] 负向路径：体检预测 errCode=${predicted.get}，实测 err=$errSeen errCode=$errCode done=$done → ${if (passed) "符合预测 ✓" else "与预测不符 ✗"}") }
        else if (!done) println(s"[$TAG] 超时：$maxCycles 拍内未观测到 IRQ_RAW.done")
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
      withClue(s"[$TAG] 正向路径不应出现 IRQ_RAW error、须在 maxCycles=$maxCycles 内观测到 done：") { (errSeen, done) should be((false, true)) }
      withClue(s"[$TAG] 六通道逐拍地址对账（语义模型 vs 硬件）：") {
        if (p.strictAddr) addrBadCnt should be(0) else println(s"[$TAG] strictAddr=false → 失配 $addrBadCnt 拍只报告不判失败") }
      withClue(s"[$TAG] C element-level golden compare (no tolerance relaxation, no anchor change): ") {
        cmp.map(_.mismatch) should be(Some(0)) }
      withClue(s"[$TAG] C lane-level AXI byte-lane audit (laneViolations): ") {
        cmp.map(_.laneViolations) should be(Some(0)) }
      // 正向路径合理性硬断言：done 已观测 ⟹ busy 与 array 采样必非零（否则 io.perf 接线断裂/采错位）
      withClue(s"[$TAG] perf 端口合理性（正向路径 busy/array 采样须非零）：") {
        perfCnt(perfNames.indexOf("busy")) should be > 0
        perfCnt(perfNames.indexOf("array")) should be > 0 } }
  }
}
