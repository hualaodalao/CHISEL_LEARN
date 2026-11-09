/** WbOsM1PeSpec —— WbWorkerOsM1 双模式单 PE 单元测试
  *
  * 金锚策略（全部独立于 RTL 行为推导）：
  *   - Test A/B：WS 模式随机逐拍对拍。harness 内 osPe 与 wsPe 共享同一组激励 wire，
  *     osM1=0 时两 PE 全输出必须逐拍相等；同时随机驱动 os* 输入，证明 osM1=0 下
  *     OS 端口组零影响。
  *   - Test C：OS INT8 脚本化精确整数锚——seed/consume/暂停/last/done/clear/seed
  *     竞争逐拍跟踪 expPsum（cEffW 绕回截断）与各输出寄存。
  *   - Test D：OS-vs-WS 差分——INT8/MXE4M3/BF16 三格式族，wsPe 以 loadVInLock 装载
  *     wReg/scaleBReg 后由 psumIn=上拍 osPe.psum 驱动 WS 累加，与 osPe 本地反馈
  *     逐拍对拍，覆盖 b/scaleB/cReg mux 接线。
  *   - Test E：结构计数——无前缀发射数 WbWorkUnit 实例行，防第二份 MAC 例化。
  *
  * 随机激励约束：
  *   - fmtReg/rndReg/bFmtReg 与 WbFusionMac* 内部 RegNext 格式标志无复位初值，
  *     Verilator 逐实例随机化 → 对拍前必须先确定性锁存 fmt（2 拍让标志收敛）。
  *   - bFmtIn 与 fmtIn 同拍同值（MX×MX 或 BF16×BF16 成对，避开 lane0 混对断言）。
  *   - loadVInLock 与 loadWInLock 互斥（WbWorker 互斥断言）。
  *   - 数据字节避开 MX8 inf/nan 码（b & 0x7C == 0x7C：E5M2 exp 全 1、E4M3 NaN），
  *     否则 FpCvtMx8ToMid/FpCvtMx8ToFp32 消费沿断言会命中（E2M1 路径无断言）。
  *   - OS BF16 差分：a/osB 指数域避开 0xFF（BF16 inf/nan 有限算术契约）。
  */

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

