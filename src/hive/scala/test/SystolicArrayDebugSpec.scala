import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SystolicArrayDebugSpec extends AnyFlatSpec with Matchers {
  "Debug 2x2 with loadVIn (vertical weight load)" should "show aligned timing" in {
    val n = 2
    simulate(new HiveCell(n, 16, 16, 0, Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8))) { dut =>
      val wVal = 5.U
      val fmt = DataFormat.INT8
      val aMask = (1L << 16) - 1

      // Init + Weight Load + Drain + Clear
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.fmtIn.poke(fmt)
      dut.io.rndIn.poke(RoundingMode.RNE)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // Weight load phase (vertical: loadH 配置 + loadV 权重，psumIn 供权重)
      for (_ <- 0 until (2 * n)) {
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        for (i <- 0 until n) { dut.io.aIn(i).poke(0.U) }
        dut.io.validIn.poke(false.B)
        for (j <- 0 until n) { dut.io.psumIn(j).poke(wVal) }
        dut.io.clear.poke(false.B); dut.clock.step()
      }

      // Drain loadV（保持 psumIn=权重直到排空）
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(wVal) }
      for (_ <- 0 until (n + 2)) dut.clock.step()

      // Clear psumReg
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.clock.step()
      dut.io.clear.poke(true.B); dut.clock.step(); dut.io.clear.poke(false.B)

      val a = Seq.fill(n)(Seq(1L, 2L))
      val prevVO = Array.fill(n)(false)
      println("=== loadVIn vertical load: pass with n=2 cycles ===")
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
      println("=== loadVIn vertical load: pass with n+1=3 cycles ===")
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
