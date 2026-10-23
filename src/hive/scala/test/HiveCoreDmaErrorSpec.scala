import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** 顶层真实 DMA rsp.fire 错误路由验证，特别区分 scaleA/bias/rqM 的共享物理端口。 */
class HiveCoreDmaErrorSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveStatusPollApi {
  import HiveSimCommon._
  private val cfg = HiveCoreConfig(cW = 40, aBufferDepth = 256,
    bBufferDepth = 256, cBufferDepth = 256, scaleBufferDepth = 64)
  private val aBase = 0x10000L
  private val bBase = 0x20000L
  private val cBase = 0x30000L
  private val saBase = 0x40000L
  private val sbBase = 0x50000L
  private val scBase = 0x60000L
  private val biasBase = 0x70000L
  private val rqBase = 0x80000L
  private val sources = Seq("A", "B", "C", "scaleA", "scaleB", "scaleC", "bias", "rqM")

  behavior of "HiveCore DMA error interrupts"

  for ((name, source) <- sources.zipWithIndex) {
    it should s"record $name early, retain inFlight, and finish with both error and done pending" in {
      simulate(new HiveCore(cfg)) { dut =>
        val reads = Seq(dut.io.dmaA, dut.io.dmaB, dut.io.dmaScaleA.get, dut.io.dmaScaleB.get)
        val writes = Seq(dut.io.dmaC, dut.io.dmaCScale.get)
        def quiesce(ready: Boolean): Unit = {
          reads.foreach { p =>
            p.req.ready.poke(ready.B); p.rsp.valid.poke(false.B)
            p.rsp.payload.data.poke(0.U); p.rsp.payload.err.poke(false.B)
          }
          writes.foreach { p =>
            p.req.ready.poke(ready.B); p.rsp.valid.poke(false.B); p.rsp.payload.err.poke(false.B)
          }
        }
        quiesce(ready = true)
        dut.io.cmd.valid.poke(false.B); dut.io.resp.ready.poke(true.B)
        dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
        def wr(a: Int, d: BigInt): Unit = {
          transactReg(dut.clock, dut.io.cmd, dut.io.resp, HiveCoreOp.REG_WRITE1, a, d0 = d)
        }
        def rd(a: Int): BigInt = readRegister(dut.clock, dut.io.cmd, dut.io.resp, a)
        val intRun = source == 7
        val biasOn = source >= 6
        val inFmt = if (intRun) DataFormat.INT8 else DataFormat.MXE4M3
        val outFmt = if (intRun) DataFormat.INT8 else DataFormat.MXE4M3
        val control = (BigInt(1) << 28) | inFmt.litValue | (inFmt.litValue << 4) |
          (outFmt.litValue << 8) | (if (biasOn) BigInt(1) << 30 else BigInt(0)) |
          (if (intRun) BigInt(1) << 29 else BigInt(0))
        // 多行、多 N tile：首次错误回复后暂停总线时还有未完成工作，不能提前结束 inFlight。
        wr(M_REG_ADDR, 4); wr(NK_REG_ADDR, (BigInt(64) << 16) | 32)
        wr(A_REG_ADDR, aBase); wr(B_REG_ADDR, bBase); wr(C_REG_ADDR, cBase)
        wr(CONTROL_REG_ADDR, control)
        wr(SCALE_A_REG_ADDR, saBase); wr(SCALE_B_REG_ADDR, sbBase); wr(SCALE_C_REG_ADDR, scBase)
        wr(BIAS_REG_ADDR, biasBase); wr(RQM_REG_ADDR, rqBase); wr(RQ_PARAM_REG_ADDR, 0)
        prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
        wr(IRQ_MASK_REG_ADDR, IRQ_ALL) // 错误必须在全屏蔽时也立即记录。
        launchStart(dut.clock, dut.io.cmd)
        var injected = false
        var checkedEarly = false
        var done = false
        var cycle = 0
        var pollSt = 0; var pollCool = 0
        val expectedError = BigInt(1) << (source + 2)

        while (!done && cycle < 6000) {
          if (injected && !checkedEarly) {
            // 上一边沿已经发生错误 rsp.fire；排空命令响应并冻结全部 DMA，独立读 IRQ。
            quiesce(ready = false)
            dut.io.cmd.valid.poke(false.B); dut.clock.step(2)
            rd(IRQ_RAW_REG_ADDR) should be(expectedError)
            rd(IRQ_MASKED_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
            dut.io.status.inFlight.expect(true.B)
            val status = decodeStatus(rd(STATUS_REG_ADDR))
            status.inFlight should be(true)
            rd(ERROR_CODE_REG_ADDR) should be(BigInt(0))
            wr(IRQ_MASK_REG_ADDR, 0)
            dut.io.irq.expect(true.B)
            rd(IRQ_MASKED_REG_ADDR) should be(expectedError)
            checkedEarly = true
            pollSt = 0; pollCool = 0
          }
          quiesce(ready = true)
          var injectedThisCycle = false
          def errorFor(eventSource: Int): Boolean = {
            val hit = eventSource == source && !injected && !injectedThisCycle
            if (hit) injectedThisCycle = true
            hit
          }
          def driveRead(port: HiveCoreDMAExtReadOnlyIF, eventSource: Int, data: BigInt): Unit = {
            if (port.req.valid.peek().litToBoolean) {
              val accepted = port.rsp.ready.peek().litToBoolean
              port.rsp.valid.poke(true.B); port.rsp.payload.data.poke(data.U)
              port.rsp.payload.err.poke((accepted && errorFor(eventSource)).B)
            }
          }
          def driveWrite(port: HiveCoreDMAExtWriteOnlyIF, eventSource: Int): Unit = {
            if (port.req.valid.peek().litToBoolean) {
              val accepted = port.rsp.ready.peek().litToBoolean
              port.rsp.valid.poke(true.B)
              port.rsp.payload.err.poke((accepted && errorFor(eventSource)).B)
            }
          }
          driveRead(dut.io.dmaA, 0, 0)
          driveRead(dut.io.dmaB, 1, 0)
          // 所有地址域互不重叠；共享端口所属来源由请求的张量地址独立推导。
          val saAddr = dut.io.dmaScaleA.get.req.payload.addr.peek().litValue.toLong
          val sharedSource = if (saAddr >= biasBase && saAddr < biasBase + 128) 6
            else if (saAddr >= rqBase && saAddr < rqBase + 128) 7 else 3
          val sharedData = if (sharedSource == 3) BigInt("7f7f7f7f", 16)
            else if (sharedSource == 7) BigInt(1) else BigInt(0)
          driveRead(dut.io.dmaScaleA.get, sharedSource, sharedData)
          driveRead(dut.io.dmaScaleB.get, 4, BigInt("7f7f7f7f", 16))
          driveWrite(dut.io.dmaC, 2)
          driveWrite(dut.io.dmaCScale.get, 5)
          if (checkedEarly) {
            val (st, cool, irq) = irqPollStep(dut.io.cmd, dut.io.resp, pollSt, pollCool)
            pollSt = st; pollCool = cool
            irq.foreach { i =>
              i.configError should be(false)
              (i.raw & ~BigInt(1)) should be(expectedError)
              done = i.done
            }
          }
          dut.clock.step(); cycle += 1
          if (injectedThisCycle) injected = true
        }
        quiesce(ready = false)
        dut.io.cmd.valid.poke(false.B); dut.clock.step(2)
        withClue(s"$name injection/early/completion at cycle $cycle: ") {
          injected should be(true); checkedEarly should be(true); done should be(true)
        }
        dut.io.status.inFlight.expect(false.B)
        rd(IRQ_RAW_REG_ADDR) should be(expectedError | 1)
        rd(IRQ_MASKED_REG_ADDR) should be(expectedError | 1)
        rd(ERROR_CODE_REG_ADDR) should be(BigInt(0))
        // 清 done 不影响 DMA pending，屏蔽/解除屏蔽只改变 irq。
        wr(IRQ_CLEAR_REG_ADDR, 1)
        rd(IRQ_RAW_REG_ADDR) should be(expectedError)
        wr(IRQ_MASK_REG_ADDR, IRQ_ALL); dut.io.irq.expect(false.B)
        wr(IRQ_MASK_REG_ADDR, 0); dut.io.irq.expect(true.B)
        // 下一 START 不自动清旧错误；显式 W1C 后旧 DMA sticky err 不得反复置 RAW。
        launchStart(dut.clock, dut.io.cmd)
        rd(IRQ_RAW_REG_ADDR) should be(expectedError)
        dut.io.status.inFlight.expect(true.B)
        wr(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
        dut.clock.step(8)
        rd(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
      }
    }
  }
}
