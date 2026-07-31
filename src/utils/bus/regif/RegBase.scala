/** SpinalHDL spinal.lib.bus.regif.RegBase 的 Chisel 移植版。
  *
  * 提供寄存器级公共逻辑：地址命中、readBits 拼接、以及各 AccessType 的保护写方法。
  *
  * 与 SpinalHDL 的差异：
  *   - Chisel 不支持对 UInt 寄存器按位赋值 `reg(i) := x`，因此 _WB/_WBR/_WBP/_W1I
  *     等位级方法全部改为向量化布尔运算（语义与源码逐位版本一致，含 strb 交互）：
  *       next = (cond & opResult) | (~cond & regBits)
  *     其中 cond = mwdata(sec)（W1x）或 ~mwdata(sec)（W0x），
  *     opResult = busif.wdata(reg, sec, oper)。
  *   - 已删除源码中 deprecated 的 BitCount 版本（RO/W1/W/RC/... 无下划线方法）。
  */

import chisel3._
import chisel3.util._

abstract class RegBase(name: String, addr: BigInt, doc: String, val busif: BusIf,
                       sec: Secure = null, grp: GrpTag = null)
    extends RegSlice(name, addr, doc, size = busif.wordAddressInc, sec = sec, grp = grp)(busif) {
  import RegIfExtract._
  import AccessType._

  def setName(name: String): RegBase

  def readErrorTag = Rerror

  val hitDoRead: Bool = rdSecurePassage(busif.readAddress() === addr.U && busif.doRead)
    .suggestName(f"read_hit_0x$addr%04x")
  val hitDoWrite: Bool = wrSecurePassage(busif.writeAddress() === addr.U && busif.doWrite)
    .suggestName(f"write_hit_0x$addr%04x")

  def haveWO: Boolean = fields.exists(_.isWriteOnly())

  def readBits(): UInt = {
    this.checkLast
    // WriteOnly 字段读回 0（安全考虑）；fields 低位在前，Cat 需高位在前
    val parts = fields.map(t => if (t.isWriteOnly()) 0.U(t.getWidth().W) else t.hardbit.asUInt).toSeq
    Cat(parts.reverse)
  }

  def eventR(): Bool = RegNext(hitDoRead, init = false.B)
  def eventW(): Bool = RegNext(hitDoWrite, init = false.B)

  protected def _RO[T <: Data](reg: T): T = reg

  protected def _W1[T <: Data](reg: T, section: Range): T = {
    val hardRestFirstFlag = RegInit(true.B).suggestName(s"${getName()}_w1lock_flag")
    when(hitDoWrite && hardRestFirstFlag) {
      reg := busif.wdata(reg, section).asTypeOf(reg)
      hardRestFirstFlag := false.B
    }
    reg
  }

  protected def _W[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section).asTypeOf(reg)
    }
    reg
  }

  protected def _RC[T <: Data](reg: T, section: Range): T = {
    when(hitDoRead) {
      reg := 0.U(section.size.W).asTypeOf(reg)
    }
    reg
  }

  protected def _RS[T <: Data](reg: T, section: Range): T = {
    when(hitDoRead) {
      reg := Fill(section.size, 1.U(1.W)).asTypeOf(reg)
    }
    reg
  }

  protected def _WRC[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section).asTypeOf(reg)
    }.elsewhen(hitDoRead) {
      reg := 0.U(section.size.W).asTypeOf(reg)
    }
    reg
  }

  protected def _WRS[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section).asTypeOf(reg)
    }.elsewhen(hitDoRead) {
      reg := Fill(section.size, 1.U(1.W)).asTypeOf(reg)
    }
    reg
  }

  protected def _WC[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section, "clear").asTypeOf(reg)
    }
    reg
  }

  protected def _WS[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section, "set").asTypeOf(reg)
    }
    reg
  }

  protected def _WSRC[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section, "set").asTypeOf(reg)
    }.elsewhen(hitDoRead) {
      reg := 0.U(section.size.W).asTypeOf(reg)
    }
    reg
  }

  protected def _WCRS[T <: Data](reg: T, section: Range): T = {
    when(hitDoWrite) {
      reg := busif.wdata(reg, section, "clear").asTypeOf(reg)
    }.elsewhen(hitDoRead) {
      reg := Fill(section.size, 1.U(1.W)).asTypeOf(reg)
    }
    reg
  }

  /** W1C/W1S/W1T/W0C/W0S/W0T：仅写数据（经 strb 掩码）匹配的位执行 clear/set/toggle */
  protected def _WB[T <: Data](reg: T, section: Range, accType: AccessType): T = {
    val mw = busif.mwdata(section)
    val (cond, oper) = accType match {
      case W1C => (mw, "clear")
      case W1S => (mw, "set")
      case W1T => (mw, "toggle")
      case W0C => (~mw, "clear")
      case W0S => (~mw, "set")
      case W0T => (~mw, "toggle")
      case _   => throw new Exception(s"_WB unsupported AccessType: ${accType}")
    }
    val opResult = busif.wdata(reg, section, oper)
    when(hitDoWrite) {
      reg := ((cond & opResult) | (~cond & reg.asUInt)).asTypeOf(reg)
    }
    reg
  }

  /** W1SRC/W1CRS/W0SRC/W0CRS：写按位 set/clear，读全清/全置（逐位优先级与源码一致） */
  protected def _WBR[T <: Data](reg: T, section: Range, accType: AccessType): T = {
    val mw = busif.mwdata(section)
    val (cond, oper, rdClear) = accType match {
      case W1SRC => (mw, "set", true)
      case W1CRS => (mw, "clear", false)
      case W0SRC => (~mw, "set", true)
      case W0CRS => (~mw, "clear", false)
      case _     => throw new Exception(s"_WBR unsupported AccessType: ${accType}")
    }
    val opResult = busif.wdata(reg, section, oper)
    val rdval = if (rdClear) 0.U(section.size.W) else Fill(section.size, 1.U(1.W))
    when(hitDoWrite || hitDoRead) {
      // 逐位语义：写选中的位执行 op；其余位在读命中时取 rdval，否则保持
      val wsel = Mux(hitDoWrite, cond, 0.U(section.size.W))
      val other = Mux(hitDoRead, rdval, reg.asUInt)
      reg := ((wsel & opResult) | (~wsel & other)).asTypeOf(reg)
    }
    reg
  }

  /** W1P/W0P：写匹配位产生单拍脉冲（toggle），其余时刻为 0 */
  protected def _WBP[T <: Data](reg: T, section: Range, accType: AccessType): T = {
    val mw = busif.mwdata(section)
    val cond = accType match {
      case W1P => mw
      case W0P => ~mw
      case _   => throw new Exception(s"_WBP unsupported AccessType: ${accType}")
    }
    val opResult = busif.wdata(reg, section, "toggle")
    when(hitDoWrite) {
      reg := (cond & opResult).asTypeOf(reg)
    }.otherwise {
      reg := 0.U(section.size.W).asTypeOf(reg)
    }
    reg
  }

  /** W1I：组合脉冲输出（不支持 strb 写） */
  protected def _W1I[T <: Data](wire: T, section: Range): T = {
    wire := Mux(hitDoWrite, busif.writeData(section), 0.U(section.size.W)).asTypeOf(wire)
    wire
  }
}
