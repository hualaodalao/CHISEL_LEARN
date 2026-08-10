/** HiveCore 轻量级 DMA 引擎。
  *
  * 仅支持线性数据搬运（push/pop queue），由内部 Executor FSM 直接控制。
  * 每个 DMA 实例通过 bufWidth 参数指定 buffer 接口位宽：
  *   - DMA0: bufWidth = cfg.totalN * cfg.aEffW  (A buffer)
  *   - DMA1: bufWidth = cfg.totalN * cfg.cEffW  (C buffer)
  */

import chisel3._
import chisel3.util._

/** HiveCoreDmaRdOnly — 只读 DMA 引擎（tile 粒度自动地址生成）。
  *
  * 职责：从外部存储逐 tile 读数据并 push 进内部矩阵 buffer
  *   - io.isA = true : 搬运 A[mTile, kTile] 子块 → A buffer
  *   - io.isA = false: 搬运整个 B[k, n] 矩阵 → B buffer
  *
  * B 矩阵固定「N 外 K 内」遍历（与 Executor 的 N→K→M 消费顺序匹配）：
  *   内层沿 K 方向（kTile*totalN 行，每拍地址 + bRowAddressOffset），
  *   外层扫 nTile 个 N tile（块间跳 bColAddressOffset）。
  * A 矩阵固定为：内层扫 mTile*totalN 行（+ aRowAddressOffset 行步进），
  *              外层扫 kTile 个 K tile（块间跳 aColAddressOffset）。
  *
  * 地址模型（与 HiveCoreExecutor 的 aTileAddr/bTileAddr 一致，字节地址）：
  *   A_tile_addr = regAAddr + mTileIdx*totalN*aRowOffset + kTileIdx*aColOffset
  *   B_tile_addr = regBAddr + kTileIdx*totalN*bRowOffset + nTileIdx*bColOffset
  * 块间跳转采用「跳回起点再前移一个块」的增量式计算：
  *   jump = blockOffset - (innerRows - 1) * rowStep
  *
  * 反压策略：
  *   - buffer 无空间 (bufAvailability == 0) 时不发起 cmd 请求
  *   - 外部 rsp 数据在 bufPush.ready 为高时才接收，天然支持逐拍暂停
  *
  * 运行模式：启动后完全自主运行——逐 tile 块连续搬运，块间不做任何
  * 上游握手，直到所有 tile 块搬完后发一拍 done 脉冲回 IDLE。
  *
  * @param cfg     HiveCore 配置
  * @param bufWidth buffer push 数据位宽（默认 = extDataWidth，每拍 push 一个 beat）
  * @param bufDepth buffer 深度（决定 bufAvailability 位宽；默认 = aBufferDepth，
  *                 B 实例需传 bBufferDepth）
  */
class HiveCoreDmaRdOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, bufDepth: Int = 0) extends Module {
  val pushW = if (bufWidth > 0) bufWidth else cfg.extDataWidth
  val availDepth = if (bufDepth > 0) bufDepth else cfg.aBufferDepth

