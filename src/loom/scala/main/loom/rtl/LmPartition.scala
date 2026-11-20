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

/** Port contract shared by a real partition and its stub. */
class LmPartitionIo(
    nMaster: Int,
    nSlave: Int,
    nOutLink: Int,
    nInLink: Int,
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    qosWidth: Int,
    retW: Int,
    rspW: Int,
    tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Bundle {
  val master = Vec(nMaster, Flipped(Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth))))
  val masterRsp = Vec(nMaster, Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
  val slave = Vec(nSlave, Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth)))
  val slaveRsp = Vec(nSlave, Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))))
  val boundaryOutReq =
    Vec(nOutLink, Decoupled(new LmNetReq(addrWidth, dataWidth, idWidth, qosWidth, tagWidth)))
  val boundaryOutRsp = Vec(nOutLink, Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))))
  val boundaryInReq =
    Vec(nInLink, Flipped(Decoupled(new LmNetReq(addrWidth, dataWidth, idWidth, qosWidth, tagWidth))))
  val boundaryInRsp = Vec(nInLink, Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
  val boundaryOutRspFwd = Vec(nOutLink, Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
  val boundaryInRspFwd = Vec(nInLink, Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))))
  val perf = new Bundle {
    val returnSlots = Vec(nMaster, UInt(retW.W))
    val respSlots = Vec(nSlave, UInt(rspW.W))
  }
}

/** One partition of a multi-router network.
  *
  * Router port layouts and route tables are inherited from the full network,
  * so a router that routes to a slave in another partition simply drives the
  * boundary port at that link position. Boundary channels are registered
  * with at least one pipe stage. Async boundary links are legal: the wrapper
  * inserts the CDC on the cut, while internal async links stay rejected
  * because every router in a partition shares one clock.
  */
class LmPartitionTop(p: NetworkParams, pp: PartitionParams) extends Module {
  require(!pp.internalLinks.exists(_.async),
    "async links inside a partition are pointless: all members share one clock")
  require(pp.pipe >= 1, "boundary pipe must be at least one stage")

  private val qosW = math.max(1, log2Ceil(p.classWeights.length))
  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  val io = IO(new LmPartitionIo(
    pp.masters.size, pp.slaves.size, pp.outLinks.size, pp.inLinks.size,
    p.addrWidth, p.dataWidth, p.idWidth, qosW, maxRetW, maxRspW, p.tagWidth))

  private val routers = pp.routers.map(idx => idx -> Module(new LmNetRouter(p, p.routers(idx)))).toMap
  private val mNi = pp.masters.map(i => Module(new LmMasterNi(p, i)))
  private val sNi = pp.slaves.map(j => Module(new LmSlaveNi(p, j)))

