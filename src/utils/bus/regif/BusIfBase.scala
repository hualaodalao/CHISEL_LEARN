/** SpinalHDL spinal.lib.bus.regif.BusIfBase 的 Chisel 移植版。
  *
  * 包含总线宽度 / 地址 / 写掩码等基础设施，以及 wdata/mwdata 等写操作辅助函数，
  * 和文档生成使用的 groupConsecutiveBlocks。
  */

import chisel3._
import chisel3.util._

/** UInt 按 Range 取位段的扩展（对应 SpinalHDL `x(sec)`） */
object RegIfExtract {
  implicit class UIntRangeExtract(private val x: UInt) extends AnyVal {
    def apply(sec: Range): UInt = x(sec.max, sec.min)
  }
}

trait BusIfBase {
  import RegIfExtract._

  val busDataWidth: Int
  val busAddrWidth: Int

  val askWrite: Bool
  val askRead: Bool
  val doWrite: Bool
  val doRead: Bool
  lazy val cg_en: Bool = true.B
  lazy val bus_nsbit: Bool = false.B // NS-bit (Non-Secure Access flag bit)

  val reg_wrerr: Bool
  val reg_rderr: Bool
  val reg_rdata: UInt

  val bus_rdata: UInt
  def bus_slverr: Bool = if (withSecFireWall) (reg_wrerr || reg_rderr) else reg_rderr

  val writeData: UInt
  val readSync: Boolean = true
  val withStrb: Boolean
  val withSecFireWall: Boolean

  val wstrb: Option[UInt]  // = withStrb generate (UInt(strbWidth.W))
  val wmask: UInt
  val wmaskn: UInt

  private var version = s"Chisel-RegIf"
  protected var _addrAlignCheck = true
  def setAlignCheck(value: Boolean = false): Unit = _addrAlignCheck = value
  def setVersion(ver: String): Unit = version = ver
  def getVersion: String = version

  def readAddress(): UInt
  def writeAddress(): UInt

  def readHalt(): Unit
  def writeHalt(): Unit

  def busByteWidth: Int = scala.math.ceil(this.busDataWidth / 8.0).toInt
  def bw: Int = busByteWidth

  def wordAddressInc: Int = busByteWidth
  def strbWidth: Int = busByteWidth
  def underbitWidth: Int = log2Ceil(wordAddressInc)

  def mwdata(sec: Range): UInt = if (withStrb) (writeData(sec) & wmask(sec)) else writeData(sec)

  /**
   * Gap-aware run-length encoding algorithm (Enhanced: Added block size consistency check)
   * @param blocks Input list of blocks, each block is a consecutive list of Reg
   * @return List of triples (first block, consecutive block count, interval)
   */
  def groupConsecutiveBlocks(blocks: List[List[RegSlice]]): List[(List[RegSlice], Int, Int)] = {
    if (blocks.isEmpty) return Nil
    if (blocks.size == 1) return List((blocks.head, 1, (blocks.head.last.nextAddr - blocks.head.head.addr).toInt))
    val gaps = blocks
      .sliding(2)
      .collect { case List(a, b) => (b.last.nextAddr - a.head.addr).toInt }
      .toList
    val freqGap = gaps.groupBy(identity).map(_._2).toList.sortBy(-_.size).head.head
    val L = (1 << log2Up(freqGap / bw)) * bw * 2 // Tolerance is 2 * gap
    grupConsecutiveBlocks(blocks, L)
  }

