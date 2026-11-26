import chisel3._
import chisel3.util._

/** Queen 硬件接口约束 7.1 的硬件完成信号目标：start 当拍组合给出，由设备自行采样并随 completion 回传。 */
class QueenHwSignal extends Bundle {
    val enable = Bool()
    val isTimeline = Bool()
    val group = UInt(5.W)
    val mask = UInt(32.W)
    val timeline = UInt(3.W)
}

/** 地址窗口固定，寄存器数量可配。中断按 IntrRFMS4 顺序、0x60 对齐。 */
object QueenDsaRegIfAddr {
    val CfgBase = 0x00
    val StatusBase = 0x40
    val IntrRaw = 0x60
    val IntrForce = 0x64
    val IntrMask = 0x68
    val IntrStatus = 0x6C
    val HwSignalTarget = 0x80
    val HwSignalMask = 0x81
    val TraceTag = 0xA0
    val SpaceLimit = 0x400
}

case class QueenDsaRegIfConfig(
    numCfgRegs: Int = 64,
    numStatusRegs: Int = 32,
    intrWidth: Int = 32,
    qcfg: QueenConfig = QueenConfig()
) {
    require(numCfgRegs >= 1 && numCfgRegs <= 64,
        s"QueenDsaRegIf: numCfgRegs($numCfgRegs) 须落在 1..64（QWR2 的 6-bit 窗口）")
    require(numStatusRegs >= 0 && numStatusRegs <= 32,
        s"QueenDsaRegIf: numStatusRegs($numStatusRegs) 须落在 0..32")
    require(intrWidth >= 1 && intrWidth <= 32,
        s"QueenDsaRegIf: intrWidth($intrWidth) 须落在 1..32")

    def lanes: Int = qcfg.nativeWidth
}

class QueenDsaRegResp extends Bundle {
    val data = UInt(32.W)
    val err = Bool()
}

/** DSA 侧通用寄存器接口：直接消费 Queen 的并行 native beat。
  *
  * 一个 ready 表达全部流控：写 beat 恒可收，RUN beat 取 startReady，READ beat 取 resp.ready。
  * 写不占 resp 通道，错误走 per-lane wrErr pulse；resp 只承载 QRD 数据。
  * shadow 是软件视角副本（QRD 读它），live 在 RUN 当拍整组 dump 后输出给 DSA。
  * 中断更新方程与 utils/bus/regif/Interrupts.scala 一致，4 lane 的清除/置位先各自合并。
  */
class QueenDsaRegIf(cfg: QueenDsaRegIfConfig) extends Module {
    private val A = QueenDsaRegIfAddr
    private val lanes = cfg.lanes
    private val intrW = cfg.intrWidth

    val io = IO(new Bundle {
        val cmd = Flipped(Decoupled(Vec(lanes, Valid(new QueenNativeCmd(cfg.qcfg)))))
        val resp = Decoupled(new QueenDsaRegResp)
        val wrErr = Output(Vec(lanes, Bool()))
        val cfgRegs = Output(Vec(cfg.numCfgRegs, UInt(32.W)))
        val statusRegs = Input(Vec(cfg.numStatusRegs, UInt(32.W)))
        val intrSrc = Input(UInt(intrW.W))
        val irq = Output(Bool())
        val start = Output(Bool())
        val startReady = Input(Bool())
        val startSwTag = Output(UInt(cfg.qcfg.xlen.W))
        val hwSignal = Output(new QueenHwSignal)
    })

    private val shadow = RegInit(VecInit(Seq.fill(cfg.numCfgRegs)(0.U(32.W))))
    private val live = RegInit(VecInit(Seq.fill(cfg.numCfgRegs)(0.U(32.W))))
    private val intrRaw = RegInit(0.U(intrW.W))
    private val intrMask = RegInit(((BigInt(1) << intrW) - 1).U(intrW.W))
    private val sigTarget = RegInit(0.U(32.W))
    private val sigMask = RegInit(0.U(32.W))
    private val traceTag = RegInit(0.U(32.W))

    io.cfgRegs := live
    io.hwSignal.enable := sigTarget(0)
    io.hwSignal.isTimeline := sigTarget(1)
    io.hwSignal.group := sigTarget(6, 2)
    io.hwSignal.timeline := sigTarget(9, 7)
    io.hwSignal.mask := sigMask
    io.irq := (intrRaw & ~intrMask).orR

    private val lane = io.cmd.bits
    private val isWr = VecInit(lane.map(l =>
        l.valid && (l.bits.op === QueenNativeOp.WRITE1 || l.bits.op === QueenNativeOp.WRITE2)))
    private val isRun = VecInit(lane.map(l => l.valid && l.bits.op === QueenNativeOp.RUN))
    private val isRd = VecInit(lane.map(l => l.valid && l.bits.op === QueenNativeOp.READ))
    private val hasRun = isRun.asUInt.orR
    private val hasRd = isRd.asUInt.orR

    io.cmd.ready := Mux(hasRun, io.startReady, Mux(hasRd, io.resp.ready, true.B))
    private val accept = io.cmd.fire

    assert(!(hasRun || hasRd) || PopCount(VecInit(lane.map(_.valid))) === 1.U,
        "QueenDsaRegIf: QCTRL/QRD must occupy a beat alone")

