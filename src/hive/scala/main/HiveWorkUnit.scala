/** HiveWorkUnit（工作单元）- 可重构乘加计算模块族
  *
  * 由原 WorkUnit object 的函数实现（multiply/accumulate）重构为独立
  * Module 层次：综合时每个子模块生成独立模块文件，便于按模块统计
  * 工作单元内部各运算部件在 PE/阵列中的面积占比。
  *
  * 层次结构（整数与浮点通路完全分离，无共享 mux 混合实现）：
  *   HiveWorkUnit（顶层：fmt/rnd 控制与数据通路拼接，纯组合）
  *   ├── HiveCvtOpInt ×2: 整数操作数转换（a/b 各一实例：补零到 16 位，
  *   │                    INT8 取低 8 位符号扩展）
  *   ├── HiveCvtOpFp ×2 : 浮点操作数转换（a/b 各一实例：16 位输入
  *   │                    BF16/FP16 → fp32）
  *   ├── HiveMacInt     : 整数 MAC（内部例化 HiveMulInt + HiveAddInt；
  *   │                    乘积 32→cEffW 符号扩展/截断后送加法）
  *   └── HiveMacFp      : 浮点 MAC（内部例化 HiveMulFp + HiveAddFp；
  *                        乘积 32→cW 零扩展/截断后送加法）
  *   （HiveMulInt/HiveMulFp/HiveAddInt/HiveAddFp 为 Mac 内部子模块，
  *     仍生成独立模块文件便于面积统计）
  *
  * 行为与原 WorkUnit 函数实现完全等价（已用随机激励逐拍 bit-exact 对账）：
  *   - sum = cReg + a × b（仅为 supportedFmts 中声明的格式生成对应硬件，
  *     顶层按 fmt 运行时选择通路；不支持格式的累加由 HiveWorker 层
  *     fmtOk 门控拦截；整数加法 / fp32 加法）
  *
  * @param aW            激活位宽（默认 16）
  * @param bW            权重位宽（默认 16）
  * @param cW            累加器/部分和位宽（默认 48，cEffW = max(cW, bW)）
  * @param supportedFmts 本工作单元支持的数据格式集合（默认全部四种）。
  *                      仅为支持的格式生成 MAC 硬件
  *
  * HiveWorkUnit IO 端口语义：
  *   a / b   : 乘数输入（aW / bW 位，格式由 fmt 决定）
  *   cReg    : 当前累加值输入（cEffW 位，由宿主供给，如 HiveWorker 的 psumIn）
  *   fmt/rnd : 数据格式 / 舍入模式（脉动传入的运行时配置）
  *   sum     : cEffW 位累加结果（整数为有符号和，浮点为 fp32 和）
  *
  * 全部纯组合逻辑（无寄存器）：PE 级状态（wReg/psumReg/fmtReg/rndReg）
  * 仍留在宿主 HiveWorker，经 io 交互。
  */

import chisel3._
import chisel3.util._

/** HiveCvtOpInt（整数操作数类型转换）
  * 输入原始操作数（inW 位）：补零到 16 位；INT8 时取低 8 位符号扩展到 16 位。
  * 输出 16 位整数操作数。纯组合，面积占比分析时归入「类型转换」类。
  */
class HiveCvtOpInt(inW: Int) extends Module {
  val io = IO(new Bundle {
    val in    = Input(UInt(inW.W))
    val fmt   = Input(DataFormat())
    val out = Output(UInt(16.W))
  })
  val w16 = math.min(16, inW)
  val pad = if (w16 < 16) Cat(0.U((16 - w16).W), io.in(w16 - 1, 0)) else io.in(15, 0)
  // INT8: 取低 8 位符号扩展到 16 位；INT16: 直接使用 16 位
  io.out := Mux(io.fmt === DataFormat.INT8,
    Cat(Fill(8, pad(7)), pad(7, 0)), pad)
}

/** HiveCvtOpFp（浮点操作数类型转换）
  * 输入固定 16 位操作数（顶层负责将窄位宽预补零到 16 位），
  * 按 fmt 输出 BF16→fp32 或 FP16→fp32 的转换结果（32 位）。
  * 纯组合，面积占比分析时归入「类型转换」类。
  */
