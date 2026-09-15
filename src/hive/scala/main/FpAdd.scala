/** FpAdd — 多乘积累加器（纯组合，fp4 路径时序优化版）
  *
  * 用途：融合乘法器中一次累加操作同时接收来自不同乘法路径的多个乘积（lane0 满宽/窄化、
  * fp8 窄化、fp4 窄化 ×2），本模块将它们累加到 cReg 上，输出仍为 lazy-40 格式。
  *
  * 【配置感知 · 杠杆 A】lane0（prodFp16 槽）的乘积来源随上游 HiveFusionMacFp 的 supportedFmts 而变：
  *   · hasFp=true （默认，含 FP16/BF16）：lane0 走【宽阵列】FpMulFp16Bf16（fp16/bf16/mx8/mx4 → 满宽 fp32）；
  *   · hasFp=false（移除 fp16/bf16）：    lane0 走【窄乘法器】FpMulMx8（克隆 lane1，mx8/mx4 → mid 域）。
  *   两配置 lane0 乘积同为 fp32(32-bit) 槽、mProd=2^44·(2+m1ₐ)(2+m1_b) 不变（bit-exact，蓝图 §5），故
  *   FpAdd 本模块【逻辑与发射 RTL 两配置完全相同】；唯一差异是饱和域 magNarrow（见 fp4BlockSum 不变量 4）。
  *
  * 路径结构：
  *   fp16 only:   prodSel = prodFp16; sum = firstStage = lazyAdd(cReg, prodSel)   — 1 级
  *   fp16+fp8:    sum = secondStage = lazyAdd(firstStage, prodFp8)                — 2 级串行
  *   fp4 (is4x):  prodSel = fp4BlockSum(r0,r1,r2,r3); sum = firstStage            — 块和树 + 单级并入
  *   （M1：三模式共享 2 个 lazyAdd 实例，MX4 复用 firstStage，详见文件尾 M1 说明）
  *
  * fp4 (is4x) 路径：并行定点块和树 + 单级 lazyAdd 并入 cReg，组合深度从 ~260 级降至 ~123 级（约 2× 提速）。
  * 注：M1 实例合并给 fp4 路径净增 1 级 prodSel mux（32-bit）+ firstStage 输出扇出 +1（用户已接受此
  * 面积/时序 trade-off，记录以防后人误判 fp4 路径已到底）。
  * bit-exact 前置条件：正常工作区间（|cExp − Emax| ≤ 17 且块内无 catastrophic cancellation）下
  * 与基线 4 级串行逐位相等。极端衰减域（跨块 scale 差 > 2^17，或块内异号乘积大幅相消）：
  * 并行版精度优于串行基线（块和内部精确无舍入），但 lazy-40 位模式可能不同，最终 fp32 输出
  * 仍可能有 ≤1 ULP 差异。正常 MXFP4 工作流（scaleA+scaleB ∈ [129, ~200]）恒满足前置条件。
  *
  * lazy-40 格式定义（低 40 bit）：
  *   [39]    : sign（1-bit）
  *   [38:31] : exponent（8-bit，biased-127，不做规格化）
  *   [30:0]  : mantissa（31-bit，可能有 leading zeros — 延迟规格化核心，省 LZC 面积）
  *
  * 累加逻辑逐字复制自原 HiveWorkUnit.scala 中 HiveMacLazyDownstream / HiveMacFp 的下游部分
  * （二者已随 FusionMac 重构删除，此处为其唯一现存真源）：
  *   解包 cIn → 解包 prod32 → 指数对齐（桶形移位）→ 加减 → 上溢处理 → 零值旁路 → 输出。
  * 不做 LZC（规格化推迟到 fpNormalize40to32），故减法后 mantissa 可能有前导零。
  */

import chisel3._
import chisel3.util._

class FpAdd(cEffW: Int = 40) extends Module {
  require(cEffW >= 40, s"FpAdd: 延迟规格化需要 cEffW($cEffW) >= 40")

