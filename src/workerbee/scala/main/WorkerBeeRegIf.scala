import chisel3._
import chisel3.util._

/** 寄存器地址映射。
  *
  * 分区意图：`0x00–0x0B` 是配置区，落在 Queen QWR2 的 6-bit regBase 窗口内（可相邻配对写）；
  *   状态/IRQ 区从 `0x40` 起，超出 QWR2 窗口 ⟹ 只能由 QWR1/QRD 的 10-bit regAddr 访问。
  *   启动不再有寄存器地址：由 `WorkerBeeOp.RUN` 命令触发（原 Start=0x10 已删除）。
  */
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
  val Status = 0x40
  val IrqRaw = 0x41
  val IrqMask = 0x42
  val IrqMasked = 0x43
  val IrqClear = 0x44
  val IrqErrorCode = 0x45
}

/** 配置通路（两组寄存器 + RUN 触发 + credit）。
  *
  * shadowReg：软件配置组。QWR1/QWR2 写它、QRD 读它，**执行期也可随时写读**（不检查 inFlight、
  *   不检查 credit）。它是软件视角的"我写了什么"，不是正在执行的配置。
  * liveReg：执行上下文。RUN 被接受的那一拍把 shadowReg 整组 dump 进来，`io.regFile` 恒取它。
  *   执行期只有 RUN 能改它 ⟹ 后续 QWR 不会扰动已提交配置（Queen 契约的设备侧义务）。
  *
  * credit：`io.cfgCredit` 是电平，表示还能接受的 RUN 数。本设备只有一组执行上下文 ⟹ 1 bit，
  *   RUN 被接受即占满（inFlight），直到 done/configError 才归还。执行期 credit=0，上游 RUN 停等，
  *   但 QWR/QRD 不受门控 ⟹ 下一次任务的配置准备仍可与本次执行重叠。
  *   若要让上游在执行期就提交下一个 RUN，需要第三组缓冲；当前不做。
  *
  * hwSignal（ Queen 契约 §9.1 ）尚未实现：设备应在 RUN 被接受时锁存 HW_SIGNAL_TARGET/VALID
  *   进该次工作的配置缓冲、随 completion 回传，error=1 时不得携带有效目标。待 Queen 侧硬件语义
  *   对齐后再开寄存器与端口，见 doc/spec.md 的待办记录。
  */
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
    val cfgCredit = Output(UInt(1.W))
  })

  private val shadowReg = RegInit(0.U.asTypeOf(new WorkerBeeRegister(cfg)))
  private val liveReg = RegInit(0.U.asTypeOf(new WorkerBeeRegister(cfg)))
  val inFlight = RegInit(false.B)
  val startPending = RegInit(false.B)
  val startPc = RegInit(0.U(32.W))
  val startTag = RegInit(0.U(8.W))
  val irqRaw = RegInit(0.U(11.W))
  val irqMask = RegInit("h7ff".U(11.W))
  val errorCode = RegInit(0.U(4.W))
  val irqMasked = irqRaw & ~irqMask

  io.regFile := liveReg
  io.start := startPending
  io.startPc := startPc
  io.startTag := startTag
  io.inFlight := inFlight
  io.irq := irqMasked.orR
  io.cfgCredit := Mux(inFlight, 0.U, 1.U)
  startPending := false.B

  def writeRegister(addr: UInt, data: UInt): Unit = {
    switch(addr) {
      is(WorkerBeeRegAddr.M.U) { shadowReg.m := data(15, 0) }
      is(WorkerBeeRegAddr.NK.U) { shadowReg.n := data(15, 0); shadowReg.k := data(31, 16) }
      is(WorkerBeeRegAddr.A.U) { shadowReg.aAddr := data }
      is(WorkerBeeRegAddr.B.U) { shadowReg.bAddr := data }
      is(WorkerBeeRegAddr.C.U) { shadowReg.cAddr := data }
      is(WorkerBeeRegAddr.Control.U) {
        shadowReg.aFmt := data(3, 0).asTypeOf(DataFormat())
        shadowReg.bFmt := data(7, 4).asTypeOf(DataFormat())
        shadowReg.cFmt := data(11, 8).asTypeOf(DataFormat())
        shadowReg.aAddrMode := data(12).asTypeOf(AddrMode())
        shadowReg.bAddrMode := data(13).asTypeOf(AddrMode())
        shadowReg.cAddrMode := data(14).asTypeOf(AddrMode())
        shadowReg.reluOn := data(15)
        shadowReg.rnd := data(18, 16).asTypeOf(RoundingMode())
        shadowReg.warmdown := data(20)
        shadowReg.warmup := data(21)
        shadowReg.aMxBlockSize := data(22).asTypeOf(MXBlockSize())
        shadowReg.bMxBlockSize := data(24).asTypeOf(MXBlockSize())
        shadowReg.cMxBlockSize := data(26).asTypeOf(MXBlockSize())
        shadowReg.osFmtKeep := data(27)
        shadowReg.loadWMode := data(28)
        shadowReg.requantOn := data(29)
        shadowReg.biasOn := data(30)
        shadowReg.osM1 := data(31)
      }
      is(WorkerBeeRegAddr.ScaleA.U) { shadowReg.aScaleAddr := data }
      is(WorkerBeeRegAddr.ScaleB.U) { shadowReg.bScaleAddr := data }
      is(WorkerBeeRegAddr.ScaleC.U) { shadowReg.cScaleAddr := data }
      is(WorkerBeeRegAddr.Bias.U) { shadowReg.biasAddr := data }
      is(WorkerBeeRegAddr.RqM.U) { shadowReg.rqMBase := data }
      is(WorkerBeeRegAddr.RqParam.U) {
        shadowReg.rqShift := data(5, 0)
        shadowReg.rqZp := data(31, 16).asSInt
      }
      is(WorkerBeeRegAddr.IrqMask.U) { irqMask := data(10, 0) }
    }
  }

  // QRD 恒读 shadowReg（软件视角），不读正在执行的 liveReg。
  def readRegister(addr: UInt): UInt = {
    val data = WireDefault(0.U(32.W))
    switch(addr) {
      is(WorkerBeeRegAddr.M.U) { data := shadowReg.m }
      is(WorkerBeeRegAddr.NK.U) { data := Cat(shadowReg.k, shadowReg.n) }
      is(WorkerBeeRegAddr.A.U) { data := shadowReg.aAddr }
      is(WorkerBeeRegAddr.B.U) { data := shadowReg.bAddr }
      is(WorkerBeeRegAddr.C.U) { data := shadowReg.cAddr }
      is(WorkerBeeRegAddr.Control.U) {
        data := Cat(shadowReg.osM1, shadowReg.biasOn, shadowReg.requantOn, shadowReg.loadWMode,
          shadowReg.osFmtKeep, shadowReg.cMxBlockSize.asUInt, 0.U(1.W), shadowReg.bMxBlockSize.asUInt,
          0.U(1.W), shadowReg.aMxBlockSize.asUInt, shadowReg.warmup, shadowReg.warmdown,
          0.U(1.W), shadowReg.rnd.asUInt, shadowReg.reluOn, shadowReg.cAddrMode.asUInt,
          shadowReg.bAddrMode.asUInt, shadowReg.aAddrMode.asUInt, shadowReg.cFmt.asUInt,
          shadowReg.bFmt.asUInt, shadowReg.aFmt.asUInt)
      }
      is(WorkerBeeRegAddr.ScaleA.U) { data := shadowReg.aScaleAddr }
      is(WorkerBeeRegAddr.ScaleB.U) { data := shadowReg.bScaleAddr }
      is(WorkerBeeRegAddr.ScaleC.U) { data := shadowReg.cScaleAddr }
      is(WorkerBeeRegAddr.Bias.U) { data := shadowReg.biasAddr }
      is(WorkerBeeRegAddr.RqM.U) { data := shadowReg.rqMBase }
      is(WorkerBeeRegAddr.RqParam.U) { data := Cat(shadowReg.rqZp.asUInt, 0.U(10.W), shadowReg.rqShift) }
      is(WorkerBeeRegAddr.Status.U) { data := Cat(io.progress, 0.U(13.W), inFlight, 0.U(1.W), io.busy) }
      is(WorkerBeeRegAddr.IrqRaw.U) { data := irqRaw }
      is(WorkerBeeRegAddr.IrqMask.U) { data := irqMask }
      is(WorkerBeeRegAddr.IrqMasked.U) { data := irqMasked }
      is(WorkerBeeRegAddr.IrqErrorCode.U) { data := errorCode }
    }
    data
  }

  // 只读寄存器拒写。配置写不再受 inFlight 门控——执行期写的是 shadowReg，碰不到 liveReg。
  def rejectWrite(addr: UInt): Bool =
    addr === WorkerBeeRegAddr.Status.U || addr === WorkerBeeRegAddr.IrqRaw.U ||
      addr === WorkerBeeRegAddr.IrqMasked.U || addr === WorkerBeeRegAddr.IrqErrorCode.U

  val respPending = RegInit(VecInit(Seq.fill(4)(false.B)))
  val readPending = RegInit(VecInit(Seq.fill(4)(false.B)))
  val respData = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val respErr = RegInit(VecInit(Seq.fill(4)(false.B)))
  val respLane = PriorityEncoder(respPending.asUInt)
  val acceptBatch = !readPending.asUInt.orR && !respPending.asUInt.orR

  // 同拍最多接受一条 RUN：两条 RUN 会各自 ack 却只 dump/启动一次，语义上多算一次提交。
  //   grant 只看 valid（不看 fire），不构成 ready↔valid 组合环。
  val runPresent = VecInit((0 until 4).map { l =>
    io.cmd(l).valid && io.cmd(l).payload.op === WorkerBeeOp.RUN
  })
  val runGrant = PriorityEncoderOH(runPresent.asUInt)
  for (lane <- 0 until 4) {
    // isRun 必须并入 valid：否则空闲 lane 残留的 RUN payload 会让它的 ready 无谓拉低，
    //   上游按 ready 决定是否派发时会误判该 lane 不可用。
    val isRun = io.cmd(lane).valid && io.cmd(lane).payload.op === WorkerBeeOp.RUN
    io.cmd(lane).ready := acceptBatch && (!isRun || (!inFlight && runGrant(lane)))
  }

  io.resp.valid := respPending.asUInt.orR
  io.resp.payload.data := respData(respLane)
  io.resp.payload.err := respErr(respLane)
  io.resp.payload.laneId := respLane
  when(io.resp.fire) {
    respPending(respLane) := false.B
    readPending(respLane) := false.B
  }

  val acceptedWrites = Wire(Vec(4, Bool()))
  val acceptedRuns = Wire(Vec(4, Bool()))
  for (lane <- 0 until 4) {
    val stream = io.cmd(lane)
    val cmd = stream.payload
    val write1 = cmd.op === WorkerBeeOp.REG_WRITE1
    val write2 = cmd.op === WorkerBeeOp.REG_WRITE2
    val read = cmd.op === WorkerBeeOp.REG_READ
    val run = cmd.op === WorkerBeeOp.RUN
    val rejected = rejectWrite(cmd.reg0Addr) || (write2 && rejectWrite(cmd.reg1Addr))
    acceptedWrites(lane) := stream.fire && (write1 || write2) && !rejected
    acceptedRuns(lane) := stream.fire && run
    when(stream.fire) {
      respPending(lane) := true.B
      readPending(lane) := read
      respData(lane) := Mux(read, readRegister(cmd.reg0Addr), 0.U)
      respErr(lane) := (write1 || write2) && rejected
    }
  }

  def hasWriteTarget(addr: UInt): Bool =
    addr <= WorkerBeeRegAddr.RqParam.U || addr === WorkerBeeRegAddr.IrqMask.U ||
      addr === WorkerBeeRegAddr.IrqClear.U

  val writeTargets = (0 until 4).map { lane =>
    val cmd = io.cmd(lane).payload
    Seq(
      (acceptedWrites(lane) && hasWriteTarget(cmd.reg0Addr), cmd.reg0Addr),
      (acceptedWrites(lane) && cmd.op === WorkerBeeOp.REG_WRITE2 &&
        hasWriteTarget(cmd.reg1Addr), cmd.reg1Addr))
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
    }
    // RUN：dump shadow → live 并启动。io.regFile 与 startPending 同拍更新，故下游
    //   calcConfig 在 startIn 拍看到的就是本次提交的配置。
    when(acceptedRuns(lane)) {
      liveReg := shadowReg
      startPending := true.B
      startPc := cmd.pc
      startTag := cmd.tag
      inFlight := true.B
    }
  }

  val newEvents = Cat(writeConflict, io.dmaErrors.asUInt, io.configError, io.done)
  irqRaw := (irqRaw & ~clearBits) | newEvents
  when(clearBits(1)) { errorCode := 0.U }
  when(io.configError && (!irqRaw(1) || clearBits(1))) { errorCode := io.configErrorCode }
  when(io.done || io.configError) { inFlight := false.B }
}