  def grupConsecutiveBlocks(blocks: List[List[RegSlice]], tolerance: Int): List[(List[RegSlice], Int, Int)] = {
    if (blocks.isEmpty) return Nil
    // Get the starting address (value of the first Reg) and size of each block
    val starts = blocks.map(block => block.head.addr)
    val sizes = blocks.map(t => (t.last.nextAddr - t.head.addr).toInt)

    var result = List.empty[(List[RegSlice], Int, Int)]
    var i = 0

    while (i < blocks.length) {
      val firstBlock = blocks(i)
      val firstSize = sizes(i)
      var currentCount = 1
      var currentGap: Option[Int] = None
      var j = i

      // Try to extend the current group
      while (j < blocks.length - 1) {
        val gapVal = (starts(j + 1) - starts(j)).toInt
        val nextSize = sizes(j + 1)

        // Check continuity conditions: interval within tolerance and same block size
        if (gapVal <= tolerance && firstSize == nextSize) {
          if (currentCount == 1) {
            // Only one block in current group, can try to establish new group
            currentGap = Some(gapVal)
            currentCount += 1
          } else {
            // Multiple blocks already in current group, check if interval is consistent
            if (gapVal == currentGap.get) {
              currentCount += 1 // Consistent interval, continue expanding group
            } else {
              // Interval inconsistent, end current group
              j = blocks.length // Break out of loop
            }
          }
        } else {
          // Interval exceeds tolerance or different block size, end current group
          j = blocks.length // Break out of loop
        }
        j += 1
      }

      // Determine output interval: use inter-block interval for multi-block groups,
      // use block size for single-block groups
      val outputGap = if (currentCount > 1) currentGap.get else firstSize
      result = result :+ ((firstBlock, currentCount, outputGap))

      // Move to next group
      i += currentCount
    }

    result
  }

  /** 初始化 wmask / wmaskn：在 adapter 构造函数中 wstrb 被驱动后调用一次 */
  def initStrbMasks(): Unit = {
    if (withStrb) {
      val strb = wstrb.get
      val ones = FillInterleaved(8, strb)
      val zeros = FillInterleaved(8, ~strb)
      wmask  := ones
      wmaskn := zeros
    } else {
      wmask  := Fill(busDataWidth, 1.U(1.W))
      wmaskn := 0.U(busDataWidth.W)
    }
  }

  def wdata(reg: Data, sec: Range): UInt = wdata(reg, sec, oper = "normal")
  def wdata(reg: Data, sec: Range, oper: String): UInt = {
    val regBits = asUIntOfWidth(reg, sec.size)
    oper match {
      case "clear" =>
        if (withStrb) regBits & wmaskn(sec) else 0.U(sec.size.W)
      case "set" =>
        if (withStrb) (regBits & wmaskn(sec)) | wmask(sec) else Fill(sec.size, 1.U(1.W))
      case "normal" =>
        if (withStrb) (regBits & wmaskn(sec)) | (writeData(sec) & wmask(sec)) else writeData(sec)
      case "toggle" =>
        if (withStrb) (regBits & wmaskn(sec)) | (~regBits & wmask(sec)) else ~regBits
      case _ => throw new Exception(s"unrecognized '${oper}'")
    }
  }

  def mwdata(pos: Int): Bool = if (withStrb) writeData(pos) & wmask(pos) else writeData(pos)
  def wdata(reg: Bool, pos: Int): Bool = wdata(reg, pos, oper = "normal")
  def wdata(reg: Bool, pos: Int, oper: String): Bool = {
    oper match {
      case "clear"  => if (withStrb) (reg & wmaskn(pos)) else false.B
      case "set"    => if (withStrb) (reg & wmaskn(pos)) |  wmask(pos)  else true.B
      case "normal" => if (withStrb) (reg & wmaskn(pos)) | (writeData(pos) & wmask(pos)) else writeData(pos)
      case "toggle" => if (withStrb) (reg & wmaskn(pos)) | (~reg & wmask(pos)) else ~reg
      case _ => throw new Exception(s"unrecognized '${oper}'")
    }
  }

  /** 把 Data 按指定位宽转成 UInt；若 Data 是 UInt/SInt/Bool 均能处理 */
  protected def asUIntOfWidth(x: Data, width: Int): UInt = {
    val raw = x match {
      case b: Bool => b.asUInt
      case d       => d.asUInt
    }
    require(raw.getWidth == width, s"asUIntOfWidth: expected $width bits but got ${raw.getWidth}")
    raw
  }
}
