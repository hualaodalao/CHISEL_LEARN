/** WorkerBee MXFP8 v2 SIMD 双 MX（异构 E4M3 × E5M2）端到端数值仿真用例。
  *
  * 【v2 SIMD 打包布局】16-bit lane 打包 2 个 8-bit MX 元素：低字节=偶 k=2j、
  * 高字节=奇 k=2j+1；PE 内 4×WbCvtOpMx + 2×WbMacFp 级联（先偶后奇；二者均为原
  * WbWorkUnit.scala 子模块、已随 FusionMac 重构删除，功能并入 FpCvt/FpMul/FpAdd → WbFusionMacFp），
  * 每拍每波前推进 32 个 K 元素 = 恰好一个 32-K scale 块 = 单 pass，
  * 故 kTile 数 = K/32（scaleA 块下标 kb = kt）。外部 A/B 存储 1 字节/元素：
  * 行步长 = K 字节（非 2K），k 窗口步进 = 32 字节（2×totalN）。
  *
  * 【守护策略（#175 方案A）】本套件所有用例的数值正确性由运行期独立推导的双精度
  * golden 承担，不落盘任何锚文件。主守护 = #166 AXI byte-lane 审计（硬断言零违规）
  * + 双精度 golden 容差交叉（fp32 输出：1e-2 相对容差，唯一偏差源为 fp32 累加舍入；
  * fp16/bf16 输出：同链 40→32→16 两级舍入后逐位硬断言）。MX 运行合法输出族 =
  * outFmt ∈ {0,1,2,3,4,5}（0=fp32 非量化直出，1/2=fp16/bf16 降精度直出，
  * 3/4/5=MXE4M3/MXE5M2/MXE2M1 量化输出）。fp32 直出为 WbOutCvt.fmtReg 复位
  * 默认值与 MuxCase 默认臂，无任何硬件级拒绝路径。无 dmaCScale 请求（非量化运行
  * 死路，附加断言守护）。
  * 【历史说明】原 v1 bit 锚（sim/mx/v1_anchor_M32_N32_K32.txt）已被 owner 删除
  * （双重违规：持久金锚放在只允许临时仿真产物的 sim/ 下、且被 git add -f 越过
  * .gitignore 强行跟踪；全仓从未有过 writer，不可再生）。相关代码引用已全部清除。
  *
  * 结构与 WorkerBeeSimCaseLoadWSpec 同构（loadW 水平加载 + 转置 B），差异：
  *   - aW=bW=16（16-bit lane 打包 2×8-bit 元素），cW=40（复用 fp32 延迟规格化累加器）
  *   - (#164) 寄存器重排后：控制字全部收敛到 reg5 单寄存器（旧 regs(6)+regs(7) 已删除）：
  *       reg5 = 0x11400043 + (rnd<<16) + (outFmt<<8)
  *       aFmt = reg5[3:0]   = 3 (MXE4M3，#97 DataFormat 重编号后值)
  *       bFmt = reg5[7:4]   = 4 (MXE5M2) → 3 | 4<<4 = 0x43（域内位置与旧 regs(7) 相同）
  *       cFmt = reg5[11:8]  = outFmt（fp32 直出时为 0）
  *       rnd  = reg5[18:16]（旧 regs(7)[14:12]，#164 移位 <<12 → <<16）
  *       loadWMode = reg5[28]（旧 regs(6)[28]，位号不变）；aMxBlk/bMxBlk = reg5[22]/[24]；
  *       cMxBlk = reg5[26]；warmup/warmdown = reg5[21]/[20]（#164 新增，本 suite 不置位）
  *     (#155 MJ3) 订正：原文写 regs(7)=0x65 且「aFmt=5(MXE4M3)、bFmt=6(MXE5M2)…DataFormat
  *       重编号后值」——自相矛盾：重编号后 MXE4M3=3、MXE5M2=4，而 5/6 是重编号【前】的旧值
  *       （新编号下 5=MXE2M1、6=INT32）。runMxGemm 的构造式一直写 0x43L，故本文档行是陈旧
  *       陈述而非代码缺陷。同一陈旧字面量另见 runMxGemm 的控制字自检（已随 fp32 锚用例订正）
  *       与 runMxErrCase 内的 0x65L（负向运行器，无调用点，本轮只登记不改）。
  *       (#178 订正) runMxOutFmtErrCase 的 0x65L 已改为 0x43L（与 runMxGemm 同源），
  *       并注册了真触达负向用例（outFmt=INT32 → errCode 11），本函数现已可达。
  *       (#178 补充订正) 上行对 runMxErrCase 的陈述【本身也已陈旧】：#164 已把它的
  *       形参 `regs7` 改名为 `ctl5: Long`（完整 reg5 控制字由调用方传入），函数体内
  *       【不含任何 0x65L 字面量】；且它至今【无调用点】。全文件现存 0x65 只出现在
  *       历史说明注释里，活代码已零残留 ⟹ 该项登记可结案。
  *   - 新增两条 scale 只读 DMA 外部通道（仅 hasMx 暴露）：
  *       dmaScaleA = scaleA（8-bit/beat，随激活流动）
  *       dmaScaleB = scaleB（【任务109】端口收窄为 8-bit/beat，逐字节，WorkerBeeDmaRdOnly
  *         isA=false 降序遍历；旧 scaleRowW=totalN*8=128-bit 列驻留契约已废，scaleRowW
  *         字段已从 WorkerBeeConfig 删除。下方 scale 路由模型中「128-bit 向量/列驻留」
  *         等旧描述仅作历史留痕，实际供数已改 8-bit 内存保真 + 逐拍地址硬对账）
  *
  * scale 路由模型（逐拍对齐——见各 gen*Beat 注释）：
  *   - scaleA[m][kb]：executor sLOAD_A 每 aPop.fire pop 一个 8-bit 广播全行，
  *     随激活链 skew 流入。scaleADma（WorkerBeeDmaScaleRd，A 式三级遍历）遍历序 =
  *     executor pop 序 =
  *     (round nt) × (kt 外) × (m 内)，共 nTile×kTile×M beat。
  *   - scaleB[kb][n]：executor 每个 sLOAD_B 窗口（每 kTile = 一个 32-K 块）
  *     pop 一个 totalN 向量锁存 scaleBRegs(i)→列 i（globalCol=nTile*totalN+i）。
  *     pop 序 = (round nt 外) × (kb=kt 块内)，scaleBDma（WorkerBeeDmaScaleRd
  *     isScaleB 两级遍历）按同序取址，共 nTile×kTile beat（每 (nt,kt) 一向量）。
  *
  * golden：C[m][n] = Σ_k mxE4M3ToFloat(aByte[m][k], scaleA[m][k/32])
  *                       × mxE5M2ToFloat(bByte[k][n], scaleB[k/32][n])
  * 元素值由同一 WbSimCommon.mxToFloat 重建（硬件/golden 同源），故元素级
  * 逐 bit 相等，唯一偏差是 fp32 累加舍入（远小于 1e-2 容差）。
  *
  * 既有非 MX spec 一律零改动，非 MX 路径 bit-exact 由回归门禁守护。
  * 【硬件侧消费有效门控已实施】MX inf/nan 断言（原 WbCvtOpMx，已随 FusionMac 重构删除、
  * 现由 FpCvt 的 mx8 cvt 承接）已由 consumeValid 门控（见
  * WbFusionMacFp/WbWorker），空闲/加载/排空拍垃圾输入不再撞断言 $fatal，
  * 本 suite 已恢复 ChiselSim 默认随机化（原测试侧 Randomization.uninitialized
  * 零初值规避已移除）。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

/** 【任务97】本文件原私有常量表 MxOutFmt（任务95）已整体迁移至 WbEnum.DataFormat
  * 枚举（4-bit 编码同源：FP32/FP16/BF16/Reserved3/MXE4M3/MXE5M2/MXE2M1/Reserved7/
  * INT32/INT16/INT8；原 MxFp4 对应枚举名 MXE2M1）。outFmt 参数类型化为
  * DataFormat.Type，整型编码经 outFmtInt（litValue.toInt）收敛，供控制字位域
  * 打包与 WbSimCommon 的 Int 接口消费。
  */


class WorkerBeeSimCaseMxSpec extends AnyFlatSpec with Matchers with ChiselSim with WbFstSim with WbStatusPollApi {

  // 【硬件侧门控已实施，测试侧规避已移除】MX inf/nan 断言（原 WbCvtOpMx，已随 FusionMac
  // 重构删除、现由 FpCvt 的 mx8 cvt 承接）现受
  // consumeValid 消费有效门控（WbFusionMacFp/WbWorker 单行连入，条件 =
  // MX 结果被真实消费的充要条件），空闲/加载/排空拍的随机垃圾不再触发断言。
  // 故本 suite 恢复 ChiselSim 默认随机化（不传 settings）——默认随机化下全绿
  // 即证明硬件侧门控有效，且还原真实上电语义。

  val A_BASE:       Long = 0x00000000L
  val B_BASE:       Long = 0x00100000L
  val C_BASE:       Long = 0x00200000L
  val SCALE_A_BASE: Long = 0x00300000L
  val SCALE_B_BASE: Long = 0x00400000L
  // 量化输出（outFmt∈{3,4,5}）scale 写回基址（ScaleC 索引 8），与 scaleA/B 基址不重叠
  val SCALE_C_BASE: Long = 0x00500000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/mx"

  behavior of "WorkerBee MXFP8 GEMM Simulation"

  it should "run MX single-block GEMM M=32 N=32 K=32 E4M3xE5M2" in {
    runMxGemm(M = 32, N = 32, K = 32,
      waveName = "workerbee_mx_sim.fst",
      testMangle = "should-run-MX-single-block-GEMM-M-32-N-32-K-32-E4M3xE5M2",
      // fp32 256-bit 下 2 拍/行，窗口按新拍数几何归档（放宽不放松断言）。
      //outFmt = DataFormat.MXE4M3, 
      outFmt = DataFormat.FP16, 
      blkSel = 0,   // 【#144】原 blkSel=1（旧枚举=block16）→ 新枚举 block16=sel0；本用例 outFmt=FP16 非量化，blkSel 不参与结果，仅保持声明块=16 的原意且 ≤ totalN
      expCycleWindow = Some((120, 800)))
  }

  // ==========================================================================
  // (#175) SECOND registration -- outFmt = FP32 direct output.
  // --------------------------------------------------------------------------
  // Same stimulus (M=N=K=32, E4M3xE5M2) as the FP16 case above, exercising the
  // 40->32 normalization path against the runtime-derived double-precision golden
  // (1e-2 relative tolerance). Primary numerical guard = golden cross-check.
  // Test name retains "v1-anchor" as historical nomenclature (testMangle/waveName
  // coupling at :1082/:1086); the anchor file itself has been removed per owner
  // decision (#175). FP32 output remains fully supported hardware (WbOutCvt
  // fmtReg default + MuxCase default arm).
  // expCycleWindow = (120, 800): derived from fp32 2-beats-per-row geometry.
  // blkSel = 0: outFmt=FP32 is NOT quantized (isQuant=false), so blkSel does not
  //   enter the result; 0 keeps the declared block at 16 (<= totalN).
  // ==========================================================================
  it should "run MX single-block GEMM M=32 N=32 K=32 E4M3xE5M2 fp32 v1-anchor" in {
    runMxGemm(M = 32, N = 32, K = 32,
      waveName = "workerbee_mx_sim_fp32_anchor.fst",
      testMangle = "should-run-MX-single-block-GEMM-M-32-N-32-K-32-E4M3xE5M2-fp32-v1-anchor",
      outFmt = DataFormat.FP32,
      blkSel = 0,
      expCycleWindow = Some((120, 800)))
  }

