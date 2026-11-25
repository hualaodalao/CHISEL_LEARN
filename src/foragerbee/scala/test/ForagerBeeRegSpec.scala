import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** ForagerBeeReg（Queen 设备适配器）契约仿真：native beat 配置、RUN 反压、completion 采样回传、
  * hwSignal/traceTag、多引擎乱序 completion、flush 四相。复用 FbStreamMemSlave 字节内存模型。
  */
class ForagerBeeRegSpec extends AnyFlatSpec with Matchers {
  behavior of "ForagerBeeReg Queen device interface"

  private val defaultCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 5, tileSize = 4, maxBurstLen = 4, enableConversion = true)
  private val qcfg = QueenConfig()
  private val lanes = qcfg.nativeWidth

  private case class Comp(swTag: BigInt, hwTag: BigInt, error: Boolean, code: BigInt,
    hwEnable: Boolean, hwGroup: BigInt, hwMask: BigInt)

  private class Sim(dut: ForagerBee, cfg: ForagerBeeConfig,
      mem: mutable.HashMap[BigInt, Byte], writeDelay: Int = 1) {
    private val models = Seq.fill(cfg.numPorts)(
      new FbStreamMemSlave(mem, cfg.beatBytes, writeDelay = writeDelay))
    val comps = mutable.ArrayBuffer.empty[Comp]
    private def stepBus(): Unit = {
      if (dut.io.completion.valid.peek().litToBoolean) {
        val b = dut.io.completion.bits
        comps += Comp(b.swTag.peek().litValue, b.hwTag.peek().litValue,
          b.error.peek().litToBoolean, b.code.peek().litValue,
          b.hwSignal.enable.peek().litToBoolean, b.hwSignal.group.peek().litValue,
          b.hwSignal.mask.peek().litValue)
      }
      for (p <- 0 until cfg.numPorts) models(p).step(dut.io.bus(p))
      dut.clock.step()
    }
    private def clearCmd(): Unit = {
      dut.io.cmd.valid.poke(false.B)
      for (l <- 0 until lanes) {
        dut.io.cmd.bits(l).valid.poke(false.B)
        dut.io.cmd.bits(l).bits.poke(0.U.asTypeOf(new QueenNativeCmd(qcfg)))
      }
    }
    def reset(): Unit = {
      clearCmd()
      dut.io.resp.ready.poke(true.B)
      dut.io.flushReq.poke(false.B)
      for (b <- dut.io.bus) {
        b.rd.ready.poke(false.B); b.rdData.valid.poke(false.B)
        b.rdData.payload.poke(0.U.asTypeOf(b.rdData.payload))
        b.wr.ready.poke(false.B); b.wrResp.valid.poke(false.B); b.wrResp.payload.err.poke(false.B)
      }
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
    }
    def idle(n: Int = 1): Unit = { clearCmd(); for (_ <- 0 until n) stepBus() }
    private def setLane(l: Int, op: QueenNativeOp.Type, a0: Int, d0: BigInt,
        a1: Int, d1: BigInt, swTag: BigInt): Unit = {
      val b = dut.io.cmd.bits(l).bits
      b.op.poke(op); b.reg0Addr.poke(a0.U); b.reg1Addr.poke(a1.U)
      b.data0.poke(d0); b.data1.poke(d1); b.swTag.poke(swTag)
      dut.io.cmd.bits(l).valid.poke(true.B)
    }
    def writeReg(a: Int, d: BigInt): Unit = {
      clearCmd(); setLane(0, QueenNativeOp.WRITE1, a, d, 0, 0, 0)
      dut.io.cmd.valid.poke(true.B)
      assert(dut.io.cmd.ready.peek().litToBoolean, "纯写 beat 恒可收")
      stepBus(); clearCmd()
    }
    def write2(a0: Int, d0: BigInt, a1: Int, d1: BigInt): Unit = {
      clearCmd(); setLane(0, QueenNativeOp.WRITE2, a0, d0, a1, d1, 0)
      dut.io.cmd.valid.poke(true.B)
      assert(dut.io.cmd.ready.peek().litToBoolean)
      stepBus(); clearCmd()
    }
    // 返回 (data, err, wrErrLane0)
    def writeRegErr(a: Int, d: BigInt): Boolean = {
      clearCmd(); setLane(0, QueenNativeOp.WRITE1, a, d, 0, 0, 0)
      dut.io.cmd.valid.poke(true.B)
      val we = dut.io.wrErr(0).peek().litToBoolean
      stepBus(); clearCmd(); we
    }
    def readReg(a: Int): (BigInt, Boolean) = {
      clearCmd(); setLane(0, QueenNativeOp.READ, a, 0, 0, 0, 0)
      dut.io.cmd.valid.poke(true.B)
      assert(dut.io.cmd.ready.peek().litToBoolean, "QRD 恒可收（resp.ready=1）")
      val d = dut.io.resp.bits.data.peek().litValue
      val e = dut.io.resp.bits.err.peek().litToBoolean
      stepBus(); clearCmd(); (d, e)
    }
    // RUN 独占 beat；返回是否被接受（ready）
    def run(swTag: BigInt): Boolean = {
      clearCmd(); setLane(0, QueenNativeOp.RUN, 0, 0, 0, 0, swTag)
      dut.io.cmd.valid.poke(true.B)
      val r = dut.io.cmd.ready.peek().litToBoolean
      stepBus(); clearCmd(); r
    }
    def copy1D(src: BigInt, dst: BigInt, len: Int): Unit = {
      // op COPY(0) dimCount1(1<<3) srcFmt=INT8(3<<6) dstFmt=INT8(3<<10) = 0xCC8
      writeReg(0x00, BigInt(0xCC8))
      writeReg(0x08, len)
      writeReg(0x03, src & 0xffffffffL); writeReg(0x04, dst & 0xffffffffL)
      writeReg(0x05, ((src >> 32) & 0xff) | (((dst >> 32) & 0xff) << 8))
    }
    def waitComps(n: Int, timeout: Int = 30000): Unit = {
      var t = 0
      while (comps.size < n && t < timeout) { idle(1); t += 1 }
      assert(comps.size >= n, s"等待 $n 条 completion 超时（当前 ${comps.size}）")
    }
    def deviceIdle: Boolean = dut.io.deviceIdle.peek().litToBoolean
    def flushAck: Boolean = dut.io.flushAck.peek().litToBoolean
    def irq: Boolean = dut.io.irq.peek().litToBoolean
    def setFlush(req: Boolean): Unit = dut.io.flushReq.poke(req.B)
  }

  private def withSim(cfg: ForagerBeeConfig = defaultCfg,
      prefill: mutable.HashMap[BigInt, Byte] = mutable.HashMap.empty,
      writeDelay: Int = 1)(body: Sim => Unit): mutable.HashMap[BigInt, Byte] = {
    val mem = prefill
    implicit val testingDirectory: chisel3.testing.HasTestingDirectory =
      chisel3.testing.HasTestingDirectory.timestamp
    (new chisel3.simulator.ChiselSim {}).simulateRaw(new ForagerBee(cfg, qcfg)) { dut =>
      val sim = new Sim(dut, cfg, mem, writeDelay)
      sim.reset()
      body(sim)
    }
    mem
  }

  private def put(mem: mutable.HashMap[BigInt, Byte], base: BigInt, bytes: Seq[Int]): Unit =
    bytes.zipWithIndex.foreach { case (b, i) => mem(base + i) = b.toByte }

  it should "reset to idle and roundtrip private config registers" in {
    withSim() { sim =>
      sim.deviceIdle shouldBe true
      val (st, _) = sim.readReg(0x40)
      (st & 1) shouldBe 0; ((st >> 1) & 1) shouldBe 1
      for (a <- 0 until 0x23) {
        val v = (BigInt(a) * 0x11111111 + 0x12345678) & 0xffffffffL
        sim.writeReg(a, v)
        sim.readReg(a)._1 shouldBe v
      }
      sim.write2(0x03, 0xdeadbeefL, 0x04, 0xcafebabeL)
      sim.readReg(0x03)._1 shouldBe 0xdeadbeefL
      sim.readReg(0x04)._1 shouldBe 0xcafebabeL
    }
  }

  it should "pulse wrErr on illegal writes and err on reserved reads" in {
    withSim() { sim =>
      sim.writeRegErr(0x23, 0x1L) shouldBe true  // 保留
      sim.writeRegErr(0x100, 0x1L) shouldBe true // 保留区
      sim.writeRegErr(0x40, 0x1L) shouldBe true  // 状态只读
      sim.writeRegErr(0x03, 0x1L) shouldBe false // 合法配置写无 wrErr
      sim.readReg(0x23)._2 shouldBe true
      sim.readReg(0x100)._2 shouldBe true
    }
  }

  it should "COPY with 40-bit addresses and return completion swTag" in {
    val src = BigInt("8100001000", 16); val dst = BigInt("a100002000", 16)
    val mem = mutable.HashMap.empty[BigInt, Byte]
    put(mem, src, (1 to 16)); put(mem, src & 0xffffffffL, Seq.fill(16)(0xe7))
    val result = withSim(prefill = mem) { sim =>
      sim.copy1D(src, dst, 16)
      sim.run(0x2000) shouldBe true
      sim.waitComps(1)
      sim.comps.head.swTag shouldBe 0x2000
      sim.comps.head.error shouldBe false
      sim.comps.head.hwEnable shouldBe false
      sim.idle(4)
      sim.deviceIdle shouldBe true
    }
    for (i <- 0 until 16) {
      result(dst + i) shouldBe (i + 1).toByte
      result((src & 0xffffffffL) + i) shouldBe 0xe7.toByte
    }
  }

  it should "backpressure RUN when credit exhausted (N=1) and recover on completion" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s = BigInt("8100030000", 16); val d = BigInt("a100030000", 16)
    put(mem, s, (1 to 16))
    withSim(cfg = defaultCfg.copy(numPorts = 1), prefill = mem, writeDelay = 40) { sim =>
      sim.copy1D(s, d, 16)
      sim.run(1) shouldBe true
      sim.run(2) shouldBe false // credit==0 反压，不接受
      sim.comps.size shouldBe 0
      sim.waitComps(1)
      sim.comps.head.swTag shouldBe 1
      sim.run(3) shouldBe true // 完成后 credit 归还
      sim.waitComps(2)
      sim.comps(1).swTag shouldBe 3
    }
  }

  it should "track per-command swTag across out-of-order multi-engine completions" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s1 = BigInt("8100040000", 16); val d1 = BigInt("a100040000", 16)
    val s2 = BigInt("8100050000", 16); val d2 = BigInt("a100050000", 16)
    put(mem, s1, (1 to 16)); put(mem, s2, (101 to 116))
    val result = withSim(prefill = mem, writeDelay = 30) { sim =>
      sim.copy1D(s1, d1, 16); sim.run(0x1111) shouldBe true
      sim.copy1D(s2, d2, 16); sim.run(0x2222) shouldBe true
      sim.waitComps(2)
      sim.comps.map(_.swTag).toSet shouldBe Set(BigInt(0x1111), BigInt(0x2222))
      sim.comps.foreach(_.error shouldBe false)
      sim.idle(4); sim.deviceIdle shouldBe true
    }
    for (i <- 0 until 16) {
      result(d1 + i) shouldBe (i + 1).toByte
      result(d2 + i) shouldBe (i + 101).toByte
    }
  }

  it should "sample hwSignal at RUN and force enable=0 on error" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s = BigInt("8100060000", 16); val d = BigInt("a100060000", 16)
    put(mem, s, (1 to 16))
    withSim(prefill = mem) { sim =>
      sim.writeReg(0x80, BigInt((3 << 2) | 1)) // enable=1 kind=event group=3
      sim.writeReg(0x81, BigInt(0x8))          // mask bit3
      sim.copy1D(s, d, 16)
      sim.run(0x3000) shouldBe true
      sim.waitComps(1)
      sim.comps.head.hwEnable shouldBe true
      sim.comps.head.hwGroup shouldBe 3
      sim.comps.head.hwMask shouldBe 0x8
      sim.comps.head.swTag shouldBe 0x3000
      // 错误命令：越界目的 → error，hwSignal.enable 强制 0
      sim.writeReg(0x80, BigInt((3 << 2) | 1))
      sim.copy1D(s, BigInt("ffffffffff", 16), 16) // 越界
      sim.run(0x4000) shouldBe true
      sim.waitComps(2)
      sim.comps(1).error shouldBe true
      sim.comps(1).hwEnable shouldBe false
    }
  }

  it should "use TRACE_TAG when RUN rs2=0 and override when nonzero" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s = BigInt("8100070000", 16); val d = BigInt("a100070000", 16)
    put(mem, s, (1 to 16))
    withSim(prefill = mem) { sim =>
      sim.writeReg(0xA0, BigInt(0x5555)) // TRACE_TAG
      sim.copy1D(s, d, 16)
      sim.run(0) shouldBe true            // rs2=0 → 沿用 TRACE_TAG
      sim.waitComps(1)
      sim.comps.head.swTag shouldBe 0x5555
      sim.copy1D(s, d + 32, 16)
      sim.run(0x6666) shouldBe true       // rs2 非 0 → 覆盖
      sim.waitComps(2)
      sim.comps(1).swTag shouldBe 0x6666
      sim.readReg(0xA0)._1 shouldBe 0x5555 // 寄存器本身不被 rs2 改写
    }
  }

  it should "route command error to interrupt raw and support W1C clear" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s = BigInt("81000a0000", 16)
    put(mem, s, (1 to 16))
    withSim(prefill = mem) { sim =>
      sim.writeReg(0x68, 0) // IntrMask=0 解除屏蔽
      sim.irq shouldBe false
      sim.copy1D(s, BigInt("ffffffffff", 16), 16) // 越界目的 → err
      sim.run(0x7000) shouldBe true
      sim.waitComps(1)
      sim.comps.head.error shouldBe true
      sim.idle(2)
      (sim.readReg(0x60)._1 & 1) shouldBe 1 // IntrRaw bit0（引擎0）置位
      (sim.readReg(0x6C)._1 & 1) shouldBe 1 // IntrStatus = raw & ~mask
      sim.irq shouldBe true
      sim.writeReg(0x60, 1) // W1C 清 bit0
      sim.idle(1)
      (sim.readReg(0x60)._1 & 1) shouldBe 0
      sim.irq shouldBe false
    }
  }

  it should "complete flush four-phase and discard queued commands" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s1 = BigInt("8100080000", 16); val s2 = BigInt("8100090000", 16)
    val dst = BigInt("a100080000", 16)
    put(mem, s1, (1 to 16)); put(mem, s2, (101 to 116))
    withSim(prefill = mem, writeDelay = 60) { sim =>
      sim.copy1D(s1, dst, 16); sim.run(1) shouldBe true
      sim.copy1D(s2, dst, 16); sim.run(2) shouldBe true // 第二条 WAW 排队
      sim.idle(4)
      sim.setFlush(true)
      // flushReq 拉起后等 ack（运行中引擎自然结束、排队命令丢弃）
      var t = 0
      while (!sim.flushAck && t < 30000) { sim.idle(1); t += 1 }
      sim.flushAck shouldBe true
      sim.deviceIdle shouldBe true
      sim.setFlush(false)
      sim.idle(2)
      sim.flushAck shouldBe false
      sim.deviceIdle shouldBe true
      // flush 后可重新接受 RUN
      sim.copy1D(s1, dst, 16); sim.run(3) shouldBe true
      sim.waitComps(1)
    }
  }
}
