/** WorkerBee 控制模块配置与命令/状态 Bundle 定义。
  *
  * WorkerBee 是脉动阵列加速器的顶层控制单元：REG_WRITE 写配置寄存器、EXECUTE 触发自动
  * tiling GEMM、REG_READ 读状态/配置寄存器。内部 FSM 自动完成 M×K @ K×N → M×N，
  * 按阵列能力 (totalN×totalN) 自动切块 tiling。
  */

import chisel3._
import chisel3.util._

/** WorkerBee 编译期配置。
  *
  * @param arrayN         单 cluster 内 PE 列数（≥4）
  * @param clusterM       cluster 数量（≥1）
  * @param aW             A 矩阵元素位宽
  * @param bW             B 矩阵元素位宽
  * @param cW             C 累加器位宽（0 = 自动推导）
  * @param supportedFmts  支持的数据格式集合
  * @param aBufferDepth   A 缓冲深度
  * @param bBufferDepth   B（权重）缓冲深度（独立 B buffer，宽度 totalN*bW，
  *                       由 RdOnly DMA 自主扫描整个 B 矩阵写入，Executor 按行 pop）
  * @param cBufferDepth   C 缓冲深度（仅存结果 + Partial sum）。须 >= max(curTileM)，
  *                       否则 DMA 在 sREQ_EXT 等待空间释放（不死锁但降吞吐）。
  * @param addrWidth      地址位宽
  * @param cDmaExtW       C 写回 DMA 外部总线位宽（默认 256）。总线位宽 < 转换后行宽时
  *                       行内拆多拍输出，运行期拍数由 cFmt 决定（见 cBeatsPerRowMax）；
  *                       约束族：isPow2 / totalN*32 整除 / [totalN*8, totalN*32]。
  */
