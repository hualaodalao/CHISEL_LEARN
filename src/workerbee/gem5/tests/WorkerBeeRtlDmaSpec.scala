import chisel3._
import chisel3.util.log2Up
import chisel3.util.experimental.BoringUtils
import scala.collection.mutable
import scala.language.reflectiveCalls

class WorkerBeeRtlDmaSpec extends RtlEdgeSuite {
  private class ReadDmaHarness(c: WorkerBeeConfig) extends Module {
    val dma = Module(new WorkerBeeDmaRdOnly(c, bufWidth = c.aExtW, bufDepth = c.aBufferDepth))
    val io = IO(dma.io.cloneType)
    io <> dma.io

    // Read-only hierarchy taps; no new drivers, force operations, or changes to the DMA.
    val observed = IO(new Bundle {
      val ostCredit = Output(UInt(log2Up(c.aBufferDepth + 1).W))
      val inflCnt = Output(UInt(log2Up(c.aBufferDepth + 2).W))
      val gPhase = Output(UInt(2.W))
      val lineCounter = Output(UInt(16.W))
      val lineTarget = Output(UInt(16.W))
      val curAddr = Output(UInt(c.addrWidth.W))
      val rowStep = Output(UInt(c.addrWidth.W))
      val kCnt = Output(UInt(c.mnkWidth.W))
      val nCnt = Output(UInt(c.mnkWidth.W))
      val state = Output(UInt(2.W))
    })
    observed.ostCredit := BoringUtils.tapAndRead(dma.ostCredit)
    observed.inflCnt := BoringUtils.tapAndRead(dma.inflCnt)
    observed.gPhase := BoringUtils.tapAndRead(dma.gPhase)
    observed.lineCounter := BoringUtils.tapAndRead(dma.lineCounter)
    observed.lineTarget := BoringUtils.tapAndRead(dma.lineTarget)
    observed.curAddr := BoringUtils.tapAndRead(dma.curAddr)
    observed.rowStep := BoringUtils.tapAndRead(dma.rowStep)
    observed.kCnt := BoringUtils.tapAndRead(dma.kCnt)
    observed.nCnt := BoringUtils.tapAndRead(dma.nCnt)
    observed.state := BoringUtils.tapAndRead(dma.state)
  }

  private val portBytes = cfg.aExtW / 8
  private val halfBytes = portBytes / 2
  private def bit(value: Boolean): BigInt = if (value) BigInt(1) else BigInt(0)
  private def packBytes(bytes: Seq[Int]): BigInt =
    bytes.zipWithIndex.foldLeft(BigInt(0)) { case (word, (byte, lane)) =>
      word | (BigInt(byte & 0xff) << (8 * lane))
    }
  // Byte at address A is A mod 256; lane zero is the lowest-addressed byte.
  private def memory(address: BigInt, count: Int = portBytes): BigInt =
    packBytes((0 until count).map(i => ((address + i) & 0xff).toInt))

  private class Driver(val dut: ReadDmaHarness, caseId: String) {
    private var cycle = 0 // Monotonic even across the reset-separated gather scenarios.
    private var base = BigInt(0)
    private var rowBytes = 0
    private var rows = 0
    private var stretched = false
    private var issued = 0
    private var popped = 0
    private var pushed = 0
    private val pending = mutable.Queue.empty[BigInt]
    private val resident = mutable.Queue.empty[Int]
    private val reads = mutable.ArrayBuffer.empty[BigInt]

    def issuedCount: Int = issued
    def credit: Int = cfg.aBufferDepth - issued + popped
    def buffered: Int = resident.size
    def outstanding: Int = pending.size
    def acceptedAddresses: Seq[BigInt] = reads.toVector
    def logicalAddress(index: Int): BigInt = base + BigInt(index) * rowBytes
    def logicalWord(index: Int): BigInt = memory(logicalAddress(index))
    def physicalAddress(index: Int): BigInt =
      (logicalAddress(index) / portBytes) * portBytes

