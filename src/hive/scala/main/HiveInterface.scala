
import chisel3._
import chisel3.util._
import chisel3.dontTouch

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
  // 【P0 epilogue-1 fused bias】reg12 = bias 基地址（0x0C）；reg5 bit30 = biasOn
  //   显式使能（写侧见 wregs is(5.U) 的 d(30)，读侧见 rregs is(5.U) Cat 的 bit30）。
  val biasOn = Bool()
  val biasAddr = UInt(cfg.addrWidth.W)
  // 【P0 epilogue-2 fused requant】模式位归控制字：reg5 bit29 = requantOn、bit15 = reluOn
  //   （reluOn 不耦合 INT/浮点格式选择，与 biasOn 同为模式位）。参数归参数寄存器：
  //   reg13(0x0D) = rqMBase（per-channel int32 乘子向量基址，N 轴同构 bias）；
  //   reg14(0x0E) = [31:16]zp(int16 per-tensor) + [15:6] reserved 0 + [5:0]shift(uint6 per-tensor)。
  val requantOn = Bool()
  val reluOn = Bool()
  val rqMBase = UInt(cfg.addrWidth.W)
  val rqShift = UInt(6.W)
  val rqZp = SInt(16.W)

  def wregs(addr: UInt, d: UInt) = {
    switch(addr){
      is(0.U) { m := d(15,0) }
      is(1.U) { n := d(15,0); k := d(31,16) }
      is(2.U) { aAddr := d }
      is(3.U) { bAddr := d }
      is(4.U) { cAddr := d }
      is(5.U) {
        biasOn := d(30)
        requantOn := d(29)
        loadWMode := d(28)
        cMxBlockSize := d(26).asTypeOf(MXBlockSize())
        bMxBlockSize := d(24).asTypeOf(MXBlockSize())
        aMxBlockSize := d(22).asTypeOf(MXBlockSize())
        warmup       := d(21)
        warmdown     := d(20)
        rnd          := d(18,16).asTypeOf(RoundingMode())
        reluOn       := d(15)
        cAddrMode := d(14).asTypeOf(AddrMode())
        bAddrMode := d(13).asTypeOf(AddrMode())
        aAddrMode := d(12).asTypeOf(AddrMode())
        cFmt := d(11, 8).asTypeOf(DataFormat())
        bFmt := d(7, 4).asTypeOf(DataFormat())
        aFmt := d(3, 0).asTypeOf(DataFormat())
      }
      is(8.U) { }
      is(9.U) { aScaleAddr := d }
      is(10.U) { bScaleAddr := d }
      is(11.U) { cScaleAddr  := d }
      is(12.U) { biasAddr    := d }
      is(13.U) { rqMBase     := d }
      is(14.U) { rqZp := d(31,16).asSInt; rqShift := d(5,0) }
    }
  }

  def rregs(addr: UInt) : UInt = {
    val ret = Wire(UInt(32.W))
    ret := 0.U
    switch(addr) {
      is(0.U) { ret := Cat(0.U(16.W),m) }
      is(1.U) { ret := Cat(k,n) }
      is(2.U) { ret := aAddr }
      is(3.U) { ret := bAddr }
      is(4.U) { ret := cAddr }
      is(5.U) { ret := Cat(0.U(1.W), biasOn.asUInt,
      requantOn.asUInt, loadWMode.asUInt,
      0.U(1.W),cMxBlockSize.asUInt,
      0.U(1.W),bMxBlockSize.asUInt,
      0.U(1.W),aMxBlockSize.asUInt,
      warmup.asUInt,
      warmdown.asUInt,
      0.U(1.W), rnd.asUInt,
      reluOn.asUInt, cAddrMode.asUInt, bAddrMode.asUInt,aAddrMode.asUInt,
      cFmt.asUInt, bFmt.asUInt, aFmt.asUInt) }  // 【#120 C1】间隙 8→7，总位宽 32-bit，严格镜像 wregs 位域；【P0 epilogue-1】bit30=biasOn；【P0 epilogue-2】bit29=requantOn、bit15=reluOn（两处取代原 0 填充，位宽不变）
      is(8.U) { ret := 0.U }
      is(9.U) { ret := aScaleAddr }
      is(10.U) { ret := bScaleAddr }
      is(11.U) { ret := cScaleAddr }
      is(12.U) { ret := biasAddr }
      is(13.U) { ret := rqMBase }
      is(14.U) { ret := Cat(rqZp.asUInt, 0.U(10.W), rqShift) }  // [31:16]zp + [15:6]0 + [5:0]shift，镜像 wregs is(14.U)
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

