package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmRouter4x4Spec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val p = FabricParams.simple("t4", nMaster = 4, nSlave = 4, addrWidth = 32, dataWidth = 128)

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

  private def waitRdReq(dut: LmTop, j: Int, limit: Int = 10): Unit = {
    var cycles = 0
    while (!dut.io.slaveRdReq(j).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.slaveRdReq(j).valid.expect(true.B)
  }

  private def waitRdData(dut: LmTop, m: Int, limit: Int = 10): Unit = {
    var cycles = 0
    while (!dut.io.masterRdData(m).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.masterRdData(m).valid.expect(true.B)
  }

  /** Sources seen on a slave port while it holds a valid request. */
  private def sampleSrc(dut: LmTop, j: Int, cycles: Int): Seq[Int] = {
    val seen = Seq.newBuilder[Int]
    for (_ <- 0 until cycles) {
      if (dut.io.slaveWrData(j).valid.peek().litToBoolean)
        seen += dut.io.slaveWrData(j).bits.src.peek().litValue.toInt
      dut.clock.step()
    }
    seen.result()
  }

  behavior of "LmTop 4x4"

  it should "route each master to its addressed slave and return responses" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      for (i <- 0 until p.nMaster) {
        dut.io.masterRdReq(i).bits.addr.poke((i << 12).U)
        dut.io.masterRdReq(i).valid.poke(true.B)
        dut.clock.step()
        dut.io.masterRdReq(i).valid.poke(false.B)

        waitRdReq(dut, i)
        dut.io.slaveRdReq(i).bits.addr.expect((i << 12).U)
        dut.io.slaveRdReq(i).bits.src.expect(i.U)

        dut.io.slaveRdData(i).bits.src.poke(i.U)
        dut.io.slaveRdData(i).bits.data.poke((0xb000 + i).U)
        dut.io.slaveRdData(i).valid.poke(true.B)
        dut.clock.step()
        dut.io.slaveRdData(i).valid.poke(false.B)

        waitRdData(dut, i)
        dut.io.masterRdData(i).bits.src.expect(i.U)
        dut.io.masterRdData(i).bits.data.expect((0xb000 + i).U)
        dut.clock.step()
      }
    }
  }

  it should "round-robin two masters contending for one slave" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      // Both masters request the same slave; admission alternates through the
      // VC queues, so the slave port sees both sources in turn.
      dut.io.masterWrData(0).bits.addr.poke(0.U)
      dut.io.masterWrData(0).bits.data.poke(0x10L.U)
      dut.io.masterWrData(0).valid.poke(true.B)
      dut.io.masterWrData(1).bits.addr.poke(0.U)
      dut.io.masterWrData(1).bits.data.poke(0x11L.U)
      dut.io.masterWrData(1).valid.poke(true.B)

      val both = sampleSrc(dut, 0, 5)
      both should contain(0)
      both should contain(1)
      both.take(2) shouldBe Seq(0, 1)

      // Drop m1: its pipeline drains, then m0 keeps the port.
      dut.io.masterWrData(1).valid.poke(false.B)
      dut.clock.step(6)
      val only0 = sampleSrc(dut, 0, 3)
      only0 shouldBe Seq.fill(only0.length)(0)
      only0 should not be empty

      dut.io.masterWrData(0).valid.poke(false.B)
    }
  }
}
