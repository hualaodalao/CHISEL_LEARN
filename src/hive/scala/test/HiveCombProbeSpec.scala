/** HiveComb 阵列协议探针测试（调试用）
  *
  * 目的：隔离验证「权重加载（loadV/loadVLock 广播协议）+ 计算（valid/fmt 传播）」
  * 在 HiveComb 层面是否产生正确累加。与 HiveCore 顶层/Executor 解耦。
  *
  * 方法：A 全 1、B 全 1、INT16 格式、K=16 → 每个输出应为 16。
  * 全 1 数据天然免疫权重下沉顺序/排列问题，若输出非 16 则阵列协议本身有缺陷。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ArrayBuffer

class HiveCombProbeSpec extends AnyFlatSpec with Matchers with ChiselSim {

  behavior of "HiveWorker single-PE probe"

  it should "load weight via loadVLock and accumulate INT16" in {
    simulate(new HiveWorker(aW = 16, bW = 16, cW = 32)) { dut =>
      dut.reset.poke(true.B)
      dut.io.aIn.poke(0.U)
      dut.io.psumIn.poke(0.U)
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.loadVLock.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.clear.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.INT16)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // 锁存配置：loadH 拉高
      dut.io.loadHIn.poke(true.B)
      dut.clock.step(2)
      dut.io.loadHIn.poke(false.B)

      // 锁存权重 wReg=3：loadVLock + psumIn=3
      dut.io.psumIn.poke(3.U)
      dut.io.loadVLock.poke(true.B)
      dut.clock.step()
      dut.io.loadVLock.poke(false.B)
      dut.io.psumIn.poke(0.U)
      // clear psumReg
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.clock.step()

      // 供数：aIn=5, valid=1。首拍 psumReg := 0 + 5*3 = 15，
      // 之后 valid 持续但乘积恒定、累加值保持（期望 15）
      dut.io.aIn.poke(5.U)
      dut.io.validIn.poke(true.B)
      for (t <- 0 until 6) {
        println(s"[WorkerProbe] t=$t psumOut=${dut.io.psumOut.peek().litValue} aOut=${dut.io.aOut.peek().litValue} validOut=${dut.io.validOut.peek().litToBoolean}")
        dut.clock.step()
      }
      dut.io.psumOut.peek().litValue should be(BigInt(15))
    }
  }

  behavior of "HiveCell probe"

  it should "accumulate through an 8x8 cell (INT16 all-ones partial)" in {
    simulate(new HiveCell(n = 8, aW = 16, bW = 16, cW = 32)) { dut =>
      val n = 8
      dut.reset.poke(true.B)
      for (i <- 0 until n) { dut.io.aIn(i).poke(0.U); dut.io.psumIn(i).poke(0.U) }
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.loadVLock.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.clear.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.INT16)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // 权重加载：8 行全 1，每行 2 拍
      for (r <- 0 until n) {
        for (y <- 0 until n) dut.io.psumIn(y).poke(1.U)
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        dut.io.loadVLock.poke(true.B)
        dut.clock.step()
        dut.io.loadVIn.poke(false.B)
        dut.io.loadVLock.poke(false.B)
        dut.clock.step()
      }
      for (_ <- 0 until (2 * n)) dut.clock.step()
      for (y <- 0 until n) dut.io.psumIn(y).poke(0.U)
      dut.io.loadHIn.poke(false.B)
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.clock.step(2)

      // 计算：aIn 全 1，valid 拉高
      dut.io.validIn.poke(true.B)
      for (i <- 0 until n) dut.io.aIn(i).poke(1.U)
      var last: BigInt = 0
      for (t <- 0 until 60) {
        val c0 = dut.io.cOut(0).peek().litValue
        val v0 = dut.io.validOut(0).peek().litToBoolean
        if (v0) last = c0
        if (t < 5 || (t % 10 == 0)) println(s"[CellProbe] t=$t v0=$v0 cOut(0)=$c0 cOut(7)=${dut.io.cOut(7).peek().litValue}")
        dut.clock.step()
      }
      println(s"[CellProbe] final valid cOut(0) = $last (expected K=$n)")
      last should be(BigInt(n))
    }
  }

  behavior of "HiveComb array protocol probe"

  it should "accumulate with clusterM=1 (single cell through HiveComb)" in {
    simulate(new HiveComb(arrayN = 8, clusterM = 1, aW = 16, bW = 16, cW = 32)) { dut =>
      val totalN = 8
      dut.reset.poke(true.B)
      for (i <- 0 until totalN) { dut.io.aIn(i).poke(0.U); dut.io.psumIn(i).poke(0.U) }
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.loadVLock.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.clear.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.INT16)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      for (r <- 0 until totalN) {
        for (y <- 0 until totalN) dut.io.psumIn(y).poke(1.U)
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        dut.io.loadVLock.poke(true.B)
        dut.clock.step()
        dut.io.loadVIn.poke(false.B)
        dut.io.loadVLock.poke(false.B)
        dut.clock.step()
      }
      for (_ <- 0 until (2 * totalN)) dut.clock.step()
      for (y <- 0 until totalN) dut.io.psumIn(y).poke(0.U)
      dut.io.loadHIn.poke(false.B)
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.clock.step(2)

      dut.io.validIn.poke(true.B)
      for (i <- 0 until totalN) dut.io.aIn(i).poke(1.U)
      var last: BigInt = 0
      for (t <- 0 until 80) {
        val c0 = dut.io.cOut(0).peek().litValue
        val v0 = dut.io.validOut(0).peek().litToBoolean
        if (v0) last = c0
        if (t < 3 || t % 20 == 0) println(s"[Comb1Probe] t=$t v0=$v0 cOut(0)=$c0")
        dut.clock.step()
      }
      println(s"[Comb1Probe] final valid cOut(0) = $last (expected K=$totalN)")
      last should be(BigInt(totalN))
    }
  }


  behavior of "HiveComb FP16 probe"

  it should "accumulate all-ones FP16 GEMM to fp32 16.0" in {
    simulate(new HiveComb(arrayN = 8, clusterM = 2, aW = 16, bW = 16, cW = 32)) { dut =>
      val totalN = 16
      val fp16One = 0x3C00  // FP16 1.0

      dut.reset.poke(true.B)
      for (i <- 0 until totalN) { dut.io.aIn(i).poke(0.U); dut.io.psumIn(i).poke(0.U) }
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.loadVLock.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.clear.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.FP16)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      for (r <- 0 until totalN) {
        for (y <- 0 until totalN) dut.io.psumIn(y).poke(fp16One.U)
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        dut.io.loadVLock.poke(true.B)
        dut.clock.step()
        dut.io.loadVIn.poke(false.B)
        dut.io.loadVLock.poke(false.B)
        dut.clock.step()
      }
      for (_ <- 0 until (2 * totalN)) dut.clock.step()
      for (y <- 0 until totalN) dut.io.psumIn(y).poke(0.U)
      dut.io.loadHIn.poke(false.B)
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.clock.step(2)

      dut.io.validIn.poke(true.B)
      for (i <- 0 until totalN) dut.io.aIn(i).poke(fp16One.U)
      var last: BigInt = 0
      for (t <- 0 until 120) {
        val c0 = dut.io.cOut(0).peek().litValue
        val v0 = dut.io.validOut(0).peek().litToBoolean
        if (v0) last = c0
        if (t % 20 == 0) println(s"[Fp16Probe] t=$t v0=$v0 cOut(0)=0x${c0.toString(16)}")
        dut.clock.step()
      }
      // FP32 16.0 = 0x41800000
      val cols = (0 until totalN).map(c => s"c$c=0x${dut.io.cOut(c).peek().litValue.toString(16)}").mkString(" ")
      println(s"[Fp16Probe] final window: $cols")
      println(s"[Fp16Probe] final valid cOut(0) = 0x${last.toString(16)} (expected 0x41800000 = fp32 16.0)")
      last should be(BigInt("41800000", 16))
    }
  }

  it should "accumulate all-ones INT16 GEMM to K=16" in {
    simulate(new HiveComb(arrayN = 8, clusterM = 2, aW = 16, bW = 16, cW = 32)) { dut =>
      val totalN = 16

      // ===== Reset =====
      dut.reset.poke(true.B)
      for (i <- 0 until totalN) {
        dut.io.aIn(i).poke(0.U)
        dut.io.psumIn(i).poke(0.U)
      }
      dut.io.loadHIn.poke(false.B)
      dut.io.loadVIn.poke(false.B)
      dut.io.loadVLock.poke(false.B)
      dut.io.validIn.poke(false.B)
      dut.io.clear.poke(false.B)
      dut.io.fmtIn.poke(DataFormat.INT16)
      dut.io.rndIn.poke(RoundingMode.RNE)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      // ===== 权重加载：16 行全 1，每行 2 拍（供数拍 loadV/loadVLock=1，保持拍 0）=====
      for (r <- 0 until totalN) {
        for (y <- 0 until totalN) dut.io.psumIn(y).poke(1.U)
        dut.io.loadHIn.poke(true.B)
        dut.io.loadVIn.poke(true.B)
        dut.io.loadVLock.poke(true.B)
        dut.clock.step()
        dut.io.loadVIn.poke(false.B)
        dut.io.loadVLock.poke(false.B)
        dut.clock.step()
      }
      // 排空：保持 psumIn=1（防止尾部 0 被锁存），等待 loadV 波排空
      for (_ <- 0 until (2 * totalN)) dut.clock.step()
      // clear psum 链
      dut.io.loadHIn.poke(false.B)
      dut.io.psumIn(0).poke(0.U) // 之后全部置 0
      for (y <- 0 until totalN) dut.io.psumIn(y).poke(0.U)
      dut.io.clear.poke(true.B)
      dut.clock.step()
      dut.io.clear.poke(false.B)
      dut.clock.step(2)

      // ===== 计算：valid=1，aIn 全 1（全 1 免疫 skew）=====
      dut.io.validIn.poke(true.B)
      for (i <- 0 until totalN) dut.io.aIn(i).poke(1.U)

      // 收集 cOut(0)/validOut(0)，共跑 16(供数) + 2*15(波传播) + 15(排空) + 余量
      val samples = ArrayBuffer[(Int, BigInt)]()
      for (t <- 0 until 120) {
        val v0 = dut.io.validOut(0).peek().litToBoolean
        if (v0) samples += ((t, dut.io.cOut(0).peek().litValue))
        dut.clock.step()
      }
      dut.io.validIn.poke(false.B)

      println(s"[Probe] valid-window samples on cOut(0): ${samples.size}")
      samples.foreach { case (t, v) => println(s"[Probe]   t=$t cOut(0)=$v") }
      samples should not be empty
      // 所有 valid 拍的 cOut(0) 最终应稳定为 K=16
      samples.last._2 should be(BigInt(16))
    }
  }
}
