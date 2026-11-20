package loom.rtl

import chisel3._
import chisel3.util._
import loom.ir.{Canonical, Ir, JArr, JNum, JObj, JStr, JValue, PartitionSpec}

/** Elaborated view of one partition: local routers, local endpoints and the
  * links that leave or enter the partition. Only router-to-router links may
  * cross a boundary (checked by S2/S3).
  */
final case class PartitionParams(
    name: String,
    clock: String,
    pipe: Int,
    routers: Seq[Int],
    masters: Seq[Int],
    slaves: Seq[Int],
    internalLinks: Seq[NetLinkSpec],
    outLinks: Seq[NetLinkSpec],
    inLinks: Seq[NetLinkSpec],
    hash: String
)

object PartitionParams {
  def fromIr(ir: Ir, net: NetworkParams, spec: PartitionSpec): PartitionParams = {
    val localRouters = net.routers.filter(r => spec.members.contains(r.name)).map(_.index).sorted
    require(localRouters.nonEmpty, s"partition '${spec.name}' has no router")
    val localSet = localRouters.toSet

    val masters = net.masterRouter.indices.filter(i => localSet.contains(net.masterRouter(i))).toSeq
    val slaves = net.slaveRouter.indices.filter(j => localSet.contains(net.slaveRouter(j))).toSeq

    val internal = net.links.filter(l => localSet.contains(l.fromRouter) && localSet.contains(l.toRouter))
    val out = net.links.filter(l => localSet.contains(l.fromRouter) && !localSet.contains(l.toRouter))
    val in = net.links.filter(l => !localSet.contains(l.fromRouter) && localSet.contains(l.toRouter))

    val boundaryIds = (out ++ in).map(_.id).sorted
    val hashInput = JObj(Vector(
      "name" -> JStr(spec.name),
      "clock" -> JStr(spec.clock),
      "pipe" -> JNum(BigDecimal(spec.boundaryPipe)),
      "routers" -> JArr(localRouters.map(r => JStr(net.routers(r).name)).toVector),
      "masters" -> JArr(masters.map(i => JStr(ir.endpoints.filter(_.role == "master")(i).id)).toVector),
      "slaves" -> JArr(slaves.map(j => JStr(ir.endpoints.filter(_.role == "slave")(j).id)).toVector),
      "boundary" -> JArr(boundaryIds.map(id => JNum(BigDecimal(id))).toVector)
    ))

    PartitionParams(
      name = spec.name,
      clock = spec.clock,
      pipe = spec.boundaryPipe,
      routers = localRouters,
      masters = masters,
      slaves = slaves,
      internalLinks = internal,
      outLinks = out,
      inLinks = in,
      hash = Canonical.hash(hashInput)
    )
  }
}

/** Port contract shared by a real partition and its stub. Endpoint ports keep
  * the merged LmReq/LmRsp view; the four channel groups cross the boundary.
  */
