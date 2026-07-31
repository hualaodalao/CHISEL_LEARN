/** SpinalHDL spinal.lib.bus.amba4.axilite.AxiLite4ReadOnly 的 Chisel 移植版。 */

import chisel3._

case class AxiLite4ReadOnly(config: AxiLite4Config) extends Bundle with IMasterSlave {
  val ar = master(Stream(AxiLite4Ax(config)))
  val r  = slave(Stream(AxiLite4R(config)))

  def readCmd = ar
  def readRsp = r

  def >>(that: AxiLite4): Unit = {
    require(that.config == this.config)
    this.readCmd >> that.readCmd
    this.readRsp << that.readRsp
  }

  def <<(that: AxiLite4): Unit = that >> this

  def >>(that: AxiLite4ReadOnly): Unit = {
    require(that.config == this.config)
    this.readCmd >> that.readCmd
    this.readRsp << that.readRsp
  }

  def <<(that: AxiLite4ReadOnly): Unit = that >> this
}
