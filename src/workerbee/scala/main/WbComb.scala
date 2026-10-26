/** WbComb（巢脾）- MxM 个 NxN WbCell 组成更大集群
  *                  （权重复用通道加载，无独立 wIn/wOut）
  *
  * @param arrayN   每个子阵列维度（默认 8）
  * @param clusterM 集群中子阵列的 M×M 排列（默认 2，即 2×2 = 16×16 等效）
  * @param aW       激活位宽（默认 16）
  * @param bW       权重位宽（默认 16）
  * @param cW       累加器位宽（默认 0=自动推导，totalN = arrayN * clusterM）
  *
  * 新架构要点：
  *   - loadHIn / loadVIn 广播到所有子阵列（无 loadHOut/loadVOut 链式传播）
  *   - fmtIn / rndIn 从首列子阵列进入，经 WbCell 的 fmtOut/rndOut
  *     逐列（cj 方向）向右传播
  *   - validIn 需要 array-row 级 skew（ci * arrayN）以对齐行级错峰数据
  *   - fmtIn / rndIn 为配置信号，仅在 loadH 窗口锁存进 PE 配置寄存器，ci 方向无 skew
  *   - aIn 不做内部 skew：WbCell 与 WbComb 都直连，错峰供数由外部 feeder 承担
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
  *   - psumIn：首行（ci=0）无条件直连 io.psumIn（无 skew）——加载期
  *     （loadVIn）传权重；计算期由调用方保证：非回灌窗口驱动 0，
  *     回灌窗口（K > totalN 多 pass 非首 pass）提供按「行 i 延迟 i 拍」
  *     skew 的 partial sum 累加基底（如 Executor cpsRegs）
  *
  * 横向级联：左方 tile aOut → 右方 tile aIn。
  * psumIn 纵向级联：上方 cOut → 下方 psumIn；顶行接 0 或 io.psumIn。
  * cOut/validOut：底部行 tile 输出。
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
  // 【破坏性变更留痕（C 写回重构本轮引入）】含 FP 族直接例化须显式 cW>=40，
  // 否则构造期失败。顶层 WorkerBee 通过传 cfg.cEffW（已含 >=40 分支）规避。
  if (hasFp)  require(cEffW >= 40,         s"含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aEffW + bW, s"含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    val aIn    = Input(Vec(totalN, UInt(aEffW.W)))
    //val aOut   = Output(Vec(totalN, UInt(aEffW.W)))
    val psumIn = Input(Vec(totalN, UInt(cEffW.W)))
    val cOut   = Output(Vec(totalN, UInt(cEffW.W)))

    val loadHIn = Input(Bool())   // 水平加载（标量）
    val loadVInLock = Input(Bool())   // 垂直加载（标量）
    val loadVIn = Input(Bool())   // 垂直加载（标量）
    // 水平 loadW 加载（可选模式，与垂直加载靠信号活跃性自然二选一）：
    // loadWInLock 广播全簇；loadWIn 沿 cj 方向 per-row 级联（与 a 链 lockstep）。
    // 垂直模式下恒 0，不影响既有行为
    val loadWIn = Input(Bool())
    val loadWInLock = Input(Bool())
    val validIn = Input(Bool())   // 标量 validIn
    val fmtIn   = Input(DataFormat())
    val rndIn   = Input(RoundingMode())
    // 权重格式（标量，镜像 fmtIn 逐列 cj 传播）
    val bFmtIn  = Input(DataFormat())
    // MX scale: BOTH scaleAIn and scaleBIn are per-row, i.e. indexed by the
    // PE-grid row coordinate, which in this array is the K-lane axis (see the
    // axis note above the sub-array hookup loop). Neither is per-column.
    //   scaleAIn : rides the activation skew chain (scaleASkewedPerRow), so it
    //              stays lockstep with aIn during the compute phase.
    //   scaleBIn : fed straight from the top-level port, no skew chain. That is
    //              correct, not an asymmetry bug: scaleBIn is only ever
    //              consumed on the loadWLock beat, and during the whole loadW
    //              window scaleASkewedPerRow is bypassed to a direct connection
    //              as well (see `when(loadWInSafe)` in the scaleA skew block),
    //              so on the phase that actually matters both sides have exactly
    //              the same shape and the same per-column 1-cycle cascade depth.
    // The previous wording at this spot ("scaleBIn per-column, column-resident")
    // was stale: it described an abandoned wiring form that was already
    // commented out below and never active. Removed together with that dead line
    // in #146 after the port-chain ground-truth audit.
    val scaleAIn = Input(Vec(totalN, UInt(8.W)))
    val scaleBIn = Input(Vec(totalN, UInt(8.W)))

    val clear   = Input(Bool())

    val validOut = Output(Bool())
  })


  // 子阵列
  val arrays = Seq.fill(clusterM, clusterM)(
    Module(new WbCell(arrayN, aW, bW, cEffW, supportedFmts))
  )

  // --- loadW 端口 WireDefault 兜底：既有测试（SystolicArrayTest 等）直接
  // 例化本模块时不连接 loadW 口（invalid），兜底回落恒 0，保证垂直模式
  // bit-exact；顶层 WorkerBee 正常驱动时兜底被覆盖 ---
  val loadWInSafe = WireDefault(false.B)
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  // --- 全局 skew：每个子阵列行 (ci) 的 valid 延迟（跟踪行级错峰数据） ---
  // 外部供数已按「行 i 延迟 i 拍」错峰，数据波前到达全局 PE(x,y) 的时刻为
  // r+1+x+y（含行内偏移 x）。valid 在 WbCell 内沿 x（逐行 RegNext）+ y
  // （逐列 RegNext）二维传播，起始 skew 为 ci*arrayN，使 PE(x,y) 的 valid
  // 窗口与数据波前严格对齐。若只在 y 方向传播（广播所有 x 行），cj>0 簇的
  // valid 将滞后数据 (x_local) 拍，前几个深度的累加被错误门控
  // fmt/rnd 仅在 loadH 窗口锁存进 PE 配置寄存器，无需跟踪数据时序，直接广播
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

  //A skew
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
      // 水平 loadW 模式加载旁路（last-connect 覆盖，与 bSkew 的 loadV 旁路同构；
      // 垂直模式 loadWIn 恒 0，本分支为死路）：加载窗口内权重水平加载
      // 无需 skew，各行同拍直连 io.aIn
      when(loadWInSafe){
        aSkewedPerRow(i) := io.aIn(i)
      }
    }
  }
  // scaleA skew：per-row，与 aSkewedPerRow 完全镜像（随激活链同 skew 流动）。
  // P1 顶层 scaleAIn 恒 0，故此链输出恒 0，不影响非 MX 行为。
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
  //B skew
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
  
  
  // 连接子阵列
  for (ci <- 0 until clusterM; cj <- 0 until clusterM) {
    val arr = arrays(ci)(cj)

    // --- clear：广播（无 skew） ---
    arr.io.clear := io.clear
    arr.io.loadHIn := io.loadHIn
    arr.io.loadVInLock := io.loadVInLock
    // loadWInLock 广播全簇（仿 loadVInLock；末拍对角线落位态的唯一时刻）
    arr.io.loadWInLock := loadWInLockSafe

    // --- loadHIn/fmtIn/rndIn：loadH 广播（不再链式传播），fmt/rnd 仍逐列向右传播 ---
    if (cj == 0) {
      arr.io.fmtIn   := io.fmtIn
      arr.io.rndIn   := io.rndIn
      arr.io.bFmtIn  := io.bFmtIn
    } else {
      arr.io.fmtIn   := arrays(ci)(cj - 1).io.fmtOut
      arr.io.rndIn   := arrays(ci)(cj - 1).io.rndOut
      arr.io.bFmtIn  := arrays(ci)(cj - 1).io.bFmtOut
    }

    
    // --- Activation aIn and both MX scale feeds: per-row, left-to-right ---
    // AXIS NOTE (#146 ground truth, do not "fix" this):
    //   `globalLane` is the PE-grid ROW coordinate, and in this array the row
    //   coordinate is the K-LANE axis -- NOT the weight-row / output-N axis.
    //     * Compute phase: lane x carries the K elements of one activation beat,
    //       and the output row m is encoded in the arrival time (row x is fed
    //       skewed by x cycles), while the output column n_out is the PE column y.
    //     * loadW phase (MX always uses it, guarded by CalcConfig errCode=4):
    //       B is stored N-major, beat s carries row n_out = totalN-1-s, and lane
    //       x of that beat is shifted right along the a-chain. On the loadWLock
    //       beat PE(x,y) therefore holds B[n_out = y][k-lane x].
    //   So: row coordinate x == K-lane axis, column coordinate y == output-N
    //   axis. scaleAIn and scaleBIn are both indexed by x, which is exactly the
    //   axis the Executor's idx = (lane*step)/blk byte select is derived from.
    //   The former name `globalRow` was geometrically fine but read as "weight
    //   row", which made the correct per-lane wiring look like a wrong-axis bug.
    for (r <- 0 until arrayN) {
      val globalLane = ci * arrayN + r
      if (cj == 0) {
        // Left-most cluster column: connect straight to the top-level ports.
        // During the compute phase the external feeder is required to supply
        // row i delayed by i cycles; during the load phase no skew is needed.
        // Both phases are therefore plain direct connections here.
        arr.io.validIn(r) := validSkewedPerRow(globalLane)
        arr.io.aIn(r) := aSkewedPerRow(globalLane)
        // scaleA per-lane injection at the left edge (same skew as aIn)
        arr.io.scaleAIn(r) := scaleASkewedPerRow(globalLane)
        // loadW pulse chain injection (horizontal loadW mode): every row of the
        // left-most cluster column takes the top-level loadWIn
        arr.io.loadWIn(r) := loadWInSafe
        // scaleB per-lane injection at the left edge, straight (no skew chain).
        // This is the SAME horizontal left-to-right topology as scaleAIn above,
        // and it is correct: see the axis note and the io.scaleBIn port comment.
        arr.io.scaleBIn(r) := io.scaleBIn(globalLane)
      } else {
        // Non left-most cluster column: take everything from the cluster on the
        // left, so both scale chains cascade along cj exactly like the a chain.
        arr.io.validIn(r) := arrays(ci)(cj-1).io.validOut(r)
        arr.io.aIn(r) := arrays(ci)(cj-1).io.aOut(r)
        // scaleA chain cascades per-lane along cj (mirrors the a chain, lockstep
        // with aOut)
        arr.io.scaleAIn(r) := arrays(ci)(cj-1).io.scaleAOut(r)
        // loadW chain cascades per-lane along cj (mirrors the a chain, lockstep
        // with aOut)
        arr.io.loadWIn(r) := arrays(ci)(cj-1).io.loadWOut(r)
        // scaleB chain cascades per-lane along cj, 1 cycle per cluster column,
        // same depth and same direction as the a chain (lockstep with aOut
        // because loadWIn is high on every beat of the loadW window)
        arr.io.scaleBIn(r) := arrays(ci)(cj-1).io.scaleBOut(r)
      }
    }

    // --- psumIn: per-column, unconditional direct connection (no skew) ---
    // `globalCol` here IS the output-N axis (PE column coordinate), which is the
    // correct reading of "column" -- unlike the stale scaleB wording removed in
    // #146. The abandoned per-column scaleB form
    // (`arr.io.scaleBIn(c) := io.scaleBIn(globalCol)`) that used to sit here as a
    // commented-out line has been DELETED: indexing scaleB by the column
    // coordinate would make the scale byte depend on n_out instead of on the
    // K-lane, i.e. it would transpose the block assignment. MX blocks are cut
    // along K, so the byte index must be a function of the row/lane coordinate
    // only. That dead line was the actual wrong-axis form; the live wiring above
    // is the right one.
    for (c <- 0 until arrayN) {
      val globalCol = cj * arrayN + c
      if (ci == 0) {

        // 首行：无条件直连 io.psumIn。
        // 垂直权重加载时传权重；计算阶段（K > totalN 多 pass）由 Executor
        // 注入 partial sum 作为累加基底（cpsRegs 已按行 i 延迟 i 拍 skew）。
        // 非回灌窗口（首 pass 计算/sDRAIN/sLOAD）Executor 将 wbPsumIn 全置 0，
        // 直连不会引入意外数据
        arr.io.validInV(c) := validSkewedPerRow(globalCol)
        arr.io.psumIn(c) := bSkewedPerRow(globalCol)
        arr.io.loadVIn := io.loadVIn
      } else {
        // 非首行：从上方子阵列获取
        arr.io.loadVIn := arrays(ci-1)(cj).io.loadVOut(c)
        arr.io.validInV(c) := arrays(ci-1)(cj).io.validOutV(c)
        arr.io.psumIn(c) := arrays(ci-1)(cj).io.cOut(c)
      }
    }
  }

  // --- 集群输出 ---
  // c Deskwe
  // cOut/validOut 从底部行簇取值（psum 从上到下流动，结果在底部行）
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
