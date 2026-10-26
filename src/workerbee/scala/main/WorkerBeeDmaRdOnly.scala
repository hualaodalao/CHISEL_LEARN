/** WorkerBee 轻量级 DMA 引擎（自主 tile 扫描版）。
  *
  * 仅支持线性数据搬运（push/pop queue），启动后完全自主运行，
  * 由 occupancy/availability 反压，不依赖 Executor 逐请求控制：
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
  * B 矩阵固定「N 外 K 内」遍历（与 Executor 的 N→K→M 消费顺序匹配）：
  *   内层沿 K 方向（kTile*totalN 行，每拍地址 + bRowAddressOffset），
  *   外层扫 nTile 个 N tile（块间跳 bColAddressOffset）。
  * A 矩阵固定为三级遍历「nTile 外 → kTile 中 → M 内」：共 nTiles 轮、
  *   每轮 kTile×m 行（内层 regFile.m 行 +aRowAddressOffset，中层 kTile 块
  *   跳 aColAddressOffset，每轮结束 curAddr 复位回 regAAddr），与 executor
  *   N→K→M 消费序逐拍对齐；execute 单次启动自主供满 nTiles 轮后自然停止。
  *
  * 地址模型（字节地址；偏移全部来自顶层 calcConfig 的 aRow/aCol/bRow/bCol 四谓词，
  * 本段简写为 rowOff = io.reg_rowOffset、colOff = io.reg_colTileOffset、
  * base = io.reg_baseAddr、T = cfg.totalN、R = rowOff、C = colOff、kT = io.reg_kTile）。
  * 下面【逐条对应代码】，不含任何未在代码中出现的量。
  *
  * ★ A 通道（io.isA=true，块内【升序】，内层一次扫满 io.regFile.m 行、无 mTile 循环）：
  *     sIDLE 锁存   curAddr = base；colAddr = C；rowStep = R；lineTarget = regFile.m
  *     sTRANSFER    每 req.fire 拍 curAddr += rowStep
  *     切 k-tile    curAddr = base + colAddr；colAddr += C；kCnt += 1
  *     切 n-tile    curAddr = base；colAddr = C；kCnt = 0；nCnt += 1（或 sDONE）
  *   ⇒ 闭式：块首址(nt, kt) = base + kt*C ——【与 nt 无关】：A 是 M×K 张量、不含 N 维，
  *     每个 n-tile 轮重复扫同一份 A 地址序列。
  *     块内第 i 行 = 块首址 + i*R，i ∈ [0, regFile.m)；总拍数 = nTiles × kT × regFile.m。
  *
  * ★ B 通道（io.isA=false，块内【降序】，lineTarget 恒 = T）：
  *     sIDLE 锁存   curAddr = lastBTileStartAddr = base + (T-1)*R（= 块内降序起点，
  *                  即最高行）；colAddr = C（仅 loadWMode=0 分支消费）；rowStep = R
  *     sTRANSFER    每 req.fire 拍 curAddr -= rowStep（降序，权重下沉协议）
  *     块内第 j 行 = lastBTileStartAddr - j*R，j ∈ [0, T)
  *   余下四条跳转按 io.regFile.loadWMode 分两支（lastBTileStartAddr 恒存【当前块的
  *   降序起点】、不做末拍多减，故无需旧码的 ×2 降序补偿）：
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
  * 【#136-KW3 删除化石公式，勿恢复】此处原写「块间跳转采用『跳回起点再前移一个块』的
  *   增量式计算：jump = blockOffset - (innerRows - 1) * rowStep」。该式与 #133 从
  *   WorkerBeeDmaWrOnly 删掉的那条【逐字同源】，是更早提交的化石，与本模块代码不符：
  *   上面【六条】跳转（A 切 k-tile / A 切 n-tile / B 切 k-tile ×2 支 / B 切 n-tile ×2 支）
  *   无一匹配它 —— B 侧走 lastBTileStartAddr 累加模型、A 侧走「base + colAddr」重算
  *   模型，两者都不含「减去 (innerRows-1)*rowStep」这一项；代码中也不存在 blockOffset /
  *   innerRows 这两个量。#133 恰好重写了紧邻的死引用留痕段却只订正命名、未校验公式。
  * 【#136-KW3 订正 #133 遗留的两条不完整式】旧 A 式写 regAAddr + mTileIdx*totalN*
  *   aRowOffset + kTileIdx*aColOffset —— 其中 mTileIdx*totalN 一项在代码里【不存在】：
  *   本模块内层一次扫全部 regFile.m 行、无 mTile 循环，故 A 地址与 mTileIdx 无关。
  *   旧 B 式写 regBAddr + kTileIdx*totalN*bRowOffset + nTileIdx*bColOffset —— 漏掉了
  *   降序起点项 (T-1)*R，且未区分 loadWMode 两支（两支闭式不同，见上）。
  *
  * 【#133 死引用清理 / #136-RW1 去裸 token】此处原写「与 WorkerBeeExecutor 侧的 A/B tile
  *   首址别名一致」并复述了那两个原名的裸 token。结论不变：该类别名在 main 源码中
  *   【无任何定义】（Executor 不消费地址偏移，只发 peekBlock/doneBlock 块协议与 start
  *   脉冲），属死引用，已删除。#136-RW1 采纳选项 (b)：留痕改用【描述性表述】、不再出现
  *   裸 token，使 grep 式死引用审计在 src/workerbee/scala/main/ 下 0 命中，无需读上下文区分
  *   「活引用」与「删除留痕」。
  * 信号名对照：rowOff/colOff 是本注释简写，实际端口为 io.reg_rowOffset /
  *   io.reg_colTileOffset，顶层按通道分别接 calcConfig 的 aRowAddressOffset /
  *   aColTileAddressOffset（A）与 bRowAddressOffset / bColTileAddressOffset（B）。
  *
  * 【#124 ① 补记：C 的取值闭式（本段是上方两支闭式的权威配套，勿只改一处）】
  *   上面 A/B 两支闭式把 C 当【不透明输入】使用，这是对的——本模块不关心 C 怎么算。
  *   但 C 的取值语义必须在此留档，否则本头注释会被误读成「C 是常数」。事实：
  *     A 通道 C = aColTileAddressOffset = totalN × elemsPerLane(aFmt) × memElemBytes(aFmt)
  *              = aKpb × aEb        （kTile 步进：A 行主序 M×K，一个 kTile 跨 aKpb 个 K 元素）
  *     B 通道 C = bColTileAddressOffset = totalN × elemsPerLane(bFmt) × memElemBytes(bFmt)
  *              = bKpb × bEb        （同一式，两支语义由 loadWMode 分流：
  *                                     loadW=0 时它是 n-tile 步进（= totalN×bEb，因非 MX
  *                                       下 elemsPerLane=1、bKpb=totalN）；
  *                                     loadW=1 时它是 k-tile 步进（= bKpb×bEb））
  *   ★ C 是【运行期量】，随 regFile.aFmt/bFmt 变化：MX8/FP16/BF16/INT16 → 32，
  *     INT8 → 16，INT32 → 64，半字节族 → 0。672fb37 曾把它退化成 elaboration 期常数
  *     totalN×(aW/8)=totalN×(bW/8)=32（aW/bW 是编译期位宽，出厂 16），已由 #124 ① 修复。
  *     修复只改 WorkerBeeCalcConfig 的赋值式，本模块【功能行零改动】，两支闭式原样有效。
  *   ★ 残余偏差（不属 #124，已由 #139 承接，勿在本模块自行修补）：loadW=1 的
  *     nt 维分量 Δ_nt = nt × (kT × C − R) 来自 lastBTileStartAddr 的【累加】模型与金锚
  *     【闭式】在 K 非 bKpb 整数倍时不同源；#124 ① 只让 kt 维分量 Δ_kt = kt × (C − bKpb×bEb)
  *     恒归零，Δ_nt 改前改后同样存在。
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
  * @param cfg     WorkerBee 配置
  * @param bufWidth buffer push 数据位宽（默认 = cfg.aExtW；外部 rsp 与本口
  *                 等宽直连，每拍 push 一个 beat）
  * @param bufDepth buffer 深度（决定 bufAvailability 位宽；默认 = aBufferDepth，
  *                 B 实例需传 bBufferDepth）
  */