case class WorkerBeeConfig(
    arrayN: Int = 8,
    clusterM: Int = 2,
    aW: Int = 16,
    bW: Int = 16,
    cW: Int = 0,
    // 默认集必含 MXE2M1：下方 require(!hasMx8 || hasMx4) 会把 fp8-only 配置在 elaborate 前
    //   拦死，故任何显式传 supportedFmts 的构造点只要带 MX 就必须含 MXE2M1。
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.INT8, DataFormat.INT4, DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1, DataFormat.FP8E4M3, DataFormat.FP8E5M2, DataFormat.BF16),
    aBufferDepth: Int = 64, 
    bBufferDepth: Int = 64,
    cBufferDepth: Int = 64,
    addrWidth: Int = 32,
    scaleBufferDepth: Int = 64,
    cDmaExtW: Int = 256
) {
  
  /** PE 总列数 */
  val totalN: Int = arrayN * clusterM
  val totalNx2: Int = totalN * 2
  val totalNx4: Int = totalN * 4

  /** A 侧有效位宽 */
  val aEffW: Int = aW

  /** 是否包含 FP16（1+5+10，满宽 24-bit 有效尾数路）。 */
  val hasFp16: Boolean = supportedFmts.contains(DataFormat.FP16)

  /** 是否包含 BF16（1+8+7，16-bit 有效尾数路）。与 FP16 彼此独立，启用 BF16 不隐含启用
    * FP16；两者 lane0 乘法器硬件不同（FP16 走 fp32 域宽阵列 FpMulFp16Bf16，BF16 走专用
    * 窄乘法器 FpMulBf16Mx），故必须有各自的编译期谓词，不得只靠合并后的 hasFp 选路。 */
  val hasBf16: Boolean = supportedFmts.contains(DataFormat.BF16)

  /** 是否包含浮点格式。供「只关心有无浮点」的判据使用（cEffW>=40 下界、WbOutCvt 的
    * fp32 槽生成等）；需要区分 FP16/BF16 的场合改用上面两个谓词。 */
  val hasFp: Boolean = hasFp16 || hasBf16

  /** 是否包含整数格式（用于整数累加位宽约束校验）。整数族 = {INT8, INT4}，不含 INT16。 */
  val hasInt: Boolean = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT4)

  /** 是否包含 INT4（半字节整型，quad-lane 打包=4）。与 hasMx4 并列，供 laneElemsW 线宽
    * 与 colTileAddressOffset 的 bits 口径门控使用（亚字节族需 3-bit laneElems 与 bits 几何）。 */
  val hasInt4: Boolean = supportedFmts.contains(DataFormat.INT4)

  /** 是否包含任一 MX 格式（含 E2M1）。WorkerBee 顶层 dmaScaleA/dmaScaleB 端口与
    * scale DMA 子模块的生成门控用它。 */
  val hasMx: Boolean = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2) || supportedFmts.contains(DataFormat.MXE2M1)

  /** hasMx8 = MXFP8 生成判据（与 hasMx 同值的语义别名）；hasMx4 = MXFP4(E2M1)。
    * 「生成 MX 硬件」的判据一律写成 (hasMx8 || hasMx4)。 */
  val hasMx8: Boolean = hasMx
  val hasMx4: Boolean = supportedFmts.contains(DataFormat.MXE2M1)

  // hasMx(=hasMx8) 是 scale DMA 端口/子模块、io.sum 路由、WbOutCvt fp32 支路的唯一主开关；
  // fp4-only 会静默丢弃 quad-lane 结果且无 scale 供数。
  require(!hasMx4 || hasMx8,
    s"WorkerBeeConfig: MXFP4(E2M1) v1 必须与 MXFP8(E4M3/E5M2) 同时启用" +
    s"（hasMx 仍是 scale DMA 端口与 io.sum 路由主开关，fp4-only 会静默算错），当前 supportedFmts=$supportedFmts")

  // 编译期不变式：supportedFmts 含任一 MX 格式 ⟹ 必含 MXE2M1（MX 族全有或全无）。非 MX
  //   配置两谓词均 false、两条 require 前件为假，合法。上方 require(!hasMx4 || hasMx8) 是
  //   恒真式（hasMx8 定义本身已含 MXE2M1），拦不住 fp4-only；本条才是有牙的那条，勿删。
  //   消息只用 ASCII（firtool 对非 ASCII 断言消息会发射失败）。
  require(!hasMx8 || hasMx4,
    "WorkerBeeConfig: MXFP8(E4M3/E5M2) requires MXFP4(E2M1) -- per user design rule " +
    "\"if mxfp8 is supported then mxfp4 is mandatory\". Together with the require above " +
    "this pins hasMx4 <=> hasMx8 (the MX family is all-in or all-out; FP-only / INT-only / " +
    "no-MX configs keep both false and stay legal). Current supportedFmts=" + supportedFmts)

  /** 是否包含非 MX 纯 FP8（FP8E4M3/FP8E5M2，per-tensor scale、无块 scale 字节）。
    * 运行语义 = 顶层把阵列侧 fmt remap 回 MX8 基线 + Executor scale 注入点恒 127
    * （E8M0 2^0 恒等乘）；scale DMA 三通道因 isMx=false 不启动、零流量。 */
  val hasFp8: Boolean =
    supportedFmts.contains(DataFormat.FP8E4M3) || supportedFmts.contains(DataFormat.FP8E5M2)

  // remap 路硬前提：纯 FP8 的阵列侧数据通路复用 MX8×MX8 基线（FusionMacFp 解码谓词喂
  //   remap 后的 MXE4M3/E5M2），故 FP8 支持蕴含 MX 硬件存在；FP8-only 配置运行期会静默
  //   算错（无 MX 支路可 remap）。
  require(!hasFp8 || hasMx,
    "WorkerBeeConfig: pure-FP8(FP8E4M3/FP8E5M2) v1 rides the MX8 datapath via fmt remap " +
    "(array sees MXE4M3/E5M2 with scale tied to 127), so FP8 requires the MX family in " +
    "supportedFmts. FP8-only configs would compute silently wrong. Current supportedFmts=" + supportedFmts)

  /** C 侧量化 scale 行位宽：每 minBlockSize=16 元素量化块一个 8-bit E8M0 scale 字节
    * （WbOutCvt.scaleRow 输出宽度，dmaCScale 通道行宽同源）。
    * cScaleRowW = (totalN/16)*8 = totalN/2 **bit** = totalN/16 **字节**；
    * totalN=16 ⟹ 8 bit = 1 字节/行。 */
  //DMA    
  val minBlockSize = 16

  // cScaleRowW 用的是【编译期常量】minBlockSize，而运行期块大小 cMxBlk = 16<<blkSel 是
  //   寄存器字段，二者只在 blkSel=0 时一致：blkSel=1 时一行 totalN 元素只该产 totalN/32 个
  //   scale 字节，端口物理宽度仍是 totalN/16 字节 ⟹ 契约前进量与端口宽度差 2 倍。
  //   totalN=16 良性（截断掉的高位与低位同属 block16(0)、值相同）；totalN >= 32 且
  //   cMxBlk=32 则 2 倍重叠、后一拍首字节被前一拍次字节覆盖 ⟹ C-scale 外部内存布局静默
  //   损坏，且 dmaCScale 端口宽度对 SoC 是 2× 错的。故本 require 把 minBlockSize 绑死到
  //   MXBlockSize 枚举最小档（16 << 0 = 16）；放开 totalN>=32 前须先让 cScaleRowW 随
  //   blkSel 可变。
  // scaleC 豁免于 lane 移位契约的依据是【端口宽度 = 1 字节 ⟹ lane 窗口退化为单 lane、
  //   移位恒为 0】，不是「strb 为常量满档」（后者仅 rowW=8 时巧合成立）。
  require(minBlockSize == (16 << MXBlockSize.all.map(_.litValue.toInt).min),
    "WorkerBeeConfig: minBlockSize (" + minBlockSize + ") must equal the smallest encodable " +
    "MXBlockSize level (16 << " + MXBlockSize.all.map(_.litValue.toInt).min + "), because " +
    "cScaleRowW = (totalN/minBlockSize)*8 is a COMPILE-TIME port width while the runtime " +
    "block size cMxBlk = 16<<blkSel is a register field. cMxBlk=32 needs cScaleRowW to vary " +
    "with blkSel; that coupling must be resolved before totalN>=32 (else the C-scale external " +
    "memory layout is silently corrupted by 2x overlap).")

  val cScaleRowW: Int = (totalN / minBlockSize) * 8
  val maxScalePerCyele = totalNx4 / minBlockSize
  val scaleAW: Int = 8 * maxScalePerCyele
  val scaleBW: Int = 8 * maxScalePerCyele
  

  /** C 累加器有效位宽（与 WbComb 推导逻辑一致）。
    * cW==0 自动推导：含浮点或 MX（含 fp4 quad-lane，复用同款 40-bit 延迟规格化累加器）时
    * 至少 40（延迟规格化 {sign,exp8,mant31} 需要）；纯整数时取 autoAccW
    * （aEffW+bW+log2Up(totalN)，累加防溢出）。 */
  val cEffW: Int = {
    val autoAccW = aEffW + bW + log2Up(totalN)
    if (cW != 0) math.max(cW, bW)
    else if (hasFp || hasMx8 || hasMx4) math.max(40, autoAccW)
    else autoAccW
  }

  // 各 DMA 通道外部数据位宽分别匹配各自 buffer 行宽（一拍一行，ext 与 buffer 等宽直连，
  // 无位宽适配/拆包逻辑）。

  /** A 通道外部 DMA 数据位宽（= A buffer 行宽 totalN*aEffW） */
  val aExtW: Int = totalN * aEffW

  /** B 通道外部 DMA 数据位宽（= B buffer 行宽 totalN*bW） */
  val bExtW: Int = totalN * bW

  /** C 通道外部 DMA 数据位宽（= C buffer 行宽 totalN*cEffW） */
  val cExtW: Int = totalN * cEffW

  /** C 写回每行最大拍数上界（编译期）：= totalN*32/cDmaExtW，按最密 32-bit 元素口径
    * 计算，仅供计数器/credit 位宽取上界（实际运行期拍数由 cFmt 决定）。
    * 运行期拍数（256 默认 × totalN=16）：fp32/int32 = 2 拍/行；fp16/bf16/int16 = 1 拍/行；
    * mxfp8/int8 = 1 拍/行（半占用，高位零填充）；mxfp4 = 1 拍/行（四分之一占用，高位零填充）。
    * 禁止「双行合拍」。 */
  val cBeatsPerRowMax: Int = totalN * 32 / cDmaExtW

  val queueAdapterBufferDepth: Int = 18

  //MNK width
  val mnkWidth: Int = 16

  // cW == 0 表示自动推导（见 cEffW），此时跳过显式位宽比较
  require(cW == 0 || cW >= aW, s"WorkerBeeConfig: cW($cW) 必须 >= aW($aW)")
  require(cW == 0 || cW >= bW, s"WorkerBeeConfig: cW($cW) 必须 >= bW($bW)")
  // 延迟规格化架构约束：含浮点或 MX 格式（含 fp4 quad-lane，复用同款累加器）时
  // cEffW 必须 >= 40，以容纳 {sign(1), exp(8), mantissa(31)}。
  if (hasFp || hasMx8 || hasMx4) require(cEffW >= 40, s"WorkerBeeConfig: 含浮点/MX 格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  // 整数累加约束：含整数格式时 cEffW 必须 >= aW+bW，以容纳整数积累加防溢出
  if (hasInt) require(cEffW >= aW + bW, s"WorkerBeeConfig: 含整数格式时 cEffW($cEffW) 必须 >= aW+bW(${aW + bW})（整数累加防溢出需要）")
  require(arrayN >= 4, s"WorkerBeeConfig: arrayN($arrayN) 必须 >= 4")
  require(clusterM >= 1, s"WorkerBeeConfig: clusterM($clusterM) 必须 >= 1")
  require(aBufferDepth > 0, s"WorkerBeeConfig: aBufferDepth($aBufferDepth) 必须 > 0")
  require(bBufferDepth > 0, s"WorkerBeeConfig: bBufferDepth($bBufferDepth) 必须 > 0")
  require(cBufferDepth >= 2*totalN, s"WorkerBeeConfig: cBufferDepth($cBufferDepth) 必须 > 2*totalN($totalN)")
  require(addrWidth > 0, s"WorkerBeeConfig: addrWidth($addrWidth) 必须 > 0")
  require(addrWidth <= 32, s"WorkerBeeConfig: addrWidth($addrWidth) 必须 <= 32")
  // C 写回总线（cDmaExtW）契约 require 族：
  //   isPow2 —— 同时拦截负值/0；
  //   totalN*32 整除 —— 计数器/credit 位宽按 cBeatsPerRowMax 取上界，非整除会使上界失效；
  //   [totalN*8, totalN*32] —— 下限保证 8-bit 输出格式（mxfp8/int8）一行单拍可容，
  //     上限保证 32-bit 输出格式（fp32/int32）拍数 >= 1。
  require(isPow2(cDmaExtW), s"WorkerBeeConfig: cDmaExtW($cDmaExtW) 必须是 2 的 N 次幂")
  require(totalN * 32 % cDmaExtW == 0, s"WorkerBeeConfig: totalN*32(${totalN * 32}) 必须能被 cDmaExtW($cDmaExtW) 整除（行拍数上界为整数）")
  require(totalN * 8 <= cDmaExtW && cDmaExtW <= totalN * 32,
    s"WorkerBeeConfig: cDmaExtW($cDmaExtW) 必须位于 [totalN*8(${totalN * 8}), totalN*32(${totalN * 32})]（8-bit 格式单拍可容、拍数上界整数）")
  // mxfp4 半字节几何前提：窄总线使一行需拆多拍（totalN*4 > cDmaExtW）时，每拍元素数
  // （cDmaExtW/4）必须为偶数，否则拍段在半字节中间切开导致内存布局错位；单拍整行
  // 天然豁免。本约束覆盖 hasMx 配置的全部窄总线，含量化与非量化输出。
  if (hasMx8 || hasMx4) require(totalN * 4 <= cDmaExtW || (cDmaExtW / 4) % 2 == 0,
    s"WorkerBeeConfig: hasMx 窄总线配置要求 mxfp4 每拍元素数（cDmaExtW/4=${cDmaExtW / 4}）为偶数（拍内整字节打包），" +
    s"该约束覆盖 hasMx 配置的全部窄总线（含量化与非量化输出），当前 cDmaExtW=$cDmaExtW")
  require(scaleBufferDepth > 0, s"WorkerBeeConfig: scaleBufferDepth($scaleBufferDepth) 必须 > 0（hasMx 时 scale FIFO 深度；非 MX 死路参数）")
  // MX 阵列几何硬约束：32-K scale 块 = 16 lane × 2 元素/拍 = 单 pass，该等价关系仅在
  // totalN==16 时成立（scaleBDma lineTarget=kTile、executor 每 sLOAD_B 窗口 pop scaleB
  // 均硬编码此假设）。fp4 同样自洽：16 lane × 4 elem = 64 K/pass，恰与 mxfp4 的 64-元素
  // scale 块单 pass 对齐。
  if (hasMx8 || hasMx4) require(totalN == 16, s"WorkerBeeConfig: MX 要求 totalN==16（fp8：32-K 块=16 lane×2 元素；fp4：64-K 块=16 lane×4 元素，均单 pass），当前 totalN=$totalN")
  // MX SIMD 双元素/拍：16-bit lane 打包 2 个 8-bit 元素（低字节=偶 k、高字节=奇 k），
  // 故 aW/bW 必须 >= 16。fp4 quad-lane 同样要求 16-bit lane（打包 4 个 4-bit 元素）。
  if (hasMx8 || hasMx4) require(aW >= 16 && bW >= 16, s"WorkerBeeConfig: MX SIMD 需要 aW/bW >= 16（fp8：16-bit lane 打包 2 元素；fp4：打包 4 元素），当前 aW=$aW bW=$bW")
}



