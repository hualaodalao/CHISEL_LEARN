import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import chisel3.util.experimental.BoringUtils
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable
import scala.util.Random

private class WorkerBeeOsM1PerfWrapper(cfg: WorkerBeeConfig) extends Module {
  val worker = Module(new WorkerBee(cfg))
  val io = IO(chiselTypeOf(worker.io))
  worker.io <> io

  val observed = IO(new Bundle {
    val state = Output(UInt(4.W))
    val execute = Output(Bool())
    val done = Output(Bool())
    val cPushFire = Output(Bool())
    val osStart = Output(Bool())
    val osActive = Output(Bool())
    val phase = Output(UInt(2.W))
    val advance = Output(Bool())
    val aTokenValid = Output(Bool())
    val headValid = Output(UInt(cfg.totalN.W))
    val consume = Output(UInt(cfg.totalN.W))
    val columnDone = Output(UInt(cfg.totalN.W))
    val resultsReady = Output(Bool())
    val bDmaState = Output(UInt(2.W))
    val bGatherPhase = Output(UInt(2.W))
    val bPushFire = Output(Bool())
    val bFinalReqFire = Output(Bool())
    val wbValidIn = Output(Bool())
    val wsStallA = Output(Bool())
    val wsStallB = Output(Bool())
    val aPopFire = Output(Bool())
    val bPopFire = Output(Bool())
    val hasNext = Output(Bool())
    val drainNextCnt = Output(UInt(chisel3.util.log2Up(2 * cfg.totalN).W))
    val bOccupancy = Output(UInt(chisel3.util.log2Up(cfg.bBufferDepth + 1).W))
    val scaleBOccupancy = Output(UInt(chisel3.util.log2Up(cfg.scaleBufferDepth + 1).W))
  })

  observed.state := BoringUtils.tapAndRead(worker.executor.state)
  observed.execute := BoringUtils.tapAndRead(worker.executor.io.execute)
  observed.done := BoringUtils.tapAndRead(worker.executor.io.done)
  observed.cPushFire := BoringUtils.tapAndRead(worker.executor.io.cPush.valid) &&
    BoringUtils.tapAndRead(worker.executor.io.cPush.ready)
  observed.osStart := BoringUtils.tapAndRead(worker.executor.io.osStart)
  observed.osActive := BoringUtils.tapAndRead(worker.executor.io.osActive)
  observed.phase := BoringUtils.tapAndRead(worker.osSeq.io.phase).asUInt
  observed.advance := BoringUtils.tapAndRead(worker.osSeq.io.advance)
  observed.aTokenValid := BoringUtils.tapAndRead(worker.osSeq.io.aTokenValid)
  observed.headValid := BoringUtils.tapAndRead(worker.osSeq.io.headValid).asUInt
  observed.consume := BoringUtils.tapAndRead(worker.wbComb.io.osConsume).asUInt
  observed.columnDone := BoringUtils.tapAndRead(worker.wbComb.io.osDone).asUInt
  observed.resultsReady := BoringUtils.tapAndRead(worker.osSeq.io.resultsReady)
  observed.bDmaState := BoringUtils.tapAndRead(worker.bDma.state)
  observed.bGatherPhase := BoringUtils.tapAndRead(worker.bDma.gPhase)
  observed.bPushFire := BoringUtils.tapAndRead(worker.bDma.io.bufPush.valid) &&
    BoringUtils.tapAndRead(worker.bDma.io.bufPush.ready)
  observed.bFinalReqFire := BoringUtils.tapAndRead(worker.bDma.finalReqFire)
  observed.wbValidIn := BoringUtils.tapAndRead(worker.executor.io.wbValidIn)
  observed.wsStallA := BoringUtils.tapAndRead(worker.executor.io.perf.stallA)
  observed.wsStallB := BoringUtils.tapAndRead(worker.executor.io.perf.stallB)
  observed.aPopFire := BoringUtils.tapAndRead(worker.executor.io.aPop.valid) &&
    BoringUtils.tapAndRead(worker.executor.io.aPop.ready)
  observed.bPopFire := BoringUtils.tapAndRead(worker.executor.io.bPop.valid) &&
    BoringUtils.tapAndRead(worker.executor.io.bPop.ready)
  observed.hasNext := BoringUtils.tapAndRead(worker.executor.hasNext)
  observed.drainNextCnt := BoringUtils.tapAndRead(worker.executor.drainNextCnt)
  observed.bOccupancy := BoringUtils.tapAndRead(worker.executor.io.bOccupancy)
  observed.scaleBOccupancy := BoringUtils.tapAndRead(worker.executor.io.scaleBOcc.get)
}