class LmPartitionIo(
    nMaster: Int,
    nSlave: Int,
    nOutLink: Int,
    nInLink: Int,
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    retW: Int,
    rspW: Int,
    tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Bundle {
  val master = Vec(nMaster, Flipped(Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth))))
  val masterRsp = Vec(nMaster, Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
  val slave = Vec(nSlave, Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth)))
  val slaveRsp = Vec(nSlave, Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))))

  val boundaryOutRdReq = Vec(nOutLink, Decoupled(new LmRdReq(addrWidth, idWidth, tagWidth)))
  val boundaryOutWrData =
    Vec(nOutLink, Decoupled(new LmWrData(addrWidth, dataWidth, idWidth, tagWidth)))
  val boundaryInRdReq = Vec(nInLink, Flipped(Decoupled(new LmRdReq(addrWidth, idWidth, tagWidth))))
  val boundaryInWrData =
    Vec(nInLink, Flipped(Decoupled(new LmWrData(addrWidth, dataWidth, idWidth, tagWidth))))

  val boundaryOutRdData =
    Vec(nOutLink, Flipped(Decoupled(new LmRdData(dataWidth, idWidth, tagWidth))))
  val boundaryOutWrResp = Vec(nOutLink, Flipped(Decoupled(new LmWrResp(idWidth, tagWidth))))
  val boundaryInRdData = Vec(nInLink, Decoupled(new LmRdData(dataWidth, idWidth, tagWidth)))
  val boundaryInWrResp = Vec(nInLink, Decoupled(new LmWrResp(idWidth, tagWidth)))

  val boundaryOutRdDataFwd = Vec(nOutLink, Decoupled(new LmRdData(dataWidth, idWidth, tagWidth)))
  val boundaryOutWrRespFwd = Vec(nOutLink, Decoupled(new LmWrResp(idWidth, tagWidth)))
  val boundaryInRdDataFwd = Vec(nInLink, Flipped(Decoupled(new LmRdData(dataWidth, idWidth, tagWidth))))
  val boundaryInWrRespFwd = Vec(nInLink, Flipped(Decoupled(new LmWrResp(idWidth, tagWidth))))

  val perf = new Bundle {
    val returnSlots = Vec(nMaster, UInt(retW.W))
    val respSlots = Vec(nSlave, UInt(rspW.W))
  }
}

/** One partition of a multi-router network.
  *
  * Router port layouts and route tables are inherited from the full network,
  * so a router that routes to a slave in another partition simply drives the
  * boundary port at that link position. Every channel group is registered with
  * at least one pipe stage. Async boundary links are legal: the wrapper
  * inserts one CDC per channel on the cut, while internal async links stay
  * rejected because every router in a partition shares one clock.
  */
