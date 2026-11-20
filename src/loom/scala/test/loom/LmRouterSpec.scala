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

  private def init(dut: LmTop, q: FabricParams = p): Unit = {
    for (i <- 0 until q.nMaster) {
      dut.io.masterRdReq(i).valid.poke(false.B)
      dut.io.masterRdReq(i).bits.src.poke(i.U)
      dut.io.masterWrData(i).valid.poke(false.B)
      dut.io.masterWrData(i).bits.src.poke(i.U)
      dut.io.masterRdData(i).ready.poke(true.B)
      dut.io.masterWrResp(i).ready.poke(true.B)
    }
    for (j <- 0 until q.nSlave) {
      dut.io.slaveRdReq(j).ready.poke(true.B)
      dut.io.slaveWrData(j).ready.poke(true.B)
      dut.io.slaveRdData(j).valid.poke(false.B)
      dut.io.slaveWrResp(j).valid.poke(false.B)
    }
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  private def sendWr(dut: LmTop, m: Int, addr: Long, data: Long): Unit = {
    dut.io.masterWrData(m).bits.addr.poke(addr.U)
    dut.io.masterWrData(m).bits.data.poke(data.U)
    dut.io.masterWrData(m).valid.poke(true.B)
    var fired = false
    var guard = 0
    while (!fired && guard < 20) {
      fired = dut.io.masterWrData(m).ready.peek().litValue == 1
      dut.clock.step()
      guard += 1
    }
    dut.io.masterWrData(m).valid.poke(false.B)
    fired shouldBe true
  }

  private def sendRd(dut: LmTop, m: Int, addr: Long): Unit = {
    dut.io.masterRdReq(m).bits.addr.poke(addr.U)
    dut.io.masterRdReq(m).valid.poke(true.B)
    var fired = false
    var guard = 0
    while (!fired && guard < 20) {
      fired = dut.io.masterRdReq(m).ready.peek().litValue == 1
      dut.clock.step()
      guard += 1
    }
    dut.io.masterRdReq(m).valid.poke(false.B)
    fired shouldBe true
  }

  private def waitWr(dut: LmTop, j: Int, limit: Int = 10): Unit = {
    var cycles = 0
    while (!dut.io.slaveWrData(j).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.slaveWrData(j).valid.expect(true.B)
  }

  private def waitRd(dut: LmTop, j: Int, limit: Int = 10): Unit = {
    var cycles = 0
    while (!dut.io.slaveRdReq(j).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.slaveRdReq(j).valid.expect(true.B)
  }

  private def waitMasterRd(dut: LmTop, m: Int, limit: Int = 10): Unit = {
    var cycles = 0
    while (!dut.io.masterRdData(m).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.masterRdData(m).valid.expect(true.B)
  }

  private def waitMasterWr(dut: LmTop, m: Int, limit: Int = 10): Unit = {
    var cycles = 0
    while (!dut.io.masterWrResp(m).valid.peek().litToBoolean && cycles < limit) {
      dut.clock.step()
      cycles += 1
    }
    dut.io.masterWrResp(m).valid.expect(true.B)
  }

  behavior of "LmTop"

  it should "route a write from master 0 to slave 1 and return the response" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      sendWr(dut, 0, 0x1000L, 0xdeadL)
      waitWr(dut, 1)
      dut.io.slaveWrData(1).bits.addr.expect(0x1000.U)
      dut.io.slaveWrData(1).bits.data.expect(0xdeadL.U)

      dut.io.slaveWrResp(1).bits.src.poke(0.U)
      dut.io.slaveWrResp(1).bits.err.poke(false.B)
      dut.io.slaveWrResp(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveWrResp(1).valid.poke(false.B)

      waitMasterWr(dut, 0)
      dut.io.masterWrResp(0).bits.err.expect(false.B)
      dut.clock.step()
      dut.io.perf.rdRequests.expect(0.U)
      dut.io.perf.wrBeats.expect(1.U)
      dut.io.perf.rdResponses.expect(0.U)
      dut.io.perf.wrResponses.expect(1.U)
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
      init(dut, q)

      sendWr(dut, 0, 0x21000L, 0x11L)
      waitWr(dut, 1)
      dut.io.slaveWrData(0).valid.expect(false.B)

      sendRd(dut, 0, 0x9000L)
      waitRd(dut, 0)
      dut.io.slaveRdReq(1).valid.expect(false.B)
      dut.clock.step(2)
    }
  }

  it should "support non-power-of-two endpoint counts" in {
    val q = FabricParams.simple("odd", nMaster = 3, nSlave = 3, addrWidth = 32, dataWidth = 64)
    simulate(new LmTop(q)) { dut =>
      init(dut, q)

      sendWr(dut, 2, 0x2000L, 0x33L)
      waitWr(dut, 2)
      dut.io.slaveWrData(2).bits.addr.expect(0x2000.U)
      dut.io.slaveWrData(1).valid.expect(false.B)
      dut.io.slaveWrData(0).valid.expect(false.B)
      dut.clock.step(2)
    }
  }

  it should "return each response on the channel that issued the request" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      sendRd(dut, 0, 0x0000L)
      waitRd(dut, 0)
      sendWr(dut, 0, 0x1000L, 0x5L)
      waitWr(dut, 1)

      // The write answers first even though the read was issued first, so the
      // response must follow its own channel tag and not the request order.
      dut.io.slaveWrResp(1).bits.src.poke(0.U)
      dut.io.slaveWrResp(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveWrResp(1).valid.poke(false.B)
      waitMasterWr(dut, 0)
      dut.io.masterRdData(0).valid.expect(false.B)
      dut.clock.step()

      dut.io.slaveRdData(0).bits.src.poke(0.U)
      dut.io.slaveRdData(0).bits.data.poke(0x99L.U)
      dut.io.slaveRdData(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveRdData(0).valid.poke(false.B)
      waitMasterRd(dut, 0)
      dut.io.masterRdData(0).bits.data.expect(0x99L.U)
      dut.io.masterWrResp(0).valid.expect(false.B)
    }
  }

  it should "route a read from master 1 to slave 0" in {
    simulate(new LmTop(p)) { dut =>
      init(dut)

      sendRd(dut, 1, 0x0000L)
      waitRd(dut, 0)
      dut.io.slaveRdReq(0).bits.addr.expect(0x0000.U)
      dut.io.slaveRdReq(0).bits.src.expect(1.U)

      dut.io.slaveRdData(0).bits.src.poke(1.U)
      dut.io.slaveRdData(0).bits.data.poke(0x1234L.U)
      dut.io.slaveRdData(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveRdData(0).valid.poke(false.B)

      waitMasterRd(dut, 1)
      dut.io.masterRdData(1).bits.data.expect(0x1234L.U)
      dut.clock.step()
      dut.io.perf.rdResponses.expect(1.U)
    }
  }
}
