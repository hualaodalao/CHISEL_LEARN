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
  * start 语义：io.start 在任意状态均可重新装载，用于异常路径（Executor
  * errState：config-err 后 executor 不 pop、DMA 可能卡 sDONE 等 credit）后的
  * 强制重启。实现：sDONE/sTRANSFER 内置 start 重载分支，当拍同周期锁存新
  * 遍历参数并直达 sTRANSFER（start 为 executePulse 单拍脉冲，不可经 sIDLE
  * 中转等待）；ostCredit/errReg/lineCounter 同拍复位（flush 不返还的 credit
  * 在此恢复）。锁存体与 sIDLE 共用同一组合表达式（条件 state===sIDLE|start），
  * 正常流程 start 仅出现在 sIDLE，重载分支与新增锁存拍全死路、行为不变。
  * sNEXT_COL 为单拍过渡态无需分支（下一拍必落 sTRANSFER/sDONE 被重载捕获）。
  * 安全性：脱离 sTRANSFER 时外部流 ready/valid 自然拉低，无协议违约。
  *
  * @param cfg     HiveCore 配置
  * @param bufWidth buffer push 数据位宽（默认 = cfg.aExtW；外部 rsp 与本口
  *                 等宽直连，每拍 push 一个 beat）
  * @param bufDepth buffer 深度（决定 bufAvailability 位宽；默认 = aBufferDepth，
  *                 B 实例需传 bBufferDepth）
  */
class HiveCoreDmaRdOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, bufDepth: Int = 0, isScale: Boolean = false) extends Module {
  val pushW = bufWidth 
  val availDepth = bufDepth
  // isScale（编译期，仅 scaleB 实例传 true）：scaleB 每 (nTile, K-块) 一个
  // totalN 向量，与 B 的「每 kTile×totalN 行」遍历粒度不同，故走独立两级
  // 遍历「nt 轮外 × kb 块内」——总 beat 数 = nTile × kBlocks（kBlocks =
  // kTile>>1，K%32==0 保证 kTile 偶数）。取址序与 executor pop 序（每 nt
  // 轮内各 K-块起始 pop 一次）逐拍一致：外部布局 scaleB[kb][n] 连续（每
  // kb 占 regFile.n 字节）下，块内 kb 步进 = regFile.n 字节，轮间跳回
  // base + (nt+1)*totalN（sNEXT_COL 处理）。单纯线性取址在 nTile>1 且
  // kBlocks>1 时为 kb-major 序、与 nt-major pop 序错位，故必须两级遍历。
  // isScale=false 时下方所有分支为死路，A/B 遍历行为逐字 bit-exact 不变。

