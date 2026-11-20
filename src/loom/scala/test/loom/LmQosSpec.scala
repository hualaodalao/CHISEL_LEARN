package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.LoomDefaults
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmQosSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val Ctl = 0
  private val Lat = 1
  private val Bulk = 2

  private def init(dut: LmTop, p: FabricParams): Unit = {
    for (i <- 0 until p.nMaster) {
      dut.io.master(i).valid.poke(false.B)
      dut.io.master(i).bits.write.poke(true.B)
      dut.io.master(i).bits.addr.poke(0.U)
      dut.io.master(i).bits.len.poke(0.U)
      dut.io.master(i).bits.src.poke(i.U)
      dut.io.master(i).bits.data.poke(0.U)
      dut.io.masterRsp(i).ready.poke(true.B)
    }
    for (j <- 0 until p.nSlave) {
      dut.io.slave(j).ready.poke(true.B)
      dut.io.slaveRsp(j).valid.poke(false.B)
      dut.io.slaveRsp(j).bits.src.poke(0.U)
      dut.io.slaveRsp(j).bits.data.poke(0.U)
      dut.io.slaveRsp(j).bits.err.poke(false.B)
    }
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  behavior of "LmWrrArbiter (QoS classes)"

  it should "serve ctl:bulk at 4:1 while lat is idle" in {
    val p = FabricParams
      .simple("wrr", 2, 2, 32, 64,
        masterClass = Seq(Ctl, Bulk),
        classWeights = Seq(4, 2, 1))
      .copy(masterReturnSlots = Seq(16, 16), slaveRespSlots = Seq(32, 32))

    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      dut.io.master(0).valid.poke(true.B)
      dut.io.master(1).valid.poke(true.B)

      val grants = (0 until 5).map { _ =>
        dut.clock.step()
        dut.io.slave(0).bits.src.peek().litValue.toInt
      }

      // The post-step sample is the next combinational grant; over a full
      // 7-slot schedule with lat idle that is 4 ctl and 1 bulk.
      grants.count(_ == 0) shouldBe 4
      grants.count(_ == 1) shouldBe 1
    }
  }

  it should "age a starved ctl class past a bulk-heavy schedule" in {
    val p = FabricParams
      .simple("age", 2, 2, 32, 64,
        masterClass = Seq(Ctl, Bulk),
        classWeights = Seq(1, 1, 15))
      .copy(masterReturnSlots = Seq(16, 16), slaveRespSlots = Seq(32, 32))

    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      dut.io.master(1).valid.poke(true.B)
      for (_ <- 0 until 3) dut.clock.step()

      // Start ctl just after its schedule slot; without aging it would wait
      // 12 bulk slots before the schedule wraps back to ctl.
      dut.io.master(0).valid.poke(true.B)
      var waited = 0
      var served = false
      while (!served && waited < 12) {
        dut.clock.step()
        waited += 1
        served = dut.io.perf.returnSlots(0).peek().litValue > 0
      }

      served shouldBe true
      waited should be <= (LoomDefaults.MaxAge + 2)
    }
  }
}
