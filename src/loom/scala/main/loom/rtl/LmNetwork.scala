package loom.rtl

import chisel3._
import chisel3.util._
import loom.ir.LoomDefaults

/** Request bundle inside a multi-router network. Class is carried per
  * request because a router input port can serve masters of different
  * classes.
  */
class LmNetReq(
    val addrWidth: Int,
    val dataWidth: Int,
    val idWidth: Int,
    val qosWidth: Int,
    val tagWidth: Int = LoomDefaults.AxiIdWidth
) extends Bundle {
  val write = Bool()
  val addr = UInt(addrWidth.W)
  val len = UInt(8.W)
  val src = UInt(idWidth.W)
  val id = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
  val vc = UInt(LoomDefaults.VcWidth.W)
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

/** Router with explicit in/out port layout and route tables from NetworkParams.
  *
  * Requests carry their QoS class. Responses are routed by master index
  * through the reverse route table. End-to-end credits and response slots
  * live in the MNet/SNet NIs, so the router itself is buffer-free.
  */
class LmNetRouter(p: NetworkParams, spec: NetRouterSpec) extends Module {
  private val nClasses = p.classWeights.length
  private val qosW = math.max(1, log2Ceil(nClasses))
  private val slaveBits = math.max(1, log2Ceil(p.nSlave))
  private val outW = math.max(1, log2Ceil(spec.nOut))
  private val inW = math.max(1, log2Ceil(spec.nIn))

  val io = IO(new Bundle {
    val inReq = Vec(spec.nIn, Flipped(Decoupled(
      new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth))))
    val outReq = Vec(spec.nOut, Decoupled(
      new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth)))
    val outRsp = Vec(spec.nOut, Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth))))
    val inRsp = Vec(spec.nIn, Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
    val outRspFwd = Vec(spec.nOut, Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
    val inRspFwd = Vec(spec.nIn, Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth))))
  })

  private val routeSlaveVec = VecInit(spec.routeSlave.map(_.U(outW.W)))

  // One VC queue per (input port, vc). VC id = class * 2 + dateline half;
  // a cut link flips the half from 0 to 1 and never back.
  private val nVc = nClasses * LoomDefaults.VcHalves
  private val vc = Seq.tabulate(spec.nIn) { i =>
    Seq.tabulate(nVc) { v =>
      Module(new LmVcQueue(new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth),
        p.classDepth(v / LoomDefaults.VcHalves)))
    }
  }

  for (i <- 0 until spec.nIn) {
    val cls = io.inReq(i).bits.vc >> 1
    io.inReq(i).ready := VecInit(vc(i).map(_.io.enq.ready))(io.inReq(i).bits.vc)
    for (v <- 0 until nVc) {
      vc(i)(v).io.enq.valid := io.inReq(i).valid && (io.inReq(i).bits.vc === v.U)
      vc(i)(v).io.enq.bits := io.inReq(i).bits
    }
    when(io.inReq(i).fire) {
      assert(io.inReq(i).bits.len +& 1.U <=
        VecInit(p.classMaxPacket.map(_.U(8.W)))(cls), "LM-G6: request exceeds class maxPacketBeats")
      when(cls === LoomDefaults.QosOrder.indexOf("ctl").U) {
        assert(io.inReq(i).bits.len === 0.U, "LM-G7: ctl request must be single-beat")
      }
    }
    assert(!io.inReq(i).valid || io.inReq(i).bits.vc < nVc.U,
      "LM-NET: request carries an unknown VC")
  }

  private val outReady = Wire(Vec(spec.nOut, Bool()))
  private val coreValid = Wire(Vec(spec.nOut, Bool()))
  private val coreBits =
    Wire(Vec(spec.nOut, new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth)))
  // Every output keeps one register stage. Besides timing, this decouples
  // outReq.bits from outReq.ready: on a ring the ready chain otherwise folds
  // back into the VC select of the upstream router.
  private val bankStage: Seq[Option[LmPipe[LmNetReq]]] = Seq.tabulate(spec.nOut) { _ =>
    Some(Module(new LmPipe(new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth), 1)))
  }

  for (j <- 0 until spec.nOut) {
    outReady(j) := bankStage(j).map(_.io.in.ready).getOrElse(io.outReq(j).ready)
  }

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
    bankStage(j) match {
      case Some(st) =>
        st.io.in.valid := coreValid(j)
        st.io.in.bits := coreBits(j)
        io.outReq(j) <> st.io.out
      case None =>
        io.outReq(j).valid := coreValid(j)
        io.outReq(j).bits := coreBits(j)
    }
  }

  for (i <- 0 until spec.nIn; v <- 0 until nVc) {
    vc(i)(v).io.deq.ready := (0 until spec.nOut)
      .map(j => outFire(j) && (selVc(j) === v.U) && (selIn(j) === i.U))
      .reduce(_ || _)
  }

  // Unified response switch. Sources: local slave ports and both response
  // directions arriving on links. Destinations: master ports (in side) and
  // link ports (in side for a reverse hop, out side for a forward hop).
  private val nMasterPorts = spec.inMaster.count(_ >= 0)
  private val nSlavePorts = spec.outSlave.count(_ >= 0)
  private val nLinkPorts = spec.nOut - nSlavePorts
  private val nFwdIn = spec.nIn - nMasterPorts
  private val nSrcs = spec.nOut + nFwdIn
  private val nDests = spec.nIn + nLinkPorts
  private val destW = math.max(1, log2Ceil(math.max(1, nDests)))

  private val respDest = p.masterClass.indices.map { m =>
    val r = if (m < spec.routeMaster.size) spec.routeMaster(m) else -1
    val f = if (m < spec.routeMasterFwd.size) spec.routeMasterFwd(m) else -1
    if (r >= 0) r
    else if (f >= nSlavePorts) spec.nIn + (f - nSlavePorts)
    else 0
  }

  private val srcValid = scala.collection.mutable.ArrayBuffer.empty[Bool]
  private val srcBits = scala.collection.mutable.ArrayBuffer.empty[LmRsp]
  for (j <- 0 until spec.nOut) {
    srcValid += io.outRsp(j).valid
    srcBits += io.outRsp(j).bits
  }
  for (i <- nMasterPorts until spec.nIn) {
    srcValid += io.inRspFwd(i).valid
    srcBits += io.inRspFwd(i).bits
  }

  private val destReady = Wire(Vec(nDests, Bool()))
  private val destValid = Wire(Vec(nDests, Bool()))
  private val destBits = Wire(Vec(nDests, new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
  private val srcDst = Wire(Vec(nSrcs, UInt(destW.W)))
  private val srcReady = Wire(Vec(nSrcs, Bool()))

  for (d <- 0 until spec.nIn) destReady(d) := io.inRsp(d).ready
  for (k <- 0 until nLinkPorts) {
    destReady(spec.nIn + k) := io.outRspFwd(nSlavePorts + k).ready
  }
  for (s <- 0 until nSrcs) {
    srcDst(s) := VecInit(respDest.map(_.U(destW.W)))(srcBits(s).src)
  }

  private val dArb = (0 until nDests).map { d =>
    val arb = Module(new LmRrArbiterN(nSrcs))
    for (s <- 0 until nSrcs) {
      arb.io.req(s) := srcValid(s) && (srcDst(s) === d.U) && destReady(d)
    }
    destValid(d) := arb.io.valid
    destBits(d) := VecInit(srcBits.toSeq)(arb.io.grant)
    arb
  }

  for (s <- 0 until nSrcs) {
    srcReady(s) := (0 until nDests)
      .map(d => dArb(d).io.valid && (dArb(d).io.grant === s.U))
      .reduce(_ || _)
  }

  for (d <- 0 until spec.nIn) {
    io.inRsp(d).valid := destValid(d)
    io.inRsp(d).bits := destBits(d)
  }
  for (k <- 0 until nLinkPorts) {
    io.outRspFwd(nSlavePorts + k).valid := destValid(spec.nIn + k)
    io.outRspFwd(nSlavePorts + k).bits := destBits(spec.nIn + k)
  }
  for (j <- 0 until spec.nOut) io.outRsp(j).ready := srcReady(j)
  for (i <- nMasterPorts until spec.nIn) {
    io.inRspFwd(i).ready := srcReady(spec.nOut + (i - nMasterPorts))
  }
  for (i <- 0 until nMasterPorts) io.inRspFwd(i).ready := true.B
  for (j <- 0 until nSlavePorts) {
    io.outRspFwd(j).valid := false.B
    io.outRspFwd(j).bits := 0.U.asTypeOf(chiselTypeOf(io.outRspFwd(j).bits))
  }
}

/** Master-side NI: owns the return-slot credit and injects the class. */
class LmMasterNi(p: NetworkParams, master: Int) extends Module {
  private val qosW = math.max(1, log2Ceil(p.classWeights.length))
  private val retW = math.max(1, log2Ceil(p.masterReturnSlots(master) + 1))

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))
    val out = Decoupled(new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth))
    val inRsp = Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
    val outRsp = Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth))
    val credit = Output(UInt(retW.W))
  })

  private val credit = Module(new LmCreditMgr(p.masterReturnSlots(master)))

  io.out.valid := io.in.valid && credit.io.canAlloc
  io.out.bits.write := io.in.bits.write
  io.out.bits.addr := io.in.bits.addr
  io.out.bits.len := io.in.bits.len
  io.out.bits.src := master.U
  io.out.bits.id := io.in.bits.id
  io.out.bits.data := io.in.bits.data
  io.out.bits.vc := (p.masterClass(master) * LoomDefaults.VcHalves).U
  io.in.ready := io.out.ready && credit.io.canAlloc

  io.outRsp.valid := io.inRsp.valid
  io.outRsp.bits := io.inRsp.bits
  io.inRsp.ready := io.outRsp.ready

  credit.io.alloc := io.out.fire
  credit.io.release := io.outRsp.fire
  io.credit := credit.io.count

  assert(!io.in.valid || !io.in.ready || credit.io.canAlloc,
    "LM-P3: accepted request without a return slot")
}

