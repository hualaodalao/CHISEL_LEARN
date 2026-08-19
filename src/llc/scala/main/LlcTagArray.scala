import chisel3._
import chisel3.util._

class TagEntry(cfg: LlcConfig) extends Bundle {
  val tag   = UInt(cfg.tagBits.W)
  val valid = Bool()
  val dirty = Bool()
}

class TagArrayReadPort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
}

class TagArrayReadResp(cfg: LlcConfig) extends Bundle {
  val tags   = Output(Vec(cfg.ways, UInt(cfg.tagBits.W)))
  val valids = Output(Vec(cfg.ways, Bool()))
  val dirtys = Output(Vec(cfg.ways, Bool()))
}

class TagArrayWritePort(cfg: LlcConfig) extends Bundle {
  val enable = Input(Bool())
  val set    = Input(UInt(cfg.setBits.W))
  val way    = Input(UInt(log2Ceil(cfg.ways).W))
  val tag    = Input(UInt(cfg.tagBits.W))
  val valid  = Input(Bool())
  val dirty  = Input(Bool())
}

class LlcTagArray(cfg: LlcConfig) extends Module {
  val io = IO(new Bundle {
    val read     = new TagArrayReadPort(cfg)
    val readResp = new TagArrayReadResp(cfg)
    val write    = new TagArrayWritePort(cfg)
  })

  // Storage: SyncReadMem of Vec(ways, TagEntry)
  val tagMem = SyncReadMem(cfg.sets, Vec(cfg.ways, new TagEntry(cfg)))

  // --- Read path ---
  val rdData = tagMem.read(io.read.set, io.read.enable)

  for (w <- 0 until cfg.ways) {
    io.readResp.tags(w)   := rdData(w).tag
    io.readResp.valids(w) := rdData(w).valid
    io.readResp.dirtys(w) := rdData(w).dirty
  }

  // --- Write path ---
  // Read-modify-write: read the full Vec for the set, modify the specific way, write back
  when(io.write.enable) {
    val writeEntry = Wire(new TagEntry(cfg))
    writeEntry.tag   := io.write.tag
    writeEntry.valid := io.write.valid
    writeEntry.dirty := io.write.dirty

    // Use masked write: create a mask Vec(ways, Bool) with only the target way set
    val mask = VecInit(Seq.tabulate(cfg.ways)(w => (io.write.way === w.U)))
    val writeData = VecInit(Seq.fill(cfg.ways)(writeEntry))
    tagMem.write(io.write.set, writeData, mask)
  }
}
