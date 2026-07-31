/** SpinalHDL spinal.lib.bus.regif.BusIf 的 Chisel 移植版。
  *
  * 寄存器接口构建 DSL：newReg/newRegAt/newRAM/newWrFifo/newRdFifo/newGrp 等。
  *
  * 与 SpinalHDL 的差异：
  *   - `component.addPrePopTask(...)` 延迟生成读逻辑 → 需在所有寄存器定义完成后
  *     显式调用 `busif.finish()`（生成读数据选择 + 可选安全防火墙写错误逻辑）。
  *   - `switch/is/default` 地址译码 → 默认值先赋 + 逐地址 when 覆盖（last-connect-wins）。
  *   - docPath：SpinalHDL 取 GlobalData targetDirectory，这里改为可设置变量
  *     （setDocPath，默认 "generated"）。
  */

import chisel3._
import chisel3.util._

import scala.collection.mutable.{HashMap, ListBuffer}

trait BusIf extends BusIfBase {
  val bus: Bundle

  private val SliceInsts = ListBuffer[RegSlice]()
  private var regPtr: BigInt = 0
  protected var readDefaultValue: BigInt = 0
  protected var accessDefaultError: Boolean = false
  protected var secFailReadValue: BigInt = null

  protected var grpId: Int = 1
  protected def grpIdInc(): Unit = (grpId += 1)

  private val secureLogicDict = HashMap[Bool, Bool]()

  def getOrCreateSecLogic(key: Bool, logic: => Bool): Bool = {
    secureLogicDict.getOrElseUpdate(key, logic)
  }

  def regSlicesNotReuse: List[RegSlice] = slices.filter(_.reuseTag.id == 0)
  def reuseGroups: Map[String, List[RegSlice]] =
    slices.filter(_.reuseTag.id != 0).groupBy(_.reuseTag.blockName)
  def reuseGroupsById: Map[String, Map[Int, List[RegSlice]]] = reuseGroups.map { case (name, slices) =>
    (name, slices.groupBy(_.reuseTag.id))
  }

  def repeatGroupsHead: Map[String, List[RegSlice]] = reuseGroupsById.map(t => t._1 -> t._2.head._2)
  def repeatGroupsBase: Map[String, List[RegSlice]] =
    reuseGroupsById.map(t => t._1 -> t._2.map(_._2.head).toList.sortBy(_.reuseTag.id))

  private var _docPath: String = "generated"
  def docPath: String = _docPath
  def setDocPath(path: String): this.type = { _docPath = path; this }

  def busName = bus.getClass.getSimpleName
  def newGrpTag(name: String) = {
    val ret = GrpTag(grpId, name)
    this.grpIdInc()
    ret
  }

  protected var blockId: Int = 1
  protected def blockIdInc(): Unit = (blockId += 1)
  def resetBlockTag(): Unit = {
    currentBlockTag = ReuseTag(0, "")
  }

  private var currentBlockTag = ReuseTag(0, "")

  def getCurrentBlockTag = currentBlockTag

  def newBlockTag(instName: String)(partName: String) = {
    val ret = ReuseTag(blockId, partName, regPtr, instName)
    this.blockIdInc()
    currentBlockTag = ret
    ret
  }

  def newBlockTagAt(addr: BigInt, instName: String)(partName: String) = {
    val ret = ReuseTag(blockId, partName, addr, instName)
    this.blockIdInc()
    currentBlockTag = ret
    ret
  }

  def RegAndFifos = SliceInsts.filter(!_.isInstanceOf[RamInst]).toList
  def RegInsts = SliceInsts.collect { case t: RegInst => t }
  def RamInsts = SliceInsts.collect { case t: RamInst => t }
  def FifoInsts = SliceInsts.collect { case t: FifoInst => t }

  def orderdRegInsts = SliceInsts.sortBy(_.addr)
  def getModuleName: String
  def setReservedAddressReadValue(value: BigInt) = readDefaultValue = value
  def getReservedAddressReadValue = readDefaultValue

  def setReservedAddressErrorState(state: Boolean) = accessDefaultError = state
  def getReservedAddressErrorState = accessDefaultError

  def setSecFailReadValue(value: BigInt) = secFailReadValue = value
  def getSecFailReadValue = secFailReadValue

  def defaultReadBits: UInt = readDefaultValue.U(busDataWidth.W)
  def secFailDefaultBits: UInt =
    Option(secFailReadValue).getOrElse(readDefaultValue).U(busDataWidth.W)
  def slices = SliceInsts.toList
  def hasBlock = SliceInsts.exists(_.reuseTag.id != 0)

