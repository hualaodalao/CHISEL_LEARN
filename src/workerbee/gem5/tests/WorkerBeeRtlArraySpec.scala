import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import scala.collection.mutable

private class WorkerBeeRtlArrayWrapper(cfg: WorkerBeeConfig) extends Module {
  val array = Module(new WbComb(8, 2, 16, 16, cfg.cEffW, cfg.supportedFmts))
  val io = IO(chiselTypeOf(array.io))
  array.io <> io

  val coordinates = Seq((0, 0), (0, 1), (1, 0), (7, 7), (7, 8), (8, 7),
    (8, 8), (0, 15), (15, 0), (14, 15), (15, 14), (15, 15))
  val observed = IO(new Bundle {
    val validSkewedPerRow = Output(Vec(16, Bool()))
    val aSkewedPerRow = Output(Vec(16, UInt(16.W)))
    val scaleASkewedPerRow = Output(Vec(16, UInt(8.W)))
    val shiftEnableIn = Output(Bool())
    val cOutAllValidOut = Output(Vec(16, Bool()))
    val cOutAllData = Output(Vec(16, UInt(cfg.cEffW.W)))
    val cOutShiftEn = Output(Bool())
    val horizontalIn = Output(Vec(coordinates.size, Bool()))
    val horizontalOut = Output(Vec(coordinates.size, Bool()))
    val verticalIn = Output(Vec(coordinates.size, Bool()))
    val verticalOut = Output(Vec(coordinates.size, Bool()))
  })
  observed.validSkewedPerRow := BoringUtils.tapAndRead(array.validSkewedPerRow)
  observed.aSkewedPerRow := BoringUtils.tapAndRead(array.aSkewedPerRow)
  observed.scaleASkewedPerRow := BoringUtils.tapAndRead(array.scaleASkewedPerRow)
  observed.shiftEnableIn := BoringUtils.tapAndRead(array.shiftEnableIn)
  observed.cOutAllValidOut := BoringUtils.tapAndRead(array.cOutAllValidOut)
  observed.cOutAllData := BoringUtils.tapAndRead(array.cOutAllData)
  observed.cOutShiftEn := BoringUtils.tapAndRead(array.cOutShiftEn)
  for (((row, col), index) <- coordinates.zipWithIndex) {
    val pe = array.arrays(row / 8)(col / 8).pes(row % 8)(col % 8)
    observed.horizontalIn(index) := BoringUtils.tapAndRead(pe.io.validIn)
    observed.horizontalOut(index) := BoringUtils.tapAndRead(pe.io.validOut)
    observed.verticalIn(index) := BoringUtils.tapAndRead(pe.io.validInV)
    observed.verticalOut(index) := BoringUtils.tapAndRead(pe.io.validOutV)
  }
}

