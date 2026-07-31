import chisel3._

/** SpinalHDL Axi4Shared 的 Chisel 移植版：AR/AW 合并为共享 ARW 通道（write 位区分）。 */
case class Axi4Shared(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus {
  val arw = master(Stream(Axi4Arw(config)))
  val w   = master(Stream(Axi4W(config)))
  val b   = slave(Stream(Axi4B(config)))
  val r   = slave(Stream(Axi4R(config)))

  def sharedCmd = arw
  def writeData = w
  def writeRsp  = b
  def readRsp   = r

  def <<(that: Axi4Shared): Unit = that >> this
  def >>(that: Axi4Shared): Unit = {
    this.sharedCmd drive that.sharedCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
    that.readRsp drive this.readRsp
  }

  def arwValidPipe(): Axi4Shared = {
    val sink = Wire(Axi4Shared(config))
    sink.arw << this.arw.validPipe()
    sink.w  << this.w
    sink.r  >> this.r
    sink.b  >> this.b
    sink
  }

  def toAxi4(): Axi4 = {
    val ret = Wire(Axi4(config))
    ret.ar.payload.assignSomeByName(this.arw.payload)
    ret.aw.payload.assignSomeByName(this.arw.payload)
    ret.ar.valid := this.arw.valid && !this.arw.payload.write
    ret.aw.valid := this.arw.valid && this.arw.payload.write
    this.arw.ready := Mux(this.arw.payload.write, ret.aw.ready, ret.ar.ready)
    this.w >> ret.w
    this.r << ret.r
    this.b << ret.b
    ret
  }

  def toAxi4ReadOnly(): Axi4ReadOnly = {
    val ret = Wire(Axi4ReadOnly(config))
    ret.ar.payload.assignSomeByName(this.arw.payload)
    ret.ar.valid := this.arw.valid
    this.arw.ready := ret.ar.ready
    this.r << ret.r
    ret
  }

  def toAxi4WriteOnly(): Axi4WriteOnly = {
    val ret = Wire(Axi4WriteOnly(config))
    ret.aw.payload.assignSomeByName(this.arw.payload)
    ret.aw.valid := this.arw.valid && this.arw.payload.write
    this.arw.ready := ret.aw.ready
    this.w >> ret.w
    this.b << ret.b
    ret
  }

  def toFullConfig(): Axi4Shared = {
    val ret = Wire(Axi4Shared(config.toFullConfig()))
    ret << this
    ret
  }

  def pipelined(
      arw: StreamPipe = StreamPipe.NONE,
      w: StreamPipe = StreamPipe.NONE,
      b: StreamPipe = StreamPipe.NONE,
      r: StreamPipe = StreamPipe.NONE
  ): Axi4Shared = {
    val ret = Wire(Axi4Shared(config))
    ret.arw << this.arw.pipelined(arw)
    ret.w << this.w.pipelined(w)
    ret.b.pipelined(b) >> this.b
    ret.r.pipelined(r) >> this.r
    ret
  }
}
