import chisel3._
import chisel3.util._

/** ForagerBee 顶层 —— Queen 设备（queenHarewareInterface.md 第 13/14 章）。
  *
  * 顶层即 Queen 设备接口（镜像 WorkerBee）：直接例化 Queen 公共模块 QueenDsaRegIf（配置前端：
  * shadow/live、单 ready、wrErr pulse、中断四寄存器、公共契约区 hwSignal/traceTag、start/startSwTag）
  * 与 QueenDsaCompletion（完成回传）；内部例化原始 Stream 核心 FbCore（引擎/Dispatcher 零改动）。
  *
  * 设备侧自研四件：
  *   - startReady（credit）：N=numPorts 个上下文槽，按完成归还（13.7）；RUN 反压而非 err（13.6）。
  *   - 上下文采样：RUN 当拍采样 startSwTag(含 traceTag 沿用)/hwSignal 入槽，槽号写入 FbCmd.tag；
  *     多引擎 done 可乱序，按 tag 取槽随 completion 回传（13.10.4）。live 在 RUN 当拍寄存 dump，
  *     cfgRegs 下一拍才稳定，故命令延迟一拍发射。
  *   - completion：Valid(QueenDsaCompletion)，Queen 无条件接收；error=1 时 hwSignal.enable 强制 0。
  *   - 错误中断：每引擎 done.err 脉冲路由到 regIf.intrSrc（IntrRFMS4 @0x60）；deviceIdle 强 idle 与 flush 四相。
  *
  * 私有配置沿用 0x00..0x22 映射（见 spec 8.3）；状态仅 busy/idle/occupancy，不设 DONE/CREDIT 寄存器。
  */