class WorkerBeeOsM1PerfSpec extends AnyFlatSpec with ChiselSim with WbStatusPollApi {
  import WbDebugModel._

  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val cfg = WorkerBeeConfig()
  private val phaseNames = Vector("launch", "setup", "prime", "runAdvance", "runStall",
    "tailAdvance", "tailStall", "commit", "commitWait", "finalCWait", "finish",
    "sLOAD_B", "sNEXT_NK_TILE", "sDRAIN", "sLOAD_A")

  private case class Traffic(a: Int = 0, b: Int = 0, sa: Int = 0, sb: Int = 0,
      c: Int = 0, randomReady: Boolean = false)
  private case class PerfCase(name: String, params: DebugParams, traffic: Traffic)
  private case class Result(total: Long, advances: Long, commits: Long)

  private class Counts {
    var total = 0L
    val phases = mutable.LinkedHashMap(phaseNames.map(_ -> 0L): _*)
    val events = mutable.LinkedHashMap.empty[String, Long]
    def phase(name: String): Unit = { total += 1; phases(name) += 1 }
    def event(name: String, on: Boolean): Unit =
      events(name) = events.getOrElse(name, 0L) + (if (on) 1L else 0L)
    def count(name: String): Long = events.getOrElse(name, 0L)
    def phaseText(names: Seq[String]): String = names.map(n => s"$n=${phases(n)}").mkString(" ")
    def eventText: String = events.iterator.map { case (n, v) => s"$n=$v" }.mkString(" ")
    def check(label: String): Unit =
      assert(phases.values.sum == total, s"$label 互斥阶段和=${phases.values.sum}，总拍数=$total")
  }

  private class Tile(t: Int) extends Counts {
    var started = false
    var committed = false
    var advances = 0
    var runAdvances = 0
    var tailAdvances = 0
    var commits = 0
    val consumed = Array.fill(t)(0)
    val first = Array.fill(t)(-1)
    val last = Array.fill(t)(-1)
  }

  private def params(n: Int, k: Int, seed: Long, mx: Boolean = false,
      unaligned: Boolean = false): DebugParams = DebugParams(
    m = 1, n = n, k = k,
    aFmt = if (mx) DataFormat.MXE4M3 else DataFormat.BF16,
    bFmt = if (mx) DataFormat.MXE5M2 else DataFormat.BF16,
    cFmt = DataFormat.FP32, loadWMode = true,
    aMxBlkSel = 0, bMxBlkSel = if (mx) 1 else 0, cMxBlkSel = 0,
    addrModeSel = 0, rnd = 0, seed = seed, strictAddr = true,
    maxCycles = 600000, waveTarget = "", testHint = "OS-M1 性能观测",
    aBase = A_BASE + (if (unaligned) 3L else 0L),
    // 大 B 张量必须远离 C/scale 分区，否则地址保真内存本身会覆盖输入。
    bBase = 0x01000000L + (if (unaligned) 5L else 0L),
    osFmtKeep = false
  )

  behavior of "WorkerBee OS-M1 性能观测"

  it should "核对逐拍性能语义、互斥周期账及地址保真黄金结果" in {
    val cases = Vector(
      PerfCase("BF16 小形状 N32 K64", params(32, 64, 1101), Traffic()),
      PerfCase("BF16 单窗口 N16 K16", params(16, 16, 1102), Traffic()),
      PerfCase("BF16 连续 N2048 K1024", params(2048, 1024, 1103), Traffic()),
      PerfCase("BF16 非对齐 N48 K64", params(48, 64, 1104, unaligned = true),
        Traffic(a = 23, b = 7, c = 9, randomReady = true)),
      PerfCase("MX8 E4M3xE5M2 N48 K128", params(48, 128, 1105, mx = true),
        Traffic(a = 29, b = 5, sa = 41, sb = 11, c = 13, randomReady = true))
    )
    simulate(new WorkerBeeOsM1PerfWrapper(cfg)) { dut =>
      for (testCase <- cases) {
        val os = runTask(dut, testCase, os = true)
        val ws = runTask(dut, testCase, os = false)
        println(f"[OS-M1性能] ${testCase.name} 同种子/转置布局对照：OS=${os.total} WS=${ws.total} " +
          f"WS/OS=${ws.total.toDouble / os.total}%.3f（端到端实测，不把逻辑下界当完成时限）")
      }
    }
  }

