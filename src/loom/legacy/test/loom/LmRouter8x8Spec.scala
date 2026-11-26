package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.{Ir, Json}
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmRouter8x8Spec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/testdata/v1/minimal_8x8.json"))))
  private val p = FabricParams.fromIr(ir)

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

  behavior of "LmTop 8x8"

  it should "route all eight masters to distinct slaves concurrently" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)
      for (m <- 0 until 8) {
        val dst = (m + 1) % 8
        dut.io.masterWrData(m).bits.addr.poke((dst.toLong << 12).U)
        dut.io.masterWrData(m).bits.data.poke((0x8000L + m).U)
        dut.io.masterWrData(m).valid.poke(true.B)
      }
      dut.clock.step(2)

      for (j <- 0 until 8) {
        val src = (j + 7) % 8
        dut.io.slaveWrData(j).valid.expect(true.B)
        dut.io.slaveWrData(j).bits.addr.expect((j.toLong << 12).U)
        dut.io.slaveWrData(j).bits.src.expect(src.U)
        dut.io.slaveWrData(j).bits.data.expect((0x8000L + src).U)
      }
      for (m <- 0 until 8) dut.io.masterWrData(m).valid.poke(false.B)

      // Return one response through the 8-way response path.
      dut.io.slaveWrResp(0).bits.src.poke(7.U)
      dut.io.slaveWrResp(0).bits.err.poke(false.B)
      dut.io.slaveWrResp(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveWrResp(0).valid.poke(false.B)

      var seen = false
      var cycles = 0
      while (!seen && cycles < 10) {
        seen = dut.io.masterWrResp(7).valid.peek().litToBoolean
        if (!seen) { dut.clock.step(); cycles += 1 }
      }
      seen shouldBe true
      dut.io.masterWrResp(7).bits.src.expect(7.U)
      dut.io.masterWrResp(7).bits.err.expect(false.B)
    }
  }
}
