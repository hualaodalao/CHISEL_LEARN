package loom.rtl

import chisel3._
import chisel3.util._

/** Protocol-neutral command: one burst of `beats` single-word transactions
  * starting at `addr`. The fabric transports one word per request, so a
  * shell serializes bursts into beats and merges the responses.
  */
class LmShellCmd(val addrWidth: Int, val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth)
    extends Bundle {
  val write = Bool()
  val addr = UInt(addrWidth.W)
  val beats = UInt(9.W)
  val id = UInt(tagWidth.W)
}

class LmShellBeat(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val last = Bool()
}

class LmShellResp extends Bundle {
  val err = Bool()
}

class LmRData(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val err = Bool()
  val last = Bool()
}

/** Burst engine shared by all protocol shells.
  *
  * A command is latched, its beats are issued one by one on the fabric
  * request channel (write data travels with each request), and the matching
  * responses are drained. Writes produce one aggregated response; reads
  * produce one rdata beat per response. Requests are only ever generated in
  * the SEND state, never from the response path (G4).
  */
class LmShellCore(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    srcId: Int,
    tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Module {
  require(dataWidth >= 8 && (dataWidth & (dataWidth - 1)) == 0)
  private val busBytesLog2 = log2Ceil(dataWidth / 8)

  val io = IO(new Bundle {
    val cmd = Flipped(Decoupled(new LmShellCmd(addrWidth, tagWidth)))
    val wdata = Flipped(Decoupled(new LmShellBeat(dataWidth)))
    val bresp = Decoupled(new LmShellResp)
    val rdata = Decoupled(new LmRData(dataWidth))
    val req = Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth))
    val rsp = Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
  })

  private val sIdle :: sSend :: sDrain :: sBwait :: Nil = Enum(4)
  private val state = RegInit(sIdle)
  private val writeMode = RegInit(false.B)
  private val base = RegInit(0.U(addrWidth.W))
  private val beats = RegInit(1.U(9.W))
  private val sent = RegInit(0.U(9.W))
  private val got = RegInit(0.U(9.W))
  private val errAcc = RegInit(false.B)
  private val burstId = RegInit(0.U(tagWidth.W))

  io.cmd.ready := state === sIdle
  io.req.valid := false.B
  io.req.bits := 0.U.asTypeOf(chiselTypeOf(io.req.bits))
  io.wdata.ready := false.B
  io.rsp.ready := false.B
  io.rdata.valid := false.B
  io.rdata.bits := 0.U.asTypeOf(chiselTypeOf(io.rdata.bits))
  io.bresp.valid := state === sBwait
  io.bresp.bits.err := errAcc

  when(state === sIdle) {
    when(io.cmd.fire) {
      writeMode := io.cmd.bits.write
      base := io.cmd.bits.addr
      beats := io.cmd.bits.beats
      sent := 0.U
      got := 0.U
      errAcc := false.B
      burstId := io.cmd.bits.id
      state := sSend
    }
  }

  when(state === sSend) {
    when(sent === beats) {
      state := sDrain
    }.otherwise {
      io.req.valid := Mux(writeMode, io.wdata.valid, true.B)
      io.req.bits.write := writeMode
      io.req.bits.addr := base + (sent << busBytesLog2)
      io.req.bits.len := 0.U
      io.req.bits.src := srcId.U
      io.req.bits.id := burstId
      io.req.bits.data := io.wdata.bits.data
      io.req.bits.strb := io.wdata.bits.strb
      io.wdata.ready := io.req.ready
      when(io.req.fire) { sent := sent + 1.U }
    }
  }

  when(state === sDrain) {
    when(writeMode) {
      io.rsp.ready := true.B
    }.otherwise {
      io.rdata.valid := io.rsp.valid
      io.rdata.bits.data := io.rsp.bits.data
      io.rdata.bits.err := io.rsp.bits.err
      io.rdata.bits.last := got === beats - 1.U
      io.rsp.ready := io.rdata.ready
    }
    when(io.rsp.fire) {
      // Same-(src,id) ordering: the fabric must return the burst's ID.
      assert(io.rsp.bits.id === burstId, "LM-SHELL: response ID does not match the burst")
      errAcc := errAcc || io.rsp.bits.err
      got := got + 1.U
      when(got === beats - 1.U) {
        state := Mux(writeMode, sBwait, sIdle)
      }
    }
  }

  when(state === sBwait && io.bresp.ready) {
    state := sIdle
  }
}

