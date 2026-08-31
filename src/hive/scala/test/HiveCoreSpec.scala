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

  it should "elaborate HiveCoreDmaRdOnly" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCoreDmaRdOnly(cfg, bufWidth = cfg.totalN * cfg.aEffW, bufDepth = cfg.aBufferDepth))
    v should include("module HiveCoreDmaRdOnly")
  }

  it should "elaborate HiveCoreDmaWrOnly" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCoreDmaWrOnly(cfg, bufWidth = cfg.totalN * cfg.cEffW))
    v should include("module HiveCoreDmaWrOnly")
  }

  // ===================== C 写回缩位（cDmaExtW）合法配置与 elaboration 冒烟 =====================
  // P1：哨兵 0 = 满宽保持现状；合法缩位（cExtW/2、cExtW/4）派生量正确且可发射（active 路径）
  it should "accept legal cDmaExtW shrink configs" in {
    val base = HiveCoreConfig()
    // 哨兵 0：满宽一拍一行（出厂默认，非破坏）
    base.cDmaExtWEff should be(base.cExtW)
    base.cElementsPerBeat should be(base.totalN)
    base.cBeatsPerRow should be(1)
    // 半宽：E=8（8 元素/拍），每行 2 拍；缩位拍数变多的容量权衡由 cBufferDepth 吸收
    val half = HiveCoreConfig(cDmaExtW = base.cExtW / 2)
    half.cDmaExtWEff should be(base.cExtW / 2)
    half.cElementsPerBeat should be(base.totalN / 2)
    half.cBeatsPerRow should be(2)
    // 1/4 宽：E=4，每行 4 拍
    val quarter = HiveCoreConfig(cDmaExtW = base.cExtW / 4)
    quarter.cElementsPerBeat should be(base.totalN / 4)
    quarter.cBeatsPerRow should be(4)
    // active 路径 elaboration 冒烟（行锁存 + 多拍拆分硬件可发射）。
    // 注：这里发射 WrOnly 模块而非顶层 HiveCore——默认 supportedFmts 下顶层的
    // HiveComb 内部 cEffW 推导(36)<40 撞其自身 require，属既有失败（与本改动无关），
    // 顶层发射由仿真套件（显式 cW=40）覆盖。
    val v = ChiselStage.emitSystemVerilog(new HiveCoreDmaWrOnly(half, bufWidth = half.totalN * half.cEffW, hasFp = half.hasFp, hasMx = half.hasMx))
    v should include("module HiveCoreDmaWrOnly")
    val vq = ChiselStage.emitSystemVerilog(new HiveCoreDmaWrOnly(quarter, bufWidth = quarter.totalN * quarter.cEffW, hasFp = quarter.hasFp, hasMx = quarter.hasMx))
    vq should include("module HiveCoreDmaWrOnly")
  }

  // ===================== C 写回缩位（cDmaExtW）require 契约测试 =====================
  // 非法缩位在 elaboration 期（case class 构造）即拒绝：非 cEffW 整数倍、
  // 不整除 N、大于 cExtW、负值均抛 IllegalArgumentException。
  it should "reject illegal cDmaExtW configs" in {
    val base = HiveCoreConfig()
    // 非 cEffW 整数倍（100 % 40 != 0）：缩位必须按元素位宽拆拍
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = 100)
    // 不整除 N：E = 5，totalN(16) % 5 != 0 → 行内拆拍非整数拍，拒绝（用户需求「必须是 N 倍数所见」）
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = 5 * base.cEffW)
    // 大于整行位宽 cExtW
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = base.cExtW + base.cEffW)
    // 负值（-40 % 40 == 0 能过首条，被 (0, cExtW] 范围条拦截）
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = -base.cEffW)
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
      // A 只读 DMA 外部通道（req/rsp 流接口）：req 常就绪，rsp 初始无效
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      // C 写回 DMA 外部通道（req{addr,data}/rsp{err}）：req 不接收，rsp 无效
      dut.io.dmaC.req.ready.poke(false.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      // B 权重只读 DMA 外部通道初始化
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
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
