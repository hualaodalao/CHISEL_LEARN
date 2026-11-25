package loom.chi

import chisel3._
import chisel3.util._

/** Minimal AXI4 channel payloads for the CHI bridge (single burst, ID-tagged). */
class ChiAxiAddr(c: ChiConfig) extends Bundle {
  val addr = UInt(c.addrWidth.W)
  val id = UInt(c.txnIdWidth.W)
  val len = UInt(8.W)
}
class ChiAxiR(c: ChiConfig) extends Bundle {
  val data = UInt(c.datWidth.W)
  val id = UInt(c.txnIdWidth.W)
  val resp = UInt(2.W)
  val last = Bool()
}
class ChiAxiW(c: ChiConfig) extends Bundle {
  val data = UInt(c.datWidth.W)
  val strb = UInt(c.beWidth.W)
  val last = Bool()
}
class ChiAxiB(c: ChiConfig) extends Bundle {
  val id = UInt(c.txnIdWidth.W)
  val resp = UInt(2.W)
}

/** RN-side AXI read bridge: AR(64B burst) -> ReadNoSnp -> CompData flits ->
  * reassemble -> R beats. Maps one 64B CHI transaction to nFlits AXI beats.
  */
class ChiAxiReadBridge(c: ChiConfig) extends Module {
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)
  private val bw = math.max(1, log2Ceil(nFlits))

  val io = IO(new Bundle {
    val ar = Flipped(Decoupled(new ChiAxiAddr(c)))
    val r = Decoupled(new ChiAxiR(c))
    val req = Decoupled(new ChiReqFlit(c))
    val dat = Flipped(Decoupled(new ChiDatFlit(c)))
  })

  private val sIdle :: sReq :: sData :: sResp :: Nil = Enum(4)
  private val st = RegInit(sIdle)
  private val txn = Reg(UInt(c.txnIdWidth.W))
  private val addr = Reg(UInt(c.addrWidth.W))
  private val dataBuf = RegInit(0.U(512.W))
  private val flitCnt = RegInit(0.U(bw.W))
  private val beatIdx = RegInit(0.U(bw.W))

  io.ar.ready := st === sIdle
  io.req.valid := st === sReq
  io.req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
  io.req.bits.opcode := ChiOp.ReadNoSnp.U
  io.req.bits.txnId := txn
  io.req.bits.addr := addr
  io.req.bits.size := ChiSize.encode(64).U
  io.dat.ready := st === sData
  io.r.valid := st === sResp
  io.r.bits.data := (dataBuf >> (beatIdx * c.datWidth.U))(c.datWidth - 1, 0)
  io.r.bits.id := txn
  io.r.bits.resp := ChiRespErr.OK.U
  io.r.bits.last := beatIdx === (nFlits - 1).U

  switch(st) {
    is(sIdle) {
      when(io.ar.fire) {
        txn := io.ar.bits.id; addr := io.ar.bits.addr
        dataBuf := 0.U; flitCnt := 0.U; beatIdx := 0.U
        st := sReq
      }
    }
    is(sReq) { when(io.req.fire) { st := sData } }
    is(sData) {
      when(io.dat.fire) {
        dataBuf := dataBuf | (io.dat.bits.data.pad(512) << (io.dat.bits.dataId * 128.U))
        when(flitCnt === (nFlits - 1).U) { st := sResp }
        flitCnt := flitCnt + 1.U
      }
    }
    is(sResp) {
      when(io.r.fire) {
        when(beatIdx === (nFlits - 1).U) { st := sIdle }
        beatIdx := beatIdx + 1.U
      }
    }
  }
}

/** RN-side AXI write bridge: AW + W beats -> reassemble -> WriteNoSnp ->
  * DBIDResp -> NonCopyBackWrData flits (TxnID=DBID) -> Comp -> B.
  */
class ChiAxiWriteBridge(c: ChiConfig) extends Module {
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)
  private val bw = math.max(1, log2Ceil(nFlits))
  private val idStep = c.datWidth / 128

  val io = IO(new Bundle {
    val aw = Flipped(Decoupled(new ChiAxiAddr(c)))
    val w = Flipped(Decoupled(new ChiAxiW(c)))
    val b = Decoupled(new ChiAxiB(c))
    val req = Decoupled(new ChiReqFlit(c))
    val rsp = Flipped(Decoupled(new ChiRspFlit(c)))
    val dat = Decoupled(new ChiDatFlit(c))
  })

  private val sIdle :: sWcollect :: sReq :: sGrant :: sData :: sComp :: sResp :: Nil = Enum(7)
  private val st = RegInit(sIdle)
  private val txn = Reg(UInt(c.txnIdWidth.W))
  private val addr = Reg(UInt(c.addrWidth.W))
  private val dbid = Reg(UInt(c.txnIdWidth.W))
  private val dataBuf = RegInit(0.U(512.W))
  private val wCnt = RegInit(0.U(bw.W))
  private val beatIdx = RegInit(0.U(bw.W))

  io.aw.ready := st === sIdle
  io.w.ready := st === sWcollect
  io.req.valid := st === sReq
  io.req.bits := 0.U.asTypeOf(new ChiReqFlit(c))
  io.req.bits.opcode := ChiOp.WriteNoSnpFull.U
  io.req.bits.txnId := txn
  io.req.bits.addr := addr
  io.req.bits.size := ChiSize.encode(64).U
  io.rsp.ready := st === sGrant || st === sComp
  io.dat.valid := st === sData
  io.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))
  io.dat.bits.opcode := ChiOp.DatNonCopyBackWrData.U
  io.dat.bits.txnId := dbid
  io.dat.bits.dataId := beatIdx * idStep.U
  io.dat.bits.data := (dataBuf >> (beatIdx * c.datWidth.U))(c.datWidth - 1, 0)
  io.dat.bits.be := ((1L << c.beWidth) - 1).U
  io.b.valid := st === sResp
  io.b.bits.id := txn
  io.b.bits.resp := ChiRespErr.OK.U

  switch(st) {
    is(sIdle) {
      when(io.aw.fire) {
        txn := io.aw.bits.id; addr := io.aw.bits.addr
        dataBuf := 0.U; wCnt := 0.U; beatIdx := 0.U
        st := sWcollect
      }
    }
    is(sWcollect) {
      when(io.w.fire) {
        dataBuf := dataBuf | (io.w.bits.data.pad(512) << (wCnt * c.datWidth.U))
        when(io.w.bits.last) { st := sReq }
        wCnt := wCnt + 1.U
      }
    }
    is(sReq) { when(io.req.fire) { st := sGrant } }
    is(sGrant) { when(io.rsp.fire) { dbid := io.rsp.bits.dbid; st := sData } }
    is(sData) {
      when(io.dat.fire) {
        when(beatIdx === (nFlits - 1).U) { st := sComp }
        beatIdx := beatIdx + 1.U
      }
    }
    is(sComp) { when(io.rsp.fire) { st := sResp } }
    is(sResp) { when(io.b.fire) { st := sIdle } }
  }
}