  val io = IO(new Bundle {
    val isA = Input(Bool())                          // 例化时 tie 死：true=A buffer, false=B buffer
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtRdIF = HiveCoreDMAExtReadOnlyIF(cfg)   // 外部只读通道 cmd(addr)/rsp(data)
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数与地址偏移
    val regFile = Input(HiveCoreRegs(cfg))           // 基地址
    val bufPush = master(Stream(UInt(pushW.W)))      // 向 buffer push 数据
    val bufAvailability = Input(UInt(log2Up(availDepth + 1).W))
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sREQ_EXT  = 1.U(2.W)   // 发起读命令（检查 buffer 空间）
  val sTRANSFER = 2.U(2.W)   // 接收数据 push buffer、推进地址
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前读地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val blkJump = RegInit(0.U(cfg.addrWidth.W))  // 块间地址跳转量
  val innerRows = RegInit(0.U(cfg.mnkWidth.W)) // 内层循环总拍数（每 tile 块）
  val blkTarget = RegInit(0.U(cfg.mnkWidth.W)) // 外层 tile 块总数

  val rowCnt = RegInit(0.U(cfg.mnkWidth.W))    // 块内行计数
  val blkCnt = RegInit(0.U(cfg.mnkWidth.W))    // 已完成 tile 块计数
  val errReg = RegInit(false.B)

  // --- B 路径三级遍历专用寄存器（N 外 → kTile 中 → 块内行降序） ---
  // 权重下沉协议下 PE(x,y) 锁存「最后供数行 - x」，故每个 kTile 块必须按
  // K 行降序供数，才能使 PE(x,y).wReg = B[kTile*totalN + x][y]（正序落位）
  val isAReg   = RegInit(true.B)               // start 时锁存遍历模式
  val ktCnt    = RegInit(0.U(cfg.mnkWidth.W))  // 当前 nTile 内已完成 kTile 数
  val ktRows   = RegInit(0.U(cfg.mnkWidth.W))  // 当前 kTile 块行数（边界块可能 < totalN）
  val rowsLastReg = RegInit(0.U(cfg.mnkWidth.W)) // 末个（边界）kTile 块行数
  val bRowStep = RegInit(0.U(cfg.addrWidth.W)) // 块内行步进（负值：降序读）
  val bKtJump  = RegInit(0.U(cfg.addrWidth.W)) // kTile 间跳转量（下一块非边界）
  val bKtJumpLast = RegInit(0.U(cfg.addrWidth.W)) // kTile 间跳转量（下一块为边界块）
  val bNJump   = RegInit(0.U(cfg.addrWidth.W)) // nTile 间跳转量

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.dmaExtRdIF.cmd.valid       := false.B
  io.dmaExtRdIF.cmd.payload.addr := curAddr
  io.dmaExtRdIF.rsp.ready       := false.B

  io.bufPush.valid   := false.B
  io.bufPush.payload := io.dmaExtRdIF.rsp.payload.data

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg

  // ==========================================================================
  // FSM 主体
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待 start，锁存遍历参数 ---
    is(sIDLE) {
      rowCnt := 0.U
      blkCnt := 0.U
      errReg := false.B
      when(io.start) {
        when(io.isA) {
          // A 矩阵：内层扫 mTile*totalN 行，外层扫 kTile 个 K tile
          isAReg  := true.B
          curAddr   := io.regFile.aAddr
          rowStep   := io.calcConfig.aRowAddressOffset
          blkJump   := io.calcConfig.aColAddressOffset -
                       (io.calcConfig.mTile * cfg.totalN.U - 1.U) * io.calcConfig.aRowAddressOffset
          innerRows := io.calcConfig.mTile * cfg.totalN.U
          blkTarget := io.calcConfig.kTile
        }.otherwise {
          // B 矩阵：三级遍历「N 外 → kTile 中 → 块内行降序」。
          // 权重下沉协议下 PE(x,y) 最终锁存「最后供数行 - x」，每个 kTile 块
          // 必须按 K 行降序供数（先读块内最高行），executor 顺序 pop 供数后
          // 才能使 PE(x,y).wReg = B[kTile*totalN + x][y]（正序落位，标准 GEMM）。
          // 起始地址 = 块内最高行；块内每拍 -bRowOffset；块间用预计算跳转量。
          isAReg := false.B
          val rowOff = io.calcConfig.bRowAddressOffset
          val rowsLast = io.regFile.k - (io.calcConfig.kTile - 1.U) * cfg.totalN.U
          rowsLastReg := rowsLast
          val ktRows0 = Mux(io.calcConfig.kTile === 1.U, rowsLast, cfg.totalN.U)
          ktRows    := ktRows0
          ktCnt     := 0.U
          blkTarget := io.calcConfig.nTile
          curAddr   := io.regFile.bAddr + (ktRows0 - 1.U) * rowOff
          bRowStep  := 0.U(cfg.addrWidth.W) - rowOff
          bKtJump     := (2.U * cfg.totalN.U - 1.U) * rowOff
          bKtJumpLast := (cfg.totalN.U + rowsLast - 1.U) * rowOff
          bNJump      := io.calcConfig.bColAddressOffset + (cfg.totalN.U - 1.U) * rowOff -
                         (io.calcConfig.kTile - 1.U) * cfg.totalN.U * rowOff
        }
        state := sREQ_EXT
      }
    }

    // --- sREQ_EXT: buffer 有空间才发起读命令 ---
    is(sREQ_EXT) {
      when(io.bufAvailability =/= 0.U) {
        io.dmaExtRdIF.cmd.valid := true.B
        when(io.dmaExtRdIF.cmd.fire) {
          state := sTRANSFER
        }
      }
    }

    // --- sTRANSFER: 收数据 push buffer，推进地址与 tile 索引 ---
    is(sTRANSFER) {
      io.dmaExtRdIF.rsp.ready := io.bufPush.ready
      when(io.dmaExtRdIF.rsp.fire) {
        io.bufPush.valid := true.B
        when(io.dmaExtRdIF.rsp.payload.rsp) {
          errReg := true.B  // 外部返回错误响应
        }

        when(isAReg) {
          // A 路径：两级遍历（行升序 + 块间跳转），保持原行为
          when(rowCnt === innerRows - 1.U) {
            rowCnt := 0.U
            blkCnt := blkCnt + 1.U
            when(blkCnt === blkTarget - 1.U) {
              state := sDONE
            }.otherwise {
              curAddr := curAddr + blkJump
              state   := sREQ_EXT
            }
          }.otherwise {
            rowCnt  := rowCnt + 1.U
            curAddr := curAddr + rowStep
            state   := sREQ_EXT
          }
        }.otherwise {
          // B 路径：块内行降序（bRowStep 为负），kTile 中级、nTile 外级
          when(rowCnt === ktRows - 1.U) {
            // 本 kTile 块最后一行
            rowCnt := 0.U
            when(ktCnt === io.calcConfig.kTile - 1.U) {
              // 本 nTile 的最后一个 kTile：推进到下一个 nTile
              ktCnt  := 0.U
              blkCnt := blkCnt + 1.U
              when(blkCnt === blkTarget - 1.U) {
                state := sDONE
              }.otherwise {
                curAddr := curAddr + bNJump
                ktRows  := cfg.totalN.U  // 新 nTile 的首个 kTile 必为满块
                state   := sREQ_EXT
              }
            }.otherwise {
              // 跳到下一个 kTile 块的最高行
              val nextIsLast = (ktCnt + 1.U) === (io.calcConfig.kTile - 1.U)
              ktCnt   := ktCnt + 1.U
              ktRows  := Mux(nextIsLast, rowsLastReg, cfg.totalN.U)
              curAddr := curAddr + Mux(nextIsLast, bKtJumpLast, bKtJump)
              state   := sREQ_EXT
            }
          }.otherwise {
            // 块内降序：地址按负步进
            rowCnt  := rowCnt + 1.U
            curAddr := curAddr + bRowStep
            state   := sREQ_EXT
          }
        }
      }
    }

    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      io.done := true.B
      state := sIDLE
    }
  }
}


