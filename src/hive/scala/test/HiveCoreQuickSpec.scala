/** HiveCoreQuickSpec — 「一个函数跑一组参数」的极简 HiveCore GEMM 调试用例。
  * 用法：只改下方【唯一一处 ★★★ runCase(...) 调用】，然后重跑裸命令（不带任何 -D）：
  *   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreQuickSpec"
  * CPATH/LIBRARY_PATH 是硬需求（Verilator 5.050 FST writer 依赖 lz4 头）。波形始终开启（复用
  * HiveCoreParamDebugSpec.scala 的 HiveAlwaysFstSim trait）；本文件不注册任何 -D 键，避开 ChiselSim
  * HasCliOptions 白名单坑。只做薄封装：内存镜像 / 六通道期望地址 / golden / compare 全复用 HiveDebugModel，
  * STATUS 轮询复用 HiveStatusPollApi，供数范式与 HiveCoreDebugCase.runDebug 同构（那两个 runner 都是
  * private、无法跨文件复用，故按同一范式在此收敛成单个函数）。
  *
  * ══ 三条架构约束（自己调 cross 配置必撞）══
  * 【一】v1 合法【输入配对】只有两类：双 MX（MXE4M3/MXE5M2 互组）、双非 MX 浮点（FP16/BF16 互组）。A=MX×B=非MX
  *   数学不可实现；A=非MX×B=MX 需 executor 偶奇重排、v1 禁止留 v2；两类非法组合硬件用 errCode3 拦。★ 只管输入
  *   侧、【不限制输出 cFmt】——「非 MX 输入 → mxfp8 量化输出」合法（C 侧量化消费 fp32 累加器）。
  * 【二】MX 输入强制 loadWMode=1（否则 errCode4）。故 loadW 默认 None = 按 isMx(aFmt)||isMx(bFmt) 自动推导。
  * 【三】addrMode 当前【全 RTL 零消费者】：只写 reg6[14:12] + 回读对账、不参与 golden；内存镜像与六路期望地址
  *   序列一律按 MAT 语义生成，传 TILE 不改变任何数值结果（运行时会打印这句提示，别在波形里白找）。
  *
  * ══ errCode 实测更正（与常见 errCode 表不符，以 RTL 为准）══
  * HiveCoreCalcConfig 把 a/b/cMxBlockSize 三违约【合并】为单一 errMXLargerThanKLoadNum、errCode 恒置 0，故【errCode
  * 1 与 2 永不可达】（QuantSpec「blk32 over totalN」期望 2 实得 0 的红项根因，#124 禁动区）；且五个 when 平级
  * last-connect、后写覆盖前写 → 真实优先级 6>5>4>3>0（与 precheckConfigErr 的 0>1>2>3>4>5 相反、后者不含 6）。
  *
  * ══ 历史缺陷留痕（#133 已修复，本段为知识保留，原「已知不可用区间」段已删）══
  * dmaC 写回地址曾在多 tile 边界出错（sNEXT_N 三角累积增量式）：nt≥1 块首址实得
  * base+(M-1)*rowOff+colOff（FP32/M=32/N=32 下 0x200FC0）、正确值 base+colOff（0x200040）。
  * 当时金锚 cAddrSeqDense 是同一缺陷 RTL 的闭式反向拟合，逐拍地址硬断言退化为「RTL 与镜像
  * 自比」恒真假绿、全部既有套件放行。★ 正确触发条件【nTiles≥2 且 m≥2】，与 mTiles 无关——
  * 本段原文「凡 mTiles>1 或 nTiles>1」是错误表述：C 地址序列不含 mTiles 参数、WrOnly 也无
  * mTile 循环（反例：M=32/N=16 → mTiles=2 但 nTiles=1，修复前后都绿）。
  * 【#136-KS1 补正：m≥2 是必要条件，不可省】m==1 ∧ nTiles==2 是三角化回退的【可证明
  * 盲区】——三角式一阶项 nt×(m−1)×rowOff 在 m==1 恒为 0，二阶项系数 nt×(nt+1)/2 在
  * nt=1 恰为 1（与绝对式 nt 的系数在 nt=1 也恰为 1，两者只在 nt≥2 才分歧：3 vs 2），
  * 故 nTiles==2 时两式产出逐拍完全相同的序列，任何不变式都区分不了。金锚
  * cAddrSeqDense 的两条 require 因此覆盖不到 -Dm=1 -Dn=32（合法配置）。
  * 现状：RTL 侧 sNEXT_N 已改「基址+轮偏移」线性模型（curAddr := io.regFile.cAddr + colAddr，
  * 与 HiveCoreDmaScaleWr 同构）；金锚侧 cAddrSeqDense 已改张量语义绝对式
  * base + nt*cColOff + row*cRowOff + b*cBeatAddrStep，并内置两条 require 自证（nTiles≥2/≥3
  * 块首拍不变式，【金锚构造期】即炸——即 Scala 测试体求值、调用该函数之时，早于 simulate，
  * 既非 scalac 编译期亦非 Chisel elaboration 期；任何三角化回退都会被拦截）。永久护栏用例见
  * HiveCoreDebugCase 组(f)：M32 N48 K16 FP16→FP32（nTiles=3、strictAddr 硬编码 true）。
  * 产物：波形 sim/debug/quick.fst；失败/超时另落 sim/debug/quick_fail.txt|_A.hex|_B.hex（sim/ 已 gitignore）。 */
