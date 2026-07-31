import chisel3._
import chisel3.util._
import scala.collection.mutable

/** TileLink 总线接口层，移植自 SpinalHDL spinal.lib.bus.tilelink.Bus。
  *
  * 与原版的差异：
  *   - `SpinalEnum` 自定义编码 → `ChiselEnum`（编码值保持 TileLink 规范）；
  *   - `generate`/null 可选信号 → `Option[X]`，`Bits` → `UInt`；
  *   - `WeakConnector` → 各通道手写的 `weakAssignFrom`（语义对应：双方有则连接、
  *     仅目标有则给默认值、仅来源有且允许 drop 则丢弃）；
  *   - `DebugId` 的 ScopeProperty 机制简化为全局可变宽度（默认 0 宽，即不占端口）；
  *   - `withSourceOffset`/`fromSinkOffset` 无法使用 removeAssignments，
  *     改为逐字段连接后用 last-connect-wins 覆盖 source/sink；
  *   - beat 计数器的 `signalCache` 用 WeakHashMap 按 Stream 实例缓存，
  *     建议在模块顶层作用域（而非 when 内）首次调用 isLast()/beatCounter()；
  *   - OrderingTag / sim 相关内容未移植。
  */

object Opcode {
  object A extends ChiselEnum {
    val PUT_FULL_DATA    = Value(0.U(3.W))
    val PUT_PARTIAL_DATA = Value(1.U)
    val GET              = Value(4.U)
    val ACQUIRE_BLOCK    = Value(6.U)
    val ACQUIRE_PERM     = Value(7.U)

    def isGetPut(c: Type): Bool = c === GET || c === PUT_FULL_DATA || c === PUT_PARTIAL_DATA
    def isPut(c: Type): Bool = c === PUT_FULL_DATA || c === PUT_PARTIAL_DATA
    def isGet(c: Type): Bool = c === GET
    def isAcquire(c: Type): Bool = c === ACQUIRE_BLOCK || c === ACQUIRE_PERM
  }

  object B extends ChiselEnum {
    val PROBE_BLOCK = Value(6.U(3.W))
    val PROBE_PERM  = Value(7.U)
  }

  object C extends ChiselEnum {
    val PROBE_ACK      = Value(4.U(3.W))
    val PROBE_ACK_DATA = Value(5.U)
    val RELEASE        = Value(6.U)
    val RELEASE_DATA   = Value(7.U)

    def withoutData(c: Type): Bool = c === PROBE_ACK || c === RELEASE
    def withData(c: Type): Bool = c === PROBE_ACK_DATA || c === RELEASE_DATA
    def isProbe(c: Type): Bool = c === PROBE_ACK || c === PROBE_ACK_DATA
    def isRelease(c: Type): Bool = c === RELEASE || c === RELEASE_DATA
  }

  object D extends ChiselEnum {
    val ACCESS_ACK      = Value(0.U(3.W))
    val ACCESS_ACK_DATA = Value(1.U)
    val GRANT           = Value(4.U)
    val GRANT_DATA      = Value(5.U)
    val RELEASE_ACK     = Value(6.U)

    def fromA(opcode: Type): Bool =
      opcode === ACCESS_ACK || opcode === ACCESS_ACK_DATA || opcode === GRANT || opcode === GRANT_DATA
    def isFinal(opcode: Type): Bool =
      opcode === ACCESS_ACK || opcode === ACCESS_ACK_DATA || opcode === RELEASE_ACK
  }
}

object Param {
  object Hint {
    val NONE = 0
    val NO_ALLOCATE_ON_MISS = 2
  }

  object Cap {
    val toT = 0
    val toB = 1
    val toN = 2
  }
  object Prune {
    def fromTo(from: Int, to: Int): Int = (from, to) match {
      case (Cap.toT, Cap.toB) => TtoB
      case (Cap.toT, Cap.toN) => TtoN
      case (Cap.toB, Cap.toN) => BtoN
    }
    val TtoB = 0
    val TtoN = 1
    val BtoN = 2
  }
  object Report {
    def fromCap(cap: Int): Int = cap match {
      case Cap.toT => TtoT
      case Cap.toB => BtoB
      case Cap.toN => NtoN
    }
    val TtoT = 3
    val BtoB = 4
    val NtoN = 5
  }
  object Grow {
    val NtoB = 0
    val NtoT = 1
    val BtoT = 2

