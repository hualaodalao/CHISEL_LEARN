/** WbWorkUnit（工作单元）—— 融合 MAC 路由器（Phase 2 重写版）
  *
  * 【结构】本文件只剩一个类：WbWorkUnit。内部例化两个融合 MAC
  *   ├── WbFusionMacFp ：FP16/BF16 + MXFP8(E4M3/E5M2) + MXFP4(E2M1) 路径
  *   │                     （cvt / 窄乘法器 / lazy-40 五槽累加器全部内聚在其内部）
  *   └── WbFusionMacInt：INT8 双 lane / INT4 四 lane SIMD 整数路径
  * 顶层职责退化为「端口直通 + 按 io.aFmt 的运行期输出路由」，纯组合、无自有算术。
  *
  * 【已删除的旧离散子模块（Phase 2）】WbCvtOpInt / WbCvtOpFp / WbCvtOpMx /
  *   WbMulInt / WbMulFp / WbAddInt / WbAddFp / WbMacInt / WbMacFp /
  *   WbMacLazyDownstream / WbMacFpMx —— 共 11 个 class/object。它们的功能已分别
  *   被 FpCvt.scala / FpMul.scala / FpAdd.scala（→ WbFusionMacFp 消费）与
  *   WbFusionMacInt 内部封装取代，此处不再保留任何副本（单一真源，杜绝双源漂移）。
  *   ★ WbMacFpLazy 是【独立文件 WbMacFpLazy.scala】，用户裁定禁删，与本文件无关。
  *
  * 【端口签名与旧版逐字一致】a / b / cReg / aFmt / bFmt / scaleA / scaleB / rnd /
  *   consumeValid / sum —— 共 10 个端口、名称/方向/位宽全同 ⟹ 宿主 WbWorker.scala
  *   的 10 行连线【零改动】（workUnit.io.* 逐行照旧）。构造参数顺序亦不变：
  *   (aW, bW, cW, supportedFmts)。
  *
  * 【supportedFmts 是绝对格式集】写子集即删硬件（对齐 WorkerBeeConfig.supportedFmts 语义）：
  *   · 含 MX 格式 ⟹ 例化 WbFusionMacFp（只把 FP/MX 子集透传给它——该模块的 require
  *     以「MX 为必选硬件路径」为前提，INT 格式必须被过滤掉，否则 elaboration 期即拒）；
  *   · 含 INT8/INT4 ⟹ 例化 WbFusionMacInt（只透传 INT 子集）；
  *   · 两者皆无 ⟹ 编译期 require 拦死（不做静默 passthrough）。
  *
  * @param aW            激活位宽（默认 16；SIMD lane 打包要求 >= 16）
  * @param bW            权重位宽（默认 16；SIMD lane 打包要求 >= 16）
  * @param cW            累加器/部分和位宽（默认 48，cEffW = max(cW, bW)）
  * @param supportedFmts 本工作单元支持的数据格式集合（默认 = WorkerBeeConfig 默认集：
  *                      INT8/INT4/MXE4M3/MXE5M2/MXE2M1）
  */

import chisel3._
import chisel3.util._

/** WbWorkUnit（工作单元顶层）
  * 融合 MAC 路由器：例化 WbFusionMacFp（FP/MX 路径）与 WbFusionMacInt（INT 路径），
  * 按 io.aFmt 在运行期选择输出。所有旧的离散子模块（WbCvtOp* / WbMul* / WbAdd* /
  * WbMac*）已被融合模块内部封装，本文件仅保留顶层路由逻辑。纯组合（无自有寄存器）。
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

  // --- 编译期谓词 ---
  // 注意：DataFormat.isMx / isInt 是【硬件版】（返回 Bool，供运行期判据用），编译期
  //   Scala 谓词必须用 `==` 逐值比较（命名与 WorkerBeeConfig.hasInt/hasMx/hasFp 对齐）。
  private val hasInt = supportedFmts.exists(f => f == DataFormat.INT8 || f == DataFormat.INT4)
  private val hasMx  = supportedFmts.exists(f => f == DataFormat.MXE4M3 || f == DataFormat.MXE5M2 || f == DataFormat.MXE2M1)
  private val hasFp  = supportedFmts.exists(f => f == DataFormat.FP16 || f == DataFormat.BF16)

  require(hasInt || hasFp || hasMx,
    "WbWorkUnit: at least one compute format required in supportedFmts")
  require(!hasMx || cEffW >= 40,
    "MX/FP lazy-40 accumulator path requires cEffW >= 40")
  require(aW >= 16 && bW >= 16,
    "SIMD lane packing requires 16-bit operand width")
  // WbFusionMacFp 把 MX 当【必选硬件路径】（其 require(hasMx8 || hasMx4)），FP16/BF16
  //   只是它内部的 lane0 宽阵列分支 ⟹ 无 MX 的 FP-only 配置无法由融合模块承载。
  //   此处提前拦死，给出本层可读的错因，而不是让下游 require 抛一句费解的 MX 缺格式。
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

  // =========================================================================
  // FP/MX 路径 —— WbFusionMacFp
  // =========================================================================
  val sumFpMx: UInt = if (hasMx || hasFp) {
    // 只把 FP/MX 子集透传给 WbFusionMacFp：它的 require 以「MX 必选 + 白名单」为判据，
    //   含 INT 元素的集合会在 elaboration 期被拒（且 INT 由本层另一条路径承载）。
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

  // =========================================================================
  // INT 路径 —— WbFusionMacInt（无 rnd/scale/consumeValid 端口）
  // =========================================================================
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

  // =========================================================================
  // 输出路由（组合 mux；fmt 在 EXECUTE 窗口内静态配置 ⟹ 无 fmt/data 错拍）
  // =========================================================================
  // 优先级：MX > INT > passthrough(cReg)。
  //   · DataFormat.isMx / isInt 是硬件判据（Bool），此处直接消费运行期 io.aFmt；
  //     isInt 的字面定义含已弃用的 INT16 编码，WorkerBeeConfig 默认集不再产出该编码，
  //     故运行期不可达（保留以对齐 WbEnum 单一真源，勿在本处另立裸判据）。
  //   · fallback = io.cReg：与旧版 baseSel 的 (false,false) => io.cReg 穷尽兜底逐字同义。
  //   · 编译期 if 只决定【是否生成选择子】，不门控运行期：路径不存在时直接短接，
  //     不留恒定臂的 dead mux（对齐项目铁律「编译期 if 不门控运行期」）。
  private val selInt: UInt =
    if (hasInt) Mux(DataFormat.isInt(io.aFmt), sumInt, io.cReg) else io.cReg
  io.sum := (if (hasMx) Mux(DataFormat.isMx(io.aFmt), sumFpMx, selInt) else selInt)
}
