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

  // ---------- 组 (b2)：M≠N INT 组（裁定3 回归护栏，固定 M=16 N=32 strictAddr）----------
  // 证伪 bRow(loadWMode=0) 的 m/n 混淆 + INT16(×2)/INT32(×4) 偏移补齐：
  //   M=16≠N=32 → bRowOff 必按 n=32 计算（误用 m=16 会致 B 地址错位、strictAddr 逐拍 fatal）；
  //   M=16 → mTiles=1，规避 Issue B（多拍 nt=1 数据损坏仅在 mTiles>1 触发），使本用例
  //   纯验地址链修复、不被数据路问题掩盖；strictAddr 强制 true 锁六通道逐拍对账为硬护栏。
  //   INT16→INT32 输出 = 2 拍/行（多拍），覆盖多拍地址步进 + 半字节以外全档偏移。
  it should "run builtin M!=N INT group debug GEMM (bRow n-vs-m + INT16/INT32 offset guard)" in {
    runDebug(builtinIntMNParams())
  }

  // ---------- 组 (c)：内置 MXE4M3 组（含 mxBlockSize 生效验证） ----------
  it should "run builtin MX group debug GEMM" in {
    runDebug(builtinMxParams())
  }

  // ==========================================================================
  // 组 (d) Issue B 变量隔离对照实验（leader 裁定：进波形前先隔离触发变量）：
  //   背景——DebugCase MX 绿组（M32N32K32 MXE4M3→FP32）实测 beatsPerRow=2（多拍）、
  //   mTiles=2、nTiles=2（nt=1 存在），同时满足原 Issue B 判别器「多拍+mTiles>1+nt=1」
  //   三条件却全绿；而 FP16/INT16 两个 M32N32K32 组（loadW=0、kTiles=2）FAIL 509。
  //   MX 绿组与两失败组同时差两个变量：kTiles（1 vs 2）且 loadWMode（1 vs 0）。
  //   下列三组各锁定一个变量，全部 strictAddr=true（六通道逐拍地址硬对账），
  //   使「地址全绿+数据失配」可与「地址失配」清晰区分。
  // ==========================================================================

  // (d1) 隔离 loadWMode：FP16 M32N32K32，仅把 loadW 由 0 改 1，其余与失败组全同。
  //   转绿 → loadWMode=0 是必要变量（嫌疑转向垂直加载路径 hiveLoadV/hivePsumIn 与 C 写回交互）；
  //   仍失败 → loadW 无关，变量在 kTiles 或格式族。
  // 【历史实验结论：FAIL —— C mismatch=509，与 loadW=0 的 user-param 组完全同值，
  //   地址对账=0（六通道全绿）→ loadWMode 不是触发变量，Issue B 与垂直/水平加载路径无关。】
  // 【#122 第二阶段 B 项：解除 ignore 转 active 正式护栏】
  //   本用例满足 #122 全部四条触发条件：kTiles=2(ceil(32/16)) ∧ mTiles=2(ceil(32/16))
  //   ∧ beatsPerRow=2(ceil(16×32/256)) ∧ nTiles=2(ceil(32/16))，且 loadW=1 证明该缺陷与
  //   loadWMode 正交 → 是主修复（HiveCore.scala 恢复 blkLatchCnt/blkLatchFull 同源双门）
  //   在 loadW=1 侧的守卫。
  //   ★ 护栏「能跑红」实跑证据（主修复落地前的工作树，build/guard_prefix_122.log）：
  //     本用例转 active 后实跑 FAIL、mismatch=509、地址对账=0 —— 与 user-param 组同值，
  //     证明它确实能捕获 #122 缺陷；主修复落地后转 PASS。
  it should "run FP16 M32N32K32 loadW=1 (Issue-B isolation: loadWMode variable)" in {
    runDebug(issueBFp16LoadW1Params())
  }

  // (d2) 隔离 kTiles：FP16 M32N32K16（非 MX kTiles=ceil(16/16)=1），loadW=0，多拍输出。
  //   转绿 → kTiles>1 是必要变量（嫌疑转向多 pass psum 回灌与 C buffer 跨 pass 占用）；
  //   仍失败 → kTiles 无关。
  // 【实验结论（本会话实跑）：PASS —— C mismatch=0/1024、地址对账=0。与失败组唯一差异
  //   K 32→16（kTiles 2→1）即转绿 → kTiles>1 是 Issue B 的必要变量。永久护栏（active）。】
  it should "run FP16 M32N32K16 loadW=0 (Issue-B isolation: kTiles=1 variable)" in {
    runDebug(issueBFp16K16Params())
  }

  // (d3) MX M32N32K64（MX kTiles=64/32=2、mTiles=2、beatsPerRow=2、loadW=1 强制、FP32 多拍输出）：
  //   隔离「mTiles>1 ∧ kTiles>1 ∧ 多拍」三变量同时成立时的行为。
  // 【#121 金锚修正后重定位（本会话实跑取证，勿再按旧结论解读）】
  //   旧结论「d3 在 dmaB beat#16 撞 +16 地址错 = MX 多 kTile B 地址 RTL bug」已【撤回】：
  //   该 +16（hw=0x1003E0 / exp=0x1003D0）根因在金锚 HiveDebugModel.bKTileOff 参数退化
  //   （totalN*bEb=16，应为 bKpb*bEb=32，详见该处 #121 证据注释），硬件 B 地址路【在 MX 区间】清白。
  //   ⚠ 清白限定：本用例是 MX（MXE4M3），仅证明硬件 colTileOffset 在 MX 区间对。
  //     ★★【#124 ① 已修复：以下为历史陈述，勿再当现状读】历史事实（#124 ① 之前）：
  //     硬件 bColTileAddressOffset 曾是编译期常数 totalN*(bW/8)=32（bW 是编译期位宽、
  //     出厂 16，故格式无关），在 INT8(需16)/INT32(需64) 走 loadW=1 且 kTiles>1 时
  //     kt≥1 的 B 地址全错 —— 那是与本金锚缺陷互补的真 RTL 缺陷区间（Section E【E2】）。
  //     现状：#124 ① 已把 a/bColTileAddressOffset 改为运行期
  //     totalN × elemsPerLane(fmt) × memElemBytes(fmt)，全格式与金锚恒等
  //     （MX8/FP16/BF16/INT16=32、INT8=16、INT32=64、半字节族=0），E2 已撤下。
  //     本 d3 用例的 MX 区间清白结论【不受影响】（MX 档新旧同为 32，数值零变化）。
  //   金锚修正后实跑：六通道地址对账=0（含 dmaB beat#16 全绿）、done=true err=false、
  //   A128/B64/C128/sA128/sB64 beat 全发 → B 地址在 MX 区间已证清白，本用例不再是 B 地址复现器。
  //   残余失配落在 C 数据路：1024 元素 mismatch=506，全部集中在 nt=1（列 gc=16..31），
  //   nt=0（列 0..15）零失配（断言点 HiveCoreDebugCase.scala:590 逐元素对比）。
  //   → 新定位：#122（Issue B，多拍 C 写回数据损坏）的精确起点证据 —— 失配通道 = C 数据路，
  //     第一 n-tile（nt=0）正确、第二 n-tile（nt≥1）几乎全错，指向 nt 切换（nt0→nt1）的
  //     C 写回 / psum 跨 tile 处理，而非单 beat 内切分。【#122 诊断修订】触发条件已由
  //     三条扩为四条：kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1（d3 的 nTiles=2 亦满足）；
  //     此前 B 地址逐拍 fatal 在 beat#16 提前终止测试、掩盖了后续 C 数据损坏，金锚修正
  //     放行后才暴露 —— 印证「地址失配提前终止会掩盖下游数据损坏」这一教训。
  //   【历史】此前保持 ignore：#122（C 数据路）尚未修复 —— 现 #122 主修复已落地，见下。
  // 【#122 第二阶段 B 项：解除 ignore 转 active 正式护栏】
  //   四条件核实（MXE4M3×MXE4M3→FP32、M=32 N=32 K=64、loadW=1、cDmaExtW=256）：
  //     kTiles = K/kPerBeat(MX8) = 64/(totalN×2) = 64/32 = 2  → >1 ✓
  //     mTiles = ceil(32/16) = 2                                → >1 ✓
  //     nTiles = ceil(32/16) = 2                                → >1 ✓
  //     beatsPerRow = ceil(totalN×32/cDmaExtW) = ceil(16×32/256) = 2 → >1 ✓
  //   四条全满足 → 属 #122 簇 A，残余失配 506/1024（全在 nt=1）应由主修复清除。
  //   ★ 护栏「能跑红」实跑证据（主修复落地前的工作树，build/guard_prefix_122.log）：
  //     本用例转 active 后实跑 FAIL、C mismatch=506/1024、地址对账=0（六通道全绿），
  //     失配全部落在 nt=1（列 gc=16..31）、nt=0 零失配 —— 与 #122 表征逐条自洽。
  it should "run MX M32N32K64 loadW=1 (Issue-B isolation: mTiles>1 AND kTiles>1)" in {
    runDebug(issueBMxK64Params())
  }

  // ==========================================================================
  // 组 (e) #122 四条触发条件永久护栏（C 项）
  //   主修复 = HiveCore.scala 恢复 blkLatchCnt/blkLatchFull「同源双门」（9d74335 有、
  //   672fb37 DMA 拆分重构时被整体删除的 refactor-loss）。触发条件四条：
  //     kTiles>1 ∧ mTiles>1 ∧ beatsPerRow>1 ∧ nTiles>1
  //   其中 beatsPerRow>1 是【时序窗口条件】而非结构条件（beatsPerRow=1 时 doneBlock
  //   那一拍 C FIFO 已空、窗口不触发）；nTiles>1 是【必要条件】（超弹只损伤下一块，
  //   单块无下一块）。
  //
  //   ★ 落位理由（选 HiveCoreDebugCase 而非 HiveCoreParamDebugSpec）：
  //     1) 本组是 Issue-B 变量隔离族（d1/d2/d3）的直接延伸：d2 已锁 kTiles=1、
  //        组(b2) builtinIntMN（M=16 N=32）已锁 mTiles=1，e2/e3 补齐 nTiles=1 与
  //        beatsPerRow=1 → 四条触发条件的单变量对照矩阵在本文件内闭合，就近可比对，
  //        无需跨文件重建同一套 runner。
  //     2) 本文件的 runDebug 断言面更全：除「地址对账=0」与「逐元素 mismatch=0」外，
  //        还断言六通道 beat 计数全对齐（aSent/bSent/cBeats/saSent/sbSent），可额外
  //        捕获主修复若口径写错（如按 lineCounter 而非 cvt.in.fire 计数）导致的写回
  //        短拍/死锁 —— 这正是旧码 P3 修复 2 翻车的形态，必须被护栏抓住。
  //     3) ParamDebugSpec 的定位是「文件顶部 config 单组交互调试」，其 -D 覆盖入口使
  //        护栏取值可被命令行漂移；本组护栏全部硬编码，取值不可漂移。
  //   ★ 全部 strictAddr=true（六通道逐拍地址硬对账）+ runDebug 内 cmp.mismatch should
  //     be(0) 的逐位匹配 golden 元素计数（日志打印 `${cmp.elems} 元素`）。
  //   ★ 覆盖充分性结论（C.4）：mTiles=1 与 kTiles=1 两个对照【已存在、不重复建】——
  //     kTiles=1 → d2（FP16 M32N32K16 loadW=0，active 绿）；
  //     mTiles=1 → 组(b2) builtinIntMN（INT16 M16 N32 K32 → INT32，beatsPerRow=2、
  //                kTiles=2、nTiles=2，仅 mTiles=1 与失败组不同，active 绿）。
  //     加上 e2（nTiles=1）与 e3（beatsPerRow=1），四条触发条件各有独立单变量对照，
  //     矩阵完备。
  // ==========================================================================

  // (e1) 四条全满足的正例 —— 主修复的守卫。
  //   锁的变量：【四条同时成立】kTiles=2 ∧ mTiles=2 ∧ nTiles=2 ∧ beatsPerRow=2。
  //   失败即意味着主修复被回退/口径写错（blkLatchFull 未关门 → C FIFO 块末超弹一行
  //   「下一 n-tile 的 kt0 partial row0」→ nt≥1 整块 C 数据损坏）。
  //   与组(a)「run debug GEMM from user parameters」同配置但【不可 -D 漂移】且
  //   strictAddr 硬编码 true（组(a) 的 m/n/k/strictAddr 均可被 -D 覆盖，非硬护栏）。
  //   ★ 能跑红证明：主修复落地前实跑 FAIL（mismatch=509/1024、地址对账=0），
  //     见 build/guard_prefix_122.log；主修复落地后 PASS。
  it should "guard 122 fix with all four triggers kTiles2 mTiles2 nTiles2 beatsPerRow2" in {
    runDebug(guard122AllFourParams())
  }

  // (e2) nTiles=1 单变量对照 —— 锁「nTiles>1 是必要条件」。
  //   锁的变量：【nTiles】。M=32 N=16 K=32 → nTiles=ceil(16/16)=1；其余全保持失败组
  //   取值（FP16→FP32、cDmaExtW=256 → beatsPerRow=2、loadW=0、kTiles=2、mTiles=2、
  //   strictAddr=1）→ 与失败组的【唯一】差异是 nTiles 2→1。
  //   期望：修复前后都应绿（诊断实测 mismatch=0/512）。机理：C buffer 块粒度 == 一个
  //   n-tile（WrOnly sTRANSFER_M 按 regFile.m 行一块、nCnt 0..nTile-1），块末即 nt 边界，
  //   超弹只损伤「下一块」；nTiles=1 无下一块 → 超弹无害（该行随 flushC 丢弃）。
  //   本对照的价值是【双向】的：若它在修复后变红，说明 blkLatchFull 关门过紧（误伤
  //   单块合法消费拍），即旧码 P3 修复 2 的死锁形态。
  it should "guard 122 necessity with nTiles1 single variable control M32 N16 K32" in {
    runDebug(guard122NTiles1Params())
  }

  // (e3) beatsPerRow=1 单变量对照 —— 锁「beatsPerRow>1 是时序窗口条件」。
  //   锁的变量：【beatsPerRow】。M=32 N=32 K=32 FP16→FP16 → 输出元素 16-bit，
  //   beatsPerRow=ceil(16×16/256)=1；其余三条件全保持失败组取值（kTiles=2、mTiles=2、
  //   nTiles=2、loadW=0、strictAddr=1）→ 与失败组的【唯一】差异是 beatsPerRow 2→1。
  //   期望：修复前后都应绿（诊断实测 mismatch=0/1024）。机理：单拍写回使 store 排空
  //   与阵列生产同速，doneBlock 那一拍 C FIFO 已空（cPop.valid=0）→ 三方同拍条件
  //   （cvt.in.ready ∧ cStoreGate ∧ cPop.valid）不成立，窗口不触发。
  //   ★ 注意本对照【不是】「16-bit 输出即安全」：cDmaExtW 收窄到 128 时 FP16 输出的
  //     beatsPerRow=ceil(16×16/128)=2>1，同样中招（见 ParamDebugSpec Section E1 文档）。
  it should "guard 122 necessity with beatsPerRow1 single variable control FP16 to FP16" in {
    runDebug(guard122Beats1Params())
  }

  // ==========================================================================
  // 组 (f) #133 dmaC 金锚同源缺陷永久护栏（nTiles=3 绝对地址语义）
  //   背景：sNEXT_N 曾为三角累积增量式（curAddr += colAddr），nt=1 块首址错为
  //   base + (M-1)*rowOff + colOff（FP32/M=32/N=32 下实得 0x200FC0、正确值
  //   0x200040 = base + cColOff）；金锚 cAddrSeqDense 曾是同一缺陷 RTL 的闭式
  //   反向拟合 → 逐拍地址硬断言退化为「RTL 与镜像自比」恒真假绿，全部既有
  //   套件放行。RTL 已改为「基址 + 轮偏移」线性模型（与 HiveCoreDmaScaleWr
  //   同构），金锚已改为张量语义绝对式 base + nt*cColOff + row*cRowOff +
  //   b*cBeatAddrStep。本护栏锁死该缺陷族不得回退（RTL 侧或金锚侧）。
  //   ★ 参数取值依据（勿改）：
  //     N=48 → nTiles=3：三角式的 nt(nt-1)/2 二阶项在 nt=1 恒为 0，只验 nt=1 会
  //       漏掉「只修对第一轮」的假修复，故必须让 nTiles 达到 3。
  //       【#136-KS2 事实订正】N=48 【不是】「能暴露 nt=2 二阶项的最小取值」——
  //       nTiles=3 的最小 N 是 33（ceil(33/16)=3）。48 的选取依据是另一条：它是
  //       nTiles=3 中【最小的被 totalN=16 整除】的取值，因而无尾块 padding，
  //       C 区名义张量区与硬件实际写入区严格重合（KW1 的 tileSlack 恒为 0），
  //       边界守卫保持恰好 tight；取 N=33 会引入 tileSlack=15*cEb 的尾块松弛，
  //       使「守卫 tight」与「nTiles=3」两个目标互相干扰。
  //     K=16 → kTiles=1：隔离 #124 的 kt 维缺陷，避免纠缠污染 dmaC 判读。
  //     FP16 输入：落在 #124 的清白区，避免 A/B 侧已知缺陷污染 dmaC 判读。
  //     cFmt=FP32 + cDmaExtW=256 → beatsPerRow=2（多拍），总拍数 3×32×2=192。
  //     loadWMode=0：与 guard122 正例同口径。
  //       【#136-KS2 事实订正】相对 guard122 正例（M=N=K=32 FP16→FP16）本例变的是
  //       【两个】量而非一个：N 32→48（nTiles 2→3，本护栏的目标变量）与
  //       K 32→16（kTiles 2→1，刻意为隔离 #124 的 kt 维缺陷）；cFmt FP16→FP32 亦
  //       随之使 beatsPerRow 1→2。故本例失败时【须先排除 kt 维交叉】：先以
  //       -Dk=32（kTiles=2）复跑同一 N=48，若红项随 k 变化则归因指向 #124 而非
  //       dmaC 地址族；再按「地址对账不一致拍数」是否为 0 区分地址族与数据族。
  //   ★ strictAddr 硬编码 true（不读 -D 覆盖），192 项完整序列逐拍致命断言。
  //   ★ 三层断言：(0) 仿真前纯软件金锚自证（几何 + 192 项长度 + 三条 nt 首拍
  //     不变式，见 it 块）；(1) runDebug 内逐拍地址硬断言（strictAddr）+ beat
  //     计数对齐 + C 逐元素 golden；(2) P0-8 C 区边界守卫（cRegionGuard=true，
  //     越界写入即 fail；本档 strictAddr=true 故首拍越界即致命，见 KW1/KS4 注释）。
  //     不硬编码任何末拍常量（口算末拍易自相矛盾，序列正确性由 cAddrSeqDense 内部
  //     两条 require 自证 + 逐拍对账保证）。
  it should "guard 133 dmaC absolute address semantics with nTiles3 N48 FP32 output" in {
    import HiveDebugModel._   // C_BASE / TAG（与 runDebug 作用域同源）
    val p = guard133CAddrNTiles3Params()
    // --- 第 0 层：仿真前纯软件金锚自证（不进仿真，金锚构造期即可炸，早于 simulate） ---
    val plan = new HiveDebugModel.Plan(p, dbgCfg)
    // 【#136-KS3】cEb 在用例侧【独立重算】totalN×cOutElemBytes，与 cAddrSeqDense 内部
    //   的 colOff 不共用符号：故下面三条块首拍断言比 cAddrSeqDense 自带的两条 require
    //   【更强】（那两条锁「金锚函数自身没被三角化回退」，这三条锁「护栏消费到的
    //   序列端到端等于独立算出的张量语义值」），不是自比恒真。
    val cEb = HiveSimCommon.cOutElemBytes(p.cFmt)
    // 【#136-KS3】本护栏限定整字节输出格式：半字节族（MXE2M1/INT4）cEb=0 会让 colOff=0，
    //   下面三条断言退化为「三个块首拍都等于 C_BASE」的恒等式（假绿而非报错），故先
    //   硬 require 拦住。与 runDebug 内 cRegionGuard 的同款约束同源（见 KW1 适用域注释）。
    require(cEb > 0,
      s"#136-KS3 guard133 nTiles3 harness requires byte-aligned cFmt (cOutElemBytes>0); " +
      s"got cFmt=${p.cFmt} -> cEb=0, colOff would be 0 and the three block-start " +
      "assertions would degenerate into identities (false green).")
    val colOff = dbgCfg.totalN.toLong * cEb // = cColOff = totalN×4 = 0x40
    // 【#136-KS3】块首拍下标参数化：原写死 32*2 与 2*32*2（= p.m × beatsPerRow），
    //   改参数用例会静默错位到别的拍上而仍可能碰巧通过；现由 plan 派生。
    val blkBeats = p.m * plan.beatsPerRow   // 一个 n-tile 块的拍数 = M × beatsPerRow
    plan.nTiles should be(3)
    plan.beatsPerRow should be(2)
    plan.expCAddrSeq.size should be(3 * blkBeats) // 192 = nTiles×M×beatsPerRow
    // 三条 nt 首拍不变式（张量语义绝对式的块起点，独立于 cAddrSeqDense 内部 require）：
    withClue("nt=0 块首拍应为 C_BASE：") { plan.expCAddrSeq(0) should be(C_BASE) }
    withClue(f"nt=1 块首拍应为 C_BASE+cColOff(0x$colOff%X)：") {
      plan.expCAddrSeq(blkBeats) should be(C_BASE + colOff)
    }
    withClue(f"nt=2 块首拍应为 C_BASE+2*cColOff(0x${2 * colOff}%X)：") {
      plan.expCAddrSeq(2 * blkBeats) should be(C_BASE + 2 * colOff)
    }
    println(f"[${TAG}] #133 金锚自证通过: nTiles=3 beatsPerRow=2 seq=${plan.expCAddrSeq.size} " +
      f"块首拍=0x${C_BASE}%X/0x${C_BASE + colOff}%X/0x${C_BASE + 2 * colOff}%X")
    // --- 第 1/2 层：仿真逐拍硬断言 + C 区边界守卫 ---
    // cRegionGuard=true 的前置约束（cEb>0，半字节族不支持）已由上方 require 在进仿真前
    // 明示并拦住；本档 cEb=4 → padSlack=tileSlack=0，守卫判定区间恰等于名义张量区。
    runDebug(p, cRegionGuard = true)
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

  // 裁定3 回归护栏：M≠N（固定 M=16 N=32 K=32）+ INT16→INT32 + strictAddr 强制 true。
  // m/n/k/strictAddr 硬编码（不读 -D 覆盖），确保本护栏在任何调用下都稳定证伪
  // bRow(loadWMode=0) 的 m/n 混淆与 INT16/INT32 偏移档缺失。
  private def builtinIntMNParams(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 16, n = 32, k = 32,
      aFmt = DataFormat.INT16, bFmt = DataFormat.INT16, cFmt = DataFormat.INT32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_debug_int_mn.fst"),
      testHint = "should-run-builtin-M-N-INT-group-debug-GEMM")
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
  // 组 (d) Issue B 变量隔离护栏参数（全部硬编码 m/n/k/loadWMode/strictAddr=true，
  //   不读 -D 覆盖，确保隔离结论稳定可复现；格式与 builtin 组一致以便单变量对照）
  // ==========================================================================

  // (d1) FP16 M32N32K32 loadW=1：与失败的 user-param 组唯一差异 = loadWMode 0→1。
  private def issueBFp16LoadW1Params(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 32, k = 32,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = true, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_debug_issueb_fp16_loadw1.fst"),
      testHint = "should-run-FP16-M32N32K32-loadW-1")
  }

  // (d2) FP16 M32N32K16 loadW=0：非 MX kTiles=ceil(16/16)=1，与失败组差异 = K 32→16（kTiles 2→1）。
  private def issueBFp16K16Params(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 32, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_debug_issueb_fp16_k16.fst"),
      testHint = "should-run-FP16-M32N32K16-loadW-0")
  }

  // (d3) MX M32N32K64 loadW=1：MXE4M3×MXE4M3→FP32（非量化输出），kTiles=64/32=2、mTiles=2。
  //   blk=32=kPerBeat（单 scale 块/pass），与 builtinMxParams 唯一差异 = K 32→64（kTiles 1→2）。
  private def issueBMxK64Params(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 32, k = 64,
      aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
      loadWMode = true, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_debug_issueb_mx_k64.fst"),
      testHint = "should-run-MX-M32N32K64-loadW-1")
  }

  // ==========================================================================
  // 组 (e) #122 四条触发条件护栏参数（全部硬编码 m/n/k/格式/loadWMode/strictAddr=true，
  //   不读 -D 覆盖，确保四条件矩阵取值不可漂移；仅 rnd/seed/maxCycles/wave 允许 -D）
  // ==========================================================================

  // (e1) 四条全满足正例：FP16 M32N32K32 → FP32、loadW=0、cDmaExtW=256（cfg 默认）。
  //   kTiles=ceil(32/16)=2、mTiles=ceil(32/16)=2、nTiles=ceil(32/16)=2、
  //   beatsPerRow=ceil(16×32/256)=2 → 四条全 >1。逐元素 golden 计数 = 32×32 = 1024。
  private def guard122AllFourParams(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 32, k = 32,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_guard122_all_four.fst"),
      testHint = "should-guard-122-fix-with-all-four-triggers")
  }

  // (e2) nTiles=1 单变量对照：唯一变量 = N 32→16（nTiles 2→1），其余同失败组。
  //   beatsPerRow=ceil(16×32/256)=2 仍 >1、kTiles=2、mTiles=2 → 仅 nTiles=1。
  //   逐元素 golden 计数 = 32×16 = 512。
  private def guard122NTiles1Params(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 16, k = 32,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_guard122_ntiles1.fst"),
      testHint = "should-guard-122-necessity-with-nTiles1")
  }

  // (e3) beatsPerRow=1 单变量对照：唯一变量 = cFmt FP32→FP16（beatsPerRow 2→1），
  //   其余同失败组（M32N32K32 → kTiles=2、mTiles=2、nTiles=2）。
  //   beatsPerRow=ceil(16×16/256)=1。逐元素 golden 计数 = 32×32 = 1024。
  private def guard122Beats1Params(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 32, k = 32,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP16,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_guard122_beats1.fst"),
      testHint = "should-guard-122-necessity-with-beatsPerRow1")
  }

  // ==========================================================================
  // 组 (f) #133 护栏参数（全部硬编码 m/n/k/格式/loadWMode/strictAddr=true，
  //   不读 -D 覆盖；仅 rnd/seed/maxCycles/wave 允许 -D，照 guard122 族范式）
  // ==========================================================================

  // (f) nTiles=3 绝对地址语义护栏：FP16 M32N48K16 → FP32、loadW=0、cDmaExtW=256。
  //   nTiles=ceil(48/16)=3、kTiles=ceil(16/16)=1、mTiles=ceil(32/16)=2、
  //   beatsPerRow=ceil(16×32/256)=2 → dmaC 总拍数 3×32×2=192。
  //   逐元素 golden 计数 = 32×48 = 1536。cColOff=totalN×4=0x40、cRowOff=N×4=0xC0。
  private def guard133CAddrNTiles3Params(): HiveDebugModel.DebugParams = {
    import HiveDebugModel._
    DebugParams(
      m = 32, n = 48, k = 16,
      aFmt = DataFormat.FP16, bFmt = DataFormat.FP16, cFmt = DataFormat.FP32,
      loadWMode = false, aMxBlkSel = 2, bMxBlkSel = 2, cMxBlkSel = 0,
      addrModeSel = 0, rnd = parseRnd("rnd", opt("rnd", DEF_RND)),
      seed = parseLong("seed", opt("seed", DEF_SEED)),
      strictAddr = true,
      maxCycles = parseInt("maxCycles", opt("maxCycles", DEF_MAX_CYCLES)),
      waveTarget = opt("wave", "sim/debug/hivecore_guard133_caddr_ntiles3.fst"),
      testHint = "should-guard-133-dmaC-absolute-address-semantics")
  }

  // ==========================================================================
  // 共享运行器
  // ==========================================================================

  /** 三组（含 #133 护栏组）共用同一编译期配置（默认全格式 + 运行期 isMx 门控），
    * 使 hasFp/hasInt/hasMx 同时为真、dmaScaleA/B 与 dmaCScale 端口恒暴露。
    * 【#133 上移】原为 runDebug 内部局部 val；护栏用例需要在【仿真前】用同一 cfg 构造
    * Plan 做纯软件金锚自证（三条 nt 首拍不变式），故上移为类级共享常量，杜绝
    * 「护栏用的 cfg 与 runner 用的 cfg 漂移」这一类共模错误。cDmaExtW 取默认 256。
    *
    * 【#136 附加评估项裁定：lazy val（惰性化），既保留 #133 的上移收益又消除其副作用】
    *   副作用（Kim 越界转交）：eager 类级 `private val` 的初始化发生在【suite 构造期】，
    *   于是 HiveCoreConfig 的 require 族（arrayN/clusterM 整除性、cDmaExtW ∈
    *   [totalN*8, totalN*32] 且 isPow2 且整除 totalN*32、各 bufferDepth>0 等）的失败
    *   时机也从「测试体求值期」提前到「suite 构造期」。ScalaTest 对构造期抛出的异常
    *   报为【整个 suite aborted】——本类 9 个用例一次性全丢，且汇总行从
    *   `failed +1` 变成 `aborted 1, completed 0`，回归基线的 `aborted 0` 语义被破坏，
    *   失败定位信息也从「哪个用例、哪组参数」退化为「suite 起不来」。
    *   裁定理由（三条备选的比较）：
    *     · 保留 eager val：组间共享 ✓，失败隔离 ✗（suite abort）。
    *     · 还原为 runDebug 局部 val：失败隔离 ✓，组间共享 ✗ —— 护栏用例第 0 层
    *       自证需要 cfg，还原后必须在 it 块里再造一份，正是 #133 上移要消除的漂移源。
    *     · lazy val（采纳）：两者兼得。首次访问发生在某个测试体内部（组(f) 第 0 层
    *       或 runDebug 的 `val cfg = dbgCfg`），故 require 失败只让【该用例】failed；
    *       初始化结果被缓存，其后所有用例复用同一实例，共享性与 eager 完全等价。
    *   代价：仅一次同步开销（Scala 的 lazy val 有 bitmap 双重检查），对测试无感。
    *   注意：lazy 化【不改变】任何 cfg 取值，也不推迟 require 的【强度】——它只把
    *   失败的作用域从 suite 收回单个用例。 */
  private lazy val dbgCfg = HiveCoreConfig(
    arrayN = 8, clusterM = 2,          // totalN = 16
    aW = 16, bW = 16, cW = 40,
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
    scaleBufferDepth = 64)

  /** @param cRegionGuard #133 可选低成本护栏：仅当 true 时对本用例的 dmaC 写拍加
    *   「C 区外写入即 fail」的边界守卫（默认 false，不改变其余任何用例的行为，
    *   避免污染其他套件）。
    *   【#136-KW1 适用域（开启前必读）】
    *     1. 仅支持整字节输出格式，即 `HiveSimCommon.cOutElemBytes(p.cFmt) > 0`。
    *        半字节族（MXE2M1/INT4）cOutElemBytes=0，按裁定【不扩展支持】；开启时会在
    *        守卫几何构造期（进 simulate 之后、任何 dmaC 拍之前）require 抛出，整例 fail。
    *     2. 判定区间不是裸名义张量区 [C_BASE, C_BASE+M*N*cEb)，而是叠加了
    *        padSlack = max(0, cDmaExtW/8 - totalN*cEb) 与
    *        tileSlack = max(0, (nTiles*totalN - N)*cEb) 两项适用域 slack 之后的
    *        cRegionHi；两项均为 0 时（如组(f)：colOff=64>=32 且 48%16==0）判定区间
    *        与名义区逐字节相同，守卫保持恰好 tight。
    *     3. 守卫只抓「越界踩踏」，对「区内语义错位」无感（三角累积缺陷值 0x201780
    *        落在区内即不报警）；语义错位由 chkAddr 逐拍地址对账负责，两者互补。
    *     4. 致命性随 p.strictAddr：true = 首拍越界即致命中断；false = 收集后在报告段
    *        统一裁决（regionBad should be(empty)）。 */
  private def runDebug(p: HiveDebugModel.DebugParams, cRegionGuard: Boolean = false): Unit = {
    import HiveDebugModel._

    val cfg = dbgCfg

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
        // 【#133 P0-8】C 区边界守卫收集缓冲（仅 cRegionGuard=true 时写入）
        val regionBad = scala.collection.mutable.ArrayBuffer[String]()
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

        // ── 【#133 P0-8 / #136-KW1 适用域修正】C 区边界守卫几何 ──────────────────
        //（仅 cRegionGuard=true 时构造与消费）
        // 名义张量区 = [C_BASE, C_BASE + M*N*cEb)，cEb = cOutElemBytes(cFmt)；
        // 每拍覆盖 beatBytes = cDmaExtW/8 字节。但「拍地址 + 拍宽 <= 名义区上界」
        // 这个判据只在下面两个前提下自洽，否则对【正确 RTL】确定性假红：
        //   (P1) 列步进 colOff = totalN*cEb >= 拍宽 beatBytes。否则末列那一拍横跨进
        //        「下一 tile 的列区」，写出名义区右边界。
        //        实例（#136 实测复现）：1B 输出（INT8/MXE4M3/MXE5M2）+ cDmaExtW=256
        //        -> colOff=16 < beatBytes=32；M=32/N=32 恰好 1 拍假红：
        //        beat#63 addr=0x2003F0，0x2003F0+0x20=0x200410 > 名义上界 0x200400，
        //        而同一次运行的逐拍地址对账=0、C 逐元素 mismatch=0（纯守卫适用域缺陷）。
        //   (P2) totalN 整除 N（无尾块 padding）。否则最后一个 n-tile 只有
        //        N-(nTiles-1)*totalN 列有效，但硬件仍按整 tile 布局写到 nTiles*totalN
        //        列，名义上界比实际写入区窄 (nTiles*totalN - N)*cEb。
        //        实例：FP32 + N=24（nTiles=2，尾块仅 8/16 列有效）。
        // 【#136-KW1 裁定修法（Kim 最小修法）】把 (P1)(P2) 的缺口显式量化为两项 slack
        //   叠加到上界，不改判据形状、不放宽容差语义：
        //     padSlack  = max(0, beatBytes - colOff)
        //     tileSlack = max(0, (nTiles*totalN - N) * cEb)
        //     判定上界 cRegionHi = C_BASE + M*N*cEb + padSlack + tileSlack
        //   ★ 组(f)（FP32/M=32/N=48/totalN=16/cDmaExtW=256）两项 slack 均为 0：
        //     colOff=64 >= beatBytes=32 -> padSlack=0；48 被 16 整除 -> tileSlack=0，
        //     故上界与旧式逐字相同（0x201800），tight 性一字不变——末拍
        //     0x2017E0 + 0x20 = 0x201800 恰等于上界、`>` 不成立、不误报。
        //   ★ MXE4M3/M=32/N=32：padSlack=16、tileSlack=0 -> 上界 0x200410，恰好吃掉
        //     上述假红且不留余量（再多越 1 字节仍报）。
        // 【适用域 / 覆盖边界（务必读）】
        //   * 仅整字节输出格式：cEb > 0。半字节族（MXE2M1/INT4）cOutElemBytes=0，其
        //     列步进为 totalN/2、两列共一字节且拍内 nibble 交织，需 bit 级精算，按
        //     #136 裁定【不扩展支持】（扩展属范围扩张，非本守卫职责）。该约束由下方
        //     require 在【守卫几何构造期】抛出（不再是原实现的逐拍运行期异常：那时
        //     无 dmaC 写拍的用例根本不炸，约束被静默绕过），并在 runDebug 的 @param
        //     文档与 cRegionGuard=true 的各调用点明示。
        //   * 本守卫只抓「越界踩踏」（地址计数器暴走 / 跨区污染），对「地址落在名义区
        //     内但语义错位」完全无感——例如三角累积缺陷在 nt=1 的值 0x201780 就在区内、
        //     不报警。语义错位的唯一关口是 chkAddr 的逐拍地址对账；两者互补，不可互相
        //     替代，也不可因加了 slack 就认为守卫能替代对账。
        // 【致命性（#136-KS4 订正，与 chkAddr 同轨）】strictAddr=true 时首拍越界即致命
        //   （下方 withClue 内直接 should 断言，仿真当场中断）；strictAddr=false 时才
        //   收集进 regionBad、跑完在报告段统一裁决。#133 原注释「非致命，跑完统一 fail」
        //   只对 strictAddr=false 成立，组(f) 硬编码 strictAddr=true 属前者。
        val cRegionEb = HiveSimCommon.cOutElemBytes(p.cFmt)
        if (cRegionGuard)
          require(cRegionEb > 0,
            s"#136-KW1 C region guard supports byte-aligned output formats only (cOutElemBytes>0); " +
            s"cFmt=${p.cFmt} gives cEb=0 (half-byte family MXE2M1/INT4 is NOT supported by design: " +
            "colOff=totalN/2 with intra-beat nibble interleaving needs bit-level accounting). " +
            "Fix: pass cRegionGuard=false, or use a byte-aligned cFmt.")
        val cRegionBytes = p.m.toLong * p.n * cRegionEb
        val cRegionBeatBytes = (cfg.cDmaExtW / 8).toLong
        val cRegionColOff = cfg.totalN.toLong * cRegionEb
        val cRegionPadSlack = math.max(0L, cRegionBeatBytes - cRegionColOff)
        val cRegionTileSlack =
          math.max(0L, (plan.nTiles.toLong * cfg.totalN - p.n) * cRegionEb.toLong)
        val cRegionSlack = cRegionPadSlack + cRegionTileSlack
        val cRegionHi = C_BASE + cRegionBytes + cRegionSlack
        def chkCRegion(idx: Int, addr: Long): Unit = if (cRegionGuard) {
          if (addr < C_BASE || addr + cRegionBeatBytes > cRegionHi) {
            regionBad += f"dmaC beat#$idx addr=0x$addr%X 越界：判定区间 " +
              f"[0x$C_BASE%X, 0x$cRegionHi%X)，拍宽 ${cRegionBeatBytes}B" +
              f"（名义张量区上界 0x${C_BASE + cRegionBytes}%X + slack ${cRegionSlack}B " +
              f"= pad ${cRegionPadSlack}B + tile ${cRegionTileSlack}B）"
            if (p.strictAddr)
              withClue(f"dmaC beat#$idx C 区边界守卫（addr=0x$addr%X）：") {
                (addr >= C_BASE && addr + cRegionBeatBytes <= cRegionHi) should be(true)
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
            chkCRegion(cBeats, addr)
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
        if (cRegionGuard) {
          // 【#136-KW1】slack==0 时本行与旧输出逐字节相同（组(f) 的报告行不得变形：
          //   `C 区边界守卫: [0x200000, 0x201800) 拍宽=32B 越界拍数=0`）；slack>0 才追加
          //   注解，明示判定上界由名义张量区上界叠加多少 slack 得到（防止 slack 被误读
          //   为「放宽容差」——它是适用域修正，量化的是守卫判据本身的两个前提缺口）。
          val slackNote =
            if (cRegionSlack > 0)
              f" 名义张量区上界=0x${C_BASE + cRegionBytes}%X slack=${cRegionSlack}B" +
              f"(pad=${cRegionPadSlack}+tile=${cRegionTileSlack})"
            else ""
          println(f"[$tag] C 区边界守卫: [0x$C_BASE%X, 0x$cRegionHi%X)" +
            s" 拍宽=${cfg.cDmaExtW / 8}B 越界拍数=${regionBad.size}" + slackNote)
          regionBad.foreach(s => println(s"[$tag]   REGION-DIFF $s"))
        }

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
            if (cRegionGuard)
              withClue(s"[$tag] C 区边界守卫（越界拍明细见 REGION-DIFF）：") { regionBad.toSeq should be(empty) }
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