  val regPre: String

  private val regAddressHistory = ListBuffer[BigInt]()
  private val regAddressMap = ListBuffer[SizeMapping]()
  def addressUsed(addr: BigInt) = regAddressHistory.contains(addr)
  def getAddrMap = regAddressHistory.toList.map("0x" + _.toString(16)) ++ regAddressMap.map(_.toString)

  private def attachAddr(addr: BigInt): Unit = {
    val ret = regAddressMap.filter(t => (addr <= t.end) && (addr >= t.base))
    if (regAddressHistory.contains(addr)) {
      throw new Exception(s"Address: 0x${addr.toString(16)} already used before, check please!")
    } else if (ret.nonEmpty) {
      throw new Exception(s"${ret.head} overlap with 0x${addr.toString(16)}")
    } else {
      regAddressHistory.append(addr)
    }
  }

  private def attachAddr(sizemap: SizeMapping): Unit = {
    val ret = regAddressMap.filter(_.overlap(sizemap))
    val t = regAddressHistory.filter(t => (t <= sizemap.end) && (t >= sizemap.base))
    if (ret.nonEmpty) {
      throw new Exception(s"${ret.head} overlap with ${sizemap}")
    } else if (t.nonEmpty) {
      throw new Exception(s"0x${t.head.toString(16)} overlap with ${sizemap}")
    } else {
      regAddressMap.append(sizemap)
    }
  }

  def getRegPtr(): BigInt = regPtr

  /*Attention: Should user make address no conflict them selves*/
  def regPtrReAnchorAt(pos: BigInt) = {
    require(pos % (busDataWidth / 8) == 0, s"Address Postion need allign datawidth ${busDataWidth / 8} byte")
    regPtr = pos
  }

  private def checkLastNA(): Unit = SliceInsts.foreach(_.checkLast)
  private def regNameUpdate(): Unit = {
    val words = "\\w*".r
    val pre = regPre match {
      case ""        => ""
      case words(_*) => regPre + "_"
      case _         => throw new Exception(s"${regPre} should be Valid naming : '[A-Za-z0-9_]+'")
    }
    RegInsts.foreach(t => t.setName(s"${pre}${t.getName()}"))
  }

  private var isChecked: Boolean = false
  def preCheck(): Unit = {
    if (!isChecked) {
      checkLastNA()
      regNameUpdate()
      isChecked = true
    }
  }

  /** 在所有寄存器 / 字段定义完成后调用（替代 SpinalHDL 的 addPrePopTask 自动机制）。 */
  def finish(): Unit = {
    preCheck()
    this.readGenerator()
    if (withSecFireWall) this.writeErrorGenerator()
  }

  def regPart(name: String)(block: => Unit) = {
    this.newBlockTag(name)(name)
    block
    this.resetBlockTag()
  }

  def accept(doc: BusIfDoc) = {
    preCheck()
    doc.generate(this)
  }

  def gen(doc: BusIfDoc) = {
    preCheck()
    doc.generate(this)
  }

  def genBaseDocs(docname: String, prefix: String = "") = {
    this.accept(DocHtml(docname))
    this.accept(DocJson(docname))
    this.accept(DocRalf(docname))
    this.accept(DocCHeader(docname, prefix))
    this.accept(DocSVHeader(docname, prefix))
  }

  def newRegAt(address: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null)(implicit symbol: SymbolName) = {
    addrAlignCheck(address)
    val reg = createReg(symbol.name, address, doc, sec, grp)
    regPtr = address + wordAddressInc
    reg
  }

  def newReg(doc: String, sec: Secure = null, grp: GrpTag = null)(implicit symbol: SymbolName) = {
    val res = createReg(symbol.name, regPtr, doc, sec, grp)
    regPtr += wordAddressInc
    res
  }

  def createReg(name: String, addr: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null) = {
    val ret = new RegInst(name, addr, doc, this, sec, grp)
    SliceInsts += ret
    attachAddr(addr)
    ret
  }

  def newRAM(size: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null)(implicit symbol: SymbolName) = {
    val res = createRAM(symbol.name, regPtr, size, doc, sec, grp)
    regPtr += scala.math.ceil(size.toDouble / wordAddressInc).toLong * wordAddressInc
    res
  }

  def newRAMAt(address: BigInt, size: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null)(implicit
      symbol: SymbolName
  ) = {
    addrAlignCheck(address)
    val res = createRAM(symbol.name, address, size, doc, sec, grp)
    regPtr = address + scala.math.ceil(size.toDouble / wordAddressInc).toLong * wordAddressInc
    res
  }

