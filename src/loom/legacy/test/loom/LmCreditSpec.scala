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
      dut.io.masterRdReq(i).valid.poke(false.B)
      dut.io.masterRdReq(i).bits.src.poke(i.U)
      dut.io.masterWrData(i).valid.poke(false.B)
      dut.io.masterWrData(i).bits.src.poke(i.U)
      dut.io.masterRdData(i).ready.poke(true.B)
      dut.io.masterWrResp(i).ready.poke(true.B)
    }
    for (j <- 0 until p.nSlave) {
      dut.io.slaveRdReq(j).ready.poke(true.B)
      dut.io.slaveWrData(j).ready.poke(true.B)
      dut.io.slaveRdData(j).valid.poke(false.B)
      dut.io.slaveWrResp(j).valid.poke(false.B)
    }
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  private def issue(dut: LmTop, m: Int, addr: Long = 0L): Unit = {
    dut.io.masterWrData(m).bits.addr.poke(addr.U)
    dut.io.masterWrData(m).bits.data.poke(0x1234L.U)
    dut.io.masterWrData(m).valid.poke(true.B)
    dut.clock.step()
    dut.io.masterWrData(m).valid.poke(false.B)
  }

  private def respond(dut: LmTop, j: Int, src: Int): Unit = {
    dut.io.slaveWrResp(j).bits.src.poke(src.U)
    dut.io.slaveWrResp(j).bits.err.poke(false.B)
    dut.io.slaveWrResp(j).valid.poke(true.B)
    dut.clock.step()
    dut.io.slaveWrResp(j).valid.poke(false.B)
    // One more edge: the response leaves the port merge stage and the return
    // slot is released.
    dut.clock.step()
  }

  behavior of "LmCreditMgr and LmSlotPool"

  it should "absorb a request into the VC queue while the slave is not ready" in {
    val p = FabricParams.simple("bp", 2, 2, 32, 64)
    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      dut.io.slaveWrData(0).ready.poke(false.B)
      dut.io.masterWrData(0).bits.addr.poke(0.U)
      dut.io.masterWrData(0).valid.poke(true.B)
      dut.clock.step()

      // The request is queued and its credit is reserved, so the master can
      // keep injecting while the slave holds the request.
      dut.io.masterWrData(0).ready.expect(true.B)
      dut.io.slaveWrData(0).valid.expect(false.B)
      dut.io.perf.returnSlots(0).expect(1.U)

      dut.io.slaveWrData(0).ready.poke(true.B)
      dut.clock.step()
      dut.io.slaveWrData(0).valid.expect(true.B)
      dut.io.masterWrData(0).valid.poke(false.B)
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

      dut.io.masterWrData(0).bits.addr.poke(0.U)
      dut.io.masterWrData(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.masterWrData(0).ready.expect(false.B)
      dut.io.slaveWrData(0).valid.expect(false.B)
      dut.io.masterWrData(0).valid.poke(false.B)

      respond(dut, 0, 0)
      dut.io.perf.returnSlots(0).expect(1.U)

      dut.io.masterWrData(0).bits.addr.poke(0.U)
      dut.io.masterWrData(0).valid.poke(true.B)
      dut.clock.step()
      // The request was accepted during the cycle even though the credit
      // counter is full again afterwards (combinational output drops).
      dut.io.perf.returnSlots(0).expect(2.U)
      dut.io.masterWrData(0).valid.poke(false.B)
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

      dut.io.masterWrData(0).bits.addr.poke(0.U)
      dut.io.masterWrData(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.masterWrData(0).ready.expect(false.B)
      dut.io.masterWrData(0).valid.poke(false.B)

      respond(dut, 0, 0)
      dut.io.perf.respSlots(0).expect(1.U)

      dut.io.masterWrData(0).bits.addr.poke(0.U)
      dut.io.masterWrData(0).valid.poke(true.B)
      dut.clock.step()
      // Same observation rule: the pool is full again after accepting it.
      dut.io.perf.respSlots(0).expect(2.U)
      dut.io.masterWrData(0).valid.poke(false.B)
    }
  }

  it should "fire the P3 assertion on an unsolicited response" in {
    val p = FabricParams.simple("u", 2, 2, 32, 64)
    intercept[Throwable] {
      simulate(new LmTop(p)) { dut =>
        init(dut, p)
        dut.io.slaveWrResp(0).bits.src.poke(0.U)
        dut.io.slaveWrResp(0).valid.poke(true.B)
        dut.clock.step(3)
      }
    }
  }
}
