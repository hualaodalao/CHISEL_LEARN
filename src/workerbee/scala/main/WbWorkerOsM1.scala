/** WbWorkerOsM1 —— 双模式单 PE（WS 权重驻留 / OS-M1 输出驻留数据流）
  *
  * WS 侧逻辑与 WbWorker 逐位同构（端口面/权重锁存/psum 优先级/输出链全部复制），
  * osM1=0 时行为与 WbWorker 完全一致。OS-M1 侧按 os_m1_plan.md §4/§5/§6/§8：
  *   - A/scaleA/valid 复用 WS 水平链：MAC 组合直读 io.aIn/io.scaleAIn（两模式同源、
  *     无 mux），右传寄存器捕获条件按模式切换——OS 下仅随 osAdvance 捕获（token 波
  *     严格逐列推进，暂停拍保持），WS 下同 WbWorker（loadWIn|validIn）。
  *   - B 方向换源：osM1 ? (osB, osScaleB) : (wReg, scaleBReg)——OS 权重来自双槽窗口
  *     FIFO 槽位，不走垂直/水平加载路径；cReg 改为本地 psumReg 反馈（OS 无 psumIn
  *     垂直通道）。
  *   - osSeed/osSeedEn：OS psum 显式初始化（§6.1 首行 bias 基底）。刻意不复用
  *     loadV/loadW——§6.1 禁止以 WS 权重加载语义暗示 OS 初始化。
  *   - osAdvance：严格全 16 路门控广播（§5.1 RunAdvance）——单列 PE 看不到其他列
  *     窗口 FIFO 占用，无法本地推导推进条件。
  *   - osConsume = osM1 && osAdvance && io.validIn：本拍 token 有效即消费。§8 周期账
  *     「第 j 列延迟 j 拍」要求列 0 同拍消费入口字、列 j>0 消费左邻寄存字——统一由
  *     本拍组合输入判定，不依赖寄存状态。
  *   - osLastIn/osLastOut：K 末字标记随 token 水平传播（scaleB 链是 MX 逐 K-lane
  *     语义，无法复用）；osDone 消费 K 末字同拍置位（§5.3 ColumnDone），clear 复位。
  *
  * psumReg 优先级：clear → (osM1&&osSeedEn) → osConsume → (!osM1&&loadVIn)
  *   → (!osM1&&validIn&&fmtOk) → hold。WS 两支加 !osM1 门控：OS 下 validIn 即
  *   token 有效位，暂停拍（validIn=1、osAdvance=0）不得误触发 WS 累加。
  *
  * @param aW            激活位宽（默认 16）
  * @param bW            权重位宽（默认 16）
  * @param cW            累加器/部分和位宽（默认 48）
  * @param supportedFmts 支持的数据格式集合（口径同 WbWorker）
  */

import chisel3._
import chisel3.util._

