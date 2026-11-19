import chisel3._
import chisel3.util._

/** SCHED 端点控制器：同步指令执行 + Scheduler 寄存器前端。
  *
  * 队头连续的 QWR1/QWR2 切片一拍并行执行；QRD/SIGNAL_EVENT/SIGNAL_TIMELINE/TRACE_TAG 独占一拍，
  * 因此 QWR→SIGNAL→QRD 天然保序，不需要额外握手。TRACE_TAG 消费为 no-op（记录流未落地）。
  * POLL_EQ/ATOMIC_RMW 需要尚未实现的内存主接口，停等并触发断言。
  *
  * 寄存器映射：0x00 FAULT_CLEAR(WO) / 0x40..0x5F EVENT[0..31](RO) /
  * 0x60..0x67 TIMELINE[0..7](RO) / 0x68..0x6B FAULT_VALID/KIND/ENDPOINT/CODE(RO) / 0x6C IDLE(RO)。
  * 其余地址写回 wrErr、读回 err。
  */
class QueenSchedCtrl(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg)))))
        val sw = Output(new QueenSyncWrite(cfg))
        val resp = Decoupled(new QueenDsaRegResp)
        val wrErr = Output(Vec(cfg.instructionLanes, Bool()))
        val faultClear = Output(Bool())
        val events = Input(Vec(cfg.eventGroupCount, UInt(32.W)))
        val timelines = Input(Vec(cfg.timelineCount, UInt(32.W)))
        val faultValid = Input(Bool())
        val faultKind = Input(UInt(2.W))
        val faultEndpoint = Input(UInt(2.W))
        val faultCode = Input(UInt(cfg.faultCodeWidth.W))
        // 不含本模块与在途 QRD：读 IDLE 的那条 QRD 若计入自身，读回值恒 0。
        val idleIn = Input(Bool())
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
    private val candWr = Wire(Vec(lanes, Bool()))
    private val candOther = Wire(Vec(lanes, Bool()))
    for (j <- 0 until lanes) {
        val idx = base +& j.U
        val slot = at(idx)
        val funct3 = slot.bits.inst(14, 12)
        cand(j).valid := inRange && idx < lanes.U && slot.valid
        cand(j).bits := slot.bits
        candWr(j) := cand(j).valid && (funct3 === 0.U || funct3 === 1.U)
        candOther(j) := cand(j).valid && !candWr(j)
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

    private val hasOther = candOther.asUInt.orR
    private val hasValid = cand.map(_.valid).reduce(_ || _)
    private val firstValidIdx = firstIdx(VecInit(cand.map(_.valid)))
    private val firstOtherIdx = firstIdx(candOther)
    private val alone = hasOther && firstValidIdx === firstOtherIdx
    private val room = lanes.U(cntW.W) - base
    private val fwdLen = Mux(hasOther, firstOtherIdx, room)
    private val beatLen = Mux(alone, firstOtherIdx + 1.U, fwdLen)

    private val wrSel = VecInit((0 until lanes).map(j =>
        !alone && cand(j).valid && j.U(cntW.W) < fwdLen))
    private def wrLegal(addr: UInt): Bool = addr === 0.U
    private val clear = Wire(Vec(lanes, Bool()))
    for (j <- 0 until lanes) {
        val b = cand(j).bits
        val isW2 = b.inst(14, 12) === 0.U
        val regBase = Cat(0.U(4.W), Cat(b.inst(11), b.inst(29, 25)))
        val addr0 = Mux(isW2, regBase, b.inst(29, 20))
        val addr1 = regBase + 1.U(10.W)
        io.wrErr(j) := wrSel(j) && (!wrLegal(addr0) || (isW2 && !wrLegal(addr1)))
        clear(j) := wrSel(j) && (wrLegal(addr0) || (isW2 && wrLegal(addr1)))
    }
    io.faultClear := clear.asUInt.orR

    private val sel = VecInit((0 until lanes).map(j => alone && j.U(cntW.W) === firstOtherIdx))
    private val inst = pick(sel, j => cand(j).bits.inst, 0.U(32.W))
    private val rs1 = pick(sel, j => cand(j).bits.rs1, 0.U(cfg.xlen.W))
    private val cmd = inst(29, 26)
    private val isQrd = inst(14, 12) === 2.U
    private val isCtrl = inst(14, 12) === 3.U
    private val isSignalEvent = isCtrl && cmd === 4.U
    private val isSignalTimeline = isCtrl && cmd === 3.U
    private val isTrace = isCtrl && cmd === 5.U
    private val supported = isQrd || isSignalEvent || isSignalTimeline || isTrace
    private val exec = alone && supported

    assert(!alone || supported,
        "QueenSchedCtrl: only QRD/SIGNAL_EVENT/SIGNAL_TIMELINE/TRACE_TAG are executed at this stage")

    io.sw.valid := exec && (isSignalEvent || isSignalTimeline)
    io.sw.isTimeline := isSignalTimeline
    io.sw.index := inst(11, 7)
    io.sw.clr := isSignalEvent && !inst(25)
    // 自增/赋值取决于 rs1 的寄存器索引是否为 x0，不是读回值。
    io.sw.assign := isSignalTimeline && inst(19, 15) =/= 0.U
    io.sw.data := rs1

    private val rdAddr = inst(29, 20)
    private val isEvent = rdAddr >= 0x40.U && rdAddr <= 0x5f.U
    private val isTimeline = rdAddr >= 0x60.U && rdAddr <= 0x67.U
    private val isFault = rdAddr >= 0x68.U && rdAddr <= 0x6b.U
    private val isIdle = rdAddr === 0x6c.U
    private val faultField = VecInit(Seq(
        Cat(0.U(32.W), io.faultValid)(31, 0),
        Cat(0.U(32.W), io.faultKind)(31, 0),
        Cat(0.U(32.W), io.faultEndpoint)(31, 0),
        Cat(0.U(32.W), io.faultCode)(31, 0)))(rdAddr(1, 0))
    private val rdLegal = isEvent || isTimeline || isFault || isIdle
    io.resp.valid := exec && isQrd
    io.resp.bits.err := !rdLegal
    io.resp.bits.data := Mux(!rdLegal, 0.U(32.W),
        Mux(isEvent, io.events(rdAddr(4, 0)),
            Mux(isTimeline, io.timelines(rdAddr(2, 0)),
                Mux(isIdle, Cat(0.U(31.W), io.idleIn && io.idle), faultField))))

    private val advance = inRange && Mux(alone, exec && (!isQrd || io.resp.fire), true.B)
    when(advance) { base := base + beatLen }
    when(done && !io.in.fire) { active := false.B }
    when(io.in.fire) {
        pkt := io.in.bits
        base := 0.U
        active := true.B
    }
}