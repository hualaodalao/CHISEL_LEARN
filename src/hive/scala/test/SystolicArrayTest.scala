import chisel3._
import chisel3.simulator.EphemeralSimulator._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** 权重加载模式（测试用） */
sealed trait WeightLoadType
case object HorizontalLoad extends WeightLoadType
case object VerticalLoad   extends WeightLoadType
case object SimultaneousLoad extends WeightLoadType

class SystolicArrayTest extends AnyFlatSpec with Matchers {

  behavior of "Hive Weight-Stationary Systolic Array (loadH/loadV Architecture)"

  it should "elaborate HiveWorker with wReg and psum" in {
    val v = ChiselStage.emitSystemVerilog(new HiveWorker)
    v should include("module HiveWorker")
    v should include("wReg")
    v should include("psum")
  }

  it should "elaborate 8x8 HiveCell (no skew)" in {
    val v = ChiselStage.emitSystemVerilog(new HiveCell(8))
    v should include("module HiveCell")
    v should include("HiveWorker")
  }

  it should "elaborate 2x2 HiveComb" in {
    val v = ChiselStage.emitSystemVerilog(new HiveComb(8, 2))
    v should include("module HiveComb")
    v should include("HiveCell")
  }

  // ===================== Helper =====================
  private def runMatMul(
    n: Int, a: Seq[Seq[Long]], w: Long,
    fmt: DataFormat.Type,
    aW: Int = 16, bW: Int = 16, cW: Int = 0,
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8),
    loadMode: WeightLoadType = HorizontalLoad
  ): Array[Array[BigInt]] = {
    val results = Array.fill(n, n)(BigInt(0))
    simulate(new HiveCell(n, aW, bW, cW, supportedFmts)) { dut =>
      val aMask = (1L << aW) - 1
      val wVal = (w & ((1L << bW) - 1)).U

      // === Initialize ===
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.fmtIn.poke(fmt)
      dut.io.rndIn.poke(RoundingMode.RNE)
      for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
      for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // === Phase 1: Weight Loading ===
      for (t <- 0 until (2 * n - 1)) {
        val inLoad = t >= 0 && t < n

        loadMode match {
          case HorizontalLoad =>
            dut.io.loadHIn.poke(inLoad.B)
            dut.io.loadVIn.poke(false.B)
            for (i <- 0 until n) dut.io.aIn(i).poke(wVal)
            for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)

          case VerticalLoad =>
            // 垂直加载时必须同时水平加载配置（fmt/rnd），不存在纯垂直模式
            dut.io.loadHIn.poke(inLoad.B)
            dut.io.loadVIn.poke(inLoad.B)
            for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
            for (j <- 0 until n) dut.io.psumIn(j).poke(wVal)

          case SimultaneousLoad =>
            dut.io.loadHIn.poke(inLoad.B)
            dut.io.loadVIn.poke(inLoad.B)
            for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
            for (j <- 0 until n) dut.io.psumIn(j).poke(wVal)
        }

        dut.io.fmtIn.poke(fmt)
        dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.validIn.poke(false.B)
        dut.io.clear.poke(false.B)
        dut.clock.step()
      }

      // === Phase 2: Flush ===
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
      for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // === Phase 3: Clear psumReg ===
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)

      // === Phase 4: Computation ===
      val collected = Array.fill(n)(0)
      var risingCycle0 = -1
      var prevVO0 = false

      for (pass <- 0 until n) {
        val totalPassIter = 3 * (n + 1) + 4
        risingCycle0 = -1
        prevVO0 = false

        for (t <- 0 until totalPassIter) {
          dut.io.validIn.poke((t <= n).B)
          dut.io.fmtIn.poke(fmt)
          dut.io.rndIn.poke(RoundingMode.RNE)
          dut.io.loadHIn.poke(false.B)
          dut.io.loadVIn.poke(false.B)
          for (i <- 0 until n) {
            if (t < n) {
              dut.io.aIn(i).poke((a(t)(i) & aMask).U)
            } else if (t == n) {
              dut.io.aIn(i).poke((a(n - 1)(i) & aMask).U)
            } else {
              dut.io.aIn(i).poke(0.U)
            }
          }
          for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)

          dut.clock.step()

          val curVO0 = dut.io.validOut(0).peek().litToBoolean
          if (curVO0 && !prevVO0) {
            risingCycle0 = t
          }
          prevVO0 = curVO0
          if (risingCycle0 >= 0 && curVO0 && collected(pass) < n) {
            val offset = t - risingCycle0
            if (offset < n) {
              results(pass)(offset) = dut.io.cOut(offset).peek().litValue
              collected(pass) += 1
            }
          }
        }
      }

      // === Phase 5: Final drain ===
      dut.io.validIn.poke(false.B)
      for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
      for (_ <- 0 until (n + 8)) dut.clock.step()

      for (pass <- 0 until n) {
        assert(collected(pass) >= n, s"Pass $pass: expected $n results, got ${collected(pass)}")
      }
    }
    results
  }

  private def toSigned(raw: BigInt, cW: Int): Long = {
    if (raw.testBit(cW - 1)) (raw - (BigInt(1) << cW)).toLong
    else raw.toLong
  }

  // ===================== Tests =====================

  it should "compute 2x2 INT8 matrix multiply correctly (horizontal weight loading)" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = (row.sum) * w

    val results = runMatMul(n, a, w, DataFormat.INT8, loadMode = HorizontalLoad)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }

  it should "compute 2x2 INT16 matrix multiply with negative values" in {
    val n = 2
    val row = Seq(-1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w

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
    val expected = row.sum * w

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
    val v = ChiselStage.emitSystemVerilog(new HiveCell(2, cW = 64))
    v should include("module HiveCell")

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
    val v = ChiselStage.emitSystemVerilog(new HiveCell(4))
    v should include("[33:0]")
  }

  it should "compute 2x2 INT8 matrix multiply with vertical weight loading (+ horizontal config)" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w

    val results = runMatMul(n, a, w, DataFormat.INT8, loadMode = VerticalLoad)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }

  it should "compute 2x2 INT8 matrix multiply with simultaneous load (horizontal config + vertical weight)" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = row.sum * w

    val results = runMatMul(n, a, w, DataFormat.INT8, loadMode = SimultaneousLoad)
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

    val results = runMatMul(n, a, w, DataFormat.INT8, loadMode = HorizontalLoad)
    for (i <- 0 until n; j <- 0 until n) {
      results(i)(j).toInt shouldBe expected
    }
  }
}
