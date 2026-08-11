/** HiveCombGemmDiagSpec — 用真实 HiveCoreExecutor 硬件驱动 HiveComb 的
  * 数值正确性测试（DiagExeTop 最小顶层：DMA tie-off，A/B/C 走测试侧
  * 软件 FIFO），与 HiveCore 端到端计算路径一致，覆盖：
  *   - 权重经 B buffer 流（bPop）加载：降序供数 + loadV/loadVLock 同拍锁存
  *   - aRegs skew 供数 + valid 窗口 curTileM + 2*(totalN-1)
  *   - cDeSkewed + alignedValid(delay=totalN) 流式整行收集
  *   - 多 M tile（weight-stationary 权重复用）
  *
  * 注意：cPush.payload 必须在 step 前采样；step 后 peek 会拿到下一拍
  * payload（cPush 每拍推一行 → 表现为整体错位一行、末行 0）。
  */

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** executor 硬件直驱 HiveComb 的最小测试顶层（testbench 内部用）：
  * 自主 DMA 接口 tie-off（aDmaRescan/cStoreGate 丢弃，occupancy 由测试侧
  * 按软件 FIFO 驱动），A/B/C 用测试侧软件 FIFO */
class DiagExeTop(cfg: HiveCoreConfig) extends Module {
  val io = IO(new Bundle {
    val execute = Input(Bool())
    val done    = Output(Bool())
    val regFile = Input(HiveCoreRegs(cfg))
    val aOccupancy = Input(UInt(log2Up(cfg.aBufferDepth + 1).W))
    val cOccupancy = Input(UInt(log2Up(cfg.cBufferDepth + 1).W))
    val bOccupancy = Input(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val aPop    = Flipped(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    val bPop    = Flipped(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val cPush   = Stream(UInt((cfg.totalN * cfg.cEffW).W))
  })
  val exe  = Module(new HiveCoreExecutor(cfg))
  val hive = Module(new HiveComb(cfg.arrayN, cfg.clusterM, cfg.aW, cfg.bW, cfg.cW, cfg.supportedFmts))

  exe.io.execute := io.execute
  exe.io.regFile := io.regFile

  // 自主 DMA tie-off：aDmaRescan/cStoreGate 无消费者（留空），
  // occupancy 由测试侧按软件 FIFO 模型驱动
  exe.io.aOccupancy := io.aOccupancy
  exe.io.cOccupancy := io.cOccupancy
  exe.io.bOccupancy := io.bOccupancy

  exe.io.aPop.valid   := io.aPop.valid
  io.aPop.ready       := exe.io.aPop.ready
  exe.io.aPop.payload := io.aPop.payload
  exe.io.bPop.valid   := io.bPop.valid
  io.bPop.ready       := exe.io.bPop.ready
  exe.io.bPop.payload := io.bPop.payload
  exe.io.cPop.valid := false.B
  exe.io.cPop.payload := 0.U
  io.cPush.valid   := exe.io.cPush.valid
  exe.io.cPush.ready := io.cPush.ready
  io.cPush.payload := exe.io.cPush.payload

  hive.io.aIn    <> exe.io.hiveAIn
  hive.io.psumIn <> exe.io.hivePsumIn
  hive.io.loadHIn := exe.io.hiveLoadH
  hive.io.loadVIn := exe.io.hiveLoadV
  hive.io.loadVLock := exe.io.hiveLoadVLock
  hive.io.validIn := exe.io.hiveValidIn
  hive.io.fmtIn   := exe.io.hiveFmtIn
  hive.io.rndIn   := exe.io.hiveRndIn
  hive.io.clear   := exe.io.hiveClear
  exe.io.hiveCOut    := hive.io.cOut
  exe.io.hiveValidOut := hive.io.validOut

  io.done := exe.io.done
}

class HiveCombGemmDiagSpec extends AnyFlatSpec with Matchers {

  behavior of "HiveComb under Executor-style drive protocol"

  it should "compute exact 32x16x16 FP16 GEMM (2 M tiles) via executor hardware" in {
    // 决定性实验：用真实 HiveCoreExecutor 硬件驱动 HiveComb（DMA tie-off，
    // A/B/C 走测试侧软件 FIFO），与 HiveCore 端到端路径一致。若此路径通过而
    // 软件直驱失败，则说明直驱协议建模有误；若此路径也失败，则为硬件问题。
    val cfg = HiveCoreConfig()
    val totalN = cfg.totalN
    val mRows = 2 * totalN  // M=32：两个 M tile，区分 tile 间相位

    // 激活 / 权重：小的可区分整数；本用例用 FP16 编码（与 SimCase 格式一致，
    // 用于隔离「格式/相位」差异；值域内 FP16 精确表示）
    def f2h(f: Float): Int = {
      val bits = java.lang.Float.floatToIntBits(f)
      val sign = (bits >>> 16) & 0x8000
      var exp = ((bits >>> 23) & 0xFF) - 127 + 15
      val man = (bits >>> 13) & 0x3FF
      if (exp <= 0) 0 else if (exp >= 0x1F) 0x7BFF else sign | (exp << 10) | man
    }
    def h2f(h: Int): Float = {
      val sign = (h & 0x8000) << 16
      val exp = (h >>> 10) & 0x1F
      val man = h & 0x3FF
      if (exp == 0) java.lang.Float.intBitsToFloat(sign)
      else java.lang.Float.intBitsToFloat(sign | ((exp - 15 + 127) << 23) | (man << 13))
    }
    def bits2f(b: Int): Float = java.lang.Float.intBitsToFloat(b)

    val A = Array.tabulate(mRows, totalN)((i, k) => ((i + 1) + k).toLong)
    val B = Array.tabulate(totalN, totalN)((k, j) => ((k + 1) * 2 + j).toLong)
    val Cexp = Array.tabulate(mRows, totalN)((i, j) =>
      (0 until totalN).map(k => A(i)(k).toDouble * B(k)(j).toDouble).sum)

    val aQueue = scala.collection.mutable.Queue[BigInt]()
    for (m <- 0 until mRows) {
      var data = BigInt(0)
      for (k <- 0 until totalN) data |= BigInt(f2h(A(m)(k).toFloat)) << (k * cfg.aEffW)
      aQueue += data
    }
    // 权重行数据（降序），M tile 间权重重新加载，bQueue 耗尽时重新填充
    val bQueue = scala.collection.mutable.Queue[BigInt]()
    val bRowsDesc = (0 until totalN).map { beat =>
      val k = totalN - 1 - beat
      var data = BigInt(0)
      for (i <- 0 until totalN) data |= BigInt(f2h(B(k)(i).toFloat)) << (i * cfg.bW)
      data
    }
    bQueue ++= bRowsDesc
    val collected = scala.collection.mutable.ArrayBuffer[BigInt]()

    simulate(new DiagExeTop(cfg)) { dut =>
      // === 初始化 ===
      dut.reset.poke(true.B)
      dut.io.execute.poke(false.B)
      dut.io.aOccupancy.poke(0.U)
      dut.io.cOccupancy.poke(0.U)
      dut.io.bOccupancy.poke(0.U)
      dut.io.aPop.valid.poke(false.B); dut.io.aPop.payload.poke(0.U)
      dut.io.bPop.valid.poke(false.B); dut.io.bPop.payload.poke(0.U)
      dut.io.cPush.ready.poke(true.B)
      // 寄存器组：M=N=K=16，fmt=INT16(2)，rnd=RNE(0)，其余 0
      for (i <- 0 until cfg.registerNum) dut.io.regFile.regs(i).poke(0.U)
      dut.io.regFile.regs(0).poke(mRows.U)   // M
      dut.io.regFile.regs(1).poke(16.U)   // N
      dut.io.regFile.regs(2).poke(16.U)   // K
      dut.io.regFile.regs(7).poke(0.U)    // fmt=FP16, rnd=RNE
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // EXECUTE 单拍脉冲
      dut.io.execute.poke(true.B); dut.clock.step()
      dut.io.execute.poke(false.B)

      var cycle = 0
      var done = false
      while (!done && cycle < 5000) {
        // B FIFO 供数
        if (bQueue.nonEmpty) {
          dut.io.bPop.valid.poke(true.B)
          dut.io.bPop.payload.poke(bQueue.head.U)
        } else {
          dut.io.bPop.valid.poke(false.B)
        }
        dut.io.bOccupancy.poke(bQueue.size.U)
        // A FIFO 供数
        if (aQueue.nonEmpty) {
          dut.io.aPop.valid.poke(true.B)
          dut.io.aPop.payload.poke(aQueue.head.U)
        } else {
          dut.io.aPop.valid.poke(false.B)
        }
        dut.io.cPush.ready.poke(true.B)
        // occupancy 按软件 FIFO 模型驱动：aQueue 剩余 = A buffer 占用（executor
        // 在 sLOAD_A_DMA 等待 aOccupancy >= thisTileM）；cOccupancy 恒 0（cPush
        // 直采走，sSTORE_C_DMA 等 cOccupancy===0 立即通过，单 K pass 无 partial sum）
        dut.io.aOccupancy.poke(aQueue.size.U)
        dut.io.cOccupancy.poke(0.U)

        // fire 判定（step 前采样握手）；cPush.payload 必须在 step 前采样，
        // 否则拿到的是下一拍 payload（cPush 每拍换一行 → 整体错位一行、末行 0）
        val bFire = dut.io.bPop.valid.peek().litToBoolean && dut.io.bPop.ready.peek().litToBoolean
        val aFire = dut.io.aPop.valid.peek().litToBoolean && dut.io.aPop.ready.peek().litToBoolean
        val cFire = dut.io.cPush.valid.peek().litToBoolean && dut.io.cPush.ready.peek().litToBoolean
        val cPayload: Option[BigInt] =
          if (cFire) Some(dut.io.cPush.payload.peek().litValue) else None
        val finished = dut.io.done.peek().litToBoolean

        dut.clock.step()
        cycle += 1
        if (bFire && bQueue.nonEmpty) bQueue.dequeue()
        if (aFire && aQueue.nonEmpty) aQueue.dequeue()
        cPayload.foreach(collected += _)
        if (finished) done = true
      }
      println(s"[DIAG-EXE] finished=$done cycle=$cycle collected=${collected.size} rows")
    }

    collected.size shouldBe mRows
    def rowOf(bits: BigInt, j: Int): Long =
      ((bits >> (j * cfg.cEffW)) & ((BigInt(1) << cfg.cEffW) - 1)).toLong
    def rowF(bits: BigInt, j: Int): Double =
      bits2f(rowOf(bits, j).toInt).toDouble
    var mismatch = 0
    var firstErr = ""
    for (i <- 0 until mRows; j <- 0 until totalN) {
      val act = rowF(collected(i), j)
      val exp = Cexp(i)(j)
      val absErr = math.abs(act - exp)
      val relErr = if (math.abs(exp) > 1e-6) absErr / math.abs(exp) else absErr
      if (relErr > 1e-2 && absErr > 1e-2) {
        if (firstErr.isEmpty)
          firstErr = f"row=$i col=$j expected=$exp%.2f actual=$act%.2f"
        mismatch += 1
      }
    }
    if (firstErr.nonEmpty) {
      println(s"[DIAG-EXE] first mismatch: $firstErr, mismatches=$mismatch")
      for (i <- Seq(0, 1, 15, 16, 17, 30, 31).filter(_ < mRows)) {
        println(f"[DIAG-EXE] row $i expected: ${Cexp(i).take(4).map(v => f"$v%.0f").mkString(",")},...")
        println(f"[DIAG-EXE] row $i actual  : ${(0 until 4).map(j => f"${rowF(collected(i), j)}%.0f").mkString(",")},...")
      }
    }
    mismatch shouldBe 0
  }
}
