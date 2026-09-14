import chisel3._
import chisel3.util._

class HiveFusionMacInt(
  aW: Int = 16,
  bW: Int = 16,
  cW: Int = 48
) extends Module {

  val cEffW: Int = math.max(cW, bW)
  
    val io = IO(new Bundle {
    val a    = Input(UInt(aW.W))
    val b    = Input(UInt(bW.W))
    val cReg = Input(UInt(cEffW.W))
    val aFmt = Input(DataFormat())
    val bFmt = Input(DataFormat())
    val rnd  = Input(RoundingMode())
    val sum  = Output(UInt(cEffW.W))
  })

  val aIsInt8 = RegNext(io.aFmt === DataFormat.INT8)
  val aIsInt4 = RegNext(io.aFmt === DataFormat.INT4)
  val bIsInt8 = RegNext(io.bFmt === DataFormat.INT8)
  val bIsInt4 = RegNext(io.bFmt === DataFormat.INT4)

  val mul8_0_a = Mux(aIsInt8, io.a(7,0), Cat(Fill(5, io.a(3,3)),io.a(2,0))).asSInt
  val mul8_0_b = Mux(bIsInt8, io.b(7,0), Cat(Fill(5, io.b(3,3)),io.b(2,0))).asSInt
  val r0 = mul8_0_a * mul8_0_b

  val mul8_1_a = Mux(aIsInt8, io.a(15,8), Cat(Fill(5,io.a(7,7)),io.a(6,4))).asSInt
  val mul8_1_b = Mux(bIsInt8, io.b(15,8), Cat(Fill(5,io.b(7,7)),io.b(6,4))).asSInt
  val r1 = mul8_1_a * mul8_1_b

  val mul4_0_a = io.a(11,8).asSInt
  val mul4_0_b = io.b(11,8).asSInt
  val r2 = Mux(aIsInt4 & bIsInt4, mul4_0_a * mul4_0_b, 0.S(8.W))
  
  val mul4_1_a = io.a(15,12).asSInt
  val mul4_1_b = io.b(15,12).asSInt
  val r3 = Mux(aIsInt4 & bIsInt4, mul4_1_a * mul4_1_b, 0.S(8.W))

  io.sum := (r0 + r1 + r2 + r3 + io.cReg.asSInt).asUInt
  
}
