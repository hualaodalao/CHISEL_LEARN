/** WorkerBeeExecutor — Weight-Stationary 自动 Tiling GEMM 执行器：按 M/N/K 与阵列能力
  * (totalN×totalN) 自动切块，N→K→M 循环序实现权重驻留复用；A 侧为纯 pop 消费者，C 侧
  * 拉高 cStoreGate 开门、cOccupancy 排空即写回完成。aIn skew 契约：外部按「行 i 延迟 i
  * 拍」错峰供数（通道 0 无延迟），wbValidIn 同步保持 curM + 2*(totalN-1) 拍。 */

import chisel3._
import chisel3.util._
import scala.annotation.varargs

class WorkerBeeExecutor(cfg: WorkerBeeConfig) extends WorkerBeeExecutor2(cfg)

class WorkerBeeExecutor2(cfg: WorkerBeeConfig) extends Module {

  val io = IO(new Bundle {
    val execute  = Input(Bool())        // 脉冲启动
    val busy     = Output(Bool())
    val done     = Output(Bool())       // 脉冲完成

    val dmaABusy = Input(Bool())
    val dmaBBusy = Input(Bool())
    val dmaCBusy = Input(Bool())

    val regFile = Input(WorkerBeeRegister(cfg))
    val calcConfig = Input(WorkerBeeExePreCalcConfig(cfg))
    

    val aOccupancy = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))