    // Stretch conversion is external to the DMA, so this token is not a conversion oracle.
    private def producerBeat(index: Int): BigInt =
      packBytes((0 until portBytes).map(lane => 0x80 + index * 7 + lane))

    private def snapshot(): Map[String, BigInt] = {
      val signals: Seq[(String, Data)] = Seq(
        "reset" -> dut.reset, "start" -> dut.io.start, "isA" -> dut.io.isA,
        "req_valid" -> dut.io.dmaExtRdIF.req.valid,
        "req_ready" -> dut.io.dmaExtRdIF.req.ready,
        "req_addr" -> dut.io.dmaExtRdIF.req.payload.addr,
        "rsp_valid" -> dut.io.dmaExtRdIF.rsp.valid,
        "rsp_ready" -> dut.io.dmaExtRdIF.rsp.ready,
        "rsp_data" -> dut.io.dmaExtRdIF.rsp.payload.data,
        "rsp_err" -> dut.io.dmaExtRdIF.rsp.payload.err,
        "push_valid" -> dut.io.bufPush.valid, "push_ready" -> dut.io.bufPush.ready,
        "push_data" -> dut.io.bufPush.payload, "pop" -> dut.io.bufPopFire,
        "ostCredit" -> dut.observed.ostCredit, "inflCnt" -> dut.observed.inflCnt,
        "gPhase" -> dut.observed.gPhase, "lineCounter" -> dut.observed.lineCounter,
        "lineTarget" -> dut.observed.lineTarget, "curAddr" -> dut.observed.curAddr,
        "rowStep" -> dut.observed.rowStep, "kCnt" -> dut.observed.kCnt,
        "nCnt" -> dut.observed.nCnt, "state" -> dut.observed.state,
        "busy" -> dut.io.busy, "done" -> dut.io.done, "err" -> dut.io.err
      ) ++ dut.io.stretch.toSeq.map(s => "stretch" -> s) ++
        dut.io.stretchSrc.toSeq.map(s => "stretchSrc" -> s) ++
        dut.io.stretchBeat.toSeq.map(s => "stretchBeat" -> s)
      sample(caseId, cycle, signals: _*)
    }

    private def check(values: Map[String, BigInt], label: String,
                      expected: (String, BigInt)*): Unit = {
      expected.foreach { case (name, value) =>
        withClue(s"$caseId cycle=$cycle $label $name: ") {
          values(name) should be(value)
        }
      }
    }

    private def advance(): Unit = { dut.clock.step(); cycle += 1 }

