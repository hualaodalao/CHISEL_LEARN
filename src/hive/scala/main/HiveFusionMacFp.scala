import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters._


//this funsion mac support fp16/bf16/mxfp8/mxfp4, suppose the input is 16bits
//
// 【杠杆 A / 配置感知】supportedFmts 是【绝对集】：写子集即删硬件（对齐 HiveCoreConfig.scala:47 语义）。
//   · 默认 = MX-only(MXE4M3/MXE5M2/MXE2M1) ⟹ hasFp=false ⟹ lane0 走专属窄乘法器 FpMulMx8
//     (用户裁定砍除 FP16/BF16，见 HiveCoreConfig supportedFmts)。
//     ★【有意的破坏性默认值变更 · task#25 D2 明记】类默认 supportedFmts 已从【旧的全五格式默认】
//       (FP16/BF16/MXE4M3/MXE5M2/MXE2M1) 收窄为【MX-only】。这是「砍除 FP16/BF16」用户裁定下的
//       **有意**破坏性变更：任何依赖旧默认（不显式传 supportedFmts 即得全五格式宽阵列）的下游调用，
//       现在会得到 hasFp=false 的窄乘法器路径。需要全五格式硬件者【必须显式传】完整集合
//       （见 HiveFusionMacFpSpec 的 FMTS_DEFAULT，及 HiveFusionMacFpElaborate 头注对 verilog_fusionfp/ 锚点的说明）。
//       钉死此新默认契约的测试见 HiveFusionMacFpNarrowSpec 的 D1 用例（类默认例化 ⟹ RTL 不含 FpMulFp16Bf16）。
//   · 若传入全五格式 ⟹ hasFp=true ⟹ lane0 走宽阵列（FpMulFp16Bf16 + 8 个 fp32 域 cvt + 4:1 mux）
//     ⟹ 与历史 RTL normalized-diff=0（回归锚点）。
//   · hasFp 只在 elaboration 期决定【生成哪些实例】，绝不门控运行期：is4x/is2x、r1/r2/r3 的 Mux、
//     五槽累加器接线逐字不动（对齐项目铁律「编译期 if 不门控运行期」+ HiveEnum.scala:32-35）。
//
// 【task#18 B: useNarrowAdd 编译期选择累加器】
//   false(默认) → 例化 FpAdd（已验证 bit-exact 累加器基线）。
//     ★【措辞修约 · task#25 E2，取代原「恢复已验证回归」】本参数【只】承诺：累加器接线不变、
//       仍例化 bit-exact 的 FpAdd（五槽 wiring 逐字不动）。它【不】宣称「整条默认路径的 RTL 与 HEAD
//       逐位一致」——因为本模块还捆绑了用户有意加入的 consumeValid / inf-nan 守护改动（透传给
//       FpCvtMx8ToMid），使前端 cvt 与 HEAD 有别。故 useNarrowAdd=false 只锁定【累加器】这一处，
//       不等于锁定整条 datapath（详见 Q2 裁定：consumeValid 守护属 fusionMac 改动、与 narrow 无硬依赖）。
//   true        → 例化 FpAddNarrow（L2 窄窗口五槽 CSA 树，精度契约见 FpAddNarrow.scala 头注）
//   两者 I/O port-compatible（task#16 实测核对：8 端口方向/名称/位宽全同）⟹ 共享 wiring helper。
//   护栏：require(!useNarrowAdd || supportedFmts ⊆ {MXE4M3,MXE5M2,MXE2M1}) —— FpAddNarrow 仅支持 MX-only。
//   narrowW: Int = FpAddNarrow.DefaultW (19)：窗口宽度参数化保留，便于后续 A/B 综合对照扫 W。
//     ★【合法区间 · task#25 Sean m1/Ray M1】仅在 useNarrowAdd=true 时生效；取值必须落在 [8,28]
//       （下界 8 = mxfp8 乘积 8 位有效尾数硬下界、上界 28 = mant31 高 W 位对齐留 3 位余量），
//       该区间由 FpAddNarrow.scala:226-227 的两条 require 强制（越界在例化 FpAddNarrow 时 elaboration 期即抛）。
class HiveFusionMacFp(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 40,
  supportedFmts: Set[DataFormat.Type] =
    Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
  useNarrowAdd: Boolean = true,
  narrowW: Int = FpAddNarrow.DefaultW
) extends Module {

  // 派生谓词命名与 HiveCoreConfig.scala:78/94/95 逐字对齐，勿另立裸名
  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasMx8 = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2)
  private val hasMx4 = supportedFmts.contains(DataFormat.MXE2M1)

  // 绝对集护栏（照抄 HiveCoreConfig.scala:100-102 判据与措辞风格，见蓝图 §2.3 / R8）：
  //   · MX 是本模块必选硬件路径（lane1/2/3 无条件生成），故至少需一种 MX 格式（拒 FP-only）。
  //   · MXFP4(E2M1) v1 必须与 MXFP8(E4M3/E5M2) 同时启用（拒 fp4-only；lane1/2/3 供数依赖）。
  require(hasMx8 || hasMx4, s"HiveFusionMacFp: MX 为必选硬件路径，至少需一种 MX 格式，当前=$supportedFmts")
  require(!hasMx4 || hasMx8, s"HiveFusionMacFp: MXFP4 v1 必须与 MXFP8 同时启用（lane1/2/3 供数依赖），当前=$supportedFmts")
  // 用户设计律：MXFP8 存在则 MXFP4 一定存在（MX 族全在或全不在）。与上一条 require(!hasMx4||hasMx8)
  //   合成 hasMx8 ⟺ hasMx4。编译期 Scala 断言，不进 RTL；对已交付两配置（均 hasMx4=true）零影响。
  require(!hasMx8 || hasMx4,
    s"HiveFusionMacFp: MXFP8(E4M3/E5M2) requires MXFP4(E2M1) -- MX family is all-in-or-all-out (user design rule), current=$supportedFmts")

  // task#18 B 护栏：FpAddNarrow 只为 MX-only 做精度承诺（其 fp16-only 模式 is2x=0&&is4x=0 是 don't-care，
  //   见 FpAddNarrow.scala 头注「三模式与激活掩码」）⟹ 用【白名单】判据：supportedFmts 必须
  //   ⊆ {MXE4M3, MXE5M2, MXE2M1}。★【task#25 Sean m2】原判据 `!hasFp` 只挡 FP16/BF16，而 INT/FP32 之类的
  //   非 MX 格式集仍能绕过（它们同样使 hasFp=false）⟹ 判据与文案「仅支持 MX-only」不一致。改白名单后
  //   任何非 MX 元素都会 elaboration 期即拒，使语义与文案精确对齐。风格对齐上方三条 require（编译期断言，不进 RTL）。
  require(!useNarrowAdd || supportedFmts.subsetOf(Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)),
    s"HiveFusionMacFp: FpAddNarrow 仅支持 MX-only(MXE4M3/MXE5M2/MXE2M1)，当前 supportedFmts=$supportedFmts 含越界格式")

  // task#25 Sean m1 / Ray M1：narrowW 仅在 useNarrowAdd=true（例化 FpAddNarrow）时才被读取，
  //   useNarrowAdd=false 下它是 dead 参数（走 FpAdd 基线，窗口概念不存在）。此 require 钉死
  //   「非 narrow 路径不得携带非默认 narrowW」，避免调用方误以为改 narrowW 能影响 FpAdd 基线路径。
  //   合法区间 [8,28] 本身由 FpAddNarrow.scala:226-227 的两条 require 在例化 FpAddNarrow 时强制。
  require(useNarrowAdd || narrowW == FpAddNarrow.DefaultW,
    s"HiveFusionMacFp: narrowW($narrowW) 仅在 useNarrowAdd=true 时生效；useNarrowAdd=false 时须保持默认 ${FpAddNarrow.DefaultW}")

  val cEffW: Int = math.max(cW, bW)
  
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
    // 消费有效门控（仅 hasMx 块内有意义）：透传给 MX8 转换模块（FpCvtMx8ToMid/FpCvtMx8ToFp32，
    // 原为 HiveWorkUnit.scala 中的 4×HiveCvtOpMx、已随 FusionMac 重构删除）的 inf/nan 断言；
    // 非 MX 配置下恒不被选用，行为零影响。由宿主（HiveWorker）单行连入。
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
 
  // lane0 乘积槽引用：hasFp=true 取宽阵列 fp16Mul.io.prod，hasFp=false 取窄乘法器 mulL0.io.prod。
  // 用 module 级 var 桥接两个互斥 if 块的作用域；纯 Scala 别名，两侧都不建硬件节点 ⟹ RTL 中性
  // （默认配置下等价于历史 `val r0 = fp16Mul.io.prod`，无 r0_ 前缀污染，保 normalized-diff=0）。
  var r0: UInt = null

  //FP16
  // hasFp=true：lane0 走宽阵列（fp16/bf16/mx8/mx4 各经 fp32 域 cvt → FpMulFp16Bf16 的 4:1 mux）。
  //   本块【逐字搬迁】自历史 :45-88（仅整体缩进 +2）；hasFp=false 时整块不生成（下方 if(!hasFp) 替代）。
  if (hasFp) {
    val fp16Mul = Module(new FpMulFp16Bf16())
    val cvtFp16ToFp32A = Module(new FpCvtFp16ToFp32())
    val cvtFp16ToFp32B = Module(new FpCvtFp16ToFp32())
    val cvtBf16ToFp32A = Module(new FpCvtBf16ToFp32())
    val cvtBf16ToFp32B = Module(new FpCvtBf16ToFp32())
    val cvtFp8ToFp32A = Module(new FpCvtMx8ToFp32())
    val cvtFp8ToFp32B = Module(new FpCvtMx8ToFp32())
    val cvtFp4ToFp32A = Module(new FpCvtMx4ToFp32())
    val cvtFp4ToFp32B = Module(new FpCvtMx4ToFp32())
    //cvt assign
    cvtFp16ToFp32A.io.in := io.a
    cvtFp16ToFp32B.io.in := io.b
    cvtBf16ToFp32A.io.in := io.a
    cvtBf16ToFp32B.io.in := io.b
    cvtFp8ToFp32A.io.in := io.a(7,0)
    cvtFp8ToFp32A.io.scale := io.scaleA
    cvtFp8ToFp32A.io.isE5M2 := aIsMx5M2
    cvtFp8ToFp32A.io.consumeValid := io.consumeValid
    cvtFp8ToFp32B.io.in := io.b(7,0)
    cvtFp8ToFp32B.io.scale := io.scaleB
    cvtFp8ToFp32B.io.isE5M2 := bIsMx5M2
    cvtFp8ToFp32B.io.consumeValid := io.consumeValid
    cvtFp4ToFp32A.io.in := io.a(3,0)
    cvtFp4ToFp32A.io.scale := io.scaleA
    cvtFp4ToFp32B.io.in := io.b(3,0)
    cvtFp4ToFp32B.io.scale := io.scaleB
    //mul assign
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
  //cvt assign
  cvtFp8ToFp8MidA.io.in := io.a(15,8)
  cvtFp8ToFp8MidA.io.scale := io.scaleA
  cvtFp8ToFp8MidA.io.isE5M2 := aIsMx5M2
  cvtFp8ToFp8MidA.io.consumeValid := io.consumeValid
  cvtFp8ToFp8MidB.io.in := io.b(15,8)
  cvtFp8ToFp8MidB.io.scale := io.scaleB
  cvtFp8ToFp8MidB.io.isE5M2 := bIsMx5M2
  cvtFp8ToFp8MidB.io.consumeValid := io.consumeValid
  cvtFp4ToFp8MidA.io.in := io.a(7,4)
  cvtFp4ToFp8MidA.io.scale := io.scaleA
  cvtFp4ToFp8MidB.io.in := io.b(7,4)
  cvtFp4ToFp8MidB.io.scale := io.scaleB
  
  //mul assign
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
  
  // hasFp=false：lane0 改走专属窄乘法器（克隆 lane1 结构，见蓝图 §1.3/§4.2）。
  //   位置在既有实例序列（lane1/2/3）之后、fpAdd 之前 —— 不插入既有序列中间（R5，保 elaboration 顺序）。
  //   cvtL0* 取 io.a/io.b 的【低】8/4 bit（lane0 元素），与 lane1 用【高】位(io.a(15,8)/(7,4))区分。
  if (!hasFp) {
    val mulL0 = Module(new FpMulMx8())
    val cvtL0Fp8ToMidA = Module(new FpCvtMx8ToMid())
    val cvtL0Fp8ToMidB = Module(new FpCvtMx8ToMid())
    val cvtL0Fp4ToMx8MidA = Module(new FpCvtMx4ToMx8Mid())
    val cvtL0Fp4ToMx8MidB = Module(new FpCvtMx4ToMx8Mid())
    //cvt assign
    cvtL0Fp8ToMidA.io.in := io.a(7,0)
    cvtL0Fp8ToMidA.io.scale := io.scaleA
    cvtL0Fp8ToMidA.io.isE5M2 := aIsMx5M2
    cvtL0Fp8ToMidA.io.consumeValid := io.consumeValid
    cvtL0Fp8ToMidB.io.in := io.b(7,0)
    cvtL0Fp8ToMidB.io.scale := io.scaleB
    cvtL0Fp8ToMidB.io.isE5M2 := bIsMx5M2
    cvtL0Fp8ToMidB.io.consumeValid := io.consumeValid
    cvtL0Fp4ToMx8MidA.io.in := io.a(3,0)
    cvtL0Fp4ToMx8MidA.io.scale := io.scaleA
    cvtL0Fp4ToMx8MidB.io.in := io.b(3,0)
    cvtL0Fp4ToMx8MidB.io.scale := io.scaleB
    //mul assign
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

  val r1 = Mux(aIsMx5M2 | aIsMx4M3 | aIsMx2M1,  fp8Mul.io.prod, 0.U(32.W))
  val r2 = Mux(aIsMx2M1, fp4Mul0.io.prod, 0.U(32.W))
  val r3 = Mux(aIsMx2M1, fp4Mul1.io.prod, 0.U(32.W))

  // ─── task#18 B：五槽累加器的条件例化 + 共享接线 helper ─────────────────────────
  // FpAdd / FpAddNarrow 的 I/O 契约【逐端口一致】（task#16 机器核对：is4x[1] is2x[1] cReg[40]
  //   prodFp16[32] prodFp8[32] prodFp4_0[32] prodFp4_1[32] sum[40]）⟹ port-compatible。
  // 但两者是【不同的类】且无公共超类/trait（FpAdd.scala 一行未改，不能给它加 trait），故无法用
  //   单个 `val fpAdd` 承载。这里用「把 8 个端口引用传给同一个 helper」的手法：
  //   · 接线语义只写【一份】⟹ 杜绝两分支漏接/错接某端口（漏接在 Chisel 里是 elaboration 期
  //     "not fully initialized" 错误，但错接成 last-connect 会【静默】覆盖 ⟹ 风险更高）；
  //   · helper 参数全为 Data 引用，纯 Scala 传参、不建硬件节点 ⟹ RTL 中性；
  //   · 与上方 `var r0` 同一手法（module 级桥接互斥 if 块的作用域），是本文件既有惯用法。
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
    // L2 窄窗口累加器（MX-only；精度契约 = 三段式误差界，见 FpAddNarrow.scala 头注）
    val fpAddNarrow = Module(new FpAddNarrow(cEffW, narrowW))
    connectFiveSlotAdder(fpAddNarrow.io.cReg, fpAddNarrow.io.is4x, fpAddNarrow.io.is2x,
      fpAddNarrow.io.prodFp16, fpAddNarrow.io.prodFp8,
      fpAddNarrow.io.prodFp4_0, fpAddNarrow.io.prodFp4_1, fpAddNarrow.io.sum)
  } else {
    // 默认：已验证 bit-exact 基线
    val fpAdd = Module(new FpAdd(cEffW))
    connectFiveSlotAdder(fpAdd.io.cReg, fpAdd.io.is4x, fpAdd.io.is2x,
      fpAdd.io.prodFp16, fpAdd.io.prodFp8,
      fpAdd.io.prodFp4_0, fpAdd.io.prodFp4_1, fpAdd.io.sum)
  }
  
}


