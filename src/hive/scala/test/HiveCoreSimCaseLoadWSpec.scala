/** HiveCore 水平 loadW 加载模式完整仿真用例：M=32, N=32, K=64（K>totalN 多 kTile）, FP16, totalN=16
  *
  * 与垂直模式 HiveCoreSimCase 完全同构的回归门禁，唯一差异：
  *   - regs(7) bit5 = 1（loadWMode=1）：权重经 bPop → hiveAIn 的 a 数据链
  *     水平加载（loadW 脉冲链移位 + 末拍 loadWInLock 广播锁存）
  *   - 内存中 B 矩阵按 N×K 转置存储（行=n、列=k）：
  *       bRowOff     = K * (bW/8)            = 128（K=64）
  *       bColTileOff = totalN * K * (bW/8)   = 2048（K=64）
  *   - dma2 期望地址闭式（两套跳转机制分别建模，不可压成单变量递增）：
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

    // --- DMA0 (A buffer) 期望地址序列与数据：与垂直用例完全相同 ---
    val aKTiles = (K + totalN - 1) / totalN
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong * (cfg.aW / 8)
    val aColOff = totalN.toLong * (aEffW / 8)
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
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }

    // --- DMA2 (B buffer) 期望地址序列：转置布局闭式 ---
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
    val dma2TotalBeats = expBAddrSeq.size
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
    def genDma2WeightBeat(idx: Int): BigInt = {
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

    // --- DMA1 (C store) 期望地址序列：偏移公式与硬件 HiveCore calcConfig
    // 逐字对齐（cRowAddressOffset := regFile.n*(cW/8)、cColTileAddressOffset
    // := totalN*(cW/8)）。注意不复用 4bit 截断的 cStride 寄存器值——硬件
    // 地址生成根本不消费 stride 寄存器，旧写法 cRowOff=(cStride&0xF)=0 会
    // 使 nTile 内 32 个 M 行期望地址全部坍缩，对账形同虚设。
    // 两套跳转机制分别建模（与 HiveCoreDmaWrOnly sTRANSFER_M/sNEXT_N 一致，
    // 不可压成单变量递增 nt*cColOff）：
    //   块内：每拍 + cRowOff（实际 M 行 regFile.m，末行不加步长）
    //   轮间：curAddr += colAddr; colAddr += cColTileOff（colAddr 初值 =
    //     cColTileOff，即首跳 +1 倍、次跳 +2 倍…）；且块内末行不加 rowStep，
    //     残差 (M-1)*cRowOff 随 curAddr 累积进下一块起点
    // 等价闭式：blockStart(nt) = C_BASE + nt*(M-1)*cRowOff
    //                            + nt*(nt+1)/2*cColOff ---
    val cRowOff = N.toLong * (cfg.cW / 8)
    val cColOff = totalN.toLong * (cfg.cW / 8)
    val expCAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (nt <- 0 until aNTiles; m <- 0 until M)
      expCAddrSeq += C_BASE + nt.toLong * (M - 1) * cRowOff +
        nt.toLong * (nt + 1) / 2 * cColOff + m * cRowOff
    // 自检：前两块块起点（nt=0 → C_BASE；nt=1 → C_BASE+(M-1)*cRowOff+cColOff）
    require(expCAddrSeq(0) == C_BASE,
      f"C block0 start 0x${expCAddrSeq(0)}%X mismatch")
    require(expCAddrSeq(M) == C_BASE + (M - 1).toLong * cRowOff + cColOff,
      f"C block1 start 0x${expCAddrSeq(M)}%X mismatch")

    // --- 仿真 ---
    println(s"[HiveCoreSimCaseLoadW] Starting simulation: M=$M, N=$N, K=$K, totalN=$totalN (loadWMode=1)")
    println(s"[HiveCoreSimCaseLoadW] Config: aEffW=$aEffW, cEffW=$cEffW, bRowOff=$bRowOff, bColTileOff=$bColTileOff")

    simulate(new HiveCore(cfg)) { dut =>

      var dma0BeatsSent = 0
      var dma1StoreBeats = 0
      var dma2BeatsSent = 0

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

        // --- DMA0 (A RdOnly) 响应逻辑（与垂直用例同构） ---
        if (dma0BeatsSent < dma0TotalBeats) {
          if (dut.io.dma0Ext.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dma0Ext.req.payload.addr.peek().litValue.toLong
            if (cmdAddr != expAAddrSeq(dma0BeatsSent) && dma0BeatsSent < 8)
              println(f"[cycle=$cycle%6d] DMA0 A beat #$dma0BeatsSent addr MISMATCH: hw=0x$cmdAddr%X exp=0x${expAAddrSeq(dma0BeatsSent)}%X")
          }
          dut.io.dma0Ext.rsp.valid.poke(true.B)
          dut.io.dma0Ext.rsp.payload.data.poke(genDma0Beat(dma0BeatsSent).U)
          dut.io.dma0Ext.rsp.payload.err.poke(false.B)
          if (dut.io.dma0Ext.rsp.ready.peek().litToBoolean) {
            if (dma0BeatsSent < 4)
              println(f"[cycle=$cycle%6d] DMA0 A beat #$dma0BeatsSent delivered")
            dma0BeatsSent += 1
          }
        } else {
          dut.io.dma0Ext.rsp.valid.poke(false.B)
          dut.io.dma0Ext.rsp.payload.data.poke(0.U)
          dut.io.dma0Ext.rsp.payload.err.poke(false.B)
        }

        // --- DMA1 (C WrOnly) 响应逻辑（与垂直用例同构） ---
        dut.io.dma1Ext.req.ready.poke(true.B)
        dut.io.dma1Ext.rsp.valid.poke(false.B)
        dut.io.dma1Ext.rsp.payload.err.poke(false.B)

        if (dut.io.dma1Ext.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dma1Ext.req.payload.addr.peek().litValue.toLong
          // 逐 beat 地址硬对账（与 DMA2 同等待遇）：期望序列按硬件 calcConfig
          // 公式闭式推演，不符直接 fail，防止「按序供数掩盖地址公式 bug」
          if (dma1StoreBeats < expCAddrSeq.size) {
            withClue(s"DMA1 store beat #$dma1StoreBeats addr mismatch: ") {
              reqAddr should be(expCAddrSeq(dma1StoreBeats))
            }
          }
          if (dma1StoreBeats < 4)
            println(f"[cycle=$cycle%6d] DMA1 store req addr=0x$reqAddr%08X")
          val writeData = dut.io.dma1Ext.req.payload.data.peek().litValue
          cWriteLog += ((reqAddr, writeData))
          dma1StoreBeats += 1
        }

        if (dut.io.dma1Ext.req.valid.peek().litToBoolean) {
          dut.io.dma1Ext.rsp.valid.poke(true.B)
        } else {
          dut.io.dma1Ext.rsp.valid.poke(false.B)
        }

        // --- DMA2 (B 权重 RdOnly) 响应逻辑：逐 beat 地址硬对账（转置闭式） ---
        if (dma2BeatsSent < dma2TotalBeats) {
          if (dut.io.dma2Ext.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dma2Ext.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA2 weight beat #$dma2BeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dma2BeatsSent))
            }
            dut.io.dma2Ext.rsp.valid.poke(true.B)
            dut.io.dma2Ext.rsp.payload.data.poke(genDma2WeightBeat(dma2BeatsSent).U)
            dut.io.dma2Ext.rsp.payload.err.poke(false.B)
            if (dma2BeatsSent < 4 || dma2BeatsSent == dma2TotalBeats - 1)
              println(f"[cycle=$cycle%6d] DMA2 weight beat #$dma2BeatsSent addr=0x$cmdAddr%X delivered")
            dma2BeatsSent += 1
          } else {
            dut.io.dma2Ext.rsp.valid.poke(false.B)
            dut.io.dma2Ext.rsp.payload.data.poke(0.U)
            dut.io.dma2Ext.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dma2Ext.rsp.valid.poke(false.B)
          dut.io.dma2Ext.rsp.payload.data.poke(0.U)
          dut.io.dma2Ext.rsp.payload.err.poke(false.B)
        }

        // --- 检查完成 ---
        if (dut.io.resp.valid.peek().litToBoolean) {
          if (dut.io.resp.payload.done.peek().litToBoolean) {
            println(f"[cycle=$cycle%6d] EXECUTE complete! DMA0 beats=$dma0BeatsSent, C store beats=$dma1StoreBeats")
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
          println(f"[cycle=$cycle%6d] progress=$progress busy=$busy err=$err dma0Beats=$dma0BeatsSent storeBeats=$dma1StoreBeats dma2Beats=$dma2BeatsSent")
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dma1Ext.rsp.valid.poke(false.B)

      if (!done) {
        println(s"[HiveCoreSimCaseLoadW] WARNING: Simulation timed out at $maxCycles cycles")
        println(s"  DMA0 beats=$dma0BeatsSent/$dma0TotalBeats, store beats=$dma1StoreBeats, dma2 beats=$dma2BeatsSent/$dma2TotalBeats")
      }

      done should be(true)
      // 三路 DMA 总 beat 数收口断言：捕获「多发 beat」失败模式（DMA 终止
      // 判定失误会多发/少发 beat，逐 beat 对账只能发现错序，发现不了超发）
      dma2BeatsSent should be(dma2TotalBeats)
      dma0BeatsSent should be(dma0TotalBeats)
      dma1StoreBeats should be(expCAddrSeq.size)
    }

    // ===== C 结果重建与数值校验（与垂直用例完全相同：C = A × B） =====
    println(s"[HiveCoreSimCaseLoadW] Writing C result: ${cWriteLog.size} beats captured")

    val cResult = Array.fill(M, N)(BigInt(0))
    var cBeatIdx = 0
    for ((addr, data) <- cWriteLog) {
      val row = cBeatIdx % M
      if (row < M) {
        for (col <- 0 until math.min(totalN, N)) {
          val elem = HiveSimCommon.extractBits(data, cEffW * (col + 1) - 1, cEffW * col)
          val globalCol = (cBeatIdx / M) * totalN + col
          if (globalCol < N) {
            cResult(row)(globalCol) = elem
          }
        }
      }
      cBeatIdx += 1
    }

    HiveSimCommon.writeBigIntMatrixHex(s"$simDir/C_matrix.txt", cResult, cEffW)

    val cFloat = cResult.map(_.map(b => HiveSimCommon.fp32BitsToFloat((b & 0xFFFFFFFFL).toLong)))
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
      f"/${simDir}/hivecore_loadw_sim.fst",
      "HiveCoreSimCaseLoadW"
    )

    mismatchCnt should be(0)
  }
}
