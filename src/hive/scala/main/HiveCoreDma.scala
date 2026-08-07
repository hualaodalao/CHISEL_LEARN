/** HiveCore 轻量级 DMA 引擎。
  *
  * 仅支持线性数据搬运（push/pop queue），由内部 Executor FSM 直接控制。
  * 每个 DMA 实例通过 bufWidth 参数指定 buffer 接口位宽：
  *   - DMA0: bufWidth = cfg.totalN * cfg.aEffW  (A buffer)
  *   - DMA1: bufWidth = cfg.totalN * cfg.cEffW  (C buffer)
  */

import chisel3._
import chisel3.util._

/*
当位于MNK循环的时候，

*/


class HiveCoreDmaRdOnly(cfg: HiveCoreConfig) extends Module {
  val io = IO(new Bundle {
    val isA = Input(Bool())
    val dmaIntIF =  HiveCoreDMAIntIF(cfg)
    val dmaExtRdIF = HiveCoreDMAExtReadOnlyIF(cfg)
    val calcConfig = Input(HiveCoreExePreCalcConfig(cfg))
    val regFile = Input(HiveCoreRegs(cfg))
    val bufPush = Stream(UInt(cfg.dmaDataWidth.W))
    val bufAvailability = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))
  })

  // ==========================================================================
  // GLBFSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sREQ_EXT  = 1.U(2.W)
  val sTRANSFER = 2.U(2.W)
  val sDONE     = 3.U(2.W)    

  // ==========================================================================
  // GLBFSM 状态编码
  // ==========================================================================
  val dmaReqState = RegInit(sIDLE)
  val dmaRdAddr = RegInit(0.U(cfg.addrWidth.W))
  val dmaRdTileRowCnt = RegInit(0.U(log2up(cfg.totalN)))
  val dmaLoop1Cnt = RegInit(0.U(cfg.mnkWidth - log2up(cfg.totalN)+1))
  val dmaLoop2Cnt = RegInit(0.U(cfg.mnkWidth - log2up(cfg.totalN)+1))
  val dmaLoop1CntTarget = RegInit(0.U(cfg.mnkWidth - log2up(cfg.totalN)+1))
  val dmaLoop2CntTarget = RegInit(0.U(cfg.mnkWidth - log2up(cfg.totalN)+1))  
  val dmaNCnt = RegInit(0.U(log2up(cfg.totalN)))
  io.dmaExtRdIF.req.addr := dmaRdAddr
  
  switch(dmaReqState) {
    is(sIDLE) {
      dmaLoop1Cnt := 0.U
      dmaLoop2Cnt := 0.U
      when(io.dmaIntIF.start){
        when(io.isA){
          dmaLoop1CntTarget := io.calcConfig.mTile * cfg.totalN.U
          dmaLoop2CntTarget := io.calcConfig.kTile
        }.otherwise{
          when(io.regFile.loopMode === HiveCoreLoopMode.MKN){
            dmaLoop1CntTarget := io.calcConfig.kTile
            dmaLoop2CntTarget := io.calcConfig.nTile
          }.otherwise{
            dmaLoop1CntTarget := io.calcConfig.nTile
            dmaLoop2CntTarget := io.calcConfig.kTile
          }
        }
        dmaReqState := sREQ_EXT
      }
    }
    is(sREQ_EXT) {
      io.dmaExtRdIF.req.valid := io.bufAvailability =/= 0.U
      when(io.dmaExtRdIF.req.fire){
        when(io.isA){
          //A矩阵
          when(dmaLoop1Cnt < dmaLoop1CntTarget){
            dmaRdAddr := dmaRdAddr + io.calcConfig.aColAddressOffset
            dmaLoop1Cnt := dmaLoop1Cnt + 1.U
          }.otherwise{
            dmaLoop1Cnt := 0.U
            when(dmaLoop2Cnt < dmaLoop2CntTarget){
              dmaRdAddr := dmaRdAddr + io.calcConfig.aColAddressOffset + (cfg.totalN * cfg.aW/8).U
              dmaLoop2Cnt := dmaLoop2Cnt + 1.U
            }.otherwise{
              dmaReqState := sIDLE
            }
          }
        }.otherwise{
          //B矩阵
          

        }
        
      }
    }

  }
  
}


class HiveCoreDmaEngine(cfg: HiveCoreConfig, bufWidth: Int) extends Module {

