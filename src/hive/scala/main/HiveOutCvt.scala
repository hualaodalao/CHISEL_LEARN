/** HiveCore dmaC 写回精度转换模块（P2：纯组合逻辑；P3a 追加 MX 量化支路）。
  *
  * 输入：一行 cExtW（totalN × cEffW 位槽，每槽低 40 位 = 延迟规格化格式
  *   {sign(1), exp(8), mant(31)}）+ outFmt（3-bit，P3a 由 2-bit 扩展）+ rnd（5 模式）
  *   + blkSel（量化块大小）+ nEff（有效列数，≥ nEff 列为 padding）。
  * 输出：四条密集打包行（低列号元素在低位，与 P1 打包契约一致）：
  *   - packed32：totalN × 32-bit（fp32 通路，逐元素复用 Fp32.fpNormalize40to32，
  *     与现状内联规格化语义一致）；
  *   - packed16：totalN × 16-bit（outFmt==001 时内容为 fp16、outFmt==010 时为 bf16）；
  *   - packed8：totalN × 8-bit（P3a：outFmt==100/101 时 E4M3/E5M2 量化码）；
  *   - packed4：totalN × 4-bit（P3a：outFmt==110 时 E2M1 量化码，两元素一字节、
  *     低列号占低 nibble）；
  *   - scaleRow：cScaleRowW 位（每 8 元素块 1 字节 E8M0，低块号在低位，
  *     未覆盖的高位字节 0）。
  *
  * outFmt 编码（3-bit，与 HiveCoreRegister.outFmt regs(7)(13,11) 同源）：
  *   000=fp32（默认）、001=fp16、010=bf16、011=reserved（行为同 000）、
  *   100=mxfp8-E4M3 量化、101=mxfp8-E5M2 量化、110=mxfp4-E2M1 量化、
  *   111=reserved（降级 000：调用方选路只对 001/010 与 100/101/110 生效，
  *   本模块不对 reserved 报错，避免运行期写错配置直接打挂写回通路）。
  *   兼容性：bit13=0 时（000..011）与 P2 原 2-bit 编码逐字等价，旧软件行为逐拍不变。
  *
  * fp16/bf16 通路：先 40→32 规格化（复用 Fp32.fpNormalize40to32），再 32→16
  * 降精度。边界语义对齐 FbFormatConverter（ForagerBee 已验证实现）：
  *   - 溢出      → 饱和到目标格式最大有限值（保号）；
  *   - 下溢/零   → flush-to-zero（保号；本硬件族不产生目标格式次正规数）；
  *   - inf/nan   → 直接映射为目标格式 inf / 规范 nan。
  *
  * 【P3a MX 量化支路（共享 scale 语义）】块内元素共享一个 E8M0 scale：
  *   - 块 amax = max(|fp32|)，列 ≥ nEff 的 padding 元素不参与；
  *   - E8M0 scale s = 满足 amax ≤ maxFinite × 2^(s-127) 的最小指数（纯指数域推导，
  *     见 blockScale 注释）；全零块（含掩码后无有效元素）→ s=0x00 哨兵（反量化侧
  *     门控，不参与数值）；amax=inf → s=254 钳制；
  *   - 逐元素：指数减（÷2^(s-127)）后按目标格式舍入；舍入后溢出 → 饱和到目标最大
  *     有限值（E4M3=0x7E/448、E5M2=0x7B/57344、E2M1=0x7/6.0，保号）；低于最小正
  *     规格化 → 保号 ±0（无次正规产出）；inf → 饱和最大有限值；nan → E4M3 用
  *     NaN 编码（exp&mant 全1=0x7F）、E5M2 用 exp 全1 编码（0x7E）、E2M1 无 nan
  *     编码裁定饱和到最大有限值；零 → ±0；padding 列输出 0。
  *   字段/边界表逐条镜像 Fp32.mxToFp32（方向取逆）：E4M3 expW4/mantW3/bias7 且
  *   NaN 需 mant 全1；E5M2 expW5/mantW2/bias15；E2M1 expW2/mantW1/bias1。
  *
  * 舍入实现选择留痕：本模块内按 5 模式语义自行实现（未放宽复用
  * Fp32.applyRounding）。理由：32→16 降精度需要 sticky/lsb 参与判定
  * （RNE 为严格 tie-to-even：guard && (sticky || lsb)；方向性模式按
  * 「被截断位非零」增大幅度），而 applyRounding 是 guard 单参简化签名
  * （其 RNE 不含 lsb tie 判定），强行复用会使两侧语义互相扭曲；此处独立
  * 实现并与测试软件参考模型（HiveSimCommon.swFp32ToFp16/swFp32ToBf16）
  * 逐位同构，杜绝双源漂移。量化支路复用同一判定函数（roundUpN 别名，
  * 5 模式不变、RMM=guard），软件参考为 HiveSimCommon.swQuantBlock。
  *
  * 【两级舍入链声明】量化输出与 16-bit 通路同格式：40→32 固定简化 RNE 规格化
  * （fpNormalize40to32）+ 量化运行期 rnd（roundUpN）；方向性模式（RDN/RUP/RMM）
  * 与理想单级舍入可能存在末位差异，软件对拍须同链重建。
  *
  * 例化约束：仅当调用方配置含浮点或 MX（cfg.hasFp || cfg.hasMx）时例化；
  * 纯整数配置不生成此硬件（整数累加值写回走透传，不做精度转换）。
  */

