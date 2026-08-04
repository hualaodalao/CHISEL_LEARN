/** HiveComb（巢脾）- MxM 个 NxN HiveCell 组成更大集群
  *                  （权重复用通道加载，无独立 wIn/wOut）
  *
  * @param arrayN   每个子阵列维度（默认 8）
  * @param clusterM 集群中子阵列的 M×M 排列（默认 2，即 2×2 = 16×16 等效）
  * @param aW       激活位宽（默认 16）
  * @param bW       权重位宽（默认 16）
  * @param cW       累加器位宽（默认 0=自动推导，totalN = arrayN * clusterM）
  *
  * 新架构要点：
  *   - loadHIn / fmtIn / rndIn 从首列子阵列进入，经 HiveCell 的 loadHOut/fmtOut/rndOut
  *     逐列（cj 方向）向右传播，与水平权重波（1 PE/拍）同步推进
  *   - loadVIn 从首行子阵列进入，经 HiveCell 的 loadVOut 逐行（ci 方向）向下传播，
  *     与垂直权重波下沉节奏（arrayN 拍/子阵列）同步
  *   - validIn 需要 array-row 级 skew（ci * arrayN）以对齐行级错峰数据
  *   - fmtIn / rndIn 为配置信号，仅在 loadH 窗口锁存进 PE 配置寄存器，ci 方向无 skew
  *   - aIn 不做内部 skew：HiveCell 与 HiveComb 都直连，错峰供数由外部 feeder 承担
  *
  * aIn skew 契约（调用方责任）：
  *   计算阶段外部必须按「行 i 延迟 i 拍」错峰供数，即时刻 t 在 io.aIn(i) 上
  *   给出逻辑第 (t - i) 拍的激活（t < i 时给 0）。典型实现：scratchpad 每行
  *   一个读地址计数器，行 i 的计数器在 start 后第 i 拍启动。
  *   加载阶段（loadHIn/loadVIn）无需 skew，各行同拍给出权重即可。
  *   注：该 skew 原先由 totalN 条 ShiftRegister 链在本模块内完成
  *   （aEffW × Σi 个触发器，totalN=16/aEffW=16 时为 1920 个），
  *   移出后本模块仅保留 validIn 的 clusterM 级 skew。
  *
  * 加载时序要求（totalN = arrayN * clusterM）：
  *   - 权重加载：同时拉高 loadHIn（配置）+ loadVIn（权重），psumIn 供权重。
  *     加载使能脉冲需覆盖全链传播（>= 3*totalN + arrayN），保证 loadV 波尾
  *     到达并停留在最后一个 PE。aIn 无需供权重（loadH 不再从 aIn 锁存 wReg）
  *   - 释放 loadV 后需保持 psumIn=权重直到 loadV 沿 ci/x 链完全排空
  *     （>= totalN 拍），再把 psumIn 置 0 并 clear；否则尾部仍高的 loadV
  *     会把 0 锁进深层 PE 的 wReg
  *
  * skew 策略：
  *   - aIn：本模块不做 skew（见上方 aIn skew 契约），加载/计算阶段统一直连
  *   - validIn：保留 ci * arrayN 的行块级 skew，跟踪行级错峰数据
  *   - 垂直权重加载模式（loadVIn）：首行 psumIn 不 skew
  *
  * 横向级联：左方 tile aOut → 右方 tile aIn。
  * psumIn 纵向级联：上方 cOut → 下方 psumIn；顶行接 0 或 io.psumIn。
  * cOut/validOut：底部行 tile 输出。
  */

import chisel3._
import chisel3.util._

