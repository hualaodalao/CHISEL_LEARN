import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** TileLink get/put 总线直通 + 各通道打拍 */
class TilelinkPipeDemo extends Module {
  val bp = BusParameter.simple(addressWidth = 32, dataWidth = 64, sizeBytes = 64, sourceWidth = 4)
  val io = IO(new Bundle {
    val up = slave(Bus(bp))
    val down = master(Bus(bp))
  })
  io.down.connectFrom(io.up)(a = StreamPipe.FULL, d = StreamPipe.M2S)
}

/** 带 BCE（acquire/probe）的五通道总线直通 */
class TilelinkCoherentDemo extends Module {
  val node = NodeParameters(
    m = M2sParameters(
      addressWidth = 32,
      dataWidth = 64,
      masters = List(M2sAgent("cpu", M2sSource(
        id = SizeMapping(0, 4),
        emits = M2sTransfers(
          acquireT = SizeRange.upTo(64),
          acquireB = SizeRange.upTo(64),
          get = SizeRange.upTo(64),
          putFull = SizeRange.upTo(64),
          putPartial = SizeRange.upTo(64)))))),
    s = S2mParameters(List(S2mAgent(
      name = "l2",
      sinkId = SizeMapping(0, 4),
      emits = S2mTransfers(probe = SizeRange.upTo(64)))))
  )
  val io = IO(new Bundle {
    val up = slave(Bus(node))
    val down = master(Bus(node))
  })
  io.down << io.up
}

/** source 偏移拓宽（Arbiter 上游侧的典型用法） */
class TilelinkSourceOffsetDemo extends Module {
  val bp = BusParameter.simple(addressWidth = 32, dataWidth = 32, sizeBytes = 32, sourceWidth = 2)
  val io = IO(new Bundle {
    val up = slave(Bus(bp))
    val down = master(Bus(bp.copy(sourceWidth = 4)))
  })
  io.down << io.up.withSourceOffset(0x4, 4)
}

/** 单拍 RAM（beatMax == 1，无 FSM 路径） */
class TilelinkRamSingleBeatDemo extends Ram(
  NodeParameters(M2sParameters(
    addressWidth = 16,
    dataWidth = 32,
    masters = List(M2sAgent("m", M2sSource(SizeMapping(0, 2), M2sTransfers.allGetPut(SizeRange.upTo(4))))))),
  bytes = 256)

/** 突发 RAM（beatMax == 16，FSM 路径） */
class TilelinkRamBurstDemo extends Ram(
  NodeParameters(M2sParameters(
    addressWidth = 16,
    dataWidth = 32,
    masters = List(M2sAgent("m", M2sSource(SizeMapping(0, 4), M2sTransfers.allGetPut(SizeRange.upTo(64))))))),
  bytes = 1024)

class TilelinkTest extends AnyFlatSpec with Matchers {
  behavior of "Tilelink (SpinalHDL port)"

  it should "elaborate get/put passthrough with per-channel pipes" in {
    val v = ChiselStage.emitSystemVerilog(new TilelinkPipeDemo)
    v should include("module TilelinkPipeDemo")
    v should include("up_a_valid")
    v should include("down_a_payload_address")
    v should include("up_d_payload_data")
  }

  it should "elaborate coherent passthrough with b/c/e channels" in {
    val v = ChiselStage.emitSystemVerilog(new TilelinkCoherentDemo)
    v should include("module TilelinkCoherentDemo")
    v should include("up_b_valid")
    v should include("down_c_payload_address")
    v should include("up_e_payload_sink")
  }

  it should "elaborate withSourceOffset widening" in {
    val v = ChiselStage.emitSystemVerilog(new TilelinkSourceOffsetDemo)
    v should include("module TilelinkSourceOffsetDemo")
    v should include("down_a_payload_source")
  }

  it should "elaborate ErrorSlave" in {
    val v = ChiselStage.emitSystemVerilog(
      new ErrorSlave(BusParameter.simple(addressWidth = 32, dataWidth = 32, sizeBytes = 64, sourceWidth = 2)))
    v should include("module ErrorSlave")
    v should include("bus_d_payload_denied")
  }

  it should "elaborate single-beat Ram" in {
    val v = ChiselStage.emitSystemVerilog(new TilelinkRamSingleBeatDemo)
    v should include("module TilelinkRamSingleBeatDemo")
    v should include("up_d_payload_data")
  }

  it should "elaborate burst Ram with beat FSM" in {
    val v = ChiselStage.emitSystemVerilog(new TilelinkRamBurstDemo)
    v should include("module TilelinkRamBurstDemo")
    v should include("up_a_payload_mask")
  }

  it should "merge masters with source offsets" in {
    def mst(sourceCount: Int) = M2sParameters(
      addressWidth = 32,
      dataWidth = 32,
      masters = List(M2sAgent("m", M2sSource(SizeMapping(0, sourceCount), M2sTransfers.allGetPut))))
    val merged = NodeParameters.mergeMasters(Seq(mst(4), mst(2)))
    merged.sourceWidth shouldBe 3 // 2 位本地 id + 1 位节点偏移
    merged.masters(1).mapping.head.id.base shouldBe BigInt(4)
    merged.emits.get.some shouldBe true
  }

  it should "compute M2sTransfers algebra" in {
    val a = M2sTransfers(get = SizeRange(1, 64), putFull = SizeRange(1, 16))
    val b = M2sTransfers(get = SizeRange(4, 256), acquireT = SizeRange(64))
    val inter = a.intersect(b)
    inter.get shouldBe SizeRange(4, 64)
    inter.putFull.none shouldBe true
    val cover = a.mincover(b)
    cover.get shouldBe SizeRange(1, 256)
    cover.acquireT shouldBe SizeRange(64)
    cover.withBCE shouldBe true
    a.withBCE shouldBe false
    a.sizeBytes shouldBe 64
  }

  it should "derive BusParameter fields" in {
    val bp = BusParameter.simple(addressWidth = 32, dataWidth = 64, sizeBytes = 64, sourceWidth = 4)
    bp.dataBytes shouldBe 8
    bp.sizeMax shouldBe 6
    bp.sizeWidth shouldBe 3
    bp.beatMax shouldBe 8
    bp.beatWidth shouldBe 3
    bp.dataBytesLog2Up shouldBe 3
  }
}
