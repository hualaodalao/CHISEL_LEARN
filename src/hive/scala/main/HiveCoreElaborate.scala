/** HiveCore SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain HiveCoreElaborate"
  * 输出：src/hive/verilog/ 下生成 Hive_HiveCore.sv 及全部子模块
  * （Executor/DmaRdOnly/DmaWrOnly/Scratchpad/HiveComb/HiveWorker/HiveCell 等）
  *
  * cfg 与 HiveCoreSimCase 使用的配置完全一致（FP16：aW=bW=16, cW=32）
  *
  * 流程：
  *   1. 清理输出目录
  *   2. ChiselStage 生成 RTL + filelist 等
  *   3. 递归打包所有生成文件为 HiveCore_rtl.zip
  */

import chisel3._
import circt.stage.ChiselStage
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

object HiveCoreElaborate extends App {
  // 与 HiveCoreSimCase 的 HiveCoreConfig 构造参数原样一致。
  // 注意: DMA 的 bufAvailability 端口宽度由 aBufferDepth 决定（设计缺陷），
  // 因此 aBufferDepth 必须 >= cBufferDepth 以避免信号截断。
  val cfg = HiveCoreConfig(
    arrayN = 8,
    clusterM = 2,         // totalN = 16
    aBufferDepth = 128,
    bBufferDepth = 128,
    cBufferDepth = 2048,
    aW = 16,
    bW = 16,
    cW = 32
  )

  val outputDir = "src/hive/verilog"
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
    println(s"[HiveCoreElaborate] Cleaned output directory: $outputDir")
  }

  // --- Step 2: 生成 RTL ---
  ChiselStage.emitSystemVerilogFile(
     gen = withModulePrefix("Hive") { new HiveCore(cfg) }
    ,args = Array("--target-dir", outputDir)
    //,firtoolOpts = Array(
    //"-disable-all-randomization",
    //"-strip-debug-info"
    //)
  )

  println(s"[HiveCoreElaborate] RTL generation complete in $outputDir")

  // --- Step 3: 递归打包所有生成文件为 zip ---
  val outDirFile = outDirPath.toFile
  val zipName = "HiveCore_rtl.zip"
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
  println(s"[HiveCoreElaborate] Packaged ${allFiles.length} files into ${zipFile.getPath}")
}
