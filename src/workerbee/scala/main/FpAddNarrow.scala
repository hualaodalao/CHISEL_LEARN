/** FpAddNarrow — MX-only 统一窄窗口五槽带符号 CSA 累加器（L2 方案 · task#16）
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 定位与边界（务必先读）
  * ══════════════════════════════════════════════════════════════════════════════
  * · 本文件是【新建独立模块】，与既有 `FpAdd.scala` 并存。FpAdd.scala 及其 bit-exact 测试
  *   【一行未改】，继续作为已验证对照基线与回退路径。
  * · I/O 契约与 FpAdd 【逐端口一致】（端口名/位宽/方向全同，task#16 已机器核对）⟹ **port-compatible**：
  *     is4x, is2x, cReg(cEffW), prodFp16(32), prodFp8(32), prodFp4_0(32), prodFp4_1(32), sum(cEffW)
  *   ★ 措辞订正（task#18 D 项）：原文写 "drop-in replaceable"，【降级为 "port-compatible"】。
  *     端口兼容 ≠ 语义等价：两者的数值契约不同（FpAdd = 逐位 bit-exact；本模块 = 三段式误差契约，
  *     见下方），饱和域行为也曾分歧（原钳 255 → fp32 NaN，task#18 A 项已修为钳 254 对齐 FpAdd）。
  *     只有在"A 项修复 + 接受误差契约"两个前提下才谈得上真正 drop-in。
  * · 接入方式（task#18 B 项；★task#28 起类默认已翻转为 true）：WbFusionMacFp 编译期参数 `useNarrowAdd: Boolean = true`
  *   —— **默认 true ⟹ 例化本模块（FpAddNarrow）**；须【显式 pin】false ⟹ 回退 FpAdd（已验证 bit-exact 基线）。
  *   护栏 `require(!useNarrowAdd || !hasFp)`：本模块只支持 MX-only（fp16-only 模式是 don't-care）。
  * · 适用域 = 【MX-only】融合 MAC（mxfp8 E4M3/E5M2 + mxfp4 E2M1，上游已去掉 fp16/bf16）。
  *   fp16/bf16 满宽乘积（24-bit 有效尾数）在窄窗口下会丢低 (24−W) 位 ⟹ 本模块【不为 fp16-only
  *   模式做精度承诺】（见下方「三模式与激活掩码」）。
  * · 精度契约由【逐位 bit-exact】改为【相对张量语义 exact 的误差阈值】（良态域默认 2^-18，对 MX 推理
  *   功能不可见：MX 元素尾数仅 2~3 bit）。★ 该阈值【非无条件】，完整三段式契约见下方「精度契约」。
  *   阈值判定与金锚见 test/FpAddNarrowSpec.scala。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * lazy-40 格式与 radix 约定（逐字继承 FpAdd.scala / Fp32.fpNormalize40to32）
  * ══════════════════════════════════════════════════════════════════════════════
  *   [39]    : sign
  *   [38:31] : exponent（8-bit，biased-127，【不规格化】）
  *   [30:0]  : mantissa（31-bit，允许 leading zeros —— 延迟规格化核心）
  *   真值    : value = (−1)^sign × mant31 × 2^(exp − 127 − 30) = mant31 × 2^(exp − 157)
  *   ⟹ radix：mant31 的 bit30 ↔ 2^(exp−127)（即「bit30 ↔ 2^exp」的 biased 表述）。
  *   真零判据：exp==0 && mant31==0（下游 fpNormalize40to32 的 isZero 分支只看这两域）。
  * fp32 乘积槽（prodFp16/prodFp8/prodFp4_*）：value = mag24 × 2^(exp − 150)，mag24 = Cat(1, mant23)。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * L2 架构（Alex task#15 蓝图）：统一五槽带符号 CSA 求和树
  * ══════════════════════════════════════════════════════════════════════════════
  *   五槽 = { C=cReg, L0=prodFp16, L1=prodFp8, L2=prodFp4_0, L3=prodFp4_1 }
  *   1) 解包：每槽 → (sign, expEff(9-bit), magW(W-bit 窗口幅值))；零槽/未激活槽 expEff=0 且 magW=0
  *      · 乘积槽：magW = mag24 >> (24−W)（常量右移，纯连线）⟹ 窗口 bit(W−1) ↔ 2^(exp−127)
  *      · cReg 槽：magW = mant31 >> (31−W)（同一 radix 对齐，天然吸收 leading zeros，无需 LZC）
  *   2) 公共指数 E = max(所有【激活且非零】槽的 expEff)（2 级并行 max 树，9-bit）
  *   3) 对齐：a_i = magW_i >> clamp(E − expEff_i, W)（W-bit 桶形移位，shift ≥ W ⟹ 整槽移出 = term-drop）
  *      ★ 窗外低位【直接丢弃】= L2 的唯一精度让步点（不做 sticky/round，省面积省深度）
  *   4) 带符号求和：F_i = sign_i ? −a_i : +a_i（SInt(W+1)），显式平衡树
  *        tA = F_C +& F_L0 ; tB = F_L1 +& F_L2 ; tC = tA +& tB ; S = tC +& F_L3
  *      |S| ≤ 5×(2^W−1) < 2^(W+3) ⟹ S 为 SInt(W+4)。
  *      ★【与蓝图字面表述的差异，task#16 实测记录】蓝图写"带符号 CSA(5→3→2) + CPA"，而 firtool 实际
  *        发射的 RTL 是上述【4 级平衡带符号加法树】（tA/tB 并行 → tC → S，位宽 21/21/22/23 @W=19），
  *        RTL 层【没有】显式 3:2 压缩器。两者功能等价（同一 5 操作数带符号求和），且平衡树深度为
  *        log2 而非线性；是否映射成 CSA 属【综合器的自由度】，不应在 RTL 层硬写。
  *   5) 输出（蓝图字面实现，含「溢出收回 k 档」）：
  *        sign    = (S < 0)；magFull = |S|（W+3 位有效）
  *        k       = max(0, bitLength(magFull) − W) ∈ [0,3]   —— 增长档（=「最小 LZC」，见下方 (D1)）
  *        magTop  = magFull >> k ∈ [0, 2^W)                   —— 溢出收回：右移 k 档
  *        mant31  = Cat(magTop, 0^(31−W))                     —— 高 W 位对齐，低 (31−W) 位结构性 0
  *        expOut  = clamp(E + k, 254)                         —— 指数 += k（10-bit 算完后饱和钳位，
  *                                                               上界 254 对齐 FpAdd，见 (E3)）
  *        magFull == 0 ⟹ 输出 40'h0（真零；否则 exp≠0 的「指数非零零」会漏判下游 isZero ⟹ 真 bug，
  *                                    与 FpAdd.scala:143-146 的精确零 mux 同理，必须保留）
  *      radix 自洽验证：mant31 bit30 = magTop bit(W−1) = magFull bit(W−1+k) ↔ 2^(E−126−W+W−1+k)
  *        = 2^(E+k−127) = 2^(expOut−127) ✓（lazy-40 约定 bit30 ↔ 2^(exp−127)）。
  *
  * ── (D1) 为何【必须】做 k 档右移，而不能用「常量 expOut = E+3 + magFull 全 31 位左对齐」──
  *   后者（记 D1'）看似更优：mant31 = magFull << (28−W) 保留全部 W+3 位、零截断、且省掉 k 的
  *   探测与 4:1 桶形移位。但它在【MAC 环路回灌】下是致命错误：
  *     D1' 恒有 expOut = E+3 ⟹ 下一拍 eC = E+3 > 任何 lane 的 eField ⟹ E' = E+3 ⟹
  *     E 每拍【无条件】+3（与真实增长无关）⟹ lane 对齐移位 sh = E−e_p 每拍 +3 ⟹
  *     约 W/3 拍后（W=19 ⟹ 6~7 拍）所有乘积 lane 被整体移出窗口 ⟹ 累加器【失忆】，
  *     且 expOut 约 38 拍后饱和到钳位上界（254）。这是【功能性错误】而非精度让步。
  *   根因：lazy-40 不做 LZC ⟹ expField 必须反映「尾数字段最高有效位的真实位置」。D1' 无条件
  *   预留 3 个增长位 = 无条件把 expField 抬高 3 ⟹ 预留位没被用上时就是纯膨胀。
  *   k 档右移正是「只按【实际】增长抬 expField」：k=0（|S| < 2^W，MX 稳态最常见）⟹ expOut = E
  *   ⟹ 环路 E 稳定不膨胀，且 cReg 再入 magW = mant31 >> (31−W) = magTop = magFull 【零损失】。
  *   代价：k ≥ 1 时丢掉 magFull 的低 k 位（≤ 2^k−1 窗口 LSB），已并入下方误差界。
  *   实现上 k 只探测 {W, W+1, W+2} 三个固定位 ⟹ 2-bit 结果、3 个常量比较器，
  *   【不是】(W+3)-bit PriorityEncoder（相消产生的低位前导零仍不规格化 ⟹ lazy 语义保持）。
  *
  * ── (D2) mant31 采用【高 W 位对齐】而非「低位零扩展」──
  *   蓝图原文「mant31 = mag 从 W 零扩展到 31 位」有两种读法：
  *     (i)  低位零扩展 mant31 = magTop（占 [W−1:0]，高位补 0）⟹ 需 expOut = E+k−(31−W) = E+k−12
  *          （W=19）⟹ E ≤ 138 时 expOut < 0 → 8-bit 域绕回成大指数，属【可观测灾难性错误】
  *          （in-spec MX 工作流 E 常在 120~200，绕回高发）。
  *     (ii) 高位对齐 mant31 = magTop << (31−W)（占 [30:31−W]，低位补 0）⟹ expOut = E+k，
  *          仅 E ≥ 252 时需钳位（与 FpAdd 的 baseExp+1 → 钳 254 同级），in-spec 不触发。
  *   本实现取 (ii)。两者同为「lazy 合法、无 LZC、允许 leading zeros」，(ii) 的绕界门槛高 115 档。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 精度契约（第一性原理推导，非拟合 RTL）——★ 三段式【可证明】契约（task#18 D 项订正）
  * ══════════════════════════════════════════════════════════════════════════════
  * 记窗口 LSB = 2^(E−127−(W−1)) = 2^(E−126−W)。两处截断：
  *   (a) 对齐为 floor ⟹ 每槽绝对误差 < 1 窗口 LSB；设 n_lossy = 实际丢失 ≥1 bit 的激活槽数。
  *   (b) 溢出收回 magTop = magFull >> k ⟹ 绝对误差 = (magFull mod 2^k) 窗口 LSB ≤ 2^k−1（k=0 时为 0）。
  * 两者同向（都是「丢幅值」）。
  *
  * ┌─ ① 绝对界（【无条件恒成立】，对任意输入、任意 W、含相消域）───────────────────
  * │    |diff| ≤ (n_lossy + 2^k − 1) × 2^(E−126−W)
  * └─ 这是本模块唯一【无条件】的精度承诺，也是测试台 J3 判据。它随 E 与 W 缩放：E 由输入决定，
  *    W 是唯一设计旋钮 ⟹ 加大 W 直接按比例收紧绝对界。
  *
  * ┌─ ② 相对界（【条件成立】：仅良态域）────────────────────────────────────────
  * │    relErr ≤ 2^−(W−1)     当且仅当充分条件  |exact| ≥ (n_lossy + 2^k − 1) × 2^(W−1) × 窗口LSB
  * └─ 推导：relErr = |diff| / |exact| ≤ (n_lossy+2^k−1)·窗口LSB / |exact|，代入上式即得 2^−(W−1)。
  *    即"绝对界 ÷ 良态门槛"。测试台 A3 强断言用的正是此充分条件（写作整数形式
  *    (n_lossy + 2^k−1) × 2^(W−1) ≤ magFull − n_lossy）。
  *    W=19 ⟹ 2^−18（用户裁定的默认阈值）；W=16 ⟹ 2^−15；W=22 ⟹ 2^−21。
  *
  * ┌─ ③ 相消域（【任意有限 W 都无相对界】）─────────────────────────────────────
  * │    |exact| < 若干窗口 LSB 时，relErr = |diff|/|exact| 可任意大（|exact| → 0 时 → ∞）
  * └─ ★ 这是【数学事实】而非实现缺陷：任何有限精度加法器在 catastrophic cancellation 下都如此，
  *    FpAdd 亦然（只是其 31-bit 窗口把门槛推得更低）。**加大 W 不能消除它**，只能把门槛下移。
  *    ⟹ 该域必须由 ① 绝对界兜底（测试台 J3），不得声称相对界。
  *
  * ★【订正记录 · task#18 D 项】task#16 的报告曾表述为「若要无条件严格 2^−18，应取 W=21/22」。
  *   该表述【错误】，已被 Terry(task#17) 的独立随机负载测量推翻：
  *     · W=19：随机 MX 负载 97.33% 的向量 ≤ 2^−18
  *     · W=22：99.33%（**仍非无条件** —— 尚有 143 条 in-spec 向量超阈，最差 2^−5）
  *   根因即 ③：超阈向量集中在相消域与 n_lossy ≥ 2 的多损域，**任何有限 W 都无法给出无条件相对界**。
  *   正确表述只能是「① 无条件绝对界 + ② 良态域相对界 + ③ 相消域由绝对界兜底」。
  *   （上述百分比/条数为 Terry 的独立测量结果，非本模块 spec 的实测；本 spec 的对应实测见其报告段。）
  *
  * n_lossy = 0 的充分条件（MX 稳态工作域，逐槽论证）：
  *   · mxfp8 乘积：FpMulMx8 的 mProd=(aSig·bSig)<<40、aSig/bSig ≤ 4-bit ⟹ mant23 有效位仅 [22:16]，
  *     [15:0] 为结构性 0 ⟹ 窗口内有效位仅 [W−1 : W−8]，下方 W−8 位结构性 0 ⟹ 对齐 shift ≤ W−8 无损。
  *     （W=19 ⟹ shift ≤ 11 无损；MX8 双 lane 共享 scaleA/scaleB ⟹ 槽间指数差 ≤ ~16，越界即 term-drop。）
  *   · mxfp4 乘积：FpMulMx4 的 P=(2+m1ₐ)(2+m1_b) ∈ {4,6,9} ⟹ mag24 = P<<21（P=4/6）或 P<<20（P=9）
  *     ⟹ mant23 ∈ {0x000000, 0x400000, 0x100000}，有效位仅 {23,22,20} ⟹ 窗口下方 W−4 位结构性 0
  *     ⟹ shift ≤ W−4 无损（W=19 ⟹ ≤15；W=16 ⟹ ≤12）；同 block 四 lane 指数差 ≤ 7 ≪ W ⟹ 恒无损。
  *     ★ 这正是「取消 fp4BlockSum 中间 LZC」仍无损的依据：块内和用整数算术精确表达（≤ 4+7=11 bit
  *       + 2 增长位 = 13 bit ≤ W+3），无需先规格化再并入。
  *   · cReg：mant31 的有效位若全落在 [30 : 31−W] 内 ⟹ 提取无损。三类来源逐一论证：
  *       (a) 本模块【自产】回灌：mant31 = magTop << (31−W) ⟹ 低 (31−W) 位结构性 0 ⟹ 再入
  *           magW = mant31 >> (31−W) = magTop 【完全无损】，且 expOut = E+k ⟹ k=0 时 E 不膨胀。
  *           ★ 这是 (D1) k 档右移相对 D1' 的第二处收益：D1' 下自产 cReg 恒丢低 3 位（每拍 n_lossy=1）
  *             且 E 每拍 +3 ⟹ 环路既掉精度又失忆。
  *       (b) 由 fp32BitsTo40 装入的 MX-可表示值：mant31 = Cat(1,mant23,0^7)，提取丢 mant23[4:0]；
  *           而 MX8 乘积 mant23[15:0]=0、MX4 乘积 mant23[19:0]=0 ⟹ 丢的 5 位恒 0 ⟹ 【无损】。
  *       (c) 由 fp32BitsTo40 装入的【满精度】fp32 值（mant23[4:0] ≠ 0，如外部初值/上位机写入）：
  *           n_lossy = 1 ⟹ 良态域内 relErr < 2^−18（达 ② 的相对界；相消域则退化为 ① 绝对界）。
  *           此为 L2 的既有让步，逐拍【不放大】：
  *           误差量级恒为「当拍窗口 LSB」，而窗口 LSB 与当拍最大项同阶（≲ 2^−(W−1) 相对），
  *           故 K 拍累积的绝对误差 ≲ K × 最大项 × 2^−(W−1)（与 FpAdd 的 K×2^−30 同族，只是常数大 2^12）。
  *
  * 相消域（catastrophic cancellation）：见上方契约 ③ —— |T| ≪ 2^(W−1) 窗口 LSB 时相对误差被条件数
  *   放大，【任意有限 W 都无法给出相对界】，只能由 ① 绝对界兜底（测试台 J3 判据）。
  *   本模块的绝对误差仍 ≤ (n_lossy + 2^k − 1) × 窗口 LSB。
  *   完全相消（S==0）⟹ 输出 40'h0（+0），与 FpAdd 的 ±0 → +0 归一化后语义一致。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 三模式与激活掩码（优先级 is4x > is2x，逐字对齐 FpAdd.scala:366）
  * ══════════════════════════════════════════════════════════════════════════════
  *   MX4 (is4x=1)          ：激活 {C, L0, L1, L2, L3} = 5 操作数（4 个 fp4 积【直接进树】，
  *                           取消 fp4BlockSum 的中间 LZC 规格化）。is4x=1 时 L1 亦有效
  *                           （上游 WbFusionMacFp 的 r1 = Mux(aIsMx2M1, fp8Mul.prod, 0)）。
  *   MX8 (is2x=1 && !is4x) ：激活 {C, L0, L1} = 3 操作数，L2/L3 掩零（掩零必须同时清 expEff，
  *                           否则陈旧/无关的 prodFp4_* 指数会抬高 E ⟹ 真实槽被整体下移 ⟹ 精度崩塌）。
  *   fp16-only (is2x=0 && is4x=0)：MX-only 下【不存在】⟹ don't-care。本实现退化为 {C, L0} 两操作数
  *                           （语义 = cReg + prodFp16，与 FpAdd 的 firstStage 同义但【非 bit-exact】：
  *                           满宽 fp16/bf16 乘积尾数 24 bit > W ⟹ 丢低 (24−W) 位）。
  *                           代码保留此退化支路仅为避免误接线时产生 X/垃圾，不构成精度承诺。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * out-of-spec 域（与 FpAdd 同族，均【不】做数值承诺；行为为优雅降级而非 X 传播）
  * ══════════════════════════════════════════════════════════════════════════════
  *   (E1) fp32 subnormal 乘积（exp==0 && mant!=0）：与 FpAdd 一致【不】特判 ⟹ expEff=0 参与 max，
  *        对齐 shift = E − expEff = E。★【前提细分 · task#25 Kim Nit#6，消除与本 spec OOS1 的表面矛盾】
  *        shift=E 的后果取决于 E 与 W 的大小关系，不能一概写成「clamp ⟹ term-drop」：
  *          · E ≥ W：clamp 到 W ⟹ 整槽 term-drop（FpAdd 侧同样 clamp 到 31 档后基本丢光）；
  *          · E < W（极端：该 subnormal 槽自身即 E 的来源，甚至 E=0）：移位量不足 ⟹ 仅部分/完全透过窗口，
  *            可产出 expOut=0 && mant31≠0 的【非规格 lazy-40 码】——这与 FpAdd 在同域（全 subnormal / E<W）
  *            的行为【同族一致】，属 out-of-spec 优雅降级而非 bug（实测载体见 FpAddNarrowSpec 的 OOS1）。
  *   (E2) cReg 的 exp==0 && mant31!=0：违反 lazy-40 不变量（FpAdd.scala:138-141 已判为不可达态），
  *        本模块按 expEff=0 处理 ⟹ 对齐 shift = E，同样按上方 (E1) 的 E≥W / E<W 两分支降级
  *        （E≥W ⟹ term-drop；E<W ⟹ 可产出 expOut=0 && mant31≠0 非规格码，与 FpAdd 同域同行为）。
  *   (E3) 指数域钳位：expOut = E+k（k ≤ 3）用 10-bit 计算后【饱和钳位到 254】（而非 8-bit 绕回，
  *        也【不是】钳到 255）。钳位上界 254 逐字对齐 FpAdd.scala:331-334：
  *          val expOver  = expVRaw > 254.S
  *          val expFinal = Mux(expUnder, 0, Mux(expOver, 254.U(8.W), expVRaw.asUInt(7,0)))
  *        ★【task#18 A 项修复】本模块原先钳到 255，是一个真实 bug（Terry task#17 定位）：
  *          fp32 的 eField==255 是 Inf/NaN 保留域。饱和域（FpAdd.scala §9.1(c)）下乘积槽会溢出成
  *          Inf(eField=0xFF, mant23=0)，而本模块与 FpAdd 一样【只判零、不判 Inf】⟹ Inf 被当成
  *          "指数很大的有限数"吸收 ⟹ E=255 ⟹ expOut=255 且 mant31≠0 ⟹ 下游 fpNormalize40to32
  *          按 (exp==255 && mant!=0) 归一化出 fp32 **NaN**（可观测故障：WbFusionMacFpSpec 的
  *          饱和域 "output finite (no NaN/X)" 断言直接 FAIL）。钳到 254 后输出恒为有限值。
  *        ★【是否需要对乘积槽显式处理 Inf/NaN？——不需要，以 FpAdd 语义为准】
  *          FpAdd 同样不特判 Inf/NaN 槽（它把 eField=255 当大指数走完整条 lazyAdd），其"优雅降级"
  *          完全靠 expFinal 钳到 254 实现。故本模块只需同一行钳位即达成语义对齐；再加 Inf 检测/
  *          传播逻辑属过度设计（且会引入与 FpAdd 的新分歧，破坏 A/B 对照的可比性）。
  *        触发域 E ≥ 252 ⟺ 乘积/累加器真值 ≥ 2^125（in-spec MX 工作流 scaleA+scaleB ≤ 358 时
  *        eOut ≤ ~247，不触发 ⟹ 钳位只在 out-of-spec 饱和域起作用）。钳位后下游给出 fp32 最大
  *        有限数量级的值，与 FpAdd 的 baseExp+1 → 254 同族降级；【优于】绕回成小指数（灾难性错误）。
  *   (E4) lane0 饱和域（FpAdd.scala §9.1(c) 记录的 (c) 域）：元素饱和后 mant23 可能填满 23 bit
  *        （默认配置宽阵列 fp32Max ⟹ mant23=0x7FFFFF）⟹ 窄窗口丢低 (24−W) 位。该域元素真值
  *        已 ≥ 2^126 且 a 侧已饱和失真（相对误差 ~33%），额外 2^−(W−1) 损失被完全淹没。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 参数化
  * ══════════════════════════════════════════════════════════════════════════════
  *   W（对齐窗口宽度，编译期 Int，默认 19）：唯一精度/面积旋钮，后续可扫 W ∈ {16,19,22}。
  *     · 硬下界 8：mxfp8 乘积有效位 8 bit（含隐含 1）⟹ W < 8 连【零移位】的 MX8 乘积都要截断。
  *     · 硬上界 28：mant31 = Cat(magTop(W 位), 0^(31−W)) 要求 31−W ≥ 0（W ≤ 31）；取 28 为设计上限，
  *       留 3 位余量（W > 28 时 cReg 再入的常量右移 CSh = 31−W < 3，与自产 mant31 的低位零区重叠变小）。
  *     · W=19 ⟹ 良态域相对阈值 2^−18（= 2^−(W−1)）与 n_lossy ≤ 1 恰好匹配，故取为【默认】。
  *       ★ 但据契约 ③，这【不是无条件】的：加大 W 只按比例收紧 ① 绝对界、并下移 ② 的良态门槛，
  *         **无法消除相消域的相对误差**。Terry(task#17) 独立测量：W=19 随机 MX 负载 97.33% ≤ 2^−18，
  *         W=22 为 99.33%（仍有 143 条 in-spec 超阈，最差 2^−5）⟹ 不存在"取某个 W 就无条件达标"。
  *         故 W 默认保持 19（用户 task#18 裁定），参数化保留以便按面积/精度权衡调档。
  *   cEffW：与 FpAdd 同义（≥40，低 40 bit 为 lazy-40，高位零填充）。
  */

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters._