// ---------------------------------------------------------------------------
// Native four-channel shell: REQ / WDATA / RSP / RDATA.
// ---------------------------------------------------------------------------

class LmNativeReq(val addrWidth: Int) extends Bundle {
  val write = Bool()
  val addr = UInt(addrWidth.W)
  val len = UInt(8.W)
}

class LmNativeW(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val last = Bool()
}

class LmShellNative(addrWidth: Int, dataWidth: Int, idWidth: Int, srcId: Int) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new LmNativeReq(addrWidth)))
    val wdata = Flipped(Decoupled(new LmNativeW(dataWidth)))
    val rsp = Decoupled(new LmShellResp)
    val rdata = Decoupled(new LmRData(dataWidth))
    val fabReq = Decoupled(new LmReq(addrWidth, dataWidth, idWidth))
    val fabRsp = Flipped(Decoupled(new LmRsp(dataWidth, idWidth)))
  })

  private val core = Module(new LmShellCore(addrWidth, dataWidth, idWidth, srcId))
  core.io.cmd.valid := io.req.valid
  core.io.cmd.bits.write := io.req.bits.write
  core.io.cmd.bits.addr := io.req.bits.addr
  core.io.cmd.bits.beats := io.req.bits.len +& 1.U
  core.io.cmd.bits.id := 0.U
  io.req.ready := core.io.cmd.ready

  core.io.wdata.valid := io.wdata.valid
  core.io.wdata.bits.data := io.wdata.bits.data
  core.io.wdata.bits.strb := io.wdata.bits.strb
  core.io.wdata.bits.last := io.wdata.bits.last
  io.wdata.ready := core.io.wdata.ready

  io.rsp.valid := core.io.bresp.valid
  io.rsp.bits.err := core.io.bresp.bits.err
  core.io.bresp.ready := io.rsp.ready

  io.rdata.valid := core.io.rdata.valid
  io.rdata.bits := core.io.rdata.bits
  core.io.rdata.ready := io.rdata.ready

  io.fabReq <> core.io.req
  core.io.rsp <> io.fabRsp
}

// ---------------------------------------------------------------------------
// AxiLite4 shell: single beat, AW+W for writes, AR for reads.
// ---------------------------------------------------------------------------

class LmAxiLite4Aw(val addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W)
}

class LmAxiLite4W(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
}

class LmAxiLite4B extends Bundle {
  val resp = UInt(2.W)
}

class LmAxiLite4Ar(val addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W)
}

class LmAxiLite4R(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val resp = UInt(2.W)
}

class LmShellAxiLite4(addrWidth: Int, dataWidth: Int, idWidth: Int, srcId: Int) extends Module {
  val io = IO(new Bundle {
    val aw = Flipped(Decoupled(new LmAxiLite4Aw(addrWidth)))
    val w = Flipped(Decoupled(new LmAxiLite4W(dataWidth)))
    val b = Decoupled(new LmAxiLite4B)
    val ar = Flipped(Decoupled(new LmAxiLite4Ar(addrWidth)))
    val r = Decoupled(new LmAxiLite4R(dataWidth))
    val fabReq = Decoupled(new LmReq(addrWidth, dataWidth, idWidth))
    val fabRsp = Flipped(Decoupled(new LmRsp(dataWidth, idWidth)))
  })

  private val core = Module(new LmShellCore(addrWidth, dataWidth, idWidth, srcId))
  private val writeReady = io.aw.valid && io.w.valid

  core.io.cmd.valid := writeReady || io.ar.valid
  core.io.cmd.bits.write := writeReady
  core.io.cmd.bits.addr := Mux(writeReady, io.aw.bits.addr, io.ar.bits.addr)
  core.io.cmd.bits.beats := 1.U
  core.io.cmd.bits.id := 0.U
  io.aw.ready := writeReady && core.io.cmd.ready
  io.ar.ready := !writeReady && core.io.cmd.ready

  core.io.wdata.valid := io.w.valid
  core.io.wdata.bits.data := io.w.bits.data
  core.io.wdata.bits.strb := io.w.bits.strb
  core.io.wdata.bits.last := true.B
  io.w.ready := core.io.wdata.ready

  io.b.valid := core.io.bresp.valid
  io.b.bits.resp := Mux(core.io.bresp.bits.err, 2.U, 0.U)
  core.io.bresp.ready := io.b.ready

