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
    val dmaA = HiveCoreDMAExtReadOnlyIF(cfg, cfg.aExtW)
    val dmaC = new HiveCoreDMAExtWriteOnlyIF(cfg, cfg.cDmaExtW)  // P2-清理: 端口位宽直取新契约参数
    val dmaB = HiveCoreDMAExtReadOnlyIF(cfg, cfg.bExtW)
    val dmaScaleA = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, cfg.scaleAW))             else None
    val dmaScaleB = if (cfg.hasMx) Some(HiveCoreDMAExtReadOnlyIF(cfg, cfg.scaleBW)) else None
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
  val calcConfig = Module(new HiveCoreCalcConfig(cfg))
  val scratchpad = Module(new HiveCoreScratchpad(cfg))
  val aDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.aEffW, bufDepth = cfg.aBufferDepth))
  val bDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.bW, bufDepth = cfg.bBufferDepth))
  val cDma       = Module(new HiveCoreDmaWrOnly(cfg, bufWidth = cfg.totalN * cfg.cEffW, hasFp = cfg.hasFp, hasMx = cfg.hasMx))
  val executor   = Module(new HiveCoreExecutor2(cfg))
  val cvt        = Module(new HiveOutCvt(cfg))
  val hiveComb   = Module(new HiveComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cEffW, cfg.supportedFmts))
  val aDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.scaleAW,   bufDepth = cfg.scaleBufferDepth))) else None
  val bDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.scaleBW,   bufDepth = cfg.scaleBufferDepth))) else None
  val cDmaScale = if (cfg.hasMx) Some(Module(new HiveCoreDmaScaleWr(cfg))) else None


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
  executor.io.calcConfig := calcConfig.io.calcConfig

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

  // P3: scale wiring -- under an MX config both scale vectors are driven by the
  // Executor scale channels, replacing the P1 top-level constant 0. Both are
  // whole-Vec connections, so the index maps straight through: Executor lane n
  // drives HiveComb row/lane n. Neither vector is a "per-column resident"
  // quantity -- the lane index is the K-lane axis while the array column is the
  // output-N axis (see the axis note in HiveComb; the old wording here said
  // "scaleB per-column resident vector" and was stale). Under a non-MX config
  // both stay constant 0 (scale ports / DMA / FIFO are not generated at all),
  // behaviour bit-exact.
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
