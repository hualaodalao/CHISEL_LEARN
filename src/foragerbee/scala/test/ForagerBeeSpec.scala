/** ForagerBee 测试：elaboration 冒烟 + EphemeralSimulator 功能仿真。
  *
  * 功能仿真方案：
  *   - 测试内自带行为级 FbStreamBus slave 内存模型 [[FbStreamMemSlave]]：
  *     Scala 侧 HashMap[BigInt, Byte] 共享内存（多端口为同一内存的不同视图），
  *     每拍 peek wr/rd 通道、poke rdData/wrResp 通道；仅支持单 outstanding
  *     子集（与 ForagerBee v1 的发出行为匹配），可编程响应延迟（默认各 1 拍）。
  *   - 未读/未写地址读回 0；写按 wstrb 字节掩码落盘（支持非对齐转置写）。
  *   - chisel3.simulator 不自动断言 reset：仿真开始先驱动全部输入再显式复位 2 拍。
  *   - 主循环逐拍驱动：先跑各端口 slave 模型 → 注入命令 → 收集 done → step。
  */

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

/** 行为级 FbStreamBus slave 内存模型（单 outstanding、字节粒度 mem）。
  *
  * 端口方向（从 DUT master 视角）：
  *   - bus.rd:     DUT 驱动 valid/payload（读请求），test 驱动 ready
  *   - bus.rdData: test 驱动 valid/payload（读数据），DUT 驱动 ready
  *   - bus.wr:     DUT 驱动 valid/payload（写数据），test 驱动 ready
  *   - bus.wrResp: test 驱动 valid/payload（写响应），DUT 驱动 ready
  */
class FbStreamMemSlave(
    mem: mutable.HashMap[BigInt, Byte],
    beatBytes: Int,
    readDelay: Int = 1,
    writeDelay: Int = 1
) {
  // --- 读状态 ---
  private var rdActive = false
  private var rdAddr = BigInt(0)
  private var rdRemain = 0
  private var rdBeatIdx = 0
  private var rdWait = 0

  // --- 写状态 ---
  private var wrAddr = BigInt(0)
  private var bWait = -1 // -1=idle, 0=send resp this step, >0=countdown

  def readBeat(addr: BigInt): BigInt = {
    var v = BigInt(0)
    for (l <- 0 until beatBytes) {
      v |= BigInt(mem.getOrElse(addr + l, 0.toByte) & 0xFF) << (8 * l)
    }
    v
  }

  def step(bus: FbStreamBus): Unit = {
    // === rd 通道（DUT master 驱动 valid/payload，test slave 驱动 ready）===
    bus.rd.ready.poke((!rdActive).B)
    if (!rdActive && bus.rd.valid.peek().litToBoolean) {
      rdAddr = bus.rd.payload.addr.peek().litValue
      rdRemain = bus.rd.payload.len.peek().litValue.toInt + 1
      rdBeatIdx = 0
      rdWait = math.max(1, readDelay)
      rdActive = true
    }

    // === rdData 通道（test slave 驱动 valid/payload，DUT master 驱动 ready）===
    if (rdActive && rdRemain > 0 && rdWait == 0) {
      bus.rdData.valid.poke(true.B)
      bus.rdData.payload.data.poke(readBeat(rdAddr))
      bus.rdData.payload.sof.poke((rdBeatIdx == 0).B)
      bus.rdData.payload.eof.poke((rdRemain == 1).B)
      bus.rdData.payload.err.poke(false.B)
      if (bus.rdData.ready.peek().litToBoolean) {
        rdAddr += beatBytes
        rdBeatIdx += 1
        rdRemain -= 1
        if (rdRemain == 0) rdActive = false
      }
    } else {
      bus.rdData.valid.poke(false.B)
      bus.rdData.payload.data.poke(0.U)
      bus.rdData.payload.sof.poke(false.B)
      bus.rdData.payload.eof.poke(false.B)
      bus.rdData.payload.err.poke(false.B)
      if (rdActive && rdWait > 0) rdWait -= 1
    }

    // === wr 通道（DUT master 驱动 valid/payload，test slave 驱动 ready）===
    bus.wr.ready.poke(true.B)
    if (bus.wr.valid.peek().litToBoolean) {
      val sof = bus.wr.payload.sof.peek().litToBoolean
      val eof = bus.wr.payload.eof.peek().litToBoolean
      val data = bus.wr.payload.data.peek().litValue
      val strb = bus.wr.payload.strb.peek().litValue
      if (sof) {
        wrAddr = bus.wr.payload.addr.peek().litValue
      }
      for (l <- 0 until beatBytes) {
        if (strb.testBit(l)) mem(wrAddr + l) = ((data >> (8 * l)) & 0xFF).toByte
      }
      wrAddr += beatBytes
      if (eof) {
        bWait = math.max(1, writeDelay)
      }
    }

    // === wrResp 通道（test slave 驱动 valid/payload，DUT master 驱动 ready）===
    if (bWait == 0) {
      bus.wrResp.valid.poke(true.B)
      bus.wrResp.payload.err.poke(false.B)
      if (bus.wrResp.ready.peek().litToBoolean) {
        bWait = -1
      }
    } else {
      bus.wrResp.valid.poke(false.B)
      bus.wrResp.payload.err.poke(false.B)
      if (bWait > 0) bWait -= 1
    }
  }
}

/** 测试用命令描述（poke 到 FbCmd 端口） */
case class FbTestCmd(
    tag: Int = 0,
    transpose: Boolean = false,
    op: String = "",  // "COPY"/"TRANSPOSE"/"PERMUTE"；为空时由 transpose 推断
    dimCount: Int = 1,
    shape: Seq[Int] = Seq(16),
    srcStride: Seq[BigInt] = Seq(16),
    dstStride: Seq[BigInt] = Seq(16),
    srcAddr: BigInt = 0,
    dstAddr: BigInt = 0,
    elemBytesLog2: Int = 0,
    permVec: Seq[Int] = Seq(),
    padBefore: Seq[Int] = Seq(0, 0),
    padAfter: Seq[Int] = Seq(0, 0),
    cvtEnable: Boolean = false,
    srcFmt: Int = 7,      // FbDataFmt: 0=FP32, 1=FP16, 2=BF16, 3=INT8, 7=NONE
    dstFmt: Int = 7,
    cvtScale: BigInt = 0,
    cvtZeroPoint: BigInt = 0
)

class ForagerBeeSpec extends AnyFlatSpec with Matchers {

  behavior of "ForagerBee (NPU DMA)"