class LmPartitionTop(p: NetworkParams, pp: PartitionParams) extends Module {
  require(!pp.internalLinks.exists(_.async),
    "async links inside a partition are pointless: all members share one clock")
  require(pp.pipe >= 1, "boundary pipe must be at least one stage")

  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  private def rdReqT = new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)
  private def wrDataT = new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)
  private def rdDataT = new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)
  private def wrRespT = new LmWrResp(p.idWidth, p.tagWidth)

  val io = IO(new LmPartitionIo(
    pp.masters.size, pp.slaves.size, pp.outLinks.size, pp.inLinks.size,
    p.addrWidth, p.dataWidth, p.idWidth, maxRetW, maxRspW, p.tagWidth))

  private val routers = pp.routers.map(idx => idx -> Module(new LmNetRouter(p, p.routers(idx)))).toMap
  private val mNi = pp.masters.map(i => Module(new LmMasterNi(p, i)))
  private val sNi = pp.slaves.map(j => Module(new LmSlaveNi(p, j)))

  // Only endpoints owned by this partition are active; the tokens keep the
  // MNet/SNet shared-port bandwidth contract across the subset.
  private val mnetRdReqTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val mnetWrDataTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val mnetRdDataTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val mnetWrRespTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val snetRdReqTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))
  private val snetWrDataTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))
  private val snetRdDataTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))
  private val snetWrRespTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))

  private val sSkidRd = pp.slaves.map(_ => Module(new LmPipe(rdReqT, 1)))
  private val sSkidWr = pp.slaves.map(_ => Module(new LmPipe(wrDataT, 1)))
  for ((j, s) <- pp.slaves.zipWithIndex) {
    routers(p.slaveRouter(j)).io.outRdReq(p.slavePort(j)) <> sSkidRd(s).io.in
    routers(p.slaveRouter(j)).io.outWrData(p.slavePort(j)) <> sSkidWr(s).io.in
  }

  for (m <- 0 until p.nMnet) {
    for (i <- 0 until p.nMaster) {
      mnetRdReqTok(m).io.req(i) := false.B
      mnetWrDataTok(m).io.req(i) := false.B
      mnetRdDataTok(m).io.req(i) := false.B
      mnetWrRespTok(m).io.req(i) := false.B
    }
  }
  for (s <- 0 until p.nSnet) {
    for (j <- 0 until p.nSlave) {
      snetRdReqTok(s).io.req(j) := false.B
      snetWrDataTok(s).io.req(j) := false.B
      snetRdDataTok(s).io.req(j) := false.B
      snetWrRespTok(s).io.req(j) := false.B
    }
  }
  for ((i, m) <- pp.masters.zipWithIndex) {
    val r = routers(p.masterRouter(i))
    mnetRdReqTok(p.masterMnet(i)).io.req(i) := mNi(m).io.outRdReq.valid
    mnetWrDataTok(p.masterMnet(i)).io.req(i) := mNi(m).io.outWrData.valid
    mnetRdDataTok(p.masterMnet(i)).io.req(i) := r.io.inRdData(p.masterPort(i)).valid
    mnetWrRespTok(p.masterMnet(i)).io.req(i) := r.io.inWrResp(p.masterPort(i)).valid
  }
  for ((j, s) <- pp.slaves.zipWithIndex) {
    snetRdReqTok(p.slaveSnet(j)).io.req(j) := sSkidRd(s).io.out.valid
    snetWrDataTok(p.slaveSnet(j)).io.req(j) := sSkidWr(s).io.out.valid
    snetRdDataTok(p.slaveSnet(j)).io.req(j) := sNi(s).io.outRdData.valid
    snetWrRespTok(p.slaveSnet(j)).io.req(j) := sNi(s).io.outWrResp.valid
  }

  private def mnetRdReqOk(i: Int): Bool = mnetRdReqTok(p.masterMnet(i)).io.ok(i)
  private def mnetWrDataOk(i: Int): Bool = mnetWrDataTok(p.masterMnet(i)).io.ok(i)
  private def mnetRdDataOk(i: Int): Bool = mnetRdDataTok(p.masterMnet(i)).io.ok(i)
  private def mnetWrRespOk(i: Int): Bool = mnetWrRespTok(p.masterMnet(i)).io.ok(i)
  private def snetRdReqOk(j: Int): Bool = snetRdReqTok(p.slaveSnet(j)).io.ok(j)
  private def snetWrDataOk(j: Int): Bool = snetWrDataTok(p.slaveSnet(j)).io.ok(j)
  private def snetRdDataOk(j: Int): Bool = snetRdDataTok(p.slaveSnet(j)).io.ok(j)
  private def snetWrRespOk(j: Int): Bool = snetWrRespTok(p.slaveSnet(j)).io.ok(j)

  for ((i, m) <- pp.masters.zipWithIndex) {
    val r = routers(p.masterRouter(i))
    val port = p.masterPort(i)
    mNi(m).io.in.valid := io.master(m).valid
    mNi(m).io.in.bits := io.master(m).bits
    io.master(m).ready := mNi(m).io.in.ready

    r.io.inRdReq(port).valid := mNi(m).io.outRdReq.valid && mnetRdReqOk(i)
    r.io.inRdReq(port).bits := mNi(m).io.outRdReq.bits
    mNi(m).io.outRdReq.ready := r.io.inRdReq(port).ready && mnetRdReqOk(i)

    r.io.inWrData(port).valid := mNi(m).io.outWrData.valid && mnetWrDataOk(i)
    r.io.inWrData(port).bits := mNi(m).io.outWrData.bits
    mNi(m).io.outWrData.ready := r.io.inWrData(port).ready && mnetWrDataOk(i)

    mNi(m).io.inRdData.valid := r.io.inRdData(port).valid && mnetRdDataOk(i)
    mNi(m).io.inRdData.bits := r.io.inRdData(port).bits
    r.io.inRdData(port).ready := mNi(m).io.inRdData.ready

    mNi(m).io.inWrResp.valid := r.io.inWrResp(port).valid && mnetWrRespOk(i)
    mNi(m).io.inWrResp.bits := r.io.inWrResp(port).bits
    r.io.inWrResp(port).ready := mNi(m).io.inWrResp.ready

    r.io.inRdDataFwd(port).valid := false.B
    r.io.inRdDataFwd(port).bits := 0.U.asTypeOf(chiselTypeOf(r.io.inRdDataFwd(port).bits))
    r.io.inWrRespFwd(port).valid := false.B
    r.io.inWrRespFwd(port).bits := 0.U.asTypeOf(chiselTypeOf(r.io.inWrRespFwd(port).bits))

    io.masterRsp(m).valid := mNi(m).io.outRsp.valid
    io.masterRsp(m).bits := mNi(m).io.outRsp.bits
    mNi(m).io.outRsp.ready := io.masterRsp(m).ready
    io.perf.returnSlots(m) := mNi(m).io.credit
  }

  for ((j, s) <- pp.slaves.zipWithIndex) {
    routers(p.slaveRouter(j)).io.outRdDataFwd(p.slavePort(j)).ready := true.B
    routers(p.slaveRouter(j)).io.outWrRespFwd(p.slavePort(j)).ready := true.B
  }

  for ((j, s) <- pp.slaves.zipWithIndex) {
    val r = routers(p.slaveRouter(j))
    val port = p.slavePort(j)
    sNi(s).io.inRdReq.valid := sSkidRd(s).io.out.valid && snetRdReqOk(j)
    sNi(s).io.inRdReq.bits := sSkidRd(s).io.out.bits
    sSkidRd(s).io.out.ready := sNi(s).io.inRdReq.ready && snetRdReqOk(j)

    sNi(s).io.inWrData.valid := sSkidWr(s).io.out.valid && snetWrDataOk(j)
    sNi(s).io.inWrData.bits := sSkidWr(s).io.out.bits
    sSkidWr(s).io.out.ready := sNi(s).io.inWrData.ready && snetWrDataOk(j)

    io.slave(s).valid := sNi(s).io.out.valid
    io.slave(s).bits := sNi(s).io.out.bits
    sNi(s).io.out.ready := io.slave(s).ready

    sNi(s).io.inRsp.valid := io.slaveRsp(s).valid
    sNi(s).io.inRsp.bits := io.slaveRsp(s).bits
    io.slaveRsp(s).ready := sNi(s).io.inRsp.ready

    r.io.outRdData(port).valid := sNi(s).io.outRdData.valid && snetRdDataOk(j)
    r.io.outRdData(port).bits := sNi(s).io.outRdData.bits
    sNi(s).io.outRdData.ready := r.io.outRdData(port).ready && snetRdDataOk(j)

    r.io.outWrResp(port).valid := sNi(s).io.outWrResp.valid && snetWrRespOk(j)
    r.io.outWrResp(port).bits := sNi(s).io.outWrResp.bits
    sNi(s).io.outWrResp.ready := r.io.outWrResp(port).ready && snetWrRespOk(j)
    io.perf.respSlots(s) := sNi(s).io.slot
  }

  for (l <- pp.internalLinks) {
    val link = Module(new LmRouterLink(p.addrWidth, p.dataWidth, p.idWidth,
      l.pipe, l.async, l.asyncFifoDepth, p.tagWidth))
    link.io.aClk := false.B
    link.io.aReset := false.B
    link.io.bClk := false.B
    link.io.bReset := false.B
    link.io.aRdReq <> routers(l.fromRouter).io.outRdReq(l.fromPort)
    routers(l.toRouter).io.inRdReq(l.toPort) <> link.io.bRdReq
    link.io.aWrData <> routers(l.fromRouter).io.outWrData(l.fromPort)
    routers(l.toRouter).io.inWrData(l.toPort) <> link.io.bWrData
    link.io.bRdData <> routers(l.toRouter).io.inRdData(l.toPort)
    routers(l.fromRouter).io.outRdData(l.fromPort) <> link.io.aRdData
    link.io.bWrResp <> routers(l.toRouter).io.inWrResp(l.toPort)
    routers(l.fromRouter).io.outWrResp(l.fromPort) <> link.io.aWrResp
    link.io.aRdDataFwd <> routers(l.fromRouter).io.outRdDataFwd(l.fromPort)
    routers(l.toRouter).io.inRdDataFwd(l.toPort) <> link.io.bRdDataFwd
    link.io.aWrRespFwd <> routers(l.fromRouter).io.outWrRespFwd(l.fromPort)
    routers(l.toRouter).io.inWrRespFwd(l.toPort) <> link.io.bWrRespFwd
  }

  private def staged[T <: Data](gen: T, src: DecoupledIO[T], dst: DecoupledIO[T]): Unit = {
    val pipe = Module(new LmPipe(gen, pp.pipe))
    pipe.io.in <> src
    dst <> pipe.io.out
  }

  for ((l, k) <- pp.outLinks.zipWithIndex) {
    staged(rdReqT, routers(l.fromRouter).io.outRdReq(l.fromPort), io.boundaryOutRdReq(k))
    staged(wrDataT, routers(l.fromRouter).io.outWrData(l.fromPort), io.boundaryOutWrData(k))
    staged(rdDataT, io.boundaryOutRdData(k), routers(l.fromRouter).io.outRdData(l.fromPort))
    staged(wrRespT, io.boundaryOutWrResp(k), routers(l.fromRouter).io.outWrResp(l.fromPort))
    staged(rdDataT, routers(l.fromRouter).io.outRdDataFwd(l.fromPort), io.boundaryOutRdDataFwd(k))
    staged(wrRespT, routers(l.fromRouter).io.outWrRespFwd(l.fromPort), io.boundaryOutWrRespFwd(k))
  }

  for ((l, k) <- pp.inLinks.zipWithIndex) {
    staged(rdReqT, io.boundaryInRdReq(k), routers(l.toRouter).io.inRdReq(l.toPort))
    staged(wrDataT, io.boundaryInWrData(k), routers(l.toRouter).io.inWrData(l.toPort))
    staged(rdDataT, routers(l.toRouter).io.inRdData(l.toPort), io.boundaryInRdData(k))
    staged(wrRespT, routers(l.toRouter).io.inWrResp(l.toPort), io.boundaryInWrResp(k))
    staged(rdDataT, io.boundaryInRdDataFwd(k), routers(l.toRouter).io.inRdDataFwd(l.toPort))
    staged(wrRespT, io.boundaryInWrRespFwd(k), routers(l.toRouter).io.inWrRespFwd(l.toPort))
  }
}

