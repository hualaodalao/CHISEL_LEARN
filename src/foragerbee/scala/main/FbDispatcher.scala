/** ForagerBee 命令调度器 —— 单队列入口 + 地址冲突检查 + 通道能力匹配派发。
  *
  * 结构：
  *   io.push → StreamFifo(FbCmd, queueDepth) → 冲突检查 + 通道选择逻辑 → io.pop(0..numPorts-1)
  *
  * 冲突检查策略（保守 bounding box）：
  *   - 调度器在派发命令时，锁存每通道当前 in-flight 命令的地址范围：
  *     读区间 [srcAddr, srcAddr + srcLen)、写区间 [dstAddr, dstAddr + dstLen)，
  *     以及 MX 量化的 scale 侧信道写区间 [scaleAddr, scaleAddr + scaleLen)
  *   - srcLen/dstLen 采用最外维度步长 × 最外维度 shape 的保守估计：
  *     len = shape(maxActiveDim-1) * stride(maxActiveDim-1)（1D 时 len = shape(0) * elemBytes）
  *   - **scaleLen（MX 量化侧信道写）**：scale 区不是自 scaleAddr 起的单一连续块，而是
  *     按 dstStride 分行分布（见 FbEngine.sSWRITE：行地址 = scaleAddr + Σ(outerIdx(d)×dstStride(d))，
  *     行内以 beat 对齐的单拍 strb 部分写覆盖 ceil(shape(0)/blockSize) 字节）。故保守上界为
  *     scaleLen = Σ_{d∈[1,dimCount)}(shape(d)-1)×dstStride(d) + roundUpToBeat(shape(0)/blockSize)。
  *     仅在量化激活（enableConversion && isMx(dstFmt)）时纳入，非 MX 命令的 scaleAddr 为陈旧值，
  *     置空区间避免误报。
  *   - 新命令与所有 in-flight 命令做三种冲突检测（scale 区是真实内存写，与 dst 写同等待遇）：
  *     RAW：新命令 src 与 in-flight 的 dst / scale 有重叠
  *     WAR：新命令 dst / scale 与 in-flight 的 src 有重叠
  *     WAW：新命令 dst / scale 与 in-flight 的 dst / scale 有重叠（四种组合全查）
  *   - 重叠判定：!(a_end <= b_start || b_end <= a_start)
  *   - 有冲突时反压队列出口（pop.ready = false）
  *   - scale 区作为**独立第三区间**参与两两重叠检测（而非并入 dst 区包络），避免
  *     dst 区与 scale 区之间的空洞被判为冲突而产生大面积误报
  *
  * 通道能力匹配：
  *   - TRANSPOSE 命令只能派发到 channelTranspose(i)=true 的通道
  *   - COPY 命令可派发到任意空闲通道
  *   - 多个空闲通道满足时选编号最小的（优先级编码）
  *
  * 完成通知：
  *   - 引擎 done fire 时调度器清除对应通道的 in-flight 状态
  */

import chisel3._
import chisel3.util._

class FbDispatcher(cfg: ForagerBeeConfig) extends Module {
  val io = IO(new Bundle {

    /** 命令入队口（单个 slave Stream） */
    val push = slave(Stream(new FbCmd(cfg)))

    /** 队列占用深度（0..queueDepth） */
    val occupancy = Output(UInt(log2Up(cfg.queueDepth + 1).W))

    /** 每通道命令出口（每通道一个 master Stream(FbCmd)） */
    val pop = Vec(cfg.numPorts, master(Stream(new FbCmd(cfg))))

    /** 每通道忙信号（来自引擎） */
    val chBusy = Input(Vec(cfg.numPorts, Bool()))

    /** 每通道 done fire 信号（来自引擎，脉冲式，用于清除 in-flight 状态） */
    val chDone = Input(Vec(cfg.numPorts, Bool()))
  })

  // --- 命令 FIFO ---
  val fifo = Module(new StreamFifo(new FbCmd(cfg), cfg.queueDepth))
  fifo.io.flush := false.B
  fifo.io.push << io.push
  io.occupancy := fifo.io.occupancy

