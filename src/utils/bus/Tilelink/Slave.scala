import chisel3._

/** TileLink slave 侧参数体系，移植自 SpinalHDL spinal.lib.bus.tilelink.Slave。
  *
  * 与原版的差异：
  *   - `S2mAgent.sinkId` 的类型由 `AddressMapping` 收窄为本地的 [[SizeMapping]]；
  *   - `S2mAgent.name` 由 `Nameable` 改为 `String`。
  */

case class S2mTransfers(probe: SizeRange = SizeRange.none,
                        arithmetic: SizeRange = SizeRange.none,
                        logical: SizeRange = SizeRange.none,
                        get: SizeRange = SizeRange.none,
                        putFull: SizeRange = SizeRange.none,
                        putPartial: SizeRange = SizeRange.none,
                        hint: SizeRange = SizeRange.none) {
  def withBCE = probe.some
  def withDataB = putFull.some || putPartial.some
  def withAny = probe.some || arithmetic.some || logical.some || get.some || putFull.some || putPartial.some || hint.some

  def intersect(rhs: S2mTransfers) = S2mTransfers(
    probe      = probe.intersect(rhs.probe),
    arithmetic = arithmetic.intersect(rhs.arithmetic),
    logical    = logical.intersect(rhs.logical),
    get        = get.intersect(rhs.get),
    putFull    = putFull.intersect(rhs.putFull),
    putPartial = putPartial.intersect(rhs.putPartial),
    hint       = hint.intersect(rhs.hint)
  )
  def mincover(rhs: S2mTransfers) = S2mTransfers(
    probe      = probe.mincover(rhs.probe),
    arithmetic = arithmetic.mincover(rhs.arithmetic),
    logical    = logical.mincover(rhs.logical),
    get        = get.mincover(rhs.get),
    putFull    = putFull.mincover(rhs.putFull),
    putPartial = putPartial.mincover(rhs.putPartial),
    hint       = hint.mincover(rhs.hint)
  )
  // Reduce rendering to a simple yes/no per field
  override def toString = {
    def str(x: SizeRange, flag: String) = if (x.none) "" else flag
    def flags = Vector(
      str(probe,      "P"),
      str(arithmetic, "A"),
      str(logical,    "L"),
      str(get,        "G"),
      str(putFull,    "F"),
      str(putPartial, "P"),
      str(hint,       "H"))
    flags.mkString
  }

  val sizeBytes = List(
    probe.max,
    arithmetic.max,
    logical.max,
    get.max,
    putFull.max,
    putPartial.max,
    hint.max
  ).max
}

object S2mTransfers {
  def unknownEmits = S2mTransfers(
    arithmetic = SizeRange(1, 4096),
    logical    = SizeRange(1, 4096),
    get        = SizeRange(1, 4096),
    putFull    = SizeRange(1, 4096),
    putPartial = SizeRange(1, 4096),
    hint       = SizeRange(1, 4096),
    probe      = SizeRange(1, 4096))
  def none = S2mTransfers(
    arithmetic = SizeRange.none,
    logical    = SizeRange.none,
    get        = SizeRange.none,
    putFull    = SizeRange.none,
    putPartial = SizeRange.none,
    hint       = SizeRange.none,
    probe      = SizeRange.none)
  def unknownSupports = S2mTransfers()
  def intersect(values: Seq[S2mTransfers]): S2mTransfers = values.reduce(_ intersect _)
  def mincover(values: Seq[S2mTransfers]): S2mTransfers = if (values.isEmpty) none else values.reduce(_ mincover _)
}

object S2mAgent {
  def simple(name: String) = S2mAgent(
    name   = name,
    sinkId = SizeMapping(0, 0),
    emits  = S2mTransfers()
  )
}

case class S2mAgent(name: String,
                    sinkId: SizeMapping,
                    emits: S2mTransfers) {
  val sinkWidth = sinkId.width
  def withSinkOffset(offset: Int): S2mAgent = {
    copy(sinkId = sinkId.withOffset(offset))
  }
}

object S2mParameters {
  def none(): S2mParameters = S2mParameters(
    List()
  )
}

case class S2mParameters(slaves: Seq[S2mAgent]) {
  def defaulted[T](default: T)(body: => T): T = if (slaves.isEmpty) default else body
  val sizeBytes = defaulted(0)(slaves.map(_.emits.sizeBytes).max)
  val sinkWidth = defaulted(0)(slaves.map(_.sinkWidth).max)
  val withBCE   = defaulted(false)(slaves.map(_.emits.withBCE).reduce(_ || _))
  def withDataB = defaulted(false)(slaves.map(_.emits.withDataB).reduce(_ || _))

  val emits = S2mTransfers.mincover(slaves.map(_.emits))
}

object S2mSupport {
  def apply(p: S2mParameters): S2mSupport = S2mSupport(
    transfers = p.emits
  )

  def none() = S2mSupport(
    transfers = S2mTransfers.none
  )
}

case class S2mSupport(transfers: S2mTransfers) {
  def mincover(that: S2mSupport): S2mSupport = {
    S2mSupport(
      transfers = transfers.mincover(that.transfers)
    )
  }

  def join(p: S2mParameters): S2mParameters = {
    S2mParameters(
      slaves = p.slaves
    )
  }
}
