/******************************************************************************
  * This file describes the AXI4-Lite interface（SpinalHDL 移植版）
  *
  * Interface :
  *   _______________________________________________________________________
  *  | Global  | Write Addr | Write Data | Write Rsp | Read Addr | Read Data |
  *  |   -     |    aw      |     w      |     b     |    ar     |     r     |
  *  |----------------------- -----------------------------------------------|
  *  | aclk    | awvalid    | wvalid     | bvalid    | arvalid   | rvalid    |
  *  | arstn   | awready    | wready     | bready    | arready   | rready    |
  *  |         | awaddr     | wdata      | bresp     | araddr    | rdata     |
  *  |         | awprot     | wstrb      |           | arprot    | rresp     |
  *  |_________|____________|____________|___________|___________|___________|
  *
  * 与 SpinalHDL 的差异：
  *   - `Bits` → `UInt`；总线 Bundle 按 master 视角声明方向
  *     （aw/w/ar 正向，b/r 为 slave 反向），slave 端用 `slave(AxiLite4(...))`。
  *   - 派生总线（pipelined）返回 `Wire(...)`。
  *   - 未移植：AxiLite4SpecRenamer（Chisel 命名机制不同）、
  *     AxiLite4SlaveFactory（依赖 SlaveFactory 框架）、AxiLite4SimpleReadDma。
  */

import chisel3._
import chisel3.util._

/**
  * Definition of the constants used by the AXI Lite bus
  */
object AxiLite4 {

  def apply(addressWidth: Int, dataWidth: Int) = new AxiLite4(AxiLite4Config(
    addressWidth = addressWidth,
    dataWidth = dataWidth
  ))

  /**
    * Read-write response
    */
  object resp {
    def apply() = UInt(2.W)
    def OKAY   = "b00".U(2.W) // Normal access success
    def EXOKAY = "b01".U(2.W) // Exclusive access okay
    def SLVERR = "b10".U(2.W) // Slave error
    def DECERR = "b11".U(2.W) // Decode error
  }

  /**
    * Access permissions
    */
  object prot {
    def apply() = UInt(3.W)
    def UNPRIVILEGED_ACCESS = "b000".U(3.W)
    def PRIVILEGED_ACCESS   = "b001".U(3.W)
    def SECURE_ACCESS       = "b000".U(3.W)
    def NON_SECURE_ACCESS   = "b010".U(3.W)
    def DATA_ACCESS         = "b000".U(3.W)
    def INSTRUCTION_ACCESS  = "b100".U(3.W)
  }
}


/**
  * Configuration class for the Axi Lite bus
  * @param addressWidth Width of the address bus
  * @param dataWidth    Width of the data bus
  */
case class AxiLite4Config(addressWidth : Int,
                          dataWidth    : Int,

                          readIssuingCapability     : Int = -1,
                          writeIssuingCapability    : Int = -1,
                          combinedIssuingCapability : Int = -1,
                          readDataReorderingDepth   : Int = -1) {
  def bytePerWord = dataWidth / 8
  def addressType = UInt(addressWidth.W)
  def dataType = UInt(dataWidth.W)

  require(dataWidth == 32 || dataWidth == 64, "Data width must be 32 or 64")
}


/**
  * Definition of the Write/Read address channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4Ax(config: AxiLite4Config) extends Bundle {
  val addr = UInt(config.addressWidth.W)
  val prot = UInt(3.W)

  import AxiLite4.prot._

  def setUnprivileged(): Unit = prot := UNPRIVILEGED_ACCESS | SECURE_ACCESS | DATA_ACCESS
  def setPermissions(permission: UInt): Unit = prot := permission
}


/**
  * Definition of the Write data channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4W(config: AxiLite4Config) extends Bundle {
  val data = UInt(config.dataWidth.W)
  val strb = UInt((config.dataWidth / 8).W)

  def setStrb(): Unit = strb := ((BigInt(1) << strb.getWidth) - 1).U
  def setStrb(bytesLane: UInt): Unit = strb := bytesLane
}


/**
  * Definition of the Write response channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4B(config: AxiLite4Config) extends Bundle {
  val resp = UInt(2.W)

  import AxiLite4.resp._

  def setOKAY():   Unit = resp := OKAY
  def setEXOKAY(): Unit = resp := EXOKAY
  def setSLVERR(): Unit = resp := SLVERR
  def setDECERR(): Unit = resp := DECERR
  def isOKAY():   Bool = resp === OKAY
  def isEXOKAY(): Bool = resp === EXOKAY
  def isSLVERR(): Bool = resp === SLVERR
  def isDECERR(): Bool = resp === DECERR
}

/** Companion object to create hard-wired AXI responses. */
object AxiLite4B {
  def okay(config: AxiLite4Config) = { val resp = Wire(new AxiLite4B(config)); resp.setOKAY(); resp }
  def exclusiveOkay(config: AxiLite4Config) = { val resp = Wire(new AxiLite4B(config)); resp.setEXOKAY(); resp }
  def slaveError(config: AxiLite4Config) = { val resp = Wire(new AxiLite4B(config)); resp.setSLVERR(); resp }
  def decodeError(config: AxiLite4Config) = { val resp = Wire(new AxiLite4B(config)); resp.setDECERR(); resp }
}