  // Only endpoints owned by this partition are active; the tokens keep the
  // MNet/SNet shared-port bandwidth contract across the subset.
  private val mnetReqArb = Seq.tabulate(p.nMnet)(_ => Module(new LmRrArbiterN(p.nMaster)))
  private val mnetRspArb = Seq.tabulate(p.nMnet)(_ => Module(new LmRrArbiterN(p.nMaster)))
  private val sSkid = pp.slaves.map(j =>
    Module(new LmPipe(new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth), 1)))
  for ((j, s) <- pp.slaves.zipWithIndex) {
    routers(p.slaveRouter(j)).io.outReq(p.slavePort(j)) <> sSkid(s).io.in
  }
  private val snetReqArb = Seq.tabulate(p.nSnet)(_ => Module(new LmRrArbiterN(p.nSlave)))
  private val snetRspArb = Seq.tabulate(p.nSnet)(_ => Module(new LmRrArbiterN(p.nSlave)))
  for (m <- 0 until p.nMnet; i <- 0 until p.nMaster) {
    mnetReqArb(m).io.req(i) := false.B
    mnetRspArb(m).io.req(i) := false.B
  }
  for (s <- 0 until p.nSnet; j <- 0 until p.nSlave) {
    snetReqArb(s).io.req(j) := false.B
    snetRspArb(s).io.req(j) := false.B
  }
  for ((i, _) <- pp.masters.zipWithIndex) {
    val r = routers(p.masterRouter(i))
    mnetReqArb(p.masterMnet(i)).io.req(i) := mNi(pp.masters.indexOf(i)).io.out.valid
    mnetRspArb(p.masterMnet(i)).io.req(i) := r.io.inRsp(p.masterPort(i)).valid
  }
  for ((j, s) <- pp.slaves.zipWithIndex) {
    val r = routers(p.slaveRouter(j))
    snetReqArb(p.slaveSnet(j)).io.req(j) := sSkid(s).io.out.valid
    snetRspArb(p.slaveSnet(j)).io.req(j) := sNi(s).io.outRsp.valid
  }
  private def mnetReqOk(i: Int): Bool = {
    val a = mnetReqArb(p.masterMnet(i))
    a.io.valid && (a.io.grant === i.U)
  }
  private def mnetRspOk(i: Int): Bool = {
    val a = mnetRspArb(p.masterMnet(i))
    a.io.valid && (a.io.grant === i.U)
  }
  private def snetReqOk(j: Int): Bool = {
    val a = snetReqArb(p.slaveSnet(j))
    a.io.valid && (a.io.grant === j.U)
  }
  private def snetRspOk(j: Int): Bool = {
    val a = snetRspArb(p.slaveSnet(j))
    a.io.valid && (a.io.grant === j.U)
  }

  for ((i, m) <- pp.masters.zipWithIndex) {
    val r = routers(p.masterRouter(i))
    val port = p.masterPort(i)
    mNi(m).io.in.valid := io.master(m).valid
    mNi(m).io.in.bits := io.master(m).bits
    io.master(m).ready := mNi(m).io.in.ready
    r.io.inReq(port).valid := mNi(m).io.out.valid && mnetReqOk(i)
    r.io.inReq(port).bits := mNi(m).io.out.bits
    mNi(m).io.out.ready := r.io.inReq(port).ready && mnetReqOk(i)
    mNi(m).io.inRsp.valid := r.io.inRsp(port).valid && mnetRspOk(i)
    mNi(m).io.inRsp.bits := r.io.inRsp(port).bits
    r.io.inRsp(port).ready := mNi(m).io.inRsp.ready

    r.io.inRspFwd(port).valid := false.B
    r.io.inRspFwd(port).bits := 0.U.asTypeOf(chiselTypeOf(r.io.inRspFwd(port).bits))
    io.masterRsp(m).valid := mNi(m).io.outRsp.valid
    io.masterRsp(m).bits := mNi(m).io.outRsp.bits
    mNi(m).io.outRsp.ready := io.masterRsp(m).ready
    io.perf.returnSlots(m) := mNi(m).io.credit
  }

  for ((j, s) <- pp.slaves.zipWithIndex) {
    routers(p.slaveRouter(j)).io.outRspFwd(p.slavePort(j)).ready := true.B
  }

  for ((j, s) <- pp.slaves.zipWithIndex) {
    val r = routers(p.slaveRouter(j))
    val port = p.slavePort(j)
    sNi(s).io.in.valid := sSkid(s).io.out.valid && snetReqOk(j)
    sNi(s).io.in.bits := sSkid(s).io.out.bits
    sSkid(s).io.out.ready := sNi(s).io.in.ready && snetReqOk(j)
    io.slave(s).valid := sNi(s).io.out.valid
    io.slave(s).bits := sNi(s).io.out.bits
    sNi(s).io.out.ready := io.slave(s).ready
    sNi(s).io.inRsp.valid := io.slaveRsp(s).valid
    sNi(s).io.inRsp.bits := io.slaveRsp(s).bits
    io.slaveRsp(s).ready := sNi(s).io.inRsp.ready
    r.io.outRsp(port).valid := sNi(s).io.outRsp.valid && snetRspOk(j)
    r.io.outRsp(port).bits := sNi(s).io.outRsp.bits
    sNi(s).io.outRsp.ready := r.io.outRsp(port).ready && snetRspOk(j)
    io.perf.respSlots(s) := sNi(s).io.slot
  }

  for (l <- pp.internalLinks) {
    val link = Module(new LmRouterLink(p.addrWidth, p.dataWidth, p.idWidth, qosW,
      l.pipe, l.async, l.asyncFifoDepth, p.tagWidth))
    link.io.aClk := false.B
    link.io.aReset := false.B
    link.io.bClk := false.B
    link.io.bReset := false.B
    link.io.aReq <> routers(l.fromRouter).io.outReq(l.fromPort)
    routers(l.toRouter).io.inReq(l.toPort) <> link.io.bReq
    link.io.bRsp <> routers(l.toRouter).io.inRsp(l.toPort)
    routers(l.fromRouter).io.outRsp(l.fromPort) <> link.io.aRsp
    link.io.aRspF <> routers(l.fromRouter).io.outRspFwd(l.fromPort)
    routers(l.toRouter).io.inRspFwd(l.toPort) <> link.io.bRspF
  }

  private val reqType = new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth)
  private val rspType = new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)

  for ((l, k) <- pp.outLinks.zipWithIndex) {
    val outReqPipe = Module(new LmPipe(reqType, pp.pipe))
    outReqPipe.io.in <> routers(l.fromRouter).io.outReq(l.fromPort)
    io.boundaryOutReq(k) <> outReqPipe.io.out

    val outRspPipe = Module(new LmPipe(rspType, pp.pipe))
    outRspPipe.io.in <> io.boundaryOutRsp(k)
    routers(l.fromRouter).io.outRsp(l.fromPort) <> outRspPipe.io.out

    val outRspFPipe = Module(new LmPipe(rspType, pp.pipe))
    routers(l.fromRouter).io.outRspFwd(l.fromPort) <> outRspFPipe.io.in
    io.boundaryOutRspFwd(k) <> outRspFPipe.io.out
  }

  for ((l, k) <- pp.inLinks.zipWithIndex) {
    val inReqPipe = Module(new LmPipe(reqType, pp.pipe))
    inReqPipe.io.in <> io.boundaryInReq(k)
    routers(l.toRouter).io.inReq(l.toPort) <> inReqPipe.io.out

    val inRspPipe = Module(new LmPipe(rspType, pp.pipe))
    inRspPipe.io.in <> routers(l.toRouter).io.inRsp(l.toPort)
    io.boundaryInRsp(k) <> inRspPipe.io.out

    val inRspFPipe = Module(new LmPipe(rspType, pp.pipe))
    inRspFPipe.io.in <> io.boundaryInRspFwd(k)
    routers(l.toRouter).io.inRspFwd(l.toPort) <> inRspFPipe.io.out
  }
}

