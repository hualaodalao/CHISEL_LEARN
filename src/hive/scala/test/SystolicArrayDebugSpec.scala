import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SystolicArrayDebugSpec extends AnyFlatSpec with Matchers {
  "Debug 2x2 with loadHIn (horizontal load)" should "show aligned timing" in {
    val n = 2
    simulate(new HiveCell(n, 16, 16, 0, Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8))) { dut =>
      val wVal = 5.U
      val fmt = DataFormat.INT8
      val aMask = (1L << 16) - 1

      // Init + Weight Load + Flush + Clear
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.fmtIn.poke(fmt)
      dut.io.rndIn.poke(RoundingMode.RNE)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // Weight load phase (horizontal)
      for (t <- 0 until 3) {
        dut.io.loadHIn.poke((t >= 0 && t < n).B)
        dut.io.loadVIn.poke(false.B)
        for (i <- 0 until n) { dut.io.aIn(i).poke(wVal) }
        dut.io.validIn.poke(false.B)
        for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
        dut.io.clear.poke(false.B); dut.clock.step()
      }

      // Flush
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.io.clear.poke(false.B); dut.clock.step()

      // Clear psumReg
      dut.io.clear.poke(true.B); dut.clock.step(); dut.io.clear.poke(false.B)

      val a = Seq.fill(n)(Seq(1L, 2L))
      val prevVO = Array.fill(n)(false)
      println("=== loadHIn horizontal load: pass with n=2 cycles ===")
      for (t <- 0 until 8) {
        dut.io.validIn.poke((t < n).B)
        dut.io.fmtIn.poke(fmt); dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
        for (i <- 0 until n) {
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
      println("=== loadHIn horizontal load: pass with n+1=3 cycles ===")
      for (t <- 0 until 10) {
        dut.io.validIn.poke((t <= n).B)
        dut.io.fmtIn.poke(fmt); dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
        for (i <- 0 until n) {
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
