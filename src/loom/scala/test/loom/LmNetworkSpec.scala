package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.{Ir, Json}
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmNetworkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private def load(name: String): NetworkParams =
    NetworkParams.fromIr(Ir.fromJson(
      Json.parse(Files.readString(Paths.get(s"src/loom/examples/$name.json")))))

  private def init(dut: LmNetworkTop, p: NetworkParams): Unit = {
    for (i <- 0 until p.nMaster) {
      dut.io.master(i).valid.poke(false.B)
      dut.io.master(i).bits.write.poke(true.B)
      dut.io.master(i).bits.addr.poke(0.U)
      dut.io.master(i).bits.len.poke(0.U)
      dut.io.master(i).bits.src.poke(i.U)
      dut.io.master(i).bits.data.poke(0.U)
      dut.io.master(i).bits.strb.poke(0.U)
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

  private def send(
      dut: LmNetworkTop,
      m: Int,
      addr: Long,
      data: Long,
      write: Boolean = true
  ): Unit = {
    dut.io.master(m).bits.write.poke(write.B)
    dut.io.master(m).bits.addr.poke(addr.U)
    dut.io.master(m).bits.src.poke(m.U)
    dut.io.master(m).bits.data.poke(data.U)
    dut.io.master(m).valid.poke(true.B)
    var fired = false
    var guard = 0
    while (!fired && guard < 40) {
      fired = dut.io.master(m).ready.peek().litValue == 1
      dut.clock.step()
      guard += 1
    }
    dut.io.master(m).valid.poke(false.B)
    fired shouldBe true
  }

  private def waitSlave(dut: LmNetworkTop, j: Int, guardMax: Int = 40): Unit = {
    var seen = false
    var guard = 0
    while (!seen && guard < guardMax) {
      seen = dut.io.slave(j).valid.peek().litValue == 1
      if (!seen) { dut.clock.step(); guard += 1 }
    }
    seen shouldBe true
  }

  private def respond(dut: LmNetworkTop, j: Int, src: Int, data: Long): Unit = {
    // Let the slave accept the request first, so the response slot is
    // allocated before it is released.
    dut.clock.step()
    dut.io.slaveRsp(j).bits.src.poke(src.U)
    dut.io.slaveRsp(j).bits.data.poke(data.U)
    dut.io.slaveRsp(j).valid.poke(true.B)
    dut.clock.step()
    dut.io.slaveRsp(j).valid.poke(false.B)
  }

  private def waitMasterRsp(dut: LmNetworkTop, m: Int, guardMax: Int = 40): BigInt = {
    var got = Option.empty[BigInt]
    var guard = 0
    while (got.isEmpty && guard < guardMax) {
      if (dut.io.masterRsp(m).valid.peek().litValue == 1) {
        got = Some(dut.io.masterRsp(m).bits.data.peek().litValue)
        dut.clock.step()
      } else {
        dut.clock.step()
        guard += 1
      }
    }
    got shouldBe defined
    got.get
  }

  behavior of "LmNetworkTop"

  it should "route a root master to a leaf slave and return the response" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      send(dut, 0, 0x3000L, 0xabcL)
      waitSlave(dut, 3)
      dut.io.slave(3).bits.addr.expect(0x3000.U)
      dut.io.slave(3).bits.src.expect(0.U)
      dut.io.slave(3).bits.data.expect(0xabcL.U)
      dut.io.perf.returnSlots(0).expect(1.U)

      respond(dut, 3, 0, 0x1234L)
      // A write response carries no data.
      waitMasterRsp(dut, 0) shouldBe BigInt(0)
      dut.io.perf.returnSlots(0).expect(0.U)
    }
  }

  it should "return read data on the rdata channel" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      send(dut, 0, 0x3000L, 0L, write = false)
      waitSlave(dut, 3)
      dut.io.slave(3).bits.write.expect(false.B)
      dut.io.slave(3).bits.addr.expect(0x3000.U)
      dut.io.slave(3).bits.len.expect(0.U)
      dut.io.slave(3).bits.src.expect(0.U)

      respond(dut, 3, 0, 0x1234L)
      waitMasterRsp(dut, 0) shouldBe BigInt(0x1234)
      dut.io.perf.returnSlots(0).expect(0.U)
    }
  }

  it should "route a ctl master to the other branch" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      send(dut, 3, 0x0000L, 0L, write = false)
      waitSlave(dut, 0)
      dut.io.slave(0).bits.addr.expect(0x0.U)
      dut.io.slave(0).bits.src.expect(3.U)

      respond(dut, 0, 3, 0x77L)
      waitMasterRsp(dut, 3) shouldBe BigInt(0x77)
    }
  }

  it should "carry the AXI4 transaction id end to end" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)
      dut.io.masterRsp(3).ready.poke(true.B)

      dut.io.master(3).bits.id.poke(9.U)
      send(dut, 3, 0x0000L, 0L, write = false)
      waitSlave(dut, 0)
      dut.io.slave(0).bits.id.expect(9.U)

      dut.io.slaveRsp(0).bits.id.poke(9.U)
      respond(dut, 0, 3, 0x5a00L)

      var got = Option.empty[(BigInt, BigInt)]
      var guard = 0
      while (got.isEmpty && guard < 40) {
        if (dut.io.masterRsp(3).valid.peek().litValue == 1)
          got = Some((dut.io.masterRsp(3).bits.data.peek().litValue,
            dut.io.masterRsp(3).bits.id.peek().litValue))
        dut.clock.step()
        guard += 1
      }
      got shouldBe Some((BigInt(0x5a00), BigInt(9)))
    }
  }

  it should "traverse four routers in a chain" in {    val p = load("chain_4r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      send(dut, 1, 0x1000L, 0L, write = false)
      waitSlave(dut, 1)
      dut.io.slave(1).bits.addr.expect(0x1000.U)
      dut.io.slave(1).bits.src.expect(1.U)

      respond(dut, 1, 1, 0xbeefL)
      waitMasterRsp(dut, 1) shouldBe BigInt(0xbeef)
      dut.io.perf.respSlots(1).expect(0.U)
    }
  }

  it should "cross an XY mesh and return the response" in {
    val p = load("mesh_2x2_xy")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      send(dut, 0, 0x0000L, 0x5151L)
      waitSlave(dut, 0)
      dut.io.slave(0).bits.addr.expect(0x0.U)
      dut.io.slave(0).bits.src.expect(0.U)
      dut.io.slave(0).bits.data.expect(0x5151L.U)

      respond(dut, 0, 0, 0x6161L)
      waitMasterRsp(dut, 0) shouldBe BigInt(0)
    }
  }

  it should "carry a response forward around a unidirectional ring" in {
    val p = load("ring_4")
    p.links.count(_.forward) shouldBe 4
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      send(dut, 0, 0x0000L, 0x33L)
      waitSlave(dut, 0, 80)
      dut.io.slave(0).bits.data.expect(0x33L.U)
      dut.io.slave(0).bits.vc.expect(5.U) // bulk 4 flips to 5 on r0->r1

      respond(dut, 0, 0, 0x44L)
      waitMasterRsp(dut, 0, 80) shouldBe BigInt(0)
    }
  }

  it should "flip the dateline half on a cut link and keep it on other links" in {
    val p = load("ring_3_bidir")
    p.routers.find(_.name == "r0").get.outCut shouldBe Seq(true, true)
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      // m0 -> s0 crosses the r0->r1 cut: bulk vc 4 becomes 5.
      send(dut, 0, 0x0000L, 0x11L)
      waitSlave(dut, 0)
      dut.io.slave(0).bits.vc.expect(5.U)
      respond(dut, 0, 0, 0x21L)
      waitMasterRsp(dut, 0) shouldBe BigInt(0)

      // m1 -> s0 takes r2->r1, which is not cut: lat vc stays 2.
      send(dut, 1, 0x0000L, 0x12L)
      waitSlave(dut, 0)
      dut.io.slave(0).bits.vc.expect(2.U)
      respond(dut, 0, 1, 0x22L)
      waitMasterRsp(dut, 1) shouldBe BigInt(0)
    }
  }
}
