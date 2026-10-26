import chisel3._
import chisel3.util._

/** WbFusionMacInt —— 整数融合 MAC（INT8 双 lane / INT4 四 lane SIMD，四路乘积单拍一次吸收）
  *
  * 【lane 排布（16-bit 操作数打包，two's complement 编码；与 WbDebugModel 金锚 /
  *   DMA 供数序 / WorkerBeeCalcConfig.elemsPerLane 同源，勿另立裸常数）】
  *   INT8（双 lane，每 16-bit lane 打包 2 个 int8）：
  *     lane0 = a(7,0)   / b(7,0)     偶 k = 2j
  *     lane1 = a(15,8)  / b(15,8)    奇 k = 2j+1
  *   INT4（四 lane，每 16-bit lane 打包 4 个 nibble）：
  *     lane0 = a(3,0)   / b(3,0)     k = 4j+0
  *     lane1 = a(7,4)   / b(7,4)     k = 4j+1
  *     lane2 = a(11,8)  / b(11,8)    k = 4j+2
  *     lane3 = a(15,12) / b(15,12)   k = 4j+3
  *
  * 【统一乘法形态】四路 lane 一律「sext 到 8-bit 有符号 -> 8x8 = 16-bit 有符号乘」：
  *   · INT8 lane：8-bit 切片本身即 8-bit 有符号数，直接 asSInt；
  *   · INT4 lane：Cat(Fill(4, signBit), nibble) = sext(4->8)。与 lane0/lane1 分支里
  *     Cat(Fill(5, a(3,3)), a(2,0)) 的写法【逐位等价】：前者 = 4 份符号位 + 完整 4-bit
  *     nibble，后者 = 5 份符号位 + nibble 低 3 位，两者都是 8-bit 符号扩展结果。
  *     统一后四路乘法器形态同构（8x8->16），便于 firtool 结构去重与面积统计。
  *
  * 【累加与截位】四路乘积 + cReg 一次相加（k 升序左结合），结果按 cEffW 显式截位。
  *   整数族溢出维持【截断绕回】不加钳位（用户裁定），故取低 cEffW 位即完整语义。
  *
  * 【无 io.rnd 端口】整数乘加与舍入模式无关，端口集缩减为 a/b/cReg/aFmt/bFmt/sum（6 个）。
  *   宿主 WbWorkUnit 负责把 io.rnd 只路由给 WbFusionMacFp（FP/MX 路径）。
  *
  * @param aW            激活操作数位宽（SIMD lane 打包要求 >= 16）
  * @param bW            权重操作数位宽（SIMD lane 打包要求 >= 16）
  * @param cW            累加器位宽（默认 40，与上层 WbWorkUnit/WbFusionMacFp 的 cEffW 对齐；
  *                      cEffW = max(cW, bW)）
  * @param supportedFmts 绝对格式集（写子集即声明该子集为唯一合法输入格式，对齐
  *                      WorkerBeeConfig.supportedFmts 语义）
  */
class WbFusionMacInt(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 40,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4)
) extends Module {

  val cEffW: Int = math.max(cW, bW)

  // --- 编译期谓词（命名对齐 WorkerBeeConfig.hasInt / WbFusionMacFp.hasMx8 风格）---
  private val hasInt8 = supportedFmts.contains(DataFormat.INT8)
  private val hasInt4 = supportedFmts.contains(DataFormat.INT4)

  // --- 护栏（编译期 Scala 断言，不进 RTL；消息纯 ASCII —— firtool 硬约束）---
  require(hasInt8 || hasInt4, "WbFusionMacInt: at least one INT format required")
  require(aW >= 16 && bW >= 16, "INT SIMD lane packing requires 16-bit operands")
  require(cEffW >= 16, "INT accumulator width insufficient")

  val io = IO(new Bundle {
    val a    = Input(UInt(aW.W))
    val b    = Input(UInt(bW.W))
    val cReg = Input(UInt(cEffW.W))
    val aFmt = Input(DataFormat())
    val bFmt = Input(DataFormat())
    val sum  = Output(UInt(cEffW.W))
  })

  // 【RegNext 安全性】aFmt/bFmt are statically configured before EXECUTE phase;
  //   RegNext does not introduce fmt-data misalignment within a single EXECUTE window
  //   (fmt 在整个 EXECUTE 窗口内恒定，寄存一拍后在首个 compute 拍之前早已 settle，
  //    稳态下 fmt(N-1) == fmt(N)）。与 WbFusionMacFp 同风格，用户裁定保留。
  val aIsInt8 = RegNext(io.aFmt === DataFormat.INT8)
  val aIsInt4 = RegNext(io.aFmt === DataFormat.INT4)
  val bIsInt8 = RegNext(io.bFmt === DataFormat.INT8)
  val bIsInt4 = RegNext(io.bFmt === DataFormat.INT4)

  // --- lane0：INT8 取 a(7,0)；INT4 取 a(3,0) 并 sext(4->8) ---
  val mul8_0_a = Mux(aIsInt8, io.a(7,0), Cat(Fill(5, io.a(3,3)),io.a(2,0))).asSInt
  val mul8_0_b = Mux(bIsInt8, io.b(7,0), Cat(Fill(5, io.b(3,3)),io.b(2,0))).asSInt
  val r0 = mul8_0_a * mul8_0_b

  // --- lane1：INT8 取 a(15,8)；INT4 取 a(7,4) 并 sext(4->8) ---
  val mul8_1_a = Mux(aIsInt8, io.a(15,8), Cat(Fill(5,io.a(7,7)),io.a(6,4))).asSInt
  val mul8_1_b = Mux(bIsInt8, io.b(15,8), Cat(Fill(5,io.b(7,7)),io.b(6,4))).asSInt
  val r1 = mul8_1_a * mul8_1_b

  // --- lane2：INT4 专用（a(11,8) sext(4->8) 后 8x8 乘，与 lane0/lane1 同形）---
  val mul4_0_a = Cat(Fill(4, io.a(11)), io.a(11,8)).asSInt
  val mul4_0_b = Cat(Fill(4, io.b(11)), io.b(11,8)).asSInt
  val r2 = Mux(aIsInt4 & bIsInt4, mul4_0_a * mul4_0_b, 0.S(16.W))

  // --- lane3：INT4 专用（a(15,12) sext(4->8) 后 8x8 乘，与 lane0/lane1 同形）---
  val mul4_1_a = Cat(Fill(4, io.a(15)), io.a(15,12)).asSInt
  val mul4_1_b = Cat(Fill(4, io.b(15)), io.b(15,12)).asSInt
  val r3 = Mux(aIsInt4 & bIsInt4, mul4_1_a * mul4_1_b, 0.S(16.W))

  // --- 四路乘积 + cReg 一次吸收，显式截位到 cEffW（溢出绕回，不钳位）---
  val sumFull = r0 + r1 + r2 + r3 + io.cReg.asSInt
  io.sum := sumFull.asUInt(cEffW - 1, 0)

}
