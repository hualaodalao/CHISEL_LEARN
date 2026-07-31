import chisel3._
import chisel3.util._

/** TileLink master 侧参数体系，移植自 SpinalHDL spinal.lib.bus.tilelink.Master。
  *
  * 与原版的差异：
  *   - `M2sSource.id` 的类型由 `AddressMapping` 收窄为本地的 [[SizeMapping]]；
  *   - `M2sAgent.name` 由 `Nameable` 改为 `String`（仅作标识）；
  *   - 未继承 SpinalHDL 的 `MemoryTransfers` / `OverridedEqualsHashCode`
  *     （case class 采用结构相等，本项目未依赖引用相等语义）。
  */

case class M2sTransfers(acquireT: SizeRange = SizeRange.none,
                        acquireB: SizeRange = SizeRange.none,
                        arithmetic: SizeRange = SizeRange.none,
                        logical: SizeRange = SizeRange.none,
                        get: SizeRange = SizeRange.none,
                        putFull: SizeRange = SizeRange.none,
                        putPartial: SizeRange = SizeRange.none,
                        hint: SizeRange = SizeRange.none) {

  def isOnlyGetPut() = List(acquireT, acquireB, arithmetic, logical, hint).forall(_.none)

  def allowA(opcode: Opcode.A.Type): Boolean = opcode match {
    case Opcode.A.PUT_FULL_DATA    => putFull.some
    case Opcode.A.PUT_PARTIAL_DATA => putPartial.some
    case Opcode.A.GET              => get.some
    case Opcode.A.ACQUIRE_BLOCK    => withBCE
    case Opcode.A.ACQUIRE_PERM     => withBCE
  }
  def allowC(opcode: Opcode.C.Type): Boolean = withBCE

  def withBCE = acquireT.some || acquireB.some
  def withDataA = putFull.some || putPartial.some
  def withDataD = get.some || acquireT.some || acquireB.some || logical.some || arithmetic.some
  def withAny = withDataA || withDataD || withBCE || hint.some

  def intersect(rhs: M2sTransfers) = M2sTransfers(
    acquireT   = acquireT.intersect(rhs.acquireT),
    acquireB   = acquireB.intersect(rhs.acquireB),
    arithmetic = arithmetic.intersect(rhs.arithmetic),
    logical    = logical.intersect(rhs.logical),
    get        = get.intersect(rhs.get),
    putFull    = putFull.intersect(rhs.putFull),
    putPartial = putPartial.intersect(rhs.putPartial),
    hint       = hint.intersect(rhs.hint))
  def mincover(rhs: M2sTransfers) = M2sTransfers(
    acquireT   = acquireT.mincover(rhs.acquireT),
    acquireB   = acquireB.mincover(rhs.acquireB),
    arithmetic = arithmetic.mincover(rhs.arithmetic),
    logical    = logical.mincover(rhs.logical),
    get        = get.mincover(rhs.get),
    putFull    = putFull.mincover(rhs.putFull),
    putPartial = putPartial.mincover(rhs.putPartial),
    hint       = hint.mincover(rhs.hint))

  def isEmpty: Boolean = !nonEmpty
  def nonEmpty: Boolean = withAny

  // Reduce rendering to a simple yes/no per field
  override def toString = {
    def str(x: SizeRange, flag: String) = if (x.none) "" else flag
    def flags = Vector(
      str(acquireT,   "T"),
      str(acquireB,   "B"),
      str(arithmetic, "A"),
      str(logical,    "L"),
      str(get,        "G"),
      str(putFull,    "F"),
      str(putPartial, "P"),
      str(hint,       "H"))
    flags.mkString
  }

  val sizeBytes = List(
    acquireT.max,
    acquireB.max,
    arithmetic.max,
    logical.max,
    get.max,
    putFull.max,
    putPartial.max
  ).max

  /** 硬件谓词：给定 A 通道 opcode 是否被支持（对应原版 contains(Opcode.A.C)） */
  def contains(opcode: Opcode.A.Type): Bool = {
    MuxLookup(opcode.asUInt, false.B)(Seq(
      Opcode.A.GET.asUInt              -> get.some.B,
      Opcode.A.PUT_FULL_DATA.asUInt    -> putFull.some.B,
      Opcode.A.PUT_PARTIAL_DATA.asUInt -> putPartial.some.B,
      Opcode.A.ACQUIRE_BLOCK.asUInt    -> (acquireB.some || acquireT.some).B,
      Opcode.A.ACQUIRE_PERM.asUInt     -> (acquireB.some || acquireT.some).B
    ))
  }
}

object M2sTransfers {
  def all = M2sTransfers(
    acquireT   = SizeRange(1, 4096),
    acquireB   = SizeRange(1, 4096),
    arithmetic = SizeRange(1, 4096),
    logical    = SizeRange(1, 4096),
    get        = SizeRange(1, 4096),
    putFull    = SizeRange(1, 4096),
    putPartial = SizeRange(1, 4096),
    hint       = SizeRange(1, 4096)
  )

