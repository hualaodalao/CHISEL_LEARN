/** HiveCore dmaC 写回精度转换模块（P2：纯组合逻辑）。
  *
  * 输入：一行 cExtW（totalN × cEffW 位槽，每槽低 40 位 = 延迟规格化格式
  *   {sign(1), exp(8), mant(31)}）+ outFmt（2-bit）+ rnd（5 模式）。
  * 输出：两条密集打包行（低列号元素在低位，与 P1 打包契约一致）：
  *   - packed32：totalN × 32-bit（fp32 通路，逐元素复用 Fp32.fpNormalize40to32，
  *     与现状内联规格化语义一致）；
  *   - packed16：totalN × 16-bit（outFmt==01 时内容为 fp16、outFmt==10 时为 bf16）。
  *
  * outFmt 编码：00=fp32（默认）、01=fp16、10=bf16、11=reserved
  *   （reserved 行为同 00：调用方对 packed16 的选择只在 01/10 生效，
  *   本模块不对 11 报错，避免运行期写错配置直接打挂写回通路）。
  *
  * fp16/bf16 通路：先 40→32 规格化（复用 Fp32.fpNormalize40to32），再 32→16
  * 降精度。边界语义对齐 FbFormatConverter（ForagerBee 已验证实现）：
  *   - 溢出      → 饱和到目标格式最大有限值（保号）；
  *   - 下溢/零   → flush-to-zero（保号；本硬件族不产生目标格式次正规数）；
  *   - inf/nan   → 直接映射为目标格式 inf / 规范 nan。
  *
  * 舍入实现选择留痕：本模块内按 5 模式语义自行实现（未放宽复用
  * Fp32.applyRounding）。理由：32→16 降精度需要 sticky/lsb 参与判定
  * （RNE 为严格 tie-to-even：guard && (sticky || lsb)；方向性模式按
  * 「被截断位非零」增大幅度），而 applyRounding 是 guard 单参简化签名
  * （其 RNE 不含 lsb tie 判定），强行复用会使两侧语义互相扭曲；此处独立
  * 实现并与测试软件参考模型（HiveSimCommon.swFp32ToFp16/swFp32ToBf16）
  * 逐位同构，杜绝双源漂移。
  *
  * 例化约束：仅当调用方配置含浮点或 MX（cfg.hasFp || cfg.hasMx）时例化；
  * 纯整数配置不生成此硬件（整数累加值写回走透传，不做精度转换）。
  */

import chisel3._
import chisel3.util._

class HiveOutCvt(cfg: HiveCoreConfig) extends Module {
  // 延迟规格化格式槽约束：40-bit {sign,exp8,mant31} 必须落在 cEffW 槽低位
  require(cfg.cEffW >= 40, s"HiveOutCvt: cEffW(${cfg.cEffW}) 必须 >= 40（延迟规格化格式需要）")

  // outFmt 编码（与 HiveCoreRegister.outFmt / HiveCoreDmaWrOnly.outFmtEff 同源）
  private val FMT_FP32 = 0.U(2.W)
  private val FMT_FP16 = 1.U(2.W)
  private val FMT_BF16 = 2.U(2.W)

  val io = IO(new Bundle {
    val lineIn   = Input(UInt((cfg.totalN * cfg.cEffW).W))  // 一行 cEffW 位槽（低 40 位有效）
    val outFmt   = Input(UInt(2.W))                          // 00=fp32 01=fp16 10=bf16 11=reserved(同 00)
    val rnd      = Input(RoundingMode())                     // 舍入模式（与 regFile.rnd 同源）
    val packed32 = Output(UInt((cfg.totalN * 32).W))         // fp32 密集行（低列号在低位）
    val packed16 = Output(UInt((cfg.totalN * 16).W))         // fp16/bf16 密集行（按 outFmt 选路）
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
}
