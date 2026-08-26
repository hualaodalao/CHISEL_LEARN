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
    val clear   = Input(Bool())
    
    val validIn  = Input(Vec(n, Bool()))
    val validInV = Input(Vec(n, Bool()))
    val fmtIn   = Input(DataFormat())    
    val rndIn   = Input(RoundingMode())
    // 权重格式（标量，镜像 fmtIn 逐列传播）
    val bFmtIn  = Input(DataFormat())
    // MX scale：scaleAIn per-row（随激活链 y 方向流动），scaleBIn per-column（列驻留）
    val scaleAIn = Input(Vec(n, UInt(8.W)))
    val scaleBIn = Input(Vec(n, UInt(8.W)))
    val loadHIn = Input(Bool())
    val loadVIn = Input(Bool())
    val loadVInLock = Input(Bool())
    // 水平 loadW 加载（可选模式）：loadWInLock 全 PE 广播（仿 loadVInLock）；
    // loadWIn per-row 沿 a 数据链方向（y）逐列链传播，与 aOut lockstep。
    // 垂直模式下恒 0，不影响既有行为
    val loadWIn = Input(Vec(n, Bool()))
    val loadWInLock = Input(Bool())

    // 输出控制
    val loadVOut = Output(Vec(n, Bool()))
    val loadWOut = Output(Vec(n, Bool()))  // per-row（每行 a 链末端）
    val validOut = Output(Vec(n, Bool()))  // per-row（计算阶段结果有效）
    val validOutV = Output(Vec(n, Bool()))
    val fmtOut   = Output(DataFormat())    // 标量
    val rndOut   = Output(RoundingMode())  // 标量
    val bFmtOut  = Output(DataFormat())    // 标量（镜像 fmtOut）
    val scaleAOut = Output(Vec(n, UInt(8.W)))  // per-row（a 链末端 scaleA）
  })

  // --- HiveWorker 网格 ---
  val pes = Seq.fill(n, n)(Module(new HiveWorker(aW, bW, cEffW, supportedFmts)))

  // --- loadW 端口 WireDefault 兜底：既有测试（SystolicArrayTest 等）直接
  // 例化本模块时不连接 loadW 口（invalid），兜底回落恒 0，保证垂直模式
  // bit-exact；顶层 HiveComb 正常驱动时兜底被覆盖 ---
  val loadWInSafe = WireDefault(VecInit(Seq.fill(n)(false.B)))
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  // --- 列连接块（水平传播）：控制信号直接广播，无 ShiftRegister ---
  for (x <- 0 until n) {
    for (y <- 0 until n) {
      pes(x)(y).io.loadHIn := io.loadHIn  
      pes(x)(y).io.clear   := io.clear      
      pes(x)(y).io.loadVInLock := io.loadVInLock
      // loadWInLock 全 PE 广播（仿 loadVInLock；对角线落位态只存在于末拍
      // beat 进入列 0 的绝对时刻，链传播锁存会使所有列锁到同一权重）
      pes(x)(y).io.loadWInLock := loadWInLockSafe

      // scaleB 列驻留：per-column（y）广播到该列所有行 PE（随 loadH 锁存进 scaleBReg）
      pes(x)(y).io.scaleBIn := io.scaleBIn(y)

      if (y == 0) {
        // valid 沿 x+y 二维传播：x==0 行首接 io.validIn，x>0 行首从上一 x 行
        // 行首 PE 的 validOut 级联（仅 +1 拍），使 PE(x,y) 的 valid 相对
        // io.validIn 延迟 (x+y) 拍，与数据波前的行内偏移 x+y 严格对齐。
        // （若取上一行 y 链末端 pes(x-1)(n-1)，延迟变为 x*n+y，valid 严重滞后
        // 数据，深层 PE 在 valid 到达时 a 数据早已排空，累加被清零 → 结果全 0；
        // 若只在 y 方向传播并广播所有 x 行，cj>0 簇的 valid 会滞后数据 x 拍）
        pes(x)(y).io.fmtIn   := io.fmtIn
        pes(x)(y).io.rndIn   := io.rndIn
        pes(x)(y).io.bFmtIn  := io.bFmtIn
        pes(x)(y).io.validIn := io.validIn(x)
        pes(x)(y).io.aIn     := io.aIn(x)  // per-row 数据
        // scaleA per-row 随激活链首列注入
        pes(x)(y).io.scaleAIn := io.scaleAIn(x)
        // loadW 脉冲链 per-row 注入（水平 loadW 模式，与 a 链同向 lockstep）
        pes(x)(y).io.loadWIn := loadWInSafe(x)
      } else {
        // 传播：从左侧 PE 获取（RegNext 链）

        pes(x)(y).io.fmtIn   := pes(x)(y-1).io.fmtOut
        pes(x)(y).io.rndIn   := pes(x)(y-1).io.rndOut
        pes(x)(y).io.bFmtIn  := pes(x)(y-1).io.bFmtOut
        pes(x)(y).io.validIn := pes(x)(y-1).io.validOut
        pes(x)(y).io.aIn     := pes(x)(y-1).io.aOut
        // scaleA 链沿 a 数据链方向（y）逐列传播，与 aOut lockstep
        pes(x)(y).io.scaleAIn := pes(x)(y-1).io.scaleAOut
        // loadW 链沿 a 数据链方向（y）逐列传播，与 aOut lockstep
        pes(x)(y).io.loadWIn := pes(x)(y-1).io.loadWOut
      }

      if (y == n-1) {
        io.validOut(x) := pes(x)(y).io.validOut  // per-row
        io.aOut(x)     := pes(x)(y).io.aOut      // per-row
        io.scaleAOut(x) := pes(x)(y).io.scaleAOut // per-row（a 链末端）
        io.loadWOut(x) := pes(x)(y).io.loadWOut  // per-row（a 链末端）
      }
    }
  }

  // 标量输出：取最后一行最后一列（所有行同值，任取一行即可）
  io.fmtOut   := pes(n-1)(n-1).io.fmtOut
  io.rndOut   := pes(n-1)(n-1).io.rndOut
  io.bFmtOut  := pes(n-1)(n-1).io.bFmtOut

  // --- 行连接块（垂直传播）：psumIn/psumOut 沿 x 方向传播；
  //     loadV 广播到所有 PE ---
  for (y <- 0 until n) {
    for (x <- 0 until n) {
      if (x == 0) {
        pes(x)(y).io.loadVIn := io.loadVIn
        pes(x)(y).io.validInV := io.validInV(y)
        pes(x)(y).io.psumIn := io.psumIn(y)
      } else {
        pes(x)(y).io.loadVIn := pes(x-1)(y).io.loadVOut
        pes(x)(y).io.validInV := pes(x-1)(y).io.validOutV
        pes(x)(y).io.psumIn := pes(x-1)(y).io.psumOut
      }
      if (x == n-1) {
        io.loadVOut(y) := pes(x)(y).io.loadVOut
        io.validOutV(y) := pes(x)(y).io.validOutV
        io.cOut(y) := pes(x)(y).io.psumOut
      }
    }
  }
}
