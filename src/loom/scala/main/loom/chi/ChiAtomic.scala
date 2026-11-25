package loom.chi

import chisel3._
import chisel3.util._

/** Atomic ALU operations and a pure reference model for golden anchors. */
object ChiAtomicOp {
  val ops: Seq[String] = Seq("Add", "Clr", "Eor", "Set", "Smax", "Smin", "Umax", "Umin")
  def code(op: String): Int = ops.indexOf(op)

  /** Width-aware reference: signed ops interpret the top bit as sign. */
  def ref(op: String, old: BigInt, operand: BigInt, width: Int): BigInt = {
    val mask = (BigInt(1) << width) - 1
    val o = old & mask
    val b = operand & mask
    def signed(x: BigInt): BigInt = if (x.testBit(width - 1)) x - (BigInt(1) << width) else x
    val r = op match {
      case "Add"  => o + b
      case "Clr"  => o & ~b
      case "Eor"  => o ^ b
      case "Set"  => o | b
      case "Smax" => if (signed(o) >= signed(b)) o else b
      case "Smin" => if (signed(o) <= signed(b)) o else b
      case "Umax" => if (o >= b) o else b
      case "Umin" => if (o <= b) o else b
      case _      => o
    }
    r & mask
  }
}

/** Combinational 8-op atomic ALU on a width-bit word. */
class ChiAtomicAlu(width: Int) extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(3.W))
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val y = Output(UInt(width.W))
  })
  private val sa = io.a.asSInt
  private val sb = io.b.asSInt
  io.y := MuxLookup(io.op, io.a)(Seq(
    0.U -> (io.a +& io.b)(width - 1, 0),
    1.U -> (io.a & ~io.b),
    2.U -> (io.a ^ io.b),
    3.U -> (io.a | io.b),
    4.U -> Mux(sa >= sb, io.a, io.b),
    5.U -> Mux(sa <= sb, io.a, io.b),
    6.U -> Mux(io.a >= io.b, io.a, io.b),
    7.U -> Mux(io.a <= io.b, io.a, io.b)
  ))
}

/** SN-side atomic execution engine (D2). Single-cycle read-modify-write on a
  * memory word; returns the OLD value (Load/Swap/Compare). Device memory is
  * rejected with NDERR and leaves memory unchanged (CHI compliance).
  */
class ChiAtomicEngine(width: Int) extends Module {
  val io = IO(new Bundle {
    val cmd = Flipped(Decoupled(new Bundle {
      val op = UInt(3.W)
      val operand = UInt(width.W)
      val device = Bool()
    }))
    val retValid = Output(Bool())
    val retVal = Output(UInt(width.W))
    val respErr = Output(UInt(2.W))
    val memWord = Output(UInt(width.W))
  })

  private val mem = RegInit(0.U(width.W))
  private val alu = Module(new ChiAtomicAlu(width))
  alu.io.op := io.cmd.bits.op
  alu.io.a := mem
  alu.io.b := io.cmd.bits.operand

  io.cmd.ready := true.B
  private val retReg = RegInit(0.U(width.W))
  private val vReg = RegInit(false.B)
  private val errReg = RegInit(0.U(2.W))

  when(io.cmd.fire) {
    vReg := true.B
    retReg := mem
    when(io.cmd.bits.device) {
      errReg := ChiRespErr.NDERR.U
    }.otherwise {
      errReg := ChiRespErr.OK.U
      mem := alu.io.y
    }
  }.otherwise {
    vReg := false.B
  }

  io.retValid := vReg
  io.retVal := retReg
  io.respErr := errReg
  io.memWord := mem
}