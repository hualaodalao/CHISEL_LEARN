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
  * @param cDmaExtW       C 写回 DMA 外部总线位宽（缩位配置）。0 = 哨兵（保持现状：
  *                       总线位宽 = cExtW 整行宽，一拍一行）；非 0 = 缩位写回，行内拆多拍，
  *                       必须为 cEffW 整数倍且按元素数整除 totalN（见下方 require）。
  *                       注意：缩位使每行写回拍数变为 totalN/E，C buffer 排空变慢，
  *                       需同步增大 cBufferDepth，否则大 M 下可能触发 cPush 违约 err。
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
    cDmaExtW: Int = 0
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
  val aEffW: Int = math.max(aW, bW)

  /** 是否包含浮点格式（用于编译期条件化生成规格化硬件） */
  val hasFp: Boolean = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)

  /** 是否包含整数格式（用于整数累加位宽约束校验） */
  val hasInt: Boolean = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)

  /** 是否包含 MX 格式（MXFP8 E4M3 / E5M2） */
  val hasMx: Boolean = supportedFmts.contains(DataFormat.MXE4M3) || supportedFmts.contains(DataFormat.MXE5M2)

  /** Scale 行位宽：scaleB 每行 totalN 个 8-bit E8M0 scale 因子 */
  val scaleRowW: Int = totalN * 8

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

  /** C 写回 DMA 外部总线有效位宽：哨兵 0 解析为 cExtW（出厂默认，一拍一行，
    * 既有端口与软件布局零感知）；非 0 = 缩位写回（行内拆多拍发出） */
  val cDmaExtWEff: Int = if (cDmaExtW == 0) cExtW else cDmaExtW

  /** C 写回每拍元素数 E（= 缩位位宽 / 单元素位宽；满宽时 = totalN） */
  val cElementsPerBeat: Int = cDmaExtWEff / cEffW

  /** C 写回每行拍数：1 = legacy 一拍一行；>1 = active 行内多拍拆分 */
  val cBeatsPerRow: Int = totalN / cElementsPerBeat

  val registerNumRW: Int = 11
  val registerNum: Int = 11

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
  // --- C 写回缩位（cDmaExtW）约束：哨兵 0 = 满宽 cExtW 保持现状，以下逐项豁免 ---
  // 缩位按元素粒度拆拍：总线位宽必须是单元素位宽（cEffW）的整数倍，
  // 否则一行无法按整元素切段（负值/0 以外的非法值在此一并拦截）
  require(cDmaExtW == 0 || cDmaExtW % cEffW == 0, s"HiveCoreConfig: cDmaExtW($cDmaExtW) 必须是 cEffW($cEffW) 的整数倍（缩位按元素位宽拆拍）")
  // 缩位只能在 (0, cExtW] 内取值：不得为负、不得超过整行位宽（等于 cExtW 等价满宽一拍一行）
  require(cDmaExtW == 0 || (cDmaExtW > 0 && cDmaExtW <= cExtW), s"HiveCoreConfig: cDmaExtW($cDmaExtW) 必须位于 (0, cExtW($cExtW)]（0 为满宽哨兵）")
  // 行内多拍拆分要求每拍元素数整除 N（拍数 = totalN/E 为整数）：
  // 用户需求「必须是 N 倍数所见」在 elaboration 期强制，非 2 幂整除的 E 直接拒绝
  require(cDmaExtW == 0 || totalN % cElementsPerBeat == 0, s"HiveCoreConfig: 每拍元素数 cElementsPerBeat($cElementsPerBeat) 必须整除 totalN($totalN)（行内拆拍必须为整数拍）")
  // 缩位（cBeatsPerRow>1）每拍打包位宽下限防护：发拍时数据段按密集元素位宽拼接，
  // 若总线位宽 < 每拍元素密集位宽，Cat(0.U((cDmaExtWEff - E*elemW).W), ...) 会
  // elaboration 负位宽报错；提前在构造期用中文消息拦截（浮点配置最低按 16-bit
  // 密集（fp16/bf16 输出），纯整数配置按 32-bit 密集（写回值低 32 位））
  if (cBeatsPerRow > 1) {
    if (!hasFp && !hasMx) require(cDmaExtWEff >= cElementsPerBeat * 32,
      s"HiveCoreConfig: 纯整数缩位配置要求 cDmaExtWEff($cDmaExtWEff) >= cElementsPerBeat*32(${cElementsPerBeat * 32})，否则发拍数据拼接位宽为负")
    else require(cDmaExtWEff >= cElementsPerBeat * 16,
      s"HiveCoreConfig: 浮点/MX 缩位配置要求 cDmaExtWEff($cDmaExtWEff) >= cElementsPerBeat*16(${cElementsPerBeat * 16})，否则 16-bit 密集发拍拼接位宽为负")
  }
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



