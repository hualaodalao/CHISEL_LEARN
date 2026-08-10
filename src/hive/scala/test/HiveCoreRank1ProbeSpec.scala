/** HiveCore 端到端探针：秩-1 GEMM（M=N=K=16, totalN=16, FP16）
  *
  * A[i][k] = i+1（行常数），B[k][j] = j+1（列常数）
  * → C[i][j] = Σ_k (i+1)(j+1) = 16·(i+1)·(j+1)（fp32 精确可表示）
  *
  * 设计意图：
  *   - A 行常数 → 行内 skew/元素配对错误不可见，隔离「行级时序与收集」
  *   - B 列常数 → 权重 x 方向反转（PE(x)=B[15-x][y]）不可见
  *   若本测试失败，问题在行级 valid/供数/收集；若通过，问题在行内元素配对。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HiveCoreRank1ProbeSpec extends AnyFlatSpec with Matchers with ChiselSim {

  behavior of "HiveCore rank-1 GEMM probe"

  def fp32ToFloat(bits: BigInt): Float =
    java.lang.Float.intBitsToFloat((bits & 0xFFFFFFFFL).toInt)

  def floatToFp16Bits(f: Float): Int = {
    // 小整数精确转换（1..16 均可精确表示）
    val bits = java.lang.Float.floatToIntBits(f)
    val exp = (bits >>> 23) & 0xFF
    val mant = bits & 0x7FFFFF
    val newExp = exp - 127 + 15
    ((newExp << 10) | (mant >> 13)) & 0x7FFF
  }

  it should "produce C[i][j] = 16*(i+1)*(j+1)" in {
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2, extDataWidth = 576,
      aBufferDepth = 2048, cBufferDepth = 2048,
      aW = 16, bW = 16, cW = 32
    )
    val totalN = cfg.totalN

    // A beat：行 i 全为 (i+1)
    def aBeat(row: Int): BigInt = {
      var d = BigInt(0)
      val v = floatToFp16Bits((row + 1).toFloat)
      for (i <- 0 until totalN) d = d | (BigInt(v) << (i * 16))
      d
    }
    // B beat：任意权重行均为 [1,2,...,16]
    val bBeatVal: BigInt = {
      var d = BigInt(0)
      for (j <- 0 until totalN) d = d | (BigInt(floatToFp16Bits((j + 1).toFloat)) << (j * 16))
      d
    }

    simulate(new HiveCore(cfg)) { dut =>
      var dma0Active = false; var dma0Len = 0; var dma0Done = 0
      var dma1Active = false; var dma1Len = 0; var dma1Done = 0
      var dma2Sent = 0; val dma2Total = 16
      val storeVals = scala.collection.mutable.ArrayBuffer[BigInt]()

      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.dma0Ext.grant.poke(false.B)
      dut.io.dma1Ext.grant.poke(false.B)
      dut.io.dma0Ext.readData.valid.poke(false.B)
      dut.io.dma0Ext.readData.payload.poke(0.U)
      dut.io.dma1Ext.readData.valid.poke(false.B)
      dut.io.dma1Ext.writeData.ready.poke(false.B)
      dut.io.dma1Ext.readData.payload.poke(0.U)
      dut.io.dma2Ext.cmd.ready.poke(true.B)
      dut.io.dma2Ext.rsp.valid.poke(false.B)
      dut.io.dma2Ext.rsp.payload.data.poke(0.U)
      dut.io.dma2Ext.rsp.payload.rsp.poke(false.B)
      dut.clock.step(3)
      dut.reset.poke(false.B)
      dut.clock.step(2)

      def sendCmd(op: HiveCoreOp.Type, a0: Int, a1: Int, d0: Long, d1: Long): Unit = {
        dut.io.cmd.valid.poke(true.B)
        dut.io.cmd.payload.op.poke(op)
        dut.io.cmd.payload.reg0Addr.poke(a0.U)
        dut.io.cmd.payload.reg1Addr.poke(a1.U)
        dut.io.cmd.payload.data0.poke(d0.U)
        dut.io.cmd.payload.data1.poke(d1.U)
        dut.clock.step()
        dut.io.cmd.valid.poke(false.B)
        dut.clock.step(2)
      }

      sendCmd(HiveCoreOp.REG_WRITE2, 0, 1, 16, 16)
      sendCmd(HiveCoreOp.REG_WRITE2, 2, 3, 16, 0)
      sendCmd(HiveCoreOp.REG_WRITE2, 4, 5, 0x100000, 0x200000)
      sendCmd(HiveCoreOp.REG_WRITE2, 6, 7, 0, 0)  // fmt=FP16 rnd=RNE

      dut.io.cmd.valid.poke(true.B)
      dut.io.cmd.payload.op.poke(HiveCoreOp.EXECUTE)
      dut.io.cmd.payload.reg0Addr.poke(0.U)
      dut.io.cmd.payload.reg1Addr.poke(0.U)
      dut.io.cmd.payload.data0.poke(0.U)
      dut.io.cmd.payload.data1.poke(0.U)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B)

      var done = false
      var cycle = 0
      while (!done && cycle < 5000) {
        // dma0: A load，行 i 全为 (i+1)
        if (!dma0Active) {
          if (dut.io.dma0Ext.req.peek().litToBoolean) {
            dma0Len = dut.io.dma0Ext.len.peek().litValue.toInt
            dma0Done = 0; dma0Active = true
            dut.io.dma0Ext.grant.poke(true.B)
          }
        } else {
          dut.io.dma0Ext.grant.poke(false.B)
          if (dma0Done < dma0Len) {
            dut.io.dma0Ext.readData.valid.poke(true.B)
            dut.io.dma0Ext.readData.payload.poke(aBeat(dma0Done).U)
            if (dut.io.dma0Ext.readData.ready.peek().litToBoolean) dma0Done += 1
          } else {
            dut.io.dma0Ext.readData.valid.poke(false.B)
            dma0Active = false
          }
        }
        // dma1: C store
        if (!dma1Active) {
          if (dut.io.dma1Ext.req.peek().litToBoolean) {
            dma1Len = dut.io.dma1Ext.len.peek().litValue.toInt
            dma1Done = 0; dma1Active = true
            dut.io.dma1Ext.grant.poke(true.B)
          }
        } else {
          dut.io.dma1Ext.grant.poke(false.B)
          if (dma1Done < dma1Len) {
            dut.io.dma1Ext.writeData.ready.poke(true.B)
            if (dut.io.dma1Ext.writeData.valid.peek().litToBoolean) {
              storeVals += dut.io.dma1Ext.writeData.payload.peek().litValue
              dma1Done += 1
            }
          } else {
            dut.io.dma1Ext.writeData.ready.poke(false.B)
            dma1Active = false
          }
        }
        // dma2: B 权重 [1..16]
        if (dma2Sent < dma2Total) {
          dut.io.dma2Ext.rsp.valid.poke(true.B)
          dut.io.dma2Ext.rsp.payload.data.poke(bBeatVal.U)
          if (dut.io.dma2Ext.rsp.ready.peek().litToBoolean) dma2Sent += 1
        } else {
          dut.io.dma2Ext.rsp.valid.poke(false.B)
        }
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          done = true
        }
        dut.clock.step()
        cycle += 1
      }

      done should be(true)
      storeVals.size should be(16)

      // 解析：beat = 行 i，cEffW=32-bit slot × 16（cfg.cW=32）
      var mismatches = 0
      for (i <- 0 until 16) {
        val hwRow = (0 until totalN).map { j =>
          val bits = (storeVals(i) >> (j * 32)) & ((BigInt(1) << 32) - 1)
          fp32ToFloat(bits)
        }
        val expRow = (0 until totalN).map(j => 16.0f * (i + 1) * (j + 1))
        val hwStr = hwRow.take(5).map(v => f"$v%.1f").mkString(" ")
        val exStr = expRow.take(5).map(v => f"$v%.1f").mkString(" ")
        if (hwRow != expRow) {
          mismatches += 1
          // 贡献数 = hw / ((i+1)*(j+1))，应为 16；揭示缺失了多少 k 累加
          val cntStr = (0 until totalN).map { j =>
            val c = hwRow(j) / ((i + 1) * (j + 1))
            f"$c%.1f"
          }.mkString(" ")
          println(f"[Rank1Probe] row$i counts: $cntStr")
        }
      }
      println(s"[Rank1Probe] mismatches=$mismatches/16")
      mismatches should be(0)
    }
  }
}