  def singleSize(size: Int) = M2sTransfers(
    acquireT   = SizeRange(size),
    acquireB   = SizeRange(size),
    arithmetic = SizeRange(size),
    logical    = SizeRange(size),
    get        = SizeRange(size),
    putFull    = SizeRange(size),
    putPartial = SizeRange(size),
    hint       = SizeRange(size)
  )

  def allGetPut = M2sTransfers(
    get        = SizeRange(1, 4096),
    putFull    = SizeRange(1, 4096),
    putPartial = SizeRange(1, 4096)
  )
  def allGetPut(range: SizeRange) = M2sTransfers(
    get        = range,
    putFull    = range,
    putPartial = range
  )

  def intersect(values: Seq[M2sTransfers]): M2sTransfers = values.reduce(_ intersect _)
  def mincover(values: Seq[M2sTransfers]): M2sTransfers = values.reduce(_ mincover _)
}

case class M2sSource(id: SizeMapping,
                     emits: M2sTransfers) {
  def withSourceOffset(offset: Int) = copy(id = id.withOffset(offset))
  def bSourceId = id.lowerBound.toInt
}

object M2sAgent {
  def apply(name: String, mapping: M2sSource): M2sAgent = M2sAgent(
    name = name,
    mapping = List(mapping)
  )
}

case class M2sAgent(name: String,
                    mapping: Seq[M2sSource]) {
  def withSourceOffset(offset: Int): M2sAgent = {
    copy(mapping = mapping.map(_.withSourceOffset(offset)))
  }
  val emits = M2sTransfers.mincover(mapping.map(_.emits))
  val sourceWidth = mapping.map(_.id.width).max
  def bSourceId = mapping.head.bSourceId
  def sourceHit(source: UInt): Bool = mapping.map(_.id.hit(source)).reduce(_ || _)
  def sourceHit(source: Int): Boolean = mapping.exists(_.id.hit(BigInt(source)))
  def remapSources(f: M2sSource => M2sSource) = {
    copy(mapping = mapping.map(f))
  }
}

object M2sParameters {
  def apply(support: M2sSupport, sourceCount: Int): M2sParameters = apply(support, sourceCount, "")
  def apply(support: M2sSupport, sourceCount: Int, name: String): M2sParameters = M2sParameters(
    addressWidth = support.addressWidth,
    dataWidth = support.dataWidth,
    masters = List(M2sAgent(
      name = name,
      mapping = List(M2sSource(
        id = SizeMapping(0, sourceCount),
        emits = support.transfers
      ))
    ))
  )
}

case class M2sParameters(addressWidth: Int,
                         dataWidth: Int,
                         masters: Seq[M2sAgent]) {
  val sizeBytes = masters.map(_.emits.sizeBytes).max
  val sourceWidth = masters.map(_.sourceWidth).max
  val withBCE = masters.map(_.emits.withBCE).reduce(_ || _)
  val emits = M2sTransfers.mincover(masters.map(_.emits))
  def dataBytes = dataWidth / 8
  def withDataA = masters.map(_.emits.withDataA).reduce(_ || _)
  def withDataD = masters.map(_.emits.withDataD).reduce(_ || _)
  def sourceHit(source: UInt): Bool = masters.map(_.sourceHit(source)).reduce(_ || _)
  def getMasterFromSource(source: Int): M2sAgent =
    masters.find(_.mapping.exists(_.id.hit(BigInt(source)))).orNull
  def remapSources(f: M2sSource => M2sSource) = {
    copy(masters = masters.map(_.remapSources(f)))
  }
  def toSupport() = M2sSupport(
    transfers    = emits,
    addressWidth = addressWidth,
    dataWidth    = dataWidth
  )

  def toNodeParameters() = NodeParameters(this)
  def toBusParameter() = toNodeParameters().toBusParameter()
}

object M2sSupport {
  def apply(p: M2sParameters): M2sSupport = M2sSupport(
    transfers    = p.emits,
    addressWidth = p.addressWidth,
    dataWidth    = p.dataWidth
  )
}

case class M2sSupport(transfers: M2sTransfers,
                      addressWidth: Int,
                      dataWidth: Int) {
  def mincover(that: M2sSupport): M2sSupport = {
    M2sSupport(
      transfers = transfers.mincover(that.transfers),
      dataWidth = dataWidth max that.dataWidth,
      addressWidth = addressWidth max that.addressWidth
    )
  }

  def join(p: M2sParameters): M2sParameters = {
    M2sParameters(
      addressWidth = addressWidth,
      dataWidth    = dataWidth,
      masters = p.masters
    )
  }

  def intersect(that: M2sTransfers): M2sSupport = copy(transfers = transfers.intersect(that))

  def withAddressWidth(w: Int): M2sSupport = copy(addressWidth = w)
  def dataBytes = dataWidth / 8
}
