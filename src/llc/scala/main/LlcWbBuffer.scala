import chisel3._
import chisel3.util._

// Write-Back Buffer enqueue bundle
class WbBufEnqReq(cfg: LlcConfig) extends Bundle {
  val address = UInt(cfg.addressWidth.W)
  val data    = Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))
}

// Write-Back Buffer dequeue bundle
class WbBufDeqResp(cfg: LlcConfig) extends Bundle {
  val address = UInt(cfg.addressWidth.W)
  val data    = Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))
}

// Write-Back Buffer IO
class LlcWbBufferIO(cfg: LlcConfig) extends Bundle {
  // Enqueue from MSHR
  val enq      = Flipped(Valid(new WbBufEnqReq(cfg)))
  val enqReady = Output(Bool())

  // Dequeue to AXI4 master write channel
  val deq      = Valid(new WbBufDeqResp(cfg))
  val deqReady = Input(Bool())

  // Status
  val empty = Output(Bool())
  val full  = Output(Bool())

  // Snoop: check if address is in write-back buffer (for hazard detection)
  val snoop = new Bundle {
    val query = Input(UInt(cfg.addressWidth.W))
    val hit   = Output(Bool())
  }
}

class LlcWbBuffer(cfg: LlcConfig) extends Module {
  val io = IO(new LlcWbBufferIO(cfg))

  val numEntries = cfg.wbBufEntries

  // ---- Buffer entry registers ----
  val entryValid   = RegInit(VecInit(Seq.fill(numEntries)(false.B)))
  val entryAddress = Reg(Vec(numEntries, UInt(cfg.addressWidth.W)))
  val entryData    = Reg(Vec(numEntries, Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W))))

  // ---- Circular buffer pointers ----
  val head = RegInit(0.U(log2Ceil(numEntries).W)) // dequeue pointer (oldest)
  val tail = RegInit(0.U(log2Ceil(numEntries).W)) // enqueue pointer (next free)
  val count = RegInit(0.U(log2Ceil(numEntries + 1).W))

  val isFull  = count === numEntries.U
  val isEmpty = count === 0.U

  io.full     := isFull
  io.empty    := isEmpty
  io.enqReady := !isFull

  // ---- Enqueue logic ----
  when(io.enq.valid && !isFull) {
    entryValid(tail)   := true.B
    entryAddress(tail) := io.enq.bits.address
    entryData(tail)    := io.enq.bits.data
    tail := Mux(tail === (numEntries - 1).U, 0.U, tail + 1.U)
    count := count + 1.U
  }

  // ---- Dequeue logic ----
  io.deq.valid        := !isEmpty
  io.deq.bits.address := entryAddress(head)
  io.deq.bits.data    := entryData(head)

  when(io.deqReady && !isEmpty) {
    entryValid(head) := false.B
    head := Mux(head === (numEntries - 1).U, 0.U, head + 1.U)
    count := count - 1.U
  }

  // Handle simultaneous enqueue and dequeue
  when(io.enq.valid && !isFull && io.deqReady && !isEmpty) {
    // Both happen: count stays the same (net zero)
    count := count // no net change, override the +1 and -1
  }

  // ---- Snoop logic (combinational address match) ----
  val lineMask     = (~((1 << cfg.offsetBits) - 1).U(cfg.addressWidth.W)).asUInt
  val queryAligned = io.snoop.query & lineMask
  val snoopHitVec = (0 until numEntries).map { i =>
    entryValid(i) && (entryAddress(i) & lineMask) === queryAligned
  }
  io.snoop.hit := snoopHitVec.reduce(_ || _)
}