  // --- 每通道 in-flight 地址范围寄存器 ---
  val infSrcStart = RegInit(VecInit(Seq.fill(cfg.numPorts)(0.U(cfg.addressWidth.W))))
  val infSrcEnd   = RegInit(VecInit(Seq.fill(cfg.numPorts)(0.U(cfg.addressWidth.W))))
  val infDstStart = RegInit(VecInit(Seq.fill(cfg.numPorts)(0.U(cfg.addressWidth.W))))
  val infDstEnd   = RegInit(VecInit(Seq.fill(cfg.numPorts)(0.U(cfg.addressWidth.W))))
  // MX 量化 scale 侧信道写区间（第三区间，独立参与冲突检测）。
  // infScaleValid 与 infValid 分离：仅量化激活的命令派发时才置位，
  // 非 MX 命令的 scaleAddr 为陈旧值，不得参与检测（否则误报）。
  val infScaleStart = RegInit(VecInit(Seq.fill(cfg.numPorts)(0.U(cfg.addressWidth.W))))
  val infScaleEnd   = RegInit(VecInit(Seq.fill(cfg.numPorts)(0.U(cfg.addressWidth.W))))
  val infScaleValid = RegInit(VecInit(Seq.fill(cfg.numPorts)(false.B)))
  val infValid    = RegInit(VecInit(Seq.fill(cfg.numPorts)(false.B)))

  // --- done 清除 in-flight ---
  for (p <- 0 until cfg.numPorts) {
    when(io.chDone(p)) {
      infValid(p) := false.B
      infScaleValid(p) := false.B
    }
  }

  // --- 新命令地址范围计算（bounding box 保守估计） ---
  val cmd = fifo.io.pop.payload
  val cmdValid = fifo.io.pop.valid

  // bounding box 计算：对于 ND 命令，用最高有效维度的 (shape-1)*stride 作上界
  // srcLen = 对 dim 从 1 到 dimCount-1 取 max((shape(d)-1)*stride(d))，再加 dim0 行字节数
  // 简化实现：遍历所有维度取和的方式（实际用最外维即可），此处用保守上界：
  // totalLen = sum_{d=0..dimCount-1}( (shape(d)-1) * stride(d) ) + shape(0) * elemBytes
  // 但更保守简单的方法是：end = addr + shape(maxDim-1) * stride(maxDim-1)（仅对最外维）
  // 我们用最保守的完整边界估计：addr + max(shape(d)*stride(d) for all d)
  // 实际最安全：srcEnd = srcAddr + sum_of_all_dim_contribution + dim0_rowbytes
  // 为简单起见，这里用简化 bounding box:
  //   srcEnd = srcAddr + (shape(dimCount-1)-1) * srcStride(dimCount-1) + shape(0) * elemBytes
  //   dstEnd = dstAddr + (shape(dimCount-1)-1) * dstStride(dimCount-1) + shape(0) * elemBytes
  // 但实际上 stride(0) 才是 dim0 的行字节数信息字段（对 COPY 为 shape(0)*eb）。
  // 安全做法：对每维度做 (shape(d)-1)*stride(d) 的累加，再加 shape(0)*eb

  private val addrW = cfg.addressWidth
  private val wideW = addrW + 20
  private val BB = cfg.beatBytes
  private val log2BB = log2Ceil(BB)

  // 元素粒度由 srcFmt（读侧）/dstFmt（写侧）单一真源派生（rowBytes ceil，天然处理 INT4）
  val dim0Bytes = FbDataFmt.rowBytes(cmd.shape(0), cmd.srcFmt).pad(wideW)

  // --- PERMUTE 判定 ---
  val isPermute = cmd.op === FbOp.PERMUTE
  val isIm2col = cmd.op === FbOp.IM2COL
  val isScatterGather = cmd.op === FbOp.SCATTER || cmd.op === FbOp.GATHER