object FpAddNarrow {
  /** 默认窄对齐窗口宽度（位）。配套相对误差阈值 = 2^−(DefaultW−1) = 2^−18。 */
  val DefaultW: Int = 19
  /** 默认相对误差阈值（= 2^−(W−1)）；测试台以此为可配置常量基准（见 FpAddNarrowSpec）。 */
  val DefaultRelErrShift: Int = 18
  /** lazy-40 指数域的饱和钳位上界 = 254（【不是】255）。
    * 单一真值源：模块输出级与 FpAddNarrowSpec 的规格模型/守卫都引用此常量，避免两侧各写裸 254 而漂移。
    * 与 FpAdd.scala:331-334 的 `expOver = expVRaw > 254 → Mux(..., 254.U(8.W), ...)` 逐字对齐：
    * 255 是 fp32 的 Inf/NaN 保留指数域，累加器输出必须恒为有限值（见头注 (E3)）。 */
  val ExpSatMax: Int = 254
}

class FpAddNarrow(cEffW: Int = 40, W: Int = FpAddNarrow.DefaultW) extends Module {
  require(cEffW >= 40, s"FpAddNarrow: 延迟规格化需要 cEffW($cEffW) >= 40")
  require(W >= 8, s"FpAddNarrow: W($W) < 8 会截断 mxfp8 乘积的 8 位有效尾数（硬下界）")
  require(W <= 28, s"FpAddNarrow: W($W) > 28 超出设计上限（mant31 高 W 位对齐仅要求 W <= 31，取 28 留余量）")

