package loom.rtl

import chisel3._
import chisel3.util._
import loom.ir.LoomDefaults

/** Fields shared by the two request-direction channels: both pick an output
  * from their own address and carry the scheduling VC.
  */
abstract class LmAddrReq(val addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val vc = UInt(LoomDefaults.VcWidth.W)
}

/** Read address channel: one transaction per read burst. */
class LmRdReq(addrWidth: Int, idWidth: Int, tagWidth: Int = LoomDefaults.AxiIdWidth)
    extends LmAddrReq(addrWidth) {
  val len = UInt(8.W)
  val src = UInt(idWidth.W)
  val id = UInt(tagWidth.W)
}

/** Write data channel: one transaction per write beat. Every beat carries its
  * own address, so a burst may take different paths hop by hop.
  */
class LmWrData(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    tagWidth: Int = LoomDefaults.AxiIdWidth
) extends LmAddrReq(addrWidth) {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val src = UInt(idWidth.W)
  val id = UInt(tagWidth.W)
  val last = Bool()
}

/** Fields shared by the two response-direction channels: both are routed back
  * to the issuing master only.
  */
abstract class LmSrcRouted(val idWidth: Int) extends Bundle {
  val src = UInt(idWidth.W)
  val vc = UInt(LoomDefaults.VcWidth.W)
}

/** Read data channel: one transaction per read beat. */
class LmRdData(dataWidth: Int, idWidth: Int, tagWidth: Int = LoomDefaults.AxiIdWidth)
    extends LmSrcRouted(idWidth) {
  val data = UInt(dataWidth.W)
  val id = UInt(tagWidth.W)
  val last = Bool()
  val err = Bool()
}

/** Write response channel: one transaction per write burst, no data. */
class LmWrResp(idWidth: Int, tagWidth: Int = LoomDefaults.AxiIdWidth)
    extends LmSrcRouted(idWidth) {
  val id = UInt(tagWidth.W)
  val err = Bool()
}

/** Round-robin arbiter for an arbitrary radix (including 1). */
class LmRrArbiterN(n: Int) extends Module {
  require(n >= 1, "radix must be >= 1")
  private val w = math.max(1, log2Ceil(n))

  val io = IO(new Bundle {
    val req = Input(Vec(n, Bool()))
    val valid = Output(Bool())
    val grant = Output(UInt(w.W))
  })

  if (n == 1) {
    io.valid := io.req(0)
    io.grant := 0.U
  } else {
    val ptr = RegInit(0.U(w.W))
    val rotated = Wire(Vec(n, Bool()))

    for (k <- 0 until n) {
      val raw = ptr +& k.U
      val idx = Mux(raw >= n.U, raw - n.U, raw)(w - 1, 0)
      rotated(k) := io.req(idx)
    }

    val sel = PriorityEncoder(rotated.asUInt)
    val rawNext = ptr +& sel
    val grantWire = Mux(rawNext >= n.U, rawNext - n.U, rawNext)(w - 1, 0)

    io.valid := io.req.asUInt.orR
    io.grant := grantWire

    when(io.valid) {
      ptr := Mux(grantWire === (n - 1).U, 0.U, grantWire + 1.U)
    }
  }
}

/** Transparent N-stage Decoupled pipe. Stage count 0 is a direct wire. */
class LmPipe[T <: Data](gen: T, stages: Int) extends Module {
  require(stages >= 0, "pipe stages must be >= 0")
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(gen))
    val out = Decoupled(gen)
  })

  if (stages == 0) {
    io.out <> io.in
  } else {
    val queues = (0 until stages).map(_ =>
      Module(new Queue(gen.cloneType, 1)))
    queues.head.io.enq <> io.in
    for (i <- 1 until stages) queues(i).io.enq <> queues(i - 1).io.deq
    io.out <> queues.last.io.deq
  }
}

/** MNet/SNet shared-port token: at most one transaction per channel per
  * cycle, round-robin across the endpoints bound to that net.
  */
class LmNetToken(nPorts: Int) extends Module {
  require(nPorts >= 1, "a net needs at least one port")

  val io = IO(new Bundle {
    val req = Input(Vec(nPorts, Bool()))
    val ok = Output(Vec(nPorts, Bool()))
  })

  private val arb = Module(new LmRrArbiterN(nPorts))
  arb.io.req := io.req
  for (i <- 0 until nPorts) io.ok(i) := arb.io.valid && (arb.io.grant === i.U)
}

/** One request-direction channel: per-input x VC queues, three-level
  * arbitration (input RR per (out, vc) -> class-internal half RR -> class
  * WRR), address-window routing and the dateline half flip on cut links.
  *
  * `lenOf` is set for the read-address channel, the only channel that carries
  * a burst length; the per-beat write channel has no packet to bound.
  */
