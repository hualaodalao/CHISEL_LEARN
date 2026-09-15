/** 数据格式枚举：HiveWorker 支持的输入/计算格式（3-bit，6 值） */

import chisel3._

object DataFormat extends ChiselEnum {
  val FP32, FP16, BF16, MXE4M3, MXE5M2, MXE2M1, INT32, INT16, INT8, INT4 = Value

  /** 判断给定格式是否为 MX 系列（E4M3 或 E5M2） */
  def isMx(f: DataFormat.Type): Bool = f === MXE4M3 || f === MXE5M2 || f === MXE2M1
  def isMx8(f: DataFormat.Type): Bool = f === MXE4M3 || f === MXE5M2 
  def isMx4(f: DataFormat.Type): Bool = f === MXE2M1
  def is4B(f: DataFormat.Type): Bool = f === FP32 || f === INT32
  def is2B(f: DataFormat.Type): Bool = f === FP16 || f === BF16 
  def is1B(f: DataFormat.Type): Bool = f === INT8 || f === MXE4M3 || f === MXE5M2
  def isHalfB(f: DataFormat.Type): Bool = f === INT4 || f === MXE2M1
  def isInt(f: DataFormat.Type): Bool = f === INT8 || f === INT16 || f === INT4

  /** 每 16-bit lane 打包元素数的【纯 Scala 字面量单一真源】（MXE2M1=4、MX8=2、其余=1）。
    * 本函数只做编译期 Int 计算、不生成任何硬件（零 RTL），供两处共用：
    *   ① 下方运行期硬件版 elemsPerLane 的各 when/otherwise 分支字面量（.U 前的 Int 源）；
    *   ② HiveCoreCalcConfig.getKLoadNum 的编译期恒等性自证 require。
    * 由此「硬件字面量」与「自证口径」共用同一张表，杜绝双源漂移：改本表即同时改硬件与
    * 触发 getKLoadNum 自证（若其 totalN×2 / totalN×4 二分未同步则 elaboration 期即炸）。 */
  def elemsPerLaneLit(f: DataFormat.Type): Int =
    if (f == MXE2M1) 4 else if (f == MXE4M3 || f == MXE5M2) 2 else 1

  /** 每 16-bit lane 打包的元素数（硬件运行期解码，Phase 3 单一真源）。
    * MXFP8(E4M3/E5M2) SIMD 双元素/拍 = 2；MXFP4(E2M1) quad-lane 四元素/拍 = 4；
    * 其余 = 1。kPerBeat = totalN × 本量。各分支字面量取自 elemsPerLaneLit（纯 Scala 单一真源）。
    *
    * @param hasMx4 编译期门控（= cfg.hasMx4 = supportedFmts.contains(MXE2M1)）。
    *   false（现役 cfgBase/cfgMx 基线）：仅生成 2-bit「isMx8?2:1」逻辑，与 Phase 3
    *   之前 HiveCoreCalcConfig.elemsPerLaneOf 的内联实现【逐字同构】（同名 val ret、
    *   同 when/otherwise 结构），保证重发射 normalized-diff=0——MXE2M1 分支完全不
    *   生成硬件（elaboration 期 Scala if 展开，hasMx4=false 时 isMx4 逻辑不进入 IR）。
    *   true（未来 fp4 配置）：扩为 3-bit，追加 isMx4→4 分支（4 需 3 bit 表示）。
    * HiveCoreCalcConfig.elemsPerLaneOf 与 HiveCoreExecutor2 kTile 分母共用本定义
    *   （勿另立裸常数），确保几何/执行两侧 elemsPerLane 口径唯一。 */
  def elemsPerLane(f: DataFormat.Type, hasMx4: Boolean = false): UInt = {
    if (hasMx4) {
      val ret = Wire(UInt(3.W))
      when(isMx4(f)){
        ret := elemsPerLaneLit(MXE2M1).U
      }.elsewhen(isMx8(f)){
        ret := elemsPerLaneLit(MXE4M3).U
      }.otherwise{
        ret := elemsPerLaneLit(FP32).U
      }
      ret
    } else {
      val ret = Wire(UInt(2.W))
      when(isMx8(f)){
        ret := elemsPerLaneLit(MXE4M3).U
      }.otherwise{
        ret := elemsPerLaneLit(FP32).U
      }
      ret
    }
  }
}

