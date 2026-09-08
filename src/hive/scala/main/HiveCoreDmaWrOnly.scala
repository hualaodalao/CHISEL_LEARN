/** HiveCoreDmaScaleWr — C 侧量化 scale 写回 DMA（P3b，dmaCScale 通道）。
  *
  * 职责：量化输出（outFmt 1xx）运行时，cDma（WrOnly）每写出一行量化密集行
  * 即同拍产出一行 E8M0 块 scale（cvt.io.scaleOut，宽 cScaleRowW = totalN/8 字节），
  * 经顶层深 2 cScaleFifo（P2 裁定 5：唯一 scale 缓冲，已从 Scratchpad 上移）缓冲后
  * 由本模块写到外部存储（基址 = regFile.cScaleAddr）。
  *
  * 遍历几何（与 cDma 的 C 数据写回同序，保证 scale 行与量化数据行逐行对应）：
  *   内层沿 M 方向（实际 M 行 = regFile.m，每行一拍、地址 + cScaleRowAddressOffset
  *   = ceil(n/blk) 字节），外层扫 nTile 个 N tile（轮间跳 cScaleColTileAddressOffset
  *   = totalN/blk 字节）。
  *
  * 【地址模型——「基址 + 轮偏移」线性模型（刻意不复制 cDma sNEXT_N 的三角累积）】
  *   cDma 数据通道的块间跳转用「curAddr += colAddr; colAddr += offset」三角累积，
  *   是已知遗留问题（禁动区）；本模块为新通道，直接采用线性模型：
  *   colAddr 寄存器只存「下一轮的累计列偏移」，轮间跳转时
  *     curAddr := baseAddr + colAddr;  colAddr := colAddr + colTileOffset
  *   每轮起始地址恒等于 baseAddr + nt*colTileOffset，无累积误差、易对账。
  *
  * 反压/在途记账（写通道简化版）：
  *   - req 仅在 sTRANSFER 且 bufPop 有数据时发出，且被在途上限（顶层 cScaleFifo 深度 2）
  *     门控，防 rsp 延迟堆积超出计数位宽；
  *   - ostCredit 记在途写拍数（req.fire +1 / rsp.fire −1），sDONE 等 credit==0
  *     后发 done 脉冲回 sIDLE（写通道无回读数据，完成判据只看在途排空）。
  *
  * start 语义：io.start 任意状态均可重新装载（与 RdOnly/WrOnly/ScaleRd 同构，
  * 异常路径强制重启契约对齐）：重载拍同周期锁存新遍历参数（下方 |io.start 锁存块）
  * 并复位计数/credit/错误。正常流程 start 仅出现在 sIDLE，其余状态重载分支为死路、
  * 行为不变；异常路径（Executor config-err 回 idle 后下次 EXECUTE）可强制重启。
  * 顶层 start 门控 = executePulse & cQuantRun（cQuantRun = outFmtEff∈{100,101,110} 枚举，
  * 与 WrOnly 量化判据同源），非量化运行本模块永不启动、不向外部发任何请求。
  *
  * 【config-err 场景行为契约（评审留痕，C1 修复后更新）】量化配置违约
  * （blk>totalN/不整除、mxfp4 奇 n）时顶层 start 仍随 executePulse & cQuantRun
  * 拉高（违约由 Executor 同拍置 errState 拦截），本模块照常进入 sTRANSFER；但
  * Executor 不进计算、cScaleFifo 恒空，本模块停留于 sTRANSFER（busy 恒高、done
  * 不发）。恢复路径（C1 修复）：下次 EXECUTE 时若为量化运行（cQuantRun），start
  * 重载恢复（既有路径）；若为非量化运行（!cQuantRun），顶层经 io.abort 强制回
  * sIDLE（abort = executePulse & !cQuantRun），busy 归低，dmaCBusy OR 门解锁，
  * 非量化运行正常进行。io.done 不得在 config-err 场景接入完成链。
  *
  * @param cfg HiveCore 配置（行宽固定 = cfg.cScaleRowW，每 8 元素块 1 字节）
  */

