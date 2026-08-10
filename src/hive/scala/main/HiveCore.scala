/** HiveCore — 脉动阵列加速器顶层集成模块（寄存器配置 + 一键执行架构）
  *
  * 外部接口极简，仅 3 种命令:
  *   - REG_WRITE: 写入 2 个 32-bit 寄存器
  *   - EXECUTE: 触发矩阵计算
  *   - REG_READ: 读取 1 个寄存器
  *
  * 内部组件:
  *   - Register File: 12 个 32-bit 配置/状态寄存器
  *   - Executor: 自动 Tiling FSM
  *   - DMA0: A buffer 数据搬运
  *   - DMA1: C buffer 结果写回
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
    val dma0Ext = new DmaExtIO(cfg)
    val dma1Ext = new DmaExtIO(cfg)
    // B 权重专用只读 DMA 外部通道（cmd: 读地址流; rsp: 读数据流）
    val dma2Ext = HiveCoreDMAExtReadOnlyIF(cfg)
    val status = Output(new HiveCoreStatus(cfg))
  })

  // ==========================================================================
  // 寄存器组定义
  // ==========================================================================
  // 地址  名称
  // 0x00  REG_M         M 维度
  // 0x01  REG_N         N 维度
  // 0x02  REG_K         K 维度
  // 0x03  REG_FMT_RND   [7:0]=fmt, [15:8]=rnd
  // 0x04  REG_A_ADDR    A 矩阵基地址
  // 0x05  REG_B_ADDR    B 矩阵基地址
  // 0x06  REG_C_ADDR    C 矩阵基地址
  // 0x07  REG_A_STRIDE  A 行步长
  // 0x08  REG_B_STRIDE  B 行步长
  // 0x09  REG_C_STRIDE  C 行步长
  // 0x0A  REG_STATUS    [0]=busy, [1]=done, [2]=err, [31:16]=progress (只读)
  // 0x0B  REG_CONTROL   [0]=clear_done (写1清除)

  //val regFile = RegInit(VecInit(Seq.fill(12)(0.U(32.W))))
  val regFile = RegInit(0.U.asTypeOf(HiveCoreRegister(cfg)))

  // 状态寄存器辅助
  val doneFlag = RegInit(false.B)
  val errFlag  = RegInit(false.B)

  // ==========================================================================
  // 子模块例化
  // ==========================================================================
  val scratchpad = Module(new HiveCoreScratchpad(cfg))
  val dma0       = Module(new HiveCoreDmaEngine(cfg, cfg.totalN * cfg.aEffW))
  val dma1       = Module(new HiveCoreDmaEngine(cfg, cfg.totalN * cfg.cEffW))
  // B 权重专用只读 DMA：bufWidth = 一整行权重（totalN*bW），深度 = bBufferDepth
  val bDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.bW, bufDepth = cfg.bBufferDepth))
  val executor   = Module(new HiveCoreExecutor(cfg))
  val hiveComb   = Module(new HiveComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cW, cfg.supportedFmts))

  // ==========================================================================
  // calcConfig 生产者（喂给 bDma）：从 regFile 推导 tile 数与六个地址偏移。
  // 采用组合逻辑：bDma 在 start（EXECUTE 脉冲）当拍采样，regFile 在计算期间
  // 保持不变，因此组合驱动即可保证采样到 execute 时刻的正确值
  // （若用 Reg 锁存，start 当拍会采样到锁存前的旧值）。
  // 偏移语义与 Executor 的 aTileAddr/bTileAddr/cStoreAddr 保持一致（字节地址）：
  //   aRow = aStride（行步长）           aCol = totalN * (aEffW/8)（K tile 间列偏移）
  //   bRow = bStride（沿 K 行步长）      bCol = totalN * (aEffW/8)（N tile 间列偏移，
  //                                        与 Executor bTileAddr 用 aEffW/8 作元素字节宽一致）
  //   cRow = cStride                     cCol = totalN * (cEffW/8)
  // ==========================================================================
  val executePulse = io.cmd.fire && io.cmd.payload.op === HiveCoreOp.EXECUTE
  val calcConfig = Wire(HiveCoreExePreCalcConfig(cfg))
  calcConfig.mTile := (regFile.m + cfg.totalN.U - 1.U) / cfg.totalN.U
  calcConfig.nTile := (regFile.n + cfg.totalN.U - 1.U) / cfg.totalN.U
  calcConfig.kTile := (regFile.k + cfg.totalN.U - 1.U) / cfg.totalN.U
  calcConfig.aRowAddressOffset := regFile.aStride
  calcConfig.aColAddressOffset := cfg.totalN.U * (cfg.aEffW / 8).U
  calcConfig.bRowAddressOffset := regFile.bStride
  calcConfig.bColAddressOffset := cfg.totalN.U * (cfg.aEffW / 8).U
  calcConfig.cRowAddressOffset := regFile.cStride
  calcConfig.cColAddressOffset := cfg.totalN.U * (cfg.cEffW / 8).U

  // ==========================================================================
  // bDma 控制接线：execute 起始拍与 Executor 同步启动，自主扫完整个 B 矩阵
  // ==========================================================================
  bDma.io.isA         := false.B
  bDma.io.start       := executePulse
  bDma.io.regFile     := regFile
  bDma.io.calcConfig  := calcConfig
  // bDma.io.done/busy/err 目前无消费者，留空（FIRRTL 会优化掉）
  // 外部只读通道直连顶层 dma2Ext
  io.dma2Ext <> bDma.io.dmaExtRdIF

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
        when(cmd.reg0Addr < cfg.registerNumRW.U) { regFile.regs(addr0) := cmd.data0 }  // 0x00-0x07 可写
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
        when(cmd.reg0Addr < cfg.registerNumRW.U) { regFile.regs(addr0) := cmd.data0 }  // 0x00-0x07 可写
        val addr1 = cmd.reg1Addr(3, 0) 
        when(cmd.reg1Addr < cfg.registerNumRW.U) { regFile.regs(addr1) := cmd.data1 }  // 0x00-0x07 可写
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

  // EXECUTE 完成时生成响应
  when(executor.io.done) {
    doneFlag  := true.B
    respValid := true.B
    respData  := 0.U
    respDone  := true.B
    respErr   := false.B
  }

  // ==========================================================================
  // Executor 连接寄存器组
  // ==========================================================================
  executor.io.execute := executePulse
  executor.io.regFile := regFile
  // 独立寄存器视图：从 regFile 拆分驱动
  executor.io.regM       := regFile.m(15, 0)
  executor.io.regN       := regFile.n(15, 0)
  executor.io.regK       := regFile.k(15, 0)
  executor.io.regAAddr   := regFile.aAddr
  executor.io.regBAddr   := regFile.bAddr
  executor.io.regCAddr   := regFile.cAddr
  executor.io.regAStride := regFile.aStride.pad(16)
  executor.io.regBStride := regFile.bStride.pad(16)
  executor.io.regFmt     := regFile.fmt
  executor.io.regRnd     := regFile.rnd

  // ==========================================================================
  // DMA0 ↔ Executor + 外部总线
  // ==========================================================================
  dma0.io.start  := executor.io.dma0Start
  dma0.io.isLoad := executor.io.dma0IsLoad
  dma0.io.addr   := executor.io.dma0Addr
  dma0.io.len    := executor.io.dma0Len
  executor.io.dma0Busy := dma0.io.busy
  executor.io.dma0Done := dma0.io.done

  // DMA0 外部接口
  dma0.io.extReadData  := io.dma0Ext.readData.payload
  dma0.io.extReadValid := io.dma0Ext.readData.valid
  io.dma0Ext.readData.ready := dma0.io.extReadReady
  io.dma0Ext.writeData.valid   := dma0.io.extWriteValid
  io.dma0Ext.writeData.payload := dma0.io.extWriteData
  dma0.io.extWriteReady := io.dma0Ext.writeData.ready
  io.dma0Ext.addr    := dma0.io.extAddr
  io.dma0Ext.len     := dma0.io.extLen
  io.dma0Ext.req     := dma0.io.extReq
  dma0.io.extGrant   := io.dma0Ext.grant
  io.dma0Ext.isWrite := dma0.io.extIsWrite

  // ==========================================================================
  // DMA1 ↔ Executor + 外部总线
  // ==========================================================================
  dma1.io.start  := executor.io.dma1Start
  dma1.io.isLoad := executor.io.dma1IsLoad
  dma1.io.addr   := executor.io.dma1Addr
  dma1.io.len    := executor.io.dma1Len
  executor.io.dma1Busy := dma1.io.busy
  executor.io.dma1Done := dma1.io.done

  // DMA1 外部接口
  dma1.io.extReadData  := io.dma1Ext.readData.payload
  dma1.io.extReadValid := io.dma1Ext.readData.valid
  io.dma1Ext.readData.ready := dma1.io.extReadReady
  io.dma1Ext.writeData.valid   := dma1.io.extWriteValid
  io.dma1Ext.writeData.payload := dma1.io.extWriteData
  dma1.io.extWriteReady := io.dma1Ext.writeData.ready
  io.dma1Ext.addr    := dma1.io.extAddr
  io.dma1Ext.len     := dma1.io.extLen
  io.dma1Ext.req     := dma1.io.extReq
  dma1.io.extGrant   := io.dma1Ext.grant
  io.dma1Ext.isWrite := dma1.io.extIsWrite

  // ==========================================================================
  // DMA0 ↔ Scratchpad (A buffer)
  // ==========================================================================
  scratchpad.io.aPush.valid   := dma0.io.bufPush.valid
  scratchpad.io.aPush.payload := dma0.io.bufPush.payload
  dma0.io.bufPush.ready       := scratchpad.io.aPush.ready

  dma0.io.bufPop.valid   := false.B
  dma0.io.bufPop.payload := 0.U

  // ==========================================================================
  // C buffer push：重构后仅 Executor 写结果（权重 load 已移走，dma1 只做 store）
  // ==========================================================================
  scratchpad.io.cPush.valid   := executor.io.cPush.valid
  scratchpad.io.cPush.payload := executor.io.cPush.payload
  executor.io.cPush.ready     := scratchpad.io.cPush.ready
  dma1.io.bufPush.ready       := false.B  // dma1 不再向 C buffer push（权重 load 职责已移除）

  // ==========================================================================
  // B buffer 数据通路（无 mux：bDma RdOnly 独占写入，Executor 独占读出）
  // ==========================================================================
  scratchpad.io.bPush.valid   := bDma.io.bufPush.valid
  scratchpad.io.bPush.payload := bDma.io.bufPush.payload
  bDma.io.bufPush.ready       := scratchpad.io.bPush.ready
  bDma.io.bufAvailability     := scratchpad.io.bAvailability

  executor.io.bPop.valid   := scratchpad.io.bPop.valid
  executor.io.bPop.payload := scratchpad.io.bPop.payload
  scratchpad.io.bPop.ready := executor.io.bPop.ready
  executor.io.bOccupancy   := scratchpad.io.bOccupancy

  // ==========================================================================
  // C buffer pop mux: DMA1(store 写回) 或 Executor(partial sum 读取) 分时复用
  // ==========================================================================
  scratchpad.io.cPop.ready := executor.io.cPop.ready || dma1.io.bufPop.ready

  executor.io.cPop.valid   := scratchpad.io.cPop.valid
  executor.io.cPop.payload := scratchpad.io.cPop.payload

  dma1.io.bufPop.valid   := scratchpad.io.cPop.valid && !executor.io.cPop.ready
  dma1.io.bufPop.payload := scratchpad.io.cPop.payload

  // ==========================================================================
  // Executor ↔ Scratchpad (A buffer pop)
  // ==========================================================================
  executor.io.aPop.valid   := scratchpad.io.aPop.valid
  executor.io.aPop.payload := scratchpad.io.aPop.payload
  scratchpad.io.aPop.ready := executor.io.aPop.ready

  // ==========================================================================
  // Executor ↔ HiveComb
  // ==========================================================================
  hiveComb.io.aIn      := executor.io.hiveAIn
  hiveComb.io.psumIn   := executor.io.hivePsumIn
  hiveComb.io.loadHIn  := executor.io.hiveLoadH
  hiveComb.io.loadVIn  := executor.io.hiveLoadV
  hiveComb.io.loadVLock := executor.io.hiveLoadVLock
  hiveComb.io.validIn  := executor.io.hiveValidIn
  hiveComb.io.fmtIn    := executor.io.hiveFmtIn
  hiveComb.io.rndIn    := executor.io.hiveRndIn
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
  // DMA buffer 感知信号
  // ==========================================================================
  dma0.io.bufAvailability := scratchpad.io.aAvailability
  dma0.io.bufOccupancy    := 0.U

  dma1.io.bufAvailability := scratchpad.io.cAvailability
  dma1.io.bufOccupancy    := scratchpad.io.cOccupancy

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
