/** HiveCoreExecutor — Weight-Stationary 自动 Tiling GEMM 执行器
  *
  * EXECUTE 触发后，根据寄存器组中的 M/N/K 维度和阵列能力 (totalN×totalN)
  * 自动切块 tiling，采用 N→K→M 循环顺序实现 weight-stationary 复用：
  *   - 外层 N 循环: 每个 N tile 对应一列输出
  *   - 中层 K 循环: 每个 (N,K) 对加载一次权重
  *   - 内层 M 循环: 权重驻留，连续处理所有 M tiles
  *
  * A/C 数据搬运由自主 DMA 完成（与 bDma 同一范式）：
  *   - A: RdOnly DMA 在 execute 拍单次启动，内建 nTile 轮次（nTile 外 →
  *     kTile 中 → M 内），自主供满 nTiles 轮 A 后自然停止；本模块 A 侧
  *     为纯 pop 消费者，在 sLOAD_A_DMA 仅等待 aOccupancy 积累到位后顺序 pop
  *   - C: WrOnly DMA 在 execute 启动后自主待命，本模块在 sSTORE_C_DMA
  *     拉高 cStoreGate 开门，等 cOccupancy 排空即视为写回完成
  *
  * 内部 FSM 依次完成：
  *   - 等待权重就绪 → 写入阵列 (每个 N,K 对一次，bDma 自主扫描供数)
  *   - 等待 A 就绪 → GEMM 计算 (每个 M tile 一次，权重复用)
  *   - 处理 partial sum（K > totalN 时多 pass 累加）
  *   - 门控写回结果 (末 K pass，所有 M tiles 完成后)
  *
  * aIn skew 实现说明：
  *   根据 HiveComb 契约，外部需按「行 i 延迟 i 拍」错峰供数（通道 0 无延迟）。
  *   本模块通过 fire 使能的 ShiftRegister 链实现 skew：供数（aPop）/回灌
  *   （cPop）fire 拍才移位，供数结束后继续使能 totalN-1 拍并灌零，
  *   让波前完整流过阵列；hiveValidIn 同步保持 curM + 2*(totalN-1) 拍。
  */

import chisel3._
import chisel3.util._
import scala.annotation.varargs

/** 旧类名别名：既有测试（HiveCoreSpec elaborate 用例）仍按 HiveCoreExecutor
  * 引用；新实现类名为 HiveCoreExecutor2，此处保留类型别名使 Test/compile 通过
  */
class HiveCoreExecutor(cfg: HiveCoreConfig) extends HiveCoreExecutor2(cfg)

class HiveCoreExecutor2(cfg: HiveCoreConfig) extends Module {

  val io = IO(new Bundle {
    // 控制
    val execute  = Input(Bool())        // 脉冲启动
    val busy     = Output(Bool())
    val done     = Output(Bool())       // 脉冲完成

    //dma状态：
    val dmaABusy = Input(Bool())
    val dmaBBusy = Input(Bool())
    val dmaCBusy = Input(Bool())

    // 寄存器输入（由顶层寄存器组驱动）：全部寄存器统一经 io.regFile 引用。
    val regFile = Input(HiveCoreRegister(cfg))
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))  // tile 数、地址偏移与 cBeatAddrStep
    