class HiveCvtOpFp() extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(16.W))
    val fmt = Input(DataFormat())
    val out = Output(UInt(32.W))
  })
  val w16 = 16
  val pad = if (w16 < 16) Cat(0.U((16 - w16).W), io.in(w16 - 1, 0)) else io.in(15, 0)
  
  // 输入转换：根据 fmt 选择 bf16→fp32 或 fp16→fp32
  io.out := Mux(io.fmt === DataFormat.BF16,
    Fp32.bf16ToFp32(pad), Fp32.fp16ToFp32(pad))
}

/** HiveCvtOpMx（MX 操作数类型转换）
  * 输入 8 位 MX 元素（低 8 位有效）+ DataFormat（MXE4M3/MXE5M2）+ 8 位 E8M0 scale，
  * 按 fmt 选择 E4M3→fp32 或 E5M2→fp32，输出 fp32（32 位）。scale 以指数加法并入。
  * inf/nan 断言在此按「被选中的格式」守护——两路转换恒被求值（Mux 双臂），
  * 断言若下放到 Fp32 会因未选中路径而误报，故置于本层且只看选中格式。
  * 纯组合，面积占比分析时归入「类型转换」类。
  */
class HiveCvtOpMx extends Module {
  val io = IO(new Bundle {
    val in    = Input(UInt(8.W))
    val fmt   = Input(DataFormat())
    val scale = Input(UInt(8.W))
    val out   = Output(UInt(32.W))
  })
  val isE5 = io.fmt === DataFormat.MXE5M2
  io.out := Mux(isE5,
    Fp32.mxE5M2ToFp32(io.in, io.scale),
    Fp32.mxE4M3ToFp32(io.in, io.scale))

  // inf/nan 守护（仅对被选中格式断言）：
  //   E5M2 exp 全1 → inf/nan；E4M3 exp 全1 且 mant 全1 → NaN
  val e4 = io.in(6, 3); val m4 = io.in(2, 0)
  val e5 = io.in(6, 2)
  val selInfNan = Mux(isE5, e5.andR, e4.andR && m4.andR)
  assert(!selInfNan,
    "HiveCvtOpMx: MX element is inf/nan (saturated to fp32 max); indicates software-side anomaly")
}


/** HiveMulInt（整数乘法器）
  * 16×16 有符号乘法，INT8/INT16 共用（INT8 已在上游符号扩展）。
  * 输出 32 位有符号积（asUInt）。纯组合。
  */
class HiveMulInt extends Module {
  val io = IO(new Bundle {
    val a    = Input(UInt(16.W))
    val b    = Input(UInt(16.W))
    val prod = Output(UInt(32.W))
  })
  io.prod := (io.a.asSInt * io.b.asSInt).asUInt
}

/** HiveMulFp（浮点乘法器）
  * fp32 乘法，BF16/FP16 共用（输入已在上游转换为 fp32），rnd 控制舍入。
  * 纯组合。
  */
class HiveMulFp extends Module {
  val io = IO(new Bundle {
    val a    = Input(UInt(32.W))
    val b    = Input(UInt(32.W))
    val rnd  = Input(RoundingMode())
    val prod = Output(UInt(32.W))
  })
  io.prod := Fp32.mul(io.a, io.b, io.rnd)
}

/** HiveAddInt（整数累加器）
  * cReg + prod 有符号加法，结果截取低 cW 位（与原实现溢出语义一致）。
  * 纯组合。
  */
class HiveAddInt(cW: Int) extends Module {
  val io = IO(new Bundle {
    val cReg = Input(UInt(cW.W))
    val prod = Input(UInt(cW.W))
    val sum  = Output(UInt(cW.W))
  })
  io.sum := (io.cReg.asSInt + io.prod.asSInt).asUInt(cW - 1, 0)
}

/** HiveAddFp（浮点累加器）
  * 对 cReg/prod 低 32 位做 fp32 加法（rnd 舍入），结果零扩展到 cW 位
  * （cW<32 时该路径不可达，顶层 require 已拦截）。纯组合。
  */
class HiveAddFp(cW: Int) extends Module {
  val io = IO(new Bundle {
    val cReg = Input(UInt(cW.W))
    val prod = Input(UInt(cW.W))
    val rnd  = Input(RoundingMode())
    val sum  = Output(UInt(cW.W))
  })
  val fpResult = Fp32.add(io.cReg(31, 0), io.prod(31, 0), io.rnd)
  io.sum := (if (cW == 32) fpResult
             else if (cW > 32) Cat(0.U((cW - 32).W), fpResult)
             else fpResult(cW - 1, 0))
}

