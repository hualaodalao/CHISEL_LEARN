/** SpinalHDL spinal.lib.bus.regif.RamInst 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - localbus.MemBus → 自定义 RegIfMemBus（仅 regif RAM 对接用），
  *     以 Wire 形式创建：ce/wr/addr/wdat 由 RamInst 驱动，rdat 由用户驱动
  *     （要求 1 拍读延迟，与 ram_rdvalid 对齐）。
  */

import chisel3._
import chisel3.util._

/** 对应 SpinalHDL localbus.MemBus 的极简替身 */
class RegIfMemBus(val aw: Int, val dw: Int) extends Bundle {
  val ce = Bool()
  val wr = Bool()
  val addr = UInt(aw.W)
  val wdat = UInt(dw.W)
  val rdat = UInt(dw.W) // 用户驱动：RAM 读数据（相对 ce&&!wr 延迟 1 拍）
}

class RamInst(name: String, addr: BigInt, size: BigInt, doc: String, sec: Secure, grp: GrpTag = null)(bi: BusIf)
    extends RegSlice(name, addr, doc, size, sec, grp)(bi) {
  require(size >= bi.wordAddressInc, "byte Size must be >= busWidth Byte")
  override val regType: String = "RAM"

  val _hitRAddr = (bi.readAddress() <= endaddr.U) && (bi.readAddress() >= addr.U)
  val hitDoRead: Bool = rdSecurePassage(_hitRAddr && bi.askRead)
    .suggestName(f"ram_read_hit_0x$endaddr%04x_0x$addr%04x")
  val _hitWAddr = (bi.writeAddress() <= endaddr.U) && (bi.writeAddress() >= addr.U)
  val hitDoWrite: Bool = wrSecurePassage(_hitWAddr && bi.doWrite)
    .suggestName(f"ram_write_hit_0x$endaddr%04x_0x$addr%04x")

  val bus = Wire(new RegIfMemBus(aw = log2Ceil(size / bi.wordAddressInc), dw = bi.busDataWidth))
    .suggestName(s"${name}_mbus")
  bus.ce := hitDoRead || hitDoWrite
  bus.wr := hitDoWrite
  bus.addr := ((Mux(hitDoRead, bi.readAddress(), bi.writeAddress()) - addr.U) >> bi.underbitWidth)
    .asTypeOf(bus.addr)
  bus.wdat := bi.writeData
  bus.rdat := DontCare // 期望由用户驱动

  val ram_rdvalid = RegNext(hitDoRead, init = false.B).suggestName(s"${name}_ram_rdvalid")

  override def readBits(): UInt = bus.rdat

  def field(bit: Int, doc: String = "")(name: String): Unit = {
    val section: Range = fieldPtr to fieldPtr + bit - 1
    fields += Field(name, 0.U(bit.W), section, AccessType.RW, 0, Rerror, doc)
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
        fieldNA(pos - fieldPtr)
        field(bit, doc)(name)
      }
    }
    fieldPtr = pos + bit
  }

  // RAM 的读通路在 BusIf.readGenerator 中特判处理（ram_rdvalid 时覆盖 bus_rdata）
  override def readGenerator(): Unit = {}

  override def wrErrorGenerator(): Unit = {
    when(_hitWAddr && bi.doWrite) {
      bi.reg_wrerr := wrSecureError()
    }
  }
}
