/** ForagerBee（采集蜂）—— NPU 专用 DMA 搬运模块的配置与命令描述符定义。
  *
  * 蜂巢主题命名：采集蜂（ForagerBee）负责为 HiveWorker 脉动阵列（蜂巢）
  * 搬运张量数据，支持多维 stride 拷贝（COPY）与 2D 分块转置（TRANSPOSE）。
  *
  * 本文件定义：
  *   - [[ForagerBeeConfig]]：顶层参数（DMA 通道数/队列深度/维度上限/
  *     转置 tile 尺寸/突发上限/数据位宽/地址位宽/通道转置能力/
  *     链式描述符开关/格式转换开关），全部 compile-time 可配置并 require 校验。
  *   - [[FbOp]]：操作码枚举（COPY / TRANSPOSE / PERMUTE）。
  *   - [[FbDataFmt]]：数据格式枚举（FP32 / FP16 / BF16 / INT8 / NONE）。
  *   - [[FbCmd]]：命令描述符（统一 dims 组：shape + srcStride + dstStride + 链式 + 格式转换）。
  *   - [[FbDone]]：完成回执（tag + err + chainLen）。
  *
  * 命令语义（统一 ND 描述）：
  *   - dimCount 个维度，dim0 为最内维（连续维）；shape(i) 为第 i 维元素数。
  *   - srcStride/dstStride(i) 为字节步长；stride(0) 语义为「最内维行连续字节数」
  *     （COPY 时 dim0 被展开为连续 INCR 突发，stride(0) 仅作信息字段；
  *     TRANSPOSE 时 dstStride(0) 用作转置输出行之间的字节步长）。
  *   - 当前行地址 = base + Σ(idx(i) × stride(i))，i ∈ [1, dimCount)。
  *   - elemBytesLog2：元素粒度 0→1B、1→2B、2→4B（v1 仅支持 1/2/4 字节元素）。
  *
  * v1 对齐约束（软件必须保证，硬件对基址与 COPY 行长做运行时检查）：
  *   - 所有行起始地址（srcAddr/dstAddr 基址）按 beat（dataWidth/8 字节）对齐；
  *   - COPY：最内维行字节数 shape(0)×elemBytes 必须为整 beat 倍数；
  *   - TRANSPOSE：源行允许子 beat（自动按 ceil 取整读取并做元素级提取），
  *     转置输出按字节掩码（wstrb）写入，输出行内偏移由硬件自动处理。
  */

import chisel3._
import chisel3.util._

/** ForagerBee 顶层配置。
  *
  * @param numPorts          DMA 通道（Stream master）个数（1..4），每通道一个执行引擎
  * @param queueDepth        命令队列深度（≥1）
  * @param dataQueueDepth    引擎内数据旁路 Queue 深度（≥1，默认 = maxBurstLen；
  *                          当该值 < maxBurstLen 时，突发会被拆分以保证不溢出 Queue）
  * @param maxDims           支持的最大维度数（1..5）
  * @param tileSize          转置缓冲 tile 尺寸（tileSize × tileSize 元素槽位，≥2）
  * @param maxBurstLen       单突发最大 beat 数（1..256，受 len 8bit 限制）
  * @param dataWidth         数据位宽（bit），必须 ≥ 32
  * @param addressWidth      地址位宽（bit），必须 ≥ 12
  * @param channelTranspose  每通道是否支持转置（长度 = numPorts，默认全 true 向下兼容）
  * @param channelPermute   每通道是否支持 PERMUTE（长度 = numPorts，默认全 true 向下兼容）
  */
