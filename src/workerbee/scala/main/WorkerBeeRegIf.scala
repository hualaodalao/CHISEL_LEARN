import chisel3._
import chisel3.util._

object WorkerBeeRegAddr {
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

class WorkerBeeRegIf(cfg: WorkerBeeConfig) extends Module {
  val io = IO(new Bundle {
    val cmd = Vec(4, slave(Stream(new WorkerBeeCmd)))
    val resp = master(Stream(new WorkerBeeResp))
    val regFile = Output(new WorkerBeeRegister(cfg))
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

  val regFile = RegInit(0.U.asTypeOf(new WorkerBeeRegister(cfg)))
  val inFlight = RegInit(false.B)
  val startPending = RegInit(false.B)
  val startPc = RegInit(0.U(32.W))
  val startTag = RegInit(0.U(8.W))
  val irqRaw = RegInit(0.U(11.W))
  val irqMask = RegInit("h7ff".U(11.W))
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
      is(WorkerBeeRegAddr.M.U) { regFile.m := data(15, 0) }
      is(WorkerBeeRegAddr.NK.U) { regFile.n := data(15, 0); regFile.k := data(31, 16) }
      is(WorkerBeeRegAddr.A.U) { regFile.aAddr := data }
      is(WorkerBeeRegAddr.B.U) { regFile.bAddr := data }
      is(WorkerBeeRegAddr.C.U) { regFile.cAddr := data }
      is(WorkerBeeRegAddr.Control.U) {
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
      is(WorkerBeeRegAddr.ScaleA.U) { regFile.aScaleAddr := data }
      is(WorkerBeeRegAddr.ScaleB.U) { regFile.bScaleAddr := data }
      is(WorkerBeeRegAddr.ScaleC.U) { regFile.cScaleAddr := data }
      is(WorkerBeeRegAddr.Bias.U) { regFile.biasAddr := data }
      is(WorkerBeeRegAddr.RqM.U) { regFile.rqMBase := data }
      is(WorkerBeeRegAddr.RqParam.U) {
        regFile.rqShift := data(5, 0)
        regFile.rqZp := data(31, 16).asSInt
      }
      is(WorkerBeeRegAddr.IrqMask.U) { irqMask := data(10, 0) }
    }
  }

  def readRegister(addr: UInt): UInt = {
    val data = WireDefault(0.U(32.W))
    switch(addr) {
      is(WorkerBeeRegAddr.M.U) { data := regFile.m }
      is(WorkerBeeRegAddr.NK.U) { data := Cat(regFile.k, regFile.n) }
      is(WorkerBeeRegAddr.A.U) { data := regFile.aAddr }
      is(WorkerBeeRegAddr.B.U) { data := regFile.bAddr }
      is(WorkerBeeRegAddr.C.U) { data := regFile.cAddr }
      is(WorkerBeeRegAddr.Control.U) {
        data := Cat(0.U(1.W), regFile.biasOn, regFile.requantOn, regFile.loadWMode,
          0.U(1.W), regFile.cMxBlockSize.asUInt, 0.U(1.W), regFile.bMxBlockSize.asUInt,
          0.U(1.W), regFile.aMxBlockSize.asUInt, regFile.warmup, regFile.warmdown,
          0.U(1.W), regFile.rnd.asUInt, regFile.reluOn, regFile.cAddrMode.asUInt,
          regFile.bAddrMode.asUInt, regFile.aAddrMode.asUInt, regFile.cFmt.asUInt,
          regFile.bFmt.asUInt, regFile.aFmt.asUInt)
      }
      is(WorkerBeeRegAddr.ScaleA.U) { data := regFile.aScaleAddr }
      is(WorkerBeeRegAddr.ScaleB.U) { data := regFile.bScaleAddr }
      is(WorkerBeeRegAddr.ScaleC.U) { data := regFile.cScaleAddr }
      is(WorkerBeeRegAddr.Bias.U) { data := regFile.biasAddr }
      is(WorkerBeeRegAddr.RqM.U) { data := regFile.rqMBase }
      is(WorkerBeeRegAddr.RqParam.U) { data := Cat(regFile.rqZp.asUInt, 0.U(10.W), regFile.rqShift) }
      is(WorkerBeeRegAddr.Status.U) { data := Cat(io.progress, 0.U(13.W), inFlight, 0.U(1.W), io.busy) }
      is(WorkerBeeRegAddr.IrqRaw.U) { data := irqRaw }
      is(WorkerBeeRegAddr.IrqMask.U) { data := irqMask }
      is(WorkerBeeRegAddr.IrqMasked.U) { data := irqMasked }
      is(WorkerBeeRegAddr.IrqErrorCode.U) { data := errorCode }
    }
    data
  }

  def rejectWrite(addr: UInt, data: UInt): Bool = {
    val readOnly = addr === WorkerBeeRegAddr.Status.U || addr === WorkerBeeRegAddr.IrqRaw.U ||
      addr === WorkerBeeRegAddr.IrqMasked.U || addr === WorkerBeeRegAddr.IrqErrorCode.U
    val needsIdle = addr <= WorkerBeeRegAddr.RqParam.U ||
      (addr === WorkerBeeRegAddr.Start.U && data.orR)
    readOnly || (inFlight && needsIdle)
  }

  val respPending = RegInit(VecInit(Seq.fill(4)(false.B)))
  val readPending = RegInit(VecInit(Seq.fill(4)(false.B)))
  val respData = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val respErr = RegInit(VecInit(Seq.fill(4)(false.B)))
  val respLane = PriorityEncoder(respPending.asUInt)
  val acceptBatch = !readPending.asUInt.orR && !respPending.asUInt.orR
  io.cmd.foreach(_.ready := acceptBatch)
  io.resp.valid := respPending.asUInt.orR
  io.resp.payload.data := respData(respLane)
  io.resp.payload.err := respErr(respLane)
  io.resp.payload.laneId := respLane
  when(io.resp.fire) {
    respPending(respLane) := false.B
    readPending(respLane) := false.B
  }

  val acceptedWrites = Wire(Vec(4, Bool()))
  for (lane <- 0 until 4) {
    val stream = io.cmd(lane)
    val cmd = stream.payload
    val write1 = cmd.op === WorkerBeeOp.REG_WRITE1
    val write2 = cmd.op === WorkerBeeOp.REG_WRITE2
    val read = cmd.op === WorkerBeeOp.REG_READ
    val rejected = rejectWrite(cmd.reg0Addr, cmd.data0) ||
      (write2 && rejectWrite(cmd.reg1Addr, cmd.data1))
    acceptedWrites(lane) := stream.fire && (write1 || write2) && !rejected
    when(stream.fire) {
      respPending(lane) := true.B
      readPending(lane) := read
      respData(lane) := Mux(read, readRegister(cmd.reg0Addr), 0.U)
      respErr(lane) := !read && (!(write1 || write2) || rejected)
    }
  }

  def hasWriteTarget(addr: UInt, data: UInt): Bool = {
    addr <= WorkerBeeRegAddr.RqParam.U || addr === WorkerBeeRegAddr.IrqMask.U ||
      addr === WorkerBeeRegAddr.IrqClear.U || (addr === WorkerBeeRegAddr.Start.U && data.orR)
  }

  val writeTargets = (0 until 4).map { lane =>
    val cmd = io.cmd(lane).payload
    Seq(
      (acceptedWrites(lane) && hasWriteTarget(cmd.reg0Addr, cmd.data0), cmd.reg0Addr),
      (acceptedWrites(lane) && cmd.op === WorkerBeeOp.REG_WRITE2 &&
        hasWriteTarget(cmd.reg1Addr, cmd.data1), cmd.reg1Addr))
  }
  val conflicts = for {
    low <- 0 until 4
    high <- low + 1 until 4
    (lowValid, lowAddr) <- writeTargets(low)
    (highValid, highAddr) <- writeTargets(high)
  } yield lowValid && highValid && lowAddr === highAddr
  val writeConflict = conflicts.reduce(_ || _)

  val clearBits = WireDefault(0.U(11.W))
  for (lane <- 0 until 4) {
    val cmd = io.cmd(lane).payload
    val write2 = cmd.op === WorkerBeeOp.REG_WRITE2
    when(acceptedWrites(lane)) {
      writeRegister(cmd.reg0Addr, cmd.data0)
      when(write2) { writeRegister(cmd.reg1Addr, cmd.data1) }
      val clear0 = cmd.reg0Addr === WorkerBeeRegAddr.IrqClear.U
      val clear1 = write2 && cmd.reg1Addr === WorkerBeeRegAddr.IrqClear.U
      when(clear0 || clear1) {
        clearBits := Mux(clear0, cmd.data0(10, 0), 0.U(11.W)) |
          Mux(clear1, cmd.data1(10, 0), 0.U(11.W))
      }
      val trigger0 = cmd.reg0Addr === WorkerBeeRegAddr.Start.U && cmd.data0.orR
      val trigger1 = write2 && cmd.reg1Addr === WorkerBeeRegAddr.Start.U && cmd.data1.orR
      when(trigger0 || trigger1) {
        startPending := true.B
        startPc := cmd.pc
        startTag := cmd.tag
        inFlight := true.B
      }
    }
  }

  val newEvents = Cat(writeConflict, io.dmaErrors.asUInt, io.configError, io.done)
  irqRaw := (irqRaw & ~clearBits) | newEvents
  when(clearBits(1)) { errorCode := 0.U }
  when(io.configError && (!irqRaw(1) || clearBits(1))) { errorCode := io.configErrorCode }
  when(io.done || io.configError) { inFlight := false.B }
}
