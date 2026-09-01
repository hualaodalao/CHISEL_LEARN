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
  * 【P1 缩位写回（cfg.cBeatsPerRow>1，active）】总线位宽 < 行宽时行内拆多拍：
  * bufPop 仍每行只 pop 一次（Executor 侧背压语义不变），行数据锁存后拆为
  * cBeatsPerRow 拍、每拍 cElementsPerBeat 个元素密集打包（每元素取 40→32 规格化后
  * 的 32-bit 结果，不再零扩展回 40 位槽；低列号在低位，尾拍高位零填充）；
  * 拍内地址步进 = E×4 字节，行推进（lineCounter/curAddr/doneBlock/sNEXT_N）
  * 只在末拍发生。编译期 if/else 分支：默认哨兵配置（cBeatsPerRow==1，legacy）
  * 下功能行为与重构前一致（一拍一行），且不产生行锁存寄存器；注意该等价性
  * 仅指 outFmt=00 的功能行为（写回数据/地址/时序），hasFp/hasMx 配置含
  * HiveOutCvt 转换资源（面积/时序影响）。
  *
  * 【外部写通道契约】写回拍须按地址顺序提交（满宽拍零填充高位与 2B/4B 密集
  * 行步进的正确性依赖保序写）；外部存储侧若乱序受理写拍，行内/行间布局无法保证。
  *
  * 【P2 精度转换（outFmt）】写回输出格式运行期可选（3-bit 编码，regs(7)(13,11)，
  * 见 HiveCoreRegister.outFmt）：000=fp32（默认）/001=fp16/010=bf16/011=reserved（回落 000）/
  * 100=E4M3 量化/101=E5M2 量化/110=E2M1 量化/111=reserved（降级 000，量化输出由 P3b 消费）。
  * 有效值同源判据：
  *   outFmtEff = (isFloat || isMx) ? outFmt : fp32
  * 非浮点运行强制走透传路径（整数累加值连 40→32 规格化都不做，与现状 bit-exact）。
  * 浮点/MX 运行由 HiveOutCvt（纯组合）逐元素 40→32→{32|16}：
  *   - legacy（cBeatsPerRow==1）：outFmtEff==fp32 时逐字等于现状（40→32 规格化后零扩展回 40 位槽）；
  *     outFmtEff≠fp32 时一行密集 16-bit 元素放总线低位、高位零填充，仍按 1 拍/行发出。
  *   - active：lineData 锁存密集行（fp32 时 totalN*32 位、fp16/bf16 时低 totalN*16 位有效），
  *     拍段切分按运行期元素位宽（fp32=32/fp16=bf16=16）右移选段；拍内地址步进 = E×outElemBytes
  *     （outElemBytes = fp32?4:2，与 HiveCore calcConfig cRow/cCol 同源）。cBeatsPerRow 与 E 不变：
  *     拍数由元素数决定、与格式无关（单行数据位宽 ≤ 总线位宽时也不合并拍，契约已定）。
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
  // 总线侧写回位宽：默认哨兵 = cExtW（legacy 一拍一行）；缩位时行内拆多拍。
  // bufPop 侧仍为整行宽 popW：每行只 pop 一次，两个位宽自本模块起解耦。
  val extW = cfg.cDmaExtWEff

  val io = IO(new Bundle {
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val peekBlock  = Input(Bool())                   // 写出一个M维Block控制脉冲
    val doneBlock = Output(Bool())                   // 完成一个M维Block控制脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtWrIF = new HiveCoreDMAExtWriteOnlyIF(cfg, extW)
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数与地址偏移
    val regFile = Input(HiveCoreRegister(cfg))           // 仅使用 cAddr 基地址（loopMode 不影响 C 写回遍历）
    val bufPop = slave(Stream(UInt(popW.W)))         // 从 C buffer pop 数据
    val bufOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
    // P3b 量化 scale 行推口（仅 hasMx）：每量化行 pop 拍同拍 push 本行 E8M0 块
    // scale（cvt.io.scaleRow，宽 cScaleRowW），与行 pop 锁步（行 pop 条件追加
    // cScalePush.ready 门）。非量化运行无 push，死路。
    val cScalePush = if (hasMx) Some(master(Stream(UInt(cfg.cScaleRowW.W)))) else None
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
  // credit 记账口径 = 「拍数」（修复留痕）：active 缩位下每行只 pop 一次但拆
  // cBeatsPerRow 拍写出，最大在途拍数 = cBufferDepth × cBeatsPerRow；
  // 旧口径 log2Up(cBufferDepth+1)（行数）在 active 多拍下发时可回绕，
  // 致 sDONE 误判 ostCredit===0 提前放行，故按拍数口径拓宽。
  val ostCredit = RegInit(0.U(log2Up(cfg.cBufferDepth * cfg.cBeatsPerRow + 1).W))

  // ==========================================================================
  // 归一化/转换通路选择（P2：先于 ActiveRowSplit 定义，供其引用）
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

  // --- P3a outFmt 运行期有效值（与 HiveCore.scala cOutFmtEff 同源，互指）：
  // 浮点/MX 运行取 regFile.outFmt；整数运行强制 000（透传，不做精度转换）。
  // outFmt 编码（3-bit，P3a 由 2-bit 扩展）：000=fp32 001=fp16 010=bf16 011=rsv(同000)
  // 100=E4M3 101=E5M2 110=E2M1 量化 111=rsv(降级000，量化输出由 P3b 消费）。
  val outFmtEff: UInt = if (hasFp || hasMx) Mux(normSel, io.regFile.outFmt, 0.U(3.W)) else 0.U(3.W)
  // 16-bit 输出统一判据（与 HiveCore.cIsFmt16 同源、互指）：仅 001=fp16 / 010=bf16
  // 走 16-bit 通路；011=reserved 回落 fp32 语义（同 000），兑现注释承诺（裁定修复：
  // 旧判据「===0 才 fp32」致 11 误走 16-bit 输出 + 2B 步进）。P3a 扩 3-bit 后判据
  // 逐字不动：===1/===2 在 3-bit 下仍仅命中 001/010，011/111 回落非 16 位语义保持。
  // 数据/地址选路一律用 isFmt16，不再直接比较 outFmtEff===0。
  val isFmt16: Bool = outFmtEff === 1.U || outFmtEff === 2.U
  // 输出元素字节数（拍内/行/列地址步进同源）：fp32=4B、fp16/bf16=2B
  val outElemBytes: UInt = Mux(isFmt16, 2.U, 4.U)

  // --- P3b 量化输出判据（与 HiveCore quantRun/cIsFmt8/cIsFmt4 同源、互指，三处同一判据源）：
  // 量化判据统一为枚举白名单 isQuant = outFmtEff∈{100,101,110}（评审裁定修复，原单比特
  // outFmtEff(2) 判据会让 111=reserved 命中量化通路，产出「mxfp8 布局 + E2M1 编码」混合格式且
  // dmaCScale 照常启动，与「111=reserved 降级 000」文档声明冲突）；改枚举后 111 完整走 fp32
  // 回落路径：数据选路（isQuant=0 退化 fp32 臂）、地址步进（HiveCore cQuantRun=0 回 4B 布局）、
  // 不启动 cScaleDma（顶层 start 门 = cQuantRun）、不触发 Executor 量化校验（quantActive 同枚举）。
  // isFmt8 = mxfp8（E4M3/E5M2），isFmt4 = mxfp4（E2M1 半字节）。非 hasMx 配置编译期死 false，
  // 量化分支全死路，非 MX bit-exact（「默认配置逐拍等价」铁律：outFmt bit13=0 时 isQuant=0，
  // 下方所有量化 Mux 退化原式）。
  val isQuant: Bool = if (hasMx) (outFmtEff === 4.U || outFmtEff === 5.U || outFmtEff === 6.U) else false.B
  val isFmt8: Bool = if (hasMx) (outFmtEff === 4.U || outFmtEff === 5.U) else false.B
  val isFmt4: Bool = if (hasMx) (outFmtEff === 6.U) else false.B

  // --- P2 精度转换模块（仅含浮点/MX 配置例化；纯整数配置不生成此硬件）。
  // 输入直挂 bufPop payload（组合直通，锁存/发拍切割仍由下方行锁存承担，
  // 发拍段绝不直切组合行数据，教训留痕见 ActiveRowSplit 注释）。
  // rnd 复用 regFile.rnd（与阵列计算同舍入模式）。
  val cvt: Option[HiveOutCvt] = if (hasFp || hasMx) {
    val m = Module(new HiveOutCvt(cfg))
    m.io.lineIn := io.bufPop.payload
    m.io.outFmt := outFmtEff
    m.io.rnd    := io.regFile.rnd
    // P3a 量化支路配置面：blkSel 取 regs(7)(15,14)、nEff 取 regFile.n（有效列数）。
    // packed8/packed4/scaleRow 已由 P3b 接入发拍通路（legacy QuantRowLatch /
    // active denseLine 量化臂选路 + cScalePush 行锁步推 cScaleFifo → dmaCScale 通道）；
    // 非量化运行时量化输出不被采样。
    m.io.blkSel := io.regFile.qBlockSize
    // P3b nEff 按列 tile 修正（P3a 遗留）：不能直接用 regFile.n ——
    // 外层 nTile 扫描、每 tile 覆盖 totalN 列，边界 tile 的 padding 列（列号 >= n）
    // 若按 nEff=regFile.n 参与，实际有效列 < totalN 时 padding 列的旧值会参与块 amax、
    // 抬高共享 scale；当前 tile 有效列数 = min(totalN, n - nCnt*totalN)。
    // nCnt = 当前 N tile 序号：传输期间恒等（sNEXT_N peekBlock 拍自增），与当前遍历的 tile 对应。
    // 非量化运行时 nEff 仅影响量化支路（不被采样），行为不变。
    val nRemain = io.regFile.n - nCnt * cfg.totalN.U
    m.io.nEff   := Mux(nRemain >= cfg.totalN.U, cfg.totalN.U,
                       nRemain(log2Up(cfg.totalN + 1) - 1, 0))
    Some(m)
  } else None

  // ==========================================================================
  // Active 缩位行拆分上下文（仅 cBeatsPerRow>1 时生成；默认哨兵配置不生成，
  // legacy bit-exact 且零无谓寄存器）
  // ==========================================================================
  private class ActiveRowSplit {
    // 行锁存：宽度 = 最大密集行宽（fp32: totalN*32）+ 有效位 + 拍计数。
    // fp16/bf16 运行时仅低 totalN*16 位有效（denseLine 高位零填充后写入）。
    val lineData  = Reg(UInt((cfg.totalN * 32).W))
    val lineValid = RegInit(false.B)
    val beatCnt   = RegInit(0.U(log2Up(cfg.cBeatsPerRow).W))
    // 整数运行密集行（透传占位）：每元素取槽低 32 位、低列号在低位。
    // 与 P1 整数分支（slice(31,0)）逐字一致：整数路径不做任何规格化。
    private val intDenseLine: UInt = Cat((0 until cfg.totalN).reverse.map { i =>
      io.bufPop.payload(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)(31, 0)
    })
    // P2 密集行数据源（纯组合直通，仅供锁存拍采样）：
    //   浮点运行（normSel）→ HiveOutCvt 输出，运行期 outFmtEff 选 fp32 密集 / 16-bit 密集；
    //     packed16 高位零填充到 totalN*32，保证 lineData 高位确定性为 0。
    //   整数运行 → intDenseLine（强制透传，与现状 bit-exact）。
    val denseLine: UInt = if (hasFp || hasMx) {
      val packed16Pad = Cat(0.U((cfg.totalN * 16).W), cvt.get.io.packed16)
      // P3b 量化密集行臂：packed8/packed4 零填充至 totalN*32（与 packed16Pad 同契约，
      // 保证 lineData 高位确定性为 0）；非 hasMx 配置不生成（编译期门控）
      val denseQuant: UInt = if (hasMx) {
        val packed8Pad = Cat(0.U((cfg.totalN * 24).W), cvt.get.io.packed8)
        val packed4Pad = Cat(0.U((cfg.totalN * 28).W), cvt.get.io.packed4)
        Mux(isFmt4, packed4Pad, packed8Pad)
      } else 0.U((cfg.totalN * 32).W)
      Mux(normSel,
        if (hasMx) Mux(isQuant, denseQuant, Mux(isFmt16, packed16Pad, cvt.get.io.packed32))
        else Mux(isFmt16, packed16Pad, cvt.get.io.packed32),
        intDenseLine)
    } else intDenseLine
    // 拍段切分（P2 运行期元素位宽）：第 beatCnt 拍段起始位 = beatCnt × E × elemW
    // （每拍含 E 个元素，必须连元素数一起乘——漏乘 E 会让 beat1+ 段起点偏移 E-1 个元素，
    // 曾导致 P1 缩位回归全线错位，修复留痕）；右移后取低 E*32 段。
    // fp32 时逐字等价 P1 的按拍号固定位切段；fp16/bf16 时仅段低 E*16 位有效（上方总线拼接取用）。
    // 发拍段必须切自锁存后的 lineData——若切自组合直通，发送拍期间
    // FIFO 头部已前移到下一行，总线数据会整行错位（丢行 0 + 尾行垃圾）。
    private val segBits = cfg.cElementsPerBeat * 32
    // P3b 元素位宽扩 {4,8,16,32}：量化分支仅 hasMx 配置生成，非 MX 逐字等于原两级
    // Mux（16/32）；量化 mxfp8=8-bit、mxfp4=4-bit（半字节）
    private val elemW: UInt = if (hasMx)
      Mux(isQuant, Mux(isFmt4, 4.U(6.W), 8.U(6.W)), Mux(isFmt16, 16.U(6.W), 32.U(6.W)))
    else Mux(isFmt16, 16.U(6.W), 32.U(6.W))
    // P3b 拍内字节步进：非量化 = E×outElemBytes（原公式逐字）；量化 = E×elemW/8
    //（mxfp8 → E 字节、mxfp4 → E/2 字节；配置 require 已保证 E 偶、拍内整字节打包），
    // 运行期右移 3 实现除法。
    val beatAddrStep: UInt = if (hasMx)
      Mux(isQuant, (cfg.cElementsPerBeat.U * elemW) >> 3, cfg.cElementsPerBeat.U * outElemBytes)
    else cfg.cElementsPerBeat.U * outElemBytes
    def beatData: UInt = (lineData >> (beatCnt * cfg.cElementsPerBeat.U * elemW))(segBits - 1, 0)
  }
  private val split: Option[ActiveRowSplit] = if (cfg.cBeatsPerRow > 1) Some(new ActiveRowSplit) else None

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
  // 默认输出驱动（isFloat/normSel/outFmtEff 已上提至 ActiveRowSplit 之前，
  // 供 denseLine/outElemBytes 同源引用）
  // ==========================================================================
  io.dmaExtWrIF.rsp.ready        := true.B

  // ==========================================================================
  // 写回握手分支（编译期二选一）：
  //   legacy（cBeatsPerRow==1，默认哨兵）：一拍一行 ——
  //     bufPop.fire 与 req 锁步；outFmtEff==fp32（默认）时数据 = normSel 选路结果（规格化后零扩展回 40 位槽，
  //     逐字保留重构前实现）；P2 新增：outFmtEff≠fp32 时改发 HiveOutCvt 16-bit 密集行（低位对齐、高位零填充）。
  //   active（cBeatsPerRow>1，显式缩位）：行锁存 + 行内多拍拆分（契约见类头注释）。
  // 为何 legacy 保 40 位槽布局而 active 改密集：legacy 必须与出厂软件内存布局逐字一致（5B/元素），
  // 金锚与既有测试零迁移；active 为新配置显式选用，密集打包省去零扩展槽、省总线带宽。
  // ==========================================================================
  // --- P3b legacy 量化行锁存（仅 cBeatsPerRow==1 && hasMx 生成，模式同 ActiveRowSplit）：
  // 量化行强制过寄存器——量化密集行来自 HiveOutCvt 组合云（依赖 bufPop.payload），
  // 发送拍期间 FIFO 头部已前移到下一行，组合直通会把下一行的组合值发上总线，
  // 故 pop 拍锁存数据与地址（地址同拍锁存 curAddr，防行推进后地址漂移）、次拍单拍发出。
  private class QuantRowLatch {
    val valid = RegInit(false.B)
    val data  = Reg(UInt(cfg.cDmaExtWEff.W))
    val addr  = Reg(UInt(cfg.addrWidth.W))
  }
  private val quantLatch: Option[QuantRowLatch] =
    if (cfg.cBeatsPerRow == 1 && hasMx) Some(new QuantRowLatch) else None

  if (cfg.cBeatsPerRow == 1) {
    // P2 浮点通路数据：outFmtEff==fp32 → 现状 popDataFpNorm（默认 00 时逐字不变，回归 bit-exact 锚点）；
    // outFmtEff≠fp32 → HiveOutCvt packed16 密集行零填充至总线宽（一行 1 拍：单行数据位宽 ≤ 总线位宽时
    // 拍数不合并、仍按 1 拍发出，与 active 同契约）。
    val fpWrData: UInt = if (hasFp || hasMx) {
      val dense16 = Cat(0.U((cfg.cDmaExtWEff - cfg.totalN * 16).W), cvt.get.io.packed16)
      Mux(isFmt16, dense16, popDataFpNorm)
    } else popDataFpNorm
    if (hasMx) {
      // ---------- P3b 量化行锁存通路（legacy）：非量化保持原直通路径逐字不变 ----------
      // （编译期 if/else + 运行期 isQuant Mux 双保证：isQuant=0 时下方每个 Mux 退化原式）
      val ql = quantLatch.get
      // 量化密集行：mxfp8 → packed8 零填充至总线宽；mxfp4 → packed4 零填充
      val quantDense: UInt = Mux(isFmt4,
        Cat(0.U((cfg.cDmaExtWEff - cfg.totalN * 4).W), cvt.get.io.packed4),
        Cat(0.U((cfg.cDmaExtWEff - cfg.totalN * 8).W), cvt.get.io.packed8))
      // 行 pop 锁步门：量化行 pop 追加 cScalePush.ready（同拍 scale 行入 FIFO）；
      // isQuant=0 退化原式（req.ready）
      io.bufPop.ready := (state === sTRANSFER_M) &
        Mux(isQuant, !ql.valid & io.cScalePush.get.ready, io.dmaExtWrIF.req.ready)
      // 行 pop 拍同拍 cScalePush（valid = 行 pop 条件 && isQuant，payload = cvt 本行 scaleRow）
      io.cScalePush.get.valid   := (state === sTRANSFER_M) & io.bufPop.valid & !ql.valid & isQuant
      io.cScalePush.get.payload := cvt.get.io.scaleRow
      // pop 拍锁存（数据+地址），发送拍（req.fire）清锁存；两条件互斥无同拍双写
      when((state === sTRANSFER_M) && io.bufPop.fire && isQuant) {
        ql.valid := true.B
        ql.data  := quantDense
        ql.addr  := curAddr
      }
      when((state === sTRANSFER_M) && io.dmaExtWrIF.req.fire && isQuant) {
        ql.valid := false.B
      }
      io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & Mux(isQuant, ql.valid, io.bufPop.valid)
      io.dmaExtWrIF.req.payload.addr := Mux(isQuant, ql.addr, curAddr)
      io.dmaExtWrIF.req.payload.data := Mux(isQuant, ql.data, Mux(normSel, fpWrData, io.bufPop.payload))
    } else {
      io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & io.bufPop.valid
      io.dmaExtWrIF.req.payload.addr         := curAddr
      io.dmaExtWrIF.req.payload.data         := Mux(normSel, fpWrData, io.bufPop.payload)
      io.bufPop.ready                := (state === sTRANSFER_M) & io.dmaExtWrIF.req.ready
    }
  } else {
    val s = split.get
    // 行锁存：锁存空才收新行 → 每行只 pop 一次（Executor 侧背压语义不变）。
    // P3b 行 pop 锁步门：量化行 pop 追加 cScalePush.ready（同拍 scale 行入 FIFO）；
    // isQuant=0 时表达式退化原式（仅 !s.lineValid）
    io.bufPop.ready := (state === sTRANSFER_M) & !s.lineValid &
      (if (hasMx) (!isQuant | io.cScalePush.get.ready) else true.B)
    if (hasMx) {
      // 行锁存拍（行 pop）同拍 cScalePush：payload = 本行块 scale，与 bufPop.fire 锁步
      io.cScalePush.get.valid   := (state === sTRANSFER_M) & io.bufPop.valid & !s.lineValid & isQuant
      io.cScalePush.get.payload := cvt.get.io.scaleRow
    }
    when((state === sTRANSFER_M) && io.bufPop.fire) {
      s.lineData  := s.denseLine
      s.lineValid := true.B
      s.beatCnt   := 0.U
    }

    // 总线侧：锁存有数据即发拍；地址 = 行首址 + 拍号×拍内步进（P3b 量化时改用
    // s.beatAddrStep：非量化 = E×outElemBytes 原公式，量化 = E×elemW/8；行步进/列 tile
    // 步进由 HiveCore calcConfig 同源提供）；curAddr 只在行边界更新，拍内用组合偏移，
    // 行/块跳转自动对齐、免维护拍地址寄存器。
    // 数据：第 beatCnt 段 E 个元素放总线低位，其余高位零填充（尾拍同理）；
    // fp16/bf16 取段低 E×16 位；P3b 量化取段低 E×8（mxfp8）/E×4（mxfp4）位。
    val seg32 = s.beatData
    val data32: UInt =
      if (cfg.cDmaExtWEff == cfg.cElementsPerBeat * 32) seg32
      else Cat(0.U((cfg.cDmaExtWEff - cfg.cElementsPerBeat * 32).W), seg32)
    val data16: UInt = Cat(0.U((cfg.cDmaExtWEff - cfg.cElementsPerBeat * 16).W),
                           seg32(cfg.cElementsPerBeat * 16 - 1, 0))
    // P3b 量化拍段（仅 hasMx）：mxfp8 段低 E×8 位、mxfp4 段低 E×4 位零填充至总线宽；
    // 缩位 require 已保证 cDmaExtWEff >= E*16 >= E*8，位宽非负。
    val quantBeat: UInt = if (hasMx) {
      val data8 = Cat(0.U((cfg.cDmaExtWEff - cfg.cElementsPerBeat * 8).W),
                      seg32(cfg.cElementsPerBeat * 8 - 1, 0))
      val data4 = Cat(0.U((cfg.cDmaExtWEff - cfg.cElementsPerBeat * 4).W),
                      seg32(cfg.cElementsPerBeat * 4 - 1, 0))
      Mux(isFmt4, data4, data8)
    } else 0.U(cfg.cDmaExtWEff.W)
    io.dmaExtWrIF.req.valid        := (state === sTRANSFER_M) & s.lineValid
    io.dmaExtWrIF.req.payload.addr := curAddr + s.beatCnt * s.beatAddrStep
    io.dmaExtWrIF.req.payload.data := {
      if (hasMx) Mux(isQuant, quantBeat, Mux(isFmt16, data16, data32))
      else Mux(isFmt16, data16, data32)
    }

    // 拍推进：末拍清行锁存并回零拍计数（行推进动作在下方 switch sTRANSFER_M 分支）；
    // bufPop.fire 与 req.fire 由 lineValid 互斥（锁存空/满二态），无同拍双写冲突。
    when((state === sTRANSFER_M) && io.dmaExtWrIF.req.fire) {
      when(s.beatCnt === (cfg.cBeatsPerRow - 1).U) {
        s.lineValid := false.B
        s.beatCnt   := 0.U
      }.otherwise {
        s.beatCnt := s.beatCnt + 1.U
      }
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
    // active：强制重启时同步清行锁存（正常流程回 sIDLE 前锁存已自然清空，
    // 此处仅防异常路径残留半行状态；legacy 编译期不生成，行为不变）
    if (cfg.cBeatsPerRow > 1) {
      split.get.lineValid := false.B
      split.get.beatCnt   := 0.U
    }
    // P3b legacy 量化行锁存：强制重启同步清（与 active 行锁存同理；
    // 非 hasMx/非 legacy 编译期不生成）
    if (cfg.cBeatsPerRow == 1 && hasMx) {
      quantLatch.get.valid := false.B
    }
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
        if (cfg.cBeatsPerRow == 1) {
          if (hasMx) {
            // P3b legacy 量化行推进：非量化保持原判据（bufPop.fire）逐字不变；
            // 量化行改在锁存发出拍（req.fire）推进——该拍才是该行真实写出拍，
            // 与 active 末拍推进同语义；锁存交接无空洞（ql.valid 互斥保证每行单发）
            when(io.bufPop.fire && !isQuant) {
              when(lineCounter === io.regFile.m - 1.U) {
                lineCounter := 0.U
                state := sNEXT_N
                io.doneBlock := true.B
              }.otherwise {
                lineCounter := lineCounter + 1.U
                curAddr := curAddr + rowStep
              }
            }
            when(io.dmaExtWrIF.req.fire && isQuant) {
              when(lineCounter === io.regFile.m - 1.U) {
                lineCounter := 0.U
                state := sNEXT_N
                io.doneBlock := true.B
              }.otherwise {
                lineCounter := lineCounter + 1.U
                curAddr := curAddr + rowStep
              }
            }
          } else {
            // legacy（默认）：一拍一行，bufPop.fire 驱动行推进（逐字保留重构前实现）
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
        } else {
          // active：末拍（行内最后一拍）才做行推进，动作与 legacy bufPop.fire
          // 分支逐字同义；拍计数/行锁存清除在上方拍推进 when 块处理。
          when(io.dmaExtWrIF.req.fire && split.get.beatCnt === (cfg.cBeatsPerRow - 1).U) {
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
