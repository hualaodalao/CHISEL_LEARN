import chisel3._
import chisel3.util._

class TagEntry(cfg: LlcConfig) extends Bundle {
  val tag   = UInt(cfg.tagBits.W)
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

  // valid lives in registers so reset alone yields an empty cache; SRAM contents
  // are undefined at reset. tag and dirty stay in the SRAM.
  val tagMem = SyncReadMem(cfg.sets, Vec(cfg.ways, new TagEntry(cfg)))
  val validRegs = RegInit(VecInit(Seq.fill(cfg.sets)(VecInit(Seq.fill(cfg.ways)(false.B)))))
  val rdValids  = RegInit(VecInit(Seq.fill(cfg.ways)(false.B)))

  // --- Read path ---
  val rdData = tagMem.read(io.read.set, io.read.enable)
  when(io.read.enable) { rdValids := validRegs(io.read.set) }

  for (w <- 0 until cfg.ways) {
    io.readResp.tags(w)   := rdData(w).tag
    io.readResp.valids(w) := rdValids(w)
    io.readResp.dirtys(w) := rdData(w).dirty
  }

  // --- Write path ---
  when(io.write.enable) {
    validRegs(io.write.set)(io.write.way) := io.write.valid

    val writeEntry = Wire(new TagEntry(cfg))
    writeEntry.tag   := io.write.tag
    writeEntry.dirty := io.write.dirty

    // Use masked write: create a mask Vec(ways, Bool) with only the target way set
    val mask = VecInit(Seq.tabulate(cfg.ways)(w => (io.write.way === w.U)))
    val writeData = VecInit(Seq.fill(cfg.ways)(writeEntry))
    tagMem.write(io.write.set, writeData, mask)
  }
}
