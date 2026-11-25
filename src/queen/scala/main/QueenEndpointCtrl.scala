import chisel3._
import chisel3.util._

/** GEMM/DMA 端点控制器：在派发 adapter 之前按 lane 序消费 FENCE/WAIT/TRACE。
  *
  * 队头连续的 QWR/QRD/RUN 切片转发给 adapter；遇到第一条控制命令就停住，条件满足才消费它，
  * 其后的 lane 一律不越过。阻塞只停本端点，另一端点与 SCHED 不受影响。
  * 本模块不产生任何通知 pulse：完成通知走设备自己的中断。
  */
class QueenEndpointCtrl(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg)))))
        val out = Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg))))
        val events = Input(Vec(cfg.eventGroupCount, UInt(32.W)))
        val timelines = Input(Vec(cfg.timelineCount, UInt(32.W)))
        val backendIdle = Input(Bool())
        val flushing = Input(Bool())
        val trace = Valid(new QueenTraceRecord(cfg))
        val idle = Output(Bool())
    })

    private val lanes = cfg.instructionLanes
    private val cntW = log2Ceil(lanes + 1)
    private val active = RegInit(false.B)
    private val pkt = Reg(Vec(lanes, Valid(new QueenDsaCommand(cfg))))
    private val base = RegInit(0.U(cntW.W))

    private val done = active && base >= lanes.U
    io.in.ready := !active || done
    io.idle := !active

    private val inRange = active && base < lanes.U
    private def at(i: UInt): Valid[QueenDsaCommand] =
        if (lanes == 1) pkt(0) else pkt(i(log2Ceil(lanes) - 1, 0))

    private val cand = Wire(Vec(lanes, Valid(new QueenDsaCommand(cfg))))
    private val candFwd = Wire(Vec(lanes, Bool()))
    private val candCtrl = Wire(Vec(lanes, Bool()))
    for (j <- 0 until lanes) {
        val idx = base +& j.U
        val slot = at(idx)
        val funct3 = slot.bits.inst(14, 12)
        val cmd = slot.bits.inst(29, 26)
        cand(j).valid := inRange && idx < lanes.U && slot.valid
        cand(j).bits := slot.bits
        candFwd(j) := cand(j).valid &&
            (funct3 <= 2.U || (funct3 === 3.U && cmd === 9.U))
        candCtrl(j) := cand(j).valid && !candFwd(j)
    }

    private def firstIdx(sel: Vec[Bool]): UInt = {
        val idx = WireDefault(0.U(cntW.W))
        for (j <- (0 until lanes).reverse) {
            when(sel(j)) { idx := j.U(cntW.W) }
        }
        idx
    }

    private def pick[T <: Data](sel: Vec[Bool], get: Int => T, zero: T): T = {
        val out = WireDefault(zero)
        for (j <- (0 until lanes).reverse) {
            when(sel(j)) { out := get(j) }
        }
        out
    }

    private val hasCtrl = candCtrl.asUInt.orR
    private val firstValidIdx = firstIdx(VecInit(cand.map(_.valid)))
    private val firstCtrlIdx = firstIdx(candCtrl)
    // 首条有效 lane 就是控制命令时独占一拍消费，否则转发停在它之前的切片。
    private val alone = hasCtrl && firstValidIdx === firstCtrlIdx
    private val room = lanes.U(cntW.W) - base
    private val fwdLen = Mux(hasCtrl, firstCtrlIdx, room)
    private val beatLen = Mux(alone, firstCtrlIdx + 1.U, fwdLen)

    private val fwd = VecInit((0 until lanes).map(j =>
        !alone && cand(j).valid && j.U(cntW.W) < fwdLen))
    for (j <- 0 until lanes) {
        io.out.bits(j).valid := fwd(j)
        io.out.bits(j).bits := Mux(fwd(j), cand(j).bits, 0.U.asTypeOf(new QueenDsaCommand(cfg)))
    }
    io.out.valid := fwd.asUInt.orR && !io.flushing

    private val ctrlSel = VecInit((0 until lanes).map(j => alone && j.U(cntW.W) === firstCtrlIdx))
    private val ctrlInst = pick(ctrlSel, j => cand(j).bits.inst, 0.U(32.W))
    private val ctrlRs1 = pick(ctrlSel, j => cand(j).bits.rs1, 0.U(cfg.xlen.W))
    private val cmd = ctrlInst(29, 26)
    private val aux = ctrlInst(11, 7)
    // 必须先认 funct3：QRD 的地址位会落在 cmd 字段上，只看 cmd 会把读命令误判为 WAIT。
    private val isCtrl = ctrlInst(14, 12) === 3.U
    private val isFence = isCtrl && cmd === 0.U
    private val isWaitEvent = isCtrl && cmd === 1.U
    private val isWaitTimeline = isCtrl && cmd === 2.U
    private val isTrace = isCtrl && cmd === 5.U

    private val eventMask = io.events(aux(4, 0))
    private val eventOk = (eventMask & ctrlRs1) === ctrlRs1
    // timeline 是回绕计数，判据为 signed 距离而非无符号大小。
    private val timelineOk = (io.timelines(aux(2, 0)) - ctrlRs1).asSInt >= 0.S
    private val cond = (isFence && io.backendIdle) || (isWaitEvent && eventOk) ||
        (isWaitTimeline && timelineOk) || isTrace

    assert(!alone || isFence || isWaitEvent || isWaitTimeline || isTrace,
        "QueenEndpointCtrl: only FENCE/WAIT/TRACE are consumed at this stage")

    io.trace.valid := inRange && alone && isTrace && !io.flushing
    io.trace.bits.swTag := ctrlRs1
    io.trace.bits.hwTag := 0.U(cfg.xlen.W)

    private val advance = inRange && (io.out.fire || (alone && cond) || (!alone && !io.out.valid))
    when(advance) { base := base + beatLen }
    when(done && !io.in.fire) { active := false.B }
    when(io.in.fire && !io.flushing) {
        pkt := io.in.bits
        base := 0.U
        active := true.B
    }
    // flush 期间整包丢弃：ready 仍为 1 让上游 ring 排空，但不再装载、不再转发。
    when(io.flushing) {
        active := false.B
        base := 0.U
    }
}