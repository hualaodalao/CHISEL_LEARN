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
    op: String = "",  // "COPY"/"TRANSPOSE"/"PERMUTE"/"IM2COL"/"SCATTER"/"GATHER"/"TILE2LINEAR"；为空时由 transpose 推断
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
    cvtZeroPoint: BigInt = 0,
    im2colKernel: Seq[Int] = Seq(0, 0),
    im2colStride: Seq[Int] = Seq(0, 0),
    im2colPad: Seq[Int] = Seq(0, 0),
    im2colDilation: Seq[Int] = Seq(0, 0),
    im2colInShape: Seq[Int] = Seq(0, 0, 0),
    sgListAddr: BigInt = 0,
    sgEntryCount: Int = 0,
    srcStartIdx: Seq[Int] = Seq(0, 0, 0, 0, 0),
    // TILE2LINEAR 专用字段
    t2lMatCols: Int = 0,
    t2lTileRows: Int = 0,
    t2lTileCols: Int = 0,
    t2lNumTileRows: Int = 0,
    t2lNumTileCols: Int = 0
)

class ForagerBeeSpec extends AnyFlatSpec with Matchers {

  behavior of "ForagerBee (NPU DMA)"

  // 功能仿真用小配置：2 通道、128bit 数据口、小突发
  private val testCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 5,
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
      case "COPY"         => FbOp.COPY
      case "TRANSPOSE"    => FbOp.TRANSPOSE
      case "PERMUTE"      => FbOp.PERMUTE
      case "IM2COL"       => FbOp.IM2COL
      case "SCATTER"      => FbOp.SCATTER
      case "GATHER"       => FbOp.GATHER
      case "TILE2LINEAR"  => FbOp.TILE2LINEAR
      case "LINEAR2TILE"  => FbOp.LINEAR2TILE
      case _              => FbOp.COPY
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
    // im2col fields
    for (d <- 0 until 2) {
      port.im2colKernel(d).poke(c.im2colKernel.lift(d).getOrElse(0).U)
      port.im2colStride(d).poke(c.im2colStride.lift(d).getOrElse(0).U)
      port.im2colPad(d).poke(c.im2colPad.lift(d).getOrElse(0).U)
      port.im2colDilation(d).poke(c.im2colDilation.lift(d).getOrElse(0).U)
    }
    for (d <- 0 until 3) {
      port.im2colInShape(d).poke(c.im2colInShape.lift(d).getOrElse(0).U)
    }
    // scatter/gather fields
    port.sgListAddr.poke(c.sgListAddr)
    port.sgEntryCount.poke(c.sgEntryCount.U)
    // srcStartIdx fields
    for (d <- 0 until port.srcStartIdx.length) {
      port.srcStartIdx(d).poke(c.srcStartIdx.lift(d).getOrElse(0).U)
    }
    // TILE2LINEAR fields
    port.t2lMatCols.poke(c.t2lMatCols.U)
    port.t2lTileRows.poke(c.t2lTileRows.U)
    port.t2lTileCols.poke(c.t2lTileCols.U)
    port.t2lNumTileRows.poke(c.t2lNumTileRows.U)
    port.t2lNumTileCols.poke(c.t2lNumTileCols.U)
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

  // ===================== IM2COL 用例 =====================

  // im2col 配置：单通道 + dataWidth=32 (beatBytes=4) 简化对齐
  private val i2cCfg = ForagerBeeConfig(
    numPorts = 1, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 64,
    dataWidth = 32, addressWidth = 32,
    channelIm2col = Seq(true)
  )
  private val i2cBB = i2cCfg.beatBytes // 4

  /** im2col 参考实现：计算展开矩阵（元素值 Seq）
    * featureMap: C*H*W 连续元素，CHW 布局
    * 遍历顺序（外→内）：oh → ow → c → kh → kw
    */
  private def computeIm2col(
      featureMap: Seq[Int], C: Int, H: Int, W: Int,
      kH: Int, kW: Int, sH: Int, sW: Int,
      pH: Int, pW: Int, dH: Int, dW: Int
  ): Seq[Int] = {
    val outH = (H + 2 * pH - dH * (kH - 1) - 1) / sH + 1
    val outW = (W + 2 * pW - dW * (kW - 1) - 1) / sW + 1
    val result = mutable.ArrayBuffer[Int]()
    for (oh <- 0 until outH; ow <- 0 until outW;
         c <- 0 until C; kh <- 0 until kH; kw <- 0 until kW) {
      val ih = oh * sH + kh * dH - pH
      val iw = ow * sW + kw * dW - pW
      if (ih < 0 || ih >= H || iw < 0 || iw >= W) result += 0
      else result += featureMap(c * H * W + ih * W + iw)
    }
    result.toSeq
  }

  /** 将 2 字节元素序列写入仿真内存（LE） */
  private def writeElems16(mem: mutable.HashMap[BigInt, Byte], base: BigInt, elems: Seq[Int]): Unit =
    for ((v, i) <- elems.zipWithIndex) {
      mem(base + i * 2) = (v & 0xFF).toByte
      mem(base + i * 2 + 1) = ((v >> 8) & 0xFF).toByte
    }

