import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class QueenDsaAdapterSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private case class Cmd(inst: BigInt, pc: BigInt, rs1: BigInt, rs2: BigInt) {
        def funct3: Int = ((inst >> 12) & 7).toInt
        def isQwr2: Boolean = funct3 == 0
        def isQwr1: Boolean = funct3 == 1
        def isQrd: Boolean = funct3 == 2
        def isRun: Boolean = funct3 == 3 && ((inst >> 26) & 15).toInt == 9
        def isWrite: Boolean = isQwr1 || isQwr2
        def base: Int = ((((inst >> 11) & 1) << 5) | ((inst >> 25) & 31)).toInt
    }

    private def qwr2(base: Int, rs1: BigInt, rs2: BigInt, pc: BigInt = 0): Cmd =
        Cmd((BigInt(base & 31) << 25) | (BigInt(2) << 20) | (BigInt(1) << 15) |
            (BigInt((base >> 5) & 1) << 11) | 0x0b, pc, rs1, rs2)

    private def qwr1(addr: Int, rs1: BigInt, pc: BigInt = 0): Cmd =
        Cmd((BigInt(addr) << 20) | (BigInt(1) << 15) | (BigInt(1) << 12) | 0x0b, pc, rs1, 0)

    private def qrd(addr: Int, rd: Int, pc: BigInt = 0): Cmd =
        Cmd((BigInt(addr) << 20) | (BigInt(2) << 12) | (BigInt(rd) << 7) | 0x0b, pc, 0, 0)

    private def run(cmdTag: Int, swTag: BigInt, pc: BigInt = 0): Cmd =
        Cmd((BigInt(9) << 26) | (BigInt(1) << 15) | (BigInt(3) << 12) | 0x0b, pc, cmdTag, swTag)

    private val Write1 = 0
    private val Write2 = 1
    private val Run = 3

    private case class Native(op: Int, reg0: Int, reg1: Int, data0: BigInt, data1: BigInt,
                              pc: BigInt, cmdTag: Int, swTag: BigInt)

    private def expected(c: Cmd): Native =
        if (c.isRun) Native(Run, 0, 0, 0, 0, c.pc, (c.rs1 & 0xff).toInt, c.rs2)
        else if (c.isQwr2) Native(Write2, c.base, c.base + 1, c.rs1, c.rs2, c.pc, 0, 0)
        else Native(Write1, ((c.inst >> 20) & 1023).toInt, 0, c.rs1, 0, c.pc, 0, 0)

    private class Driver(dut: QueenDsaAdapter, cfg: QueenConfig) {
        private val lanes = cfg.instructionLanes
        private val width = cfg.nativeWidth
        private var active = false
        private var pkt = Vector.fill[Option[Cmd]](lanes)(None)
        private var base = 0
        private var held = Option.empty[Vector[Option[Native]]]
        var emitted = Vector.empty[Native]
        var beats = Vector.empty[Vector[Native]]
        var acceptedPackets = 0
        var expectedNative = 0

        dut.io.in.valid.poke(false.B)
        dut.io.in.bits.foreach { l =>
            l.valid.poke(false.B)
            l.bits.inst.poke(0.U); l.bits.pc.poke(0.U)
            l.bits.rs1.poke(0.U); l.bits.rs2.poke(0.U)
        }
        dut.io.out.ready.poke(false.B)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def offer(commands: (Int, Cmd)*): Vector[Option[Cmd]] = {
            require(commands.map(_._1).distinct.size == commands.size)
            commands.foldLeft(Vector.fill[Option[Cmd]](lanes)(None)) {
                case (v, (i, c)) => v.updated(i, Some(c))
            }
        }

        private var cycleNo = 0

        def cycle(offered: Option[Vector[Option[Cmd]]] = None,
                  ready: Boolean = false): Boolean = {
            cycleNo += 1
            dut.io.in.valid.poke(offered.nonEmpty.B)
            for (i <- 0 until lanes) {
                val lane = dut.io.in.bits(i)
                val c = offered.flatMap(_(i))
                lane.valid.poke(c.nonEmpty.B)
                lane.bits.inst.poke(c.map(_.inst).getOrElse(BigInt(0)).U)
                lane.bits.pc.poke(c.map(_.pc).getOrElse(BigInt(0)).U)
                lane.bits.rs1.poke(c.map(_.rs1).getOrElse(BigInt(0)).U)
                lane.bits.rs2.poke(c.map(_.rs2).getOrElse(BigInt(0)).U)
            }
            dut.io.out.ready.poke(ready.B)

            val done = active && base >= lanes
            val inReady = !active || done
            dut.io.in.ready.expect(inReady.B)
            val inFire = offered.nonEmpty && inReady

            val inRange = active && base < lanes
            val cand = Vector.tabulate(width) { j =>
                if (inRange && base + j < lanes) pkt(base + j) else None
            }
            val candOther = cand.map(c => c.nonEmpty && !c.get.isWrite)
            val hasOther = candOther.exists(identity)
            val firstValidIdx = cand.indexWhere(_.nonEmpty)
            val firstOtherIdx = candOther.indexWhere(identity)
            val alone = firstValidIdx == firstOtherIdx
            val full = math.min(lanes - base, width)
            val beatLen = if (hasOther) firstOtherIdx + (if (alone) 1 else 0) else full
            val beat = cand.zipWithIndex.map { case (c, j) => if (j < beatLen) c else None }
            val beatValid = beat.exists(_.nonEmpty)
            val beatSupported = beat.flatten.forall(c => c.isWrite || c.isRun)
            val outValid = beatValid && beatSupported
            dut.io.out.valid.expect(outValid.B)

            val snapshot = beat.map(_.map(expected))
            for (j <- 0 until width) {
                val port = dut.io.out.bits(j)
                port.valid.expect(beat(j).nonEmpty.B)
                val actual = Native(port.bits.op.peek().litValue.toInt,
                    port.bits.reg0Addr.peek().litValue.toInt,
                    port.bits.reg1Addr.peek().litValue.toInt,
                    port.bits.data0.peek().litValue,
                    port.bits.data1.peek().litValue,
                    port.bits.pc.peek().litValue,
                    port.bits.cmdTag.peek().litValue.toInt,
                    port.bits.swTag.peek().litValue)
                snapshot(j).foreach { e =>
                    withClue(s"cycle=$cycleNo base=$base lane=$j expected=$e actual=$actual ") {
                        actual shouldBe e
                    }
                }
            }
            if (outValid && !ready) {
                held.foreach(previous => snapshot shouldBe previous)
                held = Some(snapshot)
            } else held = None

            val outFire = outValid && ready
            if (outFire) {
                val natives = beat.flatten.map(expected)
                emitted = emitted ++ natives
                beats = beats :+ natives
                val cmds = beat.flatten
                if (cmds.exists(c => !c.isWrite)) cmds.size shouldBe 1
            }

            if (inRange && (outFire || !beatValid)) base += beatLen
            if (done && !inFire) active = false
            if (inFire) {
                pkt = offered.get
                base = 0
                active = true
                acceptedPackets += 1
                expectedNative += pkt.flatten.size
            }
            dut.clock.step()
            inFire
        }

        def drain(): Unit = {
            var cycles = 0
            while (active && cycles < 400) {
                cycle(ready = true)
                cycles += 1
            }
            active shouldBe false
        }
    }

    behavior of "QueenDsaAdapter"

    it should "pass QWR2 through as one two-address write and isolate the RUN" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr2(5, BigInt("aaaa0001", 16), BigInt("bbbb0002", 16), pc = 0x100),
                1 -> run(0x12, BigInt("cccc0003", 16), pc = 0x104))
            d.cycle(Some(packet), ready = true) shouldBe true
            d.cycle(ready = true)
            d.cycle(ready = true)
            d.emitted shouldBe Vector(
                Native(Write2, 5, 6, BigInt("aaaa0001", 16), BigInt("bbbb0002", 16), 0x100, 0, 0),
                Native(Run, 0, 0, 0, 0, 0x104, 0x12, BigInt("cccc0003", 16)))
            d.beats.map(_.size) shouldBe Vector(1, 1)
            d.drain()
        }
    }

    it should "emit a full four-write packet in one beat" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer((0 until 4).map(i => i -> qwr1(0x40 + i, 0x100 + i, pc = i)): _*)
            d.cycle(Some(packet), ready = true) shouldBe true
            d.cycle(ready = true)
            d.beats.size shouldBe 1
            d.emitted shouldBe Vector.tabulate(4)(i => Native(Write1, 0x40 + i, 0, 0x100 + i, 0, i, 0, 0))
            d.drain()
        }
    }

    it should "cover the 6-bit QWR2 base and keep sparse lane positions" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr2(62, 0x11, 0x22, pc = 0x200),
                3 -> qwr1(0x321, 0x33, pc = 0x204))
            d.cycle(Some(packet), ready = true) shouldBe true
            d.cycle(ready = true)
            d.emitted shouldBe Vector(
                Native(Write2, 62, 63, 0x11, 0x22, 0x200, 0, 0),
                Native(Write1, 0x321, 0, 0x33, 0, 0x204, 0, 0))
            d.beats.size shouldBe 1
            d.drain()
        }
    }

    it should "give every RUN its own beat and merge the writes between them" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr2(3, 0x31, 0x32), 1 -> run(0x11, 0x1),
                2 -> qwr1(9, 0x41), 3 -> run(0x22, 0x2))
            d.cycle(Some(packet), ready = true) shouldBe true
            for (_ <- 0 until 5) d.cycle(ready = true)
            d.beats.map(_.map(_.op)) shouldBe Vector(
                Vector(Write2), Vector(Run), Vector(Write1), Vector(Run))
            d.emitted.size shouldBe 4
            d.drain()
        }
    }

    it should "merge writes across invalid lanes but stop before a RUN" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr1(1, 0x10), 2 -> qwr1(2, 0x20), 3 -> run(0x33, 0))
            d.cycle(Some(packet), ready = true) shouldBe true
            for (_ <- 0 until 3) d.cycle(ready = true)
            d.beats.map(_.size) shouldBe Vector(2, 1)
            d.beats(0).map(_.reg0) shouldBe Vector(1, 2)
            d.drain()
        }
    }

    it should "let write beats flow while a RUN beat waits for the device ready" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr1(7, 0x60), 1 -> qwr1(8, 0x61), 2 -> run(0x55, 0x66))
            d.cycle(Some(packet), ready = true) shouldBe true
            d.cycle(ready = true)
            d.emitted.size shouldBe 2
            for (_ <- 0 until 3) d.cycle(ready = false)
            d.emitted.size shouldBe 2
            d.cycle(ready = true)
            d.emitted.size shouldBe 3
            d.emitted.last shouldBe Native(Run, 0, 0, 0, 0, 0, 0x55, 0x66)
            d.drain()
        }
    }

    it should "skip an all-invalid slice without spending a beat" in {
        val cfg = QueenConfig(instructionLanes = 4, nativeLanes = 2)
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(3 -> qwr1(0x21, 0x99))), ready = true) shouldBe true
            d.cycle(ready = true)
            d.cycle(ready = true)
            d.beats.size shouldBe 1
            d.emitted shouldBe Vector(Native(Write1, 0x21, 0, 0x99, 0, 0, 0, 0))
            d.drain()
        }
    }

    it should "hold a packet until every beat is taken and accept back-to-back packets" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val first = d.offer(0 -> qwr2(1, 0x10, 0x11), 2 -> run(1, 0))
            val second = d.offer(1 -> qwr1(9, 0x20))
            d.cycle(Some(first), ready = false) shouldBe true
            d.cycle(Some(second), ready = false) shouldBe false
            d.cycle(Some(second), ready = true) shouldBe false
            d.cycle(Some(second), ready = true) shouldBe false
            d.cycle(Some(second), ready = true) shouldBe false
            d.cycle(Some(second), ready = true) shouldBe true
            d.acceptedPackets shouldBe 2
            d.drain()
            d.emitted.size shouldBe 3
        }
    }

    it should "keep the native payload stable while ready is low" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr2(40, 0x77, 0x88))), ready = false) shouldBe true
            for (_ <- 0 until 4) d.cycle(ready = false)
            d.emitted shouldBe empty
            d.cycle(ready = true)
            d.emitted.size shouldBe 1
            d.drain()
        }
    }

    it should "accept an empty packet without emitting anything" in {
        val cfg = QueenConfig(instructionLanes = 4, nativeLanes = 2)
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer()), ready = true) shouldBe true
            d.cycle(ready = true)
            d.cycle(ready = true)
            d.emitted shouldBe empty
            d.beats shouldBe empty
            d.drain()
        }
    }

    it should "stall on QRD until the read-response path exists" in {
        val cfg = QueenConfig()
        val ex = intercept[Exception] {
            simulate(new QueenDsaAdapter(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                d.cycle(Some(d.offer(0 -> qrd(0x40, 1))), ready = true)
                d.cycle(ready = true)
            }
        }
        ex.getClass.getName should include("AssertionFailed")
    }

    for ((lanes, native) <- Vector((4, 4), (4, 2), (4, 1), (2, 2), (1, 1), (8, 3))) {
        it should s"match an ordered software beat model with $lanes lanes and $native native lanes" in {
            val cfg = QueenConfig(instructionLanes = lanes, nativeLanes = native)
            simulate(new QueenDsaAdapter(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val rng = new Random(700 + lanes * 10 + native)
                var pending = Option.empty[Vector[Option[Cmd]]]
                var total = 0
                for (_ <- 0 until 400) {
                    if (pending.isEmpty && rng.nextInt(3) != 0) {
                        pending = Some(Vector.tabulate(lanes) { _ =>
                            if (rng.nextInt(3) == 0) None
                            else {
                                total += 1
                                rng.nextInt(3) match {
                                    case 0 => Some(qwr2(rng.nextInt(63), rng.nextInt(1 << 30),
                                        rng.nextInt(1 << 30), pc = total))
                                    case 1 => Some(qwr1(rng.nextInt(1024), rng.nextInt(1 << 30), pc = total))
                                    case _ => Some(run(rng.nextInt(256), rng.nextInt(1 << 30), pc = total))
                                }
                            }
                        })
                        if (pending.get.forall(_.isEmpty)) pending = None
                    }
                    val fire = d.cycle(pending, rng.nextBoolean())
                    if (fire) pending = None
                }
                while (pending.nonEmpty) {
                    if (d.cycle(pending, ready = true)) pending = None
                }
                d.drain()
                d.acceptedPackets should be > 0
                d.emitted.size shouldBe d.expectedNative
            }
        }
    }

    it should "reject a native width beyond the instruction lanes" in {
        an[IllegalArgumentException] should be thrownBy QueenConfig(nativeLanes = 5)
        an[IllegalArgumentException] should be thrownBy QueenConfig(nativeLanes = -1)
        an[IllegalArgumentException] should be thrownBy
            QueenConfig(instructionLanes = 2, nativeLanes = 4)
        QueenConfig(instructionLanes = 2).nativeWidth shouldBe 2
        QueenConfig(instructionLanes = 8, nativeLanes = 3).nativeWidth shouldBe 3
    }
}