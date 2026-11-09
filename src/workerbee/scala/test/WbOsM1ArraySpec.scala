import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import chisel3.util.experimental.BoringUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.language.reflectiveCalls

/** Sequencer 与 WbComb 并列：A 走 io.aIn(0)/validIn/scaleAIn(0)，B 走首行 osB，结果读 osPsum。 */
class WbOsM1ArrayHarness(n: Int = 16) extends WbOsM1RowDut(n) {
  private val fmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4)
  val seq = Module(new WbOsM1Sequencer(T = T, hasMx = false))
  val comb = Module(new WbComb(T, T, 16, 16, 48, fmts))

  val io = IO(new WbOsM1RowIo(T))
  val obs = IO(new Bundle {
    val restActive = Output(Bool())
    val restPsumNonZero = Output(Bool())
    val cOutNonZero = Output(Bool())
    val validOut = Output(Bool())
  })

  seq.io.aIn << io.aIn
  seq.io.bIn << io.bIn
  seq.io.mxMode := false.B
  seq.io.scaleOne := false.B
  seq.io.scaleAStep := 4.U
  seq.io.scaleBStep := 4.U
  seq.io.scaleABlk := 16.U
  seq.io.scaleBBlk := 16.U
  seq.io.flush := io.flush
  seq.io.start := io.start
  seq.io.kTiles := io.kTiles

  for (i <- 0 until T) {
    comb.io.aIn(i) := (if (i == 0) seq.io.aOut else 0.U)
    comb.io.psumIn(i) := 0.U
    comb.io.scaleAIn(i) := 0.U
    comb.io.scaleBIn(i) := 0.U
  }
  comb.io.validIn := seq.io.aTokenValid
  comb.io.loadHIn := io.loadH
  comb.io.loadVIn := false.B
  comb.io.loadVInLock := false.B
  comb.io.loadWIn := false.B
  comb.io.loadWInLock := false.B
  comb.io.fmtIn := io.fmt
  comb.io.bFmtIn := io.fmt
  comb.io.rndIn := RoundingMode.RNE
  comb.io.clear := io.clear
  comb.io.osM1 := true.B
  comb.io.osB := seq.io.bOut
  comb.io.osScaleB.foreach(_ := 0.U)
  comb.io.osSeed := io.seed
  comb.io.osSeedEn := io.seedEn
  comb.io.osAdvance := seq.io.advance
  comb.io.osLastIn := seq.io.aLast

  seq.io.consume := comb.io.osConsume
  seq.io.done := comb.io.osDone
  io.consume := comb.io.osConsume
  io.done := comb.io.osDone
  io.psum := comb.io.osPsum
  for (j <- 0 until T) io.validIn(j) := BoringUtils.tapAndRead(comb.osRow(j).io.validIn)
  io.advance := seq.io.advance
  io.aTokenValid := seq.io.aTokenValid
  io.resultsReady := seq.io.resultsReady
  io.phase := seq.io.phase
  io.headValid := seq.io.headValid

  private val restValid = for (i <- 1 until T; j <- 0 until T) yield
    BoringUtils.tapAndRead(comb.pes(i)(j).io.validIn) || BoringUtils.tapAndRead(comb.pes(i)(j).io.validInV)
  private val rowZeroValidV = (0 until T).map(j => BoringUtils.tapAndRead(comb.pes(0)(j).io.validInV))
  obs.restActive := (restValid ++ rowZeroValidV :+ BoringUtils.tapAndRead(comb.shiftEnableIn)).reduce(_ || _)
  obs.restPsumNonZero := (for (i <- 1 until T; j <- 0 until T)
    yield BoringUtils.tapAndRead(comb.pes(i)(j).io.psumOut) =/= 0.U).reduce(_ || _)
  obs.cOutNonZero := comb.io.cOut.map(_ =/= 0.U).reduce(_ || _)
  obs.validOut := comb.io.validOut
}

