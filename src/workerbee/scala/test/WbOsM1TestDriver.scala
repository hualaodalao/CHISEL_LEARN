import chisel3._

import scala.util.Random

/** OS-M1 首行驱动端口面：行链 harness 与阵列 harness 共用。 */
class WbOsM1RowIo(T: Int) extends Bundle {
  val aIn = slave(Stream(UInt((T * 16).W)))
  val bIn = slave(Stream(UInt((T * 16).W)))
  val flush = Input(Bool())
  val start = Input(Bool())
  val kTiles = Input(UInt(16.W))
  val loadH = Input(Bool())
  val fmt = Input(DataFormat())
  val clear = Input(Bool())
  val seedEn = Input(Bool())
  val seed = Input(Vec(T, UInt(48.W)))
  val advance = Output(Bool())
  val aTokenValid = Output(Bool())
  val resultsReady = Output(Bool())
  val phase = Output(WbOsM1Phase())
  val headValid = Output(Vec(T, Bool()))
  val consume = Output(Vec(T, Bool()))
  val done = Output(Vec(T, Bool()))
  val validIn = Output(Vec(T, Bool()))
  val psum = Output(Vec(T, UInt(48.W)))
}

abstract class WbOsM1RowDut(val T: Int) extends Module {
  val io: WbOsM1RowIo
}

/** 有限停顿源：valid 一旦拉起保持到 fire。 */
class WbOsM1Src(words: IndexedSeq[BigInt], rng: Random, pStall: Double, readyAt: Int) {
  var idx = 0
  private var holding = false
  def offer(cycle: Int): Option[BigInt] =
    if (idx >= words.size) None
    else if (holding || (cycle >= readyAt && rng.nextDouble() >= pStall)) {
      holding = true
      Some(words(idx))
    } else None
  def fired(): Unit = { idx += 1; holding = false }
  def exhausted: Boolean = idx >= words.size
}

object WbOsM1Src {
  def empty = new WbOsM1Src(Vector.empty, new Random(0), 0.0, 0)
  def pack(words: Seq[BigInt], w: Int): BigInt =
    words.zipWithIndex.foldLeft(BigInt(0)) { case (acc, (v, i)) => acc | (v << (i * w)) }
}

case class WbOsM1Task(fmt: DataFormat.Type, kTiles: Int, nTiles: Int, seed: Long,
                      pA: Double = 0.0, pB: Double = 0.0, aDelay: Int = 0, bDelay: Int = 0,
                      primeWait: Int = 40, exact: Boolean = false, abortAfter: Int = -1) {
  def tag: String = s"$fmt k=$kTiles n=$nTiles seed=$seed pA=$pA pB=$pB aD=$aDelay bD=$bDelay"
}

case class WbOsM1Obs(advance: Boolean, tokenValid: Boolean, resultsReady: Boolean, phase: BigInt,
                     headValid: Vector[Boolean], consume: Vector[Boolean], done: Vector[Boolean],
                     validIn: Vector[Boolean])

/** 逐拍按 §5.1/§5.3 推进公式检查 advance 与 consume，逐 N tile 与张量金锚比对。 */
class WbOsM1RowRunner(dut: WbOsM1RowDut, name: String) extends chisel3.simulator.PeekPokeAPI {
  import WbOsM1Src.pack

  private val mask48 = (BigInt(1) << 48) - 1
  private val sRun = WbOsM1Phase.sRun.litValue
  private val sTail = WbOsM1Phase.sTail.litValue

  val t: Int = dut.T
  var cycle = 0
  /** 每拍在时钟沿前调用，供 harness 追加观测断言。 */
  var perCycle: () => Unit = () => ()