class LmReqSwitch[T <: LmAddrReq](
    gen: T,
    p: NetworkParams,
    spec: NetRouterSpec,
    lenOf: Option[T => UInt] = None
) extends Module {
  private val nClasses = p.classWeights.length
  private val slaveBits = math.max(1, log2Ceil(p.nSlave))
  private val outW = math.max(1, log2Ceil(spec.nOut))
  private val inW = math.max(1, log2Ceil(spec.nIn))
  private val nVc = nClasses * LoomDefaults.VcHalves

  val io = IO(new Bundle {
    val in = Vec(spec.nIn, Flipped(Decoupled(gen)))
    val out = Vec(spec.nOut, Decoupled(gen))
  })

  private val chType = chiselTypeOf(io.out(0).bits)
  private val routeSlaveVec = VecInit(spec.routeSlave.map(_.U(outW.W)))

  // One VC queue per (input port, vc). VC id = class * 2 + dateline half;
  // a cut link flips the half from 0 to 1 and never back.
  private val vc = Seq.tabulate(spec.nIn) { _ =>
    Seq.tabulate(nVc) { v =>
      Module(new LmVcQueue(chType, p.classDepth(v / LoomDefaults.VcHalves)))
    }
  }

  for (i <- 0 until spec.nIn) {
    io.in(i).ready := VecInit(vc(i).map(_.io.enq.ready))(io.in(i).bits.vc)
    for (v <- 0 until nVc) {
      vc(i)(v).io.enq.valid := io.in(i).valid && (io.in(i).bits.vc === v.U)
      vc(i)(v).io.enq.bits := io.in(i).bits
    }
    lenOf.foreach { len =>
      val cls = io.in(i).bits.vc >> 1
      when(io.in(i).fire) {
        assert(len(io.in(i).bits) +& 1.U <=
          VecInit(p.classMaxPacket.map(_.U(8.W)))(cls), "LM-G6: request exceeds class maxPacketBeats")
        when(cls === LoomDefaults.QosOrder.indexOf("ctl").U) {
          assert(len(io.in(i).bits) === 0.U, "LM-G7: ctl request must be single-beat")
        }
      }
    }
    assert(!io.in(i).valid || io.in(i).bits.vc < nVc.U,
      "LM-NET: request carries an unknown VC")
  }

  private val outReady = Wire(Vec(spec.nOut, Bool()))
  private val coreValid = Wire(Vec(spec.nOut, Bool()))
  private val coreBits = Wire(Vec(spec.nOut, chType))
  // Every output keeps one register stage. Besides timing, this decouples
  // out.bits from out.ready: on a ring the ready chain otherwise folds back
  // into the VC select of the upstream router.
  private val bankStage: Seq[LmPipe[T]] = Seq.tabulate(spec.nOut) { _ =>
    Module(new LmPipe(chType, 1))
  }

  for (j <- 0 until spec.nOut) outReady(j) := bankStage(j).io.in.ready

  // Three levels: input arbitration per (out, vc), one RR across the two
  // halves of a class, then the weighted class schedule.
  private val vcArb = Seq.tabulate(spec.nOut) { j =>
    Seq.tabulate(nVc) { v =>
      val arb = Module(new LmRrArbiterN(spec.nIn))
      val slaveOf = Wire(Vec(spec.nIn, UInt(slaveBits.W)))
      for (i <- 0 until spec.nIn) {
        slaveOf(i) := LmAddrDecode(vc(i)(v).io.deq.bits.addr, p.addrWidth,
          p.slaveBase, p.slaveSize, slaveBits)
        arb.io.req(i) := vc(i)(v).io.deq.valid &&
          (routeSlaveVec(slaveOf(i)) === j.U) &&
          outReady(j)
      }
      arb
    }
  }

  private val halfSel = Seq.tabulate(spec.nOut) { j =>
    Seq.tabulate(nClasses) { c =>
      val arb = Module(new LmRrArbiterN(LoomDefaults.VcHalves))
      for (h <- 0 until LoomDefaults.VcHalves) {
        arb.io.req(h) := vcArb(j)(c * LoomDefaults.VcHalves + h).io.valid
      }
      arb
    }
  }

  private val classSel = Seq.tabulate(spec.nOut)(_ => Module(new LmWrrArbiter(p.classWeights)))

  private val vcBitsByVc =
    VecInit((0 until nVc).map(v => VecInit(vc.map(_(v).io.deq.bits))))
  private val outFire = Wire(Vec(spec.nOut, Bool()))
  private val selVc = Wire(Vec(spec.nOut, UInt(LoomDefaults.VcWidth.W)))
  private val selIn = Wire(Vec(spec.nOut, UInt(inW.W)))

  for (j <- 0 until spec.nOut) {
    for (c <- 0 until nClasses) classSel(j).io.req(c) := halfSel(j)(c).io.valid
    coreValid(j) := classSel(j).io.valid
    val half = VecInit(halfSel(j).map(_.io.grant))(classSel(j).io.grant)
    selVc(j) := Cat(classSel(j).io.grant, half)
    selIn(j) := VecInit(vcArb(j).map(_.io.grant))(selVc(j))
    val row = vcBitsByVc(selVc(j))
    coreBits(j) := (if (spec.nIn == 1) row(0) else row(selIn(j)))
    val slavePorts = spec.outSlave.count(_ >= 0)
    val cut = if (j >= slavePorts) spec.outCut(j - slavePorts) else false
    when(cut.B) { coreBits(j).vc := Cat(classSel(j).io.grant, 1.U(1.W)) }
    outFire(j) := classSel(j).io.valid && outReady(j)
  }

  // Banking groups output muxes and adds one register stage per output; it
  // does not add arbitration or change routing.
  for (j <- 0 until spec.nOut) {
    bankStage(j).io.in.valid := coreValid(j)
    bankStage(j).io.in.bits := coreBits(j)
    io.out(j) <> bankStage(j).io.out
  }

  for (i <- 0 until spec.nIn; v <- 0 until nVc) {
    vc(i)(v).io.deq.ready := (0 until spec.nOut)
      .map(j => outFire(j) && (selVc(j) === v.U) && (selIn(j) === i.U))
      .reduce(_ || _)
  }
}