/** Wrapper top: instantiates every partition and connects the cut links at
  * their boundary ports. Endpoint and perf ports keep the global numbering.
  *
  * With one clock domain the implicit clock/reset drive every partition and
  * the port list is unchanged. With several domains the wrapper exposes
  * `domainClk`/`domainReset` (one entry per declared clock, sorted by id) and
  * runs each partition in its own domain. A sync cut link is wired directly;
  * an async cut link gets one `LmAsyncLink` per direction, clocked from the
  * source domain with the destination domain as `dstClk`.
  */
class LmWrapperTop(p: NetworkParams, parts: Seq[PartitionParams]) extends Module {
  require(parts.nonEmpty, "at least one partition is required")
  require(parts.flatMap(_.routers).sorted == p.routers.indices.toSeq,
    "partitions must cover every router exactly once")

  private val domainNames = parts.map(_.clock).distinct.sorted
  private val multiClock = domainNames.size > 1

  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

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

  private val qosW = math.max(1, log2Ceil(p.classWeights.length))
  private val reqType = new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth)
  private val rspType = new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)

  for (l <- p.links) {
    val fromPart = partOfRouter(l.fromRouter)
    val toPart = partOfRouter(l.toRouter)
    if (fromPart != toPart) {
      val ok = parts(fromPart).outLinks.indexWhere(_.id == l.id)
      val ik = parts(toPart).inLinks.indexWhere(_.id == l.id)
      require(ok >= 0 && ik >= 0, s"cut link ${l.id} is missing from a partition boundary")

      val outReq = insts(fromPart).io.boundaryOutReq(ok)
      val inReq = insts(toPart).io.boundaryInReq(ik)
      val inRsp = insts(toPart).io.boundaryInRsp(ik)
      val outRsp = insts(fromPart).io.boundaryOutRsp(ok)

      if (!l.async) {
        outReq <> inReq
        inRsp <> outRsp
        insts(fromPart).io.boundaryOutRspFwd(ok) <> insts(toPart).io.boundaryInRspFwd(ik)
      } else {
        require((l.asyncFifoDepth & (l.asyncFifoDepth - 1)) == 0 && l.asyncFifoDepth >= 2,
          s"async link ${l.id} depth ${l.asyncFifoDepth} must be a power of two >= 2")

        val reqLink = withClockAndReset(partitionClock(fromPart), partitionReset(fromPart)) {
          Module(new LmAsyncLink(reqType.getWidth, l.asyncFifoDepth))
        }
        reqLink.io.dstClk := partitionClock(toPart).asUInt.asBool
        reqLink.io.dstReset := partitionReset(toPart).asUInt.asBool
        reqLink.io.in.valid := outReq.valid
        reqLink.io.in.bits := outReq.bits.asUInt
        outReq.ready := reqLink.io.in.ready
        inReq.valid := reqLink.io.out.valid
        inReq.bits := reqLink.io.out.bits.asTypeOf(chiselTypeOf(inReq.bits))
        reqLink.io.out.ready := inReq.ready

        val rspLink = withClockAndReset(partitionClock(toPart), partitionReset(toPart)) {
          Module(new LmAsyncLink(rspType.getWidth, l.asyncFifoDepth))
        }
        rspLink.io.dstClk := partitionClock(fromPart).asUInt.asBool
        rspLink.io.dstReset := partitionReset(fromPart).asUInt.asBool
        rspLink.io.in.valid := inRsp.valid
        rspLink.io.in.bits := inRsp.bits.asUInt
        inRsp.ready := rspLink.io.in.ready
        outRsp.valid := rspLink.io.out.valid
        outRsp.bits := rspLink.io.out.bits.asTypeOf(chiselTypeOf(outRsp.bits))
        rspLink.io.out.ready := outRsp.ready

        val rspFLink = withClockAndReset(partitionClock(fromPart), partitionReset(fromPart)) {
          Module(new LmAsyncLink(rspType.getWidth, l.asyncFifoDepth))
        }
        val outRspFwd = insts(fromPart).io.boundaryOutRspFwd(ok)
        val inRspFwd = insts(toPart).io.boundaryInRspFwd(ik)
        rspFLink.io.dstClk := partitionClock(toPart).asUInt.asBool
        rspFLink.io.dstReset := partitionReset(toPart).asUInt.asBool
        rspFLink.io.in.valid := outRspFwd.valid
        rspFLink.io.in.bits := outRspFwd.bits.asUInt
        outRspFwd.ready := rspFLink.io.in.ready
        inRspFwd.valid := rspFLink.io.out.valid
        inRspFwd.bits := rspFLink.io.out.bits.asTypeOf(chiselTypeOf(inRspFwd.bits))
        rspFLink.io.out.ready := inRspFwd.ready
      }
    }
  }
}

