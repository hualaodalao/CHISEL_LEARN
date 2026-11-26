package loom.rtl

import loom.ir.{Ir, LoomDefaults}

/** Elaboration-time view of a fabric.
  *
  * S4 supports one flat router only: one router, in = #masters,
  * out = #slaves, power-of-two radix. Each master carries a static QoS
  * class; the router arbitrates requests per class and then selects a
  * class with a weighted round-robin schedule.
  */
final case class FabricParams(
    name: String,
    nMaster: Int,
    nSlave: Int,
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    slaveBase: Seq[Long],
    slaveSize: Seq[Long],
    masterMnet: Seq[Int],
    nMnet: Int,
    slaveSnet: Seq[Int],
    nSnet: Int,
    masterReturnSlots: Seq[Int],
    slaveRespSlots: Seq[Int],
    masterClass: Seq[Int],
    classWeights: Seq[Int],
    classDepth: Seq[Int],
    classMaxPacket: Seq[Int],
    banks: Int = 1,
    tagWidth: Int = LoomDefaults.AxiIdWidth
) {
  require(masterReturnSlots.length == nMaster, s"expected $nMaster return-slot depths, got ${masterReturnSlots.length}")
  require(slaveRespSlots.length == nSlave, s"expected $nSlave response-slot depths, got ${slaveRespSlots.length}")
  require(masterReturnSlots.forall(_ >= 1), "return-slot depth must be >= 1")
  require(slaveRespSlots.forall(_ >= 1), "response-slot depth must be >= 1")
  require(masterClass.length == nMaster, s"expected $nMaster master classes, got ${masterClass.length}")
  require(masterClass.forall(c => c >= 0 && c < classWeights.length), "master class out of range")
  require(classWeights.length == LoomDefaults.QosOrder.length,
    s"expected ${LoomDefaults.QosOrder.length} class weights, got ${classWeights.length}")
  require(classWeights.forall(_ >= 1), "class weight must be >= 1")
  require(classDepth.length == classWeights.length, "class depth table must match class count")
  require(classMaxPacket.length == classWeights.length, "class packet table must match class count")
  require(classDepth.forall(_ >= 1), "class depth must be >= 1")
  require(classMaxPacket.forall(_ >= 1), "class max packet must be >= 1")
  require(slaveBase.length == nSlave, "address window table must match slave count")
  require(masterMnet.length == nMaster, "mnet table must match master count")
  require(slaveSnet.length == nSlave, "snet table must match slave count")
  require(nMnet >= 1 && nSnet >= 1, "fabric needs at least one mnet and one snet")
  require(slaveSize.length == nSlave, "address window table must match slave count")
  require(banks >= 1 && banks <= 8, "bank count must be in [1,8]")
  require((banks & (banks - 1)) == 0, "bank count must be a power of two")
  require(nSlave % banks == 0, s"$nSlave outputs are not divisible by $banks banks")

  val maxReturnSlots: Int = masterReturnSlots.max
  val maxRespSlots: Int = slaveRespSlots.max
}

