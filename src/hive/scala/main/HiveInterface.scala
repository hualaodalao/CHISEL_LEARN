
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

/** 外部 DMA 通道接口（简化版，无 IMasterSlave）。
  *
  * 方向按 HiveCore（master）视角声明：
  *   - readData: 从外部存储读入的数据流（master 接收）
  *   - writeData: 向外部存储写出的数据流（master 驱动）
  *
  * @param cfg HiveCore 配置
  */
object HiveCoreDMAExtReadOnlyIF {
    def apply(cfg: HiveCoreConfig) = new HiveCoreDMAExtReadOnlyIF(cfg)
}

class HiveCoreDMAExtReadOnlyIF(cfg: HiveCoreConfig) extends Bundle {
  val rsp = Flipped(Stream(new Bundle {
    val data = UInt(cfg.extDataWidth.W)
    val rsp = Bool()
  }))
  val cmd = Stream(new Bundle {
    val addr = UInt(cfg.addrWidth.W)
  })
}

object HiveCoreDMAExtWriteOnlyIF {
  def apply(cfg: HiveCoreConfig) = new HiveCoreDMAExtWriteOnlyIF(cfg)
}

class HiveCoreDMAExtWriteOnlyIF(cfg: HiveCoreConfig) extends Bundle {
  val cmd = Stream(new Bundle {
    val data = UInt(cfg.extDataWidth.W)
    val addr = UInt(cfg.addrWidth.W)
  })
}

object HiveCoreDMAIntIF {
  def apply(cfg: HiveCoreConfig) = new HiveCoreDMAIntIF(cfg)
}

//内部DMA本质每次读取可写出都是一个Tile块
class HiveCoreDMAIntIF(cfg: HiveCoreConfig) extends Bundle {
  val peek = Decoupled(Bool()) //每次peek即读取或者写出一个Tile
  val start = Input(Bool())
  val done = Output(Bool())
  val err = Output(Bool())
  val busy  = Output(Bool())
}

  
  
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
object HiveCoreStatus {
  def apply(cfg: HiveCoreConfig) = new HiveCoreStatus(cfg)
}

class HiveCoreRegister(cfg: HiveCoreConfig) extends Bundle {
    // ==========================================================================
  // 寄存器组定义
  // ==========================================================================
  // 地址  名称
  // 0x00  REG_M/N       M/N 维度
  // 0x01  REG_K         K 维度
  // 0x02  REG_A_ADDR    A 矩阵基地址
  // 0x03  REG_B_ADDR    B 矩阵基地址
  // 0x04  REG_C_ADDR    C 矩阵基地址
  // 0x05  REG_A/B/C_STRIDE  A/B/C 行步长
  // 0x06  REG_FMT_RND   [3:0]=fmt, [11:4]=rnd
  // 0x07  REG_CONTROL   [0]=clear_done (写1清除)
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
  def fmt = regs(7)(3, 0).asTypeOf(DataFormat())
  def rnd = regs(7)(7, 4).asTypeOf(RoundingMode())
  def loopMode = regs(7)(11, 8).asTypeOf(HiveCoreLoopMode())
}
object HiveCoreRegister {
  def apply(cfg: HiveCoreConfig) = new HiveCoreRegister(cfg)
}

class HiveCoreExePreCalcConfig(cfg: HiveCoreConfig) extends Bundle {
  val nTile = UInt(cfg.mnkWidth.W)
  val mTile = UInt(cfg.mnkWidth.W)    
  val kTile = UInt(cfg.mnkWidth.W)

  def aTileRowBytes = (cfg.aW * cfg.totalN).U
  def bTileRowBytes = (cfg.bW * cfg.totalN).U
  def cTileRowBytes = (cfg.cW * cfg.totalN).U
  val aRowAddressOffset = UInt(cfg.addrWidth.W)
  val bRowAddressOffset = UInt(cfg.addrWidth.W)
  val cRowAddressOffset = UInt(cfg.addrWidth.W)
  val aColAddressOffset = UInt(cfg.addrWidth.W)
  val cColAddressOffset = UInt(cfg.addrWidth.W)
  val bColAddressOffset = UInt(cfg.addrWidth.W)
}
