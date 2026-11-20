package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmCreditSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private def init(dut: LmTop, p: FabricParams): Unit = {
    for (i <- 0 until p.nMaster) {
      dut.io.master(i).valid.poke(false.B)
      dut.io.master(i).bits.write.poke(false.B)
      dut.io.master(i).bits.addr.poke(0.U)
      dut.io.master(i).bits.len.poke(0.U)
      dut.io.master(i).bits.src.poke(0.U)
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

  private def issue(dut: LmTop, m: Int, addr: Long = 0L): Unit = {
    dut.io.master(m).bits.write.poke(true.B)
    dut.io.master(m).bits.addr.poke(addr.U)
    dut.io.master(m).bits.src.poke(m.U)
    dut.io.master(m).bits.data.poke(0x1234L.U)
    dut.io.master(m).valid.poke(true.B)
    dut.clock.step()
    dut.io.master(m).valid.poke(false.B)
  }

  private def respond(dut: LmTop, j: Int, src: Int): Unit = {
    dut.io.slaveRsp(j).bits.src.poke(src.U)
    dut.io.slaveRsp(j).bits.data.poke(0x5678L.U)
    dut.io.slaveRsp(j).bits.err.poke(false.B)
    dut.io.slaveRsp(j).valid.poke(true.B)
    dut.clock.step()
    dut.io.slaveRsp(j).valid.poke(false.B)
  }

  behavior of "LmCreditMgr and LmSlotPool"

  it should "absorb a request into the VC queue while the slave is not ready" in {
    val p = FabricParams.simple("bp", 2, 2, 32, 64)
    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      dut.io.slave(0).ready.poke(false.B)
      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()

      // The request is queued and its credit is reserved, so the master can
      // keep injecting while the slave holds the request.
      dut.io.master(0).ready.expect(true.B)
      dut.io.slave(0).valid.expect(false.B)
      dut.io.perf.returnSlots(0).expect(1.U)

      dut.io.slave(0).ready.poke(true.B)
      dut.clock.step()
      dut.io.slave(0).valid.expect(true.B)
      dut.io.master(0).valid.poke(false.B)
    }
  }

  it should "exhaust return credits and resume after a response" in {
    val p = FabricParams
      .simple("c", 2, 2, 32, 64)
      .copy(masterReturnSlots = Seq(2, 2), slaveRespSlots = Seq(4, 4))

    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      issue(dut, 0)
      issue(dut, 0)
      dut.io.perf.returnSlots(0).expect(2.U)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.master(0).ready.expect(false.B)
      dut.io.slave(0).valid.expect(false.B)
      dut.io.master(0).valid.poke(false.B)

      respond(dut, 0, 0)
      dut.io.perf.returnSlots(0).expect(1.U)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      // The request was accepted during the cycle even though the credit
      // counter is full again afterwards (combinational output drops).
      dut.io.perf.returnSlots(0).expect(2.U)
      dut.io.master(0).valid.poke(false.B)
    }
  }

  it should "exhaust destination response slots independently of return credits" in {
    val p = FabricParams
      .simple("p", 2, 2, 32, 64)
      .copy(masterReturnSlots = Seq(8, 8), slaveRespSlots = Seq(2, 2))

    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      issue(dut, 0)
      issue(dut, 0)
      dut.io.perf.respSlots(0).expect(2.U)
      dut.io.perf.returnSlots(0).expect(2.U)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.master(0).ready.expect(false.B)
      dut.io.master(0).valid.poke(false.B)

      respond(dut, 0, 0)
      dut.io.perf.respSlots(0).expect(1.U)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      // Same observation rule: the pool is full again after accepting it.
      dut.io.perf.respSlots(0).expect(2.U)
      dut.io.master(0).valid.poke(false.B)
    }
  }

  it should "fire the P3 assertion on an unsolicited response" in {
    val p = FabricParams.simple("u", 2, 2, 32, 64)
    intercept[Throwable] {
      simulate(new LmTop(p)) { dut =>
        init(dut, p)
        dut.io.slaveRsp(0).bits.src.poke(0.U)
        dut.io.slaveRsp(0).bits.data.poke(0x1L.U)
        dut.io.slaveRsp(0).valid.poke(true.B)
        dut.clock.step()
      }
    }
  }
}
