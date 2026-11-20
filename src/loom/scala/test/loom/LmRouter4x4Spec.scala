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

  behavior of "LmTop 4x4"

  it should "route each master to its addressed slave and return responses" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      for (i <- 0 until p.nMaster) {
        dut.io.master(i).bits.write.poke(true.B)
        dut.io.master(i).bits.addr.poke((i << 12).U)
        dut.io.master(i).bits.src.poke(i.U)
        dut.io.master(i).bits.data.poke((0xa000 + i).U)
        dut.io.master(i).valid.poke(true.B)
        dut.clock.step()

        dut.io.slave(i).valid.expect(true.B)
        dut.io.slave(i).bits.addr.expect((i << 12).U)
        dut.io.slave(i).bits.src.expect(i.U)
        dut.io.slave(i).bits.data.expect((0xa000 + i).U)
        dut.io.master(i).valid.poke(false.B)

        dut.io.slaveRsp(i).bits.src.poke(i.U)
        dut.io.slaveRsp(i).bits.data.poke((0xb000 + i).U)
        dut.io.slaveRsp(i).bits.err.poke(false.B)
        dut.io.slaveRsp(i).valid.poke(true.B)
        dut.clock.step()

        dut.io.masterRsp(i).valid.expect(true.B)
        dut.io.masterRsp(i).bits.src.expect(i.U)
        dut.io.masterRsp(i).bits.data.expect((0xb000 + i).U)
        dut.io.slaveRsp(i).valid.poke(false.B)
      }
    }
  }

  it should "round-robin two masters contending for one slave" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      // Both masters request the same slave. Admission alternates through
      // the VC queues: m0 enters first, so the slave sees m0 after the
      // first edge and m1 after the second.
      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).valid.poke(true.B)
      dut.io.master(1).bits.write.poke(true.B)
      dut.io.master(1).bits.addr.poke(0.U)
      dut.io.master(1).bits.src.poke(1.U)
      dut.io.master(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.slave(0).bits.src.expect(0.U)

      // m1 is admitted next and drives the port one cycle later.
      dut.clock.step()
      dut.io.slave(0).bits.src.expect(1.U)

      // Drop m1: m1 drains once, then m0 keeps the port.
      dut.io.master(1).valid.poke(false.B)
      dut.clock.step()
      dut.io.slave(0).bits.src.expect(0.U)

      dut.io.master(0).valid.poke(false.B)
    }
  }
}
