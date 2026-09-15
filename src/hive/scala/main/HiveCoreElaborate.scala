/** HiveCore SystemVerilog 固定导出入口
  *
  * 用法：sbt "runMain HiveCoreElaborate"
  * 输出：
  *   - src/hive/verilog/     基线配置（FP16 数据通路：aW=bW=16, cW=40，深度 32）。
  *                           【MX 必选】默认 supportedFmts 含全部格式（含 MX）→ hasMx=true，
  *                           顶层暴露 dmaScaleA/dmaScaleB 并例化 scaleA/scaleB DMA；但 scale 通路
  *                           由运行期 isMx(regFile.aFmt) 门控，非 MX 运行时不启动、行为与
  *                           无 MX 硬件逐拍一致。
  *                           (#155 MJ1) 默认集自本轮起含 7 种格式（追加 MXE2M1）⟹ hasMx4=true，
  *                           本套产物同样长出 fp4 quad-lane 硬件（旧「不含 fp4」陈述已作废）。
  *   - src/hive/verilog_mx/  MX 仿真对齐配置（aW=bW=16, cW=40，深度 2048/256，
  *                           supportedFmts 显式为 MX-only 三格式 MXE4M3/MXE5M2/MXE2M1）。
  *                           同样 hasMx=true 且 hasMx4=true。
  *                           (#155 MJ1) 订正：原文写「supportedFmts 显式仅含 MX 双格式」「差异
  *                           退化为缓冲深度与 supportedFmts」——前半句已作废（新 require
  *                           (!hasMx8 || hasMx4) 强制 fp8-only 非法，故 cfgMx 必须含 MXE2M1）；
  *                           后半句仍成立但内容变了：与基线的 supportedFmts 差异现在是
  *                           「MX-only 三格式 vs 默认七格式」（⟹ 本套 hasFp=hasInt=false），
  *                           加上缓冲深度差异。
  *                           ★另：cfgMx 与 cfgMxFp4 现为全 12 字段相同 ⟹ verilog_mx 与
  *                           verilog_mxfp4 是同一配置的重复交付面（本轮不删不合并，
  *                           建议见 build/155 报告）。
  *                           【注意】两套产物 hasMx 均为 true 且 aW/bW 均为 16。
  *
  * 【对外破坏性变更（MXFP8 v2 SIMD 升级）】verilog_mx 产物 dmaA/dmaB 数据位宽由
  * 128 位 → 256 位（aExtW/bExtW = totalN*aEffW = 16×16）。基线 verilog/（本就 aW=16）
  * 端口集不变，仅内部 MX 通路升级为 v2 SIMD（2 元素/拍：4 个 HiveCvtOpMx + 2 个
  * HiveMacFp 级联——二者均为原 HiveWorkUnit.scala 子模块、已随 FusionMac 重构删除，
  * 功能并入 FpCvt/FpMul/FpAdd；双 pass 拼块删除、pass 数减半、MX 算力翻倍）逻辑。
  *
  * 【对外破坏性变更（顶层 DMA 端口语义命名）】顶层 DMA 端口由 dmaNExt 数字命名改为语义命名：
  * dma0Ext → dmaA（A 激活读）、dma1Ext → dmaC（C 结果写回）、dma2Ext → dmaB（B 权重读）、
  * dma3Ext → dmaScaleA、dma4Ext → dmaScaleB（仅 hasMx）。端口集内容、顺序、类型、位宽与
  * Option 门控均不变，仅名字变化；下游集成需同步更新例化/连接名。
  *
  * P5.5 双配置发射：以同一入口分别 elaborate 两套配置，各自清理目录并打包独立
  * zip，互不覆盖。旧预期「非 MX 基线不暴露 dmaScaleA/dmaScaleB」随 MX 必选裁定作废。
  *
  * 【P3 收敛留痕（任务90：dmaC 256 默认契约 + outFmt 4-bit，本次重发射产物已按此生成）】
  *   1. 【对外破坏性】dmaC 写回数据位宽 640 → 256：两套配置均不传 cDmaExtW，
  *      默认值由哨兵 0（满宽）改为 256；cDma 行粒度多拍发射（行内 256-bit 细分地址）。
  *      cDmaExtW 契约：仅 2^N、区间 [totalN*8, totalN*32]、totalN*32 整除，违例构造期拒绝。
  *   2. outFmt 寄存器扩为 4-bit = Cat(regs(7)(16), regs(7)(13,11))：低 3 位逐字兼容旧编码，
  *      bit16 为整数族选择位，旧软件零迁移。
  *   3. 新增整数族编码：1000/1001/1010 = int32/int16/int8（槽低位截取、行内密集低位优先、
  *      有效段以上高位零填充）。
  *   4. 整数运行必须显式写 outFmt：不写 outFmt 时 outFmt=0000（fp32 直出），族交叉校验
  *      拦截非法组合（违者 config-err 拒绝启动）。
  *   5. MX 运行 fp32 直出保留：MX 合法 outFmt ∈ {0,4,5,6}（0=fp32 直出为 P0 契约后受 v1
  *      锚守护的合法路径；4/5/6 量化），否决「MX 强制量化」严格版。
  *   6. 【对外软件契约废止】5B/元素槽旧布局废止：所有格式均密集行排（4B/2B/1B/0.5B 每元素），
  *      拍内低列号低位、折行，读结果软件按新布局解析。
  *   7. P3 主代码修复 5 项摘要：①HiveCore cPop.ready store 分支折入 cDma.bufPop.ready +
  *      cvt.in.valid 追加 cStoreGate && cDma.bufPop.ready 门（防两段式幻影行）；②Executor
  *      sNEXT_NK_TILE 门吞没竞态（等 cStoreGateReg 落门后推进、副作用移入通过分支）；
  *      ③WrOnly 块末行窗口门控批准版（blkRxCnt 收行计数，blkRowCnt/lastLineLatching 变体
  *      已删除）；④MX 校验放宽（outFmt ∈ {0,4,5,6}）；⑤HiveOutCvt scaleSent 锁存防 scale
  *      重复 push 与假满死锁（释放判据放宽：scale 已交即可随 out.fire 释放）。
  *      【S-Ken3 评审项】多 tile 写回由旧的并行流水改为串行推进（Executor sNEXT_NK_TILE
  *      等 cStoreGate 落门），牲牲吐率换取 correctness-first 时序确定性，消除
  *      两段式 cvt 无同步幕下的幻影行/块超弹竞态。
  *
  * 【数值核本轮变更留痕（W-Ken2 评审项）】以下两处为 C 写回重构期间的刻意数值修复，
  * 非环境性/基线差异：
  *   - Fp32.mul（HiveWorkUnit/HiveMacFpLazy 调用）：eAdj 指数补偿修正——原实现在
  *     次正规乘数场景下指数偏差 1 位，修复后与 IEEE 754 单步乘积对齐。
  *   - Fp32.add（HiveWorkUnit 调用）：桶形移位重写——原实现远路径对齐移位存在
  *     sticky 位丢失缺陷，重写后 Far/Near 双路径与软件参考模型逐位一致。
  * 两处均已通过 Fp32Test 单测 + 42 套件端到端位锚守护，后续任何触碰需重跑全量回归。
  *
  * 【「禁动区」精确界定（W-Ken3 评审项）】本文件及相关注释中的「禁动区」定义为
  * 「C 写回重构 P0-P3 阶段未修改的模块/代码段」，不等同于「整个会话周期从未改动」：
  *   - HiveComb、HiveWorker 等 MAC/阵列本体在前序任务
  *     （MX SIMD v2 / loadW 水平加载 / Fp32 数值修复）中已有重构，不在「禁动区」内
  *     （当时并列的 HiveMacFp、HiveCvtOpMx 已随 FusionMac 重构删除）。
  *   - HiveCoreExecutor 的 sLOAD_A 离场门控（C2 修复）属评审发现的合法缺陷最小修复，
  *     不改变「P0-P3 未修改」的禁动区界定。
  *   - 禁动区典型包含：aDma/bDma RdOnly 主干遍历、Scratchpad FIFO 行为。
  *     【#133 撤销】此处原把「cDma sNEXT_N 三角累积地址模型」列为禁动区典型项，
  *     现已从清单移除：该三角累积是缺陷行为（nt≥1 块首址错，FP32/N=32 下实得
  *     base+0xFC0、正确值 base+0x40），sNEXT_N 已改为「基址 + 轮偏移」线性模型
  *     （curAddr := io.regFile.cAddr + colAddr），与 HiveCoreDmaScaleWr 同构。
  *     教训：把缺陷行为写进「禁动区」清单会让它获得伪权威、阻断修复。
  *
  * 【P3b MX 量化输出扩展留痕（声明区；重发射在验证阶段统一做，本阶段仅注释）】
  *   - 【端口变更】两套产物（hasMx）新增 dmaCScale 写通道端口（7 信号：
  *     rsp.valid/ready/err + req.valid/ready/addr/data，位宽 = cScaleRowW
  *     = (totalN/minBlockSize)*8 = totalN/2 **bit** = totalN/16 **字节**；totalN=16 时 8 bit。
  *     (#155 MJ2) 订正：原文写「cScaleRowW = totalN/8 字节」，与实际相差 2 倍
  *     （minBlockSize=16 而非 8；且原式漏了 bit/字节 量纲），
  *     同类 2× 错另见 HiveCoreDmaWrOnly 类头与 HiveOutCvt scaleRow 注释，均已一并订正），
  *     顶层端口总数 47 → 54；非 MX 配置不暴露。dmaCScale 基址 = regs(11) cScaleAddr，
  *     仅量化输出（regs(7)(13,11) 100=E4M3/101=E5M2/110=E2M1）运行时启动，非量化运行死路。
  *   - 【对外软件契约变更】量化启用时 C 内存布局改为密集 1B/元素（mxfp8）或 0.5B/元素
  *     （mxfp4，低列号低 nibble）+ 独立 scale 数组（cScaleAddr 基址，每 8 元素块 1 字节
  *     E8M0，行步进 = ceil(n/blk) 字节、列 tile 步进 = totalN/blk 字节，blk 由
  *     regs(7)(15,14) 解码）；读结果软件须按新布局反量化（默认配置不受影响）。
  *
  * 【MX scale 输入通道外部内存布局契约（dmaScaleA/dmaScaleB，仅 hasMx，MX 运行时启用）】
  *   - scaleA（regs(9) 基址，8-bit/元素）：布局 scaleA[m][kb] 行主序连续，每行 K/32 字节；
  *     每 32 个 K 元素共享 1 个 E8M0 scale 字节。DMA 取址行步进 = K/32 字节（= regFile.k >> 5）、
  *     块（kb）步进 = 1 字节；地址 = base + m×(K/32) + kb，足迹 ∈ [base, base + M×(K/32))。
  *   - scaleB（regs(10) 基址，scaleRowW=totalN×8 bit/向量）：布局 scaleB[kb][n] kb-major 连续，
  *     每 kb 占 regFile.n 字节；块内步进 = N 字节（逐 n 列），轮间跳回 base + (nt+1)×totalN 字节。
  *     地址 = base + kb×N + nt×totalN。
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
  // scale buffer 深度给足以匹配用例）。
  // (#155 MJ1) 订正：原文写「supportedFmts 显式仅含 MXE4M3/MXE5M2」「与基线的差异仅在
  //   supportedFmts 与缓冲深度」——两句自本轮起都不再成立。本轮把 MXE2M1 升格为 HiveCoreConfig
  //   的默认必选，并新增 require(!hasMx8 || hasMx4)，故 cfgMx 必须显式追加 MXE2M1，否则
  //   本 App 在【构造 val cfgMx 这一行】就抛 IllegalArgumentException，连带 cfgBase / cfgMxFp4
  //   的发射全部无法进行（Scala object 体顺序求值，一个 val 抛 ⟹ 整个 emit 入口废掉）。
  //   ⟹ 这不是可选的清理，是裁定 1.3「全仓构造点逐一对齐」的硬前提。
  //   追加后 cfgMx 与 cfgMxFp4 的【全 12 个字段逐一相同】（case class == 为 true，含
  //   scaleBufferDepth=256）⟹ verilog_mx 与 verilog_mxfp4 变成同一配置的重复交付面。
  //   本轮【不删不合并】任何 cfg / emit 入口（需用户裁定），仅在 build/155 报告登记并给建议。
  //   与 cfgBase 的差异现在只剩缓冲深度（32/32/32 vs 2048/2048/2048 与 scaleBufferDepth
  //   64 vs 256），supportedFmts 已与 cfgBase 的默认集不同（cfgMx 是 MX-only 三格式，
  //   cfgBase 是默认七格式）⟹ hasFp/hasInt 在 cfgMx 下为 false。
  // aW/bW=16 满足新 require（hasMx ⇒ aW>=16 && bW>=16）：v2 SIMD 双元素/拍需要 16-bit lane。
  val cfgMx = HiveCoreConfig(
    arrayN = 8,
    clusterM = 2,         // totalN = 16
    aW = 16, bW = 16, cW = 40,
    supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
    aBufferDepth = 2048,
    bBufferDepth = 2048,
    cBufferDepth = 2048,
    scaleBufferDepth = 256
  )

  // 【Phase 4 打通里程碑】MXFP4(E2M1) 发射配置：在 cfgMx 基础上追加 DataFormat.MXE2M1，
  // 使 hasMx4 = supportedFmts.contains(MXE2M1) = true，触发以下 additive 硬件分支：
  //   · HiveWorkUnit 内 fp4 quad-lane 通路（8×HiveCvtOpMx + 4 级 HiveMacFpMx 左结合级联
  //     ——二者均为原 HiveWorkUnit.scala 子模块、已随 FusionMac 重构删除，功能并入
  //     FpCvt/FpMul/FpAdd → HiveFusionMacFp；
  //     16-bit lane 打包 4 个 4-bit nibble：io.a(3,0)=k+0 … io.a(15,12)=k+3），运行期
  //     Mux(isMx4(aFmt), quadResult, dualLaneMx()) 选择；
  //   · HiveCoreCalcConfig: colTile uses the bits-based formula
  //     (totalN x elemsPerLane x memElemBits/8 = 32), plus the fp4-only runtime
  //     guards errCode 8 (K not divisible by kPerBeat) and the Fix-4 mix
  //     errCode 3 (isMx4(aFmt) ^ isMx4(bFmt)).
  //     (#146) The former errCode 7 ("fp4 forces mxBlockSize=64") has been
  //     DELETED: the MXBlockSize enum was narrowed to {M16=0, M32=1}, so its
  //     `getBlockSize() =/= 64.U` predicate was a tautology that rejected every
  //     fp4 run unconditionally and silently. fp4 now accepts blk in {16, 32}
  //     exactly like fp8, and errCode 7 is free again.
  //   · HiveCoreExecutor2：kTile 分母泛化为 /elemsPerLane（fp4=4）。
  // require 全部满足：totalN=8×2=16、aW=bW=16(>=16)、cEffW=max(40,16)=40(>=40)、
  //   cDmaExtW=256 默认（isPow2、totalN*32=512 整除、[128,512] 区间、totalN*4=64<=256）、
  //   cBufferDepth=2048>=2*totalN=32。
  // fp4 mxBlockSize (aMxBlockSize/bMxBlockSize) is a RUNTIME register field
  //   (regs(6) bits(25,24) / bits(23,22)), written by the simulation case; the
  //   encodable values are only sel=0 -> 16 bytes and sel=1 -> 32 bytes (the
  //   former 4-level 8/16/32/64 enum was narrowed to 2 levels, so "=M64=3" no
  //   longer exists). The config layer has no such field; hasMx4 is opened
  //   purely by supportedFmts containing MXE2M1.
  // 【#155 MJ1：旧「关键约束·零扰动基线」契约已由用户主动作废】
  //   旧契约原文：「发往独立新目录 src/hive/verilog_mxfp4/，绝不覆盖 verilog/verilog_mx
  //   两套回归锚点；cfgBase/cfgMx 的 supportedFmts 未变（不含 MXE2M1 ⟹ hasMx4=false），
  //   其门控解析出的硬件结构逐位不变 ⟹ 两套基线 normalized-diff=0 不受本配置加入的扰动。」
  //   作废理由：本轮把 MXE2M1 升格为 HiveCoreConfig 的默认必选（cfgBase 走默认集 ⟹ 自动含
  //   MXE2M1），cfgMx 亦按裁定 1.3 显式追加 MXE2M1 ⟹ 「cfgBase/cfgMx 的 supportedFmts 未变」
  //   与「hasMx4=false」两个前提同时失效。
  // 【仍然成立的部分】发往独立目录 src/hive/verilog_mxfp4/、绝不覆盖 verilog/verilog_mx
  //   这条目录隔离约束【不变】（重发射时仍需三套并存以便对照）。
  // 【新基线预期（供 #142 重发射时对照）】三套 cfg 现在 hasMx4 全为 true ⟹ fp4 支路一律
  //   elaborate。预期非零 diff 的模块：Hive_HiveOutCvt.sv（q2Seq/packed4/次正规与深下溢臂
  //   全部无门控）、Hive_HiveWorkUnit.sv（fp4 quad-lane 通路 + 运行期 Mux(isMx4, quad, dualLane)；
  //   原并列预期的 Hive_HiveMacFpMx.sv 因 HiveMacFpMx 已随 FusionMac 重构删除而不再产出，
  //   该通路并入 Hive_HiveFusionMacFp.sv）、HiveCoreCalcConfig 侧 elemsPerLane 2-bit→3-bit
  //   与 errCode 8 / Fix-4 errCode 3 两支运行期守护。三套产物之间不再有 normalized-diff=0
  //   关系；数值正确性改由 TB 侧金锚（OCP MX / IEEE-754 独立推导）+ 归一化 stdout 基线守护。
  // ★cfgMx ≡ cfgMxFp4（全 12 字段逐一相同）⟹ verilog_mx 与 verilog_mxfp4 将是同一配置的
  //   重复交付面。本轮不删不合并（需用户裁定），建议见 build/155 报告。
  val cfgMxFp4 = HiveCoreConfig(
    arrayN = 8,
    clusterM = 2,         // totalN = 16
    aW = 16, bW = 16, cW = 40,
    supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
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
  // 【Phase 4 打通里程碑】MXFP4 配置：独立新目录，含 fp4 quad-lane 通路（hasMx4=true）。
  // 绝不覆盖上方 verilog/verilog_mx 两套回归锚点；三套配置各自清理并打包独立 zip。
  emit(cfgMxFp4, "src/hive/verilog_mxfp4", "HiveCore_mxfp4_rtl.zip")
}