import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._
import scala.collection.mutable

class HiveCoreQuickSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveAlwaysFstSim with HiveStatusPollApi {
  import HiveDebugModel._
  private val TAG = "QUICK"; private val SUITE = "HiveCoreQuickSpec"

  // ══════════════════════════════════════════════════════════════════════════
  // ★★★ 你只需要改这一处调用，改完重跑：
  //   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreQuickSpec"
  // loadW/seed/strictAddr/maxCycles/wave 都有默认值，不写就是默认（见 runCase 签名）。
  // 默认这组 M=N=K=32 FP16→FP16 的 C 数据是 1024/1024 逐位匹配的。【#131 收尾裁定（选项 A）保持本默认
  // 调用不动】：它曾在金锚未对齐期于 dmaC 地址对账上报假红（nTiles=2 触发三角累积缺陷），#133 金锚对齐后
  // 恢复全绿——它是仓库里唯一每天可见的该类缺陷信号灯，勿改成 16（单 tile 会失去信号价值）。
  //
  // 【#136-KS7 几何 vs N 依赖性速查（改上方任一参数前先读）】三条通道的地址几何【全部】
  // 随 N 变化，不存在「B 权重与 N 无关」这类捷径：
  //   · B 通道：行步进 bRowAddressOffset 在 loadWMode=0（垂直加载）分支 = regFile.n×bEb
  //     （FP16 → n×2），【正比于 N】；列步进 bColTileAddressOffset = totalN×elemsPerLane(bFmt)×bEb
  //     与 N 无关（★#124 ① 起为运行期量；FP16 → totalN×1×2 = 32，与旧式 totalN×(bW/8) 同值），
  //     但轮数 nTilesB = ceil(N/totalN) 直接由 N 决定 ⇒ B 总拍数 = nTilesB × bKTiles × totalN
  //     随 N 变。loadWMode=1 分支的 bRowAddressOffset 改按 K 派生（见 HiveCoreCalcConfig），
  //     此时「正比于 N」不成立、但 nTilesB 仍随 N 变，故结论不变。
  //   · A 通道：aRowAddressOffset = regFile.k×aEb 与 N 无关（★同为运行期量，FP16 → k×2），但总拍数 =
  //     nTiles × aKTiles × M，其中 nTiles = ceil(N/totalN) ⇒ 仍随 N 变。
  //   · C 通道：cRowAddressOffset = N×cEb【正比于 N】，nTiles 亦随 N 变。
  //   ⇒ 单独改 n 会同时改动 A/B/C 三条序列与三组拍数，不能当单变量对照用；要做单变量
  //     对照请参考 HiveCoreDebugCase 的 guard122/guard133 组（各自明示了变量与非变量）。
  // 【#136 实测记录（前提矛盾项，供后续核对）】#134 评审 KS7 指认本文件此处原文含
  //   「B 权重与 N 无关」一句，#136 实施时全仓 grep（含 "与 N 无关"/"与N无关"/"N 无关"）
  //   【0 命中】，且本文件未被 git 跟踪（不在 HEAD 中）故无法查历史版本。据此判断该句
  //   要么已在 #131/#133 期间被改写掉、要么评审引用有误。上方速查表按裁定的【意图】
  //   （给出正确的 N 依赖性表述）落地，不针对任何现存错误原文。
  // ══════════════════════════════════════════════════════════════════════════
  runCase(m = 32, n = 32, k = 32, aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.MXE4M3,
          aBlk = 32, bBlk = 32, cBlk = 16, addrMode = AddrMode.MAT)

