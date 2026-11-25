import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class QueenSyncStateSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private val cfg = QueenConfig()
    private val Mask32 = BigInt("ffffffff", 16)

    private case class Hw(group: Int = 0, mask: BigInt = 0, timeline: Int = 0,
                          isTimeline: Boolean = false, enable: Boolean = true)
    private case class Sw(isTimeline: Boolean = false, index: Int = 0, clr: Boolean = false,
                          assign: Boolean = false, data: BigInt = 0)

    private def s32(v: BigInt): BigInt = {
        val m = v & Mask32
        if (m >= (BigInt(1) << 31)) m - (BigInt(1) << 32) else m
    }

    private class Driver(dut: QueenSyncState) {
        private val events = Array.fill(cfg.eventGroupCount)(BigInt(0))
        private val timelines = Array.fill(cfg.timelineCount)(BigInt(0))
        var rollbacks = Vector.empty[Int]

        private def pokeHw(port: Valid[QueenDsaCompletion], c: Option[Hw]): Unit = {
            port.valid.poke(c.nonEmpty.B)
            port.bits.swTag.poke(0.U); port.bits.hwTag.poke(0.U)
            port.bits.error.poke(false.B); port.bits.code.poke(0.U)
            port.bits.hwSignal.enable.poke(c.exists(_.enable).B)
            port.bits.hwSignal.isTimeline.poke(c.exists(_.isTimeline).B)
            port.bits.hwSignal.group.poke(c.map(_.group).getOrElse(0).U)
            port.bits.hwSignal.mask.poke(c.map(_.mask).getOrElse(BigInt(0)).U)
            port.bits.hwSignal.timeline.poke(c.map(_.timeline).getOrElse(0).U)
        }

        dut.io.completion.foreach(pokeHw(_, None))
        dut.io.sw.valid.poke(false.B)
        dut.io.sw.isTimeline.poke(false.B)
        dut.io.sw.index.poke(0.U)
        dut.io.sw.clr.poke(false.B)
        dut.io.sw.assign.poke(false.B)
        dut.io.sw.data.poke(0.U)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        def cycle(gemm: Option[Hw] = None, dma: Option[Hw] = None, sw: Option[Sw] = None,
                  vpu: Option[Hw] = None): Unit = {
            pokeHw(dut.io.completion(0), gemm)
            pokeHw(dut.io.completion(1), dma)
            pokeHw(dut.io.completion(2), vpu)
            dut.io.sw.valid.poke(sw.nonEmpty.B)
            dut.io.sw.isTimeline.poke(sw.exists(_.isTimeline).B)
            dut.io.sw.index.poke(sw.map(_.index).getOrElse(0).U)
            dut.io.sw.clr.poke(sw.exists(_.clr).B)
            dut.io.sw.assign.poke(sw.exists(_.assign).B)
            dut.io.sw.data.poke(sw.map(_.data).getOrElse(BigInt(0)).U)

            for (g <- 0 until cfg.eventGroupCount) dut.io.events(g).expect(events(g).U)
            for (t <- 0 until cfg.timelineCount) dut.io.timelines(t).expect(timelines(t).U)

            val hw = Seq(gemm, dma, vpu).flatten.filter(_.enable)
            val swTl = sw.exists(_.isTimeline)
            val idx = sw.map(_.index).getOrElse(0)
            val rollback = swTl && sw.exists(_.assign) &&
                s32(sw.map(_.data).getOrElse(BigInt(0)) - timelines(idx & 7)) < 0
            dut.io.rollback.expect(rollback.B)
            if (rollback) rollbacks = rollbacks :+ (idx & 7)

            for (g <- 0 until cfg.eventGroupCount) {
                val hwSet = hw.filter(c => !c.isTimeline && c.group == g)
                    .foldLeft(BigInt(0))(_ | _.mask)
                val hit = sw.exists(s => !s.isTimeline && s.index == g)
                val swSet = if (hit && !sw.exists(_.clr)) sw.get.data else BigInt(0)
                val swClr = if (hit && sw.exists(_.clr)) sw.get.data else BigInt(0)
                events(g) = ((events(g) & ~swClr) | hwSet | swSet) & Mask32
            }
            for (t <- 0 until cfg.timelineCount) {
                val hwInc = hw.count(c => c.isTimeline && c.timeline == t)
                val swInc = if (swTl && !sw.exists(_.assign) && (idx & 7) == t) 1 else 0
                val assign = swTl && sw.exists(_.assign) && (idx & 7) == t && !rollback
                timelines(t) =
                    if (assign) sw.get.data & Mask32
                    else (timelines(t) + hwInc + swInc) & Mask32
            }
            dut.clock.step()
        }

        def event(g: Int): BigInt = dut.io.events(g).peek().litValue
        def timeline(t: Int): BigInt = dut.io.timelines(t).peek().litValue
    }

    behavior of "QueenSyncState"

    it should "merge two same-cycle event completions within and across groups" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(gemm = Some(Hw(group = 5, mask = 0x0f)), dma = Some(Hw(group = 5, mask = 0xf0)))
            d.event(5) shouldBe BigInt(0xff)
            d.cycle(gemm = Some(Hw(group = 1, mask = 0x1)), dma = Some(Hw(group = 2, mask = 0x2)))
            d.event(1) shouldBe BigInt(1)
            d.event(2) shouldBe BigInt(2)
        }
    }

    it should "absorb a VPU completion alongside GEMM and DMA in one cycle" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(gemm = Some(Hw(group = 7, mask = 0x01)),
                dma = Some(Hw(group = 7, mask = 0x02)),
                vpu = Some(Hw(group = 7, mask = 0x04)))
            d.event(7) shouldBe BigInt(0x07)
            d.cycle(gemm = Some(Hw(isTimeline = true, timeline = 3)),
                dma = Some(Hw(isTimeline = true, timeline = 3)),
                vpu = Some(Hw(isTimeline = true, timeline = 3)))
            d.timeline(3) shouldBe BigInt(3)
        }
    }

    it should "keep set priority when software clear and hardware set hit one cycle" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(sw = Some(Sw(index = 5, data = 0xff)))
            d.event(5) shouldBe BigInt(0xff)
            // 置位优先是逐位的：bit0..3 同拍被清又被置 ⟹ 保留；bit4..7 只被清 ⟹ 归零。
            d.cycle(gemm = Some(Hw(group = 5, mask = 0x0f)),
                sw = Some(Sw(index = 5, clr = true, data = 0xff)))
            d.event(5) shouldBe BigInt(0x0f)
            d.cycle(sw = Some(Sw(index = 5, clr = true, data = 0xff)))
            d.event(5) shouldBe BigInt(0)
        }
    }

    it should "add software and both hardware increments in one cycle" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(gemm = Some(Hw(isTimeline = true, timeline = 2)),
                dma = Some(Hw(isTimeline = true, timeline = 2)),
                sw = Some(Sw(isTimeline = true, index = 2)))
            d.timeline(2) shouldBe BigInt(3)
        }
    }

    it should "let an explicit assign win over a same-cycle hardware increment" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(sw = Some(Sw(isTimeline = true, index = 1, assign = true, data = 10)))
            d.cycle(gemm = Some(Hw(isTimeline = true, timeline = 1)),
                sw = Some(Sw(isTimeline = true, index = 1, assign = true, data = 20)))
            d.timeline(1) shouldBe BigInt(20)
        }
    }

    it should "suppress a rolling-back assign but still apply hardware increments" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(sw = Some(Sw(isTimeline = true, index = 3, assign = true, data = 100)))
            d.cycle(gemm = Some(Hw(isTimeline = true, timeline = 3)),
                sw = Some(Sw(isTimeline = true, index = 3, assign = true, data = 50)))
            d.timeline(3) shouldBe BigInt(101)
            d.rollbacks shouldBe Vector(3)
            // 回绕后向前写是合法的：判据是 signed 距离而非无符号大小。
            d.cycle(sw = Some(Sw(isTimeline = true, index = 3, assign = true,
                data = BigInt("7ffffff0", 16))))
            d.cycle(sw = Some(Sw(isTimeline = true, index = 3, assign = true,
                data = BigInt("ffffff00", 16))))
            d.timeline(3) shouldBe BigInt("ffffff00", 16)
            d.cycle(sw = Some(Sw(isTimeline = true, index = 3, assign = true, data = 5)))
            d.timeline(3) shouldBe BigInt(5)
            d.rollbacks.size shouldBe 1
        }
    }

    it should "ignore disabled signals and zero masks" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            d.cycle(gemm = Some(Hw(group = 7, mask = 0xff, enable = false)),
                dma = Some(Hw(isTimeline = true, timeline = 4, enable = false)))
            d.event(7) shouldBe BigInt(0)
            d.timeline(4) shouldBe BigInt(0)
            d.cycle(gemm = Some(Hw(group = 7, mask = 0)))
            d.event(7) shouldBe BigInt(0)
        }
    }

    it should "match a software model under random dual-source traffic" in {
        simulate(new QueenSyncState(cfg)) { dut =>
            val d = new Driver(dut)
            val rng = new Random(20261118)
            for (_ <- 0 until 500) {
                def hw: Option[Hw] =
                    if (rng.nextInt(3) == 0) None
                    else Some(Hw(group = rng.nextInt(32), mask = rng.nextInt(1 << 20),
                        timeline = rng.nextInt(8), isTimeline = rng.nextBoolean(),
                        enable = rng.nextInt(4) != 0))
                val sw =
                    if (rng.nextInt(3) == 0) None
                    else if (rng.nextBoolean())
                        Some(Sw(index = rng.nextInt(32), clr = rng.nextBoolean(),
                            data = rng.nextInt(1 << 20)))
                    else Some(Sw(isTimeline = true, index = rng.nextInt(8),
                        assign = rng.nextBoolean(), data = rng.nextInt(1 << 20)))
                d.cycle(hw, hw, sw)
            }
            d.rollbacks.nonEmpty shouldBe true
        }
    }
}