  val io = IO(new Bundle {
    val isA = Input(Bool())                          // 例化时 tie 死：true=A buffer, false=B buffer
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtRdIF = HiveCoreDMAExtReadOnlyIF(cfg, pushW)   // 外部只读通道 cmd(addr)/rsp(data)，与 bufPush 等宽直连
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数与地址偏移
    val regFile = Input(HiveCoreRegister(cfg))           // 基地址
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
    // --- sIDLE: 等待 start（锁存逻辑在下方 state===sIDLE|start 块统一处理） ---
    is(sIDLE) {
       errReg := false.B
       lineCounter := 0.U  
      
      // 防重复 start 竞态：重载拍（非 sIDLE 收到 start）已直达 sTRANSFER，
      // 仅当本拍确在 sIDLE 时才允许 start 推进，避免锁存/状态二次覆盖
      when(io.start && state === sIDLE){
        state  := sTRANSFER
      }

    }

    is(sTRANSFER){
      // start 重载（契约对齐）：搬运途中收到 start（异常路径强制重启）→
      // 同拍锁存新参数（上方 |io.start 锁存块）并复位计数/credit 重启遍历；
      // 正常流程 start 只在 sIDLE 出现，本分支死路、行为不变
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        kCnt        := 0.U
        nCnt        := 0.U
        ostCredit   := bufDepth.U
      }.elsewhen(io.dmaExtRdIF.req.fire){
          when(lineCounter === lineTarget - 1.U){
            // scaleB：一轮（kBlocks beat）发完按 nCnt 分流——末 nt 轮直接
            // sDONE，否则 sNEXT_COL 轮间跳转；A/B 走 sNEXT_COL 处理块/列
            // 跳转（isScale=false 逐字不变）
            if (isScale) {
              when(nCnt === io.calcConfig.nTile - 1.U) {
                state := sDONE
              }.otherwise {
                state := sNEXT_COL
              }
            } else {
              state := sNEXT_COL
            }
            lineCounter := 0.U
          }.otherwise{
            lineCounter := lineCounter + 1.U
          }
          when(io.isA){
            curAddr := curAddr + rowStep
          }.otherwise{
            // scaleB 块内 kb 步进 +N 字节；B 块内降序（isScale=false 逐字不变）
            if (isScale) curAddr := curAddr + rowStep
            else         curAddr := curAddr - rowStep
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
        if (isScale) {
          // scaleB 轮间跳转（nt 外）：下一 nt 轮基址 = bAddr + (nt+1)*totalN
          // 字节（布局 scaleB[kb][n] 连续，nt 轮内向量的列窗右移 totalN）；
          // colAddr 累计 nt 轮偏移供后续轮复用，块内 kb 步进在 sTRANSFER 完成
          nCnt := nCnt + 1.U
          colAddr := colAddr + cfg.totalN.U
          curAddr := io.regFile.bAddr + colAddr + cfg.totalN.U
          state := sTRANSFER
        } else {
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
    }
    

    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      // start 重载（契约对齐）：修复「config-err 后卡 sDONE 等 credit」死锁链——
      // executor errState 不 pop → ostCredit 不返还 → 原实现永久等待；
      // 现 start 拍同周期锁存新参数（上方 |io.start 锁存块）并复位 credit/计数
      // 重启遍历（flush 不返还的 credit 在此恢复）
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        kCnt        := 0.U
        nCnt        := 0.U
        ostCredit   := bufDepth.U
      }.elsewhen(ostCredit === bufDepth.U){
        io.done := true.B
        state := sIDLE
      }
    }
  }

  // ==========================================================================
  // 遍历参数锁存（switch 外公共位置，与 state 无关的条件化锁存）
  // sIDLE 每拍刷新（参数恒新）；重载拍（任意状态收到 start）同周期锁存——
  // start 为单拍 executePulse，不能经 sIDLE 中转等待下一拍。正常流程 start
  // 仅在 sIDLE 出现，|io.start 项恒假、锁存拍与行为逐字不变。
  // ==========================================================================
  when(state === sIDLE || io.start) {
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
      if (isScale) {
        // scaleB 两级遍历（仅 isScale=true 生成）：nt 轮外 × kb 块内，
        // 每 (nTile, K-块) 一个 scaleRowW 向量，起始 = scaleBAddr（经顶层
        // regs(10) 重写）。外部布局 scaleB[kb][n] 连续（每 kb 占 regFile.n
        // 字节），故块内 kb 步进 = regFile.n 字节（rowStep）；lineTarget =
        // 每轮 beat 数 = kBlocks = kTile>>1，共 nTile 轮；轮间跳转由
        // sNEXT_COL 处理（colAddr 累计 nt 轮偏移，下一轮基址 = bAddr +
        // (nt+1)*totalN）。与 executor pop 序（nt 轮内逐 K-块起始 pop）
        // 逐拍同序；nTile=1 时退化为连续线性、单 tile 行为不变。
        // 块内降序分支对本实例为死路。
        curAddr    := io.regFile.bAddr
        colAddr    := 0.U
        rowStep    := io.regFile.n
        lineTarget := io.calcConfig.kTile >> 1
        nCnt       := 0.U
        bNextKTileAddressOffset := 0.U
      } else {
        // B 矩阵：三级遍历「N 外 → kTile 中 → 块内行降序」。
        // 权重下沉协议下 PE(x,y) 最终锁存「最后供数行 - x」，每个 kTile 块
        // 必须按 K 行降序供数（先读块内最高行），executor 顺序 pop 供数后
        // 才能使 PE(x,y).wReg = B[kTile*totalN + x][y]（正序落位，标准 GEMM）。
        // 起始地址 = 块内最高行；块内每拍 -bRowOffset；块间用预计算跳转量。
        curAddr := io.regFile.bAddr + (cfg.totalN - 1).U * io.calcConfig.bRowAddressOffset
        colAddr   := io.calcConfig.bColTileAddressOffset + (cfg.totalN - 1).U * io.calcConfig.bRowAddressOffset
        rowStep   := io.calcConfig.bRowAddressOffset
        lineTarget  := cfg.totalN.U
        // 双模式 kTile 跳转步长：Mux 默认分支（loadWMode=0）逐字等于垂直加载既有公式。
        // 垂直：B 降序读取，下一 k 维 tile 需 +2*totalN*bRowOff（这里乘以 2 的原因是
        // B 矩阵是降序读取）；水平 loadW：转置布局下 k 切片步进 + 降序回补 =
        // totalN*(bRowOff + bW/8)
        bNextKTileAddressOffset := Mux(io.regFile.loadWMode, cfg.totalN.U * (io.calcConfig.bRowAddressOffset + (cfg.bW / 8).U), (cfg.totalN * 2 ).U * io.calcConfig.bRowAddressOffset)
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
  * @param hasFp    是否含浮点格式（编译期）。为 true 时在写出前对每个 PE slice
  *                 做 40→32 fp32 延迟规格化（LZC+左移）；为 false 时直接透传，
  *                 不生成规格化硬件。默认取 cfg.hasFp。
  * @param hasMx    是否含 MX 格式（编译期）。MX 复用 HiveMacFp 40-bit 延迟格式，
  *                 故与 hasFp 一样需 40→32 归一化。hasMx=true 时生成归一化硬件，
  *                 并在运行时按 aFmt 是否 MX 追加选择归一化通路（isFloat||isMx）。
  *                 hasMx=false 时本参数完全死路，选择逻辑逐字等于原 isFloat 分支。
  */
class HiveCoreDmaWrOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, hasFp: Boolean = false, hasMx: Boolean = false) extends Module {
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
    val regFile = Input(HiveCoreRegister(cfg))           // 仅使用 cAddr 基地址（loopMode 不影响 C 写回遍历）
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
  // C buffer pop → 写出侧 fp32 延迟规格化（延迟规格化架构核心）
  // 对每个 PE 的 cEffW-bit slice 取低 40-bit 做 LZC+左移规格化为 fp32，
  // 结果零扩展回 cEffW 位（保持外部接口位宽 popW 不变）。
  // 仅在 hasFp 时生成规格化硬件；纯整数配置时直接透传（无硬件开销）。
  // 纯组合逻辑，插在 bufPop 到 dmaExtWrIF.req.data 之间。
  // ==========================================================================
  val popDataFpNorm: UInt = if (hasFp || hasMx) {
    val normalizedSlices = (0 until cfg.totalN).map { i =>
      val slice = io.bufPop.payload(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
      val raw40 = slice(39, 0)
      val normalized32 = Fp32.fpNormalize40to32(raw40)
      // 零扩展回 cEffW 位（hasFp 下 cEffW >= 40 > 32，恒走 Cat 分支）
      if (cfg.cEffW == 32) normalized32
      else Cat(0.U((cfg.cEffW - 32).W), normalized32)
    }
    Cat(normalizedSlices.reverse)
  } else {
    io.bufPop.payload
  }

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  val isFloat = RegInit(false.B)
  isFloat := io.regFile.isFloat
  // 归一化通路选择：hasMx=false 时逐字等于原 isFloat（无额外寄存器/逻辑，bit-exact）；
  // hasMx=true 时追加 isMx 项——MX 累加同样是 40-bit 延迟格式，须走 fpNormalize40to32。
  val normSel: Bool = if (hasMx) {
    val isMxReg = RegInit(false.B)
    isMxReg := DataFormat.isMx(io.regFile.aFmt)
    isFloat || isMxReg
  } else isFloat

  io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & io.bufPop.valid
  io.dmaExtWrIF.req.payload.addr         := curAddr
  io.dmaExtWrIF.req.payload.data         := Mux(normSel, popDataFpNorm, io.bufPop.payload)
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
