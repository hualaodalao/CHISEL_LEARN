/** SpinalHDL spinal.lib.bus.regif 中断工厂的 Chisel 移植版。
  *
  * 包含 IntrBase / IntrRFMS4 / IntrRMS3 / IntrMS2 以及 package object 中的
  * BusIfIntr 隐式类（interruptFactory / interruptFactoryNoForce / interruptLevelFactory）。
  *
  * 与 SpinalHDL 的差异：
  *   - eventLogic 的逐位 `raww(i).setWhen(sig(i))` 在 Chisel 中无法对 UInt 寄存器
  *     按位赋值，故 raw 字段改用 registerIn/AtOnlyReadLogic 注册（不生成写逻辑），
  *     由 rawUpdate 手写合并更新：
  *       raw := (raw & ~clrMask) | setMask
  *     其中 clrMask 来自 RAW 的 W1C 写命中、setMask 来自 FORCE 的 W1S 写命中与
  *     硬件事件信号（置位优先，逐位语义与源码一致）。
  *   - 中断信号类型仅支持 Bool / UInt。
  *   - trigger 无法取回信号名（getPartialName），工厂方法以下标命名字段。
  *   - IntrS1 / IntrOMS3 / IntrMMS3 / IntrOMMS4 / IntrRFMMS5 见 IntrExtra.scala，
  *     RegSC / RegSCR 见 RegSCR.scala。
  *   - 未移植：FactoryInterruptWithMask（deprecated）。
  *   - 隐式类需包裹在 object RegIfIntr 中，使用处 `import RegIfIntr._`。
  */

import chisel3._
import chisel3.util._

import scala.collection.mutable.ListBuffer

trait IntrBase {
  val name: String
  protected val statusbuf: ListBuffer[Data] = ListBuffer[Data]()

  def getStatus = statusbuf.toList

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T

  def intr()(implicit symbol: SymbolName): Bool = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    statusbuf.map {
      case x: Bool => x
      case x: UInt => x.orR
      case _       => throw new Exception("Interrupt Signals only accept Bool/UInt")
    }.reduceLeft(_ || _).suggestName(s"${nm}_intr".toLowerCase())
  }

  def levelLogic[T <: Data](signal: T, mask: T, status: T): Unit = {
    status := (signal.asUInt & (~mask.asUInt).asUInt).asTypeOf(status)
  }
}

/* RFMS4(RAW/FORCE/MASK/STATUS) 4 寄存器一级中断组
 * 1. RAW:    raw status register, set when event, clear raw when write 1
 * 2. FORCE:  force register, for SW debug use, write 1 set raw
 * 3. MASK:   mask register, 1: int off, 0: int open, default 1, int off
 * 4. STATUS: status register, status = raw && (!mask)
 * */
