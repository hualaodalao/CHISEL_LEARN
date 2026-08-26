/** HiveCore SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain HiveCoreElaborate"
  * 输出：
  *   - src/hive/verilog/     非 MX 基线配置（FP16：aW=bW=16, cW=40）。
  *                           hasMx=false → 顶层不暴露 dma3/dma4，不例化 scaleA/scaleB
  *                           DMA 与 scale FIFO，RTL 文件集合/端口与历史发射逐字一致。
  *   - src/hive/verilog_mx/  MX 配置（supportedFmts 含 MXE4M3/MXE5M2，aW=bW=8, cW=40）。
  *                           hasMx=true → 额外暴露 scaleA(8-bit)/scaleB(scaleRowW) 两条只读
  *                           DMA 外部通道（dma3/dma4）及对应 HiveCoreDmaRdOnly 子模块。
  *
  * P5.5 双配置发射：以同一入口分别 elaborate 两套配置，人工/脚本对比即可验证 hasMx
  * 门控——非 MX 生成物不含任何 scale 通道端口/子模块，MX 生成物含 scale DMA。两配置各
  * 自清理目录并打包独立 zip，互不覆盖。
  *
  * 流程（每个配置）：
  *   1. 清理输出目录
  *   2. ChiselStage 生成 RTL + filelist 等
  *   3. 递归打包所有生成文件为 zip
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
  // 非 MX 基线配置：与 HiveCoreSimCase 的 HiveCoreConfig 构造参数原样一致。
  // 注意: DMA 的 bufAvailability 端口宽度由 aBufferDepth 决定（设计缺陷），
  // 因此 aBufferDepth 必须 >= cBufferDepth 以避免信号截断。
  val cfgBase = HiveCoreConfig(
    arrayN = 8,
    clusterM = 2,         // totalN = 16
    aBufferDepth = 32,
    bBufferDepth = 32,
    cBufferDepth = 32,
    aW = 16,
    bW = 16,
    cW = 40  // 纯浮点延迟规格化仅需 40-bit（{sign(1),exp(8),mantissa(31)}），48 是整数 MAC 才需
  )

  // MX 配置：与 HiveCoreSimCaseMxSpec 的 HiveCoreConfig 对齐（aW=bW=8, cW=40，
  // supportedFmts 含 MXE4M3/MXE5M2 → hasMx=true）。scale buffer 深度给足以匹配用例。
  val cfgMx = HiveCoreConfig(
    arrayN = 8,
    clusterM = 2,         // totalN = 16
    aW = 8, bW = 8, cW = 40,
    supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2),
    aBufferDepth = 2048,
    bBufferDepth = 2048,
    cBufferDepth = 2048,
    scaleBufferDepth = 256
  )

  /** 单配置发射：清理目录 → 生成 RTL → 递归打包 zip。 */
  def emit(cfg: HiveCoreConfig, outputDir: String, zipName: String): Unit = {
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

    Files.createDirectories(outDirPath)
    // 只删除目录内容，保留目录本身
    Files.list(outDirPath).iterator().asScala.foreach(deleteRecursively)
    println(s"[HiveCoreElaborate] Cleaned output directory: $outputDir (hasMx=${cfg.hasMx})")

    // --- Step 2: 生成 RTL ---
    ChiselStage.emitSystemVerilogFile(
       gen = withModulePrefix("Hive") { new HiveCore(cfg) }
      ,args = Array("--target-dir", outputDir)
    )

    println(s"[HiveCoreElaborate] RTL generation complete in $outputDir")

    // --- Step 3: 递归打包所有生成文件为 zip ---
    val outDirFile = outDirPath.toFile
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

  // 非 MX 基线：路径/产物与历史一致（回归对比锚点）。
  emit(cfgBase, "src/hive/verilog", "HiveCore_rtl.zip")
  // MX 配置：独立目录，含 scale DMA 端口/子模块。
  emit(cfgMx, "src/hive/verilog_mx", "HiveCore_mx_rtl.zip")
}
