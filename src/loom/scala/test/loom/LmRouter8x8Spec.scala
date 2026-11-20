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
    Json.parse(Files.readString(Paths.get("src/loom/examples/minimal_8x8.json"))))
  private val p = FabricParams.fromIr(ir)

  private def init(dut: LmTop): Unit = {
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

  behavior of "LmTop 8x8"

  it should "route all eight masters to distinct slaves in one cycle" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)
      for (m <- 0 until 8) {
        val dst = (m + 1) % 8
        dut.io.master(m).bits.addr.poke((dst.toLong << 12).U)
        dut.io.master(m).bits.data.poke((0x8000L + m).U)
        dut.io.master(m).valid.poke(true.B)
      }
      dut.clock.step()

      for (j <- 0 until 8) {
        val src = (j + 7) % 8
        dut.io.slave(j).valid.expect(true.B)
        dut.io.slave(j).bits.addr.expect((j.toLong << 12).U)
        dut.io.slave(j).bits.src.expect(src.U)
        dut.io.slave(j).bits.data.expect((0x8000L + src).U)
      }
      for (m <- 0 until 8) dut.io.master(m).valid.poke(false.B)

      // Return one response through the 8-way response path.
      dut.io.slaveRsp(0).bits.src.poke(7.U)
      dut.io.slaveRsp(0).bits.data.poke(0xbeefL.U)
      dut.io.slaveRsp(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.masterRsp(7).valid.expect(true.B)
      dut.io.masterRsp(7).bits.data.expect(0xbeefL.U)
      dut.io.slaveRsp(0).valid.poke(false.B)
    }
  }
}
