package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.chi._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ChiTransactionSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  "ChiBurstSplit" should "transactionize a byte span into <=64B chunks" in {
    ChiBurstSplit.split(0L, 256) shouldBe Seq(
      ChiBurstSplit.Txn(0, 64), ChiBurstSplit.Txn(64, 64),
      ChiBurstSplit.Txn(128, 64), ChiBurstSplit.Txn(192, 64))
    ChiBurstSplit.split(0L, 100) shouldBe Seq(
      ChiBurstSplit.Txn(0, 64), ChiBurstSplit.Txn(64, 36))
  }

  "ChiWriteSink" should "run REQ->DBIDResp->DAT->Comp and reassemble by DataID" in {
    val c = ChiConfig()
    simulate(new ChiWriteSink(c, 2)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.req.valid.poke(false.B); dut.io.rsp.ready.poke(true.B)
      dut.io.dat.valid.poke(false.B); dut.io.wrDone.ready.poke(true.B)

      dut.io.req.bits.opcode.poke(ChiOp.WriteNoSnpFull.U)
      dut.io.req.bits.txnId.poke(5.U); dut.io.req.bits.srcId.poke(0.U)
      dut.io.req.valid.poke(true.B); dut.clock.step(); dut.io.req.valid.poke(false.B)

      dut.io.rsp.valid.expect(true.B)
      dut.io.rsp.bits.opcode.expect(ChiOp.RspDBIDResp.U)
      dut.io.rsp.bits.txnId.expect(5.U)
      dut.clock.step() // grant accepted -> data phase

      dut.io.dat.bits.dataId.poke(0.U); dut.io.dat.bits.data.poke(0x1111L.U)
      dut.io.dat.valid.poke(true.B); dut.clock.step()
      dut.io.dat.bits.dataId.poke(2.U); dut.io.dat.bits.data.poke(0x2222L.U)
      dut.clock.step(); dut.io.dat.valid.poke(false.B)

      dut.io.rsp.valid.expect(true.B)
      dut.io.rsp.bits.opcode.expect(ChiOp.RspComp.U)
      dut.io.wrDone.valid.expect(true.B)
      dut.io.wrDone.bits.expect((BigInt(0x2222) << 256 | BigInt(0x1111)).U)
      dut.clock.step()
      dut.io.rsp.valid.expect(false.B)
    }
  }

  "ChiReadSource" should "emit CompData flits sliced from the 64B word" in {
    val c = ChiConfig()
    simulate(new ChiReadSource(c)) { dut =>
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
      dut.io.req.valid.poke(false.B); dut.io.dat.ready.poke(true.B)
      dut.io.rdData.poke((BigInt(0xAAAA) << 256 | BigInt(0xBBBB)).U)

      dut.io.req.bits.opcode.poke(ChiOp.ReadNoSnp.U)
      dut.io.req.bits.txnId.poke(9.U); dut.io.req.bits.srcId.poke(1.U)
      dut.io.req.valid.poke(true.B); dut.clock.step(); dut.io.req.valid.poke(false.B)

      dut.io.dat.valid.expect(true.B)
      dut.io.dat.bits.opcode.expect(ChiOp.DatCompData.U)
      dut.io.dat.bits.txnId.expect(9.U)
      dut.io.dat.bits.dataId.expect(0.U)
      dut.io.dat.bits.data.expect(0xBBBBL.U)
      dut.clock.step()
      dut.io.dat.bits.dataId.expect(2.U)
      dut.io.dat.bits.data.expect(0xAAAAL.U)
      dut.clock.step()
      dut.io.dat.valid.expect(false.B)
    }
  }
}