  val io = IO(new Bundle {
    val is4x = Input(Bool())
    val is2x = Input(Bool())
    val cReg      = Input(UInt(cEffW.W))   // 当前累加器值（lazy-40 格式: sign(1)|exp(8)|mant(31)）
    val prodFp16  = Input(UInt(32.W))      // lane0 乘积槽（fp32）：宽阵列(hasFp=true)或窄乘法器(hasFp=false)，由上游配置决定
    val prodFp8   = Input(UInt(32.W))      // fp8 窄化乘法器输出（fp32）
    val prodFp4_0 = Input(UInt(32.W))      // fp4 窄化乘法器 #0 输出（fp32）
    val prodFp4_1 = Input(UInt(32.W))      // fp4 窄化乘法器 #1 输出（fp32）
    val sum       = Output(UInt(cEffW.W))  // 累加结果（lazy-40 格式）
  })

  // =========================================================================
  // 私有 lazyAdd：将 fp32 乘积累加到 lazy-40 累加器上
  // 逻辑精确复制自原 HiveWorkUnit.scala 中 HiveMacLazyDownstream.apply（原 L370-441，已随 FusionMac 重构删除）
  // P4 面积优化：共享对齐移位器（−1×31-bit 桶形移位/实例）+ 合并加减法器（−1×31-bit 减法/实例）
  // P5 面积优化：删除 pZero/cZero 两个 40-bit 旁路 mux（−2×40-bit 3:1 mux/实例），
  //    改用 pMant31 AND-mask 归零（31×AND2 ≈ 32GE）使常规路径自然产生等价结果。
  // =========================================================================
  private def lazyAdd(cIn: UInt, prod32: UInt): UInt = {
    // ==== 解包累加器 cIn（低 40 bit） ====
    val cRaw  = cIn(39, 0)
    val cSign = cRaw(39)
    val cExp  = cRaw(38, 31)
    val cMant = cRaw(30, 0)  // 31-bit，可能有 leading zeros

    // ==== 解包乘积（标准 fp32） ====
    val pSign = prod32(31)
    val pExp  = prod32(30, 23)
    val pMant = prod32(22, 0)
    val pZero = (pExp === 0.U) && (pMant === 0.U)
    // P5: pZero 时强制归零，使常规路径自然产生 cRaw（消除 pZero 旁路 mux）
    // 24-bit → 31-bit：隐含1 + 23bit mant + 7bit guard；pZero 时全部清零
    val pMant31 = Cat(1.U(1.W), pMant, 0.U(7.W)) & Fill(31, ~pZero)

    // ==== 指数对齐 ====
    val expDiffRaw = pExp.zext - cExp.zext  // 有符号 9-bit
    val pGtC = expDiffRaw >= 0.S
    val shiftAmt = Mux(pGtC,
      Mux(expDiffRaw > 31.S, 31.U(5.W), expDiffRaw.asUInt(4, 0)),
      Mux((-expDiffRaw) > 31.S, 31.U(5.W), (-expDiffRaw).asUInt(4, 0)))
    val baseExp = Mux(pGtC, pExp, cExp)

    // P4.1: 共享对齐桶形移位器（单 31-bit 5 级移位器 + 输入 mux，替代原两个独立移位器）
    // 布尔恒等：pGtC=true → shifted=cMant>>amt, pAligned=pMant31, cAligned=shifted
    //           pGtC=false → shifted=pMant31>>amt, pAligned=shifted, cAligned=cMant
    val shifted  = (Mux(pGtC, cMant, pMant31) >> shiftAmt)(30, 0)
    val pAligned = Mux(pGtC, pMant31, shifted)
    val cAligned = Mux(pGtC, shifted, cMant)

    // ==== 加/减法 ====
    val effectSub = pSign ^ cSign
    // 确定大小数：expDiff 严格 > 0 时 p 必然更大（对齐后 c 被右移，pMant31 前导 1 在
    //   bit30，右移后的 cAligned < 2^30 <= pAligned）；expDiff == 0（pGtC 也为真但不代表
    //   p 更大）与 expDiff < 0 时都必须比较对齐后的尾数 pAligned/cAligned 决定大小数，
    //   否则等指数减法会误判大小数 → sOut 取错符号且 subRes 下溢。
    val pIsLarger = (expDiffRaw > 0.S) || (pAligned >= cAligned)
    val largeM = Mux(pIsLarger, pAligned, cAligned)
    val smallM = Mux(pIsLarger, cAligned, pAligned)
    val sOut   = Mux(pIsLarger, pSign, cSign)

    // P4.2: 合并加法器/减法器为单 32-bit 加法器 + XOR/carry-in（布尔恒等）
    // 加法（effectSub=0）：res32 = {0,largeM} + {0,smallM} + 0 = largeM+smallM，bit31=进位
    // 减法（effectSub=1）：res32 = {0,largeM} + {1,~smallM} + 1 = largeM-smallM（mod 2^32），
    //   由 largeM≥smallM 保证结果 ∈ [0, 2^31-1]，bit31=0
    val res32 = Cat(0.U(1.W), largeM) +
      (Cat(0.U(1.W), smallM) ^ Fill(32, effectSub)) +
      effectSub.asUInt  // UInt(32.W)

    // ==== 仅处理上溢，不做 LZC（延迟规格化核心：省面积） ====
    val accMant = WireDefault(0.U(31.W))
    val accExp  = WireDefault(0.U(8.W))

    when(effectSub) {
      // 减法：直接写回，不规格化
      accMant := res32(30, 0)
      accExp  := baseExp
    }.otherwise {
      when(res32(31)) {
        // 加法进位：右移 1，指数 +1
        accMant := res32(31, 1)
        accExp  := baseExp + 1.U
      }.otherwise {
        // 正常：直接写回
        accMant := res32(30, 0)
        accExp  := baseExp
      }
    }

    // ==== P5: 零值处理（仅保留精确零 mux，删除 pZero/cZero 旁路） ====
    // pZero 旁路已由 pMant31 AND-mask 归零替代（常规路径自然产生 cRaw）
    // cZero 旁路冗余（cZero 时 pGtC=true → 常规路径已产生 Cat(pSign,pExp,pMant31)）
    // 【S1 不可观测态说明】删旁路 mux 后的残余差异分两类：
    //   (i) 可达态：pZero ∧ cZero（cExp=0 ∧ cMant=0）时 pAligned=cAligned=0 → pIsLarger=true
    //       → sOut=pSign（基线旁路取 cSign），产出 ±0 的符号位不同。此差异【不可观测】——
    //       下游 Fp32.fpNormalize40to32 的 isZero=(e==0)&&(m31==0) 分支直接 `return 0.U(32.W)`，
    //       丢弃符号位，故 ±0 归一化后逐位相同（皆 +0）。
    //   (ii) 不可达态：pZero ∧ cMant==0 ∧ cExp≠0 时差异落在【指数/尾数域】而非符号位
    //       （effectSub=1 时 res32=1 → accMant=1 ≠ 0，精确零 mux 不触发），且 e≠0 使下游
    //       isZero=false → 可观测。但该态违反 lazy-40 不变量（真零必为 40'h0，由下方精确零
    //       mux 保证），故不可达。
    //   两类合起来 ⟹ 删 pZero/cZero 旁路 mux 对最终 fp32 输出 bit-exact 安全。
    //   注：精确零 mux（下方）必须保留——它产出的是 e==0&&m31==0 的真零；若删之，减法完全相消会
    //   留下 baseExp≠0 的"指数非零零"，isZero 检测漏判 → 真 bug。
    val accRaw = Cat(sOut, accExp, accMant)  // 40-bit
    val result40 = Mux(effectSub && (res32(30, 0) === 0.U), 0.U(40.W), accRaw)

    // ==== 输出：40-bit 放入 cEffW 位中 ====
    if (cEffW == 40) result40
    else Cat(0.U((cEffW - 40).W), result40)
  }

