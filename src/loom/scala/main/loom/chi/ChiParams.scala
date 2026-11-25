package loom.chi

import chisel3._
import chisel3.util._
import loom.ir.{AddressMap, Ir}

final case class ChiNode(id: String, kind: ChiNodeKind.Value, nodeId: Int, clock: String)

/** SAM entry: an address window mapped to a target Node ID. */
final case class ChiSamEntry(base: Long, size: Long, tgtId: Int) {
  def end: Long = base + size - 1
}

/** Elaborated CHI generation model derived from the loom/v1 IR.
  *
  * masters become RN-I, slaves become SN-F. Node IDs are assigned
  * deterministically: request nodes first (endpoint order), then subordinate
  * nodes. The SAM maps each slave window to that slave's Node ID, moving
  * address decode to the requester NI (target spec §10).
  */
final case class ChiParams(
    name: String,
    cfg: ChiConfig,
    rnNodes: Seq[ChiNode],
    snNodes: Seq[ChiNode],
    sam: Seq[ChiSamEntry]
) {
  require(rnNodes.nonEmpty, "CHI fabric needs at least one request node")
  require(snNodes.nonEmpty, "CHI fabric needs at least one subordinate node")
  private val allIds = (rnNodes ++ snNodes).map(_.nodeId)
  require(allIds.distinct.length == allIds.length, "Node IDs must be unique")
  require(allIds.forall(id => id >= 0 && id < cfg.maxNodes), "Node ID out of range")
  require(sam.forall(e => e.size >= 1), "SAM window size must be >= 1")

  def nRn: Int = rnNodes.length
  def nSn: Int = snNodes.length
}

object ChiParams {
  def fromIr(ir: Ir, cfg: ChiConfig = ChiConfig()): ChiParams = {
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")
    require(masters.nonEmpty && slaves.nonEmpty, "need at least one master and one slave")

    val rn = masters.zipWithIndex.map { case (e, i) =>
      ChiNode(e.id, ChiNodeKind.RnI, i, e.clock)
    }
    val snBase = rn.length
    val sn = slaves.zipWithIndex.map { case (e, j) =>
      ChiNode(e.id, ChiNodeKind.SnF, snBase + j, e.clock)
    }
    val windows = AddressMap.windows(slaves)
    val sam = windows.map(w => ChiSamEntry(w.base, w.size, snBase + w.index))

    ChiParams(ir.meta.name, cfg, rn, sn, sam)
  }
}

/** SAM window-comparison decoder: address -> target Node ID.
  * An unmapped address decodes to Node ID 0 in the skeleton; the error
  * response (NDERR/DECERR) is added with the protocol engine.
  */
object ChiSamDecode {
  def apply(addr: UInt, addrWidth: Int, sam: Seq[ChiSamEntry], outWidth: Int): UInt =
    withHit(addr, addrWidth, sam, outWidth)._1

  /** Decode plus a hit flag: an unmapped address yields hit=false so the NI can
    * return NDERR locally instead of silently routing to NodeID 0 (HW-05). */
  def withHit(addr: UInt, addrWidth: Int, sam: Seq[ChiSamEntry], outWidth: Int): (UInt, Bool) = {
    val dst = Wire(UInt(outWidth.W))
    val hit = Wire(Bool())
    dst := 0.U
    hit := false.B
    sam.foreach { e =>
      when(addr >= e.base.U(addrWidth.W) && addr <= e.end.U(addrWidth.W)) {
        dst := e.tgtId.U(outWidth.W)
        hit := true.B
      }
    }
    (dst, hit)
  }
}

/** Deterministic NodeID assignment (HW-01 / AQ-01): segment order RN -> SN ->
  * HN -> MN, within a segment by declaration order; an explicit override wins
  * and is reserved before auto IDs are handed out. Conflicts / out-of-range /
  * overflow are rejected. GEN calls this when assembling nodes[].kind.
  */
object ChiNodeIds {
  private def seg(k: ChiNodeKind.Value): Int =
    if (ChiNodeKind.isRequest(k)) 0
    else if (ChiNodeKind.isSubordinate(k)) 1
    else if (ChiNodeKind.isHome(k)) 2
    else 3

  def assign(kinds: Seq[ChiNodeKind.Value], overrides: Seq[Option[Int]], nodeIdWidth: Int): Seq[Int] = {
    require(kinds.length == overrides.length, "kinds/overrides length mismatch")
    val max = 1 << nodeIdWidth
    val fixed = overrides.flatten
    require(fixed.forall(id => id >= 0 && id < max), "NodeID override out of range")
    require(fixed.distinct.length == fixed.length, "duplicate NodeID override")
    val used = fixed.toSet
    val auto = Iterator.from(0).filterNot(used.contains)
    val result = Array.fill(kinds.length)(-1)
    overrides.zipWithIndex.foreach { case (o, i) => o.foreach(id => result(i) = id) }
    kinds.zipWithIndex.sortBy { case (k, i) => (seg(k), i) }.foreach { case (_, i) =>
      if (overrides(i).isEmpty) result(i) = auto.next()
    }
    require(result.forall(id => id >= 0 && id < max), "NodeID space overflow")
    require(result.distinct.length == result.length, "NodeID conflict")
    result.toSeq
  }
}