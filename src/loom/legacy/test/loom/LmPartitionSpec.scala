package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.{Canonical, Ir, Json}
import loom.report.ReportsV1
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Two chain partitions wired at their boundary ports. */
class LmChainSplitHarness(net: NetworkParams, front: PartitionParams, back: PartitionParams)
    extends Module {
  val io = IO(new Bundle {
    val masterRdReq =
      Vec(front.masters.size, Flipped(Decoupled(new LmRdReq(net.addrWidth, net.idWidth))))
    val masterWrData =
      Vec(front.masters.size, Flipped(Decoupled(new LmWrData(net.addrWidth, net.dataWidth, net.idWidth))))
    val masterRdData =
      Vec(front.masters.size, Decoupled(new LmRdData(net.dataWidth, net.idWidth)))
    val masterWrResp = Vec(front.masters.size, Decoupled(new LmWrResp(net.idWidth)))
    val slaveRdReq =
      Vec(back.slaves.size, Decoupled(new LmRdReq(net.addrWidth, net.idWidth)))
    val slaveWrData =
      Vec(back.slaves.size, Decoupled(new LmWrData(net.addrWidth, net.dataWidth, net.idWidth)))
    val slaveRdData =
      Vec(back.slaves.size, Flipped(Decoupled(new LmRdData(net.dataWidth, net.idWidth))))
    val slaveWrResp = Vec(back.slaves.size, Flipped(Decoupled(new LmWrResp(net.idWidth))))
  })

  private val f = Module(new LmPartitionTop(net, front))
  private val b = Module(new LmPartitionTop(net, back))

  f.io.masterRdReq <> io.masterRdReq
  f.io.masterWrData <> io.masterWrData
  io.masterRdData <> f.io.masterRdData
  io.masterWrResp <> f.io.masterWrResp
  b.io.slaveRdReq <> io.slaveRdReq
  b.io.slaveWrData <> io.slaveWrData
  io.slaveRdData <> b.io.slaveRdData
  io.slaveWrResp <> b.io.slaveWrResp

  f.io.boundaryOutRdReq <> b.io.boundaryInRdReq
  f.io.boundaryOutWrData <> b.io.boundaryInWrData
  b.io.boundaryInRdData <> f.io.boundaryOutRdData
  b.io.boundaryInWrResp <> f.io.boundaryOutWrResp
  f.io.boundaryOutRdDataFwd <> b.io.boundaryInRdDataFwd
  f.io.boundaryOutWrRespFwd <> b.io.boundaryInWrRespFwd
}

class LmPartitionSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/testdata/v1/chain_split.json"))))
  private val net = NetworkParams.fromIr(ir)
  private val frontSpec = ir.partitions.find(_.name == "front").get
  private val backSpec = ir.partitions.find(_.name == "back").get
  private val front = PartitionParams.fromIr(ir, net, frontSpec)
  private val back = PartitionParams.fromIr(ir, net, backSpec)

  private def init(dut: LmChainSplitHarness): Unit = {
    for (i <- 0 until front.masters.size) {
      dut.io.masterRdReq(i).valid.poke(false.B)
      dut.io.masterRdReq(i).bits.src.poke(i.U)
      dut.io.masterWrData(i).valid.poke(false.B)
      dut.io.masterWrData(i).bits.src.poke(i.U)
      dut.io.masterRdData(i).ready.poke(true.B)
      dut.io.masterWrResp(i).ready.poke(true.B)
    }
    for (j <- 0 until back.slaves.size) {
      dut.io.slaveRdReq(j).ready.poke(true.B)
      dut.io.slaveWrData(j).ready.poke(true.B)
      dut.io.slaveRdData(j).valid.poke(false.B)
      dut.io.slaveWrResp(j).valid.poke(false.B)
    }
    dut.reset.poke(true.B)
    dut.clock.step(2)
    dut.reset.poke(false.B)
  }

  behavior of "PartitionParams"

  it should "produce disjoint, stable partition metadata" in {
    front.routers shouldBe Seq(0, 1)
    back.routers shouldBe Seq(2, 3)
    front.outLinks.map(_.id) shouldBe Seq(1)
    back.inLinks.map(_.id) shouldBe Seq(1)
    front.masters shouldBe Seq(0, 1)
    back.slaves shouldBe Seq(0, 1)
    front.hash should not be back.hash

    val ports = ReportsV1.portsJson(ir, net, front, "cfg")
    Canonical.render(ports) shouldBe Canonical.render(ReportsV1.portsJson(ir, net, front, "cfg"))

    val asyncIr = Ir.fromJson(
      Json.parse(Files.readString(Paths.get("src/loom/testdata/v1/chain_split_async.json"))))
    val asyncNet = NetworkParams.fromIr(asyncIr)
    val asyncFront = PartitionParams.fromIr(asyncIr, asyncNet,
      asyncIr.partitions.find(_.name == "front").get)
    val asyncPorts = Canonical.render(ReportsV1.portsJson(asyncIr, asyncNet, asyncFront, "cfg"))
    asyncPorts should include("\"localClock\":\"core\"")
    asyncPorts should include("\"peerClock\":\"slow\"")
    asyncPorts should include("\"async\":true")
  }

  behavior of "LmPartitionTop"

  it should "route across a registered partition boundary" in {
    simulate(new LmChainSplitHarness(net, front, back)) { dut =>
      init(dut)

      dut.io.masterWrData(0).bits.addr.poke(0x1000.U)
      dut.io.masterWrData(0).bits.data.poke(0xabcL.U)
      dut.io.masterWrData(0).valid.poke(true.B)

      var accepted = false
      var guard = 0
      while (!accepted && guard < 40) {
        accepted = dut.io.masterWrData(0).ready.peek().litValue == 1
        dut.clock.step()
        guard += 1
      }
      accepted shouldBe true
      dut.io.masterWrData(0).valid.poke(false.B)

      var seen = false
      guard = 0
      while (!seen && guard < 60) {
        seen = dut.io.slaveWrData(1).valid.peek().litValue == 1
        if (!seen) { dut.clock.step(); guard += 1 }
      }
      seen shouldBe true
      dut.io.slaveWrData(1).bits.addr.expect(0x1000.U)
      dut.io.slaveWrData(1).bits.data.expect(0xabcL.U)
      dut.io.slaveWrData(1).bits.src.expect(0.U)

      dut.clock.step()
      dut.io.slaveWrResp(1).bits.src.poke(0.U)
      dut.io.slaveWrResp(1).bits.err.poke(false.B)
      dut.io.slaveWrResp(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveWrResp(1).valid.poke(false.B)

      var done = false
      guard = 0
      while (!done && guard < 60) {
        done = dut.io.masterWrResp(0).valid.peek().litValue == 1
        dut.clock.step()
        guard += 1
      }
      done shouldBe true
      dut.io.masterWrResp(0).bits.err.expect(false.B)

      // A read crosses the same boundary on rreq and returns on rdata.
      dut.io.masterRdReq(0).bits.addr.poke(0x1000.U)
      dut.io.masterRdReq(0).valid.poke(true.B)
      accepted = false
      guard = 0
      while (!accepted && guard < 40) {
        accepted = dut.io.masterRdReq(0).ready.peek().litValue == 1
        dut.clock.step()
        guard += 1
      }
      accepted shouldBe true
      dut.io.masterRdReq(0).valid.poke(false.B)

      seen = false
      guard = 0
      while (!seen && guard < 60) {
        seen = dut.io.slaveRdReq(1).valid.peek().litValue == 1
        if (!seen) { dut.clock.step(); guard += 1 }
      }
      seen shouldBe true
      dut.io.slaveRdReq(1).bits.addr.expect(0x1000.U)

      dut.clock.step()
      dut.io.slaveRdData(1).bits.src.poke(0.U)
      dut.io.slaveRdData(1).bits.data.poke(0x5678L.U)
      dut.io.slaveRdData(1).bits.err.poke(false.B)
      dut.io.slaveRdData(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveRdData(1).valid.poke(false.B)

      var got = Option.empty[BigInt]
      guard = 0
      while (got.isEmpty && guard < 60) {
        if (dut.io.masterRdData(0).valid.peek().litValue == 1) {
          got = Some(dut.io.masterRdData(0).bits.data.peek().litValue)
          dut.clock.step()
        } else {
          dut.clock.step()
          guard += 1
        }
      }
      got shouldBe Some(BigInt(0x5678))
    }
  }
}