case class ForagerBeeConfig(
    numPorts: Int = 2,
    queueDepth: Int = 8,
    dataQueueDepth: Int = -1,
    maxDims: Int = 5,
    tileSize: Int = 8,
    maxBurstLen: Int = 64,
    dataWidth: Int = 128,
    addressWidth: Int = 32,
    channelTranspose: Seq[Boolean] = Seq.empty,
    channelPermute: Seq[Boolean] = Seq.empty,
    enableChaining: Boolean = false,      // 编译时开关：是否启用链式描述符
    chainDescBeats: Int = 0,              // 描述符内存 beat 数（0=自动计算）
    enableConversion: Boolean = false,    // 编译时开关：是否启用格式转换
    converterPipeStages: Int = 1,         // 转换流水线深度（1=组合，2+=流水）
    channelIm2col: Seq[Boolean] = Seq.empty,  // 每通道是否支持 im2col（默认全 true）
    channelScatterGather: Seq[Boolean] = Seq.empty  // 每通道是否支持 scatter/gather
) {
  /** 实际数据 Queue 深度（-1 表示自动 = max(maxBurstLen, 4)） */
  val resolvedDataQueueDepth: Int =
    if (dataQueueDepth <= 0) math.max(maxBurstLen, 4) else dataQueueDepth
  /** 实际的通道转置能力序列（空时默认全 true） */
  val resolvedChannelTranspose: Seq[Boolean] =
    if (channelTranspose.isEmpty) Seq.fill(numPorts)(true)
    else channelTranspose
  /** 实际的通道 PERMUTE 能力序列（空时默认全 true） */
  val resolvedChannelPermute: Seq[Boolean] =
    if (channelPermute.isEmpty) Seq.fill(numPorts)(true)
    else channelPermute
  /** 实际的通道 IM2COL 能力序列（空时默认全 true） */
  val resolvedChannelIm2col: Seq[Boolean] =
    if (channelIm2col.isEmpty) Seq.fill(numPorts)(true)
    else channelIm2col
  /** 实际的通道 SCATTER/GATHER 能力序列（空时默认全 true） */
  val resolvedChannelScatterGather: Seq[Boolean] =
    if (channelScatterGather.isEmpty) Seq.fill(numPorts)(true)
    else channelScatterGather

  require(numPorts >= 1 && numPorts <= 4, s"ForagerBeeConfig: numPorts($numPorts) 必须 ∈ [1,4]")
  require(queueDepth >= 1, s"ForagerBeeConfig: queueDepth($queueDepth) 必须 ≥ 1")
  require(resolvedDataQueueDepth >= 1, s"ForagerBeeConfig: dataQueueDepth($dataQueueDepth → $resolvedDataQueueDepth) 必须 ≥ 1")
  require(maxDims >= 1 && maxDims <= 5, s"ForagerBeeConfig: maxDims($maxDims) 必须 ∈ [1,5]")
  require(tileSize >= 2, s"ForagerBeeConfig: tileSize($tileSize) 必须 ≥ 2")
  require(maxBurstLen >= 1 && maxBurstLen <= 256, s"ForagerBeeConfig: maxBurstLen($maxBurstLen) 必须 ∈ [1,256]")
  require(dataWidth >= 32, s"ForagerBeeConfig: dataWidth($dataWidth) 必须 ≥ 32（元素槽位最大 32bit）")
  require(addressWidth >= 12, s"ForagerBeeConfig: addressWidth($addressWidth) 必须 ≥ 12（4KB 突发边界处理）")
  require(resolvedChannelTranspose.length == numPorts,
    s"ForagerBeeConfig: channelTranspose 长度(${resolvedChannelTranspose.length}) 必须等于 numPorts($numPorts)")
  require(resolvedChannelPermute.length == numPorts,
    s"ForagerBeeConfig: channelPermute 长度(${resolvedChannelPermute.length}) 必须等于 numPorts($numPorts)")
  require(resolvedChannelIm2col.length == numPorts,
    s"ForagerBeeConfig: channelIm2col 长度(${resolvedChannelIm2col.length}) 必须等于 numPorts($numPorts)")
  require(resolvedChannelScatterGather.length == numPorts,
    s"ForagerBeeConfig: channelScatterGather 长度(${resolvedChannelScatterGather.length}) 必须等于 numPorts($numPorts)")

  /** 每 beat 字节数 */
  def beatBytes: Int = dataWidth / 8

  /** portSel 位宽（DMA 口选择） */
  def portSelWidth: Int = math.max(1, log2Up(numPorts))
}

