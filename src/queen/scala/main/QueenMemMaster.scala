import chisel3._
import chisel3.util._

object QueenMemOp extends ChiselEnum {
    val READ, ATOMIC_ADD, ATOMIC_CLR, ATOMIC_SET = Value
}

class QueenMemReq(val cfg: QueenConfig) extends Bundle {
    val op = QueenMemOp()
    val address = UInt(cfg.xlen.W)
    val operand = UInt(32.W)
}

class QueenMemResp extends Bundle {
    val data = UInt(32.W)
    val error = Bool()
}

/** polling/atomic 系统内存主接口：`memDepth` 笔在途，响应必须按请求序返回（总线义务）。
  *
  * 在途表只存「这笔是不是 READ」：READ 的结果送 POLL_EQ 比较器，ATOMIC 只看 error。
  * 按序消费使得排在 POLL_EQ 之前的 ATOMIC 必然先落地，顺序无需额外握手。
  * 准入已为每笔在途事务预留响应槽，故 memResp.ready 恒 1。
  */
class QueenMemMaster(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val req = Flipped(Decoupled(new QueenMemReq(cfg)))
        val memReq = Decoupled(new QueenMemReq(cfg))
        val memResp = Flipped(Decoupled(new QueenMemResp))
        val rdData = Valid(UInt(32.W))
        val err = Output(Bool())
        val drop = Input(Bool())
        val used = Output(UInt(log2Ceil(cfg.memDepth + 1).W))
        val idle = Output(Bool())
    })

    private val depth = cfg.memDepth
    private val inflight = Module(new Queue(Bool(), depth))
    private val isRead = io.req.bits.op === QueenMemOp.READ

    private val hasRoom = inflight.io.count < depth.U
    io.memReq.valid := io.req.valid && hasRoom && !io.drop
    io.memReq.bits := io.req.bits
    io.req.ready := io.memReq.ready && hasRoom && !io.drop

    inflight.io.enq.valid := io.memReq.fire
    inflight.io.enq.bits := isRead
    io.used := inflight.io.count
    io.idle := inflight.io.count === 0.U

    io.memResp.ready := true.B
    inflight.io.deq.ready := io.memResp.fire
    assert(!io.memResp.valid || inflight.io.deq.valid,
        "QueenMemMaster: response without an outstanding transaction")
    io.rdData.valid := io.memResp.fire && inflight.io.deq.bits && !io.drop
    io.rdData.bits := io.memResp.bits.data
    io.err := io.memResp.fire && io.memResp.bits.error && !io.drop
}