class IntrRFMS4(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 4 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val RAW = this.newRegAt(0, s"${doc} RFMS4-Raw status Register\n set when event \n clear raw when write 1")(
    SymbolName(s"${name}_INT_RAW"))
  val FORCE = this.newReg(s"${doc} RFMS4-Force Register\n for SW debug use \n write 1 set raw")(
    SymbolName(s"${name}_INT_FORCE"))
  val MASK = this.newReg(s"${doc} RFMS4-Mask Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASK"))
  val STATUS = this.newReg(s"${doc} RFMS4-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  /** raw := (raw & ~clrMask) | setMask（置位优先，与源码逐位 setWhen 语义一致） */
  private def rawUpdate[T <: Data](raw: T, signal: T, posRaw: Int, posForce: Int): Unit = {
    val w = raw.getWidth
    val secRaw: Range = posRaw to posRaw + w - 1
    val secForce: Range = posForce to posForce + w - 1
    val clrMask = Mux(RAW.hitDoWrite, bi.mwdata(secRaw), 0.U(w.W))
    val setMask = Mux(FORCE.hitDoWrite, bi.mwdata(secForce), 0.U(w.W)) | signal.asUInt
    raw := ((raw.asUInt & (~clrMask).asUInt) | setMask).asTypeOf(raw)
  }

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val raw = RegInit(0.U(signal.getWidth.W).asTypeOf(chiselTypeOf(signal))).suggestName(s"${nm}_raw")
    RAW.registerAtOnlyReadLogic(pos, raw, AccessType.W1C, 0, s"${doc} raw, default 0", fdname = s"${nm}_raw")
    FORCE.registerAtOnlyReadLogic(pos, raw, AccessType.W1S, 0, s"${doc} force, write 1 set, debug use",
      fdname = s"${nm}_raw")
    rawUpdate(raw, signal, pos, pos)
    val mask = MASK.fieldAt(pos, chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(raw, mask, status)
    statusbuf += status
    status
  }

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.matches("^[$<].*")) name else symbol.name
    val raw = RegInit(0.U(signal.getWidth.W).asTypeOf(chiselTypeOf(signal))).suggestName(s"${nm}_raw")
    val posRaw = RAW.getfieldPtr
    RAW.registerInOnlyReadLogic(raw, AccessType.W1C, 0, s"${doc} raw, default 0", fdname = s"${nm}_raw")
    val posForce = FORCE.getfieldPtr
    FORCE.registerInOnlyReadLogic(raw, AccessType.W1S, 0, s"${doc} force, write 1 set, debug use",
      fdname = s"${nm}_raw")
    rawUpdate(raw, signal, posRaw, posForce)
    val mask = MASK.field(chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(raw, mask, status)
    statusbuf += status
    status
  }
}

/* RMS3(RAW/MASK/STATUS) 3 寄存器一级中断组（无 FORCE） */
class IntrRMS3(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 3 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val RAW = this.newRegAt(0, s"${doc} RMS3-Raw status Register\n set when event \n clear raw when write 1")(
    SymbolName(s"${name}_INT_RAW"))
  val MASK = this.newReg(s"${doc} RMS3-Mask Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASK"))
  val STATUS = this.newReg(s"${doc} RMS3-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  private def rawUpdate[T <: Data](raw: T, signal: T, posRaw: Int): Unit = {
    val w = raw.getWidth
    val secRaw: Range = posRaw to posRaw + w - 1
    val clrMask = Mux(RAW.hitDoWrite, bi.mwdata(secRaw), 0.U(w.W))
    val setMask = signal.asUInt
    raw := ((raw.asUInt & (~clrMask).asUInt) | setMask).asTypeOf(raw)
  }

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val raw = RegInit(0.U(signal.getWidth.W).asTypeOf(chiselTypeOf(signal))).suggestName(s"${nm}_raw")
    RAW.registerAtOnlyReadLogic(pos, raw, AccessType.W1C, 0, s"${doc} raw, default 0", fdname = s"${nm}_raw")
    rawUpdate(raw, signal, pos)
    val mask = MASK.fieldAt(pos, chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(raw, mask, status)
    statusbuf += status
    status
  }

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val raw = RegInit(0.U(signal.getWidth.W).asTypeOf(chiselTypeOf(signal))).suggestName(s"${nm}_raw")
    val posRaw = RAW.getfieldPtr
    RAW.registerInOnlyReadLogic(raw, AccessType.W1C, 0, s"${doc} raw, default 0", fdname = s"${nm}_raw")
    rawUpdate(raw, signal, posRaw)
    val mask = MASK.field(chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(raw, mask, status)
    statusbuf += status
    status
  }
}

/* MS2(MASK/STATUS) 2 寄存器二级中断合并组 */
class IntrMS2(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 2 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val MASK = this.newRegAt(0, s"${doc} MS2-Mask Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASK"))
  val STATUS = this.newReg(s"${doc} MS2-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val mask = MASK.fieldAt(pos, chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val mask = MASK.field(chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }
}