  io.r.valid := core.io.rdata.valid
  io.r.bits.data := core.io.rdata.bits.data
  io.r.bits.resp := Mux(core.io.rdata.bits.err, 2.U, 0.U)
  core.io.rdata.ready := io.r.ready

  io.fabReq <> core.io.req
  core.io.rsp <> io.fabRsp
}

// ---------------------------------------------------------------------------
// AXI4 subset: separate AW/W/B and AR/R, INCR bursts, bus-width beats.
// The transaction ID is carried end-to-end; the fabric never reorders, so
// same-(src,id) responses return in issue order.
// ---------------------------------------------------------------------------

class LmAxi4Aw(val addrWidth: Int, val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth)
    extends Bundle {
  val addr = UInt(addrWidth.W)
  val len = UInt(8.W)
  val size = UInt(3.W)
  val burst = UInt(2.W)
  val id = UInt(tagWidth.W)
}

class LmAxi4W(val dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val last = Bool()
}

class LmAxi4B(val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth) extends Bundle {
  val resp = UInt(2.W)
  val id = UInt(tagWidth.W)
}

class LmAxi4Ar(val addrWidth: Int, val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth)
    extends Bundle {
  val addr = UInt(addrWidth.W)
  val len = UInt(8.W)
  val size = UInt(3.W)
  val burst = UInt(2.W)
  val id = UInt(tagWidth.W)
}

class LmAxi4R(val dataWidth: Int, val tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth)
    extends Bundle {
  val data = UInt(dataWidth.W)
  val resp = UInt(2.W)
  val last = Bool()
  val id = UInt(tagWidth.W)
}

class LmShellAxi4(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    srcId: Int,
    tagWidth: Int = loom.ir.LoomDefaults.AxiIdWidth
) extends Module {
  private val busBytesLog2 = log2Ceil(dataWidth / 8)
  private val incr = 1.U(2.W)

  val io = IO(new Bundle {
    val aw = Flipped(Decoupled(new LmAxi4Aw(addrWidth, tagWidth)))
    val w = Flipped(Decoupled(new LmAxi4W(dataWidth)))
    val b = Decoupled(new LmAxi4B(tagWidth))
    val ar = Flipped(Decoupled(new LmAxi4Ar(addrWidth, tagWidth)))
    val r = Decoupled(new LmAxi4R(dataWidth, tagWidth))
    val fabReq = Decoupled(new LmReq(addrWidth, dataWidth, idWidth, tagWidth))
    val fabRsp = Flipped(Decoupled(new LmRsp(dataWidth, idWidth, tagWidth)))
  })

  private val core = Module(new LmShellCore(addrWidth, dataWidth, idWidth, srcId, tagWidth))
  private val writeSel = io.aw.valid

  core.io.cmd.valid := writeSel || io.ar.valid
  core.io.cmd.bits.write := writeSel
  core.io.cmd.bits.addr := Mux(writeSel, io.aw.bits.addr, io.ar.bits.addr)
  core.io.cmd.bits.beats := Mux(writeSel, io.aw.bits.len +& 1.U, io.ar.bits.len +& 1.U)
  core.io.cmd.bits.id := Mux(writeSel, io.aw.bits.id, io.ar.bits.id)
  io.aw.ready := writeSel && core.io.cmd.ready
  io.ar.ready := !writeSel && core.io.cmd.ready

  when(io.aw.fire) {
    assert(io.aw.bits.burst === incr, "LM-SHELL: only INCR bursts are supported")
    assert(io.aw.bits.size === busBytesLog2.U, "LM-SHELL: beat size must match the bus width")
  }
  when(io.ar.fire) {
    assert(io.ar.bits.burst === incr, "LM-SHELL: only INCR bursts are supported")
    assert(io.ar.bits.size === busBytesLog2.U, "LM-SHELL: beat size must match the bus width")
  }

  private val awId = RegInit(0.U(tagWidth.W))
  private val arId = RegInit(0.U(tagWidth.W))
  when(io.aw.fire) { awId := io.aw.bits.id }
  when(io.ar.fire) { arId := io.ar.bits.id }

  core.io.wdata.valid := io.w.valid
  core.io.wdata.bits.data := io.w.bits.data
  core.io.wdata.bits.strb := io.w.bits.strb
  core.io.wdata.bits.last := io.w.bits.last
  io.w.ready := core.io.wdata.ready

  io.b.valid := core.io.bresp.valid
  io.b.bits.resp := Mux(core.io.bresp.bits.err, 2.U, 0.U)
  io.b.bits.id := awId
  core.io.bresp.ready := io.b.ready

  io.r.valid := core.io.rdata.valid
  io.r.bits.data := core.io.rdata.bits.data
  io.r.bits.resp := Mux(core.io.rdata.bits.err, 2.U, 0.U)
  io.r.bits.last := core.io.rdata.bits.last
  io.r.bits.id := arId
  core.io.rdata.ready := io.r.ready

  io.fabReq <> core.io.req
  core.io.rsp <> io.fabRsp
}

