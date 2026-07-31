/** SpinalHDL spinal.lib.bus.regif.Apb4BusInterface 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - implicit ClassName 宏 → implicit SymbolName（取调用点 val 名，仅用于文档命名）。
  *   - allowPruning 丢弃；`withStrb generate(...)` → Option / if。
  *   - PREADY 先赋 true，readHalt/writeHalt 以 last-connect 覆盖为 false。
  *   - 记得在所有寄存器定义完成后调用 busif.finish()。
  */

import chisel3._
import chisel3.util._

case class Apb4BusInterface(bus: Apb4, sizeMap: SizeMapping, regPre: String = "",
                            withSecFireWall: Boolean = false)(implicit moduleName: SymbolName)
    extends BusIf {
  override val withStrb: Boolean = bus.c.useStrb
  override val busDataWidth: Int = bus.c.dataWidth
  override val busAddrWidth: Int = bus.c.addressWidth
  override def getModuleName = moduleName.name

  lazy val reg_wrerr: Bool = RegInit(false.B)
  val bus_rdata: UInt = Wire(UInt(busDataWidth.W))
  val reg_rderr: Bool = RegInit(false.B)
  val reg_rdata: UInt = RegInit(defaultReadBits)

  val wstrb: Option[UInt] = if (withStrb) Some(Wire(UInt(strbWidth.W))) else None
  val wmask: UInt = Wire(UInt(busDataWidth.W))
  val wmaskn: UInt = Wire(UInt(busDataWidth.W))
  wstrb.foreach(_ := bus.PSTRB)

  bus.PREADY := true.B
  bus.PRDATA := bus_rdata
  bus.PSLVERR.foreach(_ := bus_slverr)

  val askWrite = bus.PSEL(0) && bus.PWRITE
  val askRead = bus.PSEL(0) && !bus.PWRITE && !bus.PENABLE
  val doWrite = askWrite && bus.PENABLE && bus.PREADY
  val doRead = bus.PSEL(0) && !bus.PWRITE && bus.PENABLE && bus.PREADY
  val writeData = bus.PWDATA
  // 延迟 1 拍用于 W1P 写后自动清零
  override lazy val cg_en: Bool = bus.PSEL(0) || RegNext(bus.PSEL(0), init = false.B)
  override lazy val bus_nsbit: Bool = bus.PPROT(1)

  initStrbMasks()

  override def readAddress(): UInt =
    if (withStrb && underbitWidth > 0)
      Cat(bus.PADDR(busAddrWidth - 1, underbitWidth), 0.U(underbitWidth.W))
    else bus.PADDR
  override def writeAddress(): UInt = bus.PADDR

  override def readHalt(): Unit = bus.PREADY := false.B
  override def writeHalt(): Unit = bus.PREADY := false.B
}
