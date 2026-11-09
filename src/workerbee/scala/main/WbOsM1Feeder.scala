import chisel3._
import chisel3.util._

class WbOsM1Feeder(
  T: Int = 16,
  bW: Int = 16,
  scaleBW: Int = 32,
  ktW: Int = 16,
  hasMx: Boolean = true
) extends Module {
  require(T >= 2 && isPow2(T), "WbOsM1Feeder: T must be a power of two and at least 2")
  require(bW > 0 && ktW > 0, "WbOsM1Feeder: bW and ktW must be positive")
  require(scaleBW > 0 && scaleBW % 8 == 0, "WbOsM1Feeder: scaleBW must contain whole bytes")

  private val laneW = log2Ceil(T)
  private class WindowBundle extends Bundle {
    val bWindow = UInt((T * bW).W)
    val scaleWord = UInt(scaleBW.W)
    val kt = UInt(ktW.W)
  }

  val io = IO(new Bundle {
    val bIn = slave(Stream(UInt((T * bW).W)))
    val scaleBIn = if (hasMx) Some(slave(Stream(UInt(scaleBW.W)))) else None
    val mxMode = Input(Bool())
    val consume = Input(Vec(T, Bool()))
    val flush = Input(Bool())
    val bOut = Output(Vec(T, UInt(bW.W)))
    val scaleOut = Output(Vec(T, UInt(scaleBW.W)))
    val headValid = Output(Vec(T, Bool()))
    val headKt = Output(Vec(T, UInt(ktW.W)))
    val laneOut = Output(Vec(T, UInt(laneW.W)))
  })

  private val slots = Seq.fill(T)(Reg(Vec(2, new WindowBundle)))
  private val heads = RegInit(VecInit(Seq.fill(T)(false.B)))
  private val tails = RegInit(VecInit(Seq.fill(T)(false.B)))
  private val counts = RegInit(VecInit(Seq.fill(T)(0.U(2.W))))
  private val lanes = RegInit(VecInit(Seq.fill(T)(0.U(laneW.W))))
  private val column = RegInit(0.U(laneW.W))
  private val kt = RegInit(0.U(ktW.W))
  private val pop = Wire(Vec(T, Bool()))

  for (j <- 0 until T) {
    val head = slots(j)(heads(j).asUInt)
    io.headValid(j) := counts(j) =/= 0.U
    io.bOut(j) := head.bWindow.asTypeOf(Vec(T, UInt(bW.W)))(lanes(j))
    io.scaleOut(j) := head.scaleWord
    io.headKt(j) := head.kt
    io.laneOut(j) := lanes(j)
    pop(j) := !io.flush && io.consume(j) && io.headValid(j) && lanes(j) === (T - 1).U
    assert(io.flush || !io.consume(j) || io.headValid(j),
      "WbOsM1Feeder: consume requires a valid window head")
    assert(counts(j) <= 2.U, "WbOsM1Feeder: window count exceeds depth two")
  }

  // Reclaimed space may accept a write, but outputs always read the registered old head.
  private val space = counts(column) < 2.U || pop(column)
  private val scaleValid = if (hasMx) io.scaleBIn.get.valid else true.B
  private val mxActive = if (hasMx) io.mxMode else false.B
  private val enqueue = !io.flush && space && io.bIn.valid && (!mxActive || scaleValid)
  io.bIn.ready := enqueue
  if (hasMx) io.scaleBIn.get.ready := mxActive && enqueue

  for (j <- 0 until T) {
    val push = enqueue && column === j.U
    when(io.flush) {
      heads(j) := false.B
      tails(j) := false.B
      counts(j) := 0.U
      lanes(j) := 0.U
    }.otherwise {
      when(push) {
        slots(j)(tails(j).asUInt).bWindow := io.bIn.payload
        slots(j)(tails(j).asUInt).scaleWord :=
          (if (hasMx) Mux(mxActive, io.scaleBIn.get.payload, 0.U(scaleBW.W)) else 0.U(scaleBW.W))
        slots(j)(tails(j).asUInt).kt := kt
        tails(j) := !tails(j)
      }
      when(pop(j)) {
        heads(j) := !heads(j)
        lanes(j) := 0.U
      }.elsewhen(io.consume(j) && io.headValid(j)) {
        lanes(j) := lanes(j) + 1.U
      }
      when(push =/= pop(j)) {
        counts(j) := Mux(push, counts(j) + 1.U, counts(j) - 1.U)
      }
    }
  }

  when(io.flush) {
    column := 0.U
    kt := 0.U
  }.elsewhen(enqueue) {
    column := column + 1.U
    when(column === (T - 1).U) {
      kt := kt + 1.U
    }
  }
}
