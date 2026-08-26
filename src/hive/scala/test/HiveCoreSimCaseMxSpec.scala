/** HiveCore MXFP8 双 MX（异构 E4M3 × E5M2）端到端数值仿真用例。
  *
  * 结构与 HiveCoreSimCaseLoadWSpec 同构（loadW 水平加载 + 转置 B），差异：
  *   - aW=bW=8（MX 元素 8-bit），cW=40（复用 fp32 延迟规格化累加器）
  *   - regs(7)=0x5E0：aFmt=MXE4M3(4)、bFmt=MXE5M2(5)、loadWMode=1、mixFmtEn=1、rnd=RNE
  *       aFmt = Cat(regs(7)(6),regs(7)(1,0)) = Cat(1,00) = 0b100 = 4 (E4M3)
  *       bFmt = Cat(regs(7)(7),regs(7)(9,8)) = Cat(1,01) = 0b101 = 5 (E5M2)
  *       loadWMode=bit5=0x20, mixFmtEn=bit10=0x400 → 0x40|0x80|0x100|0x20|0x400 = 0x5E0
  *   - 新增两条 scale 只读 DMA 外部通道（仅 hasMx 暴露）：
  *       dma3Ext = scaleA（8-bit/beat，随激活流动）
  *       dma4Ext = scaleB（scaleRowW=totalN*8=128-bit/beat，列驻留）
  *
  * scale 路由模型（P5 最高风险点，逐拍对齐——见各 gen*Beat 注释）：
  *   - scaleA[m][kb]：executor sLOAD_A 每 aPop.fire pop 一个 8-bit 广播全行，
  *     随激活链 skew 流入。DMA(isA=true) 遍历序 = executor pop 序 =
  *     (round nt) × (kt 外) × (m 内)，共 nTile×kTile×M beat。
  *   - scaleB[kb][n]：executor sLOAD_B 每 K-块起始（curKTile 偶）pop 一个
  *     totalN 向量锁存 scaleBRegs(i)→列 i（globalCol=nTile*totalN+i）。
  *     scaleBDma（isScale 线性遍历）共 nTile×(kTile>>1) beat（每块一向量）。
  *
  * golden：C[m][n] = Σ_k mxE4M3ToFloat(aByte[m][k], scaleA[m][k/32])
  *                       × mxE5M2ToFloat(bByte[k][n], scaleB[k/32][n])
  * 元素值由同一 HiveSimCommon.mxToFloat 重建（硬件/golden 同源），故元素级
  * 逐 bit 相等，唯一偏差是 fp32 累加舍入（远小于 1e-2 容差）。
  *
  * 既有非 MX spec 一律零改动，非 MX 路径 bit-exact 由回归门禁守护。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCaseMxSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  val A_BASE:       Long = 0x00000000L
  val B_BASE:       Long = 0x00100000L
  val C_BASE:       Long = 0x00200000L
  val SCALE_A_BASE: Long = 0x00300000L
  val SCALE_B_BASE: Long = 0x00400000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/mx"

  behavior of "HiveCore MXFP8 GEMM Simulation"

  it should "run MX single-block GEMM M=4 N=16 K=32 E4M3xE5M2" in {
    runMxGemm(M = 4, N = 16, K = 32,
      waveName = "hivecore_mx_sim.fst",
      testMangle = "should-run-MX-single-block-GEMM-M-4-N-16-K-32-E4M3xE5M2")
  }

  it should "run MX multi-pass GEMM M=4 N=16 K=64 2blk 4pass" in {
    runMxGemm(M = 4, N = 16, K = 64,
      waveName = "hivecore_mx_multipass_sim.fst",
      testMangle = "should-run-MX-multi-pass-GEMM-M-4-N-16-K-64-2blk-4pass")
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
  private def runMxGemm(M: Int, N: Int, K: Int, waveName: String, testMangle: String): Unit = {

    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,                 // totalN = 16
      aW = 8, bW = 8, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2),
      aBufferDepth = 2048,
      bBufferDepth = 2048,
      cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    val totalN = cfg.totalN         // 16
    val aEffW  = cfg.aEffW          // 8
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

    // ==================== DMA0 (A buffer) 地址/数据（aW=8） ====================
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)            // = K
    val aColOff = totalN.toLong * (aEffW / 8)        // = totalN
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dma0TotalBeats = expAAddrSeq.size
    def genDma0Beat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aByte(row)(col) else 0
        data = data | (BigInt(value & 0xFF) << (i * aEffW))
      }
      data
    }

    // ==================== DMA2 (B 权重) 地址/数据（转置闭式，bW=8） ==============
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = K.toLong * (cfg.bW / 8)            // = K
    val bColTileOff = totalN.toLong * K * (cfg.bW / 8)
    val kSliceStep = totalN.toLong * (cfg.bW / 8)    // = totalN
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff + (totalN - 1).toLong * bRowOff + kt.toLong * kSliceStep
      for (r2 <- 0 until totalN) expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dma2TotalBeats = expBAddrSeq.size
    def genDma2WeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      val idxInRound = idx % beatsPerRound
      val kt = idxInRound / totalN
      val s = idxInRound % totalN
      val n = nTileIdx * totalN + (totalN - 1 - s)
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val k = kt * totalN + i
        val value = if (k < K && n < N) bByte(k)(n) else 0
        data = data | (BigInt(value & 0xFF) << (i * cfg.bW))
      }
      data
    }

    // ==================== DMA1 (C store) 地址（cW=40） ==========================
    val cRowOff = N.toLong * (cfg.cW / 8)
    val cColOff = totalN.toLong * (cfg.cW / 8)
    val expCAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until aNTiles; m <- 0 until M)
      expCAddrSeq += C_BASE + nt.toLong * (M - 1) * cRowOff + nt.toLong * (nt + 1) / 2 * cColOff + m * cRowOff

    // ==================== DMA3 (scaleA) 数据：8-bit/beat ========================
    // 遍历序（isA=true）= executor pop 序 = (round nt)×(kt 外)×(m 内)。
    // 每 beat = scaleA[m][kb]，kb = (kt*totalN)/32（kt 偶奇同块）。
    val scaleATotalBeats = aNTiles * aKTiles * M
    def genScaleABeat(idx: Int): Int = {
      val kt = (idx % (aKTiles * M)) / M
      val m = idx % M
      val kb = (kt * totalN) / 32
      scaleA(m)(kb) & 0xFF
    }

    // ==================== DMA4 (scaleB) 数据：128-bit/beat ======================
    // isScale 线性遍历，nTile×(kTile>>1) beat；nTile=1 时 beat idx = 块 idx。
    // byte i = scaleB[block][n=i]（列驻留，n<N 有效，否则补 0）。
    val scaleBTotalBeats = nTilesB * (bKTiles / 2)
    require(scaleBTotalBeats == kBlocks * nTilesB, s"scaleB beat 数自检失败: $scaleBTotalBeats vs ${kBlocks * nTilesB}")
    def genScaleBBeat(blockIdx: Int): BigInt = {
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val n = i
        val value = if (n < N) scaleB(blockIdx)(n) else 0
        data = data | (BigInt(value & 0xFF) << (8 * i))
      }
      data
    }

    println(s"[MX] Starting: M=$M N=$N K=$K totalN=$totalN kBlocks=$kBlocks")
    println(s"[MX] beats: dma0(A)=$dma0TotalBeats dma2(B)=$dma2TotalBeats C=${expCAddrSeq.size} scaleA=$scaleATotalBeats scaleB=$scaleBTotalBeats")

    simulate(new HiveCore(cfg)) { dut =>

      var dma0BeatsSent = 0
      var dma1StoreBeats = 0
      var dma2BeatsSent = 0
      var scaleABeatsSent = 0
      var scaleBBeatsSent = 0
      val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dma0Ext.req.ready.poke(true.B)
      dut.io.dma0Ext.rsp.valid.poke(false.B)
      dut.io.dma0Ext.rsp.payload.data.poke(0.U)
      dut.io.dma0Ext.rsp.payload.err.poke(false.B)
      dut.io.dma1Ext.req.ready.poke(true.B)
      dut.io.dma1Ext.rsp.valid.poke(false.B)
      dut.io.dma1Ext.rsp.payload.err.poke(false.B)
      dut.io.dma2Ext.req.ready.poke(true.B)
      dut.io.dma2Ext.rsp.valid.poke(false.B)
      dut.io.dma2Ext.rsp.payload.data.poke(0.U)
      dut.io.dma2Ext.rsp.payload.err.poke(false.B)
      dut.io.dma3Ext.get.req.ready.poke(true.B)
      dut.io.dma3Ext.get.rsp.valid.poke(false.B)
      dut.io.dma3Ext.get.rsp.payload.data.poke(0.U)
      dut.io.dma3Ext.get.rsp.payload.err.poke(false.B)
      dut.io.dma4Ext.get.req.ready.poke(true.B)
      dut.io.dma4Ext.get.rsp.valid.poke(false.B)
      dut.io.dma4Ext.get.rsp.payload.data.poke(0.U)
      dut.io.dma4Ext.get.rsp.payload.err.poke(false.B)
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

        // --- DMA0 (A) ---
        if (dma0BeatsSent < dma0TotalBeats) {
          dut.io.dma0Ext.rsp.valid.poke(true.B)
          dut.io.dma0Ext.rsp.payload.data.poke(genDma0Beat(dma0BeatsSent).U)
          dut.io.dma0Ext.rsp.payload.err.poke(false.B)
          if (dut.io.dma0Ext.rsp.ready.peek().litToBoolean) dma0BeatsSent += 1
        } else {
          dut.io.dma0Ext.rsp.valid.poke(false.B)
          dut.io.dma0Ext.rsp.payload.data.poke(0.U)
        }

        // --- DMA1 (C store) ---
        dut.io.dma1Ext.req.ready.poke(true.B)
        if (dut.io.dma1Ext.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dma1Ext.req.payload.addr.peek().litValue.toLong
          if (dma1StoreBeats < expCAddrSeq.size) {
            withClue(s"DMA1 store beat #$dma1StoreBeats addr mismatch: ") {
              reqAddr should be(expCAddrSeq(dma1StoreBeats))
            }
          }
          val writeData = dut.io.dma1Ext.req.payload.data.peek().litValue
          cWriteLog += ((reqAddr, writeData))
          dma1StoreBeats += 1
          dut.io.dma1Ext.rsp.valid.poke(true.B)
        } else {
          dut.io.dma1Ext.rsp.valid.poke(false.B)
        }

        // --- DMA2 (B 权重) 逐 beat 地址硬对账 ---
        if (dma2BeatsSent < dma2TotalBeats) {
          if (dut.io.dma2Ext.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dma2Ext.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA2 weight beat #$dma2BeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dma2BeatsSent))
            }
            dut.io.dma2Ext.rsp.valid.poke(true.B)
            dut.io.dma2Ext.rsp.payload.data.poke(genDma2WeightBeat(dma2BeatsSent).U)
            dut.io.dma2Ext.rsp.payload.err.poke(false.B)
            dma2BeatsSent += 1
          } else {
            dut.io.dma2Ext.rsp.valid.poke(false.B)
            dut.io.dma2Ext.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dma2Ext.rsp.valid.poke(false.B)
          dut.io.dma2Ext.rsp.payload.data.poke(0.U)
        }

        // --- DMA3 (scaleA) 8-bit：按 beat 序供数（遍历序=pop序） ---
        if (scaleABeatsSent < scaleATotalBeats) {
          dut.io.dma3Ext.get.rsp.valid.poke(true.B)
          dut.io.dma3Ext.get.rsp.payload.data.poke(genScaleABeat(scaleABeatsSent).U)
          dut.io.dma3Ext.get.rsp.payload.err.poke(false.B)
          if (dut.io.dma3Ext.get.rsp.ready.peek().litToBoolean) scaleABeatsSent += 1
        } else {
          dut.io.dma3Ext.get.rsp.valid.poke(false.B)
          dut.io.dma3Ext.get.rsp.payload.data.poke(0.U)
        }

        // --- DMA4 (scaleB) 128-bit：每块一向量 ---
        if (scaleBBeatsSent < scaleBTotalBeats) {
          dut.io.dma4Ext.get.rsp.valid.poke(true.B)
          dut.io.dma4Ext.get.rsp.payload.data.poke(genScaleBBeat(scaleBBeatsSent).U)
          dut.io.dma4Ext.get.rsp.payload.err.poke(false.B)
          if (dut.io.dma4Ext.get.rsp.ready.peek().litToBoolean) scaleBBeatsSent += 1
        } else {
          dut.io.dma4Ext.get.rsp.valid.poke(false.B)
          dut.io.dma4Ext.get.rsp.payload.data.poke(0.U)
        }

        // --- 完成检查 ---
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          println(f"[MX cycle=$cycle%5d] EXECUTE complete! A=$dma0BeatsSent B=$dma2BeatsSent C=$dma1StoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent")
          dut.io.resp.payload.err.peek().litToBoolean should be(false)
          done = true
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dma1Ext.rsp.valid.poke(false.B)

      if (!done) println(s"[MX] WARNING: timeout at $maxCycles cycles (A=$dma0BeatsSent B=$dma2BeatsSent C=$dma1StoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent)")

      done should be(true)
      dma0BeatsSent should be(dma0TotalBeats)
      dma2BeatsSent should be(dma2TotalBeats)
      dma1StoreBeats should be(expCAddrSeq.size)
      scaleABeatsSent should be(scaleATotalBeats)
      scaleBBeatsSent should be(scaleBTotalBeats)

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

      // ===== golden：C = Σ_k mxE4M3(a,scaleA)·mxE5M2(b,scaleB) =====
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
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,
      aW = 8, bW = 8, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2),
      aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    simulate(new HiveCore(cfg)) { dut =>
      // ---- Reset + DMA 端口拉到安全默认（本用例不喂数据，err 应在 execute 拍触发） ----
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dma0Ext.req.ready.poke(true.B)
      dut.io.dma0Ext.rsp.valid.poke(false.B)
      dut.io.dma1Ext.req.ready.poke(true.B)
      dut.io.dma1Ext.rsp.valid.poke(false.B)
      dut.io.dma2Ext.req.ready.poke(true.B)
      dut.io.dma2Ext.rsp.valid.poke(false.B)
      dut.io.dma3Ext.get.req.ready.poke(true.B)
      dut.io.dma3Ext.get.rsp.valid.poke(false.B)
      dut.io.dma4Ext.get.req.ready.poke(true.B)
      dut.io.dma4Ext.get.rsp.valid.poke(false.B)
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
    }
  }
}