  dut.io.aIn.valid.poke(false.B)
  dut.io.aIn.payload.poke(0.U)
  dut.io.bIn.valid.poke(false.B)
  dut.io.bIn.payload.poke(0.U)
  dut.io.flush.poke(false.B)
  dut.io.start.poke(false.B)
  dut.io.kTiles.poke(0.U)
  dut.io.loadH.poke(false.B)
  dut.io.fmt.poke(DataFormat.INT8)
  dut.io.clear.poke(false.B)
  dut.io.seedEn.poke(false.B)
  dut.io.seed.foreach(_.poke(0.U))
  dut.reset.poke(true.B)
  dut.clock.step(3)
  dut.reset.poke(false.B)
  dut.clock.step(1)

  def step(a: WbOsM1Src, b: WbOsM1Src, fmt: DataFormat.Type, flush: Boolean = false, clear: Boolean = false,
           loadH: Boolean = false, seeds: Option[Seq[BigInt]] = None, start: Boolean = false,
           kTiles: Int = 0): WbOsM1Obs = {
    val av = a.offer(cycle)
    val bv = b.offer(cycle)
    dut.io.aIn.valid.poke(av.nonEmpty.B)
    dut.io.aIn.payload.poke(av.getOrElse(BigInt(0)).U)
    dut.io.bIn.valid.poke(bv.nonEmpty.B)
    dut.io.bIn.payload.poke(bv.getOrElse(BigInt(0)).U)
    dut.io.flush.poke(flush.B)
    dut.io.clear.poke(clear.B)
    dut.io.loadH.poke(loadH.B)
    dut.io.fmt.poke(fmt)
    dut.io.seedEn.poke(seeds.nonEmpty.B)
    for (j <- 0 until t) dut.io.seed(j).poke(seeds.map(_(j)).getOrElse(BigInt(0)).U)
    dut.io.start.poke(start.B)
    dut.io.kTiles.poke(kTiles.U)

    val o = WbOsM1Obs(
      dut.io.advance.peek().litToBoolean,
      dut.io.aTokenValid.peek().litToBoolean,
      dut.io.resultsReady.peek().litToBoolean,
      dut.io.phase.peek().litValue,
      Vector.tabulate(t)(j => dut.io.headValid(j).peek().litToBoolean),
      Vector.tabulate(t)(j => dut.io.consume(j).peek().litToBoolean),
      Vector.tabulate(t)(j => dut.io.done(j).peek().litToBoolean),
      Vector.tabulate(t)(j => dut.io.validIn(j).peek().litToBoolean))

    val runAdv = o.phase == sRun && o.tokenValid && o.headValid.forall(identity)
    val tailAdv = o.phase == sTail && !o.done.forall(identity) &&
      (0 until t).forall(j => o.done(j) || o.headValid(j))
    assert(o.advance == (!flush && (runAdv || tailAdv)), s"拍 $cycle advance 与推进公式不符")
    for (j <- 0 until t)
      assert(o.consume(j) == (o.advance && o.validIn(j)), s"拍 $cycle 列 $j consume")
    perCycle()

    val aFire = av.nonEmpty && dut.io.aIn.ready.peek().litToBoolean
    val bFire = bv.nonEmpty && dut.io.bIn.ready.peek().litToBoolean
    dut.clock.step(1)
    cycle += 1
    if (aFire) a.fired()
    if (bFire) b.fired()
    o
  }

