/** HiveCoreExecutor — Weight-Stationary 自动 Tiling GEMM 执行器
  *
  * EXECUTE 触发后，根据寄存器组中的 M/N/K 维度和阵列能力 (totalN×totalN)
  * 自动切块 tiling，采用 N→K→M 循环顺序实现 weight-stationary 复用：
  *   - 外层 N 循环: 每个 N tile 对应一列输出
  *   - 中层 K 循环: 每个 (N,K) 对加载一次权重
  *   - 内层 M 循环: 权重驻留，连续处理所有 M tiles
  *
  * A/C 数据搬运由自主 DMA 完成（与 bDma 同一范式）：
  *   - A: RdOnly DMA 在 execute 拍单次启动，内建 nTile 轮次（nTile 外 →
  *     kTile 中 → M 内），自主供满 nTiles 轮 A 后自然停止；本模块 A 侧
  *     为纯 pop 消费者，在 sLOAD_A_DMA 仅等待 aOccupancy 积累到位后顺序 pop
  *   - C: WrOnly DMA 在 execute 启动后自主待命，本模块在 sSTORE_C_DMA
  *     拉高 cStoreGate 开门，等 cOccupancy 排空即视为写回完成
  *
  * 内部 FSM 依次完成：
  *   - 等待权重就绪 → 写入阵列 (每个 N,K 对一次，bDma 自主扫描供数)
  *   - 等待 A 就绪 → GEMM 计算 (每个 M tile 一次，权重复用)
  *   - 处理 partial sum（K > totalN 时多 pass 累加）
  *   - 门控写回结果 (末 K pass，所有 M tiles 完成后)
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

    // 寄存器输入（由顶层寄存器组驱动）：全部寄存器统一经 io.regFile 引用。
    // 此前的拆分端口（regM/regN/regK/regBAddr/regBStride/regFmt/regRnd 及
    // regAAddr/regCAddr/regAStride）均与 regFile 完全冗余（顶层驱动只是
    // 截位/补零/类型视图），已全部移除
    val regFile = Input(HiveCoreRegs(cfg))

    // A buffer 自主 DMA（RdOnly）：execute 单次启动全自主供数，
    // 本模块 A 侧无控制输出，仅占用量感知
    val aOccupancy = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))

    // C buffer 自主 DMA（WrOnly）控制：store 门控（电平）+ 占用量感知
    val cStoreGate = Output(Bool())                    // 末 K pass store 窗口拉高
    val cOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))

    // Scratchpad
    val aPop  = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    // B buffer（权重专用）：RdOnly DMA 自主写入，本模块按行 pop
    val bPop  = slave(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val bOccupancy = Input(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val cPop  = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPush = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val flushA = Output(Bool())
    val flushB = Output(Bool())
    val flushC = Output(Bool())

    // HiveComb 驱动
    val hiveAIn     = Output(Vec(cfg.totalN, UInt(cfg.aEffW.W)))
    val hivePsumIn  = Output(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveLoadH   = Output(Bool())
    val hiveLoadV   = Output(Bool())
    val hiveValidIn = Output(Bool())
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
  val errState = RegInit(false.B)

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
  val depthOfCBufferUsed = Reg(UInt(16.W))

  // DMA 地址
  val bTileAddr = Reg(UInt(cfg.addrWidth.W))

  // 计算相关
  val counter     = RegInit(0.U(16.W))
  val totalCycles = Reg(UInt(16.W))
  val loadedRows  = RegInit(0.U(16.W))  // sLOAD_WEIGHT_PE 已喂入的权重行数

  // Skew 移位寄存器
  // A 供数为对角线流式：行 m 的第 i 个元素在拍 m+i 经 hiveAIn(i) 进入
  // 第 i 行 PE，垂直 psum 流与行索引严格同步（见 sCOMPUTE 注释）。
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

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.busy := state =/= sIDLE
  io.done := false.B
  io.err  := errState

  io.cStoreGate := false.B

  io.aPop.ready  := false.B
  io.bPop.ready  := false.B
  io.cPop.ready  := false.B
  io.cPush.valid   := false.B
  io.cPush.payload := 0.U

  io.flushA := false.B
  io.flushB := false.B
  io.flushC := false.B

  for (i <- 0 until cfg.totalN) {
    io.hiveAIn(i)    := 0.U
    io.hivePsumIn(i) := 0.U
  }
  io.hiveLoadH   := false.B
  io.hiveLoadV   := false.B
  io.hiveValidIn := false.B
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
      // 时序不变量（多 pass partial sum 依赖的隐性假设）：
      // 非首 pass 中 cPop 回灌窗口（counter < curTileM）恒早于 cPush 窗口
      // （约 2*totalN 拍后结果才流到底部），pop/push 等量交替使 C buffer
      // 占用恒定在 mTiles×totalN。FSM 不引入 stall，若 push 时 buffer 满
      // （ready=0）会静默丢数：仿真由下方 assert 捕获，综合后由紧随的
      // errState 兜底（置错并经 sDONE 退出，避免丢数后继续计算产生静默错误结果）
      assert(io.cPush.ready, "HiveCoreExecutor: cPush valid but C buffer not ready (pop-before-push invariant violated, partial sum lost)")
    }
  }
  // cPush 违约硬件级防护（综合后兜底，与上方仿真 assert 对应）：
  // 容量公式放宽后生产环境不能再依赖仿真断言，违约拍置 errState 并经
  // sDONE 退出（而非直回 sIDLE），保证顶层仍能收到带 err 标记的 resp。
  // 仅改状态机，不反压 cPush 驱动逻辑（不引入 stall）
  when((state === sCOMPUTE) && io.cPush.valid && !io.cPush.ready) {
    errState := true.B
    state    := sDONE
  }

  // ==========================================================================
  // FSM 主体 (Weight-Stationary: N→K→M)
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待 execute 脉冲 ---
    is(sIDLE) {
      when(io.execute) {
        // 计算 tile 数。注意：mTiles/kTiles 为 Reg，本拍 := 下一拍才生效，
        // 故 depthOfCBufferUsed 必须用本拍推导的 newMTiles/newKTiles 计算，
        // 否则会采样到未初始化寄存器的随机旧值，误触发容量错误回 idle
        val newMTiles = (io.regFile.m(15, 0) + totalN.U - 1.U) / totalN.U
        val newNTiles = (io.regFile.n(15, 0) + totalN.U - 1.U) / totalN.U
        val newKTiles = (io.regFile.k(15, 0) + totalN.U - 1.U) / totalN.U
        mTiles   := newMTiles
        nTiles   := newNTiles
        kTiles   := newKTiles
        // 容量检查：C buffer 真实驻留量只有一组 partial sum（mTiles×totalN 行）。
        // K > totalN 时非首 pass 的 pop（回灌）与 push 等量交替，占用恒定，
        // 故 kTiles 不乘进公式（旧公式 mTiles×kTiles×totalN 会在 K 稍大时误报容量错误）
        depthOfCBufferUsed := newMTiles * totalN.U
        curMTile := 0.U
        curNTile := 0.U
        curKTile := 0.U
        doneTiles := 0.U
        errState := false.B
        // execute 起始拍清空 A/B buffer（RdOnly DMA 首个 push 至少晚一拍，安全）；
        // flushA 仅允许在此拍拉高：aDma 内建 nTile 轮次连续供数，任何
        // per-tile flush 都会丢弃已预取数据且 RdOnly 不重发 → 死锁。
        // A 侧全局排空不变量仿真检查（仅仿真可见）：上次 execute 中 aDma
        // push 总行数 = nTiles×kTiles×m = executor 消费总行数，新 execute
        // 拍 FIFO 必已排空；非 0 说明供/消行数失配（padding 混入/丢数）
        assert(io.aOccupancy === 0.U, "HiveCoreExecutor: A buffer not drained at execute pulse (previous run aDma push rows != executor consume rows)")
        io.flushA := true.B
        io.flushB := true.B
        state    := sCONFIG_TILE
      }
    }

    // --- sCONFIG_TILE: 计算当前 (N,K) tile 的权重参数和地址 ---
    is(sCONFIG_TILE) {
      // 实际 tile 尺寸（处理边界）
      val remainN = io.regFile.n(15, 0) - curNTile * totalN.U
      val remainK = io.regFile.k(15, 0) - curKTile * totalN.U
      curTileN := Mux(remainN > totalN.U, totalN.U, remainN)(15, 0)
      curTileK := Mux(remainK > totalN.U, totalN.U, remainK)(15, 0)

      // 计算权重 DMA 地址（字节地址）
      // B_tile_addr = regBAddr + curKTile*totalN*regBStride + curNTile*totalN*(aEffW/8)
      bTileAddr := io.regFile.bAddr + curKTile * totalN.U * io.regFile.bStride.pad(16) + curNTile * totalN.U * (cfg.aEffW / 8).U

      // flushC：第一个 K pass 清空 C buffer；后续保留 partial sums
      io.flushC := curKTile === 0.U

      // 容量错误也经 sDONE 退出，保证顶层 resp 带 err 标记产生（而非永久等待）
      when(depthOfCBufferUsed > cfg.cBufferDepth.U){
        state := sDONE
        errState := true.B
      }.otherwise{
        state := sLOAD_WEIGHT_DMA 
      }
    }

    // --- sLOAD_WEIGHT_DMA: 等待 B buffer 就绪（不再驱动 DMA 加载权重） ---
    // 权重整矩阵由 RdOnly DMA（bDma）在 execute 起始时自主扫描写入 B buffer，
    // 此处仅等待 B buffer 中积累 >= curTileK 行（FIFO 顺序 = Executor 消费顺序），
    // 随后进入 sLOAD_WEIGHT_PE 逐行 pop。
    is(sLOAD_WEIGHT_DMA) {
      when(io.bOccupancy >= curTileK) {
        counter    := 0.U
        state      := sLOAD_WEIGHT_PE
      }
    }

    // --- sLOAD_WEIGHT_PE: 从 B buffer pop 权重 → 加载到 HiveComb ---
    // loadH 在整个加载窗口保持拉高（fmt/rnd 配置需时间经阵列横向传播）；
    // loadV 跟随 bPop 数据有效性：B buffer 暂无数据时 loadV 自然暂停，
    // RdOnly DMA 喂数到位后自动恢复，无需固定每 2 拍一次的节流。
    is(sLOAD_WEIGHT_PE) {
      io.hiveLoadH := true.B               // 配置加载，整个窗口拉高
      val feeding = io.bPop.valid && (loadedRows < curTileK)
      io.hiveLoadV := feeding              // loadV 跟随 bPop 数据有效性
      io.bPop.ready := loadedRows < curTileK  // 有数据就消费，直到喂够 curTileK 行

      when(io.bPop.fire) {
        for (i <- 0 until cfg.totalN) {
          // 权重按 bW 切片（B buffer 行宽 = totalN * bW）
          io.hivePsumIn(i) := io.bPop.payload(cfg.bW * (i + 1) - 1, cfg.bW * i)
        }
      }

      // 已喂权重行数（仅在 bPop 实际 fire 时递增，天然支持反压暂停）
      when(io.bPop.fire) {
        loadedRows := loadedRows + 1.U
      }


      // 退出条件：已喂够 curTileK 行权重 且 经过传播延迟。
      // 权重经 psumReg 逐行寄存下沉后，各 PE 的 psumReg 残留末行权重值，
      // 排空完成当拍发一拍 clear 清零 psum 链（不影响 wReg），再进入计算
      when((loadedRows >= curTileK)) {
        io.hiveClear := true.B
        counter    := 0.U
        loadedRows := 0.U
        curMTile   := 0.U  // 重置 M tile 计数，开始 M 循环
        state      := sLOAD_A_DMA
      }
    }

    // --- sLOAD_A_DMA: 等待 A buffer 就绪（不驱动 DMA 加载 A） ---
    // A 矩阵由 RdOnly DMA（aDma）在 execute 拍单次启动后自主连续供数
    // （nTile 外 → kTile 中 → M 内，每轮 kTile×m 行，FIFO 顺序 = Executor
    // 消费顺序），此处仅等待 A buffer 积累 >= 当前 M tile 行数后进入计算逐行 pop。
    is(sLOAD_A_DMA) {
      // 动态计算当前 M tile 的参数
      val remainM = io.regFile.m(15, 0) - curMTile * totalN.U
      val thisTileM = Mux(remainM > totalN.U, totalN.U, remainM)(15, 0)

      when(io.aOccupancy >= thisTileM) {
        // 锁存当前 tile 参数
        curTileM := thisTileM
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
        state      := sLOAD_A_DMA
      }.otherwise {
        // 所有 M tiles 完成
        when(curKTile === (kTiles - 1.U)) {
          // 最后一个 K pass，结果写回外部
          state      := sSTORE_C_DMA
        }.otherwise {
          // 还有更多 K passes，partial sums 留在 C buffer
          state := sNEXT_NK_TILE
        }
      }
    }

    // --- sSTORE_C_DMA: 门控 WrOnly DMA 写回当前 N tile 的 C 结果 ---
    // WrOnly DMA 在 execute 时已 start 并自主待命；本状态拉高 cStoreGate
    // 开门放数，等 cOccupancy 排空（全部结果已被 DMA pop 出）即视为完成。
    is(sSTORE_C_DMA) {
      io.cStoreGate := true.B

      when(io.cOccupancy === 0.U) {
        doneTiles  := doneTiles + 1.U

        // 推进 N tile
        val nextNTile = curNTile + 1.U
        when(nextNTile >= nTiles) {
          // 所有 N tiles 完成
          state := sDONE
        }.otherwise {
          // 下一个 N tile：重置 K 和 M。无需重扫/flushA：aDma 内建 nTile
          // 轮次连续供数，每轮 push 行数（kTiles×m）= executor 每 N tile
          // 消费行数。注意：本拍 A FIFO 未必排空——aDma 可能已预取下一轮
          // 部分行（占位 < m 不会被 sLOAD_A_DMA 提前消费，无残留风险）；
          // 供/消总量平衡由 sIDLE execute 拍的全局排空断言兼底检查
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

    // --- sDONE: 完成（正常完成与 errState 异常退出共用此状态：
    // io.done 照常脉冲，io.err（= errState）由顶层汇聚进 resp，
    // 保证异常路径下软件栈也能收到带 err 标记的完成响应而非永久等待） ---
    is(sDONE) {
      io.done := true.B
      state   := sIDLE
    }
  }
}


class HiveCoreExecutor2(cfg: HiveCoreConfig) extends Module {

  val io = IO(new Bundle {
    // 控制
    val execute  = Input(Bool())        // 脉冲启动
    val busy     = Output(Bool())
    val done     = Output(Bool())       // 脉冲完成
    val err      = Output(Bool())

    //dma状态：
    val dmaABusy = Input(Bool())
    val dmaBBusy = Input(Bool())
    val dmaCBusy = Input(Bool())

    // 寄存器输入（由顶层寄存器组驱动）：全部寄存器统一经 io.regFile 引用。
    // 此前的拆分端口（regM/regN/regK/regBAddr/regBStride/regFmt/regRnd 及
    // regAAddr/regCAddr/regAStride）均与 regFile 完全冗余（顶层驱动只是
    // 截位/补零/类型视图），已全部移除
    val regFile = Input(HiveCoreRegs(cfg))

    // A buffer 自主 DMA（RdOnly）：execute 单次启动全自主供数，
    // 本模块 A 侧无控制输出，仅占用量感知
    val aOccupancy = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))

    // C buffer 自主 DMA（WrOnly）控制：store 门控（电平）+ 占用量感知
    val cStoreGate = Output(Bool())                    // 末 K pass store 窗口拉高
    val cPeek      = Output(Bool())
    val cPeekDone  = Input(Bool())
    val cOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))

    // Scratchpad
    val aPop  = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    // B buffer（权重专用）：RdOnly DMA 自主写入，本模块按行 pop
    val bPop  = slave(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val bOccupancy = Input(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val cPop  = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPush = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val flushA = Output(Bool())
    val flushB = Output(Bool())
    val flushC = Output(Bool())

    // HiveComb 驱动
    val hiveAIn     = Output(Vec(cfg.totalN, UInt(cfg.aEffW.W)))
    val hivePsumIn  = Output(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveLoadH   = Output(Bool())
    val hiveLoadV   = Output(Bool())
    val hiveValidIn = Output(Bool())
    val hiveClear   = Output(Bool())
    val hiveCOut    = Input(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveValidOut = Input(Vec(cfg.totalN, Bool()))

    // 进度
    val progress = Output(UInt(16.W))
  })

  // ==========================================================================
  // FSM 状态编码 (Weight-Stationary: M→N→K loop order)
  // ==========================================================================
  val sIDLE            = 0.U(4.W)
  val sLOAD_B          = 1.U(4.W) //load B buffer to prefile pe weight
  val sLOAD_A          = 2.U(4.W) //load A buffer to compute
  val sNEXT_NK_TILE    = 3.U(4.W)
  val sDRAIN           = 4.U(4.W)


  val state = RegInit(sIDLE)
  val errState = RegInit(false.B)

  // ==========================================================================
  // Tiling 寄存器
  // ==========================================================================
  val totalN = cfg.totalN

  //val mTiles   = Reg(UInt(16.W)) //M维度按行索引
  val nTiles   = Reg(UInt(16.W)) //N维度按tile索引
  val kTiles   = Reg(UInt(16.W)) //K维度按tile索引
  //val curMTile = Reg(UInt(16.W))
  val curNTile = Reg(UInt(16.W))
  val curKTile = Reg(UInt(16.W))
  
  val doneTiles = Reg(UInt(16.W))

  // 当前 tile 参数
  val curM = Reg(UInt(16.W))  // 实际行数
  val depthOfCBufferUsed = Reg(UInt(16.W))

  // 计算相关
  val tileInterCnt = RegInit(0.U(log2Up(cfg.totalN).W))

  
  // CBufferPipe
  val cStoreGateReg = RegInit(false.B)
  val cStoreGateRegNxt = RegInit(false.B)
  val cBufferReservedAtleastTotalN = RegInit(false.B)
  when(cfg.cBufferDepth.U - io.cOccupancy >= cfg.totalN.U){
    cBufferReservedAtleastTotalN := true.B
  }.otherwise{
    cBufferReservedAtleastTotalN := false.B
  }
  io.cStoreGate := cStoreGateReg
  cStoreGateRegNxt := cStoreGateReg
  io.cPeek      := ~cStoreGateRegNxt & cStoreGateReg
  when(io.cPeekDone){
    cStoreGateReg := false.B
  }
  

  // Skew 移位寄存器
  // A 供数为对角线流式：行 m 的第 i 个元素在拍 m+i 经 hiveAIn(i) 进入
  // 第 i 行 PE，垂直 psum 流与行索引严格同步（见 sCOMPUTE 注释）。
  val aSkewed = (0 until cfg.totalN).map { c =>
    val delay = cfg.totalN - 1 - c
    if (delay > 0) ShiftRegister(io.aPop.payload(cfg.aW * (c+1)-1, cfg.aW * c), delay)
    else io.aPop.payload(cfg.aW * (c+1)-1, cfg.aW * c)
  }
  for (i <- 0 until cfg.totalN) {
    io.hiveAIn(i) := aSkewed(i)
  }
  val cSkewed = (0 until cfg.totalN).map { c =>
    val delay = cfg.totalN - 1 - c
    if (delay > 0) ShiftRegister(io.cPop.payload(cfg.cW * (c+1)-1, cfg.cW * c), delay)
    else io.cPop.payload(cfg.cW * (c+1)-1, cfg.cW * c)
  }

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

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.busy := state =/= sIDLE
  io.done := false.B
  io.err  := errState

  io.cStoreGate := false.B

  io.aPop.ready  := false.B
  io.bPop.ready  := false.B
  io.cPop.ready  := false.B
  io.cPush.valid   := false.B
  io.cPush.payload := 0.U

  io.flushA := false.B
  io.flushB := false.B
  io.flushC := false.B

  io.hiveLoadH   := false.B
  io.hiveLoadV   := false.B
  io.hiveValidIn := false.B
  io.hiveClear   := false.B

  io.progress := doneTiles

  // ==========================================================================
  // C 输出收集逻辑（仅 sCOMPUTE 阶段，valid 窗口已覆盖全部结果流出）
  // cDeSkewed + alignedValid 将各列错峰数据对齐后，一次性 push 一整行到 C buffer。
  // ==========================================================================
  when(alignedValid) {
      io.cPush.valid   := true.B
      io.cPush.payload := Cat(cDeSkewed.reverse)
      // 时序不变量（多 pass partial sum 依赖的隐性假设）：
      // 非首 pass 中 cPop 回灌窗口（counter < curTileM）恒早于 cPush 窗口
      // （约 2*totalN 拍后结果才流到底部），pop/push 等量交替使 C buffer
      // 占用恒定在 mTiles×totalN。FSM 不引入 stall，若 push 时 buffer 满
      // （ready=0）会静默丢数：仿真由下方 assert 捕获，综合后由紧随的
      // errState 兜底（置错并经 sDONE 退出，避免丢数后继续计算产生静默错误结果）
      assert(io.cPush.ready, "HiveCoreExecutor: cPush valid but C buffer not ready (pop-before-push invariant violated, partial sum lost)")
  }
  // cPush 违约硬件级防护（综合后兜底，与上方仿真 assert 对应）：
  // 容量公式放宽后生产环境不能再依赖仿真断言，违约拍置 errState 并经
  // sDONE 退出（而非直回 sIDLE），保证顶层仍能收到带 err 标记的 resp。
  // 仅改状态机，不反压 cPush 驱动逻辑（不引入 stall）
  when( state =/= sIDLE & io.cPush.valid && !io.cPush.ready) {
    errState := true.B
    state    := sIDLE
  }

  for (i <- 0 until cfg.totalN) {
        io.hivePsumIn(i) := 0.U
  }

  // ==========================================================================
  // FSM 主体 (Weight-Stationary: N→K→M)
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待 execute 脉冲 ---
    is(sIDLE) {
      curNTile := 0.U
      curKTile := 0.U
      curM := 0.U
      doneTiles := 0.U
      when(io.execute) {
        // 计算 tile 数。注意：mTiles/kTiles 为 Reg，本拍 := 下一拍才生效，
        // 故 depthOfCBufferUsed 必须用本拍推导的 newMTiles/newKTiles 计算，
        // 否则会采样到未初始化寄存器的随机旧值，误触发容量错误回 idle
        val newNTiles = (io.regFile.n(15, 0) + totalN.U - 1.U) / totalN.U
        val newKTiles = (io.regFile.k(15, 0) + totalN.U - 1.U) / totalN.U
        nTiles   := newNTiles - 1.U
        kTiles   := newKTiles - 1.U
        // 容量检查：C buffer 真实驻留量只有一组 partial sum（mTiles×totalN 行）。
        // K > totalN 时非首 pass 的 pop（回灌）与 push 等量交替，占用恒定，
        // 故 kTiles 不乘进公式（旧公式 mTiles×kTiles×totalN 会在 K 稍大时误报容量错误）
        depthOfCBufferUsed := io.regFile.m
        tileInterCnt := totalN.U - 1.U
        // execute 起始拍清空 A/B buffer（RdOnly DMA 首个 push 至少晚一拍，安全）；
        // flushA 仅允许在此拍拉高：aDma 内建 nTile 轮次连续供数，任何
        // per-tile flush 都会丢弃已预取数据且 RdOnly 不重发 → 死锁。
        // A 侧全局排空不变量仿真检查（仅仿真可见）：上次 execute 中 aDma
        // push 总行数 = nTiles×kTiles×m = executor 消费总行数，新 execute
        // 拍 FIFO 必已排空；非 0 说明供/消行数失配（padding 混入/丢数）
        assert(io.aOccupancy === 0.U, "HiveCoreExecutor: A buffer not drained at execute pulse (previous run aDma push rows != executor consume rows)")
        io.flushA := true.B
        io.flushB := true.B
        io.flushC := true.B
        state    := sLOAD_B
      }
    }


    // --- sLOAD_WEIGHT_PE: 从 B buffer pop 权重 → 加载到 HiveComb ---
    // loadH 在整个加载窗口保持拉高（fmt/rnd 配置需时间经阵列横向传播）；
    // loadV 跟随 bPop 数据有效性：B buffer 暂无数据时 loadV 自然暂停，
    // RdOnly DMA 喂数到位后自动恢复，无需固定每 2 拍一次的节流。
    is(sLOAD_B) {
      io.hiveLoadH := true.B               // 配置加载，整个窗口拉高
      io.hiveLoadV := io.bPop.fire
      io.bPop.ready := true.B

      for (i <- 0 until cfg.totalN) {
        io.hivePsumIn(i) := io.bPop.payload(cfg.bW * (i + 1) - 1, cfg.bW * i)
      }

      // 已喂权重行数（仅在 bPop 实际 fire 时递增，天然支持反压暂停）
      when(io.bPop.fire) {
        when(tileInterCnt =/= 0.U){
          tileInterCnt := tileInterCnt - 1.U
        }.otherwise{
          tileInterCnt := totalN.U - 1.U
          state := sLOAD_A
        }
      }

    }

    is(sLOAD_A){
      val needPartialSum = curKTile > 0.U

      //当需要paritalsum的时候必须占用c口buffer的读出口，因此需要等到CDMA已经将cbuffer搬空让出读口权限
      val feed = (cBufferReservedAtleastTotalN) & Mux(needPartialSum, cStoreGateReg === false.B, true.B)

      io.aPop.ready := feed
      io.cPop.ready := feed & needPartialSum
      
      io.hiveValidIn := io.aPop.fire //当需要paritialsum的时候cbufer一定是有效可以被读出
      for(i <- 0 until cfg.totalN){
        when(needPartialSum){
          io.hivePsumIn(i) := cSkewed(i)
        }
      }
      
      when(io.aPop.fire){
        when(curM < io.regFile.m - 1.U){
          curM := curM + 1.U
        }.otherwise{
          curM := 0.U
          state := sNEXT_NK_TILE
        }
      }
    }

    is(sNEXT_NK_TILE){
      doneTiles := doneTiles + 1.U
      when(curKTile < kTiles){
        curKTile := curKTile + 1.U
        state := sLOAD_B
      }.otherwise{
        curKTile := 0.U
        cStoreGateReg := true.B
        when(curNTile < nTiles){
          curNTile := curNTile + 1.U
          state := sLOAD_B
        }.otherwise{
          curNTile := 0.U
          state := sDRAIN
        }
      }
 
    }
    

    // --- sDRAIN: 等待剩余输出收集完毕 ---
    is(sDRAIN) {
  
      val anyNewValid = io.hiveValidOut.asUInt.orR
  
      when(anyNewValid) {
        drainCounter := 0.U
      }.otherwise {
        drainCounter := drainCounter + 1.U
      }

      // 超时退出: 连续 2*totalN 周期无新 valid → 强制退出
      when(!anyNewValid & !io.dmaCBusy) {
        io.done := true.B
        state := sIDLE
      }
    }

    
  }
}
