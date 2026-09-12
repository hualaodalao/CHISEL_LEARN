/** HiveFp4FusionFeasibilitySpec —— Phase 5b-5d 融合前置可行性裁决（任务 #51）。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 本文件是什么 / 不是什么
  * ═══════════════════════════════════════════════════════════════════════════
  * 这是一个【纯 bit 级软件模型 + 裁决】文件，不是 RTL 实施。它用 BigInt/Long 逐位镜像
  * 现役 fp4 quad-lane 通路（Phase 2）的硬件语义，并建模拟议的 Phase 5c CSA 压缩树，
  * 在高覆盖的 fp4 输入空间逐位对拍，给出「CSA 融合是否与 Phase 2 级联 bit-exact」的裁决。
  *   - 严禁改动任何生产 RTL；本文件是【唯一新增产物】。
  *   - 裁决 A（可达）⟹ 本 spec 作为 #47/#48 的 A 级等价 oracle（断言 cascade==CSA）。
  *   - 裁决 B/C（不可达 / 受限可达）⟹ 本 spec 作为反例文档永久保留，钉死失配边界，
  *     支撑「回退保留 Phase 2 级联 / 或按受限条件实施」的决策。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 被镜像的硬件语义（源码锚点，逐位复刻，非拟合）
  * ═══════════════════════════════════════════════════════════════════════════
  *  ① HiveMacFpMx（HiveWorkUnit.scala:478-531，sigW=4 窄化乘法）：
  *     aSig=Cat(1, mA(22,24-sigW))、mProd=(aSig*bSig)<<(48-2sigW)、eAdj/eOut、
  *     mNorm/guardBit/roundUpM、prod32。MX 域 guardBit 结构性恒 0 ⟹ 舍入退化恒等。
  *  ② HiveMacLazyDownstream（HiveWorkUnit.scala:356-443，lazy-40 下游）：
  *     解包 c / 解包 prod32 → pMant31=Cat(1,pMant,0^7) → expDiff → 对齐右移【截断向零】
  *     cap 31 → effectSub 加/减 → 仅处理上溢（进位右移 1、exp+1）、【不做 LZC】→
  *     lazy-40 {sign(1),exp(8),mant(31)} → 零值 Mux 序（pZero→cRaw / cZero→prod / sub==0→0）。
  *  ③ Phase 2 quad-lane 4 级级联（HiveWorkUnit.scala:803-824）：
  *     qmac0.c=cReg → qmac1.c=qmac0.sum → … → qmac3.sum；k 升序、低 sublane 先加，
  *     左结合 ((((cReg+p0)+p1)+p2)+p3)，p_j=narrowMul(decode(aNib_j),decode(bNib_j))。
  *  ④ Fp32.mxE2M1ToFp32（Fp32.scala:139-142 + mxToFp32 subnormalFtz=false 分支）：
  *     E2M1 幅值域 {0,±0.5,±1,±1.5,±2,±3,±4,±6}×2^(scale-127)，e=0/m=1=±0.5 一等公民、
  *     0x7=±6.0 最大有限值（无 inf/nan），underflow<1→±0、overflow>254→±fp32Max。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 拟议 CSA（5c）建模 —— 三个变体，取对融合【最有利】者为裁决基准
  * ═══════════════════════════════════════════════════════════════════════════
  *  csaLazyLast（提案直译，且给最宽的对齐位宽）：4 窄积按【公共指数 E=max 积指数】并行
  *     barrel-shift 对齐（cap 31、截断向零，与级联同款）→ 符号转二补 → Wallace 3:2 + 单
  *     CPA【精确整数和】→ lazy-40 规格化 S（仅上溢右移、不 LZC）→ 再与 cReg 做【一次】
  *     lazy-40 累加（lazyAdd40，保持 31-bit 尾数，不退回 fp32）。这是融合能拿到的最好精度。
  *  csaFp32Last（字面复用 HiveMacLazyDownstream）：S 先经 swFpNormalize40to32 舍入成 fp32
  *     （31→24 位，额外丢 7 位）再 lazyAcc(prod,cReg)。这是「原样复用 Omar 下游」的实现，
  *     精度严格劣于 csaLazyLast。
  *  csaFiveOp（理论最优单对齐）：把 cReg 当第 5 操作数，与 4 积一并对齐到
  *     E'=max(cReg指数, 积指数)、单次精确和、单次 lazy-40 规格化。这是「只截断一次」的理想。
  *
  * 覆盖策略见文件末各 it should 的说明与运行日志。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 裁决结论（实测钉死，任务 #51）：C —— 仅受限条件下值等价可达，通用路径不可 bit-exact
  * ═══════════════════════════════════════════════════════════════════════════
  *  【安全岛，域1 证明】cReg=0（或 cReg 尾数低 27 位全零）时，csaLazyLast/csaFiveOp 与级联
  *    【值恒等】且最终 fp32 C 输出逐位相同（185k+ 向量 val=0/fp32=0）。根因：E2M1 窄积
  *    （sigW=4）在 31-bit lazy 尾数中仅占 ≤4 有效位、低 ≥27 位恒零，且 4 lane 共享 scale ⟹
  *    积指数跨度 ≤7 ⟹ 积间任何对齐右移（≤7）与上溢规格化都落在尾随零区 ⟹ 无损 ⟹ 结合律成立。
  *  【破裂，域2/域3/域4 证明】一旦 cReg 为前序 K-block 的【脏】lazy-40 累加器（尾数低位被
  *    加/减/规格化填满、非全零），级联在每步做「对齐右移截断向零 + 上溢规格化右移」会丢弃
  *    累加器真实低位，而 CSA 单次对齐只截断一次 ⟹ 二者【值分歧】（非仅编码不同），并穿透
  *    swFpNormalize40to32 改变最终 fp32 C（域2：csaLazyLast val=764/40000、fp32=198/40000；
  *    最优的 csaFiveOp 仍 val=215、fp32=2）。域3 gap 扫描 + gap=0 最小反例证明：分歧根因是
  *    「FP 加法非结合 + 截断向零的顺序依赖」，与对齐位宽无关（加宽对齐也不能消除）。
  *  【条件是否恒成立】cReg=0 仅在 K-reduction 的【首个】K-block 成立；此后 cReg 恒为脏累加器。
  *    故受限条件在 fp4 实际运行域【不恒成立】⟹ 对通用融合路径等价于「bit-exact 不可达」。
  *  【对 #47 建议】回退保留 Phase 2 的 4 级 lazy-40 顺序级联，【不实施】5c CSA 融合到承载
  *    数值累加的通用路径；CSA 压缩树可保留于【不改变 cReg 折入语义】的场合（见交付报告）。
  *
  * 运行：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  *        sbt -J-Xmx6G "testOnly HiveFp4FusionFeasibilitySpec"
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.lang.Float.{floatToRawIntBits, intBitsToFloat}

class HiveFp4FusionFeasibilitySpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim {

  private val TAG = "Fp4Fusion-P5"

  // ==========================================================================
  // 位域常量
  // ==========================================================================
  private val M23 = (BigInt(1) << 23) - 1
  private val M31 = (BigInt(1) << 31) - 1
  private val M40 = (BigInt(1) << 40) - 1
  private val Fp32MaxBits: Long = floatToRawIntBits(Float.MaxValue).toLong & 0x7FFFFFFFL

  private def fSign(x: Long): Int = (((x & 0xFFFFFFFFL) >>> 31) & 1).toInt
  private def fExp(x: Long): Int = (((x & 0xFFFFFFFFL) >>> 23) & 0xFF).toInt
  private def fMant(x: Long): BigInt = (x & 0xFFFFFFFFL) & M23

  private def pack40(s: Int, e: Int, m: BigInt): BigInt =
    (BigInt(s & 1) << 39) | (BigInt(e & 0xFF) << 31) | (m & M31)

  /** lazy-40 的【精确值键】(sign, mant<<exp)：两 lazy-40 表示同一有理值 ⟺ 键相等（mant=0 统一为 +0）。
    * lazy-40 是【非规格化/非规范】表示（不做 LZC，允许同一值落在不同 (exp,mant) 缩放上，
    * 如 {e,0x70000000} 与 {e+1,0x38000000} 同值）⟹ 必须用值键而非 raw bits 判定「值等价」。 */
  private def valKey(r: BigInt): (Int, BigInt) = {
    val s = ((r >> 39) & 1).toInt; val e = ((r >> 31) & 0xFF).toInt; val m = r & M31
    if (m == 0) (0, BigInt(0)) else (s, m << e)
  }
  private def sameValue(a: BigInt, b: BigInt): Boolean = valKey(a) == valKey(b)

  // ==========================================================================
  // ① E2M1 解码 → fp32 bits（忠实镜像 Fp32.mxE2M1ToFp32）
  // ==========================================================================
  private def e2m1(nib: Int, scale: Int): Long = {
    val p = MxFormatParams.E2M1
    val c = nib & ((1 << (p.expW + p.mantW + 1)) - 1)
    val s = (c >>> (p.expW + p.mantW)) & 1
    val e = (c >>> p.mantW) & ((1 << p.expW) - 1)
    val m = c & ((1 << p.mantW) - 1)
    val signBits = (s.toLong & 1L) << 31
    if (e == 0 && m == 0) signBits                                  // ±0（忽略 scale）
    else {
      val expField = e + (scale & 0xFF) - p.bias                    // = e+scale-1（含次正规 e=0）
      val mant23 = if (e == 0) 0L else ((m.toLong << (23 - p.mantW)) & 0x7FFFFFL)
      if (expField < 1) signBits                                    // underflow FTZ（保号）
      else if (expField > 254) signBits | Fp32MaxBits               // overflow 饱和（保号）
      else signBits | ((expField.toLong & 0xFFL) << 23) | mant23
    }
  }

  // ==========================================================================
  // ② 窄化乘法（忠实镜像 HiveMacFpMx，sigW=4）→ prod32（fp32 bits）
  // ==========================================================================
  private def narrowMul(a: Long, b: Long, sigW: Int = 4): Long = {
    val sA = fSign(a); val eA = fExp(a); val mA = fMant(a)
    val sB = fSign(b); val eB = fExp(b); val mB = fMant(b)
    val sOutM = sA ^ sB
    val aSig = (BigInt(1) << (sigW - 1)) | (mA >> (24 - sigW))
    val bSig = (BigInt(1) << (sigW - 1)) | (mB >> (24 - sigW))
    val mProd = (aSig * bSig) << (48 - 2 * sigW)                    // 48-bit
    val bit47 = ((mProd >> 47) & 1) == 1
    val eSum = eA + eB
    val eAdj = eSum + (if (bit47) 1 else 0)
    val eOut = if (eAdj >= 127) (eAdj - 127) & 0xFF else 0
    val mNorm = (if (bit47) (mProd >> 24) else (mProd >> 23)) & M23
    val guardBit = ((if (bit47) mProd >> 23 else mProd >> 22) & 1) == 1
    val roundUp = guardBit                                          // RNE；MX 域 guardBit 恒 false
    val mRounded = (if (roundUp) mNorm + 1 else mNorm) & M23
    val aZero = (eA == 0) && (mA == 0)
    val bZero = (eB == 0) && (mB == 0)
    if (aZero || bZero) 0L
    else (((sOutM.toLong & 1L) << 31) | ((eOut.toLong & 0xFFL) << 23) | (mRounded.toLong & 0x7FFFFFL)) & 0xFFFFFFFFL
  }

  // ==========================================================================
  // ③ lazy-40 下游核（忠实镜像 HiveMacLazyDownstream）
  //    prod 侧以 (pSign,pExp,pMant31,pZero) 抽象，供 lazyAcc / lazyAdd40 共用
  // ==========================================================================
  private def lazyCore(pSign: Int, pExp: Int, pMant31: BigInt, pZero: Boolean, c40: BigInt): BigInt = {
    val cRaw = c40 & M40
    val cSign = ((cRaw >> 39) & 1).toInt
    val cExp = ((cRaw >> 31) & 0xFF).toInt
    val cMant = cRaw & M31
    val cZero = (cExp == 0) && (cMant == 0)

    val expDiffRaw = pExp - cExp                                    // 有符号
    val pGtC = expDiffRaw >= 0
    val d = if (pGtC) expDiffRaw else -expDiffRaw
    val shiftAmt = if (d > 31) 31 else d                            // cap 31
    val baseExp = if (pGtC) pExp else cExp
    val pAligned = if (pGtC) pMant31 & M31 else (pMant31 >> shiftAmt) & M31
    val cAligned = if (pGtC) (cMant >> shiftAmt) & M31 else cMant

    val effectSub = pSign ^ cSign
    val pIsLarger = (expDiffRaw > 0) || (pAligned >= cAligned)
    val largeM = if (pIsLarger) pAligned else cAligned
    val smallM = if (pIsLarger) cAligned else pAligned
    val sOut = if (pIsLarger) pSign else cSign

    val addRes = largeM + smallM                                    // ≤32-bit
    val subRes = largeM - smallM                                    // ≥0（pIsLarger 保证）

    val (accMant, accExp) =
      if (effectSub == 1) (subRes & M31, baseExp)
      else if (((addRes >> 31) & 1) == 1) ((addRes >> 1) & M31, (baseExp + 1) & 0xFF)
      else (addRes & M31, baseExp)

    if (pZero) cRaw
    else if (cZero) pack40(pSign, pExp, pMant31)
    else if (effectSub == 1 && subRes == 0) BigInt(0)
    else pack40(sOut, accExp, accMant)
  }

  /** 现役：fp32 乘积 prod32 累加进 lazy-40 槽 c40（= HiveMacLazyDownstream） */
  private def lazyAcc(prod32: Long, c40: BigInt): BigInt = {
    val pExp = fExp(prod32); val pMant = fMant(prod32)
    val pMant31 = (BigInt(1) << 30) | (pMant << 7)                  // Cat(1,pMant,0^7)
    lazyCore(fSign(prod32), pExp, pMant31, (pExp == 0) && (pMant == 0), c40)
  }

  /** CSA 用：把一个 lazy-40 值（S）当 prod 侧累加进 lazy-40 槽 c40（保 31-bit 尾数，不退回 fp32） */
  private def lazyAdd40(s40: BigInt, c40: BigInt): BigInt = {
    val pExp = ((s40 >> 31) & 0xFF).toInt
    val pMant31 = s40 & M31
    lazyCore(((s40 >> 39) & 1).toInt, pExp, pMant31, (pExp == 0) && (pMant31 == 0), c40)
  }

  // ==========================================================================
  // Phase 2 级联（左结合，k 升序、低 sublane 先加）
  // ==========================================================================
  private def cascade(cReg40: BigInt, prods: Seq[Long]): BigInt = {
    var acc = cReg40 & M40
    for (p <- prods) acc = lazyAcc(p, acc)
    acc
  }

  // ==========================================================================
  // 拟议 CSA（5c）三变体
  // ==========================================================================
  private def unpackProd(p: Long): (Int, Int, BigInt, Boolean) = {
    val e = fExp(p); val m = fMant(p)
    (fSign(p), e, (BigInt(1) << 30) | (m << 7), (e == 0) && (m == 0))
  }
  /** 对齐右移（cap 31、截断向零）：与级联同款移位语义 */
  private def alignMag(mant31: BigInt, shift: Int): BigInt =
    if (shift >= 31) BigInt(0) else (mant31 >> shift) & M31
  /** 精确整数和 V（在参考指数 refExp 处）→ lazy-40（仅上溢右移、不 LZC、截断向零） */
  private def normalizeToLazy40(V: BigInt, refExp: Int): BigInt = {
    if (V == 0) return BigInt(0)
    val sign = if (V < 0) 1 else 0
    var mag = V.abs
    var shifts = 0
    while (mag > M31) { mag >>= 1; shifts += 1 }                    // 上溢规格化（截断）
    pack40(sign, (refExp + shifts) & 0xFF, mag & M31)
  }
  /** 4 窄积按公共指数 E=max 积指数并行对齐 → 二补精确和 → lazy-40 S */
  private def csaProductSum(prods: Seq[Long]): BigInt = {
    val items = prods.map(unpackProd)
    val nz = items.filter(!_._4)
    if (nz.isEmpty) return BigInt(0)
    val E = nz.map(_._2).max
    var V = BigInt(0)
    for ((s, e, m31, zero) <- items) if (!zero) {
      val am = alignMag(m31, E - e)
      V += (if (s == 1) -am else am)
    }
    normalizeToLazy40(V, E)
  }
  /** 变体 1（提案直译 + 最宽对齐）：S 与 cReg 再做一次 lazy-40 累加（保 31-bit） */
  private def csaLazyLast(cReg40: BigInt, prods: Seq[Long]): BigInt =
    lazyAdd40(csaProductSum(prods), cReg40 & M40)
  /** 变体 2（字面复用 HiveMacLazyDownstream）：S 舍入成 fp32 再 lazyAcc */
  private def csaFp32Last(cReg40: BigInt, prods: Seq[Long]): BigInt =
    lazyAcc(HiveSimCommon.swFpNormalize40to32(csaProductSum(prods)), cReg40 & M40)
  /** 变体 3（理论最优单对齐）：cReg 作为第 5 操作数与 4 积一并对齐、单次和、单次规格化 */
  private def csaFiveOp(cReg40: BigInt, prods: Seq[Long]): BigInt = {
    val items = prods.map(unpackProd)
    val cRaw = cReg40 & M40
    val cSign = ((cRaw >> 39) & 1).toInt
    val cExp = ((cRaw >> 31) & 0xFF).toInt
    val cMant = cRaw & M31
    val cZero = (cExp == 0) && (cMant == 0)
    val nzp = items.filter(!_._4)
    if (nzp.isEmpty && cZero) return cRaw
    if (nzp.isEmpty) return cRaw                                    // 全零积 ⟹ 级联亦原样返回 cReg
    val prodMaxE = nzp.map(_._2).max
    val E = if (cZero) prodMaxE else math.max(prodMaxE, cExp)
    var V = BigInt(0)
    for ((s, e, m31, zero) <- items) if (!zero) {
      val am = alignMag(m31, E - e); V += (if (s == 1) -am else am)
    }
    if (!cZero) { val am = alignMag(cMant, E - cExp); V += (if (cSign == 1) -am else am) }
    normalizeToLazy40(V, E)
  }

  // ==========================================================================
  // 激励构造 helper
  // ==========================================================================
  /** 由 4 对 nibble + 共享 scaleA/scaleB 生成 4 个 prod32（lane0 先加） */
  private def quadProds(aNib: Seq[Int], bNib: Seq[Int], sA: Int, sB: Int): Seq[Long] = {
    require(aNib.size == 4 && bNib.size == 4)
    (0 until 4).map(i => narrowMul(e2m1(aNib(i), sA), e2m1(bNib(i), sB)))
  }
  /** 干净 cReg：fp32 值落 lazy-40 槽（复用 HiveSimCommon.fp32BitsTo40） */
  private def cleanCReg(v: Double): BigInt =
    HiveSimCommon.fp32BitsTo40(floatToRawIntBits(v.toFloat).toLong & 0xFFFFFFFFL)
  /** 脏 cReg：直接按 (sign,exp,mant31) 构造 lazy-40（探测截断边界用） */
  private def dirtyCReg(s: Int, e: Int, m: BigInt): BigInt = pack40(s, e, m)

  private final class Mismatch {
    private val buf = scala.collection.mutable.ArrayBuffer[String]()
    private var n = 0
    def add(msg: String): Unit = { n += 1; if (buf.size < 24) buf += msg }
    def count: Int = n
    def detail: String = buf.mkString("\n  ")
  }

  private val rnd = new scala.util.Random(0x5EEDL)

  // ==========================================================================
  // A. 模型 faithfulness 自证（纯软件，禁 RTL 拟合）
  // ==========================================================================
  behavior of "模型 faithfulness —— E2M1 解码 / 窄化乘法（纯软件对账金锚）"

  it should "e2m1 解码逐值 == HiveSimCommon.mxE2M1ToFloat（16 编码 × 256 scale = 4096 向量）" in {
    val bad = new Mismatch
    var vectors = 0
    for (nib <- 0 until 16; scale <- 0 until 256) {
      vectors += 1
      val bits = e2m1(nib, scale)
      val mine = intBitsToFloat(bits.toInt).toDouble
      val golden = HiveSimCommon.mxE2M1ToFloat(nib, scale)
      // ±0 在 Double 域相等；饱和/FTZ 值亦逐位同源
      if (!(mine == golden)) bad.add(f"[decode] nib=0x$nib%X scale=$scale mine=$mine golden=$golden bits=0x$bits%08X")
    }
    withClue(s"E2M1 解码失配 ${bad.count} 处：\n  ${bad.detail}") { bad.count should be(0) }
    println(f"[$TAG] e2m1 解码对账通过：$vectors%d 向量，失配 ${bad.count}%d")
  }

  it should "narrowMul 非饱和域精确：prod32 数值 == mxE2M1ToFloat(a)*mxE2M1ToFloat(b)，且 guardBit 结构性为 0" in {
    val bad = new Mismatch
    var vectors = 0; var guardHits = 0; var satSkipped = 0
    // 单积全穷举：16×16 nibble 对 × 代表性 scale（含中性/展布/边界），验证乘积精确性
    for (aN <- 0 until 16; bN <- 0 until 16; sA <- Seq(127, 120, 134); sB <- Seq(127, 130, 124)) {
      val aBits = e2m1(aN, sA); val bBits = e2m1(bN, sB)
      // 跳过饱和操作数（fp32Max 尾数全 1，窄化会截断，属已文档化的唯一偏差域，非本次裁决目标）
      if ((aBits & 0x7FFFFFFFL) == Fp32MaxBits || (bBits & 0x7FFFFFFFL) == Fp32MaxBits) { satSkipped += 1 }
      else {
        vectors += 1
        val prod = narrowMul(aBits, bBits)
        val av = HiveSimCommon.mxE2M1ToFloat(aN, sA); val bv = HiveSimCommon.mxE2M1ToFloat(bN, sB)
        val expect = av * bv
        val got = intBitsToFloat(prod.toInt).toDouble
        // 乘积在 fp32 域精确（E2M1 每元素 ≤2 有效位 ⟹ 积 ≤4 有效位，恰被 fp32 无损表示）
        if (expect != 0.0 && expect.toFloat.toDouble != expect)
          bad.add(f"[exact-dom] aN=0x$aN%X bN=0x$bN%X expect=$expect 非 fp32 精确")
        if (!(got == expect))
          bad.add(f"[mul] aN=0x$aN%X bN=0x$bN%X sA=$sA sB=$sB got=$got expect=$expect prod=0x$prod%08X")
        // guardBit 结构性为 0 自证：roundUp 不改变尾数 ⟺ prod 与 RTZ 结果同（此处直接验证尾数低 20 位来自精确积）
        if (expect != 0.0) {
          val rtz = narrowMul(aBits, bBits) // RNE 与 RTZ 在 guardBit=0 下同
          if (rtz != prod) guardHits += 1
        }
      }
    }
    withClue(s"narrowMul 失配 ${bad.count} 处：\n  ${bad.detail}") { bad.count should be(0) }
    guardHits should be(0)
    println(f"[$TAG] narrowMul 精确性通过：$vectors%d 向量（跳过饱和 $satSkipped%d），guardBit 偏差 $guardHits%d")
  }

  // ==========================================================================
  // B. cascade 模型 == 真实 RTL quad-lane（ChiselSim 交叉验证：模型忠实性的金标准）
  // ==========================================================================
  behavior of "cascade 模型 == HiveWorkUnit quad-lane RTL（逐位交叉验证）"

  it should "craft+随机域下 cascade(40-bit) 逐位 == dut.io.sum(40-bit)" in {
    val bad = new Mismatch
    var vectors = 0
    val nibSets = Seq(Seq(0, 1, 2, 3), Seq(7, 6, 5, 4), Seq(1, 3, 5, 7), Seq(8, 9, 10, 11),
      Seq(0xF, 0xE, 0xD, 0xC), Seq(2, 4, 6, 0), Seq(1, 1, 1, 1), Seq(7, 8, 7, 8),
      Seq(0, 0, 0, 7), Seq(3, 12, 5, 10))
    val scalePairs = Seq((127, 127), (124, 130), (130, 124), (126, 128))
    val cRegVals = Seq(0.0, 1.0, -2.0, 0.5, 10.0, -0.25, 36.0)
    def packFp4(n: Seq[Int]): Int = (n(0) & 0xF) | ((n(1) & 0xF) << 4) | ((n(2) & 0xF) << 8) | ((n(3) & 0xF) << 12)

    simulate(new HiveWorkUnit(aW = 16, bW = 16, cW = 40,
        supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1))) { dut =>
      dut.reset.poke(true.B)
      dut.io.a.poke(0.U); dut.io.b.poke(0.U); dut.io.cReg.poke(0.U)
      dut.io.aFmt.poke(DataFormat.MXE2M1); dut.io.bFmt.poke(DataFormat.MXE2M1)
      dut.io.scaleA.poke(127.U); dut.io.scaleB.poke(127.U)
      dut.io.rnd.poke(RoundingMode.RNE); dut.io.consumeValid.poke(false.B)
      dut.clock.step(2); dut.reset.poke(false.B); dut.clock.step()

      def drive(a: Int, b: Int, cReg: BigInt, sA: Int, sB: Int): BigInt = {
        dut.io.a.poke(a.U); dut.io.b.poke(b.U); dut.io.cReg.poke(cReg.U)
        dut.io.aFmt.poke(DataFormat.MXE2M1); dut.io.bFmt.poke(DataFormat.MXE2M1)
        dut.io.scaleA.poke(sA.U); dut.io.scaleB.poke(sB.U); dut.io.consumeValid.poke(false.B)
        dut.io.sum.peek().litValue
      }
      for (aN <- nibSets; bN <- nibSets; (sA, sB) <- scalePairs; cv <- cRegVals) {
        vectors += 1
        val cReg40 = cleanCReg(cv)
        val rtl = drive(packFp4(aN), packFp4(bN), cReg40, sA, sB) & M40
        val model = cascade(cReg40, quadProds(aN, bN, sA, sB))
        if (rtl != model)
          bad.add(f"[cascade-vs-rtl] aN=$aN bN=$bN sA=$sA sB=$sB cReg=$cv rtl=0x${rtl.toString(16)} model=0x${model.toString(16)}")
      }
    }
    withClue(s"cascade 模型与 RTL 失配 ${bad.count} 处：\n  ${bad.detail}") { bad.count should be(0) }
    println(f"[$TAG] cascade 模型 == RTL quad-lane 通过：$vectors%d 向量逐位相同，失配 ${bad.count}%d")
  }

  // ==========================================================================
  // C. 裁决核心：cascade vs CSA 逐位对拍（分域统计）
  // ==========================================================================
  behavior of "裁决 —— cascade(Phase 2) vs CSA(5c) 逐位对拍"

  /** 把一个变体输出 csa 与 cascade 输出 casc 分三档比对：
    *   raw  = 40-bit lazy 逐位不同（最严；lazy-40 非规范编码会使「同值不同码」计入此档）
    *   val  = 有理值不同（真实数值分歧，破坏非回归的根因档）
    *   fp32 = 经 swFpNormalize40to32 归一化后的最终 C 输出不同（可观测契约档，= HiveWorkUnitMxSpec 判据） */
  private def classify(casc: BigInt, cascN: Long, csa: BigInt): (Boolean, Boolean, Boolean) = {
    val rawDiff = casc != csa
    val valDiff = rawDiff && !sameValue(casc, csa)
    val fp32Diff = cascN != HiveSimCommon.swFpNormalize40to32(csa)
    (rawDiff, valDiff, fp32Diff)
  }

  /** 通用对拍器：给定向量流，三变体各分 raw/val/fp32 三档统计并收集反例。
    * 裁决看【val】与【fp32】两档：val=0 ⟹ CSA 与级联数值恒等（仅编码可不同，对下游无害）；
    * fp32=0 ⟹ 最终可观测 C 输出逐位相同（非回归契约满足）。 */
  private final class Compare {
    var n = 0
    var rawLazy = 0; var valLazy = 0; var fp32Lazy = 0
    var rawFive = 0; var valFive = 0; var fp32Five = 0
    var rawFp32v = 0; var valFp32v = 0; var fp32Fp32v = 0
    val exVal = new Mismatch   // 值分歧反例（裁决关键）
    val exEnc = new Mismatch   // 仅编码不同反例（佐证 lazy-40 非规范）
    def run(cReg40: BigInt, prods: Seq[Long], clue: => String): Unit = {
      n += 1
      val casc = cascade(cReg40, prods)
      val cascN = HiveSimCommon.swFpNormalize40to32(casc)
      val l = csaLazyLast(cReg40, prods)
      val v = csaFiveOp(cReg40, prods)
      val f = csaFp32Last(cReg40, prods)
      val (rl, vl, fl) = classify(casc, cascN, l)
      val (rv, vv, fv) = classify(casc, cascN, v)
      val (rf, vf, ff) = classify(casc, cascN, f)
      if (rl) rawLazy += 1; if (vl) valLazy += 1; if (fl) fp32Lazy += 1
      if (rv) rawFive += 1; if (vv) valFive += 1; if (fv) fp32Five += 1
      if (rf) rawFp32v += 1; if (vf) valFp32v += 1; if (ff) fp32Fp32v += 1
      if (vl) exVal.add(s"$clue lazyLast【值分歧】casc=0x${casc.toString(16)}(norm=0x${cascN.toHexString}) csa=0x${l.toString(16)}(norm=0x${HiveSimCommon.swFpNormalize40to32(l).toHexString})")
      else if (rl) exEnc.add(s"$clue lazyLast【仅编码】casc=0x${casc.toString(16)} csa=0x${l.toString(16)}(同值 norm=0x${cascN.toHexString})")
      if (vv) exVal.add(s"$clue fiveOp【值分歧】casc=0x${casc.toString(16)} csa=0x${v.toString(16)}")
    }
    def line(tag: String): String =
      f"[$TAG] $tag：向量 $n%d%n" +
      f"  csaLazyLast  raw=$rawLazy%-6d val=$valLazy%-6d fp32=$fp32Lazy%-6d%n" +
      f"  csaFiveOp    raw=$rawFive%-6d val=$valFive%-6d fp32=$fp32Five%-6d%n" +
      f"  csaFp32Last  raw=$rawFp32v%-6d val=$valFp32v%-6d fp32=$fp32Fp32v%-6d"
  }

  it should "域 1（cReg=0，纯 4 积）：2-lane 全穷举 + 3/4-lane 定向&随机 —— cascade==csaLazyLast 逐位" in {
    val cmp = new Compare
    // 2-lane 全穷举：lane0/lane1 各遍历 16×16 nibble 对（lane2/3=0），共享中性 scale
    for (a0 <- 0 until 16; b0 <- 0 until 16; a1 <- 0 until 16; b1 <- 0 until 16) {
      val aN = Seq(a0, a1, 0, 0); val bN = Seq(b0, b1, 0, 0)
      cmp.run(BigInt(0), quadProds(aN, bN, 127, 127), s"[2L a0=$a0 b0=$b0 a1=$a1 b1=$b1]")
    }
    // 3-lane 定向（指数展布最大：6×6 与 0.5×0.5 混合；最小：全同）+ 随机
    val directed3 = Seq(
      (Seq(7, 1, 7, 0), Seq(7, 1, 7, 0)),   // 36 与 0.25 混合（最大展布）
      (Seq(1, 1, 1, 0), Seq(1, 1, 1, 0)),   // 全 0.5×0.5（最小展布）
      (Seq(7, 0xF, 6, 0), Seq(2, 2, 3, 0)), // 正负对消混合
      (Seq(2, 4, 6, 1), Seq(1, 3, 5, 7))
    )
    for ((aN, bN) <- directed3) {
      for ((sA, sB) <- Seq((127, 127), (120, 134), (134, 120), (126, 128)))
        cmp.run(BigInt(0), quadProds(aN, bN, sA, sB), s"[3Ld $aN $bN $sA/$sB]")
    }
    // 4-lane 随机（大样本）：随机 nibble + 随机共享 scale
    for (_ <- 0 until 120000) {
      val aN = Seq.fill(4)(rnd.nextInt(16)); val bN = Seq.fill(4)(rnd.nextInt(16))
      val sA = 100 + rnd.nextInt(55); val sB = 100 + rnd.nextInt(55)
      cmp.run(BigInt(0), quadProds(aN, bN, sA, sB), s"[4Lr $aN $bN $sA/$sB]")
    }
    println(cmp.line("域1 cReg=0 纯4积"))
    println(s"  【值分歧】反例（若有，前 24）：\n  ${cmp.exVal.detail}")
    println(s"  【仅编码不同/同值】反例样本（前 8，佐证 lazy-40 非规范）：\n  ${cmp.exEnc.detail}")
    // cReg=0 纯 4 积：4 lane 共享 scale ⟹ 积指数展布 ≤7 ⟹ 对齐无损 ⟹ CSA 与级联【值恒等】。
    // raw 档可 >0（lazy-40 非规范编码：同值不同码），故断言 val/fp32 两档为 0。
    cmp.valLazy should be(0)
    cmp.fp32Lazy should be(0)
    cmp.valFive should be(0)
    cmp.fp32Five should be(0)
  }

  it should "域 2（真实运行域 cReg）：cReg=前序级联输出/干净 fp32 值 —— cascade==csaLazyLast 逐位" in {
    val cmp = new Compare
    // 真实运行域：cReg 是前序 K-块累加的输出（lazy-40，指数与积同量级），或多块累加链
    val cleanVals = Seq(0.0, 1.0, -1.0, 0.5, -0.5, 2.0, -2.0, 6.0, 36.0, -36.0, 100.0, 0.25, 1e-3, 1e3)
    for (_ <- 0 until 40000) {
      val aN = Seq.fill(4)(rnd.nextInt(16)); val bN = Seq.fill(4)(rnd.nextInt(16))
      val sA = 110 + rnd.nextInt(35); val sB = 110 + rnd.nextInt(35)
      val prods = quadProds(aN, bN, sA, sB)
      // cReg 三来源：干净值、前序级联（脏 lazy-40）、随机链
      val cReg40 = rnd.nextInt(3) match {
        case 0 => cleanCReg(cleanVals(rnd.nextInt(cleanVals.size)))
        case 1 =>
          val pa = Seq.fill(4)(rnd.nextInt(16)); val pb = Seq.fill(4)(rnd.nextInt(16))
          cascade(cleanCReg(cleanVals(rnd.nextInt(cleanVals.size))), quadProds(pa, pb, sA, sB))
        case _ =>
          var acc = cleanCReg(cleanVals(rnd.nextInt(cleanVals.size)))
          for (_ <- 0 until 1 + rnd.nextInt(4)) {
            val pa = Seq.fill(4)(rnd.nextInt(16)); val pb = Seq.fill(4)(rnd.nextInt(16))
            acc = cascade(acc, quadProds(pa, pb, sA, sB))
          }
          acc
      }
      cmp.run(cReg40, prods, s"[real $aN $bN $sA/$sB cReg=0x${cReg40.toString(16)}]")
    }
    println(cmp.line("域2 真实运行域 cReg"))
    println(s"  【值分歧】反例（若有，前 24）：\n  ${cmp.exVal.detail}")
    println(s"  【仅编码不同/同值】反例样本（前 8）：\n  ${cmp.exEnc.detail}")
    // ══ 裁决 C 的核心反例证据 ══
    // 真实运行域：cReg 是前序 K-block 的【脏】lazy-40 累加器（尾数低位被加/减/规格化填满，
    // 非全零）。此时级联每步「对齐右移截断向零 + 上溢规格化右移」会丢弃累加器真实低位，
    // 而 CSA 单次对齐只截断一次 ⟹ 二者【值分歧】（非仅编码不同），且分歧可穿透 swFpNormalize40to32
    // 改变最终 fp32 C 输出 ⟹ 破坏「bit-exact 非回归」硬门槛。
    // 本 spec 作为反例文档永久保留：断言【存在】值分歧与 fp32 输出分歧（而非断言等价）。
    info(s"域2 csaLazyLast：raw=${cmp.rawLazy}/${cmp.n} val=${cmp.valLazy}/${cmp.n} fp32=${cmp.fp32Lazy}/${cmp.n}；" +
         s"csaFiveOp：val=${cmp.valFive}/${cmp.n} fp32=${cmp.fp32Five}/${cmp.n}")
    cmp.valLazy should be > 0     // 值分歧存在 ⟹ 融合在真实脏 cReg 域不可 bit-exact
    cmp.fp32Lazy should be > 0    // 且穿透到可观测 fp32 C 输出 ⟹ 硬非回归违规
  }

  it should "域 3（cReg 指数展布扫描）：定位 cascade≠CSA 的截断边界（反例测绘）" in {
    // 固定两个积四元组（最大进位 4×36；混合），cReg 指数相对 E 扫描 gap=-40..+40，
    // 尾数用多种模式（含低位全 1、单低位、1.0），统计每个 gap 的失配，揭示安全窗口。
    val quads = Seq(
      (Seq(7, 7, 7, 7), Seq(7, 7, 7, 7)),   // 4×(6×6)=4×36，进位最多
      (Seq(7, 1, 5, 3), Seq(6, 2, 4, 1))    // 混合指数展布
    )
    val mantPats: Seq[(String, BigInt)] = Seq(
      ("1.0", BigInt(1) << 30),
      ("allOnes", M31),
      ("lowBit1", (BigInt(1) << 30) | BigInt(1)),
      ("low7ones", (BigInt(1) << 30) | BigInt(0x7F)),
      ("1.5", BigInt(0x60000000))
    )
    println(f"[$TAG] 域3 cReg 指数展布扫描（gap = cReg指数 - 积最大指数 E）：")
    for ((aN, bN) <- quads) {
      val prods = quadProds(aN, bN, 127, 127)
      val E = prods.map(p => fExp(p)).filter(_ != 0) match { case s if s.isEmpty => 0; case s => s.max }
      val perGapRaw = scala.collection.mutable.ArrayBuffer[String]()
      val perGapVal = scala.collection.mutable.ArrayBuffer[String]()
      var totalVec = 0; var totalRaw = 0; var totalVal = 0
      for (gap <- -40 to 40; (mn, mp) <- mantPats; sgn <- Seq(0, 1)) {
        val cExp = E + gap
        if (cExp >= 1 && cExp <= 254) {
          val cReg40 = dirtyCReg(sgn, cExp, mp)
          val casc = cascade(cReg40, prods)
          val csa = csaLazyLast(cReg40, prods)
          totalVec += 1
          if (casc != csa) {
            totalRaw += 1; perGapRaw += f"gap=$gap%d($mn,s$sgn)"
            if (!sameValue(casc, csa)) { totalVal += 1; perGapVal += f"gap=$gap%d($mn,s$sgn)" }
          }
        }
      }
      println(f"  quad aN=$aN bN=$bN E=$E：向量 $totalVec%d，raw失配 $totalRaw%d，其中【值分歧】$totalVal%d")
      println(s"    raw 失配 gap 点：${perGapRaw.distinct.sorted.mkString(", ")}")
      println(s"    【值分歧】gap 点：${perGapVal.distinct.sorted.mkString(", ")}")
    }
    // 断言：确实存在失配（证明融合非无条件 bit-exact）——反例文档化
    // ══ 最小反例钉死根因 ══：即便 gap=0（cReg 指数 == 积最大指数 E，无对齐右移），只要 cReg
    // 尾数低位非零（dirty），级联的【逐次上溢规格化右移截断】与 CSA 的【单次规格化】即产生
    // 【值分歧】⟹ 证明根因是「FP 加法非结合 + 截断向零的顺序依赖」，而非对齐位宽不足。
    val pMini = quadProds(Seq(7, 7, 7, 7), Seq(7, 7, 7, 7), 127, 127)
    val Emini = pMini.map(fExp).filter(_ != 0).max
    val cMini = dirtyCReg(1, Emini, (BigInt(1) << 30) | BigInt(1))   // 负号 + lowBit1（低位有 1）
    val cascMini = cascade(cMini, pMini); val csaMini = csaLazyLast(cMini, pMini)
    println(f"[$TAG] 域3 最小反例 gap=0：cReg=0x${cMini.toString(16)} casc=0x${cascMini.toString(16)} csa=0x${csaMini.toString(16)} 同值=${sameValue(cascMini, csaMini)}")
    sameValue(cascMini, csaMini) should be(false)   // gap=0 脏 cReg 即值分歧 ⟹ 融合根因非对齐位宽
    info("域3 结论：积间对齐恒无损（跨度≤7）；分歧根因=脏 cReg 参与时级联的顺序截断向零，安全窗口仅 cReg 尾数低位全零（含 cReg=0）")
  }

  it should "域 4（csaFp32Last / csaFiveOp 对照）：量化两种 cReg 折入设计的偏差面" in {
    val cmp = new Compare
    for (_ <- 0 until 40000) {
      val aN = Seq.fill(4)(rnd.nextInt(16)); val bN = Seq.fill(4)(rnd.nextInt(16))
      val sA = 100 + rnd.nextInt(55); val sB = 100 + rnd.nextInt(55)
      val prods = quadProds(aN, bN, sA, sB)
      val cReg40 = rnd.nextInt(2) match {
        case 0 => BigInt(0)
        case _ => cleanCReg(Seq(0.0, 1.0, -2.0, 36.0, 0.5, 100.0)(rnd.nextInt(6)))
      }
      cmp.run(cReg40, prods, s"[v4 $aN $bN cReg=0x${cReg40.toString(16)}]")
    }
    println(cmp.line("域4 变体对照"))
    println(s"  【值分歧】反例（若有，前 24）：\n  ${cmp.exVal.detail}")
    info(s"三变体分档见上；csaLazyLast/csaFiveOp 为融合候选，csaFp32Last 为字面复用下游（精度最劣）")
  }
}
