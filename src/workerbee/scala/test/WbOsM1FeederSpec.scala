import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.util.Random

class WbOsM1FeederSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private case class Window(index: Int, words: Vector[BigInt], scale: BigInt) {
    def packed(bW: Int): BigInt = words.zipWithIndex.foldLeft(BigInt(0)) {
      case (acc, (word, lane)) => acc | (word << (lane * bW))
    }
  }

  private def window(index: Int, t: Int = 16, bW: Int = 16, scaleBW: Int = 32): Window = {
    val wordMask = (BigInt(1) << bW) - 1
    val scaleMask = (BigInt(1) << scaleBW) - 1
    Window(index, Vector.tabulate(t)(lane => BigInt((index + 1) * 257 ^ lane * 193) & wordMask),
      (BigInt(index + 1) * BigInt("01020305", 16)) & scaleMask)
  }

  private class Driver(dut: WbOsM1Feeder, val t: Int = 16, bW: Int = 16,
                       ktW: Int = 16, hasMx: Boolean = true, mx: Boolean = false) {
    val queues: Array[mutable.Queue[Window]] = Array.fill(t)(mutable.Queue.empty[Window])
    val lanes: Array[Int] = Array.fill(t)(0)
    val consumed: Array[mutable.ArrayBuffer[(Int, Int)]] =
      Array.fill(t)(mutable.ArrayBuffer.empty[(Int, Int)])
    var accepted = 0
    private var cycle = 0
    private val ktMask = (BigInt(1) << ktW) - 1

    dut.io.bIn.valid.poke(false.B)
    dut.io.bIn.payload.poke(0.U)
    dut.io.scaleBIn.foreach { in =>
      in.valid.poke(false.B)
      in.payload.poke(0.U)
    }
    dut.io.mxMode.poke(mx.B)
    dut.io.flush.poke(false.B)
    dut.io.consume.foreach(_.poke(false.B))
    dut.reset.poke(true.B)
    dut.clock.step(3)
    dut.reset.poke(false.B)
    dut.clock.step(2)
    check()

    def nonempty: Set[Int] = (0 until t).filter(queues(_).nonEmpty).toSet

    private def check(): Unit = {
      for (j <- 0 until t) {
        val label = s"拍 $cycle 列 $j"
        assert(dut.io.headValid(j).peek().litToBoolean == queues(j).nonEmpty, s"$label headValid")
        assert(dut.io.laneOut(j).peek().litValue == lanes(j), s"$label lane")
        if (queues(j).nonEmpty) {
          val head = queues(j).head
          assert(dut.io.bOut(j).peek().litValue == head.words(lanes(j)), s"$label B 字/窗口顺序")
          assert(dut.io.scaleOut(j).peek().litValue == (if (mx) head.scale else BigInt(0)),
            s"$label B/scale 配对")
          assert(dut.io.headKt(j).peek().litValue == (BigInt(head.index / t) & ktMask),
            s"$label K 窗口标识")
        }
      }
    }

    def tick(b: Option[Window] = None, scale: Option[BigInt] = None,
             consume: Set[Int] = Set.empty, flush: Boolean = false): Boolean = {
      require(flush || consume.forall(j => queues(j).nonEmpty), "消费列必须已有有效窗口")
      b.foreach(w => require(w.index == accepted, "输入窗口必须按自然列序连续提供"))
      dut.io.bIn.valid.poke(b.nonEmpty.B)
      dut.io.bIn.payload.poke(b.map(_.packed(bW)).getOrElse(BigInt(0)).U)
      dut.io.scaleBIn.foreach { in =>
        in.valid.poke(scale.nonEmpty.B)
        in.payload.poke(scale.getOrElse(BigInt(0)).U)
      }
      dut.io.flush.poke(flush.B)
      for (j <- 0 until t) dut.io.consume(j).poke(consume.contains(j).B)
      check()

      val j = accepted % t
      val releasing = consume.contains(j) && lanes(j) == t - 1
      val fire = !flush && b.nonEmpty && (!mx || scale.nonEmpty) &&
        (queues(j).size < 2 || releasing)
      assert(dut.io.bIn.ready.peek().litToBoolean == fire, s"拍 $cycle B 握手")
      dut.io.scaleBIn.foreach { in =>
        assert(in.ready.peek().litToBoolean == (mx && fire), s"拍 $cycle scale 握手")
      }
      if (flush) {
        queues.foreach(_.clear())
        lanes.indices.foreach(lanes(_) = 0)
        consumed.foreach(_.clear())
        accepted = 0
      } else {
        for (col <- consume) {
          consumed(col) += ((queues(col).head.index, lanes(col)))
          if (lanes(col) == t - 1) {
            queues(col).dequeue()
            lanes(col) = 0
          } else lanes(col) += 1
        }
        if (fire) {
          val next = b.get.copy(scale = if (mx) scale.get else BigInt(0))
          queues(j).enqueue(next)
          accepted += 1
        }
      }
      dut.clock.step(1)
      cycle += 1
      check()
      fire
    }

    def fill(n: Int): Unit = {
      for (_ <- 0 until n) {
        val next = window(accepted, t, bW)
        assert(tick(Some(next), if (mx) Some(next.scale) else None), "窗口填充应成功")
      }
    }

    def drain(): Unit = {
      var steps = 0
      while (nonempty.nonEmpty && steps < accepted * t + 1) {
        tick(consume = nonempty)
        steps += 1
      }
      assert(nonempty.isEmpty, "所有窗口应排空")
      for (j <- 0 until t) {
        val expected = (j until accepted by t).flatMap(i => (0 until t).map(l => (i, l)))
        assert(consumed(j).toVector == expected.toVector, s"列 $j 必须逐窗口逐 lane 恰消费一次")
      }
    }
  }

  behavior of "WbOsM1Feeder window queues"

  it should "hold zero, one and two windows per column and backpressure the full target column" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut)
      d.tick()
      d.fill(16)
      assert(d.queues.forall(_.size == 1))
      d.fill(16)
      assert(d.queues.forall(_.size == 2))
      for (_ <- 0 until 5) assert(!d.tick(Some(window(32))))
      d.tick(consume = Set(1))
      assert(!d.tick(Some(window(32))), "其他列消费不能绕过满目标列")
      d.drain()
    }
  }

  it should "consume every low-to-high lane of head then next without empty-window bypass" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut)
      d.fill(32)
      for (_ <- 0 until 16) d.tick(consume = Set(0))
      assert(d.queues(0).head.index == 16 && d.lanes(0) == 0)
      for (_ <- 0 until 7) d.tick()
      for (_ <- 0 until 16) d.tick(consume = Set(0))
      assert(d.queues(0).isEmpty)
      assert(d.tick(Some(window(32))))
      assert(d.lanes(0) == 0 && d.queues(0).head.index == 32)
      d.drain()
    }
  }

  it should "read old lane fifteen during simultaneous pop and push from both one and two occupied slots" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut)
      d.fill(32)
      for (_ <- 0 until 15) {
        assert(!d.tick(Some(window(32)), consume = Set(0)))
      }
      assert(d.tick(Some(window(32)), consume = Set(0)))
      assert(d.queues(0).map(_.index).toVector == Vector(16, 32))
      for (_ <- 0 until 16) d.tick(consume = (1 until 16).toSet)
      d.fill(15)
      for (_ <- 0 until 16) d.tick(consume = Set(0))
      assert(d.queues(0).size == 1)
      for (_ <- 0 until 15) d.tick(consume = Set(0))
      assert(d.tick(Some(window(48)), consume = Set(0)))
      assert(d.queues(0).map(_.index).toVector == Vector(48))
      d.drain()
    }
  }

  it should "pair MX B and scale atomically when either source is independently delayed" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut, mx = true)
      for (_ <- 0 until 32) {
        val w = window(d.accepted)
        for (_ <- 0 until 3) assert(!d.tick(Some(w)))
        for (_ <- 0 until 2) assert(!d.tick(scale = Some(w.scale)))
        assert(d.tick(Some(w), Some(w.scale)))
      }
      val blocked = window(32)
      assert(!d.tick(Some(blocked), Some(blocked.scale)))
      for (_ <- 0 until 15) d.tick(consume = Set(0))
      assert(!d.tick(Some(blocked), consume = Set(0)))
      assert(d.tick(Some(blocked), Some(blocked.scale)))
      d.drain()
    }
  }

  it should "ignore scale validity outside MX and never return a scale credit" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut)
      for (_ <- 0 until 32) {
        val w = window(d.accepted)
        assert(d.tick(Some(w), if (w.index % 2 == 0) Some(w.scale) else None))
      }
      d.drain()
    }
  }

  it should "elaborate without an MX port and preserve parameterized lane and K identifier widths" in {
    simulate(new WbOsM1Feeder(T = 4, bW = 8, scaleBW = 16, ktW = 2, hasMx = false)) { dut =>
      val d = new Driver(dut, t = 4, bW = 8, ktW = 2, hasMx = false)
      for (_ <- 0 until 6) {
        d.fill(8)
        d.drain()
      }
      assert(d.accepted == 48)
    }
  }

  it should "keep natural column ordering under interleaved consumption and continuous N tile prefetch" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut, mx = true)
      val rng = new Random(20261105L)
      val totalWindows = 8 * 16
      var cycles = 0
      while (d.accepted < totalWindows && cycles < 4000) {
        val w = window(d.accepted)
        val consumers = d.nonempty.filter(j => rng.nextInt(4) != 0)
        d.tick(Some(w), Some(w.scale), consumers)
        cycles += 1
      }
      assert(d.accepted == totalWindows, "跨 N tile 预取不得丢窗或停死")
      d.drain()
    }
  }

  it should "flush partially consumed slots and reset dispatch while suppressing concurrent upstream fires" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut, mx = true)
      d.fill(32)
      for (_ <- 0 until 5) d.tick(consume = (0 until 16).toSet)
      assert(!d.tick(Some(window(32)), Some(window(32).scale), (0 until 16).toSet, flush = true))
      assert(d.accepted == 0 && d.nonempty.isEmpty)
      d.fill(32)
      d.drain()
      d.tick(flush = true)
      d.fill(17)
      assert(!d.tick(Some(window(17)), Some(window(17).scale), d.nonempty, flush = true))
      assert(d.accepted == 0 && d.nonempty.isEmpty)
      d.fill(16)
      assert(d.queues(0).head.index == 0)
      d.drain()
    }
  }

  it should "match an input-derived queue model with persistent sources and randomized B, scale and column stalls" in {
    simulate(new WbOsM1Feeder()) { dut =>
      val d = new Driver(dut, mx = true)
      for (seed <- Seq(19L, 71L, 20261105L)) {
        d.tick(flush = true)
        val rng = new Random(seed)
        val totalWindows = 12 * 16
        var pendingB: Option[Window] = None
        var pendingScale: Option[BigInt] = None
        var cycles = 0
        var stalled = 0
        while (d.accepted < totalWindows && cycles < 6000) {
          val w = window(d.accepted)
          if (pendingB.isEmpty && rng.nextInt(4) == 0) pendingB = Some(w)
          if (pendingScale.isEmpty && rng.nextInt(5) == 0) pendingScale = Some(w.scale)
          val consumers = d.nonempty.filter(_ => rng.nextInt(3) == 0)
          if (d.tick(pendingB, pendingScale, consumers)) {
            pendingB = None
            pendingScale = None
          } else stalled += 1
          cycles += 1
        }
        assert(d.accepted == totalWindows, s"随机种子 $seed 输入必须全部接收")
        assert(stalled > 0, "随机场景应覆盖停顿")
        d.drain()
      }
    }
  }
}