import chisel3._
import chisel3.util._

class HiveOutCvt(cfg: HiveCoreConfig) extends Module {
  // 延迟规格化格式槽约束：40-bit {sign,exp8,mant31} 必须落在 cEffW 槽低位
  require(cfg.cEffW >= 40, s"HiveOutCvt: cEffW(${cfg.cEffW}) 必须 >= 40（延迟规格化格式需要）")
  // P3a 量化支路按 8 元素组构建块 amax 树：totalN 必须为 8 的整数倍（MX 配置
  // totalN==16 天然满足，此处拦截未来非 8 倍数配置的静默错位）
  require(cfg.totalN % 8 == 0, s"HiveOutCvt: totalN(${cfg.totalN}) 必须为 8 的整数倍（量化块按 8 元素组构建）")
  private val nGroups: Int = cfg.totalN / 8   // 8 元素组数（量化块基础粒度）

  // outFmt 编码（3-bit，与 HiveCoreRegister.outFmt / HiveCoreDmaWrOnly.outFmtEff 同源）；
  // P3a 由 2-bit 扩为 3-bit：000..010 语义逐字不变，新增 100/101/110 量化编码，
  // 011/111 reserved 不参与任何选路（011 回落非 16 位语义与旧 11 一致）。
  private val FMT_FP32   = 0.U(3.W)
  private val FMT_FP16   = 1.U(3.W)
  private val FMT_BF16   = 2.U(3.W)
  private val FMT_MXE4M3 = 4.U(3.W)
  private val FMT_MXE5M2 = 5.U(3.W)
  private val FMT_MXE2M1 = 6.U(3.W)

  val io = IO(new Bundle {
    val lineIn   = Input(UInt((cfg.totalN * cfg.cEffW).W))  // 一行 cEffW 位槽（低 40 位有效）
    val outFmt   = Input(UInt(3.W))                          // 000=fp32 001=fp16 010=bf16 011=rsv(同000)
                                                             // 100=E4M3 101=E5M2 110=E2M1 111=rsv(同000)
    val rnd      = Input(RoundingMode())                     // 舍入模式（与 regFile.rnd 同源）
    val blkSel   = Input(UInt(2.W))                          // P3a 量化块大小：00=8 01=16 10=32 11=64（元素/块）
    val nEff     = Input(UInt(log2Up(cfg.totalN + 1).W))     // P3a 有效列数（≥ nEff 列 = padding）
    val packed32 = Output(UInt((cfg.totalN * 32).W))         // fp32 密集行（低列号在低位）
    val packed16 = Output(UInt((cfg.totalN * 16).W))         // fp16/bf16 密集行（按 outFmt 选路）
    val packed8  = Output(UInt((cfg.totalN * 8).W))          // P3a E4M3/E5M2 量化码密集行（低列号在低位）
    val packed4  = Output(UInt((cfg.totalN * 4).W))          // P3a E2M1 量化码（两元素一字节、低列号低 nibble）
    val scaleRow = Output(UInt(cfg.cScaleRowW.W))            // P3a E8M0 块 scale 行（每 8 元素块 1 字节，低块号低位）
  })