class WorkerBeeDmaRdOnly(cfg: WorkerBeeConfig, bufWidth: Int = 0, bufDepth: Int = 0) extends Module {
  val pushW = bufWidth 
  val availDepth = bufDepth
  // 【注释订正（B5 调研发现，仅文档、不改行为）】旧注释称「MX scale 读搬运已拆分为
  // 独立模块 WorkerBeeDmaScaleRd」已 STALE：672fb37 提交删除了 WorkerBeeDmaScaleRd.scala，
  // scaleA/scaleB 现复用本模块（WorkerBee 例化 bufWidth=8：scaleA tie isA=true、
  // scaleB tie isA=false，见 WorkerBee.scala scale DMA 接线）。WorkerBee.scala L457
  // 同类 STALE 注释属禁动区外的文档瑕疵，一并列入报告交回裁定，不在此改动。

  val io = IO(new Bundle {
    val isA = Input(Bool())                          // 例化时 tie 死：true=A buffer, false=B buffer
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志

    
    val dmaExtRdIF = WorkerBeeDMAExtReadOnlyIF(cfg, pushW)   // 外部只读通道 cmd(addr)/rsp(data)，与 bufPush 等宽直连

    val regFile = Input(WorkerBeeRegister(cfg))           // 基地址
    val reg_baseAddr = Input(UInt(cfg.addrWidth.W))
    val reg_kTile = Input(UInt(cfg.mnkWidth.W))
    val reg_mTile = Input(UInt(cfg.mnkWidth.W))
    val reg_nTile = Input(UInt(cfg.mnkWidth.W))
    val reg_rowOffset = Input(UInt(cfg.addrWidth.W))
    val reg_colTileOffset = Input(UInt(cfg.addrWidth.W))
    
    
    val bufPush = master(Stream(UInt(pushW.W)))      // 向 buffer push 数据
    val bufPopFire = Input(Bool())
    // 【P1-2 W4A8 订正（2026-10-13）】push 侧「半字切片 + 精确加宽」stretch 模式（仅
    //   hasMx4 配置生成端口；非 hasMx4 配置无端口、既有代码逐字不变 = anchor 零 diff）。
    //   stretch=1 时逻辑拍 = portBytes/2 字节半字（W4A8 混配 B 通道：16B = 32 个 fp4 =
    //   一个阵列 K 窗口的一行），物理读恒为 1 次对齐读 [L&~(portBytes-1), +portBytes)，
    //   本模块按「随在途读配对的半字位 FIFO」选 rsp 低/高半字输出到 stretchSrc，顶层
    //   组合加宽（E2M1 nibble→E4M3 LUT）成整拍经 stretchBeat 推入 buffer —— 1 读 1 推、
    //   每拍 1 槽，credit/节拍与 fp8 基线逐拍同构；gather 机构在本模式旁路（半字选择
    //   取代 lo/hi 两读）。订正史：初版「1 源字读→push 连推 2 拍」展开器已被证伪废弃
    //   （源字 64K 跨 2 个 loadW 窗口，连推产生行交错序，PROBE4/PROBE6 实测权重与
    //   scale 双错位），expBusy/expPhase/expWord/2 槽 credit/expPathFree 串行门随本
    //   订正一并删除；单读契约由 CalcConfig 混配 errCode8（K%64 且 bAddr%32）保证
    //   末拍读不越张量界。
    val stretch      = if (cfg.hasMx4) Some(Input(Bool())) else None
    val stretchSrc   = if (cfg.hasMx4) Some(Output(UInt(math.max(1, pushW / 2).W))) else None
    val stretchBeat  = if (cfg.hasMx4) Some(Input(UInt(pushW.W))) else None
  })

