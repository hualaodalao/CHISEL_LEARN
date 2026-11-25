import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class QueenDsaRegIfSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private val Write1 = 0
    private val Write2 = 1
    private val Read = 2
    private val Run = 3

    private val IntrRaw = 0x60
    private val IntrForce = 0x64
    private val IntrMask = 0x68
    private val IntrStatus = 0x6C
    private val SigTarget = 0x80
    private val SigMask = 0x81
    private val TraceTag = 0xA0

    private case class NC(op: Int, reg0: Int, reg1: Int = 0, data0: BigInt = 0,
                          data1: BigInt = 0, swTag: BigInt = 0)

    private def wr1(a: Int, d: BigInt) = NC(Write1, a, 0, d)
    private def wr2(a: Int, d0: BigInt, d1: BigInt) = NC(Write2, a, a + 1, d0, d1)
    private def rd(a: Int) = NC(Read, a)
    private def run(sw: BigInt = 0) = NC(Run, 0, 0, 0, 0, sw)

    private class Driver(dut: QueenDsaRegIf, cfg: QueenDsaRegIfConfig) {
        private val lanes = cfg.lanes
        private val intrW = cfg.intrWidth
        private val intrMaskRst = (BigInt(1) << intrW) - 1
        private val shadow = Array.fill(cfg.numCfgRegs)(BigInt(0))
        private val live = Array.fill(cfg.numCfgRegs)(BigInt(0))
        private var intrRaw = BigInt(0)
        private var intrMask = intrMaskRst
        private var sigTarget = BigInt(0)
        private var sigMask = BigInt(0)
        private var traceTag = BigInt(0)
        var accepted = 0
        var reads = Vector.empty[(Int, BigInt, Boolean)]
        var starts = Vector.empty[BigInt]
        var wrErrs = Vector.empty[Int]

        dut.io.cmd.valid.poke(false.B)
        dut.io.cmd.bits.foreach { l =>
            l.valid.poke(false.B)
            l.bits.op.poke(QueenNativeOp.WRITE1)
            l.bits.reg0Addr.poke(0.U); l.bits.reg1Addr.poke(0.U)
            l.bits.data0.poke(0.U); l.bits.data1.poke(0.U)
            l.bits.swTag.poke(0.U)
        }
        dut.io.resp.ready.poke(false.B)
        dut.io.startReady.poke(false.B)
        dut.io.intrSrc.poke(0.U)
        dut.io.statusRegs.foreach(_.poke(0.U))
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def beat(commands: (Int, NC)*): Vector[Option[NC]] = {
            require(commands.map(_._1).distinct.size == commands.size)
            commands.foldLeft(Vector.fill[Option[NC]](lanes)(None)) {
                case (v, (i, c)) => v.updated(i, Some(c))
            }
        }

        private def wrLegal(a: Int): Boolean =
            a < cfg.numCfgRegs || a == IntrRaw || a == IntrForce || a == IntrMask ||
                a == SigTarget || a == SigMask || a == TraceTag

        private def rdLegal(a: Int): Boolean =
            a < cfg.numCfgRegs || (a >= 0x40 && a < 0x40 + cfg.numStatusRegs) ||
                a == IntrRaw || a == IntrForce || a == IntrMask || a == IntrStatus ||
                a == SigTarget || a == SigMask || a == TraceTag

        private def readReg(a: Int, status: Vector[BigInt]): BigInt =
            if (a < cfg.numCfgRegs) shadow(a)
            else if (a >= 0x40 && a < 0x40 + cfg.numStatusRegs) status(a - 0x40)
            else a match {
                case IntrRaw | IntrForce => intrRaw
                case IntrMask => intrMask
                case IntrStatus => intrRaw & ~intrMask & intrMaskRst
                case SigTarget => sigTarget
                case SigMask => sigMask
                case TraceTag => traceTag
                case _ => BigInt(0)
            }

        private var cycleNo = 0

        def cycle(b: Vector[Option[NC]] = Vector.fill(lanes)(None),
                  respReady: Boolean = false, startReady: Boolean = false,
                  intrSrc: BigInt = 0,
                  status: Vector[BigInt] = Vector.fill(cfg.numStatusRegs)(BigInt(0))): Boolean = {
            cycleNo += 1
            val cmdValid = b.exists(_.nonEmpty)
            dut.io.cmd.valid.poke(cmdValid.B)
            for (j <- 0 until lanes) {
                val l = dut.io.cmd.bits(j)
                val c = b(j)
                l.valid.poke(c.nonEmpty.B)
                l.bits.op.poke(QueenNativeOp.all(c.map(_.op).getOrElse(0)))
                l.bits.reg0Addr.poke(c.map(_.reg0).getOrElse(0).U)
                l.bits.reg1Addr.poke(c.map(_.reg1).getOrElse(0).U)
                l.bits.data0.poke(c.map(_.data0).getOrElse(BigInt(0)).U)
                l.bits.data1.poke(c.map(_.data1).getOrElse(BigInt(0)).U)
                l.bits.swTag.poke(c.map(_.swTag).getOrElse(BigInt(0)).U)
            }
            dut.io.resp.ready.poke(respReady.B)
            dut.io.startReady.poke(startReady.B)
            dut.io.intrSrc.poke(intrSrc.U)
            for (j <- 0 until cfg.numStatusRegs) dut.io.statusRegs(j).poke(status(j).U)

            val hasRun = b.exists(_.exists(_.op == Run))
            val hasRd = b.exists(_.exists(_.op == Read))
            val expReady = if (hasRun) startReady else if (hasRd) respReady else true
            dut.io.cmd.ready.expect(expReady.B)
            val accept = cmdValid && expReady

            val wrOk = b.map(_.exists(c =>
                (c.op == Write1 || c.op == Write2) && wrLegal(c.reg0) &&
                    (c.op == Write1 || wrLegal(c.reg1))))
            for (j <- 0 until lanes) {
                val isWr = b(j).exists(c => c.op == Write1 || c.op == Write2)
                val exp = accept && isWr && !wrOk(j)
                dut.io.wrErr(j).expect(exp.B)
                if (exp) wrErrs = wrErrs :+ j
            }

            val rdLane = b.flatten.find(_.op == Read)
            dut.io.resp.valid.expect((accept && hasRd).B)
            rdLane.foreach { c =>
                val expData = readReg(c.reg0, status)
                val expErr = !rdLegal(c.reg0)
                withClue(s"cycle=$cycleNo read addr=${c.reg0} ") {
                    dut.io.resp.bits.data.expect(expData.U)
                    dut.io.resp.bits.err.expect(expErr.B)
                }
                if (accept) reads = reads :+ ((c.reg0, expData, expErr))
            }

            val runLane = b.flatten.find(_.op == Run)
            dut.io.start.expect((accept && hasRun).B)
            runLane.foreach { c =>
                val expSwTag = if (c.swTag != 0) c.swTag else traceTag
                dut.io.startSwTag.expect(expSwTag.U)
                if (accept) starts = starts :+ expSwTag
            }

            for (j <- 0 until cfg.numCfgRegs) dut.io.cfgRegs(j).expect(live(j).U)
            dut.io.irq.expect(((intrRaw & ~intrMask & intrMaskRst) != 0).B)
            dut.io.hwSignal.enable.expect(((sigTarget & 1) != 0).B)
            dut.io.hwSignal.isTimeline.expect(((sigTarget & 2) != 0).B)
            dut.io.hwSignal.group.expect(((sigTarget >> 2) & 31).U)
            dut.io.hwSignal.mask.expect(sigMask.U)
            dut.io.hwSignal.timeline.expect(((sigTarget >> 7) & 7).U)

            var clr = BigInt(0)
            var set = BigInt(0)
            if (accept) {
                accepted += 1
                def writeReg(a: Int, d: BigInt): Unit = {
                    if (a < cfg.numCfgRegs) shadow(a) = d & 0xffffffffL
                    else if (a == IntrMask) intrMask = d & intrMaskRst
                    else if (a == SigTarget) sigTarget = d & 0xffffffffL
                    else if (a == SigMask) sigMask = d & 0xffffffffL
                    else if (a == TraceTag) traceTag = d & 0xffffffffL
                    if (a == IntrRaw) clr |= d & intrMaskRst
                    if (a == IntrForce) set |= d & intrMaskRst
                }
                for (j <- 0 until lanes if wrOk(j)) {
                    val c = b(j).get
                    writeReg(c.reg0, c.data0)
                    if (c.op == Write2) writeReg(c.reg1, c.data1)
                }
                if (hasRun) {
                    Array.copy(shadow, 0, live, 0, cfg.numCfgRegs)
                }
            }
            // 中断更新不依赖命令接受，逐拍生效。
            intrRaw = ((intrRaw & ~clr) | set | (intrSrc & intrMaskRst)) & intrMaskRst
            dut.clock.step()
            accept
        }
    }

    private val small = QueenDsaRegIfConfig(numCfgRegs = 8, numStatusRegs = 4, intrWidth = 8)

    behavior of "QueenDsaRegIf"

    it should "apply four parallel writes in lane order with last-write-wins" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> wr1(0, 0x11), 1 -> wr1(1, 0x22), 2 -> wr1(0, 0x33),
                3 -> wr1(2, 0x44)), respReady = true) shouldBe true
            d.cycle(d.beat(0 -> rd(0)), respReady = true)
            d.cycle(d.beat(0 -> rd(1)), respReady = true)
            d.cycle(d.beat(0 -> rd(2)), respReady = true)
            d.reads.map(_._2) shouldBe Vector(BigInt(0x33), BigInt(0x22), BigInt(0x44))
            d.wrErrs shouldBe empty
        }
    }

    it should "write both addresses of WRITE2 atomically and reject the pair on any bad address" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> wr2(2, 0xaa, 0xbb)), respReady = true) shouldBe true
            d.cycle(d.beat(0 -> rd(2)), respReady = true)
            d.cycle(d.beat(0 -> rd(3)), respReady = true)
            d.reads.map(_._2) shouldBe Vector(BigInt(0xaa), BigInt(0xbb))
            // 0x40 是 RO 状态：整条 WRITE2 回 err，两个寄存器都不改。
            d.cycle(d.beat(0 -> wr2(0x3f, 0xcc, 0xdd)), respReady = true) shouldBe true
            d.wrErrs shouldBe Vector(0)
            d.cycle(d.beat(0 -> rd(4)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0)
        }
    }

    it should "keep writes off the resp channel and pulse wrErr per lane" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> wr1(0, 1), 1 -> wr1(0x50, 2), 2 -> wr1(1, 3)),
                respReady = false) shouldBe true
            d.wrErrs shouldBe Vector(1)
            d.reads shouldBe empty
            d.cycle(d.beat(0 -> rd(0)), respReady = true)
            d.reads.map(_._2) shouldBe Vector(BigInt(1))
        }
    }

    it should "gate READ on resp.ready and hold the response under backpressure" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> wr1(0, 0x5a)), respReady = true)
            val b = d.beat(0 -> rd(0))
            d.cycle(b, respReady = false) shouldBe false
            d.cycle(b, respReady = false) shouldBe false
            d.reads shouldBe empty
            d.cycle(b, respReady = true) shouldBe true
            d.reads shouldBe Vector((0, BigInt(0x5a), false))
        }
    }

    it should "read shadow while cfgRegs keeps the last committed live snapshot" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> wr1(0, 0x10)), respReady = true)
            d.cycle(d.beat(0 -> run()), startReady = true) shouldBe true
            d.cycle(d.beat(0 -> wr1(0, 0x20)), respReady = true)
            d.cycle(d.beat(0 -> rd(0)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0x20)
            dut.io.cfgRegs(0).expect(0x10.U)
            d.cycle(d.beat(0 -> run()), startReady = true)
            dut.io.cfgRegs(0).expect(0x20.U)
        }
    }

    it should "gate RUN on startReady and emit one start pulse with the sw tag" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            val b = d.beat(0 -> run(0xdeadbeefL))
            d.cycle(b, startReady = false) shouldBe false
            d.cycle(b, startReady = false) shouldBe false
            d.starts shouldBe empty
            d.cycle(b, startReady = true) shouldBe true
            d.starts shouldBe Vector(BigInt(0xdeadbeefL))
            d.cycle(startReady = true)
            d.starts.size shouldBe 1
        }
    }

    it should "fall back to the TRACE_TAG register when RUN rs2 is zero" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> run()), startReady = true)
            d.starts shouldBe Vector(BigInt(0))
            d.cycle(d.beat(0 -> wr1(TraceTag, 0x1234abcdL)), respReady = true)
            d.cycle(d.beat(0 -> run()), startReady = true)
            d.starts.last shouldBe BigInt(0x1234abcdL)
            // rs2 非 0 覆盖寄存器值，且不改写寄存器本身。
            d.cycle(d.beat(0 -> run(0x55L)), startReady = true)
            d.starts.last shouldBe BigInt(0x55)
            d.cycle(d.beat(0 -> run()), startReady = true)
            d.starts.last shouldBe BigInt(0x1234abcdL)
        }
    }

    it should "expose the signal target combinationally for the device to sample at start" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> wr1(SigTarget, 0x0d), 1 -> wr1(SigMask, 0x88)), respReady = true)
            dut.io.hwSignal.enable.expect(true.B)
            dut.io.hwSignal.isTimeline.expect(false.B)
            dut.io.hwSignal.group.expect(3.U)
            dut.io.hwSignal.mask.expect(0x88.U)
            d.cycle(d.beat(0 -> run()), startReady = true)
            dut.io.hwSignal.group.expect(3.U)
            d.cycle(d.beat(0 -> wr1(SigTarget, 0x283)), respReady = true)
            // 目标改写立刻可见：锁存是设备的责任，不是 RegIf 的。
            dut.io.hwSignal.isTimeline.expect(true.B)
            dut.io.hwSignal.timeline.expect(5.U)
            d.cycle(d.beat(0 -> run()), startReady = true)
            dut.io.hwSignal.timeline.expect(5.U)
        }
    }

    it should "implement RAW/FORCE/MASK/STATUS with set priority and default masking" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            dut.io.irq.expect(false.B)
            d.cycle(intrSrc = 0x05)
            dut.io.irq.expect(false.B)
            d.cycle(d.beat(0 -> rd(IntrStatus)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0)
            d.cycle(d.beat(0 -> wr1(IntrMask, 0xf0)), respReady = true)
            d.cycle(intrSrc = 0x00)
            dut.io.irq.expect(true.B)
            d.cycle(d.beat(0 -> rd(IntrRaw)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0x05)
            // FORCE 置位与 RAW 写 1 清除同拍：置位优先。
            d.cycle(d.beat(0 -> wr1(IntrForce, 0x02), 1 -> wr1(IntrRaw, 0x07)), respReady = true)
            d.cycle(d.beat(0 -> rd(IntrRaw)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0x02)
            d.cycle(d.beat(0 -> wr1(IntrRaw, 0x02)), respReady = true)
            d.cycle(intrSrc = 0x00)
            dut.io.irq.expect(false.B)
        }
    }

    it should "merge W1C and W1S across lanes instead of last-write-wins" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(intrSrc = 0x0f)
            d.cycle(d.beat(0 -> wr1(IntrRaw, 0x01), 1 -> wr1(IntrRaw, 0x02),
                2 -> wr1(IntrForce, 0x10), 3 -> wr1(IntrForce, 0x20)), respReady = true)
            d.cycle(d.beat(0 -> rd(IntrRaw)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0x3c)
        }
    }

    it should "return err on reserved addresses and zero on unimplemented window slots" in {
        simulate(new QueenDsaRegIf(small)) { dut =>
            val d = new Driver(dut, small)
            d.cycle(d.beat(0 -> rd(0x20)), respReady = true)
            d.reads.last shouldBe ((0x20, BigInt(0), true))
            d.cycle(d.beat(0 -> rd(0x44)), respReady = true)
            d.reads.last shouldBe ((0x44, BigInt(0), true))
            d.cycle(d.beat(0 -> rd(0x41)), respReady = true,
                status = Vector(BigInt(1), BigInt(0x77), BigInt(2), BigInt(3)))
            d.reads.last shouldBe ((0x41, BigInt(0x77), false))
            d.cycle(d.beat(0 -> wr1(0x20, 5)), respReady = true)
            d.wrErrs shouldBe Vector(0)
            // 块 3 已整块保留：错误由中断 RAW + 状态寄存器承载，不再另立 ERR_* 寄存器。
            for (a <- Vector(0xe0, 0xe1, 0xe4)) {
                d.cycle(d.beat(0 -> rd(a)), respReady = true)
                d.reads.last shouldBe ((a, BigInt(0), true))
            }
        }
    }

    it should "reject a beat that mixes a write with a RUN" in {
        val ex = intercept[Exception] {
            simulate(new QueenDsaRegIf(small)) { dut =>
                val d = new Driver(dut, small)
                d.cycle(d.beat(0 -> wr1(0, 1), 1 -> run()), startReady = true)
            }
        }
        ex.getClass.getName should include("AssertionFailed")
    }

    it should "support the full 64-register window and 32 status inputs" in {
        val cfg = QueenDsaRegIfConfig()
        simulate(new QueenDsaRegIf(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.beat(0 -> wr2(62, 0x62, 0x63)), respReady = true)
            d.cycle(d.beat(0 -> rd(63)), respReady = true)
            d.reads.last._2 shouldBe BigInt(0x63)
            d.cycle(d.beat(0 -> run()), startReady = true)
            dut.io.cfgRegs(63).expect(0x63.U)
            d.cycle(d.beat(0 -> rd(0x5f)), respReady = true,
                status = Vector.tabulate(32)(i => BigInt(i + 1)))
            d.reads.last._2 shouldBe BigInt(32)
        }
    }

    it should "match a software register model under random beats" in {
        val cfg = QueenDsaRegIfConfig(numCfgRegs = 16, numStatusRegs = 8, intrWidth = 16)
        simulate(new QueenDsaRegIf(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val rng = new Random(20261117)
            for (_ <- 0 until 400) {
                val kind = rng.nextInt(4)
                val b = kind match {
                    case 0 => d.beat(0 -> run(rng.nextInt(1 << 30)))
                    case 1 => d.beat(0 -> rd(rng.nextInt(0x100)))
                    case _ =>
                        val n = 1 + rng.nextInt(cfg.lanes)
                        d.beat((0 until n).map { j =>
                            val a = rng.nextInt(0x100)
                            j -> (if (rng.nextBoolean()) wr1(a, rng.nextInt(1 << 30))
                                  else wr2(a, rng.nextInt(1 << 30), rng.nextInt(1 << 30)))
                        }: _*)
                }
                d.cycle(b, respReady = rng.nextBoolean(), startReady = rng.nextBoolean(),
                    intrSrc = rng.nextInt(1 << 16),
                    status = Vector.tabulate(cfg.numStatusRegs)(_ => BigInt(rng.nextInt(1 << 30))))
            }
            d.accepted should be > 0
            d.reads.nonEmpty shouldBe true
        }
    }

    it should "reject out-of-range register counts" in {
        an[IllegalArgumentException] should be thrownBy QueenDsaRegIfConfig(numCfgRegs = 65)
        an[IllegalArgumentException] should be thrownBy QueenDsaRegIfConfig(numCfgRegs = 0)
        an[IllegalArgumentException] should be thrownBy QueenDsaRegIfConfig(numStatusRegs = 33)
        an[IllegalArgumentException] should be thrownBy QueenDsaRegIfConfig(intrWidth = 0)
    }
}