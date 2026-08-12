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
    val dma1Ext = new DmaExtIO(cfg, cfg.cExtW)
    // B 权重专用只读 DMA 外部通道（cmd: 读地址流; rsp: 读数据流，位宽 = bExtW）
    val dma2Ext = HiveCoreDMAExtReadOnlyIF(cfg, cfg.bExtW)
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
  // A 矩阵只读自主 DMA：bufWidth = 一整行 A（totalN*aEffW），深度 = aBufferDepth
  val aDma       = Module(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.aEffW, bufDepth = cfg.aBufferDepth))
  // C 结果写回自主 DMA：bufWidth = 一整行 C（totalN*cEffW），storeGate 门控
  val cDma       = Module(new HiveCoreDmaWrOnly(cfg, bufWidth = cfg.totalN * cfg.cEffW))
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
  cDma.io.storeGate   := executor.io.cStoreGate
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

  // EXECUTE 完成时生成响应；err 汇聚 Executor 与三个自主 DMA 的错误标志。
  // Executor 异常路径（容量错/cPush 违约）经 sDONE 退出，io.done 照常脉冲，
  // 此处 respErr 反映真实 err，软件栈收到带 err 标记的 resp 而非永久等待
  val dmaErr = aDma.io.err || bDma.io.err || cDma.io.err
  when(executor.io.done) {
    doneFlag  := true.B
    respValid := true.B
    respData  := 0.U
    respDone  := true.B
    respErr   := executor.io.err || dmaErr
    errFlag   := executor.io.err || dmaErr
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
  aDma.io.bufAvailability     := scratchpad.io.aAvailability

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
  bDma.io.bufAvailability     := scratchpad.io.bAvailability

  executor.io.bPop.valid   := scratchpad.io.bPop.valid
  executor.io.bPop.payload := scratchpad.io.bPop.payload
  scratchpad.io.bPop.ready := executor.io.bPop.ready
  executor.io.bOccupancy   := scratchpad.io.bOccupancy

  // ==========================================================================
  // C buffer pop mux: cDma(store 写回) 或 Executor(partial sum 读取) 分时复用
  // ==========================================================================
  scratchpad.io.cPop.ready := executor.io.cPop.ready || cDma.io.bufPop.ready

  executor.io.cPop.valid   := scratchpad.io.cPop.valid
  executor.io.cPop.payload := scratchpad.io.cPop.payload

  cDma.io.bufPop.valid   := scratchpad.io.cPop.valid && !executor.io.cPop.ready
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