    def apply(withData: Bool, toUnique: Bool): UInt = {
      Mux(toUnique,
        Mux(withData, NtoT.U(3.W), BtoT.U(3.W)),
        NtoB.U(3.W))
    }

    def getCap(grow: Int): Int = grow match {
      case NtoB => Cap.toB
      case NtoT => Cap.toT
      case BtoT => Cap.toT
    }

    def fromTo(grow: Int): (Int, Int) = grow match {
      case NtoB => (Cap.toN, Cap.toB)
      case NtoT => (Cap.toN, Cap.toT)
      case BtoT => (Cap.toB, Cap.toT)
    }
  }
  def reportPruneKeepCopy(param: UInt): Bool =
    param === Prune.TtoB.U || param === Report.BtoB.U || param === Report.TtoT.U
  def reportPruneToCap(param: Int): Int = param match {
    case Prune.TtoB  => Cap.toB
    case Prune.TtoN  => Cap.toN
    case Prune.BtoN  => Cap.toN
    case Report.TtoT => Cap.toT
    case Report.BtoB => Cap.toB
    case Report.NtoN => Cap.toN
  }
  def reportPruneFromTo(param: Int): (Int, Int) = param match {
    case Prune.TtoB  => (Cap.toT, Cap.toB)
    case Prune.TtoN  => (Cap.toT, Cap.toN)
    case Prune.BtoN  => (Cap.toB, Cap.toN)
    case Report.TtoT => (Cap.toT, Cap.toT)
    case Report.BtoB => (Cap.toB, Cap.toB)
    case Report.NtoN => (Cap.toN, Cap.toN)
  }

  /** 对应原版的 muxDc（don't care 缺省此处简化为 0） */
  def report(fromUnique: Bool, fromShared: Bool, toUnique: Bool, toShared: Bool): UInt = {
    val sel = Cat(fromUnique, fromShared, toUnique, toShared)
    MuxLookup(sel, 0.U(3.W))(Seq(
      "b1001".U -> Prune.TtoB.U(3.W),
      "b1000".U -> Prune.TtoN.U(3.W),
      "b0100".U -> Prune.BtoN.U(3.W),
      "b1010".U -> Report.TtoT.U(3.W),
      "b0101".U -> Report.BtoB.U(3.W),
      "b0000".U -> Report.NtoN.U(3.W)
    ))
  }
}

/** 简化版 DebugId 分配器（原版基于 ScopeProperty）。
  * 默认宽度 0，即 a.debugId 不占任何位；调用 setup(w) 后可分配调试 id 空间。
  * 注意：宽度是全局可变状态，跨多次 elaborate 时如需复位请再次 setup。
  */
object DebugId {
  private var widthValue = 0
  private var reservedValue = 0
  def width: Int = widthValue
  def enabled: Boolean = widthValue != 0
  def apply(): UInt = UInt(widthValue.W)
  def setup(width: Int): Unit = {
    require(width > 8)
    widthValue = width
    reservedValue = 0
  }
  def withPostfix(post: UInt): UInt = {
    if (!enabled) return 0.U(0.W)
    val size = 1 << post.getWidth
    val mask = size - 1
    val base = (reservedValue + mask) & ~mask
    reservedValue = base + size
    base.U(widthValue.W) | post.pad(widthValue)
  }
}

/** A/B/C/D 通道 payload 的公共抽象（对应原版 BusFragment）。
  * 原版的 xxxNull（可能为 null 的信号）改为 xxxOpt: Option。
  */
