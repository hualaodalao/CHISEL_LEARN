import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** QueenTop 直接对接两个 QueenDsaRegIf 与一个假 DSA：整条闭环验证配置通路契约
  * （指令 → native beat → 设备寄存器 → start → 设备采样 → completion → 同步/故障/idle）。
  * 假 DSA 只做「start 当拍采样上下文、延迟若干拍回传」，真实设备集成属设备侧工作。
  */
private class QueenFakeDsa(cfg: QueenConfig, latency: Int) extends Module {
    require(latency >= 1)
    val io = IO(new Bundle {
        val start = Input(Bool())
        val pc = Input(UInt(cfg.pcWidth.W))
        val tag = Input(UInt(8.W))
        val swTag = Input(UInt(cfg.xlen.W))
        val hw = Input(new QueenHwSignal)
        val faultEn = Input(Bool())
        val faultCode = Input(UInt(cfg.faultCodeWidth.W))
        val completion = Valid(new QueenDsaCompletion(cfg))
        val idle = Output(Bool())
    })

    private val stage = RegInit(VecInit(Seq.fill(latency)(
        0.U.asTypeOf(Valid(new QueenDsaCompletion(cfg))))))
    private val entry = Wire(Valid(new QueenDsaCompletion(cfg)))
    entry.valid := io.start
    entry.bits.pc := io.pc
    entry.bits.cmdTag := io.tag
    entry.bits.swTag := io.swTag
    entry.bits.error := io.faultEn
    entry.bits.code := io.faultCode
    entry.bits.hwSignal := io.hw
    stage(0) := entry
    for (i <- 1 until latency) stage(i) := stage(i - 1)
    io.completion := stage(latency - 1)
    io.idle := !stage.map(_.valid).reduce(_ || _)
}

