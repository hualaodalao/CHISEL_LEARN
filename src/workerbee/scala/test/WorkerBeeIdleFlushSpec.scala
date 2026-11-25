import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** WorkerBee 强 idle + flush 四相（Queen §5.3/§5.5/§13.5/§13.10.5，Phase 2）。
  *
  * 用 configError 完成路径（非法配置→completion→自然结束）与 stall 忙路径（合法配置不喂数据→
  *   executor 持续 busy）覆盖 deviceIdle 判据与 flush「等自然结束、不提前回 ack」契约，
  *   不引入重型 GEMM 喂数。裁定：flush 不复位 RegIf、不抑制 completion、不做 abort。
  */
class WorkerBeeIdleFlushSpec extends AnyFlatSpec with Matchers with ChiselSim with WbStatusPollApi {
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
    dut.io.flushReq.poke(false.B)
    dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
    dut.reset.poke(true.B); dut.clock.step(3); dut.reset.poke(false.B); dut.clock.step(2)
  }

  private def wr(dut: WorkerBee, a: Int, d: BigInt): Unit =
    transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.WRITE1, a, d0 = d)
  private def wr2(dut: WorkerBee, a0: Int, a1: Int, d0: BigInt, d1: BigInt): Unit =
    transactReg(dut.clock, dut.io.cmd, dut.io.resp, QueenNativeOp.WRITE2, a0, a1, d0, d1)
  private def rd(dut: WorkerBee, a: Int): BigInt =
    readRegister(dut.clock, dut.io.cmd, dut.io.resp, a)

  /** 合法 INT8 配置（M=1/N=16/K=32，loadW=0）：不喂数据 ⟹ executor 持续 busy（stall）。 */
  private def writeStallConfig(dut: WorkerBee): Unit = {
    wr2(dut, M_REG_ADDR, NK_REG_ADDR, 1, (BigInt(32) << 16) | 16)
    wr2(dut, A_REG_ADDR, B_REG_ADDR, 0x10000, 0x20000)
    wr2(dut, C_REG_ADDR, CONTROL_REG_ADDR, 0x30000,
      BigInt(DataFormat.INT8.litValue.toInt) | (BigInt(DataFormat.INT8.litValue.toInt) << 4) |
        (BigInt(DataFormat.INT8.litValue.toInt) << 8))
    wr2(dut, SCALE_A_REG_ADDR, SCALE_B_REG_ADDR, 0x40000, 0x50000)
    wr(dut, SCALE_C_REG_ADDR, 0x60000)
  }

  /** MX 输入但 loadWMode=0 ⟹ configError errCode 4，execute 拍即自然结束（completion）。 */
  private def writeIllegalConfig(dut: WorkerBee): Unit = {
    wr2(dut, M_REG_ADDR, NK_REG_ADDR, 1, (BigInt(32) << 16) | 16)
    wr2(dut, A_REG_ADDR, B_REG_ADDR, 0x10000, 0x20000)
    wr2(dut, C_REG_ADDR, CONTROL_REG_ADDR, 0x30000,
      BigInt(DataFormat.MXE4M3.litValue.toInt) | (BigInt(DataFormat.MXE5M2.litValue.toInt) << 4) |
        (BigInt(DataFormat.MXE4M3.litValue.toInt) << 8))
    wr2(dut, SCALE_A_REG_ADDR, SCALE_B_REG_ADDR, 0x40000, 0x50000)
  }

  /** 走到 completion.valid 当拍，返回该拍 error（completion 是脉冲，走拍后失效），再前进一拍。 */
  private def runToCompletion(dut: WorkerBee, maxCycles: Int = 200): Boolean = {
    var i = 0
    while (i < maxCycles && !dut.io.completion.valid.peek().litToBoolean) { dut.clock.step(); i += 1 }
    assert(dut.io.completion.valid.peek().litToBoolean, "completion timeout")
    val err = dut.io.completion.bits.error.peek().litToBoolean
    dut.clock.step()
    err
  }

  /** 拉 flushReq 后等 flushAck（四相 req↑→ack↑）；超时返回 false。 */
  private def requestFlush(dut: WorkerBee, maxCycles: Int = 200): Boolean = {
    dut.io.flushReq.poke(true.B)
    var i = 0
    while (i < maxCycles && !dut.io.flushAck.peek().litToBoolean) { dut.clock.step(); i += 1 }
    dut.io.flushAck.peek().litToBoolean
  }

  behavior of "WorkerBee deviceIdle and flush"

  it should "report strong idle after reset and drop it while executing" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      dut.io.deviceIdle.expect(true.B)
      writeStallConfig(dut)
      dut.io.deviceIdle.expect(true.B)   // 仅写 shadow 不算执行
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)
      dut.clock.step(5)
      dut.io.deviceIdle.expect(false.B)  // executor busy
      decodeStatus(rd(dut, STATUS_REG_ADDR)).inFlight should be(true)
    }
  }

  it should "return to idle after a configError completion" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      writeIllegalConfig(dut)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)
      runToCompletion(dut) should be(true)
      dut.clock.step(3)
      dut.io.deviceIdle.expect(true.B)
      decodeStatus(rd(dut, STATUS_REG_ADDR)).inFlight should be(false)
    }
  }

  it should "complete the four-phase flush handshake when idle" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      dut.io.flushAck.expect(false.B)
      requestFlush(dut) should be(true)
      dut.io.flushAck.expect(true.B)
      dut.io.deviceIdle.expect(true.B)
      // req↓ → ack↓
      dut.io.flushReq.poke(false.B)
      dut.clock.step()
      dut.io.flushAck.expect(false.B)
    }
  }

  it should "block new RUN while flushing" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      writeStallConfig(dut)
      requestFlush(dut) should be(true)
      // flushing 期间 startReady=0 ⟹ RUN beat 不获准入。
      pokeRegCommand(dut.io.cmd, QueenNativeOp.RUN, 0)
      dut.io.cmd.ready.expect(false.B)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
      dut.io.flushReq.poke(false.B); dut.clock.step()
      // flush 结束后 RUN 恢复准入。
      pokeRegCommand(dut.io.cmd, QueenNativeOp.RUN, 0)
      dut.io.cmd.ready.expect(true.B)
      dut.clock.step()
      dut.io.cmd.valid.poke(false.B); dut.io.cmd.bits.foreach(_.valid.poke(false.B))
    }
  }

  it should "not acknowledge flush prematurely while work is still running" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      writeStallConfig(dut)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)
      dut.clock.step(5)
      dut.io.deviceIdle.expect(false.B)
      dut.io.flushReq.poke(true.B)
      for (_ <- 0 until 50) {
        dut.clock.step()
        dut.io.flushAck.expect(false.B)   // 工作未自然结束，不得提前回 ack
        dut.io.deviceIdle.expect(false.B)
      }
    }
  }

  it should "flush after completion and accept a fresh RUN afterwards" in {
    simulate(new WorkerBee(cfg)) { dut =>
      initDut(dut)
      writeIllegalConfig(dut)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)
      runToCompletion(dut)
      dut.clock.step(3)
      requestFlush(dut) should be(true)
      dut.io.flushReq.poke(false.B); dut.clock.step()
      dut.io.flushAck.expect(false.B)
      // credit 重对齐：新配置 + RUN 正常准入并进入执行。
      writeStallConfig(dut)
      prepareStart(dut.clock, dut.io.cmd, dut.io.resp)
      launchStart(dut.clock, dut.io.cmd)
      dut.clock.step(5)
      decodeStatus(rd(dut, STATUS_REG_ADDR)).inFlight should be(true)
      dut.io.deviceIdle.expect(false.B)
    }
  }
}