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

  private def waitValid(dut: LmTop, j: Int, limit: Int = 6): Boolean = {
    var cycles = 0
    while (!dut.io.slave(j).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.slave(j).valid.peek().litToBoolean
  }

  behavior of "LmTop (banked)"

  it should "route into both banks through one register stage" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0x2000.U) // slave 2, bank 1
      dut.io.master(0).bits.data.poke(0x22L.U)
      dut.io.master(0).valid.poke(true.B)

      dut.io.master(1).bits.write.poke(true.B)
      dut.io.master(1).bits.addr.poke(0x0000.U) // slave 0, bank 0
      dut.io.master(1).bits.src.poke(1.U)
      dut.io.master(1).bits.data.poke(0x11L.U)
      dut.io.master(1).valid.poke(true.B)

      dut.clock.step()
      dut.io.master(0).valid.poke(false.B)
      dut.io.master(1).valid.poke(false.B)

      waitValid(dut, 0) shouldBe true
      dut.io.slave(0).bits.addr.expect(0x0000.U)
      dut.io.slave(0).bits.data.expect(0x11L.U)

      waitValid(dut, 2) shouldBe true
      dut.io.slave(2).bits.addr.expect(0x2000.U)
      dut.io.slave(2).bits.data.expect(0x22L.U)

      dut.io.slaveRsp(0).bits.src.poke(1.U)
      dut.io.slaveRsp(0).bits.data.poke(0xabcL.U)
      dut.io.slaveRsp(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.masterRsp(1).valid.expect(true.B)
      dut.io.masterRsp(1).bits.data.expect(0xabcL.U)
      dut.io.slaveRsp(0).valid.poke(false.B)
    }
  }

  it should "reject a bank count that does not divide the outputs" in {
    an[IllegalArgumentException] should be thrownBy flat.copy(banks = 3)
    an[IllegalArgumentException] should be thrownBy flat.copy(banks = 8)
  }
}