  def runTask(task: WbOsM1Task): Unit = {
    val rng = new Random(task.seed)
    val eb = if (task.fmt == DataFormat.INT8) 8 else 4
    val g = 16 / eb
    val lTok = task.kTiles * t
    val kElems = lTok * g
    val nCols = task.nTiles * t
    val lo = -(1 << (eb - 1))
    def elem(): Int = lo + rng.nextInt(1 << eb)
    val aT = Vector.fill(task.nTiles, kElems)(elem())
    val bT = Vector.fill(nCols, kElems)(elem())
    val seeds = Vector.fill(nCols)(BigInt(48, rng))
    val emask = (1 << eb) - 1
    def word(v: Int => Int, tok: Int): BigInt =
      pack((0 until g).map(e => BigInt(v(tok * g + e) & emask)), eb)
    val aWords = for (nt <- 0 until task.nTiles; kt <- 0 until task.kTiles)
      yield pack((0 until t).map(i => word(aT(nt)(_), kt * t + i)), 16)
    val bWords = for (nt <- 0 until task.nTiles; kt <- 0 until task.kTiles; j <- 0 until t)
      yield pack((0 until t).map(i => word(bT(nt * t + j)(_), kt * t + i)), 16)
    val golden = Vector.tabulate(nCols) { n =>
      val nt = n / t
      (seeds(n) + (0 until kElems).map(k => BigInt(aT(nt)(k)) * BigInt(bT(n)(k))).sum) & mask48
    }

    val idle = WbOsM1Src.empty
    step(idle, idle, task.fmt, flush = true, clear = true)
    // fmt 在阵列内逐列寄存传播，loadH 窗口须覆盖整条横链深度 T
    for (_ <- 0 until t) step(idle, idle, task.fmt, clear = true, loadH = true)
    val aSrc = new WbOsM1Src(aWords, new Random(task.seed + 1), task.pA, cycle + task.aDelay)
    val bSrc = new WbOsM1Src(bWords, new Random(task.seed + 2), task.pB, cycle + task.bDelay)

    for (nt <- 0 until task.nTiles) {
      if (nt == 0) for (_ <- 0 until task.primeWait) step(aSrc, bSrc, task.fmt)
      step(aSrc, bSrc, task.fmt, clear = true)
      step(aSrc, bSrc, task.fmt, seeds = Some(seeds.slice(nt * t, nt * t + t)))
      step(aSrc, bSrc, task.fmt, start = true, kTiles = task.kTiles)

      val label = s"[${task.tag}] tile $nt"
      val counts = Array.fill(t)(0)
      val firstConsumeAdv = Array.fill(t)(-1)
      var advances = 0
      var firstAdv = -1
      var finished = false
      val limit = cycle + 20000
      while (!finished) {
        if (task.abortAfter >= 0 && firstAdv >= 0 && cycle - firstAdv >= task.abortAfter) return
        val now = cycle
        val o = step(aSrc, bSrc, task.fmt)
        if (o.resultsReady) {
          assert(!o.advance, s"$label resultsReady 拍不得推进")
          assert(o.done.forall(identity), s"$label 全列 done")
          for (j <- 0 until t) {
            assert(counts(j) == lTok, s"$label 列 $j consume=${counts(j)} 期望 K/g=$lTok")
            assert(!o.validIn(j), s"$label 列 $j 仍有在途 A token")
            assert(firstConsumeAdv(j) == j,
              s"$label 列 $j 首次消费应在第 $j 次推进（列 0 起算），实测 ${firstConsumeAdv(j)}")
          }
          assert(advances == lTok + t - 1, s"$label 推进拍=$advances 期望 L+T-1=${lTok + t - 1}")
          if (task.exact)
            assert(now - firstAdv == lTok + t - 1,
              s"$label 无停顿时首推进到 resultsReady 应为 L+T-1，实测 ${now - firstAdv}")
          for (j <- 0 until t) {
            val got = dut.io.psum(j).peek().litValue
            assert(got == golden(nt * t + j),
              s"$label 列 $j psum=0x${got.toString(16)} 金锚=0x${golden(nt * t + j).toString(16)}")
          }
          finished = true
        } else {
          for (j <- 0 until t if o.consume(j)) {
            if (firstConsumeAdv(j) < 0) firstConsumeAdv(j) = advances
            counts(j) += 1
          }
          if (o.advance) {
            if (firstAdv < 0) firstAdv = now
            advances += 1
          }
          assert(cycle < limit, s"$label 卡死：20000 拍内未产生 resultsReady")
        }
      }
    }
    assert(aSrc.exhausted && bSrc.exhausted, s"[${task.tag}] 上游 A/B 应恰好被全部消费")
    println(s"[$name] PASS ${task.tag}")
  }
}
