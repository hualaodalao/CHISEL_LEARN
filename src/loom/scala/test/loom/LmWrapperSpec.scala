package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.{Ir, Json, PartitionSpec}
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmWrapperSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/examples/chain_split.json"))))
  private val net = NetworkParams.fromIr(ir)
  private val front = PartitionParams.fromIr(ir, net, ir.partitions.find(_.name == "front").get)
  private val back = PartitionParams.fromIr(ir, net, ir.partitions.find(_.name == "back").get)
  private val all = PartitionParams.fromIr(ir, net,
    PartitionSpec("all", "core", Seq("mnet0", "r0", "r1", "r2", "r3", "snet0"), 1))

  private case class Trace(
      slave: Seq[(Int, BigInt, BigInt, BigInt)],
      master: Seq[(Int, BigInt)]
  )

  private def init(dut: LmWrapperTop): Unit = {
    for (i <- 0 until net.nMaster) {
      dut.io.master(i).valid.poke(false.B)
      dut.io.master(i).bits.write.poke(true.B)
      dut.io.master(i).bits.addr.poke(0.U)
      dut.io.master(i).bits.len.poke(0.U)
      dut.io.master(i).bits.src.poke(i.U)
      dut.io.master(i).bits.data.poke(0.U)
      dut.io.master(i).bits.strb.poke(0.U)
      dut.io.masterRsp(i).ready.poke(true.B)
    }
    for (j <- 0 until net.nSlave) {
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

  private def drive(dut: LmWrapperTop, ops: Seq[(Int, Long, Long, Boolean)]): Trace = {
    val slaveTrace = Seq.newBuilder[(Int, BigInt, BigInt, BigInt)]
    val masterTrace = Seq.newBuilder[(Int, BigInt)]

    for ((m, addr, data, write) <- ops) {
      dut.io.master(m).bits.write.poke(write.B)
      dut.io.master(m).bits.addr.poke(addr.U)
      dut.io.master(m).bits.src.poke(m.U)
      dut.io.master(m).bits.data.poke(data.U)
      dut.io.master(m).valid.poke(true.B)

      var accepted = false
      var guard = 0
      while (!accepted && guard < 80) {
        accepted = dut.io.master(m).ready.peek().litValue == 1
        dut.clock.step()
        guard += 1
      }
      accepted shouldBe true
      dut.io.master(m).valid.poke(false.B)

      val j = ((addr >> 12) & 1).toInt
      var seen = false
      guard = 0
      while (!seen && guard < 100) {
        seen = dut.io.slave(j).valid.peek().litValue == 1
        if (!seen) { dut.clock.step(); guard += 1 }
      }
      seen shouldBe true
      slaveTrace += ((j,
        dut.io.slave(j).bits.addr.peek().litValue,
        dut.io.slave(j).bits.data.peek().litValue,
        dut.io.slave(j).bits.src.peek().litValue))

      dut.clock.step()
      dut.io.slaveRsp(j).bits.src.poke(m.U)
      dut.io.slaveRsp(j).bits.data.poke((data + 0x1000).U)
      dut.io.slaveRsp(j).valid.poke(true.B)
      dut.clock.step()
      dut.io.slaveRsp(j).valid.poke(false.B)

      var got = Option.empty[BigInt]
      guard = 0
      while (got.isEmpty && guard < 100) {
        if (dut.io.masterRsp(m).valid.peek().litValue == 1) {
          got = Some(dut.io.masterRsp(m).bits.data.peek().litValue)
          dut.clock.step()
        } else {
          dut.clock.step()
          guard += 1
        }
      }
      got shouldBe defined
      masterTrace += ((m, got.get))
    }

    Trace(slaveTrace.result(), masterTrace.result())
  }

  behavior of "LmWrapperTop (P9)"

  it should "keep the transaction trace identical across partition counts" in {
    val ops = Seq(
      (0, 0x0000L, 0x11L, true),
      (1, 0x1000L, 0x22L, false),
      (0, 0x1000L, 0x33L, true),
      (1, 0x2000L, 0x44L, false)
    )

    var flat: Trace = null
    var split: Trace = null

    simulate(new LmWrapperTop(net, Seq(all))) { dut =>
      init(dut)
      flat = drive(dut, ops)
    }
    simulate(new LmWrapperTop(net, Seq(front, back))) { dut =>
      init(dut)
      split = drive(dut, ops)
    }

    split shouldBe flat
  }

  behavior of "LmPartitionStub (S4)"

  it should "answer every accepted request with exactly one error" in {
    simulate(new LmPartitionStub(net, front)) { dut =>
      dut.io.master(0).valid.poke(false.B)
      dut.io.master(0).bits.write.poke(true.B)
      dut.io.master(0).bits.addr.poke(0.U)
      dut.io.master(0).bits.len.poke(0.U)
      dut.io.master(0).bits.src.poke(0.U)
      dut.io.master(0).bits.data.poke(0.U)
      dut.io.master(0).bits.strb.poke(0.U)
      dut.io.masterRsp(0).ready.poke(true.B)
      for (s <- 0 until front.slaves.size) {
        dut.io.slave(s).ready.poke(true.B)
        dut.io.slaveRsp(s).valid.poke(false.B)
      }
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.master(0).bits.src.poke(1.U)
      dut.io.master(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.master(0).valid.poke(false.B)
      dut.io.masterRsp(0).valid.expect(true.B)
      dut.io.masterRsp(0).bits.err.expect(true.B)
      dut.io.masterRsp(0).bits.src.expect(1.U)
      dut.clock.step()
    }
  }

  it should "close the boundary credit with one error response" in {
    simulate(new LmPartitionStub(net, back)) { dut =>
      dut.io.boundaryInRdReq(0).valid.poke(false.B)
      dut.io.boundaryInRdReq(0).bits.addr.poke(0.U)
      dut.io.boundaryInRdReq(0).bits.len.poke(0.U)
      dut.io.boundaryInRdReq(0).bits.src.poke(0.U)
      dut.io.boundaryInRdReq(0).bits.vc.poke(0.U)
      dut.io.boundaryInRdData(0).ready.poke(true.B)
      dut.io.boundaryInWrData(0).valid.poke(false.B)
      dut.io.boundaryInWrData(0).bits.addr.poke(0.U)
      dut.io.boundaryInWrData(0).bits.data.poke(0.U)
      dut.io.boundaryInWrData(0).bits.strb.poke(0.U)
      dut.io.boundaryInWrData(0).bits.src.poke(0.U)
      dut.io.boundaryInWrData(0).bits.last.poke(true.B)
      dut.io.boundaryInWrData(0).bits.vc.poke(0.U)
      dut.io.boundaryInWrResp(0).ready.poke(true.B)
      for (s <- 0 until back.slaves.size) {
        dut.io.slave(s).ready.poke(true.B)
        dut.io.slaveRsp(s).valid.poke(false.B)
      }
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.boundaryInRdReq(0).bits.src.poke(1.U)
      dut.io.boundaryInRdReq(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.boundaryInRdReq(0).valid.poke(false.B)
      dut.io.boundaryInRdData(0).valid.expect(true.B)
      dut.io.boundaryInRdData(0).bits.err.expect(true.B)
      dut.io.boundaryInRdData(0).bits.src.expect(1.U)

      dut.io.boundaryInWrData(0).bits.src.poke(1.U)
      dut.io.boundaryInWrData(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.boundaryInWrData(0).valid.poke(false.B)
      dut.io.boundaryInWrResp(0).valid.expect(true.B)
      dut.io.boundaryInWrResp(0).bits.err.expect(true.B)
      dut.io.boundaryInWrResp(0).bits.src.expect(1.U)
    }
  }
}