/** HiveMacInt（整数 MAC，纯算术单元）
  * 内部例化 HiveMulInt + HiveAddInt：16×16 有符号乘，32 位积适配到 cW 位
  * （cW>32 符号扩展 / cW==32 直通 / cW<32 截低位）后送加法器。
  * 不做 fmt 门控：不支持的格式编译期就不例化本模块，两通路并存时
  * 由 HiveWorkUnit 顶层按 fmt 运行时选择通路，本模块无需感知 fmt。
  * 纯组合。
  */
class HiveMacInt(cW: Int) extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(16.W))
    val b   = Input(UInt(16.W))
    val c   = Input(UInt(cW.W))
    val sum = Output(UInt(cW.W))
  })
  val mul = Module(new HiveMulInt)
  mul.io.a := io.a
  mul.io.b := io.b

  // 乘积位宽适配：32 → cW（有符号积：cW>32 符号扩展）
  private val prodCvt: UInt =
    if (cW == 32) mul.io.prod
    else if (cW > 32) Cat(Fill(cW - 32, mul.io.prod(31)), mul.io.prod)
    else mul.io.prod(cW - 1, 0)

  val add = Module(new HiveAddInt(cW))
  add.io.cReg := io.c
  add.io.prod := prodCvt
  io.sum := add.io.sum
}

/** HiveMacFp（浮点 MAC，延迟规格化版本）
  * 累加路径跳过 LZC/左移（省面积），输出 40-bit 延迟规格化格式：
  *   {sign(1), exp(8), mantissa(31)}
  * 其中 mantissa 31-bit 不保证 MSB 在 bit30（允许 leading zeros），
  * 规格化推迟到 DMA 写出时统一执行。
  *
  * 要求 cW >= 40，40-bit 结果放入 cW 位中（高位补零）。
  * 不做 fmt 门控：fmt 通路选择职责在 HiveWorkUnit 顶层。纯组合。
  */
class HiveMacFp(cW: Int) extends Module {
  require(cW >= 40, s"HiveMacFp: 延迟规格化需要 cW($cW) >= 40")

  val io = IO(new Bundle {
    val a   = Input(UInt(32.W))    // fp32 multiplicand
    val b   = Input(UInt(32.W))    // fp32 multiplier
    val c   = Input(UInt(cW.W))    // 累加器输入（低 40 bit 是延迟规格化格式）
    val rnd = Input(RoundingMode())
    val sum = Output(UInt(cW.W))   // 累加器输出（低 40 bit 是延迟规格化格式）
  })

  // ==== 乘法：得到标准 fp32 乘积 ====
  val prod32 = Fp32.mul(io.a, io.b, io.rnd)

  // ==== 解包累加器 c (低 40 bit) ====
  val cRaw  = io.c(39, 0)
  val cSign = cRaw(39)
  val cExp  = cRaw(38, 31)
  val cMant = cRaw(30, 0)  // 31-bit，可能有 leading zeros
  val cZero = (cExp === 0.U) && (cMant === 0.U)

  // ==== 解包乘积 ====
  val pSign = Fp32.sign(prod32)
  val pExp  = Fp32.exp(prod32)
  val pMant = Fp32.mant(prod32)
  // 24-bit → 31-bit：隐含1 + 23bit mant + 7bit guard
  val pMant31 = Cat(1.U(1.W), pMant, 0.U(7.W))
  val pZero = (pExp === 0.U) && (pMant === 0.U)

  // ==== 对齐 ====
  val expDiffRaw = pExp.zext - cExp.zext  // 有符号 9-bit
  val pGtC = expDiffRaw >= 0.S
  val shiftAmt = Mux(pGtC,
    Mux(expDiffRaw > 31.S, 31.U(5.W), expDiffRaw.asUInt(4, 0)),
    Mux((-expDiffRaw) > 31.S, 31.U(5.W), (-expDiffRaw).asUInt(4, 0)))
  val baseExp = Mux(pGtC, pExp, cExp)

  // 对齐移位（谁小谁右移）
  val pAligned = Mux(pGtC, pMant31, (pMant31 >> shiftAmt)(30, 0))
  val cAligned = Mux(pGtC, (cMant >> shiftAmt)(30, 0), cMant)

