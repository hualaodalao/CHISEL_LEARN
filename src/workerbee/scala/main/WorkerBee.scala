import chisel3._
import chisel3.util._

class WorkerBee(cfg: WorkerBeeConfig) extends Module {

  val io = IO(new Bundle {
    val cmd = Vec(4, slave(Stream(new WorkerBeeCmd)))
    val resp = master(Stream(new WorkerBeeResp))
    val respForTracing = master(Flow(new WorkerBeeRespForTracing))
    val dmaA = WorkerBeeDMAExtReadOnlyIF(cfg, cfg.aExtW)
    val dmaC = new WorkerBeeDMAExtWriteOnlyIF(cfg, cfg.cDmaExtW)
    val dmaB = WorkerBeeDMAExtReadOnlyIF(cfg, cfg.bExtW)
    val dmaScaleA = if (cfg.hasMx) Some(WorkerBeeDMAExtReadOnlyIF(cfg, cfg.scaleAW))             else None
    val dmaScaleB = if (cfg.hasMx) Some(WorkerBeeDMAExtReadOnlyIF(cfg, cfg.scaleBW)) else None
    val dmaCScale = if (cfg.hasMx) Some(WorkerBeeDMAExtWriteOnlyIF(cfg, cfg.cScaleRowW)) else None

    val status = Output(new WorkerBeeStatus(cfg))
    val irq = Output(Bool())
    // 性能观测信号组（纯组合电平、无内部计数；外部 PMU 按拍采样，语义见 WorkerBeePerf）
    val perf = Output(new WorkerBeePerf)
  })

  val regIf = Module(new WorkerBeeRegIf(cfg))
  regIf.io.cmd <> io.cmd
  io.resp <> regIf.io.resp
  val regFile = regIf.io.regFile