/** Wrapper top: instantiates every partition and connects the cut links at
  * their boundary ports. Endpoint and perf ports keep the global numbering.
  *
  * With one clock domain the implicit clock/reset drive every partition and
  * the port list is unchanged. With several domains the wrapper exposes
  * `domainClk`/`domainReset` (one entry per declared clock, sorted by id) and
  * runs each partition in its own domain. A sync cut link is wired directly;
  * an async cut link gets one `LmAsyncLink` per channel and direction, clocked
  * from the source domain with the destination domain as `dstClk`.
  */
class LmWrapperTop(p: NetworkParams, parts: Seq[PartitionParams]) extends Module {
  require(parts.nonEmpty, "at least one partition is required")
  require(parts.flatMap(_.routers).sorted == p.routers.indices.toSeq,
    "partitions must cover every router exactly once")

  private val domainNames = parts.map(_.clock).distinct.sorted
  private val multiClock = domainNames.size > 1

  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  private def rdReqT = new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)
  private def wrDataT = new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)
  private def rdDataT = new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)
  private def wrRespT = new LmWrResp(p.idWidth, p.tagWidth)

  val io = IO(new Bundle {
    val master = Vec(p.nMaster, Flipped(Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth))))
    val masterRsp = Vec(p.nMaster, Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
    val slave = Vec(p.nSlave, Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))
    val slaveRsp = Vec(p.nSlave, Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth))))
    val perf = Output(new Bundle {
      val returnSlots = Vec(p.nMaster, UInt(maxRetW.W))
      val respSlots = Vec(p.nSlave, UInt(maxRspW.W))
    })
    val domainClk: Option[Vec[Bool]] = if (multiClock) Some(Input(Vec(domainNames.size, Bool()))) else None
    val domainReset: Option[Vec[Bool]] = if (multiClock) Some(Input(Vec(domainNames.size, Bool()))) else None
  })

  private def domainOf(pi: Int): Int = domainNames.indexOf(parts(pi).clock)

  private def partitionClock(pi: Int): Clock =
    if (multiClock) io.domainClk.get(domainOf(pi)).asClock else clock

  private def partitionReset(pi: Int): Reset =
    if (multiClock) io.domainReset.get(domainOf(pi)) else reset

  private val insts = parts.zipWithIndex.map { case (pp, pi) =>
    if (multiClock) withClockAndReset(partitionClock(pi), partitionReset(pi)) {
      Module(new LmPartitionTop(p, pp))
    } else Module(new LmPartitionTop(p, pp))
  }

  for (pi <- parts.indices; (g, l) <- parts(pi).masters.zipWithIndex) {
    insts(pi).io.master(l).valid := io.master(g).valid
    insts(pi).io.master(l).bits := io.master(g).bits
    io.master(g).ready := insts(pi).io.master(l).ready
    io.masterRsp(g).valid := insts(pi).io.masterRsp(l).valid
    io.masterRsp(g).bits := insts(pi).io.masterRsp(l).bits
    insts(pi).io.masterRsp(l).ready := io.masterRsp(g).ready
    io.perf.returnSlots(g) := insts(pi).io.perf.returnSlots(l)
  }

  for (pi <- parts.indices; (g, l) <- parts(pi).slaves.zipWithIndex) {
    io.slave(g).valid := insts(pi).io.slave(l).valid
    io.slave(g).bits := insts(pi).io.slave(l).bits
    insts(pi).io.slave(l).ready := io.slave(g).ready
    insts(pi).io.slaveRsp(l).valid := io.slaveRsp(g).valid
    insts(pi).io.slaveRsp(l).bits := io.slaveRsp(g).bits
    io.slaveRsp(g).ready := insts(pi).io.slaveRsp(l).ready
    io.perf.respSlots(g) := insts(pi).io.perf.respSlots(l)
  }

  private def partOfRouter(r: Int): Int = parts.indexWhere(_.routers.contains(r))

  private def cdc[T <: Data](
      gen: T,
      depth: Int,
      srcClk: Clock,
      srcReset: Reset,
      dstClk: Clock,
      dstReset: Reset,
      in: DecoupledIO[T],
      out: DecoupledIO[T]
  ): Unit = {
    val link = withClockAndReset(srcClk, srcReset) {
      Module(new LmAsyncLink(gen.getWidth, depth))
    }
    link.io.dstClk := dstClk.asUInt.asBool
    link.io.dstReset := dstReset.asUInt.asBool
    link.io.in.valid := in.valid
    link.io.in.bits := in.bits.asUInt
    in.ready := link.io.in.ready
    out.valid := link.io.out.valid
    out.bits := link.io.out.bits.asTypeOf(chiselTypeOf(out.bits))
    link.io.out.ready := out.ready
  }

  for (l <- p.links) {
    val fromPart = partOfRouter(l.fromRouter)
    val toPart = partOfRouter(l.toRouter)
    if (fromPart != toPart) {
      val ok = parts(fromPart).outLinks.indexWhere(_.id == l.id)
      val ik = parts(toPart).inLinks.indexWhere(_.id == l.id)
      require(ok >= 0 && ik >= 0, s"cut link ${l.id} is missing from a partition boundary")

      val fOut = insts(fromPart).io
      val tIn = insts(toPart).io

      if (!l.async) {
        fOut.boundaryOutRdReq(ok) <> tIn.boundaryInRdReq(ik)
        fOut.boundaryOutWrData(ok) <> tIn.boundaryInWrData(ik)
        tIn.boundaryInRdData(ik) <> fOut.boundaryOutRdData(ok)
        tIn.boundaryInWrResp(ik) <> fOut.boundaryOutWrResp(ok)
        fOut.boundaryOutRdDataFwd(ok) <> tIn.boundaryInRdDataFwd(ik)
        fOut.boundaryOutWrRespFwd(ok) <> tIn.boundaryInWrRespFwd(ik)
      } else {
        require((l.asyncFifoDepth & (l.asyncFifoDepth - 1)) == 0 && l.asyncFifoDepth >= 2,
          s"async link ${l.id} depth ${l.asyncFifoDepth} must be a power of two >= 2")

        val fClk = partitionClock(fromPart)
        val fRst = partitionReset(fromPart)
        val tClk = partitionClock(toPart)
        val tRst = partitionReset(toPart)
        val depth = l.asyncFifoDepth

        cdc(rdReqT, depth, fClk, fRst, tClk, tRst,
          fOut.boundaryOutRdReq(ok), tIn.boundaryInRdReq(ik))
        cdc(wrDataT, depth, fClk, fRst, tClk, tRst,
          fOut.boundaryOutWrData(ok), tIn.boundaryInWrData(ik))
        cdc(rdDataT, depth, tClk, tRst, fClk, fRst,
          tIn.boundaryInRdData(ik), fOut.boundaryOutRdData(ok))
        cdc(wrRespT, depth, tClk, tRst, fClk, fRst,
          tIn.boundaryInWrResp(ik), fOut.boundaryOutWrResp(ok))
        cdc(rdDataT, depth, fClk, fRst, tClk, tRst,
          fOut.boundaryOutRdDataFwd(ok), tIn.boundaryInRdDataFwd(ik))
        cdc(wrRespT, depth, fClk, fRst, tClk, tRst,
          fOut.boundaryOutWrRespFwd(ok), tIn.boundaryInWrRespFwd(ik))
      }
    }
  }
}

