package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.{LmRrArbiterN, LmWrrArbiter, LmPipe}

/** Internal fabric transport: a CHI flit plus its VC sideband. The VC number
  * `class*nHalf + half` is computed at injection from QoS and carried across
  * routers; the dateline half flips 0->1 on a cut link and never back. Node
  * ports stay pure flit (adapters wrap/unwrap at the fabric boundary).
  */
class ChiVcBundle[T <: Data](gen: T, vcWidth: Int) extends Bundle {
  val flit = gen.cloneType
  val vc = UInt(vcWidth.W)
}

/** Single-channel CHI router with per-VC queues and three-level arbitration
  * (input RR per (out,vc) -> half RR within a class -> class WRR), TgtID
  * routing and the dateline half flip on cut-marked outputs. Mirrors the proven
  * v1 LmReqSwitch structure; VC numbering generalizes to nHalf (AQ-02).
  */
class ChiChannelRouter[T <: Data](
    gen: T,
    nIn: Int,
    nOut: Int,
    routeTable: Seq[Int],
    depth: Int,
    tgtOf: T => UInt,
    qm: ChiQosMap = new ChiQosMap(ChiConfig()),
    cutOut: Seq[Boolean] = Seq.empty
) extends Module {
  require(nIn >= 1 && nOut >= 1, "router needs >= 1 input and output")
  require(depth >= 1, "input queue depth must be >= 1")
  require(routeTable.nonEmpty, "route table must be non-empty")
  private val outW = math.max(1, log2Ceil(nOut))
  private val inW = math.max(1, log2Ceil(nIn))
  private val nClasses = qm.classes.length
  private val nHalf = qm.nHalf
  private val nVc = qm.nVc
  private val vcW = qm.vcWidth
  private val cuts = if (cutOut.isEmpty) Seq.fill(nOut)(false) else cutOut
  require(cuts.length == nOut, "cutOut must match nOut")
  private val weights = (0 until nClasses).map(qm.weightOf)

  val io = IO(new Bundle {
    val in = Vec(nIn, Flipped(Decoupled(new ChiVcBundle(gen, vcW))))
    val out = Vec(nOut, Decoupled(new ChiVcBundle(gen, vcW)))
  })
  private val chType = chiselTypeOf(io.out(0).bits)
  private val routeVec = VecInit(routeTable.map(_.U(outW.W)))

  private val vc = Seq.tabulate(nIn) { _ =>
    Seq.tabulate(nVc) { _ => Module(new Queue(chType, depth)) }
  }
  for (i <- 0 until nIn) {
    io.in(i).ready := VecInit(vc(i).map(_.io.enq.ready))(io.in(i).bits.vc)
    for (v <- 0 until nVc) {
      vc(i)(v).io.enq.valid := io.in(i).valid && (io.in(i).bits.vc === v.U)
      vc(i)(v).io.enq.bits := io.in(i).bits
    }
  }
  private val headDst = Seq.tabulate(nIn) { i =>
    Seq.tabulate(nVc) { v => routeVec(tgtOf(vc(i)(v).io.deq.bits.flit)) }
  }

  // One register stage per output decouples out.bits from out.ready (ring ready
  // chain otherwise folds back into the upstream VC select).
  private val bankStage = Seq.tabulate(nOut) { _ => Module(new LmPipe(chType, 1)) }
  private val outReady = Wire(Vec(nOut, Bool()))
  for (j <- 0 until nOut) outReady(j) := bankStage(j).io.in.ready

  private val vcArb = Seq.tabulate(nOut) { j =>
    Seq.tabulate(nVc) { v =>
      val arb = Module(new LmRrArbiterN(nIn))
      for (i <- 0 until nIn)
        arb.io.req(i) := vc(i)(v).io.deq.valid && (headDst(i)(v) === j.U) && outReady(j)
      arb
    }
  }
  private val halfSel = Seq.tabulate(nOut) { j =>
    Seq.tabulate(nClasses) { cls =>
      val arb = Module(new LmRrArbiterN(nHalf))
      for (h <- 0 until nHalf) arb.io.req(h) := vcArb(j)(cls * nHalf + h).io.valid
      arb
    }
  }
  private val classSel = Seq.tabulate(nOut) { _ => Module(new LmWrrArbiter(weights)) }

  private val vcBitsByVc = VecInit((0 until nVc).map(v => VecInit(vc.map(_(v).io.deq.bits))))
  private val outFire = Wire(Vec(nOut, Bool()))
  private val selVc = Wire(Vec(nOut, UInt(vcW.W)))
  private val selIn = Wire(Vec(nOut, UInt(inW.W)))
  private val coreValid = Wire(Vec(nOut, Bool()))
  private val coreBits = Wire(Vec(nOut, chType))

  for (j <- 0 until nOut) {
    for (cls <- 0 until nClasses) classSel(j).io.req(cls) := halfSel(j)(cls).io.valid
    coreValid(j) := classSel(j).io.valid
    val half = VecInit(halfSel(j).map(_.io.grant))(classSel(j).io.grant)
    selVc(j) := (classSel(j).io.grant * nHalf.U +& half)(vcW - 1, 0)
    selIn(j) := VecInit(vcArb(j).map(_.io.grant))(selVc(j))
    val row = vcBitsByVc(selVc(j))
    coreBits(j) := (if (nIn == 1) row(0) else row(selIn(j)))
    when(cuts(j).B) { coreBits(j).vc := (classSel(j).io.grant * nHalf.U +& 1.U)(vcW - 1, 0) }
    outFire(j) := classSel(j).io.valid && outReady(j)
    bankStage(j).io.in.valid := coreValid(j)
    bankStage(j).io.in.bits := coreBits(j)
    io.out(j) <> bankStage(j).io.out
  }

  for (i <- 0 until nIn; v <- 0 until nVc) {
    vc(i)(v).io.deq.ready := (0 until nOut)
      .map(j => outFire(j) && (selVc(j) === v.U) && (selIn(j) === i.U))
      .reduce(_ || _)
  }
}

