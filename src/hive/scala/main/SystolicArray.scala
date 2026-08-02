/** N×N Weight-Stationary 脉动阵列（裸 Tile，无 skew 逻辑）
  *
  * @param n     阵列维度（默认 8）
  * @param aW    激活位宽（默认 16）
  * @param bW    权重位宽（默认 16）
  * @param cW    累加器位宽（默认 0=自动推导）
  *
  * SystolicArray 为"裸 Tile"：仅负责 PE 网格连线，不包含任何 skew 逻辑。
  * 全局 skew 由上层 SystolicCluster 负责。
  *
  * loadIn / weightLoadMode 为每行独立输入，从左侧注入后经 PE 的 RegNext
  * 链式向右传播（水平脉动）。PE(i)(0) 直接接收 io.loadIn(i)，
  * PE(i)(j) 从 PE(i)(j-1).loadOut 获取（延迟 j 拍）。
  *
  * 权重加载（由 PE 内部根据 weightLoadMode 决定 latch 源）：
  *   Horizontal：loadIn 期间 aIn 作为权重源，经 aOut 列移位链下传
  *   Vertical  ：loadIn 期间 psumIn 携带权重，经 psumOut→psumIn 垂直下移
  *
  * 使用时序：
  *   阶段 A：设置 weightLoadMode，拉高 loadIn n 拍（预填充水平脉动链）
  *   阶段 B：按列注入权重 n 拍
  *   阶段 C：拉低 loadIn，拉高 validIn，喂入激活
  */

import chisel3._
import chisel3.util._

class SystolicArray(
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
    val loadIn = Input(Vec(n, Bool()))              // 每行一个 loadIn（从左侧注入）
    val validIn = Input(Vec(n, Bool()))              // 每行一个 validIn（从顶部注入）
    val fmtIn   = Input(Vec(n, DataFormat()))        // 每行一个 fmtIn
    val rndIn   = Input(Vec(n, RoundingMode()))      // 每行一个 rndIn
    val weightLoadMode = Input(Vec(n, WeightLoadMode()))  // 每行一个 loadMode
    val clear   = Input(Bool())  // psumReg 清零信号
    val validOut = Output(Vec(n, Bool()))
    val fmtOut   = Output(Vec(n, DataFormat()))
    val rndOut   = Output(Vec(n, RoundingMode()))
    val loadOut  = Output(Vec(n, Bool()))
  })

  // --- PE 网格 ---
  val pes = Seq.fill(n, n)(Module(new PE(aW, bW, cEffW, supportedFmts)))

  // --- 列连接块（水平传播）：loadIn/loadMode/validIn/fmtIn/rndIn/aIn 沿 y 方向传播 ---
  for (x <- 0 until n){
    for(y <- 0 until n){
        if(y == 0){
          pes(x)(y).io.loadIn := io.loadIn(x)
          pes(x)(y).io.weightLoadMode := io.weightLoadMode(x)
          // RegNext 对齐：与 RegNext(aIn) 同步，使 validIn 延迟 1 拍
          pes(x)(y).io.validIn := RegNext(io.validIn(x), false.B)
          pes(x)(y).io.fmtIn   := RegNext(io.fmtIn(x), DataFormat.INT8)
          pes(x)(y).io.rndIn   := RegNext(io.rndIn(x), RoundingMode.RNE)
          pes(x)(y).io.aIn := io.aIn(x)
          pes(x)(y).io.clear := io.clear
        }
        else{
          pes(x)(y).io.loadIn := pes(x)(y-1).io.loadOut
          pes(x)(y).io.weightLoadMode := pes(x)(y-1).io.weightLoadModeOut
          pes(x)(y).io.validIn := pes(x)(y-1).io.validOut
          pes(x)(y).io.fmtIn  := pes(x)(y-1).io.fmtOut
          pes(x)(y).io.rndIn  := pes(x)(y-1).io.rndOut
          pes(x)(y).io.aIn := pes(x)(y-1).io.aOut
          pes(x)(y).io.clear := io.clear
        }
        if(y == n-1){
          io.validOut(x) := pes(x)(y).io.validOut
          io.fmtOut(x)   := pes(x)(y).io.fmtOut
          io.rndOut(x)   := pes(x)(y).io.rndOut
          io.loadOut(x)  := pes(x)(y).io.loadOut
          io.aOut(x)  := pes(x)(y).io.aOut
        }
    }
  }

  // --- 行连接块（垂直传播）：仅 psumIn/psumOut 沿 x 方向传播 ---
  for( y <- 0 until n)    {
    for(x <- 0 until n){
      if(x == 0){
        pes(x)(y).io.psumIn := io.psumIn(y)
      }
      else{
        pes(x)(y).io.psumIn := pes(x-1)(y).io.psumOut
      }
      if(x == n-1)  {
        io.cOut(y)     := pes(x)(y).io.psumOut
      }
    }
  }
  
 
}
