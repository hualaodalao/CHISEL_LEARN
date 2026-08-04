/** ForagerBee 执行引擎 —— 每个 DMA 通道（FbStreamBus master）一个。
  *
  * 数据通路：
  *   cmdIn（FbCmd）→ FSM 地址生成器 → FbStreamBus 读（rd/rdData）
  *     → COPY：数据 Queue 旁路缓冲 → [FbFormatConverter] → FbStreamBus 写（wr/wrResp）
  *     → TRANSPOSE（supportsTranspose=true）：FbTransposeBuffer → FbStreamBus 写
  *     → PERMUTE：同 COPY 路径，但源 stride 按 permVec 重映射
  *     → Zero-Padding（COPY + padBefore/padAfter）：dim1 全零行 / dim0 行首尾零段直写 sZWRITE
  *   全部完成后 done 回执（tag/err）。
  *
  * COPY FSM：
  *   IDLE → 每行：READ（rd 突发 + rdData 收数入 Queue）→ WRITE（wr 排空 Queue + 等 wrResp）
  *   → beatInRow/外层维度计数器进位推进 → 全部完成发 done → IDLE。
  *   最内维（dim0）展开为连续 INCR 突发，每段 beat 数 =
  *   min(行内剩余 beat, maxBurstLen, dataQueue 信用)。
  *   当前行地址 = base + Σ(idx(i) × stride(i))（i ∈ [1, dimCount)）。
  *
  * PERMUTE：与 COPY 共用 sREAD/sWRITE 路径；源行地址生成时使用 permVec 重映射 stride 索引。
  *
  * Zero-Padding（sZWRITE）：
  *   padActive 时，对输出 virtualRow 逐行处理：
  *   - dim1 padding 行 → sZWRITE 全零突发
  *   - data 行 → dim0 padBefore(sZWRITE) → sREAD/sWRITE 数据段 → dim0 padAfter(sZWRITE)
  *
  * TRANSPOSE FSM（supportsTranspose=true 时，限定 dimCount=2，M=shape(0) 列元素数，N=shape(1) 行数）：
  *   按 tile 分块双层循环：列带 cb ∈ ceil(M/T)、行带 rb ∈ ceil(N/T)。
  *   每个 tile：TRD（逐行发 rd 读源行，beat 流转置缓冲 FILL）
  *            → TWR（逐列发 wr，转置缓冲 DRAIN 的 beat 写 wr，等 wrResp）。
  *
  * @param cfg              ForagerBee 配置
  * @param supportsTranspose 是否支持转置操作（false 时不实例化转置缓冲，收到 TRANSPOSE 命令回 err）
  */

import chisel3._
import chisel3.util._

class FbEngine(cfg: ForagerBeeConfig, supportsTranspose: Boolean = true, supportsPermute: Boolean = true) extends Module {
  private val BB = cfg.beatBytes
  private val log2BB = log2Up(BB)
  private val addrW = cfg.addressWidth
  private val wideW = addrW + 20 // 地址求和中间位宽（idx×stride 累加防溢出）
  private val T = cfg.tileSize

  val io = IO(new Bundle {

    /** 命令输入 */
    val cmdIn = slave(Stream(new FbCmd(cfg)))

    /** 完成回执输出 */
    val done = master(Stream(new FbDone(cfg)))

    /** DMA 读写口（FbStreamBus master） */
    val bus = master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth))