  // =========================================================================
  // fp4 并行定点块和树：4 个 fp32 乘积 → 精确和的 fp32 表示
  // P1 面积优化：数据通路窄化到 W=11（−24% 面积）
  //
  // ★ 窄化不变量（W=11 依赖以下前置条件）★
  //   1. fp4 (E2M1) 乘积的 mag=Cat(1,mant23) 有效位仅落在 bit[23:20]（4 位）：
  //      E2M1 值域 {±0.5,±1,±1.5,±2,±3,±4,±6}，乘积尾数 ≤ 1.001（3 bit fraction），
  //      故 fp32 mant23 的 bit[19:0] 为结构性 0。
  //   2. 同一 MX block 四 lane 共享 scaleA/scaleB ⇒ 块内指数差 S ≤ 7：
  //      E2M1 exp ∈ {0,1,2,3}（bias=1），乘积 fp32 exp 极差 = 6（无规格化进位）
  //      + 1（1.5×1.5=2.25 规格化进位）= 7。
  //   3. 对齐窗口 W = 4 + S = 11 位（覆盖 mag bit[23:13]），对齐后有效位全在 11-bit 内。
  //   4. 【离散结构不变量】magNarrow ∈ {1024,1152,1536} = 128×{8,9,12}（三路 mProd 同构：
  //      lane0 满宽 mA24=2^22(2+m1)、lane1 Mx8 aSig=4(2+m1) 且 <<40、lane2/3 Mx4 aSig=(2+m1)
  //      且 <<44 ⟹ 三者 mProd 皆 = 2^44·(2+m1ₐ)(2+m1_b)，积 ∈ {4,6,9}）。
  //      【配置感知】上式 lane0「满宽 mA24=2^22(2+m1)」仅适用默认配置(hasFp=true，走宽阵列)；移除
  //      配置(hasFp=false)下 lane0 改走窄乘法器 FpMulMx8 ⟹ aSig=4(2+m1) 且 <<40，与 lane1 同构。两配置
  //      lane0 的 mProd = 2^44·(2+m1ₐ)(2+m1_b)【均不变】(蓝图 §5) ⟹ 本不变量 in-spec 域两配置共享。
  //      联合约束 1152⟹offset≥3、1536⟹offset≥1、1024⟹offset≥0（offset=e2ₐ+e2_b+normAdj ≤ 7），
  //      配合 shift = O−offset ≤ 7 ⟹ A_i ≡ 0 (mod 8) ⟹ 8|S ⟹ |S|≥8 ⟹ L ∈ [3,12]。
  //      例外【配置感知，勿删 —— FpAdd 为两配置共享，默认配置的例外依然成立】：
  //        · 默认配置(hasFp=true)lane0 走宽阵列，overflow 饱和时 fp32Max mant23=0x7FFFFF → magNarrow=2047
  //          （≡7 mod 8）破坏本不变量，是该域【已记录例外】；但该域 Emax 通常很大（如 scaleA=253/scaleB=64→191），
  //          却【非下界】——乘积指数 eOut=(aExp+bExp+normAdj−127)(7,0) 为 8-bit 截断可回绕到 ~0（详见下方 R16 订正）。
  //        · 移除配置(hasFp=false)lane0 走窄乘法器 FpMulMx8，饱和域 aSig=Cat(1,7)=15 ⟹ magNarrow 随 P=aSig·bSig 取值
  //          （双侧饱和 P=225→1800、单侧饱和 P=120→1920），通式恒 ≡0 (mod 8)（位级实算见下方 §9.1(c)），例外【消除】。
  //      【移除配置新增饱和成员 1800/1920 · R16（订正）】窄路饱和积 P=aSig·bSig∈[64,225]（aSig=Cat(1,7)=15 为饱和值，
  //        不对应任何真实 E2M1 尾数：真实 (2+m1)∈{2,3} 给积 {4,6,9}），magNarrow=1024+16(P−64)（P<128）或
  //        1024+8(P−128)（P≥128）⟹ 全部 ≡0 (mod 8)、max=1024+16×62=2016<2048，不破坏窄化不变量（位级实算见 §9.1(c)）。
  //        【订正原 R16 论据错误】原文以「元素 exp=254 ⟹ Emax≥254 ⟹ expVRaw≥244 ⟹ expUnder 不可达、expOver 钳位生效」
  //        论证，混淆了【元素指数字段】exp=254（cvt 饱和输出）与【乘积指数字段】eOut：eOut=(aExp+bExp+normAdj−127)(7,0)
  //        是 8-bit 截断，aExp=254 时 eOut=(127+bExp+normAdj) mod 256 随 bExp 滑动（scaleA=253/scaleB=64→eOut=191；
  //        aExp=254/bExp=129/normAdj=0→eAdj=383→eOut 回绕为 0，此时 Emax 可很小、expUnder 反而可达）。
  //        【正确理由】下方 val expUnder 分支 + fp4BlockSum 末尾输出 mux 的 `|| expUnder` 掩蔽，对【任意】Emax/L 组合都已
  //        正确钳位 ⟹ 饱和域不产生新的未覆盖情形 ⟹ 无需为 expUnder 补新分支（而非「该域 Emax 必然 ≥254」）。
  //   偏离域（三条并列，均为 out-of-spec：数值本已无意义，窄化只降级不引入新错误）：
  //     (a) 指数回绕 / 饱和域【W1】：回绕的精确判据是 eAdj = aExp+bExp+normAdj ≥ 383
  //         （⟹ eOut = eAdj−127 ≥ 256 → 8-bit 截断），对应 scaleA+scaleB ≳ 378..384。
  //         "scaleA、scaleB 双侧 ≥ 253" 是【充分非必要】的易记子集（此时 eAdj ≥ 508，
  //         eOut 回绕成 ~125，元素真值 ≥ 2^127）；单侧 ≥253 + 另一侧 ~131 亦可回绕。
  //         OCP MX 规范中 E8M0 scale=0xFF(255) 为 NaN 保留码，253/254 亦属饱和边界。
  //     (b) 深度下溢【S2】：in-spec 下 shift = O−offset ≤ 7，故 shift ≥ 8 需【额外】存在
  //         eEff 比 Emax 小 ≥8 的 lane，来源仅两个（均 out-of-spec）：
  //         (b1) fp32 subnormal 乘积（eOut=0 ∧ mant23≠0，因 FpMul 的 eOut 在 eAdj<127 时钳 0）
  //              → isZero 判 false → eEff=0 → shift=Emax；需 scaleA+scaleB ≤ 128，违反『前置条件』段 scaleA+scaleB ≥ 129 不变量。
  //         (b2) 饱和域混合（一 lane eOut≈191，另一 lane 正常）。
  //         注：isZero=true 的零 lane 虽同样 shift=Emax，但 magEff=0 ⟹ 窄/宽路径都得 0，
  //         【不构成偏离】。
  //         偏离发生时优雅降级为归零（Ai=0），不产生 mis-align（shift ≥ 8 检测见下方 A0..A3）。
  //     (c) lane0 饱和【§9.1，配置感知】：MX4 模式下 lane0（prodFp16 槽）的饱和行为随上游配置而变，
  //         两配置该域【位模式不同】但【同属已记录 out-of-spec 非功能回归】：
  //         · 默认配置(hasFp=true)走满宽阵列 FpMulFp16Bf16：输入饱和 → fp32Max 尾数 23 位全 1
  //           （mant23=0x7FFFFF）→ unpackLane 只取 m(22,13) → magNarrow=2047（静默丢弃低 13 位）。
  //         · 移除配置(hasFp=false)走窄乘法器 FpMulMx8：输入饱和 → mant=7 → aSig=Cat(1,7)=15。位级实算（订正原误）：
  //           P=aSig·bSig，mProd=P<<40，normAdj=mProd(47)=(P≥128)，guardBit 结构性 0 ⟹ mRounded=mNorm，magNarrow=1024+mNorm(22,13)。
  //           双侧饱和 aSig=bSig=15 ⟹ P=225 ⟹ mProd(47)=1 ⟹ mNorm=mProd(46,24)=0x610000 ⟹ m(22,13)=776 ⟹ magNarrow=1800
  //             （原注释误写 1920 且漏算 bit16，订正之）。单侧饱和 aSig=15,bSig=8 ⟹ P=120 ⟹ mProd(47)=0 ⟹
  //             mNorm=mProd(45,23)=0x700000 ⟹ m(22,13)=896 ⟹ magNarrow=1920（1920 的真实来源）。
  //           通式：P∈[64,225]；P<128→1024+16(P−64)；P≥128→1024+8(P−128)；两式恒 ≡0 (mod 8)，max=1024+16×62=2016<2048
  //             （P=126=14×9 为 <128 分支可达上界；127 素数不可达）⟹ 11-bit magNarrow 不溢出。
  //         可达性【严于】(a)：仅需【单侧】scale ≥ 253（overflow ⟺ e2+scale ≥ 256 ⟹ scale ≥ 253
  //         且该元素 e2 ≥ 256−scale ≥ 1），故 (c) 的可达域严格大于 (a) 的双侧 ≥253。
  //         注意：该域 eOut 【不一定回绕】——如 scaleA=253/scaleB=64 时四 lane eOut 均 = 191
  //         （eAdj=318，318−127=191 < 256），指数有效且与真值自洽。
  //         仍属【非功能回归】的真正理由：A_narrow = ⌊mag/2^13⌋>>shift = A_wide>>13 精确相等
  //         （不 mis-align），仅 Σ⌊·⌋ ≠ ⌊Σ·⌋ 带来 ≤4 LSB@2^13 ≈ 2^-9 相对误差（mantV ±1 ULP）；
  //         而该域元素真值 ≥ 2^126 且 lane0 的 a 已被饱和失真（宽:fp32Max / 窄:aSig=15，相对误差 ~33%），
  //         2^-9 的额外损失被完全淹没。
  //
  // 前置条件：输入 pi 为 normal fp32 或 ±0（不含 subnormal / Inf / NaN）。
  //   - 正常 MXFP4 工作流（scaleA + scaleB ≥ 129）恒满足此条件。
  //   - 与基线 lazyAdd 一致：两者都不特殊处理 subnormal 乘积，故 bit-exact 不受影响。
  // 块内和精确性依据：fp4 乘积有效尾数 ≤4 位、同 block 指数差 ≤7、精确和 ~14 位 ≪ fp32
  //   的 24 位尾数，故对齐+求和+规格化全程无舍入。
  // =========================================================================
  private def fp4BlockSum(p0: UInt, p1: UInt, p2: UInt, p3: UInt): UInt = {
    // 窄化常量
    val W = 11   // 对齐窗口宽度 = 4(有效尾数位) + 7(最大指数差 S)
    val SBITS = 7  // 最大对齐移位量（同 block 指数差上界）

    // ---- 1. 解包 + 窄化：取 mag bit[23:13] 为 11-bit（低 13 位为结构性 0） ----
    def unpackLane(p: UInt): (Bool, UInt, UInt) = {
      val s = p(31)
      val e = p(30, 23)
      val m = p(22, 0)
      val isZero = (e === 0.U) && (m === 0.U)
      // 窄化 mag：只保留 bit[23:13]（11-bit）；fp4 乘积 bit[12:0] 恒为 0
      val magNarrow = Cat(1.U(1.W), m(22, 13))    // UInt(11.W)
      val magEff = Mux(isZero, 0.U(W.W), magNarrow)
      val eEff   = Mux(isZero, 0.U(8.W), e)
      (s, magEff, eEff)
    }

    val (s0, mag0, e0) = unpackLane(p0)
    val (s1, mag1, e1) = unpackLane(p1)
    val (s2, mag2, e2) = unpackLane(p2)
    val (s3, mag3, e3) = unpackLane(p3)

    // ---- 2. 求块内最大指数 Emax（2 级并行 max 树） ----
    val eMax01 = Mux(e0 >= e1, e0, e1)              // UInt(8.W)
    val eMax23 = Mux(e2 >= e3, e2, e3)              // UInt(8.W)
    val Emax   = Mux(eMax01 >= eMax23, eMax01, eMax23) // UInt(8.W)

    // ---- 3. 对齐右移（11-bit，3 级桶形移位器 for shift ∈ [0,7]） ----
    val shift0 = Emax - e0  // UInt(8.W)
    val shift1 = Emax - e1
    val shift2 = Emax - e2
    val shift3 = Emax - e3

    // shift >= 8 时优雅降级为归零（仅指数回绕/深度下溢可达，out-of-spec 安全行为）
    val A0 = Mux(shift0 >= (SBITS+1).U, 0.U(W.W), (mag0 >> shift0(2, 0))(W-1, 0))
    val A1 = Mux(shift1 >= (SBITS+1).U, 0.U(W.W), (mag1 >> shift1(2, 0))(W-1, 0))
    val A2 = Mux(shift2 >= (SBITS+1).U, 0.U(W.W), (mag2 >> shift2(2, 0))(W-1, 0))
    val A3 = Mux(shift3 >= (SBITS+1).U, 0.U(W.W), (mag3 >> shift3(2, 0))(W-1, 0))

    // ---- 4. 带符号定点值：zext 保证非负，SInt(12.W) ----
    val F0 = Mux(s0, -A0.zext, A0.zext)  // SInt(12.W)，范围 [-(2^11-1), 2^11-1]
    val F1 = Mux(s1, -A1.zext, A1.zext)
    val F2 = Mux(s2, -A2.zext, A2.zext)
    val F3 = Mux(s3, -A3.zext, A3.zext)

    // ---- 5. 显式 2 级平衡求和树（替代原线性三加法链，综合器映射为 CSA+CPA） ----
    // +& 扩位：12→13→14；|S| ≤ 4×(2^11-1) < 2^13
    val sum01 = F0 +& F1   // SInt(13.W)
    val sum23 = F2 +& F3   // SInt(13.W)
    val S     = sum01 +& sum23  // SInt(14.W)

    // ---- 6. 零检测 ----
    val sIsZero = (S === 0.S)

    // ---- 7. 取绝对值（S 非零时 |S| ∈ [1, 2^13-4]） ----
    val signV = S < 0.S
    val absS   = Mux(signV, -S, S).asUInt  // UInt(14.W)，bit13=0（非负 SInt）
    val absS13 = absS(12, 0)               // 13-bit 有效位（bit13 恒 0）

    // ---- 8. 前导 1 定位（LZC，13-bit 输入，4-bit 输出） ----
    val lzc = PriorityEncoder(Reverse(absS13))  // UInt(4.W)，∈ [0,12]
    val L   = 12.U(4.W) - lzc                   // 前导 1 位置，∈ [0,12]

    // ---- 9. 规格化尾数：左移使前导 1 到 bit12，取 [11:0] 为有效位 ----
    val normed = (absS13 << lzc)(12, 0)  // UInt(13.W)，前导 1 在 bit12
    // fp32 mant23 = {有效位(12), 结构性零(11)}：窄化后 bit[12:0] 以下全为 0
    val mantV = Cat(normed(11, 0), 0.U((23 - (W + 1)).W))  // UInt(23.W)

    // ---- 10. 指数计算：expV = L + Emax - 10 ----
    // 推导：窄化 absS_narrow = absS_wide >> 13，L_narrow = L_wide - 13
    //       expV = L_wide + Emax - 23 = (L_narrow + 13) + Emax - 23 = L_narrow + Emax - 10
    val expSum  = L +& Emax              // UInt(9.W)，∈ [0, 267]
    val expVRaw = expSum.zext - 10.S     // SInt(10.W)，∈ [-10, 257]

    // 钳位（fp4 典型区间 expVRaw ∈ [~100, ~160]；expUnder 侧已确证可达，见下方 S3 反例，不得删）
    // 极端下溢触发域：expUnder ⟺ L+Emax ≤ 10；由不变量 L ≥ 3 ⟹ 实际需 Emax ≤ 7
    // （⟺ scaleA+scaleB ≤ 136−O ⟹ 乘积真值 ≤ 2^(7−127) = 2^-120，压在 fp32 normal 下沿 2^-126 附近）。
    // 【勿与偏离域(b)「深度下溢【S2】」的 shift≥8 归零降级域混淆】—— 那里讲的是 shift≥8 归零降级，与此处触发域互斥。
    // 该域输出 ±0 而非 denormal。串行基线在此场景后续 fpNormalize40to32 的 eBase=Mux(e>lzc,e−lzc,0) 同样钳为 0，
    // 故【数值等价】（皆为 0）；但位模式可能差在符号位（基线 −0.0 / 并行 +0.0，因基线的 sOut 取自
    // cSign 而并行版走 fp4BlockSum 末尾输出 mux 的 0.U(32.W)）。此即头注『块内异号相消域/极端衰减域』段已声明的"≤1 ULP 偏离"，
    // 非 bit-exact 保证范围。
    // 【S3 死逻辑核对结论：expUnder 分支可达，不可删】
    //   可达条件：expVRaw = L + Emax − 10 < 1 ⟺ L + Emax ≤ 10。
    //   不变量：四 lane 的 magNarrow ∈ {1024,1152,1536}（三路 mProd 同构），且
    //     1152⟹offset≥3、1536⟹offset≥1、1024⟹offset≥0，配合 O≤7、shift=O−offset≤7
    //     ⟹ A_i = magNarrow>>shift 恒 ≡ 0 (mod 8) ⟹ 8|S ⟹ |S|≥8 ⟹ L ≥ 3。
    //   故 expUnder 需 Emax ≤ 7（即 scaleA+scaleB ≤ 136−O），靠块内异号相消压低 |S|。
    //   已验算反例（scaleA=64, scaleB=65 ⟹ B=0，落在头注声明域 scaleA+scaleB≥129 的下边界）：
    //     lane0/lane1 元素 = 4'b0000 → prod=0 → isZero → A=0, eEff=0
    //     lane2: a=isSub(e2=0,m1=1,s=0) / b=(e2=1,m1=1,s=1)
    //            → aExp=63,aSig=2; bExp=65,bSig=3; mProd=6<<44; eAdj=128
    //            → eOut=1, mant23=0x400000 → magNarrow=1536, prod=0x80C00000
    //     lane3: a=isSub / b=(e2=1,m1=0,s=0)
    //            → mProd=4<<44; eAdj=128 → eOut=1, mant23=0 → magNarrow=1024, prod=0x00800000
    //     Emax=1; shift=(1,1,0,0) 均<8; F=(0,0,−1536,+1024); S=−512 (≠0，不被 sIsZero 掩蔽)
    //     absS13=512=2^9 → lzc=3 → L=9; expSum=10 → expVRaw=0 < 1 → expUnder 触发 → 输出 32'h0
    //   若删此分支：expFinal=expVRaw.asUInt(7,0)，且 fp4BlockSum 末尾输出 mux 的 `|| expUnder` 掩蔽失效
    //     → 输出 Cat(signV, 垃圾指数, mantV) 而非 ±0，破坏 bit-exact（expVRaw<0 时
    //     低 8 位补码会翻成 ≥254 的大指数）。故保留（48 GE 换正确性，值）。
    val expUnder = expVRaw < 1.S
    val expOver  = expVRaw > 254.S
    val expFinal = Mux(expUnder, 0.U(8.W),
                   Mux(expOver, 254.U(8.W),
                     expVRaw.asUInt(7, 0)))

    // ---- 11. 组装 fp32 输出（规格化，前导 1 隐含） ----
    val vNormal = Cat(signV.asUInt, expFinal, mantV)  // 32-bit fp32
    // S=0 → +0；指数下溢 → +0（expUnder 可达，反例见上方 S3 结论；
    // 本掩蔽项 || expUnder 与 expUnder 分支必须同存同删）
    Mux(sIsZero || expUnder, 0.U(32.W), vNormal)
  }

