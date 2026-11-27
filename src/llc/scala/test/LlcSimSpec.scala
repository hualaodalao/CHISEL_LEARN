import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.PeekPokeAPI
import chisel3.simulator.scalatest.ChiselSim

import scala.collection.mutable
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LlcSimSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator()

  private val small = LlcConfig(capacity = 8 * 1024)
  private val line = BigInt(0x1000)

  behavior of "S01 read baseline"

  it should "return downstream data on a read miss and hit on the second read" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line) shouldBe Vector(b.pat(line))
      b.memReads shouldBe 1
      b.read(line) shouldBe Vector(b.pat(line))
      b.memReads shouldBe 1
    }
  }

  it should "keep distinct lines resident" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val addrs = Vector[BigInt](0x1000, 0x1040, 0x1080)
      for (a <- addrs) b.read(a) shouldBe Vector(b.pat(a))
      b.memReads shouldBe 3
      for (a <- addrs) b.read(a) shouldBe Vector(b.pat(a))
      b.memReads shouldBe 3
    }
  }

  it should "refetch after invalidate" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line) shouldBe Vector(b.pat(line))
      b.memReads shouldBe 1
      b.flush(invalidate = true)
      b.read(line) shouldBe Vector(b.pat(line))
      b.memReads shouldBe 2
    }
  }

  it should "run the miss-then-hit baseline with the default config" in {
    val cfg = LlcConfig()
    simulate(new Llc(cfg)) { dut =>
      val b = new LlcAxiBench(dut, cfg)
      b.read(line) shouldBe Vector(b.pat(line))
      b.read(line) shouldBe Vector(b.pat(line))
      b.memReads shouldBe 1
    }
  }

  behavior of "S02 write-hit keeps both beats and byte strobes (K1)"

  private val full = (BigInt(1) << 32) - 1

  it should "write both beats of a resident line and flush them downstream" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line)
      val d0 = BigInt(0x11110000L)
      val d1 = BigInt(0x22220000L)
      b.write(line, Vector((d0, full), (d1, full))) shouldBe ((BigInt(0), 0))
      b.flush()
      b.memBeat(line) shouldBe d0
      b.memBeat(line + 32) shouldBe d1
    }
  }

  it should "apply a 1-byte strobe to one byte only" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line)
      val d1 = BigInt(0x22220000L)
      b.write(line, Vector((BigInt(0xDEADBEEFL), BigInt(1)), (d1, full))) shouldBe ((BigInt(0), 0))
      b.read(line, len = 1) shouldBe Vector((b.pat(line) & ~BigInt(0xFF)) | BigInt(0xEF), d1)
      b.flush()
      b.memBeat(line) shouldBe ((b.pat(line) & ~BigInt(0xFF)) | BigInt(0xEF))
      b.memBeat(line + 32) shouldBe d1
    }
  }

  behavior of "S03 line-internal offset from the address (K4/K5)"

  it should "read a single beat at line+32 on a miss and on a hit" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line + 32) shouldBe Vector(b.pat(line + 32))
      b.memReads shouldBe 1
      b.read(line) shouldBe Vector(b.pat(line))
      b.read(line + 32) shouldBe Vector(b.pat(line + 32))
      b.memReads shouldBe 1
    }
  }

  it should "place a single-beat write to line+32 in the second beat" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line)
      val d1 = BigInt(0x22220000L)
      b.startWrite(line + 32, Vector((d1, full)))
      for (_ <- 0 until 4) b.tick()
      b.read(line, len = 1) shouldBe Vector(b.pat(line), d1)
      b.waitB() shouldBe ((BigInt(0), 0))
      b.flush()
      b.memBeat(line) shouldBe b.pat(line)
      b.memBeat(line + 32) shouldBe d1
      b.memWrites shouldBe Vector(line)
    }
  }

  behavior of "S04 downstream first read beat is kept (K8)"

  it should "refill correctly when the first downstream beat follows AR immediately" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rLat = 1)
      b.read(line) shouldBe Vector(b.pat(line))
      b.memReads shouldBe 1
      b.read(line + 32) shouldBe Vector(b.pat(line + 32))
      b.memReads shouldBe 1
    }
  }

  behavior of "S05 request held in S2 is replayed after refill (K2)"

  it should "keep both beats of a full-line write miss" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d0 = BigInt(0x11110000L)
      val d1 = BigInt(0x22220000L)
      b.startWrite(line, Vector((d0, full), (d1, full)))
      b.waitB() shouldBe ((BigInt(0), 0))
      b.flush()
      b.memBeat(line) shouldBe d0
      b.memBeat(line + 32) shouldBe d1
    }
  }

  it should "return the written beat when a single-beat write miss is read back" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d1 = BigInt(0x22220000L)
      b.startWrite(line + 32, Vector((d1, full)))
      for (_ <- 0 until 4) b.tick()
      b.read(line, len = 1) shouldBe Vector(b.pat(line), d1)
      b.flush()
      b.memBeat(line) shouldBe b.pat(line)
      b.memBeat(line + 32) shouldBe d1
    }
  }

  it should "keep a full-line write miss that arrives behind a read miss on the same line" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d0 = BigInt(0x11110000L)
      val d1 = BigInt(0x22220000L)
      b.startWrite(line, Vector((d0, full), (d1, full)))
      // The read can overtake the write beats: its data is old and not asserted here
      b.read(line, len = 1)
      b.waitB() shouldBe ((BigInt(0), 0))
      b.read(line, len = 1) shouldBe Vector(d0, d1)
      b.flush()
      b.memBeat(line) shouldBe d0
      b.memBeat(line + 32) shouldBe d1
    }
  }

  behavior of "S06 victim selection avoids ways being refilled (K12)"

  // Five lines that map to set 0
  private val set0 = Vector[BigInt](0x1000, 0x1400, 0x1800, 0x1C00, 0x2000)

  it should "keep the first line when a second miss hits the same set during its refill" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rIdle = 20)
      b.read(set0(0)) shouldBe Vector(b.pat(set0(0)))
      b.read(set0(1)) shouldBe Vector(b.pat(set0(1)))
      b.memReads shouldBe 2
      b.read(set0(1)) shouldBe Vector(b.pat(set0(1)))
      b.memReads shouldBe 2
      b.read(set0(0)) shouldBe Vector(b.pat(set0(0)))
      b.memReads shouldBe 2
    }
  }

  it should "wait for a way when every way of the partition is being refilled" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rIdle = 60)
      for (a <- set0) b.read(a) shouldBe Vector(b.pat(a))
      b.memReads shouldBe 5
      for (a <- set0) b.read(a) shouldBe Vector(b.pat(a))
    }
  }

  behavior of "B response info is enqueued once per flush (K23)"

  it should "return one B per burst for consecutive full-line write misses in one set" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x11110000L)
      for ((a, i) <- set0.take(4).zipWithIndex) {
        b.write(a, Vector((d + i, full), (d + i, full))) shouldBe ((BigInt(0), 0))
      }
      for (_ <- 0 until 400) b.tick()
      b.waitBTimeout()
      b.flush()
      for ((a, i) <- set0.take(4).zipWithIndex) {
        b.memBeat(a) shouldBe d + i
        b.memBeat(a + 32) shouldBe d + i
      }
    }
  }

  behavior of "S07 a write burst is flushed once its last beat is accepted (K3)"

  it should "return B for a single-beat write and push it downstream on flush" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x33330000L)
      b.write(line, Vector((d, full))) shouldBe ((BigInt(0), 0))
      b.flush()
      b.memBeat(line) shouldBe d
      b.memBeat(line + 32) shouldBe b.pat(line + 32)
    }
  }

  it should "return one B per burst for two single-beat writes to different lines" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.write(line, Vector((BigInt(0x44440000L), full))) shouldBe ((BigInt(0), 0))
      b.write(line + 0x400, Vector((BigInt(0x55550000L), full))) shouldBe ((BigInt(0), 0))
      b.flush()
      b.memBeat(line) shouldBe BigInt(0x44440000L)
      b.memBeat(line + 0x400) shouldBe BigInt(0x55550000L)
    }
  }

  it should "not merge beats of two bursts to the same line across a B response" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d0 = BigInt(0x11110000L)
      val d1 = BigInt(0x22220000L)
      b.write(line, Vector((d0, full))) shouldBe ((BigInt(0), 0))
      b.write(line + 32, Vector((d1, full))) shouldBe ((BigInt(0), 0))
      b.read(line, len = 1) shouldBe Vector(d0, d1)
    }
  }

  it should "read back a single-beat write without hanging when the read follows the W beat at once" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x66660000L)
      b.read(line)
      b.startWrite(line, Vector((d, full)))
      b.read(line)
      b.waitB() shouldBe ((BigInt(0), 0))
      b.read(line) shouldBe Vector(d)
    }
  }

  behavior of "S07 snoop flush completes when the snooped entry is already being flushed (K13)"

  it should "not hang when a read snoops a full-line write that is already being flushed" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(line)
      // The entry closes three cycles after the last W beat; sweep the snoop phase so
      // one case has the auto-flush already running when the snoop request arrives
      for (idle <- 0 to 4) {
        val d0 = BigInt(0x11110000L) + idle
        val d1 = BigInt(0x22220000L) + idle
        b.startWrite(line, Vector((d0, full), (d1, full)))
        for (_ <- 0 until idle) b.tick()
        b.read(line)
        b.waitB() shouldBe ((BigInt(0), 0))
        b.read(line, len = 1) shouldBe Vector(d0, d1)
      }
    }
  }

  behavior of "S08 flush drains writes accepted before it (K7)"

  it should "write downstream a burst whose W beats arrive after the flush is triggered" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x77770000L)
      b.read(line)
      // AXI lets a master present W any time after AW; the burst is ordered
      // before the flush because its AW was accepted first
      b.aw(line, 1)
      b.startFlush()
      b.writeBeats(Vector((d, full)))
      b.waitFlushDone()
      b.waitB() shouldBe ((BigInt(0), 0))
      b.memBeat(line) shouldBe d
    }
  }

  it should "write downstream a burst whose W beats arrive after an invalidate is triggered" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x88880000L)
      b.read(line)
      b.aw(line, 1)
      b.startFlush(invalidate = true)
      b.writeBeats(Vector((d, full)))
      b.waitFlushDone()
      b.waitB() shouldBe ((BigInt(0), 0))
      b.memBeat(line) shouldBe d
    }
  }

  it should "write downstream a write issued right before the flush trigger" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x99990000L)
      b.read(line)
      b.startWrite(line, Vector((d, full)))
      b.flush()
      b.memBeat(line) shouldBe d
    }
  }

  behavior of "S07b the victim tag is invalidated at MSHR allocation (K22)"

  it should "keep a write that hits the line being evicted while it is being refilled" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rIdle = 80)
      val d = BigInt(0x11110000L)
      val e = BigInt(0x22220000L)
      for (a <- set0.take(4)) b.write(a, Vector((d, full), (d, full)))
      b.startRead(set0(4))
      for (a <- set0.take(4)) b.write(a, Vector((e, full), (e, full)))
      b.readR() shouldBe Vector(b.pat(set0(4)))
      b.flush()
      for (a <- set0.take(4)) b.memBeat(a) shouldBe e
    }
  }

  behavior of "S09 MSHR write-back and early response use handshakes (K11/K14)"

  it should "keep dirty victims when the write-back buffer is full (K11)" in {
    val cfg = small.copy(wbBufEntries = 2)
    simulate(new Llc(cfg)) { dut =>
      val b = new LlcAxiBench(dut, cfg, bLat = 100)
      val a = BigInt(0x33330000L)
      val c = BigInt(0x44440000L)
      val old = set0.take(4)
      val fresh = old.map(_ + 0x2000)
      for (x <- old) b.write(x, Vector((a, full), (a, full)))
      for (x <- fresh) b.startWrite(x, Vector((c, full), (c, full)))
      for (_ <- fresh) b.waitB()
      b.flush()
      for (x <- old) b.memBeat(x) shouldBe a
      for (x <- fresh) b.memBeat(x) shouldBe c
    }
  }

  it should "deliver a read miss whose early response coincides with a write hit (K14)" in {
    for (lat <- 0 to 8) simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rLat = lat)
      val d = BigInt(0x55550000L)
      val resident = set0(1)
      b.write(resident, Vector((d, full), (d, full)))
      b.startRead(set0(0))
      b.write(resident, Vector((d, full), (d, full)))
      b.readR() shouldBe Vector(b.pat(set0(0)))
    }
  }

  behavior of "S10 illegal burst"

  private val OKAY = 0
  private val SLVERR = 2

  it should "answer a write burst longer than a line with SLVERR after all W beats (K6)" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x11110000L)
      val target = set0(0)
      b.startWrite(target, Vector((d, full), (d + 0x10, full)), id = 1)
      b.startWrite(target, Vector((d + 1, full), (d + 2, full), (d + 3, full)), id = 2)
      val (id1, resp1) = b.waitB()
      val (id2, resp2) = b.waitB()
      (id1, resp1) shouldBe (BigInt(1), OKAY)
      (id2, resp2) shouldBe (BigInt(2), SLVERR)
      b.read(target) shouldBe Vector(d)
      b.read(target + 32) shouldBe Vector(d + 0x10)
      b.waitBTimeout()
    }
  }

  it should "count a first W beat taken in the AW cycle toward an illegal burst (K6)" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x33330000L)
      val target = set0(2)
      b.awWithBeat(target, 3, (d, full))
      b.writeBeats(Vector((d + 1, full), (d + 2, full)))
      b.waitB() shouldBe (BigInt(0), SLVERR)
      b.awWithBeat(target, 1, (d, full), size = 4)
      b.waitB() shouldBe (BigInt(0), SLVERR)
      b.read(target) shouldBe Vector(b.pat(target))
      b.memWrites shouldBe Vector.empty
      b.waitBTimeout()
    }
  }

  it should "answer a read burst leaving the line with SLVERR on every beat (K6)" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rLat = 0)
      val zero = BigInt(0)
      b.startRead(set0(0) + 32, len = 1, id = 3)
      b.readRFull(1) shouldBe Vector((zero, SLVERR, false), (zero, SLVERR, true))
      b.startRead(set0(1), len = 2, id = 4)
      b.readRFull(2) shouldBe Vector((zero, SLVERR, false), (zero, SLVERR, false), (zero, SLVERR, true))
      b.memReads shouldBe 0
      b.read(set0(0) + 32) shouldBe Vector(b.pat(set0(0) + 32))
    }
  }

  it should "answer non-INCR, narrow and unaligned bursts with SLVERR (K6)" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val zero = BigInt(0)
      val d = BigInt(0x22220000L)
      b.startRead(set0(0), burst = Axi4.burst.WRAP)
      b.readRFull(0) shouldBe Vector((zero, SLVERR, true))
      b.startRead(set0(0), size = 4)
      b.readRFull(0) shouldBe Vector((zero, SLVERR, true))
      b.startRead(set0(0) + 8)
      b.readRFull(0) shouldBe Vector((zero, SLVERR, true))
      b.startWrite(set0(0), Vector((d, full), (d, full)), burst = Axi4.burst.WRAP)
      b.waitB() shouldBe (BigInt(0), SLVERR)
      b.startWrite(set0(0), Vector((d, full)), size = 4)
      b.waitB() shouldBe (BigInt(0), SLVERR)
      b.startWrite(set0(0) + 8, Vector((d, full)))
      b.waitB() shouldBe (BigInt(0), SLVERR)
      b.memReads shouldBe 0
      b.memWrites shouldBe Vector.empty
      b.waitBTimeout()
      b.write(set0(0), Vector((d, full), (d, full)))
      b.read(set0(0)) shouldBe Vector(d)
      b.read(set0(0) + 32) shouldBe Vector(d)
    }
  }

  behavior of "S12 B response attribution and writeback attribution (K16/K17)"

  // Eight lines of set 0
  private val set0Lines = Vector.tabulate(8)(i => BigInt(0x1000) + i * 0x400)

  it should "return one B with the right id to each of several writes" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x11110000L)
      for ((id, a) <- Vector(3, 1, 4, 2).zip(set0Lines.take(4))) {
        b.startWrite(a, Vector((d + id, full), (d + id, full)), id = id)
      }
      for (_ <- 0 until 4) b.tick()
      for (id <- Vector(3, 1, 4, 2)) b.waitB() shouldBe ((BigInt(id), OKAY))
      b.waitBTimeout()
      for ((id, a) <- Vector(3, 1, 4, 2).zip(set0Lines.take(4))) {
        b.read(a) shouldBe Vector(d + id)
        b.read(a + 32) shouldBe Vector(d + id)
      }
    }
  }

  it should "deliver every B when the upstream holds b_ready low across several writes" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val d = BigInt(0x22220000L)
      b.bReady(false)
      for (i <- 1 to 4) b.startWrite(set0Lines(i - 1), Vector((d + i, full)), id = i)
      // Every entry of the B FIFO is now taken and the B of the first write is
      // still waiting on b_ready
      for (_ <- 0 until 30) b.tick()
      for (i <- 5 to 6) b.startWrite(set0Lines(i - 1), Vector((d + i, full)), id = i)
      for (_ <- 0 until 30) b.tick()
      b.bReady(true)
      for (i <- 1 to 6) b.waitB() shouldBe ((BigInt(i), OKAY))
      b.waitBTimeout()
      b.flush()
      for (i <- 1 to 6) b.memBeat(set0Lines(i - 1)) shouldBe d + i
    }
  }

  it should "resume the MSHR entry whose writeback completed, not the first one waiting" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, bLat = 40)
      val d = BigInt(0x33330000L)
      // Both partitions hold dirty lines, so every miss below evicts one of them
      val p0 = Vector.tabulate(4)(i => BigInt(0x1000) + i * 0x400)
      val p1 = Vector.tabulate(4)(i => BigInt(0x2000) + i * 0x400)
      for (a <- p0) b.write(a, Vector((d, full), (d, full)))
      for (a <- p1) b.write(a, Vector((d, full), (d, full)), id = 1)
      val w0 = BigInt(0x3000)
      val w1 = BigInt(0x3400)
      val w2 = BigInt(0x3800)
      val base = b.downstreamLog.size
      // Entry 0 (partition 0) and entry 1 (partition 1) both wait for a writeback
      b.startWrite(w0, Vector((d + 1, full)))
      b.startWrite(w1, Vector((d + 2, full)), id = 1)
      // Entry 0 finishes its refill while entry 1 still waits for its writeback;
      // this write takes the free entry 0 and also evicts a dirty line
      for (_ <- 0 until 60) b.tick()
      b.startWrite(w2, Vector((d + 3, full)))
      for (id <- Vector(0, 1, 0)) b.waitB() shouldBe ((BigInt(id), OKAY))
      for ((a, i) <- Vector(w0, w1, w2).zipWithIndex) b.read(a) shouldBe Vector(d + (i + 1))
      b.read(w0 + 32) shouldBe Vector(b.pat(w0 + 32))
      val ev = b.downstreamLog.drop(base)
      def firstRefill(a: BigInt): Int = ev.indexWhere(_.contains(s"mem AR addr=$a "))
      val r1 = firstRefill(w1)
      val r2 = firstRefill(w2)
      withClue(s"refill order r1=$r1 r2=$r2: ") { Seq(r1, r2) foreach (r => r should be >= 0) }
      // The second writeback to finish belongs to entry 1, so its refill comes
      // before the refill of the write that took entry 0's place
      r1 should be < r2
    }
  }

  behavior of "S13 performance counters (K9)"

  private val perfNames = Vector("readHit", "readMiss", "writeHit", "writeMiss", "dirtyEvict", "cleanEvict",
    "mshrFullStall", "wbBufFullStall", "writeMerge", "refill")

  private val PERF_BASE = 0x10
  private val PERF_CLR  = 0x90

  private def perf(b: LlcAxiBench): Vector[BigInt] = {
    val n = perfNames.size
    val v = Vector.tabulate(n)(i => b.ctrlRead(PERF_BASE + 8 * i) | (b.ctrlRead(PERF_BASE + 4 + 8 * i) << 32))
    b.ctrlWrite(PERF_CLR, 1)
    v
  }

  it should "count reads and refills of pure read traffic and nothing else" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.ctrlWrite(PERF_CLR, 1)
      b.read(set0Lines(0))
      b.read(set0Lines(0))
      b.read(set0Lines(1))
      b.read(set0Lines(1))
      val v = perf(b)
      println("S13 perf read: " + perfShow(v))
      withClue(perfShow(v)) {
        v(0) shouldBe 2
        v(1) shouldBe 2
        v(9) shouldBe 2
        v(9) shouldBe b.memReads
        for (n <- Seq(2, 3, 4, 5, 6, 7, 8)) v(n) shouldBe 0
      }
      b.flush()
      val vf = perf(b)
      println("S13 perf flush: " + perfShow(vf))
      for (n <- vf) n shouldBe 0
    }
  }

  it should "count a write that waits for an in-flight refill once, as a hit" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rLat = 20)
      b.ctrlWrite(PERF_CLR, 1)
      b.startRead(set0Lines(0))
      b.startWrite(set0Lines(0), Vector((BigInt(0x1234), full)))
      b.readR()
      b.waitB()
      b.read(set0Lines(0)) shouldBe Vector(BigInt(0x1234))
      val v = perf(b)
      println("S13 perf wait: " + perfShow(v))
      withClue(perfShow(v)) {
        v(1) shouldBe 1
        v(2) shouldBe 1
        v(0) shouldBe 1
        v(3) shouldBe 0
        v(9) shouldBe 1
      }
    }
  }

  // Requests carrying id 0 stay in way partition 0 (four ways), so the fifth
  // line of a set evicts. Track the requests so the counters can be checked
  // against what was actually issued.
  class PerfTrace(b: LlcAxiBench) {
    private var reads = 0
    private var wBeats = 0
    private var wMiss = 0
    private var base = (0, 0, 0)
    def rd(a: BigInt): Unit = { reads += 1; b.read(a) }
    def wr(a: BigInt, beats: (BigInt, BigInt)*): Unit = {
      val before = b.memReads
      wBeats += beats.size
      b.write(a, beats.toVector)
      wMiss += b.memReads - before
    }
    // Requests issued since the counters were last cleared
    private var baseMem = b.memReads
    def mark(): Unit = { base = (reads, wBeats, wMiss); baseMem = b.memReads }
    def memSince: Int = b.memReads - baseMem
    def since: (Int, Int, Int) = (reads - base._1, wBeats - base._2, wMiss - base._3)
  }

  private def perfShow(v: Vector[BigInt]): String = perfNames.zip(v.map(_.toString)).mkString(", ")

  // Counters are cleared on read, so each check covers the requests issued since
  // the previous one: (readHit, readMiss, writeHit, writeMiss, dirty, clean, merge, refill)
  private def checkPerf(b: LlcAxiBench, t: PerfTrace)(expect: (String, BigInt)*): Unit = {
    val v = perf(b)
    val (reads, wBeats, wMiss) = t.since
    val refills = t.memSince
    t.mark()
    val facts: Map[String, BigInt] = perfNames.zip(v).toMap
    println("S13 perf: " + perfShow(v) + s" issued reads=$reads wBeats=$wBeats wMiss=$wMiss")
    withClue(perfShow(v)) {
      v(0) + v(1) shouldBe reads
      v(2) + v(3) shouldBe wBeats
      v(9) shouldBe refills
      v(3) shouldBe wMiss
      for ((name, e) <- expect) facts(name) shouldBe e
    }
  }

  it should "count every issued request once as a hit or a miss" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val t = new PerfTrace(b)
      b.ctrlWrite(PERF_CLR, 1)
      t.mark()
      for (a <- set0Lines.take(4)) t.rd(a)
      t.rd(set0Lines(4))
      // All five miss; the fifth finds no invalid way in partition 0
      checkPerf(b, t)("readMiss" -> 5, "cleanEvict" -> 1, "dirtyEvict" -> 0)
      for (a <- set0Lines.take(4)) t.rd(a)
      t.rd(set0Lines(4))
      t.wr(set0Lines(0), (BigInt(0x1234), full))
      t.wr(set0Lines(1), (BigInt(0x1234), full), (BigInt(0x5678), full))
      for (i <- 5 until 8) t.rd(set0Lines(i))
      t.wr(set0Lines(0) + 0x40, (BigInt(0x77), full))
      checkPerf(b, t)("writeMerge" -> 1, "dirtyEvict" -> 1, "cleanEvict" -> 5)
      t.rd(set0Lines(0) + 0x40)
      b.flush()
      // The flush itself is not a request
      checkPerf(b, t)("readHit" -> 1)
    }
  }

  behavior of "S14 refill waits for the writeback of the same line (K18)"

  // id 0 keeps every line in way partition 0 (four ways), so the fifth line of a
  // set evicts; the dirty line written first is the one replaced.
  it should "refill a line only after its own earlier writeback has landed" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, bLat = 40)
      val resident = set0Lines.take(4)
      for (a <- resident) b.read(a)
      b.write(resident(0), Vector((BigInt(0xA000), full), (BigInt(0xB000), full)))
      // The read evicts the dirty line; the write to it misses while the eviction's
      // writeback is still waiting for its B, and refills the line
      b.startRead(set0Lines(4))
      b.startWrite(resident(0) + 32, Vector((BigInt(0xC000), full)))
      b.readR() shouldBe Vector(b.pat(set0Lines(4)))
      b.waitB()
      b.read(resident(0)) shouldBe Vector(BigInt(0xA000))
      b.read(resident(0) + 32) shouldBe Vector(BigInt(0xC000))
      b.flush()
      b.memBeat(resident(0)) shouldBe BigInt(0xA000)
    }
  }

  behavior of "S15 configurable write-merge buffer and B response tracking"

  it should "keep six writes with b_ready low when both buffers hold eight" in {
    val cfg = LlcConfig(capacity = 8 * 1024, wmBufEntries = 8, bFifoDepth = 8)
    simulate(new Llc(cfg)) { dut =>
      val b = new LlcAxiBench(dut, cfg)
      b.bReady(false)
      for (i <- 0 until 6) b.startWrite(set0Lines(i), Vector((BigInt(0x1000 + i), full)), id = i)
      b.bReady(true)
      for (i <- 0 until 6) b.waitB() shouldBe ((BigInt(i), 0))
      b.waitBTimeout()
      for (i <- 0 until 6) b.read(set0Lines(i)) shouldBe Vector(BigInt(0x1000 + i))
    }
  }

  it should "block the merge buffer on a full two-entry B FIFO without losing a B" in {
    val cfg = LlcConfig(capacity = 8 * 1024, wmBufEntries = 2, bFifoDepth = 2)
    simulate(new Llc(cfg)) { dut =>
      val b = new LlcAxiBench(dut, cfg)
      b.bReady(false)
      for (i <- 0 until 2) b.startWrite(set0Lines(i), Vector((BigInt(0x1000 + i), full)), id = i)
      for (_ <- 0 until 30) b.tick()
      b.bReady(true)
      for (i <- 2 until 6) b.startWrite(set0Lines(i), Vector((BigInt(0x1000 + i), full)), id = i)
      for (i <- 0 until 6) b.waitB() shouldBe ((BigInt(i), 0))
      b.waitBTimeout()
      for (i <- 0 until 6) b.read(set0Lines(i)) shouldBe Vector(BigInt(0x1000 + i))
    }
  }

  behavior of "S11 reset state"

  it should "behave as an empty cache after reset without invalidate (K15)" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      b.read(set0(0)) shouldBe Vector(b.pat(set0(0)))
      b.memReads shouldBe 1
      b.read(set0(0)) shouldBe Vector(b.pat(set0(0)))
      b.memReads shouldBe 1
      val d = BigInt(0x66660000L)
      b.write(set0(1), Vector((d, full), (d, full)))
      b.read(set0(1)) shouldBe Vector(d)
      b.read(set0(1) + 32) shouldBe Vector(d)
      b.flush()
      b.memWrites shouldBe Vector(set0(1))
      b.read(set0(1) + 32) shouldBe Vector(d)
    }
  }

  behavior of "S16 PLRU state in SRAM (D4)"

  it should "return the victim one cycle after the query with the two latest updates applied" in {
    val cfg = LlcConfig(capacity = 8 * 1024, ways = 4)
    simulate(new LlcPlru(cfg)) { dut =>
      val pp = new PeekPokeAPI {}
      import pp._
      val depth = 2
      def refUpdate(tree: Int, way: Int): Int = {
        var (t, node) = (tree, 0)
        for (k <- 0 until depth) {
          val bit = (way >> (depth - 1 - k)) & 1
          val m   = 1 << node
          t = if ((~bit & 1) == 1) t | m else t & ~m
          node = 2 * node + 1 + bit
        }
        t
      }
      def refVictim(tree: Int): Int = {
        var (node, v) = (0, 0)
        for (_ <- 0 until depth) {
          val b = (tree >> node) & 1
          v = (v << 1) | b
          node = 2 * node + 1 + b
        }
        v
      }
      // Drives one cycle; the pins hold their value until the next call
      def drive(query: Int, update: Option[(Int, Int)]): Unit = {
        dut.io.query.enable.poke(true.B)
        dut.io.query.set.poke(query.U)
        dut.io.update.enable.poke(update.isDefined.B)
        dut.io.update.set.poke(update.map(_._1).getOrElse(0).U)
        dut.io.update.way.poke(update.map(_._2).getOrElse(0).U)
      }
      val trees = mutable.Map((0 until 4).map((_ -> 0)): _*).withDefaultValue(0)
      // The SRAM content is undefined after reset: every node is written first
      for (set <- 0 until 4; way <- 0 until 4) {
        trees(set) = refUpdate(trees(set), way)
        drive(0, Some(set, way))
        dut.clock.step()
      }
      val rnd = new scala.util.Random(16)
      var pendingQuery = 0
      for (_ <- 0 until 60) {
        // The pins still carry the previous cycle's query and update: the
        // victim answers that query and includes the last two updates
        dut.io.queryResp.victimWay.expect(refVictim(trees(pendingQuery)).U)
        val update = if (rnd.nextBoolean()) Some((rnd.nextInt(4), rnd.nextInt(4))) else None
        update.foreach { case (set, way) => trees(set) = refUpdate(trees(set), way) }
        pendingQuery = rnd.nextInt(4)
        drive(pendingQuery, update)
        dut.clock.step()
      }
      // Updates to other sets leave the victim of a repeatedly queried set alone
      for (i <- 0 until 4) {
        dut.io.queryResp.victimWay.expect(refVictim(trees(pendingQuery)).U)
        drive(pendingQuery, Some((pendingQuery ^ 1, i)))
        dut.clock.step()
      }
      dut.io.queryResp.victimWay.expect(refVictim(trees(pendingQuery)).U)
    }
  }

  it should "deliver a read hit held by a refill completion (K27)" in {
    for (lat <- Seq(0, 2); idle <- 5 to 7) simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small, rLat = lat, rIdle = idle)
      val d = BigInt(0x55550000L)
      val resident = set0(1)
      b.write(resident, Vector((d, full), (d, full)))
      b.read(resident) shouldBe Vector(d)
      b.startRead(set0(0))
      b.readR() shouldBe Vector(b.pat(set0(0)))
      b.read(resident) shouldBe Vector(d)
      b.read(resident + 32) shouldBe Vector(d)
      b.memReads shouldBe 2
    }
  }

  it should "replace the least recently used line in a 4-way single-partition cache" in {
    val cfg = LlcConfig(capacity = 8 * 1024, ways = 4, wayPartitions = 1)
    simulate(new Llc(cfg)) { dut =>
      val b = new LlcAxiBench(dut, cfg)
      val l = Vector.tabulate(6)(i => BigInt(0x1000) + i * 0x800)
      def miss(a: BigInt, n: Int): Unit = {
        b.read(a) shouldBe Vector(b.pat(a))
        b.memReads shouldBe n
      }
      def hit(a: BigInt, n: Int): Unit = {
        b.read(a) shouldBe Vector(b.pat(a))
        b.memReads shouldBe n
      }
      for (i <- 0 until 4) miss(l(i), i + 1)
      miss(l(4), 5)
      for (i <- 1 until 4) hit(l(i), 5)
      miss(l(5), 6)
      miss(l(0), 7)
      miss(l(2), 8)
      miss(l(1), 9)
      miss(l(3), 10)
      miss(l(5), 11)
      for (i <- Vector(3, 2, 1)) hit(l(i), 11)
      miss(l(0), 12)
      miss(l(3), 13)
      miss(l(5), 14)
      for (i <- Vector(0, 3, 1)) hit(l(i), 14)
      val d = BigInt(0x77770000L)
      b.write(l(3) + 32, Vector((d, full)))
      b.read(l(3) + 32) shouldBe Vector(d)
      b.read(l(3)) shouldBe Vector(b.pat(l(3)))
      b.memReads shouldBe 14
      b.flush()
      b.memWrites shouldBe Vector(l(3))
    }
  }

  it should "read, write and flush with a 16-way config" in {
    val cfg = LlcConfig(capacity = 16 * 1024, ways = 16)
    simulate(new Llc(cfg)) { dut =>
      val b = new LlcAxiBench(dut, cfg)
      val l = Vector.tabulate(17)(i => BigInt(0x1000) + i * 0x1000)
      for (a <- l) b.read(a) shouldBe Vector(b.pat(a))
      b.memReads shouldBe 17
      for (a <- l) b.read(a) shouldBe Vector(b.pat(a))
      b.memReads should be >= 19
      val d = BigInt(0x88880000L)
      b.write(l(16), Vector((d, full), (d, full)))
      b.read(l(16) + 32) shouldBe Vector(d)
      b.read(l(0) + 32) shouldBe Vector(b.pat(l(0) + 32))
      b.flush()
      b.memWrites shouldBe Vector(l(16))
    }
  }

  behavior of "S18 flush walk jumps to the next way needing an action"

  it should "spend about two cycles per set on an empty cache" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val t0 = b.cycles
      b.startFlush()
      b.waitFlushDone()
      val n = b.cycles - t0
      // Two cycles per set plus the register access, drain and done polling
      n should be <= 60
    }
  }

  it should "write back only the dirty lines of a set in way order and keep the clean ones" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      // One line per way of set 0: id 0 fills ways 0-3, id 1 fills ways 4-7
      val addrs = Vector.tabulate(8)(i => line + i * 0x400)
      val dirty = Set(0, 3, 4, 7)
      val d = BigInt(0x12340000L)
      for (i <- 0 until 8; id = i / 4) {
        if (dirty(i)) b.write(addrs(i), Vector((d + i, full), (d + i + 1, full)), id)
        else b.read(addrs(i), id = id) shouldBe Vector(b.pat(addrs(i)))
      }
      // A write miss fetches the line before merging its beats into it
      b.memReads shouldBe 8
      val t0 = b.cycles
      b.startFlush()
      b.waitFlushDone()
      val n = b.cycles - t0
      // The old walk took 18 cycles per set whatever it held
      n should be <= 80
      b.memWrites shouldBe addrs.zipWithIndex.collect { case (a, i) if dirty(i) => a }
      for (i <- 0 until 8; id = i / 4) b.read(addrs(i), id = id)
      b.memReads shouldBe 8
    }
  }

  it should "also clear the clean lines between the dirty ones on invalidate" in {
    simulate(new Llc(small)) { dut =>
      val b = new LlcAxiBench(dut, small)
      val addrs = Vector.tabulate(8)(i => line + i * 0x400)
      val dirty = Set(1, 2, 5, 6)
      val d = BigInt(0x56780000L)
      for (i <- 0 until 8; id = i / 4) {
        if (dirty(i)) b.write(addrs(i), Vector((d + i, full), (d + i + 1, full)), id)
        else b.read(addrs(i), id = id)
      }
      b.flush(invalidate = true)
      b.memWrites shouldBe addrs.zipWithIndex.collect { case (a, i) if dirty(i) => a }
      // Every line must be fetched again; the dirty ones hold the data just written back
      for (i <- 0 until 8; id = i / 4) b.read(addrs(i), id = id)
      b.memReads shouldBe 16
    }
  }
}