/**
  * Definition of the Read data channel
  * @param config Axi Lite configuration class
  */
case class AxiLite4R(config: AxiLite4Config) extends Bundle {
  val data = UInt(config.dataWidth.W)
  val resp = UInt(2.W)

  import AxiLite4.resp._

  def setOKAY():   Unit = resp := OKAY
  def setEXOKAY(): Unit = resp := EXOKAY
  def setSLVERR(): Unit = resp := SLVERR
  def setDECERR(): Unit = resp := DECERR
  def isOKAY():   Bool = resp === OKAY
  def isEXOKAY(): Bool = resp === EXOKAY
  def isSLVERR(): Bool = resp === SLVERR
  def isDECERR(): Bool = resp === DECERR
}


/**
  * Axi Lite interface definition
  * @param config Axi Lite configuration class
  */
case class AxiLite4(config: AxiLite4Config) extends Bundle with IMasterSlave {

  val aw = master(Stream(AxiLite4Ax(config)))
  val w  = master(Stream(AxiLite4W(config)))
  val b  = slave(Stream(AxiLite4B(config)))
  val ar = master(Stream(AxiLite4Ax(config)))
  val r  = slave(Stream(AxiLite4R(config)))

  //Because aw w b ar r are ... very lazy
  def writeCmd  = aw
  def writeData = w
  def writeRsp  = b
  def readCmd   = ar
  def readRsp   = r


  def >>(that: AxiLite4): Unit = {
    require(that.config == this.config)
    this.writeCmd  >> that.writeCmd
    this.writeData >> that.writeData
    this.writeRsp  << that.writeRsp

    this.readCmd >> that.readCmd
    this.readRsp << that.readRsp
  }

  def <<(that: AxiLite4): Unit = that >> this

  def >>(that: AxiLite4WriteOnly): Unit = {
    require(that.config == this.config)
    this.writeCmd  >> that.writeCmd
    this.writeData >> that.writeData
    this.writeRsp  << that.writeRsp
  }

  def <<(that: AxiLite4WriteOnly): Unit = that >> this

  def >>(that: AxiLite4ReadOnly): Unit = {
    require(that.config == this.config)
    this.readCmd >> that.readCmd
    this.readRsp << that.readRsp
  }

  def <<(that: AxiLite4ReadOnly): Unit = that >> this

  def setIdle(): this.type = {
    this.writeCmd.setIdle()
    this.writeData.setIdle()
    this.writeRsp.setBlocked()
    this.readCmd.setIdle()
    this.readRsp.setBlocked()
    this
  }

  def setBlocked(): this.type = {
    this.writeCmd.setBlocked()
    this.writeData.setBlocked()
    this.writeRsp.setIdle()
    this.readCmd.setBlocked()
    this.readRsp.setIdle()
    this
  }

  def pipelined(
      aw: StreamPipe = StreamPipe.NONE,
      w: StreamPipe = StreamPipe.NONE,
      b: StreamPipe = StreamPipe.NONE,
      ar: StreamPipe = StreamPipe.NONE,
      r: StreamPipe = StreamPipe.NONE
  ): AxiLite4 = {
    val ret = Wire(AxiLite4(config))
    ret.aw << this.aw.pipelined(aw)
    ret.w << this.w.pipelined(w)
    ret.b.pipelined(b) >> this.b
    ret.ar << this.ar.pipelined(ar)
    ret.r.pipelined(r) >> this.r
    ret
  }
}
