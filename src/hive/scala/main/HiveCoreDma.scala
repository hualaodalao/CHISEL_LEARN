/** HiveCore 轻量级 DMA 引擎（自主 tile 扫描版）。
  *
  * 仅支持线性数据搬运（push/pop queue），启动后完全自主运行，
  * 由 occupancy/availability 反压，不依赖 Executor 逐请求控制：
  *   - HiveCoreDmaRdOnly: 外部存储 → buffer（A buffer / B buffer）
  *   - HiveCoreDmaWrOnly: C buffer → 外部存储（storeGate 门控）
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
  * A 矩阵固定为三级遍历「nTile 外 → kTile 中 → M 内」：共 nTiles 轮、
  *   每轮 kTile×m 行（内层 regFile.m 行 +aRowAddressOffset，中层 kTile 块
  *   跳 aColAddressOffset，每轮结束 curAddr 复位回 regAAddr），与 executor
  *   N→K→M 消费序逐拍对齐；execute 单次启动自主供满 nTiles 轮后自然停止。
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
  * start 语义：io.start 在任意状态均可重新装载（复位计数/错误、重新锁存
  * 遍历参数、进入 sREQ_EXT），用于异常路径（Executor errState）后的强制
  * 重启，避免 DMA 卡在 sREQ_EXT 等永不再来的门控、下次 EXECUTE 携旧参数
  * 静默写错。安全性：脱离 sTRANSFER 时外部流 ready/valid 自然拉低，无协议违约。
  *
  * @param cfg     HiveCore 配置
  * @param bufWidth buffer push 数据位宽（默认 = cfg.aExtW；外部 rsp 与本口
  *                 等宽直连，每拍 push 一个 beat）
  * @param bufDepth buffer 深度（决定 bufAvailability 位宽；默认 = aBufferDepth，
  *                 B 实例需传 bBufferDepth）
  */
class HiveCoreDmaRdOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, bufDepth: Int = 0) extends Module {
  val pushW = bufWidth 
  val availDepth = bufDepth

