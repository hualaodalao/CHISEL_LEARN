
import chisel3._
import chisel3.util._

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
  val reg0Addr = UInt(5.W)       // 寄存器地址
  val reg1Addr = UInt(5.W)       // 寄存器地址
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

/** 外部 DMA 只读通道接口（简化版，无 IMasterSlave）。
  *
  * 方向按 HiveCore（master）视角声明：rsp 为从外部存储读入的数据流。
  * 数据位宽按通道参数化（aDma = cfg.aExtW，bDma = cfg.bExtW，
  * 各自匹配对应 buffer 行宽，一拍一行等宽直连）。
  *
  * @param cfg       HiveCore 配置
  * @param dataWidth 外部数据位宽（bit）
  */
object HiveCoreDMAExtReadOnlyIF {
    def apply(cfg: HiveCoreConfig, dataWidth: Int) = new HiveCoreDMAExtReadOnlyIF(cfg, dataWidth)
}

class HiveCoreDMAExtReadOnlyIF(cfg: HiveCoreConfig, dataWidth: Int) extends Bundle {
  val rsp = Flipped(Stream(new Bundle {
    val data = UInt(dataWidth.W)
    val err = Bool()
  }))
  val req = Stream(new Bundle {
    val addr = UInt(cfg.addrWidth.W)
  })
}

object HiveCoreDMAExtWriteOnlyIF {
  def apply(cfg: HiveCoreConfig, dataWidth: Int) = new HiveCoreDMAExtWriteOnlyIF(cfg, dataWidth)
}
class HiveCoreDMAExtWriteOnlyIF(cfg: HiveCoreConfig, dataWidth: Int) extends Bundle {
  val rsp = Flipped(Stream(new Bundle {
    val err = Bool()
  }))
  
  val req = Stream(new Bundle {
    val addr = UInt(cfg.addrWidth.W)
    val data = UInt(dataWidth.W)
  })
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

  /** B（权重）缓冲当前占用量（调试用） */
  val bOccupancy = UInt(log2Up(cfg.bBufferDepth + 1).W)

  /** C 缓冲当前占用量 */
  val cOccupancy = UInt(log2Up(cfg.cBufferDepth + 1).W)
}
object HiveCoreStatus {
  def apply(cfg: HiveCoreConfig) = new HiveCoreStatus(cfg)
}

class HiveCoreRegister(cfg: HiveCoreConfig) extends Bundle {
    // ==========================================================================
  // 寄存器组定义
  // ==========================================================================
  // 地址  名称（与下方 regs 索引 defs 一一对应）
  // 0x00  REG_M         M 维度
  // 0x01  REG_N         N 维度
  // 0x02  REG_K         K 维度
  // 0x03  REG_A_ADDR    A 矩阵基地址
  // 0x04  REG_B_ADDR    B 矩阵基地址
  // 0x05  REG_C_ADDR    C 矩阵基地址
  // 0x06  REG_A/B/C_STRIDE  A/B/C 行步长（[3:0]/[7:4]/[11:8]，各 4bit）
  // 0x07  REG_CONTROL   [0]=clear_done (写1清除), [1:0]=fmt, [4:2]=rnd,
  //                     [5]=loadWMode (0=垂直加载, 1=水平 loadW 加载)
  // 0x08  REG_STATUS    [0]=busy, [1]=done, [2]=err, [31:16]=progress (只读)

  val regs = Vec(cfg.registerNum, UInt(32.W))
  def m   = regs(0)
  def n   = regs(1)
  def k   = regs(2)
  def aAddr = regs(3)
  def bAddr = regs(4)
  def cAddr = regs(5)
  def aStride = regs(6)(3, 0)
  def bStride = regs(6)(7, 4)
  def cStride = regs(6)(11, 8)
  // 注意：位域宽度必须与枚举实际宽度一致（DataFormat=2bit, RoundingMode=3bit,
  // HiveCoreLoopMode 仅剩 MKN 单值=1bit），否则 asTypeOf 会因宽度不匹配报错
  def fmt = regs(7)(1, 0).asTypeOf(DataFormat())
  def rnd = regs(7)(4, 2).asTypeOf(RoundingMode())
  def isFloat = fmt === DataFormat.BF16 | fmt === DataFormat.FP16
  //def loopMode = regs(7)(5).asTypeOf(HiveCoreLoopMode())
  // 权重加载模式位：0=垂直加载（loadV 经 psum 口下沉，既有行为），
  // 1=水平 loadW 加载（权重经 a 数据链水平移位 + 末拍广播锁存，B 内存转置存储）
  def loadWMode = regs(7)(5)
}
object HiveCoreRegister {
  def apply(cfg: HiveCoreConfig) = new HiveCoreRegister(cfg)
}

class HiveCoreExePreCalcConfig(cfg: HiveCoreConfig) extends Bundle {
  val nTile = UInt(cfg.mnkWidth.W)
  val mTile = UInt(cfg.mnkWidth.W)    
  val kTile = UInt(cfg.mnkWidth.W)

  val aRowAddressOffset = UInt(cfg.addrWidth.W)
  val bRowAddressOffset = UInt(cfg.addrWidth.W)
  val cRowAddressOffset = UInt(cfg.addrWidth.W)
  val aColTileAddressOffset = UInt(cfg.addrWidth.W) 
  val cColTileAddressOffset = UInt(cfg.addrWidth.W) 
  val bColTileAddressOffset = UInt(cfg.addrWidth.W) 
}
object HiveCoreExePreCalcConfig {
  def apply(cfg: HiveCoreConfig) = new HiveCoreExePreCalcConfig(cfg)
}
