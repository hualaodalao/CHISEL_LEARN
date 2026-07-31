import chisel3._
import chisel3.util._

/** NaxRiscv regfile Service.scala 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - `withReady generate Input(Bool())` → `Option[Bool]`（Scala 层可选字段）。
  *   - `Bits` → `UInt`。
  *   - `RegFileIo` 中端口的 withReady 可能不同（Bundle 类型不同），故用 `MixedVec`。
  *   - `RegfileService.elaborationLock`（SpinalHDL Retainer）无对应机制，删除。
  *   - `RegFileWriter` 原用 NaxRiscv 全局配置 `Global.HART_ID()/Decode.UOP_ID()`，
  *     改为显式传宽度参数。
  */

case class RegFilePortParam(addressWidth: Int,
                            dataWidth: Int,
                            hartIdWidth: Int,
                            uopIdWidth: Int)

case class RegFileReadParameter(withReady: Boolean)
case class RegFileWriteParameter(withReady: Boolean)

class RegFileIo(rfpp: RegFilePortParam,
                readsParameter: scala.collection.Seq[RegFileReadParameter],
                writesParameter: scala.collection.Seq[RegFileWriteParameter]) extends Bundle {
  val writes = MixedVec(writesParameter.map(p => slave(RegFileWrite(rfpp, p.withReady))).toSeq)
  val reads = MixedVec(readsParameter.map(p => slave(RegFileRead(rfpp, p.withReady))).toSeq)
}

object RegFileIo {
  def apply(rfpp: RegFilePortParam,
            readsParameter: scala.collection.Seq[RegFileReadParameter],
            writesParameter: scala.collection.Seq[RegFileWriteParameter]) =
    new RegFileIo(rfpp, readsParameter, writesParameter)
}

/** 写端口（master 视角）。withReady 时带反压。 */
class RegFileWrite(val rfpp: RegFilePortParam, val withReady: Boolean) extends Bundle with IMasterSlave {
  import rfpp._
  val valid = Output(Bool())
  val ready = if (withReady) Some(Input(Bool())) else None
  val address = Output(UInt(addressWidth.W))
  val data = Output(UInt(dataWidth.W))
  val hartId = Output(UInt(hartIdWidth.W))
  val uopId = Output(UInt(uopIdWidth.W)) // Used for traces and debug

  def fire: Bool = if (withReady) valid && ready.get else valid

  def asWithoutReady(): RegFileWrite = {
    val ret = Wire(RegFileWrite(rfpp, false))
    ret.valid := this.fire
    ret.address := this.address
    ret.data := this.data
    ret.hartId := this.hartId
    ret.uopId := this.uopId
    ret
  }
}

object RegFileWrite {
  def apply(rfpp: RegFilePortParam, withReady: Boolean) = new RegFileWrite(rfpp, withReady)
}

/** 读端口（master 视角）：发地址，收数据。 */
class RegFileRead(val rfpp: RegFilePortParam, val withReady: Boolean) extends Bundle with IMasterSlave {
  import rfpp._
  val valid = Output(Bool())
  val ready = if (withReady) Some(Input(Bool())) else None
  val address = Output(UInt(addressWidth.W))
  val data = Input(UInt(dataWidth.W))
}

object RegFileRead {
  def apply(rfpp: RegFilePortParam, withReady: Boolean) = new RegFileRead(rfpp, withReady)
}

/**
 * Provide an API which allows to create new read/write ports to a given register file.
 */
trait RegfileService {
  def rfSpec: RegfileSpec
  def getPhysicalDepth: Int

  def writeLatency: Int
  def readLatency: Int

  def newRead(withReady: Boolean): RegFileRead
  def newWrite(withReady: Boolean, sharingKey: Any = null, priority: Int = 0): RegFileWrite

  def getWrites(): scala.collection.Seq[RegFileWrite] // Used in the hardware simulation to probe all the register writes of the CPU.
}

/** 寄存器堆写记录（用于 trace/debug 探针） */
class RegFileWriter(val rfSpec: RegfileSpec, hartIdWidth: Int, uopIdWidth: Int) extends Bundle {
  val hartId = UInt(hartIdWidth.W)
  val uopId = UInt(uopIdWidth.W)
  val data = UInt(rfSpec.width.W)
}

trait RegFileWriterService {
  def getRegFileWriters(): Seq[Flow[RegFileWriter]]
}
