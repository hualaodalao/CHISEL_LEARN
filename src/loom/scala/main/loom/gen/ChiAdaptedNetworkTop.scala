package loom.gen

import chisel3._
import chisel3.util._
import loom.chi._
import loom.rtl.LmWire

/** GEN-owned adaptation wrapper (HW-12/SEM-19, dependency list §9.3).
  *
  * Instantiates the inner ChiNetworkTop (all-chi) or ChiAxiNetworkTop (any
  * axi4 node) and inserts one ChiNiAdapt per DAT direction at every
  * width-mismatched CHI-protocol node boundary (node.dataWidth !=
  * chi.datWidth). All other ports pass through unchanged; AXI nodes keep
  * bridge-layer width adaptation (no double adaptation). This is sanctioned
  * GEN instantiation/wiring — the W19 thin-wrapper ruling keeps ChiNetworkTop
  * unmodified and the gearbox insertion GEN-owned (dependency list §16).
  *
  * DAT flit <-> ChiNiBeat mapping (dataId alignment, PS DAT conventions):
  *  - DataID counts 16B units within the 64B line (ChiWidths.dataIdWidth=2;
  *    ChiReadSource/ChiWriteSink use `dataId * 128` placement), so a flit of
  *    width W sits at dataId in steps of W/128 and the final line chunk has
  *    dataId = (512-W)/128. That final-chunk position drives ChiNiBeat.last.
  *  - Upsize (node narrower): ratio consecutive node beats pack low-first
  *    into one fabric flit; the fabric flit takes the FIRST beat's DataID
  *    and the concatenation of the beats' BE lanes.
  *  - Downsize (node wider): one flit splits low-first into ratio beats;
  *    beat k takes dataId = flit.dataId + k*(outW/128) and BE lane k.
  *  - Scope: full 64B transactions with in-order low-first DAT beats (the
  *    phase-1 chi engines ChiReadSource/ChiWriteSink are 64B-only). Node
  *    widths must be CHI flit tiers (128/256/512) so DataID stays expressible
  *    in 16B units; other declared widths stay fabric-width (pending).
  *
  * Frequency-mismatch adaptation is NOT inserted here: HW-14 first version
  * keeps local nodes on domain-0 routers (CHK-54) and node-boundary CDC is
  * pending; the ChiNiAdapt instances run synchronously (async=false).
  *
  * GEN-26 (HW-16/W19): implements ChiNetworkLike so emitSystem can select it
  * per subsystem through the ChiSystemTop mkNet factory; the io keeps the
  * exact MixedVec per-port construction and node widths follow the shared
  * ChiNodeWidths rule so the system boundary and the wrapper never drift.
  */
class ChiAdaptedNetworkTop(p: ChiNetworkParams) extends ChiNetworkLike(p) {
  private val c = p.cfg
  private val adaptOf: Map[Int, ChiAdapt] = p.adapt.map(a => a.nodeId -> a).toMap

  private def nodeWidth(n: ChiNetNode): Int = ChiNodeWidths.nodeWidth(p, n)

  private def cfgOf(n: ChiNetNode): ChiConfig = ChiNodeWidths.cfgOf(p, n)

  private val anyAxi = (p.rn ++ p.sn).exists(_.protocol == "axi4")
  // ChiAxiNetworkTop does not expose the inner domClk/domReset ports, so a
  // multi-domain flat fabric with AXI boundaries cannot be wired yet.
  require(!anyAxi || p.nDomain == 1,
    "ChiAdaptedNetworkTop: flat async (nDomain>1) with axi4 nodes needs ChiAxiNetworkTop domClk/domReset exposure (pending)")

  override val io = IO(new ChiNetworkIO {
    val rn = MixedVec(p.rn.map { n =>
      if (n.protocol == "axi4") new ChiAxiRnPort(c): Data
      else Flipped(new ChiRnPort(cfgOf(n)))
    })
    val sn = MixedVec(p.sn.map { n =>
      if (n.protocol == "axi4") new ChiAxiSnPort(c): Data
      else Flipped(new ChiSnPort(cfgOf(n)))
    })
    val domClk = Vec(p.nDomain - 1, Input(Bool()))
    val domReset = Vec(p.nDomain - 1, Input(Bool()))
  })