private class WorkerBeeRtlExecutorWrapper(cfg: WorkerBeeConfig) extends Module {
  val executor = Module(new WorkerBeeExecutor2(cfg))
  val calc = Module(new WorkerBeeCalcConfig(cfg))
  val io = IO(new Bundle {
    val startIn = Input(Bool())
    val regFile = Input(WorkerBeeRegister(cfg))
    val dmaABusy = Input(Bool())
    val dmaBBusy = Input(Bool())
    val dmaCBusy = Input(Bool())
    val aOccupancy = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))
    val bOccupancy = Input(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val cOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
    val cPeekDone = Input(Bool())
    val aPop = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    val bPop = slave(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val cPop = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPush = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val scaleAPop = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleAW.W)))) else None
    val scaleBPop = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleBW.W)))) else None
    val scaleBOcc = if (cfg.hasMx) Some(Input(UInt(log2Up(cfg.scaleBufferDepth + 1).W))) else None
    val biasIn = if (cfg.hasMx) Some(slave(Stream(UInt(32.W)))) else None
    val rqMIn = if (cfg.hasMx) Some(slave(Stream(UInt(32.W)))) else None
    val wbCOut = Input(Vec(cfg.totalN, UInt(cfg.cEffW.W)))
    val wbValidOut = Input(Bool())
    val busy = Output(Bool())
    val done = Output(Bool())
    val wbLoadH = Output(Bool())
    val wbLoadV = Output(Bool())
    val wbLoadVLock = Output(Bool())
    val wbLoadW = Output(Bool())
    val wbLoadWLock = Output(Bool())
    val wbValidIn = Output(Bool())
    val cStoreGate = Output(Bool())
    val flushA = Output(Bool())
    val flushB = Output(Bool())
    val flushC = Output(Bool())
    val aFire = Output(Bool())
    val bFire = Output(Bool())
    val cFire = Output(Bool())
    val scaleAFire = Output(Bool())
    val scaleBFire = Output(Bool())
    val calcStartOut = Output(Bool())
    val calcErr = Output(Bool())
    val calcErrCode = Output(UInt(4.W))
    val calcConfig = Output(WorkerBeeExePreCalcConfig(cfg))
  })
  calc.io.startIn := io.startIn
  calc.io.regFile := io.regFile
  executor.io.execute := calc.io.startOut
  executor.io.calcConfig := calc.io.calcConfig
  executor.io.regFile := io.regFile
  executor.io.dmaABusy := io.dmaABusy
  executor.io.dmaBBusy := io.dmaBBusy
  executor.io.dmaCBusy := io.dmaCBusy
  executor.io.aOccupancy := io.aOccupancy
  executor.io.bOccupancy := io.bOccupancy
  executor.io.cOccupancy := io.cOccupancy
  executor.io.cPeekDone := io.cPeekDone
  executor.io.aPop <> io.aPop
  executor.io.bPop <> io.bPop
  executor.io.cPop <> io.cPop
  io.cPush <> executor.io.cPush
  if (cfg.hasMx) {
    executor.io.scaleAPop.get <> io.scaleAPop.get
    executor.io.scaleBPop.get <> io.scaleBPop.get
    executor.io.scaleBOcc.get := io.scaleBOcc.get
    executor.io.biasIn.get <> io.biasIn.get
    executor.io.rqMIn.get <> io.rqMIn.get
  }
  executor.io.wbCOut := io.wbCOut
  executor.io.wbValidOut := io.wbValidOut
  io.busy := executor.io.busy
  io.done := executor.io.done
  io.wbLoadH := executor.io.wbLoadH
  io.wbLoadV := executor.io.wbLoadV
  io.wbLoadVLock := executor.io.wbLoadVLock
  io.wbLoadW := executor.io.wbLoadW
  io.wbLoadWLock := executor.io.wbLoadWLock
  io.wbValidIn := executor.io.wbValidIn
  io.cStoreGate := executor.io.cStoreGate
  io.flushA := executor.io.flushA
  io.flushB := executor.io.flushB
  io.flushC := executor.io.flushC
  io.aFire := executor.io.aPop.fire
  io.bFire := executor.io.bPop.fire
  io.cFire := executor.io.cPop.fire
  io.scaleAFire := (if (cfg.hasMx) executor.io.scaleAPop.get.fire else false.B)
  io.scaleBFire := (if (cfg.hasMx) executor.io.scaleBPop.get.fire else false.B)
  io.calcStartOut := calc.io.startOut
  io.calcErr := calc.io.err
  io.calcErrCode := calc.io.errCode
  io.calcConfig := calc.io.calcConfig

  val observed = IO(new Bundle {
    val state = Output(UInt(4.W))
    val drainNextCnt = Output(UInt(log2Up(2 * cfg.totalN).W))
    val tileInterCnt = Output(UInt(log2Up(cfg.totalN).W))
    val curNTile = Output(UInt(16.W))
    val curKTile = Output(UInt(16.W))
  })
  observed.state := BoringUtils.tapAndRead(executor.state)
  observed.drainNextCnt := BoringUtils.tapAndRead(executor.drainNextCnt)
  observed.tileInterCnt := BoringUtils.tapAndRead(executor.tileInterCnt)
  observed.curNTile := BoringUtils.tapAndRead(executor.curNTile)
  observed.curKTile := BoringUtils.tapAndRead(executor.curKTile)
}

