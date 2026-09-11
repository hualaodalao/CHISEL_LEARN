/** HiveCore dmaC 写回精度转换模块（P1：握手化独立模块 = 转换核 + 行寄存器）。
  *
  * 【P1 两段式契约】cvt = 转换+行寄存器；拍拆分归下游 cDma（多拍发射器）：
  *   - in: slave Stream，数据宽 = totalN × cEffW（转换前原始累加行，每槽 cEffW 位，
  *     低列号在低位）。fire 拍把行数据连同 outFmt/rnd/blkSel/nEff 一起锁存进行寄存器
  *     （行 + 配置原子提交，持行期间上游配置变化不影响本行转换）——该行寄存器是全设计
  *     唯一一份行寄存器（P2 吸收现 ActiveRowSplit 职责，本期在 cvt 内完成行锁存）。
  *   - out: master Stream，数据宽 = totalN × 32（密集行：按 outFmt 选段有效，高位无效段
  *     填零，见下方选段表）。持行直到 out.fire（行寄存器不被新行覆盖）。
  *   - scaleOut: master Stream，宽 = cScaleRowW（每 8 元素块 1 字节 E8M0，低块号在低位）。
  *     量化行与 out 同拍 valid；非量化行 scaleOut.valid 恒 0。
  *   - 原子行提交：释放 = out.fire && (!量化 || scaleOut.fire)；量化行若 out 先被接受而
  *     scale 未就绪，进入「数据已发、scale 待交」子态（out.valid 撤、scaleOut.valid 保持），
  *     scaleOut.fire 后释放——行/量化的 scale 配对永不错位、不丢失。
  *     in.ready = 行寄存器空闲（未持行，或本拍恰为释放拍，支持背靠背直通）。
  *
  * outFmt 编码（4-bit；来源 = HiveCoreRegister.cFmt := regs(7)(11,8).asTypeOf(DataFormat())，
  *   顶层 HiveCore 直连 cvt.io.outFmt := regFile.cFmt。编码定义已收敛至 HiveEnum.DataFormat
  *   枚举：成员声明即隐式 id 顺延【无显式 Value(n)】，宽度 = 最大 id INT4(9) 的 bitLength = 4）：
  *   0=FP32（默认臂）/ 1=FP16 / 2=BF16 / 3=mxfp8-E4M3 量化 / 4=mxfp8-E5M2 量化 /
  *   5=mxfp4-E2M1 量化 / 6=INT32 / 7=INT16 / 8=INT8 / 9=INT4（#137 补入：整数族四档
  *   同为「每槽截断取低位、不饱和、无舍入」，共用 int32Seq 单一数据源，无双源）；
  *   10..15 reserved：枚举无对应成员 ⟹ MuxCase 全部谓词为假 ⟹ 落默认臂 fp32
  *   （仅为 cvt 兜底；execute 拍先被族校验拦截，不参与任何量化选路，故不设枚举值）。
  *   【废止留痕】旧「bit16=整数族选择位 + 低 3 位旧编码」布局（0xxx/1xxx 拆分、
  *   1000=int32/1001=int16/1010=int8、量化判据 bit16=0 且低 3 位 ∈ {4,5,6}、
  *   「旧软件不写 bit16 零迁移」）随 OutFormat/DataFormat 合并为单一 DataFormat 枚举
  *   而整体作废：本模块已无任何位拆分判据，一律整值枚举比较。
  *   量化判据（本模块 scaleOut.valid 门）= isQuantFmt = DataFormat.isMx(fmtReg)
  *   = {MXE4M3(3), MXE5M2(4), MXE2M1(5)}。INT4 ∉ isMx ⟹ INT4 输出【无块 scale 语义】：
  *   scaleOut.valid 恒 0、不产生 scaleC 行。INT4 与 MXE2M1 仅在打包宽度上对称
  *   （HiveEnum.isHalfB = INT4 || MXE2M1 ⟹ CalcConfig 侧 8B/行、beats=1），
  *   量化/scale 语义不共享——INT4 是定点截断档，不是量化档。
  *
  * out 密集行选段（低列号元素在低位，无效高位段填零）：
  *   fp32/int32 = 全 totalN×32；fp16/bf16/int16 = 低 totalN×16；
  *   mxfp8/int8 = 低 totalN×8；mxfp4/int4 = 低 totalN×4
  *   （lane i 落 nibble i = bits[4i+3:4i]，lane0 在最低位；与 mxfp4 臂同序）。
  *
  * 例化约束：含浮点或 MX（cfg.hasFp || cfg.hasMx）时要求 cEffW >= 40（延迟规格化
  * {sign,exp8,mant31} 槽约束）；纯整数配置仅走 int 支路（槽低位截取，不依赖 40 位槽），
  * require 放宽为 cEffW >= 8（int8 截断下限；#137 int4 仅需 4 位，被该下限天然覆盖，
  * 未再放宽），fp/量化数值云编译期置零不激励。
  *
  * 【数值核（P1 零改动留痕）】以下转换语义与握手化前逐字一致：
  * （#137 只新增 int4 选段臂 + isInt4Fmt 谓词，fp16/bf16/fp32/MX 量化数值核与
  *  整数族既有三档 packedInt32/16/8 逐字未动；int4 语义派生见「整数族支路」区留痕。）
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
  * 例化约束留痕：旧版「仅含浮点或 MX 配置例化」随 P1 int 支路放宽——纯整数配置亦可例化（仅 int 支路有效）。
  */

