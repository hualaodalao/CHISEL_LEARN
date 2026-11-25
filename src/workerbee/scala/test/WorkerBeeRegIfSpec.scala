import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** WorkerBee ↔ QueenDsaRegIf 集成测试（Queen 通用 DSA 契约 §13/§14）。
  *
  * 通用 RegIf 行为（shadow/live、单 ready、W1C/W1S 跨 lane 合并、QRD 同拍 resp）由 Queen 侧
  *   suite 覆盖；本 suite 只测 WorkerBee 设备侧集成：12 槽配置译码回读、status/errorCode 区、
  *   IntrRFMS4 中断、hwSignal/traceTag 公共区、wrErr pulse、start 当拍采样 swTag/hwSignal、
  *   completion 承载 done/configError（error=1 ⟹ hwSignal 全 0）。
  */
class WorkerBeeRegIfSpec extends AnyFlatSpec with Matchers with ChiselSim with WbStatusPollApi {
  import WbSimCommon._

  private val cfg = WorkerBeeConfig(cW = 40, aBufferDepth = 256,
    bBufferDepth = 256, cBufferDepth = 256, scaleBufferDepth = 64)

  private def initDut(dut: WorkerBee): Unit = {
    Seq(dut.io.dmaA, dut.io.dmaB, dut.io.dmaScaleA.get, dut.io.dmaScaleB.get).foreach { p =>
      p.req.ready.poke(true.B); p.rsp.valid.poke(false.B)
      p.rsp.payload.data.poke(0.U); p.rsp.payload.err.poke(false.B)
    }
    Seq(dut.io.dmaC, dut.io.dmaCScale.get).foreach { p =>
      p.req.ready.poke(true.B); p.rsp.valid.poke(false.B); p.rsp.payload.err.poke(false.B)
    }
    dut.io.resp.ready.poke(true.B)
    dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
    dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
  }