/** Stub with the same port contract as a real partition (S4).
  *
  * Every accepted request (local master or boundary input) is answered with
  * exactly one error response on the matching channel, so upstream credits and
  * response slots close even when the real partition is replaced.
  */
class LmPartitionStub(p: NetworkParams, pp: PartitionParams) extends Module {
  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  val io = IO(new LmPartitionIo(
    pp.masters.size, pp.slaves.size, pp.outLinks.size, pp.inLinks.size,
    p.addrWidth, p.dataWidth, p.idWidth, maxRetW, maxRspW, p.tagWidth))

  private val mPending = Seq.fill(pp.masters.size)(RegInit(false.B))
  private val mSrc = Seq.fill(pp.masters.size)(RegInit(0.U(p.idWidth.W)))
  private val mId = Seq.fill(pp.masters.size)(RegInit(0.U(p.tagWidth.W)))

  for (m <- pp.masters.indices) {
    io.master(m).ready := !mPending(m)
    when(io.master(m).fire) {
      mPending(m) := true.B
      mSrc(m) := io.master(m).bits.src
      mId(m) := io.master(m).bits.id
    }
    io.masterRsp(m).valid := mPending(m)
    io.masterRsp(m).bits.src := mSrc(m)
    io.masterRsp(m).bits.id := mId(m)
    io.masterRsp(m).bits.data := 0.U
    io.masterRsp(m).bits.err := true.B
    io.masterRsp(m).bits.vc := 0.U
    when(io.masterRsp(m).fire) { mPending(m) := false.B }
  }

