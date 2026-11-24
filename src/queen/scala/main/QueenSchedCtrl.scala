import chisel3._
import chisel3.util._

/** SCHED 端点控制器：同步指令执行 + Scheduler 寄存器前端 + polling/atomic 执行点。
  *
  * 队头连续的 QWR1/QWR2 切片一拍并行执行；其余命令独占执行点，因此 QWR→SIGNAL→QRD 天然保序。
  * POLL_EQ 阻塞执行点直到读值相等（每 POLL_INTERVAL 拍重读一次）；ATOMIC_RMW 发出即放行，
  * 最多 memDepth 笔在途，靠内存主接口的按序响应保证它先于其后的 POLL_EQ 落地。
  * 两者都不产生 CPU 可见完成通知：效果只体现为后续指令的顺序与内存副作用。
  *
  * 寄存器映射：0x00 FAULT_CLEAR(WO) / 0x01 RECOVER(WO) / 0x02 POLL_INTERVAL(RW,复位8) /
  * 0x40..0x5F EVENT[0..31](RO) / 0x60..0x67 TIMELINE[0..7](RO) /
  * 0x68..0x6B FAULT_VALID/KIND/ENDPOINT/CODE(RO) / 0x6C IDLE(RO)。
  * 其余地址写回 wrErr、读回 err。
  */
