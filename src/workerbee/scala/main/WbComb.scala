/** WbComb（巢脾）- MxM 个 NxN WbCell 组成更大集群
  *                  （权重复用通道加载，无独立 wIn/wOut）
  *
  * @param arrayN   每个子阵列维度（默认 8）
  * @param clusterM 集群中子阵列的 M×M 排列（默认 2，即 2×2 = 16×16 等效）
  * @param aW       激活位宽（默认 16）
  * @param bW       权重位宽（默认 16）
  * @param cW       累加器位宽（默认 0=自动推导，totalN = arrayN * clusterM）
  *
  * loadHIn / loadVIn 广播到所有子阵列（无链式传播）；fmtIn / rndIn 从首列子阵列进入，经
  *   fmtOut/rndOut 逐列（cj）向右传播，只在 loadH 窗口锁存进 PE 配置寄存器、ci 方向无
  *   skew。横向级联左方 tile aOut → 右方 tile aIn；纵向级联上方 cOut → 下方 psumIn，
  *   顶行接 0 或 io.psumIn；cOut/validOut 取底部行 tile。
  *
  * aIn skew 契约（调用方责任）：计算阶段外部必须按「行 i 延迟 i 拍」错峰供数，即时刻 t
  *   在 io.aIn(i) 上给出逻辑第 (t - i) 拍的激活（t < i 时给 0）。加载阶段无需 skew。
  *
  * 加载时序要求（totalN = arrayN * clusterM）：权重加载需同时拉高 loadHIn + loadVIn，
  *   psumIn 供权重、aIn 无需供数，使能脉冲需覆盖全链传播（>= 3*totalN + arrayN）以保证
  *   loadV 波尾到达并停留在最后一个 PE；释放 loadV 后需保持 psumIn=权重直到 loadV 沿
  *   ci/x 链排空（>= totalN 拍）再置 0 并 clear，否则尾部仍高的 loadV 会把 0 锁进深层
  *   PE 的 wReg。
  *
  * skew 策略：aIn 不做 skew，加载/计算阶段统一直连；validIn 保留 ci * arrayN 的行块级
  *   skew 以跟踪行级错峰数据；psumIn 首行（ci=0）无条件直连，加载期传权重，计算期由调用
  *   方保证非回灌窗口驱动 0、回灌窗口（K > totalN 多 pass 非首 pass）提供按「行 i 延迟 i
  *   拍」skew 的 partial sum 累加基底（如 Executor cpsRegs）。
  */

import chisel3._
import chisel3.util._
import chisel3.dontTouch

