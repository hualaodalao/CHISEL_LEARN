/** ForagerBee 测试：elaboration 冒烟 + EphemeralSimulator 功能仿真。
  *
  * 功能仿真方案：
  *   - 测试内自带行为级 FbStreamBus slave 内存模型 [[FbStreamMemSlave]]：
  *     Scala 侧 HashMap[BigInt, Byte] 共享内存（多端口为同一内存的不同视图），
  *     每拍 peek wr/rd 通道、poke rdData/wrResp 通道；写响应为保序队列，
  *     支持多 outstanding 写突发（与 writeOutstandingDepth>1 的非阻塞写匹配；
  *     默认 writeDelay=1 时行为与单 outstanding 旧实现逐拍一致），
  *     可编程响应延迟（默认各 1 拍），可选对指定突发注入响应错误。
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

/** 行为级 FbStreamBus slave 内存模型（多 outstanding 写响应队列、字节粒度 mem）。
  *
  * 端口方向（从 DUT master 视角）：
  *   - bus.rd:     DUT 驱动 valid/payload（读请求），test 驱动 ready
  *   - bus.rdData: test 驱动 valid/payload（读数据），DUT 驱动 ready
  *   - bus.wr:     DUT 驱动 valid/payload（写数据），test 驱动 ready
  *   - bus.wrResp: test 驱动 valid/payload（写响应），DUT 驱动 ready
  *
  * @param errOnBurst 对第 N 个写突发（按 EOF 接收顺序，0 起）的响应置 err=true；-1 不注入
  */
class FbStreamMemSlave(
    mem: mutable.HashMap[BigInt, Byte],
    beatBytes: Int,
    readDelay: Int = 1,
    writeDelay: Int = 1,
    errOnBurst: Int = -1
) {
  // --- 读状态 ---
  private var rdActive = false
  private var rdAddr = BigInt(0)
  private var rdRemain = 0
  private var rdBeatIdx = 0
  private var rdWait = 0

  // --- 写状态 ---
  private var wrAddr = BigInt(0)
  // 写响应队列：每个写突发（eof && fire）入队一个 (延迟倒计时, err 标志)；
  // 队首为当前驱动的响应，按入队顺序发出（天然保序）。倒计时语义与旧单
  // outstanding 实现一致：EOF 拍入队 max(1, writeDelay)，每拍 >0 递减，
  // 归 0 后的下一拍起驱动 valid → 默认 writeDelay=1 时逐拍行为与旧实现完全一致。
  private val bQueue = mutable.ListBuffer[(Int, Boolean)]()
  private var eofCount = 0   // 已接受的写突发数（EOF 序号，供 errOnBurst 匹配）
  var wrRespSent = 0         // 已握手发出的写响应数（供 done 排空校验）

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
        bQueue += ((math.max(1, writeDelay), eofCount == errOnBurst))
        eofCount += 1
      }
    }

    // === wrResp 通道（test slave 驱动 valid/payload，DUT master 驱动 ready）===
    if (bQueue.nonEmpty && bQueue.head._1 <= 0) {
      // 队首倒计时归 0：驱动响应；fire 后出队处理下一个（保序）
      bus.wrResp.valid.poke(true.B)
      bus.wrResp.payload.err.poke(bQueue.head._2.B)
      if (bus.wrResp.ready.peek().litToBoolean) {
        bQueue.remove(0)
        wrRespSent += 1
      }
    } else {
      bus.wrResp.valid.poke(false.B)
      bus.wrResp.payload.err.poke(false.B)
      if (bQueue.nonEmpty) {
        val (w, e) = bQueue.head
        bQueue(0) = (w - 1, e)
      }
    }
  }
}

/** 带写突发计数的 [[FbStreamMemSlave]]：统计写通道接受的突发数（SOF 拍），
  * 用于验证写端口共享仲裁的端口利用率（哪个物理端口承载了多少突发）。
  */