/** One response-direction channel: sources are the local slave ports and the
  * forward hops arriving on links; destinations are the master ports (in side)
  * and the link ports (in side for a reverse hop, out side for a forward hop).
  * Like the request side, each source has per-VC-half queues and each
  * destination arbitrates input RR -> half RR -> class WRR, then flips the
  * dateline half to 1 on a cut crossing (reverse hops use the reverse IR
  * link's cut, forward hops the out-link's cut).
  */
class LmRspSwitch[T <: LmSrcRouted](gen: T, p: NetworkParams, spec: NetRouterSpec) extends Module {
  private val nClasses = p.classWeights.length
  private val nVc = nClasses * LoomDefaults.VcHalves
  private val nMasterPorts = spec.inMaster.count(_ >= 0)
  private val nSlavePorts = spec.outSlave.count(_ >= 0)
  private val nLinkPorts = spec.nOut - nSlavePorts
  private val nFwdIn = spec.nIn - nMasterPorts
  private val nSrcs = spec.nOut + nFwdIn
  private val nDests = spec.nIn + nLinkPorts
  private val destW = math.max(1, log2Ceil(math.max(1, nDests)))
  private val srcW = math.max(1, log2Ceil(math.max(1, nSrcs)))

  val io = IO(new Bundle {
    val out = Vec(spec.nOut, Flipped(Decoupled(gen)))
    val outFwd = Vec(spec.nOut, Decoupled(gen))
    val in = Vec(spec.nIn, Decoupled(gen))
    val inFwd = Vec(spec.nIn, Flipped(Decoupled(gen)))
  })

  private val chType = chiselTypeOf(io.out(0).bits)
  private val respDest = p.masterClass.indices.map { m =>
    val r = if (m < spec.routeMaster.size) spec.routeMaster(m) else -1
    val f = if (m < spec.routeMasterFwd.size) spec.routeMasterFwd(m) else -1
    if (r >= 0) r
    else if (f >= nSlavePorts) spec.nIn + (f - nSlavePorts)
    else 0
  }
  private val destVec = VecInit(respDest.map(_.U(destW.W)))

  private val srcPorts: Seq[DecoupledIO[T]] =
    (0 until spec.nOut).map(io.out(_)) ++ (nMasterPorts until spec.nIn).map(io.inFwd(_))

  // One VC queue per (source, vc); a response waits in its own half.
  private val vc = Seq.tabulate(nSrcs) { _ =>
    Seq.tabulate(nVc) { v =>
      Module(new LmVcQueue(chType, p.classDepth(v / LoomDefaults.VcHalves)))
    }
  }

  for (s <- 0 until nSrcs) {
    srcPorts(s).ready := VecInit(vc(s).map(_.io.enq.ready))(srcPorts(s).bits.vc)
    for (v <- 0 until nVc) {
      vc(s)(v).io.enq.valid := srcPorts(s).valid && (srcPorts(s).bits.vc === v.U)
      vc(s)(v).io.enq.bits := srcPorts(s).bits
    }
    assert(!srcPorts(s).valid || srcPorts(s).bits.vc < nVc.U,
      "LM-NET: response carries an unknown VC")
  }

  private val destReady = Wire(Vec(nDests, Bool()))
  private val coreValid = Wire(Vec(nDests, Bool()))
  private val coreBits = Wire(Vec(nDests, chType))
  // One register stage per destination decouples valid from ready, so the
  // response channel closes no combinational loop around a ring.
  private val bankStage: Seq[LmPipe[T]] = Seq.tabulate(nDests)(_ => Module(new LmPipe(chType, 1)))
  for (d <- 0 until nDests) destReady(d) := bankStage(d).io.in.ready

  private val vcArb = Seq.tabulate(nDests) { d =>
    Seq.tabulate(nVc) { v =>
      val arb = Module(new LmRrArbiterN(nSrcs))
      for (s <- 0 until nSrcs) {
        arb.io.req(s) := vc(s)(v).io.deq.valid &&
          (destVec(vc(s)(v).io.deq.bits.src) === d.U) &&
          destReady(d)
      }
      arb
    }
  }

  private val halfSel = Seq.tabulate(nDests) { d =>
    Seq.tabulate(nClasses) { c =>
      val arb = Module(new LmRrArbiterN(LoomDefaults.VcHalves))
      for (h <- 0 until LoomDefaults.VcHalves) {
        arb.io.req(h) := vcArb(d)(c * LoomDefaults.VcHalves + h).io.valid
      }
      arb
    }
  }

  private val classSel = Seq.tabulate(nDests)(_ => Module(new LmWrrArbiter(p.classWeights)))

  private val vcBitsByVc =
    VecInit((0 until nVc).map(v => VecInit(vc.map(_(v).io.deq.bits))))
  private val destFire = Wire(Vec(nDests, Bool()))
  private val selVc = Wire(Vec(nDests, UInt(LoomDefaults.VcWidth.W)))
  private val selSrc = Wire(Vec(nDests, UInt(srcW.W)))

