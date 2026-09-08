/** HiveCore 控制模块配置与命令/状态 Bundle 定义。
  *
  * HiveCore 是 Hive 脉动阵列加速器的顶层控制单元，采用「寄存器配置 + 一键执行」架构：
  *   - REG_WRITE: 写入配置寄存器
  *   - EXECUTE: 触发自动 tiling GEMM 计算
  *   - REG_READ: 读取状态/配置寄存器
  *
  * 内部 FSM 自动完成 M×K @ K×N → M×N 的 GEMM，
  * 根据阵列能力 (totalN×totalN) 自动切块 tiling。
  */

import chisel3._
import chisel3.util._

// ============================================================================
// 配置
// ============================================================================

/** HiveCore 编译期配置。
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
  * @param cBufferDepth   C 缓冲深度（仅存结果 + Partial sum，权重已改走独立 B buffer）
  *                       注意: cBufferDepth 应 >= max(curTileM)，
  *                       即能容纳 partial sum/结果数据，否则 DMA 在
  *                       sREQ_EXT 状态会等待空间释放（不会死锁但会降低吞吐）。
  * @param addrWidth      地址位宽
  * @param cDmaExtW       C 写回 DMA 外部总线位宽（P0 契约：默认 256，只允许 2 的 N 次幂；
  *                       废弃旧 0=640 满宽哨兵与 5B/元素 legacy 布局，废弃动作在 P2 落地）。
  *                       总线位宽 < 转换后行宽时行内拆多拍输出，运行期拍数由 cFmt 决定
  *                       （见 cBeatsPerRowMax 注释）；约束族：isPow2 /
  *                       totalN*32 整除 / [totalN*8, totalN*32]（见下方 require）。
  */
