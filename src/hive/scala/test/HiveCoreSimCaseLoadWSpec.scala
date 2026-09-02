/** HiveCore 水平 loadW 加载模式完整仿真用例：M=32, N=32, K=64（K>totalN 多 kTile）, FP16, totalN=16
  *
  * 与垂直模式 HiveCoreSimCase 完全同构的回归门禁，唯一差异：
  *   - regs(7) bit5 = 1（loadWMode=1）：权重经 bPop → hiveAIn 的 a 数据链
  *     水平加载（loadW 脉冲链移位 + 末拍 loadWInLock 广播锁存）
  *   - 内存中 B 矩阵按 N×K 转置存储（行=n、列=k）：
  *       bRowOff     = K * (bW/8)            = 128（K=64）
  *       bColTileOff = totalN * K * (bW/8)   = 2048（K=64）
  *   - dmaB 期望地址闭式（两套跳转机制分别建模，不可压成单变量递增）：
  *       blockStart(r, kt) = B_BASE + r*bColTileOff + (totalN-1)*bRowOff
  *                           + kt*totalN*(bW/8)
  *     块内逐 beat -bRowOff（降序，为水平加载对角线落位服务）
  *   - 落位映射与垂直模式一致：PE(x,y).wReg = B[kTile*totalN+x][nTile*totalN+y]
  *     → C 期望与垂直用例完全相同（H0: C=A·B 零 mismatch）
  *
  * 既有 spec（HiveCoreSimCase/MultiPass/Probe/HiveCoreSpec/SystolicArray*）
  * 一律零改动——垂直模式行为 bit-exact 由它们回归守护。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCaseLoadWSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  // ========== 测试参数（与垂直 SimCase 一致，C 期望相同） ==========
  val M = 32
  val N = 32
  val K = 64

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/loadw"

  // 工具方法（floatToFp16/fp16ToFloat/writeMatrixFloat/writeBigIntMatrixHex/
  // extractBits/fp32BitsToFloat）统一收敛至公共类 HiveSimCommon（限定调用）

  // ========== 主测试 ==========

  behavior of "HiveCore LoadW-mode GEMM Simulation"

  it should "run loadW-mode GEMM M=32 N=32 K=64 FP16 totalN=16" in {

    // --- 配置（与垂直 SimCase 一致） ---
    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 40
    )

    val totalN = cfg.totalN     // 16
    val aEffW  = cfg.aEffW      // 16
    val cEffW  = cfg.cEffW      // 40

    // --- 生成矩阵（逻辑布局与垂直用例完全相同：bMatrix 为 K×N） ---
    val aMatrix = Array.tabulate(M, K)((i, j) => HiveSimCommon.floatToFp16((i + 1.0f) + j * 0.1f))
    val bMatrix = Array.tabulate(K, N)((i, j) => HiveSimCommon.floatToFp16((i + 1.0f) + j * 0.1f))
    // 内存物理存储：B 按 N×K 转置布局（行=n、列=k），BT(n)(k) = bMatrix(k)(n)
    val bMatrixTransposed = Array.tabulate(N, K)((n, k) => bMatrix(k)(n))

    // --- 写入输入矩阵文件 ---
    Files.createDirectories(Paths.get(simDir))
    HiveSimCommon.writeBigIntMatrixHex(s"$simDir/A_matrix.txt", aMatrix.map(_.map(v => BigInt(v & 0xFFFF))), 16)
    HiveSimCommon.writeBigIntMatrixHex(s"$simDir/B_matrix.txt", bMatrixTransposed.map(_.map(v => BigInt(v & 0xFFFF))), 16)

    val aFloat = aMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => HiveSimCommon.fp16ToFloat(h)))
    HiveSimCommon.writeMatrixFloat(s"$simDir/A_matrix_float.txt", aFloat)
    HiveSimCommon.writeMatrixFloat(s"$simDir/B_matrix_float.txt", bFloat)

    // --- stride（与垂直用例相同：4bit 截断后全 0） ---
    val aStride = K * (aEffW / 8)
    val bStride = N * (aEffW / 8)
    val cStride = N * (cEffW / 8)

    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    // --- DMA-A (A buffer) 期望地址序列与数据：与垂直用例完全相同 ---
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

    // --- DMA-B (B buffer) 期望地址序列：转置布局闭式 ---
    // 硬件遍历结构两模式共用（初值形式、块内降序、两套跳转、终止判定），
    // 仅偏移公式随 loadWMode 切换：
    //   bRowOff     = regFile.k * (bW/8)          = K*(bW/8)   = 128（K=64）
    //   bColTileOff = totalN * regFile.k * (bW/8) = 2048（K=64）
    //   bNextKTileAddressOffset = totalN*(bRowOff + bW/8)      = 16*130 = 2080（K=64）
    // 两套跳转机制的等价闭式（轮内 kTile 跳转 + 轮间 N tile 跳转分别建模）：
    //   blockStart(r, kt) = B_BASE + r*bColTileOff + (totalN-1)*bRowOff
    //                       + kt*totalN*(bW/8)
    // 块内每拍 -bRowOff（降序为水平加载对角线落位服务）
    val bKTiles = (K + totalN - 1) / totalN
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = K.toLong * (cfg.bW / 8)                    // = regFile.k*(bW/8) = 128（K=64）
    val bColTileOff = totalN.toLong * K * (cfg.bW / 8)       // = totalN*k*(bW/8) = 2048（K=64）
    val kSliceStep = totalN.toLong * (cfg.bW / 8)            // k 切片步进 = totalN*(bW/8) = 32
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    val bNRounds = nTilesB
    for (r <- 0 until bNRounds; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff +
        (totalN - 1).toLong * bRowOff + kt.toLong * kSliceStep
      for (r2 <- 0 until totalN)
        expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    // 自检：首四块块起点（块 (r,kt) 位于序列下标 (r*bKTiles+kt)*totalN；
    // K=64/N=32/totalN=16 时 (r0,kt0)/(r0,kt1)/(r1,kt0)/(r1,kt1) =
    // 0x100780/0x1007A0/0x100F80/0x100FA0；依赖 bKTiles >= 2，即 K > totalN）
    require(bKTiles >= 2, s"B 期望序列自检要求 K($K) > totalN($totalN)")
    require(expBAddrSeq(0) == B_BASE + (totalN - 1).toLong * bRowOff,
      f"B block0 start 0x${expBAddrSeq(0)}%X mismatch")
    require(expBAddrSeq(totalN) == B_BASE + (totalN - 1).toLong * bRowOff + kSliceStep,
      f"B block1 start 0x${expBAddrSeq(totalN)}%X mismatch")
    require(expBAddrSeq(bKTiles * totalN) == B_BASE + (totalN - 1).toLong * bRowOff + bColTileOff,
      f"B block(r1,kt0) start 0x${expBAddrSeq(bKTiles * totalN)}%X mismatch")
    require(expBAddrSeq((bKTiles + 1) * totalN) == B_BASE + (totalN - 1).toLong * bRowOff + bColTileOff + kSliceStep,
      f"B block(r1,kt1) start 0x${expBAddrSeq((bKTiles + 1) * totalN)}%X mismatch")

    // beat 数据（转置布局供数）：beat s → 转置行 n = r*totalN + (totalN-1-s)，
    // lane i = bMatrix(kt*totalN+i)(n)（逻辑 K×N 矩阵取值，与垂直用例的
    // 落位映射一致：PE(x,y).wReg = B[kTile*totalN+x][nTile*totalN+y]）
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      val idxInRound = idx % beatsPerRound
      val kt = idxInRound / totalN
      val s = idxInRound % totalN
      val n = nTileIdx * totalN + (totalN - 1 - s)   // 块内 n 降序
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val k = kt * totalN + i
        val value = if (k < K && n < N) bMatrix(k)(n) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- DMA-C (C store) 期望地址序列（P3 迁移：密集布局公共推演源）：
    // 三角累积闭式（金锚，行级结构不动）+ 拍内细分，与 HiveSimCommon.
    // cAddrSeqDense 唯一推演源同源（四套件共用）。输出 outFmt=0（fp32），
    // 256-bit 下 2 拍/行（拍内步进 32B）；行步进 = N×4B、列步进 = totalN×4B。
    // 注意不复用 4bit 截断的 cStride 寄存器值——硬件地址生成根本不消费
    // stride 寄存器。两套跳转机制分别建模（与 HiveCoreDmaWrOnly
    // sTRANSFER_M/sNEXT_N 一致）：块内每拍 + cRowOff，轮间三角累积；
    // 等价闭式：blockStart(nt) = C_BASE + nt*(M-1)*cRowOff + nt*(nt+1)/2*cColOff
    val expCAddrSeq = HiveSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, outFmt = 0, cfg.cDmaExtW)
    val cBeatsPerRow = HiveSimCommon.cBeatsPerRow(outFmt = 0, totalN, cfg.cDmaExtW)  // = 2（256-bit fp32）
    // 自检：前两块块起点（nt=0 → C_BASE；nt=1 → C_BASE+(M-1)*cRowOff+cColOff，
    // 起点下标 = M×beatsPerRow，密集布局下按拍细分）
    val cRowOffChk = N.toLong * 4
    val cColOffChk = totalN.toLong * 4
    require(expCAddrSeq(0) == C_BASE,
      f"C block0 start 0x${expCAddrSeq(0)}%X mismatch")
    require(expCAddrSeq(M * cBeatsPerRow) == C_BASE + (M - 1).toLong * cRowOffChk + cColOffChk,
      f"C block1 start 0x${expCAddrSeq(M * cBeatsPerRow)}%X mismatch")

    // --- 仿真 ---
    println(s"[HiveCoreSimCaseLoadW] Starting simulation: M=$M, N=$N, K=$K, totalN=$totalN (loadWMode=1)")
    println(s"[HiveCoreSimCaseLoadW] Config: aEffW=$aEffW, cEffW=$cEffW, bRowOff=$bRowOff, bColTileOff=$bColTileOff")

    simulate(new HiveCore(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaBBeatsSent = 0

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
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // ===== Helper: 发送命令 =====
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
      // 与垂直用例唯一差异：regs(7) = 0x20 | fmt_rnd —— bit5=loadWMode=1，
      // bit[1:0]=fmt=0(FP16)，bit[4:2]=rnd=0(RNE)。写 0x20 不影响 bit0
      // （clear_done 只判 bit0）
      println("[HiveCoreSimCaseLoadW] Configuring registers (regs(7)=0x20 → loadWMode=1)...")
      val stridePacked = (aStride & 0xF) | ((bStride & 0xF) << 4) | ((cStride & 0xF) << 8)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, stridePacked, 0x20L) // loadWMode=1

      // ===== 发送 EXECUTE =====
      println("[HiveCoreSimCaseLoadW] Sending EXECUTE command...")
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ===== 主仿真循环 =====
      var done = false
      var cycle = 0
      val maxCycles = 5000

      println("[HiveCoreSimCaseLoadW] Entering DMA response loop...")

      while (!done && cycle < maxCycles) {

        // --- DMA-A (A RdOnly) 响应逻辑（与垂直用例同构） ---
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            if (cmdAddr != expAAddrSeq(dmaABeatsSent) && dmaABeatsSent < 8)
              println(f"[cycle=$cycle%6d] DMA-A A beat #$dmaABeatsSent addr MISMATCH: hw=0x$cmdAddr%X exp=0x${expAAddrSeq(dmaABeatsSent)}%X")
          }
          dut.io.dmaA.rsp.valid.poke(true.B)
          dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
          if (dut.io.dmaA.rsp.ready.peek().litToBoolean) {
            if (dmaABeatsSent < 4)
              println(f"[cycle=$cycle%6d] DMA-A A beat #$dmaABeatsSent delivered")
            dmaABeatsSent += 1
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C (C WrOnly) 响应逻辑（与垂直用例同构） ---
        dut.io.dmaC.req.ready.poke(true.B)
        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaC.rsp.payload.err.poke(false.B)

        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          // 逐 beat 地址硬对账（与 DMA-B 同等待遇）：期望序列按硬件 calcConfig
          // 公式闭式推演，不符直接 fail，防止「按序供数掩盖地址公式 bug」
          if (dmaCStoreBeats < expCAddrSeq.size) {
            withClue(s"DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
              reqAddr should be(expCAddrSeq(dmaCStoreBeats))
            }
          }
          if (dmaCStoreBeats < 4)
            println(f"[cycle=$cycle%6d] DMA-C store req addr=0x$reqAddr%08X")
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          cWriteLog += ((reqAddr, writeData))
          dmaCStoreBeats += 1
        }

        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-B (B 权重 RdOnly) 响应逻辑：逐 beat 地址硬对账（转置闭式） ---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            if (dmaBBeatsSent < 4 || dmaBBeatsSent == dmaBTotalBeats - 1)
              println(f"[cycle=$cycle%6d] DMA-B weight beat #$dmaBBeatsSent addr=0x$cmdAddr%X delivered")
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

        // --- 检查完成 ---
        if (dut.io.resp.valid.peek().litToBoolean) {
          if (dut.io.resp.payload.done.peek().litToBoolean) {
            println(f"[cycle=$cycle%6d] EXECUTE complete! DMA-A beats=$dmaABeatsSent, C store beats=$dmaCStoreBeats")
            // 完成响应必须不带 err 标记：防止 FSM 异常提前退出（errState
            // 路径）被下游数值校验兜底掩盖
            dut.io.resp.payload.err.peek().litToBoolean should be(false)
            done = true
          } else if (cycle < 40) {
            println(f"[cycle=$cycle%6d] resp valid (done=0, err=${dut.io.resp.payload.err.peek().litValue})")
          }
        }

        if (cycle > 0 && cycle % (maxCycles / 10) == 0) {
          val progress = dut.io.status.progress.peek().litValue.toInt
          val busy = dut.io.status.busy.peek().litToBoolean
          val err = dut.io.status.err.peek().litToBoolean
          println(f"[cycle=$cycle%6d] progress=$progress busy=$busy err=$err dmaABeats=$dmaABeatsSent storeBeats=$dmaCStoreBeats dmaBBeats=$dmaBBeatsSent")
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)

      if (!done) {
        println(s"[HiveCoreSimCaseLoadW] WARNING: Simulation timed out at $maxCycles cycles")
        println(s"  DMA-A beats=$dmaABeatsSent/$dmaATotalBeats, store beats=$dmaCStoreBeats, dmaB beats=$dmaBBeatsSent/$dmaBTotalBeats")
      }

      done should be(true)
      // 三路 DMA 总 beat 数收口断言：捕获「多发 beat」失败模式（DMA 终止
      // 判定失误会多发/少发 beat，逐 beat 对账只能发现错序，发现不了超发）
      dmaBBeatsSent should be(dmaBTotalBeats)
      dmaABeatsSent should be(dmaATotalBeats)
      dmaCStoreBeats should be(expCAddrSeq.size)
    }

    // ===== C 结果重建与数值校验（与垂直用例完全相同：C = A × B） =====
    println(s"[HiveCoreSimCaseLoadW] Writing C result: ${cWriteLog.size} beats captured")

    // ===== C 重建（P3 迁移：密集拍公共解析器，256-bit 拍内 8 个 fp32 元素） =====
    val cResult = HiveSimCommon.parseDenseCWriteLog(
      cWriteLog.toSeq, outFmt = 0, m = M, n = N, totalN = totalN, cDmaExtW = cfg.cDmaExtW)

    HiveSimCommon.writeBigIntMatrixHex(s"$simDir/C_matrix.txt",
      cResult.map(_.map(v => BigInt(v & 0xFFFFFFFFL))), 32)

    val cFloat = cResult.map(_.map(b => HiveSimCommon.fp32BitsToFloat(b & 0xFFFFFFFFL)))
    HiveSimCommon.writeMatrixFloat(s"$simDir/C_matrix_float.txt", cFloat)

    // H0: C = A·B（落位映射与垂直模式一致，期望完全相同）
    val refH0 = Array.tabulate(M, N) { (i, j) =>
      (0 until K).map(kk => aFloat(i)(kk).toDouble * bFloat(kk)(j).toDouble).sum
    }

    var mismatchCnt = 0
    var maxRelErr = 0.0
    for (i <- 0 until M; j <- 0 until N) {
      val ref = refH0(i)(j)
      val hwBits = (cResult(i)(j) & 0xFFFFFFFFL).toLong
      val hw = HiveSimCommon.fp32BitsToFloat(hwBits).toDouble
      val absErr = math.abs(hw - ref)
      val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
      if (relErr > 1e-2 && absErr > 1e-2) {
        if (mismatchCnt < 5) {
          println(f"[HiveCoreSimCaseLoadW] MISMATCH C($i,$j): hw=$hw%.6f ref=$ref%.6f bits=0x$hwBits%08X")
        }
        mismatchCnt += 1
      }
      if (relErr > maxRelErr) maxRelErr = relErr
    }
    println(f"[HiveCoreSimCaseLoadW] Numerical check vs H0: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
    for (i <- 0 until 4) {
      val rowStr = (0 until 4).map { j =>
        val hw = HiveSimCommon.fp32BitsToFloat((cResult(i)(j) & 0xFFFFFFFFL).toLong)
        f"$hw%.2f"
      }.mkString(" ")
      val refStr = (0 until 4).map(j => f"${refH0(i)(j)}%.2f").mkString(" ")
      println(s"[HiveCoreSimCaseLoadW] hw row$i : $rowStr")
      println(s"[HiveCoreSimCaseLoadW] ref row$i: $refStr")
    }

    println(s"[HiveCoreSimCaseLoadW] Output files written to $simDir/")

    // --- 复制 FST 波形到 sim/ 目录（与 HiveCoreSimCase 机制对齐：
    // CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
    // sbt "testOnly *HiveCoreSimCaseLoadWSpec -- -DemitFst=1"） ---
    // 锚定：源路径段 should-run-* 目录名由上方 it should 用例名 mangle 而来
    // （空格与 `=` 转 `-`，其余字符保留），二者必须逐字一致；
    // 改动用例名时须同步修改此源路径，否则拷贝将静默走 NOTE 分支。
    HiveSimCommon.copyTrace(
      "build/chiselsim/HiveCoreSimCaseLoadWSpec/HiveCore-LoadW-mode-GEMM-Simulation/should-run-loadW-mode-GEMM-M-32-N-32-K-64-FP16-totalN-16/workdir-verilator/trace.fst",
      s"$simDir/hivecore_loadw_sim.fst",
      "HiveCoreSimCaseLoadW"
    )

    mismatchCnt should be(0)
  }
}
