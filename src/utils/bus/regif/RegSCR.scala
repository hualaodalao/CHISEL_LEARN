/** SpinalHDL spinal.lib.bus.regif.Factory.RegSC / RegSCR 的 Chisel 移植版。
  *
  * SC(Set/Clear) / SCR(Set/Clear/Read) 寄存器组，常用于 clock_enable / soft_reset 信号配置：
  *   - SET:  write 1 set
  *   - CLR:  write 1 clear
  *   - READ: 只读地址（仅 SCR）
  *
  * 与 SpinalHDL 的差异：
  *   - HardType[T] → 直接传 Chisel 类型模板（如 `UInt(4.W)` / `Bool()`）。
  *   - maxSize 照抄源码（RegSC=3 / RegSCR=2，实际按字节应为 2*bw / 3*bw；
  *     RegSliceGrp.newReg 隐式偏移不做越界检查，故与源码行为一致）。
  */

import chisel3._

class RegSC(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 3, doc, sec, grpTag)(bif) {
  val SET = this.newReg("SCR set register")(SymbolName(s"${name}_SET"))
  val CLR = this.newReg("SCR clear register")(SymbolName(s"${name}_CLR"))

  def fieldAt[T <: Data](pos: Int, hardType: T, resetValue: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val reg = SET.fieldAt(pos, hardType, AccessType.W1S, resetValue, s"${doc}, write 1 set")(symbol)
    CLR.parasiteFieldAt(pos, reg, AccessType.W1C, resetValue, s"${doc}, write 1 clear")
    reg
  }

  def field[T <: Data](hardType: T, resetValue: Int, doc: String)(implicit symbol: SymbolName): T = {
    val reg = SET.field(hardType, AccessType.W1S, resetValue, s"${doc}, write 1 set")(symbol)
    CLR.parasiteField(reg, AccessType.W1C, resetValue, s"${doc}, write 1 clear")
    reg
  }
}

class RegSCR(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 2, doc, sec, grpTag)(bif) {
  val SET  = this.newRegAt(0, "SCR set register")(SymbolName(s"${name}_SET"))
  val CLR  = this.newReg("SCR clear register")(SymbolName(s"${name}_CLR"))
  val READ = this.newReg("SCR read register")(SymbolName(s"${name}_READ"))

  def fieldAt[T <: Data](pos: Int, hardType: T, resetValue: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val reg = SET.fieldAt(pos, hardType, AccessType.W1S, resetValue, s"${doc}, write 1 set")(symbol)
    CLR.parasiteFieldAt(pos, reg, AccessType.W1C, resetValue, s"${doc}, write 1 clear")
    READ.parasiteFieldAt(pos, reg, AccessType.RO, resetValue, s"${doc}, read addr")
    reg
  }

  def field[T <: Data](hardType: T, resetValue: Int, doc: String)(implicit symbol: SymbolName): T = {
    val reg = SET.field(hardType, AccessType.W1S, resetValue, s"${doc}, write 1 set")(symbol)
    CLR.parasiteField(reg, AccessType.W1C, resetValue, s"${doc}, write 1 clear")
    READ.parasiteField(reg, AccessType.RO, resetValue, s"${doc}, read addr")
    reg
  }
}
