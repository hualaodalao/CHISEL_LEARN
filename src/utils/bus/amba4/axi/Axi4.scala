/******************************************************************************
  *  This file describes the Axi4 interface（SpinalHDL 移植版）
  *
  *   _________________________________________________________________________
  *  | Global | Write Data | Write Addr | Write Resp | Read Addr  | Read Data  |
  *  |   -    |    w       |    aw      |      b     |     ar     |     r      |
  *  |-------------------------------------------------------------------------|
  *  |  aclk  |  wid       |  *awid     |  *bid      |  *arid     |  rid       |
  *  |  arstn |  wdata     |  awaddr    |  *bresp    |  araddr    |  rdata     |
  *  |        |  *wstrb    |  *awlen    |  buser     |  *arlen    |  rresp     |
  *  |        |  wlast     |  *awsize   |  bvalid    |  *arsize   |  rlast     |
  *  |        |  *wuser    |  *awburst  |  bready    |  *arburst  |  *ruser    |
  *  |        |  wvalid    |  *awlock   |            |  *arlock   |  rvalid    |
  *  |        |  wready    |  *awcache  |            |  *arcache  |  rready    |
  *  |        |            |  awprot    |            |  arprot    |            |
  *  |        |            |  *awqos    |            |  *arqos    |            |
  *  |        |            |  *awregion |            |  *arregion |            |
  *  |        |            |  *awuser   |            |  *aruser   |            |
  *  |        |            |  awvalid   |            |  arvalid   |            |
  *  |        |            |  awready   |            |  arready   |            |
  *  |________|____________|____________|____________|____________|____________|
  *   * Optional signal
  *
  * 与 SpinalHDL 的差异：
  *   - 可选信号 `if(...) X else null` → `Option[X]`；`Bits` → `UInt`。
  *   - 总线 Bundle 按 master 视角声明方向（aw/w/ar 正向，b/r 为 slave 反向），
  *     slave 端用 `slave(Axi4(...))` = `Flipped`。
  *   - 派生总线（axValidPipe/toReadOnly/pipelined 等）返回 `Wire(...)`。
  *   - 未移植：formal 验证 API、Axi4SpecRenamer（Chisel 命名机制不同）、
  *     Aligner/Arbiter/CC/Checker/Compactor/Crossbar/Decoder/Downsizer/
  *     ErrorSlave/IdRemover/OnePerId/SharedOnChipRam/SharedToApb3/BRAM/
  *     SlaveFactory/ToAxi3/ToTilelink/Unburster/Upsizer 等组件。
  */

import chisel3._
import chisel3.util._

/**
 * Configuration class for the Axi4 bus
 */
case class Axi4Config(addressWidth : Int,
                      dataWidth    : Int,
                      idWidth      : Int = -1,
                      useId        : Boolean = true,
                      withAxi3     : Boolean = false, //Very limited support
                      useRegion    : Boolean = true,
                      useBurst     : Boolean = true,
                      useLock      : Boolean = true,
                      useCache     : Boolean = true,
                      useSize      : Boolean = true,
                      useQos       : Boolean = true,
                      useLen       : Boolean = true,
                      useLast      : Boolean = true,
                      useResp      : Boolean = true,
                      useProt      : Boolean = true,
                      useStrb      : Boolean = true,
                      useAllStrb   : Boolean = false,
                      forceAxi4Len : Boolean = false,
                      arUserWidth  : Int = -1,
                      awUserWidth  : Int = -1,
                      rUserWidth   : Int = -1,
                      wUserWidth   : Int = -1,
                      bUserWidth   : Int = -1,

                      readIssuingCapability     : Int = -1,
                      writeIssuingCapability    : Int = -1,
                      combinedIssuingCapability : Int = -1,
                      readDataReorderingDepth   : Int = -1) {

  def useWid = withAxi3
  def useArUser = arUserWidth >= 0
  def useAwUser = awUserWidth >= 0
  def useRUser = rUserWidth >= 0
  def useWUser = wUserWidth >= 0
  def useBUser = bUserWidth >= 0
  def useArwUser = arwUserWidth >= 0 //Shared AR/AW channel
  def arwUserWidth = Math.max(arUserWidth, awUserWidth)
  def sizeWidth = if (withAxi3) 4 else 3
  def lenWidth = if (withAxi3 && !forceAxi4Len) 4 else 8
  def lockWidth = if (withAxi3) 2 else 1

  if (useId)
    require(idWidth >= 0, "You need to set idWidth")

  require(combinedIssuingCapability >= scala.math.max(readIssuingCapability, writeIssuingCapability),
    "Inconsistent combined issuing capability")
  require(readDataReorderingDepth <= readIssuingCapability,
    "Inconsistent read data reordering depth")

  require(List(8, 16, 32, 64, 128, 256, 512, 1024) contains dataWidth,
    "Valid data width: 8, 16, 32, 64, 128, 256, 512 or 1024 bit")

  def addressType = UInt(addressWidth.W)
  def dataType = UInt(dataWidth.W)
  def idType = UInt(idWidth.W)
  def lenType = UInt(8.W)
  def bytePerWord = dataWidth / 8
  def symbolRange: Range = (log2Up(bytePerWord) - 1) to 0 by -1
  def wordRange: Range = (addressWidth - 1) to log2Up(bytePerWord) by -1

  def toFullConfig(defaultIdWidth: Int = 1): Axi4Config = {
    this.copy(
      idWidth = if (this.useId) this.idWidth else defaultIdWidth,
      useId = true,
      useRegion = true,
      useBurst = true,
      useLock = true,
      useCache = true,
      useSize = true,
      useQos = true,
      useLen = true,
      useLast = true,
      useResp = true,
      useProt = true,
      useStrb = true
    )
  }
}


