import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** ForagerBeeReg 寄存器接口仿真：双 bank、credit、START 组装与各 op 等价。
  * 复用 ForagerBeeSpec 的 FbStreamMemSlave 字节内存模型。
  */
class FbRegIfSpec extends AnyFlatSpec with Matchers {
  behavior of "ForagerBeeReg register interface"

  private val defaultCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 5, tileSize = 4, maxBurstLen = 4,
    enableConversion = true)

  private class Sim(dut: ForagerBeeReg, cfg: ForagerBeeConfig,
      mem: mutable.HashMap[BigInt, Byte], writeDelay: Int = 1) {
    private val models = Seq.fill(cfg.numPorts)(
      new FbStreamMemSlave(mem, cfg.beatBytes, writeDelay = writeDelay))
    private def stepBus(): Unit = {
      for (p <- 0 until cfg.numPorts) models(p).step(dut.io.bus(p))
      dut.clock.step()
    }
    private def pokeLane(l: Int, op: FbRegOp.Type, a0: Int, d0: BigInt,
        a1: Int, d1: BigInt, tag: Int): Unit = {
      val p = dut.io.cmd(l).payload
      p.op.poke(op); p.reg0Addr.poke(a0.U); p.reg1Addr.poke(a1.U)
      p.data0.poke(d0); p.data1.poke(d1); p.tag.poke(tag.U); p.pc.poke(0.U)
    }
    private def clearCmd(): Unit =
      for (l <- 0 until 4) dut.io.cmd(l).valid.poke(false.B)
    def reset(): Unit = {
      clearCmd()
      for (l <- 0 until 4) dut.io.cmd(l).payload.poke(0.U.asTypeOf(new FbRegCmd))
      dut.io.resp.ready.poke(true.B)
      for (b <- dut.io.bus) {
        b.rd.ready.poke(false.B)
        b.rdData.valid.poke(false.B)
        b.rdData.payload.poke(0.U.asTypeOf(b.rdData.payload))
        b.wr.ready.poke(false.B)
        b.wrResp.valid.poke(false.B)
        b.wrResp.payload.err.poke(false.B)
      }
      dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
    }
    def idle(n: Int = 1): Unit = { clearCmd(); for (_ <- 0 until n) stepBus() }
    // 单命令走 lane0，两拍握手：接受拍 + 回执排空拍
    def xfer(op: FbRegOp.Type, a0: Int, d0: BigInt = 0, a1: Int = 0,
        d1: BigInt = 0, tag: Int = 0): (BigInt, Boolean) = {
      clearCmd()
      pokeLane(0, op, a0, d0, a1, d1, tag)
      dut.io.cmd(0).valid.poke(true.B)
      assert(dut.io.cmd(0).ready.peek().litToBoolean, "空闲 batch 应接受命令")
      stepBus()
      clearCmd()
      assert(dut.io.resp.valid.peek().litToBoolean, "应有回执")
      val data = dut.io.resp.payload.data.peek().litValue
      val err = dut.io.resp.payload.err.peek().litToBoolean
      stepBus()
      (data, err)
    }
    // 一个 batch 同时驱动多 lane，按 lane 序排空回执，返回 (data, err, laneId)
    def batch(cmds: Seq[(FbRegOp.Type, Int, BigInt, Int, BigInt, Int)]): Seq[(BigInt, Boolean, Int)] = {
      clearCmd()
      for (l <- cmds.indices) {
        val (op, a0, d0, a1, d1, tag) = cmds(l)
        pokeLane(l, op, a0, d0, a1, d1, tag)
        dut.io.cmd(l).valid.poke(true.B)
      }
      stepBus()
      clearCmd()
      val out = mutable.ArrayBuffer.empty[(BigInt, Boolean, Int)]
      for (_ <- cmds.indices) {
        assert(dut.io.resp.valid.peek().litToBoolean, "应有回执")
        val data = dut.io.resp.payload.data.peek().litValue
        val err = dut.io.resp.payload.err.peek().litToBoolean
        val lane = dut.io.resp.payload.laneId.peek().litValue.toInt
        out += ((data, err, lane))
        stepBus()
      }
      out.toSeq
    }
    def write(a: Int, d: BigInt): Unit = {
      val (_, e) = xfer(FbRegOp.WRITE1, a, d); assert(!e, s"write 0x${a.toHexString} 不应 err")
    }
    def write2(a0: Int, d0: BigInt, a1: Int, d1: BigInt): Unit = {
      val (_, e) = xfer(FbRegOp.WRITE2, a0, d0, a1, d1); assert(!e)
    }
    def read(a: Int): BigInt = {
      val (d, e) = xfer(FbRegOp.READ, a); assert(!e, s"read 0x${a.toHexString} 不应 err"); d
    }
    def startErr(tag: Int): Boolean = xfer(FbRegOp.START, 0, tag = tag)._2
    def start(tag: Int): Unit = assert(!startErr(tag), s"START tag=$tag 不应 err")
    // 每条命令须完整配置当前 shadow bank（START 后 bank 切换，新 bank 不继承旧字段）
    def copy1D(src: BigInt, dst: BigInt, len: Int): Unit = {
      write(FbRegAddr.CTRL, ctrl(0, 1))
      write(FbRegAddr.SHAPE_01, len)
      write2(FbRegAddr.SRC_ADDR_LO, src & 0xffffffffL, FbRegAddr.DST_ADDR_LO, dst & 0xffffffffL)
      write(FbRegAddr.ADDR_HI, ((src >> 32) & 0xff) | (((dst >> 32) & 0xff) << 8))
    }
    def doneCnt: Int = read(FbRegAddr.DONE_CNT).toInt
    def credit: Int = read(FbRegAddr.CREDIT).toInt
    def waitDone(n: Int, timeout: Int = 30000): Unit = {
      var t = 0
      while (doneCnt < n && t < timeout) { idle(1); t += 1 }
      assert(doneCnt >= n, s"等待 $n 个 done 超时（当前 $doneCnt）")
    }
  }

  private def withSim(cfg: ForagerBeeConfig = defaultCfg,
      prefill: mutable.HashMap[BigInt, Byte] = mutable.HashMap.empty,
      writeDelay: Int = 1)(body: Sim => Unit): mutable.HashMap[BigInt, Byte] = {
    val mem = prefill
    implicit val testingDirectory: chisel3.testing.HasTestingDirectory =
      chisel3.testing.HasTestingDirectory.timestamp
    (new chisel3.simulator.ChiselSim {}).simulateRaw(new ForagerBeeReg(cfg)) { dut =>
      val sim = new Sim(dut, cfg, mem, writeDelay)
      sim.reset()
      body(sim)
    }
    mem
  }

  private def le(value: BigInt, n: Int): Seq[Int] =
    (0 until n).map(i => ((value >> (8 * i)) & 255).toInt)
  private def put(mem: mutable.HashMap[BigInt, Byte], base: BigInt, bytes: Seq[Int]): Unit =
    bytes.zipWithIndex.foreach { case (b, i) => mem(base + i) = b.toByte }
  private def ctrl(op: Int, dimCount: Int, srcFmt: Int = 3, dstFmt: Int = 3, blk: Int = 0): BigInt =
    BigInt(op | (dimCount << 3) | (srcFmt << 6) | (dstFmt << 10) | (blk << 14))
  private def pair(lo16: Int, hi16: Int): BigInt = BigInt(lo16 | (hi16 << 16))

  it should "reset state and roundtrip every config register" in {
    withSim() { sim =>
      sim.credit shouldBe 2
      sim.read(FbRegAddr.DONE_CNT) shouldBe 0
      val status = sim.read(FbRegAddr.STATUS)
      (status & 1) shouldBe 0       // busy=0
      ((status >> 1) & 1) shouldBe 1 // idle=1
      for (a <- 0 until FbRegAddr.NumCfg) {
        val v = (BigInt(a) * 0x11111111 + 0x12345678) & 0xffffffffL
        sim.write(a, v)
        sim.read(a) shouldBe v
      }
      sim.write2(FbRegAddr.SRC_ADDR_LO, 0xdeadbeefL, FbRegAddr.DST_ADDR_LO, 0xcafebabeL)
      sim.read(FbRegAddr.SRC_ADDR_LO) shouldBe 0xdeadbeefL
      sim.read(FbRegAddr.DST_ADDR_LO) shouldBe 0xcafebabeL
    }
  }

  it should "reject same-address WRITE2, read-only, reserved and unmapped accesses" in {
    withSim() { sim =>
      sim.write(FbRegAddr.ADDR_HI, 0x11223344L)
      val (_, e1) = sim.xfer(FbRegOp.WRITE2, FbRegAddr.ADDR_HI, 0xaaaaaaaaL,
        FbRegAddr.ADDR_HI, 0xbbbbbbbbL)
      e1 shouldBe true
      sim.read(FbRegAddr.ADDR_HI) shouldBe 0x11223344L // 冲突不写
      for (a <- Seq(FbRegAddr.STATUS, FbRegAddr.DONE, FbRegAddr.DONE_CNT, FbRegAddr.CREDIT,
          0x23, 0x3f, 0x44, 0x7f)) {
        val (_, e) = sim.xfer(FbRegOp.WRITE1, a, 0x1L)
        e shouldBe true
      }
      for (a <- Seq(0x23, 0x3f, 0x44, 0x7f)) {
        val (_, e) = sim.xfer(FbRegOp.READ, a)
        e shouldBe true
      }
      sim.read(FbRegAddr.STATUS) // 状态可读
    }
  }

  it should "COPY with 40-bit high addresses assembled from registers" in {
    val src = BigInt("8100001000", 16)
    val dst = BigInt("a100002000", 16)
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val data = (1 to 16)
    put(mem, src, data)
    put(mem, src & 0xffffffffL, Seq.fill(16)(0xe7)) // 低 32 别名哨兵
    val result = withSim(prefill = mem) { sim =>
      sim.write2(FbRegAddr.CTRL, ctrl(0, 1), FbRegAddr.SHAPE_01, 16)
      sim.write2(FbRegAddr.SRC_ADDR_LO, src & 0xffffffffL, FbRegAddr.DST_ADDR_LO, dst & 0xffffffffL)
      sim.write(FbRegAddr.ADDR_HI, 0x81 | (0xa1 << 8))
      sim.write2(FbRegAddr.SRC_STRIDE0_LO, 16, FbRegAddr.DST_STRIDE0_LO, 16)
      sim.start(0x21)
      sim.waitDone(1)
      sim.read(FbRegAddr.DONE) shouldBe 0x21 // lastTag, err=0
      sim.doneCnt shouldBe 1
      sim.credit shouldBe 2
    }
    for (i <- 0 until 16) {
      result(dst + i) shouldBe data(i).toByte
      result((src & 0xffffffffL) + i) shouldBe 0xe7.toByte
    }
  }

  it should "swap to shadow bank on START and queue a second command while busy" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s1 = BigInt("8100010000", 16); val d1 = BigInt("a100010000", 16)
    val s2 = BigInt("8100020000", 16); val d2 = BigInt("a100020000", 16)
    put(mem, s1, (1 to 16)); put(mem, s2, (101 to 116))
    val result = withSim(prefill = mem, writeDelay = 60) { sim =>
      sim.copy1D(s1, d1, 16)
      sim.start(1)
      sim.credit shouldBe 1
      // 忙时写 shadow（另一 bank），读回应见新值而非在途命令值
      sim.copy1D(s2, d2, 16)
      sim.read(FbRegAddr.SRC_ADDR_LO) shouldBe (s2 & 0xffffffffL)
      sim.start(2)
      sim.credit shouldBe 0
      sim.waitDone(2)
      sim.doneCnt shouldBe 2
      sim.credit shouldBe 2
      sim.read(FbRegAddr.DONE) shouldBe 2
    }
    for (i <- 0 until 16) {
      result(d1 + i) shouldBe (i + 1).toByte
      result(d2 + i) shouldBe (i + 101).toByte
    }
  }

  it should "exhaust credit at N=1 then recover on done" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s1 = BigInt("8100030000", 16); val d1 = BigInt("a100030000", 16)
    put(mem, s1, (1 to 16))
    withSim(cfg = defaultCfg.copy(numPorts = 1), prefill = mem, writeDelay = 40) { sim =>
      sim.copy1D(s1, d1, 16)
      sim.start(1)
      sim.credit shouldBe 0
      sim.startErr(2) shouldBe true // credit==0 再 START 报错
      sim.credit shouldBe 0
      sim.waitDone(1)
      sim.credit shouldBe 1
      sim.copy1D(s1, d1, 16)
      sim.start(2)
      sim.waitDone(2)
      sim.credit shouldBe 1
    }
  }

  it should "hold credit under address hazard until completion (N=2)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s1 = BigInt("8100040000", 16); val s2 = BigInt("8100050000", 16)
    val dst = BigInt("a100040000", 16) // 两命令同目的 → WAW
    put(mem, s1, (1 to 16)); put(mem, s2, (101 to 116))
    val result = withSim(prefill = mem, writeDelay = 50) { sim =>
      sim.copy1D(s1, dst, 16)
      sim.start(1)
      sim.copy1D(s2, dst, 16)
      sim.start(2) // 入队但 hazard 阻塞派发
      sim.credit shouldBe 0
      sim.startErr(3) shouldBe true
      sim.idle(10)
      sim.credit shouldBe 0 // 阻塞期间不返还
      ((sim.read(FbRegAddr.STATUS) >> 2) & 0xffff) should be >= BigInt(1) // occupancy 含被阻塞命令
      sim.waitDone(2)
      sim.credit shouldBe 2
      sim.doneCnt shouldBe 2
    }
    for (i <- 0 until 16) result(dst + i) shouldBe (i + 101).toByte // WAW 后写覆盖
  }

  it should "err on START when command queue full even with credit remaining" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s1 = BigInt("8100060000", 16); val s2 = BigInt("8100070000", 16)
    val dst = BigInt("a100060000", 16)
    put(mem, s1, (1 to 16)); put(mem, s2, (101 to 116))
    withSim(cfg = defaultCfg.copy(numPorts = 4, queueDepth = 1), prefill = mem, writeDelay = 80) { sim =>
      sim.copy1D(s1, dst, 16)
      sim.start(1) // 派发到引擎0
      sim.copy1D(s2, dst, 16)
      sim.start(2) // WAW 阻塞，占据深度1队列
      sim.credit shouldBe 2 // N=4，仅两条在途
      sim.startErr(3) shouldBe true // 队列满，credit>0 仍 err
      sim.credit shouldBe 2
      sim.waitDone(2)
      sim.doneCnt shouldBe 2
    }
  }

  it should "assemble TRANSPOSE/PERMUTE/IM2COL/T2L/MX/SG/pad commands equivalently" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val expected = mutable.HashMap.empty[BigInt, Byte]
    def base(tag: Int): BigInt = BigInt("8100000000", 16) + tag * 0x10000
    def dbase(tag: Int): BigInt = BigInt("a100000000", 16) + tag * 0x10000
    def hi(src: BigInt, dst: BigInt): BigInt =
      ((src >> 32) & 0xff) | (((dst >> 32) & 0xff) << 8)
    def expect(dst: BigInt, bytes: Seq[Int]): Unit =
      bytes.zipWithIndex.foreach { case (b, i) => expected(dst + i) = b.toByte }

    // TRANSPOSE 4x4 INT8
    val ts = base(1); val td = dbase(1)
    put(mem, ts, (1 to 16))
    expect(td, (for (c <- 0 until 4; r <- 0 until 4) yield r * 4 + c + 1))
    // PERMUTE 3D swap dim1/dim2
    val ps = base(2); val pd = dbase(2)
    for (y <- 0 until 2; z <- 0 until 2) {
      val vals = (0 until 3).map(i => y * 40 + z * 10 + i + 1)
      put(mem, ps + y * 64 + z * 32, vals)
      expect(pd + y * 16 + z * 40, vals)
    }
    // IM2COL 1x3x4 kernel2x2
    val is = base(3); val id = dbase(3)
    put(mem, is, (1 to 12))
    expect(id, (for (oh <- 0 until 2; ow <- 0 until 3; kh <- 0 until 2; kw <- 0 until 2)
      yield (oh + kh) * 4 + ow + kw + 1))
    // TILE2LINEAR 4x4 Tm=Tn=2
    val ls = base(4); val ld = dbase(4)
    put(mem, ls, (1 to 16))
    expect(ld, (for (ty <- 0 until 2; tx <- 0 until 2; r <- 0 until 2; c <- 0 until 2)
      yield (ty * 2 + r) * 4 + tx * 2 + c + 1))
    // MX FP32->MXE4M3 blockSize16, all 1.0
    val ms = base(5); val md = dbase(5); val sc = BigInt("c100080000", 16)
    put(mem, ms, Seq.fill(16)(BigInt("3f800000", 16)).flatMap(le(_, 4)))
    expect(md, Seq.fill(16)(0x38)); expect(sc, Seq(127))
    // SG GATHER 2 entries (3+7)
    val gs1 = dbase(6); val gs2 = dbase(6) + 32; val gd = dbase(7)
    val table = BigInt("d100000000", 16)
    val gvals = (0 until 10).map(i => 0x31 + i)
    put(mem, gs1, gvals.take(3)); put(mem, gs2, gvals.drop(3))
    put(mem, table, le(gs1 | (BigInt(3) << 40), 16))
    put(mem, table + 16, le(gs2 | (BigInt(7) << 40), 16))
    expect(gd, gvals)
    // pad COPY 1D dimCount2 shape[5,1]
    val cs = base(8); val cd = dbase(8)
    put(mem, cs, (1 to 5))
    expect(cd, Seq(0x6d) ++ (1 to 5) ++ Seq(0x6d, 0x6d))

    val result = withSim(prefill = mem) { sim =>
      def common(src: BigInt, dst: BigInt): Unit = {
        sim.write2(FbRegAddr.SRC_ADDR_LO, src & 0xffffffffL, FbRegAddr.DST_ADDR_LO, dst & 0xffffffffL)
        sim.write(FbRegAddr.ADDR_HI, hi(src, dst))
      }
      // TRANSPOSE op=1 dimCount2 shape[4,4] srcStride1=4 dstStride0=4
      sim.write(FbRegAddr.CTRL, ctrl(1, 2))
      sim.write(FbRegAddr.SHAPE_01, pair(4, 4))
      sim.write(FbRegAddr.SRC_STRIDE1_LO, 4)
      sim.write(FbRegAddr.DST_STRIDE0_LO, 4)
      common(ts, td); sim.start(1); sim.waitDone(1)
      // PERMUTE op=2 dimCount3 shape[3,2,2] perm[0,2,1]
      sim.write(FbRegAddr.CTRL, ctrl(2, 3))
      sim.write(FbRegAddr.PERM_VEC, BigInt(0 | (2 << 3) | (1 << 6)))
      sim.write(FbRegAddr.SHAPE_01, pair(3, 2)); sim.write(FbRegAddr.SHAPE_23, pair(2, 0))
      sim.write(FbRegAddr.SRC_STRIDE1_LO, 32); sim.write(FbRegAddr.SRC_STRIDE2_LO, 64)
      sim.write(FbRegAddr.DST_STRIDE1_LO, 16); sim.write(FbRegAddr.DST_STRIDE2_LO, 40)
      common(ps, pd); sim.start(2); sim.waitDone(2)
      // IM2COL op=3 shape[12,24]
      sim.write(FbRegAddr.CTRL, ctrl(3, 1))
      sim.write(FbRegAddr.SHAPE_01, pair(12, 24))
      sim.write(FbRegAddr.I2C_IN_CH, pair(1, 3))
      sim.write(FbRegAddr.I2C_IN_W_KERN, BigInt(4 | (2 << 16) | (2 << 24)))
      sim.write(FbRegAddr.I2C_STRIDE_PAD, BigInt(1 | (1 << 8)))
      sim.write(FbRegAddr.I2C_DILATION, BigInt(1 | (1 << 8)))
      common(is, id); sim.start(3); sim.waitDone(3)
      // T2L op=6 dimCount2 shape[4,4]
      sim.write(FbRegAddr.CTRL, ctrl(6, 2))
      sim.write(FbRegAddr.SHAPE_01, pair(4, 4))
      sim.write(FbRegAddr.T2L_COLS_ROWS, pair(4, 2))
      sim.write(FbRegAddr.T2L_TILE_NROW, pair(2, 2))
      sim.write(FbRegAddr.T2L_NTCOL, 2)
      common(ls, ld); sim.start(4); sim.waitDone(4)
      // MX op=0 dstFmt=4 blk=0(16)
      sim.write(FbRegAddr.CTRL, ctrl(0, 1, srcFmt = 0, dstFmt = 4, blk = 0))
      sim.write(FbRegAddr.SHAPE_01, 16)
      sim.write(FbRegAddr.SCALE_LO, sc & 0xffffffffL)
      sim.write(FbRegAddr.ADDR_HI, hi(ms, md) | (((sc >> 32) & 0xff) << 24))
      sim.write2(FbRegAddr.SRC_ADDR_LO, ms & 0xffffffffL, FbRegAddr.DST_ADDR_LO, md & 0xffffffffL)
      sim.start(5); sim.waitDone(5)
      // SG GATHER op=5 shape[64,10]
      sim.write(FbRegAddr.CTRL, ctrl(5, 1))
      sim.write(FbRegAddr.SHAPE_01, pair(64, 10))
      sim.write(FbRegAddr.AUX, 2) // sgEntryCount
      sim.write(FbRegAddr.SG_LIST_LO, table & 0xffffffffL)
      sim.write(FbRegAddr.ADDR_HI, hi(gs1, gd) | (((table >> 32) & 0xff) << 16))
      sim.write2(FbRegAddr.SRC_ADDR_LO, gs1 & 0xffffffffL, FbRegAddr.DST_ADDR_LO, gd & 0xffffffffL)
      sim.start(6); sim.waitDone(6)
      // pad COPY op=0 dimCount2 shape[5,1] padBefore0=1 padAfter0=2 padValue=0x6d
      sim.write(FbRegAddr.CTRL, ctrl(0, 2))
      sim.write(FbRegAddr.SHAPE_01, pair(5, 1))
      sim.write(FbRegAddr.PAD_VALUE, 0x6d)
      sim.write(FbRegAddr.PAD_BEFORE_01, pair(1, 0))
      sim.write(FbRegAddr.PAD_AFTER_01, pair(2, 0))
      sim.write(FbRegAddr.DST_STRIDE1_LO, 8)
      common(cs, cd); sim.start(7); sim.waitDone(7)
      sim.doneCnt shouldBe 7
      sim.credit shouldBe 2
    }
    for ((addr, byte) <- expected) result(addr) shouldBe byte
  }

  it should "commit 4-lane parallel writes and return reads in lane order" in {
    withSim() { sim =>
      val addrs = Seq(FbRegAddr.SRC_ADDR_LO, FbRegAddr.DST_ADDR_LO,
        FbRegAddr.SG_LIST_LO, FbRegAddr.SCALE_LO)
      val vals = Seq(BigInt(0x11111111L), BigInt(0x22222222L), BigInt(0x33333333L), BigInt(0x44444444L))
      val w = sim.batch(addrs.zip(vals).map { case (a, v) => (FbRegOp.WRITE1, a, v, 0, BigInt(0), 0) })
      w.map(_._2) shouldBe Seq(false, false, false, false)
      w.map(_._3) shouldBe Seq(0, 1, 2, 3)
      for ((a, v) <- addrs.zip(vals)) sim.read(a) shouldBe v
      // 4 路读按 lane 序返回
      val r = sim.batch(addrs.map(a => (FbRegOp.READ, a, BigInt(0), 0, BigInt(0), 0)))
      r.map(_._3) shouldBe Seq(0, 1, 2, 3)
      r.map(_._1) shouldBe vals
      r.map(_._2) shouldBe Seq(false, false, false, false)
      // 两条读占据 lane0/lane1，读不同地址，回执仍按 lane 升序排空
      val r2 = sim.batch(Seq(
        (FbRegOp.READ, addrs(1), BigInt(0), 0, BigInt(0), 0),
        (FbRegOp.READ, addrs(3), BigInt(0), 0, BigInt(0), 0)))
      r2.map(_._3) shouldBe Seq(0, 1)
      r2.map(_._1) shouldBe Seq(vals(1), vals(3))
    }
  }

  it should "err on cross-lane same-address writes without committing" in {
    withSim() { sim =>
      val res = sim.batch(Seq(
        (FbRegOp.WRITE1, FbRegAddr.ADDR_HI, BigInt(0xaaaaL), 0, BigInt(0), 0),
        (FbRegOp.WRITE1, FbRegAddr.ADDR_HI, BigInt(0xbbbbL), 0, BigInt(0), 0),
        (FbRegOp.WRITE1, FbRegAddr.SRC_ADDR_LO, BigInt(0x1234L), 0, BigInt(0), 0)))
      res.map(_._3) shouldBe Seq(0, 1, 2)
      res.map(_._2) shouldBe Seq(true, true, false) // 同址两 lane 冲突 err
      sim.read(FbRegAddr.ADDR_HI) shouldBe 0       // 冲突不写
      sim.read(FbRegAddr.SRC_ADDR_LO) shouldBe 0x1234L
    }
  }

  it should "grant only the lowest START lane in a batch" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val s = BigInt("8100080000", 16); val d = BigInt("a100080000", 16)
    put(mem, s, (1 to 16))
    val result = withSim(prefill = mem) { sim =>
      sim.copy1D(s, d, 16)
      val res = sim.batch(Seq(
        (FbRegOp.START, 0, BigInt(0), 0, BigInt(0), 1),
        (FbRegOp.START, 0, BigInt(0), 0, BigInt(0), 2)))
      res.map(_._3) shouldBe Seq(0, 1)
      res.map(_._2) shouldBe Seq(false, true) // 仅最低 lane 入队，其余 err
      sim.credit shouldBe 1                   // N=2，仅一条占用
      sim.waitDone(1)
      sim.doneCnt shouldBe 1
      sim.read(FbRegAddr.DONE) shouldBe 1     // tag 来自获授权 lane0
    }
    for (i <- 0 until 16) result(d + i) shouldBe (i + 1).toByte
  }
}