abstract class BusFragment extends Bundle {
  def p: BusParameter
  def size: UInt
  def withBeats: Bool
  def dataOpt: Option[UInt]
  def corruptOpt: Option[Bool]
  def maskOpt: Option[UInt] = None
  def deniedOpt: Option[Bool] = None
  def sinkOpt: Option[UInt] = None
  def addressOpt: Option[UInt]

  def withAddress: Boolean
  def withData: Boolean
  def withMask: Boolean
  def withDenied: Boolean
  def withSink: Boolean = false

  def sizeToBeatMinusOne(): UInt = tilelink.sizeToBeatMinusOne(p, size)

  def asNoData(): BusFragment
}

object ChannelA {
  def apply(p: BusParameter): ChannelA = new ChannelA(p)
  def apply(node: NodeParameters): ChannelA = new ChannelA(node.toBusParameter())
}

class ChannelA(val p: BusParameter) extends BusFragment {
  val opcode  = Opcode.A()
  val param   = UInt(3.W)
  val source  = p.source()
  val address = p.address()
  val size    = p.size()
  val mask    = if (p.withDataA) Some(p.mask()) else None
  val data    = if (p.withDataA) Some(p.data()) else None
  val corrupt = if (p.withDataA) Some(Bool()) else None
  val debugId = DebugId()

  override def withBeats: Bool =
    if (p.withDataA) opcode === Opcode.A.PUT_FULL_DATA || opcode === Opcode.A.PUT_PARTIAL_DATA
    else false.B

  def asNoData(): ChannelA = {
    if (!p.withDataA) this
    else {
      val a = Wire(ChannelA(p.copy(withDataA = false)))
      a.opcode := opcode
      a.param := param
      a.source := source
      a.address := address
      a.size := size
      a.debugId := debugId
      a
    }
  }

  override def dataOpt = data
  override def corruptOpt = corrupt
  override def maskOpt = mask
  override def addressOpt = Some(address)

  def withAddress: Boolean = true
  def withData: Boolean = p.withDataA
  def withMask: Boolean = p.withDataA
  def withDenied: Boolean = false

  /** 由 size/address/opcode 推导出的合法字节使能掩码 */
  def compliantMask(): UInt = {
    val spec = (0 until (1 << p.sizeWidth)).map { i =>
      i.U -> (((BigInt(1) << (1 << i)) - 1) & ((BigInt(1) << p.dataBytes) - 1)).U(p.dataBytes.W)
    }
    val fromSize = MuxLookup(size, 0.U(p.dataBytes.W))(spec)
    val shifted =
      if (p.dataBytesLog2Up == 0) fromSize
      else ((fromSize << address(p.dataBytesLog2Up - 1, 0)) (p.dataBytes - 1, 0)).asUInt
    shifted & (mask.get | Fill(p.dataBytes, !Opcode.A.isPut(opcode)))
  }

  def weakAssignFrom(m: ChannelA): Unit = {
    opcode := m.opcode
    param := m.param
    source := tilelink.resized(m.source, source.getWidth)
    address := tilelink.resized(m.address, address.getWidth)
    debugId := m.debugId
    require(size.getWidth >= m.size.getWidth, "ChannelA.weakAssignFrom: size cannot be down-sized")
    size := tilelink.resized(m.size, size.getWidth)
    (mask, m.mask) match {
      case (Some(s), Some(v)) => s := v
      case (Some(s), None)    => s := DontCare
      case _                  => // drop allowed
    }
    (data, m.data) match {
      case (Some(s), Some(v)) => s := v
      case (Some(s), None)    => s := DontCare
      case _                  => // drop allowed
    }
    (corrupt, m.corrupt) match {
      case (Some(s), Some(v)) => s := v
      case (Some(s), None)    => s := false.B
      case (None, Some(_))    => require(false, "ChannelA.weakAssignFrom: corrupt cannot be dropped")
      case _                  =>
    }
  }
}

object ChannelB {
  def apply(p: BusParameter): ChannelB = new ChannelB(p)
  def apply(node: NodeParameters): ChannelB = new ChannelB(node.toBusParameter())
}

