/** SpinalHDL spinal.lib.bus.regif.RegInst 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - HardType[T] → 直接传 Chisel 类型模板（如 `UInt(4.W)` / `Bool()`）。
  *   - 已删除 deprecated 的 BitCount 版 field/fieldAt 重载。
  *   - 未移植 SpinalEnum 字段支持。
  *   - Chisel elaboration 期无法判断信号是否为寄存器，parasiteField 的
  *     isReg 断言改由用户保证。
  *   - Field 名称无法从硬件信号取回（reg.getName），registerIn*WriteLogic
  *     增加 fdname 参数由调用方传入。
  */

import chisel3._
import chisel3.util._

class RegInst(name: String, addr: BigInt, doc: String, busif: BusIf, sec: Secure = null, grp: GrpTag = null)
    extends RegBase(name, addr, doc, busif, sec = sec, grp = grp) {
  import AccessType._

  def setName(name: String): RegInst = {
    _name = name
    this
  }

  def fieldAt[T <: Data](pos: Int, hardType: T, acc: AccessType)(implicit symbol: SymbolName): T =
    fieldAt(pos, hardType, acc, resetValue = 0, doc = "")(symbol)
  def fieldAt[T <: Data](pos: Int, hardType: T, acc: AccessType, doc: String)(implicit symbol: SymbolName): T =
    fieldAt(pos, hardType, acc, resetValue = 0, doc = doc)(symbol)
  def fieldAt[T <: Data](pos: Int, hardType: T, acc: AccessType, resetValue: BigInt)(implicit
      symbol: SymbolName
  ): T = fieldAt(pos, hardType, acc, resetValue, doc = "")(symbol)
  def fieldAt[T <: Data](pos: Int, hardType: T, acc: AccessType, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    val width = hardType.getWidth
    val sectionNext: Range = pos to pos + width - 1
    val sectionExists: Range = 0 to fieldPtr
    val ret = pos match {
      case x if x < fieldPtr =>
        throw new Exception(
          s"next field section ${Section(sectionNext)} overlap to allocated Section ${Section(sectionExists)}"
        )
      case _ if sectionNext.max >= busif.busDataWidth =>
        throw new Exception(s"Range ${Section(sectionNext)} exceed Bus width ${busif.busDataWidth}")
      case x if (x == fieldPtr) => field(hardType, acc, resetValue, doc)(symbol)
      case _ => {
        field(UInt((pos - fieldPtr).W), AccessType.NA)(SymbolName("reserved"))
        field(hardType, acc, resetValue, doc)(symbol)
      }
    }
    fieldPtr = pos + width
    ret
  }

  def field[T <: Data](hardType: T, acc: AccessType)(implicit symbol: SymbolName): T =
    field(hardType, acc, resetValue = 0, doc = "")(symbol)
  def field[T <: Data](hardType: T, acc: AccessType, doc: String)(implicit symbol: SymbolName): T =
    field(hardType, acc, resetValue = 0, doc = doc)(symbol)
  def field[T <: Data](hardType: T, acc: AccessType, resetValue: BigInt)(implicit symbol: SymbolName): T =
    field(hardType, acc, resetValue, doc = "")(symbol)
  def field[T <: Data](hardType: T, acc: AccessType, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    val reg: T = acc match {
      case AccessType.NA => {
        val w = Wire(hardType)
        w := 0.U(hardType.getWidth.W).asTypeOf(hardType)
        w
      }
      case AccessType.ROV =>
        // 常量（如 device-ID / hw-Version）
        resetValue.U(hardType.getWidth.W).asTypeOf(hardType)
      case AccessType.RO | AccessType.W1I =>
        Wire(hardType) // RO 由用户驱动；W1I 由 _W1I 驱动
      case _ =>
        RegInit(regInitValue(hardType, resetValue))
    }
    (acc, symbol.name.startsWith("<local")) match {
      case (AccessType.ROV, _) =>
      case (_, true)           => reg.suggestName("unload")
      case (_, false)          => reg.suggestName(symbol.name)
    }
    registerInWithWriteLogic(reg, acc, resetValue, doc, fdname = symbol.name)
    reg
  }

  private def regInitValue[T <: Data](hardType: T, resetValue: BigInt): T = hardType match {
    case _: Bool => (resetValue % 2 == 1).B.asInstanceOf[T]
    case _       => resetValue.U(hardType.getWidth.W).asTypeOf(hardType)
  }

  /*
   * 多个地址共享同一个寄存器实例时使用，例：
   * val REG0 = busif.newReg(doc="Share Clock EnableRegister RW   address")
   * val REG1 = busif.newReg(doc="Share Clock EnableRegister W1S  address")
   * val reg32bit = REG0.field(UInt(32.W), RW, 0xffff, "clock enable reg RW")
   *                REG1.parasiteField(reg32bit, W1S, 0xffff, "clock enable reg write 1 set")
   * 注意：reg 必须是寄存器（Chisel 无法在 elaboration 期断言，由用户保证）
   * */
  def parasiteField[T <: Data](reg: T, acc: AccessType, resetValue: BigInt, doc: String): Unit = {
    registerInWithWriteLogic(reg, acc, resetValue, doc)
  }
  def parasiteFieldAt[T <: Data](pos: Int, reg: T, acc: AccessType, resetValue: BigInt, doc: String): Unit = {
    registerAtWithWriteLogic(pos, reg, acc, resetValue, doc)
  }

  def fieldHSRW[T <: Data](seten: Bool, setval: T)(implicit symbol: SymbolName): T =
    fieldHSRW(seten, setval, resetValue = 0, doc = "")(symbol)
  def fieldHSRW[T <: Data](seten: Bool, setval: T, resetValue: BigInt)(implicit symbol: SymbolName): T =
    fieldHSRW(seten, setval, resetValue, doc = "")(symbol)
  def fieldHSRW[T <: Data](seten: Bool, setval: T, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    val reg = field(chiselTypeOf(setval), acc = AccessType.HSRW, resetValue = resetValue, doc = doc)(symbol)
    when(seten) {
      reg := setval
    }
    reg
  }

  def fieldHSRWAt[T <: Data](pos: Int, seten: Bool, setval: T)(implicit symbol: SymbolName): T =
    fieldHSRWAt(pos, seten, setval, resetValue = 0, doc = "")(symbol)
  def fieldHSRWAt[T <: Data](pos: Int, seten: Bool, setval: T, resetValue: BigInt)(implicit
      symbol: SymbolName
  ): T = fieldHSRWAt(pos, seten, setval, resetValue, doc = "")(symbol)
  def fieldHSRWAt[T <: Data](pos: Int, seten: Bool, setval: T, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    val reg = fieldAt(pos, chiselTypeOf(setval), acc = AccessType.HSRW, resetValue = resetValue, doc = doc)(symbol)
    when(seten) {
      reg := setval
    }
    reg
  }

  private def newHsReg[T <: Data](setval: T, resetValue: BigInt, symbol: SymbolName): T = {
    val reg = RegInit(regInitValue(chiselTypeOf(setval), resetValue))
    if (symbol.name.startsWith("<local")) reg.suggestName("unload") else reg.suggestName(symbol.name)
    reg
  }

  def fieldRWHS[T <: Data](seten: Bool, setval: T)(implicit symbol: SymbolName): T =
    fieldRWHS(seten, setval, resetValue = 0, doc = "")(symbol)
  def fieldRWHS[T <: Data](seten: Bool, setval: T, resetValue: BigInt)(implicit symbol: SymbolName): T =
    fieldRWHS(seten, setval, resetValue, doc = "")(symbol)
  def fieldRWHS[T <: Data](seten: Bool, setval: T, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    val reg = newHsReg(setval, resetValue, symbol)
    when(seten) {
      reg := setval
    }
    registerInWithWriteLogic(reg, AccessType.RWHS, resetValue, doc, fdname = symbol.name)
    reg
  }

  def fieldRWHSAt[T <: Data](pos: Int, seten: Bool, setval: T)(implicit symbol: SymbolName): T =
    fieldRWHSAt(pos, seten, setval, resetValue = 0, doc = "")(symbol)
  def fieldRWHSAt[T <: Data](pos: Int, seten: Bool, setval: T, resetValue: BigInt)(implicit
      symbol: SymbolName
  ): T = fieldRWHSAt(pos, seten, setval, resetValue, doc = "")(symbol)
  def fieldRWHSAt[T <: Data](pos: Int, seten: Bool, setval: T, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    val reg = newHsReg(setval, resetValue, symbol)
    when(seten) {
      reg := setval
    }
    registerAtWithWriteLogic(pos, reg, AccessType.RWHS, resetValue, doc, fdname = symbol.name)
    reg
  }

  def fieldW1HS[T <: Data](seten: Bool, setval: T, acc: AccessType)(implicit symbol: SymbolName): T =
    fieldW1HS(seten, setval, acc, resetValue = 0, doc = "")(symbol)
  def fieldW1HS[T <: Data](seten: Bool, setval: T, acc: AccessType, resetValue: BigInt)(implicit
      symbol: SymbolName
  ): T = fieldW1HS(seten, setval, acc, resetValue, doc = "")(symbol)
  def fieldW1HS[T <: Data](seten: Bool, setval: T, acc: AccessType, resetValue: BigInt, doc: String)(implicit
      symbol: SymbolName
  ): T = {
    acc match {
      case `W1CHS` | `W1SHS` =>
      case _                 => throw new Exception("fieldW1HS only support W1CHS or W1SHS")
    }
    val reg = newHsReg(setval, resetValue, symbol)
    when(seten) {
      reg := setval
    }
    registerInWithWriteLogic(reg, acc, resetValue, doc, fdname = symbol.name)
    reg
  }

  def fieldW1HSAt[T <: Data](pos: Int, seten: Bool, setval: T, acc: AccessType)(implicit
      symbol: SymbolName
  ): T = fieldW1HSAt(pos, seten, setval, acc, resetValue = 0, doc = "")(symbol)
  def fieldW1HSAt[T <: Data](pos: Int, seten: Bool, setval: T, acc: AccessType, resetValue: BigInt)(implicit
      symbol: SymbolName
  ): T = fieldW1HSAt(pos, seten, setval, acc, resetValue, doc = "")(symbol)
  def fieldW1HSAt[T <: Data](pos: Int, seten: Bool, setval: T, acc: AccessType, resetValue: BigInt, doc: String)(
      implicit symbol: SymbolName
  ): T = {
    acc match {
      case `W1CHS` | `W1SHS` =>
      case _                 => throw new Exception("fieldW1HSAt only support W1CHS or W1SHS")
    }
    val reg = newHsReg(setval, resetValue, symbol)
    when(seten) {
      reg := setval
    }
    registerAtWithWriteLogic(pos, reg, acc, resetValue, doc, fdname = symbol.name)
    reg
  }

  protected def creatWriteLogic[T <: Data](reg: T, acc: AccessType, section: Range): Unit = {
    acc match {
      case AccessType.RO    => _RO(reg)             //- W: no effect, R: no effect
      case AccessType.ROV   => _RO(reg)             //- ReadOnlyValue, used for constant like device-ID/hw-Version
      case AccessType.RW    => _W(reg, section)     //- W: as-is, R: no effect
      case AccessType.RC    => _RC(reg, section)    //- W: no effect, R: clears all bits
      case AccessType.RS    => _RS(reg, section)    //- W: no effect, R: sets all bits
      case AccessType.WRC   => _WRC(reg, section)   //- W: as-is, R: clears all bits
      case AccessType.WRS   => _WRS(reg, section)   //- W: as-is, R: sets all bits
      case AccessType.WC    => _WC(reg, section)    //- W: clears all bits, R: no effect
      case AccessType.WS    => _WS(reg, section)    //- W: sets all bits, R: no effect
      case AccessType.WSRC  => _WSRC(reg, section)  //- W: sets all bits, R: clears all bits
      case AccessType.WCRS  => _WCRS(reg, section)  //- W: clears all bits, R: sets all bits
      case AccessType.W1C   => _WB(reg, section, AccessType.W1C)
      case AccessType.W1S   => _WB(reg, section, AccessType.W1S)
      case AccessType.W1T   => _WB(reg, section, AccessType.W1T)
      case AccessType.W0C   => _WB(reg, section, AccessType.W0C)
      case AccessType.W0S   => _WB(reg, section, AccessType.W0S)
      case AccessType.W0T   => _WB(reg, section, AccessType.W0T)
      case AccessType.W1SRC => _WBR(reg, section, AccessType.W1SRC)
      case AccessType.W1CRS => _WBR(reg, section, AccessType.W1CRS)
      case AccessType.W0SRC => _WBR(reg, section, AccessType.W0SRC)
      case AccessType.W0CRS => _WBR(reg, section, AccessType.W0CRS)
      case AccessType.WO    => Rerror = true; _W(reg, section)  //- W: as-is, R: error
      case AccessType.WOC   => Rerror = true; _WC(reg, section) //- W: clears all bits, R: error
      case AccessType.WOS   => Rerror = true; _WS(reg, section) //- W: sets all bits, R: error
      case AccessType.W1    => _W1(reg, section)                //- W: 复位后首写生效，R: no effect
      case AccessType.WO1   => Rerror = true; _W1(reg, section) //- W: 复位后首写生效，R: error
      case AccessType.NA    =>                                  //- 保留位（field() 中已绑 0）
      case AccessType.W1P   => _WBP(reg, section, AccessType.W1P)
      case AccessType.W0P   => _WBP(reg, section, AccessType.W0P)
      case AccessType.W1I   => _W1I(reg, section)
      case AccessType.HSRW  => _W(reg, section) // HardWare Set then SoftWare RW
      case AccessType.RWHS  => _W(reg, section) // SoftWare RW then HardWare Set
      case AccessType.W1CHS => _WB(reg, section, AccessType.W1C)
      case AccessType.W1SHS => _WB(reg, section, AccessType.W1S)
      case _: AccessType.CSTM => // CSTM 不生成逻辑，仅登记（用户自行实现）
    }
  }

  def registerInOnlyReadLogic[T <: Data](reg: T, acc: AccessType, resetValue: BigInt, doc: String,
      fdname: String = ""): Unit = {
    registerInWithWriteLogic(reg, acc, resetValue, doc, dontCreatWriteLogic = true, fdname = fdname)
  }

  def registerAtOnlyReadLogic[T <: Data](pos: Int, reg: T, acc: AccessType, resetValue: BigInt, doc: String,
      fdname: String = ""): Unit = {
    registerAtWithWriteLogic(pos, reg, acc, resetValue, doc, dontCreatWriteLogic = true, fdname = fdname)
  }

  def registerInWithWriteLogic[T <: Data](reg: T, acc: AccessType, resetValue: BigInt, doc: String,
      dontCreatWriteLogic: Boolean = false, fdname: String = ""): Unit = {
    val section: Range = fieldPtr to fieldPtr + reg.getWidth - 1
    if (!dontCreatWriteLogic) {
      creatWriteLogic(reg, acc, section)
    }
    val newdoc = if (doc.isEmpty && acc == AccessType.NA) "Reserved" else doc
    val nameRemoveNA =
      if (acc == AccessType.NA) "--" else if (fdname.nonEmpty) fdname else s"field_$fieldPtr"
    fields += Field(nameRemoveNA, reg, section, acc, resetValue, Rerror, newdoc)
    fieldPtr += reg.getWidth
  }

  def registerAtWithWriteLogic[T <: Data](pos: Int, reg: T, acc: AccessType, resetValue: BigInt, doc: String,
      dontCreatWriteLogic: Boolean = false, fdname: String = ""): Unit = {
    val sectionNext: Range = pos to pos + reg.getWidth - 1
    val sectionExists: Range = 0 to fieldPtr - 1
    pos match {
      case x if x < fieldPtr =>
        throw new Exception(
          s"next field section ${Section(sectionNext)} overlap to allocated Section ${Section(sectionExists)}"
        )
      case _ if sectionNext.max >= busif.busDataWidth =>
        throw new Exception(s"Range ${Section(sectionNext)} exceed Bus width ${busif.busDataWidth}")
      case x if (x == fieldPtr) => registerInWithWriteLogic(reg, acc, resetValue, doc, dontCreatWriteLogic, fdname)
      case _ => {
        val reserved = 0.U((pos - fieldPtr).W)
        registerInWithWriteLogic(reserved, AccessType.NA, 0, "Reserved", dontCreatWriteLogic)
        registerInWithWriteLogic(reg, acc, resetValue, doc, dontCreatWriteLogic, fdname)
      }
    }
    fieldPtr = pos + reg.getWidth
  }

  def reserved(bits: Int): UInt = {
    field(UInt(bits.W), AccessType.NA)(SymbolName("reserved"))
  }

  override def readGenerator(): Unit = {
    when(bi.readAddress() === addr.U) {
      bi.reg_rdata := rdSecurePassage(this.rdata())
      bi.reg_rderr := rdSecureError(this.haveWO.B)
    }
  }
}
