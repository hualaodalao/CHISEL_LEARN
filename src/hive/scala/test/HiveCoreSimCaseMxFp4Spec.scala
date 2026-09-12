/** HiveCoreSimCaseMxFp4Spec — Phase 4「MXFP4/E2M1 端到端打通里程碑」仿真用例。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 本文件锁死的是什么
  * ═══════════════════════════════════════════════════════════════════════════
  * 严格仿 HiveCoreSimCaseMxSpec（MXFP8 v2 SIMD）的结构与驱动方式，把整条 GEMM 数据流
  * 从 MXFP8 dual-lane 迁移到 MXFP4(E2M1) quad-lane，端到端复用 Phase 0-3 已就位的 fp4
  * 能力（解码 Fp32.mxE2M1ToFp32 / 计算 HiveWorkUnit quad-lane / 几何 bits 口径 /
  * 金锚 HiveDebugModel.decodeInElem），验证「fp4×fp4 GEMM 硬件产出 == 张量语义金锚」。
  *
  * 【fp4 quad-lane 打包布局】16-bit lane 打包 4 个 4-bit E2M1 nibble：
  *   io.a(3,0)=k+0、io.a(7,4)=k+1、io.a(11,8)=k+2、io.a(15,12)=k+3（低 sublane=低 k，
  *   与硬件 4 级 HiveMacFpMx「k 升序、低 sublane 先加」左结合级联逐拍消费对齐）。
  *   每拍每波前推进 16 lane × 4 = 64 个 K 元素 = 恰好一个 64-K scale 块 = 单 pass，
  *   故 kTile 数 = K/64，errCode7 强制 fp4 mxBlockSize=64（否则运行期拒绝启动）。
  *   外部 A/B 存储半字节/元素（0.5B）：行步长 = K/2 字节，k 窗口步进 colTile = 32 字节。
  *
  * 【金锚纪律（禁 RTL 拟合）】C[i][j] = Σ_k decodeInElem(A[i][k]) × decodeInElem(B[k][j])，
  *   decodeInElem(MXE2M1,·) 走 Phase 3 输入解码域（±0.5 subnormal 一等公民、与硬件
  *   Fp32.mxE2M1ToFp32 逐位同源）。crafted 元素取 E2M1 精确域 {0,±0.5,±1,±1.5,±2,±3,±4,±6}
  *   ×2^(scale-127)：每元素 ≤2 有效位 ⟹ 每乘积 ≤4 有效位（恰被 HiveMacFpMx sigW=4 窄化
  *   乘法器与 31-bit lazy 尾数无损表示），crafted scale 使指数展布 << 27 ⟹ 级联对齐不丢位
  *   ⟹ Double 求和即硬件精确结果、与累加顺序无关。每用例前置 require 自证金锚和在 fp32 域
  *   精确（toFloat 往返无损），排除偶然舍入把测试退化成拟合。
  *
  * 【scaleB 内存布局（多块正确性关键）】硬件 scaleB DMA（HiveCoreDmaRdOnly isA=false,
  *   loadWMode=1）逐 beat 地址 = base + n×kBlocks + kb（n=全局列、kb=kTile=scale 块号；
  *   推导：scaleBRowAddressOffset=K/blk=kBlocks 为 n 步进、scaleBColTileAddressOffset=1
  *   为 kb 步进、scaleBNTileJump=totalN×kBlocks）。故内存映像按【n-major】组织：
  *   byte(n×kBlocks + kb) = scaleB[kb][n]。单块（kBlocks=1）时退化为 off=n，与 MxSpec 的
  *   [kb][n] 解码逐值一致（MxSpec/QuantSpec 仅覆盖单块 K=blk，故二者等价从未分歧）；
  *   本 spec 的多 pass 用例（K=128 → kBlocks=2）首次覆盖多块，必须用 n-major 解码。
  *
  * 运行：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  *        sbt -J-Xmx6G "testOnly HiveCoreSimCaseMxFp4Spec"
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._
import java.lang.Float.floatToRawIntBits

class HiveCoreSimCaseMxFp4Spec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim with HiveStatusPollApi {

  // 【硬件侧门控已实施】HiveCvtOpMx inf/nan 断言受 consumeValid 消费有效门控，空闲/加载/
  // 排空拍随机垃圾不触发断言，故本 suite 用 ChiselSim 默认随机化（还原真实上电语义）。

  val A_BASE:       Long = 0x00000000L
  val B_BASE:       Long = 0x00100000L
  val C_BASE:       Long = 0x00200000L
  val SCALE_A_BASE: Long = 0x00300000L
  val SCALE_B_BASE: Long = 0x00400000L
  val SCALE_C_BASE: Long = 0x00500000L   // 量化输出（outFmt∈{4,5,6}）scale 写回基址

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/mxfp4"

  behavior of "HiveCore MXFP4 GEMM Simulation"

  // 【FINDING-B】svsim workspace 目录名 = ScalaTest 对 test 全名（"should " + 标题）的 mangle：
  //   把所有非 [a-zA-Z0-9._-] 字符替换为 '-'（空格/=/( /)/!// → '-'；'.'、'-'、字母数字保留，
  //   连续特殊字符逐个替换、不折叠）。运行期从 it should 标题派生，消除手写 testMangle 字面量耦合
  //   ——此前手写字面量对 '(' '!=' ')' '.' 处理有误（写成 -not- / 丢 '.'），致 copyTrace 源路径拼错、
  //   走 else 静默 NOTE 不 fail、第5个 .fst（errCode7 负向）从未落入 sim/mxfp4/。标题为单一真源。
  private def mangleOf(title: String): String = ("should " + title).replaceAll("[^a-zA-Z0-9._-]", "-")

  // ① 核心打通证据：单 pass（K=64=1 scale 块）fp4×fp4，fp32 直出，逐位金锚守护。
  val titleSinglePass = "run fp4 single-pass GEMM M=32 N=32 K=64 E2M1xE2M1 fp32-out"
  it should titleSinglePass in {
    runFp4Gemm(M = 32, N = 32, K = 64, outFmt = DataFormat.FP32, blkSel = 0, rnd = 0,
      waveName = "hivecore_mxfp4_single_pass.fst",
      testMangle = mangleOf(titleSinglePass))
  }

  // ② 多 pass 累加：K=128 → 2 kTile（2 scale 块），C buffer 部分和跨 pass 累加。
  val titleMultiPass = "run fp4 multi-pass GEMM M=32 N=32 K=128 E2M1xE2M1 fp32-out"
  it should titleMultiPass in {
    runFp4Gemm(M = 32, N = 32, K = 128, outFmt = DataFormat.FP32, blkSel = 0, rnd = 0,
      waveName = "hivecore_mxfp4_multi_pass.fst",
      testMangle = mangleOf(titleMultiPass))
  }

  // ③ 缩位（partial tile）：M=4（<totalN 部分 m）、N=24（2 n-tile，第二 tile 部分列补零）。
  val titleShrunk = "run fp4 shrunk GEMM M=4 N=24 K=64 E2M1xE2M1 fp32-out"
  it should titleShrunk in {
    runFp4Gemm(M = 4, N = 24, K = 64, outFmt = DataFormat.FP32, blkSel = 0, rnd = 0,
      waveName = "hivecore_mxfp4_shrunk.fst",
      testMangle = mangleOf(titleShrunk))
  }

  // ④ fp4 输入 × mxfp4(E2M1) 输出量化组合：outFmt=MXE2M1、量化块 blk16，
  //    dmaC 密集半字节码 + dmaCScale 块 E8M0 双通道逐拍硬对账 + 反量化容差交叉。
  val titleInOutQuant = "run fp4-in mxfp4-out quant GEMM M=32 N=32 K=64 E2M1xE2M1 blk16"
  it should titleInOutQuant in {
    runFp4Gemm(M = 32, N = 32, K = 64, outFmt = DataFormat.MXE2M1, blkSel = 1, rnd = 0,
      waveName = "hivecore_mxfp4_in_out_quant.fst",
      testMangle = mangleOf(titleInOutQuant))
  }

  // ⑤【Fix-10】errCode7 端到端负向：fp4 输入 mxBlockSize=32（blkSel=2，≠64）→ 硬件在
  //    EXECUTE 后直接置 STATUS.err=1/errCode=7，不进计算、dmaC 零写回、不发 done。
  //    钉死 Phase 3 的 fp4 v1「强制 block=64」契约在【顶层端到端】生效（CalcConfig 级已由
  //    HiveFp4GeometrySpec case2 覆盖，本用例补顶层 STATUS 传播 + 无 C 写回）。
  val titleErrCode7 = "reject fp4 blkSel=2 (blk=32 != 64) end-to-end with STATUS.err/errCode=7 and zero dmaC writeback"
  it should titleErrCode7 in {
    runFp4ErrCase7(M = 32, N = 32, K = 64, blkSel = 2,
      waveName = "hivecore_mxfp4_errcode7_blk32.fst",
      testMangle = mangleOf(titleErrCode7))
  }

  // ==========================================================================
  // 共享运行器：配置 fp4(hasMx4) HiveCore，逐拍供 A/B/C/scaleA/scaleB[/scaleC]，
  //   金锚（张量语义 decodeInElem）比对。结构逐段镜像 HiveCoreSimCaseMxSpec.runMxGemm，
  //   差异仅在 quad-lane 打包（4 nibble/lane）、64-K 单 pass 几何（block=64）、
  //   E2M1 输入解码金锚、scaleB n-major 多块布局。
  // ==========================================================================
  private def runFp4Gemm(M: Int, N: Int, K: Int, outFmt: DataFormat.Type,
      blkSel: Int, rnd: Int, waveName: String, testMangle: String): Unit = {

    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,                 // totalN = 16
      aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),  // hasMx4=true
      aBufferDepth = 2048,
      bBufferDepth = 2048,
      cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    val totalN = cfg.totalN          // 16
    val aEffW  = cfg.aEffW           // 16（16-bit lane 打包 4×4-bit nibble）
    val elemsPerLane = 4             // fp4 quad-lane
    val kPerPass = totalN * elemsPerLane   // 64 K/pass = 64-K scale 块 = 单 pass
    require(K % kPerPass == 0, s"fp4 用例 K($K) 必须 $kPerPass 对齐（1 pass=64 K=1 scale 块；errCode7 强制 block=64）")
    val kBlocks = K / kPerPass       // scale 块数 = kTile 数（block=64）

    val isQuant = outFmt == DataFormat.MXE4M3 || outFmt == DataFormat.MXE5M2 || outFmt == DataFormat.MXE2M1
    val outFmtInt = outFmt.litValue.toInt
    val blk = 8 << blkSel
    require(blkSel >= 0 && blkSel <= 3, s"blkSel($blkSel) 必须 0..3（8/16/32/64）")
    require(rnd >= 0 && rnd <= 4, s"rnd($rnd) 必须 0..4（RNE/RTZ/RDN/RUP/RMM）")
    if (isQuant) {
      require(blk <= totalN && totalN % blk == 0,
        s"量化块 blk($blk) 必须 ≤ totalN($totalN) 且整除（硬件同校验 config-err）")
      if (outFmt == DataFormat.MXE2M1) require(N % 2 == 0,
        s"mxfp4 半字节/元素，N($N) 必须偶数（行宽整字节，硬件同校验 config-err）")
    }
    val beatsPerRow = HiveSimCommon.cBeatsPerRow(outFmt, totalN, cfg.cDmaExtW)  // fp32=2 拍/行；mxfp4=1 拍/行

    // ---- crafted E2M1 精确域码 + scale 指数（确定性，含正负、覆盖多块/多列暴露路由错位）----
    // 幅值码 0..7 → {0,0.5,1,1.5,2,3,4,6}（decodeMxE2M1Input(c,127)）；bit3=符号（8..15=负）。
    def aCode(m: Int, k: Int): Int = { val mag = (m * 3 + k * 5) % 8; val sg = if (((m * 5 + k * 2) % 4) < 2) 0 else 8; mag | sg }
    def bCode(k: Int, n: Int): Int = { val mag = (k * 7 + n * 3) % 8; val sg = if (((k * 3 + n * 5) % 4) < 2) 0 else 8; mag | sg }
    def scaleAExp(m: Int, kb: Int): Int = ((m + kb) % 3) - 1        // {-1,0,1}
    def scaleBExp(kb: Int, n: Int): Int = ((n + 2 * kb) % 3) - 1    // {-1,0,1}

    val scaleA = Array.tabulate(M, kBlocks)((m, kb) => HiveSimCommon.mxScaleByte(scaleAExp(m, kb)))
    val scaleB = Array.tabulate(kBlocks, N)((kb, n) => HiveSimCommon.mxScaleByte(scaleBExp(kb, n)))
    val aByte = Array.tabulate(M, K)((m, k) => aCode(m, k))     // 4-bit E2M1 码
    val bByte = Array.tabulate(K, N)((k, n) => bCode(k, n))

    // ---- 金锚（张量语义正向推导，禁 RTL 拟合）：输入解码走 Phase 3 decodeInElem ----
    def aVal(m: Int, k: Int): Double =
      HiveDebugModel.decodeInElem(DataFormat.MXE2M1, aByte(m)(k).toLong, scaleA(m)(k / kPerPass))
    def bVal(k: Int, n: Int): Double =
      HiveDebugModel.decodeInElem(DataFormat.MXE2M1, bByte(k)(n).toLong, scaleB(k / kPerPass)(n))
    def refSum(m: Int, gc: Int): Double =
      (0 until K).map { k => aVal(m, k) * bVal(k, gc) }.sum

    // 自证 crafted 域精确性：金锚和在 fp32 域无损往返 ⟹ 比对是精确判据而非拟合。
    for (m <- 0 until M; gc <- 0 until N)
      require(refSum(m, gc).toFloat.toDouble == refSum(m, gc),
        s"[MXFP4] 金锚非精确（crafted 域被破坏）C($m,$gc)=${refSum(m, gc)}")

    // 期望 fp32 bits 行（totalN 元素，gc≥N 补 0）：与硬件同链重建 40→32 固定简化 RNE 规格化。
    def expectedFp32Row(nt: Int, m: Int): Seq[Long] =
      (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        val sum = if (gc < N) refSum(m, gc) else 0.0
        val fp32Bits = floatToRawIntBits(sum.toFloat) & 0xFFFFFFFFL
        HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(fp32Bits))
      }
    // 量化期望行：40→32 规格化 → swQuantBlock（逐元素码 + 每 blk 元素组 1 字节 E8M0 scale）。
    def expectedQuantRow(nt: Int, m: Int): (Seq[Int], Seq[Int]) = {
      val nEffTile = math.min(totalN, N - nt * totalN)
      HiveSimCommon.swQuantBlock(expectedFp32Row(nt, m), rnd, outFmt, blkSel, nEffTile)
    }

    Files.createDirectories(Paths.get(simDir))

    // ==================== DMA-A (A buffer) 地址/数据（fp4：16-bit lane 打包 4 nibble）=====
    // kTile 数 = K/64；行步长 = K/2 字节（半字节/元素）；k 窗口步进 colTile = 32 字节。
    val aKTiles = K / kPerPass
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = (K / 2).toLong                     // aRowAddressOffset = k/2（半字节档）
    val aColOff = 32L                                // aColTileAddressOffset = 16×4×4/8 = 32
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    // lane i = { nibble3=k+3, nibble2=k+2, nibble1=k+1, nibble0=k+0 }，k = kt*64 + 4i + j
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        var lane = 0
        for (j <- 0 until elemsPerLane) {
          val k = kt * kPerPass + elemsPerLane * i + j
          val nib = if (row < M && k < K) aByte(row)(k) else 0
          lane |= (nib & 0xF) << (4 * j)
        }
        data = data | (BigInt(lane & 0xFFFF) << (i * aEffW))
      }
      data
    }

    // ==================== DMA-B (B 权重，转置 loadW=1) 地址/数据（fp4 打包）=============
    val bKTiles = K / kPerPass
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = (K / 2).toLong                     // 转置行(n)步长 = K/2 字节
    val bColTileOff = totalN.toLong * (K / 2)        // 轮间跳 = totalN 转置行 × K/2 字节
    val kSliceStep = 32L                             // bColTileAddressOffset = 32（k 窗口步进）
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff + (totalN - 1).toLong * bRowOff + kt.toLong * kSliceStep
      for (r2 <- 0 until totalN) expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      val idxInRound = idx % beatsPerRound
      val kt = idxInRound / totalN
      val s = idxInRound % totalN
      val n = nTileIdx * totalN + (totalN - 1 - s)
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        var lane = 0
        for (j <- 0 until elemsPerLane) {
          val k = kt * kPerPass + elemsPerLane * i + j
          val nib = if (k < K && n < N) bByte(k)(n) else 0
          lane |= (nib & 0xF) << (4 * j)
        }
        data = data | (BigInt(lane & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // ==================== DMA-C (C store) 地址+数据（密集布局 + 逐拍硬对账）=============
    val nTilesC = (N + totalN - 1) / totalN
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, outFmt, cfg.cDmaExtW)
    def expCDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / (M * beatsPerRow)
      val inBlock = beatIdx % (M * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      val elemRow: Seq[Long] =
        if (isQuant) expectedQuantRow(nt, row)._1.map(_.toLong)
        else expectedFp32Row(nt, row)
      val denseRow = HiveSimCommon.elemsToDenseRow(elemRow, outFmt, totalN = totalN)
      HiveSimCommon.denseRowBeat(denseRow, beatInRow, cfg.cDmaExtW)
    }

    // ==================== DMA-CSCALE：按 isQuant 分派（量化写回 / 非量化死路）==========
    val scaleRowOff = (N + blk - 1).toLong / blk     // scale 行步进 = ceil(N/blk) 字节
    val scaleColOff = totalN.toLong / blk            // 列 tile 步进 = totalN/blk 字节
    val scaleBytesPerRow = scaleRowOff.toInt
    val expCScaleAddrSeq: Seq[Long] =
      if (isQuant) for (nt <- 0 until nTilesC; m <- 0 until M)
        yield SCALE_C_BASE + nt * scaleColOff + m * scaleRowOff
      else Seq.empty
    def expCScaleDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / M
      val row = beatIdx % M
      val (_, scales) = expectedQuantRow(nt, row)
      var data = BigInt(0)
      for ((s, g) <- scales.zipWithIndex if g < scaleBytesPerRow)
        data = data | (BigInt(s & 0xFF) << (8 * g))
      data
    }

    // ==================== DMA-scaleA (8-bit/beat)：布局 scaleA[m][kb] m-major =========
    // 遍历序（isA=true）= executor pop 序 = (round nt)×(kt 外)×(m 内)；kb = kt（单 pass=单块）。
    val scaleARowOff = kBlocks.toLong                // 行步进 = K/64 字节
    val scaleAColOff = 1L                            // 块(kb)步进 = 1 字节
    val scaleATensorBytes = M.toLong * kBlocks
    val expScaleAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expScaleAAddrSeq += SCALE_A_BASE + m * scaleARowOff + kt * scaleAColOff
    val scaleATotalBeats = expScaleAAddrSeq.size
    def genScaleABeatByAddr(addr: Long): BigInt = {
      val off = addr - SCALE_A_BASE
      val m = (off / scaleARowOff).toInt
      val kb = (off % scaleARowOff).toInt
      BigInt(scaleA(m)(kb) & 0xFF)
    }

    // ==================== DMA-scaleB (8-bit/beat)：布局 scaleB[n][kb] n-major ==========
    // 硬件地址模型 off = n×kBlocks + kb（见文件头推导）：scaleBRowAddressOffset=K/blk=kBlocks
    //   为 n 步进、scaleBColTileAddressOffset=1 为 kb 步进、scaleBNTileJump=totalN×kBlocks。
    //   单块（kBlocks=1）退化为 off=n，与 MxSpec [kb][n] 逐值一致；多 pass 首次覆盖多块。
    val blkB = 64                                    // bMxBlockSize=M64（fp4 强制）
    val bKLoadNum = totalN * 4                       // getKLoadNum(MXE2M1) = totalN×4 = 64
    val scaleBRowOff     = (K / blkB).toLong         // = kBlocks
    val scaleBColTileOff = (bKLoadNum / blkB).toLong // = 1
    val scaleBKTiles     = bKTiles                   // = kBlocks
    val scaleBNTileJump = (totalN - 1).toLong * scaleBRowOff + scaleBKTiles.toLong * scaleBColTileOff
    val expScaleBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until scaleBKTiles) {
      val blockStart = SCALE_B_BASE + r.toLong * scaleBNTileJump +
        (totalN - 1).toLong * scaleBRowOff + kt.toLong * scaleBColTileOff
      for (r2 <- 0 until totalN) expScaleBAddrSeq += blockStart - r2.toLong * scaleBRowOff
    }
    val scaleBTotalBeats = expScaleBAddrSeq.size     // = nTile×kTile×totalN
    val scaleBTensorBytes = N.toLong * kBlocks
    def genScaleBBeatByAddr(addr: Long): BigInt = {
      val off = addr - SCALE_B_BASE
      if (off < 0 || off >= scaleBTensorBytes) BigInt(0)
      else {
        val n  = (off / kBlocks).toInt               // n-major：off = n×kBlocks + kb
        val kb = (off % kBlocks).toInt
        BigInt(scaleB(kb)(n) & 0xFF)
      }
    }

    println(s"[MXFP4] Starting: M=$M N=$N K=$K totalN=$totalN kBlocks=$kBlocks outFmt=$outFmtInt isQuant=$isQuant")
    println(s"[MXFP4] beats: dmaA=$dmaATotalBeats dmaB=$dmaBTotalBeats C=${expCAddrSeq.size} scaleA=$scaleATotalBeats scaleB=$scaleBTotalBeats")

    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaBBeatsSent = 0
      var scaleABeatsSent = 0
      var scaleBBeatsSent = 0
      var dmaCScaleReqs = 0
      val scaleBAddrLog = scala.collection.mutable.ArrayBuffer[Long]()
      val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
      val scaleWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

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

      // ===== 配置寄存器 =====
      // regs(6)：loadWMode(bit28)=1（MX 强制转置 B）、cMxBlockSize(27,26)=blkSel、
      //   bMxBlockSize(25,24)=M64=3、aMxBlockSize(23,22)=M64=3（fp4 强制 block=64，否则 errCode7）。
      // regs(7)：aFmt(3,0)=MXE2M1=5、bFmt(7,4)=MXE2M1=5 → 0x55；rnd(14,12)、cFmt/outFmt(11,8)。
      val controlWord6 = (1L << 28) | ((blkSel.toLong & 0x3) << 26) | ((3L & 0x3) << 24) | ((3L & 0x3) << 22)
      val controlWord = 0x55L | ((rnd.toLong & 0x7) << 12) | ((outFmtInt.toLong & 0xF) << 8)
      if (outFmt == DataFormat.FP32 && rnd == 0)
        require(controlWord == 0x55L, s"控制字自检失败: 0x${controlWord.toHexString}")
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, controlWord6, controlWord)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SCALE_A_BASE, SCALE_B_BASE)
      if (isQuant) sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SCALE_C_BASE, 0L)

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
      var pollSt = 0
      var pollCool = 0
      val maxCycles = if (!isQuant) 20000 else 30000

      while (!done && cycle < maxCycles) {
        // --- DMA-A ---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-A beat #$dmaABeatsSent addr mismatch: ") {
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

        // --- DMA-C (store)：逐拍地址硬对账 + 内容硬断言（bit-exact 主守护）---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          if (dmaCStoreBeats < expCAddrSeq.size) {
            withClue(s"DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
              reqAddr should be(expCAddrSeq(dmaCStoreBeats))
            }
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          if (dmaCStoreBeats < expCAddrSeq.size) {
            val expData = expCDataBeat(dmaCStoreBeats)
            withClue(s"DMA-C store beat #$dmaCStoreBeats data mismatch (hw=0x$writeData%X exp=0x$expData%X): ") {
              writeData should be(expData)
            }
          }
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-CSCALE ---
        dut.io.dmaCScale.get.req.ready.poke(true.B)
        if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) {
          if (isQuant) {
            val reqAddr = dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-CSCALE beat #$dmaCScaleReqs addr mismatch: ") {
              reqAddr should be(expCScaleAddrSeq(dmaCScaleReqs))
            }
            val writeData = dut.io.dmaCScale.get.req.payload.data.peek().litValue
            val expData = expCScaleDataBeat(dmaCScaleReqs)
            withClue(f"DMA-CSCALE beat #$dmaCScaleReqs data mismatch (hw=0x$writeData%X exp=0x$expData%X): ") {
              writeData should be(expData)
            }
            scaleWriteLog += ((reqAddr, writeData))
          }
          dmaCScaleReqs += 1
          dut.io.dmaCScale.get.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        }

        // --- DMA-B (权重) ---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
        }

        // --- DMA-scaleA (8-bit) ---
        if (scaleABeatsSent < scaleATotalBeats) {
          if (dut.io.dmaScaleA.get.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-scaleA beat #$scaleABeatsSent addr mismatch: ") {
              cmdAddr should be(expScaleAAddrSeq(scaleABeatsSent))
            }
            withClue(s"DMA-scaleA beat #$scaleABeatsSent addr out of tensor bounds: ") {
              (cmdAddr >= SCALE_A_BASE && cmdAddr < SCALE_A_BASE + scaleATensorBytes) should be(true)
            }
            dut.io.dmaScaleA.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(genScaleABeatByAddr(cmdAddr).U)
            dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
            scaleABeatsSent += 1
          } else {
            dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
        }

        // --- DMA-scaleB (8-bit，n-major 内存保真供数) ---
        if (scaleBBeatsSent < scaleBTotalBeats) {
          if (dut.io.dmaScaleB.get.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-scaleB beat #$scaleBBeatsSent addr mismatch: ") {
              cmdAddr should be(expScaleBAddrSeq(scaleBBeatsSent))
            }
            val scaleByte = genScaleBBeatByAddr(cmdAddr)
            withClue(s"DMA-scaleB beat #$scaleBBeatsSent data exceeds 8-bit port width: ") {
              (scaleByte >= BigInt(0) && scaleByte <= BigInt(0xFF)) should be(true)
            }
            scaleBAddrLog += cmdAddr
            dut.io.dmaScaleB.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(scaleByte.U)
            dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
            scaleBBeatsSent += 1
          } else {
            dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
        }

        // --- 完成检查：STATUS 轮询 ---
        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.done) {
            println(f"[MXFP4 cycle=$cycle%5d] EXECUTE complete (STATUS.done=1, err=${st.err})! A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent")
            st.err should be(false)
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)

      if (!done) println(s"[MXFP4] WARNING: timeout at $maxCycles cycles (A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent)")

      done should be(true)
      // 六通道拍数硬断言
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(nTilesC * M * beatsPerRow)
      scaleABeatsSent should be(scaleATotalBeats)
      scaleBBeatsSent should be(scaleBTotalBeats)
      scaleBAddrLog.size should be(scaleBTotalBeats)
      if (isQuant) {
        withClue("[MXFP4] dmaCScale write beat count (quant run): ") { dmaCScaleReqs should be(expCScaleAddrSeq.size) }
        withClue("[MXFP4] dmaCScale write beats should be nTiles*M (quant run): ") { dmaCScaleReqs should be(nTilesC * M) }
      } else {
        withClue("[MXFP4] dmaCScale must stay silent (zero req) under fp32 direct output: ") { dmaCScaleReqs should be(0) }
      }

      // ===== 金锚对账 =====
      val hwBits = HiveSimCommon.parseDenseCWriteLog(cWriteLog.toSeq, outFmt, M, N, totalN, cfg.cDmaExtW)
      if (isQuant) {
        // 量化：写回码按块 scale 反量化后 vs 双精度金锚（冲刷界豁免，同 MxSpec/QuantSpec 范式）。
        val isFp4 = outFmt == DataFormat.MXE2M1
        val elemBitsX = if (isFp4) 4 else 8
        def dequant(code: Int, scale: Int): Double = {
          if (isFp4) {
            val sg = (code >> 3) & 1; val e = (code >> 1) & 0x3; val mm = code & 1
            if (e == 0 || scale == 0) 0.0
            else { val v = (1.0 + mm.toDouble / 2) * math.pow(2, e - 1) * math.pow(2, scale - 127); if (sg == 1) -v else v }
          } else {
            val (expW, mantW, bias) = if (outFmt == DataFormat.MXE4M3) (4, 3, 7) else (5, 2, 15)
            val sg = (code >> (expW + mantW)) & 1
            val mag = code & ((1 << (expW + mantW)) - 1)
            val e = mag >> mantW; val mm = mag & ((1 << mantW) - 1)
            if (e == 0 || scale == 0) 0.0
            else { val v = (1.0 + mm.toDouble / (1 << mantW)) * math.pow(2, e - bias) * math.pow(2, scale - 127); if (sg == 1) -v else v }
          }
        }
        var mismatchCnt = 0
        var maxRelErr = 0.0
        var beatIdx = 0
        val elemsPerBeat = cfg.cDmaExtW / elemBitsX
        for ((_, data) <- cWriteLog) {
          val nt = beatIdx / (M * beatsPerRow)
          val inBlock = beatIdx % (M * beatsPerRow)
          val row = inBlock / beatsPerRow
          val beatInRow = inBlock % beatsPerRow
          val scaleBeat = scaleWriteLog(nt * M + row)._2
          for (i <- 0 until elemsPerBeat) {
            val globalCol = nt * totalN + beatInRow * elemsPerBeat + i
            if (globalCol < N && globalCol < (nt + 1) * totalN) {
              val code = ((data >> (elemBitsX * i)) & ((1 << elemBitsX) - 1)).toInt
              val localCol = globalCol - nt * totalN
              val grp = localCol / 8
              val scale = ((scaleBeat >> (8 * grp)) & 0xFF).toInt
              val hw = dequant(code, scale)
              val ref = refSum(row, globalCol)
              val absErr = math.abs(hw - ref)
              val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
              val flushBound = math.pow(2, scale - 127)
              if (relErr > 0.5 && absErr > 1e-3 && absErr > flushBound + 1e-9) {
                if (mismatchCnt < 8)
                  println(f"[MXFP4] DEQUANT MISMATCH C($row,$globalCol): hw=$hw%.6g ref=$ref%.6g code=0x$code%X scale=0x$scale%02X")
                mismatchCnt += 1
              }
              if (relErr > maxRelErr) maxRelErr = relErr
            }
          }
          beatIdx += 1
        }
        println(f"[MXFP4] quant outFmt=$outFmtInt dequant cross-check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
        withClue("[MXFP4] mxfp4 输出量化反量化交叉失配（C 矩阵失配数）: ") { mismatchCnt should be(0) }
      } else {
        // fp32 直出：① 逐位金锚守护（硬件 fp32 bits == expectedFp32Row bits，同链重建）；
        //   ② 双精度容差交叉（第二视角，1e-2；crafted 精确域下应逐位相等 → 失配 0）。
        var bitMismatch = 0
        for (m <- 0 until M; gc <- 0 until N) {
          val nt = gc / totalN; val lc = gc % totalN
          val exp = expectedFp32Row(nt, m)(lc)
          if (hwBits(m)(gc) != exp) {
            if (bitMismatch < 8)
              println(f"[MXFP4] BIT MISMATCH C($m,$gc): hw=0x${hwBits(m)(gc)}%08X exp=0x$exp%08X")
            bitMismatch += 1
          }
        }
        withClue("[MXFP4] fp32 直出逐位金锚守护失配（C 矩阵失配数）: ") { bitMismatch should be(0) }

        var mismatchCnt = 0
        var maxRelErr = 0.0
        for (m <- 0 until M; gc <- 0 until N) {
          val hw = HiveSimCommon.fp32BitsToFloat(hwBits(m)(gc)).toDouble
          val ref = refSum(m, gc)
          val absErr = math.abs(hw - ref)
          val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
          if (relErr > 1e-2 && absErr > 1e-6) {
            if (mismatchCnt < 8) println(f"[MXFP4] MISMATCH C($m,$gc): hw=$hw%.6g ref=$ref%.6g relErr=$relErr%.3f")
            mismatchCnt += 1
          }
          if (relErr > maxRelErr) maxRelErr = relErr
        }
        println(f"[MXFP4] fp32 cross-check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
        for (m <- 0 until math.min(M, 4)) {
          val hwStr = (0 until N).map(gc => f"${HiveSimCommon.fp32BitsToFloat(hwBits(m)(gc))}%.3f").mkString(" ")
          val refStr = (0 until N).map(gc => f"${refSum(m, gc)}%.3f").mkString(" ")
          println(s"[MXFP4] hw  row$m: $hwStr")
          println(s"[MXFP4] ref row$m: $refStr")
        }
        withClue("[MXFP4] fp32 双精度容差交叉失配（C 矩阵失配数）: ") { mismatchCnt should be(0) }
      }
    }
    } finally {
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCaseMxFp4Spec/HiveCore-MXFP4-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        "MXFP4"
      )
    }
  }

  // ==========================================================================
  // 【Fix-10】errCode7 端到端负向运行器：fp4 输入 mxBlockSize≠64（blkSel=2→blk=32）。
  //   硬件在 EXECUTE 后由 CalcConfig errComb 置 errCode7 → 顶层 errFlag → STATUS.err，
  //   executor 拒绝启动计算 ⟹ 无 dmaA/dmaB/dmaC/scale 供数请求、dmaC 零写回、不发 done。
  //   本运行器只做「配置 + EXECUTE + STATUS 轮询 + dmaC 静默核查」，不供任何数据
  //   （config-err 路径本就不进计算）。结构镜像 runFp4Gemm 的 reset/config/poll 段。
  // ==========================================================================
  private def runFp4ErrCase7(M: Int, N: Int, K: Int, blkSel: Int,
      waveName: String, testMangle: String): Unit = {

    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,               // totalN = 16
      aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),  // hasMx4=true
      aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048, scaleBufferDepth = 256
    )
    val blk = 8 << blkSel
    require(blkSel == 2 && blk == 32, s"本负向用例专测 blk=32(≠64) 触发 errCode7，当前 blkSel=$blkSel blk=$blk")

    Files.createDirectories(Paths.get(simDir))
    println(s"[MXFP4-ERR7] Starting negative case: M=$M N=$N K=$K aFmt=bFmt=MXE2M1 aMxBlk=bMxBlk=$blk(≠64) → expect errCode=7")

    try {
    simulate(new HiveCore(cfg)) { dut =>

      var dmaCStoreBeats = 0
      var dmaAReqSeen = 0
      var dmaBReqSeen = 0

      // ===== Reset（镜像 runFp4Gemm：全 DMA req.ready=1、rsp.valid=0）=====
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

      // ===== 配置寄存器：aMxBlockSize/bMxBlockSize = blkSel=2(M32)，触发 errCode7 =====
      //   regs(6)：loadWMode(28)=1、cMxBlk(27,26)=0、bMxBlk(25,24)=blkSel、aMxBlk(23,22)=blkSel。
      //   regs(7)：aFmt(3,0)=MXE2M1=5、bFmt(7,4)=MXE2M1=5 → 0x55；rnd=0；outFmt=FP32=0。
      val controlWord6 = (1L << 28) | ((0L & 0x3) << 26) | ((blkSel.toLong & 0x3) << 24) | ((blkSel.toLong & 0x3) << 22)
      val controlWord = 0x55L
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, controlWord6, controlWord)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SCALE_A_BASE, SCALE_B_BASE)

      // ===== EXECUTE =====
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      var errSeen = false
      var errCode = -1
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      val maxCycles = 3000

      while (!errSeen && !done && cycle < maxCycles) {
        // dmaC 静默核查：config-err 路径不得发起任何 C 写回请求
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }
        if (dut.io.dmaA.req.valid.peek().litToBoolean) dmaAReqSeen += 1
        if (dut.io.dmaB.req.valid.peek().litToBoolean) dmaBReqSeen += 1

        val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        statusOpt.foreach { st =>
          if (st.err) {
            errSeen = true
            errCode = st.errCode
            println(f"[MXFP4-ERR7 cycle=$cycle%5d] STATUS.err=1 errCode=${st.errCode} (done=${st.done})")
          } else if (st.done) {
            done = true
          }
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.cmd.valid.poke(false.B)

      println(s"[MXFP4-ERR7] 结果：cycles=$cycle errSeen=$errSeen errCode=$errCode done=$done " +
        s"dmaC写回拍=$dmaCStoreBeats dmaA请求拍=$dmaAReqSeen dmaB请求拍=$dmaBReqSeen")

      withClue("[MXFP4-ERR7] fp4 blk=32(≠64) 必须被硬件拒绝（STATUS.err=1）：") { errSeen should be(true) }
      withClue("[MXFP4-ERR7] errCode 必须为 7（fp4 v1 强制 mxBlockSize=64）：") { errCode should be(7) }
      withClue("[MXFP4-ERR7] config-err 路径不得发 done：") { done should be(false) }
      withClue("[MXFP4-ERR7] config-err 路径 dmaC 必须零写回：") { dmaCStoreBeats should be(0) }
    }
    } finally {
      HiveSimCommon.copyTrace(
        s"build/chiselsim/HiveCoreSimCaseMxFp4Spec/HiveCore-MXFP4-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        "MXFP4-ERR7"
      )
    }
  }
}
