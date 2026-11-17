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
        def isRun: Boolean = funct3 == 3 && ((inst >> 26) & 15).toInt == 9
        def base: Int = ((((inst >> 11) & 1) << 5) | ((inst >> 25) & 31)).toInt
    }

    private def qwr2(base: Int, rs1: BigInt, rs2: BigInt, pc: BigInt = 0): Cmd =
        Cmd((BigInt(base & 31) << 25) | (BigInt(2) << 20) | (BigInt(1) << 15) |
            (BigInt((base >> 5) & 1) << 11) | 0x0b, pc, rs1, rs2)

    private def qwr1(addr: Int, rs1: BigInt, pc: BigInt = 0): Cmd =
        Cmd((BigInt(addr) << 20) | (BigInt(1) << 15) | (BigInt(1) << 12) | 0x0b, pc, rs1, 0)

    private def run(cmdTag: Int, swTag: BigInt, pc: BigInt = 0): Cmd =
        Cmd((BigInt(9) << 26) | (BigInt(1) << 15) | (BigInt(3) << 12) | 0x0b, pc, cmdTag, swTag)

    private case class Native(isStart: Boolean, regAddr: Int, data: BigInt, pc: BigInt,
                              cmdTag: Int, swTag: BigInt)

    private def expected(c: Cmd, second: Boolean): Native = {
        val addr = if (c.isRun) 0 else if (c.isQwr2) c.base + (if (second) 1 else 0)
            else ((c.inst >> 20) & 1023).toInt
        val data = if (c.isRun) BigInt(0) else if (c.isQwr2 && second) c.rs2 else c.rs1
        Native(c.isRun, addr, data, c.pc,
            if (c.isRun) (c.rs1 & 0xff).toInt else 0,
            if (c.isRun) c.rs2 else BigInt(0))
    }

    private class Driver(dut: QueenDsaAdapter, cfg: QueenConfig) {
        private val lanes = cfg.instructionLanes
        private var active = false
        private var pkt = Vector.fill[Option[Cmd]](lanes)(None)
        private var laneIdx = 0
        private var second = false
        private var held = Option.empty[Native]
        var emitted = Vector.empty[Native]
        var acceptedPackets = 0
        var expectedNative = 0

        dut.io.in.valid.poke(false.B)
        dut.io.in.bits.foreach { l =>
            l.valid.poke(false.B)
            l.bits.inst.poke(0.U); l.bits.pc.poke(0.U)
            l.bits.rs1.poke(0.U); l.bits.rs2.poke(0.U)
        }
        dut.io.out.ready.poke(false.B)
        dut.io.cfgCredit.poke(0.U)
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
                  ready: Boolean = false, credit: Int = 0): Boolean = {
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
            dut.io.cfgCredit.poke(credit.U)

            val done = active && laneIdx == lanes
            val inReady = !active || done
            dut.io.in.ready.expect(inReady.B)
            val inFire = offered.nonEmpty && inReady

            val inRange = active && laneIdx < lanes
            val cur = if (inRange) pkt(laneIdx) else None
            val canEmit = cur.exists(c => c.isQwr1 || c.isQwr2 || (c.isRun && credit != 0))
            dut.io.out.valid.expect(canEmit.B)
            val exp = cur.filter(_ => canEmit).map(c => expected(c, second))
            exp.foreach { e =>
                val actual = Native(dut.io.out.bits.isStart.peek().litToBoolean,
                    dut.io.out.bits.regAddr.peek().litValue.toInt,
                    dut.io.out.bits.data.peek().litValue,
                    dut.io.out.bits.pc.peek().litValue,
                    dut.io.out.bits.cmdTag.peek().litValue.toInt,
                    dut.io.out.bits.swTag.peek().litValue)
                withClue(s"cycle=$cycleNo lane=$laneIdx second=$second expected=$e actual=$actual ") {
                    actual shouldBe e
                }
                if (!ready) {
                    held.foreach(previous => actual shouldBe previous)
                    held = Some(actual)
                } else held = None
            }
            if (exp.isEmpty) held = None
            val outFire = canEmit && ready
            if (outFire) emitted = emitted :+ exp.get

            if (inRange && pkt(laneIdx).isEmpty) laneIdx += 1
            if (outFire) {
                val c = cur.get
                if (c.isQwr2 && !second) second = true
                else { second = false; laneIdx += 1 }
            }
            if (done && !inFire) active = false
            if (inFire) {
                pkt = offered.get
                laneIdx = 0
                second = false
                active = true
                acceptedPackets += 1
                expectedNative += pkt.flatten.count(c => c.isQwr2 || c.isQwr1 || c.isRun) +
                    pkt.flatten.count(_.isQwr2)
            }
            dut.clock.step()
            inFire
        }

        def drain(): Unit = {
            var cycles = 0
            while (active && cycles < 400) {
                cycle(ready = true, credit = 3)
                cycles += 1
            }
            active shouldBe false
        }
    }

    behavior of "QueenDsaAdapter"

    it should "serialize QWR2 into two ordered writes and RUN into a start" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr2(5, BigInt("aaaa0001", 16), BigInt("bbbb0002", 16), pc = 0x100),
                1 -> run(0x12, BigInt("cccc0003", 16), pc = 0x104))
            d.cycle(Some(packet), ready = true, credit = 2) shouldBe true
            d.cycle(ready = true, credit = 2)
            d.cycle(ready = true, credit = 2)
            d.cycle(ready = true, credit = 2)
            d.emitted shouldBe Vector(
                Native(false, 5, BigInt("aaaa0001", 16), 0x100, 0, 0),
                Native(false, 6, BigInt("bbbb0002", 16), 0x100, 0, 0),
                Native(true, 0, BigInt(0), 0x104, 0x12, BigInt("cccc0003", 16)))
            d.drain()
        }
    }

    it should "skip invalid lanes, keep program order and cover the 6-bit QWR2 base" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr2(33, 0x11, 0x22, pc = 0x200),
                3 -> qwr1(0x321, 0x33, pc = 0x204))
            d.cycle(Some(packet), ready = true, credit = 1) shouldBe true
            for (_ <- 0 until 6) d.cycle(ready = true, credit = 1)
            d.emitted shouldBe Vector(
                Native(false, 33, BigInt(0x11), 0x200, 0, 0),
                Native(false, 34, BigInt(0x22), 0x200, 0, 0),
                Native(false, 0x321, BigInt(0x33), 0x204, 0, 0))
            d.drain()
        }
    }

    it should "gate only RUN on credit and keep configuration writes flowing" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val packet = d.offer(0 -> qwr2(7, 0x41, 0x42), 1 -> run(0x55, 0x66))
            d.cycle(Some(packet), ready = true, credit = 0) shouldBe true
            d.cycle(ready = true, credit = 0)
            d.cycle(ready = true, credit = 0)
            // Both writes are out, the RUN is still waiting for credit.
            d.emitted.size shouldBe 2
            d.cycle(ready = true, credit = 0)
            d.emitted.size shouldBe 2
            d.cycle(ready = true, credit = 1)
            d.emitted.size shouldBe 3
            d.emitted.last shouldBe Native(true, 0, BigInt(0), 0, 0x55, BigInt(0x66))
            d.drain()
        }
    }

    it should "hold a packet until every lane is emitted and accept back-to-back packets" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val first = d.offer(0 -> qwr2(1, 0x10, 0x11), 2 -> run(1, 0))
            val second = d.offer(1 -> qwr1(9, 0x20))
            d.cycle(Some(first), ready = false, credit = 3) shouldBe true
            d.cycle(Some(second), ready = false, credit = 3) shouldBe false
            d.cycle(Some(second), ready = true, credit = 3) shouldBe false
            d.cycle(Some(second), ready = true, credit = 3) shouldBe false
            d.cycle(Some(second), ready = true, credit = 3) shouldBe false
            d.cycle(Some(second), ready = true, credit = 3) shouldBe false
            d.cycle(Some(second), ready = true, credit = 3) shouldBe false
            // Two invalid lanes are skipped one per cycle, then the packet end frees the slot.
            d.cycle(Some(second), ready = true, credit = 3) shouldBe true
            d.acceptedPackets shouldBe 2
            d.drain()
            d.emitted.size shouldBe 4
        }
    }

    it should "keep the native payload stable while ready is low" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaAdapter(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(d.offer(0 -> qwr2(40, 0x77, 0x88))), ready = false, credit = 0) shouldBe true
            for (_ <- 0 until 4) d.cycle(ready = false, credit = 0)
            d.emitted shouldBe empty
            d.cycle(ready = true, credit = 0)
            d.emitted.size shouldBe 1
            d.drain()
        }
    }

    for (lanes <- Vector(4, 1, 2)) {
        it should s"match an ordered software serializer with $lanes lanes" in {
            val cfg = QueenConfig(instructionLanes = lanes)
            simulate(new QueenDsaAdapter(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val rng = new Random(700 + lanes)
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
                    val fire = d.cycle(pending, rng.nextBoolean(), rng.nextInt(4))
                    if (fire) pending = None
                }
                while (pending.nonEmpty) {
                    if (d.cycle(pending, ready = true, credit = 3)) pending = None
                }
                d.drain()
                d.acceptedPackets should be > 0
                d.emitted.size shouldBe d.expectedNative
            }
        }
    }
}