import chisel3._
import chisel3.util._

class HiveOutCvt(cfg: HiveCoreConfig) extends Module {
  // 延迟规格化格式槽约束：40-bit {sign,exp8,mant31} 必须落在 cEffW 槽低位。
  // P1 按格式族条件化：含 fp/mx 时原 require 语义不变；纯整数配置仅走 int 支路（槽低位截取），
  // 放宽为 cEffW >= 8（int8 截断下限；int32 支路对 <32 位槽零扩展后取低 32 位；
  // #137 int4 仅需 4 位，被该下限天然覆盖，未再放宽）。
  if (cfg.hasFp || cfg.hasMx) {
    require(cfg.cEffW >= 40, s"HiveOutCvt: cEffW(${cfg.cEffW}) 必须 >= 40（延迟规格化格式需要）")
  } else {
    require(cfg.cEffW >= 8, s"HiveOutCvt: 纯整数配置 cEffW(${cfg.cEffW}) 必须 >= 8（int 支路槽低位截取）")
  }
  // P3a 量化支路按 8 元素组构建块 amax 树：totalN 必须为 8 的整数倍（MX 配置
  // totalN==16 天然满足，此处拦截未来非 8 倍数配置的静默错位）
  require(cfg.totalN % 8 == 0, s"HiveOutCvt: totalN(${cfg.totalN}) 必须为 8 的整数倍（量化块按 8 元素组构建）")
  private val nGroups: Int = cfg.totalN / 8   // 8 元素组数（量化块基础粒度）

  // outFmt 编码（4-bit；来源 = HiveCoreRegister.cFmt := regs(7)(11,8).asTypeOf(DataFormat())）；
  // 编码定义已收敛至 HiveEnum.DataFormat 枚举（成员声明即隐式 id 顺延，无显式 Value(n)，
  // 最大 id = INT4(9) ⟹ 宽度 4-bit）：0=FP32 1=FP16 2=BF16 3=MXE4M3 4=MXE5M2 5=MXE2M1
  // 6=INT32 7=INT16 8=INT8 9=INT4；10..15 reserved 回落 fp32（枚举无成员，仅为 cvt MuxCase
  // 默认臂兜底；execute 拍先被族校验拦截，不参与任何量化选路，故不设枚举值）。