  for (d <- 0 until nDests) {
    for (c <- 0 until nClasses) classSel(d).io.req(c) := halfSel(d)(c).io.valid
    coreValid(d) := classSel(d).io.valid
    val half = VecInit(halfSel(d).map(_.io.grant))(classSel(d).io.grant)
    selVc(d) := Cat(classSel(d).io.grant, half)
    selSrc(d) := VecInit(vcArb(d).map(_.io.grant))(selVc(d))
    val row = vcBitsByVc(selVc(d))
    coreBits(d) := (if (nSrcs == 1) row(0) else row(selSrc(d)))
    val cut =
      if (d < nMasterPorts) false
      else if (d < spec.nIn) {
        val k = d - nMasterPorts
        if (k < spec.inCut.size) spec.inCut(k) else false
      } else spec.outCut(d - spec.nIn)
    when(cut.B) { coreBits(d).vc := Cat(classSel(d).io.grant, 1.U(1.W)) }
    destFire(d) := classSel(d).io.valid && destReady(d)
  }

  for (d <- 0 until nDests) {
    bankStage(d).io.in.valid := coreValid(d)
    bankStage(d).io.in.bits := coreBits(d)
  }
  for (d <- 0 until spec.nIn) io.in(d) <> bankStage(d).io.out
  for (k <- 0 until nLinkPorts) io.outFwd(nSlavePorts + k) <> bankStage(spec.nIn + k).io.out

  for (s <- 0 until nSrcs; v <- 0 until nVc) {
    vc(s)(v).io.deq.ready := (0 until nDests)
      .map(d => destFire(d) && (selVc(d) === v.U) && (selSrc(d) === s.U))
      .reduce(_ || _)
  }

  for (i <- 0 until nMasterPorts) io.inFwd(i).ready := true.B
  for (j <- 0 until nSlavePorts) {
    io.outFwd(j).valid := false.B
    io.outFwd(j).bits := 0.U.asTypeOf(chType)
  }
}

/** Router with explicit in/out port layout and route tables from NetworkParams.
  *
  * The four channels are physically independent: each has its own queues,
  * arbitration and handshake, so no channel can block another. End-to-end
  * credits and response slots live in the MNet/SNet NIs, so the router itself
  * is buffer-free apart from the VC queues and the output stage.
  */
class LmNetRouter(p: NetworkParams, spec: NetRouterSpec) extends Module {
  private def rdReqT = new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)
  private def wrDataT = new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)
  private def rdDataT = new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)
  private def wrRespT = new LmWrResp(p.idWidth, p.tagWidth)

  val io = IO(new Bundle {
    val inRdReq = Vec(spec.nIn, Flipped(Decoupled(rdReqT)))
    val outRdReq = Vec(spec.nOut, Decoupled(rdReqT))
    val inWrData = Vec(spec.nIn, Flipped(Decoupled(wrDataT)))
    val outWrData = Vec(spec.nOut, Decoupled(wrDataT))
    val outRdData = Vec(spec.nOut, Flipped(Decoupled(rdDataT)))
    val inRdData = Vec(spec.nIn, Decoupled(rdDataT))
    val outRdDataFwd = Vec(spec.nOut, Decoupled(rdDataT))
    val inRdDataFwd = Vec(spec.nIn, Flipped(Decoupled(rdDataT)))
    val outWrResp = Vec(spec.nOut, Flipped(Decoupled(wrRespT)))
    val inWrResp = Vec(spec.nIn, Decoupled(wrRespT))
    val outWrRespFwd = Vec(spec.nOut, Decoupled(wrRespT))
    val inWrRespFwd = Vec(spec.nIn, Flipped(Decoupled(wrRespT)))
  })

  private val rdReqSw = Module(new LmReqSwitch(rdReqT, p, spec, Some((b: LmRdReq) => b.len)))
  rdReqSw.io.in <> io.inRdReq
  io.outRdReq <> rdReqSw.io.out

  private val wrDataSw = Module(new LmReqSwitch(wrDataT, p, spec))
  wrDataSw.io.in <> io.inWrData
  io.outWrData <> wrDataSw.io.out

  private val rdDataSw = Module(new LmRspSwitch(rdDataT, p, spec))
  rdDataSw.io.out <> io.outRdData
  io.inRdData <> rdDataSw.io.in
  io.outRdDataFwd <> rdDataSw.io.outFwd
  rdDataSw.io.inFwd <> io.inRdDataFwd

  private val wrRespSw = Module(new LmRspSwitch(wrRespT, p, spec))
  wrRespSw.io.out <> io.outWrResp
  io.inWrResp <> wrRespSw.io.in
  io.outWrRespFwd <> wrRespSw.io.outFwd
  wrRespSw.io.inFwd <> io.inWrRespFwd
}

/** Master-side NI: stamps the issuing master and the scheduling VC on both
  * request channels and owns the return-slot credit. A slot is taken by
  * either request and given back by either response; the channels themselves
  * stay independent.
  */
class LmMasterNi(p: NetworkParams, master: Int) extends Module {
  private val retW = math.max(1, log2Ceil(p.masterReturnSlots(master) + 1))
  private val vc = (p.masterClass(master) * LoomDefaults.VcHalves).U

