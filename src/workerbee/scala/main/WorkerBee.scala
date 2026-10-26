import chisel3._
import chisel3.util._

class WorkerBee(cfg: WorkerBeeConfig) extends Module {

  val io = IO(new Bundle {
    val cmd = Vec(4, slave(Stream(new WorkerBeeCmd)))
    val resp = master(Stream(new WorkerBeeResp))
    val respForTracing = master(Flow(new WorkerBeeRespForTracing))
    val dmaA = WorkerBeeDMAExtReadOnlyIF(cfg, cfg.aExtW)
    val dmaC = new WorkerBeeDMAExtWriteOnlyIF(cfg, cfg.cDmaExtW)  // P2-清理: 端口位宽直取新契约参数
    val dmaB = WorkerBeeDMAExtReadOnlyIF(cfg, cfg.bExtW)
    val dmaScaleA = if (cfg.hasMx) Some(WorkerBeeDMAExtReadOnlyIF(cfg, cfg.scaleAW))             else None
    val dmaScaleB = if (cfg.hasMx) Some(WorkerBeeDMAExtReadOnlyIF(cfg, cfg.scaleBW)) else None
    val dmaCScale = if (cfg.hasMx) Some(WorkerBeeDMAExtWriteOnlyIF(cfg, cfg.cScaleRowW)) else None

    val status = Output(new WorkerBeeStatus(cfg))
    val irq = Output(Bool())
    // 【perf 观测端口】性能观测信号组（纯组合电平、无内部计数；外部 PMU 按拍采样，
    //   信号语义见 WbInterface.scala WorkerBeePerf）
    val perf = Output(new WorkerBeePerf)
  })

  val regIf = Module(new WorkerBeeRegIf(cfg))
  regIf.io.cmd <> io.cmd
  io.resp <> regIf.io.resp
  val regFile = regIf.io.regFile

  // ==========================================================================
  // 子模块例化
  // ==========================================================================
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
  

  // ==========================================================================
  // 【P1-2 W4A8 展开路】A=MX8 × B=MX4 单向混配谓词（顶层单一真源）：bDma stretch
  //   使能、阵列侧 bFmt 重映射两处共用。反向混配（A=MX4×B=MX8）由 CalcConfig
  //   errCode3 拒绝，运行期不可达。非 hasMx4 配置不生成这些信号（零 diff）。
  // ==========================================================================
  private val w4a8Mix = if (cfg.hasMx4)
    DataFormat.isMx8(regFile.aFmt) && DataFormat.isMx4(regFile.bFmt)
  else false.B

  // 【INT W4A8】A=INT8 × B=INT4 单向混配谓词（与 w4a8Mix 同式；门控 hasInt&&hasMx4：
  //   bDma stretch 端口仅 hasMx4 配置生成，且用户裁定不存在 INT-only 配置——全集下
  //   hasInt ⟹ 含 MXE2M1）。bDma stretch 使能、stretchBeat 加宽选择、阵列侧 bFmt
  //   重映射三处共用。反向混配（A=INT4×B=INT8）由 CalcConfig errCode3 拒绝（运行期
  //   不可达）：一个 K 窗口 = 64 个 INT8 权重 = 64B，超出 B 拍宽（totalN*bW/8=32B）
  //   两倍，几何不可达。
  private val intW4a8Mix = if (cfg.hasInt && cfg.hasMx4)
    regFile.aFmt === DataFormat.INT8 && regFile.bFmt === DataFormat.INT4
  else false.B