/** Slave-side NI: owns the destination response slot. */
class LmSlaveNi(p: NetworkParams, slave: Int) extends Module {
  private val qosW = math.max(1, log2Ceil(p.classWeights.length))
  private val slotW = math.max(1, log2Ceil(p.slaveRespSlots(slave) + 1))

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth)))
    val out = Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth))
    val inRsp = Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
    val outRsp = Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth))
    val slot = Output(UInt(slotW.W))
  })

  private val slot = Module(new LmSlotPool(p.slaveRespSlots(slave)))

  io.out.valid := io.in.valid && slot.io.canAlloc
  io.out.bits.write := io.in.bits.write
  io.out.bits.addr := io.in.bits.addr
  io.out.bits.len := io.in.bits.len
  io.out.bits.src := io.in.bits.src
  io.out.bits.id := io.in.bits.id
  io.out.bits.data := io.in.bits.data
  io.out.bits.vc := io.in.bits.vc
  io.in.ready := io.out.ready && slot.io.canAlloc

  io.outRsp.valid := io.inRsp.valid
  io.outRsp.bits := io.inRsp.bits
  io.outRsp.bits.vc :=
    VecInit(p.masterClass.map(c => (c * LoomDefaults.VcHalves).U(LoomDefaults.VcWidth.W)))(
      io.inRsp.bits.src)
  io.inRsp.ready := io.outRsp.ready

  slot.io.alloc := io.out.fire
  slot.io.release := io.outRsp.fire
  io.slot := slot.io.count

  assert(!io.in.valid || !io.in.ready || slot.io.canAlloc,
    "LM-P3: accepted request without a response slot")
}

