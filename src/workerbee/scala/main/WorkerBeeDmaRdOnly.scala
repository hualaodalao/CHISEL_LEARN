/** WorkerBee 轻量级 DMA 引擎（自主 tile 扫描版）。仅支持线性数据搬运（push/pop queue），
  * 启动后完全自主运行，由 occupancy/availability 反压，不依赖 Executor 逐请求控制：
  *   - WorkerBeeDmaRdOnly: 外部存储 → buffer（A buffer / B buffer）
  *   - WorkerBeeDmaWrOnly: C buffer → 外部存储（storeGate 门控）
  */

import chisel3._
import chisel3.util._

/** WorkerBeeDmaRdOnly — 只读 DMA 引擎（tile 粒度自动地址生成）。
  *
  * 职责：从外部存储逐 tile 读数据并 push 进内部矩阵 buffer
  *   - io.isA = true : 搬运 A[mTile, kTile] 子块 → A buffer
  *   - io.isA = false: 搬运整个 B[k, n] 矩阵 → B buffer
  *
  * 遍历序与 Executor 的 N→K→M 消费顺序逐拍对齐：B 为「N 外 K 内」（内层沿 K 方向
  *   kTile*totalN 行、每拍 +bRowAddressOffset，外层扫 nTile 个 N tile、块间跳
  *   bColAddressOffset）；A 为「nTile 外 → kTile 中 → M 内」（共 nTiles 轮，每轮
  *   kTile×m 行，每轮结束 curAddr 复位回 regAAddr）；execute 单次启动供满 nTiles 轮后停止。
  *
  * 地址模型（字节地址；偏移来自顶层 calcConfig 的 aRow/aCol/bRow/bCol 四谓词。简写
  * base = io.reg_baseAddr、R = io.reg_rowOffset、C = io.reg_colTileOffset、T = cfg.totalN、
  * kT = io.reg_kTile；顶层按通道分别接 aRow/aColTile（A）与 bRow/bColTile（B））：
  *
  * ★ A 通道（io.isA=true，块内【升序】，内层一次扫满 io.regFile.m 行、无 mTile 循环）：
  *     sIDLE 锁存 curAddr = base；colAddr = C；rowStep = R；lineTarget = regFile.m
  *     sTRANSFER 每 req.fire 拍 curAddr += rowStep
  *     切 k-tile curAddr = base + colAddr；colAddr += C；切 n-tile curAddr = base；colAddr = C
  *   ⇒ 闭式：块首址(nt, kt) = base + kt*C ——【与 nt 无关】：A 是 M×K 张量、不含 N 维，
  *     每个 n-tile 轮重复扫同一份 A 地址序列。块内第 i 行 = 块首址 + i*R，
  *     i ∈ [0, regFile.m)；总拍数 = nTiles × kT × regFile.m。
  *
  * ★ B 通道（io.isA=false，块内【降序】，lineTarget 恒 = T）：
  *     sIDLE 锁存 curAddr = lastBTileStartAddr = base + (T-1)*R（块内降序起点 = 最高行）
  *     sTRANSFER 每 req.fire 拍 curAddr -= rowStep（降序，权重下沉协议）
  *     块内第 j 行 = lastBTileStartAddr - j*R，j ∈ [0, T)
  *   跳转按 io.regFile.loadWMode 分两支（lastBTileStartAddr 恒存【当前块的降序起点】、
  *   不做末拍多减，故无需 ×2 降序补偿）：
  *     切 k-tile, loadWMode=1   curAddr = lastBTileStartAddr + C；同步累加该寄存器
  *     切 k-tile, loadWMode=0   curAddr = lastBTileStartAddr + T*R；同步累加该寄存器
  *     切 n-tile, loadWMode=1   curAddr = lastBTileStartAddr + C + (T-1)*R；同步累加
  *     切 n-tile, loadWMode=0   curAddr = base + colAddr + (T-1)*R；lastBTileStartAddr
  *                              同值；colAddr += C
  *   ⇒ 闭式（块降序起点，两支【不同】，勿混用）：
  *     loadWMode=0: blockStart(nt, kt) = base + nt*C + (T-1)*R + kt*T*R
  *     loadWMode=1: blockStart(nt, kt) = base + (nt+1)*(T-1)*R + (nt*kT + kt)*C
  *     总拍数 = nTiles × kT × T
  *
  * C = colTileOffset = Kpb × Eb（A 为 kTile 步进；B 在 loadW=0 时是 n-tile 步进
  *   = totalN×bEb，loadW=1 时是 k-tile 步进）。★C 是【运行期量】、不是常数，随
  *   regFile.aFmt/bFmt 变化：MX8/FP16/BF16/INT16 → 32，INT8 → 16，INT32 → 64，半字节族 → 0。
  *   ★已知偏差（勿在本模块自行修补）：loadW=1 的 nt 维分量 Δ_nt = nt × (kT × C − R)，
  *   来自 lastBTileStartAddr 的【累加】模型与【闭式】在 K 非 bKpb 整数倍时不同源。
  *
  * 反压：bufAvailability == 0 时不发 cmd；外部 rsp 仅在 bufPush.ready 为高时接收，
  *   天然支持逐拍暂停。启动后自主逐 tile 块连续搬运、块间无上游握手，搬完发一拍 done。
  *
  * start 语义：io.start 在任意状态均可重新装载，用于异常路径（executor errState 不 pop ⟹
  *   DMA 可能卡 sDONE 等 credit）后的强制重启。sDONE/sTRANSFER 内置 start 重载分支，当拍锁存
  *   新遍历参数并直达 sTRANSFER（start 是单拍脉冲，不可经 sIDLE 中转等待），ostCredit/errReg/
  *   lineCounter 同拍复位；锁存体与 sIDLE 共用同一组合表达式。
  *
  * @param cfg     WorkerBee 配置
  * @param bufWidth buffer push 数据位宽（默认 = cfg.aExtW；外部 rsp 与本口等宽直连）
  * @param bufDepth buffer 深度（默认 = aBufferDepth，B 实例需传 bBufferDepth）
  */