trait Axi4Bus

/**
 * Axi4 interface definition
 * @param config Axi4 configuration class
 */
case class Axi4(config: Axi4Config) extends Bundle with IMasterSlave with Axi4Bus {

  val aw = master(Stream(Axi4Aw(config)))
  val w  = master(Stream(Axi4W(config)))
  val b  = slave(Stream(Axi4B(config)))
  val ar = master(Stream(Axi4Ar(config)))
  val r  = slave(Stream(Axi4R(config)))

  def writeCmd  = aw
  def writeData = w
  def writeRsp  = b
  def readCmd   = ar
  def readRsp   = r

  def <<(that: Axi4): Unit = that >> this
  def >>(that: Axi4): Unit = {
    this.readCmd drive that.readCmd
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.readRsp drive this.readRsp
    that.writeRsp drive this.writeRsp
  }

  def <<(that: Axi4WriteOnly): Unit = that >> this
  def >>(that: Axi4WriteOnly): Unit = {
    this.writeCmd drive that.writeCmd
    this.writeData drive that.writeData
    that.writeRsp drive this.writeRsp
  }

  def <<(that: Axi4ReadOnly): Unit = that >> this
  def >>(that: Axi4ReadOnly): Unit = {
    this.readCmd drive that.readCmd
    that.readRsp drive this.readRsp
  }

  def axValidPipe(): Axi4 = {
    val sink = Wire(Axi4(config))
    sink.ar << this.ar.validPipe()
    sink.aw << this.aw.validPipe()
    sink.w  << this.w
    sink.r  >> this.r
    sink.b  >> this.b
    sink
  }

  def toReadOnly(idleOthers: Boolean = false): Axi4ReadOnly = {
    val ret = Wire(Axi4ReadOnly(config))
    ret << this
    if (idleOthers) {
      this.writeCmd.setBlocked()
      this.writeData.setBlocked()
      this.writeRsp.setIdle()
    }
    ret
  }

  def toWriteOnly(idleOthers: Boolean = false): Axi4WriteOnly = {
    val ret = Wire(Axi4WriteOnly(config))
    ret << this
    if (idleOthers) {
      this.readCmd.setBlocked()
      this.readRsp.setIdle()
    }
    ret
  }

  def expendId(idWidth: Int): Axi4 = {
    require(config.idWidth <= idWidth)
    val ret = Wire(Axi4(config.copy(idWidth = idWidth)))
    ret << this
    ret
  }

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

  def toShared(): Axi4Shared = {
    Axi4ToAxi4Shared(this)
  }

  def toFullConfig(): Axi4 = {
    val ret = Wire(Axi4(config.toFullConfig()))
    ret << this
    ret
  }

  def pipelined(
      aw: StreamPipe = StreamPipe.NONE,
      w: StreamPipe = StreamPipe.NONE,
      b: StreamPipe = StreamPipe.NONE,
      ar: StreamPipe = StreamPipe.NONE,
      r: StreamPipe = StreamPipe.NONE
  ): Axi4 = {
    val ret = Wire(Axi4(config))
    ret.aw << this.aw.pipelined(aw)
    ret.w << this.w.pipelined(w)
    ret.b.pipelined(b) >> this.b
    ret.ar << this.ar.pipelined(ar)
    ret.r.pipelined(r) >> this.r
    ret
  }
}


