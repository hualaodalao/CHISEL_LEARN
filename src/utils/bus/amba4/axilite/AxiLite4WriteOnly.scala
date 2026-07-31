/** SpinalHDL spinal.lib.bus.amba4.axilite.AxiLite4WriteOnly 的 Chisel 移植版。 */

import chisel3._

case class AxiLite4WriteOnly(config: AxiLite4Config) extends Bundle with IMasterSlave {
  val aw = master(Stream(AxiLite4Ax(config)))
  val w  = master(Stream(AxiLite4W(config)))
  val b  = slave(Stream(AxiLite4B(config)))

  def writeCmd  = aw
  def writeData = w
  def writeRsp  = b

  def >>(that: AxiLite4): Unit = {
    require(that.config == this.config)
    this.writeCmd  >> that.writeCmd
    this.writeData >> that.writeData
    this.writeRsp  << that.writeRsp
  }

  def <<(that: AxiLite4): Unit = that >> this

  def >>(that: AxiLite4WriteOnly): Unit = {
    require(that.config == this.config)
    this.writeCmd  >> that.writeCmd
    this.writeData >> that.writeData
    this.writeRsp  << that.writeRsp
  }

  def <<(that: AxiLite4WriteOnly): Unit = that >> this
}
