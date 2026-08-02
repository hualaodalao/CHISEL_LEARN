import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SystolicArrayDebugSpec extends AnyFlatSpec with Matchers {
  "Debug 2x2 with RegNext validIn" should "show aligned timing" in {
    val n = 2
    simulate(new SystolicArray(n, 16, 16, 0, Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8))) { dut =>
      val wVal = 5.U
      val fmt = DataFormat.INT8
      val aMask = (1L << 16) - 1

      // Init + Weight Load + Flush + Clear (same as before)
      for (i <- 0 until n) { dut.io.loadIn(i).poke(false.B); dut.io.weightLoadMode(i).poke(WeightLoadMode.Horizontal); dut.io.aIn(i).poke(0.U) }
      for (i <- 0 until n) { dut.io.validIn(i).poke(false.B); dut.io.fmtIn(i).poke(fmt); dut.io.rndIn(i).poke(RoundingMode.RNE) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.io.clear.poke(false.B); dut.clock.step()
      for (t <- 0 until 3) {
        for (i <- 0 until n) { dut.io.loadIn(i).poke((t >= i && t < i + n).B); dut.io.weightLoadMode(i).poke(WeightLoadMode.Horizontal); dut.io.aIn(i).poke(wVal) }
        for (i <- 0 until n) { dut.io.validIn(i).poke(false.B) }
        for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
        dut.io.clear.poke(false.B); dut.clock.step()
      }
      for (i <- 0 until n) { dut.io.loadIn(i).poke(false.B); dut.io.aIn(i).poke(0.U); dut.io.validIn(i).poke(false.B) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.io.clear.poke(false.B); dut.clock.step()
      dut.io.clear.poke(true.B); dut.clock.step(); dut.io.clear.poke(false.B)

      val a = Seq.fill(n)(Seq(1L, 2L))
      val prevVO = Array.fill(n)(false)
      println("=== RegNext validIn: pass with n=2 cycles ===")
      for (t <- 0 until 8) {
        for (i <- 0 until n) {
          dut.io.validIn(i).poke((t < n).B)
          dut.io.fmtIn(i).poke(fmt); dut.io.rndIn(i).poke(RoundingMode.RNE)
        }
        for (i <- 0 until n) {
          dut.io.loadIn(i).poke(false.B); dut.io.weightLoadMode(i).poke(WeightLoadMode.Horizontal)
          if (t < n) dut.io.aIn(i).poke((a(t)(i) & aMask).U)
          else dut.io.aIn(i).poke(0.U)
        }
        for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
        dut.clock.step()
        val vo = (0 until n).map(i => dut.io.validOut(i).peek().litToBoolean)
        val co = (0 until n).map(i => dut.io.cOut(i).peek().litValue)
        val rising = (0 until n).map(i => vo(i) && !prevVO(i))
        for (i <- 0 until n) prevVO(i) = vo(i)
        println(f"  t=$t: vld=${t<n} | vOut=(${vo.mkString(",")}) cOut=(${co.mkString(",")}) rising=(${rising.mkString(",")})")
      }

      // Also test with n+1=3 validIn cycles
      dut.io.clear.poke(true.B); dut.clock.step(); dut.io.clear.poke(false.B)
      for (i <- 0 until n) prevVO(i) = false
      println("=== RegNext validIn: pass with n+1=3 cycles ===")
      for (t <- 0 until 10) {
        for (i <- 0 until n) {
          dut.io.validIn(i).poke((t <= n).B)  // t=0,1,2 (n+1=3 cycles)
          dut.io.fmtIn(i).poke(fmt); dut.io.rndIn(i).poke(RoundingMode.RNE)
        }
        for (i <- 0 until n) {
          dut.io.loadIn(i).poke(false.B); dut.io.weightLoadMode(i).poke(WeightLoadMode.Horizontal)
          if (t < n) dut.io.aIn(i).poke((a(t)(i) & aMask).U)
          else if (t == n) dut.io.aIn(i).poke((a(n-1)(i) & aMask).U)
          else dut.io.aIn(i).poke(0.U)
        }
        for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
        dut.clock.step()
        val vo = (0 until n).map(i => dut.io.validOut(i).peek().litToBoolean)
        val co = (0 until n).map(i => dut.io.cOut(i).peek().litValue)
        val rising = (0 until n).map(i => vo(i) && !prevVO(i))
        for (i <- 0 until n) prevVO(i) = vo(i)
        println(f"  t=$t: vld=${t<=n} | vOut=(${vo.mkString(",")}) cOut=(${co.mkString(",")}) rising=(${rising.mkString(",")})")
      }
    }
  }
}
