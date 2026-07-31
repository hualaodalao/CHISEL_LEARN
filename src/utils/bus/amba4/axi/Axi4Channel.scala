import chisel3._
import chisel3.util._

/** SpinalHDL Axi4Channel.scala 的 Chisel 移植版。
  *
  * 各通道 payload Bundle（AW/AR/共享 ARW、W、B、R）+ 跨配置连接 `drive` +
  * 突发拆分 `unburstify`。
  *
  * 与 SpinalHDL 的差异：
  *   - 可选信号 `if(...) X else null` → `Option[X]`；`Bits` → `UInt`。
  *   - `assignSomeByName` → 显式实现（按同名字段连接，宽度自动 resize）。
  *   - `stream.len` 等 payload 直访 → `stream.payload.len`。
  *   - 未移植：formalContext/formalCovers、FormalAxi4Record。
  */

/**
 * Definition of the Write/Read address channel
 * @param config Axi4 configuration class
 */
class Axi4Ax(val config: Axi4Config, val userWidth: Int, val readOnly: Boolean) extends Bundle {
  val addr    = UInt(config.addressWidth.W)
  val id      = if (config.useId)     Some(UInt(config.idWidth.W))    else None
  val region  = if (config.useRegion) Some(UInt(4.W))                 else None
  val len     = if (config.useLen)    Some(UInt(config.lenWidth.W))   else None
  val size    = if (config.useSize)   Some(UInt(config.sizeWidth.W))  else None
  val burst   = if (config.useBurst)  Some(UInt(2.W))                 else None
  val lock    = if (config.useLock)   Some(UInt(config.lockWidth.W))  else None
  val cache   = if (config.useCache)  Some(UInt(4.W))                 else None
  val qos     = if (config.useQos)    Some(UInt(4.W))                 else None
  val user    = if (userWidth >= 0)   Some(UInt(userWidth.W))         else None
  val prot    = if (config.useProt)   Some(UInt(3.W))                 else None
  val allStrb = if (config.useAllStrb && !readOnly) Some(Bool())      else None

  import Axi4.burst._

  def setBurstFIXED(): Unit = { require(config.useBurst); burst.get := FIXED }
  def setBurstWRAP():  Unit = { require(config.useBurst); burst.get := WRAP }
  def setBurstINCR():  Unit = { require(config.useBurst); burst.get := INCR }

  def isINCR()  = burst.map(_ === INCR).getOrElse(true.B)
  def isFIXED() = burst.map(_ === FIXED).getOrElse(false.B)

  def setSize(sizeBurst: UInt): Unit = if (config.useSize) size.get := sizeBurst
  def setFullSize(): Unit = this.setSize(log2Up(config.dataWidth / 8).U)
  def setLock(lockType: UInt): Unit = if (config.useLock) lock.get := lockType
  def setCache(cacheType: UInt): Unit = if (config.useCache) cache.get := cacheType
  def setQos(qosType: UInt): Unit = if (config.useQos) qos.get := qosType
  def setProt(protType: UInt): Unit = if (config.useProt) prot.get := protType

  /** 按同名字段连接（宽度自动 resize，双方都存在的可选字段才连接） */
  def assignSomeByName(that: Axi4Ax): Unit = {
    this.addr := Axi4Priv.resized(that.addr, this.addr.getWidth)
    val pairs = Seq(
      this.id -> that.id, this.region -> that.region, this.len -> that.len,
      this.size -> that.size, this.burst -> that.burst, this.lock -> that.lock,
      this.cache -> that.cache, this.qos -> that.qos, this.user -> that.user,
      this.prot -> that.prot)
    for ((t, b) <- pairs; tt <- t; bb <- b) tt := Axi4Priv.resized(bb, tt.getWidth)
    for (tt <- this.allStrb; bb <- that.allStrb) tt := bb
  }

  /** 换到更宽数据总线后的等效 len */
  def getLenOnDataWidth(dataWidth: Int): UInt = {
    require(dataWidth > config.dataWidth)
    val w = 8 + log2Up(config.bytePerWord)
    val byteCount = Axi4Priv.resized((len.get << size.get).asUInt, w)
    val incrLen = (byteCount.pad(w + 1) +% addr(log2Up(dataWidth / 8) - 1, 0))(w, log2Up(dataWidth / 8))
    incrLen
  }

