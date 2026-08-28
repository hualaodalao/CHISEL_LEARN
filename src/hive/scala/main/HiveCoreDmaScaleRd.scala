/** HiveCoreDmaScaleRd — MX scale 专用只读 DMA 引擎（从 HiveCoreDmaRdOnly 拆出）。
  *
  * 纯结构重构：行为与拆分前 HiveCoreDmaRdOnly 的 isScale 路径逐拍完全一致
  * （scaleA 走 A 式三级遍历、scaleB 走两级「nt 轮外 × kb 块内」遍历，
  * 含每轮 1 拍 sNEXT_COL 开销、旧 colAddr 跳转语义、+rowStep 升序）。
  *
  * 与数据 DMA（HiveCoreDmaRdOnly）的差异（几何由编译期参数定死，无 io.isA）：
  *   - isScaleB = false（scaleA 实例）：三级遍历「nTile 外 → kTile 中 → M 内」，
  *     共 nTile 轮、每轮 kTile×m beat（beat 数 nTile×kTile×M 不变），
  *     遍历序 (nt)×(kt)×(m) 与 executor 每激活行 pop scaleA 一一对应。
  *   - isScaleB = true（scaleB 实例）：两级遍历「nt 轮外 × kb 块内」——总
  *     beat 数 = nTile × kTile（v2 单 pass：每个 kTile = 一个 32-K scale 块，
  *     executor 每 sLOAD_B 窗口 pop 一次）。取址序与 executor pop 序（每 nt
  *     轮内逐 kTile 起始 pop 一次）逐拍一致：外部布局 scaleB[kb][n] 连续
  *     （每 kb 占 regFile.n 字节）下，块内 kb 步进 = regFile.n 字节，轮间
  *     跳回 base + (nt+1)*totalN（sNEXT_COL 处理）。单纯线性取址在
  *     nTile>1 且 kTile>1 时为 kb-major 序、与 nt-major pop 序错位，故必须两级遍历。
  *
  * IO 收窄（相对数据 DMA）：
  *   - 无 io.isA：遍历几何由 isScaleB 编译期定死；
  *   - 无整包 regFile：直读 scale 基址（io.baseAddr ← regFile.scaleAAddr /
  *     scaleBAddr）与单一维度（io.dim ← regFile.m / regFile.n），顶层不再
  *     需要 WireInit(regFile) 覆写 regs(3)/regs(4) 的伪装 hack；
  *   - 无 kSlice 回补、loadW 几何、降序遍历、bNextKTileAddressOffset、mxRunDma。
  *
  * 反压策略与数据 DMA 相同：buffer 无空间（ostCredit==0）不发请求；外部
  * rsp 在 bufPush.ready 为高时才接收。
  *
  * start 语义：io.start 在任意状态均可重新装载（保留既有恢复路径，简化掉
  * 会引入死锁回归）：用于异常路径（Executor errState：config-err 后
  * executor 不 pop、DMA 可能卡 sDONE 等 credit）后的强制重启。实现：
  * sDONE/sTRANSFER 内置 start 重载分支，当拍同周期锁存新遍历参数并直达
  * sTRANSFER（start 为 executePulse 单拍脉冲，不可经 sIDLE 中转等待）；
  * ostCredit/errReg/lineCounter 同拍复位（flush 不返还的 credit 在此恢复）。
  * 锁存体与 sIDLE 共用同一组合表达式（条件 state===sIDLE|start），正常
  * 流程 start 仅出现在 sIDLE，重载分支为死路、行为不变。
  * sNEXT_COL 为单拍过渡态无需分支（下一拍必落 sTRANSFER/sDONE 被重载捕获）。
  * 安全性：脱离 sTRANSFER 时外部流 ready/valid 自然拉低，无协议违约。
  * 已知窄窗口：运行期 err 后强制重载时，旧 run 尚在外部总线延迟中的残余 rsp 可能
  * 在重载后 1~2 拍到达并被当作新 run 数据推入（仅限异常恢复路径；正常 executePulse
  * 启动时 DMA 必在 sIDLE 不受影响）。修复方案为代际标记（另立任务）。
  *
  * @param cfg      HiveCore 配置
  * @param isScaleB 编译期几何选择：false = scaleA（A 式三级遍历），
  *                 true = scaleB（两级 nt×kb 遍历）
  * @param bufWidth buffer push 数据位宽（外部 rsp 与本口等宽直连，每拍一个
  *                 beat；scaleA = 8，scaleB = cfg.scaleRowW）
  * @param bufDepth buffer 深度（决定 ostCredit 位宽；= cfg.scaleBufferDepth）
  */

