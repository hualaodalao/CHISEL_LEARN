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
    asyncFifoDepth: Int = 0, toDatWidth: Int = 0, dvmForward: Boolean = false,
    outstanding: Int = 1)

/** System top: aggregates independent subsystem CHI networks joined by
  * inter-NoC bridges (HW-11/W13/SEM-20). Each subsystem keeps its own NodeID
  * space, SAM and clock domains; the bridge cuts the inter-NoC dependency ring
  * (G1 holds per subsystem). GEN supplies subsystem params + bridge placements
  * and only instantiates/wires; this module is the parameterized hardware top.
  *
  * v1 requires a uniform ChiConfig across subsystems (per-subsystem DAT width
  * differences are carried by the bridge toDatWidth gearbox). Bridge-consumed
  * SN/RN slots are wired internally; all other endpoint ports are exposed flat.
  *
  * HW-15: subDomain assigns each subsystem a system-level clock domain (0 = the
  * module clock, mirroring ChiNetworkParams.routerDomain). Each subsystem network
  * is instantiated under its own root clock, and a bridge runs in its fromSub
  * domain with dstClk = toSub's root clock -- so an async bridge really crosses
  * two different clocks. Sync bridges require both ends in the same domain;
  * async bridges require different domains. Default (all domain 0) keeps every
  * subsystem on the module clock and exposes no extra clock ports.
  *
  * Boundary port clock ownership: io.rn/io.sn entry k belongs to the clock domain
  * of the subsystem that owns that free slot.
  */
class ChiSystemTop(subsystems: Seq[ChiNetworkParams], bridges: Seq[ChiBridgeLink],
                   subDomain: Seq[Int] = Seq.empty,
                   mkNet: ChiNetworkParams => ChiNetworkLike = p => Module(new ChiNetworkUniform(p))) extends Module {
  require(subsystems.nonEmpty, "ChiSystemTop needs at least one subsystem")
  private val c = subsystems.head.cfg
  require(subsystems.forall(_.cfg == c), "ChiSystemTop v1: uniform ChiConfig across subsystems")
  require(subDomain.isEmpty || subDomain.length == subsystems.length,
    "subDomain must provide one clock domain per subsystem")
  private val domOfSub = if (subDomain.nonEmpty) subDomain else Seq.fill(subsystems.length)(0)
  private val nSubDom = domOfSub.max + 1
  for (b <- bridges) {
    val df = domOfSub(b.fromSub); val dt = domOfSub(b.toSub)
    if (b.asyncFifoDepth >= 2)
      require(df != dt, s"async bridge ${b.fromSub}->${b.toSub} needs different subDomain (both $df)")
    else
      require(df == dt, s"sync bridge ${b.fromSub}->${b.toSub} needs the same subDomain ($df vs $dt)")
    // Bridge endpoints are synthetic fabric-width native chi slots (SEM-20a),
    // so the bridge wiring is independent of any free-port width adaptation.
    require(ChiNodeWidths.nodeWidth(subsystems(b.fromSub), subsystems(b.fromSub).sn(b.fromSn)) == c.datWidth,
      "bridge from-slot must be fabric-width native chi")
    require(ChiNodeWidths.nodeWidth(subsystems(b.toSub), subsystems(b.toSub).rn(b.toRn)) == c.datWidth,
      "bridge to-slot must be fabric-width native chi")
  }

  private val usedSn = bridges.map(b => (b.fromSub, b.fromSn)).toSet
  private val usedRn = bridges.map(b => (b.toSub, b.toRn)).toSet
  private val freeRn = for (s <- subsystems.indices; i <- subsystems(s).rn.indices if !usedRn((s, i))) yield (s, i)
  private val freeSn = for (s <- subsystems.indices; j <- subsystems(s).sn.indices if !usedSn((s, j))) yield (s, j)
  private val nDomExtra = subsystems.map(_.nDomain - 1).sum

  val io = IO(new Bundle {
    val rn = MixedVec(freeRn.map { case (s, i) =>
      Flipped(new ChiRnPort(ChiNodeWidths.cfgOf(subsystems(s), subsystems(s).rn(i)))): Data })
    val sn = MixedVec(freeSn.map { case (s, j) =>
      Flipped(new ChiSnPort(ChiNodeWidths.cfgOf(subsystems(s), subsystems(s).sn(j)))): Data })
    val domClk = Vec(nDomExtra, Input(Bool()))
    val domReset = Vec(nDomExtra, Input(Bool()))
    val subClk = Vec(nSubDom - 1, Input(Bool()))
    val subReset = Vec(nSubDom - 1, Input(Bool()))
  })

  private def subClkOf(s: Int): Clock =
    if (domOfSub(s) == 0) clock else io.subClk(domOfSub(s) - 1).asClock
  private def subRstOf(s: Int): Reset =
    if (domOfSub(s) == 0) reset else io.subReset(domOfSub(s) - 1).asAsyncReset
  private def subClkBoolOf(s: Int): Bool =
    if (domOfSub(s) == 0) clock.asBool else io.subClk(domOfSub(s) - 1)
  private def subRstBoolOf(s: Int): Bool =
    if (domOfSub(s) == 0) reset.asBool else io.subReset(domOfSub(s) - 1)

  private val nets = subsystems.zipWithIndex.map { case (p, s) =>
    withClockAndReset(subClkOf(s), subRstOf(s)) { mkNet(p) }
  }

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
    // The bridge lives in its fromSub domain; its to side runs on toSub's clock.
    val br = withClockAndReset(subClkOf(b.fromSub), subRstOf(b.fromSub)) {
      Module(new ChiNocBridge(c, b.windows, fromNode, toNode, b.asyncFifoDepth, b.toDatWidth,
        b.dvmForward, b.outstanding))
    }
    nets(b.fromSub).io.sn(b.fromSn) <> br.io.from
    nets(b.toSub).io.rn(b.toRn) <> br.io.to
    br.io.dstClk := subClkBoolOf(b.toSub)
    br.io.dstReset := subRstBoolOf(b.toSub)
  }
}