/** Router-to-router link: sync Pipe stages or a bidirectional async link. */
class LmRouterLink(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    qosWidth: Int,
    pipe: Int,
    isAsync: Boolean,
    asyncDepth: Int,
    tagWidth: Int = LoomDefaults.AxiIdWidth
) extends Module {
  require(pipe >= 0 && pipe <= 8)
  require(!isAsync || (asyncDepth >= 2 && (asyncDepth & (asyncDepth - 1)) == 0),
    "async link depth must be a power of two >= 2")

  val io = IO(new Bundle {
    val aClk = Input(Bool())
    val aReset = Input(Bool())
    val bClk = Input(Bool())
    val bReset = Input(Bool())
    val aReq = Flipped(Decoupled(new LmNetReq(addrWidth, dataWidth, idWidth, qosWidth, tagWidth)))
    val bReq = Decoupled(new LmNetReq(addrWidth, dataWidth, idWidth, qosWidth, tagWidth))
    val bRsp = Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
    val aRsp = Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))
    val aRspF = Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
    val bRspF = Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))
    val creditUsed = Output(UInt(16.W))
  })

  private val reqType = new LmNetReq(addrWidth, dataWidth, idWidth, qosWidth, tagWidth)
  private val rspType = new LmRsp(dataWidth, idWidth, tagWidth)

  if (!isAsync) {
    val reqPipe = Module(new LmPipe(reqType, pipe))
    reqPipe.io.in <> io.aReq
    io.bReq <> reqPipe.io.out

    // Responses always get at least one register: on a ring the response
    // channel closes a combinational loop around the routers.
    val rspPipe = Module(new LmPipe(rspType, math.max(1, pipe)))
    rspPipe.io.in <> io.bRsp
    io.aRsp <> rspPipe.io.out

    val rspFPipe = Module(new LmPipe(rspType, math.max(1, pipe)))
    rspFPipe.io.in <> io.aRspF
    io.bRspF <> rspFPipe.io.out

    io.creditUsed := 0.U
  } else {
    val reqLink = withClockAndReset(io.aClk.asClock, io.aReset) {
      Module(new LmAsyncLink(reqType.getWidth, asyncDepth))
    }
    reqLink.io.dstClk := io.bClk
    reqLink.io.dstReset := io.bReset
    reqLink.io.in.valid := io.aReq.valid
    reqLink.io.in.bits := io.aReq.bits.asUInt
    io.aReq.ready := reqLink.io.in.ready
    io.bReq.valid := reqLink.io.out.valid
    io.bReq.bits := reqLink.io.out.bits.asTypeOf(chiselTypeOf(io.bReq.bits))
    reqLink.io.out.ready := io.bReq.ready

    val rspLink = withClockAndReset(io.bClk.asClock, io.bReset) {
      Module(new LmAsyncLink(rspType.getWidth, asyncDepth))
    }
    rspLink.io.dstClk := io.aClk
    rspLink.io.dstReset := io.aReset
    rspLink.io.in.valid := io.bRsp.valid
    rspLink.io.in.bits := io.bRsp.bits.asUInt
    io.bRsp.ready := rspLink.io.in.ready
    io.aRsp.valid := rspLink.io.out.valid
    io.aRsp.bits := rspLink.io.out.bits.asTypeOf(chiselTypeOf(io.aRsp.bits))
    rspLink.io.out.ready := io.aRsp.ready

    val rspFLink = withClockAndReset(io.aClk.asClock, io.aReset) {
      Module(new LmAsyncLink(rspType.getWidth, asyncDepth))
    }
    rspFLink.io.dstClk := io.bClk
    rspFLink.io.dstReset := io.bReset
    rspFLink.io.in.valid := io.aRspF.valid
    rspFLink.io.in.bits := io.aRspF.bits.asUInt
    io.aRspF.ready := rspFLink.io.in.ready
    io.bRspF.valid := rspFLink.io.out.valid
    io.bRspF.bits := rspFLink.io.out.bits.asTypeOf(chiselTypeOf(io.bRspF.bits))
    rspFLink.io.out.ready := io.bRspF.ready

    io.creditUsed := reqLink.io.creditUsed
  }
}

