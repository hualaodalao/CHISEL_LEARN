import chisel3._
import chisel3.util._

class QueenNativeCmd(val cfg: QueenConfig) extends Bundle {
    val isStart = Bool()
    val regAddr = UInt(10.W)
    val data = UInt(cfg.xlen.W)
    val pc = UInt(cfg.pcWidth.W)
    val cmdTag = UInt(8.W)
    val swTag = UInt(cfg.xlen.W)
}

// Serializes one endpoint packet into device register writes and start commands.
// FENCE/WAIT/TRACE/QRD must be consumed by the endpoint controller before this stage.
class QueenDsaAdapter(val cfg: QueenConfig) extends Module {
    val io = IO(new Bundle {
        val in = Flipped(Decoupled(Vec(cfg.instructionLanes, Valid(new QueenDsaCommand(cfg)))))
        val cfgCredit = Input(UInt(cfg.cfgCreditWidth.W))
        val out = Decoupled(new QueenNativeCmd(cfg))
    })

    private val lanes = cfg.instructionLanes
    private val active = RegInit(false.B)
    private val pkt = Reg(Vec(lanes, Valid(new QueenDsaCommand(cfg))))
    private val laneIdx = RegInit(0.U(log2Ceil(lanes + 1).W))
    private val second = RegInit(false.B)

    private val done = active && laneIdx === lanes.U
    io.in.ready := !active || done

    private val inRange = active && laneIdx < lanes.U
    // The index may equal `lanes` at packet end, so narrow it for the guarded Vec access.
    private def at(i: UInt): Valid[QueenDsaCommand] =
        if (lanes == 1) pkt(0) else pkt(i(log2Ceil(lanes) - 1, 0))
    private val curValid = inRange && at(laneIdx).valid
    private val cur = at(laneIdx).bits
    private val funct3 = cur.inst(14, 12)
    private val isQwr2 = funct3 === 0.U
    private val isQwr1 = funct3 === 1.U
    private val isRun = funct3 === 3.U && cur.inst(29, 26) === 9.U

    // credit only gates RUN; configuration writes always flow.
    private val canEmit = curValid && (isQwr1 || isQwr2 || (isRun && io.cfgCredit =/= 0.U))
    private val base = Cat(cur.inst(11), cur.inst(29, 25))

    io.out.valid := canEmit
    io.out.bits.isStart := isRun
    io.out.bits.regAddr := Mux(isRun, 0.U,
        Mux(isQwr2, Cat(0.U(4.W), base) + second.asUInt, cur.inst(29, 20)))
    io.out.bits.data := Mux(isRun, 0.U, Mux(isQwr2 && second, cur.rs2, cur.rs1))
    io.out.bits.pc := cur.pc
    io.out.bits.cmdTag := Mux(isRun, cur.rs1(7, 0), 0.U)
    io.out.bits.swTag := Mux(isRun, cur.rs2, 0.U)

    assert(!curValid || isQwr1 || isQwr2 || isRun,
        "QueenDsaAdapter: only QWR1/QWR2/RUN are supported at this stage")

    when(inRange && !at(laneIdx).valid) {
        laneIdx := laneIdx + 1.U
    }
    when(canEmit && io.out.fire) {
        when(isQwr2 && !second) {
            second := true.B
        }.otherwise {
            second := false.B
            laneIdx := laneIdx + 1.U
        }
    }
    when(done && !io.in.fire) {
        active := false.B
    }
    when(io.in.fire) {
        pkt := io.in.bits
        laneIdx := 0.U
        second := false.B
        active := true.B
    }
}