  // ---- 编译期派生常量（全部为 Scala 层，不生成运行期选择子）----
  private val MagW      = W + 3            // |S| 有效位宽：|S| ≤ 5×(2^W−1) < 2^(W+3)
  private val LowSh     = 31 - W           // magTop 左对齐入 mant31 的档数（mant31 = Cat(magTop, 0^LowSh)）
  private val ShiftBits = log2Up(W + 1)    // 对齐移位量 0..W 的位宽（W∈{16,19,22} 皆为 5）
  private val CSh       = LowSh            // ★ 同一常量：输出左移档 == cReg 再入右移档 ⟹ 自产 cReg 零损失
  require((1 << ShiftBits) > W, s"FpAddNarrow: ShiftBits($ShiftBits) 装不下 W($W)")

  // ==========================================================================
  // I/O：与 FpAdd 逐端口一致（port-compatible；语义差异见头注「定位与边界」）
  // ==========================================================================
  val io = IO(new Bundle {
    val is4x = Input(Bool())
    val is2x = Input(Bool())
    val cReg      = Input(UInt(cEffW.W))   // 当前累加器值（lazy-40: sign(1)|exp(8)|mant(31)）
    val prodFp16  = Input(UInt(32.W))      // lane0 乘积槽（fp32）
    val prodFp8   = Input(UInt(32.W))      // lane1：fp8 窄化乘法器输出（MX4 模式下为 nibble1 积）
    val prodFp4_0 = Input(UInt(32.W))      // lane2：fp4 窄化乘法器 #0 输出
    val prodFp4_1 = Input(UInt(32.W))      // lane3：fp4 窄化乘法器 #1 输出
    val sum       = Output(UInt(cEffW.W))  // 累加结果（lazy-40）
  })