class WorkerBeeDmaRdOnly(cfg: WorkerBeeConfig, bufWidth: Int = 0, bufDepth: Int = 0) extends Module {
  val pushW = bufWidth 
  val availDepth = bufDepth
  // scaleA/scaleB 搬运复用本模块（WorkerBee 例化 bufWidth=8：scaleA tie isA=true、
  // scaleB tie isA=false，见 WorkerBee.scala scale DMA 接线）。

  val io = IO(new Bundle {
    val isA = Input(Bool())                          // 例化时 tie 死：true=A buffer, false=B buffer
    val start = Input(Bool())
    val done  = Output(Bool())
    val busy  = Output(Bool())
    val err   = Output(Bool())

    
    val dmaExtRdIF = WorkerBeeDMAExtReadOnlyIF(cfg, pushW)   // 外部只读通道，与 bufPush 等宽直连

    val regFile = Input(WorkerBeeRegister(cfg))
    val reg_baseAddr = Input(UInt(cfg.addrWidth.W))
    val reg_kTile = Input(UInt(cfg.mnkWidth.W))
    val reg_mTile = Input(UInt(cfg.mnkWidth.W))
    val reg_nTile = Input(UInt(cfg.mnkWidth.W))
    val reg_rowOffset = Input(UInt(cfg.addrWidth.W))
    val reg_colTileOffset = Input(UInt(cfg.addrWidth.W))
    
    
    val bufPush = master(Stream(UInt(pushW.W)))
    val bufPopFire = Input(Bool())
    // push 侧「半字切片 + 精确加宽」stretch 模式（仅 hasMx4 配置生成端口）：stretch=1 时
    //   逻辑拍 = portBytes/2 字节半字（W4A8 混配 B 通道：16B = 32 个 fp4 = 一个阵列 K 窗口
    //   的一行），物理读恒为 1 次对齐读 [L&~(portBytes-1), +portBytes)；本模块按「随在途读
    //   配对的半字位 FIFO」选 rsp 低/高半字输出到 stretchSrc，顶层组合加宽（E2M1 nibble→
    //   E4M3 LUT）成整拍经 stretchBeat 推入 buffer —— 1 读 1 推、每拍 1 槽，credit/节拍与
    //   fp8 基线逐拍同构；gather 机构在本模式旁路（半字选择取代 lo/hi 两读）。单读契约由
    //   CalcConfig 混配 errCode8（K%64 且 bAddr%32）保证末拍读不越张量界。
    val stretch      = if (cfg.hasMx4) Some(Input(Bool())) else None
    val stretchSrc   = if (cfg.hasMx4) Some(Output(UInt(math.max(1, pushW / 2).W))) else None
    val stretchBeat  = if (cfg.hasMx4) Some(Input(UInt(pushW.W))) else None
  })

