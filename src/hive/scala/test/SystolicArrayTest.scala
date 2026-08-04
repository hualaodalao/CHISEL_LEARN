import chisel3._
import chisel3.simulator.EphemeralSimulator._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
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

      // === Phase 1: Weight Loading（垂直加载：loadH 配置 + loadV 权重，psumIn 供权重）===
      // 脉冲覆盖全链传播，权重沿 psum 链下沉；aIn 无需供权重
      for (_ <- 0 until (2 * n)) {
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
        for (j <- 0 until n) dut.io.psumIn(j).poke(wVal)
        dut.io.fmtIn.poke(fmt)
        dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.validIn.poke(false.B)
        dut.io.clear.poke(false.B)
        dut.clock.step()
      }

      // === Phase 2: Drain loadV（保持 psumIn=权重直到 loadV 沿 x 链排空）===
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
      for (j <- 0 until n) dut.io.psumIn(j).poke(wVal)
      for (_ <- 0 until (n + 2)) dut.clock.step()

      // === Phase 3: Clear psumReg（loadV 已排空，wReg 冻结，清零 psum 链）===
      for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
      dut.clock.step()
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

  it should "compute 2x2 INT8 matrix multiply correctly (vertical weight loading)" in {
    val n = 2
    val row = Seq(1L, 2L)
    val a = Seq.fill(n)(row)
    val w = 5L
    val expected = (row.sum) * w

    val results = runMatMul(n, a, w, DataFormat.INT8)
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

  // ===================== HiveComb 级测试（轨迹等价验证） =====================
  // HiveComb 的 psum 沿 ci（cluster 行）方向级联，输出为逐行 running sum。
  // 采用逐拍 bit-exact 轨迹对比；golden 为级联架构（loadH/fmt/rnd 沿 cj、
  // loadV 沿 ci 传播，valid 随 aOut 数据链级联）下的验证基准。

  // 外部 feeder：HiveComb 不再内部 skew aIn，测试承担「行 i 延迟 i 拍」错峰供数。
  // 时刻 t 在 aIn(i) 上给出逻辑第 (t - i) 拍的激活，t < i 时给 0。
  private def feedSkewed(
    dut: HiveComb, n: Int, t: Int
  )(src: (Int, Int) => Long): Unit = {
    for (i <- 0 until n) {
      val tt = t - i
      dut.io.aIn(i).poke((if (tt >= 0) src(tt, i) else 0L).U)
    }
  }

  // 权重加载 + 排空 + clear 协议（垂直加载：loadH 配置 + loadV 权重）
  private def loadAndPrepare(
    dut: HiveComb,
    fmt: DataFormat.Type,
    w: Long,
    n: Int,
    arrayN: Int
  ): Unit = {
    val wVal = w.U

    // === 初始化 ===
    dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
    dut.io.validIn.poke(false.B)
    dut.io.fmtIn.poke(fmt); dut.io.rndIn.poke(RoundingMode.RNE)
    dut.io.clear.poke(false.B)
    for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
    for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
    dut.clock.step()

    // === 权重加载：loadH（配置）+ loadV（权重），psumIn 供权重沿链下沉。
    //     脉冲覆盖全链传播；aIn 无需供权重（loadH 不再从 aIn 锁存 wReg）===
    val hold = 3 * n + arrayN
    for (_ <- 0 until hold) {
      dut.io.loadHIn.poke(true.B); dut.io.loadVIn.poke(true.B)
      for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
      for (j <- 0 until n) dut.io.psumIn(j).poke(wVal)
      dut.io.fmtIn.poke(fmt); dut.io.rndIn.poke(RoundingMode.RNE)
      dut.clock.step()
    }

    // === Drain loadV：释放使能后保持 psumIn=权重直到 loadV 沿 ci/x 链排空，
    //     否则尾部仍高的 loadV 会把 0 锁进深层 wReg ===
    dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
    dut.io.validIn.poke(false.B)
    for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
    for (j <- 0 until n) dut.io.psumIn(j).poke(wVal)
    for (_ <- 0 until (n + 2)) dut.clock.step()

    // === Clear：loadV 已排空、wReg 冻结，清零 psum 链准备累加 ===
    for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
    dut.clock.step()
    dut.io.clear.poke(true.B); dut.clock.step(); dut.io.clear.poke(false.B)
  }

  private def runCombTrace(
    fmt: DataFormat.Type,
    w: Long,
    passes: Int = 1,
    arrayN: Int = 4,
    clusterM: Int = 2
  ): Seq[String] = {
    val n = arrayN * clusterM
    val trace = scala.collection.mutable.ArrayBuffer[(Boolean, String)]()
    simulate(new HiveComb(arrayN, clusterM, 16, 16, 0)) { dut =>
      loadAndPrepare(dut, fmt, w, n, arrayN)

      // === 计算 pass（权重驻留，多 pass 复用）===
      // aIn 由 feedSkewed 外部错峰；totalIter 需覆盖最后一行的 skew 尾部
      val totalIter = 4 * n + 16
      for (pass <- 0 until passes) {
        for (t <- 0 until totalIter) {
          dut.io.validIn.poke((t <= n).B)
          dut.io.fmtIn.poke(fmt); dut.io.rndIn.poke(RoundingMode.RNE)
          dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
          feedSkewed(dut, n, t) { (tt, i) =>
            if (tt < n) (tt * 3 + i * (pass + 1) + 1) % 64
            else if (tt == n) ((n - 1) * 3 + i * (pass + 1) + 1) % 64
            else 0
          }
          for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)

          dut.clock.step()

          val vo = (0 until n).map(i => dut.io.validOut(i).peek().litToBoolean)
          val co = (0 until n).map(i => dut.io.cOut(i).peek().litValue)
          val voBits = vo.map(b => if (b) "1" else "0").mkString
          trace += ((vo.contains(true), f"p$pass t=$t%2d $voBits ${co.mkString(",")}"))
        }
        // pass 间 clear（权重驻留）
        dut.io.validIn.poke(false.B)
        for (i <- 0 until n) dut.io.aIn(i).poke(0.U)
        dut.io.clear.poke(true.B); dut.clock.step()
        dut.io.clear.poke(false.B)
      }
    }
    // 裁剪：保留 valid 窗口内 + 窗口结束后 2 拍
    val kept = scala.collection.mutable.ArrayBuffer[String]()
    var tail = 2
    for ((anyV, line) <- trace) {
      if (anyV) { kept += line; tail = 2 }
      else if (tail > 0 && kept.nonEmpty) { kept += line; tail -= 1 }
    }
    kept.toSeq
  }

  // 逐行 diff 打印 + 断言（定位轨迹差异位置）
  private def assertTrace(actual: Seq[String], golden: Seq[String]): Unit = {
    if (actual.size != golden.size)
      println(s"[TRACE] size mismatch golden=${golden.size} actual=${actual.size}")
    val maxN = math.max(golden.size, actual.size)
    for (i <- 0 until maxN) {
      val g = golden.lift(i).getOrElse("<none>")
      val a = actual.lift(i).getOrElse("<none>")
      if (g != a) println(s"[TRACE] DIFF @$i\n  golden: $g\n  actual: $a")
    }
    actual shouldBe golden
  }

  // ===================== 级联对齐解析基准 =====================
  // 激励 aIn(i,t)=t+1 (t<=n)、valid=(t<=n)，所有 PE 权重为 w。
  // cj=1 子阵列的数据经左侧 aOut 链延迟 arrayN 拍，valid 经 validOut(0)
  // 级联携带相同延迟，二者对齐。对角波分析（PE(x,y) 数据下标 = t-x-arrayN-y）：
  //   cell(0,1)：末条有效对角波 T=12，4 行各贡献 w*(5+1) → 24w
  //   cell(1,1)：cell(0,1) 终值透传 + 自身 24w → 48w
  // 权重装入全部 PE 后验证 valid 级联对齐（旧广播方案中 cj=1 valid 早于数据 n 拍）。
  private def runAlignmentFinal(fmt: DataFormat.Type, w: Long): Seq[BigInt] = {
    val arrayN = 4; val clusterM = 2; val n = arrayN * clusterM
    var result = Seq.empty[BigInt]
    simulate(new HiveComb(arrayN, clusterM, 16, 16, 0)) { dut =>
      loadAndPrepare(dut, fmt, w, n, arrayN)
      val totalIter = 4 * n + 16
      for (t <- 0 until totalIter) {
        dut.io.validIn.poke((t <= n).B)
        dut.io.fmtIn.poke(fmt); dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
        feedSkewed(dut, n, t) { (tt, _) => if (tt <= n) tt + 1 else 0 }
        for (j <- 0 until n) dut.io.psumIn(j).poke(0.U)
        dut.clock.step()
        if (t == totalIter - 1)
          result = (0 until n).map(j => dut.io.cOut(j).peek().litValue)
      }
    }
    result
  }

  it should "cascaded alignment benchmark: INT8 weight load" in {
    runAlignmentFinal(DataFormat.INT8, 5) shouldBe
      (Seq.fill(4)(BigInt(120)) ++ Seq.fill(4)(BigInt(240)))
  }

  it should "cascaded alignment benchmark: INT16 weight load" in {
    runAlignmentFinal(DataFormat.INT16, 7) shouldBe
      (Seq.fill(4)(BigInt(168)) ++ Seq.fill(4)(BigInt(336)))
  }

  it should "testWeightStationary: HiveComb weight load + multi-pass weight reuse" in {
    val golden = Seq(
      "p0 t= 7 11110000 50,0,0,0,0,0,0,0",
      "p0 t= 8 11110000 110,50,0,0,0,0,0,0",
      "p0 t= 9 11110000 170,110,50,0,0,0,0,0",
      "p0 t=10 11110000 230,170,110,50,0,0,0,0",
      "p0 t=11 11111111 290,230,170,110,180,0,0,0",
      "p0 t=12 11111111 350,290,230,170,300,180,0,0",
      "p0 t=13 11111111 350,350,290,230,420,300,180,0",
      "p0 t=14 11111111 350,350,350,290,540,420,300,180",
      "p0 t=15 11111111 350,350,350,350,660,540,420,300",
      "p0 t=16 00001111 350,350,350,350,780,660,540,420",
      "p0 t=17 00001111 350,350,350,350,780,780,660,540",
      "p0 t=18 00001111 350,350,350,350,780,780,780,660",
      "p0 t=19 00001111 350,350,350,350,780,780,780,780",
      "p0 t=20 00000000 350,350,350,350,780,780,780,780",
      "p0 t=21 00000000 350,350,350,350,780,780,780,780",
      "p1 t= 7 11110000 80,0,0,0,0,0,0,0",
      "p1 t= 8 11110000 140,80,0,0,0,0,0,0",
      "p1 t= 9 11110000 200,140,80,0,0,0,0,0",
      "p1 t=10 11110000 260,200,140,80,0,0,0,0",
      "p1 t=11 11111111 320,260,200,140,320,0,0,0",
      "p1 t=12 11111111 380,320,260,200,440,320,0,0",
      "p1 t=13 11111111 380,380,320,260,560,440,320,0",
      "p1 t=14 11111111 380,380,380,320,680,560,440,320",
      "p1 t=15 11111111 380,380,380,380,800,680,560,440",
      "p1 t=16 00001111 380,380,380,380,920,800,680,560",
      "p1 t=17 00001111 380,380,380,380,920,920,800,680",
      "p1 t=18 00001111 380,380,380,380,920,920,920,800",
      "p1 t=19 00001111 380,380,380,380,920,920,920,920",
      "p1 t=20 00000000 380,380,380,380,920,920,920,920",
      "p1 t=21 00000000 380,380,380,380,920,920,920,920"
    )
    val actual = runCombTrace(DataFormat.INT8, 5, passes = 2)
    assertTrace(actual, golden)
  }

  it should "testVerticalWeightLoading: HiveComb vertical load (+ horizontal config)" in {
    val golden = Seq(
      "p0 t= 7 11110000 30,0,0,0,0,0,0,0",
      "p0 t= 8 11110000 66,30,0,0,0,0,0,0",
      "p0 t= 9 11110000 102,66,30,0,0,0,0,0",
      "p0 t=10 11110000 138,102,66,30,0,0,0,0",
      "p0 t=11 11111111 174,138,102,66,108,0,0,0",
      "p0 t=12 11111111 210,174,138,102,180,108,0,0",
      "p0 t=13 11111111 210,210,174,138,252,180,108,0",
      "p0 t=14 11111111 210,210,210,174,324,252,180,108",
      "p0 t=15 11111111 210,210,210,210,396,324,252,180",
      "p0 t=16 00001111 210,210,210,210,468,396,324,252",
      "p0 t=17 00001111 210,210,210,210,468,468,396,324",
      "p0 t=18 00001111 210,210,210,210,468,468,468,396",
      "p0 t=19 00001111 210,210,210,210,468,468,468,468",
      "p0 t=20 00000000 210,210,210,210,468,468,468,468",
      "p0 t=21 00000000 210,210,210,210,468,468,468,468"
    )
    val actual = runCombTrace(DataFormat.INT8, 3, passes = 1)
    assertTrace(actual, golden)
  }

  it should "testTwoClusterVerticalWeightLoading: HiveComb simultaneous load across 2x2 clusters" in {
    val golden = Seq(
      "p0 t= 7 11110000 70,0,0,0,0,0,0,0",
      "p0 t= 8 11110000 154,70,0,0,0,0,0,0",
      "p0 t= 9 11110000 238,154,70,0,0,0,0,0",
      "p0 t=10 11110000 322,238,154,70,0,0,0,0",
      "p0 t=11 11111111 406,322,238,154,252,0,0,0",
      "p0 t=12 11111111 490,406,322,238,420,252,0,0",
      "p0 t=13 11111111 490,490,406,322,588,420,252,0",
      "p0 t=14 11111111 490,490,490,406,756,588,420,252",
      "p0 t=15 11111111 490,490,490,490,924,756,588,420",
      "p0 t=16 00001111 490,490,490,490,1092,924,756,588",
      "p0 t=17 00001111 490,490,490,490,1092,1092,924,756",
      "p0 t=18 00001111 490,490,490,490,1092,1092,1092,924",
      "p0 t=19 00001111 490,490,490,490,1092,1092,1092,1092",
      "p0 t=20 00000000 490,490,490,490,1092,1092,1092,1092",
      "p0 t=21 00000000 490,490,490,490,1092,1092,1092,1092"
    )
    val actual = runCombTrace(DataFormat.INT16, 7, passes = 1)
    assertTrace(actual, golden)
  }
}
