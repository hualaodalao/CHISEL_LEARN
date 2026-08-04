/** HiveCell（巢室）- N×N HiveWorker 脉动阵列（无内部 skew）
  *
  * @param n     阵列维度（默认 8）
  * @param aW    激活位宽（默认 16）
  * @param bW    权重位宽（默认 16）
  * @param cW    累加器位宽（默认 0=自动推导）
  *
  * 新架构要点：
  *   - loadHIn / validIn / fmtIn / rndIn 为标量输入，直接广播到首列 PE（无 ShiftRegister）
  *   - HiveComb 已完成全局 skew，HiveCell 内部不再做行级 skew
  *   - loadH/valid/fmt/rnd 沿水平 PE 链（y 方向）传播（RegNext），自然产生列级延迟
  *   - loadV 沿垂直 PE 链（x 方向，随 psum 链）传播，与垂直权重波同步下沉
  *   - loadH/fmt/rnd 输出为标量（所有行同值）
  *   - 权重仅经 loadV（psumIn）加载；loadH 只锁存配置，不再经 a 链传权重
  *
  * 列连接块（y==0 注入点）：
  *   loadH/valid/fmt/rnd 控制信号直接广播（无 ShiftRegister 链）
  *   aIn 保持 per-row
  *
  * 行连接块（x==0 注入点）：
  *   psumIn 保持 per-column；loadV 广播到所有列首行 PE
  *
  * 使用时序：
  *   权重加载：同时拉高 loadHIn（配置）+ loadVIn（权重），权重经 psumIn 垂直下沉；
  *             aIn 无需供权重（loadH 不再从 aIn 锁存 wReg）
  *   释放后保持 psumIn=权重直到 loadV 沿垂直链排空（约 n 拍），再置 0 并 clear，
  *   否则尾部仍高的 loadV 会把 0 锁进 wReg
  */

import chisel3._
import chisel3.util._

class HiveCell(
  n: Int = 8,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 0,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {

  // --- 有效位宽派生 ---
  val aEffW: Int = math.max(aW, bW)
  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
  private val autoAccW = aEffW + bW + log2Up(n)
  val accW: Int = if (cW != 0) math.max(cW, bW)
                  else if (hasFp) math.max(32, autoAccW)
                  else autoAccW
  val cEffW: Int = accW

  // --- elaboration-time require ---
  if (hasFp)  require(cEffW >= 32,         s"含浮点格式时 cEffW($cEffW) 必须 >= 32")
  if (hasInt) require(cEffW >= aEffW + bW, s"含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")
  require(cEffW >= bW, s"cEffW($cEffW) 必须 >= bW($bW)")

  val io = IO(new Bundle {
    val aIn    = Input(Vec(n, UInt(aEffW.W)))
    val aOut   = Output(Vec(n, UInt(aEffW.W)))
    val psumIn = Input(Vec(n, UInt(cEffW.W)))
    val cOut   = Output(Vec(n, UInt(cEffW.W)))

    // 输入控制（标量）
    val loadHIn = Input(Bool())
    val loadVIn = Input(Bool())
    val validIn = Input(Bool())
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())
    val clear   = Input(Bool())

    // 输出控制
    val validOut = Output(Vec(n, Bool()))  // per-row（计算阶段结果有效）
    val fmtOut   = Output(DataFormat())    // 标量
    val rndOut   = Output(RoundingMode())  // 标量
    val loadHOut = Output(Bool())          // 标量
    val loadVOut = Output(Bool())          // 标量
  })

  // --- HiveWorker 网格 ---
  val pes = Seq.fill(n, n)(Module(new HiveWorker(aW, bW, cEffW, supportedFmts)))

  // --- 列连接块（水平传播）：控制信号直接广播，无 ShiftRegister ---
  for (x <- 0 until n) {
    for (y <- 0 until n) {
      if (y == 0) {
        // 控制信号：直接广播（无 ShiftRegister）
        pes(x)(y).io.loadHIn := io.loadHIn
        // valid 沿 x+y 二维传播：x==0 行首接 io.validIn，x>0 行首从上一 x 行
        // 的 y 链末端级联，使 PE(x,y) 的 valid 相对 io.validIn 延迟 (x+y) 拍，
        // 与数据波前的行内偏移 x 严格对齐（若只在 y 方向传播并广播所有 x 行，
        // cj>0 簇的 valid 会滞后数据 x 拍，前几个深度累加被错误门控）
        if (x == 0) {
          pes(x)(y).io.validIn := io.validIn
        } else {
          pes(x)(y).io.validIn := pes(x - 1)(n - 1).io.validOut
        }
        pes(x)(y).io.fmtIn   := io.fmtIn
        pes(x)(y).io.rndIn   := io.rndIn
        pes(x)(y).io.aIn     := io.aIn(x)  // per-row 数据
        pes(x)(y).io.clear   := io.clear
      } else {
        // 传播：从左侧 PE 获取（RegNext 链）
        pes(x)(y).io.loadHIn := pes(x)(y-1).io.loadHOut
        pes(x)(y).io.validIn := pes(x)(y-1).io.validOut
        pes(x)(y).io.fmtIn   := pes(x)(y-1).io.fmtOut
        pes(x)(y).io.rndIn   := pes(x)(y-1).io.rndOut
        pes(x)(y).io.aIn     := pes(x)(y-1).io.aOut
        pes(x)(y).io.clear   := io.clear    // 广播
      }

      if (y == n-1) {
        io.validOut(x) := pes(x)(y).io.validOut  // per-row
        io.aOut(x)     := pes(x)(y).io.aOut      // per-row
      }
    }
  }

  // 标量输出：取最后一行最后一列（所有行同值，任取一行即可）
  io.fmtOut   := pes(n-1)(n-1).io.fmtOut
  io.rndOut   := pes(n-1)(n-1).io.rndOut
  io.loadHOut := pes(n-1)(n-1).io.loadHOut
  // loadVOut：loadV 已改为 PE 级广播，此处仅为保持接口兼容（透传 RegNext）
  io.loadVOut := pes(n-1)(0).io.loadVOut

  // --- 行连接块（垂直传播）：仅 psumIn/psumOut 沿 x 方向传播 ---
  for (y <- 0 until n) {
    for (x <- 0 until n) {
      if (x == 0) {
        pes(x)(y).io.psumIn := io.psumIn(y)
        pes(x)(y).io.loadVIn := io.loadVIn
      } else {
        pes(x)(y).io.psumIn := pes(x-1)(y).io.psumOut
        pes(x)(y).io.loadVIn := pes(x-1)(y).io.loadVOut
      }
      if (x == n-1) {
        io.cOut(y) := pes(x)(y).io.psumOut
      }
    }
  }
}
