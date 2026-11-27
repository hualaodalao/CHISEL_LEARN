import chisel3._
import chisel3.simulator.PeekPokeAPI
import chisel3.util.log2Ceil

import scala.collection.mutable

class LlcSimTimeout(msg: String) extends RuntimeException(msg)

case class LlcObs(
  arFire:     Boolean,
  awFire:     Boolean,
  wFire:      Boolean,
  rFire:      Boolean,
  rData:      BigInt,
  rId:        BigInt,
  rResp:      Int,
  rLast:      Boolean,
  bFire:      Boolean,
  bId:        BigInt,
  bResp:      Int,
  ctrlAwFire: Boolean,
  ctrlWFire:  Boolean,
  ctrlBFire:  Boolean,
  ctrlArFire: Boolean,
  ctrlRFire:  Boolean,
  ctrlRData:  BigInt)

/** LLC 顶层的周期级驱动：cpu 口做 AXI4 master，mem 口做内存 slave，ctrl 口做 AxiLite4 master。
  *
  * 下游内存按拍地址存储，未写过的拍返回 pat(addr)。下游读数据在 AR 握手后第 rLat 拍给出，
  * 相邻两拍 R 之间插入 rIdle 个空闲拍，写响应在末拍 W 后第 bLat 拍给出，写数据在 B 握手时才提交到内存。
  */