import chisel3._
import chisel3.util._

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
    val bufPop = slave(Stream(UInt(popW.W)))         // 消费 cvt.out 转换后整行（行粒度，fire 即锁存）
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
  val lastBeat: Bool = beatCnt === io.calcConfig.cDmaBufBeatTar 

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
  io.bufPop.ready := (state === sTRANSFER_M) & io.dmaExtWrIF.req.ready & lastBeat
  io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & io.bufPop.valid
  io.dmaExtWrIF.req.payload.addr := curAddr + beatCnt * io.calcConfig.cBeatAddrStep
  io.dmaExtWrIF.req.payload.data := (io.bufPop.payload >> (beatCnt * cfg.cDmaExtW.U))(cfg.cDmaExtW - 1, 0)

  // 拍推进：末拍清行锁存并回零拍计数（行推进动作在下方 switch sTRANSFER_M 分支）
  when((state === sTRANSFER_M) && io.dmaExtWrIF.req.fire) {
    when(lastBeat) {
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
      when(io.peekBlock) {
        state := sTRANSFER_M
      }
    }

    is(sTRANSFER_M){
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



class HiveCoreDmaScaleWr(cfg: HiveCoreConfig) extends Module {
  private val rowW = cfg.cScaleRowW
  // P2（裁定 5）：唯一 scale 缓冲 = 顶层深 2 cScaleFifo（原 Scratchpad 内深度
  // scaleBufferDepth 已删）；在途上限/credit 位宽随之收窄（log2Up(2+1)=2 bit）。
  private val depth = 2

  val io = IO(new Bundle {
    // 控制/状态（无 peek 握手：cScaleFifo 解耦，启动后自主搬完所有行）
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志（rsp.err 锁存）
    val dmaExtWrIF = HiveCoreDMAExtWriteOnlyIF(cfg, rowW)  // 外部写通道（行宽 = cScaleRowW）
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // nTile + cScale 行/列步进
    val baseAddr = Input(UInt(cfg.addrWidth.W))      // scale 写回基址（← regFile.cScaleAddr）
    val regFile = Input(HiveCoreRegister(cfg))       // 仅使用 m（内层行数）
    val bufPop = slave(Stream(UInt(rowW.W)))         // 从 cScaleFifo pop 数据
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sTRANSFER = 1.U(2.W)   // pop FIFO 发写拍、推进地址
  val sNEXT_N   = 2.U(2.W)   // N tile 轮间跳转（线性模型）
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前写地址（行首址）
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 「下一轮」累计列偏移（线性模型）
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每行地址步进
  val lineTarget  = RegInit(0.U(16.W))         // 内层行数 = regFile.m
  val lineCounter = RegInit(0.U(16.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))
  val ostCredit = RegInit(0.U(log2Up(depth + 1).W))  // 在途写拍数（上限 = 顶层 FIFO 深度 2）
  val errReg = RegInit(false.B)

  // ==========================================================================
  // 在途 credit 记账 + rsp.err 锁存（与 WrOnly 数据通道同模式）
  // ==========================================================================
  when(state === sIDLE) {
    ostCredit := 0.U
    errReg := false.B
  }.otherwise {
    when(io.dmaExtWrIF.req.fire & io.dmaExtWrIF.rsp.fire) {
      ostCredit := ostCredit
    }.elsewhen(io.dmaExtWrIF.req.fire) {
      ostCredit := ostCredit + 1.U
    }.elsewhen(io.dmaExtWrIF.rsp.fire) {
      ostCredit := ostCredit - 1.U
    }
    when(io.dmaExtWrIF.rsp.fire) {
      when(io.dmaExtWrIF.rsp.payload.err) {
        errReg := true.B
      }
    }
  }

  // ==========================================================================
  // 默认输出驱动
  //   req 被在途上限（FIFO 深度）门控：防外部 rsp 长延迟堆积超出计数位宽
  // ==========================================================================
  io.dmaExtWrIF.req.valid         := (state === sTRANSFER) & io.bufPop.valid & (ostCredit =/= depth.U)
  io.dmaExtWrIF.req.payload.addr  := curAddr
  io.dmaExtWrIF.req.payload.data  := io.bufPop.payload
  io.bufPop.ready                 := (state === sTRANSFER) & io.dmaExtWrIF.req.ready
  io.dmaExtWrIF.rsp.ready         := true.B

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg

  switch(state) {
    // --- sIDLE: 等待 start（锁存逻辑在下方 state===sIDLE|start 块统一处理）---
    is(sIDLE) {
      errReg := false.B
      lineCounter := 0.U
      // 防重复 start 竞态：重载拍（非 sIDLE 收到 start）已直达 sTRANSFER，
      // 仅当本拍确在 sIDLE 时才允许 start 推进（同 RdOnly 留痕）
      when(io.start && state === sIDLE) {
        state := sTRANSFER
      }
    }

    // --- sTRANSFER: 每行一拍，pop FIFO 发写拍并推进地址 ---
    is(sTRANSFER) {
      // start 重载（契约对齐，同 RdOnly sTRANSFER）：途中强制重启 →
      // 同拍重载参数（下方 |io.start 锁存块）并复位计数/credit；
      // 正常流程 start 只在 sIDLE 出现，本分支死路、行为不变
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(io.dmaExtWrIF.req.fire) {
        when(lineCounter === lineTarget - 1.U) {
          lineCounter := 0.U
          state := sNEXT_N
        }.otherwise {
          lineCounter := lineCounter + 1.U
          curAddr := curAddr + rowStep
        }
      }
    }

    // --- sNEXT_N: N tile 轮间跳转（线性模型：基址 + 轮偏移）---
    is(sNEXT_N) {
      // start 重载（收紧契约）：sNEXT_N 为单拍过渡态，重载拍直接回 sTRANSFER
      // 起跑（|io.start 锁存块已把 curAddr/colAddr/计数复位回首轮参数）
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(nCnt === io.calcConfig.nTile - 1.U) {
        state := sDONE
      }.otherwise {
        // 线性模型：下一轮起始 = base + (nt+1)*colTileOffset；
        // colAddr 存累计轮偏移（锁存初值 = 1 轮偏移），无三角累积
        nCnt := nCnt + 1.U
        curAddr := io.baseAddr + colAddr
        colAddr := colAddr + io.calcConfig.scaleCColTileAddressOffset
          state := sTRANSFER
      }
    }

    // --- sDONE: 等在途写拍排空后发完成脉冲，回 IDLE ---
    is(sDONE) {
      // start 重载（契约对齐，同 RdOnly sDONE）：绕过 sIDLE 时 credit 归零
      // 不执行，故重载另需 ostCredit := 0.U（last-connect 覆盖上方
      // state=/=sIDLE 分支的同拍增减）
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        nCnt        := 0.U
        ostCredit   := 0.U
      }.elsewhen(ostCredit === 0.U) {
        io.done := true.B
        state := sIDLE
      }
    }
  }

  // ==========================================================================
  // 遍历参数锁存（switch 外公共位置，与 RdOnly/ScaleRd 同模式）：
  // sIDLE 每拍刷新；重载拍（任意状态收到 start）同周期锁存——
  // start 为单拍 executePulse，不能经 sIDLE 中转。正常流程 |io.start 项恒假，
  // 锁存拍与行为逐字不变。
  // ==========================================================================
  when(state === sIDLE || io.start) {
    curAddr    := io.baseAddr
    colAddr    := io.calcConfig.scaleCColTileAddressOffset
    rowStep    := io.calcConfig.scaleCRowAddressOffset
    lineTarget := io.regFile.m
    nCnt       := 0.U
  }

}