/** DMA 操作码 */
object FbOp extends ChiselEnum {

  /** 多维 stride 拷贝（dimCount ∈ [1, maxDims]，dim0 连续展开为 INCR 突发） */
  val COPY = Value

  /** 2D 分块转置（限定 dimCount=2：读侧按 (dim0 行序, dim1) 遍历，写侧转置后按 dim1 为连续维写出） */
  val TRANSPOSE = Value

  /** 多维维度重排（dimCount ∈ [1, maxDims]，dim0 仍为连续突发维；
    通过 permVec 重映射源 stride 索引实现任意维度重排） */
  val PERMUTE = Value

  /** im2col 卷积展开（将 feature map 展开为 GEMM 输入矩阵） */
  val IM2COL = Value

  /** 分发：从线性源读取，写到多个非连续目的地址（由描述符表指定） */
  val SCATTER = Value

  /** 聚集：从多个非连续源地址（由描述符表指定）读取，线性写入连续目的缓冲区 */
  val GATHER = Value

  /** Tile-to-Linear：将 2D 行主序矩阵按 tile 分块重排为线性输出（展开为 4D COPY） */
  val TILE2LINEAR = Value

  /** Linear-to-Tile：TILE2LINEAR 的逆操作，将线性连续缓冲按 tile 布局写回 2D 行主序矩阵
    （展开为 4D COPY，与 TILE2LINEAR 数学对称，仅 src/dst stride 互换） */
  val LINEAR2TILE = Value
}

/** 数据格式枚举（用于搬运时原位格式转换） */
object FbDataFmt {
  val FP32 = 0.U(3.W)
  val FP16 = 1.U(3.W)
  val BF16 = 2.U(3.W)
  val INT8 = 3.U(3.W)
  val NONE = 7.U(3.W) // 不转换（bypass）
}

/** DMA 命令描述符（统一 dims 组，1D/2D/ND 共用一套字段）。
  *
  * @param cfg ForagerBee 配置
  */
class FbCmd(cfg: ForagerBeeConfig) extends Bundle {

  /** 操作码（COPY / TRANSPOSE） */
  val op = FbOp()

  /** 软件自定义标签，原样随 done 回执返回 */
  val tag = UInt(8.W)

  /** 有效维度数（1..maxDims；TRANSPOSE 固定为 2；0 视为非法命令） */
  val dimCount = UInt(3.W)

  /** 每维元素数（dim0 为最内维；未用维度填 0） */
  val shape = Vec(cfg.maxDims, UInt(16.W))

  /** 源字节步长（stride(0) = 最内维行连续字节数，信息字段） */
  val srcStride = Vec(cfg.maxDims, UInt(cfg.addressWidth.W))

  /** 目的字节步长（COPY：同 srcStride 语义；TRANSPOSE：dstStride(0) = 转置输出行步长） */
  val dstStride = Vec(cfg.maxDims, UInt(cfg.addressWidth.W))

  /** 源张量各维起始索引（子块裁剪：从源张量的 srcStartIdx 位置开始读取）。
    * 默认全 0（等同于从原点开始），非裁剪场景不需要设置。 */
  val srcStartIdx = Vec(cfg.maxDims, UInt(16.W))

  /** TILE2LINEAR：源矩阵列数 N（非 TILE2LINEAR 时忽略） */
  val t2lMatCols = UInt(16.W)

  /** TILE2LINEAR：tile 行数 Tm（非 TILE2LINEAR 时忽略） */
  val t2lTileRows = UInt(16.W)

  /** TILE2LINEAR：tile 列数 Tn（非 TILE2LINEAR 时忽略） */
  val t2lTileCols = UInt(16.W)