class WorkerBeeRtlArraySpec extends RtlEdgeSuite {
  behavior of "WorkerBee RTL array timing"

  it should "array_wavefront" in {
    simulate(new WorkerBeeRtlArrayWrapper(cfg)) { dut =>
      val t = 16
      val inputs = mutable.ArrayBuffer.empty[Boolean]
      val aDelay = Array.tabulate(t)(row => Array.fill(row)(BigInt(0)))
      val scaleDelay = Array.tabulate(t)(row => Array.fill(row)(BigInt(0)))
      val cDelay = Array.tabulate(t)(col => Array.fill(t - 1 - col)(BigInt(0)))
      val rawValues = Array.fill(t)(mutable.Set.empty[BigInt])
      val nonzeroValidColumns = mutable.Set.empty[Int]
      var cycle = 0
      var disabledFirstStageChanges = 0
      var disabledLaterHolds = 0
      var enabledLaterMoves = 0
      var skewHoldChanges = 0
      var loadWBypassChanges = 0
      var outputTokens = 0

      def bit(value: Boolean): BigInt = if (value) BigInt(1) else BigInt(0)
      def tick(valid: Boolean = false, loadH: Boolean = false, loadV: Boolean = false,
          loadW: Boolean = false, lockW: Boolean = false, clear: Boolean = false,
          resetting: Boolean = false): Unit = {
        dut.reset.poke(resetting.B)
        dut.io.validIn.poke(valid.B)
        dut.io.loadHIn.poke(loadH.B)
        dut.io.loadVIn.poke(loadV.B)
        dut.io.loadVInLock.poke(false.B)
        dut.io.loadWIn.poke(loadW.B)
        dut.io.loadWInLock.poke(lockW.B)
        dut.io.clear.poke(clear.B)
        dut.io.fmtIn.poke(DataFormat.INT8)
        dut.io.bFmtIn.poke(DataFormat.INT8)
        dut.io.rndIn.poke(RoundingMode.RNE)
        val a = Vector.tabulate(t)(row => BigInt(1 + ((cycle + 1) * 17 + row * 3) % 101) * 257)
        val scale = Vector.tabulate(t)(row => BigInt((cycle * 7 + row * 11) & 255))
        for (row <- 0 until t) {
          dut.io.aIn(row).poke(a(row).U)
          dut.io.psumIn(row).poke((1000 + cycle * 13 + row * 5).U)
          dut.io.scaleAIn(row).poke(scale(row).U)
          dut.io.scaleBIn(row).poke(127.U)
        }
        val signals = Seq[(String, Data)](
          "reset" -> dut.reset, "validIn" -> dut.io.validIn,
          "loadHIn" -> dut.io.loadHIn, "loadVIn" -> dut.io.loadVIn,
          "loadVInLock" -> dut.io.loadVInLock, "loadWIn" -> dut.io.loadWIn,
          "loadWInLock" -> dut.io.loadWInLock, "clear" -> dut.io.clear,
          "shiftEnableIn" -> dut.observed.shiftEnableIn,
          "cOutShiftEn" -> dut.observed.cOutShiftEn, "validOut" -> dut.io.validOut
        ) ++ (0 until t).flatMap { row =>
          Seq[(String, Data)](
            s"aIn_$row" -> dut.io.aIn(row), s"psumIn_$row" -> dut.io.psumIn(row),
            s"scaleAIn_$row" -> dut.io.scaleAIn(row), s"scaleBIn_$row" -> dut.io.scaleBIn(row),
            s"validSkewedPerRow_$row" -> dut.observed.validSkewedPerRow(row),
            s"aSkewedPerRow_$row" -> dut.observed.aSkewedPerRow(row),
            s"scaleASkewedPerRow_$row" -> dut.observed.scaleASkewedPerRow(row),
            s"cOutAllValidOut_$row" -> dut.observed.cOutAllValidOut(row),
            s"cOutAllData_$row" -> dut.observed.cOutAllData(row),
            s"cOut_$row" -> dut.io.cOut(row))
        } ++ dut.coordinates.zipWithIndex.flatMap { case ((row, col), index) =>
          Seq[(String, Data)](
            s"pe_${row}_${col}_validIn" -> dut.observed.horizontalIn(index),
            s"pe_${row}_${col}_validOut" -> dut.observed.horizontalOut(index),
            s"pe_${row}_${col}_validInV" -> dut.observed.verticalIn(index),
            s"pe_${row}_${col}_validOutV" -> dut.observed.verticalOut(index))
        }
        val values = sample("array_wavefront", cycle, signals: _*)
        def token(delay: Int): Boolean =
          if (delay == 0) valid else cycle >= delay && inputs(cycle - delay)
        val shiftEnable = (0 until t).exists(row => token(row))
        val cEnable = (0 until t).exists(col => token(t + col))
        if (!resetting) {
          withClue(s"array_wavefront pre_edge=$cycle: ") {
            values("shiftEnableIn") should be(bit(shiftEnable))
            values("cOutShiftEn") should be(bit(cEnable))
            values("validOut") should be(bit(token(2 * t - 1)))
            if (token(2 * t - 1)) outputTokens += 1
            for (row <- 0 until t) {
              values(s"validSkewedPerRow_$row") should be(bit(token(row)))
              val expectedA = if (row == 0 || loadW) a(row) else aDelay(row).last
              val expectedScale = if (row == 0 || loadW) scale(row) else scaleDelay(row).last
              values(s"aSkewedPerRow_$row") should be(expectedA)
              values(s"scaleASkewedPerRow_$row") should be(expectedScale)
              if (row > 0 && !shiftEnable && !loadW && a(row) != expectedA) skewHoldChanges += 1
              if (row > 0 && loadW && a(row) != aDelay(row).last) loadWBypassChanges += 1
              values(s"cOutAllValidOut_$row") should be(bit(token(t + row)))
              val raw = values(s"cOutAllData_$row")
              rawValues(row) += raw
              if (token(t + row) && raw != 0) nonzeroValidColumns += row
              val expectedC = if (cDelay(row).isEmpty) raw else cDelay(row).last
              values(s"cOut_$row") should be(expectedC)
              if (!cEnable && cDelay(row).length == 1 && raw != cDelay(row)(0))
                disabledFirstStageChanges += 1
              if (!cEnable && cDelay(row).length > 1 && raw != cDelay(row)(0))
                disabledLaterHolds += 1
              if (cEnable && cDelay(row).length > 1 && cDelay(row).distinct.size > 1)
                enabledLaterMoves += 1
            }
            for ((row, col) <- dut.coordinates) {
              values(s"pe_${row}_${col}_validIn") should be(bit(token(row + col)))
              values(s"pe_${row}_${col}_validOut") should be(bit(token(row + col + 1)))
              values(s"pe_${row}_${col}_validInV") should be(bit(token(row + col)))
              values(s"pe_${row}_${col}_validOutV") should be(bit(token(row + col + 1)))
            }
          }
        }
        for (row <- 0 until t) {
          if (resetting) {
            for (stage <- aDelay(row).indices) aDelay(row)(stage) = BigInt(0)
            for (stage <- scaleDelay(row).indices) scaleDelay(row)(stage) = BigInt(0)
            for (stage <- cDelay(row).indices) cDelay(row)(stage) = BigInt(0)
          } else {
            if (shiftEnable && row > 0) {
              for (stage <- (1 until row).reverse) {
                aDelay(row)(stage) = aDelay(row)(stage - 1)
                scaleDelay(row)(stage) = scaleDelay(row)(stage - 1)
              }
              aDelay(row)(0) = a(row)
              scaleDelay(row)(0) = scale(row)
            }
            if (cDelay(row).nonEmpty) {
              if (cEnable) {
                for (stage <- (1 until cDelay(row).length).reverse)
                  cDelay(row)(stage) = cDelay(row)(stage - 1)
              }
              cDelay(row)(0) = values(s"cOutAllData_$row")
            }
          }
        }
        inputs += (valid && !resetting)
        dut.clock.step()
        cycle += 1
      }

      tick(resetting = true)
      tick(resetting = true)
      for (_ <- 0 until 32) tick(loadH = true)
      for (_ <- 0 until 24) tick(loadV = true)
      for (_ <- 0 until 20) tick()
      tick(clear = true)
      tick()
      for (beat <- 0 until t) tick(loadH = true, loadW = true, lockW = beat == t - 1)
      for (_ <- 0 until 32) tick()
      val sparse = Set(0, 4, 25, 47)
      val bursts = (70 until 77).toSet ++ (112 until 130).toSet
      for (offset <- 0 until 200) tick(valid = sparse(offset) || bursts(offset))
      outputTokens should be(sparse.size + bursts.size)
      nonzeroValidColumns.toSet should be((0 until t).toSet)
      for (col <- 0 until t) {
        rawValues(col).exists(_ != 0) should be(true)
        rawValues(col).size should be > 1
      }
      disabledFirstStageChanges should be > 0
      disabledLaterHolds should be > 0
      enabledLaterMoves should be > 0
      skewHoldChanges should be > 0
      loadWBypassChanges should be > 0
    }
  }

