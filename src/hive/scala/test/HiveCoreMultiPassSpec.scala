/** HiveCore 多 pass partial sum 回灌端到端测试（K > totalN, FP16）
  *
  * K > totalN=16 → 多个 K pass，验证非首 K pass 计算时 C buffer 中的
  * partial sum 经 cPop → cpsRegs skew → hivePsumIn 回灌进阵列作为累加基底：
  *   pass0 (k=0..15) : psumIn=0，结果 push 进 C buffer（M 行）
  *   passN (k=N*16..) : pop M 行 partial sum 回灌 + push M 行新和（占用恒定）
  *
  * 用例：
  *   - M=16, N=16, K=32：单 M/N tile + 2 个满 K pass（最小回灌路径）
  *   - M=32, N=16, K=32：2 M tile × 2 K pass（weight-stationary M 循环 ×
  *     多 pass，C buffer 驻留 2×totalN 行，验证容量公式 mTiles×totalN）
  *   - M=16, N=16, K=40：kTiles=3，尾 pass curTileK=8 —— 覆盖 remainK
  *     截断、变长权重等待（bOccupancy>=8）、变长回灌窗口
  *   - M=16, N=32, K=32：nTiles=2 × kTiles=2 —— 覆盖 N tile 推进时
  *     partial sum 已排空、flushC 语义（仅 curKTile==0）、cStoreAddr 按
  *     curNTile 推进、bDma 多 N tile 扫描顺序
  *   - M=24, N=16, K=40：尾 M tile（8 行）× 尾 K pass —— 覆盖
  *     curTileM < totalN 的 pop/push 计数与 de-skew 截断
  *   - M=24, N=32, K=32：尾 M tile × 多 N tile —— 覆盖 aDma 内建 nTile
  *     轮次与 regFile.m 边界感知（innerRows=M）交叠：每轮仍只供实际 M 行
  *
  * 外部 DMA 模型与 HiveCoreSimCase 一致：dma0Ext 供 A（cmd/rsp 顺序流）、
  * dma2Ext 供权重（N 外 → kTile 升序 → 块内 K 行降序）、dma1Ext 收 C store
  * （逐 beat req/grant + writeData 握手）。
  * 附加防护：dma0 每拍 cmd 地址与软件侧按 RdOnly 地址公式
  * （regAAddr + m*aStride + kTile*totalN*(aEffW/8)，stride 寄存器写 0）
  * 推导的期望值逐拍对账（tile-aware，不用 globalBeatIdx/totalN 推算），
  * 防止“按序供数掩盖地址公式 bug”。
  * 数值验证：软件侧 double 计算期望 C，与硬件 fp32 结果逐元素比较
  * （相对误差 <= 1e-2 且绝对误差 <= 1e-2 视为失配）。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HiveCoreMultiPassSpec extends AnyFlatSpec with Matchers with ChiselSim {

  // ========== FP16 工具（与 HiveCoreSimCase 一致） ==========
  def floatToFp16(f: Float): Int = {
    val bits = java.lang.Float.floatToIntBits(f)
    val sign = (bits >>> 31) & 1
    val exp = (bits >>> 23) & 0xFF
    val mant = bits & 0x7FFFFF
    if (exp == 0xFF) {
      (sign << 15) | (31 << 10) | (if (mant != 0) 0x200 else 0)
    } else if (exp == 0) {
      (sign << 15)
    } else {
      val newExp = exp - 127 + 15
      if (newExp >= 31) (sign << 15) | (31 << 10)
      else if (newExp <= 0) (sign << 15)
      else {
        val fp16Mant = (mant + 0x1000) >> 13
        if (fp16Mant >= 0x400) (sign << 15) | ((newExp + 1) << 10)
        else (sign << 15) | (newExp << 10) | fp16Mant
      }
    }
  }
  def fp16ToFloat(h: Int): Float = {
    val sign = (h >>> 15) & 1
    val exp = (h >>> 10) & 0x1F
    val mant = h & 0x3FF
    val value = if (exp == 0) {
      math.pow(2, -14).toFloat * (mant.toFloat / 1024.0f)
    } else if (exp == 31) {
      if (mant == 0) Float.PositiveInfinity else Float.NaN
    } else {
      math.pow(2, exp - 15).toFloat * (1.0f + mant.toFloat / 1024.0f)
    }
    if (sign == 1) -value else value
  }
  def extractBits(value: BigInt, hi: Int, lo: Int): BigInt =
    (value >> lo) & ((BigInt(1) << (hi - lo + 1)) - 1)
  def fp32BitsToFloat(bits: Long): Float =
    java.lang.Float.intBitsToFloat((bits & 0xFFFFFFFFL).toInt)

  behavior of "HiveCore multi-pass K-split GEMM (partial sum feedback)"

  it should "compute exact C=A*B for M=16 N=16 K=32 (2 K passes) in FP16" in {
    runMultiPassCase(M = 16, N = 16, K = 32)
  }

  it should "compute exact C=A*B for M=32 N=16 K=32 (2 M tiles x 2 K passes) in FP16" in {
    runMultiPassCase(M = 32, N = 16, K = 32)
  }

  it should "handle tail K tile: M=16 N=16 K=40 (kTiles=3, last pass K=8) in FP16" in {
    runMultiPassCase(M = 16, N = 16, K = 40)
  }

  it should "handle multi N tiles x multi K passes: M=16 N=32 K=32 in FP16" in {
    runMultiPassCase(M = 16, N = 32, K = 32)
  }

  it should "handle tail M tile x tail K pass: M=24 N=16 K=40 in FP16" in {
    runMultiPassCase(M = 24, N = 16, K = 40)
  }

  it should "handle tail M tile x multi N tiles (nTile-round overlap): M=24 N=32 K=32 in FP16" in {
    runMultiPassCase(M = 24, N = 32, K = 32)
  }

  /** 参数化多 pass 用例（M/N/K 任意正整数，要求 K > totalN 触发多 pass） */
  def runMultiPassCase(M: Int, N: Int, K: Int): Unit = {

    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 32
    )
    val totalN = cfg.totalN      // 16
    val aEffW  = cfg.aEffW       // 16
    val cEffW  = cfg.cEffW       // 32（cW=32 显式传入，cEffW=max(cW,bW)）
    // 各 DMA 通道外部数据位宽各自匹配 buffer 行宽：aExtW=bExtW=256，cExtW=512
    require(K > totalN, "本测试要求 K > totalN 以触发多 pass")
    val mTiles = (M + totalN - 1) / totalN
    val nTiles = (N + totalN - 1) / totalN
    val kTiles = (K + totalN - 1) / totalN
    def tileLen(remain: Int): Int = math.min(remain, totalN)

    val A_BASE: Long = 0x00000000L
    val B_BASE: Long = 0x00100000L
    val C_BASE: Long = 0x00200000L

    // ========== 生成矩阵（FP16 编码） ==========
    // A[i][k] = (i+1) + k*0.1, B[k][j] = (k+1) + j*0.1 —— 值域内 FP16 精度足够，
    // 且随下标单调变化，可区分行列错位/回灌错位等排布错误
    val aMatrix = Array.tabulate(M, K)((i, j) => floatToFp16((i + 1.0f) + j * 0.1f))
    val bMatrix = Array.tabulate(K, N)((i, j) => floatToFp16((i + 1.0f) + j * 0.1f))
    val aFloat = aMatrix.map(_.map(fp16ToFloat))
    val bFloat = bMatrix.map(_.map(fp16ToFloat))

    // 软件期望：fp16 元素乘、fp32 累加（double 计算参考值，误差容限覆盖）
    val cExpected = Array.tabulate(M, N) { (i, j) =>
      (0 until K).map(k => aFloat(i)(k).toDouble * bFloat(k)(j).toDouble).sum
    }

    // ========== Executor 循环展开（N 外 → K 中 → M 内） ==========
    // aDma 内建 nTile 轮次（execute 单次启动自主供满 nTiles 轮）：每轮按
    // kTile 块顺序遍历，块内供实际 M 行（无 padding）。软件侧按与 RdOnly
    // 相同的地址公式（regAAddr + m*aStride + kt*totalN*(aEffW/8)，stride
    // 寄存器写 0 故行步长项为 0）逐 beat 推导期望 cmd 地址流（各轮地址相同）。
    // 务必延续 tile-aware 教训：不用 globalBeatIdx/totalN 推算，按块累计
    val beatsPerScan = kTiles * M
    val expDma0Addrs = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until nTiles; kt <- 0 until kTiles; m <- 0 until M) {
      expDma0Addrs += A_BASE + m.toLong * 0 + kt.toLong * totalN * (aEffW / 8)
    }
    // C store 期望地址逐 beat 序列：内 M（实际 M 行，cStride 寄存器写 0）外 N，
    // 按 curNTile 推进
    val expStoreAddrs = (for (nt <- 0 until nTiles; r <- 0 until M) yield
      C_BASE + nt.toLong * totalN * (cEffW / 8) + r.toLong * 0).toVector

    // ========== DMA0 (A buffer) beat 数据 ==========
    // 供数顺序 = aDma 自主扫描顺序 = nTile 轮外 → kTile 中 → 实际 M 行内；
    // 每拍一行 = 当前 kTile 的 K 列切片 totalN 个元素。
    // 边界 tile 只供实际 M 行（无 padding），不能按 globalBeatIdx/totalN 推算
    def genDma0Beat(globalBeatIdx: Int): BigInt = {
      val idx = globalBeatIdx % beatsPerScan  // 各 nTile 轮供数内容相同
      val kt = idx / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val col = kt * totalN + i
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data
    }

    // ========== DMA2 (B 权重) beat 数据 ==========
    // 顺序流模型（与 HiveCoreSimCase.genDma2WeightBeat 相同，扩展多 N tile）：
    // beat 顺序 = bDma 逻辑扫描顺序 = N 外 → kTile 升序 → 块内 K 行降序。
    // 权重下沉协议要求每个 kTile 块按 K 行降序供数，executor 顺序供数后
    // PE(x,y).wReg = B[kTile*totalN + x][nTile*totalN + y]（正序落位，标准 GEMM）
    def genDma2WeightBeat(globalBeatIdx: Int): BigInt = {
      val nTileIdx = globalBeatIdx / K
      var idxInBlock = globalBeatIdx % K
      val rowsLast = K - (kTiles - 1) * totalN
      var row = 0
      var kt = 0
      var resolved = false
      while (kt < kTiles && !resolved) {
        val rows = if (kt == kTiles - 1) rowsLast else totalN
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

    // ========== 仿真 ==========
    println(s"[MultiPass] Starting simulation: M=$M, N=$N, K=$K, totalN=$totalN (mTiles=$mTiles, nTiles=$nTiles, kTiles=$kTiles)")

    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    simulate(new HiveCore(cfg)) { dut =>

      // ===== DMA 通道状态 =====
      // DMA0 (A RdOnly)：逐拍 rsp 供数 + 逐拍 cmd 地址对账；
      // 多 N tile 用例下 aDma 内建 nTile 轮次连续供 A 共 nTiles 轮
      var dma0BeatsSent = 0
      var dma0Cmds = 0
      val dma0TotalBeats = nTiles * kTiles * M

      // DMA1 (C WrOnly)：逐 beat 接收 store + 逐拍地址对账
      var dma1StoreBeats = 0
      var dma1CurAddr = 0L

      var dma2BeatsSent = 0
      val dma2TotalBeats = K * nTiles  // 整个 B 矩阵的行数（每个 N tile K 行）

      // ===== Reset（Chisel 7 EphemeralSimulator 需手动 reset） =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      // DMA0（A 只读通道）：cmd 常就绪，rsp 初始无效
      dut.io.dma0Ext.req.ready.poke(true.B)
      dut.io.dma0Ext.rsp.valid.poke(false.B)
      dut.io.dma0Ext.rsp.payload.data.poke(0.U)
      dut.io.dma0Ext.rsp.payload.err.poke(false.B)
      // DMA1（C 写回通道，req{addr,data}/rsp{err}）：req 常接收，rsp 恒无错
      dut.io.dma1Ext.req.ready.poke(true.B)
      dut.io.dma1Ext.rsp.valid.poke(false.B)
      dut.io.dma1Ext.rsp.payload.err.poke(false.B)
      // DMA2（B 权重只读通道）：req 常就绪，rsp 初始无效
      dut.io.dma2Ext.req.ready.poke(true.B)
      dut.io.dma2Ext.rsp.valid.poke(false.B)
      dut.io.dma2Ext.rsp.payload.data.poke(0.U)
      dut.io.dma2Ext.rsp.payload.err.poke(false.B)
      dut.clock.step(2)
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
      // 测试侧外部存储模型按 beat 序号供数（不解析地址），实际行步长不参与
      // 数据正确性；但 dma0 每拍 cmd 地址仍按 RdOnly 公式对账（stride 寄存器
      // 写 0，对账公式中行步长项同取 0，见 expDma0Addrs）。stride 寄存器映射
      // 仅 4bit，无法表达实际 stride，全置 0（与 OnesProbe/SimCase 处理一致）
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)            // REG_M, REG_N
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)       // REG_K, A_ADDR
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)  // B_ADDR, C_ADDR
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, 0, 0)            // stride=0, fmt=FP16 rnd=RNE

      // ===== 发送 EXECUTE =====
      println("[MultiPass] Sending EXECUTE command...")
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // ===== 主仿真循环: DMA 响应 + 等待完成 =====
      var done = false
      var cycle = 0
      val maxCycles = 500000

      while (!done && cycle < maxCycles) {

        // --- DMA0 (A RdOnly) 响应：逐拍 rsp 供数 + 逐拍 cmd 地址对账 ---
        // cmd fire 拍（valid && ready，ready 常开）对账地址，与软件侧按
        // RdOnly 地址公式推导的期望逐拍比较，防止“按序供数掩盖地址公式 bug”
        if (dut.io.dma0Ext.req.valid.peek().litToBoolean &&
            dut.io.dma0Ext.req.ready.peek().litToBoolean) {
          val cmdAddr = dut.io.dma0Ext.req.payload.addr.peek().litValue.toLong
          withClue(s"DMA0 cmd #$dma0Cmds addr mismatch: ") {
            if (dma0Cmds >= expDma0Addrs.size)
              fail(s"DMA0 cmd #$dma0Cmds exceeds expected ${expDma0Addrs.size} cmds")
            cmdAddr should be(expDma0Addrs(dma0Cmds))
          }
          if (dma0Cmds < 4)
            println(f"[MultiPass] [cycle=$cycle%6d] DMA0 cmd #$dma0Cmds addr=0x$cmdAddr%08X")
          dma0Cmds += 1
        }
        if (dma0BeatsSent < dma0TotalBeats) {
          dut.io.dma0Ext.rsp.valid.poke(true.B)
          dut.io.dma0Ext.rsp.payload.data.poke(genDma0Beat(dma0BeatsSent).U)
          dut.io.dma0Ext.rsp.payload.err.poke(false.B)
          if (dut.io.dma0Ext.rsp.ready.peek().litToBoolean) {
            dma0BeatsSent += 1
          }
        } else {
          dut.io.dma0Ext.rsp.valid.poke(false.B)
          dut.io.dma0Ext.rsp.payload.data.poke(0.U)
          dut.io.dma0Ext.rsp.payload.err.poke(false.B)
        }

        // --- DMA1 (C WrOnly) 响应：逐 beat store + 逐拍地址对账 ---

        if (dut.io.dma1Ext.req.fire.peek().litToBoolean) {
          val reqAddr = dut.io.dma1Ext.req.payload.addr.peek().litValue.toLong
          withClue(s"DMA1 store beat #$dma1StoreBeats addr mismatch: ") {
            if (dma1StoreBeats >= expStoreAddrs.size)
              fail(s"DMA1 store beat #$dma1StoreBeats exceeds expected ${expStoreAddrs.size} beats")
            reqAddr should be(expStoreAddrs(dma1StoreBeats))
          }
          if (dma1StoreBeats < 4)
            println(f"[MultiPass] [cycle=$cycle%6d] DMA1 store req addr=0x$reqAddr%08X")
          dma1CurAddr = reqAddr
        }
        
        if (dut.io.dma1Ext.req.fire.peek().litToBoolean) {
          // store payload 采样在 step 前（历史教训）
          val writeData = dut.io.dma1Ext.req.payload.data.peek().litValue
          cWriteLog += ((dma1CurAddr, writeData))
          dma1StoreBeats += 1
        }

        // --- DMA2 (B 权重 RdOnly) 响应 ---
        if (dma2BeatsSent < dma2TotalBeats) {
          dut.io.dma2Ext.rsp.valid.poke(true.B)
          dut.io.dma2Ext.rsp.payload.data.poke(genDma2WeightBeat(dma2BeatsSent).U)
          dut.io.dma2Ext.rsp.payload.err.poke(false.B)
          if (dut.io.dma2Ext.rsp.ready.peek().litToBoolean) {
            if (dma2BeatsSent < 2 || dma2BeatsSent == K || dma2BeatsSent == dma2TotalBeats - 1)
              println(f"[MultiPass] [cycle=$cycle%6d] DMA2 weight beat #$dma2BeatsSent delivered")
            dma2BeatsSent += 1
          }
        } else {
          dut.io.dma2Ext.rsp.valid.poke(false.B)
          dut.io.dma2Ext.rsp.payload.data.poke(0.U)
          dut.io.dma2Ext.rsp.payload.err.poke(false.B)
        }

        // --- 检查完成 ---
        if (dut.io.resp.valid.peek().litToBoolean) {
          if (dut.io.resp.payload.done.peek().litToBoolean) {
            println(f"[MultiPass] [cycle=$cycle%6d] EXECUTE complete! DMA0 beats=$dma0BeatsSent (cmds=$dma0Cmds), DMA1 beats=$dma1StoreBeats, DMA2 beats=$dma2BeatsSent")
            done = true
          } else if (cycle < 40) {
            println(f"[MultiPass] [cycle=$cycle%6d] resp valid (done=0, err=${dut.io.resp.payload.err.peek().litValue})")
          }
        }

        dut.clock.step()
        cycle += 1
      }

      done should be(true)
      // 多 pass 特征断言：aDma 供满 nTiles 轮 A，每轮 kTiles 块各实际 M 行
      dma0Cmds should be(nTiles * kTiles * M)
      dma0BeatsSent should be(nTiles * kTiles * M)
      dma2BeatsSent should be(dma2TotalBeats)
      dma1StoreBeats should be(nTiles * M)
      // err 状态应为 false（executor errState 未被触发，含 cPush 兜底路径）
      dut.io.status.err.peek().litToBoolean should be(false)
    }

    // ===== 解析 C store beat → C 矩阵 =====
    println(s"[MultiPass] C result: ${cWriteLog.size} store beats captured")
    cWriteLog.size should be(nTiles * M)

    val cResult = Array.fill(M, N)(BigInt(0))
    for (((_, data), beat) <- cWriteLog.zipWithIndex) {
      // C buffer 严格 FIFO：store beat 顺序 = push 顺序 =
      // nTile0 全部 M 行 → nTile1 全部 M 行 → ...（每组内 M tile0 → M tile1）
      val nTile = beat / M
      val row = beat % M
      for (col <- 0 until totalN) {
        val globalCol = nTile * totalN + col
        if (globalCol < N) {
          cResult(row)(globalCol) = extractBits(data, cEffW * (col + 1) - 1, cEffW * col)
        }
      }
    }

    // ===== 数值校验：逐元素与期望比较 =====
    var mismatchCnt = 0
    var maxRelErr = 0.0
    for (i <- 0 until M; j <- 0 until N) {
      val hwBits = (cResult(i)(j) & 0xFFFFFFFFL).toLong
      val hw = fp32BitsToFloat(hwBits).toDouble
      val ref = cExpected(i)(j)
      val absErr = math.abs(hw - ref)
      val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
      if (relErr > 1e-2 && absErr > 1e-2) {
        if (mismatchCnt < 8) {
          println(f"[MultiPass] MISMATCH C($i,$j): hw=$hw%.6f ref=$ref%.6f bits=0x$hwBits%08X")
        }
        mismatchCnt += 1
      }
      if (relErr > maxRelErr) maxRelErr = relErr
    }
    println(f"[MultiPass] Numerical check (M=$M N=$N K=$K): ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
    for (i <- 0 until math.min(4, M)) {
      val rowStr = (0 until math.min(4, N)).map { j =>
        f"${fp32BitsToFloat((cResult(i)(j) & 0xFFFFFFFFL).toLong)}%.2f"
      }.mkString(" ")
      val refStr = (0 until math.min(4, N)).map(j => f"${cExpected(i)(j)}%.2f").mkString(" ")
      println(s"[MultiPass] hw row$i : $rowStr")
      println(s"[MultiPass] ref row$i: $refStr")
    }
    mismatchCnt should be(0)
  }
}
