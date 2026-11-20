package loom.rtl

import chisel3._
import chisel3.util._
import loom.ir.LoomDefaults

/** Endpoint request: the merged view of the `rreq` and `wdata` channels.
  * `src` names the issuing master; `id` is the AXI4 transaction namespace and
  * is transported unchanged by every hop. The network splits this port into
  * per-channel traffic inside its NIs.
  */
class LmReq(
    val addrWidth: Int,
    val dataWidth: Int,
    val idWidth: Int,
    val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Bundle {
  val write = Bool()
  val addr = UInt(addrWidth.W)
  val len = UInt(8.W)
  val src = UInt(idWidth.W)
  val id = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val vc = UInt(LoomDefaults.VcWidth.W)
}

class LmRsp(
    val dataWidth: Int,
    val idWidth: Int,
    val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Bundle {
  val src = UInt(idWidth.W)
  val id = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
  val err = Bool()
  val vc = UInt(LoomDefaults.VcWidth.W)
}

/** Return-slot credit for one master.
  *
  * Allocated when a request is injected, released when its response reaches
  * the master. Treats "alloc without capacity" and "release while empty" as
  * protocol failures (P3).
  */
class LmCreditMgr(depth: Int) extends Module {
  require(depth >= 1, "credit depth must be >= 1")
  private val w = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val alloc = Input(Bool())
    val release = Input(Bool())
    val canAlloc = Output(Bool())
    val count = Output(UInt(w.W))
  })

  private val cnt = RegInit(0.U(w.W))
  io.canAlloc := cnt < depth.U
  io.count := cnt

  when(io.alloc && !io.release) { cnt := cnt + 1.U }
  when(io.release && !io.alloc) { cnt := cnt - 1.U }

  assert(!io.alloc || io.canAlloc, "LM-P3: return credit overflow")
  assert(!io.release || cnt =/= 0.U, "LM-P3: return credit underflow")
}

/** Destination response slots for one slave.
  *
  * Allocated at request injection, released when the response leaves the
  * slave. This is the G3 pre-reservation that removes the
  * "request waits for a response buffer" deadlock state.
  */
class LmSlotPool(depth: Int) extends Module {
  require(depth >= 1, "response slot depth must be >= 1")
  private val w = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val alloc = Input(Bool())
    val release = Input(Bool())
    val canAlloc = Output(Bool())
    val count = Output(UInt(w.W))
  })

  private val cnt = RegInit(0.U(w.W))
  io.canAlloc := cnt < depth.U
  io.count := cnt

  when(io.alloc && !io.release) { cnt := cnt + 1.U }
  when(io.release && !io.alloc) { cnt := cnt - 1.U }

  assert(!io.alloc || io.canAlloc, "LM-P3: response slot overflow")
  assert(!io.release || cnt =/= 0.U, "LM-P3: response slot underflow")
}

/** Per-class virtual-channel queue.
  *
  * One instance per input port: a port carries a single static class, so
  * the class VC sets stay disjoint (G5). Depth comes from the class table;
  * a queue that is full backpressures its input port while other ports and
  * other classes keep flowing.
  */
class LmVcQueue[T <: Data](gen: T, depth: Int) extends Module {
  require(depth >= 1, "VC queue depth must be >= 1")
  private val w = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val enq = Flipped(Decoupled(gen.cloneType))
    val deq = Decoupled(gen.cloneType)
    val count = Output(UInt(w.W))
  })

  private val q = Module(new Queue(gen.cloneType, depth))
  q.io.enq <> io.enq
  io.deq <> q.io.deq
  io.count := q.io.count
}

/** Round-robin arbiter. Requests must be pre-gated by downstream ready and
  * by credit/slot availability; the pointer advances only on a grant.
  */
