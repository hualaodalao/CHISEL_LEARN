

/** RISC-V ISA 全局配置。
  * 原 NaxRiscv 中这些是 SpinalHDL 的 ScopeProperty，这里简化为可变全局配置，
  * 在 elaborate 前按需修改。
  */
object Riscv {

  var XLEN: Int = 32
  var FLEN: Int = 64
  var LSLEN: Int = 64
  var RVC, RVM, RVE, RVD, RVF, RVA, RVZba, RVZbb, RVZbc, RVZbs, RVZcbm: Boolean = false

  def withFpu = RVF || RVD

  def fpuExponentWidth = if (RVD) 11 else if (RVF) 8 else 0
  def fpuMantissaWidth = if (RVD) 52 else if (RVF) 23 else 0

  def rfDepth = if (RVE) 16 else 32
}
