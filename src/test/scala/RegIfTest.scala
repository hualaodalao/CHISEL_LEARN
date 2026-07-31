import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import AccessType._
import RegIfIntr._

/** APB4 寄存器接口：常见访问类型 + RAM 窗口 */
class Apb4RegIfDemo extends Module {
  val io = IO(new Bundle {
    val apb = slave(Apb4(addressWidth = 16, dataWidth = 32))
    val stat = Input(UInt(8.W))
    val pulse = Output(Bool())
    val cfg = Output(UInt(16.W))
  })
  val busif = Apb4BusInterface(io.apb, SizeMapping(0x0, 0x1000))

  val REG0 = busif.newReg("ctrl reg")
  val enable = REG0.field(Bool(), RW, 0, "enable bit")
  val mode = REG0.field(UInt(4.W), RW, 0x3, "mode select")
  val kick = REG0.field(Bool(), W1P, 0, "kick pulse")

  val REG1 = busif.newReg("status reg")
  val status = REG1.field(UInt(8.W), RO, "hw status")
  val version = REG1.field(UInt(8.W), ROV, 0x5a, "version const")
  val err = REG1.field(Bool(), W1C, 0, "error flag, write 1 clear")

  val REG2 = busif.newRegAt(0x40, "config reg")
  val cfg = REG2.field(UInt(16.W), RW, 0xabcdL, "config value")

  val RAM0 = busif.newRAMAt(0x100, 0x100, "buffer ram")
  val mem = SyncReadMem(0x100 / 4, UInt(32.W))
  when(RAM0.bus.ce && RAM0.bus.wr) { mem.write(RAM0.bus.addr, RAM0.bus.wdat) }
  RAM0.bus.rdat := mem.read(RAM0.bus.addr, RAM0.bus.ce && !RAM0.bus.wr)

  busif.finish()

  status := io.stat
  io.pulse := kick
  io.cfg := cfg
  dontTouch(enable)
  dontTouch(mode)
  dontTouch(err)
}

/** AXI4-Lite 寄存器接口 */
class AxiLite4RegIfDemo extends Module {
  val io = IO(new Bundle {
    val axi = slave(AxiLite4(addressWidth = 16, dataWidth = 32))
    val gpo = Output(UInt(32.W))
    val gpi = Input(UInt(32.W))
  })
  val busif = AxiLite4BusInterface(io.axi, SizeMapping(0x0, 0x1000))

  val REG0 = busif.newReg("gpio out")
  val gpo = REG0.field(UInt(32.W), RW, 0, "gpio output")

  val REG1 = busif.newReg("gpio in")
  val gpi = REG1.field(UInt(32.W), RO, "gpio input")

  busif.finish()

  gpi := io.gpi
  io.gpo := gpo
}

/** 中断工厂：RAW/FORCE/MASK/STATUS 四寄存器 */
class RegIfIntrDemo extends Module {
  val io = IO(new Bundle {
    val apb = slave(Apb4(addressWidth = 16, dataWidth = 32))
    val txDone = Input(Bool())
    val rxDone = Input(Bool())
    val intr = Output(Bool())
  })
  val busif = Apb4BusInterface(io.apb, SizeMapping(0x0, 0x1000))
  io.intr := busif.interruptFactory("UART", io.txDone, io.rxDone)
  busif.finish()
}

/** RegSC 软件置位/清零 + W1S/W1C mask 电平中断工厂 + 全套文档生成 */
class RegIfDocDemo extends Module {
  val io = IO(new Bundle {
    val apb = slave(Apb4(addressWidth = 16, dataWidth = 32))
    val lvA = Input(Bool())
    val lvB = Input(Bool())
    val intr = Output(Bool())
  })
  val busif = Apb4BusInterface(io.apb, SizeMapping(0x0, 0x1000))

  val REG0 = busif.newReg("ctrl reg")
  val en = REG0.field(Bool(), RW, 0, "enable")

  val SC = busif.newRegSC("soft ctrl")
  val swEn = SC.field(Bool(), 0, "soft enable")

  io.intr := busif.interruptLevel_W1SCmask_FactoryAt(0x100, "SYS", io.lvA, io.lvB)

  busif.finish()
  busif.setDocPath("generated/regif-doc")
  busif.accept(DocHtml("DocDemo"))
  busif.accept(DocJson("DocDemo"))
  busif.accept(DocCHeader("DocDemo", "TOP"))
  busif.accept(DocSVHeader("DocDemo", "TOP"))
  busif.accept(DocRalf("DocDemo"))
  busif.accept(DocSystemRdl("DocDemo"))

  dontTouch(en)
  dontTouch(swEn)
}

class RegIfTest extends AnyFlatSpec with Matchers {
  behavior of "regif (SpinalHDL port)"

  it should "elaborate Apb4 regif with RW/W1P/RO/ROV/W1C fields and RAM" in {
    val v = ChiselStage.emitSystemVerilog(new Apb4RegIfDemo)
    v should include("module Apb4RegIfDemo")
    v should include("apb_PRDATA")
    v should include("apb_PREADY")
  }

  it should "elaborate AxiLite4 regif" in {
    val v = ChiselStage.emitSystemVerilog(new AxiLite4RegIfDemo)
    v should include("module AxiLite4RegIfDemo")
    v should include("axi_r_payload_data")
    v should include("axi_b_payload_resp")
  }

  it should "elaborate interruptFactory with RAW/FORCE/MASK/STATUS" in {
    val v = ChiselStage.emitSystemVerilog(new RegIfIntrDemo)
    v should include("module RegIfIntrDemo")
    v should include("intr")
  }

  it should "elaborate RegSC and W1SCmask level interrupt factory, and dump docs" in {
    val v = ChiselStage.emitSystemVerilog(new RegIfDocDemo)
    v should include("module RegIfDocDemo")
    v should include("intr")

    def read(suffix: String): String = {
      val f = new java.io.File(s"generated/regif-doc/DocDemo.${suffix}")
      assert(f.exists(), s"${f.getPath} should be generated")
      val src = scala.io.Source.fromFile(f)
      try src.mkString finally src.close()
    }
    read("html") should include("DocDemo Register Interface")
    read("json") should include("\"system\"     : \"DocDemo\"")
    read("h") should include("#ifndef __DOCDEMO_REGIF_H__")
    read("svh") should include("`ifndef DOCDEMO_REGIF_H")
    read("ralf") should include("block DocDemo {")
    read("rdl") should include("addrmap DocDemo {")
  }

  it should "reject overlapping register addresses at elaboration" in {
    an[Exception] should be thrownBy ChiselStage.emitSystemVerilog(new Module {
      val io = IO(new Bundle { val apb = slave(Apb4(addressWidth = 16, dataWidth = 32)) })
      val busif = Apb4BusInterface(io.apb, SizeMapping(0x0, 0x1000))
      val REG0 = busif.newRegAt(0x10, "reg a")
      REG0.field(UInt(32.W), RW, 0, "fa")
      val REG1 = busif.newRegAt(0x10, "reg b")
      REG1.field(UInt(32.W), RW, 0, "fb")
      busif.finish()
    })
  }
}
