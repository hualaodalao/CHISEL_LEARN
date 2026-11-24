import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Random

class QueenMemMasterSpec extends AnyFlatSpec with Matchers with ChiselSim {
    implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
        verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

    private case class Req(op: Int, addr: BigInt, operand: BigInt)
    private case class Resp(data: BigInt, error: Boolean)

    private class Driver(dut: QueenMemMaster, cfg: QueenConfig) {
        private var inflight = Vector.empty[Boolean]
        var reqReady = true
        var issued = Vector.empty[Req]
        var reads = Vector.empty[BigInt]
        var errs = 0

        dut.io.req.valid.poke(false.B)
        dut.io.req.bits.op.poke(QueenMemOp.READ)
        dut.io.req.bits.address.poke(0.U)
        dut.io.req.bits.operand.poke(0.U)
        dut.io.memReq.ready.poke(true.B)
        dut.io.memResp.valid.poke(false.B)
        dut.io.memResp.bits.data.poke(0.U)
        dut.io.memResp.bits.error.poke(false.B)
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)

        private def opOf(i: Int): QueenMemOp.Type = i match {
            case 0 => QueenMemOp.READ
            case 1 => QueenMemOp.ATOMIC_ADD
            case 2 => QueenMemOp.ATOMIC_CLR
            case _ => QueenMemOp.ATOMIC_SET
        }

        def cycle(req: Option[Req] = None, resp: Option[Resp] = None): Boolean = {
            dut.io.req.valid.poke(req.nonEmpty.B)
            req.foreach { r =>
                dut.io.req.bits.op.poke(opOf(r.op))
                dut.io.req.bits.address.poke(r.addr.U)
                dut.io.req.bits.operand.poke(r.operand.U)
            }
            dut.io.memReq.ready.poke(reqReady.B)
            dut.io.memResp.valid.poke(resp.nonEmpty.B)
            resp.foreach { r =>
                dut.io.memResp.bits.data.poke(r.data.U)
                dut.io.memResp.bits.error.poke(r.error.B)
            }

            val room = inflight.size < cfg.memDepth
            val expValid = req.nonEmpty && room
            dut.io.memReq.valid.expect(expValid.B)
            dut.io.req.ready.expect((reqReady && room).B)
            dut.io.used.expect(inflight.size.U)
            dut.io.idle.expect(inflight.isEmpty.B)
            dut.io.memResp.ready.expect(true.B)
            val fire = expValid && reqReady
            if (fire) {
                val r = req.get
                val act = (dut.io.memReq.bits.op.peek().litValue.toInt,
                    dut.io.memReq.bits.address.peek().litValue,
                    dut.io.memReq.bits.operand.peek().litValue)
                act shouldBe ((r.op, r.addr, r.operand))
                issued = issued :+ r
            }

            resp.foreach { r =>
                inflight should not be empty
                val isRead = inflight.head
                dut.io.rdData.valid.expect(isRead.B)
                dut.io.err.expect(r.error.B)
                if (isRead) {
                    dut.io.rdData.bits.expect(r.data.U)
                    reads = reads :+ r.data
                }
                if (r.error) errs += 1
                inflight = inflight.drop(1)
            }
            if (resp.isEmpty) {
                dut.io.rdData.valid.expect(false.B)
                dut.io.err.expect(false.B)
            }
            if (fire) inflight = inflight :+ (req.get.op == 0)
            dut.clock.step()
            fire
        }

        def outstanding: Int = inflight.size

