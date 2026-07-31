import chisel3._
import chisel3.simulator.EphemeralSimulator._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SystolicArrayTest extends AnyFlatSpec with Matchers {

  behavior of "Hive Systolic Array"

  it should "elaborate PE" in {
    val v = ChiselStage.emitSystemVerilog(new PE)
    v should include("module PE")
    v should include("cReg")
  }

  it should "elaborate 8x8 SystolicArray" in {
    val v = ChiselStage.emitSystemVerilog(new SystolicArray(8))
    v should include("module SystolicArray")
    v should include("PE")
  }

  it should "elaborate 2x2 SystolicCluster (4x 8x8 arrays)" in {
    val v = ChiselStage.emitSystemVerilog(new SystolicCluster(8, 2))
    v should include("module SystolicCluster")
    v should include("SystolicArray")
  }

  it should "compute 2x2 INT8 matrix multiply correctly" in {
    val n = 2
    // A = [[1,2],[3,4]], B = [[5,6],[7,8]]
    // C = A*B = [[19,22],[43,50]]
    val a = Seq(Seq(1, 2), Seq(3, 4))
    val b = Seq(Seq(5, 6), Seq(7, 8))
    val expected = for (i <- 0 until n) yield
      for (j <- 0 until n) yield
        (for (k <- 0 until n) yield a(i)(k) * b(k)(j)).sum

    simulate(new SystolicArray(n, skew = true)) { dut =>
      dut.io.fmtIn.poke(DataFormat.INT8)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.io.clearIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.drainIn.poke(false.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U); dut.io.bIn(i).poke(0.U) }
      dut.clock.step()

      // Cycle 0: clear 脉冲
      dut.io.clearIn.poke(true.B)
      dut.clock.step()

      // Cycle 1..N: valid + 数据
      dut.io.clearIn.poke(false.B)
      dut.io.validIn.poke(true.B)
      for (k <- 0 until n) {
        for (i <- 0 until n) {
          dut.io.aIn(i).poke(a(i)(k).U(16.W))
          dut.io.bIn(i).poke(b(k)(i).U(16.W))
        }
        dut.clock.step()
      }

      // 停止输入，等待传播
      dut.io.validIn.poke(false.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U); dut.io.bIn(i).poke(0.U) }
      for (_ <- 0 until (3 * n)) { dut.clock.step() }

      // Drain：持续拉高，整列同时移位，底部先输出最后一行
      dut.io.drainIn.poke(true.B)
      val results = Array.fill(n, n)(0)
      for (i <- (n - 1) to 0 by -1) {
        // 当前底部 = row i
        for (j <- 0 until n) {
          results(i)(j) = dut.io.cOut(j).peek().litValue.toInt
        }
        dut.clock.step()
      }
      dut.io.drainIn.poke(false.B)

      // 验证：results(i)(j) 应等于 expected(i)(j)
      for (i <- 0 until n; j <- 0 until n) {
        results(i)(j) shouldBe expected(i)(j)
      }
    }
  }

  it should "compute 2x2 INT16 matrix multiply with negative values" in {
    val n = 2
    val a = Seq(Seq(-1, 2), Seq(3, -4))
    val b = Seq(Seq(5, -6), Seq(7, 8))
    val expected = for (i <- 0 until n) yield
      for (j <- 0 until n) yield
        (for (k <- 0 until n) yield a(i)(k) * b(k)(j)).sum

    simulate(new SystolicArray(n, skew = true)) { dut =>
      dut.io.fmtIn.poke(DataFormat.INT16)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.io.clearIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.drainIn.poke(false.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U); dut.io.bIn(i).poke(0.U) }
      dut.clock.step()

      dut.io.clearIn.poke(true.B)
      dut.clock.step()

      dut.io.clearIn.poke(false.B)
      dut.io.validIn.poke(true.B)
      for (k <- 0 until n) {
        for (i <- 0 until n) {
          dut.io.aIn(i).poke((a(i)(k) & 0xFFFF).U(16.W))
          dut.io.bIn(i).poke((b(k)(i) & 0xFFFF).U(16.W))
        }
        dut.clock.step()
      }

      dut.io.validIn.poke(false.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U); dut.io.bIn(i).poke(0.U) }
      for (_ <- 0 until (3 * n)) { dut.clock.step() }

      // Drain
      dut.io.drainIn.poke(true.B)
      val results = Array.fill(n, n)(0)
      for (i <- (n - 1) to 0 by -1) {
        for (j <- 0 until n) {
          val raw = dut.io.cOut(j).peek().litValue
          results(i)(j) = if (raw > 0x7FFFFFFFL) (raw - 0x100000000L).toInt else raw.toInt
        }
        dut.clock.step()
      }
      dut.io.drainIn.poke(false.B)

      for (i <- 0 until n; j <- 0 until n) {
        results(i)(j) shouldBe expected(i)(j)
      }
    }
  }
}
