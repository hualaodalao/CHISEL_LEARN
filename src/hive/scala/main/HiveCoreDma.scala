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
class HiveCoreDmaRdOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, bufDepth: Int = 0) extends Module {
  val pushW = bufWidth 
  val availDepth = bufDepth
  // MX scale 读搬运已拆分为独立模块 HiveCoreDmaScaleRd（scaleA/scaleB），
  // 本模块回到纯 A/B 数据双模式。

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

  // MX v2 SIMD 运行期判据（与顶层 mxRun 同语义）：非 MX 配置（!cfg.hasMx）
  // 编译期短路为 false，不生成比较逻辑，非 MX 路径 bit-exact。
  private val mxRunDma: Bool = if (cfg.hasMx) DataFormat.isMx(io.regFile.aFmt) else false.B

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
            state := sNEXT_COL
            lineCounter := 0.U
          }.otherwise{
            lineCounter := lineCounter + 1.U
          }
          when(io.isA){
            curAddr := curAddr + rowStep
          }.otherwise{
            // B 块内降序（权重下沉协议，见锁存块注释）
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
      // start 重载（契约对齐）：修复「config-err 后卡 sDONE 等 credit」死锁链——
      // executor errState 不 pop → ostCredit 不返还 → 原实现永久等待；
      // 现 start 拍同周期锁存新参数（上方 |io.start 锁存块）并复位 credit/计数、
      // 直达 sTRANSFER 重启遍历（flush 不返还的 credit 在此恢复）。
      // last-connect 自洽：.elsewhen 分支的 state := sIDLE 在 start 拍不可达（when 优先）；
      // 次拍以新参数/满 credit/零计数从 sTRANSFER 起跑（与 HiveCoreDmaScaleRd 同构）
      when(io.start) {
        state       := sTRANSFER
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
      // K pass 只消费实际 M 行，若按 mTile*totalN 扫描会把 padding 行混入 FIFO。
      // 【既有 wart 留痕（v1 即存在，另立任务；仅注释，不改行为）】
      curAddr   := io.regFile.aAddr
      colAddr   := io.calcConfig.aColTileAddressOffset
      rowStep   := io.calcConfig.aRowAddressOffset
      lineTarget  := io.regFile.m
    }.otherwise {
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
        // B 矩阵是降序读取）。
        // 水平 loadW 通用推导：块末拍后 curAddr 已越过块内最低行一拍（= start(kt)
        //   - totalN*bRowOff，降序多减一拍），下一块起点 start(kt+1) = start(kt)
        //   + kSliceStep（转置行内 k 窗口步进），故跳转量 = kSliceStep + totalN*bRowOff：
        //   - 非 MX（1 元素/拍）：kSliceStep = totalN*(bW/8)（16 元素×元素字节）
        //     → totalN*(bRowOff + bW/8)（原公式逐字保留）
        //   - MX v2（2 元素/拍打包，1 字节/元素）：一个 kTile 窗口 = 32 元素 = 32 字节，
        //     kSliceStep = 2*totalN 字节（行步长 bRowOff 已由 calcConfig 给为打包 K 字节）
        //     → totalN*(bRowOff + 2)，其中 2 = kSliceStep/totalN（每 lane 2 字节）。
        //   MX 运行必为 loadWMode=1（executor sIDLE 已校验），垂直分支对 MX 为死路。
        bNextKTileAddressOffset := Mux(io.regFile.loadWMode,
          Mux(mxRunDma, cfg.totalN.U * (io.calcConfig.bRowAddressOffset + 2.U),
                      cfg.totalN.U * (io.calcConfig.bRowAddressOffset + (cfg.bW / 8).U)),
          (cfg.totalN * 2 ).U * io.calcConfig.bRowAddressOffset)
    }
  }
}


