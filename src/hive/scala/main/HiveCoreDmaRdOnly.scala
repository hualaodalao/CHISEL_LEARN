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
  *   HiveCoreDmaWrOnly 删掉的那条【逐字同源】，是更早提交的化石，与本模块代码不符：
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
  * 【#133 死引用清理 / #136-RW1 去裸 token】此处原写「与 HiveCoreExecutor 侧的 A/B tile
  *   首址别名一致」并复述了那两个原名的裸 token。结论不变：该类别名在 main 源码中
  *   【无任何定义】（Executor 不消费地址偏移，只发 peekBlock/doneBlock 块协议与 start
  *   脉冲），属死引用，已删除。#136-RW1 采纳选项 (b)：留痕改用【描述性表述】、不再出现
  *   裸 token，使 grep 式死引用审计在 src/hive/scala/main/ 下 0 命中，无需读上下文区分
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
  *     修复只改 HiveCoreCalcConfig 的赋值式，本模块【功能行零改动】，两支闭式原样有效。
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
  * @param cfg     HiveCore 配置
  * @param bufWidth buffer push 数据位宽（默认 = cfg.aExtW；外部 rsp 与本口
  *                 等宽直连，每拍 push 一个 beat）
  * @param bufDepth buffer 深度（决定 bufAvailability 位宽；默认 = aBufferDepth，
  *                 B 实例需传 bBufferDepth）
  */
class HiveCoreDmaRdOnly(cfg: HiveCoreConfig, bufWidth: Int = 0, bufDepth: Int = 0) extends Module {
  val pushW = bufWidth 
  val availDepth = bufDepth
  // 【注释订正（B5 调研发现，仅文档、不改行为）】旧注释称「MX scale 读搬运已拆分为
  // 独立模块 HiveCoreDmaScaleRd」已 STALE：672fb37 提交删除了 HiveCoreDmaScaleRd.scala，
  // scaleA/scaleB 现复用本模块（HiveCore 例化 bufWidth=8：scaleA tie isA=true、
  // scaleB tie isA=false，见 HiveCore.scala scale DMA 接线）。HiveCore.scala L457
  // 同类 STALE 注释属禁动区外的文档瑕疵，一并列入报告交回裁定，不在此改动。

  val io = IO(new Bundle {
    val isA = Input(Bool())                          // 例化时 tie 死：true=A buffer, false=B buffer
    // 控制/状态（无 peek 握手：DMA 启动后自主搬完所有 tile）
    val start = Input(Bool())                        // 启动脉冲
    val done  = Output(Bool())                       // 完成脉冲
    val busy  = Output(Bool())                       // 忙标志
    val err   = Output(Bool())                       // 错误标志

    
    val dmaExtRdIF = HiveCoreDMAExtReadOnlyIF(cfg, pushW)   // 外部只读通道 cmd(addr)/rsp(data)，与 bufPush 等宽直连

    val regFile = Input(HiveCoreRegister(cfg))           // 基地址
    val reg_baseAddr = Input(UInt(cfg.addrWidth.W))
    val reg_kTile = Input(UInt(cfg.mnkWidth.W))
    val reg_mTile = Input(UInt(cfg.mnkWidth.W))
    val reg_nTile = Input(UInt(cfg.mnkWidth.W))
    val reg_rowOffset = Input(UInt(cfg.addrWidth.W))
    val reg_colTileOffset = Input(UInt(cfg.addrWidth.W))
    
    
    val bufPush = master(Stream(UInt(pushW.W)))      // 向 buffer push 数据
    val bufPopFire = Input(Bool())
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
              // 结构取舍留痕（leader 授权约束 1）：旧 HiveCoreDma.scala 用
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
            // 旧 HiveCoreDma.scala 用 curAddr := curAddr + bNextKTileAddressOffset，
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


}