  // ==========================================================================
  // 32→16 舍入判定（5 模式，sticky/lsb-aware）：
  //   RNE: guard && (sticky || lsb)（严格 tie-to-even，与 FbFormatConverter 一致）
  //   RTZ: 恒不进位
  //   RDN: 负数且被截断位非零（向 -∞ 增大幅度）
  //   RUP: 正数且被截断位非零（向 +∞ 增大幅度）
  //   RMM: ties to Max Magnitude——仅 tie（guard=1，不论 sticky）进位，
  //        与 Fp32.applyRounding 的 RMM 分支（guard 单参）同语义；
  //        旧实现误为「截断位非零即进位」（guard|sticky），已裁定修正。
  // ==========================================================================
  private def roundUp16(rnd: RoundingMode.Type, guard: Bool, sticky: Bool,
                        lsb: Bool, sign: Bool): Bool = {
    val truncNZ = guard | sticky
    MuxLookup(rnd, guard && (sticky || lsb))(Seq(
      RoundingMode.RTZ -> false.B,
      RoundingMode.RDN -> (sign && truncNZ),
      RoundingMode.RUP -> (!sign && truncNZ),
      RoundingMode.RMM -> guard
    ))
  }

  // ==========================================================================
  // 泛化尾数位宽舍入判定（P3a 量化支路用）：5 模式不变（RMM=guard）。
  // roundUp16 本身与尾数位宽无关（guard/sticky/lsb 均为外部送入的标量判定量），
  // 量化支路直接复用；此别名显式标注参数化入口（mantW 仅作语义留痕，
  // 避免后人误以为舍入判定依赖目标格式位宽而另写一份造成双源）。
  // ==========================================================================
  private def roundUpN(mantW: Int, rnd: RoundingMode.Type, guard: Bool, sticky: Bool,
                       lsb: Bool, sign: Bool): Bool =
    roundUp16(rnd, guard, sticky, lsb, sign)

  // ==========================================================================
  // fp32 → fp16（sign1/exp5/mant10，bias=15）
  // 结构对齐 FbFormatConverter.fp32ToFp16，舍入由固定 RNE 扩展为 5 模式运行期选路；
  // 差异留痕：溢出饱和到最大有限值（0x7BFF/0xFBFF），而非 Fb 的 inf 语义分支之一
  // （Fb 溢出分支实际同为 0x7BFF...=exp 全1 mant 全0 即 inf——本模块按计划裁定
  // 「溢出饱和到目标格式最大有限值」改为 mant 全1 的最大有限值）。
  // ==========================================================================
  private def fp32ToFp16Rnd(x: UInt, rnd: RoundingMode.Type): UInt = {
    val sign = x(31)
    val exp  = x(30, 23)
    val man  = x(22, 0)

    val signedZero = Cat(sign, 0.U(15.W))
    val isZero    = exp === 0.U                 // ±0 / fp32 次正规 → FTZ（本硬件族无次正规）
    val isInf     = (exp === 255.U) && (man === 0.U)
    val isNan     = (exp === 255.U) && (man =/= 0.U)
    val fp16Inf   = Cat(sign, 31.U(5.W), 0.U(10.W))
    val fp16Nan   = Cat(sign, 31.U(5.W), 0x200.U(10.W))   // 规范 nan（mant 非零即可）
    val fp16Max   = Cat(sign, 30.U(5.W), 0x3FF.U(10.W))   // ±65504 最大有限值（饱和目标）

    // 尾数截断：保留高 10 位，guard=bit12，sticky=bit11..0
    val mantTop = man(22, 13)
    val guard   = man(12)
    val sticky  = man(11, 0).orR
    val roundUp = roundUp16(rnd, guard, sticky, mantTop(0), sign)
    val manRnd  = mantTop +& roundUp            // 11-bit，bit10 = 尾数进位
    val carry   = manRnd(10)
    val manFin  = Mux(carry, 0.U(10.W), manRnd(9, 0))

    // 溢出：fp32 真指数 > 15（exp>142，或 exp==142 且尾数进位推到 2^16）
    val overflow  = (exp > 142.U) || ((exp === 142.U) && carry)
    // 下溢：低于目标最小正规格化（2^-14，fp32 exp=113）→ FTZ；
    // 舍入后为零的边界值同归此分支（无次正规产出，与计划裁定一致）
    val underflow = exp < 113.U

    Mux(isZero || underflow, signedZero,
      Mux(isInf, fp16Inf,
        Mux(isNan, fp16Nan,
          Mux(overflow, fp16Max,
            Cat(sign, (exp +& carry - 112.U)(4, 0), manFin)))))
  }

