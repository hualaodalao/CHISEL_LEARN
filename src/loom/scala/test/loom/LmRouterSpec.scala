package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmRouterSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val p = FabricParams.simple("t", nMaster = 2, nSlave = 2, addrWidth = 32, dataWidth = 64)

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

  behavior of "LmTop"

  it should "route a write from master 0 to slave 1 and return the response" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0x1000.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).bits.data.poke(0xdeadL.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()

      dut.io.slave(1).valid.expect(true.B)
      dut.io.slave(1).bits.addr.expect(0x1000.U)
      dut.io.slave(1).bits.write.expect(true.B)
      dut.io.slave(1).bits.data.expect(0xdeadL.U)
      dut.io.master(0).valid.poke(false.B)

      dut.io.slaveRsp(1).bits.src.poke(0.U)
      dut.io.slaveRsp(1).bits.data.poke(0xbeefL.U)
      dut.io.slaveRsp(1).bits.err.poke(false.B)
      dut.io.slaveRsp(1).valid.poke(true.B)
      dut.clock.step()

      dut.io.masterRsp(0).valid.expect(true.B)
      dut.io.masterRsp(0).bits.data.expect(0xbeefL.U)
      dut.io.slaveRsp(1).valid.poke(false.B)
      dut.clock.step()
      dut.io.perf.requests.expect(1.U)
      dut.io.perf.responses.expect(1.U)
      dut.io.perf.errors.expect(0.U)
      dut.io.perf.errSticky.expect(false.B)
    }
  }

  it should "decode explicit base/size windows" in {
    val q = p.copy(
      slaveBase = Seq(0x8000L, 0x20000L),
      slaveSize = Seq(0x8000L, 0x8000L)
    )
    simulate(new LmTop(q)) { dut =>
      init(dut)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0x21000.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).bits.data.poke(0x11L.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.slave(1).valid.expect(true.B)
      dut.io.slave(0).valid.expect(false.B)
      dut.io.master(0).valid.poke(false.B)
      dut.clock.step()

      dut.io.master(0).bits.write.poke(false.B)
      dut.io.master(0).bits.addr.poke(0x9000.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.slave(0).valid.expect(true.B)
      dut.io.slave(1).valid.expect(false.B)
      dut.io.master(0).valid.poke(false.B)
      dut.clock.step(2)
    }
  }

  it should "support non-power-of-two endpoint counts" in {
    val q = FabricParams.simple("odd", nMaster = 3, nSlave = 3, addrWidth = 32, dataWidth = 64)
    simulate(new LmTop(q)) { dut =>
      for (i <- 0 until q.nMaster) {
        dut.io.master(i).valid.poke(false.B)
        dut.io.master(i).bits.write.poke(false.B)
        dut.io.master(i).bits.addr.poke(0.U)
        dut.io.master(i).bits.len.poke(0.U)
        dut.io.master(i).bits.src.poke(0.U)
        dut.io.master(i).bits.data.poke(0.U)
        dut.io.masterRsp(i).ready.poke(true.B)
      }
      for (j <- 0 until q.nSlave) {
        dut.io.slave(j).ready.poke(true.B)
        dut.io.slaveRsp(j).valid.poke(false.B)
        dut.io.slaveRsp(j).bits.src.poke(0.U)
        dut.io.slaveRsp(j).bits.data.poke(0.U)
        dut.io.slaveRsp(j).bits.err.poke(false.B)
      }
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.master(2).bits.write.poke(true.B)
      dut.io.master(2).bits.addr.poke(0x2000.U)
      dut.io.master(2).bits.src.poke(2.U)
      dut.io.master(2).bits.data.poke(0x33L.U)
      dut.io.master(2).valid.poke(true.B)
      dut.clock.step()
      dut.io.slave(2).valid.expect(true.B)
      dut.io.slave(2).bits.addr.expect(0x2000.U)
      dut.io.slave(1).valid.expect(false.B)
      dut.io.slave(0).valid.expect(false.B)
      dut.io.master(2).valid.poke(false.B)
      dut.clock.step(2)
    }
  }

  it should "route a read from master 1 to slave 0" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      dut.io.master(1).bits.write.poke(false.B)
      dut.io.master(1).bits.addr.poke(0x0000.U)
      dut.io.master(1).bits.src.poke(1.U)
      dut.io.master(1).valid.poke(true.B)
      dut.clock.step()

      dut.io.slave(0).valid.expect(true.B)
      dut.io.slave(0).bits.addr.expect(0x0000.U)
      dut.io.slave(0).bits.write.expect(false.B)
      dut.io.master(1).valid.poke(false.B)

      dut.io.slaveRsp(0).bits.src.poke(1.U)
      dut.io.slaveRsp(0).bits.data.poke(0x1234L.U)
      dut.io.slaveRsp(0).valid.poke(true.B)
      dut.clock.step()

      dut.io.masterRsp(1).valid.expect(true.B)
      dut.io.masterRsp(1).bits.data.expect(0x1234L.U)
    }
  }
}