  // 【Ivy⑧ 死信号清理】原 private val mxRunDma（MX 运行期判据 = isMx(aFmt)）在本模块内
  //   从未被任何逻辑消费（全仓 grep 仅此一处定义），firtool DCE 早已消除 ⟹ 删除定义 RTL
  //   逐位不变（三套 verilog normalized-diff 由 Verify 复核）。problem.md ④ 对 mxRunDma 的
  //   引用属 W4A8 根因分析的历史留痕（Executor 侧 mxRunB 仍在），保留不动。

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
  val lastBTileStartAddr = RegInit(0.U(cfg.addrWidth.W))
  val colAddr = RegInit(0.U(cfg.addrWidth.W))  // 当前读地址
  val rowStep = RegInit(0.U(cfg.addrWidth.W))  // 内层每拍地址步进
  // 【B5 清理】旧 bNextKTileAddressOffset 寄存器（loadWMode=0 的 +2*totalN*rowOff
  //   k-switch 步长）在拆分重构后已无引用（loadWMode=0/1 均改用 lastBTileStartAddr
  //   模型），为死寄存器；随 B5 修复一并下线，消除「回退残留」误导。
  val kCnt = RegInit(0.U(cfg.mnkWidth.W))
  val nCnt = RegInit(0.U(cfg.mnkWidth.W))