    val cStoreGate = Output(Bool())                    // 末 K pass store 窗口拉高
    val cPeek      = Output(Bool())
    val cPeekDone  = Input(Bool())
    val cOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))

    val aPop  = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    val bPop  = slave(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val bOccupancy = Input(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val cPop  = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPush = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val flushA = Output(Bool())
    val flushB = Output(Bool())
    val flushC = Output(Bool())

    val scaleAPop    = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleAW.W))))             else None
    val scaleBPop    = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleBW.W)))) else None
    // scaleB FIFO 占用量：loadW 预取窗口门控用（sDRAIN→sLOAD_B）
    val scaleBOcc    = if (cfg.hasMx) Some(Input(UInt(log2Up(cfg.scaleBufferDepth + 1).W))) else None
    val wbScaleAIn = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(8.W))))   else None
    val wbScaleBIn = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(8.W))))   else None

    // bias 取数握手：biasIn = 16×32b 字流（每 N-tile 一组，收齐置 biasLoaded），
    //   biasFetchReq/Nt = 取数触发脉冲，由顶层取数器消费（共用 dmaScaleA 端口）
    val biasIn       = if (cfg.hasMx) Some(slave(Stream(UInt(32.W))))    else None
    val biasFetchReq = if (cfg.hasMx) Some(Output(Bool()))               else None
    val biasFetchNt  = if (cfg.hasMx) Some(Output(UInt(cfg.mnkWidth.W))) else None

    // rqM 取数握手（与 bias 同范式）：rqMOut = per-channel M 向量，接至 WbOutCvt requant
    val rqMIn       = if (cfg.hasMx) Some(slave(Stream(UInt(32.W))))    else None
    val rqMFetchReq = if (cfg.hasMx) Some(Output(Bool()))               else None
    val rqMFetchNt  = if (cfg.hasMx) Some(Output(UInt(cfg.mnkWidth.W))) else None
    val rqMOut      = if (cfg.hasMx) Some(Output(Vec(cfg.totalN, UInt(32.W)))) else None

    val wbAIn     = Output(Vec(cfg.totalN, UInt(cfg.aEffW.W)))
    val wbPsumIn  = Output(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val wbLoadH   = Output(Bool())
    val wbLoadV   = Output(Bool())
    val wbLoadVLock   = Output(Bool())
    // 水平 loadW 加载（可选模式，regs(7)(5) 选择）：垂直模式下恒 0
    val wbLoadW       = Output(Bool())
    val wbLoadWLock   = Output(Bool())
    val wbValidIn = Output(Bool())
    val wbClear   = Output(Bool())
    val wbCOut    = Input(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val wbValidOut = Input(Bool())

    val progress = Output(UInt(16.W))

    // 探针（纯组合）：stallA = sLOAD_A aPop 未 fire，stallB = loadW 窗口停拍 ∨ 预蓄等待
    val perf = Output(new Bundle {
      val stallA = Bool()
      val stallB = Bool()
    })
  })

  val sIDLE            = 0.U(4.W)
  val sLOAD_B          = 1.U(4.W)
  val sLOAD_A          = 2.U(4.W)
  val sNEXT_NK_TILE    = 3.U(4.W)
  val sDRAIN           = 4.U(4.W)


  val state = RegInit(sIDLE)

  val totalN = cfg.totalN

  val nTiles   = Reg(UInt(16.W))
  val kTiles   = Reg(UInt(16.W))
  val curNTile = Reg(UInt(16.W))
  val curKTile = Reg(UInt(16.W))
  
  val doneTiles = Reg(UInt(16.W))

  val curM = Reg(UInt(16.W))
  val depthOfCBufferUsed = Reg(UInt(16.W))

  val tileInterCnt = RegInit(0.U(log2Up(cfg.totalN).W))
  // drain 计数位宽按 loadW 模式最大 drain 值 2*(totalN-1)+1 取，垂直模式 totalN-1 亦在内
  val drainNextCnt = RegInit(0.U(log2Up(2 * cfg.totalN).W))
  val hasNext = RegInit(false.B)
  // 水平 loadW 模式锁存（execute 拍采样 regs(7)(5)；垂直模式恒 false）
  val loadWModeReg = RegInit(false.B)

  // mxRunB —— 运行期唯一 MX 判据（消费方 = scaleB pop 握手门 + lock 拍 valid 断言），
  //   以 aFmt 为权威（与 MAC 的 sumMx 消费门同源）⟹「握手 fire ⇔ scaleB 被消费 ⇔
  //   sumMx 消费」构成结构恒等式；判据分裂会使阵列锁存陈旧 scale 而 MAC 真实消费它。
  val mxRunB = DataFormat.isMx(io.regFile.aFmt)

  // 运行期纯 FP8 判据（aFmt 权威）：纯 FP8 时 mxRunB=false 而阵列 fmt 已被 remap 成
  //   MXE4M3/E5M2、MAC 真实消费 scale ⟹ 两注入点必须组合直供 127（E8M0 2^0=1.0）。
  val fp8Run = if (cfg.hasFp8) DataFormat.isFp8(io.regFile.aFmt) else false.B

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
    io.wbAIn(i) := io.aPop.payload(cfg.aEffW * (i+1)-1, cfg.aEffW * i)
    if(cfg.hasMx){
      val block = Mux(io.regFile.aMxBlockSize === MXBlockSize.M16, 16.U, 32.U)
      val step = Mux(io.calcConfig.isAX2, 2.U, 4.U)
      val idx = (i.U * step) / block
      // apply(hi, lo) 只接受字面量、不支持动态位选 ⟹ 取第 idx 个 8-bit scale 字节的
      //   唯一等价形态 = 右移 idx*8 后取低 8 位；两臂显式 8-bit 防 Mux 静默零扩。
      val scaleAByte = (io.scaleAPop.get.payload >> (idx * 8.U))(7, 0)
      io.wbScaleAIn.get(i) := (if (cfg.hasFp8) Mux(fp8Run, 127.U(8.W), scaleAByte) else scaleAByte)
    }
  }

  // B scale feed —— 轴向语义（勿按直觉改动）：lane 是 K-lane 索引而非权重行(N)索引，
  //   承载 b beat 的 K 元素 [kt*kPerBeat + lane*step, ... + step-1]，故 idx =
  //   (lane*step)/blk 选中该 lane 所属 MX 块的 E8M0 字节。wbScaleBIn(lane) → 列 y=0 的
  //   WbCell(x=lane) 再沿 a 链右移；loadW 模式下 PE(x,y) 的权重锁自 beat (totalN-1-y) 的
  //   lane x，scaleB 链与 a 链 lockstep ⟹ 同一 loadWLock 拍捕获同 n_out、同 lane、同 K
  //   块的 scale，零 skew。故阵列行坐标 x 即 K-lane 轴、列坐标 y 即输出 N 轴。
  if(cfg.hasMx){
    val block = Mux(io.regFile.bMxBlockSize === MXBlockSize.M16, 16.U, 32.U)
    // W4A8 混配时 bFmt 仍为 E2M1 ⟹ isBX2=false，但 B DMA 走 stretch 每拍恒推进 32 个 K
    //   元素 ⟹ 每 lane 每拍 2 个 K、与纯 MX8 同构 ⟹ step=2；scaleB 侧按 fp8 语义切片。
    val step = if (cfg.hasMx4)
      Mux(io.calcConfig.isBX2 || (DataFormat.isMx8(io.regFile.aFmt) && DataFormat.isMx4(io.regFile.bFmt)), 2.U, 4.U)
    else Mux(io.calcConfig.isBX2, 2.U, 4.U)

    for (lane <- 0 until cfg.totalN) {
      val idx = (lane.U * step) / block
      // 同 A 侧：动态字节选择 = 右移 idx*8 后取低 8 位；fp8Run 时恒 127 注入
      val scaleBByte = (io.scaleBPop.get.payload >> (idx * 8.U))(7, 0)
      io.wbScaleBIn.get(lane) := (if (cfg.hasFp8) Mux(fp8Run, 127.U(8.W), scaleBByte) else scaleBByte)
    }
  }

  // bias 基底注入（零加法器）：取代 sLOAD_A pass-0 的 psum 零基底，沿既有 psum 逐列链进
  // 阵列累加。psumIn 是输出-N 轴逐列注入 ⟹ biasRegs(i) 即输出列 nt*totalN+i；bias 是列
  // 常量、bSkew 的逐列延迟不改变取值。K>totalN 的后续 pass 走 cPop 回灌。
  private val biasRegs   = if (cfg.hasMx) Some(RegInit(VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))) else None
  private val biasLoaded = if (cfg.hasMx) Some(RegInit(false.B)) else None
  private val biasCnt    = if (cfg.hasMx) Some(RegInit(0.U(log2Up(cfg.totalN).W))) else None

  // rqM 收集寄存器（与 bias 同构；M 向量不进阵列，经 rqMOut 接至 WbRequant，无 lazy 转换）
  private val rqMRegs   = if (cfg.hasMx) Some(RegInit(VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))) else None
  private val rqMLoaded = if (cfg.hasMx) Some(RegInit(false.B)) else None
  private val rqMCnt    = if (cfg.hasMx) Some(RegInit(0.U(log2Up(cfg.totalN).W))) else None
  // rqM 暂存-提交双缓冲：消费者在 WbOutCvt in.fire（C 写回窗口末尾晚到），重取却触发在
  //   sNEXT_NK_TILE（窗口开门拍），原地重载会覆盖仍在排空路径上的行将锁存的 M。故字流先
  //   落 staging、rqMRegs 仅在提交点整组更新：cPeekDone 恰为 cvt 消费窗口边界 ⟹ 两侧分别
  //   看到旧/新 M 无中间态，feedBase 的 rqMLoaded 门控保证 staging 收齐早于该 tile 过 cvt。
  private val rqMStaging     = if (cfg.hasMx) Some(RegInit(VecInit(Seq.fill(cfg.totalN)(0.U(32.W))))) else None
  private val rqMCommitArmed = if (cfg.hasMx) Some(RegInit(false.B)) else None
  // rqM 提交点：C 块 doneBlock 布防、rqMLoaded 即整组提交；本块须在上述声明之后（Chisel
  //   when 在 Scala 求值期建图，先引用会 NPE）。
  if (cfg.hasMx) {
    when(io.cPeekDone) { rqMCommitArmed.get := true.B }
    when(rqMCommitArmed.get && rqMLoaded.get) {
      rqMRegs.get := rqMStaging.get
      rqMCommitArmed.get := false.B
    }
  }

  /** fp32 → lazy40 累加基底：e==0（含全部 subnormal）→ 绝对 0；否则
    * {s, e, 隐含 1, m, 0^(cEffW-33)}，在 cEffW 内右侧对齐、高位补零。 */
  private def fp32ToLazy40(x: UInt): UInt = {
    require(cfg.cEffW >= 40, s"WorkerBeeExecutor fp32ToLazy40 requires cEffW(${cfg.cEffW}) >= 40 (lazy fp32 accumulator)")
    val e = x(30, 23)
    Mux(e === 0.U, 0.U(cfg.cEffW.W), Cat(x(31), e, 1.U(1.W), x(22, 0), 0.U((cfg.cEffW - 33).W)))
  }

  /** bias 32b 字 → cEffW 累加基底：INT 运行按 INT32 符号扩展、fp/MX 运行按 fp32 →
    * lazy40。runIsInt 必须含 !isFp8，否则纯 FP8 被误判 INT 族、bias 错走 sext32。 */
  private def biasLazy(i: Int): UInt = {
    val runIsInt = !DataFormat.is2B(io.regFile.aFmt) && io.regFile.aFmt =/= DataFormat.FP32 &&
      !DataFormat.isMx(io.regFile.aFmt) && !DataFormat.isFp8(io.regFile.aFmt)
    val w = biasRegs.get(i)
    Mux(runIsInt, Cat(Fill(cfg.cEffW - 32, w(31)), w), fp32ToLazy40(w))
  }

  if (cfg.hasMx) {
    // bias 收集：16 字齐 → biasLoaded 置位（sLOAD_A 供数门控放行）；ready 与状态无关
    io.biasIn.get.ready := io.regFile.biasOn && !biasLoaded.get
    when(io.biasIn.get.fire) {
      biasRegs.get(biasCnt.get) := io.biasIn.get.payload
      when(biasCnt.get === (cfg.totalN - 1).U) {
        biasLoaded.get := true.B
        biasCnt.get := 0.U
      }.otherwise {
        biasCnt.get := biasCnt.get + 1.U
      }
    }

    // rqM 收集：字流落 staging（16 字齐 → rqMLoaded；rqMRegs 只在提交点整组更新）
    io.rqMIn.get.ready := io.regFile.requantOn && !rqMLoaded.get
    when(io.rqMIn.get.fire) {
      rqMStaging.get(rqMCnt.get) := io.rqMIn.get.payload
      when(rqMCnt.get === (cfg.totalN - 1).U) {
        rqMLoaded.get := true.B
        rqMCnt.get := 0.U
      }.otherwise {
        rqMCnt.get := rqMCnt.get + 1.U
      }
    }
    io.rqMOut.get := rqMRegs.get
  }


  io.cPush.valid := io.wbValidOut
  io.cPush.payload := Cat(io.wbCOut.reverse)
  assert(!(io.cPush.valid & !io.cPush.ready), "WorkerBeeExecutor: cPush valid but C buffer not ready (pop-before-push invariant violated, partial sum lost)")

  
  io.busy := state =/= sIDLE
  io.done := false.B

  io.aPop.ready  := false.B
  io.bPop.ready  := false.B
  io.cPop.ready  := false.B
  if(cfg.hasMx){
    io.scaleAPop.get.ready := false.B
    io.scaleBPop.get.ready := false.B
    io.biasFetchReq.get := false.B
    io.biasFetchNt.get := 0.U
    io.rqMFetchReq.get := false.B
    io.rqMFetchNt.get := 0.U
  }

  io.flushA := false.B
  io.flushB := false.B
  io.flushC := false.B

  io.wbLoadH   := false.B
  io.wbLoadV   := false.B
  io.wbLoadVLock   := false.B
  io.wbLoadW       := false.B
  io.wbLoadWLock   := false.B
  io.wbValidIn := false.B
  io.wbClear   := false.B

  io.progress := doneTiles


  for (i <- 0 until cfg.totalN) {
        io.wbPsumIn(i) := 0.U
  }


  switch(state) {


    is(sIDLE) {

      curNTile := 0.U
      curKTile := 0.U
      curM := 0.U
      doneTiles := 0.U
      io.wbClear := true.B
      hasNext := false.B
      drainNextCnt := 0.U
        // nTiles/kTiles 是 Reg（本拍 := 下一拍才生效），故容量推导必须用本拍的 new* 值
        val newNTiles = (io.regFile.n(15, 0) + totalN.U - 1.U) / totalN.U
        // kTiles 分母看打包格式：半字节族 kPerBeat=totalN×4、单字节族 totalN×2，非打包
        //   走 ceil(K/totalN)；与 WorkerBeeCalcConfig.kTile 同源
        val isPackedA = DataFormat.is1B(io.regFile.aFmt) || DataFormat.isHalfB(io.regFile.aFmt)
        val newKTiles = Mux(isPackedA,
          Mux(DataFormat.isHalfB(io.regFile.aFmt),
              io.regFile.k(15, 0) / (totalN.U * 4.U),   // INT4 / MXE2M1: K/(totalN*4)
              io.regFile.k(15, 0) / (totalN.U * 2.U)),  // INT8 / MX8:    K/(totalN*2)
          (io.regFile.k(15, 0) + totalN.U - 1.U) / totalN.U)
        nTiles   := newNTiles - 1.U
        kTiles   := newKTiles - 1.U
        // 容量检查：C buffer 驻留量只有一组 partial sum（m 行）——非首 pass 的 pop 与
        // push 等量交替、占用恒定，故 kTiles 不乘进公式。
        depthOfCBufferUsed := io.regFile.m
        tileInterCnt := totalN.U - 1.U

      when(io.execute) {
        // flushA 仅允许在 execute 拍拉高：aDma 内建 nTile 轮次连续供数，per-tile flush
        // 会丢弃已预取数据且 RdOnly 不重发 → 死锁；下方 assert 守新 execute 拍 A 已排空
        assert(io.aOccupancy === 0.U, "WorkerBeeExecutor: A buffer not drained at execute pulse (previous run aDma push rows != executor consume rows)")
        io.flushA := true.B
        io.flushB := true.B
        io.flushC := true.B
        // gather 读使供数可低至 0.5 字/拍，而 loadW 窗口要求 bPop 严格逐拍（断拍破坏权重
        // 对角线与 scaleB 空间对齐），故统一经 sDRAIN 门控预蓄后再进窗口，不直入 sLOAD_B。
        hasNext  := true.B
        state    := sDRAIN
      }

      when(io.execute) {
        loadWModeReg := io.regFile.loadWMode
      }

      // bias 首拍取数触发：请求 nt=0 的 16 字；biasLoaded/biasCnt 同拍清零
      if (cfg.hasMx) {
        when(io.execute && io.regFile.biasOn) {
          io.biasFetchReq.get := true.B
          io.biasFetchNt.get := 0.U
          biasLoaded.get := false.B
          biasCnt.get := 0.U
        }
        // rqM 首拍取数触发：与 bias 同拍发；首块无 doneBlock 前驱 ⟹ execute 拍同步布防
        when(io.execute && io.regFile.requantOn) {
          io.rqMFetchReq.get := true.B
          io.rqMFetchNt.get := 0.U
          rqMLoaded.get := false.B
          rqMCnt.get := 0.U
          rqMCommitArmed.get := true.B
        }
      }

    }


    // loadH 在整个加载窗口保持拉高（fmt/rnd 配置需时间经阵列横向传播）；loadV 跟随
    // bPop 数据有效性：B buffer 暂无数据时自然暂停、喂数到位后自动恢复。
    is(sLOAD_B) {
      io.wbLoadH := true.B
      io.wbLoadV := io.bPop.fire

      if(cfg.hasMx){
        // MX 运行时 scaleBPop 与 bPop 必须同拍 lockstep fire，两侧 ready 同取
        //   (bPop.valid & scaleBPop.valid)；非 MX 运行 scaleBPop 恒不 ready。
        when(mxRunB){
          io.scaleBPop.get.ready := io.bPop.valid & io.scaleBPop.get.valid
          io.bPop.ready          := io.bPop.valid & io.scaleBPop.get.valid
        }.otherwise{
          io.scaleBPop.get.ready := false.B
          io.bPop.ready          := true.B
        }
        // Lockstep guard (only elaborated when hasMx, only evaluated when mxRunB).
        //   "bPop and scaleBPop fire on the same cycle" is the premise of the whole scaleB
        //   path: wbScaleBIn is a pure combinational read of scaleBPop.payload, so
        //   decoupling the two ready terms misaligns the per-lane scaleB chain.
        when(mxRunB) {
          // (1) fire XOR guard: both sides must fire on the same cycle
          assert(!(io.bPop.fire ^ io.scaleBPop.get.fire),
            "WorkerBeeExecutor: MX scaleB lockstep violated - bPop.fire XOR scaleBPop.fire (must fire same cycle, else the per-lane scaleB chain misaligns against the weight diagonal at loadWLock)")
          // (2) lock-beat scaleB valid guard: wbLoadWLock fires combinationally on the
          //     last beat while every lane reads scaleBPop.payload that very cycle, so an
          //     invalid payload makes every scaleBReg latch stale FIFO content.
          when(loadWModeReg && io.bPop.fire && tileInterCnt === 0.U) {
            assert(io.scaleBPop.get.valid,
              "WorkerBeeExecutor: MX lock beat scaleBPop not valid - scaleBReg would latch stale FIFO payload on loadWLock")
          }
        }
      }
      else{
        io.bPop.ready := true.B
      }
      

      for (i <- 0 until cfg.totalN) {
        io.wbPsumIn(i) := io.bPop.payload(cfg.bW * (i + 1) - 1, cfg.bW * i)
      }

      
      when(io.bPop.fire) {
        when(tileInterCnt =/= 0.U){
          tileInterCnt := tileInterCnt - 1.U
        }.otherwise{
          tileInterCnt := totalN.U - 1.U
          state := sLOAD_A
          io.wbLoadVLock := true.B
        }
      }


      // 水平 loadW 覆盖块（last-connect；loadWModeReg=0 时整块死路、上方垂直通路即生效
      //     路径）：权重经 wbAIn 的 a 链水平加载（脉冲链移位 + 末拍 loadWLock 广播锁存），
      //     psumIn 保持 0、loadV 压制为 0；计数与握手共用上方
      when(loadWModeReg) {
        io.wbLoadV     := false.B
        io.wbLoadW     := io.bPop.fire   // 水平移位脉冲：停顿自然暂停
        io.wbLoadVLock := false.B
        for (i <- 0 until cfg.totalN) {
          // 权重经 a 口供数（bPop 的 totalN 个 bW 切片，aEffW > bW 时高位自动补零）
          io.wbAIn(i)    := io.bPop.payload(cfg.bW * (i + 1) - 1, cfg.bW * i)
          io.wbPsumIn(i) := 0.U
        }
        // Last beat fire: loadWLock must be emitted on this very cycle, combinationally
        // with the fire cycle of the last beat entering column 0 - the unique moment of
        // the diagonal landed state.
        when(io.bPop.fire && tileInterCnt === 0.U) {
          io.wbLoadWLock := true.B
        }
      }

    }

    is(sLOAD_A){
      val needPartialSum = curKTile > 0.U

      // 需要 partial sum 时必须占用 c 口读出口 ⟹ 等 CDMA 搬空 cbuffer 让出读口；
      // biasOn/requantOn 且未收齐时挂起供数（aPop/scaleA 均可反压，挂起安全）
      val feedBase = (cBufferReservedAtleastTotalN) & Mux(needPartialSum, cStoreGateReg === false.B, true.B) &
        (if (cfg.hasMx) !(io.regFile.biasOn && !biasLoaded.get) else true.B) &
        (if (cfg.hasMx) !(io.regFile.requantOn && !rqMLoaded.get) else true.B)
      // MX：激活消费与 scaleA 严格 lockstep——scaleA FIFO 尚空时消费首个激活行会使全体
      //   scaleA 错位一行（row m 误用 row m-1 的 scale），故 isMx 时并入 scaleAPop.valid。
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
      io.wbValidIn := io.aPop.fire 
      
      

      for(i <- 0 until cfg.totalN){
        when(needPartialSum){
          io.wbPsumIn(i) := io.cPop.payload(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
        }
      }

      // pass-0 基底注入：bias 取代零基底经既有 psum 逐列链下发（与 needPartialSum
      //   回灌支路互斥：pass-0 注 bias、后续 pass 回灌 partial sum）
      if (cfg.hasMx) {
        when(!needPartialSum && io.regFile.biasOn) {
          for (i <- 0 until cfg.totalN) {
            io.wbPsumIn(i) := biasLazy(i)
          }
        }
      }

      when(io.aPop.fire){
        curM := curM + 1.U
      }

      // 排空窗口结束才离开计算状态，保证波前完整流过阵列；离场必须与 aPop.fire 原子门控，
      // 否则 m=1 会在未消费任何 A 数据的当拍离场（死锁）、m>1 每 tile 末行少供一行。
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
        // 门吞没竞态：上一块 drain 未完（cStoreGateReg 仍高）时拉门无上升沿，随后上一块
        // doneBlock 落下共享寄存器 → 本块门被永久吞掉、结果永不写回。故等 cPeekDone 清门
        // 后再升门；副作用置于通过分支内，防停留拍重复计数。
        when(cStoreGateReg === false.B){
          doneTiles := doneTiles + 1.U
          drainNextCnt := cfg.totalN.U - 1.U
          curKTile := 0.U
          cStoreGateReg := true.B
          when(curNTile < nTiles){
            curNTile := curNTile + 1.U
            hasNext := true.B
            state := sDRAIN
            // 下一 N-tile 的 bias 重取；k 推进分支不取——biasRegs 跨全部 K pass 驻留复用
            if (cfg.hasMx) {
              when(io.regFile.biasOn) {
                io.biasFetchReq.get := true.B
                io.biasFetchNt.get := curNTile + 1.U
                biasLoaded.get := false.B
                biasCnt.get := 0.U
              }
              // 下一 N-tile 的 rqM 重取（字流落 staging，提交点见 rqMStaging 声明处）
              when(io.regFile.requantOn) {
                io.rqMFetchReq.get := true.B
                io.rqMFetchNt.get := curNTile + 1.U
                rqMLoaded.get := false.B
                rqMCnt.get := 0.U
              }
            }
          }.otherwise{
            curNTile := 0.U
            state := sDRAIN
          }
        }
      }
    }
    

    is(sDRAIN) {
  
      when(hasNext){
        when(drainNextCnt =/= 0.U){
          drainNextCnt := drainNextCnt - 1.U
        }.elsewhen(io.bOccupancy >= cfg.totalN.U && (!mxRunB || io.scaleBOcc.get >= cfg.totalN.U)){
          // 进入 sLOAD_B 的预蓄条件：① B FIFO ≥ totalN（窗口内 bPop 逐拍消费 totalN 拍，
          //   最坏 2 拍/字、缺口 ≤ 8 字）；② MX 另需 scaleB FIFO ≥ totalN（与 bPop 同拍
          //   消费）。满足后窗口内 pop 严格逐拍 ⟹ 空间对齐不被破坏。
          drainNextCnt := totalN.U - 1.U
          // 水平 loadW 模式 drain 重载（与 sNEXT_NK_TILE 初值一致）
          when(loadWModeReg) {
            drainNextCnt := (2 * (totalN - 1) + 1).U
          }
          hasNext := false.B
          state := sLOAD_B
        }
      }.otherwise{
  
      val anyNewValid = io.wbValidOut.asUInt.orR
      when(!anyNewValid & !io.dmaCBusy) {
        io.done := true.B
        state := sIDLE
      }
      }

    }

  }

  // perf 观测探针（纯组合、无状态）：bWindowReady 与 sDRAIN→sLOAD_B 转移条件同源；
  //   stallB 的 sDRAIN 项仅计 drain 计数归零后的纯等待拍。
  val bWindowReady = io.bOccupancy >= cfg.totalN.U &&
    (if (cfg.hasMx) (!mxRunB || io.scaleBOcc.get >= cfg.totalN.U) else true.B)
  io.perf.stallA := state === sLOAD_A && !io.aPop.fire
  io.perf.stallB := (state === sLOAD_B && !io.bPop.fire) ||
    (state === sDRAIN && hasNext && drainNextCnt === 0.U && !bWindowReady)


}
