import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** AXI4 直通 + 五通道打拍 */
class Axi4PipeDemo extends Module {
  val config = Axi4Config(addressWidth = 32, dataWidth = 32, idWidth = 4)
  val io = IO(new Bundle {
    val up = slave(Axi4(config))
    val down = master(Axi4(config))
  })
  io.down << io.up.pipelined(
    aw = StreamPipe.FULL, w = StreamPipe.M2S, b = StreamPipe.S2M,
    ar = StreamPipe.HALF, r = StreamPipe.M2S)
}

/** AXI4 → 共享 ARW 通道（轮询仲裁）*/
class Axi4ToSharedDemo extends Module {
  val config = Axi4Config(addressWidth = 32, dataWidth = 32, idWidth = 2)
  val io = IO(new Bundle {
    val up = slave(Axi4(config))
    val down = master(Axi4Shared(config))
  })
  io.down << io.up.toShared()
}

/** 共享通道恢复为标准 AXI4 */
class SharedToAxi4Demo extends Module {
  val config = Axi4Config(addressWidth = 32, dataWidth = 32, idWidth = 2)
  val io = IO(new Bundle {
    val up = slave(Axi4Shared(config))
    val down = master(Axi4(config))
  })
  io.down << io.up.toAxi4()
}

/** AR 通道突发拆分为逐拍命令 */
class Axi4UnburstifyDemo extends Module {
  val config = Axi4Config(addressWidth = 32, dataWidth = 32, idWidth = 2)
  val io = IO(new Bundle {
    val ar = slave(Stream(Axi4Ar(config)))
    val beatAddr = master(Stream(UInt(32.W)))
    val beatLast = Output(Bool())
  })
  val beats = io.ar.unburstify
  io.beatLast := beats.payload.last
  io.beatAddr << beats.map(_.fragment.addr)
}

/** 读写分离 + validPipe */
class Axi4SplitDemo extends Module {
  val config = Axi4Config(addressWidth = 16, dataWidth = 32, idWidth = 2)
  val io = IO(new Bundle {
    val up = slave(Axi4(config))
    val ro = master(Axi4ReadOnly(config))
    val wo = master(Axi4WriteOnly(config))
  })
  io.ro << io.up.toReadOnly().arValidPipe()
  io.wo << io.up.toWriteOnly().awValidPipe()
}

/** 精简配置 master 接入全配置 slave（跨配置 drive + 默认值填充）。
  * 注：last 无默认值，useLast=false 无法驱动全配置（与 SpinalHDL 原版一致），
  * 故这里保留 useLast。 */
class Axi4LiteishToFullDemo extends Module {
  val lite = Axi4Config(addressWidth = 16, dataWidth = 32, useId = false,
    useRegion = false, useBurst = false, useLock = false, useCache = false,
    useQos = false, useLen = false, useProt = false)
  val io = IO(new Bundle {
    val up = slave(Axi4(lite))
    val down = master(Axi4(lite.toFullConfig()))
  })
  io.down << io.up.toFullConfig()
}

class Axi4Test extends AnyFlatSpec with Matchers {
  behavior of "Axi4 (SpinalHDL port)"

  it should "elaborate Axi4 passthrough with per-channel pipes" in {
    val v = ChiselStage.emitSystemVerilog(new Axi4PipeDemo)
    v should include("module Axi4PipeDemo")
    v should include("up_aw_valid")
    v should include("down_r_payload_data")
  }

  it should "elaborate Axi4 to Axi4Shared conversion" in {
    val v = ChiselStage.emitSystemVerilog(new Axi4ToSharedDemo)
    v should include("StreamArbiter")
    v should include("down_arw_payload_write")
  }

  it should "elaborate Axi4Shared back to Axi4" in {
    val v = ChiselStage.emitSystemVerilog(new SharedToAxi4Demo)
    v should include("module SharedToAxi4Demo")
    v should include("up_arw_payload_write")
  }

  it should "elaborate AR unburstify" in {
    val v = ChiselStage.emitSystemVerilog(new Axi4UnburstifyDemo)
    v should include("module Axi4UnburstifyDemo")
    v should include("beatLast")
  }

  it should "elaborate read/write split with validPipe" in {
    val v = ChiselStage.emitSystemVerilog(new Axi4SplitDemo)
    v should include("ro_ar_valid")
    v should include("wo_b_ready")
  }

  it should "elaborate reduced config driving full config" in {
    val v = ChiselStage.emitSystemVerilog(new Axi4LiteishToFullDemo)
    v should include("down_aw_payload_len")
    v should include("down_aw_payload_burst")
  }
}