  private def wr(dut: WorkerBee, a: Int, d: BigInt): Unit =
    transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.WRITE1, a, d0 = d)
  private def wr2(dut: WorkerBee, a0: Int, a1: Int, d0: BigInt, d1: BigInt): Unit =
    transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.WRITE2, a0, a1, d0, d1)
  private def rd(dut: WorkerBee, a: Int): BigInt =
    readRegister(dut.clock, dut.io.cmd, dut.io.resp, a)

  /** 合法 INT8 配置（M=1/N=16/K=32，loadW=0）：RUN 后 executor 等数据停住，inFlight 保持。 */
  private def writeStallConfig(dut: WorkerBee): Unit = {
    wr2(dut, M_REG_ADDR, NK_REG_ADDR, 1, (BigInt(32) << 16) | 16)
    wr2(dut, A_REG_ADDR, B_REG_ADDR, 0x10000, 0x20000)
    wr2(dut, C_REG_ADDR, CONTROL_REG_ADDR, 0x30000,
      BigInt(DataFormat.INT8.litValue.toInt) | (BigInt(DataFormat.INT8.litValue.toInt) << 4) |
        (BigInt(DataFormat.INT8.litValue.toInt) << 8))
    wr2(dut, SCALE_A_REG_ADDR, SCALE_B_REG_ADDR, 0x40000, 0x50000)
    wr(dut, SCALE_C_REG_ADDR, 0x60000)
  }

  /** MX 输入但 loadWMode=0 ⟹ configError errCode 4（MX 强制 B 转置装载）；输出取 MXE4M3 避免族交叉抢跑。 */
  private val illegalCtl5: BigInt =
    BigInt(DataFormat.MXE4M3.litValue.toInt) |
      (BigInt(DataFormat.MXE5M2.litValue.toInt) << 4) |
      (BigInt(DataFormat.MXE4M3.litValue.toInt) << 8)

  private def waitCompletion(dut: WorkerBee, maxCycles: Int = 200): Option[(Boolean, BigInt, BigInt, Boolean)] = {
    var i = 0
    while (i < maxCycles) {
      if (dut.io.completion.valid.peek().litToBoolean) {
        val b = dut.io.completion.bits
        val r = Some((b.error.peek().litToBoolean, b.code.peek().litValue,
          b.swTag.peek().litValue, b.hwSignal.enable.peek().litToBoolean))
        dut.clock.step()   // errorCodeReg 在 completion 下一拍才锁存，读状态寄存器前先推进
        return r
      }
      dut.clock.step(); i += 1
    }
    None
  }

  behavior of "WorkerBee Queen config interface"

  it should "write/read back all 12 config slots via QWR1 and QWR2" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      val words = Seq.tabulate(12)(i => BigInt(0x1000 + i) * 0x11111111L & 0xFFFFFFFFL)
      // 相邻配对写（QWR2 6-bit regBase 窗口）：0/1、2/3、...、10/11。
      for (i <- 0 until 12 by 2) wr2(dut, i, i + 1, words(i), words(i + 1))
      for (i <- 0 until 12) withClue(s"cfg slot $i: ") { rd(dut, i) should be(words(i)) }
      // 单写覆盖：QWR1 改 slot5，shadow 立即可读（执行期也可写读）。
      wr(dut, CONTROL_REG_ADDR, 0xDEADBEEFL)
      rd(dut, CONTROL_REG_ADDR) should be(BigInt(0xDEADBEEFL))
    }
  }

  it should "report status at 0x40 and zero errorCode at 0x41 after reset" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      val st = decodeStatus(rd(dut, STATUS_REG_ADDR))
      st.busy should be(false); st.inFlight should be(false)
      rd(dut, ERROR_CODE_REG_ADDR) should be(BigInt(0))
    }
  }

  it should "flag illegal read with resp.err and illegal write with wrErr pulse" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      // 0x100 既非配置(0..11)也非 status/IRQ/hwSignal/traceTag ⟹ 非法。
      transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.READ, 0x100, expectedErr = true)
      // 非法写：wrErr(0) 在 accept 当拍 pulse，不占 resp。
      dut.io.resp.ready.poke(true.B)
      pokeRegCommand(dut.io.cmd, QueenNativeOp.WRITE1, 0x100, d0 = 0x1234)
      dut.io.cmd.ready.peek().litToBoolean should be(true)   // 写 beat 恒可收
      dut.io.wrErr(0).peek().litToBoolean should be(true)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
      dut.io.wrErr(0).peek().litToBoolean should be(false)  // pulse 仅一拍
      transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.READ, 0x100, expectedErr = true) should be(BigInt(0))
    }
  }

  it should "gate READ ready by resp.ready and keep write beat always ready" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      dut.io.resp.ready.poke(false.B)
      pokeRegCommand(dut.io.cmd, QueenNativeOp.READ, M_REG_ADDR)
      dut.io.cmd.ready.peek().litToBoolean should be(false)  // READ 取 resp.ready
      dut.io.resp.valid.peek().litToBoolean should be(false)
      dut.clock.step()
      // 同一反压下写 beat 仍可收。
      pokeRegCommand(dut.io.cmd, QueenNativeOp.WRITE1, M_REG_ADDR, d0 = 7)
      dut.io.cmd.ready.peek().litToBoolean should be(true)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
      dut.io.resp.ready.poke(true.B)
      rd(dut, M_REG_ADDR) should be(BigInt(7))
    }
  }

  it should "accept RUN, hold inFlight, and block a second RUN while busy" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      writeStallConfig(dut)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)
      dut.clock.step(2)
      decodeStatus(rd(dut, STATUS_REG_ADDR)).inFlight should be(true)
      // 第二条 RUN：startReady=!inFlight ⟹ cmd.ready 拉低。
      pokeRegCommand(dut.io.cmd, QueenNativeOp.RUN, 0)
      dut.io.cmd.ready.peek().litToBoolean should be(false)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
    }
  }

  it should "sample swTag at start and return configError completion with zero hwSignal" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      // hwSignal 目标 enable=1/group=5、mask、traceTag 先写入（start 当拍被采样）。
      wr(dut, HW_SIGNAL_TARGET_REG_ADDR, BigInt(1) | (BigInt(5) << 2))
      wr(dut, HW_SIGNAL_MASK_REG_ADDR, 0xFFFFFFFFL)
      wr(dut, TRACE_TAG_REG_ADDR, 0x77)
      rd(dut, HW_SIGNAL_TARGET_REG_ADDR) should be(BigInt(1) | (BigInt(5) << 2))
      rd(dut, HW_SIGNAL_MASK_REG_ADDR) should be(BigInt(0xFFFFFFFFL))
      rd(dut, TRACE_TAG_REG_ADDR) should be(BigInt(0x77))
      // 非法配置（MX×INT8 族交叉 errCode11）。
      wr2(dut, M_REG_ADDR, NK_REG_ADDR, 1, (BigInt(32) << 16) | 16)
      wr2(dut, A_REG_ADDR, B_REG_ADDR, 0x10000, 0x20000)
      wr2(dut, C_REG_ADDR, CONTROL_REG_ADDR, 0x30000, illegalCtl5)
      wr2(dut, SCALE_A_REG_ADDR, SCALE_B_REG_ADDR, 0x40000, 0x50000)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      // RUN 携带 swTag=0xAB（非 0 覆盖 traceTag）。
      transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.RUN, 0, swTag = 0xAB)
      val c = waitCompletion(dut)
      c shouldBe defined
      val (err, code, swTag, hwEn) = c.get
      err should be(true); code should be(BigInt(4))
      swTag should be(BigInt(0xAB))
      hwEn should be(false)                 // error=1 ⟹ hwSignal 全 0
      rd(dut, ERROR_CODE_REG_ADDR) should be(BigInt(4))
      decodeStatus(rd(dut, STATUS_REG_ADDR)).inFlight should be(false)
      rd(dut, IRQ_RAW_REG_ADDR) should be(BigInt(0))   // configError 不进 IRQ
    }
  }

  it should "fall back to traceTag when RUN swTag is zero" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      wr(dut, TRACE_TAG_REG_ADDR, 0x55)
      wr2(dut, M_REG_ADDR, NK_REG_ADDR, 1, (BigInt(32) << 16) | 16)
      wr2(dut, A_REG_ADDR, B_REG_ADDR, 0x10000, 0x20000)
      wr2(dut, C_REG_ADDR, CONTROL_REG_ADDR, 0x30000, illegalCtl5)
      wr2(dut, SCALE_A_REG_ADDR, SCALE_B_REG_ADDR, 0x40000, 0x50000)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)    // RUN swTag=0 ⟹ 沿用 traceTag
      val c = waitCompletion(dut)
      c shouldBe defined
      c.get._3 should be(BigInt(0x55))
    }
  }

  it should "record IntrForce, mask/unmask irq, and W1C clear IntrRaw" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      wr(dut, IRQ_MASK_REG_ADDR, IRQ_ALL)   // 全屏蔽
      wr(dut, IRQ_FORCE_REG_ADDR, 0x05)     // W1S 置 bit0/bit2
      rd(dut, IRQ_RAW_REG_ADDR) should be(BigInt(0x05))
      rd(dut, IRQ_MASKED_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
      wr(dut, IRQ_MASK_REG_ADDR, 0)         // 解除屏蔽
      rd(dut, IRQ_MASKED_REG_ADDR) should be(BigInt(0x05)); dut.io.irq.expect(true.B)
      wr(dut, IRQ_MASK_REG_ADDR, BigInt(0x01))  // 屏蔽 bit0
      rd(dut, IRQ_MASKED_REG_ADDR) should be(BigInt(0x04)); dut.io.irq.expect(true.B)
      wr(dut, IRQ_RAW_REG_ADDR, 0x04)       // W1C 清 bit2
      rd(dut, IRQ_RAW_REG_ADDR) should be(BigInt(0x01))
      wr(dut, IRQ_RAW_REG_ADDR, IRQ_ALL)
      rd(dut, IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
    }
  }
}