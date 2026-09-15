import chisel3._
import circt.stage.ChiselStage

object Elaborate extends App {
  ChiselStage.emitSystemVerilogFile(
    gen = new MyRTL,
    args = Array("--target-dir", "generated")
  )
}

object HiveMacFpLazyElaborate extends App {
  val buildDir = "generated/macfp_lazy"
  (new java.io.File(buildDir)).mkdirs()
  ChiselStage.emitSystemVerilogFile(
    gen = new HiveMacFpLazy,
    args = Array("--target-dir", buildDir)
  )
}

// 【task#68 Phase 2 删除留痕】原 object HiveMacFpElaborate（gen = new HiveMacFp(40)，
//   发射到 generated/macfp_current）已随 HiveWorkUnit.scala 的融合重构一并删除：
//   HiveMacFp 类不复存在（其功能由 HiveFusionMacFp 内部的 FpMul*/FpAdd 承载）。
//   需要 MAC 级 RTL 发射入口请用 HiveFusionMacFpElaborate（HiveFusionMacFp.scala）。
//   上方 HiveMacFpLazyElaborate 对应独立文件 HiveMacFpLazy.scala（用户裁定禁删），保持不动。
