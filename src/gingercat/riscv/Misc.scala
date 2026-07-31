import chisel3._
import chisel3.util.BitPat

/** SpinalHDL 兼容层 */

/** 对应 SpinalHDL 的 Nameable（简化版，仅存/取名字） */
trait Nameable {
  private var _name: String = ""
  def setName(name: String): this.type = { _name = name; this }
  def getName(default: String = ""): String = if (_name.nonEmpty) _name else default
}

/** 对应 SpinalHDL 的 AreaObject（原用于自动命名，无硬件语义，占位） */
trait AreaObject

object Misc {

  /** SpinalHDL 掩码字面量语法 `M"0101----"` → `BitPat("b0101????")`。
    * '-' 表示无关位，'_' 仅作分隔符。使用处需 `import Misc._`。
    */
  implicit class MaskedLiteralInterpolator(val sc: StringContext) {
    def M(args: Any*): BitPat =
      BitPat("b" + sc.parts.mkString.replace("_", "").replace("-", "?"))
  }

  /** SpinalHDL 的 `31 downto 25` 语法 → 递减 Range */
  implicit class RichIntDownto(val hi: Int) extends AnyVal {
    def downto(lo: Int): Range = Range.inclusive(hi, lo, -1)
  }

  /** 用 Range 抽取位段：`inst(31 downto 20)` → `inst(31, 20)` */
  implicit class UIntRangeAccess(val value: UInt) extends AnyVal {
    def apply(r: Range): UInt = value(r.max, r.min)
  }
}