  // ==========================================================================
  // fp32 → bf16（高 16 位直取 + 舍入进位）
  // 结构对齐 FbFormatConverter.fp32ToBf16（upper +& roundUp），舍入扩展为 5 模式；
  // bf16 指数域与 fp32 等宽，无范围溢出问题；追加 exp==255 显式映射保证
  // inf→inf、nan→规范 nan（截断/进位路径对 exp 全1 值会产出错误尾数，须旁路）。
  // ==========================================================================
  private def fp32ToBf16Rnd(x: UInt, rnd: RoundingMode.Type): UInt = {
    val sign = x(31)
    val exp  = x(30, 23)
    val man  = x(22, 0)

    val isZero = exp === 0.U
    val isInf  = (exp === 255.U) && (man === 0.U)
    val isNan  = (exp === 255.U) && (man =/= 0.U)
    val bf16Inf = Cat(sign, 0xFF.U(8.W), 0.U(7.W))
    val bf16Nan = Cat(sign, 0xFF.U(8.W), 0x20.U(7.W))     // 规范 nan（mant 非零即可）

    // 截断位：guard=x(15)，sticky=x(14,0)，lsb=保留位最低位 x(16)
    val guard   = x(15)
    val sticky  = x(14, 0).orR
    val roundUp = roundUp16(rnd, guard, sticky, x(16), sign)

    Mux(isZero, Cat(sign, 0.U(15.W)),
      Mux(isInf, bf16Inf,
        Mux(isNan, bf16Nan,
          (x(31, 16) +& roundUp)(15, 0))))
  }

