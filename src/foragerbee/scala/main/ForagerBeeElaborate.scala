/** ForagerBee SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain ForagerBeeElaborate"
  * 输出：
  *   - 基线配置（写端口 1:1 直连）：src/foragerbee/verilog/ 下生成 Fb_ForagerBee.sv 及全部子模块，
  *     打包为 ForagerBee_rtl.zip
  *   - 写端口共享仲裁配置（enableSharedWritePorts=true）：src/foragerbee/verilog_wrshared/，
  *     打包为 ForagerBee_wrshared_rtl.zip
  *   - 非阻塞写配置（writeOutstandingDepth=4）：src/foragerbee/verilog_nbwr/，
  *     打包为 ForagerBee_nbwr_rtl.zip
  *   - 非阻塞写 + 共享写端口配置：src/foragerbee/verilog_nbwr_shared/，
  *     打包为 ForagerBee_nbwr_shared_rtl.zip
  *
  * 流程（每套配置）：
  *   1. 清理输出目录
  *   2. ChiselStage 生成 RTL + filelist 等
  *   3. 递归打包所有生成文件为 zip
  */

import chisel3._
import circt.stage.ChiselStage

import java.io.{File, FileOutputStream, BufferedOutputStream}
import java.io.{FileInputStream, BufferedInputStream}
import java.nio.file.{Files, Path}
import java.util.zip.{ZipOutputStream, ZipEntry}
import scala.collection.JavaConverters._

object ForagerBeeElaborate extends App {

  // 基线配置：写端口 1:1 直连（enableSharedWritePorts 默认关闭）
  val cfg = ForagerBeeConfig(
    numPorts = 2,
    queueDepth = 4,
    maxDims = 5,
    tileSize = 16,
    maxBurstLen = 64,
    dataWidth = 256,
    addressWidth = 32,
    channelTranspose = Seq(true, false),
    channelPermute = Seq(true, true),
    enableConversion = true,
    converterPipeStages = 1,
    channelIm2col = Seq(false, true),
    channelScatterGather = Seq(true, true)
  )

  // 写端口共享仲裁配置（仅验证开关开启时的 elaboration，其余参数与基线一致）
  val cfgWrShared = cfg.copy(enableSharedWritePorts = true)

  // 非阻塞写配置（写 outstanding 深度 4，验证 nbWr 编译期分支可正常发射；其余参数与基线一致）
  val cfgNbWr = cfg.copy(writeOutstandingDepth = 4)

  // 非阻塞写 + 共享写端口配置（验证仲裁器归属 FIFO 分支可正常发射）
  val cfgNbWrShared = cfg.copy(writeOutstandingDepth = 4, enableSharedWritePorts = true)

  def deleteRecursively(path: Path): Unit = {
    if (Files.exists(path)) {
      if (Files.isDirectory(path)) {
        Files.list(path).iterator().asScala.foreach(deleteRecursively)
      }
      Files.delete(path)
    }
  }

  def collectFiles(dir: File): Seq[File] = {
    dir.listFiles().flatMap { f =>
      if (f.isDirectory) collectFiles(f)
      else Seq(f)
    }.toSeq
  }

  /** 清理目录 → 发射 RTL → 打包 zip */
  def emitAndZip(config: ForagerBeeConfig, outputDir: String, zipName: String): Unit = {
    val outDirPath = Path.of(outputDir)

    if (Files.exists(outDirPath)) {
      // 只删除目录内容，保留目录本身
      Files.list(outDirPath).iterator().asScala.foreach(deleteRecursively)
      println(s"[ForagerBeeElaborate] Cleaned output directory: $outputDir")
    }

    ChiselStage.emitSystemVerilogFile(
       gen = withModulePrefix("Fb") { new ForagerBee(config) }
      ,args = Array("--target-dir", outputDir)
    )

    println(s"[ForagerBeeElaborate] RTL generation complete in $outputDir")

    val outDirFile = outDirPath.toFile
    val zipFile = new File(outDirFile, zipName)
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
    println(s"[ForagerBeeElaborate] Packaged ${allFiles.length} files into ${zipFile.getPath}")
  }

  // --- 基线配置（1:1 直连，产物路径与打包名保持不变） ---
  emitAndZip(cfg, "src/foragerbee/verilog", "ForagerBee_rtl.zip")

  // --- 写端口共享仲裁配置 ---
  emitAndZip(cfgWrShared, "src/foragerbee/verilog_wrshared", "ForagerBee_wrshared_rtl.zip")

  // --- 非阻塞写配置（writeOutstandingDepth=4，独立目录，不参与零回归对比） ---
  emitAndZip(cfgNbWr, "src/foragerbee/verilog_nbwr", "ForagerBee_nbwr_rtl.zip")

  // --- 非阻塞写 + 共享写端口配置（仲裁器归属 FIFO 分支，独立目录） ---
  emitAndZip(cfgNbWrShared, "src/foragerbee/verilog_nbwr_shared", "ForagerBee_nbwr_shared_rtl.zip")
}
