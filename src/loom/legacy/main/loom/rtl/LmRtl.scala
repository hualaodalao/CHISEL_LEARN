package loom.rtl

import chisel3._
import chisel3.util._
import loom.ir.LoomDefaults

/** Merged request view kept inside the flat router: `write` selects the
  * channel, and one word travels per transaction. Endpoint ports carry the
  * four channels separately; only `LmTop` still builds this view.
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

/** Merged response view kept inside the flat router. `write` tags the channel
  * the response belongs to, so the endpoint adapter can hand it back on the
  * channel that issued the request.
  */
class LmRsp(
    val dataWidth: Int,
    val idWidth: Int,
    val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Bundle {
  val write = Bool()
  val src = UInt(idWidth.W)
  val id = UInt(tagWidth.W)
  val data = UInt(dataWidth.W)
  val err = Bool()
  val vc = UInt(LoomDefaults.VcWidth.W)
}

/** Passive Decoupled wiring: `to` is driven from `from` whichever of the two
  * is a port of the current module and whichever is a child port.
  */
object LmWire {
  def link[T <: Data](from: DecoupledIO[T], to: DecoupledIO[T]): Unit = {
    to.valid := from.valid
    to.bits := from.bits
    from.ready := to.ready
  }
}

/** Return-slot credit for one master.
  *
  * Allocated when a request is injected, released when its response reaches
  * the master. Treats "alloc without capacity" and "release while empty" as
  * protocol failures (P3). Allocation and release are counts because the two
  * request and the two response channels may each move in the same cycle.
  */
class LmCreditMgr(depth: Int) extends Module {
  require(depth >= 1, "credit depth must be >= 1")
  private val w = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val alloc = Input(UInt(2.W))
    val release = Input(UInt(2.W))
    val canAlloc = Output(Bool())
    val count = Output(UInt(w.W))
  })

  private val cnt = RegInit(0.U(w.W))
  io.canAlloc := cnt < depth.U
  io.count := cnt

  private val up = io.alloc >= io.release
  private val step = Mux(up, io.alloc - io.release, io.release - io.alloc)
  when(step =/= 0.U) {
    cnt := Mux(up, cnt + step, cnt - step)
  }

  assert(cnt +& io.alloc <= depth.U, "LM-P3: return credit overflow")
  assert(io.release <= cnt, "LM-P3: return credit underflow")
}

/** Destination response slots for one slave.
  *
  * Allocated at request injection, released when the response leaves the
  * slave. This is the G3 pre-reservation that removes the
  * "request waits for a response buffer" deadlock state. Both request
  * channels may allocate in one cycle, so allocation is a count.
  */
class LmSlotPool(depth: Int) extends Module {
  require(depth >= 1, "response slot depth must be >= 1")
  private val w = math.max(1, log2Ceil(depth + 1))

  val io = IO(new Bundle {
    val alloc = Input(UInt(2.W))
    val release = Input(UInt(2.W))
    val canAlloc = Output(Bool())
    val count = Output(UInt(w.W))
  })

  private val cnt = RegInit(0.U(w.W))
  io.canAlloc := cnt < depth.U
  io.count := cnt

  private val up = io.alloc >= io.release
  private val step = Mux(up, io.alloc - io.release, io.release - io.alloc)
  when(step =/= 0.U) {
    cnt := Mux(up, cnt + step, cnt - step)
  }

  assert(cnt +& io.alloc <= depth.U, "LM-P3: response slot overflow")
  assert(io.release <= cnt, "LM-P3: response slot underflow")
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

    retCredit(i).io.alloc := vc(i).io.enq.fire.asUInt
    retCredit(i).io.release := (io.inRsp(i).valid && io.inRsp(i).ready).asUInt
    io.perf.returnSlots(i) := retCredit(i).io.count

