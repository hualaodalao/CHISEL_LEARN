import chisel3._
import chisel3.util._

object WbOsM1Phase extends ChiselEnum {
  val sIdle, sRun, sTail, sDone = Value
}

class WbOsM1Sequencer(
  T: Int = 16,
  aEffW: Int = 16,
  bW: Int = 16,
  scaleAW: Int = 32,
  scaleBW: Int = 32,
  ktW: Int = 16,
  hasMx: Boolean = true
) extends Module {
  require(aEffW > 0, "WbOsM1Sequencer: aEffW must be positive")
  require(scaleAW > 0 && scaleAW % 8 == 0, "WbOsM1Sequencer: scaleAW must contain whole bytes")

  import WbOsM1Phase._

  private val laneW = log2Ceil(T)

  val io = IO(new Bundle {
    val aIn = slave(Stream(UInt((T * aEffW).W)))
    val scaleAIn = if (hasMx) Some(slave(Stream(UInt(scaleAW.W)))) else None
    val bIn = slave(Stream(UInt((T * bW).W)))
    val scaleBIn = if (hasMx) Some(slave(Stream(UInt(scaleBW.W)))) else None
    val mxMode = Input(Bool())
    val flush = Input(Bool())
    val start = Input(Bool())
    val kTiles = Input(UInt(ktW.W))
    val consume = Input(Vec(T, Bool()))
    val done = Input(Vec(T, Bool()))
    // scale 字节选择：byte = word[idx*8 +: 8]，idx = lane*step/blk；scaleOne 强制 127（E8M0 1.0）
    val scaleOne = Input(Bool())
    val scaleAStep = Input(UInt(3.W))
    val scaleBStep = Input(UInt(3.W))
    val scaleABlk = Input(UInt(6.W))
    val scaleBBlk = Input(UInt(6.W))
    // scaleB 基址字对齐 ⟹ DMA 推真实对齐字，字节索引须补字内偏移；非对齐 ⟹ DMA 已
    //   gather 移位重组，偏移必须归 0。与 Executor WS 注入点同源。
    val scaleBAligned = Input(Bool())

    val advance = Output(Bool())
    val aOut = Output(UInt(aEffW.W))
    val aScaleWord = Output(UInt(scaleAW.W))
    val aScaleByte = Output(UInt(8.W))
    val aLane = Output(UInt(laneW.W))
    val aTokenValid = Output(Bool())
    val aLast = Output(Bool())
    val bOut = Output(Vec(T, UInt(bW.W)))
    val scaleBOut = Output(Vec(T, UInt(scaleBW.W)))
    val scaleBByte = Output(Vec(T, UInt(8.W)))
    val bLane = Output(Vec(T, UInt(laneW.W)))
    val headValid = Output(Vec(T, Bool()))
    val headKt = Output(Vec(T, UInt(ktW.W)))
    val resultsReady = Output(Bool())
    val phase = Output(WbOsM1Phase())
  })

  private val feeder = Module(new WbOsM1Feeder(T, bW, scaleBW, ktW, hasMx))
  feeder.io.bIn << io.bIn
  if (hasMx) feeder.io.scaleBIn.get << io.scaleBIn.get
  feeder.io.mxMode := io.mxMode
  feeder.io.flush := io.flush
  feeder.io.consume := io.consume
  io.bOut := feeder.io.bOut
  io.scaleBOut := feeder.io.scaleOut
  io.bLane := feeder.io.laneOut
  io.headValid := feeder.io.headValid
  io.headKt := feeder.io.headKt

  private val phase = RegInit(sIdle)
  private val aWindow = Reg(UInt((T * aEffW).W))
  private val aScale = Reg(UInt(scaleAW.W))
  private val aValid = RegInit(false.B)
  private val aLane = RegInit(0.U(laneW.W))
  private val aKt = RegInit(0.U(ktW.W))
  private val tileKt = RegInit(0.U(ktW.W))
  private val kTilesReg = RegInit(0.U(ktW.W))

  private val running = phase === sRun
  private val tail = phase === sTail
  private val allDone = io.done.asUInt.andR
  private val allHeads = feeder.io.headValid.asUInt.andR
  private val tailReady = io.done.zip(feeder.io.headValid).map { case (d, h) => d || h }.reduce(_ && _)

  private val tokenValid = running && aValid
  private val advance = !io.flush && ((tokenValid && allHeads) || (tail && !allDone && tailReady))
  private val inject = advance && tokenValid
  private val lastLane = aLane === (T - 1).U
  private val lastWindow = tileKt === kTilesReg - 1.U

  private val scaleAValid = if (hasMx) io.scaleAIn.get.valid else true.B
  private val mxActive = if (hasMx) io.mxMode else false.B
  // The final lane of the captured window may hand its slot to the next window in the same cycle.
  private val aSpace = !aValid || (inject && lastLane)
  private val aCapture = !io.flush && aSpace && io.aIn.valid && (!mxActive || scaleAValid)
  io.aIn.ready := aCapture
  if (hasMx) io.scaleAIn.get.ready := mxActive && aCapture

  when(io.flush) {
    phase := sIdle
    aValid := false.B
    aLane := 0.U
    aKt := 0.U
    tileKt := 0.U
  }.otherwise {
    when(aCapture) {
      aWindow := io.aIn.payload
      aScale := (if (hasMx) Mux(mxActive, io.scaleAIn.get.payload, 0.U(scaleAW.W)) else 0.U(scaleAW.W))
    }
    when(aCapture) {
      aValid := true.B
    }.elsewhen(inject && lastLane) {
      aValid := false.B
    }
    when(inject) {
      aLane := aLane + 1.U
      when(lastLane) {
        aKt := aKt + 1.U
        tileKt := tileKt + 1.U
      }
    }
    when(io.start) {
      phase := sRun
      kTilesReg := io.kTiles
      tileKt := 0.U
    }.elsewhen(inject && lastLane && lastWindow) {
      phase := sTail
    }.elsewhen(tail && allDone) {
      phase := sDone
    }
  }

  io.advance := advance
  io.aOut := aWindow.asTypeOf(Vec(T, UInt(aEffW.W)))(aLane)
  io.aScaleWord := aScale
  // 选择源（aScale/aLane、槽头 scale 字/lane）暂停拍均保持 ⟹ 字节选择随之保持
  private def selByte(word: UInt, lane: UInt, step: UInt, blk: UInt, off: UInt): UInt =
    Mux(io.scaleOne, 127.U(8.W), (word >> (((lane * step) / blk + off) * 8.U))(7, 0))
  // scaleB 字内偏移：DMA 恒推 alignedBase 那个真实对齐字，故索引须补
  //   off = demand*(j*kTiles + kt) mod 4，demand = T*step/blk ∈{1,2,4}（整除 4 ⟹ 有用字节
  //   永不跨字，off ≤ 4-demand ⟹ idx+off ≤ 3）。列步进 sbRowOff = kTiles*demand 在
  //   K%64≠0 时非 4B 倍数 ⟹ off 逐列不同，必须按 j 算。
  //   ★kt 必须取本列槽头 headKt(j)：OS 第 j 列 token 比列 0 延迟 j 拍，各列当前窗口号不同，
  //   用列 0 的 tileKt 会让 j>0 全选错字节。headKt 是跨 N tile 全局计数，减 (aKt - tileKt)
  //   = nt*kTiles 换回本 tile 窗口号；只需 mod 4 ⟹ 低位加减即可，无除法器。
  private val scaleBDemand = (T.U * io.scaleBStep) / io.scaleBBlk
  private def bOff(j: Int): UInt = {
    val ktLocal = feeder.io.headKt(j) - aKt + tileKt
    val q = (j.U * io.kTiles + ktLocal)(1, 0)
    Mux(!io.scaleBAligned, 0.U(2.W),
      Mux(scaleBDemand === 4.U, 0.U(2.W),
        Mux(scaleBDemand === 2.U, Cat(q(0), 0.U(1.W)), q)))
  }
  io.aScaleByte := selByte(aScale, aLane, io.scaleAStep, io.scaleABlk, 0.U(2.W))
  for (j <- 0 until T)
    io.scaleBByte(j) := selByte(feeder.io.scaleOut(j), feeder.io.laneOut(j), io.scaleBStep, io.scaleBBlk, bOff(j))
  io.aLane := aLane
  io.aTokenValid := tokenValid
  io.aLast := tokenValid && lastLane && lastWindow
  io.resultsReady := (tail || phase === sDone) && allDone
  io.phase := phase

  for (j <- 0 until T) {
    assert(!io.consume(j) || advance, "WbOsM1Sequencer: consume requires advance")
    assert(!(io.consume(j) && io.done(j)), "WbOsM1Sequencer: column consumed after done")
  }
  assert(io.consume(0) === inject, "WbOsM1Sequencer: column 0 consume must equal A injection")
  assert(!io.consume(0) || (feeder.io.headKt(0) === aKt && feeder.io.laneOut(0) === aLane),
    "WbOsM1Sequencer: column 0 A and B windows must pair by kt and lane")
  assert(io.flush || !io.start || ((phase === sIdle || phase === sDone) && aLane === 0.U &&
    io.kTiles =/= 0.U && !io.done.asUInt.orR),
    "WbOsM1Sequencer: start requires idle or done phase, lane 0, nonzero kTiles and cleared columns")
}