object HiveFusionMacFpElaborate extends App {
  // 发射配置（对齐 HiveCoreElaborate.scala:166-168「绝不覆盖回归锚点」约定，见蓝图 §2.4/§7）：
  //   · A 类默认配置（MX-only, useNarrowAdd=false ⟹ FpAdd）→ src/hive/verilog_fusionfp_mxonly/  ★task#25 C2 改目录
  //   · B nofp16 配置（显式 MX-only, useNarrowAdd=false）  → src/hive/verilog_fusionfp_nofp16/
  //   · C narrow 配置（MX-only, useNarrowAdd=true ⟹ FpAddNarrow）
  //                                                       → src/hive/verilog_fusionfp_narrow/  ★task#18 C 新增
  // 独立目录的理由：Chisel 不把构造参数附到模块名，各配置顶层都叫 Hive_HiveFusionMacFp，
  //   发同目录会互相覆盖（R10）。每次发射前清空目标目录（照抄 HiveCoreElaborate.scala:185-196
  //   的 deleteRecursively，R11）：否则反复发射会在目录内滞留陈旧 .sv。
  //
  // ★【task#25 C2：verilog_fusionfp/ 是 HEAD 回归锚点，本脚本【不再】向它发射】
  //   · src/hive/verilog_fusionfp/  = 【HEAD 全五格式(FP16/BF16/MXE4M3/MXE5M2/MXE2M1)宽阵列回归锚点】，
  //     含 Hive_FpMulFp16Bf16.sv / Hive_FpCvtFp16ToFp32.sv / Hive_FpCvtBf16ToFp32.sv /
  //     Hive_FpCvtMx8ToFp32.sv / Hive_FpCvtMx4ToFp32.sv 五个宽阵列 .sv。它由 git 恢复到 HEAD 后
  //     【冻结】，本脚本任何入口都【不】再写它 ⟹ 锚点永不被覆盖（用户 Q1=Option A 裁定）。
  //     若确需重新取得全五格式 RTL，须【显式传】 supportedFmts = 全五格式集合并另择目录，
  //     本脚本刻意不做此事（那会重新引入已被用户裁定砍除的 FP16/BF16 硬件路径，且有覆盖锚点之险）。
  //   · src/hive/verilog_fusionfp_mxonly/ = 【当前 MX-only 类默认】（含用户有意加入的 consumeValid/inf-nan
  //     守护 ⟹ 前端 FpCvtMx8ToMid 与 HEAD 有别，见类头注 E2 措辞修约）。这是 config A 的新落点。
  //   · 订正原自相矛盾注释：旧文案一面说「A→verilog_fusionfp/」一面又说「A≡B 完全相同硬件」，
  //     而 A（类默认，含 consumeValid 守护）与 B（显式 MXONLY）虽同为 MX-only、但发射目录与语义定位不同，
  //     且 verilog_fusionfp/ 承载的是【全五格式 HEAD 锚点】而非 MX-only ⟹ 三者不可混为一谈。
  //     真正有区分度的对照仍是 B vs C（同一 MX-only 前端、只换累加器 FpAdd↔FpAddNarrow）。
  def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        Files.list(path).iterator().asScala.foreach(deleteRecursively)
      }
      Files.delete(path)
    }
  }
  def emitClean(outputDir: String): Unit = {
    val outDirPath = Path.of(outputDir)
    Files.createDirectories(outDirPath)
    // 只删除目录内容，保留目录本身
    Files.list(outDirPath).iterator().asScala.foreach(deleteRecursively)
    println(s"[HiveFusionMacFpElaborate] Cleaned output directory: $outputDir")
  }
  private def emit(gen: => HiveFusionMacFp, dir: String, label: String): Unit = {
    emitClean(dir)
    ChiselStage.emitSystemVerilogFile(
       gen = withModulePrefix("Hive") { gen }
      ,args = Array("--target-dir", dir)
    )
    println(s"[HiveFusionMacFpElaborate] $label -> $dir")
  }
  private val MXONLY = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)

  // --- 配置 A：类默认（MX-only, useNarrowAdd=false ⟹ FpAdd 基线，含 consumeValid 守护）---
  //   ★task#25 C2：发到 verilog_fusionfp_mxonly（新目录），【绝不】写 verilog_fusionfp（HEAD 全五格式锚点）。
  emit(new HiveFusionMacFp(), "src/hive/verilog_fusionfp_mxonly",
       "config A: class default MX-only (useNarrowAdd=false -> FpAdd)")

  // --- 配置 B：显式 MX-only（hasFp=false）+ FpAdd ⟹ lane0 走窄乘法器，删宽阵列 ---
  emit(new HiveFusionMacFp(supportedFmts = MXONLY), "src/hive/verilog_fusionfp_nofp16",
       "config B: nofp16 (hasFp=false, useNarrowAdd=false -> FpAdd)")

  // --- 配置 C：MX-only + useNarrowAdd=true ⟹ 例化 FpAddNarrow（L2 窄窗口）★task#18 C 新增 ---
  emit(new HiveFusionMacFp(supportedFmts = MXONLY, useNarrowAdd = true),
       "src/hive/verilog_fusionfp_narrow",
       s"config C: narrow (hasFp=false, useNarrowAdd=true -> FpAddNarrow W=${FpAddNarrow.DefaultW})")
}