class QueenSchedCtrl(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg)))))
        val sw = Output(new QueenSyncWrite(cfg))
        val resp = Decoupled(new QueenDsaRegResp)
        val wrErr = Output(Vec(cfg.instructionLanes, Bool()))
        val faultClear = Output(Bool())
        val flushTrig = Output(Bool())
        val memReq = Decoupled(new QueenMemReq(cfg))
        val rdData = Flipped(Valid(UInt(32.W)))
        val memUsed = Input(UInt(log2Ceil(cfg.memDepth + 1).W))
        val addrErr = Output(Bool())
        val events = Input(Vec(cfg.eventGroupCount, UInt(32.W)))
        val timelines = Input(Vec(cfg.timelineCount, UInt(32.W)))
        val faultValid = Input(Bool())
        val faultKind = Input(UInt(2.W))
        val faultEndpoint = Input(UInt(2.W))
        val faultCode = Input(UInt(cfg.faultCodeWidth.W))
        // 不含本模块与在途 QRD：读 IDLE 的那条 QRD 若计入自身，读回值恒 0。
        val idleIn = Input(Bool())
        val flushing = Input(Bool())
        val idle = Output(Bool())
    })

    private val lanes = cfg.instructionLanes
    private val cntW = log2Ceil(lanes + 1)
    private val active = RegInit(false.B)
    private val pkt = Reg(Vec(lanes, Valid(new QueenDsaCommand(cfg))))
    private val base = RegInit(0.U(cntW.W))
    private val pollInterval = RegInit(8.U(32.W))

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
        !alone && !io.flushing && cand(j).valid && j.U(cntW.W) < fwdLen))
    private def wrLegal(addr: UInt, data: UInt): Bool =
        addr === 0.U || addr === 1.U || (addr === 2.U && data =/= 0.U)
    private val clear = Wire(Vec(lanes, Bool()))
    private val recover = Wire(Vec(lanes, Bool()))
    for (j <- 0 until lanes) {
        val b = cand(j).bits
        val isW2 = b.inst(14, 12) === 0.U
        val regBase = Cat(0.U(4.W), Cat(b.inst(11), b.inst(29, 25)))
        val addr0 = Mux(isW2, regBase, b.inst(29, 20))
        val addr1 = regBase + 1.U(10.W)
        io.wrErr(j) := wrSel(j) &&
            (!wrLegal(addr0, b.rs1) || (isW2 && !wrLegal(addr1, b.rs2)))
        clear(j) := wrSel(j) && (addr0 === 0.U || (isW2 && addr1 === 0.U))
        recover(j) := wrSel(j) && (addr0 === 1.U || (isW2 && addr1 === 1.U))
        // 同拍多 lane 写 POLL_INTERVAL 以 lane 序最后一条为准。
        when(wrSel(j) && addr0 === 2.U && b.rs1 =/= 0.U) { pollInterval := b.rs1 }
        when(wrSel(j) && isW2 && addr1 === 2.U && b.rs2 =/= 0.U) { pollInterval := b.rs2 }
    }
    io.faultClear := clear.asUInt.orR

    private val sel = VecInit((0 until lanes).map(j => alone && j.U(cntW.W) === firstOtherIdx))
    private val inst = pick(sel, j => cand(j).bits.inst, 0.U(32.W))
    private val rs1 = pick(sel, j => cand(j).bits.rs1, 0.U(cfg.xlen.W))
    private val rs2 = pick(sel, j => cand(j).bits.rs2, 0.U(cfg.xlen.W))
    private val cmd = inst(29, 26)
    private val isQrd = inst(14, 12) === 2.U
    private val isCtrl = inst(14, 12) === 3.U
    private val isSignalEvent = isCtrl && cmd === 4.U
    private val isSignalTimeline = isCtrl && cmd === 3.U
    private val isTrace = isCtrl && cmd === 5.U
    private val isPollEq = isCtrl && cmd === 6.U
    private val isAtomic = isCtrl && cmd === 8.U
    private val isFlush = isCtrl && cmd === 10.U
    private val supported = isQrd || isSignalEvent || isSignalTimeline || isTrace ||
        isPollEq || isAtomic || isFlush
    private val exec = alone && supported && !io.flushing

    assert(!alone || supported,
        "QueenSchedCtrl: unsupported QCTRL cmd reached the SCHED execution point")

    io.sw.valid := exec && (isSignalEvent || isSignalTimeline)
    io.sw.isTimeline := isSignalTimeline
    io.sw.index := inst(11, 7)
    io.sw.clr := isSignalEvent && !inst(25)
    // 自增/赋值取决于 rs1 的寄存器索引是否为 x0，不是读回值。
    io.sw.assign := isSignalTimeline && inst(19, 15) =/= 0.U
    io.sw.data := rs1

    // ---- polling 执行点：POLL_EQ 跨多拍持有队头，期间不执行其它命令 ----
    private val pollBusy = RegInit(false.B)
    private val pollWait = RegInit(false.B)
    private val pollTimer = RegInit(0.U(32.W))
    private val pollAddr = Reg(UInt(cfg.xlen.W))
    private val pollExp = Reg(UInt(32.W))

    private val pollStart = exec && isPollEq && !pollBusy
    private val pollMisalign = pollStart && rs1(1, 0) =/= 0.U
    private val pollMatch = pollBusy && pollWait && io.rdData.valid &&
        io.rdData.bits === pollExp
    private val pollMiss = pollBusy && pollWait && io.rdData.valid &&
        io.rdData.bits =/= pollExp
    private val pollIssue = pollBusy && !pollWait && pollTimer === 0.U

    when(pollStart && !pollMisalign) {
        pollBusy := true.B
        pollWait := false.B
        pollTimer := 0.U
        pollAddr := rs1
        pollExp := rs2
    }
    when(pollIssue && io.memReq.fire) { pollWait := true.B }
    when(pollMatch) { pollBusy := false.B; pollWait := false.B }
    when(pollMiss) { pollWait := false.B; pollTimer := pollInterval }
    when(pollBusy && !pollWait && pollTimer =/= 0.U) { pollTimer := pollTimer - 1.U }

    private val atomicOp = MuxLookup(inst(8, 7), QueenMemOp.READ.asUInt)(Seq(
        0.U -> QueenMemOp.ATOMIC_ADD.asUInt,
        1.U -> QueenMemOp.ATOMIC_CLR.asUInt,
        2.U -> QueenMemOp.ATOMIC_SET.asUInt)).asTypeOf(QueenMemOp())
    private val atomicStart = exec && isAtomic && !pollBusy
    private val atomicMisalign = atomicStart && rs1(1, 0) =/= 0.U
    private val memRoom = io.memUsed < cfg.memDepth.U

    io.memReq.valid := (pollIssue || (atomicStart && !atomicMisalign)) && memRoom && !io.flushing
    io.memReq.bits.op := Mux(pollIssue, QueenMemOp.READ.asUInt, atomicOp.asUInt)
        .asTypeOf(QueenMemOp())
    io.memReq.bits.address := Mux(pollIssue, pollAddr, rs1)
    io.memReq.bits.operand := Mux(pollIssue, 0.U(32.W), rs2)
    io.addrErr := pollMisalign || atomicMisalign
    io.flushTrig := recover.asUInt.orR || (exec && isFlush)

    private val rdAddr = inst(29, 20)
    private val isEvent = rdAddr >= 0x40.U && rdAddr <= 0x5f.U
    private val isTimeline = rdAddr >= 0x60.U && rdAddr <= 0x67.U
    private val isFault = rdAddr >= 0x68.U && rdAddr <= 0x6b.U
    private val isIdle = rdAddr === 0x6c.U
    private val isInterval = rdAddr === 0x02.U
    private val faultField = VecInit(Seq(
        Cat(0.U(32.W), io.faultValid)(31, 0),
        Cat(0.U(32.W), io.faultKind)(31, 0),
        Cat(0.U(32.W), io.faultEndpoint)(31, 0),
        Cat(0.U(32.W), io.faultCode)(31, 0)))(rdAddr(1, 0))
    private val rdLegal = isEvent || isTimeline || isFault || isIdle || isInterval
    io.resp.valid := exec && isQrd
    io.resp.bits.err := !rdLegal
    io.resp.bits.data := Mux(!rdLegal, 0.U(32.W),
        Mux(isEvent, io.events(rdAddr(4, 0)),
            Mux(isTimeline, io.timelines(rdAddr(2, 0)),
                Mux(isIdle, Cat(0.U(31.W), io.idleIn && io.idle),
                    Mux(isInterval, pollInterval, faultField)))))

    private val aloneDone = Mux(isPollEq, pollMatch || pollMisalign,
        Mux(isAtomic, atomicMisalign || io.memReq.fire,
            Mux(isQrd, io.resp.fire, exec)))
    private val advance = inRange && Mux(alone, aloneDone, !pollBusy)
    when(advance) { base := base + beatLen }
    when(done && !io.in.fire) { active := false.B }
    when(io.in.fire && !io.flushing) {
        pkt := io.in.bits
        base := 0.U
        active := true.B
    }
    // flush 期间整包丢弃并中止 polling：ready 仍为 1 让 SCHED 暂存排空。
    when(io.flushing) {
        active := false.B
        base := 0.U
        pollBusy := false.B
        pollWait := false.B
        pollTimer := 0.U
    }
}