  def getAddrSizeMaskedLow(): UInt = {
    val width = log2Up(config.bytePerWord)
    addr(width - 1, 0) & Axi4Priv.resized((1.U(1.W) << size.get).asUInt - 1.U, width)
  }

  def getFirstBeatBytesMinusOne(): UInt = {
    Axi4Priv.resized((1.U(1.W) << size.get).asUInt - 1.U - getAddrSizeMaskedLow(), log2Up(config.bytePerWord))
  }

  def getBurstBytesMinusOne(boundaryWidth: Int = Axi4.boundaryWidth): UInt = {
    Axi4Priv.resized((len.get << size.get).asUInt, boundaryWidth) +% getFirstBeatBytesMinusOne()
  }

  /** 按 size 对齐后的地址（size 为 0 或超界时原样返回） */
  def getLenAlignedAddr(): UInt = {
    val ones = (BigInt(1) << config.addressWidth) - 1
    val mask = MuxLookup(size.get, ones.U(config.addressWidth.W))(
      (1 to log2Up(config.bytePerWord)).map(l =>
        l.U -> (ones - ((BigInt(1) << l) - 1)).U(config.addressWidth.W)))
    addr & mask
  }
}


class Axi4Aw(config: Axi4Config) extends Axi4Ax(config, config.awUserWidth, readOnly = false)
class Axi4Ar(config: Axi4Config) extends Axi4Ax(config, config.arUserWidth, readOnly = true)
class Axi4Arw(config: Axi4Config) extends Axi4Ax(config, config.arwUserWidth, readOnly = false) {
  val write = Bool()

  override def assignSomeByName(that: Axi4Ax): Unit = {
    super.assignSomeByName(that)
    that match {
      case arw: Axi4Arw => write := arw.write
      case _            =>
    }
  }
}


/**
 * Definition of the Write data channel
 * @param config Axi4 configuration class
 */
class Axi4W(val config: Axi4Config) extends Bundle {
  val data = UInt(config.dataWidth.W)
  val strb = if (config.useStrb)  Some(UInt(config.bytePerWord.W)) else None
  val user = if (config.useWUser) Some(UInt(config.wUserWidth.W))  else None
  val last = if (config.useLast)  Some(Bool())                     else None
  val id   = if (config.useWid)   Some(UInt(config.idWidth.W))     else None

  def setStrb(): Unit = if (config.useStrb) strb.get := ((BigInt(1) << config.bytePerWord) - 1).U
  def setStrb(bytesLane: UInt): Unit = if (config.useStrb) strb.get := bytesLane
}

object Axi4W {
  def apply(config: Axi4Config) = new Axi4W(config)

  implicit class StreamPimper(stream: Stream[Axi4W]) {
    def drive(sink: Stream[Axi4W]): Unit = {
      sink.arbitrationFrom(stream)
      val s = stream.payload
      val k = sink.payload
      sink.payload.data := stream.payload.data
      Axi4Priv.driveWeak(s, k, s.strb, k.strb, () => ((BigInt(1) << k.config.bytePerWord) - 1).U(k.config.bytePerWord.W), false, false)
      Axi4Priv.driveWeak(s, k, s.user, k.user, () => 0.U(k.config.wUserWidth.W), false, true)
      Axi4Priv.driveWeak(s, k, s.last, k.last, null, false, true)
      Axi4Priv.driveWeak(s, k, s.id, k.id, null, true, true)
    }
  }
}


/**
 * Definition of the Write response channel
 * @param config Axi4 configuration class
 */
class Axi4B(val config: Axi4Config) extends Bundle {
  val id   = if (config.useId)    Some(UInt(config.idWidth.W))    else None
  val resp = if (config.useResp)  Some(UInt(2.W))                 else None
  val user = if (config.useBUser) Some(UInt(config.bUserWidth.W)) else None

  import Axi4.resp._

  def setOKAY():   Unit = resp.get := OKAY
  def setEXOKAY(): Unit = resp.get := EXOKAY
  def setSLVERR(): Unit = resp.get := SLVERR
  def setDECERR(): Unit = resp.get := DECERR
  def isOKAY():   Bool = resp.get === OKAY
  def isEXOKAY(): Bool = resp.get === EXOKAY
  def isSLVERR(): Bool = resp.get === SLVERR
  def isDECERR(): Bool = resp.get === DECERR
}

