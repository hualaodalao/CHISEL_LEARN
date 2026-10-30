import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.util.Random

class QueenDsaDecoderSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private case class Command(inst: BigInt, rs1: BigInt = 0, rs2: BigInt = 0,
                               rs1Valid: Boolean = false, rs2Valid: Boolean = false) {
        def endpoint: Int = ((inst >> 30) & 3).toInt
        def payload: (BigInt, BigInt, BigInt) =
            (inst, if (rs1Valid) rs1 else BigInt(0), if (rs2Valid) rs2 else BigInt(0))
    }

    private def write2(endpoint: Int, tag: Int): Command = Command(
        (BigInt(endpoint) << 30) | (BigInt(tag % 31) << 25) | (BigInt(2) << 20) |
            (BigInt(1) << 15) | (BigInt(tag & 1) << 11) | 0x0b,
        BigInt("12340000", 16) | tag,
        BigInt("fedc0000", 16) | tag,
        rs1Valid = true, rs2Valid = true
    )

    private def control(endpoint: Int, cmd: Int, rs1: BigInt = 0, rs2: BigInt = 0,
                        aux: Int = 0, bit25: Int = 0, hasRs1: Boolean = true,
                        hasRs2: Boolean = false): Command = Command(
        (BigInt(endpoint) << 30) | (BigInt(cmd) << 26) | (BigInt(bit25) << 25) |
            (BigInt(if (hasRs2) 2 else 0) << 20) | (BigInt(if (hasRs1) 1 else 0) << 15) |
            (BigInt(3) << 12) | (BigInt(aux) << 7) | 0x0b,
        rs1, rs2, hasRs1, hasRs2
    )

    private class Driver(dut: QueenDsaDecoder, cfg: QueenConfig) {
        private val ports = Vector(dut.io.gemm, dut.io.dma, dut.io.scheduler)
        private val endpoints = Vector(0, 1, 3)
        private val ringDepth = cfg.cmdRingDepth
        private val entries = mutable.HashMap.empty[BigInt, Vector[Option[Command]]]
        private val sched = mutable.Queue.empty[Vector[Option[Command]]]
        private var writePtr = BigInt(0)
        private var gemmCursor = BigInt(0)
        private var dmaCursor = BigInt(0)
        private var pending = Vector.fill[Option[Command]](cfg.instructionLanes)(None)
        private var held = Vector.fill(3)(Option.empty[Vector[(Boolean, BigInt, BigInt, BigInt)]])
        val empty: Vector[Option[Command]] = Vector.fill(cfg.instructionLanes)(None)
        var lastOutput = Vector.fill(3)(Vector.empty[(BigInt, BigInt, BigInt)])
        var lastMask = Vector.fill(3)(Vector.fill(cfg.instructionLanes)(false))
        var acceptedCount = 0
        var outputCount = 0

        dut.io.inst.foreach { p => p.valid.poke(false.B); p.bits.poke(0.U) }
        dut.io.rs1.foreach { p => p.valid.poke(false.B); p.bits.poke(0.U) }
        dut.io.rs2.foreach { p => p.valid.poke(false.B); p.bits.poke(0.U) }
        ports.foreach(_.ready.poke(false.B))
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        private def maskOf(packet: Vector[Option[Command]], e: Int): Vector[Option[Command]] =
            packet.map(_.filter(_.endpoint == endpoints(e)))

        private def ringHead(cursor: BigInt): Option[Vector[Option[Command]]] =
            if (cursor == writePtr) None else Some(entries(cursor % ringDepth))

        private def hasRingLane(packet: Vector[Option[Command]], e: Int): Boolean =
            maskOf(packet, e).exists(_.nonEmpty)

        def offer(commands: (Int, Command)*): Vector[Option[Command]] = {
            require(commands.map(_._1).distinct.size == commands.size)
            commands.foldLeft(empty) { case (lanes, (i, command)) => lanes.updated(i, Some(command)) }
        }

        private var cycleNo = 0

        def cycle(offered: Vector[Option[Command]] = empty,
                  ready: Vector[Boolean] = Vector(false, false, false)): Vector[Boolean] = {
            cycleNo += 1
            for (i <- 0 until cfg.instructionLanes) {
                dut.io.inst(i).valid.poke(offered(i).nonEmpty.B)
                dut.io.inst(i).bits.poke(offered(i).map(_.inst).getOrElse(BigInt(0)).U)
                dut.io.rs1(i).valid.poke(pending(i).exists(_.rs1Valid).B)
                dut.io.rs1(i).bits.poke(pending(i).map(_.rs1).getOrElse(BigInt(0)).U)
                dut.io.rs2(i).valid.poke(pending(i).exists(_.rs2Valid).B)
                dut.io.rs2(i).bits.poke(pending(i).map(_.rs2).getOrElse(BigInt(0)).U)
            }
            ports.zip(ready).foreach { case (port, value) => port.ready.poke(value.B) }

            val gemmHead = ringHead(gemmCursor)
            val dmaHead = ringHead(dmaCursor)
            val schedHead = sched.headOption
            val heads = Vector(gemmHead.map(maskOf(_, 0)), dmaHead.map(maskOf(_, 1)),
                schedHead.map(maskOf(_, 2)))
            val presenting = heads.map(_.exists(_.exists(_.nonEmpty)))

            for (e <- 0 until 3) {
                val port = ports(e)
                port.valid.expect(presenting(e).B)
                heads(e).filter(_ => presenting(e)).foreach { packet =>
                    packet.zip(port.bits).zipWithIndex.foreach { case ((command, lane), i) =>
                        val actual = (lane.bits.inst.peek().litValue, lane.bits.rs1.peek().litValue,
                            lane.bits.rs2.peek().litValue)
                        withClue(s"cycle=$cycleNo port=$e lane=$i " +
                            s"expected=${command.map(_.payload)} actual=$actual ") {
                            lane.valid.peek().litToBoolean shouldBe command.nonEmpty
                            command.foreach(c => actual shouldBe c.payload)
                        }
                    }
                }
                val snapshot = port.bits.map { lane =>
                    (lane.valid.peek().litToBoolean, lane.bits.inst.peek().litValue,
                        lane.bits.rs1.peek().litValue, lane.bits.rs2.peek().litValue)
                }.toVector
                if (presenting(e) && !ready(e)) {
                    held(e).foreach(previous => snapshot shouldBe previous)
                    held = held.updated(e, Some(snapshot))
                } else {
                    held = held.updated(e, None)
                }
                lastMask = lastMask.updated(e, snapshot.map(_._1))
                lastOutput = lastOutput.updated(e,
                    snapshot.collect { case (true, inst, rs1, rs2) => (inst, rs1, rs2) })
            }

            val gemmAdvance = gemmHead.exists(p => !hasRingLane(p, 0) || ready(0))
            val dmaAdvance = dmaHead.exists(p => !hasRingLane(p, 1) || ready(1))
            val schedFire = schedHead.nonEmpty && ready(2)
            val gemmFire = gemmHead.exists(hasRingLane(_, 0)) && ready(0)
            val dmaFire = dmaHead.exists(hasRingLane(_, 1)) && ready(1)

            val pendingRing = pending.exists(_.exists(c => c.endpoint == 0 || c.endpoint == 1))
            val pendingSched = pending.exists(_.exists(_.endpoint == 3))
            val gemmNext = gemmCursor + (if (gemmAdvance) 1 else 0)
            val dmaNext = dmaCursor + (if (dmaAdvance) 1 else 0)
            val nextCommit = gemmNext.min(dmaNext)
            val ringReady = writePtr + (if (pendingRing) 1 else 0) - nextCommit < BigInt(ringDepth)
            val schedNext = sched.size + (if (pendingSched) 1 else 0) - (if (schedFire) 1 else 0)
            val schedReady = schedNext < 1

            val accepted = Vector.tabulate(cfg.instructionLanes) { i =>
                offered(i) match {
                    case Some(command) =>
                        val canAccept = command.endpoint match {
                            case 3 => schedReady
                            case 2 => false
                            case _ => ringReady
                        }
                        dut.io.inst(i).ready.expect(canAccept.B)
                        canAccept
                    case None => false
                }
            }

            if (gemmFire) outputCount += maskOf(gemmHead.get, 0).flatten.size
            if (dmaFire) outputCount += maskOf(dmaHead.get, 1).flatten.size
            if (schedFire) outputCount += maskOf(schedHead.get, 2).flatten.size
            if (gemmAdvance) gemmCursor += 1
            if (dmaAdvance) dmaCursor += 1
            if (schedFire) sched.dequeue()
            if (pendingRing) {
                entries(writePtr % ringDepth) = pending
                writePtr += 1
            }
            if (pendingSched) {
                sched.enqueue(maskOf(pending, 2))
            }
            sched.size should be <= 1
            pending = offered.zip(accepted).map { case (command, fire) => if (fire) command else None }
            acceptedCount += accepted.count(identity)
            dut.clock.step()
            accepted
        }

        def drain(): Unit = {
            var cycles = 0
            while ((pending.exists(_.nonEmpty) || sched.nonEmpty || writePtr != gemmCursor ||
                writePtr != dmaCursor) && cycles < 200) {
                cycle(ready = Vector(true, true, true))
                cycles += 1
            }
            pending.forall(_.isEmpty) shouldBe true
            writePtr shouldBe gemmCursor
            writePtr shouldBe dmaCursor
            sched.isEmpty shouldBe true
            outputCount shouldBe acceptedCount
        }
    }

    behavior of "QueenDsaDecoder"

    it should "keep sparse lane masks and input batch boundaries in each ring slot" in {
        val cfg = QueenConfig(cmdRingDepth = 3)
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.offer(0 -> write2(0, 1), 3 -> write2(0, 2))) shouldBe
                Vector(true, false, false, true)
            d.cycle(d.offer(1 -> write2(0, 3))) shouldBe Vector(false, true, false, false)
            d.cycle()
            d.lastMask(0) shouldBe Vector(true, false, false, true)
            d.cycle(ready = Vector(true, false, false))
            d.lastOutput(0).size shouldBe 2
            d.cycle(ready = Vector(true, false, false))
            d.lastMask(0) shouldBe Vector(false, true, false, false)
            d.lastOutput(0).size shouldBe 1
            d.drain()
        }
    }

    it should "pair back-to-back instructions with next-cycle operands on the same lane" in {
        val cfg = QueenConfig(cmdRingDepth = 2)
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            for ((endpoint, index) <- Vector(0, 1, 3, 0, 1, 3).zipWithIndex) {
                d.cycle(d.offer(2 -> write2(endpoint, 100 + index)), Vector(true, true, true)) shouldBe
                    Vector(false, false, true, false)
            }
            d.drain()
        }
    }

    it should "enqueue four commands per endpoint and preserve operands and lane order" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            for ((endpoint, tag) <- Vector(0 -> 0, 1 -> 100, 3 -> 200)) {
                d.cycle(d.offer((0 until 4).map(i => i -> write2(endpoint, tag + i)): _*)) shouldBe
                    Vector(true, true, true, true)
            }
            d.cycle()
            d.cycle()
            d.lastOutput.map(_.size) shouldBe Vector(4, 4, 4)
            d.drain()
        }
    }

    it should "pack a whole batch into one ring slot and keep SCHED flowing while the ring is full" in {
        val cfg = QueenConfig(cmdRingDepth = 2)
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.offer((0 until 4).map(i => i -> write2(0, i)): _*)) shouldBe Vector.fill(4)(true)
            d.cycle(d.offer((0 until 4).map(i => i -> write2(1, 10 + i)): _*)) shouldBe Vector.fill(4)(true)
            // Both GEMM and DMA slots are live, so only the SCHED bypass can be accepted.
            d.cycle(d.offer(0 -> write2(0, 20), 3 -> write2(3, 30))) shouldBe
                Vector(false, false, false, true)
            // GEMM drains its slot this cycle, so its slot is reusable in the same cycle.
            d.cycle(d.offer(0 -> write2(0, 21)), Vector(true, false, false)) shouldBe
                Vector(true, false, false, false)
            d.drain()
        }
    }

    it should "keep GEMM flowing while a DMA packet holds the ring head" in {
        val cfg = QueenConfig(cmdRingDepth = 4)
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.offer(0 -> write2(1, 1))) shouldBe Vector(true, false, false, false)
            d.cycle(d.offer(0 -> write2(0, 2))) shouldBe Vector(true, false, false, false)
            d.cycle(d.offer(0 -> write2(1, 3))) shouldBe Vector(true, false, false, false)
            d.cycle(ready = Vector(true, false, false))
            d.lastOutput(1).size shouldBe 1
            d.lastOutput(0).size shouldBe 1
            d.cycle(ready = Vector(true, false, false))
            d.lastOutput(1).size shouldBe 1
            d.lastOutput(0).size shouldBe 0
            d.cycle(ready = Vector(true, true, true))
            d.lastOutput(1).size shouldBe 1
            d.drain()
        }
    }

    it should "hold the full output batch stable while new commands arrive" in {
        val cfg = QueenConfig()
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.offer(3 -> write2(0, 1))) shouldBe Vector(false, false, false, true)
            d.cycle(d.offer(0 -> write2(0, 2), 2 -> write2(0, 3), 3 -> write2(0, 4))) shouldBe
                Vector(true, false, true, true)
            d.cycle()
            d.lastOutput(0).size shouldBe 1
            d.cycle()
            d.lastOutput(0).size shouldBe 1
            d.cycle(ready = Vector(true, false, false))
            d.lastOutput(0).size shouldBe 1
            d.cycle(ready = Vector(true, false, false))
            d.lastOutput(0).size shouldBe 3
            d.drain()
        }
    }

    it should "package source channels without decoding the instruction's operand requirements" in {
        val cfg = QueenConfig()
        val commands = Vector(
            Command((BigInt(0x321) << 20) | (BigInt(1) << 15) | (BigInt(1) << 12) | 0x80b,
                rs1 = 55, rs1Valid = true),
            Command((BigInt(1) << 30) | (BigInt(0x123) << 20) | (BigInt(2) << 12) | (BigInt(5) << 7) | 0x0b),
            control(0, 0, bit25 = 1, hasRs1 = false),
            control(3, 0, rs1 = 3, bit25 = 1),
            control(1, 1, rs1 = 0x55, aux = 31, bit25 = 1),
            control(0, 2, rs1 = 1234, aux = 7),
            control(3, 3, aux = 6, hasRs1 = false),
            control(3, 3, rs1 = 2222, aux = 3),
            control(3, 4, rs1 = 0x88, aux = 5, bit25 = 1),
            control(0, 5, rs1 = 0x12345678),
            control(3, 6, rs1 = 0x1000, rs2 = 0x87654321L, bit25 = 1, hasRs2 = true),
            control(3, 8, rs1 = 0x2000, rs2 = 0x42, aux = 2, hasRs2 = true),
            write2(0, 100).copy(rs1Valid = false, rs2Valid = false),
            control(1, 0, rs1 = 0x11, rs2 = 0x22, hasRs2 = true),
            control(3, 7, rs1 = 0x33, rs2 = 0x44, hasRs2 = true)
        )
        simulate(new QueenDsaDecoder(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            var index = 0
            var cycles = 0
            // Back-to-back SCHED commands need a retry because the scheduler stage holds one entry.
            while (index < commands.length && cycles < 200) {
                val accepted = d.cycle(d.offer((index % 4) -> commands(index)), Vector(true, true, true))
                accepted.count(identity) should be <= 1
                if (accepted.exists(identity)) index += 1
                cycles += 1
            }
            index shouldBe commands.length
            d.drain()
        }
    }

    for ((lanes, ringDepth) <- Vector(4 -> 2, 4 -> 8, 1 -> 2)) {
        it should s"match an ordered software ring with $lanes lanes and depth $ringDepth" in {
            val cfg = QueenConfig(instructionLanes = lanes, cmdRingDepth = ringDepth)
            simulate(new QueenDsaDecoder(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val rng = new Random(100 + ringDepth + lanes)
                var offered = d.empty
                var tag = 1000
                for (_ <- 0 until 500) {
                    offered = offered.map { command =>
                        if (command.isEmpty && rng.nextInt(4) != 0) {
                            tag += 1
                            Some(write2(Vector(0, 1, 3)(rng.nextInt(3)), tag))
                        } else command
                    }
                    val accepted = d.cycle(offered, Vector.fill(3)(rng.nextBoolean()))
                    offered = offered.zip(accepted).map { case (command, fire) => if (fire) None else command }
                }
                d.drain()
            }
        }
    }

    it should "reject a command ring shallower than two slots" in {
        an[IllegalArgumentException] should be thrownBy QueenConfig(cmdRingDepth = 1)
        an[IllegalArgumentException] should be thrownBy QueenConfig(cmdRingDepth = 0)
    }
}
