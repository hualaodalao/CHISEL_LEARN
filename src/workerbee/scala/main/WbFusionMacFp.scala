import chisel3._
import chisel3.util._


// this fusion mac supports fp16/bf16/mxfp8/mxfp4, input is 16 bits
//
// lane0 三选路（互斥且穷尽，hasFp = hasFp16 || hasBf16）；lane1/2/3 与累加器接线三路相同：
//   hasFp16             → FpMulFp16Bf16（fp32 域 24×24 满宽阵列 + 8 个 fp32 cvt + 4:1 mux）
//   hasBf16 && !hasFp16 → FpMulBf16Mx（BF16/MX 共享 8×8 阵列，3250 晶体管 vs 宽阵列 24018）
//   !hasFp              → FpMulMx8（MX 专属 4×4 窄阵列）
//
// supportedFmts 是绝对集：写子集即删硬件。类默认值为 MX-only，只在独立单测例化时生效；整机
//   例化由 WorkerBee 透传 cfg.supportedFmts（含 BF16 ⟹ lane0 走 FpMulBf16Mx）。需要全五格式
//   宽阵列硬件者必须显式传完整集合。hasFp 只在 elaboration 期决定生成哪些实例，绝不门控运行
//   期：is4x/is2x、r1/r2/r3 的 Mux、五槽累加器接线与格式集无关。
//
// useNarrowAdd 编译期选择累加器：true（默认）例化 FpAddNarrow（L2 窄窗口五槽 CSA 树，精度
//   契约见 FpAddNarrow.scala 头注）；false 须显式 pin，例化 bit-exact 基线 FpAdd。两者 I/O
//   port-compatible（8 端口方向/名称/位宽全同）⟹ 共享 wiring helper。FpAddNarrow 白名单为
//   MX + BF16，FP16 与 INT/FP32 集越界。narrowW 默认 FpAddNarrow.DefaultW (19)，仅在
//   useNarrowAdd=true 时生效，合法区间 [8,28]（下界 8 = mxfp8 乘积 8 位有效尾数，上界 28 =
//   mant31 高 W 位对齐留 3 位余量），由 FpAddNarrow 的两条 require 强制。
class WbFusionMacFp(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 40,
  supportedFmts: Set[DataFormat.Type] =
    Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
  useNarrowAdd: Boolean = true,
  narrowW: Int = FpAddNarrow.DefaultW
) extends Module {

  // FP16 与 BF16 彼此独立：hasFp16 走 fp32 域宽阵列（满 24-bit 有效尾数），hasBf16 &&
  //   !hasFp16 走 FpMulBf16Mx（16-bit 有效尾数、低 8 位结构性 0），hasFp 为二者之或。
  private val hasFp16 = supportedFmts.contains(DataFormat.FP16)
  private val hasBf16 = supportedFmts.contains(DataFormat.BF16)
  private val hasFp  = hasFp16 || hasBf16
  private val hasMx8 = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2)
  private val hasMx4 = supportedFmts.contains(DataFormat.MXE2M1)

  // 绝对集护栏：MX 是本模块必选硬件路径（lane1/2/3 无条件生成），至少需一种 MX 格式；
  //   MXFP4 与 MXFP8 必须同时启用（lane1/2/3 供数依赖），即 MX 族全在或全不在。
  require(hasMx8 || hasMx4, s"WbFusionMacFp: MX 为必选硬件路径，至少需一种 MX 格式，当前=$supportedFmts")
  require(!hasMx4 || hasMx8, s"WbFusionMacFp: MXFP4 v1 必须与 MXFP8 同时启用（lane1/2/3 供数依赖），当前=$supportedFmts")
  require(!hasMx8 || hasMx4,
    s"WbFusionMacFp: MXFP8(E4M3/E5M2) requires MXFP4(E2M1) -- MX family is all-in-or-all-out (user design rule), current=$supportedFmts")

  // BF16 在 FpAddNarrow 白名单内：其乘积来自 FpMulBf16Mx、mant23 低 8 位结构性恒 0，mag24 有效
  //   位只占 [23:8] 共 16 位，narrowW >= 16 时 magW = mag24 >> (24−W) 把全部有效位留在窗内。
  //   FP16 在名单外：它走 fp32 转换路，mant23 全 23 位有效，会被窄窗口截断。
  require(!useNarrowAdd || supportedFmts.subsetOf(
            Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1, DataFormat.BF16)),
    s"WbFusionMacFp: FpAddNarrow 仅支持 MX(MXE4M3/MXE5M2/MXE2M1) + BF16，当前 supportedFmts=$supportedFmts 含越界格式")

  // BF16 窗口无损下界 narrowW >= 16（= FpMulBf16Mx 乘积有效位数 1+8+8-1）：低于 16 会截掉真实
  //   有效位，编译期拦死而非静默降精度。
  require(!(useNarrowAdd && hasBf16) || narrowW >= 16,
    s"WbFusionMacFp: BF16 requires narrowW($narrowW) >= 16 to keep the 16-bit product significand lossless")

  // narrowW 仅在 useNarrowAdd=true 时被读取，FpAdd 基线路径下无窗口概念：钉死非 narrow 路径
  //   不得携带非默认 narrowW。
  require(useNarrowAdd || narrowW == FpAddNarrow.DefaultW,
    s"WbFusionMacFp: narrowW($narrowW) 仅在 useNarrowAdd=true 时生效；useNarrowAdd=false 时须保持默认 ${FpAddNarrow.DefaultW}")

  val cEffW: Int = math.max(cW, bW)
  
    val io = IO(new Bundle {
    val a    = Input(UInt(aW.W))
    val b    = Input(UInt(bW.W))
    val cReg = Input(UInt(cEffW.W))
    val aFmt = Input(DataFormat())
    val bFmt = Input(DataFormat())
    // scaleA 随激活流，scaleB 驻留
    val scaleA = Input(UInt(8.W))
    val scaleB = Input(UInt(8.W))
    val rnd  = Input(RoundingMode())
    // 消费有效门控：透传给 MX8 转换模块（FpCvtMx8ToMid/FpCvtMx8ToFp32）的 inf/nan 断言，
    // 非 MX 配置下不被选用。由宿主 WbWorker 连入。
    val consumeValid = Input(Bool())
    val sum  = Output(UInt(cEffW.W))
  })
  val aIsFp16 = RegNext(io.aFmt === DataFormat.FP16)
  val aIsBf16 = RegNext(io.aFmt === DataFormat.BF16)
  val aIsMx5M2 = RegNext(io.aFmt === DataFormat.MXE5M2)
  val aIsMx4M3 = RegNext(io.aFmt === DataFormat.MXE4M3)
  val aIsMx2M1 = RegNext(io.aFmt === DataFormat.MXE2M1)
  val bIsFp16 = RegNext(io.bFmt === DataFormat.FP16)
  val bIsBf16 = RegNext(io.bFmt === DataFormat.BF16)
  val bIsMx5M2 = RegNext(io.bFmt === DataFormat.MXE5M2)
  val bIsMx4M3 = RegNext(io.bFmt === DataFormat.MXE4M3)
  val bIsMx2M1 = RegNext(io.bFmt === DataFormat.MXE2M1)
  val isMx4 = RegNext(io.aFmt === DataFormat.MXE2M1 & io.bFmt === DataFormat.MXE2M1)
  val isMx8 = RegNext(io.aFmt === DataFormat.MXE5M2 | io.aFmt === DataFormat.MXE4M3 | io.bFmt === DataFormat.MXE5M2 | io.bFmt === DataFormat.MXE4M3)

  // BF16 运行期判据（其余配置为 false.B 字面量 ⟹ 不建节点）
  private val isBf16Pair: Bool = if (hasBf16 && !hasFp16) aIsBf16 && bIsBf16 else false.B

  // MX8 解码器的 inf/nan 断言输入：BF16 运行时 io.a/io.b 的字节按 E4M3 解读可能命中 inf/nan
  //   码位（如 BF16 exp=254 使高字节 e4=15,m3=7）而那是合法有限 BF16，故必须按运行格式门控。
  private val cvtConsumeValid: Bool =
    if (hasBf16 && !hasFp16) io.consumeValid && !isBf16Pair else io.consumeValid

  // lane0 乘积槽引用：module 级 var 桥接下方三个互斥 if 块的作用域，纯 Scala 别名、不建节点
  var r0: UInt = null

  // hasFp16=true：lane0 走宽阵列（fp16/bf16/mx8/mx4 各经 fp32 域 cvt → FpMulFp16Bf16 的
  //   4:1 mux）；其余两配置下整块不生成。
  if (hasFp16) {
    val fp16Mul = Module(new FpMulFp16Bf16())
    val cvtFp16ToFp32A = Module(new FpCvtFp16ToFp32())
    val cvtFp16ToFp32B = Module(new FpCvtFp16ToFp32())
    val cvtBf16ToFp32A = Module(new FpCvtBf16ToFp32())
    val cvtBf16ToFp32B = Module(new FpCvtBf16ToFp32())
    val cvtFp8ToFp32A = Module(new FpCvtMx8ToFp32())
    val cvtFp8ToFp32B = Module(new FpCvtMx8ToFp32())
    val cvtFp4ToFp32A = Module(new FpCvtMx4ToFp32())
    val cvtFp4ToFp32B = Module(new FpCvtMx4ToFp32())
    cvtFp16ToFp32A.io.in := io.a
    cvtFp16ToFp32B.io.in := io.b
    cvtBf16ToFp32A.io.in := io.a
    cvtBf16ToFp32B.io.in := io.b
    cvtFp8ToFp32A.io.in := io.a(7,0)
    cvtFp8ToFp32A.io.scale := io.scaleA
    cvtFp8ToFp32A.io.isE5M2 := aIsMx5M2
    cvtFp8ToFp32A.io.consumeValid := cvtConsumeValid
    cvtFp8ToFp32B.io.in := io.b(7,0)
    cvtFp8ToFp32B.io.scale := io.scaleB
    cvtFp8ToFp32B.io.isE5M2 := bIsMx5M2
    cvtFp8ToFp32B.io.consumeValid := cvtConsumeValid
    cvtFp4ToFp32A.io.in := io.a(3,0)
    cvtFp4ToFp32A.io.scale := io.scaleA
    cvtFp4ToFp32B.io.in := io.b(3,0)
    cvtFp4ToFp32B.io.scale := io.scaleB
    fp16Mul.io.rnd := io.rnd
    when(aIsFp16){
      fp16Mul.io.a := cvtFp16ToFp32A.io.out
    }.elsewhen(aIsBf16){
      fp16Mul.io.a := cvtBf16ToFp32A.io.out
    }.elsewhen(aIsMx5M2 | aIsMx4M3){
      fp16Mul.io.a := cvtFp8ToFp32A.io.out
    }.otherwise{
      fp16Mul.io.a := cvtFp4ToFp32A.io.out
    }
    when(bIsFp16){
      fp16Mul.io.b := cvtFp16ToFp32B.io.out
    }.elsewhen(bIsBf16){
      fp16Mul.io.b := cvtBf16ToFp32B.io.out
    }.elsewhen(bIsMx5M2 | bIsMx4M3){
      fp16Mul.io.b := cvtFp8ToFp32B.io.out
    }.otherwise{
      fp16Mul.io.b := cvtFp4ToFp32B.io.out
    }
    r0 = fp16Mul.io.prod
  }

  val fp8Mul = Module(new FpMulMx8())
  val cvtFp8ToFp8MidA = Module(new FpCvtMx8ToMid())
  val cvtFp8ToFp8MidB = Module(new FpCvtMx8ToMid())
  val cvtFp4ToFp8MidA = Module(new FpCvtMx4ToMx8Mid())
  val cvtFp4ToFp8MidB = Module(new FpCvtMx4ToMx8Mid())
  cvtFp8ToFp8MidA.io.in := io.a(15,8)
  cvtFp8ToFp8MidA.io.scale := io.scaleA
  cvtFp8ToFp8MidA.io.isE5M2 := aIsMx5M2
  cvtFp8ToFp8MidA.io.consumeValid := cvtConsumeValid
  cvtFp8ToFp8MidB.io.in := io.b(15,8)
  cvtFp8ToFp8MidB.io.scale := io.scaleB
  cvtFp8ToFp8MidB.io.isE5M2 := bIsMx5M2
  cvtFp8ToFp8MidB.io.consumeValid := cvtConsumeValid
  cvtFp4ToFp8MidA.io.in := io.a(7,4)
  cvtFp4ToFp8MidA.io.scale := io.scaleA
  cvtFp4ToFp8MidB.io.in := io.b(7,4)
  cvtFp4ToFp8MidB.io.scale := io.scaleB
  
  fp8Mul.io.rnd := io.rnd
  when(aIsMx5M2 | aIsMx4M3){
    fp8Mul.io.aSign := cvtFp8ToFp8MidA.io.sign
    fp8Mul.io.aExp := cvtFp8ToFp8MidA.io.exp
    fp8Mul.io.aMant := cvtFp8ToFp8MidA.io.mant
  }.otherwise{
    fp8Mul.io.aSign := cvtFp4ToFp8MidA.io.sign
    fp8Mul.io.aExp := cvtFp4ToFp8MidA.io.exp
    fp8Mul.io.aMant := cvtFp4ToFp8MidA.io.mant
  }
  when(bIsMx5M2 | bIsMx4M3){
    fp8Mul.io.bSign := cvtFp8ToFp8MidB.io.sign
    fp8Mul.io.bExp := cvtFp8ToFp8MidB.io.exp
    fp8Mul.io.bMant := cvtFp8ToFp8MidB.io.mant
  }.otherwise{
    fp8Mul.io.bSign := cvtFp4ToFp8MidB.io.sign
    fp8Mul.io.bExp := cvtFp4ToFp8MidB.io.exp
    fp8Mul.io.bMant := cvtFp4ToFp8MidB.io.mant
  }
  

  val fp4Mul0 = Module(new FpMulMx4())
  val cvtFp4ToFp4Mid0A = Module(new FpCvtMx4ToMid())
  val cvtFp4ToFp4Mid0B = Module(new FpCvtMx4ToMid())
  cvtFp4ToFp4Mid0A.io.in := io.a(11,8)
  cvtFp4ToFp4Mid0A.io.scale := io.scaleA
  cvtFp4ToFp4Mid0B.io.in := io.b(11,8)
  cvtFp4ToFp4Mid0B.io.scale := io.scaleB
  fp4Mul0.io.rnd := io.rnd
  fp4Mul0.io.aSign := cvtFp4ToFp4Mid0A.io.sign
  fp4Mul0.io.bSign := cvtFp4ToFp4Mid0B.io.sign
  fp4Mul0.io.bExp := cvtFp4ToFp4Mid0B.io.exp
  fp4Mul0.io.bMant := cvtFp4ToFp4Mid0B.io.mant
  fp4Mul0.io.aExp := cvtFp4ToFp4Mid0A.io.exp
  fp4Mul0.io.aMant := cvtFp4ToFp4Mid0A.io.mant
  
  val fp4Mul1 = Module(new FpMulMx4())
  val cvtFp4ToFp4Mid1A = Module(new FpCvtMx4ToMid())
  val cvtFp4ToFp4Mid1B = Module(new FpCvtMx4ToMid())
  cvtFp4ToFp4Mid1A.io.in := io.a(15,12)
  cvtFp4ToFp4Mid1A.io.scale := io.scaleA
  cvtFp4ToFp4Mid1B.io.in := io.b(15,12)
  cvtFp4ToFp4Mid1B.io.scale := io.scaleB
  fp4Mul1.io.rnd := io.rnd
  fp4Mul1.io.aSign := cvtFp4ToFp4Mid1A.io.sign
  fp4Mul1.io.bSign := cvtFp4ToFp4Mid1B.io.sign
  fp4Mul1.io.bExp := cvtFp4ToFp4Mid1B.io.exp
  fp4Mul1.io.bMant := cvtFp4ToFp4Mid1B.io.mant
  fp4Mul1.io.aExp := cvtFp4ToFp4Mid1A.io.exp
  fp4Mul1.io.aMant := cvtFp4ToFp4Mid1A.io.mant
  
  // hasFp=false：lane0 走专属窄乘法器（克隆 lane1 结构）。cvtL0* 取 io.a/io.b 的低 8/4 bit
  //   （lane0 元素），与 lane1 取高位 io.a(15,8)/(7,4) 区分。
  if (!hasFp) {
    val mulL0 = Module(new FpMulMx8())
    val cvtL0Fp8ToMidA = Module(new FpCvtMx8ToMid())
    val cvtL0Fp8ToMidB = Module(new FpCvtMx8ToMid())
    val cvtL0Fp4ToMx8MidA = Module(new FpCvtMx4ToMx8Mid())
    val cvtL0Fp4ToMx8MidB = Module(new FpCvtMx4ToMx8Mid())
    cvtL0Fp8ToMidA.io.in := io.a(7,0)
    cvtL0Fp8ToMidA.io.scale := io.scaleA
    cvtL0Fp8ToMidA.io.isE5M2 := aIsMx5M2
    cvtL0Fp8ToMidA.io.consumeValid := cvtConsumeValid
    cvtL0Fp8ToMidB.io.in := io.b(7,0)
    cvtL0Fp8ToMidB.io.scale := io.scaleB
    cvtL0Fp8ToMidB.io.isE5M2 := bIsMx5M2
    cvtL0Fp8ToMidB.io.consumeValid := cvtConsumeValid
    cvtL0Fp4ToMx8MidA.io.in := io.a(3,0)
    cvtL0Fp4ToMx8MidA.io.scale := io.scaleA
    cvtL0Fp4ToMx8MidB.io.in := io.b(3,0)
    cvtL0Fp4ToMx8MidB.io.scale := io.scaleB
    mulL0.io.rnd := io.rnd
    when(aIsMx5M2 | aIsMx4M3){
      mulL0.io.aSign := cvtL0Fp8ToMidA.io.sign
      mulL0.io.aExp := cvtL0Fp8ToMidA.io.exp
      mulL0.io.aMant := cvtL0Fp8ToMidA.io.mant
    }.otherwise{
      mulL0.io.aSign := cvtL0Fp4ToMx8MidA.io.sign
      mulL0.io.aExp := cvtL0Fp4ToMx8MidA.io.exp
      mulL0.io.aMant := cvtL0Fp4ToMx8MidA.io.mant
    }
    when(bIsMx5M2 | bIsMx4M3){
      mulL0.io.bSign := cvtL0Fp8ToMidB.io.sign
      mulL0.io.bExp := cvtL0Fp8ToMidB.io.exp
      mulL0.io.bMant := cvtL0Fp8ToMidB.io.mant
    }.otherwise{
      mulL0.io.bSign := cvtL0Fp4ToMx8MidB.io.sign
      mulL0.io.bExp := cvtL0Fp4ToMx8MidB.io.exp
      mulL0.io.bMant := cvtL0Fp4ToMx8MidB.io.mant
    }
    r0 = mulL0.io.prod
  }

  // hasBf16 && !hasFp16：lane0 走 BF16/MX 共享窄乘法器 FpMulBf16Mx（8×8 尾数阵列），结构同上方
  //   if(!hasFp) 块（同取低 8/4 bit），两处不同：
  //     ① 送入前用 isBf16Pair 在 BF16 原始字段与 MX 中间表示之间二选一（MX 的 3-bit 尾数左对齐
  //        到 7-bit：Cat(mant,0.U(4.W))，符合 FpMulBf16Mx 的 MX 尾数左对齐契约，使 MX 臂与
  //        FpMulMx8 逐位相等）；
  //     ② FpMulBf16Mx 无 rnd 端口（MX 域 guardBit 结构性恒 0、BF16 域乘积尾数 16 位全保留 ⟹
  //        两域都无舍入位可用）。
  //   BF16 运行期 r1/r2/r3 被 aIsMx* 判据门为 0、累加器 is4x=is2x=0 ⟹ 退化为 {C, L0} 两操作数模式。
  if (hasBf16 && !hasFp16) {
    val mulL0 = Module(new FpMulBf16Mx())
    val cvtL0Fp8ToMidA = Module(new FpCvtMx8ToMid())
    val cvtL0Fp8ToMidB = Module(new FpCvtMx8ToMid())
    val cvtL0Fp4ToMx8MidA = Module(new FpCvtMx4ToMx8Mid())
    val cvtL0Fp4ToMx8MidB = Module(new FpCvtMx4ToMx8Mid())
    cvtL0Fp8ToMidA.io.in := io.a(7,0)
    cvtL0Fp8ToMidA.io.scale := io.scaleA
    cvtL0Fp8ToMidA.io.isE5M2 := aIsMx5M2
    cvtL0Fp8ToMidA.io.consumeValid := cvtConsumeValid
    cvtL0Fp8ToMidB.io.in := io.b(7,0)
    cvtL0Fp8ToMidB.io.scale := io.scaleB
    cvtL0Fp8ToMidB.io.isE5M2 := bIsMx5M2
    cvtL0Fp8ToMidB.io.consumeValid := cvtConsumeValid
    cvtL0Fp4ToMx8MidA.io.in := io.a(3,0)
    cvtL0Fp4ToMx8MidA.io.scale := io.scaleA
    cvtL0Fp4ToMx8MidB.io.in := io.b(3,0)
    cvtL0Fp4ToMx8MidB.io.scale := io.scaleB
    //MX 中间表示选择（mx8 / mx4），判据与上方 if(!hasFp) 块同源
    val mxSignA = Wire(UInt(1.W)); val mxExpA = Wire(UInt(8.W)); val mxMantA = Wire(UInt(3.W))
    val mxSignB = Wire(UInt(1.W)); val mxExpB = Wire(UInt(8.W)); val mxMantB = Wire(UInt(3.W))
    when(aIsMx5M2 | aIsMx4M3){
      mxSignA := cvtL0Fp8ToMidA.io.sign
      mxExpA := cvtL0Fp8ToMidA.io.exp
      mxMantA := cvtL0Fp8ToMidA.io.mant
    }.otherwise{
      mxSignA := cvtL0Fp4ToMx8MidA.io.sign
      mxExpA := cvtL0Fp4ToMx8MidA.io.exp
      mxMantA := cvtL0Fp4ToMx8MidA.io.mant
    }
    when(bIsMx5M2 | bIsMx4M3){
      mxSignB := cvtL0Fp8ToMidB.io.sign
      mxExpB := cvtL0Fp8ToMidB.io.exp
      mxMantB := cvtL0Fp8ToMidB.io.mant
    }.otherwise{
      mxSignB := cvtL0Fp4ToMx8MidB.io.sign
      mxExpB := cvtL0Fp4ToMx8MidB.io.exp
      mxMantB := cvtL0Fp4ToMx8MidB.io.mant
    }
    //mul assign：BF16 直接送原始 {sign, exp(8), mant(7)} 字段，MX 送左对齐中间表示
    mulL0.io.aSign := Mux(isBf16Pair, io.a(15).asUInt, mxSignA)
    mulL0.io.aExp  := Mux(isBf16Pair, io.a(14,7), mxExpA)
    mulL0.io.aMant := Mux(isBf16Pair, io.a(6,0), Cat(mxMantA, 0.U(4.W)))
    mulL0.io.bSign := Mux(isBf16Pair, io.b(15).asUInt, mxSignB)
    mulL0.io.bExp  := Mux(isBf16Pair, io.b(14,7), mxExpB)
    mulL0.io.bMant := Mux(isBf16Pair, io.b(6,0), Cat(mxMantB, 0.U(4.W)))
    mulL0.io.isBf16 := isBf16Pair

    // 混格式操作数对（BF16 × MX）无硬件语义：lane0 只能按单一域解码，且 r1/r2/r3 门控判据只看
    //   A 侧 ⟹ 混对会静默算错，故在消费沿拦死。
    val mxPair = (aIsMx5M2 | aIsMx4M3 | aIsMx2M1) & (bIsMx5M2 | bIsMx4M3 | bIsMx2M1)
    assert(!io.consumeValid || isBf16Pair || mxPair,
      "WbFusionMacFp: lane0 BF16 path accepts only BF16 x BF16 or MX x MX operand pairs")
    // BF16 的有限算术契约：exp=255（inf/nan）在输入域之外，由软件保证不出现。
    assert(!io.consumeValid || !isBf16Pair || (io.a(14,7) =/= 255.U && io.b(14,7) =/= 255.U),
      "WbFusionMacFp: BF16 inf/nan operands are outside the finite arithmetic contract")
    r0 = mulL0.io.prod
  }

  val r1 = Mux(aIsMx5M2 | aIsMx4M3 | aIsMx2M1,  fp8Mul.io.prod, 0.U(32.W))
  val r2 = Mux(aIsMx2M1, fp4Mul0.io.prod, 0.U(32.W))
  val r3 = Mux(aIsMx2M1, fp4Mul1.io.prod, 0.U(32.W))

  // 五槽累加器的条件例化 + 共享接线 helper。FpAdd / FpAddNarrow 逐端口一致但是不同的类且无公共
  //   trait，故把 8 个端口引用传给同一个 helper：接线语义只写一份，杜绝两分支漏接/错接端口
  //   （错接会被 last-connect 静默覆盖）；参数全为 Data 引用，不建硬件节点。
  private def connectFiveSlotAdder(cReg: UInt, is4x: Bool, is2x: Bool,
                                   prodFp16: UInt, prodFp8: UInt,
                                   prodFp4_0: UInt, prodFp4_1: UInt, sum: UInt): Unit = {
    cReg      := io.cReg
    is4x      := isMx4
    is2x      := isMx8
    prodFp16  := r0
    prodFp8   := r1
    prodFp4_0 := r2
    prodFp4_1 := r3
    io.sum    := sum
  }
  if (useNarrowAdd) {
    // 默认路径：L2 窄窗口累加器（精度契约见 FpAddNarrow.scala 头注）
    val fpAddNarrow = Module(new FpAddNarrow(cEffW, narrowW))
    connectFiveSlotAdder(fpAddNarrow.io.cReg, fpAddNarrow.io.is4x, fpAddNarrow.io.is2x,
      fpAddNarrow.io.prodFp16, fpAddNarrow.io.prodFp8,
      fpAddNarrow.io.prodFp4_0, fpAddNarrow.io.prodFp4_1, fpAddNarrow.io.sum)
  } else {
    // useNarrowAdd=false 分支（须显式 pin）：bit-exact 基线 FpAdd
    val fpAdd = Module(new FpAdd(cEffW))
    connectFiveSlotAdder(fpAdd.io.cReg, fpAdd.io.is4x, fpAdd.io.is2x,
      fpAdd.io.prodFp16, fpAdd.io.prodFp8,
      fpAdd.io.prodFp4_0, fpAdd.io.prodFp4_1, fpAdd.io.sum)
  }
  
}