  // ==========================================================================
  // 槽解包 → (sign, expEff(9-bit), magW(W-bit))
  //   expEff = 0 且 magW = 0 ⟺ 该槽不参与（零槽 / 未激活槽）——掩零必须同时清指数，
  //   否则无关槽的陈旧指数会抬高公共指数 E ⟹ 真实槽整体下移 ⟹ 精度崩塌（见头注 MX8 段）。
  // ==========================================================================
  private def unpackProd(p: UInt, active: Bool): (Bool, UInt, UInt) = {
    val s = p(31)
    val e = p(30, 23)
    val m = p(22, 0)
    val isZero = (e === 0.U) && (m === 0.U)   // 与 FpAdd/FpMul 同判据：不特判 subnormal（见 (E1)）
    val valid  = active && !isZero
    val mag24  = Cat(1.U(1.W), m)             // 隐含 1 在 bit23
    // 常量移位（纯连线）：窗口 bit(W−1) ↔ 2^(exp−127)
    val magW   = if (W >= 24) mag24 << (W - 24) else mag24 >> (24 - W)
    val eEff   = Mux(valid, Cat(0.U(1.W), e), 0.U(9.W))
    // AND-mask 归零（对齐 FpAdd P5 手法：省一个 W-bit 2:1 mux）
    val mEff   = magW & Fill(W, valid)
    (s, eEff, mEff)
  }