/** 对拍 harness：osPe（WbWorkerOsM1）与 wsPe（WbWorker）共享同一组激励 wire。 */
class WbOsM1PeHarness(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48,
  supportedFmts: Set[DataFormat.Type]
) extends Module {
  val aEffW: Int = math.max(aW, bW)
  val cEffW: Int = math.max(cW, bW)

  val osPe = Module(new WbWorkerOsM1(aW, bW, cW, supportedFmts))
  val wsPe = Module(new WbWorker(aW, bW, cW, supportedFmts))

  val io = IO(new Bundle {
    // ---- 共享激励（两 PE 同源直连） ----
    val aIn         = Input(UInt(aEffW.W))
    val psumIn      = Input(UInt(cEffW.W))
    val loadHIn     = Input(Bool())
    val loadVInLock = Input(Bool())
    val loadVIn     = Input(Bool())
    val loadWIn     = Input(Bool())
    val loadWInLock = Input(Bool())
    val validIn     = Input(Bool())
    val validInV    = Input(Bool())
    val fmtIn       = Input(DataFormat())
    val bFmtIn      = Input(DataFormat())
    val scaleAIn    = Input(UInt(8.W))
    val scaleBIn    = Input(UInt(8.W))
    val rndIn       = Input(RoundingMode())
    val clear       = Input(Bool())
    // ---- OS-M1 激励（仅 osPe） ----
    val osM1        = Input(Bool())
    val osB         = Input(UInt(bW.W))
    val osScaleB    = Input(UInt(8.W))
    val osSeed      = Input(UInt(cEffW.W))
    val osSeedEn    = Input(Bool())
    val osAdvance   = Input(Bool())
    val osLastIn    = Input(Bool())
    // ---- 观测：osPe 全输出 ----
    val osAOut      = Output(UInt(aEffW.W))
    val osPsumOut   = Output(UInt(cEffW.W))
    val osValidOut  = Output(Bool())
    val osValidOutV = Output(Bool())
    val osLoadVOut  = Output(Bool())
    val osLoadWOut  = Output(Bool())
    val osFmtOut    = Output(DataFormat())
    val osBFmtOut   = Output(DataFormat())
    val osScaleAOut = Output(UInt(8.W))
    val osScaleBOut = Output(UInt(8.W))
    val osRndOut    = Output(RoundingMode())
    val osLastOut   = Output(Bool())
    val osConsume   = Output(Bool())
    val osDone      = Output(Bool())
    // ---- 观测：wsPe 全输出 ----
    val wsAOut      = Output(UInt(aEffW.W))
    val wsPsumOut   = Output(UInt(cEffW.W))
    val wsValidOut  = Output(Bool())
    val wsValidOutV = Output(Bool())
    val wsLoadVOut  = Output(Bool())
    val wsLoadWOut  = Output(Bool())
    val wsFmtOut    = Output(DataFormat())
    val wsBFmtOut   = Output(DataFormat())
    val wsScaleAOut = Output(UInt(8.W))
    val wsScaleBOut = Output(UInt(8.W))
    val wsRndOut    = Output(RoundingMode())
  })

  osPe.io.aIn := io.aIn
  osPe.io.psumIn := io.psumIn
  osPe.io.loadHIn := io.loadHIn
  osPe.io.loadVInLock := io.loadVInLock
  osPe.io.loadVIn := io.loadVIn
  osPe.io.loadWIn := io.loadWIn
  osPe.io.loadWInLock := io.loadWInLock
  osPe.io.validIn := io.validIn
  osPe.io.validInV := io.validInV
  osPe.io.fmtIn := io.fmtIn
  osPe.io.bFmtIn := io.bFmtIn
  osPe.io.scaleAIn := io.scaleAIn
  osPe.io.scaleBIn := io.scaleBIn
  osPe.io.rndIn := io.rndIn
  osPe.io.clear := io.clear
  osPe.io.osM1 := io.osM1
  osPe.io.osB := io.osB
  osPe.io.osScaleB := io.osScaleB
  osPe.io.osSeed := io.osSeed
  osPe.io.osSeedEn := io.osSeedEn
  osPe.io.osAdvance := io.osAdvance
  osPe.io.osLastIn := io.osLastIn

  wsPe.io.aIn := io.aIn
  wsPe.io.psumIn := io.psumIn
  wsPe.io.loadHIn := io.loadHIn
  wsPe.io.loadVInLock := io.loadVInLock
  wsPe.io.loadVIn := io.loadVIn
  wsPe.io.loadWIn := io.loadWIn
  wsPe.io.loadWInLock := io.loadWInLock
  wsPe.io.validIn := io.validIn
  wsPe.io.validInV := io.validInV
  wsPe.io.fmtIn := io.fmtIn
  wsPe.io.bFmtIn := io.bFmtIn
  wsPe.io.scaleAIn := io.scaleAIn
  wsPe.io.scaleBIn := io.scaleBIn
  wsPe.io.rndIn := io.rndIn
  wsPe.io.clear := io.clear

  io.osAOut := osPe.io.aOut
  io.osPsumOut := osPe.io.psumOut
  io.osValidOut := osPe.io.validOut
  io.osValidOutV := osPe.io.validOutV
  io.osLoadVOut := osPe.io.loadVOut
  io.osLoadWOut := osPe.io.loadWOut
  io.osFmtOut := osPe.io.fmtOut
  io.osBFmtOut := osPe.io.bFmtOut
  io.osScaleAOut := osPe.io.scaleAOut
  io.osScaleBOut := osPe.io.scaleBOut
  io.osRndOut := osPe.io.rndOut
  io.osLastOut := osPe.io.osLastOut
  io.osConsume := osPe.io.osConsume
  io.osDone := osPe.io.osDone

  io.wsAOut := wsPe.io.aOut
  io.wsPsumOut := wsPe.io.psumOut
  io.wsValidOut := wsPe.io.validOut
  io.wsValidOutV := wsPe.io.validOutV
  io.wsLoadVOut := wsPe.io.loadVOut
  io.wsLoadWOut := wsPe.io.loadWOut
  io.wsFmtOut := wsPe.io.fmtOut
  io.wsBFmtOut := wsPe.io.bFmtOut
  io.wsScaleAOut := wsPe.io.scaleAOut
  io.wsScaleBOut := wsPe.io.scaleBOut
  io.wsRndOut := wsPe.io.rndOut
}

