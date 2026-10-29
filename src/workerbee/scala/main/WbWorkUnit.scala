import chisel3._
import chisel3.util._

/** WbWorkUnit（工作单元顶层）—— 融合 MAC 路由器，纯组合、无自有算术/寄存器。
  *   ├── WbFusionMacFp ：FP16/BF16 + MXFP8(E4M3/E5M2) + MXFP4(E2M1)
  *   └── WbFusionMacInt：INT8 双 lane / INT4 四 lane SIMD
  * 职责为端口直通 + 按 io.aFmt 的运行期输出路由。
  *
  * supportedFmts 是绝对格式集，写子集即删硬件：含 MX 格式例化 WbFusionMacFp（只透传
  *   FP/MX 子集，INT 元素会被其 require 拒）；含 INT8/INT4 例化 WbFusionMacInt（只透传
  *   INT 子集）；两者皆无则编译期 require 拦死，不做静默 passthrough。
  *
  * @param aW            激活位宽（默认 16；SIMD lane 打包要求 >= 16）
  * @param bW            权重位宽（默认 16；SIMD lane 打包要求 >= 16）
  * @param cW            累加器/部分和位宽（默认 48，cEffW = max(cW, bW)）
  * @param supportedFmts 支持的数据格式集合（默认 INT8/INT4/MXE4M3/MXE5M2/MXE2M1）
  */
class WbWorkUnit(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type] = Set(
    DataFormat.INT8, DataFormat.INT4,
    DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1
  )
) extends Module {
  private val cEffW: Int = math.max(cW, bW)

  // DataFormat.isMx / isInt 是硬件版（返回 Bool，供运行期判据）；编译期谓词须用 `==` 比较。
  private val hasInt = supportedFmts.exists(f => f == DataFormat.INT8 || f == DataFormat.INT4)
  private val hasMx  = supportedFmts.exists(f => f == DataFormat.MXE4M3 || f == DataFormat.MXE5M2 || f == DataFormat.MXE2M1)
  // BF16 运行期既不满足 isMx 也不满足 isInt，输出路由须为它生成专属选择子。
  private val hasBf16 = supportedFmts.exists(f => f == DataFormat.BF16)
  private val hasFp  = supportedFmts.exists(f => f == DataFormat.FP16 || f == DataFormat.BF16)

  require(hasInt || hasFp || hasMx,
    "WbWorkUnit: at least one compute format required in supportedFmts")
  require(!hasMx || cEffW >= 40,
    "MX/FP lazy-40 accumulator path requires cEffW >= 40")
  require(aW >= 16 && bW >= 16,
    "SIMD lane packing requires 16-bit operand width")
  // WbFusionMacFp 以 MX 为必选硬件路径，FP16/BF16 只是其 lane0 宽阵列分支 ⟹ FP-only
  //   配置无法承载，此处提前拦死。
  require(!hasFp || hasMx,
    "WbWorkUnit: FP16/BF16 is served by WbFusionMacFp which mandates MX hardware; hasFp requires hasMx")

  val io = IO(new Bundle {
    val a            = Input(UInt(aW.W))
    val b            = Input(UInt(bW.W))
    val cReg         = Input(UInt(cEffW.W))
    val aFmt         = Input(DataFormat())
    val bFmt         = Input(DataFormat())
    val scaleA       = Input(UInt(8.W))
    val scaleB       = Input(UInt(8.W))
    val rnd          = Input(RoundingMode())
    // consumeValid: gates inf/nan assertion in MX cvt path (not data-path)
    val consumeValid = Input(Bool())
    val sum          = Output(UInt(cEffW.W))
  })

  // FP/MX 路径 —— WbFusionMacFp
  val sumFpMx: UInt = if (hasMx || hasFp) {
    val fpMxFmts = supportedFmts.filter(f =>
      f == DataFormat.MXE4M3 || f == DataFormat.MXE5M2 || f == DataFormat.MXE2M1 ||
      f == DataFormat.FP16     || f == DataFormat.BF16)
    val fusionFp = Module(new WbFusionMacFp(aW, bW, cEffW, fpMxFmts))
    fusionFp.io.a            := io.a
    fusionFp.io.b            := io.b
    fusionFp.io.cReg         := io.cReg
    fusionFp.io.aFmt         := io.aFmt
    fusionFp.io.bFmt         := io.bFmt
    fusionFp.io.scaleA       := io.scaleA
    fusionFp.io.scaleB       := io.scaleB
    fusionFp.io.rnd          := io.rnd
    fusionFp.io.consumeValid := io.consumeValid
    fusionFp.io.sum
  } else {
    0.U(cEffW.W)
  }

  // INT 路径 —— WbFusionMacInt（无 rnd/scale/consumeValid 端口）
  val sumInt: UInt = if (hasInt) {
    val intFmts = supportedFmts.filter(f => f == DataFormat.INT8 || f == DataFormat.INT4)
    val fusionInt = Module(new WbFusionMacInt(aW, bW, cEffW, intFmts))
    fusionInt.io.a    := io.a
    fusionInt.io.b    := io.b
    fusionInt.io.cReg := io.cReg
    fusionInt.io.aFmt := io.aFmt
    fusionInt.io.bFmt := io.bFmt
    fusionInt.io.sum
  } else {
    0.U(cEffW.W)
  }

  // 输出路由（组合 mux；fmt 在 EXECUTE 窗口内静态配置 ⟹ 无 fmt/data 错拍）
  //   优先级 MX > BF16 > INT > passthrough(cReg)，判据只看运行期 io.aFmt。
  //   BF16 与 MX 共用同一个 WbFusionMacFp 实例（lane0 第三分支），故数据源同为 sumFpMx；
  //     缺此槽时 BF16 乘积会被兜底臂 io.cReg 盖掉。FP16 无专属槽，落兜底臂。
  //   BF16×MX 混对无硬件语义，由 WbFusionMacFp 在消费沿 assert 拦死，本层不重复判。
  //   编译期 if 只决定是否生成选择子，不门控运行期：路径不存在时直接短接，无 dead mux。
  private val selInt: UInt =
    if (hasInt) Mux(DataFormat.isInt(io.aFmt), sumInt, io.cReg) else io.cReg
  private val selBf16: UInt =
    if (hasBf16) Mux(io.aFmt === DataFormat.BF16, sumFpMx, selInt) else selInt
  io.sum := (if (hasMx) Mux(DataFormat.isMx(io.aFmt), sumFpMx, selBf16) else selBf16)
}