  private def unpackC(cIn: UInt): (Bool, UInt, UInt) = {
    val cRaw  = cIn(39, 0)
    val s     = cRaw(39)
    val e     = cRaw(38, 31)
    val m31   = cRaw(30, 0)                   // 可能带 leading zeros（lazy 语义，无需 LZC）
    val isZero = (e === 0.U) && (m31 === 0.U)
    val magW   = m31 >> CSh                   // 同一 radix（bit30 ↔ 2^(exp−127)）对齐到 W 窗口
    val eEff   = Mux(isZero, 0.U(9.W), Cat(0.U(1.W), e))
    val mEff   = magW & Fill(W, ~isZero)
    (s, eEff, mEff)
  }

  // ---- 激活掩码（三模式；优先级 is4x > is2x，见头注）----
  private val actL1 = io.is2x || io.is4x      // MX8 与 MX4 两模式 lane1 均供数
  private val actL2 = io.is4x                 // 仅 MX4
  private val actL3 = io.is4x                 // 仅 MX4

  private val (sC, eC, mC) = unpackC(io.cReg)
  private val (s0, e0, m0) = unpackProd(io.prodFp16,  true.B)   // L0 恒激活
  private val (s1, e1, m1) = unpackProd(io.prodFp8,   actL1)
  private val (s2, e2, m2) = unpackProd(io.prodFp4_0, actL2)
  private val (s3, e3, m3) = unpackProd(io.prodFp4_1, actL3)

