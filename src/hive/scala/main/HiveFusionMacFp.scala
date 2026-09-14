import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters._


//this funsion mac support fp16/bf16/mxfp8/mxfp4, suppose the input is 16bits
//
// 【杠杆 A / 配置感知】supportedFmts 是【绝对集】：写子集即删硬件（对齐 HiveCoreConfig.scala:47 语义）。
//   · 默认 = 全五格式(FP16/BF16/MXE4M3/MXE5M2/MXE2M1) ⟹ hasFp=true ⟹ lane0 走宽阵列
//     (FpMulFp16Bf16 + 8 个 fp32 域 cvt + 4:1 mux) ⟹ 与历史 RTL normalized-diff=0（回归锚点）。
//   · opt-in 去掉 FP16/BF16 ⟹ hasFp=false ⟹ 删宽阵列（4 个 fp16/bf16 格式标志 aIsFp16/aIsBf16/
//     bIsFp16/bIsBf16 随之失去唯一消费者，被 firtool DCE 自动消除）+ lane0 改走专属窄乘法器
//     FpMulMx8（克隆 lane1 结构，bit-exact 论证见蓝图 §5）。
//   · hasFp 只在 elaboration 期决定【生成哪些实例】，绝不门控运行期：is4x/is2x、r1/r2/r3 的 Mux、
//     FpAdd 接线逐字不动（对齐项目铁律「编译期 if 不门控运行期」+ HiveEnum.scala:32-35）。
class HiveFusionMacFp(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 40,
  supportedFmts: Set[DataFormat.Type] =
    Set(DataFormat.FP16, DataFormat.BF16,
        DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)
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
    // 消费有效门控（仅 hasMx 块内有意义）：透传给 4×HiveCvtOpMx 的 inf/nan 断言；
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
    cvtFp8ToFp32B.io.in := io.b(7,0)
    cvtFp8ToFp32B.io.scale := io.scaleB
    cvtFp8ToFp32B.io.isE5M2 := bIsMx5M2
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
  cvtFp8ToFp8MidB.io.in := io.b(15,8)
  cvtFp8ToFp8MidB.io.scale := io.scaleB
  cvtFp8ToFp8MidB.io.isE5M2 := bIsMx5M2
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
    cvtL0Fp8ToMidB.io.in := io.b(7,0)
    cvtL0Fp8ToMidB.io.scale := io.scaleB
    cvtL0Fp8ToMidB.io.isE5M2 := bIsMx5M2
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

  val fpAdd = Module(new FpAdd(cEffW))
  val r1 = Mux(aIsMx5M2 | aIsMx4M3 | aIsMx2M1,  fp8Mul.io.prod, 0.U(32.W))
  val r2 = Mux(aIsMx2M1, fp4Mul0.io.prod, 0.U(32.W))
  val r3 = Mux(aIsMx2M1, fp4Mul1.io.prod, 0.U(32.W))
  fpAdd.io.cReg := io.cReg
  fpAdd.io.is4x := isMx4
  fpAdd.io.is2x := isMx8
  fpAdd.io.prodFp16 := r0
  fpAdd.io.prodFp8 := r1
  fpAdd.io.prodFp4_0 := r2
  fpAdd.io.prodFp4_1 := r3
  io.sum := fpAdd.io.sum
  
}


object HiveFusionMacFpElaborate extends App {
  // 双配置发射（对齐 HiveCoreElaborate.scala:166-168「绝不覆盖回归锚点」约定，见蓝图 §2.4/§7）：
  //   · 默认配置（hasFp=true，全五格式）    → src/hive/verilog_fusionfp/        （回归锚点，零 diff 基准）
  //   · 移除配置（去 FP16/BF16，hasFp=false）→ src/hive/verilog_fusionfp_nofp16/ （独立目录：Chisel 不把
  //     构造参数附到模块名，两配置顶层都叫 Hive_HiveFusionMacFp，发同目录会互相覆盖，R10）
  // 每次发射前清空目标目录（照抄 HiveCoreElaborate.scala:185-196 的 deleteRecursively，R11）：
  //   否则双配置反复发射会在目录内滞留陈旧 .sv。
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

  // --- 配置 1：默认（hasFp=true，全五格式）→ 回归锚点，与 golden normalized-diff=0 ---
  emitClean("src/hive/verilog_fusionfp")
  ChiselStage.emitSystemVerilogFile(
     gen = withModulePrefix("Hive") { new HiveFusionMacFp() }
    ,args = Array("--target-dir", "src/hive/verilog_fusionfp")
  )
  println("[HiveFusionMacFpElaborate] default config (hasFp=true) -> src/hive/verilog_fusionfp")

  // --- 配置 2：移除 fp16/bf16（hasFp=false）→ lane0 走窄乘法器，删宽阵列 ---
  emitClean("src/hive/verilog_fusionfp_nofp16")
  ChiselStage.emitSystemVerilogFile(
     gen = withModulePrefix("Hive") {
       new HiveFusionMacFp(
         supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)
       )
     }
    ,args = Array("--target-dir", "src/hive/verilog_fusionfp_nofp16")
  )
  println("[HiveFusionMacFpElaborate] nofp16 config (hasFp=false) -> src/hive/verilog_fusionfp_nofp16")
}