  // ==========================================================================
  // 逐元素转换：40→32 规格化（与现状内联实现同源）→ 32→16 降精度
  // ==========================================================================
  private val fp32Seq: Seq[UInt] = (0 until cfg.totalN).map { i =>
    val slice = io.lineIn(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
    Fp32.fpNormalize40to32(slice(39, 0))
  }
  private val fp16Seq: Seq[UInt] = fp32Seq.map(x => fp32ToFp16Rnd(x, io.rnd))
  private val bf16Seq: Seq[UInt] = fp32Seq.map(x => fp32ToBf16Rnd(x, io.rnd))

  // 密集打包：Cat(reverse) 使元素 0 落最低位（与 popDataFpNorm 列序一致）
  io.packed32 := Cat(fp32Seq.reverse)
  io.packed16 := Mux(io.outFmt === FMT_FP16, Cat(fp16Seq.reverse), Cat(bf16Seq.reverse))

  // ==========================================================================
  // P3a MX 量化支路（纯增量：上方 packed32/packed16 两条既有通路逐字不动；
  // 非量化运行（outFmt bit13=0）时本支路输出不被调用方采样，行为门控在侧）。
  //
  // 【共享 scale 推导（关键决策留痕）】块 amax = 1.f × 2^(E-127)（E 为 fp32
  // 偏置指数），目标格式最大有限值 maxFinite = maxMantVal × 2^(maxCodeExp-bias)。
  // 约束 amax ≤ maxFinite × 2^(s-127) 展开：
  //   1.f × 2^(E-127) ≤ maxMantVal × 2^(maxCodeExp-bias+s-127)
  //   ⟺ E_eff ≤ maxCodeExp - bias + s     （1.f > maxMantVal 时指数提 1：E_eff = E+1）
  //   ⟹ s_min = E_eff + bias - maxCodeExp
  // （fp32 偏置指数 E 已含 127 偏置，与 E8M0 的 bias=127 恰好抵消，故公式中
  // 不显式出现 127；全程纯指数域组合逻辑，无乘法/移位。）
  // 边界：全零块（含掩码后无有效元素）→ s=0x00 哨兵；amax=inf（E=255）→ s=254；
  // s<1（块幅值极小）→ 钳制 1（E8M0 最小非哨兵因子 2^-126，此时块内元素量化后
  // 多为 ±0，反量化重建误差与 FTZ 语义一致）。
  // ==========================================================================

  // 量化格式选路判据（三格式静态常量表各自例化，避免动态位选）
  private val isQE4M3 = io.outFmt === FMT_MXE4M3
  private val isQE5M2 = io.outFmt === FMT_MXE5M2
  // E2M1 不再显式判定：110/111 均落入 E2M1 参数（111=reserved 降级语义由调用方
  // 不采样量化输出自然实现，本模块输出保持确定性、不报错）

  // --- 块 amax：符号位清零取幅值（31-bit 无符号比较即 |fp32| 单调序）；
  // 列 ≥ nEff 的 padding 元素掩码为 0（不进 amax） ---
  private val magSeq: Seq[UInt] = (0 until cfg.totalN).map { i =>
    Mux(i.U < io.nEff, fp32Seq(i)(30, 0), 0.U(31.W))
  }
  // 8 元素组并行比较树 → 按 blkSel 逐级合并出 16/32/64 块界（blockScale 对
  // amax 单调，先合并 amax 再求 scale 与「逐组求 scale 再取最大」等价）
  private val groupAmax: Seq[UInt] = (0 until nGroups).map { g =>
    magSeq.slice(g * 8, g * 8 + 8).reduce((a, b) => Mux(a >= b, a, b))
  }
  private def mergeMax(s: Seq[UInt]): Seq[UInt] =
    s.grouped(2).map(p => if (p.size == 2) Mux(p(0) >= p(1), p(0), p(1)) else p(0)).toSeq
  private val amax16: Seq[UInt] = mergeMax(groupAmax)
  private val amax32: Seq[UInt] = mergeMax(amax16)
  private val amax64: Seq[UInt] = mergeMax(amax32)
  // 第 i 列所属量化块的 amax（按 blkSel 选层；totalN=16 时 32/64 层退化为全行共享）
  private def elemAmax(i: Int): UInt = Mux(io.blkSel === 0.U, groupAmax(i / 8),
    Mux(io.blkSel === 1.U, amax16(i / 16),
      Mux(io.blkSel === 2.U, amax32(i / 32), amax64(i / 64))))

  // --- E8M0 块共享 scale（推导见上方分区注释；纯指数域组合逻辑） ---
  private def blockScale(amax: UInt, maxCodeExp: Int, bias: Int, maxMant23: BigInt): UInt = {
    val aExp  = amax(30, 23)
    val aMant = amax(22, 0)
    val blockZero = amax === 0.U
    val blockInf  = aExp === 255.U
    // 1.f > maxMantVal（目标格式最大尾数值左对齐到 23 位）→ 指数提 1 再容下
    val needBump = aMant > maxMant23.U(23.W)
    val eEff = Mux(needBump, aExp +& 1.U, Cat(0.U(1.W), aExp))   // 9-bit
    val sSum  = eEff +& bias.U                                    // E_eff + bias
    val sDiff = sSum - maxCodeExp.U                               // s = E_eff + bias - maxCodeExp（可绕回）
    val sSmall = sSum <= maxCodeExp.U                             // s < 1 → 钳制 1（见分区注释）
    Mux(blockZero, 0.U(8.W),
      Mux(blockInf, 254.U(8.W),
        Mux(sSmall, 1.U(8.W),
          Mux(sDiff > 254.U, 254.U(8.W), sDiff(7, 0)))))
  }

  // --- 逐元素量化（指数减后舍入到目标格式；边界表镜像 Fp32.mxToFp32 取逆） ---
  // @param maxNormalExp 正常数最大指数码（E4M3=15：exp 全1 配 mant≤6 仍有限；
  //        E5M2=30：exp 全1 为 inf/nan；E2M1=3：无特殊编码）
  // @param satCode 饱和（最大有限值）编码（不含符号位）；nanCode nan 映射编码；
  // @param nanToSat nan 输入饱和处理（E2M1 无 nan 编码的裁定）；
  // @param fixNanCode 舍入落入 NaN 编码时改写为饱和（仅 E4M3：exp 全1 mant 全1）
  private def quantElem(x: UInt, s: UInt, rnd: RoundingMode.Type,
                        expW: Int, mantW: Int, bias: Int,
                        maxNormalExp: Int, satCode: Int, nanCode: Int,
                        nanToSat: Boolean, fixNanCode: Boolean): UInt = {
    val sign = x(31)
    val e    = x(30, 23)
    val man  = x(22, 0)
    val tailW = expW + mantW
    val signedZero = Cat(sign, 0.U(tailW.W))
    val satFull    = Cat(sign, satCode.U(tailW.W))
    val nanFull    = Cat(sign, nanCode.U(tailW.W))

    val isZero = e === 0.U                    // ±0 / fp32 次正规 → FTZ 保号 ±0（同硬件族语义）
    val isInf  = (e === 255.U) && (man === 0.U)
    val isNan  = (e === 255.U) && (man =/= 0.U)

    // 尾数截断（静态位选，同 fp32ToFp16Rnd 结构）：保留高 mantW 位，
    // guard = 首个被截断位，sticky = 其余被截断位或，lsb = 保留位最低位
    val drop    = 23 - mantW
    val mantTop = man(22, drop)
    val guard   = man(drop - 1)
    val sticky  = man(drop - 2, 0).orR
    val roundUp = roundUpN(mantW, rnd, guard, sticky, mantTop(0), sign)
    val manRnd  = mantTop +& roundUp          // (mantW+1)-bit，bit(mantW) = 尾数进位
    val mCarry  = manRnd(mantW)
    val manFin  = Mux(mCarry, 0.U(mantW.W), manRnd(mantW - 1, 0))

    // 指数减：÷ 2^(s-127) ⟺ 目标偏置指数 = e + bias - s（有符号比较防绕回）
    val eTgt   = (Cat(0.U(1.W), e).asSInt + bias.S) - Cat(0.U(1.W), s).asSInt
    val eRnd   = eTgt + Mux(mCarry, 1.S, 0.S)
    val underflow = eTgt < 1.S                // 低于目标最小正规格化 → 保号 ±0（无次正规产出）
    val overflow  = eRnd > maxNormalExp.S     // 舍入后溢出 → 饱和到最大有限值（保号）
    // E4M3 专属修正：舍入落到 (exp 全1, mant 全1) = NaN 编码时改写为饱和值 0x7E（448），
    // 与「饱和到最大有限值」裁定一致（正常值域内不可能产出 NaN 编码）
    val nanFix = fixNanCode.B && (eRnd === maxNormalExp.S) && (manFin === ((1 << mantW) - 1).U(mantW.W))
    val normal = Cat(sign, eRnd.asUInt(expW - 1, 0), manFin)

    Mux(isZero || underflow, signedZero,
      Mux(isInf, satFull,                     // inf → 饱和最大有限值（目标格式无 inf 编码或裁定饱和）
        Mux(isNan, Mux(nanToSat.B, satFull, nanFull),
          Mux(overflow || nanFix, satFull, normal))))
  }

  // 三格式各自例化（静态常量表，逐条镜像 Fp32 反量化侧字段语义）：
  //   E4M3: maxFinite=448=1.75×2^8   饱和 0x7E、NaN=(15,7)=0x7F、maxMantVal=1.75→尾数 0.75→0x600000
  //   E5M2: maxFinite=57344=1.75×2^15 饱和 0x7B、NaN=(31,2)=0x7E（规范尾数同 fp16Nan 取法）
  //   E2M1: maxFinite=6.0=1.5×2^2    饱和 0x7、无 nan 编码→饱和裁定、maxMantVal=1.5→尾数 0.5→0x400000
  private val s4Elem: Seq[UInt] = (0 until cfg.totalN).map(i => blockScale(elemAmax(i), 15, 7, BigInt("600000", 16)))
  private val s5Elem: Seq[UInt] = (0 until cfg.totalN).map(i => blockScale(elemAmax(i), 30, 15, BigInt("600000", 16)))
  private val s2Elem: Seq[UInt] = (0 until cfg.totalN).map(i => blockScale(elemAmax(i), 3, 1, BigInt("400000", 16)))
  private val q4Seq: Seq[UInt] = fp32Seq.zip(s4Elem).map { case (x, sc) =>
    quantElem(x, sc, io.rnd, expW = 4, mantW = 3, bias = 7,
      maxNormalExp = 15, satCode = 0x7E, nanCode = 0x7F, nanToSat = false, fixNanCode = true)
  }
  private val q5Seq: Seq[UInt] = fp32Seq.zip(s5Elem).map { case (x, sc) =>
    quantElem(x, sc, io.rnd, expW = 5, mantW = 2, bias = 15,
      maxNormalExp = 30, satCode = 0x7B, nanCode = 0x7E, nanToSat = false, fixNanCode = false)
  }
  private val q2Seq: Seq[UInt] = fp32Seq.zip(s2Elem).map { case (x, sc) =>
    quantElem(x, sc, io.rnd, expW = 2, mantW = 1, bias = 1,
      maxNormalExp = 3, satCode = 0x7, nanCode = 0x7, nanToSat = true, fixNanCode = false)
  }
  // outFmt 三选一 + nEff 掩码（列 ≥ nEff 的 padding 元素输出 0：不参与量化产出）
  private val qSelSeq: Seq[UInt] = (0 until cfg.totalN).map { i =>
    val code = Mux(isQE4M3, q4Seq(i), Mux(isQE5M2, q5Seq(i), q2Seq(i)))
    Mux(i.U < io.nEff, code, 0.U(8.W))
  }
  io.packed8 := Cat(qSelSeq.reverse)

  // packed4：E2M1 两元素一字节，低列号占低 nibble（q2Seq 同样经 nEff 掩码）
  io.packed4 := Cat((0 until cfg.totalN / 2).reverse.map { p =>
    val lo = Mux((2 * p).U     < io.nEff, q2Seq(2 * p),     0.U(4.W))
    val hi = Mux((2 * p + 1).U < io.nEff, q2Seq(2 * p + 1), 0.U(4.W))
    Cat(hi, lo)
  })

  // scaleRow：每 8 元素组 1 字节（低组号在低位），按 blkSel 共享同块 scale；
  // cScaleRowW = nGroups*8，恰无未覆盖高位（更大阵列时未覆盖字节天然 0）
  private def fmtScalesAt(g: Int): Seq[UInt] = {
    // 三级块界各层 scale（同层各组 amax 已合并，字节值与 elemAmax 侧同源一致）
    val fmt8  = Mux(isQE4M3, blockScale(groupAmax(g), 15, 7, BigInt("600000", 16)),
                Mux(isQE5M2, blockScale(groupAmax(g), 30, 15, BigInt("600000", 16)),
                             blockScale(groupAmax(g), 3, 1, BigInt("400000", 16))))
    Seq(fmt8)
  }
  io.scaleRow := Cat((0 until nGroups).reverse.map { g =>
    val s8 = fmtScalesAt(g).head
    val s16 = Mux(isQE4M3, blockScale(amax16(g / 2), 15, 7, BigInt("600000", 16)),
              Mux(isQE5M2, blockScale(amax16(g / 2), 30, 15, BigInt("600000", 16)),
                           blockScale(amax16(g / 2), 3, 1, BigInt("400000", 16))))
    val s32 = Mux(isQE4M3, blockScale(amax32(g / 4), 15, 7, BigInt("600000", 16)),
              Mux(isQE5M2, blockScale(amax32(g / 4), 30, 15, BigInt("600000", 16)),
                           blockScale(amax32(g / 4), 3, 1, BigInt("400000", 16))))
    val s64 = Mux(isQE4M3, blockScale(amax64(g / 8), 15, 7, BigInt("600000", 16)),
              Mux(isQE5M2, blockScale(amax64(g / 8), 30, 15, BigInt("600000", 16)),
                           blockScale(amax64(g / 8), 3, 1, BigInt("400000", 16))))
    Mux(io.blkSel === 0.U, s8,
      Mux(io.blkSel === 1.U, s16,
        Mux(io.blkSel === 2.U, s32, s64)))
  })
}
