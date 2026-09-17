
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters._


object QueenElaborate extends App {
 
  def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        Files.list(path).iterator().asScala.foreach(deleteRecursively)
      }
      Files.delete(path)
    }
  }
  def emitClean(outputDir: String): Unit = {
    val outDirPath = Path.of(outputDir)
    Files.createDirectories(outDirPath)
    // 只删除目录内容，保留目录本身
    Files.list(outDirPath).iterator().asScala.foreach(deleteRecursively)
    println(s"[QueenElaborate] Cleaned output directory: $outputDir")
  }
  private def emit(gen: => Module, dir: String, label: String): Unit = {
    emitClean(dir)
    ChiselStage.emitSystemVerilogFile(
       gen = withModulePrefix("Queen") { gen }
      ,args = Array("--target-dir", dir)
    )
    println(s"[QueenElaborate] $label -> $dir")
  }

  
  emit(new Queen(QueenConfig()), "src/queen/verilog",
       "default config")

}