    def initialize(address: BigInt, stride: Int, count: Int, stretch: Boolean = false): Unit = {
      portBytes should be(32)
      cfg.aBufferDepth should be(64)
      cfg.hasMx4 should be(true) // Preserve the complete default configuration.
      base = address; rowBytes = stride; rows = count; stretched = stretch
      issued = 0; popped = 0; pushed = 0
      pending.clear(); resident.clear(); reads.clear()

      dut.io.isA.poke(true.B)
      dut.io.start.poke(false.B)
      dut.io.dmaExtRdIF.req.ready.poke(false.B)
      dut.io.dmaExtRdIF.rsp.valid.poke(false.B)
      dut.io.dmaExtRdIF.rsp.payload.data.poke(0.U)
      dut.io.dmaExtRdIF.rsp.payload.err.poke(false.B)
      dut.io.bufPush.ready.poke(true.B)
      dut.io.bufPopFire.poke(false.B)
      dut.io.stretch.get.poke(stretch.B)
      dut.io.stretchBeat.get.poke(0.U)
      dut.io.reg_baseAddr.poke(address.U)
      dut.io.reg_kTile.poke(1.U)
      dut.io.reg_mTile.poke(1.U)
      dut.io.reg_nTile.poke(1.U)
      dut.io.reg_rowOffset.poke(stride.U)
      dut.io.reg_colTileOffset.poke(portBytes.U)
      val r = dut.io.regFile
      r.m.poke(count.U); r.n.poke((cfg.totalN * 2).U); r.k.poke((cfg.totalN * 2).U)
      r.aAddr.poke(address.U); r.bAddr.poke(0x2000.U); r.cAddr.poke(0x4000.U)
      r.aAddrMode.poke(AddrMode.MAT); r.bAddrMode.poke(AddrMode.MAT); r.cAddrMode.poke(AddrMode.MAT)
      r.aMxBlockSize.poke(MXBlockSize.M32); r.bMxBlockSize.poke(MXBlockSize.M32)
      r.cMxBlockSize.poke(MXBlockSize.M32)
      r.loadWMode.poke(false.B)
      r.aFmt.poke(if (stretch) DataFormat.MXE2M1 else DataFormat.INT8)
      r.bFmt.poke(DataFormat.INT8); r.cFmt.poke(DataFormat.INT8)
      r.rnd.poke(RoundingMode.RNE)
      r.aScaleAddr.poke(0.U); r.bScaleAddr.poke(0.U); r.cScaleAddr.poke(0.U)
      r.warmup.poke(false.B); r.warmdown.poke(false.B)
      r.biasOn.poke(false.B); r.biasAddr.poke(0.U)
      r.requantOn.poke(false.B); r.reluOn.poke(false.B)
      r.rqMBase.poke(0.U); r.rqShift.poke(0.U); r.rqZp.poke(0.S)

      dut.reset.poke(true.B)
      snapshot(); advance()
      val resetFrame = snapshot()
      check(resetFrame, "reset", "state" -> BigInt(0), "ostCredit" -> BigInt(0),
        "inflCnt" -> BigInt(0), "gPhase" -> BigInt(0), "lineCounter" -> BigInt(0),
        "curAddr" -> BigInt(0), "req_valid" -> BigInt(0), "push_valid" -> BigInt(0))
      advance()
      dut.reset.poke(false.B)
      val idleFrame = snapshot()
      check(idleFrame, "first idle", "state" -> BigInt(0), "ostCredit" -> BigInt(0),
        "req_valid" -> BigInt(0), "busy" -> BigInt(0), "done" -> BigInt(0))
      advance() // The idle edge initializes all 64 credits.
      dut.io.start.poke(true.B)
      val startFrame = snapshot()
      check(startFrame, "start", "state" -> BigInt(0), "ostCredit" -> BigInt(cfg.aBufferDepth),
        "req_valid" -> BigInt(0), "inflCnt" -> BigInt(0), "gPhase" -> BigInt(0),
        "lineCounter" -> BigInt(0), "busy" -> BigInt(0), "err" -> BigInt(0))
      advance()
      dut.io.start.poke(false.B)
    }