/** Wrap a pure node flit into the fabric VC transport at injection (half 0). */
class ChiVcInject[T <: Data](gen: T, qm: ChiQosMap, qosOf: T => UInt) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(gen))
    val out = Decoupled(new ChiVcBundle(gen, qm.vcWidth))
  })
  private val qos = qosOf(io.in.bits)
  private val cls = Mux(qos >= qm.ctlThreshold.U, 0.U,
    Mux(qos >= qm.latThreshold.U, 1.U, 2.U))
  io.out.valid := io.in.valid
  io.out.bits.flit := io.in.bits
  io.out.bits.vc := (cls * qm.nHalf.U)(qm.vcWidth - 1, 0)
  io.in.ready := io.out.ready
}

/** Unwrap the fabric VC transport back to a pure node flit at ejection. */
class ChiVcEject[T <: Data](gen: T, vcWidth: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new ChiVcBundle(gen, vcWidth)))
    val out = Decoupled(gen)
  })
  io.out.valid := io.in.valid
  io.out.bits := io.in.bits.flit
  io.in.ready := io.out.ready
}

/** One router port carrying all four CHI channel classes with VC sideband. */
class ChiRouterPort(c: ChiConfig) extends Bundle {
  private val vw = c.vcWidth
  val rxReq = Flipped(Decoupled(new ChiVcBundle(new ChiReqFlit(c), vw)))
  val rxRsp = Flipped(Decoupled(new ChiVcBundle(new ChiRspFlit(c), vw)))
  val rxSnp = Flipped(Decoupled(new ChiVcBundle(new ChiSnpFlit(c), vw)))
  val rxDat = Flipped(Decoupled(new ChiVcBundle(new ChiDatFlit(c), vw)))
  val txReq = Decoupled(new ChiVcBundle(new ChiReqFlit(c), vw))
  val txRsp = Decoupled(new ChiVcBundle(new ChiRspFlit(c), vw))
  val txSnp = Decoupled(new ChiVcBundle(new ChiSnpFlit(c), vw))
  val txDat = Decoupled(new ChiVcBundle(new ChiDatFlit(c), vw))
}

/** Four-class CHI router. Each channel class switches independently by TgtID
  * with per-VC weighted scheduling. SNP is instantiated only when DVM is
  * generated; otherwise its ports are safely tied off.
  */
class ChiRouter4(
    c: ChiConfig,
    nPort: Int,
    routeReq: Seq[Int],
    routeRsp: Seq[Int],
    routeSnp: Seq[Int],
    routeDat: Seq[Int],
    depth: Int,
    snpEnable: Boolean,
    cutOut: Seq[Boolean] = Seq.empty
) extends Module {
  val io = IO(new Bundle {
    val port = Vec(nPort, new ChiRouterPort(c))
  })
  private val qm = new ChiQosMap(c)

  private val reqR = Module(new ChiChannelRouter(new ChiReqFlit(c), nPort, nPort, routeReq, depth, (f: ChiReqFlit) => f.tgtId, qm, cutOut))
  private val rspR = Module(new ChiChannelRouter(new ChiRspFlit(c), nPort, nPort, routeRsp, depth, (f: ChiRspFlit) => f.tgtId, qm, cutOut))
  private val datR = Module(new ChiChannelRouter(new ChiDatFlit(c), nPort, nPort, routeDat, depth, (f: ChiDatFlit) => f.tgtId, qm, cutOut))

  for (p <- 0 until nPort) {
    reqR.io.in(p) <> io.port(p).rxReq
    io.port(p).txReq <> reqR.io.out(p)
    rspR.io.in(p) <> io.port(p).rxRsp
    io.port(p).txRsp <> rspR.io.out(p)
    datR.io.in(p) <> io.port(p).rxDat
    io.port(p).txDat <> datR.io.out(p)
  }

  if (snpEnable) {
    val snpR = Module(new ChiChannelRouter(new ChiSnpFlit(c), nPort, nPort, routeSnp, depth, (f: ChiSnpFlit) => f.tgtId, qm, cutOut))
    for (p <- 0 until nPort) {
      snpR.io.in(p) <> io.port(p).rxSnp
      io.port(p).txSnp <> snpR.io.out(p)
    }
  } else {
    for (p <- 0 until nPort) {
      io.port(p).rxSnp.ready := true.B
      io.port(p).txSnp.valid := false.B
      io.port(p).txSnp.bits := 0.U.asTypeOf(new ChiVcBundle(new ChiSnpFlit(c), c.vcWidth))
    }
  }
}