/** HiveCoreDmaWrOnly — 只写 DMA 引擎（tile 粒度自动地址生成）。
  *
  * 职责：从内部 C buffer 逐 tile pop 结果数据并写到外部存储
  * （HiveCoreDmaRdOnly 的方向对称版本：bufPush/读 → bufPop/写）。
  *
  * 搬运对象固定为结果矩阵 C[mTile, nTile]。C 写回固定「内 M 外 N」遍历：
  * C buffer 为严格 FIFO（StreamFifo），写回顺序 = Executor push 顺序，
  * 而 Executor 只有一套固定的 N→K→M 循环（M 恒为最内层），loopMode 只
  * 影响容量检查、不改变 C 写回遍历顺序，因此与 loopMode 无关。
  *   - 内层沿 M 方向（mTile*totalN 行，每拍地址 + cRowAddressOffset），
  *     外层扫 nTile 个 N tile（块间跳 cColAddressOffset）
  *
  * 地址模型（与 HiveCoreExecutor 的 cStoreAddr 约定一致，字节地址）：
  *   C_tile_addr = regCAddr + mTileIdx*totalN*cRowOffset + nTileIdx*cColOffset
  * 块间跳转采用与 RdOnly 相同的「跳回起点再前移一个块」增量式计算：
  *   jump = blockOffset - (innerRows - 1) * rowStep
  *
  * 反压策略（与 RdOnly 对称）：
  *   - C buffer 无数据 (bufOccupancy == 0) 时不发起写请求
  *   - 数据在外部 writeData.ready 为高时才 pop 发出，天然支持逐拍暂停
  *
  * 运行模式：启动后完全自主运行——逐 tile 块连续写回，块间不做任何
  * 上游握手，直到所有 tile 块搬完后发一拍 done 脉冲回 IDLE。
  *
  * @param cfg      HiveCore 配置
  * @param bufWidth buffer pop 数据位宽（默认 = extDataWidth，每拍 pop 一个 beat）
  */
class HiveCoreDmaWrOnly(cfg: HiveCoreConfig, bufWidth: Int = 0) extends Module {
  val popW = if (bufWidth > 0) bufWidth else cfg.extDataWidth

