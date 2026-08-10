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
  * @param extDataWidth   外部数据总线位宽（bit）
  * @param addrWidth      地址位宽
  */
case class HiveCoreConfig(
    arrayN: Int = 8,
    clusterM: Int = 2,
    aW: Int = 16,
    bW: Int = 16,
    cW: Int = 0,
    supportedFmts: Set[DataFormat.Type] = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8),
    aBufferDepth: Int = 64, 
    bBufferDepth: Int = 64,
    cBufferDepth: Int = 64,
    extDataWidth: Int = 128,
    addrWidth: Int = 32
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

  /** C 累加器有效位宽（与 HiveComb 推导逻辑一致） */
  val cEffW: Int = {
    val hasFp  = supportedFmts.contains(DataFormat.FP16) || supportedFmts.contains(DataFormat.BF16)
    val autoAccW = aEffW + bW + log2Up(totalN)
    if (cW != 0) math.max(cW, bW)
    else if (hasFp) math.max(32, autoAccW)
    else autoAccW
  }

  val registerNumRW: Int = 8 
  val registerNum: Int = 9

  //DMA
 

  //MNK width
  val mnkWidth: Int = 16

  // --- elaboration-time require 校验 ---
  // cW == 0 表示自动推导（见 cEffW），此时跳过显式位宽比较
  require(cW == 0 || cW >= aW, s"HiveCoreConfig: cW($cW) 必须 >= aW($aW)")
  require(cW == 0 || cW >= bW, s"HiveCoreConfig: cW($cW) 必须 >= bW($bW)")
  require(arrayN >= 4, s"HiveCoreConfig: arrayN($arrayN) 必须 >= 4")
  require(clusterM >= 1, s"HiveCoreConfig: clusterM($clusterM) 必须 >= 1")
  require(aBufferDepth > 0, s"HiveCoreConfig: aBufferDepth($aBufferDepth) 必须 > 0")
  require(bBufferDepth > 0, s"HiveCoreConfig: bBufferDepth($bBufferDepth) 必须 > 0")
  require(cBufferDepth > 0, s"HiveCoreConfig: cBufferDepth($cBufferDepth) 必须 > 0")
  require(extDataWidth > 0, s"HiveCoreConfig: extDataWidth($extDataWidth) 必须 > 0")
  require(addrWidth > 0, s"HiveCoreConfig: addrWidth($addrWidth) 必须 > 0")
  require(addrWidth <= 32, s"HiveCoreConfig: addrWidth($addrWidth) 必须 <= 32")
}



