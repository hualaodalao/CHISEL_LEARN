package loom.chi

import loom.ir.Ir

/** CHI generation parameters (target spec §9). Defaults follow the closed
  * rulings D5/D12; every field is configurable within the validated range.
  */
final case class ChiConfig(
    datWidth: Int = 256,
    txnIdWidth: Int = 8,
    outstanding: Int = 64,
    nodeIdWidth: Int = 7,
    addrWidth: Int = 48,
    atomicEnable: Boolean = true,
    atomicEntries: Int = 4,
    dvmEnable: Boolean = true,
    portMode: String = "axi4",
    pcrdTypes: Int = 1,
    lcreditDepth: Int = 4,
    qosCtlThreshold: Int = 8,
    qosLatThreshold: Int = 4,
    qosWeights: Seq[Int] = Seq(4, 2, 1),
    nHalf: Int = 2
) {
  require(Set(128, 256, 512).contains(datWidth),
    s"CHI DAT width must be 128/256/512 (no 64b), got $datWidth")
  require(Set(8, 12).contains(txnIdWidth), s"TxnID width must be 8/12, got $txnIdWidth")
  require(outstanding >= 1, "outstanding must be >= 1")
  require(Set(7, 11).contains(nodeIdWidth), s"NodeID width must be 7/11, got $nodeIdWidth")
  require(Set(32, 40, 48).contains(addrWidth), s"addr width must be 32/40/48, got $addrWidth")
  require(atomicEntries >= 1, "atomicEntries must be >= 1")
  require(Set("axi4", "chi", "both").contains(portMode), s"unknown portMode $portMode")
  require(pcrdTypes >= 1, "pcrdTypes must be >= 1")
  require(lcreditDepth >= 1 && lcreditDepth <= 15, "L-Credit depth must be in [1,15]")
  require(qosCtlThreshold >= 0 && qosCtlThreshold <= 15, "qosCtlThreshold must be in [0,15]")
  require(qosLatThreshold >= 0 && qosLatThreshold <= 15, "qosLatThreshold must be in [0,15]")
  require(qosCtlThreshold > qosLatThreshold, "ctl threshold must exceed lat threshold (no gap/overlap)")
  require(qosWeights.length == 3, "qosWeights must have 3 entries (ctl/lat/bulk)")
  require(qosWeights.forall(w => w >= 1 && w <= 64), "qos weight must be in [1,64]")
  require(nHalf >= 1, "nHalf must be >= 1 (topology-derived)")

  val beWidth: Int = ChiWidths.beWidth(datWidth)
  val maxNodes: Int = 1 << nodeIdWidth
  val nClasses: Int = 3
  val nVc: Int = nClasses * nHalf
  val vcWidth: Int = chisel3.util.log2Ceil(nVc)
}

object ChiConfig {
  /** Derive a CHI config from the existing loom/v1 IR without changing its
    * schema (D7). Fabric DAT width stays at the 256b default; endpoint width
    * may be narrower and is adapted at the NI in a later story.
    */
  def fromIr(ir: Ir): ChiConfig = {
    val masters = ir.endpoints.filter(_.role == "master")
    require(masters.nonEmpty, "CHI path needs at least one master endpoint")
    val addr = masters.head.addrWidth
    ChiConfig(
      addrWidth = if (Set(32, 40, 48).contains(addr)) addr else 48,
      outstanding = math.max(64, ir.endpoints.map(_.outstanding).max)
    )
  }
}