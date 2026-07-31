/** SpinalHDL spinal.lib.bus.regif.RegSlice / RegSliceGrp 的 Chisel 移植版。
  *
  *  RegSlice  -->  RegBase  -->  RegInst
  *            \--------------->  RamInst
  *             \-------------->  FifoInst
  *
  * 与 SpinalHDL 的差异：
  *   - readGenerator/wrErrorGenerator 自带 `when(地址命中)` 包裹
  *     （SpinalHDL 使用外层 switch + is(addr)，Chisel 用 when 链等效）。
  *   - setName(weak) → suggestName。
  */

import chisel3._
import chisel3.util._

import scala.collection.mutable.ListBuffer

abstract class RegSlice(val name: String, val addr: BigInt, val doc: String, val size: BigInt,
                        sec: Secure = null, val grp: GrpTag = null)(val bi: BusIf) {
  protected var _name = name
  protected val fields = ListBuffer[Field]()
  protected var fieldPtr: Int = 0
  protected var Rerror: Boolean = false
  def nextAddr = addr + size

  /*
   * 1: access pass
   * 0: access block
   * */
  private val blockSignals = ListBuffer[Bool]()
  def addBlockSignal(sig: Bool) = blockSignals.append(sig)

  lazy val secureLogic: (Option[Bool], Option[Bool]) = {
    val ret: (Bool, Bool) = if (bi.withSecFireWall) {
      Option(sec) match {
        case Some(Secure.MS(x, y)) => {
          def pass = (!bi.bus_nsbit).suggestName("ns_sec_pass")
          val ret = bi.getOrCreateSecLogic(bi.bus_nsbit, pass)
          (x, y) match {
            case (true, true)   => (ret, ret)
            case (false, false) => (null, null)
            case (true, false)  => (ret, null)
            case (false, true)  => (null, ret)
          }
        }
        case Some(Secure.CS(wrns, rdns)) => {
          if (wrns == rdns) {
            val ret = bi.getOrCreateSecLogic(wrns, (!(bi.bus_nsbit & !wrns)).suggestName("cs_sec_pass"))
            (ret, ret)
          } else {
            val ret1 = bi.getOrCreateSecLogic(wrns, (!(bi.bus_nsbit & !wrns)).suggestName("cs_wr_sec_pass"))
            val ret2 = bi.getOrCreateSecLogic(rdns, (!(bi.bus_nsbit & !rdns)).suggestName("cs_rd_sec_pass"))
            (ret1, ret2)
          }
        }
        case None => (null, null)
      }
    } else (null, null)
    (Option(ret._1), Option(ret._2))
  }

  def wrSecureError(): Bool = {
    secureLogic._1 match {
      case Some(pass: Bool) => !pass
      case None             => false.B
    }
  }

  def rdSecureError(err: Bool): Bool = {
    secureLogic._2 match {
      case Some(pass: Bool) => (!pass) || err
      case None             => err
    }
  }

  def wrSecurePassage(access: Bool): Bool = {
    secureLogic._1 match {
      case Some(nspass: Bool) => access & nspass
      case None               => access
    }
  }

  def wrSecurePassage(wrbits: UInt): UInt = {
    secureLogic._1 match {
      case Some(nspass: Bool) => Mux(nspass, wrbits, 0.U(bi.busDataWidth.W))
      case None               => wrbits
    }
  }

  def rdSecurePassage(access: Bool): Bool = {
    secureLogic._2 match {
      case Some(nspass: Bool) => access & nspass
      case None               => access
    }
  }

  def rdSecurePassage(rdbits: UInt): UInt = {
    secureLogic._2 match {
      case Some(nspass: Bool) => Mux(nspass, rdbits, bi.secFailDefaultBits)
      case None               => rdbits
    }
  }

  def getGrp = Option(grp).getOrElse(GrpTag(0, ""))

  def endaddr = addr + size - bi.wordAddressInc
  def getfieldPtr = fieldPtr

  def getDoc(): String = doc
  def getName(): String = _name
  def upperName(): String = _name.toUpperCase()
  def lowerName(): String = _name.toLowerCase()
  def getAddr(): BigInt = addr
  def getSize(): BigInt = size
  val regType: String = "REG"

  val hitDoRead: Bool
  val hitDoWrite: Bool
  def getFields(): List[Field] = fields.toList
  def readValid(): Bool = RegNext(hitDoRead, init = false.B)
  def readBits(): UInt
  val reuseTag: ReuseTag = bi.getCurrentBlockTag

  override def toString: String = s"${regType}($name, 0x${addr.toString(16)}, 0x${size.toString(16)})"

  // users to rewrite rdata to replace the original register read back signal
  var updateReadBits: UInt = null
  protected def rdata(): UInt = Option(updateReadBits).getOrElse(readBits())

  def fieldNA(pos: Int, bit: Int): Unit = {
    val section: Range = pos to pos + bit - 1
    fields += Field("--", 0.U(bit.W), section, AccessType.NA, 0, Rerror, "reserved")
    fieldPtr += bit
  }

  def fieldNA(bit: Int): Unit = fieldNA(fieldPtr, bit)

  def checkLast = {
    val spareNumbers = if (fields.isEmpty) bi.busDataWidth else bi.busDataWidth - 1 - fields.last.tailBitPos
    spareNumbers match {
      case x if x > 0 => fieldNA(fieldPtr, x)
      case x if x < 0 =>
        throw new Exception(s"Range ${Section(fields.last.section)} exceed Bus width ${bi.busDataWidth}")
      case _ =>
    }
  }

  def allIsNA: Boolean = {
    checkLast
    fields.map(_.accType == AccessType.NA).foldLeft(true)(_ && _)
  }

  /** 在 busif.finish() 的 when(askRead) 上下文里调用，自带地址命中包裹 */
  def readGenerator(): Unit
  def wrErrorGenerator(): Unit = {
    when(bi.writeAddress() === addr.U) {
      bi.reg_wrerr := wrSecureError()
    }
  }
}

