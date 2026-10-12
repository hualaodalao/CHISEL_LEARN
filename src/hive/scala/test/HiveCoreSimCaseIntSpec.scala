/** HiveCoreSimCaseIntSpec -- INT8/INT4 end-to-end GEMM verification suite.
  *
  * Covers the Phase 3 architecture: HiveFusionMacInt dual-lane (INT8) and
  * quad-lane (INT4) packed MAC with elemsPerLane = 2 / 4 respectively.
  *
  * Test matrix:
  *   1. INT8 single kTile  (K=32, loadWMode=1)
  *   2. INT8 multi kTile   (K=64, loadWMode=1)
  *   3. INT4 single kTile  (K=64, loadWMode=1)
  *   4. INT4 multi kTile   (K=128, loadWMode=1)
  *   5. INT8 K not divisible (K=24 -> errCode 8 negative case)
  *   6. INT8 cAddr misaligned (C_BASE+8 -> errCode 10 negative case) [#170-W5-2]
  *
  * Output format: INT32 (direct truncation of 40-bit accumulator lower 32 bits;
  * no rounding, no FTZ; golden = exact integer sum).
  *
  * NOTE on loadWMode: packed INT formats (INT8 elemsPerLane=2, INT4 elemsPerLane=4)
  * require loadWMode=1 (transposed B, N*K layout) for correct operation -- the same
  * architectural constraint as MX formats (errCode 4). In loadWMode=0 the B DMA
  * address stepping (bRowOff = N*elemBytes) causes overlapping reads that don't
  * correctly pack elemsPerLane consecutive K elements into each 16-bit lane.
  * The hardware currently lacks an errCode check for INT+loadWMode=0 (unlike MX
  * which has errCode 4); this is a known gap reported to the team.
  *
  * Infrastructure: reuses HiveDebugModel.Plan for memory image, golden model,
  * and address sequences. Uses a custom INT exact-match compare path. (The
  * historical Plan.compare() histogram overflow for 32-bit output formats,
  * nCodes = 1 << 32 wraps to 1, is now fixed in #73.)
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._
import scala.collection.mutable

class HiveCoreSimCaseIntSpec extends AnyFlatSpec with Matchers with ChiselSim
    with HiveAlwaysFstSim with HiveStatusPollApi {
  import HiveDebugModel._

  private val TAG = "INT-E2E"
  private val SUITE = "HiveCoreSimCaseIntSpec"

  // ========== Hardware configuration (same as QuickSpec / MxSpec) ==========
  private val cfg = HiveCoreConfig(
    arrayN = 8, clusterM = 2,       // totalN = 16
    aW = 16, bW = 16, cW = 40,
    // Default supportedFmts = {INT8, INT4, MXE4M3, MXE5M2, MXE2M1}
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
    scaleBufferDepth = 64
  )

  behavior of "HiveCore INT GEMM"

  // ==========================================================================
  // Positive cases
  // ==========================================================================

  it should "run INT8 single kTile M=32 N=16 K=32 loadW=1" in {
    runIntGemm(m = 32, n = 16, k = 32,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = true, seed = 42L,
      hint = "int8-single-kTile-loadW1")
  }

  it should "run INT8 multi kTile M=32 N=16 K=64 loadW=1" in {
    runIntGemm(m = 32, n = 16, k = 64,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = true, seed = 43L,
      hint = "int8-multi-kTile-loadW1")
  }

  it should "run INT4 single kTile M=32 N=16 K=64 loadW=1" in {
    runIntGemm(m = 32, n = 16, k = 64,
      aFmt = DataFormat.INT4, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
      loadWMode = true, seed = 44L,
      hint = "int4-single-kTile-loadW1")
  }

  it should "run INT4 multi kTile M=32 N=16 K=128 loadW=1" in {
    runIntGemm(m = 32, n = 16, k = 128,
      aFmt = DataFormat.INT4, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
      loadWMode = true, seed = 45L,
      hint = "int4-multi-kTile-loadW1")
  }

  // ==========================================================================
  // 【P0-2 gather】非对齐 A/B 基址：读 DMA 对齐多发读 + 内部字节移位重组的
  //   端到端数据通路证明。aBase off=3、bBase off=11（不同偏移量覆盖不同合并
  //   移位档）；行步长 K=32B 对齐 ⟹ 每拍均非对齐、全走 gather 慢路径。
  //   cAddr 保持 32B 对齐（errCode 10 守护不触发）；全量逐元素对拍验证 merge。
  // ==========================================================================
  it should "run INT8 gather M=32 N=16 K=32 loadW=1 with misaligned A/B bases" in {
    runIntGemm(m = 32, n = 16, k = 32,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = true, seed = 46L,
      hint = "int8-gather-misaligned-bases",
      aBase = A_BASE + 3, bBase = B_BASE + 11)
  }

  // ==========================================================================
  // Negative case: K not divisible by kPerBeat -> errCode 8
  // ==========================================================================

  it should "reject INT8 K=24 with errCode 8 (K not divisible by kPerBeat=32)" in {
    runIntErrCase(m = 32, n = 16, k = 24,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = false, expectedErrCode = 8,
      hint = "int8-errK24")
  }

  // ==========================================================================
  // (#170-W5-2) Negative case: cAddr misaligned -> errCode 10
  // ==========================================================================

  it should "reject INT8 cAddr misaligned with errCode 10 (cAddr%32!=0)" in {
    runIntErrCase(m = 32, n = 16, k = 32,
      aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
      loadWMode = true, expectedErrCode = 10,
      hint = "int8-errCAddrMisalign",
      cAddr = C_BASE + 8)   // 0x200008: 8 mod 32 = 8 != 0 -> triggers errCode 10
  }

  // ==========================================================================
  // Shared runner: positive INT GEMM
  // ==========================================================================
  private def runIntGemm(m: Int, n: Int, k: Int,
      aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
      loadWMode: Boolean, seed: Long, hint: String,
      aBase: Long = A_BASE, bBase: Long = B_BASE): Unit = {

    val p = DebugParams(
      m = m, n = n, k = k,
      aFmt = aFmt, bFmt = bFmt, cFmt = cFmt,
      loadWMode = loadWMode,
      aMxBlkSel = 0, bMxBlkSel = 0, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = seed,
      strictAddr = true, maxCycles = 16000,
      waveTarget = s"sim/int/$hint.fst", testHint = hint,
      aBase = aBase, bBase = bBase
    )

    // Pre-flight: errCode check (should be None for valid INT configs)
    val predicted = hwErrCode(p, cfg.totalN, cfg.hasMx4)
    require(predicted.isEmpty,
      s"[$TAG] Config should be legal but hwErrCode returned $predicted; params: ${p.banner}")

    // Validate (golden model constraints)
    validate(p, cfg.totalN, cfg.hasMx4)

    val plan = new Plan(p, cfg)
    plan.prepare()

    val totalN = cfg.totalN
    println(s"[$TAG] Starting: M=$m N=$n K=$k aFmt=$aFmt bFmt=$bFmt cFmt=$cFmt loadW=$loadWMode")
    println(s"[$TAG] Geometry: totalN=$totalN kTiles=${plan.aKTiles} nTiles=${plan.nTiles} " +
      s"beatsPerRow=${plan.beatsPerRow}")
    println(s"[$TAG] Expected beats: A=${plan.expAAddrSeq.size} B=${plan.expBAddrSeq.size} " +
      s"C=${plan.expCAddrSeq.size} sA=${plan.expScaleAAddrSeq.size} sB=${plan.expScaleBAddrSeq.size}")

    // 【#166】cLog 升为 (addr,data,strb) 三元组，以支持 lane 级逐字节对账；
    //   sLog（scaleC）保持二元组 -- (#170-W3) scaleC 通道端口宽度 = 1 字节
    //   (rowW = cScaleRowW = totalN/2 bits; totalN=16 gives rowW=8 bits=1 byte),
    //   故 lane 窗口退化为单 lane、移位恒 0、strb 恒 0xFF。契约并非「豁免」而是
    //   在单 lane 端口上平凡退化，结构上不可能出现 data/strb 错位。
    val cLog = mutable.ArrayBuffer[HiveSimCommon.CWriteBeat]()
    val sLog = mutable.ArrayBuffer[(Long, BigInt)]()
    val addrBad = mutable.ArrayBuffer[String]()
    var addrBadCnt = 0
    var cycle = 0
    var done = false
    var errSeen = false
    var errCode = -1
    var cmpMismatch = -1
    var cmpElems = 0

    try {
      simulate(new HiveCore(cfg)) { dut =>
        // ---- Address check helper (collect, don't throw) ----
        def chk(ch: String, i: Int, hw: Long, exp: Seq[Long]): Unit =
          if (i < exp.size && hw != exp(i)) {
            addrBadCnt += 1
            if (addrBad.size < 16)
              addrBad += f"$ch beat#$i hw=0x$hw%X exp=0x${exp(i)}%X delta=${hw - exp(i)}%d"
          }

        // ---- Read channel driver (memory-faithful: respond by address) ----
        def rdCh(chan: String, cnt: Int, exp: Seq[Long], vld: => Boolean, adr: => Long,
                 rsp: (Boolean, BigInt) => Unit, at: Long => BigInt): Int =
          if (vld && cnt < exp.size) { val a = adr; chk(chan, cnt, a, exp); rsp(true, at(a)); cnt + 1 }
          else { rsp(false, BigInt(0)); cnt }

        // ---- Write channel driver ----
        def wrCh(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                 ack: Boolean => Unit, log: mutable.ArrayBuffer[(Long, BigInt)]): Int =
          if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da)); ack(true); i + 1 }
          else { ack(false); i }

        // ---- (#166) dmaC write channel driver: additionally peek wstrb so the lane-level
        //      byte-wise audit (HiveSimCommon.auditCBeatLanes) gets the strobe bits. The old
        //      path never peeked strb, which is exactly why "strb shifted but wdata not"
        //      stayed invisible on the TB side. ----
        def wrChC(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                  st: => BigInt, ack: Boolean => Unit,
                  log: mutable.ArrayBuffer[HiveSimCommon.CWriteBeat]): Int =
          if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da, st)); ack(true); i + 1 }
          else { ack(false); i }

        // ---- Reset ----
        Seq(dut.io.dmaA.rsp, dut.io.dmaB.rsp).foreach { r =>
          r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaScaleA.get.rsp, dut.io.dmaScaleB.get.rsp).foreach { r =>
          r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaC.rsp, dut.io.dmaCScale.get.rsp).foreach { r =>
          r.valid.poke(false.B); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaA.req, dut.io.dmaB.req, dut.io.dmaC.req,
          dut.io.dmaScaleA.get.req, dut.io.dmaScaleB.get.req,
          dut.io.dmaCScale.get.req).foreach(_.ready.poke(true.B))
        dut.io.resp.ready.poke(true.B)
        dut.io.cmd.valid.poke(false.B)
        dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)

        // ---- Command helper ----
        def cmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
          dut.io.cmd.valid.poke(true.B); dut.io.cmd.payload.op.poke(op)
          dut.io.cmd.payload.reg0Addr.poke(r0.U); dut.io.cmd.payload.reg1Addr.poke(r1.U)
          dut.io.cmd.payload.data0.poke(d0.U); dut.io.cmd.payload.data1.poke(d1.U)
          dut.clock.step(); dut.io.cmd.valid.poke(false.B); dut.clock.step(2)
        }

        // ---- Configure registers ----
        // (#164) 寄存器重排后的新写序列（槽位由 12 个压到 8 个有效 = 0..5, 8..11）：
        //   reg0=m、reg1=n|k<<16（旧 reg2=k 已并入 reg1 高 16 位，读回 Cat(k,n)）、
        //   reg2/3/4 = a/b/cAddr（旧 reg3/4/5）、reg5 = 控制字（旧 reg6+reg7 合并）。
        //   ★旧 `cmd(REG_WRITE2, 0x06, 0x07, p.controlWord6, p.controlWord7)` 已删：main 侧
        //   wregs 无 is(6)/is(7) 分支 ⟹ 写为 no-op、reg5 保持复位 0（aFmt/bFmt/cFmt 全 FP32）
        //   ⟹ errCode 5 抢跑，且 a/b/cAddr 会被错位写到 reg3/4/5（reg5 被当成 cAddr）。
        cmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, m, n.toLong | (k.toLong << 16))
        cmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, p.aBase, p.bBase)
        cmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, C_BASE, p.controlWord5)
        cmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SA_BASE, SB_BASE)
        cmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SC_BASE, 0L)

        println(s"[$TAG] regs: reg5(controlWord5)=0x${p.controlWord5.toHexString}")

        // ---- EXECUTE ----
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
        dut.io.cmd.payload.reg0Addr.poke(0.U); dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U); dut.io.cmd.payload.data1.poke(0.U)
        dut.clock.step(); dut.io.cmd.valid.poke(false.B)

        // ---- Main simulation loop ----
        var aS = 0; var bS = 0; var saS = 0; var sbS = 0; var cB = 0; var csB = 0
        var pollSt = 0; var pollCool = 0

        def rdA(v: Boolean, d: BigInt): Unit = {
          dut.io.dmaA.rsp.valid.poke(v.B); dut.io.dmaA.rsp.payload.data.poke(d.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B) }
        def rdB(v: Boolean, d: BigInt): Unit = {
          dut.io.dmaB.rsp.valid.poke(v.B); dut.io.dmaB.rsp.payload.data.poke(d.U)
          dut.io.dmaB.rsp.payload.err.poke(false.B) }
        def rdSA(v: Boolean, d: BigInt): Unit = {
          dut.io.dmaScaleA.get.rsp.valid.poke(v.B)
          dut.io.dmaScaleA.get.rsp.payload.data.poke(d.U)
          dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B) }
        def rdSB(v: Boolean, d: BigInt): Unit = {
          dut.io.dmaScaleB.get.rsp.valid.poke(v.B)
          dut.io.dmaScaleB.get.rsp.payload.data.poke(d.U)
          dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B) }

        while (!done && !errSeen && cycle < p.maxCycles) {
          // DMA-A read
          aS = rdCh("dmaA", aS, plan.expAAddrSeq,
            dut.io.dmaA.req.valid.peek().litToBoolean,
            dut.io.dmaA.req.payload.addr.peek().litValue.toLong, rdA, plan.beatAtA)
          // DMA-B read
          bS = rdCh("dmaB", bS, plan.expBAddrSeq,
            dut.io.dmaB.req.valid.peek().litToBoolean,
            dut.io.dmaB.req.payload.addr.peek().litValue.toLong, rdB, plan.beatAtB)
          // DMA-scaleA (should be idle for INT)
          saS = rdCh("dmaScaleA", saS, plan.expScaleAAddrSeq,
            dut.io.dmaScaleA.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong, rdSA, plan.wordAt)
          // DMA-scaleB (should be idle for INT)
          sbS = rdCh("dmaScaleB", sbS, plan.expScaleBAddrSeq,
            dut.io.dmaScaleB.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong, rdSB, plan.wordAt)
          // DMA-C write
          cB = wrChC("dmaC", cB, plan.expCAddrSeq,
            dut.io.dmaC.req.valid.peek().litToBoolean,
            dut.io.dmaC.req.payload.addr.peek().litValue.toLong,
            dut.io.dmaC.req.payload.data.peek().litValue,
            dut.io.dmaC.req.payload.strb.peek().litValue,
            v => dut.io.dmaC.rsp.valid.poke(v.B), cLog)
          // DMA-CScale write (should be idle for INT32 output)
          csB = wrCh("dmaCScale", csB, Seq.empty,
            dut.io.dmaCScale.get.req.valid.peek().litToBoolean,
            dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong,
            dut.io.dmaCScale.get.req.payload.data.peek().litValue,
            v => dut.io.dmaCScale.get.rsp.valid.poke(v.B), sLog)

          // STATUS poll
          val (ps, pc, st) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
          pollSt = ps; pollCool = pc
          st.foreach { s =>
            if (s.err) { errSeen = true; errCode = s.errCode
              println(f"[$TAG cycle=$cycle%5d] STATUS.err=1 errCode=${s.errCode}") }
            else if (s.done) { done = true
              println(f"[$TAG cycle=$cycle%5d] STATUS.done=1 A=$aS B=$bS C=$cB sA=$saS sB=$sbS") }
          }
          dut.clock.step(); cycle += 1
        }

        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        dut.io.cmd.valid.poke(false.B)

        println(s"[$TAG] Done: cycles=$cycle done=$done err=$errSeen; " +
          s"beats A=$aS/${plan.expAAddrSeq.size} B=$bS/${plan.expBAddrSeq.size} " +
          s"C=$cB/${plan.expCAddrSeq.size} sA=$saS sB=$sbS csC=$csB")

        // ---- Golden compare (INT-specific exact-match path). NOTE: the historical
        //      Plan.compare() histogram overflow for 32-bit output (nCodes = 1 << 32 = 1)
        //      is now fixed (#73: elemBits>=32 skips the histogram tally); this local
        //      path is kept for its INT exact-match semantics. ----
        if (done && !errSeen) {
          // 【#166 口径升级：strb 逐字节校验】解析走 Checked 变体，同时跑 lane 级审计
          //   （addr↔strb 双向对账、未选通 lane 零填充核查、BEAT-COUNT），违规直接
          //   计入 mismatchCnt ⟹ lane 级契约缺陷不再静默放行。期望拍数取金锚
          //   plan.expCAddrSeq.size（张量语义侧推导），不取硬件实收拍数。
          val parsed = HiveSimCommon.parseDenseCWriteLogChecked(
            cLog.toSeq, p.cFmt, m, n, totalN, cfg.cDmaExtW,
            expBeats = Some(plan.expCAddrSeq.size), expElems = Some(m * n))
          val hwBits = parsed.matrix
          println(s"[$TAG] (#166 LANE) ${parsed.laneSummary}")
          parsed.violations.take(16).foreach(v => println(s"[$TAG]   [#166 LANE] $v"))
          // (#170-W7) Separate element mismatch from lane violations (single unit each)
          val laneViolations = parsed.violationCount
          var mismatchCnt = 0
          var maxAbsErr = 0.0
          val details = mutable.ArrayBuffer[String]()
          for (mm <- 0 until m; nn <- 0 until n) {
            val ref = plan.goldenC(mm)(nn)
            val hw = decodeElem(p.cFmt, hwBits(mm)(nn), 127)
            val absErr = math.abs(hw - ref)
            if (absErr > maxAbsErr) maxAbsErr = absErr
            // INT output: exact match required (integer arithmetic, no rounding)
            if (hw != ref) {
              mismatchCnt += 1
              if (details.size < 16)
                details += f"C($mm,$nn): hw=$hw%.1f ref=$ref%.1f delta=$absErr%.1f " +
                  f"hwBits=0x${hwBits(mm)(nn)}%X"
            }
          }
          println(f"[$TAG] Compare: elemMismatch=$mismatchCnt/${m * n} laneViolations=$laneViolations maxAbsErr=$maxAbsErr%.3e")
          details.foreach(d => println(s"[$TAG]   MISMATCH $d"))
          cmpMismatch = mismatchCnt + laneViolations
          cmpElems = m * n
        }
      }
    } finally {
      copyWave(SUITE, hint, p.waveTarget)
    }

    // ---- Assertions (after waveform is saved) ----
    withClue(s"[$TAG] Should complete without STATUS.err: ") {
      errSeen should be(false)
    }
    withClue(s"[$TAG] Should reach STATUS.done within ${p.maxCycles} cycles: ") {
      done should be(true)
    }
    withClue(s"[$TAG] Six-channel address check (semantic model vs hardware): ") {
      addrBadCnt should be(0)
    }
    withClue(s"[$TAG] Scale channels must be idle for INT run: ") {
      sLog.size should be(0)
    }
    addrBad.foreach(s => println(s"[$TAG]   ADDR-DIFF $s"))
    withClue(s"[$TAG] C golden compare (zero mismatch required): ") {
      cmpMismatch should be(0)
    }
  }

  // ==========================================================================
  // Shared runner: negative case (errCode expected)
  // ==========================================================================
  private def runIntErrCase(m: Int, n: Int, k: Int,
      aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
      loadWMode: Boolean, expectedErrCode: Int, hint: String,
      cAddr: Long = C_BASE): Unit = {

    val p = DebugParams(
      m = m, n = n, k = k,
      aFmt = aFmt, bFmt = bFmt, cFmt = cFmt,
      loadWMode = loadWMode,
      aMxBlkSel = 0, bMxBlkSel = 0, cMxBlkSel = 0,
      addrModeSel = 0, rnd = 0, seed = 99L,
      strictAddr = true, maxCycles = 2000,
      waveTarget = s"sim/int/$hint.fst", testHint = hint
    )

    // Pre-flight: confirm hwErrCode predicts the expected code
    val predicted = hwErrCode(p, cfg.totalN, cfg.hasMx4, cAddr, cfg.cDmaExtW)
    require(predicted == Some(expectedErrCode),
      s"[$TAG] hwErrCode predicted $predicted but expected Some($expectedErrCode); params: ${p.banner}")

    println(s"[$TAG] Negative case: M=$m N=$n K=$k aFmt=$aFmt -> expect errCode=$expectedErrCode")

    var done = false
    var errSeen = false
    var errCode = -1
    var cWrites = 0
    var cycle = 0

    try {
      simulate(new HiveCore(cfg)) { dut =>
        // ---- Reset ----
        Seq(dut.io.dmaA.rsp, dut.io.dmaB.rsp).foreach { r =>
          r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaScaleA.get.rsp, dut.io.dmaScaleB.get.rsp).foreach { r =>
          r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaC.rsp, dut.io.dmaCScale.get.rsp).foreach { r =>
          r.valid.poke(false.B); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaA.req, dut.io.dmaB.req, dut.io.dmaC.req,
          dut.io.dmaScaleA.get.req, dut.io.dmaScaleB.get.req,
          dut.io.dmaCScale.get.req).foreach(_.ready.poke(true.B))
        dut.io.resp.ready.poke(true.B)
        dut.io.cmd.valid.poke(false.B)
        dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)

        // ---- Command helper ----
        def cmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
          dut.io.cmd.valid.poke(true.B); dut.io.cmd.payload.op.poke(op)
          dut.io.cmd.payload.reg0Addr.poke(r0.U); dut.io.cmd.payload.reg1Addr.poke(r1.U)
          dut.io.cmd.payload.data0.poke(d0.U); dut.io.cmd.payload.data1.poke(d1.U)
          dut.clock.step(); dut.io.cmd.valid.poke(false.B); dut.clock.step(2)
        }

        // ---- Configure registers (same illegal config) ----
        // (#164) 与 runIntGemm 同构的新写序列：reg1=n|k<<16、reg2/3/4=a/b/cAddr、reg5=控制字；
        //   旧写 reg6/reg7 那条已删（新布局下为 no-op）。本负向用例期望 errCode=8
        //   （K 非 kPerBeat 整除 / K=0）；若控制字写不进 reg5，aFmt/bFmt 会退化为 FP32
        //   ⟹ errCode 5 抢跑（优先级 6>5>4>3>0 的 last-connect 序），归因全失。
        cmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, m, n.toLong | (k.toLong << 16))
        cmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, A_BASE, B_BASE)
        cmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, cAddr, p.controlWord5)
        cmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SA_BASE, SB_BASE)
        cmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SC_BASE, 0L)

        // ---- EXECUTE ----
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
        dut.io.cmd.payload.reg0Addr.poke(0.U); dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U); dut.io.cmd.payload.data1.poke(0.U)
        dut.clock.step(); dut.io.cmd.valid.poke(false.B)

        // ---- Poll for err (no data feeding; hardware should reject immediately) ----
        var pollSt = 0; var pollCool = 0
        while (!done && !errSeen && cycle < p.maxCycles) {
          // Keep DMA responses idle
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
          dut.io.dmaC.rsp.valid.poke(false.B)
          dut.io.dmaCScale.get.rsp.valid.poke(false.B)
          // Count any dmaC write requests (should be zero)
          if (dut.io.dmaC.req.valid.peek().litToBoolean) cWrites += 1

          val (ps, pc, st) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
          pollSt = ps; pollCool = pc
          st.foreach { s =>
            if (s.err) { errSeen = true; errCode = s.errCode
              println(f"[$TAG cycle=$cycle%5d] STATUS.err=1 errCode=${s.errCode}") }
            else if (s.done) { done = true
              println(f"[$TAG cycle=$cycle%5d] STATUS.done=1 (unexpected!)") }
          }
          dut.clock.step(); cycle += 1
        }
        dut.io.cmd.valid.poke(false.B)
        println(s"[$TAG] ErrCase end: cycles=$cycle err=$errSeen errCode=$errCode done=$done cWrites=$cWrites")
      }
    } finally {
      copyWave(SUITE, hint, p.waveTarget)
    }

    // ---- Assertions ----
    withClue(s"[$TAG] Hardware should set STATUS.err for illegal K: ") {
      errSeen should be(true)
    }
    withClue(s"[$TAG] errCode should be $expectedErrCode: ") {
      errCode should be(expectedErrCode)
    }
    withClue(s"[$TAG] Hardware should NOT set done for rejected config: ") {
      done should be(false)
    }
    withClue(s"[$TAG] DMA-C should have zero write requests for rejected config: ") {
      cWrites should be(0)
    }
  }
}
