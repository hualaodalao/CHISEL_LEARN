/** PE：Weight-Stationary 处理单元（权重复用通道加载，无 wOut 端口）
  *
  * @param aW            激活位宽（默认 16）
  * @param bW            权重位宽（默认 16）
  * @param cW            累加器/部分和位宽（默认 48）
  * @param supportedFmts 本 PE 支持的数据格式集合（默认全部四种）。
  *                      仅为支持的格式生成 MAC 硬件；运行时收到不支持的 fmt 则
  *                      不执行累加，psum 透传。
  *
  * 有效位宽派生：
  *   aEffW = max(aW, bW)  —— 激活通道有效位宽（权重复用时需要承载 bW 位数据）
  *   cEffW = max(cW, bW)  —— 部分和通道有效位宽（垂直模式下需承载 bW 位权重）
  *
  * 权重加载模式（weightLoadMode）：
  *   Horizontal: 权重通过 aIn 的低 bW 位加载，aOut 在 loadIn 期间输出 wReg（权重向下传播）
  *   Vertical  : 权重通过 psumIn 的低 bW 位加载，psumOut 在 loadIn 期间输出 wReg（权重向下传播）
  *
  * 数据通路（Weight-Stationary）：
  *   loadIn=0: aIn → [Reg] → aOut                 激活水平传播（左→右），aEffW 位
  *   loadIn=1: wReg → aOut（水平模式）或 psumOut（垂直模式）  权重组合输出，列移位链
  *   psumIn → MAC → [psumReg] → psumOut           部分和垂直传播（上→下），cEffW 位
  *
  * 权重驻留：
  *   loadIn 有效时，wReg 从 wSrc 捕获权重（Horizontal: aIn 低位, Vertical: psumIn 低位）。
  *   水平模式：aOut := wReg.pad(aEffW)（组合输出），形成列移位链。
  *   垂直模式：psumOut := wReg.pad(cEffW)（组合输出），形成列移位链。
  *   连续 n 拍从列顶部压入权重后，第 i 行 PE 驻留第 i 个权重。
  *
  * psumReg 在 loadIn 期间的行为：
  *   loadIn 有效时：psumReg := psumIn（透传，不累加）
  *   loadIn 无效且 validIn && fmtOk 时：psumReg := accumulate(psumIn, product)
  *   否则：psumReg := psumIn（透传）
  *
  * 控制通路（水平 RegNext 右行，loadIn/loadMode 从左侧注入脉动传播）：
  *   loadIn  → [Reg] → loadOut          （水平传播至右侧 PE）
  *   validIn → [Reg] → validOut         （纵向传播至下行 PE）
  *   fmtIn   → [Reg] → fmtOut           （纵向传播至下行 PE）
  *   rndIn   → [Reg] → rndOut           （纵向传播至下行 PE）
  *   weightLoadMode → [Reg] → weightLoadModeOut （水平传播至右侧 PE）
  */

import chisel3._
import chisel3.util._

/** 权重加载模式枚举
  * Horizontal: 权重通过激活通道（aIn）水平加载，aOut 在 loadIn 期间传播权重
  * Vertical  : 权重通过部分和通道（psumIn）垂直加载，psumOut 在 loadIn 期间传播权重
  */
object WeightLoadMode extends ChiselEnum {
  val Horizontal, Vertical = Value
}

