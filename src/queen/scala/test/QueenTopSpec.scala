import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class QueenTopSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private val Gemm = 0
    private val Dma = 1
    private val Sched = 3
    private val Write1 = 0
    private val Write2 = 1
    private val Read = 2
    private val Run = 3

    private case class Cmd(inst: BigInt, rs1: BigInt = 0, rs2: BigInt = 0, pc: BigInt = 0) {
        def endpoint: Int = ((inst >> 30) & 3).toInt
        def funct3: Int = ((inst >> 12) & 7).toInt
        def isRun: Boolean = funct3 == 3 && ((inst >> 26) & 15).toInt == 9
        def base: Int = ((((inst >> 11) & 1) << 5) | ((inst >> 25) & 31)).toInt
    }

    private def qwr2(ep: Int, base: Int, rs1: BigInt, rs2: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(base & 31) << 25) | (BigInt(2) << 20) |
            (BigInt(1) << 15) | (BigInt((base >> 5) & 1) << 11) | 0x0b, rs1, rs2, pc)

    private def qwr1(ep: Int, addr: Int, rs1: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(addr) << 20) | (BigInt(1) << 15) |
            (BigInt(1) << 12) | 0x0b, rs1, 0, pc)

    private def run(ep: Int, tag: Int, sw: BigInt = 0, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(9) << 26) | (BigInt(1) << 15) |
            (BigInt(3) << 12) | 0x0b, tag, sw, pc)

    private def qrd(ep: Int, addr: Int, rd: Int, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(addr) << 20) | (BigInt(2) << 12) |
            (BigInt(rd) << 7) | 0x0b, 0, 0, pc)

    private def traceTag(ep: Int, value: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(5) << 26) | (BigInt(1) << 15) |
            (BigInt(3) << 12) | 0x0b, value, 0, pc)

    private def signalEvent(group: Int, mask: BigInt, set: Boolean = true, pc: BigInt = 0) =
        Cmd((BigInt(3) << 30) | (BigInt(4) << 26) | (BigInt(if (set) 1 else 0) << 25) |
            (BigInt(1) << 15) | (BigInt(group) << 7) | (BigInt(3) << 12) | 0x0b, mask, 0, pc)

    private def signalTimeline(id: Int, value: BigInt, inc: Boolean = false, pc: BigInt = 0) =
        Cmd((BigInt(3) << 30) | (BigInt(3) << 26) | (BigInt(if (inc) 0 else 1) << 15) |
            (BigInt(id) << 7) | (BigInt(3) << 12) | 0x0b, value, 0, pc)

    private def fence(ep: Int, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(1) << 15) | (BigInt(3) << 12) | 0x0b, 0, 0, pc)

    private def waitEvent(ep: Int, group: Int, mask: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(1) << 26) | (BigInt(1) << 15) |
            (BigInt(group) << 7) | (BigInt(3) << 12) | 0x0b, mask, 0, pc)

    private case class Native(op: Int, reg0: Int, reg1: Int, data0: BigInt, data1: BigInt, swTag: BigInt)

    private case class Comp(enable: Boolean = true, isTimeline: Boolean = false, group: Int = 0,
                            mask: BigInt = 0, timeline: Int = 0, error: Boolean = false,
                            code: Int = 0, swTag: BigInt = 0, hwTag: BigInt = 0)


    private def expected(c: Cmd): Native =
        if (c.isRun) Native(Run, 0, 0, 0, 0, c.rs2)
        else if (c.funct3 == 2) Native(Read, ((c.inst >> 20) & 1023).toInt, 0, 0, 0, 0)
        else if (c.funct3 == 0) Native(Write2, c.base, c.base + 1, c.rs1, c.rs2, 0)
        else Native(Write1, ((c.inst >> 20) & 1023).toInt, 0, c.rs1, 0, 0)

    private class Driver(dut: QueenTop, cfg: QueenConfig) {
        private val lanes = cfg.instructionLanes
        private val width = cfg.nativeWidth
        var gemmBeats = Vector.empty[Vector[Native]]
        var dmaBeats = Vector.empty[Vector[Native]]
        var traps = Vector.empty[(BigInt, BigInt, Int)]
        var asyncWrites = Vector.empty[(Int, BigInt)]
        var gemmTraces = Vector.empty[(BigInt, BigInt)]
        var dmaTraces = Vector.empty[(BigInt, BigInt)]
        var schedTraces = Vector.empty[(BigInt, BigInt)]
        var asyncReady = true

        dut.io.inst.foreach { p => p.valid.poke(false.B); p.bits.inst.poke(0.U); p.bits.pc.poke(0.U) }
        dut.io.regRead.foreach { p => p.valid.poke(false.B); p.bits.poke(0.U) }
        dut.io.gemm.ready.poke(false.B)
        dut.io.dma.ready.poke(false.B)
        for (c <- Seq(dut.io.gemmCompletion, dut.io.dmaCompletion, dut.io.vpuCompletion)) {
            c.valid.poke(false.B)
            c.bits.swTag.poke(0.U); c.bits.hwTag.poke(0.U)
            c.bits.error.poke(false.B); c.bits.code.poke(0.U)
            c.bits.hwSignal.enable.poke(false.B)
            c.bits.hwSignal.isTimeline.poke(false.B)
            c.bits.hwSignal.group.poke(0.U)
            c.bits.hwSignal.mask.poke(0.U)
            c.bits.hwSignal.timeline.poke(0.U)
        }
        dut.io.gemmWrErr.foreach(_.poke(false.B))
        dut.io.dmaWrErr.foreach(_.poke(false.B))
        dut.io.gemmDeviceIdle.poke(true.B)
        dut.io.dmaDeviceIdle.poke(true.B)
        for (r <- Seq(dut.io.gemmResp, dut.io.dmaResp)) {
            r.valid.poke(false.B); r.bits.data.poke(0.U); r.bits.err.poke(false.B)
        }
        dut.io.asyncRd.ready.poke(true.B)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        private def readBeat(port: Vec[Valid[QueenNativeCmd]]): Vector[Native] =
            port.filter(_.valid.peek().litToBoolean).map { l =>
                Native(l.bits.op.peek().litValue.toInt,
                    l.bits.reg0Addr.peek().litValue.toInt,
                    l.bits.reg1Addr.peek().litValue.toInt,
                    l.bits.data0.peek().litValue,
                    l.bits.data1.peek().litValue,
                    l.bits.swTag.peek().litValue)
            }.toVector

        /** 提供一整包，返回各 lane 是否被接受；trap 与 inst.valid 同拍，故在 step 前采样。 */
        def offer(commands: (Int, Cmd)*): Vector[Boolean] = {
            val packet = commands.foldLeft(Vector.fill[Option[Cmd]](lanes)(None)) {
                case (v, (i, c)) => v.updated(i, Some(c))
            }
            for (i <- 0 until lanes) {
                val c = packet(i)
                dut.io.inst(i).valid.poke(c.nonEmpty.B)
                dut.io.inst(i).bits.inst.poke(c.map(_.inst).getOrElse(BigInt(0)).U)
                dut.io.inst(i).bits.pc.poke(c.map(_.pc).getOrElse(BigInt(0)).U)
            }
            val accepted = Vector.tabulate(lanes) { i =>
                packet(i).nonEmpty && dut.io.inst(i).ready.peek().litToBoolean
            }
            if (dut.io.trap.valid.peek().litToBoolean) {
                traps = traps :+ ((dut.io.trap.bits.pc.peek().litValue,
                    dut.io.trap.bits.inst.peek().litValue,
                    dut.io.trap.bits.cause.peek().litValue.toInt))
            }
            dut.clock.step()
            // 次拍送操作数，只与本拍已 fire 的 lane 配对。
            for (i <- 0 until lanes) {
                val c = if (accepted(i)) packet(i) else None
                dut.io.inst(i).valid.poke(false.B)
                dut.io.regRead(2 * i).valid.poke(c.nonEmpty.B)
                dut.io.regRead(2 * i).bits.poke(c.map(_.rs1).getOrElse(BigInt(0)).U)
                dut.io.regRead(2 * i + 1).valid.poke(c.nonEmpty.B)
                dut.io.regRead(2 * i + 1).bits.poke(c.map(_.rs2).getOrElse(BigInt(0)).U)
            }
            accepted
        }

        def step(gemmReady: Boolean = true, dmaReady: Boolean = true): Unit = {
            dut.io.gemm.ready.poke(gemmReady.B)
            dut.io.dma.ready.poke(dmaReady.B)
            dut.io.asyncRd.ready.poke(asyncReady.B)
            if (dut.io.trap.valid.peek().litToBoolean) {
                traps = traps :+ ((dut.io.trap.bits.pc.peek().litValue,
                    dut.io.trap.bits.inst.peek().litValue,
                    dut.io.trap.bits.cause.peek().litValue.toInt))
            }
            if (asyncReady && dut.io.asyncRd.valid.peek().litToBoolean) {
                asyncWrites = asyncWrites :+ ((dut.io.asyncRd.bits.addr.peek().litValue.toInt,
                    dut.io.asyncRd.bits.data.peek().litValue))
            }
            if (gemmReady && dut.io.gemm.valid.peek().litToBoolean) {
                gemmBeats = gemmBeats :+ readBeat(dut.io.gemm.bits)
            }
            if (dmaReady && dut.io.dma.valid.peek().litToBoolean) {
                dmaBeats = dmaBeats :+ readBeat(dut.io.dma.bits)
            }
            if (dut.io.gemmTrace.valid.peek().litToBoolean) {
                gemmTraces = gemmTraces :+ ((dut.io.gemmTrace.bits.swTag.peek().litValue,
                    dut.io.gemmTrace.bits.hwTag.peek().litValue))
            }
            if (dut.io.dmaTrace.valid.peek().litToBoolean) {
                dmaTraces = dmaTraces :+ ((dut.io.dmaTrace.bits.swTag.peek().litValue,
                    dut.io.dmaTrace.bits.hwTag.peek().litValue))
            }
            if (dut.io.schedTrace.valid.peek().litToBoolean) {
                schedTraces = schedTraces :+ ((dut.io.schedTrace.bits.swTag.peek().litValue,
                    dut.io.schedTrace.bits.hwTag.peek().litValue))
            }
            dut.clock.step()
        }

        def settle(cycles: Int = 12): Unit = for (_ <- 0 until cycles) step()

        private def pokeCompletion(port: Valid[QueenDsaCompletion], c: Option[Comp]): Unit = {
            port.valid.poke(c.nonEmpty.B)
            port.bits.swTag.poke(c.map(_.swTag).getOrElse(BigInt(0)).U)
            port.bits.hwTag.poke(c.map(_.hwTag).getOrElse(BigInt(0)).U)
            port.bits.error.poke(c.exists(_.error).B)
            port.bits.code.poke(c.map(_.code).getOrElse(0).U)
            port.bits.hwSignal.enable.poke(c.exists(_.enable).B)
            port.bits.hwSignal.isTimeline.poke(c.exists(_.isTimeline).B)
            port.bits.hwSignal.group.poke(c.map(_.group).getOrElse(0).U)
            port.bits.hwSignal.mask.poke(c.map(_.mask).getOrElse(BigInt(0)).U)
            port.bits.hwSignal.timeline.poke(c.map(_.timeline).getOrElse(0).U)
        }

        /** completion 只有 valid：Queen 无条件接收，故这里给一拍脉冲。 */
        def complete(gemm: Option[Comp] = None, dma: Option[Comp] = None,
                     vpu: Option[Comp] = None): Unit = {
            pokeCompletion(dut.io.gemmCompletion, gemm)
            pokeCompletion(dut.io.dmaCompletion, dma)
            pokeCompletion(dut.io.vpuCompletion, vpu)
            step()
            pokeCompletion(dut.io.gemmCompletion, None)
            pokeCompletion(dut.io.dmaCompletion, None)
            pokeCompletion(dut.io.vpuCompletion, None)
        }

        def devices(gemmIdle: Boolean, dmaIdle: Boolean): Unit = {
            dut.io.gemmDeviceIdle.poke(gemmIdle.B)
            dut.io.dmaDeviceIdle.poke(dmaIdle.B)
        }

        /** 设备读响应：Queen 侧 ready 恒 1，故这里给一拍脉冲。 */
        def resp(gemm: Option[(BigInt, Boolean)] = None, dma: Option[(BigInt, Boolean)] = None): Unit = {
            for ((port, value) <- Seq(dut.io.gemmResp -> gemm, dut.io.dmaResp -> dma)) {
                port.valid.poke(value.nonEmpty.B)
                port.bits.data.poke(value.map(_._1).getOrElse(BigInt(0)).U)
                port.bits.err.poke(value.exists(_._2).B)
            }
            step()
            for (port <- Seq(dut.io.gemmResp, dut.io.dmaResp)) {
                port.valid.poke(false.B); port.bits.data.poke(0.U); port.bits.err.poke(false.B)
            }
        }

        def events: Vector[BigInt] = dut.io.events.map(_.peek().litValue).toVector
        def timelines: Vector[BigInt] = dut.io.timelines.map(_.peek().litValue).toVector
    }

    behavior of "QueenTop"

    it should "route a four-write GEMM packet to one native beat and leave DMA idle" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = (0 until 4).map(i => qwr1(Gemm, i, 0x100 + i, pc = 0x10 + i))
            d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)
            d.settle()
            d.gemmBeats shouldBe Vector(cmds.map(expected).toVector)
            d.dmaBeats shouldBe empty
            d.traps shouldBe empty
        }
    }

    it should "split a mixed packet per endpoint and isolate each RUN" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val g0 = qwr2(Gemm, 4, 0xaa, 0xbb, pc = 0x20)
            val d0 = qwr1(Dma, 0x30, 0xcc, pc = 0x24)
            val g1 = run(Gemm, 0x11, 0x1234, pc = 0x28)
            d.offer(0 -> g0, 1 -> d0, 3 -> g1)
            d.settle()
            d.gemmBeats shouldBe Vector(Vector(expected(g0)), Vector(expected(g1)))
            d.dmaBeats shouldBe Vector(Vector(expected(d0)))
            d.traps shouldBe empty
        }
    }

    it should "keep QWR2 as one two-address write end to end" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val c = qwr2(Dma, 62, BigInt("aaaa0001", 16), BigInt("bbbb0002", 16), pc = 0x40)
            d.offer(2 -> c)
            d.settle()
            d.dmaBeats shouldBe Vector(Vector(
                Native(Write2, 62, 63, BigInt("aaaa0001", 16), BigInt("bbbb0002", 16), 0)))
            d.gemmBeats shouldBe empty
        }
    }

    it should "execute a SCHED SIGNAL_EVENT without touching GEMM/DMA" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(1 -> signalEvent(5, 0x88, pc = 0x60))
            d.settle()
            d.events(5) shouldBe BigInt(0x88)
            d.gemmBeats shouldBe empty
            d.dmaBeats shouldBe empty
            d.traps shouldBe empty
        }
    }

    it should "clear an event group with SIGNAL_EVENT" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> signalEvent(5, 0xff))
            d.settle()
            d.events(5) shouldBe BigInt(0xff)
            d.offer(0 -> signalEvent(5, 0x0f, set = false))
            d.settle()
            d.events(5) shouldBe BigInt(0xf0)
        }
    }

    it should "assign and increment a timeline from SCHED and reject a rollback" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> signalTimeline(2, 100))
            d.settle()
            d.timelines(2) shouldBe BigInt(100)
            d.offer(0 -> signalTimeline(2, 0, inc = true))
            d.settle()
            d.timelines(2) shouldBe BigInt(101)
            d.offer(0 -> signalTimeline(2, 50))
            d.settle()
            d.timelines(2) shouldBe BigInt(101)
            dut.io.faultValid.expect(true.B)
            dut.io.faultKind.expect(2.U)
            dut.io.faultEndpoint.expect(3.U)
            dut.io.faultCode.expect(2.U)
        }
    }

    it should "trap a non-zero bit25 on every QCTRL except SIGNAL_EVENT" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val withBit25 = Cmd(fence(Gemm).inst | (BigInt(1) << 25))
            d.offer(0 -> withBit25)
            d.settle()
            d.traps.map(_._3) shouldBe Vector(7)
            d.gemmBeats shouldBe empty
        }
    }

    it should "report an empty-group RUN as a trap and emit nothing" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> run(Gemm, 1, pc = 0x80))
            d.settle()
            d.traps.map(_._3) shouldBe Vector(8)
            d.traps.head._1 shouldBe BigInt(0x80)
            d.gemmBeats shouldBe empty
        }
    }

    it should "hold the GEMM beat under backpressure while DMA keeps flowing" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val g = qwr1(Gemm, 1, 0x77, pc = 0x90)
            val m = qwr1(Dma, 2, 0x88, pc = 0x94)
            d.offer(0 -> g, 1 -> m)
            for (_ <- 0 until 4) d.step(gemmReady = false)
            d.gemmBeats shouldBe empty
            d.dmaBeats shouldBe Vector(Vector(expected(m)))
            d.settle()
            d.gemmBeats shouldBe Vector(Vector(expected(g)))
        }
    }

    it should "honor a narrower native width by splitting write beats" in {
        val cfg = QueenConfig(nativeLanes = 2)
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = (0 until 4).map(i => qwr1(Gemm, i, 0x10 + i))
            d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)
            d.settle()
            d.gemmBeats.map(_.size) shouldBe Vector(2, 2)
            d.gemmBeats.flatten shouldBe cmds.map(expected)
        }
    }

    it should "set an event group from a completion and ignore a disabled signal" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(gemm = Some(Comp(group = 5, mask = 0x88)))
            d.events(5) shouldBe BigInt(0x88)
            d.events.count(_ != 0) shouldBe 1
            d.complete(gemm = Some(Comp(group = 5, mask = 0x11)))
            d.events(5) shouldBe BigInt(0x99)
            d.complete(dma = Some(Comp(enable = false, group = 7, mask = 0xff)))
            d.events(7) shouldBe BigInt(0)
            d.complete(gemm = Some(Comp(group = 5, mask = 0)))
            d.events(5) shouldBe BigInt(0x99)
        }
    }

    it should "merge two same-cycle event completions across and within a group" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(gemm = Some(Comp(group = 5, mask = 0x0f)),
                dma = Some(Comp(group = 5, mask = 0xf0)))
            d.events(5) shouldBe BigInt(0xff)
            d.complete(gemm = Some(Comp(group = 1, mask = 0x1)),
                dma = Some(Comp(group = 2, mask = 0x2)))
            d.events(1) shouldBe BigInt(1)
            d.events(2) shouldBe BigInt(2)
        }
    }

    it should "absorb a VPU completion for signal and fault without a VPU command port" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(vpu = Some(Comp(group = 9, mask = 0x21)))
            d.events(9) shouldBe BigInt(0x21)
            d.complete(gemm = Some(Comp(isTimeline = true, timeline = 4)),
                dma = Some(Comp(isTimeline = true, timeline = 4)),
                vpu = Some(Comp(isTimeline = true, timeline = 4)))
            d.timelines(4) shouldBe BigInt(3)
            d.complete(vpu = Some(Comp(error = true, code = 0x5a)))
            dut.io.faultValid.expect(true.B)
            dut.io.faultKind.expect(0.U)
            dut.io.faultEndpoint.expect(2.U)
            dut.io.faultCode.expect(0x5a.U)
        }
    }

    it should "emit one trace record per endpoint at the dispatch point, never to the device" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> traceTag(Gemm, 0x1234, pc = 0x900),
                1 -> traceTag(Dma, 0x5678, pc = 0x904),
                2 -> traceTag(Sched, 0x9abc, pc = 0x908))
            d.settle()
            d.gemmTraces shouldBe Vector((BigInt(0x1234), BigInt(0)))
            d.dmaTraces shouldBe Vector((BigInt(0x5678), BigInt(0)))
            d.schedTraces shouldBe Vector((BigInt(0x9abc), BigInt(0)))
            // TRACE_TAG 不进 adapter：设备侧一条命令都不该看到。
            d.gemmBeats shouldBe empty
            d.dmaBeats shouldBe empty
            d.traps shouldBe empty
            dut.io.idle.expect(true.B)
        }
    }

    it should "count timelines additively when both endpoints complete in one cycle" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(gemm = Some(Comp(isTimeline = true, timeline = 2)))
            d.timelines(2) shouldBe BigInt(1)
            d.complete(gemm = Some(Comp(isTimeline = true, timeline = 2)),
                dma = Some(Comp(isTimeline = true, timeline = 2)))
            d.timelines(2) shouldBe BigInt(3)
            d.timelines.count(_ != 0) shouldBe 1
            // 时间线模式下 mask/group 不参与事件置位。
            d.complete(gemm = Some(Comp(isTimeline = true, timeline = 1, group = 5, mask = 0xff)))
            d.timelines(1) shouldBe BigInt(1)
            d.events(5) shouldBe BigInt(0)
        }
    }

    it should "keep only the latest fault and raise irq until cleared" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            dut.io.faultValid.expect(false.B)
            dut.io.faultIrq.expect(false.B)
            d.complete(gemm = Some(Comp(error = true, code = 0x11)))
            dut.io.faultValid.expect(true.B)
            dut.io.faultIrq.expect(true.B)
            dut.io.faultEndpoint.expect(Gemm.U)
            dut.io.faultCode.expect(0x11.U)
            d.complete(dma = Some(Comp(error = true, code = 0x22)))
            dut.io.faultEndpoint.expect(Dma.U)
            dut.io.faultCode.expect(0x22.U)
            // 无错完成不清故障。
            d.complete(gemm = Some(Comp(group = 1, mask = 1)))
            dut.io.faultValid.expect(true.B)
            // 清除只能由软件写 SCHED 0x00 触发，Queen 没有独立清除端口。
            d.offer(0 -> qwr1(Sched, 0x00, 0, pc = 0xc0))
            d.settle()
            dut.io.faultValid.expect(false.B)
            dut.io.faultIrq.expect(false.B)
        }
    }

    it should "pick the lower endpoint index when both report an error in one cycle" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(gemm = Some(Comp(error = true, code = 0x31)),
                dma = Some(Comp(error = true, code = 0x32)))
            dut.io.faultEndpoint.expect(Gemm.U)
            dut.io.faultCode.expect(0x31.U)
        }
    }

    it should "let a new error win over a same-cycle software fault clear" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(gemm = Some(Comp(error = true, code = 0x41)))
            d.offer(0 -> qwr1(Sched, 0x00, 0, pc = 0xc4))
            // accept → 前端整拍 → SCHED 暂存出队，第四拍才执行写。
            d.step()
            d.step()
            dut.io.faultValid.expect(true.B)
            d.complete(dma = Some(Comp(error = true, code = 0x42)))
            dut.io.faultValid.expect(true.B)
            dut.io.faultEndpoint.expect(Dma.U)
            dut.io.faultCode.expect(0x42.U)
            d.settle()
            dut.io.faultValid.expect(true.B)
        }
    }

    it should "assert idle only when the frontend, both adapters and both devices are idle" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.settle(2)
            dut.io.idle.expect(true.B)
            d.devices(gemmIdle = false, dmaIdle = true)
            d.step()
            dut.io.idle.expect(false.B)
            d.devices(gemmIdle = true, dmaIdle = true)
            d.step()
            dut.io.idle.expect(true.B)

            d.offer(0 -> qwr1(Gemm, 1, 0x77, pc = 0xa0))
            dut.io.idle.expect(false.B)
            d.step(gemmReady = false)
            dut.io.idle.expect(false.B)
            d.settle()
            dut.io.idle.expect(true.B)
        }
    }

    it should "latch an illegal device write as a kind-1 fault" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            dut.io.dmaWrErr(1).poke(true.B)
            d.step()
            dut.io.dmaWrErr(1).poke(false.B)
            dut.io.faultValid.expect(true.B)
            dut.io.faultKind.expect(1.U)
            dut.io.faultEndpoint.expect(Dma.U)
            dut.io.faultCode.expect(0.U)
            dut.io.irq.expect(true.B)
        }
    }

    it should "prefer a completion error over a same-cycle write error" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            dut.io.gemmWrErr(0).poke(true.B)
            dut.io.dmaWrErr(0).poke(true.B)
            d.complete(gemm = Some(Comp(error = true, code = 0x55)))
            dut.io.gemmWrErr(0).poke(false.B)
            dut.io.dmaWrErr(0).poke(false.B)
            dut.io.faultKind.expect(0.U)
            dut.io.faultEndpoint.expect(Gemm.U)
            dut.io.faultCode.expect(0x55.U)
        }
    }

    it should "hold a GEMM write behind WAIT_EVENT until a SCHED signal satisfies it" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val w = qwr1(Gemm, 3, 0x77, pc = 0xb0)
            d.offer(0 -> waitEvent(Gemm, 5, 0x88, pc = 0xb4), 1 -> w)
            d.settle()
            d.gemmBeats shouldBe empty
            dut.io.idle.expect(false.B)
            d.offer(2 -> signalEvent(5, 0x88, pc = 0xb8))
            d.settle()
            d.events(5) shouldBe BigInt(0x88)
            d.gemmBeats shouldBe Vector(Vector(expected(w)))
            d.traps shouldBe empty
            dut.io.idle.expect(true.B)
        }
    }

    it should "write back cross-endpoint QRDs in program order when responses arrive out of order" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val g = qrd(Gemm, 0x10, 3, pc = 0x100)
            val s = qrd(Sched, 0x40, 4, pc = 0x104)
            d.offer(0 -> g, 1 -> s)
            d.settle(8)
            d.gemmBeats shouldBe Vector(Vector(expected(g)))
            // SCHED 读已回，但队头是 GEMM，不得提前写回。
            d.asyncWrites shouldBe empty
            d.resp(gemm = Some((BigInt(0xdead), false)))
            d.step()
            d.step()
            d.asyncWrites shouldBe Vector((3, BigInt(0xdead)), (4, BigInt(0)))
            d.traps shouldBe empty
            dut.io.idle.expect(true.B)
        }
    }

    it should "pop a rd=x0 QRD without a register writeback or any CPU notification" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> qrd(Sched, 0x6c, 0, pc = 0x110))
            d.settle()
            d.asyncWrites shouldBe empty
            d.traps shouldBe empty
            dut.io.faultValid.expect(false.B)
            dut.io.idle.expect(true.B)
        }
    }

    it should "turn a read error into a kind-4 fault without a writeback or a trap" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> qrd(Gemm, 0x10, 5, pc = 0x120))
            d.settle(6)
            d.asyncWrites shouldBe empty
            d.resp(gemm = Some((BigInt(0), true)))
            d.step()
            d.asyncWrites shouldBe empty
            d.traps shouldBe empty
            dut.io.faultValid.expect(true.B)
            dut.io.faultKind.expect(4.U)
            dut.io.faultEndpoint.expect(Gemm.U)
            dut.io.faultCode.expect(0.U)
            dut.io.idle.expect(true.B)
        }
    }

    it should "hold the QRD head while the CPU writeback port is backpressured" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> qrd(Gemm, 0x10, 3, pc = 0x130), 1 -> qrd(Gemm, 0x11, 4, pc = 0x134))
            d.settle(6)
            d.asyncReady = false
            d.resp(gemm = Some((BigInt(0xaa), false)))
            d.resp(gemm = Some((BigInt(0xbb), false)))
            d.step()
            d.step()
            d.asyncWrites shouldBe empty
            d.asyncReady = true
            d.step()
            d.step()
            d.asyncWrites shouldBe Vector((3, BigInt(0xaa)), (4, BigInt(0xbb)))
        }
    }

    it should "latch an illegal SCHED register write as a kind-1 endpoint-3 fault" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.offer(0 -> qwr1(Sched, 0x40, 0, pc = 0x140))
            d.settle()
            dut.io.faultValid.expect(true.B)
            dut.io.faultKind.expect(1.U)
            dut.io.faultEndpoint.expect(Sched.U)
            dut.io.faultCode.expect(0.U)
        }
    }

    it should "let software read the fault records back through QRD" in {
        val cfg = QueenConfig()
        simulate(new QueenTop(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.complete(gemm = Some(Comp(error = true, code = 0x77)))
            d.offer(0 -> qrd(Sched, 0x68, 1, pc = 0x150))
            d.settle()
            d.offer(0 -> qrd(Sched, 0x6b, 2, pc = 0x154))
            d.settle()
            d.asyncWrites shouldBe Vector((1, BigInt(1)), (2, BigInt(0x77)))
        }
    }
}