    private def addr(v: Int): UInt = v.U(10.W)

    private def vecAt(v: Vec[UInt], idx: UInt, n: Int): UInt =
        if (n == 0) 0.U(32.W)
        else if (n == 1) Mux(idx === 0.U, v(0), 0.U(32.W))
        else Mux(idx < n.U, v(idx(log2Ceil(n) - 1, 0)), 0.U(32.W))

    private def cfgWrite(addr: UInt, data: UInt): Unit =
        if (cfg.numCfgRegs == 1) when(addr === 0.U) { shadow(0) := data }
        else when(addr < cfg.numCfgRegs.U) {
            shadow(addr(log2Ceil(cfg.numCfgRegs) - 1, 0)) := data
        }

    private def wrLegal(a: UInt): Bool =
        a < cfg.numCfgRegs.U || a === A.IntrRaw.U || a === A.IntrForce.U ||
            a === A.IntrMask.U || a === A.HwSignalTarget.U || a === A.HwSignalMask.U ||
            a === A.TraceTag.U

    private def rdLegal(a: UInt): Bool =
        a < cfg.numCfgRegs.U ||
            (a >= A.StatusBase.U && a < (A.StatusBase + cfg.numStatusRegs).U) ||
            a === A.IntrRaw.U || a === A.IntrForce.U || a === A.IntrMask.U ||
            a === A.IntrStatus.U || a === A.HwSignalTarget.U || a === A.HwSignalMask.U ||
            a === A.TraceTag.U

    private def readReg(a: UInt): UInt = Mux(a < cfg.numCfgRegs.U,
        vecAt(shadow, a, cfg.numCfgRegs),
        Mux(a >= A.StatusBase.U && a < (A.StatusBase + cfg.numStatusRegs).U,
            vecAt(io.statusRegs, a - A.StatusBase.U, cfg.numStatusRegs),
            MuxLookup(a, 0.U(32.W))(Seq(
                addr(A.IntrRaw) -> intrRaw.pad(32),
                addr(A.IntrForce) -> intrRaw.pad(32),
                addr(A.IntrMask) -> intrMask.pad(32),
                addr(A.IntrStatus) -> (intrRaw & ~intrMask).pad(32),
                addr(A.HwSignalTarget) -> sigTarget,
                addr(A.HwSignalMask) -> sigMask,
                addr(A.TraceTag) -> traceTag))))

    private val wrOk = VecInit(lane.map { l =>
        wrLegal(l.bits.reg0Addr) &&
            (l.bits.op === QueenNativeOp.WRITE1 || wrLegal(l.bits.reg1Addr))
    })
    for (j <- 0 until lanes) {
        io.wrErr(j) := accept && isWr(j) && !wrOk(j)
    }

    // W1C/W1S 必须跨 lane 合并而非 last-wins，故先收集再归约。
    private val clrTerms = scala.collection.mutable.ArrayBuffer.empty[(Bool, UInt)]
    private val setTerms = scala.collection.mutable.ArrayBuffer.empty[(Bool, UInt)]

    private def writeReg(a: UInt, d: UInt, en: Bool): Unit = {
        when(en) {
            cfgWrite(a, d)
            when(a === A.IntrMask.U) { intrMask := d(intrW - 1, 0) }
            when(a === A.HwSignalTarget.U) { sigTarget := d }
            when(a === A.HwSignalMask.U) { sigMask := d }
            when(a === A.TraceTag.U) { traceTag := d }
        }
        clrTerms += ((en && a === A.IntrRaw.U, d))
        setTerms += ((en && a === A.IntrForce.U, d))
    }

    for (j <- 0 until lanes) {
        val b = lane(j).bits
        val en = accept && isWr(j) && wrOk(j)
        writeReg(b.reg0Addr, b.data0, en)
        writeReg(b.reg1Addr, b.data1, en && b.op === QueenNativeOp.WRITE2)
    }

    private def merge(terms: Seq[(Bool, UInt)]): UInt =
        terms.map { case (c, d) => Mux(c, d(intrW - 1, 0), 0.U(intrW.W)) }
            .foldLeft(0.U(intrW.W))(_ | _)

    intrRaw := (intrRaw & ~merge(clrTerms.toSeq)) | merge(setTerms.toSeq) | io.intrSrc

    private def pick[T <: Data](sel: Vec[Bool], get: Int => T, zero: T): T = {
        val out = WireDefault(zero)
        for (j <- (0 until lanes).reverse) {
            when(sel(j)) { out := get(j) }
        }
        out
    }

    private val rdAddr = pick(isRd, j => lane(j).bits.reg0Addr, 0.U(10.W))
    io.resp.valid := accept && hasRd
    io.resp.bits.data := readReg(rdAddr)
    io.resp.bits.err := !rdLegal(rdAddr)

    io.start := accept && hasRun
    // RUN 的 rs2 非 0 时覆盖寄存器值，为 0 表示沿用软件此前写入的 TRACE_TAG。
    private val runSwTag = pick(isRun, j => lane(j).bits.swTag, 0.U(cfg.qcfg.xlen.W))
    io.startSwTag := Mux(runSwTag =/= 0.U, runSwTag, traceTag)

    when(accept && hasRun) {
        live := shadow
    }
}