  // ==========================================================================
  // (#178) THIRD registration -- negative case: outFmt = INT32 triggers errCode 11.
  // --------------------------------------------------------------------------
  // Same MX stimulus (M=N=K=32, E4M3xE5M2) but with an integer output format.
  // INT32 is NOT in the MX legal output family {0,1,2,3,4,5}, so runMxGemm
  // dispatches to runMxOutFmtErrCase. The hardware errCode 11 (int/non-int
  // cross-boundary XOR) fires because:
  //   runIsInt(aFmt=MXE4M3) = false (MX input -> not integer runtime)
  //   cIsIntOut(cFmt=INT32) = true  (INT32 is integer output)
  //   false XOR true = true -> errCode 11
  // Exhaustive exclusion of higher-priority codes:
  //   Fix-4 code3: isMx4(E4M3)=false, isMx4(E5M2)=false -> false!=false = NO
  //   code8: K=32, kPerBeat(MXE4M3,16)=32, 32%32=0 -> NO
  //   code10: C_BASE=0x200000, 0x200000%32=0 -> NO
  //   code7: m=32, cBufferDepth=2048, 32>2048-16=2032? NO
  //   code5: aFmt=MXE4M3!=FP32, bFmt=MXE5M2!=FP32 -> NO
  //   code4: loadWMode=1 -> NO
  //   code3(xor): isMx(E4M3)=true, isMx(E5M2)=true -> true^true=false -> NO
  //   code0: aMxBlk=16<=kLoadNum=32, bMxBlk=16<=32, cFmt not MX -> NO
  // Therefore errCode 11 is the ONLY code that fires.
  // ==========================================================================
  it should "reject MX E4M3xE5M2 outFmt INT32 errCode 11" in {
    runMxGemm(M = 32, N = 32, K = 32,
      waveName = "workerbee_mx_sim_errcode11.fst",
      testMangle = "should-reject-MX-E4M3xE5M2-outFmt-INT32-errCode-11",
      outFmt = DataFormat.INT32,
      blkSel = 0,
      expCycleWindow = None)
  }

  
  // ==========================================================================
  // 共享运行器：配置 MX WorkerBee，逐拍供 A/B/C/scaleA/scaleB，golden 比对。
  // 【任务95】outFmt 参数化（4-bit 编码，见 WbEnum DataFormat 枚举）：
  //   - outFmt ∈ {0,1,2,3,4,5}（MX 合法族）：0=fp32 直出（锚+golden 双守护，默认）；
  //     1/2=fp16/bf16 降精度直出；3/4/5=量化输出——期望链 40→32 规格化 → swQuantBlock（码+块 scale），
  //     dmaC 密集码 + dmaCScale 块 scale 双通道逐拍地址/数据硬对账，
  //     另按 scale 反量化做容差交叉（第二视角，冲刷界豁免）；
  //   - 其余编码（MX 非法族）：Executor 族交叉校验 execute 拍 config-err →
  //     分派 runMxOutFmtErrCase 负向断言（err 置位、无 done、dmaC/dmaCScale 零写请求）。
  //   - blkSel：量化块大小（【#144】新枚举 0=16/1=32 元素/块，仅量化生效，须 ≤ totalN
  //     且整除——硬件同校验 config-err，此处前置 require 防呆）；
  //   - rnd：运行期舍入（0=RNE/1=RTZ/2=RDN/3=RUP/4=RMM，量化末级用）；
  //   - cScaleBackpressure：dmaCScale req.ready 每 3 拍关 1 拍（量化防丢拍验证）。
  //   默认参数（outFmt=0/blkSel=0/rnd=0）下 (#164) reg5 = 0x11400043：低 12 位 = 0x43
  //   （= MXE4M3(3)|MXE5M2(4)<<4；(#155 MJ3) 订正：原文写 0x65，为 #97 重编号前的陈旧值），
  //   loadWMode 在 reg5[28]、aMxBlk/bMxBlk 在 reg5[22]/[24]（三者均为 sel=1=M32 与 loadWMode=1）。
  //   （#164 前：loadWMode/MxBlk 在 regs(6)、格式字在 regs(7)，现已合并。）
  // ==========================================================================
  private def runMxGemm(M: Int, N: Int, K: Int, waveName: String, testMangle: String,
      expCycleWindow: Option[(Int, Int)] = None,
      outFmt: DataFormat.Type = DataFormat.FP32, blkSel: Int = 0, rnd: Int = 0,
      cScaleBackpressure: Boolean = false): Unit = {

    val cfg = WorkerBeeConfig(
      arrayN = 8,
      clusterM = 2,                 // totalN = 16
      aW = 16, bW = 16, cW = 40,      // 与出厂 cfgMx（WorkerBeeElaborate）及 err 用例对齐
      // (#155 MJ1) 不传 supportedFmts ⟹ 走 WorkerBeeConfig 默认集，该默认集自本轮起
      //   【已含 MXE2M1】⟹ hasMx4=true，自动满足新 require(!hasMx8 || hasMx4)。
      //   下面这行历史注释掉的 fp8-only 写法【不要取消注释】：MXE2M1 已是默认必选，
      //   显式传 fp8-only 集合会在 cfg 构造期直接抛 IllegalArgumentException。
      // (#66 订正) 上行原写「（7 种格式）」已过时：默认集现为 5 种
      //   {INT8, INT4, MXE4M3, MXE5M2, MXE2M1}——FP16/BF16/INT16 已被砍除，故
      //   hasFp=false、hasInt 仍为 true、hasMx/hasMx8/hasMx4 仍全为 true。
      //   本 suite 不受影响且实测 2/2 仍绿：用例是 MX 族【输入】+ FP16/FP32【输出】，
      //   而 16-bit 输出走 WbOutCvt/FpCvt 的 40→32→16 两级舍入链，【不依赖 hasFp】
      //   （被砍的只是 FP16/BF16 作输入/计算格式）。hasMx4=true 这条本 suite 真正
      //   依赖的谓词未变 ⟹ 上方 require 与全部地址/拍数推导逐字仍成立。
      // 【P2 纯 FP8 订正】默认集现为 7 种（+FP8E4M3/FP8E5M2 ⟹ hasFp8=true，remap/
      //   127 注入/errCode FP8 臂 elaborate）；MX 用例不触发 FP8 臂，行为零变化。
     // supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2),
      aBufferDepth = 64,
      bBufferDepth = 64,
      cBufferDepth = 64,
      scaleBufferDepth = 64
    )

    val totalN = cfg.totalN         // 16
    val aEffW  = cfg.aEffW          // 16（v2：16-bit lane 打包 2×8-bit 元素）
    require(K % 32 == 0, s"MX 用例 K($K) 必须 32 对齐")
    val kBlocks = K / 32            // scale 块数
    // 【任务95】outFmt 参数防呆 + 族合法性分派（与 Executor mxFmtOk 同源）。
    // 【任务97】outFmt 类型化为 DataFormat.Type：Scala 级族判断用枚举 ==（返回硬件
    // Bool 的 DataFormat.isQuant 不能用于 Scala if 分派）；整型编码统一收敛到 outFmtInt。
    val isQuant = outFmt == DataFormat.MXE4M3 || outFmt == DataFormat.MXE5M2 || outFmt == DataFormat.MXE2M1
    // 【任务98】fp16/bf16 合法化：MX 输入运行允许降精度直出（cvt 两级舍入链
    // 40→32 规格化 + 32→16 rnd 降精度、2B/元素密集布局），与 Executor mxFmtOk
    // 白名单同步扩为 {0,1,2,3,4,5}；int 族与 reserved 仍走负向 runMxOutFmtErrCase
    // （硬件 errCode 11 拒绝，负向语义自洽；#178 订正：原写 errCode 6 为陈旧值）。
    // anchor 忽略语义：v1 锚是 fp32 位录制，fp16/bf16 位模式不可对账 → 锚守护
    // 仅 outFmt=0 生效（下方锚块门控）；用户注释掉的 anchor 守卫 require 保持注释态。
    val isFp16Out = outFmt == DataFormat.FP16 || outFmt == DataFormat.BF16
    val mxFmtOk = outFmt == DataFormat.FP32 || isFp16Out || isQuant   // MX 运行合法族 {0,1,2,3,4,5}
    val outFmtInt = outFmt.litValue.toInt  // 枚举 → 4-bit 整型编码（控制字位域打包专用；【任务104】WbSimCommon 已枚举化，地址/拍数/量化调用直传 outFmt）
    val blk = 16 << blkSel   // 【#144】新枚举 {M16=0,M32=1} → blk=16<<blkSel（旧 8<<blkSel 已废）
    // 【任务97】原 Int 范围校验（outFmt 0..15）随类型化作废：DataFormat 枚举已约束
    // 合法值域 0..10；枚举无法表达 11..15 未定义码（本文件无 11..15 使用）。
    require(blkSel >= 0 && blkSel <= 1, s"blkSel($blkSel) must be 0..1 (block 16/32)")
    require(rnd >= 0 && rnd <= 4, s"rnd($rnd) 必须 0..4（RNE/RTZ/RDN/RUP/RMM）")
    if (isQuant) {
      require(blk <= totalN && totalN % blk == 0,
        s"量化块 blk($blk) 必须 ≤ totalN($totalN) 且整除（硬件同校验 config-err）")
      if (outFmt == DataFormat.MXE2M1) require(N % 2 == 0,
        s"mxfp4 半字节/元素，N($N) 必须偶数（行宽整字节，硬件同校验 config-err）")
    }
    require(!cScaleBackpressure || isQuant,
      "cScaleBackpressure（dmaCScale 间歇反压）仅量化运行（outFmt∈{3,4,5}）有意义")
    // 【任务90 收敛：fp32 直出为合法受守护路径（P0 契约后保留）】格式族交叉校验批准版 =
    // MX 运行 outFmt ∈ {0,1,2,3,4,5}（0000=fp32 非量化直出；「MX 强制量化」严格版已否决）。
    // v1 锚录于 fp32 输出 → 本套件恢复 fp32 直出 + 逐位守护；P3 迁移仅换解析器与密集地址序列。
    val beatsPerRow = WbSimCommon.cBeatsPerRow(outFmt, totalN, cfg.cDmaExtW)  // fp32 2 拍/行；【任务98】fp16/bf16 与量化族 1 拍/行（2B/元素，totalN*16=256 恰满拍）

    println(s"debug0 mxFmtOk=$mxFmtOk")
    if (!mxFmtOk) {
      // 【任务95→98】MX 非法 outFmt（int 族/reserved）：整数输出对 MX 输入无意义，
      // Executor errCode 11 拒绝（#178 订正：原写 errCode 6 为陈旧值；fp16/bf16 已合法化改走 E2E）→ 无数值期望可对，
      // 分派族交叉闸门负向运行器（同 runMxErrCase 轮询范式），语义自洽。
      runMxOutFmtErrCase(M, N, K, outFmt, waveName, testMangle)
      return
    }

    // ---- scale 指数与元素内蕴值（确定性，含正负、覆盖多个块/列以暴露路由错位） ----
    def scaleAExp(m: Int, kb: Int): Int = ((m + kb) % 3) - 1        // {-1,0,1}
    def scaleBExp(kb: Int, n: Int): Int = ((n + 2 * kb) % 3) - 1    // {-1,0,1}
    val scaleA = Array.tabulate(M, kBlocks)((m, kb) => WbSimCommon.mxScaleByte(scaleAExp(m, kb)))
    val scaleB = Array.tabulate(kBlocks, N)((kb, n) => WbSimCommon.mxScaleByte(scaleBExp(kb, n)))

    def aValF(m: Int, k: Int): Double = ((((m * 7 + k * 3) % 11) - 5).toDouble) * 0.25   // ~[-1.25,1.25]
    def bValF(k: Int, n: Int): Double = ((((k * 5 + n * 2) % 9)  - 4).toDouble) * 0.5    // ~[-2,2]
    val aByte = Array.tabulate(M, K)((m, k) => WbSimCommon.floatToMxE4M3(aValF(m, k)))
    val bByte = Array.tabulate(K, N)((k, n) => WbSimCommon.floatToMxE5M2(bValF(k, n)))
    // 内存物理布局：B 转置存储（行=n、列=k），BT(n)(k)=bByte(k)(n)
    val bByteTransposed = Array.tabulate(N, K)((n, k) => bByte(k)(n))

    // --- golden 累加 + fp32 期望链（与锚录制数值链逐字一致：40→32 固定简化 RNE 规格化）---
    def refSum(m: Int, gc: Int): Double =
      (0 until K).map { k =>
        val kb = k / 32
        WbSimCommon.mxE4M3ToFloat(aByte(m)(k), scaleA(m)(kb)) *
          WbSimCommon.mxE5M2ToFloat(bByte(k)(gc), scaleB(kb)(gc))
      }.sum
    // 期望 fp32 bits 行（totalN 元素，gc≥N 补 0）：与硬件同链重建，供逐位守护与密集拍重组共用。
    def expectedFp32Row(nt: Int, m: Int): Seq[Long] = {
      (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        val sum = if (gc < N) refSum(m, gc) else 0.0
        val fp32Bits = java.lang.Float.floatToRawIntBits(sum.toFloat) & 0xFFFFFFFFL
        WbSimCommon.swFpNormalize40to32(WbSimCommon.fp32BitsTo40(fp32Bits))
      }
    }
    // 【任务98】fp16/bf16 期望行（与硬件 cvt 两级舍入链逐位同构：40→32 规格化 →
    // fp32ToFp16Rnd/fp32ToBf16Rnd 运行期 rnd 降精度；swFp32ToFp16/swFp32ToBf16
    // 为 WbOutCvtSpec 16 用例背书的逐位同构软件模型，含溢出饱和/下溢 FTZ/
    // inf/nan 处理）。返回 16-bit 元素位模式行，供 elemsToDenseRow（2B/元素
    // 密集布局）与 DMA-C 逐拍数据硬断言共用（主守护）；解码容差交叉见下方第二视角。
    def expectedFp16Row(nt: Int, m: Int): Seq[Long] = {
      expectedFp32Row(nt, m).map { bits =>
        val half = if (outFmt == DataFormat.FP16) WbSimCommon.swFp32ToFp16(bits, rnd)
                   else WbSimCommon.swFp32ToBf16(bits, rnd)
        half.toLong & 0xFFFFL
      }
    }
    // 【任务95】量化期望行（与硬件两级舍入链同链：40→32 规格化 → swQuantBlock）：
    // 返回 (逐元素量化码, 每 8 元素组 1 字节 E8M0 scale)；padding 列（≥ nEffTile）
    // 不进 amax、元素码输出 0（swQuantBlock 内建掩码，与 QuantSpec 同范式）。
    def expectedQuantRow(nt: Int, m: Int): (Seq[Int], Seq[Int]) = {
      val nEffTile = math.min(totalN, N - nt * totalN)
      WbSimCommon.swQuantBlock(expectedFp32Row(nt, m), rnd, outFmt, blkSel, nEffTile)
    }

    Files.createDirectories(Paths.get(simDir))

    // ==================== DMA-A (A buffer) 地址/数据（v2：16-bit lane 打包 2 元素） ==
    // v2：kTile 数 = K/32（16 lane × 2 元素/拍 = 一个 32-K scale 块 = 单 pass）；
    // 行步长 = K 字节（1 字节/元素打包）；k 窗口步进 aColOff = totalN*2 = 32 字节。
    val aKTiles = K / 32
    val aNTiles = (N + totalN - 1) / totalN
    val aRowOff = K.toLong                          // 打包行步长 = K 字节
    val aColOff = totalN.toLong * (cfg.aW / 8)      // = 2*totalN = 32 字节
    val expAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expAAddrSeq += A_BASE + m * aRowOff + kt * aColOff
    val dmaATotalBeats = expAAddrSeq.size
    // lane i = {aByte[m][kt*32+2i+1] 高字节, aByte[m][kt*32+2i] 低字节}
    // （低=偶 k，高=奇 k，与 PE 级联先偶后奇逐拍消费对齐）
    def genDmaABeat(idx: Int): BigInt = {
      val kt = (idx % (aKTiles * M)) / M
      val row = idx % M
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val kEven = kt * 32 + 2 * i
        val kOdd  = kEven + 1
        val lo = if (row < M && kEven < K) aByte(row)(kEven) else 0
        val hi = if (row < M && kOdd  < K) aByte(row)(kOdd)  else 0
        data = data | (BigInt(((hi & 0xFF) << 8) | (lo & 0xFF)) << (i * aEffW))
      }
      data
    }