  // ==========================================================================
  // 公共指数 E = max(激活且非零槽的 expEff)（2 级并行 max 树，9-bit 无符号）
  // ==========================================================================
  private val eMaxC0 = Mux(eC >= e0, eC, e0)
  private val eMax12 = Mux(e1 >= e2, e1, e2)
  private val eMaxA  = Mux(eMaxC0 >= eMax12, eMaxC0, eMax12)
  private val E      = Mux(eMaxA >= e3, eMaxA, e3)              // UInt(9.W)

  // ==========================================================================
  // 窄窗口对齐：a_i = magW_i >> clamp(E − expEff_i, W)
  //   shift ≥ W ⟹ 整槽移出窗口（term-drop）——L2 精度让步点；Chisel 动态 >> 保持位宽，
  //   移位量 ≥ 位宽时 Verilog 语义给出 0，故无需额外归零 mux。
  // ==========================================================================
  private def alignWin(magW: UInt, eEff: UInt): UInt = {
    val sRaw   = E - eEff                                        // UInt(9.W)，E 为 max ⟹ 非负
    val sClamp = Mux(sRaw > W.U, W.U(ShiftBits.W), sRaw(ShiftBits - 1, 0))
    magW >> sClamp                                               // UInt(W.W)
  }

  private val aC = alignWin(mC, eC)
  private val a0 = alignWin(m0, e0)
  private val a1 = alignWin(m1, e1)
  private val a2 = alignWin(m2, e2)
  private val a3 = alignWin(m3, e3)