  val io = IO(new Bundle {
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtWrIF = new DmaExtIO(cfg)               // 外部写通道 req/grant/addr/len/isWrite/writeData
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数与地址偏移
    val regFile = Input(HiveCoreRegs(cfg))           // 仅使用 cAddr 基地址（loopMode 不影响 C 写回遍历）
    val bufPop = slave(Stream(UInt(popW.W)))         // 从 C buffer pop 数据
    val bufOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sREQ_EXT  = 1.U(2.W)   // 发起写请求（检查 buffer 数据量）
  val sTRANSFER = 2.U(2.W)   // pop buffer 驱动 writeData、推进地址
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val blkJump = RegInit(0.U(cfg.addrWidth.W))  // 块间地址跳转量
  val innerRows = RegInit(0.U(cfg.mnkWidth.W)) // 内层循环总拍数（每 tile 块）
  val blkTarget = RegInit(0.U(cfg.mnkWidth.W)) // 外层 tile 块总数

  val rowCnt = RegInit(0.U(cfg.mnkWidth.W))    // 块内行计数
  val blkCnt = RegInit(0.U(cfg.mnkWidth.W))    // 已完成 tile 块计数
  val errReg = RegInit(false.B)

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.dmaExtWrIF.req          := false.B
  io.dmaExtWrIF.addr         := curAddr
  io.dmaExtWrIF.len          := 1.U          // 逐 beat 请求，与 RdOnly 逐行 cmd 风格一致
  io.dmaExtWrIF.isWrite      := true.B       // 只写 DMA
  io.dmaExtWrIF.readData.ready := false.B    // 只写通道不使用读数据
  io.dmaExtWrIF.writeData.valid   := false.B
  io.dmaExtWrIF.writeData.payload := io.bufPop.payload

  io.bufPop.ready := false.B

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg

  // ==========================================================================
  // FSM 主体
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待 start，锁存 C 矩阵遍历参数 ---
    is(sIDLE) {
      rowCnt := 0.U
      blkCnt := 0.U
      errReg := false.B
      when(io.start) {
        // C 矩阵：固定「内 M 外 N」遍历（C buffer 为 FIFO，写回顺序 =
        // Executor push 顺序，与 loopMode 无关）
        curAddr := io.regFile.cAddr
        // 内层沿 M（mTile*totalN 行），外层扫 nTile 个 N tile
        // 与 Executor store 顺序一致：每个 N tile 写回所有 M 行
        rowStep   := io.calcConfig.cRowAddressOffset
        blkJump   := io.calcConfig.cColAddressOffset -
                     (io.calcConfig.mTile * cfg.totalN.U - 1.U) * io.calcConfig.cRowAddressOffset
        innerRows := io.calcConfig.mTile * cfg.totalN.U
        blkTarget := io.calcConfig.nTile
        state := sREQ_EXT
      }
    }

    // --- sREQ_EXT: buffer 有数据才发起写请求 ---
    is(sREQ_EXT) {
      when(io.bufOccupancy =/= 0.U) {
        io.dmaExtWrIF.req := true.B
        when(io.dmaExtWrIF.grant) {
          state := sTRANSFER
        }
      }
    }

    // --- sTRANSFER: pop buffer 写外部，推进地址与 tile 索引 ---
    is(sTRANSFER) {
      io.bufPop.ready             := io.dmaExtWrIF.writeData.ready
      io.dmaExtWrIF.writeData.valid := io.bufPop.valid
      when(io.bufPop.fire) {
        when(rowCnt === innerRows - 1.U) {
          // 本 tile 块最后一行：无需上游握手，自主推进到下一个 tile 块
          rowCnt := 0.U
          blkCnt := blkCnt + 1.U
          when(blkCnt === blkTarget - 1.U) {
            // 所有 tile 块搬完
            state := sDONE
          }.otherwise {
            // 跳回块起点并前移一个块（buffer 数据量由 sREQ_EXT 检查）
            curAddr := curAddr + blkJump
            state   := sREQ_EXT
          }
        }.otherwise {
          // 块内普通行：地址按行步进
          rowCnt  := rowCnt + 1.U
          curAddr := curAddr + rowStep
          state   := sREQ_EXT
        }
      }
    }

    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      io.done := true.B
      state := sIDLE
    }
  }
}


class HiveCoreDmaEngine(cfg: HiveCoreConfig, bufWidth: Int) extends Module {