    // A buffer 自主 DMA（RdOnly）：execute 单次启动全自主供数，
    // 本模块 A 侧无控制输出，仅占用量感知
    val aOccupancy = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))

    // C buffer 自主 DMA（WrOnly）控制：store 门控（电平）+ 占用量感知
    val cStoreGate = Output(Bool())                    // 末 K pass store 窗口拉高
    val cPeek      = Output(Bool())
    val cPeekDone  = Input(Bool())
    val cOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))

    // Scratchpad
    val aPop  = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    // B buffer（权重专用）：RdOnly DMA 自主写入，本模块按行 pop
    val bPop  = slave(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val bOccupancy = Input(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val cPop  = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPush = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val flushA = Output(Bool())
    val flushB = Output(Bool())
    val flushC = Output(Bool())

    // MX scale 通道（仅 hasMx 生成，非 MX 配置不暴露）：
    val scaleAPop    = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleAW.W))))             else None
    val scaleBPop    = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleBW.W)))) else None
    // 【P0-1 gather】scaleB FIFO 占用量输入：loadW 预取窗口门控用（sDRAIN→sLOAD_B）
    val scaleBOcc    = if (cfg.hasMx) Some(Input(UInt(log2Up(cfg.scaleBufferDepth + 1).W))) else None
    val hiveScaleAIn = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(8.W))))   else None
    val hiveScaleBIn = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(8.W))))   else None

    // 【P0 epilogue-1 fused bias】bias 取数握手（仅 hasMx 生成，与 dmaScaleA 端口同门控）：
    //   biasIn = 16×32b 字流（每 N-tile 一组，收集齐置 biasLoaded）；biasFetchReq/Nt =
    //   取数触发脉冲（sIDLE-execute 发 nt=0，sNEXT_NK_TILE 推进发 nt=curNTile+1），
    //   由顶层 HiveCore 的 bias 取数器消费（盗用 dmaScaleA 端口供数）。
    val biasIn       = if (cfg.hasMx) Some(slave(Stream(UInt(32.W))))    else None
    val biasFetchReq = if (cfg.hasMx) Some(Output(Bool()))               else None
    val biasFetchNt  = if (cfg.hasMx) Some(Output(UInt(cfg.mnkWidth.W))) else None

    // 【P0 epilogue-2 fused requant】rqM 取数握手（仅 hasMx 生成，与 bias 同一 dmaScaleA
    //   端口、顶层两相连续取数）：rqMIn = 16×32b 字流（每 N-tile 一组，收集齐置
    //   rqMLoaded）；rqMFetchReq/Nt 同 biasFetchReq/Nt 范式。rqMOut = 已收集的
    //   per-channel M 向量（寄存器直出，顶层接线至 HiveOutCvt 的 requant 通路；
    //   hasInt 门控在例化侧，本模块只按 hasMx 供给）。
    val rqMIn       = if (cfg.hasMx) Some(slave(Stream(UInt(32.W))))    else None
    val rqMFetchReq = if (cfg.hasMx) Some(Output(Bool()))               else None
    val rqMFetchNt  = if (cfg.hasMx) Some(Output(UInt(cfg.mnkWidth.W))) else None
    val rqMOut      = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(32.W)))) else None

    // HiveComb 驱动
    val hiveAIn     = Output(Vec(cfg.totalN, UInt(cfg.aEffW.W)))
    val hivePsumIn  = Output(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveLoadH   = Output(Bool())
    val hiveLoadV   = Output(Bool())
    val hiveLoadVLock   = Output(Bool())
    // 水平 loadW 加载（可选模式，regs(7)(5) 选择）：垂直模式下恒 0
    val hiveLoadW       = Output(Bool())
    val hiveLoadWLock   = Output(Bool())
    val hiveValidIn = Output(Bool())
    val hiveClear   = Output(Bool())
    val hiveCOut    = Input(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveValidOut = Input(Bool())

    // 进度
    val progress = Output(UInt(16.W))

    // 【perf 观测端口】供数等待探针（纯组合、无状态）：顶层 io.perf 直通，
    //   由外部 PMU 按拍采样（本模块不内建计数器）
    val perf = Output(new Bundle {
      // 供数相（sLOAD_A）阵列空闲拍（aPop 未 fire，含 A/scaleA 供数不足、
      // bias/rqM 未收齐、C 写回占用读口等全部挂起成因）
      val stallA = Bool()
      // 权重供数等待：loadW 窗口 bPop 停拍 ∨ sDRAIN 预蓄等待拍
      val stallB = Bool()
    })
  })

  // ==========================================================================
  // FSM 状态编码 (Weight-Stationary: M→N→K loop order)
  // ==========================================================================
  val sIDLE            = 0.U(4.W)
  val sLOAD_B          = 1.U(4.W) //load B buffer to prefile pe weight
  val sLOAD_A          = 2.U(4.W) //load A buffer to compute
  val sNEXT_NK_TILE    = 3.U(4.W)
  val sDRAIN           = 4.U(4.W)


  val state = RegInit(sIDLE)

  // ==========================================================================
  // Tiling 寄存器
  // ==========================================================================
  val totalN = cfg.totalN

  //val mTiles   = Reg(UInt(16.W)) //M维度按行索引
  val nTiles   = Reg(UInt(16.W)) //N维度按tile索引
  val kTiles   = Reg(UInt(16.W)) //K维度按tile索引
  //val curMTile = Reg(UInt(16.W))
  val curNTile = Reg(UInt(16.W))
  val curKTile = Reg(UInt(16.W))
  
  val doneTiles = Reg(UInt(16.W))

  // 当前 tile 参数
  val curM = Reg(UInt(16.W))  // 实际行数
  val depthOfCBufferUsed = Reg(UInt(16.W))

  // 计算相关
  val tileInterCnt = RegInit(0.U(log2Up(cfg.totalN).W))
  // drain 计数：位宽按水平 loadW 模式的最大 drain 值 2*(totalN-1)+1 取
  // （log2Up(2*totalN)）；垂直模式计数值 totalN-1 仍在该位宽内，行为不变
  val drainNextCnt = RegInit(0.U(log2Up(2 * cfg.totalN).W))
  val hasNext = RegInit(false.B)
  // 水平 loadW 模式锁存（execute 拍采样 regs(7)(5)；垂直模式恒 false，
  // 所有 loadWModeReg 门控分支均为死路）
  val loadWModeReg = RegInit(false.B)

  // ==========================================================================
  // mxRunB —— single runtime MX predicate (W1)
  //   Consumers: scaleB pop handshake gate + lock-beat scaleB valid assertion.
  //   Replaces the former split predicate (handshake gate on isMx(aFmt),
  //   write/forward gates on isMx(bFmt)). Their equality used to be guaranteed
  //   only indirectly, across modules, by HiveCoreCalcConfig errCode=3
  //   (aFmt/bFmt same-family XOR check); this file had no local defence.
  //   HiveCoreExecutor2 is a publicly instantiable module (HiveCoreSpec
  //   elaborates it standalone), so any direct hookup or unit stimulus that
  //   bypasses CalcConfig could run aFmt=MX with bFmt=non-MX: the handshake
  //   gate would fire per beat and the lock beat would be emitted, while the
  //   bFmt-based gate never consumed the scaleB FIFO -> the array would latch
  //   stale/uninitialised scale bytes -> and the MAC side sumMx (its io.sum Mux
  //   predicate comes from aFmt) is selected and really consumes them ->
  //   silent numeric error.
  //   Ruling: aFmt is authoritative (same source as the MAC consume gate), so
  //   "handshake fire <=> scaleB consumed <=> sumMx consumed" is a structural
  //   identity inside this file and no longer depends on any cross-module
  //   check. That is the required local defence line.
  //   NOTE (#146): the former per-column scaleBRegs register file has been
  //   removed. scaleB now flows combinationally from scaleBPop.payload through
  //   hiveScaleBIn into the HiveComb/HiveCell/HiveWorker per-lane chain and is
  //   captured by HiveWorker.scaleBReg on the loadWLock beat. See the axis
  //   comment on the "B scale feed" loop below.
  val mxRunB = DataFormat.isMx(io.regFile.aFmt)

  // 【P2 纯 FP8】运行期纯 FP8 判据（aFmt 权威，与 mxRunB 同源同风格）：驱动 scaleA/
  //   scaleB 两注入点的 127 恒等注入。纯 FP8 运行时 isMx(aFmt)=false ⟹ mxRunB=false
  //   （scale FIFO 恒空、不消费、供数不等 scale），而阵列侧 fmt 已被 HiveCore remap
  //   成 MXE4M3/E5M2 ⟹ MAC 走 MX8 支路并真实消费 scale ⟹ scale 字节必须组合直供
  //   127（E8M0 2^0=1.0 恒等乘，FpCvt 的 exp_fp32 = e + 127 - bias 数学上即纯 FP8
  //   解码）。errCode3 新臂保证 aFmt=FP8 时 bFmt 必同为 FP8，单一 aFmt 判据完备。
  //   if(cfg.hasFp8) 门控：不含 FP8 的配置不生成此信号（零 diff）。
  val fp8Run = if (cfg.hasFp8) DataFormat.isFp8(io.regFile.aFmt) else false.B

   // ==========================================================================
  // CBufferPipe
  val cStoreGateReg = RegInit(false.B)
  val cStoreGateRegNxt = RegInit(false.B)
  val cBufferReservedAtleastTotalN = RegInit(false.B)
  when(cfg.cBufferDepth.U - io.cOccupancy >= cfg.totalN.U){
    cBufferReservedAtleastTotalN := true.B
  }.otherwise{
    cBufferReservedAtleastTotalN := false.B
  }
  io.cStoreGate := cStoreGateReg
  cStoreGateRegNxt := cStoreGateReg
  io.cPeek      := ~cStoreGateRegNxt & cStoreGateReg
  when(io.cPeekDone){
    cStoreGateReg := false.B
  }

  
  //A口供数
  for (i <- 0 until cfg.totalN) {
    io.hiveAIn(i) := io.aPop.payload(cfg.aEffW * (i+1)-1, cfg.aEffW * i)
    if(cfg.hasMx){
      val block = Mux(io.regFile.aMxBlockSize === MXBlockSize.M16, 16.U, 32.U)
      val step = Mux(io.calcConfig.isAX2, 2.U, 4.U)
      val idx = (i.U * step) / block
      // Chisel 的 UInt.apply(hi, lo) 只接受 Int/BigInt 字面量，不支持运行期动态位选；
      // 「取第 idx 个 8-bit scale 字节」的等价唯一形态 = 右移 idx*8 后取低 8 位。
      // 【P2 纯 FP8】fp8Run 时注入点恒 127（恒等乘）：scaleA FIFO 不启动（isMx=false）
      //   且 MX MAC 真实消费此字节，不能读空 FIFO 的陈旧 payload。★两臂均显式
      //   8-bit（127.U(8.W)）：裸 127.U 推 7-bit，Mux 静默零扩属本项目已三次踩坑的
      //   形态（见 chisel-mux-vecinit-pad 记忆）。
      val scaleAByte = (io.scaleAPop.get.payload >> (idx * 8.U))(7, 0)
      io.hiveScaleAIn.get(i) := (if (cfg.hasFp8) Mux(fp8Run, 127.U(8.W), scaleAByte) else scaleAByte)
    }
  }

  // ==========================================================================
  // B scale feed.
  // --------------------------------------------------------------------------
  // AXIS SEMANTICS (ground truth established in #146, do not "fix" this):
  //   loop variable `lane` is the K-lane index, NOT the weight-row (N) index.
  //   lane carries the K elements [kt*kPerBeat + lane*step, ... + step-1] of
  //   one b beat, where step = elemsPerLane (2 for fp8, 4 for fp4). Therefore
  //   idx = (lane*step)/blk selects the E8M0 scale byte of the MX block that
  //   this lane belongs to; numScalesPerBeat = kPerBeat/blk distinct bytes are
  //   consumed out of the 32-bit scaleBPop payload.
  //   hiveScaleBIn(lane) enters HiveComb.io.scaleBIn(lane), which drives
  //   HiveCell.io.scaleBIn(x=lane) at column y=0 and then shifts right along
  //   the a-chain (HiveComb cj cascade + HiveCell y cascade, 1 cycle each).
  //   In loadW mode the weight of PE(x,y) is latched from beat (totalN-1-y)
  //   lane x, i.e. B[n_out = y][k-lane x]; because the scaleB chain is
  //   lockstep with the a-chain, PE(x,y) captures on the very same loadWLock
  //   beat the scale byte of lane x of beat (totalN-1-y) -- same beat, same
  //   n_out, same lane, same K-block. Zero cycle skew.
  //   Consequence: the array row coordinate x IS the K-lane axis and the array
  //   column coordinate y IS the output-N axis. The HiveComb local name
  //   `globalRow` only denotes the PE-grid row coordinate; it never meant the
  //   weight N axis (see HiveComb / HiveCell / HiveWorker header notes).
  // ==========================================================================
  if(cfg.hasMx){
    val block = Mux(io.regFile.bMxBlockSize === MXBlockSize.M16, 16.U, 32.U)
    // 【P1-2 W4A8 stretch 订正（2026-10-13）】混配（isMx8(aFmt)&&isMx4(bFmt)，与 HiveCore.w4a8Mix
    //   同式）时 bFmt 寄存器仍为 E2M1 ⟹ isBX2=false，但 B DMA 走 stretch：每个阵列拍恒推进
    //   32 个 K 元素（fp8 口径）⟹ 每 lane 每拍 2 个 K、与纯 MX8 同构 ⟹ step=2。
    //   scaleB 侧【无展开】：scaleBDma 取全量 kTile 窗口（reg_kTile=calcConfig.kTile），
    //   每阵列拍 pop 一个相邻 32b 内存字——blk32 时 byte0=块 w；blk16 时该字 = 源块
    //   {2w,2w+1} 的字节对（byte0/byte1）。本环按基线 fp8 语义切片 idx=(lane*2)/blk
    //   即得正确字节（blk16: lane0-7→byte0、lane8-15→byte1；blk32: 恒 byte0）。
    //   非混配时 scaleBPop 字内字节排布与基线一致，此环切片式不变。非 hasMx4 配置无此支路（零 diff）。
    val step = if (cfg.hasMx4)
      Mux(io.calcConfig.isBX2 || (DataFormat.isMx8(io.regFile.aFmt) && DataFormat.isMx4(io.regFile.bFmt)), 2.U, 4.U)
    else Mux(io.calcConfig.isBX2, 2.U, 4.U)

    for (lane <- 0 until cfg.totalN) {
      val idx = (lane.U * step) / block
      // Same as the A side: dynamic byte select is expressed as "shift right by
      // idx*8 then take the low 8 bits" (Chisel apply(hi,lo) is literal-only).
      // 【P2 纯 FP8】同 A 侧：fp8Run 时恒 127 恒等注入（scaleB FIFO 不启动、
      //   阵列侧 remap 后的 MX8 MAC 真实消费；两臂显式 8-bit 防 Mux 零扩）。
      val scaleBByte = (io.scaleBPop.get.payload >> (idx * 8.U))(7, 0)
      io.hiveScaleBIn.get(lane) := (if (cfg.hasFp8) Mux(fp8Run, 127.U(8.W), scaleBByte) else scaleBByte)
    }
  }

  // ==========================================================================
  // 【P0 epilogue-1 fused bias】bias 收集寄存器、32b→cEffW 基底转换与收集 FSM 外壳
  // --------------------------------------------------------------------------
  // 基底注入方案（零加法器）：bias 取代 sLOAD_A pass-0 的 psum 零基底，沿既有
  // psum 逐列链进入阵列累加（C[m][n] = bias[n] + Σ A×B）。HiveComb 的 psumIn 是
  // 输出-N 轴【逐列】注入、psum 沿 K-lane 链向下累加（见 HiveComb 行连接块），
  // 故 biasRegs(i) 即输出列 nt*totalN+i 的 bias；bias 是列常量，经 bSkew 链的
  // 逐列延迟不改变取值。K>totalN 的后续 pass 仍走 cPop 回灌（bias 已烧进
  // pass-0 的 partial sum），阵列/MAC/OutCvt/cDma 全零改动。
  // ==========================================================================
  private val biasRegs   = if (cfg.hasMx) Some(RegInit(VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))) else None
  private val biasLoaded = if (cfg.hasMx) Some(RegInit(false.B)) else None
  private val biasCnt    = if (cfg.hasMx) Some(RegInit(0.U(log2Up(cfg.totalN).W))) else None

  // 【P0 epilogue-2 fused requant】rqM 收集寄存器（与 bias 同构；M 向量不进阵列、
  //   经 rqMOut 顶层接线至 HiveOutCvt 例化的 HiveRequant，故无 lazy 转换）
  private val rqMRegs   = if (cfg.hasMx) Some(RegInit(VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))) else None
  private val rqMLoaded = if (cfg.hasMx) Some(RegInit(false.B)) else None
  private val rqMCnt    = if (cfg.hasMx) Some(RegInit(0.U(log2Up(cfg.totalN).W))) else None
  // 【P0 epilogue-2 修复】rqM 暂存-提交双缓冲：M 的消费者在 HiveOutCvt in.fire
  //   （C 写回窗口末尾——行经 scratchpad 深缓冲晚到），而重取触发在 sNEXT_NK_TILE
  //   （窗口开门拍）。若字流直接原地重载 rqMRegs，取数会逐 lane 覆盖仍在本块 C
  //   排空路径上的行将要锁存的 M（实测 tile0 末行锁到 tile1 的 M，2/1024 元素
  //   失配；「低 lane 先被污染」的签名即逐 lane 顺序覆盖）。修复：字流先落
  //   staging，rqMRegs 仅在提交点整组更新。提交不变式（三款保证）：
  //   ① cvt.in.fire 仅发生在 cStoreGate 高电平窗口内（顶层 cvt.in.valid 门控），
  //      cPeekDone（doneBlock）恰为窗口边界 ⟹ 提交点两侧的 cvt fire 分别看到
  //      旧/新 M，不存在中间态；
  //   ② feedBase 的 rqMLoaded 门控保证目标 tile 的行在 M 收齐前不进阵列 ⟹
  //      staging 收齐必早于该 tile 任何行过 cvt ⟹「取数慢于本块 drain」的微例
  //      下迟到提交仍落在下一窗口开门之前；
  //   ③ 取数触发点/地址序列与修复前逐拍一致（sIDLE 发 nt=0、sNEXT_NK_TILE 发
  //      nt=curNTile+1），顶层两相取数器与 TB 地址金锚零改动。
  private val rqMStaging     = if (cfg.hasMx) Some(RegInit(VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))) else None
  private val rqMCommitArmed = if (cfg.hasMx) Some(RegInit(false.B)) else None
  // 【P0 epilogue-2 修复】rqM staging 提交点：每个 C 块 doneBlock（cvt 消费窗口
  //   边界）布防，rqMLoaded（= staging 收齐）即整组提交 rqMRegs。微例下取数
  //   晚于 doneBlock 时布防保持至收齐拍——由不变式②该提交仍早于目标 tile 任何
  //   行过 cvt。requantOn=0 时 rqMLoaded 恒 0（无字流），布防不触发提交。
  //   （本块必须位于上述寄存器声明之后——class 体自上而下执行，Chisel when 块
  //   在 Scala 求值期即建图，先引用后声明会 NPE。）
  if (cfg.hasMx) {
    when(io.cPeekDone) { rqMCommitArmed.get := true.B }
    when(rqMCommitArmed.get && rqMLoaded.get) {
      rqMRegs.get := rqMStaging.get
      rqMCommitArmed.get := false.B
    }
  }

  /** fp32 → lazy40 累加基底：位级镜像金锚权威 HiveSimCommon.fp32BitsTo40 ——
    * e==0（含全部 subnormal）→ 绝对 0；否则 {s, e, 隐含 1, m, 0^(cEffW-33)}，
    * 在 cEffW 内右侧对齐、高位补零（cEffW=40 时即权威式 {s<<39|e<<31|1<<30|m<<7}）。 */
  private def fp32ToLazy40(x: UInt): UInt = {
    require(cfg.cEffW >= 40, s"HiveCoreExecutor fp32ToLazy40 requires cEffW(${cfg.cEffW}) >= 40 (lazy fp32 accumulator)")
    val e = x(30, 23)
    Mux(e === 0.U, 0.U(cfg.cEffW.W), Cat(x(31), e, 1.U(1.W), x(22, 0), 0.U((cfg.cEffW - 33).W)))
  }

  /** bias 32b 字 → cEffW 累加基底：INT 运行（runIsInt，与 HiveCoreCalcConfig
    * errCode11 判据逐字同式）按 INT32 符号扩展；fp/MX 运行按 fp32 → lazy40。
    * errCode11 已在 EXECUTE 拒绝跨界组合 ⟹ 运行期实际到达的支路与 cFmt 语义一致。
    * 【P2 纯 FP8】runIsInt 补 !isFp8：FP8E4M3/E5M2 不属 is2B/FP32/isMx 任一谓词，
    *   不补则纯 FP8 运行被误判 INT 族 ⟹ bias 域错走 sext32（正确为 fp32→lazy40）。
    *   与 CalcConfig errCode11 的 runIsInt 修复逐字同式。 */
  private def biasLazy(i: Int): UInt = {
    val runIsInt = !DataFormat.is2B(io.regFile.aFmt) && io.regFile.aFmt =/= DataFormat.FP32 &&
      !DataFormat.isMx(io.regFile.aFmt) && !DataFormat.isFp8(io.regFile.aFmt)
    val w = biasRegs.get(i)
    Mux(runIsInt, Cat(Fill(cfg.cEffW - 32, w(31)), w), fp32ToLazy40(w))
  }

  if (cfg.hasMx) {
    // bias 收集：16 字齐 → biasLoaded 置位（sLOAD_A 供数门控放行）。ready 与状态
    //   无关：取数器只由本模块自己的触发脉冲启动，biasOn=0 或已收齐时无供给源。
    io.biasIn.get.ready := io.regFile.biasOn && !biasLoaded.get
    when(io.biasIn.get.fire) {
      biasRegs.get(biasCnt.get) := io.biasIn.get.payload
      when(biasCnt.get === (cfg.totalN - 1).U) {
        biasLoaded.get := true.B
        biasCnt.get := 0.U
      }.otherwise {
        biasCnt.get := biasCnt.get + 1.U
      }
    }

    // 【P0 epilogue-2】rqM 收集：字流落【staging 暂存】而非 rqMRegs（16 字齐 →
    //   rqMLoaded 置位 = staging 收齐，sLOAD_A 供数门控待收齐放行；rqMRegs 的
    //   更新只在提交点整组进行，见 rqMStaging 声明处不变式）。requantOn=0 时
    //   ready 恒 0、零行为扰动。
    io.rqMIn.get.ready := io.regFile.requantOn && !rqMLoaded.get
    when(io.rqMIn.get.fire) {
      rqMStaging.get(rqMCnt.get) := io.rqMIn.get.payload
      when(rqMCnt.get === (cfg.totalN - 1).U) {
        rqMLoaded.get := true.B
        rqMCnt.get := 0.U
      }.otherwise {
        rqMCnt.get := rqMCnt.get + 1.U
      }
    }
    io.rqMOut.get := rqMRegs.get
  }


  //C push 直接push
  io.cPush.valid := io.hiveValidOut
  io.cPush.payload := Cat(io.hiveCOut.reverse)
  assert(!(io.cPush.valid & !io.cPush.ready), "HiveCoreExecutor: cPush valid but C buffer not ready (pop-before-push invariant violated, partial sum lost)")

  
  // ==========================================================================
  // 默认输出驱动
  // ==========================================================================
  io.busy := state =/= sIDLE
  io.done := false.B

  io.aPop.ready  := false.B
  io.bPop.ready  := false.B
  io.cPop.ready  := false.B
  if(cfg.hasMx){
    io.scaleAPop.get.ready := false.B
    io.scaleBPop.get.ready := false.B
    io.biasFetchReq.get := false.B
    io.biasFetchNt.get := 0.U
    io.rqMFetchReq.get := false.B
    io.rqMFetchNt.get := 0.U
  }

  io.flushA := false.B
  io.flushB := false.B
  io.flushC := false.B

  io.hiveLoadH   := false.B
  io.hiveLoadV   := false.B
  io.hiveLoadVLock   := false.B
  io.hiveLoadW       := false.B
  io.hiveLoadWLock   := false.B
  io.hiveValidIn := false.B
  io.hiveClear   := false.B

  io.progress := doneTiles


  for (i <- 0 until cfg.totalN) {
        io.hivePsumIn(i) := 0.U
  }


  // ==========================================================================
  // FSM 主体 (Weight-Stationary: N→K→M)
  // ==========================================================================
  switch(state) {


    // --- sIDLE: 等待 execute 脉冲 ---
    is(sIDLE) {

      curNTile := 0.U
      curKTile := 0.U
      curM := 0.U
      doneTiles := 0.U
      io.hiveClear := true.B
      hasNext := false.B
      drainNextCnt := 0.U
        // 计算 tile 数。注意：mTiles/kTiles 为 Reg，本拍 := 下一拍才生效，
        // 故 depthOfCBufferUsed 必须用本拍推导的 newMTiles/newKTiles 计算，
        // 否则会采样到未初始化寄存器的随机旧值，误触发容量错误回 idle
        val newNTiles = (io.regFile.n(15, 0) + totalN.U - 1.U) / totalN.U
        // 【Phase 3 INT 生态同步】kTiles 分母判据由 isMx 泛化为「打包格式」= is1B || isHalfB
        //   （含 INT8/INT4 与全部 MX）。半字节族（INT4/MXE2M1）kPerBeat=totalN×4 → K/(totalN×4)；
        //   单字节族（INT8/MXE4M3/MXE5M2）kPerBeat=totalN×2 → K/(totalN×2)；非打包格式走 ceil(K/totalN)。
        //   与 HiveCoreCalcConfig.kTile 及金锚 HiveDebugModel.aKTiles/bKTiles 同源，不再由 hasMx4 门控。
        val isPackedA = DataFormat.is1B(io.regFile.aFmt) || DataFormat.isHalfB(io.regFile.aFmt)
        val newKTiles = Mux(isPackedA,
          Mux(DataFormat.isHalfB(io.regFile.aFmt),
              io.regFile.k(15, 0) / (totalN.U * 4.U),   // INT4 / MXE2M1: K/(totalN*4)
              io.regFile.k(15, 0) / (totalN.U * 2.U)),  // INT8 / MX8:    K/(totalN*2)
          (io.regFile.k(15, 0) + totalN.U - 1.U) / totalN.U)
        nTiles   := newNTiles - 1.U
        kTiles   := newKTiles - 1.U
        // 容量检查：C buffer 真实驻留量只有一组 partial sum（mTiles×totalN 行）。
        // K > totalN 时非首 pass 的 pop（回灌）与 push 等量交替，占用恒定，
        // 故 kTiles 不乘进公式（旧公式 mTiles×kTiles×totalN 会在 K 稍大时误报容量错误）
        depthOfCBufferUsed := io.regFile.m
        tileInterCnt := totalN.U - 1.U

      when(io.execute) {
        // execute 起始拍清空 A/B buffer（RdOnly DMA 首个 push 至少晚一拍，安全）；
        // flushA 仅允许在此拍拉高：aDma 内建 nTile 轮次连续供数，任何
        // per-tile flush 都会丢弃已预取数据且 RdOnly 不重发 → 死锁。
        // A 侧全局排空不变量仿真检查（仅仿真可见）：上次 execute 中 aDma
        // push 总行数 = nTiles×kTiles×m = executor 消费总行数，新 execute
        // 拍 FIFO 必已排空；非 0 说明供/消行数失配（padding 混入/丢数）
        assert(io.aOccupancy === 0.U, "HiveCoreExecutor: A buffer not drained at execute pulse (previous run aDma push rows != executor consume rows)")
        io.flushA := true.B
        io.flushB := true.B
        io.flushC := true.B
        // 【P0-1/P0-2 gather 门控】gather 读使非对齐逻辑拍变为 2 物理读/拍，
        // 供数速率可低至 0.5 字/拍；而 loadW 预取窗口要求窗口内 bPop 严格逐拍
        // （loadW 脉冲链/scaleB 移位链按拍位移，供数断拍破坏权重对角线与 scaleB
        // 空间对齐）。故【所有运行】不直入 sLOAD_B，统一经 sDRAIN 门控：等
        // B FIFO 占用 ≥ totalN（MX 运行另加 scaleB FIFO ≥ totalN）再进窗口。
        // 对齐快路径下 DMA 供数不慢于消费，门控仅在启动期短暂等待，行为不变。
        hasNext  := true.B
        state    := sDRAIN
      }

      // 水平 loadW 模式位锁存：独立 when（不改动上方原 execute 块）。
      // regFile 在计算期间保持不变，execute 拍采样即为本次运行模式
      when(io.execute) {
        loadWModeReg := io.regFile.loadWMode
      }

      // 【P0 epilogue-1】bias 首拍取数触发（独立 when，同 loadWModeReg 范式）：
      //   请求 nt=0 的 16 字 bias；下一 N-tile 的重取见 sNEXT_NK_TILE。
      //   biasLoaded/biasCnt 同拍清零，sLOAD_A 供数门控待收齐放行
      if (cfg.hasMx) {
        when(io.execute && io.regFile.biasOn) {
          io.biasFetchReq.get := true.B
          io.biasFetchNt.get := 0.U
          biasLoaded.get := false.B
          biasCnt.get := 0.U
        }
        // 【P0 epilogue-2】rqM 首拍取数触发：与 bias 同拍发（顶层两相取数器
        //   先 BIAS 相后 M 相连续供数，无双 drain）；requantOn=0 时零行为扰动。
        //   首块无 doneBlock 前驱 ⟹ execute 拍同步布防提交（staging 收齐即提交，
        //   由不变式②早于首行过 cvt；否则 rqMRegs 残留上一 job 末 tile 的 M）
        when(io.execute && io.regFile.requantOn) {
          io.rqMFetchReq.get := true.B
          io.rqMFetchNt.get := 0.U
          rqMLoaded.get := false.B
          rqMCnt.get := 0.U
          rqMCommitArmed.get := true.B
        }
      }

    }


    // --- sLOAD_WEIGHT_PE: 从 B buffer pop 权重 → 加载到 HiveComb ---
    // loadH 在整个加载窗口保持拉高（fmt/rnd 配置需时间经阵列横向传播）；
    // loadV 跟随 bPop 数据有效性：B buffer 暂无数据时 loadV 自然暂停，
    // RdOnly DMA 喂数到位后自动恢复，无需固定每 2 拍一次的节流。
    is(sLOAD_B) {
      io.hiveLoadH := true.B               // 配置加载，整个窗口拉高
      io.hiveLoadV := io.bPop.fire

      if(cfg.hasMx){
        // W1：握手门改读单一运行期判据 mxRunB（与写入门/前递门/MAC 消费门同源），
        //   消除判据分裂。MX 运行时 scaleBPop 与 bPop 必须同拍 lockstep fire，
        //   两侧 ready 均取 (bPop.valid & scaleBPop.valid)；非 MX 运行 scaleBPop
        //   恒不 ready、bPop 自由 pop（scaleBRegs 不写、不参与计算）。
        when(mxRunB){
          io.scaleBPop.get.ready := io.bPop.valid & io.scaleBPop.get.valid
          io.bPop.ready          := io.bPop.valid & io.scaleBPop.get.valid
        }.otherwise{
          io.scaleBPop.get.ready := false.B
          io.bPop.ready          := true.B
        }
        // W7 lockstep invariant guard (elaborated only when hasMx, evaluated at
        //   runtime only when mxRunB, so non-MX runs can never trip it).
        //   "bPop and scaleBPop fire on the same cycle" is the shared premise of
        //   the whole scaleB path: hiveScaleBIn is a pure combinational read of
        //   scaleBPop.payload, so the beat index carried by the weight FIFO and
        //   the beat index carried by the scale FIFO must advance together.
        //   Before #146 this held only as an accidental identity of the two ready
        //   expressions, with no assertion. If anyone later adds a skid buffer /
        //   pipeline stage to one side, or decouples the two ready terms, the
        //   per-lane scaleB chain silently misaligns against the weight diagonal
        //   at the loadWLock beat (as hard to spot as the single-byte broadcast
        //   bug fixed in #145).
        //   (#146) These messages no longer mention scaleBRegs: that per-column
        //   register file has been deleted; scaleB is consumed directly off the
        //   FIFO payload through the per-lane chain.
        when(mxRunB) {
          // (1) fire XOR guard: both sides must fire on the same cycle
          assert(!(io.bPop.fire ^ io.scaleBPop.get.fire),
            "HiveCoreExecutor: MX scaleB lockstep violated - bPop.fire XOR scaleBPop.fire (must fire same cycle, else the per-lane scaleB chain misaligns against the weight diagonal at loadWLock)")
          // (2) lock-beat scaleB valid guard: hiveLoadWLock fires combinationally
          //     on the last beat and every lane of hiveScaleBIn reads
          //     scaleBPop.payload on that very cycle, so the payload must be
          //     valid or all scaleBReg latches capture stale FIFO content.
          //     Structurally implied by the ready term above (fire => both sides
          //     valid); kept as a defence line in case that term is ever relaxed.
          when(loadWModeReg && io.bPop.fire && tileInterCnt === 0.U) {
            assert(io.scaleBPop.get.valid,
              "HiveCoreExecutor: MX lock beat scaleBPop not valid - scaleBReg would latch stale FIFO payload on loadWLock")
          }
        }
      }
      else{
        io.bPop.ready := true.B
      }
      

      for (i <- 0 until cfg.totalN) {
        io.hivePsumIn(i) := io.bPop.payload(cfg.bW * (i + 1) - 1, cfg.bW * i)
      }

      
      // 已喂权重行数（仅在 bPop 实际 fire 时递增，天然支持反压暂停）
      when(io.bPop.fire) {
        when(tileInterCnt =/= 0.U){
          tileInterCnt := tileInterCnt - 1.U
        }.otherwise{
          tileInterCnt := totalN.U - 1.U
          state := sLOAD_A
          io.hiveLoadVLock := true.B
        }
      }


      // --- 水平 loadW 模式覆盖块（last-connect；loadWModeReg=0 时整块死路，
      //     上方垂直路径逐字保留、行为 bit-exact 不变）：
      //     权重改经 hiveAIn 的 a 数据链水平加载（loadW 脉冲链移位 +
      //     末拍 loadWLock 广播锁存），psumIn 保持 0，loadV 信号组压制为 0。
      //     tileInterCnt/bPop.ready/loadH 计数与握手逻辑共用上方不改 ---
      when(loadWModeReg) {
        io.hiveLoadV     := false.B        // 压制垂直加载脉冲
        io.hiveLoadW     := io.bPop.fire   // 水平移位脉冲：停顿自然暂停
        io.hiveLoadVLock := false.B        // 压制垂直锁存
        for (i <- 0 until cfg.totalN) {
          // 权重经 a 口供数（bPop 的 totalN 个 bW 切片，aEffW > bW 时
          // 高位自动补零）；psumIn 恢复默认 0（权重不再经 psum 口下沉）
          io.hiveAIn(i)    := io.bPop.payload(cfg.bW * (i + 1) - 1, cfg.bW * i)
          io.hivePsumIn(i) := 0.U
        }
        // Last beat fire (tileInterCnt wrapped): emit loadWLock on this very
        // cycle (the unique moment of the diagonal landed state; it must
        // coincide combinationally with the fire cycle of the last beat
        // entering column 0).
        when(io.bPop.fire && tileInterCnt === 0.U) {
          io.hiveLoadWLock := true.B
          // (#146 D1(iii)) The former "W3 last-beat column-0 combinational
          // forward" `io.hiveScaleBIn.get(0) := io.scaleBPop.get.payload` has
          // been DELETED here. Unreachability / equivalence argument:
          //   (a) Its stated reason for existing is gone. The comment said
          //       "scaleBRegs(0) is written and only takes effect next cycle,
          //       while loadWLock fires combinationally this cycle, so column 0
          //       must forward the current scaleBPop.payload". That per-column
          //       scaleBRegs register file has been deleted by the user; scaleB
          //       is now a pure combinational read of scaleBPop.payload (see the
          //       "B scale feed" loop) that reaches HiveWorker.scaleBReg through
          //       the per-lane chain and is captured ON the loadWLock beat. There
          //       is no next-cycle write to forward around any more.
          //   (b) It was bit-exact redundant. For lane 0 the base expression
          //       gives idx = (0*step)/block = 0, hence
          //       (payload >> (0*8.U))(7,0) == payload(7,0). The deleted forward
          //       assigned the full 32-bit payload to an 8-bit sink, which
          //       Chisel truncates to payload(7,0) -- the very same 8 bits. The
          //       other 15 lanes were never touched by that last-connect. So
          //       deleting it changes no value on any lane, on any cycle.
          //   (c) It was also a latent silent-truncation hazard: a 32-to-8
          //       truncating last-connect sitting on lane 0 would silently
          //       override any future change to the lane-to-block mapping.
          // Net effect on RTL: one 8-bit 2:1 mux and its select disappear from
          // the hasMx elaboration; functionality is unchanged (zero diff in
          // behaviour, not necessarily in emitted netlist).
        }
      }

    }

    is(sLOAD_A){
      val needPartialSum = curKTile > 0.U

      //当需要paritalsum的时候必须占用c口buffer的读出口，因此需要等到CDMA已经将cbuffer搬空让出读口权限
      // 【P0 epilogue-1】biasOn 且未收齐时挂起供数（取数器盗用 dmaScaleA 端口需
      //   ≤17+drain 拍；sDRAIN+sLOAD_B 预取窗口通常已覆盖，本门控为兜底）：
      //   aPop/scaleA 均可反压，挂起安全；biasOn=0 时门控项恒真、逐位不变
      // 【P0 epilogue-2】requantOn 且 M 向量未收齐时同式挂起（两相取数在 bias 相
      //   之后还需 16 拍；requantOn=0 时门控项恒真、逐位不变）
      val feedBase = (cBufferReservedAtleastTotalN) & Mux(needPartialSum, cStoreGateReg === false.B, true.B) &
        (if (cfg.hasMx) !(io.regFile.biasOn && !biasLoaded.get) else true.B) &
        (if (cfg.hasMx) !(io.regFile.requantOn && !rqMLoaded.get) else true.B)
      // MX：激活消费与 scaleA 严格 lockstep。scaleADma 可能比 aDma 晚一拍抵达，
      //   若在 scaleA FIFO 尚空时就消费首个激活行，会使全体 scaleA 应用错位一行
      //   （row m 误用 row m-1 的 scale）。故运行期 isMx(aFmt) 时把 scaleAPop.valid
      //   并入供数门控，保证每个激活行 fire 当拍其对应 scale 已就位。
      //   【MX 必选】hasMx 编译期分支仅保留 Option 端口存在性；谓词改运行期：
      //   isMx=false 运行时（非 MX 数据）门控项恒真，feed 逐拍等于 feedBase，
      //   与无 MX 硬件时行为一致（非 MX 用例不驱动 dmaScaleA，FIFO 恒空也不死锁）。
      val feed = Wire(Bool())
      if(cfg.hasMx){
        when(DataFormat.isMx(io.regFile.aFmt)){
          feed := feedBase & io.scaleAPop.get.valid & io.aPop.valid
          io.aPop.ready := feed 
          io.scaleAPop.get.ready := feed 
        }.otherwise{
          feed := feedBase 
          io.aPop.ready := feed 
          io.scaleAPop.get.ready := false.B
        }
      }
      else{
        feed := feedBase 
      }
      

      io.cPop.ready := feed & needPartialSum

      // 跟随 fire（供数暂停时 valid 拉低，避免阵列对保持的旧 a 值重复累加）
      io.hiveValidIn := io.aPop.fire 
      
      

      for(i <- 0 until cfg.totalN){
        when(needPartialSum){
          io.hivePsumIn(i) := io.cPop.payload(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
        }
      }

      // 【P0 epilogue-1】pass-0 基底注入：bias 取代零基底经既有 psum 逐列链下发
      //   （与 needPartialSum 回灌支路互斥：pass-0 注 bias、后续 pass 回灌 partial sum，
      //   bias 已烧进 pass-0 结果）；biasOn=0 时本 when 恒假、hivePsumIn 保持默认 0
      if (cfg.hasMx) {
        when(!needPartialSum && io.regFile.biasOn) {
          for (i <- 0 until cfg.totalN) {
            io.hivePsumIn(i) := biasLazy(i)
          }
        }
      }

      when(io.aPop.fire){
        curM := curM + 1.U
      }

      // 排空窗口结束（beatCnt 达 curM + 2*(totalN-1)）才离开计算状态，
      // 保证波前完整流过阵列、最后一行累加不被截断
      // 【C2 修复】离场必须与 aPop.fire 原子门控：旧版仅 `when(curM === m-1)` 在
      // m=1 时 curM(=0)===0 当拍即进入离场，未消费任何 A 数据的确定性死锁；
      // m>1 时每 tile 末行未等待 fire 即跳离，少供一行。
      when(io.aPop.fire && curM === io.regFile.m - 1.U) {
        curM := 0.U
        state := sNEXT_NK_TILE
      }
    }

    is(sNEXT_NK_TILE){
      when(curKTile < kTiles){
        doneTiles := doneTiles + 1.U
        drainNextCnt := cfg.totalN.U - 1.U
        curKTile := curKTile + 1.U
        hasNext := true.B
        state := sDRAIN
      }.otherwise{
        // 【P3 修复：门吞没竞态】drain 与下一 tile 计算重叠流水：上一块 drain
        // 未完（cStoreGateReg 仍高）时若在此拉门，赋值无上升沿（无事件），
        // 随后上一块 doneBlock 落下共享寄存器 → 本块的门被永久吞掉，
        // 结果永不写回（K=16 实验实证：nt1 门被吞 → cDma 停块 busy 恒真 →
        // sDRAIN 死等死锁）。故停留本状态等上一块 doneBlock 落门（cPeekDone
        // 清 cStoreGateReg）后再升门推进；doneTiles/drainNextCnt 等副作用移入
        // 通过分支，防止停留拍重复计数。
        when(cStoreGateReg === false.B){
          doneTiles := doneTiles + 1.U
          drainNextCnt := cfg.totalN.U - 1.U
          curKTile := 0.U
          cStoreGateReg := true.B
          when(curNTile < nTiles){
            curNTile := curNTile + 1.U
            hasNext := true.B
            state := sDRAIN
            // 【P0 epilogue-1】下一 N-tile 的 bias 重取（同拍 curNTile+1，取数器
            //   以 nt=curNTile+1 的新 tile 序号计算地址）；k 推进分支不取——
            //   同一 N-tile 的 biasRegs 跨全部 K pass 与 M 行驻留复用
            if (cfg.hasMx) {
              when(io.regFile.biasOn) {
                io.biasFetchReq.get := true.B
                io.biasFetchNt.get := curNTile + 1.U
                biasLoaded.get := false.B
                biasCnt.get := 0.U
              }
              // 【P0 epilogue-2】下一 N-tile 的 rqM 重取（字流落 staging，rqMRegs
              //   在本块 doneBlock 提交点整组更新——见 rqMStaging 声明处不变式；
              //   requantOn=0 时零行为扰动）
              when(io.regFile.requantOn) {
                io.rqMFetchReq.get := true.B
                io.rqMFetchNt.get := curNTile + 1.U
                rqMLoaded.get := false.B
                rqMCnt.get := 0.U
              }
            }
          }.otherwise{
            curNTile := 0.U
            state := sDRAIN
          }
        }
      }
    }
    

    // --- sDRAIN: 等待剩余输出收集完毕 ---
    is(sDRAIN) {
  
      when(hasNext){
        when(drainNextCnt =/= 0.U){
          drainNextCnt := drainNextCnt - 1.U
        }.elsewhen(io.bOccupancy >= cfg.totalN.U && (!mxRunB || io.scaleBOcc.get >= cfg.totalN.U)){
          // 【P0-1/P0-2 gather 门控】进入 sLOAD_B 预取窗口的充要预蓄条件：
          //   ① B FIFO ≥ totalN：窗口内 bPop 逐拍消费 16 拍，最坏供数 2 拍/字
          //     （全非对齐），缺口 ≤ 8 字，totalN=16 字预蓄保证窗口内永不落空；
          //   ② MX 运行另需 scaleB FIFO ≥ totalN（scaleB 与 bPop lockstep 同拍消费，
          //     同样逐拍不可断）。满足后窗口内 pop 严格逐拍 ⟹ loadW 脉冲链与
          //     scaleB 移位链的空间对齐不被破坏。
          drainNextCnt := totalN.U - 1.U
          // 水平 loadW 模式 drain 重载（与 sNEXT_NK_TILE 初值一致；该值
          // 会被下一 sNEXT_NK_TILE 重新赋值，此处保持一致性）
          when(loadWModeReg) {
            drainNextCnt := (2 * (totalN - 1) + 1).U
          }
          hasNext := false.B
          state := sLOAD_B
        }
      }.otherwise{
  
      val anyNewValid = io.hiveValidOut.asUInt.orR
      // 超时退出: 连续 2*totalN 周期无新 valid → 强制退出
      when(!anyNewValid & !io.dmaCBusy) {
        io.done := true.B
        state := sIDLE
      }
      }

    }

  }

  // ==========================================================================
  // 【perf 观测探针】（纯组合、无状态）：bWindowReady 与 sDRAIN→sLOAD_B 转移条件
  //   同源。stallB 的 sDRAIN 项仅计 drain 计数归零后的纯等待拍（drainNextCnt
  //   倒数拍为固定流水排空，不计）；loadW 窗口内 bPop 停拍在 P0-1/P0-2 预蓄
  //   门控下稳态应为 0，非零即预取不变量被破坏或 B 口供数持续低于 0.5 字/拍。
  // ==========================================================================
  val bWindowReady = io.bOccupancy >= cfg.totalN.U &&
    (if (cfg.hasMx) (!mxRunB || io.scaleBOcc.get >= cfg.totalN.U) else true.B)
  io.perf.stallA := state === sLOAD_A && !io.aPop.fire
  io.perf.stallB := (state === sLOAD_B && !io.bPop.fire) ||
    (state === sDRAIN && hasNext && drainNextCnt === 0.U && !bWindowReady)


}
