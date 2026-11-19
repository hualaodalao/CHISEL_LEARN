import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable.ArrayBuffer

/** 独立接口测试：事件直接由 TB 注入，金锚只取寄存器/握手契约，不依赖 executor。
  *
  * 契约要点（两组寄存器）：QWR/QRD 只碰 shadow 组且执行期不受限；RUN 把 shadow 整组 dump 进
  *   live 组并启动，`io.regFile` 恒取 live；credit 只门控 RUN。
  */
class WorkerBeeRegIfSpec extends AnyFlatSpec with Matchers with ChiselSim with WbStatusPollApi {
  import WbSimCommon._
  private val cfg = WorkerBeeConfig(cW = 40)
  private val wordMask = BigInt("ffffffff", 16)
  // CONTROL 已定义位：fmt[11:0], addrMode[14:12], relu[15], rnd[18:16],
  // warmdown[20], warmup[21], blocks[22,24,26], osFmtKeep[27], loadW[28], requant[29], bias[30], osM1[31]。
  private val controlMask = (0 to 18).foldLeft(BigInt(0))((v, b) => v.setBit(b)) |
    Seq(20, 21, 22, 24, 26, 27, 28, 29, 30, 31).foldLeft(BigInt(0))((v, b) => v.setBit(b))

  private case class Command(lane: Int, op: WorkerBeeOp.Type, a0: Int, a1: Int = 0,
                             d0: BigInt = 0, d1: BigInt = 0, pc: BigInt = 0, tag: Int = 0)
  private def w(lane: Int, a: Int, d: BigInt, pc: BigInt = 0, tag: Int = 0): Command =
    Command(lane, WorkerBeeOp.REG_WRITE1, a, d0 = d, pc = pc, tag = tag)
  private def w2(lane: Int, a0: Int, a1: Int, d0: BigInt, d1: BigInt,
                 pc: BigInt = 0, tag: Int = 0): Command =
    Command(lane, WorkerBeeOp.REG_WRITE2, a0, a1, d0, d1, pc, tag)
  private def r(lane: Int, a: Int): Command = Command(lane, WorkerBeeOp.REG_READ, a)
  private def run(lane: Int, pc: BigInt = 0, tag: Int = 0, a0: Int = 0, d0: BigInt = 0): Command =
    Command(lane, WorkerBeeOp.RUN, a0, d0 = d0, pc = pc, tag = tag)

