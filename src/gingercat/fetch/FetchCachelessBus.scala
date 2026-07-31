import chisel3._
import chisel3.util._

/** NaxRiscv FetchCachelessBus 的 Chisel 移植版。
  *
  * 用途：取指单元的无缓存总线（内部公共接口），并提供到 TileLink 的转换。
  *
  * 与 SpinalHDL 的差异：
  *   - 项目中没有 SpinalHDL 的 tilelink 库，这里内置了一个最小 TL-UL 子集
  *     （只含转换所需的 A/D 通道字段与 Get/AccessAckData 操作码）。
  *   - `withId generate UInt(...)` → `Option[UInt]`。
  *   - `DebugId.withPostfix`（SpinalHDL tilelink 的仿真调试 id 网络）未移植。
  *   - `CachelessBusToTilelink` 由 Area 改为普通类：在当前 Module 内展开硬件，
  *     转换结果挂在 `down` 上。
  */

// ============================================================================
// 内部公共总线（fetch cacheless bus）
// ============================================================================

case class CachelessBusParam(addressWidth: Int, dataWidth: Int, idCount: Int, cmdPersistence: Boolean) {
  val idWidth = log2Up(idCount)
  def toTilelinkM2s = TilelinkBusParam(addressWidth, dataWidth, idWidth)
}

class CachelessCmd(val p: CachelessBusParam) extends Bundle {
  val addr = UInt(p.addressWidth.W)
  val id = UInt(p.idWidth.W)
}

object CachelessCmd {
  def apply(p: CachelessBusParam) = new CachelessCmd(p)
}

class CachelessRsp(val p: CachelessBusParam, val withId: Boolean = true) extends Bundle {
  val id = if (withId) Some(UInt(p.idWidth.W)) else None
  val error = Bool()
  val word = UInt(p.dataWidth.W)
}

object CachelessRsp {
  def apply(p: CachelessBusParam, withId: Boolean = true) = new CachelessRsp(p, withId)
}

/**
 * The fetch CachelessBus has the following characteristics :
 * - address in byte, always aligned on the full data width
 * - supports out of order responses via the id signals
 * - Only one transaction per id can be inflight at a given time
 */
class CachelessBus(val p: CachelessBusParam) extends Bundle with IMasterSlave {
  val cmd = master(Stream(CachelessCmd(p)))
  val rsp = slave(Flow(CachelessRsp(p)))
}

object CachelessBus {
  def apply(p: CachelessBusParam) = new CachelessBus(p)
}

// ============================================================================
// 最小 TileLink（TL-UL 子集，仅覆盖 Get/AccessAckData）
// ============================================================================

case class TilelinkBusParam(addressWidth: Int, dataWidth: Int, sourceWidth: Int) {
  val maskWidth = dataWidth / 8
  val sizeWidth = log2Up(log2Up(maskWidth) + 1).max(1)
}

object TilelinkOpcode {
  object A {
    val GET = 4.U(3.W)
  }
  object D {
    val ACCESS_ACK_DATA = 1.U(3.W)
  }
}

/** A 通道（master → slave 请求） */
class TilelinkChannelA(val p: TilelinkBusParam) extends Bundle {
  val opcode = UInt(3.W)
  val param = UInt(3.W)
  val size = UInt(p.sizeWidth.W)
  val source = UInt(p.sourceWidth.W)
  val address = UInt(p.addressWidth.W)
  val mask = UInt(p.maskWidth.W)
  val data = UInt(p.dataWidth.W)
  val corrupt = Bool()
}

/** D 通道（slave → master 响应） */
class TilelinkChannelD(val p: TilelinkBusParam) extends Bundle {
  val opcode = UInt(3.W)
  val param = UInt(2.W)
  val size = UInt(p.sizeWidth.W)
  val source = UInt(p.sourceWidth.W)
  val denied = Bool()
  val data = UInt(p.dataWidth.W)
  val corrupt = Bool()
}

/** TileLink 总线（master 视角：a 为发出的请求流，d 为收回的响应流） */
class TilelinkBus(val p: TilelinkBusParam) extends Bundle with IMasterSlave {
  val a = master(Stream(new TilelinkChannelA(p)))
  val d = slave(Stream(new TilelinkChannelD(p)))
}

object TilelinkBus {
  def apply(p: TilelinkBusParam) = new TilelinkBus(p)
}

// ============================================================================
// CachelessBus → TileLink 转换
// ============================================================================

/** 把内部 CachelessBus（up）转换为 TileLink master（down）。
  * 在当前 Module 内展开硬件，`down` 接到外部 TileLink 端口即可。
  */
class CachelessBusToTilelink(up: CachelessBus) {
  require(up.p.cmdPersistence)
  val m2sParam = up.p.toTilelinkM2s
  val down = Wire(TilelinkBus(m2sParam))

  // cmd → a：恒为对齐的全宽 Get
  down.a.valid := up.cmd.valid
  up.cmd.ready := down.a.ready
  down.a.payload.opcode := TilelinkOpcode.A.GET
  down.a.payload.param := 0.U
  down.a.payload.source := up.cmd.payload.id
  down.a.payload.address := up.cmd.payload.addr
  down.a.payload.size := log2Up(up.p.dataWidth / 8).U
  down.a.payload.mask := ((1 << m2sParam.maskWidth) - 1).U
  down.a.payload.data := 0.U
  down.a.payload.corrupt := false.B

  // d → rsp：无反压，直接透传
  down.d.ready := true.B
  up.rsp.valid := down.d.valid
  up.rsp.payload.id.foreach(_ := down.d.payload.source)
  up.rsp.payload.error := down.d.payload.denied
  up.rsp.payload.word := down.d.payload.data
}
