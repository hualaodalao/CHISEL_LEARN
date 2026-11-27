import chisel3._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** LLC (Last Level Cache) elaboration-based功能测试。
  *
  * 遵循项目风格（参见 Axi4Test）：通过 ChiselStage.emitSystemVerilog 生成
  * SystemVerilog，并对 top-level module 名称与关键 AXI 信号名做子串断言。
  */
class LlcTest extends AnyFlatSpec with Matchers {
  behavior of "Llc (Last Level Cache)"

  it should "elaborate to SystemVerilog with default config" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig()))
    v should include("module Llc")
    // AXI4 slave 前端（cpu）
    v should include("cpu_ar_valid")
    v should include("cpu_r_payload_data")
    // AXI4 master 后端（mem）
    v should include("mem_aw_valid")
    v should include("mem_ar_valid")
  }

  it should "elaborate with a custom config" in {
    val v = ChiselStage.emitSystemVerilog(
      new Llc(LlcConfig(capacity = 128 * 1024, ways = 8, mshrEntries = 2, wayPartitions = 2))
    )
    v should include("module Llc")
  }

  it should "elaborate with single-entry write-merge buffer and B response tracking" in {
    val v = ChiselStage.emitSystemVerilog(
      new Llc(LlcConfig(capacity = 8 * 1024, wmBufEntries = 1, bFifoDepth = 1))
    )
    v should include("module Llc")
    v should include("module LlcWriteMergeBuffer")
  }

  it should "elaborate with a single way partition" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig(wayPartitions = 1)))
    v should include("module Llc")
  }

  it should "elaborate with the ctrl (AxiLite4) port" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig()))
    v should include("module Llc")
    // AxiLite4 control port signals
    v should include("io_ctrl_aw_valid")
    v should include("io_ctrl_aw_ready")
    v should include("io_ctrl_w_valid")
    v should include("io_ctrl_w_ready")
    v should include("io_ctrl_b_valid")
    v should include("io_ctrl_ar_valid")
    v should include("io_ctrl_r_valid")
  }

  it should "include performance counter and write merge buffer submodules" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig()))
    v should include("module Llc")
    // Check for submodule instances
    v should include("LlcPerfCounters")
    v should include("LlcWriteMergeBuffer")
  }

  it should "elaborate with all Phase 3 features under a custom config" in {
    val v = ChiselStage.emitSystemVerilog(
      new Llc(LlcConfig(mshrEntries = 2, wbBufEntries = 2, wayPartitions = 2))
    )
    v should include("module Llc")
    // Should still have ctrl port and submodules
    v should include("io_ctrl_aw_valid")
    v should include("LlcPerfCounters")
    v should include("LlcWriteMergeBuffer")
  }

  it should "elaborate flush/invalidate FSM with control/status signals" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig()))
    v should include("module Llc")
    // Flush FSM control signals from pipeline
    v should include("_pipeline_io_flushBusy")
    v should include("_pipeline_io_flushDone")
    // Flush start pulse logic
    v should include("flushStartPulse")
    // Flush done sticky register
    v should include("flushDoneSticky")
    // Ctrl flush request handling
    v should include("ctrlFlushReq")
    // Ways of the current set still waiting for an action
    v should include("walkNeed")
  }

  it should "elaborate LlcPipeline with flush inputs and invalidate support" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig()))
    v should include("module LlcPipeline")
    // Flush FSM input signals
    v should include("io_flushStart")
    v should include("io_flushIsInvalidate")
    // Flush FSM output status signals
    v should include("io_flushBusy")
    v should include("io_flushDone")
    // WbBuffer status feedback for flush FSM
    v should include("io_wbBufEmpty")
    v should include("io_wbBufEnqReady")
  }

  it should "emit the data array SRAM with a byte mask on a single write port" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig(capacity = 8 * 1024)))
    val begin = v.indexOf("module dataMems")
    val mod = v.slice(begin, v.indexOf("endmodule", begin))
    mod should include("W0_mask")
    mod should include("if (W0_en & W0_mask[0])")
    val writePort = """input\s+(\[[^\]]+\]\s+)?W\d+_addr""".r
    writePort.findAllIn(mod).size shouldBe 1
  }

  it should "enable a data array read only on the selected way" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig(capacity = 8 * 1024)))
    val begin = v.indexOf("module LlcDataArray")
    val mod = v.slice(begin, v.indexOf("endmodule", begin))
    val bankEn = """\.R0_en\s*\(([^)]*)\)""".r
    val enables = bankEn.findAllIn(mod).toList
    enables.size shouldBe 8
    all (enables) should include("io_read_enable &")
    all (enables) should include("io_read_way")
  }

  it should "store PLRU state in a masked-write SRAM read in S0" in {
    val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig(capacity = 8 * 1024)))
    v should include("module plruState_16x7")
    val begin = v.indexOf("module plruState_16x7")
    val mod = v.slice(begin, v.indexOf("endmodule", begin))
    mod should include("W0_mask")
    val pipe = v.slice(v.indexOf("module LlcPipeline"), v.length)
    pipe should include regex """.io_query_enable\s*\(\s*s0Fire\s*\)"""
  }

  it should "elaborate with 4-way and 16-way configs" in {
    for ((ways, sets) <- Seq((4, "16x3"), (16, "16x15"))) {
      val v = ChiselStage.emitSystemVerilog(new Llc(LlcConfig(capacity = ways * 1024, ways = ways)))
      v should include("module Llc")
      v should include(s"module plruState_$sets")
    }
  }
}
