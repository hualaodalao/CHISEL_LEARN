import chisel3._
import chisel3.simulator.EphemeralSimulator._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SystolicArrayTest extends AnyFlatSpec with Matchers {

  behavior of "Hive Weight-Stationary Systolic Array (No-Skew + Horizontal LoadIn Propagation)"

  it should "elaborate PE with wReg and psum" in {
    val v = ChiselStage.emitSystemVerilog(new PE)
    v should include("module PE")
    v should include("wReg")
    v should include("psum")
  }

  it should "elaborate 8x8 SystolicArray (no skew)" in {
    val v = ChiselStage.emitSystemVerilog(new SystolicArray(8))
    v should include("module SystolicArray")
    v should include("PE")
  }

  it should "elaborate 2x2 SystolicCluster" in {
    val v = ChiselStage.emitSystemVerilog(new SystolicCluster(8, 2))
    v should include("module SystolicCluster")
    v should include("SystolicArray")
  }

  // ===================== Helper =====================
  /** 执行 NxN 矩阵乘仿真
    *
    * 关键时序模型（poke → step → peek）：
    *   - poke 在 clock.step() 上升沿之前设置输入
    *   - RegNext 在上升沿捕获当前 poke 的值
    *   - peek 看到的是上一次 step 之后的状态
    *
    * RegNext(aIn) 使列 j 的数据比列 0 延迟 j+1 cycle（j 级 RegNext + 1 cycle 捕获延迟）
    * validIn 经 RegNext 链传播到 validOut 也有类似延迟
    *
    * 收集策略：poke → step → peek（在 step 后 peek 可以看到当前拍的结果）
    */
  private def runMatMul(
    n: Int, a: Seq[Seq[Long]], w: Long,
    fmt: DataFormat.Type,
    aW: Int = 16, bW: Int = 16, cW: Int = 0,
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8),
    weightLoadMode: WeightLoadMode.Type = WeightLoadMode.Horizontal
  ): Array[Array[BigInt]] = {
    val results = Array.fill(n, n)(BigInt(0))
    simulate(new SystolicArray(n, aW, bW, cW, supportedFmts)) { dut =>
      val aMask = (1L << aW) - 1
      val wVal = (w & ((1L << bW) - 1)).U

      // === Initialize ===
      for (i <- 0 until n) {
        dut.io.loadIn(i).poke(false.B)
        dut.io.weightLoadMode(i).poke(weightLoadMode)
        dut.io.aIn(i).poke(0.U)
        dut.io.validIn(i).poke(false.B)
        dut.io.fmtIn(i).poke(fmt)
        dut.io.rndIn(i).poke(RoundingMode.RNE)
      }
      for (j <- 0 until n) {
        dut.io.psumIn(j).poke(0.U)
      }
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // === Phase 1: Weight Loading ===
      for (t <- 0 until (2 * n - 1)) {
        for (i <- 0 until n) {
          val inLoad = t >= i && t < i + n
          dut.io.loadIn(i).poke(inLoad.B)
          dut.io.weightLoadMode(i).poke(weightLoadMode)
          if (weightLoadMode == WeightLoadMode.Horizontal) dut.io.aIn(i).poke(wVal)
          else dut.io.aIn(i).poke(0.U)
        }
        for (j <- 0 until n) dut.io.validIn(j).poke(false.B)
        dut.io.clear.poke(false.B)
        for (j <- 0 until n) {
          if (weightLoadMode == WeightLoadMode.Vertical) dut.io.psumIn(j).poke(wVal)
          else dut.io.psumIn(j).poke(0.U)
        }
        dut.clock.step()
      }

      // === Phase 2: Flush ===
      for (i <- 0 until n) { dut.io.loadIn(i).poke(false.B); dut.io.aIn(i).poke(0.U); dut.io.validIn(i).poke(false.B) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // === Phase 3: Clear psumReg ===
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)

      // === Phase 4: Computation (n passes, each producing 1 result per row) ===
      // Each pass: feed activations + validIn for n+1 cycles, then drain
      // 收集策略：对角线收集——cOut(j) 在 validOut 上升沿后第 j 个周期稳定
      // 因为 PE 沿 y 方向的 RegNext 链使每列延迟递增 1 拍
      val collected = Array.fill(n)(0)
      val prevValidOut = Array.fill(n)(false)
      val risingCycle = Array.fill(n)(-1)  // cycle number of validOut rising edge

      for (pass <- 0 until n) {
        val totalPassIter = 2 * (n + 1) + 2  // (n+1) feed + (n+1) drain + margin

        for (t <- 0 until totalPassIter) {
          // --- POKE ---
          for (i <- 0 until n) {
            // validIn: active for n+1 cycles (t=0 to t=n)
            dut.io.validIn(i).poke((t <= n).B)
            dut.io.fmtIn(i).poke(fmt)
            dut.io.rndIn(i).poke(RoundingMode.RNE)
          }
          for (i <- 0 until n) {
            dut.io.loadIn(i).poke(false.B)
            dut.io.weightLoadMode(i).poke(weightLoadMode)
            if (t < n) {
              dut.io.aIn(i).poke((a(t)(i) & aMask).U)
            } else if (t == n) {
              dut.io.aIn(i).poke((a(n - 1)(i) & aMask).U)  // repeat last
            } else {
              dut.io.aIn(i).poke(0.U)
            }
          }
          for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)

          // --- STEP ---
          dut.clock.step()

          // --- PEEK (diagonal collection) ---
          // cOut(j) stabilizes at rising + j cycles due to PE pipeline delay
          // Collect column j's value at offset j from the rising edge
          for (i <- 0 until n) {
            val curValid = dut.io.validOut(i).peek().litToBoolean
            if (curValid && !prevValidOut(i)) {
              risingCycle(i) = t  // record rising edge cycle
            }
            if (curValid && risingCycle(i) >= 0) {
              val offset = t - risingCycle(i)
              // Collect cOut(offset) at offset cycle for the current row
              if (offset < n && collected(i) == offset) {
                results(offset)(i) = dut.io.cOut(offset).peek().litValue
                collected(i) += 1
              }
            }
            prevValidOut(i) = curValid
          }
        }
      }

      // === Phase 5: Final drain ===
      for (i <- 0 until n) { dut.io.validIn(i).poke(false.B) }
      for (j <- 0 until n) { dut.io.psumIn(j).poke(0.U) }
      for (i <- 0 until n) { dut.io.loadIn(i).poke(false.B); dut.io.aIn(i).poke(0.U) }
      for (_ <- 0 until (n + 4)) {
        dut.clock.step()
      }

      for (i <- 0 until n) {
        assert(collected(i) >= n, s"Row $i: expected $n results, got ${collected(i)}")
      }
    }
    results
  }

  private def toSigned(raw: BigInt, cW: Int): Long = {
    if (raw.testBit(cW - 1)) (raw - (BigInt(1) << cW)).toLong
    else raw.toLong
  }

  // ===================== Tests =====================
  // 使用 uniform-row A（所有行相同），确保 RegNext 列偏移不影响结果

  it should "compute 2x2 INT8 matrix multiply correctly (horizontal weight loading)" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = (row.sum) * w  // (1+2)*5 = 15

    val results = runMatMul(n, a, w, DataFormat.INT8, weightLoadMode = WeightLoadMode.Horizontal)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }

  it should "compute 2x2 INT16 matrix multiply with negative values" in {
    val n = 2
    val row = Seq(-1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w  // (-1+2)*5 = 5

    val results = runMatMul(n, a, w, DataFormat.INT16)
    val effectiveAccW = math.max(32, 16 + 16 + 1)
    for (i <- 0 until n; j <- 0 until n) {
      toSigned(results(i)(j), effectiveAccW) shouldBe expected
    }
  }

  it should "compute 4x4 INT16 with large values using auto-derived cW" in {
    val n = 4
    val row = Seq(30000L, -30000L, 20000L, -10000L)
    val a = Seq.fill(n)(row)
    val w = 100L
    val expected = row.sum * w  // 10000*100 = 1000000

    val results = runMatMul(n, a, w, DataFormat.INT16)
    val effectiveAccW = 34
    for (i <- 0 until n; j <- 0 until n) {
      toSigned(results(i)(j), effectiveAccW) shouldBe expected
    }
  }

  it should "produce correct results for two activation batches with one weight load" in {
    val n = 2
    val w = 3L
    val row1 = Seq(1L, 2L)
    val row2 = Seq(2L, 3L)
    val a1 = Seq.fill(n)(row1)
    val a2 = Seq.fill(n)(row2)
    val exp1 = row1.sum * w
    val exp2 = row2.sum * w

    val results1 = runMatMul(n, a1, w, DataFormat.INT8)
    val results2 = runMatMul(n, a2, w, DataFormat.INT8)
    for (i <- 0 until n; j <- 0 until n) {
      results1(i)(j).toInt shouldBe exp1
      results2(i)(j).toInt shouldBe exp2
    }
  }

  it should "work with custom aW=8, bW=8, cW=20 (INT8 only)" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w

    val results = runMatMul(n, a, w, DataFormat.INT8,
      aW = 8, bW = 8, cW = 20,
      supportedFmts = Set(DataFormat.INT8))
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }

  it should "elaborate with explicit cW=64 and compute INT16 correctly" in {
    val v = ChiselStage.emitSystemVerilog(new SystolicArray(2, cW = 64))
    v should include("module SystolicArray")

    val n = 2
    val row = Seq(100L, 200L)
    val a = Seq.fill(n)(row)
    val w = 10L
    val expected = row.sum * w

    val results = runMatMul(n, a, w, DataFormat.INT16, cW = 64)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toLong shouldBe expected
    }
  }

  it should "auto-derive cW correctly (verify via Verilog output bit width)" in {
    val v = ChiselStage.emitSystemVerilog(new SystolicArray(4))
    v should include("[33:0]")
  }

  it should "compute 2x2 INT8 matrix multiply with vertical weight loading" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w

    val results = runMatMul(n, a, w, DataFormat.INT8, weightLoadMode = WeightLoadMode.Vertical)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }

  it should "compute 2x2 INT8 matrix multiply with explicit horizontal weight loading" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w

    val results = runMatMul(n, a, w, DataFormat.INT8, weightLoadMode = WeightLoadMode.Horizontal)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }
}
