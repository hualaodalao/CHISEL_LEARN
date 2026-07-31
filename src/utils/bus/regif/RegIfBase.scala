/** SpinalHDL spinal.lib.bus.regif 基础定义的 Chisel 移植版。
  *
  * 包含：SymbolName / Section / GrpTag / ReuseTag / AccessType / Field / Secure。
  *
  * 与 SpinalHDL 的差异：
  *   - SymbolName / ClassName 宏改用 lihaoyi sourcecode 库的 `sourcecode.Name`
  *     隐式捕获调用点 val 名（语义等价：取最近的封闭定义名）。
  *   - Field.hardbit：BaseType → chisel3.Data（UInt/SInt/Bool）。
  */

import chisel3._

/** 调用点符号名（`val REG0 = busif.newReg(...)` 时 name == "REG0"） */
case class SymbolName(name: String)

object SymbolName {
  implicit def fromSourcecode(implicit n: sourcecode.Name): SymbolName = SymbolName(n.value)
}

/** 位段展示用（错误消息 / 文档） */
class Section(val max: Int, val min: Int) {
  override def toString(): String = {
    if (this.max == this.min) s"[${this.min}]" else s"[${this.max}:${this.min}]"
  }
}

object Section {
  def apply(x: Range): Section = new Section(x.max, x.min)
  implicit def tans(x: Range): Section = Section(x)
}

case class GrpTag(id: Int, name: String) {
  override def toString: String = s"$id|$name"
}

case class ReuseTag(id: Int, blockName: String, baseAddr: BigInt = 0, instName: String = "") {
  override def toString: String = s"$id|$blockName|0x${baseAddr.toString(16)}"

  def offset(x: BigInt): BigInt = x - baseAddr
}

sealed trait AccessType

object AccessType {
  case object RO    extends AccessType //- W: no effect, R: no effect
  case object RW    extends AccessType //- W: as-is, R: no effect
  case object RC    extends AccessType //- W: no effect, R: clears all bits
  case object RS    extends AccessType //- W: no effect, R: sets all bits
  case object WRC   extends AccessType //- W: as-is, R: clears all bits
  case object WRS   extends AccessType //- W: as-is, R: sets all bits
  case object WC    extends AccessType //- W: clears all bits, R: no effect
  case object WS    extends AccessType //- W: sets all bits, R: no effect
  case object WSRC  extends AccessType //- W: sets all bits, R: clears all bits
  case object WCRS  extends AccessType //- W: clears all bits, R: sets all bits
  case object W1C   extends AccessType //- W: 1/0 clears/no effect on matching bit, R: no effect
  case object W1S   extends AccessType //- W: 1/0 sets/no effect on matching bit, R: no effect
  case object W1T   extends AccessType //- W: 1/0 toggles/no effect on matching bit, R: no effect
  case object W0C   extends AccessType //- W: 1/0 no effect on/clears matching bit, R: no effect
  case object W0S   extends AccessType //- W: 1/0 no effect on/sets matching bit, R: no effect
  case object W0T   extends AccessType //- W: 1/0 no effect on/toggles matching bit, R: no effect
  case object W1SRC extends AccessType //- W: 1/0 sets/no effect on matching bit, R: clears all bits
  case object W1CRS extends AccessType //- W: 1/0 clears/no effect on matching bit, R: sets all bits
  case object W0SRC extends AccessType //- W: 1/0 no effect on/sets matching bit, R: clears all bits
  case object W0CRS extends AccessType //- W: 1/0 no effect on/clears matching bit, R: sets all bits
  case object WO    extends AccessType //- W: as-is, R: error
  case object WOC   extends AccessType //- W: clears all bits, R: error
  case object WOS   extends AccessType //- W: sets all bits, R: error
  case object W1    extends AccessType //- W: first one after ~HARD~ reset is as-is, other W have no effects, R: no effect
  case object WO1   extends AccessType //- W: first one after ~HARD~ reset is as-is, other W have no effects, R: error
  case object NA    extends AccessType // -W: reserved, R: reserved
  case object W1P   extends AccessType // -W: 1/0 pulse/no effect on matching bit, R: no effect, pulse regNextOut after writehit
  case object W1I   extends AccessType // -W: 1/0 pulse/no effect on matching bit, R: no effect, impulse combination out at writehit
  case object W0P   extends AccessType // -W: 0/1 pulse/no effect on matching bit, R: no effect
  case object HSRW  extends AccessType // HardWare Set then SoftWare RW, HW high priority than SW
  case object RWHS  extends AccessType // SoftWare RW then HardWare Set, SW high priority than HW
  case object W1CHS extends AccessType // SoftWare Write 1 Clear then HardWare Set, SW high priority than HW
  case object W1SHS extends AccessType // SoftWare Write 1 Set  then HardWare Set, SW high priority than HW
  case object ROV   extends AccessType // ReadOnly Value, used for constant
  case class CSTM(name: String) extends AccessType {
    override def toString: String = if (name.isEmpty) "CSTM" else name.toUpperCase
  }
}

case class Field(name: String,
                 hardbit: Data,
                 section: Range,
                 accType: AccessType,
                 resetValue: BigInt,
                 readError: Boolean,
                 doc: String) {
  import AccessType._

  private var _name = name

  def tailBitPos = section.max

  def getName(): String = _name
  def setName(name: String): Field = { _name = name; this }
  def getWidth(): Int = hardbit.getWidth
  def getSection(): Range = section
  def getAccessType(): AccessType = accType
  def getResetValue(): BigInt = resetValue
  def getDoc(): String = doc
  def isWriteOnly(): Boolean = accType match {
    case `WO` | `WO1` | `WOS` | `WOC` => true
    case _                            => false
  }
  def uvmBaseAcc = List(RO, RW, RC, RS, WRC, WRS, WC, WS, WSRC, WCRS, W1C, W1S, W1T, W0C, W0S,
    W0T, W1SRC, W1CRS, W0SRC, W0CRS, WO, WOC, WOS, W1, WO1)
  def isUvmAcc: Boolean = uvmBaseAcc.contains(getAccessType())
}

/*
 * AS: Always-Secure(read and write all sec)
 * NS: Non-Secure(read and write all non-sec)
 * MS: Mix-Secure(read and write securetiy seperate)
 * CS: Config-Secure(which need signal_bit indicate security status)
 * */
trait Secure {
  val wrbit: Bool = null
  val rdbit: Bool = null
}

object Secure {
  case class MS(wrsec: Boolean, rdsec: Boolean) extends Secure
  case class CS(override val wrbit: Bool, override val rdbit: Bool) extends Secure
  object AS { def apply() = MS(true, true) }
  object NS { def apply() = MS(false, false) }
  object CS {
    /* read and write configurable Security bit use one*/
    def apply(tzpc: Bool) = new CS(tzpc, tzpc)
  }
}
