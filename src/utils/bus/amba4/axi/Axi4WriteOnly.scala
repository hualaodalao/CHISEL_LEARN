import chisel3._

/** SpinalHDL Axi4WriteOnly 的 Chisel 移植版（formal API 未移植）。 */
case class Axi4WriteOnly(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus {

  val aw = master(Stream(Axi4Aw(config)))
  val w  = master(Stream(Axi4W(config)))
  val b  = slave(Stream(Axi4B(config)))

  def writeCmd  = aw
  def writeData = w
  def writeRsp  = b

  def <<(that: Axi4): Unit = that >> this
  def >>(that: Axi4): Unit = {
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
  }

  def <<(that: Axi4WriteOnly): Unit = that >> this
  def >>(that: Axi4WriteOnly): Unit = {
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
  }

  def awValidPipe(): Axi4WriteOnly = {
    val sink = Wire(Axi4WriteOnly(config))
    sink.aw << this.aw.validPipe()
    sink.w  << this.w
    sink.b  >> this.b
    sink
  }

  def setIdle(): this.type = {
    this.writeCmd.setIdle()
    this.writeData.setIdle()
    this.writeRsp.setBlocked()
    this
  }

  def setBlocked(): this.type = {
    this.writeCmd.setBlocked()
    this.writeData.setBlocked()
    this.writeRsp.setIdle()
    this
  }

  def toAxi4(): Axi4 = {
    val ret = Wire(Axi4(config))
    this >> ret

    ret.readCmd.setIdle()
    ret.readRsp.setBlocked()

    ret
  }

  def toFullConfig(): Axi4WriteOnly = {
    val ret = Wire(Axi4WriteOnly(config.toFullConfig()))
    ret << this
    ret
  }

  def pipelined(
      aw: StreamPipe = StreamPipe.NONE,
      w: StreamPipe = StreamPipe.NONE,
      b: StreamPipe = StreamPipe.NONE
  ): Axi4WriteOnly = {
    val ret = Wire(Axi4WriteOnly(config))
    ret.aw << this.aw.pipelined(aw)
    ret.w << this.w.pipelined(w)
    ret.b.pipelined(b) >> this.b
    ret
  }
}