class WbOsM1PeSpec extends AnyFlatSpec with Matchers with ChiselSim {

  implicit val plainVerilator: HasSimulator =
    HasSimulator.simulators.verilator(
      verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
    )

  private val defaultFmts: Set[DataFormat.Type] =
    Set(DataFormat.INT8, DataFormat.INT4, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1)

  /** 复位序列：全部输入置确定值，reset 3 拍 + 释放后 2 拍。 */
  private def resetDut(dut: WbOsM1PeHarness): Unit = {
    dut.reset.poke(true.B)
    dut.io.aIn.poke(0.U)
    dut.io.psumIn.poke(0.U)
    dut.io.loadHIn.poke(false.B)
    dut.io.loadVInLock.poke(false.B)
    dut.io.loadVIn.poke(false.B)
    dut.io.loadWIn.poke(false.B)
    dut.io.loadWInLock.poke(false.B)
    dut.io.validIn.poke(false.B)
    dut.io.validInV.poke(false.B)
    dut.io.fmtIn.poke(DataFormat.INT8)
    dut.io.bFmtIn.poke(DataFormat.INT8)
    dut.io.scaleAIn.poke(0.U)
    dut.io.scaleBIn.poke(0.U)
    dut.io.rndIn.poke(RoundingMode.RNE)
    dut.io.clear.poke(false.B)
    dut.io.osM1.poke(false.B)
    dut.io.osB.poke(0.U)
    dut.io.osScaleB.poke(0.U)
    dut.io.osSeed.poke(0.U)
    dut.io.osSeedEn.poke(false.B)
    dut.io.osAdvance.poke(false.B)
    dut.io.osLastIn.poke(false.B)
    dut.clock.step(3)
    dut.reset.poke(false.B)
    dut.clock.step(2)
  }

  /** WS 模式全输出逐拍对拍 + OS 端口组零影响断言。 */
  private def assertParity(dut: WbOsM1PeHarness, beat: Int): Unit = {
    val m = s"[WS parity beat#$beat]"
    assert(dut.io.osAOut.peek().litValue == dut.io.wsAOut.peek().litValue, s"$m aOut")
    assert(dut.io.osPsumOut.peek().litValue == dut.io.wsPsumOut.peek().litValue, s"$m psumOut")
    assert(dut.io.osValidOut.peek().litToBoolean == dut.io.wsValidOut.peek().litToBoolean, s"$m validOut")
    assert(dut.io.osValidOutV.peek().litToBoolean == dut.io.wsValidOutV.peek().litToBoolean, s"$m validOutV")
    assert(dut.io.osLoadVOut.peek().litToBoolean == dut.io.wsLoadVOut.peek().litToBoolean, s"$m loadVOut")
    assert(dut.io.osLoadWOut.peek().litToBoolean == dut.io.wsLoadWOut.peek().litToBoolean, s"$m loadWOut")
    assert(dut.io.osFmtOut.peek().litValue == dut.io.wsFmtOut.peek().litValue, s"$m fmtOut")
    assert(dut.io.osBFmtOut.peek().litValue == dut.io.wsBFmtOut.peek().litValue, s"$m bFmtOut")
    assert(dut.io.osScaleAOut.peek().litValue == dut.io.wsScaleAOut.peek().litValue, s"$m scaleAOut")
    assert(dut.io.osScaleBOut.peek().litValue == dut.io.wsScaleBOut.peek().litValue, s"$m scaleBOut")
    assert(dut.io.osRndOut.peek().litValue == dut.io.wsRndOut.peek().litValue, s"$m rndOut")
    assert(!dut.io.osConsume.peek().litToBoolean, s"$m osConsume must be 0 (osM1=0)")
    assert(!dut.io.osDone.peek().litToBoolean, s"$m osDone must be 0 (osM1=0)")
    assert(!dut.io.osLastOut.peek().litToBoolean, s"$m osLastOut must be 0 (osM1=0)")
  }