  val io = IO(new Bundle {
    val in       = Flipped(Decoupled(UInt((cfg.totalN * cfg.cEffW).W)))  // slave：转换前原始累加行（低列号在低位）
    val outFmt   = Input(DataFormat())                          // 4-bit 编码（in.fire 拍随行锁存，见类头）
    val rnd      = Input(RoundingMode())                     // 舍入模式（与 regFile.rnd 同源）
    val blkSel   = Input(MXBlockSize())                          // 量化块大小：00=8 01=16 10=32 11=64（元素/块）
    val nEff     = Input(UInt(log2Up(cfg.totalN + 1).W))     // 有效列数（≥ nEff 列 = padding）
    val out      = Decoupled(UInt((cfg.totalN * 32).W))      // master：密集行（按 outFmt 选段，高位填零）
    val scaleOut = Decoupled(UInt(cfg.cScaleRowW.W))         // master：量化 E8M0 scale 行（非量化行 valid 恒 0）
  })

  // ==========================================================================
  // 行寄存器（全设计唯一一份，P2 吸收 ActiveRowSplit 职责）+ 配置随行锁存：
  // in.fire 拍原子提交行 + outFmt/rnd/blkSel/nEff；释放前新行无法覆盖。
  // ==========================================================================
  private val holdValid = RegInit(false.B)                              // 行寄存器有效（持行中）
  private val lineReg   = RegInit(0.U((cfg.totalN * cfg.cEffW).W))      // 行数据寄存器（确定初值 0，仿真无 X）
  private val fmtReg    = RegInit(DataFormat.FP32)                      // 锁存 outFmt（默认 fp32；DataFormat.Type）
  private val rndReg    = RegInit(RoundingMode.RNE)                     // 锁存舍入模式
  private val blkReg    = RegInit(MXBlockSize.M8)                             // 锁存量化块大小（默认 8）
  private val nEffReg   = RegInit(cfg.totalN.U(log2Up(cfg.totalN + 1).W))  // 锁存有效列数（默认全行）
  // 量化行子态：out 已交付但 scaleOut 未提交（out.valid 撤、scaleOut.valid 保持）
  private val dataDone  = RegInit(false.B)
  // 【P3 修复 5：scale 单次交付锁存（任务90 收敛落盘）】每行 scale 只允许 push 一次：
  // scaleOut.valid &= !scaleSent；scaleOut.fire 置位；新行载入（in.fire）/释放清零。
  // 无此锁存时，行在等下游行锁存腾空的持有窗口内（>1 拍）若 cScaleFifo 恒有空位，
  // scaleOut 会每拍重复 fire（实测 typical 双 MX 用例每行 ~2 次）：scale 序列错位 ±1 档、
  // cDmaScale 按行计数提前「假满」停摆 → FIFO 塞满 → 释放条件永假 → 全链死锁。
  // 释放判据同步放宽为「scale 已交即可随 out.fire 释放」（见下方 release）。
  private val scaleSent = RegInit(false.B)

  // 格式族判据（一律整值枚举比较，无位拆分；与 HiveEnum 谓词同源）：
  //   int 族（cvt 选段用）= INT32(6)/INT16(7)/INT8(8)/INT4(9)，INT4 由 #137 补入。
  //     不引用 HiveEnum.isInt（= INT8||INT16||INT4，不含 INT32）作选段判据：本模块
  //     需要四档各自独立选段宽度，逐值判定避免把 INT32 漏在族外。
  //   量化族 = isQuantFmt = DataFormat.isMx(fmtReg) = {MXE4M3(3),MXE5M2(4),MXE2M1(5)}。
  //     【门控核实留痕（#137 要求）】INT4 ∉ isMx ⟹ cFmt=INT4 时 isQuantFmt=false ⟹
  //     io.scaleOut.valid = holdValid && isQuantFmt && ... 恒 0、release 判据退化为
  //     仅 out.fire（非量化路径）⟹ INT4 行【不产生 scaleC】，与类头「无块 scale 语义」
  //     一一对应；该门控为既有代码现状，#137 未新增任何 scale 分支。
  private val isInt32Fmt = fmtReg === DataFormat.INT32
  private val isInt16Fmt = fmtReg === DataFormat.INT16
  private val isInt8Fmt  = fmtReg === DataFormat.INT8
  private val isInt4Fmt  = fmtReg === DataFormat.INT4
  private val isQuantFmt = DataFormat.isMx(fmtReg)

