/** HiveWorker（工蜂）- Weight-Stationary 处理单元
  *
  * 架构定位：
  *   权重驻留（Weight-Stationary）HiveWorker，无独立 wIn/wOut 端口。
  *   权重通过 aIn（loadH 水平模式）或 psumIn（loadV 垂直模式）通道加载到内部 wReg，
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
  *   - 新增 fmtReg / rndReg：跟随权重一起加载，后续计算使用内部寄存器
  *   - 删除 weightLoadMode：拆分为 loadH（水平）和 loadV（垂直）两个独立信号
  *   - 权重加载时不经过 ShiftRegister：权重自然沿 a/psum PE 链传播
  *   - 垂直加载时必须同时水平加载配置（fmt/rnd），不存在「仅垂直加载」模式
  *
  * IO 端口语义：
  *   aIn  / aOut   : 激活通道，水平传播（左→右），aEffW 位。
  *   psumIn / psumOut : 部分和通道，垂直传播（上→下），cEffW 位。
  *   loadHIn / loadHOut : 水平加载使能（权重 + 配置），水平传播。
  *   loadVIn / loadVOut : 垂直加载使能（权重），垂直传播。必须与 loadHIn 同时使用。
  *   validIn / validOut : 数据有效标志，水平传播。
  *   fmtIn   / fmtOut   : 数据格式选择，水平传播。
  *   rndIn   / rndOut   : 舍入模式选择，水平传播。
  *   clear              : psumReg 清零，标量广播。
  *
  * 权重加载模式（仅两种合法模式，不存在「仅垂直加载」）：
  *   1. 仅水平（loadHIn=true, loadVIn=false）：权重 + fmt/rnd 从 aIn/fmtIn/rndIn 获取
  *   2. 水平+垂直同时（loadHIn=true, loadVIn=true）：权重从 psumIn 低位获取，fmt/rnd 从 fmtIn/rndIn 获取
  *
  * wReg 锁存逻辑：
  *   loadH && loadV：权重从 psumIn 低位获取，fmt/rnd 从 fmtIn/rndIn 获取
  *   仅 loadH：权重 + 配置都从 aIn/fmtIn/rndIn 获取
  *
  * psumReg 逻辑（优先级从高到低）：
  *   1. when(clear)                       → psumReg := 0
  *   2. elsewhen(loadHIn)                 → psumReg := psumIn（加载期间透传）
  *   3. elsewhen(validIn && fmtOk)        → psumReg := accumulate(psumIn, product, fmtReg, rndReg)
  *   4. otherwise                         → 保持
  *
  * 输出驱动：
  *   aOut   := loadHIn ? wReg.pad(aEffW) : RegNext(aIn, 0.U)
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

  if (hasFp)  require(cEffW >= 32,          s"HiveWorker: 含浮点格式时 cEffW($cEffW) 必须 >= 32")
  if (hasInt) require(cEffW >= aEffW + bW,  s"HiveWorker: 含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    // 激活通道（水平，aEffW 位）
    val aIn  = Input(UInt(aEffW.W))
    val aOut = Output(UInt(aEffW.W))
    // 部分和通道（纵向，cEffW 位）
    val psumIn  = Input(UInt(cEffW.W))
    val psumOut = Output(UInt(cEffW.W))

    // 水平加载（权重 + 配置）
    val loadHIn  = Input(Bool())
    val loadHOut = Output(Bool())

    // 垂直加载（仅权重）
    val loadVIn  = Input(Bool())
    val loadVOut = Output(Bool())

    // 控制（水平传播：RegNext → 右侧 HiveWorker）
    val validIn  = Input(Bool())
    val validOut = Output(Bool())
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

  // --- 内部配置寄存器（无 RegInit，依赖显式初始化） ---
  val fmtReg = Reg(DataFormat())
  val rndReg = Reg(RoundingMode())

  // 格式支持检测（基于 fmtReg） ---
  val fmtOk = supportedFmts.map(f => fmtReg === f).reduce(_ || _)

  // --- 权重驻留寄存器 ---
  val wReg = RegInit(0.U(bW.W))

  // --- wReg / fmtReg / rndReg 锁存逻辑 ---
  // 注意：不存在「仅垂直加载」模式，垂直加载总伴随水平加载配置（fmt/rnd）
  when(io.loadHIn && io.loadVIn) {
    // 同时：权重从 psumIn 低位获取，fmt/rnd 从 fmtIn/rndIn 获取
    wReg   := io.psumIn(bW - 1, 0)
    fmtReg := io.fmtIn
    rndReg := io.rndIn
  }.elsewhen(io.loadHIn) {
    // 仅水平：权重 + 配置都从 aIn/fmtIn/rndIn 获取
    wReg   := io.aIn(bW - 1, 0)
    fmtReg := io.fmtIn
    rndReg := io.rndIn
  }

  // --- 首次 load 时标记初始化完成（垂直加载总伴随水平，故仅需检测 loadHIn） ---
  when(io.loadHIn && !fmtInitActive) {
    fmtInitActive := true.B
  }
  when(io.loadHIn && !rndInitActive) {
    rndInitActive := true.B
  }

  // --- 可重构 MAC（使用 fmtReg / rndReg） ---
  val product = WorkUnit.multiply(io.aIn(aW - 1, 0), wReg, fmtReg, aW, bW, cEffW, supportedFmts, rndReg)

  val psumReg = RegInit(0.U(cEffW.W))
  when(io.clear) {
    psumReg := 0.U
  }.elsewhen(io.loadHIn) {
    // 加载期间透传 psumIn（垂直加载总伴随水平，loadHIn 已涵盖所有加载场景）
    psumReg := io.psumIn
  }.elsewhen(io.validIn && fmtOk) {
    // 正常计算（使用 fmtReg 和 rndReg）
    psumReg := WorkUnit.accumulate(io.psumIn, product, fmtReg, cEffW, supportedFmts, rndReg)
  }

  // --- psumOut ---
  // 直接输出 psumReg（loadVIn 期间 psumReg := psumIn，等效透传；
  // loadVIn 撤掉后 psumReg 保持最后的权重值，自然沿 psum 链向下传播）
  io.psumOut := psumReg

  // --- aOut：loadHIn 时输出 wReg（水平权重传播），否则正常 RegNext(aIn) ---
  io.aOut := Mux(io.loadHIn, wReg.pad(aEffW), RegNext(io.aIn, 0.U))

  // --- 控制传播 ---
  io.loadHOut := RegNext(io.loadHIn, false.B)
  io.loadVOut := RegNext(io.loadVIn, false.B)
  io.validOut := RegNext(io.validIn, false.B)
  io.fmtOut   := RegNext(io.fmtIn, DataFormat.INT8)
  io.rndOut   := RegNext(io.rndIn, RoundingMode.RNE)
}