  /** 编译期配置与 HiveCoreDebugCase / HiveCoreParamDebugSpec 逐字一致（全格式；totalN=16；cDmaExtW 默认 256） */
  private val cfg = HiveCoreConfig(arrayN = 8, clusterM = 2, aW = 16, bW = 16, cW = 40,
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048, scaleBufferDepth = 64)
  private val rnd: RoundingMode.Type = RoundingMode.RNE   // 舍入模式（不在主签名里，要换就改这一行）
  /** blk(用户面 Int) → MXBlockSize sel：表逐条由枚举成员构造，blk 取定义式 (8 << sel)，不写 sel 字面量（枚举重排也不错位） */
  private val blkToSel: Map[Int, Int] = Seq(MXBlockSize.M8, MXBlockSize.M16, MXBlockSize.M32, MXBlockSize.M64)
    .map(e => (8 << e.litValue.toInt) -> e.litValue.toInt).toMap
  private def selOf(w: String, blk: Int): Int = blkToSel.getOrElse(blk, throw new IllegalArgumentException(
    s"[$TAG] $w=$blk 非法：MX 块大小只能取 ${blkToSel.keys.toSeq.sorted.mkString(" / ")}"))

  /** 唯一入口：注册一个 ScalaTest 用例。改顶部调用的参数、重跑即可。 */
  def runCase(m: Int, n: Int, k: Int, aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
              aBlk: Int, bBlk: Int, cBlk: Int, addrMode: AddrMode.Type,
              loadW: Option[Int] = None,   // None = 按 isMx(aFmt)||isMx(bFmt) 自动推导（MX 强制 1，约束二）
              seed: Long = 1234L,          // 激励随机种子（固定可复现）
              strictAddr: Boolean = true,  // true = 地址对账失配判失败（仍是收集-后断言，一次看全所有失配点）
              maxCycles: Long = 20000L,    // 仿真拍数上限（量化输出内部再抬到 >= 20000）
              wave: String = "sim/debug/quick.fst"): Unit = {
    val lw = loadW.getOrElse(if (isMxAny(aFmt) || isMxAny(bFmt)) 1 else 0)
    it should s"run M=$m N=$n K=$k $aFmt x $bFmt -> $cFmt (blk $aBlk/$bBlk/$cBlk, addrMode=$addrMode)" in {
      runQuick(DebugParams(m = m, n = n, k = k, aFmt = aFmt, bFmt = bFmt, cFmt = cFmt, loadWMode = lw != 0,
        aMxBlkSel = selOf("aBlk", aBlk), bMxBlkSel = selOf("bBlk", bBlk), cMxBlkSel = selOf("cBlk", cBlk),
        addrModeSel = addrMode.litValue.toInt, rnd = rnd.litValue.toInt, seed = seed, strictAddr = strictAddr,
        maxCycles = maxCycles.toInt, waveTarget = wave, testHint = s"run-M-$m-N-$n-K-$k")) }
  }

