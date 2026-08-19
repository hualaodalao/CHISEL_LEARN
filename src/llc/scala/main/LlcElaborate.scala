/** LLC (Last Level Cache) SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain LlcElaborate"
  * 输出：src/llc/verilog/ 下生成 Llc.sv 及全部子模块
  * （LlcPipeline / LlcWbBuffer / MSHR 等）
  *
  * cfg 使用 LlcConfig 默认配置（256KB / 8-way / 256-bit bus）
  *
  * 流程：
  *   1. 清理输出目录
  *   2. ChiselStage 生成 RTL + filelist 等
  *   3. 递归打包所有生成文件为 Llc_rtl.zip
  */

import chisel3._
import _root_.circt.stage.ChiselStage
import java.nio.file.Files
import java.nio.file.Path
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import scala.collection.JavaConverters._

object LlcElaborate extends App {
  val cfg = LlcConfig()

  val outputDir = "src/llc/verilog"
  val outDirPath = Path.of(outputDir)

  // --- Step 1: 清理输出目录 ---
  def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        Files.list(path).iterator().asScala.foreach(deleteRecursively)
      }
      Files.delete(path)
    }
  }

  if (Files.exists(outDirPath)) {
    // 只删除目录内容，保留目录本身
    Files.list(outDirPath).iterator().asScala.foreach(deleteRecursively)
    println(s"[LlcElaborate] Cleaned output directory: $outputDir")
  }

  // --- Step 2: 生成 RTL ---
  ChiselStage.emitSystemVerilogFile(
     gen = new Llc(cfg)
    ,args = Array("--target-dir", outputDir)
  )

  println(s"[LlcElaborate] RTL generation complete in $outputDir")

  // --- Step 3: 递归打包所有生成文件为 zip ---
  val outDirFile = outDirPath.toFile
  val zipName = "Llc_rtl.zip"
  val zipFile = new File(outDirFile, zipName)

  def collectFiles(dir: File): Seq[File] = {
    dir.listFiles().flatMap { f =>
      if (f.isDirectory) collectFiles(f)
      else Seq(f)
    }.toSeq
  }

  val allFiles = collectFiles(outDirFile).filterNot(_.getName == zipName)

  val zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))
  allFiles.foreach { f =>
    // 使用相对路径作为 zip entry 名称
    val relativePath = outDirPath.relativize(f.toPath).toString
    val entry = new ZipEntry(relativePath)
    zipOut.putNextEntry(entry)
    val in = new BufferedInputStream(new FileInputStream(f))
    val buf = new Array[Byte](4096)
    var len = in.read(buf)
    while (len > 0) {
      zipOut.write(buf, 0, len)
      len = in.read(buf)
    }
    in.close()
    zipOut.closeEntry()
  }
  zipOut.close()
  println(s"[LlcElaborate] Packaged ${allFiles.length} files into ${zipFile.getPath}")
}
