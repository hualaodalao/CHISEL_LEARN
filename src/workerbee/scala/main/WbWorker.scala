/** WbWorker（工蜂）- Weight-Stationary 处理单元
  *
  * 权重驻留，无独立 wIn/wOut 端口：权重经 psumIn（loadV 垂直加载）进内部 wReg；计算时
  *   aIn 水平传播（aEffW 位）、psumIn 垂直传播（cEffW 位，兼作权重加载通道），权重不动。
  *   loadHIn 仅配置 fmt/rnd、loadVIn 加载权重，二者由 WbCell 广播；valid/fmt/rnd 水平
  *   传播；clear 标量广播清零 psumReg。
  *
  * 权重加载协议（双模式，靠 loadV/loadW 信号活跃性二选一，硬件断言守护互斥）：
  *   垂直模式（默认）：loadHIn=1 锁存 fmt/rnd 配置（不动 wReg）；loadVIn=1 从 psumIn 低位
  *     锁存 wReg 并透传 psumIn 使权重下沉；加载阶段同时拉高 loadH+loadV，aIn 无需供数。
  *   水平 loadW 模式：loadWIn 脉冲每拍使权重沿 a 链右移一列（停顿自然保持），末拍
  *     loadWInLock 广播拍从 aIn(bW-1,0) 锁存 wReg —— 对角线落位态的唯一时刻。
  *     垂直模式下 loadW 信号组恒 0。
  *
  * psumReg 优先级：clear → 0；loadVIn → psumIn（加载期透传）；validIn && fmtOk →
  *   accumulate(psumIn, product, fmtReg, rndReg)；否则保持。psumOut := psumReg。
  *
  * @param aW            激活位宽（默认 16）
  * @param bW            权重位宽（默认 16）
  * @param cW            累加器/部分和位宽（默认 48）
  * @param supportedFmts 支持的数据格式集合；仅为其中格式生成 MAC 硬件，运行期 fmtReg
  *                      不在集合内则不累加、psumReg 保持。
  */

import chisel3._
import chisel3.util._