  private val innerNet: Option[ChiNetworkTop] =
    if (anyAxi) None else Some(Module(new ChiNetworkTop(p)))
  private val innerAxi: Option[ChiAxiNetworkTop] =
    if (anyAxi) Some(Module(new ChiAxiNetworkTop(p))) else None
  innerNet.foreach { n =>
    n.io.domClk := io.domClk
    n.io.domReset := io.domReset
  }

  // Chi port order inside ChiAxiNetworkTop (declaration order of its Vecs).
  private val rnAxiIdx = p.rn.zipWithIndex.filter(_._1.protocol == "axi4").map(_._2)
  private val rnChiIdx = p.rn.zipWithIndex.filter(_._1.protocol != "axi4").map(_._2)
  private val snAxiIdx = p.sn.zipWithIndex.filter(_._1.protocol == "axi4").map(_._2)
  private val snChiIdx = p.sn.zipWithIndex.filter(_._1.protocol != "axi4").map(_._2)

  private def rnFabric(i: Int): ChiRnPort = innerNet match {
    case Some(n) => n.io.rn(i)
    case None    => innerAxi.get.io.rnChi(rnChiIdx.indexOf(i))
  }
  private def snFabric(j: Int): ChiSnPort = innerNet match {
    case Some(n) => n.io.sn(j)
    case None    => innerAxi.get.io.snChi(snChiIdx.indexOf(j))
  }

  /** FIFO depth normalized to a power of two >= 2 (ChiNiAdapt require);
    * CHK-53 allows any [2,64] declaration, GEN rounds up for elaboration.
    */
  private def depthOf(a: ChiAdapt): Int = {
    var d = 2
    while (d < a.fifoDepth) d <<= 1
    d
  }

  /** Final 64B-line chunk position of a width-W flit, in 16B DataID units. */
  private def lastIdOf(w: Int): Int = (512 - w) / 128

  /** One DAT direction through ChiNiAdapt with flit<->beat sideband mapping.
    * src carries width-inW flits, dst width-outW flits (same ChiDatFlit
    * field set, different data/BE widths).
    */
  private def adaptDat(src: DecoupledIO[ChiDatFlit], dst: DecoupledIO[ChiDatFlit],
                       inW: Int, outW: Int, depth: Int): Unit = {
    val ad = Module(new ChiNiAdapt(inW, outW, depth, async = false))
    ad.io.dstClk := clock.asBool
    ad.io.dstReset := reset.asBool
    ad.io.in.valid := src.valid
    ad.io.in.bits.data := src.bits.data
    ad.io.in.bits.last := src.bits.dataId === lastIdOf(inW).U
    src.ready := ad.io.in.ready
    ad.io.out.ready := dst.ready
    dst.valid := ad.io.out.valid
    if (inW < outW) {
      // Upsize: ratio input beats -> one output flit. ChiNiWidth asserts
      // out.valid on the LAST input beat, so the current src sideband is
      // valid at output time; DataID/BE come from the first beat (low-first).
      val ratio = outW / inW
      val inBe = inW / 8
      val outBe = outW / 8
      val cnt = RegInit(0.U(math.max(1, log2Ceil(ratio)).W))
      val firstId = RegInit(0.U(ChiWidths.dataIdWidth.W))
      val beBuf = RegInit(0.U(outBe.W))
      when(ad.io.in.fire) {
        when(cnt === 0.U) {
          firstId := src.bits.dataId
          beBuf := src.bits.be.pad(outBe)
        }.elsewhen(cnt =/= (ratio - 1).U) {
          beBuf := beBuf | (src.bits.be.pad(outBe) << (cnt * inBe.U))
        }
        cnt := Mux(cnt === (ratio - 1).U, 0.U, cnt + 1.U)
      }
      dst.bits := src.bits
      dst.bits.data := ad.io.out.bits.data
      dst.bits.dataId := firstId
      dst.bits.be := beBuf | (src.bits.be.pad(outBe) << ((ratio - 1) * inBe).U)
    } else {
      // Downsize: one input flit -> ratio output beats. Latch the input
      // sideband; beat k advances DataID by outW/128 and selects BE lane k.
      val ratio = inW / outW
      val outBe = outW / 8
      val cnt = RegInit(0.U(math.max(1, log2Ceil(ratio)).W))
      val side = Reg(chiselTypeOf(src.bits))
      when(ad.io.in.fire) { side := src.bits; cnt := 0.U }
      dst.bits := side
      dst.bits.data := ad.io.out.bits.data
      dst.bits.dataId := side.dataId + cnt * (outW / 128).U
      dst.bits.be := (side.be >> (cnt * outBe.U))(outBe - 1, 0)
      when(ad.io.out.fire) { cnt := cnt + 1.U }
    }
  }