  // ==========================================================================
  // 带符号 CSA 求和树（显式平衡，5 → 3 → 2 → CPA）
  //   F_i ∈ [−(2^W−1), 2^W−1] ⟹ SInt(W+1)；+& 逐层扩位 W+2 → W+3 → W+4
  //   |S| ≤ 5×(2^W−1) < 2^(W+3) ⟹ SInt(W+4) 无溢出
  // ==========================================================================
  private def signedWin(a: UInt, s: Bool): SInt = Mux(s, -a.zext, a.zext)   // SInt(W+1)

  private val fC = signedWin(aC, sC)
  private val f0 = signedWin(a0, s0)
  private val f1 = signedWin(a1, s1)
  private val f2 = signedWin(a2, s2)
  private val f3 = signedWin(a3, s3)

  private val tA = fC +& f0        // SInt(W+2)
  private val tB = f1 +& f2        // SInt(W+2)
  private val tC = tA +& tB        // SInt(W+3)
  private val S  = tC +& f3        // SInt(W+4)

  // ==========================================================================
  // 输出组装：lazy-40 = {sign, expOut, mant31}（蓝图「溢出收回 k 档」字面实现，见 (D1)）
  //   · sign    = S < 0
  //   · magFull = |S|（取低 MagW 位；bit(W+3) 结构性 0）
  //   · kGrow   = max(0, bitLength(magFull) − W) ∈ [0,3]：只探测 {W,W+1,W+2} 三个固定位
  //               ⟹ 3 个常量比较器 + 2-bit 结果，【不是】(W+3)-bit PriorityEncoder
  //   · magTop  = magFull >> kGrow ∈ [0, 2^W)
  //   · mant31  = Cat(magTop, 0^LowSh) —— 高 W 位对齐；低 LowSh 位结构性 0 ⟹ 自产 cReg 再入零损失
  //   · expOut  = clamp(E + kGrow, 254) —— 10-bit 计算后饱和钳位（见 (E3)，绝不 8-bit 绕回）
  //     ★ 钳位上界是 254 而非 255，逐字对齐 FpAdd.scala:331-334（expOver = expVRaw > 254 → 254）。
  //       理由：fp32 的 eField==255 是 Inf/NaN 保留域。若钳到 255，饱和域乘积槽溢出成
  //       Inf(eField=0xFF) 时本模块【只判零不判 Inf】、把 Inf 当"很大的有限指数"吸收
  //       ⟹ expOut=255 且 mant31≠0 ⟹ 下游 fpNormalize40to32 归一化成 fp32 NaN（可观测故障）。
  //       钳到 254 则输出恒为有限值，与 FpAdd 的饱和降级语义一致（task#18 A 项修复）。
  //   · magFull == 0 ⟹ 40'h0 真零（完全相消；下游 isZero 判据依赖 exp==0 && mant31==0）
  // ==========================================================================
  private val signOut  = S < 0.S
  private val magFull  = Mux(signOut, -S, S).asUInt(MagW - 1, 0)   // UInt(MagW.W)
  private val kGrow    = Mux(magFull(MagW - 1), 3.U(2.W),
                     Mux(magFull(MagW - 2), 2.U(2.W),
                     Mux(magFull(MagW - 3), 1.U(2.W), 0.U(2.W))))
  private val magTop   = (magFull >> kGrow)(W - 1, 0)              // UInt(W.W)
  private val mant31   = Cat(magTop, 0.U(LowSh.W))                 // UInt(31.W)
  private val expRaw   = E +& kGrow                                // UInt(10.W)：≤ 255+3 = 258
  private val expOut   = Mux(expRaw > FpAddNarrow.ExpSatMax.U,
                             FpAddNarrow.ExpSatMax.U(8.W), expRaw(7, 0))
  private val sumZero  = magFull === 0.U
  private val result40 = Mux(sumZero, 0.U(40.W), Cat(signOut.asUInt, expOut, mant31))

