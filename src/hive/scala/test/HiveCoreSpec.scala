import chisel3._
import chisel3.simulator.EphemeralSimulator._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HiveCoreSpec extends AnyFlatSpec with Matchers {

  behavior of "HiveCore Module"

  // ===================== Elaboration Tests =====================

  it should "elaborate HiveCore with default config" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCore(cfg))
    v should include("module HiveCore")
  }

  it should "elaborate HiveCore with small config" in {
    val cfg = HiveCoreConfig(arrayN = 4, clusterM = 1, aBufferDepth = 8, cBufferDepth = 8)
    val v = ChiselStage.emitSystemVerilog(new HiveCore(cfg))
    v should include("module HiveCore")
  }

  // ===================== Sub-module Elaboration Tests =====================

  it should "elaborate HiveCoreScratchpad" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCoreScratchpad(cfg))
    v should include("module HiveCoreScratchpad")
  }

  it should "elaborate HiveCoreDmaEngine" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCoreDmaEngine(cfg, cfg.totalN * cfg.aEffW))
    v should include("module HiveCoreDmaEngine")
  }

  it should "elaborate HiveCoreExecutor" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCoreExecutor(cfg))
    v should include("module HiveCoreExecutor")
  }

  // ===================== Register Read/Write Tests =====================

  it should "write and read back registers" in {
    val cfg = HiveCoreConfig(arrayN = 4, clusterM = 1, aBufferDepth = 4, cBufferDepth = 4)
    simulate(new HiveCore(cfg)) { dut =>
      // Apply explicit reset
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step()

      // Initialize
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dma0Ext.grant.poke(false.B)
      dut.io.dma1Ext.grant.poke(false.B)
      dut.io.dma0Ext.readData.valid.poke(false.B)
      dut.io.dma0Ext.readData.payload.poke(0.U)
      dut.io.dma1Ext.readData.valid.poke(false.B)
      dut.io.dma1Ext.readData.payload.poke(0.U)
      dut.io.dma0Ext.writeData.ready.poke(false.B)
      dut.io.dma1Ext.writeData.ready.poke(false.B)
      // B 权重只读 DMA 外部通道初始化
      dut.io.dma2Ext.rsp.valid.poke(false.B)
      dut.io.dma2Ext.rsp.payload.data.poke(0.U)
      dut.io.dma2Ext.rsp.payload.rsp.poke(false.B)
      dut.clock.step()

      // Write REG_M (addr 0x00) = 16, REG_N (addr 0x01) = 32
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.REG_WRITE2)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(1.U)
      dut.io.cmd.payload.data0.poke(16.U)
      dut.io.cmd.payload.data1.poke(32.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)
      dut.clock.step()

      // Read REG_M (addr 0x00)
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.REG_READ)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      // Wait for response
      var found = false
      for (_ <- 0 until 5 if !found) {
        if (dut.io.resp.valid.peek().litValue == 1) {
          dut.io.resp.payload.data.peek().litValue should be(16)
          found = true
        }
        dut.clock.step()
      }
      found should be(true)

      // Read REG_N (addr 0x01)
      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.REG_READ)
      dut.io.cmd.payload.reg0Addr.poke(1.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      found = false
      for (_ <- 0 until 5 if !found) {
        if (dut.io.resp.valid.peek().litValue == 1) {
          dut.io.resp.payload.data.peek().litValue should be(32)
          found = true
        }
        dut.clock.step()
      }
      found should be(true)
    }
  }

  // ===================== Scratchpad Tests =====================

  it should "push and pop data through A buffer" in {
    val cfg = HiveCoreConfig(arrayN = 4, clusterM = 1, aBufferDepth = 4, cBufferDepth = 4)
    simulate(new HiveCoreScratchpad(cfg)) { dut =>
      val dataWidth = cfg.totalN * cfg.aEffW

      // Apply explicit reset
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)
      dut.clock.step()

      // Initialize
      dut.io.aPush.valid.poke(false.B)
      dut.io.aPop.ready.poke(false.B)
      dut.io.bPush.valid.poke(false.B)
      dut.io.bPop.ready.poke(false.B)
      dut.io.cPush.valid.poke(false.B)
      dut.io.cPop.ready.poke(false.B)
      dut.io.flushA.poke(false.B)
      dut.io.flushB.poke(false.B)
      dut.io.flushC.poke(false.B)
      dut.clock.step()

      // Push data
      dut.io.aPush.valid.poke(true.B)
      dut.io.aPush.payload.poke(0xDEADBEEFL.U)
      dut.clock.step()
      dut.io.aPush.valid.poke(false.B)

      // Wait for data to propagate
      dut.clock.step()
      dut.clock.step()

      // Verify data is available at pop port
      dut.io.aPop.valid.peek().litValue should be(1)
      dut.io.aPop.payload.peek().litValue should be(0xDEADBEEFL)
    }
  }
}