/** HiveCoreDmaWrOnly — 只写 DMA 引擎（多拍发射器，P2 两段式架构）。
  *
  * 职责：消费顶层 HiveOutCvt 输出的转换后密集行（行粒度握手，fire 即锁存进本模块
  * 行寄存器），按 tile 自动地址生成、以 cDmaExtW 位宽逐拍发往外部存储。
  * 拍拆分归本模块（计划裁定 4：cvt = 转换+行寄存器，cDma = 多拍发射器）。
  *
  * 搬运对象固定为结果矩阵 C[mTile, nTile]。C 写回固定「内 M 外 N」遍历：
  * C buffer 为严格 FIFO（上游顺序 = Executor push 顺序），写回顺序与 loopMode 无关。
  *   - 内层沿 M 方向（实际 M 行 regFile.m，边界感知不含 padding，
  *     每行地址 + cRowAddressOffset），外层扫 nTile 个 N tile
  *     （块间跳 cColAddressOffset）
  *
  * 地址模型（与 HiveCoreExecutor 的 cStoreAddr 约定一致，字节地址）：
  *   C_tile_addr = regCAddr + mTileIdx*totalN*cRowOffset + nTileIdx*cColOffset
  * 块间跳转采用「跳回起点再前移一个块」的三角累积增量式计算（金锚背书，行级不动）：
  *   jump = blockOffset - (innerRows - 1) * rowStep
  * 拍内地址 = curAddr + beatCnt × calcConfig.cBeatAddrStep（顶层单源运行期步进）。
  *
  * 运行期拍数（裁定 6）：beatsPerRowEff = ceil(totalN×elemBits/cDmaExtW)，由 io.outFmt
  *（顶层唯一源 cOutFmtEff）解码：256 默认 × totalN=16 下 fp32/int32=2 拍、
  * fp16/bf16/int16/mxfp8/int8/mxfp4=1 拍（半/四分之一占用高位零填充，禁止双行合拍）。
  * 末拍推进单一判据：req.fire && beatCnt===beatsPerRowEff-1 → 行推进（行级结构不动，
  * 仅叠拍内偏移）；行/块跳转自动对齐、免维护拍地址寄存器。
  *
  * 反压策略（与 RdOnly 对称）：
  *   - bufPop.ready = sTRANSFER_M 且行锁存空（行粒度握手，每行只收一次）；
  *     cvt 侧持行直到本模块末拍接受（out.ready 透传本模块内部决定）
  *   - 数据在外部 writeData.ready 为高时才发拍，天然支持逐拍暂停
  *
  * 门控语义：storeGate 门控位于上游（顶层 cPop mux ready 源 + cvt.in.valid 门），
  * 本模块由 peekBlock/doneBlock 块协议与 Executor 同步；非末 K pass 期间不会收到行。
  * 内层行数锁存实际 M 行（regFile.m）：边界 M 下 Executor 只 push 实际
  * M 行，若期望 mTile*totalN 行会永久等待挂死。
  *
  * 运行模式：启动后完全自主运行——逐 tile 块连续写回，块间不做任何
  * 上游握手，直到所有 tile 块搬完后发一拍 done 脉冲回 IDLE。
  *
  * 【外部写通道契约】写回拍须按地址顺序提交（半占用拍零填充高位与密集行步进的正确性依赖保序写）；外部存储侧若乱序受理写拍，行内/行间布局无法保证。
  *
  * 【P2 两段式分工（裁定 4/6）】精度转换与行寄存器归顶层 HiveOutCvt（握手模块），
  * 本模块只消费转换后密集行（bufPop，宽 totalN×32，按 outFmt 选段、高位已由 cvt 填零）
  * 并拆拍发出；格式族判据单源化：io.outFmt ← 顶层唯一源 cOutFmtEff（废除旧「整数强制透传 000」判据）。
  *
  * start 语义：io.start 在任意状态均可重新装载（复位计数/错误、重新锁存遍历参数），
  * 用于异常路径后的强制重启（同 RdOnly 说明）。
  * 安全性：脱离 sTRANSFER_M 时 bufPop.ready/writeData.valid 自然拉低，无协议违约。
  *
  * @param cfg      HiveCore 配置
  * @param bufWidth P2 签名保留参数（语义已死：bufPop 位宽固定 = totalN×32 密集行）
  * @param hasFp    P2 签名保留参数（语义已死：规格化硬件随 cvt 上移顶层）
  * @param hasMx    P2 签名保留参数（语义已死：量化支路随 cvt 上移顶层；
  *                 cScalePush 端口删除，scale 改由顶层 cvt.scaleOut → 深 2 cScaleFifo 提交）
  */
class HiveCoreDmaWrOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, hasFp: Boolean = false, hasMx: Boolean = false) extends Module {
  // 总线侧写回位宽：P0 契约直取 cfg.cDmaExtW（默认 256）。
  val extW = cfg.cDmaExtW
  // bufPop 侧位宽：消费顶层 cvt.out 转换后密集行（行粒度，全设计唯一行寄存器在 cvt 内，
  // 本模块行锁存仅作发拍暂存——拍拆分必须切自锁存值，防发拍期间上游前移错位）。
  val popW = cfg.totalN * 32
  // 拍计数器位宽：按编译期最大拍数上界 cBeatsPerRowMax = totalN*32/cDmaExtW
  private val beatCntW = math.max(1, log2Up(cfg.cBeatsPerRowMax))

  val io = IO(new Bundle {
    // 控制/状态（块协议：peekBlock/doneBlock 与 Executor 同步）
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val peekBlock  = Input(Bool())                   // 写出一个M维Block控制脉冲
    val doneBlock = Output(Bool())                   // 完成一个M维Block控制脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtWrIF = new HiveCoreDMAExtWriteOnlyIF(cfg, extW)
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
    val regFile = Input(HiveCoreRegister(cfg))           // 仅使用 cAddr/m（loopMode 不影响 C 写回遍历）
    val outFmt = Input(UInt(4.W))                    // 顶层唯一判据源 cOutFmtEff（拍数解码专用）
    val bufPop = slave(Stream(UInt(popW.W)))         // 消费 cvt.out 转换后整行（行粒度，fire 即锁存）
    val blkRxFull = Output(Bool())                   // 本块已收满 m 行（顶层据此闸断上游超弹）
    val bufOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))  // P2 无本模块内消费者（保顶层接线稳定）
    val nCntOut = Output(UInt(cfg.mnkWidth.W))       // 回供当前 N tile 序号，顶层算 cvt.nEff
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
  // credit 记账口径 = 「拍数」（修复留痕）：窄总线下每行只 pop 一次但拆多拍写出，
  // 最大在途拍数 = cBufferDepth × 每行拍数；旧口径 log2Up(cBufferDepth+1)（行数）
  // 在多拍下发时可回绕，致 sDONE 误判 ostCredit===0 提前放行，故按拍数口径拓宽。
  // 【W-Paul 修复】+2 行余量：拍推进、行推进与外部 rsp 延迟可产生瞬态峰值，
  // 用 (cBufferDepth+2)*cBeatsPerRowMax 吸收。
  val ostCredit = RegInit(0.U(log2Up((cfg.cBufferDepth + 2) * cfg.cBeatsPerRowMax + 1).W))

  // ==========================================================================
  // 发拍行锁存（bufPop fire 拍锁存 cvt.out 整行；发拍段必须切自锁存值——
  // 若切自组合直通，发送拍期间上游已前移到下一行，总线数据会整行错位）
  // ==========================================================================
  val lineData  = Reg(UInt(popW.W))
  val lineValid = RegInit(false.B)
  val beatCnt   = RegInit(0.U(beatCntW.W))

  // ==========================================================================
  // 运行期拍数解码（裁定 6；唯一判据源 io.outFmt ← 顶层 cOutFmtEff）：
  // 元素位宽档 = fp32/int32/reserved 回落→32、fp16/bf16/int16→16、
  // mxfp8/int8→8、mxfp4→4（半字节）；beatsPerRowEff = ceil(totalN×elemBits/cDmaExtW)，
  // 256 默认下 ∈ {1,2}（mxfp4 行 64bit → 1 拍；禁止双行合拍，裁定 9）。
  // reserved 编码（3/7/1011..1111）回落 32 档，与 cvt 选段/顶层布局同源。
  // ==========================================================================
  
    
  private val elemBits: UInt = MuxCase(32.U(6.W), Seq(
    (io.outFmt === 1.U || io.outFmt === 2.U || io.outFmt === 9.U) -> 16.U(6.W),
    (io.outFmt === 4.U || io.outFmt === 5.U || io.outFmt === 10.U) -> 8.U(6.W),
    (io.outFmt === 6.U) -> 4.U(6.W)))
  private val beatsPerRowEff: UInt =
    (cfg.totalN.U * elemBits + (cfg.cDmaExtW - 1).U) >> log2Ceil(cfg.cDmaExtW)
  // 末拍推进单一判据（取代原按格式的多分支）
  private val lastBeat: Bool = beatCnt === beatsPerRowEff - 1.U

