/** ForagerBee 执行引擎 —— 每个 DMA 通道（FbStreamBus master）一个。
  *
  * 数据通路：
  *   cmdIn（FbCmd）→ FSM 地址生成器 → FbStreamBus 读（rd/rdData）
  *     → COPY：数据 Queue 旁路缓冲 → [FbFormatConverter] → FbStreamBus 写（wr/wrResp）
  *     → TRANSPOSE（supportsTranspose=true）：FbTransposeBuffer → FbStreamBus 写
  *     → PERMUTE：同 COPY 路径，但源 stride 按 permVec 重映射
  *   → Zero-Padding（COPY + padBefore/padAfter）：dim1 全零行 / dim0 行首尾零段直写 sZWRITE
  *   → MX 量化（COPY + isMx(dstFmt)，量化激活由 dstFmt 派生）：dataQueue → FbMxQuantizer → 量化数据写 wr，
  *     scale 字节累积后于每段数据写完后经 sSWRITE 以 strb 部分写突发写到 scaleAddr
  *     （与数据写串行，独占 wr/wrResp）
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

class FbEngine(cfg: ForagerBeeConfig, supportsTranspose: Boolean = true, supportsPermute: Boolean = true, supportsIm2col: Boolean = true, supportsScatterGather: Boolean = true) extends Module {
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

    /** 格式转换量化 scale（仅 cfg.enableConversion 时存在；由顶层全局供给，非命令字段）。
      *
      * **全局活线、无快照**：该 Input 未寄存，组合直连本引擎 FbFormatConverter.cfg_scale；
      * 引擎内 cmdReg 只快照命令字段，不快照量化参数，故命令在飞期间顶层改动 cvtScale 会即时
      * 改变转换结果（不报 err）。软件更新前须排空（顶层 io.busy=0 且 io.occupancy=0）。
      *
      * 实际语义（与 [[FbFormatConverter]] 实现一致，**不是 IEEE-754 FP32**）：
      * 仅低 5 bit `scale(4,0)` 被消费，作为 2 的幂移位量（q = round(v / 2^shift)）；
      * 高 27 bit 被忽略，集成方应置 0。故只支持 2 的幂 scale。 */
    val cvtScale = if (cfg.enableConversion) Some(Input(UInt(32.W))) else None

    /** 格式转换量化 zero point（仅 cfg.enableConversion 时存在；由顶层全局供给，非命令字段）。
      *
      * 实际语义：仅低 8 bit `zeroPoint(7,0)` 被消费，按**有符号整数**解释（−128..127）；
      * 高 24 bit 被忽略，集成方应置 0。 */
    val cvtZeroPoint = if (cfg.enableConversion) Some(Input(UInt(32.W))) else None

    /** 引擎非空闲 */
    val busy = Output(Bool())
  })

  // --- FSM 状态（显式二进制编码；勿用 Enum，其默认 one-hot 位宽相同会导致状态比较混淆） ---
  val sIDLE   = 0.U(4.W)
  val sREAD   = 1.U(4.W)
  val sWRITE  = 2.U(4.W)
  val sTRD    = 3.U(4.W)
  val sTWR    = 4.U(4.W)
  val sDONE   = 5.U(4.W)
  val sZWRITE = 6.U(4.W) // Zero-Padding 写状态（发 AW + W 全零突发，不经 dataQueue）
  val sI2C    = 7.U(4.W) // im2col 主状态
  val sSG     = 8.U(4.W) // scatter/gather 主状态
  val sSWRITE = 9.U(4.W) // MX 量化：E8M0 scale 累积字节写出状态
  val state = RegInit(sIDLE)

  // --- 命令锁存 ---
  val cmdReg = Reg(new FbCmd(cfg))
  val errReg = RegInit(false.B)

  // --- 派生量（全部为 cmdReg 输出的组合，单次共享 MuxLookup，不加额外锁存寄存器组） ---
  val M = cmdReg.shape(0) // 最内维元素数（转置的列数）
  val N = cmdReg.shape(1) // 转置的行数
  val srcBits = FbDataFmt.elemBits(cmdReg.srcFmt)   // 源元素位宽（恒算，摘除 enableConversion guard）
  val dstBits = FbDataFmt.elemBits(cmdReg.dstFmt)   // 目的元素位宽
  val srcRowBytes = FbDataFmt.rowBytes(M, cmdReg.srcFmt) // 源行字节数（ceil，天然处理 INT4）
  val srcEbLog2 = FbDataFmt.ebLog2Of(cmdReg.srcFmt) // 供 transpose/im2col 端口

  // --- COPY：嵌套维度计数器（dim0 展开为 beat，外层进位式递增） ---
  val outerIdx = RegInit(VecInit(Seq.fill(cfg.maxDims)(0.U(16.W))))
  val beatInRow = RegInit(0.U(20.W))
  val beatsPerRow = srcRowBytes >> log2BB // COPY 行 beat 数（约束为整数）

  // --- 格式转换派生量 ---
  private val cvtActive = if (cfg.enableConversion) (cmdReg.srcFmt =/= cmdReg.dstFmt) else false.B
  // 元素位宽取自单一真源 FbDataFmt.elemBits（FbDataFmt ChiselEnum，4-bit）：
  // MX 编码 MXE4M3/MXE5M2 登记为 8-bit，保证量化压缩比推导正确：
  //   FP16/BF16(16)→MX(8)：cvtCompress=true、cvtShift=1；FP32(32)→MX(8)：cvtShift=2
  private val cvtExpand = if (cfg.enableConversion) dstBits > srcBits else false.B
  private val cvtCompress = if (cfg.enableConversion) srcBits > dstBits else false.B
  private val cvtShift = if (cfg.enableConversion) {
    Mux((srcBits === 32.U && dstBits === 8.U) || (srcBits === 8.U && dstBits === 32.U), 2.U(2.W),
    Mux(srcBits =/= dstBits, 1.U(2.W), 0.U(2.W)))
  } else 0.U(2.W)
  // 写侧 beat 计数器（格式转换时与读侧 beatInRow 独立推进）
  val wBeatInRow = if (cfg.enableConversion) RegInit(0.U(20.W)) else beatInRow

  // --- TRANSPOSE：tile 分带游标（仅 supportsTranspose 时有意义，但寄存器始终声明以避免条件编译复杂度） ---
  val cb = RegInit(0.U(16.W)) // 列带（dim0 方向分块）
  val rb = RegInit(0.U(16.W)) // 行带（dim1 方向分块）
  val fillRow = RegInit(0.U(16.W)) // TRD：当前 tile 内已喂入的行数
  val drainCol = RegInit(0.U(16.W)) // TWR：当前 tile 内已写出的列数

  // --- 非阻塞写（仅 writeOutstandingDepth > 1 时生成；=1 走现有阻塞式路径，不新增任何硬件） ---
  private val nbWr = cfg.writeOutstandingDepth > 1
  /** 在途写突发计数（EOF 拍 +1，wrResp 收回 -1）；阻塞路径下为常量 0 不产生硬件 */
  val wrInFlight = if (nbWr) RegInit(0.U(log2Up(cfg.writeOutstandingDepth + 1).W)) else 0.U

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
  // dstSegBeats: 写侧段 beat 数（MX 量化压缩比同 cvtCompress：FP16/BF16→FP8=2，FP32→FP8=4）
  val dstSegBeats = if (cfg.enableConversion) {
    Mux(!cvtActive, segBeats,
      Mux(cvtCompress, segBeats >> cvtShift, segBeats << cvtShift))
  } else segBeats

  // --- MX 量化器（仅 enableConversion 时实例化；运行期由 mxQuantActive 门控）---
  val mxQuantizer = if (cfg.enableConversion) Some(Module(new FbMxQuantizer(cfg.dataWidth))) else None

  // --- 转置缓冲（仅 supportsTranspose 时实例化） ---
  val transp = if (supportsTranspose) Some(Module(new FbTransposeBuffer(cfg))) else None

  // --- im2col 地址生成器（仅 supportsIm2col 时实例化） ---
  val im2colGen = if (supportsIm2col) Some(Module(new FbIm2colAddrGen(cfg.addressWidth))) else None

  // --- scatter/gather 描述符控制模块（仅 supportsScatterGather 且 dataWidth 满足要求时实例化） ---
  private val sgHwEnabled = supportsScatterGather && (cfg.dataWidth >= cfg.addressWidth + 16)
  val sgCtrl = if (sgHwEnabled) Some(Module(new FbScatterGatherCtrl(cfg.addressWidth, cfg.dataWidth))) else None

  // --- IM2COL 子状态和寄存器 ---
  val i2cPhase    = RegInit(0.U(2.W))    // 0=决策, 1=读, 2=写, 3=零写
  val i2cArIssued = RegInit(false.B)
  val i2cRRemain  = RegInit(0.U(20.W))
  val i2cAwIssued = RegInit(false.B)
  val i2cWRemain  = RegInit(0.U(20.W))
  val i2cSegBeats = RegInit(0.U(20.W))
  val i2cStarted  = RegInit(false.B)     // start 脉冲已发，避免重复驱动

  // --- Scatter/Gather 子状态寄存器 ---
  val sgPhase        = RegInit(0.U(2.W))    // 0=等待描述符, 1=数据读, 2=数据写
  val sgLinearOffset = RegInit(0.U(cfg.addressWidth.W))
  val sgCurAddr      = Reg(UInt(cfg.addressWidth.W))
  val sgCurBeats     = Reg(UInt(log2Up(cfg.maxBurstLen + 1).W))
  val sgArIssued     = RegInit(false.B)
  val sgRRemain      = Reg(UInt(log2Up(cfg.maxBurstLen + 1).W))
  val sgAwIssued     = RegInit(false.B)
  val sgWRemain      = Reg(UInt(log2Up(cfg.maxBurstLen + 1).W))
  val sgIsGather     = Reg(Bool())
  val sgSegBeats     = RegInit(0.U(20.W))
  val sgStarted      = RegInit(false.B)

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

  // --- MX 量化寄存器（scale 字节累积 + 行内字节游标 + scale 写出子状态）---
  val mxQuantActive = if (cfg.enableConversion) FbDataFmt.isMx(cmdReg.dstFmt) else false.B
  val mxScaleAccum      = RegInit(VecInit(Seq.fill(BB)(0.U(8.W)))) // scale 字节累积（每段数据写完后经 sSWRITE 写出）
  val mxScaleAccumCount = RegInit(0.U(log2Up(BB + 1).W))           // 累积字节计数（0..BB）
  val mxScaleByteCur    = RegInit(0.U(20.W))                       // 行内已完成写出的 scale 字节游标
  val mxScaleWrByte     = RegInit(0.U(log2Up(BB + 1).W))           // sSWRITE：本轮累积中已写字节数（跨 beat 拆分用）
  val mxScaleAwIssued   = RegInit(false.B)                         // sSWRITE：当前单拍突发已发
  val mxRowEndPending   = RegInit(false.B)                         // sSWRITE：本轮 scale 写完后需推进行（行末）

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
      val ebv = FbDataFmt.bytesOfCeil(src.srcFmt)
      val mRem = src.shape(0) - bandCb * T.U
      val nRem = src.shape(1) - bandRb * T.U
      t.io.tileRows := Mux(nRem >= T.U, T.U, nRem)
      t.io.tileCols := Mux(mRem >= T.U, T.U, mRem)
      t.io.colStart := bandCb * T.U
      t.io.beatsPerRow := ((src.shape(0) * ebv) +& (BB - 1).U) >> log2BB
      t.io.elemBytesLog2 := FbDataFmt.ebLog2Of(src.srcFmt)
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

  /** 当前数据行写完后的推进逻辑（sWRITE 与 sSWRITE 共用）：
    * MX 量化行由 sSWRITE 调用（scale 已写出），非 MX 行由 sWRITE 直接调用 */
  private def finishDataRow(): Unit = {
    beatInRow := 0.U
    if (cfg.enableConversion) { wBeatInRow := 0.U }
    when(padActive) {
      // Zero-Padding 模式：推进源计数器，然后处理阶段转移（MX 量化与 padding 互斥，此分支无 MX）
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
      // 正常 COPY/PERMUTE：外层维度进位（含 MX 量化）
      outerIdx := nxtIdx
      when(outerCarry(cmdReg.dimCount)) {
        state := sDONE
      }.otherwise {
        state := sREAD
      }
    }
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

  // --- 非阻塞写：统一在途计数 + 统一响应汇（仅 nbWr 生成；depth=1 时此段不发射，
  //   各状态 wrResp 处理保持原样）---
  if (nbWr) {
    val nbWrEofFire = io.bus.wr.fire && io.bus.wr.payload.eof
    // 在途计数：写突发 EOF 拍 +1，响应收回拍 -1，同拍抵消
    when(nbWrEofFire && io.bus.wrResp.fire) {
      // +1/-1 同拍抵消，保持值
    }.elsewhen(nbWrEofFire) {
      wrInFlight := wrInFlight + 1.U
    }.elsewhen(io.bus.wrResp.fire) {
      wrInFlight := wrInFlight - 1.U
    }
    // 统一响应汇：只要有在途突发即回收响应；响应 err OR 入 errReg（替代 7 处分散处理）
    io.bus.wrResp.ready := wrInFlight > 0.U
    when(io.bus.wrResp.fire && io.bus.wrResp.payload.err) { errReg := true.B }
  }

  // 格式转换模块默认信号（mxQuantActive 时数据通路改经量化器，此处保持静默）
  converter.foreach { c =>
    c.io.in.valid := false.B
    c.io.in.bits := DontCare
    c.io.out.ready := false.B
    c.io.cfg_srcFmt := cmdReg.srcFmt
    c.io.cfg_dstFmt := cmdReg.dstFmt
    // 量化参数改由顶层 IO 全局供给（原 FbCmd.cvtScale/cvtZeroPoint 已删除）。
    // io.cvtScale/io.cvtZeroPoint 与 converter 同为 cfg.enableConversion 编译期门控，
    // 故此 foreach 内两个 Option 恒为 Some，.get 静态安全。
    c.io.cfg_scale := io.cvtScale.get
    c.io.cfg_zeroPoint := io.cvtZeroPoint.get
    c.io.cfg_enable := cvtActive && !mxQuantActive
    c.io.flush := false.B
  }

  // MX 量化器默认信号（命令锁存后配置常驻；flush 于 sDONE 拉高）
  mxQuantizer.foreach { q =>
    q.io.in.valid := false.B
    q.io.in.bits := DontCare
    q.io.outData.ready := false.B
    q.io.outScale.ready := false.B
    q.io.cfg_srcFmt := cmdReg.srcFmt
    q.io.cfg_dstFmt := cmdReg.dstFmt
    // 块大小编码 → 实际值：0→16、1→32、2→64 个元素共享一个 E8M0 scale
    q.io.cfg_blockSize := MuxLookup(cmdReg.scaleBlockSize, 32.U(7.W))(Seq(
      0.U -> 16.U, 1.U -> 32.U, 2.U -> 64.U))
    q.io.flush := false.B
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

  // im2col 地址生成器默认驱动
  im2colGen.foreach { gen =>
    gen.io.start := false.B
    gen.io.srcBase := cmdReg.srcAddr
    gen.io.dstBase := cmdReg.dstAddr
    // M2：读侧粒度按 srcFmt、写侧粒度按 dstFmt（INT4 已被 subByteOpBad 拒于 IM2COL 外，安全）
    gen.io.srcElemBytesLog2 := srcEbLog2
    gen.io.dstElemBytesLog2 := FbDataFmt.ebLog2Of(cmdReg.dstFmt)
    gen.io.kernel(0) := cmdReg.im2colKernel(0)
    gen.io.kernel(1) := cmdReg.im2colKernel(1)
    gen.io.stride(0) := cmdReg.im2colStride(0)
    gen.io.stride(1) := cmdReg.im2colStride(1)
    gen.io.pad(0) := cmdReg.im2colPad(0)
    gen.io.pad(1) := cmdReg.im2colPad(1)
    gen.io.dilation(0) := cmdReg.im2colDilation(0)
    gen.io.dilation(1) := cmdReg.im2colDilation(1)
    gen.io.inShape(0) := cmdReg.im2colInShape(0)
    gen.io.inShape(1) := cmdReg.im2colInShape(1)
    gen.io.inShape(2) := cmdReg.im2colInShape(2)
    gen.io.advance := false.B
  }

  // scatter/gather 控制模块默认驱动
  sgCtrl.foreach { sg =>
    sg.io.start := false.B
    sg.io.listAddr := 0.U
    sg.io.entryCount := 0.U
    sg.io.advance := false.B
    sg.io.fetchRd.ready := false.B
    sg.io.fetchRdData.valid := false.B
    sg.io.fetchRdData.payload := DontCare
  }

  io.busy := state =/= sIDLE

  // ==========================================================================
  // 非法命令检测（组合逻辑，作用于 cmdIn 上的候选命令）
  // ==========================================================================
  val c = io.cmdIn.payload
  // 行字节数由 srcFmt 单一真源派生（ceil，天然处理 INT4 亚字节）
  val cRowBytes = FbDataFmt.rowBytes(c.shape(0), c.srcFmt)
  val anyShapeZero = VecInit(
    (0 until cfg.maxDims).map(d => d.U(3.W) < c.dimCount && c.shape(d) === 0.U)
  ).asUInt.orR
  val cBadBase = c.dimCount === 0.U ||
    c.dimCount > cfg.maxDims.U ||
    (c.op === FbOp.TRANSPOSE && c.dimCount =/= 2.U) ||
    (c.op === FbOp.TRANSPOSE && !supportsTranspose.B) ||
    (c.op === FbOp.PERMUTE && !supportsPermute.B) ||
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

  // IM2COL 校验：不支持时报错；参数非零校验
  val im2colBad = (c.op === FbOp.IM2COL) && {
    !supportsIm2col.B ||
    c.im2colKernel(0) === 0.U || c.im2colKernel(1) === 0.U ||
    c.im2colStride(0) === 0.U || c.im2colStride(1) === 0.U
  }

  // SCATTER/GATHER 校验：不支持时报错；entryCount=0 或 listAddr 非 beat 对齐
  val sgBad = (c.op === FbOp.SCATTER || c.op === FbOp.GATHER) && {
    !sgHwEnabled.B ||
    c.sgEntryCount === 0.U ||
    c.sgListAddr(log2BB - 1, 0) =/= 0.U
  }

  // MX 量化校验（仅当 isMx(dstFmt) 时生效；量化激活由 dstFmt 派生，无独立开关字段）：
  //   仅 COPY；scaleBlockSize 编码合法（≤2）；
  //   shape(0) 为 blockSize 整数倍；每块 beat 数不超上限；scaleAddr beat 对齐；
  //   srcFmt 必须为 float（isFloat ⇒ 必不等于 MX dstFmt，故转换恒激活）；与 padding 互斥。
  // 整组校验受 cfg.enableConversion 编译期门控：无量化器的构建中 MX 退化为
  // 1 字节/元素裸格式，srcFmt==dstFmt==MX 是合法纯拷贝（另见 mxNonQuantBad 注释），
  // 故本项在 enableConversion=false 时恒为 false.B，不得以运行期 !cfg.xxx.B 形态
  // 进入 OR 列表（否则门控反向：开关关闭时判据恒真，误拒合法裸拷贝）。
  //
  // 注：MuxLookup 的 default 取 16（而非 0）——非法编码（scaleBlockSize>2）已由下方
  // scaleBlockBadEncoding 独立且前置地判为 err，default 分支的取值对判决结果无影响；
  // 取非 0 值可彻底消除 `shape(0) % 0` 这条硬件上无短路的除零路径及其冗余逻辑。
  val mxBlockSizeVal = MuxLookup(c.scaleBlockSize, 16.U(7.W))(Seq(
    0.U -> 16.U, 1.U -> 32.U, 2.U -> 64.U))
  val mxQuantBad = if (cfg.enableConversion) FbDataFmt.isMx(c.dstFmt) && {
    // scaleBlockSize 编码非法（仅 0/1/2 合法）。此项独立前置：既保证 shapeMisalign 的除数
    // 在后续求值时恒为 {16,32,64}，也避免与 shapeMisalign 重复列举同一判据
    val scaleBlockBadEncoding = c.scaleBlockSize > 2.U
    val shapeMisalign = c.shape(0) % mxBlockSizeVal =/= 0.U
    // 每块读侧 beat 数：须 ≤ maxBurstLen 且 ≤ dataQueue 深度，
    // 否则段长对齐后恒为 0（死锁）
    val mxBlockBeatsCmd = FbDataFmt.rowBytes(mxBlockSizeVal, c.srcFmt) >> log2BB
    val blockTooBig = mxBlockBeatsCmd > cfg.maxBurstLen.U ||
      mxBlockBeatsCmd > cfg.resolvedDataQueueDepth.U
    val scaleAddrUnaligned = c.scaleAddr(log2BB - 1, 0) =/= 0.U
    val srcFmtBadMx = !FbDataFmt.isFloat(c.srcFmt)
    // dstFmtBadMx 在本触发条件下恒为 false（前置已是 isMx(c.dstFmt)），保留以维持
    // 校验项的自文档化完整性；综合器会常量折叠，不产生逻辑
    val dstFmtBadMx = !FbDataFmt.isMx(c.dstFmt)
    val hasPadAny = c.padBefore(0) =/= 0.U || c.padAfter(0) =/= 0.U ||
      c.padBefore(1) =/= 0.U || c.padAfter(1) =/= 0.U
    c.op =/= FbOp.COPY ||
    scaleBlockBadEncoding ||
    shapeMisalign ||
    blockTooBig ||
    scaleAddrUnaligned ||
    srcFmtBadMx ||
    dstFmtBadMx ||
    hasPadAny
  } else false.B

  // MX 源格式不得进入非量化路径：硬件无反量化器（MX→FP/INT 的 scale 还原通路不存在），
  // 故 srcFmt 为 MX 族时一律拒绝——无论 dstFmt 是否也是 MX（MX↔MX 由 mxQuantBad.srcFmtBadMx 与本项共同拒绝）。
  // dstFmt 为 MX 已由 isMx(dstFmt) 隐含激活量化（走 FbMxQuantizer），不再属于「非量化裸拷贝」，
  // 因此本项不再检查 dstFmt。enableConversion=false 的构建中 MX 退化为 1 字节/元素裸格式，
  // srcFmt==dstFmt==MX 是合法纯拷贝，故保留 cfg.enableConversion 编译期门控。
  val mxNonQuantBad = cfg.enableConversion.B && FbDataFmt.isMx(c.srcFmt)

  // --- 阶段 A 新增校验 ---
  // 格式编码合法性（无条件，不受 enableConversion 门控）
  val fmtEncBad = !FbDataFmt.isLegal(c.srcFmt) || !FbDataFmt.isLegal(c.dstFmt)

  // 转换格式组合校验（仅 enableConversion 时生成）：INT16/INT32/INT4 转换算术未实现，跨格式即拒
  val cvtFmtBad = if (cfg.enableConversion) {
    (c.srcFmt =/= c.dstFmt) && (
      FbDataFmt.isSubByte(c.srcFmt) || FbDataFmt.isSubByte(c.dstFmt) ||
      c.srcFmt === FbDataFmt.INT16 || c.dstFmt === FbDataFmt.INT16 ||
      c.srcFmt === FbDataFmt.INT32 || c.dstFmt === FbDataFmt.INT32)
  } else false.B

  // 无转换器硬件时强制纯搬运契约（srcFmt==dstFmt）
  val noCvtDiffBad = if (!cfg.enableConversion) c.srcFmt =/= c.dstFmt else false.B

  // 亚字节格式（INT4）仅允许 COPY
  val subByteOpBad = (FbDataFmt.isSubByte(c.srcFmt) || FbDataFmt.isSubByte(c.dstFmt)) && c.op =/= FbOp.COPY

  // INT4 dim0 起始必须偶数保证字节对齐
  val int4StartBad = FbDataFmt.isSubByte(c.srcFmt) && c.srcStartIdx(0)(0) =/= 0.U

  // INT4 与 padding 互斥
  val int4PadBad = (FbDataFmt.isSubByte(c.srcFmt) || FbDataFmt.isSubByte(c.dstFmt)) && {
    c.padBefore(0) =/= 0.U || c.padAfter(0) =/= 0.U ||
    c.padBefore(1) =/= 0.U || c.padAfter(1) =/= 0.U
  }

  // IM2COL + 格式转换时写侧 beat 上界校验（扩展 <<2 为最坏情况）
  val i2cCvtBurstBad = if (cfg.enableConversion) {
    val i2cKW = c.im2colKernel(1)
    val microRowBeatsWorst = ((i2cKW * FbDataFmt.bytesOfCeil(c.srcFmt)).pad(20) +& (BB - 1).U(20.W)) >> log2BB
    (c.op === FbOp.IM2COL) && (c.srcFmt =/= c.dstFmt) && ((microRowBeatsWorst << 2) > cfg.maxBurstLen.U)
  } else false.B

  // M1 层3：IM2COL 压缩方向对齐下界校验。压缩时 micro-row 源 beat 数必须为 2^cvtShift 整数倍
  // 且折算后 ≥1，否则读侧对齐会截尾（语义变化）或折算为 0（wr.len 下溢/死锁）→ 拒绝。
  // 扩展方向沿用 i2cCvtBurstBad 上界；非转换（srcFmt==dstFmt）不涉及。
  val i2cCvtAlignBad = if (cfg.enableConversion) {
    val i2cKW = c.im2colKernel(1)
    val microRowBeatsCmd = ((i2cKW * FbDataFmt.bytesOfCeil(c.srcFmt)).pad(20) +& (BB - 1).U(20.W)) >> log2BB
    val cSrcBits = FbDataFmt.elemBits(c.srcFmt)
    val cDstBits = FbDataFmt.elemBits(c.dstFmt)
    val cCompress = cSrcBits > cDstBits
    val cShift = Mux((cSrcBits === 32.U && cDstBits === 8.U) || (cSrcBits === 8.U && cDstBits === 32.U), 2.U(2.W),
      Mux(cSrcBits =/= cDstBits, 1.U(2.W), 0.U(2.W)))
    val folded = microRowBeatsCmd >> cShift
    val notMultiple = (folded << cShift) =/= microRowBeatsCmd
    (c.op === FbOp.IM2COL) && (c.srcFmt =/= c.dstFmt) && cCompress && (notMultiple || folded === 0.U)
  } else false.B

  // S1：INT4 亚字节 dim0 元素数必须偶数（奇数时 ceil 半字节泄漏，多搬 1 个 garbage nibble；
  // 例：shape(0)=31 → ceil(31*4/8)=16B 恰整 beat，cBadBase 行对齐漏网，本校验兜住）
  val int4ShapeBad = (FbDataFmt.isSubByte(c.srcFmt) || FbDataFmt.isSubByte(c.dstFmt)) && c.shape(0)(0) =/= 0.U

  // S2：TRANSPOSE/PERMUTE/SCATTER/GATHER 不经 converter，srcFmt≠dstFmt 时静默不转换且 dst 偏移
  // 粒度错 → 拒绝（仅 COPY/IM2COL 支持转换；isMx(dstFmt) 的量化通路走量化器，另由 mxQuantBad 校验）。
  // PERMUTE dst 侧地址粒度未验证，保守收紧一并拒绝。
  val cvtOpBad = if (cfg.enableConversion) {
    (c.srcFmt =/= c.dstFmt) && !FbDataFmt.isMx(c.dstFmt) && !(c.op === FbOp.COPY || c.op === FbOp.IM2COL)
  } else false.B

  // N6：padding beat 对齐校验。dim0 前/后 pad 的字节数（按 dst 粒度）必须为整 beat 倍数，
  // 否则 padBeforeBytes0/padAfterBytes0 的 >>log2BB floor 会丢子 beat 余量、数据重叠错位。
  val padAlignBad = {
    val hasPadAny = c.padBefore(0) =/= 0.U || c.padAfter(0) =/= 0.U ||
      c.padBefore(1) =/= 0.U || c.padAfter(1) =/= 0.U
    val dstEb = FbDataFmt.bytesOfCeil(c.dstFmt)
    val beforeBytes = c.padBefore(0) * dstEb
    val afterBytes = c.padAfter(0) * dstEb
    hasPadAny && (beforeBytes(log2BB - 1, 0) =/= 0.U || afterBytes(log2BB - 1, 0) =/= 0.U)
  }

  val cBad = cBadBase || permBad || padBad || permPadBad || im2colBad || sgBad || mxQuantBad || mxNonQuantBad ||
    fmtEncBad || cvtFmtBad || noCvtDiffBad || subByteOpBad || int4StartBad || int4PadBad || i2cCvtBurstBad ||
    i2cCvtAlignBad || int4ShapeBad || cvtOpBad || padAlignBad


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
      outColBytes := (c.padBefore(0) +& c.shape(0) +& c.padAfter(0)) * FbDataFmt.bytesOfCeil(c.dstFmt)
      padBeforeBytes0 := (c.padBefore(0) * FbDataFmt.bytesOfCeil(c.dstFmt)) >> log2BB
      padAfterBytes0 := (c.padAfter(0) * FbDataFmt.bytesOfCeil(c.dstFmt)) >> log2BB
      zPhase := 0.U
      padOuterDone := false.B
      padOuterAddrOff := 0.U
    }.otherwise {
      padActive := false.B
      padOuterDone := false.B
    }

    // MX 量化参数复位（scale 累积器、字节游标与块内元素计数跨命令清零）
    if (cfg.enableConversion) {
      mxScaleAccumCount := 0.U
      mxScaleByteCur := 0.U
      mxScaleWrByte := 0.U
      mxScaleAwIssued := false.B
      mxRowEndPending := false.B
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
    }.elsewhen(c.op === FbOp.IM2COL && supportsIm2col.B) {
      state := sI2C
      i2cPhase := 0.U
      i2cStarted := false.B
    }.elsewhen((c.op === FbOp.SCATTER || c.op === FbOp.GATHER) && sgHwEnabled.B) {
      state := sSG
      sgPhase := 0.U
      sgLinearOffset := 0.U
      sgIsGather := (c.op === FbOp.GATHER)
      sgArIssued := false.B
      sgAwIssued := false.B
      sgStarted := false.B
    }.otherwise {
      // COPY 和 PERMUTE 路径
      when(hasPad) {
        // Zero-Padding COPY：根据 virtualRow=0 决定初始状态
        when(c.padBefore(1) > 0.U) {
          // virtualRow=0 < padBefore(1) → 全零 dim1 行
          state := sZWRITE
          zBeatInRow := 0.U
          zawIssued := false.B
        }.elsewhen((c.padBefore(0) * FbDataFmt.bytesOfCeil(c.dstFmt)) >> log2BB =/= 0.U) {
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

  // 源索引偏移向量：outerIdx + srcStartIdx（仅用于源地址计算）
  val srcIdx = Wire(Vec(cfg.maxDims, UInt(16.W)))
  for (d <- 0 until cfg.maxDims) {
    srcIdx(d) := outerIdx(d) + cmdReg.srcStartIdx(d)
  }
  // dim0 起始偏移（字节）：strideSum 从 d=1 开始，dim0 需单独加偏移
  val srcDim0Offset = FbDataFmt.rowBytes(cmdReg.srcStartIdx(0), cmdReg.srcFmt).pad(wideW)

  val srcRowAddrNormal = rowAddr(cmdReg.srcAddr, srcIdx, cmdReg.srcStride, None)
  val srcRowAddrPerm   = rowAddr(cmdReg.srcAddr, srcIdx, cmdReg.srcStride, Some(cmdReg.permVec))
  val srcRowAddrBase = Mux(isPermute, srcRowAddrPerm, srcRowAddrNormal)
  // 加上 dim0 偏移（从子块起始列开始读取）
  val srcRowAddr = (srcRowAddrBase.pad(wideW) +& srcDim0Offset)(addrW - 1, 0)

  // dstRowAddr：padActive 时使用 virtualRow + padBefore 偏移 + 外层维度贡献
  val dstRowAddrNormal = rowAddr(cmdReg.dstAddr, outerIdx, cmdReg.dstStride, None)
  val dstRowAddrPad = (cmdReg.dstAddr.pad(wideW) +&
    (virtualRow * cmdReg.dstStride(1)).pad(wideW) +&
    (cmdReg.padBefore(0) * FbDataFmt.bytesOfCeil(cmdReg.dstFmt)).pad(wideW) +&
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
    val cvtAligned = Mux(!cvtActive, raw,
      Mux(cvtCompress, (raw >> cvtShift) << cvtShift,
      Mux(cvtExpand, raw.min(cfg.maxBurstLen.U >> cvtShift), raw)))
    // MX 量化：段长再对齐到每块读侧 beat 数（= blockSize × 源元素字节 / beatBytes）的倍数，
    // 保证一个 burst 段不会把 scale 块切断（remRow 恒为整数块，对齐后不会为 0）
    val mxBlockSizeReg = MuxLookup(cmdReg.scaleBlockSize, 32.U(7.W))(Seq(
      0.U -> 16.U, 1.U -> 32.U, 2.U -> 64.U))
    val mxBlockBeatsRaw = FbDataFmt.rowBytes(mxBlockSizeReg, cmdReg.srcFmt) >> log2BB
    val mxBlockBeats = Mux(mxBlockBeatsRaw === 0.U, 1.U, mxBlockBeatsRaw)
    // 段长上限另受限于每段产出 scale 字节数（= segBeats/mxBlockBeats）≤ BB，
    // 保证段内 scale 累积寄存器（1 拍）不溢出（段末统一经 sSWRITE 写出）
    val mxSegCap = BB.U * mxBlockBeats
    val mxAligned = (cvtAligned.min(mxSegCap) / mxBlockBeats) * mxBlockBeats
    Mux(mxQuantActive, mxAligned, cvtAligned)
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
        // 读写解耦（非阻塞路径）：读数据收齐即清 arIssued，下一行读请求不再等写响应，
        // 在途读请求数仍为 1（多读在途留作后续阶段）
        if (nbWr) { arIssued := false.B }
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
      when(mxQuantActive) {
        // --- MX 量化路径：dataQueue → FbMxQuantizer → wr ---
        val q = mxQuantizer.get
        q.io.in.valid := dataQueue.io.deq.valid
        q.io.in.bits := dataQueue.io.deq.bits
        dataQueue.io.deq.ready := q.io.in.ready
        // 量化数据 → wr 通道（dstSegBeats 已按压缩比折算）
        io.bus.wr.valid := q.io.outData.valid
        io.bus.wr.payload.data := q.io.outData.bits
        q.io.outData.ready := io.bus.wr.ready
        // scale 字节即时存入累积寄存器（每段数据写完后经 sSWRITE 写出）
        q.io.outScale.ready := true.B
        when(q.io.outScale.fire) {
          mxScaleAccum(mxScaleAccumCount(log2Up(BB) - 1, 0)) := q.io.outScale.bits
          mxScaleAccumCount := mxScaleAccumCount + 1.U
        }
        // 信用归还：量化器消费 dataQueue 时归还
        when(dataQueue.io.deq.fire) {
          queueCredit := queueCredit + 1.U
        }
      }.elsewhen(cvtActive) {
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
      }.otherwise {
        io.bus.wr.valid := dataQueue.io.deq.valid
        io.bus.wr.payload.data := dataQueue.io.deq.bits
        dataQueue.io.deq.ready := io.bus.wr.ready
        when(io.bus.wr.fire) {
          queueCredit := queueCredit + 1.U
        }
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
    // 写响应 / 推进（非阻塞路径：推进前移到数据突发 EOF 拍，不等 wrResp）
    if (nbWr) {
      // SOF 发放门控：在途突发已满时不允许新突发开始发放（仅压住首拍，突发中途不受影响）
      when(!awIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
        io.bus.wr.valid := false.B
        dataQueue.io.deq.ready := false.B
        if (cfg.enableConversion) {
          when(mxQuantActive) {
            mxQuantizer.get.io.outData.ready := false.B
          }.elsewhen(cvtActive) {
            converter.get.io.out.ready := false.B
          }
        }
      }
      // 推进触发：数据突发 EOF 拍（err 由统一响应汇 OR 入 errReg；arIssued 已在 sREAD 解耦）
      when(io.bus.wr.fire && io.bus.wr.payload.eof) {
        val nxtBeat = beatInRow + segBeats
        if (cfg.enableConversion) { wBeatInRow := wBeatInRow + dstSegBeats }
        val rowEndNow = nxtBeat === beatsPerRow
        when(mxQuantActive) {
          // MX 量化：sWRITE→sSWRITE 转换从“数据突发响应后”改为“数据突发 EOF 拍”，
          // mxRowEndPending 语义不变
          mxRowEndPending := rowEndNow
          mxScaleAwIssued := false.B
          when(rowEndNow) {
            beatInRow := 0.U
            if (cfg.enableConversion) { wBeatInRow := 0.U }
          }.otherwise {
            beatInRow := nxtBeat
          }
          state := sSWRITE
        }.elsewhen(rowEndNow) {
          // 行末：推进行/段/完成（非 MX 路径）
          finishDataRow()
        }.otherwise {
          beatInRow := nxtBeat
          state := sREAD
        }
      }
    } else {
      // 写响应（阻塞式路径逐字保持）
      io.bus.wrResp.ready := wRemain === 0.U && awIssued
      when(io.bus.wrResp.fire) {
        when(io.bus.wrResp.payload.err) { errReg := true.B }
        arIssued := false.B
        val nxtBeat = beatInRow + segBeats
        if (cfg.enableConversion) { wBeatInRow := wBeatInRow + dstSegBeats }
        val rowEndNow = nxtBeat === beatsPerRow
        when(mxQuantActive) {
          // MX 量化：本段数据写完后先经 sSWRITE 写出本段累积的 scale 字节，
          //（段长受限于每段 ≤ BB 字节，累积寄存器不溢出），再推进下一段/行；
          // 行末标记由 mxRowEndPending 携带至 sSWRITE 完成后处理。
          mxRowEndPending := rowEndNow
          mxScaleAwIssued := false.B
          when(rowEndNow) {
            beatInRow := 0.U
            if (cfg.enableConversion) { wBeatInRow := 0.U }
          }.otherwise {
            beatInRow := nxtBeat
          }
          state := sSWRITE
        }.elsewhen(rowEndNow) {
          // 行末：推进行/段/完成（非 MX 路径）
          finishDataRow()
        }.otherwise {
          beatInRow := nxtBeat
          state := sREAD
        }
      }
    }
  }

  // ==========================================================================
  // MX 量化：sSWRITE —— 写出本段累积的 E8M0 scale 字节（不经 dataQueue）
  //   每次进入写空 mxScaleAccum（≤BB 字节，以字节游标 mxScaleByteCur 定位）；
  //   跨 beat 时拆分为至多 2 个单拍突发，strb 标记有效字节区间（部分写）。
  //   Scale 行地址 = scaleAddr + Σ(outerIdx(d) × dstStride(d))（d ∈ [1, dimCount)，
  //   复用外层计数器）；每行 scale 字节数 = shape(0) / blockSize，行末残余以 strb 部分写。
  //   与数据写串行复用 wr/wrResp：进入本状态时数据突发已收到 wrResp。
  // ==========================================================================
  when(state === sSWRITE) {
    val scaleRowAddr = rowAddr(cmdReg.scaleAddr, outerIdx, cmdReg.dstStride, None)
    // 当前待写字节在行内的位置与本突发写入字节数（本拍内不跨 2 个 beat）
    val byteInRow = mxScaleByteCur +& mxScaleWrByte
    val beatOff = byteInRow(log2BB - 1, 0)
    val remAccum = mxScaleAccumCount - mxScaleWrByte
    val cnt = Mux(remAccum >= (BB.U - beatOff), BB.U - beatOff, remAccum)
    val scaleAddrCur = (scaleRowAddr.pad(wideW) +&
      ((byteInRow >> log2BB) << log2BB).pad(wideW))(addrW - 1, 0)
    // strb：本突发有效字节区间 [beatOff, beatOff+cnt)，其余字节不受影响（同拍两次写可合并）
    val sStrb = ((((1.U(BB.W) << cnt) - 1.U) << beatOff))(BB - 1, 0)

    // wr 通道：累积的 scale 字节自 mxScaleWrByte 起打包，单拍突发（len=0）
    io.bus.wr.valid := !mxScaleAwIssued
    io.bus.wr.payload.sof := true.B
    io.bus.wr.payload.eof := true.B
    io.bus.wr.payload.addr := scaleAddrCur
    io.bus.wr.payload.len := 0.U
    io.bus.wr.payload.data := mxScaleAccum.asUInt >> (mxScaleWrByte << 3.U)
    io.bus.wr.payload.strb := sStrb
    when(io.bus.wr.fire) { mxScaleAwIssued := true.B }

    if (nbWr) {
      // 非阻塞：SOF 发放门控（单拍突发，首拍即 SOF）
      when(!mxScaleAwIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
        io.bus.wr.valid := false.B
      }
      // 推进前移到单拍突发 wr.fire 拍（不等 wrResp；单拍突发 fire 当拍即消费，
      // mxScaleAccum/mxScaleAccumCount 清零安全；后续可不等响应直接进入下一段读）
      when(io.bus.wr.fire) {
        mxScaleAwIssued := false.B
        mxScaleWrByte := mxScaleWrByte + cnt
        when(mxScaleWrByte + cnt >= mxScaleAccumCount) {
          // 本轮累积全部写完：推进字节游标、清累积，再推进行/段或回 sREAD 继续下一段
          mxScaleWrByte := 0.U
          mxScaleAccumCount := 0.U
          when(mxRowEndPending) {
            mxScaleByteCur := 0.U
            mxRowEndPending := false.B
            finishDataRow()
          }.otherwise {
            mxScaleByteCur := mxScaleByteCur + mxScaleAccumCount
            state := sREAD
          }
        }
        // otherwise：跨 beat 残余，留在 sSWRITE 发下一单拍突发（同一累积内容续写）
      }
    } else {
      // wrResp 通道（与数据写串行：此时无在途数据突发；阻塞式路径逐字保持）
      io.bus.wrResp.ready := mxScaleAwIssued
      when(io.bus.wrResp.fire) {
        when(io.bus.wrResp.payload.err) { errReg := true.B }
        mxScaleAwIssued := false.B
        mxScaleWrByte := mxScaleWrByte + cnt
        when(mxScaleWrByte + cnt >= mxScaleAccumCount) {
          // 本轮累积全部写完：推进字节游标、清累积，再推进行/段或回 sREAD 继续下一段
          mxScaleWrByte := 0.U
          mxScaleAccumCount := 0.U
          when(mxRowEndPending) {
            mxScaleByteCur := 0.U
            mxRowEndPending := false.B
            finishDataRow()
          }.otherwise {
            mxScaleByteCur := mxScaleByteCur + mxScaleAccumCount
            state := sREAD
          }
        }
        // otherwise：跨 beat 残余，留在 sSWRITE 发下一单拍突发（同一累积内容续写）
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
    val zDim0PadAfterOff = (cmdReg.padBefore(0) +& cmdReg.shape(0)) * FbDataFmt.bytesOfCeil(cmdReg.dstFmt)
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

    if (nbWr) {
      // 非阻塞：SOF 发放门控（仅压住新突发首拍）
      when(!zawIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
        io.bus.wr.valid := false.B
      }
      // 推进前移到零突发 EOF 拍；单拍突发（sof && eof 同拍）时 zSegBeats 尚未更新，
      // 用当拍组合量 zSeg 代替（中途拍用已锁存的 zSegBeats）
      when(io.bus.wr.fire && io.bus.wr.payload.eof) {
        val nbZBurstBeats = Mux(zawIssued, zSegBeats, zSeg)
        val nxtZBeat = zBeatInRow + nbZBurstBeats
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
    } else {
      // wrResp 通道（阻塞式路径逐字保持）
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
  }

  // ==========================================================================
  // TRANSPOSE（仅 supportsTranspose=true 时生成以下逻辑）
  // ==========================================================================
  if (supportsTranspose) {
    val t = transp.get

    // --- tile 派生量 ---
    val tileRowsCur = Mux(N - rb * T.U >= T.U, T.U, N - rb * T.U)
    val tileColsCur = Mux(M - cb * T.U >= T.U, T.U, M - cb * T.U)
    val srcRowBeats = (srcRowBytes +& (BB - 1).U) >> log2BB // 源行 beat 数（允许子 beat 行）

    // --- TRD —— 逐行读源行，beat 流喂入转置缓冲（FILL） ---
    val srcRowIdx = rb * T.U + fillRow + cmdReg.srcStartIdx(1)
    val trDim0ByteOff = FbDataFmt.rowBytes(cmdReg.srcStartIdx(0), cmdReg.srcFmt).pad(wideW)
    val trRowAddr = (cmdReg.srcAddr.pad(wideW) +& (srcRowIdx * cmdReg.srcStride(1)).pad(wideW) +& trDim0ByteOff)(addrW - 1, 0)
    val trAddrCur = (trRowAddr.pad(wideW) +& (trBeatOff << log2BB).pad(wideW))(addrW - 1, 0)
    val trRemRow = srcRowBeats - trBeatOff
    val trSeg = trRemRow.min(cfg.maxBurstLen.U)

    when(state === sTRD) {
      if (nbWr) {
        // 非阻塞：下一 tile 的 start 脉冲在此按 !busy 补发。
        // sTWR 最后一列突发 EOF 拍 FbTransposeBuffer 正在同拍消费最后一个 drain beat，
        // 其 sBDRAIN 分支按 last-connect 用 state := sBIDLE 覆盖 start 的 state := sBFILL，
        // start 脉冲会被吞掉（缓冲停 sBIDLE → rd.valid 恒 0 → 多 tile 转置死锁），
        // 故 nbWr 路径改为等缓冲真正空闲后在此补发。
        // 首 tile 的 start 已在 sIDLE→sTRD 发出（缓冲已忙），该条件不会误触发。
        when(!t.io.busy) {
          t.io.start := true.B
          driveTileParams(cmdReg, cb, rb) // cb/rb 已在上一拍 sTWR EOF 更新为新 tile 坐标
        }
      }
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
      +& ((rb * T.U) * FbDataFmt.bytesOfCeil(cmdReg.srcFmt)).pad(wideW))(addrW - 1, 0)
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
      if (nbWr) {
        // 非阻塞：SOF 发放门控（仅压住列突发首拍）
        when(!twAwIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
          io.bus.wr.valid := false.B
        }
        // 推进前移到列突发 EOF 拍（不等 wrResp）。
        // 注意：下一 tile 的 start 脉冲不在此发出（会与缓冲 sBDRAIN 末拍的
        // state := sBIDLE 冲突被吞），改由 sTRD 内按 !t.io.busy 补发。
        when(io.bus.wr.fire && io.bus.wr.payload.eof) {
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
              }
            }.otherwise {
              rb := rb + 1.U
              state := sTRD
            }
          }.otherwise {
            drainCol := drainCol + 1.U
            twAwIssued := false.B
          }
        }
      } else {
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
  }

  // ==========================================================================
  // IM2COL：sI2C 子 FSM（仅 supportsIm2col=true 时生成逻辑）
  // ==========================================================================
  if (supportsIm2col) {
    val gen = im2colGen.get

    // micro-row beat 数计算：ceil(kW * elemBytes / beatBytes)
    val i2cKW = cmdReg.im2colKernel(1)
    val i2cMicroRowBeats = ((i2cKW * FbDataFmt.bytesOfCeil(cmdReg.srcFmt)).pad(20) +& (BB - 1).U(20.W)) >> log2BB
    // M1 层1 读侧压缩对齐：镜像 COPY 路径 seg 压缩对齐（(raw >> cvtShift) << cvtShift），
    // 压缩时把 micro-row 源 beat 数向下对齐到 compRatio(=2^cvtShift) 倍数，保证读入 beat 数与
    // converter 完整吐出严格自洽、不留半包滞留。非整除组合已被 i2cCvtAlignBad 校验拒绝，故对齐
    // 公式实际只在整除时生效（恒等）；扩展/非转换方向恒等，enableConversion=false 时常量折叠，
    // RTL 逐字不变。issueRead/i2cRRemain/i2cSegBeats/queueCredit 全部改用对齐版本。
    val i2cMicroRowBeatsAligned = Mux(cvtCompress, (i2cMicroRowBeats >> cvtShift) << cvtShift, i2cMicroRowBeats)
    // 写侧 beat 折算（镜像 COPY 路径 dstSegBeats：压缩右移 cvtShift、扩展左移 cvtShift；
    // !cvtActive 时恒等 → enableConversion=false 配置常量折叠，RTL 逐字不变）
    val i2cMicroRowDstBeats = Mux(!cvtActive, i2cMicroRowBeats,
      Mux(cvtCompress, i2cMicroRowBeats >> cvtShift, i2cMicroRowBeats << cvtShift))
    val i2cDstSegBeats = Mux(!cvtActive, i2cSegBeats,
      Mux(cvtCompress, i2cSegBeats >> cvtShift, i2cSegBeats << cvtShift))

    when(state === sI2C) {
      // start 脉冲：进入 sI2C 的第一拍发出
      when(!i2cStarted) {
        gen.io.start := true.B
        i2cStarted := true.B
      }

      when(gen.io.done) {
        state := sDONE
      }.elsewhen(gen.io.valid) {
        switch(i2cPhase) {
          is(0.U) { // 决策阶段
            when(gen.io.isPad) {
              i2cPhase := 3.U  // 零写
              i2cAwIssued := false.B
              i2cWRemain := i2cMicroRowDstBeats
            }.otherwise {
              i2cPhase := 1.U  // 读
              i2cArIssued := false.B
            }
          }
          is(1.U) { // 读阶段
            // M1 层1：读 beat 数改用压缩对齐版本；镜像 COPY 的 `seg > 0.U` 零段门控
            //（非整除/折算为 0 的组合已被 i2cCvtAlignBad 校验拒绝，此处为防御性冗余）
            io.bus.rd.valid := !i2cArIssued && queueCredit > 0.U && i2cMicroRowBeatsAligned > 0.U
            issueRead(gen.io.srcAddr, i2cMicroRowBeatsAligned - 1.U)
            when(io.bus.rd.fire) {
              i2cArIssued := true.B
              i2cRRemain := i2cMicroRowBeatsAligned
              i2cSegBeats := i2cMicroRowBeatsAligned
              queueCredit := queueCredit - i2cMicroRowBeatsAligned
            }
            io.bus.rdData.ready := dataQueue.io.enq.ready
            dataQueue.io.enq.valid := io.bus.rdData.valid
            dataQueue.io.enq.bits := io.bus.rdData.payload.data
            when(io.bus.rdData.fire) {
              when(io.bus.rdData.payload.err) { errReg := true.B }
              when(i2cRRemain === 1.U) {
                i2cPhase := 2.U
                i2cAwIssued := false.B
                i2cWRemain := i2cDstSegBeats
              }.otherwise {
                i2cRRemain := i2cRRemain - 1.U
              }
            }
          }
          is(2.U) { // 写阶段
            // M1 层2 零段保护：dst beat 数为 0 时不发写突发，直接 advance 跳过写阶段
            //（镜像 COPY 路径 seg>0 保护；压缩折算为 0 的组合已被 i2cCvtAlignBad 拒绝，此为防御性冗余，
            //  避免 wr.len=0-1=255 下溢、eof 永假的死锁）
            when(i2cDstSegBeats === 0.U) {
              gen.io.advance := true.B
              i2cPhase := 0.U
            }.otherwise {
              if (cfg.enableConversion) {
                val conv = converter.get
                conv.io.in.valid := dataQueue.io.deq.valid
                conv.io.in.bits := dataQueue.io.deq.bits
                dataQueue.io.deq.ready := conv.io.in.ready
                io.bus.wr.valid := conv.io.out.valid
                io.bus.wr.payload.data := conv.io.out.bits
                conv.io.out.ready := io.bus.wr.ready
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
              io.bus.wr.payload.sof := !i2cAwIssued
              io.bus.wr.payload.eof := i2cWRemain === 1.U
              io.bus.wr.payload.addr := gen.io.dstAddr
              io.bus.wr.payload.len := i2cDstSegBeats - 1.U
              io.bus.wr.payload.strb := ((BigInt(1) << BB) - 1).U(BB.W)
              when(io.bus.wr.fire) {
                i2cWRemain := i2cWRemain - 1.U
                when(!i2cAwIssued) { i2cAwIssued := true.B }
              }
              // 写响应 / 推进（非阻塞路径前移到 EOF 拍）
              if (nbWr) {
                // SOF 发放门控（仅压住首拍）
                when(!i2cAwIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
                  io.bus.wr.valid := false.B
                  dataQueue.io.deq.ready := false.B
                  if (cfg.enableConversion) { converter.get.io.out.ready := false.B }
                }
                when(io.bus.wr.fire && io.bus.wr.payload.eof) {
                  // micro-row 完成，推进 addrGen
                  gen.io.advance := true.B
                  i2cPhase := 0.U
                }
              } else {
                io.bus.wrResp.ready := i2cWRemain === 0.U && i2cAwIssued
                when(io.bus.wrResp.fire) {
                  when(io.bus.wrResp.payload.err) { errReg := true.B }
                  // micro-row 完成，推进 addrGen
                  gen.io.advance := true.B
                  i2cPhase := 0.U
                }
              }
            }
          }
          is(3.U) { // 零写阶段
            // M1 层2 零段保护：dst beat 数为 0 时不发零写突发，直接 advance（避免无限零写冲毁内存）
            when(i2cMicroRowDstBeats === 0.U) {
              gen.io.advance := true.B
              i2cPhase := 0.U
            }.otherwise {
              io.bus.wr.valid := true.B
              io.bus.wr.payload.sof := !i2cAwIssued
              io.bus.wr.payload.eof := i2cWRemain === 1.U
              io.bus.wr.payload.addr := gen.io.dstAddr
              io.bus.wr.payload.len := i2cMicroRowDstBeats - 1.U
              io.bus.wr.payload.data := 0.U
              io.bus.wr.payload.strb := ((BigInt(1) << BB) - 1).U(BB.W)
              when(io.bus.wr.fire) {
                i2cWRemain := i2cWRemain - 1.U
                when(!i2cAwIssued) { i2cAwIssued := true.B }
              }
              if (nbWr) {
                // SOF 发放门控（仅压住首拍）
                when(!i2cAwIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
                  io.bus.wr.valid := false.B
                }
                when(io.bus.wr.fire && io.bus.wr.payload.eof) {
                  gen.io.advance := true.B
                  i2cPhase := 0.U
                }
              } else {
                io.bus.wrResp.ready := i2cWRemain === 0.U && i2cAwIssued
                when(io.bus.wrResp.fire) {
                  when(io.bus.wrResp.payload.err) { errReg := true.B }
                  gen.io.advance := true.B
                  i2cPhase := 0.U
                }
              }
            }
          }
        }
      }
    }
  }

  // ==========================================================================
  // SCATTER/GATHER：sSG 子 FSM（仅 supportsScatterGather=true 时生成逻辑）
  // ==========================================================================
  if (sgHwEnabled) {
    val sg = sgCtrl.get

    when(state === sSG) {
      // start 脉冲：进入 sSG 的第一拍发出
      when(!sgStarted) {
        sg.io.start := true.B
        sg.io.listAddr := cmdReg.sgListAddr
        sg.io.entryCount := cmdReg.sgEntryCount
        sgStarted := true.B
      }

      // 完成判定：sgCtrl.done 且当前不在数据搬运中
      when(sg.io.done && sgPhase === 0.U) {
        state := sDONE
      }.otherwise {
        switch(sgPhase) {
          is(0.U) { // 等待描述符就绪
            // bus.rd 控制权交给 sgCtrl 用于 fetch 描述符
            io.bus.rd.valid := sg.io.fetchRd.valid
            io.bus.rd.payload := sg.io.fetchRd.payload
            sg.io.fetchRd.ready := io.bus.rd.ready

            // bus.rdData 反馈给 sgCtrl
            sg.io.fetchRdData.valid := io.bus.rdData.valid
            sg.io.fetchRdData.payload := io.bus.rdData.payload
            io.bus.rdData.ready := sg.io.fetchRdData.ready

            // 当 sgCtrl 输出当前条目就绪时，锁存地址和长度
            when(sg.io.valid) {
              sgCurAddr := sg.io.curAddr
              // 条目字节数转换为 beat 数：ceil(len / beatBytes)
              val lenBeats = (sg.io.curLen +& (BB - 1).U) >> log2BB
              sgCurBeats := lenBeats
              sgPhase := 1.U
              sgArIssued := false.B
              sgRRemain := 0.U
            }
          }
          is(1.U) { // 数据读阶段
            // 读地址：GATHER 从散列源读，SCATTER 从线性源读
            val rdAddr = Mux(sgIsGather,
              sgCurAddr,
              (cmdReg.srcAddr.pad(wideW) +& sgLinearOffset.pad(wideW))(addrW - 1, 0))

            // 段长计算
            val sgRemBeats = sgCurBeats - sgRRemain
            val sgSeg = sgRemBeats.min(cfg.maxBurstLen.U).min(queueCredit)

            io.bus.rd.valid := !sgArIssued && queueCredit > 0.U && sgSeg > 0.U
            issueRead(rdAddr, sgSeg - 1.U)
            when(io.bus.rd.fire) {
              sgArIssued := true.B
              sgRRemain := sgSeg
              sgSegBeats := sgSeg
              queueCredit := queueCredit - sgSeg
            }

            // 接收读数据入 dataQueue
            io.bus.rdData.ready := dataQueue.io.enq.ready
            dataQueue.io.enq.valid := io.bus.rdData.valid
            dataQueue.io.enq.bits := io.bus.rdData.payload.data
            when(io.bus.rdData.fire) {
              when(io.bus.rdData.payload.err) { errReg := true.B }
              when(sgRRemain === 1.U) {
                // 本段读完，进入写阶段
                sgPhase := 2.U
                sgAwIssued := false.B
                sgWRemain := sgSegBeats
              }.otherwise {
                sgRRemain := sgRRemain - 1.U
              }
            }
          }
          is(2.U) { // 数据写阶段
            // 写地址：GATHER 写线性目的，SCATTER 写散列目的
            val wrAddr = Mux(sgIsGather,
              (cmdReg.dstAddr.pad(wideW) +& sgLinearOffset.pad(wideW))(addrW - 1, 0),
              sgCurAddr)

            // 从 dataQueue 写出
            io.bus.wr.valid := dataQueue.io.deq.valid
            io.bus.wr.payload.data := dataQueue.io.deq.bits
            dataQueue.io.deq.ready := io.bus.wr.ready
            io.bus.wr.payload.sof := !sgAwIssued
            io.bus.wr.payload.eof := sgWRemain === 1.U
            io.bus.wr.payload.addr := wrAddr
            io.bus.wr.payload.len := sgSegBeats - 1.U
            io.bus.wr.payload.strb := ((BigInt(1) << BB) - 1).U(BB.W)
            when(io.bus.wr.fire) {
              queueCredit := queueCredit + 1.U
              sgWRemain := sgWRemain - 1.U
              when(!sgAwIssued) { sgAwIssued := true.B }
            }

            // 写响应 / 推进（非阻塞路径前移到 EOF 拍）
            if (nbWr) {
              // SOF 发放门控（仅压住首拍）
              when(!sgAwIssued && wrInFlight >= cfg.writeOutstandingDepth.U) {
                io.bus.wr.valid := false.B
                dataQueue.io.deq.ready := false.B
              }
              when(io.bus.wr.fire && io.bus.wr.payload.eof) {
                // 更新线性偏移
                sgLinearOffset := sgLinearOffset + (sgSegBeats << log2BB)
                // 本段数据搬运完成
                val remainAfterSeg = sgCurBeats - sgSegBeats
                when(remainAfterSeg === 0.U) {
                  // 当前条目全部搬运完毕，advance 并回 phase 0
                  sg.io.advance := true.B
                  sgPhase := 0.U
                }.otherwise {
                  // 还有剩余 beat，继续读下一段
                  sgCurBeats := remainAfterSeg
                  // 更新散列端地址偏移（下一段从上次结束位置开始）
                  sgCurAddr := sgCurAddr + (sgSegBeats << log2BB)
                  sgPhase := 1.U
                  sgArIssued := false.B
                  sgRRemain := 0.U
                }
              }
            } else {
              io.bus.wrResp.ready := sgWRemain === 0.U && sgAwIssued
              when(io.bus.wrResp.fire) {
                when(io.bus.wrResp.payload.err) { errReg := true.B }
                // 更新线性偏移
                sgLinearOffset := sgLinearOffset + (sgSegBeats << log2BB)
                // 本段数据搬运完成
                val remainAfterSeg = sgCurBeats - sgSegBeats
                when(remainAfterSeg === 0.U) {
                  // 当前条目全部搬运完毕，advance 并回 phase 0
                  sg.io.advance := true.B
                  sgPhase := 0.U
                }.otherwise {
                  // 还有剩余 beat，继续读下一段
                  sgCurBeats := remainAfterSeg
                  // 更新散列端地址偏移（下一段从上次结束位置开始）
                  sgCurAddr := sgCurAddr + (sgSegBeats << log2BB)
                  sgPhase := 1.U
                  sgArIssued := false.B
                  sgRRemain := 0.U
                }
              }
            }
          }
        }
      }
    }
  }

  // ==========================================================================
  // DONE：发出完成回执 + flush converter
  // ==========================================================================
  when(state === sDONE) {
    converter.foreach { c => c.io.flush := true.B }
    mxQuantizer.foreach { q => q.io.flush := true.B }
    if (nbWr) {
      // 非阻塞：done 排空门——必须等全部在途写响应收回后才发 done；
      // 最后一个响应与 errReg 置位同拍，计数器下拍归零，done 不早发
      io.done.valid := wrInFlight === 0.U
    } else {
      io.done.valid := true.B
    }
    io.done.payload.tag := cmdReg.tag
    io.done.payload.err := errReg
    when(io.done.fire) {
      state := sIDLE
    }
  }
}
