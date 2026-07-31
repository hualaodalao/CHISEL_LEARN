/** SpinalHDL spinal.lib.bus.regif Factory 目录剩余中断组的 Chisel 移植版。
  *
  * 包含 IntrS1 / IntrOMS3 / IntrMMS3 / IntrOMMS4 / IntrRFMMS5。
  *
  * 与 SpinalHDL 的差异（同 Interrupts.scala）：
  *   - IntrRFMMS5 的 raw 字段改用 registerIn/AtOnlyReadLogic 注册 +
  *     rawUpdate 手写合并（Chisel 无法对 UInt 寄存器按位 setWhen）。
  *   - MASKS/MASKC 双地址操作同一 mask 寄存器：MASKS.field(W1S) 创建寄存器，
  *     MASKC.parasiteField(W1C) 追加写逻辑（两地址写命中互斥，无 last-connect 冲突）。
  *   - trigger 无法取回信号名（getPartialName），以 symbol/name 命名字段。
  */

import chisel3._

/* S1(STATUS) 1 Interrupt Register Group used for 2nd interrupt signal merge
 * 1. STATUS: status register, status = raw && (!mask)
 * */
class IntrS1(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 1 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val STATUS = this.newRegAt(0, s"${doc} MS2-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  @deprecated("IntrS1 without mask, so maskRstVal is invalid, use fieldAt(pos, signal, doc) instead", "")
  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T =
    fieldAt(pos, signal, doc)(symbol)
  def fieldAt[T <: Data](pos: Int, signal: T, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    status := signal
    statusbuf += status
    status
  }

  @deprecated("IntrS1 without mask, so maskRstVal is invalid, use field(signal, doc) instead", "")
  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T =
    field(signal, doc)(symbol)
  def field[T <: Data](signal: T, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    status := signal
    statusbuf += status
    status
  }
}

/* OMS3(ORIGIN/MASK/STATUS) 3 Interrupt Register Group, always used for 2nd interrupt signal merge
 * 1. ORIGIN: Origin level-signal Interrupt
 * 2. MASK:   mask register, 1: int off, 0: int open, default 1, int off
 * 3. STATUS: status register, status = origin && (!mask)
 * */
class IntrOMS3(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 3 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val ORIGIN = this.newRegAt(0, s"${doc} OMS3-Raw status Register\n set when event \n clear raw when write 1")(
    SymbolName(s"${name}_INT_RAW"))
  val MASK = this.newReg(s"${doc} OMS3-Mask Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASK"))
  val STATUS = this.newReg(s"${doc} OMS3-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val origin = ORIGIN.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} raw, default 0")(SymbolName(s"${nm}_raw"))
    val mask = MASK.fieldAt(pos, chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    origin := signal
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val origin = ORIGIN.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} raw, default 0")(SymbolName(s"${nm}_raw"))
    val mask = MASK.field(chiselTypeOf(signal), AccessType.RW, maskRstVal,
      s"${doc} mask, default 1, int off")(SymbolName(s"${nm}_mask"))
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    origin := signal
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }
}

/* MMS3(MASKS/MASKC/STATUS) 3 Interrupt Register Group, always used for sys_level_int merge
 * 1. MASKS:  mask set register, 1: int off, 0: int open, default 1, int off
 * 2. MASKC:  mask clear register, 1: int off, 0: int open, default 1, int off
 * 3. STATUS: status register, status = signal && (!mask)
 * */
class IntrMMS3(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 3 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val MASKS = this.newRegAt(0, s"${doc} MMS3-Mask W1S Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASKS"))
  val MASKC = this.newReg(s"${doc} MMS3-Mask W1C Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASKC"))
  val STATUS = this.newReg(s"${doc} MMS3-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val mask = MASKS.fieldAt(pos, chiselTypeOf(signal), AccessType.W1S, maskRstVal,
      s"${doc} mask, write 1 set")(SymbolName(s"${nm}_mask"))
    MASKC.parasiteFieldAt(pos, mask, AccessType.W1C, maskRstVal, s"${doc} mask, write 1 clr")
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val mask = MASKS.field(chiselTypeOf(signal), AccessType.W1S, maskRstVal,
      s"${doc} mask, write 1 set")(SymbolName(s"${nm}_mask"))
    MASKC.parasiteField(mask, AccessType.W1C, maskRstVal, s"${doc} mask, write 1 clr")
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }
}

/* OMMS4(ORIGIN/MASKS/MASKC/STATUS) 4 Interrupt Register Group used for 2nd interrupt signal merge
 * 1. ORIGIN: Origin level-signal Interrupt
 * 2. MASKS:  mask set register, 1: int off, 0: int open, default 1, int off
 * 3. MASKC:  mask clear register, 1: int off, 0: int open, default 1, int off
 * 4. STATUS: status register, status = origin && (!mask)
 * */