  io.sum := (if (cEffW == 40) result40 else Cat(0.U((cEffW - 40).W), result40))
}

/** FpAddNarrow RTL 发射入口（对齐 WbFusionMacFpElaborate 约定：withModulePrefix("Wb") +
  * 每次发射前清空目标目录）。
  *
  * 三档 W 各发一个独立目录：Chisel 不把构造参数附到模块名，三档顶层都叫 Wb_FpAddNarrow，
  * 发同一目录会互相覆盖（R10 同理）。目录用途：
  *   · src/workerbee/verilog_narrow      —— W=19（默认，task#16 交付结构核对基准）
  *   · src/workerbee/verilog_narrow_w16  —— W=16（精度下探 / 面积下界）
  *   · src/workerbee/verilog_narrow_w22  —— W=22（精度上探 / 面积上界）
  * A/B 面积-深度对照的基线 = src/workerbee/verilog_fusionfp_nofp16/Wb_FpAdd.sv（既有已验证版本）。
  *
  * ★ 本入口只发【独立模块】三档。系统级（WbFusionMacFp 内例化）的 A/B/C 三配置对照由
  *   WbFusionMacFpElaborate 负责（task#18 C 项）：
  *     · verilog_fusionfp/          默认（useNarrowAdd=false ⟹ Wb_FpAdd.sv）
  *     · verilog_fusionfp_nofp16/   hasFp=false（useNarrowAdd=false ⟹ Wb_FpAdd.sv）
  *     · verilog_fusionfp_narrow/   hasFp=false + useNarrowAdd=true ⟹ Wb_FpAddNarrow.sv
  *
  * ★【task#20(1) 重发射纪要】三档目录曾滞留 task#18 A 项修复【之前】的陈旧产物：钳位写成
  *   `(|(expRaw[9:8])) ? 8'hFF`（= 255，fp32 的 Inf/NaN 保留域 ⟹ 下游归一化成 NaN），
  *   与当前源 :360-361 的 `expRaw > ExpSatMax(254) ? 254 : expRaw(7,0)` 不一致。
  *   已用当前源重发射，三档 Wb_FpAddNarrow.sv 现均为 `expRaw > 10'hFE ? 8'hFE`（254 钳位 ✓）。
  *   · 一致性证据：verilog_narrow/ 与 verilog_fusionfp_narrow/ 的 Wb_FpAddNarrow.sv 做
  *     locator-normalized diff（剥 firtool 源码定位注释 + 压空白）后【仅差 clock/reset 端口声明】
  *     （独立顶层显式带 clock/reset，内嵌实例从父模块继承）⟹ 功能体逐字同硬件。
  *   · 【保留 w16/w22、不删除】的理由：(a) `.task17_verify/run.py:25` 的 DIRS 映射仍引用全部三档
  *     （Terry 的独立验证 harness），删目录会直接打断它；(b) FpAddNarrowSpec.WsToTest(:187)
  *     = Seq(19,16,22) 仍在仿真层扫三档 W，sweep 并未失效；(c) 本文件头注 :193-201 仍把 W
  *     文档为活的面积/精度调档旋钮。删除属破坏性动作且超出 task#20 范围，故取非破坏方案。
  *   · 本纪要所在的注释块位于 :367 之后，而三档 .sv 的 locator 只引用 ≤:353 ⟹ 增删本块不使
  *     已发射 RTL 的定位注释失效（无需为改注释而重发射）。
  */
object FpAddNarrowElaborate extends App {
  private def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        Files.list(path).iterator().asScala.foreach(deleteRecursively)
      }
      Files.delete(path)
    }
  }
  private def emitClean(outputDir: String): Unit = {
    val outDirPath = Path.of(outputDir)
    Files.createDirectories(outDirPath)
    Files.list(outDirPath).iterator().asScala.foreach(deleteRecursively)
    println(s"[FpAddNarrowElaborate] Cleaned output directory: $outputDir")
  }
  private def emit(w: Int, dir: String): Unit = {
    emitClean(dir)
    ChiselStage.emitSystemVerilogFile(
      gen = withModulePrefix("Wb") { new FpAddNarrow(40, w) }
      , args = Array("--target-dir", dir)
    )
    println(s"[FpAddNarrowElaborate] W=$w -> $dir")
  }

  emit(FpAddNarrow.DefaultW, "src/workerbee/verilog_narrow")     // W=19 默认
  emit(16, "src/workerbee/verilog_narrow_w16")
  emit(22, "src/workerbee/verilog_narrow_w22")
}
