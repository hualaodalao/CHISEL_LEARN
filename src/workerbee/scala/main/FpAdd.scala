/** FpAdd — 多乘积累加器（纯组合，fp4 路径走定点块和树）。一次累加同时接收多路乘积
  * （lane0 满宽/窄化、fp8 窄化、fp4 窄化 ×2）累加到 cReg，输出仍为 lazy-40。
  *
  * lane0（prodFp16 槽）的乘积来源随上游 supportedFmts 而变：hasFp=true 走宽阵列
  *   FpMulFp16Bf16（fp16/bf16/mx8/mx4 → 满宽 fp32），hasFp=false 走窄乘法器 FpMulMx8（mx8/mx4
  *   → mid 域）。两配置 lane0 同为 fp32(32-bit) 槽、mProd = 2^44·(2+m1ₐ)(2+m1_b) 不变 ⟹ 本模块
  *   逻辑相同；唯一差异是饱和域 magNarrow（见 fp4BlockSum 不变量 4）。
  *
  * 路径结构（三模式共享 2 个 lazyAdd 实例，MX4 复用 firstStage）：
  *   fp16 only:  prodSel = prodFp16; sum = firstStage = lazyAdd(cReg, prodSel)
  *   fp16+fp8:   sum = secondStage = lazyAdd(firstStage, prodFp8)
  *   fp4 (is4x): prodSel = fp4BlockSum(r0,r1,r2,r3); sum = firstStage
  *
  * bit-exact 前置条件：|cExp − Emax| ≤ 17 且块内无 catastrophic cancellation 时，与 4 级串行逐位
  *   相等。极端衰减域（跨块 scale 差 > 2^17，或块内异号乘积大幅相消）下块和内部精确无舍入，但
  *   lazy-40 位模式可能不同，最终 fp32 输出仍可能有 ≤1 ULP 差异。正常 MXFP4 工作流
  *   （scaleA+scaleB ∈ [129, ~200]）恒满足前置条件。
  *
  * lazy-40（低 40 bit）：[39] sign；[38:31] exponent（8-bit biased-127，不规格化）；[30:0]
  *   mantissa（31-bit，可能有 leading zeros）。不做 LZC（规格化推迟到 fpNormalize40to32），
  *   故减法后 mantissa 可能有前导零。
  */

import chisel3._
import chisel3.util._

class FpAdd(cEffW: Int = 40) extends Module {
  require(cEffW >= 40, s"FpAdd: 延迟规格化需要 cEffW($cEffW) >= 40")

  val io = IO(new Bundle {
    val is4x = Input(Bool())
    val is2x = Input(Bool())
    val cReg      = Input(UInt(cEffW.W))   // 当前累加器值（lazy-40: sign(1)|exp(8)|mant(31)）
    val prodFp16  = Input(UInt(32.W))      // lane0 乘积槽（fp32），来源由上游配置决定
    val prodFp8   = Input(UInt(32.W))      // fp8 窄化乘法器输出（fp32）
    val prodFp4_0 = Input(UInt(32.W))      // fp4 窄化乘法器 #0 输出（fp32）
    val prodFp4_1 = Input(UInt(32.W))      // fp4 窄化乘法器 #1 输出（fp32）
    val sum       = Output(UInt(cEffW.W))  // 累加结果（lazy-40 格式）
  })

