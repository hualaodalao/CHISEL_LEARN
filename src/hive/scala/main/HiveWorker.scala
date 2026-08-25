/** HiveWorker（工蜂）- Weight-Stationary 处理单元
  *
  * 架构定位：
  *   权重驻留（Weight-Stationary）HiveWorker，无独立 wIn/wOut 端口。
  *   权重仅通过 psumIn（loadV 垂直加载）通道加载到内部 wReg；
  *   计算时 aIn 水平传播、psumIn 垂直传播，权重不动、数据流过。
  *
  * @param aW            激活位宽（默认 16）
  * @param bW            权重位宽（默认 16）
  * @param cW            累加器/部分和位宽（默认 48）
  * @param supportedFmts 本 HiveWorker 支持的数据格式集合（默认全部四种）。
  *                      仅为支持的格式生成 MAC 硬件；运行时 fmtReg 不在 supportedFmts 中则
  *                      不执行累加，psumReg 保持。
  *
  * 新架构要点：
  *   - fmtReg / rndReg：随 loadH 锁存的配置，后续计算使用内部寄存器
  *   - 权重加载不经过 ShiftRegister：权重沿 psum PE 链自然向下传播
  *   - loadH 与 loadV 职责分离：loadH 只加载配置，loadV 只加载权重
  *   - 权重加载时同时拉高 loadH（刷新 fmt/rnd）与 loadV（锁存权重）
  *
  * IO 端口语义：
  *   aIn  / aOut   : 激活通道，水平传播（左→右），aEffW 位。
  *   psumIn / psumOut : 部分和通道，垂直传播（上→下），cEffW 位；兼作权重加载通道。
  *   loadHIn  : 水平加载使能（仅配置 fmt/rnd），由 HiveCell 广播（无 loadHOut）。
  *   loadVIn  : 垂直加载使能（权重），由 HiveCell 广播（无 loadVOut）。
  *   validIn / validOut : 数据有效标志，水平传播。
  *   fmtIn   / fmtOut   : 数据格式选择，水平传播。
  *   rndIn   / rndOut   : 舍入模式选择，水平传播。
  *   clear              : psumReg 清零，标量广播。
  *
  * 权重加载协议（双模式，靠 loadV/loadW 信号活跃性自然二选一）：
  *   垂直模式（默认）：
  *   - loadHIn=true：锁存 fmt/rnd 配置（不动 wReg）
  *   - loadVIn=true：从 psumIn 低位锁存权重进 wReg，并透传 psumIn 使权重下沉
  *   - 典型用法：加载阶段同时拉高 loadH+loadV，psumIn 供权重，aIn 无需供数
  *   水平 loadW 模式：
  *   - 权重经 a 数据链水平加载：loadWIn 脉冲每拍使权重沿 a 链右移一列
  *     （aLoadWReg 门控移位，停顿自然保持），末拍 loadWInLock 广播拍
  *     从 aIn(bW-1,0) 锁存 wReg（对角线落位态唯一时刻）
  *   - 两模式互斥（硬件断言守护），垂直模式下 loadW 信号组恒 0
  *
  * wReg / 配置锁存逻辑：
  *   loadV：wReg := psumIn 低位
  *   loadH：fmtReg := fmtIn，rndReg := rndIn
  *
  * psumReg 逻辑（优先级从高到低）：
  *   1. when(clear)                       → psumReg := 0
  *   2. elsewhen(loadVIn)                 → psumReg := psumIn（加载期间透传）
  *   3. elsewhen(validIn && fmtOk)        → psumReg := accumulate(psumIn, product, fmtReg, rndReg)
  *   4. otherwise                         → 保持
  *
  * 输出驱动：
  *   aOut   := RegNext(aIn, 0.U)
  *   psumOut := psumReg（loadVIn 期间 psumReg := psumIn 等效透传，loadVIn 撤掉后保持权重自然传播）
  */

import chisel3._
import chisel3.util._

