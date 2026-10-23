import chisel3._
import chisel3.util._

object HiveCoreRegAddr {
  val M = 0x00
  val NK = 0x01
  val A = 0x02
  val B = 0x03
  val C = 0x04
  val Control = 0x05
  val ScaleA = 0x06
  val ScaleB = 0x07
  val ScaleC = 0x08
  val Bias = 0x09
  val RqM = 0x0A
  val RqParam = 0x0B
  val Start = 0x10
  val Status = 0x1E
  val IrqRaw = 0x30
  val IrqMask = 0x31
  val IrqMasked = 0x32
  val IrqClear = 0x33
  val IrqErrorCode = 0x34
}

class HiveCoreRegIf(cfg: HiveCoreConfig) extends Module {
  val io = IO(new Bundle {
    val cmd = slave(Stream(new HiveCoreCmd))
    val resp = master(Stream(new HiveCoreResp))
    val regFile = Output(new HiveCoreRegister(cfg))
    val start = Output(Bool())
    val startPc = Output(UInt(32.W))
    val startTag = Output(UInt(8.W))
    val busy = Input(Bool())
    val progress = Input(UInt(16.W))
    val done = Input(Bool())
    val configError = Input(Bool())
    val configErrorCode = Input(UInt(4.W))
    val dmaErrors = Input(Vec(8, Bool()))
    val inFlight = Output(Bool())
    val irq = Output(Bool())
  })

  val regFile = RegInit(0.U.asTypeOf(new HiveCoreRegister(cfg)))
  val inFlight = RegInit(false.B)
  val startPending = RegInit(false.B)
  val startPc = RegInit(0.U(32.W))
  val startTag = RegInit(0.U(8.W))
  val irqRaw = RegInit(0.U(10.W))
  val irqMask = RegInit("h3ff".U(10.W))
  val errorCode = RegInit(0.U(4.W))
  val irqMasked = irqRaw & ~irqMask

  io.regFile := regFile
  io.start := startPending
  io.startPc := startPc
  io.startTag := startTag
  io.inFlight := inFlight
  io.irq := irqMasked.orR
  startPending := false.B

  def writeRegister(addr: UInt, data: UInt): Unit = {
    switch(addr) {
      is(HiveCoreRegAddr.M.U) { regFile.m := data(15, 0) }
      is(HiveCoreRegAddr.NK.U) { regFile.n := data(15, 0); regFile.k := data(31, 16) }
      is(HiveCoreRegAddr.A.U) { regFile.aAddr := data }
      is(HiveCoreRegAddr.B.U) { regFile.bAddr := data }
      is(HiveCoreRegAddr.C.U) { regFile.cAddr := data }
      is(HiveCoreRegAddr.Control.U) {
        regFile.aFmt := data(3, 0).asTypeOf(DataFormat())
        regFile.bFmt := data(7, 4).asTypeOf(DataFormat())
        regFile.cFmt := data(11, 8).asTypeOf(DataFormat())
        regFile.aAddrMode := data(12).asTypeOf(AddrMode())
        regFile.bAddrMode := data(13).asTypeOf(AddrMode())
        regFile.cAddrMode := data(14).asTypeOf(AddrMode())
        regFile.reluOn := data(15)
        regFile.rnd := data(18, 16).asTypeOf(RoundingMode())
        regFile.warmdown := data(20)
        regFile.warmup := data(21)
        regFile.aMxBlockSize := data(22).asTypeOf(MXBlockSize())
        regFile.bMxBlockSize := data(24).asTypeOf(MXBlockSize())
        regFile.cMxBlockSize := data(26).asTypeOf(MXBlockSize())
        regFile.loadWMode := data(28)
        regFile.requantOn := data(29)
        regFile.biasOn := data(30)
      }
      is(HiveCoreRegAddr.ScaleA.U) { regFile.aScaleAddr := data }
      is(HiveCoreRegAddr.ScaleB.U) { regFile.bScaleAddr := data }
      is(HiveCoreRegAddr.ScaleC.U) { regFile.cScaleAddr := data }
      is(HiveCoreRegAddr.Bias.U) { regFile.biasAddr := data }
      is(HiveCoreRegAddr.RqM.U) { regFile.rqMBase := data }
      is(HiveCoreRegAddr.RqParam.U) {
        regFile.rqShift := data(5, 0)
        regFile.rqZp := data(31, 16).asSInt
      }
      is(HiveCoreRegAddr.IrqMask.U) { irqMask := data(9, 0) }
    }
  }

