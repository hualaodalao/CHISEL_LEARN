/** ForagerBee SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain ForagerBeeElaborate"
  * 输出：src/foragerbee/verilog/ 下生成 Fb_ForagerBee.sv 及全部子模块
  *
  * 流程：
  *   1. 清理输出目录
  *   2. ChiselStage 生成 RTL + filelist 等
  *   3. 递归打包所有生成文件为 ForagerBee_rtl.zip
  */

import chisel3._
import circt.stage.ChiselStage

import java.io.{File, FileOutputStream, BufferedOutputStream}
import java.io.{FileInputStream, BufferedInputStream}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipOutputStream, ZipEntry}
import scala.collection.JavaConverters._

object ForagerBeeElaborate extends App {

  val cfg = ForagerBeeConfig(
    numPorts = 2,
    queueDepth = 4,
    maxDims = 4,
    tileSize = 16,
    maxBurstLen = 64,
    dataWidth = 256,
    addressWidth = 32,
    channelTranspose = Seq(true, false),
    channelPermute = Seq(true, true),
    enableChaining = true,
    enableConversion = true,
    converterPipeStages = 1,
    channelIm2col = Seq(false, true),
    channelScatterGather = Seq(true, true)
  )

  val outputDir = "src/foragerbee/verilog"
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
    println(s"[ForagerBeeElaborate] Cleaned output directory: $outputDir")
  }

  // --- Step 2: 生成 RTL ---
  ChiselStage.emitSystemVerilogFile(
     gen = withModulePrefix("Fb") { new ForagerBee(cfg) }
    ,args = Array("--target-dir", outputDir)
  )

  println(s"[ForagerBeeElaborate] RTL generation complete in $outputDir")

  // --- Step 3: 递归打包所有生成文件为 zip ---
  val outDirFile = outDirPath.toFile
  val zipFile = new File(outDirFile, "ForagerBee_rtl.zip")

  def collectFiles(dir: File): Seq[File] = {
    dir.listFiles().flatMap { f =>
      if (f.isDirectory) collectFiles(f)
      else Seq(f)
    }.toSeq
  }

  val allFiles = collectFiles(outDirFile).filterNot(_.getName == "ForagerBee_rtl.zip")

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
  println(s"[ForagerBeeElaborate] Packaged ${allFiles.length} files into ${zipFile.getPath}")
}
