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
  * 输入 8 位 MX 元素（低 8 位有效）+ DataFormat（MXE4M3/MXE5M2[/MXE2M1]）+ 8 位 E8M0 scale，
  * 按 fmt 选择 E4M3→fp32 或 E5M2→fp32（hasMx4 时再外扩一路 E2M1→fp32），输出 fp32（32 位）。
  * scale 以指数加法并入。
  * inf/nan 断言在此按「被选中的格式」守护——两路转换恒被求值（Mux 双臂），
  * 断言若下放到 Fp32 会因未选中路径而误报，故置于本层且只看选中格式。
  * 【消费有效门控已实施】断言受 consumeValid 门控：仅当本拍 MX 转换结果被真实消费
  * （valid 由 HiveWorker 单行连入，条件 = MX 结果被真实消费的充要条件：
  * io.validIn && fmtOk && isMx(fmtReg)）时才评估，空闲/加载/排空拍的垃圾输入不再
  * 撞编码 $fatal 误报。端口本体恒存在，模块内以 WireDefault(false.B) 兜底，
  * 未覆写时断言永不触发（宁漏报不误杀）。饱和数据通路一字不动。
  * 纯组合，面积占比分析时归入「类型转换」类。
  *
  * 【Phase 0：mxfp4/E2M1 编译期门控】构造参数 hasMx4 是 Scala 层 Boolean，经 Scala `if`
  * 在 elaboration 期展开、不产生任何运行期选择子：
  *   - hasMx4=false（默认，Phase 0 阶段全部实例化点）⟹ io.out 与 selInfNan 的表达式
  *     与泛化前【逐字一致】（二路 Mux(isE5, E5M2, E4M3)），RTL bit-exact 非回归；
  *   - hasMx4=true（Phase 4 的 cfgMxFp4 才会激活）⟹ 外扩第三路 E2M1，选择子
  *     DataFormat.isMx4(io.fmt)。io.in 仍为 8.W：E2M1 只取低 4 位（bit3=sign），
  *     高 4 位由软件保证为 0；E2M1 无 inf/nan 编码，故守护对该路恒不触发。
  */
