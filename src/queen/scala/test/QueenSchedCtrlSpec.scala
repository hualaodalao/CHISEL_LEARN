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

    private def pollEq(addr: BigInt) =
        Cmd((BigInt(Sched) << 30) | (BigInt(6) << 26) | (BigInt(1) << 15) |
            (BigInt(3) << 12) | 0x0b, addr, 0, 0)

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

        dut.io.in.valid.poke(false.B)
        dut.io.in.bits.foreach { l =>
            l.valid.poke(false.B); l.bits.inst.poke(0.U)
            l.bits.pc.poke(0.U); l.bits.rs1.poke(0.U); l.bits.rs2.poke(0.U)
        }
        dut.io.resp.ready.poke(true.B)
        dut.io.idleIn.poke(true.B)
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
            else (BigInt(0), true)

        private var cycleNo = 0

        def cycle(offered: Option[Vector[Option[Cmd]]] = None): Boolean = {
            cycleNo += 1
            dut.io.in.valid.poke(offered.nonEmpty.B)
            for (i <- 0 until lanes) {
                val l = dut.io.in.bits(i)
                val c = offered.flatMap(_(i))
                l.valid.poke(c.nonEmpty.B)
                l.bits.inst.poke(c.map(_.inst).getOrElse(BigInt(0)).U)
                l.bits.pc.poke(c.map(_.pc).getOrElse(BigInt(0)).U)
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

            val expErr = Vector.tabulate(lanes) { j =>
                if (!wrSel(j)) false else {
                    val c = cand(j).get
                    val a0 = if (c.isW2) c.regBase else c.addr
                    val a1 = c.regBase + 1
                    a0 != 0 || (c.isW2 && a1 != 0)
                }
            }
            val expClear = wrSel.zipWithIndex.exists { case (s, j) =>
                s && {
                    val c = cand(j).get
                    val a0 = if (c.isW2) c.regBase else c.addr
                    a0 == 0 || (c.isW2 && c.regBase + 1 == 0)
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
                c.funct3 == 2 || (c.funct3 == 3 && Set(3, 4, 5).contains(c.cmd)))
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
            if (exec) consumed = consumed :+ head.get

            if (inRange && (if (alone) respFire || (exec && !isQrd) else true)) base += beatLen
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

    it should "clear the fault on any write to 0x00 and reject every other write address" in {
        val cfg = QueenConfig()
        simulate(new QueenSchedCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr1(0x00, 0), 1 -> qwr1(0x01, 0), 2 -> qwr1(0x40, 0))))
            d.cycle()
            d.wrErrs shouldBe Vector(Vector(1, 2))
            d.clears shouldBe 1
            // QWR2 的第二个地址必然落在保留区，故整条 lane 报错。
            d.cycle(Some(d.offer(0 -> qwr2(0, 0, 0))))
            d.cycle()
            d.wrErrs shouldBe Vector(Vector(1, 2), Vector(0))
            d.clears shouldBe 2
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

    it should "reject POLL_EQ until the memory master exists" in {
        val cfg = QueenConfig()
        val ex = intercept[Exception] {
            simulate(new QueenSchedCtrl(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                d.cycle(Some(d.offer(0 -> pollEq(0x1000))))
                d.drain()
            }
        }
        ex.getClass.getName should include("AssertionFailed")
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