  private case class StartEvent(pc: BigInt, tag: Int, m: BigInt, aAddr: BigInt,
                                n: BigInt, k: BigInt, bAddr: BigInt, aScaleAddr: BigInt)
  private class Driver(val dut: WorkerBeeRegIf) {
    val starts = ArrayBuffer[StartEvent]()
    def tick(n: Int = 1): Unit = for (_ <- 0 until n) {
      if (dut.io.start.peek().litToBoolean) starts += StartEvent(
        dut.io.startPc.peek().litValue, dut.io.startTag.peek().litValue.toInt,
        dut.io.regFile.m.peek().litValue, dut.io.regFile.aAddr.peek().litValue,
        dut.io.regFile.n.peek().litValue, dut.io.regFile.k.peek().litValue,
        dut.io.regFile.bAddr.peek().litValue, dut.io.regFile.aScaleAddr.peek().litValue)
      dut.clock.step()
    }
    def idleCommands(): Unit = dut.io.cmd.foreach(_.valid.poke(false.B))
    def allReady(value: Boolean): Unit = dut.io.cmd.foreach(_.ready.expect(value.B))
    def drive(commands: Command*): Unit = {
      require(commands.nonEmpty && commands.map(_.lane).distinct.size == commands.size)
      require(commands.forall(c => c.lane >= 0 && c.lane < 4))
      idleCommands()
      commands.foreach { c =>
        pokeRegCommand(dut.io.cmd(c.lane), c.op, c.a0, c.a1, c.d0, c.d1, c.pc, c.tag)
      }
    }
    // 不等待 ACK、不额外 tick：只跨 cmd.fire 边沿，返回时可立即反压。
    def accept(): Unit = {
      dut.io.resp.ready.poke(false.B)
      allReady(true)
      dut.io.resp.valid.expect(false.B)
      tick()
      idleCommands()
      allReady(false)
      dut.io.resp.valid.expect(true.B)
    }
    def batch(commands: Command*): Unit = { drive(commands: _*); accept() }
    def expectResponse(lane: Int, err: Boolean = false, data: BigInt = 0): Unit = {
      allReady(false)
      dut.io.resp.valid.expect(true.B)
      dut.io.resp.payload.laneId.expect(lane.U)
      dut.io.resp.payload.err.expect(err.B)
      dut.io.resp.payload.data.expect(data.U)
    }
    def hold(lane: Int, err: Boolean = false, data: BigInt = 0, cycles: Int = 3): Unit = {
      dut.io.resp.ready.poke(false.B)
      for (_ <- 0 until cycles) { expectResponse(lane, err, data); tick() }
      expectResponse(lane, err, data)
    }
    // 恰好消费一个已核对的完整 payload，随即恢复反压，避免意外丢下一条 ACK。
    def respond(lane: Int, err: Boolean = false, data: BigInt = 0): Unit = {
      expectResponse(lane, err, data)
      dut.io.resp.ready.poke(true.B)
      allReady(false) // 最后一个 fire 当拍也不允许组合直通 cmd。
      tick()
      dut.io.resp.ready.poke(false.B)
    }
    def init(): Unit = {
      idleCommands()
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
    def request(op: WorkerBeeOp.Type, a0: Int, a1: Int = 0, d0: BigInt = 0,
                d1: BigInt = 0, pc: BigInt = 0, tag: Int = 0): Unit = {
      pokeRegCommand(dut.io.cmd(0), op, a0, a1, d0, d1, pc, tag)
      var waited = 0
      while (!dut.io.cmd(0).ready.peek().litToBoolean && waited < 50) { tick(); waited += 1 }
      dut.io.cmd(0).ready.expect(true.B)
      tick()
      dut.io.cmd(0).valid.poke(false.B)
    }
    def response(err: Boolean = false): BigInt = {
      dut.io.resp.ready.poke(false.B)
      var waited = 0
      while (!dut.io.resp.valid.peek().litToBoolean && waited < 50) { tick(); waited += 1 }
      dut.io.resp.valid.expect(true.B)
      dut.io.resp.payload.laneId.expect(0.U)
      dut.io.resp.payload.err.expect(err.B)
      val data = dut.io.resp.payload.data.peek().litValue
      allReady(false)
      dut.io.resp.ready.poke(true.B)
      tick()
      data
    }
    def write(a: Int, d: BigInt, err: Boolean = false, pc: BigInt = 0, tag: Int = 0): Unit = {
      request(WorkerBeeOp.REG_WRITE1, a, d0 = d, pc = pc, tag = tag)
      response(err) should be(BigInt(0))
    }
    def write2(a0: Int, a1: Int, d0: BigInt, d1: BigInt,
               err: Boolean = false, pc: BigInt = 0, tag: Int = 0): Unit = {
      request(WorkerBeeOp.REG_WRITE2, a0, a1, d0, d1, pc, tag)
      response(err) should be(BigInt(0))
    }
    def read(a: Int): BigInt = { request(WorkerBeeOp.REG_READ, a); response() }
    /** 发一条 RUN 并消费其 ACK。返回时 live 已 dump、inFlight=1、credit=0。 */
    def launch(pc: BigInt = 0, tag: Int = 0): Unit = {
      request(WorkerBeeOp.RUN, 0, pc = pc, tag = tag)
      response() should be(BigInt(0))
    }
    /** dump 后立即结束任务：只关心 live 内容、不关心在途状态的断言用它。 */
    def dump(pc: BigInt = 0, tag: Int = 0): Unit = { launch(pc, tag); done() }
    def credit(): BigInt = dut.io.cfgCredit.peek().litValue
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

  behavior of "WorkerBeeRegIf"

  it should "reset and decode the 10 bit address space without aliasing or truncation" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      WorkerBeeOp.REG_WRITE1.litValue should be(BigInt(0))
      WorkerBeeOp.REG_WRITE2.litValue should be(BigInt(1))
      WorkerBeeOp.REG_READ.litValue should be(BigInt(2))
      WorkerBeeOp.RUN.litValue should be(BigInt(3))
      WorkerBeeOp.all.size should be(4)
      dut.io.cmd(0).payload.reg0Addr.getWidth should be(10)
      dut.io.cmd(0).payload.reg1Addr.getWidth should be(10)
      dut.io.cfgCredit.getWidth should be(1)
      dut.io.start.expect(false.B); dut.io.inFlight.expect(false.B); dut.io.irq.expect(false.B)
      s.credit() should be(BigInt(1))
      for (a <- 0 until 128) {
        withClue(s"reset address 0x${a.toHexString}: ") {
          s.read(a) should be(BigInt(if (a == IRQ_MASK_REG_ADDR) IRQ_ALL else 0))
        }
      }
      // 地址高位不能截断：0x20/0x30/0x40 绝不能写到 M；状态区在 0x40 之后必须独立解码。
      s.write(M_REG_ADDR, 0x1234)
      val ro = Set(STATUS_REG_ADDR, IRQ_RAW_REG_ADDR, IRQ_MASKED_REG_ADDR, ERROR_CODE_REG_ADDR)
      val writable = (0 to 11).toSet ++ Set(IRQ_MASK_REG_ADDR, IRQ_CLEAR_REG_ADDR)
      for (a <- 0 until 128 if !writable(a)) {
        s.write(a, wordMask, err = ro(a))
        if (!ro(a)) s.read(a) should be(BigInt(0))
        s.read(M_REG_ADDR) should be(BigInt(0x1234))
      }
      // 旧 Start=0x10 / Status=0x1E / IRQ=0x30..0x34 已成空洞：写入被接受但无副作用、不启动。
      for (a <- Seq(0x10, 0x1E, 0x30, 0x31, 0x32, 0x33, 0x34)) {
        withClue(s"retired address 0x${a.toHexString}: ") {
          s.write(a, wordMask); s.read(a) should be(BigInt(0))
        }
      }
      s.starts.size should be(0); dut.io.inFlight.expect(false.B)
      s.read(STATUS_REG_ADDR) should be(BigInt(0))
      // WRITE1 的第二槽无效，即使它指定 RO 或 IRQ_CLEAR。
      s.request(WorkerBeeOp.REG_WRITE1, A_REG_ADDR, IRQ_RAW_REG_ADDR, 0x4567, wordMask)
      s.response() should be(BigInt(0))
      s.read(A_REG_ADDR) should be(BigInt(0x4567))
      s.dma(1)
      s.request(WorkerBeeOp.REG_WRITE1, B_REG_ADDR, IRQ_CLEAR_REG_ADDR, 0x89AB, IRQ_ALL)
      s.response() should be(BigInt(0))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1 << 2)) // dma(1) 是源 0 ⟹ RAW bit2
      s.starts.size should be(0)
    }
  }

  it should "round trip compact configuration fields and zero every reserved bit" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
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
      // 配置写只落 shadow：RUN 之前 live 仍是复位值。
      dut.io.regFile.m.expect(0.U); dut.io.regFile.aScaleAddr.expect(0.U)
      s.dump()
      dut.io.regFile.m.expect(expected(0, values(0)).U)
      dut.io.regFile.n.expect(0xC654.U); dut.io.regFile.k.expect(0xA987.U)
      dut.io.regFile.aScaleAddr.expect(values(6).U)
      dut.io.regFile.bScaleAddr.expect(values(7).U)
      dut.io.regFile.cScaleAddr.expect(values(8).U)
      dut.io.regFile.biasAddr.expect(values(9).U)
      dut.io.regFile.rqMBase.expect(values(10).U)
      dut.io.regFile.rqShift.expect(63.U); dut.io.regFile.rqZp.expect((-1).S)
      // 紧邻配置写立即读回，同址 WRITE2 第二项胜出；live 不受这些后续写扰动。
      s.write2(M_REG_ADDR, M_REG_ADDR, 0x1111, 0x2222)
      s.read(M_REG_ADDR) should be(BigInt(0x2222))
      dut.io.regFile.m.expect(expected(0, values(0)).U)
      s.write2(A_REG_ADDR, A_REG_ADDR, BigInt("11112222", 16), BigInt("ffffeeee", 16))
      s.read(A_REG_ADDR) should be(BigInt("ffffeeee", 16))
      s.write2(0x26, SCALE_A_REG_ADDR, wordMask, 0x13579BDF)
      s.read(0x26) should be(BigInt(0)); s.read(SCALE_A_REG_ADDR) should be(BigInt(0x13579BDF))
      s.write2(SCALE_B_REG_ADDR, 0x27, 0x2468ACE0, wordMask)
      s.read(SCALE_B_REG_ADDR) should be(BigInt(0x2468ACE0)); s.read(0x27) should be(BigInt(0))
      s.starts.size should be(1)
    }
  }

  it should "store and clear osM1 on CONTROL bit31 and keep the live copy frozen during execution" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      (s.read(CONTROL_REG_ADDR) & (BigInt(1) << 31)) should be(BigInt(0))
      dut.io.regFile.osM1.expect(false.B)
      s.write(CONTROL_REG_ADDR, BigInt(1) << 31)
      s.read(CONTROL_REG_ADDR) should be(BigInt(1) << 31)
      dut.io.regFile.osM1.expect(false.B) // 尚未 RUN
      s.dump(); dut.io.regFile.osM1.expect(true.B)
      // 不带 bit31 的 CONTROL 写清除 osM1（位域整体重写语义）。
      s.write(CONTROL_REG_ADDR, BigInt(1) << 30)
      s.read(CONTROL_REG_ADDR) should be(BigInt(1) << 30)
      s.dump(); dut.io.regFile.osM1.expect(false.B)
      // 在途配置写合法：写进 shadow，正在执行的 live 不动。
      s.launch()
      s.credit() should be(BigInt(0))
      s.write(CONTROL_REG_ADDR, BigInt(1) << 31)
      s.read(CONTROL_REG_ADDR) should be(BigInt(1) << 31)
      dut.io.regFile.osM1.expect(false.B)
      s.done()
      s.dump(); dut.io.regFile.osM1.expect(true.B)
    }
  }

  it should "store and clear osFmtKeep on CONTROL bit27 independently of neighbouring fields" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      (s.read(CONTROL_REG_ADDR) & (BigInt(1) << 27)) should be(BigInt(0))
      dut.io.regFile.osFmtKeep.expect(false.B)
      s.write(CONTROL_REG_ADDR, BigInt(1) << 27)
      s.read(CONTROL_REG_ADDR) should be(BigInt(1) << 27)
      s.dump()
      dut.io.regFile.osFmtKeep.expect(true.B)
      dut.io.regFile.loadWMode.expect(false.B)
      dut.io.regFile.cMxBlockSize.expect(MXBlockSize.M16)
      // 相邻 bit26(cMxBlk)/bit28(loadW) 置位而 bit27 清零：osFmtKeep 随整体重写清除
      s.write(CONTROL_REG_ADDR, (BigInt(1) << 26) | (BigInt(1) << 28))
      s.read(CONTROL_REG_ADDR) should be((BigInt(1) << 26) | (BigInt(1) << 28))
      s.launch()
      s.write(CONTROL_REG_ADDR, BigInt(1) << 27)
      dut.io.regFile.osFmtKeep.expect(false.B)
      s.done()
      s.dump(); dut.io.regFile.osFmtKeep.expect(true.B)
    }
  }

  it should "launch from RUN without writing any register and capture command metadata" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 0x1234); s.write(A_REG_ADDR, BigInt("89abcdef", 16))
      // RUN 携带的地址/数据被忽略：即使指向 M 且数据非零也不得写寄存器。
      dut.io.resp.ready.poke(false.B)
      s.request(WorkerBeeOp.RUN, M_REG_ADDR, d0 = wordMask,
        pc = BigInt("fedc1234", 16), tag = 0xA5)
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      s.credit() should be(BigInt(0))
      dut.io.startPc.expect(BigInt("fedc1234", 16).U); dut.io.startTag.expect(0xA5.U)
      s.tick(3); dut.io.start.expect(false.B) // response 背压不延迟启动
      dut.io.resp.ready.poke(true.B); s.response() should be(BigInt(0))
      s.starts.size should be(1)
      s.starts.last.m should be(BigInt(0x1234))
      s.starts.last.aAddr should be(BigInt("89abcdef", 16))
      s.read(M_REG_ADDR) should be(BigInt(0x1234))
      s.done()
      for (i <- 0 until 4) {
        val pc = BigInt("81230000", 16) + i
        s.write(M_REG_ADDR, 0x20 + i)
        s.launch(pc = pc, tag = 0xF0 + i)
        s.starts.size should be(i + 2)
        s.starts.last.pc should be(pc); s.starts.last.tag should be(0xF0 + i)
        s.starts.last.m should be(BigInt(0x20 + i))
        // RUN 不清 RAW；metadata 不受其他命令 pc/tag 扰动。
        s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1))
        s.request(WorkerBeeOp.REG_READ, M_REG_ADDR, pc = 7, tag = 3); s.response()
        dut.io.startPc.expect(pc.U); dut.io.startTag.expect((0xF0 + i).U)
        s.done()
      }
    }
  }

  it should "reserve inFlight before executor busy and let shadow writes overlap the running task" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 7); s.write(A_REG_ADDR, 0x12345678)
      s.launch(pc = 0xCAFEBABEL, tag = 0x77)
      dut.io.busy.expect(false.B); dut.io.inFlight.expect(true.B)
      s.credit() should be(BigInt(0))
      s.read(STATUS_REG_ADDR) should be(BigInt(4))
      dut.io.busy.poke(true.B); dut.io.progress.poke(0xBEEF.U)
      s.read(STATUS_REG_ADDR) should be((BigInt(0xBEEF) << 16) | 5)
      // 执行期配置写全部成功，但只改 shadow。
      for (a <- 0 to 11) s.write(a, wordMask)
      s.read(M_REG_ADDR) should be(BigInt(0xFFFF))
      dut.io.regFile.m.expect(7.U); dut.io.regFile.aAddr.expect(0x12345678.U)
      // 只读寄存器仍然拒写。
      for (a <- Seq(STATUS_REG_ADDR, IRQ_RAW_REG_ADDR, IRQ_MASKED_REG_ADDR, ERROR_CODE_REG_ADDR))
        s.write(a, 0, err = true)
      // IRQ 副作用在途仍然生效，且与配置写共存的 WRITE2 不再被整条拒绝。
      s.dma(1)
      s.write2(IRQ_CLEAR_REG_ADDR, M_REG_ADDR, IRQ_ALL, 99)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0))
      s.read(M_REG_ADDR) should be(BigInt(99))
      dut.io.regFile.m.expect(7.U)
      s.write(IRQ_MASK_REG_ADDR, 0)
      s.dma(1); dut.io.irq.expect(true.B)
      s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL); dut.io.irq.expect(false.B)
      dut.io.inFlight.expect(true.B); s.starts.size should be(1)
      dut.io.startPc.expect(0xCAFEBABEL.U); dut.io.startTag.expect(0x77.U)
      s.done(); dut.io.inFlight.expect(false.B); s.credit() should be(BigInt(1))
      // 下一次 RUN 才把重叠期准备好的 shadow 提交进 live。
      s.write(M_REG_ADDR, 10)
      s.launch(); s.starts.size should be(2); s.starts.last.m should be(BigInt(10))
      s.configError(3); dut.io.inFlight.expect(false.B); s.credit() should be(BigInt(1))
    }
  }

  it should "mask pending interrupts without losing RAW and implement W1C new-event priority" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
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
      s.write(IRQ_CLEAR_REG_ADDR, BigInt("fffff800", 16))
      s.read(IRQ_RAW_REG_ADDR) should be(pending & ~BigInt(1))
      // CLEAR 与新 done/DMA 同边沿：所有重新出现的来源保留，其余旧位清零。
      dut.io.done.poke(true.B); dut.io.dmaErrors(1).poke(true.B)
      s.request(WorkerBeeOp.REG_WRITE1, IRQ_CLEAR_REG_ADDR, d0 = IRQ_ALL)
      dut.io.done.poke(false.B); dut.io.dmaErrors(1).poke(false.B)
      s.response() should be(BigInt(0))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1 | (1 << 3)))
      // 两 CLEAR 是 W1C 的并集，不是第二槽覆盖第一槽。
      s.write2(IRQ_CLEAR_REG_ADDR, IRQ_CLEAR_REG_ADDR, 1, 1 << 3)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
    }
  }

  it should "latch the first uncleared config code including zero and replace it on clear-event collisions" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.launch(); s.configError(0)
      dut.io.inFlight.expect(false.B)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(2)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.configError(12)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.write(IRQ_CLEAR_REG_ADDR, 1)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(2)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.write(IRQ_CLEAR_REG_ADDR, 2)
      s.configError(13); s.configError(3)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(13))
      s.launch() // RUN 不清错误码
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(13))
      dut.io.configErrorCode.poke(7.U); dut.io.configError.poke(true.B)
      s.request(WorkerBeeOp.REG_WRITE1, IRQ_CLEAR_REG_ADDR, d0 = 2)
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

  it should "record all eight DMA sources immediately without ending inFlight or returning credit" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.launch()
      for (i <- 0 until 8) {
        s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
        s.dma(1 << i)
        withClue(s"DMA source $i: ") { s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1) << (i + 2)) }
        dut.io.inFlight.expect(true.B); dut.io.irq.expect(false.B)
        s.credit() should be(BigInt(0))
        s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      }
      s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
      s.write(IRQ_MASK_REG_ADDR, 0)
      s.dma(0xFF, withDone = true)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x3FD))
      s.read(IRQ_MASKED_REG_ADDR) should be(BigInt(0x3FD))
      dut.io.inFlight.expect(false.B); dut.io.irq.expect(true.B)
      s.credit() should be(BigInt(1))
      s.launch()
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x3FD))
      dut.io.inFlight.expect(true.B)
      s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
    }
  }

  it should "hold responses stable under backpressure without repeating RUN or W1C side effects" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 0x1234)
      dut.io.resp.ready.poke(false.B)
      s.request(WorkerBeeOp.REG_READ, M_REG_ADDR)
      dut.io.resp.valid.expect(true.B)
      pokeRegCommand(dut.io.cmd(0), WorkerBeeOp.RUN, 0, pc = 0x76543210, tag = 0xAB)
      for (_ <- 0 until 5) {
        dut.io.cmd(0).ready.expect(false.B)
        dut.io.resp.valid.expect(true.B); dut.io.resp.payload.data.expect(0x1234.U)
        dut.io.resp.payload.err.expect(false.B); dut.io.start.expect(false.B)
        s.tick()
      }
      dut.io.cmd(0).valid.poke(false.B)
      dut.io.resp.ready.poke(true.B); s.response() should be(BigInt(0x1234))
      s.starts.size should be(0)
      // RUN 只 fire 一次：ACK 长时间 HOLD 不得重复 dump/启动。
      dut.io.resp.ready.poke(false.B)
      s.request(WorkerBeeOp.RUN, 0, pc = 0x76543210, tag = 0xAB)
      s.tick(8)
      s.starts.size should be(1)
      dut.io.resp.valid.expect(true.B); dut.io.resp.payload.data.expect(0.U)
      dut.io.resp.payload.err.expect(false.B)
      dut.io.resp.ready.poke(true.B); s.response() should be(BigInt(0))
      s.done()
      dut.io.resp.ready.poke(false.B)
      s.request(WorkerBeeOp.REG_WRITE1, IRQ_CLEAR_REG_ADDR, d0 = 1)
      s.tick(2)
      s.done() // CLEAR 已 fire，此后新 done 不得因响应仍 HOLD 而被再次清除。
      s.tick(5)
      dut.io.resp.valid.expect(true.B); dut.io.resp.payload.err.expect(false.B)
      dut.io.resp.ready.poke(true.B); s.response()
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1))
      // 错误响应也必须稳定，且拒绝写无副作用。
      s.launch()
      dut.io.resp.ready.poke(false.B)
      s.request(WorkerBeeOp.REG_WRITE1, STATUS_REG_ADDR, d0 = 99)
      for (_ <- 0 until 5) {
        dut.io.resp.valid.expect(true.B); dut.io.resp.payload.err.expect(true.B)
        dut.io.resp.payload.data.expect(0.U); s.tick()
      }
      dut.io.resp.ready.poke(true.B); s.response(err = true)
      s.read(M_REG_ADDR) should be(BigInt(0x1234))
      s.starts.size should be(2)
      // STATUS 响应是 read-fire 快照；后续 busy/progress/done 改变不能污染 HOLD 数据。
      dut.io.resp.ready.poke(false.B)
      s.request(WorkerBeeOp.REG_READ, STATUS_REG_ADDR)
      dut.io.busy.poke(true.B); dut.io.progress.poke(0xFFFF.U); s.done()
      for (_ <- 0 until 4) {
        dut.io.resp.valid.expect(true.B); dut.io.resp.payload.data.expect(4.U)
        dut.io.resp.payload.err.expect(false.B); s.tick()
      }
      dut.io.resp.ready.poke(true.B); s.response() should be(BigInt(4))
      s.read(STATUS_REG_ADDR) should be(BigInt("ffff0001", 16))
    }
  }

  it should "commit eight distinct WRITE2 addresses in one batch and accept sparse lanes without lane0" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      dut.io.cmd.length should be(4)
      dut.io.resp.payload.data.getWidth should be(32)
      dut.io.resp.payload.err.getWidth should be(1)
      dut.io.resp.payload.laneId.getWidth should be(2)
      s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0x7FF))
      s.batch(w2(0, M_REG_ADDR, NK_REG_ADDR, 0x1234, 0x56789ABC),
        w2(1, A_REG_ADDR, B_REG_ADDR, 0x11112222, 0x33334444),
        w2(2, C_REG_ADDR, SCALE_A_REG_ADDR, 0x55556666, 0x77778888),
        w2(3, SCALE_B_REG_ADDR, SCALE_C_REG_ADDR, 0x12345678, 0x23456789))
      // 写提交不等 ACK；四路均 fire，八项没有互相覆盖。
      for (lane <- 0 until 4) { s.hold(lane, cycles = 2); s.respond(lane) }
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      val expected = Seq(0 -> BigInt(0x1234), 1 -> BigInt(0x56789ABC),
        2 -> BigInt(0x11112222), 3 -> BigInt(0x33334444), 4 -> BigInt(0x55556666),
        6 -> BigInt(0x77778888), 7 -> BigInt(0x12345678), 8 -> BigInt(0x23456789))
      expected.foreach { case (a, value) => s.read(a) should be(value) }
      dut.io.regFile.m.expect(0.U) // 无 RUN ⟹ live 未提交
      s.dump()
      dut.io.regFile.m.expect(0x1234.U)
      dut.io.regFile.n.expect(0x9ABC.U); dut.io.regFile.k.expect(0x5678.U)
      dut.io.regFile.aAddr.expect(0x11112222.U); dut.io.regFile.bAddr.expect(0x33334444.U)
      dut.io.regFile.cAddr.expect(0x55556666.U); dut.io.regFile.aScaleAddr.expect(0x77778888.U)
      dut.io.regFile.bScaleAddr.expect(0x12345678.U); dut.io.regFile.cScaleAddr.expect(0x23456789.U)
      s.batch(w2(3, BIAS_REG_ADDR, RQM_REG_ADDR, 0x3456789A, 0x456789AB),
        w2(1, M_REG_ADDR, RQ_PARAM_REG_ADDR, 0xABCD, BigInt("89ab0035", 16)))
      dut.io.cmd(0).valid.expect(false.B); dut.io.cmd(2).valid.expect(false.B)
      s.hold(1); s.respond(1); s.hold(3); s.respond(3)
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      s.read(M_REG_ADDR) should be(BigInt(0xABCD))
      s.read(BIAS_REG_ADDR) should be(BigInt(0x3456789A))
      s.read(RQM_REG_ADDR) should be(BigInt(0x456789AB))
      s.read(RQ_PARAM_REG_ADDR) should be(BigInt("89ab0035", 16))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1)) // 仅 dump 的 done 位，无 WRITE_CONFLICT
    }
  }

  it should "return four read snapshots and sparse lane0 lane2 reads strictly in ascending lane order" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 0x1357); s.write(A_REG_ADDR, 0x2468ACE0)
      s.done(); dut.io.busy.poke(true.B); dut.io.progress.poke(0x1234.U)
      s.batch(r(3, IRQ_RAW_REG_ADDR), r(1, A_REG_ADDR), r(0, M_REG_ADDR), r(2, STATUS_REG_ADDR))
      val expected = Seq(BigInt(0x1357), BigInt(0x2468ACE0), BigInt(0x12340001), BigInt(1))
      for (lane <- 0 until 4) {
        s.hold(lane, data = expected(lane), cycles = 2)
        s.respond(lane, data = expected(lane))
      }
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      s.batch(r(2, IRQ_MASK_REG_ADDR), r(0, A_REG_ADDR))
      s.hold(0, data = 0x2468ACE0); s.respond(0, data = 0x2468ACE0)
      s.hold(2, data = 0x7FF); s.respond(2, data = 0x7FF)
      s.allReady(true); dut.io.resp.valid.expect(false.B)
    }
  }

  it should "snapshot the old shadow and IRQ for a write read write read batch while writes and new events commit" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 7); s.write(A_REG_ADDR, 0x11112222); s.write(B_REG_ADDR, 0x33334444)
      dut.io.done.poke(true.B); dut.io.dmaErrors(1).poke(true.B)
      s.batch(w2(0, A_REG_ADDR, M_REG_ADDR, 0xAAAA, 9), r(1, A_REG_ADDR),
        w2(2, A_REG_ADDR, B_REG_ADDR, 0xBBBB, 0xCCCC), r(3, IRQ_RAW_REG_ADDR))
      dut.io.done.poke(false.B); dut.io.dmaErrors(1).poke(false.B)
      dut.io.regFile.aAddr.expect(0.U); dut.io.inFlight.expect(false.B)
      s.hold(0); s.respond(0)
      s.hold(1, data = 0x11112222); s.respond(1, data = 0x11112222)
      s.hold(2); s.respond(2)
      s.hold(3, data = 0); s.respond(3, data = 0)
      s.read(A_REG_ADDR) should be(BigInt(0xBBBB))
      s.read(B_REG_ADDR) should be(BigInt(0xCCCC)); s.read(M_REG_ADDR) should be(BigInt(9))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x409)) // done + DMA[1] + WRITE_CONFLICT
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.dump()
      dut.io.regFile.aAddr.expect(0xBBBB.U); dut.io.regFile.bAddr.expect(0xCCCC.U)
      dut.io.regFile.m.expect(9.U)
    }
  }

  it should "snapshot STATUS MASK MASKED RAW and ERROR_CODE before same batch writes RUN and fresh events" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.configError(6); s.done(); s.write(IRQ_MASK_REG_ADDR, 0)
      dut.io.done.poke(true.B); dut.io.dmaErrors(7).poke(true.B)
      dut.io.configError.poke(true.B); dut.io.configErrorCode.poke(9.U)
      s.batch(w2(0, IRQ_MASK_REG_ADDR, IRQ_CLEAR_REG_ADDR, 0x7FF, 0x7FF),
        r(1, IRQ_RAW_REG_ADDR), r(2, IRQ_MASK_REG_ADDR), r(3, IRQ_MASKED_REG_ADDR))
      dut.io.done.poke(false.B); dut.io.dmaErrors(7).poke(false.B); dut.io.configError.poke(false.B)
      dut.io.irq.expect(false.B)
      s.respond(0); s.hold(1, data = 3); s.respond(1, data = 3)
      s.hold(2, data = 0); s.respond(2, data = 0)
      s.hold(3, data = 3); s.respond(3, data = 3)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x203))
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(9))
      s.write(M_REG_ADDR, 17)
      dut.io.busy.poke(true.B); dut.io.progress.poke(0x1234.U)
      s.batch(run(0, pc = 0x12345678, tag = 0x91),
        r(1, STATUS_REG_ADDR), r(2, ERROR_CODE_REG_ADDR), r(3, IRQ_MASK_REG_ADDR))
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      dut.io.busy.poke(false.B); dut.io.progress.poke(0xFFFF.U)
      s.done() // 读仍是 RUN/done 前的 busy=1、inFlight=0、progress=0x1234。
      s.respond(0); s.hold(1, data = 0x12340001); s.respond(1, data = 0x12340001)
      s.respond(2, data = 9); s.respond(3, data = 0x7FF)
      s.starts.size should be(1); s.starts.last.m should be(BigInt(17))
      s.read(STATUS_REG_ADDR) should be(BigInt("ffff0000", 16))
      dut.io.configError.poke(true.B); dut.io.configErrorCode.poke(11.U)
      s.batch(r(0, ERROR_CODE_REG_ADDR), w(2, IRQ_CLEAR_REG_ADDR, 2))
      dut.io.configError.poke(false.B)
      s.hold(0, data = 9); s.respond(0, data = 9); s.respond(2)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(11))
    }
  }

  it should "arbitrate all six lane pairs and all four WRITE2 slot pairings including same value conflicts" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      val pairs = for (low <- 0 until 4; high <- low + 1 until 4) yield (low, high)
      for (((low, high), pairIndex) <- pairs.zipWithIndex; lowSlot <- 0 until 2; highSlot <- 0 until 2) {
        withClue(s"lanes $low/$high slots $lowSlot/$highSlot: ") {
          s.write(IRQ_CLEAR_REG_ADDR, 0x400)
          // 覆盖全部 0x00..0x0B；所有数值低于 64，不碰 CONTROL/RQ_PARAM 保留位。
          val shared = (pairIndex * 4 + lowSlot * 2 + highSlot) % 12
          val lowOther = (shared + 1) % 12; val highOther = (shared + 2) % 12
          val lowData = BigInt(0x20 + low * 4 + lowSlot)
          val highData = BigInt(0x20 + high * 4 + highSlot)
          val lowCommand = if (lowSlot == 0) w2(low, shared, lowOther, lowData, 0x10 + low)
            else w2(low, lowOther, shared, 0x10 + low, lowData)
          val highCommand = if (highSlot == 0) w2(high, shared, highOther, highData, 0x18 + high)
            else w2(high, highOther, shared, 0x18 + high, highData)
          s.batch(highCommand, lowCommand)
          s.hold(low, cycles = 1); s.respond(low); s.hold(high, cycles = 1); s.respond(high)
          s.read(shared) should be(highData)
          s.read(lowOther) should be(BigInt(0x10 + low))
          s.read(highOther) should be(BigInt(0x18 + high))
          s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x400))
          s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0)); dut.io.inFlight.expect(false.B)
        }
      }
      for ((low, high) <- pairs) {
        s.write(IRQ_CLEAR_REG_ADDR, 0x400)
        s.batch(w(high, M_REG_ADDR, 0x55), w(low, M_REG_ADDR, 0x55))
        s.respond(low); s.respond(high)
        s.read(M_REG_ADDR) should be(BigInt(0x55))
        s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x400))
      }
    }
  }

  it should "keep nonconflicting WRITE2 slots and exclude every slot of a rejected high lane from arbitration" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.batch(w2(0, A_REG_ADDR, B_REG_ADDR, 0x1111, 0x2222),
        w(1, A_REG_ADDR, 0x3333), w(2, B_REG_ADDR, 0x4444),
        w2(3, B_REG_ADDR, STATUS_REG_ADDR, 0xFFFF, 0))
      dut.io.regFile.aAddr.expect(0.U)
      s.respond(0); s.respond(1); s.respond(2); s.hold(3, err = true); s.respond(3, err = true)
      s.read(A_REG_ADDR) should be(BigInt(0x3333)); s.read(B_REG_ADDR) should be(BigInt(0x4444))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x400))
      val readOnly = Seq(STATUS_REG_ADDR, IRQ_RAW_REG_ADDR, IRQ_MASKED_REG_ADDR, ERROR_CODE_REG_ADDR)
      for (ro <- readOnly; badSlot <- 0 until 2) {
        s.write(IRQ_CLEAR_REG_ADDR, 0x7FF)
        val before = s.read(A_REG_ADDR)
        val rejected = if (badSlot == 0) w2(3, ro, A_REG_ADDR, 0, 0xDEAD)
          else w2(3, A_REG_ADDR, ro, 0xDEAD, 0)
        s.batch(w2(0, A_REG_ADDR, B_REG_ADDR, 0x5678, 0x9ABC), r(1, A_REG_ADDR), rejected)
        dut.io.regFile.aAddr.expect(0.U)
        s.respond(0); s.respond(1, data = before); s.respond(3, err = true)
        s.read(A_REG_ADDR) should be(BigInt(0x5678)); s.read(B_REG_ADDR) should be(BigInt(0x9ABC))
        s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0))
        s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      }
      // RO 使整条拒绝，不能改 MASK 或 W1C；高 lane 也不能阻挡合法低 lane。
      s.done(); s.write(IRQ_MASK_REG_ADDR, 0x7FF)
      s.batch(w(0, IRQ_MASK_REG_ADDR, 0), w2(1, IRQ_MASK_REG_ADDR, IRQ_RAW_REG_ADDR, 0, 0),
        w2(2, IRQ_CLEAR_REG_ADDR, ERROR_CODE_REG_ADDR, 1, 0),
        w2(3, IRQ_MASK_REG_ADDR, STATUS_REG_ADDR, 0x7FF, 0))
      s.respond(0); s.respond(1, err = true); s.respond(2, err = true); s.respond(3, err = true)
      s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0)); s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1))
      s.starts.size should be(0); dut.io.inFlight.expect(false.B)
    }
  }

  it should "accept configuration writes on the done or configError edge and commit them only at the next RUN" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 7); s.write(A_REG_ADDR, 0x1234); s.launch()
      s.dma(1)
      dut.io.done.poke(true.B)
      s.batch(w2(0, IRQ_CLEAR_REG_ADDR, M_REG_ADDR, 0x7FF, 99),
        w2(1, A_REG_ADDR, IRQ_MASK_REG_ADDR, 0xFFFF, 0),
        w2(2, C_REG_ADDR, 0x3F, 2, 0), r(3, STATUS_REG_ADDR))
      dut.io.done.poke(false.B)
      dut.io.inFlight.expect(false.B); s.credit() should be(BigInt(1))
      dut.io.regFile.m.expect(7.U); dut.io.regFile.aAddr.expect(0x1234.U)
      for (lane <- 0 until 3) { s.hold(lane, cycles = 2); s.respond(lane) }
      s.respond(3, data = 4) // STATUS 是 fire 前快照：inFlight=1
      s.read(M_REG_ADDR) should be(BigInt(99)); s.read(A_REG_ADDR) should be(BigInt(0xFFFF))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(1)); s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0))
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0)); s.starts.size should be(1)
      s.launch(); s.starts.size should be(2)
      dut.io.regFile.m.expect(99.U); dut.io.regFile.aAddr.expect(0xFFFF.U)
      dut.io.regFile.cAddr.expect(2.U)
      s.write(IRQ_CLEAR_REG_ADDR, 0x7FF)
      dut.io.configError.poke(true.B); dut.io.configErrorCode.poke(6.U)
      s.batch(w2(0, IRQ_MASK_REG_ADDR, B_REG_ADDR, 0, 0x77), w(1, IRQ_CLEAR_REG_ADDR, 0x7FF),
        r(2, STATUS_REG_ADDR), w2(3, IRQ_MASK_REG_ADDR, M_REG_ADDR, 0x7FF, 88))
      dut.io.configError.poke(false.B)
      s.respond(0); s.respond(1); s.respond(2, data = 4); s.respond(3)
      s.read(M_REG_ADDR) should be(BigInt(88)); s.read(B_REG_ADDR) should be(BigInt(0x77))
      s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0x7FF))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x402)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(6))
      dut.io.inFlight.expect(false.B); s.starts.size should be(2)
      dut.io.regFile.m.expect(99.U); dut.io.regFile.bAddr.expect(0.U)
    }
  }

  it should "dump the pre batch shadow so same batch configuration writes belong to the next submission" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.configError(5)
      s.write(M_REG_ADDR, 0x3456); s.write(NK_REG_ADDR, 0x56789ABC)
      s.write(A_REG_ADDR, 0x12345678); s.write(B_REG_ADDR, 0x23456789)
      s.write(SCALE_A_REG_ADDR, 0x3456789A)
      s.batch(w(0, M_REG_ADDR, 0x9999), run(1, pc = 0x11111111, tag = 0x11),
        r(2, M_REG_ADDR), w(3, SCALE_A_REG_ADDR, BigInt("aaaaaaaa", 16)))
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      dut.io.startPc.expect(0x11111111.U); dut.io.startTag.expect(0x11.U)
      s.starts.size should be(0) // accept 只跨接受边沿；下一拍脉冲由 tick 捕获。
      s.hold(0, cycles = 8)
      s.starts.size should be(1); dut.io.start.expect(false.B); dut.io.inFlight.expect(true.B)
      s.starts.last should be(StartEvent(0x11111111, 0x11, 0x3456,
        0x12345678, 0x9ABC, 0x5678, 0x23456789, 0x3456789A))
      s.done(); dut.io.inFlight.expect(false.B)
      s.respond(0); s.hold(1, cycles = 2); s.respond(1)
      s.hold(2, data = 0x3456, cycles = 2); s.respond(2, data = 0x3456)
      s.respond(3)
      s.tick(3); s.starts.size should be(1); dut.io.inFlight.expect(false.B)
      // RUN 不是写命令 ⟹ 与配置写同批不产生 WRITE_CONFLICT。
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x3))
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(5))
      s.read(M_REG_ADDR) should be(BigInt(0x9999))
      s.read(SCALE_A_REG_ADDR) should be(BigInt("aaaaaaaa", 16))
      s.launch()
      dut.io.regFile.m.expect(0x9999.U); dut.io.regFile.aScaleAddr.expect(BigInt("aaaaaaaa", 16).U)
      s.done()
    }
  }

  it should "gate RUN by credit and grant only the lowest RUN lane of a batch" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.credit() should be(BigInt(1))
      s.write(M_REG_ADDR, 7)
      s.drive(run(1, pc = 0x1111, tag = 0x11), run(3, pc = 0x3333, tag = 0x33),
        w(0, A_REG_ADDR, BigInt("aaaa", 16)))
      dut.io.resp.ready.poke(false.B)
      dut.io.cmd(0).ready.expect(true.B)
      dut.io.cmd(1).ready.expect(true.B)
      dut.io.cmd(2).ready.expect(true.B) // 未驱动 lane 的 ready 只随响应反压
      dut.io.cmd(3).ready.expect(false.B) // 同批第二条 RUN 不获授权
      dut.io.resp.valid.expect(false.B)
      s.tick()
      dut.io.cmd(0).valid.poke(false.B); dut.io.cmd(1).valid.poke(false.B)
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      s.credit() should be(BigInt(0))
      s.tick(2)
      s.starts.size should be(1)
      s.starts.last.pc should be(BigInt(0x1111)); s.starts.last.tag should be(0x11)
      s.starts.last.m should be(BigInt(7)); s.starts.last.aAddr should be(BigInt(0))
      s.respond(0); s.respond(1)
      // credit=0 期间等待的 RUN 不得 fire，也不得重复启动。
      for (_ <- 0 until 3) { dut.io.cmd(3).ready.expect(false.B); s.tick() }
      s.starts.size should be(1); s.credit() should be(BigInt(0))
      // done 边沿按拍前 inFlight=1 仍拒绝 RUN；credit 在该边沿归还，下一拍才准入。
      s.done()
      s.credit() should be(BigInt(1))
      dut.io.cmd(3).ready.expect(true.B); dut.io.start.expect(false.B)
      s.tick()
      dut.io.cmd(3).valid.poke(false.B)
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      s.credit() should be(BigInt(0))
      s.tick(2)
      s.starts.size should be(2)
      s.starts.last.pc should be(BigInt(0x3333)); s.starts.last.tag should be(0x33)
      s.starts.last.aAddr should be(BigInt("aaaa", 16))
      s.respond(3); s.done()
      s.credit() should be(BigInt(1))
      // 三条 lane 的 payload 仍残留 RUN 但 valid=0：ready 不得被残留 op 拉低。
      s.allReady(true); dut.io.resp.valid.expect(false.B)
    }
  }

  it should "return credit on done and configError but never on DMA errors" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.credit() should be(BigInt(1))
      s.launch(); s.credit() should be(BigInt(0))
      s.dma(0xFF); s.credit() should be(BigInt(0)); dut.io.inFlight.expect(true.B)
      s.configError(4); s.credit() should be(BigInt(1)); dut.io.inFlight.expect(false.B)
      s.launch(); s.credit() should be(BigInt(0))
      s.done(); s.credit() should be(BigInt(1)); dut.io.inFlight.expect(false.B)
      // credit 只门控 RUN：credit=0 时 QWR/QRD 依旧畅通。
      s.launch()
      s.write(M_REG_ADDR, 0x4321); s.read(M_REG_ADDR) should be(BigInt(0x4321))
      s.write2(A_REG_ADDR, B_REG_ADDR, 0x1111, 0x2222)
      s.read(B_REG_ADDR) should be(BigInt(0x2222))
      s.done()
    }
  }

  it should "treat RUN as a non writing command even when its address names RO or IRQ registers" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut)
      for (target <- Seq(A_REG_ADDR, IRQ_CLEAR_REG_ADDR, IRQ_MASK_REG_ADDR,
        STATUS_REG_ADDR, M_REG_ADDR)) {
        withClue(s"RUN address 0x${target.toHexString}: ") {
          s.init()
          s.write(A_REG_ADDR, 0x1234); s.done()
          s.write(IRQ_CLEAR_REG_ADDR, IRQ_ALL)
          val value = target match {
            case A_REG_ADDR => BigInt(0x5678)
            case IRQ_CLEAR_REG_ADDR => BigInt(IRQ_ALL)
            case IRQ_MASK_REG_ADDR => BigInt(0)
            case _ => wordMask
          }
          s.batch(run(0, pc = 0x12345678, tag = 0x21, a0 = target, d0 = value),
            r(1, IRQ_RAW_REG_ADDR), w(2, M_REG_ADDR, 0x33))
          s.respond(0); s.respond(1, data = 0); s.respond(2)
          s.read(M_REG_ADDR) should be(BigInt(0x33))
          s.read(A_REG_ADDR) should be(BigInt(0x1234))
          s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0))
          s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0x7FF))
          s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
          dut.io.inFlight.expect(true.B); s.starts.size should be(1)
          s.starts.last.pc should be(BigInt(0x12345678)); s.starts.last.tag should be(0x21)
          s.done()
        }
      }
    }
  }

  it should "ignore retired address holes and merge same lane duplicate addresses without a conflict event" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.batch(w(0, 0x26, 0xFFFF), run(1, pc = 0x13572468, tag = 0x31), r(2, 0x10),
        w2(3, 0x3F, 0x1E, 1, 2))
      dut.io.start.expect(true.B); s.hold(0, cycles = 4)
      s.starts.size should be(1); s.starts.last.pc should be(BigInt(0x13572468))
      s.starts.last.tag should be(0x31)
      s.respond(0); s.respond(1); s.respond(2, data = 0); s.respond(3)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.inFlight.expect(true.B)
      s.done(); s.write(IRQ_CLEAR_REG_ADDR, 0x7FF)
      // RUN 与同址 READ/WRITE 混批不产生 WRITE_CONFLICT；RUN 自身不写 M。
      s.batch(run(0, pc = 0x24681357, tag = 0x42, a0 = M_REG_ADDR, d0 = wordMask),
        r(1, M_REG_ADDR), w(2, M_REG_ADDR, 5), w(3, 0x26, 1))
      s.hold(0, cycles = 5); s.starts.size should be(2)
      s.starts.last.pc should be(BigInt(0x24681357)); s.starts.last.tag should be(0x42)
      s.starts.last.m should be(BigInt(0))
      s.respond(0); s.respond(1, data = 0); s.respond(2); s.respond(3)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.inFlight.expect(true.B)
      s.read(M_REG_ADDR) should be(BigInt(5))
      s.done(); s.write(IRQ_CLEAR_REG_ADDR, 0x7FF)
      s.batch(w2(0, M_REG_ADDR, M_REG_ADDR, 1, 2), r(3, M_REG_ADDR))
      s.respond(0); s.respond(3, data = 5)
      s.read(M_REG_ADDR) should be(BigInt(2)); s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0))
    }
  }

  it should "arbitrate MASK by highest lane and CLEAR by same lane OR then highest lane including CLEAR zero" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.batch(w2(0, IRQ_MASK_REG_ADDR, IRQ_MASK_REG_ADDR, 0x7FF, 1),
        w2(1, IRQ_MASK_REG_ADDR, IRQ_MASK_REG_ADDR, 0x7FF, 2),
        w2(2, IRQ_MASK_REG_ADDR, IRQ_MASK_REG_ADDR, 0x7FF, 4),
        w2(3, IRQ_MASK_REG_ADDR, IRQ_MASK_REG_ADDR, 0, 0x400))
      for (lane <- 0 until 4) s.respond(lane)
      s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0x400))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x400)); dut.io.irq.expect(false.B)
      s.write(IRQ_MASK_REG_ADDR, 0); dut.io.irq.expect(true.B)
      s.configError(5); s.done(); s.dma(3) // RAW=0x40F。
      s.batch(w2(0, IRQ_CLEAR_REG_ADDR, IRQ_CLEAR_REG_ADDR, 1, 2), w(1, IRQ_CLEAR_REG_ADDR, 4),
        w(2, IRQ_CLEAR_REG_ADDR, 8), w2(3, IRQ_CLEAR_REG_ADDR, IRQ_CLEAR_REG_ADDR, 1, 4))
      for (lane <- 0 until 4) s.respond(lane)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x40A)) // 仅最高 lane 的 1|4，不能跨 lane OR。
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(5))
      for (zero <- Seq(BigInt(0), BigInt("fffff800", 16))) {
        s.batch(w(0, IRQ_CLEAR_REG_ADDR, 0x7FF), w(3, IRQ_CLEAR_REG_ADDR, zero))
        s.respond(0); s.respond(3)
        s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x40A)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(5))
      }
      s.batch(w2(2, IRQ_CLEAR_REG_ADDR, IRQ_CLEAR_REG_ADDR, 2, 8))
      s.respond(2)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x400)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.batch(w2(1, IRQ_CLEAR_REG_ADDR, IRQ_CLEAR_REG_ADDR, 0x400, 0))
      s.respond(1)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); dut.io.irq.expect(false.B)
      dut.io.inFlight.expect(false.B)
    }
  }

  it should "retain fresh WRITE_CONFLICT over same edge W1C without contaminating the first configuration error code" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.configError(0); s.configError(7); s.write(IRQ_MASK_REG_ADDR, 0)
      dut.io.done.poke(true.B); dut.io.dmaErrors(1).poke(true.B)
      s.batch(w2(0, IRQ_CLEAR_REG_ADDR, M_REG_ADDR, 0x7FF, 0x55),
        w(1, M_REG_ADDR, 0x55), r(2, ERROR_CODE_REG_ADDR), r(3, IRQ_RAW_REG_ADDR))
      dut.io.done.poke(false.B); dut.io.dmaErrors(1).poke(false.B)
      s.respond(0); s.respond(1); s.respond(2, data = 0); s.respond(3, data = 2)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x409)); dut.io.irq.expect(true.B)
      s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0)); dut.io.inFlight.expect(false.B)
      s.configError(6) // RAW=0x40B，首个未清配置码为 6。
      dut.io.configError.poke(true.B); dut.io.configErrorCode.poke(9.U)
      s.batch(w2(0, IRQ_MASK_REG_ADDR, IRQ_CLEAR_REG_ADDR, 0, 0x400),
        w(1, IRQ_MASK_REG_ADDR, 0), r(2, ERROR_CODE_REG_ADDR), r(3, IRQ_RAW_REG_ADDR))
      dut.io.configError.poke(false.B)
      s.respond(0); s.respond(1); s.hold(2, data = 6); s.respond(2, data = 6)
      s.hold(3, data = 0x40B); s.respond(3, data = 0x40B)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0x40B)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(6))
      s.write(IRQ_CLEAR_REG_ADDR, 0x400)
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0xB)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(6))
    }
  }

  it should "block all lanes through the last pending read fire accept held valids only next cycle and discard pending on reset" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 0x1234); s.write(A_REG_ADDR, 0x5678)
      s.batch(r(0, M_REG_ADDR), r(1, A_REG_ADDR), r(2, STATUS_REG_ADDR), r(3, IRQ_RAW_REG_ADDR))
      // 四路新命令 valid 跨整批保持，包括已消费响应的空 slot；不得提前 fire。
      s.drive(r(0, M_REG_ADDR), w(1, A_REG_ADDR, 0x9ABC), r(2, STATUS_REG_ADDR),
        w2(3, IRQ_MASK_REG_ADDR, IRQ_CLEAR_REG_ADDR, 0, 0x7FF))
      dut.io.busy.poke(true.B); dut.io.progress.poke(0xABCD.U)
      s.done(); s.dma(1)
      val expected = Seq(BigInt(0x1234), BigInt(0x5678), BigInt(0), BigInt(0))
      for (lane <- 0 until 4) {
        s.hold(lane, data = expected(lane), cycles = 4)
        dut.io.cmd.foreach(_.valid.expect(true.B))
        s.respond(lane, data = expected(lane))
        if (lane != 3) s.allReady(false)
      }
      // 最后 read.fire 已过，但新 valid 没有在该边沿被接收。
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      dut.io.cmd.foreach(_.valid.expect(true.B))
      s.accept() // 下一拍同一组 held valid 才组成新批。
      s.respond(0, data = 0x1234); s.respond(1)
      s.hold(2, data = BigInt("abcd0001", 16)); s.respond(2, data = BigInt("abcd0001", 16))
      s.respond(3)
      s.read(A_REG_ADDR) should be(BigInt(0x9ABC))
      s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0)); s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0))
      dut.io.busy.poke(false.B); dut.io.progress.poke(0.U)
      s.batch(run(0, pc = 0x24681357, tag = 0x53), r(1, M_REG_ADDR),
        w(2, IRQ_RAW_REG_ADDR, 0), r(3, IRQ_MASK_REG_ADDR))
      s.respond(0); s.hold(1, data = 0x1234, cycles = 2)
      s.starts.size should be(1); dut.io.inFlight.expect(true.B)
      s.starts.last.m should be(BigInt(0x1234))
      dut.reset.poke(true.B); s.tick(2); dut.reset.poke(false.B); s.tick()
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      dut.io.start.expect(false.B); dut.io.inFlight.expect(false.B); dut.io.irq.expect(false.B)
      s.credit() should be(BigInt(1))
      dut.io.regFile.m.expect(0.U); dut.io.regFile.aAddr.expect(0.U)
      s.tick(2); s.starts.size should be(1); dut.io.resp.valid.expect(false.B)
      s.batch(r(1, M_REG_ADDR), r(3, IRQ_MASK_REG_ADDR))
      s.respond(1, data = 0); s.respond(3, data = 0x7FF) // shadow 也被复位清空
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); s.read(ERROR_CODE_REG_ADDR) should be(BigInt(0))
      s.allReady(true); dut.io.resp.valid.expect(false.B)
    }
  }

  it should "cancel a pending RUN and its responses on reset before the launch edge without replaying stale metadata" in {
    simulate(new WorkerBeeRegIf(cfg)) { dut =>
      val s = new Driver(dut); s.init()
      s.write(M_REG_ADDR, 19)
      s.batch(run(0, pc = 0x1111, tag = 0x11), r(2, M_REG_ADDR))
      dut.io.start.expect(true.B); dut.io.inFlight.expect(true.B)
      s.starts.size should be(0)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B); s.tick(3)
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      dut.io.start.expect(false.B); dut.io.inFlight.expect(false.B)
      dut.io.regFile.m.expect(0.U); s.starts.size should be(0)
      s.read(M_REG_ADDR) should be(BigInt(0))
      s.read(IRQ_RAW_REG_ADDR) should be(BigInt(0)); s.read(IRQ_MASK_REG_ADDR) should be(BigInt(0x7FF))
      s.write(M_REG_ADDR, 23)
      s.batch(run(2, pc = 0x2222, tag = 0x22))
      s.hold(2, cycles = 4); s.respond(2)
      s.starts.toSeq should be(Seq(StartEvent(0x2222, 0x22, 23, 0, 0, 0, 0, 0)))
      s.allReady(true); dut.io.resp.valid.expect(false.B)
      s.done()
    }
  }
}