  def createRAM(name: String, addr: BigInt, size: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null) = {
    val ret = new RamInst(name, addr, size, doc, sec, grp)(this)
    SliceInsts += ret
    attachAddr(SizeMapping(addr, size))
    ret
  }

  def newWrFifo(doc: String, sec: Secure = null, grp: GrpTag = null)(implicit symbol: SymbolName): WrFifoInst = {
    val res = createWrFifo(symbol.name, regPtr, doc, sec, grp)
    regPtr += wordAddressInc
    res
  }

  def newWrFifoAt(address: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null)(implicit symbol: SymbolName) = {
    addrAlignCheck(address)
    val res = createWrFifo(symbol.name, address, doc, sec, grp)
    regPtr = address + wordAddressInc
    res
  }

  def createWrFifo(name: String, addr: BigInt, Doc: String, sec: Secure = null, grp: GrpTag = null) = {
    val ret = new WrFifoInst(name, addr, Doc, sec, grp)(this)
    SliceInsts += ret
    attachAddr(addr)
    ret
  }

  def createRdFifo(name: String, addr: BigInt, Doc: String, sec: Secure = null, grp: GrpTag = null): RdFifoInst = {
    val ret = new RdFifoInst(name, addr, Doc, sec, grp)(this)
    SliceInsts += ret
    attachAddr(addr)
    ret
  }

  def newRdFifo(doc: String, sec: Secure = null, grp: GrpTag = null)(implicit symbol: SymbolName): RdFifoInst = {
    val res = createRdFifo(symbol.name, regPtr, doc, sec, grp)
    regPtr += wordAddressInc
    res
  }

  def newRdFifoAt(address: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null)(implicit
      symbol: SymbolName
  ): RdFifoInst = {
    addrAlignCheck(address)
    val res = createRdFifo(symbol.name, address, doc, sec, grp)
    regPtr = address + wordAddressInc
    res
  }

  def newGrp(maxSize: BigInt, doc: String, sec: Secure = null)(implicit symbol: SymbolName) = {
    createGrp(symbol.name, regPtr, maxSize, doc, sec)
  }

  def newGrpAt(address: BigInt, maxSize: BigInt, doc: String, sec: Secure = null)(implicit symbol: SymbolName) = {
    createGrp(symbol.name, address, maxSize, doc, sec)
  }

  def createGrp(name: String, addr: BigInt, maxSize: BigInt, doc: String, sec: Secure = null) = {
    val grp = this.newGrpTag(name)
    val ret = RegSliceGrp(addr, maxSize, doc, sec, grp)(this)
    ret
  }

  def addrAlignCheck(address: BigInt) = {
    if (_addrAlignCheck)
      require(address % wordAddressInc == 0, s"located Position not align by wordAddressInc: ${wordAddressInc}")
  }

  private def regReadPart(): Unit = {
    // 等效 SpinalHDL 的 switch/is/default：先赋默认值，再逐地址 when 覆盖
    reg_rdata := defaultReadBits
    if (accessDefaultError) {
      reg_rderr := true.B
    } else {
      if (withStrb) {
        reg_rderr := false.B
      } else {
        val alignreadhit = readAddress()(log2Ceil(wordAddressInc) - 1, 0).orR
        reg_rderr := alignreadhit
      }
    }
    RegAndFifos.foreach { (x: RegSlice) =>
      if (!x.allIsNA) x.readGenerator()
    }
  }

  private def regReadGenerator(): Unit = {
    when(askRead) {
      regReadPart()
    }.otherwise {
      // do not keep readData after read for the reason of security risk
      reg_rdata := defaultReadBits
      reg_rderr := false.B
    }
  }

  private def readGenerator(): Unit = {
    this.regReadGenerator()
    // 等效 SpinalHDL WhenBuilder：默认 reg_rdata，RAM 命中时覆盖
    bus_rdata := reg_rdata
    RamInsts.foreach { ram =>
      when(ram.ram_rdvalid) {
        bus_rdata := ram.readBits()
      }
    }
  }

  private def writeErrorGenerator(): Unit = {
    when(askWrite) {
      if (accessDefaultError) {
        reg_wrerr := true.B
      } else {
        if (withStrb) {
          reg_wrerr := false.B
        } else {
          val alignwritehit = writeAddress()(log2Ceil(wordAddressInc) - 1, 0).orR
          reg_wrerr := alignwritehit
        }
      }
      RegAndFifos.foreach { slice =>
        slice.wrErrorGenerator()
      }
      RamInsts.foreach { ram =>
        ram.wrErrorGenerator()
      }
    }.otherwise {
      reg_wrerr := false.B
    }
  }
}
