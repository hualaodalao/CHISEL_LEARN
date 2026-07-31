import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import AxiLite4Utils._

/** AXI4-Lite 直通 + 五通道打拍 */
class AxiLite4PipeDemo extends Module {
  val config = AxiLite4Config(addressWidth = 32, dataWidth = 32)
  val io = IO(new Bundle {
    val up = slave(AxiLite4(config))
    val down = master(AxiLite4(config))
  })
  io.down << io.up.pipelined(
    aw = StreamPipe.FULL, w = StreamPipe.M2S, b = StreamPipe.S2M,
    ar = StreamPipe.HALF, r = StreamPipe.M2S)
}

/** AXI4-Lite master 接入 AXI4 slave（toAxi 桥接） */
class AxiLite4ToAxiDemo extends Module {
  val config = AxiLite4Config(addressWidth = 32, dataWidth = 32)
  val io = IO(new Bundle {
    val up = slave(AxiLite4(config))
    val down = master(Axi4(Axi4Config(addressWidth = 32, dataWidth = 32,
      useId = false, useRegion = false, useBurst = false, useLock = false,
      useCache = false, useSize = false, useQos = false, useLen = false)))
  })
  io.down << io.up.toAxi()
}

/** AXI4 master（无 id/len/burst）接入 AXI4-Lite slave（toLite 桥接，含 size 剥除） */
class Axi4ToLiteDemo extends Module {
  val config = Axi4Config(addressWidth = 32, dataWidth = 32,
    useId = false, useLen = false, useBurst = false)
  val io = IO(new Bundle {
    val up = slave(Axi4(config))
    val down = master(AxiLite4(AxiLite4Config(addressWidth = 32, dataWidth = 32)))
  })
  io.down << io.up.toLite()
}

/** APB4 直通 + 命令方向打拍 */
class Apb4PipeDemo extends Module {
  val config = Apb4Config(addressWidth = 16, dataWidth = 32)
  val io = IO(new Bundle {
    val up = slave(Apb4(config))
    val down = master(Apb4(config))
  })
  io.down << io.up.m2sPipe()
}

/** APB4 地址译码：1 master → 2 slave（Apb4Hub 内部走 Apb4Decoder） */
class Apb4HubDemo extends Apb4Hub(
  Apb4Config(addressWidth = 16, dataWidth = 32),
  Seq(SizeMapping(0x0000, 0x1000), SizeMapping(0x1000, 0x1000)))

class AxiLite4Apb4Test extends AnyFlatSpec with Matchers {
  behavior of "AxiLite4 / Apb4 (SpinalHDL port)"

  it should "elaborate AxiLite4 passthrough with per-channel pipes" in {
    val v = ChiselStage.emitSystemVerilog(new AxiLite4PipeDemo)
    v should include("module AxiLite4PipeDemo")
    v should include("up_aw_valid")
    v should include("down_r_payload_data")
  }

  it should "elaborate AxiLite4 to Axi4 bridge" in {
    val v = ChiselStage.emitSystemVerilog(new AxiLite4ToAxiDemo)
    v should include("module AxiLite4ToAxiDemo")
    v should include("down_w_payload_last")
    v should include("down_ar_payload_prot")
  }

  it should "elaborate Axi4 to AxiLite4 bridge" in {
    val v = ChiselStage.emitSystemVerilog(new Axi4ToLiteDemo)
    v should include("module Axi4ToLiteDemo")
    v should include("down_aw_payload_prot")
    v should include("up_r_payload_last")
  }

  it should "elaborate Apb4 passthrough with m2sPipe" in {
    val v = ChiselStage.emitSystemVerilog(new Apb4PipeDemo)
    v should include("module Apb4PipeDemo")
    v should include("up_PADDR")
    v should include("down_PENABLE")
  }

  it should "elaborate Apb4Hub with two slaves" in {
    val v = ChiselStage.emitSystemVerilog(new Apb4HubDemo)
    v should include("module Apb4HubDemo")
    v should include("slvs_0_PSEL")
    v should include("slvs_1_PSEL")
  }

  it should "reject overlapping Apb4 address mappings at elaboration" in {
    an[Exception] should be thrownBy ChiselStage.emitSystemVerilog(new Apb4Hub(
      Apb4Config(addressWidth = 16, dataWidth = 32),
      Seq(SizeMapping(0x0000, 0x1000), SizeMapping(0x0800, 0x1000))))
  }
}
