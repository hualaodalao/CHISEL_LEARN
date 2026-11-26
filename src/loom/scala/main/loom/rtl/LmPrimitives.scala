package loom.rtl

import chisel3._
import chisel3.util._
import loom.ir.LoomDefaults

/** Shared primitives consumed by the CHI generation layer (`loom.chi`).
  *
  * MIG-11 (S6): the legacy four-channel fabric (`LmTop/LmNetworkTop/...`) was
  * isolated to `src/loom/legacy/`; only the building blocks that `loom.chi`
  * instantiates stay on the compiled source path:
  *   - LmWire        (ChiNetworkTop / ChiPartition)
  *   - LmPipe        (ChiRouter / ChiPartition)
  *   - LmRrArbiterN  (ChiRouter)
  *   - LmWrrArbiter  (ChiRouter class arbitration)
  * The CDC blocks live in LmCdc.scala (ChiAsyncLink wraps LmAsyncLink).
  * The definitions below are verbatim copies of the legacy originals kept in
  * `legacy/main/loom/rtl/LmRtl.scala` and `LmNetwork.scala`; when re-enabling
  * the legacy source set, move this file aside first (see legacy/README.md).
  */

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