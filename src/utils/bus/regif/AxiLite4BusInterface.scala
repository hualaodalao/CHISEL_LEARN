/** SpinalHDL spinal.lib.bus.regif.AxiLite4BusInterface 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - implicit ClassName 宏 → implicit SymbolName（取调用点 val 名，仅用于文档命名）。
  *   - `withStrb generate(...)` → Option / if；clearWhen/setWhen → 两条 when（set 在后获胜）。
  *   - doRead/doWrite 用 WireDefault 定义，readHalt/writeHalt 以 last-connect 覆盖为 false。
  *   - 记得在所有寄存器定义完成后调用 busif.finish()。
  */

import chisel3._
import chisel3.util._

case class AxiLite4BusInterface(bus: AxiLite4, sizeMap: SizeMapping, regPre: String = "",
                                withSecFireWall: Boolean = false)(implicit moduleName: SymbolName)
    extends BusIf {
  override val busDataWidth: Int = bus.config.dataWidth
  override val busAddrWidth: Int = bus.config.addressWidth
  override val withStrb: Boolean = true
  override def getModuleName = moduleName.name

  lazy val reg_wrerr: Bool = RegInit(false.B)
  val bus_rdata: UInt = Wire(UInt(busDataWidth.W))
  val reg_rderr: Bool = RegInit(false.B)
  val reg_rdata: UInt = RegInit(defaultReadBits)

  val axiAr = bus.readCmd.stage()
  val axiR = Wire(Stream(AxiLite4R(bus.config)))
  val axiRValid = RegInit(false.B)

  val axiAw = bus.writeCmd.stage()
  val axiW = bus.writeData.stage()
  val axiB = Wire(Stream(AxiLite4B(bus.config)))
  val axiBValid = RegInit(false.B)

  // regif 的 RC/RS/W1RC/W0RC 允许读时使用 strb（APB4 可行），
  // 但 AXI 的 strb 仅存在于写通道，读命中时强制全 1
  val wstrb: Option[UInt] = Some(Wire(UInt(strbWidth.W)))
  val wmask: UInt = Wire(UInt(busDataWidth.W))
  val wmaskn: UInt = Wire(UInt(busDataWidth.W))
  wstrb.get := Mux(axiAr.valid, ((BigInt(1) << strbWidth) - 1).U(strbWidth.W), axiW.payload.strb)
  initStrbMasks()

  val bus_err: Bool = this.bus_slverr

  when(bus_err) {
    axiR.payload.setSLVERR()
  }.otherwise {
    axiR.payload.setOKAY()
  }
  axiR.valid := axiRValid
  axiR.payload.data := bus_rdata

  axiB.payload.setOKAY()
  axiB.valid := axiBValid

  bus.r << axiR
  bus.b << axiB

  val askWrite = axiAw.valid && axiW.valid
  val askRead = axiAr.valid || (axiR.valid && !axiR.ready)
  val doWrite = WireDefault(askWrite && (!axiB.valid || axiB.ready)) // 假设 xw 与 B 之间有一拍
  val doRead = WireDefault(axiAr.valid && (!axiR.valid || axiR.ready)) // 假设 Ar 与 R 之间有一拍
  val writeData = axiW.payload.data

  override lazy val bus_nsbit: Bool = Mux(askRead, bus.ar.payload.prot(1), bus.aw.payload.prot(1))

  when(axiR.ready) { axiRValid := false.B }
  when(doRead) { axiRValid := true.B }
  axiAr.ready := doRead

  when(axiB.ready) { axiBValid := false.B }
  when(doWrite) { axiBValid := true.B }
  axiAw.ready := doWrite
  axiW.ready := doWrite

  // dataWidth >= 32，故 underbitWidth >= 2，无零宽提取问题
  override def readAddress(): UInt =
    Cat(axiAr.payload.addr(busAddrWidth - 1, underbitWidth), 0.U(underbitWidth.W))
  override def writeAddress(): UInt = axiAw.payload.addr

  override def readHalt(): Unit = doRead := false.B
  override def writeHalt(): Unit = doWrite := false.B
}
