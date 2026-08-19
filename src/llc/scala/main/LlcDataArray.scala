import chisel3._
import chisel3.util._

class DataArrayReadPort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
  val way    = Input(UInt(log2Ceil(cfg.ways).W))
}

class DataArrayReadResp(cfg: LlcConfig) extends Bundle {
  val data = Output(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))
}

class DataArrayWritePort(cfg: LlcConfig) extends Bundle {
  val enable  = Input(Bool())
  val set     = Input(UInt(cfg.setBits.W))
  val way     = Input(UInt(log2Ceil(cfg.ways).W))
  val beatIdx = Input(UInt(log2Ceil(cfg.beatsPerLine).W))
  val data    = Input(UInt(cfg.dataWidth.W))
  val strb    = Input(UInt(cfg.bytePerBeat.W))
}

class DataArrayWriteLinePort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
  val way    = Input(UInt(log2Ceil(cfg.ways).W))
  val data   = Input(Vec(cfg.beatsPerLine, UInt(cfg.dataWidth.W)))
}

class LlcDataArray(cfg: LlcConfig) extends Module {
  val io = IO(new Bundle {
    val read      = new DataArrayReadPort(cfg)
    val readResp  = new DataArrayReadResp(cfg)
    val write     = new DataArrayWritePort(cfg)
    val writeLine = new DataArrayWriteLinePort(cfg)
  })

  // Storage: ways separate SyncReadMem instances
  // Each entry stores the full cache line as Vec(lineBytes, UInt(8.W)) for byte-granularity writes
  val dataMems = Seq.fill(cfg.ways)(SyncReadMem(cfg.sets, Vec(cfg.lineBytes, UInt(8.W))))

  // --- Read path ---
  // Read ALL ways in parallel, mux result next cycle using registered way select
  val rdResults = VecInit(dataMems.map(_.read(io.read.set, io.read.enable)))
  val wayReg = RegNext(io.read.way)

  // Select the way and repack bytes into beats
  val selectedLine = rdResults(wayReg)
  for (beat <- 0 until cfg.beatsPerLine) {
    val beatBytes = VecInit(
      (0 until cfg.bytePerBeat).map(b => selectedLine(beat * cfg.bytePerBeat + b))
    )
    io.readResp.data(beat) := beatBytes.asUInt
  }

  // --- Write path (per-beat with byte strobe) ---
  when(io.write.enable) {
    // Build the write mask and data for the full line (lineBytes entries)
    // Use static iteration to avoid dynamic LHS indexing issues
    val writeMask = Wire(Vec(cfg.lineBytes, Bool()))
    val writeData = Wire(Vec(cfg.lineBytes, UInt(8.W)))

    for (i <- 0 until cfg.lineBytes) {
      val beatOfThisByte = (i / cfg.bytePerBeat).U
      val byteInBeat = i % cfg.bytePerBeat
      writeMask(i) := (io.write.beatIdx === beatOfThisByte) && io.write.strb(byteInBeat)
      writeData(i) := io.write.data(byteInBeat * 8 + 7, byteInBeat * 8)
    }

    // Write to the selected way's SyncReadMem with mask
    for (w <- 0 until cfg.ways) {
      when(io.write.way === w.U) {
        dataMems(w).write(io.write.set, writeData, writeMask)
      }
    }
  }

  // --- WriteLine path (full cache line refill) ---
  when(io.writeLine.enable) {
    val writeData = Wire(Vec(cfg.lineBytes, UInt(8.W)))
    val writeMask = VecInit(Seq.fill(cfg.lineBytes)(true.B))

    // Unpack beats into bytes
    for (beat <- 0 until cfg.beatsPerLine) {
      for (b <- 0 until cfg.bytePerBeat) {
        writeData(beat * cfg.bytePerBeat + b) := io.writeLine.data(beat)(b * 8 + 7, b * 8)
      }
    }

    // Write to the selected way
    for (w <- 0 until cfg.ways) {
      when(io.writeLine.way === w.U) {
        dataMems(w).write(io.writeLine.set, writeData, writeMask)
      }
    }
  }
}
