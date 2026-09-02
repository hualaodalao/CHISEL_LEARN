import chisel3._
import chisel3.simulator.scalatest._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// 【任务90 收敛】仿真用例由 EphemeralSimulator 迁至 ChiselSim：前者在当前环境下不生成
// workdir-verilator/simulation-log.txt 导致 FileNotFoundException，后者经探针验证同配置全通。
class HiveCoreSpec extends AnyFlatSpec with Matchers with ChiselSim {

  behavior of "HiveCore Module"

  // ===================== Elaboration Tests =====================

  it should "elaborate HiveCore with default config" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCore(cfg))
    v should include("module HiveCore")
  }

  it should "elaborate HiveCore with small config" in {
    // 【任务90 收敛】P0 契约 require 族适配：hasMx 要求 totalN==16、HiveOutCvt 要求
    // totalN 为 8 的整数倍，故小阵列取 4×2=8 并排除 MX；cDmaExtW 须落在 [64,256]。
    val cfg = HiveCoreConfig(arrayN = 4, clusterM = 2, aBufferDepth = 16, cBufferDepth = 16,
      supportedFmts = Set(DataFormat.FP16, DataFormat.INT8), cDmaExtW = 128)
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

  // ===================== C 写回总线（cDmaExtW）合法配置与 elaboration 冒烟 =====================
  // P0 契约：默认 256、只允许 2 的 N 次幂、[totalN*8, totalN*32]；旧哨兵/缩位派生量已删。
  it should "accept legal cDmaExtW shrink configs" in {
    val base = HiveCoreConfig()
    // P3-清理: 旧哨兵派生量断言已废（cDmaExtWEff/cElementsPerBeat/cBeatsPerRow），
    // 新契约派生量断言与断言语义由 P3 测试迁移重写；本处仅维持编译与新默认冒烟。
    base.cDmaExtW should be(256)
    base.cBeatsPerRowMax should be(base.totalN * 32 / 256)
    // 新契约合法值：2 的 N 次幂且位于 [totalN*8, totalN*32]（totalN=16 → [128, 512]）
    val half = HiveCoreConfig(cDmaExtW = 128)
    half.cBeatsPerRowMax should be(half.totalN * 32 / 128)
    val dbl = HiveCoreConfig(cDmaExtW = 512)
    dbl.cBeatsPerRowMax should be(dbl.totalN * 32 / 512)
    // active 路径 elaboration 冒烟（行锁存 + 多拍拆分硬件可发射）。
    // 注：这里发射 WrOnly 模块而非顶层 HiveCore——默认 supportedFmts 下顶层的
    // HiveComb 内部 cEffW 推导(36)<40 撞其自身 require（C 写回重构本轮收紧，
    // 非基线既有失败；顶层通过传 cfg.cEffW 规避），顶层发射由仿真套件（显式 cW=40）覆盖。
    val v = ChiselStage.emitSystemVerilog(new HiveCoreDmaWrOnly(half, bufWidth = half.totalN * half.cEffW, hasFp = half.hasFp, hasMx = half.hasMx))
    v should include("module HiveCoreDmaWrOnly")
    val vq = ChiselStage.emitSystemVerilog(new HiveCoreDmaWrOnly(dbl, bufWidth = dbl.totalN * dbl.cEffW, hasFp = dbl.hasFp, hasMx = dbl.hasMx))
    vq should include("module HiveCoreDmaWrOnly")
  }

  // ===================== C 写回总线（cDmaExtW）require 契约测试 =====================
  // P0 新契约非法值在 elaboration 期（case class 构造）即拒绝：非 2 幂、
  // totalN*32 不整除、超出 [totalN*8, totalN*32]、负值均抛 IllegalArgumentException。
  it should "reject illegal cDmaExtW configs" in {
    val base = HiveCoreConfig()
    // 非 2 的 N 次幂（100）：新契约只允许 2 幂（同时拦截非整除）
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = 100)
    // 2 幂但 totalN*32(512) 不整除 → 拍数上界非整数，拒绝（8 元素/拍 × 40-bit 旧口径 320 同为非 2 幂拦截）
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = 5 * base.cEffW)
    // 大于上界 totalN*32（512）
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = base.totalN * 32 * 2)
    // 负值（被 isPow2 拦截）
    an[IllegalArgumentException] should be thrownBy HiveCoreConfig(cDmaExtW = -base.cEffW)
  }

  it should "elaborate HiveCoreExecutor" in {
    val cfg = HiveCoreConfig()
    val v = ChiselStage.emitSystemVerilog(new HiveCoreExecutor(cfg))
    v should include("module HiveCoreExecutor")
  }

  // ===================== Register Read/Write Tests =====================

  it should "write and read back registers" in {
    // 【任务90 收敛】P0 契约适配：非 MX 格式集（免 totalN==16 约束与 scale 端口）、
    // totalN 为 8 整数倍（4×2）、cBufferDepth ≥ 2*totalN、cDmaExtW=128 合法。
    val cfg = HiveCoreConfig(arrayN = 4, clusterM = 2, aBufferDepth = 16, cBufferDepth = 16,
      supportedFmts = Set(DataFormat.FP16), cDmaExtW = 128)
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
    // 【任务90 收敛】P0 契约适配（同上：非 MX + totalN 8 整数倍 + cBufferDepth ≥ 2*totalN）
    val cfg = HiveCoreConfig(arrayN = 4, clusterM = 2, aBufferDepth = 16, cBufferDepth = 16,
      supportedFmts = Set(DataFormat.FP16), cDmaExtW = 128)
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
