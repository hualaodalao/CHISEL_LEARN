package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** Inter-NoC bridge placement (HW-11/W13, mirrors GEN BridgeParams). The bridge
  * terminates as a subordinate on fromSub's SN slot `fromSn` and re-originates
  * as a request node on toSub's RN slot `toRn`. Those two slots are consumed by
  * the bridge and are not exposed at the system boundary. */
final case class ChiBridgeLink(
    fromSub: Int, fromSn: Int, toSub: Int, toRn: Int,
    windows: Seq[(Long, Long)],
    asyncFifoDepth: Int = 0, toDatWidth: Int = 0, dvmForward: Boolean = false)

/** System top: aggregates independent subsystem CHI networks joined by
  * inter-NoC bridges (HW-11/W13/SEM-20). Each subsystem keeps its own NodeID
  * space, SAM and clock domains; the bridge cuts the inter-NoC dependency ring
  * (G1 holds per subsystem). GEN supplies subsystem params + bridge placements
  * and only instantiates/wires; this module is the parameterized hardware top.
  *
  * v1 requires a uniform ChiConfig across subsystems (per-subsystem DAT width
  * differences are carried by the bridge toDatWidth gearbox). Bridge-consumed
  * SN/RN slots are wired internally; all other endpoint ports are exposed flat.
  */
class ChiSystemTop(subsystems: Seq[ChiNetworkParams], bridges: Seq[ChiBridgeLink]) extends Module {
  require(subsystems.nonEmpty, "ChiSystemTop needs at least one subsystem")
  private val c = subsystems.head.cfg
  require(subsystems.forall(_.cfg == c), "ChiSystemTop v1: uniform ChiConfig across subsystems")

  private val usedSn = bridges.map(b => (b.fromSub, b.fromSn)).toSet
  private val usedRn = bridges.map(b => (b.toSub, b.toRn)).toSet
  private val freeRn = for (s <- subsystems.indices; i <- subsystems(s).rn.indices if !usedRn((s, i))) yield (s, i)
  private val freeSn = for (s <- subsystems.indices; j <- subsystems(s).sn.indices if !usedSn((s, j))) yield (s, j)
  private val nDomExtra = subsystems.map(_.nDomain - 1).sum

  val io = IO(new Bundle {
    val rn = Vec(freeRn.size, Flipped(new ChiRnPort(c)))
    val sn = Vec(freeSn.size, Flipped(new ChiSnPort(c)))
    val domClk = Vec(nDomExtra, Input(Bool()))
    val domReset = Vec(nDomExtra, Input(Bool()))
  })

  private val nets = subsystems.map(p => Module(new ChiNetworkTop(p)))

  private var off = 0
  for (s <- subsystems.indices) {
    val extra = subsystems(s).nDomain - 1
    for (d <- 0 until extra) {
      nets(s).io.domClk(d) := io.domClk(off + d)
      nets(s).io.domReset(d) := io.domReset(off + d)
    }
    off += extra
  }

  for (((s, i), k) <- freeRn.zipWithIndex) nets(s).io.rn(i) <> io.rn(k)
  for (((s, j), k) <- freeSn.zipWithIndex) nets(s).io.sn(j) <> io.sn(k)

  for (b <- bridges) {
    val fromNode = subsystems(b.fromSub).sn(b.fromSn).nodeId
    val toNode = subsystems(b.toSub).rn(b.toRn).nodeId
    val br = Module(new ChiNocBridge(c, b.windows, fromNode, toNode, b.asyncFifoDepth, b.toDatWidth, b.dvmForward))
    nets(b.fromSub).io.sn(b.fromSn) <> br.io.from
    nets(b.toSub).io.rn(b.toRn) <> br.io.to
    if (b.asyncFifoDepth >= 2) {
      br.io.dstClk := io.domClk(0)
      br.io.dstReset := io.domReset(0)
    } else {
      br.io.dstClk := false.B
      br.io.dstReset := false.B
    }
  }
}