case class HiveCoreConfig(
    arrayN: Int = 8,
    clusterM: Int = 2,
    aW: Int = 16,
    bW: Int = 16,
    cW: Int = 0,
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8, DataFormat.MXE4M3, DataFormat.MXE5M2),
    aBufferDepth: Int = 64, 
    bBufferDepth: Int = 64,
    cBufferDepth: Int = 64,
    addrWidth: Int = 32,
    scaleBufferDepth: Int = 64,
    cDmaExtW: Int = 256
) {
  /*
  //hive矩阵切分逻辑
  for(n <- 1 to numberOfN){
    for(M <- 1 to numberOfM ){
        kTile = nTile * mTile
    }
  }
  //
  //存在K维切分的时候buffer
  //limit m * n < nKTileInCBuffer if kTileNum > 1
  */
  
  // --- 派生量 ---

  /** PE 总列数 */
  val totalN: Int = arrayN * clusterM

  /** A 侧有效位宽 */
  val aEffW: Int = aW

  /** 是否包含浮点格式（用于编译期条件化生成规格化硬件） */
  val hasFp: Boolean = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)

  /** 是否包含整数格式（用于整数累加位宽约束校验） */
  val hasInt: Boolean = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)

  /** 是否包含 MX 格式（MXFP8 E4M3 / E5M2） */
  val hasMx: Boolean = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2)

  /** P3a C 侧量化 scale 行位宽：每 8 元素量化块一个 8-bit E8M0 scale 字节
    * （HiveOutCvt.scaleRow 输出宽度，P3b dmaCScale 通道行宽同源） */
  val cScaleRowW: Int = (totalN / 8) * 8

  /** C 累加器有效位宽（与 HiveComb 推导逻辑一致）。
    * cW==0 自动推导：含浮点或 MX 时至少 40（延迟规格化 {sign,exp8,mant31} 需要），
    * 纯整数时取 autoAccW（aEffW+bW+log2Up(totalN)，累加防溢出）。 */
  val cEffW: Int = {
    val autoAccW = aEffW + bW + log2Up(totalN)
    if (cW != 0) math.max(cW, bW)
    else if (hasFp || hasMx) math.max(40, autoAccW)
    else autoAccW
  }

  // --- 各 DMA 通道外部数据位宽：分别匹配各自 buffer 行宽（一拍一行，
  // ext 与 buffer 等宽直连，无位宽适配/拆包逻辑） ---

  /** A 通道外部 DMA 数据位宽（= A buffer 行宽 totalN*aEffW） */
  val aExtW: Int = totalN * aEffW

  /** B 通道外部 DMA 数据位宽（= B buffer 行宽 totalN*bW） */
  val bExtW: Int = totalN * bW

  /** C 通道外部 DMA 数据位宽（= C buffer 行宽 totalN*cEffW） */
  val cExtW: Int = totalN * cEffW

  /** C 写回每行最大拍数上界（编译期）：= totalN*32/cDmaExtW，按最密 32-bit 元素口径
    * 计算，仅供计数器/credit 位宽取上界（实际运行期拍数由 cFmt 决定，见下）。
    * 运行期拍数（256 默认 × totalN=16）：fp32/int32 = 2 拍/行；fp16/bf16/int16 = 1 拍/行；
    * mxfp8/int8 = 1 拍/行（半占用，高位零填充）；mxfp4 = 1 拍/行（四分之一占用，高位零填充）。
    * 本期禁止「双行合拍」，占用率契约见重构计划裁定 9。 */
  val cBeatsPerRowMax: Int = totalN * 32 / cDmaExtW

  // P3a：新增 regs(11) = cScaleAddr（C 侧量化 scale 写回基地址），寄存器组 11 → 12。
  // 旧软件只写 0x00..0x0A，新增寄存器默认 0 不影响既有行为。
  val registerNumRW: Int = 12
  val registerNum: Int = 12

  //DMA
 

  //MNK width
  val mnkWidth: Int = 16

  // --- elaboration-time require 校验 ---
  // cW == 0 表示自动推导（见 cEffW），此时跳过显式位宽比较
  require(cW == 0 || cW >= aW, s"HiveCoreConfig: cW($cW) 必须 >= aW($aW)")
  require(cW == 0 || cW >= bW, s"HiveCoreConfig: cW($cW) 必须 >= bW($bW)")
  // 延迟规格化架构约束：含浮点格式或 MX 格式时 cEffW 必须 >= 40，
  // 以容纳 40-bit 延迟规格化格式 {sign(1), exp(8), mantissa(31)}。
  // MX 复用延迟规格化 fp 累加器。
  if (hasFp || hasMx) require(cEffW >= 40, s"HiveCoreConfig: 含浮点/MX 格式时 cEffW($cEffW) 必须 >= 40（延迟规格化需要）")
  // 整数累加约束：含整数格式时 cEffW 必须 >= aW+bW，以容纳整数积累加防溢出
  if (hasInt) require(cEffW >= aW + bW, s"HiveCoreConfig: 含整数格式时 cEffW($cEffW) 必须 >= aW+bW(${aW + bW})（整数累加防溢出需要）")
  require(arrayN >= 4, s"HiveCoreConfig: arrayN($arrayN) 必须 >= 4")
  require(clusterM >= 1, s"HiveCoreConfig: clusterM($clusterM) 必须 >= 1")
  require(aBufferDepth > 0, s"HiveCoreConfig: aBufferDepth($aBufferDepth) 必须 > 0")
  require(bBufferDepth > 0, s"HiveCoreConfig: bBufferDepth($bBufferDepth) 必须 > 0")
  require(cBufferDepth >= 2*totalN, s"HiveCoreConfig: cBufferDepth($cBufferDepth) 必须 > 2*totalN($totalN)")
  require(addrWidth > 0, s"HiveCoreConfig: addrWidth($addrWidth) 必须 > 0")
  require(addrWidth <= 32, s"HiveCoreConfig: addrWidth($addrWidth) 必须 <= 32")
  // --- C 写回总线（cDmaExtW）契约 require 族（P0 重构：默认 256、只允许 2 的 N 次幂，
  // 废弃旧 0=满宽哨兵与 5B/元素布局；窄总线拆拍与密集打包由 P2 落地，本阶段只立契约） ---
  // 位宽只允许 2 的 N 次幂（用户裁定；同时拦截负值/0 等非法值）
  require(isPow2(cDmaExtW), s"HiveCoreConfig: cDmaExtW($cDmaExtW) 必须是 2 的 N 次幂")
  // 行拍数上界（最密 32-bit 元素口径 totalN*32/cDmaExtW）必须为整数：
  // 计数器/credit 位宽按 cBeatsPerRowMax 取上界，非整除会使上界语义失效
  require(totalN * 32 % cDmaExtW == 0, s"HiveCoreConfig: totalN*32(${totalN * 32}) 必须能被 cDmaExtW($cDmaExtW) 整除（行拍数上界为整数）")
  // 取值区间 [totalN*8, totalN*32]：下限保证 8-bit 输出格式（mxfp8/int8）一行单拍可容，
  // 上限保证 32-bit 输出格式（fp32/int32）拍数 ≥ 1（不超过整行最密位宽）
  require(totalN * 8 <= cDmaExtW && cDmaExtW <= totalN * 32,
    s"HiveCoreConfig: cDmaExtW($cDmaExtW) 必须位于 [totalN*8(${totalN * 8}), totalN*32(${totalN * 32})]（8-bit 格式单拍可容、拍数上界整数）")
  // mxfp4 半字节几何前提（P0 保留、措辞适配新位宽族）：mxfp4（E2M1）输出为半字节/元素，
  // 窄总线使一行需拆多拍（totalN*4 > cDmaExtW）时，每拍元素数（cDmaExtW/4）必须为偶数，
  // 否则拍段在半字节中间切开导致内存布局错位；单拍整行（totalN*4 <= cDmaExtW，
  // 256 默认 × totalN=16 天然成立）天然豁免。量化判据门控在运行期，此处为编译期几何前提。
  // 【显式声明（评审裁定留痕）】本约束对 hasMx 配置的全部窄总线配置生效——含量化
  // （mxfp8/mxfp4）与非量化输出；下游配置窄总线时不得假设非量化输出可豁免本约束。
  if (hasMx) require(totalN * 4 <= cDmaExtW || (cDmaExtW / 4) % 2 == 0,
    s"HiveCoreConfig: hasMx 窄总线配置要求 mxfp4 每拍元素数（cDmaExtW/4=${cDmaExtW / 4}）为偶数（拍内整字节打包），" +
    s"该约束覆盖 hasMx 配置的全部窄总线（含量化与非量化输出），当前 cDmaExtW=$cDmaExtW")
  require(scaleBufferDepth > 0, s"HiveCoreConfig: scaleBufferDepth($scaleBufferDepth) 必须 > 0（hasMx 时 scale FIFO 深度；非 MX 死路参数）")
  // MX 阵列几何硬约束：32-K scale 块 = 16 lane × 2 元素/拍 = 单 pass，该等价关系仅在
  // totalN==16 时成立（scaleBDma lineTarget=kTile、executor 每 sLOAD_B 窗口 pop scaleB
  // 均硬编码此假设）；totalN≠16 的 hasMx 配置会静默数值错或挂死，故在
  // elaboration 期直接拒绝。若未来支持其它 totalN，需同步重推上述两处节奏。
  if (hasMx) require(totalN == 16, s"HiveCoreConfig: MX 要求 totalN==16（32-K scale 块 = 单 pass = 16 lane × 2 元素），当前 totalN=$totalN")
  // MX SIMD 双元素/拍：16-bit lane 打包 2 个 8-bit 元素（低字节=偶 k、高字节=奇 k），
  // PE 内 4×HiveCvtOpMx + 2×HiveMacFp 级联，故 aW/bW 必须 >= 16。
  if (hasMx) require(aW >= 16 && bW >= 16, s"HiveCoreConfig: MX SIMD 双元素/拍需要 aW/bW >= 16（16-bit lane 打包 2 元素），当前 aW=$aW bW=$bW")
}



