import chisel3._
import circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CounterTest extends AnyFlatSpec with Matchers {
  behavior of "Counter"
/*
  it should "elaborate to SystemVerilog with a 4-bit register" in {
    val verilog = ChiselStage.emitSystemVerilog(new Counter(4))
    verilog should include("module Counter")
    verilog should include("reg [3:0] count")
    verilog should include("io_en")
    verilog should include("io_out")
  }
  */
}
