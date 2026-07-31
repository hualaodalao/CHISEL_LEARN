/** SystolicCluster：MxM 个 NxN 阵列组成更大集群
  *
  * @param arrayN   每个子阵列维度（默认 8）
  * @param clusterM 集群中子阵列的 M×M 排列（默认 2，即 2×2 = 16×16 等效）
  * @param aW       A 通道位宽（默认 16）
  * @param bW       B 通道位宽（默认 16）
  * @param cW       累加器位宽（默认 32）
  *
  * 物理意义：每个子阵列可独立做后端 floorplan 分区，
  * 集群级通过 aOut/bOut 边界信号级联数据流。
  */

import chisel3._
import chisel3.util._

class SystolicCluster(
  arrayN: Int = 8,
  clusterM: Int = 2,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 32,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val totalN = arrayN * clusterM

  val io = IO(new Bundle {
    val aIn  = Input(Vec(totalN, UInt(aW.W)))
    val bIn  = Input(Vec(totalN, UInt(bW.W)))
    val aOut = Output(Vec(totalN, UInt(aW.W)))
    val bOut = Output(Vec(totalN, UInt(bW.W)))
    val cOut = Output(Vec(totalN, UInt(cW.W)))
    val drainIn  = Input(Bool())
    val drainOut = Output(Vec(totalN, Bool()))
    val clearIn = Input(Bool())
    val validIn = Input(Bool())
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())
  })

  val arrays = Seq.fill(clusterM, clusterM)(Module(new SystolicArray(arrayN, skew = false, aW, bW, cW, supportedFmts)))

  // 全局 skew：行 i 延迟 i，列 j 延迟 j（覆盖整个 totalN 维度）
  val aSkewed = Seq.fill(totalN)(Wire(UInt(aW.W)))
  val bSkewed = Seq.fill(totalN)(Wire(UInt(bW.W)))
  for (i <- 0 until totalN) {
    aSkewed(i) := (if (i == 0) io.aIn(i) else ShiftRegister(io.aIn(i), i))
  }
  for (j <- 0 until totalN) {
    bSkewed(j) := (if (j == 0) io.bIn(j) else ShiftRegister(io.bIn(j), j))
  }

  // 水平级联 + 垂直级联
  for (ci <- 0 until clusterM; cj <- 0 until clusterM) {
    val arr = arrays(ci)(cj)

    // A 输入：来自左侧阵列或集群边界（已 skew）
    for (r <- 0 until arrayN) {
      val globalRow = ci * arrayN + r
      if (cj == 0) {
        arr.io.aIn(r) := aSkewed(globalRow)
      } else {
        arr.io.aIn(r) := arrays(ci)(cj - 1).io.aOut(r)
      }
    }

    // B 输入：来自上方阵列或集群边界（已 skew）
    for (c <- 0 until arrayN) {
      val globalCol = cj * arrayN + c
      if (ci == 0) {
        arr.io.bIn(c) := bSkewed(globalCol)
      } else {
        arr.io.bIn(c) := arrays(ci - 1)(cj).io.bOut(c)
      }
    }

    // 控制 + fmt + rnd：广播（简化；未来可改为脉动传播）
    arr.io.clearIn := io.clearIn
    arr.io.validIn := io.validIn
    arr.io.fmtIn   := io.fmtIn
    arr.io.rndIn   := io.rndIn

    // drain 垂直级联：上方阵列的 drainOut(0) → 下方阵列的 drainIn
    if (ci == 0) {
      arr.io.drainIn := io.drainIn
    } else {
      arr.io.drainIn := arrays(ci - 1)(cj).io.drainOut(0)
    }
  }

  // C 结果：底部行子阵列的 cOut → 集群 cOut
  for (cj <- 0 until clusterM; c <- 0 until arrayN) {
    io.cOut(cj * arrayN + c) := arrays(clusterM - 1)(cj).io.cOut(c)
    io.drainOut(cj * arrayN + c) := arrays(clusterM - 1)(cj).io.drainOut(c)
  }

  // 集群边界输出
  for (ci <- 0 until clusterM; r <- 0 until arrayN) {
    io.aOut(ci * arrayN + r) := arrays(ci)(clusterM - 1).io.aOut(r)
  }
  for (cj <- 0 until clusterM; c <- 0 until arrayN) {
    io.bOut(cj * arrayN + c) := arrays(clusterM - 1)(cj).io.bOut(c)
  }
}