class HiveWorker(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val aEffW: Int = math.max(aW, bW)
  val cEffW: Int = math.max(cW, bW)

  private val hasFp  = supportedFmts.exists(f => f == DataFormat.FP16 || f == DataFormat.BF16)
  private val hasInt = supportedFmts.exists(f => f == DataFormat.INT8  || f == DataFormat.INT16)

  if (hasFp)  require(cEffW >= 40,          s"HiveWorker: 含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aEffW + bW,  s"HiveWorker: 含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    // 激活通道（水平，aEffW 位）
    val aIn  = Input(UInt(aEffW.W))
    val aOut = Output(UInt(aEffW.W))
    // 部分和通道（纵向，cEffW 位）
    val psumIn  = Input(UInt(cEffW.W))
    val psumOut = Output(UInt(cEffW.W))

    // 水平加载（配置）：由 HiveCell 广播到所有 PE，无水平传播输出
    val loadHIn  = Input(Bool())

    // 垂直加载（仅权重）：由 HiveCell 垂直传递，拉高期间传递
    val loadVInLock = Input(Bool()) //垂直锁存,脉冲
    val loadVIn  = Input(Bool())
    val loadVOut  = Output(Bool())

    // 水平 loadW 加载（可选模式，与垂直加载二选一，靠信号活跃性自然选择）：
    // loadWIn 沿 a 数据链方向逐列链传播（每脉冲权重右移一列，停顿保持）；
    // loadWInLock 广播脉冲拍从 aIn 低位锁存 wReg（对角线落位态唯一时刻）。
    // 垂直模式下该信号组恒 0，相关分支全部为死路（行为 bit-exact 不变）
    val loadWIn     = Input(Bool())
    val loadWOut    = Output(Bool())
    val loadWInLock = Input(Bool())

    // 控制（水平传播：RegNext → 右侧 HiveWorker）
    val validIn  = Input(Bool())
    val validOut = Output(Bool())
    val validInV  = Input(Bool())
    val validOutV = Output(Bool())

    // 格式（水平传播：RegNext → 右侧 HiveWorker）
    val fmtIn  = Input(DataFormat())
    val fmtOut = Output(DataFormat())
    // 舍入模式（水平传播：RegNext → 右侧 HiveWorker）
    val rndIn   = Input(RoundingMode())
    val rndOut  = Output(RoundingMode())
    // psumReg 清零信号
    val clear   = Input(Bool())
  })

  // --- 初始化状态标志 ---
  val fmtInitActive = RegInit(false.B)
  val rndInitActive = RegInit(false.B)

  // --- loadW 端口 WireDefault 兜底：既有测试（SystolicArrayTest 等）直接
  // 例化本 PE 时不连接 loadW 口（invalid），兜底回落恒 0，保证垂直模式
  // bit-exact；HiveCell 正常驱动时兜底被覆盖 ---
  val loadWInSafe = WireDefault(false.B)
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  // --- 内部配置寄存器（无 RegInit，依赖显式初始化） ---
  val fmtReg = Reg(DataFormat())
  val rndReg = Reg(RoundingMode())

  // 格式支持检测（基于 fmtReg） ---
  val fmtOk = supportedFmts.map(f => fmtReg === f).reduce(_ || _)

  // --- 权重驻留寄存器 ---
  val wReg = RegInit(0.U(bW.W))

  // --- wReg / fmtReg / rndReg 锁存逻辑 ---
  // 权重仅经垂直加载（loadV 上升沿，从 psumIn 低位）；水平加载（loadH）仅锁存配置 fmt/rnd。
  // 二者独立：加载权重时同时拉高 loadH（刷新配置）与 loadV（锁存权重）。
  when(io.loadVInLock) {
    wReg := io.psumIn(bW - 1, 0)
  }
  // 水平 loadW 模式并列分支（last-connect 覆盖）：loadWInLock 广播拍从 aIn
  // 低位锁存权重。垂直模式 loadWInLock 恒 0，本分支为死路，wReg 行为不变
  when(loadWInLockSafe) {
    wReg := io.aIn(bW - 1, 0)
  }
  // 两种加载模式互斥：同一拍不允许 loadV 与 loadW 同时锁存
  assert(!(io.loadVInLock && loadWInLockSafe), "HiveWorker: loadVInLock and loadWInLock asserted simultaneously (mutually exclusive load modes)")
  when(io.loadHIn) {
    fmtReg := io.fmtIn
    rndReg := io.rndIn
  }

  // --- 首次 load 时标记初始化完成（配置随 loadH 锁存） ---
  when(io.loadHIn && !fmtInitActive) {
    fmtInitActive := true.B
  }
  when(io.loadHIn && !rndInitActive) {
    rndInitActive := true.B
  }

  // --- 可重构 MAC（使用 fmtReg / rndReg） ---
  // WorkUnit 已由函数实现重构为独立 HiveWorkUnit Module（便于综合面积
  // 占比分析）：纯组合 MAC，PE 级状态（wReg/psumReg/fmtReg/rndReg）仍留本模块
  val workUnit = Module(new HiveWorkUnit(aW, bW, cW, supportedFmts))
  workUnit.io.a    := io.aIn(aW - 1, 0)
  workUnit.io.b    := wReg
  workUnit.io.cReg := io.psumIn
  workUnit.io.fmt  := fmtReg
  workUnit.io.rnd  := rndReg

  val psumReg = RegInit(0.U(cEffW.W))
  when(io.clear) {
    psumReg := 0.U
  }.elsewhen(io.loadVIn){
    psumReg := io.psumIn
  }.elsewhen(io.validIn && fmtOk) {
    // 正常计算（使用 fmtReg 和 rndReg）
    psumReg := workUnit.io.sum
  }

  // --- psumOut ---
  // 始终输出寄存值。行式权重加载协议（loadV 广播脉冲，每权重行 2 拍：
  // 偶数拍供数 + loadV 脉冲锁存/下沉，奇数拍保持）依赖 psumReg 逐拍寄存
  // 使权重每拍下沉一行；若做组合旁路，脉冲拍当前供数会穿透到底部簇，
  // 深层 PE 锁存到错误权重
  io.psumOut := psumReg

  // --- aOut：激活正常水平传播（权重不再经 a 链加载，去掉 loadH 分支）---
  val aReg = RegInit(0.U(aEffW.W))
  when(io.clear) {
    aReg := 0.U
  }.elsewhen(io.loadWIn | io.validIn){
    aReg := io.aIn
  }
  
  io.aOut := aReg


  // --- 控制传播 ---
  io.validOut := RegNext(io.validIn, false.B)
  io.validOutV := RegNext(io.validInV, false.B)
  io.loadVOut  := RegNext(io.loadVIn, false.B)
  // loadW 脉冲沿 a 数据链方向逐列链传播（与 aOut lockstep，1 拍/列）。
  // lock 拍起截断传播：lock 拍各列 aIn 已处于对角线落位态（wReg 锁存采样
  // aIn），lock 之后继续下传的尾脉冲会在 lock 已撤的深列重新置位 loadWWin
  // （窗口永久重开，aOut 输出残留权重顶替计算期激活，实证 col>=2 全错且
  // 行间相同），必须就地截断。下一加载窗口 lock=0 时自然恢复传播
  io.loadWOut  := RegNext(loadWInSafe && !loadWInLockSafe, false.B)
  io.fmtOut   := RegNext(io.fmtIn, DataFormat.INT8)
  io.rndOut   := RegNext(io.rndIn, RoundingMode.RNE)
}