class WbWorker(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)
) extends Module {
  val aEffW: Int = math.max(aW, bW)
  val cEffW: Int = math.max(cW, bW)

  private val hasFp  = supportedFmts.exists(f => f == DataFormat.FP16 || f == DataFormat.BF16)
  // 整数族为 {INT8, INT4}，与 WorkerBeeConfig.hasInt 同口径。
  private val hasInt = supportedFmts.exists(f => f == DataFormat.INT8  || f == DataFormat.INT4)

  // 含 FP 族直接例化须显式 cW>=40，否则构造期失败；顶层经 WbComb 链传 cfg.cEffW。
  if (hasFp)  require(cEffW >= 40,          s"WbWorker: 含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aEffW + bW,  s"WbWorker: 含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    val aIn  = Input(UInt(aEffW.W))
    val aOut = Output(UInt(aEffW.W))
    val psumIn  = Input(UInt(cEffW.W))
    val psumOut = Output(UInt(cEffW.W))

    // 水平加载（配置）：由 WbCell 广播到所有 PE，无水平传播输出
    val loadHIn  = Input(Bool())

    // 垂直加载（仅权重）：由 WbCell 垂直传递，拉高期间传递
    val loadVInLock = Input(Bool()) //垂直锁存,脉冲
    val loadVIn  = Input(Bool())
    val loadVOut  = Output(Bool())

    // 水平 loadW 加载（与垂直加载二选一）：loadWIn 沿 a 数据链逐列传播（每脉冲权重右移
    // 一列，停顿保持）；loadWInLock 广播脉冲拍从 aIn 低位锁存 wReg。垂直模式下恒 0。
    val loadWIn     = Input(Bool())
    val loadWOut    = Output(Bool())
    val loadWInLock = Input(Bool())

    // 控制 / 格式 / 舍入模式：水平传播（RegNext → 右侧 WbWorker）
    val validIn  = Input(Bool())
    val validOut = Output(Bool())
    val validInV  = Input(Bool())
    val validOutV = Output(Bool())

    val fmtIn  = Input(DataFormat())
    val fmtOut = Output(DataFormat())
    // 权重格式：mixFmtEn=0 时由上层令 bFmtIn 跟随 fmtIn
    val bFmtIn  = Input(DataFormat())
    val bFmtOut = Output(DataFormat())
    // MX scale: scaleAIn flows with the activations (lockstep with aReg) and is
    // consumed COMBINATIONALLY by the MAC. scaleBIn shifts right along the same
    // a-chain but is CAPTURED into scaleBReg on the load lock beat; it is resident
    // per K-lane (coordinate x), never per output-N column (coordinate y).
    val scaleAIn  = Input(UInt(8.W))
    val scaleAOut = Output(UInt(8.W))
    val scaleBIn  = Input(UInt(8.W))
    val scaleBOut  = Output(UInt(8.W))
    val rndIn   = Input(RoundingMode())
    val rndOut  = Output(RoundingMode())
    val clear   = Input(Bool())
  })

  val fmtInitActive = RegInit(false.B)
  val rndInitActive = RegInit(false.B)

  // loadW 端口 WireDefault 兜底：直接例化本 PE 而不连 loadW 口时回落恒 0；
  //   WbCell 正常驱动时兜底被覆盖。
  val loadWInSafe = WireDefault(false.B)
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  // 内部配置寄存器（无 RegInit，依赖显式初始化）
  val fmtReg = Reg(DataFormat())
  val rndReg = Reg(RoundingMode())
  val bFmtReg = Reg(DataFormat())
  // scaleB resident register: holds the E8M0 scale of this PE's K-lane for the whole
  // weight-resident window, latched on the load LOCK beat strictly together with wReg.
  val scaleBReg = RegInit(0.U(8.W))
  // scaleA flow register: lockstep with aReg. The MAC consumes io.scaleAIn
  // combinationally, NOT scaleAReg; scaleAReg only drives scaleAOut propagation.
  val scaleAReg = RegInit(0.U(8.W))

  // 按 litValue 排序后归约：supportedFmts 作为 HashSet 迭代顺序逐实例不同，而 firtool
  //   dedup 不识别 OR 交换律等价，会把同构 PE 判为不同模块。
  val fmtOk = supportedFmts.toSeq.sortBy(_.litValue).map(f => fmtReg === f).reduce(_ || _)

  val wReg = RegInit(0.U(bW.W))

  // wReg / fmtReg / rndReg 锁存：权重经 loadV 从 psumIn 低位锁存，loadH 仅锁存 fmt/rnd；
  //   加载权重时同时拉高 loadH 与 loadV。
  when(io.loadVInLock) {
    wReg := io.psumIn(bW - 1, 0)
  }
  // 水平 loadW 模式并列分支（last-connect 覆盖）：loadWInLock 广播拍从 aIn 低位锁存
  // 权重；垂直模式下 loadWInLock 恒 0，本分支为死路。
  when(loadWInLockSafe) {
    wReg := io.aIn(bW - 1, 0)
  }
  assert(!(io.loadVInLock && loadWInLockSafe), "WbWorker: loadVInLock and loadWInLock asserted simultaneously (mutually exclusive load modes)")
  when(io.loadHIn) {
    fmtReg := io.fmtIn
    rndReg := io.rndIn
    bFmtReg := io.bFmtIn
  }
  // scaleBReg 必须与 wReg 严格同拍锁存（Lock 脉冲拍），不能随 loadHIn 全窗口跟踪：
  //   loadHIn 在下一 K-块 sLOAD_B 起始拍即拉高而 wReg 要到窗口末 Lock 拍才更新，错拍
  //   窗口内仍在阵列深层排空的上一块乘积尾会持旧权重读到新块 scaleB → 高 (m+n) 对角错位。
  when(io.loadVInLock || loadWInLockSafe) {
    scaleBReg := io.scaleBIn
  }

  // 首次 load 时标记初始化完成（配置随 loadH 锁存）
  when(io.loadHIn && !fmtInitActive) {
    fmtInitActive := true.B
  }
  when(io.loadHIn && !rndInitActive) {
    rndInitActive := true.B
  }

  // 可重构 MAC：WbWorkUnit 是纯组合 MAC，PE 级状态（wReg/psumReg/fmtReg/rndReg）留本模块
  val workUnit = Module(new WbWorkUnit(aW, bW, cW, supportedFmts))
  workUnit.io.a    := io.aIn(aW - 1, 0)
  workUnit.io.b    := wReg
  workUnit.io.cReg := io.psumIn
  workUnit.io.aFmt := fmtReg
  workUnit.io.bFmt := bFmtReg
  // MAC 的 scaleA 必须与激活输入 io.aIn 同拍组合直连，而非已寄存的 scaleAReg（后者仅供
  //   scaleAOut 右传）；否则本 PE 乘积会误用上一激活行的 scale。
  workUnit.io.scaleA := io.scaleAIn
  workUnit.io.scaleB := scaleBReg
  workUnit.io.rnd  := rndReg
  workUnit.io.consumeValid := io.validIn && fmtOk && DataFormat.isMx(fmtReg)

  val psumReg = RegInit(0.U(cEffW.W))
  when(io.clear) {
    psumReg := 0.U
  }.elsewhen(io.loadVIn){
    psumReg := io.psumIn
  }.elsewhen(io.validIn && fmtOk) {
    psumReg := workUnit.io.sum
  }

  // psumOut 始终输出寄存值：行式权重加载协议（每权重行 2 拍，偶数拍供数 + loadV 脉冲
  //   锁存/下沉，奇数拍保持）依赖 psumReg 逐拍寄存使权重每拍下沉一行；若做组合旁路，
  //   脉冲拍的当前供数会穿透到底部簇，深层 PE 会锁存到错误权重。
  io.psumOut := psumReg

  // aOut：激活水平传播
  val aReg = RegInit(0.U(aEffW.W))
  when(io.clear) {
    aReg := 0.U
  }.elsewhen(io.loadWIn | io.validIn){
    aReg := io.aIn
  }
  
  io.aOut := aReg

  // scaleAOut：scaleA 随激活流，与 aReg lockstep
  when(io.clear) {
    scaleAReg := 0.U
  }.elsewhen(io.loadWIn | io.validIn){
    scaleAReg := io.scaleAIn
  }
  io.scaleAOut := scaleAReg
  io.scaleBOut := RegNext(io.scaleBIn)

  io.validOut := RegNext(io.validIn, false.B)
  io.validOutV := RegNext(io.validInV, false.B)
  io.loadVOut  := RegNext(io.loadVIn, false.B)
  io.loadWOut  := RegNext(loadWInSafe && !loadWInLockSafe, false.B)
  io.fmtOut   := RegNext(io.fmtIn, DataFormat.INT8)
  io.bFmtOut  := RegNext(io.bFmtIn, DataFormat.INT8)
  io.rndOut   := RegNext(io.rndIn, RoundingMode.RNE)
}
