import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class QueenSchedCtrlSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private val Sched = 3

    private case class Cmd(inst: BigInt, rs1: BigInt = 0, rs2: BigInt = 0, pc: BigInt = 0) {
        def funct3: Int = ((inst >> 12) & 7).toInt
        def cmd: Int = ((inst >> 26) & 15).toInt
        def aux: Int = ((inst >> 7) & 31).toInt
        def memOp: Int = ((inst >> 7) & 3).toInt
        def rs1Idx: Int = ((inst >> 15) & 31).toInt
        def set: Boolean = ((inst >> 25) & 1) == 1
        def addr: Int = ((inst >> 20) & 1023).toInt
        def regBase: Int = ((((inst >> 11) & 1) << 5) | ((inst >> 25) & 31)).toInt
        def isWr: Boolean = funct3 == 0 || funct3 == 1
        def isW2: Boolean = funct3 == 0
    }

    private def signalEvent(group: Int, mask: BigInt, set: Boolean = true, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(4) << 26) | (BigInt(if (set) 1 else 0) << 25) |
            (BigInt(1) << 15) | (BigInt(group) << 7) | (BigInt(3) << 12) | 0x0b, mask, 0, pc)

    private def signalTimeline(id: Int, value: BigInt, inc: Boolean = false, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(3) << 26) | (BigInt(if (inc) 0 else 1) << 15) |
            (BigInt(id) << 7) | (BigInt(3) << 12) | 0x0b, value, 0, pc)

    private def traceTag(value: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(5) << 26) | (BigInt(1) << 15) |
            (BigInt(3) << 12) | 0x0b, value, 0, pc)

    private def pollEq(addr: BigInt, exp: BigInt = 0, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(6) << 26) | (BigInt(1) << 15) |
            (BigInt(2) << 20) | (BigInt(3) << 12) | 0x0b, addr, exp, pc)

    private def atomicRmw(op: Int, addr: BigInt, operand: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(8) << 26) | (BigInt(op) << 7) |
            (BigInt(1) << 15) | (BigInt(2) << 20) | (BigInt(3) << 12) | 0x0b,
            addr, operand, pc)

    private def flushCmd(pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(10) << 26) | (BigInt(3) << 12) | 0x0b, 0, 0, pc)

    private def qwr1(addr: Int, data: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(addr) << 20) | (BigInt(1) << 15) |
            (BigInt(1) << 12) | 0x0b, data, 0, pc)

    private def qwr2(base: Int, data0: BigInt, data1: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(base & 31) << 25) | (BigInt(2) << 20) |
            (BigInt(1) << 15) | (BigInt((base >> 5) & 1) << 11) | 0x0b, data0, data1, pc)

    private def qrd(addr: Int, rd: Int = 1, pc: BigInt = 0) =
        Cmd((BigInt(Sched) << 30) | (BigInt(addr) << 20) | (BigInt(2) << 12) |
            (BigInt(rd) << 7) | 0x0b, 0, 0, pc)

    private case class Sw(isTimeline: Boolean, index: Int, clr: Boolean, assign: Boolean,
                          data: BigInt)

    private class Driver(dut: QueenSchedCtrl, cfg: QueenConfig) {
        private val lanes = cfg.instructionLanes
        private var active = false
        private var pkt = Vector.fill[Option[Cmd]](lanes)(None)
        private var base = 0
        var writes = Vector.empty[Sw]
        var consumed = Vector.empty[Cmd]
        var resps = Vector.empty[(BigInt, Boolean)]
        var wrErrs = Vector.empty[Vector[Int]]
        var clears = 0
        var respReady = true
        var events = Vector.fill(cfg.eventGroupCount)(BigInt(0))
        var timelines = Vector.fill(cfg.timelineCount)(BigInt(0))
        var faultValid = false
        var faultKind = 0
        var faultEndpoint = 0
        var faultCode = 0
        var idleIn = true
        var memReqReady = true
        var rdDataNext = Option.empty[BigInt]
        var memInflight = Vector.empty[Boolean]
        var memReqs = Vector.empty[(Int, BigInt, BigInt)]
        var addrErrs = 0
        var flushTrigs = 0
        var pollInterval = BigInt(8)
        var pollTimeoutReg = BigInt(0)
        var timeouts = 0
        private var timeoutCnt = BigInt(0)
        private var pollBusy = false
        private var pollWait = false
        private var pollTimer = BigInt(0)
        private var pollAddr = BigInt(0)
        private var pollExp = BigInt(0)

        dut.io.in.valid.poke(false.B)
        dut.io.in.bits.foreach { l =>
            l.valid.poke(false.B); l.bits.inst.poke(0.U)
            l.bits.rs1.poke(0.U); l.bits.rs2.poke(0.U)
        }
        dut.io.resp.ready.poke(true.B)
        dut.io.idleIn.poke(true.B)
        dut.io.memReq.ready.poke(true.B)
        dut.io.memUsed.poke(0.U)
        dut.io.rdData.valid.poke(false.B)
        dut.io.rdData.bits.poke(0.U)
        dut.io.events.foreach(_.poke(0.U))
        dut.io.timelines.foreach(_.poke(0.U))
        dut.io.faultValid.poke(false.B)
        dut.io.faultKind.poke(0.U)
        dut.io.faultEndpoint.poke(0.U)
        dut.io.faultCode.poke(0.U)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def offer(commands: (Int, Cmd)*): Vector[Option[Cmd]] =
            commands.foldLeft(Vector.fill[Option[Cmd]](lanes)(None)) {
                case (v, (i, c)) => v.updated(i, Some(c))
            }

        private def readValue(addr: Int): (BigInt, Boolean) =
            if (addr >= 0x40 && addr <= 0x5f) (events(addr - 0x40), false)
            else if (addr >= 0x60 && addr <= 0x67) (timelines(addr - 0x60), false)
            else if (addr == 0x68) (BigInt(if (faultValid) 1 else 0), false)
            else if (addr == 0x69) (BigInt(faultKind), false)
            else if (addr == 0x6a) (BigInt(faultEndpoint), false)
            else if (addr == 0x6b) (BigInt(faultCode), false)
            else if (addr == 0x6c) (BigInt(if (idleIn && !active) 1 else 0), false)
            else if (addr == 0x02) (pollInterval, false)
            else if (addr == 0x03) (pollTimeoutReg, false)
            else (BigInt(0), true)

        private def wrLegal(addr: Int, data: BigInt): Boolean =
            addr == 0 || addr == 1 || addr == 3 || (addr == 2 && data != 0)

        private var cycleNo = 0

        def cycle(offered: Option[Vector[Option[Cmd]]] = None): Boolean = {
            cycleNo += 1
            dut.io.in.valid.poke(offered.nonEmpty.B)
            for (i <- 0 until lanes) {
                val l = dut.io.in.bits(i)
                val c = offered.flatMap(_(i))
                l.valid.poke(c.nonEmpty.B)
                l.bits.inst.poke(c.map(_.inst).getOrElse(BigInt(0)).U)
                l.bits.rs1.poke(c.map(_.rs1).getOrElse(BigInt(0)).U)
                l.bits.rs2.poke(c.map(_.rs2).getOrElse(BigInt(0)).U)
            }
            dut.io.resp.ready.poke(respReady.B)
            dut.io.idleIn.poke(idleIn.B)
            for (g <- 0 until cfg.eventGroupCount) dut.io.events(g).poke(events(g).U)
            for (t <- 0 until cfg.timelineCount) dut.io.timelines(t).poke(timelines(t).U)
            dut.io.faultValid.poke(faultValid.B)
            dut.io.faultKind.poke(faultKind.U)
            dut.io.faultEndpoint.poke(faultEndpoint.U)
            dut.io.faultCode.poke(faultCode.U)
            dut.io.memReq.ready.poke(memReqReady.B)
            dut.io.memUsed.poke(memInflight.size.U)
            dut.io.rdData.valid.poke(rdDataNext.nonEmpty.B)
            dut.io.rdData.bits.poke(rdDataNext.getOrElse(BigInt(0)).U)

            val done = active && base >= lanes
            val inReady = !active || done
            dut.io.in.ready.expect(inReady.B)
            dut.io.idle.expect((!active).B)
            val inFire = offered.nonEmpty && inReady

            val inRange = active && base < lanes
            val cand = Vector.tabulate(lanes) { j =>
                if (inRange && base + j < lanes) pkt(base + j) else None
            }
            val candOther = cand.map(c => c.nonEmpty && !c.get.isWr)
            val hasOther = candOther.exists(identity)
            val hasValid = cand.exists(_.nonEmpty)
            val firstValidIdx = cand.indexWhere(_.nonEmpty)
            val firstOtherIdx = candOther.indexWhere(identity)
            val alone = hasOther && firstValidIdx == firstOtherIdx
            val room = lanes - base
            val fwdLen = if (hasOther) firstOtherIdx else room
            val beatLen = if (alone) firstOtherIdx + 1 else fwdLen
            val wrSel = Vector.tabulate(lanes)(j => !alone && cand(j).nonEmpty && j < fwdLen)

            val interval0 = pollInterval
            val tReg0 = pollTimeoutReg
            val expErr = Vector.tabulate(lanes) { j =>
                if (!wrSel(j)) false else {
                    val c = cand(j).get
                    val a0 = if (c.isW2) c.regBase else c.addr
                    val a1 = c.regBase + 1
                    !wrLegal(a0, c.rs1) || (c.isW2 && !wrLegal(a1, c.rs2))
                }
            }
            def hits(c: Cmd, target: Int): Boolean = {
                val a0 = if (c.isW2) c.regBase else c.addr
                a0 == target || (c.isW2 && c.regBase + 1 == target)
            }
            val expClear = wrSel.zipWithIndex.exists { case (s, j) => s && hits(cand(j).get, 0) }
            val expRecover = wrSel.zipWithIndex.exists { case (s, j) => s && hits(cand(j).get, 1) }
            for (j <- 0 until lanes) {
                if (wrSel(j)) {
                    val c = cand(j).get
                    val a0 = if (c.isW2) c.regBase else c.addr
                    if (a0 == 2 && c.rs1 != 0) pollInterval = c.rs1
                    if (c.isW2 && c.regBase + 1 == 2 && c.rs2 != 0) pollInterval = c.rs2
                    if (a0 == 3) pollTimeoutReg = c.rs1
                    if (c.isW2 && c.regBase + 1 == 3) pollTimeoutReg = c.rs2
                }
            }
            for (j <- 0 until lanes) {
                withClue(s"cycle=$cycleNo lane=$j wrErr ") {
                    dut.io.wrErr(j).expect(expErr(j).B)
                }
            }
            dut.io.faultClear.expect(expClear.B)
            if (expClear) clears += 1
            val errLanes = expErr.zipWithIndex.filter(_._1).map(_._2)
            if (errLanes.nonEmpty) wrErrs = wrErrs :+ errLanes
            if (inRange) consumed = consumed ++ cand.zipWithIndex.filter { case (_, j) => wrSel(j) }.map(_._1.get)

            val head = if (alone) Some(cand(firstOtherIdx).get) else None
            val isQrd = head.exists(_.funct3 == 2)
            val supported = head.exists(c =>
                c.funct3 == 2 || (c.funct3 == 3 && Set(3, 4, 5, 6, 8, 10).contains(c.cmd)))
            val exec = alone && supported
            val respFire = exec && isQrd && respReady

            val expSw = exec && head.exists(c => c.funct3 == 3 && (c.cmd == 3 || c.cmd == 4))
            dut.io.sw.valid.expect(expSw.B)
            head.foreach { c =>
                if (expSw) {
                    val exp = Sw(c.cmd == 3, c.aux, c.cmd == 4 && !c.set,
                        c.cmd == 3 && c.rs1Idx != 0, c.rs1)
                    val act = Sw(dut.io.sw.isTimeline.peek().litToBoolean,
                        dut.io.sw.index.peek().litValue.toInt,
                        dut.io.sw.clr.peek().litToBoolean,
                        dut.io.sw.assign.peek().litToBoolean,
                        dut.io.sw.data.peek().litValue)
                    withClue(s"cycle=$cycleNo sw ") { act shouldBe exp }
                    writes = writes :+ exp
                }
            }

            dut.io.resp.valid.expect((exec && isQrd).B)
            head.foreach { c =>
                if (exec && isQrd) {
                    val (data, err) = readValue(c.addr)
                    val act = (dut.io.resp.bits.data.peek().litValue,
                        dut.io.resp.bits.err.peek().litToBoolean)
                    withClue(s"cycle=$cycleNo addr=${c.addr} ") { act shouldBe ((data, err)) }
                    if (respFire) resps = resps :+ ((data, err))
                }
            }
            val isPoll = head.exists(c => c.funct3 == 3 && c.cmd == 6)
            val isAtomic = head.exists(c => c.funct3 == 3 && c.cmd == 8)
            val isFlush = head.exists(c => c.funct3 == 3 && c.cmd == 10)
            val pBusy0 = pollBusy
            val pWait0 = pollWait
            val pTimer0 = pollTimer
            val tCnt0 = timeoutCnt
            val timeoutEn = tReg0 != 0
            val pollTimeout = pBusy0 && timeoutEn && tCnt0 == 0
            val pollStart = exec && isPoll && !pBusy0
            val pollMisalign = pollStart && head.get.rs1 % 4 != 0
            val pollIssue = pBusy0 && !pWait0 && pTimer0 == 0 && !pollTimeout
            val pollMatch = pBusy0 && pWait0 && rdDataNext.contains(pollExp) && !pollTimeout
            val pollMiss = pBusy0 && pWait0 && rdDataNext.exists(_ != pollExp) && !pollTimeout
            val pollAbort = pollTimeout && (!pWait0 || rdDataNext.nonEmpty)
            val atomicStart = exec && isAtomic && !pBusy0
            val atomicMisalign = atomicStart && head.get.rs1 % 4 != 0
            val memRoom = memInflight.size < cfg.memDepth
            val expMemValid = (pollIssue || (atomicStart && !atomicMisalign)) && memRoom
            val memFire = expMemValid && memReqReady
            val expOp = if (pollIssue) 0 else Vector(1, 2, 3, 0)(head.map(_.memOp).getOrElse(0))
            val expAddr = if (pollIssue) pollAddr else head.map(_.rs1).getOrElse(BigInt(0))
            val expOperand = if (pollIssue) BigInt(0) else head.map(_.rs2).getOrElse(BigInt(0))

            dut.io.memReq.valid.expect(expMemValid.B)
            if (expMemValid) {
                val act = (dut.io.memReq.bits.op.peek().litValue.toInt,
                    dut.io.memReq.bits.address.peek().litValue,
                    dut.io.memReq.bits.operand.peek().litValue)
                withClue(s"cycle=$cycleNo memReq ") { act shouldBe ((expOp, expAddr, expOperand)) }
            }
            val expAddrErr = pollMisalign || atomicMisalign
            dut.io.addrErr.expect(expAddrErr.B)
            if (expAddrErr) addrErrs += 1
            dut.io.timeout.expect(pollAbort.B)
            if (pollAbort) timeouts += 1
            val expFlush = expRecover || (exec && isFlush)
            dut.io.flushTrig.expect(expFlush.B)
            if (expFlush) flushTrigs += 1

            val aloneDone =
                if (isPoll) pollMatch || pollMisalign || pollAbort
                else if (isAtomic) atomicMisalign || memFire
                else if (isQrd) respFire
                else exec
            val advance = inRange && (if (alone) aloneDone else !pBusy0)
            if (advance && alone) consumed = consumed :+ head.get

            if (pollStart && !pollMisalign) {
                pollBusy = true; pollWait = false; pollTimer = BigInt(0)
                pollAddr = head.get.rs1; pollExp = head.get.rs2
                timeoutCnt = tReg0
            }
            if (pollIssue && memFire) pollWait = true
            if (pollMatch) { pollBusy = false; pollWait = false }
            if (pollMiss) { pollWait = false; pollTimer = interval0 }
            if (pollAbort) { pollBusy = false; pollWait = false }
            if (pBusy0 && !pWait0 && pTimer0 != 0) pollTimer = pTimer0 - 1
            if (pBusy0 && timeoutEn && tCnt0 != 0) timeoutCnt = tCnt0 - 1
            if (memFire) {
                memInflight = memInflight :+ pollIssue
                memReqs = memReqs :+ ((expOp, expAddr, expOperand))
            }
            if (rdDataNext.nonEmpty) memInflight = memInflight.drop(1)
            rdDataNext = None

            if (advance) base += beatLen
            if (done && !inFire) active = false
            if (inFire) {
                pkt = offered.get
                base = 0
                active = true
            }
            dut.clock.step()
            inFire
        }

        def drain(): Unit = {
            var cycles = 0
            while (active && cycles < 400) { cycle(); cycles += 1 }
            active shouldBe false
        }
    }

    behavior of "QueenSchedCtrl"

    it should "turn SIGNAL_EVENT into a set or clear write" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> signalEvent(5, 0x88), 1 -> signalEvent(6, 0x0f, set = false))))
            d.drain()
            d.writes shouldBe Vector(Sw(false, 5, false, false, BigInt(0x88)),
                Sw(false, 6, true, false, BigInt(0x0f)))
        }
    }

    it should "decide timeline increment by the rs1 register index, not its value" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            // x0 索引 + 非零读回值仍须自增；非 x0 索引 + 零值仍须赋值。
            d.cycle(Some(d.offer(0 -> signalTimeline(2, 0x1234, inc = true),
                1 -> signalTimeline(3, 0))))
            d.drain()
            d.writes shouldBe Vector(Sw(true, 2, false, false, BigInt(0x1234)),
                Sw(true, 3, false, true, BigInt(0)))
        }
    }

    it should "consume TRACE_TAG without issuing a sync write" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> traceTag(0x99), 1 -> signalEvent(1, 0x1))))
            d.drain()
            d.writes shouldBe Vector(Sw(false, 1, false, false, BigInt(1)))
            d.consumed.size shouldBe 2
        }
    }

    it should "execute a four-lane packet one command per cycle in lane order" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = Vector(signalEvent(0, 1), signalTimeline(1, 5), traceTag(7),
                signalEvent(2, 3, set = false))
            d.cycle(Some(d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)))
            d.drain()
            d.consumed shouldBe cmds
            d.writes.size shouldBe 3
        }
    }

    it should "execute a contiguous write slice in parallel in one cycle" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = Vector(qwr1(0, 1, pc = 0), qwr1(0, 2, pc = 4), qwr1(0, 3, pc = 8),
                signalEvent(1, 0x1, pc = 12))
            d.cycle(Some(d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)))
            d.cycle()
            d.consumed shouldBe cmds.take(3)
            d.clears shouldBe 1
            d.drain()
            d.consumed shouldBe cmds
        }
    }

    it should "clear the fault on 0x00, trigger recover on 0x01 and reject reserved writes" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr1(0x00, 0), 1 -> qwr1(0x01, 0), 2 -> qwr1(0x40, 0))))
            d.cycle()
            d.wrErrs shouldBe Vector(Vector(2))
            d.clears shouldBe 1
            d.flushTrigs shouldBe 1
            // QWR2 可以一拍同时写 POLL_INTERVAL 与 POLL_TIMEOUT，两个地址都合法。
            d.cycle(Some(d.offer(0 -> qwr2(2, 4, 9))))
            d.cycle()
            d.wrErrs shouldBe Vector(Vector(2))
            d.pollInterval shouldBe BigInt(4)
            d.pollTimeoutReg shouldBe BigInt(9)
            // 第二个地址落在保留区时整条 lane 报错。
            d.cycle(Some(d.offer(0 -> qwr2(3, 0, 0))))
            d.cycle()
            d.wrErrs shouldBe Vector(Vector(2), Vector(0))
            d.clears shouldBe 1
            d.drain()
        }
    }

    it should "read events, timelines, fault fields and idle through QRD" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.events = Vector.tabulate(32)(g => BigInt(g * 3 + 1))
            d.timelines = Vector.tabulate(8)(t => BigInt(t * 7 + 2))
            d.faultValid = true
            d.faultKind = 2
            d.faultEndpoint = 3
            d.faultCode = 5
            val addrs = Vector(0x40, 0x5f, 0x60, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c)
            addrs.foreach { a => d.cycle(Some(d.offer(0 -> qrd(a)))); d.drain() }
            d.resps shouldBe Vector(
                (BigInt(1), false), (BigInt(94), false), (BigInt(2), false), (BigInt(51), false),
                (BigInt(1), false), (BigInt(2), false), (BigInt(3), false), (BigInt(5), false),
                (BigInt(0), false))
        }
    }

    it should "return err for a read outside the mapped window" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            Vector(0x00, 0x3f, 0x6d, 0x80, 0x3ff).foreach { a =>
                d.cycle(Some(d.offer(0 -> qrd(a))))
                d.drain()
            }
            d.resps shouldBe Vector.fill(5)((BigInt(0), true))
        }
    }

    it should "hold a QRD until the response channel accepts it" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.respReady = false
            d.cycle(Some(d.offer(0 -> qrd(0x40), 1 -> signalEvent(1, 0x1))))
            for (_ <- 0 until 4) d.cycle()
            d.resps shouldBe empty
            d.writes shouldBe empty
            d.respReady = true
            d.drain()
            d.resps.size shouldBe 1
            d.writes.size shouldBe 1
        }
    }

    it should "not let a QRD bypass an earlier SIGNAL in the same packet" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> signalEvent(2, 0x4), 1 -> qrd(0x42))))
            d.cycle()
            d.writes shouldBe Vector(Sw(false, 2, false, false, BigInt(0x4)))
            d.resps shouldBe empty
            d.drain()
            d.resps.size shouldBe 1
        }
    }

    it should "block the dispatch point on POLL_EQ until the read value matches" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> pollEq(0x1000, 0x5a5a), 1 -> signalEvent(1, 0x1))))
            d.cycle()
            d.cycle()
            d.memReqs shouldBe Vector((0, BigInt(0x1000), BigInt(0)))
            d.rdDataNext = Some(BigInt(1))
            d.cycle()
            d.writes shouldBe empty
            // 复位间隔 8：失配后要等满 8 拍才重读。
            for (_ <- 0 until 8) d.cycle()
            d.memReqs.size shouldBe 1
            d.cycle()
            d.memReqs.size shouldBe 2
            d.rdDataNext = Some(BigInt(0x5a5a))
            d.cycle()
            d.drain()
            d.writes shouldBe Vector(Sw(false, 1, false, false, BigInt(1)))
            d.consumed.size shouldBe 2
        }
    }

    it should "program the polling interval through 0x02 and read it back" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr1(0x02, 3))))
            d.drain()
            d.cycle(Some(d.offer(0 -> qrd(0x02))))
            d.drain()
            d.resps shouldBe Vector((BigInt(3), false))
            d.cycle(Some(d.offer(0 -> qwr1(0x02, 0))))
            d.cycle()
            d.wrErrs shouldBe Vector(Vector(0))
            d.pollInterval shouldBe BigInt(3)
            d.drain()
        }
    }

    it should "program POLL_TIMEOUT through 0x03 and read it back" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr1(0x03, 20))))
            d.drain()
            d.pollTimeoutReg shouldBe BigInt(20)
            d.cycle(Some(d.offer(0 -> qrd(0x03))))
            d.drain()
            d.resps shouldBe Vector((BigInt(20), false))
            // 0 合法，表示禁用超时。
            d.cycle(Some(d.offer(0 -> qwr1(0x03, 0))))
            d.drain()
            d.pollTimeoutReg shouldBe BigInt(0)
            d.wrErrs shouldBe empty
        }
    }

    it should "abort POLL_EQ on timeout but wait for the outstanding READ" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr1(0x03, 4))))
            d.drain()
            d.cycle(Some(d.offer(0 -> pollEq(0x1000, 0x5a5a), 1 -> signalEvent(1, 0x1))))
            d.cycle()
            d.cycle()
            d.memReqs.size shouldBe 1
            for (_ <- 0 until 3) d.cycle()
            d.timeouts shouldBe 0
            // 计数到 0 但 READ 还没回：不得放行，否则陈旧响应会配到下一条 POLL_EQ。
            d.cycle()
            d.timeouts shouldBe 0
            d.writes shouldBe empty
            d.rdDataNext = Some(BigInt(1))
            d.cycle()
            d.timeouts shouldBe 1
            d.drain()
            d.writes shouldBe Vector(Sw(false, 1, false, false, BigInt(1)))
            d.consumed.size shouldBe 3
        }
    }

    it should "abort immediately when the timeout expires with no READ outstanding" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr1(0x03, 6))))
            d.drain()
            d.cycle(Some(d.offer(0 -> pollEq(0x1000, 0x5a5a))))
            d.cycle()
            d.cycle()
            d.rdDataNext = Some(BigInt(1))
            d.cycle()
            d.memReqs.size shouldBe 1
            for (_ <- 0 until 4) d.cycle()
            d.timeouts shouldBe 0
            d.cycle()
            d.timeouts shouldBe 1
            // 超时后不得再发 READ。
            d.memReqs.size shouldBe 1
            d.drain()
        }
    }

    it should "poll forever when POLL_TIMEOUT is zero" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> pollEq(0x1000, 0x5a5a))))
            d.cycle()
            d.cycle()
            for (_ <- 0 until 30) d.cycle()
            d.timeouts shouldBe 0
            d.memReqs.size shouldBe 1
            d.rdDataNext = Some(BigInt(0x5a5a))
            d.cycle()
            d.drain()
            d.consumed.size shouldBe 1
        }
    }

    it should "issue ATOMIC_RMW without blocking the dispatch point" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = Vector(atomicRmw(0, 0x2000, 1), atomicRmw(1, 0x2004, 2),
                atomicRmw(2, 0x2008, 4), atomicRmw(0, 0x200c, 8))
            d.cycle(Some(d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)))
            d.drain()
            d.memReqs shouldBe Vector((1, BigInt(0x2000), BigInt(1)),
                (2, BigInt(0x2004), BigInt(2)), (3, BigInt(0x2008), BigInt(4)),
                (1, BigInt(0x200c), BigInt(8)))
            d.memInflight.size shouldBe 4
            d.consumed shouldBe cmds
        }
    }

    it should "hold ATOMIC_RMW while the memory master refuses the request" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.memReqReady = false
            d.cycle(Some(d.offer(0 -> atomicRmw(0, 0x2000, 1))))
            for (_ <- 0 until 4) d.cycle()
            d.memReqs shouldBe empty
            d.memReqReady = true
            d.drain()
            d.memReqs.size shouldBe 1
        }
    }

    it should "flag an unaligned polling or atomic address without issuing a request" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> pollEq(0x1002, 1))))
            d.cycle()
            d.addrErrs shouldBe 1
            d.memReqs shouldBe empty
            d.drain()
            d.cycle(Some(d.offer(0 -> atomicRmw(0, 0x2001, 1))))
            d.cycle()
            d.addrErrs shouldBe 2
            d.memReqs shouldBe empty
            d.drain()
        }
    }

    it should "raise flushTrig for QCTRL FLUSH and for a write to RECOVER" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> flushCmd())))
            d.cycle()
            d.flushTrigs shouldBe 1
            d.drain()
            d.cycle(Some(d.offer(0 -> qwr1(0x01, 0))))
            d.cycle()
            d.flushTrigs shouldBe 2
            d.drain()
        }
    }

    it should "match an ordered software model under random packets" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val rng = new Random(4242)
            var pending = Option.empty[Vector[Option[Cmd]]]
            var expected = Vector.empty[Cmd]
            for (_ <- 0 until 400) {
                if (pending.isEmpty && rng.nextInt(3) != 0) {
                    val p = Vector.tabulate(cfg.instructionLanes) { _ =>
                        if (rng.nextInt(4) == 0) None
                        else Some(rng.nextInt(6) match {
                            case 0 => signalEvent(rng.nextInt(32), rng.nextInt(1 << 20),
                                rng.nextBoolean())
                            case 1 => signalTimeline(rng.nextInt(8), rng.nextInt(1 << 20),
                                rng.nextBoolean())
                            case 2 => traceTag(rng.nextInt(1 << 20))
                            case 3 => qwr1(rng.nextInt(4), rng.nextInt(1 << 20))
                            case 4 => qwr2(rng.nextInt(3), rng.nextInt(1 << 20), rng.nextInt(1 << 20))
                            case _ => qrd(Vector(0x00, 0x40, 0x5f, 0x60, 0x68, 0x6c, 0x7f)(
                                rng.nextInt(7)), rng.nextInt(32))
                        })
                    }
                    if (p.exists(_.nonEmpty)) {
                        pending = Some(p)
                        expected = expected ++ p.flatten
                    }
                }
                d.events = Vector.tabulate(32)(_ => BigInt(rng.nextInt(1 << 20)))
                d.timelines = Vector.tabulate(8)(_ => BigInt(rng.nextInt(1 << 20)))
                d.faultValid = rng.nextBoolean()
                d.faultKind = rng.nextInt(4)
                d.faultEndpoint = rng.nextInt(4)
                d.faultCode = rng.nextInt(256)
                d.idleIn = rng.nextBoolean()
                if (d.cycle(pending)) pending = None
            }
            while (pending.nonEmpty) { if (d.cycle(pending)) pending = None }
            d.drain()
            d.consumed shouldBe expected
        }
    }
}