class HiveComb(
  arrayN: Int = 8,
  clusterM: Int = 2,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 0,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val totalN = arrayN * clusterM

  // --- 有效位宽派生（用 totalN） ---
  val aEffW: Int = math.max(aW, bW)
  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
  private val autoAccW = aEffW + bW + log2Up(totalN)
  val accW: Int = if (cW != 0) math.max(cW, bW)
                  else if (hasFp) math.max(32, autoAccW)
                  else autoAccW
  val cEffW: Int = accW

  // --- elaboration-time require ---
  if (hasFp)  require(cEffW >= 32,         s"含浮点格式时 cEffW($cEffW) 必须 >= 32")
  if (hasInt) require(cEffW >= aEffW + bW, s"含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    val aIn    = Input(Vec(totalN, UInt(aEffW.W)))
    val aOut   = Output(Vec(totalN, UInt(aEffW.W)))
    val psumIn = Input(Vec(totalN, UInt(cEffW.W)))
    val cOut   = Output(Vec(totalN, UInt(cEffW.W)))

    val loadHIn = Input(Bool())   // 水平加载（标量）
    val loadVIn = Input(Bool())   // 垂直加载（标量）
    val validIn = Input(Bool())   // 标量 validIn
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())

    val clear   = Input(Bool())

    val validOut = Output(Vec(totalN, Bool()))
  })

  // 子阵列
  val arrays = Seq.fill(clusterM, clusterM)(
    Module(new HiveCell(arrayN, aW, bW, cEffW, supportedFmts))
  )

  // --- 全局 skew：每个子阵列行 (ci) 的 valid 延迟（跟踪行级错峰数据） ---
  // 外部供数已按「行 i 延迟 i 拍」错峰，数据波前到达全局 PE(x,y) 的时刻为
  // r+1+x+y（含行内偏移 x）。valid 在 HiveCell 内沿 x（逐行 RegNext）+ y
  // （逐列 RegNext）二维传播，起始 skew 为 ci*arrayN，使 PE(x,y) 的 valid
  // 窗口与数据波前严格对齐。若只在 y 方向传播（广播所有 x 行），cj>0 簇的
  // valid 将滞后数据 (x_local) 拍，前几个深度的累加被错误门控
  // fmt/rnd 仅在 loadH 窗口锁存进 PE 配置寄存器，无需跟踪数据时序，直接广播
  val validSkewedPerRow = (0 until clusterM).map { ci =>
    val rowDelay = ci * arrayN
    if (rowDelay == 0) io.validIn else ShiftRegister(io.validIn, rowDelay)
  }

  // 连接子阵列
  for (ci <- 0 until clusterM; cj <- 0 until clusterM) {
    val arr = arrays(ci)(cj)

    // --- clear：广播（无 skew） ---
    arr.io.clear := io.clear

    // --- loadHIn/fmtIn/rndIn：首列进入，逐列向右传播 ---
    if (cj == 0) {
      arr.io.loadHIn := io.loadHIn
      arr.io.fmtIn   := io.fmtIn
      arr.io.rndIn   := io.rndIn
    } else {
      arr.io.loadHIn := arrays(ci)(cj - 1).io.loadHOut
      arr.io.fmtIn   := arrays(ci)(cj - 1).io.fmtOut
      arr.io.rndIn   := arrays(ci)(cj - 1).io.rndOut
    }


    // --- validIn：ci 方向行级 skew；cj 方向随数据链级联 ---
    // aIn 经左侧子阵列 aOut 链延迟 arrayN 拍，valid 必须同步级联，
    // 否则 cj>0 子阵列的 valid 窗口与数据错位
    if (cj == 0) {
      arr.io.validIn := validSkewedPerRow(ci)
    } else {
      arr.io.validIn := arrays(ci)(cj - 1).io.validOut(0)
    }

    // --- 激活数据 aIn：per-row，直连（skew 由外部 feeder 负责） ---
    for (r <- 0 until arrayN) {
      val globalRow = ci * arrayN + r
      if (cj == 0) {
        // 首列：直连顶层输入。计算阶段要求外部已按「行 i 延迟 i 拍」错峰供数；
        // 加载阶段本就不需要 skew，两种阶段统一为直连
        arr.io.aIn(r) := io.aIn(globalRow)
      } else {
        // 非首列：从左侧子阵列获取
        arr.io.aIn(r) := arrays(ci)(cj-1).io.aOut(r)
      }
    }

    // --- psumIn：per-column，按加载模式动态禁用 skew ---
    for (c <- 0 until arrayN) {
      val globalCol = cj * arrayN + c
      if (ci == 0) {
      
        // 首行：垂直权重加载时直接传 io.psumIn（无 skew），
        // 计算阶段接 0
        arr.io.psumIn(c) := Mux(io.loadVIn,
                                io.psumIn(globalCol),  // 加载模式：无 skew
                                0.U(cEffW.W))          // 计算阶段：psumIn 为 0
      } else {
        // 非首行：从上方子阵列获取
        arr.io.psumIn(c) := arrays(ci-1)(cj).io.cOut(c)
        arr.io.loadVIn   := arrays(ci-1)(cj).io.loadVOut
      }
    }

    if (ci == 0){
      arr.io.loadVIn := io.loadVIn
    }    
    else{
      arr.io.loadVIn := arrays(ci-1)(cj).io.loadVOut
    }
    
  }

  // --- 集群输出 ---
  // cOut/validOut 从底部行簇取值（psum 从上到下流动，结果在底部行）
  for (cj <- 0 until clusterM; c <- 0 until arrayN) {
    val globalCol = cj * arrayN + c
    io.cOut(globalCol)     := arrays(clusterM - 1)(cj).io.cOut(c)
    io.validOut(globalCol) := arrays(clusterM - 1)(cj).io.validOut(c)
  }

  // aOut：右侧 tile 列的子阵列输出
  for (ci <- 0 until clusterM; r <- 0 until arrayN) {
    io.aOut(ci * arrayN + r) := arrays(ci)(clusterM - 1).io.aOut(r)
  }
}