  io.nCntOut := nCnt  // 回供顶层算 cvt.nEff（原 WrOnly 内 nEff 组合逻辑已迁出）

  // P2：内嵌 HiveOutCvt 例化已删除（裁定 4）——cvt 上移 HiveCore 顶层（握手模块：
  // in ← cPop mux、out → 本模块 bufPop、scaleOut → 顶层深 2 cScaleFifo），
  // 原 TODO(P2) 临时接法（8+3 处）随本重构全部清除。

  // P2：ActiveRowSplit 已删除（裁定 4/6）——行寄存器职责归顶层 cvt（全设计唯一一份），
  // 本模块仅保留上方 lineData/lineValid/beatCnt 发拍暂存；拍段切分按总线位宽固定切自锁存行。
  // P2：popDataFpNorm（40→32 延迟规格化）已删除——规格化下沉到 cvt 数值核（fpNormalize40to32），
  // 本模块收到的已是转换后密集行。

  // ==========================================================================
  // 默认输出驱动（P2：isFloat/normSel/outFmtEff 判据族已随两段式重构删除，
  // 格式判据唯一源 = io.outFmt ← 顶层 cOutFmtEff，见上方拍数解码块）
  // ==========================================================================
  io.dmaExtWrIF.rsp.ready        := true.B

  // ==========================================================================
  // 写回握手（P2 两段式，裁定 4/6；取代原 legacy/active 编译期双分支与
  // outFmtEff/isQuant/normSel 判据族）：
  //   收行：bufPop.ready = sTRANSFER_M 且行锁存空 → 每行只收一次，
  //         fire 拍原子锁存 cvt.out 整行（宽 totalN×32，已按 outFmt 选段、
  //         高位由 cvt 填零，禁止双行合拍，裁定 9）。
  //   发拍：锁存有数据即发拍，共 beatsPerRowEff 拍（运行期解码，见上）；
  //         拍内地址 = curAddr + beatCnt × cBeatAddrStep（顶层单源），
  //         数据 = 锁存行按总线位宽右移取段。curAddr 只在行边界更新，
  //         行/块跳转自动对齐、免维护拍地址寄存器。
  //   bufPop.fire 与 req.fire 由 lineValid 互斥（锁存空/满二态），无同拍双写。
  // ==========================================================================
  // 【块末行窗口竞态防护：末行锁存后关收行门（P3 定位）】块内收行计数：本块收满
  // m 行（末行锁存完成）后关收行门，杜绝上游在末行之后超前预取不存在的第 m+1
  // 行（波形实证：cPop=33/bufPopFire=32 对账，块错位/死锁两形态）。
  // 门控判据必须用收行计数而非 lineCounter：lineCounter===m-1 在末行锁存前即成立，
  // 直接关门会阻断末行接收（实测死锁：store beats 停在 (m-1)×beatsPerRow），
  // 故按「末行锁存后关门」语义以 blkRxCnt 记账（P3 定位）。
  private val blkRxCnt = RegInit(0.U(cfg.mnkWidth.W))
  when(io.bufPop.fire) { blkRxCnt := blkRxCnt + 1.U }
  when(io.doneBlock || io.start) { blkRxCnt := 0.U }   // 块末/重启归零（last-connect）
  io.blkRxFull := blkRxCnt === io.regFile.m            // 本块收满 m 行（供顶层闸断上游超弹）
  io.bufPop.ready := (state === sTRANSFER_M) & !lineValid & (blkRxCnt =/= io.regFile.m)
  when((state === sTRANSFER_M) && io.bufPop.fire) {
    lineData  := io.bufPop.payload
    lineValid := true.B
    beatCnt   := 0.U
  }

  io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & lineValid
  io.dmaExtWrIF.req.payload.addr := curAddr + beatCnt * io.calcConfig.cBeatAddrStep
  io.dmaExtWrIF.req.payload.data := (lineData >> (beatCnt * cfg.cDmaExtW.U))(cfg.cDmaExtW - 1, 0)