  // ==========================================================================
  // 【P2 纯 FP8】FP8E4M3/FP8E5M2 → MXE4M3/MXE5M2 阵列侧格式重映射（顶层单一真源）：
  //   纯 FP8 的元素编码与 MX8 完全相同（E4M3/E5M2 裸码），差别仅在无块 scale 字节
  //   ⟹ 阵列/MAC/Cell/Worker 零改动、整体走已验证的 MX8×MX8 基线，唯一运行语义
  //   差异 = Executor scale 注入点恒 127（E8M0 2^0=1.0 恒等乘，见 WorkerBeeExecutor
  //   wbScaleAIn/wbScaleBIn 的 Mux）。本函数对非 FP8 值恒等（remap 只改写
  //   FP8E4M3/FP8E5M2 两个码点），故 a/b 两侧共用。调用点仅两处（wbComb.io.fmtIn /
  //   .bFmtIn），均 if(cfg.hasFp8) 门控：不含 FP8 的配置零 Mux、逐字不变（零 diff）。
  //   ★混配谓词互斥性：w4a8Mix/intW4a8Mix 与 FP8 互斥（aFmt=FP8×bFmt=MX4/INT4 由
  //   CalcConfig errCode3 新臂运行期拒绝），remap 对 MX4/INT8 值恒等，嵌套安全。
  // ==========================================================================
  private def fp8FmtRemap(f: DataFormat.Type): DataFormat.Type =
    Mux(f === DataFormat.FP8E5M2, DataFormat.MXE5M2,
      Mux(f === DataFormat.FP8E4M3, DataFormat.MXE4M3, f))

  // 【P1-2 W4A8 订正（2026-10-13）】混配下 B/scaleB 两 DMA 的 kt 轮次一律取【全量
  //   calcConfig.kTile = K/aKLoadNum = K/32（A=MX8 口径 = 阵列 K 窗口数）】：bDma 每窗口
  //   每行读一个 16B 半字片（stretch 模式，见 WorkerBeeDmaRdOnly），scaleBDma 每窗口
  //   每行读一个相邻 scale 字 —— 与 fp8×fp8 基线同窗口数、同拍数、同节奏。初版
  //   「源字数 K/64 折半（bKTile）+ push 连推 2 拍」已被证伪废弃：一个 fp4 源字
  //   （64K）跨 2 个 loadW 窗口，连推产生行交错序（PROBE4 权重错序、PROBE6 列对
  //   共享 scale），bKTile 寄存器随之删除。纯 fp4×fp4（aFmt=E2M1）时 kTile 本身
  //   = K/64 = 源字数，纯 fp8 亦同值，均逐拍不变。

  // 【P1-2 W4A8 订正】E2M1 nibble → E4M3 字节精确加宽（7-bit 幅值码 + 符号直通）：
  //   fp4 全部 8 个值 {0,±0.5,±1,±1.5,±2,±3,±4,±6} 在 E4M3 中精确可表示（零精度
  //   损失、永不产生 inf/NaN），故混配时阵列按已验证的 MX8×MX8 基线解码即可。
  //   输入 = bDma stretch 半字片（16B = 32 个 nibble，小端：元素 j ↔ bit 4j+3:4j），
  //   输出 = 32 字节阵列拍（byte j ↔ bit 8j+7:8j），lane 切片（16B/lane）与 A 侧
  //   stride-2 天然对齐。
  private def fp4SliceToMx8Beat(src: UInt): UInt = {
    val lut = VecInit(Seq(0x00, 0x30, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C).map(_.U(7.W)))
    val bytes = Seq.tabulate(src.getWidth / 4) { j =>
      val nib = src(4 * j + 3, 4 * j)
      Cat(nib(3), lut(nib(2, 0)))
    }
    Cat(bytes.reverse)
  }

