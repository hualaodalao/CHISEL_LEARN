import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** CachelessBus → TileLink 桥的 demo 模块 */
class CachelessToTilelinkDemo extends Module {
  val p = CachelessBusParam(addressWidth = 32, dataWidth = 32, idCount = 4, cmdPersistence = true)
  val io = IO(new Bundle {
    val up = slave(CachelessBus(p))
    val down = master(TilelinkBus(p.toTilelinkM2s))
  })

  val bridge = new CachelessBusToTilelink(io.up)
  io.down.a << bridge.down.a
  bridge.down.d << io.down.d
}

class FetchCachelessBusTest extends AnyFlatSpec with Matchers {
  behavior of "FetchCachelessBus (NaxRiscv port)"

  it should "elaborate CachelessBus to TileLink bridge" in {
    val v = ChiselStage.emitSystemVerilog(new CachelessToTilelinkDemo)
    v should include("module CachelessToTilelinkDemo")
    v should include("up_cmd_valid")
    v should include("down_a_valid")
  }
}
