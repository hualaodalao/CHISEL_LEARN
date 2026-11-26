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
      Json.parse(Files.readString(Paths.get(s"src/loom/testdata/v1/$name.json")))))

  private def init(dut: LmNetworkTop, p: NetworkParams): Unit = {
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

  private def sendWrite(
      dut: LmNetworkTop,
      m: Int,
      addr: Long,
      data: Long
  ): Unit = {
    dut.io.masterWrData(m).bits.addr.poke(addr.U)
    dut.io.masterWrData(m).bits.data.poke(data.U)
    dut.io.masterWrData(m).valid.poke(true.B)
    var fired = false
    var guard = 0
    while (!fired && guard < 40) {
      fired = dut.io.masterWrData(m).ready.peek().litValue == 1
      dut.clock.step()
      guard += 1
    }
    dut.io.masterWrData(m).valid.poke(false.B)
    fired shouldBe true
  }

  private def sendRead(dut: LmNetworkTop, m: Int, addr: Long): Unit = {
    dut.io.masterRdReq(m).bits.addr.poke(addr.U)
    dut.io.masterRdReq(m).valid.poke(true.B)
    var fired = false
    var guard = 0
    while (!fired && guard < 40) {
      fired = dut.io.masterRdReq(m).ready.peek().litValue == 1
      dut.clock.step()
      guard += 1
    }
    dut.io.masterRdReq(m).valid.poke(false.B)
    fired shouldBe true
  }

  private def waitSlaveRd(dut: LmNetworkTop, j: Int, guardMax: Int = 40): Unit = {
    var seen = false
    var guard = 0
    while (!seen && guard < guardMax) {
      seen = dut.io.slaveRdReq(j).valid.peek().litValue == 1
      if (!seen) { dut.clock.step(); guard += 1 }
    }
    seen shouldBe true
  }

  private def waitSlaveWr(dut: LmNetworkTop, j: Int, guardMax: Int = 40): Unit = {
    var seen = false
    var guard = 0
    while (!seen && guard < guardMax) {
      seen = dut.io.slaveWrData(j).valid.peek().litValue == 1
      if (!seen) { dut.clock.step(); guard += 1 }
    }
    seen shouldBe true
  }

  private def respondRead(dut: LmNetworkTop, j: Int, src: Int, data: Long): Unit = {
    // Let the slave accept the request first, so the response slot is
    // allocated before it is released.
    dut.clock.step()
    dut.io.slaveRdData(j).bits.src.poke(src.U)
    dut.io.slaveRdData(j).bits.data.poke(data.U)
    dut.io.slaveRdData(j).bits.err.poke(false.B)
    dut.io.slaveRdData(j).valid.poke(true.B)
    dut.clock.step()
    dut.io.slaveRdData(j).valid.poke(false.B)
  }

  private def respondWrite(dut: LmNetworkTop, j: Int, src: Int): Unit = {
    dut.clock.step()
    dut.io.slaveWrResp(j).bits.src.poke(src.U)
    dut.io.slaveWrResp(j).bits.err.poke(false.B)
    dut.io.slaveWrResp(j).valid.poke(true.B)
    dut.clock.step()
    dut.io.slaveWrResp(j).valid.poke(false.B)
  }

  private def waitMasterRd(dut: LmNetworkTop, m: Int, guardMax: Int = 40): BigInt = {
    var got = Option.empty[BigInt]
    var guard = 0
    while (got.isEmpty && guard < guardMax) {
      if (dut.io.masterRdData(m).valid.peek().litValue == 1) {
        got = Some(dut.io.masterRdData(m).bits.data.peek().litValue)
        dut.clock.step()
      } else {
        dut.clock.step()
        guard += 1
      }
    }
    got shouldBe defined
    got.get
  }

  private def waitMasterWr(dut: LmNetworkTop, m: Int, guardMax: Int = 40): Unit = {
    var seen = false
    var guard = 0
    while (!seen && guard < guardMax) {
      seen = dut.io.masterWrResp(m).valid.peek().litValue == 1
      dut.clock.step()
      guard += 1
    }
    seen shouldBe true
    dut.io.masterWrResp(m).bits.err.expect(false.B)
  }

  private def waitMasterWrVc(dut: LmNetworkTop, m: Int, guardMax: Int = 80): BigInt = {
    var got = Option.empty[BigInt]
    var guard = 0
    while (got.isEmpty && guard < guardMax) {
      if (dut.io.masterWrResp(m).valid.peek().litValue == 1) {
        got = Some(dut.io.masterWrResp(m).bits.vc.peek().litValue)
        dut.io.masterWrResp(m).bits.err.expect(false.B)
      }
      dut.clock.step()
      guard += 1
    }
    got shouldBe defined
    got.get
  }

  private def waitMasterRdVc(dut: LmNetworkTop, m: Int, guardMax: Int = 80): BigInt = {
    var got = Option.empty[BigInt]
    var guard = 0
    while (got.isEmpty && guard < guardMax) {
      if (dut.io.masterRdData(m).valid.peek().litValue == 1) {
        got = Some(dut.io.masterRdData(m).bits.vc.peek().litValue)
        dut.io.masterRdData(m).bits.err.expect(false.B)
      }
      dut.clock.step()
      guard += 1
    }
    got shouldBe defined
    got.get
  }

  behavior of "LmNetworkTop"

  it should "route a root master to a leaf slave and return the response" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendWrite(dut, 0, 0x3000L, 0xabcL)
      waitSlaveWr(dut, 3)
      dut.io.slaveWrData(3).bits.addr.expect(0x3000.U)
      dut.io.slaveWrData(3).bits.src.expect(0.U)
      dut.io.slaveWrData(3).bits.data.expect(0xabcL.U)
      dut.io.perf.returnSlots(0).expect(1.U)

      respondWrite(dut, 3, 0)
      // A write response carries no data, only the error bit.
      waitMasterWr(dut, 0)
      dut.io.perf.wrBeats.expect(1.U)
      dut.io.perf.wrResponses.expect(1.U)
      dut.io.perf.rdRequests.expect(0.U)
      dut.io.perf.rdResponses.expect(0.U)
      dut.io.perf.returnSlots(0).expect(0.U)
    }
  }

  it should "return read data on the rdata channel" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendRead(dut, 0, 0x3000L)
      waitSlaveRd(dut, 3)
      dut.io.slaveRdReq(3).bits.addr.expect(0x3000.U)
      dut.io.slaveRdReq(3).bits.len.expect(0.U)
      dut.io.slaveRdReq(3).bits.src.expect(0.U)
      dut.io.slaveWrData(3).valid.expect(false.B)

      respondRead(dut, 3, 0, 0x1234L)
      waitMasterRd(dut, 0) shouldBe BigInt(0x1234)
      dut.io.masterWrResp(0).valid.expect(false.B)
      dut.io.perf.rdRequests.expect(1.U)
      dut.io.perf.rdResponses.expect(1.U)
      dut.io.perf.returnSlots(0).expect(0.U)
    }
  }

  it should "route a ctl master to the other branch" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendRead(dut, 3, 0x0000L)
      waitSlaveRd(dut, 0)
      dut.io.slaveRdReq(0).bits.addr.expect(0x0.U)
      dut.io.slaveRdReq(0).bits.src.expect(3.U)

      respondRead(dut, 0, 3, 0x77L)
      waitMasterRd(dut, 3) shouldBe BigInt(0x77)
    }
  }

  it should "carry the AXI4 transaction id end to end" in {
    val p = load("tree_3r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      dut.io.masterRdReq(3).bits.id.poke(9.U)
      sendRead(dut, 3, 0x0000L)
      waitSlaveRd(dut, 0)
      dut.io.slaveRdReq(0).bits.id.expect(9.U)

      dut.io.slaveRdData(0).bits.id.poke(9.U)
      respondRead(dut, 0, 3, 0x5a00L)

      var got = Option.empty[(BigInt, BigInt)]
      var guard = 0
      while (got.isEmpty && guard < 40) {
        if (dut.io.masterRdData(3).valid.peek().litValue == 1)
          got = Some((dut.io.masterRdData(3).bits.data.peek().litValue,
            dut.io.masterRdData(3).bits.id.peek().litValue))
        dut.clock.step()
        guard += 1
      }
      got shouldBe Some((BigInt(0x5a00), BigInt(9)))
    }
  }

  it should "traverse four routers in a chain" in {
    val p = load("chain_4r")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendRead(dut, 1, 0x1000L)
      waitSlaveRd(dut, 1)
      dut.io.slaveRdReq(1).bits.addr.expect(0x1000.U)
      dut.io.slaveRdReq(1).bits.src.expect(1.U)

      respondRead(dut, 1, 1, 0xbeefL)
      waitMasterRd(dut, 1) shouldBe BigInt(0xbeef)
      dut.io.perf.respSlots(1).expect(0.U)
    }
  }

  it should "cross an XY mesh and return the response" in {
    val p = load("mesh_2x2_xy")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendWrite(dut, 0, 0x0000L, 0x5151L)
      waitSlaveWr(dut, 0)
      dut.io.slaveWrData(0).bits.addr.expect(0x0.U)
      dut.io.slaveWrData(0).bits.src.expect(0.U)
      dut.io.slaveWrData(0).bits.data.expect(0x5151L.U)

      respondWrite(dut, 0, 0)
      waitMasterWr(dut, 0)
    }
  }

  it should "carry a response forward around a unidirectional ring" in {
    val p = load("ring_4")
    p.links.count(_.forward) shouldBe 4
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendWrite(dut, 0, 0x0000L, 0x33L)
      waitSlaveWr(dut, 0, 80)
      dut.io.slaveWrData(0).bits.data.expect(0x33L.U)
      dut.io.slaveWrData(0).bits.vc.expect(5.U) // bulk 4 flips to 5 on r0->r1

      respondWrite(dut, 0, 0)
      waitMasterWr(dut, 0, 80)
    }
  }

  it should "flip the dateline half on a cut link and keep it on other links" in {
    val p = load("ring_3_bidir")
    p.routers.find(_.name == "r0").get.outCut shouldBe Seq(true, true)
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      // m0 -> s0 crosses the r0->r1 cut: bulk vc 4 becomes 5.
      sendWrite(dut, 0, 0x0000L, 0x11L)
      waitSlaveWr(dut, 0)
      dut.io.slaveWrData(0).bits.vc.expect(5.U)
      respondWrite(dut, 0, 0)
      waitMasterWr(dut, 0)

      // m1 -> s0 takes r2->r1, which is not cut: lat vc stays 2.
      sendWrite(dut, 1, 0x0000L, 0x12L)
      waitSlaveWr(dut, 0)
      dut.io.slaveWrData(0).bits.vc.expect(2.U)
      respondWrite(dut, 0, 1)
      waitMasterWr(dut, 1)
    }
  }

  it should "flip the wresp dateline half on a forward cut and keep it elsewhere" in {
    val p = load("ring_4")
    p.routers.find(_.name == "r0").get.outCut shouldBe Seq(true)
    p.routers.filter(_.name != "r0").forall(_.outCut == Seq(false)) shouldBe true
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      // m1@r1 -> s1@r3: the response returns r3->r0->r1 and crosses the
      // r0->r1 cut, so lat vc 2 flips to 3.
      sendWrite(dut, 1, 0x1000L, 0x21L)
      waitSlaveWr(dut, 1, 80)
      dut.io.slaveWrData(1).bits.vc.expect(2.U)
      respondWrite(dut, 1, 1)
      waitMasterWrVc(dut, 1) shouldBe BigInt(3)

      // m0@r0 -> s0@r2: the response returns r2->r3->r0, no cut, so bulk
      // vc stays 4 (half 0) even though the request flipped to 5.
      sendWrite(dut, 0, 0x0000L, 0x22L)
      waitSlaveWr(dut, 0, 80)
      dut.io.slaveWrData(0).bits.vc.expect(5.U)
      respondWrite(dut, 0, 0)
      waitMasterWrVc(dut, 0) shouldBe BigInt(4)
    }
  }

  it should "flip the rdata dateline half on a forward cut and keep it elsewhere" in {
    val p = load("ring_4")
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      sendRead(dut, 1, 0x1000L)
      waitSlaveRd(dut, 1, 80)
      respondRead(dut, 1, 1, 0x31L)
      waitMasterRdVc(dut, 1) shouldBe BigInt(3)

      sendRead(dut, 0, 0x0000L)
      waitSlaveRd(dut, 0, 80)
      respondRead(dut, 0, 0, 0x32L)
      waitMasterRdVc(dut, 0) shouldBe BigInt(4)
    }
  }

  it should "keep the response half on reverse hops whose reverse link is not cut" in {
    val p = load("ring_3_bidir")
    // r0->r1 and r0->r2 are cut, so r0's in-links carry inCut=true; the
    // reverse links r1->r0 and r1->r2 exist but are not cut.
    p.routers.find(_.name == "r0").get.inCut shouldBe Seq(true, true)
    p.routers.find(_.name == "r1").get.inCut shouldBe Seq(false, false)
    simulate(new LmNetworkTop(p)) { dut =>
      init(dut, p)

      // Response s0@r1 -> m0@r0 rides link r0->r1 backwards; the reverse
      // IR link r1->r0 is not cut, so bulk vc stays 4.
      sendWrite(dut, 0, 0x0000L, 0x41L)
      waitSlaveWr(dut, 0)
      dut.io.slaveWrData(0).bits.vc.expect(5.U)
      respondWrite(dut, 0, 0)
      waitMasterWrVc(dut, 0) shouldBe BigInt(4)

      // Response s0@r1 -> m1@r2 rides link r2->r1 backwards; r1->r2 is
      // not cut either, so lat vc stays 2.
      sendWrite(dut, 1, 0x0000L, 0x42L)
      waitSlaveWr(dut, 0)
      respondWrite(dut, 0, 1)
      waitMasterWrVc(dut, 1) shouldBe BigInt(2)
    }
  }
}
