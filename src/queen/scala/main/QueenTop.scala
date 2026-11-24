import chisel3._
import chisel3.util._

/** Queen 顶层：前端分流 + 每端点控制器与派发 adapter + SCHED 同步执行与寄存器 + 同步状态与故障记录。
  *
  * completion 绕过 adapter 直接进入本级：Queen 无条件接收，只消费 hwSignal 与 error/code，
  * pc/cmdTag/swTag 原样带回但暂不消费（trace 协议未定）。
  * QRD 在前端 accept 时入在途表，三个来源的响应按程序序退休。
  * 完成通知不走 Queen：设备用自己的中断块通知 CPU。
  */
class QueenTop(val cfg: QueenConfig = QueenConfig()) extends Module {
    val io = IO(new Bundle {
        val inst = Vec(cfg.instructionLanes, Flipped(Decoupled(new QueenIssue(cfg))))
        val rs1 = Vec(cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val rs2 = Vec(cfg.instructionLanes, Flipped(Valid(UInt(cfg.xlen.W))))
        val trap = Valid(new QueenTrap(cfg))
        val asyncRd = Decoupled(new QueenAsyncRd(cfg))
        val qrdRetire = Decoupled(new QueenRetire(cfg))
        val gemm = Decoupled(Vec(cfg.nativeWidth, Valid(new QueenNativeCmd(cfg))))
        val dma = Decoupled(Vec(cfg.nativeWidth, Valid(new QueenNativeCmd(cfg))))
        val gemmResp = Flipped(Decoupled(new QueenDsaRegResp))
        val dmaResp = Flipped(Decoupled(new QueenDsaRegResp))
        val gemmCompletion = Flipped(Valid(new QueenDsaCompletion(cfg)))
        val dmaCompletion = Flipped(Valid(new QueenDsaCompletion(cfg)))
        val gemmWrErr = Input(Vec(cfg.nativeWidth, Bool()))
        val dmaWrErr = Input(Vec(cfg.nativeWidth, Bool()))
        val gemmDeviceIdle = Input(Bool())
        val dmaDeviceIdle = Input(Bool())
        val memReq = Decoupled(new QueenMemReq(cfg))
        val memResp = Flipped(Decoupled(new QueenMemResp))
        val flushReq = Input(Bool())
        val flushAck = Output(Bool())
        val gemmFlushReq = Output(Bool())
        val gemmFlushAck = Input(Bool())
        val dmaFlushReq = Output(Bool())
        val dmaFlushAck = Input(Bool())
        val idle = Output(Bool())
        val events = Output(Vec(cfg.eventGroupCount, UInt(32.W)))
        val timelines = Output(Vec(cfg.timelineCount, UInt(32.W)))
        val faultValid = Output(Bool())
        val faultKind = Output(UInt(2.W))
        val faultEndpoint = Output(UInt(2.W))
        val faultCode = Output(UInt(cfg.faultCodeWidth.W))
        val faultIrq = Output(Bool())
        val irq = Output(Bool())
    })

    private val decoder = Module(new QueenDsaDecoder(cfg))
    private val gemmCtrl = Module(new QueenEndpointCtrl(cfg))
    private val dmaCtrl = Module(new QueenEndpointCtrl(cfg))
    private val gemmAdapter = Module(new QueenDsaAdapter(cfg))
    private val dmaAdapter = Module(new QueenDsaAdapter(cfg))
    private val schedCtrl = Module(new QueenSchedCtrl(cfg))
    private val sync = Module(new QueenSyncState(cfg))
    private val qrd = Module(new QueenQrdTracker(cfg))
    private val mem = Module(new QueenMemMaster(cfg))
    private val flush = Module(new QueenFlushCtrl(cfg.flushQuietCycles))

    decoder.io.inst <> io.inst
    decoder.io.rs1 <> io.rs1
    decoder.io.rs2 <> io.rs2
    io.trap <> decoder.io.trap
    decoder.io.qrdUsed := qrd.io.used
    qrd.io.alloc := decoder.io.qrdAlloc
    decoder.io.flushing := flush.io.flushing
    decoder.io.flushStart := flush.io.start
    qrd.io.markDummy := flush.io.start
    qrd.io.flushing := flush.io.flushing
    io.asyncRd <> qrd.io.asyncRd
    io.qrdRetire <> qrd.io.retire
    qrd.io.resp(0) <> io.gemmResp
    qrd.io.resp(1) <> io.dmaResp
    qrd.io.resp(2) <> schedCtrl.io.resp

    gemmCtrl.io.in <> decoder.io.gemm
    dmaCtrl.io.in <> decoder.io.dma
    gemmAdapter.io.in <> gemmCtrl.io.out
    dmaAdapter.io.in <> dmaCtrl.io.out
    io.gemm <> gemmAdapter.io.out
    io.dma <> dmaAdapter.io.out
    schedCtrl.io.in <> decoder.io.scheduler
    schedCtrl.io.memReq <> mem.io.req
    io.memReq <> mem.io.memReq
    mem.io.memResp <> io.memResp
    schedCtrl.io.rdData := mem.io.rdData
    schedCtrl.io.memUsed := mem.io.used
    mem.io.drop := flush.io.flushing

    gemmCtrl.io.events := sync.io.events
    gemmCtrl.io.timelines := sync.io.timelines
    dmaCtrl.io.events := sync.io.events
    dmaCtrl.io.timelines := sync.io.timelines
    gemmCtrl.io.backendIdle := gemmAdapter.io.idle
    dmaCtrl.io.backendIdle := dmaAdapter.io.idle
    gemmCtrl.io.flushing := flush.io.flushing
    dmaCtrl.io.flushing := flush.io.flushing
    gemmAdapter.io.flushing := flush.io.flushing
    dmaAdapter.io.flushing := flush.io.flushing
    schedCtrl.io.flushing := flush.io.flushing

    gemmAdapter.io.deviceIdle := io.gemmDeviceIdle
    dmaAdapter.io.deviceIdle := io.dmaDeviceIdle
    private val dispatchIdle = decoder.io.idle &&
        gemmCtrl.io.idle && gemmAdapter.io.idle && dmaCtrl.io.idle && dmaAdapter.io.idle
    schedCtrl.io.idleIn := dispatchIdle
    io.idle := dispatchIdle && schedCtrl.io.idle && qrd.io.idle && mem.io.idle && flush.io.idle

    flush.io.trig := schedCtrl.io.flushTrig
    flush.io.cpuReq := io.flushReq
    io.flushAck := flush.io.cpuAck
    io.gemmFlushReq := flush.io.devReq(0)
    io.dmaFlushReq := flush.io.devReq(1)
    flush.io.devAck(0) := io.gemmFlushAck
    flush.io.devAck(1) := io.dmaFlushAck
    flush.io.drained := dispatchIdle && schedCtrl.io.idle && qrd.io.idle && mem.io.idle
    // flush 对 CPU 是异步的：静默窗内任何新指令、迟到 completion 或总线响应都重新计数。
    flush.io.disturb := io.inst.map(_.fire).reduce(_ || _) ||
        io.gemmCompletion.valid || io.dmaCompletion.valid || io.memResp.fire

    // flush 期间到达的 completion 属于旧 context，丢弃，不置同步状态也不记 fault。
    private val gemmCompValid = io.gemmCompletion.valid && !flush.io.flushing
    private val dmaCompValid = io.dmaCompletion.valid && !flush.io.flushing
    sync.io.completion(0).valid := gemmCompValid
    sync.io.completion(0).bits := io.gemmCompletion.bits
    sync.io.completion(1).valid := dmaCompValid
    sync.io.completion(1).bits := io.dmaCompletion.bits
    sync.io.clear := flush.io.clearState
    sync.io.sw := schedCtrl.io.sw
    io.events := sync.io.events
    io.timelines := sync.io.timelines
    schedCtrl.io.events := sync.io.events
    schedCtrl.io.timelines := sync.io.timelines

    private val faultValid = RegInit(false.B)
    private val faultKind = RegInit(0.U(2.W))
    private val faultEndpoint = RegInit(0.U(2.W))
    private val faultCode = RegInit(0.U(cfg.faultCodeWidth.W))

    // kind: 0=设备执行错误（code 透传设备私有码）1=配置写非法地址 2=timeline 显式回退
    //       3=polling/atomic 内存错误（code 0=总线错误 1=地址未对齐）
    // endpoint: 0=GEMM 1=DMA 3=SCHED
    private case class FaultSrc(cond: Bool, kind: Int, ep: Int, code: UInt)
    private val srcs = Seq(
        FaultSrc(gemmCompValid && io.gemmCompletion.bits.error, 0, 0,
            io.gemmCompletion.bits.code),
        FaultSrc(dmaCompValid && io.dmaCompletion.bits.error, 0, 1,
            io.dmaCompletion.bits.code),
        FaultSrc(io.gemmWrErr.asUInt.orR, 1, 0, 0.U(cfg.faultCodeWidth.W)),
        FaultSrc(io.dmaWrErr.asUInt.orR, 1, 1, 0.U(cfg.faultCodeWidth.W)),
        FaultSrc(schedCtrl.io.wrErr.asUInt.orR, 1, 3, 0.U(cfg.faultCodeWidth.W)),
        FaultSrc(schedCtrl.io.addrErr || mem.io.err, 3, 3,
            Mux(schedCtrl.io.addrErr, 1.U, 0.U(cfg.faultCodeWidth.W))),
        FaultSrc(sync.io.rollback, 2, 3, sync.io.rollbackId.pad(cfg.faultCodeWidth)))
    private val sel = VecInit(srcs.map(_.cond))
    private val idx = PriorityEncoder(sel)
    private def field(get: FaultSrc => UInt): UInt =
        MuxLookup(idx, 0.U)(srcs.zipWithIndex.map { case (s, i) => i.U -> get(s) })

    // 只保留最新一条；同拍多源按固定优先级取一条。新错误优先于清除。
    when(sel.asUInt.orR) {
        faultValid := true.B
        faultKind := field(s => s.kind.U(2.W))
        faultEndpoint := field(s => s.ep.U(2.W))
        faultCode := field(_.code)
    }.elsewhen(flush.io.clearState) {
        faultValid := false.B
    }.elsewhen(schedCtrl.io.faultClear) {
        faultValid := false.B
    }
    io.faultValid := faultValid
    io.faultKind := faultKind
    io.faultEndpoint := faultEndpoint
    io.faultCode := faultCode
    io.faultIrq := faultValid
    io.irq := faultValid
    schedCtrl.io.faultValid := faultValid
    schedCtrl.io.faultKind := faultKind
    schedCtrl.io.faultEndpoint := faultEndpoint
    schedCtrl.io.faultCode := faultCode
}