        def drain(responses: Vector[Resp]): Unit = {
            responses.foreach(r => cycle(resp = Some(r)))
            while (inflight.nonEmpty) cycle(resp = Some(Resp(0, false)))
            dut.io.idle.expect(true.B)
        }
    }

    behavior of "QueenMemMaster"

    it should "pass requests through and route only READ results to the comparator" in {
        val cfg = QueenConfig()
        simulate(new QueenMemMaster(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(Req(0, 0x1000, 0)))
            d.cycle(Some(Req(1, 0x2000, 7)))
            d.cycle(resp = Some(Resp(0xabc, false)))
            d.cycle(resp = Some(Resp(0xdef, false)))
            d.reads shouldBe Vector(BigInt(0xabc))
            d.issued shouldBe Vector(Req(0, 0x1000, 0), Req(1, 0x2000, 7))
            dut.io.idle.expect(true.B)
        }
    }

    it should "allow memDepth transactions in flight and then backpressure" in {
        val cfg = QueenConfig()
        simulate(new QueenMemMaster(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            for (i <- 0 until cfg.memDepth) {
                d.cycle(Some(Req(1, 0x3000 + 4 * i, i))) shouldBe true
            }
            d.cycle(Some(Req(0, 0x4000, 0))) shouldBe false
            d.issued.size shouldBe cfg.memDepth
            d.cycle(resp = Some(Resp(0, false)))
            d.cycle(Some(Req(0, 0x4000, 0))) shouldBe true
            d.drain(Vector.fill(cfg.memDepth)(Resp(0x55, false)))
            d.issued.size shouldBe cfg.memDepth + 1
        }
    }

    it should "hold the request when the bus refuses it" in {
        val cfg = QueenConfig()
        simulate(new QueenMemMaster(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.reqReady = false
            for (_ <- 0 until 4) d.cycle(Some(Req(2, 0x5000, 3))) shouldBe false
            d.issued shouldBe empty
            d.reqReady = true
            d.cycle(Some(Req(2, 0x5000, 3))) shouldBe true
            d.drain(Vector(Resp(0, false)))
        }
    }

    it should "report a bus error on any response and stay in order" in {
        val cfg = QueenConfig()
        simulate(new QueenMemMaster(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(Req(0, 0x6000, 0)))
            d.cycle(Some(Req(3, 0x6004, 1)))
            d.cycle(Some(Req(0, 0x6008, 0)))
            d.cycle(resp = Some(Resp(0x11, false)))
            d.cycle(resp = Some(Resp(0, true)))
            d.cycle(resp = Some(Resp(0x22, false)))
            d.reads shouldBe Vector(BigInt(0x11), BigInt(0x22))
            d.errs shouldBe 1
            dut.io.idle.expect(true.B)
        }
    }

    it should "accept a response in the same cycle it issues a request" in {
        val cfg = QueenConfig()
        simulate(new QueenMemMaster(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            d.cycle(Some(Req(0, 0x7000, 0)))
            for (i <- 0 until 20) {
                d.cycle(Some(Req(i % 4, 0x7000 + 4 * i, i)), Some(Resp(i, false)))
            }
            d.cycle(resp = Some(Resp(99, false)))
            d.issued.size shouldBe 21
            dut.io.idle.expect(true.B)
        }
    }

    it should "match an ordered model under random traffic" in {
        val cfg = QueenConfig(memDepth = 5)
        simulate(new QueenMemMaster(cfg)) { dut =>
            val d = new Driver(dut, cfg)
            val rng = new Random(9182)
            var pending = Option.empty[Req]
            var nextAddr = 0
            for (_ <- 0 until 600) {
                if (pending.isEmpty && rng.nextInt(3) != 0) {
                    pending = Some(Req(rng.nextInt(4), 0x8000 + 4 * nextAddr, rng.nextInt(1 << 20)))
                    nextAddr += 1
                }
                d.reqReady = rng.nextInt(4) != 0
                val resp = if (d.outstanding > 0 && rng.nextInt(2) == 0)
                    Some(Resp(rng.nextInt(1 << 20), rng.nextInt(8) == 0)) else None
                if (d.cycle(pending, resp)) pending = None
            }
            while (pending.nonEmpty) {
                d.reqReady = true
                if (d.cycle(pending)) pending = None
            }
            d.drain(Vector.empty)
            d.issued.size shouldBe nextAddr
            d.issued.count(_.op == 0) shouldBe d.reads.size
        }
    }
}