class LmRrArbiter(n: Int) extends Module {
  require(n >= 1, "arbiter radix must be >= 1")
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

/** Weighted round-robin arbiter over QoS classes.
  *
  * The schedule is a fixed interleaving of class slots expanded from the
  * weights (e.g. 4:2:1 -> ctl,lat,bulk,ctl,lat,ctl,ctl). Idle classes are
  * skipped, so active classes keep their relative weight. A class that has
  * been requesting for `LoomDefaults.MaxAge` cycles without a grant is
  * force-granted, which bounds starvation independently of the schedule.
  */
class LmWrrArbiter(weights: Seq[Int]) extends Module {
  require(weights.nonEmpty && weights.forall(_ >= 1), "weights must be >= 1")
  private val n = weights.length
  private val schedule: Seq[Int] =
    (0 until weights.max).flatMap(k => weights.indices.filter(i => weights(i) > k))
  private val len = schedule.length
  private val sw = math.max(1, log2Ceil(len))
  private val cw = math.max(1, log2Ceil(n))

  val io = IO(new Bundle {
    val req = Input(Vec(n, Bool()))
    val valid = Output(Bool())
    val grant = Output(UInt(cw.W))
  })

  private val ptr = RegInit(0.U(sw.W))
  private val age = RegInit(VecInit(Seq.fill(n)(0.U(8.W))))
  private val schedVec = VecInit(schedule.map(_.U(cw.W)))
  private val maxAge = LoomDefaults.MaxAge.U(8.W)

  private def wrap(x: UInt): UInt =
    Mux(x >= len.U, x - len.U, x)(sw - 1, 0)

  private val starved = Wire(Vec(n, Bool()))
  for (i <- 0 until n) starved(i) := io.req(i) && age(i) >= maxAge
  private val anyStarved = starved.asUInt.orR

  private val rotated = Wire(Vec(len, Bool()))
  for (k <- 0 until len) rotated(k) := io.req(schedVec(wrap(ptr +& k.U)))

  private val sel = PriorityEncoder(rotated.asUInt)
  private val schedIdx = wrap(ptr +& sel)
  private val schedGrant = schedVec(schedIdx)
  private val starvedGrant = PriorityEncoder(starved.asUInt)

  io.valid := io.req.asUInt.orR
  io.grant := Mux(anyStarved, starvedGrant, schedGrant)

  when(io.valid) {
    when(anyStarved) { ptr := ptr }.otherwise { ptr := wrap(schedIdx +& 1.U) }
  }

  for (i <- 0 until n) {
    when(io.valid && (io.grant === i.U)) {
      age(i) := 0.U
    }.elsewhen(io.req(i)) {
      when(age(i) < maxAge) { age(i) := age(i) + 1.U }
    }.otherwise {
      age(i) := 0.U
    }
  }
}

class LmFlatRouter(p: FabricParams) extends Module {
  require(p.nMaster >= 1, "nMaster must be at least 1")
  require(p.nSlave >= 1, "nSlave must be at least 1")

  private val outW = math.max(1, log2Ceil(p.nSlave))
  private val maxRetW = math.max(1, log2Ceil(p.maxReturnSlots + 1))
  private val maxRspW = math.max(1, log2Ceil(p.maxRespSlots + 1))

