/** WorkerBeePhase0Spec — 优化报告第 8 章「阶段 0：取证与测量基础设施」的实测入口。
  *
  * 对既有文件的改动均为纯附加观测，不改任何功能逻辑：
  *   ① WorkerBeeCaseSpec.runSeqCase 增默认值 0 的 bpPeriod 形参（反压透传）；
  *   ② io.perf 增 cRowIn 组合抽头（= executor.cPush.valid，零新状态）+ TB 记 array/cRowIn
  *      事件拍号，供 B5 组测 kTile 间回灌依赖下界。
  *
  * 运行（单进程、须带 lz4 头路径与 6G 堆）：
  *   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  *     sbt -J-Xmx6G -J-Djava.io.tmpdir=build/tmp "testOnly WorkerBeePhase0Spec"
  *
  * 产物：build/phase0-20261111/measurements.md（测量报告）+ sim/debug/phase0_*.fst（临时波形）
  *
  * ══ 判据订正 1：读放大不是 dmaB/array 之比 ══
  * 混配与纯 MX8 的 B 逻辑拍数相同（窗口数由 aFmt 的 kPerBeat 决定，每 kTile 16 拍）⟹ dmaB 与
  *   array 计数都相同、比值恒 1。放大体现在每读携带的有效字节只有一半：
  *     bAmp     = B 读数 / distinct 32B 字数
  *     bByteAmp = B 读数×32 / (N×K×bEbits/8)
  *   读数取 plan.expBAddrSeq.size，前提是 TB 逐拍地址对账 addrBadCnt==0 已断言硬件序列==模型序列。
  *   ★阶段3 pairStretch 后混配 B 仅偶窗发读、奇窗缓存复用 ⟹ bAmp/bByteAmp 由 2.0 降到 1.0
  *     （纯格式恒 1.0）；B 读/kTile 16→8，但 predPort 与拍/kTile 不变（约束侧仍是阵列/scaleB）。
  *
  * ══ 判据订正 2：必须计入 scale 通道，它可能是真正的约束侧 ══
  * scaleB 端口 4 B/拍，每列每窗口 scale 需求 = kPerBeat/blk 字节（1/2/4 B）。A 级基址对齐后
  *   每窗口恒 1 对齐读（跳 gather 第二读）⟹ 16 读/kTile。rank2b pairScale 再按需求做同字复用：
  *   需求 2B → F=2（偶窗读、奇窗复用）⟹ 8 读/kTile；需求 1B → F=4 ⟹ 4 读/kTile；
  *   需求 4B → 每窗独立字、不复用 ⟹ 16 读/kTile。sbAmp = scaleB 物理字节 / 有效字节（N×K/blk）。
  *
  * ══ 两个约束侧下界（范围裁定：只 cover WorkerBee 本身）══
  * predPort = max(阵列 16, B读/kTile, A读/kTile, sB读/kTile, sA读/kTile)
  *   —— 本报告的权威口径：WorkerBee 各读端口独立、每端口 1 读/拍，故取 max 而非求和。
  *      sA 项不可省：scaleA 拍数随 m 线性增长，WS M=16 MX8 达 24 拍/kTile 反超阵列 16。
  * predDram = (B物理 + A物理 + sB物理 + sA物理) / 32 / iters
  *   —— 仅作参考列：假设所有字节挤同一条 32 B/拍总线。外部真实 DRAM 时序模型暂缓，
  *      故该列不构成任何硅上周期结论，也不用于收益估算。
  *
  * ══ 结构周期模型（解释「+1 拍/kTile」的唯一失效区）══
  * 每 n-tile：阵列需求 = 16×aKTiles + (totalN-1) 列 skew 尾波；供数需求 = (predPort+1)×aKTiles。
  *   取 max 后乘 nTiles 即结构周期，余下 30~110 拍为 launch/epilogue 固定常数。
  *   aKTiles < totalN-1 时尾波藏不进供数余量 ⟹ 阵列侧反超，cyc/kTile 高于 predPort+1。
  *
  * ══ 带宽扫描的边界 ══
  * bpPeriod 只降 req.ready 占空比（各通道独立端口、相位错开），是受控有效带宽旋钮，
  *   不是 DRAM 时序模型：不模拟 row miss / refresh / 读写翻转，也不模拟共享 DRAM 争用。
  *
  * ══ 测试名长度纪律 ══
  * svsim 工作目录由测试名派生，单个路径分量上限 255 字节 ⟹ 每个 runSeqCase 最多 3 个任务。
  */
import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._
import scala.collection.mutable

class WorkerBeePhase0Spec extends WorkerBeeCaseSpec {
  import WbDebugModel._
  import WorkerBeeCaseSpec.TaskOutcome

  override protected def TAG: String = "PHASE0"

  private val N = 512
  private val PortBytes = 32
  private val ScalePortBytes = 4
  private val BBase = 0x01000000L
  private val MaxCyc = 400000
  private val ArrayFloor = 16.0

  behavior of "WorkerBee 阶段0 取证"

  private case class Meas(label: String, os: Boolean, m: Int, fmts: String, blk: Int, bp: Int,
      iters: Long, nTiles: Int, aKTiles: Int, totalN: Int, cycles: Long, var marginal: Option[Double],
      bReads: Long, bDistinct: Long, bAmp: Double, bUseful: Long, bPhys: Long, bByteAmp: Double,
      sbReads: Long, sbPerKTile: Double, sbUseful: Long, sbPhys: Long, sbAmp: Double,
      aReads: Long, predPort: Double, predDram: Double, cycPerKTile: Double,
      dmaBValid: Int, array: Int, busy: Int, stallA: Int, stallB: Int,
      arrayPerKTile: Double, addrBad: Int, var stage: String = "")

  private val rows = mutable.ArrayBuffer.empty[Meas]
  private var reportNote = ""

  private def params(a: DataFormat.Type, b: DataFormat.Type, k: Int, m: Int, os: Boolean,
      tag: String, blkSel: Int = 0, cFmt: DataFormat.Type = DataFormat.FP32): DebugParams =
    DebugParams(m = m, n = N, k = k, aFmt = a, bFmt = b, cFmt = cFmt,
      loadWMode = true, aMxBlkSel = blkSel, bMxBlkSel = blkSel, cMxBlkSel = 0,
      addrModeSel = AddrMode.MAT.litValue.toInt, rnd = RoundingMode.RNE.litValue.toInt,
      seed = 20261111L, strictAddr = true, maxCycles = MaxCyc,
      waveTarget = s"sim/debug/phase0_$tag.fst", testHint = s"phase0-$tag",
      bBase = BBase, osM1 = os, osFmtKeep = false)