object FabricParams {
  def fromIr(ir: Ir): FabricParams = {
    val masters = ir.endpoints.filter(_.role == "master")
    val slaves = ir.endpoints.filter(_.role == "slave")

    require(ir.routers.length == 1, s"S3: exactly one router is supported, got ${ir.routers.length}")
    val r = ir.routers.head
    require(r.switchKind == "flat" || r.switchKind == "banked", s"unknown switch kind ${r.switchKind}")
    require(r.switchKind == "banked" || r.banks == 1, "flat switch requires banks = 1")
    require(r.switchKind != "banked" || r.banks > 1, "banked switch requires banks > 1")
    require(r.in == ir.mnets.length, s"router.in=${r.in} must equal MNet count ${ir.mnets.length}")
    require(r.out == ir.snets.length, s"router.out=${r.out} must equal SNet count ${ir.snets.length}")
    require(masters.nonEmpty, "at least one master is required")
    require(slaves.nonEmpty, "at least one slave is required")

    val dataWidth = masters.head.dataWidth
    require((masters ++ slaves).forall(_.dataWidth == dataWidth), "S3: all endpoints must share dataWidth")
    val addrWidth = masters.head.addrWidth
    require((masters ++ slaves).forall(_.addrWidth == addrWidth), "S3: all endpoints must share addrWidth")

    val returnSlots = masters.map(_.outstanding)
    val totalOutstanding = returnSlots.sum
    val masterClass = masters.map(e => LoomDefaults.QosOrder.indexOf(e.qos))
    require(masterClass.forall(_ >= 0), "endpoint qos must be one of ctl/lat/bulk")
    val classes = if (ir.qosClasses.isEmpty) LoomDefaults.DefaultQos else ir.qosClasses
    val weightOf = classes.map(c => c.name -> c.weight).toMap
    require(weightOf.keySet == LoomDefaults.QosOrder.toSet, "qos classes must be exactly ctl/lat/bulk")
    val classWeights = LoomDefaults.QosOrder.map(weightOf)
    val classOf = classes.map(c => c.name -> c).toMap
    val ordered = LoomDefaults.QosOrder.map(classOf)
    require(ordered.forall(c => c.depth >= c.maxPacketBeats), "G6: class depth must cover one packet")
    val windows = loom.ir.AddressMap.windows(slaves)

    FabricParams(
      name = ir.meta.name,
      nMaster = masters.length,
      nSlave = slaves.length,
      addrWidth = addrWidth,
      dataWidth = dataWidth,
      idWidth = math.max(1, log2Ceil(masters.length)),
      slaveBase = windows.map(_.base),
      slaveSize = windows.map(_.size),
      masterMnet = masters.map(e => ir.mnets.indexWhere(_.name == e.attach)),
      nMnet = ir.mnets.length,
      slaveSnet = slaves.map(e => ir.snets.indexWhere(_.name == e.attach)),
      nSnet = ir.snets.length,
      masterReturnSlots = returnSlots,
      slaveRespSlots = slaves.map(_ => totalOutstanding),
      masterClass = masterClass,
      classWeights = classWeights,
      classDepth = ordered.map(_.depth),
      classMaxPacket = ordered.map(_.maxPacketBeats),
      banks = r.banks,
      tagWidth = ir.axiIdWidth
    )
  }

  /** Convenience constructor for tests and small experiments. */
  def simple(
      name: String,
      nMaster: Int,
      nSlave: Int,
      addrWidth: Int,
      dataWidth: Int,
      outstanding: Int = 4,
      masterClass: Seq[Int] = Nil,
      classWeights: Seq[Int] = LoomDefaults.DefaultQos.map(_.weight)
  ): FabricParams = {
    require(outstanding >= 1)
    val classes = if (masterClass.isEmpty) Seq.fill(nMaster)(LoomDefaults.QosOrder.indexOf("bulk"))
    else masterClass
    FabricParams(
      name = name,
      nMaster = nMaster,
      nSlave = nSlave,
      addrWidth = addrWidth,
      dataWidth = dataWidth,
      idWidth = math.max(1, log2Ceil(nMaster)),
      slaveBase = Seq.tabulate(nSlave)(i => i.toLong * loom.ir.AddressMap.DefaultSize),
      slaveSize = Seq.fill(nSlave)(loom.ir.AddressMap.DefaultSize),
      masterMnet = (0 until nMaster).toSeq,
      nMnet = nMaster,
      slaveSnet = (0 until nSlave).toSeq,
      nSnet = nSlave,
      masterReturnSlots = Seq.fill(nMaster)(outstanding),
      slaveRespSlots = Seq.fill(nSlave)(outstanding * nMaster),
      masterClass = classes,
      classWeights = classWeights,
      classDepth = LoomDefaults.DefaultQos.map(_.depth),
      classMaxPacket = LoomDefaults.DefaultQos.map(_.maxPacketBeats),
      banks = 1
    )
  }

  private def pow2(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0

  private def log2Ceil(n: Int): Int =
    if (n <= 1) 0 else 32 - Integer.numberOfLeadingZeros(n - 1)
}