  val io = IO(new Bundle {
    val in = Vec(p.nMaster, Flipped(Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth))))
    val out = Vec(p.nSlave, Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))
    val outRsp = Vec(p.nSlave, Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth))))
    val inRsp = Vec(p.nMaster, Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
    val perf = Output(new Bundle {
      val returnSlots = Vec(p.nMaster, UInt(maxRetW.W))
      val respSlots = Vec(p.nSlave, UInt(maxRspW.W))
    })
  })

  private val retCredit = Seq.tabulate(p.nMaster)(i => Module(new LmCreditMgr(p.masterReturnSlots(i))))
  private val respPool = Seq.tabulate(p.nSlave)(j => Module(new LmSlotPool(p.slaveRespSlots(j))))
  private val nClasses = p.classWeights.length
  private val reqFire = Wire(Vec(p.nSlave, Bool()))

  // One VC queue per input port. A port is statically bound to one class,
  // so per-class VC sets remain disjoint (G5) without a VC-id field.
  private val vc = Seq.tabulate(p.nMaster) { i =>
    Module(new LmVcQueue(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth),
      p.classDepth(p.masterClass(i))))
  }
  private val respCanAlloc = VecInit(respPool.map(_.io.canAlloc))
  private val vcReady = VecInit(vc.map(_.io.enq.ready))
  private val dstOf = Wire(Vec(p.nMaster, UInt(outW.W)))
  private val routeOf = Wire(Vec(p.nMaster, UInt(outW.W)))
  for (i <- 0 until p.nMaster) {
    dstOf(i) := LmAddrDecode(io.in(i).bits.addr, p.addrWidth, p.slaveBase, p.slaveSize, outW)
    routeOf(i) := LmAddrDecode(vc(i).io.deq.bits.addr, p.addrWidth, p.slaveBase, p.slaveSize, outW)
  }

  // Admission is serialized per destination: at most one request per slave
  // per cycle, which matches its single response-slot allocation. Admission
  // uses the same class weights as the egress arbiter; otherwise a plain RR
  // admit stage would cap the achievable class ratio. Requests to
  // different slaves still enter in parallel. A return slot and a
  // destination response slot are reserved at admission (G3).
  private val admitClassArb = Seq.tabulate(p.nSlave) { j =>
    Seq.tabulate(nClasses) { c =>
      val arb = Module(new LmRrArbiter(p.nMaster))
      for (i <- 0 until p.nMaster) {
        if (p.masterClass(i) == c) {
          arb.io.req(i) := io.in(i).valid && (dstOf(i) === j.U) &&
            retCredit(i).io.canAlloc && respCanAlloc(j) && vcReady(i)
        } else {
          arb.io.req(i) := false.B
        }
      }
      arb
    }
  }
  private val admitClassSel =
    Seq.tabulate(p.nSlave)(j => Module(new LmWrrArbiter(p.classWeights)))
  private val admitGrant = Seq.tabulate(p.nSlave) { j =>
    for (c <- 0 until nClasses) admitClassSel(j).io.req(c) := admitClassArb(j)(c).io.valid
    VecInit(admitClassArb(j).map(_.io.grant))(admitClassSel(j).io.grant)
  }

  // MNet/SNet are shared physical ports: at most one admission per net per
  // cycle. The token is taken after per-slave admission, so only a slave
  // with a pending request can win its SNet, and only a master with a
  // granted admission can win its MNet.
  private val snetAdmitArb = Seq.tabulate(p.nSnet)(_ => Module(new LmRrArbiterN(p.nSlave)))
  private val snetAdmitOk = Wire(Vec(p.nSlave, Bool()))
  for (s <- 0 until p.nSnet; j <- 0 until p.nSlave) {
    snetAdmitArb(s).io.req(j) := admitClassSel(j).io.valid && (p.slaveSnet(j) == s).B
  }
  for (j <- 0 until p.nSlave) {
    val a = snetAdmitArb(p.slaveSnet(j))
    snetAdmitOk(j) := a.io.valid && (a.io.grant === j.U)
  }

  private val admitCand = Wire(Vec(p.nMaster, Bool()))
  for (i <- 0 until p.nMaster) {
    admitCand(i) := (0 until p.nSlave)
      .map(j => admitClassSel(j).io.valid && snetAdmitOk(j) &&
        (admitGrant(j) === i.U) && (dstOf(i) === j.U))
      .reduce(_ || _)
  }
  private val mnetAdmitArb = Seq.tabulate(p.nMnet)(_ => Module(new LmRrArbiterN(p.nMaster)))
  private val mnetAdmitOk = Wire(Vec(p.nMaster, Bool()))
  for (m <- 0 until p.nMnet; i <- 0 until p.nMaster) {
    mnetAdmitArb(m).io.req(i) := admitCand(i) && (p.masterMnet(i) == m).B
  }
  for (i <- 0 until p.nMaster) {
    val a = mnetAdmitArb(p.masterMnet(i))
    mnetAdmitOk(i) := a.io.valid && (a.io.grant === i.U)
  }

  private val admitFire = Wire(Vec(p.nMaster, Bool()))
  for (i <- 0 until p.nMaster) {
    admitFire(i) := admitCand(i) && mnetAdmitOk(i)
    vc(i).io.enq.valid := admitFire(i)
    vc(i).io.enq.bits := io.in(i).bits
    vc(i).io.enq.bits.vc := (p.masterClass(i) * LoomDefaults.VcHalves).U
    io.in(i).ready := admitFire(i)

    when(vc(i).io.enq.fire) {
      assert(io.in(i).bits.len +& 1.U <= p.classMaxPacket(p.masterClass(i)).U,
        "LM-G6: request exceeds class maxPacketBeats")
      if (p.masterClass(i) == LoomDefaults.QosOrder.indexOf("ctl")) {
        assert(io.in(i).bits.len === 0.U, "LM-G7: request must be single-beat")
      }
    }
  }
  private val admitFireSlave = Wire(Vec(p.nSlave, Bool()))
  for (j <- 0 until p.nSlave) {
    admitFireSlave(j) := snetAdmitOk(j) &&
      (0 until p.nMaster).map(i => admitFire(i) && (dstOf(i) === j.U)).reduce(_ || _)
  }

  // Banked egress: outputs are grouped into banks and each output gets one
  // register stage. No extra arbitration is introduced.
  private val outReady = Wire(Vec(p.nSlave, Bool()))
  private val coreValid = Wire(Vec(p.nSlave, Bool()))
  private val coreBits = Wire(Vec(p.nSlave, new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))
  private val bankStage = Seq.tabulate(p.nSlave) { j =>
    if (p.banks > 1) Some(Module(new LmPipe(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth), 1)))
    else None
  }
  for (j <- 0 until p.nSlave) {
    outReady(j) := bankStage(j).map(_.io.in.ready).getOrElse(io.out(j).ready)
  }

  // One RR arbiter per (output, class). Only queue heads take part, so a
  // blocked head behind one output does not stall the other outputs.
  private val classArb = Seq.tabulate(p.nSlave) { j =>
    Seq.tabulate(nClasses) { c =>
      val arb = Module(new LmRrArbiter(p.nMaster))
      for (i <- 0 until p.nMaster) {
        if (p.masterClass(i) == c) {
          arb.io.req(i) := vc(i).io.deq.valid && (routeOf(i) === j.U) && outReady(j)
        } else {
          arb.io.req(i) := false.B
        }
      }
      arb
    }
  }

  private val classSel = Seq.tabulate(p.nSlave)(j => Module(new LmWrrArbiter(p.classWeights)))

  // The selected class's winner drives the output. Ready is only asserted
  // for that winner, so a class arbiter that is not selected cannot consume
  // its input.
  private val selGrant = Seq.tabulate(p.nSlave) { j =>
    for (c <- 0 until nClasses) classSel(j).io.req(c) := classArb(j)(c).io.valid
    coreValid(j) := classSel(j).io.valid
    val grant = VecInit(classArb(j).map(_.io.grant))(classSel(j).io.grant)
    coreBits(j) := VecInit(vc.map(_.io.deq.bits))(grant)
    reqFire(j) := classSel(j).io.valid && outReady(j)
    grant
  }

  for (j <- 0 until p.nSlave) {
    bankStage(j) match {
      case Some(st) =>
        st.io.in.valid := coreValid(j)
        st.io.in.bits := coreBits(j)
        io.out(j) <> st.io.out
      case None =>
        io.out(j).valid := coreValid(j)
        io.out(j).bits := coreBits(j)
    }
  }

  for (i <- 0 until p.nMaster) {
    vc(i).io.deq.ready := (0 until p.nSlave)
      .map(j => reqFire(j) && (selGrant(j) === i.U))
      .reduce(_ || _)

    retCredit(i).io.alloc := vc(i).io.enq.fire
    retCredit(i).io.release := io.inRsp(i).valid && io.inRsp(i).ready
    io.perf.returnSlots(i) := retCredit(i).io.count

    // Every queued request holds a return credit, so the credit count is a
    // conservative upper bound on the queue occupancy (G6).
    assert(vc(i).io.count <= retCredit(i).io.count,
      "LM-G6: queued request without an in-flight credit")
  }

  for (j <- 0 until p.nSlave) {
    respPool(j).io.alloc := admitFireSlave(j)
    respPool(j).io.release := io.outRsp(j).valid && io.outRsp(j).ready
    io.perf.respSlots(j) := respPool(j).io.count
  }

  // Shared SNet return path: one response per SNet per cycle.
  private val snetRspArb = Seq.tabulate(p.nSnet)(_ => Module(new LmRrArbiterN(p.nSlave)))
  private val snetRspOk = Wire(Vec(p.nSlave, Bool()))
  for (s <- 0 until p.nSnet; j <- 0 until p.nSlave) {
    snetRspArb(s).io.req(j) := io.outRsp(j).valid && (p.slaveSnet(j) == s).B
  }
  for (j <- 0 until p.nSlave) {
    val a = snetRspArb(p.slaveSnet(j))
    snetRspOk(j) := a.io.valid && (a.io.grant === j.U)
  }

  private val rspArb = Seq.tabulate(p.nMaster) { i =>
    val arb = Module(new LmRrArbiter(p.nSlave))
    for (j <- 0 until p.nSlave) {
      arb.io.req(j) := io.outRsp(j).valid && (io.outRsp(j).bits.src === i.U) &&
        snetRspOk(j) && io.inRsp(i).ready
    }
    io.inRsp(i).bits := VecInit(io.outRsp.map(_.bits))(arb.io.grant)
    arb
  }

  // Shared MNet return path: one response per MNet per cycle.
  private val mnetRspArb = Seq.tabulate(p.nMnet)(_ => Module(new LmRrArbiterN(p.nMaster)))
  private val mnetRspOk = Wire(Vec(p.nMaster, Bool()))
  for (m <- 0 until p.nMnet; i <- 0 until p.nMaster) {
    mnetRspArb(m).io.req(i) := rspArb(i).io.valid && (p.masterMnet(i) == m).B
  }
  for (i <- 0 until p.nMaster) {
    val a = mnetRspArb(p.masterMnet(i))
    mnetRspOk(i) := a.io.valid && (a.io.grant === i.U)
    io.inRsp(i).valid := rspArb(i).io.valid && mnetRspOk(i)
  }

  for (j <- 0 until p.nSlave) {
    io.outRsp(j).ready := snetRspOk(j) &&
      (0 until p.nMaster).map(i =>
        rspArb(i).io.valid && mnetRspOk(i) && (rspArb(i).io.grant === j.U)).reduce(_ || _)
  }

  for (i <- 0 until p.nMaster) {
    assert(!io.in(i).valid || !io.in(i).ready || retCredit(i).io.canAlloc,
      "LM-P3: accepted request without a return slot")
  }
}

