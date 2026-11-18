import chisel3._
import chisel3.util._

object QueenNativeOp extends ChiselEnum {
    val WRITE1, WRITE2, READ, RUN = Value
}

class QueenNativeCmd(val cfg: QueenConfig) extends Bundle {
    val op = QueenNativeOp()
    val reg0Addr = UInt(10.W)
    val reg1Addr = UInt(10.W)
    val data0 = UInt(cfg.xlen.W)
    val data1 = UInt(cfg.xlen.W)
    val pc = UInt(cfg.pcWidth.W)
    val cmdTag = UInt(8.W)
    val swTag = UInt(cfg.xlen.W)
}

/** 把一个端点包并行展开为设备配置命令。纯写 lane 合并为一个 beat；QCTRL/QRD 独占一个
  * beat，故设备侧只需一个 ready：写 beat 恒可收，RUN/READ beat 由设备按 op 给出可收性。
  * FENCE/WAIT/TRACE 必须由端点控制器在本级之前消费，QRD 走尚未实现的读响应通路。
  */
class QueenDsaAdapter(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg)))))
        val out = Decoupled(Vec(cfg.nativeWidth, Valid(new QueenNativeCmd(cfg))))
    })

    private val lanes = cfg.instructionLanes
    private val width = cfg.nativeWidth
    private val cntW = log2Ceil(lanes + 1)
    private val active = RegInit(false.B)
    private val pkt = Reg(Vec(lanes, Valid(new QueenDsaCommand(cfg))))
    private val base = RegInit(0.U(cntW.W))

    private val done = active && base >= lanes.U
    io.in.ready := !active || done

    private val inRange = active && base < lanes.U
    // base 可以等于 lanes（包尾），越界索引由 inRange/idx 判定挡住后再窄化取值。
    private def at(i: UInt): Valid[QueenDsaCommand] =
        if (lanes == 1) pkt(0) else pkt(i(log2Ceil(lanes) - 1, 0))

    private val cand = Wire(Vec(width, Valid(new QueenDsaCommand(cfg))))
    private val candWr = Wire(Vec(width, Bool()))
    private val candOther = Wire(Vec(width, Bool()))
    private val candRun = Wire(Vec(width, Bool()))
    for (j <- 0 until width) {
        val idx = base +& j.U
        val slot = at(idx)
        val funct3 = slot.bits.inst(14, 12)
        cand(j).valid := inRange && idx < lanes.U && slot.valid
        cand(j).bits := slot.bits
        candWr(j) := cand(j).valid && (funct3 === 0.U || funct3 === 1.U)
        candOther(j) := cand(j).valid && !candWr(j)
        candRun(j) := candOther(j) && funct3 === 3.U && slot.bits.inst(29, 26) === 9.U
    }

    private def firstIdx(sel: Vec[Bool]): UInt = {
        val idx = WireDefault(0.U(cntW.W))
        for (j <- (0 until width).reverse) {
            when(sel(j)) { idx := j.U(cntW.W) }
        }
        idx
    }

    private val hasOther = candOther.asUInt.orR
    private val firstValidIdx = firstIdx(VecInit(cand.map(_.valid)))
    private val firstOtherIdx = firstIdx(candOther)
    // 首条有效 lane 就是 QCTRL/QRD 时把它含进 beat（独占），否则停在它之前。
    private val alone = firstValidIdx === firstOtherIdx
    private val room = lanes.U(cntW.W) - base
    private val full = Mux(room < width.U(cntW.W), room, width.U(cntW.W))
    private val beatLen = Mux(hasOther, firstOtherIdx + alone.asUInt, full)
    private val inBeat = (0 until width).map(j => cand(j).valid && j.U(cntW.W) < beatLen)
    private val beatValid = inBeat.reduce(_ || _)
    private val beatSupported = (0 until width)
        .map(j => !inBeat(j) || candWr(j) || candRun(j)).reduce(_ && _)

    for (j <- 0 until width) {
        val b = cand(j).bits
        val funct3 = b.inst(14, 12)
        val isW2 = funct3 === 0.U
        val isRun = candRun(j)
        val regBase = Cat(0.U(4.W), Cat(b.inst(11), b.inst(29, 25)))
        val o = io.out.bits(j)
        o.valid := inBeat(j)
        o.bits.op := Mux(isRun, QueenNativeOp.RUN.asUInt,
            Mux(isW2, QueenNativeOp.WRITE2.asUInt, QueenNativeOp.WRITE1.asUInt))
            .asTypeOf(QueenNativeOp())
        o.bits.reg0Addr := Mux(isRun, 0.U(10.W), Mux(isW2, regBase, b.inst(29, 20)))
        o.bits.reg1Addr := Mux(isW2, regBase + 1.U(10.W), 0.U(10.W))
        o.bits.data0 := Mux(isRun, 0.U(cfg.xlen.W), b.rs1)
        o.bits.data1 := Mux(isW2, b.rs2, 0.U(cfg.xlen.W))
        o.bits.pc := b.pc
        o.bits.cmdTag := Mux(isRun, b.rs1(7, 0), 0.U(8.W))
        o.bits.swTag := Mux(isRun, b.rs2, 0.U(cfg.xlen.W))
        assert(!cand(j).valid || isW2 || funct3 === 1.U || isRun,
            "QueenDsaAdapter: only QWR1/QWR2/RUN are supported at this stage")
    }

    io.out.valid := beatValid && beatSupported

    when(inRange && (io.out.fire || !beatValid)) {
        base := base + beatLen
    }
    when(done && !io.in.fire) {
        active := false.B
    }
    when(io.in.fire) {
        pkt := io.in.bits
        base := 0.U
        active := true.B
    }
}