  // ==== 加/减法 ====
  val effectSub = pSign ^ cSign
  // 确定大小数：expDiff 严格 > 0 时 p 必然更大（对齐后 c 被右移，pMant31 前导 1 在
  //   bit30，右移后的 cAligned < 2^30 <= pAligned）；expDiff == 0（pGtC 也为真但不代表
  //   p 更大）与 expDiff < 0 时都必须比较对齐后的尾数 pAligned/cAligned 决定大小数，
  //   否则等指数减法会误判大小数 → sOut 取错符号且 subRes 下溢（表现为该乘积项符号翻转）。
  val pIsLarger = (expDiffRaw > 0.S) || (pAligned >= cAligned)
  val largeM = Mux(pIsLarger, pAligned, cAligned)
  val smallM = Mux(pIsLarger, cAligned, pAligned)
  val sOut   = Mux(pIsLarger, pSign, cSign)

  val addRes = largeM +& smallM  // 32-bit
  val subRes = largeM - smallM   // 31-bit

  // ==== 仅处理上溢，不做 LZC（延迟规格化核心：省面积） ====
  val accMant = WireDefault(0.U(31.W))
  val accExp  = WireDefault(0.U(8.W))

  when(effectSub) {
    // 减法：直接写回，不规格化
    accMant := subRes(30, 0)
    accExp  := baseExp
  }.otherwise {
    when(addRes(31)) {
      // 加法进位：右移 1，指数 +1
      accMant := addRes(31, 1)
      accExp  := baseExp + 1.U
    }.otherwise {
      // 正常：直接写回
      accMant := addRes(30, 0)
      accExp  := baseExp
    }
  }

  // ==== 零值处理 ====
  val accRaw = Cat(sOut, accExp, accMant)  // 40-bit
  val result40 = Mux(pZero, cRaw,
    Mux(cZero, Cat(pSign, pExp, pMant31),
      Mux(effectSub && (subRes === 0.U), 0.U(40.W), accRaw)))

  // ==== 输出：40-bit 放入 cW 位中 ====
  io.sum := (if (cW == 40) result40
             else Cat(0.U((cW - 40).W), result40))
}



/** HiveWorkUnit（工作单元顶层）
  * 负责 fmt/rnd 控制与数据通路拼接：例化操作数转换（a/b）与整数/浮点 MAC，
  * 按 fmt 选择整数或浮点通路。乘积位宽适配已内嵌于 HiveMacInt/HiveMacFp。
  * 详见文件头层次说明。
  */