class ForagerBee(cfg: ForagerBeeConfig = ForagerBeeConfig(),
    qcfg: QueenConfig = QueenConfig()) extends Module {
  require(cfg.addressWidth <= 40,
    s"ForagerBee: addressWidth(${cfg.addressWidth}) 须 <= 40（高 8 位拼槽寄存器 ABI 上限）")
  require(cfg.maxDims <= 5, s"ForagerBee: maxDims(${cfg.maxDims}) 须 <= 5")
  require(cfg.numPorts <= 32, s"ForagerBee: numPorts(${cfg.numPorts}) 须 <= 32（中断位宽上限）")

  private val addrW = cfg.addressWidth
  private val numCfg = 0x23
  private val numStatus = 1
  private val n = cfg.numPorts
  private val intrW = n
  private val rcfg = QueenDsaRegIfConfig(numCfgRegs = numCfg, numStatusRegs = numStatus,
    intrWidth = intrW, qcfg = qcfg)
  private val lanes = qcfg.nativeWidth
  private val slotW = math.max(1, log2Up(n))

  val io = IO(new Bundle {
    val cmd        = Flipped(Decoupled(Vec(lanes, Valid(new QueenNativeCmd(qcfg)))))
    val resp       = Decoupled(new QueenDsaRegResp)
    val wrErr      = Output(Vec(lanes, Bool()))
    val completion = Valid(new QueenDsaCompletion(qcfg))
    val deviceIdle = Output(Bool())
    val flushReq   = Input(Bool())
    val flushAck   = Output(Bool())
    val irq        = Output(Bool())
    val bus        = Vec(cfg.numPorts, master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth)))
  })

  private val fb = Module(new FbCore(cfg))
  // flushReq 期间复位 RegIf（含公共契约区），满足 13.10.5 第 4 条；RegIf 本身无 flush 端口。
  private val regIf = withReset(reset.asBool || io.flushReq) { Module(new QueenDsaRegIf(rcfg)) }

  regIf.io.cmd <> io.cmd
  io.resp <> regIf.io.resp
  io.wrErr := regIf.io.wrErr
  io.irq := regIf.io.irq
  regIf.io.statusRegs(0) := Cat(0.U(14.W), fb.io.occupancy.pad(16), fb.io.idle, fb.io.busy)
  // 每引擎错误完成脉冲 → 中断源（IntrRFMS4 raw 置位，软件 W1C 清除）
  regIf.io.intrSrc := fb.io.errPulse.asUInt
  fb.io.bus <> io.bus

  // --- 上下文槽：credit 按完成归还 ---
  private class Ctx extends Bundle {
    val valid = Bool()
    val swTag = UInt(qcfg.xlen.W)
    val hwTag = UInt(qcfg.xlen.W)
    val hw    = new QueenHwSignal
  }
  private val slots = RegInit(VecInit(Seq.fill(n)(0.U.asTypeOf(new Ctx))))
  private val valids = VecInit(slots.map(_.valid))
  private val hasFree = !valids.asUInt.andR
  private val freeIdx = PriorityEncoder(~valids.asUInt)

  // live 在 RUN 当拍寄存 dump（cfgRegs 下一拍才稳定），故命令延迟一拍发射：
  // RUN 当拍采样上下文入槽并扣 credit，pendingSlot 记住槽号，下一拍读稳定 cfgRegs 组装入队。
  private val pendingValid = RegInit(false.B)
  private val pendingSlot = RegInit(0.U(slotW.W))

  // --- flush 四相：req↑→ack↑→req↓→ack↓ ---
  private val flushing = RegInit(false.B)
  when(io.flushReq && !flushing) {
    flushing := true.B
    slots.foreach(_.valid := false.B) // 已采样未回传副本作废
    pendingValid := false.B           // 未发射的 pending 命令丢弃
  }
  when(flushing && !io.flushReq) { flushing := false.B }
  fb.io.flush := flushing
  io.flushAck := flushing && fb.io.idle

  regIf.io.startReady := hasFree && !pendingValid && fb.io.cmd.ready && !flushing

  // --- 私有配置组装（0x00..0x22，读 live 组 cfgRegs）---
  private def addrOf(lo: UInt, hiByte: UInt): UInt = Cat(hiByte, lo)(addrW - 1, 0)
  private def assemble(b: Vec[UInt], tag: UInt): FbCmd = {
    val c = Wire(new FbCmd(cfg))
    val ctrl = b(0x00); val aux = b(0x02); val hi = b(0x05)
    val hi12 = b(0x15); val hi34 = b(0x16)
    c.op             := ctrl(2, 0).asTypeOf(FbOp())
    c.tag            := tag.pad(8)
    c.dimCount       := ctrl(5, 3)
    c.srcFmt         := ctrl(9, 6).asTypeOf(FbDataFmt())
    c.dstFmt         := ctrl(13, 10).asTypeOf(FbDataFmt())
    c.scaleBlockSize := ctrl(15, 14)
    c.srcAddr        := addrOf(b(0x03), hi(7, 0))
    c.dstAddr        := addrOf(b(0x04), hi(15, 8))
    c.sgListAddr     := addrOf(b(0x06), hi(23, 16))
    c.scaleAddr      := addrOf(b(0x07), hi(31, 24))
    c.sgEntryCount   := aux(15, 0)
    c.padValue       := b(0x19)
    c.padBefore(0)   := b(0x1A)(15, 0)
    c.padBefore(1)   := b(0x1A)(31, 16)
    c.padAfter(0)    := b(0x1B)(15, 0)
    c.padAfter(1)    := b(0x1B)(31, 16)
    c.im2colInShape(0) := b(0x1C)(15, 0)
    c.im2colInShape(1) := b(0x1C)(31, 16)
    c.im2colInShape(2) := b(0x1D)(15, 0)
    c.im2colKernel(0)  := b(0x1D)(23, 16)
    c.im2colKernel(1)  := b(0x1D)(31, 24)
    c.im2colStride(0)  := b(0x1E)(7, 0)
    c.im2colStride(1)  := b(0x1E)(15, 8)
    c.im2colPad(0)     := b(0x1E)(23, 16)
    c.im2colPad(1)     := b(0x1E)(31, 24)
    c.im2colDilation(0) := b(0x1F)(7, 0)
    c.im2colDilation(1) := b(0x1F)(15, 8)
    c.t2lMatCols     := b(0x20)(15, 0)
    c.t2lTileRows    := b(0x20)(31, 16)
    c.t2lTileCols    := b(0x21)(15, 0)
    c.t2lNumTileRows := b(0x21)(31, 16)
    c.t2lNumTileCols := b(0x22)(15, 0)
    val shapes = Seq(b(0x08)(15, 0), b(0x08)(31, 16), b(0x09)(15, 0), b(0x09)(31, 16), b(0x0A)(15, 0))
    val starts = Seq(b(0x0A)(31, 16), b(0x0B)(15, 0), b(0x0B)(31, 16), b(0x0C)(15, 0), b(0x0C)(31, 16))
    val srcLo = Seq(b(0x17), b(0x0D), b(0x0E), b(0x0F), b(0x10))
    val dstLo = Seq(b(0x18), b(0x11), b(0x12), b(0x13), b(0x14))
    val srcHi = Seq(aux(23, 16), hi12(7, 0), hi12(15, 8), hi34(7, 0), hi34(15, 8))
    val dstHi = Seq(aux(31, 24), hi12(23, 16), hi12(31, 24), hi34(23, 16), hi34(31, 24))
    val pv = b(0x01)
    for (d <- 0 until cfg.maxDims) {
      c.shape(d)       := shapes(d)
      c.srcStartIdx(d) := starts(d)
      c.srcStride(d)   := addrOf(srcLo(d), srcHi(d))
      c.dstStride(d)   := addrOf(dstLo(d), dstHi(d))
      c.permVec(d)     := pv(3 * d + 2, 3 * d)
    }
    c
  }

  // --- RUN 当拍采样上下文入槽（扣 credit）；命令延迟一拍发射 ---
  private val start = regIf.io.start
  when(start) {
    slots(freeIdx).valid := true.B
    slots(freeIdx).swTag := regIf.io.startSwTag
    slots(freeIdx).hwTag := freeIdx.pad(qcfg.xlen)
    slots(freeIdx).hw    := regIf.io.hwSignal
    pendingValid := true.B
    pendingSlot  := freeIdx
  }
  // 下一拍 cfgRegs(live) 已稳定，组装命令入队；pending 期间 startReady=0 防止 live 被新 RUN 覆盖
  fb.io.cmd.valid := pendingValid
  fb.io.cmd.payload := assemble(regIf.io.cfgRegs, pendingSlot)
  when(fb.io.cmd.fire) { pendingValid := false.B }

  // --- 完成：按 tag 取槽回传，Queen 无条件接收 ---
  fb.io.done.ready := true.B
  private val doneFire = fb.io.done.fire
  private val doneSlot = fb.io.done.payload.tag(slotW - 1, 0)
  io.completion.valid := doneFire
  io.completion.bits.swTag := slots(doneSlot).swTag
  io.completion.bits.hwTag := slots(doneSlot).hwTag
  io.completion.bits.error := fb.io.done.payload.err
  io.completion.bits.code := Mux(fb.io.done.payload.err, 1.U(qcfg.faultCodeWidth.W), 0.U)
  io.completion.bits.hwSignal := slots(doneSlot).hw
  io.completion.bits.hwSignal.enable := slots(doneSlot).hw.enable && !fb.io.done.payload.err
  when(doneFire) { slots(doneSlot).valid := false.B }

  io.deviceIdle := fb.io.idle && !valids.asUInt.orR
}