  def readRegister(addr: UInt): UInt = {
    val data = WireDefault(0.U(32.W))
    switch(addr) {
      is(HiveCoreRegAddr.M.U) { data := regFile.m }
      is(HiveCoreRegAddr.NK.U) { data := Cat(regFile.k, regFile.n) }
      is(HiveCoreRegAddr.A.U) { data := regFile.aAddr }
      is(HiveCoreRegAddr.B.U) { data := regFile.bAddr }
      is(HiveCoreRegAddr.C.U) { data := regFile.cAddr }
      is(HiveCoreRegAddr.Control.U) {
        data := Cat(0.U(1.W), regFile.biasOn, regFile.requantOn, regFile.loadWMode,
          0.U(1.W), regFile.cMxBlockSize.asUInt, 0.U(1.W), regFile.bMxBlockSize.asUInt,
          0.U(1.W), regFile.aMxBlockSize.asUInt, regFile.warmup, regFile.warmdown,
          0.U(1.W), regFile.rnd.asUInt, regFile.reluOn, regFile.cAddrMode.asUInt,
          regFile.bAddrMode.asUInt, regFile.aAddrMode.asUInt, regFile.cFmt.asUInt,
          regFile.bFmt.asUInt, regFile.aFmt.asUInt)
      }
      is(HiveCoreRegAddr.ScaleA.U) { data := regFile.aScaleAddr }
      is(HiveCoreRegAddr.ScaleB.U) { data := regFile.bScaleAddr }
      is(HiveCoreRegAddr.ScaleC.U) { data := regFile.cScaleAddr }
      is(HiveCoreRegAddr.Bias.U) { data := regFile.biasAddr }
      is(HiveCoreRegAddr.RqM.U) { data := regFile.rqMBase }
      is(HiveCoreRegAddr.RqParam.U) { data := Cat(regFile.rqZp.asUInt, 0.U(10.W), regFile.rqShift) }
      is(HiveCoreRegAddr.Status.U) { data := Cat(io.progress, 0.U(13.W), inFlight, 0.U(1.W), io.busy) }
      is(HiveCoreRegAddr.IrqRaw.U) { data := irqRaw }
      is(HiveCoreRegAddr.IrqMask.U) { data := irqMask }
      is(HiveCoreRegAddr.IrqMasked.U) { data := irqMasked }
      is(HiveCoreRegAddr.IrqErrorCode.U) { data := errorCode }
    }
    data
  }

  def rejectWrite(addr: UInt, data: UInt): Bool = {
    val readOnly = addr === HiveCoreRegAddr.Status.U || addr === HiveCoreRegAddr.IrqRaw.U ||
      addr === HiveCoreRegAddr.IrqMasked.U || addr === HiveCoreRegAddr.IrqErrorCode.U
    val needsIdle = addr <= HiveCoreRegAddr.RqParam.U ||
      (addr === HiveCoreRegAddr.Start.U && data.orR)
    readOnly || (inFlight && needsIdle)
  }

  val respValid = RegInit(false.B)
  val respData = RegInit(0.U(32.W))
  val respErr = RegInit(false.B)
  io.cmd.ready := !respValid
  io.resp.valid := respValid
  io.resp.payload.data := respData
  io.resp.payload.err := respErr
  when(io.resp.fire) { respValid := false.B }

  val cmd = io.cmd.payload
  val write1 = cmd.op === HiveCoreOp.REG_WRITE1
  val write2 = cmd.op === HiveCoreOp.REG_WRITE2
  val read = cmd.op === HiveCoreOp.REG_READ
  val rejected = rejectWrite(cmd.reg0Addr, cmd.data0) ||
    (write2 && rejectWrite(cmd.reg1Addr, cmd.data1))
  val acceptedWrite = io.cmd.fire && (write1 || write2) && !rejected
  val clear0 = Mux(acceptedWrite && cmd.reg0Addr === HiveCoreRegAddr.IrqClear.U, cmd.data0(9, 0), 0.U(10.W))
  val clear1 = Mux(acceptedWrite && write2 && cmd.reg1Addr === HiveCoreRegAddr.IrqClear.U, cmd.data1(9, 0), 0.U(10.W))
  val clearBits = clear0 | clear1
  val newEvents = Cat(io.dmaErrors.asUInt, io.configError, io.done)
  irqRaw := (irqRaw & ~clearBits) | newEvents
  when(clearBits(1)) { errorCode := 0.U }
  when(io.configError && (!irqRaw(1) || clearBits(1))) { errorCode := io.configErrorCode }

  when(io.cmd.fire) {
    respValid := true.B
    respData := Mux(read, readRegister(cmd.reg0Addr), 0.U)
    respErr := !read && (!(write1 || write2) || rejected)
  }
  when(acceptedWrite) {
    writeRegister(cmd.reg0Addr, cmd.data0)
    when(write2) { writeRegister(cmd.reg1Addr, cmd.data1) }
    val trigger0 = cmd.reg0Addr === HiveCoreRegAddr.Start.U && cmd.data0.orR
    val trigger1 = write2 && cmd.reg1Addr === HiveCoreRegAddr.Start.U && cmd.data1.orR
    when(trigger0 || trigger1) {
      startPending := true.B
      startPc := cmd.pc
      startTag := cmd.tag
      inFlight := true.B
    }
  }
  when(io.done || io.configError) { inFlight := false.B }
}
