/** 数据格式枚举：HiveWorker 支持的输入/计算格式（3-bit，6 值） */

import chisel3._

object DataFormat extends ChiselEnum {
  val FP16, BF16, INT16, INT8, MXE4M3, MXE5M2 = Value

  /** 判断给定格式是否为 MX 系列（E4M3 或 E5M2） */
  def isMx(f: DataFormat.Type): Bool = f === MXE4M3 || f === MXE5M2
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

/** 循环顺序枚举：loopMode 位域已被 aFmt/bFmt/mixFmtEn/outFmt 重新占用，
  * 本枚举仅防历史引用编译断裂（结构性重构后 Executor 固定 weight-stationary
  * N→K→M 遍历，C 写回顺序 = push FIFO 序，均与 loopMode 无关）。
  */
object HiveCoreLoopMode extends ChiselEnum {
  val MKN = Value
}
