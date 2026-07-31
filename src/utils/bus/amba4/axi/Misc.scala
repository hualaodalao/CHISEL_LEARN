import chisel3._
import chisel3.util._

/** SpinalHDL amba4/axi/Misc.scala 的 Chisel 移植版。
  *
  * 与 SpinalHDL 的差异：
  *   - `Axi4SpecRenamer` 未移植（Chisel 的信号命名机制不同，如需 AXI 规范命名
  *     需在顶层手工声明 IO）。
  *   - `axi.ar.asInstanceOf[Stream[Axi4Ax]] <<` 的子类型直连 → 显式
  *     `arbitrationFrom` + `assignSomeByName`（自动处理 user 宽度差异）。
  */

object Axi4ToAxi4Shared {

  /** 把独立 AR/AW 通道的 Axi4 用轮询仲裁合并为共享 ARW 通道的 Axi4Shared */
  def apply(axi: Axi4): Axi4Shared = {
    val axiShared = Wire(Axi4Shared(axi.config))
    val arbiter = Module(StreamArbiterFactory().roundRobin
      .build(new Axi4Ax(axi.config, axi.config.arwUserWidth, readOnly = false), 2))

    arbiter.io.inputs(0).payload := DontCare
    arbiter.io.inputs(0).arbitrationFrom(axi.ar)
    arbiter.io.inputs(0).payload.assignSomeByName(axi.ar.payload)
    if (axi.config.useAllStrb) arbiter.io.inputs(0).payload.allStrb.get := false.B

    arbiter.io.inputs(1).payload := DontCare
    arbiter.io.inputs(1).arbitrationFrom(axi.aw)
    arbiter.io.inputs(1).payload.assignSomeByName(axi.aw.payload)

    axiShared.arw.arbitrationFrom(arbiter.io.output)
    axiShared.arw.payload.assignSomeByName(arbiter.io.output.payload)
    axiShared.arw.payload.write := arbiter.io.chosenOH(1)
    axi.w >> axiShared.w
    axi.b << axiShared.b
    axi.r << axiShared.r
    axiShared
  }
}
