package loom.chi

import chisel3._
import chisel3.util._

/** Subordinate-side write sink implementing the CHI pull model:
  * REQ(WriteNoSnp) -> DBIDResp(grant) -> NonCopyBackWrData(TxnID=DBID) -> Comp.
  * Data flits are placed by DataID into a 64B reassembly buffer; the DBID is
  * held from grant until completion (ChiIdPool backpressure when full).
  * S6 handles full 64B transactions; variable size lands with the bridge.
  */
class ChiWriteSink(c: ChiConfig, depth: Int) extends Module {
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)
  private val bw = math.max(1, log2Ceil(nFlits))

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ChiReqFlit(c)))
    val rsp = Decoupled(new ChiRspFlit(c))
    val dat = Flipped(Decoupled(new ChiDatFlit(c)))
    val wrDone = Decoupled(UInt(512.W))
  })

  private val pool = Module(new ChiIdPool(depth, c.txnIdWidth))
  private val sIdle :: sGrant :: sData :: sComp :: Nil = Enum(4)
  private val st = RegInit(sIdle)
  private val reqTxn = Reg(UInt(c.txnIdWidth.W))
  private val reqSrc = Reg(UInt(c.nodeIdWidth.W))
  private val dbid = Reg(UInt(c.txnIdWidth.W))
  private val beat = RegInit(0.U(bw.W))
  private val dataBuf = RegInit(0.U(512.W))

  io.req.ready := st === sIdle && pool.io.canAlloc
  pool.io.alloc := io.req.fire
  pool.io.release := st === sComp && io.rsp.fire && io.wrDone.fire
  pool.io.releaseId := dbid

  io.rsp.valid := false.B
  io.rsp.bits := 0.U.asTypeOf(new ChiRspFlit(c))
  io.dat.ready := st === sData
  io.wrDone.valid := st === sComp
  io.wrDone.bits := dataBuf

  switch(st) {
    is(sIdle) {
      when(io.req.fire) {
        reqTxn := io.req.bits.txnId
        reqSrc := io.req.bits.srcId
        dbid := pool.io.allocId
        beat := 0.U
        dataBuf := 0.U
        st := sGrant
      }
    }
    is(sGrant) {
      io.rsp.valid := true.B
      io.rsp.bits.opcode := ChiOp.RspDBIDResp.U
      io.rsp.bits.txnId := reqTxn
      io.rsp.bits.dbid := dbid
      io.rsp.bits.tgtId := reqSrc
      when(io.rsp.fire) { st := sData }
    }
    is(sData) {
      when(io.dat.fire) {
        val off = io.dat.bits.dataId * 128.U
        dataBuf := dataBuf | (io.dat.bits.data.pad(512) << off)
        when(beat === (nFlits - 1).U) { st := sComp }
        beat := beat + 1.U
      }
    }
    is(sComp) {
      io.rsp.valid := true.B
      io.rsp.bits.opcode := ChiOp.RspComp.U
      io.rsp.bits.txnId := reqTxn
      io.rsp.bits.tgtId := reqSrc
      when(io.rsp.fire && io.wrDone.fire) { st := sIdle }
    }
  }
}

/** Subordinate-side read source: REQ(ReadNoSnp) -> CompData flits. The 64B
  * read word is sliced into DAT flits, each tagged with its DataID position.
  */
class ChiReadSource(c: ChiConfig) extends Module {
  private val nFlits = ChiWidths.flitCount(64, c.datWidth)
  private val bw = math.max(1, log2Ceil(nFlits))
  private val idStep = c.datWidth / 128

  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new ChiReqFlit(c)))
    val dat = Decoupled(new ChiDatFlit(c))
    val rdData = Input(UInt(512.W))
  })

  private val sIdle :: sData :: Nil = Enum(2)
  private val st = RegInit(sIdle)
  private val reqTxn = Reg(UInt(c.txnIdWidth.W))
  private val reqSrc = Reg(UInt(c.nodeIdWidth.W))
  private val beat = RegInit(0.U(bw.W))
  private val word = Reg(UInt(512.W))

  io.req.ready := st === sIdle
  io.dat.valid := st === sData
  io.dat.bits := 0.U.asTypeOf(new ChiDatFlit(c))

  switch(st) {
    is(sIdle) {
      when(io.req.fire) {
        reqTxn := io.req.bits.txnId
        reqSrc := io.req.bits.srcId
        word := io.rdData
        beat := 0.U
        st := sData
      }
    }
    is(sData) {
      io.dat.bits.opcode := ChiOp.DatCompData.U
      io.dat.bits.txnId := reqTxn
      io.dat.bits.tgtId := reqSrc
      io.dat.bits.dataId := beat * idStep.U
      io.dat.bits.data := (word >> (beat * c.datWidth.U))(c.datWidth - 1, 0)
      io.dat.bits.be := ((1L << c.beWidth) - 1).U
      when(io.dat.fire) {
        when(beat === (nFlits - 1).U) { st := sIdle }
        beat := beat + 1.U
      }
    }
  }
}

/** Split a byte span into <=64B CHI transactions (AXI burst transactionization). */
object ChiBurstSplit {
  final case class Txn(addr: Long, bytes: Int)
  def split(addr: Long, totalBytes: Int): Seq[Txn] = {
    require(totalBytes >= 1, "burst must carry >= 1 byte")
    val b = scala.collection.mutable.ArrayBuffer.empty[Txn]
    var a = addr
    var rem = totalBytes
    while (rem > 0) {
      val n = math.min(rem, 64)
      b += Txn(a, n)
      a += n
      rem -= n
    }
    b.toSeq
  }
}