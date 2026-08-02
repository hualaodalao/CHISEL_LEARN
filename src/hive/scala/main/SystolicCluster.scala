/** SystolicCluster：MxM 个 NxN Weight-Stationary 阵列组成更大集群
  *                  （权重复用通道加载，无独立 wIn/wOut）
  *
  * @param arrayN   每个子阵列维度（默认 8）
  * @param clusterM 集群中子阵列的 M×M 排列（默认 2，即 2×2 = 16×16 等效）
  * @param aW       激活位宽（默认 16）
  * @param bW       权重位宽（默认 16）
  * @param cW       累加器位宽（默认 0=自动推导，totalN = arrayN * clusterM）
  *
  * SystolicCluster 承担全局 skew 责任：
  *   - 行 i 延迟 i 拍（激活数据 aIn）
  *   - 列 j 延迟 j 拍（控制信号 loadIn/validIn/fmtIn/rndIn + 数据 psumIn）
  *   然后通过 ShiftRegister 将 skew 后的信号注入到各子阵列的边界端口。
  *
  * loadIn / weightLoadMode 以 per-row 方式注入子阵列：
  *   每行 loadIn 从子阵列左侧进入，经 PE 的 RegNext 水平脉动传播。
  *
  * 权重加载：
  *   Horizontal：loadIn 期间 aIn 携带权重，通过子阵列内部列移位链传播
  *   Vertical  ：loadIn 期间 psumIn 携带权重，经 psumOut→psumIn 垂直级联
  *   跨 tile 级联通过 psumOut → psumIn（PE 的 psumReg 在 loadIn 期间提供寄存传播）
  *
  * 计算：
  *   validIn/fmtIn/rndIn 从顶行 tile 注入后逐行 pipelined 下行（与 psum 对齐）。
  *   下方 tile 的 validIn 来自上方 tile 的 validOut。
  *   psumIn 纵向级联：上方 cOut → 下方 psumIn；顶行接 0。
  *
  * 横向级联：左方 tile aOut → 右方 tile aIn。
  * cOut/validOut：底部行 tile 输出。
  */

import chisel3._
import chisel3.util._