  val io = IO(new Bundle {
    val inRdReq = Flipped(Decoupled(new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)))
    val inWrData = Flipped(Decoupled(new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))
    val outRdData = Decoupled(new LmRdData(p.dataWidth, p.idWidth, p.tagWidth))
    val outWrResp = Decoupled(new LmWrResp(p.idWidth, p.tagWidth))
    val outRdReq = Decoupled(new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth))
    val outWrData = Decoupled(new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth))
    val inRdData = Flipped(Decoupled(new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)))
    val inWrResp = Flipped(Decoupled(new LmWrResp(p.idWidth, p.tagWidth)))
    val credit = Output(UInt(retW.W))
  })

  private val credit = Module(new LmCreditMgr(p.masterReturnSlots(master)))

  // Both request channels can allocate in the same cycle, so admission is
  // checked for the pair.
  private val twoFree = credit.io.count +& 2.U <= p.masterReturnSlots(master).U
  private val reqOk =
    credit.io.canAlloc && (!(io.inRdReq.valid && io.inWrData.valid) || twoFree)

  io.outRdReq.valid := io.inRdReq.valid && reqOk
  io.outRdReq.bits := io.inRdReq.bits
  io.outRdReq.bits.src := master.U
  io.outRdReq.bits.vc := vc
  io.inRdReq.ready := io.outRdReq.ready && reqOk

  io.outWrData.valid := io.inWrData.valid && reqOk
  io.outWrData.bits := io.inWrData.bits
  io.outWrData.bits.src := master.U
  io.outWrData.bits.vc := vc
  io.inWrData.ready := io.outWrData.ready && reqOk

  LmWire.link(io.inRdData, io.outRdData)
  LmWire.link(io.inWrResp, io.outWrResp)

  credit.io.alloc := io.outRdReq.fire.asUInt +& io.outWrData.fire.asUInt
  credit.io.release := io.inRdData.fire.asUInt +& io.inWrResp.fire.asUInt
  io.credit := credit.io.count

  assert(!io.inRdReq.valid || !io.inRdReq.ready || credit.io.canAlloc,
    "LM-P3: accepted request without a return slot")
  assert(!io.inWrData.valid || !io.inWrData.ready || credit.io.canAlloc,
    "LM-P3: accepted request without a return slot")
}

/** Slave-side NI: stamps the response VC from the issuing master and owns the
  * destination response slot. A slot is taken by either request and given back
  * by either response, so neither channel can block the other.
  */
class LmSlaveNi(p: NetworkParams, slave: Int) extends Module {
  private val slotW = math.max(1, log2Ceil(p.slaveRespSlots(slave) + 1))
  private val rspVc = p.masterClass.map(c => (c * LoomDefaults.VcHalves).U(LoomDefaults.VcWidth.W))

  val io = IO(new Bundle {
    val inRdReq = Flipped(Decoupled(new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)))
    val inWrData = Flipped(Decoupled(new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))
    val outRdData = Decoupled(new LmRdData(p.dataWidth, p.idWidth, p.tagWidth))
    val outWrResp = Decoupled(new LmWrResp(p.idWidth, p.tagWidth))
    val outRdReq = Decoupled(new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth))
    val outWrData = Decoupled(new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth))
    val inRdData = Flipped(Decoupled(new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)))
    val inWrResp = Flipped(Decoupled(new LmWrResp(p.idWidth, p.tagWidth)))
    val slot = Output(UInt(slotW.W))
  })

  private val slot = Module(new LmSlotPool(p.slaveRespSlots(slave)))

  private val twoFree = slot.io.count +& 2.U <= p.slaveRespSlots(slave).U
  private val reqOk =
    slot.io.canAlloc && (!(io.inRdReq.valid && io.inWrData.valid) || twoFree)

  io.outRdReq.valid := io.inRdReq.valid && reqOk
  io.outRdReq.bits := io.inRdReq.bits
  io.inRdReq.ready := io.outRdReq.ready && reqOk

  io.outWrData.valid := io.inWrData.valid && reqOk
  io.outWrData.bits := io.inWrData.bits
  io.inWrData.ready := io.outWrData.ready && reqOk

  io.outRdData.valid := io.inRdData.valid
  io.outRdData.bits := io.inRdData.bits
  io.outRdData.bits.vc := VecInit(rspVc)(io.inRdData.bits.src)
  io.inRdData.ready := io.outRdData.ready

  io.outWrResp.valid := io.inWrResp.valid
  io.outWrResp.bits := io.inWrResp.bits
  io.outWrResp.bits.vc := VecInit(rspVc)(io.inWrResp.bits.src)
  io.inWrResp.ready := io.outWrResp.ready

  slot.io.alloc := io.outRdReq.fire.asUInt +& io.outWrData.fire.asUInt
  slot.io.release := io.inRdData.fire.asUInt +& io.inWrResp.fire.asUInt
  io.slot := slot.io.count

  assert(!io.inRdReq.valid || !io.inRdReq.ready || slot.io.canAlloc,
    "LM-P3: accepted request without a response slot")
  assert(!io.inWrData.valid || !io.inWrData.ready || slot.io.canAlloc,
    "LM-P3: accepted request without a response slot")
}

/** Router-to-router link: four independent channel groups. Requests take the
  * configured pipe stages, responses always at least one register; an async
  * link gets one CDC FIFO per channel and direction.
  */
