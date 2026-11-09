/** WbComb（巢脾）- arrayN × arrayM 个 PE 平面脉动阵列（无簇层级；首行为 WbWorkerOsM1，其余 WbWorker）
  *                  （权重复用通道加载，无独立 wIn/wOut）
  *
  * @param arrayN   阵列行数（K-lane 轴，默认 16）
  * @param arrayM   阵列列数（输出 N 轴，默认 16）
  * @param aW       激活位宽（默认 16）
  * @param bW       权重位宽（默认 16）
  * @param cW       累加器位宽（默认 0=自动推导）
  *
  * loadHIn / loadVIn 广播到所有 PE（无链式传播）；fmtIn / rndIn / bFmtIn 从第 0 列注入，
  *   沿行链（j）逐列向右传播，只在 loadH 窗口锁存进 PE 配置寄存器、i 方向无 skew。横向
  *   级联左侧 PE aOut → 右侧 PE aIn；纵向级联上方 psumOut → 下方 psumIn，顶行接 0 或
  *   io.psumIn；cOut/validOut 取底部行。
  *
  * aIn skew 契约（调用方责任）：计算阶段外部必须按「行 i 延迟 i 拍」错峰供数，即时刻 t
  *   在 io.aIn(i) 上给出逻辑第 (t - i) 拍的激活（t < i 时给 0）。加载阶段无需 skew。
  *
  * 加载时序要求：权重加载需同时拉高 loadHIn + loadVIn（loadW 水平模式则拉高 loadHIn +
  *   loadWIn），psumIn 供权重（loadW 模式经 aIn 供权重）、另一输入无需供数，使能脉冲需
  *   覆盖全链传播（纵链/横链全深 arrayN，Executor 按 totalN 拍窗口驱动）；释放 loadV 后
  *   需保持 psumIn=权重直到 loadV 沿 i 链排空（>= arrayN 拍）再置 0 并 clear，否则尾部
  *   仍高的 loadV 会把 0 锁进深层 PE 的 wReg。
  *
  * skew 策略：aIn 不做 skew，加载/计算阶段统一直连；validIn 保留行级 skew（行 i 延迟 i
  *   拍）以跟踪行级错峰数据；psumIn 首行（i=0）无条件直连，加载期传权重，计算期由调用
  *   方保证非回灌窗口驱动 0、回灌窗口（K > arrayN 多 pass 非首 pass）提供按「行 i 延迟
  *   i 拍」skew 的 partial sum 累加基底（如 Executor cpsRegs）。
  *
  * OS-M1（osM1=1）：仅全局第 0 行计算。A token 复用 io.aIn(0)/io.validIn/io.scaleAIn(0)
  *   （行 0 无 skew，直达 PE(0,0) 后沿 WS 横链右传）；B/scaleB/seed 经 os* 端口按列直供；
  *   结果按自然列序从 osPsum 读出。行 1…arrayN-1 的 valid 与全部 skew 链在 OS 下冻结，
  *   cOut/validOut 不参与。
  */

import chisel3._
import chisel3.util._
import chisel3.dontTouch
import chisel3.experimental.BaseModule
import scala.language.reflectiveCalls

object WbComb {
  /** WbWorker 与 WbWorkerOsM1 共有的 WS 端口面；后者端口为其超集。 */
  type WsPeIo = Bundle {
    val aIn: UInt; val aOut: UInt; val psumIn: UInt; val psumOut: UInt
    val loadHIn: Bool; val loadVInLock: Bool; val loadVIn: Bool; val loadVOut: Bool
    val loadWIn: Bool; val loadWOut: Bool; val loadWInLock: Bool
    val validIn: Bool; val validOut: Bool; val validInV: Bool; val validOutV: Bool
    val fmtIn: DataFormat.Type; val fmtOut: DataFormat.Type
    val bFmtIn: DataFormat.Type; val bFmtOut: DataFormat.Type
    val scaleAIn: UInt; val scaleAOut: UInt; val scaleBIn: UInt; val scaleBOut: UInt
    val rndIn: RoundingMode.Type; val rndOut: RoundingMode.Type
    val clear: Bool
  }
  type WsPe = BaseModule { val io: WsPeIo }
}