class ChannelB(val p: BusParameter) extends BusFragment {
  require(!p.withDataB, "ChannelB with data is not supported yet")

  val opcode  = Opcode.B()
  val param   = UInt(3.W)
  val source  = p.source()
  val address = p.address()
  val size    = p.size()
  val mask    = if (p.withDataB) Some(p.mask()) else None
  val data    = if (p.withDataB) Some(p.data()) else None
  val corrupt = if (p.withDataB) Some(Bool()) else None

  override def withBeats: Bool = false.B
  override def dataOpt = data
  override def corruptOpt = corrupt
  override def maskOpt = mask
  override def addressOpt = Some(address)

  def withAddress: Boolean = true
  def withData: Boolean = p.withDataB
  def withMask: Boolean = p.withDataB
  def withDenied: Boolean = false

  def asNoData(): ChannelB = this

  def weakAssignFrom(m: ChannelB): Unit = {
    opcode := m.opcode
    param := m.param
    source := tilelink.resized(m.source, source.getWidth)
    address := tilelink.resized(m.address, address.getWidth)
    size := tilelink.resized(m.size, size.getWidth)
  }
}

object ChannelC {
  def apply(p: BusParameter): ChannelC = new ChannelC(p)
  def apply(node: NodeParameters): ChannelC = new ChannelC(node.toBusParameter())
}

class ChannelC(val p: BusParameter) extends BusFragment {
  val opcode  = Opcode.C()
  val param   = UInt(3.W)
  val source  = p.source()
  val address = p.address()
  val size    = p.size()
  val data    = if (p.withDataC) Some(p.data()) else None
  val corrupt = Bool()

  def isProbeKind(): Bool = opcode === Opcode.C.PROBE_ACK || opcode === Opcode.C.PROBE_ACK_DATA
  def isReleaseKind(): Bool = opcode === Opcode.C.RELEASE || opcode === Opcode.C.RELEASE_DATA
  def isDataKind(): Bool = opcode === Opcode.C.PROBE_ACK_DATA || opcode === Opcode.C.RELEASE_DATA

  override def withBeats: Bool = opcode === Opcode.C.PROBE_ACK_DATA || opcode === Opcode.C.RELEASE_DATA
  override def dataOpt = data
  override def corruptOpt = Some(corrupt)
  override def addressOpt = Some(address)

  def withAddress: Boolean = true
  def withData: Boolean = p.withDataC
  def withMask: Boolean = false
  def withDenied: Boolean = false

  def asNoData(): ChannelC = {
    if (!p.withDataC) this
    else {
      val c = Wire(ChannelC(p.copy(withDataC = false)))
      c.opcode := opcode
      c.param := param
      c.source := source
      c.address := address
      c.size := size
      c.corrupt := corrupt
      c
    }
  }

  def weakAssignFrom(m: ChannelC): Unit = {
    opcode := m.opcode
    param := m.param
    source := tilelink.resized(m.source, source.getWidth)
    address := tilelink.resized(m.address, address.getWidth)
    size := tilelink.resized(m.size, size.getWidth)
    corrupt := m.corrupt
    (data, m.data) match {
      case (Some(s), Some(v)) => s := v
      case (Some(s), None)    => s := DontCare
      case _                  => // drop allowed
    }
  }
}

object ChannelD {
  def apply(p: BusParameter): ChannelD = new ChannelD(p)
  def apply(node: NodeParameters): ChannelD = new ChannelD(node.toBusParameter())
}

class ChannelD(val p: BusParameter) extends BusFragment {
  val opcode  = Opcode.D()
  val param   = UInt(3.W)
  val source  = p.source()
  val sink    = p.sink()
  val size    = p.size()
  val denied  = Bool()
  val data    = if (p.withDataD) Some(p.data()) else None
  val corrupt = if (p.withDataD) Some(Bool()) else None

  override def withBeats: Bool =
    if (p.withDataD) opcode === Opcode.D.ACCESS_ACK_DATA || opcode === Opcode.D.GRANT_DATA
    else false.B

