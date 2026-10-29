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
        private val depths = Vector(cfg.gemmQueueDepth, cfg.dmaQueueDepth, cfg.schedQueueDepth)
        private val queues = Vector.fill(3)(mutable.Queue.empty[Vector[Option[Command]]])
        private var pending = Vector.fill[Option[Command]](cfg.instructionLanes)(None)
        private var buffered: Option[Vector[Option[Command]]] = None
        private var stalled = Vector.fill[Option[Vector[(Boolean, BigInt, BigInt, BigInt)]]](3)(None)
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

        def offer(commands: (Int, Command)*): Vector[Option[Command]] = {
            require(commands.map(_._1).distinct.size == commands.size)
            commands.foldLeft(empty) { case (lanes, (i, command)) => lanes.updated(i, Some(command)) }
        }

        def cycle(offered: Vector[Option[Command]] = empty,
                  ready: Vector[Boolean] = Vector(false, false, false)): Vector[Boolean] = {
            for (i <- 0 until cfg.instructionLanes) {
                dut.io.inst(i).valid.poke(offered(i).nonEmpty.B)
                dut.io.inst(i).bits.poke(offered(i).map(_.inst).getOrElse(BigInt(0)).U)
                dut.io.rs1(i).valid.poke(pending(i).exists(_.rs1Valid).B)
                dut.io.rs1(i).bits.poke(pending(i).map(_.rs1).getOrElse(BigInt(0)).U)
                dut.io.rs2(i).valid.poke(pending(i).exists(_.rs2Valid).B)
                dut.io.rs2(i).bits.poke(pending(i).map(_.rs2).getOrElse(BigInt(0)).U)
            }
            ports.zip(ready).foreach { case (port, value) => port.ready.poke(value.B) }

            val snapshots = ports.map { port =>
                port.bits.map { entry =>
                    (entry.valid.peek().litToBoolean, entry.bits.inst.peek().litValue,
                        entry.bits.rs1.peek().litValue, entry.bits.rs2.peek().litValue)
                }.toVector
            }
            lastOutput = snapshots.zip(queues).map { case (entries, queue) =>
                if (queue.nonEmpty) entries.collect { case (true, inst, rs1, rs2) => (inst, rs1, rs2) }
                else Vector.empty
            }
            lastMask = snapshots.zip(queues).map { case (entries, queue) =>
                if (queue.nonEmpty) entries.map(_._1) else Vector.fill(cfg.instructionLanes)(false)
            }
            for (e <- 0 until 3) {
                ports(e).valid.expect(queues(e).nonEmpty.B)
                stalled(e).foreach { expected => snapshots(e) shouldBe expected }
                if (queues(e).nonEmpty) {
                    val expected = queues(e).front
                    lastMask(e) shouldBe expected.map(_.nonEmpty)
                    lastOutput(e) shouldBe expected.flatten.map(_.payload)
                    lastOutput(e).size should be > 0
                }
                stalled = stalled.updated(e, if (queues(e).nonEmpty && !ready(e)) Some(snapshots(e)) else None)
            }

            val allReady = queues.zip(depths).forall { case (queue, depth) => queue.size < depth }
            dut.io.inst.foreach(_.ready.expect(allReady.B))
            val accepted = offered.map(_.nonEmpty && allReady)
            val returning = if (pending.exists(_.nonEmpty)) Some(pending) else None
            val outgoing = buffered.orElse(returning)

            for (e <- 0 until 3) {
                if (ready(e) && queues(e).nonEmpty) {
                    outputCount += queues(e).dequeue().flatten.size
                }
                if (allReady) outgoing.foreach { packet =>
                    val batch = packet.map(_.filter(_.endpoint == endpoints(e)))
                    if (batch.exists(_.nonEmpty)) queues(e).enqueue(batch)
                }
            }
            if (allReady) buffered = if (buffered.nonEmpty) returning else None
            else if (buffered.isEmpty) buffered = returning
            else returning shouldBe None
            pending = offered.zip(accepted).map { case (command, fire) => if (fire) command else None }
            acceptedCount += accepted.count(identity)
            dut.clock.step()
            accepted
        }

        def drain(): Unit = {
            var cycles = 0
            while ((pending.exists(_.nonEmpty) || buffered.nonEmpty || queues.exists(_.nonEmpty)) && cycles < 100) {
                cycle(ready = Vector(true, true, true))
                cycles += 1
            }
            pending shouldBe empty
            buffered shouldBe None
            queues.foreach(_.isEmpty shouldBe true)
            outputCount shouldBe acceptedCount
        }
    }

    behavior of "QueenDsaDecoder"

    it should "keep sparse lane masks and input batch boundaries in each Queue entry" in {
        val cfg = QueenConfig(gemmQueueDepth = 3)
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
        val cfg = QueenConfig(gemmQueueDepth = 3, dmaQueueDepth = 2, schedQueueDepth = 2)
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

    for ((blocker, index) <- Vector(0, 1, 3).zipWithIndex) {
        it should s"stop all lanes when endpoint $blocker is full and retain next-cycle operands" in {
            val cfg = QueenConfig(gemmQueueDepth = 1, dmaQueueDepth = 1, schedQueueDepth = 1)
            simulate(new QueenDsaDecoder(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val others = Vector(0, 1, 3).filterNot(_ == blocker)
                d.cycle(d.offer((0 until 4).map(i => i -> write2(blocker, i)): _*)) shouldBe
                    Vector.fill(4)(true)
                val returning = d.offer((0 until 4).map(i => i -> write2(others(i % 2), 10 + i)): _*)
                d.cycle(returning) shouldBe Vector.fill(4)(true)
                val offered = d.offer((0 until 4).map(i => i -> write2(Vector(0, 1, 3)(i % 3), 100 + i)): _*)
                for (_ <- 0 until 3) {
                    d.cycle(offered) shouldBe Vector.fill(4)(false)
                    d.lastOutput.zipWithIndex.foreach { case (commands, e) =>
                        if (e != index) commands shouldBe empty
                    }
                }
                d.cycle(offered, Vector.tabulate(3)(_ == index)) shouldBe Vector.fill(4)(false)
                d.cycle(offered) shouldBe Vector.fill(4)(true)
                d.drain()
            }
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
            commands.zipWithIndex.foreach { case (command, i) =>
                d.cycle(d.offer((i % 4) -> command), Vector(true, true, true)).count(identity) shouldBe 1
            }
            d.drain()
        }
    }

    for ((lanes, depths) <- Vector(4 -> Vector(1, 2, 3), 4 -> Vector(5, 7, 9), 1 -> Vector(1, 3, 5))) {
        it should s"match an ordered software queue with $lanes lanes and depths ${depths.mkString("-")}" in {
            val cfg = QueenConfig(instructionLanes = lanes, gemmQueueDepth = depths(0),
                dmaQueueDepth = depths(1), schedQueueDepth = depths(2))
            simulate(new QueenDsaDecoder(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val rng = new Random(100 + depths.sum + lanes)
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

    it should "reject nonpositive endpoint FIFO depths" in {
        an[IllegalArgumentException] should be thrownBy QueenConfig(gemmQueueDepth = 0)
        an[IllegalArgumentException] should be thrownBy QueenConfig(dmaQueueDepth = 0)
        an[IllegalArgumentException] should be thrownBy QueenConfig(schedQueueDepth = 0)
    }
}
