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
  * 权重加载协议：
  *   - loadHIn=true：锁存 fmt/rnd 配置（不动 wReg）
  *   - loadVIn=true：从 psumIn 低位锁存权重进 wReg，并透传 psumIn 使权重下沉
  *   - 典型用法：加载阶段同时拉高 loadH+loadV，psumIn 供权重，aIn 无需供数
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

  if (hasFp)  require(cEffW >= 32,          s"HiveWorker: 含浮点格式时 cEffW($cEffW) 必须 >= 32")
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

    // 垂直加载（仅权重）：由 HiveCell 广播到所有 PE，无垂直传播输出
    val loadVIn  = Input(Bool())

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
  // 权重仅经垂直加载（loadV 上升沿，从 psumIn 低位）；水平加载（loadH）仅锁存配置 fmt/rnd。
  // 二者独立：加载权重时同时拉高 loadH（刷新配置）与 loadV（锁存权重）。
  when(io.loadVIn) {
    wReg := io.psumIn(bW - 1, 0)
  }
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
  val product = WorkUnit.multiply(io.aIn(aW - 1, 0), wReg, fmtReg, aW, bW, cEffW, supportedFmts, rndReg)

  val psumReg = RegInit(0.U(cEffW.W))
  when(io.clear) {
    psumReg := 0.U
  }.elsewhen(io.loadVIn){
    psumReg := io.psumIn
  }.elsewhen(io.validIn && fmtOk) {
    // 正常计算（使用 fmtReg 和 rndReg）
    psumReg := WorkUnit.accumulate(io.psumIn, product, fmtReg, cEffW, supportedFmts, rndReg)
  }

  // --- psumOut ---
  // 始终输出寄存值。行式权重加载协议（loadV 广播脉冲，每权重行 2 拍：
  // 偶数拍供数 + loadV 脉冲锁存/下沉，奇数拍保持）依赖 psumReg 逐拍寄存
  // 使权重每拍下沉一行；若做组合旁路，脉冲拍当前供数会穿透到底部簇，
  // 深层 PE 锁存到错误权重
  io.psumOut := psumReg

  // --- aOut：激活正常水平传播（权重不再经 a 链加载，去掉 loadH 分支）---
  io.aOut := RegNext(io.aIn, 0.U)

  // --- 控制传播 ---
  io.validOut := RegNext(io.validIn, false.B)
  io.fmtOut   := RegNext(io.fmtIn, DataFormat.INT8)
  io.rndOut   := RegNext(io.rndIn, RoundingMode.RNE)
}