  private def runTask(dut: WorkerBeeOsM1PerfWrapper, testCase: PerfCase, os: Boolean): Result = {
    val p = testCase.params.copy(osM1 = os)
    val tag = s"[OS-M1性能] ${testCase.name} ${if (os) "OS" else "WS"}"
    validate(p, cfg.totalN, cfg.hasMx4)
    assert(hwErrCode(p, cfg.totalN, cfg.hasMx4, cDmaExtW = cfg.cDmaExtW).isEmpty,
      s"$tag 性能用例配置不合法")
    val plan = new Plan(p, cfg)
    val memory = plan.prepare()
    val t = cfg.totalN
    val lanes = elemsPerLane(p.aFmt)
    val length = p.k / lanes
    val allMask = (BigInt(1) << t) - 1
    val totals = new Counts
    val tiles = Vector.fill(plan.nTiles)(new Tile(t))
    val cLog = mutable.ArrayBuffer.empty[WbSimCommon.CWriteBeat]

    def bool(x: Bool): Boolean = x.peek().litToBoolean
    def number(x: UInt): Int = x.peek().litValue.toInt
    def checkAddress(channel: String, index: Int, address: Long, expected: Vector[Long]): Unit = {
      assert(index < expected.size, s"$tag $channel 多发请求，序号=$index 期望拍数=${expected.size}")
      assert(address == expected(index),
        f"$tag $channel 请求#$index 地址=0x$address%X，期望=0x${expected(index)}%X")
    }

    class ReadMemory(channel: String, port: WorkerBeeDMAExtReadOnlyIF, expected: Vector[Long],
        width: Int, latency: Int, salt: Int) {
      private val rng = new Random(p.seed ^ salt.toLong)
      private val queue = mutable.Queue.empty[(Int, BigInt)]
      var requests = 0
      var responses = 0
      var reqFire = false
      var rspFire = false
      var heldResponses = 0
      // 此接口没有 len 字段；每个请求严格是一拍，长度由响应端口位宽固定。
      val expectedBytes = if (channel.startsWith("scale")) 4 else 32
      assert(port.rsp.payload.data.getWidth == expectedBytes * 8 && width == expectedBytes * 8,
        s"$tag $channel 固定单拍请求长度错误，期望=$expectedBytes 字节")

      def idle(): Unit = {
        port.req.ready.poke(true.B)
        port.rsp.valid.poke(false.B)
        port.rsp.payload.data.poke(0.U)
        port.rsp.payload.err.poke(false.B)
      }
      def drive(cycle: Int): Unit = {
        port.req.ready.poke((!testCase.traffic.randomReady || rng.nextInt(10) < 7).B)
        port.rsp.valid.poke((queue.headOption.exists(_._1 <= cycle)).B)
        port.rsp.payload.data.poke(queue.headOption.map(_._2).getOrElse(BigInt(0)).U)
        port.rsp.payload.err.poke(false.B)
        reqFire = bool(port.req.valid) && bool(port.req.ready)
        if (reqFire) {
          val address = port.req.payload.addr.peek().litValue.toLong
          checkAddress(channel, requests, address, expected)
          assert(address % (width / 8) == 0, s"$tag $channel 物理 gather 请求未按拍对齐")
          queue.enqueue((cycle + latency, memory.loadBits(address, width)))
          requests += 1
        }
        port.rsp.valid.poke(queue.headOption.exists(_._1 <= cycle).B)
        port.rsp.payload.data.poke(queue.headOption.map(_._2).getOrElse(BigInt(0)).U)
        assert(reqFire == (bool(port.req.valid) && bool(port.req.ready)),
          s"$tag $channel 同拍响应改变了已采样请求握手")
        rspFire = bool(port.rsp.valid) && bool(port.rsp.ready)
      }
      def retire(): Unit = {
        if (rspFire) { queue.dequeue(); responses += 1 }
        else if (bool(port.rsp.valid)) heldResponses += 1
      }
      def check(): Unit = {
        assert(requests == expected.size && responses == expected.size && queue.isEmpty,
          s"$tag $channel 请求/响应/期望=$requests/$responses/${expected.size}，在途=${queue.size}")
        println(s"$tag $channel 地址/固定长度通过：请求=$requests 响应=$responses " +
          s"每拍=${width / 8}字节 总字节=${requests.toLong * (width / 8)} 响应保持拍=$heldResponses")
      }
      def events(counts: Counts): Unit = {
        counts.event(s"$channel.req", reqFire)
        counts.event(s"$channel.rsp", rspFire)
        counts.event(s"$channel.valid", bool(port.req.valid))
        counts.event(s"$channel.reqWait", bool(port.req.valid) && !bool(port.req.ready))
      }
    }

    class WriteMemory {
      private val port = dut.io.dmaC
      private val rng = new Random(p.seed ^ 0xC011L)
      private val queue = mutable.Queue.empty[Int]
      private val expected = plan.expCAddrSeq.toVector
      var responses = 0
      var reqFire = false
      var rspFire = false
      assert(port.req.payload.data.getWidth == cfg.cDmaExtW &&
        port.req.payload.strb.getWidth == cfg.cDmaExtW / 8,
        s"$tag C 固定单拍数据/选通长度错误")
      def idle(): Unit = {
        port.req.ready.poke(true.B)
        port.rsp.valid.poke(false.B)
        port.rsp.payload.err.poke(false.B)
      }
      def drive(cycle: Int): Unit = {
        port.req.ready.poke((!testCase.traffic.randomReady || rng.nextInt(10) < 7).B)
        port.rsp.valid.poke(queue.headOption.exists(_ <= cycle).B)
        port.rsp.payload.err.poke(false.B)
        reqFire = bool(port.req.valid) && bool(port.req.ready)
        if (reqFire) {
          val index = cLog.size
          val address = port.req.payload.addr.peek().litValue.toLong
          val data = port.req.payload.data.peek().litValue
          val strb = port.req.payload.strb.peek().litValue
          checkAddress("C", index, address, expected)
          val errors = WbSimCommon.auditCBeatLanes(index, address, data, strb,
            p.cFmt, t, cfg.cDmaExtW, beatInRow = index % plan.beatsPerRow)
          assert(errors.isEmpty, s"$tag C 地址/选通字节审计失败：${errors.mkString("；")}")
          cLog += ((address, data, strb))
          val wordBase = address - address % (cfg.cDmaExtW / 8)
          for (lane <- 0 until cfg.cDmaExtW / 8 if strb.testBit(lane))
            memory.storeByte(wordBase + lane, ((data >> (8 * lane)) & 0xFF).toInt)
          queue.enqueue(cycle + testCase.traffic.c)
        }
        port.rsp.valid.poke(queue.headOption.exists(_ <= cycle).B)
        assert(reqFire == (bool(port.req.valid) && bool(port.req.ready)),
          s"$tag C 同拍响应改变了已采样请求握手")
        rspFire = bool(port.rsp.valid) && bool(port.rsp.ready)
      }
      def retire(): Unit = if (rspFire) { queue.dequeue(); responses += 1 }
      def check(): Unit = {
        assert(cLog.size == expected.size && responses == expected.size && queue.isEmpty,
          s"$tag C 请求/响应/期望=${cLog.size}/$responses/${expected.size}，在途=${queue.size}")
        for ((address, data, strb) <- cLog; lane <- 0 until cfg.cDmaExtW / 8 if strb.testBit(lane)) {
          val wordBase = address - address % (cfg.cDmaExtW / 8)
          assert(memory.loadByte(wordBase + lane) == ((data >> (8 * lane)) & 0xFF).toInt,
            s"$tag C 字节内存写回与选通日志不一致")
        }
      }
      def events(counts: Counts): Unit = {
        counts.event("C.req", reqFire)
        counts.event("C.rsp", rspFire)
        counts.event("C.valid", bool(port.req.valid))
        counts.event("C.reqWait", bool(port.req.valid) && !bool(port.req.ready))
        counts.event("C.rspHold", bool(port.rsp.valid) && !bool(port.rsp.ready))
      }
    }

    val readers = Vector(
      new ReadMemory("A", dut.io.dmaA, plan.expAAddrSeq.toVector, cfg.aExtW, testCase.traffic.a, 0xA011),
      new ReadMemory("B", dut.io.dmaB, plan.expBAddrSeq.toVector, cfg.bExtW, testCase.traffic.b, 0xB011),
      new ReadMemory("scaleA", dut.io.dmaScaleA.get, plan.expScaleAAddrSeq.toVector,
        cfg.scaleAW, testCase.traffic.sa, 0x5A11),
      new ReadMemory("scaleB", dut.io.dmaScaleB.get, plan.expScaleBAddrSeq.toVector,
        cfg.scaleBW, testCase.traffic.sb, 0x5B11)
    )
    val writer = new WriteMemory
    readers.foreach(_.idle())
    writer.idle()
    dut.io.dmaCScale.get.req.ready.poke(true.B)
    dut.io.dmaCScale.get.rsp.valid.poke(false.B)
    dut.io.dmaCScale.get.rsp.payload.err.poke(false.B)
    dut.io.resp.ready.poke(true.B)
    dut.io.cmd.foreach { cmd =>
      pokeRegCommand(cmd, WorkerBeeOp.REG_WRITE1, 0)
      cmd.valid.poke(false.B)
    }
    dut.reset.poke(true.B)
    dut.clock.step(3)
    dut.reset.poke(false.B)
    dut.clock.step(2)

    def writePair(r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
      transactReg(dut.clock, dut.io.cmd(0), dut.io.resp, WorkerBeeOp.REG_WRITE2,
        r0, r1, BigInt(d0), BigInt(d1))
    }
    writePair(WbSimCommon.M_REG_ADDR, WbSimCommon.NK_REG_ADDR, p.m, p.n.toLong | (p.k.toLong << 16))
    writePair(WbSimCommon.A_REG_ADDR, WbSimCommon.B_REG_ADDR, p.aBase, p.bBase)
    writePair(WbSimCommon.C_REG_ADDR, WbSimCommon.CONTROL_REG_ADDR, C_BASE, p.controlWord5)
    writePair(WbSimCommon.SCALE_A_REG_ADDR, WbSimCommon.SCALE_B_REG_ADDR, SA_BASE, SB_BASE)
    transactReg(dut.clock, dut.io.cmd(0), dut.io.resp, WorkerBeeOp.REG_WRITE1,
      WbSimCommon.SCALE_C_REG_ADDR, d0 = BigInt(SC_BASE))
    assert(readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.CONTROL_REG_ADDR) ==
      BigInt(p.controlWord5), s"$tag 控制字回读不一致")
    prepareStart(dut.clock, dut.io.cmd(0), dut.io.resp)
    launchStart(dut.clock, dut.io.cmd(0))

