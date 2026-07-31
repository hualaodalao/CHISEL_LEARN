/** SpinalHDL spinal.lib.bus.regif.FifoInst / WrFifoInst / RdFifoInst 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - Flow/Stream Bundle 以 Wire 形式创建（WrFifo 的 bus 由本类驱动，
  *     RdFifo 的 bus.valid/payload 由用户驱动）。
  *   - RdFifoInst.hitDoRead 修正源码疑似 bug：`bi.writeAddress === addr && doRead`
  *     → 改用 readAddress（AXI 类总线读写地址通道分离，源码写法在 APB 下恰好等价）。
  */

import chisel3._
import chisel3.util._

abstract class FifoInst(name: String, addr: BigInt, doc: String, sec: Secure = null, grp: GrpTag = null)(bi: BusIf)
    extends RegSlice(name, addr, doc, size = bi.wordAddressInc, sec = sec, grp = grp)(bi) {
  override val regType: String = "FIFO"

  val hitDoRead: Bool
  val hitDoWrite: Bool

  def field(bit: Int, doc: String = "")(name: String): Unit = {
    val section: Range = fieldPtr to fieldPtr + bit - 1
    val acctype = regType match {
      case "rFIFO" => AccessType.RO
      case "wFIFO" => AccessType.WO
      case _       => AccessType.WO
    }
    fields += Field(name, 0.U(bit.W), section, acctype, 0, Rerror, doc)
    fieldPtr += bit
  }

  def NA(bit: Int): Unit = {
    val section: Range = fieldPtr to fieldPtr + bit - 1
    fields += Field("reserved", 0.U(bit.W), section, AccessType.NA, 0, Rerror, "N/A")
    fieldPtr += bit
  }

  def fieldAt(pos: Int, bit: Int, doc: String = "")(name: String): Unit = {
    val sectionNext: Range = pos to pos + bit - 1
    val sectionExists: Range = 0 to fieldPtr
    pos match {
      case x if x < fieldPtr =>
        throw new Exception(
          s"next field section ${Section(sectionNext)} overlap to allocated Section ${Section(sectionExists)}"
        )
      case _ if sectionNext.max >= bi.busDataWidth =>
        throw new Exception(s"Range ${Section(sectionNext)} exceed Bus width ${bi.busDataWidth}")
      case x if (x == fieldPtr) => field(bit, doc)(name)
      case _ => {
        NA(pos - fieldPtr)
        field(bit, doc)(name)
      }
    }
    fieldPtr = pos + bit
  }
}

class WrFifoInst(name: String, addr: BigInt, doc: String, sec: Secure, grp: GrpTag = null)(bi: BusIf)
    extends FifoInst(name, addr, doc, sec, grp)(bi) {
  override val regType: String = "wFIFO"

  val hitDoRead: Bool = false.B
  val hitDoWrite: Bool = wrSecurePassage(bi.writeAddress() === addr.U && bi.doWrite)
    .suggestName(f"write_hit_0x$addr%04x")

  val bus = Wire(Flow(UInt(bi.busDataWidth.W))).suggestName(s"${name}_wrfifo")
  bus.valid := hitDoWrite
  bus.payload := bi.writeData

  override def readBits(): UInt = bi.defaultReadBits

  override def readGenerator(): Unit = {
    when(bi.readAddress() === addr.U) {
      bi.reg_rdata := rdSecurePassage(this.rdata())
      bi.reg_rderr := true.B
    }
  }
}

class RdFifoInst(name: String, addr: BigInt, doc: String, sec: Secure, grp: GrpTag = null)(bi: BusIf)
    extends FifoInst(name, addr, doc, sec, grp)(bi) {
  override val regType: String = "rFIFO"

  /** 用户驱动 bus.valid / bus.payload */
  val bus = Wire(Stream(UInt(bi.busDataWidth.W))).suggestName(s"${name}_rdfifo")

  val hitDoRead: Bool = rdSecurePassage(bi.readAddress() === addr.U && bi.doRead)
    .suggestName(f"read_hit_0x$addr%04x")
  val hitDoWrite: Bool = false.B

  bus.ready := hitDoRead

  override def readBits(): UInt = bus.payload

  override def readGenerator(): Unit = {
    when(bi.readAddress() === addr.U) {
      bi.reg_rdata := rdSecurePassage(this.rdata())
      bi.reg_rderr := !bus.valid
    }
  }
}
