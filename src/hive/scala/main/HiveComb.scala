/** HiveComb（巢脾）- MxM 个 NxN HiveCell 组成更大集群
  *                  （权重复用通道加载，无独立 wIn/wOut）
  *
  * @param arrayN   每个子阵列维度（默认 8）
  * @param clusterM 集群中子阵列的 M×M 排列（默认 2，即 2×2 = 16×16 等效）
  * @param aW       激活位宽（默认 16）
  * @param bW       权重位宽（默认 16）
  * @param cW       累加器位宽（默认 0=自动推导，totalN = arrayN * clusterM）
  *
  * 新架构要点：
  *   - loadHIn / loadVIn 为标量输入，直接广播到所有子阵列（无 skew）
  *   - validIn/fmtIn/rndIn 需要 array-row 级 skew（ci * arrayN）
  *   - HiveCell 内部无 skew，HiveComb 承担全部全局 skew 责任
  *
  * skew 策略（按加载模式动态禁用）：
  *   - 水平权重加载模式（loadHIn）：首列 aIn 不 skew（其他阶段仍 skew）
  *   - 垂直权重加载模式（loadVIn）：首行 psumIn 不 skew（其他阶段仍 skew）
  *
  * 横向级联：左方 tile aOut → 右方 tile aIn。
  * psumIn 纵向级联：上方 cOut → 下方 psumIn；顶行接 0 或 io.psumIn。
  * cOut/validOut：底部行 tile 输出。
  */

import chisel3._
import chisel3.util._

class HiveComb(
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

    val loadHIn = Input(Bool())   // 水平加载（标量）
    val loadVIn = Input(Bool())   // 垂直加载（标量）
    val validIn = Input(Bool())   // 标量 validIn
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())

    val clear   = Input(Bool())

    val validOut = Output(Vec(totalN, Bool()))
  })

  // 子阵列
  val arrays = Seq.fill(clusterM, clusterM)(
    Module(new HiveCell(arrayN, aW, bW, cEffW, supportedFmts))
  )

  // --- 全局行 skew：行 i 延迟 i 拍（用于计算阶段的 aIn） ---
  val aSkewed = (0 until totalN).map { i =>
    if (i == 0) io.aIn(i) else ShiftRegister(io.aIn(i), i)
  }

  // --- 全局 skew：每个子阵列行 (ci) 的控制信号延迟 ---
  val skewedCtrl = (0 until clusterM).map { ci =>
    val rowDelay = ci * arrayN
    val validSkewed = if (rowDelay == 0) io.validIn else ShiftRegister(io.validIn, rowDelay)
    val fmtSkewed   = if (rowDelay == 0) io.fmtIn   else ShiftRegister(io.fmtIn, rowDelay)
    val rndSkewed   = if (rowDelay == 0) io.rndIn   else ShiftRegister(io.rndIn, rowDelay)
    (validSkewed, fmtSkewed, rndSkewed)
  }

  // 连接子阵列
  for (ci <- 0 until clusterM; cj <- 0 until clusterM) {
    val arr = arrays(ci)(cj)
    val (validSk, fmtSk, rndSk) = (skewedCtrl(ci)._1, skewedCtrl(ci)._2, skewedCtrl(ci)._3)

    // --- 控制信号：广播（无 skew） ---
    arr.io.loadHIn := io.loadHIn
    arr.io.loadVIn := io.loadVIn
    arr.io.clear   := io.clear

    // --- validIn/fmtIn/rndIn：计算阶段需要 skew ---
    arr.io.validIn := validSk
    arr.io.fmtIn   := fmtSk
    arr.io.rndIn   := rndSk

    // --- 激活数据 aIn：per-row，按加载模式动态禁用 skew ---
    for (r <- 0 until arrayN) {
      val globalRow = ci * arrayN + r
      if (cj == 0) {
        // 首列：水平权重加载时禁用 skew，其他阶段 skew
        arr.io.aIn(r) := Mux(io.loadHIn,
                             io.aIn(globalRow),  // 加载模式：无 skew
                             aSkewed(globalRow)) // 计算阶段：有 skew
      } else {
        // 非首列：从左侧子阵列获取
        arr.io.aIn(r) := arrays(ci)(cj-1).io.aOut(r)
      }
    }

    // --- psumIn：per-column，按加载模式动态禁用 skew ---
    for (c <- 0 until arrayN) {
      val globalCol = cj * arrayN + c
      if (ci == 0) {
        // 首行：垂直权重加载时直接传 io.psumIn（无 skew），
        // 计算阶段接 0
        arr.io.psumIn(c) := Mux(io.loadVIn,
                                io.psumIn(globalCol),  // 加载模式：无 skew
                                0.U(cEffW.W))          // 计算阶段：psumIn 为 0
      } else {
        // 非首行：从上方子阵列获取
        arr.io.psumIn(c) := arrays(ci-1)(cj).io.cOut(c)
      }
    }
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
