package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.{Canonical, Ir, Json}
import loom.report.Reports
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Two chain partitions wired at their boundary ports. */
class LmChainSplitHarness(net: NetworkParams, front: PartitionParams, back: PartitionParams)
    extends Module {
  val io = IO(new Bundle {
    val master = Vec(front.masters.size, Flipped(Decoupled(
      new LmReq(net.addrWidth, net.dataWidth, net.idWidth))))
    val masterRsp = Vec(front.masters.size, Decoupled(new LmRsp(net.dataWidth, net.idWidth)))
    val slave = Vec(back.slaves.size, Decoupled(new LmReq(net.addrWidth, net.dataWidth, net.idWidth)))
    val slaveRsp = Vec(back.slaves.size, Flipped(Decoupled(new LmRsp(net.dataWidth, net.idWidth))))
  })

  private val f = Module(new LmPartitionTop(net, front))
  private val b = Module(new LmPartitionTop(net, back))

  f.io.master <> io.master
  io.masterRsp <> f.io.masterRsp
  b.io.slave <> io.slave
  io.slaveRsp <> b.io.slaveRsp

  f.io.boundaryOutReq <> b.io.boundaryInReq
  b.io.boundaryInRsp <> f.io.boundaryOutRsp
  f.io.boundaryOutRspFwd <> b.io.boundaryInRspFwd
}

class LmPartitionSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/examples/chain_split.json"))))
  private val net = NetworkParams.fromIr(ir)
  private val frontSpec = ir.partitions.find(_.name == "front").get
  private val backSpec = ir.partitions.find(_.name == "back").get
  private val front = PartitionParams.fromIr(ir, net, frontSpec)
  private val back = PartitionParams.fromIr(ir, net, backSpec)

  private def init(dut: LmChainSplitHarness): Unit = {
    for (i <- 0 until front.masters.size) {
      dut.io.master(i).valid.poke(false.B)
      dut.io.master(i).bits.write.poke(true.B)
      dut.io.master(i).bits.addr.poke(0.U)
      dut.io.master(i).bits.len.poke(0.U)
      dut.io.master(i).bits.src.poke(i.U)
      dut.io.master(i).bits.data.poke(0.U)
      dut.io.masterRsp(i).ready.poke(true.B)
    }
    for (j <- 0 until back.slaves.size) {
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

  behavior of "PartitionParams"

  it should "produce disjoint, stable partition metadata" in {
    front.routers shouldBe Seq(0, 1)
    back.routers shouldBe Seq(2, 3)
    front.outLinks.map(_.id) shouldBe Seq(1)
    back.inLinks.map(_.id) shouldBe Seq(1)
    front.masters shouldBe Seq(0, 1)
    back.slaves shouldBe Seq(0, 1)
    front.hash should not be back.hash

    val ports = Reports.portsJson(ir, net, front, "cfg")
    Canonical.render(ports) shouldBe Canonical.render(Reports.portsJson(ir, net, front, "cfg"))

    val asyncIr = Ir.fromJson(
      Json.parse(Files.readString(Paths.get("src/loom/examples/chain_split_async.json"))))
    val asyncNet = NetworkParams.fromIr(asyncIr)
    val asyncFront = PartitionParams.fromIr(asyncIr, asyncNet,
      asyncIr.partitions.find(_.name == "front").get)
    val asyncPorts = Canonical.render(Reports.portsJson(asyncIr, asyncNet, asyncFront, "cfg"))
    asyncPorts should include("\"localClock\":\"core\"")
    asyncPorts should include("\"peerClock\":\"slow\"")
    asyncPorts should include("\"async\":true")
  }

  behavior of "LmPartitionTop"

  it should "route across a registered partition boundary" in {
    simulate(new LmChainSplitHarness(net, front, back)) { dut =>
      init(dut)

      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0x1000.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).bits.data.poke(0xabcL.U)
      dut.io.master(0).valid.poke(true.B)

      var accepted = false
      var guard = 0
      while (!accepted && guard < 40) {
        accepted = dut.io.master(0).ready.peek().litValue == 1
        dut.clock.step()
        guard += 1
      }
      accepted shouldBe true
      dut.io.master(0).valid.poke(false.B)

      var seen = false
      guard = 0
      while (!seen && guard < 60) {
        seen = dut.io.slave(1).valid.peek().litValue == 1
        if (!seen) { dut.clock.step(); guard += 1 }
      }
      seen shouldBe true
      dut.io.slave(1).bits.addr.expect(0x1000.U)
      dut.io.slave(1).bits.data.expect(0xabcL.U)
      dut.io.slave(1).bits.src.expect(0.U)

      dut.clock.step()
      dut.io.slaveRsp(1).bits.src.poke(0.U)
      dut.io.slaveRsp(1).bits.data.poke(0x1234L.U)
      dut.io.slaveRsp(1).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveRsp(1).valid.poke(false.B)

      var got = Option.empty[BigInt]
      guard = 0
      while (got.isEmpty && guard < 60) {
        if (dut.io.masterRsp(0).valid.peek().litValue == 1) {
          got = Some(dut.io.masterRsp(0).bits.data.peek().litValue)
          dut.clock.step()
        } else {
          dut.clock.step()
          guard += 1
        }
      }
      got shouldBe Some(BigInt(0x1234))
    }
  }
}