  /** TILE2LINEAR：行方向 tile 数 Mt = ceil(M/Tm)（非 TILE2LINEAR 时忽略） */
  val t2lNumTileRows = UInt(16.W)

  /** TILE2LINEAR：列方向 tile 数 Nt = ceil(N/Tn)（非 TILE2LINEAR 时忽略） */
  val t2lNumTileCols = UInt(16.W)

  /** 源基址（须按 beat 对齐） */
  val srcAddr = UInt(cfg.addressWidth.W)

  /** 目的基址（须按 beat 对齐） */
  val dstAddr = UInt(cfg.addressWidth.W)

  /** 元素粒度 log2：0→1B、1→2B、2→4B（>2 非法） */
  val elemBytesLog2 = UInt(2.W)

  /** PERMUTE 维度映射向量：输出第 d 维对应源张量的第 permVec(d) 维。
    * 非 PERMUTE 操作时忽略（默认全 0）。每项 3bit 足以索引最多 5 维。 */
  val permVec = Vec(cfg.maxDims, UInt(3.W))

  /** COPY Zero-Padding：dim0/dim1 前置填充元素数（仅 COPY + dimCount≤2 有效）。
    * 非 padding 操作时填 0。 */
  val padBefore = Vec(2, UInt(16.W))

  /** COPY Zero-Padding：dim0/dim1 后置填充元素数（仅 COPY + dimCount≤2 有效）。
    * 非 padding 操作时填 0。 */
  val padAfter = Vec(2, UInt(16.W))

  /** 链式描述符：下一描述符内存地址（0 表示链终止） */
  val nextDescAddr = UInt(cfg.addressWidth.W)

  /** 链式描述符：是否为链模式（true 时 done 由 ChainController 管理） */
  val chainMode = Bool()

  /** 格式转换：是否启用搬运时原位格式转换 */
  val cvtEnable = Bool()

  /** 格式转换：源数据格式（FbDataFmt 枚举） */
  val srcFmt = UInt(3.W)

  /** 格式转换：目标数据格式（FbDataFmt 枚举） */
  val dstFmt = UInt(3.W)

  /** 格式转换：量化 scale（IEEE-754 FP32 编码） */
  val cvtScale = UInt(32.W)

  /** 格式转换：量化 zero_point（IEEE-754 FP32 编码） */
  val cvtZeroPoint = UInt(32.W)

  /** im2col：卷积核尺寸 (kH, kW)，非 IM2COL 时忽略 */
  val im2colKernel = Vec(2, UInt(8.W))

  /** im2col：卷积步长 (sH, sW) */
  val im2colStride = Vec(2, UInt(8.W))

  /** im2col：输入 padding (pH, pW)，上下/左右对称 */
  val im2colPad = Vec(2, UInt(8.W))

  /** im2col：空洞卷积膨胀率 (dH, dW) */
  val im2colDilation = Vec(2, UInt(8.W))

  /** im2col：输入 feature map 形状 (C, H, W) */
  val im2colInShape = Vec(3, UInt(16.W))

  /** scatter/gather：地址描述符表的内存基址（须按 beat 对齐） */
  val sgListAddr = UInt(cfg.addressWidth.W)

  /** scatter/gather：描述符表条目数（1..65535） */
  val sgEntryCount = UInt(16.W)
}

object FbCmd {
  def apply(cfg: ForagerBeeConfig): FbCmd = new FbCmd(cfg)
}

/** DMA 完成回执。
  *
  * @param cfg ForagerBee 配置
  */
class FbDone(cfg: ForagerBeeConfig) extends Bundle {

  /** 命令标签（原样返回） */
  val tag = UInt(8.W)

  /** 错误标志（非法命令：dimCount=0/超界、TRANSPOSE 维度≠2、非对齐、元素粒度非法等） */
  val err = Bool()

  /** 链已完成的描述符计数（非链模式时为 1） */
  val chainLen = UInt(16.W)
}

object FbDone {
  def apply(cfg: ForagerBeeConfig): FbDone = new FbDone(cfg)
}
