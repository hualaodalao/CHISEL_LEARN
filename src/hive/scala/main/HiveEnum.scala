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
// ============================================================================
// 操作码枚举（仅 3 种命令）
// ============================================================================

/** HiveCore 操作码：寄存器写、执行、寄存器读。 */
object HiveCoreOp extends ChiselEnum {
  val REG_WRITE1, REG_WRITE2, EXECUTE, REG_READ = Value
}

/** 循环顺序枚举：结构性重构后仅保留 MKN（N 外 K 内 M 最内，
  * 与 Executor 固定的 weight-stationary N→K→M 遍历一致）。
  * MNK 已移除；该枚举仅为兼容 regFile.loopMode 字段而保留单值。
  */
object HiveCoreLoopMode extends ChiselEnum {
  val MKN = Value
}