  for (s <- pp.slaves.indices) {
    io.slave(s).valid := false.B
    io.slave(s).bits := 0.U.asTypeOf(chiselTypeOf(io.slave(s).bits))
    io.slaveRsp(s).ready := true.B
  }

  private val bRdReqPend = Seq.fill(pp.inLinks.size)(RegInit(false.B))
  private val bRdReqSrc = Seq.fill(pp.inLinks.size)(RegInit(0.U(p.idWidth.W)))
  private val bRdReqId = Seq.fill(pp.inLinks.size)(RegInit(0.U(p.tagWidth.W)))
  private val bWrPend = Seq.fill(pp.inLinks.size)(RegInit(false.B))
  private val bWrSrc = Seq.fill(pp.inLinks.size)(RegInit(0.U(p.idWidth.W)))
  private val bWrId = Seq.fill(pp.inLinks.size)(RegInit(0.U(p.tagWidth.W)))

  for (b <- pp.inLinks.indices) {
    io.boundaryInRdReq(b).ready := !bRdReqPend(b)
    when(io.boundaryInRdReq(b).fire) {
      bRdReqPend(b) := true.B
      bRdReqSrc(b) := io.boundaryInRdReq(b).bits.src
      bRdReqId(b) := io.boundaryInRdReq(b).bits.id
    }
    io.boundaryInRdData(b).valid := bRdReqPend(b)
    io.boundaryInRdData(b).bits.src := bRdReqSrc(b)
    io.boundaryInRdData(b).bits.id := bRdReqId(b)
    io.boundaryInRdData(b).bits.data := 0.U
    io.boundaryInRdData(b).bits.last := true.B
    io.boundaryInRdData(b).bits.err := true.B
    io.boundaryInRdData(b).bits.vc := 0.U
    when(io.boundaryInRdData(b).fire) { bRdReqPend(b) := false.B }

    // A write burst ends with `last` and is answered by exactly one wresp, so
    // beats are accepted until a response is owed.
    io.boundaryInWrData(b).ready := !bWrPend(b)
    when(io.boundaryInWrData(b).fire) {
      when(io.boundaryInWrData(b).bits.last) {
        bWrPend(b) := true.B
        bWrSrc(b) := io.boundaryInWrData(b).bits.src
        bWrId(b) := io.boundaryInWrData(b).bits.id
      }
    }
    io.boundaryInWrResp(b).valid := bWrPend(b)
    io.boundaryInWrResp(b).bits.src := bWrSrc(b)
    io.boundaryInWrResp(b).bits.id := bWrId(b)
    io.boundaryInWrResp(b).bits.err := true.B
    io.boundaryInWrResp(b).bits.vc := 0.U
    when(io.boundaryInWrResp(b).fire) { bWrPend(b) := false.B }
  }