// ---------------------------------------------------------------------------
// TileLink-UL subset: A channel Get/Put, D channel AccessAck(Data), 1 beat.
// ---------------------------------------------------------------------------

class LmTlA(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val opcode = UInt(3.W) // 0 PutFull, 1 PutPartial, 4 Get
  val param = UInt(3.W)
  val size = UInt(3.W)
  val source = UInt(8.W)
  val address = UInt(addrWidth.W)
  val data = UInt(dataWidth.W)
  val mask = UInt((dataWidth / 8).W)
}

class LmTlD(val dataWidth: Int) extends Bundle {
  val opcode = UInt(3.W) // 0 AccessAck, 1 AccessAckData
  val param = UInt(2.W)
  val size = UInt(3.W)
  val source = UInt(8.W)
  val data = UInt(dataWidth.W)
  val error = Bool()
}

class LmShellTlUl(addrWidth: Int, dataWidth: Int, idWidth: Int, srcId: Int) extends Module {
  private val opPutFull = 0.U(3.W)
  private val opPutPartial = 1.U(3.W)
  private val opGet = 4.U(3.W)
  private val busBytesLog2 = log2Ceil(dataWidth / 8)

  val io = IO(new Bundle {
    val a = Flipped(Decoupled(new LmTlA(addrWidth, dataWidth)))
    val d = Decoupled(new LmTlD(dataWidth))
    val fabReq = Decoupled(new LmReq(addrWidth, dataWidth, idWidth))
    val fabRsp = Flipped(Decoupled(new LmRsp(dataWidth, idWidth)))
  })

  private val core = Module(new LmShellCore(addrWidth, dataWidth, idWidth, srcId))
  private val isPut = io.a.bits.opcode === opPutFull || io.a.bits.opcode === opPutPartial
  private val isGet = io.a.bits.opcode === opGet
  private val legal = isPut || isGet

  // The A channel carries command and data in one beat, so latch the write
  // payload when the command is accepted; the core consumes it in SEND.
  private val wHold = RegInit(false.B)
  private val wData = RegInit(0.U(dataWidth.W))
  private val wMask = RegInit(0.U((dataWidth / 8).W))
  private val dSource = RegInit(0.U(8.W))
  private val dSize = RegInit(0.U(3.W))

  when(io.a.fire) {
    dSource := io.a.bits.source
    dSize := io.a.bits.size
    when(isPut) {
      wHold := true.B
      wData := io.a.bits.data
      wMask := io.a.bits.mask
    }
  }

  core.io.cmd.valid := io.a.valid && legal
  core.io.cmd.bits.write := isPut
  core.io.cmd.bits.addr := io.a.bits.address
  core.io.cmd.bits.beats := 1.U
  core.io.cmd.bits.id := 0.U
  io.a.ready := core.io.cmd.ready && legal

  when(io.a.fire) {
    assert(legal, "LM-SHELL: only TL-UL Get/Put are supported")
    assert(io.a.bits.size === busBytesLog2.U, "LM-SHELL: beat size must match the bus width")
  }

  core.io.wdata.valid := wHold
  core.io.wdata.bits.data := wData
  core.io.wdata.bits.strb := wMask
  core.io.wdata.bits.last := true.B
  when(core.io.wdata.ready) { wHold := false.B }

  private val writeDone = core.io.bresp.valid
  private val readDone = core.io.rdata.valid
  io.d.valid := writeDone || readDone
  io.d.bits.opcode := Mux(writeDone, 0.U, 1.U)
  io.d.bits.param := 0.U
  io.d.bits.size := dSize
  io.d.bits.source := dSource
  io.d.bits.data := core.io.rdata.bits.data
  io.d.bits.error := Mux(writeDone, core.io.bresp.bits.err, core.io.rdata.bits.err)
  core.io.bresp.ready := io.d.ready && writeDone
  core.io.rdata.ready := io.d.ready && readDone

  io.fabReq <> core.io.req
  core.io.rsp <> io.fabRsp
}