private class QueenConfigPathDut(cfg: QueenConfig, rcfg: QueenDsaRegIfConfig,
                                 latency: Int = 3) extends Module {
    val io = IO(new Bundle {
        val inst = Vec(cfg.instructionLanes, Flipped(Decoupled(new QueenIssue(cfg))))
        val rs1 = Vec(cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val rs2 = Vec(cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val trap = Valid(new QueenTrap(cfg))
        val asyncRd = Decoupled(new QueenAsyncRd(cfg))
        val qrdRetire = Decoupled(new QueenRetire(cfg))
        val gemmStartReady = Input(Bool())
        val dmaStartReady = Input(Bool())
        val gemmFaultEn = Input(Bool())
        val dmaFaultEn = Input(Bool())
        val gemmFaultCode = Input(UInt(cfg.faultCodeWidth.W))
        val dmaFaultCode = Input(UInt(cfg.faultCodeWidth.W))
        val gemmRegs = Output(Vec(rcfg.numCfgRegs, UInt(32.W)))
        val dmaRegs = Output(Vec(rcfg.numCfgRegs, UInt(32.W)))
        val gemmStart = Output(Bool())
        val dmaStart = Output(Bool())
        val gemmStartTag = Output(UInt(8.W))
        val dmaStartTag = Output(UInt(8.W))
        val gemmStartPc = Output(UInt(cfg.pcWidth.W))
        val dmaStartPc = Output(UInt(cfg.pcWidth.W))
        val gemmHw = Output(new QueenHwSignal)
        val dmaHw = Output(new QueenHwSignal)
        val gemmWrErr = Output(Vec(cfg.nativeWidth, Bool()))
        val dmaWrErr = Output(Vec(cfg.nativeWidth, Bool()))
        val gemmCompletion = Valid(new QueenDsaCompletion(cfg))
        val dmaCompletion = Valid(new QueenDsaCompletion(cfg))
        val idle = Output(Bool())
        val events = Output(Vec(cfg.eventGroupCount, UInt(32.W)))
        val timelines = Output(Vec(cfg.timelineCount, UInt(32.W)))
        val faultValid = Output(Bool())
        val faultKind = Output(UInt(2.W))
        val faultEndpoint = Output(UInt(2.W))
        val faultCode = Output(UInt(cfg.faultCodeWidth.W))
        val faultIrq = Output(Bool())
    })

    private val top = Module(new QueenTop(cfg))
    private val gemmReg = Module(new QueenDsaRegIf(rcfg))
    private val dmaReg = Module(new QueenDsaRegIf(rcfg))
    private val gemmDev = Module(new QueenFakeDsa(cfg, latency))
    private val dmaDev = Module(new QueenFakeDsa(cfg, latency))

    top.io.inst <> io.inst
    top.io.rs1 <> io.rs1
    top.io.rs2 <> io.rs2
    io.trap <> top.io.trap
    io.asyncRd <> top.io.asyncRd
    io.qrdRetire <> top.io.qrdRetire

    top.io.gemm <> gemmReg.io.cmd
    top.io.dma <> dmaReg.io.cmd
    gemmReg.io.startReady := io.gemmStartReady
    dmaReg.io.startReady := io.dmaStartReady
    top.io.gemmResp <> gemmReg.io.resp
    top.io.dmaResp <> dmaReg.io.resp
    gemmReg.io.statusRegs.foreach(_ := 0.U)
    dmaReg.io.statusRegs.foreach(_ := 0.U)
    gemmReg.io.intrSrc := 0.U
    dmaReg.io.intrSrc := 0.U

    private def hookDevice(dev: QueenFakeDsa, reg: QueenDsaRegIf,
                           completion: Valid[QueenDsaCompletion],
                           deviceIdle: Bool, faultEn: Bool, faultCode: UInt): Unit = {
        dev.io.start := reg.io.start
        dev.io.pc := reg.io.startPc
        dev.io.tag := reg.io.startTag
        dev.io.swTag := reg.io.startSwTag
        dev.io.hw := reg.io.hwSignal
        dev.io.faultEn := faultEn
        dev.io.faultCode := faultCode
        completion := dev.io.completion
        deviceIdle := dev.io.idle
    }

    hookDevice(gemmDev, gemmReg, top.io.gemmCompletion, top.io.gemmDeviceIdle,
        io.gemmFaultEn, io.gemmFaultCode)
    hookDevice(dmaDev, dmaReg, top.io.dmaCompletion, top.io.dmaDeviceIdle,
        io.dmaFaultEn, io.dmaFaultCode)
    top.io.gemmWrErr := gemmReg.io.wrErr
    top.io.dmaWrErr := dmaReg.io.wrErr

    io.gemmRegs := gemmReg.io.cfgRegs
    io.dmaRegs := dmaReg.io.cfgRegs
    io.gemmStart := gemmReg.io.start
    io.dmaStart := dmaReg.io.start
    io.gemmStartTag := gemmReg.io.startTag
    io.dmaStartTag := dmaReg.io.startTag
    io.gemmStartPc := gemmReg.io.startPc
    io.dmaStartPc := dmaReg.io.startPc
    io.gemmHw := gemmReg.io.hwSignal
    io.dmaHw := dmaReg.io.hwSignal
    io.gemmWrErr := gemmReg.io.wrErr
    io.dmaWrErr := dmaReg.io.wrErr
    io.gemmCompletion := top.io.gemmCompletion
    io.dmaCompletion := top.io.dmaCompletion
    io.idle := top.io.idle
    io.events := top.io.events
    io.timelines := top.io.timelines
    io.faultValid := top.io.faultValid
    io.faultKind := top.io.faultKind
    io.faultEndpoint := top.io.faultEndpoint
    io.faultCode := top.io.faultCode
    io.faultIrq := top.io.faultIrq
}

class QueenConfigPathSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private val Gemm = 0
    private val Dma = 1
    private val Sched = 3
    private val NumRegs = 8
    private val cfg = QueenConfig()
    private val rcfg = QueenDsaRegIfConfig(numCfgRegs = NumRegs, numStatusRegs = 4, intrWidth = 8,
        qcfg = cfg)

    private case class Cmd(inst: BigInt, rs1: BigInt = 0, rs2: BigInt = 0, pc: BigInt = 0)

    private def qwr2(ep: Int, base: Int, rs1: BigInt, rs2: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(base & 31) << 25) | (BigInt(2) << 20) |
            (BigInt(1) << 15) | (BigInt((base >> 5) & 1) << 11) | 0x0b, rs1, rs2, pc)

    private def qwr1(ep: Int, addr: Int, rs1: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(addr) << 20) | (BigInt(1) << 15) |
            (BigInt(1) << 12) | 0x0b, rs1, 0, pc)

    private def run(ep: Int, tag: Int, sw: BigInt = 0, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(9) << 26) | (BigInt(1) << 15) |
            (BigInt(3) << 12) | 0x0b, tag, sw, pc)

    private def waitEvent(ep: Int, group: Int, mask: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(1) << 26) | (BigInt(1) << 15) |
            (BigInt(group) << 7) | (BigInt(3) << 12) | 0x0b, mask, 0, pc)

    private def qrd(ep: Int, addr: Int, rd: Int, pc: BigInt = 0) =
        Cmd((BigInt(ep) << 30) | (BigInt(addr) << 20) | (BigInt(2) << 12) |
            (BigInt(rd) << 7) | 0x0b, 0, 0, pc)

    private def signalEvent(group: Int, mask: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(3) << 30) | (BigInt(4) << 26) | (BigInt(1) << 25) | (BigInt(1) << 15) |
            (BigInt(group) << 7) | (BigInt(3) << 12) | 0x0b, mask, 0, pc)

    private class Driver(dut: QueenConfigPathDut) {
        private val lanes = cfg.instructionLanes
        var gemmStarts = Vector.empty[(Int, BigInt)]
        var dmaStarts = Vector.empty[(Int, BigInt)]
        var wrErrCycles = 0
        var dualStartCycles = 0
        var gemmCompletions = Vector.empty[(BigInt, Int, BigInt)]
        var dmaCompletions = Vector.empty[(BigInt, Int, BigInt)]
        var retires = Vector.empty[(BigInt, Boolean, Int, Boolean, Int)]
        var asyncWrites = Vector.empty[(Int, BigInt)]

        dut.io.inst.foreach { p => p.valid.poke(false.B); p.bits.inst.poke(0.U); p.bits.pc.poke(0.U) }
        dut.io.rs1.foreach { p => p.valid.poke(false.B); p.bits.poke(0.U) }
        dut.io.rs2.foreach { p => p.valid.poke(false.B); p.bits.poke(0.U) }
        dut.io.gemmStartReady.poke(true.B)
        dut.io.dmaStartReady.poke(true.B)
        dut.io.gemmFaultEn.poke(false.B)
        dut.io.dmaFaultEn.poke(false.B)
        dut.io.gemmFaultCode.poke(0.U)
        dut.io.dmaFaultCode.poke(0.U)
        dut.io.asyncRd.ready.poke(true.B)
        dut.io.qrdRetire.ready.poke(true.B)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def offer(commands: (Int, Cmd)*): Unit = {
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
            accepted.count(identity) shouldBe packet.count(_.nonEmpty)
            dut.io.trap.valid.expect(false.B)
            dut.clock.step()
            for (i <- 0 until lanes) {
                val c = if (accepted(i)) packet(i) else None
                dut.io.inst(i).valid.poke(false.B)
                dut.io.rs1(i).valid.poke(c.nonEmpty.B)
                dut.io.rs1(i).bits.poke(c.map(_.rs1).getOrElse(BigInt(0)).U)
                dut.io.rs2(i).valid.poke(c.nonEmpty.B)
                dut.io.rs2(i).bits.poke(c.map(_.rs2).getOrElse(BigInt(0)).U)
            }
        }

        def step(gemmStartReady: Boolean = true, dmaStartReady: Boolean = true): Unit = {
            dut.io.gemmStartReady.poke(gemmStartReady.B)
            dut.io.dmaStartReady.poke(dmaStartReady.B)
            val gStart = dut.io.gemmStart.peek().litToBoolean
            val dStart = dut.io.dmaStart.peek().litToBoolean
            if (gStart) {
                gemmStarts = gemmStarts :+ ((dut.io.gemmStartTag.peek().litValue.toInt,
                    dut.io.gemmStartPc.peek().litValue))
            }
            if (dStart) {
                dmaStarts = dmaStarts :+ ((dut.io.dmaStartTag.peek().litValue.toInt,
                    dut.io.dmaStartPc.peek().litValue))
            }
            if (gStart && dStart) dualStartCycles += 1
            if (dut.io.gemmCompletion.valid.peek().litToBoolean) {
                val b = dut.io.gemmCompletion.bits
                gemmCompletions = gemmCompletions :+ ((b.pc.peek().litValue,
                    b.cmdTag.peek().litValue.toInt, b.swTag.peek().litValue))
            }
            if (dut.io.dmaCompletion.valid.peek().litToBoolean) {
                val b = dut.io.dmaCompletion.bits
                dmaCompletions = dmaCompletions :+ ((b.pc.peek().litValue,
                    b.cmdTag.peek().litValue.toInt, b.swTag.peek().litValue))
            }
            if (dut.io.gemmWrErr.exists(_.peek().litToBoolean) ||
                dut.io.dmaWrErr.exists(_.peek().litToBoolean)) wrErrCycles += 1
            if (dut.io.asyncRd.valid.peek().litToBoolean) {
                asyncWrites = asyncWrites :+ ((dut.io.asyncRd.bits.addr.peek().litValue.toInt,
                    dut.io.asyncRd.bits.data.peek().litValue))
            }
            if (dut.io.qrdRetire.valid.peek().litToBoolean) {
                val b = dut.io.qrdRetire.bits
                retires = retires :+ ((b.pc.peek().litValue, b.rdValid.peek().litToBoolean,
                    b.rdIndex.peek().litValue.toInt, b.trap.peek().litToBoolean,
                    b.trapCause.peek().litValue.toInt))
            }
            dut.clock.step()
        }

        def settle(cycles: Int = 16, gemmStartReady: Boolean = true,
                   dmaStartReady: Boolean = true): Unit =
            for (_ <- 0 until cycles) step(gemmStartReady, dmaStartReady)

        def regs(port: Vec[UInt]): Vector[BigInt] = port.map(_.peek().litValue).toVector
        def events: Vector[BigInt] = dut.io.events.map(_.peek().litValue).toVector
        def timelines: Vector[BigInt] = dut.io.timelines.map(_.peek().litValue).toVector
    }

    behavior of "Queen config path end to end"

    it should "commit configuration to the device only at RUN" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr2(Gemm, 0, 0x11, 0x22, pc = 0x100),
                1 -> qwr2(Gemm, 2, 0x33, 0x44, pc = 0x104),
                2 -> qwr1(Gemm, 4, 0x55, pc = 0x108),
                3 -> qwr1(Gemm, 5, 0x66, pc = 0x10c))
            d.settle()
            d.regs(dut.io.gemmRegs) shouldBe Vector.fill(NumRegs)(BigInt(0))
            d.gemmStarts shouldBe empty
            d.wrErrCycles shouldBe 0

            d.offer(0 -> run(Gemm, 0x21, pc = 0x110))
            d.settle()
            d.regs(dut.io.gemmRegs) shouldBe Vector(BigInt(0x11), BigInt(0x22), BigInt(0x33),
                BigInt(0x44), BigInt(0x55), BigInt(0x66), BigInt(0), BigInt(0))
            d.gemmStarts shouldBe Vector((0x21, BigInt(0x110)))
            d.dmaStarts shouldBe empty
        }
    }

    it should "keep the committed snapshot stable while the next group is prepared" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0, 0xaa, pc = 0x200))
            d.offer(0 -> run(Gemm, 1, pc = 0x204))
            d.settle()
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0xaa)

            d.offer(0 -> qwr1(Gemm, 0, 0xbb, pc = 0x208))
            d.settle()
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0xaa)
            d.gemmStarts.size shouldBe 1

            d.offer(0 -> run(Gemm, 2, pc = 0x20c))
            d.settle()
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0xbb)
            d.gemmStarts.map(_._1) shouldBe Vector(1, 2)
        }
    }

    it should "stall the RUN on startReady after the writes already landed" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0, 0x5a, pc = 0x300))
            d.settle()
            d.offer(0 -> run(Gemm, 7, pc = 0x304))
            d.settle(cycles = 10, gemmStartReady = false)
            d.gemmStarts shouldBe empty
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0)
            d.settle(gemmStartReady = true)
            d.gemmStarts shouldBe Vector((7, BigInt(0x304)))
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0x5a)
        }
    }

    it should "apply the signal target the device sampled at start" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0, 0x01, pc = 0x400),
                1 -> qwr1(Gemm, 0x80, 0x0d, pc = 0x404),
                2 -> qwr1(Gemm, 0x81, 0x88, pc = 0x408))
            d.offer(0 -> run(Gemm, 3, pc = 0x40c))
            d.settle()
            d.events(3) shouldBe BigInt(0x88)

            d.offer(0 -> qwr1(Gemm, 0x80, 0x283, pc = 0x410))
            d.settle()
            d.offer(0 -> run(Gemm, 4, pc = 0x414))
            d.settle()
            d.timelines(5) shouldBe BigInt(1)
            d.events(3) shouldBe BigInt(0x88)
        }
    }

    it should "use the target sampled at start rather than the one visible at completion" in {
        simulate(new QueenConfigPathDut(cfg, rcfg, latency = 12)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0x80, 0x0d, pc = 0x400),
                1 -> qwr1(Gemm, 0x81, 0x88, pc = 0x404))
            d.offer(0 -> run(Gemm, 3, pc = 0x408))
            var guard = 0
            while (d.gemmStarts.isEmpty && guard < 20) { d.step(); guard += 1 }
            d.gemmStarts.size shouldBe 1
            d.gemmCompletions shouldBe empty
            // 任务在飞行中改目标：completion 必须仍按 start 当拍采样的目标置位。
            d.offer(0 -> qwr1(Gemm, 0x80, 0x183, pc = 0x40c))
            d.settle(cycles = 30)
            d.gemmCompletions.size shouldBe 1
            d.events(3) shouldBe BigInt(0x88)
            d.timelines(3) shouldBe BigInt(0)
        }
    }

    it should "return the trace context the device sampled at start" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0, 0x5a, pc = 0x700))
            d.offer(0 -> run(Gemm, 0x3c, sw = BigInt("cafe0001", 16), pc = 0x704))
            d.settle()
            d.gemmCompletions shouldBe Vector((BigInt(0x704), 0x3c, BigInt("cafe0001", 16)))
            d.dmaCompletions shouldBe empty
        }
    }

    it should "record a device fault from completion and clear it by a software write" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            dut.io.faultValid.expect(false.B)
            dut.io.gemmFaultEn.poke(true.B)
            dut.io.gemmFaultCode.poke(0x2b.U)
            d.offer(0 -> qwr1(Gemm, 0, 1, pc = 0x800))
            d.offer(0 -> run(Gemm, 5, pc = 0x804))
            d.settle()
            dut.io.faultValid.expect(true.B)
            dut.io.faultIrq.expect(true.B)
            dut.io.faultEndpoint.expect(Gemm.U)
            dut.io.faultCode.expect(0x2b.U)
            dut.io.gemmFaultEn.poke(false.B)
            d.offer(0 -> qwr1(Sched, 0x00, 0, pc = 0x808))
            d.settle()
            dut.io.faultValid.expect(false.B)
            dut.io.faultIrq.expect(false.B)
        }
    }

    it should "drop idle while a task is in flight and raise it again after completion" in {
        simulate(new QueenConfigPathDut(cfg, rcfg, latency = 8)) { dut =>
            val d = new Driver(dut)
            d.settle(2)
            dut.io.idle.expect(true.B)
            d.offer(0 -> qwr1(Gemm, 0, 1, pc = 0x900))
            dut.io.idle.expect(false.B)
            d.offer(0 -> run(Gemm, 6, pc = 0x904))
            d.settle(cycles = 4)
            dut.io.idle.expect(false.B)
            d.settle(cycles = 20)
            d.gemmCompletions.size shouldBe 1
            dut.io.idle.expect(true.B)
        }
    }

    it should "drive GEMM and DMA register files independently from one packet" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr2(Gemm, 0, 0x11, 0x22, pc = 0x500),
                1 -> qwr2(Dma, 0, 0x33, 0x44, pc = 0x504))
            d.offer(0 -> run(Gemm, 0x10, pc = 0x508), 1 -> run(Dma, 0x20, pc = 0x50c))
            d.settle()
            d.regs(dut.io.gemmRegs).take(2) shouldBe Vector(BigInt(0x11), BigInt(0x22))
            d.regs(dut.io.dmaRegs).take(2) shouldBe Vector(BigInt(0x33), BigInt(0x44))
            d.gemmStarts.map(_._1) shouldBe Vector(0x10)
            d.dmaStarts.map(_._1) shouldBe Vector(0x20)
            d.wrErrCycles shouldBe 0
            // 两端点同拍 start、同拍 completion：两条都必须被吸收。
            d.dualStartCycles shouldBe 1
            d.gemmCompletions.map(_._2) shouldBe Vector(0x10)
            d.dmaCompletions.map(_._2) shouldBe Vector(0x20)
        }
    }

    it should "report an illegal device address as wrErr without disturbing the register file" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0, 0x1234, pc = 0x600),
                1 -> qwr1(Gemm, 0x40, 0xdead, pc = 0x604))
            d.settle()
            d.wrErrCycles should be > 0
            dut.io.faultValid.expect(true.B)
            dut.io.faultKind.expect(1.U)
            dut.io.faultEndpoint.expect(Gemm.U)
            d.offer(0 -> run(Gemm, 9, pc = 0x608))
            d.settle()
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0x1234)
        }
    }

    it should "close the loop from a RUN completion to a blocked WAIT_EVENT" in {
        simulate(new QueenConfigPathDut(cfg, rcfg, latency = 20)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0x80, 0x0d, pc = 0xa00),
                1 -> qwr1(Gemm, 0x81, 0x08, pc = 0xa04))
            d.offer(0 -> run(Gemm, 1, pc = 0xa08))
            // 队头 WAIT 未满足：其后的 QWR 与 RUN 一律不得派发。
            d.offer(0 -> waitEvent(Gemm, 3, 0x08, pc = 0xa0c),
                1 -> qwr1(Gemm, 0, 0xbb, pc = 0xa10),
                2 -> run(Gemm, 2, pc = 0xa14))
            d.settle(cycles = 8)
            d.gemmStarts.map(_._1) shouldBe Vector(1)
            d.events(3) shouldBe BigInt(0)
            dut.io.idle.expect(false.B)

            d.settle(cycles = 60)
            d.events(3) shouldBe BigInt(0x08)
            d.gemmStarts.map(_._1) shouldBe Vector(1, 2)
            d.regs(dut.io.gemmRegs).head shouldBe BigInt(0xbb)
            dut.io.idle.expect(true.B)
        }
    }

    it should "read a device config register back through the closed QRD loop" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qwr1(Gemm, 0, 0x5a, pc = 0xb00))
            d.settle()
            d.offer(0 -> qrd(Gemm, 0, 3, pc = 0xb04))
            d.settle()
            d.asyncWrites shouldBe Vector((3, BigInt(0x5a)))
            d.retires shouldBe Vector((BigInt(0xb04), true, 3, false, 0))
            dut.io.idle.expect(true.B)
        }
    }

    it should "not let a SCHED read overtake a GEMM read held behind a WAIT" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> waitEvent(Gemm, 3, 0x08, pc = 0xc00), 1 -> qrd(Gemm, 0, 3, pc = 0xc04))
            d.offer(0 -> qrd(Sched, 0x40, 4, pc = 0xc08))
            d.settle(cycles = 8)
            // SCHED 读已回，但队头 GEMM 读还没派发，不得提前退休。
            d.retires shouldBe empty
            d.offer(0 -> signalEvent(3, 0x08, pc = 0xc0c))
            d.settle()
            d.retires.map(_._1) shouldBe Vector(BigInt(0xc04), BigInt(0xc08))
            d.asyncWrites shouldBe Vector((3, BigInt(0)), (4, BigInt(0)))
            dut.io.idle.expect(true.B)
        }
    }

    it should "return err for a read outside the device register window" in {
        simulate(new QueenConfigPathDut(cfg, rcfg)) { dut =>
            val d = new Driver(dut)
            d.offer(0 -> qrd(Gemm, 0x300, 5, pc = 0xd00))
            d.settle()
            d.retires shouldBe Vector((BigInt(0xd00), false, 5, true, 9))
            d.asyncWrites shouldBe empty
        }
    }
}