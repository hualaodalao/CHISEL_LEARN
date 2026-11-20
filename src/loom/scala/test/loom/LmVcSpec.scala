package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmVcSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val Ctl = 0
  private val Lat = 1
  private val Bulk = 2

  private def init(dut: LmTop, p: FabricParams): Unit = {
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

  behavior of "LmVcQueue"

  it should "fill to its depth, backpressure, and preserve order" in {
    simulate(new LmVcQueue(new LmWrData(32, 64, 1), 2)) { dut =>
      dut.io.enq.bits.addr.poke(0.U)
      dut.io.enq.bits.src.poke(0.U)
      dut.io.enq.bits.vc.poke(0.U)
      dut.io.enq.bits.data.poke(0x11L.U)
      dut.io.deq.ready.poke(false.B)

      dut.io.enq.valid.poke(true.B)
      dut.clock.step()
      dut.io.enq.bits.data.poke(0x22L.U)
      dut.clock.step()

      dut.io.enq.ready.expect(false.B)
      dut.io.count.expect(2.U)

      dut.io.enq.valid.poke(false.B)
      dut.io.deq.ready.poke(true.B)
      dut.io.deq.valid.expect(true.B)
      dut.io.deq.bits.data.expect(0x11L.U)
      dut.clock.step()
      dut.io.deq.bits.data.expect(0x22L.U)
      dut.clock.step()
      dut.io.deq.valid.expect(false.B)
      dut.io.count.expect(0.U)
    }
  }

  behavior of "class packet guards"

  it should "accept a bulk packet up to maxPacketBeats" in {
    val p = FabricParams.simple("pkt", 2, 2, 32, 64, masterClass = Seq(Bulk, Bulk))
    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      dut.io.masterRdReq(0).bits.len.poke(3.U) // 4 beats, the bulk maximum
      dut.io.masterRdReq(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.masterRdReq(0).ready.expect(true.B)
    }
  }

  it should "reject a bulk packet above maxPacketBeats" in {
    val p = FabricParams.simple("pkt", 2, 2, 32, 64, masterClass = Seq(Bulk, Bulk))
    intercept[Throwable] {
      simulate(new LmTop(p)) { dut =>
        init(dut, p)
        dut.io.masterRdReq(0).bits.len.poke(4.U) // 5 beats
        dut.io.masterRdReq(0).valid.poke(true.B)
        dut.clock.step()
      }
    }
  }

  it should "reject a multi-beat control request (G7)" in {
    val p = FabricParams.simple("pkt", 2, 2, 32, 64, masterClass = Seq(Ctl, Bulk))
    intercept[Throwable] {
      simulate(new LmTop(p)) { dut =>
        init(dut, p)
        dut.io.masterRdReq(0).bits.len.poke(1.U)
        dut.io.masterRdReq(0).valid.poke(true.B)
        dut.clock.step()
      }
    }
  }

  it should "accept a latency packet at its own limit" in {
    val p = FabricParams.simple("pkt", 2, 2, 32, 64, masterClass = Seq(Lat, Bulk))
    simulate(new LmTop(p)) { dut =>
      init(dut, p)
      dut.io.masterRdReq(0).bits.len.poke(1.U) // 2 beats, the lat maximum
      dut.io.masterRdReq(0).valid.poke(true.B)
      dut.clock.step()
      dut.io.masterRdReq(0).ready.expect(true.B)
    }
  }
}
