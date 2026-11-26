package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmBankedSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val flat = FabricParams.simple("banked4", nMaster = 4, nSlave = 4,
    addrWidth = 32, dataWidth = 64)
  private val p = flat.copy(banks = 2)

  private def init(dut: LmTop): Unit = {
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

  private def waitValid(dut: LmTop, j: Int, limit: Int = 6): Boolean = {
    var cycles = 0
    while (!dut.io.slaveWrData(j).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.slaveWrData(j).valid.peek().litToBoolean
  }

  behavior of "LmTop (banked)"

  it should "route into both banks through one register stage" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      dut.io.masterWrData(0).bits.addr.poke(0x2000.U) // slave 2, bank 1
      dut.io.masterWrData(0).bits.data.poke(0x22L.U)
      dut.io.masterWrData(0).valid.poke(true.B)

      dut.io.masterWrData(1).bits.addr.poke(0x0000.U) // slave 0, bank 0
      dut.io.masterWrData(1).bits.data.poke(0x11L.U)
      dut.io.masterWrData(1).valid.poke(true.B)

      dut.clock.step()
      dut.io.masterWrData(0).valid.poke(false.B)
      dut.io.masterWrData(1).valid.poke(false.B)

      waitValid(dut, 0) shouldBe true
      dut.io.slaveWrData(0).bits.addr.expect(0x0000.U)
      dut.io.slaveWrData(0).bits.data.expect(0x11L.U)

      waitValid(dut, 2) shouldBe true
      dut.io.slaveWrData(2).bits.addr.expect(0x2000.U)
      dut.io.slaveWrData(2).bits.data.expect(0x22L.U)

      dut.io.slaveWrResp(0).bits.src.poke(1.U)
      dut.io.slaveWrResp(0).bits.err.poke(false.B)
      dut.io.slaveWrResp(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveWrResp(0).valid.poke(false.B)

      var seen = false
      var cycles = 0
      while (!seen && cycles < 10) {
        seen = dut.io.masterWrResp(1).valid.peek().litToBoolean
        if (!seen) { dut.clock.step(); cycles += 1 }
      }
      seen shouldBe true
      dut.io.masterWrResp(1).bits.src.expect(1.U)
    }
  }

  it should "reject a bank count that does not divide the outputs" in {
    an[IllegalArgumentException] should be thrownBy flat.copy(banks = 3)
    an[IllegalArgumentException] should be thrownBy flat.copy(banks = 8)
  }
}