  private def meas(o: TaskOutcome, label: String, blk: Int, bp: Int): Meas = {
    val pl = o.plan
    val p = pl.p
    withClue(s"[$TAG] $label：读放大统计以【硬件地址序列 == 模型序列】为前提，地址对账必须全绿：") {
      o.addrBadCnt should be(0)
    }
    val iters = math.max(pl.nTiles.toLong * pl.aKTiles, 1L)
    val bReads = pl.expBAddrSeq.size.toLong
    val bDistinct = pl.expBAddrSeq.map(a => a & ~(PortBytes - 1).toLong).distinct.size.toLong
    val bUseful = p.n.toLong * p.k * pl.bEbits / 8
    val bPhys = bReads * PortBytes
    val aReads = pl.expAAddrSeq.size.toLong
    val aPhys = aReads * PortBytes
    val sbReads = pl.expScaleBAddrSeq.size.toLong
    val sbPhys = sbReads * ScalePortBytes
    val sbUseful = if (isMxAny(p.bFmt)) p.n.toLong * p.k / p.bMxBlk else 0L
    val sbPerKTile = sbReads.toDouble / iters
    // scaleA 也是独立读端口，且拍数随 m 线性增长（WS M=16 MX8 达 24 拍/kTile > 阵列 16）
    //   ⟹ 必须进 max，否则大 M 下 predPort 偏低、把 scaleA 端口墙误判成别的机制。
    val saReads = pl.expScaleAAddrSeq.size.toLong
    val saPhys = saReads * ScalePortBytes
    val saPerKTile = saReads.toDouble / iters
    val predPort = Seq(ArrayFloor.toDouble, bReads.toDouble / iters, aReads.toDouble / iters,
      sbPerKTile, saPerKTile).max
    val predDram = (bPhys + aPhys + sbPhys + saPhys).toDouble / PortBytes / iters
    val r = Meas(label, p.osM1, p.m, s"${fmtName(p.aFmt)}×${fmtName(p.bFmt)}", blk, bp,
      iters, pl.nTiles, pl.aKTiles, pl.totalN, o.cycles.toLong, None,
      bReads, bDistinct, bReads.toDouble / math.max(bDistinct, 1), bUseful, bPhys,
      bPhys.toDouble / math.max(bUseful, 1),
      sbReads, sbPerKTile, sbUseful, sbPhys,
      if (sbUseful > 0) sbPhys.toDouble / sbUseful else 0.0,
      aReads, predPort, predDram, o.cycles.toDouble / iters,
      o.perf("dmaB"), o.perf("array"), o.perf("busy"), o.perf("stallA"), o.perf("stallB"),
      o.perf("array").toDouble / iters, o.addrBadCnt)
    if (p.osM1) {
      withClue(s"[$TAG] $label：OS 阵列活跃拍恒等式 array == 16×iters + (T-1)×nTiles" +
        "（每窗口 16 拍 + 每 n-tile 一次 15 拍列 skew 尾波，与 K/格式/反压无关）：") {
        o.perf("array").toLong should be(ArrayFloor.toLong * iters + (pl.totalN - 1).toLong * pl.nTiles)
      }
    }
    rows += r
    println(f"[$TAG] $label%-26s iters=$iters%5d cyc=${o.cycles}%7d cyc/kTile=${r.cycPerKTile}%7.3f " +
      f"predPort=$predPort%6.2f predDram=$predDram%6.2f | B读=$bReads%6d dist=$bDistinct%6d " +
      f"bAmp=${r.bAmp}%.2f bByteAmp=${r.bByteAmp}%.2f | sB读=$sbReads%6d sB/kTile=$sbPerKTile%5.1f sbAmp=${r.sbAmp}%.2f " +
      f"| array/kTile=${r.arrayPerKTile}%6.3f stallB=${r.stallB}%6d")
    r
  }

  private def marginalOf(a: Meas, b: Meas): Double = {
    require(b.iters > a.iters, s"[$TAG] 差分要求 iters 递增：${a.iters} → ${b.iters}")
    (b.cycles - a.cycles).toDouble / (b.iters - a.iters).toDouble
  }

  private def setMarginal(label: String, v: Double): Unit =
    rows.find(_.label == label).foreach(_.marginal = Some(v))

  private def dutyOf(bp: Int): Double =
    if (bp == 0) 1.0 else { val low = (bp + 2) / 3; (bp - low).toDouble / bp }

  private def byLabel(s: String): Meas = rows.find(_.label == s).getOrElse(
    throw new IllegalStateException(s"[$TAG] 未找到测量行：$s"))

  /** OS 结构性周期：每 n-tile 取「阵列需求」与「供数需求」的较大者。
    *   阵列需求 = 16×aKTiles + (totalN-1)  ← 每窗口 16 拍 + 一次列 skew 尾波
    *   供数需求 = (predPort+1)×aKTiles    ← 约束侧端口每 kTile 的读拍 + 1 拍切换
    * aKTiles 小时尾波藏不进供数余量，阵列侧反超 ⟹ 这就是「+1 拍/kTile」模型的唯一失效区。
    */
  private def structCycles(r: Meas): Long = {
    val arrayNeed = ArrayFloor.toLong * r.aKTiles + (r.totalN - 1)
    val feedNeed = (r.predPort.toLong + 1) * r.aKTiles
    r.nTiles.toLong * math.max(arrayNeed, feedNeed)
  }

  /** 把 cyc/kTile 相对 (predPort+1) 的残差拆成两项，逐项可核对：
    *   尾波项 = (结构周期 - (predPort+1)×iters)/iters，纯几何、无拟合；
    *   固定项 = (实测 - 结构周期)/iters，launch/epilogue 常数摊薄，随 K 增大而消失。
    */
  private def explain(r: Meas): Unit = {
    val naive = (r.predPort + 1) * r.iters
    val sc = structCycles(r)
    val tail = (sc - naive).toDouble / r.iters
    val fixed = (r.cycles - sc).toDouble / r.iters
    println(f"[$TAG] 残差分解 ${r.label}%-24s aKTiles=${r.aKTiles}%3d 实测=${r.cycPerKTile}%7.3f " +
      f"朴素下界=${r.predPort + 1}%6.2f = 尾波项 $tail%.3f + 固定项 $fixed%.3f" +
      f"（结构周期=$sc 实测=${r.cycles} 固定常数=${r.cycles - sc}%4d 拍）")
  }