    def tick(label: String, req: Option[BigInt] = None, ready: Boolean = true,
             response: Boolean = false, logical: Boolean = false,
             pushIndex: Option[Int] = None, pop: Boolean = false,
             phase: Int = 0, state: Int = 1): Map[String, BigInt] = {
      val reqFire = req.nonEmpty && ready
      require(!logical || reqFire, s"$label: logical completion without a physical request")
      require(!pop || resident.nonEmpty, s"$label: cannot pop an empty sink")
      require(pushIndex.isEmpty || response, s"$label: push requires a response")
      require(!response || pending.nonEmpty || reqFire, s"$label: unsolicited response")
      require(!logical || issued < rows, s"$label: too many logical requests")
      pushIndex.foreach(i => require(i == pushed, s"$label: out-of-order logical result"))
      val rspAddress = if (!response) BigInt(0) else if (pending.nonEmpty) pending.front else req.get
      val rspData = if (response) memory(rspAddress) else BigInt(0)
      val pushData = pushIndex.map(i => if (stretched) producerBeat(i) else logicalWord(i))
      dut.io.dmaExtRdIF.req.ready.poke(ready.B)
      dut.io.dmaExtRdIF.rsp.valid.poke(response.B)
      dut.io.dmaExtRdIF.rsp.payload.data.poke(rspData.U)
      dut.io.dmaExtRdIF.rsp.payload.err.poke(false.B)
      dut.io.bufPush.ready.poke(true.B)
      dut.io.bufPopFire.poke(pop.B)
      dut.io.stretchBeat.get.poke(pushData.getOrElse(BigInt(0)).U)

      val values = snapshot()
      val expectedCur = if (state == 3 || state == 0) base else logicalAddress(issued)
      val expectedLine = if (issued == rows) 0 else issued
      check(values, label,
        "reset" -> BigInt(0), "start" -> BigInt(0), "isA" -> BigInt(1),
        "req_valid" -> bit(req.nonEmpty), "req_ready" -> bit(ready),
        "rsp_valid" -> bit(response), "rsp_ready" -> BigInt(1), "rsp_data" -> rspData,
        "rsp_err" -> BigInt(0), "push_valid" -> bit(pushIndex.nonEmpty),
        "push_ready" -> BigInt(1), "pop" -> bit(pop),
        "ostCredit" -> BigInt(credit), "inflCnt" -> BigInt(pending.size),
        "gPhase" -> BigInt(phase), "lineCounter" -> BigInt(expectedLine),
        "lineTarget" -> BigInt(rows), "curAddr" -> expectedCur,
        "rowStep" -> BigInt(rowBytes), "kCnt" -> BigInt(0), "nCnt" -> BigInt(0),
        "state" -> BigInt(state), "busy" -> bit(state != 0),
        "done" -> bit(state == 3 && credit == cfg.aBufferDepth), "err" -> BigInt(0),
        "stretch" -> bit(stretched))
      req.foreach(a => check(values, label, "req_addr" -> a))
      pushData.foreach(data => check(values, label, "push_data" -> data))
      if (stretched) pushIndex.foreach { i =>
        check(values, label, "stretchSrc" -> memory(logicalAddress(i), halfBytes),
          "stretchBeat" -> producerBeat(i))
      }

      // Independent event ledger. No recurrence uses a value sampled from the DUT.
      if (reqFire) { pending.enqueue(req.get); reads += req.get }
      if (response) pending.dequeue() // Oldest response, including empty-queue same-edge bypass.
      if (logical) issued += 1
      if (pop) { resident.dequeue(); popped += 1 }
      pushIndex.foreach { i => resident.enqueue(i); pushed += 1 }
      withClue(s"$caseId $label legal external storage: ") {
        credit should be >= 0
        credit should be <= cfg.aBufferDepth
        resident.size should be <= cfg.aBufferDepth
        pushed should be <= issued
      }
      advance()
      values
    }

    def finish(): Unit = {
      issued should be(rows)
      pushed should be(rows)
      pending.size should be(0)
      tick("tile boundary", state = 2)
      while (resident.nonEmpty) tick("consume completed item", pop = true, state = 3)
      tick("done only after all logical items consumed", state = 3)
      tick("return to idle", state = 0)
      popped should be(rows)
      credit should be(cfg.aBufferDepth)
      outstanding should be(0)
      buffered should be(0)
    }
  }

  behavior of "WorkerBee P0 read DMA RTL edges"

