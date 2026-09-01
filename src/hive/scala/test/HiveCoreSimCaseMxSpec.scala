/** HiveCore MXFP8 v2 SIMD 双 MX（异构 E4M3 × E5M2）端到端数值仿真用例。
  *
  * 【v2 SIMD 打包布局】16-bit lane 打包 2 个 8-bit MX 元素：低字节=偶 k=2j、
  * 高字节=奇 k=2j+1；PE 内 4×HiveCvtOpMx + 2×HiveMacFp 级联（先偶后奇），
  * 每拍每波前推进 32 个 K 元素 = 恰好一个 32-K scale 块 = 单 pass，
  * 故 kTile 数 = K/32（scaleA 块下标 kb = kt）。外部 A/B 存储 1 字节/元素：
  * 行步长 = K 字节（非 2K），k 窗口步进 = 32 字节（2×totalN）。
  *
  * 【v1 bit 锚守护】单块用例（M=N=K=32）加载金锚文件（在改动前 v1 代码上录制的
  * C 写回 fp32 bits + 拍数），对硬件写回逐位相等断言；级联序（偶→奇、行升序）
  * = v1 全局 k 升序左结合、每乘积同一舍入点，应逐 bit 相等。
  * 【无锚用例留痕（Hank P1-B）】multi-tile（M=4/N=32/K=64）与 multi-pass（M=4/N=16/K=64）
  * 两条用例无 v1 bit 锚：v1 代码已被 v2 直接替换、v1 硬件已不在工作区，无法补录锚；
  * 该两条仅靠 1e-2 golden 容差守护（属降级守护）。若需升级回 bit 锚，须先在 git 历史
  * 恢复 v1 代码补录锚文件至 sim/mx/ 后再接入 anchorPath。
  *
  * 结构与 HiveCoreSimCaseLoadWSpec 同构（loadW 水平加载 + 转置 B），差异：
  *   - aW=bW=16（16-bit lane 打包 2×8-bit 元素），cW=40（复用 fp32 延迟规格化累加器）
  *   - regs(7)=0x5E0：aFmt=MXE4M3(4)、bFmt=MXE5M2(5)、loadWMode=1、mixFmtEn=1、rnd=RNE
  *       aFmt = Cat(regs(7)(6),regs(7)(1,0)) = Cat(1,00) = 0b100 = 4 (E4M3)
  *       bFmt = Cat(regs(7)(7),regs(7)(9,8)) = Cat(1,01) = 0b101 = 5 (E5M2)
  *       loadWMode=bit5=0x20, mixFmtEn=bit10=0x400 → 0x40|0x80|0x100|0x20|0x400 = 0x5E0
  *   - 新增两条 scale 只读 DMA 外部通道（仅 hasMx 暴露）：
  *       dmaScaleA = scaleA（8-bit/beat，随激活流动）
  *       dmaScaleB = scaleB（scaleRowW=totalN*8=128-bit/beat，列驻留）
  *
  * scale 路由模型（逐拍对齐——见各 gen*Beat 注释）：
  *   - scaleA[m][kb]：executor sLOAD_A 每 aPop.fire pop 一个 8-bit 广播全行，
  *     随激活链 skew 流入。scaleADma（HiveCoreDmaScaleRd，A 式三级遍历）遍历序 =
  *     executor pop 序 =
  *     (round nt) × (kt 外) × (m 内)，共 nTile×kTile×M beat。
  *   - scaleB[kb][n]：executor 每个 sLOAD_B 窗口（每 kTile = 一个 32-K 块）
  *     pop 一个 totalN 向量锁存 scaleBRegs(i)→列 i（globalCol=nTile*totalN+i）。
  *     pop 序 = (round nt 外) × (kb=kt 块内)，scaleBDma（HiveCoreDmaScaleRd
  *     isScaleB 两级遍历）按同序取址，共 nTile×kTile beat（每 (nt,kt) 一向量）。
  *
  * golden：C[m][n] = Σ_k mxE4M3ToFloat(aByte[m][k], scaleA[m][k/32])
  *                       × mxE5M2ToFloat(bByte[k][n], scaleB[k/32][n])
  * 元素值由同一 HiveSimCommon.mxToFloat 重建（硬件/golden 同源），故元素级
  * 逐 bit 相等，唯一偏差是 fp32 累加舍入（远小于 1e-2 容差）。
  *
  * 既有非 MX spec 一律零改动，非 MX 路径 bit-exact 由回归门禁守护。
  * 【硬件侧消费有效门控已实施】HiveCvtOpMx inf/nan 断言已由 consumeValid 门控（见
  * HiveWorkUnit/HiveWorker），空闲/加载/排空拍垃圾输入不再撞断言 $fatal，
  * 本 suite 已恢复 ChiselSim 默认随机化（原测试侧 Randomization.uninitialized
  * 零初值规避已移除）。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCaseMxSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  // 【硬件侧门控已实施，测试侧规避已移除】HiveCvtOpMx inf/nan 断言现受
  // consumeValid 消费有效门控（HiveWorkUnit/HiveWorker 单行连入，条件 =
  // MX 结果被真实消费的充要条件），空闲/加载/排空拍的随机垃圾不再触发断言。
  // 故本 suite 恢复 ChiselSim 默认随机化（不传 settings）——默认随机化下全绿
  // 即证明硬件侧门控有效，且还原真实上电语义。

  val A_BASE:       Long = 0x00000000L
  val B_BASE:       Long = 0x00100000L
  val C_BASE:       Long = 0x00200000L
  val SCALE_A_BASE: Long = 0x00300000L
  val SCALE_B_BASE: Long = 0x00400000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/mx"

  behavior of "HiveCore MXFP8 GEMM Simulation"

  it should "run MX single-block GEMM M=32 N=32 K=32 E4M3xE5M2" in {
    runMxGemm(M = 32, N = 32, K = 32,
      waveName = "hivecore_mx_sim.fst",
      testMangle = "should-run-MX-single-block-GEMM-M-32-N-32-K-32-E4M3xE5M2",
      anchorPath = Some(s"$simDir/v1_anchor_M32_N32_K32.txt"),
      expCycleWindow = Some((150, 170)))
  }

  // 多 nTile×多 kTiles（N=32→nTile=2，K=64→kTiles=2）：scaleB 供数序
  // nt 外 × kb 内两级遍历对账（线性 kb-major 取址在此维度组合下必错位）；
  // 多 kTile 同轮同时钉死 bNextKTileAddressOffset v2 公式 totalN*(bRowOff+2)。
  // 【无 v1 bit 锚（Hank P1-B）】v1 代码已被 v2 直接替换、无法补录锚 →
  // 本用例仅 1e-2 golden 容差守护（降级守护，详见文件头留痕段落）。
  it should "run MX multi-tile GEMM M=4 N=32 K=64 2nTile 2blk" in {
    runMxGemm(M = 4, N = 32, K = 64,
      waveName = "hivecore_mx_multintile_sim.fst",
      testMangle = "should-run-MX-multi-tile-GEMM-M-4-N-32-K-64-2nTile-2blk")
  }

  // K=64→kTiles=2 顺序 pass（v2 单 pass/块，不再双 pass 拼块）
  // 【无 v1 bit 锚（Hank P1-B）】v1 代码已被 v2 直接替换、无法补录锚 →
  // 本用例仅 1e-2 golden 容差守护（降级守护，详见文件头留痕段落）。
  it should "run MX multi-pass GEMM M=4 N=16 K=64 2blk 2pass" in {
    runMxGemm(M = 4, N = 16, K = 64,
      waveName = "hivecore_mx_multipass_sim.fst",
      testMangle = "should-run-MX-multi-pass-GEMM-M-4-N-16-K-64-2blk-2pass")
  }

  // P5.4 err 负向：MX config 违约直回 sIDLE、不发 done，STATUS.err 位可被软件轮询。
  //   (1) K%32≠0（K=48，MX 块必须 32 对齐）
  it should "raise STATUS.err on MX K not 32-aligned (no done)" in {
    runMxErrCase(M = 4, N = 16, K = 48, regs7 = 0x5E0L,
      caseName = "K%32!=0")
  }
  //   (2) isMx(bFmt) && loadWMode=0（MX 下 B 必须转置读取/水平加载）
  it should "raise STATUS.err on MX bFmt without loadWMode (no done)" in {
    runMxErrCase(M = 4, N = 16, K = 32, regs7 = 0x5C0L,   // 0x5E0 去掉 loadWMode(0x20)
      caseName = "isMx(bFmt)&&!loadWMode")
  }

  // ==========================================================================
  // 共享运行器：配置 MX HiveCore，逐拍供 A/B/C/scaleA/scaleB，golden 比对。
  // ==========================================================================
  private def runMxGemm(M: Int, N: Int, K: Int, waveName: String, testMangle: String,
      anchorPath: Option[String] = None, expCycleWindow: Option[(Int, Int)] = None): Unit = {

    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,                 // totalN = 16
      aW = 16, bW = 16, cW = 40,      // 与出厂 cfgMx（HiveCoreElaborate）及 err 用例对齐
     // supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2),
      aBufferDepth = 64,
      bBufferDepth = 64,
      cBufferDepth = 64,
      scaleBufferDepth = 64
    )

    val totalN = cfg.totalN         // 16
    val aEffW  = cfg.aEffW          // 16（v2：16-bit lane 打包 2×8-bit 元素）
    val cEffW  = cfg.cEffW          // 40
    require(K % 32 == 0, s"MX 用例 K($K) 必须 32 对齐")
    val kBlocks = K / 32            // scale 块数

    // ---- scale 指数与元素内蕴值（确定性，含正负、覆盖多个块/列以暴露路由错位） ----
    def scaleAExp(m: Int, kb: Int): Int = ((m + kb) % 3) - 1        // {-1,0,1}
    def scaleBExp(kb: Int, n: Int): Int = ((n + 2 * kb) % 3) - 1    // {-1,0,1}
    val scaleA = Array.tabulate(M, kBlocks)((m, kb) => HiveSimCommon.mxScaleByte(scaleAExp(m, kb)))
    val scaleB = Array.tabulate(kBlocks, N)((kb, n) => HiveSimCommon.mxScaleByte(scaleBExp(kb, n)))

    def aValF(m: Int, k: Int): Double = ((((m * 7 + k * 3) % 11) - 5).toDouble) * 0.25   // ~[-1.25,1.25]
    def bValF(k: Int, n: Int): Double = ((((k * 5 + n * 2) % 9)  - 4).toDouble) * 0.5    // ~[-2,2]
    val aByte = Array.tabulate(M, K)((m, k) => HiveSimCommon.floatToMxE4M3(aValF(m, k)))
    val bByte = Array.tabulate(K, N)((k, n) => HiveSimCommon.floatToMxE5M2(bValF(k, n)))
    // 内存物理布局：B 转置存储（行=n、列=k），BT(n)(k)=bByte(k)(n)
    val bByteTransposed = Array.tabulate(N, K)((n, k) => bByte(k)(n))

    Files.createDirectories(Paths.get(simDir))

    // ==================== DMA-A (A buffer) 地址/数据（v2：16-bit lane 打包 2 元素） ==
    // v2：kTile 数 = K/32（16 lane × 2 元素/拍 = 一个 32-K scale 块 = 单 pass）；
    // 行步长 = K 字节（1 字节/元素打包）；k 窗口步进 aColOff = totalN*2 = 32 字节。
    val aKTiles = K / 32
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong                          // 打包行步长 = K 字节
    val aColOff = totalN.toLong * (cfg.aW / 8)      // = 2*totalN = 32 字节
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    // lane i = {aByte[m][kt*32+2i+1] 高字节, aByte[m][kt*32+2i] 低字节}
    // （低=偶 k，高=奇 k，与 PE 级联先偶后奇逐拍消费对齐）
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val kEven = kt * 32 + 2 * i
        val kOdd  = kEven + 1
        val lo = if (row < M && kEven < K) aByte(row)(kEven) else 0
        val hi = if (row < M && kOdd  < K) aByte(row)(kOdd)  else 0
        data = data | (BigInt(((hi & 0xFF) << 8) | (lo & 0xFF)) << (i * aEffW))
      }
      data
    }

    // ==================== DMA-B (B 权重) 地址/数据（转置闭式，v2 打包） ===========
    // v2：kTile 数 = K/32；行步长 = K 字节；k 窗口步进 kSliceStep = 2*totalN = 32
    // 字节；块间跳转（bNextKTileAddressOffset）= totalN*(bRowOff+2)，由逐 beat
    // 地址硬断言钉死（多 kTile 用例覆盖）。
    val bKTiles = K / 32
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = K.toLong                          // 打包行步长 = K 字节
    val bColTileOff = totalN.toLong * K             // 轮间跳 = totalN 个转置行 × K 字节
    val kSliceStep = totalN.toLong * (cfg.bW / 8)   // = 2*totalN = 32 字节（k 窗口步进）
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff + (totalN - 1).toLong * bRowOff + kt.toLong * kSliceStep
      for (r2 <- 0 until totalN) expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    // lane i = pack{bByte[kt*32+2i+1][n] 高, bByte[kt*32+2i][n] 低}；n 降序协议不变。
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      val idxInRound = idx % beatsPerRound
      val kt = idxInRound / totalN
      val s = idxInRound % totalN
      val n = nTileIdx * totalN + (totalN - 1 - s)
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val kEven = kt * 32 + 2 * i
        val kOdd  = kEven + 1
        val lo = if (kEven < K && n < N) bByte(kEven)(n) else 0
        val hi = if (kOdd  < K && n < N) bByte(kOdd)(n)  else 0
        data = data | (BigInt(((hi & 0xFF) << 8) | (lo & 0xFF)) << (i * cfg.bW))
      }
      data
    }

    // ==================== DMA-C (C store) 地址（cW=40） ==========================
    val cRowOff = N.toLong * (cfg.cW / 8)
    val cColOff = totalN.toLong * (cfg.cW / 8)
    val expCAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until aNTiles; m <- 0 until M)
      expCAddrSeq += C_BASE + nt.toLong * (M - 1) * cRowOff + nt.toLong * (nt + 1) / 2 * cColOff + m * cRowOff

    // ==================== DMA-scaleA (scaleA) 数据：8-bit/beat ========================
    // 遍历序（isA=true）= executor pop 序 = (round nt)×(kt 外)×(m 内)。
    // 每 beat = scaleA[m][kb]；v2 单 pass：一个 kTile = 一个 32-K scale 块 → kb = kt。
    // 【修复（taskId=86）】地址独立从张量语义推导（与 QuantSpec 同源），不再镜像 A 数据：
    //   外部布局 scaleA[m][kb] 连续（每行 K/32 字节），地址 = SCALE_A_BASE + m×(K/32) + kb×1。
    val scaleARowOff = kBlocks.toLong             // 行步进 = K/32 字节
    val scaleAColOff = 1L                         // 块（kb）步进 = 1 字节
    val scaleATensorBytes = M.toLong * kBlocks    // 真实张量字节数 = M×(K/32)
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

    // ==================== DMA-scaleB (scaleB) 数据：128-bit/beat ======================
    // isScaleB 两级遍历（nt 外 × kb 内），共 nTile×kTile beat（v2：kTile=kBlocks，
    // 不再 >>1）；beat idx 按硬件遍历序解码：nt = idx / kBlocks（轮），
    // kb = idx % kBlocks（块内）。byte i = scaleB[kb][nt*totalN + i]
    // （列驻留，n<N 有效，否则补 0）；与 executor pop 序（每 nt 轮内每 kTile/
    // 32-K 块起始 pop 一次）逐拍一致。
    val scaleBTotalBeats = nTilesB * bKTiles
    require(scaleBTotalBeats == kBlocks * nTilesB, s"scaleB beat 数自检失败: $scaleBTotalBeats vs ${kBlocks * nTilesB}")
    def genScaleBBeat(idx: Int): BigInt = {
      val nt = idx / kBlocks
      val kb = idx % kBlocks
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val n = nt * totalN + i
        val value = if (n < N) scaleB(kb)(n) else 0
        data = data | (BigInt(value & 0xFF) << (8 * i))
      }
      data
    }

    println(s"[MX] Starting: M=$M N=$N K=$K totalN=$totalN kBlocks=$kBlocks")
    println(s"[MX] beats: dmaA(A)=$dmaATotalBeats dmaB(B)=$dmaBTotalBeats C=${expCAddrSeq.size} scaleA=$scaleATotalBeats scaleB=$scaleBTotalBeats")

    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaBBeatsSent = 0
      var scaleABeatsSent = 0
      var scaleBBeatsSent = 0
      val scaleBAddrLog = scala.collection.mutable.ArrayBuffer[Long]()  // scaleB DMA 实际取址序列（逐 beat 对账）
      val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

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
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, 0x5E0L) // MX: E4M3×E5M2, loadWMode=1, mixFmtEn=1
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

      var done = false
      var cycle = 0
      val maxCycles = 8000

      while (!done && cycle < maxCycles) {

        // --- DMA-A (A) ---
        // 已知待办（评审记录）：expAAddrSeq 构建后未用于逐 beat 地址断言，
        // 当前按 beat 序号供数（内存保真依赖 DMA/executor 同序约定）；
        // 后续可仿 DMA-B/scaleB 风格改为 req.valid 拍响应 + 逐拍地址硬对账。
        if (dmaABeatsSent < dmaATotalBeats) {
          dut.io.dmaA.rsp.valid.poke(true.B)
          dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
          if (dut.io.dmaA.rsp.ready.peek().litToBoolean) dmaABeatsSent += 1
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
        }

        // --- DMA-C (C store) ---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          if (dmaCStoreBeats < expCAddrSeq.size) {
            withClue(s"DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
              reqAddr should be(expCAddrSeq(dmaCStoreBeats))
            }
          }
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-B (B 权重) 逐 beat 地址硬对账 ---
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

        // --- DMA-scaleA (scaleA) 8-bit：req.valid 拍响应 + 逐拍地址硬对账（同 scaleB 风格） ---
        // 【修复（taskId=86）】由按 beat 序供数（无地址对账）改为按地址响应 + 逐拍硬对账；
        // 供数值不变（同序解码 m/kb 等价）。边界断言防地址足迹越出真实张量。
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

        // --- DMA-scaleB (scaleB) 128-bit：每 (nt,kb) 一向量 ---
        // 仅在 req.valid 拍响应（同 DMA-B 风格）：DMA req 恰 fire nTile×kBlocks
        // 次（每拍 fire 推进 lineCounter），rsp 与 req 逐拍 lockstep（rsp.ready
        // 恒高）；轮间跳转拍（sNEXT_COL）req.valid 拉低则不给 rsp，避免与
        // 请求脱拍的伪 push 扰乱逐拍取址对账。
        if (scaleBBeatsSent < scaleBTotalBeats) {
          if (dut.io.dmaScaleB.get.req.valid.peek().litToBoolean) {
            scaleBAddrLog += dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong
            dut.io.dmaScaleB.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(genScaleBBeat(scaleBBeatsSent).U)
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

        // --- 完成检查 ---
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          println(f"[MX cycle=$cycle%5d] EXECUTE complete! A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent")
          dut.io.resp.payload.err.peek().litToBoolean should be(false)
          done = true
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)

      if (!done) println(s"[MX] WARNING: timeout at $maxCycles cycles (A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent)")

      done should be(true)
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      dmaCStoreBeats should be(expCAddrSeq.size)
      scaleABeatsSent should be(scaleATotalBeats)
      scaleBBeatsSent should be(scaleBTotalBeats)

      // ===== 拍数断言（归档）：v2 单 pass 几何，单块用例应 ≈v1（281/282）减半 =====
      expCycleWindow.foreach { case (lo, hi) =>
        withClue(s"[MX] cycles=$cycle 超出预期窗口 [$lo,$hi]（v1 锚 281/282）: ") {
          (cycle >= lo && cycle <= hi) should be(true)
        }
      }

      // scaleB 取址序硬对账（同 DMA-B 风格）：外部布局 scaleB[kb][n] 连续
      // （每 kb 占 N 字节），向量 (kb,nt) 字节地址 = base + kb*N + nt*totalN。
      // executor pop 序 = nt 轮外 × kb 轮内（每 nt 轮内各 K-块起始 pop 一次），
      // 故 DMA 取址序列必须同为 nt 外 × kb 内：
      //   addr(idx) = base + (idx % kBlocks)*N + (idx / kBlocks)*totalN
      // genScaleBBeat 按同一解码供该地址处数据（内存保真），两者钉死后
      // 任意 nTile×kBlocks 组合（含 N=32×K=64 的双多组合）供数序不错位。
      val expScaleBAddrSeq = (0 until scaleBTotalBeats).map { idx =>
        val nt = idx / kBlocks
        val kb = idx % kBlocks
        SCALE_B_BASE + kb.toLong * N + nt.toLong * totalN
      }
      scaleBAddrLog.size should be(scaleBTotalBeats)
      for ((a, i) <- scaleBAddrLog.zipWithIndex) {
        withClue(s"scaleB beat #$i addr mismatch: ") {
          a should be(expScaleBAddrSeq(i))
        }
      }

      // ===== C 重建 =====
      val cResult = Array.fill(M, N)(BigInt(0))
      var cBeatIdx = 0
      for ((_, data) <- cWriteLog) {
        val row = cBeatIdx % M
        if (row < M) {
          for (col <- 0 until math.min(totalN, N)) {
            val elem = HiveSimCommon.extractBits(data, cEffW * (col + 1) - 1, cEffW * col)
            val globalCol = (cBeatIdx / M) * totalN + col
            if (globalCol < N) cResult(row)(globalCol) = elem
          }
        }
        cBeatIdx += 1
      }

      // ===== v1 bit 锚逐位相等断言（主门禁，仅单块用例传入锚文件） =====
      // 锚在未改动 v1 代码上录制；v2 级联序（偶→奇、行升序）= v1 全局 k 升序
      // 左结合、每乘积同一 Fp32.mul 舍入点 → 应逐 bit 相等；若偏差需定位到具体
      // 舍入点，仅不可消除的对齐截断差异才可降级 ≤1 ulp 容差（预期不发生）。
      anchorPath.foreach { path =>
        // 锚缺失清晰报错（Hank P1-B）：anchorPath=Some 但文件不存在时，先给出可读的
        // 失败提示（而非 Source.fromFile 的晦涩 FileNotFoundException 堆栈）
        withClue(s"[MX-ANCHOR] v1 bit 锚文件缺失：$path —— 锚需在未改动 v1 代码上录制，" +
          "v1 已被 v2 替换无法补录；请从 git 历史恢复 v1 录制后放回该路径：") {
          Files.exists(Paths.get(path)) should be(true)
        }
        val anchorTokens = scala.io.Source.fromFile(path).getLines()
          .filter(l => l.nonEmpty && !l.startsWith("#"))
          .flatMap(_.trim.split("\\s+")).toArray
        anchorTokens.length should be(M * N)
        var anchorMismatch = 0
        for (m <- 0 until M; n <- 0 until N) {
          val hwBits = (cResult(m)(n) & 0xFFFFFFFFL).toLong
          val anchorBits = java.lang.Long.parseLong(anchorTokens(m * N + n).stripPrefix("0x"), 16)
          if (hwBits != anchorBits) {
            if (anchorMismatch < 32)
              println(f"[MX-ANCHOR] MISMATCH C($m,$n): hw=0x$hwBits%08X anchor=0x$anchorBits%08X")
            anchorMismatch += 1
          }
        }
        withClue("[MX-ANCHOR] v1 bit 锚逐位相等: ") { anchorMismatch should be(0) }
        println(s"[MX-ANCHOR] bit-exact vs v1 anchor: ${M * N}/${M * N} elems")
      }

      // ===== golden：C = Σ_k mxE4M3(a,scaleA)·mxE5M2(b,scaleB)（二道防线，零改动） =====
      val ref = Array.tabulate(M, N) { (m, n) =>
        (0 until K).map { k =>
          val kb = k / 32
          HiveSimCommon.mxE4M3ToFloat(aByte(m)(k), scaleA(m)(kb)) *
          HiveSimCommon.mxE5M2ToFloat(bByte(k)(n), scaleB(kb)(n))
        }.sum
      }

      var mismatchCnt = 0
      var maxRelErr = 0.0
      for (m <- 0 until M; n <- 0 until N) {
        val r = ref(m)(n)
        val hwBits = (cResult(m)(n) & 0xFFFFFFFFL).toLong
        val hw = HiveSimCommon.fp32BitsToFloat(hwBits).toDouble
        val absErr = math.abs(hw - r)
        val relErr = if (math.abs(r) > 1e-6) absErr / math.abs(r) else absErr
        if (relErr > 1e-2 && absErr > 1e-2) {
          if (mismatchCnt < 64)
            println(f"[MX] MISMATCH C($m,$n): hw=$hw%.6f ref=$r%.6f bits=0x$hwBits%08X")
          mismatchCnt += 1
        }
        if (relErr > maxRelErr) maxRelErr = relErr
      }
      println(f"[MX] Numerical check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
      for (m <- 0 until math.min(M, 4)) {
        val hwStr = (0 until N).map { n =>
          f"${HiveSimCommon.fp32BitsToFloat((cResult(m)(n) & 0xFFFFFFFFL).toLong)}%.3f"
        }.mkString(" ")
        val refStr = (0 until N).map(n => f"${ref(m)(n)}%.3f").mkString(" ")
        println(s"[MX] hw  row$m: $hwStr")
        println(s"[MX] ref row$m: $refStr")
      }

      mismatchCnt should be(0)
    }
    // FST 波形须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
    // 若在块内复制，源文件仍在写入、只能拿到 0 字节空壳（对齐非 MX SimCase 的
    // 块外复制约定，源路径段由 testMangle 逐字锚定 it should 用例名）。
    HiveSimCommon.copyTrace(
      s"build/chiselsim/HiveCoreSimCaseMxSpec/HiveCore-MXFP8-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
      s"$simDir/$waveName",
      "MX"
    )
  }

  // ==========================================================================
  // P5.4 err 负向运行器：写入违约 MX 配置 + EXECUTE，轮询 STATUS(0x08).err 位。
  //   config-err 在 executor sIDLE 拍直接置 errState 并回 sIDLE（不进入计算、
  //   不发 done）；HiveCore hasMx-gated 追加 errFlag 直连（HiveCore.scala P5.4），
  //   故软件通过 REG_READ 0x08 读 statusReg bit[2]=err 即可观测。
  //   断言：err 位在若干拍内置位；done 位与 resp.done 全程不发射。
  // ==========================================================================
  private def runMxErrCase(M: Int, N: Int, K: Int, regs7: Long, caseName: String): Unit = {
    // v2：hasMx 要求 aW/bW>=16（SIMD 双元素/拍）；err 在 sIDLE 拍触发，与位宽无关。
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,
      aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2),
      aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    simulate(new HiveCore(cfg)) { dut =>
      // ---- Reset + DMA 端口拉到安全默认（本用例不喂数据，err 应在 execute 拍触发） ----
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaScaleA.get.req.ready.poke(true.B)
      dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
      dut.io.dmaScaleB.get.req.ready.poke(true.B)
      dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
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

      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0L, regs7)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SCALE_A_BASE, SCALE_B_BASE)

      // EXECUTE（违约配置：executor 在 sIDLE 拍置 errState 回 sIDLE，不发 done）
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ---- 轮询 STATUS(0x08)：期望 err 置位、done 全程不发 ----
      var errSeen = false
      var doneSeen = false
      var respDoneSeen = false
      for (_ <- 0 until 200) {
        // resp.done 全程不应发射（config-err 不走 done 路径）
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean)
          respDoneSeen = true
        if (dut.io.status.done.peek().litToBoolean) doneSeen = true
        if (dut.io.status.err.peek().litToBoolean) errSeen = true
        dut.clock.step()
      }

      // REG_READ 0x08 → statusReg bit[2]=err，bit[1]=done（软件轮询路径对账）
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
      println(f"[MX-ERR $caseName] status=0x$statusVal%08X errBit=$errBit doneBit=$doneBit  errSeen=$errSeen doneSeen=$doneSeen respDoneSeen=$respDoneSeen")

      withClue(s"[$caseName] STATUS.err should be set: ") { errSeen should be(true) }
      withClue(s"[$caseName] STATUS(0x08) bit[2] err should be 1: ") { errBit should be(BigInt(1)) }
      withClue(s"[$caseName] done must NOT be asserted (config-err returns to sIDLE without done): ") {
        respDoneSeen should be(false)
        doneSeen should be(false)
        doneBit should be(BigInt(0))
      }

      // ===== Victor P1 验证：合法再 EXECUTE 后 err 位被清除 =====
      // errState/errFlag 均在 execute 脉冲拍清除（同拍新违约次拍仍可再置位）。
      // 此处改写全合法配置（K=32 对齐 + loadWMode=1）再发 EXECUTE，轮询
      // STATUS.err 应连续拉低（不喂数据，运行停在等 B 供数，不影响 err 清除验证）。
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x07, 32L, 0x5E0L)
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
}

