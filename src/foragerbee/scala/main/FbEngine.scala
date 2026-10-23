import chisel3._
import chisel3.util._

class FbEngine(cfg: ForagerBeeConfig, supportsTranspose: Boolean = true, supportsPermute: Boolean = true,
    supportsIm2col: Boolean = true, supportsScatterGather: Boolean = true) extends Module {
  private val BB = cfg.beatBytes
  private val addrW = cfg.addressWidth
  private val log2BB = log2Ceil(BB)
  private val T = cfg.tileSize
  private val limit = (BigInt(1) << addrW).U
  private val sgEnabled = supportsScatterGather && cfg.dataWidth >= addrW + 16

  val io = IO(new Bundle {
    val cmdIn = slave(Stream(new FbCmd(cfg)))
    val done = master(Stream(new FbDone(cfg)))
    val bus = master(new FbStreamBus(cfg.dataWidth, addrW))
    val busy = Output(Bool())
  })

  private val sIDLE = 0.U(4.W)
  private val sCOPY = 1.U(4.W)
  private val sSTART = 2.U(4.W)
  private val sMOVE = 3.U(4.W)
  private val sSCALE = 4.U(4.W)
  private val sTRD = 5.U(4.W)
  private val sTWR = 6.U(4.W)
  private val sIINIT = 7.U(4.W)
  private val sI2C = 8.U(4.W)
  private val sSGINIT = 9.U(4.W)
  private val sSG = 10.U(4.W)
  private val sDONE = 11.U(4.W)
  val state = RegInit(sIDLE)
  val cmdReg = Reg(new FbCmd(cfg))
  val errReg = RegInit(false.B)
  val reader = Module(new FbByteReader(cfg))
  val writer = Module(new FbByteWriter(cfg))
  val converter = if (cfg.enableConversion) Some(Module(new FbFormatConverter(cfg.dataWidth, cfg.converterPipeStages))) else None
  val quantizer = if (cfg.enableConversion) Some(Module(new FbMxQuantizer(cfg.dataWidth))) else None
  val transp = if (supportsTranspose) Some(withReset(reset.asBool || state === sDONE) {
    Module(new FbTransposeBuffer(cfg))
  }) else None
  val im2col = if (supportsIm2col) Some(Module(new FbIm2colAddrGen(addrW))) else None
  val sg = if (sgEnabled) Some(Module(new FbScatterGatherCtrl(addrW, cfg.dataWidth))) else None

  val moveSrc = Reg(UInt(addrW.W))
  val moveDst = Reg(UInt(addrW.W))
  val moveSrcBytes = Reg(UInt(24.W))
  val moveDstBytes = Reg(UInt(24.W))
  val moveMode = Reg(UInt(2.W))
  val moveKind = Reg(UInt(2.W))
  val fillRemaining = RegInit(0.U(24.W))
  val mxRemaining = RegInit(0.U(24.W))
  val outerIdx = RegInit(VecInit(Seq.fill(cfg.maxDims)(0.U(16.W))))
  val rowCursor = RegInit(0.U(18.W))
  val copyPhase = RegInit(0.U(2.W))
  val blockIdx = RegInit(0.U(16.W))
  val scaleAccum = RegInit(VecInit(Seq.fill(BB)(0.U(8.W))))
  val scaleCount = RegInit(0.U(log2Ceil(BB + 1).W))
  val scaleWritten = RegInit(0.U(16.W))
  val scaleRowEnd = RegInit(false.B)
  val kw = RegInit(0.U(8.W))
  val i2cWhole = RegInit(false.B)
  val sgOffset = RegInit(0.U((addrW + 1).W))
  val sgLength = Reg(UInt(16.W))

  val cb = RegInit(0.U(16.W))
  val rb = RegInit(0.U(16.W))
  val fillRow = RegInit(0.U(16.W))
  val drainCol = RegInit(0.U(16.W))
  val trReading = RegInit(false.B)
  val twSent = RegInit(0.U(9.W))
  val twActive = RegInit(false.B)
  val twLength = RegInit(0.U(9.W))
  val twRemain = RegInit(0.U(9.W))
  val twFirst = RegInit(false.B)
  val twWaiting = RegInit(false.B)
  val trOutstanding = RegInit(0.U(log2Ceil(cfg.writeOutstandingDepth + 1).W))

  private def sumOffsets(indices: Vec[UInt], strides: Vec[UInt]): UInt = {
    val terms = (1 until cfg.maxDims).map(d => Mux(d.U < cmdReg.dimCount, indices(d) * strides(d), 0.U))
    if (terms.isEmpty) 0.U else terms.reduce(_ +& _)
  }
  private def startMove(src: UInt, dst: UInt, sourceBytes: UInt, destinationBytes: UInt, mode: UInt, kind: UInt): Unit = {
    val sourceEnd = src +& sourceBytes
    val physicalEnd = ((sourceEnd +& (BB - 1).U) >> log2BB) << log2BB
    val invalid = dst +& destinationBytes > limit ||
      ((mode === 0.U || mode === 2.U) && (src >= limit || physicalEnd > limit))
    when(invalid) {
      errReg := true.B
      state := sDONE
    }.otherwise {
      moveSrc := src
      moveDst := dst
      moveSrcBytes := sourceBytes
      moveDstBytes := destinationBytes
      moveMode := mode
      moveKind := kind
      state := sSTART
    }
  }

  val padAny = cmdReg.padBefore.asUInt.orR || cmdReg.padAfter.asUInt.orR
  val sourceRows = Mux(cmdReg.dimCount > 1.U, cmdReg.shape(1), 1.U)
  val totalRows = sourceRows +& cmdReg.padBefore(1) +& cmdReg.padAfter(1)
  val padRow = rowCursor < cmdReg.padBefore(1) || rowCursor >= sourceRows +& cmdReg.padBefore(1)
  val srcIndices = Wire(Vec(cfg.maxDims, UInt(18.W)))
  val dstIndices = Wire(Vec(cfg.maxDims, UInt(18.W)))
  val srcStrides = Wire(Vec(cfg.maxDims, UInt(addrW.W)))
  for (d <- 0 until cfg.maxDims) {
    val index = if (d == 1) (rowCursor - cmdReg.padBefore(1)) else outerIdx(d)
    srcIndices(d) := index +& cmdReg.srcStartIdx(d)
    dstIndices(d) := (if (d == 1) rowCursor else outerIdx(d))
    srcStrides(d) := Mux(cmdReg.op === FbOp.PERMUTE, cmdReg.srcStride(cmdReg.permVec(d)), cmdReg.srcStride(d))
  }
  val srcRow = cmdReg.srcAddr +& sumOffsets(srcIndices, srcStrides) +& FbDataFmt.rowBytes(cmdReg.srcStartIdx(0), cmdReg.srcFmt)
  val dstRow = cmdReg.dstAddr +& sumOffsets(dstIndices, cmdReg.dstStride)
  val srcRowBytes = FbDataFmt.rowBytes(cmdReg.shape(0), cmdReg.srcFmt)
  val dstRowBytes = FbDataFmt.rowBytes(cmdReg.shape(0), cmdReg.dstFmt)
  val dstElemBytes = FbDataFmt.bytesOfCeil(cmdReg.dstFmt)
  val beforeBytes = cmdReg.padBefore(0) * dstElemBytes
  val afterBytes = cmdReg.padAfter(0) * dstElemBytes
  val fullRowBytes = beforeBytes +& dstRowBytes +& afterBytes
  val mxActive = if (cfg.enableConversion) FbDataFmt.isMx(cmdReg.dstFmt) else false.B
  val blockSize = MuxLookup(cmdReg.scaleBlockSize, 16.U(7.W))(Seq(0.U -> 16.U, 1.U -> 32.U, 2.U -> 64.U))
  val blockSourceBytes = FbDataFmt.rowBytes(blockSize, cmdReg.srcFmt)
  val scaleRow = cmdReg.scaleAddr +& sumOffsets(dstIndices, cmdReg.dstStride)

  private def advanceRow(): Unit = {
    copyPhase := 0.U
    blockIdx := 0.U
    scaleWritten := 0.U
    when(rowCursor + 1.U < totalRows) {
      rowCursor := rowCursor + 1.U
      state := sCOPY
    }.otherwise {
      rowCursor := 0.U
      val carry = Wire(Vec(math.max(3, cfg.maxDims + 1), Bool()))
      carry.foreach(_ := false.B)
      carry(2) := true.B
      for (d <- 2 until cfg.maxDims) {
        val active = d.U < cmdReg.dimCount
        val wrap = outerIdx(d) + 1.U === cmdReg.shape(d)
        carry(d + 1) := carry(d) && active && wrap
        when(active && carry(d)) { outerIdx(d) := Mux(wrap, 0.U, outerIdx(d) + 1.U) }
      }
      when(cmdReg.dimCount <= 2.U || carry(cmdReg.dimCount)) { state := sDONE }
        .otherwise { state := sCOPY }
    }
  }

  io.busy := state =/= sIDLE
  io.cmdIn.ready := state === sIDLE
  io.done.valid := state === sDONE && trOutstanding === 0.U && !reader.io.busy && !writer.io.busy
  io.done.payload.tag := cmdReg.tag
  io.done.payload.err := errReg
  reader.io.start := false.B
  reader.io.srcAddr := moveSrc
  reader.io.byteCount := moveSrcBytes
  reader.io.out.ready := false.B
  writer.io.start := false.B
  writer.io.dstAddr := moveDst
  writer.io.byteCount := moveDstBytes
  writer.io.in.valid := false.B
  writer.io.in.bits := 0.U.asTypeOf(writer.io.in.bits)
  io.bus.rd.valid := reader.io.bus.rd.valid
  io.bus.rd.payload := reader.io.bus.rd.payload
  reader.io.bus.rd.ready := io.bus.rd.ready
  reader.io.bus.rdData.valid := io.bus.rdData.valid
  reader.io.bus.rdData.payload := io.bus.rdData.payload
  io.bus.rdData.ready := reader.io.bus.rdData.ready
  io.bus.wr.valid := writer.io.bus.wr.valid
  io.bus.wr.payload := writer.io.bus.wr.payload
  writer.io.bus.wr.ready := io.bus.wr.ready
  writer.io.bus.wrResp.valid := io.bus.wrResp.valid
  writer.io.bus.wrResp.payload := io.bus.wrResp.payload
  io.bus.wrResp.ready := writer.io.bus.wrResp.ready
  reader.io.bus.wr.ready := false.B
  reader.io.bus.wrResp.valid := false.B
  reader.io.bus.wrResp.payload := 0.U.asTypeOf(reader.io.bus.wrResp.payload)
  writer.io.bus.rd.ready := false.B
  writer.io.bus.rdData.valid := false.B
  writer.io.bus.rdData.payload := 0.U.asTypeOf(writer.io.bus.rdData.payload)

  converter.foreach { c =>
    c.io.in.valid := false.B
    c.io.in.bits := reader.io.out.bits.data
    c.io.inBytes := reader.io.out.bits.bytes
    c.io.inLast := reader.io.out.bits.last
    c.io.out.ready := false.B
    c.io.cfg_srcFmt := cmdReg.srcFmt
    c.io.cfg_dstFmt := cmdReg.dstFmt
    c.io.cfg_enable := cmdReg.srcFmt =/= cmdReg.dstFmt
    c.io.flush := state === sSTART || state === sDONE
  }
  quantizer.foreach { q =>
    q.io.in.valid := false.B
    q.io.in.bits := reader.io.out.bits.data
    q.io.outData.ready := false.B
    q.io.outScale.ready := false.B
    q.io.cfg_srcFmt := cmdReg.srcFmt
    q.io.cfg_dstFmt := cmdReg.dstFmt
    q.io.cfg_blockSize := blockSize
    q.io.flush := state === sDONE || (state === sSTART && moveMode === 2.U)
  }
  transp.foreach { t =>
    t.io.start := false.B
    t.io.tileRows := Mux(cmdReg.shape(1) - rb * T.U >= T.U, T.U, cmdReg.shape(1) - rb * T.U)
    t.io.tileCols := Mux(cmdReg.shape(0) - cb * T.U >= T.U, T.U, cmdReg.shape(0) - cb * T.U)
    t.io.colStart := cb * T.U
    t.io.beatsPerRow := (srcRowBytes +& (BB - 1).U) >> log2BB
    t.io.elemBytesLog2 := FbDataFmt.ebLog2Of(cmdReg.srcFmt)
    t.io.outByteOffset := 0.U
    t.io.inBeat.valid := false.B
    t.io.inBeat.payload := reader.io.out.bits.data
    t.io.outBeat.ready := false.B
  }
  im2col.foreach { g =>
    g.io.start := state === sIINIT
    g.io.srcBase := cmdReg.srcAddr
    g.io.dstBase := cmdReg.dstAddr
    g.io.srcElemBytesLog2 := FbDataFmt.ebLog2Of(cmdReg.srcFmt)
    g.io.dstElemBytesLog2 := FbDataFmt.ebLog2Of(cmdReg.dstFmt)
    g.io.kernel := cmdReg.im2colKernel
    g.io.stride := cmdReg.im2colStride
    g.io.pad := cmdReg.im2colPad
    g.io.dilation := cmdReg.im2colDilation
    g.io.inShape := cmdReg.im2colInShape
    g.io.kw := kw
    g.io.advance := false.B
  }
  sg.foreach { g =>
    g.io.start := state === sSGINIT
    g.io.listAddr := cmdReg.sgListAddr
    g.io.entryCount := cmdReg.sgEntryCount
    g.io.advance := false.B
    g.io.fetchRd.ready := false.B
    g.io.fetchRdData.valid := false.B
    g.io.fetchRdData.payload := io.bus.rdData.payload
  }

  val candidate = io.cmdIn.payload
  val cPad = candidate.padBefore.asUInt.orR || candidate.padAfter.asUInt.orR
  val cMx = cfg.enableConversion.B && FbDataFmt.isMx(candidate.dstFmt)
  val cBlock = MuxLookup(candidate.scaleBlockSize, 16.U(7.W))(Seq(0.U -> 16.U, 1.U -> 32.U, 2.U -> 64.U))
  val shapeBad = VecInit((0 until cfg.maxDims).map(d => d.U < candidate.dimCount && candidate.shape(d) === 0.U)).asUInt.orR
  val permBad = candidate.op === FbOp.PERMUTE && VecInit((0 until cfg.maxDims).map(d =>
    d.U < candidate.dimCount && candidate.permVec(d) >= candidate.dimCount)).asUInt.orR
  val int4 = FbDataFmt.isSubByte(candidate.srcFmt) || FbDataFmt.isSubByte(candidate.dstFmt)
  val differing = candidate.srcFmt =/= candidate.dstFmt
  val formatBad = !FbDataFmt.isLegal(candidate.srcFmt) || !FbDataFmt.isLegal(candidate.dstFmt) ||
    (differing && (!cfg.enableConversion.B || !FbDataFmt.isFloat(candidate.srcFmt) ||
      (!FbDataFmt.isFloat(candidate.dstFmt) && !cMx))) ||
    (cfg.enableConversion.B && FbDataFmt.isMx(candidate.srcFmt)) ||
    (differing && candidate.op =/= FbOp.COPY && candidate.op =/= FbOp.IM2COL) ||
    (int4 && (candidate.op =/= FbOp.COPY || candidate.shape(0)(0) || candidate.srcStartIdx(0)(0) || cPad))
  val mxBad = cMx && (candidate.op =/= FbOp.COPY || !FbDataFmt.isFloat(candidate.srcFmt) ||
    candidate.scaleBlockSize > 2.U || candidate.shape(0) % cBlock =/= 0.U || cPad ||
    candidate.scaleAddr(log2BB - 1, 0) =/= 0.U)
  val hEffective = candidate.im2colDilation(0) * Mux(candidate.im2colKernel(0) === 0.U, 0.U, candidate.im2colKernel(0) - 1.U) +& 1.U
  val wEffective = candidate.im2colDilation(1) * Mux(candidate.im2colKernel(1) === 0.U, 0.U, candidate.im2colKernel(1) - 1.U) +& 1.U
  val hPadded = candidate.im2colInShape(1) +& (candidate.im2colPad(0) << 1)
  val wPadded = candidate.im2colInShape(2) +& (candidate.im2colPad(1) << 1)
  val outH = Mux(hPadded >= hEffective, hPadded - hEffective, 0.U) / Mux(candidate.im2colStride(0) === 0.U, 1.U, candidate.im2colStride(0)) +& 1.U
  val outW = Mux(wPadded >= wEffective, wPadded - wEffective, 0.U) / Mux(candidate.im2colStride(1) === 0.U, 1.U, candidate.im2colStride(1)) +& 1.U
  val i2cSourceSpan = candidate.im2colInShape(0) * candidate.im2colInShape(1) * candidate.im2colInShape(2) * FbDataFmt.bytesOfCeil(candidate.srcFmt)
  val i2cDestSpan = outH * outW * candidate.im2colInShape(0) * candidate.im2colKernel(0) * candidate.im2colKernel(1) * FbDataFmt.bytesOfCeil(candidate.dstFmt)
  val i2cBad = candidate.op === FbOp.IM2COL && (!supportsIm2col.B ||
    candidate.im2colKernel(0) === 0.U || candidate.im2colKernel(1) === 0.U ||
    candidate.im2colStride(0) === 0.U || candidate.im2colStride(1) === 0.U ||
    candidate.im2colDilation(0) === 0.U || candidate.im2colDilation(1) === 0.U ||
    candidate.im2colInShape(0) === 0.U || candidate.im2colInShape(1) === 0.U || candidate.im2colInShape(2) === 0.U ||
    hPadded < hEffective || wPadded < wEffective || outH > 65535.U || outW > 65535.U ||
    candidate.srcAddr +& i2cSourceSpan > limit || candidate.dstAddr +& i2cDestSpan > limit)
  val sgBad = (candidate.op === FbOp.SCATTER || candidate.op === FbOp.GATHER) &&
    (!sgEnabled.B || candidate.sgEntryCount === 0.U || candidate.sgListAddr(log2BB - 1, 0) =/= 0.U ||
      candidate.sgListAddr +& candidate.sgEntryCount * BB.U > limit)
  val bad = candidate.dimCount === 0.U || candidate.dimCount > cfg.maxDims.U || shapeBad || permBad || formatBad || mxBad || i2cBad || sgBad ||
    (candidate.op === FbOp.TRANSPOSE && (!supportsTranspose.B || candidate.dimCount =/= 2.U)) ||
    (candidate.op === FbOp.PERMUTE && !supportsPermute.B) || (cPad && candidate.op =/= FbOp.COPY) ||
    (candidate.dimCount === 1.U && (candidate.padBefore(1) =/= 0.U || candidate.padAfter(1) =/= 0.U))

  when(io.cmdIn.fire) {
    cmdReg := candidate
    errReg := bad
    outerIdx.foreach(_ := 0.U)
    rowCursor := 0.U
    copyPhase := 0.U
    blockIdx := 0.U
    scaleCount := 0.U
    scaleWritten := 0.U
    sgOffset := 0.U
    kw := 0.U
    cb := 0.U
    rb := 0.U
    fillRow := 0.U
    drainCol := 0.U
    trReading := false.B
    twSent := 0.U
    twActive := false.B
    twWaiting := false.B
    when(bad) { state := sDONE }
      .elsewhen(candidate.op === FbOp.TRANSPOSE) { state := sTRD }
      .elsewhen(candidate.op === FbOp.IM2COL) { state := sIINIT }
      .elsewhen(candidate.op === FbOp.SCATTER || candidate.op === FbOp.GATHER) { state := sSGINIT }
      .otherwise { state := sCOPY }
  }

  when(state === sCOPY) {
    when(padAny && padRow) {
      startMove(0.U, dstRow, 0.U, fullRowBytes, 1.U, 0.U)
    }.elsewhen(copyPhase === 0.U) {
      when(beforeBytes === 0.U) { copyPhase := 1.U }
        .otherwise { startMove(0.U, dstRow, 0.U, beforeBytes, 1.U, 0.U) }
    }.elsewhen(copyPhase === 1.U) {
      when(mxActive) {
        startMove(srcRow +& blockIdx * blockSourceBytes, dstRow +& blockIdx * blockSize,
          blockSourceBytes, blockSize, 2.U, 0.U)
      }.otherwise {
        startMove(srcRow, dstRow +& beforeBytes, srcRowBytes, dstRowBytes, 0.U, 0.U)
      }
    }.otherwise {
      when(afterBytes === 0.U) { advanceRow() }
        .otherwise { startMove(0.U, dstRow +& beforeBytes +& dstRowBytes, 0.U, afterBytes, 1.U, 0.U) }
    }
  }
  when(state === sSCALE) {
    startMove(0.U, scaleRow +& scaleWritten, 0.U, scaleCount, 3.U, 3.U)
  }
  when(state === sSTART) {
    reader.io.start := moveMode === 0.U || moveMode === 2.U
    writer.io.start := true.B
    fillRemaining := moveDstBytes
    mxRemaining := moveDstBytes
    state := sMOVE
  }

  val padWord = MuxLookup(dstElemBytes, Fill(BB, cmdReg.padValue(7, 0)))(Seq(
    2.U -> Fill(BB / 2, cmdReg.padValue(15, 0)),
    4.U -> Fill(BB / 4, cmdReg.padValue)))
  when(state === sMOVE) {
    when(moveMode === 1.U || moveMode === 3.U) {
      writer.io.in.valid := fillRemaining =/= 0.U
      writer.io.in.bits.data := Mux(moveMode === 3.U, scaleAccum.asUInt, padWord)
      writer.io.in.bits.bytes := fillRemaining.min(BB.U)
      writer.io.in.bits.last := fillRemaining <= BB.U
      when(writer.io.in.fire) { fillRemaining := fillRemaining - writer.io.in.bits.bytes }
    }.elsewhen(moveMode === 2.U) {
      quantizer.foreach { q =>
        q.io.in.valid := reader.io.out.valid
        reader.io.out.ready := q.io.in.ready
        writer.io.in.valid := q.io.outData.valid
        writer.io.in.bits.data := q.io.outData.bits
        writer.io.in.bits.bytes := mxRemaining.min(BB.U)
        writer.io.in.bits.last := mxRemaining <= BB.U
        q.io.outData.ready := writer.io.in.ready
        when(writer.io.in.fire) { mxRemaining := mxRemaining - writer.io.in.bits.bytes }
        q.io.outScale.ready := true.B
        when(q.io.outScale.fire) {
          scaleAccum(scaleCount(log2BB - 1, 0)) := q.io.outScale.bits
          scaleCount := scaleCount + 1.U
        }
      }
    }.otherwise {
      if (cfg.enableConversion) {
        val c = converter.get
        c.io.in.valid := reader.io.out.valid
        reader.io.out.ready := c.io.in.ready
        writer.io.in.valid := c.io.out.valid
        writer.io.in.bits.data := c.io.out.bits
        writer.io.in.bits.bytes := c.io.outBytes
        writer.io.in.bits.last := c.io.outLast
        c.io.out.ready := writer.io.in.ready
      } else {
        writer.io.in <> reader.io.out
      }
    }
    when(writer.io.done && (moveMode === 1.U || moveMode === 3.U || reader.io.done)) {
      errReg := errReg || writer.io.err || ((moveMode === 0.U || moveMode === 2.U) && reader.io.err)
      when(moveKind === 0.U) {
        when(moveMode === 2.U) {
          val rowEnd = (blockIdx +& 1.U) * blockSize === cmdReg.shape(0)
          blockIdx := blockIdx + 1.U
          when(scaleCount === BB.U || rowEnd) {
            scaleRowEnd := rowEnd
            state := sSCALE
          }.otherwise { state := sCOPY }
        }.elsewhen(padAny && padRow) { advanceRow() }
          .elsewhen(copyPhase === 2.U) { advanceRow() }
          .otherwise { copyPhase := copyPhase + 1.U; state := sCOPY }
      }.elsewhen(moveKind === 1.U) {
        im2col.foreach { g =>
          when(i2cWhole || kw +& 1.U === cmdReg.im2colKernel(1)) {
            kw := 0.U
            g.io.advance := true.B
          }.otherwise { kw := kw + 1.U }
        }
        state := sI2C
      }.elsewhen(moveKind === 2.U) {
        sgOffset := sgOffset + sgLength
        sg.foreach(_.io.advance := true.B)
        state := sSG
      }.otherwise {
        scaleWritten := scaleWritten + scaleCount
        scaleCount := 0.U
        when(scaleRowEnd) { advanceRow() }.otherwise { state := sCOPY }
      }
    }
  }

  when(state === sIINIT) { state := sI2C }
  im2col.foreach { g =>
    when(state === sI2C) {
      when(g.io.done) { state := sDONE }
        .elsewhen(g.io.valid) {
          val whole = g.io.isPad || g.io.burstable
          val elements = Mux(whole, g.io.microRowElems, 1.U)
          val fill = g.io.isPad || (!whole && g.io.elemIsPad)
          i2cWhole := whole
          startMove(Mux(whole, g.io.srcAddr, g.io.elemSrcAddr),
            g.io.dstAddr +& Mux(whole, 0.U, kw * dstElemBytes),
            elements * FbDataFmt.bytesOfCeil(cmdReg.srcFmt), elements * dstElemBytes,
            Mux(fill, 1.U, 0.U), 1.U)
        }
    }
  }

  when(state === sSGINIT) { state := sSG }
  sg.foreach { g =>
    when(state === sSG) {
      io.bus.rd.valid := g.io.fetchRd.valid
      io.bus.rd.payload := g.io.fetchRd.payload
      g.io.fetchRd.ready := io.bus.rd.ready
      reader.io.bus.rd.ready := false.B
      reader.io.bus.rdData.valid := false.B
      g.io.fetchRdData.valid := io.bus.rdData.valid
      io.bus.rdData.ready := g.io.fetchRdData.ready
      when(io.bus.rdData.fire && io.bus.rdData.payload.err) { errReg := true.B }
      when(g.io.done) { state := sDONE }
        .elsewhen(g.io.valid) {
          when(g.io.curLen === 0.U) { errReg := true.B; state := sDONE }
            .otherwise {
              val gather = cmdReg.op === FbOp.GATHER
              sgLength := g.io.curLen
              startMove(Mux(gather, g.io.curAddr.pad(addrW + 1), cmdReg.srcAddr +& sgOffset),
                Mux(gather, cmdReg.dstAddr +& sgOffset, g.io.curAddr.pad(addrW + 1)),
                g.io.curLen, g.io.curLen, 0.U, 2.U)
            }
        }
    }
  }

  val transposeCommand = cmdReg.op === FbOp.TRANSPOSE && state =/= sIDLE
  val trEof = transposeCommand && io.bus.wr.fire && io.bus.wr.payload.eof
  val trResp = transposeCommand && io.bus.wrResp.fire
  when(transposeCommand) {
    writer.io.bus.wrResp.valid := false.B
    io.bus.wrResp.ready := trOutstanding =/= 0.U || trEof
    when(trEof =/= trResp) { trOutstanding := Mux(trEof, trOutstanding + 1.U, trOutstanding - 1.U) }
  }
  transp.foreach { t =>
    val tileRows = t.io.tileRows
    val tileCols = t.io.tileCols
    val sourceRow = cmdReg.srcAddr +& ((rb * T.U +& fillRow +& cmdReg.srcStartIdx(1)) * cmdReg.srcStride(1)) +&
      FbDataFmt.rowBytes(cmdReg.srcStartIdx(0), cmdReg.srcFmt)
    when(state === sTRD) {
      when(!t.io.busy) { t.io.start := true.B }
      when(t.io.busy && !trReading) {
        val physicalEnd = ((sourceRow +& srcRowBytes +& (BB - 1).U) >> log2BB) << log2BB
        when(physicalEnd > limit) { errReg := true.B; state := sDONE }
          .otherwise {
            reader.io.srcAddr := sourceRow
            reader.io.byteCount := srcRowBytes
            reader.io.start := true.B
            trReading := true.B
          }
      }
      t.io.inBeat.valid := reader.io.out.valid
      reader.io.out.ready := t.io.inBeat.ready
      when(trReading && reader.io.done) {
        errReg := errReg || reader.io.err
        trReading := false.B
        when(fillRow + 1.U === tileRows) {
          state := sTWR
          drainCol := 0.U
          twSent := 0.U
          twActive := false.B
        }.otherwise { fillRow := fillRow + 1.U }
      }
    }
    val rawDst = cmdReg.dstAddr +& ((cb * T.U +& drainCol) * cmdReg.dstStride(0)) +&
      (rb * T.U * FbDataFmt.bytesOfCeil(cmdReg.srcFmt))
    t.io.outByteOffset := rawDst(log2BB - 1, 0)
    val columnFinished = WireDefault(false.B)
    when(state === sTWR) {
      val remaining = t.io.beatsPerCol - twSent
      val burst = remaining.min(cfg.maxBurstLen.U)
      when(!twActive && !twWaiting && remaining =/= 0.U && trOutstanding < cfg.writeOutstandingDepth.U) {
        when(rawDst +& tileRows * FbDataFmt.bytesOfCeil(cmdReg.srcFmt) > limit) { errReg := true.B; state := sDONE }
          .otherwise {
            twActive := true.B
            twFirst := true.B
            twLength := burst
            twRemain := burst
          }
      }
      io.bus.wr.valid := twActive && t.io.outBeat.valid
      io.bus.wr.payload.addr := (rawDst & ~((BB - 1).U(rawDst.getWidth.W))) + (twSent << log2BB)
      io.bus.wr.payload.len := twLength - 1.U
      io.bus.wr.payload.data := t.io.outBeat.payload
      io.bus.wr.payload.strb := t.io.outStrb
      io.bus.wr.payload.sof := twFirst
      io.bus.wr.payload.eof := twRemain === 1.U
      writer.io.bus.wr.ready := false.B
      t.io.outBeat.ready := io.bus.wr.ready && twActive
      when(io.bus.wr.fire) {
        twSent := twSent + 1.U
        twRemain := twRemain - 1.U
        twFirst := false.B
        when(io.bus.wr.payload.eof) {
          twActive := false.B
          if (cfg.writeOutstandingDepth == 1) { twWaiting := true.B }
          else { when(twSent + 1.U === t.io.beatsPerCol) { columnFinished := true.B } }
        }
      }
      if (cfg.writeOutstandingDepth == 1) {
        when(trResp && (twWaiting || trEof)) {
          twWaiting := false.B
          when(twSent + trEof.asUInt === t.io.beatsPerCol) { columnFinished := true.B }
        }
      }
      when(columnFinished) {
        twSent := 0.U
        when(drainCol + 1.U === tileCols) {
          drainCol := 0.U
          fillRow := 0.U
          when((rb + 1.U) * T.U >= cmdReg.shape(1)) {
            rb := 0.U
            when((cb + 1.U) * T.U >= cmdReg.shape(0)) { state := sDONE }
              .otherwise { cb := cb + 1.U; state := sTRD }
          }.otherwise { rb := rb + 1.U; state := sTRD }
        }.otherwise { drainCol := drainCol + 1.U }
      }
    }
  }
  when(trResp && io.bus.wrResp.payload.err) { errReg := true.B }
  when(io.done.fire) { state := sIDLE }
}