    // Every queued request holds a return credit, so the credit count is a
    // conservative upper bound on the queue occupancy (G6).
    assert(vc(i).io.count <= retCredit(i).io.count,
      "LM-G6: queued request without an in-flight credit")
  }

  for (j <- 0 until p.nSlave) {
    respPool(j).io.alloc := admitFireSlave(j).asUInt
    respPool(j).io.release := (io.outRsp(j).valid && io.outRsp(j).ready).asUInt
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

/** Endpoint adapter of the flat router, master side: the two request channels
  * are interleaved onto the router's merged request port and the merged
  * response goes back out on the channel its `write` tag names. Credits stay
  * in the router.
  */
class LmFlatMasterPort(p: FabricParams) extends Module {
  private def rdReqT = new LmRdReq(p.addrWidth, p.idWidth, p.tagWidth)
  private def wrDataT = new LmWrData(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)
  private def rdDataT = new LmRdData(p.dataWidth, p.idWidth, p.tagWidth)
  private def wrRespT = new LmWrResp(p.idWidth, p.tagWidth)

  val io = IO(new Bundle {
    val rdReq = Flipped(Decoupled(rdReqT))
    val wrData = Flipped(Decoupled(wrDataT))
    val rdData = Decoupled(rdDataT)
    val wrResp = Decoupled(wrRespT)
    val req = Decoupled(new LmReq(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth))
    val rsp = Flipped(Decoupled(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth)))
  })

  private val reqArb = Module(new LmRrArbiterN(2))
  reqArb.io.req(0) := io.rdReq.valid
  reqArb.io.req(1) := io.wrData.valid
  private val selWr = reqArb.io.grant === 1.U

  io.req.valid := reqArb.io.valid
  io.req.bits.write := selWr
  io.req.bits.addr := Mux(selWr, io.wrData.bits.addr, io.rdReq.bits.addr)
  io.req.bits.len := Mux(selWr, 0.U, io.rdReq.bits.len)
  io.req.bits.src := Mux(selWr, io.wrData.bits.src, io.rdReq.bits.src)
  io.req.bits.id := Mux(selWr, io.wrData.bits.id, io.rdReq.bits.id)
  io.req.bits.data := Mux(selWr, io.wrData.bits.data, 0.U)
  io.req.bits.strb := Mux(selWr, io.wrData.bits.strb, 0.U)
  io.req.bits.vc := Mux(selWr, io.wrData.bits.vc, io.rdReq.bits.vc)
  io.rdReq.ready := io.req.ready && reqArb.io.valid && !selWr
  io.wrData.ready := io.req.ready && reqArb.io.valid && selWr

  // The router's response bits follow its ready (the grant picks the source),
  // so the split by channel tag needs one register in between.
  private val rspQ = Module(new Queue(new LmRsp(p.dataWidth, p.idWidth, p.tagWidth), 1))
  rspQ.io.enq <> io.rsp
  private val head = rspQ.io.deq.bits
  private val have = rspQ.io.deq.valid

  io.rdData.valid := have && !head.write
  io.rdData.bits.data := head.data
  io.rdData.bits.src := head.src
  io.rdData.bits.id := head.id
  io.rdData.bits.last := true.B
  io.rdData.bits.err := head.err
  io.rdData.bits.vc := head.vc

  io.wrResp.valid := have && head.write
  io.wrResp.bits.src := head.src
  io.wrResp.bits.id := head.id
  io.wrResp.bits.err := head.err
  io.wrResp.bits.vc := head.vc

  rspQ.io.deq.ready := Mux(head.write, io.wrResp.ready, io.rdData.ready)
}

/** Endpoint adapter of the flat router, slave side: one merged request is
  * split onto the channel its write bit selects, and the two response channels
  * are merged back for the router.
  */