  val sIDLE       = 0.U(2.W)
  val sTRANSFER   = 1.U(2.W)
  val sNEXT_COL  = 2.U(2.W)
  val sDONE       = 3.U(2.W)

  val state = RegInit(sIDLE)

  val curAddr = RegInit(0.U(cfg.addrWidth.W))
  val lastBTileStartAddr = RegInit(0.U(cfg.addrWidth.W))
  val colAddr = RegInit(0.U(cfg.addrWidth.W))
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  val kCnt = RegInit(0.U(cfg.mnkWidth.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))

  val lineTarget  = RegInit(0.U(16.W))
  val lineCounter = RegInit(0.U(16.W))
  val ostCredit= RegInit(0.U(log2Up(bufDepth + 1).W))
  val errReg = RegInit(false.B)
  val ERR_BUS = 1.U(2.W)
  val ERR_FIFO = 2.U(2.W)
  val errState = RegInit(0.U(2.W))

  // gather 读：非对齐逻辑拍的「对齐多发读 + 字节移位重组」。外部读端口按
  //   portBytes = pushW/8 字节对齐取数；逻辑拍地址 L 非对齐时，DMA 自发读
  //   [L-off, L-off+portBytes] 两个对齐字，内部拼出 memory[L .. L+portBytes)
  //   （off = L 的字节偏移）。端口位宽、软件张量布局、buffer 每逻辑拍恰收 1 push
  //   的契约全部不变；无 errCode 守护。
  //   机制 = drain-then-gather：对齐拍保持原流水（credit 门控多拍在途）；遇到
  //   非对齐拍先等在途读全部返回（inflCnt===0），再串行发 lo/hi 两读并重组。
  //   gPhase: 0=idle（对齐拍自由流水）/ 1=lo 已发、等 lo rsp / 2=发 hi 读 /
  //           3=hi 已发、等 hi rsp（hi rsp 拍合并 push，回 0）。
  //   credit 记账口径 = 每【逻辑拍】一次：只在逻辑拍最后一个物理读 fire 时 -1
  //   （对齐拍 = 其 req.fire；非对齐拍 = hi 读 req.fire；lo 读不扣 credit）。
  //   全对齐激励下 gPhase 恒 0、req/push/credit/推进逐拍同非 gather 实现。
  val portBytes = pushW / 8
  val offBits   = log2Up(portBytes)
  val inflCnt = RegInit(0.U(log2Up(bufDepth + 2).W))  // 在途物理读计数
  val gPhase  = RegInit(0.U(2.W))
  val loReg   = Reg(UInt(pushW.W))                     // 非对齐拍 lo 字暂存
  val offReg  = Reg(UInt(offBits.W))                   // 非对齐拍字节偏移（合并移位量）

  val byteOff     = curAddr(offBits - 1, 0)
  // stretch 模式旁路 gather 判定：逻辑拍是半字片，奇片拍由半字位选择取代 lo/hi 两读。
  val unaligned   = if (cfg.hasMx4) byteOff =/= 0.U && !io.stretch.get else byteOff =/= 0.U
  val alignedBase = curAddr & ~((portBytes - 1).U(cfg.addrWidth.W))
  // 逻辑拍「末物理读」fire：对齐拍 = req.fire；非对齐拍 = hi 读 fire（gPhase===2）。
  // lo 读 fire（gPhase===0 && unaligned）不算 ⟹ lineCounter/curAddr/credit 均按逻辑拍推进。
  val finalReqFire = io.dmaExtRdIF.req.fire && !(gPhase === 0.U && unaligned)
  // hi 字与 lo 字拼接后右移 off 字节，截回 pushW：= memory[L .. L+portBytes)
  val mergedData = (Cat(io.dmaExtRdIF.rsp.payload.data, loReg) >> (offReg << 3))(pushW - 1, 0)

  // gPhase 推进（lo 发→lo 收→hi 发→hi 收）。★TB/AXI 可能零延迟应答（req.fire 同拍
  // rsp.fire）：lo/hi 各有同拍捷径（loSameCycle 0→2、hiSameCycle 2→0），否则 lo 字会被
  // 误当整拍 push 且 phase1 永等不到 rsp 而死锁。
  val loSameCycle = io.dmaExtRdIF.req.fire && io.dmaExtRdIF.rsp.fire && gPhase === 0.U && unaligned
  val hiSameCycle = io.dmaExtRdIF.req.fire && io.dmaExtRdIF.rsp.fire && gPhase === 2.U
  when(loSameCycle) {
    gPhase := 2.U
    offReg := byteOff
    loReg  := io.dmaExtRdIF.rsp.payload.data
  }.elsewhen(io.dmaExtRdIF.req.fire && gPhase === 0.U && unaligned) {
    gPhase := 1.U
    offReg := byteOff
  }.elsewhen(io.dmaExtRdIF.rsp.fire && gPhase === 1.U) {
    gPhase := 2.U
    loReg  := io.dmaExtRdIF.rsp.payload.data
  }.elsewhen(hiSameCycle) {
    gPhase := 0.U
  }.elsewhen(io.dmaExtRdIF.req.fire && gPhase === 2.U) {
    gPhase := 3.U
  }.elsewhen(io.dmaExtRdIF.rsp.fire && gPhase === 3.U) {
    gPhase := 0.U
  }