  val lineTarget  = RegInit(0.U(16.W))
  val lineCounter = RegInit(0.U(16.W))
  val ostCredit= RegInit(0.U(log2Up(bufDepth + 1).W))
  val errReg = RegInit(false.B)
  val ERR_BUS = 1.U(2.W)
  val ERR_FIFO = 2.U(2.W)
  val errState = RegInit(0.U(2.W))

  // ==========================================================================
  // 【P0-1/P0-2 gather 读】非对齐逻辑拍的「对齐多发读 + 字节移位重组」
  //   裁定（2026-10-12）：外部读端口按 portBytes = pushW/8 字节对齐取数；
  //   逻辑拍地址 L 非对齐时，DMA 自发读 [L-off, L-off+portBytes] 两个对齐字，
  //   内部拼出 memory[L .. L+portBytes)（off = L 的字节偏移）。端口位宽、软件
  //   张量布局、buffer 每逻辑拍恰收 1 push 的契约全部不变；不新增 errCode 守护。
  //
  //   机制 = drain-then-gather：对齐拍保持原流水（credit 门控多拍在途）；遇到
  //   非对齐拍先等在途读全部返回（inflCnt===0），再串行发 lo/hi 两读并重组。
  //   gPhase: 0=idle（对齐拍自由流水）/ 1=lo 已发、等 lo rsp / 2=发 hi 读 /
  //           3=hi 已发、等 hi rsp（hi rsp 拍合并 push，回 0）。
  //   credit 记账口径 = 每【逻辑拍】一次：只在逻辑拍最后一个物理读 fire 时 -1
  //   （对齐拍 = 其 req.fire；非对齐拍 = hi 读 req.fire；lo 读不扣 credit）。
  //   快路径恒等：全对齐激励下 gPhase 恒 0、req/push/credit/推进逐拍同原实现。
  // ==========================================================================
  val portBytes = pushW / 8
  val offBits   = log2Up(portBytes)
  val inflCnt = RegInit(0.U(log2Up(bufDepth + 2).W))  // 在途物理读计数（饱和递减防 flush 后陈旧 rsp 下溢）
  val gPhase  = RegInit(0.U(2.W))
  val loReg   = Reg(UInt(pushW.W))                     // 非对齐拍 lo 字暂存
  val offReg  = Reg(UInt(offBits.W))                   // 非对齐拍字节偏移（合并移位量）