  /** 随机 WS 对拍主体：fmts 格式集、beats 拍数、seed 随机种子、tag 标签。 */
  private def runWsParity(fmts: Set[DataFormat.Type], beats: Int, seed: Long, tag: String): Unit = {
    val rng = new Random(seed)
    val fmtSeq = fmts.toSeq
    val rndSeq = RoundingMode.all.toSeq

    simulate(new WbOsM1PeHarness(supportedFmts = fmts)) { dut =>
      resetDut(dut)
      // 确定性 fmt/rnd 锁存：fmtReg/rndReg/bFmtReg 无复位初值（Verilator 逐实例随机化），
      //   不先锁存则两 PE 初值不同、首次累加即分叉；第 2 拍让 WbFusionMac* 内部
      //   RegNext 格式标志收敛。
      val f0 = fmtSeq.head
      for (_ <- 1 to 2) {
        dut.io.loadHIn.poke(true.B)
        dut.io.fmtIn.poke(f0)
        dut.io.bFmtIn.poke(f0)
        dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.aIn.poke(0.U)
        dut.io.psumIn.poke(0.U)
        dut.io.loadVInLock.poke(false.B)
        dut.io.loadVIn.poke(false.B)
        dut.io.loadWIn.poke(false.B)
        dut.io.loadWInLock.poke(false.B)
        dut.io.validIn.poke(false.B)
        dut.io.validInV.poke(false.B)
        dut.io.scaleAIn.poke(0.U)
        dut.io.scaleBIn.poke(0.U)
        dut.io.clear.poke(false.B)
        dut.io.osM1.poke(false.B)
        dut.io.osB.poke(0.U)
        dut.io.osScaleB.poke(0.U)
        dut.io.osSeed.poke(0.U)
        dut.io.osSeedEn.poke(false.B)
        dut.io.osAdvance.poke(false.B)
        dut.io.osLastIn.poke(false.B)
        dut.clock.step(1)
      }

      def safeByte: Int = rng.nextInt(0x7C) | (rng.nextInt(2) << 7) // 避开 MX8 inf/nan 码

      var beat = 0
      while (beat < beats) {
        val loadVInLock = rng.nextBoolean()
        val loadWInLock = if (loadVInLock) false else rng.nextBoolean()
        val f = fmtSeq(rng.nextInt(fmtSeq.size))
        dut.io.aIn.poke((safeByte | (safeByte << 8)).U)
        // psumIn 低 16 位会经 loadVInLock 进入 wReg（权重元素解码），一并避让
        dut.io.psumIn.poke(((BigInt(32, rng) << 16) | BigInt(safeByte | (safeByte << 8))).U)
        dut.io.loadHIn.poke(rng.nextBoolean().B)
        dut.io.loadVInLock.poke(loadVInLock.B)
        dut.io.loadVIn.poke(rng.nextBoolean().B)
        dut.io.loadWIn.poke(rng.nextBoolean().B)
        dut.io.loadWInLock.poke(loadWInLock.B)
        dut.io.validIn.poke(rng.nextBoolean().B)
        dut.io.validInV.poke(rng.nextBoolean().B)
        dut.io.fmtIn.poke(f)
        dut.io.bFmtIn.poke(f)
        dut.io.scaleAIn.poke(BigInt(8, rng).U)
        dut.io.scaleBIn.poke(BigInt(8, rng).U)
        dut.io.rndIn.poke(rndSeq(rng.nextInt(rndSeq.size)))
        dut.io.clear.poke((rng.nextInt(8) == 0).B)
        // os* 全部随机驱动：osM1=0 下必须零影响
        dut.io.osM1.poke(false.B)
        dut.io.osB.poke(BigInt(16, rng).U)
        dut.io.osScaleB.poke(BigInt(8, rng).U)
        dut.io.osSeed.poke(BigInt(48, rng).U)
        dut.io.osSeedEn.poke(rng.nextBoolean().B)
        dut.io.osAdvance.poke(rng.nextBoolean().B)
        dut.io.osLastIn.poke(rng.nextBoolean().B)
        dut.clock.step(1)
        assertParity(dut, beat)
        beat += 1
      }
      println(s"[WbOsM1PeSpec] WS parity ($tag) PASS $beat/$beats")
    }
  }

