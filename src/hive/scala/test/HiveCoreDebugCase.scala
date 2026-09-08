/** HiveCoreDebugCase — 用户可传参的 HiveCore GEMM 调试用例。
  *
  * 用途：让用户用一条命令跑自己关心的 (M,N,K,格式,loadMode,MX 块大小,addrMode,rnd)
  * 组合，驱动 HiveCore 完成 GEMM，与软件 golden 逐元素对比并输出 mismatch 明细，
  * 便于定位「是硬件错还是我的参数/数据布局错」。
  *
  * ==========================================================================
  * 参数表（两条传入途径 + 文件顶部默认值，优先级：ConfigMap > JVM 系统属性 > 默认）
  *   途径 1（推荐）：sbt "testOnly HiveCoreDebugCase -- -Dm=64 -DaFmt=fp16"
  *   途径 2        ：sbt -Dm=64 "testOnly HiveCoreDebugCase"（JVM 系统属性）
  * -------------------------------------------------------------------------
  *   参数        -D 键       默认    取值范围                          硬件对应物
  *   M           m           32      >0                                regFile.m       (regs 0x00)
  *   N           n           32      >0                                regFile.n       (regs 0x01)
  *   K           k           32      >0；MX 需 K%(totalN*2)==0          regFile.k       (regs 0x02)
  *   aFmt        aFmt        fp16    fp32|fp16|bf16|mxe4m3|mxe5m2|      regFile.aFmt    (regs 0x07[3:0])
  *                                 |mxe2m1|int32|int16|int8|int4
  *                                 （别名 e4m3/e5m2/mxfp4/i8... 大小写不敏感）
  *   bFmt        bFmt        fp16    同 aFmt                            regFile.bFmt    (regs 0x07[7:4])
  *   cFmt        cFmt        fp32    同 aFmt                            regFile.cFmt    (regs 0x07[11:8])
  *   loadMode    loadW       0       0|1（MX 必须 1）                    regFile.loadWMode (regs 0x06[28])
  *   aMxBlockSize aMxBlk     32      8|16|32|64（等价 m8..m64 或 0..3）  regFile.aMxBlockSize (regs 0x06[23:22])
  *   bMxBlockSize bMxBlk     32      同上                               regFile.bMxBlockSize (regs 0x06[25:24])
  *   cMxBlockSize cMxBlk     8       同上（仅量化输出生效）              regFile.cMxBlockSize (regs 0x06[27:26])
  *   addrMode    addrMode    mat     mat|tile（tile 当前无 RTL 消费者）   regs 0x06[14:12]
  *   rnd         rnd         rne     rne|rtz|rdn|rup|rmm 或 0..4         regFile.rnd     (regs 0x07[13:12])
  *   seed        seed        1234    任意长整数（激励固定种子，可复现）    —（纯软件）
  *   strictAddr  strictAddr  0       0|1（1=地址对账逐拍致命断言）         —（纯软件）
  *   maxCycles   maxCycles   8000    >0（量化输出自动抬到 20000）          —（纯软件）
  *   wave        wave        sim/hivecore_debug.fst   任意路径            —（FST 产物）
  * -------------------------------------------------------------------------
  * 示例命令（均已验证可跑，前置 lz4 环境变量见 cmd.sh 注释）：
  *   export CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib
  *   sbt "testOnly HiveCoreDebugCase -- -DemitFst=1"
  *   sbt "testOnly HiveCoreDebugCase -- -DemitFst=1 -Dm=64 -Dn=32 -Dk=32 -Dseed=7"
  *   sbt "testOnly HiveCoreDebugCase -- -DemitFst=1 -DaFmt=mxe4m3 -DbFmt=mxe4m3 \
  *        -DcFmt=fp32 -DloadW=1 -DaMxBlk=32 -DbMxBlk=32 -Dwave=sim/debug/mx.fst"
  *   # 单跑某一组：ScalaTest 的 -z 子串过滤同样放在 -- 之后
  *   sbt "testOnly HiveCoreDebugCase -- -z MX -DemitFst=1"
  *
  * 【重要】上述自定义键已在本类体内用 HasCliOptions.CliOption.string 逐个注册：
  * ChiselSim 对 configMap 做白名单校验，未注册的 -Dkey 会抛
  * "illegal ChiselSim ScalaTest option '<key>'" 并使整组用例失败。新增键时务必同步注册。
  *
  * ==========================================================================
  * 判定策略（调试用例与回归用例的关键差异）：地址对账默认**非致命收集**——
  * 逐拍把 hw 期望差异记进报告，跑完再统一断言，避免第一拍就 fail 导致拿不到
  * C 矩阵对比与波形；`-DstrictAddr=1` 切回逐拍致命断言。数据一律按 req.addr
  * 从字节内存镜像取（内存保真），杜绝「按序供数掩盖地址错位」。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Args, Status}
import java.nio.file._

class HiveCoreDebugCase extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim with HiveStatusPollApi {

  import HiveCoreDebugCase._

  // --------------------------------------------------------------------------
  // 【必须】ChiselSim 的 HasCliOptions 对 ScalaTest configMap 做**白名单校验**：
  // 任何未注册的 -Dkey 会在 simulate 拍抛 "illegal ChiselSim ScalaTest option"
  // 并连带打印全部合法选项，导致整组用例失败。故此处把本用例的全部自定义键注册
  // 为 CliOption.string（simple 选项：不改动 chisel/firtool/common/backend 设置），
  // 值仍交由 HiveDebugModel 的解析器做大小写不敏感匹配与清晰非法值报错。
  // --------------------------------------------------------------------------
  private val debugCliKeys: Seq[(String, String)] = Seq(
    "m" -> "矩阵 M（行数），默认 32",
    "n" -> "矩阵 N（列数），默认 32",
    "k" -> "矩阵 K（归约长度），默认 32；MX 需 K%(totalN*2)==0",
    "aFmt" -> "A 数据格式：fp32|fp16|bf16|mxe4m3|mxe5m2|mxe2m1|int32|int16|int8|int4（大小写不敏感，含别名）",
    "bFmt" -> "B 数据格式，取值同 aFmt",
    "cFmt" -> "C 输出格式，取值同 aFmt（量化输出 mxe4m3/mxe5m2/mxe2m1 会写回 scaleC）",
    "loadW" -> "loadWMode：0=B 为 K×N 行主序，1=B 为 N×K 转置（MX 必须 1）",
    "aMxBlk" -> "aMxBlockSize：8|16|32|64（等价 m8..m64 或 0..3），决定 scaleA 地址步进 K/blk",
    "bMxBlk" -> "bMxBlockSize：8|16|32|64，决定 scaleB 地址步进 K/blk",
    "cMxBlk" -> "cMxBlockSize：8|16|32|64，仅量化输出生效（须 <= totalN 且整除）",
    "addrMode" -> "地址模式 mat|tile（tile 当前无 RTL 消费者，仅 mat 可用）",
    "rnd" -> "舍入模式 rne|rtz|rdn|rup|rmm 或 0..4",
    "seed" -> "激励随机种子（固定种子可复现），默认 1234",
    "strictAddr" -> "0=地址对账非致命收集（默认），1=逐拍致命断言",
    "maxCycles" -> "仿真最大拍数，默认 8000（量化输出自动抬到 20000）",
    "wave" -> "FST 波形复制目标路径，默认 sim/hivecore_debug.fst")
  debugCliKeys.foreach { case (k, h) => addOption(HasCliOptions.CliOption.string(k, h)) }

  // ScalaTest ConfigMap（`--` 之后的 -Dkey=value）由 run 覆写捕获
  private var cliMap: Map[String, Any] = Map.empty

  override def run(testName: Option[String], args: Args): Status = {
    cliMap = args.configMap.toMap
    super.run(testName, args)
  }

  /** 参数取值三途径合并：ConfigMap > JVM 系统属性 > 文件顶部默认块 */
  private def opt(key: String, default: String): String =
    cliMap.get(key).map(_.toString)
      .orElse(Option(System.getProperty(key)))
      .getOrElse(default)

  /** 由 -D / 系统属性 / 默认块解析出本次运行的完整参数集 */
  private def paramsFrom(waveDefault: String, hint: String): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = parseInt("m", opt("m", DEF_M)),
      n = parseInt("n", opt("n", DEF_N)),
      k = parseInt("k", opt("k", DEF_K)),
      aFmt = parseFmt("aFmt", opt("aFmt", DEF_A_FMT)),
      bFmt = parseFmt("bFmt", opt("bFmt", DEF_B_FMT)),
      cFmt = parseFmt("cFmt", opt("cFmt", DEF_C_FMT)),
      loadWMode = parseBool("loadW", opt("loadW", DEF_LOAD_W)),
      aMxBlkSel = parseBlkSel("aMxBlk", opt("aMxBlk", DEF_A_BLK)),
      bMxBlkSel = parseBlkSel("bMxBlk", opt("bMxBlk", DEF_B_BLK)),
      cMxBlkSel = parseBlkSel("cMxBlk", opt("cMxBlk", DEF_C_BLK)),
      addrModeSel = parseAddrMode("addrMode", opt("addrMode", DEF_ADDR_MODE)),
      rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = parseBool("strictAddr", opt("strictAddr", DEF_STRICT_ADDR)),
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", waveDefault),
      testHint = hint)
  }

  behavior of "HiveCore Debug GEMM"

  // ---------- 组 (a)：用户传参入口（默认 = FP16 M=N=K=32 loadW=0 cFmt=fp32） ----------
  it should "run debug GEMM from user parameters" in {
    runDebug(paramsFrom("sim/hivecore_debug.fst", "should-run-debug-GEMM-from-user-parameters"))
  }

  // ---------- 组 (b)：内置 INT 组（INT16 × INT16 → INT32） ----------
  it should "run builtin INT group debug GEMM" in {
    runDebug(builtinIntParams())
  }

  // ---------- 组 (c)：内置 MXE4M3 组（含 mxBlockSize 生效验证） ----------
  it should "run builtin MX group debug GEMM" in {
    runDebug(builtinMxParams())
  }

  // ==========================================================================
  // 内置组参数（用户可用 -D 覆盖对应键；此处显式给出组内固定值）
  // ==========================================================================
  private def builtinIntParams(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = parseInt("m", opt("m", DEF_M)), n = parseInt("n", opt("n", DEF_N)),
      k = parseInt("k", opt("k", DEF_K)),
      aFmt = DataFormat.INT16, bFmt = DataFormat.INT16, cFmt = DataFormat.INT32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = parseBool("strictAddr", opt("strictAddr", DEF_STRICT_ADDR)),
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_debug_int.fst"),
      testHint = "should-run-builtin-INT-group-debug-GEMM")
  }

  private def builtinMxParams(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = parseInt("m", opt("m", DEF_M)), n = parseInt("n", opt("n", DEF_N)),
      k = 32,   // MX 要求 K%(totalN*2)==0，默认 32 = 单 scale 块单 pass
      aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
      loadWMode = true,
      // 默认 blk = 32 = kPerBeat（MX8 一个 kTile = 32 K = 单 pass），此时 DMA 的
      // scale 地址步进（rowOff=K/blk、colTileOff=kLoadNum/blk）与 executor 每 beat
      // pop 一个 scale 广播全行的消费粒度自洽 → 数值全绿。
      // 实测对照：-DaMxBlk=16 时六通道地址对账仍 0 差异（证明 mxBlockSize 确实
      // 改变硬件 scale 地址步进 = 参数生效），但 C 出现 640/1024 失配且 relErr 恒为
      // 2 的幂（2^-1/2^+1/2^+2），指向阶段 2 在途 executor/CalcConfig 在
      // blk < kPerBeat 时 scale 广播粒度未细化到子块（一个 beat 内 2 个 16-K 块共用
      // 1 个 pop）→ 属在途 RTL 局限，非 harness 缺陷。可用 -DaMxBlk=16 复现。
      aMxBlkSel = parseBlkSel("aMxBlk", opt("aMxBlk", "32")),
      bMxBlkSel = parseBlkSel("bMxBlk", opt("bMxBlk", "32")),
      cMxBlkSel = parseBlkSel("cMxBlk", opt("cMxBlk", DEF_C_BLK)),
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = parseBool("strictAddr", opt("strictAddr", DEF_STRICT_ADDR)),
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_debug_mx.fst"),
      testHint = "should-run-builtin-MX-group-debug-GEMM")
  }

  // ==========================================================================
  // 共享运行器
  // ==========================================================================
  private def runDebug(p: HiveDebugModel.DebugParams): Unit = {
    import HiveDebugModel._

    // 三组共用同一编译期配置（默认全格式 + 运行期 isMx 门控），
    // 使 hasFp/hasInt/hasMx 同时为真、dmaScaleA/B 与 dmaCScale 端口恒暴露。
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,          // totalN = 16
      aW = 16, bW = 16, cW = 40,
      aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 64)

    validate(p, cfg.totalN)
    val plan = new Plan(p, cfg)
    plan.prepare()

    val tag = TAG
    println(s"[$tag] ================= HiveCoreDebugCase =================")
    println(s"[$tag] 生效参数: ${p.banner}")
    println(s"[$tag] 几何: totalN=${cfg.totalN} aKTiles=${plan.aKTiles} bKTiles=${plan.bKTiles} " +
      s"nTiles=${plan.nTiles} beatsPerRow=${plan.beatsPerRow} aExtW=${cfg.aExtW} bExtW=${cfg.bExtW} " +
      s"cDmaExtW=${cfg.cDmaExtW} aEb=${plan.aEb} bEb=${plan.bEb}")
    println(s"[$tag] 期望 beat 数: A=${plan.expAAddrSeq.size} B=${plan.expBAddrSeq.size} " +
      s"C=${plan.expCAddrSeq.size} scaleA=${plan.expScaleAAddrSeq.size} scaleB=${plan.expScaleBAddrSeq.size} " +
      s"scaleC=${plan.expCScaleAddrSeq.size}")
    println(s"[$tag] 内存镜像有效字节数=${plan.memByteCount}（DMA 按 req.addr 取数，内存保真）")

    // mxBlockSize 生效自检（纯软件）：scale 行步进必须随 blkSel 变化 = K/blk
    if (isMxAny(p.aFmt)) {
      val strides = (0 to 3).map(sel => (8 << sel, p.k / (8 << sel)))
      println(s"[$tag] mxBlockSize 生效自检 scaleA (blk,行步进=K/blk): " + strides.mkString(" "))
      strides.map(_._2).distinct.size should be > 1
      println(s"[$tag] 本次生效 aMxBlk=${p.aMxBlk} → scaleARowOff=${plan.saRowOff} " +
        s"scaleAColTileOff=${plan.saColOff}；bMxBlk=${p.bMxBlk} → scaleBRowOff=${plan.sbRowOff} " +
        s"scaleBColTileOff=${plan.sbColOff}")
    }

    val cfgErr = precheckConfigErr(p, cfg.totalN)
    cfgErr.foreach(c => println(s"[$tag] 预检：该组合硬件应直接 config-err（errCode=$c），走负向路径"))

    val maxCycles = if (isMxAny(p.cFmt)) math.max(p.maxCycles, 20000) else p.maxCycles

    try {
      simulate(new HiveCore(cfg)) { dut =>

        val cWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
        val scaleWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()
        val addrBad = scala.collection.mutable.ArrayBuffer[String]()
        var addrBadCnt = 0
        var aSent = 0; var bSent = 0; var saSent = 0; var sbSent = 0
        var cBeats = 0; var csBeats = 0

        // 地址对账：默认非致命收集（跑完统一报告），strictAddr=1 时逐拍致命
        def chkAddr(chan: String, idx: Int, actual: Long, exp: Seq[Long]): Unit = {
          if (idx < exp.size && actual != exp(idx)) {
            addrBadCnt += 1
            if (addrBad.size < 16)
              addrBad += f"$chan beat#$idx hw=0x$actual%X exp=0x${exp(idx)}%X delta=${actual - exp(idx)}%d"
            if (p.strictAddr) withClue(f"$chan beat#$idx addr mismatch (hw=0x$actual%X exp=0x${exp(idx)}%X): ") {
              actual should be(exp(idx))
            }
          }
        }

        // ===== Reset =====
        dut.reset.poke(true.B)
        dut.io.cmd.valid.poke(false.B)
        dut.io.resp.ready.poke(true.B)
        dut.io.dmaA.req.ready.poke(true.B)
        dut.io.dmaA.rsp.valid.poke(false.B)
        dut.io.dmaA.rsp.payload.data.poke(0.U)
        dut.io.dmaA.rsp.payload.err.poke(false.B)
        dut.io.dmaB.req.ready.poke(true.B)
        dut.io.dmaB.rsp.valid.poke(false.B)
        dut.io.dmaB.rsp.payload.data.poke(0.U)
        dut.io.dmaB.rsp.payload.err.poke(false.B)
        dut.io.dmaC.req.ready.poke(true.B)
        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaC.rsp.payload.err.poke(false.B)
        dut.io.dmaScaleA.get.req.ready.poke(true.B)
        dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
        dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
        dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
        dut.io.dmaScaleB.get.req.ready.poke(true.B)
        dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
        dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
        dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
        dut.io.dmaCScale.get.req.ready.poke(true.B)
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        dut.io.dmaCScale.get.rsp.payload.err.poke(false.B)
        dut.clock.step(3)
        dut.reset.poke(false.B)
        dut.clock.step(2)

        def sendCmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long): Unit = {
          dut.io.cmd.valid.poke(true.B)
          dut.io.cmd.payload.op.poke(op)
          dut.io.cmd.payload.reg0Addr.poke(r0.U)
          dut.io.cmd.payload.reg1Addr.poke(r1.U)
          dut.io.cmd.payload.data0.poke(d0.U)
          dut.io.cmd.payload.data1.poke(d1.U)
          dut.clock.step()
          dut.io.cmd.valid.poke(false.B)
          dut.clock.step(2)
        }

        // ===== 配置寄存器 =====
        sendCmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, p.m, p.n)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, p.k, A_BASE)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, p.controlWord6, p.controlWord7)
        sendCmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SA_BASE, SB_BASE)
        sendCmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SC_BASE, 0L)
        println(f"[$tag] regs 已写入: regs6=0x${p.controlWord6}%08X regs7=0x${p.controlWord7}%08X")

        // ===== EXECUTE =====
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
        dut.io.cmd.payload.reg0Addr.poke(0.U)
        dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U)
        dut.io.cmd.payload.data1.poke(0.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)

        var done = false
        var errSeen = false
        var errCode = -1
        var cycle = 0
        var pollSt = 0
        var pollCool = 0

        while (!done && !errSeen && cycle < maxCycles) {
          // --- DMA-A：req.valid 拍按地址供数 + 地址对账 ---
          if (dut.io.dmaA.req.valid.peek().litToBoolean && aSent < plan.expAAddrSeq.size) {
            val addr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaA", aSent, addr, plan.expAAddrSeq)
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(plan.beatAtA(addr).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            aSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }

          // --- DMA-B（权重）---
          if (dut.io.dmaB.req.valid.peek().litToBoolean && bSent < plan.expBAddrSeq.size) {
            val addr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaB", bSent, addr, plan.expBAddrSeq)
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(plan.beatAtB(addr).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            bSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
          }

          // --- DMA-scaleA（8-bit/beat）---
          if (dut.io.dmaScaleA.get.req.valid.peek().litToBoolean && saSent < plan.expScaleAAddrSeq.size) {
            val addr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaScaleA", saSent, addr, plan.expScaleAAddrSeq)
            dut.io.dmaScaleA.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(plan.byteAt(addr).U)
            dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
            saSent += 1
          } else {
            dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
          }

          // --- DMA-scaleB（8-bit/beat）---
          if (dut.io.dmaScaleB.get.req.valid.peek().litToBoolean && sbSent < plan.expScaleBAddrSeq.size) {
            val addr = dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaScaleB", sbSent, addr, plan.expScaleBAddrSeq)
            dut.io.dmaScaleB.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(plan.byteAt(addr).U)
            dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
            sbSent += 1
          } else {
            dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
          }

          // --- DMA-C（结果写回）：记录 (addr,data)，逐拍地址对账 ---
          dut.io.dmaC.req.ready.poke(true.B)
          if (dut.io.dmaC.req.valid.peek().litToBoolean) {
            val addr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
            chkAddr("dmaC", cBeats, addr, plan.expCAddrSeq)
            cWriteLog += ((addr, dut.io.dmaC.req.payload.data.peek().litValue))
            cBeats += 1
            dut.io.dmaC.rsp.valid.poke(true.B)
          } else {
            dut.io.dmaC.rsp.valid.poke(false.B)
          }

          // --- DMA-CSCALE（量化输出块 scale 写回）---
          dut.io.dmaCScale.get.req.ready.poke(true.B)
          if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) {
            val addr = dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong
            if (isMxAny(p.cFmt)) chkAddr("dmaCScale", csBeats, addr, plan.expCScaleAddrSeq)
            scaleWriteLog += ((addr, dut.io.dmaCScale.get.req.payload.data.peek().litValue))
            csBeats += 1
            dut.io.dmaCScale.get.rsp.valid.poke(true.B)
          } else {
            dut.io.dmaCScale.get.rsp.valid.poke(false.B)
          }

          // --- 完成/错误判定：STATUS(0x08) 轮询 ---
          val (stNext, coolNext, statusOpt) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
          pollSt = stNext
          pollCool = coolNext
          statusOpt.foreach { st =>
            if (st.err) {
              errSeen = true
              errCode = st.errCode
              println(f"[$tag cycle=$cycle%5d] STATUS.err=1 errCode=${st.errCode} (done=${st.done})")
            } else if (st.done) {
              done = true
              println(f"[$tag cycle=$cycle%5d] STATUS.done=1 A=$aSent B=$bSent C=$cBeats " +
                s"sA=$saSent sB=$sbSent sC=$csBeats")
            }
          }

          dut.clock.step()
          cycle += 1
        }

        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        dut.io.cmd.valid.poke(false.B)

        // ================= 报告 =================
        println(s"[$tag] ---------------- 运行报告 ----------------")
        println(s"[$tag] cycles=$cycle / maxCycles=$maxCycles  done=$done err=$errSeen(errCode=$errCode)")
        println(s"[$tag] beat 计数: A=$aSent/${plan.expAAddrSeq.size} B=$bSent/${plan.expBAddrSeq.size} " +
          s"C=$cBeats/${plan.expCAddrSeq.size} scaleA=$saSent/${plan.expScaleAAddrSeq.size} " +
          s"scaleB=$sbSent/${plan.expScaleBAddrSeq.size} scaleC=$csBeats/${plan.expCScaleAddrSeq.size}")
        println(s"[$tag] 地址对账不一致拍数=$addrBadCnt（strictAddr=${p.strictAddr}）")
        addrBad.foreach(s => println(s"[$tag]   ADDR-DIFF $s"))
        if (addrBadCnt > 0)
          println(s"[$tag] 提示：地址对账不一致会使按地址供数取到非预期数据，" +
            s"下方 C 元素失配大概率由此引起（属硬件地址公式问题，非 harness 问题）")

        cfgErr match {
          case Some(expCode) =>
            // 负向路径：期望硬件直接 config-err、不发 done
            withClue(s"[$tag] 期望 config-err errCode=$expCode：") { errSeen should be(true) }
            withClue(s"[$tag] errCode 应为 $expCode：") { errCode should be(expCode) }
            withClue(s"[$tag] config-err 时不得发 done：") { done should be(false) }
            println(s"[$tag] 负向路径通过：errCode=$errCode，无 done，C 写回拍=$cBeats")

          case None =>
            if (!done) {
              println(s"[$tag] WARNING: 超时未 done（cycle=$cycle），仍输出已有 C 数据对比")
              plan.printRows(cWriteLog.toSeq, 4)
              fail(s"[$tag] 超时：$maxCycles 拍内未观测到 STATUS.done（A=$aSent B=$bSent C=$cBeats）")
            }
            withClue(s"[$tag] 正常路径不应出现 STATUS.err：") { errSeen should be(false) }
            withClue(s"[$tag] beat 计数应全部对齐：") {
              (aSent, bSent, cBeats, saSent, sbSent) should be(
                (plan.expAAddrSeq.size, plan.expBAddrSeq.size, plan.expCAddrSeq.size,
                  plan.expScaleAAddrSeq.size, plan.expScaleBAddrSeq.size))
            }
            if (!isMxAny(p.cFmt))
              withClue(s"[$tag] 非量化输出时 dmaCScale 必须静默：") { csBeats should be(0) }

            val cmp = plan.compare(cWriteLog.toSeq, scaleWriteLog.toSeq)
            println(f"[$tag] C 对比: ${cmp.elems} 元素, mismatch=${cmp.mismatch}, " +
              f"maxRelErr=${cmp.maxRelErr}%.3e, maxAbsErr=${cmp.maxAbsErr}%.3e")
            cmp.details.foreach(s => println(s"[$tag]   MISMATCH $s"))
            plan.printRows(cWriteLog.toSeq, 4)

            withClue(s"[$tag] 地址对账（语义模型 vs 硬件）：") { addrBadCnt should be(0) }
            withClue(s"[$tag] C 逐元素对比 golden：") { cmp.mismatch should be(0) }
            println(s"[$tag] PASS: ${cmp.elems}/${cmp.elems} 元素全部匹配，地址对账全绿")
        }
      }
    } finally {
      // 波形须在 simulate 块退出（verilator flush/close trace.fst）后转存；置于
      // finally 保证断言失败路径同样能拿到波形。源路径按 test-name 提示定位，
      // 未命中则回落「最近修改的 trace.fst」，对 mangle 规则变化鲁棒。
      copyWave("HiveCoreDebugCase", p.testHint, p.waveTarget)
    }
  }
}

object HiveCoreDebugCase {
  // ==========================================================================
  // 文件顶部默认参数块（途径 3）：无 -D / 无系统属性时生效。
  // 全部为字符串，与 -D 传入值走同一套解析器（大小写不敏感、非法值清晰报错），
  // 保证「默认值」与「用户传参」行为完全一致。
  // ==========================================================================
  val DEF_M: String = "32"
  val DEF_N: String = "32"
  val DEF_K: String = "32"
  val DEF_A_FMT: String = "fp16"
  val DEF_B_FMT: String = "fp16"
  val DEF_C_FMT: String = "fp32"
  val DEF_LOAD_W: String = "0"
  val DEF_A_BLK: String = "32"
  val DEF_B_BLK: String = "32"
  val DEF_C_BLK: String = "8"
  val DEF_ADDR_MODE: String = "mat"
  val DEF_RND: String = "rne"
  val DEF_SEED: String = "1234"
  val DEF_STRICT_ADDR: String = "0"
  val DEF_MAX_CYCLES: String = "8000"
}
