import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RegFileMem（NaxRiscv 移植）四种拓扑的 elaboration 冒烟测试 */
class RegFileMemTest extends AnyFlatSpec with Matchers {
  behavior of "RegFileMem (NaxRiscv port)"

  val rfpp = RegFilePortParam(addressWidth = 5, dataWidth = 32, hartIdWidth = 1, uopIdWidth = 8)
  val reads = Seq.fill(2)(RegFileReadParameter(withReady = false))
  val writes1 = Seq(RegFileWriteParameter(withReady = false))
  val writes2 = Seq.fill(2)(RegFileWriteParameter(withReady = false))

  it should "elaborate single-write Mem topology (async & sync read)" in {
    val vAsync = ChiselStage.emitSystemVerilog(new RegFileMem(
      rfpp, reads, writes1, headZero = true, syncRead = false, dualPortRam = true, regBasedRam = false))
    vAsync should include("module RegFileMem")

    val vSync = ChiselStage.emitSystemVerilog(new RegFileMem(
      rfpp, reads, writes1, headZero = true, syncRead = true, dualPortRam = true, regBasedRam = false))
    vSync should include("module RegFileMem")
  }

  it should "elaborate reg-based topology" in {
    val v = ChiselStage.emitSystemVerilog(new RegFileMem(
      rfpp, reads, writes1, headZero = true, syncRead = false, dualPortRam = false, regBasedRam = true))
    v should include("module RegFileMem")
  }

  it should "elaborate multi-write LVT topology (async & sync read)" in {
    val vAsync = ChiselStage.emitSystemVerilog(new RegFileMem(
      rfpp, reads, writes2, headZero = true, syncRead = false, dualPortRam = true, regBasedRam = false))
    vAsync should include("RamAsyncMwMux")

    val vSync = ChiselStage.emitSystemVerilog(new RegFileMem(
      rfpp, reads, writes2, headZero = true, syncRead = true, dualPortRam = true, regBasedRam = false))
    vSync should include("RamSyncMwMux")
  }
}
