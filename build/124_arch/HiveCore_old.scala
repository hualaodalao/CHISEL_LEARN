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
    // ── 对外契约（破坏性变更声明，相对旧版统一 extDataWidth 接口）──
    // 三通道 dmaExt 数据位宽不再由统一 extDataWidth 配置项决定，改为各通道
    // 独立匹配自身 buffer 行宽：dmaA = aExtW、dmaC = cExtW、
    // dmaB = bExtW。每个 beat 搬运一整行数据，模块内部不做任何
    // 拆包/拼包（无 beat 内字段切分、无多 beat 组行），外部存储侧必须按
    // 对应通道位宽供数（A/B 读）/收数（C 写），否则数据对齐错乱。
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
    val dmaScaleB = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, cfg.scaleRowW)) else None
    // P3b C 侧量化 scale 写回 DMA 外部通道（仅 hasMx 生成，非 MX 配置不暴露）：
    //   dmaCScale = 量化 C 输出的 E8M0 块 scale（cScaleRowW = totalN/8 字节/拍，
    //   有效字节 = ceil(n/blk)，高位零填充），基址 = regFile.cScaleAddr（regs(11)）
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
  //                     (16)+(13,11)=outFmt (C 写回 dmaC 输出格式，P0 扩 4-bit：
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
  // P4.2: MX scale DMA 提到外层用 Option 持有（P3 接线块内改用 .get 引用），
  //   以便下方 dmaErr 汇聚点引用其 io.err。非 MX 配置为 None（整块不例化），
  //   汇聚式逐字不变、bit-exact。位宽/深度与 P3 块内原例化一致。
  //   独立模块 HiveCoreDmaScaleRd：isScaleB=false 走 A 式三级遍历（scaleA），
  //   isScaleB=true 走两级 nt×kb 遍历（scaleB），行为与原 HiveCoreDmaRdOnly
  //   isScale 路径逐拍一致。
  val aDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaScaleRd(cfg, isScaleB = false, bufWidth = 8,             bufDepth = cfg.scaleBufferDepth))) else None
  val bDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaScaleRd(cfg, isScaleB = true,  bufWidth = cfg.scaleRowW, bufDepth = cfg.scaleBufferDepth))) else None
  // P3b C 侧量化 scale 写回 DMA（新端口集：start/done/busy/err + dmaCScale 写通道）：
  // 仅 hasMx 例化；数据源 = 顶层深 2 cScaleFifo（P2 裁定 5 上移，唯一 scale 缓冲，
  // cvt.scaleOut 供数）。start 门控 = executePulse & cQuantRun（cQuantRun = 低 3 位∈{4,5,6}
  // 且 bit16=0 枚举，与 cvt 量化支路同源）。
  val cDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaScaleWr(cfg))) else None

  // ==========================================================================
  // calcConfig 生产者（喂给 bDma）：从 regFile 推导 tile 数与六个地址偏移。
  // 采用组合逻辑：bDma 在 start（EXECUTE 脉冲）当拍采样，regFile 在计算期间
  // 保持不变，因此组合驱动即可保证采样到 execute 时刻的正确值
  // （若用 Reg 锁存，start 当拍会采样到锁存前的旧值）。
  // 偏移语义与 Executor 的 aTileAddr/bTileAddr/cStoreAddr 保持一致（字节地址）：
  //   aRow = aStride（行步长）           aCol = totalN * (aEffW/8)（K tile 间列偏移）
  //   bRow/bCol 双模式（regFile.loadWMode，regs(7)(5)）：
  //     垂直加载（loadWMode=0，既有行为）：bRow = n*(bW/8)（沿 K 行步长），
  //       bCol = totalN*(bW/8)（N tile 间列偏移）
  //     水平 loadW 加载（loadWMode=1）：B 内存按 N×K 转置存储（行=n 列=k），
  //       bRow = k*(bW/8)（转置行步长），bCol = totalN*k*(bW/8)
  //       （轮间跳 = 跨 totalN 个转置行）
  //   cRow = cStride                     cCol = totalN * (cEffW/8)
  //
  // 【MX v2 SIMD】mxRun = cfg.hasMx && isMx(regFile.aFmt)（与 scale DMA start 门控同判据）：
  //   16-bit lane 打包 2 个 8-bit 元素（1 字节/元素），行步长 = K 字节（非 K*(aW/8)），
  //   一个 kTile pass 覆盖 32 K 元素（kTile 数 = K/32）；非 MX 运行逐字等于原公式。
  // ==========================================================================
  val executePulse = io.cmd.fire && io.cmd.payload.op === HiveCoreOp.EXECUTE
  // MX 运行期判据：非 MX 配置（!cfg.hasMx）编译期短路为 false，不生成比较逻辑，bit-exact。
  val mxRun: Bool = if (cfg.hasMx) DataFormat.isMx(regFile.aFmt) else false.B
  // dmaC 写回输出格式运行期有效值——P2 判据单源化：cOutFmtEff = regFile.outFmt，
  // 全设计唯一源（同时供 cvt.io.outFmt、cDma.io.outFmt 与下方布局/步进判据）。
  // 废除旧「整数运行强制 000 透传」判据（outFmtEff=(isFloat||mxRun)?outFmt:000，两处删除）：
  // 整数族编码 1000/1001/1010 直接生效，族合法性由 P0 Executor 格式族交叉校验接管（违者 config-err）。
  val cOutFmtEff: UInt = regFile.outFmt
  // 16-bit 输出判据：001=fp16 / 010=bf16 / 1001=int16；011/0111=reserved 回落 fp32 语义。
  val cIsInt16: Bool = cOutFmtEff === 9.U
  val cIsInt8: Bool = cOutFmtEff === 10.U
  val cIsFmt16: Bool = cOutFmtEff === 1.U || cOutFmtEff === 2.U || cIsInt16
  // P3b 量化输出判据（与 WrOnly io.outFmt 同源）：枚举白名单 cQuantRun = 低 3 位∈{4,5,6}
  // 且 bit16=0（0100/0101/0110）；111/011/1100 等 reserved 完整回落（不命中量化通路）。
  // 非 hasMx 配置编译期死 false。
  val cQuantRun: Bool = if (cfg.hasMx) (cOutFmtEff === 4.U || cOutFmtEff === 5.U || cOutFmtEff === 6.U) else false.B
  val cIsFmt8: Bool = if (cfg.hasMx) (cOutFmtEff === 4.U || cOutFmtEff === 5.U) else false.B
  val cIsFmt4: Bool = if (cfg.hasMx) (cOutFmtEff === 6.U) else false.B
  // 输出元素字节数三档（运行期）：fp32/int32/reserved 回落=4B、fp16/bf16/int16=2B、
  // mxfp8/int8=1B（mxfp4 半字节不走本式，地址/步进另用 n>>1 / totalN>>1 专用分支）。
  val cOutElemBytes: UInt = MuxCase(4.U(3.W), Seq(
    cIsFmt16 -> 2.U(3.W),
    (cIsFmt8 || cIsInt8) -> 1.U(3.W)))
  val calcConfig = Wire(HiveCoreExePreCalcConfig(cfg))
  calcConfig.mTile := (regFile.m + cfg.totalN.U - 1.U) / cfg.totalN.U
  calcConfig.nTile := (regFile.n + cfg.totalN.U - 1.U) / cfg.totalN.U
  // MX：16 lane × 2 元素/拍 = 32 K 元素/拍，32-K scale 块 = 单 pass → kTile = K/32；
  // 非 MX：逐字保留原 ceil(K/totalN) 公式。
  calcConfig.kTile := Mux(mxRun, regFile.k / 32.U, (regFile.k + cfg.totalN.U - 1.U) / cfg.totalN.U)

  // MX：打包行步长 = K 字节（每元素 1 字节）；非 MX：K * (aW/8) 逐字保留。
  calcConfig.aRowAddressOffset := Mux(mxRun, regFile.k * 1.U, regFile.k * (cfg.aW / 8).U)
  // aColTileAddressOffset = totalN*(aW/8) = 16 lane × 2 字节 = 32 字节：
  // 打包后恰好等于单 kTile 的 k 窗口字节数，数值自动正确，公式不改。
  calcConfig.aColTileAddressOffset := cfg.totalN.U * (cfg.aW / 8).U

  // --- scaleA 读通道专用地址偏移（仅 hasMx 生成真实逻辑；非 MX 配置驱 0 死值）：
  // MXFP8 每 32 个 K 元素共享 1 个 E8M0 scale 字节，行步进 = K/32 字节（>> 5，整除由
  // MX 运行期 K%32==0 校验保证）、块（kb）步进 = 1 字节。独立于 aRowAddressOffset/
  // aColTileAddressOffset（A 数据 DMA 共用，禁动）。scale DMA 仅 mxRun 启动，非 MX 死路。
  if (cfg.hasMx) {
    calcConfig.scaleARowAddressOffset := regFile.k >> 5       // K/32 字节
    calcConfig.scaleAColTileAddressOffset := 1.U              // kb 步进 1 字节
  } else {
    calcConfig.scaleARowAddressOffset := 0.U
    calcConfig.scaleAColTileAddressOffset := 0.U
  }

  // bRow/bCol 双模式：loadWMode=0 垂直分支逐字不动；loadWMode=1 分支按打包语义加 mxRun Mux
  calcConfig.bRowAddressOffset := Mux(regFile.loadWMode,
    Mux(mxRun, regFile.k * 1.U, regFile.k * (cfg.bW / 8).U),
    regFile.n * (cfg.bW / 8).U)
  calcConfig.bColTileAddressOffset := Mux(regFile.loadWMode,
    Mux(mxRun, cfg.totalN.U * regFile.k * 1.U, cfg.totalN.U * regFile.k * (cfg.bW / 8).U),
    cfg.totalN.U * (cfg.bW / 8).U)
  
  // ==========================================================================
  // C 写回地址布局（P2 裁定 8 单式化，取代原 cBeatsPerRowMax==1/>1 × hasMx 四分支）：
  //   行步进 = n×cOutElemBytes，列 tile 步进 = totalN×cOutElemBytes（运行期三档 4/2/1B）；
  //   mxfp4 半字节沿用专用分支 n>>1 / totalN>>1（n 偶由 Executor 校验保证）。
  //   reserved 回落：非量化/非 16/非 8 编码（含 0000 fp32 与 1011..1111）自然落 4B 档。
  // ==========================================================================
  calcConfig.cRowAddressOffset := Mux(cIsFmt4, regFile.n >> 1, regFile.n * cOutElemBytes)
  calcConfig.cColTileAddressOffset := Mux(cIsFmt4, cfg.totalN.U >> 1, cfg.totalN.U * cOutElemBytes)

  // --- P3b C 侧量化 scale 地址偏移（仅 hasMx 生成真实逻辑；非 MX 配置驱 0 死值）：
  // blk 由 qBlockSize 运行期解码（移位实现：blk = 8 << qBlockSize，log2(blk) = 3+qBlockSize）；
  // 行步进 = ceil(n/blk) 字节、列 tile 步进 = totalN/blk 字节。非量化运行无消费者（死路）。
  if (cfg.hasMx) {
    val cBlkShift = regFile.qBlockSize +& 3.U        // log2(blk)
    val cBlkElems = 8.U << regFile.qBlockSize        // blk 元素数（8/16/32/64）
    calcConfig.cScaleRowAddressOffset := (regFile.n + cBlkElems - 1.U) >> cBlkShift
    calcConfig.cScaleColTileAddressOffset := cfg.totalN.U >> cBlkShift
  } else {
    calcConfig.cScaleRowAddressOffset := 0.U
    calcConfig.cScaleColTileAddressOffset := 0.U
  }

  // P0/P2 契约：cBeatAddrStep = (cDmaExtW/elemBits)×elemBytes（运行期，按 cOutFmtEff），
  // 供 WrOnly 拍内地址 = curAddr + beatCnt×cBeatAddrStep 消费。
  // 元素位宽档 = fp32/int32/reserved 回落→32、fp16/bf16/int16→16、mxfp8/int8→8、mxfp4→4，
  // 与 cvt 选段、WrOnly 拍数解码、上方地址布局同源；移位实现避免运行期除法器。
  // mxfp4 沿用半字节专用分支：每拍字节数 = cDmaExtW/8（64-bit 行 → 1 拍）。
  private val cElemBitsLog2: UInt = MuxCase(5.U(3.W), Seq(
    cIsFmt16 -> 4.U(3.W),
    (cIsFmt8 || cIsInt8) -> 3.U(3.W),
    cIsFmt4 -> 2.U(3.W)))
  calcConfig.cBeatAddrStep := Mux(cIsFmt4, (cfg.cDmaExtW / 8).U,
    (cfg.cDmaExtW.U >> cElemBitsLog2) * cOutElemBytes)

  // ==========================================================================
  // bDma 控制接线：execute 起始拍与 Executor 同步启动，自主扫完整个 B 矩阵
  // ==========================================================================
  bDma.io.isA         := false.B
  bDma.io.start       := executePulse
  bDma.io.regFile     := regFile
  bDma.io.calcConfig  := calcConfig
  // bDma.io.done/busy 目前无消费者（err 参与 dmaErr 汇聚），留空部分 FIRRTL 会优化掉
  // 外部只读通道直连顶层 dmaB
  io.dmaB <> bDma.io.dmaExtRdIF

  // ==========================================================================
  // aDma 控制接线：与 bDma 同为 execute 单次启动全自主供数——内建 nTile
  // 轮次（nTile 外 → kTile 中 → M 内），供满 nTiles 轮后自然停止；
  // regFile/calcConfig 复用上方组合 Wire
  // ==========================================================================
  aDma.io.isA         := true.B
  aDma.io.start       := executePulse
  aDma.io.regFile     := regFile
  aDma.io.calcConfig  := calcConfig
  io.dmaA <> aDma.io.dmaExtRdIF

  // ==========================================================================
  // cDma 控制接线：execute 起始拍启动后自主待命，storeGate 由 Executor 在
  // 末 K pass 的 store 窗口拉高，防止多 pass 中间 partial sum 被误搬
  // ==========================================================================
  cDma.io.start       := executePulse
  cDma.io.peekBlock        := executor.io.cPeek
  cDma.io.regFile     := regFile
  cDma.io.calcConfig  := calcConfig
  io.dmaC <> cDma.io.dmaExtWrIF

  // ==========================================================================
  // 命令处理逻辑
  // ==========================================================================
  val respValid = RegInit(false.B)
  val respData  = RegInit(0.U(32.W))
  val respDone  = RegInit(false.B)
  val respErr   = RegInit(false.B)

  io.cmd.ready := !respValid  // 上一个响应未被取走时反压
  io.resp.valid       := respValid
  io.resp.payload.data := respData
  io.resp.payload.done := respDone
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
        when(cmd.reg0Addr < cfg.registerNumRW.U && cmd.reg0Addr =/= 0x08.U) { regFile.regs(addr0) := cmd.data0 }
        when(cmd.reg0Addr === 0x07.U) {
              when(cmd.data0(0)) { doneFlag := false.B }  // clear_done
        }
        // 生成响应
        respValid := true.B
        respData  := 0.U
        respDone  := false.B
        respErr   := false.B
      }
      is(HiveCoreOp.REG_WRITE2){
        val addr0 = cmd.reg0Addr(3, 0) 
        // P4.3: 同 REG_WRITE1，覆盖 0x09/0x0A/0x0B、排斥 STATUS 0x08（regs(8) 不被读，bit-exact）
        when(cmd.reg0Addr < cfg.registerNumRW.U && cmd.reg0Addr =/= 0x08.U) { regFile.regs(addr0) := cmd.data0 }
        val addr1 = cmd.reg1Addr(3, 0) 
        when(cmd.reg1Addr < cfg.registerNumRW.U && cmd.reg1Addr =/= 0x08.U) { regFile.regs(addr1) := cmd.data1 }
        when(cmd.reg1Addr === 0x07.U) {  
          when(cmd.data1(0)) { doneFlag := false.B }  // clear_done
        }
        // 生成响应
        respValid := true.B
        respData  := 0.U
        respDone  := false.B
        respErr   := false.B
      }
      is(HiveCoreOp.REG_READ) {
        // 读取寄存器（addr 截 4 位与写路径 addr0/addr1 同源；寄存器组 ≤16，
        // 动态索引语义与截断前一致，仅消除位宽超宽告警）
        val addr = cmd.reg0Addr(3, 0)
        val statusReg = Cat(executor.io.progress, 0.U(13.W), errFlag, doneFlag, executor.io.busy)
        val readVal = Mux(addr === 0x08.U, statusReg, regFile.regs(addr))
        respValid := true.B
        respData  := readVal
        respDone  := false.B
        respErr   := false.B
      }
      is(HiveCoreOp.EXECUTE) {
        // 不需要立即回响应, 等完成后回
        respValid := false.B
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
  when(executePulse) { errFlag := false.B }

  when(executor.io.done) {
    doneFlag  := true.B
    respValid := true.B
    respData  := 0.U
    respDone  := true.B
    respErr   := executor.io.err || dmaErr
    errFlag   := executor.io.err || dmaErr
  }

  // P5.4: MX config-err（K%32≠0 / isMx(bFmt)&&!loadWMode）直接回 sIDLE、不发 done，
  // 故上方 done 门控捕捉不到。追加 hasMx-gated 的 err 直连，使 STATUS.err 位可被软件轮询。
  // 清除/置位语义（Victor P1）：errFlag 在 execute 脉冲拍被上方清除；同拍违约不会丢失——
  // 新违约在脉冲拍置 errState（寄存器），次拍起经 io.err 由本直连正常锁存；
  // 而脉冲拍 io.err 的高电平必为上次运行遗留的旧错（已被同拍清除），故用 !executePulse
  // 排除，避免合法再 EXECUTE 被旧 err 回置。非 MX 配置此块编译期不生成，bit-exact。
  if (cfg.hasMx) {
    when(executor.io.err && !executePulse) { errFlag := true.B }
  }

  // ==========================================================================
  // Executor 连接寄存器组
  // ==========================================================================
  executor.io.execute := executePulse
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
  // ==========================================================================
  // 【P3 修复 2（块末行窗口竞态防护：末行锁存后关收行门，重实版）】
  // 历史：首版用顶层 blkRowCnt/lastLineLatching（cDma.bufPop.fire 口径）在「末行消费拍」
  // 闸断 → 闸掉的恰是末行本身的合法消费拍（二分实证）被撤销；撤销后末行锁存后窗口
  // 超弹回归（LoadWSpec/QuantSpec nt=1 块数据错位复现）。本版改用 WrOnly 内部收行计数
  // blkRxCnt 导出的 blkRxFull：本块收满 m 行（末行已锁存）后关上游消费门，
  // 不阻断末行本身；doneBlock 归零后自然重开。无组合环：计数为寄存器态。
  private val blkRxFull = cDma.io.blkRxFull
  // 【S-Ken4 评审项】blkRxFull 由 WrOnly bufPop.fire 口径记账，同拍直通竞态下晚一拍关门（P3 修复 3），
  // 已被 blkLatchFull（cvt.in.fire 口径）完全替代为实际闸门源（见下方 cPop.ready 与 cvt.in.valid）。
  // blkRxFull 保留为 WrOnly 内部统计输出，不再作为顶层闸门源，无硬件副作用。
  // 【P3 修复 3（块边界同拍直通竞态：blkRxFull 晚一拍关门，Tina/任务90）】
  // 根因（typical 双MX M=32 实证，DBG2/DBG3 对账）：cvt 背靠背直通时「释放行 m」与
  // 「锁存行 m+1」发生在同一拍，而 blkRxFull 按 WrOnly bufPop.fire（=行释放）记账，
  // 该拍寄存器态仍为 m-1 → 闸门大开，cvt 把下一块的首行锁存进来（实测：nt0 写回窗
  // 口 cPop=33 拍，第 33 行 = nt1-kt0 partial row0）。双重损伤：①该行滞留 cvt 行寄存，
  // 下一写回窗口作为 tile1 首行发出（tile1 beat0 = 量化后的 kt0-only partial）；
  // ②回灌侧丢失该 partial 行 → nt1-kt1 回灌整体移一拍（POPR=63/64，POPR==PUSH[1:]），
  // tile1 全段数据错。
  // 修复：改按 cvt.in.fire（=行锁存）记账。cvt 行寄存器唯一一份，第 m 次锁存当拍
  // holdValid 置真，下一次锁存必先等释放（≥1 拍后），届时计数已达 m → 闸已关，
  // 无同拍竞态。blkLatchFull 为实际闸门源，blkRxFull 仅保留为 WrOnly 内部输出（见 S-Ken4）。
  private val blkLatchCnt = RegInit(0.U(cfg.mnkWidth.W))
  when(cvt.io.in.fire) { blkLatchCnt := blkLatchCnt + 1.U }
  when(cDma.io.doneBlock || executePulse) { blkLatchCnt := 0.U }  // 块末/重启归零（last-connect）
  private val blkLatchFull = blkLatchCnt === regFile.m
  scratchpad.io.cPop.ready := Mux(executor.io.cStoreGate, cvt.io.in.ready && cDma.io.bufPop.ready && !blkLatchFull, executor.io.cPop.ready)

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
  cvt.io.in.valid := scratchpad.io.cPop.valid && !executor.io.cPop.ready && executor.io.cStoreGate && cDma.io.bufPop.ready && !blkLatchFull
  cvt.io.in.bits  := scratchpad.io.cPop.payload
  cvt.io.outFmt   := cOutFmtEff          // 判据单源化（全设计唯一源）
  cvt.io.rnd      := regFile.rnd
  cvt.io.blkSel   := regFile.qBlockSize
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
  cDma.io.outFmt         := cOutFmtEff   // 拍数解码唯一判据源（与 cvt 同源）
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
  hiveComb.io.bFmtIn   := Mux(regFile.mixFmtEn, regFile.bFmt, regFile.aFmt)
  hiveComb.io.rndIn    := regFile.rnd
  // 非 MX 配置：v1 禁止混合格式（mixFmtEn 必须为 0，bFmt 跟随 aFmt），断言保护旧行为。
  // MX 配置（hasMx）：解禁 mixFmtEn 以支持异构 E4M3×E5M2；bFmtIn mux 已覆盖。
  // 本 gate 为编译期，非 MX 配置断言逐字保留，bit-exact。
  if (!cfg.hasMx) {
    assert(!regFile.mixFmtEn, "v1: mixFmtEn must be 0")
  }
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
  //     * scaleBDma 两级遍历（nt 轮外 × kb 块内）；Executor 仅在 K-块起始
  //       pop scaleB，FIFO 反压自然节流上游。
  //   - 【MX 必选】start 加运行期 isMx(regFile.aFmt) 门控：仅 MX 运行时启动；
  //     非 MX 运行（fmt 非 MX 枚举）恒不启动，不向外部发请求（非 MX harness 不
  //     驱动 dmaScaleA/dmaScaleB 也天然安全）。判据与 Executor err/scale 块同用 aFmt。
  // ==========================================================================
  if (cfg.hasMx) {
    val mxRun = DataFormat.isMx(regFile.aFmt)
    // scaleA DMA：A 式三级遍历，基址直连 scaleAAddr，内层行数 = m
    val scaleADma = aDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleADma.io.start      := executePulse & mxRun
    scaleADma.io.baseAddr   := regFile.scaleAAddr
    scaleADma.io.dim        := regFile.m
    scaleADma.io.calcConfig := calcConfig
    io.dmaScaleA.get <> scaleADma.io.dmaExtRdIF

    // scaleB DMA：两级 nt×kb 遍历，基址直连 scaleBAddr，块内步进 = n
    val scaleBDma = bDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleBDma.io.start      := executePulse & mxRun
    scaleBDma.io.baseAddr   := regFile.scaleBAddr
    scaleBDma.io.dim        := regFile.n
    scaleBDma.io.calcConfig := calcConfig
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
    cDmaScale.get.io.start      := executePulse & cQuantRun
    // 【C1 修复】abort：非量化运行 EXECUTE 拍强制 cDmaScale 回 sIDLE。解除 config-err 后
    // cDmaScale 卡 sTRANSFER、busy 恒高、dmaCBusy OR 门封死后续非量化运行的死锁链。
    // 量化运行（cQuantRun）拍 start 拉高，abort 无效（HiveCoreDmaScaleWr 内 abort&&!start 门控）。
    cDmaScale.get.io.abort      := executePulse & !cQuantRun
    cDmaScale.get.io.baseAddr   := regFile.cScaleAddr
    cDmaScale.get.io.regFile    := regFile
    cDmaScale.get.io.calcConfig := calcConfig
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
