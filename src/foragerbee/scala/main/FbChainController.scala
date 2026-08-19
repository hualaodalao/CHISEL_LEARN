/** ForagerBee 链式描述符控制器 —— 在 io.cmd 与 Dispatcher 之间插入，
  * 支持通过独立读总线从内存中自动拉取后续描述符，串联执行多个 DMA 命令。
  *
  * FSM：
  *   sPASS    → 透传模式（cmdIn→cmdOut / doneIn→doneOut）
  *   sWAIT_DONE → 等待当前描述符执行完成
  *   sFETCH   → 从 chainBus 读取下一描述符
  *   sINJECT  → 将描述符打包为 FbCmd 注入 cmdOut
  *
  * @param cfg ForagerBee 配置
  */

import chisel3._
import chisel3.util._

/** 描述符内存布局：定义 FbCmd 各字段在描述符 beat 序列中的 bit 偏移。
  * 字段按 FbCmd 声明顺序紧密排列，末尾追加 nextDescAddr。
  */
object FbDescLayout {
  def totalBits(cfg: ForagerBeeConfig): Int = {
    val addrW = cfg.addressWidth
    val maxDims = cfg.maxDims
    // op: 2bit (ChiselEnum COPY/TRANSPOSE/PERMUTE -> 2 bits enough for 3 values)
    val opBits = 2
    val tagBits = 8
    val dimCountBits = 3
    val shapeBits = maxDims * 16
    val srcStrideBits = maxDims * addrW
    val dstStrideBits = maxDims * addrW
    val srcAddrBits = addrW
    val dstAddrBits = addrW
    val elemBytesLog2Bits = 2
    val permVecBits = maxDims * 3
    val padBeforeBits = 2 * 16
    val padAfterBits = 2 * 16
    val nextDescAddrBits = addrW
    val chainModeBits = 1
    val cvtEnableBits = 1
    val srcFmtBits = 3
    val dstFmtBits = 3
    val cvtScaleBits = 32
    val cvtZeroPointBits = 32

    opBits + tagBits + dimCountBits + shapeBits + srcStrideBits + dstStrideBits +
      srcAddrBits + dstAddrBits + elemBytesLog2Bits + permVecBits +
      padBeforeBits + padAfterBits + nextDescAddrBits + chainModeBits +
      cvtEnableBits + srcFmtBits + dstFmtBits + cvtScaleBits + cvtZeroPointBits
  }

  def descBeats(cfg: ForagerBeeConfig): Int = {
    if (cfg.chainDescBeats > 0) cfg.chainDescBeats
    else {
      val total = totalBits(cfg)
      (total + cfg.dataWidth - 1) / cfg.dataWidth
    }
  }
}

class FbChainController(cfg: ForagerBeeConfig) extends Module {
  private val addrW = cfg.addressWidth
  private val dataW = cfg.dataWidth
  private val descBeats = FbDescLayout.descBeats(cfg)

  val io = IO(new Bundle {
    /** 来自顶层 io.cmd */
    val cmdIn = slave(Stream(new FbCmd(cfg)))
    /** 输出到 Dispatcher */
    val cmdOut = master(Stream(new FbCmd(cfg)))
    /** 拦截 engine done */
    val doneIn = slave(Stream(new FbDone(cfg)))
    /** 上报给顶层 io.done */
    val doneOut = master(Stream(new FbDone(cfg)))
    /** 独立读总线（仅用 rd/rdData） */
    val chainBus = master(new FbStreamBus(dataW, addrW))
  })

  // --- FSM 状态 ---
  val sPASS      = 0.U(3.W)
  val sWAIT_DONE = 1.U(3.W)
  val sFETCH     = 2.U(3.W)
  val sINJECT    = 3.U(3.W)

  val state = RegInit(sPASS)

  // --- 链式状态寄存器 ---
  val nextDescAddr = RegInit(0.U(addrW.W))
  val chainTag     = RegInit(0.U(8.W))
  val chainLen     = RegInit(0.U(16.W))

  // --- 描述符数据寄存器 ---
  val descRegs  = Reg(Vec(descBeats, UInt(dataW.W)))
  val fetchCnt  = RegInit(0.U(log2Up(descBeats + 1).W))
  val fetchSent = RegInit(false.B) // rd 请求已发出