class IntrOMMS4(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 4 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val ORIGIN = this.newRegAt(0, s"${doc} OMMS4-Raw status Register\n set when event \n clear raw when write 1")(
    SymbolName(s"${name}_INT_RAW"))
  val MASKS = this.newReg(s"${doc} OMMS4-Mask W1S Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASKS"))
  val MASKC = this.newReg(s"${doc} OMMS4-Mask W1C Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASKC"))
  val STATUS = this.newReg(s"${doc} OMMS4-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  def fieldAt[T <: Data](pos: Int, signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val origin = ORIGIN.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} raw, default 0")(SymbolName(s"${nm}_raw"))
    val mask = MASKS.fieldAt(pos, chiselTypeOf(signal), AccessType.W1S, maskRstVal,
      s"${doc} mask, write 1 set")(SymbolName(s"${nm}_mask"))
    MASKC.parasiteFieldAt(pos, mask, AccessType.W1C, maskRstVal, s"${doc} mask, write 1 clr")
    val status = STATUS.fieldAt(pos, chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    origin := signal
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }

  def field[T <: Data](signal: T, maskRstVal: BigInt, doc: String)(implicit symbol: SymbolName): T = {
    val nm = if (symbol.name.startsWith("<local")) name else symbol.name
    val origin = ORIGIN.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} raw, default 0")(SymbolName(s"${nm}_raw"))
    val mask = MASKS.field(chiselTypeOf(signal), AccessType.W1S, maskRstVal,
      s"${doc} mask, write 1 set")(SymbolName(s"${nm}_mask"))
    MASKC.parasiteField(mask, AccessType.W1C, maskRstVal, s"${doc} mask, write 1 clr")
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    origin := signal
    this.levelLogic(signal, mask, status)
    statusbuf += status
    status
  }
}

/* RFMMS5(RAW/FORCE/MASKS/MASKC/STATUS) 5 Interrupt Register Group used for 1nd interrupt generate
 * 1. RAW:    raw status register, set when event, clear raw when write 1
 * 2. FORCE:  force register, for SW debug use, write 1 set raw
 * 3. MASKS:  mask set register, 1: int off, 0: int open, default 1, int off
 * 4. MASKC:  mask clear register, 1: int off, 0: int open, default 1, int off
 * 5. STATUS: status register, status = raw && (!mask)
 * */
class IntrRFMMS5(val name: String, offset: BigInt, doc: String, bif: BusIf, sec: Secure, grpTag: GrpTag)
    extends RegSliceGrp(offset, maxSize = 5 * bif.bw, doc, sec, grpTag)(bif) with IntrBase {
  val RAW = this.newRegAt(0, s"${doc} RFMMS5-Raw status Register\n set when event \n clear raw when write 1")(
    SymbolName(s"${name}_INT_RAW"))
  val FORCE = this.newReg(s"${doc} RFMMS5-Force Register\n for SW debug use \n write 1 set raw")(
    SymbolName(s"${name}_INT_FORCE"))
  val MASKS = this.newReg(s"${doc} RFMMS5-Mask Set Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASKS"))
  val MASKC = this.newReg(s"${doc} RFMMS5-Mask Clr Register\n1: int off\n0: int open\n default 1, int off")(
    SymbolName(s"${name}_INT_MASKC"))
  val STATUS = this.newReg(s"${doc} RFMMS5-status Register\n status = raw && (!mask)")(
    SymbolName(s"${name}_INT_STATUS"))

  /** raw := (raw & ~clrMask) | setMask（置位优先，与源码 eventLogic 逐位 setWhen 语义一致） */
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
    val mask = MASKS.fieldAt(pos, chiselTypeOf(signal), AccessType.W1S, maskRstVal,
      s"${doc} mask, write 1 set")(SymbolName(s"${nm}_mask"))
    MASKC.parasiteFieldAt(pos, mask, AccessType.W1C, maskRstVal, s"${doc} mask, write 1 clear")
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
    val posForce = FORCE.getfieldPtr
    FORCE.registerInOnlyReadLogic(raw, AccessType.W1S, 0, s"${doc} force, write 1 set, debug use",
      fdname = s"${nm}_raw")
    rawUpdate(raw, signal, posRaw, posForce)
    val mask = MASKS.field(chiselTypeOf(signal), AccessType.W1S, maskRstVal,
      s"${doc} mask, write 1 set")(SymbolName(s"${nm}_mask"))
    MASKC.parasiteField(mask, AccessType.W1C, maskRstVal, s"${doc} mask, write 1 clear")
    val status = STATUS.field(chiselTypeOf(signal), AccessType.RO, 0,
      s"${doc} status default 0")(SymbolName(s"${nm}_status"))
    this.levelLogic(raw, mask, status)
    statusbuf += status
    status
  }
}
