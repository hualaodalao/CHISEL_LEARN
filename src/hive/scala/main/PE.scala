/** PE：Output-Stationary 处理单元
  *
  * @param aW            A 通道位宽（默认 16）
  * @param bW            B 通道位宽（默认 16）
  * @param cW            累加器位宽（默认 32）
  * @param supportedFmts 本 PE 支持的数据格式集合（默认全部四种）。
  *                      仅为支持的格式生成 MAC 硬件；运行时收到不支持的 fmt 则
  *                      不执行累加，cReg 保持不变（只传递数据/控制）。
  *
  * 数据通路（参数化共享通道）：
  *   aIn → MAC（组合） + [Reg] → aOut   水平传播（左→右）
  *   bIn → MAC（组合） + [Reg] → bOut   垂直传播（上→下）
  *   cReg                                本地累加器（output-stationary）
  *
  * 时序：io.validIn 当拍门控计算，cReg 在当拍→下一拍边沿更新（1 拍延迟）。
  * 传播寄存器仅为下一个 PE 提供 1 拍延迟。
  *
  * C 结果列传递（垂直，上→下，移位寄存器模式）：
  *   cIn → cReg → cOut    drain 期间 cReg := cIn（整列同时移位）
  *   drainIn → drainOut    drain 广播（组合透传）
  *   drain 期间不执行累加，每周期整列 cReg 下移一格
  *
  * 控制通路（脉动传播，RegNext 给下一个 PE）：
  *   clearIn → [Reg] → clearOut
  *   validIn → [Reg] → validOut
  *   fmtIn   → [Reg] → fmtOut
  *   rndIn   → [Reg] → rndOut
  *   计算直接使用 io 组合信号（不经寄存器）
  */

import chisel3._
import chisel3.util._

class PE(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 32,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val io = IO(new Bundle {
    // 数据通道（参数化位宽）
    val aIn  = Input(UInt(aW.W))
    val aOut = Output(UInt(aW.W))
    val bIn  = Input(UInt(bW.W))
    val bOut = Output(UInt(bW.W))
    // C 结果列传递（垂直）
    val cIn  = Input(UInt(cW.W))
    val cOut = Output(UInt(cW.W))
    // drain 控制（垂直传播）
    val drainIn  = Input(Bool())
    val drainOut = Output(Bool())
    // 控制（脉动传播）
    val clearIn  = Input(Bool())
    val clearOut = Output(Bool())
    val validIn  = Input(Bool())
    val validOut = Output(Bool())
    // 格式（脉动传播）
    val fmtIn  = Input(DataFormat())
    val fmtOut = Output(DataFormat())
    // 舍入模式（脉动传播）
    val rndIn  = Input(RoundingMode())
    val rndOut = Output(RoundingMode())
  })

  // --- 格式支持检测（组合，基于当前 io） ---
  val fmtOk = supportedFmts.map(f => io.fmtIn === f).reduce(_ || _)

  // --- 可重构 MAC（组合计算，直接使用 io） ---
  val cReg = RegInit(0.U(cW.W))
  val product = MacUnit.multiply(io.aIn, io.bIn, io.fmtIn, aW, bW, cW, supportedFmts, io.rndIn)

  when(io.clearIn) {
    cReg := 0.U
  }.elsewhen(io.drainIn) {
    // drain：将 cIn 写入 cReg（整列同时移位）
    cReg := io.cIn
  }.elsewhen(io.validIn && fmtOk) {
    // 当拍门控累加
    cReg := MacUnit.accumulate(cReg, product, io.fmtIn, cW, supportedFmts, io.rndIn)
  }

  // --- C 输出：始终输出本地 cReg ---
  io.cOut := cReg
  io.drainOut := io.drainIn

  // --- 传播寄存器（仅为下一个 PE 提供 1 拍延迟） ---
  io.aOut := RegNext(io.aIn, 0.U)
  io.bOut := RegNext(io.bIn, 0.U)
  io.clearOut := RegNext(io.clearIn, false.B)
  io.validOut := RegNext(io.validIn, false.B)
  io.fmtOut := RegNext(io.fmtIn, DataFormat.INT8)
  io.rndOut := RegNext(io.rndIn, RoundingMode.RNE)
}