  def withDontCareData(): ChannelD = {
    val ret = Wire(ChannelD(p.copy(withDataD = true)))
    ret.opcode := opcode
    ret.param := param
    ret.source := source
    ret.sink := sink
    ret.size := size
    ret.denied := denied
    ret.data.get := DontCare
    ret.corrupt.get := DontCare
    ret
  }

  override def dataOpt = data
  override def corruptOpt = corrupt
  override def deniedOpt = Some(denied)
  override def sinkOpt = Some(sink)
  override def addressOpt = None

  def withAddress: Boolean = false
  def withData: Boolean = p.withDataD
  def withMask: Boolean = false
  def withDenied: Boolean = true
  override def withSink: Boolean = true

  def weakAssignFrom(m: ChannelD): Unit = {
    opcode := m.opcode
    param := m.param
    source := tilelink.resized(m.source, source.getWidth)
    sink := tilelink.resized(m.sink, sink.getWidth)
    denied := m.denied
    size := tilelink.resized(m.size, size.getWidth) // up/down 都允许
    (data, m.data) match {
      case (Some(s), Some(v)) => s := v
      case (Some(s), None)    => s := DontCare
      case _                  => // drop allowed
    }
    (corrupt, m.corrupt) match {
      case (Some(s), Some(v)) => s := v
      case (Some(s), None)    => s := false.B
      case _                  => // drop allowed
    }
  }

  def asNoData(): ChannelD = {
    if (!p.withDataD) this
    else {
      val d = Wire(ChannelD(p.copy(withDataD = false)))
      d.opcode := opcode
      d.param := param
      d.source := source
      d.sink := sink
      d.size := size
      d.denied := denied
      d
    }
  }
}

object ChannelE {
  def apply(p: BusParameter): ChannelE = new ChannelE(p)
  def apply(node: NodeParameters): ChannelE = new ChannelE(node.toBusParameter())
}

class ChannelE(val p: BusParameter) extends Bundle {
  val sink = p.sink()
}

object Bus {
  def apply(p: BusParameter): Bus = new Bus(p)
  def apply(p: NodeParameters): Bus = new Bus(p.toBusParameter())
  def apply(p: M2sParameters): Bus = new Bus(p.toNodeParameters().toBusParameter())
}

/** TileLink 总线 Bundle，按 master 视角声明方向：
  * a/c/e 由 master 驱动（原样），b/d 由 slave 驱动（Flipped）。
  * `slave(Bus(p))` 即得到 slave 视角。
  */
class Bus(val p: BusParameter) extends Bundle with IMasterSlave {
  val a = Stream(ChannelA(p))
  val b = if (p.withBCE) Some(Flipped(Stream(ChannelB(p)))) else None
  val c = if (p.withBCE) Some(Stream(ChannelC(p))) else None
  val d = Flipped(Stream(ChannelD(p)))
  val e = if (p.withBCE) Some(Stream(ChannelE(p))) else None

  /** this（下游）从 m（上游 master 侧）直连 */
  def <<(m: Bus): Unit = {
    a << m.a
    m.d << d
    if (p.withBCE) {
      m.b.get << b.get
      c.get << m.c.get
      e.get << m.e.get
    }
  }
  def >>(s: Bus): Unit = s << this

  def connectFrom(m: Bus)(
      a: StreamPipe = StreamPipe.NONE,
      b: StreamPipe = StreamPipe.NONE,
      c: StreamPipe = StreamPipe.NONE,
      d: StreamPipe = StreamPipe.NONE,
      e: StreamPipe = StreamPipe.NONE
  ): Unit = {
    this.a << a(m.a)
    m.d << d(this.d)
    if (p.withBCE) {
      m.b.get << b(this.b.get)
      this.c.get << c(m.c.get)
      this.e.get << e(m.e.get)
    }
  }

  def combStage(): Bus = {
    val ret = Wire(new Bus(p))
    ret << this
    ret
  }
  def fromCombStage(): Bus = {
    val ret = Wire(new Bus(p))
    ret >> this
    ret
  }