  // 私有 lazyAdd：将 fp32 乘积累加到 lazy-40 累加器上。
  // 共享对齐移位器 + 合并加减法器；pZero/cZero 旁路 mux 由 pMant31 AND-mask 归零替代。
  private def lazyAdd(cIn: UInt, prod32: UInt): UInt = {
    val cRaw  = cIn(39, 0)
    val cSign = cRaw(39)
    val cExp  = cRaw(38, 31)
    val cMant = cRaw(30, 0)  // 31-bit，可能有 leading zeros

    val pSign = prod32(31)
    val pExp  = prod32(30, 23)
    val pMant = prod32(22, 0)
    val pZero = (pExp === 0.U) && (pMant === 0.U)
    // 24-bit → 31-bit：隐含 1 + 23bit mant + 7bit guard；pZero 时归零，使常规路径自然产生 cRaw
    val pMant31 = Cat(1.U(1.W), pMant, 0.U(7.W)) & Fill(31, ~pZero)

    val expDiffRaw = pExp.zext - cExp.zext  // 有符号 9-bit
    val pGtC = expDiffRaw >= 0.S
    val shiftAmt = Mux(pGtC,
      Mux(expDiffRaw > 31.S, 31.U(5.W), expDiffRaw.asUInt(4, 0)),
      Mux((-expDiffRaw) > 31.S, 31.U(5.W), (-expDiffRaw).asUInt(4, 0)))
    val baseExp = Mux(pGtC, pExp, cExp)

    // 共享对齐桶形移位器（布尔恒等）：pGtC ⟹ shifted=cMant>>amt、pAligned=pMant31、cAligned=shifted；
    // !pGtC ⟹ shifted=pMant31>>amt、pAligned=shifted、cAligned=cMant
    val shifted  = (Mux(pGtC, cMant, pMant31) >> shiftAmt)(30, 0)
    val pAligned = Mux(pGtC, pMant31, shifted)
    val cAligned = Mux(pGtC, shifted, cMant)

    val effectSub = pSign ^ cSign
    // 确定大小数：expDiff 严格 > 0 时 p 必然更大（对齐后 cAligned < 2^30 <= pAligned）；
    //   expDiff == 0（pGtC 也为真但不代表 p 更大）与 expDiff < 0 时都必须比较对齐后的尾数，
    //   否则等指数减法会误判大小数 → sOut 取错符号且 subRes 下溢。
    val pIsLarger = (expDiffRaw > 0.S) || (pAligned >= cAligned)
    val largeM = Mux(pIsLarger, pAligned, cAligned)
    val smallM = Mux(pIsLarger, cAligned, pAligned)
    val sOut   = Mux(pIsLarger, pSign, cSign)

    // 单 32-bit 加法器 + XOR/carry-in（布尔恒等）：effectSub=0 ⟹ largeM+smallM，bit31=进位；
    // effectSub=1 ⟹ largeM−smallM（mod 2^32），由 largeM≥smallM 保证结果 ∈ [0, 2^31-1]、bit31=0
    val res32 = Cat(0.U(1.W), largeM) +
      (Cat(0.U(1.W), smallM) ^ Fill(32, effectSub)) +
      effectSub.asUInt  // UInt(32.W)

    // 仅处理上溢，不做 LZC（延迟规格化核心：省面积）
    val accMant = WireDefault(0.U(31.W))
    val accExp  = WireDefault(0.U(8.W))

    when(effectSub) {
      accMant := res32(30, 0)
      accExp  := baseExp
    }.otherwise {
      when(res32(31)) {
        accMant := res32(31, 1)
        accExp  := baseExp + 1.U
      }.otherwise {
        accMant := res32(30, 0)
        accExp  := baseExp
      }
    }

    // 精确零 mux 必须保留：它产出 e==0 && m31==0 的真零；若删之，减法完全相消会留下 baseExp≠0
    //   的「指数非零零」，下游 isZero 检测漏判。
    // pZero ∧ cZero 时 pIsLarger=true ⟹ sOut=pSign，±0 的符号位与 cSign 取法不同，但下游
    //   Fp32.fpNormalize40to32 的 isZero 分支直接返回 0.U(32.W) 丢弃符号位 ⟹ 不可观测。
    val accRaw = Cat(sOut, accExp, accMant)  // 40-bit
    val result40 = Mux(effectSub && (res32(30, 0) === 0.U), 0.U(40.W), accRaw)

    if (cEffW == 40) result40
    else Cat(0.U((cEffW - 40).W), result40)
  }

