package loom.chi

import chisel3._
import chisel3.util._

/** Shared subsystem-network port contract (HW-16). Declared with abstract vals
  * so an implementer can instantiate anonymously -- `IO(new ChiNetworkIO { val
  * rn = MixedVec(...) ... })` -- and keep its own per-port construction logic.
  *
  * Elements are `Data` because a subsystem may expose per-node DAT widths
  * (width-adapted chi nodes) or protocol-specific ports; callers that know the
  * concrete type cast at the access site.
  */
abstract class ChiNetworkIO extends Bundle {
  val rn: MixedVec[Data]
  val sn: MixedVec[Data]
  val domClk: Vec[Bool]
  val domReset: Vec[Bool]
}

/** A subsystem network top usable by ChiSystemTop. GEN supplies width-adapted
  * implementations through the ChiSystemTop mkNet factory; hardware never
  * imports loom.gen. The factory is responsible for the Module() wrapping. */
abstract class ChiNetworkLike(val p: ChiNetworkParams) extends Module {
  val io: ChiNetworkIO
}

/** Node DAT boundary width rules shared by the system boundary and any
  * adaptation wrapper, so both agree on a free port's concrete width. A node is
  * adapted only when it is chi-protocol, declared mismatched, and its bus width
  * is a CHI flit tier different from the fabric width.
  */
object ChiNodeWidths {
  val Supported: Set[Int] = Set(128, 256, 512)

  def nodeWidth(p: ChiNetworkParams, n: ChiNetNode): Int =
    p.adapt.find(_.nodeId == n.nodeId).filter { a =>
      n.protocol != "axi4" && a.mismatch && a.dataWidth != p.cfg.datWidth &&
        Supported.contains(a.dataWidth)
    }.map(_.dataWidth).getOrElse(p.cfg.datWidth)

  def cfgOf(p: ChiNetworkParams, n: ChiNetNode): ChiConfig = {
    val w = nodeWidth(p, n)
    if (w == p.cfg.datWidth) p.cfg else p.cfg.copy(datWidth = w)
  }
}

/** Uniform subsystem top: exposes the ChiNetworkIO contract over an unmodified
  * ChiNetworkTop (every node at fabric width). This is the default ChiSystemTop
  * factory; the MixedVec <-> Vec narrowing stays here so ChiNetworkTop keeps its
  * strongly-typed io.
  */
class ChiNetworkUniform(p: ChiNetworkParams) extends ChiNetworkLike(p) {
  private val c = p.cfg
  override val io = IO(new ChiNetworkIO {
    val rn = MixedVec(p.rn.map(_ => Flipped(new ChiRnPort(c)): Data))
    val sn = MixedVec(p.sn.map(_ => Flipped(new ChiSnPort(c)): Data))
    val domClk = Vec(p.nDomain - 1, Input(Bool()))
    val domReset = Vec(p.nDomain - 1, Input(Bool()))
  })
  private val net = Module(new ChiNetworkTop(p))
  for (i <- p.rn.indices) io.rn(i) <> net.io.rn(i)
  for (j <- p.sn.indices) io.sn(j) <> net.io.sn(j)
  for (d <- 0 until p.nDomain - 1) {
    net.io.domClk(d) := io.domClk(d)
    net.io.domReset(d) := io.domReset(d)
  }
}