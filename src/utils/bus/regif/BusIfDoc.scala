/** SpinalHDL spinal.lib.bus.regif.Doc.BusIfDoc 的 Chisel 移植版。
  *
  * 包含文档生成器公共 trait BusIfDoc，以及 RegIfDocHelper：
  *   - formatResetValue：源码位于 package object regif；
  *   - hexString：源码为 spinal.lib 的 BigIntRicher/IntRicher/LongRicher 扩展
  *     （lib.scala + binarySystem.LiteralToString.HexString），此处自写等价实现。
  *
  * 与 SpinalHDL 的差异：
  *   - GlobalData phaseContext → 移除（docPath 由 BusIf.setDocPath 管理）。
  *   - SpinalInfo → println；dump 前自动创建输出目录。
  */

import java.io.{File, PrintWriter}

object RegIfDocHelper {
  /** 复位值格式化为 verilog 风格 `<bitCount>'h<hex>`（负数转补码） */
  def formatResetValue(value: BigInt, bitCount: Int): String = {
    if (bitCount < 0) return "-"
    val hexCount = scala.math.ceil(bitCount / 4.0).toInt
    val unsignedValue = if (value >= 0) value else ((BigInt(1) << bitCount) + value)
    s"${bitCount}'h%${hexCount}s".format(unsignedValue.toString(16)).replace(' ', '0')
  }

  /** 等价 spinal.lib.binarySystem.LiteralToString.HexString */
  private def hexStringOf(value: BigInt, alignBits: Int): String = {
    if (alignBits > 0) {
      val hexCount = scala.math.ceil(alignBits / 4.0).toInt
      val unsignedValue = if (value >= 0) value else ((BigInt(1) << alignBits) + value)
      s"%${hexCount}s".format(unsignedValue.toString(16)).replace(' ', '0')
    } else {
      value.toString(16)
    }
  }

  implicit class BigIntHexRicher(private val value: BigInt) {
    def hexString(): String = hexStringOf(value, 0)
    def hexString(bitSize: Int): String = hexStringOf(value, bitSize)
  }

  implicit class IntHexRicher(private val value: Int) {
    def hexString(): String = hexStringOf(BigInt(value), 0)
    def hexString(bitSize: Int): String = hexStringOf(BigInt(value), bitSize)
  }

  implicit class LongHexRicher(private val value: Long) {
    def hexString(): String = hexStringOf(BigInt(value), 0)
    def hexString(bitSize: Int): String = hexStringOf(BigInt(value), bitSize)
  }
}

trait BusIfDoc {
  val name: String
  val suffix: String
  val prefix: String = name
  protected var bi: BusIf = null
  protected var header: String = ""

  def body(): String

  def path = s"${bi.docPath}/${name}.${suffix}"

  def setheader(str: String) = {
    header = str
    this
  }

  def setBusIf(bif: BusIf) = bi = bif

  def generate(t: BusIf) = {
    bi = t
    this.dump()
  }

  def dump(): Unit = {
    val file = new File(path)
    Option(file.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(file)
    pw.write(this.body())
    pw.close()
    println(s"[RegIf] dump ${path}")
  }

  def clean(str: String): String = {
    str.replace("\n", "\\n").replace("\r", "\\r")
  }
}