  // fp4 并行定点块和树：4 个 fp32 乘积 → 精确和的 fp32 表示；数据通路窄化到 W=11。
  // 窄化不变量（W=11 的依据）：
  //   1. fp4 (E2M1) 乘积 mag=Cat(1,mant23) 有效位仅落在 bit[23:20]（4 位）：E2M1 值域
  //      {±0.5,±1,±1.5,±2,±3,±4,±6}，乘积尾数 ≤ 1.001（3 bit fraction）⟹ mant23 bit[19:0] 恒 0。
  //   2. 同一 MX block 四 lane 共享 scaleA/scaleB ⟹ 块内指数差 S ≤ 7（E2M1 exp ∈ {0,1,2,3}，
  //      bias=1；乘积 fp32 exp 极差 = 6 + 1 规格化进位）。
  //   3. 对齐窗口 W = 4 + S = 11 位（覆盖 mag bit[23:13]），对齐后有效位全在 11-bit 内。
  //   4. magNarrow ∈ {1024,1152,1536} = 128×{8,9,12}：三路 mProd 皆 = 2^44·(2+m1ₐ)(2+m1_b)，
  //      积 ∈ {4,6,9}。联合约束 1152⟹offset≥3、1536⟹offset≥1、1024⟹offset≥0
  //      （offset=e2ₐ+e2_b+normAdj ≤ 7），配合 shift = O−offset ≤ 7 ⟹ A_i ≡ 0 (mod 8) ⟹ 8|S
  //      ⟹ |S|≥8 ⟹ L ∈ [3,12]。饱和域例外：宽阵列饱和出 fp32Max（mant23=0x7FFFFF）⟹
  //      magNarrow=2047（≡7 mod 8）；窄路饱和 aSig=Cat(1,7)=15、P=aSig·bSig ∈ [64,225]、guardBit
  //      结构性 0 ⟹ magNarrow = 1024+16(P−64)（P<128）或 1024+8(P−128)（P≥128），恒 ≡0 (mod 8)、
  //      max=2016<2048 ⟹ 11-bit 不溢出（双侧饱和 P=225→1800，单侧 P=120→1920）。
  // 偏离域（均 out-of-spec，窄化只降级不引入新错误）：(a) 指数回绕/饱和，判据
  //   eAdj = aExp+bExp+normAdj ≥ 383（eOut = eAdj−127 ≥ 256 被 8-bit 截断），对应
  //   scaleA+scaleB ≳ 378..384。(b) 深度下溢（shift ≥ 8）需存在 eEff 比 Emax 小 ≥8 的 lane，
  //   偏离时降级为 Ai=0 不 mis-align；isZero=true 的零 lane magEff=0 不构成偏离。(c) lane0 饱和
  //   仅需单侧 scale ≥ 253（overflow ⟺ e2+scale ≥ 256）；【元素】指数字段 exp=254 ≠【乘积】指数
  //   字段 eOut=(aExp+bExp+normAdj−127)(7,0)，后者 8-bit 截断可回绕到 ~0 ⟹ 该域 expUnder 仍可达，
  //   由 expUnder 分支 + 末尾 mux 的 `|| expUnder` 兜底；误差有界：A_narrow = ⌊mag/2^13⌋>>shift
  //   = A_wide>>13 精确相等（不 mis-align），仅 Σ⌊·⌋ ≠ ⌊Σ·⌋ 带来 ≤4 LSB@2^13 ≈ 2^-9（mantV
  //   ±1 ULP），而 a 已饱和失真 ~33% ⟹ 被淹没。
  // 前置条件：输入 pi 为 normal fp32 或 ±0（不含 subnormal/Inf/NaN）；正常 MXFP4 工作流
  //   （scaleA + scaleB ≥ 129）恒满足；与 lazyAdd 一致不特殊处理 subnormal 乘积。
  // 块内和精确性：fp4 乘积有效尾数 ≤4 位、同 block 指数差 ≤7、精确和 ~14 位 ≪ fp32 的 24 位尾数
  //   ⟹ 对齐+求和+规格化全程无舍入。
  private def fp4BlockSum(p0: UInt, p1: UInt, p2: UInt, p3: UInt): UInt = {
    val W = 11   // 对齐窗口宽度 = 4(有效尾数位) + 7(最大指数差 S)
    val SBITS = 7  // 最大对齐移位量（同 block 指数差上界）

    // 解包 + 窄化：取 mag bit[23:13] 为 11-bit（fp4 乘积 bit[12:0] 恒为 0）
    def unpackLane(p: UInt): (Bool, UInt, UInt) = {
      val s = p(31)
      val e = p(30, 23)
      val m = p(22, 0)
      val isZero = (e === 0.U) && (m === 0.U)
      val magNarrow = Cat(1.U(1.W), m(22, 13))    // UInt(11.W)
      val magEff = Mux(isZero, 0.U(W.W), magNarrow)
      val eEff   = Mux(isZero, 0.U(8.W), e)
      (s, magEff, eEff)
    }

    val (s0, mag0, e0) = unpackLane(p0)
    val (s1, mag1, e1) = unpackLane(p1)
    val (s2, mag2, e2) = unpackLane(p2)
    val (s3, mag3, e3) = unpackLane(p3)

    val eMax01 = Mux(e0 >= e1, e0, e1)
    val eMax23 = Mux(e2 >= e3, e2, e3)
    val Emax   = Mux(eMax01 >= eMax23, eMax01, eMax23)

    // 对齐右移（11-bit，3 级桶形移位器 for shift ∈ [0,7]）
    val shift0 = Emax - e0
    val shift1 = Emax - e1
    val shift2 = Emax - e2
    val shift3 = Emax - e3

    // shift >= 8 时优雅降级为归零（仅指数回绕/深度下溢可达，out-of-spec 安全行为）
    val A0 = Mux(shift0 >= (SBITS+1).U, 0.U(W.W), (mag0 >> shift0(2, 0))(W-1, 0))
    val A1 = Mux(shift1 >= (SBITS+1).U, 0.U(W.W), (mag1 >> shift1(2, 0))(W-1, 0))
    val A2 = Mux(shift2 >= (SBITS+1).U, 0.U(W.W), (mag2 >> shift2(2, 0))(W-1, 0))
    val A3 = Mux(shift3 >= (SBITS+1).U, 0.U(W.W), (mag3 >> shift3(2, 0))(W-1, 0))

    val F0 = Mux(s0, -A0.zext, A0.zext)  // SInt(12.W)，范围 [-(2^11-1), 2^11-1]
    val F1 = Mux(s1, -A1.zext, A1.zext)
    val F2 = Mux(s2, -A2.zext, A2.zext)
    val F3 = Mux(s3, -A3.zext, A3.zext)

    // 显式 2 级平衡求和树；+& 扩位 12→13→14；|S| ≤ 4×(2^11-1) < 2^13
    val sum01 = F0 +& F1
    val sum23 = F2 +& F3
    val S     = sum01 +& sum23

    val sIsZero = (S === 0.S)

    val signV = S < 0.S
    val absS   = Mux(signV, -S, S).asUInt  // UInt(14.W)，bit13=0（非负 SInt）
    val absS13 = absS(12, 0)               // 13-bit 有效位（bit13 恒 0）

    val lzc = PriorityEncoder(Reverse(absS13))  // UInt(4.W)，∈ [0,12]
    val L   = 12.U(4.W) - lzc                   // 前导 1 位置，∈ [0,12]

    val normed = (absS13 << lzc)(12, 0)  // UInt(13.W)，前导 1 在 bit12
    // fp32 mant23 = {有效位(12), 结构性零(11)}：窄化后 bit[12:0] 以下全为 0
    val mantV = Cat(normed(11, 0), 0.U((23 - (W + 1)).W))  // UInt(23.W)

    // expV = L + Emax − 10（由 absS_narrow = absS_wide >> 13、L_narrow = L_wide − 13 代入
    // expV = L_wide + Emax − 23 得）
    val expSum  = L +& Emax              // UInt(9.W)，∈ [0, 267]
    val expVRaw = expSum.zext - 10.S     // SInt(10.W)，∈ [-10, 257]

    // 钳位（fp4 典型区间 expVRaw ∈ [~100, ~160]）。expUnder ⟺ L+Emax ≤ 10；由不变量 L ≥ 3 ⟹ 需
    // Emax ≤ 7（⟺ scaleA+scaleB ≤ 136−O ⟹ 乘积真值 ≤ 2^(7−127)，压在 fp32 normal 下沿附近），靠
    // 块内异号相消压低 |S| 即可达（与偏离域 (b) 的 shift≥8 降级域互斥）。该分支不得删：删之则
    // expVRaw<0 时低 8 位补码翻成 ≥254 的大指数。该域输出 ±0（FTZ）而非 denormal，符号位可能
    // 为 +0（属头注声明的 ≤1 ULP 偏离域）。
    val expUnder = expVRaw < 1.S
    val expOver  = expVRaw > 254.S
    val expFinal = Mux(expUnder, 0.U(8.W),
                   Mux(expOver, 254.U(8.W),
                     expVRaw.asUInt(7, 0)))

    val vNormal = Cat(signV.asUInt, expFinal, mantV)  // 32-bit fp32
    // S=0 → +0；指数下溢 → +0（本掩蔽项 || expUnder 与 expUnder 分支必须同存同删）
    Mux(sIsZero || expUnder, 0.U(32.W), vNormal)
  }

  // 三模式共享 2 个 lazyAdd 实例（Mux 布尔恒等重组，逐位相同）；优先级 is4x > is2x > else
  // （is4x=1 时 is2x 必为 0；即便 is2x=1，!is4x 仍使 sum=firstStage）。
  val blockV      = fp4BlockSum(io.prodFp16, io.prodFp8, io.prodFp4_0, io.prodFp4_1)
  val prodSel     = Mux(io.is4x, blockV, io.prodFp16)  // 32-bit：MX4→块和 V，其余→r0
  val firstStage  = lazyAdd(io.cReg, prodSel)          // fp16/MX8 与 MX4 共用
  val secondStage = lazyAdd(firstStage, io.prodFp8)    // 仅 MX8 使用
  io.sum := Mux(io.is2x && !io.is4x, secondStage, firstStage)

}