  behavior of "WbWorkerOsM1 WS-mode parity vs WbWorker"

  it should "match WbWorker bit-exactly under 800 random WS beats with os* driven randomly (default fmt set)" in {
    runWsParity(defaultFmts, 800, 20261105L, "default fmts")
  }

  it should "match WbWorker bit-exactly under 400 random WS beats (BF16 config)" in {
    runWsParity(Set(DataFormat.BF16, DataFormat.MXE4M3, DataFormat.MXE2M1), 400, 20261106L, "BF16")
  }

  behavior of "WbWorkerOsM1 OS INT8 scripted golden"

  it should "seed, consume, stall, last, done, clear and seed-competition per contract" in {
    simulate(new WbOsM1PeHarness(supportedFmts = Set(DataFormat.INT8))) { dut =>
      resetDut(dut)
      dut.io.osM1.poke(true.B)
      // fmt 锁存拍：OS 消费不判 fmtOk，但 workUnit 输出路由看 fmtReg（无 init 的 Reg
      //   复位后为 FP32，不锁存 INT8 则 sum 落 passthrough 臂）
      dut.io.loadHIn.poke(true.B)
      dut.io.fmtIn.poke(DataFormat.INT8)
      dut.io.bFmtIn.poke(DataFormat.INT8)
      dut.io.clear.poke(true.B)
      dut.clock.step(1)
      dut.io.loadHIn.poke(false.B)

      val mask48 = (BigInt(1) << 48) - 1
      var expPsum = BigInt(0)
      var expAOut = BigInt(0)
      var expValidOut = false
      var expLastOut = false
      var expDone = false
      var beat = 0

      def osStep(a: Int, b: Int, valid: Boolean, advance: Boolean, last: Boolean,
                 seedEn: Boolean, seed: BigInt, clr: Boolean): Unit = {
        dut.io.aIn.poke(a.U)
        dut.io.osB.poke(b.U)
        dut.io.validIn.poke(valid.B)
        dut.io.osAdvance.poke(advance.B)
        dut.io.osLastIn.poke(last.B)
        dut.io.osSeedEn.poke(seedEn.B)
        dut.io.osSeed.poke(seed.U)
        dut.io.clear.poke(clr.B)
        dut.clock.step(1)

        val consume = advance && valid
        // 金锚更新（硬件优先级序：clear → seed → consume）
        if (clr) expPsum = 0
        else if (seedEn) expPsum = seed
        else if (consume) expPsum = (expPsum + a * b) & mask48
        if (clr) expAOut = 0
        else if (advance) expAOut = a
        if (clr) { expValidOut = false; expLastOut = false }
        else {
          if (advance) expValidOut = valid
          if (advance) expLastOut = last
        }
        if (clr) expDone = false
        else if (consume && last) expDone = true

        val m = s"[OS INT8 beat#$beat]"
        assert(dut.io.osPsumOut.peek().litValue == expPsum,
          s"$m psum=0x${dut.io.osPsumOut.peek().litValue.toString(16)} expected=0x${expPsum.toString(16)}")
        assert(dut.io.osAOut.peek().litValue == expAOut, s"$m aOut")
        assert(dut.io.osValidOut.peek().litToBoolean == expValidOut, s"$m validOut")
        assert(dut.io.osLastOut.peek().litToBoolean == expLastOut, s"$m osLastOut")
        assert(dut.io.osDone.peek().litToBoolean == expDone, s"$m osDone")
        assert(dut.io.osConsume.peek().litToBoolean == consume, s"$m osConsume")
        beat += 1
      }

      osStep(0, 0, valid = false, advance = false, last = false, seedEn = false, seed = 0, clr = true) // clear 复位
      osStep(0, 0, valid = false, advance = true,  last = false, seedEn = true,  seed = 100, clr = false) // seed=100（advance 但无 token）
      for (_ <- 1 to 5) osStep(1, 1, valid = true, advance = true, last = false, seedEn = false, seed = 0, clr = false) // 5×1×1
      for (_ <- 1 to 3) osStep(9, 1, valid = true, advance = false, last = false, seedEn = false, seed = 0, clr = false) // 暂停
      osStep(2, 3, valid = true, advance = true, last = false, seedEn = false, seed = 0, clr = false) // +6
      osStep(4, 5, valid = true, advance = true, last = false, seedEn = false, seed = 0, clr = false) // +20
      osStep(0, 0, valid = true, advance = true, last = true,  seedEn = false, seed = 0, clr = false) // last → done
      osStep(0, 0, valid = true, advance = false, last = false, seedEn = false, seed = 0, clr = false) // 暂停：done/last 保持
      osStep(0, 0, valid = true, advance = true, last = false, seedEn = false, seed = 0, clr = true) // clear 竞争
      osStep(1, 1, valid = true, advance = true, last = false, seedEn = true,  seed = 77, clr = false) // seed 竞争（优先级胜 consume）
      for (_ <- 1 to 3) osStep(5, 0, valid = true, advance = true, last = false, seedEn = false, seed = 0, clr = false) // osB=0 链段：psum 不变
      println(s"[WbOsM1PeSpec] OS INT8 scripted PASS $beat beats")
    }
  }