  // =========================================================================
  // M1 实例合并：3 个 lazyAdd 实例 → 2 个（Mux 布尔恒等重组，三模式逐位相同）
  //   prodSel     = is4x ? blockV : prodFp16       // MX4→块和；fp16/MX8→r0
  //   firstStage  = lazyAdd(cReg, prodSel)          // fp16/MX8 模式 ≡ 旧 c0；MX4 模式 ≡ 旧 c4x
  //   secondStage = lazyAdd(firstStage, prodFp8)    // MX8 模式 ≡ 旧 c1
  //   sum         = (is2x && !is4x) ? secondStage : firstStage   // 优先级 is4x>is2x>else
  //
  // 三模式 bit-exact 逐一核对（prodSel/输出均为 Mux 精确选值，无算术改动）：
  //   fp16/bf16 (is4x=0,is2x=0)：prodSel=prodFp16=r0 → firstStage=lazyAdd(cReg,r0)≡旧 c0；
  //                               sum=Mux(false,·)=firstStage=c0 ✓
  //   MX8       (is4x=0,is2x=1)：prodSel=r0 → firstStage=c0；secondStage=lazyAdd(c0,r1)≡旧 c1；
  //                               sum=Mux(true,·)=secondStage=c1 ✓
  //   MX4       (is4x=1,is2x=0)：prodSel=blockV → firstStage=lazyAdd(cReg,blockV)≡旧 c4x；
  //                               sum=Mux(false,·)=firstStage=c4x ✓
  //   （is4x=1 时 is2x 必为 0：is4x 要求双侧 E2M1；即便 is2x=1，!is4x 仍使 sum=firstStage=c4x，
  //     精确保留 is4x>is2x 优先级）
  //   注：MX4 模式下 secondStage 仍被计算但被输出 mux 丢弃——死计算实例数由 2 降至 1
  //       （净功耗改善）；新增副作用仅为 firstStage 输出扇出 +1。
  //       这正是省 1 个 lazyAdd 实例（3→2）的关键；firstStage 在 MX4 模式复用作旧 c4x。
  // =========================================================================
  val blockV      = fp4BlockSum(io.prodFp16, io.prodFp8, io.prodFp4_0, io.prodFp4_1)
  val prodSel     = Mux(io.is4x, blockV, io.prodFp16)  // 32-bit：MX4→块和 V，其余→r0
  val firstStage  = lazyAdd(io.cReg, prodSel)          // ≡ 旧 c0（fp16/MX8）/ 旧 c4x（MX4）
  val secondStage = lazyAdd(firstStage, io.prodFp8)    // ≡ 旧 c1（仅 MX8 使用）
  io.sum := Mux(io.is2x && !io.is4x, secondStage, firstStage)

}
