/** HiveCoreExecutor — Weight-Stationary 自动 Tiling GEMM 执行器
  *
  * EXECUTE 触发后，根据寄存器组中的 M/N/K 维度和阵列能力 (totalN×totalN)
  * 自动切块 tiling，采用 N→K→M 循环顺序实现 weight-stationary 复用：
  *   - 外层 N 循环: 每个 N tile 对应一列输出
  *   - 中层 K 循环: 每个 (N,K) 对加载一次权重
  *   - 内层 M 循环: 权重驻留，连续处理所有 M tiles
  *
  * 内部 FSM 依次完成：
  *   - DMA 加载权重 → 写入阵列 (每个 N,K 对一次)
  *   - DMA 加载激活 → GEMM 计算 (每个 M tile 一次，权重复用)
  *   - 处理 partial sum（K > totalN 时多 pass 累加）
  *   - DMA 写回结果 (所有 M tiles 完成后一次性写回)
  *
  * aIn skew 实现说明：
  *   根据 HiveComb 契约，外部需按「行 i 延迟 i 拍」错峰供数。
  *   本模块通过移位寄存器 aRegs 实现 skew。
  */

import chisel3._
import chisel3.util._

class HiveCoreExecutor(cfg: HiveCoreConfig) extends Module {

  val io = IO(new Bundle {
    // 控制
    val execute  = Input(Bool())        // 脉冲启动
    val busy     = Output(Bool())
    val done     = Output(Bool())       // 脉冲完成
    val err      = Output(Bool())

    // 寄存器输入（由顶层寄存器组驱动）
    val regM       = Input(UInt(32.W))
    val regN       = Input(UInt(32.W))
    val regK       = Input(UInt(32.W))
    val regFmt     = Input(DataFormat())
    val regRnd     = Input(RoundingMode())
    val regAAddr   = Input(UInt(cfg.addrWidth.W))
    val regBAddr   = Input(UInt(cfg.addrWidth.W))
    val regCAddr   = Input(UInt(cfg.addrWidth.W))
    val regAStride = Input(UInt(32.W))
    val regBStride = Input(UInt(32.W))
    val regCStride = Input(UInt(32.W))

    // DMA0 控制（A buffer）
    val dma0Start  = Output(Bool())
    val dma0IsLoad = Output(Bool())
    val dma0Addr   = Output(UInt(cfg.addrWidth.W))
    val dma0Len    = Output(UInt(16.W))
    val dma0Busy   = Input(Bool())
    val dma0Done   = Input(Bool())

    // DMA1 控制（C buffer）
    val dma1Start  = Output(Bool())
    val dma1IsLoad = Output(Bool())
    val dma1Addr   = Output(UInt(cfg.addrWidth.W))
    val dma1Len    = Output(UInt(16.W))
    val dma1Busy   = Input(Bool())
    val dma1Done   = Input(Bool())

    // Scratchpad
    val aPop  = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    val cPop  = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPush = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val flushA = Output(Bool())
    val flushC = Output(Bool())

    // HiveComb 驱动
    val hiveAIn     = Output(Vec(cfg.totalN, UInt(cfg.aEffW.W)))
    val hivePsumIn  = Output(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveLoadH   = Output(Bool())
    val hiveLoadV   = Output(Bool())
    val hiveValidIn = Output(Bool())
    val hiveFmtIn   = Output(DataFormat())
    val hiveRndIn   = Output(RoundingMode())
    val hiveClear   = Output(Bool())
    val hiveCOut    = Input(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveValidOut = Input(Vec(cfg.totalN, Bool()))

    // 进度
    val progress = Output(UInt(16.W))
  })

  // ==========================================================================
  // FSM 状态编码 (Weight-Stationary: N→K→M loop order)
  // ==========================================================================
  val sIDLE            = 0.U(4.W)
  val sCONFIG_TILE     = 1.U(4.W)
  val sLOAD_WEIGHT_DMA = 2.U(4.W)
  val sLOAD_WEIGHT_PE  = 3.U(4.W)
  val sLOAD_A_DMA      = 4.U(4.W)
  val sCOMPUTE         = 5.U(4.W)
  val sDRAIN           = 6.U(4.W)
  val sNEXT_M_TILE     = 7.U(4.W)
  val sSTORE_C_DMA     = 8.U(4.W)
  val sNEXT_NK_TILE    = 9.U(4.W)
  val sDONE            = 10.U(4.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // Tiling 寄存器
  // ==========================================================================
  val totalN = cfg.totalN

  val mTiles   = Reg(UInt(16.W))
  val nTiles   = Reg(UInt(16.W))
  val kTiles   = Reg(UInt(16.W))
  val curMTile = Reg(UInt(16.W))
  val curNTile = Reg(UInt(16.W))
  val curKTile = Reg(UInt(16.W))
  val doneTiles = Reg(UInt(16.W))

  // 当前 tile 参数
  val curTileM = Reg(UInt(16.W))  // 实际行数（边界处理）
  val curTileN = Reg(UInt(16.W))  // 实际列数
  val curTileK = Reg(UInt(16.W))  // 实际 K 长度

  // DMA 地址
  val bTileAddr = Reg(UInt(cfg.addrWidth.W))
  val aTileAddr = Reg(UInt(cfg.addrWidth.W))
  val cStoreAddr = Reg(UInt(cfg.addrWidth.W))

  // 计算相关
  val counter     = RegInit(0.U(16.W))
  val totalCycles = Reg(UInt(16.W))

  // Skew 移位寄存器
  val aRegs   = Reg(Vec(cfg.totalN, UInt((cfg.totalN * cfg.aEffW).W)))
  val cpsRegs = Reg(Vec(cfg.totalN, UInt((cfg.totalN * cfg.cEffW).W)))

  // C 输出收集 — 流式输出 de-skew（反向错峰对齐）
  // 结果行以波前形式流出底部行簇：行 m 在列 c 于 (m + 2*totalN - 1 + c) 拍就绪，
  // 各列逐列号错峰 1 拍。用 ShiftRegister 将列 c 延迟 (totalN-1-c) 拍，
  // 使所有列对齐到最后一列（c = totalN-1）的时序后，一次性 push 一整行。
  val cDeSkewed = (0 until cfg.totalN).map { c =>
    val delay = cfg.totalN - 1 - c
    if (delay > 0) ShiftRegister(io.hiveCOut(c), delay)
    else io.hiveCOut(c)
  }
  // 对齐后的 valid：行 m 完整和 de-skew 后于 (m + 2*totalN) 拍对齐；
  // validOut(0)（底部 ci=1 簇列 0）于拍 2*arrayN 起有效（ci skew + y 链寄存），
  // 故需再延迟 totalN 拍才与行 0 数据对齐时刻（拍 2*totalN）一致。
  // 注意延迟必须为 totalN 而非 totalN-1，否则 alignCnt=m 捕获行 m-1（整体错位一行）
  val alignedValid = ShiftRegister(io.hiveValidOut(0), cfg.totalN)
  // alignedValid 窗口内已出现的行数（行 m 在 alignCnt=m 拍推出），
  // 用于截断尾部零填充拍，每 tile 只 push curTileM 行
  val alignCnt     = RegInit(0.U(16.W))
  val drainCounter = RegInit(0.U(16.W))

  // DMA 启动延迟（保证 start 为单拍脉冲）
  val dmaStarted = RegInit(false.B)

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.busy := state =/= sIDLE
  io.done := false.B
  io.err  := false.B

  io.dma0Start  := false.B
  io.dma0IsLoad := true.B
  io.dma0Addr   := 0.U
  io.dma0Len    := 0.U
  io.dma1Start  := false.B
  io.dma1IsLoad := true.B
  io.dma1Addr   := 0.U
  io.dma1Len    := 0.U

  io.aPop.ready  := false.B
  io.cPop.ready  := false.B
  io.cPush.valid   := false.B
  io.cPush.payload := 0.U

  io.flushA := false.B
  io.flushC := false.B

  for (i <- 0 until cfg.totalN) {
    io.hiveAIn(i)    := 0.U
    io.hivePsumIn(i) := 0.U
  }
  io.hiveLoadH   := false.B
  io.hiveLoadV   := false.B
  io.hiveValidIn := false.B
  io.hiveFmtIn   := DataFormat.INT16
  io.hiveRndIn   := RoundingMode.RNE
  io.hiveClear   := false.B

  io.progress := doneTiles

  // ==========================================================================
  // C 输出收集逻辑（仅 sCOMPUTE 阶段，valid 窗口已覆盖全部结果流出）
  // cDeSkewed + alignedValid 将各列错峰数据对齐后，一次性 push 一整行到 C buffer。
  // ==========================================================================
  when((state === sCOMPUTE) && alignedValid) {
    alignCnt := alignCnt + 1.U
    when(alignCnt < curTileM) {
      io.cPush.valid   := true.B
      io.cPush.payload := Cat(cDeSkewed.reverse)
    }
  }

  // ==========================================================================
  // FSM 主体 (Weight-Stationary: N→K→M)
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待 execute 脉冲 ---
    is(sIDLE) {
      when(io.execute) {
        // 计算 tile 数
        mTiles   := (io.regM + totalN.U - 1.U) / totalN.U
        nTiles   := (io.regN + totalN.U - 1.U) / totalN.U
        kTiles   := (io.regK + totalN.U - 1.U) / totalN.U
        curMTile := 0.U
        curNTile := 0.U
        curKTile := 0.U
        doneTiles := 0.U
        state    := sCONFIG_TILE
      }
    }

    // --- sCONFIG_TILE: 计算当前 (N,K) tile 的权重参数和地址 ---
    is(sCONFIG_TILE) {
      // 实际 tile 尺寸（处理边界）
      val remainN = io.regN - curNTile * totalN.U
      val remainK = io.regK - curKTile * totalN.U
      curTileN := Mux(remainN > totalN.U, totalN.U, remainN)(15, 0)
      curTileK := Mux(remainK > totalN.U, totalN.U, remainK)(15, 0)

      // 计算权重 DMA 地址（字节地址）
      // B_tile_addr = regBAddr + curKTile*totalN*regBStride + curNTile*totalN*(aEffW/8)
      bTileAddr := io.regBAddr + curKTile * totalN.U * io.regBStride + curNTile * totalN.U * (cfg.aEffW / 8).U

      // 计算 C store 地址（所有 M tiles 的起始地址）
      // cStoreAddr = regCAddr + curNTile*totalN*(cEffW/8)
      cStoreAddr := io.regCAddr + curNTile * totalN.U * (cfg.cEffW / 8).U

      // flush buffers
      io.flushA := true.B
      io.flushC := curKTile === 0.U  // 第一个 K pass 清空 C buffer；后续保留 partial sums

      dmaStarted := false.B
      state := sLOAD_WEIGHT_DMA
    }

    // --- sLOAD_WEIGHT_DMA: DMA 加载权重到 C buffer ---
    is(sLOAD_WEIGHT_DMA) {
      io.dma1IsLoad := true.B
      io.dma1Addr   := bTileAddr
      io.dma1Len    := curTileK  // 每个 buffer entry 含一整行权重（totalN 列）

      when(!dmaStarted) {
        io.dma1Start := true.B
        dmaStarted   := true.B
      }

      when(io.dma1Done) {
        counter    := 0.U
        dmaStarted := false.B
        state      := sLOAD_WEIGHT_PE
      }
    }

    // --- sLOAD_WEIGHT_PE: 从 C buffer pop 权重 → 加载到 HiveComb ---
    is(sLOAD_WEIGHT_PE) {
      io.hiveLoadV := io.cPop.fire
      io.hiveLoadH := true.B
      io.hiveFmtIn := io.regFmt
      io.hiveRndIn := io.regRnd

      // 从 cPop 读取权重（仅前 curTileK 拍 pop）
      val loadCycles = (curTileK << 1) + totalN.U
      io.cPop.ready := !counter(0) && (counter >> 1) < curTileK

      when(io.cPop.fire) {
        for (i <- 0 until cfg.totalN) {
          io.hivePsumIn(i) := io.cPop.payload(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
        }
      }

      // 时钟周期计数器
      counter := counter + 1.U

      // 等待足够拍数让 loadV 波完全传播排空后再进入下一阶段。
      // 权重经 psumReg 逐行寄存下沉后，各 PE 的 psumReg 残留末行权重值，
      // 排空完成当拍发一拍 clear 清零 psum 链（不影响 wReg），再进入计算
      when(counter >= loadCycles) {
        io.hiveClear := true.B
        counter    := 0.U
        dmaStarted := false.B
        curMTile   := 0.U  // 重置 M tile 计数，开始 M 循环
        state      := sLOAD_A_DMA
      }
    }

    // --- sLOAD_A_DMA: DMA 加载 A 数据到 A buffer ---
    is(sLOAD_A_DMA) {
      // 动态计算当前 M tile 的参数
      val remainM = io.regM - curMTile * totalN.U
      val thisTileM = Mux(remainM > totalN.U, totalN.U, remainM)(15, 0)

      // 计算当前 (M, K) tile 的 A 地址
      // A_tile_addr = regAAddr + curMTile*totalN*regAStride + curKTile*totalN*(aEffW/8)
      val thisATileAddr = io.regAAddr + curMTile * totalN.U * io.regAStride + curKTile * totalN.U * (cfg.aEffW / 8).U

      io.dma0IsLoad := true.B
      io.dma0Addr   := thisATileAddr
      io.dma0Len    := thisTileM

      // 第一个周期: flush A buffer 并启动 DMA
      when(!dmaStarted) {
        io.flushA    := true.B  // 安全清空 A buffer
        io.dma0Start := true.B
        dmaStarted   := true.B
        // 锁存当前 tile 参数到寄存器
        curTileM  := thisTileM
        aTileAddr := thisATileAddr
      }

      when(io.dma0Done) {
        // 进入计算
        counter := 0.U
        // 拍数预算（三段）：
        //   curTileM            : valid 供数窗口（每拍一行 M）
        //   + 2*(totalN-1)      : valid 保持，让波尾流过全部 totalN 层 PE
        //                         （行 m 在底部列 c 于 m+2*(totalN-1)+c 拍才就绪，
        //                          valid 提前撤除会丢弃尾部行的累加）
        //   + (totalN-1)        : de-skew 对齐链排空，最后一行完成 push
        val validCycles = thisTileM + (2 * (totalN - 1)).U
        totalCycles := validCycles + (totalN - 1).U
        dmaStarted  := false.B
        for (i <- 0 until cfg.totalN) {
          aRegs(i)    := 0.U
          cpsRegs(i)  := 0.U
        }
        alignCnt := 0.U
        state := sCOMPUTE
      }
    }

    // --- sCOMPUTE: GEMM 计算 ---
    is(sCOMPUTE) {
      val needPartialSum = curKTile > 0.U
      // valid 必须持续到波尾排空：curTileM（供数）+ 2*(totalN-1)（波传播），
      // 否则行 curTileM-1 尚未流到底部即停止累加，尾部行结果丢失
      io.hiveValidIn := counter < curTileM + (2 * (totalN - 1)).U
      io.hiveFmtIn   := io.regFmt
      io.hiveRndIn   := io.regRnd

      // A buffer skew（移位寄存器）
      io.aPop.ready := counter < curTileM

      when(io.aPop.fire) {
        for (i <- cfg.totalN - 1 to 1 by -1) {
          aRegs(i) := aRegs(i - 1)
        }
        aRegs(0) := io.aPop.payload
      }.otherwise {
        when(counter >= curTileM) {
          for (i <- cfg.totalN - 1 to 1 by -1) {
            aRegs(i) := aRegs(i - 1)
          }
          aRegs(0) := 0.U
        }
      }

      // aIn 输出
      for (i <- 0 until cfg.totalN) {
        io.hiveAIn(i) := aRegs(i)((i + 1) * cfg.aEffW - 1, i * cfg.aEffW)
      }

      // Partial sum 处理
      when(needPartialSum) {
        io.cPop.ready := counter < curTileM
        when(io.cPop.fire) {
          for (i <- cfg.totalN - 1 to 1 by -1) {
            cpsRegs(i) := cpsRegs(i - 1)
          }
          cpsRegs(0) := io.cPop.payload
        }.otherwise {
          when(counter >= curTileM) {
            for (i <- cfg.totalN - 1 to 1 by -1) {
              cpsRegs(i) := cpsRegs(i - 1)
            }
            cpsRegs(0) := 0.U
          }
        }
        for (i <- 0 until cfg.totalN) {
          io.hivePsumIn(i) := cpsRegs(i)((i + 1) * cfg.cEffW - 1, i * cfg.cEffW)
        }
      }.otherwise {
        for (i <- 0 until cfg.totalN) {
          io.hivePsumIn(i) := 0.U
        }
      }

      // counter 递增
      when(io.aPop.fire || counter >= curTileM) {
        counter := counter + 1.U
      }

      // 完成条件
      when(counter >= totalCycles) {
        state        := sDRAIN
        drainCounter := 0.U
      }
    }

    // --- sDRAIN: 等待剩余输出收集完毕 ---
    is(sDRAIN) {
      io.hiveValidIn := false.B

      for (i <- cfg.totalN - 1 to 1 by -1) {
        aRegs(i) := aRegs(i - 1)
      }
      aRegs(0) := 0.U

      val anyNewValid = io.hiveValidOut.asUInt.orR
      when(anyNewValid) {
        drainCounter := 0.U
      }.otherwise {
        drainCounter := drainCounter + 1.U
      }

      // 超时退出: 连续 2*totalN 周期无新 valid → 强制退出
      when(drainCounter >= (2 * totalN).U) {
        state := sNEXT_M_TILE
      }
    }

    // --- sNEXT_M_TILE: 推进 M tile 索引（weight-stationary 核心） ---
    is(sNEXT_M_TILE) {
      val nextMTile = curMTile + 1.U
      when(nextMTile < mTiles) {
        // 还有更多 M tiles，复用当前权重
        curMTile   := nextMTile
        dmaStarted := false.B
        state      := sLOAD_A_DMA
      }.otherwise {
        // 所有 M tiles 完成
        when(curKTile === (kTiles - 1.U)) {
          // 最后一个 K pass，结果写回外部
          dmaStarted := false.B
          state      := sSTORE_C_DMA
        }.otherwise {
          // 还有更多 K passes，partial sums 留在 C buffer
          state := sNEXT_NK_TILE
        }
      }
    }

    // --- sSTORE_C_DMA: DMA 写回所有 M tiles 的 C 结果到外部 ---
    is(sSTORE_C_DMA) {
      io.dma1IsLoad := false.B
      io.dma1Addr   := cStoreAddr
      io.dma1Len    := io.regM(15, 0)  // 所有 M 行的结果

      when(!dmaStarted) {
        io.dma1Start := true.B
        dmaStarted   := true.B
      }

      when(io.dma1Done) {
        doneTiles  := doneTiles + 1.U
        dmaStarted := false.B

        // 推进 N tile
        val nextNTile = curNTile + 1.U
        when(nextNTile >= nTiles) {
          // 所有 N tiles 完成
          state := sDONE
        }.otherwise {
          // 下一个 N tile：重置 K 和 M
          curNTile := nextNTile
          curKTile := 0.U
          curMTile := 0.U
          state    := sCONFIG_TILE
        }
      }
    }

    // --- sNEXT_NK_TILE: 推进 K tile（partial sums 保留在 C buffer） ---
    is(sNEXT_NK_TILE) {
      curKTile := curKTile + 1.U
      curMTile := 0.U
      state    := sCONFIG_TILE
    }

    // --- sDONE: 完成 ---
    is(sDONE) {
      io.done := true.B
      state   := sIDLE
    }
  }
}