  it should "dma_credit" in {
    simulate(new ReadDmaHarness(cfg)) { dut =>
      val s = new Driver(dut, "dma_credit")
      s.initialize(0x1000, stride = 32, count = 128)
      for (i <- 0 until 3)
        s.tick(s"ready hold $i", req = Some(s.logicalAddress(0)), ready = false)
      s.tick("prime a real resident item", req = Some(s.logicalAddress(0)),
        response = true, logical = true, pushIndex = Some(0))
      val simultaneous = s.tick("request and old-item pop cancel credit updates",
        req = Some(s.logicalAddress(1)), response = true, logical = true,
        pushIndex = Some(1), pop = true)
      simultaneous("ostCredit") should be(BigInt(63))
      s.credit should be(63)
      s.tick("pop remaining priming item without a request", req = Some(s.logicalAddress(2)),
        ready = false, pop = true)
      s.credit should be(64)
      s.buffered should be(0)

      // Exactly 64 accepted requests, each with its own same-edge response and no pop.
      for (index <- 2 until 66)
        s.tick(s"64-credit burst logical $index", req = Some(s.logicalAddress(index)),
          response = true, logical = true, pushIndex = Some(index))
      s.issuedCount should be(66)
      s.credit should be(0)
      s.buffered should be(64)
      s.outstanding should be(0)
      for (i <- 0 until 2) s.tick(s"exhausted stall $i")
      val zeroPop = s.tick("zero-credit pop edge must still suppress request", pop = true)
      zeroPop("ostCredit") should be(BigInt(0))
      zeroPop("req_valid") should be(BigInt(0))
      zeroPop("lineCounter") should be(BigInt(66))
      s.credit should be(1)
      s.tick("next edge restores request; delay its response", req = Some(s.logicalAddress(66)),
        logical = true)
      s.credit should be(0)
      s.outstanding should be(1)
      for (i <- 0 until 2) s.tick(s"wait for delayed response $i")
      s.tick("delayed response pushes but returns no credit", response = true, pushIndex = Some(66))
      s.credit should be(0)
      s.outstanding should be(0)
      s.tick("response did not reopen request gate")

      s.tick("free one slot for the remaining pipeline", pop = true)
      for (index <- 67 until 128)
        s.tick(s"remaining logical $index", req = Some(s.logicalAddress(index)),
          response = true, logical = true, pushIndex = Some(index), pop = true)
      s.acceptedAddresses should be((0 until 128).map(i => BigInt(0x1000) + i * 32))
      s.finish()
    }
  }

  it should "dma_gather" in {
    simulate(new ReadDmaHarness(cfg)) { dut =>
      val s = new Driver(dut, "dma_gather")
      val expected03Through22 = packBytes(3 until 35)
      // Test each shortcut independently as well as the fully delayed and fully immediate pairs.
      for ((loImmediate, hiImmediate) <- Seq((false, false), (true, false), (false, true), (true, true))) {
        val tag = s"loSame=$loImmediate hiSame=$hiImmediate"
        s.initialize(0x1003, stride = 32, count = 2)
        for (i <- 0 until 2)
          s.tick(s"$tag lo hold $i", req = Some(BigInt(0x1000)), ready = false)
        s.tick(s"$tag lo request reserves without consuming credit", req = Some(BigInt(0x1000)),
          response = loImmediate)
        s.credit should be(64)
        s.issuedCount should be(0)
        s.buffered should be(0)
        if (!loImmediate) {
          for (i <- 0 until 2) s.tick(s"$tag wait lo $i", phase = 1)
          s.tick(s"$tag separate lo response is not a push", response = true, phase = 1)
        }
        for (i <- 0 until 2)
          s.tick(s"$tag hi hold $i", req = Some(BigInt(0x1020)), ready = false, phase = 2)
        val hiRequest = s.tick(s"$tag hi request consumes exactly one logical credit",
          req = Some(BigInt(0x1020)), response = hiImmediate, logical = true,
          pushIndex = if (hiImmediate) Some(0) else None, phase = 2)
        s.credit should be(63)
        s.issuedCount should be(1)
        val result = if (hiImmediate) hiRequest else {
          for (i <- 0 until 2) s.tick(s"$tag wait hi $i", phase = 3)
          s.tick(s"$tag separate hi response merges and pushes", response = true,
            pushIndex = Some(0), phase = 3)
        }
        result("push_valid") should be(BigInt(1))
        result("push_data") should be(expected03Through22)
        s.credit should be(63) // Neither lo nor hi responses return credit.
        s.outstanding should be(0)
        s.tick(s"$tag back in phase zero", req = Some(BigInt(0x1020)), ready = false)
        s.tick(s"$tag second item immediate lo", req = Some(BigInt(0x1020)), response = true)
        s.tick(s"$tag second item immediate hi", req = Some(BigInt(0x1040)), response = true,
          logical = true, pushIndex = Some(1), phase = 2)
        s.acceptedAddresses should be(Seq(0x1000, 0x1020, 0x1020, 0x1040).map(BigInt(_)))
        s.finish()
      }

      // L0=0x1000 is ordinary; L1=0x1003 must drain its old response before lo can fire.
      s.initialize(0x1000, stride = 3, count = 3)
      s.tick("ordinary request left in flight", req = Some(BigInt(0x1000)), logical = true)
      for (i <- 0 until 2) s.tick(s"gather lo blocked by old in-flight read $i")
      val drain = s.tick("old response drain edge still cannot issue gather lo",
        response = true, pushIndex = Some(0))
      drain("req_valid") should be(BigInt(0))
      drain("inflCnt") should be(BigInt(1))
      drain("ostCredit") should be(BigInt(63))
      s.outstanding should be(0)
      s.tick("lo becomes eligible only on the following edge", req = Some(BigInt(0x1000)))
      s.credit should be(63)
      s.issuedCount should be(1)
      s.tick("drained gather separate lo response", response = true, phase = 1)
      val drainedResult = s.tick("drained gather immediate hi response", req = Some(BigInt(0x1020)),
        response = true, logical = true, pushIndex = Some(1), phase = 2)
      drainedResult("push_data") should be(expected03Through22)
      s.credit should be(62)
      s.tick("third logical item immediate lo", req = Some(BigInt(0x1000)), response = true)
      s.tick("third logical item immediate hi", req = Some(BigInt(0x1020)), response = true,
        logical = true, pushIndex = Some(2), phase = 2)
      s.acceptedAddresses should be(Seq(0x1000, 0x1000, 0x1020, 0x1000, 0x1020).map(BigInt(_)))
      s.finish()
    }
  }

