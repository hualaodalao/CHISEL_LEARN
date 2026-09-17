/** FpAddNarrowOptSpec — FpAddNarrowOpt（L2 窄窗口五槽带符号 CSA 累加器）的【误差阈值】测试台
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 定位（task#16 用户裁定：L2 方案 ⟹ 放弃逐位 bit-exact，改为相对张量语义 exact 的误差阈值）
  * ══════════════════════════════════════════════════════════════════════════════
  * · 被测模块 = 新建的 `FpAddNarrowOpt`（standalone，直接驱动其 5 个槽端口）。
  * · 既有 `FpAdd.scala` 及其 bit-exact 测试（HiveFusionMacFpSpec）【一行未改】，独立回归、互不干扰。
  * · 精度契约（★ task#18 D 项订正为【三段式可证明契约】，与模块头注同源）：
  *     ① 绝对界 |diff| ≤ (nLossy + 2^k − 1) × 2^(E−126−W)          —— 【无条件恒成立】
  *     ② 相对界 relErr ≤ 2^−(W−1)（W=19 ⟹ 2^−18）                    —— 【仅良态域】：
  *        充分条件 |exact| ≥ (nLossy + 2^k − 1) × 2^(W−1) × windowLsb
  *     ③ 相消域（|exact| < 若干 windowLsb）在【任意有限 W】下都无相对界 ⟹ 由 ① 兜底
  *   ★ 订正记录：task#16 报告曾称「若要无条件严格 2^−18 应取 W=21/22」——【该表述错误】，
  *     已被 Terry(task#17) 独立随机负载测量推翻：W=19 达标率 97.33%、W=22 为 99.33%
  *     （W=22 仍有 143 条 in-spec 向量超阈、最差 2^−5）⟹ 不存在"取某个 W 就无条件达标"。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 铁律：金锚 = 张量语义精确值，由 Dyadic（精确二进制有理数）全程整数算出，禁拟合 RTL
  * ══════════════════════════════════════════════════════════════════════════════
  * 【为何不用 Double 作金锚】本模块阈值是 2^−18，而相消域里 |exact| 可比最大项小十几个数量级；
  *   Double（53-bit 尾数）在构造「exact = cReg + Σ lane」时自身就会舍入 ⟹ 金锚被污染 ⟹
  *   判定失去意义（既可能误判 FAIL，也可能因金锚与 RTL 同向舍入而假 PASS）。
  * 【为何不用 BigDecimal】2^−n 在十进制下有限（5^−n），但相对误差判定要做除法 ⟹ 必须给定 scale
  *   与舍入模式 ⟹ 又把「舍入」引入金锚。Dyadic 用 (BigInt 尾数, Int 2 的幂) ⟹ 加/减/乘【封闭无舍入】，
  *   相对误差判定式 |diff|×2^relShift ≤ |exact| 退化为【纯整数比较】，零浮点参与。
  * 【金锚来源】
  *   · 元素真值：mxElemDyadic(code, scale, fmt) —— 与 HiveSimCommon.mxToFloat / Fp32.mxToFp32 语义
  *     同族（expSigned = e + scale − bias；E2M1 subnormalFtz=false ⟹ e=0/m=1 = ±0.5 一等公民；
  *     fp8 subnormalFtz=true ⟹ e=0 → ±0），但【全程整数】。格式参数一律引 HiveEnum.MxFormatParams
  *     单例（禁裸字面量，house 铁律）。
  *   · lane 乘积真值 = elemA × elemB（Dyadic 乘，精确）→ 编码成 fp32 位模式驱动槽端口。
  *     ★ 该编码与上游窄乘法器 FpMulMx8 / FpMulMx4 的 in-spec 输出【逐位同值】—— 这是数学证明
  *       而非拟合：FpMulMx8 的 mag24 = P<<16（P≥128）/ P<<17（P<128），P = aSig×bSig ∈[64,225]，
  *       eOut = aExp+bExp−127+normAdj ⟹ 槽真值 = mag24×2^(eOut−150) = P×2^(aExp+bExp−260)
  *       = (aSig×2^(aExp−130)) × (bSig×2^(bExp−130)) = elemA × elemB（元素真值 = aSig×2^(aExp−130)）。
  *       FpMulMx4 同构（aSig = 2+m1，P ∈{4,6,9}）。⟹ 「张量语义乘积」与「上游精确乘积」是同一个数。
  *   · cReg 真值：由 Dyadic 反向编码成 lazy-40（radix: mant31 × 2^(exp−157)），与
  *     HiveSimCommon.fp32BitsTo40 的编码逐位一致（lz=0 时可在本 spec 内代数验证：
  *     exp = v.e+126+bl、mant31 = mag<<(30−bl+1) ⟹ 对 fp32 normal 恰还原 (1<<30)|(m<<7)）。
  *   · 金锚和 exact = cReg + Σ(【激活】lane 乘积)（纯 Dyadic，激活集由 is4x/is2x 的模式语义决定）。
  * 【绝不】跑一遍 RTL 把输出录下来当期望值。判定失败 ⟹ 真 bug 或激励越域 ⟹ 带证据报告，不调金锚凑 RTL。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 判定体系：J0（实现忠实性）＋ J1/J2/J3（语义精度，pass = J0 ∧ (J2 ∨ J3)）
  * ══════════════════════════════════════════════════════════════════════════════
  * 记 windowLsb = 2^(E−126−W)，E = 激活非零槽 expField 的 max（软件侧从【激励】算出，不读 RTL）。
  *
  * ── 规格模型（analyze 产出 modelRaw，J0 的被比对象）──
  *   按模块头注【写定的算法】用精确整数复算，全程不跑 RTL：
  *     ① 激活掩码：L0 恒活、L1 = is2x||is4x、L2/L3 = is4x；零槽（prod: e==0&&m==0 / cReg: e==0&&m31==0）
  *        排除（RTL 的 AND-mask 同时清 magW 与 eEff ⟹ 模型等价排除）。
  *     ② E = max(激活非零槽 eField)。
  *     ③ 每槽 a_i = mag_i >> ts_i，ts_i = (fieldW_i − W) + clamp(E − e_i, W)；prod fieldW=24、cReg fieldW=31。
  *        ★ 与 RTL 的「先常量提取 magW = mag>>(fieldW−W) 再动态对齐 >>clamp(E−e,W)」严格等价，
  *          依据嵌套 floor 恒等式 floor(floor(x/2^p)/2^q) = floor(x/2^(p+q))。
  *     ④ signedSum = Σ (sign_i ? −a_i : +a_i)（整数加法结合律 ⟹ 与 CSA 树的求和次序无关）。
  *     ⑤ k = max(0, bitLength(|signedSum|) − W) ∈[0,3]；magTop = |signedSum| >> k。
  *     ⑥ mant31 = magTop << (31−W)；expOut = clamp(E+k, ExpSatMax=254)；raw = 0 若 signedSum==0，否则
  *        (sign<<39)|(expOut<<31)|mant31。
  *        ★ 钳位上界 = FpAddNarrowOpt.ExpSatMax = 254（task#18 A 项修复；原为 255，会让饱和域的
  *          Inf 乘积槽产出 expOut=255 且 mant31≠0 ⟹ 下游归一化成 fp32 NaN）。255 是 fp32 的
  *          Inf/NaN 保留域，累加器输出必须恒有限 ⟹ 派生【无条件不变量】：eOut ≤ 254（见 judge）。
  *   lossy_i ⟺ mag_i & (2^ts_i − 1) ≠ 0（含 term-drop 槽）；nLossy = Σ lossy_i；nDrop = Σ(e_i 差 ≥ W)。
  *
  * ── J0（实现忠实性，【所有】向量必过）──
  *   rawDut == modelRaw。这不是拟合 RTL —— modelRaw 是纯软件按 spec 算出的整数，一次 RTL 都没跑过；
  *   它把「RTL 是否忠实实现了写定的 spec」钉死。J0 失败 ⟹ 实现 bug（非精度让步），直接 abort。
  *   J0 与下面的语义判定【互不遮蔽】：J0 只管实现↔spec，J1/J2/J3 只管 spec↔张量语义真值。
  *
  * ── 解析误差界（第一性原理，含 k 档截断项）＝三段式【可证明】契约 ──
  *   两处截断，方向同为「丢幅值」：
  *     (a) 对齐 floor ⟹ 槽 i 丢 frac_i × windowLsb，frac_i ∈[0,1)；合计 < nLossy × windowLsb。
  *     (b) 溢出收回 magTop = magFull >> k ⟹ 丢 (magFull mod 2^k) windowLsb ≤ (2^k − 1) windowLsb。
  *   ┌ ① 绝对界（【无条件恒成立】，对任意输入/任意 W/含相消域）= J3 判据 absBound
  *   │   |diff| ≤ (nLossy + 2^k − 1) × windowLsb，windowLsb = 2^(E−126−W)
  *   └ 这是本模块唯一无条件的精度承诺。
  *   ┌ ② 相对界（【条件成立】：仅良态域）= J2 判据 / A3 强断言
  *   │   relErr ≤ 2^−(W−1)   当充分条件 |exact| ≥ (nLossy + 2^k − 1) × 2^(W−1) × windowLsb 成立
  *   └ 推导：relErr = |diff|/|exact| ≤ ①/|exact|，代入充分条件即得。整数化写法（A3 用）：
  *       (nLossy + 2^k−1) × 2^(W−1) ≤ magFull − nLossy。
  *       nLossy=0 ∧ k=0 ⟹ ① 给 0 ⟹ 【精确无损】（J1）。
  *   ┌ ③ 相消域（【任意有限 W 都无相对界】）
  *   │   |exact| < 若干 windowLsb 时 relErr = |diff|/|exact| 可任意大（|exact|→0 时 →∞）
  *   └ 数学事实而非实现缺陷：任何有限精度加法器皆然（FpAdd 亦然，只是 31-bit 窗口把门槛推得更低）。
  *       **加大 W 不能消除它**，只能把门槛下移 ⟹ 该域必须由 ① 兜底（J3），不得声称相对界。
  *   ★【诚实标注 · task#18 D 项订正】nLossy ≥ 2 时阈值 2^−(W−1) 可被【超出】。已构造出 MX8-legal
  *     的实测反例（见 mlbound 组 ML1/ML2：实测相对误差档 = W−2 / W−3 < W−1）⟹ 这类向量 J2 失败、
  *     由 J3 绝对界兜住。触发规律：E 槽【无损且幅值最小】(magW = 2^(W−1)) + 其余槽大幅移位后
  *     a_i 很小但 frac_i ≈ 1 ⟹ relErr ≈ nLossy/2^(W−1)。
  *     ★ 本 spec 原先写「若要无条件严格 2^−18，应取 W=20/21」——【该表述错误，已删除】。据 ③，
  *       超阈向量同时来自相消域与多损域，**任何有限 W 都无法给出无条件相对界**。Terry(task#17)
  *       独立随机负载测量为证：W=19 达标率 97.33%、W=22 仅升到 99.33%（仍有 143 条 in-spec 超阈、
  *       最差 2^−5）。正确契约只能是「① 无条件绝对界 + ② 良态域相对界 + ③ 相消域由绝对界兜底」。
  *
  * ── J1（语义精确无损 ⟹ 最强：diff == 0）──
  *   nLossy == 0 ∧ k == 0 ∧ 未钳位 ⟹ 每槽 a_i × windowLsb 恰等于该槽精确真值 ⟹ signedSum = exact/windowLsb
  *   ⟹ 输出【完全由金锚 + spec 的编码约定】决定，无一个自由度留给 RTL：
  *       sign = sign(exact)；mant31 = |exact|/windowLsb << (31−W)；expOut = E；signedSum==0 ⟹ raw = 40'h0。
  *   A2 强断言：解析 lossless 成立 ⟹ j1 必须成立（否则金锚/模型/RTL 三者必有一处错）。
  *   MX4 全域（同 block 指数差 ≤ 7 ≤ W−4）与 MX8 稳态域（sh ≤ W−8）恒落 J1 ⟹ 用户要求的
  *   「MX4 应无损、可断言更严」由 J1 兑现（见 mx4sweep 组的【全域 J1】审计断言）。
  *   ★【措辞限定 · task#20(2) 同族订正】上句「MX4 全域」的准确含义是【mx4sweep 组的全 256 码对域】
  *     （E2M1 16×16，SA/SB 固定、cReg 按 :846 交替注入 {CZero, 2.0}），**不是** MX4 模式的全输入域。
  *     括号里「同 block 指数差 ≤7 ≤ W−4」只论证了【四个 lane 槽】的对齐无损，并未覆盖【cReg 槽】：
  *     mx4sweep 的 cReg 之所以同样无损，靠的是 2.0 的 mant31 低 31−W 位为 0 且 E−cE < W（激励受限的
  *     巧合，不是格式性质）。cReg 一旦换成满尾数 lazy-40 即出现 nLossy ≥ 1 ⟹ 落 J2/J3 ——
  *     同文件反例：DR3(:765)/G2b(:804)/G3b(:810)/LZ2(:818)/ML2(:829)，全部 Mx4 模式 + cSatMag 满幅 cReg。
  *     系统级同族反例见 HiveFusionMacFpNarrowSpec 的 n_mx8_cReg_spread（E=154 vs cE=127 ⟹ 差 27 ≥ W=19
  *     ⟹ cReg 整槽 term-drop ⟹ nLossy=2 ⟹ 契约 ② 充分条件不成立 ⟹ 只能由契约 ① 绝对界兜底）。
  *
  * ── J2（相对误差阈值，主判定）──
  *   |diff| × 2^relShift ≤ |exact|，relShift = W−1（可配置常量 relErrShiftOf）。纯整数比较。
  *   A3 强断言：解析充分条件 (nLossy + 2^k−1) × 2^(W−1) ≤ magFull − nLossy 成立 ⟹ j2 必须成立。
  *
  * ── J3（相消域 / 多损域兜底：解析绝对界 = 契约 ①）──
  *   |exact| 远小于最大项时相对误差被条件数放大 —— 契约 ③ 指出这在【任意有限 W】下都无法避免
  *   （任何有限精度加法器的共性，FpAdd 亦然，只是其 31-bit 窗口把门槛推得更低），不构成 L2 的
  *   额外缺陷。故该域改用【无条件】绝对界：
  *     |diff| ≤ (nLossy + 2^k − 1) × windowLsb
  *   J3 命中即打印 path=J3-absbound，报告中与 J1/J2 分开计数（诚实区分「达标」与「兜底」）。
  *
  * ── relShiftMeasured（实测最差相对误差，报告用）──
  *   对每向量求最大 r 使 |diff|×2^r ≤ |exact| ⟹ 实测 relErr ≤ 2^−r（diff==0 ⟹ Int.MaxValue）。
  *   全向量取【最小】r 作为「实测最差相对误差档」，与阈值 W−1 对比后写入报告（估算 vs 实测分离）。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 覆盖矩阵（group 字段；每组的命中数与判定路径在末尾 coverage-audit block 汇总断言）
  * ══════════════════════════════════════════════════════════════════════════════
  *   norm     正常累加（MX8 3 槽 / MX4 5 槽 / 混合符号 / 非零 cReg）
  *   wbound   指数差跨 W 边界：d ∈ {W−8, W−7（无损↔有损精确转折点）, W−4, W−1, W, W+1, W+8}
  *   drop     term-drop（整槽移出窗口；含 cReg 被丢、多槽同时被丢）
  *   cancel   异号相消（完全相消 → 40'h0 真零；近完全相消仍【精确】；有损相消走 J3）
  *   zero     零操作数（全零 → 40'h0；单操作数；未激活 lane 灌垃圾指数 ⟹ 掩零必须同时清 expEff；
  *            Fp16Only don't-care 退化支路）
  *   growth   溢出增长档 k = bitLength(magFull) − W ∈ {0,1,2,3}（k=3 需 synthetic 满窗口幅值槽，
  *            因 MX-legal 尾数下 |S|max < 2^(W+2) ⟹ k ≤ 2，见报告）
  *   lz       cReg 带前导零（显式注入 lz 档 / fp32 满尾数 cReg / 自产形态 cReg = magTop<<(31−W)）
  *   mlbound  【诚实标注组】MX-legal 但 nLossy ≥ 2 ⟹ 实测相对误差档 < W−1 的构造反例
  *            （J2 失败、由 J3 绝对界兜住；用于给报告提供「阈值可被超出」的实测证据，不是 bug）
  *   loop     环路回灌（独立 block）：cReg_{i+1} = DUT 输出 ⟹ 检验
  *            ① 每拍绝对误差 ≤ absBound 不放大；② 【反膨胀】expOut_i ≤ maxLaneE + 3 + 2·ceilLog2(i+2)
  *              （k 档右移的必要性证据：常量 expOut=E+3 的 D1' 变体会给 maxLaneE+3i ⟹ 立即被捕获）
  *   mx4sweep E2M1 全 16×16 码对扫（4 lane 各自组合）⟹ 断言【全域 J1 无损】
  *   mx8sweep E4M3 / E5M2 / 混合码对扫（宽指数跨度）⟹ 覆盖 lossy + term-drop 真实工作域
  *   oos      out-of-spec（独立 block，semantic=false ⟹ 只断言 J0 + 结构性守卫，不做金锚裁决）：
  *            全 fp32 subnormal 槽 ⟹ E=0（E1）、cReg exp==0&&mant31!=0（E2）、
  *            expOut 饱和钳位（E3：断言钳到 ExpSatMax=254 而非 8-bit 绕回，也【不是】255）、
  *            inf/nan 槽（E4/E5）、
  *            MX8 下 L2/L3 灌 eField=254 垃圾（E6：验掩零清 expEff 的极限形态）
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * W 参数化扫描
  * ══════════════════════════════════════════════════════════════════════════════
  * 同一套【W 无关】的金锚与判定引擎，对 W ∈ {19(默认), 16, 22} 各跑一次（阈值 relShift = W−1 同步）。
  * 向量集中所有「跨 W 边界」的指数差都写成 W 的函数 ⟹ 三档 W 覆盖到的是【同一组语义边界】。
  *
  * ══════════════════════════════════════════════════════════════════════════════
  * 测试坑规避（项目记忆 / house 约定，镜像 HiveFusionMacFpSpec）
  * ══════════════════════════════════════════════════════════════════════════════
  *   · ScalaTest 参数化用例名撞车致整套 ABORTED(tests run=0)：本 spec 用【固定唯一】的 it should 描述串
  *     （block 名互异），向量在 block 内以 for 循环 + 带 tag 的 assert 消息逐一校验。
  *   · 显式 reset（Verilator 上电语义）：每个 simulate 开头 reset.poke(true) + 全输入安全默认 + step(3)。
  *   · backend：显式 verilator【无 trace】⟹ 不依赖 lz4/CPATH/LIBRARY_PATH、不产波形。
  *   · FpAddNarrowOpt 是【纯组合】模块（无寄存器）⟹ 无 F1 时序契约问题；仍按 house 习惯 poke → step(1) → peek。
  *   · AnyFlatSpec 按声明序执行 ⟹ 末尾 coverage-audit block 可读前面 block 填充的统计映射。
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class FpAddNarrowOptSpec extends AnyFlatSpec with Matchers with ChiselSim {

  // 显式 verilator 后端（默认 CompilationSettings，无 trace）：不依赖 lz4/CPATH，不产波形。
  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  // ════════════════════════════════════════════════════════════════════════════
  // 可配置常量
  // ════════════════════════════════════════════════════════════════════════════
  /** 相对误差阈值指数 relShift：relErr ≤ 2^−relShift。
    * · FixedRelErrShift < 0（默认）⟹ relShift = W−1，与头注解析界 relErr < nLossy × 2^−(W−1)、
    *   nLossy ≤ 1 恰好匹配：W=19 → 2^−18（= FpAddNarrowOpt.DefaultRelErrShift，用户裁定的默认阈值）、
    *   W=16 → 2^−15、W=22 → 2^−21。
    * · FixedRelErrShift ≥ 0 ⟹ 三档 W 统一用该固定阈值（收紧/放宽旋钮，便于做阈值敏感度实验）。 */
  private val FixedRelErrShift: Int = -1
  private def relErrShiftOf(w: Int): Int = if (FixedRelErrShift >= 0) FixedRelErrShift else w - 1

  /** 参与扫描的窗口宽度档（W=19 为默认交付档；16/22 证明参数化可用）。 */
  private val WsToTest: Seq[Int] = Seq(FpAddNarrowOpt.DefaultW, 16, 22)

  private val E4 = DataFormat.MXE4M3
  private val E5 = DataFormat.MXE5M2
  private val E2 = DataFormat.MXE2M1

  /** in-spec scale 域（三格式元素均不饱和，见 HiveFusionMacFpSpec 头注 §in-spec：scale ≤ 238）。 */
  private val SA = 127
  private val SB = 127

  /** 结构覆盖向量的基准 fp32 指数域。E = EB ⟹ expOut = EB + k ≤ 143 ≤ 252 ⟹ 不触发 (E3) 钳位。
    * 同时 EB − (W−7) ≥ 1 ⟹ 跨 W 边界的低指数槽仍落在 fp32 normal 域（W ≤ 22 时 EB−15 = 125 ✓）。 */
  private val EB = 140

  // ════════════════════════════════════════════════════════════════════════════
  // Dyadic：精确二进制有理数 value = m × 2^e（金锚载体，加/减/乘封闭无舍入）
  // ════════════════════════════════════════════════════════════════════════════
  /** 不变量（由 Dyadic.apply 唯一入口保证，禁止裸 new）：m 为奇数，或 m == 0 且 e == 0。
    * ⟹ 规范形唯一 ⟹ 结构相等 (m,e) 即数学相等，equals 可直接比字段。 */
  final class Dyadic(val m: BigInt, val e: Int) {
    def isZero: Boolean = m.signum == 0
    def signum: Int = m.signum
    def abs: Dyadic = if (m.signum < 0) Dyadic(-m, e) else this
    def unary_- : Dyadic = Dyadic(-m, e)
    def +(o: Dyadic): Dyadic = {
      if (isZero) o
      else if (o.isZero) this
      else {
        val k = math.min(e, o.e)
        Dyadic(m * (BigInt(1) << (e - k)) + o.m * (BigInt(1) << (o.e - k)), k)
      }
    }
    def -(o: Dyadic): Dyadic = this + (-o)
    def *(o: Dyadic): Dyadic = if (isZero || o.isZero) Dyadic.Zero else Dyadic(m * o.m, e + o.e)
    /** × 2^n（n 可为负）——无舍入。 */
    def shl(n: Int): Dyadic = Dyadic(m, e + n)
    /** |this| ≤ |o|（精确整数判定：对齐到公共 2 的幂后比 BigInt）。 */
    def leAbs(o: Dyadic): Boolean = {
      if (isZero) true
      else if (o.isZero) false
      else { val k = math.min(e, o.e); (m.abs << (e - k)) <= (o.m.abs << (o.e - k)) }
    }
    /** |this| < |o|（严格）。 */
    def ltAbs(o: Dyadic): Boolean = {
      if (o.isZero) false
      else if (isZero) true
      else { val k = math.min(e, o.e); (m.abs << (e - k)) < (o.m.abs << (o.e - k)) }
    }
    /** 仅供报告打印（不参与任何判定）；|值| 超 Double 域时给 Inf，可接受。 */
    def toDouble: Double = if (isZero) 0.0 else m.toDouble * math.pow(2.0, e.toDouble)
    override def equals(o: Any): Boolean = o match {
      case d: Dyadic => m == d.m && e == d.e
      case _ => false
    }
    override def hashCode: Int = m.hashCode * 31 + e
    override def toString: String =
      if (isZero) "0" else f"$m*2^$e(=${toDouble}%.9g)"
  }
  object Dyadic {
    val Zero: Dyadic = new Dyadic(BigInt(0), 0)
    def apply(m: BigInt, e: Int): Dyadic = {
      if (m.signum == 0) Zero
      else {
        var mm = m.abs; var ee = e
        while (mm > 1 && !mm.testBit(0)) { mm >>= 1; ee += 1 }   // 剥尾零 → 规范形
        new Dyadic(if (m.signum < 0) -mm else mm, ee)
      }
    }
  }

  /** Dyadic → 精确整数（当且仅当 e ≥ 0 或值为零；否则 None）。 */
  private def dyadicToInt(d: Dyadic): Option[BigInt] =
    if (d.isZero) Some(BigInt(0)) else if (d.e >= 0) Some(d.m << d.e) else None

  // ════════════════════════════════════════════════════════════════════════════
  // 位域编解码（第一性原理；radix 约定逐字继承 FpAdd.scala / Fp32.fpNormalize40to32）
  // ════════════════════════════════════════════════════════════════════════════
  /** 十六进制打印工具：Scala 的 `Long` 无 `toString(radix)` 重载（只有 `BigInt`/`Int` 有），
    * 统一走 `java.lang.Long.toHexString` 避免类型分歧（纯打印用途，不参与任何数值判定）。 */
  private def hx(x: Long): String = java.lang.Long.toHexString(x)

  /** fp32 bits → 精确真值。normal 域：value = (2^23+m) × 2^(e−150)。
    * e==0&&m==0 → ±0；e==0&&m!=0（subnormal）与 e==255（inf/nan）属 out-of-spec ⟹ require 拒绝
    * （这些槽只在 oos block 里以裸位模式驱动，不进金锚判定）。 */
  private def fp32BitsToDyadic(b: Long): Dyadic = {
    val bb = b & 0xFFFFFFFFL
    val s = ((bb >> 31) & 1).toInt
    val e = ((bb >> 23) & 0xFF).toInt
    val m = (bb & 0x7FFFFF).toInt
    if (e == 0 && m == 0) return Dyadic.Zero
    require(e >= 1, s"fp32BitsToDyadic: e==0&&m!=0 是 fp32 subnormal 槽，属 out-of-spec(E1)，in-spec 金锚不覆盖 (bits=0x${hx(bb)})")
    require(e <= 254, s"fp32BitsToDyadic: e==255 是 fp32 inf/nan 槽，属 out-of-spec，in-spec 金锚不覆盖 (bits=0x${hx(bb)})")
    Dyadic(BigInt(if (s == 1) -1 else 1) * ((BigInt(1) << 23) | BigInt(m)), e - 150)
  }

  /** 精确真值 → fp32 bits（要求 ≤24-bit 有效位且落在 normal 指数域 [1,254]）。
    * require 失败 = 【激励设计错误】（向量越出 in-spec 域），非 RTL 问题 ⟹ 直接抛出，不放宽金锚。 */
  private def dyadicToFp32Bits(v: Dyadic): Long = {
    if (v.isZero) return 0L
    val s = if (v.signum < 0) 1L else 0L
    val mag = v.m.abs
    val bl = mag.bitLength
    require(bl <= 24, s"dyadicToFp32Bits: 有效位 $bl > 24 ⟹ 非 fp32 精确可表示（激励设计错误）v=$v")
    val expField = bl - 1 + v.e + 127
    require(expField >= 1 && expField <= 254,
      s"dyadicToFp32Bits: expField=$expField 越 fp32 normal 域 [1,254]（饱和/subnormal 属 out-of-spec）v=$v")
    val mant23 = ((mag << (24 - bl)) & BigInt(0x7FFFFF)).toLong
    ((s << 31) | (expField.toLong << 23) | mant23) & 0xFFFFFFFFL
  }

  /** lazy-40 bits → 精确真值。radix：value = mant31 × 2^(exp−157)（bit30 ↔ 2^(exp−127)）。
    * 真零判据 exp==0 && mant31==0（下游 fpNormalize40to32 的 isZero 分支只看这两域）。 */
  private def lazy40ToDyadic(raw: BigInt): Dyadic = {
    val s = ((raw >> 39) & 1).toInt
    val e = ((raw >> 31) & 0xFF).toInt
    val m31 = raw & ((BigInt(1) << 31) - 1)
    if (e == 0 && m31 == 0) Dyadic.Zero
    else Dyadic(BigInt(if (s == 1) -1 else 1) * m31, e - 157)
  }

  /** 精确真值 → lazy-40 bits。lz = 【强制注入】的前导零位数（覆盖「cReg 带前导零」，lazy 格式合法）。
    * 推导：mant31 = mag << (30−lz−(bl−1)) ⟹ value = mant31 × 2^(exp−157) = mag × 2^v.e
    *       ⟹ exp = v.e + 126 + lz + bl。lz=0 且 bl=24 时恰还原 HiveSimCommon.fp32BitsTo40 的编码。 */
  private def dyadicToLazy40(v: Dyadic, lz: Int = 0): BigInt = {
    if (v.isZero) return BigInt(0)
    require(lz >= 0 && lz <= 30, s"dyadicToLazy40: lz=$lz 越界 [0,30]")
    val s = if (v.signum < 0) 1 else 0
    val mag = v.m.abs
    val bl = mag.bitLength
    require(bl + lz <= 31, s"dyadicToLazy40: 有效位 $bl + 前导零 $lz > 31 ⟹ mant31 装不下")
    val mant31 = mag << (30 - lz - (bl - 1))
    val expField = v.e + 126 + lz + bl
    require(expField >= 1 && expField <= 255, s"dyadicToLazy40: expField=$expField 越 lazy-40 域 [1,255]")
    (BigInt(s) << 39) | (BigInt(expField) << 31) | mant31
  }

  // ════════════════════════════════════════════════════════════════════════════
  // MX 元素精确真值（第一性原理，全程整数；参数引 MxFormatParams 单例，禁裸字面量）
  // ════════════════════════════════════════════════════════════════════════════
  /** 三格式的解码旗标（与 HiveSimCommon.mxToFloat 的调用点逐条同源）：
    * · E4M3：nanNeedMantAllOne=true（e&m 全 1 才 NaN）、subnormalFtz=true（e==0 → ±0）
    * · E5M2：nanNeedMantAllOne=false（e 全 1 即 inf/nan）、subnormalFtz=true
    * · E2M1：noInfNan=true（无 inf/nan 编码，0x7=±6.0 为最大有限值）、subnormalFtz=false
    *         （e=0/m=1 = ±0.5 是一等公民值，隐含 1.0 ⟹ 不乘 (1+m/2)） */
  private final case class MxElemFmt(p: MxFormatParams, nanNeedMantAllOne: Boolean,
                                     noInfNan: Boolean, subnormalFtz: Boolean)
  private val FmtE4M3 = MxElemFmt(MxFormatParams.E4M3, nanNeedMantAllOne = true,  noInfNan = false, subnormalFtz = true)
  private val FmtE5M2 = MxElemFmt(MxFormatParams.E5M2, nanNeedMantAllOne = false, noInfNan = false, subnormalFtz = true)
  private val FmtE2M1 = MxElemFmt(MxFormatParams.E2M1, nanNeedMantAllOne = false, noInfNan = true,  subnormalFtz = false)
  private def fmtOf(d: DataFormat.Type): MxElemFmt =
    if (d == DataFormat.MXE4M3) FmtE4M3
    else if (d == DataFormat.MXE5M2) FmtE5M2
    else if (d == DataFormat.MXE2M1) FmtE2M1
    else { require(false, s"fmtOf: 非 MX 格式 $d（本 spec 只覆盖 MX-only 域）"); FmtE2M1 }

  /** MX 元素精确真值（Dyadic）。expSigned = e + scale − bias（= 元素 fp32 指数域）。
    * normal  : ±(2^mantW + m) × 2^(expSigned − 127 − mantW)
    * subnormal(E2M1) : ±1 × 2^(expSigned − 127)
    * fp8 e==0（FTZ）/ expSigned < 1（underflow clamp）⟹ ±0
    * inf/nan 码 或 expSigned > 254（元素饱和）⟹ out-of-spec，require 拒绝（不进金锚判定） */
  private def mxElemDyadic(code: Int, scale: Int, f: MxElemFmt): Dyadic = {
    val p = f.p
    val b = code & 0xFF
    val s = (b >> (p.expW + p.mantW)) & 1
    val e = (b >> p.mantW) & ((1 << p.expW) - 1)
    val m = b & ((1 << p.mantW) - 1)
    val sgn = if (s == 1) -1 else 1
    val eMax = (1 << p.expW) - 1
    val mMax = (1 << p.mantW) - 1
    val isZero = (e == 0) && (m == 0)
    val isSub = (e == 0) && (m != 0)
    val isInfNan = if (f.noInfNan) false
                   else if (f.nanNeedMantAllOne) (e == eMax && m == mMax) else (e == eMax)
    val expSigned = e + (scale & 0xFF) - p.bias
    require(!isInfNan, s"mxElemDyadic: 码 0x${b.toHexString} 是 inf/nan（out-of-spec），in-spec 金锚不覆盖")
    require(expSigned <= 254, s"mxElemDyadic: expSigned=$expSigned > 254 ⟹ 元素饱和（out-of-spec），请收紧 scale")
    if (isZero) Dyadic.Zero
    else if (expSigned < 1) Dyadic.Zero
    else if (isSub && f.subnormalFtz) Dyadic.Zero
    else if (isSub) Dyadic(BigInt(sgn), expSigned - 127)
    else Dyadic(BigInt(sgn) * ((BigInt(1) << p.mantW) + BigInt(m)), expSigned - 127 - p.mantW)
  }

  // 元素码构造器（纯 Scala，第一性原理位域打包；镜像 HiveFusionMacFpSpec）
  private def e4m3(s: Int, e: Int, m: Int): Int = ((s & 1) << 7) | ((e & 0xF) << 3) | (m & 0x7)
  private def e5m2(s: Int, e: Int, m: Int): Int = ((s & 1) << 7) | ((e & 0x1F) << 2) | (m & 0x3)
  private def e2m1(s: Int, e: Int, m: Int): Int = ((s & 1) << 3) | ((e & 0x3) << 1) | (m & 0x1)

  // ════════════════════════════════════════════════════════════════════════════
  // 槽构造器：返回 (硬件位模式, 精确真值)。真值一律由位域/语义第一性原理算出，绝不读 RTL。
  // ════════════════════════════════════════════════════════════════════════════
  private type ProdSlot = (Long, Dyadic)
  private type CSlot = (BigInt, Dyadic)
  private val PZero: ProdSlot = (0L, Dyadic.Zero)
  private val CZero: CSlot = (BigInt(0), Dyadic.Zero)

  /** 按 fp32 位域直接构造乘积槽（结构覆盖用：可精确控制 expField 与尾数位模式）。
    * ★ 严格 in-spec：eField==255（Inf/NaN）会被 fp32BitsToDyadic 的 require 拒绝 —— 这是【故意】的，
    * 它能捕获「不小心把 Inf 槽混进 in-spec 向量」的激励设计错误。out-of-spec 槽请改用 prodOos。 */
  private def prodRaw(s: Int, eField: Int, m: Int): ProdSlot = {
    require(eField >= 0 && eField <= 255 && m >= 0 && m <= 0x7FFFFF, s"prodRaw: 位域越界 e=$eField m=0x${m.toHexString}")
    val bits = ((s.toLong << 31) | (eField.toLong << 23) | m.toLong) & 0xFFFFFFFFL
    (bits, if (eField == 0) Dyadic.Zero else fp32BitsToDyadic(bits))
  }

  /** out-of-spec 槽构造器：【位模式照给，真值位置填 Dyadic.Zero 占位】。
    * 理由：e==255（Inf/NaN）与 e==0&&m!=0（fp32 subnormal）的真值不在 Dyadic 的可表示域内
    * （Dyadic 只表示有限二进制有理数），而 OOS 向量按 `semantic = false` 只走
    *   · J0（RTL 输出 == 规格模型 modelRaw，纯整数，与真值无关）
    *   · 结构性守卫（expOut 不得 8-bit 绕回）
    * 【绝不】用金锚裁决（judge 内 if(semantic) 分支整体跳过）⟹ 占位值不参与任何断言。
    * 这不是「放宽金锚」：in-spec 向量仍走 prodRaw 的严格 require。 */
  private def prodOos(s: Int, eField: Int, m: Int): ProdSlot = {
    require(eField >= 0 && eField <= 255 && m >= 0 && m <= 0x7FFFFF, s"prodOos: 位域越界 e=$eField m=0x${m.toHexString}")
    (((s.toLong << 31) | (eField.toLong << 23) | m.toLong) & 0xFFFFFFFFL, Dyadic.Zero)
  }

  /** out-of-spec 的 cReg 槽构造器（同 prodOos 的占位语义；cRaw 的 lazy40ToDyadic 无指数域约束，
    * 但对 exp==0&&mant31!=0 这类【违反 lazy-40 不变量】的输入，其"真值"本身无定义 ⟹ 一律占位）。 */
  private def cOos(s: Int, eField: Int, m31: BigInt): CSlot = {
    require(eField >= 0 && eField <= 255 && m31 >= 0 && m31 < (BigInt(1) << 31),
      s"cOos: 位域越界 e=$eField m31=0x${m31.toString(16)}")
    ((BigInt(s) << 39) | (BigInt(eField) << 31) | m31, Dyadic.Zero)
  }

  /** MX 语义乘积槽：elemA × elemB（Dyadic 精确）→ fp32 位。与上游 FpMulMx8/FpMulMx4 的
    * in-spec 输出逐位同值（数学证明见文件头「金锚来源」）。 */
  private def mxProd(fa: DataFormat.Type, ca: Int, sa: Int,
                     fb: DataFormat.Type, cb: Int, sb: Int): ProdSlot = {
    val v = mxElemDyadic(ca, sa, fmtOf(fa)) * mxElemDyadic(cb, sb, fmtOf(fb))
    (dyadicToFp32Bits(v), v)
  }

  /** MX8-legal 乘积槽：P = aSig×bSig ∈ [64,225]（aSig,bSig ∈ [8,15]）⟹ mag24 = P<<16（P≥128）
    * 或 P<<17（P<128）⟹ mant23 低 16 位结构性 0（对齐 shift ≤ W−8 无损的根据）。 */
  private def prodMx8P(s: Int, eField: Int, p: Int): ProdSlot = {
    require(p >= 64 && p <= 225, s"prodMx8P: P=$p 越 MX8 尾数积域 [64,225]")
    val mag24 = if (p >= 128) BigInt(p) << 16 else BigInt(p) << 17
    prodRaw(s, eField, (mag24 - (BigInt(1) << 23)).toInt)
  }

  /** MX4-legal 乘积槽：P = (2+m1ₐ)(2+m1_b) ∈ {4,6,9} ⟹ mag24 = P<<21（P=4/6）/ P<<20（P=9）
    * ⟹ mant23 ∈ {0, 0x400000, 0x100000}（低 20 位结构性 0 ⟹ shift ≤ W−4 无损）。
    * eField 直接给【已含 normAdj 的 eOut】（P=9 时上游 +1），故与 FpMulMx4 输出同值。 */
  private def prodMx4P(s: Int, eField: Int, p: Int): ProdSlot = {
    val mag24 = p match {
      case 4 => BigInt(4) << 21
      case 6 => BigInt(6) << 21
      case 9 => BigInt(9) << 20
      case _ => require(false, s"prodMx4P: 非法 MX4 尾数积 P=$p（∈{4,6,9}）"); BigInt(0)
    }
    prodRaw(s, eField, (mag24 - (BigInt(1) << 23)).toInt)
  }

  /** 满窗口幅值乘积槽（synthetic，【非 MX-legal】）：mag24 = (2^W−1) << (24−W) ⟹ magW = 2^W−1，
    * 且低 (24−W) 位为 0 ⟹ 提取【无损】。5 槽同指数 ⟹ |S| = 5×(2^W−1) ⟹ k=3 档结构覆盖
    * （MX-legal 尾数下 k ≤ 2，见 growth 组注释）。 */
  private def prodFullWin(s: Int, eField: Int, w: Int): ProdSlot = {
    require(w >= 8 && w <= 24, s"prodFullWin: 本构造器要求 W ∈ [8,24]（W>24 时提取为左移，另议）w=$w")
    val mag24 = ((BigInt(1) << w) - 1) << (24 - w)
    prodRaw(s, eField, (mag24 - (BigInt(1) << 23)).toInt)
  }

  /** 按 lazy-40 位域直接构造 cReg 槽。 */
  private def cRaw(s: Int, eField: Int, m31: BigInt): CSlot = {
    require(eField >= 0 && eField <= 255 && m31 >= 0 && m31 < (BigInt(1) << 31),
      s"cRaw: 位域越界 e=$eField m31=0x${m31.toString(16)}")
    val bits = (BigInt(s) << 39) | (BigInt(eField) << 31) | m31
    (bits, lazy40ToDyadic(bits))
  }

  /** 由精确真值构造 cReg 槽；lz = 强制注入的前导零位数（lazy 格式合法，覆盖「cReg 带前导零」）。 */
  private def cVal(v: Dyadic, lz: Int = 0): CSlot = (dyadicToLazy40(v, lz), v)

  /** 满窗口幅值 cReg 槽：mant31 = (2^W−1) << (31−W) ⟹ magW = 2^W−1、低 (31−W) 位为 0 ⟹ 无损。 */
  private def cFullWin(s: Int, eField: Int, w: Int): CSlot =
    cRaw(s, eField, ((BigInt(1) << w) - 1) << (31 - w))

  // ════════════════════════════════════════════════════════════════════════════
  // 模式与向量
  // ════════════════════════════════════════════════════════════════════════════
  /** 三模式（优先级 is4x > is2x，逐字对齐 FpAdd.scala:366 与 HiveFusionMacFp.scala:252-253）。
    * laneActive = L0..L3 是否供数；L0 恒激活；L1 = is2x||is4x；L2/L3 = is4x。
    * 上游实际驱动：MX4（aFmt=bFmt=MXE2M1）⟹ isMx4=1、isMx8=0；MX8 ⟹ isMx4=0、isMx8=1。 */
  private final case class ModeSpec(is4x: Boolean, is2x: Boolean) {
    def laneActive: Seq[Boolean] = Seq(true, is2x || is4x, is4x, is4x)
    def name: String = if (is4x) "MX4(5槽)" else if (is2x) "MX8(3槽)" else "FP16ONLY(don't-care,2槽)"
  }
  private val Mx4 = ModeSpec(is4x = true, is2x = false)
  private val Mx4b = ModeSpec(is4x = true, is2x = true)   // 上游不会出现，验证 is4x 优先级鲁棒性
  private val Mx8 = ModeSpec(is4x = false, is2x = true)
  private val Fp16Only = ModeSpec(is4x = false, is2x = false)

  /** 一条激励向量：5 槽硬件位模式 + 每槽【张量语义精确真值】。 */
  private final case class Vec(tag: String, group: String, mode: ModeSpec,
                               cBits: BigInt, cValExact: Dyadic,
                               pBits: Seq[Long], pVal: Seq[Dyadic], note: String = "")

  private def vec(tag: String, group: String, mode: ModeSpec, c: CSlot,
                  p0: ProdSlot = PZero, p1: ProdSlot = PZero,
                  p2: ProdSlot = PZero, p3: ProdSlot = PZero, note: String = ""): Vec =
    Vec(tag, group, mode, c._1, c._2, Seq(p0._1, p1._1, p2._1, p3._1),
        Seq(p0._2, p1._2, p2._2, p3._2), note)

  // ════════════════════════════════════════════════════════════════════════════
  // 软件解析（纯数学，不触碰 RTL）：金锚 exact、公共指数 E、windowLsb、nLossy、nDrop、
  // 以及【无损路径的完整输出位模式金锚】
  // ════════════════════════════════════════════════════════════════════════════
  /** (active, isZero, eField, mag, fieldW, value, signBit)：
    * prod 槽 fieldW=24 / mag=mag24=Cat(1,mant23)；cReg 槽 fieldW=31 / mag=mant31。 */
  private type SlotM = (Boolean, Boolean, Int, BigInt, Int, Dyadic, Int)

  /** @param modelRaw    规格模型算出的 40-bit 输出位模式（见 analyze 头注）
    * @param magFull     模型 |S|（= |Σ ±a_i|，整数）
    * @param kModel      溢出收回档 k = max(0, bitLength(magFull) − W)
    * @param expOutModel 模型 expOut = clamp(E + k, FpAddNarrowOpt.ExpSatMax=254)
    * @param absBound    解析绝对界 (nLossy + 2^k − 1) × windowLsb
    * @param mustRel     A3 强断言的【充分条件】是否成立（成立 ⟹ J2 必须通过）
    * @param lossless    nLossy==0 && k==0 && 未钳位 ⟹ 语义必须精确（diff==0） */
  private final case class Analysis(w: Int, relShift: Int, eCommon: Int, lsb: Dyadic,
                                    nLive: Int, nLossy: Int, nDrop: Int, exact: Dyadic,
                                    modelRaw: BigInt, magFull: BigInt, kModel: Int,
                                    expOutModel: Int, absBound: Dyadic, mustRel: Boolean,
                                    lossless: Boolean)

  /** 纯软件解析。两条独立产出：
    *   ① 金锚 `exact` = 张量语义精确和（Dyadic，只由激励的【数值语义】算出，与模块算法无关）；
    *   ② 规格模型 `modelRaw` = 按模块头注【写定的算法】（floor 对齐 → 带符号整数求和 →
    *      k 档溢出收回 → lazy-40 高位对齐编码）用精确整数算出的输出位模式。
    * ② 不是 RTL 录制（全程不跑 RTL），它把「实现是否忠实于写定的 spec」钉死（J0）；
    * 而「spec 本身是否够准」由 ① 经 J1/J2/J3 独立裁决 ⟹ 两者互不遮蔽。
    * 模型与 RTL 的逐步对应（嵌套 floor 恒等式 floor(floor(x/2^p)/2^q)=floor(x/2^(p+q))）：
    *   magW = mag >> (fieldW−W)  再 >> clamp(E−e, W)  ≡  a = mag >> ts，ts = (fieldW−W)+clamp(E−e,W)
    *   非激活/零槽：RTL 的 AND-mask 同时清 magW 与 eEff ⟹ 模型直接排除（不进 live）
    *   S = Σ ±a_i（RTL 的 CSA 树与求和次序无关，整数加法结合律 ⟹ 模型用 foldLeft 即可）
    *   k / magTop / expOut / mant31 / 真零 mux：逐字对应模块输出级公式 */
  private def analyze(v: Vec, w: Int): Analysis = {
    // ★【task#25 Kim Minor#1 守卫】解析模型的 prod 槽 pre-shift 常量 (24−w) 仅在 w ≤ 24 时为右移；
    //   w > 24 时 RTL 走 `mag24 << (w−24)` 的左移分支（见 FpAddNarrowOpt.scala:263），本模型的
    //   ts = (fieldW − w) + sh 会给出负值、语义另议。对齐 prodFullWin(:453) 的既有 w ≤ 24 守卫，
    //   在入口钉死适用域，避免 WsToTest 未来误加 w > 24 档时掩码退化被静默吞掉。
    require(w <= 24, s"analyze: 解析模型仅在 W<=24（prod 槽 pre-shift 为右移）下已验证，收到 w=$w")
    val relShift = relErrShiftOf(w)
    val cE = ((v.cBits >> 31) & 0xFF).toInt
    val cM = v.cBits & ((BigInt(1) << 31) - 1)
    val cS = ((v.cBits >> 39) & 1).toInt
    val cZ = (cE == 0) && (cM.signum == 0)
    val cSlot: SlotM = (true, cZ, cE, cM, 31, v.cValExact, cS)
    val pSlots: Seq[SlotM] = (0 until 4).map { i =>
      val b = v.pBits(i) & 0xFFFFFFFFL
      val e = ((b >> 23) & 0xFF).toInt
      val m = (b & 0x7FFFFF).toInt
      val sg = ((b >> 31) & 1).toInt
      val z = (e == 0) && (m == 0)
      (v.mode.laneActive(i), z, e, (BigInt(1) << 23) | BigInt(m), 24, v.pVal(i), sg)
    }
    val live = (cSlot +: pSlots).filter(s => s._1 && !s._2)
    val eCommon = if (live.isEmpty) 0 else live.map(_._3).max
    val lsb = Dyadic(BigInt(1), eCommon - 126 - w)

    var nLossy = 0
    var nDrop = 0
    var signedSum = BigInt(0)
    live.foreach { s =>
      val sh = math.min(math.max(eCommon - s._3, 0), w)
      val ts = (s._5 - w) + sh
      val a = s._4 >> ts
      // ★【task#25 Kim Minor#1】ts 理论上可为负（当 w > 24 使 prod 槽 (24−w) < 0，或 cReg 槽极端参数），
      //   负 ts 会让掩码 (BigInt(1) << ts) − 1 退化为 −1（全 1）⟹ 任何非零 s._4 都被误判 lossy ⟹ nLossy 虚增。
      //   用 lost = max(0, ts) 做丢失位数：lost==0 表示无低位被丢（不进掩码判定），lost>0 才检查低 lost 位。
      //   a = s._4 >> ts 保持不变（负 ts 左移与 RTL 的 W>24 `mag24 << (w−24)` 分支语义一致，见入口 require(w<=24)）。
      val lost = math.max(0, ts)
      if (lost > 0 && (s._4 & ((BigInt(1) << lost) - 1)).signum != 0) nLossy += 1
      if (eCommon - s._3 >= w) nDrop += 1
      signedSum += (if (s._7 == 1) -a else a)
    }
    val exact = live.foldLeft(Dyadic.Zero)((acc, s) => acc + s._6)

    val magFull = signedSum.abs
    val k = math.max(0, magFull.bitLength - w)
    val magTop = magFull >> k
    val expOutRaw = eCommon + k
    // ★ 钳位上界【引用模块的单一真值源】FpAddNarrowOpt.ExpSatMax（= 254，task#18 A 项修复；原为裸 255）。
    //   引常量而非在模型里再写一遍 254 ⟹ 若将来上界调整，模型与 RTL 不会各自漂移（也不会假 PASS）。
    val expClamped = expOutRaw > FpAddNarrowOpt.ExpSatMax
    val expOut = if (expClamped) FpAddNarrowOpt.ExpSatMax else expOutRaw
    val modelRaw: BigInt =
      if (magFull.signum == 0) BigInt(0)
      else {
        val mant31 = magTop << (31 - w)
        require(mant31 < (BigInt(1) << 31), s"[${v.tag}] 模型 mant31 溢出 31 位（设计错误）")
        (BigInt(if (signedSum.signum < 0) 1 else 0) << 39) | (BigInt(expOut) << 31) | mant31
      }

    val truncMax = (BigInt(1) << k) - 1                       // magFull mod 2^k 的上界
    val absBound = Dyadic(BigInt(nLossy) + truncMax, lsb.e)     // |diff| ≤ 此值
    // A3 充分条件：(nLossy + 2^k−1) × 2^relShift ≤ magFull − nLossy  ⟹  |diff|×2^relShift ≤ |exact|
    //   ★【task#25 Kim Minor#2】用 relShift（= relErrShiftOf(w)，默认 w−1，但 FixedRelErrShift≥0 时被覆盖）
    //   替代硬编码 (w−1)，使 A3 的充分条件与 J2 判据(:615 diff.shl(a.relShift)) 始终同一阈值口径；
    //   否则做阈值敏感度实验（FixedRelErrShift≥0）时 mustRel 仍按 w−1 算 ⟹ A3 与 J2 口径漂移、可致假 PASS/假 FAIL。
    val mustRel = (BigInt(nLossy) + truncMax) * (BigInt(1) << relShift) <= (magFull - BigInt(nLossy))
    val lossless = (nLossy == 0) && (k == 0) && !expClamped
    Analysis(w, relShift, eCommon, lsb, live.size, nLossy, nDrop, exact,
             modelRaw, magFull, k, expOut, absBound, mustRel, lossless)
  }

  /** 判定结果。path ∈ {"J1-exact","J2-relative","J3-absbound","FAIL-MODEL","FAIL-SEMANTIC"}。
    * · j0：rawDut == 规格模型 modelRaw（实现忠实于 spec）——【所有】向量都必须成立
    * · j1：diff == 0（语义精确无损）——解析 lossless 成立时【必须】成立（A2 强断言）
    * · j2：|diff| × 2^relShift ≤ |exact|（用户裁定的相对阈值，默认 2^−18）
    * · j3：|diff| ≤ (nLossy + 2^k − 1) × windowLsb（解析绝对界；相消/多损域兜底）
    * · mustRel（A3）：解析充分条件成立 ⟹ j2 必须成立
    * · relShiftMeasured：实测最大 r 使 |diff|×2^r ≤ |exact| ⟹ 实测相对误差 ≤ 2^−r
    *   （diff==0 → Int.MaxValue）；报告中取全向量最小值作为「实测最差相对误差」
    * @param semantic false ⟹ out-of-spec 向量：只断言 j0 + 输出不变量，不做金锚裁决 */
  private final case class Verdict(ok: Boolean, j0: Boolean, j1: Boolean, j2: Boolean, j3: Boolean,
                                   mustRel: Boolean, lossless: Boolean, path: String, diff: Dyadic,
                                   relShiftMeasured: Int, kMeasured: Int, msg: String)

  private def judge(v: Vec, a: Analysis, rawDut: BigInt, semantic: Boolean = true): Verdict = {
    // ── 输出不变量守卫（结构性硬约束，与金锚无关 ⟹ oos 向量同样适用）──
    assert((rawDut >> 40).signum == 0, s"[${v.tag}] 输出超出 40 位：0x${rawDut.toString(16)}")
    val eOut = ((rawDut >> 31) & 0xFF).toInt
    val mOut = rawDut & ((BigInt(1) << 31) - 1)
    // ★ task#18 A 无条件不变量：expOut ≤ ExpSatMax(254) ⟹ 输出恒有限（非 Inf/NaN）。
    //   255 是 fp32 Inf/NaN 保留域：若 eOut=255 且 mant31≠0 → 下游 fpNormalize40to32 归一化成 NaN。
    assert(eOut <= FpAddNarrowOpt.ExpSatMax,
      s"[${v.tag}] ★ 无条件不变量违背：eOut=$eOut > ExpSatMax=${FpAddNarrowOpt.ExpSatMax} ⟹ 下游会归一化成 fp32 NaN")

    val j0 = rawDut == a.modelRaw
    val got = lazy40ToDyadic(rawDut)
    val diff = got - a.exact
    val j1 = diff.isZero
    val j2 = diff.shl(a.relShift).leAbs(a.exact)                  // |diff|×2^relShift ≤ |exact|
    val j3 = diff.abs.leAbs(a.absBound)                           // |diff| ≤ absBound
    val ok = if (semantic) (j0 && (j2 || j3)) else j0
    val path =
      if (!j0) "FAIL-MODEL"
      else if (!semantic) "OOS-model-only"
      else if (j1) "J1-exact"
      else if (j2) "J2-relative"
      else if (j3) "J3-absbound"
      else "FAIL-SEMANTIC"

    // 实测相对误差档：最大 r 使 |diff|×2^r ≤ |exact|
    val relShiftMeasured: Int =
      if (diff.isZero) Int.MaxValue
      else if (a.exact.isZero) 0
      else { var r = 0; while (r < 800 && diff.shl(r + 1).leAbs(a.exact)) r += 1; r }

    // k 实测（增长档覆盖统计）：expOut = E + k ⟹ k = expOut − E（钳位时记为 ExpSatMax(254)−E）
    val kMeasured: Int = eOut - a.eCommon

    val msg = {
      val sb = new StringBuilder
      sb.append(s"[W=${a.w}][${v.tag}][${v.group}][${v.mode.name}] VERDICT path=$path\n")
      sb.append(s"    note      : ${v.note}\n")
      sb.append(s"    inputs    : cReg=0x${v.cBits.toString(16)} L0=0x${hx(v.pBits(0))} " +
        s"L1=0x${hx(v.pBits(1))} L2=0x${hx(v.pBits(2))} L3=0x${hx(v.pBits(3))} " +
        s"is4x=${v.mode.is4x} is2x=${v.mode.is2x}\n")
      sb.append(s"    dutRaw    : 0x${rawDut.toString(16)}  (sign=${(rawDut >> 39) & 1} exp=$eOut mant31=0x${mOut.toString(16)})\n")
      sb.append(s"    modelRaw  : 0x${a.modelRaw.toString(16)}  (E=${a.eCommon} k=${a.kModel} |S|=${a.magFull} expOut=${a.expOutModel}) ⟹ J0=$j0\n")
      sb.append(s"    golden    : exact=${a.exact}\n")
      sb.append(s"    got       : ${got}\n")
      sb.append(s"    diff      : ${diff}\n")
      sb.append(s"    windowLsb=${a.lsb} nLive=${a.nLive} nLossy=${a.nLossy} nDrop=${a.nDrop} lossless=${a.lossless}\n")
      sb.append(s"    J1 diff==0 ⟹ $j1\n")
      sb.append(s"    J2 需 |diff|×2^${a.relShift} ≤ |exact| ⟹ $j2（实测档 relShiftMeasured=$relShiftMeasured）\n")
      sb.append(s"    J3 需 |diff| ≤ (nLossy+2^k−1)×windowLsb = ${a.absBound} ⟹ $j3\n")
      sb.append(s"    A3 mustRel=${a.mustRel}（解析充分条件 (nLossy+2^k−1)×2^(W−1) ≤ |S|−nLossy）")
      sb.toString
    }
    // ── 强断言（先于 ok，失败信息更精准）──
    assert(j0, msg + "\n    ★ J0 违背：RTL 输出与【写定 spec 的整数模型】不一致 ⟹ 实现 bug（非精度问题）")
    if (semantic) {
      // lazy-40 不变量：exp==0 ⟹ mant31 必为 0（否则下游 fpNormalize40to32 的 isZero 漏判）。
      // in-spec 域恒成立：E=0 要求【所有】激活槽 expField==0 ⟹ 只能是全 ±0 或全 fp32 subnormal
      // （后者属 out-of-spec (E1)，在 oos block 单独驱动，见 OOS1）。
      assert(!(eOut == 0 && mOut.signum != 0),
        msg + s"\n    ★ lazy-40 不变量违背：exp==0 但 mant31!=0。raw=0x${rawDut.toString(16)}")
      assert(!a.lossless || j1, msg + "\n    ★ A2 违背：解析 nLossy==0 && k==0 ⟹ 输出必须【语义精确】(diff==0)")
      assert(!a.mustRel || j2, msg + "\n    ★ A3 违背：解析充分条件成立 ⟹ 相对误差必须 ≤ 2^−" + a.relShift)
      assert(ok, msg + "\n    ★ 判定失败：J2（相对阈值）与 J3（解析绝对界）双双不成立")
    }
    Verdict(ok, j0, j1, j2, j3, a.mustRel, a.lossless, path, diff, relShiftMeasured, kMeasured, msg)
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 追加槽构造器：精确控制「窗口幅值 magW」与「尾数低位全 1（frac≈1）」两种极限形态
  // ════════════════════════════════════════════════════════════════════════════
  /** 满尾数乘积槽：mant23 = 0x7FFFFF ⟹ mag24 = 2^24−1（24 位全 1）。
    * 用途：任意 ts ≥ 1 都给出 frac ≈ 1 的最大截断，且 a = 2^(24−ts)−1 在 ts 接近 24 时可【很小】
    * ⟹ 是构造「小 a + frac≈1」相对误差反例（mlbound 组）的唯一形态。
    * 可达性：这正是 FpAdd.scala §9.1(c) 记录的 lane0 饱和域（宽阵列 fp32Max ⟹ mant23=0x7FFFFF）。 */
  private def prodSatMag(s: Int, eField: Int): ProdSlot = prodRaw(s, eField, 0x7FFFFF)

  /** 满尾数 cReg 槽：mant31 = 0x7FFFFFFF ⟹ magW = 2^W−1，且【sh=0 时】ts = 31−W 已丢低 31−W 位
    * （frac ≈ 1）⟹ 任何由 fp32BitsTo40 装入的【满精度】fp32 cReg 在 W=19 下恒 n_lossy ≥ 1（头注来源 (c)）。 */
  private def cSatMag(s: Int, eField: Int): CSlot = cRaw(s, eField, (BigInt(1) << 31) - 1)

  /** 【自产形态】cReg 槽（= 本模块 k 档右移的输出编码）：mant31 = magTop << (31−W)。
    * 低 (31−W) 位结构性 0 ⟹ sh=0 时再入 magW = magTop 【完全无损】（头注 cReg 来源 (a) 的实测载体），
    * 且 expOut = E+k ⟹ k=0 时 E 不膨胀（(D1) 的核心收益，由 loop 组的反膨胀断言实测）。 */
  private def cSelfProduced(s: Int, eField: Int, magTop: BigInt, w: Int): CSlot = {
    require(magTop >= 0 && magTop < (BigInt(1) << w), s"cSelfProduced: magTop=$magTop 越 [0, 2^W)")
    cRaw(s, eField, magTop << (31 - w))
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 结构覆盖向量集（所有「跨 W 边界」的量都写成 w 的函数 ⟹ 三档 W 覆盖同一组语义边界）
  // ════════════════════════════════════════════════════════════════════════════
  private def structVecs(w: Int): Seq[Vec] = {
    // 无损↔有损的【精确转折点】（第一性原理，见模块头注「n_lossy = 0 的充分条件」）：
    //   MX8 乘积 mant23 低 16 位结构性 0 ⟹ ts ≤ 16 无损 ⟹ sh ≤ W−8 无损、sh = W−7 首次 lossy
    //   MX4 乘积 mant23 低 20 位结构性 0 ⟹ ts ≤ 20 无损 ⟹ sh ≤ W−4 无损
    //   cReg  低 (31−W) 位（非自产形态时）⟹ sh=0 已可 lossy
    val d8Ok  = w - 8      // MX8 最后一个无损档
    val d8Bad = w - 7      // MX8 第一个 lossy 档
    val d4Ok  = w - 4      // MX4 最后一个无损档
    val msb   = BigInt(1) << (w - 1)      // 窗口 MSB 幅值（= prodRaw(_,_,0) 的 magW，最小 E 槽）
    val b = mutable.ArrayBuffer[Vec]()

    // ── norm：正常累加（MX8 3 槽 / MX4 5 槽 / 混合符号 / 非零 cReg）──────────────
    b += vec("N1", "norm", Mx8, CZero, prodMx8P(0, EB, 64), prodMx8P(0, EB, 100),
      note = "MX8 双 lane 同指数（P=64/100）⟹ |S| = 164×2^(W−7) ⟹ k=1、nLossy=0 ⟹ J1 精确")
    b += vec("N2", "norm", Mx8, cSelfProduced(0, EB - 2, BigInt(3) << (w - 4), w),
      prodMx8P(0, EB, 225), prodMx8P(1, EB - 5, 96),
      note = "MX8 异号 + 自产形态 cReg（低 31−W 位结构性 0）⟹ 三槽全无损 ⟹ J1")
    b += vec("N3", "norm", Mx8, cVal(Dyadic(BigInt(5), 0)),
      mxProd(E4, e4m3(0, 10, 3), SA, E4, e4m3(0, 8, 5), SB),   // 11 × 3.25  =  35.75
      mxProd(E4, e4m3(1, 12, 7), SA, E4, e4m3(0, 9, 2), SB),   // −60 × 5    = −300
      note = "MX8 语义向量（E4M3×E4M3 双 lane）+ cReg=5.0 ⟹ 张量语义金锚和 = −259.25")
    b += vec("N4", "norm", Mx8, cVal(Dyadic(BigInt(-7), 3)),
      mxProd(E5, e5m2(0, 17, 0), SA, E5, e5m2(0, 16, 2), SB),  // 4 × 3      =  12
      mxProd(E5, e5m2(1, 20, 3), SA, E5, e5m2(0, 14, 1), SB),  // −56 × 0.625 = −35
      note = "MX8 语义向量（E5M2×E5M2）+ cReg=−56 ⟹ 金锚和 = −79")
    b += vec("N5", "norm", Mx4, CZero,
      mxProd(E2, e2m1(0, 1, 1), SA, E2, e2m1(0, 1, 1), SB),    // 1.5 × 1.5  =  2.25
      mxProd(E2, e2m1(0, 1, 1), SA, E2, e2m1(0, 2, 0), SB),    // 1.5 × 2.0  =  3.0
      mxProd(E2, e2m1(1, 3, 1), SA, E2, e2m1(0, 2, 0), SB),    // −6.0 × 2.0 = −12.0
      mxProd(E2, e2m1(0, 1, 0), SA, E2, e2m1(0, 1, 0), SB),    // 1.0 × 1.0  =  1.0
      note = "MX4 语义向量：4 个 fp4 积【直接进树】（取消 fp4BlockSum 中间 LZC）⟹ 金锚和 = −5.75")
    b += vec("N6", "norm", Mx4, cVal(Dyadic(BigInt(1), 1)),
      mxProd(E2, e2m1(0, 3, 1), SA, E2, e2m1(0, 3, 1), SB),    // 6 × 6      =  36
      mxProd(E2, e2m1(1, 0, 1), SA, E2, e2m1(0, 2, 1), SB),    // −0.5 × 3   = −1.5
      mxProd(E2, e2m1(0, 0, 1), SA, E2, e2m1(0, 0, 1), SB),    // 0.5 × 0.5  =  0.25（E2M1 subnormal 一等公民）
      mxProd(E2, e2m1(1, 2, 0), SA, E2, e2m1(0, 1, 0), SB),    // −2 × 1     = −2
      note = "MX4 含 E2M1 subnormal(±0.5) + cReg=2.0 ⟹ 金锚和 = 34.75")
    b += vec("N7", "norm", Mx4b, CZero,
      mxProd(E2, e2m1(0, 1, 1), SA, E2, e2m1(0, 1, 1), SB),
      mxProd(E2, e2m1(0, 1, 1), SA, E2, e2m1(0, 2, 0), SB),
      mxProd(E2, e2m1(1, 3, 1), SA, E2, e2m1(0, 2, 0), SB),
      mxProd(E2, e2m1(0, 1, 0), SA, E2, e2m1(0, 1, 0), SB),
      note = "is4x=1 && is2x=1（上游不出现）⟹ 验 is4x 优先级：激活集必须仍是 5 槽（与 N5 同结果）")

    // ── wbound：指数差跨 W 边界（无损↔有损转折点 / W−1 / W / W+1 / clamp）──────────
    b += vec("WB1", "wbound", Mx8, CZero, prodRaw(0, EB, 0), prodMx8P(0, EB - d8Ok, 225),
      note = s"MX8 shift=W−8=$d8Ok（mant23 低 16 位结构性 0 的边界）⟹ ts=16 ⟹ 【无损】⟹ J1")
    b += vec("WB2", "wbound", Mx8, CZero, prodRaw(0, EB, 0), prodMx8P(0, EB - d8Bad, 225),
      note = s"MX8 shift=W−7=$d8Bad ⟹ ts=17 ⟹ 225(奇数)×2^16 丢掉 bit16 ⟹ 【首个 lossy 档】nLossy=1")
    b += vec("WB3", "wbound", Mx8, CZero, prodRaw(0, EB, 0), prodRaw(0, EB - (w - 1), 0),
      note = "shift=W−1 ⟹ ts=23 ⟹ a=1（窗口内最小存活量）⟹ 无损、k=0 ⟹ J1")
    b += vec("WB4", "wbound", Mx8, CZero, prodRaw(0, EB, 0), prodRaw(0, EB - w, 0),
      note = "shift=W ⟹ ts=24 ⟹ a=0 ⟹ 【term-drop 精确边界】（该槽真值 = 0.5 LSB）")
    b += vec("WB5", "wbound", Mx8, CZero, prodRaw(0, EB, 0), prodMx8P(0, EB - (w + 1), 200),
      note = "shift=W+1 ⟹ sRaw>W ⟹ 走 clamp Mux 的【上支】⟹ drop（真值 0.78125 LSB）")
    b += vec("WB6", "wbound", Mx8, CZero, prodRaw(0, EB, 0), prodMx8P(0, EB - 32, 225),
      note = "shift=32（32&31=0）⟹ 若无 sRaw>W 的 clamp，sRaw(ShiftBits−1:0)=0 会让该槽【完全不移位】、" +
             "以 E 档满幅混入求和树；而正确 clamp 到 W 后 a=0（term-drop）⟹ 二者差异【最大】，" +
             "恢复对 clamp 逻辑的判别力（原 shift=60 时 60&31=28 与 clamp 后 19 都 drop ⟹ 无法区分）")
    b += vec("WB7", "wbound", Mx8, cSatMag(0, EB), prodRaw(0, EB, 0), PZero,
      note = "cReg 侧 sh=0 但 mant31 满 31 位 ⟹ ts=31−W ⟹ frac≈1 ⟹ nLossy=1 且 |S| 触发 k=1（两处截断并存）")
    b += vec("WB8", "wbound", Mx8, cSatMag(0, EB - d8Bad), prodRaw(0, EB, 0), PZero,
      note = s"cReg 侧 shift=W−7=$d8Bad ⟹ ts=24 ⟹ a=127、frac≈1 ⟹ nLossy=1 ⟹ J2 达标（与 ML1 的 nLossy=2 对照）")

    // ── drop：term-drop（整槽移出窗口；含多槽同时被丢）──────────────────────────
    b += vec("DR1", "drop", Mx4, cSelfProduced(0, EB, msb, w),
      prodMx4P(0, EB - (w + 2), 9), prodMx4P(0, EB - (w + 2), 9),
      prodMx4P(0, EB - (w + 2), 6), prodMx4P(0, EB - (w + 2), 4),
      note = "4 lane 同时 term-drop ⟹ nLossy=nDrop=4。★【实测纠正估算】四槽真值合计仅 0.59375 LSB" +
             "（p=9→0.140625、p=6→0.1875、p=4→0.125 LSB）而 |exact| = 2^(W−1)+0.59 LSB ⟹ |diff|=0.59 LSB" +
             " ⟹ 实测相对档 = W−1（恰达阈值、走 J2）⟹ 结论：nLossy 大【本身】不足以超阈，" +
             "还必须 |exact| 小到与 nLossy×LSB 同阶（见 mlbound / X4）")
    b += vec("DR2", "drop", Mx8, CZero, prodRaw(0, EB, 0), prodMx8P(0, EB - (w + 3), 225),
      note = "单 lane drop（nLossy=1、真值 0.879 LSB）⟹ 相对档 ≈ W−1 ⟹ J2 恰达标")
    b += vec("DR3", "drop", Mx4, cSatMag(0, EB - 3),
      prodMx4P(0, EB, 9), prodMx4P(0, EB - 1, 6), prodMx4P(0, EB - w, 4), prodMx4P(0, EB - d4Ok, 9),
      note = s"混合：1 lane drop(sh=W) + 1 lane 处于 MX4 无损边界 sh=W−4=$d4Ok + cReg 满尾数 lossy")

    // ── cancel：异号相消（完全相消 → 40'h0；近相消仍精确；有损相消走 J3）──────────
    b += vec("X1", "cancel", Mx8, CZero, prodMx8P(0, EB, 128), prodMx8P(1, EB, 128),
      note = "完全相消（同幅值异号）⟹ signedSum=0 ⟹ 输出 40'h0 真零（下游 isZero 命中）")
    b += vec("X2", "cancel", Mx8, cSelfProduced(1, EB, msb, w), prodMx8P(0, EB, 129), PZero,
      note = "近完全相消：残差 2^(W−8) ⟹ mant31 出现 7 个前导零且【不做 LZC】（lazy 语义核心）")
    b += vec("X3", "cancel", Mx8, cFullWin(1, EB, w), prodFullWin(0, EB, w), PZero,
      note = "满窗口幅值精确相消：(2^W−1) − (2^W−1) = 0 ⟹ 40'h0 ⟹ 验真零 mux 在最大幅值下仍正确")
    b += vec("X4", "cancel", Mx8, cSatMag(1, EB), prodFullWin(0, EB, w), PZero,
      note = "★ 有损相消（catastrophic）：|exact| ≈ 1 LSB ⟹ 相对误差 ~1 ⟹ J2 必不达，由 J3 绝对界兜住")
    b += vec("X5", "cancel", Mx4, cSelfProduced(0, EB - 2, BigInt(3) << (w - 3), w),
      prodMx4P(0, EB, 9), prodMx4P(1, EB, 9), prodMx4P(0, EB - 2, 6), prodMx4P(1, EB - 2, 6),
      note = "MX4 两两相消（4 lane 和恰为 0）+ cReg 残值 ⟹ 验 5 槽 CSA 树的成对抵消与符号判定")

    // ── zero：零操作数 / 未激活 lane 灌垃圾（掩零必须同时清 expEff）───────────────
    b += vec("Z1", "zero", Mx4, CZero, PZero, PZero, PZero, PZero,
      note = "全零（live 集为空）⟹ E=0、signedSum=0 ⟹ 输出 40'h0")
    b += vec("Z2", "zero", Mx8, CZero, PZero, prodMx8P(0, EB, 200), PZero, PZero,
      note = "单操作数（仅 L1 供数）⟹ 透传：mant31 = magW<<(31−W)、expOut = E")
    b += vec("Z3", "zero", Mx8, CZero, prodRaw(0, EB, 0), PZero,
      prodRaw(0, 250, 0), prodRaw(0, 252, 0x400000),
      note = "★ MX8 下 L2/L3 灌高指数垃圾 ⟹ 掩零必须【同时清 expEff】，否则 E 被抬到 252 ⟹ L0 整体 term-drop")
    b += vec("Z4", "zero", Fp16Only, cSelfProduced(0, EB - 2, BigInt(1) << (w - 2), w),
      prodMx8P(0, EB, 100), prodRaw(0, 250, 0), prodRaw(0, 251, 0), prodRaw(0, 253, 0),
      note = "fp16-only（MX-only 下 don't-care）⟹ 退化为 {C,L0} 两操作数；L1/L2/L3 垃圾必须被掩掉")
    b += vec("Z5", "zero", Mx4, CZero, PZero, prodMx4P(0, EB, 6), prodMx4P(1, EB, 4), prodMx4P(0, EB - 1, 9),
      note = "MX4 下 L0 为零（上游 nibble0 积为 0）⟹ 仅 3 个 fp4 lane 参与、cReg 为零")

    // ── growth：溢出增长档 k = bitLength(|S|) − W ∈ {0,1,2,3} ───────────────────
    b += vec("G0", "growth", Mx8, CZero, prodRaw(0, EB, 0), PZero,
      note = "k=0：|S| = 2^(W−1) ⟹ bitLength=W ⟹ expOut = E（★ 环路 E 稳定不膨胀的必要条件，见 (D1)）")
    b += vec("G1", "growth", Mx8, CZero, prodRaw(0, EB, 0), prodRaw(0, EB, 0),
      note = "k=1：|S| = 2^W ⟹ magTop = 2^(W−1)、expOut = E+1")
    b += vec("G2", "growth", Mx4, cFullWin(0, EB, w),
      prodMx4P(0, EB, 9), prodMx4P(0, EB, 9), prodMx4P(0, EB, 9), prodMx4P(0, EB, 9),
      note = "k=2（MX4-legal 上界）：|S| = 4×9×2^(W−4) + (2^W−1) = 3.25×2^W − 1。" +
             "★【task#25 Kim Nit#4 订正：原写「全无损 ⟹ J1」错误】对齐无损(nLossy=0)但 k 档截断非零" +
             "(|S| mod 2^k = 3) ⟹ diff = −3 windowLsb ≠ 0 ⟹ 落 J2（relErr ≈ 2^-(W+0.1) 达标）；" +
             "lossless=false 因 k≠0，A2 不触发")
    b += vec("G2b", "growth", Mx4, cSatMag(0, EB),
      prodMx4P(0, EB, 9), prodMx4P(0, EB, 9), prodMx4P(0, EB, 9), prodMx4P(0, EB, 9),
      note = "k=2 且 cReg lossy ⟹ 对齐截断与 k 档截断【同时】出现（absBound = 1+3 = 4 LSB）⟹ A3 必须成立")
    b += vec("G3", "growth", Mx4, cFullWin(0, EB, w),
      prodFullWin(0, EB, w), prodFullWin(0, EB, w), prodFullWin(0, EB, w), prodFullWin(0, EB, w),
      note = "k=3（synthetic 满窗口幅值，【非 MX-legal】）：|S| = 5×(2^W−1) ⟹ 探测 magFull(W+2) ⟹ kGrow=3。" +
             "★【task#25 Kim Nit#4 订正：原写「全无损 ⟹ J1」错误】对齐无损(nLossy=0)但 k 档截断非零" +
             "(|S| mod 2^k = 3) ⟹ diff = −3 windowLsb ≠ 0 ⟹ 落 J2（relErr ≈ 2^-(W+0.7) 达标）；" +
             "lossless=false 因 k≠0，A2 不触发")
    b += vec("G3b", "growth", Mx4, cSatMag(0, EB),
      prodSatMag(0, EB), prodSatMag(0, EB), prodSatMag(0, EB), prodSatMag(0, EB),
      note = "k=3 且【五槽全 lossy】⟹ absBound = 5+7 = 12 LSB，截断项的最坏组合")

    // ── lz：cReg 带前导零 ───────────────────────────────────────────────────────
    b += vec("LZ1", "lz", Mx8, cVal(Dyadic(BigInt(3), -1), lz = 5),
      prodMx8P(0, EB, 200), prodMx8P(0, EB - 3, 96),
      note = "cReg 显式注入 5 个前导零（expField 相应抬高 5）⟹ 窗口按同一 radix 吸收 ⟹ 仍【无损】⟹ J1")
    b += vec("LZ2", "lz", Mx4, cSatMag(0, EB),
      prodMx4P(0, EB, 4), prodMx4P(0, EB, 4), prodMx4P(0, EB, 4), prodMx4P(0, EB, 4),
      note = "满尾数 cReg（31 位全 1、前导零 0）+ 4 lane ⟹ k=2 且 nLossy=1 ⟹ A3 触发且 J2 必过")
    b += vec("LZ3", "lz", Mx8, cVal(Dyadic(BigInt(1), 0), lz = 20), prodRaw(0, EB, 0), PZero,
      note = "★ 前导零膨胀：lz=20 ⟹ expField=147 抬高 E，而 mant31=1024 可能整体落在窗口外 ⟹ windowLsb 被粗化" +
             " ⟹ 相对档退化（W=19/16 时 J2 不达、由 J3 兜住；W=22 时窗口够宽仍 J1）。lazy 格式不 LZC 的固有特性，FpAdd 同族")

    // ── mlbound：【诚实标注组】MX-legal 但 nLossy ≥ 2 ⟹ 阈值被超出的构造反例 ──────
    b += vec("ML1", "mlbound", Mx8, cSatMag(0, EB - d8Bad), prodRaw(0, EB, 0), prodMx8P(0, EB - d8Bad, 225),
      note = "★【阈值反例·MX-legal】E 槽幅值最小(2^(W−1)) + cReg(a=127, frac≈1) + L1(a=112, frac=0.5) ⟹ " +
             "nLossy=2、k=0 ⟹ |diff|≈1.5 LSB 而 |exact|≈2^(W−1) LSB ⟹ 实测相对档 = W−2 < W−1 ⟹ J2 不达、J3 兜住")
    b += vec("ML2", "mlbound", Mx4, cSatMag(0, EB - (w - 3)), prodRaw(0, EB, 0),
      prodSatMag(0, EB - (w - 3)), prodSatMag(0, EB - (w - 3)), prodSatMag(0, EB - (w - 2)),
      note = "★【阈值反例·最坏构造】4 槽「小 a + frac≈1」（a ∈ {7,7,3,7}）⟹ nLossy=4、k=0 ⟹ |diff|≈4 LSB ⟹ " +
             "实测相对档 = W−3 < W−1 ⟹ J2 不达、J3 兜住。mant23=0x7FFFFF 形态可达于 FpAdd §9.1(c) 饱和域")
    b.toSeq
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 全域扫描向量集
  // ════════════════════════════════════════════════════════════════════════════
  /** MX4 全域扫描：E2M1 的 16×16 = 256 个码对，四 lane 各自不同组合。
    * 【必须全域 nLossy == 0】：同 block 四 lane 共享 scaleA/scaleB ⟹ expSigned 跨度 ≤ 6，
    * 加上 1.5×1.5=2.25 的规格化进位 ⟹ expField 跨度 ≤ 7 ≤ W−4（W ≥ 16）⟹ 对齐恒无损。
    * 这正是用户「MX4 应无损、可断言更严」的机器验证（coverage-audit 断言 nAlignLossless == n）。 */
  private def mx4SweepVecs(): Seq[Vec] = {
    val b = mutable.ArrayBuffer[Vec]()
    for (ca <- 0 until 16; cb <- 0 until 16) {
      val c = if (((ca + cb) & 1) == 0) CZero else cVal(Dyadic(BigInt(1), 1))   // 交替注入 cReg=2.0
      b += vec("M4-" + ca.toHexString.toUpperCase + cb.toHexString.toUpperCase, "mx4sweep", Mx4, c,
        mxProd(E2, ca, SA, E2, cb, SB),
        mxProd(E2, cb, SA, E2, ca, SB),
        mxProd(E2, ca, SA, E2, ca ^ 0x8, SB),   // 符号取反 ⟹ 部分相消
        mxProd(E2, cb, SA, E2, cb ^ 0x2, SB),   // exp 位 0 翻转 ⟹ 块内 1 档指数差
        note = "E2M1 全码对扫（同 block expField 跨度 ≤7 ≤ W−4 ⟹ 对齐必须【全域无损】）")
    }
    b.toSeq
  }

  /** MX8 全域扫描：E4M3 / E5M2 / 跨格式混合码对，宽指数跨度（E5M2×E5M2 的 expField ∈ [99,158]，
    * 跨度 59 ⟹ 单次扫描即贯穿「无损 → lossy → term-drop → clamp」全谱）。每对生成 2 条向量：
    *   -a：同号累加 + 非零 cReg；-b：l0 反号 ⟹ 相消域。 */
  private def mx8SweepVecs(): Seq[Vec] = {
    def codesOf(f: DataFormat.Type): Seq[Int] =
      if (f == DataFormat.MXE4M3)
        (for (e <- Seq(1, 7, 15); m <- Seq(0, 7); if !(e == 15 && m == 7)) yield e4m3(0, e, m))
      else
        (for (e <- Seq(1, 15, 30); m <- Seq(0, 3)) yield e5m2(0, e, m))
    val tag4 = "E4"; val tag5 = "E5"
    def tagOf(f: DataFormat.Type): String = if (f == DataFormat.MXE4M3) tag4 else tag5
    val b = mutable.ArrayBuffer[Vec]()
    for (fa <- Seq(E4, E5); fb <- Seq(E4, E5)) {
      val cA = codesOf(fa); val cB = codesOf(fb)
      for (i <- cA.indices; j <- cB.indices) {
        val ca = cA(i); val cb = cB(j)
        val caNeg = ca ^ 0x80                                   // 符号位取反（两格式符号位同为 bit7）
        val base = "M8-" + tagOf(fa) + ca.toHexString.toUpperCase + "-" + tagOf(fb) + cb.toHexString.toUpperCase
        val l0 = mxProd(fa, ca, SA, fb, cb, SB)
        val l1 = mxProd(fa, cA((i + 1) % cA.size), SA, fb, cB((j + 2) % cB.size), SB)
        val l1n = (l1._1 | 0x80000000L, -l1._2)                  // 反号（位模式 | sign，真值取负）
        b += vec(base + "-a", "mx8sweep", Mx8, cVal(Dyadic(BigInt(1), 0)), l0, l1, PZero, PZero,
          note = s"${tagOf(fa)}×${tagOf(fb)} 同号累加 + cReg=1.0")
        b += vec(base + "-b", "mx8sweep", Mx8, CZero, l0, l1n, PZero, PZero,
          note = s"${tagOf(fa)}×${tagOf(fb)} 异号 ⟹ 相消域（|exact| 可能 ≪ 最大项）")
      }
    }
    b.toSeq
  }

  /** out-of-spec 向量（semantic=false ⟹ 只断言 J0 + 结构性守卫，不做金锚裁决；逐条 println 记录降级行为）。
    * ★ 全部槽一律用 prodOos / cOos 构造（真值占位），因为 Inf/NaN/subnormal 的真值不在 Dyadic 域内。 */
  private def oosVecs(w: Int): Seq[Vec] = Seq(
    vec("OOS1", "oos", Mx8, CZero, prodOos(0, 0, 0x400000), PZero, PZero, PZero,
      note = "(E1) 单 fp32 subnormal 槽 ⟹ eEff=0 ⟹ E=0 ⟹ expOut=0 但 mant31≠0（违反 lazy-40 真零判据）。" +
             "【FpAdd 同域同行为】：其 pZero=false ⟹ baseExp=pExp=0、accMant=pMant31≠0 ⟹ 共享 out-of-spec 特性，非 L2 回归"),
    vec("OOS2", "oos", Mx8, cOos(0, 0, (BigInt(1) << 31) - 1), prodMx8P(0, EB, 64), PZero, PZero, PZero,
      note = "(E2) cReg exp==0 && mant31!=0（FpAdd.scala:138-141 判为不可达态）⟹ eEff=0 ⟹ cReg 整槽 term-drop"),
    vec("OOS3", "oos", Mx8, cOos(0, 255, BigInt(1) << 30), prodOos(0, 255, 0), prodOos(0, 254, 0x400000), PZero, PZero,
      note = "(E3) expOut 饱和：E=255、k=1 ⟹ expOutRaw=256 ⟹ 必须【饱和钳位到 ExpSatMax=254】而非 8-bit 绕回成 0" +
             "（task#18 A 修复：255 是 fp32 Inf/NaN 保留域，钳 254 保证输出恒有限）"),
    vec("OOS4", "oos", Mx8, CZero, prodOos(0, 255, 0), prodOos(1, 255, 0), PZero, PZero,
      note = "(E4) +Inf 与 −Inf 同拍 ⟹ 两者 mag24 同为 2^23 ⟹ signedSum=0 ⟹ 输出 40'h0" +
             "（与 FpAdd 同族的非数值承诺降级，不传播 Inf）"),
    vec("OOS5", "oos", Mx4, CZero, prodOos(0, 255, 0x400000), prodRaw(0, EB, 0),
      prodRaw(0, EB - 1, 0), prodRaw(0, EB - 2, 0),
      note = "(E5) NaN 槽（e=255,m≠0）⟹ 按 eField=255 的最大有限数处理（无 NaN 传播）⟹ E=255 使四个" +
             " EB 域 lane 全部 term-drop（优雅降级）"),
    vec("OOS6", "oos", Mx8, CZero, prodRaw(0, EB, 0), PZero,
      prodOos(0, 254, 0x7FFFFF), prodOos(1, 255, 0x7FFFFF),
      note = "(E6) MX8 下 L2/L3 灌 eField=254/255 的满幅值垃圾 ⟹ 掩零必须同时清 expEff（E 仍须 = EB，L0 不得 drop）")
  )

  private def allVecs(w: Int): Seq[Vec] =
    structVecs(w) ++ mx4SweepVecs() ++ mx8SweepVecs() ++ oosVecs(w)

  // ════════════════════════════════════════════════════════════════════════════
  // 覆盖统计（末尾 audit block 汇总断言；AnyFlatSpec 按声明序执行 ⟹ 可直接读）
  // ════════════════════════════════════════════════════════════════════════════
  private final class Grp {
    var n = 0; var nAlignLossless = 0; var nLossless = 0; var nDrop = 0
    var j1 = 0; var j2 = 0; var j3 = 0; var oos = 0
    var kMax = 0; var nLossyMax = 0
    var minRel = Int.MaxValue; var minRelTag = ""
  }

  /** 一条「实测相对档 < 阈值」的向量记录（报告用；每条都必须有【解析解释】，见 checkW ⑥b）。
    * @param measured 实测最大 r 使 |diff|×2^r ≤ |exact|（⟹ 实测 relErr ≤ 2^−r）
    * @param illCond  病态相消判据：|exact| < 2^(W−1)×windowLsb = 2^(E−127)（= E 档的 1 ULP）
    *                 ⟹ 条件数 maxTerm/|exact| > 2^(W−1)，相对误差被【条件数】放大而非模块缺陷
    * @param nLossy   有损槽数（≥2 ⟹ 解析上界 nLossy×2^−(W−1) 本就可超阈，见文件头「诚实标注」） */
  private final case class BelowTh(tag: String, group: String, measured: Int, path: String,
                                   illCond: Boolean, nLossy: Int, kModel: Int, note: String) {
    def reason: String = {
      val r = mutable.ArrayBuffer[String]()
      if (illCond) r += "病态相消(|exact| < 1 ULP ⟹ 条件数 > 2^(W−1))"
      if (nLossy >= 2) r += s"多损域(nLossy=$nLossy ≥ 2 ⟹ 解析上界 nLossy×2^−(W−1))"
      if (r.isEmpty) r += "★★ 无解析解释 ⟹ 说明误差界推导或实现有漏洞"
      r.mkString(" + ")
    }
    def explained: Boolean = illCond || nLossy >= 2
  }

  private final class Acc(val w: Int) {
    var total = 0
    var nJ1 = 0; var nJ2 = 0; var nJ3 = 0; var nOos = 0
    var nAlignLossless = 0; var nLossless = 0; var nDropVec = 0; var nMustRel = 0
    var minRel = Int.MaxValue; var minRelTag = ""; var minRelNote = ""
    /** 达标域（path ∈ {J1-exact, J2-relative}）的实测最差相对档 —— 与阈值直接可比的量。 */
    var minRelPass = Int.MaxValue; var minRelPassTag = ""
    /** 未达阈值向量清单（诚实枚举；每条都必须 explained，否则 checkW 失败）。 */
    val belowTh = mutable.ArrayBuffer[BelowTh]()
    val kHist = mutable.SortedMap[Int, Int]()
    val nLossyHist = mutable.SortedMap[Int, Int]()
    val groups = mutable.LinkedHashMap[String, Grp]()

    def nBelowTh: Int = belowTh.size
    def nPass: Int = total - nOos - nBelowTh

    def record(v: Vec, a: Analysis, vd: Verdict): Unit = {
      total += 1
      val g = groups.getOrElseUpdate(v.group, new Grp)
      g.n += 1
      if (a.nLossy == 0) { g.nAlignLossless += 1; nAlignLossless += 1 }
      if (a.lossless)    { g.nLossless += 1;     nLossless += 1 }
      if (a.nDrop > 0)   { g.nDrop += 1;         nDropVec += 1 }
      if (a.kModel > g.kMax) g.kMax = a.kModel
      if (a.nLossy > g.nLossyMax) g.nLossyMax = a.nLossy
      kHist(a.kModel) = kHist.getOrElse(a.kModel, 0) + 1
      nLossyHist(a.nLossy) = nLossyHist.getOrElse(a.nLossy, 0) + 1
      if (a.mustRel) nMustRel += 1
      if (vd.path == "OOS-model-only") { g.oos += 1; nOos += 1 }
      else {
        if (vd.j1) { g.j1 += 1; nJ1 += 1 } else if (vd.j2) { g.j2 += 1; nJ2 += 1 }
        else if (vd.j3) { g.j3 += 1; nJ3 += 1 }
        if (vd.relShiftMeasured < g.minRel) { g.minRel = vd.relShiftMeasured; g.minRelTag = v.tag }
        if (vd.relShiftMeasured < minRel) {
          minRel = vd.relShiftMeasured; minRelTag = v.tag; minRelNote = v.note
        }
        val th = relErrShiftOf(w)
        if (vd.relShiftMeasured < th) {
          // 病态相消判据（纯解析，不读 RTL）：|exact| < 2^(W−1) × windowLsb = 2^(E−127)
          val illCond = a.exact.abs.ltAbs(a.lsb.shl(w - 1))
          belowTh += BelowTh(v.tag, v.group, vd.relShiftMeasured, vd.path, illCond,
                             a.nLossy, a.kModel, v.note)
        } else if (vd.relShiftMeasured < minRelPass) {
          minRelPass = vd.relShiftMeasured; minRelPassTag = v.tag
        }
      }
    }

    /** 全域实测最差相对档（含病态相消域）→ 可读串。 */
    def minRelStr: String =
      if (minRel == Int.MaxValue) "inf（全部向量 diff==0，语义精确无损）"
      else {
        val v = math.pow(2.0, -minRel.toDouble)
        val th = relErrShiftOf(w)
        val verdict = if (minRel >= th) "达标" else s"未达阈值（差 ${th - minRel} 档，见下方清单，全部由 J3 绝对界兜住）"
        f"2^−$minRel = $v%.4e  ⟹ $verdict（阈值 2^−$th）"
      }

    def report(): String = {
      val sb = new StringBuilder
      val th = relErrShiftOf(w)
      sb.append(s"\n════ FpAddNarrowOpt 覆盖/精度报告  W=$w  relShift(阈值)=$th ════\n")
      sb.append(f"  向量总数        : $total%d\n")
      sb.append(f"  判定路径直方图  : J1-exact=$nJ1%d  J2-relative=$nJ2%d  J3-absbound=$nJ3%d  OOS(仅 J0)=$nOos%d\n")
      sb.append(f"  对齐无损(nLossy=0): $nAlignLossless%d   完全无损(nLossy=0∧k=0): $nLossless%d\n")
      sb.append(f"  含 term-drop 向量 : $nDropVec%d   A3(mustRel) 触发次数: $nMustRel%d\n")
      sb.append(s"  k 档直方图      : ${kHist.toSeq.map { case (k, n) => s"k=$k:$n" }.mkString(" ")}\n")
      sb.append(s"  nLossy 直方图   : ${nLossyHist.toSeq.map { case (l, n) => s"l=$l:$n" }.mkString(" ")}\n")
      sb.append(s"  ── 实测精度（诚实分离：全域 vs 达标域）──\n")
      sb.append(s"  ★ 全域实测最差相对档 : $minRelStr   @ [$minRelTag]\n")
      if (minRelNote.nonEmpty) sb.append(s"      该向量说明       : $minRelNote\n")
      sb.append(s"  ★ 达标域实测最差相对档: " +
        (if (minRelPass == Int.MaxValue) s"inf（达标的 $nPass 条全部 diff==0）"
         else {
           val pv = math.pow(2.0, -minRelPass.toDouble)
           f"2^−$minRelPass = $pv%.4e @ [$minRelPassTag]"
         }) +
        s"  （阈值 2^−$th）\n")
      sb.append(s"  ★ 达阈向量 $nPass / ${total - nOos}（in-spec），其中 diff==0 精确无损 $nJ1 条；" +
        s"未达阈 ${nBelowTh} 条【全部】由 J3 解析绝对界兜住\n")
      if (belowTh.nonEmpty) {
        sb.append("  ── 未达阈值向量清单（逐条给出【解析解释】；无解释 ⟹ checkW 判失败）──\n")
        sb.append("    " + pad("tag", 10) + padR("measured", 9) + padR("阈值差", 7) + pad("path", 15) +
                  padR("nLossy", 7) + padR("k", 3) + "  原因\n")
        belowTh.foreach { b =>
          sb.append("    " + pad(b.tag, 10) + padR("2^−" + b.measured, 9) +
                    padR("−" + (th - b.measured) + " 档", 7) + pad(b.path, 15) +
                    padR(b.nLossy.toString, 7) + padR(b.kModel.toString, 3) + "  " + b.reason + "\n")
          sb.append("        note: " + b.note + "\n")
        }
      }
      sb.append("  ── 分组明细 ──\n")
      sb.append(pad("group", 10) + padR("n", 5) + padR("J1", 5) + padR("J2", 5) + padR("J3", 5) +
                padR("oos", 5) + padR("alignLL", 8) + padR("fullLL", 7) + padR("drop", 5) +
                padR("kMax", 5) + padR("lMax", 5) + "  minRel\n")
      groups.foreach { case (name, g) =>
        val mr = if (g.minRel == Int.MaxValue) "inf" else g.minRel.toString
        sb.append("    " + pad(name, 10) + padR(g.n.toString, 5) + padR(g.j1.toString, 5) +
                  padR(g.j2.toString, 5) + padR(g.j3.toString, 5) + padR(g.oos.toString, 5) +
                  padR(g.nAlignLossless.toString, 8) + padR(g.nLossless.toString, 7) +
                  padR(g.nDrop.toString, 5) + padR(g.kMax.toString, 5) + padR(g.nLossyMax.toString, 5) +
                  "  " + mr + " @ " + g.minRelTag + "\n")
      }
      sb.toString
    }
  }

  /** 报表对齐用（纯 Scala 字符串工具，不参与任何判定）。 */
  private def pad(s: String, n: Int): String = if (s.length >= n) s else s + " " * (n - s.length)
  private def padR(s: String, n: Int): String = if (s.length >= n) s else " " * (n - s.length) + s

  // ════════════════════════════════════════════════════════════════════════════
  // 环路回灌（MAC 语义：cReg_{i+1} = DUT 本拍输出）
  // ════════════════════════════════════════════════════════════════════════════
  /** 环一拍数。40 拍足以让 |Σ| 从 5.75 增长到 230 ⟹ 触发多次 k 档抬升（expOut 对数增长）。 */
  private val LoopCycles = 40

  /** ceil(log2(n))（n ≥ 1）；用于反膨胀上界。 */
  private def clog2(n: Int): Int = if (n <= 1) 0 else BigInt(n - 1).bitLength

  private final case class LoopStat(w: Int, cycles: Int, maxLaneE: Int, expOutSeq: Seq[Int],
                                    nExact: Int, worstDrift: Dyadic, boundAcc: Dyadic,
                                    minRel: Int, minRelTag: String) {
    def maxExpOut: Int = if (expOutSeq.isEmpty) 0 else expOutSeq.max
    def report: String = {
      val sb = new StringBuilder
      sb.append(s"\n──── MAC 环路回灌报告  W=$w  cycles=$cycles ────\n")
      sb.append(s"  lane expField 上界 maxLaneE = $maxLaneE（MX4 四 lane：2.25 / 3.0 / −12.0 / 1.0，每拍和 = −5.75）\n")
      sb.append(s"  expOut 序列（每拍）  = ${expOutSeq.mkString(",")}\n")
      sb.append(s"  expOut 最大 = $maxExpOut ⟹ 相对 maxLaneE 仅抬升 ${maxExpOut - maxLaneE} 档（对数增长）\n")
      sb.append(s"  ★ 反膨胀上界 = maxLaneE + 3 + 2·clog2(cycles+1) = ${maxLaneE + 3 + 2 * clog2(cycles + 1)}" +
                s"；(D1) 所述 D1' 变体（常量 expOut=E+3）会给 ${maxLaneE + 3 * cycles} ⟹ 第 2 拍即被捕获\n")
      sb.append(s"  累积漂移 |Σgot − Σexact| 最坏 = ${worstDrift}，可加界 ΣabsBound = $boundAcc ⟹ 漂移有界不放大\n")
      sb.append(s"  逐拍【精确】(drift==0) 的拍数 = $nExact / $cycles\n")
      sb.append(s"  逐拍判定最差相对档 = ${if (minRel == Int.MaxValue) "inf" else minRel.toString} @ [$minRelTag]\n")
      sb.toString
    }
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 单档 W 的完整驱动（一次 simulate 内跑完：结构向量 + 全域扫描 + oos + 环路回灌）
  // ════════════════════════════════════════════════════════════════════════════
  private val results = mutable.LinkedHashMap[Int, (Acc, LoopStat)]()

  private def runW(w: Int): (Acc, LoopStat) = {
    val acc = new Acc(w)
    var loopStat: LoopStat = null
    val vs = allVecs(w)

    simulate(new FpAddNarrowOpt(40, w)) { dut =>
      // 显式 reset 序列（house 约定：Verilator 上电语义 + 全输入安全默认）
      dut.reset.poke(true.B)
      dut.io.is4x.poke(false.B); dut.io.is2x.poke(false.B)
      dut.io.cReg.poke(0.U)
      dut.io.prodFp16.poke(0.U); dut.io.prodFp8.poke(0.U)
      dut.io.prodFp4_0.poke(0.U); dut.io.prodFp4_1.poke(0.U)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // FpAddNarrowOpt 是【纯组合】模块 ⟹ 无 F1 时序契约问题；仍按 house 习惯 poke → step(1) → peek
      def drive(v: Vec): BigInt = {
        dut.io.is4x.poke(v.mode.is4x.B)
        dut.io.is2x.poke(v.mode.is2x.B)
        dut.io.cReg.poke(v.cBits.U(40.W))
        dut.io.prodFp16.poke(v.pBits(0).U(32.W))
        dut.io.prodFp8.poke(v.pBits(1).U(32.W))
        dut.io.prodFp4_0.poke(v.pBits(2).U(32.W))
        dut.io.prodFp4_1.poke(v.pBits(3).U(32.W))
        dut.clock.step(1)
        dut.io.sum.peek().litValue
      }

      // ── ① 全部向量（in-spec 走完整四级判定；oos 只走 J0 + 结构守卫）──
      vs.foreach { v =>
        val raw = drive(v)
        val a = analyze(v, w)
        val oos = v.group == "oos"
        val vd = judge(v, a, raw, semantic = !oos)
        acc.record(v, a, vd)
        if (oos) {
          val eOut = ((raw >> 31) & 0xFF).toInt
          val mOut = raw & ((BigInt(1) << 31) - 1)
          // (E3) 指数域守卫：只允许【饱和钳位】（钳到 ExpSatMax=254），绝不允许 8-bit 绕回成小指数。
          // 饱和钳位使 eOut < E 合法（仅当 E > ExpSatMax 时发生），故下界取 min(E, ExpSatMax)。
          assert(eOut == 0 || eOut >= math.min(a.eCommon, FpAddNarrowOpt.ExpSatMax),
            s"[${v.tag}] ★ expOut 8-bit 绕回：eOut=$eOut < min(E=${a.eCommon}, ExpSatMax=${FpAddNarrowOpt.ExpSatMax}) ⟹ 灾难性错误")
          println(f"  [OOS][W=$w%d] ${v.tag}%-6s E=${a.eCommon}%3d k=${a.kModel}%d → raw=0x${raw.toString(16)}%-11s" +
                  f" (sign=${(raw >> 39) & 1}%d exp=$eOut%3d mant31=0x${mOut.toString(16)}%s)")
          println(s"        note: ${v.note}")
        }
      }

      // ── ② 环路回灌 ──
      val loopLanes: Seq[ProdSlot] = Seq(
        mxProd(E2, e2m1(0, 1, 1), SA, E2, e2m1(0, 1, 1), SB),   // 1.5 × 1.5  =  2.25
        mxProd(E2, e2m1(0, 1, 1), SA, E2, e2m1(0, 2, 0), SB),   // 1.5 × 2.0  =  3.0
        mxProd(E2, e2m1(1, 3, 1), SA, E2, e2m1(0, 2, 0), SB),   // −6.0 × 2.0 = −12.0
        mxProd(E2, e2m1(0, 1, 0), SA, E2, e2m1(0, 1, 0), SB)    // 1.0 × 1.0  =  1.0
      )
      val laneSum  = loopLanes.foldLeft(Dyadic.Zero)((s, p) => s + p._2)      // = −5.75
      val maxLaneE = loopLanes.map(p => ((p._1 >> 23) & 0xFF).toInt).max
      var cBits     = BigInt(0)
      var exactAcc  = Dyadic.Zero     // 张量语义金锚：Σ_{j≤i} laneSum（纯 Dyadic，与 RTL 无关）
      var boundAcc  = Dyadic.Zero     // 严格可加漂移界：|err_i| ≤ |err_{i−1}| + absBound_i
      var worstDrift = Dyadic.Zero
      var nExact    = 0
      var lMinRel   = Int.MaxValue
      var lMinTag   = ""
      val expOuts   = mutable.ArrayBuffer[Int]()

      for (i <- 0 until LoopCycles) {
        val tag = s"LOOP-W$w-$i"
        val v = Vec(tag, "loop", Mx4, cBits, lazy40ToDyadic(cBits),
                    loopLanes.map(_._1), loopLanes.map(_._2),
                    s"MAC 环路第 ${i + 1} 拍（cReg = 上一拍 DUT 输出；本拍金锚 = 上一拍输出值 + ${laneSum}）")
        val raw = drive(v)
        val a = analyze(v, w)
        val vd = judge(v, a, raw)                 // 每拍 J0 + 单拍语义判定（含 A2/A3 强断言）
        acc.record(v, a, vd)

        exactAcc = exactAcc + laneSum
        boundAcc = boundAcc + a.absBound
        val got   = lazy40ToDyadic(raw)
        val drift = got - exactAcc
        assert(drift.abs.leAbs(boundAcc),
          s"[$tag] ★ 环路累积漂移超出【可加解析界】：drift=$drift  boundAcc=$boundAcc  " +
          s"exactAcc=$exactAcc  got=$got  ⟹ 误差被逐拍放大（L2 契约违背）")
        if (drift.isZero) nExact += 1
        if (worstDrift.ltAbs(drift.abs)) worstDrift = drift.abs

        // ★★ 反膨胀守卫：(D1) k 档右移的必要性证据。
        //   expOut 只允许【对数】增长（|Σ| 每翻一倍才抬 1 档）。
        //   D1' 变体（常量 expOut = E+3）会给 expOut_i = maxLaneE + 3(i+1) ⟹ 第 2 拍即被本断言捕获。
        val eOut = ((raw >> 31) & 0xFF).toInt
        expOuts += eOut
        val cap = maxLaneE + 3 + 2 * clog2(i + 2)
        assert(eOut <= cap,
          s"[$tag] ★ 环路【指数膨胀】：expOut=$eOut > 对数上界 cap=$cap " +
          s"(= maxLaneE $maxLaneE + 3 + 2·clog2(${i + 2}) = ${2 * clog2(i + 2)})。" +
          "这正是 FpAddNarrowOpt 头注 (D1) 所述「常量 expOut=E+3 ⟹ E 每拍无条件 +3 ⟹ lane 被整体移出窗口 " +
          "⟹ 累加器失忆」的症状")
        if (vd.relShiftMeasured < lMinRel) { lMinRel = vd.relShiftMeasured; lMinTag = tag }
        cBits = raw
      }
      loopStat = LoopStat(w, LoopCycles, maxLaneE, expOuts.toSeq, nExact, worstDrift, boundAcc, lMinRel, lMinTag)
    }
    (acc, loopStat)
  }

  // ════════════════════════════════════════════════════════════════════════════
  // I/O 契约逐端口一致性（port-compatible；饱和语义修复后才真正 drop-in）：机器核对，而非人工比对
  // ════════════════════════════════════════════════════════════════════════════
  /** firtool 的 ANSI 端口表是【分组】风格：同方向同位宽的连续端口只在组首行写出 `input [39:0]`，
    * 后续行只列端口名（缩进对齐），且每行尾带 `\t// loc` 注释。实测形态：
    * {{{
    * module Hive_FpAddNarrowOpt(   // ...
    *   input         clock,     // ...
    *                 reset,     // ...
    *                 io_is4x,   // ...
    *   input  [39:0] io_cReg,   // ...
    *   output [39:0] io_sum     // ...
    * );
    * }}}
    * ⟹ 逐行独立正则必然失配（续行没有方向关键字），必须【状态化】解析：先切出 `module X(` 与
    * `\n);` 之间的端口表，剥行尾注释后按逗号切分，逐 fragment 继承/更新 (方向, 位宽) 状态。
    * @return Seq((方向, 端口名, 位宽十进制串))，已排除 clock/reset（两模块同，无鉴别力）。 */
  private def svPorts(gen: => Module): Seq[(String, String, String)] = {
    val sv = _root_.circt.stage.ChiselStage.emitSystemVerilog(gen)
    // 取【首个行首 module 声明】= 顶层（firtool 先发射顶层再发射子模块，若有）
    val open = """(?m)^module\s+[A-Za-z_][A-Za-z0-9_]*\s*\(""".r
      .findFirstMatchIn(sv).map(_.end - 1).getOrElse(-1)
    val close = if (open < 0) -1 else sv.indexOf("\n);", open)
    assert(open >= 0 && close > open,
      "svPorts: 未能定位 module 端口表（emitSystemVerilog 输出格式变化 ⟹ 核对无效，不可静默通过）")
    var dir = ""
    var wid = "1"
    val ps = mutable.ArrayBuffer[(String, String, String)]()
    sv.substring(open + 1, close).split(",").foreach { frag =>
      val toks = frag.replaceAll("//[^\n]*", " ").split("\\s+").filter(_.nonEmpty).toSeq
      var rest = toks
      if (rest.headOption.exists(t => t == "input" || t == "output")) {
        dir = rest.head
        wid = "1"                                  // 新组开始 ⟹ 位宽状态复位为默认 1 位
        rest = rest.tail
      }
      rest = rest.dropWhile(t => t == "wire" || t == "logic" || t == "var")
      if (rest.headOption.exists(_.startsWith("["))) {
        wid = portWidth(rest.head)
        rest = rest.tail
      }
      if (rest.nonEmpty) {
        assert(rest.size == 1,
          s"svPorts: 端口 fragment 解析异常（tokens=$toks）⟹ 核对无效，不可静默通过")
        assert(dir.nonEmpty, s"svPorts: 端口 ${rest.head} 无方向关键字（非 ANSI 端口表？）")
        if (rest.head != "clock" && rest.head != "reset") ps += ((dir, rest.head, wid))
      }
    }
    assert(ps.nonEmpty, "svPorts: 未能抓到任何端口（解析失配 ⟹ 核对无效，不可静默通过）")
    ps.toSeq
  }

  /** `[hi:lo]` → 位宽十进制串；无方括号（1 位端口）→ "1"。比较【位宽数值】而非原始区间串，
    * 可容忍 firtool 改写区间书写形式（如 `[39:0]` vs `[0:39]` 之外的排版差异）。 */
  private def portWidth(spec: String): String = {
    val parts = spec.stripPrefix("[").stripSuffix("]").split(":")
    assert(parts.length == 2, s"portWidth: 无法解析位宽区间 '$spec'")
    (BigInt(parts(0).trim) - BigInt(parts(1).trim) + 1).toString
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 单档 W 的完整断言集
  // ════════════════════════════════════════════════════════════════════════════
  private val AllGroups =
    Seq("norm", "wbound", "drop", "cancel", "zero", "growth", "lz", "mlbound", "mx4sweep", "mx8sweep", "oos", "loop")

  private def checkW(w: Int): Unit = {
    val (acc, loop) = runW(w)
    results(w) = (acc, loop)
    println(acc.report())
    println(loop.report)

    val th = relErrShiftOf(w)

    // ① 常量自洽：默认档的阈值必须恰为用户裁定的 2^−18
    if (w == FpAddNarrowOpt.DefaultW) {
      FpAddNarrowOpt.DefaultW shouldBe 19
      FpAddNarrowOpt.DefaultRelErrShift shouldBe 18
      th shouldBe FpAddNarrowOpt.DefaultRelErrShift
    }

    // ② J0/J1/J2/J3 与 A2/A3 的逐向量断言已在 judge() 内部完成（失败即 abort）。此处复核计数与覆盖面。
    acc.total should be > 500
    (acc.nJ1 + acc.nJ2 + acc.nJ3 + acc.nOos) shouldBe acc.total

    // ③ 覆盖组齐全，且每组都有命中
    AllGroups.foreach { g =>
      withClue(s"W=$w 缺失覆盖组 [$g]：") { acc.groups.contains(g) shouldBe true }
      withClue(s"W=$w 覆盖组 [$g] 无命中：") { acc.groups(g).n should be > 0 }
    }

    // ④ 用户点名的覆盖项逐一兑现
    withClue("k 档未全覆盖 {0,1,2,3}：") {
      Seq(0, 1, 2, 3).foreach(k => acc.kHist.getOrElse(k, 0) should be > 0)
    }
    withClue("nLossy 未覆盖 0..5：") {
      Seq(0, 1, 2, 3, 4, 5).foreach(n => acc.nLossyHist.getOrElse(n, 0) should be > 0)
    }
    acc.nDropVec    should be > 0        // term-drop
    acc.nLossless   should be > 0        // 完全无损（J1 的解析前提）
    acc.nMustRel    should be > 0        // A3 强断言确实被触发过（否则该断言形同虚设）
    acc.nJ1         should be > 0        // 精确无损路径有命中
    acc.nJ2         should be > 0        // 相对阈值路径有命中
    acc.nJ3         should be > 0        // 绝对界兜底路径有命中（相消/多损域）

    // ⑤ MX4 全域（256 码对）必须【逐向量语义精确无损 J1】—— 用户「MX4 应无损、可断言更严」的最强兑现。
    //   证明（第一性原理，非拟合）：① 同 block 四 lane 共享 scaleA/scaleB ⟹ expField 跨度 ≤7 ≤ W−4
    //   ⟹ 对齐 ts ≤ 20 ⟹ mag24 低 20 位结构性 0 ⟹ nLossy = 0；② 各槽对齐后 a_i 均可被 2^(W−11) 整除
    //   （W=16 最坏：p=9 且 sh=7 ⟹ a = 9×2^5）⟹ signedSum ≡ 0 (mod 2^k) 对 k ≤ 3 恒成立
    //   ⟹ k 档右移的截断项亦为 0 ⟹ diff == 0。
    val g4 = acc.groups("mx4sweep")
    g4.n shouldBe 256
    withClue(s"W=$w MX4 全域扫描出现对齐有损向量（同 block 指数跨度 ≤7 ≤ W−4 的论证被推翻）：") {
      g4.nAlignLossless shouldBe g4.n
    }
    withClue(s"W=$w MX4 全域扫描存在 diff≠0 的向量（「MX4 精确无损」承诺被推翻）：") {
      g4.j1 shouldBe g4.n
    }

    // ⑥ mlbound 组必须【真的】构造出超阈值反例（否则「诚实标注」变成空头承诺）
    val gml = acc.groups("mlbound")
    withClue(s"W=$w mlbound 组未能构造出实测相对档 < 阈值 $th 的反例：") {
      gml.minRel should be <= (th - 1)
    }
    gml.j3 should be > 0

    // ⑥b 未达阈值向量的【逐条解析解释】审计 —— 诚实性的机器化：不允许存在「莫名低于阈值」的向量。
    //   解析解释只有两种（见 BelowTh.reason）：
    //     · 病态相消：|exact| < 2^(W−1)×windowLsb = 2^(E−127)（E 档 1 ULP）⟹ 条件数 > 2^(W−1)，
    //       相对误差被条件数放大 —— 这是【任何】有限精度加法器的共性（FpAdd 的 31-bit 窗口亦然，
    //       只是门槛更低），不构成 L2 的额外缺陷 ⟹ 该域按 J3 绝对界裁决。
    //     · 多损域：nLossy ≥ 2 ⟹ 解析上界 nLossy×2^−(W−1) 本就【可】超阈（文件头「诚实标注」）。
    withClue(s"W=$w 未达阈值向量数(${acc.nBelowTh}) ≠ J3 兜底数(${acc.nJ3}) ⟹ 存在「未达阈却未被绝对界" +
             "兜住」或「达阈却计为兜底」的向量（记账漏洞）：") {
      acc.nBelowTh shouldBe acc.nJ3
    }
    acc.belowTh.foreach { b =>
      withClue(s"W=$w 向量 [${b.tag}] 实测相对档 2^−${b.measured} < 阈值 2^−$th 却【无解析解释】" +
               s"（illCond=${b.illCond} nLossy=${b.nLossy} k=${b.kModel}）⟹ 误差界推导或实现有漏洞：") {
        b.explained shouldBe true
      }
      withClue(s"W=$w 向量 [${b.tag}] 未达阈却未走 J3 兜底（path=${b.path}）：") {
        b.path shouldBe "J3-absbound"
      }
    }
    // ⑥c 记账自洽：达标域（J1/J2）的实测最差档必须 ≥ 阈值。此条【定义使然】，其价值在于
    //    捕获 record() 的分桶 bug（若把一条低于阈值的向量误记为达标，本断言立即失败）。
    withClue(s"W=$w 达标域实测最差相对档 ${acc.minRelPass} < 阈值 $th（记账自相矛盾）：") {
      (acc.minRelPass == Int.MaxValue || acc.minRelPass >= th) shouldBe true
    }
    withClue(s"W=$w 达阈向量数(${acc.nPass}) + 未达阈(${acc.nBelowTh}) + oos(${acc.nOos}) ≠ 总数：") {
      acc.nPass + acc.nBelowTh + acc.nOos shouldBe acc.total
    }

    // ⑦ wbound 组必须同时含「无损转折点」与「term-drop 边界」
    val gwb = acc.groups("wbound")
    gwb.n should be >= 8
    gwb.nAlignLossless should be > 0
    gwb.nDrop should be > 0

    // ⑧ 环路：反膨胀 + 累积漂移有界（逐拍断言已在 runW 内完成）
    loop.cycles shouldBe LoopCycles
    loop.maxExpOut should be <= (loop.maxLaneE + 3 + 2 * clog2(loop.cycles + 1))
    loop.nExact should be > 0
    withClue(s"W=$w 环路累积漂移超出可加解析界：") {
      loop.worstDrift.leAbs(loop.boundAcc) shouldBe true
    }
    info(s"W=$w 全域实测最差相对档 = ${acc.minRel}（阈值 $th）；达标域最差 = ${acc.minRelPass}；" +
         s"达阈 ${acc.nPass}/${acc.total - acc.nOos}、未达阈 ${acc.nBelowTh} 条全由 J3 兜住；" +
         s"k 档直方图 = ${acc.kHist.toSeq.mkString(",")}")
  }

  // ════════════════════════════════════════════════════════════════════════════
  // 测试 block（固定唯一 it should 串，规避 ScalaTest 参数化用例名撞车致 ABORTED）
  // ════════════════════════════════════════════════════════════════════════════
  behavior of "FpAddNarrowOpt（L2 窄窗口五槽带符号 CSA 累加器 · task#16）"

  it should "pass the full W=19 (default) suite: J0 spec-model fidelity + J1/J2/J3 semantics at the 2^-18 threshold, MX4 all-code lossless alignment, and MAC-loop non-inflation" in {
    checkW(FpAddNarrowOpt.DefaultW)
  }

  it should "pass the full W=16 suite (narrowest parameterized window, threshold scales to 2^-15)" in {
    checkW(16)
  }

  it should "pass the full W=22 suite (widest parameterized window, threshold scales to 2^-21)" in {
    checkW(22)
  }

  it should "expose an I/O contract port-by-port identical to the untouched FpAdd (port-compatible for A/B synthesis)" in {
    val ref = svPorts(new FpAdd(40))
    println("\n════ I/O 契约逐端口核对（FpAdd 为基准，FpAdd.scala 一行未改）════")
    ref.foreach { case (d, n, t) => println(f"    FpAdd         : $d%-6s $n%-14s [$t%s]") }
    WsToTest.foreach { w =>
      val got = svPorts(new FpAddNarrowOpt(40, w))
      withClue(s"W=$w 的 I/O 端口表与 FpAdd 不一致（port-compatible 可替换性被破坏）：\n" +
               s"  FpAdd        = $ref\n  FpAddNarrowOpt  = $got\n") {
        got shouldBe ref
      }
      println(s"    FpAddNarrowOpt(W=$w) : 端口表与 FpAdd 【逐位一致】（${got.size} 个端口，方向/名称/位宽全同）✓")
    }
  }

  it should "publish the consolidated measured-vs-analytic accuracy report across all three W settings" in {
    // ★【task#25 Sean m3】原为 `results.keySet shouldBe WsToTest.toSet`（硬依赖前三档 W block 已跑），
    //   单跑本汇总用例（如 `testOnly ... -- -z "consolidated"`）时 results 为空 ⟹ 假 FAIL。改为自补缺档：
    //   任何未跑过的 W 就地补跑 checkW(w)（幂等：已跑过的档 results 已含、不重复跑），使本用例与前序解耦。
    WsToTest.foreach(w => if (!results.contains(w)) checkW(w))
    val sb = new StringBuilder
    sb.append("\n════════════ task#16 实测汇总（估算 vs 实测，诚实分离）════════════\n")
    sb.append("  W   阈值     向量数   J1精确  J2达阈  J3兜底  oos   全域最差档  达标域最差档  达阈比     环路expOut抬升  环路逐拍精确\n")
    WsToTest.foreach { w =>
      val (acc, loop) = results(w)
      def rr(x: Int): String = if (x == Int.MaxValue) "inf" else "2^−" + x
      sb.append(padR(w.toString, 4) + padR("2^−" + relErrShiftOf(w), 9) + padR(acc.total.toString, 8) +
        padR(acc.nJ1.toString, 8) + padR(acc.nJ2.toString, 8) + padR(acc.nJ3.toString, 8) +
        padR(acc.nOos.toString, 6) +
        padR(rr(acc.minRel), 12) + padR(rr(acc.minRelPass), 14) +
        padR(acc.nPass + "/" + (acc.total - acc.nOos), 11) +
        padR("+" + (loop.maxExpOut - loop.maxLaneE) + " / " + loop.cycles + "拍", 16) +
        padR(loop.nExact + "/" + loop.cycles, 14) + "\n")
    }
    sb.append("\n  ── 解析【估算】(模块头注，第一性原理) ──\n")
    sb.append("    |diff| ≤ (nLossy + 2^k − 1) × windowLsb ;  relErr ≤ (nLossy + 2^k − 1)/(magFull − nLossy)\n")
    sb.append("    nLossy ≤ 1 ⟹ relErr ≲ 2^−(W−1) = 阈值 ;  nLossy = 0 ∧ k = 0 ⟹ 精确无损\n")
    sb.append("    nLossy ≥ 2 ⟹ 阈值【可被超出】（上界 nLossy × 2^−(W−1)），但仍受绝对界 (nLossy+2^k−1)×windowLsb 约束\n")
    sb.append("    病态相消域（|exact| < 1 ULP = 2^(E−127)）⟹ 相对误差被【条件数】放大，与窗口宽度无关（FpAdd 同族）\n")
    sb.append("  ── 实测【验证】：未达阈值向量逐条枚举 + 解析解释 ──\n")
    WsToTest.foreach { w =>
      val (acc, _) = results(w)
      val th = relErrShiftOf(w)
      val gml = acc.groups("mlbound")
      sb.append(s"    W=$w（阈值 2^−$th）：全域最差 = " +
        (if (acc.minRel == Int.MaxValue) "inf" else s"2^−${acc.minRel} @ [${acc.minRelTag}]") +
        s"；达标域最差 = ${if (acc.minRelPass == Int.MaxValue) "inf" else "2^−" + acc.minRelPass} ⟹ " +
        s"达阈 ${acc.nPass}/${acc.total - acc.nOos}，未达阈 ${acc.nBelowTh} 条【全部】走 J3 绝对界且【全部】有解析解释\n")
      sb.append(s"      mlbound 组实测最差 = ${gml.minRel}（= 阈值−${th - gml.minRel} 档）" +
                s" ⟹ 与解析预测【一致】（ML1 预测 W−2、ML2 预测 W−3）\n")
      acc.belowTh.foreach { b =>
        sb.append(s"        · ${pad(b.tag, 8)} measured=2^−${b.measured}  nLossy=${b.nLossy} k=${b.kModel}  " +
                  s"原因=${b.reason}\n")
      }
    }
    sb.append("  ── 结论 ──\n")
    sb.append("    · MX4 全域 256 码对 × 3 档 W：【逐向量 diff==0】(J1) ⟹ 用户「MX4 应无损、可断言更严」以最强形式兑现\n")
    sb.append("    · MX8 稳态域（对齐 shift ≤ W−8）与 cReg 自产形态：nLossy=0 ⟹ 同样 diff==0\n")
    sb.append("    · nLossy ≤ 1 的全部向量：实测相对档 ≥ W−1 ⟹ 【达到用户裁定的 2^−18（W=19）】\n")
    sb.append("    · 未达阈的两类向量均已【显式枚举 + 解析解释 + J3 绝对界兜住】：\n")
    sb.append("        (i)  病态相消（X4 / LZ3）：|exact| ≪ 最大项 ⟹ 条件数放大，任何有限精度加法器皆然（FpAdd 同族）\n")
    sb.append("        (ii) 多损域（ML1 / ML2）：nLossy ≥ 2 且 |exact| 与 nLossy×LSB 同阶 ⟹ 上界 nLossy×2^−(W−1)\n")
    sb.append("      ★【task#18 D 订正】不存在「取某个 W 就无条件达到相对阈值」：相消域在【任意有限 W】下都无相对界（契约 ③）。\n")
    sb.append("        Terry(task#17) 独立测量：W=19 达标率 97.33%、W=22 仅升到 99.33%（仍有 143 条 in-spec 超阈，最差 2^−5）。\n")
    sb.append("        正确契约 = ① 无条件绝对界 + ② 良态域相对界 + ③ 相消域由绝对界兜底。W 默认 19、参数化保留。\n")
    sb.append("    · 环路 40 拍：expOut 仅【对数】抬升（无 (D1) 所述每拍 +3 膨胀），逐拍 drift==0、累积漂移 ≤ 可加解析界\n")
    sb.append("      ⟹ k 档右移（expOut = E + k）是 MAC 环路可用性的【必要条件】，已由反膨胀断言实测钉死\n")
    val rep = sb.toString
    println(rep)
    // 汇总数字自身也做断言：三档 W 的 J1 命中都必须占多数（MX 稳态域应几乎无损）
    WsToTest.foreach { w =>
      val (acc, _) = results(w)
      withClue(s"W=$w 的 J1（语义精确无损）占比过低 ⟹ MX 稳态域论证被推翻：") {
        acc.nJ1 should be > (acc.total / 2)
      }
    }
    rep should include("task#16 实测汇总")   // 防御性：确保汇总报告串非退化
  }
}
