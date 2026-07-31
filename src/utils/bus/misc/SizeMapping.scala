/** SpinalHDL spinal.lib.bus.misc.SizeMapping 的最小移植版。
  *
  * 描述一段地址空间 [base, base + size)，提供地址命中判断（硬件）
  * 与区间重叠检查（elaboration 期）。
  * 未移植：AddressMapping 层次（MaskMapping/OrMapping 等）、AddressTransformer。
  */

import chisel3._
import chisel3.util._

case class SizeMapping(base: BigInt, size: BigInt) {

  require(size >= 0, "SizeMapping size must not be negative")

  def end: BigInt = base + size - 1

  /** 最高地址位宽（用作 id/sink 等编号域宽度，对应 SpinalHDL AddressMapping.width） */
  def width: Int = (base + size - 1).bitLength

  def lowerBound: BigInt = base

  /** elaboration 期命中判断 */
  def hit(value: BigInt): Boolean = value >= base && value < base + size

  /** 整体偏移（对应 SpinalHDL AddressMapping.withOffset） */
  def withOffset(offset: BigInt): SizeMapping = copy(base = base + offset)

  /** 地址是否落在 [base, base + size) 内。
    * 若 size 为 2 的幂且 base 对齐，则只比较高位（无进位链）。
    */
  def hit(address: UInt): Bool = {
    if (size == 0) {
      false.B
    } else if (isPow2(size) && base % size == 0) {
      val lowBits = log2Up(size)
      if (lowBits >= address.getWidth) {
        // 整个可寻址空间都被该映射覆盖（或 base 超出可寻址范围）
        ((base >> address.getWidth) == 0).B
      } else {
        address(address.getWidth - 1, lowBits) === (base >> lowBits).U
      }
    } else {
      address >= base.U && address < (base + size).U
    }
  }

  /** 去掉基址偏移后的局部地址 */
  def removeOffset(address: UInt): UInt = {
    if (isPow2(size) && base % size == 0)
      address & (size - 1).U(address.getWidth.W)
    else
      address - base.U
  }

  /** 两段映射是否重叠（elaboration 期检查用） */
  def overlap(that: SizeMapping): Boolean =
    this.base < that.base + that.size && that.base < this.base + this.size
}
