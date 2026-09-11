/** HiveCore — 脉动阵列加速器顶层集成模块（寄存器配置 + 一键执行架构）
  *
  * 外部接口极简，仅 3 种命令:
  *   - REG_WRITE: 写入 2 个 32-bit 寄存器
  *   - EXECUTE: 触发矩阵计算
  *   - REG_READ: 读取 1 个寄存器
  *
  * 内部组件:
  *   - Register File: 12 个 32-bit 配置/状态寄存器（映射见下方寄存器组注释）
  *   - Executor: 自动 Tiling FSM
  *   - aDma (RdOnly): execute 单次启动自主扫描 A 矩阵 → A buffer（内建 nTile 轮次）
  *   - cDma (WrOnly): C buffer → 外部存储（storeGate 门控，末 K pass 开门）
  *   - bDma (RdOnly): 自主扫描整个 B 矩阵 → 独立 B buffer（权重专用）
  *   - Scratchpad: A buffer + B buffer + C buffer
  *   - HiveComb: 脉动阵列组合逻辑
  */

import chisel3._
import chisel3.util._

class HiveCore(cfg: HiveCoreConfig) extends Module {

  val io = IO(new Bundle {
    val cmd  = slave(Stream(new HiveCoreCmd))
    val resp = master(Stream(new HiveCoreResp))
    val respForTracing = master(Flow(new HiveCoreRespForTracing))
    // ── 对外契约（破坏性变更声明，相对旧版统一 extDataWidth 接口）──
    // 三通道 dmaExt 数据位宽不再由统一 extDataWidth 配置项决定，改为各通道独立取自身
    // 总线宽度参数：dmaA = aExtW、dmaB = bExtW、dmaC = cDmaExtW（【#136-KW4 订正】
    // 原文写「dmaC = cExtW」是伪权威：cExtW = totalN*cEffW 是 C buffer 的【行宽】
    // （debug 配置 = 16*40 = 640），而 6 行后的端口声明实际传的是 cfg.cDmaExtW
    // （默认 256，见 HiveCoreConfig 的 cDmaExtW require 族：2 的幂 ∧ ∈ [totalN*8,
    // totalN*32] ∧ 整除 totalN*32）。两者在默认配置下 640 != 256，原文与代码直接矛盾。）
    //
    // 【拍粒度：读写两侧不对称，勿一概而论】
    //   · A/B 读通道：每个 beat 搬运【一整行】，模块内部不做任何拆包/拼包
    //     （无 beat 内字段切分、无多 beat 组行）。外部存储侧必须按 aExtW / bExtW 供数，
    //     否则数据对齐错乱。
    //   · C 写通道：【不成立】。一行 C 结果（totalN 个元素、每元素 elemBits 位）由
    //     HiveCoreDmaWrOnly 在运行期按总线位宽拆成
    //       beatsPerRowEff = ceil(totalN × elemBits / cDmaExtW)
    //     个 beat 逐拍发出（拍内数据 = 锁存行右移 beatCnt*cDmaExtW 后取低 cDmaExtW 位，
    //     见 HiveCoreDmaWrOnly 的 `req.payload.data := (io.bufPop.payload >> ...)`；
    //     拍内地址 = curAddr + beatCnt × calcConfig.cBeatAddrStep）。elemBits 由 io.outFmt
    //     运行期解码，故拍数是【运行期量】而非编译期常量；编译期只有上界
    //     cfg.cBeatsPerRowMax = totalN*32/cDmaExtW。
    //     默认配置实例：FP32 输出（elemBits=32）→ ceil(16*32/256) = 2 拍/行；
    //     FP16/MXE4M3（elemBits=16/8）→ 1 拍/行。外部存储侧必须按 cDmaExtW 收数并
    //     接受「一行 = 多拍」。
    //   ★ 危害具体化（#134 影响面评审）：若照原文把 C 拍宽当成 cExtW/8 = 80 字节，
    //     测试侧的 C 区边界守卫（HiveCoreDebugCase chkCRegion 的 beatBytes = cDmaExtW/8）
    //     会对 nTiles=3 永久护栏用例立刻假红；正确拍宽是 cDmaExtW/8 = 32 字节。
    // A 矩阵只读 DMA 外部通道（cmd: 读地址流; rsp: 读数据流，位宽 = aExtW）
    val dmaA = HiveCoreDMAExtReadOnlyIF(cfg, cfg.aExtW)
    // C 结果写回 DMA 外部通道（req/grant + writeData 逐 beat 流，位宽 = cDmaExtW）。
    // 【P0 契约】cDmaExtW 默认改 256（只允许 2 的 N 次幂，废弃旧 0 哨兵 → cExtW=640
    // 满宽一拍一行与 5B/元素布局，废弃动作在 P2 落地）；窄总线行内拆多拍、密集打包由
    // P2 WrOnly 重构实现，本阶段只立契约（仿真套件可能失败，属中间态预期）。
    // 握手语义：WrOnly DMA 为逐 beat req/grant，len 恒 1（旧 DmaEngine 的
    // 单 burst len=M 协议已废弃），外部集成方需逐拍 grant + writeData.ready
    val dmaC = new HiveCoreDMAExtWriteOnlyIF(cfg, cfg.cDmaExtW)  // P2-清理: 端口位宽直取新契约参数
    // B 权重专用只读 DMA 外部通道（cmd: 读地址流; rsp: 读数据流，位宽 = bExtW）
    val dmaB = HiveCoreDMAExtReadOnlyIF(cfg, cfg.bExtW)
    // MX scale 只读 DMA 外部通道（仅 hasMx 生成，非 MX 配置不暴露，避免破坏旧 harness）：
    //   dmaScaleA = scaleA（8-bit/行），dmaScaleB = scaleB（scaleRowW = totalN*8/行）
    val dmaScaleA = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, 8))             else None
    val dmaScaleB = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, 8)) else None
    // P3b C 侧量化 scale 写回 DMA 外部通道（仅 hasMx 生成，非 MX 配置不暴露）：
    //   dmaCScale = 量化 C 输出的 E8M0 块 scale（cScaleRowW = totalN/8 字节/拍，
    //   有效字节 = ceil(n/blk)，高位零填充），基址 = regFile.cScaleAddr（regs(11)）
    // 【裁定2 修复：还原 dmaCScale 端口位宽 = cfg.cScaleRowW（refactor-loss，同 IssueA/B5 类）】
    //   git 取证 672fb37^ 原文为 HiveCoreDMAExtWriteOnlyIF(cfg, cfg.cScaleRowW)（=16-bit，
    //   totalN=16 → 每行 2 个 8-元素组各 1 字节 E8M0）；672fb37 重构时被误改为硬编码 8。
    //   内层 cDmaScale.io.dmaExtWrIF 行宽恒为 rowW=cScaleRowW=16（见 HiveCoreDmaScaleWr），
    //   io.dmaCScale <> cDmaScale.io.dmaExtWrIF 在 8-bit 端口下把 cvt.scaleWire 的高字节
    //   （第 2 个 8-元素组 scale）截断丢弃 → blk16 量化写回每行仅落低字节（实测
    //   dualMX K=32 CSCALE beat#0 hw=0x7A vs 金锚 0x7A7A，数据码通道 bit-exact 全绿，
    //   仅 scale 高字节丢失）。上方注释「cScaleRowW = totalN/8 字节/拍」与硬编码 8 自相
    //   矛盾，佐证为误改。修复即还原 cScaleRowW，使端口位宽与内层写回行宽一致。
    val dmaCScale = if (cfg.hasMx) Some(HiveCoreDMAExtWriteOnlyIF(cfg, cfg.cScaleRowW)) else None

    val status = Output(new HiveCoreStatus(cfg))
  })

  // ==========================================================================
  // 寄存器组定义（映射与 HiveInterface.scala HiveCoreRegister 一致，共 12 个）
  // ==========================================================================
  // 地址  名称
  // 0x00  REG_M         M 维度
  // 0x01  REG_N         N 维度
  // 0x02  REG_K         K 维度
  // 0x03  REG_A_ADDR    A 矩阵基地址
  // 0x04  REG_B_ADDR    B 矩阵基地址
  // 0x05  REG_C_ADDR    C 矩阵基地址
  // 0x06  REG_A/B/C_STRIDE  A/B/C 行步长（[3:0]/[7:4]/[11:8]，各 4bit）
  // 0x07  REG_CONTROL   [0]=clear_done (写1清除), [1:0]=fmt, [4:2]=rnd,
  //                     [5]=loadWMode (0=垂直加载, 1=水平 loadW 加载),
  //                     (16)+(13,11)=cFmt (C 写回 dmaC 输出格式，P0 扩 4-bit：
  //                     Cat(bit16,bit[13:11])，0xxx 低 3 位逐字不变（0000=fp32 默认/0001=fp16/
  //                     0010=bf16/0011=reserved 回落/0100・0101・0110=mx-E4M3/E5M2、mxfp4-E2M1 量化/
  //                     0111=reserved 降级），1000/1001/1010=int32/int16/int8，1011..1111 reserved),
  //                     (15,14)=qBlockSize (P3a 量化块大小：00=8/01=16/10=32/11=64 元素/块)
  // 0x08  REG_STATUS    [0]=busy, [1]=done, [2]=err, [31:16]=progress (只读)
  // 0x09  REG_SCALE_A_ADDR  scaleA 基地址
  // 0x0A  REG_SCALE_B_ADDR  scaleB 基地址
  // 0x0B  REG_SCALE_C_ADDR  P3a C 侧量化 scale 行写回基地址（P3b dmaCScale 通道使用）

  //val regFile = RegInit(VecInit(Seq.fill(12)(0.U(32.W))))
  val regFile = RegInit(0.U.asTypeOf(HiveCoreRegister(cfg)))

  // 状态寄存器辅助
  val doneFlag = RegInit(false.B)
  val errFlag  = RegInit(false.B)

  // ==========================================================================
  // 子模块例化
  // ==========================================================================
  val scratchpad = Module(new HiveCoreScratchpad(cfg))
  // A 矩阵只读自主 DMA：bufWidth = 一整行 A（totalN*aEffW），深度 = aBufferDepth
  val aDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.aEffW, bufDepth = cfg.aBufferDepth))
  // C 结果写回自主 DMA（P2 两段式：cvt = 转换+行寄存器，cDma = 多拍发射器）：
  // bufPop 消费顶层 cvt.out 转换后密集行（宽 totalN×32，行粒度 fire 即锁存）；
  // 总线侧位宽 = cfg.cDmaExtW（P0 契约，默认 256），行内多拍拆分由 WrOnly 运行期落地；
  // bufWidth/hasFp/hasMx 为 P2 签名保留参数（语义已死：规格化/量化硬件随 cvt 上移顶层）。
  val cDma       = Module(new HiveCoreDmaWrOnly(cfg, bufWidth = cfg.totalN * cfg.cEffW, hasFp = cfg.hasFp, hasMx = cfg.hasMx))
  // B 权重专用只读 DMA：bufWidth = 一整行权重（totalN*bW），深度 = bBufferDepth
  val bDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.bW, bufDepth = cfg.bBufferDepth))
  val executor   = Module(new HiveCoreExecutor2(cfg))
  // P2 两段式（裁定 4）：精度转换+行寄存器上移顶层（全设计唯一行寄存器在 cvt 内，
  // 原 WrOnly 内嵌例化已删）：in ← cPop mux（storeGate 窗口）、out → cDma.bufPop、
  // scaleOut → 顶层深 2 cScaleFifo（仅 hasMx）。
  val cvt        = Module(new HiveOutCvt(cfg))
  // 【任务90 收敛】HiveComb 传 cfg.cEffW（而非原始 cfg.cW）：cW=0 自动推导时
  // HiveComb 内部仅 max(32, autoAccW)（=36）会撞其「含浮点 >= 40」require；
  // 顶层 cfg.cEffW 已含 >=40 分支，两者口径对齐。显式 cW 配置下 cEffW=max(cW,bW)，
  // 行为不变（HiveComb 内部同式）。HiveComb 本体禁动，仅在例化边界对齐口径。
  val hiveComb   = Module(new HiveComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cEffW, cfg.supportedFmts))
  val aDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaRdOnly(cfg, bufWidth = 8,   bufDepth = cfg.scaleBufferDepth))) else None
  val bDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaRdOnly(cfg, bufWidth = 8,   bufDepth = cfg.scaleBufferDepth))) else None

  // P3b C 侧量化 scale 写回 DMA（新端口集：start/done/busy/err + dmaCScale 写通道）：
  // 仅 hasMx 例化；数据源 = 顶层深 2 cScaleFifo（P2 裁定 5 上移，唯一 scale 缓冲，
  // cvt.scaleOut 供数）。start 门控 = executePulse & cQuantRun（cQuantRun = 低 3 位∈{4,5,6}
  // 且 bit16=0 枚举，与 cvt 量化支路同源）。
  val cDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaScaleWr(cfg))) else None
  val calcConfig = Module(new HiveCoreCalcConfig(cfg))
  
  // ==========================================================================
  // calcConfig 生产者（喂给 bDma）：从 regFile 推导 tile 数与六个地址偏移。
  // 采用组合逻辑：bDma 在 start（EXECUTE 脉冲）当拍采样，regFile 在计算期间
  // 保持不变，因此组合驱动即可保证采样到 execute 时刻的正确值
  // （若用 Reg 锁存，start 当拍会采样到锁存前的旧值）。
  // 偏移语义（字节地址）的实际消费者是三个 DMA 模块：HiveCoreDmaRdOnly（A/B 通道，
  // aRow/aCol/bRow/bCol）与 HiveCoreDmaWrOnly（C 通道，cRow/cCol）。
  // 【#133 死引用清理 / #136-RW1 去裸 token】此处原写「偏移语义与 Executor 侧的三个
  // tile 首址别名（A tile 首址 / B tile 首址 / C 写回地址）保持一致」，但那三个别名在
  // main 源码中【无任何定义】（全仓仅注释命中），Executor 也不消费任何地址偏移（它只发
  // peekBlock/doneBlock 块协议与 start 脉冲），故删除该伪约定引用。
  // 【#136-RW1 采纳选项 (b)】留痕文本改用【描述性表述】而不复述原名裸 token：这样未来
  // 基于 grep 的死引用审计在 src/hive/scala/main/ 下 0 命中，无需读上下文去区分
  // 「活引用」与「删除留痕」，也不会让审计工具把留痕误报为残留缺陷。
  // --------------------------------------------------------------------------
  // 偏移语义速查（字节地址；下列每条均以 HiveCoreCalcConfig 的【实际赋值式】为准，
  // 本段只是索引，勿当独立权威。记 aEb/bEb/cEb = memElemBytes(aFmt/bFmt/cFmt)。）
  // 【#124 附带订正（只改注释、零功能影响）】本段原有三处失真，均已按下述实况改写：
  //   ✗旧「aRow = aStride」 ✗旧「cRow = cStride」 —— CalcConfig 全文【不消费任何 stride
  //     寄存器】（grep Stride 在该文件 0 命中），行步进一律由 K/N × 元素字节数推出；
  //   ✗旧「loadWMode=1: bCol = totalN*k*(bW/8)」 —— 那是更早已废弃的 Mux 分支式，
  //     现行 RTL 只输出【单一】bColTileAddressOffset（不随 loadWMode 分流），
  //     loadW=1 的轮间跳由 HiveCoreDmaRdOnly 的 lastBTileStartAddr 累加模型完成。
  //   aRow = k * aEb                            （A 行主序 M×K，行步进沿 K）
  //   aCol = totalN × elemsPerLane(aFmt) × aEb  （A 的 kTile 步进 = aKpb × aEb）
  //   bRow/bCol 双模式（regFile.loadWMode，regs(6)(28)）：
  //     bRow = (loadWMode ? k : n) * bEb        （loadW=0 沿 K 行、行长 N；
  //                                                loadW=1 B 转置 N×K、行长 K）
  //     bCol = totalN × elemsPerLane(bFmt) × bEb（【单一式、两支共用】：
  //            loadW=0 时语义是 n-tile 步进（非 MX ⇒ elemsPerLane=1 ⇒ = totalN×bEb）；
  //            loadW=1 时语义是 k-tile 步进（= bKpb×bEb）。两支持有的 elemsPerLane
  //            天然互斥，因 loadW=0 恒非 MX（errCode4 拦 MX+loadW=0）。）
  //   cRow = n * cEb（半字节档 cFmt 为 n>>1）   cCol = totalN * cEb（半字节档 totalN>>1）
  //   ★ aCol/bCol 是【运行期量】（#124 ① 起）：MX8/FP16/BF16/INT16 → 32，INT8 → 16，
  //     INT32 → 64，半字节族 → 0。672fb37 曾退化为 elaboration 期常数 totalN*(aW/8)
  //     = totalN*(bW/8) = 32（aW/bW 是编译期位宽、出厂 16，与运行期格式无关）。
  //   ★ scaleC 行步进 scaleCRowAddressOffset = CEIL(n / cMxBlk)（#124 ② 起；
  //     672fb37 曾退化为 FLOOR）。scaleC 的 n-tile 步进保持 FLOOR(totalN / cMxBlk)。
  //
  // 【MX v2 SIMD】mxRun = cfg.hasMx && isMx(regFile.aFmt)（与 scale DMA start 门控同判据）：
  //   16-bit lane 打包 2 个 8-bit 元素（1 字节/元素），行步长 = K 字节（非 K*(aW/8)），
  //   一个 kTile pass 覆盖 32 K 元素（kTile 数 = K/32）；非 MX 运行逐字等于原公式。
  // ==========================================================================
  val startPulse = io.cmd.fire && io.cmd.payload.op === HiveCoreOp.EXECUTE
  calcConfig.io.regFile := regFile
  calcConfig.io.startIn := startPulse


  //For tracing
  val tracingSt = RegInit(0.U(1.W))  
  val tracingPcReg = Reg(UInt(32.W))
  val tracingTagReg = Reg(UInt(8.W))
  io.respForTracing.valid := false.B
  io.respForTracing.payload.state := 0.U
  io.respForTracing.payload.pc := tracingPcReg
  io.respForTracing.payload.tag := tracingTagReg
  when(tracingSt === 0.U){
      when(startPulse){
        tracingSt := 1.U
        tracingPcReg := io.cmd.payload.pc
        tracingTagReg := io.cmd.payload.tag
        io.respForTracing.valid := true.B
        io.respForTracing.payload.state := 0.U
        io.respForTracing.payload.pc := io.cmd.payload.pc
        io.respForTracing.payload.tag := io.cmd.payload.tag
      }
  }.otherwise{
    io.respForTracing.valid := executor.io.done | calcConfig.io.errPluse
    when(executor.io.done){
      io.respForTracing.payload.state := 1.U
    }.elsewhen(calcConfig.io.errPluse){
      io.respForTracing.payload.state := 3.U
    }
  }
  

  // ==========================================================================
  // bDma 控制接线：execute 起始拍与 Executor 同步启动，自主扫完整个 B 矩阵
  // ==========================================================================
  bDma.io.isA         := false.B
  bDma.io.start       := calcConfig.io.startOut
  bDma.io.regFile     := regFile
  bDma.io.reg_baseAddr := regFile.bAddr
  bDma.io.reg_kTile := calcConfig.io.calcConfig.kTile
  bDma.io.reg_mTile := calcConfig.io.calcConfig.mTile
  bDma.io.reg_nTile := calcConfig.io.calcConfig.nTile
  bDma.io.reg_rowOffset := calcConfig.io.calcConfig.bRowAddressOffset
  bDma.io.reg_colTileOffset := calcConfig.io.calcConfig.bColTileAddressOffset

  
  val reg_baseAddr = Input(UInt(cfg.addrWidth.W))
    val reg_kTile = Input(UInt(cfg.mnkWidth.W))
    val reg_mTile = Input(UInt(cfg.mnkWidth.W))
    val reg_nTile = Input(UInt(cfg.mnkWidth.W))
    val reg_rowOffset = Input(UInt(cfg.addrWidth.W))
    val reg_colTileOffset = Input(UInt(cfg.addrWidth.W))
    
  
  
  // bDma.io.done/busy 目前无消费者（err 参与 dmaErr 汇聚），留空部分 FIRRTL 会优化掉
  // 外部只读通道直连顶层 dmaB
  io.dmaB <> bDma.io.dmaExtRdIF

  // ==========================================================================
  // aDma 控制接线：与 bDma 同为 execute 单次启动全自主供数——内建 nTile
  // 轮次（nTile 外 → kTile 中 → M 内），供满 nTiles 轮后自然停止；
  // regFile/calcConfig 复用上方组合 Wire
  // ==========================================================================
  aDma.io.isA         := true.B
  aDma.io.start       := calcConfig.io.startOut
  aDma.io.regFile     := regFile
  aDma.io.reg_baseAddr := regFile.aAddr
  aDma.io.reg_kTile := calcConfig.io.calcConfig.kTile
  aDma.io.reg_mTile := calcConfig.io.calcConfig.mTile
  aDma.io.reg_nTile := calcConfig.io.calcConfig.nTile
  aDma.io.reg_rowOffset := calcConfig.io.calcConfig.aRowAddressOffset
  aDma.io.reg_colTileOffset := calcConfig.io.calcConfig.aColTileAddressOffset

  io.dmaA <> aDma.io.dmaExtRdIF

  // ==========================================================================
  // cDma 控制接线：execute 起始拍启动后自主待命，storeGate 由 Executor 在
  // 末 K pass 的 store 窗口拉高，防止多 pass 中间 partial sum 被误搬
  // ==========================================================================
  cDma.io.start       := calcConfig.io.startOut
  cDma.io.peekBlock        := executor.io.cPeek
  cDma.io.regFile     := regFile
  cDma.io.calcConfig  := calcConfig.io.calcConfig
  io.dmaC <> cDma.io.dmaExtWrIF

  // ==========================================================================
  // 命令处理逻辑
  // ==========================================================================
  val respValid = RegInit(false.B)
  val respData  = RegInit(0.U(32.W))
  val respErr   = RegInit(false.B)

  io.cmd.ready := !respValid  // 上一个响应未被取走时反压
  io.resp.valid       := respValid
  io.resp.payload.data := respData
  io.resp.payload.err  := respErr

  when(io.resp.fire) {
    respValid := false.B
  }

  when(io.cmd.fire) {
    val cmd = io.cmd.payload
    switch(cmd.op) {
      is(HiveCoreOp.REG_WRITE1) {
        val addr0 = cmd.reg0Addr(3, 0) 
        // P4.3: 写门控覆盖 0x00-0x07 + 0x09/0x0A/0x0B（含 scale 地址），排斥 STATUS 0x08 只读。
        //   registerNumRW=12 使 <registerNumRW 区间含 0x08，故显式 =/= 8.U 排除。
        //   regs(8) 从不被读（0x08 读返回计算 statusReg），故该门控观测 bit-exact。
        when(cmd.reg0Addr < cfg.registerNumRW.U && cmd.reg0Addr =/= 0x08.U) { 
          regFile.wregs(addr0, cmd.data0)
        }
        // 【B1】移除 clear_done 残留：旧机制 `when(reg0Addr===0x07){when(data0(0)){doneFlag:=false}}`
        //   在新 reg7 位域下 bit0 = aFmt[0]，写奇数 aFmt（FP16=1/MXE4M3=3/MXE2M1=5…）会
        //   意外清除 doneFlag（确定性 bug）。doneFlag 现仅由 reset 与 executor.io.done 驱动，
        //   语义为「本次上电以来是否完成过一次执行」，与 STATUS.done 粘滞契约一致。
        // 生成响应
        respValid := true.B
        respData  := 0.U
        respErr   := false.B
      }
      is(HiveCoreOp.REG_WRITE2){
        val addr0 = cmd.reg0Addr(3, 0) 
        // P4.3: 同 REG_WRITE1，覆盖 0x09/0x0A/0x0B、排斥 STATUS 0x08（regs(8) 不被读，bit-exact）
        when(cmd.reg0Addr < cfg.registerNumRW.U && cmd.reg0Addr =/= 0x08.U) {
           regFile.wregs(addr0, cmd.data0)
        }
        val addr1 = cmd.reg1Addr(3, 0) 
        when(cmd.reg1Addr < cfg.registerNumRW.U && cmd.reg1Addr =/= 0x08.U) { 
          regFile.wregs(addr1, cmd.data1)
        }
        // 【B1】移除 clear_done 残留（REG_WRITE2 侧，同上：reg7 bit0 = aFmt[0] 冲突）
        // 生成响应
        respValid := true.B
        respData  := 0.U
        respErr   := false.B
      }
      is(HiveCoreOp.REG_READ) {
        // 读取寄存器（addr 截 4 位与写路径 addr0/addr1 同源；寄存器组 ≤16，
        // 动态索引语义与截断前一致，仅消除位宽超宽告警）
        val addr = cmd.reg0Addr(3, 0)
        val statusReg = Cat(executor.io.progress, 0.U(9.W), calcConfig.io.errCode, errFlag, doneFlag, executor.io.busy)
        val readVal = Mux(addr === 0x08.U, statusReg, regFile.rregs(addr))
        respValid := true.B
        respData  := readVal
        respErr   := false.B
      }
      is(HiveCoreOp.EXECUTE) {
        // 不需要立即回响应, 等完成后回
        respValid := true.B
        respData  := 0.U
        respErr   := false.B
      }
    }
  }

  // EXECUTE 完成时生成响应；err 汇聚 Executor 与三个自主 DMA 的错误标志。
  // Executor 异常路径（容量错/cPush 违约）经 sDONE 退出，io.done 照常脉冲，
  // 此处 respErr 反映真实 err，软件栈收到带 err 标记的 resp 而非永久等待。
  // P4.2: MX 配置追加 scale DMA（scaleADma/scaleBDma）err；非 MX 配置为
  //   else 分支，表达式逐字等于原式（无 scale DMA 生成），bit-exact。
  val dmaErr = if (cfg.hasMx) {
    aDma.io.err || bDma.io.err || cDma.io.err || aDmaScale.get.io.err || bDmaScale.get.io.err ||
      cDmaScale.get.io.err  // P3b 追加 dmaCScale 通道错误（rsp.err 锁存）
  } else {
    aDma.io.err || bDma.io.err || cDma.io.err
  }
  // Victor P1 修复：execute 脉冲拍清除 errFlag（executor 的 errState 同拍同步清除，
  // 见 HiveCoreExecutor sIDLE execute 块）。本清除置于下方两个置位块之前，
  // last-connect 序保证同拍 done 路径的 errFlag 置位仍可覆盖本清除。
  // 注意：execute 脉冲拍 executor.io.err 反映的是 errState「清除前」旧值（寄存器
  // 同拍清除次拍才生效），故下方 hasMx 直连置位用 !executePulse 排除本拍，
  // 否则合法再 EXECUTE 会被旧 err 立即回置、假 err 永久化。
  //
  // 【B2 裁定：errFlag 语义 = "EXECUTE 时锁存报错"，非"写入即报错"】
  //   问题：CalcConfig 拦截 startOut 时（配置违约）Executor 永不启动 → io.done 永不
  //   触发 → 原实现 errFlag 仅在 done 拍赋值，配置错误对软件完全不可见（STATUS.err 恒 0）。
  //   修复：新增 calcConfig.io.err → errFlag 直连置位路径，不依赖 executor.io.done。
  //   语义选择理由（"EXECUTE 时锁存" 而非 "写入即报错"）：
  //     - STATUS.err 的契约是「一次执行尝试的结果」，配置装配阶段（多次 REG_WRITE）
  //       不应污染 STATUS；CalcConfig 已在 sIDLE 无条件清 err（见 HiveCoreCalcConfig），
  //       故 calcConfig.io.err 是「本次 EXECUTE 进入 sC0 后的配置判决」而非「idle 期当前
  //       配置是否合法」——直连不会在 EXECUTE 前的装配期误置 errFlag。
  //   同拍优先级（显式处理与 startPulse 清除的竞争）：startPulse 清除 > 直连置位。
  //     直连置位用 `&& !startPulse` 门排除执行拍本身，保证合法再 EXECUTE 不被上一轮
  //     sticky err 在同拍立即回置（与上方 Victor P1 注释同一防假-err-永久化考量）。
  //   sticky：errFlag 置位后保持到下一次 startPulse 才清（直连路径与 done 路径均不自动清）。
  //   保留 executor.io.done 路径：捕获 DMA 运行期错误 dmaErr；且不破坏 MxSpec
  //     runMxOutFmtErrCase 所依赖的「executor done 拍置 errFlag」时序（该路径原样保留）。
  //   last-connect 序：startPulse 清 → 直连置（!startPulse 门）→ done 拍置（最终裁决，
  //     executor 实际完成时以 done 拍表达式为准；配置违约时 done 不触发，直连置生效）。
  when(startPulse) { errFlag := false.B }

  when(calcConfig.io.err && !startPulse) { errFlag := true.B }

  when(executor.io.done) {
    doneFlag  := true.B
    errFlag   := calcConfig.io.err || dmaErr
  }



  // ==========================================================================
  // Executor 连接寄存器组
  // ==========================================================================
  executor.io.execute := calcConfig.io.startOut
  executor.io.regFile := regFile

  // ==========================================================================
  // aDma ↔ Scratchpad (A buffer)：aDma RdOnly 独占写入，Executor 独占读出
  // ==========================================================================
  scratchpad.io.aPush.valid   := aDma.io.bufPush.valid
  scratchpad.io.aPush.payload := aDma.io.bufPush.payload
  aDma.io.bufPush.ready       := scratchpad.io.aPush.ready
  aDma.io.bufPopFire          := scratchpad.io.aPop.fire

  // ==========================================================================
  // C buffer push：仅 Executor 写结果（cDma 只做 store）
  // ==========================================================================
  scratchpad.io.cPush.valid   := executor.io.cPush.valid
  scratchpad.io.cPush.payload := executor.io.cPush.payload
  executor.io.cPush.ready     := scratchpad.io.cPush.ready

  // ==========================================================================
  // B buffer 数据通路（无 mux：bDma RdOnly 独占写入，Executor 独占读出）
  // ==========================================================================
  scratchpad.io.bPush.valid   := bDma.io.bufPush.valid
  scratchpad.io.bPush.payload := bDma.io.bufPush.payload
  bDma.io.bufPush.ready       := scratchpad.io.bPush.ready
  bDma.io.bufPopFire          := scratchpad.io.bPop.fire

  executor.io.bPop.valid   := scratchpad.io.bPop.valid
  executor.io.bPop.payload := scratchpad.io.bPop.payload
  scratchpad.io.bPop.ready := executor.io.bPop.ready
  executor.io.bOccupancy   := scratchpad.io.bOccupancy

  // ==========================================================================
  // C buffer pop mux: cvt(store 写回链) 或 Executor(partial sum 读取) 分时复用。
  // P2：mux 结构与 !executor.io.cPop.ready 优先门逐字保留，仅 ready 源从
  // cDma.bufPop.ready 换为 cvt.io.in.ready（两段式：cvt 先行接收转换）。
  // 【P3 修复：两段式超前预取防护（P3 定位，恢复 pop 行 = cDma 锁存行不变量）】
  // cvt 行寄存器与 cDma 行锁存解耦后，store 排空末期 cvt 可凭 in.ready 先行
  // fire 而 cDma 本拍无法锁存（行锁存被占）→ 从共享 C FIFO 超前多弹一行滞留，
  // 后续行整体错位。修复 = 把 cDma.io.bufPop.ready 折入消费门：pop 当且仅当
  // cDma 本拍能锁存。无组合环：两个 ready 均由寄存器态驱动（cvt.holdValid /
  // WrOnly lineValid），valid 方向不构成回路。
  //
  // 【P3 修复 2 已撤销（P3 回归实证留痕）】原方案曾在此加块行计数器闸断「末行
  // 锁存拍」消费（blkRowCnt/lastLineLatching），诊断假设为「块末单拍窗口超弹」；
  // 但二分实验证明该门闸断的恰是末行本身的合法消费拍（该拍 cPop 载荷即末行数据，
  // 并非第 m+1 行）→ cvt 与 cDma 消费节奏脱拍：量化运行时 scale 行与数据行失步、
  // 大块用例数据通道中途停摆（M=32 实测 C=35/64 死锁、CSCALE 行序错位，
  // MxSpec/QuantSpec 同形态复现）。撤销后 MxSpec multi-pass 单例即转绿。
  // 原「512 处数值错行」观察由上方 bufPop.ready 折入修复单独覆盖。
  //
  // ==========================================================================
  // 【#122 主修复：恢复块满守卫 blkLatchCnt/blkLatchFull「同源双门」（refactor-loss）】
  // --------------------------------------------------------------------------
  // 取证：`git show 9d74335:src/hive/scala/main/HiveCore.scala` L443-446（计数器+闸门）
  //   与 L462（in.valid 侧同源门）；`git log --all -S"blkLatch"` 只命中两个 commit：
  //   9d74335 引入、672fb37（DMA 拆分重构）整体删除 —— 而描述它的注释（下方
  //   「P3 修复（同源双门）」段）被原样留在代码里，形成「注释在、硬件不在」的失配。
  //
  // 根因机制：三方在 cDma.doneBlock 那一拍同时为高，而顶层已无任何块满守卫
  //   → C FIFO 多弹一行「下一 n-tile 的 kt0 partial row0」：
  //     ① cvt.io.in.ready 是组合的且含 io.out.fire → out.fire 同拍 in.ready 拉高（背靠背直通）；
  //     ② executor.io.cStoreGate 是寄存器，由 cPeekDone(=cDma.doneBlock) 次拍才拉低 → 该拍仍高；
  //     ③ cPop.valid 因多拍写回（beatsPerRow>1）使 store 排空慢于阵列生产 → 该拍 C FIFO 未排空。
  //   双重损伤：(a) 该行滞留 cvt 行寄存，下一写回窗口作为 nt=1 的 row0 发出
  //   （= 未经第二 pass 累加的裸部分和）；(b) 该行从 psum FIFO 缺失 → nt=1 的 kt1
  //   回灌整体移一行（hw[m] = kt0[m] + kt1[m-1]）。数值指纹 bit-exact 512/512。
  //   触发条件四条：kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1
  //   （nTiles>1 必要 —— C buffer 块粒度 == 一个 n-tile，块末即 nt 边界，超弹只损伤
  //     「下一块」，单块无下一块；beatsPerRow>1 是【时序窗口条件】而非结构条件 ——
  //     单拍写回时该拍 C FIFO 已空、③ 不成立，窗口不触发）。
  //
  // 【为何是 cvt.in.fire 口径而非 lineCounter 口径】口径必须是「按 cvt.io.in.fire 计数
  //   到 regFile.m」，即末行锁存【之后】才关门。lineCounter===m-1 在末行锁存【之前】
  //   即成立，据此关门闸掉的恰是末行本身的合法消费拍 → cvt 与 cDma 消费节奏脱拍，
  //   实测 M=32 死锁（store beats 停在 C=35/64，见上方「P3 修复 2 已撤销」段与
  //   HiveCoreDmaWrOnly 收行门控注释的同源说明）。改按 in.fire 计数则：cvt 行寄存器
  //   全设计唯一一份，第 m 次锁存当拍 holdValid 置真，下一次锁存必先等释放（≥1 拍后），
  //   届时计数已达 m、闸已关 → 既无同拍竞态、又不阻断末行。
  //
  // 【同源双门缺一不可】blkLatchFull 必须同时出现在 cPop.ready 与 cvt.in.valid 两处：
  //   只门 cPop.ready 会让 cvt.in.fire 在 FIFO 不 pop 的拍幻影锁存同一行（重复行，
  //   比原缺陷更糟）。这正是 9d74335 注释「同源双门」的含义。
  //
  // 【禁动区声明：不恢复 cDma.io.bufPop.ready 项】9d74335 原文 L447/L462 两处门内均含
  //   `cDma.io.bufPop.ready`，但该信号语义在同一次重构中已变：
  //     旧 = (state===sTRANSFER_M) & !lineValid & (blkRxCnt =/= m)
  //     新 = (state===sTRANSFER_M) & io.dmaExtWrIF.req.ready & lastBeat
  //   把新语义折入 cPop.ready 不是 loss 恢复而是发明新门控，须先经设计评审；且因果
  //   干预实验已证明不加它也充分（failed 12→4、mismatch 509→0、六通道地址对账仍 0、
  //   无死锁）。同理不恢复 WrOnly 的 blkRxCnt/blkRxFull（9d74335 的 S-Ken4 评审项已把
  //   它降级为内部统计输出、由 blkLatchFull 完全替代为闸门源），也不恢复 lineData/
  //   lineValid（行寄存职责已归 cvt，全设计唯一一份）。
  // ==========================================================================
  private val blkLatchCnt = RegInit(0.U(cfg.mnkWidth.W))
  when(cvt.io.in.fire) { blkLatchCnt := blkLatchCnt + 1.U }
  // 块末/重启归零（last-connect）。旧名 executePulse 已在 672fb37 重命名为 startPulse，
  // 表达式与 9d74335 L445 逐字相同（仅信号改名）。
  when(cDma.io.doneBlock || startPulse) { blkLatchCnt := 0.U }
  private val blkLatchFull = blkLatchCnt === regFile.m
  scratchpad.io.cPop.ready := Mux(executor.io.cStoreGate, cvt.io.in.ready && !blkLatchFull, executor.io.cPop.ready)

  executor.io.cPop.valid   := scratchpad.io.cPop.valid
  executor.io.cPop.payload := scratchpad.io.cPop.payload
  executor.io.cPeekDone := cDma.io.doneBlock

  // ==========================================================================
  // P2 转换链（裁定 4）：cPop → cvt.in（转换+行寄存器）→ cvt.out → cDma.bufPop。
  // cvt.io.in.valid 追加 cStoreGate 门（关键接线决策）：store 窗口外 cPop.ready 被
  // 路由给 Executor partial sum 读取，若无此门，cvt 会在 FIFO 不 pop 的拍自行
  // fire 锁存「幻影行」（旧架构该门隐含在 cDma 状态门控内，两段式后必须显式化）。
  // ==========================================================================
  // 【P3 修复（同源双门）】in.valid 与上方 cPop.ready 必须同含
  // cDma.io.bufPop.ready 与块满闸：否则 cvt.in.fire 与 FIFO pop 条件不一致，
  // 仍会幻影锁存/块末超弹。块满闸用 blkLatchFull（按锁存口径，见上方 P3 修复 3）。
  // 【#122 注】本段是 672fb37 删掉 blkLatch 硬件后【原样留下的注释】。现块满闸已按
  //   9d74335 L462 恢复（下方 && !blkLatchFull）；`cDma.io.bufPop.ready` 一项【刻意不
  //   恢复】—— 该信号语义在同一次重构中已变（旧 = !lineValid & blkRxCnt=/=m，
  //   新 = req.ready & lastBeat），折入属发明新门控而非 loss 恢复，须先经设计评审；
  //   因果干预已证明只恢复块满闸即充分。详见上方「#122 主修复」段的禁动区声明。
  cvt.io.in.valid := scratchpad.io.cPop.valid && executor.io.cStoreGate && !blkLatchFull
  cvt.io.in.bits  := scratchpad.io.cPop.payload
  cvt.io.outFmt   := regFile.cFmt
  cvt.io.rnd      := regFile.rnd
  cvt.io.blkSel   := regFile.cMxBlockSize
  // nEff = min(totalN, n − nCnt×totalN)：nCnt 由 cDma 新增输出口回供，顶层组合算；
  // 位宽截段防 := 宽度不匹配（nCnt 上界 = nTile−1，减法无下溢）。
  private val cNRemain = regFile.n - cDma.io.nCntOut * cfg.totalN.U
  private val nEffW = log2Up(cfg.totalN + 1)
  cvt.io.nEff := Mux(cNRemain >= cfg.totalN.U, cfg.totalN.U(nEffW - 1, 0), cNRemain(nEffW - 1, 0))

  // cvt.out → cDma.bufPop（行粒度，fire 即锁存进 WrOnly 行寄存器）；
  // out.ready 透传 WrOnly 末拍接受决定（行锁存空才收新行）。
  cDma.io.bufPop.valid   := cvt.io.out.valid
  cvt.io.out.ready       := cDma.io.bufPop.ready
  cDma.io.bufPop.payload := cvt.io.out.bits
  cDma.io.bufOccupancy   := scratchpad.io.cOccupancy

  // ==========================================================================
  // Executor ↔ Scratchpad (A buffer pop)
  // ==========================================================================
  executor.io.aPop.valid   := scratchpad.io.aPop.valid
  executor.io.aPop.payload := scratchpad.io.aPop.payload
  scratchpad.io.aPop.ready := executor.io.aPop.ready
  executor.io.aOccupancy   := scratchpad.io.aOccupancy
  executor.io.cOccupancy   := scratchpad.io.cOccupancy


  executor.io.dmaABusy := aDma.io.busy
  executor.io.dmaBBusy := bDma.io.busy
  // P3b：量化运行时 drain/done 需等 dmaCScale 写完——dmaCBusy OR 门
  //（仅 hasMx 分支；非 MX else 分支逐字不变）。sDRAIN 完成判据 !dmaCBusy
  // 自然等两通道均空闲。
  executor.io.dmaCBusy := (if (cfg.hasMx) cDma.io.busy || cDmaScale.get.io.busy else cDma.io.busy)

  // ==========================================================================
  // Executor ↔ HiveComb
  // ==========================================================================
  hiveComb.io.aIn      := executor.io.hiveAIn
  hiveComb.io.psumIn   := executor.io.hivePsumIn
  hiveComb.io.loadHIn  := executor.io.hiveLoadH
  hiveComb.io.loadVInLock  := executor.io.hiveLoadVLock
  hiveComb.io.loadVIn  := executor.io.hiveLoadV
  // 水平 loadW 加载（可选模式）：垂直模式下 executor 输出恒 0，阵列侧
  // 相关分支为死路，不加显式 mode 端口、靠信号活跃性自然二选一
  hiveComb.io.loadWIn      := executor.io.hiveLoadW
  hiveComb.io.loadWInLock  := executor.io.hiveLoadWLock
  hiveComb.io.validIn  := executor.io.hiveValidIn
  hiveComb.io.fmtIn    := regFile.aFmt
  // bFmt 跟随 aFmt（mixFmtEn=0），保证旧软件 bit-exact；MX 配置允许 mixFmtEn=1
  // 以支持异构双 MX（aFmt=E4M3, bFmt=E5M2）
  hiveComb.io.bFmtIn   := regFile.bFmt
  hiveComb.io.rndIn    := regFile.rnd

  // P3: scale 广播接线——MX 配置下由 Executor 的 scale 通道驱动（scaleB 每列
  // 驻留向量、scaleA 沿激活链 skew 流动，替换 P1 顶层常量 0）；非 MX 配置保持
  // 常量 0（scale 端口/DMA/FIFO 均不生成，行为 bit-exact）
  if (cfg.hasMx) {
    hiveComb.io.scaleAIn := executor.io.hiveScaleAIn.get
    hiveComb.io.scaleBIn := executor.io.hiveScaleBIn.get
  } else {
    hiveComb.io.scaleAIn.foreach(_ := 0.U)
    hiveComb.io.scaleBIn.foreach(_ := 0.U)
  }
  hiveComb.io.clear    := executor.io.hiveClear

  executor.io.hiveCOut     := hiveComb.io.cOut
  executor.io.hiveValidOut := hiveComb.io.validOut

  // ==========================================================================
  // Flush 信号
  // ==========================================================================
  scratchpad.io.flushA := executor.io.flushA
  scratchpad.io.flushB := executor.io.flushB
  scratchpad.io.flushC := executor.io.flushC

  // ==========================================================================
  // P3: MX Scale 通道（仅 hasMx 生成；非 MX 配置不例化 DMA/FIFO、不暴露 dmaScaleA/dmaScaleB）
  //   - scaleADma/scaleBDma 为独立模块 HiveCoreDmaScaleRd（从 HiveCoreDmaRdOnly
  //     拆出，行为逐拍一致）。基址直连 scale 地址寄存器，不再需要
  //     WireInit(regFile) 覆写 regs(3)/regs(4) 的伪装 hack：
  //     scaleADma.baseAddr ← regFile.scaleAAddr（regs(9)），dim ← regFile.m；
  //     scaleBDma.baseAddr ← regFile.scaleBAddr（regs(10)），dim ← regFile.n
  //     （块内 kb 步进，运行期 N）。
  //   - 遍历参数复用共享 calcConfig（nTile/kTile/aColTileAddressOffset/
  //     aRowAddressOffset）。
  //     * scaleADma 行数天然镜像 aDma（m×kTile×nTile），与 Executor
  //       每激活行 pop 的 scaleA 一一对应。
  //     * scaleBDma 两级遍历（nt 轮外 × kb 块内），bufWidth=8：每 sLOAD_B 窗口
  //       按列供 totalN 拍（8-bit/拍/列）；Executor 与 bPop 同拍 lockstep pop
  //       scaleB（beat b → 列 n=(totalN-1)-b），逐拍暂存进 scaleBRegs、末拍
  //       lock 时 totalN 列并列呈现（非「每 K-块起始 pop 一个 totalN 向量广播」
  //       ——旧描述已过期，scaleBPop 端口实为 UInt(8.W)）；FIFO 反压自然节流上游。
  //   - 【MX 必选】start 加运行期 isMx(regFile.aFmt) 门控：仅 MX 运行时启动；
  //     非 MX 运行（fmt 非 MX 枚举）恒不启动，不向外部发请求（非 MX harness 不
  //     驱动 dmaScaleA/dmaScaleB 也天然安全）。判据与 Executor err/scale 块同用 aFmt。
  // ==========================================================================
  if (cfg.hasMx) {
    // scaleA DMA：A 式三级遍历，基址直连 scaleAAddr，内层行数 = m
    val scaleADma = aDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleADma.io.isA := true.B
    scaleADma.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.aFmt)
    scaleADma.io.regFile    := regFile
    scaleADma.io.reg_baseAddr := regFile.aScaleAddr
    scaleADma.io.reg_kTile := calcConfig.io.calcConfig.kTile
    scaleADma.io.reg_mTile := calcConfig.io.calcConfig.mTile
    scaleADma.io.reg_nTile := calcConfig.io.calcConfig.nTile
    scaleADma.io.reg_rowOffset := calcConfig.io.calcConfig.scaleARowAddressOffset
    scaleADma.io.reg_colTileOffset := calcConfig.io.calcConfig.scaleAColTileAddressOffset
    io.dmaScaleA.get <> scaleADma.io.dmaExtRdIF

    // scaleB DMA：两级 nt×kb 遍历，基址直连 scaleBAddr，块内步进 = n
    val scaleBDma = bDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleBDma.io.isA := false.B
    scaleBDma.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.bFmt)
    scaleBDma.io.regFile    := regFile
    scaleBDma.io.reg_baseAddr := regFile.bScaleAddr
    scaleBDma.io.reg_kTile := calcConfig.io.calcConfig.kTile
    scaleBDma.io.reg_mTile := calcConfig.io.calcConfig.mTile
    scaleBDma.io.reg_nTile := calcConfig.io.calcConfig.nTile
    scaleBDma.io.reg_rowOffset := calcConfig.io.calcConfig.scaleBRowAddressOffset
    scaleBDma.io.reg_colTileOffset := calcConfig.io.calcConfig.scaleBColTileAddressOffset
    io.dmaScaleB.get <> scaleBDma.io.dmaExtRdIF

    // scale DMA → scratchpad scale FIFO
    scratchpad.io.scaleAPush.get.valid   := scaleADma.io.bufPush.valid
    scratchpad.io.scaleAPush.get.payload := scaleADma.io.bufPush.payload
    scaleADma.io.bufPush.ready           := scratchpad.io.scaleAPush.get.ready
    scaleADma.io.bufPopFire              := scratchpad.io.scaleAPop.get.fire

    scratchpad.io.scaleBPush.get.valid   := scaleBDma.io.bufPush.valid
    scratchpad.io.scaleBPush.get.payload := scaleBDma.io.bufPush.payload
    scaleBDma.io.bufPush.ready           := scratchpad.io.scaleBPush.get.ready
    scaleBDma.io.bufPopFire              := scratchpad.io.scaleBPop.get.fire

    // scale FIFO flush 跟随 A/B flush（execute 起始拍清空，与 aDma/bDma 同范式）
    scratchpad.io.flushScaleA.get := executor.io.flushA
    scratchpad.io.flushScaleB.get := executor.io.flushB

    // scratchpad scale pop → Executor
    executor.io.scaleAPop.get.valid   := scratchpad.io.scaleAPop.get.valid
    executor.io.scaleAPop.get.payload := scratchpad.io.scaleAPop.get.payload
    scratchpad.io.scaleAPop.get.ready := executor.io.scaleAPop.get.ready
    executor.io.scaleBPop.get.valid   := scratchpad.io.scaleBPop.get.valid
    executor.io.scaleBPop.get.payload := scratchpad.io.scaleBPop.get.payload
    scratchpad.io.scaleBPop.get.ready := executor.io.scaleBPop.get.ready
  }

  // ==========================================================================
  // P3b: C 侧量化 scale 写回通道（仅 hasMx 生成；非 MX 配置不例化/不接线）
  //   - cScaleDma（HiveCoreDmaScaleWr）：start = executePulse & cQuantRun
  //     （非量化运行永不启动）；
  //   - 数据流（P2 裁定 5）：cvt.scaleOut → 顶层深 2 cScaleFifo（唯一 scale 缓冲）
  //     → cScaleDma 自主写外；
  //   - 完成序：executor.io.dmaCBusy 已 OR cScaleDma.busy（上方），drain 等 scale 写完；
  //   - err 进 dmaErr 汇聚（上方 hasMx 分支）。
  // ==========================================================================
  if (cfg.hasMx) {
    cDmaScale.get.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.cFmt)
    cDmaScale.get.io.baseAddr   := regFile.cScaleAddr
    cDmaScale.get.io.regFile    := regFile
    cDmaScale.get.io.calcConfig := calcConfig.io.calcConfig
    io.dmaCScale.get <> cDmaScale.get.io.dmaExtWrIF

    // P2（裁定 5）：顶层深 2 cScaleFifo 是唯一 scale 缓冲（从 Scratchpad 上移，
    // 深 64→2；原 cDma.cScalePush 锁步通路删除）：
    //   cvt.scaleOut → cScaleFifo → cDmaScale.bufPop；flush 接 executor.flushC。
    //   cvt 内已保证 out/scale 原子配对（scale 未就绪时进 dataDone 子态，out 不重发），
    //   FIFO 满时自然反压 cvt。
    val cScaleFifo = Module(new StreamFifo(UInt(cfg.cScaleRowW.W), 2))
    cScaleFifo.io.push.valid   := cvt.io.scaleOut.valid
    cScaleFifo.io.push.payload := cvt.io.scaleOut.bits
    cvt.io.scaleOut.ready      := cScaleFifo.io.push.ready
    cScaleFifo.io.flush        := executor.io.flushC

    cDmaScale.get.io.bufPop.valid   := cScaleFifo.io.pop.valid
    cDmaScale.get.io.bufPop.payload := cScaleFifo.io.pop.payload
    cScaleFifo.io.pop.ready         := cDmaScale.get.io.bufPop.ready
  } else {
    // 非 MX 配置：cvt 内部无量化支路，scaleOut.valid 恒 0，ready 恒高吸收。
    cvt.io.scaleOut.ready := true.B
  }

  // ==========================================================================
  // Status 输出
  // ==========================================================================
  io.status.busy       := executor.io.busy
  io.status.done       := doneFlag
  io.status.err        := errFlag
  io.status.progress   := executor.io.progress
  io.status.aOccupancy := scratchpad.io.aOccupancy
  io.status.bOccupancy := scratchpad.io.bOccupancy
  io.status.cOccupancy := scratchpad.io.cOccupancy
}
