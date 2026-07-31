import chisel3._
import chisel3.util._


class Op(width: Int) extends Module {
  val io = IO(new Bundle {
      val in = Flipped(DecoupledIO(UInt(width.W)))
      val out = DecoupledIO(UInt(width.W))
  })
  
  io.out.valid := io.in.valid
  io.out.bits  := io.in.bits + 1.U
  io.in.ready := io.out.ready
  
}

class MyBundle extends Bundle {
  val en  = Input(Bool())
  val out = Output(UInt(8.W))
}

class MyRTL() extends Module {
  val io = IO(new Bundle{
    val in0 = Flipped(DecoupledIO(UInt(8.W)))
    val in1 = Flipped(DecoupledIO(UInt(8.W)))
    val out = DecoupledIO(UInt(8.W))
  }
  )

  val io2 = IO(new Bundle{
    val in0 = slave (Stream(UInt(8.W)))
    val in1 = slave (Stream(UInt(8.W)))
    val out = master (Stream(UInt(8.W)))
  })
  
  val arb = Module(new RRArbiter(UInt(8.W), 2))
  val pipedA = Queue(io.in0, 1)
  val pipedB = Queue(io.in1, 2)
  
  arb.io.in(0) <> pipedA
  arb.io.in(1) <> pipedB
  
  val op = Module(new Op(8))  
  op.io.in <> arb.io.out
  io.out <> Queue(op.io.out, 1)

  
  val arb2 = StreamArbiterFactory.roundRobin.onArgs(io2.in0.s2mPipe(), io2.in1.s2mPipe().m2sPipe())
  io2.out << arb2
  
  
}
