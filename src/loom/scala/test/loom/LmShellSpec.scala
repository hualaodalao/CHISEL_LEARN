package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmShellSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  behavior of "LmShellNative"

  it should "serialize a two-beat write and merge the responses" in {
    simulate(new LmShellNative(32, 64, 1, 0)) { dut =>
      dut.io.req.valid.poke(false.B)
      dut.io.req.bits.write.poke(false.B)
      dut.io.req.bits.addr.poke(0.U)
      dut.io.req.bits.len.poke(0.U)
      dut.io.wdata.valid.poke(false.B)
      dut.io.wdata.bits.data.poke(0.U)
      dut.io.wdata.bits.strb.poke(0.U)
      dut.io.wdata.bits.last.poke(false.B)
      dut.io.rsp.ready.poke(true.B)
      dut.io.rdata.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.req.bits.write.poke(true.B)
      dut.io.req.bits.addr.poke(0x100.U)
      dut.io.req.bits.len.poke(1.U)
      dut.io.req.valid.poke(true.B)
      dut.clock.step()
      dut.io.req.valid.poke(false.B)

      dut.io.wdata.bits.data.poke(0xaaL.U)
      dut.io.wdata.bits.strb.poke(0xff.U)
      dut.io.wdata.valid.poke(true.B)
      dut.io.fabWrData.valid.expect(true.B)
      dut.io.fabWrData.bits.addr.expect(0x100.U)
      dut.io.fabWrData.bits.data.expect(0xaaL.U)
      dut.io.fabWrData.bits.strb.expect(0xff.U)
      dut.io.fabRdReq.valid.expect(false.B)
      dut.clock.step()

      dut.io.wdata.bits.data.poke(0xbbL.U)
      dut.io.wdata.bits.last.poke(true.B)
      dut.io.fabWrData.bits.addr.expect(0x108.U)
      dut.io.fabWrData.bits.data.expect(0xbbL.U)
      dut.clock.step()
      dut.io.wdata.valid.poke(false.B)
      dut.clock.step() // command moves from SEND to DRAIN

      // One write response per beat; a write response carries no data.
      dut.io.fabWrResp.valid.poke(true.B)
      dut.clock.step()
      dut.clock.step()
      dut.io.fabWrResp.valid.poke(false.B)

      dut.io.rsp.valid.expect(true.B)
      dut.io.rsp.bits.err.expect(false.B)
    }
  }

  it should "split a two-beat read into ordered rdata beats" in {
    simulate(new LmShellNative(32, 64, 1, 0)) { dut =>
      dut.io.req.valid.poke(false.B)
      dut.io.req.bits.write.poke(false.B)
      dut.io.req.bits.addr.poke(0.U)
      dut.io.req.bits.len.poke(0.U)
      dut.io.wdata.valid.poke(false.B)
      dut.io.rsp.ready.poke(true.B)
      dut.io.rdata.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.req.bits.addr.poke(0x200.U)
      dut.io.req.bits.len.poke(1.U)
      dut.io.req.valid.poke(true.B)
      dut.clock.step()
      dut.io.req.valid.poke(false.B)

      dut.io.fabRdReq.valid.expect(true.B)
      dut.io.fabRdReq.bits.addr.expect(0x200.U)
      dut.io.fabRdReq.bits.len.expect(0.U)
      dut.clock.step()
      dut.io.fabRdReq.bits.addr.expect(0x208.U)
      dut.clock.step()
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabRdData.valid.poke(true.B)
      dut.io.fabRdData.bits.data.poke(0x11L.U)
      dut.io.rdata.valid.expect(true.B)
      dut.io.rdata.bits.data.expect(0x11L.U)
      dut.io.rdata.bits.last.expect(false.B)
      dut.clock.step()

      dut.io.fabRdData.bits.data.poke(0x22L.U)
      dut.io.rdata.bits.data.expect(0x22L.U)
      dut.io.rdata.bits.last.expect(true.B)
      dut.clock.step()
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.rdata.valid.expect(false.B)
    }
  }

  behavior of "LmShellAxiLite4"

  it should "convert single-beat write and read transactions" in {
    simulate(new LmShellAxiLite4(32, 64, 1, 0)) { dut =>
      dut.io.aw.valid.poke(false.B)
      dut.io.aw.bits.addr.poke(0.U)
      dut.io.aw.bits.prot.poke(0.U)
      dut.io.w.valid.poke(false.B)
      dut.io.w.bits.data.poke(0.U)
      dut.io.w.bits.strb.poke(0.U)
      dut.io.b.ready.poke(true.B)
      dut.io.ar.valid.poke(false.B)
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.prot.poke(0.U)
      dut.io.r.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.aw.bits.addr.poke(0x300.U)
      dut.io.aw.valid.poke(true.B)
      dut.io.w.bits.data.poke(0x55L.U)
      dut.io.w.bits.strb.poke(0xff.U)
      dut.io.w.valid.poke(true.B)
      dut.clock.step()
      dut.io.aw.valid.poke(false.B)

      dut.io.fabWrData.valid.expect(true.B)
      dut.io.fabWrData.bits.addr.expect(0x300.U)
      dut.io.fabWrData.bits.data.expect(0x55L.U)
      dut.clock.step()
      dut.clock.step() // SEND -> DRAIN
      dut.io.w.valid.poke(false.B)

      dut.io.fabWrResp.valid.poke(true.B)
      dut.clock.step()
      dut.io.fabWrResp.valid.poke(false.B)
      dut.io.b.valid.expect(true.B)
      dut.io.b.bits.resp.expect(0.U)

      dut.io.ar.bits.addr.poke(0x400.U)
      dut.io.ar.valid.poke(true.B)
      dut.clock.step() // B handshake, back to idle
      dut.clock.step() // AR accepted
      dut.io.ar.valid.poke(false.B)
      dut.io.fabRdReq.valid.expect(true.B)
      dut.io.fabRdReq.bits.addr.expect(0x400.U)
      dut.clock.step()
      dut.clock.step() // SEND -> DRAIN
      dut.io.fabRdData.valid.poke(true.B)
      dut.io.fabRdData.bits.data.poke(0x77L.U)
      dut.io.r.valid.expect(true.B)
      dut.io.r.bits.data.expect(0x77L.U)
      dut.clock.step()
      dut.io.fabRdData.valid.poke(false.B)
    }
  }

  behavior of "LmShellAxi4"

  it should "run an INCR write burst and propagate the error response" in {
    simulate(new LmShellAxi4(32, 64, 1, 0)) { dut =>
      dut.io.aw.valid.poke(false.B)
      dut.io.aw.bits.addr.poke(0.U)
      dut.io.aw.bits.len.poke(0.U)
      dut.io.aw.bits.size.poke(3.U)
      dut.io.aw.bits.burst.poke(1.U)
      dut.io.w.valid.poke(false.B)
      dut.io.w.bits.data.poke(0.U)
      dut.io.w.bits.strb.poke(0.U)
      dut.io.w.bits.last.poke(false.B)
      dut.io.b.ready.poke(true.B)
      dut.io.ar.valid.poke(false.B)
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.len.poke(0.U)
      dut.io.ar.bits.size.poke(3.U)
      dut.io.ar.bits.burst.poke(1.U)
      dut.io.r.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.aw.bits.addr.poke(0x500.U)
      dut.io.aw.bits.len.poke(1.U)
      dut.io.aw.valid.poke(true.B)
      dut.clock.step()
      dut.io.aw.valid.poke(false.B)

      dut.io.w.bits.data.poke(0x1L.U)
      dut.io.w.bits.strb.poke(0xff.U)
      dut.io.w.valid.poke(true.B)
      dut.io.fabWrData.valid.expect(true.B)
      dut.io.fabWrData.bits.addr.expect(0x500.U)
      dut.io.fabWrData.bits.data.expect(0x1L.U)
      dut.clock.step()
      dut.io.w.bits.data.poke(0x2L.U)
      dut.io.w.bits.last.poke(true.B)
      dut.io.fabWrData.bits.addr.expect(0x508.U)
      dut.io.fabWrData.bits.data.expect(0x2L.U)
      dut.clock.step()
      dut.io.w.valid.poke(false.B)
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabWrResp.valid.poke(true.B)
      dut.io.fabWrResp.bits.err.poke(false.B)
      dut.clock.step()
      dut.io.fabWrResp.bits.err.poke(true.B)
      dut.clock.step()
      dut.io.fabWrResp.valid.poke(false.B)
      dut.io.b.valid.expect(true.B)
      dut.io.b.bits.resp.expect(2.U)
    }
  }

  it should "run an INCR read burst with last flags" in {
    simulate(new LmShellAxi4(32, 64, 1, 0)) { dut =>
      dut.io.aw.valid.poke(false.B)
      dut.io.aw.bits.addr.poke(0.U)
      dut.io.aw.bits.len.poke(0.U)
      dut.io.aw.bits.size.poke(3.U)
      dut.io.aw.bits.burst.poke(1.U)
      dut.io.w.valid.poke(false.B)
      dut.io.ar.valid.poke(false.B)
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.len.poke(0.U)
      dut.io.ar.bits.size.poke(3.U)
      dut.io.ar.bits.burst.poke(1.U)
      dut.io.r.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.ar.bits.addr.poke(0x600.U)
      dut.io.ar.bits.len.poke(1.U)
      dut.io.ar.valid.poke(true.B)
      dut.clock.step()
      dut.io.ar.valid.poke(false.B)

      dut.io.fabRdReq.valid.expect(true.B)
      dut.io.fabRdReq.bits.addr.expect(0x600.U)
      dut.io.fabWrData.valid.expect(false.B)
      dut.clock.step()
      dut.io.fabRdReq.bits.addr.expect(0x608.U)
      dut.clock.step()
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabRdData.valid.poke(true.B)
      dut.io.fabRdData.bits.data.poke(0x31L.U)
      dut.io.r.valid.expect(true.B)
      dut.io.r.bits.data.expect(0x31L.U)
      dut.io.r.bits.last.expect(false.B)
      dut.clock.step()

      dut.io.fabRdData.bits.data.poke(0x32L.U)
      dut.io.r.bits.data.expect(0x32L.U)
      dut.io.r.bits.last.expect(true.B)
      dut.clock.step()
      dut.io.fabRdData.valid.poke(false.B)
    }
  }

  it should "echo the AXI4 transaction id on B and R" in {
    simulate(new LmShellAxi4(32, 64, 1, 0)) { dut =>
      dut.io.aw.valid.poke(false.B)
      dut.io.aw.bits.addr.poke(0.U)
      dut.io.aw.bits.len.poke(0.U)
      dut.io.aw.bits.size.poke(3.U)
      dut.io.aw.bits.burst.poke(1.U)
      dut.io.aw.bits.id.poke(0.U)
      dut.io.w.valid.poke(false.B)
      dut.io.w.bits.data.poke(0.U)
      dut.io.w.bits.strb.poke(0.U)
      dut.io.w.bits.last.poke(false.B)
      dut.io.b.ready.poke(true.B)
      dut.io.ar.valid.poke(false.B)
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.len.poke(0.U)
      dut.io.ar.bits.size.poke(3.U)
      dut.io.ar.bits.burst.poke(1.U)
      dut.io.ar.bits.id.poke(0.U)
      dut.io.r.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabRdData.bits.id.poke(0.U)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.io.fabWrResp.bits.id.poke(0.U)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.aw.bits.addr.poke(0x100.U)
      dut.io.aw.bits.id.poke(6.U)
      dut.io.aw.valid.poke(true.B)
      dut.clock.step()
      dut.io.aw.valid.poke(false.B)

      dut.io.w.bits.data.poke(0x11L.U)
      dut.io.w.bits.strb.poke(0xff.U)
      dut.io.w.bits.last.poke(true.B)
      dut.io.w.valid.poke(true.B)
      dut.io.fabWrData.bits.id.expect(6.U)
      dut.clock.step()
      dut.io.w.valid.poke(false.B)
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabWrResp.bits.id.poke(6.U)
      dut.io.fabWrResp.valid.poke(true.B)
      dut.clock.step()
      dut.io.fabWrResp.valid.poke(false.B)
      dut.io.b.valid.expect(true.B)
      dut.io.b.bits.id.expect(6.U)
      dut.clock.step() // consume B

      dut.io.ar.bits.addr.poke(0x200.U)
      dut.io.ar.bits.id.poke(3.U)
      dut.io.ar.valid.poke(true.B)
      dut.clock.step()
      dut.io.ar.valid.poke(false.B)
      dut.io.fabRdReq.bits.id.expect(3.U)
      dut.clock.step() // beat
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabRdData.bits.id.poke(3.U)
      dut.io.fabRdData.bits.data.poke(0x22L.U)
      dut.io.fabRdData.valid.poke(true.B)
      dut.io.r.valid.expect(true.B)
      dut.io.r.bits.id.expect(3.U)
      dut.io.r.bits.data.expect(0x22L.U)
      dut.clock.step()
      dut.io.fabRdData.valid.poke(false.B)
    }
  }

  behavior of "LmShellTlUl"

  it should "convert Put and Get into fabric requests" in {
    simulate(new LmShellTlUl(32, 64, 1, 0)) { dut =>
      dut.io.a.valid.poke(false.B)
      dut.io.a.bits.opcode.poke(0.U)
      dut.io.a.bits.param.poke(0.U)
      dut.io.a.bits.size.poke(3.U)
      dut.io.a.bits.source.poke(0.U)
      dut.io.a.bits.address.poke(0.U)
      dut.io.a.bits.data.poke(0.U)
      dut.io.a.bits.mask.poke(0.U)
      dut.io.d.ready.poke(true.B)
      dut.io.fabRdReq.ready.poke(true.B)
      dut.io.fabWrData.ready.poke(true.B)
      dut.io.fabRdData.valid.poke(false.B)
      dut.io.fabWrResp.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      dut.io.a.bits.opcode.poke(0.U) // PutFullData
      dut.io.a.bits.address.poke(0x700.U)
      dut.io.a.bits.data.poke(0x99L.U)
      dut.io.a.bits.mask.poke(0xff.U)
      dut.io.a.bits.source.poke(5.U)
      dut.io.a.valid.poke(true.B)
      dut.clock.step()
      dut.io.a.valid.poke(false.B)

      dut.io.fabWrData.valid.expect(true.B)
      dut.io.fabWrData.bits.addr.expect(0x700.U)
      dut.io.fabWrData.bits.data.expect(0x99L.U)
      dut.io.fabWrData.bits.strb.expect(0xff.U)
      dut.io.fabRdReq.valid.expect(false.B)
      dut.clock.step()
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabWrResp.valid.poke(true.B)
      dut.clock.step()
      dut.io.fabWrResp.valid.poke(false.B)
      dut.io.d.valid.expect(true.B)
      dut.io.d.bits.opcode.expect(0.U)
      dut.io.d.bits.source.expect(5.U)

      dut.io.a.bits.opcode.poke(4.U) // Get
      dut.io.a.bits.address.poke(0x800.U)
      dut.io.a.bits.source.poke(7.U)
      dut.io.a.valid.poke(true.B)
      dut.clock.step() // D handshake, back to idle
      dut.clock.step() // Get accepted
      dut.io.a.valid.poke(false.B)
      dut.io.fabRdReq.valid.expect(true.B)
      dut.io.fabRdReq.bits.addr.expect(0x800.U)
      dut.io.fabWrData.valid.expect(false.B)
      dut.clock.step()
      dut.clock.step() // SEND -> DRAIN

      dut.io.fabRdData.valid.poke(true.B)
      dut.io.fabRdData.bits.data.poke(0x44L.U)
      dut.io.d.valid.expect(true.B)
      dut.io.d.bits.opcode.expect(1.U)
      dut.io.d.bits.source.expect(7.U)
      dut.io.d.bits.data.expect(0x44L.U)
      dut.clock.step()
    }
  }

  behavior of "G4 response isolation"

  it should "never turn a response into a request" in {
    simulate(new LmShellCore(32, 64, 1, 0)) { dut =>
      dut.io.cmd.valid.poke(false.B)
      dut.io.cmd.bits.write.poke(false.B)
      dut.io.cmd.bits.addr.poke(0.U)
      dut.io.cmd.bits.beats.poke(1.U)
      dut.io.wdata.valid.poke(false.B)
      dut.io.wdata.bits.data.poke(0.U)
      dut.io.wdata.bits.strb.poke(0.U)
      dut.io.wdata.bits.last.poke(false.B)
      dut.io.bresp.ready.poke(true.B)
      dut.io.rdata.ready.poke(true.B)
      dut.io.rdReq.ready.poke(true.B)
      dut.io.wrData.ready.poke(true.B)
      dut.io.rdData.valid.poke(true.B)
      dut.io.rdData.bits.src.poke(0.U)
      dut.io.rdData.bits.data.poke(0x5aL.U)
      dut.io.rdData.bits.err.poke(false.B)
      dut.io.wrResp.valid.poke(true.B)
      dut.io.wrResp.bits.src.poke(0.U)
      dut.io.wrResp.bits.err.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      for (_ <- 0 until 4) {
        dut.io.rdReq.valid.expect(false.B)
        dut.io.wrData.valid.expect(false.B)
        dut.io.rdata.valid.expect(false.B)
        dut.clock.step()
      }
    }
  }
}