    var cycle = 0
    var measuring = false
    var finished = false
    var executeCount = 0
    var doneCount = 0
    var executeCycle = -1
    var doneCycle = -1
    var tileIndex = 0
    var modelPhase = WbOsM1Phase.sIdle.litValue.toInt
    val idlePhase = WbOsM1Phase.sIdle.litValue.toInt
    val runPhase = WbOsM1Phase.sRun.litValue.toInt
    val tailPhase = WbOsM1Phase.sTail.litValue.toInt
    val donePhase = WbOsM1Phase.sDone.litValue.toInt
    val tokens = Array.fill(t)(false)

    while (!finished && cycle < p.maxCycles) {
      readers.foreach(_.drive(cycle))
      writer.drive(cycle)
      val o = dut.observed
      val execute = bool(o.execute)
      val done = bool(o.done)
      if (execute) {
        executeCount += 1
        assert(!measuring && executeCount == 1, s"$tag execute 必须恰好一次")
        executeCycle = cycle
        measuring = true
      }
      assert(!bool(dut.io.dmaCScale.get.req.valid), s"$tag 非量化输出不应请求 scaleC 写回")
      if (bool(dut.io.resp.valid)) assert(!bool(dut.io.resp.payload.err), s"$tag 命令响应错误")
      if (measuring) {
        val label = s"$tag 拍$cycle tile=$tileIndex"
        val row = tiles(math.min(tileIndex, tiles.size - 1))
        val state = number(o.state)
        val active = bool(o.osActive)
        val start = bool(o.osStart)
        val commit = bool(o.cPushFire)
        val token = bool(o.aTokenValid)
        val heads = o.headValid.peek().litValue
        val consumed = o.consume.peek().litValue
        val columnDone = o.columnDone.peek().litValue
        val advance = bool(o.advance)
        val resultsReady = bool(o.resultsReady)
        val running = os && active && row.started && !row.committed && modelPhase == runPhase
        val tail = os && active && row.started && !row.committed && modelPhase == tailPhase
        val expectedDone = if (row.started && !row.committed && os)
          (0 until t).foldLeft(BigInt(0)) { (mask, j) =>
            if (row.advances >= length + j) mask.setBit(j) else mask
          } else BigInt(0)
        val allDone = expectedDone == allMask
        val missingTailHead = ((allMask ^ expectedDone) & (allMask ^ heads)) != 0
        val expectedAdvance = (running && token && heads == allMask) ||
          (tail && !allDone && !missingTailHead)
        val expectedConsume = if (expectedAdvance)
          (0 until t).foldLeft(BigInt(0)) { (mask, j) =>
            if (row.advances >= j && row.advances < length + j) mask.setBit(j) else mask
          } else BigInt(0)
        val bWindowReady = number(o.bOccupancy) >= t &&
          (!isMxAny(p.aFmt) || number(o.scaleBOccupancy) >= t)
        val wsA = state == 2 && !bool(o.aPopFire)
        val wsB = (state == 1 && !bool(o.bPopFire)) ||
          (state == 4 && bool(o.hasNext) && number(o.drainNextCnt) == 0 && !bWindowReady)
        val stallA = if (os) running && !token else wsA
        val stallB = if (os) (running && heads != allMask) ||
          (tail && !allDone && missingTailHead) else wsB

        assert(bool(dut.io.perf.stallA) == stallA, s"$label stallA 与独立 Run/WS 条件不一致")
        assert(bool(dut.io.perf.stallB) == stallB, s"$label stallB 与独立 Run/Tail 未完成列条件不一致")
        assert(bool(dut.io.perf.array) == (if (os) expectedConsume != 0 else bool(o.wbValidIn)),
          s"$label array 必须表示真实消费（含 Tail），WS 保留 wbValidIn")
        assert(bool(dut.io.perf.busy) == bool(dut.io.status.busy), s"$label busy 性能电平不一致")
        for ((perf, request) <- Vector(dut.io.perf.dmaA -> dut.io.dmaA.req.valid,
            dut.io.perf.dmaB -> dut.io.dmaB.req.valid, dut.io.perf.dmaC -> dut.io.dmaC.req.valid,
            dut.io.perf.scaleA -> dut.io.dmaScaleA.get.req.valid,
            dut.io.perf.scaleB -> dut.io.dmaScaleB.get.req.valid,
            dut.io.perf.scaleC -> dut.io.dmaCScale.get.req.valid))
          assert(bool(perf) == bool(request), s"$label DMA 性能电平不等于物理请求 valid")

        if (os) {
          assert(active || execute, s"$label OS 测量窗中执行器必须处于 OS 生命周期（execute 拍除外）")
          assert(number(o.phase) == modelPhase, s"$label Sequencer 相位与独立状态模型不一致")
          assert(columnDone == expectedDone, s"$label 列 done 与独立累计推进位置不一致")
          assert(advance == expectedAdvance, s"$label advance 与独立 Run/Tail 门控不一致")
          assert(consumed == expectedConsume, s"$label 列消费不满足 advance 区间 [j,K/g+j)")
          assert(resultsReady == ((modelPhase == tailPhase || modelPhase == donePhase) && allDone),
            s"$label resultsReady 与独立列完成模型不一致")
          assert(!tail || !token, s"$label Tail 不得索取 A token")
          for (j <- 0 until t) {
            val expectedPrefix = math.max(0, math.min(length, row.advances - j))
            assert(row.consumed(j) == expectedPrefix, s"$label 列$j 消费前缀与独立延迟${j}不一致")
            val delayedToken = if (j == 0) token else tokens(j - 1)
            assert((expectedAdvance && delayedToken) == expectedConsume.testBit(j),
              s"$label 列$j token 延迟模型与几何消费计划不一致")
          }
          if (start) {
            assert(tileIndex < tiles.size && !row.started && !row.committed && !advance,
              s"$label 每个 tile 必须单次启动且启动拍不消费")
            row.started = true
          }
        } else {
          assert(!active && !start && !advance && consumed == 0, s"$label WS 不得激活 OS")
          assert(bool(o.wsStallA) == wsA && bool(o.wsStallB) == wsB,
            s"$label WS 原有 stallA/stallB 表达式不一致")
        }

        val category = if (execute) "launch"
          else if (done) "finish"
          else if (!os) state match {
            case 1 => "sLOAD_B"
            case 2 => "sLOAD_A"
            case 3 => "sNEXT_NK_TILE"
            case 4 => "sDRAIN"
            case _ => throw new AssertionError(s"$label WS 未分类状态=$state")
          }
          else if (state == 5) "setup"
          else if (commit) "commit"
          else if (resultsReady) "commitWait"
          else if (state == 8) "finalCWait"
          else if (expectedAdvance && running) "runAdvance"
          else if (expectedAdvance && tail) "tailAdvance"
          else if (row.advances == 0) "prime"
          else if (running) "runStall"
          else if (tail) "tailStall"
          else throw new AssertionError(s"$label OS 未分类状态=$state 相位=$modelPhase")
        totals.phase(category)
        if (os) row.phase(category)
        for (counts <- if (os) Vector(totals, row) else Vector(totals)) {
          counts.event("stallA", stallA)
          counts.event("stallB", stallB)
          counts.event("stallOverlap", stallA && stallB)
          counts.event("array", bool(dut.io.perf.array))
          counts.event("epilogue", bool(dut.io.perf.epilogue))
          counts.event("B.sNEXT_COL", number(o.bDmaState) == 2)
          counts.event("B.gather", number(o.bGatherPhase) != 0)
          for (g <- 1 to 3) counts.event(s"B.g$g", number(o.bGatherPhase) == g)
          counts.event("B.logicalReq", bool(o.bFinalReqFire))
          counts.event("B.push", bool(o.bPushFire))
          counts.event("tailDoneEmptyExempt", tail && !allDone && (expectedDone & (allMask ^ heads)) != 0)
          readers.foreach(_.events(counts))
          writer.events(counts)
        }

        if (os) {
          if (expectedAdvance) {
            for (j <- 0 until t if consumed.testBit(j)) {
              if (row.first(j) < 0) row.first(j) = row.advances
              row.last(j) = row.advances
              row.consumed(j) += 1
            }
            for (j <- (0 until t).reverse) tokens(j) = if (j == 0) token else tokens(j - 1)
            row.advances += 1
            if (running) row.runAdvances += 1 else row.tailAdvances += 1
          }
          if (start) modelPhase = runPhase
          else if (running && expectedAdvance && row.runAdvances == length) modelPhase = tailPhase
          else if (tail && allDone) modelPhase = donePhase
          if (state == 5) modelPhase = idlePhase
          if (commit) {
            assert(row.started && !row.committed && resultsReady && allDone,
              s"$label 提交必须晚于所有列完成且每 tile 恰好一次")
            row.commits += 1
            assert(row.commits == 1 && row.runAdvances == length && row.tailAdvances == t - 1 &&
              row.advances == length + t - 1, s"$label 提交时 K/g、Tail T-1 或总 advance 账不一致")
            for (j <- 0 until t) {
              assert(row.consumed(j) == length && row.first(j) == j && row.last(j) == length + j - 1,
                s"$label 列$j 提交时消费 K/g 或首末 token 延迟错误")
            }
            row.committed = true
            java.util.Arrays.fill(tokens, false)
            tileIndex += 1
          }
        }
        if (done) { doneCount += 1; doneCycle = cycle; finished = true }
      } else {
        assert(!done && !readers.exists(_.reqFire) && !writer.reqFire,
          s"$tag execute 之前不应完成或发出 DMA 请求，避免测量丢首拍")
      }
      readers.foreach(_.retire())
      writer.retire()
      dut.clock.step()
      cycle += 1
    }