  // 拍推进：末拍清行锁存并回零拍计数（行推进动作在下方 switch sTRANSFER_M 分支）
  when((state === sTRANSFER_M) && io.dmaExtWrIF.req.fire) {
    when(lastBeat) {
      lineValid := false.B
      beatCnt   := 0.U
    }.otherwise {
      beatCnt := beatCnt + 1.U
    }
  }

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
  

  // ==========================================================================
  // start 重载公共动作（任意状态生效，引用同文件 RdOnly 的 sTRANSFER/sDONE
  // 重载同构实现）：回 sFIRST_PEEK、清 errReg/lineCounter/nCnt、重载
  // curAddr/rowStep/colAddr；active 另清 lineValid/beatCnt（防异常路径残留
  // 半行状态）。正常流程 start 仅出现在 sIDLE，其余状态的重载分支为死路、
  // 行为不变；异常路径（Executor 回 idle 后下次 EXECUTE）可强制重启卡死状态。
  // ==========================================================================
  private def startReload(): Unit = {
    state       := sFIRST_PEEK
    errReg      := false.B
    lineCounter := 0.U
    // C 矩阵：固定「内 M 外 N」遍历（C buffer 为 FIFO，写回顺序 =
    // Executor push 顺序，与 loopMode 无关）
    curAddr := io.regFile.cAddr
    // 内层沿实际 M 行（regFile.m，边界感知不含 padding），外层扫 nTile 个 N tile
    rowStep := io.calcConfig.cRowAddressOffset
    colAddr := io.calcConfig.cColTileAddressOffset
    nCnt    := 0.U
    // 强制重启时同步清行锁存/拍计数（防异常路径残留半行状态；
    // 正常流程回 sIDLE 前锁存已自然清空）
    lineValid := false.B
    beatCnt   := 0.U
  }

  switch(state) {

    // --- sIDLE: 等待 start ---
    is(sIDLE) { 
      // ==========================================================================
      // start 重新装载（任意状态生效，语义与安全性说明见 RdOnly 同构实现）：
      // Executor 异常回 idle 后下次 EXECUTE 必须能强制重启卡死的本 DMA
      // ==========================================================================
      when(io.start) {
        startReload()
      }


    }
    
    is(sFIRST_PEEK) {
      // start 重载（契约对齐，同 RdOnly）：等 peek 拍收到 start → 重载回起点；
      // 正常流程 start 不出现在本状态，分支死路、行为不变
      when(io.start) {
        startReload()
      }.elsewhen(io.peekBlock) {
        state := sTRANSFER_M
      }
    }

    is(sTRANSFER_M){
      // start 重载（契约对齐，同 RdOnly sTRANSFER）：搬运途中强制重启 →
      // 同拍重载参数并回 sFIRST_PEEK（last-connect：上方拍推进/行锁存块
      // 本拍若有赋值，均被 startReload 的 lineValid/beatCnt 覆盖）
      when(io.start) {
        startReload()
      }.otherwise {
        // 末拍（行内最后一拍）才做行推进（末拍推进单一判据，裁定 6；
        // 取代原按格式的 legacy/active/quant 多分支）；
        // 拍计数/行锁存清除在上方拍推进 when 块处理。
        when(io.dmaExtWrIF.req.fire && lastBeat) {
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
    }


    is(sNEXT_N) {
      // start 重载（契约对齐，同 RdOnly）：块间跳转拍强制重启 → 重载回起点；
      // 下一拍若未重载也会被 sTRANSFER_M/sDONE 分支捕获（双保险）
      when(io.start) {
        startReload()
      }.elsewhen(nCnt === io.calcConfig.nTile - 1.U){
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
      // start 重载（契约对齐，同 RdOnly sDONE）：绕过 sIDLE 时 credit 归零
      // 不执行，故重载另需 ostCredit := 0.U（last-connect 覆盖上方
      // state=/=sIDLE 分支的同拍增减）；重启后以新写回拍重新累计在途。
      when(io.start) {
        startReload()
        ostCredit := 0.U
      }.elsewhen(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }
}