import chisel3._
import chisel3.util._

class HiveCoreDmaScaleRd(cfg: HiveCoreConfig, isScaleB: Boolean, bufWidth: Int, bufDepth: Int) extends Module {
  val pushW = bufWidth

  val io = IO(new Bundle {
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲（任意状态可重载）
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志
    val dmaExtRdIF = HiveCoreDMAExtReadOnlyIF(cfg, pushW)   // 外部只读通道，与 bufPush 等宽直连
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // 预计算的 tile 数（nTile/kTile）与列偏移
    val baseAddr = Input(UInt(cfg.addrWidth.W))      // scale 基址（← regFile.scaleAAddr / scaleBAddr）
    val dim = Input(UInt(cfg.mnkWidth.W))            // 内层行数（← regFile.m / 块内步进 ← regFile.n；
                                                     // 32-bit regs 直连时隐式截断低位，与原实现逐位等价）
    val bufPush = master(Stream(UInt(pushW.W)))      // 向 scale FIFO push 数据
    val bufPopFire = Input(Bool())
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE       = 0.U(2.W)
  val sTRANSFER   = 1.U(2.W)   // 接收数据 push buffer、推进地址
  val sNEXT_COL   = 2.U(2.W)
  val sDONE       = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val curAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前读地址
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 列/轮间累计偏移
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val kCnt = RegInit(0.U(cfg.mnkWidth.W))      // 仅 scaleA 使用（中层 kTile 计数）
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
      // 同拍锁存新参数（下方 |io.start 锁存块）并复位计数/credit 重启遍历；
      // 正常流程 start 只在 sIDLE 出现，本分支死路、行为不变
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        kCnt        := 0.U
        nCnt        := 0.U
        ostCredit   := bufDepth.U
      }.elsewhen(io.dmaExtRdIF.req.fire){
          when(lineCounter === lineTarget - 1.U){
            // scaleB：一轮（kTile beat）发完按 nCnt 分流——末 nt 轮直接
            // sDONE，否则 sNEXT_COL 轮间跳转；scaleA 走 sNEXT_COL 处理
            // 块/列跳转
            if (isScaleB) {
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
          // scale 双通道块内均升序：scaleA +aRowOff、scaleB +N 字节
          curAddr := curAddr + rowStep
      }
    }
    // --- sNEXT_COL: 块/轮间跳转（单拍过渡态） ---
    is(sNEXT_COL) {
      if (isScaleB) {
        // scaleB 轮间跳转（nt 外）：下一 nt 轮基址 = base + (nt+1)*totalN
        // 字节（布局 scaleB[kb][n] 连续，nt 轮内向量的列窗右移 totalN）；
        // colAddr 累计 nt 轮偏移供后续轮复用，块内 kb 步进在 sTRANSFER 完成。
        // 注意：curAddr 表达式用「旧」colAddr（last-connect 同拍更新不互见）
        nCnt := nCnt + 1.U
        colAddr := colAddr + cfg.totalN.U
        curAddr := io.baseAddr + colAddr + cfg.totalN.U
        state := sTRANSFER
      } else {
        // scaleA 三级遍历跳转（A 式）：中层扫 kTile 个 K 块，外层共 nTile 轮
        // （每轮结束 curAddr 复位回 base）
        when(kCnt === io.calcConfig.kTile - 1.U){
          curAddr := io.baseAddr
          colAddr := io.calcConfig.aColTileAddressOffset
          kCnt := 0.U
          when(nCnt === io.calcConfig.nTile - 1.U){
            state := sDONE
          }.otherwise{
            nCnt := nCnt + 1.U
            state := sTRANSFER
          }
          
        }.otherwise{
          curAddr := io.baseAddr + colAddr
          colAddr := colAddr + io.calcConfig.aColTileAddressOffset
          kCnt := kCnt + 1.U
          state := sTRANSFER
        }
      }
    }
    

    // --- sDONE: 发完成脉冲，回 IDLE ---
    is(sDONE) {
      // start 重载（契约对齐）：修复「config-err 后卡 sDONE 等 credit」死锁链——
      // executor errState 不 pop → ostCredit 不返还 → 原实现永久等待；
      // 现 start 拍同周期锁存新参数（下方 |io.start 锁存块）并复位 credit/计数、
      // 直达 sTRANSFER 重启遍历（flush 不返还的 credit 在此恢复）。
      // last-connect 自洽：本分支补 state := sTRANSFER 后，.elsewhen 分支的
      // state := sIDLE 在 start 拍不可达（when 优先）；同拍 |io.start 锁存块
      // 以新参数覆写，次拍以新参数/满 credit/零计数从 sTRANSFER 起跑。
      // 【已知窄窗口留痕】运行期 err 后强制重载时，旧 run 尚在外部总线延迟中的
      // 残余 rsp 可能在重载后 1~2 拍到达并被当作新 run 数据推入（触发链仅限异常
      // 恢复路径，正常 executePulse 启动时 DMA 必在 sIDLE 不受影响）；修复方案为
      // 代际标记（另立任务）。
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
    if (isScaleB) {
      // scaleB 两级遍历（仅 isScaleB=true 生成）：nt 轮外 × kb 块内，
      // 每 (nTile, K-块) 一个 scaleRowW 向量，起始 = scaleBAddr（顶层直连
      // regFile.scaleBAddr）。外部布局 scaleB[kb][n] 连续（每 kb 占
      // regFile.n 字节），故块内 kb 步进 = regFile.n 字节（rowStep，运行期
      // N，不可改成常量 totalN）；lineTarget = 每轮 beat 数 = kTile（v2 单
      // pass：每 kTile = 一个 32-K scale 块），共 nTile 轮；轮间跳转由
      // sNEXT_COL 处理（colAddr 累计 nt 轮偏移，下一轮基址 = base +
      // (nt+1)*totalN）。与 executor pop 序（每 nt 轮内逐 kTile 起始 pop）
      // 逐拍同序；nTile=1 时退化为连续线性、单 tile 行为不变。
      curAddr    := io.baseAddr
      colAddr    := 0.U
      rowStep    := io.dim
      lineTarget := io.calcConfig.kTile
      nCnt       := 0.U
    } else {
      // scaleA：三级遍历「nTile 外 → kTile 中 → M 内」。内层扫实际 M 行
      // （regFile.m，边界感知不含 padding 行），中层扫 kTile 个 K tile，
      // 外层共 nTile 轮（每轮结束 curAddr 复位回 base）。
      // 【既有 wart 留痕（v1 即存在，另立任务；仅注释，不改行为）】本实例取址公式
      // 是 A 矩阵 tiling 公式的镜像（base + m*aRowOff + kt*aColOff），与真实
      // scaleA 外部布局（scaleA[m][kb]，行步长 = kBlocks 字节）失配。测试侧按
      // beat 序供数（见 MxSpec genScaleABeat）且未对该通道逐 beat 地址对账，
      // 故数值路径不受影响；v2 未使该 wart 恶化，取址公式修正另立任务跟踪。
      curAddr    := io.baseAddr
      colAddr    := io.calcConfig.aColTileAddressOffset
      rowStep    := io.calcConfig.aRowAddressOffset
      lineTarget := io.dim
    }
  }
}