    // ==================== DMA-B (B 权重) 地址/数据（转置闭式，v2 打包） ===========
    // v2：kTile 数 = K/32；行步长 = K 字节；k 窗口步进 kSliceStep = 2*totalN = 32
    // 字节；块间跳转（bNextKTileAddressOffset）= totalN*(bRowOff+2)，由逐 beat
    // 地址硬断言钉死（多 kTile 用例覆盖）。
    val bKTiles = K / 32
    val nTilesB = (N + totalN - 1) / totalN
    val bRowOff = K.toLong                          // 打包行步长 = K 字节
    val bColTileOff = totalN.toLong * K             // 轮间跳 = totalN 个转置行 × K 字节
    val kSliceStep = totalN.toLong * (cfg.bW / 8)   // = 2*totalN = 32 字节（k 窗口步进）
    val expBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until bKTiles) {
      val blockStart = B_BASE + r.toLong * bColTileOff + (totalN - 1).toLong * bRowOff + kt.toLong * kSliceStep
      for (r2 <- 0 until totalN) expBAddrSeq += blockStart - r2 * bRowOff
    }
    val dmaBTotalBeats = expBAddrSeq.size
    // lane i = pack{bByte[kt*32+2i+1][n] 高, bByte[kt*32+2i][n] 低}；n 降序协议不变。
    def genDmaBWeightBeat(idx: Int): BigInt = {
      val beatsPerRound = bKTiles * totalN
      val nTileIdx = idx / beatsPerRound
      val idxInRound = idx % beatsPerRound
      val kt = idxInRound / totalN
      val s = idxInRound % totalN
      val n = nTileIdx * totalN + (totalN - 1 - s)
      var data = BigInt(0)
      for (i <- 0 until totalN) {
        val kEven = kt * 32 + 2 * i
        val kOdd  = kEven + 1
        val lo = if (kEven < K && n < N) bByte(kEven)(n) else 0
        val hi = if (kOdd  < K && n < N) bByte(kOdd)(n)  else 0
        data = data | (BigInt(((hi & 0xFF) << 8) | (lo & 0xFF)) << (i * cfg.bW))
      }
      data
    }

    // ==================== DMA-C (C store) 地址+数据（P3 迁移：密集布局 + fp32 直出硬对账） ====
    // 【任务90 收敛】outFmt=0（fp32 直出）：256-bit 下 2 拍/行，拍内地址步进 32B；
    // 块间跳转 = 张量语义绝对式 nt×cColOff + 拍内细分，四套件共用唯一推演源
    // （#133 订正旧注「三角累积闭式」：那是把当时缺陷 RTL 的行为当规范）。
    // 【任务95】outFmt 参数化：量化族走 cAddrSeqDense 量化布局（mxfp8 行步进 N×1B、
    // mxfp4 N>>1；拍内步进 cBeatAddrStep 同源）；fp32 路径与原逐字一致。
    val expCAddrSeq = WbSimCommon.cAddrSeqDense(C_BASE, M, N, totalN, outFmt, cfg.cDmaExtW)
    def expCDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / (M * beatsPerRow)
      val inBlock = beatIdx % (M * beatsPerRow)
      val row = inBlock / beatsPerRow
      val beatInRow = inBlock % beatsPerRow
      val elemRow: Seq[Long] =
        if (isQuant) expectedQuantRow(nt, row)._1.map(_.toLong)
        else if (isFp16Out) expectedFp16Row(nt, row)   // 【任务98】fp16/bf16 16-bit 位模式（与 cvt 逐位同构）
        else expectedFp32Row(nt, row)
      val denseRow = WbSimCommon.elemsToDenseRow(elemRow, outFmt, totalN = totalN)
      // 【#166】lane 偏移由【金锚期望地址】expCAddrSeq(beatIdx) 推出，【不读硬件实收
      //   addr】——后者会把期望值退化成 RTL 的镜像，违反金锚铁律（#133 同类教训）。
      //   推导链：addr mod dmaBytes = 本拍在总线字内的起始 byte lane（AXI 契约），
      //   denseRowBeat 再把密集段左移同样多的 lane ⟹ 与 main 侧 wdata/wstrb 同源移位一致。
      val laneOff = WbSimCommon.cLaneOffset(expCAddrSeq(beatIdx), cfg.cDmaExtW)
      WbSimCommon.denseRowBeat(denseRow, beatInRow, cfg.cDmaExtW, laneOff)
    }

    // ==================== DMA-CSCALE：按 isQuant 分派 ====
    // 【任务90 收敛】fp32 直出非量化运行 → 该通道为死路，仅保留「全程零请求」负向守护。
    // 【任务95】量化运行（outFmt∈{3,4,5}）→ 双通道写回：地址线性模型
    //   base + nt×(totalN/blk) + m×ceil(N/blk)（与 WorkerBeeDmaScaleWr 同源），
    //   数据 = 每 8 元素组 1 字节 E8M0 scale（scaleBytesPerRow = ceil(N/blk) 字节）；
    //   主循环逐拍地址/数据硬对账（runMxQuantGemm 同范式，W-Adam2 背压可选）。
    val nTilesC = (N + totalN - 1) / totalN
    val scaleRowOff = (N + blk - 1).toLong / blk          // scale 行步进 = ceil(N/blk) 字节
    val scaleColOff = totalN.toLong / blk                 // 列 tile 步进 = totalN/blk 字节
    val scaleBytesPerRow = scaleRowOff.toInt
    val expCScaleAddrSeq: Seq[Long] =
      if (isQuant) for (nt <- 0 until nTilesC; m <- 0 until M)
        yield SCALE_C_BASE + nt * scaleColOff + m * scaleRowOff
      else Seq.empty
    def expCScaleDataBeat(beatIdx: Int): BigInt = {
      val nt = beatIdx / M
      val row = beatIdx % M
      val (_, scales) = expectedQuantRow(nt, row)
      var data = BigInt(0)
      for ((s, g) <- scales.zipWithIndex if g < scaleBytesPerRow)
        data = data | (BigInt(s & 0xFF) << (8 * g))
      data
    }

    // ==================== DMA-scaleA (scaleA) 数据：32-bit/beat ========================
    // 遍历序（isA=true）= executor pop 序 = (round nt)×(kt 外)×(m 内)。
    // 每逻辑 beat = scaleA[m][kb]；v2 单 pass：一个 kTile = 一个 32-K scale 块 → kb = kt。
    // 【修复（taskId=86）】地址独立从张量语义推导（与 QuantSpec 同源），不再镜像 A 数据：
    //   外部布局 scaleA[m][kb] 连续（每行 K/32 字节），地址 = SCALE_A_BASE + m×(K/32) + kb×1。
    // 【P0-1 gather】scale 端口 = 32-bit（4B 对齐量子）。读 DMA 对非对齐逻辑拍发
    //   【对齐多发读】：物理序列 = off==0 → [L]；off!=0 → [L-off, L-off+4]（DMA 内部
    //   右移 off 字节重组，见 WorkerBeeDmaRdOnly gather 段）。期望序列按此契约展开为
    //   物理序列；供数按【物理地址】返回完整 4 字节对齐字（张量字节映像，越界补 0），
    //   绝不只发 byte0 —— 重组取的是对齐字的第 off 字节。
    def gatherPhys4(addr: Long): Seq[Long] = {
      val off = addr % 4
      if (off == 0) Seq(addr) else Seq(addr - off, addr - off + 4)
    }
    val scaleARowOff = kBlocks.toLong             // 行步进 = K/32 字节
    val scaleAColOff = 1L                         // 块（kb）步进 = 1 字节
    val scaleATensorBytes = M.toLong * kBlocks    // 真实张量字节数 = M×(K/32)
    val expScaleAAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (_ <- 0 until aNTiles; kt <- 0 until aKTiles; m <- 0 until M)
      expScaleAAddrSeq += SCALE_A_BASE + m * scaleARowOff + kt * scaleAColOff
    val expScaleAPhysSeq = expScaleAAddrSeq.toSeq.flatMap(gatherPhys4)
    val scaleATotalBeats = expScaleAPhysSeq.size
    def genScaleAWordByAddr(addr: Long): BigInt = {
      var w = BigInt(0)
      for (b <- 0 until 4) {
        val off = addr + b - SCALE_A_BASE
        if (off >= 0 && off < scaleATensorBytes) {
          val m = (off / scaleARowOff).toInt
          val kb = (off % scaleARowOff).toInt
          w |= BigInt(scaleA(m)(kb) & 0xFF) << (8 * b)
        }
      }
      w
    }

    // ==================== DMA-scaleB (scaleB) 数据：8-bit/beat ======================
    // 【任务109】main 侧裁定：dmaScaleB 端口从旧 scaleRowW(=totalN*8=128-bit) 收窄为
    // 硬编码 8-bit（WorkerBee.scala L46），scaleRowW 已从 WorkerBeeConfig 删除。驱动
    // DMA 仍是 WorkerBeeDmaRdOnly(bufWidth=8, isA=false)（WorkerBee.scala L108），与
    // dmaB 权重通道同构，逐拍地址生成完全一致：
    //   start 门控 = calcConfig.startOut & isMx(regFile.bFmt)（WorkerBee.scala L471）；
    //   lineTarget = totalN(=16)（WorkerBeeDmaRdOnly L174）；每拍搬 1 字节；块内地址
    //   降序 -rowStep（L202）；两级遍历 kTile(reg_kTile=calcConfig.kTile=K/32) 内 ×
    //   nTile(reg_nTile) 外；loadWMode=1 下 switch-K 跳 colTileOff（L258）、
    //   switch-n 跳 colTileOff+(totalN-1)*rowOff（L241）。
    // rowStep/colTileOff 直取 calcConfig 的 scaleB 偏移（与硬件同源，WorkerBeeCalcConfig
    //   L199-214）：scaleBRowAddressOffset = K/blkB、scaleBColTileAddressOffset =
    //   bKLoadNum/blkB；blkB 由 regFile.bMxBlockSize 决定（【#144】MXBlockSize 收窄后
    //   (#164) reg5[24]=1=M32 → blkB=32，见下方 controlWord5 打包），
    //   bKLoadNum = getKLoadNum(bFmt=MXE5M2, mx8) = totalN*2 = 32。
    val blkB = 16 << 1                                 // bMxBlockSize=1(新枚举 M32) → 32（(#164) reg5[24] 硬编码 1L<<24）
    val bKLoadNum = totalN * 2                         // bFmt 为 mx8 → totalN*2 = 32
    val scaleBRowOff     = (K / blkB).toLong           // = calcConfig.scaleBRowAddressOffset
    val scaleBColTileOff = (bKLoadNum / blkB).toLong   // = calcConfig.scaleBColTileAddressOffset
    val scaleBKTiles     = bKTiles                     // reg_kTile = calcConfig.kTile = K/32
    // 期望取址序列：镜像 WorkerBeeDmaRdOnly(isA=false, loadWMode=1) 遍历（与上方
    // expBAddrSeq 同构，仅偏移换 scaleB 的 rowOff/colTileOff）：
    //   block(nTile=r, kTile=kt) 首址 = base + r*nTileJump + (totalN-1)*rowOff + kt*colTileOff
    //   nTileJump = (totalN-1)*rowOff + kTile*colTileOff（switch-n 增量，见 RTL L241）
    //   块内 totalN 拍降序 -rowOff。总 beat = nTile*kTile*totalN。
    val scaleBNTileJump = (totalN - 1).toLong * scaleBRowOff + scaleBKTiles.toLong * scaleBColTileOff
    val expScaleBAddrSeq = scala.collection.mutable.ArrayBuffer[Long]()
    for (r <- 0 until nTilesB; kt <- 0 until scaleBKTiles) {
      val blockStart = SCALE_B_BASE + r.toLong * scaleBNTileJump +
        (totalN - 1).toLong * scaleBRowOff + kt.toLong * scaleBColTileOff
      for (r2 <- 0 until totalN) expScaleBAddrSeq += blockStart - r2.toLong * scaleBRowOff
    }
    // 【P0-1 gather】同 scaleA：逻辑序列按 4B 对齐契约展开为物理序列（总 beat 增加）
    val expScaleBPhysSeq = expScaleBAddrSeq.toSeq.flatMap(gatherPhys4)
    val scaleBTotalBeats = expScaleBPhysSeq.size       // 逻辑 = nTile*kTile*totalN，物理 = +gather 额外读
    // scale 内存映像（内存保真）：【#61 修复】scaleB 按【n-major】组织。硬件 scaleB DMA
    //   （WorkerBeeDmaRdOnly isA=false, loadWMode=1）逐 beat 地址 = base + n×kBlocks + kb
    //   （n=全局列、kb=kTile=scale 块号；与 scaleA 的 m×kBlocks+kb 对称），故 byte(n×kBlocks+kb)=scaleB[kb][n]。
    //   旧解码误按 [kb][n]（kb-major，kb=off/N、n=off%N）：kBlocks=1（K=blk 单块）两布局重合而侥幸绿，
    //   kBlocks>=2 多 pass 发散 → 第二 pass 各列收到错误 scaleB 字节 → 累加错 → fp32 cross-check/DMA-C 失配。
    //   RTL 与金锚均正确。与 WorkerBeeSimCaseMxFp4Spec 头部注释 + genScaleBBeatByAddr n-major 范式对齐（三 runner 一致）。
    //   按 req.addr 从映像取字节返回（越界补 0），绝不按 beat 序号顺序发数（历史教训：按序发数会掩盖供数序错位）。
    val scaleBTensorBytes = kBlocks.toLong * N
    // 【P0-1 gather】供完整 4 字节对齐字（张量字节映像 n-major，越界补 0）：DMA 对非对齐
    //   逻辑拍读对齐字后右移 off 字节重组，取的是对齐字第 off 字节，故不能只发 byte0。
    def genScaleBWordByAddr(addr: Long): BigInt = {
      var w = BigInt(0)
      for (b <- 0 until 4) {
        val off = addr + b - SCALE_B_BASE
        if (off >= 0 && off < scaleBTensorBytes) {
          val n  = (off / kBlocks).toInt             // n-major：off = n×kBlocks + kb
          val kb = (off % kBlocks).toInt
          w |= BigInt(scaleB(kb)(n) & 0xFF) << (8 * b)
        }
      }
      w
    }

    println(s"[MX] Starting: M=$M N=$N K=$K totalN=$totalN kBlocks=$kBlocks")
    println(s"[MX] beats: dmaA(A)=$dmaATotalBeats dmaB(B)=$dmaBTotalBeats C=${expCAddrSeq.size} scaleA=$scaleATotalBeats scaleB=$scaleBTotalBeats")

    // 【任务94】失败路径也 dump 波形：块内断言（DMA 地址/数据对账、锚守护等）抛出
    // 异常时，旧代码 copyTrace 位于 simulate 块之后、永不执行 → sim/ 无波形可查。
    // 将 simulate 包入 try/finally：无论 pass/fail，块退出后都执行转存，异常自然
    // 向上重抛保持 ScalaTest fail 语义；成功路径的转存时机（块退出后）与旧代码
    // 完全一致，不破坏 sim/*.fst 固定路径依赖。
    try {
    simulate(new WorkerBee(cfg)) { dut =>

      var dmaABeatsSent = 0
      var dmaCStoreBeats = 0
      var dmaBBeatsSent = 0
      var scaleABeatsSent = 0
      var scaleBBeatsSent = 0
      // 【任务95】dmaCScale 计数语义按运行分派：量化 = 写拍数（fire 拍，须 = nTilesC×M）；
      // 非量化 = 「全程零请求」负向计数（【任务90 收敛】守护不变）。
      var dmaCScaleReqs = 0
      val scaleBAddrLog = scala.collection.mutable.ArrayBuffer[Long]()  // scaleB DMA 实际取址序列（逐 beat 对账）
      // 【#166】dmaC 写回日志升为 (addr,data,strb) 三元组：旧口径从不 peek strb，
      //   使「strb 已移位、wdata 未同源移位」在 TB 侧长期不可见。
      val cWriteLog = scala.collection.mutable.ArrayBuffer[WbSimCommon.CWriteBeat]()
      // 【任务95】dmaCScale 实际写回序列（量化 dequant 交叉第二视角用，非量化恒空）
      val scaleWriteLog = scala.collection.mutable.ArrayBuffer[(Long, BigInt)]()

      // ===== Reset =====
      dut.reset.poke(true.B)
      dut.io.cmd.foreach(_.valid.poke(false.B))
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaA.rsp.payload.data.poke(0.U)
      dut.io.dmaA.rsp.payload.err.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaC.rsp.payload.err.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaB.rsp.payload.data.poke(0.U)
      dut.io.dmaB.rsp.payload.err.poke(false.B)
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

      def sendCmd(op: WorkerBeeOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        transactReg(dut.clock, dut.io.cmd(0), dut.io.resp, op, reg0Addr, reg1Addr, BigInt(data0), BigInt(data1))
      }

      // ===== 配置寄存器（【任务90 收敛】fp32 直出默认；【任务95】outFmt 参数化叠加） =====
      // (#164) 寄存器重排：旧 regs(6)（loadWMode + 三 MxBlk + 三 addrMode + 三 stride）与
      //   旧 regs(7)（rnd + 三 fmt）已合并为单一 reg5，旧 reg6/reg7 在 main 侧 wregs 里
      //   无 is(6)/is(7) 分支 ⟹ 写为 no-op、读回 0（Chisel switch 无 otherwise 即 no-op）。
      // MX 基础位 E4M3×E5M2：aFmt → reg5[3:0]、bFmt → reg5[7:4]（域内位置与旧 regs(7) 相同）；
      // loadWMode=1 在 reg5[28]（MX 下缺省即 errCode 4）；aMxBlk/bMxBlk = reg5[22]/[24]。
      // 按参数叠加位域（与 WbInterface.wregs is(5.U) 同源）：rnd → reg5[18:16]（★#164：旧 <<12
      //   → 新 <<16；默认 RNE=0 不置位）、outFmt(cFmt) → reg5[11:8]、blkSel(cMxBlk) → reg5[26]。
      // ★(#164 裁定 4) outFmtInt 的掩码由 `& 0x7` 修为 `& 0xF`：DataFormat 是 4-bit 域（枚举 10 值），
      //   3-bit 掩码会把 INT8(8)→0=FP32、INT4(9)→1=FP16 静默截断，而 FP32/FP16 恰好落在 MX
      //   输入的【合法】输出族 {0,1,2,3,4,5} 里 ⟹ 负向用例 runMxOutFmtErrCase 会被静默变成
      //   正向（硬件不置 err）。金锚 WbDebugModel.controlWord5 一直用 `& 0xF`，本处为既存缺陷。
      // ★(#155 MJ3) 订正本段注释与下方自检的陈旧字面量 0x65：
      //   0x65 = 5 | 6<<4 是【#97 之前】的 DataFormat 编号（MXE4M3=5、MXE5M2=6）。
      //   #97 把枚举重编号为 FP32=0/FP16=1/BF16=2/MXE4M3=3/MXE5M2=4/MXE2M1=5/INT32=6/…，
      //   于是同一个 E4M3×E5M2 组合的基础位变成 3 | 4<<4 = 0x43 —— 下方构造式写的正是
      //   0x43L，而自检的期望值却漏改、仍写 0x65L。
      //   git 归因（read-only，未做任何 git 写）：commit 672fb37「debug mx」(Sep 8) 同一笔里
      //     · 把构造式的基础位改成 0x43L，
      //     · 把自检期望从 0x5E0L（v1 布局，与锚文件头 "regs7=0x5E0" 对得上）改成 0x65L，
      //     · 并把唯一现役用例的 outFmt 从 FP32 改成 FP16。
      //   第三条使自检的前件 (outFmt==FP32 && blkSel==0 && rnd==0) 恒假 ⟹ 0x43 vs 0x65 的
      //   矛盾被【不可达性】掩盖至今。#155 MJ3 新增的 fp32 锚用例让它重新可达，实测立刻抛：
      //     java.lang.IllegalArgumentException: requirement failed: 控制字自检失败: 0x43
      //     at WorkerBeeSimCaseMxSpec.$anonfun$runMxGemm$38(WorkerBeeSimCaseMxSpec.scala:552)
      //   （build/155_mxfp4_fp32anchor_try1.log）。这不是锚漂移，是 TB 自检字面量陈旧。
      //   修复方式：期望值改为由 DataFormat 枚举【反解码推导】，不再写死任何进制字面量 ⟹
      //   今后若再有人重编号枚举而忘了改上面的 0x43L，本自检会再次长牙（比原字面量版更强）。
      //   ★这不是放宽判据：锚的逐位比对（anchorMismatch == 0）、锚存在性、锚 token 数三条
      //   断言一字未动，本 require 与锚无关，只校验控制字位域打包。
      //   旁证 0x43 才是正确编码：现役 FP16 用例走的正是 0x43 | 1<<8 = 0x143，其 1024/1024
      //   逐拍 DMA-C 数据硬断言与 golden 容差交叉全绿 ⟹ 硬件把 bits(2,0)=3 解成 MXE4M3、
      //   bits(6,4)=4 解成 MXE5M2，与本文件 floatToMxE4M3/floatToMxE5M2 的激励一致。
      // 【#144】MXBlockSize 收窄后新枚举 {M16=0, M32=1}：a/bMxBlk 由旧 sel=2(旧枚举=32) 改为
      //   sel=1(新枚举 M32=32)，保持「MX 输入 scale 块 = 32」的原意不变（金锚 kBlocks=K/32、
      //   kb=k/32、lane 打包 kt*32 全部按 block=32 推导）。旧 sel=2 在新枚举下越界，硬件
      //   getBlockSize(2) 落 otherwise→16，会使 scaleA/scaleB 行步进 K/16 与金锚 K/32 失配。
      // (#164) 合并后的单一控制字 reg5：位域由 WbInterface.wregs 的 is(5.U) 分支逐位推导，
      //   与金锚 WbDebugModel.controlWord5 同形（warmup[21]/warmdown[20] 本轮不置位，恒 0）。
      //   三个 MxBlk 的 `& 0x3` 掩码沿用旧写法（域实际宽 1 bit）：sel∈{0,1} 下与 `& 0x1` 逐位
      //   相同 ⟹ 零行为变化；属既存遗留，本轮不改（已在报告登记）。
      val controlWord5 =
        (1L << 28) |                                 // loadWMode   reg5[28]
        ((blkSel.toLong & 0x3) << 26) |              // cMxBlk      reg5[26]
        ((1L & 0x3) << 24) |                         // bMxBlk      reg5[24] = sel1 = M32
        ((1L & 0x3) << 22) |                         // aMxBlk      reg5[22] = sel1 = M32
        ((rnd.toLong & 0x7) << 16) |                 // rnd         reg5[18:16]（旧 <<12）
        ((outFmtInt.toLong & 0xF) << 8) |            // cFmt        reg5[11:8]（★掩码 0x7→0xF）
        0x43L                                        // bFmt=MXE5M2(4)<<4 | aFmt=MXE4M3(3)
      if (outFmt == DataFormat.FP32 && blkSel == 0 && rnd == 0) {
        // (#155 MJ3) 期望基础位 = 枚举反解码（MXE4M3 于 reg5[3:0]、MXE5M2 于 reg5[7:4]），
        //   取代陈旧字面量 0x65L（#97 前编号）；fp32 直出 + blkSel=0 + rnd=RNE 时
        //   outFmt/rnd/blkSel 三个位域均为 0 ⟹ 期望值 = 基础位 | 固定的 loadWMode 与
        //   a/bMxBlk 三个结构位（三者不随用例参数变，属本 runner 的硬编码契约）。
        //   (#164) 旧写法只校 controlWord7（不含 loadWMode/MxBlk）；合并后本自检覆盖面
        //   【扩大】到 reg5 全字，断言强度不降反升，且期望值仍由枚举反解码而非 RTL 拟合。
        val expCtlBase = DataFormat.MXE4M3.litValue.toInt | (DataFormat.MXE5M2.litValue.toInt << 4)
        val expCtl5 = (1L << 28) | (1L << 24) | (1L << 22) | expCtlBase.toLong
        require(controlWord5 == expCtl5,
          s"reg5 control-word self-check failed: got 0x${controlWord5.toHexString} expected " +
          s"0x${expCtl5.toHexString} = loadWMode(1<<28) | bMxBlk(1<<24) | aMxBlk(1<<22) | " +
          s"MXE4M3(${DataFormat.MXE4M3.litValue.toInt}) | MXE5M2(${DataFormat.MXE5M2.litValue.toInt})<<4")
      }
      // (#164) 新写序列：reg0=m、reg1=n|k<<16（旧 reg2=k 已并入 reg1 高 16 位）、
      //   reg2/3/4 = a/b/cAddr（旧 reg3/4/5）、reg5 = 控制字（旧 reg6+reg7）。
      //   ★旧 reg6/reg7 那条 sendCmd 已删除：写它们在新布局下是 no-op，留着只会误导。
      sendCmd(WorkerBeeOp.REG_WRITE2, 0x00, 0x01, M, N.toLong | (K.toLong << 16))
      sendCmd(WorkerBeeOp.REG_WRITE2, 0x02, 0x03, A_BASE, B_BASE)
      sendCmd(WorkerBeeOp.REG_WRITE2, 0x04, 0x05, C_BASE, controlWord5)
      println(s"debug1 controlWord5=0x${controlWord5.toHexString}")
      sendCmd(WorkerBeeOp.REG_WRITE2, WbSimCommon.SCALE_A_REG_ADDR, WbSimCommon.SCALE_B_REG_ADDR, SCALE_A_BASE, SCALE_B_BASE)
      // 【任务95】量化运行补写 0x08 cScaleAddr（ScaleWr 写通道基址源，QuantSpec
      // 同范式）；非量化运行该基址不被使用，保持不写（【任务90 收敛】语义零变化）。
      if (isQuant) sendCmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.SCALE_C_REG_ADDR, 0x00, SCALE_C_BASE, 0L)

      prepareStart(dut.clock, dut.io.cmd(0), dut.io.resp)
      launchStart(dut.clock, dut.io.cmd(0))

      // 完成判定只读 IRQ_RAW.done；STATUS 仅报告 busy/inFlight/progress。
      var done = false
      var cycle = 0
      var pollSt = 0
      var pollCool = 0
      // 【任务95】量化输出（cvt 量化支路 + dmaCScale 双通道写回）放宽超时上限
      // （对齐 QuantSpec runMxQuantGemm：常规 16000 / 带背压 24000）；fp32 默认 8000 不变。
      val maxCycles = if (!isQuant) 8000 else if (cScaleBackpressure) 24000 else 16000

      while (!done && cycle < maxCycles) {
        // --- DMA-A (A) ---
        // 【P3 迁移】由旧「rsp.valid 恒高 + rsp.ready 计数」改为 req.valid 拍响应 +
        // 逐拍地址硬对账（与 WorkerBeeSimCase 主用例/新 RdOnly 架构握手同模式）：
        // 旧模式在新架构下与 req 节奏脱钩造成供数错位/死锁（8000 拍超时、C=0）。
        if (dmaABeatsSent < dmaATotalBeats) {
          if (dut.io.dmaA.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaA.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-A beat #$dmaABeatsSent addr mismatch: ") {
              cmdAddr should be(expAAddrSeq(dmaABeatsSent))
            }
            dut.io.dmaA.rsp.valid.poke(true.B)
            dut.io.dmaA.rsp.payload.data.poke(genDmaABeat(dmaABeatsSent).U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
            dmaABeatsSent += 1
          } else {
            dut.io.dmaA.rsp.valid.poke(false.B)
            dut.io.dmaA.rsp.payload.data.poke(0.U)
            dut.io.dmaA.rsp.payload.err.poke(false.B)
          }
        } else {
          dut.io.dmaA.rsp.valid.poke(false.B)
          dut.io.dmaA.rsp.payload.data.poke(0.U)
          dut.io.dmaA.rsp.payload.err.poke(false.B)
        }

        // --- DMA-C (C store)：逐拍地址硬对账 + 【#166】strb 逐字节 lane 级硬对账 +
        //     lane 偏移后的完整拍数据硬断言（主守护） ---
        dut.io.dmaC.req.ready.poke(true.B)
        if (dut.io.dmaC.req.valid.peek().litToBoolean) {
          val reqAddr = dut.io.dmaC.req.payload.addr.peek().litValue.toLong
          val writeData = dut.io.dmaC.req.payload.data.peek().litValue
          val writeStrb = dut.io.dmaC.req.payload.strb.peek().litValue
          if (dmaCStoreBeats < expCAddrSeq.size) {
            withClue(s"DMA-C store beat #$dmaCStoreBeats addr mismatch: ") {
              reqAddr should be(expCAddrSeq(dmaCStoreBeats))
            }
            val expData = expCDataBeat(dmaCStoreBeats)
            withClue(s"DMA-C store beat #$dmaCStoreBeats data mismatch " +
                     f"(hw=0x$writeData%X exp=0x$expData%X): ") {
              writeData should be(expData)
            }
            // 【#166】AXI byte lane 逐字节审计：① addr↔strb 双向对账（addr 推出期望
            //   lane 窗口，strb 必须逐位一致，不一致报错而不静默放行）；② 未选通 lane
            //   不做数据断言，但必须为 0；③ 选通命中的 lane 逐字节与金锚 expData 比对。
            val laneBeatInRow = (dmaCStoreBeats % (M * beatsPerRow)) % beatsPerRow
            val laneBad = WbSimCommon.auditCBeatLanes(
              dmaCStoreBeats, reqAddr, writeData, writeStrb, outFmt, totalN, cfg.cDmaExtW,
              laneBeatInRow, Some(expData))
            laneBad.take(8).foreach(v => println(s"[MX]   [#166 LANE] $v"))
            withClue(s"DMA-C store beat #$dmaCStoreBeats AXI byte-lane audit " +
                     f"(addr=0x$reqAddr%X strb=0x$writeStrb%X expStrb=" +
                     f"0x${WbSimCommon.cExpStrb(expCAddrSeq(dmaCStoreBeats), outFmt, totalN, cfg.cDmaExtW, laneBeatInRow)}%X): ") {
              laneBad should be(Seq.empty[String])
            }
          }
          cWriteLog += ((reqAddr, writeData, writeStrb))
          dmaCStoreBeats += 1
          dut.io.dmaC.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaC.rsp.valid.poke(false.B)
        }

        // --- DMA-CSCALE：【任务95】按 isQuant 分派（runMxQuantGemm 范式移植） ---
        // 量化：逐拍地址/数据硬对账（线性模型 base+nt×colOff+m×rowOff）+ 写回日志；
        //   可选背压（W-Adam2：每 3 拍关 1 拍 req.ready，仅 fire 拍计数/校验——
        //   背压拍硬件重试同一地址，按 valid 计数会超前错位）。
        // 非量化：死路 → 全程零请求负向计数（done 后断言守护，【任务90 收敛】不变）。
        val cScaleReady = !cScaleBackpressure || (cycle % 3 != 0)
        dut.io.dmaCScale.get.req.ready.poke(cScaleReady.B)
        if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean && cScaleReady) {
          if (isQuant) {
            val reqAddr = dut.io.dmaCScale.get.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-CSCALE beat #$dmaCScaleReqs addr mismatch: ") {
              reqAddr should be(expCScaleAddrSeq(dmaCScaleReqs))
            }
            val writeData = dut.io.dmaCScale.get.req.payload.data.peek().litValue
            val expData = expCScaleDataBeat(dmaCScaleReqs)
            withClue(f"DMA-CSCALE beat #$dmaCScaleReqs data mismatch (hw=0x$writeData%X exp=0x$expData%X): ") {
              writeData should be(expData)
            }
            scaleWriteLog += ((reqAddr, writeData))
          }
          dmaCScaleReqs += 1
          dut.io.dmaCScale.get.rsp.valid.poke(true.B)
        } else {
          dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        }

        // --- DMA-B (B 权重) 逐 beat 地址硬对账 ---
        if (dmaBBeatsSent < dmaBTotalBeats) {
          if (dut.io.dmaB.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaB.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-B weight beat #$dmaBBeatsSent addr mismatch: ") {
              cmdAddr should be(expBAddrSeq(dmaBBeatsSent))
            }
            dut.io.dmaB.rsp.valid.poke(true.B)
            dut.io.dmaB.rsp.payload.data.poke(genDmaBWeightBeat(dmaBBeatsSent).U)
            dut.io.dmaB.rsp.payload.err.poke(false.B)
            dmaBBeatsSent += 1
          } else {
            dut.io.dmaB.rsp.valid.poke(false.B)
            dut.io.dmaB.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dmaB.rsp.valid.poke(false.B)
          dut.io.dmaB.rsp.payload.data.poke(0.U)
        }

        // --- DMA-scaleA (scaleA) 8-bit：req.valid 拍响应 + 逐拍地址硬对账（同 scaleB 风格） ---
        // 【修复（taskId=86）】由按 beat 序供数（无地址对账）改为按地址响应 + 逐拍硬对账；
        // 供数值不变（同序解码 m/kb 等价）。边界断言防地址足迹越出真实张量。
        if (scaleABeatsSent < scaleATotalBeats) {
          if (dut.io.dmaScaleA.get.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-scaleA beat #$scaleABeatsSent addr mismatch: ") {
              cmdAddr should be(expScaleAPhysSeq(scaleABeatsSent))
            }
            // 【P0-1 gather】物理读是 4B 对齐字。合法窗口由契约推导：逻辑负载 [L, L+4)
            // （L ∈ 张量）⟹ lo 最小 = BASE&~3；hi 最大 = 张量末字节所在对齐字 + 1 字
            // （末拍 hi 字可合法越尾 ≤3 字节，越界字节供 0，与旧 TB 语义一致）。
            withClue(s"DMA-scaleA beat #$scaleABeatsSent addr out of gather window: ") {
              (cmdAddr % 4 == 0 && cmdAddr >= (SCALE_A_BASE & ~3L) &&
                cmdAddr <= (((SCALE_A_BASE + scaleATensorBytes - 1) & ~3L) + 4)) should be(true)
            }
            dut.io.dmaScaleA.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(genScaleAWordByAddr(cmdAddr).U)
            dut.io.dmaScaleA.get.rsp.payload.err.poke(false.B)
            scaleABeatsSent += 1
          } else {
            dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleA.get.rsp.payload.data.poke(0.U)
        }

        // --- DMA-scaleB (scaleB) 32-bit：req.valid 拍逐拍地址硬对账 + 按地址内存保真供
        // 【P0-1 gather】完整 4 字节对齐字。rsp.valid 仅在 req.valid 拍拉高；计数按 req 拍
        // 推进（该 DMA rsp.ready 恒高，按 rsp.ready 计数会把启动空拍误计进来）；轮间跳转拍
        // req.valid 拉低则不给 rsp。物理序列 = 逻辑序列按 4B 对齐契约 gather 展开。
        if (scaleBBeatsSent < scaleBTotalBeats) {
          if (dut.io.dmaScaleB.get.req.valid.peek().litToBoolean) {
            val cmdAddr = dut.io.dmaScaleB.get.req.payload.addr.peek().litValue.toLong
            withClue(s"DMA-scaleB beat #$scaleBBeatsSent addr mismatch: ") {
              cmdAddr should be(expScaleBPhysSeq(scaleBBeatsSent))
            }
            val scaleWord = genScaleBWordByAddr(cmdAddr)
            // poke 前位宽自检：dmaScaleB 端口为 32-bit（scaleBW），供数超宽会让 svsim
            // Verilator harness 直接 exit(EXIT_FAILURE) 且不刷盘 FST（波形静默消失）；
            // 提前用可读断言拦截，今后任何位宽不匹配都能看到波形与清晰错误。
            withClue(s"DMA-scaleB beat #$scaleBBeatsSent data exceeds 32-bit port width: ") {
              (scaleWord >= BigInt(0) && scaleWord <= BigInt(0xFFFFFFFFL)) should be(true)
            }
            scaleBAddrLog += cmdAddr
            dut.io.dmaScaleB.get.rsp.valid.poke(true.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(scaleWord.U)
            dut.io.dmaScaleB.get.rsp.payload.err.poke(false.B)
            scaleBBeatsSent += 1
          } else {
            dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
            dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
          }
        } else {
          dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
          dut.io.dmaScaleB.get.rsp.payload.data.poke(0.U)
        }

        val (stNext, coolNext, irqOpt) = irqPollStep(dut.io.cmd(0), dut.io.resp, pollSt, pollCool)
        pollSt = stNext
        pollCool = coolNext
        irqOpt.foreach { st =>
          withClue(f"[MX] IRQ_RAW=0x${st.raw}%X: ") { st.anyError should be(false) }
          if (st.done) done = true
        }

        dut.clock.step()
        cycle += 1
      }

      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaCScale.get.rsp.valid.poke(false.B)
      dut.io.cmd.foreach(_.valid.poke(false.B))   // 轮询命令用后拉低（防残留影响后续拍）

      if (!done) println(s"[MX] WARNING: timeout at $maxCycles cycles (A=$dmaABeatsSent B=$dmaBBeatsSent C=$dmaCStoreBeats sA=$scaleABeatsSent sB=$scaleBBeatsSent)")

      done should be(true)
      dut.io.status.inFlight.expect(false.B)
      // 拍数硬断言（六通道）
      dmaABeatsSent should be(dmaATotalBeats)
      dmaBBeatsSent should be(dmaBTotalBeats)
      dmaCStoreBeats should be(expCAddrSeq.size)
      dmaCStoreBeats should be(nTilesC * M * beatsPerRow)
      scaleABeatsSent should be(scaleATotalBeats)
      scaleBBeatsSent should be(scaleBTotalBeats)
      // 【任务95】dmaCScale 断言按 isQuant 分派：量化 = 写拍数硬对账（nTilesC×M，
      // 每 (nt,m) 行一拍）；非量化 = 死路全程零请求（【任务90 收敛】负向守护不变）。
      if (isQuant) {
        withClue("[MX] dmaCScale write beat count (quant run): ") {
          dmaCScaleReqs should be(expCScaleAddrSeq.size)
        }
        withClue("[MX] dmaCScale write beats should be nTiles*M (quant run): ") {
          dmaCScaleReqs should be(nTilesC * M)
        }
      } else {
        withClue("[MX] dmaCScale must stay silent (zero req) under non-quant direct output (fp32/fp16/bf16): ") {
          dmaCScaleReqs should be(0)
        }
      }

      // ===== 拍数断言（归档）：【任务90 收敛】fp32 直出 2 拍/行，窗口按新拍数几何归档 =====
      expCycleWindow.foreach { case (lo, hi) =>
        withClue(s"[MX] cycles=$cycle 超出预期窗口 [$lo,$hi]: ") {
          (cycle >= lo && cycle <= hi) should be(true)
        }
      }

      // 【任务109】scaleB 取址序硬对账已上移到主循环 req.valid 拍逐 beat 内联执行
      // （见上方 DMA-scaleB 供数块 withClue "addr mismatch"）：旧 128-bit 列驻留序列
      // addr(idx)=base+(idx%kBlocks)*N+(idx/kBlocks)*totalN 已作废，改为镜像
      // WorkerBeeDmaRdOnly(isA=false,loadWMode=1) 的 8-bit 降序遍历 expScaleBAddrSeq。
      // 此处仅保留「全部 beat 均已被逐拍对账」的规模自检。
      scaleBAddrLog.size should be(scaleBTotalBeats)

      // ===== 【任务90 收敛】v1 锚逐位守护：密集拍解析 → 硬件 fp32 bits == 锚 bits =====
      // fp32 直出为合法受守护路径（P0 契约后保留）：锚录于 v1 的 REG_CONTROL 布局
      //   （锚文件头原文 regs7=0x5E0、含已删除的 mixFmtEn 位），当前布局的基础位是
      //   0x43 = MXE4M3(3) | MXE5M2(4)<<4（#97 重编号后），(#164) loadWMode 在 reg5[28]。
      //   (#155 MJ3) 订正：原文写「锚录于 regs7=0x65」，0x65 既不是锚录制值（0x5E0）
      //   也不是当前值（0x43），是 #97 前编号的陈旧中间态。锚守护只比 C 数据位，
      //   寄存器布局差异不是比对输入 ⟹ 该订正不改变任何断言语义。
      // 本套件与锚录制条件（M/N/K、基址、E4M3×E5M2 激励、确定性 scale）逐字一致，
      //   仅换解析器（parseDenseCWriteLog）与密集地址序列。
      // 【任务95】解析位宽随 outFmt 参数化（量化行返回逐元素码位模式；量化分支
      // 不消费 hwBits，交叉校验直接读 cWriteLog/scaleWriteLog）。
      // 【#166】解析改走 Checked 变体：除了矩阵还原，额外拿到全拍 lane 级审计汇总
      //   （addr↔strb 双向对账 + 未选通 lane 零填充 + BEAT-COUNT）并硬断言零违规。
      //   期望拍数取金锚 expCAddrSeq.size（张量语义侧），不取硬件实收拍数。
      val parsedC = WbSimCommon.parseDenseCWriteLogChecked(
        cWriteLog.toSeq, outFmt, M, N, totalN, cfg.cDmaExtW,
        expBeats = Some(expCAddrSeq.size), expElems = Some(M * N))
      val hwBits = parsedC.matrix
      println(s"[MX] (#166 LANE) ${parsedC.laneSummary}")
      parsedC.violations.take(16).foreach(v => println(s"[MX]   [#166 LANE] $v"))
      withClue("[MX] (#166) AXI byte-lane audit over all dmaC store beats " +
               "(addr<->strb cross-check + unselected-lane zero-fill + beat count): ") {
        parsedC.violations should be(Seq.empty[String])
      }

      // ===== 容差交叉（【任务95】按 isQuant 分派） =====
      if (isQuant) {
        // 量化：写回码按块 scale 反量化后 vs 双精度 golden（runMxQuantGemm 范式移植）；
        // 冲刷界豁免（|ref| < 2^(scale-127) 冲刷 ±0 不判失），判失条件
        // relErr>0.5 && absErr>1e-3 && absErr>flushBound+1e-9（防期望链自身漂移）。
        val (expW, mantW, bias) = outFmt match {
          case DataFormat.MXE4M3 => (4, 3, 7)    // E4M3
          case DataFormat.MXE5M2 => (5, 2, 15)  // E5M2
          case _               => (2, 1, 1)   // mxfp4 E2M1
        }
        val isFp4 = outFmt == DataFormat.MXE2M1
        val elemBitsX = if (isFp4) 4 else 8
        def dequant(code: Int, scale: Int): Double = {
          if (isFp4) {
            // mxfp4：4-bit 码 = sg(1)+e(2)+m(1)，低列号低 nibble（elemsToDenseRow 同序）
            val sg = (code >> 3) & 1
            val e = (code >> 1) & 0x3
            val mm = code & 1
            if (e == 0 || scale == 0) 0.0
            else {
              val v = (1.0 + mm.toDouble / 2) * math.pow(2, e - 1) * math.pow(2, scale - 127)
              if (sg == 1) -v else v
            }
          } else {
            val sg = (code >> (expW + mantW)) & 1
            val mag = code & ((1 << (expW + mantW)) - 1)
            val e = mag >> mantW
            val mm = mag & ((1 << mantW) - 1)
            if (e == 0 || scale == 0) 0.0
            else {
              val v = (1.0 + mm.toDouble / (1 << mantW)) * math.pow(2, e - bias) * math.pow(2, scale - 127)
              if (sg == 1) -v else v
            }
          }
        }
        var mismatchCnt = 0
        var maxRelErr = 0.0
        var compared = 0   // (#170-W11) count of elements actually compared
        var beatIdx = 0
        val elemsPerBeat = cfg.cDmaExtW / elemBitsX
        for ((_hwAddr, data, strb) <- cWriteLog) {
          val nt = beatIdx / (M * beatsPerRow)
          val inBlock = beatIdx % (M * beatsPerRow)
          val row = inBlock / beatsPerRow
          val beatInRow = inBlock % beatsPerRow
          val scaleBeat = scaleWriteLog(nt * M + row)._2
          // (#170-W11) Use golden expCAddrSeq(beatIdx) instead of hardware-received addr
          //   for lane offset derivation. This eliminates the scenario where the new strb
          //   gating could cause the branch to silently pass with zero comparisons.
          val laneOff = WbSimCommon.cLaneOffset(expCAddrSeq(beatIdx), cfg.cDmaExtW)
          val aligned = data >> (laneOff * 8)
          for (i <- 0 until elemsPerBeat) {
            val globalCol = nt * totalN + beatInRow * elemsPerBeat + i
            // 密集布局列归属边界（任务90 收敛）：拍内超出 tile 宽（totalN）的
            // 高半区是零填充，不得计入列空间。
            if (globalCol < N && globalCol < (nt + 1) * totalN &&
                strb.testBit(laneOff + (elemBitsX * i) / 8)) {
              compared += 1
              val code = ((aligned >> (elemBitsX * i)) & ((1 << elemBitsX) - 1)).toInt
              val localCol = globalCol - nt * totalN
              val grp = localCol / 8   // scale 字节按 tile 内局部列分组（低块号低位）
              val scale = ((scaleBeat >> (8 * grp)) & 0xFF).toInt
              val hw = dequant(code, scale)
              val ref = refSum(row, globalCol)
              val absErr = math.abs(hw - ref)
              val relErr = if (math.abs(ref) > 1e-6) absErr / math.abs(ref) else absErr
              val flushBound = math.pow(2, scale - 127)
              if (relErr > 0.5 && absErr > 1e-3 && absErr > flushBound + 1e-9) {
                if (mismatchCnt < 5)
                  println(f"[MX] DEQUANT MISMATCH C($row,$globalCol): hw=$hw%.6g ref=$ref%.6g code=0x$code%X scale=0x$scale%02X")
                mismatchCnt += 1
              }
              if (relErr > maxRelErr) maxRelErr = relErr
            }
          }
          beatIdx += 1
        }
        println(f"[MX] quant outFmt=$outFmtInt dequant cross-check: compared=$compared/${M * N}, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
        withClue("[MX] (#170-W11) dequant cross-check must compare at least one element " +
                 "(guards against strb-gating making this branch vacuously pass): ") {
          compared should be > (0)
        }
        mismatchCnt should be(0)
      } else if (isFp16Out) {
        // ===== 【任务98】fp16/bf16 解码容差交叉：硬件 16-bit 值 vs 双精度 golden =====
        // 唯一偏差源 = 16-bit 降精度舍入（DMA-C 逐拍硬断言已逐位钉死期望链，本交叉
        // 为第二视角）：fp16 有 10-bit 尾数（ulp ~1e-3，1e-2 容差安全）；bf16 仅
        // 7-bit 尾数（ulp ~2^-8 ≈ 4e-3，叠加 fp32 累加舍入取 2e-2 更稳妥）。
        // 与既有 fp32 golden 容差风格一致：relErr > tol && absErr > 1e-6 才判失
        // （近零元素按 absErr 门槛豁免）；fp16ToFloat 含 subnormal/inf/nan 处理，
        // bf16 尾数低位补零重建 fp32 bits 后经 fp32BitsToFloat 解码（同源语义）。
        def decode16(v: Long): Double =
          if (outFmt == DataFormat.FP16) WbSimCommon.fp16ToFloat((v & 0xFFFFL).toInt).toDouble
          else WbSimCommon.fp32BitsToFloat((v & 0xFFFFL) << 16).toDouble   // bf16 → fp32 尾数低位补零
        val tol = if (outFmt == DataFormat.FP16) 1e-2 else 2e-2
        val hwVal = Array.tabulate(M, N) { (m, n) => decode16(hwBits(m)(n)) }
        val ref = Array.tabulate(M, N) { (m, n) => refSum(m, n) }
        var mismatchCnt = 0
        var maxRelErr = 0.0
        for (m <- 0 until M; n <- 0 until N) {
          val r = ref(m)(n)
          val hw = hwVal(m)(n)
          val absErr = math.abs(hw - r)
          val relErr = if (math.abs(r) > 1e-6) absErr / math.abs(r) else absErr
          if (relErr > tol && absErr > 1e-6) {
            if (mismatchCnt < 64)
              println(f"[MX] MISMATCH C($m,$n): hw=$hw%.6g ref=$r%.6g relErr=$relErr%.3f")
            mismatchCnt += 1
          }
          if (relErr > maxRelErr) maxRelErr = relErr
        }
        println(f"[MX] outFmt=$outFmtInt 16-bit cross-check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e")
        for (m <- 0 until math.min(M, 4)) {
          val hwStr = (0 until N).map(n => f"${hwVal(m)(n)}%.3f").mkString(" ")
          val refStr = (0 until N).map(n => f"${ref(m)(n)}%.3f").mkString(" ")
          println(s"[MX] hw  row$m: $hwStr")
          println(s"[MX] ref row$m: $refStr")
        }

        mismatchCnt should be(0)
      } else {
        // ===== PRIMARY GUARD (#175): fp32 output vs double-precision golden =====
        // Derivation: refSum(m,n) = sum_k mxToFloat(a,sA)*mxToFloat(b,sB) in IEEE-754
        // double (53-bit mantissa), fully independent of RTL. Hardware deviation source:
        // fp32 accumulation rounding (24-bit mantissa, K=32 terms), bounded well below
        // 1e-2 relative error. Covers ALL M*N elements. Tolerance = 1e-2 relative.
        val fp32GoldenTol = 1e-2
        val hwVal = Array.tabulate(M, N) { (m, n) => WbSimCommon.fp32BitsToFloat(hwBits(m)(n)).toDouble }
        val ref = Array.tabulate(M, N) { (m, n) => refSum(m, n) }
        var mismatchCnt = 0
        var maxRelErr = 0.0
        for (m <- 0 until M; n <- 0 until N) {
          val r = ref(m)(n)
          val hw = hwVal(m)(n)
          val absErr = math.abs(hw - r)
          val relErr = if (math.abs(r) > 1e-6) absErr / math.abs(r) else absErr
          if (relErr > fp32GoldenTol && absErr > 1e-6) {
            if (mismatchCnt < 64)
              println(f"[MX-FP32-PRIMARY] MISMATCH C($m,$n): hw=$hw%.6g ref=$r%.6g relErr=$relErr%.3f")
            mismatchCnt += 1
          }
          if (relErr > maxRelErr) maxRelErr = relErr
        }
        println(f"[MX-FP32-PRIMARY] golden cross-check: ${M * N} elems, mismatches=$mismatchCnt, maxRelErr=$maxRelErr%.2e, tol=$fp32GoldenTol%.0e")
        for (m <- 0 until math.min(M, 4)) {
          val hwStr = (0 until N).map(n => f"${hwVal(m)(n)}%.3f").mkString(" ")
          val refStr = (0 until N).map(n => f"${ref(m)(n)}%.3f").mkString(" ")
          println(s"[MX-FP32-PRIMARY] hw  row$m: $hwStr")
          println(s"[MX-FP32-PRIMARY] ref row$m: $refStr")
        }

        withClue(f"[MX-FP32-PRIMARY] double-precision golden cross-check (tol=$fp32GoldenTol%.0e rel, " +
                 f"${M * N} elems, maxRelErr=$maxRelErr%.2e): ") {
          mismatchCnt should be(0)
        }
      }
    }
    } finally {
      // FST 波形须在 simulate 块退出（verilator flush/close trace.fst）后再复制；
      // 若在块内复制，源文件仍在写入、只能拿到 0 字节空壳（对齐非 MX SimCase 的
      // 块外复制约定，源路径段由 testMangle 逐字锚定 it should 用例名）。
      // 【任务94】置于 finally：断言失败时异常上抛后同样在此转存（Verilator 已对
      // 失败拍之前的周期持续写出 trace.fst，文件非空即可拷出供波形器回放）。
      WbSimCommon.copyTrace(
        s"build/chiselsim/WorkerBeeSimCaseMxSpec/WorkerBee-MXFP8-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        "MX"
      )
    }
  }

  // 配置错误只置 IRQ_RAW[1]；合法 START 保留旧 pending，须显式 W1C 清除。
  // (#164) 形参 `regs7` 改名 `ctl5`：旧语义是「只传格式字（写 reg7），reg6 硬编码 0」；
  //   新语义是「传【完整】的 reg5 控制字」（loadWMode/MxBlk/rnd/addrMode/fmt 全在一个字里）。
  //   本 def 目前【无调用点】（历史用例已删），故改名与语义变更无下游影响；按任务书
  //   【不删函数、保持结构】，只把它与新排布对齐，以免日后重新接入时踩 no-op 陷阱。
  private def runMxErrCase(M: Int, N: Int, K: Int, ctl5: Long, caseName: String): Unit = {
    // v2：hasMx 要求 aW/bW>=16（SIMD 双元素/拍）；err 在 sIDLE 拍触发，与位宽无关。
    // (#155 MJ1) supportedFmts 追加 MXE2M1：WorkerBeeConfig 新增 require(!hasMx8 || hasMx4)
    //   （用户设计律「有 mxfp8 就有 mxfp4，是必选项」）⟹ 原先这个 fp8-only 集合会在
    //   【cfg 构造这一行】抛 IllegalArgumentException。追加后本 cfg 与 WorkerBeeElaborate.cfgMx
    //   / cfgMxFp4 全 12 字段相同。对本负向运行器的断言零影响：断言只看 STATUS.err 位、
    //   done 位与 dmaC/dmaCScale 零写请求拍，不绑定具体 errCode；且 aFmt/bFmt 仍是 fp8
    //   （E4M3×E5M2）⟹ isMx4 两臂均假 ⟹ fp4 专属守护（mix errCode3 / K 整除 errCode8）
    //   不抢跑，elemsPerLane 对 fp8 仍为 2 ⟹ 几何与拍数不变。
    //   注：本 def 目前【无调用点】（历史用例已删），仍一并对齐以免留下 fp8-only 陷阱。
    val cfg = WorkerBeeConfig(
      arrayN = 8, clusterM = 2,
      aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
      aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    simulate(new WorkerBee(cfg)) { dut =>
      // ---- Reset + DMA 端口拉到安全默认（本用例不喂数据，err 应在 execute 拍触发） ----
      dut.reset.poke(true.B)
      dut.io.cmd.foreach(_.valid.poke(false.B))
      dut.io.resp.ready.poke(true.B)
      dut.io.dmaA.req.ready.poke(true.B)
      dut.io.dmaA.rsp.valid.poke(false.B)
      dut.io.dmaC.req.ready.poke(true.B)
      dut.io.dmaC.rsp.valid.poke(false.B)
      dut.io.dmaB.req.ready.poke(true.B)
      dut.io.dmaB.rsp.valid.poke(false.B)
      dut.io.dmaScaleA.get.req.ready.poke(true.B)
      dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
      dut.io.dmaScaleB.get.req.ready.poke(true.B)
      dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: WorkerBeeOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
        transactReg(dut.clock, dut.io.cmd(0), dut.io.resp, op, reg0Addr, reg1Addr, BigInt(data0), BigInt(data1))
      }

      // (#164) 新写序列：reg0=m、reg1=n|k<<16、reg2/3/4=a/b/cAddr、reg5=控制字（旧 reg6+reg7）。
      //   ★旧 `sendCmd(REG_WRITE2, 0x06, 0x07, 0L, regs7)` 已删：reg6/reg7 在新布局下写为 no-op。
      sendCmd(WorkerBeeOp.REG_WRITE2, 0x00, 0x01, M, N.toLong | (K.toLong << 16))
      sendCmd(WorkerBeeOp.REG_WRITE2, 0x02, 0x03, A_BASE, B_BASE)
      sendCmd(WorkerBeeOp.REG_WRITE2, 0x04, 0x05, C_BASE, ctl5)
      sendCmd(WorkerBeeOp.REG_WRITE2, WbSimCommon.SCALE_A_REG_ADDR, WbSimCommon.SCALE_B_REG_ADDR, SCALE_A_BASE, SCALE_B_BASE)

      prepareStart(dut.clock, dut.io.cmd(0), dut.io.resp)
      launchStart(dut.clock, dut.io.cmd(0))
      dut.clock.step(200)
      val raw = readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.IRQ_RAW_REG_ADDR)
      withClue(s"[$caseName] config error only in IRQ_RAW: ") { raw should be(BigInt(2)) }
      dut.io.status.inFlight.expect(false.B)

      val legalCtl5 = (1L << 28) |
        DataFormat.MXE4M3.litValue.toLong | (DataFormat.MXE5M2.litValue.toLong << 4)
      sendCmd(WorkerBeeOp.REG_WRITE1, 0x05, 0x00, legalCtl5, 0L)
      // START 不清 pending；合法重启后旧 configError 仍记录，须显式 W1C。
      launchStart(dut.clock, dut.io.cmd(0))
      dut.clock.step(100)
      readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.IRQ_RAW_REG_ADDR) should be(raw)
      sendCmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.IRQ_CLEAR_REG_ADDR, 0, 2L, 0L)
      readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.IRQ_RAW_REG_ADDR) should be(BigInt(0))
      readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.ERROR_CODE_REG_ADDR) should be(BigInt(0))
      dut.io.status.inFlight.expect(true.B)
    }
  }

  // MX 整数输出违约应仅记录配置错误码 11，且不产生 C/ScaleC 写请求。
  private def runMxOutFmtErrCase(M: Int, N: Int, K: Int, outFmt: DataFormat.Type,
                                 waveName: String, testMangle: String): Unit = {
    // 【任务97】outFmt 类型化为 DataFormat.Type：caseName/控制字位域消费整型编码，
    // 经 outFmtInt（litValue.toInt）收敛，保持原 Int 语义（caseName 数字、位域打包）。
    val outFmtInt = outFmt.litValue.toInt
    val caseName = s"MX-outFmt${outFmtInt}-family-cross"
    // 控制字：MX 基础 0x43（E4M3×E5M2，#97 重编号后 reg5 布局）+ 违约 outFmt 位域
    // （<<8）；blkSel/rnd 位不写（K=32 对齐、【#144】缺省 sel=0 → 新枚举 block=16
    // ≤ totalN 合法，唯一违约项 = outFmt 族交叉，err 归因隔离）；
    // (#164) loadWMode=1 与格式字现同居 reg5，故 1<<28 直接并进本控制字（旧写法是由
    //   sendCmd(REG_WRITE2, 0x06, 0x07, 1L<<28, controlWord) 分两个寄存器承载）。
    // ★(#164 裁定 4) outFmt 掩码 `& 0x7` → `& 0xF`：DataFormat 是 4-bit 域，3-bit 掩码会把
    //   INT8(8)→0=FP32、INT4(9)→1=FP16 静默截断；而 FP32/FP16 恰好属于 MX 输入的【合法】
    //   输出族 {0,1,2,3,4,5} ⟹ 本负向 runner 会被静默变成正向（硬件根本不置 err，
    //   下方 `errSeen should be(true)` 直接打红）。旧注释「8/9/10 截为 0/1/2——是否仍属违约族
    //   由 Executor 新校验决定，留待用户调试」的待调项，本次按裁定定为【不截断】。
    // ★(#178) 基础字面量 0x65L → 0x43L：0x65 在 #97 重编号下解为 aFmt=5(MXE2M1)、
    //   bFmt=6(INT32)，是 MX×INT 混配（触发 errCode 3 而非 11）。现改为 0x43 =
    //   aFmt=3(MXE4M3) | bFmt=4(MXE5M2)<<4，与 runMxGemm 激励同源，确保唯一违约项
    //   = outFmt 族交叉（errCode 11），不抢跑 errCode 3/4/5/8。
    //   独立核算：reg5[3:0]=MXE4M3=3, reg5[7:4]=MXE5M2=4 → (4<<4)|3 = 0x43 ✓
    val controlWord5 =
      (1L << 28) | 0x43L | ((outFmtInt.toLong & 0xF) << 8)
    // (#155 MJ1) supportedFmts 追加 MXE2M1：WorkerBeeConfig 新增 require(!hasMx8 || hasMx4)
    //   ⟹ 原先这个 fp8-only 集合会在【cfg 构造这一行】抛 IllegalArgumentException。
    //   本 def 是【活代码】：runMxGemm 在 outFmt 不属合法族 {0,1,2,3,4,5} 时分派到此处
    //   （现役用例 outFmt=FP16、#155 新增锚用例 outFmt=FP32，都在合法族内；
    //   #178 新增负向用例 outFmt=INT32 不在合法族内 ⟹ 本函数现已可达）。追加后与 cfgMx/cfgMxFp4 全 12 字段相同。
    //   (#178) 断言现绑定 errCode=11（叠加式变强，不放宽既有断言）；违约项仍是唯一的
    //   outFmt 族交叉（aFmt/bFmt = E4M3×E5M2 ⟹ isMx4 两臂均假 ⟹ fp4 专属守护不抢跑）。
    val cfg = WorkerBeeConfig(
      arrayN = 8, clusterM = 2,
      aW = 16, bW = 16, cW = 40,
      supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
      aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048,
      scaleBufferDepth = 256
    )

    try {
      simulate(new WorkerBee(cfg)) { dut =>
        // ---- Reset + DMA 端口拉到安全默认（不喂数据，err 应在 execute 拍触发） ----
        dut.reset.poke(true.B)
        dut.io.cmd.foreach(_.valid.poke(false.B))
        dut.io.resp.ready.poke(true.B)
        dut.io.dmaA.req.ready.poke(true.B)
        dut.io.dmaA.rsp.valid.poke(false.B)
        dut.io.dmaC.req.ready.poke(true.B)
        dut.io.dmaC.rsp.valid.poke(false.B)
        dut.io.dmaB.req.ready.poke(true.B)
        dut.io.dmaB.rsp.valid.poke(false.B)
        dut.io.dmaScaleA.get.req.ready.poke(true.B)
        dut.io.dmaScaleA.get.rsp.valid.poke(false.B)
        dut.io.dmaScaleB.get.req.ready.poke(true.B)
        dut.io.dmaScaleB.get.rsp.valid.poke(false.B)
        dut.io.dmaCScale.get.req.ready.poke(true.B)
        dut.io.dmaCScale.get.rsp.valid.poke(false.B)
        dut.clock.step(3)
        dut.reset.poke(false.B)
        dut.clock.step(2)

        def sendCmd(op: WorkerBeeOp.Type, reg0Addr: Int, reg1Addr: Int, data0: Long, data1: Long): Unit = {
          transactReg(dut.clock, dut.io.cmd(0), dut.io.resp, op, reg0Addr, reg1Addr, BigInt(data0), BigInt(data1))
        }

        // (#164) 新写序列：reg0=m、reg1=n|k<<16、reg2/3/4=a/b/cAddr、reg5=控制字。
        //   ★旧 `sendCmd(REG_WRITE2, 0x06, 0x07, 1L << 28, controlWord)` 已删：reg6/reg7 在新
        //   布局下写为 no-op ⟹ loadWMode 与格式字都写不进去，reg5 保持复位 0
        //   （= aFmt/bFmt/cFmt 全 FP32）⟹ errCode 5 抢跑、且 err 归因不再是 outFmt 族交叉。
        sendCmd(WorkerBeeOp.REG_WRITE2, 0x00, 0x01, M, N.toLong | (K.toLong << 16))
        sendCmd(WorkerBeeOp.REG_WRITE2, 0x02, 0x03, A_BASE, B_BASE)
        sendCmd(WorkerBeeOp.REG_WRITE2, 0x04, 0x05, C_BASE, controlWord5) // reg5[28]=loadWMode=1（保持 err 归因 = outFmt 族交叉）
        sendCmd(WorkerBeeOp.REG_WRITE2, WbSimCommon.SCALE_A_REG_ADDR, WbSimCommon.SCALE_B_REG_ADDR, SCALE_A_BASE, SCALE_B_BASE)

        prepareStart(dut.clock, dut.io.cmd(0), dut.io.resp)
        launchStart(dut.clock, dut.io.cmd(0))
        var pollSt = 0; var pollCool = 0
        var dmaCReqSeen = 0; var dmaCScaleReqSeen = 0
        var raw = BigInt(0)
        for (_ <- 0 until 200) {
          val (st, cool, irq) = irqPollStep(dut.io.cmd(0), dut.io.resp, pollSt, pollCool)
          pollSt = st; pollCool = cool
          irq.foreach(i => raw = i.raw)
          if (dut.io.dmaC.req.valid.peek().litToBoolean) dmaCReqSeen += 1
          if (dut.io.dmaCScale.get.req.valid.peek().litToBoolean) dmaCScaleReqSeen += 1
          dut.clock.step()
        }
        // 结束轮询时先排空可能在途的读响应，再做同步寄存器读取。
        dut.io.cmd.foreach(_.valid.poke(false.B))
        dut.clock.step(2)
        withClue(s"[$caseName] configError only, no done/DMA errors: ") { raw should be(BigInt(2)) }
        readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.IRQ_RAW_REG_ADDR) should be(BigInt(2))
        readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.ERROR_CODE_REG_ADDR) should be(BigInt(11))
        dut.io.status.inFlight.expect(false.B)
        dmaCReqSeen should be(0)
        dmaCScaleReqSeen should be(0)

        val legalCtl5 = (1L << 28) |
          DataFormat.MXE4M3.litValue.toLong | (DataFormat.MXE5M2.litValue.toLong << 4)
        sendCmd(WorkerBeeOp.REG_WRITE1, 0x05, 0x00, legalCtl5, 0L)
        launchStart(dut.clock, dut.io.cmd(0))
        dut.clock.step(100)
        // 合法 START 不清此前配置错误，也不自动清首次 code。
        readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.IRQ_RAW_REG_ADDR) should be(BigInt(2))
        readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.ERROR_CODE_REG_ADDR) should be(BigInt(11))
        sendCmd(WorkerBeeOp.REG_WRITE1, WbSimCommon.IRQ_CLEAR_REG_ADDR, 0, 2L, 0L)
        readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.IRQ_RAW_REG_ADDR) should be(BigInt(0))
        readRegister(dut.clock, dut.io.cmd(0), dut.io.resp, WbSimCommon.ERROR_CODE_REG_ADDR) should be(BigInt(0))
        dut.io.status.inFlight.expect(true.B)
      }
    } finally {
      WbSimCommon.copyTrace(
        s"build/chiselsim/WorkerBeeSimCaseMxSpec/WorkerBee-MXFP8-GEMM-Simulation/$testMangle/workdir-verilator/trace.fst",
        s"$simDir/$waveName",
        "MX"
      )
    }
  }
}