class HiveCvtOpMx(hasMx4: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val in    = Input(UInt(8.W))
    val fmt   = Input(DataFormat())
    val scale = Input(UInt(8.W))
    // 消费有效：本拍转换结果被下游真实消费时为真（仅门控下方断言，不入数据通路）
    val consumeValid = Input(Bool())
    val out   = Output(UInt(32.W))
  })
  val isE5 = io.fmt === DataFormat.MXE5M2
  io.out := (if (hasMx4) {
    // 三路：E2M1（mxfp4）优先判选，否则落回原二路 E5M2/E4M3
    Mux(DataFormat.isMx4(io.fmt),
      Fp32.mxE2M1ToFp32(io.in, io.scale),
      Mux(isE5,
        Fp32.mxE5M2ToFp32(io.in, io.scale),
        Fp32.mxE4M3ToFp32(io.in, io.scale)))
  } else {
    Mux(isE5,
      Fp32.mxE5M2ToFp32(io.in, io.scale),
      Fp32.mxE4M3ToFp32(io.in, io.scale))
  })

  // inf/nan 守护（仅对被选中格式断言）：
  //   E5M2 exp 全1 → inf/nan；E4M3 exp 全1 且 mant 全1 → NaN；
  //   E2M1（hasMx4）无 inf/nan 编码——最大码 0x7 = ±6.0 是最大有限值，守护恒不触发
  val e4 = io.in(6, 3); val m4 = io.in(2, 0)
  val e5 = io.in(6, 2)
  val selInfNan = if (hasMx4) {
    !DataFormat.isMx4(io.fmt) && Mux(isE5, e5.andR, e4.andR && m4.andR)
  } else {
    Mux(isE5, e5.andR, e4.andR && m4.andR)
  }
  // 消费有效：consumeValid 由唯一父级（HiveWorker）恒驱动；WireDefault(false.B)
  // 仅为构造期占位/防未驱动告警，运行期恒被下方端口连接覆写（Chisel Input 不可
  // 内部驱动，只能经 := 端口值接入）
  val consumeValidEff = WireDefault(false.B)
  consumeValidEff := io.consumeValid
  assert(!consumeValidEff || !selInfNan,
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

/** HiveMacLazyDownstream —— lazy-40（延迟规格化）MAC【下游】共享实现（Phase 5a 抽取）
  *
  * 职责边界：只做「乘积已算好之后」的那一段，即
  *   解包累加器 c → 解包乘积 prod32 → 指数对齐 → 加/减 → 上溢处理 → lazy-40 规格化 → 输出。
  * 【不消费 io.rnd】舍入在上游乘积阶段（Fp32.mul 的 applyRounding / HiveMacFpMx 的
  * roundUpM）就已完成，prod32 已是舍入后的标准 fp32；下游纯组合、与舍入模式无关。
  *
  * 抽取动机：HiveMacFp 与 HiveMacFpMx 各自内联了一份同构副本，Phase 5b-5d 的新融合模块
  * 若不共享会产生第 3 份副本。本 object 是【未来融合模块的唯一调用入口】。
  *
  * 【零 diff 铁律 / 命名前缀陷阱】本 object 目前仅被 HiveMacFpMx 调用；HiveMacFp 的内联副本
  * 按 Phase 5a 禁改区要求【逐字冻结】、不改为调用本 object（Elaborate.scala 的
  * `new HiveMacFp(40)` 单参发射与 Phase 5d「FP16 通路 RTL 逐字不变」都依赖它）。
  *
  * 实测结论（Phase 5a，probe 重发射逐字节比对）：Chisel 7 命名插件会把【调用点名字】作为
  * 前缀下推进 object 内部——`io.sum := HiveMacLazyDownstream(...)` 让内部信号全部变成
  * io_sum_pMant31 / io_sum_pGtC / io_sum_baseExp / io_sum_pAligned / io_sum_cAligned /
  * io_sum_effectSub / io_sum_pIsLarger / io_sum_largeM / io_sum_smallM / io_sum_addRes /
  * _io_sum_expDiffRaw_T_2 / _io_sum_shiftAmt_T_8 / _io_sum_subRes_T，normalized-diff 20+ 行；
  * 同理 `val x = Fp32.mul(...)` 会产出 prod32_sOut / prod32_mProd / prod32_mNorm（既有实证）。
  * 解法 = 在 apply 内部用 `chisel3.experimental.noPrefix { ... }` 清空前缀栈，内部信号名即与
  * 「原本内联在 Module 类体（前缀栈为空）」逐字一致 ⇒ Hive_HiveMacFpMx.sv normalized-diff = 0。
  * 【Phase 5b-5d 融合模块复用须知】noPrefix 已内建在 apply 内部，调用方无论写成
  * `io.sum := HiveMacLazyDownstream(...)` 还是绑定 val，都不会再引入前缀漂移。
  *
  * @param prod32 上游产出的标准 fp32 乘积（32-bit，已含舍入）
  * @param c      累加器输入（取低 40 bit 的 lazy 格式 {sign(1),exp(8),mant(31)}）
  * @param cW     累加器位宽（>=40；40-bit lazy 结果放低位，高位补零）
  * @return cW-bit 累加器输出（低 40 bit 为 lazy 格式）
  */
private object HiveMacLazyDownstream {
  def apply(prod32: UInt, c: UInt, cW: Int): UInt = {
    require(cW >= 40, s"HiveMacLazyDownstream: 延迟规格化需要 cW($cW) >= 40")

    // 【零 diff 命门】必须用 noPrefix 清空 Chisel 命名前缀栈后再建节点。
    // 实测（Phase 5a）：不加 noPrefix 时，调用点 `io.sum := HiveMacLazyDownstream(...)`
    // 会让命名插件把 `io_sum_` 前缀下推进本方法内部，使 pMant31/pGtC/baseExp/pAligned/
    // cAligned/effectSub/pIsLarger/largeM/smallM/addRes 以及匿名节点 _expDiffRaw_T_2/
    // _shiftAmt_T_8/_subRes_T 全部改名（并连带改变 firtool 的换行折行），
    // Hive_HiveMacFpMx.sv 的 normalized-diff 由 0 涨到 20+ 行。
    // noPrefix 使内部信号名与「原本内联在 Module 类体（前缀栈为空）」时逐字一致 ⇒ RTL 零 diff。
    // 附加实测：因 noPrefix 已在本方法内部生效，调用方即使写成
    // `val x = HiveMacLazyDownstream(...); io.sum := x` 也仍是 normalized-diff = 0
    // （前缀已被吃掉）；本模块仍采用直接连接的规范写法。
    chisel3.experimental.noPrefix {
      // ==== 解包累加器 c (低 40 bit) ====
      val cRaw  = c(39, 0)
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
      if (cW == 40) result40
      else Cat(0.U((cW - 40).W), result40)
    }
  }
}

/** HiveMacFpMx（MX 奇 lane 专用窄化浮点 MAC，延迟规格化版本）—— C2
  *
  * 结构照搬 HiveMacFp，唯一区别：把满宽 24×24 尾数乘法阵列窄化为 sigW×sigW
  * （默认 sigW=4），窄积零扩展回原 48-bit mProd 布局后，下游（指数调整 eAdj、
  * 规格化 mNorm、guardBit、对齐、加减、上溢处理、lazy-40 输出）逐字复用 HiveMacFp。
  * 【Phase 5a】其中「对齐 / 加减 / 上溢处理 / lazy-40 输出」这段下游已抽为共享
  * object HiveMacLazyDownstream 并由本模块调用（内容逐字搬迁、零功能变化）；
  * eAdj/mNorm/guardBit 属上游乘积阶段，仍内联在本类体内。HiveMacFp 按禁改区要求
  * 保留自己的内联下游副本原样不动。
  *
  * 【前置条件 / 非饱和域 bit-exact 依据】仅用于 MX 通路。MX 操作数（E4M3 尾数 3 位 /
  * E5M2 尾数 2 位）经 Fp32.mxToFp32 后 fp32 尾数 = Cat(elemMant, 0)，低 (23-mantW) 位恒 0；
  * 加隐含 1 后 24-bit 有效位 ≤ 4（=1+mantW）。故满宽积 mA24*mB24 = (aSig<<20)*(bSig<<20)
  * = (aSig*bSig)<<40，低 40 位恒 0；窄化取 aSig=Cat(1, mA 高 sigW-1 位) 后
  * mProd=(aSig*bSig)<<(48-2*sigW) 与满宽逐位相等，guardBit（落在恒 0 低位）恒 0 ⇒
  * applyRounding 退化为恒等 ⇒ 非饱和域与满宽 HiveMacFp 完全 bit-exact。
  *
  * 【唯一偏差域】overflow 饱和操作数：mxToFp32 上溢时输出 fp32Max（尾数全 1），此时低位
  * 非 0，窄化截断低位 ⇒ 单边约 −6.25%（15/16）、双边约 −12.1%（(15/16)^2）。仅极端/误配
  * E8M0 scale（真值 ≥ 2^120 量级才触发上溢饱和）时发生，正常推理不出现。本次仅文档化该
  * 偏差，不加硬件 errCode、不加仿真断言（保持单文件最小改动）。
  *
  * FP16/BF16/INT 通路不受影响（走满宽 macShared / macInt，不用本模块）。
  * 要求 cW >= 40（同 HiveMacFp）。纯组合。
  *
  * 【io.rnd 保留端口】MX 域 guardBit 结构性恒 0 ⇒ 舍入退化为恒等，io.rnd 被忽略，
  * 保留仅为与 HiveMacFp 端口同形。警示：禁止把 HiveMacFpMx 当作 HiveMacFp 的窄化替身
  * 用于非 MX/宽尾数通路，否则会静默丢失 RTZ/RDN/RUP/RNE 舍入行为。
  * 注：每个不同 sigW 取值会派生一个独立 RTL 模块（firtool 按参数分别综合）。
  *
  * @param cW   累加器位宽（>=40，40-bit lazy 结果放低位，高位补零）
  * @param sigW 窄化乘法器有效位宽（默认 4：覆盖 E4M3 的 1+3 有效位，E5M2 的 1+2 亦被覆盖）
  */
class HiveMacFpMx(cW: Int, sigW: Int = 4) extends Module {
  require(cW >= 40, s"HiveMacFpMx: 延迟规格化需要 cW($cW) >= 40")
  // sigW 下界：必须 ≥ 1(隐含位) + 被支持 MX 格式的 mantW，否则该格式尾数低位被静默截断算错。
  //   · fp8 dual-lane（E4M3 mantW=3 / E5M2 mantW=2）⇒ sigW≥4，故 macOdd 仍 hardcode 默认 sigW=4；
  //   · fp4 quad-lane（E2M1 mantW=1）⇒ 仅需 sigW≥2（Ivy④ 面积微优化：解码后尾数 m23=Cat(m,0^22)
  //     仅 bit22 有效、mA(21..0) 恒 0，sigW=4 的乘法阵列 3/4 恒零浪费）。
  //   放宽 bound 允许 sigW==2【不改 fp8 RTL】：fp8 路径恒用默认 sigW=4，绝不下探到 2（见调用点）。
  //   上限 24 = 1 位隐含 + 23 位尾数（退化为满宽）。
  require((sigW == 2 || sigW >= 4) && sigW <= 24, s"HiveMacFpMx: sigW($sigW) 需为 2（fp4/E2M1 专用）或落在 [4,24]（fp8 需 ≥1+mantW=3，否则尾数低位被静默截断）")

  val io = IO(new Bundle {
    val a   = Input(UInt(32.W))    // fp32 multiplicand（MX 转换后，尾数低位恒 0）
    val b   = Input(UInt(32.W))    // fp32 multiplier
    val c   = Input(UInt(cW.W))    // 累加器输入（低 40 bit 是延迟规格化格式）
    // io.rnd：契约性保留端口——MX 域 guardBit 结构性恒 0，舍入退化为恒等，此端口被忽略；
    // 保留仅为与 HiveMacFp 端口同形（便于同位置例化）。禁止将本模块当作 HiveMacFp 的
    // 窄化替身用于非 MX/宽尾数通路，否则会静默丢失 RTZ/RDN/RUP/RNE 舍入行为。
    val rnd = Input(RoundingMode())
    val sum = Output(UInt(cW.W))   // 累加器输出（低 40 bit 是延迟规格化格式）
  })

  // ==== 窄化乘法：替代 Fp32.mul 的 24×24 阵列，产出与满宽逐位相等的 prod32 ====
  val sA = io.a(31); val eA = io.a(30, 23); val mA = io.a(22, 0)
  val sB = io.b(31); val eB = io.b(30, 23); val mB = io.b(22, 0)
  val sOutM = sA ^ sB
  // aSig/bSig = 隐含 1 + 高 (sigW-1) 位尾数（MX 尾数低 23-(sigW-1) 位恒 0，可安全丢弃）
  val aSig = Cat(1.U(1.W), mA(22, 24 - sigW))   // sigW 位
  val bSig = Cat(1.U(1.W), mB(22, 24 - sigW))   // sigW 位
  // sigW×sigW 窄积零扩展回 48-bit mProd 布局（低 48-2*sigW 位恒 0，等价满宽 mA24*mB24）
  val mProd = (aSig * bSig) << (48 - 2 * sigW)  // 48-bit
  // 指数相加 - bias；尾数积 >=2（mProd(47)=1）时 +1（与 Fp32.mul 逐字同源）
  val eSum = eA +& eB
  val eAdj = eSum + Mux(mProd(47), 1.U(9.W), 0.U(9.W))
  val eOut = Mux(eAdj >= 127.U, (eAdj - 127.U)(7, 0), 0.U(8.W))
  // 规格化 + 舍入位：guardBit 落在恒 0 低位 ⇒ 恒 0 ⇒ applyRounding 退化为恒等（照搬其逻辑）
  val mNorm    = Mux(mProd(47), mProd(46, 24), mProd(45, 23))
  val guardBit = Mux(mProd(47), mProd(23), mProd(22))
  val gM       = guardBit.asBool
  val roundUpM = Mux(io.rnd === RoundingMode.RTZ, false.B,
    Mux(io.rnd === RoundingMode.RDN, sOutM && gM,
      Mux(io.rnd === RoundingMode.RUP, !sOutM && gM, gM)))
  val mRounded = Mux(roundUpM, mNorm +& 1.U, mNorm)(22, 0)
  // 任一操作数为零 → 结果为零
  val aZeroM = (eA === 0.U) && (mA === 0.U)
  val bZeroM = (eB === 0.U) && (mB === 0.U)
  val prod32 = Mux(aZeroM || bZeroM, 0.U(32.W), Cat(sOutM, eOut, mRounded))

  // ==== 下游（解包 c / 解包乘积 / 对齐 / 加减 / 上溢 / lazy-40 / 输出）====
  // Phase 5a：原本此处内联了一份与 HiveMacFp 逐字同构的下游副本，现改为调用共享
  // object HiveMacLazyDownstream（内容逐字搬迁、零功能变化）。
  // 【零 diff 已实测保住】命名插件会把调用点名字（此处 = io_sum）作为前缀下推进 object
  // 内部，实测使 pMant31/pGtC/baseExp/pAligned/cAligned/effectSub/pIsLarger/largeM/smallM/
  // addRes 等全面改名（io_sum_pMant31 …）、normalized-diff 20+ 行；该 object 的 apply 内部
  // 已用 chisel3.experimental.noPrefix 清空前缀栈 ⇒ Hive_HiveMacFpMx.sv normalized-diff = 0。
  // 舍入已在上游窄化乘法阶段完成（roundUpM，MX 域 guardBit 恒 0 ⇒ 退化为恒等），
  // 下游不消费 io.rnd。
  io.sum := HiveMacLazyDownstream(prod32, io.c, cW)
}



/** HiveWorkUnit（工作单元顶层）
  * 负责 fmt/rnd 控制与数据通路拼接：例化操作数转换（a/b）与整数/浮点 MAC，
  * 按 fmt 选择整数或浮点通路。乘积位宽适配已内嵌于 HiveMacInt/HiveMacFp。
  * C1 实例合并：c1Merge（hasFp && hasMx）时 FP 通路与 MX 偶 lane 共用单个
  * HiveMacFp，每 PE 的 MAC 实例数由 3（macFp + macEven + macOdd）降为 2。
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
  // hasMx：历史谓词，保留原义与原表达式【逐字不动】——c1Merge（C1 共享，Phase 2 明令逐位不变）
  // 与 io.sum（禁改）仍引用它。对任意配置其取值恒等于下方 hasMx8。
  private val hasMx  = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2)
  // Phase 1：MX 硬件生成谓词收敛。hasMx8（MXFP8 E4M3/E5M2）与 hasMx 定义逐字相同（= 现 hasMx
  // 语义），单列以配合 hasMx4 组成清晰判据；凡「生成 MX 硬件」的 require/门控由 hasMx 改为
  // (hasMx8 || hasMx4)（新增并列，false 分支逐字保留）。c1Merge 与 io.sum 例外（分别明令
  // 「逐位不变」与「禁改」），仍用 hasMx——二者取值恒等于 hasMx8，故行为一致。
  // ★【前提显式化（Fix-1）】上述「行为一致」并非天然成立，而是由 HiveCoreConfig 的
  //   require(!hasMx4 || hasMx8) 强制保证：v1 禁止 fp4-only 配置，故 hasMx4=true ⟹ hasMx8=true
  //   ⟹ hasMx==hasMx8==true，c1Merge/io.sum 的 hasMx 判据与 (hasMx8||hasMx4) 同值。若未来解禁
  //   fp4-only（v2），必须同步迁移 c1Merge/io.sum 主开关，否则「行为一致」前提失效。
  // 【恒等性铁律】不含 MXE2M1 的配置 hasMx4=false ⟹ hasMx8||hasMx4 == hasMx8 == hasMx 取值不变
  // ⟹ 门控解析出的硬件结构逐位不变（verilog/verilog_mx 零 diff 的根本保证）。
  private val hasMx8 = hasMx  // 语义别名：MXFP8 生成判据，与历史 hasMx 同值，单一真源
  // Phase 0 引入、Phase 1 收敛：mxfp4/E2M1 的【编译期】门控（纯 Scala Boolean，不入硬件）。
  // ① 透传给 HiveCvtOpMx 决定是否 elaboration 期展开第三路 E2M1→fp32；
  // ② Phase 1 起并入 MX 硬件生成判据 (hasMx8 || hasMx4)；③ Phase 2 起门控 fp4 quad-lane 分支。
  // 当前无任何配置含 MXE2M1 ⟹ 全部实例 hasMx4=false ⟹ CvtOpMx/MX 通路硬件与改动前逐位一致。
  private val hasMx4 = supportedFmts.contains(DataFormat.MXE2M1)

  // C1 实例合并门控：仅当同时含 FP 与 MX 通路时，FP 专用 MAC 与 MX 偶-lane MAC
  // 合并为单个共享 HiveMacFp（macShared），其 a/b 前置 2:1 mux（选择子 = isMx(io.aFmt)）。
  private val c1Merge = hasFp && hasMx

  require(hasInt || hasFp || hasMx8 || hasMx4, "HiveWorkUnit: supportedFmts 不能为空（至少含一种支持格式）")
  if (hasFp)  require(cEffW >= 40,         s"HiveWorkUnit: 含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aW + bW,    s"HiveWorkUnit: 含整数格式时 cEffW($cEffW) 必须 >= aW($aW) + bW($bW)")
  // MX 复用 fp32 域延迟规格化累加器（不扩宽），故同样要求 cEffW>=40；
  // v2 SIMD：16-bit lane 打包 2 个 8-bit 元素（低字节=偶 k、高字节=奇 k），
  // 故要求 aW/bW>=16。Phase 1：判据 hasMx → (hasMx8 || hasMx4)；fp4 quad-lane 同样
  // 复用 40-bit 延迟规格化累加器、同样以 16-bit lane 打包（4 个 4-bit 元素），故两约束
  // 对 fp4 亦成立。不含 MXE2M1 的配置 hasMx4=false ⟹ 取值与 hasMx 逐位相同（零 diff）。
  if (hasMx8 || hasMx4)  require(cEffW >= 40,         s"HiveWorkUnit: 含 MX 格式时 cEffW($cEffW) 必须 >= 40（复用 fp32 延迟规格化累加器）")
  if (hasMx8 || hasMx4)  require(aW >= 16 && bW >= 16, s"HiveWorkUnit: 含 MX 格式时 aW($aW)/bW($bW) 必须 >= 16（SIMD：16-bit lane 打包 fp8 双元素/fp4 四元素）")

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
    // 消费有效门控（仅 hasMx 块内有意义）：透传给 4×HiveCvtOpMx 的 inf/nan 断言；
    // 非 MX 配置下恒不被选用，行为零影响。由宿主（HiveWorker）单行连入。
    val consumeValid = Input(Bool())
    val sum  = Output(UInt(cEffW.W))
  })

  // 消费有效：consumeValid 由唯一父级（HiveWorker）恒驱动；WireDefault(false.B)
  // 仅为构造期占位/防未驱动告警，运行期恒被下方端口连接覆写（非「未驱动兜底」路径）
  private val consumeValidEff = WireDefault(false.B)
  consumeValidEff := io.consumeValid

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

  // --- C1 共享 MAC 实例（仅 c1Merge=hasFp&&hasMx 时创建） ---
  // 位置：类体内 INT 块之后、FP 块之前创建，使 Module 例化顺序贴近原 macFp 位置，减少
  // firtool 实例名前缀漂移。其端口 a/b/c/rnd 的实际驱动点全部在下方 hasMx 块内
  // （a/b 经 2:1 mux 选 MX 转换器输出或 fpAOut/fpBOut，c/rnd 直连 io.cReg/io.rnd）；
  // FP 块仅消费 macShared.io.sum（不驱动其任何端口）。
  // 不变量：c1Merge ⇒ hasMx 恒成立，故 MX 块内的驱动点必然存在，不会悬空。
  private val macShared: Option[HiveMacFp] =
    if (c1Merge) Some(Module(new HiveMacFp(cEffW))) else None

  // --- 浮点通路（hasFp 时生成：操作数转换 ×2 + HiveMacFp） ---
  // HiveCvtOpFp 输入固定 16 位，窄位宽在顶层预补零
  private val aFpIn: UInt = if (aW < 16) Cat(0.U((16 - aW).W), io.a) else io.a(15, 0)
  private val bFpIn: UInt = if (bW < 16) Cat(0.U((16 - bW).W), io.b) else io.b(15, 0)
  // C1 共享：三元组 = (sumFp, fpAOut, fpBOut)。c1Merge=true 时不创建 macFp，
  // aCvt/bCvt 的 fp32 输出经 fpAOut/fpBOut 暴露给 MX 块的共享实例 mux，sumFp
  // 由共享实例在非 MX 拍驱动（其 a/b/c/rnd 全部在 MX 块连接）；c1Merge=false
  // 分支逐字保留原 macFp 代码（实例创建顺序 aCvt→bCvt→macFp、val 命名均不变）。
  private val fpCvt: (UInt, Option[UInt], Option[UInt]) = if (hasFp) {
    val aCvt = Module(new HiveCvtOpFp)
    aCvt.io.in  := aFpIn
    aCvt.io.fmt := io.aFmt
    val bCvt = Module(new HiveCvtOpFp)
    bCvt.io.in  := bFpIn
    bCvt.io.fmt := io.bFmt
    if (c1Merge) {
      (macShared.get.io.sum, Some(aCvt.io.out), Some(bCvt.io.out))
    } else {
      val macFp = Module(new HiveMacFp(cEffW))
      macFp.io.a   := aCvt.io.out
      macFp.io.b   := bCvt.io.out
      macFp.io.c   := io.cReg
      macFp.io.rnd := io.rnd
      (macFp.io.sum, None, None)
    }
  } else (0.U(cEffW.W), None, None)
  val sumFp: UInt = fpCvt._1
  private val fpAOut: Option[UInt] = fpCvt._2
  private val fpBOut: Option[UInt] = fpCvt._3

  // --- MX 通路（hasMx 时生成：SIMD 双元素/拍，4×HiveCvtOpMx + 2×HiveMacFp 级联） ---
  // v2 SIMD：16-bit lane 携带 2 个 8-bit 元素（低字节=偶 k=2j、高字节=奇 k=2j+1），
  // 两元素同拍消费：a 偶/奇 + b 偶/奇共 4 个 HiveCvtOpMx（各含 E8M0 scale 指数加法，
  // 同一 32-K 块内 scaleA/scaleB 共享，两半接同一 scale 合法），两个乘积由 2 个
  // HiveMacFp 级联吸收：macEven 先加偶 k（低字节）→ sumEven，macOdd 再加奇 k（高字节）
  // → sumMx。级联顺序必须低字节先加：等价于 v1 的 k 升序左结合累加，是逐 bit 等价前提。
  // C1 共享：c1Merge（hasFp&&hasMx）时 macEven 复用 FP 通路的共享 HiveMacFp（macShared），
  // 非 MX 拍该实例充当 FP MAC、MX 拍充当偶-lane MAC；a/b 前置 2:1 mux，选择子 isMxSel 与
  // 输出选择同判据。bit-exact 依据：HiveMacFp 纯组合、输出为 (a,b,c,rnd) 纯函数，mux 双臂
  // 等宽 32-bit，INT/FP/MX 三通路运行期互斥；c/rnd 两用途同为 io.cReg/io.rnd 无需 mux。
  // ── fp8 dual-lane MX 通路（现有逻辑；抽出为 private def 以便 hasMx4 时与 quad-lane 零复制复用）──
  // 【命名前缀不变 · 零 diff 关键】def 体逐字节保留原 if(hasMx) 块体（aCvtLo/aCvtHi/bCvtLo/bCvtHi
  // + isMxSel + macEven/macOdd + c1Merge 共享逻辑，含全部 FIRRTL 信号名）。调用点位于 val sumMx 的
  // RHS 作用域内，Chisel 命名插件把 sumMx 作为前缀下推进 def 体，故内部实例仍命名 sumMx_aCvtLo /
  // sumMx_macOdd……与抽出前逐字一致（已由 verilog/verilog_mx normalized-diff=0 复核）。def 在下方
  // sumMx 的两个互斥 Scala 分支中仅其一被调用一次 ⟹ 每次 elaboration 只生成一份 dual-lane 硬件。
  private def dualLaneMx(): UInt = {
    // Phase 0：hasMx4 = supportedFmts.contains(MXE2M1) 透传给 4 个 HiveCvtOpMx。
    // 当前无任何配置含 MXE2M1 ⟹ 恒 false ⟹ 4 个实例的硬件与改动前逐位一致；
    // Phase 4 的 cfgMxFp4（supportedFmts 含 MXE2M1）才会激活第三路 E2M1 解码。
    val aCvtLo = Module(new HiveCvtOpMx(hasMx4 = hasMx4))
    aCvtLo.io.in    := io.a(7, 0)
    aCvtLo.io.fmt   := io.aFmt
    aCvtLo.io.scale := io.scaleA
    aCvtLo.io.consumeValid := consumeValidEff
    val aCvtHi = Module(new HiveCvtOpMx(hasMx4 = hasMx4))
    aCvtHi.io.in    := io.a(15, 8)
    aCvtHi.io.fmt   := io.aFmt
    aCvtHi.io.scale := io.scaleA
    aCvtHi.io.consumeValid := consumeValidEff
    val bCvtLo = Module(new HiveCvtOpMx(hasMx4 = hasMx4))
    bCvtLo.io.in    := io.b(7, 0)
    bCvtLo.io.fmt   := io.bFmt
    bCvtLo.io.scale := io.scaleB
    bCvtLo.io.consumeValid := consumeValidEff
    val bCvtHi = Module(new HiveCvtOpMx(hasMx4 = hasMx4))
    bCvtHi.io.in    := io.b(15, 8)
    bCvtHi.io.fmt   := io.bFmt
    bCvtHi.io.scale := io.scaleB
    bCvtHi.io.consumeValid := consumeValidEff
    // isMxSel：与输出选择逻辑同判据表达式，共享实例 a/b mux 的选择子
    val isMxSel = DataFormat.isMx(io.aFmt)
    if (c1Merge) {
      // macEven：偶 k（低字节）先加 = 共享实例 macShared（复用 FP 通路 MAC）
      // a/b 前置 2:1 mux：MX 拍取 aCvtLo/bCvtLo，非 MX 拍取 FP 转换输出 fpAOut/fpBOut
      val macEven = macShared.get
      macEven.io.a   := Mux(isMxSel, aCvtLo.io.out, fpAOut.get)
      macEven.io.b   := Mux(isMxSel, bCvtLo.io.out, fpBOut.get)
      macEven.io.c   := io.cReg
      macEven.io.rnd := io.rnd
      // macOdd：奇 k（高字节）后加，累加器输入 = sumEven → sumMx（单一累加器语义）
      // C2：macOdd 在 if(hasMx) 块内无条件使用窄化 HiveMacFpMx（sigW=4）——其 a/b 恒来自
      // HiveCvtOpMx 的无条件 MX 解码（输出尾数低 20 位恒 0，满足窄化前提）；a/b/c/rnd
      // 连接语义与满宽 HiveMacFp 完全一致，非饱和域 bit-exact（详见类头）。
      val macOdd = Module(new HiveMacFpMx(cEffW))
      macOdd.io.a   := aCvtHi.io.out
      macOdd.io.b   := bCvtHi.io.out
      macOdd.io.c   := macEven.io.sum
      macOdd.io.rnd := io.rnd
      macOdd.io.sum
    } else {
      // macEven：偶 k（低字节）先加，累加器输入 = cReg → sumEven
      val macEven = Module(new HiveMacFp(cEffW))
      macEven.io.a   := aCvtLo.io.out
      macEven.io.b   := bCvtLo.io.out
      macEven.io.c   := io.cReg
      macEven.io.rnd := io.rnd
      // macOdd：奇 k（高字节）后加，累加器输入 = sumEven → sumMx（单一累加器语义）
      // C2：macOdd 在 if(hasMx) 块内无条件使用窄化 HiveMacFpMx（sigW=4）——其 a/b 恒来自
      // HiveCvtOpMx 的无条件 MX 解码（输出尾数低 20 位恒 0，满足窄化前提）；a/b/c/rnd
      // 连接语义与满宽 HiveMacFp 完全一致，非饱和域 bit-exact（详见类头）。
      val macOdd = Module(new HiveMacFpMx(cEffW))
      macOdd.io.a   := aCvtHi.io.out
      macOdd.io.b   := bCvtHi.io.out
      macOdd.io.c   := macEven.io.sum
      macOdd.io.rnd := io.rnd
      macOdd.io.sum
    }
  }  // ← 关闭 private def dualLaneMx（def 体逐字节保留原 fp8 dual-lane 代码，全部 FIRRTL 信号名不变）

  // sumMx：hasMx4 时 = Mux(isMx4, quadResult, dualLaneMx())；hasMx4=false 时 = dualLaneMx()（原样、无 mux）。
  // 【命名前缀不变 · 零 diff 关键】dualLaneMx() 的两个调用点均位于本 val sumMx 的 RHS 作用域内、且不在任何
  // 额外命名 val 中（bare if/else 分支不加前缀）⟹ Chisel 命名插件把 sumMx 作为唯一前缀下推进 def 体 ⟹
  // fp8 实例仍命名 sumMx_aCvtLo / sumMx_macOdd……与抽出前逐字一致。两个 Scala 分支互斥、每次 elaboration
  // 只调用一次 ⟹ 只生成一份 dual-lane 硬件。
  val sumMx: UInt = if (hasMx8 || hasMx4) {
    // ═══ fp4 quad-lane（Phase 2 新增，与 fp8 dual-lane 物理隔离的独立编译期分支）═══
    // 物理隔离原则：走独立 if(hasMx4) 分支，绝不重写/参数化循环替换上方 fp8 dual-lane。
    // hasMx4=false（当前全部出厂配置 verilog/verilog_mx）时本分支根本不 elaborate ⟹ 零 diff。
    // 结构：elemBits=4，16-bit lane 打包 4 个 nibble（io.a(3,0)/(7,4)/(11,8)/(15,12)，io.b 同理），
    // 共 8 个 HiveCvtOpMx(hasMx4=true)（a/b 各 4，含 E8M0 scale 指数加法；同一 64-K 块内 scale
    // 共享，a 侧 4 nibble 同接 scaleA、b 侧同接 scaleB 合法）。
    // MAC 级联 4 级、k 升序、低 sublane 先加（lane0=io.a(3,0) 先、lane3=io.a(15,12) 后），
    // 左结合 ((((cReg + a0*b0) + a1*b1) + a2*b2) + a3*b3)，每级用 HiveMacFpMx(cEffW, sigW=2)
    // （【Ivy④ 面积微优化】E2M1 mantW=1，解码后尾数 m23=Cat(m,0^22) 仅 bit22 有效，
    // sigW=2 与 sigW=4 对 1 位尾数 m∈{0,1} 逐位相等：sigW=4 给 (8+4m)(8+4m')<<40、
    // sigW=2 给 (2+m)(2+m')<<44，因 (8+4m)(8+4m')=16·(2+m)(2+m') ⟹ 两者 = (2+m)(2+m')·2^44
    // 完全相同；narrow 变体只在 hasMx4 分支 elaborate，绝不进 fp8-only 的 verilog/verilog_mx。
    // 已由 HiveWorkUnitMxSpec fp4 语义 + HiveMxDecodeSpec 穷举实测 0 失配）。C1 共享【不】扩展到
    // quad-lane：4 个 MAC 全独立、不与 FP 通路共享（共享/融合属 Phase 5）；c1Merge 定义与行为逐位
    // 不变（仍只服务 dual-lane）。
    if (hasMx4) {
      val quadResult: UInt = {
        // --- a 侧 4 nibble 解码（同一 scaleA）---
        val aQ0 = Module(new HiveCvtOpMx(hasMx4 = true))
        aQ0.io.in    := io.a(3, 0)
        aQ0.io.fmt   := io.aFmt
        aQ0.io.scale := io.scaleA
        aQ0.io.consumeValid := consumeValidEff
        val aQ1 = Module(new HiveCvtOpMx(hasMx4 = true))
        aQ1.io.in    := io.a(7, 4)
        aQ1.io.fmt   := io.aFmt
        aQ1.io.scale := io.scaleA
        aQ1.io.consumeValid := consumeValidEff
        val aQ2 = Module(new HiveCvtOpMx(hasMx4 = true))
        aQ2.io.in    := io.a(11, 8)
        aQ2.io.fmt   := io.aFmt
        aQ2.io.scale := io.scaleA
        aQ2.io.consumeValid := consumeValidEff
        val aQ3 = Module(new HiveCvtOpMx(hasMx4 = true))
        aQ3.io.in    := io.a(15, 12)
        aQ3.io.fmt   := io.aFmt
        aQ3.io.scale := io.scaleA
        aQ3.io.consumeValid := consumeValidEff
        // --- b 侧 4 nibble 解码（同一 scaleB）---
        val bQ0 = Module(new HiveCvtOpMx(hasMx4 = true))
        bQ0.io.in    := io.b(3, 0)
        bQ0.io.fmt   := io.bFmt
        bQ0.io.scale := io.scaleB
        bQ0.io.consumeValid := consumeValidEff
        val bQ1 = Module(new HiveCvtOpMx(hasMx4 = true))
        bQ1.io.in    := io.b(7, 4)
        bQ1.io.fmt   := io.bFmt
        bQ1.io.scale := io.scaleB
        bQ1.io.consumeValid := consumeValidEff
        val bQ2 = Module(new HiveCvtOpMx(hasMx4 = true))
        bQ2.io.in    := io.b(11, 8)
        bQ2.io.fmt   := io.bFmt
        bQ2.io.scale := io.scaleB
        bQ2.io.consumeValid := consumeValidEff
        val bQ3 = Module(new HiveCvtOpMx(hasMx4 = true))
        bQ3.io.in    := io.b(15, 12)
        bQ3.io.fmt   := io.bFmt
        bQ3.io.scale := io.scaleB
        bQ3.io.consumeValid := consumeValidEff
        // --- 4 级 HiveMacFpMx 左结合级联（k 升序、低 sublane 先加）---
        val qmac0 = Module(new HiveMacFpMx(cEffW, sigW = 2))
        qmac0.io.a   := aQ0.io.out
        qmac0.io.b   := bQ0.io.out
        qmac0.io.c   := io.cReg
        qmac0.io.rnd := io.rnd
        val qmac1 = Module(new HiveMacFpMx(cEffW, sigW = 2))
        qmac1.io.a   := aQ1.io.out
        qmac1.io.b   := bQ1.io.out
        qmac1.io.c   := qmac0.io.sum
        qmac1.io.rnd := io.rnd
        val qmac2 = Module(new HiveMacFpMx(cEffW, sigW = 2))
        qmac2.io.a   := aQ2.io.out
        qmac2.io.b   := bQ2.io.out
        qmac2.io.c   := qmac1.io.sum
        qmac2.io.rnd := io.rnd
        val qmac3 = Module(new HiveMacFpMx(cEffW, sigW = 2))
        qmac3.io.a   := aQ3.io.out
        qmac3.io.b   := bQ3.io.out
        qmac3.io.c   := qmac2.io.sum
        qmac3.io.rnd := io.rnd
        qmac3.io.sum
      }
      // 运行期选择：MXE2M1 走 quad-lane，MXE4M3/MXE5M2 仍走 fp8 dual-lane（逐字路径）。
      Mux(DataFormat.isMx4(io.aFmt), quadResult, dualLaneMx())
    } else {
      // hasMx4=false：无 quad-lane、无运行期 mux，直接返回 fp8 dual-lane（原样、逐字路径）
      dualLaneMx()
    }
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