  // 【INT W4A8】INT4 nibble → INT8 字节符号扩展加宽：二补码 nibble 直接 sext 至
  //   8-bit，数值逐值精确、无需 LUT。输入/输出拍型与 fp4SliceToMx8Beat 完全同构
  //   （16B 半字片 32 个 nibble 小端 ↔ 32 字节阵列拍），两混配谓词互斥、stretchBeat
  //   处 Mux 二选一。★Cat 位宽必须凑足 8（Fill(5)+nib(2,0)）：初版 Fill(4) 只拼出
  //   7-bit 字节 ⟹ 整拍 224 位，Mux 两臂 224/256 不等宽被 Chisel 静默零扩成
  //   「7-bit 字段错位打包 + 高 32 位全零」的乱码拍（fp4 版靠 7-bit LUT + 1-bit
  //   符号天然凑 8 位故无此坑）——两臂等宽是 stretchBeat Mux 的硬约束。
  private def int4SliceToInt8Beat(src: UInt): UInt = {
    val bytes = Seq.tabulate(src.getWidth / 4) { j =>
      val nib = src(4 * j + 3, 4 * j)
      Cat(Fill(5, nib(3)), nib(2, 0))
    }
    Cat(bytes.reverse)
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

  if (cfg.hasMx4) {
    // 【P1-2 W4A8 订正】bDma stretch 连线：混配时逻辑拍 = 16B fp4 半字片（一个阵列
    //   K 窗口的一行），物理读 32B 对齐单读、半字选择在 DMA 内 sel FIFO、E2M1→E4M3
    //   LUT 加宽组合在此处闭环。A 侧激活本身即 MX8、不 stretch（恒 0，DMA 内
    //   Mux 死路 = 基线支路）。
    // 【INT W4A8】stretch 使能扩为两混配谓词之或（互斥：aFmt 不能同时为 MX8 与
    //   INT8）；加宽函数按谓词 Mux 二选一——MX8×MX4 走 E2M1→E4M3 LUT、INT8×INT4
    //   走 INT4→INT8 sext。非 hasInt 配置 intW4a8Mix 恒 false.B，常量折叠后与
    //   P1-2 基线逐位相同。
    bDma.io.stretch.get := w4a8Mix || intW4a8Mix
    bDma.io.stretchBeat.get := Mux(intW4a8Mix,
      int4SliceToInt8Beat(bDma.io.stretchSrc.get),
      fp4SliceToMx8Beat(bDma.io.stretchSrc.get))
    aDma.io.stretch.get := false.B
    aDma.io.stretchBeat.get := 0.U
  }

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

  // 【P0 epilogue-2 fused requant】requant 接线（cvt 侧端口仅 hasInt 生成；rqM 取自
  //   Executor 收集寄存器 rqMOut——仅 hasMx 供给，纯 INT 无 MX 的假想配置下 requantOn
  //   已被 errCode13 的 !hasMx 臂运行期拒绝，恒 0 输入无害；标量直读 regFile：
  //   requantOn=reg5[29]、reluOn=reg5[15]、shift/zp=RQ_PARAM）。cvt 在 in.fire 拍随行
  //   锁存——同一 N-tile 的全部行在该 tile 的 rqM 重取触发（sNEXT_NK_TILE，晚于
  //   doneBlock=末行已过 cvt）之前完成锁存，跨 tile 不串值。
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
  // Executor ↔ WbComb
  // ==========================================================================
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
  // 【P2 纯 FP8】a 侧同款 remap：纯 FP8 运行时阵列看到 MXE4M3/MXE5M2（FusionMacFp
  //   解码、WorkUnit isMx 路由、Worker fmtOk/consumeValid 全部自动走 MX8 基线），
  //   Executor 的 mxRunB 读 raw regFile（isMx(FP8)=false）走非 MX 握手支路——供数
  //   不等 scale FIFO、scale DMA 不启动（零流量），127 由注入点组合直供。
  //   if(cfg.hasFp8) 门控：不含 FP8 的配置逐字不变（零 diff）。
  wbComb.io.fmtIn := (if (cfg.hasFp8) fp8FmtRemap(regFile.aFmt) else regFile.aFmt)
  // bFmt 跟随 aFmt（mixFmtEn=0），保证旧软件 bit-exact；MX 配置允许 mixFmtEn=1
  // 以支持异构双 MX（aFmt=E4M3, bFmt=E5M2）
  // 【P1-2 W4A8 展开路】混配时阵列侧 bFmt 重映射为 MXE4M3：权重已由 bDma push 侧
  //   逐 nibble 展开成 E4M3 ⟹ 阵列解码（含 FusionMac isMx4 双条件、fp8Mul 支路）
  //   整体走已验证的 MX8×MX8 基线，阵列/Cell/Worker/MAC 零改动。非 hasMx4 配置
  //   无 Mux、逐字不变（零 diff）。
  // 【INT W4A8】INT8×INT4 混配同款重映射为 INT8：权重已由 push 侧逐 nibble sext
  //   展开成 INT8 ⟹ 阵列解码走已验证的 INT8×INT8 双 lane 基线（FusionMacInt
  //   lane0/1 按位解码，lane2/3 的 aIsInt4&bIsInt4 双条件自然旁路为零），
  //   阵列/Cell/Worker/MAC 零改动。两混配谓词互斥，嵌套 Mux 无歧义。
  // 【P2 纯 FP8】remap 施加于整条既有链【之外】（fp8FmtRemap 对混配产物
  //   MXE4M3/INT8 恒等）：aFmt=FP8 时 bFmt 必同为 FP8（异配由 errCode3 拒绝），
  //   b 侧 remap 后阵列跑 MX8×MX8；E4M3×E5M2 异构双 FP8 与 MX 的异构双 MX
  //   同构（remap 分别落 E4M3/E5M2）。
  wbComb.io.bFmtIn := {
    val bFmtChain = (if (cfg.hasMx4)
      Mux(w4a8Mix, DataFormat.MXE4M3, Mux(intW4a8Mix, DataFormat.INT8, regFile.bFmt))
    else regFile.bFmt)
    if (cfg.hasFp8) fp8FmtRemap(bFmtChain) else bFmtChain
  }
  wbComb.io.rndIn    := regFile.rnd

  // P3: scale wiring -- under an MX config both scale vectors are driven by the
  // Executor scale channels, replacing the P1 top-level constant 0. Both are
  // whole-Vec connections, so the index maps straight through: Executor lane n
  // drives WbComb row/lane n. Neither vector is a "per-column resident"
  // quantity -- the lane index is the K-lane axis while the array column is the
  // output-N axis (see the axis note in WbComb; the old wording here said
  // "scaleB per-column resident vector" and was stale). Under a non-MX config
  // both stay constant 0 (scale ports / DMA / FIFO are not generated at all),
  // behaviour bit-exact.
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

  // ==========================================================================
  // Flush 信号
  // ==========================================================================
  scratchpad.io.flushA := executor.io.flushA
  scratchpad.io.flushB := executor.io.flushB
  scratchpad.io.flushC := executor.io.flushC

  // 【perf 端口】bias/rqM 取数器活动观测线（默认 0；hasMx 块内由取数器 FSM 驱动。
  //   声明上提至类级是因 biasFState/sBiasFIDLE 作用域限于 hasMx 块内）
  val perfBiasFetchActive = Wire(Bool())
  perfBiasFetchActive := false.B

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

    // ==========================================================================
    // 【P0 epilogue-1/2】dmaScaleA 端口仲裁：epilogue 取数器（bias + rqM 两相）drain-then-steal
    // --------------------------------------------------------------------------
    // Executor 每逢 execute（nt=0）与 N-tile 推进同拍发 biasFetchReq / rqMFetchReq 脉冲
    // （各自被 biasOn / requantOn 门控，两脉冲可同拍或只来其一）；本取数器 WAIT 态先把
    // 端口在途读排空（计数 req.fire−rsp.fire，同拍双 fire 净零；scaleA RdOnly 的 req 可被
    // ready=0 完全反压、curAddr/credit 原地保持，断点续传无损），再按锁存的相使能【连续】
    // 串行发 16 个 4B 读：先 BIAS 相（biasAddr + nt*totalN*4 + 4*i → executor.biasIn），
    // 后 M 相（rqMBase + nt*totalN*4 + 4*i → executor.rqMIn），两相间不回 WAIT（端口
    // 在途读在 BIAS 相收齐时已归零）。全相完成回 IDLE、端口归还。非 MX 格式运行
    // （isMx(aFmt)=0）时 scaleADma 不启动、端口天然空闲；MX 运行时 sDRAIN/sLOAD_B 预取
    // 窗口（≥31 拍）覆盖取数时长，executor sLOAD_A 的 !biasLoaded/!rqMLoaded 供数门控
    // 为兜底。steal=0（IDLE）时 req/rsp 两侧逐项直连、与原 `<>` 连接逐位等价 ⟹ 既有用例
    // 零 diff。
    // ==========================================================================
    val sBiasFIDLE    = 0.U(2.W)
    val sBiasFWAIT    = 1.U(2.W)
    val sBiasFFETCH   = 2.U(2.W)
    val sBiasFFETCH_M = 3.U(2.W)
    val biasFState  = RegInit(sBiasFIDLE)
    val biasFNt     = RegInit(0.U(cfg.mnkWidth.W))
    val biasFSent   = RegInit(0.U(log2Up(cfg.totalN + 1).W))   // 已发请求数 0..totalN（两相共用，相位切换清零）
    val biasFRcvd   = RegInit(0.U(log2Up(cfg.totalN + 1).W))   // 已收响应数 0..totalN（同上）
    val biasPortInfl = RegInit(0.U(log2Up(cfg.scaleBufferDepth + 2).W))
    // 相使能（IDLE 请求拍锁存）：本轮取数含 BIAS 相 / 含 M 相
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

    // 【perf 端口】取数器活动拍（WAIT 排空拍亦计入——端口等待属取数成本）
    perfBiasFetchActive := biasFState =/= sBiasFIDLE


    // scaleB DMA：两级 nt×kb 遍历，基址直连 scaleBAddr，块内步进 = n
    val scaleBDma = bDmaScale.get  // P4.2: 引用外层 Option 持有的实例（原地例化已上提）
    scaleBDma.io.isA := false.B
    scaleBDma.io.start      := calcConfig.io.startOut & DataFormat.isMx(regFile.bFmt)
    scaleBDma.io.regFile    := regFile
    scaleBDma.io.reg_baseAddr := regFile.bScaleAddr
    // 【P1-2 W4A8 订正】scaleB kt 轮次 = 全量 kTile（阵列 K 窗口数，与 bPop 逐拍
    //   lockstep：每窗口 16 行 × 每行一个相邻 scale 字）；kTile 步进
    //   scaleBColTileAddressOffset 混配取 aKLoadNum/blk（CalcConfig 订正），非对齐
    //   读交 P0-1 gather，DMA 零特判、零展开。
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

    if (cfg.hasMx4) {
      // 【P1-2 W4A8 订正】scaleBDma 不 stretch：读侧节奏与 fp8 基线同构（每阵列拍
      //   pop 一个内存相邻 scale 字；blk16 时窗口 w 的字含源块 {2w,2w+1} 的
      //   byte0/1、blk32 时 byte0 = 块 w，由 Executor feed 环 idx=(lane*2)/blk 动态
      //   切片）。scaleADma 同恒 0。
      scaleBDma.io.stretch.get := false.B
      scaleBDma.io.stretchBeat.get := 0.U
      scaleADma.io.stretch.get := false.B
      scaleADma.io.stretchBeat.get := 0.U
    }

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
    // 【P0-1 gather】scaleB FIFO 占用量 → Executor loadW 预取窗口门控
    executor.io.scaleBOcc.get         := scratchpad.io.scaleBOccupancy.get
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
  io.status.inFlight   := regIf.io.inFlight
  io.status.progress   := executor.io.progress
  io.status.aOccupancy := scratchpad.io.aOccupancy
  io.status.bOccupancy := scratchpad.io.bOccupancy
  io.status.cOccupancy := scratchpad.io.cOccupancy
  io.irq               := regIf.io.irq

  // ==========================================================================
  // 【perf 观测端口】纯组合直连既有信号（零新状态、零新时序路径）；计数由外部
  //   PMU 按拍采样完成。dmaX/scaleX 为物理通道口径（scaleA 含 bias/rqM 取数
  //   盗用拍）；array/busy/stallA/stallB/epilogue 语义见 WorkerBeePerf 注释。
  // ==========================================================================
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
