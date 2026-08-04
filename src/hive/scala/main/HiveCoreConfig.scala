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
  * @param cBufferDepth   C 缓冲深度（统一缓冲：权重 + 结果 + Partial sum）
  *                       注意: cBufferDepth 应 >= max(curTileM) + max(curTileK * curTileN)，
  *                       即同时容纳 partial sum 和新加载的权重数据，否则 DMA 在
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
    cBufferDepth: Int = 64,
    extDataWidth: Int = 128,
    addrWidth: Int = 32
) {
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

  // --- elaboration-time require 校验 ---
  require(arrayN >= 4, s"HiveCoreConfig: arrayN($arrayN) 必须 >= 4")
  require(clusterM >= 1, s"HiveCoreConfig: clusterM($clusterM) 必须 >= 1")
  require(aBufferDepth > 0, s"HiveCoreConfig: aBufferDepth($aBufferDepth) 必须 > 0")
  require(cBufferDepth > 0, s"HiveCoreConfig: cBufferDepth($cBufferDepth) 必须 > 0")
  require(extDataWidth > 0, s"HiveCoreConfig: extDataWidth($extDataWidth) 必须 > 0")
  require(addrWidth > 0, s"HiveCoreConfig: addrWidth($addrWidth) 必须 > 0")
}

// ============================================================================
// 操作码枚举（仅 3 种命令）
// ============================================================================

/** HiveCore 操作码：寄存器写、执行、寄存器读。 */
object HiveCoreOp extends ChiselEnum {
  val REG_WRITE, EXECUTE, REG_READ = Value
}

// ============================================================================
// 命令 Bundle
// ============================================================================

/** HiveCore 命令描述符。
  *
  * REG_WRITE: 一次写入 regAddr 和 regAddr+1 两个寄存器
  * EXECUTE: 触发 GEMM 计算（data0/data1/regAddr 无用）
  * REG_READ: 读取 regAddr 对应的寄存器
  */
class HiveCoreCmd extends Bundle {
  val op      = HiveCoreOp()    // 操作码
  val regAddr = UInt(8.W)       // 寄存器地址
  val data0   = UInt(32.W)      // 写入数据 0
  val data1   = UInt(32.W)      // 写入数据 1
}

// ============================================================================
// 命令响应 Bundle
// ============================================================================

/** HiveCore 命令响应。 */
class HiveCoreResp extends Bundle {
  val data = UInt(32.W)         // REG_READ 返回数据
  val done = Bool()             // EXECUTE 完成标志
  val err  = Bool()             // 错误标志
}

// ============================================================================
// DMA 外部通道接口
// ============================================================================

/** 外部 DMA 通道接口（简化版，无 IMasterSlave）。
  *
  * 方向按 HiveCore（master）视角声明：
  *   - readData: 从外部存储读入的数据流（master 接收）
  *   - writeData: 向外部存储写出的数据流（master 驱动）
  *
  * @param cfg HiveCore 配置
  */
class DmaExtIO(cfg: HiveCoreConfig) extends Bundle {

  /** 从外部读入数据（slave 驱动 valid/payload，master 驱动 ready） */
  val readData = Flipped(new Stream(UInt(cfg.extDataWidth.W)))

  /** 向外部写出数据（master 驱动 valid/payload，slave 驱动 ready） */
  val writeData = new Stream(UInt(cfg.extDataWidth.W))

  /** 访问地址 */
  val addr = Output(UInt(cfg.addrWidth.W))

  /** 传输长度（beat 数） */
  val len = Output(UInt(16.W))

  /** 请求有效 */
  val req = Output(Bool())

  /** 授权信号（外部返回） */
  val grant = Input(Bool())

  /** 写方向标志（true = 写，false = 读） */
  val isWrite = Output(Bool())
}

// ============================================================================
// 状态 Bundle
// ============================================================================

/** HiveCore 运行时状态。
  *
  * @param cfg HiveCore 配置
  */
class HiveCoreStatus(cfg: HiveCoreConfig) extends Bundle {

  /** 忙标志 */
  val busy = Bool()

  /** 完成标志 */
  val done = Bool()

  /** 错误标志 */
  val err = Bool()

  /** 当前进度（已完成的 tile 数） */
  val progress = UInt(16.W)

  /** A 缓冲当前占用量 */
  val aOccupancy = UInt(log2Up(cfg.aBufferDepth + 1).W)

  /** C 缓冲当前占用量 */
  val cOccupancy = UInt(log2Up(cfg.cBufferDepth + 1).W)
}
