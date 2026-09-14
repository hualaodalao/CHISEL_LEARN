
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

  val m = UInt(cfg.mnkWidth.W)
  val n = UInt(cfg.mnkWidth.W)
  val k = UInt(cfg.mnkWidth.W)
  val aAddr = UInt(cfg.addrWidth.W)
  val bAddr = UInt(cfg.addrWidth.W)
  val cAddr = UInt(cfg.addrWidth.W)

  val aStride = UInt(4.W)
  val bStride = UInt(4.W)
  val cStride = UInt(4.W)
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
  

  def wregs(addr: UInt, d: UInt) = {
    switch(addr){
      is(0.U) { m := d }
      is(1.U) { n := d }
      is(2.U) { k := d }
      is(3.U) { aAddr := d }
      is(4.U) { bAddr := d }
      is(5.U) { cAddr := d }
      is(6.U) { 
        loadWMode := d(28)
        cMxBlockSize := d(26).asTypeOf(MXBlockSize())
        bMxBlockSize := d(24).asTypeOf(MXBlockSize())
        aMxBlockSize := d(22).asTypeOf(MXBlockSize())
        cAddrMode := d(14).asTypeOf(AddrMode())
        bAddrMode := d(13).asTypeOf(AddrMode())
        aAddrMode := d(12).asTypeOf(AddrMode())
        cStride := d(11, 8)        
        bStride := d(7, 4)
        aStride := d(3, 0)
        }
      is(7.U) {
        rnd := d(14, 12).asTypeOf(RoundingMode())   // 【#120 C2】3-bit：bits[14:12]，支持全 5 种舍入（RNE/RTZ/RDN/RUP/RMM）
        cFmt := d(11, 8).asTypeOf(DataFormat())
        bFmt := d(7, 4).asTypeOf(DataFormat())
        aFmt := d(3, 0).asTypeOf(DataFormat())
      }
      is(8.U) { }
      is(9.U) { aScaleAddr := d }
      is(10.U) { bScaleAddr := d }
      is(11.U) { cScaleAddr  := d }
    }
  }

  def rregs(addr: UInt) : UInt = {
    val ret = Wire(UInt(32.W))
    ret := 0.U
    switch(addr) {
      is(0.U) { ret := m }
      is(1.U) { ret := n }
      is(2.U) { ret := k }
      is(3.U) { ret := aAddr }
      is(4.U) { ret := bAddr }
      is(5.U) { ret := cAddr }
      is(6.U) { ret := Cat(0.U(3.W), loadWMode.asUInt,0.U(1.W),cMxBlockSize.asUInt, 0.U(1.W),bMxBlockSize.asUInt, 0.U(1.W),aMxBlockSize.asUInt, 0.U(7.W),cAddrMode.asUInt, bAddrMode.asUInt,aAddrMode.asUInt, cStride, bStride, aStride) }  // 【#120 C1】间隙 8→7，总位宽 32-bit，严格镜像 wregs 位域
      
      is(7.U) {
        ret := Cat(0.U(17.W), rnd.asUInt, cFmt.asUInt, bFmt.asUInt, aFmt.asUInt)  // 【#120 C2】17+3+4+4+4=32，rnd 占 bits[14:12]
       }
      is(8.U) { ret := 0.U }
      is(9.U) { ret := aScaleAddr }
      is(10.U) { ret := bScaleAddr }
      is(11.U) { ret := cScaleAddr }
    }
    ret
  }
  
  

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