  // 在途物理读计数（饱和递减防 flush 后陈旧 rsp 下溢）
  when(io.dmaExtRdIF.req.fire && io.dmaExtRdIF.rsp.fire) {
  }.elsewhen(io.dmaExtRdIF.req.fire) {
    inflCnt := inflCnt + 1.U
  }.elsewhen(io.dmaExtRdIF.rsp.fire && inflCnt =/= 0.U) {
    inflCnt := inflCnt - 1.U
  }

  // stretch 半字选择 FIFO（hasMx4 only，1-bit × depth 环形）：req.fire 拍压入本逻辑拍 L 的半
  //   字位 curAddr(offBits-1)，rsp 返回拍弹出以选 rsp 低/高半字。前提 rsp 与 req 同序；depth
  //   ≥ 最大在途读数（credit 1/拍门控 ⟹ 在途 ≤ bufDepth，队满不可达）。零延迟应答（req.fire
  //   同拍 rsp.fire 且队空）走旁路直取本拍 enq 位。start 重载拍 selCnt 清零，不跨 run 泄漏。
  private val selDepth = math.max(1, bufDepth)
  private val selBits = if (cfg.hasMx4) Some(RegInit(VecInit(Seq.fill(selDepth)(false.B)))) else None
  private val selHead = if (cfg.hasMx4) Some(RegInit(0.U(log2Up(selDepth).W))) else None
  private val selTail = if (cfg.hasMx4) Some(RegInit(0.U(log2Up(selDepth).W))) else None
  private val selCnt  = if (cfg.hasMx4) Some(RegInit(0.U(log2Up(bufDepth + 1).W))) else None
  if (cfg.hasMx4) {
    val selEnqFire = io.dmaExtRdIF.req.fire && io.stretch.get
    // 零延迟应答且队空：本拍 rsp 即本拍 req 的应答，半字位直取当前 curAddr，不入队
    val selBypass  = selEnqFire && io.dmaExtRdIF.rsp.fire && selCnt.get === 0.U
    val selStore   = selEnqFire && !selBypass
    val selDeqFire = io.dmaExtRdIF.rsp.fire && io.stretch.get && selCnt.get =/= 0.U
    // 半字位真源：旁路（零延迟应答且队空）取本拍请求地址的半字位；否则取队头位。
    //   ★stretchSrc 的 Mux 两臂【必须】都是「按位选出的 rsp 半字」：若把 1-bit 半字位本身放进
    //   Mux 臂，Chisel 会零扩成 128'h0/1 ⟹ stretchSrc 退化为「元素0=LUT(halfbit)、其余全 0」。
    val selBit = Mux(selBypass, curAddr(offBits - 1), selBits.get(selTail.get))
    io.stretchSrc.get := Mux(selBit,
      io.dmaExtRdIF.rsp.payload.data(pushW - 1, pushW / 2),
      io.dmaExtRdIF.rsp.payload.data(pushW / 2 - 1, 0))
    when(io.start) {
      selCnt.get := 0.U
    }.elsewhen(selStore && selDeqFire) {
      // 同拍一进一出：响应属 selTail 旧请求、新请求的半字位须写 selHead ⟹ head/tail 各推进
      //   一格、cnt 不变。漏判会丢新位且下一响应复用已消费的 tail 位。
      selBits.get(selHead.get) := curAddr(offBits - 1)
      selHead.get := Mux(selHead.get === (selDepth - 1).U, 0.U, selHead.get + 1.U)
      selTail.get := Mux(selTail.get === (selDepth - 1).U, 0.U, selTail.get + 1.U)
    }.elsewhen(selStore) {
      selBits.get(selHead.get) := curAddr(offBits - 1)
      selHead.get := Mux(selHead.get === (selDepth - 1).U, 0.U, selHead.get + 1.U)
      selCnt.get  := selCnt.get + 1.U
    }.elsewhen(selDeqFire) {
      selTail.get := Mux(selTail.get === (selDepth - 1).U, 0.U, selTail.get + 1.U)
      selCnt.get  := selCnt.get - 1.U
    }
  }