class LmFlatSlavePort(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth)))
    val rsp = Decoupled(new LmRsp(dataWidth, idWidth, tagWidth))
    val rdReq = Decoupled(new LmRdReq(addrWidth, idWidth, tagWidth))
    val wrData = Decoupled(new LmWrData(addrWidth, dataWidth, idWidth, tagWidth))
    val rdData = Flipped(Decoupled(new LmRdData(dataWidth, idWidth, tagWidth)))
    val wrResp = Flipped(Decoupled(new LmWrResp(idWidth, tagWidth)))
  })

  // The router's output ready depends on its output bits (the grant follows
  // the decoded address), so the split needs one register in between.
  private val reqQ = Module(new Queue(new LmReq(addrWidth, dataWidth, idWidth, tagWidth), 1))
  reqQ.io.enq <> io.req
  private val head = reqQ.io.deq.bits
  private val have = reqQ.io.deq.valid

  io.rdReq.valid := have && !head.write
  io.rdReq.bits.addr := head.addr
  io.rdReq.bits.len := head.len
  io.rdReq.bits.src := head.src
  io.rdReq.bits.id := head.id
  io.rdReq.bits.vc := head.vc

  io.wrData.valid := have && head.write
  io.wrData.bits.addr := head.addr
  io.wrData.bits.data := head.data
  io.wrData.bits.strb := head.strb
  io.wrData.bits.src := head.src
  io.wrData.bits.id := head.id
  io.wrData.bits.vc := head.vc
  io.wrData.bits.last := true.B
  reqQ.io.deq.ready := Mux(head.write, io.wrData.ready, io.rdReq.ready)

  // Each channel keeps one entry so the merge decision never depends on the
  // arriving valid bits.
  private val rdQ = Module(new Queue(new LmRdData(dataWidth, idWidth, tagWidth), 1))
  private val wrQ = Module(new Queue(new LmWrResp(idWidth, tagWidth), 1))
  rdQ.io.enq.valid := io.rdData.valid
  rdQ.io.enq.bits := io.rdData.bits
  io.rdData.ready := rdQ.io.enq.ready
  wrQ.io.enq.valid := io.wrResp.valid
  wrQ.io.enq.bits := io.wrResp.bits
  io.wrResp.ready := wrQ.io.enq.ready

  // The merge decision must not depend on the router's ready, which in turn
  // depends on this port's valid.
  private val rspArb = Module(new LmRrArbiterN(2))
  rspArb.io.req(0) := rdQ.io.deq.valid
  rspArb.io.req(1) := wrQ.io.deq.valid
  private val pickWr = rspArb.io.grant === 1.U

  io.rsp.valid := rspArb.io.valid
  io.rsp.bits.write := pickWr
  io.rsp.bits.src := Mux(pickWr, wrQ.io.deq.bits.src, rdQ.io.deq.bits.src)
  io.rsp.bits.id := Mux(pickWr, wrQ.io.deq.bits.id, rdQ.io.deq.bits.id)
  io.rsp.bits.data := Mux(pickWr, 0.U, rdQ.io.deq.bits.data)
  io.rsp.bits.err := Mux(pickWr, wrQ.io.deq.bits.err, rdQ.io.deq.bits.err)
  io.rsp.bits.vc := Mux(pickWr, wrQ.io.deq.bits.vc, rdQ.io.deq.bits.vc)
  rdQ.io.deq.ready := io.rsp.ready && rspArb.io.valid && !pickWr
  wrQ.io.deq.ready := io.rsp.ready && rspArb.io.valid && pickWr
}

/** Top-level shell: master-facing and slave-facing four-channel ports.
  * MNet/SNet are zero-delay port groupings; the flat router keeps its merged
  * request/response view behind the endpoint adapters.
  */
class LmTop(p: FabricParams) extends Module {
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

  private val router = Module(new LmFlatRouter(p))
  private val mPort = Seq.tabulate(p.nMaster)(_ => Module(new LmFlatMasterPort(p)))
  private val sPort = Seq.tabulate(p.nSlave)(_ =>
    Module(new LmFlatSlavePort(p.addrWidth, p.dataWidth, p.idWidth, p.tagWidth)))

  private val rdReqCnt = RegInit(0.U(32.W))
  private val wrBeatCnt = RegInit(0.U(32.W))
  private val rdRspCnt = RegInit(0.U(32.W))
  private val wrRspCnt = RegInit(0.U(32.W))
  private val errCnt = RegInit(0.U(32.W))
  private val errSticky = RegInit(false.B)

  for (i <- 0 until p.nMaster) {
    LmWire.link(io.masterRdReq(i), mPort(i).io.rdReq)
    LmWire.link(io.masterWrData(i), mPort(i).io.wrData)
    LmWire.link(mPort(i).io.rdData, io.masterRdData(i))
    LmWire.link(mPort(i).io.wrResp, io.masterWrResp(i))
    mPort(i).io.req <> router.io.in(i)
    router.io.inRsp(i) <> mPort(i).io.rsp

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

    io.perf.returnSlots(i) := router.io.perf.returnSlots(i)
  }

  for (j <- 0 until p.nSlave) {
    router.io.out(j) <> sPort(j).io.req
    sPort(j).io.rsp <> router.io.outRsp(j)
    LmWire.link(sPort(j).io.rdReq, io.slaveRdReq(j))
    LmWire.link(sPort(j).io.wrData, io.slaveWrData(j))
    LmWire.link(io.slaveRdData(j), sPort(j).io.rdData)
    LmWire.link(io.slaveWrResp(j), sPort(j).io.wrResp)

    io.perf.respSlots(j) := router.io.perf.respSlots(j)
  }

  io.perf.rdRequests := rdReqCnt
  io.perf.wrBeats := wrBeatCnt
  io.perf.rdResponses := rdRspCnt
  io.perf.wrResponses := wrRspCnt
  io.perf.errors := errCnt
  io.perf.errSticky := errSticky
}