  val io = IO(new Bundle {
    // 内部控制信号（由 Executor FSM 直接驱动）
    val start     = Input(Bool())       // 脉冲启动传输
    val isLoad    = Input(Bool())       // true=外部→buffer, false=buffer→外部
    val addr      = Input(UInt(cfg.addrWidth.W))
    val len       = Input(UInt(16.W))   // beat 数
    val busy      = Output(Bool())
    val done      = Output(Bool())      // 脉冲，完成一次传输

    // 外部数据接口
    val extReadData  = Input(UInt(cfg.extDataWidth.W))
    val extReadValid = Input(Bool())
    val extReadReady = Output(Bool())   // 反压信号：buffer 满时拒绝外部数据
    val extWriteData = Output(UInt(cfg.extDataWidth.W))
    val extWriteValid = Output(Bool())
    val extWriteReady = Input(Bool())
    val extAddr  = Output(UInt(cfg.addrWidth.W))
    val extLen   = Output(UInt(16.W))
    val extReq   = Output(Bool())
    val extGrant = Input(Bool())
    val extIsWrite = Output(Bool())

    // Scratchpad buffer 接口（push = 写入 buffer, pop = 从 buffer 读）
    val bufPush      = master(Stream(UInt(bufWidth.W)))
    val bufPop       = slave(Stream(UInt(bufWidth.W)))
    val bufAvailability = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))
    val bufOccupancy    = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
  })

  // ==========================================================================
  // FSM 状态编码
  // ==========================================================================
  val sIDLE     = 0.U(2.W)
  val sREQ_EXT  = 1.U(2.W)
  val sTRANSFER = 2.U(2.W)
  val sDONE     = 3.U(2.W)

  val state = RegInit(sIDLE)

  // ==========================================================================
  // 内部寄存器
  // ==========================================================================
  val addrReg    = Reg(UInt(cfg.addrWidth.W))
  val lenReg     = Reg(UInt(16.W))
  val isLoadReg  = Reg(Bool())

  val beatCounter = Reg(UInt(16.W))

  // ==========================================================================
  // 默认输出
  // ==========================================================================
  io.done          := false.B
  io.extAddr       := 0.U
  io.extLen        := 0.U
  io.extReq        := false.B
  io.extIsWrite    := false.B
  io.extReadReady  := false.B
  io.extWriteData  := 0.U
  io.extWriteValid := false.B

  io.bufPush.valid   := false.B
  io.bufPush.payload := 0.U
  io.bufPop.ready    := false.B

  io.busy := state =/= sIDLE

  // ==========================================================================
  // FSM 逻辑
  // ==========================================================================
  switch(state) {

    // --- sIDLE: 等待启动脉冲 ---
    is(sIDLE) {
      when(io.start) {
        isLoadReg   := io.isLoad
        addrReg     := io.addr
        lenReg      := io.len
        beatCounter := io.len
        state       := sREQ_EXT
      }
    }

    // --- sREQ_EXT: 向外部发起请求（检查 buffer 空间/数据量） ---
    is(sREQ_EXT) {
      // Load 模式: 确保 buffer 有足够空间接收整个 burst
      // Store 模式: 确保 buffer 有足够数据发送整个 burst
      val canRequest = Mux(isLoadReg,
        io.bufAvailability >= lenReg,
        io.bufOccupancy >= lenReg)

      when(canRequest) {
        io.extReq     := true.B
        io.extAddr    := addrReg
        io.extLen     := lenReg
        io.extIsWrite := !isLoadReg

        when(io.extGrant) {
          state := sTRANSFER
        }
      }
    }

    // --- sTRANSFER: 数据搬运 ---
    is(sTRANSFER) {
      when(isLoadReg) {
        // Load 模式：外部 → buffer
        // 仅在 buffer 能接收且还有剩余 beat 时才 ready
        io.extReadReady := io.bufPush.ready && (beatCounter =/= 0.U)
        when(io.extReadValid && io.bufPush.ready && (beatCounter =/= 0.U)) {
          io.bufPush.valid   := true.B
          io.bufPush.payload := io.extReadData
          beatCounter := beatCounter - 1.U
        }
      }.otherwise {
        // Store 模式：buffer → 外部
        when((io.bufOccupancy > 0.U) && io.extWriteReady) {
          io.bufPop.ready    := true.B
          io.extWriteData    := io.bufPop.payload
          io.extWriteValid   := io.bufPop.valid
          when(io.bufPop.fire) {
            beatCounter := beatCounter - 1.U
          }
        }
      }

      // 传输完成
      when(beatCounter === 0.U) {
        state := sDONE
      }
    }

    // --- sDONE: 发送完成脉冲 ---
    is(sDONE) {
      io.done := true.B
      state   := sIDLE
    }
  }
}
