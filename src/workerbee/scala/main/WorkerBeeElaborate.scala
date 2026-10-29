/** WorkerBee SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain WorkerBeeElaborate"
  * 输出（唯一交付面）：src/workerbee/verilog/ ——`WorkerBeeConfig()` 纯默认配置，无字段覆写。
  *   默认 supportedFmts = {INT8, INT4, MXE4M3, MXE5M2, MXE2M1, FP8E4M3, FP8E5M2, BF16}
  *   ⟹ hasMx/hasMx4/hasInt/hasFp8/hasBf16 全为 true：scale DMA、fp4 quad-lane、FP8 remap、
  *   BF16 专用窄乘法器（FpMulBf16Mx，WbFusionMacFp lane0 第三条分支）全部 elaborate。
  *   所有格式通路由运行期 fmt 门控隔离，非该格式运行时行为与不含该硬件时逐拍一致。
  *   子模块 RTL 不单独发射，均作为实例包含在本套产物中。
  *
  * 顶层 DMA 端口语义命名：dmaA（A 激活读）、dmaC（C 结果写回）、dmaB（B 权重读）、
  * dmaScaleA / dmaScaleB（仅 hasMx）。
  *
  * 【C 侧量化输出内存布局契约（量化启用时）】C 为密集 1B/元素（mxfp8）或 0.5B/元素
  *   （mxfp4，低列号低 nibble）+ 独立 scale 数组：基址 regs(11) cScaleAddr，每 blk 元素块
  *   1 字节 E8M0，行步进 = ceil(n/blk) 字节、列 tile 步进 = totalN/blk 字节，blk 由
  *   regs(7)(15,14) 解码。dmaCScale 端口位宽 = cScaleRowW = (totalN/minBlockSize)*8
  *   = totalN/2 **bit** = totalN/16 **字节**（totalN=16 时 8 bit）；仅量化输出
  *   （regs(7)(13,11) 100=E4M3/101=E5M2/110=E2M1）运行时启动，非 MX 配置不暴露该端口。
  *
  * 【MX scale 输入通道外部内存布局契约（dmaScaleA/dmaScaleB，仅 hasMx，MX 运行时启用）】
  *   - scaleA（regs(9) 基址，8-bit/元素）：scaleA[m][kb] 行主序连续，每行 K/32 字节、
  *     每 32 个 K 元素共享 1 个 E8M0 字节。行步进 = K/32 字节、块（kb）步进 = 1 字节；
  *     地址 = base + m×(K/32) + kb，足迹 ∈ [base, base + M×(K/32))。
  *   - scaleB（regs(10) 基址，scaleRowW=totalN×8 bit/向量）：scaleB[kb][n] kb-major 连续，
  *     每 kb 占 regFile.n 字节；块内步进 = N 字节（逐 n 列），轮间跳回
  *     base + (nt+1)×totalN 字节。地址 = base + kb×N + nt×totalN。
  *
  * 【C 写回总线契约】dmaC 数据位宽默认 256，cDma 行粒度多拍发射（行内 256-bit 细分地址）；
  *   cDmaExtW 仅允许 2^N、区间 [totalN*8, totalN*32]、totalN*32 整除，违例构造期拒绝。
  *
  * 【outFmt 编码】outFmt = Cat(regs(7)(16), regs(7)(13,11))，4-bit：bit16 为整数族选择位；
  *   1000/1001/1010 = int32/int16/int8（槽低位截取、行内密集低位优先、有效段以上高位零填充）。
  *   不写 outFmt 时 outFmt=0000（fp32 直出）；整数运行必须显式写 outFmt，否则族交叉校验
  *   以 config-err 拒绝启动。MX 运行合法 outFmt ∈ {0,4,5,6}（0=fp32 直出，4/5/6 量化）。
  *   所有格式均密集行排（4B/2B/1B/0.5B 每元素），拍内低列号低位、折行。
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

object WorkerBeeElaborate extends App {
  // 唯一发射配置：WorkerBeeConfig() 纯默认，无任何字段覆写 ⟹ 所有格式通路一律 elaborate，
  // 由运行期 fmt 门控隔离。缓冲深度只影响 Scratchpad 规模与 bufAvailability 端口位宽，
  // 不改数据通路语义；需满足 aBufferDepth >= cBufferDepth（端口宽度由 aBufferDepth 决定，
  // 不等则截断）。
  val cfg = WorkerBeeConfig()

  /** 单配置发射：清理目录 → 生成 RTL → 递归打包 zip。 */
  def emit(cfg: WorkerBeeConfig, outputDir: String, zipName: String): Unit = {
    val outDirPath = Path.of(outputDir)

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
    println(s"[WorkerBeeElaborate] Cleaned output directory: $outputDir (hasMx=${cfg.hasMx})")

    ChiselStage.emitSystemVerilogFile(
       gen = withModulePrefix("Wb") { new WorkerBee(cfg) }
      ,args = Array("--target-dir", outputDir)
    )

    println(s"[WorkerBeeElaborate] RTL generation complete in $outputDir")

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
    println(s"[WorkerBeeElaborate] Packaged ${allFiles.length} files into ${zipFile.getPath}")
  }

  emit(cfg, "src/workerbee/verilog", "WorkerBee_rtl.zip")
}
