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

    decoder.io.inst <> io.inst
    decoder.io.rs1 <> io.rs1
    decoder.io.rs2 <> io.rs2
    io.trap <> decoder.io.trap
    decoder.io.qrdUsed := qrd.io.used
    qrd.io.alloc := decoder.io.qrdAlloc
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

    gemmCtrl.io.events := sync.io.events
    gemmCtrl.io.timelines := sync.io.timelines
    dmaCtrl.io.events := sync.io.events
    dmaCtrl.io.timelines := sync.io.timelines
    gemmCtrl.io.backendIdle := gemmAdapter.io.idle
    dmaCtrl.io.backendIdle := dmaAdapter.io.idle

    gemmAdapter.io.deviceIdle := io.gemmDeviceIdle
    dmaAdapter.io.deviceIdle := io.dmaDeviceIdle
    private val dispatchIdle = decoder.io.idle &&
        gemmCtrl.io.idle && gemmAdapter.io.idle && dmaCtrl.io.idle && dmaAdapter.io.idle
    schedCtrl.io.idleIn := dispatchIdle
    io.idle := dispatchIdle && schedCtrl.io.idle && qrd.io.idle

    sync.io.completion(0) := io.gemmCompletion
    sync.io.completion(1) := io.dmaCompletion
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
    // endpoint: 0=GEMM 1=DMA 3=SCHED
    private case class FaultSrc(cond: Bool, kind: Int, ep: Int, code: UInt)
    private val srcs = Seq(
        FaultSrc(io.gemmCompletion.valid && io.gemmCompletion.bits.error, 0, 0,
            io.gemmCompletion.bits.code),
        FaultSrc(io.dmaCompletion.valid && io.dmaCompletion.bits.error, 0, 1,
            io.dmaCompletion.bits.code),
        FaultSrc(io.gemmWrErr.asUInt.orR, 1, 0, 0.U(cfg.faultCodeWidth.W)),
        FaultSrc(io.dmaWrErr.asUInt.orR, 1, 1, 0.U(cfg.faultCodeWidth.W)),
        FaultSrc(schedCtrl.io.wrErr.asUInt.orR, 1, 3, 0.U(cfg.faultCodeWidth.W)),
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