  val io = IO(new Bundle {
    // 内部控制信号（由 Executor FSM 直接驱动）
    val start     = Input(Bool())       // 脉冲启动传输
    val isLoad    = Input(Bool())       // true=外部→buffer, false=buffer→外部
    val addr      = Input(UInt(cfg.addrWidth.W))
    val len       = Input(UInt(16.W))   // beat 数
    val busy      = Output(Bool())
    val done      = Output(Bool())      // 脉冲，完成一次传输

    // 外部数据接口
    val extReadData  = Input(UInt(cfg.extDataWidth.W))
    val extReadValid = Input(Bool())
    val extReadReady = Output(Bool())   // 反压信号：buffer 满时拒绝外部数据
    val extWriteData = Output(UInt(cfg.extDataWidth.W))
    val extWriteValid = Output(Bool())
    val extWriteReady = Input(Bool())
    val extAddr  = Output(UInt(cfg.addrWidth.W))
    val extLen   = Output(UInt(16.W))
    val extReq   = Output(Bool())
    val extGrant = Input(Bool())
    val extIsWrite = Output(Bool())

    // Scratchpad buffer 接口（push = 写入 buffer, pop = 从 buffer 读）
    val bufPush      = master(Stream(UInt(bufWidth.W)))
    val bufPop       = slave(Stream(UInt(bufWidth.W)))
    val bufAvailability = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))
    val bufOccupancy    = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sREQ_EXT  = 1.U(2.W)
  val sTRANSFER = 2.U(2.W)
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val addrReg    = Reg(UInt(cfg.addrWidth.W))
  val lenReg     = Reg(UInt(16.W))
  val isLoadReg  = Reg(Bool())

  val beatCounter = Reg(UInt(16.W))

  // ==========================================================================
  // 默认输出
  // ==========================================================================
  io.done          := false.B
  io.extAddr       := 0.U
  io.extLen        := 0.U
  io.extReq        := false.B
  io.extIsWrite    := false.B
  io.extReadReady  := false.B
  io.extWriteData  := 0.U
  io.extWriteValid := false.B

  io.bufPush.valid   := false.B
  io.bufPush.payload := 0.U
  io.bufPop.ready    := false.B

  io.busy := state =/= sIDLE

  // ==========================================================================
  // FSM 逻辑
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待启动脉冲 ---
    is(sIDLE) {
      when(io.start) {
        isLoadReg   := io.isLoad
        addrReg     := io.addr
        lenReg      := io.len
        beatCounter := io.len
        state       := sREQ_EXT
      }
    }

    // --- sREQ_EXT: 向外部发起请求（检查 buffer 空间/数据量） ---
    is(sREQ_EXT) {
      // Load 模式: 确保 buffer 有足够空间接收整个 burst
      // Store 模式: 确保 buffer 有足够数据发送整个 burst
      val canRequest = Mux(isLoadReg,
        io.bufAvailability >= lenReg,
        io.bufOccupancy >= lenReg)

      when(canRequest) {
        io.extReq     := true.B
        io.extAddr    := addrReg
        io.extLen     := lenReg
        io.extIsWrite := !isLoadReg

        when(io.extGrant) {
          state := sTRANSFER
        }
      }
    }

    // --- sTRANSFER: 数据搬运 ---
    is(sTRANSFER) {
      when(isLoadReg) {
        // Load 模式：外部 → buffer
        // 仅在 buffer 能接收且还有剩余 beat 时才 ready
        io.extReadReady := io.bufPush.ready && (beatCounter =/= 0.U)
        when(io.extReadValid && io.bufPush.ready && (beatCounter =/= 0.U)) {
          io.bufPush.valid   := true.B
          io.bufPush.payload := io.extReadData
          beatCounter := beatCounter - 1.U
        }
      }.otherwise {
        // Store 模式：buffer → 外部
        when((io.bufOccupancy > 0.U) && io.extWriteReady) {
          io.bufPop.ready    := true.B
          io.extWriteData    := io.bufPop.payload
          io.extWriteValid   := io.bufPop.valid
          when(io.bufPop.fire) {
            beatCounter := beatCounter - 1.U
          }
        }
      }

      // 传输完成
      when(beatCounter === 0.U) {
        state := sDONE
      }
    }

    // --- sDONE: 发送完成脉冲 ---
    is(sDONE) {
      io.done := true.B
      state   := sIDLE
    }
  }
}