  // --- Tie off chainBus wr/wrResp ---
  io.chainBus.wr.valid := false.B
  io.chainBus.wr.payload.addr := 0.U
  io.chainBus.wr.payload.len  := 0.U
  io.chainBus.wr.payload.data := 0.U
  io.chainBus.wr.payload.strb := 0.U
  io.chainBus.wr.payload.sof  := false.B
  io.chainBus.wr.payload.eof  := false.B
  io.chainBus.wrResp.ready := false.B

  // --- 默认输出 ---
  io.cmdOut.valid   := false.B
  io.cmdOut.payload := 0.U.asTypeOf(new FbCmd(cfg))
  io.cmdIn.ready    := false.B

  io.doneOut.valid   := false.B
  io.doneOut.payload := 0.U.asTypeOf(new FbDone(cfg))
  io.doneIn.ready    := false.B

  io.chainBus.rd.valid        := false.B
  io.chainBus.rd.payload.addr := 0.U
  io.chainBus.rd.payload.len  := 0.U
  io.chainBus.rdData.ready    := false.B

  // --- 描述符解析：从 descRegs 中提取 FbCmd ---
  // 将所有 beat 拼接为一个大的 bit 向量（little-endian beat 顺序）
  val descBitsWire = Wire(UInt((descBeats * dataW).W))
  descBitsWire := descRegs.asUInt

  // 从 bit 向量中按顺序提取各字段
  private def extract(offset: Int, width: Int): UInt = descBitsWire(offset + width - 1, offset)

  // 计算各字段偏移
  private val opBits = 2
  private val tagBits = 8
  private val dimCountBits = 3
  private val shapeBits = cfg.maxDims * 16
  private val srcStrideBits = cfg.maxDims * addrW
  private val dstStrideBits = cfg.maxDims * addrW
  private val srcAddrBits = addrW
  private val dstAddrBits = addrW
  private val elemBytesLog2Bits = 2
  private val permVecBits = cfg.maxDims * 3
  private val padBeforeBits = 2 * 16
  private val padAfterBits = 2 * 16
  private val nextDescAddrBits = addrW
  private val chainModeBits = 1
  private val cvtEnableBits = 1
  private val srcFmtBits = 3
  private val dstFmtBits = 3
  private val cvtScaleBits = 32
  private val cvtZeroPointBits = 32

  private var off = 0
  private val offOp = off; off += opBits
  private val offTag = off; off += tagBits
  private val offDimCount = off; off += dimCountBits
  private val offShape = off; off += shapeBits
  private val offSrcStride = off; off += srcStrideBits
  private val offDstStride = off; off += dstStrideBits
  private val offSrcAddr = off; off += srcAddrBits
  private val offDstAddr = off; off += dstAddrBits
  private val offElemBytesLog2 = off; off += elemBytesLog2Bits
  private val offPermVec = off; off += permVecBits
  private val offPadBefore = off; off += padBeforeBits
  private val offPadAfter = off; off += padAfterBits
  private val offNextDescAddr = off; off += nextDescAddrBits
  private val offChainMode = off; off += chainModeBits
  private val offCvtEnable = off; off += cvtEnableBits
  private val offSrcFmt = off; off += srcFmtBits
  private val offDstFmt = off; off += dstFmtBits
  private val offCvtScale = off; off += cvtScaleBits
  private val offCvtZeroPoint = off; off += cvtZeroPointBits

