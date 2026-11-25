import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable
import scala.util.Random

class QueenQrdTrackerSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private case class Entry(pc: BigInt, rd: Int, source: Int)
    private case class Pop(pc: BigInt, rdValid: Boolean, rdIndex: Int, err: Boolean)

    private class Driver(dut: QueenQrdTracker, cfg: QueenConfig) {
        private val lanes = cfg.instructionLanes
        private val depth = cfg.qrdDepth
        private var entries = Vector.empty[Entry]
        private val fifos = Vector.fill(3)(mutable.Queue.empty[(BigInt, Boolean)])
        private val pendingResp = Array.fill(3)(0)
        var asyncReady = true
        var pops = Vector.empty[Pop]
        var rdErrors = Vector.empty[Int]
        var asyncWrites = Vector.empty[(Int, BigInt)]
        var allocCount = 0

        dut.io.alloc.foreach { a =>
            a.valid.poke(false.B); a.bits.pc.poke(0.U)
            a.bits.rdIndex.poke(0.U); a.bits.source.poke(0.U)
        }
        dut.io.resp.foreach { r => r.valid.poke(false.B); r.bits.data.poke(0.U); r.bits.err.poke(false.B) }
        dut.io.asyncRd.ready.poke(true.B)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def alloc(entries: (Int, Entry)*): Vector[Option[Entry]] =
            entries.foldLeft(Vector.fill[Option[Entry]](lanes)(None)) {
                case (v, (i, e)) => v.updated(i, Some(e))
            }

        private var cycleNo = 0

        def cycle(allocs: Vector[Option[Entry]] = Vector.fill(lanes)(None),
                  resp: Vector[Option[(BigInt, Boolean)]] = Vector.fill(3)(None)): Unit = {
            cycleNo += 1
            for (i <- 0 until lanes) {
                val a = dut.io.alloc(i)
                val e = allocs(i)
                a.valid.poke(e.nonEmpty.B)
                a.bits.pc.poke(e.map(_.pc).getOrElse(BigInt(0)).U)
                a.bits.rdIndex.poke(e.map(_.rd).getOrElse(0).U)
                a.bits.source.poke(e.map(_.source).getOrElse(0).U)
            }
            for (s <- 0 until 3) {
                val r = dut.io.resp(s)
                r.valid.poke(resp(s).nonEmpty.B)
                r.bits.data.poke(resp(s).map(_._1).getOrElse(BigInt(0)).U)
                r.bits.err.poke(resp(s).exists(_._2).B)
                r.ready.expect(true.B)
            }
            dut.io.asyncRd.ready.poke(asyncReady.B)
            dut.io.used.expect(entries.size.U)
            dut.io.idle.expect(entries.isEmpty.B)

            val head = entries.headOption
            val headResp = head.flatMap(e => fifos(e.source).headOption)
            val headReady = head.nonEmpty && headResp.nonEmpty
            val writeBack = head.exists(_.rd != 0) && headResp.exists(!_._2)
            val asyncValid = headReady && writeBack
            val pop = headReady && (!writeBack || asyncReady)
            dut.io.asyncRd.valid.expect(asyncValid.B)
            val expErr = pop && headResp.exists(_._2)
            dut.io.rdError.valid.expect(expErr.B)
            if (expErr) {
                dut.io.rdError.bits.expect(head.get.source.U)
                rdErrors = rdErrors :+ head.get.source
            }
            if (asyncValid) {
                dut.io.asyncRd.bits.addr.expect(head.get.rd.U)
                dut.io.asyncRd.bits.data.expect(headResp.get._1.U)
            }
            if (asyncValid && asyncReady) {
                asyncWrites = asyncWrites :+ ((head.get.rd, headResp.get._1))
            }
            if (pop) {
                pops = pops :+ Pop(head.get.pc, writeBack, head.get.rd, headResp.get._2)
                fifos(head.get.source).dequeue()
                entries = entries.tail
            }
            val pushed = allocs.flatten
            allocCount += pushed.size
            entries = entries ++ pushed
            pushed.foreach(e => pendingResp(e.source) += 1)
            for (s <- 0 until 3) resp(s).foreach { v =>
                fifos(s).enqueue(v)
                pendingResp(s) -= 1
            }
            dut.clock.step()
        }

        def respond(source: Int, data: BigInt, err: Boolean = false): Unit = {
            pendingResp(source) should be > 0
            cycle(resp = Vector.tabulate(3)(s => if (s == source) Some((data, err)) else None))
        }

        def settle(cycles: Int = 8): Unit = for (_ <- 0 until cycles) cycle()

        def used: Int = entries.size
        def pending(source: Int): Int = pendingResp(source)

        def drain(): Unit = {
            var guard = 0
            while (entries.nonEmpty && guard < 400) {
                val head = entries.head
                if (fifos(head.source).isEmpty && pendingResp(head.source) > 0) {
                    respond(head.source, BigInt(0))
                } else cycle()
                guard += 1
            }
            entries shouldBe empty
        }
    }

    behavior of "QueenQrdTracker"

    it should "pop a single source in request order" in {
        val cfg = QueenConfig()
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(0 -> Entry(0x100, 3, 0), 1 -> Entry(0x104, 4, 0)))
            d.respond(0, 0xaa)
            d.respond(0, 0xbb)
            d.settle(4)
            d.pops shouldBe Vector(Pop(0x100, true, 3, false), Pop(0x104, true, 4, false))
            d.asyncWrites shouldBe Vector((3, BigInt(0xaa)), (4, BigInt(0xbb)))
        }
    }

    it should "pop in program order when three sources respond out of order" in {
        val cfg = QueenConfig()
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(0 -> Entry(0x200, 1, 0), 1 -> Entry(0x204, 2, 2), 2 -> Entry(0x208, 3, 1)))
            d.respond(2, 0xcc)
            d.respond(1, 0xbb)
            d.settle(2)
            d.pops shouldBe empty
            d.respond(0, 0xaa)
            d.settle(4)
            d.pops shouldBe Vector(Pop(0x200, true, 1, false), Pop(0x204, true, 2, false),
                Pop(0x208, true, 3, false))
            d.asyncWrites shouldBe Vector((1, BigInt(0xaa)), (2, BigInt(0xcc)), (3, BigInt(0xbb)))
        }
    }

    it should "push four same-cycle allocations in lane order across a pointer wrap" in {
        val cfg = QueenConfig(qrdDepth = 5)
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            for (round <- 0 until 4) {
                val es = (0 until 4).map(i => Entry(0x300 + round * 16 + i * 4, i + 1, round % 3))
                d.cycle(d.alloc(es.zipWithIndex.map { case (e, i) => i -> e }: _*))
                d.drain()
            }
            d.allocCount shouldBe 16
            d.pops.map(_.pc) shouldBe (0 until 4).flatMap(r =>
                (0 until 4).map(i => BigInt(0x300 + r * 16 + i * 4)))
        }
    }

    it should "skip invalid lanes when compacting the allocation" in {
        val cfg = QueenConfig()
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(1 -> Entry(0x400, 5, 1), 3 -> Entry(0x404, 6, 2)))
            d.respond(2, 0x22)
            d.respond(1, 0x11)
            d.settle(4)
            d.pops shouldBe Vector(Pop(0x400, true, 5, false), Pop(0x404, true, 6, false))
        }
    }

    it should "hold the head while the CPU writeback port is backpressured" in {
        val cfg = QueenConfig()
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(0 -> Entry(0x500, 3, 0), 1 -> Entry(0x504, 4, 0)))
            d.asyncReady = false
            d.respond(0, 0xaa)
            d.respond(0, 0xbb)
            d.settle(4)
            d.pops shouldBe empty
            d.asyncWrites shouldBe empty
            d.asyncReady = true
            d.settle(4)
            d.pops.map(_.pc) shouldBe Vector(BigInt(0x500), BigInt(0x504))
        }
    }

    it should "hold the head while the writeback port is backpressured" in {
        val cfg = QueenConfig()
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(0 -> Entry(0x600, 5, 0)))
            d.asyncReady = false
            d.respond(0, 0xaa)
            d.settle(4)
            d.pops shouldBe empty
            d.asyncWrites shouldBe empty
            d.asyncReady = true
            d.settle(2)
            d.pops shouldBe Vector(Pop(0x600, true, 5, false))
        }
    }

    it should "pop rd=x0 without a writeback and report err on rdError instead of a trap" in {
        val cfg = QueenConfig()
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(0 -> Entry(0x700, 0, 0), 1 -> Entry(0x704, 7, 1)))
            d.respond(0, 0xaa)
            d.respond(1, 0xbb, err = true)
            d.settle(4)
            d.pops shouldBe Vector(Pop(0x700, false, 0, false), Pop(0x704, false, 7, true))
            d.asyncWrites shouldBe empty
            d.rdErrors shouldBe Vector(1)
        }
    }

    it should "report used and stop at the configured depth" in {
        val cfg = QueenConfig(qrdDepth = 3)
        simulate(new QueenQrdTracker(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(d.alloc(0 -> Entry(0x800, 1, 0), 1 -> Entry(0x804, 2, 0), 2 -> Entry(0x808, 3, 0)))
            dut.io.used.expect(3.U)
            d.respond(0, 0x11)
            dut.io.used.expect(3.U)
            d.settle(1)
            dut.io.used.expect(2.U)
            d.cycle(d.alloc(0 -> Entry(0x80c, 4, 0)))
            d.drain()
            d.pops.map(_.pc) shouldBe Vector(BigInt(0x800), BigInt(0x804), BigInt(0x808), BigInt(0x80c))
        }
    }

    for (depth <- Vector(1, 2, 5, 16)) {
        it should s"match an ordered software model with depth $depth under random traffic" in {
            val cfg = QueenConfig(qrdDepth = depth)
            simulate(new QueenQrdTracker(cfg)) { dut =>
                val d = new Driver(dut, cfg)
                val rng = new Random(3100 + depth)
                var pc = 0x1000
                for (_ <- 0 until 500) {
                    val room = depth - d.used
                    val allocs = Vector.tabulate(cfg.instructionLanes) { i =>
                        if (i < room && rng.nextInt(3) != 0) {
                            pc += 4
                            Some(Entry(pc, rng.nextInt(32), rng.nextInt(3)))
                        } else None
                    }
                    val resp = Vector.tabulate(3) { s =>
                        if (d.pending(s) > 0 && rng.nextInt(2) == 0) {
                            Some((BigInt(rng.nextInt(1 << 30)), rng.nextInt(8) == 0))
                        } else None
                    }
                    d.cycle(allocs, resp)
                    d.asyncReady = rng.nextInt(4) != 0
                    d.asyncReady = rng.nextInt(4) != 0
                }
                d.asyncReady = true
                d.asyncReady = true
                d.drain()
                d.allocCount shouldBe d.pops.size
                d.pops.map(_.pc) shouldBe (0 until d.allocCount).map(i => BigInt(0x1004 + i * 4))
            }
        }
    }

    it should "reject a depth below one entry" in {
        an[IllegalArgumentException] should be thrownBy QueenConfig(qrdDepth = 0)
    }
}