  // --- 源端 bounding box（支持 PERMUTE stride 重映射 + IM2COL + SCATTER/GATHER） ---
  // PERMUTE 时：srcStride 按 permVec 重映射索引；COPY/TRANSPOSE 时按原维度索引
  // IM2COL 时：srcEnd = srcAddr + C*H*W*elemBytes（由 shape(0) 传入 src 总字节数）
  // SCATTER/GATHER 时：srcEnd = srcAddr + shape(0)（软件填入源端总字节范围）
  val newSrcStart = cmd.srcAddr
  val newSrcEnd = {
    val contributions = (1 until cfg.maxDims).map { d =>
      val active = d.U(3.W) < cmd.dimCount
      val stride = Mux(isPermute, cmd.srcStride(cmd.permVec(d)), cmd.srcStride(d))
      val contrib = ((cmd.shape(d) - 1.U) * stride).pad(wideW)
      Mux(active, contrib, 0.U(wideW.W))
    }
    val outerSum = if (contributions.isEmpty) 0.U(wideW.W)
                   else contributions.reduce(_ +& _)
    val normalEnd = (cmd.srcAddr.pad(wideW) +& outerSum +& dim0Bytes)(addrW - 1, 0)
    // IM2COL：保守 bbox = srcAddr + shape(0)（软件填入 src 总字节数）
    val im2colSrcEnd = (cmd.srcAddr.pad(wideW) +& cmd.shape(0).pad(wideW))(addrW - 1, 0)
    // SCATTER/GATHER：保守 bbox = srcAddr + shape(0)（软件填入源端总字节范围）
    val sgSrcEnd = (cmd.srcAddr.pad(wideW) +& cmd.shape(0).pad(wideW))(addrW - 1, 0)
    Mux(isScatterGather, sgSrcEnd, Mux(isIm2col, im2colSrcEnd, normalEnd))
  }

  // --- 目的端 bounding box（支持 Zero-Padding + IM2COL + SCATTER/GATHER） ---
  // Zero-Padding 对 COPY 有效（任意 dimCount）；有 padding 时 dim0/dim1 用 outShape 替代 shape
  // IM2COL：dstEnd = dstAddr + shape(1)（软件填入 dst 总字节数）
  // SCATTER/GATHER：dstEnd = dstAddr + shape(1)（软件填入目的端总字节范围）
  val hasPad = cmd.padBefore(0) =/= 0.U || cmd.padAfter(0) =/= 0.U ||
               cmd.padBefore(1) =/= 0.U || cmd.padAfter(1) =/= 0.U
  val outShape0 = cmd.padBefore(0) +& cmd.shape(0) +& cmd.padAfter(0)
  val outShape1 = cmd.padBefore(1) +& cmd.shape(1) +& cmd.padAfter(1)

  val newDstStart = cmd.dstAddr

  /** dst 侧外层维度 stride 跨度累加：Σ_{d∈[1,maxDims), d<dimCount} (shapeD(d)-1) × dstStride(d)。
    * dst 数据区与 MX scale 区共用（scale 行地址同样按 dstStride 推进，见 FbEngine.sSWRITE）。
    * @param shapeD1 dim1 的有效 shape（有 padding 时 dst 数据区用 outShape1；scale 区与 padding 互斥，恒用 shape(1)） */
  private def dstOuterSpan(shapeD1: UInt): UInt = {
    val contributions = (1 until cfg.maxDims).map { d =>
      val active = d.U(3.W) < cmd.dimCount
      val shapeD = if (d == 1) shapeD1 else cmd.shape(d)
      val contrib = ((shapeD - 1.U) * cmd.dstStride(d)).pad(wideW)
      Mux(active, contrib, 0.U(wideW.W))
    }
    if (contributions.isEmpty) 0.U(wideW.W)
    else contributions.reduce(_ +& _)
  }

  val newDstEnd = {
    // dst 侧 bounding box 用 dstFmt 派生粒度（修复：此前误用 src eb，导致 cvt 命令 WAR/WAW 检测粒度错误）
    val dstDim0Bytes = Mux(hasPad,
      FbDataFmt.rowBytes(outShape0, cmd.dstFmt).pad(wideW),
      FbDataFmt.rowBytes(cmd.shape(0), cmd.dstFmt).pad(wideW))
    val outerSum = dstOuterSpan(Mux(hasPad, outShape1, cmd.shape(1)))
    val normalEnd = (cmd.dstAddr.pad(wideW) +& outerSum +& dstDim0Bytes)(addrW - 1, 0)
    // IM2COL：保守 bbox = dstAddr + shape(1)（软件填入 dst 总字节数）
    val im2colDstEnd = (cmd.dstAddr.pad(wideW) +& cmd.shape(1).pad(wideW))(addrW - 1, 0)
    // SCATTER/GATHER：保守 bbox = dstAddr + shape(1)（软件填入目的端总字节范围）
    val sgDstEnd = (cmd.dstAddr.pad(wideW) +& cmd.shape(1).pad(wideW))(addrW - 1, 0)
    Mux(isScatterGather, sgDstEnd, Mux(isIm2col, im2colDstEnd, normalEnd))
  }

