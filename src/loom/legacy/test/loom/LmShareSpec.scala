package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmShareSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val p = FabricParams
    .simple("share", nMaster = 2, nSlave = 2, addrWidth = 32, dataWidth = 64)
    .copy(masterMnet = Seq(0, 0), nMnet = 1, slaveSnet = Seq(0, 0), nSnet = 1)

  behavior of "LmTop shared MNet/SNet"

  it should "admit at most one master and one slave per shared net per cycle" in {
    simulate(new LmTop(p)) { dut =>
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

      dut.io.masterRdReq(0).bits.addr.poke(0x0.U)
      dut.io.masterRdReq(1).bits.addr.poke(0x1000.U)
      dut.io.masterRdReq(0).valid.poke(true.B)
      dut.io.masterRdReq(1).valid.poke(true.B)

      val ready0 = dut.io.masterRdReq(0).ready.peek().litValue
      val ready1 = dut.io.masterRdReq(1).ready.peek().litValue
      (ready0 + ready1) shouldBe BigInt(1)

      dut.clock.step()
      dut.io.masterRdReq(0).valid.poke(false.B)
      dut.io.masterRdReq(1).valid.poke(false.B)
      dut.clock.step(2)
    }
  }
}
