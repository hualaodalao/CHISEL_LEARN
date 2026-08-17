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
  *                        乘积 32→cEffW 零扩展/截断后送加法）
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

/** HiveMacFp（浮点 MAC，纯算术单元）
  * 内部例化 HiveMulFp + HiveAddFp：fp32 乘（rnd 舍入），32 位积适配到 cW 位
  * （cW>32 零扩展 / cW==32 直通 / cW<32 截低位）后送加法器。
  * 不做 fmt 门控：fmt 通路选择职责在 HiveWorkUnit 顶层。纯组合。
  */
class HiveMacFp(cW: Int) extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(32.W))
    val b   = Input(UInt(32.W))
    val c   = Input(UInt(cW.W))
    val rnd = Input(RoundingMode())
    val sum = Output(UInt(cW.W))
  })
  val mul = Module(new HiveMulFp)
  mul.io.a := io.a
  mul.io.b := io.b
  mul.io.rnd := io.rnd

  // 乘积位宽适配：32 → cW（fp32 积：cW>32 零扩展）
  private val prodCvt: UInt =
    if (cW == 32) mul.io.prod
    else if (cW > 32) Cat(0.U((cW - 32).W), mul.io.prod)
    else mul.io.prod(cW - 1, 0)

  val add = Module(new HiveAddFp(cW))
  add.io.cReg := io.c
  add.io.prod := prodCvt
  add.io.rnd := io.rnd
  io.sum := add.io.sum
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

  require(hasInt || hasFp, "HiveWorkUnit: supportedFmts 不能为空（至少含一种支持格式）")
  if (hasFp)  require(cEffW >= 32,         s"HiveWorkUnit: 含浮点格式时 cEffW($cEffW) 必须 >= 32")
  if (hasInt) require(cEffW >= aW + bW,    s"HiveWorkUnit: 含整数格式时 cEffW($cEffW) 必须 >= aW($aW) + bW($bW)")

  val io = IO(new Bundle {
    val a    = Input(UInt(aW.W))
    val b    = Input(UInt(bW.W))
    val cReg = Input(UInt(cEffW.W))
    val fmt  = Input(DataFormat())
    val rnd  = Input(RoundingMode())
    val sum  = Output(UInt(cEffW.W))
  })

  // --- 整数通路（hasInt 时生成：操作数转换 ×2 + HiveMacInt） ---
  // 未启用时 sumInt 恒 0（不生成任何硬件）
  val sumInt: UInt = if (hasInt) {
    val aCvt = Module(new HiveCvtOpInt(aW))
    aCvt.io.in  := io.a
    aCvt.io.fmt := io.fmt
    val bCvt = Module(new HiveCvtOpInt(bW))
    bCvt.io.in  := io.b
    bCvt.io.fmt := io.fmt
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
    aCvt.io.fmt := io.fmt
    val bCvt = Module(new HiveCvtOpFp)
    bCvt.io.in  := bFpIn
    bCvt.io.fmt := io.fmt
    val macFp = Module(new HiveMacFp(cEffW))
    macFp.io.a   := aCvt.io.out
    macFp.io.b   := bCvt.io.out
    macFp.io.c   := io.cReg
    macFp.io.rnd := io.rnd
    macFp.io.sum
  } else 0.U(cEffW.W)

  // --- 累加通路选择（整数/浮点按 fmt 分离） ---
  io.sum := ((hasInt, hasFp) match {
    case (true, false)  => sumInt
    case (false, true)  => sumFp
    case (true, true)   =>
      val isInt = io.fmt === DataFormat.INT8 || io.fmt === DataFormat.INT16
      Mux(isInt, sumInt, sumFp)
    case (false, false) => io.cReg       // 穷尽兜底：构造处 require 已保证不可达
  })
}