  // --- MX 量化 scale 侧信道写 bounding box（第三区间） ---
  // 量化激活由 dstFmt 派生（与 FbEngine.mxQuantActive 同一判据），且仅在 enableConversion 构建中存在。
  // 非激活时区间置空（start=end=0），rangesOverlap 恒为 false，不参与检测——避免 scaleAddr 陈旧值误报。
  //
  // 地址范围推导（依据 FbEngine.sSWRITE）：
  //   写地址 = scaleAddr + Σ_{d∈[1,dimCount)}(outerIdx(d) × dstStride(d)) + alignDown(byteInRow, BB)
  //   其中 outerIdx(d) ∈ [0, shape(d)-1]，byteInRow ∈ [0, ceil(shape(0)/blockSize))，
  //   每次为 len=0 的单拍突发，以 strb 部分写覆盖 [写地址, 写地址+BB)。
  //   ⇒ 下界 = scaleAddr（outerIdx 全 0、byteInRow=0）
  //   ⇒ 上界 = scaleAddr + Σ(shape(d)-1)×dstStride(d) + alignDown(sBytes-1, BB) + BB
  //           = scaleAddr + Σ(shape(d)-1)×dstStride(d) + roundUpToBeat(sBytes)
  //   注意 scale 区按行 stride 分布、并非自 scaleAddr 起的连续块，故上界含 dstStride 跨度。
  val mxActive = if (cfg.enableConversion) FbDataFmt.isMx(cmd.dstFmt) else false.B
  val (newScaleStart, newScaleEnd) = if (cfg.enableConversion) {
    // blockSize 解码（0→16、1→32、2→64）；非法编码（>2）由引擎 mxQuantBad 报 err，
    // 此处 default 取 16（而非 0）以保证除法/移位不产生除零与无谓逻辑
    val mxBlockSizeVal = MuxLookup(cmd.scaleBlockSize, 16.U(7.W))(Seq(
      0.U -> 16.U, 1.U -> 32.U, 2.U -> 64.U))
    val mxBlockShift = MuxLookup(cmd.scaleBlockSize, 4.U(3.W))(Seq(
      0.U -> 4.U, 1.U -> 5.U, 2.U -> 6.U))
    // 每行 scale 字节数 = ceil(shape(0)/blockSize)（合法命令保证整除，ceil 仅作保守上界）
    val mxScaleRowBytes = (cmd.shape(0).pad(wideW) +& (mxBlockSizeVal - 1.U).pad(wideW)) >> mxBlockShift
    // 行末残余以 strb 部分写覆盖整个 beat ⇒ 按 beat 向上取整
    val mxScaleRowBytesAligned = ((mxScaleRowBytes +& (BB - 1).U(wideW.W)) >> log2BB) << log2BB
    (Mux(mxActive, cmd.scaleAddr, 0.U(addrW.W)),
     Mux(mxActive,
       (cmd.scaleAddr.pad(wideW) +& dstOuterSpan(cmd.shape(1)) +& mxScaleRowBytesAligned)(addrW - 1, 0),
       0.U(addrW.W)))
  } else {
    // copy-only 构建无量化器 ⇒ 不产生 scale 区间逻辑
    (0.U(addrW.W), 0.U(addrW.W))
  }

  // --- 冲突检测：新命令与每个 in-flight 通道 ---
  def rangesOverlap(aStart: UInt, aEnd: UInt, bStart: UInt, bEnd: UInt): Bool = {
    // 有交集当且仅当 !(aEnd <= bStart || bEnd <= aStart)
    !(aEnd <= bStart || bEnd <= aStart)
  }