/** Stub with the same port contract as a real partition (S4).
  *
  * Every accepted request (local master or boundary input) is answered with
  * exactly one error response, so upstream credits and response slots close
  * even when the real partition is replaced.
  */
class LmPartitionStub(p: NetworkParams, pp: PartitionParams) extends Module {
  private val qosW = math.max(1, log2Ceil(p.classWeights.length))
  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  val io = IO(new LmPartitionIo(
    pp.masters.size, pp.slaves.size, pp.outLinks.size, pp.inLinks.size,
    p.addrWidth, p.dataWidth, p.idWidth, qosW, maxRetW, maxRspW, p.tagWidth))

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

  private val bPending = Seq.fill(pp.inLinks.size)(RegInit(false.B))
  private val bSrc = Seq.fill(pp.inLinks.size)(RegInit(0.U(p.idWidth.W)))
  private val bId = Seq.fill(pp.inLinks.size)(RegInit(0.U(p.tagWidth.W)))

  for (b <- pp.inLinks.indices) {
    io.boundaryInReq(b).ready := !bPending(b)
    when(io.boundaryInReq(b).fire) {
      bPending(b) := true.B
      bSrc(b) := io.boundaryInReq(b).bits.src
      bId(b) := io.boundaryInReq(b).bits.id
    }
    io.boundaryInRsp(b).valid := bPending(b)
    io.boundaryInRsp(b).bits.src := bSrc(b)
    io.boundaryInRsp(b).bits.id := bId(b)
    io.boundaryInRsp(b).bits.data := 0.U
    io.boundaryInRsp(b).bits.err := true.B
    io.boundaryInRsp(b).bits.vc := 0.U
    when(io.boundaryInRsp(b).fire) { bPending(b) := false.B }
  }

  for (o <- pp.outLinks.indices) {
    io.boundaryOutReq(o).valid := false.B
    io.boundaryOutReq(o).bits := 0.U.asTypeOf(chiselTypeOf(io.boundaryOutReq(o).bits))
    io.boundaryOutRsp(o).ready := true.B
    io.boundaryOutRspFwd(o).valid := false.B
    io.boundaryOutRspFwd(o).bits := 0.U.asTypeOf(chiselTypeOf(io.boundaryOutRspFwd(o).bits))
  }

  for (b <- pp.inLinks.indices) {
    io.boundaryInRspFwd(b).ready := true.B
  }

  for (m <- pp.masters.indices) io.perf.returnSlots(m) := 0.U
  for (s <- pp.slaves.indices) io.perf.respSlots(s) := 0.U
}