case class RegSliceGrp(baseAddr: BigInt, maxSize: BigInt, doc: String, sec: Secure, grp: GrpTag)(val bi: BusIf) {
  private val slices = ListBuffer[RegSlice]()
  def len: Int = slices.length

  def check(offset: BigInt) = {
    if (offset < 0 || offset >= maxSize) {
      throw new Exception(s"Offset $offset is out of range [0, $maxSize)")
    }
  }

  def newReg(doc: String)(implicit symbol: SymbolName): RegInst = {
    val ret = bi.newReg(doc, sec, grp)(symbol)
    slices += ret
    ret
  }

  def newRegAt(offset: BigInt, doc: String)(implicit symbol: SymbolName): RegInst = {
    check(offset)
    val ret = bi.newRegAt(baseAddr + offset, doc, sec, grp)(symbol)
    slices += ret
    ret
  }

  def newFifo(doc: String)(implicit symbol: SymbolName): WrFifoInst = {
    val ret = bi.newWrFifo(doc, sec, grp)(symbol)
    slices += ret
    ret
  }

  def newFifoAt(offset: BigInt, doc: String)(implicit symbol: SymbolName) = {
    check(offset)
    val ret = bi.newWrFifoAt(baseAddr + offset, doc, sec, grp)(symbol)
    slices += ret
    ret
  }

  def newRAM(size: BigInt, doc: String)(implicit symbol: SymbolName) = {
    val ret = bi.newRAM(size, doc, sec, grp)(symbol)
    slices += ret
    ret
  }

  def newRAMAt(offset: BigInt, size: BigInt, doc: String)(implicit symbol: SymbolName) = {
    check(offset + size)
    val ret = bi.newRAMAt(baseAddr + offset, size, doc, sec, grp)(symbol)
    slices += ret
    ret
  }
}