  // 功能仿真用小配置：2 通道、128bit 数据口、小突发
  private val testCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 8,
    dataWidth = 128, addressWidth = 32
  )
  private val BB = testCfg.beatBytes // 16 字节/beat

  // ===================== elaboration 冒烟 =====================

  it should "elaborate all numPorts combinations" in {
    for (p <- Seq(1, 2, 3, 4)) {
      val v = ChiselStage.emitSystemVerilog(new ForagerBee(ForagerBeeConfig(numPorts = p)))
      v should include("module ForagerBee")
    }
  }

  it should "elaborate with mixed channelTranspose" in {
    val v = ChiselStage.emitSystemVerilog(new ForagerBee(ForagerBeeConfig(
      numPorts = 3, channelTranspose = Seq(true, false, true)
    )))
    v should include("module ForagerBee")
  }

  it should "reject illegal config parameters" in {
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(numPorts = 0)
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(numPorts = 5)
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(maxBurstLen = 512)
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(queueDepth = 0)
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(maxDims = 0)
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(tileSize = 1)
    // channelTranspose 长度不匹配
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(numPorts = 2, channelTranspose = Seq(true))
    // channelPermute 长度不匹配
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(numPorts = 2, channelPermute = Seq(true))
  }

  // ===================== 仿真辅助 =====================

  private def pokeCmdPayload(port: FbCmd, c: FbTestCmd): Unit = {
    val actualOp = if (c.op.nonEmpty) c.op match {
      case "COPY"      => FbOp.COPY
      case "TRANSPOSE" => FbOp.TRANSPOSE
      case "PERMUTE"   => FbOp.PERMUTE
      case _           => FbOp.COPY
    } else if (c.transpose) FbOp.TRANSPOSE else FbOp.COPY
    port.op.poke(actualOp)
    port.tag.poke(c.tag.U)
    port.dimCount.poke(c.dimCount.U)
    for (d <- 0 until port.shape.length) {
      port.shape(d).poke(c.shape.lift(d).getOrElse(0).U)
      port.srcStride(d).poke(c.srcStride.lift(d).getOrElse(BigInt(0)))
      port.dstStride(d).poke(c.dstStride.lift(d).getOrElse(BigInt(0)))
      port.permVec(d).poke(c.permVec.lift(d).getOrElse(0).U)
    }
    for (d <- 0 until 2) {
      port.padBefore(d).poke(c.padBefore.lift(d).getOrElse(0).U)
      port.padAfter(d).poke(c.padAfter.lift(d).getOrElse(0).U)
    }
    port.srcAddr.poke(c.srcAddr)
    port.dstAddr.poke(c.dstAddr)
    port.elemBytesLog2.poke(c.elemBytesLog2.U)
    port.cvtEnable.poke(c.cvtEnable.B)
    port.srcFmt.poke(c.srcFmt.U)
    port.dstFmt.poke(c.dstFmt.U)
    port.cvtScale.poke(c.cvtScale)
    port.cvtZeroPoint.poke(c.cvtZeroPoint)
    port.nextDescAddr.poke(0.U)
    port.chainMode.poke(false.B)
  }

  /** 跑一组命令直到全部 done（或超时 fail），返回 (done 列表, 内存终态)
    * 命令逐条注入：上一条被接受后再注入下一条 */
  private def runForagerBee(
      cfg: ForagerBeeConfig,
      cmds: Seq[FbTestCmd],
      prefill: mutable.HashMap[BigInt, Byte] = mutable.HashMap.empty,
      maxCycles: Int = 30000
  ): (Seq[(Int, Boolean)], mutable.HashMap[BigInt, Byte]) = {
    val mem = prefill
    val dones = mutable.ArrayBuffer[(Int, Boolean)]() // (tag, err)
    simulate(new ForagerBee(cfg)) { dut =>
      val models = Seq.fill(cfg.numPorts)(new FbStreamMemSlave(mem, cfg.beatBytes))
      val dummy = FbTestCmd()
      var cmdIdx = 0

      // 初始化后显式复位
      pokeCmdPayload(dut.io.cmd.payload, cmds.headOption.getOrElse(dummy))
      dut.io.cmd.valid.poke(false.B)
      dut.io.done.ready.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      var cycle = 0
      while (dones.size < cmds.size && cycle < maxCycles) {
        for (p <- 0 until cfg.numPorts) models(p).step(dut.io.bus(p))
        // 注入命令
        if (cmdIdx < cmds.size) {
          pokeCmdPayload(dut.io.cmd.payload, cmds(cmdIdx))
          dut.io.cmd.valid.poke(true.B)
        } else {
          dut.io.cmd.valid.poke(false.B)
        }
        dut.io.done.ready.poke(true.B)
        // 收集 done
        if (dut.io.done.valid.peek().litToBoolean) {
          dones += ((
            dut.io.done.payload.tag.peek().litValue.toInt,
            dut.io.done.payload.err.peek().litToBoolean
          ))
        }
        // 命令 fire → 推进指针
        if (cmdIdx < cmds.size && dut.io.cmd.ready.peek().litToBoolean) cmdIdx += 1
        dut.clock.step()
        cycle += 1
      }
      assert(dones.size == cmds.size, s"ForagerBee 超时：$maxCycles 拍内仅收到 ${dones.size}/${cmds.size} 个 done")

      // 空跑确认无多余 done、busy 回落
      for (_ <- 0 until 16) {
        for (p <- 0 until cfg.numPorts) models(p).step(dut.io.bus(p))
        dut.io.cmd.valid.poke(false.B)
        dut.io.done.ready.poke(true.B)
        assert(!dut.io.done.valid.peek().litToBoolean, "收到多余 done 回执")
        dut.clock.step()
      }
      assert(!dut.io.busy.peek().litToBoolean, "全部命令完成后 busy 应回落")
    }
    (dones.toSeq, mem)
  }

  private def fillPattern(mem: mutable.HashMap[BigInt, Byte], base: BigInt, len: Int, seed: Int = 0): Unit =
    for (i <- 0 until len) mem(base + i) = ((i * 7 + seed * 31 + 3) & 0xFF).toByte

  private def readLe(mem: mutable.HashMap[BigInt, Byte], addr: BigInt, bytes: Int): BigInt = {
    var v = BigInt(0)
    for (l <- 0 until bytes) v |= BigInt(mem.getOrElse(addr + l, 0.toByte) & 0xFF) << (8 * l)
    v
  }

  // ===================== COPY 用例 =====================

  it should "1D COPY: contiguous block transfer (burst split by maxBurstLen)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 256
    fillPattern(mem, 0, len)
    val cmd = FbTestCmd(tag = 0x11, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x11, false))
    for (i <- 0 until len) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
  }

  it should "2D COPY: strided rows with srcStride(1) > row bytes" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val cols = 32; val rows = 4
    for (r <- 0 until rows; c <- 0 until cols) mem(BigInt(r * 64 + c)) = ((r * cols + c) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x22, dimCount = 2,
      shape = Seq(cols, rows), srcStride = Seq(cols, 64), dstStride = Seq(cols, cols),
      srcAddr = 0, dstAddr = 0x2000)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x22, false))
    for (r <- 0 until rows; c <- 0 until cols) {
      m(BigInt(0x2000 + r * cols + c)) shouldBe (((r * cols + c) & 0xFF).toByte)
    }
  }

  it should "ND COPY: dimCount=3 strided tensor" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val d0 = 16; val d1 = 2; val d2 = 2
    for (i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0) {
      mem(BigInt(i2 * 128 + i1 * 32 + c)) = ((i2 * 64 + i1 * 16 + c) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x33, dimCount = 3,
      shape = Seq(d0, d1, d2), srcStride = Seq(d0, 32, 128), dstStride = Seq(d0, d0, d0 * d1),
      srcAddr = 0, dstAddr = 0x4000)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x33, false))
    for (i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0) {
      m(BigInt(0x4000 + i2 * 32 + i1 * 16 + c)) shouldBe (((i2 * 64 + i1 * 16 + c) & 0xFF).toByte)
    }
  }

  it should "ND COPY: dimCount=4 strided tensor" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val d0 = 16; val d1 = 2; val d2 = 2; val d3 = 2
    for (i3 <- 0 until d3; i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0) {
      val v = ((i3 * 100 + i2 * 30 + i1 * 7 + c * 3 + 5) & 0xFF).toByte
      mem(BigInt(i3 * 256 + i2 * 96 + i1 * 32 + c)) = v
    }
    val cmd = FbTestCmd(tag = 0x34, dimCount = 4,
      shape = Seq(d0, d1, d2, d3),
      srcStride = Seq(d0, 32, 96, 256),
      dstStride = Seq(d0, d0, d0 * d1, d0 * d1 * d2),
      srcAddr = 0, dstAddr = 0x5000)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x34, false))
    for (i3 <- 0 until d3; i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0) {
      val v = ((i3 * 100 + i2 * 30 + i1 * 7 + c * 3 + 5) & 0xFF).toByte
      m(BigInt(0x5000 + i3 * 64 + i2 * 32 + i1 * 16 + c)) shouldBe v
    }
  }

  // ===================== TRANSPOSE 用例 =====================

  it should "transpose 8x8 INT8 matrix" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x44, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(n, 0),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x44, false))
    for (j <- 0 until n; i <- 0 until n) {
      m(BigInt(0x8000 + j * n + i)) shouldBe (((i * n + j) & 0xFF).toByte)
    }
  }

  it should "transpose 8x8 INT16 matrix" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8; val eb = 2
    for (i <- 0 until n; j <- 0 until n) {
      val v = ((i * n + j) * 131) & 0xFFFF
      mem(BigInt(i * 16 + j * eb)) = (v & 0xFF).toByte
      mem(BigInt(i * 16 + j * eb + 1)) = ((v >> 8) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x55, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n * eb, 16), dstStride = Seq(n * eb, 0),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 1)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x55, false))
    for (j <- 0 until n; i <- 0 until n) {
      readLe(m, BigInt(0x8000 + j * n * eb + i * eb), eb) shouldBe (((i * n + j) * 131) & 0xFFFF)
    }
  }

  it should "transpose 10x6 INT8 with non-tile-multiple shape" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val mCols = 10; val nRows = 6
    for (i <- 0 until nRows; j <- 0 until mCols) mem(BigInt(i * 16 + j)) = ((i * mCols + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x66, transpose = true, dimCount = 2,
      shape = Seq(mCols, nRows), srcStride = Seq(mCols, 16), dstStride = Seq(16, 0),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x66, false))
    for (j <- 0 until mCols; i <- 0 until nRows) {
      m(BigInt(0x8000 + j * 16 + i)) shouldBe (((i * mCols + j) & 0xFF).toByte)
    }
  }

  it should "transpose 12x12 INT8 across multiple tile bands" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 12
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x77, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(16, 0),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x77, false))
    for (j <- 0 until n; i <- 0 until n) {
      m(BigInt(0x8000 + j * 16 + i)) shouldBe (((i * n + j) & 0xFF).toByte)
    }
  }

  it should "transpose long rows with TRD read segmentation (maxBurstLen=4 regression)" in {
    val segCfg = ForagerBeeConfig(
      numPorts = 1, queueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 4,
      dataWidth = 128, addressWidth = 32
    )
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val mCols = 128; val nRows = 4
    for (i <- 0 until nRows; j <- 0 until mCols) {
      mem(BigInt(i * 128 + j)) = (((i * 37 + j * 5 + 11) * 7 + 3) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0xAA, transpose = true, dimCount = 2,
      shape = Seq(mCols, nRows), srcStride = Seq(mCols, 128), dstStride = Seq(16, 0),
      srcAddr = 0, dstAddr = 0xB000, elemBytesLog2 = 0)
    val (dones, m) = runForagerBee(segCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xAA, false))
    for (j <- 0 until mCols; i <- 0 until nRows) {
      m(BigInt(0xB000 + j * 16 + i)) shouldBe mem(BigInt(i * 128 + j))
    }
  }

  // ===================== 多通道并发 =====================

  it should "dispatch two non-conflicting commands to different channels concurrently" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0x0000, len, seed = 1)
    fillPattern(mem, 0x2000, len, seed = 2)
    val cmds = Seq(
      FbTestCmd(tag = 0xA1, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0xB2, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x2000, dstAddr = 0x3000)
    )
    val (dones, m) = runForagerBee(testCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xA1, 0xB2)
    dones.forall(!_._2) shouldBe true
    for (i <- 0 until len) {
      m(BigInt(0x1000) + i) shouldBe m(BigInt(0x0000) + i)
      m(BigInt(0x3000) + i) shouldBe m(BigInt(0x2000) + i)
    }
  }

  // ===================== 非法命令 =====================

  it should "report done with err=true for illegal command then stay functional" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 32)
    val cmds = Seq(
      FbTestCmd(tag = 0xEE, transpose = true, dimCount = 3,
        shape = Seq(4, 4, 4), srcStride = Seq(4, 16, 64), dstStride = Seq(4, 16, 64),
        srcAddr = 0, dstAddr = 0x1000),
      FbTestCmd(tag = 0x0F, dimCount = 1,
        shape = Seq(32), srcStride = Seq(32), dstStride = Seq(32),
        srcAddr = 0, dstAddr = 0x2000)
    )
    val (dones, m) = runForagerBee(testCfg, cmds, mem)
    dones shouldBe Seq((0xEE, true), (0x0F, false))
    for (i <- 0 until 32) m(BigInt(0x2000) + i) shouldBe m(BigInt(i))
  }

  private val illegalCmdCases: Seq[(String, FbTestCmd)] = Seq(
    ("dimCount=0", FbTestCmd(tag = 0xD0, dimCount = 0,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x1000)),
    ("基址非对齐", FbTestCmd(tag = 0xD1, dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = BigInt(3), dstAddr = 0x1000)),
    ("COPY 行长非整 beat", FbTestCmd(tag = 0xD2, dimCount = 1,
      shape = Seq(8), srcStride = Seq(8), dstStride = Seq(8),
      srcAddr = 0, dstAddr = 0x1000))
  )

  for ((name, cmd) <- illegalCmdCases) {
    it should s"reject illegal command ($name) with err done and no bus write" in {
      val (dones, m) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
      dones shouldBe Seq((cmd.tag, true))
      for (a <- BigInt(0x1000) until BigInt(0x1100)) m.get(a) shouldBe None
    }
  }

  // ===================== 地址冲突检查用例 =====================

  it should "RAW conflict: block read-after-write on overlapping address" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 128
    fillPattern(mem, 0x0000, len, seed = 10)
    fillPattern(mem, 0x1000, len, seed = 20)
    val cmds = Seq(
      FbTestCmd(tag = 0xC1, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0xC2, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x1000, dstAddr = 0x2000)
    )
    val (dones, m) = runForagerBee(testCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xC1, 0xC2)
    dones.forall(!_._2) shouldBe true
    for (i <- 0 until len) {
      m(BigInt(0x2000) + i) shouldBe m(BigInt(0x0000) + i)
    }
  }

  it should "WAR conflict: block write-after-read on overlapping address" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 128
    fillPattern(mem, 0x1000, len, seed = 30)
    val cmds = Seq(
      FbTestCmd(tag = 0xD1, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x1000, dstAddr = 0x3000),
      FbTestCmd(tag = 0xD2, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000)
    )
    fillPattern(mem, 0x0000, len, seed = 40)
    val (dones, m) = runForagerBee(testCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xD1, 0xD2)
    dones.forall(!_._2) shouldBe true
    for (i <- 0 until len) {
      m(BigInt(0x3000) + i) shouldBe (((i * 7 + 30 * 31 + 3) & 0xFF).toByte)
    }
  }

  it should "WAW conflict: block write-after-write on overlapping address" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 128
    fillPattern(mem, 0x0000, len, seed = 50)
    fillPattern(mem, 0x4000, len, seed = 60)
    val cmds = Seq(
      FbTestCmd(tag = 0xE1, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0xE2, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x4000, dstAddr = 0x1000)
    )
    val (dones, m) = runForagerBee(testCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xE1, 0xE2)
    dones.forall(!_._2) shouldBe true
    for (i <- 0 until len) {
      m(BigInt(0x1000) + i) shouldBe (((i * 7 + 60 * 31 + 3) & 0xFF).toByte)
    }
  }

  it should "no-conflict parallel: two non-overlapping commands execute concurrently on 2 channels" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0x0000, len, seed = 1)
    fillPattern(mem, 0x5000, len, seed = 2)
    val cmds = Seq(
      FbTestCmd(tag = 0xF1, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0xF2, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x5000, dstAddr = 0x6000)
    )
    val (dones, m) = runForagerBee(testCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xF1, 0xF2)
    dones.forall(!_._2) shouldBe true
    for (i <- 0 until len) {
      m(BigInt(0x1000) + i) shouldBe m(BigInt(0x0000) + i)
      m(BigInt(0x6000) + i) shouldBe m(BigInt(0x5000) + i)
    }
  }

  // ===================== 转置通道能力匹配 =====================

  it should "dispatch TRANSPOSE only to transpose-capable channel" in {
    val capCfg = ForagerBeeConfig(
      numPorts = 2, queueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 8,
      dataWidth = 128, addressWidth = 32,
      channelTranspose = Seq(true, false)
    )
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0xCC, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(n, 0),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0)
    val (dones, m) = runForagerBee(capCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xCC, false))
    for (j <- 0 until n; i <- 0 until n) {
      m(BigInt(0x8000 + j * n + i)) shouldBe (((i * n + j) & 0xFF).toByte)
    }
  }

  it should "dispatch COPY to non-transpose channel when transpose channel is busy" in {
    val capCfg = ForagerBeeConfig(
      numPorts = 2, queueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 8,
      dataWidth = 128, addressWidth = 32,
      channelTranspose = Seq(true, false)
    )
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0x0000, len, seed = 1)
    fillPattern(mem, 0x5000, len, seed = 2)
    val cmds = Seq(
      FbTestCmd(tag = 0xA1, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0xA2, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x5000, dstAddr = 0x6000)
    )
    val (dones, m) = runForagerBee(capCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xA1, 0xA2)
    dones.forall(!_._2) shouldBe true
  }

  // ===================== dataQueue 深度约束（信用管理）用例 =====================

  it should "split AR bursts when dataQueueDepth < row beats (credit management)" in {
    val creditCfg = ForagerBeeConfig(
      numPorts = 1, queueDepth = 4, dataQueueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 16,
      dataWidth = 128, addressWidth = 32
    )
    val bb = creditCfg.beatBytes
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val totalBytes = 32 * bb
    fillPattern(mem, 0, totalBytes, seed = 42)
    val cmd = FbTestCmd(tag = 0xDD, dimCount = 1,
      shape = Seq(totalBytes), srcStride = Seq(totalBytes), dstStride = Seq(totalBytes),
      srcAddr = 0, dstAddr = 0x4000)
    val (dones, m) = runForagerBee(creditCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xDD, false))
    for (i <- 0 until totalBytes) {
      m(BigInt(0x4000) + i) shouldBe m(BigInt(i))
    }
  }

  it should "not deadlock with dataQueueDepth=1 (minimum queue)" in {
    val minQCfg = ForagerBeeConfig(
      numPorts = 1, queueDepth = 2, dataQueueDepth = 1, maxDims = 4,
      tileSize = 8, maxBurstLen = 16,
      dataWidth = 128, addressWidth = 32
    )
    val bb = minQCfg.beatBytes
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val totalBytes = 8 * bb
    fillPattern(mem, 0, totalBytes, seed = 99)
    val cmd = FbTestCmd(tag = 0xEF, dimCount = 1,
      shape = Seq(totalBytes), srcStride = Seq(totalBytes), dstStride = Seq(totalBytes),
      srcAddr = 0, dstAddr = 0x6000)
    val (dones, m) = runForagerBee(minQCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xEF, false))
    for (i <- 0 until totalBytes) {
      m(BigInt(0x6000) + i) shouldBe m(BigInt(i))
    }
  }

  it should "2D COPY with small dataQueueDepth splits bursts per row correctly" in {
    val cfg2d = ForagerBeeConfig(
      numPorts = 1, queueDepth = 2, dataQueueDepth = 2, maxDims = 4,
      tileSize = 8, maxBurstLen = 16,
      dataWidth = 128, addressWidth = 32
    )
    val bb = cfg2d.beatBytes
    val cols = 48; val rows = 4
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until rows; c <- 0 until cols) mem(BigInt(r * 64 + c)) = ((r * cols + c) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0xAB, dimCount = 2,
      shape = Seq(cols, rows), srcStride = Seq(cols, 64), dstStride = Seq(cols, cols),
      srcAddr = 0, dstAddr = 0x8000)
    val (dones, m) = runForagerBee(cfg2d, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xAB, false))
    for (r <- 0 until rows; c <- 0 until cols) {
      m(BigInt(0x8000 + r * cols + c)) shouldBe (((r * cols + c) & 0xFF).toByte)
    }
  }

  // ===================== 占用观测 =====================

  it should "expose queue occupancy (non-zero while pending, zero after done)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 256)
    val cmd = FbTestCmd(tag = 0xC1, dimCount = 1,
      shape = Seq(256), srcStride = Seq(256), dstStride = Seq(256),
      srcAddr = 0, dstAddr = 0x1000)
    simulate(new ForagerBee(testCfg)) { dut =>
      val models = Seq.fill(testCfg.numPorts)(new FbStreamMemSlave(mem, testCfg.beatBytes))
      pokeCmdPayload(dut.io.cmd.payload, cmd)
      dut.io.cmd.valid.poke(false.B)
      dut.io.done.ready.poke(true.B)
      dut.reset.poke(true.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      var pushed = 0
      var doneCnt = 0
      var sawOcc = false
      var cycle = 0
      while (doneCnt < 3 && cycle < 30000) {
        for (p <- 0 until testCfg.numPorts) models(p).step(dut.io.bus(p))
        pokeCmdPayload(dut.io.cmd.payload, cmd)
        dut.io.cmd.valid.poke((pushed < 3).B)
        dut.io.done.ready.poke(true.B)
        if (dut.io.occupancy.peek().litValue > 0) sawOcc = true
        if (dut.io.cmd.ready.peek().litToBoolean && pushed < 3) pushed += 1
        if (dut.io.done.valid.peek().litToBoolean) {
          dut.io.done.payload.tag.peek().litValue.toInt shouldBe 0xC1
          dut.io.done.payload.err.peek().litToBoolean shouldBe false
          doneCnt += 1
        }
        dut.clock.step()
        cycle += 1
      }
      assert(doneCnt == 3, s"occupancy 用例超时：30000 拍内仅收到 $doneCnt/3 个 done")
      assert(sawOcc, "命令入队后应观测到队列占用非零")
      pushed shouldBe 3
      for (_ <- 0 until 8) {
        for (p <- 0 until testCfg.numPorts) models(p).step(dut.io.bus(p))
        dut.io.cmd.valid.poke(false.B)
        dut.io.done.ready.poke(true.B)
        assert(!dut.io.done.valid.peek().litToBoolean, "收到多余 done 回执")
        dut.clock.step()
      }
      dut.io.occupancy.peek().litValue shouldBe 0
      dut.io.busy.peek().litToBoolean shouldBe false
    }
  }

  // ===================== PERMUTE 用例 =====================

  private val permCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 5,
    tileSize = 8, maxBurstLen = 8,
    dataWidth = 128, addressWidth = 32
  )

  it should "PERMUTE 2D: simple row-col swap (elaboration smoke)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 32, seed = 77)
    val cmd = FbTestCmd(tag = 0x81, op = "PERMUTE", dimCount = 2, elemBytesLog2 = 2,
      shape = Seq(4, 2), permVec = Seq(1, 0),
      srcStride = Seq(16, 16), dstStride = Seq(16, 16),
      srcAddr = 0, dstAddr = 0x2000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x81, false))
  }

  it should "PERMUTE 3D: swap dim1 and dim2 (shape=[4,3,4])" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 4 * 3 * 4 * 4, seed = 88)
    val cmd = FbTestCmd(tag = 0x82, op = "PERMUTE", dimCount = 3, elemBytesLog2 = 2,
      shape = Seq(4, 3, 4), permVec = Seq(0, 2, 1),
      srcStride = Seq(16, 16, 48), dstStride = Seq(16, 16, 48),
      srcAddr = 0, dstAddr = 0x3000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x82, false))
  }

  it should "PERMUTE 5D: complex permutation (maxDims=5)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 4 * 2 * 2 * 2 * 3 * 4, seed = 99)
    val cmd = FbTestCmd(tag = 0x83, op = "PERMUTE", dimCount = 5, elemBytesLog2 = 2,
      shape = Seq(4, 2, 2, 2, 3), permVec = Seq(0, 4, 2, 3, 1),
      srcStride = Seq(16, 16, 32, 64, 128),
      dstStride = Seq(16, 16, 32, 64, 128),
      srcAddr = 0, dstAddr = 0x4000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x83, false))
  }

  it should "PERMUTE illegal permVec (value >= dimCount) → err" in {
    val cmd = FbTestCmd(tag = 0x84, op = "PERMUTE", dimCount = 2, elemBytesLog2 = 2,
      shape = Seq(4, 2), permVec = Seq(1, 5),
      srcStride = Seq(16, 16), dstStride = Seq(16, 16),
      srcAddr = 0, dstAddr = 0x5000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x84, true))
  }

  // ===================== Zero-Padding 用例 =====================

  private val padCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 5,
    tileSize = 8, maxBurstLen = 8,
    dataWidth = 128, addressWidth = 32
  )

  it should "Zero-Padding 1D: dim0 padding (dimCount=2 shape(1)=1)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 16, seed = 11)
    val cmd = FbTestCmd(tag = 0x85, op = "COPY", dimCount = 2, elemBytesLog2 = 0,
      shape = Seq(16, 1),
      srcStride = Seq(16, 16), dstStride = Seq(48, 48),
      srcAddr = 0, dstAddr = 0x6000,
      padBefore = Seq(16, 0), padAfter = Seq(16, 0))
    val (dones, _) = runForagerBee(padCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x85, false))
  }

  it should "Zero-Padding 2D: dim0+dim1 padding (shape=[16,4])" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 16 * 4, seed = 22)
    val cmd = FbTestCmd(tag = 0x86, op = "COPY", dimCount = 2, elemBytesLog2 = 0,
      shape = Seq(16, 4),
      srcStride = Seq(16, 16), dstStride = Seq(48, 48),
      srcAddr = 0, dstAddr = 0x7000,
      padBefore = Seq(16, 1), padAfter = Seq(16, 1))
    val (dones, _) = runForagerBee(padCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x86, false))
  }

  it should "Zero-Padding dimCount > 2 → legal (padding on dim0/dim1 only)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val d0 = 16; val d1 = 2; val d2 = 2
    fillPattern(mem, 0, d0 * d1 * d2, seed = 87)
    val outCols = 16 + d0 + 16
    val outRows = 1 + d1 + 1
    val cmd = FbTestCmd(tag = 0x87, op = "COPY", dimCount = 3, elemBytesLog2 = 0,
      shape = Seq(d0, d1, d2),
      srcStride = Seq(d0, d0, d0 * d1), dstStride = Seq(outCols, outCols, outCols * outRows),
      srcAddr = 0, dstAddr = 0x8000,
      padBefore = Seq(16, 1), padAfter = Seq(16, 1))
    val (dones, _) = runForagerBee(padCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x87, false))
  }

  it should "Zero-Padding 3D: dim2 outer loop with dim0+dim1 padding" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val d0 = 16; val d1 = 3; val d2 = 2
    val padB0 = 16; val padA0 = 16; val padB1 = 1; val padA1 = 1
    val srcRowStride = d0
    val srcSliceStride = d0 * d1
    for (i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0)
      mem(BigInt(i2 * srcSliceStride + i1 * srcRowStride + c)) = ((i2 * 50 + i1 * 10 + c) & 0xFF).toByte
    val outCols = padB0 + d0 + padA0
    val outRows = padB1 + d1 + padA1
    val dstRowStride = outCols
    val dstSliceStride = dstRowStride * outRows
    val cmd = FbTestCmd(tag = 0x90, op = "COPY", dimCount = 3, elemBytesLog2 = 0,
      shape = Seq(d0, d1, d2),
      srcStride = Seq(d0, srcRowStride, srcSliceStride),
      dstStride = Seq(outCols, dstRowStride, dstSliceStride),
      srcAddr = 0, dstAddr = 0xB000,
      padBefore = Seq(padB0, padB1), padAfter = Seq(padA0, padA1))
    val (dones, m) = runForagerBee(padCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x90, false))
    for (i2 <- 0 until d2) {
      val sliceBase = BigInt(0xB000) + i2 * dstSliceStride
      for (c <- 0 until outCols)
        m.getOrElse(sliceBase + 0 * dstRowStride + c, 0.toByte) shouldBe 0.toByte
      for (i1 <- 0 until d1) {
        val rowBase = sliceBase + (padB1 + i1) * dstRowStride
        for (c <- 0 until padB0)
          m.getOrElse(rowBase + c, 0.toByte) shouldBe 0.toByte
        for (c <- 0 until d0) {
          val expected = ((i2 * 50 + i1 * 10 + c) & 0xFF).toByte
          m.getOrElse(rowBase + padB0 + c, 0.toByte) shouldBe expected
        }
        for (c <- 0 until padA0)
          m.getOrElse(rowBase + padB0 + d0 + c, 0.toByte) shouldBe 0.toByte
      }
      for (c <- 0 until outCols)
        m.getOrElse(sliceBase + (padB1 + d1) * dstRowStride + c, 0.toByte) shouldBe 0.toByte
    }
  }

  it should "Zero-Padding + non-COPY op (PERMUTE) → err" in {
    val cmd = FbTestCmd(tag = 0x88, op = "PERMUTE", dimCount = 2, elemBytesLog2 = 2,
      shape = Seq(4, 2), permVec = Seq(1, 0),
      srcStride = Seq(16, 16), dstStride = Seq(16, 16),
      srcAddr = 0, dstAddr = 0x9000,
      padBefore = Seq(4, 0), padAfter = Seq(4, 0))
    val (dones, _) = runForagerBee(padCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x88, true))
  }

  it should "COPY regression (pad=0): 2D COPY with zero padding fields" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val cols = 32; val rows = 4
    for (r <- 0 until rows; c <- 0 until cols) mem(BigInt(r * 32 + c)) = ((r * cols + c) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x89, op = "COPY", dimCount = 2, elemBytesLog2 = 0,
      shape = Seq(cols, rows),
      srcStride = Seq(cols, cols), dstStride = Seq(cols, cols),
      srcAddr = 0, dstAddr = 0xA000,
      padBefore = Seq(0, 0), padAfter = Seq(0, 0))
    val (dones, m) = runForagerBee(padCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x89, false))
    for (r <- 0 until rows; c <- 0 until cols) {
      m(BigInt(0xA000 + r * cols + c)) shouldBe (((r * cols + c) & 0xFF).toByte)
    }
  }

  // ===================== Copy-Only DMA（轻量级）用例 =====================

  private val copyOnlyCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 8,
    dataWidth = 128, addressWidth = 32,
    channelTranspose = Seq(false, false),
    channelPermute = Seq(false, false)
  )

  it should "elaborate copy-only DMA (channelTranspose all false, channelPermute all false)" in {
    val v = ChiselStage.emitSystemVerilog(new ForagerBee(copyOnlyCfg))
    v should include("module ForagerBee")
  }

  it should "copy-only DMA rejects TRANSPOSE with err=true" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0xC0, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(n, 0),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0)
    val (dones, _) = runForagerBee(copyOnlyCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xC0, true))
  }

  it should "copy-only DMA rejects PERMUTE with err=true" in {
    val cmd = FbTestCmd(tag = 0xC1, op = "PERMUTE", dimCount = 2, elemBytesLog2 = 2,
      shape = Seq(4, 2), permVec = Seq(1, 0),
      srcStride = Seq(16, 16), dstStride = Seq(16, 16),
      srcAddr = 0, dstAddr = 0x2000)
    val (dones, _) = runForagerBee(copyOnlyCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC1, true))
  }

  it should "copy-only DMA COPY works normally" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0, len, seed = 77)
    val cmd = FbTestCmd(tag = 0xC2, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000)
    val (dones, m) = runForagerBee(copyOnlyCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xC2, false))
    for (i <- 0 until len) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
  }

  it should "mixed config: TRANSPOSE to ch0, PERMUTE to ch1" in {
    val mixedCfg = ForagerBeeConfig(
      numPorts = 2, queueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 8,
      dataWidth = 128, addressWidth = 32,
      channelTranspose = Seq(true, false),
      channelPermute = Seq(false, true)
    )
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    fillPattern(mem, 0x3000, 4 * 2 * 4, seed = 55)
    val cmds = Seq(
      FbTestCmd(tag = 0xD1, transpose = true, dimCount = 2,
        shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(n, 0),
        srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0),
      FbTestCmd(tag = 0xD2, op = "PERMUTE", dimCount = 2, elemBytesLog2 = 2,
        shape = Seq(4, 2), permVec = Seq(1, 0),
        srcStride = Seq(16, 16), dstStride = Seq(16, 16),
        srcAddr = 0x3000, dstAddr = 0x4000)
    )
    val (dones, m) = runForagerBee(mixedCfg, cmds, mem)
    dones.map(_._1).toSet shouldBe Set(0xD1, 0xD2)
    dones.forall(!_._2) shouldBe true
    for (j <- 0 until n; i <- 0 until n) {
      m(BigInt(0x8000 + j * n + i)) shouldBe (((i * n + j) & 0xFF).toByte)
    }
  }

  // ===================== 链式描述符 (Chaining) 用例 =====================

  private val chainCfg = ForagerBeeConfig(
    numPorts = 1, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 8,
    dataWidth = 128, addressWidth = 32,
    enableChaining = true
  )
  private val chainBB = chainCfg.beatBytes // 16

  /** 将 FbCmd 字段值序列化为描述符 beat 序列（Seq[BigInt]，每元素 dataWidth 位）。
    * 字段布局遵循 FbDescLayout 的声明顺序。
    */
  private def serializeDescriptor(
      cfg: ForagerBeeConfig,
      op: Int,         // 0=COPY,1=TRANSPOSE,2=PERMUTE
      tag: Int,
      dimCount: Int,
      shape: Seq[Int],
      srcStride: Seq[BigInt],
      dstStride: Seq[BigInt],
      srcAddr: BigInt,
      dstAddr: BigInt,
      elemBytesLog2: Int,
      permVec: Seq[Int] = Seq(),
      padBefore: Seq[Int] = Seq(0, 0),
      padAfter: Seq[Int] = Seq(0, 0),
      nextDescAddr: BigInt = 0,
      chainMode: Boolean = true,
      cvtEnable: Boolean = false,
      srcFmt: Int = 7,
      dstFmt: Int = 7,
      cvtScale: BigInt = 0,
      cvtZeroPoint: BigInt = 0
  ): Seq[BigInt] = {
    val addrW = cfg.addressWidth
    val maxDims = cfg.maxDims
    val dataWidth = cfg.dataWidth

    // Build bit vector by packing fields in order
    var bits = BigInt(0)
    var offset = 0

    def pack(value: BigInt, width: Int): Unit = {
      bits |= (value & ((BigInt(1) << width) - 1)) << offset
      offset += width
    }

    pack(BigInt(op), 2)           // op
    pack(BigInt(tag), 8)          // tag
    pack(BigInt(dimCount), 3)     // dimCount
    for (d <- 0 until maxDims) pack(BigInt(shape.lift(d).getOrElse(0)), 16)        // shape
    for (d <- 0 until maxDims) pack(srcStride.lift(d).getOrElse(BigInt(0)), addrW) // srcStride
    for (d <- 0 until maxDims) pack(dstStride.lift(d).getOrElse(BigInt(0)), addrW) // dstStride
    pack(srcAddr, addrW)          // srcAddr
    pack(dstAddr, addrW)          // dstAddr
    pack(BigInt(elemBytesLog2), 2) // elemBytesLog2
    for (d <- 0 until maxDims) pack(BigInt(permVec.lift(d).getOrElse(0)), 3)       // permVec
    for (d <- 0 until 2) pack(BigInt(padBefore.lift(d).getOrElse(0)), 16)          // padBefore
    for (d <- 0 until 2) pack(BigInt(padAfter.lift(d).getOrElse(0)), 16)           // padAfter
    pack(nextDescAddr, addrW)     // nextDescAddr
    pack(if (chainMode) BigInt(1) else BigInt(0), 1) // chainMode
    pack(if (cvtEnable) BigInt(1) else BigInt(0), 1) // cvtEnable
    pack(BigInt(srcFmt), 3)       // srcFmt
    pack(BigInt(dstFmt), 3)       // dstFmt
    pack(cvtScale, 32)            // cvtScale
    pack(cvtZeroPoint, 32)        // cvtZeroPoint

    // Split into beats
    val descBeats = FbDescLayout.descBeats(cfg)
    val result = (0 until descBeats).map { i =>
      (bits >> (i * dataWidth)) & ((BigInt(1) << dataWidth) - 1)
    }
    result
  }

  /** 将描述符 beat 序列写入内存（little-endian） */
  private def writeDescToMem(mem: mutable.HashMap[BigInt, Byte], baseAddr: BigInt, beats: Seq[BigInt], beatBytes: Int): Unit = {
    for ((beat, i) <- beats.zipWithIndex) {
      for (b <- 0 until beatBytes) {
        mem(baseAddr + i * beatBytes + b) = ((beat >> (8 * b)) & 0xFF).toByte
      }
    }
  }

  /** 链式描述符仿真：包含 ChainBusSlave 处理 chainBus 读请求 */
  private def runForagerBeeChain(
      cfg: ForagerBeeConfig,
      cmds: Seq[FbTestCmdChain],
      prefill: mutable.HashMap[BigInt, Byte] = mutable.HashMap.empty,
      chainMem: mutable.HashMap[BigInt, Byte] = mutable.HashMap.empty,
      maxCycles: Int = 50000
  ): (Seq[(Int, Boolean, Int)], mutable.HashMap[BigInt, Byte]) = {
    // returns (tag, err, chainLen)
    val mem = prefill
    val dones = mutable.ArrayBuffer[(Int, Boolean, Int)]()
    simulate(new ForagerBee(cfg)) { dut =>
      val models = Seq.fill(cfg.numPorts)(new FbStreamMemSlave(mem, cfg.beatBytes))
      // ChainBusSlave state
      val chainBus = dut.io.chainBus.get
      var chainRdActive = false
      var chainRdAddr = BigInt(0)
      var chainRdRemain = 0
      var chainRdBeatIdx = 0

      val dummy = FbTestCmdChain()
      var cmdIdx = 0

      // 初始化
      pokeCmdPayload(dut.io.cmd.payload, cmds.headOption.getOrElse(dummy).toFbTestCmd)
      dut.io.cmd.valid.poke(false.B)
      dut.io.cmd.payload.chainMode.poke(false.B)
      dut.io.cmd.payload.nextDescAddr.poke(0.U)
      dut.io.done.ready.poke(true.B)
      dut.reset.poke(true.B)
      // chainBus 初始化
      chainBus.rd.ready.poke(true.B)
      chainBus.rdData.valid.poke(false.B)
      chainBus.rdData.payload.data.poke(0.U)
      chainBus.rdData.payload.sof.poke(false.B)
      chainBus.rdData.payload.eof.poke(false.B)
      chainBus.rdData.payload.err.poke(false.B)
      chainBus.wr.ready.poke(true.B)
      chainBus.wrResp.valid.poke(false.B)
      chainBus.wrResp.payload.err.poke(false.B)
      dut.clock.step(2)
      dut.reset.poke(false.B)

      var cycle = 0
      while (dones.size < cmds.size && cycle < maxCycles) {
        // Engine bus slaves
        for (p <- 0 until cfg.numPorts) models(p).step(dut.io.bus(p))

        // === ChainBus Slave ===
        chainBus.rd.ready.poke((!chainRdActive).B)
        if (!chainRdActive && chainBus.rd.valid.peek().litToBoolean) {
          chainRdAddr = chainBus.rd.payload.addr.peek().litValue
          chainRdRemain = chainBus.rd.payload.len.peek().litValue.toInt + 1
          chainRdBeatIdx = 0
          chainRdActive = true
        }
        if (chainRdActive && chainRdRemain > 0) {
          chainBus.rdData.valid.poke(true.B)
          // Read from chainMem
          var beatVal = BigInt(0)
          for (b <- 0 until cfg.beatBytes) {
            beatVal |= BigInt(chainMem.getOrElse(chainRdAddr + b, 0.toByte) & 0xFF) << (8 * b)
          }
          chainBus.rdData.payload.data.poke(beatVal)
          chainBus.rdData.payload.sof.poke((chainRdBeatIdx == 0).B)
          chainBus.rdData.payload.eof.poke((chainRdRemain == 1).B)
          chainBus.rdData.payload.err.poke(false.B)
          if (chainBus.rdData.ready.peek().litToBoolean) {
            chainRdAddr += cfg.beatBytes
            chainRdBeatIdx += 1
            chainRdRemain -= 1
            if (chainRdRemain == 0) chainRdActive = false
          }
        } else {
          chainBus.rdData.valid.poke(false.B)
          chainBus.rdData.payload.data.poke(0.U)
          chainBus.rdData.payload.sof.poke(false.B)
          chainBus.rdData.payload.eof.poke(false.B)
          chainBus.rdData.payload.err.poke(false.B)
        }
        chainBus.wrResp.valid.poke(false.B)
        chainBus.wrResp.payload.err.poke(false.B)

        // 注入命令
        if (cmdIdx < cmds.size) {
          pokeCmdPayload(dut.io.cmd.payload, cmds(cmdIdx).toFbTestCmd)
          // 需要额外 poke chainMode 和 nextDescAddr
          dut.io.cmd.payload.chainMode.poke(cmds(cmdIdx).chainMode.B)
          dut.io.cmd.payload.nextDescAddr.poke(cmds(cmdIdx).nextDescAddr)
          dut.io.cmd.valid.poke(true.B)
        } else {
          dut.io.cmd.valid.poke(false.B)
        }
        dut.io.done.ready.poke(true.B)

        // 收集 done
        if (dut.io.done.valid.peek().litToBoolean) {
          dones += ((
            dut.io.done.payload.tag.peek().litValue.toInt,
            dut.io.done.payload.err.peek().litToBoolean,
            dut.io.done.payload.chainLen.peek().litValue.toInt
          ))
        }
        // 命令 fire → 推进指针
        if (cmdIdx < cmds.size && dut.io.cmd.ready.peek().litToBoolean) cmdIdx += 1
        dut.clock.step()
        cycle += 1
      }
      assert(dones.size == cmds.size, s"Chain 测试超时：$maxCycles 拍内仅收到 ${dones.size}/${cmds.size} 个 done")
    }
    (dones.toSeq, mem)
  }

  /** 扩展的测试命令（支持链模式字段） */
  case class FbTestCmdChain(
      tag: Int = 0,
      transpose: Boolean = false,
      op: String = "",
      dimCount: Int = 1,
      shape: Seq[Int] = Seq(16),
      srcStride: Seq[BigInt] = Seq(16),
      dstStride: Seq[BigInt] = Seq(16),
      srcAddr: BigInt = 0,
      dstAddr: BigInt = 0,
      elemBytesLog2: Int = 0,
      permVec: Seq[Int] = Seq(),
      padBefore: Seq[Int] = Seq(0, 0),
      padAfter: Seq[Int] = Seq(0, 0),
      chainMode: Boolean = false,
      nextDescAddr: BigInt = 0
  ) {
    def toFbTestCmd: FbTestCmd = FbTestCmd(tag, transpose, op, dimCount, shape, srcStride, dstStride,
      srcAddr, dstAddr, elemBytesLog2, permVec, padBefore, padAfter)
  }

  it should "chain: elaborate with enableChaining=true" in {
    val v = _root_.circt.stage.ChiselStage.emitSystemVerilog(new ForagerBee(chainCfg))
    v should include("module ForagerBee")
  }

  it should "chain: single descriptor (chainMode=true, nextDescAddr=0) behaves like normal COPY" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val chainMem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0, len)

    val cmd = FbTestCmdChain(tag = 0x50, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000,
      chainMode = true, nextDescAddr = 0)

    val (dones, m) = runForagerBeeChain(chainCfg, Seq(cmd), mem, chainMem)
    dones.size shouldBe 1
    dones(0)._1 shouldBe 0x50
    dones(0)._2 shouldBe false
    dones(0)._3 shouldBe 1  // chainLen=1 (only one descriptor, nextAddr=0)
    for (i <- 0 until len) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
  }

  it should "chain: 2-node chain executes both descriptors and reports chainLen=2" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val chainMem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    // Source data for first COPY: 0→0x1000
    fillPattern(mem, 0, len, seed = 10)
    // Source data for second COPY: 0x2000→0x3000
    fillPattern(mem, 0x2000, len, seed = 20)

    // Descriptor 1 at address 0x10000 in chainMem:
    // COPY 0x2000→0x3000, nextDescAddr=0 (end of chain)
    val desc1Addr = BigInt(0x10000)
    val desc1Beats = serializeDescriptor(chainCfg,
      op = 0, tag = 0x51, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = BigInt(0x2000), dstAddr = BigInt(0x3000),
      elemBytesLog2 = 0,
      nextDescAddr = 0, chainMode = true
    )
    writeDescToMem(chainMem, desc1Addr, desc1Beats, chainCfg.beatBytes)

    // Initial command: COPY 0→0x1000, chainMode=true, nextDescAddr=desc1Addr
    val cmd = FbTestCmdChain(tag = 0x51, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000,
      chainMode = true, nextDescAddr = desc1Addr)

    val (dones, m) = runForagerBeeChain(chainCfg, Seq(cmd), mem, chainMem)
    dones.size shouldBe 1
    dones(0)._1 shouldBe 0x51
    dones(0)._2 shouldBe false
    dones(0)._3 shouldBe 2  // chainLen=2
    // Verify first COPY
    for (i <- 0 until len) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
    // Verify second COPY
    for (i <- 0 until len) m(BigInt(0x3000) + i) shouldBe m(BigInt(0x2000) + i)
  }

  it should "chain: chainMode=false passes through without chaining" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val chainMem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0, len, seed = 30)

    val cmd = FbTestCmdChain(tag = 0x52, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000,
      chainMode = false, nextDescAddr = 0)

    val (dones, m) = runForagerBeeChain(chainCfg, Seq(cmd), mem, chainMem)
    dones.size shouldBe 1
    dones(0)._1 shouldBe 0x52
    dones(0)._2 shouldBe false
    dones(0)._3 shouldBe 1  // non-chain → chainLen=1
    for (i <- 0 until len) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
  }

  // ===================== 格式转换 (Format Conversion) 用例 =====================

  private val cvtCfg = ForagerBeeConfig(
    numPorts = 1, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 16,
    dataWidth = 128, addressWidth = 32,
    enableConversion = true, converterPipeStages = 1
  )

  it should "format conversion: cvtEnable=false regression (plain COPY unchanged)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0, len)
    val cmd = FbTestCmd(tag = 0xF0, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000,
      cvtEnable = false, srcFmt = 7, dstFmt = 7)
    val (dones, m) = runForagerBee(cvtCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xF0, false))
    for (i <- 0 until len) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
  }

  it should "format conversion: FP32→FP16 compression (4 FP32 elements → 2 beats become 1)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // Write 4 FP32 values into source: 1.0f, 2.0f, 3.0f, 4.0f (each 4 bytes)
    // FP32 1.0 = 0x3F800000, 2.0 = 0x40000000, 3.0 = 0x40400000, 4.0 = 0x40800000
    val fp32Vals = Seq(0x3F800000L, 0x40000000L, 0x40400000L, 0x40800000L,
                       0x3F800000L, 0x40000000L, 0x40400000L, 0x40800000L)
    for (i <- fp32Vals.indices; b <- 0 until 4) {
      mem(BigInt(i * 4 + b)) = ((fp32Vals(i) >> (b * 8)) & 0xFF).toByte
    }
    // 8 FP32 elements = 32 bytes = 2 beats of 128-bit
    // After FP32→FP16: 8 FP16 elements = 16 bytes = 1 beat
    val cmd = FbTestCmd(tag = 0xF1, dimCount = 1, elemBytesLog2 = 2,
      shape = Seq(8), srcStride = Seq(32), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x2000,
      cvtEnable = true, srcFmt = 0, dstFmt = 1) // FP32→FP16
    val (dones, m) = runForagerBee(cvtCfg, Seq(cmd), mem, maxCycles = 5000)
    dones shouldBe Seq((0xF1, false))
    // Verify FP16 values: 1.0h=0x3C00, 2.0h=0x4000, 3.0h=0x4200, 4.0h=0x4400
    val expectedFp16 = Seq(0x3C00, 0x4000, 0x4200, 0x4400, 0x3C00, 0x4000, 0x4200, 0x4400)
    for (i <- expectedFp16.indices) {
      val lo = m.getOrElse(BigInt(0x2000 + i * 2), 0.toByte) & 0xFF
      val hi = m.getOrElse(BigInt(0x2000 + i * 2 + 1), 0.toByte) & 0xFF
      val actual = lo | (hi << 8)
      actual shouldBe expectedFp16(i)
    }
  }

  it should "format conversion: FP16→FP32 expansion (8 FP16 elements → 1 beat becomes 2)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // Write 8 FP16 values: 1.0h=0x3C00, 2.0h=0x4000, ...
    val fp16Vals = Seq(0x3C00, 0x4000, 0x4200, 0x4400, 0x4500, 0x4600, 0x4700, 0x4800)
    for (i <- fp16Vals.indices; b <- 0 until 2) {
      mem(BigInt(i * 2 + b)) = ((fp16Vals(i) >> (b * 8)) & 0xFF).toByte
    }
    // 8 FP16 = 16 bytes = 1 beat. After FP16→FP32: 8 FP32 = 32 bytes = 2 beats
    val cmd = FbTestCmd(tag = 0xF2, dimCount = 1, elemBytesLog2 = 1,
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x3000,
      cvtEnable = true, srcFmt = 1, dstFmt = 0) // FP16→FP32
    val (dones, m) = runForagerBee(cvtCfg, Seq(cmd), mem, maxCycles = 5000)
    dones shouldBe Seq((0xF2, false))
    // Verify FP32 1.0 = 0x3F800000
    val fp32_1_0 = 0x3F800000L
    for (b <- 0 until 4) {
      (m.getOrElse(BigInt(0x3000 + b), 0.toByte) & 0xFF) shouldBe ((fp32_1_0 >> (b * 8)) & 0xFF).toInt
    }
  }

  it should "format conversion: BF16→FP32 expansion" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // BF16 1.0 = 0x3F80 (upper 16 bits of FP32 1.0=0x3F800000)
    // BF16 2.0 = 0x4000
    val bf16Vals = Seq(0x3F80, 0x4000, 0x4040, 0x4080, 0x40A0, 0x40C0, 0x40E0, 0x4100)
    for (i <- bf16Vals.indices; b <- 0 until 2) {
      mem(BigInt(i * 2 + b)) = ((bf16Vals(i) >> (b * 8)) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0xF3, dimCount = 1, elemBytesLog2 = 1,
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x4000,
      cvtEnable = true, srcFmt = 2, dstFmt = 0) // BF16→FP32
    val (dones, m) = runForagerBee(cvtCfg, Seq(cmd), mem, maxCycles = 5000)
    dones shouldBe Seq((0xF3, false))
    // BF16→FP32: just pad 16 zeros. BF16 0x3F80 → FP32 0x3F800000
    val expected0 = 0x3F800000L
    for (b <- 0 until 4) {
      (m.getOrElse(BigInt(0x4000 + b), 0.toByte) & 0xFF) shouldBe ((expected0 >> (b * 8)) & 0xFF).toInt
    }
  }

  it should "format conversion: FP16↔BF16 same-width (1:1 ratio)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 8 FP16 values = 16 bytes = 1 beat
    val fp16Vals = Seq(0x3C00, 0x4000, 0x4200, 0x4400, 0x4500, 0x4600, 0x4700, 0x4800)
    for (i <- fp16Vals.indices; b <- 0 until 2) {
      mem(BigInt(i * 2 + b)) = ((fp16Vals(i) >> (b * 8)) & 0xFF).toByte
    }
    // FP16→BF16: same element width (16 bits), so 1:1 ratio, beat count unchanged
    val cmd = FbTestCmd(tag = 0xF4, dimCount = 1, elemBytesLog2 = 1,
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x5000,
      cvtEnable = true, srcFmt = 1, dstFmt = 2) // FP16→BF16
    val (dones, m) = runForagerBee(cvtCfg, Seq(cmd), mem, maxCycles = 5000)
    dones shouldBe Seq((0xF4, false))
    // FP16 1.0 (0x3C00) → FP32 0x3F800000 → BF16 0x3F80
    val lo = m.getOrElse(BigInt(0x5000), 0.toByte) & 0xFF
    val hi = m.getOrElse(BigInt(0x5001), 0.toByte) & 0xFF
    (lo | (hi << 8)) shouldBe 0x3F80
  }
}