  // ══ ① 运行前配置体检：镜像 HiveCoreCalcConfig 的 errComb/errCodeComb 裸块（RTL 真实口径）══
  private def kLoadNum(f: DataFormat.Type): Int = if (isMx8(f)) cfg.totalN * 2 else cfg.totalN * 4  // 镜像 getKLoadNum
  /** Some(code) = 硬件会在 EXECUTE 后直接置 STATUS.err、不进计算。优先级按 RTL 平级 when 的 last-connect 序 */
  private def hwErrCode(p: DebugParams): Option[Int] = {
    val aMx = isMxAny(p.aFmt); val bMx = isMxAny(p.bFmt); var e = Option.empty[Int]
    if ((aMx && p.aMxBlk > kLoadNum(p.aFmt)) || (bMx && p.bMxBlk > kLoadNum(p.bFmt)) ||
        (isMxAny(p.cFmt) && p.cMxBlk > cfg.totalN)) e = Some(0)   // RTL 把三个 blk 违约合并成单一 errCode 0
    if (aMx != bMx) e = Some(3)
    if ((aMx || bMx) && !p.loadWMode) e = Some(4)
    if (p.aFmt == DataFormat.FP32 || p.bFmt == DataFormat.FP32) e = Some(5)
    if (p.cFmt == DataFormat.MXE2M1 && (p.n & 1) == 1) e = Some(6); e
  }
  /** 体检命中时的人类可读原因 + 建议值（取值全部由 cfg/枚举推导，不写死） */
  private def errExplain(p: DebugParams, c: Int): String = c match {
    case 0 => s"MX 块大小超过硬件单拍可装载的 K 元素数 —— aBlk=${p.aMxBlk}（上限 kLoadNum(aFmt=${p.aFmt})=${kLoadNum(p.aFmt)}）、" +
      s"bBlk=${p.bMxBlk}（上限 ${kLoadNum(p.bFmt)}）、cBlk=${p.cMxBlk}（上限 cNWriteNum=totalN=${cfg.totalN}）。 建议：输入侧 aBlk/bBlk " +
      s"取 ${kLoadNum(p.aFmt)}（= kPerBeat，一个 kTile 恰好一个 scale 块）；输出侧 cBlk 只能取 <= totalN 且整除 totalN 的值 → " +
      s"${blkToSel.keys.toSeq.sorted.filter(b => b <= cfg.totalN && cfg.totalN % b == 0).mkString(" 或 ")}（totalN=${cfg.totalN} 下 cBlk=32/64 必被拒）"
    case 3 => s"输入侧 MX 与非 MX 混配 —— isMx(aFmt=${p.aFmt})=${isMxAny(p.aFmt)} XOR isMx(bFmt=${p.bFmt})=${isMxAny(p.bFmt)}。 v1 只允许双 MX" +
      "（E4M3/E5M2 互组）或双非 MX 浮点（FP16/BF16 互组）：A=MX×B=非MX 数学不可实现，A=非MX×B=MX 需 executor 偶奇重排（v1 禁止留 v2）。" +
      " 建议：aFmt/bFmt 改成同族；本约束不限制输出 cFmt"
    case 4 => s"MX 输入（aFmt=${p.aFmt}、bFmt=${p.bFmt}）必须 loadWMode=1（B 按 N×K 转置存放），当前为 0。 建议：loadW = Some(1) 或留 None 自动推导"
    case 5 => s"FP32 只能作【输出】格式、不能作 A/B 输入（aFmt=${p.aFmt}、bFmt=${p.bFmt}）。 建议：输入改用 FP16 或 BF16"
    case 6 => s"mxfp4(MXE2M1) 输出要求 N 为偶数（半字节打包），当前 N=${p.n}。 建议：N 改偶数，或 cFmt 换 MXE4M3/MXE5M2"
    case _ => "未知 errCode（不应发生）"
  }
  /** ② 打印体检结论 + 生效配置摘要 + 派生几何（一次打全，便于对着波形看） */
  private def printHealth(p: DebugParams, predicted: Option[Int], plan: Plan): Unit = {
    println(s"[$TAG] ═════════════ HiveCoreQuickSpec 配置体检 ═════════════")
    println(s"[$TAG] 生效配置: ${p.banner} rnd=${rnd}(sel=${p.rnd}) strictAddr=${p.strictAddr}")
    println(s"[$TAG] 派生几何: totalN=${cfg.totalN} cDmaExtW=${cfg.cDmaExtW} aExtW=${cfg.aExtW} bExtW=${cfg.bExtW} " +
      s"kTiles=${plan.aKTiles} mTiles=${(p.m + cfg.totalN - 1) / cfg.totalN} nTiles=${plan.nTiles} beatsPerRow=${plan.beatsPerRow}；" +
      s"期望 beat A=${plan.expAAddrSeq.size} B=${plan.expBAddrSeq.size} C=${plan.expCAddrSeq.size} sA=${plan.expScaleAAddrSeq.size} " +
      s"sB=${plan.expScaleBAddrSeq.size} sC=${plan.expCScaleAddrSeq.size}；内存镜像字节=${plan.memByteCount}")
    if (p.addrModeSel != AddrMode.MAT.litValue.toInt) println(s"[$TAG] ⚠ addrMode=TILE 已写入 reg6[14:12] 并回读对账通过；" +
      "当前 RTL 无消费者，不影响数值结果（内存镜像 / golden / 六路期望地址一律按 MAT 语义生成）")
    predicted match {
      case None => println(s"[$TAG] 体检结论：配置合法，硬件不会置 config-err → 走正向 golden 逐元素比对 ✓")
      case Some(c) => println(s"[$TAG] ★★★ 体检不通过：硬件将在 EXECUTE 后直接置 STATUS.err（errCode=$c），不进计算 ★★★\n" +
        s"[$TAG]   原因：${errExplain(p, c)}\n[$TAG]   处置：仍然继续跑（负向路径）并硬断言硬件确实以 errCode=$c 拒绝、留下波形") }
    val model = precheckConfigErr(p, cfg.totalN)   // 金锚口径与 RTL 口径不一致时给出 #124 提示
    if (model != predicted) println(s"[$TAG] 注：HiveDebugModel.precheckConfigErr 预测=$model 与本处 RTL 口径=$predicted 不一致" +
      "（#124：errCode 0/1/2 被 RTL 合并为 0，真实优先级是 6>5>4>3>0 的 last-connect 序）")
  }
  /** ⑥ 失败/超时落盘关键数据文件（波形由 finally 的 copyWave 负责） */
  private def dumpFailFiles(p: DebugParams, plan: Plan, addrBad: Seq[String], cmpLines: Seq[String]): Unit = {
    val b = "sim/debug/quick_fail"; val pw = new java.io.PrintWriter(new java.io.File(s"$b.txt"))
    pw.println(p.banner); pw.println("--- 地址对账失配 ---"); addrBad.foreach(pw.println)
    pw.println("--- C 元素失配 ---"); cmpLines.foreach(pw.println); pw.close()
    HiveSimCommon.writeMatrixHex(s"${b}_A.hex", plan.aCode.map(_.map(_.toInt)), plan.aEb * 8)
    HiveSimCommon.writeMatrixHex(s"${b}_B.hex", plan.bCode.map(_.map(_.toInt)), plan.bEb * 8)
    println(s"[$TAG] FAIL 数据文件已落盘: $b.txt / ${b}_A.hex / ${b}_B.hex") }

