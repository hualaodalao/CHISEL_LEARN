package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiNiSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )
  private val c = ChiConfig()

  "ChiNiError" should "answer an unmapped request with Comp NDERR, not forward" in {
    simulate(new ChiNiError(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.hit.poke(false.B)
      dut.io.fwd.ready.poke(true.B); dut.io.rsp.ready.poke(true.B)
      dut.io.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.req.bits.txnId.poke(3.U); dut.io.req.bits.srcId.poke(1.U)
      dut.io.req.valid.poke(true.B)
      dut.clock.step()
      dut.io.req.valid.poke(false.B)
      dut.io.fwd.valid.expect(false.B)
      dut.io.rsp.valid.expect(true.B)
      dut.io.rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.rsp.bits.respErr.expect(ChiRespErr.NDERR.U)
      dut.io.rsp.bits.txnId.expect(3.U)
      dut.io.rsp.bits.tgtId.expect(1.U)
      dut.clock.step()
      dut.io.rsp.valid.expect(false.B)
    }
  }

  it should "pass a mapped request through to the fabric" in {
    simulate(new ChiNiError(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.hit.poke(true.B)
      dut.io.fwd.ready.poke(true.B); dut.io.rsp.ready.poke(true.B)
      dut.io.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
      dut.io.req.bits.txnId.poke(8.U)
      dut.io.req.valid.poke(true.B)
      dut.io.fwd.valid.expect(true.B)
      dut.io.fwd.bits.txnId.expect(8.U)
      dut.io.rsp.valid.expect(false.B)
      dut.io.req.ready.expect(true.B)
    }
  }

  private class SamProbe(sam: Seq[ChiSamEntry]) extends Module {
    val io = IO(new Bundle {
      val addr = Input(UInt(c.addrWidth.W))
      val tgtId = Output(UInt(c.nodeIdWidth.W))
      val hit = Output(Bool())
    })
    val (t, h) = ChiSamDecode.withHit(io.addr, c.addrWidth, sam, c.nodeIdWidth)
    io.tgtId := t; io.hit := h
  }

  "ChiSamDecode.withHit" should "flag hit only inside a window" in {
    val sam = Seq(ChiSamEntry(0x1000L, 0x1000L, 2))
    simulate(new SamProbe(sam)) { dut =>
      dut.io.addr.poke(0x1000L.U); dut.io.hit.expect(true.B); dut.io.tgtId.expect(2.U)
      dut.io.addr.poke(0x1FFFL.U); dut.io.hit.expect(true.B); dut.io.tgtId.expect(2.U)
      dut.io.addr.poke(0x2000L.U); dut.io.hit.expect(false.B)
      dut.io.addr.poke(0x0L.U); dut.io.hit.expect(false.B)
    }
  }
}