  val hasConflict = Wire(Vec(cfg.numPorts, Bool()))
  for (p <- 0 until cfg.numPorts) {
    // scale 区间仅在派发时确为量化命令（infScaleValid）且该通道 in-flight 时参与检测
    val infScaleLive = infValid(p) && infScaleValid(p)
    // RAW：新命令读区 vs in-flight 写区（dst + scale 侧信道写）
    val raw = rangesOverlap(newSrcStart, newSrcEnd, infDstStart(p), infDstEnd(p)) ||
      (infScaleLive && rangesOverlap(newSrcStart, newSrcEnd, infScaleStart(p), infScaleEnd(p)))
    // WAR：新命令写区（dst + scale）vs in-flight 读区
    val war = rangesOverlap(newDstStart, newDstEnd, infSrcStart(p), infSrcEnd(p)) ||
      (mxActive && rangesOverlap(newScaleStart, newScaleEnd, infSrcStart(p), infSrcEnd(p)))
    // WAW：新命令写区 × in-flight 写区，四种组合全查（dst×dst、scale×dst、dst×scale、scale×scale）
    val waw = rangesOverlap(newDstStart, newDstEnd, infDstStart(p), infDstEnd(p)) ||
      (mxActive && rangesOverlap(newScaleStart, newScaleEnd, infDstStart(p), infDstEnd(p))) ||
      (infScaleLive && rangesOverlap(newDstStart, newDstEnd, infScaleStart(p), infScaleEnd(p))) ||
      (mxActive && infScaleLive &&
        rangesOverlap(newScaleStart, newScaleEnd, infScaleStart(p), infScaleEnd(p)))
    hasConflict(p) := infValid(p) && (raw || war || waw)
  }
  val anyConflict = hasConflict.asUInt.orR

  // --- 通道能力匹配 + 空闲检测 ---
  val isTranspose = cmd.op === FbOp.TRANSPOSE
  val isPermuteOp = cmd.op === FbOp.PERMUTE
  val channelCapable = Wire(Vec(cfg.numPorts, Bool()))
  val channelEligible = Wire(Vec(cfg.numPorts, Bool()))
  val channelIdle = Wire(Vec(cfg.numPorts, Bool()))
  for (p <- 0 until cfg.numPorts) {
    val canTranspose = cfg.resolvedChannelTranspose(p).B
    val canPermute = cfg.resolvedChannelPermute(p).B
    val canIm2col = cfg.resolvedChannelIm2col(p).B
    val canSG = cfg.resolvedChannelScatterGather(p).B
    val capable = (!isTranspose || canTranspose) && (!isPermuteOp || canPermute) && (!isIm2col || canIm2col) && (!isScatterGather || canSG)
    val idle = !io.chBusy(p) && !infValid(p)
    channelCapable(p) := capable
    channelIdle(p) := idle
    channelEligible(p) := capable && idle
  }

  // 当没有通道具备所需能力时，退化派发到任意空闲通道（引擎将返回 err）
  val noCapableChannel = !channelCapable.asUInt.orR
  val effectiveEligible = Mux(noCapableChannel, channelIdle.asUInt, channelEligible.asUInt)

  // 优先级编码：选编号最小的空闲且能力匹配通道
  val anyEligible = effectiveEligible.orR
  val selectedChannel = PriorityEncoder(effectiveEligible)

  // --- 派发逻辑 ---
  val canDispatch = cmdValid && !anyConflict && anyEligible

  // 默认：所有 pop 口无效
  for (p <- 0 until cfg.numPorts) {
    io.pop(p).valid := false.B
    io.pop(p).payload := cmd
  }
  fifo.io.pop.ready := false.B

  when(canDispatch) {
    // 选中通道 valid 拉高
    for (p <- 0 until cfg.numPorts) {
      io.pop(p).valid := selectedChannel === p.U
    }
    // fifo pop ready 取决于选中通道是否 fire
    fifo.io.pop.ready := io.pop(selectedChannel).ready

    // 派发成功时锁存地址范围
    when(fifo.io.pop.fire) {
      infSrcStart(selectedChannel) := newSrcStart
      infSrcEnd(selectedChannel)   := newSrcEnd
      infDstStart(selectedChannel) := newDstStart
      infDstEnd(selectedChannel)   := newDstEnd
      // MX scale 侧信道写区间：仅量化激活命令置 infScaleValid，
      // 非 MX 命令清零以免其陈旧 scaleAddr 被后续命令误判为冲突
      infScaleStart(selectedChannel) := newScaleStart
      infScaleEnd(selectedChannel)   := newScaleEnd
      infScaleValid(selectedChannel) := mxActive
      infValid(selectedChannel)    := true.B
    }
  }
}
