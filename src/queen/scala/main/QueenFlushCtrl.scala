import chisel3._
import chisel3.util._

/** Flush 控制器：三个触发源汇聚成一次双向四相 req/ack 恢复流程。
  *
  * 触发源：SCHED `0x01 RECOVER` 写、`QCTRL FLUSH`、sys 广播的 `flushReq` 端口。
  * 四相握手两侧同构：req 拉起后必须等 ack 拉起才能落下，ack 再随 req 落下。
  *
  * `flushReq` 是 sys 级广播，要等所有模块都回 ack 才会落下，因此它可能远高于 Queen 自己
  * 的排空时间。**整个 req 有效期间 Queen 都必须停在 flush 态**（前端接受并丢弃），
  * 不能排空后就恢复派发。
  *
  * 排空后还要静默 `quietCycles` 拍才拉 ack：期间任何扰动（CPU 又发指令、迟到 completion、
  * 总线响应）都重新计数，因为 flush 对 CPU 是异步的，它随时可能再发 QRD。
  */
class QueenFlushCtrl(val quietCycles: Int) extends Module {
    require(quietCycles >= 1)

    val io = IO(new Bundle {
        val trig = Input(Bool())
        val cpuReq = Input(Bool())
        val cpuAck = Output(Bool())
        val devReq = Output(Vec(2, Bool()))
        val devAck = Input(Vec(2, Bool()))
        val drained = Input(Bool())
        val disturb = Input(Bool())
        val flushing = Output(Bool())
        val start = Output(Bool())
        val clearState = Output(Bool())
        val idle = Output(Bool())
    })

    private val sIdle :: sReqUp :: sReqDown :: sDrain :: sQuiet :: sAck :: Nil = Enum(6)
    private val state = RegInit(sIdle)

    private val devReq = RegInit(false.B)
    private val cpuAck = RegInit(false.B)
    private val cntW = log2Ceil(quietCycles + 1)
    private val quietCnt = RegInit(0.U(cntW.W))

    io.start := state === sIdle && (io.trig || io.cpuReq)
    io.flushing := state =/= sIdle
    io.clearState := state === sQuiet && io.drained && !io.disturb && quietCnt === 0.U
    io.devReq.foreach(_ := devReq)
    io.cpuAck := cpuAck
    io.idle := state === sIdle

    switch(state) {
        is(sIdle) {
            when(io.trig || io.cpuReq) {
                devReq := true.B
                state := sReqUp
            }
        }
        is(sReqUp) {
            when(io.devAck.asUInt.andR) {
                devReq := false.B
                state := sReqDown
            }
        }
        is(sReqDown) {
            when(!io.devAck.asUInt.orR) { state := sDrain }
        }
        is(sDrain) {
            when(io.drained) {
                quietCnt := quietCycles.U
                state := sQuiet
            }
        }
        is(sQuiet) {
            when(!io.drained) {
                state := sDrain
            }.elsewhen(io.disturb) {
                quietCnt := quietCycles.U
            }.elsewhen(quietCnt =/= 0.U) {
                quietCnt := quietCnt - 1.U
            }.otherwise {
                cpuAck := true.B
                state := sAck
            }
        }
        is(sAck) {
            // 内部触发没有 sys req 可等，ack 只有一拍。
            when(!io.cpuReq) {
                cpuAck := false.B
                state := sIdle
            }
        }
    }
}