object Axi4B {
  def apply(config: Axi4Config) = new Axi4B(config)

  implicit class StreamPimper(stream: Stream[Axi4B]) {
    def drive(sink: Stream[Axi4B]): Unit = {
      require(stream.payload.config.idWidth >= sink.payload.config.idWidth,
        s"Expect stream idWidth=${stream.payload.config.idWidth} >= sink idWidth=${sink.payload.config.idWidth}")
      sink.arbitrationFrom(stream)
      val s = stream.payload
      val k = sink.payload
      Axi4Priv.driveWeak(s, k, s.id, k.id, null, true, true)
      Axi4Priv.driveWeak(s, k, s.resp, k.resp, () => Axi4.resp.OKAY, false, true)
      Axi4Priv.driveWeak(s, k, s.user, k.user, () => 0.U(k.config.bUserWidth.W), false, true)
    }
  }
}


/**
 * Definition of the Read Data channel
 * @param config Axi4 configuration class
 */
class Axi4R(val config: Axi4Config) extends Bundle {
  val data = UInt(config.dataWidth.W)
  val id   = if (config.useId)    Some(UInt(config.idWidth.W))    else None
  val resp = if (config.useResp)  Some(UInt(2.W))                 else None
  val last = if (config.useLast)  Some(Bool())                    else None
  val user = if (config.useRUser) Some(UInt(config.rUserWidth.W)) else None

  import Axi4.resp._

  def setOKAY():   Unit = resp.get := OKAY
  def setEXOKAY(): Unit = resp.get := EXOKAY
  def setSLVERR(): Unit = resp.get := SLVERR
  def setDECERR(): Unit = resp.get := DECERR
  def isOKAY():   Bool = resp.get === OKAY
  def isEXOKAY(): Bool = resp.get === EXOKAY
  def isSLVERR(): Bool = resp.get === SLVERR
  def isDECERR(): Bool = resp.get === DECERR
}

object Axi4R {
  def apply(config: Axi4Config) = new Axi4R(config)

  implicit class StreamPimper(stream: Stream[Axi4R]) {
    def drive(sink: Stream[Axi4R]): Unit = {
      require(stream.payload.config.idWidth >= sink.payload.config.idWidth,
        s"Expect stream idWidth=${stream.payload.config.idWidth} >= sink idWidth=${sink.payload.config.idWidth}")
      sink.arbitrationFrom(stream)
      val s = stream.payload
      val k = sink.payload
      sink.payload.data := stream.payload.data
      Axi4Priv.driveWeak(s, k, s.last, k.last, null, false, true)
      Axi4Priv.driveWeak(s, k, s.id, k.id, null, true, true)
      Axi4Priv.driveWeak(s, k, s.resp, k.resp, () => Axi4.resp.OKAY, false, true)
      Axi4Priv.driveWeak(s, k, s.user, k.user, () => 0.U(k.config.rUserWidth.W), false, true)
    }
  }
}


// ============================================================================
// 突发拆分（unburstify）
// ============================================================================

class Axi4AxUnburstified(val config: Axi4Config, val userWidth: Int) extends Bundle {
  val addr   = UInt(config.addressWidth.W)
  val id     = if (config.useId)     Some(UInt(config.idWidth.W)) else None
  val region = if (config.useRegion) Some(UInt(4.W))              else None
  val size   = if (config.useSize)   Some(UInt(3.W))              else None
  val burst  = if (config.useBurst)  Some(UInt(2.W))              else None
  val lock   = if (config.useLock)   Some(UInt(1.W))              else None
  val cache  = if (config.useCache)  Some(UInt(4.W))              else None
  val qos    = if (config.useQos)    Some(UInt(4.W))              else None
  val user   = if (userWidth >= 0)   Some(UInt(userWidth.W))      else None
  val prot   = if (config.useProt)   Some(UInt(3.W))              else None