  val byteOff     = curAddr(offBits - 1, 0)
  // 【P1-2 W4A8 订正】stretch 模式旁路 gather 判定：逻辑拍是半字片（L mod portBytes
  //   ∈ {0, portBytes/2}），奇片拍不走 lo/hi 两读机构，由半字位选择取代（见 sel FIFO）。
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

  // 在途物理读计数：req.fire +1 / rsp.fire -1（饱和递减）
  when(io.dmaExtRdIF.req.fire && io.dmaExtRdIF.rsp.fire) {
  }.elsewhen(io.dmaExtRdIF.req.fire) {
    inflCnt := inflCnt + 1.U
  }.elsewhen(io.dmaExtRdIF.rsp.fire && inflCnt =/= 0.U) {
    inflCnt := inflCnt - 1.U
  }

  // ==========================================================================
  // 【P1-2 W4A8 订正 · hasMx4 only】stretch 半字选择 FIFO（1-bit × depth 环形）。
  //   req.fire 拍压入本逻辑拍 L 的半字位 curAddr(offBits-1)（16B 拍片契约下
  //   L mod portBytes ∈ {0, portBytes/2}），rsp 返回拍弹出选 rsp 低/高半字。
  //   rsp 与 req 同序（与 gather lo/hi 同前提）；depth ≥ 最大在途读数（credit
  //   1/拍门控 ⟹ 在途 ≤ bufDepth，队满不可达）。TB 零延迟应答（req.fire 同拍
  //   rsp.fire 且队空）走旁路直取本拍 enq 位。start 重载拍 selCnt 清零（残位
  //   不跨 run 泄漏，与 credit/gPhase/inflCnt 同拍复位口径一致）。
  // ==========================================================================
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
    //   ★外层 Mux 两臂都必须是「按位选出的 rsp 半字」——初版误把 1-bit 半字位本身放进
    //   Mux 臂，Chisel 将其零扩成 128'h0/1 ⟹ 旁路拍 stretchSrc 退化为「元素0=LUT(halfbit)、
    //   其余全 0」（实测 PROBE4：全列恒 0.5 = 唯一存活乘积 A[m][32]×0.5，窗口0 全零拍）。
    val selBit = Mux(selBypass, curAddr(offBits - 1), selBits.get(selTail.get))
    io.stretchSrc.get := Mux(selBit,
      io.dmaExtRdIF.rsp.payload.data(pushW - 1, pushW / 2),
      io.dmaExtRdIF.rsp.payload.data(pushW / 2 - 1, 0))
    when(io.start) {
      selCnt.get := 0.U
    }.elsewhen(selStore && selDeqFire) {
      // 同拍一进一出（新请求入队 + 旧在途应答出队）：响应属 selTail 旧请求、新请求的
      //   半字位须写 selHead ⟹ head/tail 各推进一格、cnt 不变。漏判此角会使新位丢失
      //   且下一响应复用已消费的 tail 位（真实带延迟内存可达；零延迟 TB 恒走 bypass 不可达）。
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

    // 【P1-2 W4A8 订正】stretch 模式 1 读 1 推、1 槽/拍 ⟹ credit 记账回基线单一式
    //   （初版 2 槽/字预留块已随连推方案一并废弃）。
    when(finalReqFire & io.bufPopFire){
    }.elsewhen(finalReqFire){
      ostCredit := ostCredit - 1.U
    }.elsewhen(io.bufPopFire){
      ostCredit := ostCredit + 1.U
    }

  }

  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  // gPhase=0：对齐拍凭 credit 自由发；非对齐拍须先 drain（inflCnt===0）且预占
  //   1 credit（hi 读将扣）。gPhase=2：发 hi 读（credit 已在起拍预占，不再查）。
  //   gPhase=1/3：等 rsp，不发。
  // 【P1-2 W4A8 订正】stretch=1 时请求门控即基线（1 读 1 槽/拍，无串行化门）；
  //   gather lo/hi 机构被 unaligned 覆盖旁路（见 unaligned 定义），gPhase 恒 0。
  io.dmaExtRdIF.req.valid       := state === sTRANSFER && (
    (gPhase === 0.U && ostCredit =/= 0.U && (!unaligned || inflCnt === 0.U)) ||
    (gPhase === 2.U)
  )
  // 【P1-2 W4A8 订正】stretch 默认臂取 alignedBase：奇半字拍（L mod portBytes =
  //   portBytes/2）的对齐单读即 [L-off, +portBytes)，想要的半字由 sel FIFO 选择，
  //   不走 lo/hi 两读；偶半字拍 alignedBase == curAddr，与基线逐值相同。
  private val reqAddrDefault = if (cfg.hasMx4) Mux(io.stretch.get, alignedBase, curAddr) else curAddr
  io.dmaExtRdIF.req.payload.addr := MuxCase(reqAddrDefault, Seq(
    (gPhase === 0.U && unaligned) -> alignedBase,               // lo 读：对齐化
    (gPhase === 2.U)              -> (alignedBase + portBytes.U) // hi 读：下一对齐字
  ))
  io.dmaExtRdIF.rsp.ready       := true.B //buffer永远能push的时候dma才会发请求