  it should "executor_windows" in {
    simulate(new WorkerBeeRtlExecutorWrapper(cfg)) { dut =>
      var cycle = 0
      def bit(value: Boolean): BigInt = if (value) BigInt(1) else BigInt(0)
      val signals = Seq[(String, Data)](
        "reset" -> dut.reset, "startIn" -> dut.io.startIn,
        "aFmt" -> dut.io.regFile.aFmt, "bFmt" -> dut.io.regFile.bFmt,
        "cFmt" -> dut.io.regFile.cFmt, "loadWMode" -> dut.io.regFile.loadWMode,
        "m" -> dut.io.regFile.m, "n" -> dut.io.regFile.n, "k" -> dut.io.regFile.k,
        "state" -> dut.observed.state, "drainNextCnt" -> dut.observed.drainNextCnt,
        "tileInterCnt" -> dut.observed.tileInterCnt, "curNTile" -> dut.observed.curNTile,
        "curKTile" -> dut.observed.curKTile, "calcStartOut" -> dut.io.calcStartOut,
        "calcErr" -> dut.io.calcErr, "calcErrCode" -> dut.io.calcErrCode,
        "calcMTile" -> dut.io.calcConfig.mTile, "calcNTile" -> dut.io.calcConfig.nTile,
        "calcKTile" -> dut.io.calcConfig.kTile, "isAX2" -> dut.io.calcConfig.isAX2,
        "isBX2" -> dut.io.calcConfig.isBX2,
        "busy" -> dut.io.busy, "done" -> dut.io.done,
        "dmaABusy" -> dut.io.dmaABusy, "dmaBBusy" -> dut.io.dmaBBusy,
        "dmaCBusy" -> dut.io.dmaCBusy, "wbValidOut" -> dut.io.wbValidOut,
        "wbValidIn" -> dut.io.wbValidIn, "wbLoadH" -> dut.io.wbLoadH,
        "wbLoadV" -> dut.io.wbLoadV, "wbLoadVLock" -> dut.io.wbLoadVLock,
        "wbLoadW" -> dut.io.wbLoadW, "wbLoadWLock" -> dut.io.wbLoadWLock,
        "aOccupancy" -> dut.io.aOccupancy, "bOccupancy" -> dut.io.bOccupancy,
        "cOccupancy" -> dut.io.cOccupancy, "scaleBOcc" -> dut.io.scaleBOcc.get,
        "aValid" -> dut.io.aPop.valid, "aReady" -> dut.io.aPop.ready,
        "aFire" -> dut.io.aFire, "bValid" -> dut.io.bPop.valid,
        "bReady" -> dut.io.bPop.ready, "bFire" -> dut.io.bFire,
        "cValid" -> dut.io.cPop.valid, "cReady" -> dut.io.cPop.ready,
        "cFire" -> dut.io.cFire, "cPushValid" -> dut.io.cPush.valid,
        "cPushReady" -> dut.io.cPush.ready, "cPeekDone" -> dut.io.cPeekDone,
        "scaleAValid" -> dut.io.scaleAPop.get.valid, "scaleAReady" -> dut.io.scaleAPop.get.ready,
        "scaleAFire" -> dut.io.scaleAFire, "scaleBValid" -> dut.io.scaleBPop.get.valid,
        "scaleBReady" -> dut.io.scaleBPop.get.ready, "scaleBFire" -> dut.io.scaleBFire,
        "cStoreGate" -> dut.io.cStoreGate, "flushA" -> dut.io.flushA,
        "flushB" -> dut.io.flushB, "flushC" -> dut.io.flushC)

      def rawEdge(): Unit = {
        sample("executor_windows", cycle, signals: _*)
        dut.clock.step()
        cycle += 1
      }
      def edge(state: Int, drain: Int, count: Int, kt: Int,
          checks: (String, BigInt)*): Int = {
        val current = cycle
        val values = sample("executor_windows", cycle, signals: _*)
        withClue(s"executor_windows pre_edge=$cycle: ") {
          values("state") should be(BigInt(state))
          values("drainNextCnt") should be(BigInt(drain))
          values("tileInterCnt") should be(BigInt(count))
          values("curNTile") should be(BigInt(0))
          values("curKTile") should be(BigInt(kt))
          values("busy") should be(bit(state != 0))
          values("done") should be(BigInt(0))
          values("calcErr") should be(BigInt(0))
          values("calcErrCode") should be(BigInt(0))
          for ((name, expected) <- checks) values(name) should be(expected)
        }
        dut.clock.step()
        cycle += 1
        current
      }

      for ((fmt, horizontal) <- Seq((DataFormat.INT8, false), (DataFormat.INT8, true),
          (DataFormat.MXE4M3, true))) {
        val mx = fmt == DataFormat.MXE4M3
        val reload = if (horizontal) 31 else 15
        dut.io.startIn.poke(false.B)
        dut.io.regFile.m.poke(1.U)
        dut.io.regFile.n.poke(16.U)
        dut.io.regFile.k.poke(64.U)
        dut.io.regFile.aAddr.poke(0x1000.U)
        dut.io.regFile.bAddr.poke(0x2000.U)
        dut.io.regFile.cAddr.poke(0x3000.U)
        dut.io.regFile.aAddrMode.poke(AddrMode.MAT)
        dut.io.regFile.bAddrMode.poke(AddrMode.MAT)
        dut.io.regFile.cAddrMode.poke(AddrMode.MAT)
        dut.io.regFile.aMxBlockSize.poke(MXBlockSize.M16)
        dut.io.regFile.bMxBlockSize.poke(MXBlockSize.M16)
        dut.io.regFile.cMxBlockSize.poke(MXBlockSize.M16)
        dut.io.regFile.loadWMode.poke(horizontal.B)
        dut.io.regFile.aFmt.poke(fmt)
        dut.io.regFile.bFmt.poke(fmt)
        dut.io.regFile.cFmt.poke(if (mx) DataFormat.FP32 else DataFormat.INT32)
        dut.io.regFile.rnd.poke(RoundingMode.RNE)
        dut.io.regFile.aScaleAddr.poke(0x4000.U)
        dut.io.regFile.bScaleAddr.poke(0x5000.U)
        dut.io.regFile.cScaleAddr.poke(0x6000.U)
        dut.io.regFile.warmup.poke(false.B)
        dut.io.regFile.warmdown.poke(false.B)
        dut.io.regFile.biasOn.poke(false.B)
        dut.io.regFile.biasAddr.poke(0.U)
        dut.io.regFile.requantOn.poke(false.B)
        dut.io.regFile.reluOn.poke(false.B)
        dut.io.regFile.rqMBase.poke(0.U)
        dut.io.regFile.rqShift.poke(0.U)
        dut.io.regFile.rqZp.poke(0.S)
        dut.io.dmaABusy.poke(false.B)
        dut.io.dmaBBusy.poke(false.B)
        dut.io.dmaCBusy.poke(true.B)
        dut.io.aOccupancy.poke(0.U)
        dut.io.bOccupancy.poke(0.U)
        dut.io.cOccupancy.poke(0.U)
        dut.io.cPeekDone.poke(false.B)
        dut.io.aPop.valid.poke(false.B)
        dut.io.aPop.payload.poke(0.U)
        dut.io.bPop.valid.poke(false.B)
        dut.io.bPop.payload.poke(0.U)
        dut.io.cPop.valid.poke(true.B)
        dut.io.cPop.payload.poke(0.U)
        dut.io.cPush.ready.poke(true.B)
        dut.io.scaleAPop.get.valid.poke(false.B)
        dut.io.scaleAPop.get.payload.poke(0.U)
        dut.io.scaleBPop.get.valid.poke(false.B)
        dut.io.scaleBPop.get.payload.poke(0.U)
        dut.io.scaleBOcc.get.poke(0.U)
        dut.io.biasIn.get.valid.poke(false.B)
        dut.io.biasIn.get.payload.poke(0.U)
        dut.io.rqMIn.get.valid.poke(false.B)
        dut.io.rqMIn.get.payload.poke(0.U)
        dut.io.wbValidOut.poke(false.B)
        for (col <- 0 until cfg.totalN) dut.io.wbCOut(col).poke(0.U)
        dut.reset.poke(true.B)
        rawEdge()
        rawEdge()
        dut.reset.poke(false.B)
        edge(0, 0, 0, 0)
        dut.io.startIn.poke(true.B)
        edge(0, 0, 15, 0, "calcStartOut" -> BigInt(0))
        dut.io.startIn.poke(false.B)
        edge(0, 0, 15, 0, "calcStartOut" -> BigInt(0))
        edge(0, 0, 15, 0, "calcStartOut" -> BigInt(1),
          "calcMTile" -> BigInt(1), "calcNTile" -> BigInt(1), "calcKTile" -> BigInt(2),
          "isAX2" -> BigInt(1), "isBX2" -> BigInt(1),
          "flushA" -> BigInt(1), "flushB" -> BigInt(1), "flushC" -> BigInt(1))

        def preload(b: Int, scaleB: Int): Unit = {
          dut.io.bOccupancy.poke(b.U)
          dut.io.scaleBOcc.get.poke(scaleB.U)
        }
        preload(15, if (mx) 16 else 0)
        for (_ <- 0 until 3) edge(4, 0, 15, 0, "bFire" -> BigInt(0))
        if (mx) {
          preload(16, 15)
          for (_ <- 0 until 3) edge(4, 0, 15, 0, "scaleBFire" -> BigInt(0))
        }
        preload(16, if (mx) 16 else 0)
        val firstGate = edge(4, 0, 15, 0)

        def loadWindow(kt: Int): Int = {
          val first = cycle
          for (beat <- 0 until 16) {
            preload(16 - beat, if (mx) 16 - beat else 0)
            dut.io.bPop.valid.poke(true.B)
            dut.io.bPop.payload.poke((BigInt(0x0101 + beat) *
              (0 until 16).map(lane => BigInt(1) << (16 * lane)).sum).U)
            dut.io.scaleBPop.get.valid.poke(mx.B)
            dut.io.scaleBPop.get.payload.poke(BigInt("7f7f7f7f", 16).U)
            edge(1, reload, 15 - beat, kt,
              "bReady" -> BigInt(1), "bFire" -> BigInt(1),
              "scaleBReady" -> bit(mx), "scaleBFire" -> bit(mx),
              "wbLoadH" -> BigInt(1), "wbLoadW" -> bit(horizontal),
              "wbLoadV" -> bit(!horizontal),
              "wbLoadWLock" -> bit(horizontal && beat == 15),
              "wbLoadVLock" -> bit(!horizontal && beat == 15), "wbValidIn" -> BigInt(0))
          }
          dut.io.bPop.valid.poke(false.B)
          dut.io.scaleBPop.get.valid.poke(false.B)
          preload(0, 0)
          cycle - first should be(16)
          first
        }
        loadWindow(0) should be(firstGate + 1)
        for (_ <- 0 until 3)
          edge(2, reload, 15, 0, "aFire" -> BigInt(0), "wbValidIn" -> BigInt(0))
        dut.io.aOccupancy.poke(1.U)
        dut.io.aPop.valid.poke(true.B)
        dut.io.aPop.payload.poke(0x0101.U)
        if (mx) edge(2, reload, 15, 0, "aFire" -> BigInt(0), "scaleAFire" -> BigInt(0))
        dut.io.scaleAPop.get.valid.poke(mx.B)
        dut.io.scaleAPop.get.payload.poke(BigInt("7f7f7f7f", 16).U)
        val lastAFire = edge(2, reload, 15, 0, "aFire" -> BigInt(1),
          "scaleAFire" -> bit(mx), "wbValidIn" -> BigInt(1), "cFire" -> BigInt(0))
        dut.io.aOccupancy.poke(0.U)
        dut.io.aPop.valid.poke(false.B)
        dut.io.scaleAPop.get.valid.poke(false.B)
        edge(3, reload, 15, 0) should be(lastAFire + 1)
        preload(16, if (mx) 16 else 0)
        val firstDrain = cycle
        firstDrain should be(lastAFire + 2)
        for (remaining <- 15 to 1 by -1)
          edge(4, remaining, 15, 1, "bFire" -> BigInt(0), "wbLoadH" -> BigInt(0))
        cycle should be(firstDrain + 15)
        preload(15, if (mx) 16 else 0)
        for (_ <- 0 until 3) edge(4, 0, 15, 1, "bFire" -> BigInt(0))
        if (mx) {
          preload(16, 15)
          for (_ <- 0 until 3) edge(4, 0, 15, 1, "scaleBFire" -> BigInt(0))
        }
        preload(16, if (mx) 16 else 0)
        val secondGate = edge(4, 0, 15, 1)
        loadWindow(1) should be(secondGate + 1)
        dut.io.cOccupancy.poke(1.U)
        dut.io.cPop.payload.poke(123.U)
        dut.io.aOccupancy.poke(1.U)
        dut.io.aPop.valid.poke(true.B)
        dut.io.scaleAPop.get.valid.poke(mx.B)
        val secondAFire = edge(2, reload, 15, 1, "aFire" -> BigInt(1),
          "scaleAFire" -> bit(mx), "wbValidIn" -> BigInt(1), "cFire" -> BigInt(1))
        dut.io.aOccupancy.poke(0.U)
        dut.io.aPop.valid.poke(false.B)
        dut.io.scaleAPop.get.valid.poke(false.B)
        edge(3, reload, 15, 1) should be(secondAFire + 1)
        edge(4, 15, 15, 0, "cStoreGate" -> BigInt(1), "done" -> BigInt(0))
        dut.io.wbValidOut.poke(true.B)
        edge(4, 15, 15, 0, "cPushValid" -> BigInt(1), "done" -> BigInt(0))
        dut.io.wbValidOut.poke(false.B)
        edge(4, 15, 15, 0, "cPushValid" -> BigInt(0), "done" -> BigInt(0))
      }
    }
  }
}