    assert(finished && executeCount == 1 && doneCount == 1 && totals.total > 0,
      s"$tag 未在${p.maxCycles}拍内覆盖实际 execute 至实际 done（含两端）")
    assert(totals.total == doneCycle.toLong - executeCycle + 1,
      s"$tag 阶段总拍数必须等于独立采样的 execute/done 周期间隔（含两端）")
    readers.foreach(_.check())
    writer.check()
    readers.foreach(_.idle())
    writer.idle()
    totals.check(tag)
    assert(totals.count("B.logicalReq") == plan.nTiles.toLong * plan.bKTiles * t &&
      totals.count("B.push") == plan.nTiles.toLong * plan.bKTiles * t,
      s"$tag B 逻辑请求/推入数量与张量窗口不一致")
    assert(totals.count("C.req") == cLog.size && totals.count("C.rsp") == writer.responses,
      s"$tag C 事件必须完整包含在 execute/done 测量窗内")
    assert(totals.count("stallA") + totals.count("stallB") - totals.count("stallOverlap") <= totals.total,
      s"$tag 重叠停顿集合统计错误")
    val irq = WbSimCommon.decodeIrq(readRegister(dut.clock, dut.io.cmd(0), dut.io.resp,
      WbSimCommon.IRQ_RAW_REG_ADDR))
    assert(irq.done && !irq.anyError && !bool(dut.io.status.busy) && !bool(dut.io.status.inFlight),
      s"$tag 完成后 IRQ/错误/忙碌状态不一致")
    val comparison = plan.compare(cLog.toSeq, Seq.empty)
    assert(comparison.elems == p.m * p.n && comparison.mismatch == 0 && comparison.laneViolations == 0,
      s"$tag 独立黄金结果或字节选通不一致：${comparison.details.mkString("；")}")