  // 组装注入 cmd
  val injCmd = Wire(new FbCmd(cfg))
  injCmd.op        := extract(offOp, opBits).asTypeOf(FbOp())
  injCmd.tag       := extract(offTag, tagBits)
  injCmd.dimCount  := extract(offDimCount, dimCountBits)
  for (d <- 0 until cfg.maxDims) {
    injCmd.shape(d)     := extract(offShape + d * 16, 16)
    injCmd.srcStride(d) := extract(offSrcStride + d * addrW, addrW)
    injCmd.dstStride(d) := extract(offDstStride + d * addrW, addrW)
    injCmd.permVec(d)   := extract(offPermVec + d * 3, 3)
  }
  injCmd.srcAddr       := extract(offSrcAddr, addrW)
  injCmd.dstAddr       := extract(offDstAddr, addrW)
  injCmd.elemBytesLog2 := extract(offElemBytesLog2, elemBytesLog2Bits)
  for (d <- 0 until 2) {
    injCmd.padBefore(d) := extract(offPadBefore + d * 16, 16)
    injCmd.padAfter(d)  := extract(offPadAfter + d * 16, 16)
  }
  injCmd.nextDescAddr  := extract(offNextDescAddr, nextDescAddrBits)
  injCmd.chainMode     := extract(offChainMode, chainModeBits).asBool
  injCmd.cvtEnable     := extract(offCvtEnable, cvtEnableBits).asBool
  injCmd.srcFmt        := extract(offSrcFmt, srcFmtBits)
  injCmd.dstFmt        := extract(offDstFmt, dstFmtBits)
  injCmd.cvtScale      := extract(offCvtScale, cvtScaleBits)
  injCmd.cvtZeroPoint  := extract(offCvtZeroPoint, cvtZeroPointBits)
  // im2col fields default to 0 (chain controller does not support im2col descriptors in v1)
  for (d <- 0 until 2) {
    injCmd.im2colKernel(d)   := 0.U
    injCmd.im2colStride(d)   := 0.U
    injCmd.im2colPad(d)      := 0.U
    injCmd.im2colDilation(d) := 0.U
  }
  for (d <- 0 until 3) {
    injCmd.im2colInShape(d) := 0.U
  }
  // scatter/gather fields default to 0 (chain controller does not support SG descriptors in v1)
  injCmd.sgListAddr   := 0.U
  injCmd.sgEntryCount := 0.U
  // srcStartIdx default to 0 (chain controller does not serialize this field in v1)
  injCmd.srcStartIdx.foreach(_ := 0.U)

  // --- FSM 逻辑 ---
  switch(state) {
    is(sPASS) {
      // 透传 cmdIn → cmdOut
      io.cmdOut.valid   := io.cmdIn.valid
      io.cmdOut.payload := io.cmdIn.payload
      io.cmdIn.ready    := io.cmdOut.ready

      // 透传 doneIn → doneOut（非链模式 chainLen=1）
      io.doneOut.valid           := io.doneIn.valid
      io.doneOut.payload         := io.doneIn.payload
      io.doneOut.payload.chainLen := 1.U
      io.doneIn.ready            := io.doneOut.ready

      // 当 cmdOut.fire 且 chainMode=true 时进入链模式
      when(io.cmdOut.fire && io.cmdOut.payload.chainMode) {
        nextDescAddr := io.cmdOut.payload.nextDescAddr
        chainTag     := io.cmdOut.payload.tag
        chainLen     := 1.U
        state        := sWAIT_DONE
      }
    }

    is(sWAIT_DONE) {
      // 等待 doneIn
      io.doneIn.ready := true.B
      // 不透传 cmdIn（阻塞）
      io.cmdIn.ready := false.B

      when(io.doneIn.fire) {
        chainLen := chainLen + 1.U
        when(nextDescAddr === 0.U || io.doneIn.payload.err) {
          // 链结束或出错：构造最终 done
          io.doneOut.valid            := true.B
          io.doneOut.payload.tag      := chainTag
          io.doneOut.payload.err      := io.doneIn.payload.err
          io.doneOut.payload.chainLen := chainLen // 包含当前这条也已+1
          state := sPASS
        }.otherwise {
          // 继续取下一描述符
          fetchCnt  := 0.U
          fetchSent := false.B
          state     := sFETCH
        }
      }
    }

    is(sFETCH) {
      // 发读请求
      when(!fetchSent) {
        io.chainBus.rd.valid        := true.B
        io.chainBus.rd.payload.addr := nextDescAddr
        io.chainBus.rd.payload.len  := (descBeats - 1).U
        when(io.chainBus.rd.fire) {
          fetchSent := true.B
        }
      }

      // 接收读数据
      io.chainBus.rdData.ready := true.B
      when(io.chainBus.rdData.fire) {
        descRegs(fetchCnt) := io.chainBus.rdData.payload.data
        fetchCnt := fetchCnt + 1.U
        when(fetchCnt === (descBeats - 1).U) {
          state := sINJECT
        }
      }
    }

    is(sINJECT) {
      // 将解析出的 cmd 推送给 Dispatcher
      io.cmdOut.valid   := true.B
      io.cmdOut.payload := injCmd

      when(io.cmdOut.fire) {
        // 锁存新的 nextDescAddr
        nextDescAddr := injCmd.nextDescAddr
        state        := sWAIT_DONE
      }
    }
  }
}
