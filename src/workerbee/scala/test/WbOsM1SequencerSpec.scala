import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

/** 首行链：Sequencer + T 个 WbWorkerOsM1 横向串接（仅测试侧）。 */
class WbOsM1RowHarness(n: Int = 16) extends WbOsM1RowDut(n) {
  private val fmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4)
  val seq = Module(new WbOsM1Sequencer(T = T, hasMx = false))
  val pes = Seq.fill(T)(Module(new WbWorkerOsM1(16, 16, 48, fmts)))

  val io = IO(new WbOsM1RowIo(T))

  seq.io.aIn << io.aIn
  seq.io.bIn << io.bIn
  seq.io.mxMode := false.B
  seq.io.scaleOne := false.B
  seq.io.scaleAStep := 4.U
  seq.io.scaleBStep := 4.U
  seq.io.scaleABlk := 16.U
  seq.io.scaleBBlk := 16.U
  seq.io.flush := io.flush
  seq.io.start := io.start
  seq.io.kTiles := io.kTiles

  for (j <- 0 until T) {
    val pe = pes(j).io
    pe.aIn := (if (j == 0) seq.io.aOut else pes(j - 1).io.aOut)
    pe.validIn := (if (j == 0) seq.io.aTokenValid else pes(j - 1).io.validOut)
    pe.scaleAIn := (if (j == 0) 0.U else pes(j - 1).io.scaleAOut)
    pe.osLastIn := (if (j == 0) seq.io.aLast else pes(j - 1).io.osLastOut)
    pe.osB := seq.io.bOut(j)
    pe.osScaleB := 0.U
    pe.osSeed := io.seed(j)
    pe.osSeedEn := io.seedEn
    pe.osAdvance := seq.io.advance
    pe.osM1 := true.B
    pe.psumIn := 0.U
    pe.loadHIn := io.loadH
    pe.loadVInLock := false.B
    pe.loadVIn := false.B
    pe.loadWIn := false.B
    pe.loadWInLock := false.B
    pe.validInV := false.B
    pe.fmtIn := io.fmt
    pe.bFmtIn := io.fmt
    pe.scaleBIn := 0.U
    pe.rndIn := RoundingMode.RNE
    pe.clear := io.clear
    seq.io.consume(j) := pe.osConsume
    seq.io.done(j) := pe.osDone
    io.consume(j) := pe.osConsume
    io.done(j) := pe.osDone
    io.validIn(j) := pe.validIn
    io.psum(j) := pe.psumOut
  }
  io.advance := seq.io.advance
  io.aTokenValid := seq.io.aTokenValid
  io.resultsReady := seq.io.resultsReady
  io.phase := seq.io.phase
  io.headValid := seq.io.headValid
}