  // AXI boundaries: pure pass-through to the inner bridge ports.
  for ((ni, k) <- rnAxiIdx.zipWithIndex) {
    val a = io.rn(ni).asInstanceOf[ChiAxiRnPort]
    val b = innerAxi.get.io.rnAxi(k)
    LmWire.link(a.ar, b.ar); LmWire.link(b.r, a.r)
    LmWire.link(a.aw, b.aw); LmWire.link(a.w, b.w); LmWire.link(b.b, a.b)
  }
  for ((nj, k) <- snAxiIdx.zipWithIndex) {
    val a = io.sn(nj).asInstanceOf[ChiAxiSnPort]
    val b = innerAxi.get.io.snAxi(k)
    LmWire.link(b.ar, a.ar); LmWire.link(a.r, b.r)
    LmWire.link(b.aw, a.aw); LmWire.link(b.w, a.w); LmWire.link(a.b, b.b)
  }

  // CHI RN boundaries: REQ/RSP/SNP/txRsp pass through; DAT adapts when the
  // node bus width mismatches the fabric flit width.
  for ((n, i) <- p.rn.zipWithIndex if n.protocol != "axi4") {
    val ext = io.rn(i).asInstanceOf[ChiRnPort]
    val fab = rnFabric(i)
    LmWire.link(ext.req, fab.req)
    LmWire.link(fab.rsp, ext.rsp)
    LmWire.link(fab.snp, ext.snp)
    LmWire.link(ext.txRsp, fab.txRsp)
    if (nodeWidth(n) != c.datWidth) {
      val a = adaptOf(n.nodeId)
      adaptDat(ext.txDat, fab.txDat, a.dataWidth, c.datWidth, depthOf(a))
      adaptDat(fab.dat, ext.dat, c.datWidth, a.dataWidth, depthOf(a))
    } else {
      LmWire.link(ext.txDat, fab.txDat)
      LmWire.link(fab.dat, ext.dat)
    }
  }

  // CHI SN boundaries: dat is SN->fabric read data, rxDat fabric->SN write data.
  for ((n, j) <- p.sn.zipWithIndex if n.protocol != "axi4") {
    val ext = io.sn(j).asInstanceOf[ChiSnPort]
    val fab = snFabric(j)
    LmWire.link(fab.req, ext.req)
    LmWire.link(ext.rsp, fab.rsp)
    if (nodeWidth(n) != c.datWidth) {
      val a = adaptOf(n.nodeId)
      adaptDat(ext.dat, fab.dat, a.dataWidth, c.datWidth, depthOf(a))
      adaptDat(fab.rxDat, ext.rxDat, c.datWidth, a.dataWidth, depthOf(a))
    } else {
      LmWire.link(ext.dat, fab.dat)
      LmWire.link(fab.rxDat, ext.rxDat)
    }
  }
}

object ChiAdaptedNetworkTop {
  /** Node bus widths expressible as CHI flit tiers (DataID in 16B units);
    * aliased to the shared rule so wrapper and system boundary never drift. */
  val SupportedNodeWidths: Set[Int] = ChiNodeWidths.Supported

  /** True when at least one chi-protocol RN/SN node needs GEN-side DAT width
    * adaptation (HW-12 §9.3); selects the wrapper as the emitted flat top and
    * as the per-subsystem mkNet choice in hierarchical emission (GEN-26).
    */
  def needed(p: ChiNetworkParams): Boolean =
    (p.rn ++ p.sn).exists(n => ChiNodeWidths.nodeWidth(p, n) != p.cfg.datWidth)
}