/** ForagerBee DMA 配置、操作码、数据格式、命令与完成回执。
  * 数据地址为逻辑字节地址，支持非 beat 对齐搬运与精确尾部写掩码。
  */

import chisel3._
import chisel3.util._

/** ForagerBee 顶层配置。
  *
  * @param numPorts          DMA 通道（Stream master）个数（1..4），每通道一个执行引擎
  * @param queueDepth        命令队列深度（≥1）
  * @param dataQueueDepth    数据 Queue 深度（≤0 时自动取 max(maxBurstLen, 4)）
  * @param maxDims           支持的最大维度数（1..5）
  * @param tileSize          转置缓冲 tile 尺寸（tileSize × tileSize 元素槽位，≥2）
  * @param maxBurstLen       单突发最大 beat 数（1..256，受 len 8bit 限制）
  * @param dataWidth         数据位宽（bit），必须为 ≥32 的 2 次幂
  * @param addressWidth      地址位宽（bit），必须 ≥ 12
  * @param channelTranspose  每通道是否支持转置（长度 = numPorts，默认全 true）
  * @param channelPermute    每通道是否支持 PERMUTE（长度 = numPorts，默认全 true）
  * @param enableSharedWritePorts 写端口共享仲裁开关（默认 false，1:1 直连）
  * @param writeOutstandingDepth 最大在途写突发数（≥1；1 为阻塞式，>1 为非阻塞式）
  */