  // --- 握手：原子行提交（契约见类头） ---
  // 释放 = out.fire 且（非量化 || scale 已交/同拍提交）；或 dataDone 子态下 scaleOut.fire 补交。
  // 【P3 修复 5】量化行释放判据放宽：scaleSent 已置位时（scale 已交付一次）允许
  // 仅凭 out.fire 释放，无需再等 scaleOut.fire（否则 scaleSent 压制 valid 后释放永假 → 死锁）。
  private val release = (io.out.fire && (!isQuantFmt || scaleSent || io.scaleOut.fire)) ||
                        (dataDone && io.scaleOut.fire)
  io.in.ready       := !holdValid || release          // 释放拍可背靠背直通锁存新行
  io.out.valid      := holdValid && !dataDone         // dataDone 子态下数据已交付，不重发
  // 【P3 修复：scale-skew 幽灵双推】原式 holdValid && isQuantFmt 在 out 被下游反压
  // （out.ready=0）而 scale FIFO 有空的拍单独 fire，同一持行产出多份 scale（实测：
  // 每被阻拍多推一份，幽灵条目致 cScaleFifo 多行、cScaleDma 行计数与数据行脱同后
  // 死锁：数据 35/64 行停发、scale 乱序）。收紧为与 out 交付原子配对：仅当
  // out 本拍可 fire（out.ready）或 dataDone 补交子态时才允许 scale fire。
  // 【P3 修复 5】追加 !scaleSent 门：scale 每行至多交付一次（重复 push 与假满死锁根因，
  // 见 scaleSent 声明处留痕）；(dataDone || out.ready) 门保留（防 scale 超前于 out 交付的幽灵序）。
  io.scaleOut.valid := holdValid && isQuantFmt && !scaleSent && (dataDone || io.out.ready)
  when(io.scaleOut.fire) {
    scaleSent := true.B                               // scale 已交：本行不再重复 push（last-connect 在 in.fire/release 清零）
  }
  when(io.out.fire && isQuantFmt && !io.scaleOut.fire) {
    dataDone := true.B                                // scale 未就绪：持行等 scale，数据不重发（同拍 scaleSent 置位时本条件恒假）
  }
  when(release) {
    holdValid := false.B
    dataDone  := false.B
    scaleSent := false.B                              // 释放清零：下一行重新允许一次交付（同拍 in.fire 直通由下方 last-connect 覆盖）
  }
  when(io.in.fire) {                                  // last-connect：覆盖上方 release 清零（直通）
    lineReg   := io.in.bits
    fmtReg    := io.outFmt
    rndReg    := io.rnd
    blkReg    := io.blkSel
    nEffReg   := io.nEff
    holdValid := true.B
    scaleSent := false.B                              // 新行载入清零（任务90 裁定：行级原子性）
  }

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
  // 逐元素转换：40→32 规格化（与现状内联实现同源）→ 32→16 降精度。
  // P1：数据源由 io.lineIn 改为行寄存器 lineReg（握手化唯一改动，数值核不变）；
  // 纯整数配置（!hasFp && !hasMx）时 cEffW 可 <40，槽(39,0) 截取不合法，
  // fp/量化数值云编译期置零不激励（int 支路在下方独立生成）。
  // ==========================================================================
  private val fp32Seq: Seq[UInt] = if (cfg.hasFp || cfg.hasMx) {
    (0 until cfg.totalN).map { i =>
      val slice = lineReg(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
      Fp32.fpNormalize40to32(slice(39, 0))
    }
  } else {
    Seq.fill(cfg.totalN)(0.U(32.W))
  }
  private val fp16Seq: Seq[UInt] = fp32Seq.map(x => fp32ToFp16Rnd(x, rndReg))
  private val bf16Seq: Seq[UInt] = fp32Seq.map(x => fp32ToBf16Rnd(x, rndReg))

  // 密集打包：Cat(reverse) 使元素 0 落最低位（与 popDataFpNorm 列序一致）；
  // P1 由 io 直出改为内部线，统一汇入 io.out.bits 选段（选段逻辑见文件尾部）。
  private val packed32: UInt = Cat(fp32Seq.reverse)
  private val packed16: UInt = Mux(fmtReg === DataFormat.FP16, Cat(fp16Seq.reverse), Cat(bf16Seq.reverse))

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
  private val isQE4M3 = fmtReg === DataFormat.MXE4M3
  private val isQE5M2 = fmtReg === DataFormat.MXE5M2
  // E2M1 不单独判定：isQE4M3、isQE5M2 均为 false 时量化组合逻辑自然落入 E2M1 参数（q2Seq）。
  // 非量化编码（FP32/FP16/BF16/INT32/INT16/INT8/INT4 与 reserved 10..15）：isQuantFmt=false
  // （不在量化白名单 isMx = {MXE4M3(3), MXE5M2(4), MXE2M1(5)}），不引发量化输出、不产生
  // scale 输出（scaleOut.valid 恒 0）——量化组合逻辑产出的值不被 out.bits 的 MuxCase 采样，
  // 硬件行为与类头编码表一致。
  // 【#137 校正留痕】旧文「0111=Reserved7 / 不在量化白名单 {4,5,6}」引用的是已废止的
  // bit16 拆分编码布局（旧 4=E4M3 / 5=E5M2 / 6=E2M1、0111=reserved）；OutFormat/DataFormat
  // 合并为单一枚举后编码为 3/4/5、reserved 为 10..15，判据一律整值 isMx 比较，无位拆分。

  // --- 块 amax：符号位清零取幅值（31-bit 无符号比较即 |fp32| 单调序）；
  // 列 ≥ nEff 的 padding 元素掩码为 0（不进 amax） ---
  private val magSeq: Seq[UInt] = (0 until cfg.totalN).map { i =>
    Mux(i.U < nEffReg, fp32Seq(i)(30, 0), 0.U(31.W))
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
  private def elemAmax(i: Int): UInt = Mux(blkReg === MXBlockSize.M8, groupAmax(i / 8),
    Mux(blkReg === MXBlockSize.M16, amax16(i / 16),
      Mux(blkReg === MXBlockSize.M32, amax32(i / 32), amax64(i / 64))))

  // --- E8M0 块共享 scale（推导见上方分区注释；纯指数域组合逻辑） ---
  private def blockScale(amax: UInt, p: MxFormatParams): UInt = {
    val aExp  = amax(30, 23)
    val aMant = amax(22, 0)
    val blockZero = amax === 0.U
    val blockInf  = aExp === 255.U
    // 1.f > maxMantVal（目标格式最大尾数值左对齐到 23 位）→ 指数提 1 再容下
    val needBump = aMant > p.maxMant23.U(23.W)
    val eEff = Mux(needBump, aExp +& 1.U, Cat(0.U(1.W), aExp))   // 9-bit
    val sSum  = eEff +& p.bias.U                                  // E_eff + bias
    val sDiff = sSum - p.maxCodeExp.U                             // s = E_eff + bias - maxCodeExp（可绕回）
    val sSmall = sSum <= p.maxCodeExp.U                           // s < 1 → 钳制 1（见分区注释）
    Mux(blockZero, 0.U(8.W),
      Mux(blockInf, 254.U(8.W),
        Mux(sSmall, 1.U(8.W),
          Mux(sDiff > 254.U, 254.U(8.W), sDiff(7, 0)))))
  }

  // --- 逐元素量化（指数减后舍入到目标格式；边界表镜像 Fp32.mxToFp32 取逆） ---
  // @param p 目标格式参数表（MxFormatParams 单例，字段语义见 HiveEnum）：
  //        p.maxCodeExp = 正常数最大指数码（E4M3=15：exp 全1 配 mant≤6 仍有限；
  //        E5M2=30：exp 全1 为 inf/nan；E2M1=3：无特殊编码），与 blockScale 侧取值一致共用；
  //        p.satCode 饱和（最大有限值）编码（不含符号位）；p.nanCode nan 映射编码；
  //        p.nanToSat nan 输入饱和处理（E2M1 无 nan 编码的裁定）；
  //        p.fixNanCode 舍入落入 NaN 编码时改写为饱和（仅 E4M3：exp 全1 mant 全1）
  private def quantElem(x: UInt, s: UInt, rnd: RoundingMode.Type, p: MxFormatParams): UInt = {
    val sign = x(31)
    val e    = x(30, 23)
    val man  = x(22, 0)
    val tailW = p.expW + p.mantW
    val signedZero = Cat(sign, 0.U(tailW.W))
    val satFull    = Cat(sign, p.satCode.U(tailW.W))
    val nanFull    = Cat(sign, p.nanCode.U(tailW.W))

    val isZero = e === 0.U                    // ±0 / fp32 次正规 → FTZ 保号 ±0（同硬件族语义）
    val isInf  = (e === 255.U) && (man === 0.U)
    val isNan  = (e === 255.U) && (man =/= 0.U)

    // 尾数截断（静态位选，同 fp32ToFp16Rnd 结构）：保留高 mantW 位，
    // guard = 首个被截断位，sticky = 其余被截断位或，lsb = 保留位最低位
    val drop    = 23 - p.mantW
    val mantTop = man(22, drop)
    val guard   = man(drop - 1)
    val sticky  = man(drop - 2, 0).orR
    val roundUp = roundUpN(p.mantW, rnd, guard, sticky, mantTop(0), sign)
    val manRnd  = mantTop +& roundUp          // (mantW+1)-bit，bit(mantW) = 尾数进位
    val mCarry  = manRnd(p.mantW)
    val manFin  = Mux(mCarry, 0.U(p.mantW.W), manRnd(p.mantW - 1, 0))

    // 指数减：÷ 2^(s-127) ⟺ 目标偏置指数 = e + bias - s（有符号比较防绕回）
    val eTgt   = (Cat(0.U(1.W), e).asSInt + p.bias.S) - Cat(0.U(1.W), s).asSInt
    val eRnd   = eTgt + Mux(mCarry, 1.S, 0.S)
    val underflow = eTgt < 1.S                // 低于目标最小正规格化 → 保号 ±0（无次正规产出）
    val overflow  = eRnd > p.maxCodeExp.S     // 舍入后溢出 → 饱和到最大有限值（保号）
    // E4M3 专属修正：舍入落到 (exp 全1, mant 全1) = NaN 编码时改写为饱和值 0x7E（448），
    // 与「饱和到最大有限值」裁定一致（正常值域内不可能产出 NaN 编码）
    val nanFix = p.fixNanCode.B && (eRnd === p.maxCodeExp.S) && (manFin === ((1 << p.mantW) - 1).U(p.mantW.W))
    val normal = Cat(sign, eRnd.asUInt(p.expW - 1, 0), manFin)

    Mux(isZero || underflow, signedZero,
      Mux(isInf, satFull,                     // inf → 饱和最大有限值（目标格式无 inf 编码或裁定饱和）
        Mux(isNan, Mux(p.nanToSat.B, satFull, nanFull),
          Mux(overflow || nanFix, satFull, normal))))
  }

  // 三格式各自例化（静态常量表，字面量收敛为 MxFormatParams 三单例，
  // 字段语义注释表统一移至 HiveEnum.MxFormatParams，逐条镜像 Fp32 反量化侧字段语义）：
  private val s4Elem: Seq[UInt] = (0 until cfg.totalN).map(i => blockScale(elemAmax(i), MxFormatParams.E4M3))
  private val s5Elem: Seq[UInt] = (0 until cfg.totalN).map(i => blockScale(elemAmax(i), MxFormatParams.E5M2))
  private val s2Elem: Seq[UInt] = (0 until cfg.totalN).map(i => blockScale(elemAmax(i), MxFormatParams.E2M1))
  private val q4Seq: Seq[UInt] = fp32Seq.zip(s4Elem).map { case (x, sc) =>
    quantElem(x, sc, rndReg, MxFormatParams.E4M3)
  }
  private val q5Seq: Seq[UInt] = fp32Seq.zip(s5Elem).map { case (x, sc) =>
    quantElem(x, sc, rndReg, MxFormatParams.E5M2)
  }
  private val q2Seq: Seq[UInt] = fp32Seq.zip(s2Elem).map { case (x, sc) =>
    quantElem(x, sc, rndReg, MxFormatParams.E2M1)
  }
  // outFmt 三选一 + nEff 掩码（列 ≥ nEff 的 padding 元素输出 0：不参与量化产出）
  private val qSelSeq: Seq[UInt] = (0 until cfg.totalN).map { i =>
    val code = Mux(isQE4M3, q4Seq(i), Mux(isQE5M2, q5Seq(i), q2Seq(i)))
    Mux(i.U < nEffReg, code, 0.U(8.W))
  }
  private val packed8: UInt = Cat(qSelSeq.reverse)

  // packed4：E2M1 两元素一字节，低列号占低 nibble（q2Seq 同样经 nEff 掩码）
  private val packed4: UInt = Cat((0 until cfg.totalN / 2).reverse.map { p =>
    val lo = Mux((2 * p).U     < nEffReg, q2Seq(2 * p),     0.U(4.W))
    val hi = Mux((2 * p + 1).U < nEffReg, q2Seq(2 * p + 1), 0.U(4.W))
    Cat(hi, lo)
  })

  // scaleRow：每 8 元素组 1 字节（低组号在低位），按 blkSel 共享同块 scale；
  // cScaleRowW = nGroups*8，恰无未覆盖高位（更大阵列时未覆盖字节天然 0）
  private def fmtScalesAt(g: Int): Seq[UInt] = {
    // 三级块界各层 scale（同层各组 amax 已合并，字节值与 elemAmax 侧同源一致）
    val fmt8  = Mux(isQE4M3, blockScale(groupAmax(g), MxFormatParams.E4M3),
                Mux(isQE5M2, blockScale(groupAmax(g), MxFormatParams.E5M2),
                             blockScale(groupAmax(g), MxFormatParams.E2M1)))
    Seq(fmt8)
  }
  private val scaleWire: UInt = Cat((0 until nGroups).reverse.map { g =>
    val s8 = fmtScalesAt(g).head
    val s16 = Mux(isQE4M3, blockScale(amax16(g / 2), MxFormatParams.E4M3),
              Mux(isQE5M2, blockScale(amax16(g / 2), MxFormatParams.E5M2),
                           blockScale(amax16(g / 2), MxFormatParams.E2M1)))
    val s32 = Mux(isQE4M3, blockScale(amax32(g / 4), MxFormatParams.E4M3),
              Mux(isQE5M2, blockScale(amax32(g / 4), MxFormatParams.E5M2),
                           blockScale(amax32(g / 4), MxFormatParams.E2M1)))
    val s64 = Mux(isQE4M3, blockScale(amax64(g / 8), MxFormatParams.E4M3),
              Mux(isQE5M2, blockScale(amax64(g / 8), MxFormatParams.E5M2),
                           blockScale(amax64(g / 8), MxFormatParams.E2M1)))
    Mux(blkReg === MXBlockSize.M8, s8,
      Mux(blkReg === MXBlockSize.M16, s16,
        Mux(blkReg === MXBlockSize.M32, s32, s64)))
  })

  // ==========================================================================
  // P1 整数族支路（纯组合，从行寄存器槽低位截取：截断取低位、不饱和；
  // 整数累加位宽由既有 require 防溢出）。对 <32 位槽先零扩展再取段，
  // int16/int8/int4 统一从 32 位扩展值取低 16/8/4 位（与 int32 同源、无双源）。
  //
  // 【#137 INT4 语义派生留痕：逐条对照 INT8 臂，零自创】
  //   ① 数据源：INT8 臂用 int32Seq（槽低 32 位/零扩展）→ INT4 臂复用同一 int32Seq，
  //      不新增取数路径（杜绝双源漂移）。
  //   ② 舍入：INT8 臂【不调用】roundUp16/roundUpN，rndReg 完全不参与整数支路
  //      → INT4 臂同样忽略 rnd（5 模式输出恒同，spec 有定向断言）。
  //   ③ 饱和：INT8 臂 _(7,0) 硬截断，超范围值按二进制补码【绕回】而非钳位到
  //      [-128,127]（类头「截断取低位、不饱和」）→ INT4 臂 _(3,0) 同构收窄：
  //      超范围值绕回 mod 16，【不】钳位到 4-bit 有符号范围 [-8,7]。
  //      裁定依据：钳位将是本仓从未存在的第三种语义（INT8/INT16/INT32 三档均无钳位），
  //      且与金锚 HiveDebugModel.decodeElem 的 INT4 分支 sext(bits,4)（纯二补码符号
  //      扩展、无饱和分支）冲突 ⟹ 依「不得自创语义」取截断绕回。
  //   ④ 符号/负零：INT8 臂原样保留 bit7（二补码无负零，-0 ≡ 0x00）→ INT4 臂原样
  //      保留 bit3（-0 ≡ 0x0）；两档均无符号扩展/符号特殊处理。
  //   ⑤ nEff 掩码：INT8 臂【不掩码】（padding 列输出真实截断位，区别于量化支路
  //      packed8/qSelSeq 的掩码语义）→ INT4 臂同样不掩码，保持整数族四档一致。
  //   ⑥ lane→nibble 序：INT8 臂 Cat(int32Seq.reverse) ⟹ lane i 落 byte i（bits[8i+7:8i]）、
  //      lane0 最低位 → INT4 臂同式 ⟹ lane i 落 nibble i（bits[4i+3:4i]）、lane0 最低位。
  //      与 MXE2M1 臂 packed4 的逐字节组装（Cat(hi=elem(2p+1), lo=elem(2p))，外层 reverse）
  //      位级等价：elem 2p 落低 nibble、elem 2p+1 落高 nibble ⟺ elem i 落 bits[4i+3:4i]。
  // ==========================================================================
  private val int32Seq: Seq[UInt] = (0 until cfg.totalN).map { i =>
    val slot = lineReg(cfg.cEffW * (i + 1) - 1, cfg.cEffW * i)
    if (cfg.cEffW >= 32) slot(31, 0)
    else Cat(0.U((32 - cfg.cEffW).W), slot)
  }
  private val packedInt32: UInt = Cat(int32Seq.reverse)
  private val packedInt16: UInt = Cat(int32Seq.reverse.map(_(15, 0)))
  private val packedInt8: UInt  = Cat(int32Seq.reverse.map(_(7, 0)))
  // INT4：与 packedInt8 同形、位宽 8→4 同构收窄（语义派生见上方 ①..⑥ 留痕）
  private val packedInt4: UInt  = Cat(int32Seq.reverse.map(_(3, 0)))

  // ==========================================================================
  // out 密集行选段（契约见类头）：默认臂 = fp32，#137 起【仅】覆盖 reserved 编码
  // 10..15（DataFormat 枚举无成员 ⟹ 九条谓词全假）；INT4(9) 已有专属臂，不再回落。
  // 量化/16/8/4/int 支路按选中格式落低位、高位零填充（int4/mxfp4 同为 totalN×28 填零）。
  // ==========================================================================
  io.out.bits := MuxCase(packed32, Seq(
    (fmtReg === DataFormat.FP16)   -> Cat(0.U((cfg.totalN * 16).W), packed16),
    (fmtReg === DataFormat.BF16)   -> Cat(0.U((cfg.totalN * 16).W), packed16),
    (fmtReg === DataFormat.MXE4M3) -> Cat(0.U((cfg.totalN * 24).W), packed8),
    (fmtReg === DataFormat.MXE5M2) -> Cat(0.U((cfg.totalN * 24).W), packed8),
    (fmtReg === DataFormat.MXE2M1) -> Cat(0.U((cfg.totalN * 28).W), packed4),
    isInt32Fmt                    -> packedInt32,
    isInt16Fmt                    -> Cat(0.U((cfg.totalN * 16).W), packedInt16),
    isInt8Fmt                     -> Cat(0.U((cfg.totalN * 24).W), packedInt8),
    isInt4Fmt                     -> Cat(0.U((cfg.totalN * 28).W), packedInt4)
  ))
  // scaleOut：valid 门见上方握手块；数值 = scaleWire（持行期 lineReg 恒定，组合安全）
  io.scaleOut.bits := scaleWire
}