  it should "dma_stretch" in {
    simulate(new ReadDmaHarness(cfg)) { dut =>
      val s = new Driver(dut, "dma_stretch")
      s.initialize(0x1000, stride = 16, count = 10, stretch = true)
      s.tick("low half request queued", req = Some(s.physicalAddress(0)), logical = true)
      s.tick("old low response plus new high request at the same physical address",
        req = Some(s.physicalAddress(1)), response = true, logical = true, pushIndex = Some(0))
      s.outstanding should be(1)
      s.credit should be(62)
      s.tick("old high response plus new low request must retain the new selector",
        req = Some(s.physicalAddress(2)), response = true, logical = true, pushIndex = Some(1))
      s.tick("return retained low selector and empty the FIFO",
        req = Some(s.physicalAddress(3)), ready = false, response = true, pushIndex = Some(2))
      s.outstanding should be(0)
      s.credit should be(61)
      s.tick("empty FIFO same-edge response bypass selects high half",
        req = Some(s.physicalAddress(3)), response = true, logical = true, pushIndex = Some(3))
      s.tick("empty FIFO same-edge response bypass selects low half",
        req = Some(s.physicalAddress(4)), response = true, logical = true, pushIndex = Some(4))
      s.outstanding should be(0)

      s.tick("queue high selector", req = Some(s.physicalAddress(5)), logical = true)
      s.tick("queue low selector behind high", req = Some(s.physicalAddress(6)), logical = true)
      s.tick("nonempty FIFO old response and new request preserve both queued selectors",
        req = Some(s.physicalAddress(7)), response = true, logical = true, pushIndex = Some(5))
      s.outstanding should be(2)
      s.tick("drain queued low selector under request hold", req = Some(s.physicalAddress(8)),
        ready = false, response = true, pushIndex = Some(6))
      s.tick("drain queued high selector under request hold", req = Some(s.physicalAddress(8)),
        ready = false, response = true, pushIndex = Some(7))
      s.outstanding should be(0)
      s.tick("empty FIFO low bypass after queued traffic", req = Some(s.physicalAddress(8)),
        response = true, logical = true, pushIndex = Some(8))
      s.tick("empty FIFO high bypass after queued traffic", req = Some(s.physicalAddress(9)),
        response = true, logical = true, pushIndex = Some(9))
      // Ten logical halves require ten physical reads, not five cached full-word reads.
      s.acceptedAddresses should be((0 until 10).map(i => BigInt(0x1000) + (i / 2) * 32))
      s.acceptedAddresses.size should be(10)
      s.credit should be(54)
      s.buffered should be(10)
      s.finish()
    }
  }
}