  /** 从仿真内存读取 16-bit 元素 */
  private def readElem16(mem: mutable.HashMap[BigInt, Byte], addr: BigInt): Int = {
    val lo = mem.getOrElse(addr, 0.toByte) & 0xFF
    val hi = mem.getOrElse(addr + 1, 0.toByte) & 0xFF
    lo | (hi << 8)
  }

  /** 构造 im2col FbTestCmd
    * shape(0) = srcBboxBytes, shape(1) = dstBboxBytes（用于冲突检测 bbox） */
  private def mkIm2colCmd(
      tag: Int, C: Int, H: Int, W: Int,
      kH: Int, kW: Int, sH: Int, sW: Int,
      pH: Int, pW: Int, dH: Int, dW: Int,
      elemBytesLog2: Int, srcAddr: BigInt, dstAddr: BigInt
  ): FbTestCmd = {
    val elemBytes = 1 << elemBytesLog2
    val outH = (H + 2 * pH - dH * (kH - 1) - 1) / sH + 1
    val outW = (W + 2 * pW - dW * (kW - 1) - 1) / sW + 1
    val srcBbox = C * H * W * elemBytes
    val dstBbox = C * kH * kW * outH * outW * elemBytes
    FbTestCmd(
      tag = tag, op = "IM2COL", dimCount = 1,
      shape = Seq(srcBbox, dstBbox),
      srcStride = Seq(srcBbox), dstStride = Seq(dstBbox),
      srcAddr = srcAddr, dstAddr = dstAddr,
      elemBytesLog2 = elemBytesLog2,
      im2colKernel = Seq(kH, kW),
      im2colStride = Seq(sH, sW),
      im2colPad = Seq(pH, pW),
      im2colDilation = Seq(dH, dW),
      im2colInShape = Seq(C, H, W)
    )
  }