  /** 得到 source 加了偏移（拓宽到 width）后的下游视角总线 */
  def withSourceOffset(offset: Int, width: Int): Bus = {
    val ret = Wire(new Bus(p.copy(sourceWidth = width)))
    ret.a.arbitrationFrom(a)
    ret.a.payload.weakAssignFrom(a.payload)
    ret.a.payload.source := a.payload.source.pad(width) | offset.U(width.W)
    d.arbitrationFrom(ret.d)
    d.payload.weakAssignFrom(ret.d.payload)
    if (p.withBCE) {
      b.get.arbitrationFrom(ret.b.get)
      b.get.payload.weakAssignFrom(ret.b.get.payload)
      ret.c.get.arbitrationFrom(c.get)
      ret.c.get.payload.weakAssignFrom(c.get.payload)
      ret.c.get.payload.source := c.get.payload.source.pad(width) | offset.U(width.W)
      ret.e.get << e.get
    }
    ret
  }

  /** 得到 sink 加了偏移（拓宽到 width）后的上游视角总线 */
  def fromSinkOffset(offset: Int, width: Int): Bus = {
    val ret = Wire(new Bus(p.copy(sinkWidth = width)))
    a << ret.a
    ret.d.arbitrationFrom(d)
    ret.d.payload.weakAssignFrom(d.payload)
    if (p.withBCE) {
      ret.b.get << b.get
      c.get << ret.c.get
      ret.d.payload.sink := d.payload.sink.pad(width) | offset.U(width.W)
      e.get.arbitrationFrom(ret.e.get)
      e.get.payload.sink := tilelink.resized(ret.e.get.payload.sink, p.sinkWidth)
    } else {
      ret.d.payload.sink := 0.U(width.W)
    }
    ret
  }
}

/** 对应原版 package object tilelink 中的 beat 计数工具。
  * 用法：`import tilelink._` 后即可在 Stream[ChannelX] 上调用 isLast()/isFirst() 等。
  */
object tilelink {

  /** 宽度自适应连接辅助（宽则截断、窄则零扩展） */
  def resized(src: UInt, width: Int): UInt = {
    if (src.getWidth == width) src
    else if (src.getWidth > width) {
      if (width == 0) 0.U(0.W) else src(width - 1, 0)
    } else src.pad(width)
  }

  def sizeToBeatMinusOne(p: BusParameter, size: UInt): UInt = {
    val table = VecInit((0 to p.sizeMax).map { s =>
      (((1 << s) + p.dataBytes - 1) / p.dataBytes - 1).U(p.beatWidth.W)
    })
    table(size)
  }

  // 对应原版 signalCache：同一 Stream 实例只生成一份 beat 计数器
  private val beatCaches = mutable.WeakHashMap[Any, (UInt, Bool)]()

  implicit class TilelinkBusFragmentPimper[T <: BusFragment](ch: Stream[T]) {
    private def fillBeatCache(): (UInt, Bool) = beatCaches.getOrElseUpdate(ch, {
      val p = ch.payload.p
      if (p.beatWidth == 0) {
        (0.U, true.B)
      } else {
        val beat = RegInit(0.U(p.beatWidth.W))
        val last = !ch.payload.withBeats || beat === sizeToBeatMinusOne(p, ch.payload.size)
        when(ch.fire) {
          beat := beat +% 1.U
          when(last) {
            beat := 0.U
          }
        }
        (beat, last)
      }
    })

    def isLast(): Bool = ch.payload.withData match {
      case false => true.B
      case true  => fillBeatCache()._2
    }

    def isFirst(): Bool = ch.payload.withData match {
      case false => true.B
      case true  => fillBeatCache()._1 === 0.U
    }

    def beatCounter(): UInt = fillBeatCache()._1

    def beatAddress: UInt = {
      val addr = ch.payload.addressOpt.get
      resized(addr | (beatCounter() << ch.payload.p.dataBytesLog2Up), addr.getWidth)
    }
  }
}
