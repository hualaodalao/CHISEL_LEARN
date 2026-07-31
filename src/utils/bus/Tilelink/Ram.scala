import chisel3._
import chisel3.util._
import tilelink._

/** TileLink 单口 RAM 从设备，移植自 SpinalHDL spinal.lib.bus.tilelink.Ram。
  *
  * 与原版的差异：
  *   - 原版基于 spinal.lib.pipeline 框架（cmd/rsp 两级 + M2S 打拍），
  *     这里重写为手写的两级流水：cmd 级（组合 + GET 突发 FSM）与 rsp 级（寄存器）；
  *   - `Mem.readWriteSyncPort(maskWidth)` → `SyncReadMem.readWrite`（按字节掩码）；
  *   - OrderingCmd/OrderingTag（coherent 依赖）未移植。
  *
  * 支持 GET / PUT_FULL / PUT_PARTIAL（含 burst）；burst PUT 只在最后一拍回响应。
  */
class Ram(p: NodeParameters, bytes: Int) extends Module {
  val io = IO(new Bundle {
    val up = slave(Bus(p))
  })

  val bp = io.up.p
  require(bp.withDataA && bp.withDataD, "Ram requires get/put capable bus")
  require(!bp.withBCE, "Ram does not support coherency channels")
  require(bytes % bp.dataBytes == 0 && bytes >= bp.sizeBytes)

  val wordCount = bytes / bp.dataBytes
  val memAddressWidth = log2Ceil(wordCount)
  val mem = SyncReadMem(wordCount, Vec(bp.dataBytes, UInt(8.W)))

  val a = io.up.a
  val d = io.up.d

  // ---- cmd 级：与 a 通道握手，GET 突发时自持 valid 逐拍读 ----
  val cmdValid = WireDefault(a.valid)
  val cmdReady = Wire(Bool()) // 由 rsp 级给出
  val cmdFire = cmdValid && cmdReady
  val cmdIsGet = WireDefault(Opcode.A.isGet(a.payload.opcode))
  val cmdSize = WireDefault(a.payload.size)
  val cmdSource = WireDefault(a.payload.source)
  val cmdLast = Wire(Bool())

  a.ready := cmdReady
  cmdLast := true.B

  val addressShifted = (a.payload.address >> bp.dataBytesLog2Up).asUInt
  val portAddress = Wire(UInt(memAddressWidth.W))

  val withFsm = bp.beatMax != 1
  if (!withFsm) {
    portAddress := tilelink.resized(addressShifted, memAddressWidth)
  } else {
    val counter = RegInit(0.U(bp.beatWidth.W))
    val rAddress = Reg(UInt(memAddressWidth.W))
    val rSize = Reg(UInt(bp.sizeWidth.W))
    val rSource = Reg(UInt(bp.sourceWidth.W))
    val rIsGet = Reg(Bool())
    val busy = counter =/= 0.U

    when(busy && rIsGet) {
      a.ready := false.B
      cmdValid := true.B
    }

    when(a.fire && !busy) {
      rSize := a.payload.size
      rSource := a.payload.source
      rIsGet := Opcode.A.isGet(a.payload.opcode)
      rAddress := tilelink.resized(addressShifted, memAddressWidth)
    }

    when(busy) {
      cmdSize := rSize
      cmdSource := rSource
      cmdIsGet := rIsGet
    }
    cmdLast := counter === sizeToBeatMinusOne(bp, cmdSize)

    when(cmdFire) {
      counter := counter +% 1.U
      when(cmdLast) {
        counter := 0.U
      }
    }
    portAddress := tilelink.resized(
      Mux(busy, rAddress, tilelink.resized(addressShifted, memAddressWidth)) | counter.pad(memAddressWidth),
      memAddressWidth)
  }

  val rdata = mem.readWrite(
    portAddress,
    a.payload.data.get.asTypeOf(Vec(bp.dataBytes, UInt(8.W))),
    a.payload.mask.get.asBools,
    cmdFire,
    !cmdIsGet
  )

  // ---- rsp 级：M2S 打拍；写突发只在最后一拍回响应 ----
  val rspValid = RegInit(false.B)
  val rspIsGet = Reg(Bool())
  val rspSize = Reg(UInt(bp.sizeWidth.W))
  val rspSource = Reg(UInt(bp.sourceWidth.W))
  val rspLast = Reg(Bool())

  val takeIt = rspLast || rspIsGet
  cmdReady := !rspValid || d.ready || !takeIt
  when(cmdReady) {
    rspValid := cmdValid
  }
  when(cmdFire) {
    rspIsGet := cmdIsGet
    rspSize := cmdSize
    rspSource := cmdSource
    rspLast := cmdLast
  }

  d.valid := rspValid && takeIt
  d.payload.opcode := Mux(rspIsGet, Opcode.D.ACCESS_ACK_DATA, Opcode.D.ACCESS_ACK)
  d.payload.param := 0.U
  d.payload.source := rspSource
  d.payload.sink := 0.U(bp.sinkWidth.W)
  d.payload.size := rspSize
  d.payload.denied := false.B
  d.payload.corrupt.get := false.B
  d.payload.data.get := rdata.asUInt
}