case class ForagerBeeConfig(
    numPorts: Int = 2,
    queueDepth: Int = 8,
    dataQueueDepth: Int = -1,
    maxDims: Int = 5,
    tileSize: Int = 8,
    maxBurstLen: Int = 64,
    dataWidth: Int = 128,
    addressWidth: Int = 40,
    channelTranspose: Seq[Boolean] = Seq.empty,
    channelPermute: Seq[Boolean] = Seq.empty,
    enableConversion: Boolean = false,    // 编译时开关：是否启用格式转换
    converterPipeStages: Int = 1,         // 兼容参数：当前转换器未使用它配置流水级数
    channelIm2col: Seq[Boolean] = Seq.empty,  // 每通道是否支持 im2col（默认全 true）
    channelScatterGather: Seq[Boolean] = Seq.empty,  // 每通道是否支持 scatter/gather
    enableSharedWritePorts: Boolean = false,  // 编译时开关：写端口共享仲裁（默认关闭，保持每引擎 1:1 直连）
    writeOutstandingDepth: Int = 1        // 最大在途写突发数：1 为阻塞式，>1 为非阻塞式
) {
  /** 实际数据 Queue 深度（≤0 时自动取 max(maxBurstLen, 4)） */
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
  require(dataWidth >= 32 && isPow2(dataWidth), s"ForagerBeeConfig: dataWidth($dataWidth) 必须为 ≥32 的 2 次幂")
  require(addressWidth >= 12, s"ForagerBeeConfig: addressWidth($addressWidth) 必须 ≥ 12")
  require(resolvedChannelTranspose.length == numPorts,
    s"ForagerBeeConfig: channelTranspose 长度(${resolvedChannelTranspose.length}) 必须等于 numPorts($numPorts)")
  require(resolvedChannelPermute.length == numPorts,
    s"ForagerBeeConfig: channelPermute 长度(${resolvedChannelPermute.length}) 必须等于 numPorts($numPorts)")
  require(resolvedChannelIm2col.length == numPorts,
    s"ForagerBeeConfig: channelIm2col 长度(${resolvedChannelIm2col.length}) 必须等于 numPorts($numPorts)")
  require(resolvedChannelScatterGather.length == numPorts,
    s"ForagerBeeConfig: channelScatterGather 长度(${resolvedChannelScatterGather.length}) 必须等于 numPorts($numPorts)")
  require(writeOutstandingDepth >= 1,
    s"ForagerBeeConfig: writeOutstandingDepth($writeOutstandingDepth) 必须 ≥ 1（=1 保持现有阻塞式写路径）")

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

/** 数据格式枚举（4-bit）；软件可见编码固定，6/7/11–15 保留。
  * 支持同格式搬运、浮点格式转换与浮点到 MX 量化，不支持整数转换算术。
  */
object FbDataFmt extends ChiselEnum {

  /** IEEE-754 单精度（1+8+23，bias=127，4 字节/元素） */
  val FP32 = Value(0.U(4.W))

  /** IEEE-754 半精度（1+5+10，bias=15，2 字节/元素） */
  val FP16 = Value(1.U(4.W))

  /** bfloat16（1+8+7，bias=127，2 字节/元素） */
  val BF16 = Value(2.U(4.W))

  /** 有符号 8-bit 整数（1 字节/元素；仅支持同格式搬运） */
  val INT8 = Value(3.U(4.W))

  /** MXFP8 E4M3（{sign(1),exp(4),mant(3)}，bias=7，E8M0 共享 scale，1 字节/元素） */
  val MXE4M3 = Value(4.U(4.W))

  /** MXFP8 E5M2（{sign(1),exp(5),mant(2)}，bias=15，E8M0 共享 scale，1 字节/元素） */
  val MXE5M2 = Value(5.U(4.W))

  // 编码 6：历史空位，保留不复用（避免与既有软件/波形约定冲突）
  // 编码 7：历史 NONE（bypass 哨兵），已删除，永久保留不复用

  /** 有符号 32-bit 整数（4 字节/元素；仅支持同格式搬运） */
  val INT32 = Value(8.U(4.W))

  /** 有符号 16-bit 整数（2 字节/元素；仅支持同格式搬运） */
  val INT16 = Value(9.U(4.W))

  /** 有符号 4-bit 整数（半字节/元素；仅支持同格式 COPY） */
  val INT4 = Value(10.U(4.W))

  /** 编码契约自检（elaboration 期）：任何重排/插入/位宽漂移都在生成 RTL 之前抛异常，
    * 杜绝「硬件悄悄改了软件可见编码」这类难以定位的兼容性事故。 */
  require(
    FP32.litValue == 0 && FP16.litValue == 1 && BF16.litValue == 2 && INT8.litValue == 3 &&
      MXE4M3.litValue == 4 && MXE5M2.litValue == 5 &&
      INT32.litValue == 8 && INT16.litValue == 9 && INT4.litValue == 10 &&
      all.size <= 16 && getWidth == 4,
    s"FbDataFmt: 编码契约被破坏（须保持 4-bit 宽度与既有编码 0/1/2/3/4/5），当前 = $this")

  /** 是否浮点族（FP32 / FP16 / BF16） */
  def isFloat(f: FbDataFmt.Type): Bool = f === FP32 || f === FP16 || f === BF16

  /** 是否整数族（INT8 / INT16 / INT32 / INT4） */
  def isInt(f: FbDataFmt.Type): Bool = f === INT8 || f === INT16 || f === INT32 || f === INT4

  /** 是否 MX 族（MXE4M3 / MXE5M2，仅量化器认识） */
  def isMx(f: FbDataFmt.Type): Bool = f === MXE4M3 || f === MXE5M2

  /** 元素位宽（bit）的【单一真源】：格式编码 → 位宽的运行期硬件解码。
    * 引擎（读写侧 beat/压缩扩展比推导）与格式转换器（Rate Adapter）共用本表，
    * 杜绝各模块私自定义 FMT_* 常量与位宽表造成的双源漂移。
    * 未定义编码 → 32（保守默认，硬件上由 fmtEncBad 校验拒绝）。 */
  def elemBits(f: FbDataFmt.Type): UInt = MuxLookup(f, 32.U(6.W))(Seq(
    FP32 -> 32.U(6.W), INT32 -> 32.U(6.W),
    FP16 -> 16.U(6.W), BF16 -> 16.U(6.W), INT16 -> 16.U(6.W),
    INT8 -> 8.U(6.W), MXE4M3 -> 8.U(6.W), MXE5M2 -> 8.U(6.W),
    INT4 -> 4.U(6.W)
  ))

  /** 编码是否合法（拒绝 6/7/11-15 等保留或未定义值）。
    * NONE 删除后 all 不含编码 7，合法集合 = {0,1,2,3,4,5,8,9,10}。 */
  def isLegal(f: FbDataFmt.Type): Bool = {
    val legal = Seq(FP32, FP16, BF16, INT8, MXE4M3, MXE5M2, INT32, INT16, INT4)
    VecInit(legal.map(f === _)).asUInt.orR
  }

  /** 是否亚字节格式（INT4：4-bit 元素，需特殊对齐处理） */
  def isSubByte(f: FbDataFmt.Type): Bool = f === INT4

  /** 元素字节数（向上取整）：INT4→1，其余 elemBits>>3。3-bit 宽度足够（最大 4）。 */
  def bytesOfCeil(f: FbDataFmt.Type): UInt = {
    Mux(f === INT4, 1.U(3.W), (elemBits(f) >> 3)(2, 0))
  }

  /** elemBytesLog2 派生：INT8/MXE4M3/MXE5M2→0，FP16/BF16/INT16→1，FP32/INT32→2。
    * 仅字节粒度格式有意义，INT4 由校验挡在调用路径外。 */
  def ebLog2Of(f: FbDataFmt.Type): UInt = MuxLookup(f, 0.U(2.W))(Seq(
    FP16 -> 1.U(2.W), BF16 -> 1.U(2.W), INT16 -> 1.U(2.W),
    FP32 -> 2.U(2.W), INT32 -> 2.U(2.W)
  ))

  /** 行字节数（ceil）：shape0 × elemBits / 8，天然处理 INT4 亚字节。
    * 返回位宽 = shape0.getWidth + 6（足以容纳最大 elemBits=32 的乘积 + 7 的 ceil 余量）。 */
  def rowBytes(shape0: UInt, f: FbDataFmt.Type): UInt = {
    val w = shape0.getWidth + 6
    ((shape0 * elemBits(f)).pad(w) +& 7.U) >> 3
  }
}


/** DMA 命令描述符（统一 dims 组，1D/2D/ND 共用一套字段）。
  *
  * @param cfg ForagerBee 配置
  */
class FbCmd(cfg: ForagerBeeConfig) extends Bundle {

  /** DMA 操作码，见 [[FbOp]] */
  val op = FbOp()

  /** 软件自定义标签，原样随 done 回执返回 */
  val tag = UInt(8.W)

  /** 有效维度数（1..maxDims；TRANSPOSE 固定为 2；0 视为非法命令） */
  val dimCount = UInt(3.W)

  /** 每维元素数（dim0 为最内维；未用维度填 0） */
  val shape = Vec(cfg.maxDims, UInt(16.W)) //max 5*16

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

  /** 源逻辑字节基址（无需 beat 对齐） */
  val srcAddr = UInt(cfg.addressWidth.W)

  /** 目的逻辑字节基址（无需 beat 对齐） */
  val dstAddr = UInt(cfg.addressWidth.W)

  /** PERMUTE 维度映射向量：输出第 d 维对应源张量的第 permVec(d) 维。
    * 非 PERMUTE 操作时忽略（默认全 0）。每项 3bit 足以索引最多 5 维。 */
  val permVec = Vec(cfg.maxDims, UInt(3.W))

  /** ND COPY：dim0/dim1 前置填充元素数（未填充时为 0） */
  val padBefore = Vec(2, UInt(16.W))

  /** ND COPY：dim0/dim1 后置填充元素数（未填充时为 0） */
  val padAfter = Vec(2, UInt(16.W))

  /** 填充值的目标格式原始位模式，按目标元素宽度取低位重复 */
  val padValue = UInt(32.W)

  /** 格式转换：源数据格式（[[FbDataFmt]] ChiselEnum，4-bit） */
  val srcFmt = FbDataFmt()

  /** 格式转换：目标数据格式（[[FbDataFmt]] ChiselEnum，4-bit） */
  val dstFmt = FbDataFmt()

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

  /** MX 量化：scale 写出基址（须按 beat 对齐；非 MX 量化时忽略） */
  val scaleAddr = UInt(cfg.addressWidth.W)

  /** MX 量化：块大小编码（0→16、1→32、2→64 个元素共享一个 E8M0 scale） */
  val scaleBlockSize = UInt(2.W)

}

object FbCmd {
  def apply(cfg: ForagerBeeConfig): FbCmd = new FbCmd(cfg)
}


/** 共享（压缩）描述符载体：3 个 32-bit 双字，用于多通道共享同一份命令编码的场景。
  *
  * 注意：历史上本类漏写 `extends Bundle`，成员 `UInt(32.W)` 因此只是普通 Scala 求值、
  * 并非硬件类型，一旦被实例化即抛 Builder 异常（且拼写为 FbShareedCmd）。此处修正为
  * 正规 Bundle 并订正拼写；当前无模块引用，保留作为后续共享描述符通路的占位定义。
  */
class FbSharedCmd extends Bundle {
  val DW0 = UInt(32.W)
  val DW1 = UInt(32.W)
  val DW2 = UInt(32.W)
}

object FbSharedCmd {
  def apply(): FbSharedCmd = new FbSharedCmd
}

/** DMA 完成回执。
  *
  * @param cfg ForagerBee 配置
  */
class FbDone(cfg: ForagerBeeConfig) extends Bundle {

  /** 命令标签（原样返回） */
  val tag = UInt(8.W)

  /** 命令校验或执行错误标志 */
  val err = Bool()

}

object FbDone {
  def apply(cfg: ForagerBeeConfig): FbDone = new FbDone(cfg)
}
