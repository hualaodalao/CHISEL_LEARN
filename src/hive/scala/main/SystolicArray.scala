/** N×N 脉动阵列
  *
  * @param n     阵列维度（默认 8）
  * @param skew  是否启用输入 skew buffer。
  *              true：行 i 延迟 i 周期、列 j 延迟 j 周期，外部可直接同步喂数；
  *              false：裸 Tile，用于 Cluster 级联（由上层处理对齐）。
  * @param aW    A 通道位宽（默认 16）
  * @param bW    B 通道位宽（默认 16）
  * @param cW    累加器位宽（默认 32）
  *
  * 接口：
  *   - aIn(n)：左侧每行一个 aW-bit A 输入
  *   - bIn(n)：顶部每列一个 bW-bit B 输入
  *   - aOut(n) / bOut(n)：右侧 / 底部输出（用于级联）
  *   - cOut(n)：每列底部的 cW-bit 结果（drain 时逐周期串行输出）
  *   - drainIn：drain 脉冲从顶部注入，逐 PE 向下传播
  *   - drainOut(n)：每列底部的 drain 输出（用于级联）
  *   - clearIn / validIn / fmtIn / rndIn：从左侧/顶部注入，逐 PE 脉动传播
  */

import chisel3._
import chisel3.util._

class SystolicArray(
  n: Int = 8,
  skew: Boolean = true,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 32,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val io = IO(new Bundle {
    // 数据
    val aIn  = Input(Vec(n, UInt(aW.W)))
    val bIn  = Input(Vec(n, UInt(bW.W)))
    val aOut = Output(Vec(n, UInt(aW.W)))
    val bOut = Output(Vec(n, UInt(bW.W)))
    // C 结果（列底部输出，drain 时串行读出）
    val cOut = Output(Vec(n, UInt(cW.W)))
    // drain 控制
    val drainIn  = Input(Bool())
    val drainOut = Output(Vec(n, Bool()))
    // 控制（脉动传播）
    val clearIn = Input(Bool())
    val validIn = Input(Bool())
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())
  })

  val pes = Seq.fill(n, n)(Module(new PE(aW, bW, cW, supportedFmts)))

  // 水平连线
  val aH = Seq.fill(n, n + 1)(Wire(UInt(aW.W)))
  // 垂直连线
  val bV = Seq.fill(n + 1, n)(Wire(UInt(bW.W)))
  // C 列传递（垂直，移位寄存器链）
  val cV = Seq.fill(n + 1, n)(Wire(UInt(cW.W)))
  // 控制水平传播
  val clrH = Seq.fill(n, n + 1)(Wire(Bool()))
  val vldH = Seq.fill(n, n + 1)(Wire(Bool()))
  val fmtH = Seq.fill(n, n + 1)(Wire(DataFormat()))
  val rndH = Seq.fill(n, n + 1)(Wire(RoundingMode()))
  // 控制垂直传播
  val clrV = Seq.fill(n + 1, n)(Wire(Bool()))
  val vldV = Seq.fill(n + 1, n)(Wire(Bool()))
  val fmtV = Seq.fill(n + 1, n)(Wire(DataFormat()))
  val rndV = Seq.fill(n + 1, n)(Wire(RoundingMode()))

  // 默认值
  for (i <- 0 until n) { aH(i)(0) := 0.U; clrH(i)(0) := false.B; vldH(i)(0) := false.B; fmtH(i)(0) := DataFormat.INT8; rndH(i)(0) := RoundingMode.RNE }
  for (j <- 0 until n) { bV(0)(j) := 0.U; clrV(0)(j) := false.B; vldV(0)(j) := false.B; fmtV(0)(j) := DataFormat.INT8; rndV(0)(j) := RoundingMode.RNE; cV(0)(j) := 0.U }

  // 边界输入（可选 skew：行 i 延迟 i，列 j 延迟 j）
  if (skew) {
    for (i <- 0 until n) {
      aH(i)(0) := (if (i == 0) io.aIn(i) else ShiftRegister(io.aIn(i), i))
      clrH(i)(0) := (if (i == 0) io.clearIn else ShiftRegister(io.clearIn, i))
      vldH(i)(0) := (if (i == 0) io.validIn else ShiftRegister(io.validIn, i))
      fmtH(i)(0) := (if (i == 0) io.fmtIn else ShiftRegister(io.fmtIn, i))
      rndH(i)(0) := (if (i == 0) io.rndIn else ShiftRegister(io.rndIn, i))
    }
    for (j <- 0 until n) {
      bV(0)(j) := (if (j == 0) io.bIn(j) else ShiftRegister(io.bIn(j), j))
      clrV(0)(j) := (if (j == 0) io.clearIn else ShiftRegister(io.clearIn, j))
      vldV(0)(j) := (if (j == 0) io.validIn else ShiftRegister(io.validIn, j))
      fmtV(0)(j) := (if (j == 0) io.fmtIn else ShiftRegister(io.fmtIn, j))
      rndV(0)(j) := (if (j == 0) io.rndIn else ShiftRegister(io.rndIn, j))
    }
  } else {
    for (i <- 0 until n) { aH(i)(0) := io.aIn(i) }
    for (j <- 0 until n) { bV(0)(j) := io.bIn(j) }
    // clear/valid/fmt/rnd 从左上角注入
    clrH(0)(0) := io.clearIn
    vldH(0)(0) := io.validIn
    fmtH(0)(0) := io.fmtIn
    rndH(0)(0) := io.rndIn
    clrV(0)(0) := io.clearIn
    vldV(0)(0) := io.validIn
    fmtV(0)(0) := io.fmtIn
    rndV(0)(0) := io.rndIn
  }

  // 连接 PE 网格
  for (i <- 0 until n; j <- 0 until n) {
    val pe = pes(i)(j)
    pe.io.aIn := aH(i)(j)
    pe.io.bIn := bV(i)(j)
    pe.io.clearIn := clrH(i)(j) | clrV(i)(j)
    pe.io.validIn := vldH(i)(j) | vldV(i)(j)
    // fmt/rnd：水平优先
    pe.io.fmtIn := Mux(vldH(i)(j) | clrH(i)(j), fmtH(i)(j), fmtV(i)(j))
    pe.io.rndIn := Mux(vldH(i)(j) | clrH(i)(j), rndH(i)(j), rndV(i)(j))
    // C 列传递 + drain（广播）
    pe.io.cIn := cV(i)(j)
    pe.io.drainIn := io.drainIn

    // 水平输出 → 下一列
    aH(i)(j + 1) := pe.io.aOut
    clrH(i)(j + 1) := pe.io.clearOut
    vldH(i)(j + 1) := pe.io.validOut
    fmtH(i)(j + 1) := pe.io.fmtOut
    rndH(i)(j + 1) := pe.io.rndOut

    // 垂直输出 → 下一行
    bV(i + 1)(j) := pe.io.bOut
    clrV(i + 1)(j) := pe.io.clearOut
    vldV(i + 1)(j) := pe.io.validOut
    fmtV(i + 1)(j) := pe.io.fmtOut
    rndV(i + 1)(j) := pe.io.rndOut
    // C 垂直传递
    cV(i + 1)(j) := pe.io.cOut
  }

  // 边界输出
  for (i <- 0 until n) { io.aOut(i) := aH(i)(n) }
  for (j <- 0 until n) { io.bOut(j) := bV(n)(j) }
  // C 结果：每列底部输出
  for (j <- 0 until n) { io.cOut(j) := cV(n)(j) }
  for (j <- 0 until n) { io.drainOut(j) := io.drainIn }
}