class HiveWorkUnit(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {

  val cEffW: Int = math.max(cW, bW)

  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
  private val hasMx  = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2)

  require(hasInt || hasFp || hasMx, "HiveWorkUnit: supportedFmts 不能为空（至少含一种支持格式）")
  if (hasFp)  require(cEffW >= 40,         s"HiveWorkUnit: 含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aW + bW,    s"HiveWorkUnit: 含整数格式时 cEffW($cEffW) 必须 >= aW($aW) + bW($bW)")
  // MX 复用 fp32 域延迟规格化累加器（不扩宽），故同样要求 cEffW>=40；
  // 元素固定 8-bit，位于 a/b 低 8 位，故要求 aW/bW>=8
  if (hasMx)  require(cEffW >= 40,         s"HiveWorkUnit: 含 MX 格式时 cEffW($cEffW) 必须 >= 40（复用 fp32 延迟规格化累加器）")
  if (hasMx)  require(aW >= 8 && bW >= 8,  s"HiveWorkUnit: 含 MX 格式时 aW($aW)/bW($bW) 必须 >= 8（MX 元素占低 8 位）")

  val io = IO(new Bundle {
    val a    = Input(UInt(aW.W))
    val b    = Input(UInt(bW.W))
    val cReg = Input(UInt(cEffW.W))
    val aFmt = Input(DataFormat())
    val bFmt = Input(DataFormat())
    // P1: scale 端口先接入（P2 MX MAC 才使用）：scaleA 随激活流，scaleB 列驻留
    val scaleA = Input(UInt(8.W))
    val scaleB = Input(UInt(8.W))
    val rnd  = Input(RoundingMode())
    val sum  = Output(UInt(cEffW.W))
  })

  // P2: scaleA/scaleB 在 hasMx 时经 MX 通路（HiveCvtOpMx）以指数加法并入 fp32；
  // hasMx=false 时二者为未使用输入端口（Chisel 允许悬空 Input，行为 bit-exact）

  // --- 整数通路（hasInt 时生成：操作数转换 ×2 + HiveMacInt） ---
  // 未启用时 sumInt 恒 0（不生成任何硬件）
  val sumInt: UInt = if (hasInt) {
    val aCvt = Module(new HiveCvtOpInt(aW))
    aCvt.io.in  := io.a
    aCvt.io.fmt := io.aFmt
    val bCvt = Module(new HiveCvtOpInt(bW))
    bCvt.io.in  := io.b
    bCvt.io.fmt := io.bFmt
    val macInt = Module(new HiveMacInt(cEffW))
    macInt.io.a := aCvt.io.out
    macInt.io.b := bCvt.io.out
    macInt.io.c := io.cReg
    macInt.io.sum
  } else 0.U(cEffW.W)

  // --- 浮点通路（hasFp 时生成：操作数转换 ×2 + HiveMacFp） ---
  // HiveCvtOpFp 输入固定 16 位，窄位宽在顶层预补零
  private val aFpIn: UInt = if (aW < 16) Cat(0.U((16 - aW).W), io.a) else io.a(15, 0)
  private val bFpIn: UInt = if (bW < 16) Cat(0.U((16 - bW).W), io.b) else io.b(15, 0)
  val sumFp: UInt = if (hasFp) {
    val aCvt = Module(new HiveCvtOpFp)
    aCvt.io.in  := aFpIn
    aCvt.io.fmt := io.aFmt
    val bCvt = Module(new HiveCvtOpFp)
    bCvt.io.in  := bFpIn
    bCvt.io.fmt := io.bFmt
    val macFp = Module(new HiveMacFp(cEffW))
    macFp.io.a   := aCvt.io.out
    macFp.io.b   := bCvt.io.out
    macFp.io.c   := io.cReg
    macFp.io.rnd := io.rnd
    macFp.io.sum
  } else 0.U(cEffW.W)

  // --- MX 通路（hasMx 时生成：MX 操作数转换 ×2 + HiveMacFp 复用） ---
  // MX 走 fp32 域：a/b 各取低 8 位经 HiveCvtOpMx（含 E8M0 scale 指数加法）转为
  // fp32，再复用一份独立 HiveMacFp 实例做 fp32 域累加（不扩宽累加器）。
  // 独立实例（而非复用 sumFp 的 MAC）以保持通路清晰、非 MX 逐 bit 不受影响。
  val sumMx: UInt = if (hasMx) {
    val aCvt = Module(new HiveCvtOpMx)
    aCvt.io.in    := io.a(7, 0)
    aCvt.io.fmt   := io.aFmt
    aCvt.io.scale := io.scaleA
    val bCvt = Module(new HiveCvtOpMx)
    bCvt.io.in    := io.b(7, 0)
    bCvt.io.fmt   := io.bFmt
    bCvt.io.scale := io.scaleB
    val macMx = Module(new HiveMacFp(cEffW))
    macMx.io.a   := aCvt.io.out
    macMx.io.b   := bCvt.io.out
    macMx.io.c   := io.cReg
    macMx.io.rnd := io.rnd
    macMx.io.sum
  } else 0.U(cEffW.W)

  // --- 累加通路选择（整数/浮点/MX 按 fmt 分离） ---
  // baseSel 为原整数/浮点二路选择（与 P1 逐 bit 一致）；仅当 hasMx 时外包一层
  // Mux(isMx, sumMx, baseSel)，故 hasMx=false 时选择逻辑与原二路完全相同。
  // 双 MX 时 aFmt/bFmt 同族，选择逻辑一致，故以 io.aFmt 作 isInt/isMx 判据。
  private val baseSel: UInt = (hasInt, hasFp) match {
    case (true, false)  => sumInt
    case (false, true)  => sumFp
    case (true, true)   =>
      val isInt = io.aFmt === DataFormat.INT8 || io.aFmt === DataFormat.INT16
      Mux(isInt, sumInt, sumFp)
    case (false, false) => io.cReg       // 穷尽兜底：仅 hasMx 时可达，下方 MX 层覆盖
  }
  io.sum := (if (hasMx) Mux(DataFormat.isMx(io.aFmt), sumMx, baseSel) else baseSel)
}
