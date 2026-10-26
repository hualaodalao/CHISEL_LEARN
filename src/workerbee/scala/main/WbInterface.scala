
import chisel3._
import chisel3.util._
import chisel3.dontTouch

// ============================================================================
// 命令 Bundle
// ============================================================================

class HiveCoreCmd extends Bundle {
  val op      = HiveCoreOp()    // 操作码
  val reg0Addr = UInt(6.W)       // 寄存器索引
  val reg1Addr = UInt(6.W)       // 寄存器索引
  val data0   = UInt(32.W)      // 写入数据 0
  val data1   = UInt(32.W)      // 写入数据 1
  val pc      = UInt(32.W)
  val tag     = UInt(8.W)
}

class HiveCoreRespForTracing extends Bundle {
  val state = UInt(2.W) // 00 start 01 end 1x err
  val pc = UInt(32.W)         // REG_READ 返回数据
  val tag  = UInt(8.W)
}


// ============================================================================
// 命令响应 Bundle
// ============================================================================

/** HiveCore 命令响应。 */
class HiveCoreResp extends Bundle {
  val data = UInt(32.W)         // REG_READ 返回数据
  val err  = Bool()             // 错误标志
  val laneId = UInt(2.W)
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
    val strb = UInt((dataWidth/8).W)
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

  val inFlight = Bool()

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

/** HiveCore 性能观测信号组（【perf 端口】纯组合电平、无内部计数状态；计数/统计
  * 由外部 PMU 按拍采样完成——本模块不内建 perf counter，用户裁定）。
  *
  * - dmaA/B/C、scaleA/B/C：各 DMA 通道请求有效拍（物理通道口径：scaleA 含
  *   bias/rqM 取数盗用拍；成因分解可外部结合 epilogue 交叉采样）
  * - array：阵列有效计算拍（hiveValidIn）
  * - stallA：供数相（sLOAD_A）阵列空闲拍——aPop 未 fire（含 A/scaleA 供数
  *   不足、bias/rqM 未收齐、C 写回占用读口等全部挂起成因）
  * - stallB：权重供数等待（loadW 窗口 bPop 停拍 ∨ sDRAIN 预蓄等待拍），双缓冲/
  *   预取深度决策依据
  * - epilogue：epilogue 子系统活动拍（bias/rqM 取数 ∨ cvt 转换/量化管线占用；
  *   C 写回通道活动另由 dmaC/scaleC 观测）
  * - busy：总忙碌电平（= status.busy，作分母对齐）
  *
  * 非 MX 配置下 scaleA/B/C 恒 false（端口形状跨配置保持稳定）。
  */
class HiveCorePerf extends Bundle {
  val dmaA = Bool()
  val dmaB = Bool()
  val dmaC = Bool()
  val scaleA = Bool()
  val scaleB = Bool()
  val scaleC = Bool()
  val array = Bool()
  val stallA = Bool()
  val stallB = Bool()
  val epilogue = Bool()
  val busy = Bool()
}
object HiveCorePerf {
  def apply() = new HiveCorePerf()
}

class HiveCoreRegister(cfg: HiveCoreConfig) extends Bundle {
  //占用DW0高位编码
  //val cmdType = UInt(4.W);dontTouch(cmdType)

  val m = UInt(cfg.mnkWidth.W)
  val n = UInt(cfg.mnkWidth.W)
  val k = UInt(cfg.mnkWidth.W)
  val aAddr = UInt(cfg.addrWidth.W)
  val bAddr = UInt(cfg.addrWidth.W)
  val cAddr = UInt(cfg.addrWidth.W)

  //val aStride = UInt(4.W)
  //val bStride = UInt(4.W)
  //val cStride = UInt(4.W)
  val aAddrMode = AddrMode()
  val bAddrMode = AddrMode()
  val cAddrMode = AddrMode()
  val aMxBlockSize = MXBlockSize()
  val bMxBlockSize = MXBlockSize()
  val cMxBlockSize = MXBlockSize()
  val loadWMode = Bool()
  val aFmt = DataFormat()
  val bFmt = DataFormat()
  val cFmt = DataFormat()
  val rnd = RoundingMode()
  
  val aScaleAddr = UInt(32.W)
  val bScaleAddr = UInt(32.W)
  val cScaleAddr = UInt(32.W)
  val warmup = Bool()
  val warmdown = Bool()
  val biasOn = Bool()
  val biasAddr = UInt(cfg.addrWidth.W)
  val requantOn = Bool()
  val reluOn = Bool()
  val rqMBase = UInt(cfg.addrWidth.W)
  val rqShift = UInt(6.W)
  val rqZp = SInt(16.W)

  
  

}
object HiveCoreRegister {
  def apply(cfg: HiveCoreConfig) = new HiveCoreRegister(cfg)
}

class HiveCoreExePreCalcConfig(cfg: HiveCoreConfig) extends Bundle {
  val nTile = UInt(cfg.mnkWidth.W)
  val mTile = UInt(cfg.mnkWidth.W)    
  val kTile = UInt(cfg.mnkWidth.W)
  val kTileB = UInt(cfg.mnkWidth.W)

  val aRowAddressOffset = UInt(cfg.addrWidth.W)
  val bRowAddressOffset = UInt(cfg.addrWidth.W)
  val cRowAddressOffset = UInt(cfg.addrWidth.W)
  val aColTileAddressOffset = UInt(cfg.addrWidth.W) 
  val cColTileAddressOffset = UInt(cfg.addrWidth.W) 
  val bColTileAddressOffset = UInt(cfg.addrWidth.W) 

  // 仅 mxRun 启动时消费，非 MX 为死路。
  val scaleARowAddressOffset = UInt(cfg.addrWidth.W)
  val scaleAColTileAddressOffset = UInt(cfg.addrWidth.W)
  val scaleBRowAddressOffset = UInt(cfg.addrWidth.W)
  val scaleBColTileAddressOffset = UInt(cfg.addrWidth.W)
  val scaleCRowAddressOffset = UInt(cfg.addrWidth.W)
  val scaleCColTileAddressOffset = UInt(cfg.addrWidth.W)
  // --- P0 C 写回重构：cDma 拍内地址步进（行内多拍拆分） ---
  // cBeatAddrStep = (cDmaExtW/elemBits)×elemBytes（运行期：elemBits/elemBytes 由 outFmt
  // 决定），供 P2 WrOnly 拍内地址 = curAddr + beatCnt×cBeatAddrStep 消费；
  // P0 阶段只立契约（字段位宽/语义），无消费者，顶层暂占位驱 0。
  val cBeatAddrStep = UInt(cfg.addrWidth.W)
  val cDmaBufBeatTar = UInt(4.W)
  val cDmaDmaBeatTar = UInt(4.W)
  val isAX2 = Bool()
  val isAX4 = Bool()
  val isBX2 = Bool()
  val isBX4 = Bool()

  
}
object HiveCoreExePreCalcConfig {
  def apply(cfg: HiveCoreConfig) = new HiveCoreExePreCalcConfig(cfg)
}