    /** 引擎非空闲 */
    val busy = Output(Bool())
  })

  // --- FSM 状态（显式二进制编码；勿用 Enum，其默认 one-hot 位宽相同会导致状态比较混淆） ---
  val sIDLE = 0.U(3.W)
  val sREAD = 1.U(3.W)
  val sWRITE = 2.U(3.W)
  val sTRD = 3.U(3.W)
  val sTWR = 4.U(3.W)
  val sDONE = 5.U(3.W)
  val sZWRITE = 6.U(3.W) // Zero-Padding 写状态（发 AW + W 全零突发，不经 dataQueue）
  val state = RegInit(sIDLE)

  // --- 命令锁存 ---
  val cmdReg = Reg(new FbCmd(cfg))
  val errReg = RegInit(false.B)

  // --- 派生量 ---
  val eb = 1.U(3.W) << cmdReg.elemBytesLog2 // 元素字节数
  val M = cmdReg.shape(0) // 最内维元素数（转置的列数）
  val N = cmdReg.shape(1) // 转置的行数

  // --- COPY：嵌套维度计数器（dim0 展开为 beat，外层进位式递增） ---
  val outerIdx = RegInit(VecInit(Seq.fill(cfg.maxDims)(0.U(16.W))))
  val beatInRow = RegInit(0.U(20.W))
  val beatsPerRow = (M * eb) >> log2BB // COPY 行 beat 数（约束为整数）

  // --- 格式转换派生量 ---
  private val cvtActive = if (cfg.enableConversion) cmdReg.cvtEnable && (cmdReg.srcFmt =/= cmdReg.dstFmt) else false.B
  private val srcEB = if (cfg.enableConversion) MuxLookup(cmdReg.srcFmt, 32.U(6.W))(Seq(
    0.U -> 32.U, 1.U -> 16.U, 2.U -> 16.U, 3.U -> 8.U)) else 32.U(6.W)
  private val dstEB = if (cfg.enableConversion) MuxLookup(cmdReg.dstFmt, 32.U(6.W))(Seq(
    0.U -> 32.U, 1.U -> 16.U, 2.U -> 16.U, 3.U -> 8.U)) else 32.U(6.W)
  private val cvtExpand = if (cfg.enableConversion) dstEB > srcEB else false.B
  private val cvtCompress = if (cfg.enableConversion) srcEB > dstEB else false.B
  private val cvtShift = if (cfg.enableConversion) {
    Mux((srcEB === 32.U && dstEB === 8.U) || (srcEB === 8.U && dstEB === 32.U), 2.U(2.W),
    Mux(srcEB =/= dstEB, 1.U(2.W), 0.U(2.W)))
  } else 0.U(2.W)
  // 写侧 beat 计数器（格式转换时与读侧 beatInRow 独立推进）
  val wBeatInRow = if (cfg.enableConversion) RegInit(0.U(20.W)) else beatInRow

  // --- TRANSPOSE：tile 分带游标（仅 supportsTranspose 时有意义，但寄存器始终声明以避免条件编译复杂度） ---
  val cb = RegInit(0.U(16.W)) // 列带（dim0 方向分块）
  val rb = RegInit(0.U(16.W)) // 行带（dim1 方向分块）
  val fillRow = RegInit(0.U(16.W)) // TRD：当前 tile 内已喂入的行数
  val drainCol = RegInit(0.U(16.W)) // TWR：当前 tile 内已写出的列数

  // --- 通道握手寄存器（全部显式复位，避免仿真初始 X 污染 when 条件） ---
  val arIssued = RegInit(false.B)
  val rRemain = RegInit(0.U(20.W))
  val awIssued = RegInit(false.B)
  val wRemain = RegInit(0.U(20.W))
  val segBeats = RegInit(0.U(20.W))
  val trArIssued = RegInit(false.B)
  val trRRemain = RegInit(0.U(20.W))
  val trBeatOff = RegInit(0.U(20.W)) // TRD：行内 beat 偏移（读侧分段）
  val twAwIssued = RegInit(false.B)
  val twWRemain = RegInit(0.U(8.W))

  // --- COPY 数据旁路缓冲（深度由 cfg.resolvedDataQueueDepth 指定，信用计数器保证不溢出） ---
  val dataQueue = Module(new Queue(UInt(cfg.dataWidth.W), cfg.resolvedDataQueueDepth))

  // --- dataQueue 信用计数器：跟踪剩余可入队 beat 数 ---
  private val dqDepth = cfg.resolvedDataQueueDepth
  private val creditW = log2Up(dqDepth + 1)
  val queueCredit = RegInit(dqDepth.U(creditW.W))

  // --- 格式转换模块（仅 enableConversion 时实例化）---
  val converter = if (cfg.enableConversion) Some(Module(new FbFormatConverter(cfg.dataWidth, cfg.converterPipeStages))) else None
  // dstSegBeats: 写侧段 beat 数
  val dstSegBeats = if (cfg.enableConversion) {
    Mux(!cvtActive, segBeats,
      Mux(cvtCompress, segBeats >> cvtShift, segBeats << cvtShift))
  } else segBeats

  // --- 转置缓冲（仅 supportsTranspose 时实例化） ---
  val transp = if (supportsTranspose) Some(Module(new FbTransposeBuffer(cfg))) else None

  // --- Zero-Padding 寄存器 ---
  val padActive = RegInit(false.B)         // 当前命令是否有 padding
  val virtualRow = RegInit(0.U(16.W))      // dim1 虚拟行索引（含 padding 行）
  val outRowTotal = RegInit(0.U(16.W))     // 输出总行数 = padBefore(1)+shape(1)+padAfter(1)
  val outColBytes = RegInit(0.U(20.W))     // 输出行总字节数 = (padBefore(0)+shape(0)+padAfter(0))*elemBytes
  val padBeforeBytes0 = RegInit(0.U(20.W)) // padBefore(0)*elemBytes / beatBytes (in beats)
  val padAfterBytes0 = RegInit(0.U(20.W))  // padAfter(0)*elemBytes / beatBytes (in beats)
  val zPhase = RegInit(0.U(2.W))           // 零写阶段：0=行首pad, 1=data, 2=行尾pad
  val zBeatInRow = RegInit(0.U(20.W))      // sZWRITE 中当前段内已写 beat 偏移
  val zRemain = RegInit(0.U(20.W))         // sZWRITE 当前 AXI 突发剩余 beat
  val zSegBeats = RegInit(0.U(20.W))       // sZWRITE 当前 AXI 突发长度
  val zawIssued = RegInit(false.B)         // sZWRITE AW 已发
  val padOuterDone = RegInit(false.B)      // dimCount>2 时所有外层维度切片已遍历完毕
  val padOuterAddrOff = RegInit(0.U(wideW.W)) // 当前外层切片的目的地址偏移 Σ(outerIdx(d)*dstStride(d)) for d∈[2,dimCount)

  // ==========================================================================
  // 地址与 AXI 通道辅助
  // ==========================================================================

  /** Σ(idx(i) × stride(i))，i ∈ [1, maxDims)，宽位宽累加；
    * 可选 perm 参数重映射 stride 索引（PERMUTE 用） */
  private def strideSum(idx: Vec[UInt], stride: Vec[UInt], perm: Option[Vec[UInt]] = None): UInt = {
    if (cfg.maxDims == 1) 0.U(wideW.W)
    else {
      val contributions = (1 until cfg.maxDims).map { d =>
        val s = perm match {
          case None    => stride(d)
          case Some(p) => stride(p(d))
        }
        (idx(d) * s).pad(wideW)
      }
      contributions.reduce(_ +& _)
    }
  }

  /** 当前行地址 = base + Σ(idx(i) × stride(i))（截断到地址位宽） */
  private def rowAddr(base: UInt, idx: Vec[UInt], stride: Vec[UInt], perm: Option[Vec[UInt]] = None): UInt =
    (base.pad(wideW) +& strideSum(idx, stride, perm))(addrW - 1, 0)

  /** 设置读请求 payload */
  private def issueRead(addr: UInt, len: UInt): Unit = {
    io.bus.rd.payload.addr := addr
    io.bus.rd.payload.len := len
  }

  /** 驱动转置缓冲 tile 参数（start 拉高当拍有效）——仅 supportsTranspose 时调用 */
  private def driveTileParams(src: FbCmd, bandCb: UInt, bandRb: UInt): Unit = {
    transp.foreach { t =>
      val ebv = 1.U(3.W) << src.elemBytesLog2
      val mRem = src.shape(0) - bandCb * T.U
      val nRem = src.shape(1) - bandRb * T.U
      t.io.tileRows := Mux(nRem >= T.U, T.U, nRem)
      t.io.tileCols := Mux(mRem >= T.U, T.U, mRem)
      t.io.colStart := bandCb * T.U
      t.io.beatsPerRow := ((src.shape(0) * ebv) +& (BB - 1).U) >> log2BB
      t.io.elemBytesLog2 := src.elemBytesLog2
    }
  }

  /** 当前 outerIdx 的 dim2+ 对目的地址的贡献（用于 slice 切换时更新 padOuterAddrOff） */
  private val padOuterContribNew: UInt = {
    val contributions = (2 until cfg.maxDims).map { d =>
      val active = d.U(3.W) < cmdReg.dimCount
      Mux(active, (outerIdx(d) * cmdReg.dstStride(d)).pad(wideW), 0.U(wideW.W))
    }
    if (contributions.isEmpty) 0.U(wideW.W)
    else contributions.reduce(_ +& _)
  }

  /** Zero-Padding 推进到下一 virtualRow 的逻辑（从多处调用） */
  private def advanceToNextVirtualRow(): Unit = {
    val nextVR = virtualRow + 1.U
    virtualRow := nextVR
    when(nextVR >= outRowTotal) {
      when(padOuterDone || cmdReg.dimCount <= 2.U) {
        state := sDONE
      }.otherwise {
        // 外层切片未遍历完：重置 virtualRow，锁存新切片的目的地址偏移
        virtualRow := 0.U
        padOuterAddrOff := padOuterContribNew
        // 根据 dim1 padBefore 决定新切片起始状态
        when(cmdReg.padBefore(1) > 0.U) {
          state := sZWRITE
          zBeatInRow := 0.U
          zawIssued := false.B
        }.elsewhen(padBeforeBytes0 > 0.U) {
          state := sZWRITE
          zPhase := 0.U
          zBeatInRow := 0.U
          zawIssued := false.B
        }.otherwise {
          state := sREAD
          arIssued := false.B
        }
      }
    }.otherwise {
      val nextIsDim1Pad = nextVR < cmdReg.padBefore(1) ||
        nextVR >= (cmdReg.padBefore(1) +& cmdReg.shape(1))
      when(nextIsDim1Pad) {
        state := sZWRITE
        zBeatInRow := 0.U
        zawIssued := false.B
      }.otherwise {
        when(padBeforeBytes0 > 0.U) {
          state := sZWRITE
          zPhase := 0.U
          zBeatInRow := 0.U
          zawIssued := false.B
        }.otherwise {
          state := sREAD
          arIssued := false.B
        }
      }
    }
  }

  // ==========================================================================
  // 默认信号（各状态按需覆盖，last-connect 生效）
  // ==========================================================================
  // 所有 master Stream：valid=false, payload=DontCare
  io.bus.rd.valid := false.B
  io.bus.rd.payload := DontCare
  io.bus.wr.valid := false.B
  io.bus.wr.payload := DontCare
  // 所有 slave Stream：ready=false
  io.bus.wrResp.ready := false.B
  io.bus.rdData.ready := false.B
  io.cmdIn.ready := false.B
  io.done.valid := false.B
  io.done.payload := DontCare
  dataQueue.io.enq.valid := false.B
  dataQueue.io.enq.bits := DontCare
  dataQueue.io.deq.ready := false.B

  // 格式转换模块默认信号
  converter.foreach { c =>
    c.io.in.valid := false.B
    c.io.in.bits := DontCare
    c.io.out.ready := false.B
    c.io.cfg_srcFmt := cmdReg.srcFmt
    c.io.cfg_dstFmt := cmdReg.dstFmt
    c.io.cfg_scale := cmdReg.cvtScale
    c.io.cfg_zeroPoint := cmdReg.cvtZeroPoint
    c.io.cfg_enable := cmdReg.cvtEnable
    c.io.flush := false.B
  }

  // 转置缓冲默认驱动
  transp.foreach { t =>
    t.io.start := false.B
    t.io.tileRows := 0.U
    t.io.tileCols := 0.U
    t.io.colStart := 0.U
    t.io.beatsPerRow := 0.U
    t.io.elemBytesLog2 := 0.U
    t.io.outByteOffset := 0.U
    t.io.inBeat.valid := false.B
    t.io.inBeat.payload := DontCare
    t.io.outBeat.ready := false.B
  }

  io.busy := state =/= sIDLE

  // ==========================================================================
  // 非法命令检测（组合逻辑，作用于 cmdIn 上的候选命令）
  // ==========================================================================
  val c = io.cmdIn.payload
  val cEb = 1.U(3.W) << c.elemBytesLog2
  val cRowBytes = c.shape(0) * cEb
  val anyShapeZero = VecInit(
    (0 until cfg.maxDims).map(d => d.U(3.W) < c.dimCount && c.shape(d) === 0.U)
  ).asUInt.orR
  val cBadBase = c.dimCount === 0.U ||
    c.dimCount > cfg.maxDims.U ||
    (c.op === FbOp.TRANSPOSE && c.dimCount =/= 2.U) ||
    (c.op === FbOp.TRANSPOSE && !supportsTranspose.B) ||
    (c.op === FbOp.PERMUTE && !supportsPermute.B) ||
    c.elemBytesLog2 > 2.U ||
    anyShapeZero ||
    (c.op =/= FbOp.TRANSPOSE && cRowBytes(log2BB - 1, 0) =/= 0.U) ||
    c.srcAddr(log2BB - 1, 0) =/= 0.U ||
    c.dstAddr(log2BB - 1, 0) =/= 0.U

  // PERMUTE 校验：permVec 中活跃维度的映射值不得 >= dimCount
  val permBad = (c.op === FbOp.PERMUTE) && {
    val checks = (0 until cfg.maxDims).map { d =>
      d.U(3.W) < c.dimCount && c.permVec(d) >= c.dimCount
    }
    VecInit(checks).asUInt.orR
  }

  // Padding 仅对 COPY 有效（任意 dimCount 均可，但 padding 仅作用于 dim0/dim1）
  val padBad = {
    val hasPadAny = c.padBefore(0) =/= 0.U || c.padAfter(0) =/= 0.U ||
      c.padBefore(1) =/= 0.U || c.padAfter(1) =/= 0.U
    hasPadAny && c.op =/= FbOp.COPY
  }

  // PERMUTE 与 padding 互斥
  val permPadBad = (c.op === FbOp.PERMUTE) && {
    c.padBefore(0) =/= 0.U || c.padAfter(0) =/= 0.U ||
    c.padBefore(1) =/= 0.U || c.padAfter(1) =/= 0.U
  }

  val cBad = cBadBase || permBad || padBad || permPadBad

  // ==========================================================================
  // IDLE：接收命令、锁存、分流
  // ==========================================================================
  val hasPad = (c.padBefore(0) =/= 0.U || c.padAfter(0) =/= 0.U ||
    c.padBefore(1) =/= 0.U || c.padAfter(1) =/= 0.U) && c.op === FbOp.COPY

  io.cmdIn.ready := state === sIDLE
  when(state === sIDLE && io.cmdIn.fire) {
    cmdReg := c
    errReg := cBad
    outerIdx.foreach(_ := 0.U)
    beatInRow := 0.U
    if (cfg.enableConversion) { wBeatInRow := 0.U }
    cb := 0.U
    rb := 0.U

    // Zero-Padding 参数锁存
    padActive := hasPad && !cBad
    when(hasPad && !cBad) {
      virtualRow := 0.U
      outRowTotal := c.padBefore(1) +& c.shape(1) +& c.padAfter(1)
      outColBytes := (c.padBefore(0) +& c.shape(0) +& c.padAfter(0)) * cEb
      padBeforeBytes0 := (c.padBefore(0) * cEb) >> log2BB
      padAfterBytes0 := (c.padAfter(0) * cEb) >> log2BB
      zPhase := 0.U
      padOuterDone := false.B
      padOuterAddrOff := 0.U
    }.otherwise {
      padActive := false.B
      padOuterDone := false.B
    }

    when(cBad) {
      state := sDONE
    }.elsewhen(c.op === FbOp.TRANSPOSE && supportsTranspose.B) {
      state := sTRD
      fillRow := 0.U
      trArIssued := false.B
      trBeatOff := 0.U
      transp.foreach { t =>
        t.io.start := true.B
      }
      driveTileParams(c, 0.U, 0.U)
    }.otherwise {
      // COPY 和 PERMUTE 路径
      when(hasPad) {
        // Zero-Padding COPY：根据 virtualRow=0 决定初始状态
        when(c.padBefore(1) > 0.U) {
          // virtualRow=0 < padBefore(1) → 全零 dim1 行
          state := sZWRITE
          zBeatInRow := 0.U
          zawIssued := false.B
        }.elsewhen((c.padBefore(0) * cEb) >> log2BB =/= 0.U) {
          // 数据行，dim0 行首 pad
          state := sZWRITE
          zPhase := 0.U
          zBeatInRow := 0.U
          zawIssued := false.B
        }.otherwise {
          // 数据行，无 dim0 行首 pad → 直接读数据
          state := sREAD
          arIssued := false.B
        }
      }.otherwise {
        state := sREAD
        arIssued := false.B
      }
    }
  }

  // ==========================================================================
  // COPY/PERMUTE：地址计算
  // ==========================================================================
  val isPermute = if (supportsPermute) cmdReg.op === FbOp.PERMUTE else false.B
  val srcRowAddrNormal = rowAddr(cmdReg.srcAddr, outerIdx, cmdReg.srcStride, None)
  val srcRowAddrPerm   = rowAddr(cmdReg.srcAddr, outerIdx, cmdReg.srcStride, Some(cmdReg.permVec))
  val srcRowAddr = Mux(isPermute, srcRowAddrPerm, srcRowAddrNormal)

  // dstRowAddr：padActive 时使用 virtualRow + padBefore 偏移 + 外层维度贡献
  val dstRowAddrNormal = rowAddr(cmdReg.dstAddr, outerIdx, cmdReg.dstStride, None)
  val dstRowAddrPad = (cmdReg.dstAddr.pad(wideW) +&
    (virtualRow * cmdReg.dstStride(1)).pad(wideW) +&
    (cmdReg.padBefore(0) * eb).pad(wideW) +&
    padOuterAddrOff)(addrW - 1, 0)
  val dstRowAddr = Mux(padActive, dstRowAddrPad, dstRowAddrNormal)

  val rowByteOff = beatInRow << log2BB
  val srcAddrCur = (srcRowAddr.pad(wideW) +& rowByteOff.pad(wideW))(addrW - 1, 0)
  val dstAddrCur = (dstRowAddr.pad(wideW) +& rowByteOff.pad(wideW))(addrW - 1, 0)
  // 写侧地址（enableConversion 时用独立的 wBeatInRow）
  val wRowByteOff = wBeatInRow << log2BB
  val wDstAddrCur = if (cfg.enableConversion) {
    (dstRowAddr.pad(wideW) +& wRowByteOff.pad(wideW))(addrW - 1, 0)
  } else dstAddrCur

  // 突发段 beat 数 = min(行内剩余, maxBurstLen, dataQueue 剩余信用)
  val remRow = beatsPerRow - beatInRow
  val seg = if (cfg.enableConversion) {
    val raw = remRow.min(cfg.maxBurstLen.U).min(queueCredit)
    // 压缩时 seg 须为 compRatio 的倍数；扩展时 seg*ratio 须 <= maxBurstLen
    Mux(!cvtActive, raw,
      Mux(cvtCompress, (raw >> cvtShift) << cvtShift,
      Mux(cvtExpand, raw.min(cfg.maxBurstLen.U >> cvtShift), raw)))
  } else remRow.min(cfg.maxBurstLen.U).min(queueCredit)

  // --- 外层维度进位链：nxtIdx 为进位后的计数器，outerCarry(d) 表示低位全回卷 ---
  val outerCarry = Wire(Vec(cfg.maxDims + 1, Bool()))
  outerCarry(0) := false.B
  outerCarry(1) := true.B
  val nxtIdx = Wire(Vec(cfg.maxDims, UInt(16.W)))
  nxtIdx := outerIdx
  for (d <- 1 until cfg.maxDims) {
    val active = d.U(3.W) < cmdReg.dimCount
    val wrap = outerIdx(d) + 1.U === cmdReg.shape(d)
    outerCarry(d + 1) := outerCarry(d) && active && wrap
    when(active && outerCarry(d)) {
      nxtIdx(d) := Mux(wrap, 0.U, outerIdx(d) + 1.U)
    }
  }

  // ==========================================================================
  // COPY/PERMUTE：READ —— dim0 展开为 INCR 突发段，rdData 数据入 Queue
  // ==========================================================================
  when(state === sREAD) {
    // 当信用为 0 时阻塞 rd（等待 queue 有空间）
    io.bus.rd.valid := !arIssued && queueCredit > 0.U && seg > 0.U
    issueRead(srcAddrCur, seg - 1.U)
    when(io.bus.rd.fire) {
      arIssued := true.B
      rRemain := seg
      segBeats := seg
      // 信用扣减：一次性扣减整个 burst 的 beat 数
      queueCredit := queueCredit - seg
    }
    io.bus.rdData.ready := dataQueue.io.enq.ready
    dataQueue.io.enq.valid := io.bus.rdData.valid
    dataQueue.io.enq.bits := io.bus.rdData.payload.data
    when(io.bus.rdData.fire) {
      when(io.bus.rdData.payload.err) { errReg := true.B }
      when(rRemain === 1.U) {
        state := sWRITE
        awIssued := false.B
        wRemain := dstSegBeats
      }.otherwise {
        rRemain := rRemain - 1.U
      }
    }
  }

  // ==========================================================================
  // COPY/PERMUTE：WRITE —— wr 突发 + Queue/Converter 排空 + 等 wrResp
  // ==========================================================================
  when(state === sWRITE) {
    if (cfg.enableConversion) {
      val conv = converter.get
      // 连接 dataQueue → converter
      conv.io.in.valid := dataQueue.io.deq.valid
      conv.io.in.bits := dataQueue.io.deq.bits
      dataQueue.io.deq.ready := conv.io.in.ready
      // 连接 converter → wr 通道
      io.bus.wr.valid := conv.io.out.valid
      io.bus.wr.payload.data := conv.io.out.bits
      conv.io.out.ready := io.bus.wr.ready
      // 信用归还：converter 消费 dataQueue 时归还
      when(dataQueue.io.deq.fire) {
        queueCredit := queueCredit + 1.U
      }
    } else {
      io.bus.wr.valid := dataQueue.io.deq.valid
      io.bus.wr.payload.data := dataQueue.io.deq.bits
      dataQueue.io.deq.ready := io.bus.wr.ready
      when(io.bus.wr.fire) {
        queueCredit := queueCredit + 1.U
      }
    }
    io.bus.wr.payload.sof := !awIssued
    io.bus.wr.payload.eof := wRemain === 1.U
    io.bus.wr.payload.addr := wDstAddrCur
    io.bus.wr.payload.len := dstSegBeats - 1.U
    io.bus.wr.payload.strb := ((BigInt(1) << BB) - 1).U(BB.W)
    when(io.bus.wr.fire) {
      wRemain := wRemain - 1.U
      when(!awIssued) { awIssued := true.B }
    }
    // 写响应
    io.bus.wrResp.ready := wRemain === 0.U && awIssued
    when(io.bus.wrResp.fire) {
      when(io.bus.wrResp.payload.err) { errReg := true.B }
      arIssued := false.B
      val nxtBeat = beatInRow + segBeats
      if (cfg.enableConversion) { wBeatInRow := wBeatInRow + dstSegBeats }
      when(nxtBeat === beatsPerRow) {
        // 本行写完
        beatInRow := 0.U
        if (cfg.enableConversion) { wBeatInRow := 0.U }
        when(padActive) {
          // Zero-Padding 模式：推进源计数器，然后处理阶段转移
          outerIdx := nxtIdx
          when(outerCarry(cmdReg.dimCount)) {
            padOuterDone := true.B
          }
          when(padAfterBytes0 > 0.U) {
            // dim0 行尾 pad → sZWRITE phase 2
            state := sZWRITE
            zPhase := 2.U
            zBeatInRow := 0.U
            zawIssued := false.B
          }.otherwise {
            // 无行尾 pad → 直接推进 virtualRow
            advanceToNextVirtualRow()
          }
        }.otherwise {
          // 正常 COPY/PERMUTE：外层维度进位
          outerIdx := nxtIdx
          when(outerCarry(cmdReg.dimCount)) {
            state := sDONE
          }.otherwise {
            state := sREAD
          }
        }
      }.otherwise {
        beatInRow := nxtBeat
        state := sREAD
      }
    }
  }

  // ==========================================================================
  // Zero-Padding：sZWRITE —— 发 wr 全零突发（不经 dataQueue）
  // ==========================================================================
  when(state === sZWRITE) {
    // 当前 virtualRow 是否为 dim1 padding 行
    val isDim1PadRow = virtualRow < cmdReg.padBefore(1) ||
      virtualRow >= (cmdReg.padBefore(1) +& cmdReg.shape(1))

    // 当前阶段总 beat 数
    val zPhaseTotalBeats = Mux(isDim1PadRow,
      outColBytes >> log2BB,
      Mux(zPhase === 0.U, padBeforeBytes0, padAfterBytes0))

    // 当前阶段剩余 beat
    val zRemBeats = zPhaseTotalBeats - zBeatInRow

    // 目的地址：基于 virtualRow 和阶段偏移（含外层维度贡献）
    val zRowBaseAddr = (cmdReg.dstAddr.pad(wideW) +&
      (virtualRow * cmdReg.dstStride(1)).pad(wideW) +&
      padOuterAddrOff)(addrW - 1, 0)
    val zDim0PadAfterOff = (cmdReg.padBefore(0) +& cmdReg.shape(0)) * eb
    val zBaseOff = Mux(isDim1PadRow || zPhase === 0.U, 0.U(wideW.W), zDim0PadAfterOff.pad(wideW))
    val zDstAddr = (zRowBaseAddr.pad(wideW) +& zBaseOff +&
      (zBeatInRow << log2BB).pad(wideW))(addrW - 1, 0)

    // 段长计算（无 4KB 边界限制）
    val zSeg = zRemBeats.min(cfg.maxBurstLen.U)

    // wr 通道：全零数据
    io.bus.wr.valid := !(zawIssued && zRemain === 0.U)
    io.bus.wr.payload.sof := !zawIssued
    io.bus.wr.payload.eof := Mux(!zawIssued, zSeg === 1.U, zRemain === 1.U)
    io.bus.wr.payload.addr := zDstAddr
    io.bus.wr.payload.len := zSeg - 1.U
    io.bus.wr.payload.data := 0.U
    io.bus.wr.payload.strb := ((BigInt(1) << BB) - 1).U(BB.W)
    when(io.bus.wr.fire) {
      zRemain := zRemain - 1.U
      when(!zawIssued) {
        zawIssued := true.B
        zRemain := zSeg - 1.U // 首拍已发，剩余 = total - 1
        zSegBeats := zSeg
      }
    }

    // wrResp 通道
    io.bus.wrResp.ready := zRemain === 0.U && zawIssued
    when(io.bus.wrResp.fire) {
      when(io.bus.wrResp.payload.err) { errReg := true.B }
      val nxtZBeat = zBeatInRow + zSegBeats
      when(nxtZBeat >= zPhaseTotalBeats) {
        // 当前阶段完成
        when(isDim1PadRow) {
          // dim1 全零行完成 → 推进 virtualRow
          advanceToNextVirtualRow()
        }.elsewhen(zPhase === 0.U) {
          // dim0 行首 pad 完成 → 进入数据段 sREAD
          state := sREAD
          arIssued := false.B
        }.otherwise {
          // dim0 行尾 pad（phase 2）完成 → 推进 virtualRow
          advanceToNextVirtualRow()
        }
      }.otherwise {
        // 当前阶段未完成，继续下一个突发
        zBeatInRow := nxtZBeat
        zawIssued := false.B
      }
    }
  }

  // ==========================================================================
  // TRANSPOSE（仅 supportsTranspose=true 时生成以下逻辑）
  // ==========================================================================
  if (supportsTranspose) {
    val t = transp.get

    // --- tile 派生量 ---
    val tileRowsCur = Mux(N - rb * T.U >= T.U, T.U, N - rb * T.U)
    val tileColsCur = Mux(M - cb * T.U >= T.U, T.U, M - cb * T.U)
    val srcRowBeats = ((M * eb) +& (BB - 1).U) >> log2BB // 源行 beat 数（允许子 beat 行）

    // --- TRD —— 逐行读源行，beat 流喂入转置缓冲（FILL） ---
    val srcRowIdx = rb * T.U + fillRow
    val trRowAddr = (cmdReg.srcAddr.pad(wideW) +& (srcRowIdx * cmdReg.srcStride(1)).pad(wideW))(addrW - 1, 0)
    val trAddrCur = (trRowAddr.pad(wideW) +& (trBeatOff << log2BB).pad(wideW))(addrW - 1, 0)
    val trRemRow = srcRowBeats - trBeatOff
    val trSeg = trRemRow.min(cfg.maxBurstLen.U)

    when(state === sTRD) {
      io.bus.rd.valid := t.io.busy && !trArIssued
      issueRead(trAddrCur, trSeg - 1.U)
      when(io.bus.rd.fire) {
        trArIssued := true.B
        trRRemain := trSeg
      }
      io.bus.rdData.ready := t.io.inBeat.ready
      t.io.inBeat.valid := io.bus.rdData.valid
      t.io.inBeat.payload := io.bus.rdData.payload.data
      when(io.bus.rdData.fire) {
        when(io.bus.rdData.payload.err) { errReg := true.B }
        when(trRRemain === 1.U) {
          trArIssued := false.B
          when(trBeatOff + trSeg === srcRowBeats) {
            trBeatOff := 0.U
            when(fillRow + 1.U === tileRowsCur) {
              state := sTWR
              drainCol := 0.U
              twAwIssued := false.B
            }.otherwise {
              fillRow := fillRow + 1.U
            }
          }.otherwise {
            trBeatOff := trBeatOff + trSeg
          }
        }.otherwise {
          trRRemain := trRRemain - 1.U
        }
      }
    }

    // --- TWR —— 逐列发 wr，转置缓冲 DRAIN 的 beat 写出，等 wrResp ---
    val outRowIdx = cb * T.U + drainCol
    val twRawAddr = (cmdReg.dstAddr.pad(wideW)
      +& (outRowIdx * cmdReg.dstStride(0)).pad(wideW)
      +& ((rb * T.U) * eb).pad(wideW))(addrW - 1, 0)
    val twAlignedAddr = twRawAddr & ~((BB - 1).U(addrW.W))

    val twLastCol = drainCol + 1.U === tileColsCur
    val twLastRowBand = (rb + 1.U) * T.U >= N
    val twLastColBand = (cb + 1.U) * T.U >= M

    when(state === sTWR) {
      t.io.outByteOffset := twRawAddr(log2BB - 1, 0)
      // wr.valid：首拍无条件有效；后续拍需 outBeat 有效且 burst 未完成
      io.bus.wr.valid := Mux(!twAwIssued, true.B, t.io.outBeat.valid && twWRemain =/= 0.U)
      io.bus.wr.payload.sof := !twAwIssued
      // eof：首拍用 beatsPerCol===1 判断单拍突发；后续拍用 twWRemain===1
      io.bus.wr.payload.eof := Mux(!twAwIssued, t.io.beatsPerCol === 1.U, twWRemain === 1.U)
      io.bus.wr.payload.addr := twAlignedAddr
      io.bus.wr.payload.len := t.io.beatsPerCol - 1.U
      io.bus.wr.payload.data := t.io.outBeat.payload
      io.bus.wr.payload.strb := t.io.outStrb
      t.io.outBeat.ready := io.bus.wr.fire
      when(io.bus.wr.fire) {
        twWRemain := twWRemain - 1.U
        when(!twAwIssued) {
          twAwIssued := true.B
          twWRemain := t.io.beatsPerCol - 1.U // 首拍已发，剩余 = total - 1
        }
      }
      io.bus.wrResp.ready := twWRemain === 0.U && twAwIssued
      when(io.bus.wrResp.fire) {
        when(io.bus.wrResp.payload.err) { errReg := true.B }
        when(twLastCol) {
          twAwIssued := false.B
          drainCol := 0.U
          fillRow := 0.U
          trArIssued := false.B
          trBeatOff := 0.U
          when(twLastRowBand) {
            rb := 0.U
            when(twLastColBand) {
              state := sDONE
            }.otherwise {
              cb := cb + 1.U
              state := sTRD
              t.io.start := true.B
              driveTileParams(cmdReg, cb + 1.U, 0.U)
            }
          }.otherwise {
            rb := rb + 1.U
            state := sTRD
            t.io.start := true.B
            driveTileParams(cmdReg, cb, rb + 1.U)
          }
        }.otherwise {
          drainCol := drainCol + 1.U
          twAwIssued := false.B
        }
      }
    }
  }

  // ==========================================================================
  // DONE：发出完成回执 + flush converter
  // ==========================================================================
  when(state === sDONE) {
    converter.foreach { c => c.io.flush := true.B }
    io.done.valid := true.B
    io.done.payload.tag := cmdReg.tag
    io.done.payload.err := errReg
    when(io.done.fire) {
      state := sIDLE
    }
  }
}