  val io = IO(new Bundle {
    val isA = Input(Bool())                          // 例化时 tie 死：true=A buffer, false=B buffer
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtRdIF = HiveCoreDMAExtReadOnlyIF(cfg, pushW)   // 外部只读通道 cmd(addr)/rsp(data)，与 bufPush 等宽直连
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数与地址偏移
    val regFile = Input(HiveCoreRegs(cfg))           // 基地址
    val bufPush = master(Stream(UInt(pushW.W)))      // 向 buffer push 数据
    val bufPopFire = Input(Bool())
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE       = 0.U(2.W)
  val sTRANSFER   = 1.U(2.W)   // 接收数据 push buffer、推进地址
  val sNEXT_COL  = 2.U(2.W)
  val sDONE       = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前读地址
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前读地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val bNextKTileAddressOffset = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val kCnt = RegInit(0.U(cfg.mnkWidth.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))

  val lineTarget  = RegInit(0.U(16.W))
  val lineCounter = RegInit(0.U(16.W))
  val ostCredit= RegInit(0.U(log2Up(bufDepth + 1).W))
  val errReg = RegInit(false.B)
  val ERR_BUS = 1.U(2.W)
  val ERR_FIFO = 2.U(2.W)
  val errState = RegInit(0.U(2.W))

  when(state === sIDLE){
    ostCredit := bufDepth.U
    errReg := false.B
  }.otherwise{
    when(io.dmaExtRdIF.rsp.fire){
      when(io.dmaExtRdIF.rsp.payload.err){
        errReg := true.B
        errState := ERR_BUS
      }
    }

    when(io.dmaExtRdIF.req.fire & io.bufPopFire){
    }.elsewhen(io.dmaExtRdIF.req.fire){
      ostCredit := ostCredit - 1.U
    }.elsewhen(io.bufPopFire){
      ostCredit := ostCredit + 1.U
    }
    
  }

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.dmaExtRdIF.req.valid       := ostCredit =/= 0.U & state === sTRANSFER
  io.dmaExtRdIF.req.payload.addr := curAddr
  io.dmaExtRdIF.rsp.ready       := true.B //buffer永远能push的时候dma才会发请求

  io.bufPush.valid   := io.dmaExtRdIF.rsp.valid
  io.bufPush.payload := io.dmaExtRdIF.rsp.payload.data

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg



  switch(state) {
    // --- sIDLE: 等待 start（锁存逻辑在上方统一处理，本状态无动作） ---
    is(sIDLE) {
       errReg := false.B
       lineCounter := 0.U  
      
      when(io.isA) {
          // A 矩阵：三级遍历「nTile 外 → kTile 中 → M 内」。内层扫实际 M 行
          // （regFile.m，边界感知不含 padding 行），中层扫 kTile 个 K tile，
          // 外层共 nTile 轮（每轮结束 curAddr 复位回 aAddr）。Executor 每
          // K pass 只消费实际 M 行，若按 mTile*totalN 扫描会把 padding 行混入 FIFO
          curAddr   := io.regFile.aAddr
          colAddr   := io.calcConfig.aColTileAddressOffset
          rowStep   := io.calcConfig.aRowAddressOffset
          lineTarget  := io.regFile.m

        }.otherwise {
          //B 矩阵是块内降序
          
          // B 矩阵：三级遍历「N 外 → kTile 中 → 块内行降序」。
          // 权重下沉协议下 PE(x,y) 最终锁存「最后供数行 - x」，每个 kTile 块
          // 必须按 K 行降序供数（先读块内最高行），executor 顺序 pop 供数后
          // 才能使 PE(x,y).wReg = B[kTile*totalN + x][y]（正序落位，标准 GEMM）。
          // 起始地址 = 块内最高行；块内每拍 -bRowOffset；块间用预计算跳转量。
          curAddr := io.regFile.bAddr + (cfg.totalN - 1).U * io.calcConfig.bRowAddressOffset
          colAddr   := io.calcConfig.bColTileAddressOffset + (cfg.totalN - 1).U * io.calcConfig.bRowAddressOffset
          rowStep   := io.calcConfig.bRowAddressOffset
          lineTarget  := cfg.totalN.U
          bNextKTileAddressOffset := (cfg.totalN * 2 ).U * io.calcConfig.bRowAddressOffset //这里乘以2的原因是B矩阵是降序读取，那么下一个k维tile需要增加两个

      }
      
      when(io.start){
        state  := sTRANSFER
      }

    }

    is(sTRANSFER){
      when(io.dmaExtRdIF.req.fire){
          when(lineCounter === lineTarget - 1.U){
            state := sNEXT_COL
            lineCounter := 0.U
          }.otherwise{
            lineCounter := lineCounter + 1.U
          }
          when(io.isA){
            curAddr := curAddr + rowStep
          }.otherwise{
            curAddr := curAddr - rowStep
          }
      }
    }
    // --- sTRANSFER: 收数据 push buffer，推进地址与 tile 索引 ---
    is(sNEXT_COL) {
      when(io.isA) {
        when(kCnt === io.calcConfig.kTile - 1.U){
          curAddr := io.regFile.aAddr
          colAddr := io.calcConfig.aColTileAddressOffset
          kCnt := 0.U
          when(nCnt === io.calcConfig.nTile - 1.U){
            state := sDONE
          }.otherwise{
            nCnt := nCnt + 1.U
            state := sTRANSFER
          }
          
        }.otherwise{
          curAddr := io.regFile.aAddr + colAddr
          colAddr := colAddr + io.calcConfig.aColTileAddressOffset
          kCnt := kCnt + 1.U
          state := sTRANSFER
        }
        
        
        
      }.otherwise {
        
        when(kCnt === io.calcConfig.kTile - 1.U){
          when(nCnt === io.calcConfig.nTile - 1.U){
            state := sDONE
          }.otherwise{
            kCnt := 0.U
            curAddr := io.regFile.bAddr + colAddr
            colAddr := colAddr + io.calcConfig.bColTileAddressOffset
            nCnt := nCnt + 1.U
            state := sTRANSFER
          }
        }.otherwise{
            kCnt := kCnt + 1.U
            curAddr := curAddr + bNextKTileAddressOffset
            state := sTRANSFER
        }
 
      }
    }
    

    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      when(ostCredit === bufDepth.U){
        io.done := true.B
        state := sIDLE
      }
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
  *   - 内层沿 M 方向（实际 M 行 regFile.m，边界感知不含 padding，
  *     每拍地址 + cRowAddressOffset），外层扫 nTile 个 N tile
  *     （块间跳 cColAddressOffset）
  *
  * 地址模型（与 HiveCoreExecutor 的 cStoreAddr 约定一致，字节地址）：
  *   C_tile_addr = regCAddr + mTileIdx*totalN*cRowOffset + nTileIdx*cColOffset
  * 块间跳转采用与 RdOnly 相同的「跳回起点再前移一个块」增量式计算：
  *   jump = blockOffset - (innerRows - 1) * rowStep
  *
  * 反压策略（与 RdOnly 对称）：
  *   - C buffer 无数据 (bufOccupancy == 0) 或 storeGate 未拉高时不发起写请求
  *   - 数据在外部 writeData.ready 为高时才 pop 发出，天然支持逐拍暂停
  *
  * 门控语义：io.storeGate 由 Executor 在末 K pass 的 store 窗口（sSTORE_C_DMA）
  * 拉高，防止多 pass 中间 partial sum 被误搬；非末 K pass 期间 Executor
  * 不会进入 store 窗口，storeGate 恒低，本 DMA 即使已 start 也不会写外。
  * 内层行数锁存实际 M 行（regFile.m）：边界 M 下 Executor 只 push 实际
  * M 行，若期望 mTile*totalN 行会永久等待挂死。
  *
  * 运行模式：启动后完全自主运行——逐 tile 块连续写回，块间不做任何
  * 上游握手，直到所有 tile 块搬完后发一拍 done 脉冲回 IDLE。
  *
  * start 语义：io.start 在任意状态均可重新装载（复位计数/错误、重新锁存
  * 遍历参数、进入 sREQ_EXT），用于异常路径后的强制重启（同 RdOnly 说明）。
  * 安全性：脱离 sTRANSFER 时 bufPop.ready/writeData.valid 自然拉低，无协议违约。
  *
  * @param cfg      HiveCore 配置
  * @param bufWidth buffer pop 数据位宽（默认 = cfg.cExtW；外部 writeData 与本口
  *                 等宽直连，每拍 pop 一个 beat）
  */
class HiveCoreDmaWrOnly(cfg: HiveCoreConfig, bufWidth: Int = 0) extends Module {
  val popW = if (bufWidth > 0) bufWidth else cfg.cExtW

  val io = IO(new Bundle {
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val peekBlock  = Input(Bool())                   // 写出一个M维Block控制脉冲
    val doneBlock = Output(Bool())                   // 完成一个M维Block控制脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtWrIF = new HiveCoreDMAExtWriteOnlyIF(cfg, popW)
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数与地址偏移
    val regFile = Input(HiveCoreRegs(cfg))           // 仅使用 cAddr 基地址（loopMode 不影响 C 写回遍历）
    val bufPop = slave(Stream(UInt(popW.W)))         // 从 C buffer pop 数据
    val bufOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
  })

  //
  

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE      = 0.U(3.W)
  val sFIRST_PEEK = 1.U(3.W)
  val sTRANSFER_M  = 2.U(3.W)   // pop buffer 驱动 writeData、推进地址  
  val sNEXT_N = 3.U(3.W)
  val sDONE      = 4.U(3.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写列地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val lineCounter = RegInit(0.U(cfg.mnkWidth.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))
  val errReg = RegInit(false.B)
  val ostCredit = RegInit(0.U(log2Up(cfg.cBufferDepth + 1).W))

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & io.bufPop.valid
  io.dmaExtWrIF.req.payload.addr         := curAddr
  io.dmaExtWrIF.req.payload.data         := io.bufPop.payload
  io.dmaExtWrIF.rsp.ready        := true.B
  io.bufPop.ready                := (state === sTRANSFER_M) & io.dmaExtWrIF.req.ready

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg
  io.doneBlock := false.B

  when(state === sIDLE){
    ostCredit := 0.U
  }.otherwise{
    when(io.dmaExtWrIF.req.fire & io.dmaExtWrIF.rsp.fire){
      ostCredit := ostCredit
    }.elsewhen(io.dmaExtWrIF.rsp.fire){
      ostCredit := ostCredit - 1.U
    }.elsewhen(io.dmaExtWrIF.req.fire){
      ostCredit := ostCredit + 1.U
    }
  }

  when(io.dmaExtWrIF.rsp.fire){
    when(io.dmaExtWrIF.rsp.payload.err){
      errReg := true.B
    }
  }
  

  switch(state) {

    // --- sIDLE: 等待 start（锁存逻辑在上方统一处理，本状态无动作） ---
    is(sIDLE) { 
      // ==========================================================================
      // start 重新装载（任意状态生效，语义与安全性说明见 RdOnly 同构实现）：
      // Executor 异常回 idle 后下次 EXECUTE 必须能强制重启卡死的本 DMA
      // ==========================================================================
      when(io.start) {
        state  := sFIRST_PEEK
        errReg := false.B
        // C 矩阵：固定「内 M 外 N」遍历（C buffer 为 FIFO，写回顺序 =
        // Executor push 顺序，与 loopMode 无关）
        curAddr := io.regFile.cAddr
        // 内层沿实际 M 行（regFile.m，边界感知不含 padding），外层扫 nTile 个 N tile
        // 与 Executor store 顺序一致：每个 N tile 写回所有实际 M 行
        // （Executor 只 push 实际 M 行，若锁存 mTile*totalN 会永久等待挂死）
        rowStep   := io.calcConfig.cRowAddressOffset
        colAddr   := io.calcConfig.cColTileAddressOffset
        nCnt      := 0.U
      }


    }
    
    is(sFIRST_PEEK) {
      when(io.peekBlock) {
        state := sTRANSFER_M
      }
    }

    is(sTRANSFER_M){
      when(io.bufPop.fire) {
        when(lineCounter === io.regFile.m - 1.U) {
          lineCounter := 0.U
          state := sNEXT_N
          io.doneBlock := true.B
        }.otherwise {
          lineCounter := lineCounter + 1.U
          curAddr := curAddr + rowStep
        }
      }
    }


    is(sNEXT_N) {
      when(nCnt === io.calcConfig.nTile - 1.U){
        state := sDONE
      }.otherwise{
        when(io.peekBlock){
          nCnt := nCnt + 1.U
          curAddr := curAddr + colAddr
          colAddr := colAddr + io.calcConfig.cColTileAddressOffset
          state := sTRANSFER_M
        }
      }
      
    }

 
    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      when(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }
}
