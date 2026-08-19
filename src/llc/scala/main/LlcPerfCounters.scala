import chisel3._
import chisel3.util._

/** LLC performance counter block.
  *
  * Aggregates event pulses coming from the pipeline / buffers into a bank of
  * 64-bit free-running counters. Each counter increments by 1 on any cycle its
  * corresponding event input is asserted. All counters can be cleared together
  * via the `clear` input (typically driven by a W1P control-register pulse).
  *
  * Counter index mapping (see [[io.counters]]):
  *   0  readHitCount
  *   1  readMissCount
  *   2  writeHitCount
  *   3  writeMissCount
  *   4  dirtyEvictCount
  *   5  cleanEvictCount
  *   6  mshrFullStallCycles
  *   7  wbBufFullStallCycles
  *   8  writeMergeCount
  *   9  refillCount
  *   10-15 reserved (always read 0)
  */
class LlcPerfCounters(cfg: LlcConfig) extends Module {
  val io = IO(new Bundle {
    // Event inputs (level/pulse signals, +1 per cycle when true)
    val events = Input(new Bundle {
      val readHit       = Bool()
      val readMiss      = Bool()
      val writeHit      = Bool()
      val writeMiss     = Bool()
      val dirtyEvict    = Bool()
      val cleanEvict    = Bool()
      val mshrFullStall = Bool()
      val wbBufFullStall = Bool()
      val writeMerge    = Bool()  // write combined into existing entry
      val refillComplete = Bool() // for counting refills
    })

    // Counter read interface (for register interface)
    val counters = Output(Vec(16, UInt(64.W)))

    // Control
    val clear = Input(Bool()) // clear all counters
  })

  val numCounters = 16

  // Map the 10 active event inputs onto counter indices 0..9.
  // Indices 10..15 are reserved and permanently read 0.
  val eventVec: Seq[Bool] = Seq(
    io.events.readHit,        // 0
    io.events.readMiss,       // 1
    io.events.writeHit,       // 2
    io.events.writeMiss,      // 3
    io.events.dirtyEvict,     // 4
    io.events.cleanEvict,     // 5
    io.events.mshrFullStall,  // 6
    io.events.wbBufFullStall, // 7
    io.events.writeMerge,     // 8
    io.events.refillComplete  // 9
  )

  val counters = RegInit(VecInit(Seq.fill(numCounters)(0.U(64.W))))

  for (i <- 0 until numCounters) {
    if (i < eventVec.length) {
      when(io.clear) {
        counters(i) := 0.U
      }.elsewhen(eventVec(i)) {
        counters(i) := counters(i) + 1.U
      }
    } else {
      // Reserved counters: hold 0
      counters(i) := 0.U
    }
    io.counters(i) := counters(i)
  }
}