  for (o <- pp.outLinks.indices) {
    io.boundaryOutRdReq(o).valid := false.B
    io.boundaryOutRdReq(o).bits := 0.U.asTypeOf(chiselTypeOf(io.boundaryOutRdReq(o).bits))
    io.boundaryOutWrData(o).valid := false.B
    io.boundaryOutWrData(o).bits := 0.U.asTypeOf(chiselTypeOf(io.boundaryOutWrData(o).bits))
    io.boundaryOutRdData(o).ready := true.B
    io.boundaryOutWrResp(o).ready := true.B
    io.boundaryOutRdDataFwd(o).valid := false.B
    io.boundaryOutRdDataFwd(o).bits := 0.U.asTypeOf(chiselTypeOf(io.boundaryOutRdDataFwd(o).bits))
    io.boundaryOutWrRespFwd(o).valid := false.B
    io.boundaryOutWrRespFwd(o).bits := 0.U.asTypeOf(chiselTypeOf(io.boundaryOutWrRespFwd(o).bits))
  }

  for (b <- pp.inLinks.indices) {
    io.boundaryInRdDataFwd(b).ready := true.B
    io.boundaryInWrRespFwd(b).ready := true.B
  }

  for (m <- pp.masters.indices) io.perf.returnSlots(m) := 0.U
  for (s <- pp.slaves.indices) io.perf.respSlots(s) := 0.U
}