/**
  * Definition of the constants used by the Axi4 bus
  */
object Axi4 {
  val boundaryWidth = 12

  object size {
    def apply() = UInt(3.W)
    def BYTE_1   = "b000".U(3.W)
    def BYTE_2   = "b001".U(3.W)
    def BYTE_4   = "b010".U(3.W)
    def BYTE_8   = "b011".U(3.W)
    def BYTE_16  = "b100".U(3.W)
    def BYTE_32  = "b101".U(3.W)
    def BYTE_64  = "b110".U(3.W)
    def BYTE_128 = "b111".U(3.W)
  }

  object awcache {
    def apply() = UInt(4.W)
    def OTHER      = "b1000".U(4.W)
    def ALLOCATE   = "b0100".U(4.W)
    def MODIFIABLE = "b0010".U(4.W)
    def BUFFERABLE = "b0001".U(4.W)
  }

  object arcache {
    def apply() = UInt(4.W)
    def ALLOCATE   = "b1000".U(4.W)
    def OTHER      = "b0100".U(4.W)
    def MODIFIABLE = "b0010".U(4.W)
    def BUFFERABLE = "b0001".U(4.W)
  }

  object burst {
    def apply() = UInt(2.W)
    def FIXED    = "b00".U(2.W)
    def INCR     = "b01".U(2.W)
    def WRAP     = "b10".U(2.W)
    def RESERVED = "b11".U(2.W)
  }

  object lock {
    def apply() = UInt(1.W)
    def NORMAL    = "b0".U(1.W)
    def EXCLUSIVE = "b1".U(1.W)
  }

  object prot {
    def apply() = UInt(3.W)
    def PRIVILEGED  = "b001".U(3.W)
    def NON_SECURE  = "b010".U(3.W)
    def INSTRUCTION = "b100".U(3.W)
  }

  object resp {
    def apply() = UInt(2.W)
    def OKAY   = "b00".U(2.W) // Normal access success
    def EXOKAY = "b01".U(2.W) // Exclusive access okay
    def SLVERR = "b10".U(2.W) // Slave error
    def DECERR = "b11".U(2.W) // Decode error
  }

  //Return the increment of a address depending the burst configuration (INCR,WRAP,FIXED)
  def incr(address: UInt, burst: UInt, len: UInt, size: UInt, bytePerWord: Int): UInt = {
    val addressWidth = address.getWidth
    val maxSize = log2Up(bytePerWord)
    val validSizeWidth = log2Up(maxSize + 1)
    val validSize = size(validSizeWidth - 1, 0)
    val result = Wire(UInt(addressWidth.W))
    // 12 位以上的高位在 INCR/WRAP 中保持不变（4K 边界内递增）
    val highCat: UInt = if (addressWidth > 12) address(addressWidth - 1, 12) else 0.U(0.W)
    val sizeValue = (1.U(1.W) << validSize).asUInt.pad(maxSize + 1) // 1 << size
    val alignMask = ((1.U(1.W) << validSize).asUInt - 1.U).pad(12)(11, 0) // (1 << size) - 1
    val base = address.pad(12)(11, 0) & ~alignMask
    val baseIncr = base +% sizeValue
    val wrapCaseMax = maxSize + 3 // 3 is the maximum result of the len mux below
    val wrapCaseWidth = log2Up(wrapCaseMax + 1)
    // WRAP 合法 len 为 1/3/7/15（2/4/8/16 拍），按最高位优先编码
    val lenWrapSel = Mux(len(3), 3.U(2.W), Mux(len(2), 2.U(2.W), Mux(len(1), 1.U(2.W), 0.U(2.W))))
    val wrapCase = validSize.pad(wrapCaseWidth) +% lenWrapSel

    def resizeTo(value: UInt, width: Int): UInt =
      if (value.getWidth >= width) value(width - 1, 0) else value.pad(width)

    // default（INCR / RESERVED），后续 switch 命中时覆盖
    result := resizeTo(Cat(highCat, baseIncr), addressWidth)
    switch(burst) {
      is(Axi4.burst.FIXED) {
        result := address
      }
      is(Axi4.burst.WRAP) {
        val cases = VecInit((0 to wrapCaseMax).map(i => Cat(base(11, i + 1), baseIncr(i, 0))))
        result := resizeTo(Cat(highCat, cases(wrapCase)), addressWidth)
      }
    }
    result
  }
}