  val calcConfig = Module(new WorkerBeeCalcConfig(cfg))
  val scratchpad = Module(new WorkerBeeScratchpad(cfg))
  val aDma       = Module(new WorkerBeeDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.aEffW, bufDepth = cfg.aBufferDepth))
  val bDma       = Module(new WorkerBeeDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.bW, bufDepth = cfg.bBufferDepth))
  val cDma       = Module(new WorkerBeeDmaWrOnly(cfg, bufWidth = cfg.totalN * cfg.cEffW, hasFp = cfg.hasFp, hasMx = cfg.hasMx))
  val executor   = Module(new WorkerBeeExecutor2(cfg))
  val cvt        = Module(new WbOutCvt(cfg))
  val wbComb   = Module(new WbComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cEffW, cfg.supportedFmts))
  val aDmaScale = if (cfg.hasMx) Some(Module(new WorkerBeeDmaRdOnly(cfg, bufWidth = cfg.scaleAW,   bufDepth = cfg.scaleBufferDepth))) else None
  val bDmaScale = if (cfg.hasMx) Some(Module(new WorkerBeeDmaRdOnly(cfg, bufWidth = cfg.scaleBW,   bufDepth = cfg.scaleBufferDepth))) else None
  val cDmaScale = if (cfg.hasMx) Some(Module(new WorkerBeeDmaScaleWr(cfg))) else None


  val startPulse = regIf.io.start
  calcConfig.io.regFile := regFile
  calcConfig.io.startIn := startPulse
  regIf.io.busy := executor.io.busy
  regIf.io.progress := executor.io.progress
  regIf.io.done := executor.io.done
  regIf.io.configError := calcConfig.io.errPluse
  regIf.io.configErrorCode := calcConfig.io.errCode
  regIf.io.dmaErrors := VecInit(Seq.fill(8)(false.B))
  regIf.io.dmaErrors(0) := io.dmaA.rsp.fire && io.dmaA.rsp.payload.err
  regIf.io.dmaErrors(1) := io.dmaB.rsp.fire && io.dmaB.rsp.payload.err
  regIf.io.dmaErrors(2) := io.dmaC.rsp.fire && io.dmaC.rsp.payload.err
  if (cfg.hasMx) {
    regIf.io.dmaErrors(4) := io.dmaScaleB.get.rsp.fire && io.dmaScaleB.get.rsp.payload.err
    regIf.io.dmaErrors(5) := io.dmaCScale.get.rsp.fire && io.dmaCScale.get.rsp.payload.err
  }


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
        tracingPcReg := regIf.io.startPc
        tracingTagReg := regIf.io.startTag
        io.respForTracing.valid := true.B
        io.respForTracing.payload.state := 0.U
        io.respForTracing.payload.pc := regIf.io.startPc
        io.respForTracing.payload.tag := regIf.io.startTag
      }
  }.otherwise{
    io.respForTracing.valid := executor.io.done | calcConfig.io.errPluse
    when(executor.io.done){
      io.respForTracing.payload.state := 1.U
    }.elsewhen(calcConfig.io.errPluse){
      io.respForTracing.payload.state := 3.U
    }
  }
  

  // A=MX8 × B=MX4 单向混配谓词（顶层单一真源）：bDma stretch 使能、阵列侧 bFmt 重映射
  //   两处共用。反向混配（A=MX4×B=MX8）由 CalcConfig errCode3 拒绝，运行期不可达。
  private val w4a8Mix = if (cfg.hasMx4)
    DataFormat.isMx8(regFile.aFmt) && DataFormat.isMx4(regFile.bFmt)
  else false.B

  // A=INT8 × B=INT4 单向混配谓词（与 w4a8Mix 同式）：bDma stretch 使能、stretchBeat 加宽
  //   选择、阵列侧 bFmt 重映射三处共用。反向混配由 errCode3 拒绝，且几何不可达——一个
  //   K 窗口 = 64 个 INT8 权重 = 64B，超出 B 拍宽（totalN*bW/8 = 32B）两倍。
  private val intW4a8Mix = if (cfg.hasInt && cfg.hasMx4)
    regFile.aFmt === DataFormat.INT8 && regFile.bFmt === DataFormat.INT4
  else false.B

  // FP8E4M3/FP8E5M2 → MXE4M3/MXE5M2 阵列侧格式重映射（顶层单一真源）：纯 FP8 元素编码与
  //   MX8 相同（E4M3/E5M2 裸码），差别仅在无块 scale 字节 ⟹ 阵列整体走 MX8×MX8 基线，唯一
  //   运行语义差异 = Executor scale 注入点恒 127（E8M0 2^0=1.0 恒等乘）。本函数对非 FP8 值
  //   及混配产物（MXE4M3/INT8）均恒等，故 a/b 两侧共用、嵌套安全。
  private def fp8FmtRemap(f: DataFormat.Type): DataFormat.Type =
    Mux(f === DataFormat.FP8E5M2, DataFormat.MXE5M2,
      Mux(f === DataFormat.FP8E4M3, DataFormat.MXE4M3, f))

  // 混配下 B/scaleB 两 DMA 的 kt 轮次一律取【全量 calcConfig.kTile = K/32 = 阵列 K 窗口数】：
  //   bDma 每窗口每行读一个 16B 半字片（stretch 模式），scaleBDma 每窗口每行读一个相邻
  //   scale 字，与 fp8×fp8 基线同窗口数、同拍数。★不可按源字数折半（K/64）并在 push 侧连推
  //   2 拍：一个 fp4 源字跨 2 个 loadW 窗口，连推会产生行交错序（权重错序、列对共享 scale）。

  // E2M1 nibble → E4M3 字节精确加宽（7-bit 幅值码 + 符号直通）：fp4 全部 8 个值
  //   {0,±0.5,±1,±1.5,±2,±3,±4,±6} 在 E4M3 中精确可表示（零精度损失、永不产生 inf/NaN）。
  //   输入 = 16B 半字片（32 个 nibble 小端：元素 j ↔ bit 4j+3:4j），输出 = 32 字节阵列拍
  //   （byte j ↔ bit 8j+7:8j），lane 切片（16B/lane）与 A 侧 stride-2 天然对齐。
  private def fp4SliceToMx8Beat(src: UInt): UInt = {
    val lut = VecInit(Seq(0x00, 0x30, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C).map(_.U(7.W)))
    val bytes = Seq.tabulate(src.getWidth / 4) { j =>
      val nib = src(4 * j + 3, 4 * j)
      Cat(nib(3), lut(nib(2, 0)))
    }
    Cat(bytes.reverse)
  }

  // INT4 nibble → INT8 字节符号扩展加宽：二补码 nibble 直接 sext 至 8-bit，逐值精确、无需
  //   LUT，拍型与 fp4SliceToMx8Beat 同构。★Cat 位宽必须凑足 8（Fill(5)+nib(2,0)）：只拼出
  //   7-bit 字节会使整拍 224 位，与另一臂 256 位不等宽、被 Chisel 静默零扩成乱码拍——两臂
  //   等宽是 stretchBeat Mux 的硬约束。
  private def int4SliceToInt8Beat(src: UInt): UInt = {
    val bytes = Seq.tabulate(src.getWidth / 4) { j =>
      val nib = src(4 * j + 3, 4 * j)
      Cat(Fill(5, nib(3)), nib(2, 0))
    }
    Cat(bytes.reverse)
  }

  bDma.io.isA         := false.B
  bDma.io.start       := calcConfig.io.startOut
  bDma.io.regFile     := regFile
  bDma.io.reg_baseAddr := regFile.bAddr
  bDma.io.reg_kTile := calcConfig.io.calcConfig.kTile
  bDma.io.reg_mTile := calcConfig.io.calcConfig.mTile
  bDma.io.reg_nTile := calcConfig.io.calcConfig.nTile
  bDma.io.reg_rowOffset := calcConfig.io.calcConfig.bRowAddressOffset
  bDma.io.reg_colTileOffset := calcConfig.io.calcConfig.bColTileAddressOffset

  if (cfg.hasMx4) {
    // bDma stretch 连线：混配时逻辑拍 = 16B fp4/int4 半字片（一个阵列 K 窗口的一行），物理读
    //   32B 对齐单读、半字选择在 DMA 内 sel FIFO、nibble 加宽在此处闭环（按两互斥谓词 Mux 二
    //   选一：MX8×MX4 走 E2M1→E4M3 LUT、INT8×INT4 走 sext）。A 侧本身即 MX8/INT8、不 stretch。
    bDma.io.stretch.get := w4a8Mix || intW4a8Mix
    bDma.io.stretchBeat.get := Mux(intW4a8Mix,
      int4SliceToInt8Beat(bDma.io.stretchSrc.get),
      fp4SliceToMx8Beat(bDma.io.stretchSrc.get))
    aDma.io.stretch.get := false.B
    aDma.io.stretchBeat.get := 0.U
  }

  io.dmaB <> bDma.io.dmaExtRdIF

  // aDma/bDma 均为 execute 单次启动全自主供数：aDma 内建 nTile 外→kTile 中→M 内三级轮次
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

  // cDma 控制接线：execute 起始拍启动后自主待命，storeGate 由 Executor 在末 K pass 的
  //   store 窗口拉高，防止多 pass 中间 partial sum 被误搬
  cDma.io.start       := calcConfig.io.startOut
  cDma.io.peekBlock        := executor.io.cPeek
  cDma.io.regFile     := regFile
  cDma.io.calcConfig  := calcConfig.io.calcConfig
  io.dmaC <> cDma.io.dmaExtWrIF




  executor.io.execute := calcConfig.io.startOut
  executor.io.regFile := regFile
  executor.io.calcConfig := calcConfig.io.calcConfig

  scratchpad.io.aPush.valid   := aDma.io.bufPush.valid
  scratchpad.io.aPush.payload := aDma.io.bufPush.payload
  aDma.io.bufPush.ready       := scratchpad.io.aPush.ready
  aDma.io.bufPopFire          := scratchpad.io.aPop.fire

  scratchpad.io.cPush.valid   := executor.io.cPush.valid
  scratchpad.io.cPush.payload := executor.io.cPush.payload
  executor.io.cPush.ready     := scratchpad.io.cPush.ready

  scratchpad.io.bPush.valid   := bDma.io.bufPush.valid
  scratchpad.io.bPush.payload := bDma.io.bufPush.payload
  bDma.io.bufPush.ready       := scratchpad.io.bPush.ready
  bDma.io.bufPopFire          := scratchpad.io.bPop.fire

  executor.io.bPop.valid   := scratchpad.io.bPop.valid
  executor.io.bPop.payload := scratchpad.io.bPop.payload
  scratchpad.io.bPop.ready := executor.io.bPop.ready
  executor.io.bOccupancy   := scratchpad.io.bOccupancy

  private val blkLatchCnt = RegInit(0.U(cfg.mnkWidth.W))
  when(cvt.io.in.fire) { blkLatchCnt := blkLatchCnt + 1.U }
  when(cDma.io.doneBlock || startPulse) { blkLatchCnt := 0.U }
  private val blkLatchFull = blkLatchCnt === regFile.m
  scratchpad.io.cPop.ready := Mux(executor.io.cStoreGate, cvt.io.in.ready && !blkLatchFull, executor.io.cPop.ready)

  executor.io.cPop.valid   := scratchpad.io.cPop.valid
  executor.io.cPop.payload := scratchpad.io.cPop.payload
  executor.io.cPeekDone := cDma.io.doneBlock

  cvt.io.in.valid := scratchpad.io.cPop.valid && executor.io.cStoreGate && !blkLatchFull
  cvt.io.in.bits  := scratchpad.io.cPop.payload
  cvt.io.outFmt   := regFile.cFmt
  cvt.io.rnd      := regFile.rnd
  cvt.io.blkSel   := regFile.cMxBlockSize
  // nEff = min(totalN, n − nCnt×totalN)：nCnt 由 cDma 回供；位宽截段防 := 宽度不匹配
  // （nCnt 上界 = nTile−1，减法无下溢）。
  private val cNRemain = regFile.n - cDma.io.nCntOut * cfg.totalN.U
  private val nEffW = log2Up(cfg.totalN + 1)
  cvt.io.nEff := Mux(cNRemain >= cfg.totalN.U, cfg.totalN.U(nEffW - 1, 0), cNRemain(nEffW - 1, 0))

  // requant 接线（cvt 侧端口仅 hasInt 生成；rqM 取自 Executor rqMOut，仅 hasMx 供给，纯 INT
  //   无 MX 的配置已被 errCode13 拒绝）。cvt 在 in.fire 拍随行锁存——同一 N-tile 的全部行在该
  //   tile 的 rqM 重取触发（sNEXT_NK_TILE，晚于 doneBlock）之前完成锁存，跨 tile 不串值。
  if (cfg.hasInt) {
    cvt.io.rqM.get := (if (cfg.hasMx) executor.io.rqMOut.get
                       else VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))
    cvt.io.rqEn.get := regFile.requantOn
    cvt.io.rqShift.get := regFile.rqShift
    cvt.io.rqZp.get := regFile.rqZp
    cvt.io.reluOn.get := regFile.reluOn
  }

  // cvt.out → cDma.bufPop（行粒度，fire 即锁存进 WrOnly 行寄存器）；
  // out.ready 透传 WrOnly 末拍接受决定（行锁存空才收新行）。
  cDma.io.bufPop.valid   := cvt.io.out.valid
  cvt.io.out.ready       := cDma.io.bufPop.ready
  cDma.io.bufPop.payload := cvt.io.out.bits
  cDma.io.bufOccupancy   := scratchpad.io.cOccupancy

  executor.io.aPop.valid   := scratchpad.io.aPop.valid
  executor.io.aPop.payload := scratchpad.io.aPop.payload
  scratchpad.io.aPop.ready := executor.io.aPop.ready
  executor.io.aOccupancy   := scratchpad.io.aOccupancy
  executor.io.cOccupancy   := scratchpad.io.cOccupancy


  executor.io.dmaABusy := aDma.io.busy
  executor.io.dmaBBusy := bDma.io.busy
  // 量化运行时 drain/done 需等 dmaCScale 写完 ⟹ dmaCBusy 取两通道之或
  executor.io.dmaCBusy := (if (cfg.hasMx) cDma.io.busy || cDmaScale.get.io.busy else cDma.io.busy)

  wbComb.io.aIn      := executor.io.wbAIn
  wbComb.io.psumIn   := executor.io.wbPsumIn
  wbComb.io.loadHIn  := executor.io.wbLoadH
  wbComb.io.loadVInLock  := executor.io.wbLoadVLock
  wbComb.io.loadVIn  := executor.io.wbLoadV
  // 水平 loadW 加载（可选模式）：垂直模式下 executor 输出恒 0，阵列侧
  // 相关分支为死路，不加显式 mode 端口、靠信号活跃性自然二选一
  wbComb.io.loadWIn      := executor.io.wbLoadW
  wbComb.io.loadWInLock  := executor.io.wbLoadWLock
  wbComb.io.validIn  := executor.io.wbValidIn
  // a 侧 FP8 remap：纯 FP8 运行时阵列看到 MXE4M3/MXE5M2，而 Executor 的 mxRunB 读 raw
  //   regFile（isMx(FP8)=false）走非 MX 握手支路——供数不等 scale FIFO、scale DMA 不启动，
  //   127 由注入点组合直供。
  wbComb.io.fmtIn := (if (cfg.hasFp8) fp8FmtRemap(regFile.aFmt) else regFile.aFmt)
  // b 侧格式链：bFmt 跟随 aFmt（mixFmtEn=0）保证旧软件 bit-exact，MX 配置允许 mixFmtEn=1
  //   以支持异构双 MX；混配时重映射为 MXE4M3 / INT8——权重已由 push 侧逐 nibble 展开/sext，
  //   阵列解码走 MX8×MX8 与 INT8×INT8 基线。FP8 remap 施加于整条链【之外】（对混配产物恒
  //   等），aFmt=FP8 时 bFmt 必同为 FP8。
  wbComb.io.bFmtIn := {
    val bFmtChain = (if (cfg.hasMx4)
      Mux(w4a8Mix, DataFormat.MXE4M3, Mux(intW4a8Mix, DataFormat.INT8, regFile.bFmt))
    else regFile.bFmt)
    if (cfg.hasFp8) fp8FmtRemap(bFmtChain) else bFmtChain
  }
  wbComb.io.rndIn    := regFile.rnd

  // scale 接线：MX 配置下两向量由 Executor scale 通道驱动，整 Vec 连接 ⟹ 下标直通。两者都
  //   【不是】按列驻留量：lane 下标是 K-lane 轴，而阵列列是输出 N 轴。非 MX 配置下恒 0。
  if (cfg.hasMx) {
    wbComb.io.scaleAIn := executor.io.wbScaleAIn.get
    wbComb.io.scaleBIn := executor.io.wbScaleBIn.get
  } else {
    wbComb.io.scaleAIn.foreach(_ := 0.U)
    wbComb.io.scaleBIn.foreach(_ := 0.U)
  }
  wbComb.io.clear    := executor.io.wbClear

  executor.io.wbCOut     := wbComb.io.cOut
  executor.io.wbValidOut := wbComb.io.validOut

  scratchpad.io.flushA := executor.io.flushA
  scratchpad.io.flushB := executor.io.flushB
  scratchpad.io.flushC := executor.io.flushC

  // bias/rqM 取数器活动观测线：声明上提至类级，因 biasFState 作用域限于 hasMx 块内
  val perfBiasFetchActive = Wire(Bool())
  perfBiasFetchActive := false.B

   if (cfg.hasMx) {
    // scaleA DMA：A 式三级遍历，基址直连 scaleAAddr，内层行数 = m
    val scaleADma = aDmaScale.get
    scaleADma.io.isA := true.B
    scaleADma.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.aFmt)
    scaleADma.io.regFile    := regFile
    scaleADma.io.reg_baseAddr := regFile.aScaleAddr
    scaleADma.io.reg_kTile := calcConfig.io.calcConfig.kTile
    scaleADma.io.reg_mTile := calcConfig.io.calcConfig.mTile
    scaleADma.io.reg_nTile := calcConfig.io.calcConfig.nTile
    scaleADma.io.reg_rowOffset := calcConfig.io.calcConfig.scaleARowAddressOffset
    scaleADma.io.reg_colTileOffset := calcConfig.io.calcConfig.scaleAColTileAddressOffset

    // dmaScaleA 端口仲裁：epilogue 取数器（bias + rqM 两相）drain-then-steal。Executor 每逢
    //   execute（nt=0）与 N-tile 推进同拍发 biasFetchReq / rqMFetchReq 脉冲（各自被
    //   biasOn / requantOn 门控，可同拍或只来其一）；WAIT 态先把端口在途读排空（计数
    //   req.fire−rsp.fire，同拍双 fire 净零；scaleA RdOnly 的 req 可被 ready=0 完全反压、
    //   curAddr/credit 原地保持，断点续传无损），再按锁存的相使能【连续】串行发 16 个 4B
    //   读：先 BIAS 相（biasAddr + nt*totalN*4 + 4*i → biasIn），后 M 相（rqMBase + 同偏移
    //   → rqMIn），两相间不回 WAIT（在途读在 BIAS 相收齐时已归零），全相完成回 IDLE 归还
    //   端口。MX 运行时 sDRAIN/sLOAD_B 预取窗口（≥31 拍）覆盖取数时长，executor sLOAD_A 的
    //   !biasLoaded/!rqMLoaded 供数门控为兜底。steal=0（IDLE）时 req/rsp 两侧逐项直连。
    val sBiasFIDLE    = 0.U(2.W)
    val sBiasFWAIT    = 1.U(2.W)
    val sBiasFFETCH   = 2.U(2.W)
    val sBiasFFETCH_M = 3.U(2.W)
    val biasFState  = RegInit(sBiasFIDLE)
    val biasFNt     = RegInit(0.U(cfg.mnkWidth.W))
    val biasFSent   = RegInit(0.U(log2Up(cfg.totalN + 1).W))   // 已发请求数 0..totalN（两相共用，相位切换清零）
    val biasFRcvd   = RegInit(0.U(log2Up(cfg.totalN + 1).W))   // 已收响应数 0..totalN（同上）
    val biasPortInfl = RegInit(0.U(log2Up(cfg.scaleBufferDepth + 2).W))
    val biasFPhaseBias = RegInit(false.B)
    val biasFPhaseM    = RegInit(false.B)

    val biasPortReq = io.dmaScaleA.get.req
    val biasPortRsp = io.dmaScaleA.get.rsp
    val biasDmaReq  = scaleADma.io.dmaExtRdIF.req
    val biasDmaRsp  = scaleADma.io.dmaExtRdIF.rsp

    val biasSteal = biasFState =/= sBiasFIDLE
    val rspToBias = biasFState === sBiasFFETCH
    val rspToM    = biasFState === sBiasFFETCH_M
    val epilReqValid = (biasFState === sBiasFFETCH || biasFState === sBiasFFETCH_M) && biasFSent < cfg.totalN.U
    val biasReqAddr  = regFile.biasAddr + biasFNt * (cfg.totalN * 4).U + biasFSent * 4.U
    val rqMReqAddr   = regFile.rqMBase  + biasFNt * (cfg.totalN * 4).U + biasFSent * 4.U

    biasPortReq.valid := Mux(biasSteal, epilReqValid, biasDmaReq.valid)
    biasPortReq.payload.addr := Mux(biasSteal, Mux(biasFState === sBiasFFETCH, biasReqAddr, rqMReqAddr), biasDmaReq.payload.addr)
    biasDmaReq.ready := biasPortReq.ready && !biasSteal

    // rsp 路由：FETCH/FETCH_M 期间端口全部在途读均属取数器（WAIT 已排空 + scaleADma
    //   被反压无法发新请求）；WAIT/IDLE 期归还 scaleADma（含排空期未返回的旧读）
    biasDmaRsp.valid := biasPortRsp.valid && !rspToBias && !rspToM
    biasDmaRsp.payload := biasPortRsp.payload
    executor.io.biasIn.get.valid := biasPortRsp.valid && rspToBias
    executor.io.biasIn.get.payload := biasPortRsp.payload.data
    executor.io.rqMIn.get.valid := biasPortRsp.valid && rspToM
    executor.io.rqMIn.get.payload := biasPortRsp.payload.data
    biasPortRsp.ready := Mux(rspToBias, executor.io.biasIn.get.ready,
                          Mux(rspToM, executor.io.rqMIn.get.ready, biasDmaRsp.ready))

    // 端口在途计数（区别于 RdOnly inflCnt 的 when/elsewhen 式：同拍 req.fire+rsp.fire
    //   必须净零，否则 WAIT 会因漏减一拍而多等；饱和递减防陈旧 rsp 下溢）
    when(biasPortReq.fire && !biasPortRsp.fire) {
      biasPortInfl := biasPortInfl + 1.U
    }.elsewhen(!biasPortReq.fire && biasPortRsp.fire && biasPortInfl =/= 0.U) {
      biasPortInfl := biasPortInfl - 1.U
    }
    val scalePortError = biasPortRsp.fire && biasPortRsp.payload.err
    regIf.io.dmaErrors(3) := scalePortError && !rspToBias && !rspToM
    regIf.io.dmaErrors(6) := scalePortError && rspToBias
    regIf.io.dmaErrors(7) := scalePortError && rspToM

    // 取数器重入守卫：上一轮取数未回 IDLE 时新脉冲到达 = bias/rqM 字流会交错错序
    assert(!((executor.io.biasFetchReq.get || executor.io.rqMFetchReq.get) && biasFState =/= sBiasFIDLE),
      "WorkerBeeExecutor epilogue fetch request while previous bias/rqM fetch still in flight")

    switch(biasFState) {
      is(sBiasFIDLE) {
        when(executor.io.biasFetchReq.get || executor.io.rqMFetchReq.get) {
          biasFNt       := Mux(executor.io.biasFetchReq.get, executor.io.biasFetchNt.get, executor.io.rqMFetchNt.get)
          biasFSent     := 0.U
          biasFRcvd     := 0.U
          biasFPhaseBias := executor.io.biasFetchReq.get
          biasFPhaseM    := executor.io.rqMFetchReq.get
          biasFState    := sBiasFWAIT
        }
      }
      is(sBiasFWAIT) {
        when(biasPortInfl === 0.U) {
          biasFState := Mux(biasFPhaseBias, sBiasFFETCH, sBiasFFETCH_M)
        }
      }
      is(sBiasFFETCH) {
        when(biasPortReq.fire) { biasFSent := biasFSent + 1.U }
        when(executor.io.biasIn.get.fire) { biasFRcvd := biasFRcvd + 1.U }
        when(biasFRcvd === cfg.totalN.U) {
          biasFSent := 0.U
          biasFRcvd := 0.U
          biasFState := Mux(biasFPhaseM, sBiasFFETCH_M, sBiasFIDLE)
        }
      }
      is(sBiasFFETCH_M) {
        when(biasPortReq.fire) { biasFSent := biasFSent + 1.U }
        when(executor.io.rqMIn.get.fire) { biasFRcvd := biasFRcvd + 1.U }
        when(biasFRcvd === cfg.totalN.U) { biasFState := sBiasFIDLE }
      }
    }

    perfBiasFetchActive := biasFState =/= sBiasFIDLE


    // scaleB DMA：两级 nt×kb 遍历，基址直连 scaleBAddr，块内步进 = n
    val scaleBDma = bDmaScale.get
    scaleBDma.io.isA := false.B
    scaleBDma.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.bFmt)
    scaleBDma.io.regFile    := regFile
    scaleBDma.io.reg_baseAddr := regFile.bScaleAddr
    // scaleB kt 轮次 = 全量 kTile（与 bPop 逐拍 lockstep：每窗口 16 行 × 每行一个相邻
    //   scale 字）；kTile 步进混配取 aKLoadNum/blk，非对齐读交 DMA 内 gather 处理。
    scaleBDma.io.reg_kTile := calcConfig.io.calcConfig.kTile
    scaleBDma.io.reg_mTile := calcConfig.io.calcConfig.mTile
    scaleBDma.io.reg_nTile := calcConfig.io.calcConfig.nTile
    scaleBDma.io.reg_rowOffset := calcConfig.io.calcConfig.scaleBRowAddressOffset
    scaleBDma.io.reg_colTileOffset := calcConfig.io.calcConfig.scaleBColTileAddressOffset
    io.dmaScaleB.get <> scaleBDma.io.dmaExtRdIF

    scratchpad.io.scaleAPush.get.valid   := scaleADma.io.bufPush.valid
    scratchpad.io.scaleAPush.get.payload := scaleADma.io.bufPush.payload
    scaleADma.io.bufPush.ready           := scratchpad.io.scaleAPush.get.ready
    scaleADma.io.bufPopFire              := scratchpad.io.scaleAPop.get.fire

    scratchpad.io.scaleBPush.get.valid   := scaleBDma.io.bufPush.valid
    scratchpad.io.scaleBPush.get.payload := scaleBDma.io.bufPush.payload
    scaleBDma.io.bufPush.ready           := scratchpad.io.scaleBPush.get.ready
    scaleBDma.io.bufPopFire              := scratchpad.io.scaleBPop.get.fire

    if (cfg.hasMx4) {
      // scaleBDma 不 stretch：读侧节奏与 fp8 基线同构（每阵列拍 pop 一个内存相邻 scale
      //   字；blk16 时窗口 w 的字含源块 {2w,2w+1} 的 byte0/1、blk32 时 byte0 = 块 w，
      //   由 Executor feed 环 idx=(lane*2)/blk 动态切片）。scaleADma 同恒 0。
      scaleBDma.io.stretch.get := false.B
      scaleBDma.io.stretchBeat.get := 0.U
      scaleADma.io.stretch.get := false.B
      scaleADma.io.stretchBeat.get := 0.U
    }

    // scale FIFO flush 跟随 A/B flush（execute 起始拍清空，与 aDma/bDma 同范式）
    scratchpad.io.flushScaleA.get := executor.io.flushA
    scratchpad.io.flushScaleB.get := executor.io.flushB

    executor.io.scaleAPop.get.valid   := scratchpad.io.scaleAPop.get.valid
    executor.io.scaleAPop.get.payload := scratchpad.io.scaleAPop.get.payload
    scratchpad.io.scaleAPop.get.ready := executor.io.scaleAPop.get.ready
    executor.io.scaleBPop.get.valid   := scratchpad.io.scaleBPop.get.valid
    executor.io.scaleBPop.get.payload := scratchpad.io.scaleBPop.get.payload
    scratchpad.io.scaleBPop.get.ready := executor.io.scaleBPop.get.ready
    executor.io.scaleBOcc.get         := scratchpad.io.scaleBOccupancy.get
  }

  if (cfg.hasMx) {
    cDmaScale.get.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.cFmt)
    cDmaScale.get.io.baseAddr   := regFile.cScaleAddr
    cDmaScale.get.io.regFile    := regFile
    cDmaScale.get.io.calcConfig := calcConfig.io.calcConfig
    io.dmaCScale.get <> cDmaScale.get.io.dmaExtWrIF

    // 顶层深 2 cScaleFifo 是唯一 scale 缓冲：cvt.scaleOut → cScaleFifo → cDmaScale.bufPop，
    //   flush 接 executor.flushC。cvt 内已保证 out/scale 原子配对（scale 未就绪时进
    //   dataDone 子态，out 不重发），FIFO 满时自然反压 cvt。
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

  io.status.busy       := executor.io.busy
  io.status.inFlight   := regIf.io.inFlight
  io.status.progress   := executor.io.progress
  io.status.aOccupancy := scratchpad.io.aOccupancy
  io.status.bOccupancy := scratchpad.io.bOccupancy
  io.status.cOccupancy := scratchpad.io.cOccupancy
  io.irq               := regIf.io.irq

  // perf 观测端口：纯组合直连既有信号，计数由外部 PMU 按拍采样。dmaX/scaleX 为物理通道
  //   口径（scaleA 含 bias/rqM 取数盗用拍）。
  io.perf.dmaA   := io.dmaA.req.valid
  io.perf.dmaB   := io.dmaB.req.valid
  io.perf.dmaC   := io.dmaC.req.valid
  io.perf.scaleA := (if (cfg.hasMx) io.dmaScaleA.get.req.valid else false.B)
  io.perf.scaleB := (if (cfg.hasMx) io.dmaScaleB.get.req.valid else false.B)
  io.perf.scaleC := (if (cfg.hasMx) io.dmaCScale.get.req.valid else false.B)
  io.perf.array  := executor.io.wbValidIn
  io.perf.stallA := executor.io.perf.stallA
  io.perf.stallB := executor.io.perf.stallB
  // epilogue 活动拍 = bias/rqM 取数 ∨ cvt 管线占用（in.fire 收行 / out.valid 持行
  //   交付 / scaleOut.valid 量化 scale 行）；C 写回通道活动另由 dmaC/scaleC 观测
  io.perf.epilogue := perfBiasFetchActive || cvt.io.in.fire || cvt.io.out.valid || cvt.io.scaleOut.valid
  io.perf.busy   := executor.io.busy
}