class LmRouterLink(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    pipe: Int,
    isAsync: Boolean,
    asyncDepth: Int,
    tagWidth: Int = LoomDefaults.AxiIdWidth
) extends Module {
  require(pipe >= 0 && pipe <= 8)
  require(!isAsync || (asyncDepth >= 2 && (asyncDepth & (asyncDepth - 1)) == 0),
    "async link depth must be a power of two >= 2")

  private def rdReqT = new LmRdReq(addrWidth, idWidth, tagWidth)
  private def wrDataT = new LmWrData(addrWidth, dataWidth, idWidth, tagWidth)
  private def rdDataT = new LmRdData(dataWidth, idWidth, tagWidth)
  private def wrRespT = new LmWrResp(idWidth, tagWidth)

  val io = IO(new Bundle {
    val aClk = Input(Bool())
    val aReset = Input(Bool())
    val bClk = Input(Bool())
    val bReset = Input(Bool())
    val aRdReq = Flipped(Decoupled(rdReqT))
    val bRdReq = Decoupled(rdReqT)
    val aWrData = Flipped(Decoupled(wrDataT))
    val bWrData = Decoupled(wrDataT)
    val bRdData = Flipped(Decoupled(rdDataT))
    val aRdData = Decoupled(rdDataT)
    val bWrResp = Flipped(Decoupled(wrRespT))
    val aWrResp = Decoupled(wrRespT)
    val aRdDataFwd = Flipped(Decoupled(rdDataT))
    val bRdDataFwd = Decoupled(rdDataT)
    val aWrRespFwd = Flipped(Decoupled(wrRespT))
    val bWrRespFwd = Decoupled(wrRespT)
    val creditUsed = Output(UInt(16.W))
  })

  private def stage[T <: Data](gen: T, stages: Int, in: DecoupledIO[T], out: DecoupledIO[T]): Unit = {
    val p = Module(new LmPipe(gen, stages))
    p.io.in <> in
    out <> p.io.out
  }

  private def cdc[T <: Data](
      gen: T,
      srcClk: Bool,
      srcReset: Bool,
      dstClk: Bool,
      dstReset: Bool,
      in: DecoupledIO[T],
      out: DecoupledIO[T]
  ): UInt = {
    val link = withClockAndReset(srcClk.asClock, srcReset) {
      Module(new LmAsyncLink(gen.getWidth, asyncDepth))
    }
    link.io.dstClk := dstClk
    link.io.dstReset := dstReset
    link.io.in.valid := in.valid
    link.io.in.bits := in.bits.asUInt
    in.ready := link.io.in.ready
    out.valid := link.io.out.valid
    out.bits := link.io.out.bits.asTypeOf(chiselTypeOf(out.bits))
    link.io.out.ready := out.ready
    link.io.creditUsed
  }

  if (!isAsync) {
    stage(rdReqT, pipe, io.aRdReq, io.bRdReq)
    stage(wrDataT, pipe, io.aWrData, io.bWrData)
    // Responses always get at least one register: on a ring the response
    // channel closes a combinational loop around the routers.
    stage(rdDataT, math.max(1, pipe), io.bRdData, io.aRdData)
    stage(wrRespT, math.max(1, pipe), io.bWrResp, io.aWrResp)
    stage(rdDataT, math.max(1, pipe), io.aRdDataFwd, io.bRdDataFwd)
    stage(wrRespT, math.max(1, pipe), io.aWrRespFwd, io.bWrRespFwd)
    io.creditUsed := 0.U
  } else {
    val credits = Seq(
      cdc(rdReqT, io.aClk, io.aReset, io.bClk, io.bReset, io.aRdReq, io.bRdReq),
      cdc(wrDataT, io.aClk, io.aReset, io.bClk, io.bReset, io.aWrData, io.bWrData),
      cdc(rdDataT, io.bClk, io.bReset, io.aClk, io.aReset, io.bRdData, io.aRdData),
      cdc(wrRespT, io.bClk, io.bReset, io.aClk, io.aReset, io.bWrResp, io.aWrResp),
      cdc(rdDataT, io.aClk, io.aReset, io.bClk, io.bReset, io.aRdDataFwd, io.bRdDataFwd),
      cdc(wrRespT, io.aClk, io.aReset, io.bClk, io.bReset, io.aWrRespFwd, io.bWrRespFwd)
    )
    io.creditUsed := credits.reduce(_ +& _)(15, 0)
  }
}

/** Multi-router top. All routers currently share the implicit clock; async
  * RouterLink is verified standalone and wired into a multi-clock top later.
  */
