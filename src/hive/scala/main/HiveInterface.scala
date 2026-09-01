
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
  // 0x07  REG_CONTROL   位域编码（3-bit DataFormat 枚举）：
  //         [0]    = clear_done (写1清除) — 与 aFmt[1:0] 复用低 2 位
  //         [1:0]  = aFmt 低 2 位
  //         [4:2]  = rnd (RoundingMode)
  //         [5]    = loadWMode (0=垂直加载, 1=水平 loadW 加载)
  //         [6]    = aFmt 高 1 位（拼接 [6],[1:0] 得完整 3-bit aFmt）
  //         [7]    = bFmt 高 1 位（拼接 [7],[9:8] 得完整 3-bit bFmt）
  //         [9:8]  = bFmt 低 2 位
  //         [10]   = mixFmtEn (v1 恒 0：bFmt 跟随 aFmt)
  //         [13:11] = outFmt (P3a dmaC 写回输出格式，3-bit 编码：
  //                   000=fp32 默认 / 001=fp16 / 010=bf16 / 011=reserved 同 000 /
  //                   100=mxfp8-E4M3 量化 / 101=mxfp8-E5M2 量化 / 110=mxfp4-E2M1 量化 /
  //                   111=reserved 降级 000。兼容性：bit13=0 时旧软件语义逐字不变；
  //                   仅浮点/MX 运行生效，整数运行强制透传，
  //                   见 HiveCoreDmaWrOnly.outFmtEff；默认 0 → 既有软件不写该位行为不变。
  //                   16-bit 与量化输出均为两级舍入链：40→32 固定简化 RNE 规格化 +
  //                   降精度/量化运行期 rnd，方向性模式与理想单级舍入可能存在末位差异)
  //         [15:14] = qBlockSize (P3a 量化块大小：00=8 / 01=16 / 10=32 / 11=64 元素/块，
  //                   块内元素共享一个 E8M0 scale；仅量化输出（outFmt 1xx）生效)
  // 0x08  REG_STATUS    [0]=busy, [1]=done, [2]=err, [31:16]=progress (只读)
  // 0x09  REG_SCALE_A_ADDR  scaleA 基地址
  // 0x0A  REG_SCALE_B_ADDR  scaleB 基地址
  // 0x0B  REG_SCALE_C_ADDR  P3a C 侧量化 scale 行写回基地址（regs(11)，P3b dmaCScale 通道使用）

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

  // --- REG_CONTROL (0x07) 位域 ---
  // aFmt: 3-bit = Cat(regs(7)(6), regs(7)(1,0))
  def aFmt = Cat(regs(7)(6), regs(7)(1, 0)).asTypeOf(DataFormat())
  // bFmt: 3-bit = Cat(regs(7)(7), regs(7)(9,8))
  def bFmt = Cat(regs(7)(7), regs(7)(9, 8)).asTypeOf(DataFormat())
  // mixFmtEn: v1 恒 0（bFmt 跟随 aFmt）
  def mixFmtEn = regs(7)(10)
  // P3a dmaC 写回输出格式 outFmt：regs(7) bit(13,11)，3-bit 编码表：
  //   000=fp32（默认）、001=fp16、010=bf16、011=reserved（行为同 000）、
  //   100=mxfp8-E4M3 量化、101=mxfp8-E5M2 量化、110=mxfp4-E2M1 量化、
  //   111=reserved（降级 000）。
  //   兼容性留痕：bit13=0 时（000..011）与原 2-bit 编码逐字等价（原 [12:11] 升级为
  //   低 2 位），旧软件不写 bit13 行为逐拍不变；量化编码（1xx）共享 E8M0 块 scale，
  //   量化语义（块 amax/全零块 0x00 哨兵/饱和与 inf-nan 映射）见 HiveOutCvt 类头注释。
  //   运行期有效值 outFmtEff = (isFloat || isMx) ? outFmt : 000（整数累加值不做精度转换），
  //   判据在 HiveCoreDmaWrOnly 与 HiveCore（地址步进）同源定义、互指。
  //   【舍入链声明】16-bit 与量化输出均为两级舍入链：40→32 固定简化 RNE 规格化（fpNormalize40to32）
  //   + 降精度/量化运行期 rnd（HiveOutCvt roundUp16/roundUpN）；方向性模式（RDN/RUP/RMM）与
  //   理想单级舍入可能存在末位差异（两级舍入累积），软件对拍须同链重建。
  def outFmt = regs(7)(13, 11)
  // P3a 量化块大小 qBlockSize：regs(7) bit(15,14)，00=8 / 01=16 / 10=32 / 11=64（元素/块）。
  // 块内元素共享一个 E8M0 scale（HiveOutCvt.blkSel 同源）；仅量化输出时生效，
  // 非量化运行写入无副作用。
  def qBlockSize = regs(7)(15, 14)
  def rnd = regs(7)(4, 2).asTypeOf(RoundingMode())
  // 权重加载模式位：0=垂直加载（loadV 经 psum 口下沉，既有行为，B内存非转置），
  // 1=水平 loadW 加载（权重经 a 数据链水平移位 + 末拍广播锁存，B内存转置存储）
  def loadWMode = regs(7)(5)

  // 兼容别名：旧代码通过 regFile.fmt 访问格式，等同 aFmt
  def fmt = aFmt
  // isFloat 判据：仅传统 FP16/BF16（MX 不走此路径，DMA 侧把 MX 当原始字节）
  def isFloat = aFmt === DataFormat.BF16 || aFmt === DataFormat.FP16

  // --- Scale 地址寄存器 (0x09 / 0x0A / 0x0B) ---
  def scaleAAddr = regs(9)
  def scaleBAddr = regs(10)
  // P3a：C 侧量化 scale 行写回基地址（0x0B；P3b dmaCScale 通道地址模型同源，
  // 行步进/列步进公式由 P3b 定义，本阶段仅占位寄存器）
  def cScaleAddr = regs(11)
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
  // --- P3b C 侧量化 scale 写回（dmaCScale 通道）地址偏移 ---
  // 行步进 = ceil(n/blk) 字节（每 M 行一个 scale 行，块粒度打包，运行期由顶层计算）；
  // 列 tile 步进 = totalN/blk 字节（每个 N tile 覆盖 totalN 列 = totalN/blk 个块）。
  // blk = 8 << qBlockSize 运行期解码；非量化运行时本字段无消费者（死路）。
  val cScaleRowAddressOffset = UInt(cfg.addrWidth.W)
  val cScaleColTileAddressOffset = UInt(cfg.addrWidth.W)
}
object HiveCoreExePreCalcConfig {
  def apply(cfg: HiveCoreConfig) = new HiveCoreExePreCalcConfig(cfg)
}
