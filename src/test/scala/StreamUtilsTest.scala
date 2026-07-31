import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** 综合演练 Stream 库核心 API 的 demo 模块 */
class StreamDemo extends Module {
  val io = IO(new Bundle {
    val a = slave(Stream(UInt(8.W)))
    val b = slave(Stream(UInt(8.W)))
    val sum = master(Stream(UInt(8.W)))
    val dec = Decoupled(UInt(8.W))
  })

  // queue + m2sPipe + 轮询仲裁
  val arbitrated = StreamArbiterFactory().roundRobin.transactionLock
    .onArgs(io.a.queue(4), io.b.m2sPipe())

  // 一分二，各自打不同类型的拍，再 join 回来
  val (f0, f1) = StreamFork2(arbitrated)
  val joined = StreamJoin.vec(Seq(f0.s2mPipe(), f1.halfPipe()))

  // payload 变换后输出
  io.sum << joined.map(v => v(0) + v(1))

  // 与 DecoupledIO 互转 + validPipe + throwWhen
  val ev = Wire(Stream(UInt(8.W)))
  ev.valid := io.b.valid
  ev.payload := io.b.payload // 旁路观察，不参与 io.b 握手
  io.dec <> ev.validPipe().throwWhen(ev.payload === 0.U).toDecoupled
}

/** 综合演练 Flow 库核心 API 的 demo 模块 */
class FlowDemo extends Module {
  val io = IO(new Bundle {
    val in = slave(Flow(UInt(8.W)))
    val out = master(Flow(UInt(8.W)))
    val buffered = master(Stream(UInt(8.W)))
    val overflow = Output(Bool())
    val last = Output(UInt(8.W))
  })

  // m2sPipe + map + takeWhen 链
  val piped = io.in.m2sPipe(holdPayload = true).map(_ + 1.U).takeWhen(io.in.payload =/= 0.U)

  // Flow 寄存器 + push/default
  val reg = RegFlow(UInt(8.W))
  reg.default(0.U)
  when(piped.fire) { reg.push(piped.payload) }

  // FlowArbiter（valid 互斥：takeWhen/throwWhen 互补）
  val other = io.in.throwWhen(io.in.payload =/= 0.U).stage()
  io.out << FlowArbiter(Seq(piped.stage(), other.m2sPipe()))

  // toStream + FIFO 缓冲 + overflow 指示
  io.buffered << io.in.toStream(io.overflow, fifoSize = 4, overflowOccupancyAt = 3)

  // toReg：fire 时锁存 payload
  io.last := io.in.toReg(0.U)
}

class StreamUtilsTest extends AnyFlatSpec with Matchers {
  behavior of "Stream utils (SpinalHDL port)"

  it should "elaborate StreamDemo to SystemVerilog" in {
    val verilog = ChiselStage.emitSystemVerilog(new StreamDemo)
    verilog should include("module StreamDemo")
    verilog should include("StreamArbiter")
    verilog should include("StreamFifo")
    verilog should include("StreamFork")
  }

  it should "elaborate FlowDemo to SystemVerilog" in {
    val verilog = ChiselStage.emitSystemVerilog(new FlowDemo)
    verilog should include("module FlowDemo")
    verilog should include("StreamFifo")
  }
}