  // push 口径：
  //   phase0 的 rsp = 对齐拍（同拍对齐 req 的应答，或 drain 前遗留的在途对齐读）→ 直 push；
  //     但 phase0 且本拍 req.fire 的是 lo 读（unaligned）时，该 rsp 是 lo 字 → 进 loReg 不 push
  //   phase1 的 rsp = 延迟返回的 lo 字 → 不 push
  //   phase2 同拍 hi 应答（hiSameCycle）/ phase3 的 rsp = hi 字 → 与 loReg 合并 push
  val alignedRspPush = io.dmaExtRdIF.rsp.valid && gPhase === 0.U &&
    !(io.dmaExtRdIF.req.fire && unaligned)
  if (cfg.hasMx4) {
    when(io.stretch.get) {
      // 【P1-2 W4A8 订正】stretch：gPhase 恒 0、无 gather ⟹ push 脉冲 = alignedRspPush
      //   （= rsp.valid），payload = 顶层组合加宽后的整拍（E2M1 半字片 → E4M3 阵列拍）。
      //   1 读 1 推、每拍 1 槽，较基线零额外拍延迟，消费端占用门不受影响。
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
    // --- sIDLE: 等待 start（锁存逻辑在下方 state===sIDLE|start 块统一处理） ---
    is(sIDLE) {
       errReg := false.B
       lineCounter := 0.U  
      
      // 防重复 start 竞态：重载拍（非 sIDLE 收到 start）已直达 sTRANSFER，
      // 仅当本拍确在 sIDLE 时才允许 start 推进，避免锁存/状态二次覆盖
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
      // start 重载（契约对齐）：搬运途中收到 start（异常路径强制重启）→
      // 同拍锁存新参数（上方 |io.start 锁存块）并复位计数/credit 重启遍历；
      // 正常流程 start 只在 sIDLE 出现，本分支死路、行为不变
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
    // --- sTRANSFER: 收数据 push buffer，推进地址与 tile 索引 ---
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
            // 切换n
            
            kCnt := 0.U
            nCnt := nCnt + 1.U
            state := sTRANSFER            

            //Transport read
            when(io.regFile.loadWMode){
              //curAddr := lastBTileStartAddr + cfg.totalN.U * io.reg_rowOffset
              curAddr := lastBTileStartAddr + io.reg_colTileOffset + (cfg.totalN.U - 1.U) * io.reg_rowOffset
              lastBTileStartAddr := lastBTileStartAddr + io.reg_colTileOffset + (cfg.totalN.U - 1.U) * io.reg_rowOffset
            }.otherwise{
              // 【B5 修复：loadWMode=0（垂直/非转置 B）切换 n-tile】
              // 结构取舍留痕（leader 授权约束 1）：旧 WorkerBeeDma.scala 用
              //   curAddr := bAddr + colAddr，其中 colAddr 在 sIDLE 锁存为
              //   bColTileAddressOffset + (totalN-1)*bRowAddressOffset（含降序起点项）。
              // 拆分重构后本文件改用 lastBTileStartAddr 模型（loadWMode=1 分支已采用），
              //   且 sIDLE 的 colAddr 初值收窄为纯 colTileOffset（降序起点项已移到
              //   curAddr/lastBTileStartAddr 初值）。此处不逐字恢复旧 bNextKTileAddressOffset
              //   + colAddr 结构，而是镜像 loadWMode=1 的 lastBTileStartAddr 风格：
              //   两分支结构对称、可审计性一致，且死寄存器 bNextKTileAddressOffset 得以下线。
              // 语义等价性：下一 n-tile 的 kt0 降序块起点 = bAddr + n*colTileOff + (totalN-1)*rowOff。
              //   本文件 colAddr 累计 = n*colTileOff，故须显式补 (totalN-1)*rowOff 降序起点项
              //   （旧码把该项藏在 colAddr 初值里，等价）。原 BUG：用 totalN*rowOffset（多一拍）
              //   且未维护 lastBTileStartAddr（致后续 k-switch 基于陈旧块起点）。
              curAddr := io.reg_baseAddr + colAddr + (cfg.totalN.U - 1.U) * io.reg_rowOffset
              lastBTileStartAddr := io.reg_baseAddr + colAddr + (cfg.totalN.U - 1.U) * io.reg_rowOffset
              colAddr := colAddr + io.reg_colTileOffset
            }

          }
        }.otherwise{
          
          // 切换K
          kCnt := kCnt + 1.U
          state := sTRANSFER
          
          //Transport read
          when(io.regFile.loadWMode){
            
            curAddr := lastBTileStartAddr + io.reg_colTileOffset
            lastBTileStartAddr := lastBTileStartAddr + io.reg_colTileOffset
            
          }.otherwise{
            // 【B5 修复：loadWMode=0（垂直/非转置 B）切换 k-tile】
            // 旧 WorkerBeeDma.scala 用 curAddr := curAddr + bNextKTileAddressOffset，
            //   loadWMode=0 分支 bNextKTileAddressOffset = (totalN*2)*bRowAddressOffset，
            //   旧注释原文：「垂直：B 降序读取，下一 k 维 tile 需 +2*totalN*bRowOff
            //   （这里乘以 2 的原因是 B 矩阵是降序读取）」。×2 的由来：降序遍历末拍后
            //   curAddr 已越过块内最低行一拍（= blockStart - totalN*rowOff），故须
            //   +2*totalN*rowOff 才落到下一块起点 blockStart + totalN*rowOff。
            // 本文件改用 lastBTileStartAddr 模型（恒存当前块降序起点，不做末拍多减），
            //   故下一块起点 = lastBTileStartAddr + totalN*rowOffset（等价于旧码结果，
            //   leader 已独立验算）。原 BUG：curAddr := curAddr + totalN*rowOffset
            //   （少一个 totalN*rowOff 的降序补偿）→ kt1 重读 kt0 的最低行，GEMM 静默算错。
            curAddr := lastBTileStartAddr + cfg.totalN.U * io.reg_rowOffset
            lastBTileStartAddr := lastBTileStartAddr + cfg.totalN.U * io.reg_rowOffset
          }
          
         
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
      // 次拍以新参数/满 credit/零计数从 sTRANSFER 起跑（与 WorkerBeeDmaScaleRd 同构）
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