class PE(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val aEffW: Int = math.max(aW, bW)
  val cEffW: Int = math.max(cW, bW)

  private val hasFp  = supportedFmts.exists(f => f == DataFormat.FP16 || f == DataFormat.BF16)
  private val hasInt = supportedFmts.exists(f => f == DataFormat.INT8  || f == DataFormat.INT16)

  if (hasFp)  require(cEffW >= 32,          s"PE: 含浮点格式时 cEffW($cEffW) 必须 >= 32")
  if (hasInt) require(cEffW >= aEffW + bW,  s"PE: 含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    // 激活通道（水平，aEffW 位）
    val aIn  = Input(UInt(aEffW.W))
    val aOut = Output(UInt(aEffW.W))
    // 部分和通道（纵向，cEffW 位）
    val psumIn  = Input(UInt(cEffW.W))
    val psumOut = Output(UInt(cEffW.W))
    // 权重加载使能（水平脉动传播：从左侧注入，RegNext 向右传播）
    val loadIn  = Input(Bool())
    val loadOut = Output(Bool())
    // 权重加载模式（水平脉动传播：从左侧注入，RegNext 向右传播）
    val weightLoadMode    = Input(WeightLoadMode())
    val weightLoadModeOut = Output(WeightLoadMode())
    // 控制（纵向传播）
    val validIn  = Input(Bool())
    val validOut = Output(Bool())
    // 格式（纵向传播）
    val fmtIn  = Input(DataFormat())
    val fmtOut = Output(DataFormat())
    // 舍入模式（纵向传播）
    val rndIn   = Input(RoundingMode())
    val rndOut  = Output(RoundingMode())
    // psumReg 清零信号（权重加载后清零累加器）
    val clear   = Input(Bool())
  })

  // --- 格式支持检测（组合） ---
  val fmtOk = supportedFmts.map(f => io.fmtIn === f).reduce(_ || _)

  // --- 权重来源：水平模式取 aIn 低 bW 位，垂直模式取 psumIn 低 bW 位 ---
  val wSrc: UInt = Mux(io.weightLoadMode === WeightLoadMode.Horizontal,
    io.aIn(bW - 1, 0),
    io.psumIn(bW - 1, 0))

  // --- 权重驻留寄存器 ---
  val wReg = RegInit(0.U(bW.W))
  when(io.loadIn) {
    wReg := wSrc
  }

  // --- 可重构 MAC（aIn 取低 aW 位参与乘法） ---
  val product = MacUnit.multiply(io.aIn(aW - 1, 0), wReg, io.fmtIn, aW, bW, cEffW, supportedFmts, io.rndIn)

  val psumReg = RegInit(0.U(cEffW.W))
  when(io.clear) {
    psumReg := 0.U
  }.elsewhen(io.loadIn && io.weightLoadMode === WeightLoadMode.Vertical) {
    // loadIn 期间：透传 psumIn（不累加，loadIn 结束后 psumIn 归零则 psumReg 自然清零）
    psumReg := io.psumIn
  }.elsewhen(io.validIn && fmtOk) {
    psumReg := MacUnit.accumulate(io.psumIn, product, io.fmtIn, cEffW, supportedFmts, io.rndIn)
  }.otherwise {
    //psumReg := io.psumIn // 透传，保证下游行 psum/valid 对齐
  }

  // --- psumOut ---
  // 垂直模式 + loadIn：输出 wReg（权重向下传播，列移位链）
  // 其他情况：输出 psumReg（组合路径，PE 间级联累加）
  io.psumOut := psumReg


  // --- aOut 双模式 ---
  // 水平模式 + loadIn：输出 wReg（权重向下传播，列移位链）
  // 其他情况：输出 aIn 的 RegNext（激活水平流动）
  io.aOut := Mux(io.loadIn && io.weightLoadMode === WeightLoadMode.Horizontal,
    wReg.pad(aEffW),
    RegNext(io.aIn, 0.U))

  // --- 控制传播 ---
  // loadIn / weightLoadMode：水平脉动传播（RegNext → 右侧 PE）
  // validIn / fmtIn / rndIn：纵向传播（RegNext → 下行 PE）
  // 注意：validOut 使用与 psumOutReg 相同的延迟（RegNext），
  // 确保 psumOut 和 validOut 在 PE 输出端对齐。
  io.loadOut  := RegNext(io.loadIn, false.B)
  io.validOut := RegNext(io.validIn, false.B)
  io.fmtOut   := RegNext(io.fmtIn, DataFormat.INT8)
  io.rndOut   := RegNext(io.rndIn, RoundingMode.RNE)
  io.weightLoadModeOut := RegNext(io.weightLoadMode, WeightLoadMode.Horizontal)
}
