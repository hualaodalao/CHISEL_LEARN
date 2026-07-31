import chisel3._

/** SpinalHDL Axi4ReadOnly 的 Chisel 移植版（formal API 未移植）。 */
case class Axi4ReadOnly(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus {
  val ar = master(Stream(Axi4Ar(config)))
  val r  = slave(Stream(Axi4R(config)))

  def readCmd = ar
  def readRsp = r

  def <<(that: Axi4): Unit = that >> this
  def >>(that: Axi4): Unit = {
    this.readCmd drive that.readCmd
    that.readRsp drive this.readRsp
  }

  def <<(that: Axi4ReadOnly): Unit = that >> this
  def >>(that: Axi4ReadOnly): Unit = {
    this.readCmd drive that.readCmd
    that.readRsp drive this.readRsp
  }

  def arValidPipe(): Axi4ReadOnly = {
    val sink = Wire(Axi4ReadOnly(config))
    sink.ar << this.ar.validPipe()
    sink.r  >> this.r
    sink
  }

  def setIdle(): this.type = {
    this.readCmd.setIdle()
    this.readRsp.setBlocked()
    this
  }

  def setBlocked(): this.type = {
    this.readCmd.setBlocked()
    this.readRsp.setIdle()
    this
  }

  def toAxi4(): Axi4 = {
    val ret = Wire(Axi4(config))
    this >> ret

    ret.writeCmd.setIdle()
    ret.writeData.setIdle()
    ret.writeRsp.setBlocked()

    ret
  }

  def toFullConfig(): Axi4ReadOnly = {
    val ret = Wire(Axi4ReadOnly(config.toFullConfig()))
    ret << this
    ret
  }

  def pipelined(
      ar: StreamPipe = StreamPipe.NONE,
      r: StreamPipe = StreamPipe.NONE
  ): Axi4ReadOnly = {
    val ret = Wire(Axi4ReadOnly(config))
    ret.ar << this.ar.pipelined(ar)
    ret.r.pipelined(r) >> this.r
    ret
  }
}
