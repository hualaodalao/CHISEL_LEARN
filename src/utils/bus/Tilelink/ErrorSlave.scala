import chisel3._
import chisel3.util._
import tilelink._

/** 恒定返回 denied 响应的 TileLink 从设备，移植自 SpinalHDL
  * spinal.lib.bus.tilelink.ErrorSlave。
  *
  * a 通道经 halfPipe 缓冲一拍；burst PUT 只在最后一拍消耗完后
  * 回一个 denied 的响应，GET 则按拍回 denied 的数据响应。
  */
class ErrorSlave(bp: BusParameter) extends Module {
  require(!bp.withBCE)

  val io = IO(new Bundle {
    val bus = slave(Bus(bp))
  })

  val buffer = io.bus.a.halfPipe()
  buffer.ready := io.bus.d.fire && io.bus.d.isLast()
  io.bus.d.valid := buffer.valid && buffer.isLast()
  io.bus.d.payload.opcode := MuxLookup(buffer.payload.opcode.asUInt, Opcode.D.ACCESS_ACK)(Seq(
    Opcode.A.PUT_FULL_DATA.asUInt    -> Opcode.D.ACCESS_ACK,
    Opcode.A.PUT_PARTIAL_DATA.asUInt -> Opcode.D.ACCESS_ACK,
    Opcode.A.GET.asUInt              -> Opcode.D.ACCESS_ACK_DATA,
    Opcode.A.ACQUIRE_BLOCK.asUInt    -> Opcode.D.GRANT_DATA,
    Opcode.A.ACQUIRE_PERM.asUInt     -> Opcode.D.GRANT
  ))
  io.bus.d.payload.param := 0.U
  io.bus.d.payload.source := buffer.payload.source
  io.bus.d.payload.sink := 0.U(bp.sinkWidth.W)
  io.bus.d.payload.size := buffer.payload.size
  io.bus.d.payload.denied := true.B
  if (bp.withDataD) {
    io.bus.d.payload.data.get := DontCare
    io.bus.d.payload.corrupt.get := DontCare
  }
}