class WbOsM1SequencerSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private type Src = WbOsM1Src
  private val Task = WbOsM1Task
  private def pack(words: Seq[BigInt], w: Int): BigInt = WbOsM1Src.pack(words, w)

  behavior of "WbOsM1Sequencer first-row chain"

  it should "complete stall-free INT8/INT4 tasks in exactly L+T-1 advances per N tile" in {
    simulate(new WbOsM1RowHarness()) { dut =>
      val r = new WbOsM1RowRunner(dut, "WbOsM1SequencerSpec")
      r.runTask(Task(DataFormat.INT8, kTiles = 1, nTiles = 1, seed = 11, exact = true))
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 3, seed = 12, exact = true))
      r.runTask(Task(DataFormat.INT4, kTiles = 4, nTiles = 2, seed = 13, exact = true))
      r.runTask(Task(DataFormat.INT4, kTiles = 1, nTiles = 2, seed = 14, exact = true))
    }
  }

  it should "freeze Run during Prime until A and every column head are valid" in {
    simulate(new WbOsM1RowHarness()) { dut =>
      val r = new WbOsM1RowRunner(dut, "WbOsM1SequencerSpec")
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 1, seed = 21, primeWait = 0, bDelay = 60))
      r.runTask(Task(DataFormat.INT8, kTiles = 1, nTiles = 2, seed = 22, primeWait = 0, aDelay = 45))
      r.runTask(Task(DataFormat.INT4, kTiles = 2, nTiles = 2, seed = 23, primeWait = 0))
    }
  }

  it should "terminate Tail without further A and keep prefetched next-tile data" in {
    simulate(new WbOsM1RowHarness()) { dut =>
      val r = new WbOsM1RowRunner(dut, "WbOsM1SequencerSpec")
      r.runTask(Task(DataFormat.INT8, kTiles = 1, nTiles = 1, seed = 31, primeWait = 80))
      r.runTask(Task(DataFormat.INT4, kTiles = 2, nTiles = 4, seed = 32, primeWait = 80))
    }
  }

  it should "match tensor goldens under random A/B stalls" in {
    simulate(new WbOsM1RowHarness()) { dut =>
      val r = new WbOsM1RowRunner(dut, "WbOsM1SequencerSpec")
      for ((s, i) <- Seq(41L, 42L, 43L).zipWithIndex) {
        val fmt = if (i % 2 == 0) DataFormat.INT8 else DataFormat.INT4
        r.runTask(Task(fmt, kTiles = 2 + i, nTiles = 3, seed = s, pA = 0.6, pB = 0.4, primeWait = i * 7))
      }
    }
  }

  it should "restart cleanly after a flush in the middle of Run" in {
    simulate(new WbOsM1RowHarness()) { dut =>
      val r = new WbOsM1RowRunner(dut, "WbOsM1SequencerSpec")
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 2, seed = 51, abortAfter = 21))
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 2, seed = 52, pA = 0.3, pB = 0.3))
    }
  }

  behavior of "WbOsM1Sequencer A/scaleA capture"

  /** 软件首行 token 链模型驱动 consume/done，检查 A/scaleA 与 B/scaleB 按 lane 与 kt 配对。 */
  private def runStandalone(mx: Boolean, seed: Long, t: Int = 4, scaleW: Int = 16, gA: Int = 4, gB: Int = 4,
                            blkA: Int = 16, blkB: Int = 16, one: Boolean = false): Unit = {
    val kTiles = 2
    val nTiles = 3
    val tag = s"[mx=$mx T=$t gA=$gA/blkA=$blkA gB=$gB/blkB=$blkB one=$one]"
    // 字节选择金锚：lane 覆盖 K 元素 [lane*g, lane*g+g-1]，所属 MX 块号 = floor(lane*g/blk)
    def selByte(word: BigInt, lane: Int, g: Int, blk: Int): BigInt =
      if (one) BigInt(127) else (word >> ((lane * g / blk) * 8)) & 0xFF
    simulate(new WbOsM1Sequencer(T = t, aEffW = 8, bW = 8, scaleAW = scaleW, scaleBW = scaleW, ktW = 4)) { dut =>
      val rng = new Random(seed)
      val nA = kTiles * nTiles
      val nB = nA * t
      val aWin = Vector.fill(nA, t)(BigInt(8, rng))
      val aScale = Vector.fill(nA)(BigInt(scaleW, rng))
      val bWin = Vector.fill(nB, t)(BigInt(8, rng))
      val bScale = Vector.fill(nB)(BigInt(scaleW, rng))
      val aSrc = new Src(aWin.map(pack(_, 8)), new Random(seed + 1), 0.5, 0)
      val sASrc = new Src(aScale, new Random(seed + 2), 0.5, 3)
      val bSrc = new Src(bWin.map(pack(_, 8)), new Random(seed + 3), 0.3, 0)
      val sBSrc = new Src(bScale, new Random(seed + 4), 0.3, 5)
      val tok = Array.fill(t)(false)
      val lst = Array.fill(t)(false)
      val done = Array.fill(t)(false)
      val counts = Array.fill(t)(0)
      var cycle = 0
      var holdA: Option[BigInt] = None
      val holdB = Array.fill[Option[BigInt]](t)(None)

      dut.io.mxMode.poke(mx.B)
      dut.io.scaleOne.poke(one.B)
      dut.io.scaleAStep.poke(gA.U)
      dut.io.scaleBStep.poke(gB.U)
      dut.io.scaleABlk.poke(blkA.U)
      dut.io.scaleBBlk.poke(blkB.U)
      dut.io.flush.poke(false.B)
      dut.io.start.poke(false.B)
      dut.io.kTiles.poke(kTiles.U)
      dut.io.consume.foreach(_.poke(false.B))
      dut.io.done.foreach(_.poke(false.B))
      dut.io.aIn.valid.poke(false.B)
      dut.io.bIn.valid.poke(false.B)
      dut.io.scaleAIn.get.valid.poke(false.B)
      dut.io.scaleBIn.get.valid.poke(false.B)
      dut.reset.poke(true.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(1)

      def step(start: Boolean = false, clear: Boolean = false): Boolean = {
        val av = aSrc.offer(cycle); val sav = sASrc.offer(cycle)
        val bv = bSrc.offer(cycle); val sbv = sBSrc.offer(cycle)
        dut.io.aIn.valid.poke(av.nonEmpty.B)
        dut.io.aIn.payload.poke(av.getOrElse(BigInt(0)).U)
        dut.io.scaleAIn.get.valid.poke(sav.nonEmpty.B)
        dut.io.scaleAIn.get.payload.poke(sav.getOrElse(BigInt(0)).U)
        dut.io.bIn.valid.poke(bv.nonEmpty.B)
        dut.io.bIn.payload.poke(bv.getOrElse(BigInt(0)).U)
        dut.io.scaleBIn.get.valid.poke(sbv.nonEmpty.B)
        dut.io.scaleBIn.get.payload.poke(sbv.getOrElse(BigInt(0)).U)
        dut.io.start.poke(start.B)
        for (j <- 0 until t) dut.io.done(j).poke(done(j).B)

        val adv = dut.io.advance.peek().litToBoolean
        val validIn = Vector.tabulate(t)(j => if (j == 0) dut.io.aTokenValid.peek().litToBoolean else tok(j - 1))
        val lastIn = Vector.tabulate(t)(j => if (j == 0) dut.io.aLast.peek().litToBoolean else lst(j - 1))
        val consume = Vector.tabulate(t)(j => adv && validIn(j))
        for (j <- 0 until t) dut.io.consume(j).poke(consume(j).B)

        val label = s"$tag 拍 $cycle"
        val aByte = dut.io.aScaleByte.peek().litValue
        val bBytes = Vector.tabulate(t)(j => dut.io.scaleBByte(j).peek().litValue)
        holdA.foreach(v => assert(aByte == v, s"$label 暂停期间 scaleA 字节须保持"))
        for (j <- 0 until t) holdB(j).foreach(v => assert(bBytes(j) == v, s"$label 列 $j 暂停期间 scaleB 字节须保持"))
        val aReady = dut.io.aIn.ready.peek().litToBoolean
        val sAReady = dut.io.scaleAIn.get.ready.peek().litToBoolean
        val bReady = dut.io.bIn.ready.peek().litToBoolean
        val sBReady = dut.io.scaleBIn.get.ready.peek().litToBoolean
        assert(sAReady == (mx && aReady), s"$label A/scaleA 原子握手")
        assert(sBReady == (mx && bReady), s"$label B/scaleB 原子握手")
        assert(!aReady || (av.nonEmpty && (!mx || sav.nonEmpty)), s"$label A 握手需源有效")

        for (j <- 0 until t if consume(j)) {
          val c = counts(j)
          val (win, lane) = (c / t, c % t)
          if (j == 0) {
            assert(dut.io.aLane.peek().litValue == lane, s"$label A lane")
            assert(dut.io.aOut.peek().litValue == aWin(win)(lane), s"$label A 字")
            assert(dut.io.aScaleWord.peek().litValue == (if (mx) aScale(win) else BigInt(0)), s"$label A/scaleA 配对")
            assert(lastIn(0) == (lane == t - 1 && win % kTiles == kTiles - 1), s"$label aLast")
            assert(aByte == selByte(if (mx) aScale(win) else BigInt(0), lane, gA, blkA), s"$label scaleA 字节选择")
          }
          val bi = win * t + j
          assert(dut.io.bLane(j).peek().litValue == lane, s"$label 列 $j B lane")
          assert(dut.io.bOut(j).peek().litValue == bWin(bi)(lane), s"$label 列 $j B 字")
          assert(dut.io.scaleBOut(j).peek().litValue == (if (mx) bScale(bi) else BigInt(0)), s"$label 列 $j B/scaleB 配对")
          assert(bBytes(j) == selByte(if (mx) bScale(bi) else BigInt(0), lane, gB, blkB), s"$label 列 $j scaleB 字节选择")
          assert(dut.io.headKt(j).peek().litValue == (win & 0xF), s"$label 列 $j kt")
          counts(j) += 1
        }
        val ready = dut.io.resultsReady.peek().litToBoolean
        holdA = if (validIn(0) && !adv) Some(aByte) else None
        for (j <- 0 until t)
          holdB(j) = if (dut.io.headValid(j).peek().litToBoolean && !consume(j)) Some(bBytes(j)) else None

        dut.clock.step(1)
        cycle += 1
        if (av.nonEmpty && aReady) aSrc.fired()
        if (sav.nonEmpty && sAReady) sASrc.fired()
        if (bv.nonEmpty && bReady) bSrc.fired()
        if (sbv.nonEmpty && sBReady) sBSrc.fired()
        if (clear) for (j <- 0 until t) { tok(j) = false; lst(j) = false; done(j) = false }
        else if (adv) for (j <- (0 until t).reverse) {
          if (consume(j) && lastIn(j)) done(j) = true
          tok(j) = validIn(j)
          lst(j) = lastIn(j)
        }
        ready
      }

      for (nt <- 0 until nTiles) {
        step(clear = true)
        step(start = true)
        var guard = 0
        while (!step()) {
          guard += 1
          assert(guard < 5000, s"$tag tile $nt 卡死")
        }
        for (j <- 0 until t) assert(counts(j) == (nt + 1) * kTiles * t, s"$tag tile $nt 列 $j consume 数")
      }
      assert(aSrc.exhausted && bSrc.exhausted, s"$tag A/B 应全部消费")
      assert(sASrc.exhausted == mx && sBSrc.exhausted == mx, s"$tag scale 流仅 MX 下被消费")
      println(s"[WbOsM1SequencerSpec] standalone $tag PASS")
    }
  }

  it should "pair A with scaleA and B with scaleB atomically in MX mode (T=4)" in {
    runStandalone(mx = true, seed = 61)
  }

  it should "ignore scaleA and scaleB streams outside MX mode (T=4)" in {
    runStandalone(mx = false, seed = 62)
  }

  behavior of "WbOsM1Sequencer scale byte select"

  for ((gA, blkA, gB, blkB, s) <- Seq((4, 16, 4, 16, 71L), (2, 16, 2, 16, 72L), (4, 32, 4, 32, 73L),
                                      (2, 32, 2, 32, 74L), (4, 16, 2, 32, 75L)))
    it should s"select per-lane scaleA/scaleB bytes with gA=$gA blkA=$blkA gB=$gB blkB=$blkB and hold them while paused (T=16)" in {
      runStandalone(mx = true, seed = s, t = 16, scaleW = 32, gA = gA, gB = gB, blkA = blkA, blkB = blkB)
    }

  it should "force both scale bytes to 127 under scaleOne (T=16)" in {
    runStandalone(mx = true, seed = 76, t = 16, scaleW = 32, one = true)
  }
}