  // ══ 主运行器：③ 镜像+golden → ④ 仿真+六通道收集式地址对账 → ⑤ 逐元素比对 → ⑥⑦ 波形/回读 → ⑧ 收尾 ══
  private def runQuick(p: DebugParams): Unit = {
    val predicted = hwErrCode(p)                     // ① 体检。负向路径不跑金锚 validate：硬件不进计算，
    if (predicted.isEmpty) validate(p, cfg.totalN)   //   且 validate 的 require 会先于仿真抛掉、用户看不到 errCode
    val plan = new Plan(p, cfg); plan.prepare()      // ③ 内存镜像（loadW=1 时 B 为 N×K 转置，HiveDebugModel 已实现）
    printHealth(p, predicted, plan)                  // ② 摘要
    val maxCycles = if (isMxAny(p.cFmt)) math.max(p.maxCycles, 20000) else p.maxCycles
    val cLog = mutable.ArrayBuffer[(Long, BigInt)](); val sLog = mutable.ArrayBuffer[(Long, BigInt)]()
    val addrBad = mutable.ArrayBuffer[String](); var addrBadCnt = 0; var cycle = 0; var regRbMsg = ""; var passed = false
    var regRbOk = false; var done = false; var errSeen = false; var errCode = -1; var cmp: Option[plan.CmpSummary] = None
    try {
      simulate(new HiveCore(cfg)) { dut =>
        // ④ 地址对账只收集不抛（strictAddr 决定跑完后是否判失败），一次能看到全部失配点
        def chk(ch: String, i: Int, hw: Long, exp: Seq[Long]): Unit = if (i < exp.size && hw != exp(i)) {
          addrBadCnt += 1
          if (addrBad.size < 16) addrBad += f"$ch beat#$i hw=0x$hw%X exp=0x${exp(i)}%X delta=${hw - exp(i)}%d" }
        /** 读通道统一驱动：req.valid 拍按【请求地址】从字节镜像供数（内存保真，绝不按 beat 序号发数）+ 地址对账 */
        def rdCh(chan: String, cnt: Int, exp: Seq[Long], vld: => Boolean, adr: => Long,
                 rsp: (Boolean, BigInt) => Unit, at: Long => BigInt): Int =
          if (vld && cnt < exp.size) { val a = adr; chk(chan, cnt, a, exp); rsp(true, at(a)); cnt + 1 }
          else { rsp(false, BigInt(0)); cnt }
        // ---- Reset：六个 DMA 通道全置 idle（req.ready=1、rsp.valid=0）----
        Seq(dut.io.dmaA.rsp, dut.io.dmaB.rsp).foreach { r => r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaScaleA.get.rsp, dut.io.dmaScaleB.get.rsp).foreach { r => r.valid.poke(false.B); r.payload.data.poke(0.U); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaC.rsp, dut.io.dmaCScale.get.rsp).foreach { r => r.valid.poke(false.B); r.payload.err.poke(false.B) }
        Seq(dut.io.dmaA.req, dut.io.dmaB.req, dut.io.dmaC.req, dut.io.dmaScaleA.get.req, dut.io.dmaScaleB.get.req,
          dut.io.dmaCScale.get.req).foreach(_.ready.poke(true.B))
        dut.io.resp.ready.poke(true.B); dut.io.cmd.valid.poke(false.B)
        dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        // ---- 命令通道 helper：REG_WRITE1/2 写寄存器；REG_READ 回读（tail=回读时 resp.data 的 peek 时机）----
        def cmd(op: HiveCoreOp.Type, r0: Int, r1: Int, d0: Long, d1: Long, tail: Int = 2): Unit = {
          dut.io.cmd.valid.poke(true.B); dut.io.cmd.payload.op.poke(op)
          dut.io.cmd.payload.reg0Addr.poke(r0.U); dut.io.cmd.payload.reg1Addr.poke(r1.U)
          dut.io.cmd.payload.data0.poke(d0.U); dut.io.cmd.payload.data1.poke(d1.U)
          dut.clock.step(); dut.io.cmd.valid.poke(false.B); dut.clock.step(tail) }
        def readReg(a: Int): BigInt = { cmd(HiveCoreOp.REG_READ, a, 0, 0L, 0L, tail = 1)
          val d = dut.io.resp.payload.data.peek().litValue; dut.clock.step(); d }
        // ---- 配置寄存器：reg0=m reg1=n reg2=k reg3/4/5=a/b/cAddr reg6=控制字 reg7=格式字
        //      reg9/0A=scaleA/B 基址 regB=scaleC 基址（reg8=STATUS 只读）----
        cmd(HiveCoreOp.REG_WRITE2, 0x00, 0x01, p.m, p.n); cmd(HiveCoreOp.REG_WRITE2, 0x02, 0x03, p.k, A_BASE)
        cmd(HiveCoreOp.REG_WRITE2, 0x04, 0x05, B_BASE, C_BASE)
        cmd(HiveCoreOp.REG_WRITE2, 0x06, 0x07, p.controlWord6, p.controlWord7)
        cmd(HiveCoreOp.REG_WRITE2, 0x09, 0x0A, SA_BASE, SB_BASE); cmd(HiveCoreOp.REG_WRITE1, 0x0B, 0x00, SC_BASE, 0L)
        // ⑦ addrMode / 格式字回读对账：addrMode 无硬件消费者，只能靠回读证明「确实写进去了」
        val rb6 = readReg(0x06).toLong; val rb7 = readReg(0x07).toLong
        regRbOk = rb6 == p.controlWord6 && rb7 == p.controlWord7
        regRbMsg = f"reg6 写=0x${p.controlWord6}%08X 读=0x$rb6%08X（addrMode 占 reg6[14:12]）；reg7 写=0x${p.controlWord7}%08X 读=0x$rb7%08X"
        println(s"[$TAG] 寄存器回读对账：$regRbMsg → ${if (regRbOk) "一致 ✓" else "不一致 ✗"}")
        // ---- EXECUTE ----
        dut.io.cmd.valid.poke(true.B); dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
        dut.io.cmd.payload.reg0Addr.poke(0.U); dut.io.cmd.payload.reg1Addr.poke(0.U)
        dut.io.cmd.payload.data0.poke(0.U); dut.io.cmd.payload.data1.poke(0.U); dut.clock.step(); dut.io.cmd.valid.poke(false.B)
        var aS = 0; var bS = 0; var saS = 0; var sbS = 0; var cB = 0; var csB = 0; var pollSt = 0; var pollCool = 0
        def rdA(v: Boolean, d: BigInt): Unit = { dut.io.dmaA.rsp.valid.poke(v.B); dut.io.dmaA.rsp.payload.data.poke(d.U) }
        def rdB(v: Boolean, d: BigInt): Unit = { dut.io.dmaB.rsp.valid.poke(v.B); dut.io.dmaB.rsp.payload.data.poke(d.U) }
        def rdSA(v: Boolean, d: BigInt): Unit = { dut.io.dmaScaleA.get.rsp.valid.poke(v.B); dut.io.dmaScaleA.get.rsp.payload.data.poke(d.U) }
        def rdSB(v: Boolean, d: BigInt): Unit = { dut.io.dmaScaleB.get.rsp.valid.poke(v.B); dut.io.dmaScaleB.get.rsp.payload.data.poke(d.U) }
        while (!done && !errSeen && cycle < maxCycles) {
          aS = rdCh("dmaA", aS, plan.expAAddrSeq, dut.io.dmaA.req.valid.peek().litToBoolean,
            dut.io.dmaA.req.payload.addr.peek().litValue.toLong, rdA, plan.beatAtA)
          bS = rdCh("dmaB", bS, plan.expBAddrSeq, dut.io.dmaB.req.valid.peek().litToBoolean,
            dut.io.dmaB.req.payload.addr.peek().litValue.toLong, rdB, plan.beatAtB)
          saS = rdCh("dmaScaleA", saS, plan.expScaleAAddrSeq, dut.io.dmaScaleA.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong, rdSA, plan.byteAt)
          sbS = rdCh("dmaScaleB", sbS, plan.expScaleBAddrSeq, dut.io.dmaScaleB.get.req.valid.peek().litToBoolean,
            dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong, rdSB, plan.byteAt)
          /** 写通道统一驱动：req.valid 拍记录 (addr,data) + 地址对账（scaleC 传空序列即自动跳过对账） */
          def wrCh(ch: String, i: Int, exp: Seq[Long], on: Boolean, ad: => Long, da: => BigInt,
                   ack: Boolean => Unit, log: mutable.ArrayBuffer[(Long, BigInt)]): Int =
            if (on) { val a = ad; chk(ch, i, a, exp); log += ((a, da)); ack(true); i + 1 } else { ack(false); i }
          cB = wrCh("dmaC", cB, plan.expCAddrSeq, dut.io.dmaC.req.valid.peek().litToBoolean,
            dut.io.dmaC.req.payload.addr.peek().litValue.toLong, dut.io.dmaC.req.payload.data.peek().litValue,
            v => dut.io.dmaC.rsp.valid.poke(v.B), cLog)
          csB = wrCh("dmaCScale", csB, if (isMxAny(p.cFmt)) plan.expCScaleAddrSeq else Seq.empty,
            dut.io.dmaCScale.get.req.valid.peek().litToBoolean, dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong,
            dut.io.dmaCScale.get.req.payload.data.peek().litValue, v => dut.io.dmaCScale.get.rsp.valid.poke(v.B), sLog)
          // STATUS(reg8) 轮询：bit0=busy bit1=done bit2=err bits[6:3]=errCode bits[31:16]=progress
          val (ps, pc, st) = statusPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool); pollSt = ps; pollCool = pc
          st.foreach { s =>
            if (s.err) { errSeen = true; errCode = s.errCode; println(f"[$TAG cycle=$cycle%5d] STATUS.err=1 errCode=${s.errCode}") }
            else if (s.done) { done = true; println(f"[$TAG cycle=$cycle%5d] STATUS.done=1 A=$aS B=$bS C=$cB sA=$saS sB=$sbS sC=$csB") } }
          dut.clock.step(); cycle += 1
        }
        dut.io.dmaC.rsp.valid.poke(false.B); dut.io.dmaCScale.get.rsp.valid.poke(false.B); dut.io.cmd.valid.poke(false.B)
        println(s"[$TAG] 运行结束: cycles=$cycle/$maxCycles done=$done err=$errSeen(errCode=$errCode)；beat A=$aS/${plan.expAAddrSeq.size} " +
          s"B=$bS/${plan.expBAddrSeq.size} C=$cB/${plan.expCAddrSeq.size} sA=$saS/${plan.expScaleAAddrSeq.size} sB=$sbS/${plan.expScaleBAddrSeq.size} sC=$csB/${plan.expCScaleAddrSeq.size}")
        println(s"[$TAG] 六通道地址对账不一致拍数=$addrBadCnt（strictAddr=${p.strictAddr}）")
        addrBad.foreach(s => println(s"[$TAG]   ADDR-DIFF $s"))
        // ⑤ golden 逐元素比对；负向路径下硬件根本不该进计算，只核对 errCode
        if (predicted.isDefined) { passed = errSeen && errCode == predicted.get && !done && regRbOk
          println(s"[$TAG] 负向路径：体检预测 errCode=${predicted.get}，实测 err=$errSeen errCode=$errCode done=$done → ${if (passed) "符合预测 ✓" else "与预测不符 ✗"}") }
        else if (!done) println(s"[$TAG] 超时：$maxCycles 拍内未观测到 STATUS.done")
        else { val c = plan.compare(cLog.toSeq, sLog.toSeq); cmp = Some(c)
          println(f"[$TAG] C 对比: mismatch=${c.mismatch}/${c.elems}，maxRelErr=${c.maxRelErr}%.3e，maxAbsErr=${c.maxAbsErr}%.3e")
          c.details.zipWithIndex.foreach { case (s, i) => println(s"[$TAG]   ${if (i == 0) "首个失配样本" else "MISMATCH"} $s") }
          passed = !errSeen && regRbOk && c.mismatch == 0 && (addrBadCnt == 0 || !p.strictAddr) }
      }
    } finally {
      // ⑥ 波形：成功也 dump；失败/超时同样走这里（simulate 块退出后 verilator 才 flush trace.fst）
      copyWave(SUITE, p.testHint, p.waveTarget)
      val wb = try Files.size(Paths.get(p.waveTarget)) catch { case _: Throwable => -1L }
      if (passed) println(s"[$TAG] PASS: " + cmp.map(c => s"${c.elems - c.mismatch}/${c.elems} 元素全部匹配")   // ⑧ 收尾行
        .getOrElse(s"负向路径 errCode=${errCode} 命中预测、硬件未进计算") +
        s"，地址对账全绿（失配 $addrBadCnt 拍），寄存器回读一致，波形 ${p.waveTarget} = $wb bytes")
      else {
        val first = cmp.flatMap(_.details.headOption).getOrElse(if (!done) s"超时未 done（cycles=$cycle/$maxCycles）" else s"errSeen=$errSeen errCode=$errCode regRbOk=$regRbOk")
        println(s"[$TAG] FAIL: mismatch=${cmp.map(_.mismatch).getOrElse(-1)}/${cmp.map(_.elems).getOrElse(p.m * p.n)}，" +
          s"地址对账失配 $addrBadCnt 拍，首个失配 $first，波形已转存 ${p.waveTarget}（$wb bytes）")
        dumpFailFiles(p, plan, addrBad.toSeq, cmp.map(_.details).getOrElse(Seq.empty)) }
    }
    // 硬断言放在 finally 之后：确保波形与数据文件已落盘再报红
    withClue(s"[$TAG] 寄存器回读对账（$regRbMsg）：") { regRbOk should be(true) }
    if (predicted.isDefined)
      withClue(s"[$TAG] 体检预测硬件应以 errCode=${predicted.get} 拒绝（RTL 把 blk 三违约合并为 0，见文件头更正段）：") {
        (errSeen, errCode, done) should be((true, predicted.get, false)) }
    else {
      withClue(s"[$TAG] 正向路径不应出现 STATUS.err、须在 maxCycles=$maxCycles 内观测到 done：") { (errSeen, done) should be((false, true)) }
      withClue(s"[$TAG] 六通道逐拍地址对账（语义模型 vs 硬件）：") {
        if (p.strictAddr) addrBadCnt should be(0) else println(s"[$TAG] strictAddr=false → 失配 $addrBadCnt 拍只报告不判失败") }
      withClue(s"[$TAG] C 逐元素对比 golden（不放宽容差、不改金锚）：") { cmp.map(_.mismatch) should be(Some(0)) } }
  }
}
