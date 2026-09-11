/** HiveCore P3b MX 量化输出端到端仿真用例（dmaC 数据通道 + dmaCScale scale 通道）。
  *
  * 与 HiveCoreSimCase.runOutFmtGemm 同结构：寄存器配置 → EXECUTE → DMA 供数/收数 → 完成。
  * 输入统一 FP16（aFmt=FP16，isFloat → normSel → 量化生效，无需 scaleA/scaleB 供数），
  * crafted 小整数矩阵（|a|≤4、|b|≤5，K=32 → |和|≤640）保证 40-bit 累加全程精确，
  * double 和转 fp32 与硬件 bit-exact，期望按两级舍入链同链重建：
  *   swFpNormalize40to32(fp32BitsTo40(floatBits(sum))) → swQuantBlock(..., nEffTile)
  *
  * 双通道逐拍请求收集 + 地址硬断言（历史教训：假存储必须校验地址，暴露供数序错位）：
  *   - dmaC 数据通道：量化布局行步进（mxfp8=n×1B / mxfp4=n>>1）与列步进（totalN 同源），
  *     期望地址序列 = 张量语义绝对式 base + nt×cColOff + m×cRowOff + b×cBeatAddrStep
  *     （HiveSimCommon.cAddrSeqDense 唯一推演源；#133 前此处写「含 cDma sNEXT_N 三角
  *     累积跳转（既有遗留语义，测试按同公式推演）」——那是把缺陷 RTL 的行为当规范，
  *     已随 RTL 改为「基址 + 轮偏移」线性模型一并废止）；
  *   - dmaCScale 通道：「基址 + 轮偏移」线性模型——行步进 = ceil(n/blk)、
  *     列 tile 步进 = totalN/blk（与 HiveCoreDmaScaleWr 地址模型逐拍对账）。
  * 数值对拍：scale 字节逐位硬断言；量化数据逐拍硬断言（含高位零填充）；
  * 另按 scale 反量化做容差交叉校验（防期望链自身漂移的第二视角）。
  *
  * 负向（config-err，仿 MxSpec runMxErrCase 轮询模式）：
  *   - blk=32（blkSel=2，N=16 时 blk>totalN）→ STATUS.err 置位、无数据拍；
  *   - mxfp4 + N 奇（n(0)=1）→ STATUS.err 置位、无数据拍。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCaseQuantSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim with HiveStatusPollApi {

  val A_BASE: Long       = 0x00000000L
  val B_BASE: Long       = 0x00100000L
  val C_BASE: Long       = 0x00200000L
  val SCALE_C_BASE: Long = 0x00300000L
  // typical 双 MX 异构用例专用：MX 输入侧 scale 基址（与 SCALE_C_BASE 不重叠）
  val SCALE_A_BASE: Long = 0x00400000L
  val SCALE_B_BASE: Long = 0x00500000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim"

  behavior of "HiveCore Quant GEMM Simulation"

  // ==========================================================================
  // 正向用例注册（覆盖矩阵：
  //   ① blk=8  × E4M3 满宽 RNE（M=32 N=32 K=32）
  //   ② blk=16 × E5M2 满宽 RMM（M=4  N=24：边界 tile nEff=8 掩码 + 跨组块）
  //   ③ blk=8  × E2M1 满宽 RNE（mxfp4 半字节，N=32）
  //   ④ blk=8  × E4M3 缩位 E=8（cDmaExtW=320，active 多拍量化）
  //   ⑤ blk=16 × E2M1 缩位 E=8（M=4 N=24，mxfp4 × active 组合）
  //   ⑥ blk=8  × E5M2 缩位 E=8（评审补齐：E5M2 × 缩位端到端格子，满宽已有 ②）
  //   ⑦⑧ outFmt=011/111 reserved 回落 fp32 定向（评审补齐：量化判据枚举化后验证，
  //      fp32 布局 + 无 dmaCScale 拍 + 无 err；既有套件均无 outFmt=3 覆盖，
  //      故 011/111 两格一并补齐））
  // ==========================================================================
 /*
  【P3 迁移留痕 + W-Ken4 评审更新】本注释块保留历史上下文，不可直接解注释使用。
  块内用例参数已同步至 256 语义：旧哨兵 cDmaExtW=0（→满宽 cExtW）与 320
  （缩位 E=8 多拍）均已废止，统一为 cDmaExtW=256（P1 起唯一位宽：mxfp8
  1 拍/行半占用、mxfp4 1 拍 1/4 占用）；「满宽/缩位」用例名仅存历史语义，
  若恢复启用需按密集布局公共推演源（HiveSimCommon.cAddrSeqDense/
  parseDenseCWriteLog）整体重写 runQuantGemm（含地址推演/期望链/断言）。
  it should "run quant GEMM blk8 E4M3 full width RNE" in {
    runQuantGemm(outFmt3 = 4, blkSel = 0, rnd = 0, cDmaExtW = 256, M = 32, N = 32, K = 32,
      waveName = "quant_blk8_E4M3_full_RNE.fst",
      testMangle = "should-run-quant-GEMM-blk8-E4M3-full-width-RNE")
  }
  it should "run quant GEMM blk16 E5M2 full width RMM" in {
    runQuantGemm(outFmt3 = 5, blkSel = 1, rnd = 4, cDmaExtW = 256, M = 4, N = 24, K = 32,
      waveName = "quant_blk16_E5M2_full_RMM.fst",
      testMangle = "should-run-quant-GEMM-blk16-E5M2-full-width-RMM")
  }
  it should "run quant GEMM blk8 E2M1 full width RNE" in {
    runQuantGemm(outFmt3 = 6, blkSel = 0, rnd = 0, cDmaExtW = 256, M = 32, N = 32, K = 32,
      waveName = "quant_blk8_E2M1_full_RNE.fst",
      testMangle = "should-run-quant-GEMM-blk8-E2M1-full-width-RNE")
  }
  it should "run quant GEMM blk8 E4M3 shrunk E8" in {
    runQuantGemm(outFmt3 = 4, blkSel = 0, rnd = 0, cDmaExtW = 256, M = 32, N = 32, K = 32,
      waveName = "quant_blk8_E4M3_shrunk_E8_RNE.fst",
      testMangle = "should-run-quant-GEMM-blk8-E4M3-shrunk-E8")
  }
  it should "run quant GEMM blk16 E2M1 shrunk E8" in {
    runQuantGemm(outFmt3 = 6, blkSel = 1, rnd = 4, cDmaExtW = 256, M = 4, N = 24, K = 32,
      waveName = "quant_blk16_E2M1_shrunk_E8_RMM.fst",
      testMangle = "should-run-quant-GEMM-blk16-E2M1-shrunk-E8")
  }
  it should "run quant GEMM blk8 E5M2 shrunk E8" in {
    // 评审补齐：端到端矩阵缺失格子「E5M2 × 缩位（active 多拍）」——
    // E5M2 满宽已有（②）、E4M3 缩位已有（④），本例补 E5M2 × cDmaExtW=256（P3 迁移后
    // mxfp8 1 拍/行半占用），双通道逐拍地址硬对账 + swQuantBlock 同链对拍，与既有用例同范式。
    runQuantGemm(outFmt3 = 5, blkSel = 0, rnd = 0, cDmaExtW = 256, M = 32, N = 32, K = 32,
      waveName = "quant_blk8_E5M2_shrunk_E8_RNE.fst",
      testMangle = "should-run-quant-GEMM-blk8-E5M2-shrunk-E8")
  }
  it should "fall back to fp32 with outFmt 111 reserved" in {
    // 评审补齐（P1-1 枚举判据定向）：outFmt=111 必须完整走 fp32 回落路径——
    // 数据按 fp32 密集 4B/元素布局写出、无 dmaCScale 请求、无 err；
    // 修复前单比特判据会命中量化通路产出混合格式。
    runQuantFallbackFp32(outFmt3 = 7,
      waveName = "quant_outfmt111_fallback_fp32.fst",
      testMangle = "should-fall-back-to-fp32-with-outFmt-111-reserved")
  }
  it should "fall back to fp32 with outFmt 011 reserved" in {
    // 011 同理：既有套件（HiveCoreSimCase/HiveOutCvtSpec/MxSpec/LoadWSpec）均无
    // outFmt=3 用例（留痕：未隐式覆盖），故与 111 一并定向补齐，断言同构。
    runQuantFallbackFp32(outFmt3 = 3,
      waveName = "quant_outfmt011_fallback_fp32.fst",
      testMangle = "should-fall-back-to-fp32-with-outFmt-011-reserved")
  }


  // ==========================================================================
  // 负向用例注册
  // ==========================================================================
  it should "reject quant config blk32 over totalN" in {
    // blkSel=2 → blk=32 > totalN=16 → execute 拍 config-err
    runQuantErrCase(outFmt3 = 4, blkSel = 2, M = 4, N = 16, K = 32,
      caseName = "Quant-ERR-blk32")
  }
  it should "reject quant config mxfp4 odd N" in {
    // mxfp4（outFmt=110）+ N=17 奇数 → 行宽非整字节 → execute 拍 config-err
    runQuantErrCase(outFmt3 = 6, blkSel = 0, M = 4, N = 17, K = 32,
      caseName = "Quant-ERR-mxfp4-oddN")
  }
*/

  // ==========================================================================
  // W-Adam1 评审修复：以下用例在注释块【外】注册活跃实例，调用已适配密集 256 布局的
  // 运行器；用户 /* */ 注释块本体保持注释状态不动（历史上下文保留）。
  // ==========================================================================

  // --- reserved outFmt 回落 fp32（真·未定义编码 1011/1111）：验证非量化编码走 fp32 密集直出 ---
  it should "actively fall back to fp32 with outFmt 1111 reserved" in {
    runQuantFallbackFp32(outFmt3 = 15,
      waveName = "quant_outfmt1111_fallback_fp32.fst",
      testMangle = "should-actively-fall-back-to-fp32-with-outFmt-1111-reserved")
  }
  it should "actively fall back to fp32 with outFmt 1011 reserved" in {
    runQuantFallbackFp32(outFmt3 = 11,
      waveName = "quant_outfmt1011_fallback_fp32.fst",
      testMangle = "should-actively-fall-back-to-fp32-with-outFmt-1011-reserved")
  }

  // --- 负向 config-err：cMxBlockSize > totalN（errCode 2）与 mxfp4 奇数 N（errCode 6）---
  it should "actively reject quant config blk32 over totalN" in {
    // cFmt=MXE5M2(4) 为 MX、cMxBlk=2(M32=32>totalN=16) → errCode 2；aFmt=bFmt=FP16 非 MX
    // 以避免误触 errCode 3/4；N=16（偶）避免误触 errCode 6。
    runQuantErrCase(aFmt = 1, bFmt = 1, cFmt = 4, cMxBlk = 2, loadWMode = 0,
      expErrCode = 2, M = 4, N = 16, K = 32, caseName = "Quant-ERR-blk32")
  }
  it should "actively reject quant config mxfp4 odd N" in {
    // cFmt=MXE2M1(5) + N=17（奇）→ errCode 6（B4 恢复的运行期校验）；cMxBlk=0(M8=8≤16)
    // 避免误触 errCode 2；aFmt=bFmt=FP16 非 MX 避免误触 errCode 3/4。N=17 奇数为本用例核心意图。
    runQuantErrCase(aFmt = 1, bFmt = 1, cFmt = 5, cMxBlk = 0, loadWMode = 0,
      expErrCode = 6, M = 4, N = 17, K = 32, caseName = "Quant-ERR-mxfp4-oddN")
  }

  // --- C1 回归：量化 config-err → 转非量化 fp32 运行可恢复（证明 cDmaScale abort 生效）---
  it should "recover from quant config-err to non-quant fp32 run without deadlock" in {
    runQuantErrNonQuantRecovery()
  }

  // ==========================================================================
  // typical 双 MX 异构用例（注册于上方用户 /* ... */ 注释块之外，独立分区）：
  //   A=MXFP8 E4M3（K 轴 32-K 块共享 E8M0 scaleA）× B=MXFP8 E5M2（32-K 块 scaleB，
  //   异构双 MX → mixFmtEn=1，MX 下 B 必须转置读取 → loadWMode=1）→
  //   fp32 MAC（40-bit 延迟规格化累加）→ 40→32 规格化 → 量化写回 mxfp8 E4M3
  //   blk16（outFmt3=4、blkSel=1），dmaC 数据 + dmaCScale scale 双通道
  //   逐拍地址/数据硬对账 + swQuantBlock 同链对拍。
  //   规模 M=32 N=32 K=64：K 覆盖 2 个 32-K scale 块（kTiles=2 多 pass）、
  //   N 覆盖 2 个 nTile（scaleB nt 外×kb 内两级遍历非退化组合）。
  // ==========================================================================
  it should "run typical dual MX quant GEMM M=32 N=32 K=64 E4M3xE5M2 blk16" in {
    runMxQuantGemm(M = 32, N = 32, K = 64,
      waveName = "quant_mx_dual_E4M3xE5M2_blk16.fst",
      testMangle = "should-run-typical-dual-MX-quant-GEMM-M-32-N-32-K-64-E4M3xE5M2-blk16")
  }

  // --- W-Adam2 背压 E2E：dmaCScale req.ready 间歇拉低，验证 scale 不丢/不错位 ---
  it should "run typical dual MX quant GEMM with dmaCScale backpressure" in {
    runMxQuantGemm(M = 32, N = 32, K = 64,
      waveName = "quant_mx_dual_cscale_bp.fst",
      testMangle = "should-run-typical-dual-MX-quant-GEMM-with-dmaCScale-backpressure",
      cScaleBackpressure = true)
  }

  // ==========================================================================
  // 【裁定2 回归护栏】单 kTile（K=32，kBlocks=1）双 MX 异构量化写回：dmaCScale 端口
  //   位宽修复（8 → cfg.cScaleRowW=16）的永久护栏。修复前顶层 dmaCScale 端口硬编码 8-bit，
  //   把 cvt.scaleWire 每行 2 字节 E8M0（totalN=16 → 2 个 8-元素组）的高字节截断丢弃
  //   （实测 CSCALE beat#0 hw=0x7A vs 金锚 0x7A7A）。本例 K=32 单块、M=32（mTiles=2）、
  //   blk16 量化输出，与 MxSpec（K=32、fp16 直出、bit-exact 绿）同 K 规模但走量化写回，
  //   是唯一覆盖「单 kTile MX 量化输出 + dmaCScale 双字节/行」的活跃用例。
  //   【#122 第二阶段 E 项：归因更正】原注「多 kTile（K≥64，kBlocks≥2）数据路仍失配
  //   （executor 多 pass K 累加，属 #118 scaleB/scaleA 领域）」是【错误归因】，与
  //   HiveCoreParamDebugSpec 旧 Section E 自测的「E4 → #118」同源：两处都建立在
  //   「bMxBlk=16<32」这个假前提上，而本运行器 controlWord6（见下方 L1231-1232）把
  //   aMxBlkSel/bMxBlkSel 硬编码为 2(M32=32)，bMxBlk 本就等于 32，E4 判据从未成立。
  //   ParamDebugSpec 的旧 mk() 把 a/b/c 三个 MxBlkSel 压成同一个 mxSel=1，才凭空造出
  //   bMxBlk=16 → 自测在自造假参数上自证 → 错误归因到 #118（该 mk() 已在 D 项拆分更正）。
  //   ★ 正确归因：【#127】MX 多 kTile 量化输出数据路缺陷。已排除项（逐条实证）：
  //     · 不是 B 地址路 —— B=64 拍逐拍硬对账全绿（地址对账不一致拍数=0）；
  //     · 不是 #118 scaleB/scaleA —— #118 范围是 scaleB 三维度评审合并项（判据同源/
  //       RegInit/前递门控/注释契约/断言），不含修复多 kTile MX 数据路失配；且 bMxBlk=32
  //       本就满足「MX 运行时使用 bMxBlk=32」的规避要求；
  //     · 不是 #122（多拍 C 写回块末超弹）—— 本用例 beatsPerRow=ceil(16×8/256)=1，
  //       而 beatsPerRow=1 经实验证明不触发该窗口；且 #122 主修复（HiveCore.scala 恢复
  //       blkLatchCnt/blkLatchFull 同源双门）的因果干预对失败值【逐位零影响】；
  //       另 #122 的 onset 精确落在 beat#64，本用例 onset=beat#0；
  //     · 不是共享 scale 字节错误 —— E4M3 逐元素比值 0.0625~58.67，非均匀、非 2 的幂
  //       （若为 scale 错，relErr 必恒为 2 的幂）→ 是真实数值路分歧。
  //   由上方 K=64 两个用例守护，与本单块护栏正交。
  // ==========================================================================
  it should "run dual MX quant GEMM K=32 single-kTile (dmaCScale width regression guard)" in {
    runMxQuantGemm(M = 32, N = 32, K = 32,
      waveName = "quant_mx_dual_K32_single_ktile.fst",
      testMangle = "should-run-dual-MX-quant-GEMM-K-32-single-kTile")
  }

  // ==========================================================================
  // 正向运行器（历史代码，仅被上方注释块引用）：克隆 runOutFmtGemm 结构 + dmaCScale
  // 双通道。参数 cDmaExtW：2^N ∈ [totalN*8, totalN*32]，当前全部用 256（P0 起唯一
  // 合法值族）。旧 0=满宽 / 320 缩位哨兵已废止。恢复启用需整体重写本函数。
  // 【#136-KW2】本函数体内已装【死码复活硬闸】（见下方 expCAddrSeq 之后的 require）：
  //   #133 只同源化了地址序列，其余期望链仍为旧口径 beatsPerRow = totalN/elemE，与新口径
  //   HiveSimCommon.cBeatsPerRow 恒不相等，故任何调用都会在金锚构造期即炸并给出整体重写
  //   清单（expCDataBeat 解码 / 尾部互斥拍数断言 / parseDenseCWriteLog 接入）。
  //   【#125 裁定：保留不删】本死函数连同其 #136-KW2 复活硬闸一并保留——理由：(a) 复活闸
  //   + 上方 L58 块注释构成「有意保留、复活即高声报错」的归档设计，删函数会连带删掉该闸；
  //   (b) 函数体保存了 legacy beat 口径的金锚推导链，具取证/历史对照价值；(c) 本轮禁跑回归，
  //   唯一安全网是 RTL .sv 逐字 diff，覆盖不到测试树语义变更，故不做无法验证的删除。
  //   详见 build/125_wrap_up_report.md。
  //   outFmt3: DataFormat.MXE4M3/MXE5M2/MXE2M1（【任务104】旧编码 4/5/6 已枚举化）；blkSel: 0=8 1=16 2=32 3=64；
  //   rnd: 0=RNE 1=RTZ 2=RDN 3=RUP 4=RMM
  // ===========================================================================
  private def runQuantGemm(outFmt3: DataFormat.Type, blkSel: Int, rnd: Int, cDmaExtW: Int,
                           M: Int, N: Int, K: Int,
                           waveName: String, testMangle: String): Unit = {
    require(outFmt3 == DataFormat.MXE4M3 || outFmt3 == DataFormat.MXE5M2 || outFmt3 == DataFormat.MXE2M1,
      s"量化用例族仅支持 MXE4M3/MXE5M2/MXE2M1，当前=$outFmt3")
    val blk = 8 << blkSel
    // blk 须 ≤ totalN（否则 execute 拍 config-err）；N 不要求被 blk 整除——
    // 非整除时 padding 列不进 amax（nEff 掩码），正是边界覆盖目标
    val isFp4 = outFmt3 == DataFormat.MXE2M1
    val tag = s"Quant-fmt${outFmt3.litValue}-blk$blk-rnd$rnd-E${if (cDmaExtW == 0) "FULL" else cDmaExtW.toString}-M$M-N$N"

    // --- 配置（与主用例同参数；cDmaExtW=256 密集布局，旧 0/320 哨兵已废止）---
    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 40,
      cDmaExtW = cDmaExtW
    )

    val totalN = cfg.totalN
    val aEffW  = cfg.aEffW
    // P2-清理: P0 删除 Config 旧派生 cElementsPerBeat/cBeatsPerRow（语义已死：按 40-bit 槽口径）；
    // P3 决策：本运行器仅被上方注释块引用，保持编译即可；活跃量化用例已迁至
    // runMxQuantGemm（密集布局公共推演源），恢复启用时需整体重写本函数。
    val cDmaExtWEffLocal = if (cfg.cDmaExtW == 0) cfg.cExtW else cfg.cDmaExtW
    val elemE  = cDmaExtWEffLocal / cfg.cEffW
    val beatsPerRow = totalN / elemE
    val nTiles = (N + totalN - 1) / totalN
    // 量化布局（与 HiveCore calcConfig 量化分支同源）：
    //   mxfp8 行步进 = n×1B、列步进 = totalN×1B；mxfp4 = n>>1 / totalN>>1
    val cRowOff = if (isFp4) (N.toLong >> 1) else N.toLong
    val cColOff = if (isFp4) (totalN.toLong >> 1) else totalN.toLong
    val beatStep = if (isFp4) (elemE.toLong / 2) else elemE.toLong
    // scale 布局（dmaCScale 线性模型）：行步进 = ceil(n/blk)、列步进 = totalN/blk
    val scaleRowOff = (N + blk - 1).toLong / blk
    val scaleColOff = totalN.toLong / blk
    val scaleBytesPerRow = scaleRowOff.toInt

    // --- crafted 小整数矩阵（累加精确，保证期望链 bit-exact）：
    // i==0 零行 → 全零块 → scale 0x00 哨兵；其余 -4..4 / -5..5 多样分布 ---
    def aVal(i: Int, k: Int): Float =
      if (i == 0) 0.0f
      else (((i * 7 + k * 3) % 9) - 4).toFloat
    def bVal(k: Int, j: Int): Float = (((k * 5 + j * 2) % 11) - 5).toFloat
    val aMatrix = Array.tabulate(M, K)((i, j) => HiveSimCommon.floatToFp16(aVal(i, j)))
    val bMatrix = Array.tabulate(K, N)((i, j) => HiveSimCommon.floatToFp16(bVal(i, j)))
    val aFloat = aMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))

    // --- 期望链（与硬件两级舍入链同链重建）：每 (nt, m) 行先 40→32 规格化参考，
    // 再 swQuantBlock 得 (逐元素量化码, 每 8 元素组 scale 字节) ---
    def expectedRow(nt: Int, m: Int): (Seq[Int], Seq[Int]) = {
      val nEffTile = math.min(totalN, N - nt * totalN)
      val normRow = (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        val sum = if (gc < N)
          (0 until K).map(kk => aFloat(m)(kk).toDouble * bFloat(kk)(gc).toDouble).sum
        else 0.0
        val fp32Bits = java.lang.Float.floatToRawIntBits(sum.toFloat) & 0xFFFFFFFFL
        HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(fp32Bits))
      }
      HiveSimCommon.swQuantBlock(normRow, rnd, outFmt3, blkSel, nEffTile)
    }

    // --- DMA-A / DMA-B 期望地址与供数（与主/缩位用例同公式同结构）---
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)
    val aColOff = totalN.toLong * (aEffW / 8)
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = N.toLong * (cfg.bW / 8)
    val bColTileOff = totalN.toLong * (cfg.bW / 8)
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * totalN * bRowOff
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      var idxInBlock = idx % beatsPerRound
      val rowsLast = K - (bKTiles - 1) * totalN
      var row = K
      var kt = 0
      var resolved = false
      while (kt < bKTiles && !resolved) {
        val rows = if (kt == bKTiles - 1) rowsLast else totalN
        if (idxInBlock < rows) {
          row = kt * totalN + (rows - 1 - idxInBlock)
          resolved = true
        } else {
          idxInBlock -= rows
          kt += 1
        }
      }
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = nTileIdx * totalN + i
        val value = if (row < K && col < N) bMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-C 数据通道期望地址：直接调用四套件共用唯一推演源 HiveSimCommon.cAddrSeqDense
    //（张量语义绝对式 base + nt×cColOff + m×cRowOff + b×cBeatAddrStep，量化布局由
    // outFmt3 自动派生：mxfp8 = n×1B / totalN×1B、mxfp4 = n>>1 / totalN>>1）。
    // 【#133 同源化】此处原有第二份「cDma sNEXT_N 三角累积跳转闭式」的独立拷贝，是对
    // 当时缺陷 RTL 的反向拟合（本函数复活即二次投毒），已删除改为单一权威源调用。
    // 【#136-KW2 订正不实注释】#133 原注称 cColOff/beatStep「仅供 println 摘要」，
    // 实测【不成立】：这两个本地 val（上方定义）在全文件范围内【零消费者】——println
    // 摘要只引用 elemE/beatsPerRow/cRowOff/scaleRowOff，不含 cColOff 与 beatStep。
    // 它们自 #133 删除三角闭式拷贝起即为【死 val】（保留只为编译通过与历史对照）。
    // 【#125 裁定：随 runQuantGemm 一并保留不删】——本 val 位于已裁保留的死函数体内，
    // 单独删之与「保留整个函数」的决定不一致，故一并保留。详见 build/125_wrap_up_report.md。
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, outFmt3, cfg.cDmaExtW)
    // 【#136-KW2 硬闸（dead-code revival gate）】本函数（runQuantGemm）是死码：仅被上方
    // L58 起的注释块引用，无任何活跃 it should 调用。#133 只把【地址序列】同源化了，
    // 其余期望链仍是旧口径，故「复活即静默出错」：
    //   · 旧口径 beatsPerRow = totalN/elemE（elemE = cDmaExtW/cEffW = 256/40 = 6）= 16/6 = 2；
    //   · 新口径 HiveSimCommon.cBeatsPerRow(outFmt3, totalN, cDmaExtW)
    //     = ceil(totalN×cOutElemBits/cDmaExtW) = ceil(16×8/256) = 1（MXE4M3/MXE5M2），
    //     mxfp4 同为 1。两者恒不相等。
    // 不加闸时的失败形态（三条，均为误导性）：
    //   (1) expCAddrSeq 长度按新口径 = nTiles×M×1，而下方拍数硬断言之一按旧口径期望
    //       nTiles×M×2 ⇒ 同一 dmaCStoreBeats 被给出【互斥的两个期望值】；
    //   (2) 逐拍地址对账 `expCAddrSeq(dmaCStoreBeats)` 用运行期递增值索引更短的序列 ⇒
    //       IndexOutOfBoundsException，报错点落在仿真循环里，看不出根因是口径分裂；
    //   (3) expCDataBeat / 容差交叉校验用旧 beatsPerRow 与 elemE 反解 (nt,row,beatInRow)
    //       ⇒ 数据错位但地址对账可能仍绿，形成假红/假绿混合。
    // 因此在此处（expCAddrSeq 生成之后、任何消费之前）立即炸，把根因钉在口径分裂上。
    // ★ 本 require 是【死码复活闸】，不是运行期断言：现役量化用例走 runMxQuantGemm，
    //   不经过本函数，故本闸对回归零影响（76/73/3 基线不变）。
    require(beatsPerRow == HiveSimCommon.cBeatsPerRow(outFmt3, totalN, cfg.cDmaExtW),
      s"#136-KW2 runQuantGemm is DEAD CODE with a split beat-count convention: local " +
      s"beatsPerRow=$beatsPerRow (legacy totalN/elemE = $totalN/$elemE) != authoritative " +
      s"HiveSimCommon.cBeatsPerRow(outFmt3=$outFmt3, totalN=$totalN, cDmaExtW=${cfg.cDmaExtW})=" +
      s"${HiveSimCommon.cBeatsPerRow(outFmt3, totalN, cfg.cDmaExtW)}. Reviving this function " +
      "requires an integral rewrite of the whole expectation chain, NOT a local patch: " +
      "(a) drop the legacy beatsPerRow/elemE pair and use HiveSimCommon.cBeatsPerRow + " +
      "cDmaExtW/cOutElemBits; (b) rewrite expCDataBeat's (nt,row,beatInRow) decode; " +
      "(c) fix the mutually exclusive beat-count assertions at the tail " +
      "(dmaCStoreBeats should be expCAddrSeq.size AND nTiles*M*beatsPerRow); " +
      "(d) switch C-write-log parsing to HiveSimCommon.parseDenseCWriteLog; " +
      "(e) the commented-out call sites in the P3 block still pass the pre-enumeration " +
      "numeric outFmt3 (e.g. 4/5/6) which the head require of this function rejects. " +
      "See the P3 decision comment at the top of this file.")
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    // --- DMA-C 期望数据（逐拍硬断言）：量化码密集打包、低列号低位、高位零填充；
    // mxfp4 低列号占低 nibble ---
    def expCDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / (M * beatsPerRow)
      val inBlock = beatIdx % (M * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      val (codes, _) = expectedRow(nt, row)
      var data = BigInt(0)
      for (i <- 0 until elemE) {
        val code = codes(beatInRow * elemE + i)
        if (isFp4) data = data | (BigInt(code & 0xF) << (4 * i))
        else       data = data | (BigInt(code & 0xFF) << (8 * i))
      }
      data
    }

    // --- DMA-CSCALE 期望地址（线性模型：base + nt*colOff + m*rowOff）与期望数据 ---
    val expScaleAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until nTiles; m <- 0 until M)
      expScaleAddrSeq += SCALE_C_BASE + nt * scaleColOff + m * scaleRowOff
    val scaleWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
    def expScaleDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / M
      val row = beatIdx % M
      val (_, scales) = expectedRow(nt, row)
      var data = BigInt(0)
      for ((s, g) <- scales.zipWithIndex if g < scaleBytesPerRow)
        data = data | (BigInt(s & 0xFF) << (8 * g))
      data
    }

    println(s"[$tag] Starting: M=$M N=$N K=$K totalN=$totalN blk=$blk cDmaExtW=$cDmaExtW " +
            s"E=$elemE beatsPerRow=$beatsPerRow cRowOff=$cRowOff scaleRowOff=$scaleRowOff")

    // 【任务102】失败路径也 dump 波形（#94 范式）：try 包 simulate，copyTrace 移入
    // finally——超时/断言失败异常上抛时同样转存，成功路径转存时机（块退出后）
    // 与旧代码完全一致。
    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaBBeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaCScaleBeats = 0

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
      // dmaCScale 新端口集（P3b）：req.ready 常开、rsp 默认不发
      dut.io.dmaCScale.get.req.ready.poke(true.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.payload.err.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // ===== 配置寄存器（新 REG_CONTROL 布局）=====
      // reg7: aFmt=FP16(1)[3:0] | bFmt=FP16(1)[7:4] | cFmt=outFmt3[11:8] | rnd[14:12]（3-bit，#120 C2）；
      // reg6: cMxBlockSize=blkSel[27:26]（qBlockSize 已从旧 reg7[17:16] 迁至 reg6）。
      // 【注】本函数为历史死代码（仅被上方 /* */ 注释块引用，无活跃调用），
      //   恢复启用需按新枚举/位域整体重写（见 L190 说明）；此处仅同步位域布局避免误导。
      val controlWord = (1L) | (1L << 4) |
        ((outFmt3.litValue.toLong & 0xF) << 8) | ((rnd.toLong & 0x7) << 12)
      val controlWord6 = (blkSel.toLong & 0x3) << 26
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, controlWord6, controlWord)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0B, SCALE_C_BASE, SCALE_C_BASE)
      // REG_WRITE2 写 reg0Addr 与 reg1Addr 两个独立寄存器：上一条已同时写 0x09 与 0x0B；
      // 此处再单独写一次 0x0B（cScaleAddr）做冗余确认，行为等价、无副作用
      sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

      // ===== EXECUTE =====
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // 【任务101】EXECUTE 立即响应语义：完成判定 = STATUS 轮询（statusPollStep）
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      val maxCycles = 12000

      while (!done && cycle < maxCycles) {

        // --- DMA-A（req-gated 供数 + 逐拍地址硬对账）---
        // 【任务103】由旧「rsp.valid 恒高 + rsp.ready 计数 + 限前 8 拍软对账」
        // 改为 req.valid 拍响应（与 HiveCoreSimCase/MxSpec 同模式）：RdOnly
        // rsp.ready 恒真，按它计数会把启动/跳转空拍误计进来致下标错位；
        // 且无条件 poke rsp.valid 会在 aDma 尚在 sIDLE 时预灌 A buffer
        // （CalcConfig startOut 两拍延迟），撞 executor 的 "A buffer not
        // drained at execute pulse" 断言。
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C 数据通道：逐拍地址硬对账 + 量化内容硬断言 ---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
            reqAddr should be(expCAddrSeq(dmaCStoreBeats))
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          val expData = expCDataBeat(dmaCStoreBeats)
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats data mismatch " +
                   f"(hw=0x$writeData%X exp=0x$expData%X): ") {
            writeData should be(expData)
          }
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-CSCALE 通道：逐拍地址硬对账 + scale 字节逐位硬断言 ---
        dut.io.dmaCScale.get.req.ready.poke(true.B)
        if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong
          withClue(s"[$tag] DMA-CSCALE beat #$dmaCScaleBeats addr mismatch: ") {
            reqAddr should be(expScaleAddrSeq(dmaCScaleBeats))
          }
          val writeData = dut.io.dmaCScale.get.req.payload.data.peek().litValue
          val expData = expScaleDataBeat(dmaCScaleBeats)
          withClue(s"[$tag] DMA-CSCALE beat #$dmaCScaleBeats data mismatch " +
                   f"(hw=0x$writeData%X exp=0x$expData%X): ") {
            writeData should be(expData)
          }
          scaleWriteLog += ((reqAddr, writeData))
          dmaCScaleBeats += 1
          dut.io.dmaCScale.get.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        }

        // --- DMA-B（req.valid 拍地址硬对账 + 按同下标供权重行）---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B)
        }

        // --- 完成检查：STATUS 寄存器轮询（bit1=done 粘滞位、bit2=err）---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.done) {
            println(f"[$tag cycle=$cycle%5d] EXECUTE complete (STATUS.done=1, err=${st.err})! A=$dmaABeatsSent B=$dmaBBeatsSent " +
                    s"C=$dmaCStoreBeats CSCALE=$dmaCScaleBeats")
            st.err should be(false)
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) println(s"[$tag] WARNING: timeout at $maxCycles cycles " +
        s"(A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats CSCALE=$dmaCScaleBeats)")

      done should be(true)
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      // 拍数硬断言：数据通道 = nTile×M×beatsPerRow；scale 通道 = nTile×M（每行一拍）
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(nTiles * M * beatsPerRow)
      dmaCScaleBeats should be(expScaleAddrSeq.size)
      dmaCScaleBeats should be(nTiles * M)

      // ===== 容差交叉校验（第二视角）：按 scale 反量化重建数值，与 double GEMM
      // 参考对比（量化相对误差上限 = 目标格式半 ulp 量级，取 0.5 保守门限）=====
      val (expW, mantW, bias) = outFmt3 match {
        case DataFormat.MXE4M3 => (4, 3, 7)
        case DataFormat.MXE5M2 => (5, 2, 15)
        case _ => (2, 1, 1)
      }
      def dequant(code: Int, scale: Int): Double = {
        val sg = if (isFp4) (code >> 3) & 1 else (code >> (expW + mantW)) & 1
        val mag = if (isFp4) code & 0x7 else code & ((1 << (expW + mantW)) - 1)
        val e = mag >> mantW
        val mm = mag & ((1 << mantW) - 1)
        if (e == 0 || scale == 0) 0.0
        else {
          val v = (1.0 + mm.toDouble / (1 << mantW)) * math.pow(2, e - bias) * math.pow(2, scale - 127)
          if (sg == 1) -v else v
        }
      }
      var mismatchCnt = 0
      var maxRelErr = 0.0
      var beatIdx = 0
      for ((_, data) <- cWriteLog) {
        val nt = beatIdx / (M * beatsPerRow)
        val inBlock = beatIdx % (M * beatsPerRow)
        val row = inBlock / beatsPerRow
        val beatInRow = inBlock % beatsPerRow
        val scaleBeat = scaleWriteLog(nt * M + row)._2
        for (i <- 0 until elemE) {
          val globalCol = nt * totalN + beatInRow * elemE + i
          if (globalCol < N) {
            val code = if (isFp4) ((data >> (4 * i)) & 0xF).toInt
                       else ((data >> (8 * i)) & 0xFF).toInt
            val localCol = globalCol - nt * totalN
            val grp = localCol / 8   // scale 字节按 tile 内局部列分组（低块号低位），非全局列
            val scale = ((scaleBeat >> (8 * grp)) & 0xFF).toInt
            val hw = dequant(code, scale)
            val ref = (0 until K).map(kk => aFloat(row)(kk).toDouble * bFloat(kk)(globalCol).toDouble).sum
            val absErr = math.abs(hw - ref)
            val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
            // 冲刷界：|ref| < 2^(scale-127) 时元素合法冲刷为 ±0（绝对误差 < 该界），
            // 另有舍入误差 ≤ 半 ulp ≤ 该界；此时相对误差可达 1.0，不得判失（E2M1 实测）
            val flushBound = math.pow(2, scale - 127)
            if (relErr > 0.5 && absErr > 1e-3 && absErr > flushBound + 1e-9) {
              if (mismatchCnt < 5)
                println(f"[$tag] DEQUANT MISMATCH C($row,$globalCol): hw=$hw%.6g ref=$ref%.6g code=0x$code%X scale=0x$scale%02X")
              mismatchCnt += 1
            }
            if (relErr > maxRelErr) maxRelErr = relErr
          }
        }
        beatIdx += 1
      }
      println(f"[$tag] Dequant cross-check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
      mismatchCnt should be(0)
    }
    } finally {
      // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
      // FST 须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 【任务102】置于 finally：超时/断言失败异常上抛后同样在此转存（Verilator
      // 已对失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供回放）。
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCaseQuantSpec/HiveCore-Quant-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        tag
      )
    }
  }

  // ==========================================================================
  // reserved outFmt 回落 fp32 定向运行器（评审补齐）：真·未定义 reserved 编码必须完整降级 fp32。
  //   【枚举重排适配】旧 reserved 3/7 在 DataFormat 重排后已变成合法格式（3=MXE4M3、7=INT16），
  //   不再能表达「reserved 回落」意图；改用新枚举下仍未定义的 11(0b1011)/15(0b1111)
  //   （DataFormat 仅定义 0..9），忠实保留「reserved→fp32 回落」原测试意图。
  //   量化判据统一为枚举（DataFormat.isMx，三处同一判据源：
  //   HiveCoreDmaWrOnly.isQuant / HiveCore.cQuantRun / HiveCoreExecutor.quantActive）后，
  //   reserved 编码全链路回落：数据按 fp32 legacy 布局写出（满宽 40-bit 槽、5B/元素）、
  //   地址按 5B 步进、cScaleDma 不启动（dmaCScale 零请求拍）、量化校验不误触发（无 err）。
  //   与 runQuantGemm 同构：同几何（M=32 N=32 K=32）、同 crafted 矩阵、同期望链（仅末级
  //   改为 fp32 规格化直出），双通道逐拍地址硬对账 + 数据硬断言；qBlockSize 照常写入，
  //   验证非量化运行下该位被忽略（不启动 scale 通道、不触发校验）。
  // ==========================================================================
  private def runQuantFallbackFp32(outFmt3: Int, waveName: String, testMangle: String): Unit = {
    require(outFmt3 == 11 || outFmt3 == 15, s"回落用例仅支持真·未定义 reserved 编码 11/15，当前=$outFmt3")
    val M = 32; val N = 32; val K = 32; val blkSel = 0
    val tag = s"QuantFallback-fmt$outFmt3-M$M-N$N-K$K"

    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 40
      // cDmaExtW 缺省 256（P0 起唯一合法默认值，密集 4B/元素 fp32 布局）
    )

    val totalN = cfg.totalN
    val aEffW  = cfg.aEffW
    val nTiles = (N + totalN - 1) / totalN
    // fp32 回落：密集 4B/元素，行内 256-bit 多拍拆分（fp32: 2 拍/行，elemsPerBeat=8）
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(DataFormat.FP32, totalN, cfg.cDmaExtW)
    val elemsPerBeat = cfg.cDmaExtW / 32

    // --- crafted 小整数矩阵（与 runQuantGemm 同构造，累加精确）---
    def aVal(i: Int, k: Int): Float =
      if (i == 0) 0.0f
      else (((i * 7 + k * 3) % 9) - 4).toFloat
    def bVal(k: Int, j: Int): Float = (((k * 5 + j * 2) % 11) - 5).toFloat
    val aMatrix = Array.tabulate(M, K)((i, j) => HiveSimCommon.floatToFp16(aVal(i, j)))
    val bMatrix = Array.tabulate(K, N)((i, j) => HiveSimCommon.floatToFp16(bVal(i, j)))
    val aFloat = aMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))

    // --- 期望行：fp32 回落 = 40→32 规格化直出（两级舍入链首级，无量化末级）---
    def expectedFp32Row(nt: Int, m: Int): Seq[Long] = {
      (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        val sum = if (gc < N)
          (0 until K).map(kk => aFloat(m)(kk).toDouble * bFloat(kk)(gc).toDouble).sum
        else 0.0
        val fp32Bits = java.lang.Float.floatToRawIntBits(sum.toFloat) & 0xFFFFFFFFL
        HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(fp32Bits))
      }
    }

    // --- DMA-A / DMA-B 期望地址与供数（与 runQuantGemm 同公式同结构，FP16 非 MX 供数）---
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)
    val aColOff = totalN.toLong * (aEffW / 8)
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = N.toLong * (cfg.bW / 8)
    val bColTileOff = totalN.toLong * (cfg.bW / 8)
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * totalN * bRowOff
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      var idxInBlock = idx % beatsPerRound
      val rowsLast = K - (bKTiles - 1) * totalN
      var row = K
      var kt = 0
      var resolved = false
      while (kt < bKTiles && !resolved) {
        val rows = if (kt == bKTiles - 1) rowsLast else totalN
        if (idxInBlock < rows) {
          row = kt * totalN + (rows - 1 - idxInBlock)
          resolved = true
        } else {
          idxInBlock -= rows
          kt += 1
        }
      }
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = nTileIdx * totalN + i
        val value = if (row < K && col < N) bMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-C 期望地址（密集 4B/元素 + 256-bit 多拍；块间跳转 = 张量语义绝对式
    // nt×cColOff，cAddrSeqDense 唯一推演源。#133 订正：旧注「sNEXT_N 三角累积闭式」
    // 是对当时缺陷 RTL 的拟合描述，已废止）---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, DataFormat.FP32, cfg.cDmaExtW)

    // --- DMA-C 期望数据（逐拍硬断言）：每元素 32-bit fp32 规格化值，低列号低位，
    // 256-bit 总线 / 32-bit = 8 元素/拍，2 拍/行 ---
    def expCDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / (M * beatsPerRow)
      val inBlock = beatIdx % (M * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      val norms = expectedFp32Row(nt, row)
      var data = BigInt(0)
      for (i <- 0 until elemsPerBeat) {
        val col = beatInRow * elemsPerBeat + i
        data = data | (BigInt(norms(col) & 0xFFFFFFFFL) << (32 * i))
      }
      data
    }

    println(s"[$tag] Starting: M=$M N=$N K=$K totalN=$totalN outFmt=$outFmt3(reserved→fp32) " +
            s"beatsPerRow=$beatsPerRow elemsPerBeat=$elemsPerBeat")

    // 【任务102】失败路径也 dump 波形（#94 范式）：try 包 simulate，copyTrace 移入
    // finally——超时/断言失败异常上抛时同样转存，成功路径转存时机（块退出后）
    // 与旧代码完全一致。
    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaBBeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaCScaleReqSeen = 0

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
      dut.io.dmaCScale.get.req.ready.poke(true.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.payload.err.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // ===== 配置寄存器（新布局）：FP16 输入（aFmt=bFmt=1）+ reserved cFmt=outFmt3(11/15)→bits[11:8]；
      // qBlockSize(cMxBlockSize) 迁至 reg6[27:26] 照常写入，验证非量化下被忽略；cScaleAddr 也写，验证无 scale 请求 =====
      val controlWord  = (1L) | (1L << 4) | ((outFmt3.toLong & 0xF) << 8)  // reg7: aFmt=FP16|bFmt=FP16<<4|cFmt=reserved
      val controlWord6 = (blkSel.toLong & 0x3) << 26                        // reg6: cMxBlockSize（非量化下应被忽略）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, controlWord6, controlWord)
      sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

      // ===== EXECUTE =====
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // 【任务101】EXECUTE 立即响应语义：完成判定 = STATUS 轮询（statusPollStep）
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      val maxCycles = 12000

      while (!done && cycle < maxCycles) {

        // --- DMA-A（req-gated 供数 + 逐拍地址硬对账）---
        // 【任务103】由旧「rsp.valid 恒高 + rsp.ready 计数 + 限前 8 拍软对账」
        // 改为 req.valid 拍响应（与 HiveCoreSimCase/MxSpec 同模式）：RdOnly
        // rsp.ready 恒真，按它计数会把启动/跳转空拍误计进来致下标错位；
        // 且无条件 poke rsp.valid 会在 aDma 尚在 sIDLE 时预灌 A buffer
        // （CalcConfig startOut 两拍延迟），撞 executor 的 "A buffer not
        // drained at execute pulse" 断言。
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C：逐拍地址硬对账 + fp32 数据硬断言（回落布局）---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
            reqAddr should be(expCAddrSeq(dmaCStoreBeats))
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          val expData = expCDataBeat(dmaCStoreBeats)
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats data mismatch " +
                   f"(hw=0x$writeData%X exp=0x$expData%X): ") {
            writeData should be(expData)
          }
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-CSCALE：回落路径必须零请求拍（全程监视）---
        dut.io.dmaCScale.get.req.ready.poke(true.B)
        if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) dmaCScaleReqSeen += 1
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)

        // --- DMA-B（req.valid 拍地址硬对账 + 按同下标供权重行）---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B)
        }

        // --- 完成检查：STATUS 寄存器轮询（bit1=done 粘滞位、bit2=err）---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.done) {
            println(f"[$tag cycle=$cycle%5d] EXECUTE complete (STATUS.done=1, err=${st.err})! A=$dmaABeatsSent B=$dmaBBeatsSent " +
                    s"C=$dmaCStoreBeats CSCALE_REQ=$dmaCScaleReqSeen")
            st.err should be(false)
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) println(s"[$tag] WARNING: timeout at $maxCycles cycles " +
        s"(A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats CSCALE_REQ=$dmaCScaleReqSeen)")

      done should be(true)
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      // 拍数硬断言：fp32 密集 256-bit 多拍 = nTile×M×beatsPerRow；dmaCScale 全程零请求拍
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(nTiles * M * beatsPerRow)
      withClue(s"[$tag] dmaCScale must stay idle on reserved-outFmt fallback: ") {
        dmaCScaleReqSeen should be(0)
      }
    }
    } finally {
      // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
      // FST 须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 【任务102】置于 finally：超时/断言失败异常上抛后同样在此转存（Verilator
      // 已对失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供回放）。
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCaseQuantSpec/HiveCore-Quant-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        tag
      )
    }
  }

  // ==========================================================================
  // typical 双 MX 异构运行器：MX 计算通路（scaleA/scaleB 按 32-K 块，供数范式与
  //   HiveCoreSimCaseMxSpec 同源）叠加 C 侧量化写回（blk16，双通道硬对账范式与
  //   runQuantGemm 同源）。对拍参考链：E4M3×scaleA × E5M2×scaleB → fp32 MAC →
  //   40→32 → swQuantBlock E4M3 blk16（MX MAC 语义与 MxSpec golden 同源：
  //   mxToFloat 元素解码 + scale 指数加，勿另起漂移源）。
  //   crafted 矩阵元素均为 2^-6 粒度小数（a：0.25 粒度→E4M3 精确；b：0.5 粒度→
  //   E5M2 精确；scale 为 2^±1 幂），乘积/累加全程在 fp32 精确域内（|和|≤1024、
  //   尾数跨度 < 24 bit），故 double 和→fp32 与硬件左结合累加链 bit-exact，
  //   期望链可按 runQuantGemm 同式两级舍入重建。
  //   假存储内存保真（历史教训）：全部读通道（A/B/scaleA/scaleB）仅在 req.valid 拍
  //   响应，逐拍硬断言请求地址，数据按「该地址处真实内存内容」反解码供数；
  //   写通道（dmaC/dmaCScale）逐拍地址+数据硬断言。
  // ==========================================================================
  private def runMxQuantGemm(M: Int, N: Int, K: Int,
                             waveName: String, testMangle: String,
                             cScaleBackpressure: Boolean = false): Unit = {
    val outFmt3 = DataFormat.MXE4M3   // 量化输出 mxfp8 E4M3（【#104】旧编码 4 按用例意图修正：新枚举 4=MXE5M2，E4M3 litValue=3）
    val blkSel  = 1   // qBlockSize：16 元素/块
    val rnd     = 0   // RNE
    val blk = 8 << blkSel
    require(K % 32 == 0, s"MX 用例 K($K) 必须 32 对齐")
    val kBlocks = K / 32
    val tag = s"QuantMX-E4M3xE5M2-blk$blk-M$M-N$N-K$K"

    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,          // totalN = 16（MX 硬约束）
      aBufferDepth = 2048,
      bBufferDepth = 2048,
      cBufferDepth = 2048,
      scaleBufferDepth = 256,
      aW = 16,
      bW = 16,
      cW = 40
    )

    val totalN = cfg.totalN
    val aEffW  = cfg.aEffW
    // P3 迁移：密集布局公共推演源（mxfp8 8-bit 元素：256-bit → 1 拍/行半占用——
    // 拍内 16 个码占低 128 位、高位零；拍内地址步进 = 32B）。
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(outFmt3, totalN, cfg.cDmaExtW)
    val elemsPerBeat = cfg.cDmaExtW / 8
    val nTiles = (N + totalN - 1) / totalN
    val aKTiles = K / 32                // v2：kTile = 一个 32-K scale 块 = 单 pass
    val nTilesB = nTiles
    val bKTiles = K / 32

    // --- scale 布局（dmaCScale 线性模型）：行步进 = ceil(n/blk)、列步进 = totalN/blk ---
    val scaleRowOff = (N + blk - 1).toLong / blk
    val scaleColOff = totalN.toLong / blk
    val scaleBytesPerRow = scaleRowOff.toInt

    // --- MX 输入数据（与 MxSpec 同构造）：scale 指数含正负、覆盖多块/列以暴露路由错位 ---
    def scaleAExp(m: Int, kb: Int): Int = ((m + kb) % 3) - 1        // {-1,0,1}
    def scaleBExp(kb: Int, n: Int): Int = ((n + 2 * kb) % 3) - 1    // {-1,0,1}
    val scaleA = Array.tabulate(M, kBlocks)((m, kb) => HiveSimCommon.mxScaleByte(scaleAExp(m, kb)))
    val scaleB = Array.tabulate(kBlocks, N)((kb, n) => HiveSimCommon.mxScaleByte(scaleBExp(kb, n)))
    def aValF(m: Int, k: Int): Double = ((((m * 7 + k * 3) % 11) - 5).toDouble) * 0.25
    def bValF(k: Int, n: Int): Double = ((((k * 5 + n * 2) % 9)  - 4).toDouble) * 0.5
    val aByte = Array.tabulate(M, K)((m, k) => HiveSimCommon.floatToMxE4M3(aValF(m, k)))
    val bByte = Array.tabulate(K, N)((k, n) => HiveSimCommon.floatToMxE5M2(bValF(k, n)))

    // --- golden 累加（与 MxSpec 同源：mxToFloat 解码 + scale 指数加）；
    // 2^-6 粒度小数在 fp32 累加全程精确 → double 和转 fp32 与硬件 bit-exact ---
    def refSum(m: Int, gc: Int): Double =
      (0 until K).map { k =>
        val kb = k / 32
        HiveSimCommon.mxE4M3ToFloat(aByte(m)(k), scaleA(m)(kb)) *
          HiveSimCommon.mxE5M2ToFloat(bByte(k)(gc), scaleB(kb)(gc))
      }.sum

    // --- 期望链（与硬件两级舍入链同链重建）：40→32 规格化 → swQuantBlock ---
    def expectedRow(nt: Int, m: Int): (Seq[Int], Seq[Int]) = {
      val nEffTile = math.min(totalN, N - nt * totalN)
      val normRow = (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        val sum = if (gc < N) refSum(m, gc) else 0.0
        val fp32Bits = java.lang.Float.floatToRawIntBits(sum.toFloat) & 0xFFFFFFFFL
        HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(fp32Bits))
      }
      HiveSimCommon.swQuantBlock(normRow, rnd, outFmt3, blkSel, nEffTile)
    }

    // --- DMA-A 期望地址（v2 打包：行步长 = K 字节、k 窗口步进 = 2×totalN 字节，
    // 遍历序 nt 外 × kt 中 × m 内）与按地址反解码供数（内存保真） ---
    val aRowOff = K.toLong
    val aColOff = totalN.toLong * (cfg.aW / 8)
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until nTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    def genDmaABeatByAddr(addr: Long): BigInt = {
      val off = addr - A_BASE
      val m = (off / aRowOff).toInt
      val kt = ((off % aRowOff) / aColOff).toInt
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val kEven = kt * 32 + 2 * i
        val kOdd = kEven + 1
        val lo = if (m < M && kEven < K) aByte(m)(kEven) else 0
        val hi = if (m < M && kOdd  < K) aByte(m)(kOdd)  else 0
        data = data | (BigInt(((hi & 0xFF) << 8) | (lo & 0xFF)) << (i * aEffW))
      }
      data
    }

    // --- DMA-B 期望地址（转置存储：行 n 基址 = B_BASE + n×K 字节；遍历序与
    // MxSpec 同：nt 轮 × kt 块内降序行）与按地址反解码供数 ---
    val bRowOff = K.toLong
    val bColTileOff = totalN.toLong * K
    val kSliceStep = totalN.toLong * (cfg.bW / 8)
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * kSliceStep
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    def genDmaBBeatByAddr(addr: Long): BigInt = {
      // 转置布局下行 n 的地址 = B_BASE + n×K；地址低 K 字节内选 k 窗口（32 字节/窗）
      val off = addr - B_BASE
      val n = (off / bRowOff).toInt
      val kt = ((off % bRowOff) / kSliceStep).toInt
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val kEven = kt * 32 + 2 * i
        val kOdd = kEven + 1
        val lo = if (kEven < K && n < N) bByte(kEven)(n) else 0
        val hi = if (kOdd  < K && n < N) bByte(kOdd)(n)  else 0
        data = data | (BigInt(((hi & 0xFF) << 8) | (lo & 0xFF)) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-scaleA 期望地址（独立从张量语义推导，不复用 A 数据步进）：外部布局
    //   scaleA[m][kb] 连续（每行 K/32 字节），地址 = SCALE_A_BASE + m×(K/32) + kb×1；
    //   遍历序仍为 A 式三级（nt 外 × kt 中 × m 内），与 HiveCoreDmaScaleRd isScaleB=false
    //   逐拍同源；v2 单 pass → kb=kt。与按地址反解码供数 ---
    val scaleARowOff = kBlocks.toLong             // 行步进 = K/32 字节
    val scaleAColOff = 1L                         // 块（kb）步进 = 1 字节
    val scaleATensorBytes = M.toLong * kBlocks    // 真实张量字节数 = M×(K/32)
    val expScaleAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until nTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expScaleAAddrSeq += SCALE_A_BASE + m * scaleARowOff + kt * scaleAColOff
    val scaleATotalBeats = expScaleAAddrSeq.size
    // 期望地址足迹边界自检：全部落在真实张量 [base, base+M×(K/32)) 内，
    // 防止再次出现地址越出真实张量的自洽循环。
    expScaleAAddrSeq.foreach { a =>
      assert(a >= SCALE_A_BASE && a < SCALE_A_BASE + scaleATensorBytes,
        s"[$tag] scaleA 期望地址 0x${a.toHexString} 越出张量区间 " +
        s"[0x${SCALE_A_BASE.toHexString}, 0x${(SCALE_A_BASE + scaleATensorBytes).toHexString})")
    }
    def genScaleABeatByAddr(addr: Long): BigInt = {
      val off = addr - SCALE_A_BASE
      val m = (off / scaleARowOff).toInt
      val kt = ((off % scaleARowOff) / scaleAColOff).toInt
      BigInt(scaleA(m)(kt) & 0xFF)
    }

    // --- DMA-scaleB 期望地址 + 按地址供数（【任务109】8-bit 端口收窄适配）---
    // main 侧裁定：dmaScaleB 端口从旧 scaleRowW(=totalN*8=128-bit) 收窄为硬编码 8-bit，
    // 驱动 DMA 为 HiveCoreDmaRdOnly(bufWidth=8, isA=false)。故每拍只供「该地址处」1 字节
    // （内存保真），不再拼 128-bit 列驻留向量——往 8-bit 端口 poke 超宽会让 svsim
    // Verilator harness exit(EXIT_FAILURE) 且不刷盘 FST（波形静默消失）。
    // 【枚举重排适配】aFmt/bFmt 修正为新枚举 MX 值（3=MXE4M3、4=MXE5M2）后，
    // calcConfig XOR 校验不再误触 errCode 3，scaleBDma.start = startOut & isMx(bFmt)
    // 成立 → scaleB 通道全程活跃，须按 HiveCoreDmaRdOnly(isA=false, loadWMode=1)
    // 逐字节降序遍历重推（镜像 MxSpec 同型实现 L373-389）。
    // rowStep/colTileOff 直取 calcConfig scaleB 偏移（HiveCoreCalcConfig L199-214）：
    //   scaleBRowAddressOffset = K/blkB、scaleBColTileAddressOffset = bKLoadNum/blkB；
    //   blkB 由 regFile.bMxBlockSize 决定（controlWord6 bits[25:24]=2=M32 → blkB=32），
    //   bKLoadNum = getKLoadNum(bFmt=MXE5M2, mx8) = totalN*2 = 32。
    val blkB = 8 << 2                                  // bMxBlockSize=2(M32) → 32（controlWord6 硬编码 2L<<24）
    val bKLoadNum = totalN * 2                         // bFmt 为 mx8 → totalN*2 = 32
    val scaleBRowOff     = (K / blkB).toLong           // = calcConfig.scaleBRowAddressOffset
    val scaleBColTileOff = (bKLoadNum / blkB).toLong   // = calcConfig.scaleBColTileAddressOffset
    val scaleBKTiles     = bKTiles                     // reg_kTile = calcConfig.kTile = K/32
    // block(nTile=r, kTile=kt) 首址 = base + r*nTileJump + (totalN-1)*rowOff + kt*colTileOff；
    //   nTileJump = (totalN-1)*rowOff + kTile*colTileOff（switch-n 增量，RTL L241）；
    //   块内 totalN 拍降序 -rowOff。总 beat = nTile*kTile*totalN。
    val scaleBNTileJump = (totalN - 1).toLong * scaleBRowOff + scaleBKTiles.toLong * scaleBColTileOff
    val expScaleBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until scaleBKTiles) {
      val blockStart = SCALE_B_BASE + r.toLong * scaleBNTileJump +
        (totalN - 1).toLong * scaleBRowOff + kt.toLong * scaleBColTileOff
      for (r2 <- 0 until totalN) expScaleBAddrSeq += blockStart - r2.toLong * scaleBRowOff
    }
    val scaleBTotalBeats = expScaleBAddrSeq.size       // = nTile*kTile*totalN
    val scaleBTensorBytes = kBlocks.toLong * N
    def genScaleBBeatByAddr(addr: Long): BigInt = {
      // 内存映像 scaleB[kb][n] 行主序（每 kb 行占 N 字节），按 req.addr 取 1 字节，越界补 0。
      val off = addr - SCALE_B_BASE
      if (off < 0 || off >= scaleBTensorBytes) BigInt(0)
      else {
        val kb = (off / N).toInt
        val n  = (off % N).toInt
        BigInt(scaleB(kb)(n) & 0xFF)
      }
    }

    // --- DMA-C 数据通道期望地址（P3 迁移：密集布局公共推演源；量化布局 mxfp8：
    // 行步进 = N×1B、列步进 = totalN×1B、拍内步进 32B；块间跳转 = 张量语义绝对式
    // nt×cColOff + 拍内细分。#133 订正：旧注「三角累积闭式」是对当时缺陷 RTL 的
    // 拟合描述，已随 RTL 改线性模型废止）---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, outFmt = outFmt3, cfg.cDmaExtW)
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
    def expCDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / (M * beatsPerRow)
      val inBlock = beatIdx % (M * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      val (codes, _) = expectedRow(nt, row)
      // 密集行（低列号低位、padding 列零）→ 按 cDmaExtW 右移取拍段，
      // 与 WrOnly 发拍表达式逐字同源（256 下 1 拍/行：16 码占低 128 位、高位零）
      val denseRow = HiveSimCommon.elemsToDenseRow(codes.map(_.toLong), outFmt = outFmt3, totalN = totalN)
      HiveSimCommon.denseRowBeat(denseRow, beatInRow, cfg.cDmaExtW)
    }

    // --- DMA-CSCALE 期望地址（线性模型：base + nt×colOff + m×rowOff）与期望数据 ---
    val expCScaleAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until nTiles; m <- 0 until M)
      expCScaleAddrSeq += SCALE_C_BASE + nt * scaleColOff + m * scaleRowOff
    val scaleWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
    def expCScaleDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / M
      val row = beatIdx % M
      val (_, scales) = expectedRow(nt, row)
      var data = BigInt(0)
      for ((s, g) <- scales.zipWithIndex if g < scaleBytesPerRow)
        data = data | (BigInt(s & 0xFF) << (8 * g))
      data
    }

    // 控制字（新 REG_CONTROL 布局）：reg7 = aFmt[3:0] | bFmt[7:4] | cFmt[11:8] | rnd[14:12]（3-bit，#120 C2）。
    //   【枚举重排适配】aFmt=MXE4M3(新 3)、bFmt=MXE5M2(新 4)——旧值 5/6 在新枚举下
    //   为 MXE2M1/INT32，isMx XOR 触发 errCode 3。cFmt=outFmt3=MXE4M3(litValue=3)，
    //   rnd=RNE=0。合成 reg7 = 3|(4<<4)|(3<<8)|(0<<12) = 0x343。
    // reg6 = loadWMode[28] | cMxBlockSize[27:26] | bMxBlockSize[25:24] | aMxBlockSize[23:22]：
    //   loadWMode=1（MX 下 B 必须转置读取，否则 errCode 4）；cMxBlockSize=blkSel=1(M16=16，
    //   =totalN，不越 errCode 2 的 > 判据）；aMxBlockSize=bMxBlockSize=2(M32=32，恰等于
    //   aKLoadNum/bKLoadNum=totalN*2=32，过 errCode 0/1 的 > 判据)。
    val controlWord =
      (3L) | (4L << 4) |
      ((outFmt3.litValue.toLong & 0xF) << 8) | ((rnd.toLong & 0x7) << 12)   // 【#120 C2】3-bit rnd
    val controlWord6 =
      (1L << 28) | ((blkSel.toLong & 0x3) << 26) | ((2L & 0x3) << 24) | ((2L & 0x3) << 22)
    require(controlWord == 0x343L, s"reg7 控制字自检失败: 0x${controlWord.toHexString}")

    println(s"[$tag] Starting: M=$M N=$N K=$K totalN=$totalN kBlocks=$kBlocks blk=$blk ctrl=0x${controlWord.toHexString}")
    println(s"[$tag] beats: A=$dmaATotalBeats B=$dmaBTotalBeats sA=$scaleATotalBeats sB=$scaleBTotalBeats " +
            s"C=${expCAddrSeq.size} CSCALE=${expCScaleAddrSeq.size}")

    // 【任务102】失败路径也 dump 波形（#94 范式）：try 包 simulate，copyTrace 移入
    // finally——超时/断言失败异常上抛时同样转存，成功路径转存时机（块退出后）
    // 与旧代码完全一致。
    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaBBeatsSent = 0
      var scaleABeatsSent = 0
      var scaleBBeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaCScaleBeats = 0

      // ===== Reset（7 通道：A/B/C + scaleA/scaleB/cScale）=====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
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

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // ===== 配置寄存器（双 MX 异构 + 量化输出；0x09/0x0A=scaleA/B 基址，0x0B=scaleC 基址）=====
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, controlWord6, controlWord) // data0=reg6: loadWMode=1 + cMx/aMx/bMx block sizes；data1=reg7: fmt/rnd
      sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SCALE_A_BASE, SCALE_B_BASE)
      sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

      // ===== EXECUTE =====
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // 【任务101】EXECUTE 立即响应语义：完成判定 = STATUS 轮询（statusPollStep）
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      val maxCycles = if (cScaleBackpressure) 24000 else 16000

      while (!done && cycle < maxCycles) {

        // --- DMA-A：req.valid 拍逐拍地址硬对账 + 按地址内存保真供数（同拍 lockstep）---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeatByAddr(cmdAddr).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-B：req.valid 拍逐拍地址硬对账 + 按地址内存保真供数 ---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBBeatByAddr(cmdAddr).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B)
        }

        // --- DMA-scaleA：req.valid 拍逐拍地址硬对账 + 按地址供 8-bit scale ---
        if (scaleABeatsSent < scaleATotalBeats) {
          if (dut.io.dmaScaleA.get.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-scaleA beat #$scaleABeatsSent addr mismatch: ") {
              cmdAddr should be(expScaleAAddrSeq(scaleABeatsSent))
            }
            withClue(s"[$tag] DMA-scaleA beat #$scaleABeatsSent addr out of tensor bounds: ") {
              (cmdAddr >= SCALE_A_BASE && cmdAddr < SCALE_A_BASE + scaleATensorBytes) should be(true)
            }
            dut.io.dmaScaleA.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(genScaleABeatByAddr(cmdAddr).U)
            dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
            scaleABeatsSent += 1
          } else {
            dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
            dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
          dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
        }

        // --- DMA-scaleB：req.valid 拍逐拍地址硬对账 + 按地址供 8-bit 单字节（【任务109】）---
        if (scaleBBeatsSent < scaleBTotalBeats) {
          if (dut.io.dmaScaleB.get.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong
            withClue(s"[$tag] DMA-scaleB beat #$scaleBBeatsSent addr mismatch: ") {
              cmdAddr should be(expScaleBAddrSeq(scaleBBeatsSent))
            }
            dut.io.dmaScaleB.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(genScaleBBeatByAddr(cmdAddr).U)
            dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
            scaleBBeatsSent += 1
          } else {
            dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
            dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
          dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C 数据通道：逐拍地址硬对账 + 量化内容硬断言 ---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
            reqAddr should be(expCAddrSeq(dmaCStoreBeats))
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          val expData = expCDataBeat(dmaCStoreBeats)
          withClue(s"[$tag] DMA-C store beat #$dmaCStoreBeats data mismatch: ") {
            writeData should be(expData)
          }
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-CSCALE 通道：逐拍地址硬对账 + scale 字节逐位硬断言 ---
        // W-Adam2：背压模式下每 3 拍关 1 拍 req.ready，验证不丢 scale/不错位。
        // 仅在 fire（valid && ready）时计数/校验——背压拍硬件重试同一地址，
        // 若按 valid 计数则计数器超前导致地址不匹配。
        val cScaleReady = !cScaleBackpressure || (cycle % 3 != 0)
        dut.io.dmaCScale.get.req.ready.poke(cScaleReady.B)
        if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean && cScaleReady) {
          val reqAddr = dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong
          withClue(s"[$tag] DMA-CSCALE beat #$dmaCScaleBeats addr mismatch: ") {
            reqAddr should be(expCScaleAddrSeq(dmaCScaleBeats))
          }
          val writeData = dut.io.dmaCScale.get.req.payload.data.peek().litValue
          val expData = expCScaleDataBeat(dmaCScaleBeats)
          withClue(s"[$tag] DMA-CSCALE beat #$dmaCScaleBeats data mismatch: ") {
            writeData should be(expData)
          }
          scaleWriteLog += ((reqAddr, writeData))
          dmaCScaleBeats += 1
          dut.io.dmaCScale.get.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        }

        // --- 完成检查：STATUS 寄存器轮询（bit1=done 粘滞位、bit2=err）---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.done) {
            println(f"[$tag cycle=$cycle%5d] EXECUTE complete (STATUS.done=1, err=${st.err})! A=$dmaABeatsSent B=$dmaBBeatsSent " +
                    s"sA=$scaleABeatsSent sB=$scaleBBeatsSent C=$dmaCStoreBeats CSCALE=$dmaCScaleBeats")
            st.err should be(false)
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) println(s"[$tag] WARNING: timeout at $maxCycles cycles " +
        s"(A=$dmaABeatsSent B=$dmaBBeatsSent sA=$scaleABeatsSent sB=$scaleBBeatsSent " +
        s"C=$dmaCStoreBeats CSCALE=$dmaCScaleBeats)")

      done should be(true)
      // 拍数硬断言（六通道）
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      scaleABeatsSent should be(scaleATotalBeats)
      scaleBBeatsSent should be(scaleBTotalBeats)
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(nTiles * M * beatsPerRow)
      dmaCScaleBeats should be(expCScaleAddrSeq.size)
      dmaCScaleBeats should be(nTiles * M)

      // ===== 容差交叉校验（第二视角）：量化输出反量化后与 golden 双精度参考对比；
      // 冲刷界豁免沿用 runQuantGemm 范式（|ref| < 2^(scale-127) 冲刷 ±0 不判失）=====
      val expW = 4; val mantW = 3; val bias = 7   // E4M3
      def dequant(code: Int, scale: Int): Double = {
        val sg = (code >> (expW + mantW)) & 1
        val mag = code & ((1 << (expW + mantW)) - 1)
        val e = mag >> mantW
        val mm = mag & ((1 << mantW) - 1)
        if (e == 0 || scale == 0) 0.0
        else {
          val v = (1.0 + mm.toDouble / (1 << mantW)) * math.pow(2, e - bias) * math.pow(2, scale - 127)
          if (sg == 1) -v else v
        }
      }
      var mismatchCnt = 0
      var maxRelErr = 0.0
      var beatIdx = 0
      for ((_, data) <- cWriteLog) {
        val nt = beatIdx / (M * beatsPerRow)
        val inBlock = beatIdx % (M * beatsPerRow)
        val row = inBlock / beatsPerRow
        val beatInRow = inBlock % beatsPerRow
        val scaleBeat = scaleWriteLog(nt * M + row)._2
        for (i <- 0 until elemsPerBeat) {
          val globalCol = nt * totalN + beatInRow * elemsPerBeat + i
          // 【任务90 收敛】密集布局列归属边界：拍内超出 tile 宽（totalN）的高半区是零填充，
          // 不得计入列空间（旧实现仅判 globalCol<N，256-bit 下把 padding 误读为列 16..31）
          if (globalCol < N && globalCol < (nt + 1) * totalN) {
            val code = ((data >> (8 * i)) & 0xFF).toInt
            val localCol = globalCol - nt * totalN
            val grp = localCol / 8   // scale 字节按 tile 内局部列分组（低块号低位）
            val scale = ((scaleBeat >> (8 * grp)) & 0xFF).toInt
            val hw = dequant(code, scale)
            val ref = refSum(row, globalCol)
            val absErr = math.abs(hw - ref)
            val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
            val flushBound = math.pow(2, scale - 127)
            if (relErr > 0.5 && absErr > 1e-3 && absErr > flushBound + 1e-9) {
              if (mismatchCnt < 5)
                println(f"[$tag] DEQUANT MISMATCH C($row,$globalCol): hw=$hw%.6g ref=$ref%.6g code=0x$code%X scale=0x$scale%02X")
              mismatchCnt += 1
            }
            if (relErr > maxRelErr) maxRelErr = relErr
          }
        }
        beatIdx += 1
      }
      println(f"[$tag] Dequant cross-check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
      mismatchCnt should be(0)
    }
    } finally {
      // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
      // FST 须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 【任务102】置于 finally：超时/断言失败异常上抛后同样在此转存（Verilator
      // 已对失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供回放）。
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCaseQuantSpec/HiveCore-Quant-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        tag
      )
    }
  }

  // ==========================================================================
  // 负向运行器：显式构造违约配置 + EXECUTE，轮询 STATUS(0x08) 的 err 与 errCode。
  //   【B2/B4 联调】config-err 现由 CalcConfig 校验（errCode 0..6）拦截 startOut：
  //   err 时 io.startOut 永不拉高 → executor 不启动、done 不发、dmaC/dmaCScale 零请求；
  //   顶层 errFlag 直连（B2）把 calcConfig.io.err 传播到 STATUS.err（bit2），
  //   errCode 落 STATUS bits[6:3]。断言不止 err 置位，还硬断言 errCode==expErrCode。
  //   入参显式给出 aFmt/bFmt/cFmt/cMxBlk/loadWMode（新 reg7/reg6 位域），
  //   由调用点精确构造目标违约项（errCode 2 = cMxBlockSize>totalN 且 isMx(cFmt)；
  //   errCode 6 = cFmt==MXE2M1 且 N 为奇）。
  // ==========================================================================
  private def runQuantErrCase(aFmt: Int, bFmt: Int, cFmt: Int, cMxBlk: Int,
                              loadWMode: Int, expErrCode: Int,
                              M: Int, N: Int, K: Int, caseName: String): Unit = {
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,
      aW = 16, bW = 16, cW = 40,
      aBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    simulate(new HiveCore(cfg)) { dut =>
      // ---- Reset + DMA 端口拉到安全默认（本用例不喂数据，err 应在 execute 拍触发）----
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.req.ready.poke(true.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // 控制字（新位域）：reg7 = aFmt[3:0] | bFmt[7:4] | cFmt[11:8]；
      // reg6 = loadWMode[28] | cMxBlockSize[27:26]。违约项由入参精确构造，
      // 目标 errCode（2 或 6）由 CalcConfig 校验并在 STATUS bits[6:3] 回报。
      val controlWord  = (aFmt.toLong & 0xF) | ((bFmt.toLong & 0xF) << 4) | ((cFmt.toLong & 0xF) << 8)
      val controlWord6 = ((loadWMode.toLong & 0x1) << 28) | ((cMxBlk.toLong & 0x3) << 26)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, controlWord6, controlWord)
      sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

      // EXECUTE（违约配置：executor 在 sIDLE 拍置 errState 回 sIDLE，不发 done）
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ---- 轮询 STATUS(0x08)：期望 err 置位、done 全程不发、无数据拍 ----
      // 【任务101】EXECUTE 立即响应语义：resp bundle 已无 done 字段，完成判定
      // 统一走 STATUS 轮询（io.status 端口 + 尾部 REG_READ 0x08 对账）
      var errSeen = false
      var doneSeen = false
      var dmaCReqSeen = 0
      var dmaCScaleReqSeen = 0
      for (_ <- 0 until 200) {
        if (dut.io.status.done.peek().litToBoolean) doneSeen = true
        if (dut.io.status.err.peek().litToBoolean) errSeen = true
        if (dut.io.dmaC.req.valid.peek().litToBoolean) dmaCReqSeen += 1
        if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) dmaCScaleReqSeen += 1
        dut.clock.step()
      }

      // REG_READ 0x08 → statusReg bit[2]=err（软件轮询路径对账）
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.REG_READ)
      dut.io.cmd.payload.reg0Addr.poke(0x08.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      var statusVal = BigInt(0)
      var got = false
      for (_ <- 0 until 8 if !got) {
        if (dut.io.resp.valid.peek().litToBoolean) {
          statusVal = dut.io.resp.payload.data.peek().litValue
          got = true
        }
        dut.clock.step()
      }
      val errBit   = (statusVal >> 2) & 1
      val doneBit  = (statusVal >> 1) & 1
      val errCodeR = (statusVal >> 3) & 0xF          // STATUS bits[6:3] = CalcConfig errCode
      println(f"[$caseName] status=0x$statusVal%08X errBit=$errBit doneBit=$doneBit errCode=$errCodeR " +
              s"errSeen=$errSeen dmaCReq=$dmaCReqSeen dmaCScaleReq=$dmaCScaleReqSeen")

      withClue(s"[$caseName] STATUS.err should be set: ") { errSeen should be(true) }
      withClue(s"[$caseName] STATUS(0x08) bit[2] err should be 1: ") { errBit should be(BigInt(1)) }
      withClue(s"[$caseName] STATUS(0x08) bits[6:3] errCode should be $expErrCode: ") {
        errCodeR should be(BigInt(expErrCode))
      }
      withClue(s"[$caseName] done must NOT be asserted (config-err returns to sIDLE without done): ") {
        doneSeen should be(false)
        doneBit should be(BigInt(0))
      }
      withClue(s"[$caseName] no data beats must be produced on dmaC/dmaCScale: ") {
        dmaCReqSeen should be(0)
        dmaCScaleReqSeen should be(0)
      }

      // ===== 合法再 EXECUTE 清 err 验证（FP 族合法：aFmt=bFmt=FP16、cFmt=FP32）=====
      // 不喂数据，运行停在等 B 供数，不影响 err 清除验证。依赖 B3 的 sDONE→sC0 回退
      // 与 B2 的 startPulse 清 errFlag：再 EXECUTE 时 CalcConfig 以合法配置重判 → 无 err。
      // 合法控制字 0x11 = aFmt=FP16(1) | bFmt=FP16(1)<<4 | cFmt=FP32(0)：非 MX（errCode 0/1/2/4
      // 均不触发）、非 FP32 输入（errCode 5 不触发）、cFmt≠MXE2M1（errCode 6 不触发）。
      // reg6 残留的 cMxBlockSize 在 cFmt=FP32（非 MX）下被 errCode 2 的 isMx(cFmt) 门屏蔽，无害。
      val legalCtrl = 0x11L   // aFmt=FP16 | bFmt=FP16<<4 | cFmt=FP32（合法，无族交叉冲突）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x01, 0x07, 16L, legalCtrl) // 修正 N=16（偶）+ 合法控制字
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      var errAfterReexec = false
      for (_ <- 0 until 100) {
        if (dut.io.status.err.peek().litToBoolean) errAfterReexec = true
        dut.clock.step()
      }
      withClue(s"[$caseName] STATUS.err should be cleared after a legal re-EXECUTE: ") {
        errAfterReexec should be(false)
      }
    }
  }

  // ==========================================================================
  // C1 回归运行器：验证「量化 config-err → 转非量化 fp32 运行」可恢复。
  //   根因（C1）：cDmaScale 在量化 config-err 后停留 sTRANSFER、busy 恒高，
  //   顶层 executePulse & cQuantRun 永不拉高 → cDmaScale 恢复路径封死 →
  //   dmaCBusy OR 门锁死后续所有非量化运行。
  //   修复验证：HiveCoreDmaScaleWr.abort（executePulse & !cQuantRun）强制
  //   cDmaScale 回 sIDLE；本用例断言 DMA-B req 出现（系统进入 sLOAD_B），
  //   证明非量化运行未被 cDmaScale.busy 死锁。
  // ==========================================================================
  private def runQuantErrNonQuantRecovery(): Unit = {
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,
      aW = 16, bW = 16, cW = 40,
      aBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 256
    )
    val tag = "C1-Recovery"

    simulate(new HiveCore(cfg)) { dut =>
      // ---- Reset ----
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.req.ready.poke(true.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, reg0Addr: Int, reg1Addr: Int,
                  data0: Long, data1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(reg0Addr.U)
        dut.io.cmd.payload.reg1Addr.poke(reg1Addr.U)
        dut.io.cmd.payload.data0.poke(data0.U)
        dut.io.cmd.payload.data1.poke(data1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      // ===== Phase 1: 量化违约配置 → config-err（errCode 2）=====
      // cFmt=MXE4M3(新 3, isMx) + reg6 cMxBlockSize=2(M32=32) > totalN=16 → CalcConfig errCode 2；
      // aFmt=bFmt=FP16 非 MX 避免误触 errCode 3/4。err 经 B2 直连传播到 STATUS.err。
      val errCtrl  = (1L) | (1L << 4) | (3L << 8)   // reg7: aFmt=FP16 | bFmt=FP16<<4 | cFmt=MXE4M3(3)
      val errCtrl6 = (2L & 0x3) << 26               // reg6: cMxBlockSize=M32=32（>totalN=16，违约）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, 4, 16)       // M=4 N=16
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, 32, A_BASE)  // K=32
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, errCtrl6, errCtrl)
      sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      var errSeen = false
      for (_ <- 0 until 200) {
        if (dut.io.status.err.peek().litToBoolean) errSeen = true
        dut.clock.step()
      }
      withClue(s"[$tag] Phase1: config-err must be set (blk32>totalN16): ") {
        errSeen should be(true)
      }
      println(s"[$tag] Phase1 OK: config-err detected")

      // ===== Phase 2: 切换非量化 fp32 配置（aFmt=bFmt=FP16、cFmt=FP32）→ EXECUTE =====
      // 关键：C1 修复前此步确定性死锁（cDmaScale.busy 恒高 → dmaCBusy OR 门封死）。
      // data0=0L 同时清 Phase1 残留的 reg6 cMxBlockSize；依赖 B3 sDONE→sC0 回退重执行。
      val fp32Ctrl = 0x11L  // reg7: aFmt=FP16(1) | bFmt=FP16(1)<<4 | cFmt=FP32(0)，非量化合法
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, fp32Ctrl)

      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ===== Phase 3: 验证不死锁 =====
      // DMA-B req.valid 出现 → 系统进入 sLOAD_B（executor 可推进）
      // err 必须已清除（executePulse 拍 abort cDmaScale → busy 落）
      var dmaBReqSeen = false
      var errAfter = false
      for (_ <- 0 until 500) {
        if (dut.io.dmaB.req.valid.peek().litToBoolean) dmaBReqSeen = true
        if (dut.io.status.err.peek().litToBoolean) errAfter = true
        dut.clock.step()
      }

      println(s"[$tag] Phase2/3: dmaBReqSeen=$dmaBReqSeen errAfter=$errAfter")
      withClue(s"[$tag] err should be cleared after non-quant re-EXECUTE: ") {
        errAfter should be(false)
      }
      withClue(s"[$tag] DMA-B req must appear (system not deadlocked by cDmaScale.busy): ") {
        dmaBReqSeen should be(true)
      }
    }
  }
}