  /** 从完整 Ax 通道按同名字段连接（len 被丢弃，宽度自动 resize） */
  def assignSomeByName(that: Axi4Ax): Unit = {
    this.addr := Axi4Priv.resized(that.addr, this.addr.getWidth)
    val pairs = Seq(
      this.id -> that.id, this.region -> that.region, this.size -> that.size,
      this.burst -> that.burst, this.lock -> that.lock, this.cache -> that.cache,
      this.qos -> that.qos, this.user -> that.user, this.prot -> that.prot)
    for ((t, b) <- pairs; tt <- t; bb <- b) tt := Axi4Priv.resized(bb, tt.getWidth)
  }
}

object Axi4AxUnburstified {

  /** 把带 len 的突发命令流拆成逐拍命令流（Fragment.last 标记最后一拍） */
  def unburstify[X <: Axi4Ax, Y <: Axi4AxUnburstified](stream: Stream[X], outPayloadType: Y): Stream[Fragment[Y]] = {
    val config = stream.payload.config
    val result = Wire(Stream(new Fragment(outPayloadType.cloneType)))
    val addrLow = math.min(11, config.addressWidth - 1) // 递增只影响 4K 边界内的低位

    val bufferValid = RegInit(false.B)
    val bufferLen = Reg(UInt(8.W))
    val bufferBeat = Reg(UInt(8.W))
    val bufferTransaction = Reg(outPayloadType.cloneType)
    val bufferLast = bufferBeat === 1.U
    val bufferAddress = Axi4.incr(
      address = bufferTransaction.addr,
      burst = bufferTransaction.burst.getOrElse(Axi4.burst.INCR),
      len = if (config.useLen) bufferLen else 0.U(8.W),
      size = bufferTransaction.size.getOrElse(log2Up(config.bytePerWord).U(3.W)),
      bytePerWord = config.bytePerWord
    )

    when(result.ready) {
      bufferBeat := bufferBeat - 1.U
      if (config.addressWidth - 1 > addrLow)
        bufferTransaction.addr := Cat(bufferTransaction.addr(config.addressWidth - 1, addrLow + 1), bufferAddress(addrLow, 0))
      else
        bufferTransaction.addr := bufferAddress
      when(bufferLast) { bufferValid := false.B }
    }

    stream.ready := false.B
    when(bufferValid) {
      result.valid := true.B
      result.payload.last := bufferLast
      result.payload.fragment := bufferTransaction
      result.payload.fragment.addr := bufferAddress
    }.otherwise {
      stream.ready := result.ready
      result.valid := stream.valid
      result.payload.fragment.assignSomeByName(stream.payload)
      result.payload.last := true.B
      if (config.useLen) {
        when(stream.payload.len.get =/= 0.U) {
          result.payload.last := false.B
          when(result.ready) {
            bufferValid := stream.valid
            bufferTransaction.assignSomeByName(stream.payload)
            bufferBeat := stream.payload.len.get
            bufferLen := stream.payload.len.get
          }
        }
      }
    }
    result
  }
}

class Axi4ArUnburstified(axiConfig: Axi4Config) extends Axi4AxUnburstified(axiConfig, axiConfig.arUserWidth)
class Axi4AwUnburstified(axiConfig: Axi4Config) extends Axi4AxUnburstified(axiConfig, axiConfig.awUserWidth)
class Axi4ArwUnburstified(axiConfig: Axi4Config) extends Axi4AxUnburstified(axiConfig, axiConfig.arwUserWidth) {
  val write = Bool()

  override def assignSomeByName(that: Axi4Ax): Unit = {
    super.assignSomeByName(that)
    that match {
      case arw: Axi4Arw => write := arw.write
      case _            =>
    }
  }
}

object Axi4ArUnburstified {
  def apply(axiConfig: Axi4Config) = new Axi4ArUnburstified(axiConfig)
}
object Axi4AwUnburstified {
  def apply(axiConfig: Axi4Config) = new Axi4AwUnburstified(axiConfig)
}
object Axi4ArwUnburstified {
  def apply(axiConfig: Axi4Config) = new Axi4ArwUnburstified(axiConfig)
}


// ============================================================================
// 跨配置连接工具
// ============================================================================

object Axi4Priv {

  /** 截断或补零到指定宽度 */
  def resized(value: UInt, width: Int): UInt = {
    if (width == 0) 0.U(0.W)
    else if (value.getWidth >= width) value(width - 1, 0)
    else value.pad(width)
  }

