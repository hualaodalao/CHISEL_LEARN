/** HiveCore — 脉动阵列加速器顶层集成模块（寄存器配置 + 一键执行架构）
  *
  * 外部接口极简，仅 3 种命令:
  *   - REG_WRITE: 写入 2 个 32-bit 寄存器
  *   - EXECUTE: 触发矩阵计算
  *   - REG_READ: 读取 1 个寄存器
  *
  * 内部组件:
  *   - Register File: 9 个 32-bit 配置/状态寄存器（映射见下方寄存器组注释）
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
    // 独立匹配自身 buffer 行宽：dma0Ext = aExtW、dma1Ext = cExtW、
    // dma2Ext = bExtW。每个 beat 搬运一整行数据，模块内部不做任何
    // 拆包/拼包（无 beat 内字段切分、无多 beat 组行），外部存储侧必须按
    // 对应通道位宽供数（A/B 读）/收数（C 写），否则数据对齐错乱。
    // A 矩阵只读 DMA 外部通道（cmd: 读地址流; rsp: 读数据流，位宽 = aExtW）
    val dma0Ext = HiveCoreDMAExtReadOnlyIF(cfg, cfg.aExtW)
    // C 结果写回 DMA 外部通道（req/grant + writeData 逐 beat 流，位宽 = cExtW）。
    // 握手语义：WrOnly DMA 为逐 beat req/grant，len 恒 1（旧 DmaEngine 的
    // 单 burst len=M 协议已废弃），外部集成方需逐拍 grant + writeData.ready
    val dma1Ext = new HiveCoreDMAExtWriteOnlyIF(cfg, cfg.cExtW)
    // B 权重专用只读 DMA 外部通道（cmd: 读地址流; rsp: 读数据流，位宽 = bExtW）
    val dma2Ext = HiveCoreDMAExtReadOnlyIF(cfg, cfg.bExtW)
    // MX scale 只读 DMA 外部通道（仅 hasMx 生成，非 MX 配置不暴露，避免破坏旧 harness）：
    //   dma3Ext = scaleA（8-bit/行），dma4Ext = scaleB（scaleRowW = totalN*8/行）
    val dma3Ext = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, 8))             else None
    val dma4Ext = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, cfg.scaleRowW)) else None
    val status = Output(new HiveCoreStatus(cfg))
  })

  // ==========================================================================
  // 寄存器组定义（映射与 HiveInterface.scala HiveCoreRegister 一致，共 9 个）
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
  //                     [5]=loadWMode (0=垂直加载, 1=水平 loadW 加载)
  // 0x08  REG_STATUS    [0]=busy, [1]=done, [2]=err, [31:16]=progress (只读)

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
  // C 结果写回自主 DMA：bufWidth = 一整行 C（totalN*cEffW），storeGate 门控；
  // hasFp 传入 cfg.hasFp：含浮点时 cDma 内部对写出数据做 40→32 延迟规格化
  val cDma       = Module(new HiveCoreDmaWrOnly(cfg, bufWidth = cfg.totalN * cfg.cEffW, hasFp = cfg.hasFp, hasMx = cfg.hasMx))
  // B 权重专用只读 DMA：bufWidth = 一整行权重（totalN*bW），深度 = bBufferDepth
  val bDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.bW, bufDepth = cfg.bBufferDepth))
  val executor   = Module(new HiveCoreExecutor2(cfg))
  val hiveComb   = Module(new HiveComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cW, cfg.supportedFmts))
  // P4.2: MX scale DMA 提到外层用 Option 持有（P3 接线块内改用 .get 引用），
  //   以便下方 dmaErr 汇聚点引用其 io.err。非 MX 配置为 None（整块不例化），
  //   汇聚式逐字不变、bit-exact。位宽/深度与 P3 块内原例化一致。
  val aDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaRdOnly(cfg, bufWidth = 8,               bufDepth = cfg.scaleBufferDepth))) else None
  val bDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.scaleRowW,   bufDepth = cfg.scaleBufferDepth, isScale = true))) else None

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
  // ==========================================================================
  val executePulse = io.cmd.fire && io.cmd.payload.op === HiveCoreOp.EXECUTE
  val calcConfig = Wire(HiveCoreExePreCalcConfig(cfg))
  calcConfig.mTile := (regFile.m + cfg.totalN.U - 1.U) / cfg.totalN.U
  calcConfig.nTile := (regFile.n + cfg.totalN.U - 1.U) / cfg.totalN.U
  calcConfig.kTile := (regFile.k + cfg.totalN.U - 1.U) / cfg.totalN.U
  
  calcConfig.aRowAddressOffset := regFile.k * (cfg.aW / 8).U
  calcConfig.aColTileAddressOffset := cfg.totalN.U * (cfg.aW / 8).U
  
  // bRow/bCol 双模式：Mux 默认分支（loadWMode=0）逐字等于垂直加载既有公式
  calcConfig.bRowAddressOffset := Mux(regFile.loadWMode, regFile.k * (cfg.bW / 8).U, regFile.n * (cfg.bW / 8).U)
  calcConfig.bColTileAddressOffset := Mux(regFile.loadWMode, cfg.totalN.U * regFile.k * (cfg.bW / 8).U, cfg.totalN.U * (cfg.bW / 8).U)
  
  calcConfig.cRowAddressOffset := regFile.n * (cfg.cW / 8).U
  calcConfig.cColTileAddressOffset := cfg.totalN.U * (cfg.cW / 8).U

  // ==========================================================================
  // bDma 控制接线：execute 起始拍与 Executor 同步启动，自主扫完整个 B 矩阵
  // ==========================================================================
  bDma.io.isA         := false.B
  bDma.io.start       := executePulse
  bDma.io.regFile     := regFile
  bDma.io.calcConfig  := calcConfig
  // bDma.io.done/busy 目前无消费者（err 参与 dmaErr 汇聚），留空部分 FIRRTL 会优化掉
  // 外部只读通道直连顶层 dma2Ext
  io.dma2Ext <> bDma.io.dmaExtRdIF

  // ==========================================================================
  // aDma 控制接线：与 bDma 同为 execute 单次启动全自主供数——内建 nTile
  // 轮次（nTile 外 → kTile 中 → M 内），供满 nTiles 轮后自然停止；
  // regFile/calcConfig 复用上方组合 Wire
  // ==========================================================================
  aDma.io.isA         := true.B
  aDma.io.start       := executePulse
  aDma.io.regFile     := regFile
  aDma.io.calcConfig  := calcConfig
  io.dma0Ext <> aDma.io.dmaExtRdIF

  // ==========================================================================
  // cDma 控制接线：execute 起始拍启动后自主待命，storeGate 由 Executor 在
  // 末 K pass 的 store 窗口拉高，防止多 pass 中间 partial sum 被误搬
  // ==========================================================================
  cDma.io.start       := executePulse
  cDma.io.peekBlock        := executor.io.cPeek
  cDma.io.regFile     := regFile
  cDma.io.calcConfig  := calcConfig
  io.dma1Ext <> cDma.io.dmaExtWrIF

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
        // P4.3: 写门控覆盖 0x00-0x07 + 0x09/0x0A（含 scale 地址），排斥 STATUS 0x08 只读。
        //   registerNumRW=11 使 <registerNumRW 区间含 0x08，故显式 =/= 8.U 排除。
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
        // P4.3: 同 REG_WRITE1，覆盖 0x09/0x0A、排斥 STATUS 0x08（regs(8) 不被读，bit-exact）
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
        // 读取寄存器
        val addr = cmd.reg0Addr
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
    aDma.io.err || bDma.io.err || cDma.io.err || aDmaScale.get.io.err || bDmaScale.get.io.err
  } else {
    aDma.io.err || bDma.io.err || cDma.io.err
  }
  when(executor.io.done) {
    doneFlag  := true.B
    respValid := true.B
    respData  := 0.U
    respDone  := true.B
    respErr   := executor.io.err || dmaErr
    errFlag   := executor.io.err || dmaErr
  }

  // P5.4: MX config-err（K%32≠0 / isMx(bFmt)&&!loadWMode）直接回 sIDLE、不发 done，
  // 故上方 done 门控捕捉不到。追加 hasMx-gated 的 err 直连，使 STATUS.err 位可被
  // 软件轮询。errState 只置不清，err 恒高直到下次 EXECUTE 复位。非 MX 配置此块
  // 编译期不生成，errFlag 行为逐字等于原实现，bit-exact。
  if (cfg.hasMx) {
    when(executor.io.err) { errFlag := true.B }
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
  // C buffer pop mux: cDma(store 写回) 或 Executor(partial sum 读取) 分时复用
  // ==========================================================================
  scratchpad.io.cPop.ready := Mux(executor.io.cStoreGate, cDma.io.bufPop.ready, executor.io.cPop.ready)

  executor.io.cPop.valid   := scratchpad.io.cPop.valid
  executor.io.cPop.payload := scratchpad.io.cPop.payload
  executor.io.cPeekDone := cDma.io.doneBlock
  cDma.io.bufPop.valid   := scratchpad.io.cPop.valid && !executor.io.cPop.ready

  // ==========================================================================
  // C buffer pop → cDma 直连（fp32 延迟规格化已下沉至 HiveCoreDmaWrOnly 内部，
  // 由 hasFp 编译期参数控制是否生成规格化硬件）
  // ==========================================================================
  cDma.io.bufPop.payload := scratchpad.io.cPop.payload
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
  executor.io.dmaCBusy := cDma.io.busy

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
  // P3: MX Scale 通道（仅 hasMx 生成；非 MX 配置不例化 DMA/FIFO、不暴露 dma3/dma4）
  //   - scaleADma/scaleBDma 复用 HiveCoreDmaRdOnly。基址经 regFile 视图重写：
  //     DMA isA=true 内部读 regFile.aAddr → 重写 regs(3)=scaleAAddr；
  //     isA=false 内部读 regFile.bAddr → 重写 regs(4)=scaleBAddr。
  //   - 遍历/地址步长复用共享 calcConfig（镜像 A/B 的 K-块 tiling）。
  //     * scaleADma（isA=true）行数天然镜像 aDma（m×kTile×nTile），与 Executor
  //       每激活行 pop 的 scaleA 一一对应。
  //     * scaleBDma（isA=false）复用 B 遍历；Executor 仅在 K-块起始 pop scaleB，
  //       FIFO 反压自然节流上游。
  //   - 【MX 必选】start 加运行期 isMx(regFile.aFmt) 门控：仅 MX 运行时启动；
  //     非 MX 运行（fmt 非 MX 枚举）恒不启动，不向外部发请求（非 MX harness 不
  //     驱动 dma3/dma4 也天然安全）。判据与 Executor err/scale 块同用 aFmt。
  // ==========================================================================
  if (cfg.hasMx) {
    val mxRun = DataFormat.isMx(regFile.aFmt)
    // scaleA DMA：isA=true，基址重写为 scaleAAddr
    val scaleARegFile = WireInit(regFile)
    scaleARegFile.regs(3) := regFile.scaleAAddr
    val scaleADma = aDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleADma.io.isA        := true.B
    scaleADma.io.start      := executePulse & mxRun
    scaleADma.io.regFile    := scaleARegFile
    scaleADma.io.calcConfig := calcConfig
    io.dma3Ext.get <> scaleADma.io.dmaExtRdIF

    // scaleB DMA：isA=false，基址重写为 scaleBAddr
    val scaleBRegFile = WireInit(regFile)
    scaleBRegFile.regs(4) := regFile.scaleBAddr
    val scaleBDma = bDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleBDma.io.isA        := false.B
    scaleBDma.io.start      := executePulse & mxRun
    scaleBDma.io.regFile    := scaleBRegFile
    scaleBDma.io.calcConfig := calcConfig
    io.dma4Ext.get <> scaleBDma.io.dmaExtRdIF

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
