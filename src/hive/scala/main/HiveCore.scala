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
  *   - DMA1: C buffer 数据搬运
  *   - Scratchpad: A buffer + C buffer
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
  val executor   = Module(new HiveCoreExecutor(cfg))
  val hiveComb   = Module(new HiveComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cW, cfg.supportedFmts))

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
      is(HiveCoreOp.REG_WRITE1 | HiveCoreOp.REG_WRITE2) {
        val addr0 = cmd.regAddr0(3, 0) 
        when(cmd.regAddr0 < cfg.registerNumRW.U) { regFile.regs(addr0) := cmd.data0 }  // 0x00-0x09 可写
        when(cmd.regAddr0 == 0x08.U) {
              when(cmd.data0(0)) { doneFlag := false.B }  // clear_done
        }
        // 生成响应
        respValid := true.B
        respData  := 0.U
        respDone  := false.B
        respErr   := false.B
      }
      is(HiveCoreOp.REG_WRITE2){
        val addr1 = cmd.regAddr1(3, 0) 
        when(cmd.regAddr1 < cfg.registerNumRW.U) { regFile.regs(addr1) := cmd.data1 }  // 0x00-0x09 可写
        when(cmd.regAddr1 == 0x08.U) {  
          when(cmd.data1(0)) { doneFlag := false.B }  // clear_done
        }
      }
      is(HiveCoreOp.REG_READ) {
        // 读取寄存器
        val addr = cmd.regAddr
        val statusReg = Cat(executor.io.progress, 0.U(13.W), errFlag, doneFlag, executor.io.busy)
        val readVal = regFile.regs(addr)
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
  executor.io.execute := io.cmd.fire && io.cmd.payload.op === HiveCoreOp.EXECUTE
  executor.io.regFile := regFile
  executor.io.regN       := regFile(1)

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
  // C buffer push mux: DMA1(load) 或 Executor(GEMM结果)
  // ==========================================================================
  scratchpad.io.cPush.valid   := executor.io.cPush.valid || dma1.io.bufPush.valid
  scratchpad.io.cPush.payload := Mux(executor.io.cPush.valid,
                                     executor.io.cPush.payload,
                                     dma1.io.bufPush.payload)
  executor.io.cPush.ready     := scratchpad.io.cPush.ready
  dma1.io.bufPush.ready       := scratchpad.io.cPush.ready && !executor.io.cPush.valid

  // ==========================================================================
  // C buffer pop mux: DMA1(store) 或 Executor(权重加载/partial sum)
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
  io.status.cOccupancy := scratchpad.io.cOccupancy
}