/** 对应 SpinalHDL package object regif 中的 BusIfIntr 隐式类，使用处 `import RegIfIntr._` */
object RegIfIntr {
  implicit class BusIfIntr(bi: BusIf) {
    /* interrupt with Raw/Force/Mask/Status 4 Register Interface */
    def interruptFactory(regNamePre: String, triggers: Bool*): Bool =
      interruptFactoryAt(bi.getRegPtr(), regNamePre, triggers: _*)
    def interruptFactoryAt(addrOffset: BigInt, regNamePre: String, triggers: Bool*): Bool = {
      require(triggers.size > 0 && triggers.size <= bi.busDataWidth,
        s"The maxNum should be ${bi.busDataWidth} else split 2 or more groups")
      val IntrGrp = this.newIntrRFMS4(addrOffset, "")(SymbolName(s"${regNamePre}"))
      triggers.zipWithIndex.foreach { case (event, i) =>
        IntrGrp.field(event, maskRstVal = 0x1, doc = "")(SymbolName(s"${regNamePre}_$i"))
      }
      IntrGrp.intr()(SymbolName(s"${regNamePre}"))
    }

    /* interrupt with Raw/Mask/Status 3 Register Interface */
    def interruptFactoryNoForce(regNamePre: String, triggers: Bool*): Bool =
      interruptFactoryNoForceAt(bi.getRegPtr(), regNamePre, triggers: _*)
    def interruptFactoryNoForceAt(addrOffset: BigInt, regNamePre: String, triggers: Bool*): Bool = {
      require(triggers.size > 0 && triggers.size <= bi.busDataWidth,
        s"The maxNum should be ${bi.busDataWidth} else split 2 or more groups")
      val IntrGrp = this.newIntrRMS3(addrOffset, "")(SymbolName(s"${regNamePre}"))
      triggers.zipWithIndex.foreach { case (event, i) =>
        IntrGrp.field(event, maskRstVal = 0x1, doc = "")(SymbolName(s"${regNamePre}_$i"))
      }
      IntrGrp.intr()(SymbolName(s"${regNamePre}"))
    }

    /* interrupt with Raw/Force/Mask_SET/Mask_CLR/Status 5 Register Interface
     * It is to solve the atomic problem that may arise from operating the same mask address
     * on multiple processor cores: two independent mask addresses operate on the same mask
     * register, so two cores can independently set one bit without affecting other bits */
    def interrupt_W1SCmask_FactoryAt(addrOffset: BigInt, regNamePre: String, triggers: Bool*): Bool = {
      require(triggers.size > 0 && triggers.size <= bi.busDataWidth,
        s"The maxNum should be ${bi.busDataWidth} else split 2 or more groups")
      val IntrGrp = this.newIntrRFMMS5(addrOffset, "")(SymbolName(s"${regNamePre}"))
      triggers.zipWithIndex.foreach { case (event, i) =>
        IntrGrp.field(event, maskRstVal = 0x1, doc = "")(SymbolName(s"${regNamePre}_$i"))
      }
      IntrGrp.intr()(SymbolName(s"${regNamePre}"))
    }

    /* interrupt with Mask_SET/Mask_CLR/Status 3 Register Interface, always used for sys_level_int merge */
    def interruptLevel_W1SCmask_FactoryAt(addrOffset: BigInt, regNamePre: String, levels: Bool*): Bool = {
      require(levels.size > 0 && levels.size <= bi.busDataWidth,
        s"The maxNum should be ${bi.busDataWidth} else split 2 or more groups")
      val IntrGrp = this.newIntrMMS3(addrOffset, "")(SymbolName(s"${regNamePre}"))
      levels.zipWithIndex.foreach { case (level, i) =>
        IntrGrp.field(level, maskRstVal = 0x1, doc = "")(SymbolName(s"${regNamePre}_$i"))
      }
      IntrGrp.intr()(SymbolName(s"${regNamePre}"))
    }

    /* interrupt with Mask/Status 2 Register Interface, always used for sys_level_int merge */
    def interruptLevelFactory(regNamePre: String, levels: Bool*): Bool =
      interruptLevelFactoryAt(bi.getRegPtr(), regNamePre, levels: _*)
    def interruptLevelFactoryAt(addrOffset: BigInt, regNamePre: String, levels: Bool*): Bool = {
      require(levels.size > 0 && levels.size <= bi.busDataWidth,
        s"The maxNum should be ${bi.busDataWidth} else split 2 or more groups")
      val IntrGrp = this.newIntrMS2(addrOffset, "")(SymbolName(s"${regNamePre}"))
      levels.zipWithIndex.foreach { case (level, i) =>
        IntrGrp.field(level, maskRstVal = 0x1, doc = "")(SymbolName(s"${regNamePre}_$i"))
      }
      IntrGrp.intr()(SymbolName(s"${regNamePre}"))
    }

    def newIntrRFMS4(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrRFMS4 =
      new IntrRFMS4(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrRFMS4(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrRFMS4 =
      new IntrRFMS4(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrRFMS4(doc: String)(implicit symbol: SymbolName): IntrRFMS4 =
      new IntrRFMS4(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrRMS3(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrRMS3 =
      new IntrRMS3(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrRMS3(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrRMS3 =
      new IntrRMS3(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrRMS3(doc: String)(implicit symbol: SymbolName): IntrRMS3 =
      new IntrRMS3(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrMS2(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrMS2 =
      new IntrMS2(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrMS2(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrMS2 =
      new IntrMS2(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrMS2(doc: String)(implicit symbol: SymbolName): IntrMS2 =
      new IntrMS2(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrRFMMS5(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrRFMMS5 =
      new IntrRFMMS5(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrRFMMS5(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrRFMMS5 =
      new IntrRFMMS5(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrRFMMS5(doc: String)(implicit symbol: SymbolName): IntrRFMMS5 =
      new IntrRFMMS5(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrOMS3(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrOMS3 =
      new IntrOMS3(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrOMS3(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrOMS3 =
      new IntrOMS3(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrOMS3(doc: String)(implicit symbol: SymbolName): IntrOMS3 =
      new IntrOMS3(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrOMMS4(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrOMMS4 =
      new IntrOMMS4(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrOMMS4(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrOMMS4 =
      new IntrOMMS4(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrOMMS4(doc: String)(implicit symbol: SymbolName): IntrOMMS4 =
      new IntrOMMS4(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrMMS3(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrMMS3 =
      new IntrMMS3(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newIntrMMS3(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrMMS3 =
      new IntrMMS3(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newIntrMMS3(doc: String)(implicit symbol: SymbolName): IntrMMS3 =
      new IntrMMS3(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newIntrS1(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): IntrS1 =
      new IntrS1(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag("-"))
    def newIntrS1(addr: BigInt, doc: String)(implicit symbol: SymbolName): IntrS1 =
      new IntrS1(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag("-"))
    def newIntrS1(doc: String)(implicit symbol: SymbolName): IntrS1 =
      new IntrS1(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag("-"))

    def newRegSCAt(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): RegSC =
      new RegSC(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newRegSCAt(addr: BigInt, doc: String)(implicit symbol: SymbolName): RegSC =
      new RegSC(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newRegSC(doc: String)(implicit symbol: SymbolName): RegSC =
      new RegSC(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))

    def newRegSCRAt(addr: BigInt, doc: String, sec: Secure)(implicit symbol: SymbolName): RegSCR =
      new RegSCR(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = sec, grpTag = bi.newGrpTag(symbol.name))
    def newRegSCRAt(addr: BigInt, doc: String)(implicit symbol: SymbolName): RegSCR =
      new RegSCR(name = symbol.name, offset = addr, doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
    def newRegSCR(doc: String)(implicit symbol: SymbolName): RegSCR =
      new RegSCR(name = symbol.name, offset = bi.getRegPtr(), doc = doc, bif = bi, sec = null, grpTag = bi.newGrpTag(symbol.name))
  }
}