class LmNetworkTop(p: NetworkParams) extends Module {
  require(!p.links.exists(_.async),
    "async multi-router top is not supported yet; use LmRouterLink standalone")

  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  private def rdReqT = new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)
  private def wrDataT = new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)
  private def rdDataT = new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)
  private def wrRespT = new LmWrResp(p.idWidth, p.tagWidth)

  val io = IO(new Bundle {
    val masterRdReq = Vec(p.nMaster, Flipped(Decoupled(rdReqT)))
    val masterWrData = Vec(p.nMaster, Flipped(Decoupled(wrDataT)))
    val masterRdData = Vec(p.nMaster, Decoupled(rdDataT))
    val masterWrResp = Vec(p.nMaster, Decoupled(wrRespT))
    val slaveRdReq = Vec(p.nSlave, Decoupled(rdReqT))
    val slaveWrData = Vec(p.nSlave, Decoupled(wrDataT))
    val slaveRdData = Vec(p.nSlave, Flipped(Decoupled(rdDataT)))
    val slaveWrResp = Vec(p.nSlave, Flipped(Decoupled(wrRespT)))
    val perf = Output(new Bundle {
      val returnSlots = Vec(p.nMaster, UInt(maxRetW.W))
      val respSlots = Vec(p.nSlave, UInt(maxRspW.W))
      val rdRequests = UInt(32.W)
      val wrBeats = UInt(32.W)
      val rdResponses = UInt(32.W)
      val wrResponses = UInt(32.W)
      val errors = UInt(32.W)
      val errSticky = Bool()
    })
  })

  private val rdReqCnt = RegInit(0.U(32.W))
  private val wrBeatCnt = RegInit(0.U(32.W))
  private val rdRspCnt = RegInit(0.U(32.W))
  private val wrRspCnt = RegInit(0.U(32.W))
  private val errCnt = RegInit(0.U(32.W))
  private val errSticky = RegInit(false.B)
  private val mNi = Seq.tabulate(p.nMaster)(i => Module(new LmMasterNi(p, i)))
  private val sNi = Seq.tabulate(p.nSlave)(j => Module(new LmSlaveNi(p, j)))
  private val routers = p.routers.map(spec => Module(new LmNetRouter(p, spec)))
  private val links = p.links.map(l =>
    Module(new LmRouterLink(p.addrWidth, p.dataWidth, p.idWidth,
      l.pipe, l.async, l.asyncFifoDepth, p.tagWidth)))

  // MNet/SNet are shared ports: one transaction per channel and direction per
  // net per cycle. Ports stay per endpoint; the tokens enforce the shared
  // bandwidth.
  private val mnetRdReqTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val mnetWrDataTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val mnetRdDataTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val mnetWrRespTok = Seq.tabulate(p.nMnet)(_ => Module(new LmNetToken(p.nMaster)))
  private val snetRdReqTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))
  private val snetWrDataTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))
  private val snetRdDataTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))
  private val snetWrRespTok = Seq.tabulate(p.nSnet)(_ => Module(new LmNetToken(p.nSlave)))

  // One-beat skid per slave and request channel breaks the token<->ready
  // combinational path: the router sees only the skid's capacity, while the
  // SNet token is decided from the skid output.
  private val sSkidRd = Seq.tabulate(p.nSlave)(_ => Module(new LmPipe(rdReqT, 1)))
  private val sSkidWr = Seq.tabulate(p.nSlave)(_ => Module(new LmPipe(wrDataT, 1)))
  for (j <- 0 until p.nSlave) {
    routers(p.slaveRouter(j)).io.outRdReq(p.slavePort(j)) <> sSkidRd(j).io.in
    routers(p.slaveRouter(j)).io.outWrData(p.slavePort(j)) <> sSkidWr(j).io.in
  }

  for (m <- 0 until p.nMnet; i <- 0 until p.nMaster) {
    val onNet = (p.masterMnet(i) == m).B
    mnetRdReqTok(m).io.req(i) := mNi(i).io.outRdReq.valid && onNet
    mnetWrDataTok(m).io.req(i) := mNi(i).io.outWrData.valid && onNet
    mnetRdDataTok(m).io.req(i) :=
      routers(p.masterRouter(i)).io.inRdData(p.masterPort(i)).valid && onNet
    mnetWrRespTok(m).io.req(i) :=
      routers(p.masterRouter(i)).io.inWrResp(p.masterPort(i)).valid && onNet
  }
  for (s <- 0 until p.nSnet; j <- 0 until p.nSlave) {
    val onNet = (p.slaveSnet(j) == s).B
    snetRdReqTok(s).io.req(j) := sSkidRd(j).io.out.valid && onNet
    snetWrDataTok(s).io.req(j) := sSkidWr(j).io.out.valid && onNet
    snetRdDataTok(s).io.req(j) := sNi(j).io.outRdData.valid && onNet
    snetWrRespTok(s).io.req(j) := sNi(j).io.outWrResp.valid && onNet
  }

  private def mnetRdReqOk(i: Int): Bool = mnetRdReqTok(p.masterMnet(i)).io.ok(i)
  private def mnetWrDataOk(i: Int): Bool = mnetWrDataTok(p.masterMnet(i)).io.ok(i)
  private def mnetRdDataOk(i: Int): Bool = mnetRdDataTok(p.masterMnet(i)).io.ok(i)
  private def mnetWrRespOk(i: Int): Bool = mnetWrRespTok(p.masterMnet(i)).io.ok(i)
  private def snetRdReqOk(j: Int): Bool = snetRdReqTok(p.slaveSnet(j)).io.ok(j)
  private def snetWrDataOk(j: Int): Bool = snetWrDataTok(p.slaveSnet(j)).io.ok(j)
  private def snetRdDataOk(j: Int): Bool = snetRdDataTok(p.slaveSnet(j)).io.ok(j)
  private def snetWrRespOk(j: Int): Bool = snetWrRespTok(p.slaveSnet(j)).io.ok(j)

  for (i <- 0 until p.nMaster) {
    val r = p.masterRouter(i)
    val port = p.masterPort(i)
    LmWire.link(io.masterRdReq(i), mNi(i).io.inRdReq)
    LmWire.link(io.masterWrData(i), mNi(i).io.inWrData)
    LmWire.link(mNi(i).io.outRdData, io.masterRdData(i))
    LmWire.link(mNi(i).io.outWrResp, io.masterWrResp(i))

    routers(r).io.inRdReq(port).valid := mNi(i).io.outRdReq.valid && mnetRdReqOk(i)
    routers(r).io.inRdReq(port).bits := mNi(i).io.outRdReq.bits
    mNi(i).io.outRdReq.ready := routers(r).io.inRdReq(port).ready && mnetRdReqOk(i)

    routers(r).io.inWrData(port).valid := mNi(i).io.outWrData.valid && mnetWrDataOk(i)
    routers(r).io.inWrData(port).bits := mNi(i).io.outWrData.bits
    mNi(i).io.outWrData.ready := routers(r).io.inWrData(port).ready && mnetWrDataOk(i)

    routers(r).io.inRdData(port).ready := mNi(i).io.inRdData.ready
    mNi(i).io.inRdData.valid := routers(r).io.inRdData(port).valid && mnetRdDataOk(i)
    mNi(i).io.inRdData.bits := routers(r).io.inRdData(port).bits

    routers(r).io.inWrResp(port).ready := mNi(i).io.inWrResp.ready
    mNi(i).io.inWrResp.valid := routers(r).io.inWrResp(port).valid && mnetWrRespOk(i)
    mNi(i).io.inWrResp.bits := routers(r).io.inWrResp(port).bits

    routers(r).io.inRdDataFwd(port).valid := false.B
    routers(r).io.inRdDataFwd(port).bits :=
      0.U.asTypeOf(chiselTypeOf(routers(r).io.inRdDataFwd(port).bits))
    routers(r).io.inWrRespFwd(port).valid := false.B
    routers(r).io.inWrRespFwd(port).bits :=
      0.U.asTypeOf(chiselTypeOf(routers(r).io.inWrRespFwd(port).bits))

    when(io.masterRdReq(i).fire) { rdReqCnt := rdReqCnt + 1.U }
    when(io.masterWrData(i).fire) { wrBeatCnt := wrBeatCnt + 1.U }
    when(io.masterRdData(i).fire) {
      rdRspCnt := rdRspCnt + 1.U
      when(io.masterRdData(i).bits.err) {
        errCnt := errCnt + 1.U
        errSticky := true.B
      }
    }
    when(io.masterWrResp(i).fire) {
      wrRspCnt := wrRspCnt + 1.U
      when(io.masterWrResp(i).bits.err) {
        errCnt := errCnt + 1.U
        errSticky := true.B
      }
    }

    io.perf.returnSlots(i) := mNi(i).io.credit
  }

  for (j <- 0 until p.nSlave) {
    routers(p.slaveRouter(j)).io.outRdDataFwd(p.slavePort(j)).ready := true.B
    routers(p.slaveRouter(j)).io.outWrRespFwd(p.slavePort(j)).ready := true.B
  }

  for (j <- 0 until p.nSlave) {
    val r = p.slaveRouter(j)
    val port = p.slavePort(j)
    sNi(j).io.inRdReq.valid := sSkidRd(j).io.out.valid && snetRdReqOk(j)
    sNi(j).io.inRdReq.bits := sSkidRd(j).io.out.bits
    sSkidRd(j).io.out.ready := sNi(j).io.inRdReq.ready && snetRdReqOk(j)

    sNi(j).io.inWrData.valid := sSkidWr(j).io.out.valid && snetWrDataOk(j)
    sNi(j).io.inWrData.bits := sSkidWr(j).io.out.bits
    sSkidWr(j).io.out.ready := sNi(j).io.inWrData.ready && snetWrDataOk(j)

    LmWire.link(sNi(j).io.outRdReq, io.slaveRdReq(j))
    LmWire.link(sNi(j).io.outWrData, io.slaveWrData(j))
    LmWire.link(io.slaveRdData(j), sNi(j).io.inRdData)
    LmWire.link(io.slaveWrResp(j), sNi(j).io.inWrResp)

    routers(r).io.outRdData(port).valid := sNi(j).io.outRdData.valid && snetRdDataOk(j)
    routers(r).io.outRdData(port).bits := sNi(j).io.outRdData.bits
    sNi(j).io.outRdData.ready := routers(r).io.outRdData(port).ready && snetRdDataOk(j)

    routers(r).io.outWrResp(port).valid := sNi(j).io.outWrResp.valid && snetWrRespOk(j)
    routers(r).io.outWrResp(port).bits := sNi(j).io.outWrResp.bits
    sNi(j).io.outWrResp.ready := routers(r).io.outWrResp(port).ready && snetWrRespOk(j)

    io.perf.respSlots(j) := sNi(j).io.slot
  }

  for ((l, link) <- p.links.zip(links)) {
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

  io.perf.rdRequests := rdReqCnt
  io.perf.wrBeats := wrBeatCnt
  io.perf.rdResponses := rdRspCnt
  io.perf.wrResponses := wrRspCnt
  io.perf.errors := errCnt
  io.perf.errSticky := errSticky
}