class WbComb(
  arrayN: Int = 16,
  arrayM: Int = 16,
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 0,
  supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)
) extends Module {
  val aEffW: Int = math.max(aW, bW)
  private val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
  private val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
  private val autoAccW = aEffW + bW + log2Up(arrayN)
  val accW: Int = if (cW != 0) math.max(cW, bW)
                  else if (hasFp) math.max(32, autoAccW)
                  else autoAccW
  val cEffW: Int = accW

  // 含 FP 族直接例化须显式 cW>=40，否则构造期失败；顶层 WorkerBee 传 cfg.cEffW。
  if (hasFp)  require(cEffW >= 40,         s"含浮点格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  if (hasInt) require(cEffW >= aEffW + bW, s"含整数格式时 cEffW($cEffW) 必须 >= aEffW($aEffW) + bW($bW)")

  val io = IO(new Bundle {
    val aIn    = Input(Vec(arrayN, UInt(aEffW.W)))
    val psumIn = Input(Vec(arrayN, UInt(cEffW.W)))
    val cOut   = Output(Vec(arrayM, UInt(cEffW.W)))

    val loadHIn = Input(Bool())   // 水平加载（标量）
    val loadVInLock = Input(Bool())   // 垂直加载（标量）
    val loadVIn = Input(Bool())   // 垂直加载（标量）
    // 水平 loadW 加载（与垂直加载靠信号活跃性二选一）：loadWInLock 广播全阵列；
    // loadWIn 沿 j 方向 per-row 级联（与 a 链 lockstep）。垂直模式下恒 0。
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
    val scaleAIn = Input(Vec(arrayN, UInt(8.W)))
    val scaleBIn = Input(Vec(arrayN, UInt(8.W)))

    val clear   = Input(Bool())

    val validOut = Output(Bool())

    val osM1      = Input(Bool())
    val osB       = Input(Vec(arrayM, UInt(bW.W)))
    val osScaleB  = Input(Vec(arrayM, UInt(8.W)))
    val osSeed    = Input(Vec(arrayM, UInt(cEffW.W)))
    val osSeedEn  = Input(Bool())
    val osAdvance = Input(Bool())
    val osLastIn  = Input(Bool())
    val osPsum    = Output(Vec(arrayM, UInt(cEffW.W)))
    val osConsume = Output(Vec(arrayM, Bool()))
    val osDone    = Output(Vec(arrayM, Bool()))
  })


  val osRow = Seq.fill(arrayM)(Module(new WbWorkerOsM1(aW, bW, cEffW, supportedFmts)))
  val pes: Seq[Seq[WbComb.WsPe]] = (osRow: Seq[WbComb.WsPe]) +: Seq.fill(arrayN - 1, arrayM)(
    Module(new WbWorker(aW, bW, cEffW, supportedFmts)): WbComb.WsPe
  )

  // loadW 端口 WireDefault 兜底：直接例化本模块而不连 loadW 口时回落恒 0；
  //   顶层 WorkerBee 正常驱动时兜底被覆盖。
  val loadWInSafe = WireDefault(false.B)
  loadWInSafe := io.loadWIn
  val loadWInLockSafe = WireDefault(false.B)
  loadWInLockSafe := io.loadWInLock

  // 全局 valid skew：外部供数按「行 i 延迟 i 拍」错峰，数据波前到达 PE(i,j) 的时刻为
  //   i+1+j。valid 在阵列内沿 i+j 二维传播，起始 skew 取行号 i，使 PE(i,j) 的 valid
  //   窗口与数据波前严格对齐。
  val validSkewedShiftRegisters = RegInit(VecInit(Seq.fill(arrayN)(false.B)))
  validSkewedShiftRegisters(0) := io.validIn && !io.osM1
  for(i <- 1 until arrayN){
      validSkewedShiftRegisters(i) := validSkewedShiftRegisters(i-1)
  }
  val validSkewedPerRow = WireInit(VecInit(Seq.fill(arrayN)(false.B)))
  val shiftEnableIn = validSkewedPerRow.reduceTree(_ || _) && !io.osM1
  for(i <- 0 until arrayN){
    if(i == 0){
      validSkewedPerRow(0) := io.validIn
    }
    else{
      validSkewedPerRow(i) := validSkewedShiftRegisters(i-1)
    }
  }

  val aSkewedPerRow = WireInit(VecInit(Seq.fill(arrayN)(0.U(aEffW.W))))
  dontTouch(aSkewedPerRow)
  for(i <- 0 until arrayN){
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
  val scaleASkewedPerRow = WireInit(VecInit(Seq.fill(arrayN)(0.U(8.W))))
  dontTouch(scaleASkewedPerRow)
  for(i <- 0 until arrayN){
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
  val bSkewedPerRow = WireInit(VecInit(Seq.fill(arrayN)(0.U(cEffW.W))))
  dontTouch(bSkewedPerRow)
  for(i <- 0 until arrayN){
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
  
  // PE 接线（i = 行 / K-lane 轴，j = 列 / 输出 N 轴）：横链（j 向）传播
  //   valid/fmt/rnd/bFmt/a/scaleA/scaleB/loadW；纵链（i 向）传播 psum/validV/loadV；
  //   clear/loadHIn/loadVInLock/loadWInLock 全阵列广播（无 skew）。
  for (i <- 0 until arrayN) {
    for (j <- 0 until arrayM) {
      val pe = pes(i)(j)

      pe.io.clear        := io.clear
      pe.io.loadHIn      := io.loadHIn
      pe.io.loadVInLock  := io.loadVInLock
      pe.io.loadWInLock  := loadWInLockSafe

      if (j == 0) {
        pe.io.fmtIn    := io.fmtIn
        pe.io.rndIn    := io.rndIn
        pe.io.bFmtIn   := io.bFmtIn
        pe.io.validIn  := validSkewedPerRow(i)
        pe.io.aIn      := aSkewedPerRow(i)
        pe.io.scaleAIn := scaleASkewedPerRow(i)
        // scaleB per-lane injection at the left edge, straight (no skew chain)
        pe.io.scaleBIn := io.scaleBIn(i)
        pe.io.loadWIn  := loadWInSafe
      } else {
        pe.io.fmtIn    := pes(i)(j-1).io.fmtOut
        pe.io.rndIn    := pes(i)(j-1).io.rndOut
        pe.io.bFmtIn   := pes(i)(j-1).io.bFmtOut
        pe.io.validIn  := pes(i)(j-1).io.validOut
        pe.io.aIn      := pes(i)(j-1).io.aOut
        pe.io.scaleAIn := pes(i)(j-1).io.scaleAOut
        pe.io.scaleBIn := pes(i)(j-1).io.scaleBOut
        pe.io.loadWIn  := pes(i)(j-1).io.loadWOut
      }

      if (i == 0) {
        // 首行无条件直连 io.psumIn：垂直权重加载时传权重；计算阶段（K > arrayN 多 pass）
        // 由 Executor 注入已按行 i 延迟 i 拍 skew 的 partial sum 作为累加基底，非回灌窗口
        // Executor 将 wbPsumIn 全置 0。
        pe.io.loadVIn  := io.loadVIn
        pe.io.validInV := validSkewedPerRow(j) && !io.osM1
        pe.io.psumIn   := bSkewedPerRow(j)
      } else {
        pe.io.loadVIn  := pes(i-1)(j).io.loadVOut
        pe.io.validInV := pes(i-1)(j).io.validOutV
        pe.io.psumIn   := pes(i-1)(j).io.psumOut
      }
    }
  }

  for (j <- 0 until arrayM) {
    val pe = osRow(j).io
    pe.osM1      := io.osM1
    pe.osB       := io.osB(j)
    pe.osScaleB  := io.osScaleB(j)
    pe.osSeed    := io.osSeed(j)
    pe.osSeedEn  := io.osSeedEn
    pe.osAdvance := io.osAdvance
    pe.osLastIn  := (if (j == 0) io.osLastIn else osRow(j - 1).io.osLastOut)
    io.osPsum(j)    := pe.psumOut
    io.osConsume(j) := pe.osConsume
    io.osDone(j)    := pe.osDone
  }
  assert(!io.osM1 || !(io.loadVIn || io.loadVInLock || loadWInSafe || loadWInLockSafe),
    "WbComb: weight load signals must stay low in OS-M1 mode")

  // 阵列输出 deskew：cOut/validOut 从底部行取值（psum 从上到下流动，结果在底部行）
  val cOutAllValidOut = WireInit(VecInit(Seq.fill(arrayM)(false.B)))
  val cOutAllData = WireInit(VecInit(Seq.fill(arrayM)(0.U(cEffW.W))))
  val cOutShiftEn = WireInit(false.B)
  cOutShiftEn := cOutAllValidOut.reduceTree(_ || _)
  for (j <- 0 until arrayM) {
    cOutAllValidOut(j) := pes(arrayN - 1)(j).io.validOut
    cOutAllData(j)     := pes(arrayN - 1)(j).io.psumOut
  }
  for (i <- 0 until arrayM){
    val dly = arrayM - 1 - i
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
  io.validOut := pes(arrayN - 1)(arrayM - 1).io.validOutV
}