  it should "im2col: basic 1x5x5 kernel3x3 stride1 no padding" in {
    val iC = 1; val iH = 5; val iW = 5
    val (kH, kW, sH, sW, pH, pW, dH, dW) = (3, 3, 1, 1, 0, 0, 1, 1)
    val elemBytesLog2 = 1
    val featureMap = (0 until iC * iH * iW).toSeq
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x10, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, elemBytesLog2,
      srcAddr = 0, dstAddr = 0x10000)
    val (dones, m) = runForagerBee(i2cCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x10, false))
    for (i <- expected.indices) {
      val actual = readElem16(m, BigInt(0x10000 + i * 2))
      withClue(s"im2col[${i}] (expected ${expected(i)}, got $actual): ") {
        actual shouldBe expected(i)
      }
    }
  }

  it should "im2col: vertical padding 1x4x6 kernel3x3 stride1 pH=1 pW=0" in {
    val iC = 1; val iH = 4; val iW = 6
    val (kH, kW, sH, sW, pH, pW, dH, dW) = (3, 3, 1, 1, 1, 0, 1, 1)
    val elemBytesLog2 = 1
    val featureMap = (0 until iC * iH * iW).map(i => (i * 7 + 3) & 0xFFFF)
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x11, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, elemBytesLog2,
      srcAddr = 0, dstAddr = 0x10000)
    val (dones, m) = runForagerBee(i2cCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x11, false))
    // 验证边界零填充行和数据行
    for (i <- expected.indices) {
      val actual = readElem16(m, BigInt(0x10000 + i * 2))
      withClue(s"im2col[${i}] (expected ${expected(i)}, got $actual): ") {
        actual shouldBe expected(i)
      }
    }
  }

  it should "im2col: stride2 1x8x8 kernel3x3" in {
    val iC = 1; val iH = 8; val iW = 8
    val (kH, kW, sH, sW, pH, pW, dH, dW) = (3, 3, 2, 2, 0, 0, 1, 1)
    val elemBytesLog2 = 1
    val featureMap = (0 until iC * iH * iW).map(i => (i * 13 + 5) & 0xFFFF)
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x12, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, elemBytesLog2,
      srcAddr = 0, dstAddr = 0x10000)
    val (dones, m) = runForagerBee(i2cCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x12, false))
    for (i <- expected.indices) {
      val actual = readElem16(m, BigInt(0x10000 + i * 2))
      withClue(s"im2col[${i}] (expected ${expected(i)}, got $actual): ") {
        actual shouldBe expected(i)
      }
    }
  }

  it should "im2col: multi-channel C=3 H=6 W=6 kernel3x3 stride1" in {
    val iC = 3; val iH = 6; val iW = 6
    val (kH, kW, sH, sW, pH, pW, dH, dW) = (3, 3, 1, 1, 0, 0, 1, 1)
    val elemBytesLog2 = 1
    val featureMap = (0 until iC * iH * iW).map(i => (i * 11 + 7) & 0xFFFF)
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x13, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, elemBytesLog2,
      srcAddr = 0, dstAddr = 0x10000)
    val (dones, m) = runForagerBee(i2cCfg, Seq(cmd), mem, maxCycles = 100000)
    dones shouldBe Seq((0x13, false))
    for (i <- expected.indices) {
      val actual = readElem16(m, BigInt(0x10000 + i * 2))
      withClue(s"im2col[${i}] (expected ${expected(i)}, got $actual): ") {
        actual shouldBe expected(i)
      }
    }
  }

  it should "im2col: reject when channel does not support im2col" in {
    val noI2cCfg = ForagerBeeConfig(
      numPorts = 1, queueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 64,
      dataWidth = 32, addressWidth = 32,
      channelIm2col = Seq(false)
    )
    val cmd = mkIm2colCmd(0x14, 1, 4, 4, 3, 3, 1, 1, 0, 0, 1, 1, 1,
      srcAddr = 0, dstAddr = 0x10000)
    val (dones, _) = runForagerBee(noI2cCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x14, true))
  }

  it should "im2col: reject kernel=0 with err" in {
    val cmd = FbTestCmd(
      tag = 0x15, op = "IM2COL", dimCount = 1,
      shape = Seq(32, 32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 1,
      im2colKernel = Seq(0, 3), im2colStride = Seq(1, 1),
      im2colPad = Seq(0, 0), im2colDilation = Seq(1, 1),
      im2colInShape = Seq(1, 4, 4)
    )
    val (dones, _) = runForagerBee(i2cCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x15, true))
  }

  // ===================== SCATTER/GATHER 用例 =====================

  private val sgCfg = ForagerBeeConfig(
    numPorts = 1, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 64,
    dataWidth = 128, addressWidth = 32,
    channelScatterGather = Seq(true)
  )
  private val sgBB = sgCfg.beatBytes // 16

  /** 将一个 scatter/gather 描述符条目写入内存（addr: 低 32bit, len: bits[47:32]） */
  private def writeSgEntry(mem: mutable.HashMap[BigInt, Byte], entryBase: BigInt, addr: BigInt, len: Int): Unit = {
    // 低 4 字节 = addr (little-endian)
    for (b <- 0 until 4) mem(entryBase + b) = ((addr >> (8 * b)) & 0xFF).toByte
    // 接下来 2 字节 = len (bits 32..47)
    mem(entryBase + 4) = (len & 0xFF).toByte
    mem(entryBase + 5) = ((len >> 8) & 0xFF).toByte
    // 高位填 0（bytes 6..15）
    for (b <- 6 until sgBB) mem(entryBase + b) = 0.toByte
  }

  it should "GATHER basic: 3 non-contiguous blocks gathered to linear destination" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val blockLen = 32 // 2 beats each
    // 在 3 个不连续位置准备源数据
    fillPattern(mem, 0x1000, blockLen, seed = 10)
    fillPattern(mem, 0x2000, blockLen, seed = 20)
    fillPattern(mem, 0x3000, blockLen, seed = 30)
    // 准备描述符表（3 条目）at 0x5000
    val listAddr = BigInt(0x5000)
    writeSgEntry(mem, listAddr + 0 * sgBB, BigInt(0x1000), blockLen)
    writeSgEntry(mem, listAddr + 1 * sgBB, BigInt(0x2000), blockLen)
    writeSgEntry(mem, listAddr + 2 * sgBB, BigInt(0x3000), blockLen)
    // 命令：GATHER，dstAddr=0x8000，total=96B
    val totalBytes = blockLen * 3
    val cmd = FbTestCmd(tag = 0xA0, op = "GATHER", dimCount = 1,
      shape = Seq(totalBytes), srcStride = Seq(totalBytes), dstStride = Seq(totalBytes),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0,
      sgListAddr = listAddr, sgEntryCount = 3)
    val (dones, m) = runForagerBee(sgCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xA0, false))
    // 验证 dst = block0 ++ block1 ++ block2
    for (i <- 0 until blockLen)
      m(BigInt(0x8000) + i) shouldBe (((i * 7 + 10 * 31 + 3) & 0xFF).toByte)
    for (i <- 0 until blockLen)
      m(BigInt(0x8000) + blockLen + i) shouldBe (((i * 7 + 20 * 31 + 3) & 0xFF).toByte)
    for (i <- 0 until blockLen)
      m(BigInt(0x8000) + 2 * blockLen + i) shouldBe (((i * 7 + 30 * 31 + 3) & 0xFF).toByte)
  }

  it should "SCATTER basic: linear source scattered to 3 non-contiguous destinations" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val blockLen = 32 // 2 beats each
    val totalBytes = blockLen * 3
    // 准备连续源数据 at 0x1000
    fillPattern(mem, 0x1000, totalBytes, seed = 42)
    // 准备描述符表（3 条目）at 0x5000
    val listAddr = BigInt(0x5000)
    writeSgEntry(mem, listAddr + 0 * sgBB, BigInt(0x8000), blockLen)
    writeSgEntry(mem, listAddr + 1 * sgBB, BigInt(0x9000), blockLen)
    writeSgEntry(mem, listAddr + 2 * sgBB, BigInt(0xA000), blockLen)
    // 命令：SCATTER，srcAddr=0x1000
    val cmd = FbTestCmd(tag = 0xA1, op = "SCATTER", dimCount = 1,
      shape = Seq(totalBytes), srcStride = Seq(totalBytes), dstStride = Seq(totalBytes),
      srcAddr = 0x1000, dstAddr = 0, elemBytesLog2 = 0,
      sgListAddr = listAddr, sgEntryCount = 3)
    val (dones, m) = runForagerBee(sgCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xA1, false))
    // 验证 3 个目的地址处各有正确的 32B 数据
    for (i <- 0 until blockLen)
      m(BigInt(0x8000) + i) shouldBe (((i * 7 + 42 * 31 + 3) & 0xFF).toByte)
    for (i <- 0 until blockLen)
      m(BigInt(0x9000) + i) shouldBe ((((blockLen + i) * 7 + 42 * 31 + 3) & 0xFF).toByte)
    for (i <- 0 until blockLen)
      m(BigInt(0xA000) + i) shouldBe ((((2 * blockLen + i) * 7 + 42 * 31 + 3) & 0xFF).toByte)
  }

  it should "GATHER variable-length entries (16B + 64B + 32B = 112B)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 3 个不等长源块
    val len0 = 16; val len1 = 64; val len2 = 32
    val totalBytes = len0 + len1 + len2 // 112
    fillPattern(mem, 0x1000, len0, seed = 1)
    fillPattern(mem, 0x2000, len1, seed = 2)
    fillPattern(mem, 0x3000, len2, seed = 3)
    // 描述符表 at 0x5000
    val listAddr = BigInt(0x5000)
    writeSgEntry(mem, listAddr + 0 * sgBB, BigInt(0x1000), len0)
    writeSgEntry(mem, listAddr + 1 * sgBB, BigInt(0x2000), len1)
    writeSgEntry(mem, listAddr + 2 * sgBB, BigInt(0x3000), len2)
    // 命令：shape(0) 必须是 beatBytes 整数倍 → 112 % 16 = 0 ✓
    val cmd = FbTestCmd(tag = 0xA2, op = "GATHER", dimCount = 1,
      shape = Seq(totalBytes), srcStride = Seq(totalBytes), dstStride = Seq(totalBytes),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0,
      sgListAddr = listAddr, sgEntryCount = 3)
    val (dones, m) = runForagerBee(sgCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xA2, false))
    // 验证 dst = block0(16B) ++ block1(64B) ++ block2(32B)
    var off = 0
    for (i <- 0 until len0) {
      m(BigInt(0x8000) + off + i) shouldBe (((i * 7 + 1 * 31 + 3) & 0xFF).toByte)
    }
    off += len0
    for (i <- 0 until len1) {
      m(BigInt(0x8000) + off + i) shouldBe (((i * 7 + 2 * 31 + 3) & 0xFF).toByte)
    }
    off += len1
    for (i <- 0 until len2) {
      m(BigInt(0x8000) + off + i) shouldBe (((i * 7 + 3 * 31 + 3) & 0xFF).toByte)
    }
  }

  it should "SCATTER rejected when channelScatterGather=false → done.err=true" in {
    val noSgCfg = ForagerBeeConfig(
      numPorts = 1, queueDepth = 4, maxDims = 4,
      tileSize = 8, maxBurstLen = 64,
      dataWidth = 128, addressWidth = 32,
      channelScatterGather = Seq(false)
    )
    val cmd = FbTestCmd(tag = 0xA3, op = "SCATTER", dimCount = 1,
      shape = Seq(96), srcStride = Seq(96), dstStride = Seq(96),
      srcAddr = 0, dstAddr = 0, elemBytesLog2 = 0,
      sgListAddr = BigInt(0x5000), sgEntryCount = 3)
    val (dones, _) = runForagerBee(noSgCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xA3, true))
  }

  it should "GATHER rejected when sgEntryCount=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0xA4, op = "GATHER", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x8000, elemBytesLog2 = 0,
      sgListAddr = BigInt(0x5000), sgEntryCount = 0)
    val (dones, _) = runForagerBee(sgCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xA4, true))
  }

  // ===================== sub-block 裁剪 (srcStartIdx) 用例 =====================

  it should "2D sub-block crop: extract 3 rows × 16 cols from 32×8 matrix" in {
    // 源：32×8 矩阵（行主序，1 byte/elem，行 stride=32）
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until 8; c <- 0 until 32) {
      mem(BigInt(r * 32 + c)) = ((r * 32 + c) & 0xFF).toByte
    }
    // 从 (row=2, col=4) 开始取 3 行 × 16 列子块
    // cRowBytes = shape(0) * elemBytes = 16 * 1 = 16 ≡ 0 mod 16 ✔
    val cmd = FbTestCmd(tag = 0xB1, op = "COPY", dimCount = 2,
      shape = Seq(16, 3),                      // 16 cols × 3 rows
      srcStride = Seq(16, 32),                 // dim0=16 bytes read, dim1=32 byte/row stride
      dstStride = Seq(16, 16),                 // dst 紧凑：16 bytes/row
      srcAddr = 0, dstAddr = 0x2000,
      elemBytesLog2 = 0,
      srcStartIdx = Seq(4, 2, 0, 0)            // col=4, row=2
    )
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xB1, false))
    // 验证：dst 应包含 src[row+2][col+4..col+19]
    for (r <- 0 until 3; c <- 0 until 16) {
      val expected = (((r + 2) * 32 + (c + 4)) & 0xFF).toByte
      m(BigInt(0x2000 + r * 16 + c)) shouldBe expected
    }
  }

  it should "2D sub-block crop with 4-byte elements (FP32)" in {
    // 源：8×4 矩阵，FP32 (4 bytes/elem)，行 stride = 8*4 = 32 bytes
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until 4; c <- 0 until 8) {
      val v = (r * 8 + c) * 0x01010101 // 用重复字节模式填充便于验证
      for (b <- 0 until 4) {
        mem(BigInt(r * 32 + c * 4 + b)) = ((v >> (b * 8)) & 0xFF).toByte
      }
    }
    // 从 (row=1, col=2) 取 4 cols × 2 rows 子块
    // cRowBytes = shape(0) * elemBytes = 4 * 4 = 16 ≡ 0 mod 16 ✔
    val cmd = FbTestCmd(tag = 0xB2, op = "COPY", dimCount = 2,
      shape = Seq(4, 2),                       // 4 cols × 2 rows
      srcStride = Seq(16, 32),                 // dim0=4*4=16 bytes read, dim1=32 byte/row stride
      dstStride = Seq(16, 16),                 // dst 紧凑：4 elems * 4 bytes = 16 bytes/row
      srcAddr = 0, dstAddr = 0x3000,
      elemBytesLog2 = 2,                       // 4 bytes/elem
      srcStartIdx = Seq(2, 1, 0, 0)           // col=2, row=1
    )
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xB2, false))
    // 验证：dst 应包含 row1..2, col2..5 的 FP32 数据
    for (r <- 0 until 2; c <- 0 until 4) {
      val srcR = r + 1; val srcC = c + 2
      val v = (srcR * 8 + srcC) * 0x01010101
      for (b <- 0 until 4) {
        m(BigInt(0x3000 + r * 16 + c * 4 + b)) shouldBe ((v >> (b * 8)) & 0xFF).toByte
      }
    }
  }

  it should "3D sub-block crop: extract 16×2×2 from 32×4×4 tensor" in {
    // 源：32×4×4 张量，1 byte/elem，总 512 字节
    // stride: dim1=32 bytes (one row), dim2=128 bytes (one plane)
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (d2 <- 0 until 4; d1 <- 0 until 4; d0 <- 0 until 32) {
      mem(BigInt(d2 * 128 + d1 * 32 + d0)) = ((d2 * 128 + d1 * 32 + d0) & 0xFF).toByte
    }
    // 从 (d0=2, d1=1, d2=1) 取 16×2×2 子块
    // cRowBytes = shape(0) * 1 = 16 ≡ 0 mod 16 ✔
    val cmd = FbTestCmd(tag = 0xB3, op = "COPY", dimCount = 3,
      shape = Seq(16, 2, 2),                   // 16×2×2
      srcStride = Seq(16, 32, 128),            // dim0=16 bytes read, dim1=32 stride, dim2=128 stride
      dstStride = Seq(16, 16, 32),             // dst 紧凑：16 per row, 2 rows per plane
      srcAddr = 0, dstAddr = 0x4000,
      elemBytesLog2 = 0,
      srcStartIdx = Seq(2, 1, 1, 0)            // d0从2开始, d1从1, d2从1
    )
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xB3, false))
    // 验证
    for (d2 <- 0 until 2; d1 <- 0 until 2; d0 <- 0 until 16) {
      val srcByte = (d2 + 1) * 128 + (d1 + 1) * 32 + (d0 + 2)
      val expected = (srcByte & 0xFF).toByte
      m(BigInt(0x4000 + d2 * 32 + d1 * 16 + d0)) shouldBe expected
    }
  }

  it should "srcStartIdx all-zero fallback: identical to plain 2D strided copy" in {
    // 与现有 2D strided copy 相同参数，显式设置 srcStartIdx 全 0
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val cols = 32; val rows = 4
    for (r <- 0 until rows; c <- 0 until cols) {
      mem(BigInt(r * 64 + c)) = ((r * cols + c) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0xB4, op = "COPY", dimCount = 2,
      shape = Seq(cols, rows),
      srcStride = Seq(cols, 64),
      dstStride = Seq(cols, cols),
      srcAddr = 0, dstAddr = 0x2000,
      elemBytesLog2 = 0,
      srcStartIdx = Seq(0, 0, 0, 0)            // 显式全 0
    )
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xB4, false))
    for (r <- 0 until rows; c <- 0 until cols) {
      m(BigInt(0x2000 + r * cols + c)) shouldBe (((r * cols + c) & 0xFF).toByte)
    }
  }

  it should "TRANSPOSE + srcStartIdx: transpose 2×2 sub-block from 4×4 matrix" in {
    // 源：4×4 矩阵，1 byte/elem
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until 4; c <- 0 until 4) {
      mem(BigInt(r * 16 + c)) = ((r * 4 + c) & 0xFF).toByte
    }
    // 从 (row=1, col=1) 取 2×2 子块并转置
    // TRANSPOSE 模式：shape(0)=M(cols), shape(1)=N(rows)
    // srcStride(1) = row stride in bytes
    val cmd = FbTestCmd(tag = 0xB5, op = "TRANSPOSE", dimCount = 2,
      shape = Seq(2, 2),                       // M=2 cols, N=2 rows
      srcStride = Seq(2, 16),                  // dim0 unused info, dim1=16 byte row stride
      dstStride = Seq(2, 0),                   // dstStride(0)=转置输出行步长(N=2 bytes), (1) ignored
      srcAddr = 0, dstAddr = 0x5000,
      elemBytesLog2 = 0,
      srcStartIdx = Seq(1, 1, 0, 0, 0)            // col=1, row=1
    )
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xB5, false))
    // 源子块 (row 1..2, col 1..2):
    //   src[1][1]=5, src[1][2]=6
    //   src[2][1]=9, src[2][2]=10
    // 转置后 dst[col][row]:
    //   dst[0][0]=5, dst[0][1]=9   (col=1 → dst row 0)
    //   dst[1][0]=6, dst[1][1]=10  (col=2 → dst row 1)
    m(BigInt(0x5000 + 0)) shouldBe 5.toByte   // dst[0][0] = src[1][1]
    m(BigInt(0x5000 + 1)) shouldBe 9.toByte   // dst[0][1] = src[2][1]
    m(BigInt(0x5000 + 2)) shouldBe 6.toByte   // dst[1][0] = src[1][2]
    m(BigInt(0x5000 + 3)) shouldBe 10.toByte  // dst[1][1] = src[2][2]
  }

  // ===================== TILE2LINEAR 用例 =====================

  /** TILE2LINEAR 参考模型：2D M×N 行主序矩阵，按 Tm×Tn tile 切块后
    * 以 tile-row-major 顺序展平为线性输出。
    * 返回：输出字节序列的预期值 (dst offset → byte value)
    */
  private def tile2linearRef(
      srcMem: mutable.HashMap[BigInt, Byte],
      srcBase: BigInt,
      M: Int, N: Int, Tm: Int, Tn: Int, elemBytes: Int
  ): Seq[Byte] = {
    val Mt = M / Tm
    val Nt = N / Tn
    val result = mutable.ArrayBuffer[Byte]()
    // 遍历顺序（外→内）：tile-row(tr) → tile-col(tc) → intra-row(ir) → intra-col(ic)
    for (tr <- 0 until Mt; tc <- 0 until Nt; ir <- 0 until Tm; ic <- 0 until Tn) {
      val row = tr * Tm + ir
      val col = tc * Tn + ic
      val srcAddr = srcBase + (row * N + col).toLong * elemBytes
      for (b <- 0 until elemBytes) {
        result += srcMem.getOrElse(srcAddr + b, 0.toByte)
      }
    }
    result.toSeq
  }

  it should "TILE2LINEAR basic: 32×32 matrix, Tm=Tn=16, 1-byte elements" in {
    val M = 32; val N = 32; val Tm = 16; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 2, 2
    val elemBytes = 1; val elemBytesLog2 = 0
    // Tn * e = 16 ≡ 0 mod 16(beatBytes) ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = ((r * N + c) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x71, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = elemBytesLog2,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x71, false))
    // 逐元素比对参考模型
    val expected = tile2linearRef(mem, BigInt(0), M, N, Tm, Tn, elemBytes)
    for (i <- expected.indices) {
      withClue(s"tile2linear[$i]: ") {
        m(BigInt(0x10000) + i) shouldBe expected(i)
      }
    }
  }

  it should "TILE2LINEAR asymmetric tile: M=48, N=32, Tm=8, Tn=16 (Tm≠Tn, Mt≠Nt)" in {
    val M = 48; val N = 32; val Tm = 8; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 6, 2
    val elemBytes = 1; val elemBytesLog2 = 0
    // Tn * e = 16 ≡ 0 mod 16 ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = (((r * 7 + c * 13 + 5) * 3) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x72, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = elemBytesLog2,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x72, false))
    val expected = tile2linearRef(mem, BigInt(0), M, N, Tm, Tn, elemBytes)
    for (i <- expected.indices) {
      withClue(s"tile2linear asymmetric[$i]: ") {
        m(BigInt(0x10000) + i) shouldBe expected(i)
      }
    }
  }

  it should "TILE2LINEAR illegal: Tn=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0x73, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 0,
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 0,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x73, true))
  }

  it should "TILE2LINEAR illegal: Mt=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0x74, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 0,
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 16,
      t2lNumTileRows = 0, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x74, true))
  }

  it should "TILE2LINEAR beat alignment violation: Tn*e not divisible by beatBytes → err" in {
    // Tn=3, elemBytes=4 → dim0 row bytes = 3*4=12, not divisible by 16 → cBad → err
    val cmd = FbTestCmd(tag = 0x75, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 2,
      t2lMatCols = 12, t2lTileRows = 4, t2lTileCols = 3,
      t2lNumTileRows = 2, t2lNumTileCols = 4)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x75, true))
  }

  it should "5D COPY: dimCount=5 strided tensor (maxDims=5 extension)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val d0 = 16; val d1 = 2; val d2 = 2; val d3 = 2; val d4 = 2
    // 源：5D 张量，各维 stride 不紧凑
    for (i4 <- 0 until d4; i3 <- 0 until d3; i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0) {
      val off = i4 * 512 + i3 * 192 + i2 * 80 + i1 * 32 + c
      mem(BigInt(off)) = ((i4 * 113 + i3 * 71 + i2 * 37 + i1 * 19 + c * 3 + 7) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x5D, op = "COPY", dimCount = 5,
      shape = Seq(d0, d1, d2, d3, d4),
      srcStride = Seq(d0, 32, 80, 192, 512),
      dstStride = Seq(d0, d0, d0 * d1, d0 * d1 * d2, d0 * d1 * d2 * d3),
      srcAddr = 0, dstAddr = 0x8000)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x5D, false))
    // 验证目的端为紧凑排列
    for (i4 <- 0 until d4; i3 <- 0 until d3; i2 <- 0 until d2; i1 <- 0 until d1; c <- 0 until d0) {
      val srcOff = i4 * 512 + i3 * 192 + i2 * 80 + i1 * 32 + c
      val dstOff = i4 * (d0 * d1 * d2 * d3) + i3 * (d0 * d1 * d2) + i2 * (d0 * d1) + i1 * d0 + c
      withClue(s"5D COPY[$i4][$i3][$i2][$i1][$c]: ") {
        m(BigInt(0x8000) + dstOff) shouldBe mem(BigInt(srcOff))
      }
    }
  }

  it should "TILE2LINEAR mixed with COPY and TRANSPOSE: dispatcher conflict detection" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 准备 TILE2LINEAR 源数据：16×16 矩阵
    val M = 16; val N = 16; val Tm = 8; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 2, 1
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = ((r * N + c) & 0xFF).toByte
    }
    // 准备 COPY 源数据
    fillPattern(mem, 0x5000, 64, seed = 55)
    // 准备 TRANSPOSE 源数据：8×8 matrix at 0x8000
    for (i <- 0 until 8; j <- 0 until 8) {
      mem(BigInt(0x8000 + i * 16 + j)) = ((i * 8 + j) & 0xFF).toByte
    }
    val cmds = Seq(
      // TILE2LINEAR: 16×16 → linear (Tn*e=16 ≡ 0 mod 16 ✓)
      FbTestCmd(tag = 0x76, op = "TILE2LINEAR", dimCount = 1,
        shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
        srcAddr = 0, dstAddr = 0x20000, elemBytesLog2 = 0,
        t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
        t2lNumTileRows = Mt, t2lNumTileCols = Nt),
      // COPY: 64 bytes non-conflicting
      FbTestCmd(tag = 0x77, op = "COPY", dimCount = 1,
        shape = Seq(64), srcStride = Seq(64), dstStride = Seq(64),
        srcAddr = 0x5000, dstAddr = 0x6000),
      // TRANSPOSE: 8×8 INT8 non-conflicting
      FbTestCmd(tag = 0x78, op = "TRANSPOSE", dimCount = 2,
        shape = Seq(8, 8), srcStride = Seq(8, 16), dstStride = Seq(8, 0),
        srcAddr = 0x8000, dstAddr = 0x30000, elemBytesLog2 = 0)
    )
    val (dones, m) = runForagerBee(testCfg, cmds, mem, maxCycles = 50000)
    dones.map(_._1).toSet shouldBe Set(0x76, 0x77, 0x78)
    dones.forall(!_._2) shouldBe true
    // 验证 TILE2LINEAR 输出
    val expected = tile2linearRef(mem, BigInt(0), M, N, Tm, Tn, 1)
    for (i <- expected.indices) {
      withClue(s"mixed tile2linear[$i]: ") {
        m(BigInt(0x20000) + i) shouldBe expected(i)
      }
    }
    // 验证 COPY
    for (i <- 0 until 64) m(BigInt(0x6000) + i) shouldBe mem(BigInt(0x5000) + i)
    // 验证 TRANSPOSE
    for (j <- 0 until 8; i <- 0 until 8) {
      m(BigInt(0x30000 + j * 8 + i)) shouldBe (((i * 8 + j) & 0xFF).toByte)
    }
  }

  // ===================== LINEAR2TILE 用例 =====================

  /** LINEAR2TILE 参考模型：从线性 tile-row-major 缓冲读出，按 tile 布局写回 2D M×N 行主序大矩阵。
    * 返回：输出字节序列的预期值（按行主序排列的 M×N 矩阵字节）
    */
  private def linear2tileRef(
      srcMem: mutable.HashMap[BigInt, Byte],
      srcBase: BigInt,
      M: Int, N: Int, Tm: Int, Tn: Int, elemBytes: Int
  ): mutable.HashMap[BigInt, Byte] = {
    val Mt = M / Tm
    val Nt = N / Tn
    val result = mutable.HashMap.empty[BigInt, Byte]
    var linearOff = 0
    // 遍历顺序（外→内）：tile-row(tr) → tile-col(tc) → intra-row(ir) → intra-col(ic)
    // 与 tile2linearRef 相同的遍历顺序，但方向相反：从线性源读、写到行主序目标
    for (tr <- 0 until Mt; tc <- 0 until Nt; ir <- 0 until Tm; ic <- 0 until Tn) {
      val row = tr * Tm + ir
      val col = tc * Tn + ic
      for (b <- 0 until elemBytes) {
        val srcByte = srcMem.getOrElse(srcBase + linearOff, 0.toByte)
        result(BigInt((row * N + col).toLong * elemBytes + b)) = srcByte
        linearOff += 1
      }
    }
    result
  }

  it should "LINEAR2TILE basic: 32×32 matrix, Tm=Tn=16, 1-byte elements" in {
    val M = 32; val N = 32; val Tm = 16; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 2, 2
    val elemBytes = 1; val elemBytesLog2 = 0
    // Tn * e = 16 ≡ 0 mod 16(beatBytes) ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 源：线性 tile-row-major 缓冲（M*N*elemBytes 字节）
    val totalBytes = M * N * elemBytes
    for (i <- 0 until totalBytes) {
      mem(BigInt(i)) = ((i * 7 + 13) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x81, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = elemBytesLog2,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x81, false))
    // 逐元素比对参考模型
    val expected = linear2tileRef(mem, BigInt(0), M, N, Tm, Tn, elemBytes)
    for ((offset, expByte) <- expected) {
      withClue(s"linear2tile[offset=$offset]: ") {
        m(BigInt(0x10000) + offset) shouldBe expByte
      }
    }
  }

  it should "LINEAR2TILE asymmetric tile: M=48, N=32, Tm=8, Tn=16 (Tm≠Tn, Mt≠Nt)" in {
    val M = 48; val N = 32; val Tm = 8; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 6, 2
    val elemBytes = 1; val elemBytesLog2 = 0
    // Tn * e = 16 ≡ 0 mod 16 ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val totalBytes = M * N * elemBytes
    for (i <- 0 until totalBytes) {
      mem(BigInt(i)) = (((i * 13 + 7) * 3) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x82, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = elemBytesLog2,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0x82, false))
    val expected = linear2tileRef(mem, BigInt(0), M, N, Tm, Tn, elemBytes)
    for ((offset, expByte) <- expected) {
      withClue(s"linear2tile asymmetric[offset=$offset]: ") {
        m(BigInt(0x10000) + offset) shouldBe expByte
      }
    }
  }

  it should "LINEAR2TILE illegal: Tn=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0x83, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 0,
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 0,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x83, true))
  }

  it should "LINEAR2TILE illegal: N=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0x84, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 0,
      t2lMatCols = 0, t2lTileRows = 16, t2lTileCols = 16,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x84, true))
  }

  it should "LINEAR2TILE beat alignment violation: Tn*e not divisible by beatBytes → err" in {
    // Tn=3, elemBytes=4 → dim0 row bytes = 3*4=12, not divisible by 16 → err
    val cmd = FbTestCmd(tag = 0x85, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = 2,
      t2lMatCols = 12, t2lTileRows = 4, t2lTileCols = 3,
      t2lNumTileRows = 2, t2lNumTileCols = 4)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x85, true))
  }

  it should "LINEAR2TILE round-trip: TILE2LINEAR then LINEAR2TILE restores original matrix" in {
    val M = 32; val N = 32; val Tm = 16; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 2, 2
    val elemBytes = 1; val elemBytesLog2 = 0
    // Tn * e = 16 ≡ 0 mod 16 ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 准备行主序矩阵 A at 0x0000
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = ((r * N + c + 17) & 0xFF).toByte
    }
    // 第一步：TILE2LINEAR（src=0x0000 行主序矩阵 → dst=0x10000 线性缓冲）
    val cmd1 = FbTestCmd(tag = 0x86, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, elemBytesLog2 = elemBytesLog2,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones1, m1) = runForagerBee(testCfg, Seq(cmd1), mem, maxCycles = 50000)
    dones1 shouldBe Seq((0x86, false))
    // 第二步：LINEAR2TILE（src=0x10000 线性缓冲 → dst=0x20000 行主序矩阵）
    val cmd2 = FbTestCmd(tag = 0x87, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0x10000, dstAddr = 0x20000, elemBytesLog2 = elemBytesLog2,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones2, m2) = runForagerBee(testCfg, Seq(cmd2), m1, maxCycles = 50000)
    dones2 shouldBe Seq((0x87, false))
    // 验证 A' == A（原始矩阵 at 0x0000 vs 还原矩阵 at 0x20000）
    for (r <- 0 until M; c <- 0 until N) {
      val off = r * N + c
      withClue(s"round-trip[$r][$c]: ") {
        m2(BigInt(0x20000) + off) shouldBe m2(BigInt(off))
      }
    }
  }
}