  when(state === sIDLE){
    ostCredit := bufDepth.U
    errReg := false.B
    gPhase  := 0.U
    inflCnt := 0.U
  }.otherwise{
    when(io.dmaExtRdIF.rsp.fire){
      when(io.dmaExtRdIF.rsp.payload.err){
        errReg := true.B
        errState := ERR_BUS
      }
    }

    when(finalReqFire & io.bufPopFire){
    }.elsewhen(finalReqFire){
      ostCredit := ostCredit - 1.U
    }.elsewhen(io.bufPopFire){
      ostCredit := ostCredit + 1.U
    }

  }

  // 请求门控。gPhase=0：对齐拍凭 credit 自由发；非对齐拍须先 drain（inflCnt===0）且预占 1
  //   credit（hi 读将扣）。gPhase=2：发 hi 读（credit 已预占，不再查）。gPhase=1/3：等 rsp 不发。
  //   stretch=1 时 unaligned 恒假 ⟹ gPhase 恒 0、门控即基线（1 读 1 槽/拍）。
  io.dmaExtRdIF.req.valid       := state === sTRANSFER && (
    (gPhase === 0.U && ostCredit =/= 0.U && (!unaligned || inflCnt === 0.U)) ||
    (gPhase === 2.U)
  )
  // stretch 默认臂取 alignedBase：奇半字拍（L mod portBytes = portBytes/2）只发一次对齐读
  //   [L-off, +portBytes)，半字由 sel FIFO 选；偶半字拍 alignedBase == curAddr，同基线。
  private val reqAddrDefault = if (cfg.hasMx4) Mux(io.stretch.get, alignedBase, curAddr) else curAddr
  io.dmaExtRdIF.req.payload.addr := MuxCase(reqAddrDefault, Seq(
    (gPhase === 0.U && unaligned) -> alignedBase,               // lo 读：对齐化
    (gPhase === 2.U)              -> (alignedBase + portBytes.U) // hi 读：下一对齐字
  ))
  io.dmaExtRdIF.rsp.ready       := true.B //buffer永远能push的时候dma才会发请求

  // push 口径：phase0 的 rsp = 对齐拍（直 push），但 phase0 且本拍 req.fire 是 lo 读
  //   （unaligned）时该 rsp 为 lo 字 → 进 loReg 不 push；phase1 的 rsp = 延迟返回的 lo 字 → 不
  //   push；phase2 同拍 hi 应答（hiSameCycle）/ phase3 的 rsp = hi 字 → 与 loReg 合并 push。
  val alignedRspPush = io.dmaExtRdIF.rsp.valid && gPhase === 0.U &&
    !(io.dmaExtRdIF.req.fire && unaligned)
  if (cfg.hasMx4) {
    when(io.stretch.get) {
      // stretch：gPhase 恒 0、无 gather ⟹ push 脉冲 = alignedRspPush，payload = 顶层加宽整拍
      io.bufPush.valid   := alignedRspPush
      io.bufPush.payload := io.stretchBeat.get
    }.otherwise {
      io.bufPush.valid   := alignedRspPush || hiSameCycle ||
        (io.dmaExtRdIF.rsp.valid && gPhase === 3.U)
      io.bufPush.payload := Mux(gPhase === 3.U || hiSameCycle, mergedData, io.dmaExtRdIF.rsp.payload.data)
    }
  } else {
    io.bufPush.valid   := alignedRspPush || hiSameCycle ||
      (io.dmaExtRdIF.rsp.valid && gPhase === 3.U)
    io.bufPush.payload := Mux(gPhase === 3.U || hiSameCycle, mergedData, io.dmaExtRdIF.rsp.payload.data)
  }

  io.done := false.B
  io.busy := state =/= sIDLE
  io.err  := errReg


