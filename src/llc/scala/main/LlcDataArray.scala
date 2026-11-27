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
  // A lookup reads one way only, so only that bank's read port is enabled;
  // the result is muxed next cycle using registered way select
  val rdResults = VecInit(dataMems.zipWithIndex.map { case (mem, w) =>
    mem.read(io.read.set, io.read.enable && (io.read.way === w.U))
  })
  val wayReg = RegNext(io.read.way)

  // Select the way and repack bytes into beats
  val selectedLine = rdResults(wayReg)
  for (beat <- 0 until cfg.beatsPerLine) {
    val beatBytes = VecInit(
      (0 until cfg.bytePerBeat).map(b => selectedLine(beat * cfg.bytePerBeat + b))
    )
    io.readResp.data(beat) := beatBytes.asUInt
  }

  // --- Write path ---
  // Per-beat and full-line writes share one masked port: a SyncReadMem with both a
  // masked and an unmasked write port loses its mask during emission.
  val wrSet = Mux(io.write.enable, io.write.set, io.writeLine.set)
  val wrWay = Mux(io.write.enable, io.write.way, io.writeLine.way)
  val writeData = Wire(Vec(cfg.lineBytes, UInt(8.W)))
  val writeMask = Wire(Vec(cfg.lineBytes, Bool()))

  for (i <- 0 until cfg.lineBytes) {
    val beat       = i / cfg.bytePerBeat
    val byteInBeat = i % cfg.bytePerBeat
    writeMask(i) := Mux(io.write.enable,
      (io.write.beatIdx === beat.U) && io.write.strb(byteInBeat), true.B)
    writeData(i) := Mux(io.write.enable,
      io.write.data(byteInBeat * 8 + 7, byteInBeat * 8),
      io.writeLine.data(beat)(byteInBeat * 8 + 7, byteInBeat * 8))
  }

  when(io.write.enable || io.writeLine.enable) {
    for (w <- 0 until cfg.ways) {
      when(wrWay === w.U) {
        dataMems(w).write(wrSet, writeData, writeMask)
      }
    }
  }
}
