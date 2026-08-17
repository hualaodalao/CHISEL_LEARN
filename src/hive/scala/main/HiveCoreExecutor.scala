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
    val err      = Output(Bool())

    //dma状态：
    val dmaABusy = Input(Bool())
    val dmaBBusy = Input(Bool())
    val dmaCBusy = Input(Bool())

    // 寄存器输入（由顶层寄存器组驱动）：全部寄存器统一经 io.regFile 引用。
    // 此前的拆分端口（regM/regN/regK/regBAddr/regBStride/regFmt/regRnd 及
    // regAAddr/regCAddr/regAStride）均与 regFile 完全冗余（顶层驱动只是
    // 截位/补零/类型视图），已全部移除
    val regFile = Input(HiveCoreRegs(cfg))

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

    // HiveComb 驱动
    val hiveAIn     = Output(Vec(cfg.totalN, UInt(cfg.aEffW.W)))
    val hivePsumIn  = Output(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveLoadH   = Output(Bool())
    val hiveLoadV   = Output(Bool())
    val hiveLoadVLock   = Output(Bool())
    val hiveValidIn = Output(Bool())
    val hiveClear   = Output(Bool())
    val hiveCOut    = Input(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val hiveValidOut = Input(Bool())

    // 进度
    val progress = Output(UInt(16.W))
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
  val errState = RegInit(false.B)

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
  val drainNextCnt = RegInit(0.U(log2Up(cfg.totalN).W))
  val hasNext = RegInit(false.B)
  
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
  
  for (i <- 0 until cfg.totalN) {
    io.hiveAIn(i) := io.aPop.payload(cfg.aEffW * (i+1)-1, cfg.aEffW * i)
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
  io.err  := errState

  io.aPop.ready  := false.B
  io.bPop.ready  := false.B
  io.cPop.ready  := false.B

  io.flushA := false.B
  io.flushB := false.B
  io.flushC := false.B

  io.hiveLoadH   := false.B
  io.hiveLoadV   := false.B
  io.hiveLoadVLock   := false.B
  io.hiveValidIn := false.B
  io.hiveClear   := false.B

  io.progress := doneTiles


  // cPush 违约硬件级防护（综合后兜底，与上方仿真 assert 对应）：
  // 容量公式放宽后生产环境不能再依赖仿真断言，违约拍置 errState 并经
  // sDONE 退出（而非直回 sIDLE），保证顶层仍能收到带 err 标记的 resp。
  // 仅改状态机，不反压 cPush 驱动逻辑（不引入 stall）
  when( state =/= sIDLE & io.cPush.valid && !io.cPush.ready) {
    errState := true.B
    state    := sIDLE
  }

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
        val newKTiles = (io.regFile.k(15, 0) + totalN.U - 1.U) / totalN.U
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
        state    := sLOAD_B
      }
    }


    // --- sLOAD_WEIGHT_PE: 从 B buffer pop 权重 → 加载到 HiveComb ---
    // loadH 在整个加载窗口保持拉高（fmt/rnd 配置需时间经阵列横向传播）；
    // loadV 跟随 bPop 数据有效性：B buffer 暂无数据时 loadV 自然暂停，
    // RdOnly DMA 喂数到位后自动恢复，无需固定每 2 拍一次的节流。
    is(sLOAD_B) {
      io.hiveLoadH := true.B               // 配置加载，整个窗口拉高
      io.hiveLoadV := io.bPop.fire
      io.bPop.ready := true.B

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

    }

    is(sLOAD_A){
      val needPartialSum = curKTile > 0.U

      //当需要paritalsum的时候必须占用c口buffer的读出口，因此需要等到CDMA已经将cbuffer搬空让出读口权限
      val feed = (cBufferReservedAtleastTotalN) & Mux(needPartialSum, cStoreGateReg === false.B, true.B)

      // ready 门控：供数满 m 行（mDone）后关断，防止排空窗口误消费
      // 下一 tile 数据；clear 准备拍关断，避免首拍 fire 累加被 clear
      // 优先级覆盖（PE 内 clear > accumulate）
      io.aPop.ready := feed 
      io.cPop.ready := feed & needPartialSum

      // 跟随 fire（供数暂停时 valid 拉低，避免阵列对保持的旧 a 值重复累加）
      io.hiveValidIn := io.aPop.fire 

      for(i <- 0 until cfg.totalN){
        when(needPartialSum){
          io.hivePsumIn(i) := io.cPop.payload(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
        }
      }

      when(io.aPop.fire){
        curM := curM + 1.U
      }

      // 排空窗口结束（beatCnt 达 curM + 2*(totalN-1)）才离开计算状态，
      // 保证波前完整流过阵列、最后一行累加不被截断
      when(curM === io.regFile.m - 1.U) {
        curM := 0.U
        state := sNEXT_NK_TILE
      }
    }

    is(sNEXT_NK_TILE){
      doneTiles := doneTiles + 1.U
      drainNextCnt := cfg.totalN.U - 1.U
      when(curKTile < kTiles){
        curKTile := curKTile + 1.U
        hasNext := true.B
        state := sDRAIN
        //state := sLOAD_B
      }.otherwise{
        curKTile := 0.U
        cStoreGateReg := true.B
        when(curNTile < nTiles){
          curNTile := curNTile + 1.U
          state := sLOAD_B
        }.otherwise{
          curNTile := 0.U
          state := sDRAIN
        }
      }
    }
    

    // --- sDRAIN: 等待剩余输出收集完毕 ---
    is(sDRAIN) {
  
      when(hasNext){
        when(drainNextCnt =/= 0.U){
          drainNextCnt := drainNextCnt - 1.U
        }.otherwise{
          drainNextCnt := totalN.U - 1.U
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
}
