
import chisel3._
import chisel3.util._
import chisel3.dontTouch


class WorkerBeeCmd extends Bundle {
  val op      = WorkerBeeOp()
  val reg0Addr = UInt(6.W)
  val reg1Addr = UInt(6.W)
  val data0   = UInt(32.W)
  val data1   = UInt(32.W)
  val pc      = UInt(32.W)
  val tag     = UInt(8.W)
}

class WorkerBeeRespForTracing extends Bundle {
  val state = UInt(2.W) // 00 start 01 end 1x err
  val pc = UInt(32.W)
  val tag  = UInt(8.W)
}



class WorkerBeeResp extends Bundle {
  val data = UInt(32.W)         // REG_READ 返回数据
  val err  = Bool()
  val laneId = UInt(2.W)
}



/** 外部 DMA 只读通道接口。方向按 WorkerBee（master）视角声明：rsp 为从外部存储读入的
  * 数据流。数据位宽按通道参数化（aDma = cfg.aExtW，bDma = cfg.bExtW），各自匹配对应
  * buffer 行宽，一拍一行等宽直连。
  */
object WorkerBeeDMAExtReadOnlyIF {
    def apply(cfg: WorkerBeeConfig, dataWidth: Int) = new WorkerBeeDMAExtReadOnlyIF(cfg, dataWidth)
}

class WorkerBeeDMAExtReadOnlyIF(cfg: WorkerBeeConfig, dataWidth: Int) extends Bundle {
  val rsp = Flipped(Stream(new Bundle {
    val data = UInt(dataWidth.W)
    val err = Bool()
  }))
  val req = Stream(new Bundle {
    val addr = UInt(cfg.addrWidth.W)
  })
}

object WorkerBeeDMAExtWriteOnlyIF {
  def apply(cfg: WorkerBeeConfig, dataWidth: Int) = new WorkerBeeDMAExtWriteOnlyIF(cfg, dataWidth)
}
class WorkerBeeDMAExtWriteOnlyIF(cfg: WorkerBeeConfig, dataWidth: Int) extends Bundle {
  val rsp = Flipped(Stream(new Bundle {
    val err = Bool()
  }))
  
  val req = Stream(new Bundle {
    val addr = UInt(cfg.addrWidth.W)
    val data = UInt(dataWidth.W)
    val strb = UInt((dataWidth/8).W)
  })
}



class WorkerBeeStatus(cfg: WorkerBeeConfig) extends Bundle {

  val busy = Bool()

  val inFlight = Bool()

  val progress = UInt(16.W)

  val aOccupancy = UInt(log2Up(cfg.aBufferDepth + 1).W)

  val bOccupancy = UInt(log2Up(cfg.bBufferDepth + 1).W)

  val cOccupancy = UInt(log2Up(cfg.cBufferDepth + 1).W)
}
object WorkerBeeStatus {
  def apply(cfg: WorkerBeeConfig) = new WorkerBeeStatus(cfg)
}

/** WorkerBee 性能观测信号组：纯组合电平、无内部计数状态，计数由外部 PMU 按拍采样。
  * dmaA/B/C、scaleA/B/C = 各 DMA 通道请求有效拍（物理通道口径，scaleA 含 bias/rqM 取数
  * 盗用拍，非 MX 配置恒 false）；array = 阵列有效计算拍；stallA = 供数相（sLOAD_A）阵列
  * 空闲拍（aPop 未 fire，含 A/scaleA 供数不足、bias/rqM 未收齐、C 写回占用读口等成因）；
  * stallB = 权重供数等待（loadW 窗口 bPop 停拍 ∨ sDRAIN 预蓄等待拍）；epilogue =
  * bias/rqM 取数 ∨ cvt 转换/量化管线占用（C 写回通道活动另由 dmaC/scaleC 观测）；
  * busy = 总忙碌电平（作分母对齐）。
  */
class WorkerBeePerf extends Bundle {
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
object WorkerBeePerf {
  def apply() = new WorkerBeePerf()
}

class WorkerBeeRegister(cfg: WorkerBeeConfig) extends Bundle {

  val m = UInt(cfg.mnkWidth.W)
  val n = UInt(cfg.mnkWidth.W)
  val k = UInt(cfg.mnkWidth.W)
  val aAddr = UInt(cfg.addrWidth.W)
  val bAddr = UInt(cfg.addrWidth.W)
  val cAddr = UInt(cfg.addrWidth.W)

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
object WorkerBeeRegister {
  def apply(cfg: WorkerBeeConfig) = new WorkerBeeRegister(cfg)
}

class WorkerBeeExePreCalcConfig(cfg: WorkerBeeConfig) extends Bundle {
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
  // cBeatAddrStep = (cDmaExtW/elemBits)×elemBytes（elemBits/elemBytes 运行期由 outFmt
  // 决定），供 WrOnly 拍内地址 = curAddr + beatCnt×cBeatAddrStep 消费（行内多拍拆分）。
  val cBeatAddrStep = UInt(cfg.addrWidth.W)
  val cDmaBufBeatTar = UInt(4.W)
  val cDmaDmaBeatTar = UInt(4.W)
  val isAX2 = Bool()
  val isAX4 = Bool()
  val isBX2 = Bool()
  val isBX4 = Bool()

  
}
object WorkerBeeExePreCalcConfig {
  def apply(cfg: WorkerBeeConfig) = new WorkerBeeExePreCalcConfig(cfg)
}