class SystolicCluster(
  arrayN: Int = 8,
  clusterM: Int = 2,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 0,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val totalN = arrayN * clusterM

  // --- 有效位宽派生（用 totalN） ---
  val aEffW: Int = math.max(aW, bW)
  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
  private val autoAccW = aEffW + bW + log2Up(totalN)
  val accW: Int = if (cW != 0) math.max(cW, bW)
                  else if (hasFp) math.max(32, autoAccW)
                  else autoAccW
  val cEffW: Int = accW

  // --- elaboration-time require ---
  if (hasFp)  require(cEffW >= 32,         s"含浮点格式时 cEffW($cEffW) 必须 >= 32")
  if (hasInt) require(cEffW >= aEffW + bW, s"含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    val aIn    = Input(Vec(totalN, UInt(aEffW.W)))
    val aOut   = Output(Vec(totalN, UInt(aEffW.W)))
    val psumIn = Input(Vec(totalN, UInt(cEffW.W)))
    val cOut   = Output(Vec(totalN, UInt(cEffW.W)))
    val loadIn = Input(Vec(totalN, Bool()))            // 每行一个 loadIn
    val validIn = Input(Vec(totalN, Bool()))            // 每行一个 validIn
    val fmtIn   = Input(Vec(totalN, DataFormat()))      // 每行一个 fmtIn
    val rndIn   = Input(Vec(totalN, RoundingMode()))    // 每行一个 rndIn
    val weightLoadMode = Input(Vec(totalN, WeightLoadMode()))  // 每行一个 loadMode
    val clear   = Input(Bool())  // psumReg 清零信号
    val validOut = Output(Vec(totalN, Bool()))
  })

  // 子阵列：skew 已移除，统一 cEffW
  val arrays = Seq.fill(clusterM, clusterM)(
    Module(new SystolicArray(arrayN, aW, bW, cEffW, supportedFmts))
  )

  // --- 全局行 skew：行 i 延迟 i 拍（激活数据 aIn） ---
  val aSkewed = (0 until totalN).map { i =>
    if (i == 0) io.aIn(i) else ShiftRegister(io.aIn(i), i)
  }

  // --- Per-column load signals（用于 psumIn mux）---
  // io.loadIn 是 per-row Vec(totalN)，psumIn mux 需要 per-column 信号。
  // 对每列 C，OR 该列所有行的 skewed loadIn，覆盖整个垂直负载窗口。
  val colLoadActive = (0 until totalN).map { c =>
    val cj = c / arrayN
    val tileColSkew = cj * arrayN
    val rowLoads = (0 until arrayN).map { r =>
      val skew = r + tileColSkew
      if (skew == 0) io.loadIn(r) else ShiftRegister(io.loadIn(r), skew)
    }
    rowLoads.reduce(_ || _)
  }

  // 连接子阵列
  for (ci <- 0 until clusterM; cj <- 0 until clusterM) {
    val arr = arrays(ci)(cj)

    // --- 激活（水平）：来自左侧 tile 或集群边界（已全局行 skew） ---
    for (r <- 0 until arrayN) {
      val globalRow = ci * arrayN + r
      arr.io.aIn(r) := (if (cj == 0) aSkewed(globalRow) else arrays(ci)(cj - 1).io.aOut(r))
    }

    // --- loadIn / weightLoadMode：per-row 注入子阵列 ---
    // 全局行 skew：PE(R)(C) 在 cycle R+C 看到 loadIn
    // 水平传播提供 C 延迟（tile 内列偏移），cluster 需提供 R（全局行偏移）
    for (r <- 0 until arrayN) {
      val globalRow = ci * arrayN + r
      val loadSkewed = if (globalRow == 0) io.loadIn(globalRow)
                       else ShiftRegister(io.loadIn(globalRow), globalRow)
      val modeSkewed = if (globalRow == 0) io.weightLoadMode(globalRow)
                       else ShiftRegister(io.weightLoadMode(globalRow), globalRow)
      arr.io.loadIn(r) := loadSkewed
      arr.io.weightLoadMode(r) := modeSkewed
    }

    // --- psumIn 纵向级联 ---
    // 垂直传播提供行延迟 R，cluster 需提供 per-column skew = C
    for (c <- 0 until arrayN) {
      val globalCol = cj * arrayN + c
      if (ci == 0) {
        // 顶行 tile：per-column skew = globalCol
        val colSkew = globalCol
        val weightPsumIn = if (colSkew == 0) io.psumIn(globalCol)
                           else ShiftRegister(io.psumIn(globalCol), colSkew)
        // 使用预计算的 per-column load 信号（OR 所有行），避免 per-row loadIn 索引越界
        arr.io.psumIn(c) := Mux(colLoadActive(globalCol), weightPsumIn, 0.U(cEffW.W))
      } else {
        // 上方 tile cOut → 下方 psumIn（计算模式累加；loadIn 期间 PE 透传 psumIn 实现跨 tile 级联）
        arr.io.psumIn(c) := arrays(ci - 1)(cj).io.cOut(c)
      }
    }

    // --- validIn/fmtIn/rndIn：per-row 从顶部注入，每行施加 per-row skew = R ---
    // SystolicArray 的 validIn/fmtIn/rndIn 为 per-row Vec，沿 y 方向（水平）传播
    for (r <- 0 until arrayN) {
      val globalRow = ci * arrayN + r
      val rowSkew = globalRow
      val vldSkewed = if (rowSkew == 0) io.validIn(globalRow)
                      else ShiftRegister(io.validIn(globalRow), rowSkew)
      val fmtSkewed = if (rowSkew == 0) io.fmtIn(globalRow)
                      else ShiftRegister(io.fmtIn(globalRow), rowSkew)
      val rndSkewed = if (rowSkew == 0) io.rndIn(globalRow)
                      else ShiftRegister(io.rndIn(globalRow), rowSkew)
      if (ci == 0) {
        arr.io.validIn(r) := vldSkewed
        arr.io.fmtIn(r)   := fmtSkewed
        arr.io.rndIn(r)   := rndSkewed
      } else {
        arr.io.validIn(r) := arrays(ci - 1)(cj).io.validOut(r)
        arr.io.fmtIn(r)   := arrays(ci - 1)(cj).io.fmtOut(r)
        arr.io.rndIn(r)   := arrays(ci - 1)(cj).io.rndOut(r)
      }
    }

    // --- clear 信号：直接广播到所有子阵列 ---
    arr.io.clear := io.clear
  }

  // --- 集群输出 ---
  for (ci <- 0 until clusterM; r <- 0 until arrayN) {
    val globalRow = ci * arrayN + r
    io.cOut(globalRow)     := arrays(ci)(clusterM - 1).io.cOut(r)
    io.validOut(globalRow) := arrays(ci)(clusterM - 1).io.validOut(r)
  }

  // aOut：右侧 tile 列的子阵列输出
  for (ci <- 0 until clusterM; r <- 0 until arrayN) {
    io.aOut(ci * arrayN + r) := arrays(ci)(clusterM - 1).io.aOut(r)
  }
}
