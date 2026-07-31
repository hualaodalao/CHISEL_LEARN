import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** 多端口 RAM 库（SpinalHDL 移植）的 elaboration 冒烟测试 */
class MultiportRamTest extends AnyFlatSpec with Matchers {
  behavior of "Multiport RAM (SpinalHDL port)"

  it should "elaborate RamAsyncMwXor" in {
    val v = ChiselStage.emitSystemVerilog(new RamAsyncMwXor(UInt(8.W), 32, 2, 2))
    v should include("module RamAsyncMwXor")
  }

  it should "elaborate RamAsyncMwReg" in {
    val v = ChiselStage.emitSystemVerilog(new RamAsyncMwReg(UInt(8.W), 16, 2, 2))
    v should include("module RamAsyncMwReg")
  }

  it should "elaborate RamAsyncMwMux" in {
    val v = ChiselStage.emitSystemVerilog(new RamAsyncMwMux(UInt(8.W), 32, 2, 2))
    v should include("module RamAsyncMwMux")
    v should include("RamAsyncMwReg") // location 表
  }

  it should "elaborate RamSyncMwXor and RamSyncMwMux" in {
    val v1 = ChiselStage.emitSystemVerilog(new RamSyncMwXor(UInt(8.W), 32, 2, 2))
    v1 should include("module RamSyncMwXor")
    val v2 = ChiselStage.emitSystemVerilog(new RamSyncMwMux(UInt(8.W), 32, 2, 2))
    v2 should include("module RamSyncMwMux")
  }

  it should "elaborate RamMr" in {
    val v = ChiselStage.emitSystemVerilog(new RamMr(UInt(8.W), 32, 3))
    v should include("module RamMr")
  }
}