/** Top-level shell: master-facing ports and slave-facing ports.
  * MNet/SNet are zero-delay port groupings.
  */
class LmTop(p: FabricParams) extends Module {
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

  private val router = Module(new LmFlatRouter(p))
  private val reqCnt = RegInit(0.U(32.W))
  private val rspCnt = RegInit(0.U(32.W))
  private val errCnt = RegInit(0.U(32.W))
  private val errSticky = RegInit(false.B)

  for (i <- 0 until p.nMaster) {
    router.io.in(i).valid := io.master(i).valid
    router.io.in(i).bits := io.master(i).bits
    io.master(i).ready := router.io.in(i).ready

    when(io.master(i).fire) { reqCnt := reqCnt + 1.U }
    when(io.masterRsp(i).fire) {
      rspCnt := rspCnt + 1.U
      when(io.masterRsp(i).bits.err) {
        errCnt := errCnt + 1.U
        errSticky := true.B
      }
    }

    io.masterRsp(i).valid := router.io.inRsp(i).valid
    io.masterRsp(i).bits := router.io.inRsp(i).bits
    router.io.inRsp(i).ready := io.masterRsp(i).ready

    io.perf.returnSlots(i) := router.io.perf.returnSlots(i)
  }

  for (j <- 0 until p.nSlave) {
    io.slave(j).valid := router.io.out(j).valid
    io.slave(j).bits := router.io.out(j).bits
    router.io.out(j).ready := io.slave(j).ready

    router.io.outRsp(j).valid := io.slaveRsp(j).valid
    router.io.outRsp(j).bits := io.slaveRsp(j).bits
    io.slaveRsp(j).ready := router.io.outRsp(j).ready

    io.perf.respSlots(j) := router.io.perf.respSlots(j)
  }

  io.perf.requests := reqCnt
  io.perf.responses := rspCnt
  io.perf.errors := errCnt
  io.perf.errSticky := errSticky
}