  /** 可选信号的弱连接：
    *   - 双方都有 → 直连（allowResize 时自动调宽）
    *   - 仅 sink 有 → 用 defaultValue 填充（无默认值则报错）
    *   - 仅 source 有 → allowDrop 时丢弃，否则报错
    */
  def driveWeak[T <: Data](source: Bundle, sink: Bundle, by: Option[T], to: Option[T],
                           defaultValue: () => T, allowResize: Boolean, allowDrop: Boolean): Unit = {
    (to, by) match {
      case (None, None) =>
      case (Some(t), None) =>
        require(defaultValue != null, s"$source can't drive $t because this first doesn't has the corresponding pin")
        t := defaultValue()
      case (None, Some(b)) =>
        require(allowDrop, s"$b can't drive $sink because this last one doesn't has the corresponding pin")
      case (Some(t), Some(b)) =>
        (t, b) match {
          case (tu: UInt, bu: UInt) if allowResize => tu := resized(bu, tu.getWidth)
          case _                                   => t := b
        }
    }
  }

  def driveAx[T <: Axi4Ax](stream: Stream[T], sink: Stream[T]): Unit = {
    sink.arbitrationFrom(stream)
    val s = stream.payload
    val k = sink.payload
    require(s.config.idWidth <= k.config.idWidth,
      s"Expect stream idWidth=${s.config.idWidth} <= sink idWidth=${k.config.idWidth}")
    require(s.config.addressWidth >= k.config.addressWidth,
      s"Expect stream addressWidth=${s.config.addressWidth} >= sink addressWidth=${k.config.addressWidth}")

    k.addr := resized(s.addr, k.config.addressWidth)
    driveWeak(s, k, s.id, k.id, () => 0.U(k.config.idWidth.W), true, false)
    driveWeak(s, k, s.region, k.region, () => 0.U(4.W), false, true)
    driveWeak(s, k, s.len, k.len, () => 0.U(k.config.lenWidth.W), false, false)
    driveWeak(s, k, s.size, k.size, () => log2Up(k.config.dataWidth / 8).U(k.config.sizeWidth.W), false, false)
    driveWeak(s, k, s.burst, k.burst, () => Axi4.burst.INCR, false, false)
    driveWeak(s, k, s.lock, k.lock, () => 0.U(k.config.lockWidth.W), false, true)
    driveWeak(s, k, s.cache, k.cache, () => "b0000".U(4.W), false, true)
    driveWeak(s, k, s.qos, k.qos, () => "b0000".U(4.W), false, true)
    driveWeak(s, k, s.user, k.user, () => 0.U, true, true)
    driveWeak(s, k, s.prot, k.prot, () => "b010".U(3.W), false, true)
    driveWeak(s, k, s.allStrb, k.allStrb, () => false.B, false, true)
  }
}


object Axi4Aw {
  def apply(config: Axi4Config) = new Axi4Aw(config)

  implicit class StreamPimper(stream: Stream[Axi4Aw]) {
    def unburstify: Stream[Fragment[Axi4AwUnburstified]] =
      Axi4AxUnburstified.unburstify(stream, Axi4AwUnburstified(stream.payload.config))

    def drive(sink: Stream[Axi4Aw]): Unit = Axi4Priv.driveAx(stream, sink)
  }
}

object Axi4Ar {
  def apply(config: Axi4Config) = new Axi4Ar(config)

  implicit class StreamPimper(stream: Stream[Axi4Ar]) {
    def unburstify: Stream[Fragment[Axi4ArUnburstified]] =
      Axi4AxUnburstified.unburstify(stream, Axi4ArUnburstified(stream.payload.config))

    def drive(sink: Stream[Axi4Ar]): Unit = Axi4Priv.driveAx(stream, sink)
  }
}

object Axi4Arw {
  def apply(config: Axi4Config) = new Axi4Arw(config)

  implicit class StreamPimper(stream: Stream[Axi4Arw]) {
    def unburstify: Stream[Fragment[Axi4ArwUnburstified]] =
      Axi4AxUnburstified.unburstify(stream, Axi4ArwUnburstified(stream.payload.config))

    def drive(sink: Stream[Axi4Arw]): Unit = {
      Axi4Priv.driveAx(stream, sink)
      sink.payload.write := stream.payload.write
    }
  }
}
