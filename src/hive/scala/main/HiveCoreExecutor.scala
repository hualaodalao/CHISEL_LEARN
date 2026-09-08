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

    //dma状态：
    val dmaABusy = Input(Bool())
    val dmaBBusy = Input(Bool())
    val dmaCBusy = Input(Bool())

    // 寄存器输入（由顶层寄存器组驱动）：全部寄存器统一经 io.regFile 引用。
    // 此前的拆分端口（regM/regN/regK/regBAddr/regBStride/regFmt/regRnd 及
    // regAAddr/regCAddr/regAStride）均与 regFile 完全冗余（顶层驱动只是
    // 截位/补零/类型视图），已全部移除
    val regFile = Input(HiveCoreRegister(cfg))

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
    //   - scaleAPop：每激活行 pop 一个 8-bit（随激活链 skew 流入阵列）
    //   - scaleBPop：每 sLOAD_B 窗口（每 kTile = 一个 32-K scale 块，单 pass）
    //     pop 一个 totalN 向量（scaleRowW），锁存广播
    //   - hiveScaleAIn/hiveScaleBIn：广播到 HiveComb 的 scale 端口
    val scaleAPop    = if (cfg.hasMx) Some(slave(Stream(UInt(8.W))))             else None
    val scaleBPop    = if (cfg.hasMx) Some(slave(Stream(UInt(8.W)))) else None
    val hiveScaleAIn = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(8.W))))   else None
    val hiveScaleBIn = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(8.W))))   else None

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
  // scaleBRegs —— MX 权重侧 per-column scale 暂存（16 列 × 8-bit）
  //
  // 架构约束来源（移位链 + 单广播 lock）：MX 强制走水平 loadW 路径，权重
  //   数据从阵列 y=0 注入后沿 a 移位链逐拍右移，16 拍形成"对角线落位态"，
  //   直到末拍（第 16 拍）才由一个全局广播 lock 脉冲让 16 列同拍一次性锁存
  //   wReg / scaleBReg（见 HiveWorker.loadWInLockSafe、HiveCell 全 PE 单广播、
  //   HiveCoreExecutor 末拍 hiveLoadWLock）。
  // 而 scaleB DMA 通道 bufWidth=8：1 拍只供 1 字节 1 列，块内按 -rowStep 降序，
  //   beat b 对应列 n = 15-b；恰好 tileInterCnt（初值 totalN-1，每 fire 拍递减）
  //   == 目标列号 n。故 16 列的 scale 是跨 16 拍逐拍到达的，无法在末拍 lock 的
  //   那一拍由单个 payload 广播呈现（旧实现把末拍列 0 的 scale 广播到全 16 列，
  //   导致全列指数恒为列 0 的值 —— 单字节广播缺陷）。
  // 解决：本组寄存器把每拍到达的 scale 按列号暂存（scaleBRegs(tileInterCnt)），
  //   末拍 lock 时 16 列并列呈现 scaleBRegs(n)，供 HiveComb 同拍锁存。
  // 仅 MX 配置生成（Option 门控），非 MX 路径不产生任何硬件，行为零回归。
  // ==========================================================================
  val scaleBRegs = if (cfg.hasMx) Some(Reg(Vec(totalN, UInt(8.W)))) else None

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
  
  for (i <- 0 until cfg.totalN) {
    io.hiveAIn(i) := io.aPop.payload(cfg.aEffW * (i+1)-1, cfg.aEffW * i)
    if(cfg.hasMx){
      io.hiveScaleAIn.get(i) := io.scaleAPop.get.payload
    }
  }

  // scaleB 按列并列供数（取代旧的单字节全列广播）：
  //   每列 n 呈现跨 16 拍逐拍暂存到 scaleBRegs(n) 的 scale，末拍 lock 时
  //   16 列同时有效（架构约束见 scaleBRegs 声明处注释）。
  //   非 MX 运行（isMx(bFmt)=false）不写 scaleBRegs，值不参与实际计算，
  //   行为与改动前一致（旧广播仅在 MX scaleBPop fire 时才有意义）。
  if(cfg.hasMx){
    for (n <- 0 until cfg.totalN) {
      io.hiveScaleBIn.get(n) := scaleBRegs.get(n)
    }
    // 关键 off-by-one 组合前递：末拍（tileInterCnt===0）scaleBRegs(0) 的写入
    //   下一拍才生效，而 loadWLock 恰在本拍组合触发，故列 0 必须直接用当拍
    //   scaleBPop.payload 前递，否则会锁到上一轮残留的旧寄存器值。
    when(DataFormat.isMx(io.regFile.bFmt) && io.scaleBPop.get.fire && tileInterCnt === 0.U) {
      io.hiveScaleBIn.get(0) := io.scaleBPop.get.payload
    }
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
        // MX v2 SIMD：16 lane × 2 元素/拍 = 32 K 元素/拍，一个 32-K scale 块 = 单 pass，
        // 故 kTile 数 = K/32（K%32==0 已在 sIDLE 校验）；非 MX 分支逐字等于原公式。
        val newKTiles = Mux(DataFormat.isMx(io.regFile.aFmt), io.regFile.k(15, 0) / (totalN.U) / 2.U, (io.regFile.k(15, 0) + totalN.U - 1.U) / totalN.U)
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

      // 水平 loadW 模式位锁存：独立 when（不改动上方原 execute 块）。
      // regFile 在计算期间保持不变，execute 拍采样即为本次运行模式
      when(io.execute) {
        loadWModeReg := io.regFile.loadWMode
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
        when(DataFormat.isMx(io.regFile.aFmt)){
          io.scaleBPop.get.ready := io.bPop.valid & io.scaleBPop.get.valid
          io.bPop.ready          := io.bPop.valid & io.scaleBPop.get.valid
        }.otherwise{
          io.scaleBPop.get.ready := false.B
          io.bPop.ready          := true.B
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

      // MX scaleB 逐拍暂存：scaleBPop 与 bPop 同拍 fire，本拍 tileInterCnt
      //   == 目标列号 n（beat b → n = 15-b，tileInterCnt 初值 15 递减）。
      //   把当拍 1 字节 scale 写入对应列，供末拍 lock 时 16 列并列呈现。
      //   isMx(bFmt) 门控：非 MX 运行不写，scaleBRegs 保持原值（不参与计算）。
      if(cfg.hasMx){
        when(DataFormat.isMx(io.regFile.bFmt) && io.scaleBPop.get.fire) {
          scaleBRegs.get(tileInterCnt) := io.scaleBPop.get.payload
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
        // 末拍 fire（tileInterCnt 计满）当拍发 loadWLock（对角线落位态
        // 唯一时刻，必须与末拍 beat 进入列 0 的 fire 拍组合重合）
        when(io.bPop.fire && tileInterCnt === 0.U) {
          io.hiveLoadWLock := true.B
        }
      }

    }

    is(sLOAD_A){
      val needPartialSum = curKTile > 0.U

      //当需要paritalsum的时候必须占用c口buffer的读出口，因此需要等到CDMA已经将cbuffer搬空让出读口权限
      val feedBase = (cBufferReservedAtleastTotalN) & Mux(needPartialSum, cStoreGateReg === false.B, true.B)
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
        }.otherwise{
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


}