/** MX 量化格式数值参数表（HiveOutCvt blockScale/quantElem 共用字段，
  * 三格式单例逐条镜像既有内联字面量，bit-exact 来源）：
  *   - blockScale 消费 (maxCodeExp, bias, maxMant23)；
  *   - quantElem 消费 (expW, mantW, bias, maxNormalExp=maxCodeExp, satCode,
  *     nanCode, nanToSat, fixNanCode)——三格式上 maxNormalExp 与 blockScale 的
  *     maxCodeExp 取值恰好一致（E4M3=15 / E5M2=30 / E2M1=3），故共用单一字段。
  */
case class MxFormatParams(
  expW: Int,          // 指数域位宽
  mantW: Int,         // 尾数域位宽
  bias: Int,          // 偏置
  maxCodeExp: Int,    // 正常数最大指数码（quantElem 的 maxNormalExp 同值共用）
  satCode: Int,       // 饱和（最大有限值）编码（不含符号位）
  nanCode: Int,       // nan 映射编码（不含符号位）
  nanToSat: Boolean,  // nan 输入饱和处理（E2M1 无 nan 编码的裁定）
  fixNanCode: Boolean, // 舍入落入 NaN 编码时改写为饱和（仅 E4M3）
  maxMant23: BigInt   // maxMantVal 尾数左对齐到 23 位（blockScale needBump 判定）
)
object MxFormatParams {
  /** mxfp8 E4M3：maxFinite=448=1.75×2^8，饱和 0x7E、NaN=(15,7)=0x7F、
    * maxMantVal=1.75→尾数 0.75→0x600000 */
  val E4M3 = MxFormatParams(expW = 4, mantW = 3, bias = 7, maxCodeExp = 15,
    satCode = 0x7E, nanCode = 0x7F, nanToSat = false, fixNanCode = true,
    maxMant23 = BigInt("600000", 16))
  /** mxfp8 E5M2：maxFinite=57344=1.75×2^15，饱和 0x7B、NaN=(31,2)=0x7E
    * （规范尾数同 fp16Nan 取法） */
  val E5M2 = MxFormatParams(expW = 5, mantW = 2, bias = 15, maxCodeExp = 30,
    satCode = 0x7B, nanCode = 0x7E, nanToSat = false, fixNanCode = false,
    maxMant23 = BigInt("600000", 16))
  /** mxfp4 E2M1：maxFinite=6.0=1.5×2^2，饱和 0x7、无 nan 编码→饱和裁定、
    * maxMantVal=1.5→尾数 0.5→0x400000 */
  val E2M1 = MxFormatParams(expW = 2, mantW = 1, bias = 1, maxCodeExp = 3,
    satCode = 0x7, nanCode = 0x7, nanToSat = true, fixNanCode = false,
    maxMant23 = BigInt("400000", 16))
}

/** IEEE-754 舍入模式 */
object RoundingMode extends ChiselEnum {
  val RNE,  // Round to Nearest, ties to Even
      RTZ,  // Round Toward Zero
      RDN,  // Round Down (toward -∞)
      RUP,  // Round Up (toward +∞)
      RMM   // Round to Nearest, ties to Max Magnitude
      = Value
}
// ============================================================================
// 操作码枚举（仅 3 种命令）
// ============================================================================

/** HiveCore 操作码：寄存器写、执行、寄存器读。 */
object HiveCoreOp extends ChiselEnum {
  val REG_WRITE1, REG_WRITE2, EXECUTE, REG_READ = Value
}

/** 循环顺序枚举：loopMode 位域已被 aFmt/bFmt/mixFmtEn/outFmt 重新占用，
  * 本枚举仅防历史引用编译断裂（结构性重构后 Executor 固定 weight-stationary
  * N→K→M 遍历，C 写回顺序 = push FIFO 序，均与 loopMode 无关）。
  */
object HiveCoreLoopMode extends ChiselEnum {
  val MKN = Value
  val TILED = Value
}

/*内存读取方式：
MAT：大矩阵存储（原始大矩阵，hive dma自行按行跳地址取tile）
TILE：Tile连续存储（经过DMA进行Tile切分，地址连续）
*/
object AddrMode extends ChiselEnum {
  val MAT, TILE = Value
}

object MXBlockSize extends ChiselEnum{
  val M16 = Value(0.U)
  val M32 = Value(1.U)
}