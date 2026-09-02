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
  *     期望地址序列含 cDma sNEXT_N 三角累积跳转（既有遗留语义，测试按同公式推演）；
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

class HiveCoreSimCaseQuantSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

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

  // --- reserved outFmt 回落 fp32（011/111）：验证非量化编码走 fp32 密集直出 ---
  it should "actively fall back to fp32 with outFmt 111 reserved" in {
    runQuantFallbackFp32(outFmt3 = 7,
      waveName = "quant_outfmt111_fallback_fp32.fst",
      testMangle = "should-actively-fall-back-to-fp32-with-outFmt-111-reserved")
  }
  it should "actively fall back to fp32 with outFmt 011 reserved" in {
    runQuantFallbackFp32(outFmt3 = 3,
      waveName = "quant_outfmt011_fallback_fp32.fst",
      testMangle = "should-actively-fall-back-to-fp32-with-outFmt-011-reserved")
  }

  // --- 负向 config-err：blk > totalN 与 mxfp4 奇数 N ---
  it should "actively reject quant config blk32 over totalN" in {
    runQuantErrCase(outFmt3 = 4, blkSel = 2, M = 4, N = 16, K = 32,
      caseName = "Quant-ERR-blk32")
  }
  it should "actively reject quant config mxfp4 odd N" in {
    runQuantErrCase(outFmt3 = 6, blkSel = 0, M = 4, N = 17, K = 32,
      caseName = "Quant-ERR-mxfp4-oddN")
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
  // 正向运行器（历史代码，仅被上方注释块引用）：克隆 runOutFmtGemm 结构 + dmaCScale
  // 双通道。参数 cDmaExtW：2^N ∈ [totalN*8, totalN*32]，当前全部用 256（P0 起唯一
  // 合法值族）。旧 0=满宽 / 320 缩位哨兵已废止。恢复启用需整体重写本函数。
  //   outFmt3: 4=E4M3 5=E5M2 6=E2M1；blkSel: 0=8 1=16 2=32 3=64；
  //   rnd: 0=RNE 1=RTZ 2=RDN 3=RUP 4=RMM
  // ===========================================================================
  private def runQuantGemm(outFmt3: Int, blkSel: Int, rnd: Int, cDmaExtW: Int,
                           M: Int, N: Int, K: Int,
                           waveName: String, testMangle: String): Unit = {
    require(outFmt3 >= 4 && outFmt3 <= 6, s"量化用例族仅支持 outFmt 4/5/6，当前=$outFmt3")
    val blk = 8 << blkSel
    // blk 须 ≤ totalN（否则 execute 拍 config-err）；N 不要求被 blk 整除——
    // 非整除时 padding 列不进 amax（nEff 掩码），正是边界覆盖目标
    val isFp4 = outFmt3 == 6
    val tag = s"Quant-fmt$outFmt3-blk$blk-rnd$rnd-E${if (cDmaExtW == 0) "FULL" else cDmaExtW.toString}-M$M-N$N"

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

    // --- DMA-C 数据通道期望地址：量化布局 + cDma sNEXT_N 三角累积跳转闭式
    //（既有遗留语义，测试按同公式推演；与 runOutFmtGemm 同构）---
    val expCAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until nTiles; m <- 0 until M; b <- 0 until beatsPerRow)
      expCAddrSeq += C_BASE + nt.toLong * (M - 1) * cRowOff +
        nt.toLong * (nt + 1) / 2 * cColOff + m * cRowOff + b * beatStep
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

    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaAReqIssued = 0   // req 拍独立计数：RdOnly req 领先 rsp，软对账打印须按 req 序号
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

      // ===== 配置寄存器（FP16 输入 + rnd + outFmt(3-bit)<<11 + qBlockSize<<14；
      // cScaleAddr 写 0x0B）=====
      val controlWord = ((rnd.toLong & 0x7) << 2) |
        ((outFmt3.toLong & 0x7) << 11) | ((blkSel.toLong & 0x3) << 14)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, controlWord)
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

      var done = false
      var cycle = 0
      val maxCycles = 12000

      while (!done && cycle < maxCycles) {

        // --- DMA-A（按序供数，地址软对账打印）---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            if (dmaAReqIssued < dmaATotalBeats) {
              val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
              if (cmdAddr != expAAddrSeq(dmaAReqIssued) && dmaAReqIssued < 8)
                println(f"[$tag cycle=$cycle%6d] DMA-A beat #$dmaAReqIssued addr MISMATCH: hw=0x$cmdAddr%X exp=0x${expAAddrSeq(dmaAReqIssued)}%X")
              dmaAReqIssued += 1
            }
          }
          dut.io.dmaA.rsp.valid.poke(true.B)
          dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
          if (dut.io.dmaA.rsp.ready.peek().litToBoolean) dmaABeatsSent += 1
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

        // --- 完成检查 ---
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          println(f"[$tag cycle=$cycle%5d] EXECUTE complete! A=$dmaABeatsSent B=$dmaBBeatsSent " +
                  s"C=$dmaCStoreBeats CSCALE=$dmaCScaleBeats")
          dut.io.resp.payload.err.peek().litToBoolean should be(false)
          done = true
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)

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
        case 4 => (4, 3, 7)
        case 5 => (5, 2, 15)
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

    // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
    HiveSimCommon.copyTrace(
      s"build/chiselsim/HiveCoreSimCaseQuantSpec/HiveCore-Quant-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
      s"$simDir/$waveName",
      tag
    )
  }

  // ==========================================================================
  // reserved outFmt 回落 fp32 定向运行器（评审补齐）：outFmt=011/111 必须完整降级 000。
  //   量化判据统一为枚举（outFmtEff∈{100,101,110}，三处同一判据源：
  //   HiveCoreDmaWrOnly.isQuant / HiveCore.cQuantRun / HiveCoreExecutor.quantActive）后，
  //   reserved 编码全链路回落：数据按 fp32 legacy 布局写出（满宽 40-bit 槽、5B/元素）、
  //   地址按 5B 步进、cScaleDma 不启动（dmaCScale 零请求拍）、量化校验不误触发（无 err）。
  //   与 runQuantGemm 同构：同几何（M=32 N=32 K=32）、同 crafted 矩阵、同期望链（仅末级
  //   改为 fp32 规格化直出），双通道逐拍地址硬对账 + 数据硬断言；qBlockSize 照常写入，
  //   验证非量化运行下该位被忽略（不启动 scale 通道、不触发校验）。
  // ==========================================================================
  private def runQuantFallbackFp32(outFmt3: Int, waveName: String, testMangle: String): Unit = {
    require(outFmt3 == 3 || outFmt3 == 7, s"回落用例仅支持 reserved 编码 3/7，当前=$outFmt3")
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
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(0, totalN, cfg.cDmaExtW)
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

    // --- DMA-C 期望地址（密集 4B/元素 + 256-bit 多拍 + sNEXT_N 三角累积闭式）---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, 0, cfg.cDmaExtW)

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

    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaAReqIssued = 0
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

      // ===== 配置寄存器：FP16 输入 + reserved outFmt<<11 + qBlockSize<<14（照常写入，
      // 验证非量化下被忽略）；cScaleAddr 也写，验证无 scale 请求 =====
      val controlWord = ((outFmt3.toLong & 0x7) << 11) | ((blkSel.toLong & 0x3) << 14)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, controlWord)
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

      var done = false
      var cycle = 0
      val maxCycles = 12000

      while (!done && cycle < maxCycles) {

        // --- DMA-A（按序供数，地址软对账打印）---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            if (dmaAReqIssued < dmaATotalBeats) {
              val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
              if (cmdAddr != expAAddrSeq(dmaAReqIssued) && dmaAReqIssued < 8)
                println(f"[$tag cycle=$cycle%6d] DMA-A beat #$dmaAReqIssued addr MISMATCH: hw=0x$cmdAddr%X exp=0x${expAAddrSeq(dmaAReqIssued)}%X")
              dmaAReqIssued += 1
            }
          }
          dut.io.dmaA.rsp.valid.poke(true.B)
          dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
          if (dut.io.dmaA.rsp.ready.peek().litToBoolean) dmaABeatsSent += 1
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

        // --- 完成检查 ---
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          println(f"[$tag cycle=$cycle%5d] EXECUTE complete! A=$dmaABeatsSent B=$dmaBBeatsSent " +
                  s"C=$dmaCStoreBeats CSCALE_REQ=$dmaCScaleReqSeen")
          dut.io.resp.payload.err.peek().litToBoolean should be(false)
          done = true
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)

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

    // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
    HiveSimCommon.copyTrace(
      s"build/chiselsim/HiveCoreSimCaseQuantSpec/HiveCore-Quant-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
      s"$simDir/$waveName",
      tag
    )
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
    val outFmt3 = 4   // 量化输出 mxfp8 E4M3
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
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(4, totalN, cfg.cDmaExtW)
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

    // --- DMA-scaleB 期望地址（两级遍历：nt 外 × kb 内，addr = base + kb×N + nt×totalN，
    // 与 HiveCoreDmaScaleRd isScaleB=true 取址公式逐拍同源）与按地址反解码供数 ---
    val expScaleBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until nTilesB; kb <- 0 until kBlocks)
      expScaleBAddrSeq += SCALE_B_BASE + kb.toLong * N + nt.toLong * totalN
    val scaleBTotalBeats = expScaleBAddrSeq.size
    def genScaleBBeatByAddr(addr: Long): BigInt = {
      val off = addr - SCALE_B_BASE
      val kb = (off / N).toInt
      val nt = ((off % N) / totalN).toInt
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val n = nt * totalN + i
        val value = if (n < N) scaleB(kb)(n) else 0
        data = data | (BigInt(value & 0xFF) << (8 * i))
      }
      data
    }

    // --- DMA-C 数据通道期望地址（P3 迁移：密集布局公共推演源；量化布局 mxfp8：
    // 行步进 = N×1B、列步进 = totalN×1B、拍内步进 32B；三角累积闭式 + 拍内细分）---
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, outFmt = 4, cfg.cDmaExtW)
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
    def expCDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / (M * beatsPerRow)
      val inBlock = beatIdx % (M * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      val (codes, _) = expectedRow(nt, row)
      // 密集行（低列号低位、padding 列零）→ 按 cDmaExtW 右移取拍段，
      // 与 WrOnly 发拍表达式逐字同源（256 下 1 拍/行：16 码占低 128 位、高位零）
      val denseRow = HiveSimCommon.elemsToDenseRow(codes.map(_.toLong), outFmt = 4, totalN = totalN)
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

    // 控制字：aFmt=MXE4M3(0b100)=bit6|bits(1,0)=00；bFmt=MXE5M2(0b101)=bit7|bits(9,8)=01；
    // loadWMode=bit5（MX 下 B 必须转置读取）；mixFmtEn=bit10（异构双 MX）；
    // outFmt3<<11；blkSel<<14；rnd=RNE=0 占 bits(4,2)。合成 = 0x65E0
    val controlWord =
      (1L << 6) | (1L << 7) | (1L << 8) | (1L << 5) | (1L << 10) |
      ((outFmt3.toLong & 0x7) << 11) | ((blkSel.toLong & 0x3) << 14)
    require(controlWord == 0x65E0L, s"控制字自检失败: 0x${controlWord.toHexString}")

    println(s"[$tag] Starting: M=$M N=$N K=$K totalN=$totalN kBlocks=$kBlocks blk=$blk ctrl=0x${controlWord.toHexString}")
    println(s"[$tag] beats: A=$dmaATotalBeats B=$dmaBTotalBeats sA=$scaleATotalBeats sB=$scaleBTotalBeats " +
            s"C=${expCAddrSeq.size} CSCALE=${expCScaleAddrSeq.size}")

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
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, controlWord)
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

      var done = false
      var cycle = 0
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

        // --- DMA-scaleB：req.valid 拍逐拍地址硬对账 + 按地址供 128-bit 向量 ---
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

        // --- 完成检查 ---
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          println(f"[$tag cycle=$cycle%5d] EXECUTE complete! A=$dmaABeatsSent B=$dmaBBeatsSent " +
                  s"sA=$scaleABeatsSent sB=$scaleBBeatsSent C=$dmaCStoreBeats CSCALE=$dmaCScaleBeats")
          dut.io.resp.payload.err.peek().litToBoolean should be(false)
          done = true
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)

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

    // --- 复制 FST 波形（源路径段由 testMangle 逐字锚定 it should 用例名）---
    HiveSimCommon.copyTrace(
      s"build/chiselsim/HiveCoreSimCaseQuantSpec/HiveCore-Quant-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
      s"$simDir/$waveName",
      tag
    )
  }

  // ==========================================================================
  // 负向运行器：写入违约量化配置 + EXECUTE，轮询 STATUS(0x08).err 位。
  //   config-err 在 executor sIDLE 拍直接置 errState 并回 sIDLE（不进入计算、
  //   不发 done、不产生任何数据拍）；顶层 hasMx-gated errFlag 直连现成。
  //   断言：err 置位；done 全程不发；dmaC/dmaCScale 无任何请求拍。
  // ==========================================================================
  private def runQuantErrCase(outFmt3: Int, blkSel: Int, M: Int, N: Int, K: Int,
                              caseName: String): Unit = {
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

      // 控制字：FP16 输入 + 量化 outFmt + blkSel（违约项由参数给出）
      val controlWord = ((outFmt3.toLong & 0x7) << 11) | ((blkSel.toLong & 0x3) << 14)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, controlWord)
      sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

      // EXECUTE（违约配置：executor 在 sIDLE 拍置 errState 回 sIDLE，不发 done）
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ---- 轮询 STATUS(0x08)：期望 err 置位、done 全程不发、无数据拍 ----
      var errSeen = false
      var doneSeen = false
      var respDoneSeen = false
      var dmaCReqSeen = 0
      var dmaCScaleReqSeen = 0
      for (_ <- 0 until 200) {
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean)
          respDoneSeen = true
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
      val errBit  = (statusVal >> 2) & 1
      val doneBit = (statusVal >> 1) & 1
      println(f"[$caseName] status=0x$statusVal%08X errBit=$errBit doneBit=$doneBit " +
              s"errSeen=$errSeen dmaCReq=$dmaCReqSeen dmaCScaleReq=$dmaCScaleReqSeen")

      withClue(s"[$caseName] STATUS.err should be set: ") { errSeen should be(true) }
      withClue(s"[$caseName] STATUS(0x08) bit[2] err should be 1: ") { errBit should be(BigInt(1)) }
      withClue(s"[$caseName] done must NOT be asserted (config-err returns to sIDLE without done): ") {
        respDoneSeen should be(false)
        doneSeen should be(false)
        doneBit should be(BigInt(0))
      }
      withClue(s"[$caseName] no data beats must be produced on dmaC/dmaCScale: ") {
        dmaCReqSeen should be(0)
        dmaCScaleReqSeen should be(0)
      }

      // ===== 合法再 EXECUTE 清 err 验证（FP 族合法：outFmt=0 fp32）=====
      // 不喂数据，运行停在等 B 供数，不影响 err 清除验证。
      // 注意：输入为 FP16（非 MX），族交叉校验 fpFmtOk={0,1,2,3,7}——outFmt=4(E4M3)
      // 属 MX 族编码，会被拦截再次 config-err。此处只需证明 err 可清除，用 fp32 即可。
      val legalCtrl = 0L   // outFmt=0 fp32 + blk=8（FP 族合法编码，无族交叉冲突）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x01, 0x07, 16L, legalCtrl) // 修正 N=16（偶）+ fp32 控制字
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

      // ===== Phase 1: 量化违约配置 → config-err =====
      // blkSel=2 → blk=32 > totalN=16 → execute 拍 config-err
      val errCtrl = ((4L & 0x7) << 11) | ((2L & 0x3) << 14)  // E4M3 + blk32（违约）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, 4, 16)       // M=4 N=16
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, 32, A_BASE)  // K=32
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, errCtrl)
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

      // ===== Phase 2: 切换非量化 fp32 配置（outFmt=0）→ EXECUTE =====
      // 关键：C1 修复前此步确定性死锁（cDmaScale.busy 恒高 → dmaCBusy OR 门封死）
      val fp32Ctrl = 0L  // outFmt=0000（fp32 直出），非量化
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