    var advances = 0L
    var commits = 0L
    if (os) {
      assert(tileIndex == plan.nTiles, s"$tag 提交 tile 数量错误")
      assert(tiles.map(_.total).sum == totals.total, s"$tag 每 tile 周期和必须等于总拍数")
      for ((row, index) <- tiles.zipWithIndex) {
        row.check(s"$tag tile=$index")
        assert(row.started && row.committed && row.commits == 1, s"$tag tile=$index 启动/提交不完整")
        advances += row.advances
        commits += row.commits
        println(s"$tag tile=$index 总拍=${row.total} ${row.phaseText(phaseNames.take(11))} " +
          s"advance=${row.advances} 列consume=${t}x$length 列延迟=0..${t - 1}（逐列通过） " +
          s"重叠：stallA=${row.count("stallA")} stallB=${row.count("stallB")} overlap=${row.count("stallOverlap")} " +
          s"B.next=${row.count("B.sNEXT_COL")} B.gather=${row.count("B.gather")} " +
          s"C.req=${row.count("C.req")} C.rsp=${row.count("C.rsp")}")
      }
      val lowerBound = plan.nTiles.toLong * (length + t - 1 + 1)
      assert(advances == plan.nTiles.toLong * (length + t - 1) && commits == plan.nTiles &&
        totals.total >= lowerBound, s"$tag 逻辑推进/提交下界错误")
      assert(totals.count("array") == advances && totals.phases("runAdvance") == plan.nTiles.toLong * length &&
        totals.phases("tailAdvance") == plan.nTiles.toLong * (t - 1) && totals.phases("commit") == commits,
        s"$tag 互斥推进阶段与真实阵列消费/提交事件不一致")
      if (p.k == 1024 && p.n == 2048) {
        assert(advances == 132992L && commits == 128L && lowerBound == 133120L,
          s"$tag 大形状逻辑下界必须为132992次advance加128次commit")
      }
      if (p.k == 16 && p.n == 16)
        assert(totals.count("tailDoneEmptyExempt") > 0 && totals.phases("tailAdvance") == t - 1,
          s"$tag 单窗口必须覆盖 Tail 已完成空列豁免并继续真实消费")
      if (testCase.traffic.randomReady)
        assert(totals.count("stallA") > 0 && totals.count("stallB") > 0 &&
          totals.count("stallOverlap") > 0, s"$tag 延迟/随机反压必须覆盖 A/B 独立及重叠停顿")
      println(s"$tag 逻辑下界=$lowerBound，仅含advance=${advances}及commit=$commits，不承诺端到端时限")
    }
    val reportedPhases = if (os) phaseNames.take(11)
      else Vector("launch", "sLOAD_B", "sNEXT_NK_TILE", "sDRAIN", "sLOAD_A", "finish")
    println(s"$tag 实际execute至done含两端：总拍=${totals.total} 互斥阶段：${totals.phaseText(reportedPhases)}")
    println(s"$tag 重叠事件（不加入互斥阶段和，按执行拍归属，预取/前块写回可跨tile）：${totals.eventText}")
    println(s"$tag 地址/固定请求长度/字节选通/独立黄金对比全部通过，元素=${comparison.elems}")
    Result(totals.total, advances, commits)
  }
}
