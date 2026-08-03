/** 数据格式枚举：HiveWorker 支持的四种输入/计算格式 */

import chisel3._

object DataFormat extends ChiselEnum {
  val FP16, BF16, INT16, INT8 = Value
}

/** IEEE-754 舍入模式 */
object RoundingMode extends ChiselEnum {
  val RNE,  // Round to Nearest, ties to Even
      RTZ,  // Round Toward Zero
      RDN,  // Round Down (toward -∞)
      RUP,  // Round Up (toward +∞)
      RMM   // Round to Nearest, ties to Max Magnitude
      = Value
}