class FbWrCountingMemSlave(
    mem: mutable.HashMap[BigInt, Byte],
    beatBytes: Int,
    readDelay: Int = 1,
    writeDelay: Int = 1,
    errOnBurst: Int = -1
) extends FbStreamMemSlave(mem, beatBytes, readDelay, writeDelay, errOnBurst) {
  var wrBursts = 0
  override def step(bus: FbStreamBus): Unit = {
    super.step(bus) // super 中 wr.ready 恒为 true，valid&&sof 即一次突发握手
    if (bus.wr.valid.peek().litToBoolean && bus.wr.payload.sof.peek().litToBoolean) {
      wrBursts += 1
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
    permVec: Seq[Int] = Seq(),
    padBefore: Seq[Int] = Seq(0, 0),
    padAfter: Seq[Int] = Seq(0, 0),
    srcFmt: Int = 3,      // FbDataFmt(ChiselEnum,4-bit)：0=FP32,1=FP16,2=BF16,3=INT8,4=MXE4M3,5=MXE5M2,8=INT32,9=INT16,10=INT4（编码6/7已废弃非法）
    dstFmt: Int = 3,
    // 注：cvtScale/cvtZeroPoint 已从命令字段移至 ForagerBee 顶层 IO（全局供给），
    //     由 runForagerBee 的 cvtScale/cvtZeroPoint 形参驱动，不再是 FbTestCmd 成员
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
    t2lNumTileCols: Int = 0,
    // MX 量化字段（量化激活由 dstFmt 派生：dstFmt∈{4,5} 即激活，无独立开关）
    scaleAddr: BigInt = 0,
    scaleBlockSize: Int = 0     // 编码：0→16, 1→32, 2→64 个元素共享一个 E8M0 scale
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
    // writeOutstandingDepth 必须 ≥ 1（=0 非法）
    an [IllegalArgumentException] should be thrownBy ForagerBeeConfig(writeOutstandingDepth = 0)
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
    port.srcFmt.poke(BigInt(c.srcFmt))   // FbDataFmt ChiselEnum（4-bit）按编码值 poke
    port.dstFmt.poke(BigInt(c.dstFmt))
    // cvtScale/cvtZeroPoint 已上移为顶层 IO，由 pokeCvtGlobal 驱动（见下）
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
    // MX 量化字段（量化激活由 dstFmt 派生，无独立开关端口）
    port.scaleAddr.poke(c.scaleAddr)
    port.scaleBlockSize.poke(c.scaleBlockSize.U)
  }

  /** 驱动顶层全局量化参数端口（`ForagerBee.io.cvtScale/cvtZeroPoint`，仅 enableConversion 时存在）。
    *
    * 显式 poke 而非依赖仿真器对未驱动 Input 的默认值：不同后端语义不一致
    * （2-state 后端初值为 0，4-state 后端可能为 X 并污染 converter 的移位量/ZP 解码），
    * 显式 poke 保证跨后端确定性，也与「所有功能转换用例原本用默认 0」保持等价。 */
  private def pokeCvtGlobal(dut: ForagerBee, scale: BigInt, zp: BigInt): Unit = {
    dut.io.cvtScale.foreach(_.poke(scale))
    dut.io.cvtZeroPoint.foreach(_.poke(zp))
  }

  /** 跑一组命令直到全部 done（或超时 fail），返回 (done 列表, 内存终态)
    * 命令逐条注入：上一条被接受后再注入下一条。
    * onDoneFire：每次 done.valid 拍回调，参数为当前周期号（供 done 时机校验）
    * cvtScale/cvtZeroPoint：顶层全局量化参数（原 FbCmd 字段，已上移为 IO；默认 0 与迁移前等价） */
  private def runForagerBee(
      cfg: ForagerBeeConfig,
      cmds: Seq[FbTestCmd],
      prefill: mutable.HashMap[BigInt, Byte] = mutable.HashMap.empty,
      maxCycles: Int = 30000,
      modelFactory: (mutable.HashMap[BigInt, Byte], Int) => FbStreamMemSlave =
        (m, bb) => new FbStreamMemSlave(m, bb),
      onDoneFire: Int => Unit = _ => (),
      cvtScale: BigInt = 0,
      cvtZeroPoint: BigInt = 0
  ): (Seq[(Int, Boolean)], mutable.HashMap[BigInt, Byte]) = {
    val mem = prefill
    val dones = mutable.ArrayBuffer[(Int, Boolean)]() // (tag, err)
    simulate(new ForagerBee(cfg)) { dut =>
      val models = Seq.fill(cfg.numPorts)(modelFactory(mem, cfg.beatBytes))
      val dummy = FbTestCmd()
      var cmdIdx = 0

      // 初始化后显式复位
      pokeCmdPayload(dut.io.cmd.payload, cmds.headOption.getOrElse(dummy))
      pokeCvtGlobal(dut, cvtScale, cvtZeroPoint)
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
          onDoneFire(cycle)
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
      srcAddr = 0, dstAddr = 0x8000)
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
      srcAddr = 0, dstAddr = 0x8000, srcFmt = 9, dstFmt = 9)
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
      srcAddr = 0, dstAddr = 0x8000)
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
      srcAddr = 0, dstAddr = 0x8000)
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
      srcAddr = 0, dstAddr = 0xB000)
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
      srcAddr = 0, dstAddr = 0x8000)
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
    val cmd = FbTestCmd(tag = 0x81, op = "PERMUTE", dimCount = 2, srcFmt = 8, dstFmt = 8,
      shape = Seq(4, 2), permVec = Seq(1, 0),
      srcStride = Seq(16, 16), dstStride = Seq(16, 16),
      srcAddr = 0, dstAddr = 0x2000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x81, false))
  }

  it should "PERMUTE 3D: swap dim1 and dim2 (shape=[4,3,4])" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 4 * 3 * 4 * 4, seed = 88)
    val cmd = FbTestCmd(tag = 0x82, op = "PERMUTE", dimCount = 3, srcFmt = 8, dstFmt = 8,
      shape = Seq(4, 3, 4), permVec = Seq(0, 2, 1),
      srcStride = Seq(16, 16, 48), dstStride = Seq(16, 16, 48),
      srcAddr = 0, dstAddr = 0x3000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x82, false))
  }

  it should "PERMUTE 5D: complex permutation (maxDims=5)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 4 * 2 * 2 * 2 * 3 * 4, seed = 99)
    val cmd = FbTestCmd(tag = 0x83, op = "PERMUTE", dimCount = 5, srcFmt = 8, dstFmt = 8,
      shape = Seq(4, 2, 2, 2, 3), permVec = Seq(0, 4, 2, 3, 1),
      srcStride = Seq(16, 16, 32, 64, 128),
      dstStride = Seq(16, 16, 32, 64, 128),
      srcAddr = 0, dstAddr = 0x4000)
    val (dones, _) = runForagerBee(permCfg, Seq(cmd), mem)
    dones shouldBe Seq((0x83, false))
  }

  it should "PERMUTE illegal permVec (value >= dimCount) → err" in {
    val cmd = FbTestCmd(tag = 0x84, op = "PERMUTE", dimCount = 2, srcFmt = 8, dstFmt = 8,
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
    val cmd = FbTestCmd(tag = 0x85, op = "COPY", dimCount = 2, 
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
    val cmd = FbTestCmd(tag = 0x86, op = "COPY", dimCount = 2, 
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
    val cmd = FbTestCmd(tag = 0x87, op = "COPY", dimCount = 3, 
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
    val cmd = FbTestCmd(tag = 0x90, op = "COPY", dimCount = 3, 
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
    val cmd = FbTestCmd(tag = 0x88, op = "PERMUTE", dimCount = 2, srcFmt = 8, dstFmt = 8,
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
    val cmd = FbTestCmd(tag = 0x89, op = "COPY", dimCount = 2, 
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
      srcAddr = 0, dstAddr = 0x8000)
    val (dones, _) = runForagerBee(copyOnlyCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xC0, true))
  }

  it should "copy-only DMA rejects PERMUTE with err=true" in {
    val cmd = FbTestCmd(tag = 0xC1, op = "PERMUTE", dimCount = 2, srcFmt = 8, dstFmt = 8,
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
        srcAddr = 0, dstAddr = 0x8000),
      FbTestCmd(tag = 0xD2, op = "PERMUTE", dimCount = 2, srcFmt = 8, dstFmt = 8,
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
    * 字段布局遵循 FbDescLayout（v3）的声明顺序：dstFmt 为最后一个字段，
    * cvtScale/cvtZeroPoint 已上移为顶层 IO、不再序列化。
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
      permVec: Seq[Int] = Seq(),
      padBefore: Seq[Int] = Seq(0, 0),
      padAfter: Seq[Int] = Seq(0, 0),
      nextDescAddr: BigInt = 0,
      chainMode: Boolean = true,
      srcFmt: Int = 3,
      dstFmt: Int = 3
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
    for (d <- 0 until maxDims) pack(BigInt(permVec.lift(d).getOrElse(0)), 3)       // permVec
    for (d <- 0 until 2) pack(BigInt(padBefore.lift(d).getOrElse(0)), 16)          // padBefore
    for (d <- 0 until 2) pack(BigInt(padAfter.lift(d).getOrElse(0)), 16)           // padAfter
    pack(nextDescAddr, addrW)     // nextDescAddr
    pack(if (chainMode) BigInt(1) else BigInt(0), 1) // chainMode
    pack(BigInt(srcFmt), FbDataFmt.getWidth)  // srcFmt（FbDataFmt ChiselEnum，4-bit）
    pack(BigInt(dstFmt), FbDataFmt.getWidth)  // dstFmt（FbDataFmt ChiselEnum，4-bit）—— v3 最后一个字段

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
      maxCycles: Int = 50000,
      cvtScale: BigInt = 0,
      cvtZeroPoint: BigInt = 0
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
      pokeCvtGlobal(dut, cvtScale, cvtZeroPoint)
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
      permVec: Seq[Int] = Seq(),
      padBefore: Seq[Int] = Seq(0, 0),
      padAfter: Seq[Int] = Seq(0, 0),
      chainMode: Boolean = false,
      nextDescAddr: BigInt = 0
  ) {
    def toFbTestCmd: FbTestCmd = FbTestCmd(tag, transpose, op, dimCount, shape, srcStride, dstStride,
      srcAddr, dstAddr, permVec, padBefore, padAfter)
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

  it should "format conversion: srcFmt==dstFmt regression (plain COPY unchanged)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 64
    fillPattern(mem, 0, len)
    val cmd = FbTestCmd(tag = 0xF0, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000,
      srcFmt = 3, dstFmt = 3)
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
    val cmd = FbTestCmd(tag = 0xF1, dimCount = 1, 
      shape = Seq(8), srcStride = Seq(32), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x2000,
      srcFmt = 0, dstFmt = 1) // FP32→FP16
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
    val cmd = FbTestCmd(tag = 0xF2, dimCount = 1, 
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x3000,
      srcFmt = 1, dstFmt = 0) // FP16→FP32
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
    val cmd = FbTestCmd(tag = 0xF3, dimCount = 1, 
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x4000,
      srcFmt = 2, dstFmt = 0) // BF16→FP32
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
    val cmd = FbTestCmd(tag = 0xF4, dimCount = 1, 
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x5000,
      srcFmt = 1, dstFmt = 2) // FP16→BF16
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

  /** Scala 侧格式编码→字节数映射（镜像 FbDataFmt.bytesOfCeil，用于纯 Scala bbox/地址计算） */
  private def fbFmtBytes(f: Int): Int = f match {
    case 0 => 4   // FP32
    case 1 => 2   // FP16
    case 2 => 2   // BF16
    case 3 => 1   // INT8
    case 4 => 1   // MXE4M3
    case 5 => 1   // MXE5M2
    case 8 => 4   // INT32
    case 9 => 2   // INT16
    case 10 => 1  // INT4（ceil 字节）
    case _ => 4
  }

  /** 构造 im2col FbTestCmd
    * shape(0) = srcBboxBytes, shape(1) = dstBboxBytes（用于冲突检测 bbox）
    * 元素粒度由 srcFmt/dstFmt 派生（elemBytesLog2 字段已删除） */
  private def mkIm2colCmd(
      tag: Int, C: Int, H: Int, W: Int,
      kH: Int, kW: Int, sH: Int, sW: Int,
      pH: Int, pW: Int, dH: Int, dW: Int,
      srcFmt: Int, dstFmt: Int, srcAddr: BigInt, dstAddr: BigInt
  ): FbTestCmd = {
    val elemBytes = fbFmtBytes(srcFmt)
    val outH = (H + 2 * pH - dH * (kH - 1) - 1) / sH + 1
    val outW = (W + 2 * pW - dW * (kW - 1) - 1) / sW + 1
    val srcBbox = C * H * W * elemBytes
    val dstBbox = C * kH * kW * outH * outW * elemBytes
    FbTestCmd(
      tag = tag, op = "IM2COL", dimCount = 1,
      shape = Seq(srcBbox, dstBbox),
      srcStride = Seq(srcBbox), dstStride = Seq(dstBbox),
      srcAddr = srcAddr, dstAddr = dstAddr,
      srcFmt = srcFmt, dstFmt = dstFmt,
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
    val featureMap = (0 until iC * iH * iW).toSeq
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x10, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, 9, 9,
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
    val featureMap = (0 until iC * iH * iW).map(i => (i * 7 + 3) & 0xFFFF)
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x11, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, 9, 9,
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
    val featureMap = (0 until iC * iH * iW).map(i => (i * 13 + 5) & 0xFFFF)
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x12, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, 9, 9,
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
    val featureMap = (0 until iC * iH * iW).map(i => (i * 11 + 7) & 0xFFFF)
    val expected = computeIm2col(featureMap, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)

    val mem = mutable.HashMap.empty[BigInt, Byte]
    writeElems16(mem, BigInt(0), featureMap)
    val cmd = mkIm2colCmd(0x13, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, 9, 9,
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
    val cmd = mkIm2colCmd(0x14, 1, 4, 4, 3, 3, 1, 1, 0, 0, 1, 1, 9, 9,
      srcAddr = 0, dstAddr = 0x10000)
    val (dones, _) = runForagerBee(noI2cCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x14, true))
  }

  it should "im2col: reject kernel=0 with err" in {
    val cmd = FbTestCmd(
      tag = 0x15, op = "IM2COL", dimCount = 1,
      shape = Seq(32, 32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x10000, srcFmt = 9, dstFmt = 9,
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
      srcAddr = 0, dstAddr = 0x8000, 
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
      srcAddr = 0x1000, dstAddr = 0, 
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
      srcAddr = 0, dstAddr = 0x8000, 
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
      srcAddr = 0, dstAddr = 0, 
      sgListAddr = BigInt(0x5000), sgEntryCount = 3)
    val (dones, _) = runForagerBee(noSgCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xA3, true))
  }

  it should "GATHER rejected when sgEntryCount=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0xA4, op = "GATHER", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x8000, 
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
      srcFmt = 0, dstFmt = 0,                  // FP32：4 bytes/elem（子块语义明确 FP32）
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
    val elemBytes = 1
    // Tn * e = 16 ≡ 0 mod 16(beatBytes) ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = ((r * N + c) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x71, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000,
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
    val elemBytes = 1
    // Tn * e = 16 ≡ 0 mod 16 ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = (((r * 7 + c * 13 + 5) * 3) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x72, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000,
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
      srcAddr = 0, dstAddr = 0x10000, 
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 0,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x73, true))
  }

  it should "TILE2LINEAR illegal: Mt=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0x74, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, 
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 16,
      t2lNumTileRows = 0, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x74, true))
  }

  it should "TILE2LINEAR beat alignment violation: Tn*e not divisible by beatBytes → err" in {
    // Tn=3, elemBytes=4 → dim0 row bytes = 3*4=12, not divisible by 16 → cBad → err
    val cmd = FbTestCmd(tag = 0x75, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, srcFmt = 8, dstFmt = 8,
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
        srcAddr = 0, dstAddr = 0x20000, 
        t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
        t2lNumTileRows = Mt, t2lNumTileCols = Nt),
      // COPY: 64 bytes non-conflicting
      FbTestCmd(tag = 0x77, op = "COPY", dimCount = 1,
        shape = Seq(64), srcStride = Seq(64), dstStride = Seq(64),
        srcAddr = 0x5000, dstAddr = 0x6000),
      // TRANSPOSE: 8×8 INT8 non-conflicting
      FbTestCmd(tag = 0x78, op = "TRANSPOSE", dimCount = 2,
        shape = Seq(8, 8), srcStride = Seq(8, 16), dstStride = Seq(8, 0),
        srcAddr = 0x8000, dstAddr = 0x30000)
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
    val elemBytes = 1
    // Tn * e = 16 ≡ 0 mod 16(beatBytes) ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 源：线性 tile-row-major 缓冲（M*N*elemBytes 字节）
    val totalBytes = M * N * elemBytes
    for (i <- 0 until totalBytes) {
      mem(BigInt(i)) = ((i * 7 + 13) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x81, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000,
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
    val elemBytes = 1
    // Tn * e = 16 ≡ 0 mod 16 ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val totalBytes = M * N * elemBytes
    for (i <- 0 until totalBytes) {
      mem(BigInt(i)) = (((i * 13 + 7) * 3) & 0xFF).toByte
    }
    val cmd = FbTestCmd(tag = 0x82, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000,
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
      srcAddr = 0, dstAddr = 0x10000, 
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 0,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x83, true))
  }

  it should "LINEAR2TILE illegal: N=0 → done.err=true" in {
    val cmd = FbTestCmd(tag = 0x84, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, 
      t2lMatCols = 0, t2lTileRows = 16, t2lTileCols = 16,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x84, true))
  }

  it should "LINEAR2TILE beat alignment violation: Tn*e not divisible by beatBytes → err" in {
    // Tn=3, elemBytes=4 → dim0 row bytes = 3*4=12, not divisible by 16 → err
    val cmd = FbTestCmd(tag = 0x85, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, srcFmt = 8, dstFmt = 8,
      t2lMatCols = 12, t2lTileRows = 4, t2lTileCols = 3,
      t2lNumTileRows = 2, t2lNumTileCols = 4)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x85, true))
  }

  it should "LINEAR2TILE round-trip: TILE2LINEAR then LINEAR2TILE restores original matrix" in {
    val M = 32; val N = 32; val Tm = 16; val Tn = 16
    val Mt = M / Tm; val Nt = N / Tn  // 2, 2
    val elemBytes = 1
    // Tn * e = 16 ≡ 0 mod 16 ✓
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 准备行主序矩阵 A at 0x0000
    for (r <- 0 until M; c <- 0 until N) {
      mem(BigInt(r * N + c)) = ((r * N + c + 17) & 0xFF).toByte
    }
    // 第一步：TILE2LINEAR（src=0x0000 行主序矩阵 → dst=0x10000 线性缓冲）
    val cmd1 = FbTestCmd(tag = 0x86, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000,
      t2lMatCols = N, t2lTileRows = Tm, t2lTileCols = Tn,
      t2lNumTileRows = Mt, t2lNumTileCols = Nt)
    val (dones1, m1) = runForagerBee(testCfg, Seq(cmd1), mem, maxCycles = 50000)
    dones1 shouldBe Seq((0x86, false))
    // 第二步：LINEAR2TILE（src=0x10000 线性缓冲 → dst=0x20000 行主序矩阵）
    val cmd2 = FbTestCmd(tag = 0x87, op = "LINEAR2TILE", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0x10000, dstAddr = 0x20000,
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

  // ===================== MX 量化（MXFP8）用例 =====================

  // MX 量化配置：需 enableConversion（量化器仅在该开关下实例化）
  private val mxqCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 5,
    tileSize = 8, maxBurstLen = 16,
    dataWidth = 128, addressWidth = 32,
    enableConversion = true, converterPipeStages = 1
  )

  // --- 浮点编码辅助 ---
  private def f32bits(v: Float): Int = java.lang.Float.floatToRawIntBits(v)

  /** FP16 编码（RNE 舍入，与硬件输入准备一致） */
  private def fp16Of(v: Float): Int = {
    val b = f32bits(v)
    val s = (b >>> 16) & 0x8000
    val e = (b >>> 23) & 0xFF
    val m = b & 0x7FFFFF
    if (e == 255) { // inf/NaN
      if (m != 0) s | 0x7E00 else s | 0x7C00
    } else {
      val e16 = e - 112
      if (e16 >= 31) s | 0x7C00 // 上溢 → inf（测试输入避开舍入边界）
      else if (e16 >= 1) { // 正常数：尾数 23→10 位 + RNE，进位时指数 +1（溢出到 inf）
        val round = (m >>> 12) & 1
        val sticky = if ((m & 0xFFF) != 0) 1 else 0
        val lsb = (m >>> 13) & 1
        val m10 = (m >>> 13) + (if (round == 1 && (sticky == 1 || lsb == 1)) 1 else 0)
        if (m10 > 0x3FF) { if (e16 == 30) s | 0x7C00 else s | ((e16 + 1) << 10) }
        else s | (e16 << 10) | m10
      }
      else if (e16 >= -10) { // 次正规：显式前导 1 右移后 RNE
        val full = 0x400000 | m
        val shift = 14 - e16
        val round = (full >>> (shift - 1)) & 1
        val sticky = if ((full & ((1 << (shift - 1)) - 1)) != 0) 1 else 0
        var vq = full >>> shift
        if (round == 1 && (sticky == 1 || (vq & 1) == 1)) vq += 1
        s | (vq & 0x3FF)
      } else s // 太小 → ±0（FTZ）
    }
  }

  /** BF16 编码（截断尾数；测试输入均避开舍入边界） */
  private def bf16Of(v: Float): Int = (f32bits(v) >>> 16) & 0xFFFF

  private def putFp16(mem: mutable.HashMap[BigInt, Byte], base: BigInt, vals: Seq[Float]): Unit =
    vals.zipWithIndex.foreach { case (v, i) =>
      val h = fp16Of(v)
      mem(base + 2 * i) = (h & 0xFF).toByte
      mem(base + 2 * i + 1) = ((h >>> 8) & 0xFF).toByte
    }

  private def putBf16(mem: mutable.HashMap[BigInt, Byte], base: BigInt, vals: Seq[Float]): Unit =
    vals.zipWithIndex.foreach { case (v, i) =>
      val h = bf16Of(v)
      mem(base + 2 * i) = (h & 0xFF).toByte
      mem(base + 2 * i + 1) = ((h >>> 8) & 0xFF).toByte
    }

  private def putFp32(mem: mutable.HashMap[BigInt, Byte], base: BigInt, vals: Seq[Float]): Unit =
    vals.zipWithIndex.foreach { case (v, i) =>
      val b = f32bits(v)
      for (k <- 0 until 4) mem(base + 4 * i + k) = ((b >>> (8 * k)) & 0xFF).toByte
    }

  /** FP16 (16 bits) → FP32 (32 bits)，与 FbMxQuantizer.fp16ToFp32 语义一致：
    * 次正规保持为 fp32 次正规（量化阶段按零冲刷） */
  private def fp16BitsToFp32Bits(x: Int): Int = {
    val sign = x & 0x8000
    val e = (x >>> 10) & 0x1F
    val man = x & 0x3FF
    if (e == 0) {
      if (man == 0) sign << 16
      else (sign << 16) | (man << 13) // fp32 次正规
    } else if (e == 31) (sign << 16) | (0xFF << 23) | (man << 13)
    else (sign << 16) | ((e + 112) << 23) | (man << 13)
  }

  /** MXFP8 量化参考模型（与 FbMxQuantizer 位级对齐）。
    * elemsF32Bits：源元素的 fp32 位表示（FP16 需先经 fp16BitsToFp32Bits，BF16 左移 16 位）。
    * dstFmt：4=MXE4M3、5=MXE5M2。返回 (量化字节列表, E8M0 scale 字节)。
    * 语义：零/次正规不贡献 max_exp 且输出 ±0；scale = max(0, 127+max_exp) 低 8 位；
    * 尾数截取 + RNE；下溢（new_exp≤0）FTZ；上溢/inf 饱和到最大正常数。 */
  private def mxQuantizeRef(elemsF32Bits: Seq[Int], dstFmt: Int): (Seq[Int], Int) = {
    val dstE4 = dstFmt == 4
    val bias = if (dstE4) 7 else 15
    val mantW = if (dstE4) 3 else 2
    val maxNormExp = if (dstE4) 14 else 30
    val satExp = if (dstE4) 14 else 30
    val satMant = if (dstE4) 7 else 3
    def satByte(sign: Int) = (sign << 7) | (satExp << mantW) | satMant

    // 块内最大真指数（零/次正规/NaN 不贡献；无任何贡献 → -128 → scale=0）
    val maxExp = elemsF32Bits.foldLeft(-128) { (acc, b) =>
      val e = (b >>> 23) & 0xFF
      val m = b & 0x7FFFFF
      val contrib = (e != 0) && (e != 255 || m == 0)
      if (contrib) math.max(acc, (if (e == 255) 127 else e - 127)) else acc
    }
    val scaleByte = { val s = maxExp + 127; if (s <= 0) 0 else s & 0xFF }

    val bytes = elemsF32Bits.map { b =>
      val sign = b >>> 31
      val e = (b >>> 23) & 0xFF
      val man = b & 0x7FFFFF
      if (e == 0) sign << 7 // 零/次正规 → ±0（保号）
      else {
        val isNan = e == 255 && man != 0
        val isInfNan = e == 255
        val trueExp = e - 127
        val newExp0 = trueExp - maxExp + bias
        // 尾数截取 + RNE（guard/sticky/lsb，与硬件 mt/up/mr 同构）
        val kept = man >>> (23 - mantW)
        val guard = (man >>> (23 - mantW - 1)) & 1
        val sticky = if ((man & ((1 << (23 - mantW - 1)) - 1)) != 0) 1 else 0
        val roundUp = guard == 1 && (sticky == 1 || (kept & 1) == 1)
        var mant = kept + (if (roundUp) 1 else 0)
        var exp = newExp0
        if (mant > (1 << mantW) - 1) { mant = 0; exp += 1 } // 进位 → 指数 +1、尾数清零
        if (isNan && dstE4) (sign << 7) | 0x7F
        else if (isNan || isInfNan || exp > maxNormExp) satByte(sign)
        else if (exp <= 0) sign << 7 // 下溢 → FTZ（保号）
        else (sign << 7) | (exp << mantW) | mant
      }
    }
    (bytes, scaleByte)
  }

  it should "MX quantize FP16→MXE4M3 1D blockSize=32" in {
    val n = 64
    val mem = mutable.HashMap.empty[BigInt, Byte]
    // 64 个 FP16：块 0 指数跨度大（验证 FTZ/饱和），块 1 覆盖舍入与负值；末元素为次正规（→±0）
    val vals0 = Seq.tabulate(32)(i =>
      if (i < 8) math.pow(2.0, 10 - i).toFloat else
      if (i < 16) -math.pow(2.0, 10 - i).toFloat else
      if (i < 24) (1.0 + i * 0.25).toFloat * 4.0f else
      if (i == 31) 6.0e-8f else -(1.0 + i * 0.25).toFloat * 2.0f)
    val vals1 = Seq.tabulate(32)(i =>
      if (i % 7 == 0) 0.0f else ((i % 5) + 1).toFloat * (if (i % 3 == 0) -0.5f else 0.25f))
    putFp16(mem, 0, vals0 ++ vals1)
    val srcBits = (vals0 ++ vals1).map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val cmd = FbTestCmd(tag = 0x91, op = "COPY", dimCount = 1,
      shape = Seq(n), srcStride = Seq(n * 2), dstStride = Seq(n),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 1, dstFmt = 4,
      scaleBlockSize = 1) // blockSize=32（量化激活由 dstFmt=4 派生）
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0x91, false))
    // 量化数据：64 字节连续写于 0x1000
    for (blk <- 0 until 2) {
      val (expBytes, expScale) = mxQuantizeRef(srcBits.slice(blk * 32, blk * 32 + 32), 4)
      for (j <- 0 until 32) {
        withClue(s"blk$blk[$j]: ") {
          (m.getOrElse(BigInt(0x1000 + blk * 32 + j), 0.toByte) & 0xFF) shouldBe expBytes(j)
        }
      }
      withClue(s"scale[$blk]: ") {
        (m.getOrElse(BigInt(0x2000 + blk), 0.toByte) & 0xFF) shouldBe expScale
      }
    }
    // scale beat 内未写字节保持为 0（strb 部分写）
    for (k <- 2 until 16) (m.getOrElse(BigInt(0x2000 + k), 0.toByte) & 0xFF) shouldBe 0
  }

  it should "MX quantize BF16→MXE5M2 2D blockSize=16" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val row0 = Seq.tabulate(32)(i => if (i < 16) (i + 1).toFloat else math.pow(2.0, 14 - (i - 16)).toFloat)
    val row1 = Seq.tabulate(32)(i => if (i % 2 == 0) -(i / 2 + 1).toFloat / 8.0f else (i / 2 + 1).toFloat / 16.0f)
    putBf16(mem, 0, row0)
    putBf16(mem, 64, row1) // 行间距 64 字节（源含空隙，读出 32 字节/行）
    val srcBits = (row0 ++ row1).map(v => bf16Of(v) << 16)
    val cmd = FbTestCmd(tag = 0x92, op = "COPY", dimCount = 2,
      shape = Seq(32, 2), srcStride = Seq(64, 64), dstStride = Seq(32, 32),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x3000,
      srcFmt = 2, dstFmt = 5,
      scaleBlockSize = 0) // blockSize=16（量化激活由 dstFmt=5 派生）
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0x92, false))
    // 每行 32 元素 → 16 字节量化数据 + 2 个 scale 字节；行地址按 dstStride 步进
    for (row <- 0 until 2) {
      for (blk <- 0 until 2) {
        val off = row * 32 + blk * 16
        val (expBytes, expScale) = mxQuantizeRef(srcBits.slice(off, off + 16), 5)
        for (j <- 0 until 16) {
          withClue(s"row$row blk$blk[$j]: ") {
            (m.getOrElse(BigInt(0x1000 + row * 32 + blk * 16 + j), 0.toByte) & 0xFF) shouldBe expBytes(j)
          }
        }
        withClue(s"row$row scale[$blk]: ") {
          (m.getOrElse(BigInt(0x3000 + row * 32 + blk), 0.toByte) & 0xFF) shouldBe expScale
        }
      }
    }
  }

  it should "MX quantize FP32→MXE4M3 blockSize=64 (4:1 compression)" in {
    val n = 64
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val vals = Seq.tabulate(n)(i =>
      math.pow(2.0, 8 - i % 16).toFloat * (1.0f + (i % 8) * 0.0625f) * (if (i % 5 == 0) -1.0f else 1.0f))
    putFp32(mem, 0, vals)
    val srcBits = vals.map(f32bits)
    val cmd = FbTestCmd(tag = 0x93, op = "COPY", dimCount = 1,
      shape = Seq(n), srcStride = Seq(n * 4), dstStride = Seq(n),
      srcAddr = 0, dstAddr = 0x2000, scaleAddr = 0x4000,
      srcFmt = 0, dstFmt = 4,
      scaleBlockSize = 2) // blockSize=64（每块 16 读 beat，恰好 ≤ maxBurstLen；量化激活由 dstFmt=4 派生）
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0x93, false))
    // 64 个 FP32（256 字节）→ 64 字节量化数据 + 1 字节 scale（4:1 压缩）
    val (expBytes, expScale) = mxQuantizeRef(srcBits, 4)
    for (j <- 0 until n) {
      withClue(s"elem[$j]: ") {
        (m.getOrElse(BigInt(0x2000 + j), 0.toByte) & 0xFF) shouldBe expBytes(j)
      }
    }
    withClue("scale[0]: ") {
      (m.getOrElse(BigInt(0x4000), 0.toByte) & 0xFF) shouldBe expScale
    }
  }

  it should "MX quantize minimal shape(0)=blockSize" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val vals = Seq(1.0f, 0.5f, -1.5f, 0.75f, 2.0f, -2.5f, 3.0f, -4.0f,
                   8.0f, 0.125f, -0.25f, 5.0f, 6.0f, 7.0f, 0.0f, -0.0f)
    putFp16(mem, 0, vals)
    val srcBits = vals.map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val cmd = FbTestCmd(tag = 0x94, op = "COPY", dimCount = 1,
      shape = Seq(16), srcStride = Seq(32), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 1, dstFmt = 4,
      scaleBlockSize = 0) // shape(0)=16=blockSize，恰一个块（量化激活由 dstFmt=4 派生）
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0x94, false))
    val (expBytes, expScale) = mxQuantizeRef(srcBits, 4)
    for (j <- 0 until 16) {
      withClue(s"elem[$j]: ") {
        (m.getOrElse(BigInt(0x1000 + j), 0.toByte) & 0xFF) shouldBe expBytes(j)
      }
    }
    (m.getOrElse(BigInt(0x2000), 0.toByte) & 0xFF) shouldBe expScale
  }

  it should "MX quantize all-zero input → all-zero output, scale=0" in {
    val n = 32
    val mem = mutable.HashMap.empty[BigInt, Byte]
    putFp16(mem, 0, Seq.fill(n)(0.0f))
    val cmd = FbTestCmd(tag = 0x95, op = "COPY", dimCount = 1,
      shape = Seq(n), srcStride = Seq(n * 2), dstStride = Seq(n),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 1, dstFmt = 4,
      scaleBlockSize = 1) // blockSize=32（量化激活由 dstFmt=4 派生）
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0x95, false))
    for (j <- 0 until n) (m.getOrElse(BigInt(0x1000 + j), 0.toByte) & 0xFF) shouldBe 0
    (m.getOrElse(BigInt(0x2000), 0.toByte) & 0xFF) shouldBe 0 // 全零块 → scale=0
  }

  it should "MX quantize cBad: shape not multiple of blockSize → done.err=true" in {
    // shape(0)=48、blockSize=32 → 48 % 32 ≠ 0 → mxQuantBad
    val cmd = FbTestCmd(tag = 0x96, op = "COPY", dimCount = 1,
      shape = Seq(48), srcStride = Seq(96), dstStride = Seq(48),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 1, dstFmt = 4,
      scaleBlockSize = 1)
    val (dones, _) = runForagerBee(mxqCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0x96, true))
  }

  // ===================== 写端口共享仲裁（enableSharedWritePorts） =====================

  // 共享仲裁配置：与 testCfg 其余参数一致，仅开关打开（写突发经 FbWrPortArbiter 分配）
  private val sharedWrCfg = testCfg.copy(enableSharedWritePorts = true)

  it should "elaborate with enableSharedWritePorts=true (default numPorts=2)" in {
    val v = ChiselStage.emitSystemVerilog(new ForagerBee(
      ForagerBeeConfig(enableSharedWritePorts = true)))
    v should include("module ForagerBee")
  }

  it should "elaborate with enableSharedWritePorts=false (default) without FbWrPortArbiter" in {
    // 负向守卫：开关关闭时顶层保持 1:1 直连，仲裁器不应被例化/生成进 SV 文本
    val v = ChiselStage.emitSystemVerilog(new ForagerBee(ForagerBeeConfig()))
    v should not include "FbWrPortArbiter"
  }

  it should "elaborate enableSharedWritePorts=true with numPorts=1 (degenerate), 3 and 4" in {
    for (p <- Seq(1, 3, 4)) {
      val v = ChiselStage.emitSystemVerilog(new ForagerBee(
        ForagerBeeConfig(numPorts = p, enableSharedWritePorts = true)))
      v should include("module ForagerBee")
    }
  }

  it should "shared wr arb: two engines write disjoint addresses concurrently, both ports used" in {
    // 两条地址互不冲突的命令被调度器并发派发到两个引擎，
    // 突发窗口重叠时仲裁器必须把它们分配到不同物理端口。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 256 // 16B/beat × 16 beat = 2 个突发（maxBurstLen=8）/引擎
    fillPattern(mem, 0x0000, len, seed = 1)
    fillPattern(mem, 0x2000, len, seed = 2)
    val cmds = Seq(
      FbTestCmd(tag = 0x5A, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0x6B, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x2000, dstAddr = 0x3000)
    )
    val counters = Seq.fill(sharedWrCfg.numPorts)(
      new FbWrCountingMemSlave(mem, sharedWrCfg.beatBytes))
    var idx = 0
    val (dones, m) = runForagerBee(sharedWrCfg, cmds, mem,
      modelFactory = (_, _) => { val s = counters(idx); idx += 1; s })
    dones.map(_._1).toSet shouldBe Set(0x5A, 0x6B)
    dones.forall(!_._2) shouldBe true
    // 数据正确性强断言：两条命令各自完整写出，互不串扰
    for (i <- 0 until len) {
      withClue(s"engine-A dst[$i]: ") { m(BigInt(0x1000) + i) shouldBe m(BigInt(0x0000) + i) }
      withClue(s"engine-B dst[$i]: ") { m(BigInt(0x3000) + i) shouldBe m(BigInt(0x2000) + i) }
    }
    // 端口分配强断言：突发总数守恒且两个物理端口均被使用（并发窗口重叠时必然分端口）
    val perPort = counters.map(_.wrBursts)
    val expBurstsPerEng = len / (sharedWrCfg.beatBytes * sharedWrCfg.maxBurstLen)
    withClue(s"per-port burst distribution $perPort: ") {
      perPort.sum shouldBe expBurstsPerEng * 2
      perPort.forall(_ > 0) shouldBe true
    }
  }

  it should "shared wr arb: single engine multi-burst COPY stays functional (all bursts land, data correct)" in {
    // 单引擎串行写模型：引擎逐突发等待 wrResp，端口在其下一 SOF 前总已释放，
    // 突发全部落同一物理端口是预期行为（非本用例断言点）；
    // 此处仅验证共享模式下功能正确：突发总数守恒 + 数据全量比对。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 512 // 32 beat → 4 个突发（maxBurstLen=8）
    fillPattern(mem, 0, len, seed = 7)
    val cmd = FbTestCmd(tag = 0x7C, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x4000)
    val counters = Seq.fill(sharedWrCfg.numPorts)(
      new FbWrCountingMemSlave(mem, sharedWrCfg.beatBytes))
    var idx = 0
    val (dones, m) = runForagerBee(sharedWrCfg, Seq(cmd), mem,
      modelFactory = (_, _) => { val s = counters(idx); idx += 1; s })
    dones shouldBe Seq((0x7C, false))
    for (i <- 0 until len) {
      withClue(s"dst[$i]: ") { m(BigInt(0x4000) + i) shouldBe m(BigInt(i)) }
    }
    withClue(s"per-port burst distribution ${counters.map(_.wrBursts)}: ") {
      counters.map(_.wrBursts).sum shouldBe len / (sharedWrCfg.beatBytes * sharedWrCfg.maxBurstLen)
    }
  }

  it should "shared wr arb: round-robin fairness under repeated contention (2 engines x 2 serial cmds)" in {
    // 公平性竞争复用：2 引擎各发 2 条串行命令（前一条完成后经命令队列续派下一条），
    // 总突发数 4 > 物理端口数 2，每个引擎的第二个突发必然遭遇二次端口竞争；
    // round-robin 保证突发在两端口间均衡分布。
    val fairnessCfg = sharedWrCfg // testCfg.copy: numPorts=2, enableSharedWritePorts=true
    val len = 128 // 8 beat → 1 个突发（maxBurstLen=8）/命令
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val srcBases = Seq(0x0000, 0x0100, 0x0200, 0x0300)
    val dstBases = Seq(0x1000, 0x1100, 0x1200, 0x1300)
    srcBases.zipWithIndex.foreach { case (b, i) => fillPattern(mem, b, len, seed = i) }
    val cmds = srcBases.zip(dstBases).zipWithIndex.map { case ((s, d), i) =>
      FbTestCmd(tag = 0xA0 + i, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = s, dstAddr = d)
    }
    val counters = Seq.fill(fairnessCfg.numPorts)(
      new FbWrCountingMemSlave(mem, fairnessCfg.beatBytes))
    var idx = 0
    val (dones, m) = runForagerBee(fairnessCfg, cmds, mem,
      modelFactory = (_, _) => { val s = counters(idx); idx += 1; s })
    dones.map(_._1).toSet shouldBe Set(0xA0, 0xA1, 0xA2, 0xA3)
    dones.forall(!_._2) shouldBe true
    // 数据全量比对（强断言）：4 条命令各自完整写出，互不串扰
    for (i <- 0 until 4; j <- 0 until len) {
      withClue(s"cmd$i dst[$j]: ") { m(BigInt(dstBases(i)) + j) shouldBe m(BigInt(srcBases(i)) + j) }
    }
    // 端口均衡断言：总突发 4（> 端口数 2，产生二次竞争），两端口都 ≥ 1 且分布均衡（各 2）
    val perPort = counters.map(_.wrBursts)
    withClue(s"per-port burst distribution $perPort: ") {
      perPort.sum shouldBe 4
      perPort.forall(_ >= 1) shouldBe true
      perPort.forall(_ == 2) shouldBe true
    }
  }

  it should "shared wr arb: non-COPY traffic (2D TRANSPOSE) through shared write ports" in {
    // 共享模式下的非 COPY 写流量：复用 8x8 INT8 TRANSPOSE 用例，仅开关打开；
    // 验证经 FbTransposeBuffer 产生的写突发也能正确通过共享仲裁器。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x44, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(n, 0),
      srcAddr = 0, dstAddr = 0x8000)
    val counters = Seq.fill(sharedWrCfg.numPorts)(
      new FbWrCountingMemSlave(mem, sharedWrCfg.beatBytes))
    var idx = 0
    val (dones, m) = runForagerBee(sharedWrCfg, Seq(cmd), mem,
      modelFactory = (_, _) => { val s = counters(idx); idx += 1; s })
    dones shouldBe Seq((0x44, false))
    for (j <- 0 until n; i <- 0 until n) {
      m(BigInt(0x8000 + j * n + i)) shouldBe (((i * n + j) & 0xFF).toByte)
    }
    withClue(s"per-port burst distribution ${counters.map(_.wrBursts)}: ") {
      counters.map(_.wrBursts).sum should be > 0
    }
  }

  // ===================== 非阻塞写（writeOutstandingDepth > 1） =====================
  // 本地仿真环境未就绪：仿真类用例作为环境修复后的基准保留；
  // 配置验证/文本守卫类用例可在本地直接运行。

  // 非阻塞写配置：与 testCfg 其余参数一致，仅提升写 outstanding 深度（启用 nbWr 编译期分支）
  private val nbwrCfg = testCfg.copy(writeOutstandingDepth = 4)
  private val nbwrSharedCfg = testCfg.copy(writeOutstandingDepth = 4, enableSharedWritePorts = true)
  private val nbWrDelay = 8 // 大写响应延迟：迫使多个写突发同时在途（>1 拍间隔无法填满深度 4）
  private val nbwrSlaveFactory: (mutable.HashMap[BigInt, Byte], Int) => FbStreamMemSlave =
    (m, bb) => new FbStreamMemSlave(m, bb, writeDelay = nbWrDelay)

  it should "nbwr: elaborate writeOutstandingDepth=4 (direct) and +enableSharedWritePorts=true" in {
    val v1 = ChiselStage.emitSystemVerilog(new ForagerBee(nbwrCfg))
    v1 should include("module ForagerBee")
    v1 should include("wrInFlight") // 非阻塞路径应生成在途写计数器
    val v2 = ChiselStage.emitSystemVerilog(new ForagerBee(nbwrSharedCfg))
    v2 should include("module ForagerBee")
    v2 should include("wrInFlight")
    v2 should include("FbWrPortArbiter")
  }

  it should "nbwr: default config SV contains no wrInFlight (zero-regression guard)" in {
    // 编译期分支零回归的测试层守卫：=1 时不新增任何非阻塞写硬件，
    // wrInFlight 信号名不应出现在生成的 SystemVerilog 文本中。
    val v = ChiselStage.emitSystemVerilog(new ForagerBee(ForagerBeeConfig()))
    v should include("module ForagerBee")
    v should not include "wrInFlight"
    // 共享仲裁模式（=1 + enableSharedWritePorts）同样不应引入非阻塞写硬件：
    // 仲裁器例化但仅走阻塞式路径（无 wrInFlight 信号）
    val v2 = ChiselStage.emitSystemVerilog(new ForagerBee(ForagerBeeConfig(enableSharedWritePorts = true)))
    v2 should include("FbWrPortArbiter")
    v2 should not include "wrInFlight"
  }

  it should "nbwr: 1D COPY with writeDelay=8 completes and data correct" in {
    // 256B = 16 beat → 2 个突发（maxBurstLen=8）；writeDelay=8 下第二个突发在
    // 第一个响应返回前即发出，验证非阻塞写路径下的数据完整性。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 256
    fillPattern(mem, 0, len)
    val cmd = FbTestCmd(tag = 0x21, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x1000)
    val (dones, m) = runForagerBee(nbwrCfg, Seq(cmd), mem, maxCycles = 10000,
      modelFactory = nbwrSlaveFactory)
    dones shouldBe Seq((0x21, false))
    for (i <- 0 until len) {
      withClue(s"dst[$i]: ") { m(BigInt(0x1000) + i) shouldBe m(BigInt(i)) }
    }
  }

  it should "nbwr: 2D TRANSPOSE with writeDelay=8 produces correct transposed data" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 8
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x23, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(n, 0),
      srcAddr = 0, dstAddr = 0x8000)
    val (dones, m) = runForagerBee(nbwrCfg, Seq(cmd), mem, maxCycles = 10000,
      modelFactory = nbwrSlaveFactory)
    dones shouldBe Seq((0x23, false))
    for (j <- 0 until n; i <- 0 until n) {
      withClue(s"dst[$j][$i]: ") {
        m(BigInt(0x8000 + j * n + i)) shouldBe (((i * n + j) & 0xFF).toByte)
      }
    }
  }

  it should "nbwr: multi-tile 16x16 TRANSPOSE (tileSize=8, 4 tiles) with writeDelay=8 no deadlock" in {
    // 多 tile 非阻塞转置回归：修复前下一 tile 的 start 脉冲在 sTWR 最后一列突发
    // EOF 拍发出，与 FbTransposeBuffer 同拍消费的最后一个 drain beat 冲突（sBDRAIN
    // 分支 last-connect 吞掉 start）→ 缓冲停 sBIDLE、rd.valid 恒 0 → 死锁。
    // 修复后 start 改由 sTRD 内按 !busy 补发；本用例 4 个 tile 顺序流转，
    // writeDelay=8 下多列写突发同时在途，验证不再死锁且数据正确。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val n = 16 // tileSize=8 → 2×2 共 4 个 tile（M、N 均 > tileSize）
    for (i <- 0 until n; j <- 0 until n) mem(BigInt(i * 16 + j)) = ((i * n + j) & 0xFF).toByte
    val cmd = FbTestCmd(tag = 0x29, transpose = true, dimCount = 2,
      shape = Seq(n, n), srcStride = Seq(n, 16), dstStride = Seq(16, 0),
      srcAddr = 0, dstAddr = 0x8000)
    val (dones, m) = runForagerBee(nbwrCfg, Seq(cmd), mem, maxCycles = 20000,
      modelFactory = nbwrSlaveFactory)
    dones shouldBe Seq((0x29, false))
    for (j <- 0 until n; i <- 0 until n) {
      withClue(s"dst[$j][$i]: ") {
        m(BigInt(0x8000 + j * 16 + i)) shouldBe (((i * n + j) & 0xFF).toByte)
      }
    }
  }

  it should "nbwr: MX quantize FP16→MXE4M3 1D blockSize=32 with writeDelay=8" in {
    // 复用既有 MX 量化用例的命令参数，仅叠加非阻塞写 + 大响应延迟；
    // 量化数据突发与 scale 突发均为在途写（MX 特例路径同样受 wrInFlight 门控）。
    val n = 64
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val vals0 = Seq.tabulate(32)(i =>
      if (i < 8) math.pow(2.0, 10 - i).toFloat else
      if (i < 16) -math.pow(2.0, 10 - i).toFloat else
      if (i < 24) (1.0 + i * 0.25).toFloat * 4.0f else
      if (i == 31) 6.0e-8f else -(1.0 + i * 0.25).toFloat * 2.0f)
    val vals1 = Seq.tabulate(32)(i =>
      if (i % 7 == 0) 0.0f else ((i % 5) + 1).toFloat * (if (i % 3 == 0) -0.5f else 0.25f))
    putFp16(mem, 0, vals0 ++ vals1)
    val srcBits = (vals0 ++ vals1).map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val cmd = FbTestCmd(tag = 0x24, op = "COPY", dimCount = 1,
      shape = Seq(n), srcStride = Seq(n * 2), dstStride = Seq(n),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 1, dstFmt = 4,
      scaleBlockSize = 1) // blockSize=32（量化激活由 dstFmt=4 派生）
    val nbMxCfg = mxqCfg.copy(writeOutstandingDepth = 4)
    val (dones, m) = runForagerBee(nbMxCfg, Seq(cmd), mem, maxCycles = 10000,
      modelFactory = nbwrSlaveFactory)
    dones shouldBe Seq((0x24, false))
    for (blk <- 0 until 2) {
      val (expBytes, expScale) = mxQuantizeRef(srcBits.slice(blk * 32, blk * 32 + 32), 4)
      for (j <- 0 until 32) {
        withClue(s"blk$blk[$j]: ") {
          (m.getOrElse(BigInt(0x1000 + blk * 32 + j), 0.toByte) & 0xFF) shouldBe expBytes(j)
        }
      }
      withClue(s"scale[$blk]: ") {
        (m.getOrElse(BigInt(0x2000 + blk), 0.toByte) & 0xFF) shouldBe expScale
      }
    }
    for (k <- 2 until 16) (m.getOrElse(BigInt(0x2000 + k), 0.toByte) & 0xFF) shouldBe 0
  }

  it should "nbwr: done fires only after all write responses drained (writeDelay=8)" in {
    // done 不早发：512B = 32 beat → 4 个突发，恰好填满深度 4；
    // done.valid 拍回调时采样从设备已发出的响应数，必须等于总突发数。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 512
    fillPattern(mem, 0, len)
    val expBursts = len / (nbwrCfg.beatBytes * nbwrCfg.maxBurstLen)
    val cmd = FbTestCmd(tag = 0x25, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x2000)
    val slaves = Seq.fill(nbwrCfg.numPorts)(
      new FbStreamMemSlave(mem, nbwrCfg.beatBytes, writeDelay = nbWrDelay))
    var idx = 0
    var doneCycle = -1
    var respAtDone = -1
    val (dones, m) = runForagerBee(nbwrCfg, Seq(cmd), mem, maxCycles = 10000,
      modelFactory = (_, _) => { val s = slaves(idx); idx += 1; s },
      onDoneFire = c => if (doneCycle < 0) {
        doneCycle = c
        respAtDone = slaves.map(_.wrRespSent).sum
      })
    dones shouldBe Seq((0x25, false))
    doneCycle should be >= 0
    withClue(s"done@cyc$doneCycle: 已响应 $respAtDone / 应响应 $expBursts —— done 早于写响应排空: ") {
      respAtDone shouldBe expBursts
    }
    for (i <- 0 until len) {
      withClue(s"dst[$i]: ") { m(BigInt(0x2000) + i) shouldBe m(BigInt(i)) }
    }
  }

  it should "nbwr: shared write ports + multi-outstanding, 2 engines concurrent with writeDelay=8" in {
    // enableSharedWritePorts=true + depth=4 + 大延迟：2 引擎并发写不同地址，
    // 验证数据正确 + 响应路由正确（无超时、无错误 done、突发总数守恒）。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 256 // 16 beat → 2 个突发/引擎，共 4 个在途写（恰填满深度 4）
    fillPattern(mem, 0x0000, len, seed = 1)
    fillPattern(mem, 0x2000, len, seed = 2)
    val cmds = Seq(
      FbTestCmd(tag = 0x26, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x0000, dstAddr = 0x1000),
      FbTestCmd(tag = 0x27, dimCount = 1,
        shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
        srcAddr = 0x2000, dstAddr = 0x3000)
    )
    val counters = Seq.fill(nbwrSharedCfg.numPorts)(
      new FbWrCountingMemSlave(mem, nbwrSharedCfg.beatBytes, writeDelay = nbWrDelay))
    var idx = 0
    val (dones, m) = runForagerBee(nbwrSharedCfg, cmds, mem, maxCycles = 10000,
      modelFactory = (_, _) => { val s = counters(idx); idx += 1; s })
    dones.map(_._1).toSet shouldBe Set(0x26, 0x27)
    dones.forall(!_._2) shouldBe true // 无错误 done（含超时前正常完成）
    for (i <- 0 until len) {
      withClue(s"engine-A dst[$i]: ") { m(BigInt(0x1000) + i) shouldBe m(BigInt(0x0000) + i) }
      withClue(s"engine-B dst[$i]: ") { m(BigInt(0x3000) + i) shouldBe m(BigInt(0x2000) + i) }
    }
    val perPort = counters.map(_.wrBursts)
    val expBurstsPerEng = len / (nbwrSharedCfg.beatBytes * nbwrSharedCfg.maxBurstLen)
    withClue(s"per-port burst distribution $perPort: ") {
      perPort.sum shouldBe expBurstsPerEng * 2 // 响应路由守恒：所有突发均被某个物理端口回收
    }
  }

  it should "nbwr: err injected on burst response → done.err=true after all responses drained" in {
    // 对第 2 个突发（EOF 序号 1）的响应注入 err：验证 done.err 在全部响应回收后置位，
    // 且 err done 同样不早于写响应排空。
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val len = 256 // 2 个突发
    fillPattern(mem, 0, len)
    val expBursts = len / (nbwrCfg.beatBytes * nbwrCfg.maxBurstLen)
    val cmd = FbTestCmd(tag = 0x28, dimCount = 1,
      shape = Seq(len), srcStride = Seq(len), dstStride = Seq(len),
      srcAddr = 0, dstAddr = 0x4000)
    val slaves = Seq.fill(nbwrCfg.numPorts)(
      new FbStreamMemSlave(mem, nbwrCfg.beatBytes, writeDelay = nbWrDelay, errOnBurst = 1))
    var idx = 0
    var doneCycle = -1
    var respAtDone = -1
    val (dones, m) = runForagerBee(nbwrCfg, Seq(cmd), mem, maxCycles = 10000,
      modelFactory = (_, _) => { val s = slaves(idx); idx += 1; s },
      onDoneFire = c => if (doneCycle < 0) {
        doneCycle = c
        respAtDone = slaves.map(_.wrRespSent).sum
      })
    dones shouldBe Seq((0x28, true)) // 全部响应回收后 done.err 置位
    withClue(s"err done@cyc$doneCycle: 已响应 $respAtDone / 应响应 $expBursts: ") {
      respAtDone shouldBe expBursts
    }
    // 写数据本身已落盘（响应错误不影响数据写入）
    for (i <- 0 until len) {
      withClue(s"dst[$i]: ") { m(BigInt(0x4000) + i) shouldBe m(BigInt(i)) }
    }
  }

  // ===================== 阶段 E：描述符布局一致性（纯 Scala，无需仿真） =====================

  // 该用例不经 simulate，直接在 Scala 侧运行：验证 serializeDescriptor（软件打包）与
  // FbDescLayout（硬件 extract 偏移单一真源）逐字段一致——守住 v3 布局契约。
  // v3：已删除 cvtScale/cvtZeroPoint（量化参数改由 ForagerBee 顶层 IO 全局供给），
  //     dstFmt 为最后一个序列化字段，maxDims=4/dw=128 时 totalBits 578→514。
  it should "descriptor layout: serializeDescriptor packing matches FbDescLayout offsets (pure Scala)" in {
    val cfg = ForagerBeeConfig(
      numPorts = 1, maxDims = 4, dataWidth = 128, addressWidth = 32, enableChaining = true)
    val shp = Seq(16, 8, 4)
    val ss = Seq(BigInt(64), BigInt(256), BigInt(1024))
    val ds = Seq(BigInt(32), BigInt(128), BigInt(512))
    val pv = Seq(2, 0, 1)
    val beats = serializeDescriptor(cfg,
      op = 1, tag = 0xAB, dimCount = 3,
      shape = shp, srcStride = ss, dstStride = ds,
      srcAddr = BigInt(0x1000), dstAddr = BigInt(0x2000),
      permVec = pv, padBefore = Seq(3, 5), padAfter = Seq(7, 9),
      nextDescAddr = BigInt(0x3000), chainMode = true,
      srcFmt = 8, dstFmt = 1)
    // 由 beat 序列（little-endian beat 拼接）重组完整 bit 向量
    var bits = BigInt(0)
    for ((b, i) <- beats.zipWithIndex) bits |= b << (i * cfg.dataWidth)
    val o = FbDescLayout.offsets(cfg)
    def slice(off: Int, w: Int): BigInt = (bits >> off) & ((BigInt(1) << w) - 1)

    slice(o.offOp, FbDescLayout.opBits) shouldBe BigInt(1)
    slice(o.offTag, FbDescLayout.tagBits) shouldBe BigInt(0xAB)
    slice(o.offDimCount, FbDescLayout.dimCountBits) shouldBe BigInt(3)
    for (d <- 0 until cfg.maxDims) {
      slice(o.offShape + d * 16, 16) shouldBe BigInt(shp.lift(d).getOrElse(0))
      slice(o.offSrcStride + d * cfg.addressWidth, cfg.addressWidth) shouldBe ss.lift(d).getOrElse(BigInt(0))
      slice(o.offDstStride + d * cfg.addressWidth, cfg.addressWidth) shouldBe ds.lift(d).getOrElse(BigInt(0))
      slice(o.offPermVec + d * 3, 3) shouldBe BigInt(pv.lift(d).getOrElse(0))
    }
    slice(o.offSrcAddr, cfg.addressWidth) shouldBe BigInt(0x1000)
    slice(o.offDstAddr, cfg.addressWidth) shouldBe BigInt(0x2000)
    slice(o.offPadBefore, 16) shouldBe BigInt(3)
    slice(o.offPadBefore + 16, 16) shouldBe BigInt(5)
    slice(o.offPadAfter, 16) shouldBe BigInt(7)
    slice(o.offPadAfter + 16, 16) shouldBe BigInt(9)
    slice(o.offNextDescAddr, cfg.addressWidth) shouldBe BigInt(0x3000)
    slice(o.offChainMode, FbDescLayout.chainModeBits) shouldBe BigInt(1)
    slice(o.offSrcFmt, FbDescLayout.srcFmtBits) shouldBe BigInt(8)
    slice(o.offDstFmt, FbDescLayout.dstFmtBits) shouldBe BigInt(1)
    // 布局自检：偏移末端 == totalBits；descBeats 覆盖 totalBits 且与产出一致
    o.end shouldBe FbDescLayout.totalBits(cfg)
    // v3 实算值（maxDims=4、addressWidth=32、dataWidth=128）：
    //   totalBits = 514（v2 为 578，删除 cvtScale/cvtZeroPoint 共 64 bit）
    //   descBeats = ceil(514/128) = 5
    FbDescLayout.totalBits(cfg) shouldBe 514
    o.end shouldBe 514
    o.offDstFmt shouldBe 510
    FbDescLayout.descBeats(cfg) shouldBe 5
    FbDescLayout.descBeats(cfg) shouldBe beats.length
    // v3 尾部无残留字段：dstFmt 之后到 totalBits 之间不应再有内容
    slice(o.end, cfg.dataWidth * beats.length - o.end) shouldBe BigInt(0)
  }

  // v3 描述符尺寸实算表（纯 Scala，不经 simulate ⟹ 可实跑）：锁死 totalBits/descBeats，
  // 任何再次增删序列化字段（如未来为链式 MX 量化补 scaleAddr/scaleBlockSize）都必须显式更新本表。
  // v2 → v3 差值恒为 −64 bit（删除 cvtScale(32) + cvtZeroPoint(32)，量化参数改由顶层 IO 全局供给）。
  it should "descriptor layout v3: totalBits/descBeats match the published table for all shipped configs (pure Scala)" in {
    // (名称, maxDims, dataWidth, v3 totalBits, v2 totalBits, v3 descBeats, v2 descBeats)
    val table = Seq(
      ("factory  dataWidth=256 maxDims=5", 5, 256, 597, 661, 3, 3),
      ("testCfg  dataWidth=128 maxDims=5", 5, 128, 597, 661, 5, 6),
      ("mxqCfg   dataWidth=128 maxDims=5", 5, 128, 597, 661, 5, 6),
      ("chainCfg dataWidth=128 maxDims=4", 4, 128, 514, 578, 5, 5),
      ("cvtCfg   dataWidth=128 maxDims=4", 4, 128, 514, 578, 5, 5)
    )
    for ((name, maxDims, dataWidth, v3Bits, v2Bits, v3Beats, v2Beats) <- table) {
      val cfg = ForagerBeeConfig(numPorts = 1, maxDims = maxDims, dataWidth = dataWidth,
        addressWidth = 32, enableChaining = true)
      withClue(s"$name totalBits(v3): ") { FbDescLayout.totalBits(cfg) shouldBe v3Bits }
      withClue(s"$name totalBits(v3) 应比 v2 少 64 bit: ") { v2Bits - v3Bits shouldBe 64 }
      withClue(s"$name descBeats(v3): ") { FbDescLayout.descBeats(cfg) shouldBe v3Beats }
      withClue(s"$name descBeats(v2)=ceil(v2Bits/dataWidth): ") {
        v2Beats shouldBe (v2Bits + dataWidth - 1) / dataWidth
      }
      withClue(s"$name descBeats(v3)=ceil(v3Bits/dataWidth): ") {
        v3Beats shouldBe (v3Bits + dataWidth - 1) / dataWidth
      }
      // 布局自检：Offsets.end 必须恰等于 totalBits（FbDescLayout 内部 require 亦覆盖，此处显式断言）
      withClue(s"$name offsets.end: ") { FbDescLayout.offsets(cfg).end shouldBe v3Bits }
      // dstFmt 为 v3 最后一个字段
      withClue(s"$name offDstFmt: ") {
        FbDescLayout.offsets(cfg).offDstFmt shouldBe v3Bits - FbDescLayout.dstFmtBits
      }
    }
  }

  // ===================== 阶段 E：格式派生校验用例（非法编码 / INT4 约束 / 未实现转换 / MX 回归） =====================
  // N7：非法枚举编码（6/15）经 pokeCmdPayload 的 port.srcFmt.poke(BigInt(v)) 注入。已核实
  //     ChiselSim 7.13 的 TestableEnum.poke(BigInt) 直接走 simulationPort.set(rawBits)（raw UInt
  //     旁路），不经 encode 的 all.find(...).get 枚举成员校验——越界编码可安全注入，由引擎
  //     fmtEncBad 拒绝。故 pokeCmdPayload 的 BigInt poke 即 raw 注入路径，无需额外改造。

  // 非法格式编码（6=历史废弃、15=未定义）→ fmtEncBad（无条件，不受 enableConversion 门控）
  for (badFmt <- Seq(6, 15)) {
    it should s"reject illegal srcFmt encoding ($badFmt) with err done" in {
      val cmd = FbTestCmd(tag = 0xB0 + badFmt, op = "COPY", dimCount = 1,
        shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
        srcAddr = 0, dstAddr = 0x1000, srcFmt = badFmt, dstFmt = badFmt)
      val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
      dones shouldBe Seq((cmd.tag, true))
    }
  }

  // INT4 奇数 shape(0)：行字节 ceil(33*4/8)=17 非整 beat → cBadBase 行对齐拒绝
  it should "reject INT4 with odd shape(0) (row not beat-aligned) with err done" in {
    val cmd = FbTestCmd(tag = 0xB1, op = "COPY", dimCount = 1,
      shape = Seq(33), srcStride = Seq(17), dstStride = Seq(17),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 10, dstFmt = 10)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB1, true))
  }

  // INT4 + TRANSPOSE → subByteOpBad（亚字节格式仅允许 COPY）
  it should "reject INT4 with TRANSPOSE (sub-byte only allows COPY) with err done" in {
    val cmd = FbTestCmd(tag = 0xB2, op = "TRANSPOSE", dimCount = 2,
      shape = Seq(32, 32), srcStride = Seq(16, 512), dstStride = Seq(16, 512),
      srcAddr = 0, dstAddr = 0x2000, srcFmt = 10, dstFmt = 10)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB2, true))
  }

  // INT4 + padding → int4PadBad（互斥）
  it should "reject INT4 with padding (mutually exclusive) with err done" in {
    val cmd = FbTestCmd(tag = 0xB3, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x1000, padBefore = Seq(1, 0),
      srcFmt = 10, dstFmt = 10)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB3, true))
  }

  // INT4 COPY 正例：shape(0)=32 → 16B=1 beat（beatBytes=16），纯搬运 srcFmt==dstFmt → 数据正确
  it should "INT4 COPY positive: shape(0)=32 (16B=1 beat) transfers correctly" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 16)
    val cmd = FbTestCmd(tag = 0xB4, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 10, dstFmt = 10)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xB4, false))
    for (i <- 0 until 16) m(BigInt(0x1000) + i) shouldBe m(BigInt(i))
  }

  // INT16→FP16（跨格式且 INT16 转换算术未实现）→ cvtFmtBad（需 enableConversion）
  it should "reject INT16→FP16 conversion (unimplemented arithmetic) with err done" in {
    val cmd = FbTestCmd(tag = 0xB5, op = "COPY", dimCount = 1,
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 9, dstFmt = 1)
    val (dones, _) = runForagerBee(cvtCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB5, true))
  }

  // isMx(dstFmt) + 非 float srcFmt（INT8）→ mxQuantBad.srcFmtBadMx（回归守护）
  // 量化激活已由 dstFmt 派生（mxQuantEn 字段已删除），触发路径变为「isMx(dstFmt=4) → srcFmtBadMx」，用例语义不变
  it should "reject MX quantize with non-float srcFmt (INT8) with err done" in {
    val cmd = FbTestCmd(tag = 0xB7, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 3, dstFmt = 4, scaleBlockSize = 1)
    val (dones, _) = runForagerBee(mxqCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB7, true))
  }

  // ===================== 阶段 D：IM2COL / padding 写侧 beat 折算用例 =====================
  // 仿真环境（EDR）恢复后运行；此处先落编译。折算核心断言：done.err=false（beat 数不匹配会死锁/超时）。

  // IM2COL + 格式转换配置：channelIm2col + enableConversion，dataWidth=32（beatBytes=4）
  private val i2cCvtCfg = ForagerBeeConfig(
    numPorts = 1, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 64,
    dataWidth = 32, addressWidth = 32,
    channelIm2col = Seq(true),
    enableConversion = true, converterPipeStages = 1
  )

  // IM2COL FP16→FP32 扩展：micro-row 源 4 FP16=8B=2 beats，目的 4 FP32=16B=4 beats（=源×2）
  it should "im2col folding: FP16→FP32 expansion, dst beats = src × 2 (micro-row)" in {
    val iC = 1; val iH = 1; val iW = 4
    val (kH, kW, sH, sW, pH, pW, dH, dW) = (1, 4, 1, 1, 0, 0, 1, 1)
    val srcVals = Seq(1.0f, 2.0f, 3.0f, 4.0f)
    val mem = mutable.HashMap.empty[BigInt, Byte]
    putFp16(mem, BigInt(0), srcVals)
    val srcBits = srcVals.map(fp16Of)
    val expected = computeIm2col(srcBits, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)
    val cmd = mkIm2colCmd(0xA1, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, 1, 0,
      srcAddr = 0, dstAddr = 0x20000)
    val (dones, m) = runForagerBee(i2cCvtCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0xA1, false))
    // 目的端每元素 FP32（4B）：4 元素 = 16B = 4 beats（源 2 beats << cvtShift(1)）
    for ((e, i) <- expected.zipWithIndex) {
      val f32 = fp16BitsToFp32Bits(e)
      for (b <- 0 until 4) {
        withClue(s"dst FP32[$i] byte$b: ") {
          (m.getOrElse(BigInt(0x20000 + i * 4 + b), 0.toByte) & 0xFF) shouldBe ((f32 >>> (8 * b)) & 0xFF)
        }
      }
    }
  }

  // IM2COL FP32→FP16 压缩：micro-row 源 4 FP32=16B=4 beats，目的 4 FP16=8B=2 beats（=源/2）
  it should "im2col folding: FP32→FP16 compression, dst beats = src / 2 (micro-row)" in {
    val iC = 1; val iH = 1; val iW = 4
    val (kH, kW, sH, sW, pH, pW, dH, dW) = (1, 4, 1, 1, 0, 0, 1, 1)
    val srcVals = Seq(1.0f, 2.0f, 3.0f, 4.0f)
    val mem = mutable.HashMap.empty[BigInt, Byte]
    putFp32(mem, BigInt(0), srcVals)
    val srcBits = srcVals.map(f32bits)
    val expected = computeIm2col(srcBits, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW)
    val cmd = mkIm2colCmd(0xA2, iC, iH, iW, kH, kW, sH, sW, pH, pW, dH, dW, 0, 1,
      srcAddr = 0, dstAddr = 0x20000)
    val (dones, m) = runForagerBee(i2cCvtCfg, Seq(cmd), mem, maxCycles = 50000)
    dones shouldBe Seq((0xA2, false))
    // 目的端每元素 FP16（2B）：4 元素 = 8B = 2 beats（源 4 beats >> cvtShift(1)）
    for ((e, i) <- expected.zipWithIndex) {
      val h = fp16Of(java.lang.Float.intBitsToFloat(e))
      withClue(s"dst FP16[$i]: ") { readElem16(m, BigInt(0x20000 + i * 2)) shouldBe h }
    }
  }

  // padding + FP32→FP16 压缩：输出列宽按 dst 粒度（FP16=2B），padBefore(0)=8 → 16B=1 beat 零 pad
  it should "padding folding: FP32→FP16 compression, output column width by dst granularity" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val row0 = Seq(1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f)
    val row1 = Seq(1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f)
    putFp32(mem, BigInt(0), row0)   // 32 bytes
    putFp32(mem, BigInt(32), row1)  // 32 bytes
    val cmd = FbTestCmd(tag = 0xA3, op = "COPY", dimCount = 2,
      shape = Seq(8, 2), srcStride = Seq(32, 32), dstStride = Seq(32, 32),
      srcAddr = 0, dstAddr = 0x2000,
      padBefore = Seq(8, 0), padAfter = Seq(0, 0),
      srcFmt = 0, dstFmt = 1) // FP32→FP16 压缩
    val (dones, m) = runForagerBee(cvtCfg, Seq(cmd), mem, maxCycles = 10000)
    dones shouldBe Seq((0xA3, false))
    // 每输出行 = 8 FP16 零 pad(16B) + 8 FP16 数据(16B) = 32B（列宽按 dstFmt=FP16 粒度）
    for ((row, r) <- Seq(row0, row1).zipWithIndex) {
      val rowBase = BigInt(0x2000 + r * 32)
      for (p <- 0 until 16) {
        withClue(s"row$r pad[$p]: ") { (m.getOrElse(rowBase + p, (-1).toByte) & 0xFF) shouldBe 0 }
      }
      for ((v, i) <- row.zipWithIndex) {
        withClue(s"row$r data[$i]: ") { readElem16(m, rowBase + 16 + i * 2) shouldBe fp16Of(v) }
      }
    }
  }

  // ===================== Task#50 评审修复新增用例（M3/S1/S2/N6/M1/S3） =====================
  // 仿真环境（EDR）恢复后运行；此处先落编译。err 用例核心断言：done.err=true。

  // M3-a：INT4 + TILE2LINEAR → expander 入口自守（dimCount:=0）→ cBadBase err。
  // 关键：op 被 expander 改写为 COPY，引擎 subByteOpBad 无法回溯识别，唯一拦截点为 expander 自守。
  it should "reject INT4 with TILE2LINEAR (expander self-guard) with err done" in {
    val cmd = FbTestCmd(tag = 0xC0, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, srcFmt = 10, dstFmt = 10,
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 16,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC0, true))
  }

  // M3-b：T2L 且 srcFmt≠dstFmt（FP32→FP16）→ expander 入口自守拒绝（单粒度 e 不支持跨格式）。
  // 用 enableConversion 配置以隔离 expander 自守（否则 noCvtDiffBad 会先拦截，无法验证 M3）。
  it should "reject TILE2LINEAR with srcFmt!=dstFmt (expander self-guard) with err done" in {
    val cmd = FbTestCmd(tag = 0xC1, op = "TILE2LINEAR", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x10000, srcFmt = 0, dstFmt = 1,
      t2lMatCols = 32, t2lTileRows = 16, t2lTileCols = 16,
      t2lNumTileRows = 2, t2lNumTileCols = 2)
    val (dones, _) = runForagerBee(cvtCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC1, true))
  }

  // S1：INT4 shape(0)=31（奇数）→ ceil(31*4/8)=16B 恰整 beat，cBadBase 行对齐漏网；
  // int4ShapeBad（dim0 元素数必须偶数）兜住 → err。
  it should "reject INT4 with odd shape(0)=31 (half-byte leak, int4ShapeBad) with err done" in {
    val cmd = FbTestCmd(tag = 0xC2, op = "COPY", dimCount = 1,
      shape = Seq(31), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 10, dstFmt = 10)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC2, true))
  }

  // S2：TRANSPOSE + FP32→FP16（不经 converter）→ cvtOpBad err（需 enableConversion）。
  it should "reject TRANSPOSE with srcFmt!=dstFmt (no converter path) with err done" in {
    val cmd = FbTestCmd(tag = 0xC3, op = "TRANSPOSE", dimCount = 2,
      shape = Seq(8, 8), srcStride = Seq(32, 256), dstStride = Seq(32, 256),
      srcAddr = 0, dstAddr = 0x2000, srcFmt = 0, dstFmt = 1)
    val (dones, _) = runForagerBee(cvtCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC3, true))
  }

  // N6：padding dim0 字节数非 beat 对齐（INT8 padBefore(0)=1 → 1B，BB=16）→ padAlignBad err。
  it should "reject padding with non-beat-aligned dim0 pad bytes (padAlignBad) with err done" in {
    val cmd = FbTestCmd(tag = 0xC4, op = "COPY", dimCount = 2,
      shape = Seq(16, 1), srcStride = Seq(16, 16), dstStride = Seq(48, 48),
      srcAddr = 0, dstAddr = 0x6000, padBefore = Seq(1, 0), padAfter = Seq(0, 0),
      srcFmt = 3, dstFmt = 3)
    val (dones, _) = runForagerBee(testCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC4, true))
  }

  // M1：IM2COL 压缩方向非对齐（kW=1 FP32→FP16，BB=4 → micro-row 源 1 beat，折算 1>>1=0）
  // → i2cCvtAlignBad err（否则 wr.len 下溢 255、FSM 死锁）。
  it should "reject IM2COL compression with non-aligned micro-row beats (i2cCvtAlignBad) with err done" in {
    val cmd = mkIm2colCmd(0xC5, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1,
      srcAddr = 0, dstAddr = 0x20000)
    val (dones, _) = runForagerBee(i2cCvtCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xC5, true))
  }

  // S3：Dispatcher dst bbox 修复的多端口回归（numPorts=2 + enableConversion）。
  private val dispCvtCfg = ForagerBeeConfig(
    numPorts = 2, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 16,
    dataWidth = 128, addressWidth = 32,
    enableConversion = true, converterPipeStages = 1
  )

  // S3-a：FP16→FP32 扩展 + dst 地址重叠于「真实 dst 粒度」范围（cmd2 dst 落在 cmd1 扩展后
  // 32B dst 区间内、但落在旧 src 粒度 16B 误算区间外）→ 修复后 dispatcher 检出冲突，
  // 第二命令被阻塞至第一 done；两命令均应 err=false 完成。
  //
  // 期望值逐字节推演（BB=16，FP16=2B/元素，FP32=4B/元素，内存小端）：
  //   cmd1 src [0x000,0x010) = FP16(1.0..8.0)；dst 真实区间 [0x2000,0x2020)
  //     beat0 [0x2000,0x2010) = FP32(1.0..4.0)（元素 0..3）
  //     beat1 [0x2010,0x2020) = FP32(5.0..8.0)（元素 4..7）
  //   cmd2 src [0x100,0x110) = FP16(10.0..17.0)；dst 真实区间 [0x2010,0x2030)
  //     beat0 [0x2010,0x2020) = FP32(10.0..13.0)（元素 0..3）
  //     beat1 [0x2020,0x2030) = FP32(14.0..17.0)（元素 4..7）
  //   两 dst 区间重叠于 [0x2010,0x2020)。修复后 cmd2 被 WAW 阻塞至 cmd1 done，写序严格
  //   cmd1→cmd2 ⟹ 终态：[0x2000,0x2010)=FP32(1..4)（cmd1 独占前缀，未被覆盖）、
  //   [0x2010,0x2030)=FP32(10..17)（重叠区必为 cmd2 元素 0..3）。
  //   若 bbox 修复被回退（dst 误用 src 粒度 16B ⟹ cmd1 [0x2000,0x2010)、cmd2 [0x2010,0x2020)
  //   判为不重叠）⟹ 两命令并行：cmd1 beat1 落重叠区的时刻晚于 cmd2 beat0，终态变为
  //   FP32(5.0..8.0)，下面的重叠区断言即红 —— 这是本用例的主防线。
  it should "dispatcher dst bbox: FP16->FP32 expand with dst overlap blocks 2nd cmd until 1st done" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val src1 = (0 until 8).map(i => (i + 1).toFloat)    // cmd1 src：1.0..8.0
    val src2 = (0 until 8).map(i => (i + 10).toFloat)   // cmd2 src：10.0..17.0
    putFp16(mem, BigInt(0x0), src1)
    putFp16(mem, BigInt(0x100), src2)
    val cmd1 = FbTestCmd(tag = 0xD0, op = "COPY", dimCount = 1,
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(32),
      srcAddr = 0x0, dstAddr = 0x2000, srcFmt = 1, dstFmt = 0)   // dst 真实区间 [0x2000,0x2020)
    val cmd2 = FbTestCmd(tag = 0xD1, op = "COPY", dimCount = 1,
      shape = Seq(8), srcStride = Seq(16), dstStride = Seq(32),
      srcAddr = 0x100, dstAddr = 0x2010, srcFmt = 1, dstFmt = 0)  // 与 cmd1 真实 dst 重叠
    val (dones, m) = runForagerBee(dispCvtCfg, Seq(cmd1, cmd2), mem, maxCycles = 50000)

    // done 顺序即「cmd2 阻塞至 cmd1 done」的串行化证据：runForagerBee 按 io.done 观测序记录，
    // io.done 为单 Stream（每拍至多一个回执），且 cmd2 的引擎在 chDone(0) 后才被派发 ⟹ 顺序可靠，
    // 故用有序断言而非 toSet（toSet 会抹除串行化语义，回退后仍可能全绿）。
    dones shouldBe Seq((0xD0, false), (0xD1, false))

    /** FP16 源值经硬件扩展后的期望 FP32 小端字节序列（与 FbFormatConverter.fp16ToFp32 位级一致） */
    def expFp32Le(v: Float): Seq[Int] = {
      val b = fp16BitsToFp32Bits(fp16Of(v))
      (0 until 4).map(k => (b >>> (8 * k)) & 0xFF)
    }
    /** FP16 小端字节序列（源区完整性校验用） */
    def expFp16Le(v: Float): Seq[Int] = {
      val h = fp16Of(v)
      Seq(h & 0xFF, (h >>> 8) & 0xFF)
    }

    // cmd1 dst 未被覆盖前缀 [0x2000,0x2010)：元素 0..3 = FP32(1.0..4.0)
    for (i <- 0 until 4; k <- 0 until 4) {
      val addr = BigInt(0x2000 + 4 * i + k)
      withClue(f"cmd1 dst elem$i byte$k @0x${0x2000 + 4 * i + k}%04X: ") {
        (m.getOrElse(addr, 0.toByte) & 0xFF) shouldBe expFp32Le(src1(i))(k)
      }
    }
    // 重叠区 + cmd2 独占后缀 [0x2010,0x2030)：cmd2 元素 0..7 = FP32(10.0..17.0)
    // 其中 [0x2010,0x2020) 同时证明 cmd1 元素 4..7 已被后行的 cmd2 完整覆盖（无残留混写）
    for (j <- 0 until 8; k <- 0 until 4) {
      val addr = BigInt(0x2010 + 4 * j + k)
      withClue(f"cmd2 dst elem$j byte$k @0x${0x2010 + 4 * j + k}%04X: ") {
        (m.getOrElse(addr, 0.toByte) & 0xFF) shouldBe expFp32Le(src2(j))(k)
      }
    }
    // 合并 dst 区间 [0x2000,0x2030) 之外不得有任何写：前缀 [0x1FF0,0x2000) 与后缀 [0x2030,0x2040)
    //   捕获扩展方向的越界写（如误按 2× 扩展比多写 beat、dst 基址下越界/pad 误算）
    for (a <- (0x1FF0 until 0x2000) ++ (0x2030 until 0x2040)) {
      withClue(f"out-of-dst byte @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
    // 源区间完整（DMA 只读 src；dst 写越界/交叉污染会破坏 src 字节）
    for (i <- 0 until 8; k <- 0 until 2) {
      withClue(f"cmd1 src elem$i byte$k: ") {
        (m.getOrElse(BigInt(2 * i + k), 0.toByte) & 0xFF) shouldBe expFp16Le(src1(i))(k)
      }
      withClue(f"cmd2 src elem$i byte$k: ") {
        (m.getOrElse(BigInt(0x100 + 2 * i + k), 0.toByte) & 0xFF) shouldBe expFp16Le(src2(i))(k)
      }
    }
  }

  // S3-b：FP32→FP16 压缩 + dst 地址相邻（cmd2 dst 紧接 cmd1 压缩后 16B dst 区间、无重叠）
  // → 修复后 dispatcher 用 dst 粒度不误判冲突，两命令可并行派发；均应 err=false 完成。
  //
  // 期望值逐字节推演（BB=16，FP32=4B/元素，FP16=2B/元素，内存小端）：
  //   cmd1 src [0x000,0x020) = FP32(1.0..8.0)（2 beat）；压缩后 dst 真实区间 [0x3000,0x3010)
  //     = FP16(1.0..8.0) 共 8×2B = 1 beat
  //   cmd2 src [0x100,0x120) = FP32(10.0..17.0)；dst 真实区间 [0x3010,0x3020)
  //     = FP16(10.0..17.0)
  //   两区间首尾相接（0x3010 为界）、互不重叠 ⟹ 终态 [0x3000,0x3020) 为 32 个连续有效字节，
  //   无空洞、无相互覆盖；[0x3020,0x3030) 不得被任何写触碰（若引擎误按 src 粒度 32B 写 dst，
  //   cmd1 会越界覆盖 cmd2 区间并溢出到 0x3020 之后，此断言即红）。
  //   源值 1.0..17.0 均为 FP16 精确可表示 ⟹ fp32ToFp16(RNE) 结果 = fp16Of(v)，无舍入歧义。
  it should "dispatcher dst bbox: FP32->FP16 compress with adjacent dst dispatches in parallel" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val src1 = (0 until 8).map(i => (i + 1).toFloat)    // cmd1 src：1.0..8.0
    val src2 = (0 until 8).map(i => (i + 10).toFloat)   // cmd2 src：10.0..17.0
    putFp32(mem, BigInt(0x0), src1)
    putFp32(mem, BigInt(0x100), src2)
    val cmd1 = FbTestCmd(tag = 0xD2, op = "COPY", dimCount = 1,
      shape = Seq(8), srcStride = Seq(32), dstStride = Seq(16),
      srcAddr = 0x0, dstAddr = 0x3000, srcFmt = 0, dstFmt = 1)   // dst 真实区间 [0x3000,0x3010)
    val cmd2 = FbTestCmd(tag = 0xD3, op = "COPY", dimCount = 1,
      shape = Seq(8), srcStride = Seq(32), dstStride = Seq(16),
      srcAddr = 0x100, dstAddr = 0x3010, srcFmt = 0, dstFmt = 1)  // 相邻、无重叠
    val (dones, m) = runForagerBee(dispCvtCfg, Seq(cmd1, cmd2), mem, maxCycles = 50000)

    // 并行派发下两引擎 done 同拍竞争，io.done 由 roundRobin 仲裁汇流 ⟹ 回执先后不确定，
    // 保留 toSet（顺序不承载语义）；回归保护由下面的逐字节数据断言承担。
    dones.toSet shouldBe Set((0xD2, false), (0xD3, false))

    /** FP32 源值经硬件压缩后的期望 FP16 小端字节序列（与 FbFormatConverter.fp32ToFp16 位级一致） */
    def expFp16Le(v: Float): Seq[Int] = {
      val h = fp16Of(v)
      Seq(h & 0xFF, (h >>> 8) & 0xFF)
    }

    // cmd1 dst [0x3000,0x3010)：元素 0..7 = FP16(1.0..8.0)
    for (i <- 0 until 8; k <- 0 until 2) {
      withClue(f"cmd1 dst elem$i byte$k @0x${0x3000 + 2 * i + k}%04X: ") {
        (m.getOrElse(BigInt(0x3000 + 2 * i + k), 0.toByte) & 0xFF) shouldBe expFp16Le(src1(i))(k)
      }
    }
    // cmd2 dst [0x3010,0x3020)：元素 0..7 = FP16(10.0..17.0)（紧邻 cmd1，无交叉污染）
    for (j <- 0 until 8; k <- 0 until 2) {
      withClue(f"cmd2 dst elem$j byte$k @0x${0x3010 + 2 * j + k}%04X: ") {
        (m.getOrElse(BigInt(0x3010 + 2 * j + k), 0.toByte) & 0xFF) shouldBe expFp16Le(src2(j))(k)
      }
    }
    // dst 之外不得有任何写：前缀 [0x2FF0,0x3000) 与后缀 [0x3020,0x3030)
    //   后缀 = 回退后误按 src 粒度 32B 写 dst 时的越界尾段（会覆盖 cmd2 区间并溢出）
    //   前缀 = dst 基址下越界（负偏移/pad 误算的典型落点）
    for (a <- (0x2FF0 until 0x3000) ++ (0x3020 until 0x3030)) {
      withClue(f"out-of-dst byte @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
    // 源区间完整（只读；压缩方向 src 32B 不得被 dst 16B 写回覆盖）
    for (i <- 0 until 8; k <- 0 until 4) {
      val b1 = (f32bits(src1(i)) >>> (8 * k)) & 0xFF
      withClue(f"cmd1 src elem$i byte$k: ") {
        (m.getOrElse(BigInt(4 * i + k), 0.toByte) & 0xFF) shouldBe b1
      }
      val b2 = (f32bits(src2(i)) >>> (8 * k)) & 0xFF
      withClue(f"cmd2 src elem$i byte$k: ") {
        (m.getOrElse(BigInt(0x100 + 4 * i + k), 0.toByte) & 0xFF) shouldBe b2
      }
    }
  }

  // ===================== MX scale 侧信道写纳入 Dispatcher bbox（改动 4）=====================

  /** 采集每个 done 的周期号（顺序与 dones 一致），用于串行化时序断言。
    * 弱断言（仅 err=false）对「冲突未被检出、两命令并行」零检出力，故所有 bbox 用例
    * 均以 done 周期差 + 逐字节终态双重断言。 */
  private def runCollectCycles(
      cfg: ForagerBeeConfig,
      cmds: Seq[FbTestCmd],
      prefill: mutable.HashMap[BigInt, Byte],
      maxCycles: Int = 50000
  ): (Seq[(Int, Boolean)], Seq[Int], mutable.HashMap[BigInt, Byte]) = {
    val cyc = mutable.ArrayBuffer[Int]()
    val (dones, m) = runForagerBee(cfg, cmds, prefill, maxCycles = maxCycles,
      onDoneFire = c => cyc += c)
    (dones, cyc.toSeq, m)
  }

  // S4-a：两条 MX 量化命令共用同一 scaleAddr ⟹ scale 区完全重叠，而 src/dst 区互不重叠，
  // 唯一冲突来源是 scale×scale（WAW）。改动前 dispatcher 只看 src/dst bbox，两命令会被并行
  // 派发到 mxqCfg 的 numPorts=2 两个通道，对同一 scale beat 竞争 strb 部分写。
  //
  // 三区间布局逐字节推演（BB=16，FP16=2B/元素，MXE4M3=1B/元素，blockSize=32 ⟹ 2 个 scale 字节/命令）：
  //   cmd1: src [0x0000,0x0080)  dst [0x4000,0x4040)  scale [0x8000,0x8010)
  //   cmd2: src [0x0100,0x0180)  dst [0x5000,0x5040)  scale [0x8000,0x8010)
  //   src×src / src×dst / dst×src / dst×dst 四组均不重叠 ⟹ 检出只能来自 scale×scale。
  it should "dispatcher scale bbox: two MX cmds sharing scaleAddr serialize (scale-vs-scale WAW)" in {
    val vals1 = Seq.tabulate(64)(i => math.pow(2.0, 10 - (i % 8)).toFloat) // maxExp=10 ⟹ scale=137
    val vals2 = Seq.tabulate(64)(i => math.pow(2.0, 3 - (i % 4)).toFloat)  // maxExp=3  ⟹ scale=130
    def mkMem(): mutable.HashMap[BigInt, Byte] = {
      val mm = mutable.HashMap.empty[BigInt, Byte]
      putFp16(mm, BigInt(0x000), vals1)
      putFp16(mm, BigInt(0x100), vals2)
      mm
    }
    val cmd1 = FbTestCmd(tag = 0xE4, op = "COPY", dimCount = 1,
      shape = Seq(64), srcStride = Seq(128), dstStride = Seq(64),
      srcAddr = 0x000, dstAddr = 0x4000, scaleAddr = 0x8000,
      srcFmt = 1, dstFmt = 4, scaleBlockSize = 1)
    // 仅换 tag/src/dst，scaleAddr 保持不变 ⟹ scale 区完全重叠
    val cmd2 = cmd1.copy(tag = 0xE5, srcAddr = 0x100, dstAddr = 0x5000)

    // 自校准基线：cmd2 单独运行的完成周期。串行化后 cmd2 必须在 cmd1 done 之后才开始，
    // 故两命令 done 周期差应 ≈ solo；并行派发时该差值塌缩到 ~0（两通道同时起跑）。
    val (_, soloCyc, _) = runCollectCycles(mxqCfg, Seq(cmd2), mkMem())
    soloCyc.size shouldBe 1
    val solo = soloCyc.head

    val (dones, cyc, m) = runCollectCycles(mxqCfg, Seq(cmd1, cmd2), mkMem())
    // 有序断言：cmd2 的 done 必须晚于 cmd1（toSet 会抹除串行化语义）
    dones shouldBe Seq((0xE4, false), (0xE5, false))
    cyc.size shouldBe 2
    withClue(s"done 周期 cmd1=${cyc(0)} cmd2=${cyc(1)}（差 ${cyc(1) - cyc(0)}），cmd2 单独运行=$solo：" +
      "scale 区未纳入 bbox 时两命令并行派发、周期差塌缩：") {
      (cyc(1) - cyc(0)) should be >= (solo / 2)
    }

    val bits1 = vals1.map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val bits2 = vals2.map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val (exp1a, sc1a) = mxQuantizeRef(bits1.slice(0, 32), 4)
    val (exp1b, sc1b) = mxQuantizeRef(bits1.slice(32, 64), 4)
    val (exp2a, sc2a) = mxQuantizeRef(bits2.slice(0, 32), 4)
    val (exp2b, sc2b) = mxQuantizeRef(bits2.slice(32, 64), 4)
    // 两命令 scale 字节必须不同，否则「后写者胜」断言无检出力
    withClue(s"cmd1 scale=($sc1a,$sc1b) cmd2 scale=($sc2a,$sc2b) 应不同：") {
      (sc1a != sc2a || sc1b != sc2b) shouldBe true
    }

    // 量化数据各自落盘（dst 区不重叠 ⟹ 互不污染，任一侧被对方覆盖即红）
    for (j <- 0 until 32) {
      withClue(s"cmd1 dst blk0[$j] @0x${0x4000 + j}%04X: ") {
        (m.getOrElse(BigInt(0x4000 + j), 0.toByte) & 0xFF) shouldBe exp1a(j)
      }
      withClue(s"cmd1 dst blk1[$j] @0x${0x4020 + j}%04X: ") {
        (m.getOrElse(BigInt(0x4020 + j), 0.toByte) & 0xFF) shouldBe exp1b(j)
      }
      withClue(s"cmd2 dst blk0[$j] @0x${0x5000 + j}%04X: ") {
        (m.getOrElse(BigInt(0x5000 + j), 0.toByte) & 0xFF) shouldBe exp2a(j)
      }
      withClue(s"cmd2 dst blk1[$j] @0x${0x5020 + j}%04X: ") {
        (m.getOrElse(BigInt(0x5020 + j), 0.toByte) & 0xFF) shouldBe exp2b(j)
      }
    }
    // scale beat 终态必为后写者 cmd2 —— 逐字节主防线：并行派发时该 beat 由两通道竞争写，
    // 终态可能停在 cmd1 的 E8M0 scale（137）而非 cmd2 的（130）
    withClue(s"scale[0] @0x8000 应为 cmd2 的 $sc2a（而非 cmd1 的 $sc1a）: ") {
      (m.getOrElse(BigInt(0x8000), 0.toByte) & 0xFF) shouldBe sc2a
    }
    withClue(s"scale[1] @0x8001 应为 cmd2 的 $sc2b（而非 cmd1 的 $sc1b）: ") {
      (m.getOrElse(BigInt(0x8001), 0.toByte) & 0xFF) shouldBe sc2b
    }
    // strb 部分写：scale beat 内 [0x8002,0x8010) 不得被触碰
    for (k <- 2 until 16) {
      withClue(f"scale beat 未写字节 @0x${0x8000 + k}%04X: ") {
        (m.getOrElse(BigInt(0x8000 + k), 0.toByte) & 0xFF) shouldBe 0
      }
    }
    // dst 区外不得有任何写（scale bbox 上界若误算会导致引擎越界写；此处校验数据侧完整性）
    for (a <- (0x3FF0 until 0x4000) ++ (0x4040 until 0x4050) ++
              (0x4FF0 until 0x5000) ++ (0x5040 until 0x5050) ++
              (0x7FF0 until 0x8000) ++ (0x8010 until 0x8020)) {
      withClue(f"out-of-region byte @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // S4-b：MX 命令的 scale 区与另一条普通 COPY 的 dst 区重叠（交叉场景）。
  //   cmd1: FP16→MXE4M3 64 元素，src [0x0000,0x0080)、dst [0x4000,0x4040)、scale [0x6000,0x6010)
  //   cmd2: INT8 纯拷贝 64 字节，src [0x0200,0x0240)、dst [0x6000,0x6040)
  //   cmd2.src × cmd1.{src,dst,scale} 全不重叠；cmd2.dst × cmd1.{src,dst} 全不重叠；
  //   唯一冲突 = cmd2.dst × cmd1.scale（WAW）⟹ 只有把 scale 区当作真实写区才能检出。
  // 并行派发时 cmd2（短命令）会先写完，随后 cmd1 段末的 sSWRITE 以 strb 部分写覆盖
  // 0x6000/0x6001 两字节为 E8M0 scale ⟹ 逐字节断言与 done 顺序同时变红。
  it should "dispatcher scale bbox: MX scale region vs plain COPY dst region serialize (WAW)" in {
    val vals1 = Seq.tabulate(64)(i => math.pow(2.0, 10 - (i % 8)).toFloat)
    def mkMem(): mutable.HashMap[BigInt, Byte] = {
      val mm = mutable.HashMap.empty[BigInt, Byte]
      putFp16(mm, BigInt(0x000), vals1)
      fillPattern(mm, BigInt(0x200), 64, seed = 77)
      mm
    }
    val cmd1 = FbTestCmd(tag = 0xE6, op = "COPY", dimCount = 1,
      shape = Seq(64), srcStride = Seq(128), dstStride = Seq(64),
      srcAddr = 0x000, dstAddr = 0x4000, scaleAddr = 0x6000,
      srcFmt = 1, dstFmt = 4, scaleBlockSize = 1)
    val cmd2 = FbTestCmd(tag = 0xE7, op = "COPY", dimCount = 1,
      shape = Seq(64), srcStride = Seq(64), dstStride = Seq(64),
      srcAddr = 0x200, dstAddr = 0x6000, srcFmt = 3, dstFmt = 3)

    val (_, soloCyc, _) = runCollectCycles(mxqCfg, Seq(cmd2), mkMem())
    val solo = soloCyc.head

    val (dones, cyc, m) = runCollectCycles(mxqCfg, Seq(cmd1, cmd2), mkMem())
    // 串行化 ⟹ cmd2 必在 cmd1 之后完成。并行时 cmd2（1 beat×4 的纯拷贝）远快于 cmd1
    // （含量化器流水 + sSWRITE），done 顺序会反转为 (0xE7, 0xE6) —— 本断言即检出点
    dones shouldBe Seq((0xE6, false), (0xE7, false))
    withClue(s"done 周期 cmd1=${cyc(0)} cmd2=${cyc(1)}（差 ${cyc(1) - cyc(0)}），cmd2 单独运行=$solo：") {
      (cyc(1) - cyc(0)) should be >= (solo / 2)
    }
    // 串行化 ⟹ cmd2 的整 beat 写（strb 全 1）落在 cmd1 的 scale 部分写之后，
    // [0x6000,0x6040) 终态必逐字节等于 cmd2 源数据；前 2 字节是最敏感的检出位
    for (i <- 0 until 64) {
      withClue(f"cmd2 dst[$i] @0x${0x6000 + i}%04X（并行时被 cmd1 的 E8M0 scale 覆盖）: ") {
        (m.getOrElse(BigInt(0x6000 + i), 0.toByte) & 0xFF) shouldBe
          (m.getOrElse(BigInt(0x200 + i), 0.toByte) & 0xFF)
      }
    }
    // cmd1 侧完整性：量化数据与 scale 各自就位（scale 已被 cmd2 覆盖，故只校验 dst）
    val bits1 = vals1.map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val (exp1a, _) = mxQuantizeRef(bits1.slice(0, 32), 4)
    val (exp1b, _) = mxQuantizeRef(bits1.slice(32, 64), 4)
    for (j <- 0 until 32) {
      withClue(s"cmd1 dst blk0[$j]: ") {
        (m.getOrElse(BigInt(0x4000 + j), 0.toByte) & 0xFF) shouldBe exp1a(j)
      }
      withClue(s"cmd1 dst blk1[$j]: ") {
        (m.getOrElse(BigInt(0x4020 + j), 0.toByte) & 0xFF) shouldBe exp1b(j)
      }
    }
    // 源区完整（只读）
    for (i <- 0 until 64; k <- 0 until 2) {
      withClue(f"cmd1 src elem$i byte$k: ") {
        (m.getOrElse(BigInt(2 * i + k), 0.toByte) & 0xFF) shouldBe
          ((fp16Of(vals1(i)) >>> (8 * k)) & 0xFF)
      }
    }
  }

  // S4-c（MUST-FIX 2）：RAW src×in-flight scale —— 新命令的读区落在 in-flight MX 命令的 scale
  // 侧信道写区间内（FbDispatcher 约 L221）。改动前该判据缺失 ⟹ 新命令被并行派发、在 cmd1 尚未
  // 写完 scale beat 时抢读该区间（读到 0 而非 E8M0 scale 终值），done 顺序亦反转为 (cmd2, cmd1)。
  //   cmd1: FP16→MXE4M3 64 元素，src [0x0000,0x0080)、dst [0x4000,0x4040)、scale [0x8000,0x8010)
  //   cmd2: INT8 纯拷贝 16 字节，src [0x8000,0x8010)（= cmd1 scale 区）、dst [0x9000,0x9010)
  //   cmd2.src×cmd1.{src,dst} 不重叠；cmd2 非 MX ⟹ WAR/WAW 均不触发；唯一冲突 = cmd2.src × cmd1.scale（RAW）
  it should "dispatcher scale bbox: plain COPY src reading in-flight MX scale region serializes (RAW src-vs-scale)" in {
    val vals1 = Seq.tabulate(64)(i => math.pow(2.0, 10 - (i % 8)).toFloat) // 两块 maxExp=10 ⟹ scale=137
    def mkMem(): mutable.HashMap[BigInt, Byte] = {
      val mm = mutable.HashMap.empty[BigInt, Byte]
      putFp16(mm, BigInt(0x000), vals1)
      mm
    }
    val cmd1 = FbTestCmd(tag = 0xE8, op = "COPY", dimCount = 1,
      shape = Seq(64), srcStride = Seq(128), dstStride = Seq(64),
      srcAddr = 0x000, dstAddr = 0x4000, scaleAddr = 0x8000,
      srcFmt = 1, dstFmt = 4, scaleBlockSize = 1)
    val cmd2 = FbTestCmd(tag = 0xE9, op = "COPY", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(16),
      srcAddr = 0x8000, dstAddr = 0x9000, srcFmt = 3, dstFmt = 3)

    // 自校准基线：cmd2 单独运行的完成周期
    val (_, soloCyc, _) = runCollectCycles(mxqCfg, Seq(cmd2), mkMem())
    soloCyc.size shouldBe 1
    val solo = soloCyc.head

    val (dones, cyc, m) = runCollectCycles(mxqCfg, Seq(cmd1, cmd2), mkMem())
    // 有序断言：串行化 ⟹ cmd1（慢，含量化器+sSWRITE）先 done、cmd2 后 done；
    // 并行时 cmd2（1 beat 纯拷贝）远快于 cmd1 ⟹ 顺序反转为 (0xE9,0xE8) —— 本断言即检出点
    dones shouldBe Seq((0xE8, false), (0xE9, false))
    cyc.size shouldBe 2
    withClue(s"done 周期 cmd1=${cyc(0)} cmd2=${cyc(1)}（差 ${cyc(1) - cyc(0)}），cmd2 单独运行=$solo：" +
      "scale 区未纳入 RAW 时两命令并行派发、周期差塌缩：") {
      (cyc(1) - cyc(0)) should be >= (solo / 2)
    }
    // cmd1 写入的两个 E8M0 scale 字节（block0/block1）
    val bits1 = vals1.map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val (_, sc1a) = mxQuantizeRef(bits1.slice(0, 32), 4)
    val (_, sc1b) = mxQuantizeRef(bits1.slice(32, 64), 4)
    // 关键字节互异：scale 终值必须非 0，否则与「并行抢读到未写区间（0）」不可区分 ⟹ 断言零检出力
    withClue(s"cmd1 scale=($sc1a,$sc1b) 应非 0（否则 RAW 抢读 0 与串行读终值不可区分）：") {
      (sc1a != 0 && sc1b != 0) shouldBe true
    }
    // 逐字节主防线：串行化 ⟹ cmd2 读到的必是 cmd1 写完的 scale 终值（并行抢读则为 0）
    withClue(s"cmd2 dst[0] @0x9000 应为 cmd1 scale 终值 $sc1a（并行抢读则为 0）: ") {
      (m.getOrElse(BigInt(0x9000), 0.toByte) & 0xFF) shouldBe sc1a
    }
    withClue(s"cmd2 dst[1] @0x9001 应为 cmd1 scale 终值 $sc1b: ") {
      (m.getOrElse(BigInt(0x9001), 0.toByte) & 0xFF) shouldBe sc1b
    }
    // scale beat 内 [0x8002,0x8010) 未被 cmd1 的 strb 部分写覆盖 ⟹ cmd2 拷出应为 0
    for (k <- 2 until 16) {
      withClue(f"cmd2 dst[$k] @0x${0x9000 + k}%04X（scale strb 未覆盖区应为 0）: ") {
        (m.getOrElse(BigInt(0x9000 + k), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // S4-d（MUST-FIX 2）：WAR new scale×in-flight src —— 新 MX 命令的 scale 写区落在 in-flight
  // 纯拷贝命令的读区内（FbDispatcher 约 L224）。改动前该判据缺失 ⟹ 两命令并行，cmd2 的 scale
  // strb 写会覆盖 cmd1 尚未读到的源 beat，污染 cmd1 搬运结果；且 cmd2（小命令）先完成 ⟹ 顺序反转。
  //   cmd1: INT8 纯拷贝 256 字节（长命令），src [0x0200,0x0300)、dst [0x4000,0x4100)
  //   cmd2: FP16→MXE4M3 16 元素（短命令），src [0x0000,0x0020)、dst [0x5000,0x5010)、scale [0x0280,0x0290)
  //   scaleAddr=0x280 落在 cmd1 源区中段（偏移 128）；cmd2.{src,dst}×cmd1.{src,dst} 不重叠；
  //   cmd1 非 MX ⟹ RAW/WAW 均不触发；唯一冲突 = cmd2.scale × cmd1.src（WAR）
  it should "dispatcher scale bbox: new MX scale region vs in-flight COPY src serializes (WAR scale-vs-src)" in {
    val vals2 = Seq.tabulate(16)(i => math.pow(2.0, 5 - (i % 8)).toFloat) // maxExp=5 ⟹ scale=132
    def origSrc(k: Int): Int = (k * 7 + 34) & 0xFF // fillPattern(seed=1) 在源区偏移 k 的原值
    def mkMem(): mutable.HashMap[BigInt, Byte] = {
      val mm = mutable.HashMap.empty[BigInt, Byte]
      putFp16(mm, BigInt(0x000), vals2)
      fillPattern(mm, BigInt(0x200), 256, seed = 1) // cmd1 源区
      mm
    }
    val cmd1 = FbTestCmd(tag = 0xEA, op = "COPY", dimCount = 1,
      shape = Seq(256), srcStride = Seq(256), dstStride = Seq(256),
      srcAddr = 0x200, dstAddr = 0x4000, srcFmt = 3, dstFmt = 3)
    val cmd2 = FbTestCmd(tag = 0xEB, op = "COPY", dimCount = 1,
      shape = Seq(16), srcStride = Seq(32), dstStride = Seq(16),
      srcAddr = 0x000, dstAddr = 0x5000, scaleAddr = 0x280,
      srcFmt = 1, dstFmt = 4, scaleBlockSize = 0)

    val (_, soloCyc, _) = runCollectCycles(mxqCfg, Seq(cmd2), mkMem())
    soloCyc.size shouldBe 1
    val solo = soloCyc.head

    val (dones, cyc, m) = runCollectCycles(mxqCfg, Seq(cmd1, cmd2), mkMem())
    // 串行化 ⟹ cmd1 先 done、cmd2 后 done；并行时 cmd2（16 元素量化）远快于 cmd1（256 字节）
    // ⟹ 顺序反转为 (0xEB,0xEA) —— 本断言即检出点
    dones shouldBe Seq((0xEA, false), (0xEB, false))
    withClue(s"done 周期 cmd1=${cyc(0)} cmd2=${cyc(1)}（差 ${cyc(1) - cyc(0)}），cmd2 单独运行=$solo：") {
      (cyc(1) - cyc(0)) should be >= (solo / 2)
    }
    // cmd2 写入 0x280 的 E8M0 scale 终值
    val bits2 = vals2.map(v => fp16BitsToFp32Bits(fp16Of(v)))
    val (_, sc2) = mxQuantizeRef(bits2, 4)
    // 关键字节互异：cmd1 源区被 scale 覆盖处（偏移 128）的原值必须与 sc2 不同，
    // 否则「并行时 cmd2 覆盖 cmd1 源」不会改变 cmd1.dst ⟹ WAR 数据断言零检出力
    withClue(s"cmd1 src[128]=${origSrc(128)} 应与 cmd2 scale=$sc2 互异：") {
      (origSrc(128) != sc2) shouldBe true
    }
    // 逐字节主防线：串行化 ⟹ cmd1 在 cmd2 覆盖源区之前已读完整源 ⟹ cmd1.dst 必逐字节等于源区原值
    for (k <- 0 until 256) {
      withClue(f"cmd1 dst[$k] @0x${0x4000 + k}%04X（并行时被 cmd2 的 scale 写污染源区，尤以 k=128 最敏感）: ") {
        (m.getOrElse(BigInt(0x4000 + k), 0.toByte) & 0xFF) shouldBe origSrc(k)
      }
    }
    // 命令结束后 cmd2 的 scale 已覆盖源区偏移 128（印证 WAR 确为真冲突：scale 落点就在 cmd1 源区内）
    withClue(s"源区 0x280 终态应为 cmd2 scale $sc2: ") {
      (m.getOrElse(BigInt(0x280), 0.toByte) & 0xFF) shouldBe sc2
    }
  }

  // ===================== mxNonQuantBad / MX 裸拷贝边界（改动 1 回归）=====================

  // mxNonQuantBad 收紧为「srcFmt 为 MX 族即拒」（硬件无反量化器）。该判据此前无测试覆盖。
  // src=MXE4M3(4)、dst=FP32(0)：唯一触发项是 mxNonQuantBad —— fmtEncBad（编码均合法）、
  // cvtFmtBad（非 INT16/INT32/亚字节）、cvtOpBad（op=COPY）、mxQuantBad（isMx(dstFmt)=false）均不触发。
  it should "reject srcFmt=MX with non-MX dstFmt (dequantize unsupported) with err done" in {
    val cmd = FbTestCmd(tag = 0xB8, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(32), dstStride = Seq(128),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 4, dstFmt = 0)
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB8, true))
    // cBad ⟹ FSM 直接 IDLE→DONE，不得产生任何写
    for (a <- 0x1000 until 0x1080) {
      withClue(f"err 命令不应写 @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // MX↔MX（src=MXE4M3、dst=MXE5M2）在 enableConversion=true 下双重拒绝：
  // mxNonQuantBad（isMx(srcFmt)）与 mxQuantBad.srcFmtBadMx（!isFloat(srcFmt)）同时命中。
  it should "reject MX->MX copy under enableConversion with err done" in {
    val cmd = FbTestCmd(tag = 0xB9, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 4, dstFmt = 5)
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mutable.HashMap.empty)
    dones shouldBe Seq((0xB9, true))
    for (a <- 0x1000 until 0x1020) {
      withClue(f"err 命令不应写 @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // CONSIDER-1：dstFmt=MXE4M3 + scaleBlockSize=3（非法编码，仅 0/1/2 合法）⟹ scaleBlockBadEncoding。
  // 该判据由改动⑤从 shapeMisalign 中抽出为独立前置项；此前 Spec 中 scaleBlockSize 仅取 0/1/2、
  // 从未激活该判据。若被后续重构静默合并回 shapeMisalign（除数回落为默认 16 后 shape(0)=32 整除
  // ⟹ 漏判为合法），本用例即红。其余参数均合法（srcFmt=FP16 为 float、shape(0)=32 为默认 blockSize=16
  // 整数倍、scaleAddr beat 对齐、op=COPY、无 padding）⟹ 唯一触发项是 scaleBlockBadEncoding。
  it should "reject MX quantize with illegal scaleBlockSize=3 (scaleBlockBadEncoding) with err done" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    putFp16(mem, BigInt(0x0), Seq.tabulate(32)(i => (i + 1).toFloat))
    val cmd = FbTestCmd(tag = 0xBC, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(64), dstStride = Seq(32),
      srcAddr = 0x0, dstAddr = 0x1000, scaleAddr = 0x2000,
      srcFmt = 1, dstFmt = 4, scaleBlockSize = 3)
    val (dones, m) = runForagerBee(mxqCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xBC, true))
    // cBad ⟹ FSM 直接 IDLE→DONE，dst 与 scale 区均不得产生任何写
    for (a <- 0x1000 until 0x1040) {
      withClue(f"err 命令不应写 dst @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
    for (a <- 0x2000 until 0x2010) {
      withClue(f"err 命令不应写 scale @0x$a%04X: ") {
        (m.getOrElse(BigInt(a), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // 反向守卫：enableConversion=false 的构建中无量化器，MX 编码退化为 1 字节/元素裸格式，
  // srcFmt==dstFmt==MXE4M3 必须是合法纯字节拷贝。守住 mxQuantActive / mxNonQuantBad 的
  // cfg.enableConversion 编译期门控未泄漏（泄漏则本用例被误判为 err）。
  it should "allow MX->MX plain byte copy when enableConversion=false (no quantizer)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0, 32, seed = 91)
    val cmd = FbTestCmd(tag = 0xBA, op = "COPY", dimCount = 1,
      shape = Seq(32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = 0, dstAddr = 0x1000, srcFmt = 4, dstFmt = 4)
    val (dones, m) = runForagerBee(testCfg, Seq(cmd), mem)
    dones shouldBe Seq((0xBA, false))
    for (i <- 0 until 32) {
      withClue(s"MX 裸拷贝 dst[$i]: ") {
        (m.getOrElse(BigInt(0x1000 + i), 0.toByte) & 0xFF) shouldBe
          (m.getOrElse(BigInt(i), 0.toByte) & 0xFF)
      }
    }
  }

  // ===================== 链式描述符 + isMx(dstFmt) 入口自守（改动 2）=====================

  // 链式 + 转换双开配置：此前测试中不存在该组合，而出厂 RTL（ForagerBeeElaborate）恰为此组合，
  // 故 isMx(dstFmt) 链式描述符的内存破坏路径在出厂构建中是活的。
  private val chainCvtCfg = ForagerBeeConfig(
    numPorts = 1, queueDepth = 4, maxDims = 4,
    tileSize = 8, maxBurstLen = 8,
    dataWidth = 128, addressWidth = 32,
    enableChaining = true,
    enableConversion = true, converterPipeStages = 1
  )

  // 安全关键用例：链式描述符不序列化 scaleAddr/scaleBlockSize，FbChainController 将其硬连线为 0。
  // 量化激活派生为 isMx(dstFmt) 后，若不拦截，带 MX dstFmt 的描述符会真的走量化通路并向
  // **物理地址 0x0** 发出 E8M0 scale 的 strb 部分写（len=0 单拍）—— 内存破坏。
  // 入口自守：isMx(dstFmt) ⟹ injCmd.dimCount := 0 ⟹ 引擎 cBadBase 拒绝 ⟹ err done、链终止。
  it should "chain: reject descriptor with isMx(dstFmt) via entry self-guard (no write to 0x0)" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val chainMem = mutable.HashMap.empty[BigInt, Byte]
    // 物理地址 0x0 附近预置哨兵：入口自守失效时被 E8M0 scale 部分写破坏，逐字节即检出
    for (i <- 0 until 64) mem(BigInt(i)) = 0xA5.toByte
    fillPattern(mem, 0x1000, 64, seed = 41)

    // 描述符（v3 布局，dstFmt 为最后一个序列化字段）：COPY + srcFmt=FP16(1)、dstFmt=MXE4M3(4)
    val descAddr = BigInt(0x10000)
    val descBeats = serializeDescriptor(chainCvtCfg,
      op = 0, tag = 0x61, dimCount = 1,
      shape = Seq(32), srcStride = Seq(64), dstStride = Seq(32),
      srcAddr = BigInt(0x3000), dstAddr = BigInt(0x5000),
      nextDescAddr = 0, chainMode = true,
      srcFmt = 1, dstFmt = 4)
    writeDescToMem(chainMem, descAddr, descBeats, chainCvtCfg.beatBytes)

    // 首命令：普通 INT8 纯拷贝（chainMode=true，指向上述描述符）
    val cmd = FbTestCmdChain(tag = 0x60, dimCount = 1,
      shape = Seq(64), srcStride = Seq(64), dstStride = Seq(64),
      srcAddr = 0x1000, dstAddr = 0x2000,
      chainMode = true, nextDescAddr = descAddr)

    val (dones, m) = runForagerBeeChain(chainCvtCfg, Seq(cmd), mem, chainMem)
    // 注入的 MX 描述符被 cBadBase（dimCount=0）拒绝 ⟹ 链在 err 处终止，chainLen=2（首命令 + 注入命令）
    dones shouldBe Seq((0x60, true, 2))
    // 首命令本身已正常搬运（err 来自第二个链节点）
    for (i <- 0 until 64) {
      withClue(s"首命令 dst[$i] @0x${0x2000 + i}%04X: ") {
        (m.getOrElse(BigInt(0x2000 + i), 0.toByte) & 0xFF) shouldBe
          (m.getOrElse(BigInt(0x1000 + i), 0.toByte) & 0xFF)
      }
    }
    // 内存破坏防线：scaleAddr 硬连线为 0 ⟹ 哨兵 beat 必须逐字节保持 0xA5。
    // 入口自守被移除时 sSWRITE 会以 strb 部分写覆盖其中若干字节（E8M0 scale 值），本断言即红。
    for (i <- 0 until 64) {
      withClue(f"scale 写目标哨兵 @0x$i%04X（入口自守失效则被 E8M0 部分写破坏）: ") {
        (m.getOrElse(BigInt(i), 0.toByte) & 0xFF) shouldBe 0xA5
      }
    }
    // 注入描述符的 dst 区不得被写（命令在 sIDLE 即被判 err，FSM 直落 sDONE）
    for (i <- 0 until 64) {
      withClue(f"注入描述符 dst @0x${0x5000 + i}%04X: ") {
        (m.getOrElse(BigInt(0x5000 + i), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // MUST-FIX 1 正例：enableChaining=true + enableConversion=false（chainCfg）下，链式描述符携带
  // srcFmt==dstFmt==MXE4M3 必须是合法纯字节拷贝（copy-only 构建中 MX 退化为 1 字节/元素裸格式）。
  // 改动前入口自守无条件生成 ⟹ 注入的 MX 描述符被 dimCount=0 误拒并截断整条链（err=true、dst 全 0）；
  // 改动后自守受 cfg.enableConversion 门控 ⟹ copy-only 构建不生成，链式 MX 裸拷贝放行、整条链无 err，
  // 与经 io.cmd 直连路径（sPASS 原样透传）行为对称。这是此前无任何用例覆盖的 false+chain+MX 组合。
  it should "chain: allow MX->MX plain byte copy descriptor when enableConversion=false" in {
    val mem = mutable.HashMap.empty[BigInt, Byte]
    val chainMem = mutable.HashMap.empty[BigInt, Byte]
    fillPattern(mem, 0x1000, 32, seed = 11)  // 首命令源区（INT8 纯拷贝）
    fillPattern(mem, 0x3000, 32, seed = 22)  // 注入描述符源区（MX 裸字节）

    // 描述符（v3 布局）：COPY + srcFmt=MXE4M3(4)、dstFmt=MXE4M3(4) —— copy-only 构建下为合法裸拷贝
    val descAddr = BigInt(0x10000)
    val descBeats = serializeDescriptor(chainCfg,
      op = 0, tag = 0x65, dimCount = 1,
      shape = Seq(32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = BigInt(0x3000), dstAddr = BigInt(0x5000),
      nextDescAddr = 0, chainMode = true,
      srcFmt = 4, dstFmt = 4)
    writeDescToMem(chainMem, descAddr, descBeats, chainCfg.beatBytes)

    // 首命令：INT8 纯拷贝（chainMode=true，指向上述 MX 描述符）
    val cmd = FbTestCmdChain(tag = 0x64, dimCount = 1,
      shape = Seq(32), srcStride = Seq(32), dstStride = Seq(32),
      srcAddr = 0x1000, dstAddr = 0x2000,
      chainMode = true, nextDescAddr = descAddr)

    val (dones, m) = runForagerBeeChain(chainCfg, Seq(cmd), mem, chainMem)
    // 入口自守受 enableConversion 门控 ⟹ copy-only 构建不生成 ⟹ MX 描述符放行、整条链无 err、chainLen=2
    //（改动前该 MX 描述符被 dimCount=0 误拒 ⟹ err=true，本断言即红）
    dones shouldBe Seq((0x64, false, 2))
    // 首命令搬运正确
    for (i <- 0 until 32) {
      withClue(s"首命令 dst[$i] @0x${0x2000 + i}%04X: ") {
        (m.getOrElse(BigInt(0x2000 + i), 0.toByte) & 0xFF) shouldBe
          (m.getOrElse(BigInt(0x1000 + i), 0.toByte) & 0xFF)
      }
    }
    // 注入的 MX 裸拷贝描述符逐字节搬运正确（改动前被误拒 ⟹ dst 全 0）
    for (i <- 0 until 32) {
      withClue(s"MX 描述符 dst[$i] @0x${0x5000 + i}%04X（改动前被 dimCount=0 误拒则全 0）: ") {
        (m.getOrElse(BigInt(0x5000 + i), 0.toByte) & 0xFF) shouldBe
          (m.getOrElse(BigInt(0x3000 + i), 0.toByte) & 0xFF)
      }
    }
    // copy-only 构建无量化器 ⟹ 物理地址 0x0 附近不得有任何 scale 写
    for (i <- 0 until 16) {
      withClue(f"copy-only 构建不应写 0x0 区 @0x$i%04X: ") {
        (m.getOrElse(BigInt(i), 0.toByte) & 0xFF) shouldBe 0
      }
    }
  }

  // ===================== 顶层 IO 量化参数穿透链的非默认值动态覆盖（SHOULD-FIX 3）=====================

  // SHOULD-FIX 3a：既有全部用例 pokeCvtGlobal 恒为 (0,0)，若分发循环误写为只接 engines(0)、
  // 或 cfg_scale/cfg_zeroPoint 被接反，测试仍全绿。本用例经 ForagerBee.io.cvtScale/cvtZeroPoint
  // 显式驱动非零值，按 FbFormatConverter.int8ToFp32 实际算术逐元素断言。
  // 期望值推导（INT8→FP32 反量化，dispCvtCfg）：shift=scale(4,0)=2、zp=zeroPoint(7,0)=3；
  //   elem i：srcByte b_i ⟹ diff=b_i-3（选 b_i≥8 保证 diff≥0，规避硬件对负 diff 的 32-bit 符号扩展语义）
  //   ⟹ shifted=diff<<2 ⟹ FP32=float(shifted)（整数 <2^24 精确可表示）。
  //   接反（scale↔zp）会算成 (b_i-2)<<5，与期望不同 ⟹ 断言即检出；(0,0) 会算成 b_i，亦不同。
  it should "drive non-zero cvtScale/cvtZeroPoint through top IO: INT8->FP32 dequant per-element" in {
    val srcBytes = (0 until 16).map(i => 8 + i * 2) // 8,10,...,38（互异、正数、均 > zp=3）
    val mem = mutable.HashMap.empty[BigInt, Byte]
    srcBytes.zipWithIndex.foreach { case (b, i) => mem(BigInt(i)) = b.toByte }
    val cmd = FbTestCmd(tag = 0xF0, op = "COPY", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(64),
      srcAddr = 0x0, dstAddr = 0x2000, srcFmt = 3, dstFmt = 0)
    val (dones, m) = runForagerBee(dispCvtCfg, Seq(cmd), mem, cvtScale = 2, cvtZeroPoint = 3)
    dones shouldBe Seq((0xF0, false))
    // 逐元素：FP32((b-3)<<2) 小端 4 字节
    for (i <- 0 until 16) {
      val shifted = (srcBytes(i) - 3) << 2
      val expBits = f32bits(shifted.toFloat)
      for (k <- 0 until 4) {
        withClue(f"dst elem$i byte$k @0x${0x2000 + 4 * i + k}%04X（期望 FP32($shifted)）: ") {
          (m.getOrElse(BigInt(0x2000 + 4 * i + k), 0.toByte) & 0xFF) shouldBe ((expBits >>> (8 * k)) & 0xFF)
        }
      }
    }
    // 检出力自检：非零 (scale,zp) 结果必须与 (0,0) 及接反 (scale↔zp) 均不同，否则上面断言零检出力
    val expCorrect = srcBytes.map(b => f32bits(((b - 3) << 2).toFloat))
    val expZero    = srcBytes.map(b => f32bits(b.toFloat))                 // (0,0)：value=b
    val expSwapped = srcBytes.map(b => f32bits(((b - 2) << 5).toFloat))     // 接反：shift=5、zp=2
    withClue("非零量化参数结果应与 (0,0) 不同：") { expCorrect should not be expZero }
    withClue("非零量化参数结果应与接反 (scale↔zp) 不同：") { expCorrect should not be expSwapped }
  }

  // SHOULD-FIX 3b：锁定「量化参数分发到全部引擎」而非只接 engines(0)。numPorts=2 下先发一条长命令
  // 占住 channel 0，迫使转换命令落到 channel 1；若分发循环只接 engines(0)，ch1 的 converter 读到
  // 未驱动/0 的 cfg_scale ⟹ 反量化结果退化为 (b-0)<<0=b，与期望 (b-3)<<2 不同 ⟹ 断言即检出。
  it should "deliver global cvtScale/cvtZeroPoint to channel 1 (not only engines(0))" in {
    val srcBytes = (0 until 16).map(i => 8 + i * 2)
    val mem = mutable.HashMap.empty[BigInt, Byte]
    srcBytes.zipWithIndex.foreach { case (b, i) => mem(BigInt(i)) = b.toByte }
    fillPattern(mem, 0x8000, 256, seed = 5) // cmd0 源区（长命令，占住 ch0）
    // cmd0：256 字节 INT8 纯拷贝（16 beat），先派发占住 channel 0
    val cmd0 = FbTestCmd(tag = 0xF1, op = "COPY", dimCount = 1,
      shape = Seq(256), srcStride = Seq(256), dstStride = Seq(256),
      srcAddr = 0x8000, dstAddr = 0xC000, srcFmt = 3, dstFmt = 3)
    // cmd1：INT8→FP32 转换，地址与 cmd0 完全不相交 ⟹ ch0 忙时并行派发到 channel 1
    val cmd1 = FbTestCmd(tag = 0xF2, op = "COPY", dimCount = 1,
      shape = Seq(16), srcStride = Seq(16), dstStride = Seq(64),
      srcAddr = 0x0, dstAddr = 0x2000, srcFmt = 3, dstFmt = 0)
    val (dones, m) = runForagerBee(dispCvtCfg, Seq(cmd0, cmd1), mem,
      maxCycles = 50000, cvtScale = 2, cvtZeroPoint = 3)
    // 两命令并行派发（地址不相交）⟹ done 先后由 roundRobin 仲裁不定，用 toSet
    dones.toSet shouldBe Set((0xF1, false), (0xF2, false))
    // cmd1 落 channel 1：其 dst 必按全局 (scale=2,zp=3) 反量化 —— 只接 engines(0) 时此处退化即红
    for (i <- 0 until 16) {
      val shifted = (srcBytes(i) - 3) << 2
      val expBits = f32bits(shifted.toFloat)
      for (k <- 0 until 4) {
        withClue(f"ch1 dst elem$i byte$k @0x${0x2000 + 4 * i + k}%04X（期望 FP32($shifted)）: ") {
          (m.getOrElse(BigInt(0x2000 + 4 * i + k), 0.toByte) & 0xFF) shouldBe ((expBits >>> (8 * k)) & 0xFF)
        }
      }
    }
    // cmd0 纯拷贝完整性（占位命令本身也须正确）
    for (i <- 0 until 256) {
      withClue(f"cmd0 dst[$i] @0x${0xC000 + i}%04X: ") {
        (m.getOrElse(BigInt(0xC000 + i), 0.toByte) & 0xFF) shouldBe
          (m.getOrElse(BigInt(0x8000 + i), 0.toByte) & 0xFF)
      }
    }
  }

  // 非仿真冒烟：链式+转换双开（出厂 RTL 组合）与 MX 量化多通道组合均可发射。
  // 这两条覆盖本次新增逻辑的 elaboration 面：FbChainController 的 isMx(dstFmt) 入口自守、
  // FbDispatcher 的 scale 第三区间（仅 enableConversion 时生成）、FbEngine 的 mxQuantActive 派生。
  it should "elaborate enableChaining+enableConversion (factory combination) and mxqCfg (numPorts=2)" in {
    for (cfg <- Seq(chainCvtCfg, mxqCfg, dispCvtCfg)) {
      val v = _root_.circt.stage.ChiselStage.emitSystemVerilog(new ForagerBee(cfg))
      v should include("module ForagerBee")
      // 顶层量化参数端口应作为输入出现在 RTL 中（enableConversion=true ⟹ Option 为 Some）
      v should include("cvtScale")
      v should include("cvtZeroPoint")
    }
  }

  // 反向：copy-only 构建（enableConversion=false）不得声明量化参数输入端口（无悬空引脚）
  it should "not expose cvtScale/cvtZeroPoint ports when enableConversion=false" in {
    val v = _root_.circt.stage.ChiselStage.emitSystemVerilog(new ForagerBee(testCfg))
    v should include("module ForagerBee")
    v should not include ("cvtScale")
    v should not include ("cvtZeroPoint")
    // CONSIDER-2：copy-only 构建不得实例化量化器/转换器模块。出厂四套 RTL 全为 enableConversion=true，
    // 本批唯一真正改变行为的 false 构建从未发射为交付产物 ⟹ 在 elaborate 层锁定其结构裁剪。
    // （未驱动这些模块时 Chisel 不发射对应 `module`，故模块名字符串不应出现）
    v should not include ("FbMxQuantizer")
    v should not include ("FbFormatConverter")
  }
}