class WbWorkerOsM1(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)
) extends Module {
  val aEffW: Int = math.max(aW, bW)
  val cEffW: Int = math.max(cW, bW)

  private val hasFp  = supportedFmts.exists(f => f == DataFormat.FP16 || f == DataFormat.BF16)
  private val hasInt = supportedFmts.exists(f => f == DataFormat.INT8  || f == DataFormat.INT4)

  if (hasFp)  require(cEffW >= 40,          s"WbWorkerOsM1: FP formats require cEffW($cEffW) >= 40 (lazy normalization)")
  if (hasInt) require(cEffW >= aEffW + bW,  s"WbWorkerOsM1: INT formats require cEffW($cEffW) >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    // ---- WS 端口组（与 WbWorker 逐字段一致；A/scaleA/valid 链两模式复用） ----
    val aIn  = Input(UInt(aEffW.W))
    val aOut = Output(UInt(aEffW.W))
    val psumIn  = Input(UInt(cEffW.W))
    val psumOut = Output(UInt(cEffW.W))

    val loadHIn  = Input(Bool())

    val loadVInLock = Input(Bool())
    val loadVIn  = Input(Bool())
    val loadVOut  = Output(Bool())

    val loadWIn     = Input(Bool())
    val loadWOut    = Output(Bool())
    val loadWInLock = Input(Bool())

    val validIn  = Input(Bool())
    val validOut = Output(Bool())
    val validInV  = Input(Bool())
    val validOutV = Output(Bool())

    val fmtIn  = Input(DataFormat())
    val fmtOut = Output(DataFormat())
    val bFmtIn  = Input(DataFormat())
    val bFmtOut = Output(DataFormat())
    val scaleAIn  = Input(UInt(8.W))
    val scaleAOut = Output(UInt(8.W))
    val scaleBIn  = Input(UInt(8.W))
    val scaleBOut  = Output(UInt(8.W))
    val rndIn   = Input(RoundingMode())
    val rndOut  = Output(RoundingMode())
    val clear   = Input(Bool())

    // ---- OS-M1 端口组 ----
    val osM1      = Input(Bool())             // 运行期模式位：0=WS、1=OS-M1
    val osB       = Input(UInt(bW.W))         // OS 权重（窗口 FIFO 槽）
    val osScaleB  = Input(UInt(8.W))          // OS scaleB（与 osB 同槽位）
    val osSeed    = Input(UInt(cEffW.W))      // OS psum 初始化值（bias 基底）
    val osSeedEn  = Input(Bool())             // OS psum 初始化使能（优先级仅次 clear）
    val osAdvance = Input(Bool())             // RunAdvance 全阵列广播门控（§5.1）
    val osLastIn  = Input(Bool())             // K 末字标记（随 token 水平传播）
    val osLastOut = Output(Bool())
    val osConsume = Output(Bool())            // 本拍 token 有效消费
    val osDone    = Output(Bool())            // ColumnDone（§5.3）
  })

  val fmtInitActive = RegInit(false.B)
  val rndInitActive = RegInit(false.B)

  // loadW 端口 WireDefault 兜底：直接例化本 PE 而不连 loadW 口时回落恒 0
  val loadWInSafe = WireDefault(false.B)
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  val fmtReg = Reg(DataFormat())
  val rndReg = Reg(RoundingMode())
  val bFmtReg = Reg(DataFormat())
  // scaleB 驻留寄存器：持有本 PE 的 K-lane E8M0 scale（WS 模式），load Lock 拍与
  //   wReg 严格同拍锁存。
  val scaleBReg = RegInit(0.U(8.W))
  // scaleA 流寄存器：与 aReg lockstep（仅驱动 scaleAOut 右传，MAC 组合读 io.scaleAIn）
  val scaleAReg = RegInit(0.U(8.W))

  // 按 litValue 排序后归约：HashSet 迭代序逐实例不同会导致 firtool 去重失效
  val fmtOk = supportedFmts.toSeq.sortBy(_.litValue).map(f => fmtReg === f).reduce(_ || _)

  val wReg = RegInit(0.U(bW.W))

  when(io.loadVInLock) {
    wReg := io.psumIn(bW - 1, 0)
  }
  when(loadWInLockSafe) {
    wReg := io.aIn(bW - 1, 0)
  }
  assert(!(io.loadVInLock && loadWInLockSafe), "WbWorkerOsM1: loadVInLock and loadWInLock asserted simultaneously (mutually exclusive load modes)")
  when(io.loadHIn) {
    fmtReg := io.fmtIn
    rndReg := io.rndIn
    bFmtReg := io.bFmtIn
  }
  when(io.loadVInLock || loadWInLockSafe) {
    scaleBReg := io.scaleBIn
  }

  when(io.loadHIn && !fmtInitActive) {
    fmtInitActive := true.B
  }
  when(io.loadHIn && !rndInitActive) {
    rndInitActive := true.B
  }

  // OS 消费脉冲（组合）：本拍 osAdvance 且 token 有效即消费入口字
  val osConsume = WireDefault(false.B)
  osConsume := io.osM1 && io.osAdvance && io.validIn

  // OS 下 cReg 为本地反馈，psumReg 须先于 workUnit 连线声明
  val psumReg = RegInit(0.U(cEffW.W))

  val workUnit = Module(new WbWorkUnit(aW, bW, cW, supportedFmts))
  workUnit.io.a    := io.aIn(aW - 1, 0)
  workUnit.io.b    := Mux(io.osM1, io.osB, wReg)
  workUnit.io.cReg := Mux(io.osM1, psumReg, io.psumIn)
  workUnit.io.aFmt := fmtReg
  workUnit.io.bFmt := bFmtReg
  // scaleA 与激活输入同拍组合直连（同 WbWorker），不读已寄存的 scaleAReg
  workUnit.io.scaleA := io.scaleAIn
  workUnit.io.scaleB := Mux(io.osM1, io.osScaleB, scaleBReg)
  workUnit.io.rnd  := rndReg
  // 仅门控 FP/MX 解码断言：与 WS 同为 fmtOk && isMx(fmtReg)，INT/BF16 token 不进 MX 断言域
  workUnit.io.consumeValid := Mux(io.osM1, osConsume, io.validIn) && fmtOk && DataFormat.isMx(fmtReg)

  when(io.clear) {
    psumReg := 0.U
  }.elsewhen(io.osM1 && io.osSeedEn) {
    psumReg := io.osSeed
  }.elsewhen(osConsume) {
    psumReg := workUnit.io.sum
  }.elsewhen(!io.osM1 && io.loadVIn) {
    psumReg := io.psumIn
  }.elsewhen(!io.osM1 && io.validIn && fmtOk) {
    psumReg := workUnit.io.sum
  }

  io.psumOut := psumReg

  // aOut/scaleAOut：捕获条件按模式切换（OS 仅随 osAdvance，暂停拍保持）
  val aReg = RegInit(0.U(aEffW.W))
  when(io.clear) {
    aReg := 0.U
  }.elsewhen(Mux(io.osM1, io.osAdvance, io.loadWIn | io.validIn)) {
    aReg := io.aIn
  }
  io.aOut := aReg

  when(io.clear) {
    scaleAReg := 0.U
  }.elsewhen(Mux(io.osM1, io.osAdvance, io.loadWIn | io.validIn)) {
    scaleAReg := io.scaleAIn
  }
  io.scaleAOut := scaleAReg
  io.scaleBOut := RegNext(io.scaleBIn)

  // validOut：WS 下每拍捕获（等价 RegNext）；OS 下随 osAdvance、暂停保持。
  //   clear 复位须 osM1 门控——WbWorker 的 validOut=RegNext 不受 clear 影响，
  //   无条件复位会破坏 WS 逐拍同构。
  val validOutReg = RegInit(false.B)
  when(io.osM1 && io.clear) {
    validOutReg := false.B
  }.elsewhen(Mux(io.osM1, io.osAdvance, true.B)) {
    validOutReg := io.validIn
  }
  io.validOut := validOutReg
  io.validOutV := RegNext(io.validInV, false.B)
  io.loadVOut  := RegNext(io.loadVIn, false.B)
  io.loadWOut  := RegNext(loadWInSafe && !loadWInLockSafe, false.B)
  io.fmtOut   := RegNext(io.fmtIn, DataFormat.INT8)
  io.bFmtOut  := RegNext(io.bFmtIn, DataFormat.INT8)
  io.rndOut   := RegNext(io.rndIn, RoundingMode.RNE)

  // osLast 独立水平链（WS scaleB 链为 MX 逐 K-lane 语义，不可复用）
  val lastOutReg = RegInit(false.B)
  when(io.osM1 && io.clear) {
    lastOutReg := false.B
  }.elsewhen(io.osM1 && io.osAdvance) {
    lastOutReg := io.osLastIn
  }
  io.osLastOut := lastOutReg

  val osDoneReg = RegInit(false.B)
  when(io.clear) {
    osDoneReg := false.B
  }.elsewhen(osConsume && io.osLastIn) {
    osDoneReg := true.B
  }
  io.osDone := osDoneReg

  io.osConsume := osConsume
}