  // ══════════ A1：BF16（无 scale）双 K 差分 ══════════
  runSeqCase("-p0-A1", Seq(
    params(DataFormat.BF16, DataFormat.BF16, 512, 1, true, "A1-bf16-k512"),
    params(DataFormat.BF16, DataFormat.BF16, 1024, 1, true, "A1-bf16-k1024")
  )) { os =>
    val a = meas(os(0), "BF16 OS blk16 K512", 16, 0)
    val b = meas(os(1), "BF16 OS blk16 K1024", 16, 0)
    val m = marginalOf(a, b)
    setMarginal("BF16 OS blk16 K1024", m)
    println(f"[$TAG] BF16 OS 边际稳态拍/kTile（差分消开销）= $m%.4f")
    withClue(s"[$TAG] BF16 无 scale 通路，B 侧不得有读放大：") { (a.bAmp, a.bByteAmp) should be((1.0, 1.0)) }
    withClue(s"[$TAG] BF16 OS 稳态拍/kTile 须 = 17（16 阵列下界 + 1 拍开销）：") { m should be(17.0 +- 0.3) }
    writeReport("A1")
  }

  // ══════════ A2：MX8 纯 vs W4A8 混配（blk16），混配双 K 差分 ══════════
  runSeqCase("-p0-A2", Seq(
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 512, 1, true, "A2-mx8"),
    params(DataFormat.MXE4M3, DataFormat.MXE2M1, 512, 1, true, "A2-mix-k512"),
    params(DataFormat.MXE4M3, DataFormat.MXE2M1, 1024, 1, true, "A2-mix-k1024")
  )) { os =>
    val pure = meas(os(0), "MX8xMX8 OS blk16", 16, 0)
    val mix = meas(os(1), "MX8xMX4 OS blk16 K512", 16, 0)
    val mix2 = meas(os(2), "MX8xMX4 OS blk16 K1024", 16, 0)
    val m = marginalOf(mix, mix2)
    setMarginal("MX8xMX4 OS blk16 K1024", m)
    println(f"[$TAG] MX8×MX4 OS 边际稳态拍/kTile = $m%.4f")
    withClue(s"[$TAG] 纯 MX8×MX8 的 B 侧不得有读放大：") { (pure.bAmp, pure.bByteAmp) should be((1.0, 1.0)) }
    withClue(s"[$TAG] 阶段3 pairStretch：混配偶窗读、奇窗缓存复用 ⟹ B 读放大消除（bAmp/bByteAmp 2→1）：") { (mix.bAmp, mix.bByteAmp) should be((1.0, 1.0)) }
    withClue(s"[$TAG] blk16 + kPerBeat=32 ⟹ scale 需求 2B/窗口；rank2b pairScale F=2 偶窗读、奇窗同字复用 ⟹ sB 读/kTile 16→8、sbAmp 2→1：") {
      Seq(pure, mix).foreach { r =>
        r.sbPerKTile should be(8.0 +- 0.01)
        r.sbAmp should be(1.0 +- 0.01)
      }
    }
    withClue(s"[$TAG] rank2b 后 sB 8 读/kTile < 阵列下界 16 ⟹ 约束侧仍是阵列，稳态拍/kTile 须 ≈17（原 25）：") {
      m should be(17.0 +- 1.0)
      pure.cycPerKTile should be(17.0 +- 1.5)
    }
    writeReport("A2")
  }

  // ══════════ A3：MX4 纯（blk16 恰好 4B 对齐）+ INT 混配（无 scale）══════════
  runSeqCase("-p0-A3", Seq(
    params(DataFormat.MXE2M1, DataFormat.MXE2M1, 512, 1, true, "A3-mx4"),
    params(DataFormat.INT8, DataFormat.INT4, 512, 1, true, "A3-intmix", cFmt = DataFormat.INT32)
  )) { os =>
    val mx4 = meas(os(0), "MX4xMX4 OS blk16", 16, 0)
    val im = meas(os(1), "INT8xINT4 OS blk16", 16, 0)
    withClue(s"[$TAG] MX4×MX4：kPerBeat=64、blk16 ⟹ scale 需求 4B/窗口、全对齐 ⟹ 16 读/kTile、sbAmp=1：") {
      mx4.sbPerKTile should be(16.0 +- 0.01)
      mx4.sbAmp should be(1.0 +- 0.01)
      (mx4.bAmp, mx4.bByteAmp) should be((1.0, 1.0))
    }
    withClue(s"[$TAG] MX4×MX4 blk16 K512：aKTiles=8 < totalN-1=15 ⟹ 供数余量 8 拍藏不住 15 拍列 skew 尾波，" +
      "阵列侧反超 7 拍/n-tile ⟹ 结构周期须 = 32×143 = 4576（不是朴素下界 17×256 = 4352）：") {
      structCycles(mx4) should be(4576L)
      mx4.cycles should be > structCycles(mx4)
      (mx4.cycles - structCycles(mx4)) should be < 200L
    }
    explain(mx4)
    withClue(s"[$TAG] 阶段3 pairStretch：INT8×INT4 混配 B 读放大消除（2→1），且 INT 族无 scale 通路：") {
      (im.bAmp, im.bByteAmp) should be((1.0, 1.0))
      im.sbReads should be(0L)
    }
    withClue(s"[$TAG] INT8×INT4 无 scale 瓶颈 ⟹ 稳态拍/kTile 须 ≈17：") { im.cycPerKTile should be(17.0 +- 1.0) }
    writeReport("A3")
  }

  // ══════════ A4：blk32 对照（scale 需求降到 1B/窗口 ⟹ 非对齐比例升到 3/4）══════════
  runSeqCase("-p0-A4", Seq(
    params(DataFormat.MXE4M3, DataFormat.MXE2M1, 512, 1, true, "A4-mix-blk32", blkSel = 1),
    params(DataFormat.MXE2M1, DataFormat.MXE2M1, 512, 1, true, "A4-mx4-blk32", blkSel = 1)
  )) { os =>
    val mix32 = meas(os(0), "MX8xMX4 OS blk32", 32, 0)
    val mx432 = meas(os(1), "MX4xMX4 OS blk32", 32, 0)
    withClue(s"[$TAG] blk32 + kPerBeat=32 ⟹ scale 需求 1B/窗口；rank2b pairScale F=4 每 4 窗一读、余窗同字复用 ⟹ 16→4 读/kTile、sbAmp 4→1：") {
      mix32.sbPerKTile should be(4.0 +- 0.01)
      mix32.sbAmp should be(1.0 +- 0.01)
    }
    withClue(s"[$TAG] blk32 + kPerBeat=64 ⟹ scale 需求 2B/窗口；rank2b pairScale F=2 偶窗读、奇窗同字复用 ⟹ 16→8 读/kTile、sbAmp 2→1：") {
      mx432.sbPerKTile should be(8.0 +- 0.01)
      mx432.sbAmp should be(1.0 +- 0.01)
    }
    withClue(s"[$TAG] rank2b 后 blk32/blk16 同触阵列下界 ⟹ 吞吐持平；blk32 scale 需求更小 ⟹ 同字复用 F 更大 ⟹ sB 读/kTile 反而更少（mix 4<8、mx4 8<16），sbAmp 双方均 1：") {
      mix32.cycPerKTile should be(byLabel("MX8xMX4 OS blk16 K512").cycPerKTile +- 1.0)
      mx432.cycPerKTile should be(byLabel("MX4xMX4 OS blk16").cycPerKTile +- 1.0)
      mix32.sbPerKTile should be < byLabel("MX8xMX4 OS blk16 K512").sbPerKTile
      mx432.sbPerKTile should be < byLabel("MX4xMX4 OS blk16").sbPerKTile
    }
    writeReport("A4")
  }

  // ══════════ A5：0.8 归因 —— MX4 blk16 的 +1.29 拍/kTile 残差是尾波还是真实稳态成本 ══════════
  // 假设：残差 = 列 skew 尾波藏不进供数余量（几何项）+ 固定开销摊薄（随 K 消失）。
  //   MX4 blk16 的 aKTiles = K/64 = 8，供数余量只有 1×8 = 8 拍 < 尾波 15 拍 ⟹ 阵列侧反超 7 拍/n-tile。
  //   K 翻倍后 aKTiles = 16，余量 16 拍 > 15 ⟹ 尾波重新被藏住，残差应塌掉。
  // 判决实验：双 K 差分（nTiles 不变 ⟹ 尾波与固定开销全部消掉）。
  //   若 marginal ≈ 16~17 ⟹ 残差非稳态成本，0.8 闭合；若 ≈ 18.3 ⟹ 下界模型另有缺项。
  runSeqCase("-p0-A5", Seq(
    params(DataFormat.MXE2M1, DataFormat.MXE2M1, 1024, 1, true, "A5-mx4-k1024"),
    params(DataFormat.INT8, DataFormat.INT4, 1024, 1, true, "A5-intmix-k1024", cFmt = DataFormat.INT32)
  )) { os =>
    val mx4a = byLabel("MX4xMX4 OS blk16")
    val mx4b = meas(os(0), "MX4xMX4 OS blk16 K1024", 16, 0)
    val imA = byLabel("INT8xINT4 OS blk16")
    val imB = meas(os(1), "INT8xINT4 OS blk16 K1024", 16, 0)
    val m4 = marginalOf(mx4a, mx4b)
    val mi = marginalOf(imA, imB)
    setMarginal("MX4xMX4 OS blk16 K1024", m4)
    setMarginal("INT8xINT4 OS blk16 K1024", mi)
    explain(mx4b); explain(imB)
    println(f"[$TAG] 0.8 判决：MX4×MX4 blk16 边际=$m4%.4f（K512 含开销 ${mx4a.cycPerKTile}%.3f → K1024 ${mx4b.cycPerKTile}%.3f）" +
      f"；INT8×INT4 边际=$mi%.4f（${imA.cycPerKTile}%.3f → ${imB.cycPerKTile}%.3f）")

    withClue(s"[$TAG] 尾波反超只在 aKTiles < totalN-1 时发生：MX4 blk16 K512 须满足、K1024 须不满足：") {
      mx4a.aKTiles should be < (mx4a.totalN - 1)
      mx4b.aKTiles should be >= (mx4b.totalN - 1)
    }
    withClue(s"[$TAG] 结构模型须解释 MX4 blk16 K512 的尾波项 = (15 - aKTiles)/aKTiles = 7/8：") {
      val tail = (structCycles(mx4a) - (mx4a.predPort + 1) * mx4a.iters).toDouble / mx4a.iters
      tail should be(0.875 +- 0.001)
    }
    withClue(s"[$TAG] 双 K 差分把尾波与固定开销全部消掉 ⟹ MX4 边际须回落到阵列下界附近（16~17），" +
      "而非停在 18.3；若停在 18.3 则残差是真实稳态成本、下界模型缺项：") {
      m4 should be(16.5 +- 1.0)
    }
    withClue(s"[$TAG] INT8×INT4 对照组（aKTiles=16→32，从不触发尾波反超）边际须同样落在 16~17：") {
      mi should be(16.5 +- 1.0)
    }
    withClue(s"[$TAG] 结构模型 + 固定常数须复现实测周期到 3% 以内（固定常数为经验值，不参与判决）：") {
      Seq(mx4a, mx4b, imA, imB).foreach { r =>
        r.cycles.toDouble should be(structCycles(r).toDouble +- (structCycles(r) * 0.03))
      }
    }
    writeReport("A5")
  }

  // ══════════ B：WS（M>1）周期账，裁定 tbd.md:23 ══════════
  runSeqCase("-p0-B", Seq(1, 2, 4, 8, 16).map { m =>
    params(DataFormat.BF16, DataFormat.BF16, 512, m, false, s"B-ws-m$m")
  }) { os =>
    val ms = Seq(1, 2, 4, 8, 16)
    val rs = os.zip(ms).map { case (o, m) => meas(o, s"BF16 WS M=$m", 16, 0) }
    println(s"[$TAG] WS 周期账（改造前 = 16 LOAD_B + m LOAD_A + 1 NEXT + 16 DRAIN = 33+m；" +
      "双 bank 预装载跳过 LOAD_B ⟹ 下界 max(m+1, L+1)）：")
    rs.zip(ms).foreach { case (r, m) =>
      val lb = 2 * r.totalN - 1
      val t = math.max(m + 1.0, (lb + 1).toDouble)
      println(f"[$TAG]   M=$m%2d 改造前=${33.0 + m}%6.2f 实测=${r.cycPerKTile}%7.3f " +
        f"下界=max(m+1,L+1)=$t%5.1f 增益=${(33.0 + m) / r.cycPerKTile}%.3f×")
    }
    // L=31 是 cPush.valid 口径（B5 实测）；C FIFO 是 Queue(flow=false) ⟹ cPop.valid 晚一拍，
    //   而 feed 门查的是 cPop.valid ⟹ 真实回灌下界 L+1=32。m≤16 时 m+1≤17 全部被它压住。
    withClue(s"[$TAG] 双 bank 预装载后 m≤16 全落在 L+1=32 平台上 ⟹ 斜率须≈0（改造前为 1）：") {
      val slope = (rs.last.cycPerKTile - rs.head.cycPerKTile) / (16 - 1)
      slope should be(0.0 +- 0.05)
    }
    withClue(s"[$TAG] M=1 WS 须落在 L+1=32 拍/kTile：") { rs.head.cycPerKTile should be(32.0 +- 0.6) }
    withClue(s"[$TAG] M=16 WS 须同样落在 32 平台（m+1=17 < 32 ⟹ 仍由回灌下界主导）：") {
      rs.last.cycPerKTile should be(32.0 +- 0.6)
    }
    assertGuardActive(os)
    writeReport("B")
  }

  // ══════════ B2/B3/B4：MX WS 周期账（rank4 在 MXFP8/MXFP4 路的承重数）══════════
  // 改造前 BF16 的 33+m 不得搬到 MX，须独立实测：MX 多一条 scaleB 通道。当时实测结论是截距只比
  //   BF16 高 0.107（MX8）/ 0.180（MX4）拍 ⟹ 收益格式无关。
  // 双 bank 预装载落地后 LOAD_B 整段被跳过 ⟹ 三格式一律压到 max(m+1, L+1) 平台，m≤16 时即 32，
  //   斜率由 1 变 0。格式差异只剩固定常数（scaleB 通道），不再进入斜率。
  // perf.array 在 WS 下 = executor.wbValidIn（WorkerBee.scala:549）⟹ array/kTile 是真实供
  //   激活拍，cyc/kTile − array/kTile = 排空开销。该开销【不是】可白拿的隐藏池：
  //   其中含 kTile 间经 C buffer 回灌 psum 的波前依赖下界 L=2*totalN-1（B5 组实测钉死）。
  private val wsMs = Seq(1, 2, 4, 8, 16)

  private def wsMxSweep(tag: String, fmt: DataFormat.Type, label: String): Unit =
    runSeqCase(s"-p0-$tag", wsMs.map { m => params(fmt, fmt, 512, m, false, s"$tag-m$m") }) { os =>
      val rs = os.zip(wsMs).map { case (o, m) => meas(o, s"$label WS M=$m", 16, 0) }
      println(s"[$TAG] $label WS 周期账（blk16、loadW=1；改造前对照 BF16 的 33+m）：")
      rs.zip(wsMs).foreach { case (r, m) =>
        val ovh = r.cycPerKTile - r.arrayPerKTile
        val lb = 2 * r.totalN - 1
        val t = math.max(m + 1.0, (lb + 1).toDouble)
        println(f"[$TAG]   M=$m%2d 实测=${r.cycPerKTile}%7.3f array/kTile=${r.arrayPerKTile}%6.3f " +
          f"非阵列开销=$ovh%6.3f sB/kTile=${r.sbPerKTile}%5.1f stallB=${r.stallB}%6d " +
          f"｜下界=max(m+1,L+1)=$t%5.1f 增益=${(33.0 + m) / r.cycPerKTile}%.3f×")
      }
      val slope = (rs.last.cycPerKTile - rs.head.cycPerKTile) / (wsMs.last - wsMs.head).toDouble
      val intercept = rs.head.cycPerKTile - wsMs.head * slope
      println(f"[$TAG] $label WS 拟合：斜率=$slope%.4f 截距=$intercept%.3f（改造前 BF16 = 1.0000 / 33.0）")
      withClue(s"[$TAG] $label WS 预装载后 m≤16 须落在 L+1=32 平台 ⟹ 斜率≈0：") {
        slope should be(0.0 +- 0.05)
      }
      assertGuardActive(os)
      writeReport(tag)
    }

  // 陷阱4 守护的承重证据：FSM 名义周期 = m+1+(30-m) = 31 < 有效下界 32 ⟹ 每个带 partial-sum
  //   回灌的 kTile 至少停等 1 拍。改造前 33+m ≫ 32，stallA 恒 0；S4 之后必须 > 0。
  //   带反馈的 kTile 数 = iters − nTiles（每个 N tile 的首个 K pass 无回灌）。
  private def assertGuardActive(os: Seq[TaskOutcome]): Unit =
    os.foreach { o =>
      val iters = math.max(o.plan.nTiles.toLong * o.plan.aKTiles, 1L)
      val feedbackTiles = iters - o.plan.nTiles
      withClue(s"[$TAG] ${o.p.testHint}：cPop.valid 守护须真实停等（名义 31 < 下界 32），" +
        s"stallA 须 ≥ 带反馈 kTile 数 $feedbackTiles（改造前恒 0）：") {
        o.perf("stallA").toLong should be >= feedbackTiles
      }
    }

  wsMxSweep("B2", DataFormat.MXE4M3, "MX8xMX8")
  wsMxSweep("B3", DataFormat.MXE2M1, "MX4xMX4")

  // B4：M=8 双 K 差分，消 launch/epilogue 固定常数 ⟹ MX WS 稳态边际拍/kTile。
  //   增益以【边际】为分子基准：改造前 16 拍 LOAD_B 是每 kTile 的稳态成本，落在边际内。
  runSeqCase("-p0-B4", Seq(
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 1024, 8, false, "B4-mx8-k1024"),
    params(DataFormat.MXE2M1, DataFormat.MXE2M1, 1024, 8, false, "B4-mx4-k1024")
  )) { os =>
    val hi8 = meas(os(0), "MX8xMX8 WS M=8 K1024", 16, 0)
    val hi4 = meas(os(1), "MX4xMX4 WS M=8 K1024", 16, 0)
    val mg8 = marginalOf(byLabel("MX8xMX8 WS M=8"), hi8)
    val mg4 = marginalOf(byLabel("MX4xMX4 WS M=8"), hi4)
    setMarginal("MX8xMX8 WS M=8 K1024", mg8)
    setMarginal("MX4xMX4 WS M=8 K1024", mg4)
    println(f"[$TAG] MX WS 稳态边际（M=8，K512→K1024 差分）：MX8×MX8=$mg8%.4f MX4×MX4=$mg4%.4f")
    val lb = 2 * hi8.totalN - 1
    val t = math.max(8 + 1.0, (lb + 1).toDouble)
    println(f"[$TAG]   rank4 A（rd_sel 随 skew ⟹ 下界 max(m+1,L+1)=$t%.1f）：实测边际距下界 " +
      f"MX8×MX8=${mg8 / t}%.3f× MX4×MX4=${mg4 / t}%.3f×（1.000 = 贴死下界）")
    writeReport("B4")
  }

  // ══════════ B5：kTile 间 partial-sum 回灌依赖下界 L（rank4 的承重取证）══════════
  // 为什么存在与 m 无关的硬下界：第 r 行的回灌拍 = 本 pass 第 r 行激活拍 + 周期 T，而它的结果行
  //   在激活拍 + L 才入 C FIFO ⟹ T ≥ L，r 与 m 全部消掉。
  // L 的源码闭式 = (arrayN-1) 纵向 validV 链 + (arrayM-1) 末列横向 skew + 1 输出寄存
  //   = 2*totalN-1 = 31（WbComb.scala:264/268/315 + WbWorker.scala:195）。
  //   【已实测确认】2026-11-13：min=max=31，跨 m∈{1,8} 与 MX8/MX4 共 6656 样本零例外。
  // ⚠️ 有效下界是 L+1 而非 L：L 测在 cPush.valid，C FIFO 为 Queue(flow=false) ⟹ cPop.valid
  //   晚一拍。S5 已把 cPop.valid 并入 feed 门 ⟹ 压到 L+1 以下会停等（可观测），不再是静默回灌。
  // 观测口径：perf.array（=wbValidIn，每输入行 1 拍）与 perf.cRowIn（=cPush.valid，每
  //   deskew 对齐结果行 1 拍）是同序等长两条流 ⟹ 逐下标拍号相减即 L；array 相邻事件差
  //   >1 处即 kTile 边界，其差值就是实测周期 T。
  private def wavefront(o: TaskOutcome, label: String): Int = {
    withClue(s"[$TAG] $label：L 探针须建立在任务正常跑完且地址对账全绿之上：") {
      (o.done, o.errSeen, o.addrBadCnt) should be((true, false, 0))
    }
    val arr = o.perfEv("array"); val cin = o.perfEv("cRowIn")
    withClue(s"[$TAG] $label：array 与 cRowIn 须同序等长（每输入激活行恰产生一结果行）：") {
      arr.size should be > 0
      cin.size should be(arr.size)
    }
    val lat = arr.zip(cin).map { case (a, c) => c - a }
    val gaps = arr.sliding(2).collect { case Seq(x, y) if y - x > 1 => y - x }.toSeq
    val mode = if (gaps.isEmpty) 0 else gaps.groupBy(identity).maxBy(_._2.size)._1
    // 边界间隔是「上一 kTile 末行 → 下一 kTile 首行」的空档，不是周期本身：
    //   周期 T = 空档 + (m-1)（kTile 内 m 行逐拍连续）。
    val t = mode + o.p.m - 1
    println(f"[$TAG] $label%-14s 波前延迟 L：min=${lat.min}%3d max=${lat.max}%3d " +
      f"均值=${lat.sum.toDouble / lat.size}%7.3f 样本=${lat.size}%6d ｜ kTile 边界=${gaps.size}%4d " +
      f"空档众数=$mode%3d ⟹ 周期 T=$t%3d")
    withClue(s"[$TAG] $label：L 须为常数（纯移位链延迟，不随行号/m/格式变化）：") {
      lat.max should be(lat.min)
    }
    withClue(s"[$TAG] $label：事件流推出的周期 T 须不低于回灌下界 L：") { t should be >= lat.min }
    lat.min
  }

  runSeqCase("-p0-B5", Seq(
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 512, 1, false, "B5-mx8-m1"),
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 512, 8, false, "B5-mx8-m8"),
    params(DataFormat.MXE2M1, DataFormat.MXE2M1, 512, 8, false, "B5-mx4-m8")
  )) { os =>
    val labels = Seq("MX8xMX8 M=1", "MX8xMX8 M=8", "MX4xMX4 M=8")
    val ls = labels.zip(os).map { case (lb, o) => wavefront(o, lb) }
    val l = ls.head
    val totalN = os.head.plan.totalN
    withClue(s"[$TAG] L 须跨 m（1→8）与跨格式（MX8→MX4）恒定：") { ls.foreach(_ should be(l)) }
    withClue(s"[$TAG] L 须等于源码闭式 2*totalN-1（纵向 15 + 末列横向 15 + 输出寄存 1）：") {
      l should be(2 * totalN - 1)
    }
    os.foreach { o =>
      val iters = math.max(o.plan.nTiles.toLong * o.plan.aKTiles, 1L)
      withClue(s"[$TAG] 实测拍/kTile 不得低于有效回灌下界 L+1（C FIFO 写→读延迟一拍）：") {
        o.cycles.toDouble / iters should be >= (l + 1).toDouble
      }
    }
    val sb = new StringBuilder
    sb ++= s"\n## B5：kTile 间回灌依赖下界（行级波前延迟 L）\n\n"
    sb ++= s"- 实测 L = **$l 拍**，跨 m∈{1,8} 与 MX8/MX4 恒定，等于源码闭式 2*totalN-1 = ${2 * totalN - 1}\n"
    sb ++= "- 口径：`perf.array`（wbValidIn）与 `perf.cRowIn`（cPush.valid）逐下标拍号相减\n"
    sb ++= s"- **有效下界是 L+1 = ${l + 1}**：L 测在 cPush.valid，而 C FIFO 是 `Queue(flow=false)`" +
      "（Stream.scala:371）⟹ cPop.valid 晚一拍，feed 门查的正是 cPop.valid\n"
    sb ++= "- 承重性：S5 已把 `cPop.valid` 并入 feed 门 ⟹ 周期压到 L+1 以下会**停等**而非静默回灌错值\n"
    sb ++= "- ⟹ rank4 方案 A（wr_sel 广播锁存 / rd_sel 随 A 口 skew）隐藏 16 拍 LOAD_B 后 " +
      "**T = max(m+1, L+1, 16/duty)**；rd_sel 若误做成全局广播则退化为 max(m+30, L+1)，增益塌到 ≈1.08×\n\n"
    sb ++= "| M | 改造前 33+m | A 下界 max(m+1,L+1) | 真实增益 | 忽略 L 的假想增益 |\n|---|---|---|---|---|\n"
    Seq(1, 2, 4, 8, 16, 30, 32).foreach { m =>
      val base = 33.0 + m; val t = math.max(m + 1.0, (l + 1).toDouble)
      val tag = if (m > 16) "（外推）" else ""
      sb ++= f"| $m$tag | $base%.1f | $t%.1f | ${base / t}%.3f× | ${base / (m + 1.0)}%.3f× |\n"
      println(f"[$TAG]   M=$m%2d 改造前=$base%5.1f A下界=$t%5.1f 真实增益=${base / t}%.3f× " +
        f"（忽略 L 则假想为 ${base / (m + 1.0)}%.3f×）$tag")
    }
    reportNote = sb.toString
    writeReport("B5")
  }

  // ══════════ B6：rd_sel 对角传播取证 + blk32 + 节流 B 供给下的预装载 ══════════
  // 陷阱5 的正向证据：方案 A 的 rd_sel 是【随波前对角传播】的电平（行 skew i + 横链 j），
  //   不是全局广播。两个角 PE 的上升沿必须恰差 (arrayN-1)+(arrayM-1)=30 拍。
  //   若误做成全局广播则差 0，且周期会被迫抬到 m+30 ⟹ 本组与 B 组互为反证。
  // 同时覆盖 blk32（scaleB 字内偏移的另一档）与 bp=5 节流：B 供给变慢时预装载可能来不及
  //   在 drain 内装完 ⟹ 走 sLOAD_B 全量重推回退路径，该路径也必须数值正确。
  runSeqCase("-p0-B6", Seq(
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 512, 8, false, "B6-mx8-blk16"),
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 512, 8, false, "B6-mx8-blk32", blkSel = 1),
    params(DataFormat.MXE2M1, DataFormat.MXE2M1, 512, 1, false, "B6-mx4-blk32", blkSel = 1)
  ), 5) { os =>
    val labels = Seq("MX8 blk16 M=8", "MX8 blk32 M=8", "MX4 blk32 M=1")
    val totalN = os.head.plan.totalN
    val diag = 2 * (totalN - 1)
    labels.zip(os).foreach { case (lb, o) =>
      val first = o.perfEv("rdSelFirst"); val last = o.perfEv("rdSelLast")
      withClue(s"[$TAG] $lb：两个角 PE 都必须观测到 rd_sel 翻面（否则双 bank 从未被切换）：") {
        first.size should be > 0
        last.size should be(first.size)
      }
      val offs = first.zip(last).map { case (f, l) => l - f }
      println(f"[$TAG] $lb%-14s rd_sel 对角偏移：min=${offs.min}%3d max=${offs.max}%3d " +
        f"翻面次数=${first.size}%4d（源码闭式 (arrayN-1)+(arrayM-1)=$diag）")
      withClue(s"[$TAG] $lb：rd_sel 须随波前对角传播 ⟹ 偏移恒为 $diag（全局广播会是 0）：") {
        offs.foreach(_ should be(diag))
      }
    }
    writeReport("B6")
  }

  // ══════════ B7：零余量带宽档位（bp=4）下预装载不得掉进回退悬崖 ══════════
  // duty(bp) = (bp − (bp+2)/3)/bp 用整数除法 ⟹ 非单调，全局最小 duty=0.5 只在 bp=2/4 出现，
  //   对应 B 端口需求 16/duty = 32 拍，恰等于有效回灌下界 L+1=32 ⟹ **B 侧余量为零**。
  //   （旧下界 31 时 bp=4 是唯一「退化档位」；订正为 32 后 B 侧不再退化，但余量归零。）
  // 为什么仍必须测：预装载窗口 = m+1+D = 32 拍，16 个 B 字在 duty0.5 下也恰需 32 拍。
  //   一旦预蓄门（bOccupancy≥16）延迟点火使窗口不够，FSM 回退走完整 sLOAD_B ⟹ 周期变成
  //   m + 1 + (30−m) + 16 = **47 拍**（同时付拉长的 drain 与完整装载），m≤13 时比改造前
  //   33+m 还慢。故风险是**悬崖**而非平局。
  // ⚠️ 下界必须取【全部读端口】的最大值，不能只看 B：**scaleA 拍数随 m 线性增长**
  //   （本配置实测 1.5×m 拍/kTile），m=16 时 24 拍 ÷ duty0.5 = 48 拍 ⟹ scaleA 端口先撞墙，
  //   此时周期 48 与回退值 47 数值接近但**成因完全不同**（实测 stallB 仅 35、scaleA 占用
  //   97.6% ⟹ 预装载根本没回退）。只断言 32 会把端口墙误判成预装载缺陷。
  runSeqCase("-p0-B7", Seq(1, 8, 16).map { m =>
    params(DataFormat.MXE4M3, DataFormat.MXE4M3, 512, m, false, s"B7-mx8-bp4-m$m")
  }, 4) { os =>
    val ms = Seq(1, 8, 16)
    val duty = dutyOf(4)
    println(f"[$TAG] B7 零余量档位 bp=4 duty=$duty%.4f ⟹ B 端口需求=${16.0 / duty}%.1f 拍/kTile，" +
      f"有效回灌下界 L+1=32 ⟹ B 侧余量=${32.0 - 16.0 / duty}%.1f 拍")
    os.zip(ms).foreach { case (o, m) =>
      val r = meas(o, s"MX8 WS bp4 M=$m", 16, 4)
      val pl = o.plan
      val iters = math.max(pl.nTiles.toLong * pl.aKTiles, 1L).toDouble
      // 各读端口的拍/kTile（期望地址序列来自张量语义，非拟合 RTL）
      val perKt = Seq("B" -> pl.expBAddrSeq.size / iters, "A" -> pl.expAAddrSeq.size / iters,
        "scaleA" -> pl.expScaleAAddrSeq.size / iters, "scaleB" -> pl.expScaleBAddrSeq.size / iters)
      val (bindName, bindBeats) = perKt.maxBy(_._2)
      val portBound = bindBeats / duty
      val lb = 2 * r.totalN - 1 + 1
      val bound = math.max(lb.toDouble, portBound)
      val fallback = (m + 1) + (2 * r.totalN - 2 - m) + r.totalN
      println(f"[$TAG]   M=$m%2d 实测=${r.cycPerKTile}%7.3f stallB=${r.stallB}%6d " +
        f"｜端口拍/kTile=${perKt.map { case (n, v) => f"$n=$v%.1f" }.mkString(" ")} " +
        f"⟹ 约束侧=$bindName 端口下界=$portBound%.1f 回灌下界=$lb ⟹ 取 $bound%.1f" +
        f"（若预装载回退则≈$fallback%d）")
      withClue(s"[$TAG] M=$m bp=4：周期须落在 max(回灌下界 $lb, 各读端口下界 $portBound%.1f) " +
        s"= $bound%.1f 上；显著高于它才说明预装载回退（≈$fallback%d）：") {
        r.cycPerKTile should be(bound +- 1.0)
      }
    }
    writeReport("B7")
  }

  // ══════════ C：受控有效带宽扫描（bpPeriod 真正透传）══════════
  private def sweep(bp: Int): Unit =
    runSeqCase(s"-p0-C$bp", Seq(
      params(DataFormat.BF16, DataFormat.BF16, 512, 1, true, s"C$bp-bf16"),
      params(DataFormat.MXE4M3, DataFormat.MXE2M1, 512, 1, true, s"C$bp-mix")
    ), bp) { os =>
      val duty = dutyOf(bp)
      val bf = meas(os(0), s"BF16 OS duty$duty", 16, bp)
      val mx = meas(os(1), s"MX8xMX4 OS duty$duty", 16, bp)
      val bfPred = math.max(ArrayFloor, 16.0 / duty)
      // 混配 pair F=2：偶 kTile 为读窗（16 读受 duty 节流 ⟹ 16/duty 拍），奇 kTile 为复用窗
      //   （16 缓存推、无读、全速 ⟹ 16 拍）⟹ 稳态拍/kTile = (16/duty + 16)/2 = 8/duty + 8。
      //   duty=1 时 = 16（阵列下界）；duty<1 时读窗节流项主导，复用窗仍全速 ⟹ 优于 BF16 全节流。
      val mxPred = math.max(ArrayFloor, 8.0 + 8.0 / duty)
      println(f"[$TAG] 带宽扫描 bp=$bp duty=$duty%.4f：BF16 预测=$bfPred%.3f（B 端口 16 读/kTile 全节流）" +
        f" 混配预测=$mxPred%.3f（pair F=2 半读窗节流 16/duty + 半复用窗全速 16，均摊 8/duty+8）")
      withClue(s"[$TAG] 反压必须真的生效：perf.dmaB 计 valid 周期，须 > 真实事务数：") {
        Seq(bf, mx).foreach { r => r.dmaBValid.toLong should be > r.bReads }
      }
      withClue(s"[$TAG] duty<1 时读窗受节流：BF16 全读窗=16/duty；混配 pair 半读窗半复用窗=8/duty+8：") {
        bf.cycPerKTile should be(bfPred +- (bfPred * 0.25))
        mx.cycPerKTile should be(mxPred +- (mxPred * 0.25))
      }
      writeReport(s"C$bp")
    }

  sweep(3)
  sweep(5)

  // ══════════ 测量报告落盘（build/ 只放报告与日志）══════════
  private def writeReport(stage: String): Unit = {
    rows.filter(_.stage.isEmpty).foreach(_.stage = stage)
    val dir = Paths.get("build/phase0-20261111")
    Files.createDirectories(dir)
    val f = dir.resolve("measurements.md")
    val sb = new StringBuilder
    sb ++= "# 阶段 0 实测数据（WorkerBeePhase0Spec）\n\n"
    sb ++= s"- 形状：N=$N，M=1（B 组除外），loadW=1，cFmt=FP32（INT 组 INT32），seed=20261111，bBase=0x01000000\n"
    sb ++= "- 证据等级：RTL 仿真计数（理想存储模型、零延迟响应）；**无硅、无 STA、无功耗仿真**\n"
    sb ++= "- 读数取 `plan.exp*AddrSeq.size`，前提是逐拍地址对账 `addrBadCnt==0`（已硬断言）\n"
    sb ++= "- `bAmp` = B读数/distinct32B；`bByteAmp` = B读数×32/(N×K×bEbits/8)\n"
    sb ++= "- `sbAmp` = scaleB读数×4/(N×K/blk)；`sB/kTile` = scaleB读数/iters（scaleB 端口 1 读/拍）\n"
    sb ++= "- `predPort` = max(16, B读/kTile, A读/kTile, sB读/kTile, sA读/kTile)：**权威口径**，WorkerBee 各端口独立 ⟹ 取 max；sA 随 m 增长，大 M 下可反超阵列 16\n"
    sb ++= "- `predDram` = (B物理+A物理+sB物理+sA物理)/32/iters：**仅参考列**，外部真实 DRAM 模型暂缓，不作硅上结论\n"
    sb ++= "- `marginal` = 双 K 差分稳态拍/kTile（nTiles 不变 ⟹ 消列 skew 尾波与固定开销）\n"
    sb ++= "- 结构周期 = nTiles × max(16×aKTiles + totalN-1, (predPort+1)×aKTiles)\n"
    sb ++= "- `固定常数` = 实测 − 结构周期，仅 bp=0 的 OS 行有意义（29~107 拍，launch/epilogue）；反压行含 stall 故不列\n\n"
    sb ++= "| 组 | 标签 | 模式 | M | 格式 | blk | bp | nT | aKT | iters | cycles | cyc/kTile | predPort | predDram | marginal | 固定常数 | B读 | distinct32 | bAmp | bByteAmp | sB读 | sB/kTile | sbAmp | array/kTile | stallB | dmaBvalid |\n"
    sb ++= "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n"
    rows.foreach { r =>
      sb ++= s"| ${r.stage} | ${r.label} | ${if (r.os) "OS" else "WS"} | ${r.m} | ${r.fmts} | ${r.blk} | ${r.bp} | " +
        s"${r.nTiles} | ${r.aKTiles} | ${r.iters} | ${r.cycles} | " +
        f"${r.cycPerKTile}%.3f | ${r.predPort}%.2f | ${r.predDram}%.2f | " + r.marginal.map(v => f"$v%.4f").getOrElse("—") +
        s" | ${if (r.os && r.bp == 0) (r.cycles - structCycles(r)).toString else "—"} | " +
        s"${r.bReads} | ${r.bDistinct} | ${f"${r.bAmp}%.2f"} | ${f"${r.bByteAmp}%.2f"} | ${r.sbReads} | ${f"${r.sbPerKTile}%.1f"} | ${f"${r.sbAmp}%.2f"} | " +
        f"${r.arrayPerKTile}%.3f | ${r.stallB} | ${r.dmaBValid} |\n"
    }
    if (reportNote.nonEmpty) sb ++= reportNote
    Files.write(f, sb.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    println(s"[$TAG] 测量报告已落盘：$f（累计 ${rows.size} 行）")
  }
}