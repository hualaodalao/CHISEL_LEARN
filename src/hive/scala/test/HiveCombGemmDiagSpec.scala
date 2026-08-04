/** HiveCombGemmDiagSpec — 复现 HiveCoreExecutor 的驱动协议，隔离验证
  * HiveComb 在「逐深度不同权重 + aRegs 式外部 skew + 延长 valid + de-skew 收集」
  * 下能否得到精确 GEMM 结果（INT16，无浮点歧义）。
  *
  * 与 SystolicArrayTest 的差别：权重不再是全阵列标量，而是 W[k][g] = B[k][g]，
  * 且激励时序完全照搬 HiveCoreExecutor（aRegs +1 偏移、valid 窗口
  * curTileM + 2*(totalN-1)、de-skew 对齐、alignedValid delay totalN）。
  */

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HiveCombGemmDiagSpec extends AnyFlatSpec with Matchers {

  behavior of "HiveComb under Executor-style drive protocol"

  it should "compute exact 16x16 INT16 GEMM with per-depth weights" in {
    val arrayN = 8
    val clusterM = 2
    val totalN = arrayN * clusterM   // 16
    val mRows = totalN               // 16 行激活

    // 激活 / 权重：小的可区分整数
    val A = Array.tabulate(mRows, totalN)((i, k) => ((i + 1) + k).toLong)
    val B = Array.tabulate(totalN, totalN)((k, j) => ((k + 1) * 2 + j).toLong)
    val Cexp = Array.tabulate(mRows, totalN)((i, j) =>
      (0 until totalN).map(k => A(i)(k) * B(k)(j)).sum)

    // aRegs 式 skew：行 m 于拍 m 进入 aRegs(0)，每拍右移
    val aRegs = Array.fill(totalN)(Array.fill(totalN)(0L))

    val collected = scala.collection.mutable.ArrayBuffer[Array[BigInt]]()

    simulate(new HiveComb(arrayN, clusterM, 16, 16, 32, Set(DataFormat.INT16))) { dut =>
      // === 初始化 ===
      dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.INT16); dut.io.rndIn.poke(RoundingMode.RNE)
      dut.io.clear.poke(false.B)
      for (i <- 0 until totalN) dut.io.aIn(i).poke(0.U)
      for (j <- 0 until totalN) dut.io.psumIn(j).poke(0.U)
      dut.clock.step()

      // === 权重加载（照搬 Executor sLOAD_WEIGHT_PE 行式协议）===
      // 每权重行 2 拍：偶数拍供行 k（psumIn(g)=B[k][g]）+ loadV 脉冲，奇数拍保持
      val loadCycles = 2 * totalN + totalN
      for (c <- 0 until loadCycles) {
        val beat = c / 2
        val feeding = (c % 2 == 0) && beat < totalN
        dut.io.loadVIn.poke(feeding.B)
        dut.io.loadHIn.poke(true.B)
        dut.io.fmtIn.poke(DataFormat.INT16); dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.validIn.poke(false.B)
        dut.io.clear.poke(false.B)
        for (i <- 0 until totalN) dut.io.aIn(i).poke(0.U)
        for (g <- 0 until totalN) {
          val w = if (feeding) B(beat)(g) else 0L
          dut.io.psumIn(g).poke(((w & 0xFFFFL).U))
        }
        dut.clock.step()
      }
      // 排空完成当拍：clear 一拍（照搬 Executor）
      dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
      for (g <- 0 until totalN) dut.io.psumIn(g).poke(0.U)
      dut.io.clear.poke(true.B); dut.clock.step()
      dut.io.clear.poke(false.B); dut.clock.step()

      // === 计算（照搬 sCOMPUTE：aRegs skew + 延长 valid）===
      val validCycles = mRows + 2 * (totalN - 1)
      val totalCycles = validCycles + (totalN - 1)

      // de-skew 历史缓冲（软件侧 ShiftRegister 等效）
      val cHist = Array.fill(totalN)(scala.collection.mutable.Queue[BigInt]())
      val vHist = scala.collection.mutable.Queue[Boolean]()
      var alignCnt = 0

      for (t <- 0 until totalCycles + 4) {
        // 先用上一拍末的 aRegs 状态驱动 aIn（等效 Executor：aIn(i)@t = 行 t-1-i），
        // 再移位并把行 t 注入 aRegs(0)
        dut.io.validIn.poke((t < validCycles).B)
        dut.io.fmtIn.poke(DataFormat.INT16); dut.io.rndIn.poke(RoundingMode.RNE)
        dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
        dut.io.clear.poke(false.B)
        // hiveAIn(i) = aRegs(i) 的第 i 个槽位（与 Executor 一致：+1 偏移）
        for (i <- 0 until totalN) dut.io.aIn(i).poke((aRegs(i)(i) & 0xFFFFL).U)
        for (g <- 0 until totalN) dut.io.psumIn(g).poke(0.U)

        for (i <- totalN - 1 to 1 by -1) aRegs(i) = aRegs(i - 1)
        for (k <- 0 until totalN) aRegs(0)(k) = if (t < mRows) A(t)(k) else 0L

        dut.clock.step()

        // 采样并软件 de-skew：列 c 延迟 (totalN-1-c)，
        // validOut(0) 升始于拍 15（ci skew 8 + y 链 7），行 m 数据于拍 17+m+g
        // 就绪于列 g，de-skew 后对齐于拍 m+32，故 alignedValid 延迟 totalN-1
        for (c <- 0 until totalN) {
          val d = totalN - 1 - c
          cHist(c).enqueue(dut.io.cOut(c).peek().litValue)
          if (cHist(c).size > d + 1) cHist(c).dequeue()
        }
        vHist.enqueue(dut.io.validOut(0).peek().litToBoolean)
        if (vHist.size > totalN) vHist.dequeue()

        val alignedValid = vHist.head
        if (alignedValid) {
          if (alignCnt < mRows) {
            collected += cHist.map(_.head).toArray
          }
          alignCnt += 1
        }
      }
    }

    collected.size shouldBe mRows
    var maxDiff = 0L
    var firstErr = ""
    for (i <- 0 until mRows; j <- 0 until totalN) {
      val act = collected(i)(j).toLong
      val exp = Cexp(i)(j)
      if (act != exp && firstErr.isEmpty)
        firstErr = f"row=$i col=$j expected=$exp actual=$act"
      maxDiff = math.max(maxDiff, math.abs(act - exp))
    }
    if (firstErr.nonEmpty) {
      println(s"[DIAG] first mismatch: $firstErr, maxDiff=$maxDiff")
      for (i <- Seq(0, 1, 8, 15)) {
        println(s"[DIAG] row $i expected: ${Cexp(i).mkString(",")}")
        println(s"[DIAG] row $i actual  : ${collected(i).map(_.toString).mkString(",")}")
      }
    }
    // 首次失败时转储时序波形帮助定位（列就绪拍探测）
    if (maxDiff != 0L) dumpSingleRowWaveform()
    maxDiff shouldBe 0L
  }

  /** 单行激励探测：只喂激活行 5（权重全 1），逐拍打印 cOut，
    * 实测每列承载该行完整和的首拍，校准 de-skew 延迟模型 */
  private def dumpSingleRowWaveform(): Unit = {
    val arrayN = 8; val clusterM = 2; val totalN = arrayN * clusterM
    val rowIdx = 5
    simulate(new HiveComb(arrayN, clusterM, 16, 16, 32, Set(DataFormat.INT16))) { dut =>
      dut.io.loadHIn.poke(false.B); dut.io.loadVIn.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.INT16); dut.io.rndIn.poke(RoundingMode.RNE)
      dut.io.clear.poke(false.B)
      for (i <- 0 until totalN) dut.io.aIn(i).poke(0.U)
      for (j <- 0 until totalN) dut.io.psumIn(j).poke(0.U)
      dut.clock.step()
      // 全 1 权重（行式协议：每行 2 拍，行内容全 1）
      for (k <- 0 until totalN) {
        dut.io.loadHIn.poke(true.B)
        for (g <- 0 until totalN) dut.io.psumIn(g).poke(1.U)
        dut.io.loadVIn.poke(true.B); dut.clock.step()
        dut.io.loadVIn.poke(false.B); dut.clock.step()
      }
      dut.io.loadHIn.poke(false.B)
      for (g <- 0 until totalN) dut.io.psumIn(g).poke(0.U)
      dut.io.clear.poke(true.B); dut.clock.step()
      dut.io.clear.poke(false.B); dut.clock.step()

      // 只喂一行：aIn(i) 在拍 (rowIdx+1+i) 给 100（Executor +1 相位）
      val aRegs = Array.fill(totalN)(0L)
      for (t <- 0 until 90) {
        dut.io.validIn.poke((t < 70).B)
        for (i <- 0 until totalN) dut.io.aIn(i).poke((aRegs(i) & 0xFFFFL).U)
        for (g <- 0 until totalN) dut.io.psumIn(g).poke(0.U)
        for (i <- totalN - 1 to 1 by -1) aRegs(i) = aRegs(i - 1)
        aRegs(0) = if (t == rowIdx) 100L else 0L
        dut.clock.step()
        val co = (0 until totalN).map(c => dut.io.cOut(c).peek().litValue.toLong)
        val vo = (0 until totalN).map(c => if (dut.io.validOut(c).peek().litToBoolean) "1" else "0").mkString
        if (co.exists(_ != 0) || t > 60)
          println(f"[WAVE] t=$t%3d v=$vo c=${co.mkString(",")}")
      }
    }
  }
}
