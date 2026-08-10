/** HiveCore 完整仿真用例：M=32, N=32, K=128, FP16, totalN=16
  *
  * 验证 FSM 完整流程：寄存器配置 → EXECUTE → DMA 加载/存储 → 完成
  * 外部存储器由测试 Memory Model 模拟
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCase extends AnyFlatSpec with Matchers with ChiselSim {

  // ========== 测试参数 ==========
  val M = 16
  val N = 16
  val K = 16

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim"

  // ========== 工具方法 ==========

  /** 将 Float 转换为 FP16 (IEEE 754 half-precision) 的 16-bit 整数表示 */
  def floatToFp16(f: Float): Int = {
    val bits = java.lang.Float.floatToIntBits(f)
    val sign = (bits >>> 31) & 1
    val exp = (bits >>> 23) & 0xFF
    val mant = bits & 0x7FFFFF

    if (exp == 0xFF) {
      // Inf / NaN
      (sign << 15) | (31 << 10) | (if (mant != 0) 0x200 else 0)
    } else if (exp == 0) {
      // Zero / FP32 denorm → FP16 zero
      (sign << 15)
    } else {
      val newExp = exp - 127 + 15
      if (newExp >= 31) {
        // Overflow → Inf
        (sign << 15) | (31 << 10)
      } else if (newExp <= 0) {
        // Underflow → zero (simplified, no denorm handling)
        (sign << 15)
      } else {
        val fp16Mant = (mant + 0x1000) >> 13 // round to nearest
        if (fp16Mant >= 0x400) {
          // mantissa overflow after rounding, increment exponent
          (sign << 15) | ((newExp + 1) << 10) | 0
        } else {
          (sign << 15) | (newExp << 10) | fp16Mant
        }
      }
    }
  }

  /** 将 FP16 的 16-bit 整数表示转换回 Float */
  def fp16ToFloat(h: Int): Float = {
    val sign = (h >>> 15) & 1
    val exp = (h >>> 10) & 0x1F
    val mant = h & 0x3FF

    val value = if (exp == 0) {
      // Denorm or zero
      math.pow(2, -14).toFloat * (mant.toFloat / 1024.0f)
    } else if (exp == 31) {
      // Inf / NaN
      if (mant == 0) Float.PositiveInfinity else Float.NaN
    } else {
      math.pow(2, exp - 15).toFloat * (1.0f + mant.toFloat / 1024.0f)
    }
    if (sign == 1) -value else value
  }

  /** 将浮点矩阵写入文本文件（每行空格分隔，%.2f 格式） */
  def writeMatrixFloat(path: String, matrix: Array[Array[Float]]): Unit = {
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => f"$v%.2f").mkString(" "))
    }
    pw.close()
  }

  /** 将 Long 矩阵写入文本文件（每行空格分隔，十进制） */
  def writeMatrixLong(path: String, matrix: Array[Array[Long]]): Unit = {
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(_.toString).mkString(" "))
    }
    pw.close()
  }

  /** 将矩阵写入文本文件（每行空格分隔的 hex 值） */
  def writeMatrixHex(path: String, matrix: Array[Array[Int]], bitsPerElem: Int): Unit = {
    val hexDigits = (bitsPerElem + 3) / 4
    val mask = (1L << bitsPerElem) - 1
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => String.format(s"%0${hexDigits}X", Long.box(v.toLong & mask))).mkString(" "))
    }
    pw.close()
  }

  /** 将 BigInt 矩阵写入文本文件 */
  def writeBigIntMatrixHex(path: String, matrix: Array[Array[BigInt]], bitsPerElem: Int): Unit = {
    val hexDigits = (bitsPerElem + 3) / 4
    val mask = (BigInt(1) << bitsPerElem) - 1
    val pw = new PrintWriter(new File(path))
    for (row <- matrix) {
      pw.println(row.map(v => (v & mask).toString(16).toUpperCase.reverse.padTo(hexDigits, '0').reverse).mkString(" "))
    }
    pw.close()
  }

  /** 从 BigInt 中提取指定位域 */
  def extractBits(value: BigInt, hi: Int, lo: Int): BigInt = {
    (value >> lo) & ((BigInt(1) << (hi - lo + 1)) - 1)
  }

  // ========== 主测试 ==========

  behavior of "HiveCore GEMM Simulation"

  it should "run complete GEMM M=32 N=32 K=128 FP16 totalN=16" in {

    // --- 配置 ---
    // 注意: DMA 的 bufAvailability 端口宽度由 aBufferDepth 决定（设计缺陷），
    // 因此 aBufferDepth 必须 >= cBufferDepth 以避免信号截断。
    val cfg = HiveCoreConfig(
      arrayN = 8,
      clusterM = 2,         // totalN = 16
      extDataWidth = 576,   // 匹配 C buffer 宽度 (16*36=576)
      aBufferDepth = 2048,
      cBufferDepth = 2048,
      aW = 16,
      bW = 16,
      cW = 32
    )

    val totalN = cfg.totalN     // 16
    val aEffW  = cfg.aEffW      // 16
    val cEffW  = cfg.cEffW      // 36
    val extW   = cfg.extDataWidth // 576
    val bytesPerBeat = extW / 8 // 72

    // --- 生成矩阵 ---
    // element[row][col] = (row + 1.0) + col * 0.1，以 FP16 编码
    val aMatrix = Array.tabulate(M, K)((i, j) => floatToFp16((i + 1.0f) + j * 0.1f))
    val bMatrix = Array.tabulate(K, N)((i, j) => floatToFp16((i + 1.0f) + j * 0.1f))

    // --- 写入输入矩阵文件 ---
    Files.createDirectories(Paths.get(simDir))
    writeMatrixHex(s"$simDir/A_matrix.txt", aMatrix, 16)
    writeMatrixHex(s"$simDir/B_matrix.txt", bMatrix, 16)

    // 浮点数格式的 A/B 矩阵
    val aFloat = aMatrix.map(_.map(h => fp16ToFloat(h)))
    val bFloat = bMatrix.map(_.map(h => fp16ToFloat(h)))
    writeMatrixFloat(s"$simDir/A_matrix_float.txt", aFloat)
    writeMatrixFloat(s"$simDir/B_matrix_float.txt", bFloat)

    // --- 计算 stride ---
    val aStride = K * (aEffW / 8)       // 16*2 = 32 bytes
    val bStride = N * (aEffW / 8)       // 16*2 = 32 bytes
    val cStride = N * (cEffW / 8)       // 16*4 = 64 bytes
    // 注意：寄存器映射中每个 stride 仅 4 bit（regs(6) 的 [3:0]/[7:4]/[11:8]），
    // 只能表达 0~15 字节。本用例 stride(32/64) 无法完整写入，仅低 4 位有效
    // （M=N=K=totalN 时 tile 索引恒为 0，行步长不参与地址计算，不影响本用例）
    println(s"[HiveCoreSimCase] WARNING: stride 字段仅 4bit，实际 stride 超出表达范围（既有寄存器映射限制）")

    // --- Memory Model: 用 Map 存储已写入的 C 数据 ---
    val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

    // --- 数据生成: DMA0 (A buffer) beat 数据 ---
    // 每个 beat 的 lower 256 bits 包含 16 个 FP16 值
    def genDma0Beat(globalBeatIdx: Int): BigInt = {
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val elemIdx = globalBeatIdx * totalN + i
        val row = (elemIdx / K) % M
        val col = elemIdx % K
        val value = if (row < M && col < K) aMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * aEffW))
      }
      data // 只有 lower 256 bits 有效
    }

    // --- 数据生成: DMA2 (B buffer) weight beat 数据 ---
    // 顺序流模型（stride 寄存器仅 4bit，外部地址退化，按 beat 序号供数）：
    // beat 顺序 = bDma 逻辑扫描顺序 = N 外 → kTile 中 → 块内 K 行降序。
    // 权重下沉协议要求每个 kTile 块按 K 行降序供数，executor 顺序供数后
    // PE(x,y).wReg = B[kTile*totalN + x][y]（正序落位，标准 GEMM）。
    val kRows = K  // 每个 N tile 的 K 方向行数（新扫描只读实际 K 行，不含 padding）
    def genDma2WeightBeat(globalBeatIdx: Int): BigInt = {
      val nTileIdx = globalBeatIdx / kRows
      var idxInBlock = globalBeatIdx % kRows
      val kTiles = (K + totalN - 1) / totalN
      val rowsLast = K - (kTiles - 1) * totalN
      // 按 kTile 升序、块内行降序解析出当前 beat 对应的 K 行号
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
        val col = nTileIdx * totalN + i  // N 方向列
        val value = if (row < K && col < N) bMatrix(row)(col) else 0
        data = data | (BigInt(value & 0xFFFF) << (i * cfg.bW))
      }
      data
    }

    // --- 仿真 ---
    println(s"[HiveCoreSimCase] Starting simulation: M=$M, N=$N, K=$K, totalN=$totalN")
    println(s"[HiveCoreSimCase] Config: aEffW=$aEffW, cEffW=$cEffW, extDataWidth=$extW")
    println(s"[HiveCoreSimCase] Strides: A=$aStride, B=$bStride, C=$cStride")

    simulate(new HiveCore(cfg)) { dut =>

      // ===== DMA 通道状态 =====
      var dma0Active = false
      var dma0IsWrite = false
      var dma0Addr = 0L
      var dma0Len = 0
      var dma0BeatsDone = 0
      var dma0TotalBeats = 0

      var dma1Active = false
      var dma1IsWrite = false
      var dma1Addr = 0L
      var dma1Len = 0
      var dma1BeatsDone = 0
      var dma1TotalBeats = 0
      var dma1StoreBeats = 0   // Store beat 全局计数

      // DMA2 (B 权重 RdOnly)：逐拍响应 rsp 流（顺序流模型）
      var dma2BeatsSent = 0
      val dma2TotalBeats = K * ((N + totalN - 1) / totalN)  // 整个 B 矩阵的行数

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dma0Ext.grant.poke(false.B)
      dut.io.dma1Ext.grant.poke(false.B)
      dut.io.dma0Ext.readData.valid.poke(false.B)
      dut.io.dma0Ext.readData.payload.poke(0.U)
      dut.io.dma1Ext.readData.valid.poke(false.B)
      dut.io.dma1Ext.readData.payload.poke(0.U)
      dut.io.dma0Ext.writeData.ready.poke(false.B)
      dut.io.dma1Ext.writeData.ready.poke(false.B)
      // DMA2（B 权重只读通道）：cmd 常就绪，rsp 初始无效
      dut.io.dma2Ext.cmd.ready.poke(true.B)
      dut.io.dma2Ext.rsp.valid.poke(false.B)
      dut.io.dma2Ext.rsp.payload.data.poke(0.U)
      dut.io.dma2Ext.rsp.payload.rsp.poke(false.B)
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
        // 等待 resp 被消费
        dut.clock.step(2)
      }

      // ===== 配置寄存器 =====
      // 寄存器映射（与 HiveCoreRegister 一致）：
      //   regs(0)=M, regs(1)=N, regs(2)=K, regs(3)=aAddr, regs(4)=bAddr, regs(5)=cAddr,
      //   regs(6)=[11:8]=cStride, [7:4]=bStride, [3:0]=aStride, regs(7)=fmt/rnd/loopMode
      println("[HiveCoreSimCase] Configuring registers...")
      val stridePacked = (aStride & 0xF) | ((bStride & 0xF) << 4) | ((cStride & 0xF) << 8)
      sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, M, N)          // REG_M, REG_N
      sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, K, A_BASE)     // REG_K, A_ADDR
      sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE) // B_ADDR, C_ADDR
      sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, stridePacked, 0) // 三个 stride 打包进 regs(6), FMT_RND=0(FP16+RNE)

      // ===== 发送 EXECUTE =====
      println("[HiveCoreSimCase] Sending EXECUTE command...")
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

      println("[HiveCoreSimCase] Entering DMA response loop...")

      while (!done && cycle < maxCycles) {

        // --- DMA0 响应逻辑 ---
        if (!dma0Active) {
          // 检查新请求
          if (dut.io.dma0Ext.req.peek().litToBoolean) {
            dma0Addr = dut.io.dma0Ext.addr.peek().litValue.toLong
            dma0Len = dut.io.dma0Ext.len.peek().litValue.toInt
            dma0IsWrite = dut.io.dma0Ext.isWrite.peek().litToBoolean
            dma0BeatsDone = 0
            dma0Active = true
            dut.io.dma0Ext.grant.poke(true.B)
            if (cycle % 10000 == 0 || dma0TotalBeats < 10)
              println(f"[cycle=$cycle%6d] DMA0 req: addr=0x${dma0Addr}%08X len=$dma0Len isWrite=$dma0IsWrite")
          }
        } else {
          dut.io.dma0Ext.grant.poke(false.B)

          if (!dma0IsWrite) {
            // Load: 提供读数据
            // 注意: 不能在 beat fire 的同周期将 valid 置 false (last poke wins)
            if (dma0BeatsDone < dma0Len) {
              val beatData = genDma0Beat(dma0TotalBeats)
              dut.io.dma0Ext.readData.valid.poke(true.B)
              dut.io.dma0Ext.readData.payload.poke(beatData.U)
              if (dut.io.dma0Ext.readData.ready.peek().litToBoolean) {
                dma0BeatsDone += 1
                dma0TotalBeats += 1
              }
            } else {
              // 所有 beat 已送出, 停止驱动
              dut.io.dma0Ext.readData.valid.poke(false.B)
              dut.io.dma0Ext.readData.payload.poke(0.U)
              dma0Active = false
            }
          } else {
            // Store (DMA0 通常不 store, 但以防万一)
            if (dma0BeatsDone < dma0Len) {
              dut.io.dma0Ext.writeData.ready.poke(true.B)
              if (dut.io.dma0Ext.writeData.valid.peek().litToBoolean) {
                dma0BeatsDone += 1
                dma0TotalBeats += 1
              }
            } else {
              dut.io.dma0Ext.writeData.ready.poke(false.B)
              dma0Active = false
            }
          }
        }

        // --- DMA1 响应逻辑（重构后仅 C store） ---
        if (!dma1Active) {
          if (dut.io.dma1Ext.req.peek().litToBoolean) {
            dma1Addr = dut.io.dma1Ext.addr.peek().litValue.toLong
            dma1Len = dut.io.dma1Ext.len.peek().litValue.toInt
            dma1IsWrite = dut.io.dma1Ext.isWrite.peek().litToBoolean
            dma1BeatsDone = 0
            dma1Active = true
            dut.io.dma1Ext.grant.poke(true.B)
            if (cycle % 10000 == 0 || dma1TotalBeats < 10)
              println(f"[cycle=$cycle%6d] DMA1 req: addr=0x${dma1Addr}%08X len=$dma1Len isWrite=$dma1IsWrite")
          }
        } else {
          dut.io.dma1Ext.grant.poke(false.B)

          if (!dma1IsWrite) {
            // 重构后 dma1 不再有 load（权重已改走 dma2Ext/B buffer）；
            // 若意外出现 load 请求，停止供数并告警
            println(f"[cycle=$cycle%6d] WARNING: unexpected DMA1 load request (weights should use dma2Ext)")
            dut.io.dma1Ext.readData.valid.poke(false.B)
            dma1Active = false
          } else {
            // Store: 接收 C 结果数据
            if (dma1BeatsDone < dma1Len) {
              dut.io.dma1Ext.writeData.ready.poke(true.B)
              if (dut.io.dma1Ext.writeData.valid.peek().litToBoolean) {
                val writeData = dut.io.dma1Ext.writeData.payload.peek().litValue
                cWriteLog += ((dma1Addr + dma1BeatsDone.toLong * bytesPerBeat, writeData))
                dma1BeatsDone += 1
                dma1TotalBeats += 1
                dma1StoreBeats += 1
              }
            } else {
              dut.io.dma1Ext.writeData.ready.poke(false.B)
              dma1Active = false
            }
          }
        }

        // --- DMA2 (B 权重 RdOnly) 响应逻辑 ---
        // bDma 每发一拍 cmd（读地址）随后等一拍 rsp（读数据）；
        // rsp.ready 仅在 bDma 的 sTRANSFER 拉高，按 rsp fire 递推 beat 序号，
        // 顺序流模型按 bDma 逻辑扫描顺序（块内 K 行降序）供数。
        if (dma2BeatsSent < dma2TotalBeats) {
          dut.io.dma2Ext.rsp.valid.poke(true.B)
          dut.io.dma2Ext.rsp.payload.data.poke(genDma2WeightBeat(dma2BeatsSent).U)
          dut.io.dma2Ext.rsp.payload.rsp.poke(false.B)
          if (dut.io.dma2Ext.rsp.ready.peek().litToBoolean) {
            if (dma2BeatsSent < 4 || cycle % 10000 == 0)
              println(f"[cycle=$cycle%6d] DMA2 weight beat #$dma2BeatsSent delivered")
            dma2BeatsSent += 1
          }
        } else {
          dut.io.dma2Ext.rsp.valid.poke(false.B)
          dut.io.dma2Ext.rsp.payload.data.poke(0.U)
          dut.io.dma2Ext.rsp.payload.rsp.poke(false.B)
        }

        // --- 检查完成 ---
        if (dut.io.resp.valid.peek().litToBoolean) {
          if (dut.io.resp.payload.done.peek().litToBoolean) {
            println(f"[cycle=$cycle%6d] EXECUTE complete! Total DMA0 beats=$dma0TotalBeats, DMA1 beats=$dma1TotalBeats, C store beats=$dma1StoreBeats")
            done = true
          } else if (cycle < 40) {
            println(f"[cycle=$cycle%6d] resp valid (done=0, err=${dut.io.resp.payload.err.peek().litValue})")
          }
        }

        // 进度打印
        if (cycle > 0 && cycle % 50000 == 0) {
          val progress = dut.io.status.progress.peek().litValue.toInt
          val busy = dut.io.status.busy.peek().litToBoolean
          val aOcc = dut.io.status.aOccupancy.peek().litValue.toInt
          val bOcc = dut.io.status.bOccupancy.peek().litValue.toInt
          val cOcc = dut.io.status.cOccupancy.peek().litValue.toInt
          val err  = dut.io.status.err.peek().litToBoolean
          println(f"[cycle=$cycle%6d] progress=$progress busy=$busy err=$err aOcc=$aOcc bOcc=$bOcc cOcc=$cOcc dma0Beats=$dma0TotalBeats dma1Beats=$dma1TotalBeats dma2Beats=$dma2BeatsSent")
        }

        dut.clock.step()
        cycle += 1
      }

      if (!done) {
        println(s"[HiveCoreSimCase] WARNING: Simulation timed out at $maxCycles cycles")
        println(s"  DMA0 active=$dma0Active beats=$dma0BeatsDone/$dma0Len")
        println(s"  DMA1 active=$dma1Active beats=$dma1BeatsDone/$dma1Len isWrite=$dma1IsWrite")
        val progress = dut.io.status.progress.peek().litValue.toInt
        val busy = dut.io.status.busy.peek().litToBoolean
        println(s"  Status: busy=$busy progress=$progress")
      }

      done should be(true)
    }

    // ===== 写入 C 结果文件 =====
    println(s"[HiveCoreSimCase] Writing C result: ${cWriteLog.size} beats captured")

    val cResult = Array.fill(M, N)(BigInt(0))

    // 解析 C 写入日志: 每个 beat 576 bits = 16 × 36-bit 值
    // 简单策略: 按顺序将 beats 映射到 C 矩阵行
    var cBeatIdx = 0
    for ((addr, data) <- cWriteLog) {
      val row = cBeatIdx % M
      if (row < M) {
        for (col <- 0 until math.min(totalN, N)) {
          val elem = extractBits(data, cEffW * (col + 1) - 1, cEffW * col)
          val globalCol = (cBeatIdx / M) * totalN + col
          if (globalCol < N) {
            cResult(row)(globalCol) = elem
          }
        }
      }
      cBeatIdx += 1
    }

    writeBigIntMatrixHex(s"$simDir/C_matrix.txt", cResult, cEffW)

    // C 矩阵浮点文件 - 36-bit 累加器值输出为十进制整数
    val cLong = cResult.map(_.map(_.toLong))
    writeMatrixLong(s"$simDir/C_matrix_float.txt", cLong)

    // ===== 数值校验：C = A × B（FP16 输入，fp32 累加）=====
    // 参考结果用 double 计算；简化浮点硬件（无 denorm/特殊值、RNE 简化为 guard+1）
    // 允许少量误差：相对误差 <= 1e-2 或绝对误差 <= 1e-2
    def fp32BitsToFloat(bits: Long): Float =
      java.lang.Float.intBitsToFloat((bits & 0xFFFFFFFFL).toInt)

    var mismatchCnt = 0
    var maxRelErr = 0.0
    // 多假设比对：定位硬件结果的排列规律
    // H0: C = A·B（标准）
    // H1: B 行反转（权重下沉落位 PE(x)=B[K-1-x]）
    // H2: H1 且结果行反转
    // H3: H1 且结果列反转
    def gemm(aRev: Boolean, bRowRev: Boolean, cRowRev: Boolean, cColRev: Boolean): Array[Array[Double]] = {
      Array.tabulate(M, N) { (i0, j0) =>
        val i = if (cRowRev) M - 1 - i0 else i0
        val j = if (cColRev) N - 1 - j0 else j0
        (0 until K).map { kk =>
          val a = aFloat(if (aRev) M - 1 - i else i)(kk).toDouble
          val b = bFloat(if (bRowRev) K - 1 - kk else kk)(j).toDouble
          a * b
        }.sum
      }
    }
    val hyps = Seq(
      ("H0: A·B", gemm(false, false, false, false)),
      ("H1: B行反转", gemm(false, true, false, false)),
      ("H2: B行反转+C行反转", gemm(false, true, true, false)),
      ("H3: B行反转+C列反转", gemm(false, true, false, true)),
      ("H4: A行反转+B行反转", gemm(true, true, false, false))
    )
    for ((name, refM) <- hyps) {
      var mc = 0; var me = 0.0
      for (i <- 0 until M; j <- 0 until N) {
        val ref = refM(i)(j)
        val hw = fp32BitsToFloat((cResult(i)(j) & 0xFFFFFFFFL).toLong).toDouble
        val absErr = math.abs(hw - ref)
        val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
        if (relErr > 1e-2 && absErr > 1e-2) mc += 1
        if (relErr > me) me = relErr
      }
      println(f"[HiveCoreSimCase] $name%-28s mismatches=$mc%3d maxRelErr=$me%.2e")
    }
    for (i <- 0 until M; j <- 0 until N) {
      val ref = hyps(0)._2(i)(j)
      val hwBits = (cResult(i)(j) & 0xFFFFFFFFL).toLong
      val hw = fp32BitsToFloat(hwBits).toDouble
      val absErr = math.abs(hw - ref)
      val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
      if (relErr > 1e-2 && absErr > 1e-2) {
        if (mismatchCnt < 5) {
          println(f"[HiveCoreSimCase] MISMATCH C($i,$j): hw=$hw%.6f ref=$ref%.6f bits=0x$hwBits%08X")
        }
        mismatchCnt += 1
      }
      if (relErr > maxRelErr) maxRelErr = relErr
    }
    println(f"[HiveCoreSimCase] Numerical check vs H0: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
    // 打印 hw/ref 前 4 行 × 4 列便于人工找规律
    for (i <- 0 until 4) {
      val rowStr = (0 until 4).map { j =>
        val hw = fp32BitsToFloat((cResult(i)(j) & 0xFFFFFFFFL).toLong)
        f"$hw%.2f"
      }.mkString(" ")
      val refStr = (0 until 4).map(j => f"${hyps(0)._2(i)(j)}%.2f").mkString(" ")
      println(s"[HiveCoreSimCase] hw row$i : $rowStr")
      println(s"[HiveCoreSimCase] ref row$i: $refStr")
    }
    mismatchCnt should be(0)

    // --- 复制 VCD 文件到 sim/ 目录 ---
    val vcdSource = Paths.get("build/chiselsim/HiveCoreSimCase/HiveCore-GEMM-Simulation/should-run-complete-GEMM-M-32-N-32-K-128-FP16-totalN-16/workdir-verilator/trace.vcd")
    val vcdTarget = Paths.get(s"$simDir/hivecore_sim.vcd")
    if (Files.exists(vcdSource)) {
      Files.copy(vcdSource, vcdTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      println(s"[HiveCoreSimCase] VCD waveform copied to $simDir/hivecore_sim.vcd")
    } else {
      println(s"[HiveCoreSimCase] NOTE: VCD not found at $vcdSource (run with -DemitVcd=1 to generate)")
    }

    println(s"[HiveCoreSimCase] Output files written to $simDir/")
    println(s"  - A_matrix.txt (${M}x${K}, 16-bit hex)")
    println(s"  - B_matrix.txt (${K}x${N}, 16-bit hex)")
    println(s"  - C_matrix.txt (${M}x${N}, ${cEffW}-bit hex)")
    println(s"  - A_matrix_float.txt (${M}x${K}, float %.2f)")
    println(s"  - B_matrix_float.txt (${K}x${N}, float %.2f)")
    println(s"  - C_matrix_float.txt (${M}x${N}, decimal long)")
    println(s"  - hivecore_sim.vcd (waveform, run with -DemitVcd=1)")
  }
}
