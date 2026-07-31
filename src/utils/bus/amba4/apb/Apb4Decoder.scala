/** SpinalHDL spinal.lib.bus.amba4.apb.Apb4Decoder / SizeMappingCheck / Apb4Hub 的 Chisel 移植版。
  *
  * Apb4Decoder：按地址映射把一个 master 分发到多个 slave（每个 slave selWidth=1）。
  * 未命中任何映射时返回 PREADY=1 + PSLVERR（若使能）。
  */

import chisel3._
import chisel3.util._
import scala.collection.mutable.ListBuffer

/** elaboration 期地址映射合法性检查：区间重叠 + 基址对齐 */
object SizeMappingCheck {
  def apply(maps: Seq[SizeMapping]): Unit = {
    def map2string(m: SizeMapping) =
      s"[0x${m.base.toString(16)} ~ 0x${(m.base + m.size).toString(16)}](${m.size} bytes)"
    val msg = new ListBuffer[String]()
    for (i1 <- maps.indices; i2 <- maps.indices if i1 != i2) {
      val m1 = maps(i1)
      val m2 = maps(i2)
      if (m1.overlap(m2)) {
        msg.append(s"[Error] address overlap: ${map2string(m2)} -OVERLAP- ${map2string(m1)}")
      }
    }
    maps.foreach { map =>
      if (map.base % map.size != 0) {
        msg.append(s"[Error] mapping at 0x${map.base.toString(16)} is not aligned to its size (0x${map.size.toString(16)} bytes)")
      }
    }
    require(msg.isEmpty, "\nAddress Overlap and Align check failed:\n" + msg.mkString("\n") + "\nCheck please!")
  }
}

object Apb4Decoder {
  def apply(mst: Apb4, maps: Seq[SizeMapping]): Vec[Apb4] = {
    SizeMappingCheck(maps)

    val c = mst.c
    val slvs = Wire(Vec(maps.size, Apb4(c.copy(selWidth = 1))))
    val decSel = Wire(Vec(maps.size, Bool()))
    decSel.suggestName("psel_dec")

    maps.zipWithIndex.foreach { case (sm, i) =>
      decSel(i)       := sm.hit(mst.PADDR) && mst.PSEL(0)
      slvs(i).PADDR   := mst.PADDR
      slvs(i).PENABLE := mst.PENABLE
      slvs(i).PSEL    := decSel(i).asUInt
      slvs(i).PWRITE  := mst.PWRITE
      slvs(i).PWDATA  := mst.PWDATA
      slvs(i).PSTRB   := mst.PSTRB
      slvs(i).PPROT   := mst.PPROT
    }

    val pselid = OHToUInt(decSel.asUInt)

    mst.PREADY := slvs(pselid).PREADY
    mst.PRDATA := slvs(pselid).PRDATA

    if (c.useSlaveError) {
      mst.PSLVERR.get := slvs(pselid).PSLVERR.get
    }

    when(mst.PSEL(0) && !decSel.asUInt.orR) {
      mst.PREADY := true.B
      if (c.useSlaveError) {
        mst.PSLVERR.get := true.B
      }
    }
    slvs
  }
}

class Apb4Hub(c: Apb4Config, maps: Seq[SizeMapping]) extends Module {
  val io = IO(new Bundle {
    val mst  = slave(Apb4(c))
    val slvs = Vec(maps.size, master(Apb4(c.copy(selWidth = 1))))
  })
  io.slvs.zip(Apb4Decoder(io.mst, maps)).foreach { case (a, b) => a << b }
}
