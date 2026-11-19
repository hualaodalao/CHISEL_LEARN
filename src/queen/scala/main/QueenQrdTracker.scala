import chisel3._
import chisel3.util._

class QueenQrdEntry(val cfg: QueenConfig) extends Bundle {
    val pc = UInt(cfg.pcWidth.W)
    val rdIndex = UInt(5.W)
    // 0=GEMM 1=DMA 2=SCHED
    val source = UInt(2.W)
}

/** QRD 在途表：前端 accept 时按 lane 序分配 entry，退休时按程序序取回响应。
  *
  * 同端点响应必须按请求序返回（设备义务），故 entry 不存 regAddr，队头只认来源。
  * 三个来源各自缓存早到响应，跨端点乱序到达仍按程序序退休。
  * 准入已为每条在途 QRD 预留一个响应槽，故 resp.ready 恒 1。
  */
class QueenQrdTracker(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val alloc = Flipped(Vec(cfg.instructionLanes, Valid(new QueenQrdEntry(cfg))))
        val used = Output(UInt(log2Ceil(cfg.qrdDepth + 1).W))
        val resp = Vec(3, Flipped(Decoupled(new QueenDsaRegResp)))
        val asyncRd = Decoupled(new QueenAsyncRd(cfg))
        val retire = Decoupled(new QueenRetire(cfg))
        val idle = Output(Bool())
    })

    private val depth = cfg.qrdDepth
    private val lanes = cfg.instructionLanes
    private val idxW = math.max(1, log2Ceil(depth))
    private val cntW = log2Ceil(depth + 1)
    private val entries = Reg(Vec(depth, new QueenQrdEntry(cfg)))
    // 物理索引与计数分开：depth 可以不是 2 的幂，计数器回绕点必须是 depth 的整数倍，
    // 否则用「计数器 % depth」取槽会在回绕处错位。
    private val wrIdx = RegInit(0.U(idxW.W))
    private val rdIdx = RegInit(0.U(idxW.W))
    private val count = RegInit(0.U(cntW.W))

    private def slot(p: UInt): UInt = (p % depth.U).pad(idxW)(idxW - 1, 0)
    io.used := count

    private val prefix = (0 until lanes).map(i =>
        (0 until i).map(j => io.alloc(j).valid.asUInt).foldLeft(0.U)(_ +& _))
    private val allocCount = PopCount(io.alloc.map(_.valid))
    for (i <- 0 until lanes) {
        when(io.alloc(i).valid) {
            entries(slot(wrIdx +& prefix(i))) := io.alloc(i).bits
        }
    }
    assert(count +& allocCount <= depth.U, "QueenQrdTracker: allocation overflow")
    wrIdx := slot(wrIdx +& allocCount)
    count := (count +& allocCount - io.retire.fire.asUInt)(cntW - 1, 0)

    private val fifos = Seq.fill(3)(Module(new Queue(new QueenDsaRegResp, depth)))
    for (s <- 0 until 3) {
        fifos(s).io.enq.valid := io.resp(s).valid
        fifos(s).io.enq.bits := io.resp(s).bits
        io.resp(s).ready := true.B
        assert(!io.resp(s).valid || fifos(s).io.enq.ready,
            "QueenQrdTracker: response overflow, admission must reserve a slot")
    }

    private val head = entries(rdIdx)
    private val headValid = count =/= 0.U
    private val respValid = VecInit(fifos.map(_.io.deq.valid))
    private val respBits = VecInit(fifos.map(_.io.deq.bits))
    private val headResp = respBits(head.source)
    private val headReady = headValid && respValid(head.source)
    private val writeBack = head.rdIndex =/= 0.U && !headResp.err

    io.asyncRd.valid := headReady && writeBack
    io.asyncRd.bits.addr := head.rdIndex
    io.asyncRd.bits.data := headResp.data
    io.retire.valid := headReady && (!writeBack || io.asyncRd.ready)
    io.retire.bits.pc := head.pc
    io.retire.bits.rdValid := writeBack
    io.retire.bits.rdIndex := head.rdIndex
    io.retire.bits.trap := headResp.err
    io.retire.bits.trapCause := Mux(headResp.err, QueenTrapCause.rdError.U, QueenTrapCause.none.U)

    for (s <- 0 until 3) {
        fifos(s).io.deq.ready := io.retire.fire && head.source === s.U
    }
    when(io.retire.fire) { rdIdx := slot(rdIdx + 1.U) }

    io.idle := count === 0.U
}