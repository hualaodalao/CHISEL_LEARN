import chisel3._
import chisel3.util._

/** SCHED 侧软件同步写请求：每拍至多一条，由 QueenSchedCtrl 按 lane 序发出。 */
class QueenSyncWrite(val cfg: QueenConfig) extends Bundle {
    val valid = Bool()
    val isTimeline = Bool()
    val index = UInt(5.W)
    val clr = Bool()
    val assign = Bool()
    val data = UInt(32.W)
}

/** Queen 内部同步状态：硬件完成源 + 软件 SIGNAL 指令两个更新源。
  *
  * 两个端点同拍各回一条 completion 时按位或（事件）与计数相加（时间线）合并，不丢任何一条。
  * 同一 event bit 上软件清除与硬件/软件置位同拍冲突时**置位优先**。
  * timeline 显式赋值不得回退（signed 距离为负）：该拍抑制更新并报 rollback。
  */
class QueenSyncState(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val completion = Input(Vec(2, Valid(new QueenDsaCompletion(cfg))))
        val sw = Input(new QueenSyncWrite(cfg))
        val events = Output(Vec(cfg.eventGroupCount, UInt(32.W)))
        val timelines = Output(Vec(cfg.timelineCount, UInt(32.W)))
        val rollback = Output(Bool())
        val rollbackId = Output(UInt(3.W))
    })

    private val events = RegInit(VecInit(Seq.fill(cfg.eventGroupCount)(0.U(32.W))))
    private val timelines = RegInit(VecInit(Seq.fill(cfg.timelineCount)(0.U(32.W))))
    io.events := events
    io.timelines := timelines

    private def hit(c: Valid[QueenDsaCompletion], timeline: Bool): Bool =
        c.valid && c.bits.hwSignal.enable && c.bits.hwSignal.isTimeline === timeline

    private val swEvent = io.sw.valid && !io.sw.isTimeline
    private val swTimeline = io.sw.valid && io.sw.isTimeline

    for (g <- 0 until cfg.eventGroupCount) {
        val hwSet = io.completion.map(c =>
            Mux(hit(c, false.B) && c.bits.hwSignal.group === g.U, c.bits.hwSignal.mask, 0.U(32.W)))
            .reduce(_ | _)
        val sel = swEvent && io.sw.index === g.U
        val swSet = Mux(sel && !io.sw.clr, io.sw.data, 0.U(32.W))
        val swClr = Mux(sel && io.sw.clr, io.sw.data, 0.U(32.W))
        events(g) := (events(g) & ~swClr) | hwSet | swSet
    }

    private val assignSel = VecInit((0 until cfg.timelineCount).map(t =>
        swTimeline && io.sw.assign && io.sw.index(2, 0) === t.U))
    io.rollback := assignSel.asUInt.orR &&
        (io.sw.data - timelines(io.sw.index(2, 0))).asSInt < 0.S
    io.rollbackId := io.sw.index(2, 0)

    for (t <- 0 until cfg.timelineCount) {
        val hwInc = PopCount(VecInit(io.completion.map(c =>
            hit(c, true.B) && c.bits.hwSignal.timeline === t.U)))
        val swInc = swTimeline && !io.sw.assign && io.sw.index(2, 0) === t.U
        val assign = assignSel(t) && !io.rollback
        timelines(t) := Mux(assign, io.sw.data, timelines(t) + hwInc + swInc.asUInt)
    }
}