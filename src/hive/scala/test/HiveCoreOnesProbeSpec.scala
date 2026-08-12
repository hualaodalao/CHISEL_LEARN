/** HiveCore 端到端探针：全 1 FP16 GEMM（M=N=K=16, totalN=16）
  *
  * 与 HiveCoreSimCase 相同的寄存器配置 + EXECUTE 流程，但 A/B 数据全为 FP16 1.0，
  * 期望 C = 16.0（fp32 0x41800000）。用于隔离「顶层数据路径/executor 时序」问题：
  * HiveComb 探针已证明阵列协议正确，此测试检验 bDma→B buffer→权重装载→
  * A skew 供数→累加→de-skew 收集→C store 的完整链路。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HiveCoreOnesProbeSpec extends AnyFlatSpec with Matchers with ChiselSim {

  behavior of "HiveCore all-ones FP16 GEMM probe"

  it should "produce C = fp32 16.0 everywhere" in {
    val cfg = HiveCoreConfig(
      arrayN = 8, clusterM = 2,
      aBufferDepth = 2048, cBufferDepth = 2048,
      aW = 16, bW = 16, cW = 32
    )
    val totalN = cfg.totalN
    val fp16One = 0x3C00
    // 切换 fmt 调试（与下方 sendCmd 处的 USE_INT16 保持一致）
    val USE_INT16 = false
    val oneElem = if (USE_INT16) 0x0001 else fp16One
    // 全 1 beat：低 totalN*16 位全部为 oneElem
    var onesBeat = BigInt(0)
    for (i <- 0 until totalN) onesBeat = onesBeat | (BigInt(oneElem) << (i * 16))

    simulate(new HiveCore(cfg)) { dut =>
      var dma0Sent = 0; val dma0Total = 16   // A 全 1，单 tile 16 beat
      var dma1Done = 0
      var dma2Sent = 0; val dma2Total = 16
      val storeVals = scala.collection.mutable.ArrayBuffer[BigInt]()

      dut.reset.poke(true.B)
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      // A 只读通道：cmd 常就绪，rsp 初始无效
      dut.io.dma0Ext.cmd.ready.poke(true.B)
      dut.io.dma0Ext.rsp.valid.poke(false.B)
      dut.io.dma0Ext.rsp.payload.data.poke(0.U)
      dut.io.dma0Ext.rsp.payload.rsp.poke(false.B)
      // C 写回通道：grant 关闭，writeData 不接收
      dut.io.dma1Ext.grant.poke(false.B)
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
      sendCmd(HiveCoreOp.REG_WRITE2, 6, 7, 0, 0)  // stride=0（tile 索引恒 0 不影响）, fmt=FP16 rnd=RNE
      // fmt 由顶部 USE_INT16 决定：INT16 时重写 regs(7)=2
      // 注意 REG_WRITE2 会同时写 reg0<-data0、reg1<-data1，故两地址同为 7 时
      // 必须 data0=data1=fmt，否则 data0 会覆盖 fmt
      if (USE_INT16) sendCmd(HiveCoreOp.REG_WRITE2, 7, 7, 2, 2)  // fmt=INT16

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
        // dma0: A load 全 1（cmd/rsp 顺序流模型，常量供数）
        if (dma0Sent < dma0Total) {
          dut.io.dma0Ext.rsp.valid.poke(true.B)
          dut.io.dma0Ext.rsp.payload.data.poke(onesBeat.U)
          dut.io.dma0Ext.rsp.payload.rsp.poke(false.B)
          if (dut.io.dma0Ext.rsp.ready.peek().litToBoolean) dma0Sent += 1
        } else {
          dut.io.dma0Ext.rsp.valid.poke(false.B)
        }
        // dma1: C store（逐 beat req/grant + writeData 握手）
        dut.io.dma1Ext.grant.poke(true.B)
        dut.io.dma1Ext.writeData.ready.poke(true.B)
        if (dut.io.dma1Ext.writeData.valid.peek().litToBoolean) {
          storeVals += dut.io.dma1Ext.writeData.payload.peek().litValue
          dma1Done += 1
        }
        // dma2: B 权重全 1
        if (dma2Sent < dma2Total) {
          dut.io.dma2Ext.rsp.valid.poke(true.B)
          dut.io.dma2Ext.rsp.payload.data.poke(onesBeat.U)
          if (dut.io.dma2Ext.rsp.ready.peek().litToBoolean) dma2Sent += 1
        } else {
          dut.io.dma2Ext.rsp.valid.poke(false.B)
        }
        if (dut.io.resp.valid.peek().litToBoolean && dut.io.resp.payload.done.peek().litToBoolean) {
          done = true
          println(f"[OnesProbe] cycle=$cycle EXECUTE done")
        }
        dut.clock.step()
        cycle += 1
      }

      done should be(true)
      // 每个 store beat 低 32 位（第 0 列）：FP16 期望 fp32 16.0；INT16 期望整数 16
      val elem0 = storeVals.head & ((BigInt(1) << 32) - 1)
      val expect = if (USE_INT16) BigInt(16) else BigInt("41800000", 16)
      println(s"[OnesProbe] store beats=${storeVals.size}, beat0 col0=0x${elem0.toString(16)} (expect 0x${expect.toString(16)})")
      elem0 should be(expect)
      // 全 1 数据下所有行所有列结果相同：校验每个 beat 的全部 totalN 列
      storeVals.size should be(16)
      for ((v, row) <- storeVals.zipWithIndex) {
        for (col <- 0 until totalN) {
          val e = (v >> (col * 32)) & ((BigInt(1) << 32) - 1)
          withClue(s"row=$row col=$col: 0x${e.toString(16)} != 0x${expect.toString(16)} ") {
            e should be(expect)
          }
        }
      }
    }
  }
}