/** Multi-router top. All routers currently share the implicit clock; async
  * RouterLink is verified standalone and wired into a multi-clock top later.
  */
class LmNetworkTop(p: NetworkParams) extends Module {
  require(!p.links.exists(_.async),
    "async multi-router top is not supported yet; use LmRouterLink standalone")

  private val qosW = math.max(1, log2Ceil(p.classWeights.length))
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
      val requests = UInt(32.W)
      val responses = UInt(32.W)
      val errors = UInt(32.W)
      val errSticky = Bool()
    })
  })

  private val reqCnt = RegInit(0.U(32.W))
  private val rspCnt = RegInit(0.U(32.W))
  private val errCnt = RegInit(0.U(32.W))
  private val errSticky = RegInit(false.B)
  private val mNi = Seq.tabulate(p.nMaster)(i => Module(new LmMasterNi(p, i)))
  private val sNi = Seq.tabulate(p.nSlave)(j => Module(new LmSlaveNi(p, j)))
  private val routers = p.routers.map(spec => Module(new LmNetRouter(p, spec)))
  private val links = p.links.map(l =>
    Module(new LmRouterLink(p.addrWidth, p.dataWidth, p.idWidth, qosW,
      l.pipe, l.async, l.asyncFifoDepth, p.tagWidth)))

  // MNet/SNet are shared ports: one request and one response per net per
  // cycle. Ports stay per endpoint; the tokens enforce the shared bandwidth.
  private val mnetReqArb = Seq.tabulate(p.nMnet)(_ => Module(new LmRrArbiterN(p.nMaster)))
  private val mnetReqOk = Wire(Vec(p.nMaster, Bool()))
  private val mnetRspArb = Seq.tabulate(p.nMnet)(_ => Module(new LmRrArbiterN(p.nMaster)))
  private val mnetRspOk = Wire(Vec(p.nMaster, Bool()))
  // One-beat skid per slave breaks the token<->ready combinational path:
  // the router sees only the skid's capacity, while the SNet token is
  // decided from the skid output.
  private val sSkid = Seq.tabulate(p.nSlave)(_ =>
    Module(new LmPipe(new LmNetReq(p.addrWidth, p.dataWidth, p.idWidth, qosW, p.tagWidth), 1)))
  for (j <- 0 until p.nSlave) {
    routers(p.slaveRouter(j)).io.outReq(p.slavePort(j)) <> sSkid(j).io.in
  }
  private val snetReqArb = Seq.tabulate(p.nSnet)(_ => Module(new LmRrArbiterN(p.nSlave)))
  private val snetReqOk = Wire(Vec(p.nSlave, Bool()))
  private val snetRspArb = Seq.tabulate(p.nSnet)(_ => Module(new LmRrArbiterN(p.nSlave)))
  private val snetRspOk = Wire(Vec(p.nSlave, Bool()))

  for (m <- 0 until p.nMnet; i <- 0 until p.nMaster) {
    mnetReqArb(m).io.req(i) := mNi(i).io.out.valid && (p.masterMnet(i) == m).B
    mnetRspArb(m).io.req(i) := routers(p.masterRouter(i)).io.inRsp(p.masterPort(i)).valid &&
      (p.masterMnet(i) == m).B
  }
  for (i <- 0 until p.nMaster) {
    val aq = mnetReqArb(p.masterMnet(i))
    mnetReqOk(i) := aq.io.valid && (aq.io.grant === i.U)
    val ar = mnetRspArb(p.masterMnet(i))
    mnetRspOk(i) := ar.io.valid && (ar.io.grant === i.U)
  }
  for (s <- 0 until p.nSnet; j <- 0 until p.nSlave) {
    snetReqArb(s).io.req(j) := sSkid(j).io.out.valid && (p.slaveSnet(j) == s).B
    snetRspArb(s).io.req(j) := sNi(j).io.outRsp.valid && (p.slaveSnet(j) == s).B
  }
  for (j <- 0 until p.nSlave) {
    val aq = snetReqArb(p.slaveSnet(j))
    snetReqOk(j) := aq.io.valid && (aq.io.grant === j.U)
    val ar = snetRspArb(p.slaveSnet(j))
    snetRspOk(j) := ar.io.valid && (ar.io.grant === j.U)
  }

  for (i <- 0 until p.nMaster) {
    val r = p.masterRouter(i)
    val port = p.masterPort(i)
    mNi(i).io.in.valid := io.master(i).valid
    mNi(i).io.in.bits := io.master(i).bits
    io.master(i).ready := mNi(i).io.in.ready

    routers(r).io.inReq(port).valid := mNi(i).io.out.valid && mnetReqOk(i)
    routers(r).io.inReq(port).bits := mNi(i).io.out.bits
    mNi(i).io.out.ready := routers(r).io.inReq(port).ready && mnetReqOk(i)

    mNi(i).io.inRsp.valid := routers(r).io.inRsp(port).valid && mnetRspOk(i)
    mNi(i).io.inRsp.bits := routers(r).io.inRsp(port).bits
    routers(r).io.inRsp(port).ready := mNi(i).io.inRsp.ready

    routers(r).io.inRspFwd(port).valid := false.B
    routers(r).io.inRspFwd(port).bits :=
      0.U.asTypeOf(chiselTypeOf(routers(r).io.inRspFwd(port).bits))

    io.masterRsp(i).valid := mNi(i).io.outRsp.valid
    io.masterRsp(i).bits := mNi(i).io.outRsp.bits
    mNi(i).io.outRsp.ready := io.masterRsp(i).ready

    when(io.master(i).fire) { reqCnt := reqCnt + 1.U }
    when(io.masterRsp(i).fire) {
      rspCnt := rspCnt + 1.U
      when(io.masterRsp(i).bits.err) {
        errCnt := errCnt + 1.U
        errSticky := true.B
      }
    }

    io.perf.returnSlots(i) := mNi(i).io.credit
  }

  for (j <- 0 until p.nSlave) {
    routers(p.slaveRouter(j)).io.outRspFwd(p.slavePort(j)).ready := true.B
  }

  for (j <- 0 until p.nSlave) {
    val r = p.slaveRouter(j)
    val port = p.slavePort(j)
    sNi(j).io.in.valid := sSkid(j).io.out.valid && snetReqOk(j)
    sNi(j).io.in.bits := sSkid(j).io.out.bits
    sSkid(j).io.out.ready := sNi(j).io.in.ready && snetReqOk(j)

    io.slave(j).valid := sNi(j).io.out.valid
    io.slave(j).bits := sNi(j).io.out.bits
    sNi(j).io.out.ready := io.slave(j).ready

    sNi(j).io.inRsp.valid := io.slaveRsp(j).valid
    sNi(j).io.inRsp.bits := io.slaveRsp(j).bits
    io.slaveRsp(j).ready := sNi(j).io.inRsp.ready

    routers(r).io.outRsp(port).valid := sNi(j).io.outRsp.valid && snetRspOk(j)
    routers(r).io.outRsp(port).bits := sNi(j).io.outRsp.bits
    sNi(j).io.outRsp.ready := routers(r).io.outRsp(port).ready && snetRspOk(j)

    io.perf.respSlots(j) := sNi(j).io.slot
  }

  for ((l, link) <- p.links.zip(links)) {
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

  io.perf.requests := reqCnt
  io.perf.responses := rspCnt
  io.perf.errors := errCnt
  io.perf.errSticky := errSticky
}
