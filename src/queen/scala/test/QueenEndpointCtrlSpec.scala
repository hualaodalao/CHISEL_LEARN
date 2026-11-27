import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class QueenEndpointCtrlSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private val Gemm = 0
    private val Mask32 = BigInt("ffffffff", 16)

    private case class Cmd(inst: BigInt, rs1: BigInt = 0, pc: BigInt = 0) {
        def funct3: Int = ((inst >> 12) & 7).toInt
        def cmd: Int = ((inst >> 26) & 15).toInt
        def aux: Int = ((inst >> 7) & 31).toInt
        def isFwd: Boolean = funct3 <= 2 || (funct3 == 3 && cmd == 9)
    }

    private def qwr1(addr: Int, rs1: BigInt, pc: BigInt = 0) =
        Cmd((BigInt(Gemm) << 30) | (BigInt(addr) << 20) | (BigInt(1) << 15) |
            (BigInt(1) << 12) | 0x0b, rs1, pc)

    private def run(tag: Int, pc: BigInt = 0) =
        Cmd((BigInt(Gemm) << 30) | (BigInt(9) << 26) | (BigInt(1) << 15) |
            (BigInt(3) << 12) | 0x0b, tag, pc)

    private def ctrl(cmd: Int, aux: Int = 0, rs1: BigInt = 0, pc: BigInt = 0) =
        Cmd((BigInt(Gemm) << 30) | (BigInt(cmd) << 26) | (BigInt(1) << 15) |
            (BigInt(aux) << 7) | (BigInt(3) << 12) | 0x0b, rs1, pc)

    private def fence(pc: BigInt = 0) = ctrl(0, pc = pc)
    private def waitEvent(group: Int, mask: BigInt, pc: BigInt = 0) = ctrl(1, group, mask, pc)
    private def waitTimeline(id: Int, target: BigInt, pc: BigInt = 0) = ctrl(2, id, target, pc)
    private def traceTag(value: BigInt, pc: BigInt = 0) = ctrl(5, rs1 = value, pc = pc)
    private def qrd(addr: Int) = Cmd((BigInt(Gemm) << 30) | (BigInt(addr) << 20) |
        (BigInt(1) << 15) | (BigInt(2) << 12) | 0x0b)

    private def s32(v: BigInt): BigInt = {
        val m = v & Mask32
        if (m >= (BigInt(1) << 31)) m - (BigInt(1) << 32) else m
    }

    private class Driver(dut: QueenEndpointCtrl, cfg: QueenConfig) {
        private val lanes = cfg.instructionLanes
        private var active = false
        private var pkt = Vector.fill[Option[Cmd]](lanes)(None)
        private var base = 0
        var forwarded = Vector.empty[Cmd]
        var beats = Vector.empty[Vector[Cmd]]
        var acceptedPackets = 0

        dut.io.in.valid.poke(false.B)
        dut.io.in.bits.foreach { l =>
            l.valid.poke(false.B); l.bits.inst.poke(0.U)
            l.bits.rs1.poke(0.U); l.bits.rs2.poke(0.U)
        }
        dut.io.out.ready.poke(false.B)
        dut.io.backendIdle.poke(true.B)
        dut.io.events.foreach(_.poke(0.U))
        dut.io.timelines.foreach(_.poke(0.U))
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def offer(commands: (Int, Cmd)*): Vector[Option[Cmd]] =
            commands.foldLeft(Vector.fill[Option[Cmd]](lanes)(None)) {
                case (v, (i, c)) => v.updated(i, Some(c))
            }

        private var cycleNo = 0

        def cycle(offered: Option[Vector[Option[Cmd]]] = None, ready: Boolean = false,
                  backendIdle: Boolean = true,
                  events: Vector[BigInt] = Vector.fill(cfg.eventGroupCount)(BigInt(0)),
                  timelines: Vector[BigInt] = Vector.fill(cfg.timelineCount)(BigInt(0))): Boolean = {
            cycleNo += 1
            dut.io.in.valid.poke(offered.nonEmpty.B)
            for (i <- 0 until lanes) {
                val l = dut.io.in.bits(i)
                val c = offered.flatMap(_(i))
                l.valid.poke(c.nonEmpty.B)
                l.bits.inst.poke(c.map(_.inst).getOrElse(BigInt(0)).U)
                l.bits.rs1.poke(c.map(_.rs1).getOrElse(BigInt(0)).U)
                l.bits.rs2.poke(0.U)
            }
            dut.io.out.ready.poke(ready.B)
            dut.io.backendIdle.poke(backendIdle.B)
            for (g <- 0 until cfg.eventGroupCount) dut.io.events(g).poke(events(g).U)
            for (t <- 0 until cfg.timelineCount) dut.io.timelines(t).poke(timelines(t).U)

            val done = active && base >= lanes
            val inReady = !active || done
            dut.io.in.ready.expect(inReady.B)
            dut.io.idle.expect((!active).B)
            val inFire = offered.nonEmpty && inReady

            val inRange = active && base < lanes
            val cand = Vector.tabulate(lanes) { j =>
                if (inRange && base + j < lanes) pkt(base + j) else None
            }
            val candCtrl = cand.map(c => c.nonEmpty && !c.get.isFwd)
            val hasCtrl = candCtrl.exists(identity)
            val firstValidIdx = cand.indexWhere(_.nonEmpty)
            val firstCtrlIdx = candCtrl.indexWhere(identity)
            val alone = hasCtrl && firstValidIdx == firstCtrlIdx
            val room = lanes - base
            val fwdLen = if (hasCtrl) firstCtrlIdx else room
            val beatLen = if (alone) firstCtrlIdx + 1 else fwdLen
            val fwd = Vector.tabulate(lanes)(j => !alone && cand(j).nonEmpty && j < fwdLen)
            val outValid = fwd.exists(identity)
            dut.io.out.valid.expect(outValid.B)

            for (j <- 0 until lanes) {
                val port = dut.io.out.bits(j)
                port.valid.expect(fwd(j).B)
                if (fwd(j)) {
                    val e = cand(j).get
                    withClue(s"cycle=$cycleNo base=$base lane=$j ") {
                        port.bits.inst.peek().litValue shouldBe e.inst
                        port.bits.rs1.peek().litValue shouldBe e.rs1
                    }
                }
            }

            val cond =
                if (!alone) false
                else {
                    val c = cand(firstCtrlIdx).get
                    if (c.funct3 != 3) false else c.cmd match {
                        case 0 => backendIdle
                        case 1 => (events(c.aux) & c.rs1) == c.rs1
                        case 2 => s32(timelines(c.aux & 7) - c.rs1) >= 0
                        case 5 => true
                        case _ => false
                    }
                }

            val outFire = outValid && ready
            if (outFire) {
                val cmds = cand.zipWithIndex.filter { case (_, j) => fwd(j) }.map(_._1.get)
                forwarded = forwarded ++ cmds
                beats = beats :+ cmds
            }
            if (inRange && (outFire || (alone && cond) || (!alone && !outValid))) base += beatLen
            if (done && !inFire) active = false
            if (inFire) {
                pkt = offered.get
                base = 0
                active = true
                acceptedPackets += 1
            }
            dut.clock.step()
            inFire
        }

        def settle(cycles: Int, backendIdle: Boolean = true,
                   events: Vector[BigInt] = Vector.fill(cfg.eventGroupCount)(BigInt(0)),
                   timelines: Vector[BigInt] = Vector.fill(cfg.timelineCount)(BigInt(0))): Unit =
            for (_ <- 0 until cycles) cycle(ready = true, backendIdle = backendIdle,
                events = events, timelines = timelines)

        def drain(backendIdle: Boolean = true,
                  events: Vector[BigInt] = Vector.fill(cfg.eventGroupCount)(BigInt(0)),
                  timelines: Vector[BigInt] = Vector.fill(cfg.timelineCount)(BigInt(0))): Unit = {
            var cycles = 0
            while (active && cycles < 400) {
                cycle(ready = true, backendIdle = backendIdle, events = events, timelines = timelines)
                cycles += 1
            }
            active shouldBe false
        }
    }

    behavior of "QueenEndpointCtrl"

    it should "forward a whole write packet in one beat" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = (0 until 4).map(i => qwr1(i, 0x10 + i, pc = i))
            d.cycle(Some(d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)), ready = true)
            d.drain()
            d.beats shouldBe Vector(cmds.toVector)
        }
    }

    it should "hold a FENCE at the head until the backend is idle" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val w = qwr1(0, 0xaa, pc = 4)
            d.cycle(Some(d.offer(0 -> fence(pc = 0), 1 -> w)), ready = true, backendIdle = false)
            for (_ <- 0 until 4) d.cycle(ready = true, backendIdle = false)
            d.forwarded shouldBe empty
            d.cycle(ready = true, backendIdle = true)
            d.drain()
            d.forwarded shouldBe Vector(w)
        }
    }

    it should "forward the writes before a WAIT and hold the ones after it" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val before = qwr1(0, 0x11, pc = 0)
            val after = qwr1(1, 0x22, pc = 8)
            d.cycle(Some(d.offer(0 -> before, 1 -> waitEvent(5, 0x88, pc = 4), 2 -> after)),
                ready = true)
            d.settle(4)
            d.forwarded shouldBe Vector(before)
            val ev = Vector.tabulate(32)(g => if (g == 5) BigInt(0x88) else BigInt(0))
            d.cycle(ready = true, events = ev)
            d.drain(events = ev)
            d.forwarded shouldBe Vector(before, after)
        }
    }

    it should "evaluate WAIT_TIMELINE with signed distance across a wrap" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val w = qwr1(0, 0x33, pc = 4)
            // target 取回绕点附近的 0xfffffff0（signed -16）。
            d.cycle(Some(d.offer(0 -> waitTimeline(2, BigInt("fffffff0", 16), pc = 0), 1 -> w)),
                ready = true)
            val tlBefore = Vector.tabulate(8)(t =>
                if (t == 2) BigInt("ffffffe0", 16) else BigInt(0))
            d.settle(4, timelines = tlBefore)
            d.forwarded shouldBe empty
            // 计数已回绕到 5：相对 target 的 signed 距离为 +21 ⟹ 条件满足。
            val tlWrap = Vector.tabulate(8)(t => if (t == 2) BigInt(5) else BigInt(0))
            d.cycle(ready = true, timelines = tlWrap)
            d.drain(timelines = tlWrap)
            d.forwarded shouldBe Vector(w)
        }
    }

    it should "consume TRACE_TAG as a no-op without stalling the packet" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val a = qwr1(0, 1, pc = 0)
            val b = qwr1(1, 2, pc = 8)
            d.cycle(Some(d.offer(0 -> traceTag(0x1234, pc = 4), 1 -> a, 2 -> b)), ready = true)
            d.drain()
            d.forwarded shouldBe Vector(a, b)
        }
    }

    it should "consume multiple RUNs in lane order across beats" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val cmds = Vector(qwr1(0, 1), run(1), qwr1(1, 2), run(2))
            d.cycle(Some(d.offer(cmds.zipWithIndex.map { case (c, i) => i -> c }: _*)), ready = true)
            d.drain()
            d.forwarded shouldBe cmds
        }
    }

    it should "forward a QRD inside the write slice and leave beat splitting to the adapter" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val a = qwr1(0, 1, pc = 0)
            val r = qrd(0x40)
            val b = qwr1(1, 2, pc = 8)
            d.cycle(Some(d.offer(0 -> a, 1 -> r, 2 -> b)), ready = true)
            d.drain()
            d.beats shouldBe Vector(Vector(a, r, b))
        }
    }

    it should "hold a QRD behind an unsatisfied WAIT" in {
        val cfg = QueenConfig()
        simulate(new QueenEndpointCtrl(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val r = qrd(0x40)
            d.cycle(Some(d.offer(0 -> waitEvent(5, 0x88, pc = 0), 1 -> r)), ready = true)
            d.settle(4)
            d.forwarded shouldBe empty
            val ev = Vector.tabulate(32)(g => if (g == 5) BigInt(0x88) else BigInt(0))
            d.drain(events = ev)
            d.forwarded shouldBe Vector(r)
        }
    }

    for (lanes <- Vector(4, 2, 1)) {
        it should s"match an ordered software model with $lanes lanes under random traffic" in {
            val cfg = QueenConfig(instructionLanes = lanes)
            simulate(new QueenEndpointCtrl(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val rng = new Random(900 + lanes)
                var pending = Option.empty[Vector[Option[Cmd]]]
                var expected = Vector.empty[Cmd]
                for (_ <- 0 until 400) {
                    if (pending.isEmpty && rng.nextInt(3) != 0) {
                        val p = Vector.tabulate(lanes) { _ =>
                            if (rng.nextInt(4) == 0) None
                            else Some(rng.nextInt(7) match {
                                case 0 => qwr1(rng.nextInt(64), rng.nextInt(1 << 20))
                                case 1 => run(rng.nextInt(256))
                                case 2 => fence()
                                case 3 => waitEvent(rng.nextInt(32), rng.nextInt(1 << 8))
                                case 4 => waitTimeline(rng.nextInt(8), rng.nextInt(1 << 8))
                                case 5 => qrd(rng.nextInt(64))
                                case _ => traceTag(rng.nextInt(1 << 20))
                            })
                        }
                        if (p.exists(_.nonEmpty)) {
                            pending = Some(p)
                            expected = expected ++ p.flatten.filter(_.isFwd)
                        }
                    }
                    val ev = Vector.tabulate(32)(_ => BigInt(rng.nextInt(1 << 8)))
                    val tl = Vector.tabulate(8)(_ => BigInt(rng.nextInt(1 << 8)))
                    // 随机条件可能长期不满足，故周期性给出全满足的资源视图推动排空。
                    val satisfy = rng.nextInt(4) == 0
                    val fire = d.cycle(pending, rng.nextBoolean(), backendIdle = satisfy,
                        events = if (satisfy) Vector.fill(32)(Mask32) else ev,
                        timelines = if (satisfy) Vector.fill(8)(BigInt("7fffffff", 16)) else tl)
                    if (fire) pending = None
                }
                while (pending.nonEmpty) {
                    if (d.cycle(pending, ready = true, backendIdle = true,
                        events = Vector.fill(32)(Mask32),
                        timelines = Vector.fill(8)(BigInt("7fffffff", 16))))
                        pending = None
                }
                d.drain(backendIdle = true, events = Vector.fill(32)(Mask32),
                    timelines = Vector.fill(8)(BigInt("7fffffff", 16)))
                d.acceptedPackets should be > 0
                d.forwarded shouldBe expected
            }
        }
    }
}