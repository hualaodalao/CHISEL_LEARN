import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ArrayBuffer

/** 独立接口测试：事件直接由 TB 注入，金锚只取寄存器/握手契约，不依赖 executor。 */
class HiveCoreRegIfSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveStatusPollApi {
  import HiveSimCommon._
  private val cfg = HiveCoreConfig(cW = 40)
  private val wordMask = BigInt("ffffffff", 16)
  // CONTROL 已定义位：fmt[11:0], addrMode[14:12], relu[15], rnd[18:16],
  // warmdown[20], warmup[21], blocks[22,24,26], loadW[28], requant[29], bias[30]。
  private val controlMask = (0 to 18).foldLeft(BigInt(0))((v, b) => v.setBit(b)) |
    Seq(20, 21, 22, 24, 26, 28, 29, 30).foldLeft(BigInt(0))((v, b) => v.setBit(b))

  private case class StartEvent(pc: BigInt, tag: Int, m: BigInt, aAddr: BigInt)
  private class Driver(val dut: HiveCoreRegIf) {
    val starts = ArrayBuffer[StartEvent]()
    def tick(n: Int = 1): Unit = for (_ <- 0 until n) {
      if (dut.io.start.peek().litToBoolean) starts += StartEvent(
        dut.io.startPc.peek().litValue, dut.io.startTag.peek().litValue.toInt,
        dut.io.regFile.m.peek().litValue, dut.io.regFile.aAddr.peek().litValue)
      dut.clock.step()
    }
    def init(): Unit = {
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B)
      dut.io.busy.poke(false.B)
      dut.io.progress.poke(0.U)
      dut.io.done.poke(false.B)
      dut.io.configError.poke(false.B)
      dut.io.configErrorCode.poke(0.U)
      dut.io.dmaErrors.foreach(_.poke(false.B))
      dut.reset.poke(true.B); tick(3); dut.reset.poke(false.B); tick(2)
      starts.clear()
    }
    def request(op: HiveCoreOp.Type, a0: Int, a1: Int = 0, d0: BigInt = 0,
                d1: BigInt = 0, pc: BigInt = 0, tag: Int = 0): Unit = {
      pokeRegCommand(dut.io.cmd, op, a0, a1, d0, d1, pc, tag)
      var waited = 0
      while (!dut.io.cmd.ready.peek().litToBoolean && waited < 50) { tick(); waited += 1 }
      dut.io.cmd.ready.expect(true.B)
      tick()
      dut.io.cmd.valid.poke(false.B)
    }
    def response(err: Boolean = false): BigInt = {
      var waited = 0
      while (!dut.io.resp.valid.peek().litToBoolean && waited < 50) { tick(); waited += 1 }
      dut.io.resp.valid.expect(true.B)
      dut.io.resp.payload.err.expect(err.B)
      val data = dut.io.resp.payload.data.peek().litValue
      tick()
      data
    }
    def write(a: Int, d: BigInt, err: Boolean = false, pc: BigInt = 0, tag: Int = 0): Unit = {
      request(HiveCoreOp.REG_WRITE1, a, d0 = d, pc = pc, tag = tag)
      response(err) should be(BigInt(0))
    }
    def write2(a0: Int, a1: Int, d0: BigInt, d1: BigInt,
               err: Boolean = false, pc: BigInt = 0, tag: Int = 0): Unit = {
      request(HiveCoreOp.REG_WRITE2, a0, a1, d0, d1, pc, tag)
      response(err) should be(BigInt(0))
    }
    def read(a: Int): BigInt = { request(HiveCoreOp.REG_READ, a); response() }
    def done(): Unit = { dut.io.done.poke(true.B); tick(); dut.io.done.poke(false.B) }
    def configError(code: Int): Unit = {
      dut.io.configErrorCode.poke(code.U); dut.io.configError.poke(true.B)
      tick(); dut.io.configError.poke(false.B)
    }
    def dma(mask: Int, withDone: Boolean = false): Unit = {
      for (i <- 0 until 8) dut.io.dmaErrors(i).poke(((mask & (1 << i)) != 0).B)
      dut.io.done.poke(withDone.B)
      tick()
      dut.io.dmaErrors.foreach(_.poke(false.B)); dut.io.done.poke(false.B)
    }
  }

  behavior of "HiveCoreRegIf"

  it should "reset and decode all 64 addresses without aliasing or truncation" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      HiveCoreOp.REG_WRITE1.litValue should be(BigInt(0))
      HiveCoreOp.REG_WRITE2.litValue should be(BigInt(1))
      HiveCoreOp.REG_READ.litValue should be(BigInt(2))
      HiveCoreOp.all.size should be(3)
      dut.io.cmd.payload.reg0Addr.getWidth should be(6)
      dut.io.cmd.payload.reg1Addr.getWidth should be(6)
      dut.io.start.expect(false.B); dut.io.inFlight.expect(false.B); dut.io.irq.expect(false.B)
      for (a <- 0 until 64) {
        withClue(s"reset address 0x${a.toHexString}: ") {
          s.read(a) should be(BigInt(if (a == IRQ_MASK_REG_ADDR) IRQ_ALL else 0))
        }
      }
      // 地址 bit4/bit5 不能截断：例如 0x20/0x30 绝不能写到 M。
      s.write(M_REG_ADDR, 0x1234)
      val ro = Set(STATUS_REG_ADDR, IRQ_RAW_REG_ADDR, IRQ_MASKED_REG_ADDR, ERROR_CODE_REG_ADDR)
      val writable = (0 to 11).toSet ++ Set(START_REG_ADDR, IRQ_MASK_REG_ADDR, IRQ_CLEAR_REG_ADDR)
      for (a <- 0 until 64 if !writable(a)) {
        s.write(a, wordMask, err = ro(a))
        if (!ro(a)) s.read(a) should be(BigInt(0))
        s.read(M_REG_ADDR) should be(BigInt(0x1234))
      }
      // WRITE1 的第二槽无效，即使它指定 RO/START。
      s.request(HiveCoreOp.REG_WRITE1, A_REG_ADDR, START_REG_ADDR, 0x4567, 1)
      s.response() should be(BigInt(0))
      s.starts.size should be(0)
      s.request(HiveCoreOp.REG_WRITE1, B_REG_ADDR, IRQ_RAW_REG_ADDR, 0x89AB, wordMask)
      s.response() should be(BigInt(0))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0))
    }
  }

  it should "round trip compact configuration fields and zero every reserved bit" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      val values = Seq(BigInt("fedcba98", 16), BigInt("a987c654", 16),
        BigInt("81234567", 16), BigInt("92345678", 16), BigInt("a3456789", 16),
        wordMask, BigInt("b456789a", 16), BigInt("c56789ab", 16),
        BigInt("d6789abc", 16), BigInt("e789abcd", 16), BigInt("f89abcde", 16), wordMask)
      def expected(a: Int, v: BigInt): BigInt = a match {
        case 0 => v & 0xFFFF
        case 5 => v & controlMask
        case 11 => v & BigInt("ffff003f", 16)
        case _ => v & wordMask
      }
      for (a <- values.indices) s.write(a, values(a))
      for (a <- values.indices) {
        withClue(s"compact config address $a: ") { s.read(a) should be(expected(a, values(a))) }
      }
      dut.io.regFile.n.expect(0xC654.U); dut.io.regFile.k.expect(0xA987.U)
      dut.io.regFile.aScaleAddr.expect(values(6).U)
      dut.io.regFile.bScaleAddr.expect(values(7).U)
      dut.io.regFile.cScaleAddr.expect(values(8).U)
      dut.io.regFile.biasAddr.expect(values(9).U)
      dut.io.regFile.rqMBase.expect(values(10).U)
      dut.io.regFile.rqShift.expect(63.U); dut.io.regFile.rqZp.expect((-1).S)
      // 紧邻配置写立即读回，同址 WRITE2 第二项胜出。
      s.write2(M_REG_ADDR, M_REG_ADDR, 0x1111, 0x2222)
      s.read(M_REG_ADDR) should be(BigInt(0x2222))
      s.write2(A_REG_ADDR, A_REG_ADDR, BigInt("11112222", 16), BigInt("ffffeeee", 16))
      s.read(A_REG_ADDR) should be(BigInt("ffffeeee", 16))
      s.write2(0x26, SCALE_A_REG_ADDR, wordMask, 0x13579BDF)
      s.read(0x26) should be(BigInt(0)); s.read(SCALE_A_REG_ADDR) should be(BigInt(0x13579BDF))
      s.write2(SCALE_B_REG_ADDR, 0x27, 0x2468ACE0, wordMask)
      s.read(SCALE_B_REG_ADDR) should be(BigInt(0x2468ACE0)); s.read(0x27) should be(BigInt(0))
      s.starts.size should be(0)
    }
  }

  it should "launch once from either WRITE2 slot and capture updated config and command metadata" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(START_REG_ADDR, 0, pc = 0x1111, tag = 1)
      s.starts.size should be(0); dut.io.inFlight.expect(false.B)
      s.read(START_REG_ADDR) should be(BigInt(0))
      // 任意非零（不要求 bit0），接受后的下一拍 start=1，response 背压不延迟启动。
      dut.io.resp.ready.poke(false.B)
      s.request(HiveCoreOp.REG_WRITE1, START_REG_ADDR, d0 = BigInt("80000000", 16), pc = BigInt("fedc1234", 16), tag = 0xA5)
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      dut.io.startPc.expect(BigInt("fedc1234", 16).U); dut.io.startTag.expect(0xA5.U)
      s.tick(3); dut.io.start.expect(false.B)
      dut.io.resp.ready.poke(true.B); s.response()
      s.starts.size should be(1); s.done()

      val cases = Seq(
        (M_REG_ADDR, START_REG_ADDR, BigInt(0x1234), BigInt(2)),
        (START_REG_ADDR, A_REG_ADDR, BigInt(4), BigInt("89abcdef", 16)),
        (START_REG_ADDR, START_REG_ADDR, BigInt(1), BigInt(8)),
        (START_REG_ADDR, START_REG_ADDR, BigInt(0), BigInt(2)),
        (START_REG_ADDR, START_REG_ADDR, BigInt(2), BigInt(0)))
      for (((a0, a1, d0, d1), i) <- cases.zipWithIndex) {
        val count = s.starts.size
        val pc = BigInt("81230000", 16) + i
        s.write2(a0, a1, d0, d1, pc = pc, tag = 0xF0 + i)
        s.starts.size should be(count + 1)
        s.starts.last.pc should be(pc); s.starts.last.tag should be(0xF0 + i)
        if (a0 == M_REG_ADDR) s.starts.last.m should be(d0)
        if (a1 == A_REG_ADDR) s.starts.last.aAddr should be(d1)
        s.read(START_REG_ADDR) should be(BigInt(0))
        // START 保留先前 done RAW，metadata 不受其他命令 pc/tag 扰动。
        s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1))
        s.request(HiveCoreOp.REG_READ, M_REG_ADDR, pc = 7, tag = 3); s.response()
        dut.io.startPc.expect(pc.U); dut.io.startTag.expect((0xF0 + i).U)
        s.done()
      }
      val count = s.starts.size
      s.write2(START_REG_ADDR, START_REG_ADDR, 0, 0)
      s.starts.size should be(count)
    }
  }

  it should "reserve inFlight before executor busy and atomically reject conflicting WRITE2 commands" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 7); s.write(A_REG_ADDR, 0x12345678)
      s.write(START_REG_ADDR, 1, pc = 0xCAFEBABEL, tag = 0x77)
      dut.io.busy.expect(false.B); dut.io.inFlight.expect(true.B)
      s.read(STATUS_REG_ADDR) should be(BigInt(4))
      dut.io.busy.poke(true.B); dut.io.progress.poke(0xBEEF.U)
      s.read(STATUS_REG_ADDR) should be((BigInt(0xBEEF) << 16) | 5)
      for (a <- 0 to 11) s.write(a, wordMask, err = true)
      s.write(START_REG_ADDR, 2, err = true, pc = 1, tag = 1)
      s.write(START_REG_ADDR, 0)
      s.write2(M_REG_ADDR, START_REG_ADDR, 9, 1, err = true)
      s.write2(START_REG_ADDR, A_REG_ADDR, 1, 0x9999, err = true)
      // 即使 IRQ 槽在前/后，配置冲突使整条命令（含 IRQ 副作用）失效。
      s.dma(1)
      s.write2(IRQ_CLEAR_REG_ADDR, M_REG_ADDR, IRQ_ALL, 99, err = true)
      s.write2(A_REG_ADDR, IRQ_MASK_REG_ADDR, 99, 0, err = true)
      s.read(M_REG_ADDR) should be(BigInt(7)); s.read(A_REG_ADDR) should be(BigInt(0x12345678))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(4)); s.read(IRQ_MASK_REG_ADDR) should be(BigInt(IRQ_ALL))
      for (a <- Seq(STATUS_REG_ADDR, IRQ_RAW_REG_ADDR, IRQ_MASKED_REG_ADDR, ERROR_CODE_REG_ADDR)) s.write(a, 0, err = true)
      s.write(0x3F, wordMask)
      s.write(IRQ_MASK_REG_ADDR, 0); dut.io.irq.expect(true.B)
      s.write(IRQ_CLEAR_REG_ADDR, 4); dut.io.irq.expect(false.B)
      s.write2(IRQ_MASK_REG_ADDR, IRQ_CLEAR_REG_ADDR, IRQ_ALL, IRQ_ALL)
      dut.io.inFlight.expect(true.B); s.starts.size should be(1)
      dut.io.startPc.expect(0xCAFEBABEL.U); dut.io.startTag.expect(0x77.U)
      s.done(); dut.io.inFlight.expect(false.B)
      // admission 以 inFlight 为准，不以 executorBusy/未清 done RAW 为准。
      s.write(M_REG_ADDR, 10); s.read(M_REG_ADDR) should be(BigInt(10))
      s.write(START_REG_ADDR, 8); s.starts.size should be(2)
      s.configError(3); dut.io.inFlight.expect(false.B)
    }
  }

  it should "mask pending interrupts without losing RAW and implement W1C new-event priority" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.done(); s.dma(0xA5)
      val pending = BigInt(1 | (0xA5 << 2))
      s.read(IRQ_RAW_REG_ADDR) should be(pending)
      s.read(IRQ_MASKED_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
      s.write(IRQ_MASK_REG_ADDR, wordMask)
      s.read(IRQ_MASK_REG_ADDR) should be(BigInt(IRQ_ALL))
      s.write(IRQ_MASK_REG_ADDR, 1)
      s.read(IRQ_MASKED_REG_ADDR) should be(pending & ~BigInt(1)); dut.io.irq.expect(true.B)
      s.write(IRQ_MASK_REG_ADDR, 0)
      s.read(IRQ_MASKED_REG_ADDR) should be(pending)
      s.write(IRQ_CLEAR_REG_ADDR, 1)
      s.read(IRQ_RAW_REG_ADDR) should be(pending & ~BigInt(1))
      s.read(IRQ_CLEAR_REG_ADDR) should be(BigInt(0))
      s.write(IRQ_CLEAR_REG_ADDR, BigInt("fffffc00", 16))
      s.read(IRQ_RAW_REG_ADDR) should be(pending & ~BigInt(1))
      // CLEAR 与新 done/DMA 同边沿：所有重新出现的来源保留，其余旧位清零。
      dut.io.done.poke(true.B); dut.io.dmaErrors(1).poke(true.B)
      s.request(HiveCoreOp.REG_WRITE1, IRQ_CLEAR_REG_ADDR, d0 = IRQ_ALL)
      dut.io.done.poke(false.B); dut.io.dmaErrors(1).poke(false.B)
      s.response() should be(BigInt(0))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1 | (1 << 3)))
      // 两 CLEAR 是 W1C 的并集，不是第二槽覆盖第一槽。
      s.write2(IRQ_CLEAR_REG_ADDR, IRQ_CLEAR_REG_ADDR, 1, 1 << 3)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
    }
  }

  it should "latch the first uncleared config code including zero and replace it on clear-event collisions" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(START_REG_ADDR, 1); s.configError(0)
      dut.io.inFlight.expect(false.B)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(2)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.configError(12)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.write(IRQ_CLEAR_REG_ADDR, 1)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(2)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.write(IRQ_CLEAR_REG_ADDR, 2)
      s.configError(13); s.configError(3)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(13))
      s.write(START_REG_ADDR, 2)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(13))
      dut.io.configErrorCode.poke(7.U); dut.io.configError.poke(true.B)
      s.request(HiveCoreOp.REG_WRITE1, IRQ_CLEAR_REG_ADDR, d0 = 2)
      dut.io.configError.poke(false.B); s.response()
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(2)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(7))
      dut.io.inFlight.expect(false.B)
      s.write(IRQ_CLEAR_REG_ADDR, 2)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      // done 与 configError 不互斥，复合事件独立记录。
      dut.io.done.poke(true.B); s.configError(15); dut.io.done.poke(false.B)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(3)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(15))
    }
  }

  it should "record all eight DMA sources immediately without ending inFlight and combine them with done" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(START_REG_ADDR, 1)
      for (i <- 0 until 8) {
        s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
        s.dma(1 << i)
        withClue(s"DMA source $i: ") { s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1) << (i + 2)) }
        dut.io.inFlight.expect(true.B); dut.io.irq.expect(false.B)
        s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      }
      s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
      s.write(IRQ_MASK_REG_ADDR, 0)
      s.dma(0xFF, withDone = true)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x3FD))
      s.read(IRQ_MASKED_REG_ADDR) should be(BigInt(0x3FD))
      dut.io.inFlight.expect(false.B); dut.io.irq.expect(true.B)
      s.write(START_REG_ADDR, 4)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x3FD))
      dut.io.inFlight.expect(true.B)
      s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
    }
  }

  it should "hold responses stable under backpressure without repeating START or W1C side effects" in {
    simulate(new HiveCoreRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 0x1234)
      dut.io.resp.ready.poke(false.B)
      s.request(HiveCoreOp.REG_READ, M_REG_ADDR)
      dut.io.resp.valid.expect(true.B)
      pokeRegCommand(dut.io.cmd, HiveCoreOp.REG_WRITE1, START_REG_ADDR, d0 = 1, pc = 0x76543210, tag = 0xAB)
      for (_ <- 0 until 5) {
        dut.io.cmd.ready.expect(false.B)
        dut.io.resp.valid.expect(true.B); dut.io.resp.payload.data.expect(0x1234.U)
        dut.io.resp.payload.err.expect(false.B); dut.io.start.expect(false.B)
        s.tick()
      }
      dut.io.cmd.valid.poke(false.B)
      dut.io.resp.ready.poke(true.B); s.response() should be(BigInt(0x1234))
      s.starts.size should be(0)
      dut.io.resp.ready.poke(false.B)
      s.request(HiveCoreOp.REG_WRITE2, START_REG_ADDR, START_REG_ADDR, 2, 4, 0x76543210, 0xAB)
      s.tick(8)
      s.starts.size should be(1)
      dut.io.resp.valid.expect(true.B); dut.io.resp.payload.data.expect(0.U); dut.io.resp.payload.err.expect(false.B)
      dut.io.resp.ready.poke(true.B); s.response()
      s.done()
      dut.io.resp.ready.poke(false.B)
      s.request(HiveCoreOp.REG_WRITE1, IRQ_CLEAR_REG_ADDR, d0 = 1)
      s.tick(2)
      s.done() // CLEAR 已 fire，此后新 done 不得因响应仍 HOLD 而被再次清除。
      s.tick(5)
      dut.io.resp.valid.expect(true.B); dut.io.resp.payload.err.expect(false.B)
      dut.io.resp.ready.poke(true.B); s.response()
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1))
      // 错误响应也必须稳定，且拒绝写无副作用。
      s.write(START_REG_ADDR, 1)
      dut.io.resp.ready.poke(false.B)
      s.request(HiveCoreOp.REG_WRITE1, M_REG_ADDR, d0 = 99)
      for (_ <- 0 until 5) {
        dut.io.resp.valid.expect(true.B); dut.io.resp.payload.err.expect(true.B)
        dut.io.resp.payload.data.expect(0.U); s.tick()
      }
      dut.io.resp.ready.poke(true.B); s.response(err = true)
      s.read(M_REG_ADDR) should be(BigInt(0x1234))
      s.starts.size should be(2)
      // STATUS 响应是 read-fire 快照；后续 busy/progress/done 改变不能污染 HOLD 数据。
      dut.io.resp.ready.poke(false.B)
      s.request(HiveCoreOp.REG_READ, STATUS_REG_ADDR)
      dut.io.busy.poke(true.B); dut.io.progress.poke(0xFFFF.U); s.done()
      for (_ <- 0 until 4) {
        dut.io.resp.valid.expect(true.B); dut.io.resp.payload.data.expect(4.U)
        dut.io.resp.payload.err.expect(false.B); s.tick()
      }
      dut.io.resp.ready.poke(true.B); s.response() should be(BigInt(4))
      s.read(STATUS_REG_ADDR) should be(BigInt("ffff0001", 16))
    }
  }
}
