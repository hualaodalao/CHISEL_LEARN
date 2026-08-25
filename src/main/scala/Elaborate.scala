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

object HiveMacFpElaborate extends App {
  val buildDir = "generated/macfp_current"
  (new java.io.File(buildDir)).mkdirs()
  ChiselStage.emitSystemVerilogFile(
    gen = new HiveMacFp(40),
    args = Array("--target-dir", buildDir)
  )
}