  behavior of "WbWorkerOsM1 OS-vs-WS differential"

  it should "match wsPe accumulation for INT8 / MXE4M3 / BF16 (B/scaleB/cReg mux wiring)" in {
    val cases: Seq[(Set[DataFormat.Type], DataFormat.Type, Int, String)] = Seq(
      (Set(DataFormat.INT8), DataFormat.INT8, 0x0012, "INT8"),
      (Set(DataFormat.MXE4M3, DataFormat.MXE2M1), DataFormat.MXE4M3, 0x3F3F, "MXE4M3"),
      (Set(DataFormat.BF16, DataFormat.MXE4M3, DataFormat.MXE2M1), DataFormat.BF16, 0x3F80, "BF16")
    )

    for ((fmts, fmt, w, tag) <- cases) {
      val rng = new Random(1000L + tag.hashCode)
      simulate(new WbOsM1PeHarness(supportedFmts = fmts)) { dut =>
        resetDut(dut)
        val sa = 127 // E8M0 2^0 中性 scale
        // L1 拍：确定性锁存 fmtReg/bFmtReg（无复位初值，须先于一切消费；WbFusionMacFp
        //   的 aIsMx*/isMx8/isMx4 为 RegNext，本拍锁存后其标志在装载拍边缘收敛）
        dut.io.osM1.poke(true.B)
        dut.io.aIn.poke(0.U)
        dut.io.psumIn.poke(0.U)
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVInLock.poke(false.B)
        dut.io.loadVIn.poke(false.B)
        dut.io.loadWIn.poke(false.B)
        dut.io.loadWInLock.poke(false.B)
        dut.io.validIn.poke(false.B)
        dut.io.validInV.poke(false.B)
        dut.io.fmtIn.poke(fmt)
        dut.io.bFmtIn.poke(fmt)
        dut.io.scaleAIn.poke(sa.U)
        dut.io.scaleBIn.poke(sa.U)
        dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.clear.poke(false.B)
        dut.io.osB.poke(w.U)
        dut.io.osScaleB.poke(sa.U)
        dut.io.osSeed.poke(0.U)
        dut.io.osSeedEn.poke(false.B)
        dut.io.osAdvance.poke(false.B)
        dut.io.osLastIn.poke(false.B)
        dut.clock.step(1)
        // L2 拍：装载 wsPe 的 wReg/scaleBReg；osPe 同拍静止（无消费/seed）
        dut.io.loadHIn.poke(false.B)
        dut.io.psumIn.poke(w.U)
        dut.io.loadVInLock.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        dut.clock.step(1)
        dut.io.loadVInLock.poke(false.B)
        dut.io.loadVIn.poke(false.B)

        var prev = dut.io.osPsumOut.peek().litValue
        var beat = 0
        while (beat < 12) {
          val aVal = tag match {
            case "INT8"   => rng.nextInt(1 << 16)
            case "MXE4M3" => rng.nextInt(64) | (rng.nextInt(64) << 8) // E4M3 有限正常数域
            case _        => 0x3C00 | rng.nextInt(1024)               // BF16 exp=0x78≠0xFF
          }
          dut.io.aIn.poke(aVal.U)
          dut.io.psumIn.poke(prev.U)
          dut.io.validIn.poke(true.B)
          dut.io.osAdvance.poke(true.B)
          dut.io.osB.poke(w.U)
          dut.clock.step(1)
          val osP = dut.io.osPsumOut.peek().litValue
          val wsP = dut.io.wsPsumOut.peek().litValue
          assert(osP == wsP,
            s"[diff $tag beat#$beat] osPsum=0x${osP.toString(16)} wsPsum=0x${wsP.toString(16)}")
          assert(dut.io.osAOut.peek().litValue == dut.io.wsAOut.peek().litValue, s"[diff $tag #$beat] aOut")
          assert(dut.io.osScaleAOut.peek().litValue == dut.io.wsScaleAOut.peek().litValue, s"[diff $tag #$beat] scaleAOut")
          assert(dut.io.osConsume.peek().litToBoolean, s"[diff $tag #$beat] osConsume should be 1")
          prev = osP
          beat += 1
        }
        // 末字拍：osDone 置位
        dut.io.aIn.poke(0.U)
        dut.io.psumIn.poke(prev.U)
        dut.io.osB.poke(w.U)
        dut.io.validIn.poke(true.B)
        dut.io.osAdvance.poke(true.B)
        dut.io.osLastIn.poke(true.B)
        dut.clock.step(1)
        assert(dut.io.osDone.peek().litToBoolean, s"[diff $tag] osDone should be set after last token")
        println(s"[WbOsM1PeSpec] differential $tag PASS $beat beats")
      }
    }
  }

  behavior of "WbWorkerOsM1 elaboration structure"

  it should "instantiate exactly one WbWorkUnit and emit the module" in {
    val sv = _root_.circt.stage.ChiselStage.emitSystemVerilog(new WbWorkerOsM1())
    val macInsts = sv.linesIterator.count(_.trim.startsWith("WbWorkUnit "))
    assert(macInsts == 1, s"expected exactly 1 WbWorkUnit instance, found $macInsts")
    assert(sv.contains("module WbWorkerOsM1("), "WbWorkerOsM1 module not emitted")
    println("[WbOsM1PeSpec] structural PASS: 1 WbWorkUnit instance")
  }
}