class LlcAxiBench(dut: Llc, cfg: LlcConfig, rLat: Int = 2, rIdle: Int = 0, bLat: Int = 0,
                  initInvalidate: Boolean = false) extends PeekPokeAPI {
  import LlcAxiBench._

  val beatBytes: Int = cfg.bytePerBeat
  private val timeoutCycles = 20000
  private val flushTimeoutCycles = 200000

  private var cycle = 0
  private val mem = mutable.Map[BigInt, BigInt]()
  private val log = mutable.ArrayBuffer[String]()

  private case class DBurst(addr: BigInt, id: BigInt, len: Int, beat: Int = 0)
  private val rPend = mutable.Queue[(Int, DBurst)]()
  private var rCur: Option[(DBurst, Int)] = None
  private val bPend = mutable.Queue[(Int, () => Unit)]()
  private var bCur: Option[() => Unit] = None
  private var awPend: Option[DBurst] = None
  private var wBeats = Vector.empty[(BigInt, BigInt)]
  private val bSeen = mutable.Queue[(BigInt, Int)]()
  private val rSeen = mutable.Queue[BigInt]()
  private val rSeenResp = mutable.Queue[(Int, Boolean)]()

  var memReads = 0
  var memWrites = Vector.empty[BigInt]

  def pat(addr: BigInt): BigInt = 0x5A000000L + (addr >> 5)
  def memBeat(addr: BigInt): BigInt = mem.getOrElse(addr, pat(addr))

  private val c = dut.io.cpu
  private val m = dut.io.mem
  private val k = dut.io.ctrl

  c.ar.valid.poke(false.B)
  c.aw.valid.poke(false.B)
  c.w.valid.poke(false.B)
  c.r.ready.poke(true.B)
  c.b.ready.poke(true.B)
  m.aw.ready.poke(true.B)
  m.w.ready.poke(true.B)
  m.ar.ready.poke(true.B)
  m.b.valid.poke(false.B)
  m.r.valid.poke(false.B)
  k.aw.valid.poke(false.B)
  k.w.valid.poke(false.B)
  k.ar.valid.poke(false.B)
  k.b.ready.poke(true.B)
  k.r.ready.poke(true.B)

  dut.reset.poke(true.B)
  dut.clock.step(2)
  dut.clock.step()
  dut.reset.poke(false.B)
  // 复位后 cache 为空，不需要 invalidate；需要时显式打开
  if (initInvalidate) {
    flush(invalidate = true)
    memReads = 0
    memWrites = Vector.empty
    log.clear()
  }

  private def timeout(what: String, n: Int): Nothing =
    throw new LlcSimTimeout(s"TIMEOUT: $what after $n cycles\n${log.mkString("\n")}")

  private def spin(what: String, max: Int = timeoutCycles)(each: => LlcObs)(done: LlcObs => Boolean): LlcObs = {
    var o: LlcObs = null
    var n = 0
    while ({ o = each; n += 1; if (n > max) timeout(what, n); !done(o) }) {}
    o
  }

  private def mergeBeat(old: BigInt, data: BigInt, strb: BigInt): BigInt = {
    var r = old
    for (i <- 0 until beatBytes) {
      val sh = i * 8
      if (strb.testBit(i)) r = (r & ~(BigInt(0xFF) << sh)) | ((data >> sh & BigInt(0xFF)) << sh)
    }
    r
  }

  /** 推进一拍：先驱动下游输出，采样各通道握手，再步进时钟并更新模型。 */
  def tick(): LlcObs = {
    if (rCur.isEmpty && rPend.nonEmpty && rPend.head._1 <= cycle) {
      rCur = Some((rPend.dequeue()._2, 0))
    }
    m.r.valid.poke(rCur.nonEmpty.B)
    rCur.foreach { case (b, i) =>
      val beat = b.beat + i
      m.r.payload.data.poke(memBeat(b.addr + beat * beatBytes).U)
      m.r.payload.id.get.poke(b.id.U)
      m.r.payload.resp.get.poke(0.U)
      m.r.payload.last.get.poke((beat == b.len).B)
    }

    if (bCur.isEmpty && bPend.nonEmpty && bPend.head._1 <= cycle) {
      bCur = Some(bPend.dequeue()._2)
    }
    m.b.valid.poke(bCur.nonEmpty.B)
    m.b.payload.resp.get.poke(0.U)
    m.b.payload.id.foreach(_.poke(0.U))

    val o = LlcObs(
      arFire = c.ar.valid.peek().litToBoolean && c.ar.ready.peek().litToBoolean,
      awFire = c.aw.valid.peek().litToBoolean && c.aw.ready.peek().litToBoolean,
      wFire = c.w.valid.peek().litToBoolean && c.w.ready.peek().litToBoolean,
      rFire = c.r.valid.peek().litToBoolean && c.r.ready.peek().litToBoolean,
      rData = c.r.payload.data.peek().litValue,
      rId = c.r.payload.id.get.peek().litValue,
      rResp = c.r.payload.resp.get.peek().litValue.toInt,
      rLast = c.r.payload.last.get.peek().litToBoolean,
      bFire = c.b.valid.peek().litToBoolean && c.b.ready.peek().litToBoolean,
      bId = c.b.payload.id.get.peek().litValue,
      bResp = c.b.payload.resp.get.peek().litValue.toInt,
      ctrlAwFire = k.aw.valid.peek().litToBoolean && k.aw.ready.peek().litToBoolean,
      ctrlWFire = k.w.valid.peek().litToBoolean && k.w.ready.peek().litToBoolean,
      ctrlBFire = k.b.valid.peek().litToBoolean && k.b.ready.peek().litToBoolean,
      ctrlArFire = k.ar.valid.peek().litToBoolean && k.ar.ready.peek().litToBoolean,
      ctrlRFire = k.r.valid.peek().litToBoolean && k.r.ready.peek().litToBoolean,
      ctrlRData = k.r.payload.data.peek().litValue)

    val mArFire = m.ar.valid.peek().litToBoolean && m.ar.ready.peek().litToBoolean
    val mAwFire = m.aw.valid.peek().litToBoolean && m.aw.ready.peek().litToBoolean
    val mWFire = m.w.valid.peek().litToBoolean && m.w.ready.peek().litToBoolean
    val mRFire = m.r.valid.peek().litToBoolean && m.r.ready.peek().litToBoolean
    val mBFire = m.b.valid.peek().litToBoolean && m.b.ready.peek().litToBoolean

    if (o.bFire) bSeen.enqueue((o.bId, o.bResp))
    if (o.rFire) { rSeen.enqueue(o.rData); rSeenResp.enqueue((o.rResp, o.rLast)) }

    if (mArFire) {
      val addr = m.ar.payload.addr.peek().litValue
      val len = m.ar.payload.len.get.peek().litValue.toInt
      rPend.enqueue((cycle + rLat, DBurst(addr, m.ar.payload.id.get.peek().litValue, len)))
      memReads += 1
      log += s"[$cycle] mem AR addr=$addr len=$len"
    }
    if (mAwFire) {
      awPend = Some(DBurst(m.aw.payload.addr.peek().litValue, 0, m.aw.payload.len.get.peek().litValue.toInt))
      wBeats = Vector.empty
      log += s"[$cycle] mem AW addr=${awPend.get.addr}"
    }
    if (mWFire) {
      wBeats = wBeats :+ (m.w.payload.data.peek().litValue, m.w.payload.strb.get.peek().litValue)
    }
    if (mBFire) {
      bCur.get.apply()
      bCur = None
      log += s"[$cycle] mem B"
    }
    if (mRFire) {
      rCur match {
        case Some((b, i)) if b.beat + i < b.len =>
          if (rIdle == 0) rCur = Some((b, i + 1))
          else {
            rCur = None
            rPend.enqueue((cycle + rIdle, b.copy(beat = b.beat + i + 1)))
          }
        case _ => rCur = None
      }
    }

    if (mWFire && m.w.payload.last.get.peek().litToBoolean) {
      val burst = awPend.get
      val data = wBeats
      memWrites = memWrites :+ burst.addr
      bPend.enqueue((cycle + bLat, () => {
        for (i <- data.indices) {
          val a = burst.addr + i * beatBytes
          mem(a) = mergeBeat(memBeat(a), data(i)._1, data(i)._2)
        }
      }))
    }

    dut.clock.step()
    cycle += 1
    o
  }

  /** 单笔读：AR 握手后收齐 len+1 拍。 */
  def read(addr: BigInt, len: Int = 0, id: Int = 0): Vector[BigInt] = {
    startRead(addr, len, id)
    readR(len)
  }

  /** 只完成 AR 握手，数据拍由 readR 另行收取。 */
  def startRead(addr: BigInt, len: Int = 0, id: Int = 0,
                burst: UInt = Axi4.burst.INCR, size: Int = log2Ceil(beatBytes)): Unit = {
    c.ar.payload.addr.poke(addr.U)
    c.ar.payload.id.get.poke(id.U)
    c.ar.payload.len.get.poke(len.U)
    c.ar.payload.size.get.poke(size.U)
    c.ar.payload.burst.get.poke(burst)
    c.ar.valid.poke(true.B)
    spin("AR handshake")(tick())(_.arFire)
    c.ar.valid.poke(false.B)
  }

  /** 收齐一笔读的 len+1 拍数据；其间驱动其他事务时已握手的 R 拍照样计入。 */
  def readR(len: Int = 0): Vector[BigInt] = readRFull(len).map(_._1)

  /** 同 readR，另带回每拍的 (resp, last)。 */
  def readRFull(len: Int = 0): Vector[(BigInt, Int, Boolean)] = {
    val out = Vector.newBuilder[(BigInt, Int, Boolean)]
    var n = 0
    while (rSeen.size <= len) {
      tick()
      n += 1
      if (n > timeoutCycles) timeout("R beat", n)
    }
    for (_ <- 0 to len) {
      val m = rSeenResp.dequeue()
      out += ((rSeen.dequeue(), m._1, m._2))
    }
    out.result()
  }

  /** 单笔写的地址与全部数据拍握手完成即返回，不等待 B。 */
  def startWrite(addr: BigInt, beats: Vector[(BigInt, BigInt)], id: Int = 0,
                 burst: UInt = Axi4.burst.INCR, size: Int = log2Ceil(beatBytes)): Unit = {
    c.aw.payload.addr.poke(addr.U)
    c.aw.payload.id.get.poke(id.U)
    c.aw.payload.len.get.poke((beats.size - 1).U)
    c.aw.payload.size.get.poke(size.U)
    c.aw.payload.burst.get.poke(burst)
    c.aw.valid.poke(true.B)
    spin("AW handshake")(tick())(_.awFire)
    c.aw.valid.poke(false.B)
    writeBeats(beats)
  }

  /** 只完成 AW 握手，数据拍由 writeBeats 另行发出。 */
  def aw(addr: BigInt, beats: Int, id: Int = 0,
          burst: UInt = Axi4.burst.INCR, size: Int = log2Ceil(beatBytes)): Unit = {
    pokeAw(addr, beats, id, burst, size)
    c.aw.valid.poke(true.B)
    spin("AW handshake")(tick())(_.awFire)
    c.aw.valid.poke(false.B)
  }

  /** AW 与 W 首拍在同一拍握手；余下的拍由 writeBeats 发出。 */
  def awWithBeat(addr: BigInt, beats: Int, first: (BigInt, BigInt), id: Int = 0,
                 burst: UInt = Axi4.burst.INCR, size: Int = log2Ceil(beatBytes)): Unit = {
    pokeAw(addr, beats, id, burst, size)
    c.aw.valid.poke(true.B)
    c.w.payload.data.poke(first._1.U)
    c.w.payload.strb.get.poke(first._2.U)
    c.w.payload.last.get.poke((beats == 1).B)
    c.w.valid.poke(true.B)
    spin("AW and first W beat in one cycle")(tick()) { o =>
      if (o.awFire) c.aw.valid.poke(false.B)
      if (o.wFire) c.w.valid.poke(false.B)
      o.awFire && o.wFire
    }
  }

  private def pokeAw(addr: BigInt, beats: Int, id: Int, burst: UInt, size: Int): Unit = {
    c.aw.payload.addr.poke(addr.U)
    c.aw.payload.id.get.poke(id.U)
    c.aw.payload.len.get.poke((beats - 1).U)
    c.aw.payload.size.get.poke(size.U)
    c.aw.payload.burst.get.poke(burst)
  }

  /** 逐拍发出 W 数据拍。 */
  def writeBeats(beats: Vector[(BigInt, BigInt)]): Unit = {
    for (i <- beats.indices) {
      val (d, s) = beats(i)
      c.w.payload.data.poke(d.U)
      c.w.payload.strb.get.poke(s.U)
      c.w.payload.last.get.poke((i == beats.size - 1).B)
      c.w.valid.poke(true.B)
      spin("W beat")(tick())(_.wFire)
      c.w.valid.poke(false.B)
    }
  }

  /** 取下一次 B，返回 (id, resp)；此前任何时刻已握手的 B 先被返回。 */
  def waitB(): (BigInt, Int) = {
    var n = 0
    while (bSeen.isEmpty) {
      tick()
      n += 1
      if (n > timeoutCycles) timeout("B response", n)
    }
    bSeen.dequeue()
  }

  /** 断言此后不再出现任何 B 响应。 */
  def waitBTimeout(): Unit = {
    for (_ <- 0 until 200) tick()
    if (bSeen.nonEmpty || c.b.valid.peek().litToBoolean) throw new LlcSimTimeout("unexpected B response")
  }

  def bReady(en: Boolean): Unit = c.b.ready.poke(en.B)

  /** 下游 AR/AW/B 的握手记录，按时间顺序。 */
  def downstreamLog: Seq[String] = log.toSeq
  /** 仿真至今的拍数。 */
  def cycles: Int = cycle

  def write(addr: BigInt, beats: Vector[(BigInt, BigInt)], id: Int = 0): (BigInt, Int) = {
    startWrite(addr, beats, id)
    waitB()
  }

  def ctrlWrite(addr: Int, data: BigInt): Unit = {
    k.aw.payload.addr.poke(addr.U)
    k.aw.payload.prot.poke(0.U)
    k.w.payload.data.poke(data.U)
    k.w.payload.strb.poke(15.U)
    k.aw.valid.poke(true.B)
    k.w.valid.poke(true.B)
    var awDone = false
    var wDone = false
    spin("ctrl AW/W")(tick()) { o =>
      if (o.ctrlAwFire) { k.aw.valid.poke(false.B); awDone = true }
      if (o.ctrlWFire) { k.w.valid.poke(false.B); wDone = true }
      awDone && wDone
    }
    spin("ctrl B")(tick())(_.ctrlBFire)
  }

  def ctrlRead(addr: Int): BigInt = {
    k.ar.payload.addr.poke(addr.U)
    k.ar.payload.prot.poke(0.U)
    k.ar.valid.poke(true.B)
    spin("ctrl AR")(tick())(_.ctrlArFire)
    k.ar.valid.poke(false.B)
    spin("ctrl R")(tick())(_.ctrlRFire).ctrlRData
  }

  /** 触发 flush 或 invalidate 并等待 STATUS.flushDone。 */
  def flush(invalidate: Boolean = false): Unit = {
    startFlush(invalidate)
    waitFlushDone()
  }

  /** 触发 flush 或 invalidate 后即返回，不等待完成。 */
  def startFlush(invalidate: Boolean = false): Unit = {
    ctrlWrite(REG_CTRL, if (invalidate) 4 else 2)
    ctrlWrite(REG_CTRL, 0)
  }

  def waitFlushDone(): Unit = {
    var n = 0
    while (!ctrlRead(REG_STATUS).testBit(STATUS_FLUSH_DONE)) {
      for (_ <- 0 until 8) tick()
      n += 1
      if (n > flushTimeoutCycles) timeout("flushDone", n)
    }
  }
}

object LlcAxiBench {
  val REG_CTRL = 0x00
  val REG_STATUS = 0x04
  val STATUS_FLUSH_DONE = 5
}