class WbOsM1ArraySpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val Task = WbOsM1Task

  /** OS 下第 1–15 行、纵向 valid、WS skew 与 cOut 通路全程静止。 */
  private def runner(dut: WbOsM1ArrayHarness): WbOsM1RowRunner = {
    val r = new WbOsM1RowRunner(dut, "WbOsM1ArraySpec")
    r.perCycle = () => {
      assert(!dut.obs.restActive.peek().litToBoolean, s"拍 ${r.cycle} OS 下 WS 行/纵向 valid/skew 移位不得活动")
      assert(!dut.obs.restPsumNonZero.peek().litToBoolean, s"拍 ${r.cycle} OS 下第 1–15 行 psum 须保持 0")
      assert(!dut.obs.cOutNonZero.peek().litToBoolean, s"拍 ${r.cycle} OS 下 cOut 须保持 0")
      assert(!dut.obs.validOut.peek().litToBoolean, s"拍 ${r.cycle} OS 下 validOut 须保持 0")
    }
    r
  }

  behavior of "WbComb OS-M1 first row"

  it should "complete stall-free INT8/INT4 tasks in exactly L+T-1 advances with frozen WS rows" in {
    simulate(new WbOsM1ArrayHarness()) { dut =>
      val r = runner(dut)
      r.runTask(Task(DataFormat.INT8, kTiles = 1, nTiles = 1, seed = 111, exact = true))
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 3, seed = 112, exact = true))
      r.runTask(Task(DataFormat.INT4, kTiles = 4, nTiles = 2, seed = 113, exact = true))
      r.runTask(Task(DataFormat.INT4, kTiles = 1, nTiles = 2, seed = 114, exact = true))
    }
  }

  it should "handle Prime freeze, Tail with prefetch and flush restart" in {
    simulate(new WbOsM1ArrayHarness()) { dut =>
      val r = runner(dut)
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 1, seed = 121, primeWait = 0, bDelay = 60))
      r.runTask(Task(DataFormat.INT8, kTiles = 1, nTiles = 2, seed = 122, primeWait = 0, aDelay = 45))
      r.runTask(Task(DataFormat.INT4, kTiles = 2, nTiles = 4, seed = 123, primeWait = 80))
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 2, seed = 124, abortAfter = 21))
      r.runTask(Task(DataFormat.INT8, kTiles = 2, nTiles = 2, seed = 125, pA = 0.3, pB = 0.3))
    }
  }

  it should "match tensor goldens under random A/B stalls" in {
    simulate(new WbOsM1ArrayHarness()) { dut =>
      val r = runner(dut)
      for ((s, i) <- Seq(141L, 142L, 143L).zipWithIndex) {
        val fmt = if (i % 2 == 0) DataFormat.INT8 else DataFormat.INT4
        r.runTask(Task(fmt, kTiles = 2 + i, nTiles = 3, seed = s, pA = 0.6, pB = 0.4, primeWait = i * 7))
      }
    }
  }

  behavior of "WbComb OS-M1 elaboration structure"

  it should "instantiate 16 WbWorkerOsM1, 240 WbWorker and 256 WbWorkUnit" in {
    val sv = _root_.circt.stage.ChiselStage.emitSystemVerilog(
      new WbComb(16, 16, 16, 16, 48, Set(DataFormat.INT8, DataFormat.INT4)))
    val mods = "(?sm)^module (\\w+)\\((.*?)^endmodule".r.findAllMatchIn(sv)
      .map(m => m.group(1) -> m.group(2)).toMap
    assert(mods.contains("WbComb"), "未发射 WbComb 模块")
    val inst = "(?m)^\\s+(\\w+)\\s+\\w+\\s*\\(".r
    val children = mods.map { case (k, body) =>
      k -> inst.findAllMatchIn(body).map(_.group(1)).filter(mods.contains).toSeq }
    val memo = scala.collection.mutable.Map.empty[(String, String => Boolean), Int]
    def count(top: String, hit: String => Boolean): Int =
      if (hit(top)) 1 else memo.getOrElseUpdate((top, hit), children(top).map(count(_, hit)).sum)
    val osRow = count("WbComb", _ == "WbWorkerOsM1")
    val ws = count("WbComb", m => "WbWorker(_\\d+)?".r.matches(m))
    val mac = count("WbComb", m => "WbWorkUnit(_\\d+)?".r.matches(m))
    assert(osRow == 16, s"WbWorkerOsM1 实例数 $osRow 期望 16")
    assert(ws == 240, s"WbWorker 实例数 $ws 期望 240")
    assert(mac == 256, s"WbWorkUnit 实例数 $mac 期望 256")
    println(s"[WbOsM1ArraySpec] structural PASS: osRow=$osRow ws=$ws mac=$mac")
  }
}