class WbComb(
  arrayN: Int = 8,
  clusterM: Int = 2,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 0,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val totalN = arrayN * clusterM

  val aEffW: Int = math.max(aW, bW)
  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
  private val autoAccW = aEffW + bW + log2Up(totalN)
  val accW: Int = if (cW != 0) math.max(cW, bW)
                  else if (hasFp) math.max(32, autoAccW)
                  else autoAccW
  val cEffW: Int = accW

  // 含 FP 族直接例化须显式 cW>=40，否则构造期失败；顶层 WorkerBee 传 cfg.cEffW。
  if (hasFp)  require(cEffW >= 40,         s"含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aEffW + bW, s"含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    val aIn    = Input(Vec(totalN, UInt(aEffW.W)))
    val psumIn = Input(Vec(totalN, UInt(cEffW.W)))
    val cOut   = Output(Vec(totalN, UInt(cEffW.W)))

    val loadHIn = Input(Bool())   // 水平加载（标量）
    val loadVInLock = Input(Bool())   // 垂直加载（标量）
    val loadVIn = Input(Bool())   // 垂直加载（标量）
    // 水平 loadW 加载（与垂直加载靠信号活跃性二选一）：loadWInLock 广播全簇；
    // loadWIn 沿 cj 方向 per-row 级联（与 a 链 lockstep）。垂直模式下恒 0。
    val loadWIn = Input(Bool())
    val loadWInLock = Input(Bool())
    val validIn = Input(Bool())   // 标量 validIn
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())
    val bFmtIn  = Input(DataFormat())
    // MX scale: scaleAIn and scaleBIn are both per-row, indexed by the PE-grid row
    // coordinate, which in this array is the K-lane axis; neither is per-column.
    // scaleAIn rides the activation skew chain (scaleASkewedPerRow), staying lockstep
    // with aIn during compute. scaleBIn comes straight from the top-level port with no
    // skew chain: it is only consumed on the loadWLock beat, and during the whole loadW
    // window scaleASkewedPerRow is bypassed to a direct connection too, so on that
    // phase both sides have the same shape and the same per-column cascade depth.
    val scaleAIn = Input(Vec(totalN, UInt(8.W)))
    val scaleBIn = Input(Vec(totalN, UInt(8.W)))

    val clear   = Input(Bool())

    val validOut = Output(Bool())
  })


  val arrays = Seq.fill(clusterM, clusterM)(
    Module(new WbCell(arrayN, aW, bW, cEffW, supportedFmts))
  )

  // loadW 端口 WireDefault 兜底：直接例化本模块而不连 loadW 口时回落恒 0；
  //   顶层 WorkerBee 正常驱动时兜底被覆盖。
  val loadWInSafe = WireDefault(false.B)
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  // 全局 valid skew：外部供数按「行 i 延迟 i 拍」错峰，数据波前到达 PE(x,y) 的时刻为
  //   r+1+x+y。valid 在 WbCell 内沿 x+y 二维传播，起始 skew 取 ci*arrayN，使 PE(x,y) 的
  //   valid 窗口与数据波前严格对齐。
  val validSkewedShiftRegisters = RegInit(VecInit(Seq.fill(clusterM * arrayN)(false.B)))
  validSkewedShiftRegisters(0) := io.validIn
  for(i <- 1 until clusterM*arrayN){
      validSkewedShiftRegisters(i) := validSkewedShiftRegisters(i-1)
  }
  val validSkewedPerRow = WireInit(VecInit(Seq.fill(clusterM*arrayN)(false.B)))
  val shiftEnableIn = validSkewedPerRow.reduceTree(_ || _)
  for(i <- 0 until clusterM*arrayN){
    if(i == 0){
      validSkewedPerRow(0) := io.validIn
    }
    else{
      validSkewedPerRow(i) := validSkewedShiftRegisters(i-1)
    }
  }

  val aSkewedPerRow = WireInit(VecInit(Seq.fill(clusterM*arrayN)(0.U(aEffW.W))))  
  dontTouch(aSkewedPerRow)
  for(i <- 0 until clusterM*arrayN){
    if(i == 0){
      aSkewedPerRow(0) := io.aIn(0)
    }
    else{
      val delay = i 
      val aSkewedShiftRegisters = RegInit(VecInit(Seq.fill(delay)(0.U(aEffW.W))))
      when(shiftEnableIn){
        aSkewedShiftRegisters(0) := io.aIn(i)
      }
      for (j <- 1 until delay){
        when(shiftEnableIn){
          aSkewedShiftRegisters(j) := aSkewedShiftRegisters(j-1)
        }
      }
      aSkewedPerRow(i) := aSkewedShiftRegisters(delay - 1)
      // 水平 loadW 模式加载旁路（last-connect 覆盖，与 bSkew 的 loadV 旁路同构；垂直模式
      // loadWIn 恒 0 时为死路）：加载窗口内权重水平加载无需 skew，各行同拍直连 io.aIn
      when(loadWInSafe){
        aSkewedPerRow(i) := io.aIn(i)
      }
    }
  }
  // scaleA skew：per-row，与 aSkewedPerRow 完全镜像（随激活链同 skew 流动）。
  val scaleASkewedPerRow = WireInit(VecInit(Seq.fill(clusterM*arrayN)(0.U(8.W))))
  dontTouch(scaleASkewedPerRow)
  for(i <- 0 until clusterM*arrayN){
    if(i == 0){
      scaleASkewedPerRow(0) := io.scaleAIn(0)
    }
    else{
      val delay = i
      val scaleASkewedShiftRegisters = RegInit(VecInit(Seq.fill(delay)(0.U(8.W))))
      when(shiftEnableIn){
        scaleASkewedShiftRegisters(0) := io.scaleAIn(i)
      }
      for (j <- 1 until delay){
        when(shiftEnableIn){
          scaleASkewedShiftRegisters(j) := scaleASkewedShiftRegisters(j-1)
        }
      }
      scaleASkewedPerRow(i) := scaleASkewedShiftRegisters(delay - 1)
      when(loadWInSafe){
        scaleASkewedPerRow(i) := io.scaleAIn(i)
      }
    }
  }
  val bSkewedPerRow = WireInit(VecInit(Seq.fill(clusterM*arrayN)(0.U(cEffW.W))))
  dontTouch(bSkewedPerRow)
  for(i <- 0 until clusterM*arrayN){
    if(i == 0){
      bSkewedPerRow(0) := io.psumIn(0)
    }
    else{
      val delay = i 
      val bSkewedShiftRegisters = RegInit(VecInit(Seq.fill(delay)(0.U(cEffW.W))))
      when(shiftEnableIn){
        bSkewedShiftRegisters(0) := io.psumIn(i)
      }
      for (j <- 1 until delay){
        when(shiftEnableIn){
          bSkewedShiftRegisters(j) := bSkewedShiftRegisters(j-1)
        }
      }
      when(io.loadVIn){
        bSkewedPerRow(i) := io.psumIn(i)
      }.otherwise{
        bSkewedPerRow(i) := bSkewedShiftRegisters(delay - 1)
      } 
    }
  }
  
  
  for (ci <- 0 until clusterM; cj <- 0 until clusterM) {
    val arr = arrays(ci)(cj)

    // clear / loadH / loadVInLock / loadWInLock：广播（无 skew）
    arr.io.clear := io.clear
    arr.io.loadHIn := io.loadHIn
    arr.io.loadVInLock := io.loadVInLock
    // loadWInLock 广播全簇：末拍对角线落位态的唯一时刻
    arr.io.loadWInLock := loadWInLockSafe

    // fmt/rnd/bFmt 逐列（cj）向右传播
    if (cj == 0) {
      arr.io.fmtIn   := io.fmtIn
      arr.io.rndIn   := io.rndIn
      arr.io.bFmtIn  := io.bFmtIn
    } else {
      arr.io.fmtIn   := arrays(ci)(cj - 1).io.fmtOut
      arr.io.rndIn   := arrays(ci)(cj - 1).io.rndOut
      arr.io.bFmtIn  := arrays(ci)(cj - 1).io.bFmtOut
    }

    
    // Activation aIn and both MX scale feeds: per-row, left-to-right.
    // AXIS NOTE: `globalLane` is the PE-grid ROW coordinate = the K-LANE axis, not the
    //   weight-row / output-N axis. In compute, lane x carries the K elements of one
    //   activation beat, the output row m is encoded in the arrival time (row x fed
    //   skewed by x cycles) and the output column n_out is the PE column y. In the
    //   loadW phase (MX always uses it, guarded by CalcConfig errCode=4) B is stored
    //   N-major, beat s carries row n_out = totalN-1-s and lane x shifts right along
    //   the a-chain, so on the loadWLock beat PE(x,y) holds B[n_out = y][k-lane x].
    //   scaleAIn/scaleBIn are both indexed by x, the same axis the Executor's
    //   idx = (lane*step)/blk byte select is derived from.
    for (r <- 0 until arrayN) {
      val globalLane = ci * arrayN + r
      if (cj == 0) {
        // Left-most cluster column: connect straight to the top-level ports.
        arr.io.validIn(r) := validSkewedPerRow(globalLane)
        arr.io.aIn(r) := aSkewedPerRow(globalLane)
        arr.io.scaleAIn(r) := scaleASkewedPerRow(globalLane)
        arr.io.loadWIn(r) := loadWInSafe
        // scaleB per-lane injection at the left edge, straight (no skew chain)
        arr.io.scaleBIn(r) := io.scaleBIn(globalLane)
      } else {
        // Non left-most cluster column: take everything from the cluster on the left,
        // so both scale chains cascade along cj exactly like the a chain, 1 cycle per
        // cluster column, lockstep with aOut.
        arr.io.validIn(r) := arrays(ci)(cj-1).io.validOut(r)
        arr.io.aIn(r) := arrays(ci)(cj-1).io.aOut(r)
        arr.io.scaleAIn(r) := arrays(ci)(cj-1).io.scaleAOut(r)
        arr.io.loadWIn(r) := arrays(ci)(cj-1).io.loadWOut(r)
        arr.io.scaleBIn(r) := arrays(ci)(cj-1).io.scaleBOut(r)
      }
    }

    // psumIn: per-column, unconditional direct connection (no skew). `globalCol` is
    //   the output-N axis. MX blocks are cut along K, so a scale byte index must be a
    //   function of the row/lane coordinate only, never of this column coordinate.
    for (c <- 0 until arrayN) {
      val globalCol = cj * arrayN + c
      if (ci == 0) {

        // 首行无条件直连 io.psumIn：垂直权重加载时传权重；计算阶段（K > totalN 多 pass）
        // 由 Executor 注入已按行 i 延迟 i 拍 skew 的 partial sum 作为累加基底，非回灌窗口
        // Executor 将 wbPsumIn 全置 0。
        arr.io.validInV(c) := validSkewedPerRow(globalCol)
        arr.io.psumIn(c) := bSkewedPerRow(globalCol)
        arr.io.loadVIn := io.loadVIn
      } else {
        arr.io.loadVIn := arrays(ci-1)(cj).io.loadVOut(c)
        arr.io.validInV(c) := arrays(ci-1)(cj).io.validOutV(c)
        arr.io.psumIn(c) := arrays(ci-1)(cj).io.cOut(c)
      }
    }
  }

  // 集群输出 deskew：cOut/validOut 从底部行簇取值（psum 从上到下流动，结果在底部行）
  val cOutAllValidOut = WireInit(VecInit(Seq.fill(clusterM * arrayN)(false.B)))
  val cOutAllData = WireInit(VecInit(Seq.fill(clusterM * arrayN)(0.U(cEffW.W))))
  val cOutShiftEn = WireInit(false.B)
  cOutShiftEn := cOutAllValidOut.reduceTree(_ || _)
  for (cj <- 0 until clusterM; c <- 0 until arrayN) {
    val globalCol = cj * arrayN + c
    cOutAllValidOut(globalCol) := arrays(clusterM - 1)(cj).io.validOut(c)
    cOutAllData(globalCol)     := arrays(clusterM - 1)(cj).io.cOut(c)
  }
  for (i <- 0 until clusterM*arrayN){
    val dly = clusterM * arrayN - 1 - i
    if(dly == 0){
      io.cOut(i) := cOutAllData(i)
    }
    else{
      val cOutAllDataDly = RegInit(VecInit(Seq.fill(dly)(0.U(cEffW.W))))
      cOutAllDataDly(0) := cOutAllData(i)
      for (j <- 1 until dly){
        when(cOutShiftEn){
          cOutAllDataDly(j) := cOutAllDataDly(j-1)
        }
      }
      io.cOut(i) := cOutAllDataDly(dly-1)
    }
  }
  io.validOut := arrays(clusterM - 1)(clusterM - 1).io.validOutV(arrayN - 1)
  
 
}