  switch(state) {
    is(sIDLE) {
       errReg := false.B
       lineCounter := 0.U  
      
      // 防重复 start 竞态：重载拍已直达 sTRANSFER，仅当本拍确在 sIDLE 才允许 start 推进
      when(io.start){
        state  := sTRANSFER

        when(io.isA) {
          curAddr   := io.reg_baseAddr
          colAddr   := io.reg_colTileOffset
          rowStep   := io.reg_rowOffset
          lineTarget  := io.regFile.m
        }.otherwise {
          curAddr            := io.reg_baseAddr + (cfg.totalN - 1).U * io.reg_rowOffset
          lastBTileStartAddr := io.reg_baseAddr + (cfg.totalN - 1).U * io.reg_rowOffset
          colAddr   := io.reg_colTileOffset
          rowStep   := io.reg_rowOffset
          lineTarget  := cfg.totalN.U
        }        
        
      }

    }

    is(sTRANSFER){
      // 搬运途中收到 start（异常路径强制重启）：同拍锁存新参数并复位计数/credit 重启遍历
      when(io.start) {
        errReg      := false.B
        lineCounter := 0.U
        kCnt        := 0.U
        nCnt        := 0.U
        ostCredit   := bufDepth.U
        gPhase      := 0.U
        inflCnt     := 0.U
      }.elsewhen(finalReqFire){
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
    is(sNEXT_COL) {
      when(io.isA) {
        when(kCnt === io.reg_kTile - 1.U){
          curAddr := io.reg_baseAddr
          colAddr := io.reg_colTileOffset
          kCnt := 0.U
          when(nCnt === io.reg_nTile - 1.U){
            state := sDONE
          }.otherwise{
            nCnt := nCnt + 1.U
            state := sTRANSFER
          }
          
        }.otherwise{
          curAddr := io.reg_baseAddr + colAddr
          colAddr := colAddr + io.reg_colTileOffset
          kCnt := kCnt + 1.U
          state := sTRANSFER
        }
        
      }.otherwise {
        when(kCnt === io.reg_kTile - 1.U){
          when(nCnt === io.reg_nTile - 1.U){
            state := sDONE
          }.otherwise{
            
            kCnt := 0.U
            nCnt := nCnt + 1.U
            state := sTRANSFER            

            when(io.regFile.loadWMode){
              curAddr := lastBTileStartAddr + io.reg_colTileOffset + (cfg.totalN.U - 1.U) * io.reg_rowOffset
              lastBTileStartAddr := lastBTileStartAddr + io.reg_colTileOffset + (cfg.totalN.U - 1.U) * io.reg_rowOffset
            }.otherwise{
              // loadWMode=0 切 n-tile：下一 n-tile 的 kt0 降序块起点 = base + n*colTileOff +
              //   (totalN-1)*rowOff。本文件 colAddr 只累计 n*colTileOff，故须显式补
              //   (totalN-1)*rowOff 降序起点项，并同步维护 lastBTileStartAddr 供后续 k-switch。
              curAddr := io.reg_baseAddr + colAddr + (cfg.totalN.U - 1.U) * io.reg_rowOffset
              lastBTileStartAddr := io.reg_baseAddr + colAddr + (cfg.totalN.U - 1.U) * io.reg_rowOffset
              colAddr := colAddr + io.reg_colTileOffset
            }

          }
        }.otherwise{
          
          kCnt := kCnt + 1.U
          state := sTRANSFER
          
          when(io.regFile.loadWMode){
            
            curAddr := lastBTileStartAddr + io.reg_colTileOffset
            lastBTileStartAddr := lastBTileStartAddr + io.reg_colTileOffset
            
          }.otherwise{
            // loadWMode=0 切 k-tile：lastBTileStartAddr 恒存当前块的降序起点（不做末拍多减），
            //   故下一块起点 = lastBTileStartAddr + totalN*rowOffset。若改用 curAddr 累加，须
            //   额外补一个 totalN*rowOff 的降序补偿，否则 kt1 会重读 kt0 的最低行。
            curAddr := lastBTileStartAddr + cfg.totalN.U * io.reg_rowOffset
            lastBTileStartAddr := lastBTileStartAddr + cfg.totalN.U * io.reg_rowOffset
          }
          
         
        }
      }
    }
    

    is(sDONE) {
      // sDONE 收到 start：同拍锁存新参数并复位 credit/计数、直达 sTRANSFER（executor errState
      //   不 pop 时 ostCredit 不返还，否则会永久卡在 sDONE 等 credit）。
      when(io.start) {
        state       := sTRANSFER
        errReg      := false.B
        lineCounter := 0.U
        kCnt        := 0.U
        nCnt        := 0.U
        ostCredit   := bufDepth.U
        gPhase      := 0.U
        inflCnt     := 0.U
      }.elsewhen(ostCredit === bufDepth.U){
        io.done := true.B
        state := sIDLE
      }
    }
  }


}


