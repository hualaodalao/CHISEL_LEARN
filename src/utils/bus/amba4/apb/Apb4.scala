/** SpinalHDL spinal.lib.bus.amba4.apb.Apb4 的 Chisel 移植版。
  *
  * AMBA 4 APB 协议接口。
  *
  * 与 SpinalHDL 的差异：
  *   - 可选信号 `if(...) X else null` → `Option[X]`；`Bits` → `UInt`。
  *   - Bundle 按 master 视角声明方向，slave 端用 `slave(Apb4(...))` = `Flipped`。
  *   - 未移植：`apply(Apb3Config)` 重载（amba3/apb 未移植）、
  *     Apb4SlaveFactory（依赖 SpinalHDL 总线 SlaveFactory 框架）、
  *     Apb4CCToggle 跨时钟域桥。
  */

import chisel3._
import chisel3.util._

case class Apb4Config(addressWidth  : Int,
                      dataWidth     : Int,
                      selWidth      : Int = 1,
                      useStrb       : Boolean = true,
                      useSlaveError : Boolean = true) {
  def strbWidth: Int = dataWidth >> 3
}

object Apb4 {
  def apply(addressWidth: Int, dataWidth: Int) =
    new Apb4(Apb4Config(addressWidth = addressWidth, dataWidth = dataWidth))
}

/**
  * AMBA 4 APB Protocol interface
  */
case class Apb4(c: Apb4Config) extends Bundle with IMasterSlave {
  val PADDR   = Output(UInt(c.addressWidth.W))
  val PSEL    = Output(UInt(c.selWidth.W))
  val PENABLE = Output(Bool())
  val PREADY  = Input(Bool())
  val PWRITE  = Output(Bool())
  val PSTRB   = Output(UInt(c.strbWidth.W))
  val PPROT   = Output(UInt(3.W))
  val PWDATA  = Output(UInt(c.dataWidth.W))
  val PRDATA  = Input(UInt(c.dataWidth.W))
  val PSLVERR = if (c.useSlaveError) Some(Input(Bool())) else None

  def <<(that: Apb4): Unit = that >> this

  def >>(that: Apb4): Unit = {
    require(this.c.selWidth == that.c.selWidth, "APB4 mismatch sel width")

    that.PADDR   := Apb4Priv.resized(this.PADDR, that.c.addressWidth)
    that.PSEL    := this.PSEL
    that.PENABLE := this.PENABLE
    this.PREADY  := that.PREADY
    that.PWRITE  := this.PWRITE
    that.PWDATA  := this.PWDATA
    that.PSTRB   := this.PSTRB
    that.PPROT   := this.PPROT
    this.PRDATA  := that.PRDATA

    for (s <- this.PSLVERR)
      s := that.PSLVERR.getOrElse(false.B)
  }

  /** 命令方向打一拍（PSEL/PENABLE 按 APB 时序调整） */
  def m2sPipe(): Apb4 = {
    val that = Wire(Apb4(c))
    that.PADDR   := RegNext(this.PADDR)
    that.PWRITE  := RegNext(this.PWRITE)
    that.PPROT   := RegNext(this.PPROT)
    that.PSTRB   := RegNext(this.PSTRB)
    that.PWDATA  := RegNext(this.PWDATA)
    that.PSEL    := RegNext(Mux(this.PREADY, 0.U, this.PSEL))
    that.PENABLE := RegNext(this.PENABLE && !this.PREADY)
    this.PRDATA  := that.PRDATA
    this.PREADY  := that.PREADY && that.PENABLE
    for (s <- this.PSLVERR) s := that.PSLVERR.get
    that
  }
}

/** 内部工具：截断或补零到指定宽度 */
object Apb4Priv {
  def resized(value: UInt, width: Int): UInt = {
    if (width == 0) 0.U(0.W)
    else if (value.getWidth >= width) value(width - 1, 0)
    else value.pad(width)
  }
}
