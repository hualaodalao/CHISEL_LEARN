/** HiveCore SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain HiveCoreElaborate"
  * 输出：
  *   - src/hive/verilog/     基线配置（FP16 数据通路：aW=bW=16, cW=40，深度 32）。
  *                           【MX 必选】默认 supportedFmts 含全部格式（含 MX）→ hasMx=true，
  *                           顶层暴露 dmaScaleA/dmaScaleB 并例化 scaleA/scaleB DMA；但 scale 通路
  *                           由运行期 isMx(regFile.aFmt) 门控，非 MX 运行时不启动、行为与
  *                           无 MX 硬件逐拍一致。
  *   - src/hive/verilog_mx/  MX 仿真对齐配置（aW=bW=16, cW=40，深度 2048/256，
  *                           supportedFmts 显式仅含 MX 双格式）。同样 hasMx=true。
  *                           【注意】两套产物 hasMx 均为 true 且 aW/bW 均为 16，
  *                           差异退化为缓冲深度与 supportedFmts，而非 hasMx。
  *
  * 【对外破坏性变更（MXFP8 v2 SIMD 升级）】verilog_mx 产物 dmaA/dmaB 数据位宽由
  * 128 位 → 256 位（aExtW/bExtW = totalN*aEffW = 16×16）。基线 verilog/（本就 aW=16）
  * 端口集不变，仅内部 MX 通路升级为 v2 SIMD（2 元素/拍：4 个 HiveCvtOpMx + 2 个
  * HiveMacFp 级联，双 pass 拼块删除、pass 数减半、MX 算力翻倍）逻辑。
  *
  * 【对外破坏性变更（顶层 DMA 端口语义命名）】顶层 DMA 端口由 dmaNExt 数字命名改为语义命名：
  * dma0Ext → dmaA（A 激活读）、dma1Ext → dmaC（C 结果写回）、dma2Ext → dmaB（B 权重读）、
  * dma3Ext → dmaScaleA、dma4Ext → dmaScaleB（仅 hasMx）。端口集内容、顺序、类型、位宽与
  * Option 门控均不变，仅名字变化；下游集成需同步更新例化/连接名。
  *
  * P5.5 双配置发射：以同一入口分别 elaborate 两套配置，各自清理目录并打包独立
  * zip，互不覆盖。旧预期「非 MX 基线不暴露 dmaScaleA/dmaScaleB」随 MX 必选裁定作废。
  *
  * 【P1/P2 dmaC 写回重构留痕】本入口两套配置均不传 cDmaExtW（哨兵 0 → 满宽 cExtW，
  * 一拍一行，dmaC 端口位宽保持 640，非破坏性）；新增参数 cDmaExtW（缩位）与运行期寄存器
  * regs(7)(13,11)=outFmt（3-bit：000=fp32 默认/001=fp16/010=bf16/011=reserved 回落 000/
  * 100..110=mxfp8-E4M3/E5M2、mxfp4-E2M1 量化/111=reserved 降级 000，精度转换模块 HiveOutCvt）
  * 需显式启用，默认不写 outFmt 位时出厂产物行为与重构前 bit-exact。
  * 【对外软件契约变更留痕】显式启用缩位/降精度（cDmaExtW≠0 或 outFmt≠000）时，
  * C 写回内存布局由 5B/元素槽改为 4B（fp32）/2B（fp16/bf16）密集，属对外软件契约变更，
  * 读结果软件需同步迁移（默认配置不受影响）。
  *
  * 【P3b MX 量化输出扩展留痕（声明区；重发射在验证阶段统一做，本阶段仅注释）】
  *   - 【端口变更】两套产物（hasMx）新增 dmaCScale 写通道端口（7 信号：
  *     rsp.valid/ready/err + req.valid/ready/addr/data，位宽 = cScaleRowW = totalN/8 字节），
  *     顶层端口总数 47 → 54；非 MX 配置不暴露。dmaCScale 基址 = regs(11) cScaleAddr，
  *     仅量化输出（regs(7)(13,11) 100=E4M3/101=E5M2/110=E2M1）运行时启动，非量化运行死路。
  *   - 【对外软件契约变更】量化启用时 C 内存布局改为密集 1B/元素（mxfp8）或 0.5B/元素
  *     （mxfp4，低列号低 nibble）+ 独立 scale 数组（cScaleAddr 基址，每 8 元素块 1 字节
  *     E8M0，行步进 = ceil(n/blk) 字节、列 tile 步进 = totalN/blk 字节，blk 由
  *     regs(7)(15,14) 解码）；读结果软件须按新布局反量化（默认配置不受影响）。
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
  // 基线配置：与 HiveCoreSimCase 的 HiveCoreConfig 构造参数原样一致。
  // 【MX 必选】不传 supportedFmts → 默认含全部格式（含 MX）→ hasMx=true，
  // 产物含 dmaScaleA/dmaScaleB 与 scale DMA；非 MX 运行由运行期 isMx 门控隔离。
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

  // MX 仿真对齐配置：与 HiveCoreSimCaseMxSpec 的 HiveCoreConfig 对齐（aW=bW=16, cW=40，
  // supportedFmts 显式仅含 MXE4M3/MXE5M2 → hasMx=true）。scale buffer 深度给足以匹配用例。
  // 与基线的差异仅在 supportedFmts 与缓冲深度（两者 hasMx 均真、aW/bW 均为 16）。
  // aW/bW=16 满足新 require（hasMx ⇒ aW>=16 && bW>=16）：v2 SIMD 双元素/拍需要 16-bit lane。
  val cfgMx = HiveCoreConfig(
    arrayN = 8,
    clusterM = 2,         // totalN = 16
    aW = 16, bW = 16, cW = 40,
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

  // 基线（现含 MX 硬件）：路径/产物与历史一致（回归对比锚点）。
  emit(cfgBase, "src/hive/verilog", "HiveCore_rtl.zip")
  // MX 配置：独立目录，含 scale DMA 端口/子模块。
  emit(cfgMx, "src/hive/verilog_mx", "HiveCore_mx_rtl.zip")
}
