/** HiveDebugModel — HiveCoreDebugCase 的纯软件参考模型（不含任何 poke/peek）。
  *
  * 职责划分：本文件只做「参数解析 / 几何推演 / 字节内存镜像 / 软件 golden /
  * 期望地址序列 / 结果解码对比」，仿真驱动与断言在 HiveCoreDebugCase.scala。
  *
  * 三条设计原则（对齐本项目既有教训）：
  *  1. **内存保真供数**：外部存储建模为「字节地址 → 8-bit」镜像，DMA 请求拍按
  *     req.addr 从镜像取 aExtW/bExtW 位返回，绝不按 beat 序号顺序发数——按序发数
  *     会掩盖 DMA 供数序/地址公式错位（历史事故：scaleB 假存储按序供数使
  *     kb-major vs nt-major 错位恒 0 mismatch）。
  *  2. **语义独立的期望地址**：期望地址序列由「张量语义布局」闭式推导，不镜像
  *     HiveCoreCalcConfig 的谓词链；两者不一致即为硬件缺陷（如 INT16 未落入
  *     is2B 分支导致 aRowAddressOffset = k/2）。默认非致命收集，`-DstrictAddr=1`
  *     改为逐拍致命断言。
  *  3. **只加法依赖 HiveSimCommon**：本文件仅调用 HiveSimCommon 既有公共函数，
  *     未对其做任何修改。
  *
  * 内存布局约定（与硬件地址公式在 FP16/MX 族上逐字自洽）：
  *   A       : row-major M×K，元素 memElemBytes(aFmt) 字节，基址 A_BASE
  *   B       : loadWMode=0 → row-major K×N；loadWMode=1 → row-major N×K（转置）
  *   scaleA  : row-major M×(K/aBlk) 字节（aBlk = 8<<aMxBlkSel）
  *   scaleB  : row-major N×(K/bBlk) 字节，byte = n*(K/bBlk) + kb（与硬件
  *             scaleBRowAddressOffset = K/bBlk 作为 n 向步进逐字对应）
  *   C       : 密集布局，地址序列直接复用 HiveSimCommon.cAddrSeqDense（四套件
  *             共用唯一推演源）。【#133 订正】此处原写「与 HiveCoreDmaWrOnly 三角
  *             累积闭式同源」——那是把缺陷 RTL 的行为当规范（金锚反向拟合 RTL，
  *             使 nTiles≥2 的 dmaC 逐拍地址断言恒真假绿）。正确口径与 A/B/scale
  *             五通道一致：由【张量语义布局】绝对式推导 addr(C[i][j]) =
  *             base + j×cEb + i×(N×cEb)，即块间跳转 = nt×cColOff，【不镜像 RTL】；
  *             RTL 侧 HiveCoreDmaWrOnly.sNEXT_N 现亦为「基址 + 轮偏移」线性模型
  *             （curAddr := io.regFile.cAddr + colAddr），两者独立推导后逐拍相等。
  */

import chisel3._
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable

object HiveDebugModel {

  // ========== 外部存储分区基址（与既有四套件一致，互不重叠） ==========
  val A_BASE:  Long = 0x00000000L
  val B_BASE:  Long = 0x00100000L
  val C_BASE:  Long = 0x00200000L
  val SA_BASE: Long = 0x00300000L   // scaleA
  val SB_BASE: Long = 0x00400000L   // scaleB
  val SC_BASE: Long = 0x00500000L   // scaleC（量化输出写回）

  val TAG = "DEBUG"

  // ==========================================================================
  // 格式谓词 / 几何
  // ==========================================================================

  def isMx8(f: DataFormat.Type): Boolean = f == DataFormat.MXE4M3 || f == DataFormat.MXE5M2
  def isMxAny(f: DataFormat.Type): Boolean = isMx8(f) || f == DataFormat.MXE2M1
  def isIntFmt(f: DataFormat.Type): Boolean =
    f == DataFormat.INT32 || f == DataFormat.INT16 || f == DataFormat.INT8 || f == DataFormat.INT4
  def isFloatFmt(f: DataFormat.Type): Boolean =
    f == DataFormat.FP32 || f == DataFormat.FP16 || f == DataFormat.BF16

  /** 外部内存中每元素字节数（半字节族返回 0：4-bit 元素不足 1 整字节）。
    * 【Phase 3 说明】bytes 口径对亚字节族退化到 0，故几何/内存镜像一律改用下方
    * memElemBits 的 bits 口径（bits/8）；本函数保留供整字节 storeElem 与语义查询。 */
  def memElemBytes(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32 || f == DataFormat.INT32) 4
    else if (f == DataFormat.FP16 || f == DataFormat.BF16 || f == DataFormat.INT16) 2
    else if (f == DataFormat.INT8 || isMx8(f)) 1
    else 0   // MXE2M1 / INT4：半字节，bytes 口径为 0（bits 口径见 memElemBits=4）

  /** 外部内存中每元素【位宽】（Phase 3 新增，bits 口径几何的单一真源）。
    * 与硬件 HiveCoreCalcConfig.memElemBitsOf 同源；字节对齐族 memElemBits=memElemBytes×8
    * （恒等），亚字节族 MXE2M1/INT4=4（bytes 口径为 0，bits 口径给 4）。
    * 地址偏移一律用 X×memElemBits/8：字节对齐族结果与旧 X×memElemBytes 逐值相等
    * （memElemBits 是 8 的倍数，整除无损），fp4 则得到正确的半字节地址（k×4/8=k/2）。 */
  def memElemBits(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32 || f == DataFormat.INT32) 32
    else if (f == DataFormat.FP16 || f == DataFormat.BF16 || f == DataFormat.INT16) 16
    else if (f == DataFormat.INT8 || isMx8(f)) 8
    else 4   // MXE2M1 / INT4：半字节

  /** 每 16-bit lane 打包的元素数：MXFP8 dual-lane=2、MXFP4 quad-lane=4、其余=1。
    * 与硬件 DataFormat.elemsPerLane / HiveCoreCalcConfig.elemsPerLaneOf 同源。
    * 【Phase 3 additive】新增 MXE2M1→4；对现有格式（MX8→2、其余→1）取值逐字不变，
    * 故所有非 fp4 用例（DebugCase/ParamDebugSpec 现役格式）几何零变化。 */
  def elemsPerLane(f: DataFormat.Type): Int =
    if (f == DataFormat.MXE2M1) 4 else if (isMx8(f)) 2 else 1

  /** 每拍推进的 K 元素数（= kTile 粒度）：MX8 = totalN*2、MXE2M1 = totalN*4、其余 = totalN */
  def kPerBeat(f: DataFormat.Type, totalN: Int): Int = totalN * elemsPerLane(f)

  // ==========================================================================
  // FTZ（flush-to-zero）阈值：由输出格式推导，镜像硬件下溢冲零语义（#128）
  // ==========================================================================
  // 【#128 金锚保真度修复 —— 设计意图取证结论：分支 A】硬件 HiveOutCvt 各输出支路
  //   对 subnormal 是【显式 FTZ】（类头注释原文："低于最小正规格化 → flush-to-zero
  //   （保号；本硬件族不产生目标格式次正规数）"）：
  //     · fp32ToFp16Rnd：underflow = (exp < 113.U) → signedZero（113 = fp32 编码下的
  //       2^-14，即 FP16 最小正规数）；
  //     · quantElem（MX 支路）：underflow = (eTgt < 1.S) → signedZero，其中
  //       eTgt = e + bias - s（s = 块 E8M0 scale），即目标偏置指数 < 1。
  //   与 HiveSimCommon 软件镜像逐位同构：swFp32ToFp16 (e<113→signedZero)、
  //   swQuantElem (eTgt<1→signPart)。SimCase golden 亦以 isStrict/strictExpect 手工
  //   锚点建模同一 FTZ（C[3][1] 理想值 3.2e-5 < 2^-14 → 期望码 0x0000）。
  //   金锚 compare() 旧版拿【未再编码的理想 fp32 golden】配 tolAbs=1e-6 比较，
  //   subnormal 元素被误判失配（假红）—— 修复方式是对齐硬件 FTZ，【不放宽容差】。
  //   下列阈值【全部由格式参数推导】，严禁硬编码字面量（本仓已确证 6 处「参数化量
  //   退化为编译期常数」的 refactor-loss，不再加第 7 处）。

  /** 浮点输出格式的指数域位宽（结构常量）。MX 族取自 MxFormatParams 单一真源，
    * FP16/BF16/FP32 取自 IEEE-754 二元格式定义。非浮点格式抛错（无 FTZ 概念）。 */
  def floatExpW(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32) 8
    else if (f == DataFormat.BF16) 8
    else if (f == DataFormat.FP16) 5
    else if (f == DataFormat.MXE4M3) MxFormatParams.E4M3.expW
    else if (f == DataFormat.MXE5M2) MxFormatParams.E5M2.expW
    else if (f == DataFormat.MXE2M1) MxFormatParams.E2M1.expW
    else throw new IllegalArgumentException(s"floatExpW: $f 非浮点输出格式，无 FTZ 阈值")

  /** 浮点输出格式的偏置，由 expW 推导 = 2^(expW-1) - 1（IEEE 标准偏置，非字面量）。
    * 交叉核对：MX 族此式取值与 MxFormatParams.bias 逐一相等
    * （E4M3: 2^3-1=7、E5M2: 2^4-1=15、E2M1: 2^1-1=1）。 */
  def floatBias(f: DataFormat.Type): Int = (1 << (floatExpW(f) - 1)) - 1

  /** 浮点输出格式的最小正规格化数 = 2^(1 - bias)（FTZ 阈值的格式无关基）：
    * |v| 非零且低于此值 → 硬件冲零（保号 ±0，无次正规产出）。
    * 推导值：FP16=2^-14≈6.10e-5、BF16/FP32=2^-126≈1.18e-38、MXE4M3=2^-6=0.015625、
    * MXE5M2=2^-14≈6.10e-5、MXE2M1=2^0=1.0。全部经 floatBias 推导，非硬编码字面量。 */
  def minNormal(f: DataFormat.Type): Double = math.pow(2.0, 1.0 - floatBias(f))

  /** FTZ 命中时期望的【保号零码】（#129 收紧：compare() bit-exact 判据的期望值）。
    * 符号位位置由元素位宽推导（= cOutElemBits(f) - 1），负 ref → 置符号位、正 ref → 全零；
    * 【零裸字面量】：本仓已确证 7 处「参数化量退化为编译期常数」的 refactor-loss，不加第 8 处。
    * 各格式推导值（由 cOutElemBits 位宽自动得出，非硬编码）：
    *   FP16/BF16(w16) → +0=0x0000 / -0=0x8000；FP32(w32) → 0x0 / 0x80000000；
    *   MXE4M3/MXE5M2(w8) → 0x00 / 0x80；MXE2M1(w4) → 0x0 / 0x8。 */
  def ftzSignedZeroCode(f: DataFormat.Type, refNeg: Boolean): Long = {
    val w = HiveSimCommon.cOutElemBits(f)
    if (refNeg) (1L << (w - 1)) else 0L
  }

  // ==========================================================================
  // 参数解析（大小写不敏感；非法值给清晰报错）
  // ==========================================================================

  private val fmtAliases: Map[String, DataFormat.Type] = Map(
    "fp32" -> DataFormat.FP32, "float32" -> DataFormat.FP32, "f32" -> DataFormat.FP32,
    "fp16" -> DataFormat.FP16, "float16" -> DataFormat.FP16, "f16" -> DataFormat.FP16, "half" -> DataFormat.FP16,
    "bf16" -> DataFormat.BF16, "bfloat16" -> DataFormat.BF16,
    "mxe4m3" -> DataFormat.MXE4M3, "e4m3" -> DataFormat.MXE4M3, "mxfp8e4m3" -> DataFormat.MXE4M3,
    "mxe5m2" -> DataFormat.MXE5M2, "e5m2" -> DataFormat.MXE5M2, "mxfp8e5m2" -> DataFormat.MXE5M2,
    "mxe2m1" -> DataFormat.MXE2M1, "e2m1" -> DataFormat.MXE2M1, "mxfp4" -> DataFormat.MXE2M1, "fp4" -> DataFormat.MXE2M1,
    "int32" -> DataFormat.INT32, "i32" -> DataFormat.INT32,
    "int16" -> DataFormat.INT16, "i16" -> DataFormat.INT16,
    "int8"  -> DataFormat.INT8,  "i8"  -> DataFormat.INT8,
    "int4"  -> DataFormat.INT4,  "i4"  -> DataFormat.INT4
  )

  private val rndAliases: Map[String, Int] = Map(
    "rne" -> 0, "rtz" -> 1, "rdn" -> 2, "rup" -> 3, "rmm" -> 4,
    "0" -> 0, "1" -> 1, "2" -> 2, "3" -> 3, "4" -> 4
  )

  private val addrModeAliases: Map[String, Int] = Map("mat" -> 0, "tile" -> 1, "0" -> 0, "1" -> 1)

  private val blkAliases: Map[String, Int] = Map(
    "8" -> 0, "16" -> 1, "32" -> 2, "64" -> 3,
    "m8" -> 0, "m16" -> 1, "m32" -> 2, "m64" -> 3,
    "0" -> 0, "1" -> 1, "2" -> 2, "3" -> 3
  )

  private def bad(key: String, raw: String, legal: String): Nothing =
    throw new IllegalArgumentException(
      s"[HiveDebugModel] 参数 -D$key=$raw 非法：合法取值 = $legal（大小写不敏感）")

  /** 解析数据格式（大小写不敏感，支持 e4m3/mxfp4 等常用别名） */
  def parseFmt(key: String, raw: String): DataFormat.Type =
    fmtAliases.getOrElse(raw.trim.toLowerCase,
      bad(key, raw, fmtAliases.keys.toSeq.sorted.mkString("|")))

  def parseRnd(key: String, raw: String): Int =
    rndAliases.getOrElse(raw.trim.toLowerCase, bad(key, raw, "rne|rtz|rdn|rup|rmm|0..4"))

  def parseAddrMode(key: String, raw: String): Int =
    addrModeAliases.getOrElse(raw.trim.toLowerCase, bad(key, raw, "mat|tile|0|1"))

  def parseBlkSel(key: String, raw: String): Int =
    blkAliases.getOrElse(raw.trim.toLowerCase, bad(key, raw, "8|16|32|64 (等价 m8|m16|m32|m64 或 0..3)"))

  def parseBool(key: String, raw: String): Boolean = raw.trim.toLowerCase match {
    case "1" | "true" | "yes" | "on"  => true
    case "0" | "false" | "no" | "off" => false
    case _ => bad(key, raw, "0|1|true|false")
  }

  def parseInt(key: String, raw: String): Int =
    try raw.trim.toInt
    catch { case _: NumberFormatException => bad(key, raw, "十进制整数") }

  def parseLong(key: String, raw: String): Long =
    try raw.trim.toLong
    catch { case _: NumberFormatException => bad(key, raw, "十进制长整数") }

  // ==========================================================================
  // 参数集合
  // ==========================================================================

  /** 一次调试运行的全部生效参数。所有字段均可由 -D 覆盖（见 HiveCoreDebugCase 顶部默认块）。 */
  final case class DebugParams(
      m: Int, n: Int, k: Int,
      aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
      loadWMode: Boolean,
      aMxBlkSel: Int, bMxBlkSel: Int, cMxBlkSel: Int,
      addrModeSel: Int,
      rnd: Int,
      seed: Long,
      strictAddr: Boolean,
      maxCycles: Int,
      waveTarget: String,
      testHint: String) {

    def aMxBlk: Int = 8 << aMxBlkSel
    def bMxBlk: Int = 8 << bMxBlkSel
    def cMxBlk: Int = 8 << cMxBlkSel

    /** 控制字 regs(7)：rnd(14,12) | cFmt(11,8) | bFmt(7,4) | aFmt(3,0)
      * 【#120 C2】rnd 扩为 3-bit（bits[14:12]），支持全 5 种舍入模式（RNE/RTZ/RDN/RUP/RMM）。 */
    def controlWord7: Long =
      ((rnd.toLong & 0x7) << 12) | ((cFmt.litValue.toLong & 0xF) << 8) |
        ((bFmt.litValue.toLong & 0xF) << 4) | (aFmt.litValue.toLong & 0xF)

    /** 控制字 regs(6)：loadWMode(28) | cMxBlk(27,26) | bMxBlk(25,24) | aMxBlk(23,22)
      * | cAddrMode(14) | bAddrMode(13) | aAddrMode(12) | strides(11,0)=0 */
    def controlWord6: Long =
      (if (loadWMode) 1L << 28 else 0L) |
        ((cMxBlkSel.toLong & 0x3) << 26) | ((bMxBlkSel.toLong & 0x3) << 24) |
        ((aMxBlkSel.toLong & 0x3) << 22) |
        ((addrModeSel.toLong & 0x1) << 14) | ((addrModeSel.toLong & 0x1) << 13) |
        ((addrModeSel.toLong & 0x1) << 12)

    /** 生效参数一览（启动时打印） */
    def banner: String =
      s"M=$m N=$n K=$k aFmt=$aFmt bFmt=$bFmt cFmt=$cFmt loadWMode=${if (loadWMode) 1 else 0} " +
        s"aMxBlk=$aMxBlk bMxBlk=$bMxBlk cMxBlk=$cMxBlk addrMode=${if (addrModeSel == 0) "MAT" else "TILE"} " +
        s"rnd=$rnd seed=$seed strictAddr=$strictAddr maxCycles=$maxCycles " +
        f"regs6=0x${controlWord6}%08X regs7=0x${controlWord7}%08X"
  }

  // ==========================================================================
  // 配置合法性预检（与 HiveCoreCalcConfig errCode 语义同源，用于分派负向路径）
  // ==========================================================================

  /** 返回 Some(errCode) 表示硬件会在 EXECUTE 拍直接置 config-err（不进计算）。
    * 【Fix-7】新增 hasMx4 入参（= cfg.hasMx4），用于门控 fp4 专属 errCode（7 与 MX4×MX8 的 3）：
    *   hasMx4=false 时硬件根本不 elaborate errCode7 / 新 errCode3 分支，金锚亦不得返回。
    * 优先级严格镜像硬件 last-connect 真值（后写覆盖前写，源码越靠后优先级越高）：
    *   7(fp4 blk≠64) > 3(MX4×MX8 混配) > 6 > 5 > 4 > 3(isAMx^isBMx) > 0；本 if/else 链首即最高。
    *   注：errCode 0/1/2 在本金锚仍分列（历史口径），硬件已合并为单一 0，属 #124 已知偏差。
    *   注：MX4×MX8 混配(W4A8)数学/OCP 合法但真数据通路支持列 v2；此处 Some(3) 是【运行期可诊断】
    *   守护的金锚镜像（非永久拒绝），与硬件 Fix-4 的 hasMx4 门控 when 同源、同 last-connect 序。 */
  def precheckConfigErr(p: DebugParams, totalN: Int, hasMx4: Boolean = false): Option[Int] = {
    val aMx = isMxAny(p.aFmt)
    val bMx = isMxAny(p.bFmt)
    val aKLoad = if (isMx8(p.aFmt)) totalN * 2 else totalN * 4
    val bKLoad = if (isMx8(p.bFmt)) totalN * 2 else totalN * 4
    // 【Fix-7】errCode7 移到链首（最高优先级）并按 hasMx4 门控。fp4(E2M1) v1 强制 block=64
    //   （=1 kTile=1 pass=1 scale 块，保 Executor 单广播不变式）。OCP block=32 属 v2（DEFERRED）。
    if (hasMx4 && p.aFmt == DataFormat.MXE2M1 && p.aMxBlk != 64) Some(7)
    else if (hasMx4 && p.bFmt == DataFormat.MXE2M1 && p.bMxBlk != 64) Some(7)
    // 【Fix-8】MX4×MX8 混配 → Some(3)，与硬件 Fix-4 门控 + 优先级一致（7 > 本 3 > 其余）。
    //   条件镜像硬件 isMx4(aFmt) ^ isMx4(bFmt)：a/b 必须同为 fp4(E2M1) 或同为 fp8(E4M3/E5M2)。
    //   注：E4M3×E5M2 双 fp8 混配两侧 isMx4 均 false → 不触发（合法，保留现有 dual-MX8 用例）。
    else if (hasMx4 && (p.aFmt == DataFormat.MXE2M1) != (p.bFmt == DataFormat.MXE2M1)) Some(3)
    else if (aMx && p.aMxBlk > aKLoad) Some(0)
    else if (bMx && p.bMxBlk > bKLoad) Some(1)
    else if (isMxAny(p.cFmt) && p.cMxBlk > totalN) Some(2)
    else if (aMx != bMx) Some(3)
    else if ((aMx || bMx) && !p.loadWMode) Some(4)
    else if (p.aFmt == DataFormat.FP32 || p.bFmt == DataFormat.FP32) Some(5)
    else None
  }

  /** 运行前防呆：不支持的组合给清晰报错（harness 不得崩溃，故在 simulate 之前抛）
    * 【Fix-8】hasMx4 入参（= cfg.hasMx4）门控 MX4×MX8 混配 require：hasMx4=false 时 fp4 输入
    *   本身已被 Plan 的 cfg-aware 守卫（Fix-9）拦为"需 hasMx4"，此处不再重复拦混配（诊断更精确）；
    *   hasMx4=true 时才拦混配，与硬件 Fix-4 门控 when 同源。 */
  def validate(p: DebugParams, totalN: Int, hasMx4: Boolean = false): Unit = {
    require(p.m > 0 && p.n > 0 && p.k > 0, s"M/N/K 必须 > 0，当前 M=${p.m} N=${p.n} K=${p.k}")
    // 【Phase 3】半字节输入拒绝范围收窄：原 `memElemBytes(aFmt)>0` 会把 INT4 与 MXE2M1
    //   一并拒掉；现改为【仅拒 INT4】——MXE2M1(fp4) 已由 bits 口径几何 + decodeInElem +
    //   nibble buildMemory 支持作为输入。对非 MXE2M1 格式判据取值【完全一致】
    //   （INT4 仍拒、其余仍放行），非回归。
    require(p.aFmt != DataFormat.INT4,
      s"aFmt=${p.aFmt}：INT4 半字节整型调试模型不支持作为输入（fp4/MXE2M1 仅 hasMx4 硬件支持，见 Plan 内 cfg-aware 守卫；请改用 mxe4m3/mxe5m2/mxe2m1/fp16/bf16/int8/int16）")
    require(p.bFmt != DataFormat.INT4,
      s"bFmt=${p.bFmt}：INT4 半字节整型调试模型不支持作为输入（fp4/MXE2M1 仅 hasMx4 硬件支持，见 Plan 内 cfg-aware 守卫；请改用 mxe4m3/mxe5m2/mxe2m1/fp16/bf16/int8/int16）")
    require(p.rnd >= 0 && p.rnd <= 4, s"rnd=${p.rnd} 越界（0..4）")
    if (isMxAny(p.aFmt) || isMxAny(p.bFmt)) {
      // 【Phase 3】kPerBeat 由硬编码 totalN*2（MX8）泛化为 kPerBeat(fmt)：
      //   MX8→totalN*2（与旧式逐值相同）、MXE2M1→totalN*4。非 fp4 用例判据不变。
      val kpb = kPerBeat(if (isMxAny(p.aFmt)) p.aFmt else p.bFmt, totalN)
      require(p.k % kpb == 0,
        s"MX 运行要求 K(${p.k}) 能被 kPerBeat=$kpb 整除（一个 kTile = 一个 scale 块 = 单 pass）")
      require(p.loadWMode, "MX 运行必须 loadWMode=1（B 转置存储，硬件 errCode 4 拦截）")
    }
    if (isMxAny(p.aFmt)) require(p.k % p.aMxBlk == 0, s"K(${p.k}) 必须被 aMxBlk(${p.aMxBlk}) 整除")
    if (isMxAny(p.bFmt)) require(p.k % p.bMxBlk == 0, s"K(${p.k}) 必须被 bMxBlk(${p.bMxBlk}) 整除")
    // 【Fix-8】MX4×MX8 混配金锚守护（与硬件 HiveCoreCalcConfig errCode3(Fix-4) 同源，早抛早清晰）：
    //   MX 输入要求 a/b 同为 fp4(E2M1) 或同为 fp8(E4M3/E5M2)。硬件运行期选择子只看 aFmt，
    //   而 b 侧解码器接 io.bFmt，v1 混配会 lane 粒度与解码格式矛盾、静默算错。
    //   ▸ hasMx4 门控：hasMx4=false 时 fp4 输入已被 Plan cfg-aware 守卫(Fix-9)拦为"需 hasMx4"，
    //     不在此重复拦；hasMx4=true 时才拦混配，对应 precheckConfigErr 的 Some(3)（同门控）。
    //   ▸ 定位：MX4×MX8(W4A8)数学/OCP 合法，真数据通路支持列 v2；本 require 是【运行期可诊断】
    //     守护（非永久拒绝），将来支持只需删此 require + 硬件 Fix-4 的 when + 补 W4A8 数据通路。
    //   ▸ E4M3×E5M2 双 fp8 混配两侧 (==MXE2M1) 均 false → 相等 → 不触发（合法，保留 dual-MX8 用例）。
    if (hasMx4)
      require(!(isMxAny(p.aFmt) && isMxAny(p.bFmt)) ||
              (p.aFmt == DataFormat.MXE2M1) == (p.bFmt == DataFormat.MXE2M1),
        s"MX4×MX8 混配 v1 暂不支持（W4A8 属 v2 路线）：a/b 须同为 fp4(E2M1) 或同为 fp8(E4M3/E5M2)，当前 aFmt=${p.aFmt} bFmt=${p.bFmt}")
    // 【Phase 3】fp4 v1 强制 block=64（=1 kTile=1 pass=1 scale 块，保 Executor 单广播
    //   不变式），与硬件 errCode 7 同源。OCP 规范允许 block=32，属 v2 per-half-pass 路线
    //   （DEFERRED）。仅对 MXE2M1 生效，非 fp4 用例无影响。
    if (p.aFmt == DataFormat.MXE2M1)
      require(p.aMxBlk == 64, s"fp4(E2M1) v1 强制 aMxBlk=64（当前 ${p.aMxBlk}）；block=32 属 v2 per-half-pass 路线")
    if (p.bFmt == DataFormat.MXE2M1)
      require(p.bMxBlk == 64, s"fp4(E2M1) v1 强制 bMxBlk=64（当前 ${p.bMxBlk}）；block=32 属 v2 per-half-pass 路线")
    if (isMxAny(p.cFmt)) {
      require(p.cMxBlk <= totalN && totalN % p.cMxBlk == 0,
        s"cMxBlk(${p.cMxBlk}) 必须 <= totalN($totalN) 且整除")
      if (p.cFmt == DataFormat.MXE2M1) require(p.n % 2 == 0, s"mxfp4 输出要求 N(${p.n}) 为偶数")
    }
    // 【#120 B2】addrMode=TILE 放宽：硬件全 RTL 零消费者（#123 审计确认 CalcConfig 与
    //   两个 DMA 一次都没引用 regs(6) bits[14:12]），设置 TILE 不会改变实际 DMA 寻址行为。
    //   内存镜像 buildMemory、golden 计算、六路期望地址序列全部仍按 MAT 语义生成，
    //   不受 addrMode 取值影响。仅供寄存器级 debug 与回读对账。
    //   原 require(p.addrModeSel == 0, ...) 已移除。
  }

  // ==========================================================================
  // 字节内存镜像
  // ==========================================================================

  /** 稀疏字节内存：未写地址读出 0（与真实外部存储零初始化语义一致） */
  final class ByteMemory {
    private val map = mutable.HashMap.empty[Long, Int]
    def storeByte(addr: Long, b: Int): Unit = map(addr) = b & 0xFF
    def loadByte(addr: Long): Int = map.getOrElse(addr, 0)

    /** 写入一个元素的位模式（小端，bytes 字节） */
    def storeElem(addr: Long, bits: Long, bytes: Int): Unit = {
      var v = bits
      var i = 0
      while (i < bytes) { storeByte(addr + i, (v & 0xFF).toInt); v >>>= 8; i += 1 }
    }

    /** 【Phase 3 新增】写入一个亚字节元素（elemBits<8）到 byteAddr 的 bitOff 位处。
      * 读-改-写：保留同字节内相邻元素位（邻 nibble），只覆盖 [bitOff, bitOff+elemBits)。
      * 供 fp4(E2M1) lane=4 供数布局：一个 16-bit lane = 4 个 nibble = 4 个 K 元素，
      * 元素 k 落 byte=k/2、nibble=(k%2)（低 nibble = 偶 k，小端 nibble 序）。 */
    def storeSubByte(byteAddr: Long, bits: Long, bitOff: Int, elemBits: Int): Unit = {
      val mask = (1 << elemBits) - 1                 // elemBits 位掩码（fp4: 0xF）
      val frag = ((bits.toInt & mask) << bitOff) & 0xFF
      val keep = loadByte(byteAddr) & ~(mask << bitOff) & 0xFF
      storeByte(byteAddr, keep | frag)
    }

    /** 从 addr 起读 width 位（小端：addr 处字节 = bits[7:0]），供 DMA rsp 数据 */
    def loadBits(addr: Long, width: Int): BigInt = {
      var acc = BigInt(0)
      val nb = width / 8
      var i = nb - 1
      while (i >= 0) { acc = (acc << 8) | BigInt(loadByte(addr + i)); i -= 1 }
      acc
    }

    def writtenBytes: Int = map.size
  }

  // ==========================================================================
  // 元素编解码
  // ==========================================================================

  private def sext(bits: Long, w: Int): Long = {
    val v = bits & ((1L << w) - 1)
    if (((v >>> (w - 1)) & 1L) == 1L) v - (1L << w) else v
  }

  /** 位模式 → Double（MX 族需传对应块 scale 字节） */
  def decodeElem(f: DataFormat.Type, bits: Long, scaleByte: Int): Double =
    if (f == DataFormat.FP32) HiveSimCommon.fp32BitsToFloat(bits & 0xFFFFFFFFL).toDouble
    else if (f == DataFormat.FP16) HiveSimCommon.fp16ToFloat((bits & 0xFFFFL).toInt).toDouble
    else if (f == DataFormat.BF16) HiveSimCommon.fp32BitsToFloat((bits & 0xFFFFL) << 16).toDouble
    else if (f == DataFormat.INT32) sext(bits, 32).toDouble
    else if (f == DataFormat.INT16) sext(bits, 16).toDouble
    else if (f == DataFormat.INT8) sext(bits, 8).toDouble
    else if (f == DataFormat.INT4) sext(bits, 4).toDouble
    else if (f == DataFormat.MXE4M3) HiveSimCommon.mxE4M3ToFloat((bits & 0xFFL).toInt, scaleByte)
    else if (f == DataFormat.MXE5M2) HiveSimCommon.mxE5M2ToFloat((bits & 0xFFL).toInt, scaleByte)
    else if (f == DataFormat.MXE2M1) {
      // ★★【输出量化域】专用解码（dequant/compare/printRows 经 cFmt=MXE2M1 消费）★★
      //   此处 e==0 → 0.0 的 flush 是【正确建模硬件输出侧 FTZ】，非 bug：
      //   HiveOutCvt 对 E2M1 的 minNormal=2^(1-bias)=2^0=1.0，任何 |v|<1.0（含
      //   e=0/m=1 的 ±0.5）在输出量化时被 flush-to-zero（见本文件 minNormal/compare
      //   #129 FTZ bit-exact 判据）。硬件【从不】把 ±0.5 写进 E2M1 输出码流，故输出域
      //   flush 与硬件逐位一致，HiveOutCvtSpec E2M1 blk8/blk16 绿测依赖此行为。
      //   ▸ 【勿改此分支】：它服务「输出量化域」；fp4【输入】解码走下方
      //     decodeInElem→decodeMxE2M1Input（±0.5 是一等公民输入值，不得 flush）。
      //   ▸ 潜在语义细节（仅记录，无强证据不改动输出行为）：e==0&&scaleByte!=0 时
      //     本可解出 ±0.5×2^(scale-127) 的 subnormal，但硬件输出 FTZ 后不存在该码，
      //     故 flush 到 0 是与硬件对齐的正确选择。
      val c = (bits & 0xFL).toInt
      val sg = (c >> 3) & 1
      val e = (c >> 1) & 0x3
      val mm = c & 1
      val v = if (e == 0 || scaleByte == 0) 0.0
              else (1.0 + mm.toDouble / 2) * math.pow(2, e - 1) * math.pow(2, scaleByte - 127)
      if (sg == 1) -v else v
    } else throw new IllegalArgumentException(s"decodeElem: 未知格式 $f")

  /** 【输入解码域】MXFP4(E2M1) 元素 + 块 scale → Double（±0.5 是一等公民值，不 flush）。
    * 与硬件 Fp32.mxE2M1ToFp32 逐位同源：委托 HiveSimCommon.mxE2M1ToFloat（其格式参数
    * 一律引 MxFormatParams.E2M1：expW=2/mantW=1/bias=1、noInfNan=true、subnormalFtz=false，
    * 经 HiveMxDecodeSpec 16×256 穷举验证与硬件 bit-exact）。
    * ▸ 与 decodeElem 的 MXE2M1 分支【关键区别】：本函数 subnormalFtz=false，
    *   e=0/m=1 解出 ±0.5×2^(scale-127)（对齐硬件输入解码 fp32Sub 路径）；
    *   decodeElem 的 e==0→0 flush 是【输出量化域】语义，二者不可混用。 */
  def decodeMxE2M1Input(bits: Long, scaleByte: Int): Double =
    HiveSimCommon.mxE2M1ToFloat((bits & 0xFL).toInt, scaleByte)

  /** 【输入解码域】统一入口：位模式 → Double（A/B 激励解码，golden 累加消费）。
    * MXE2M1 走 decodeMxE2M1Input（±0.5 正确），其余格式【完全等价于 decodeElem】
    * （逐字委托，非 fp4 用例数值零变化，保 DebugCase/ParamDebugSpec 非回归）。
    * ▸ 域分离纪律：本函数【仅】用于输入解码（aVal/bVal）；输出量化域（dequant/
    *   compare/printRows 经 cFmt）一律继续用 decodeElem，两域不得交叉。 */
  def decodeInElem(f: DataFormat.Type, bits: Long, scaleByte: Int): Double =
    if (f == DataFormat.MXE2M1) decodeMxE2M1Input(bits, scaleByte)
    else decodeElem(f, bits, scaleByte)

  /** Double → 位模式（按格式量化；rnd 用于 bf16/fp16 舍入模式） */
  def encodeElem(f: DataFormat.Type, v: Double, rnd: Int): Long =
    if (f == DataFormat.FP32) (java.lang.Float.floatToRawIntBits(v.toFloat) & 0xFFFFFFFFL)
    else if (f == DataFormat.FP16) (HiveSimCommon.floatToFp16(v.toFloat) & 0xFFFFL)
    else if (f == DataFormat.BF16)
      (HiveSimCommon.swFp32ToBf16(java.lang.Float.floatToRawIntBits(v.toFloat) & 0xFFFFFFFFL, rnd) & 0xFFFFL)
    else if (f == DataFormat.INT32) (math.rint(v).toLong & 0xFFFFFFFFL)
    else if (f == DataFormat.INT16) (math.rint(v).toLong & 0xFFFFL)
    else if (f == DataFormat.INT8) (math.rint(v).toLong & 0xFFL)
    else if (f == DataFormat.INT4) (math.rint(v).toLong & 0xFL)
    else if (f == DataFormat.MXE4M3) (HiveSimCommon.floatToMxE4M3(v) & 0xFFL)
    else if (f == DataFormat.MXE5M2) (HiveSimCommon.floatToMxE5M2(v) & 0xFFL)
    else if (f == DataFormat.MXE2M1) encodeMxE2M1(v)
    else throw new IllegalArgumentException(s"encodeElem: 格式 $f 不支持作为输入编码")

  /** 【输入编码域】float → MXFP4(E2M1) 元素码（scale-free 内蕴值，scale=127）。
    * 在 16 个合法 4-bit 码中暴力挑选【输入解码值】最接近 v 者：用 decodeMxE2M1Input
    * （subnormalFtz=false）⟹ e=0/m=1 的 ±0.5 可被正确编码，与硬件输入解码域一致。
    * ▸ 不复用 HiveSimCommon.floatToMx：其默认 subnormalFtz=true 会把 ±0.5 当 subnormal
    *   flush，无法编码 E2M1 的一等公民值；本函数自包含（仅调用 decodeMxE2M1Input），
    *   遵守「只加法依赖 HiveSimCommon、不修改之」的模型纪律。
    * ▸ 相等误差偏好较小码（c 递增、严格 < 才替换）⟹ 确定性，避免 ±0 抖动。
    * 仅低 4 位有效，高位为 0（与硬件/金锚同约定）。仅 MXE2M1 输入用例调用（additive）。 */
  def encodeMxE2M1(v: Double): Long = {
    var best = 0; var bestErr = Double.MaxValue
    var c = 0
    while (c < 16) {
      val dv = decodeMxE2M1Input(c.toLong, 127)
      val err = math.abs(dv - v)
      if (err < bestErr) { bestErr = err; best = c }
      c += 1
    }
    best.toLong & 0xFL
  }

  /** golden 累加值 → 输出格式位模式（与硬件 cvt 两级舍入链同构） */
  def encodeOut(cFmt: DataFormat.Type, golden: Double, rnd: Int): Long = {
    if (isIntFmt(cFmt)) {
      val iv = math.rint(golden).toLong
      cFmt match {
        case DataFormat.INT32 => iv & 0xFFFFFFFFL
        case DataFormat.INT16 => iv & 0xFFFFL
        case DataFormat.INT8  => iv & 0xFFL
        case _                => iv & 0xFL
      }
    } else {
      val norm = HiveSimCommon.swFpNormalize40to32(
        HiveSimCommon.fp32BitsTo40(java.lang.Float.floatToRawIntBits(golden.toFloat) & 0xFFFFFFFFL))
      if (cFmt == DataFormat.FP32) norm & 0xFFFFFFFFL
      else if (cFmt == DataFormat.FP16) HiveSimCommon.swFp32ToFp16(norm, rnd).toLong & 0xFFFFL
      else if (cFmt == DataFormat.BF16) HiveSimCommon.swFp32ToBf16(norm, rnd).toLong & 0xFFFFL
      else 0L   // MX 量化输出走 swQuantBlock 路径，不用本函数
    }
  }

  // ==========================================================================
  // 计划：几何 / 内存镜像 / 期望地址 / golden
  // ==========================================================================

  final class Plan(val p: DebugParams, val cfg: HiveCoreConfig) {
    val totalN: Int = cfg.totalN
    val cDmaExtW: Int = cfg.cDmaExtW

    // 【Fix-9】fp4 守卫下沉为 cfg-aware：validate 是 object 层（Plan(p,cfg) 之前）、DebugParams
    //   无 supportedFmts/hasMx4 字段，模型无从感知 DUT 是否支持 fp4。若 HiveCoreDebugCase
    //   （默认 supportedFmts、hasMx4=false）跑 -DaFmt=mxfp4，会建 fp4 金锚却撞 hasMx4=false 的 DUT
    //   （E2M1 落 E4M3 臂误码、fmtOk=false、psumReg 停摆）→ 从清晰 fail-fast 退化为不可诊断的
    //   静默失配。Plan 已持有 cfg，故在此早抛。DataFormat.isMx4 返回硬件 Bool，此处用纯 Scala ==。
    if (p.aFmt == DataFormat.MXE2M1 || p.bFmt == DataFormat.MXE2M1)
      require(cfg.hasMx4,
        s"aFmt/bFmt=mxfp4(E2M1) 需硬件 supportedFmts 含 MXE2M1（当前 cfg.hasMx4=${cfg.hasMx4}）；" +
        s"HiveCoreDebugCase 的 dbgCfg 用默认 supportedFmts 不含 fp4 → 请改用 mxe4m3/mxe5m2，" +
        s"或换用 hasMx4=true 的 cfgMxFp4 配置（aFmt=${p.aFmt} bFmt=${p.bFmt}）")

    val aEb: Int = memElemBytes(p.aFmt)
    val bEb: Int = memElemBytes(p.bFmt)
    // 【Phase 3】bits 口径几何单一真源（aEb/bEb 保留供整字节 storeElem 快路径）。
    //   地址偏移一律用 X*aEbits/8：字节对齐族 aEbits 是 8 的倍数 ⟹ X*aEbits/8 == X*aEb
    //   逐值恒等（现有 DebugCase/ParamDebugSpec 几何零变化）；fp4(aEbits=4) 得到正确的
    //   半字节地址（k*4/8=k/2、aKpb*4/8=64*4/8=32），不再因 aEb=0 退化到 0。
    val aEbits: Int = memElemBits(p.aFmt)
    val bEbits: Int = memElemBits(p.bFmt)
    val aKpb: Int = kPerBeat(p.aFmt, totalN)      // A 每拍 K 元素数
    val bKpb: Int = kPerBeat(p.bFmt, totalN)
    val aKTiles: Int = if (isMxAny(p.aFmt)) p.k / aKpb else (p.k + totalN - 1) / totalN
    val bKTiles: Int = if (isMxAny(p.bFmt)) p.k / bKpb else (p.k + totalN - 1) / totalN
    val nTiles: Int = (p.n + totalN - 1) / totalN

    // ---- 期望地址偏移（张量语义推导，不镜像 HiveCoreCalcConfig 谓词链） ----
    // 【Phase 3 bits 口径】下列 4 式由 X*aEb 改为 X*aEbits/8；字节对齐族逐值恒等，
    //   fp4 additive 正确（与硬件 aRowAddressOffset 半字节档 k/2、aColTile fp4=32 对齐）。
    val aRowOff: Long = p.k.toLong * aEbits / 8                 // A 行步进
    val aColOff: Long = aKpb.toLong * aEbits / 8                // A kTile 步进
    val bRowOff: Long = (if (p.loadWMode) p.k else p.n).toLong * bEbits / 8
    val bNTileOff: Long = if (p.loadWMode) totalN.toLong * bRowOff else totalN.toLong * bEbits / 8
    // 【#121 金锚修正（勿改回）：bKTileOff(loadW=1) 由 totalN*bEb 改为 bKpb*bEb。这是 672fb37
    //  同批 6 处参数退化的第 6 处，也是唯一落在测试参考模型（本文件）而非 RTL 的一处；
    //  【限定】硬件的 colTileOffset 在 MX 区间清白（下证），改此行是修正金锚自身，绝非
    //  「为让用例转绿而调金锚掩盖硬件缺陷」。此清白结论【仅限 MX/FP16/BF16/INT16 区间】：
    //  ★★【#124 ① 已修复，本段以下的「硬件仍是编译期常数」表述为【历史陈述】，勿再当现状读】
    //  历史事实（672fb37 起、#124 ① 修复前）：硬件 bColTileAddressOffset 曾是编译期常数
    //  totalN*(bW/8)（bW=16 恒=32、格式无关），而正确的 k-switch 步进应为运行期
    //  totalN*elemsPerLane(bFmt)*memElemBytes(bFmt)；二者相等当且仅当
    //  bW/8 == elemsPerLane*bEb —— MX8(2==2×1)✓、FP16/BF16/INT16(2==1×2)✓，但
    //  INT8(需16)/INT32(需64) 硬件仍给 32 ✗。故当时 INT8/INT32 走 loadW=1 且 kTiles>1 时
    //  kt≥1 的 B 地址全错，是【真 RTL 缺陷】，与本行金锚缺陷落在互补区间（MX 多 kTile
    //  金锚错硬件对；INT8/INT32 多 kTile 金锚对硬件错；无配置同时出错，故两者长期互相
    //  掩护未被发现）。该 RTL 缺陷不属 #121 范围（金锚只改一行），故当时另立任务。
    //  ▸ 现状（#124 ① 修复后）：HiveCoreCalcConfig 已把 a/bColTileAddressOffset 改为
    //    cfg.totalN.U * (elemsPerLaneOf(fmt) * memElemBytesOf(fmt))，与本行 bKpb*bEb
    //    【逐字同式、全格式恒等】（半字节族 memElemBytes=0，两侧同为 0）。互补区间
    //    已合并：MX 多 kTile 与 INT8/INT32 多 kTile 现在【金锚与硬件同时对】。
    //    INT8 现值 16、INT32 现值 64，不再是 32。Section E【E2】据此撤下。
    //  ▸ 本行【不动】的理由（#124 ① 未触碰此处）：#124 ① 改的是 RTL 侧赋值式，本行是
    //    金锚侧闭式，两者本就同式，无需调整；#121 的修正值 bKpb*bEb 保持禁改。
    //  ▸ 仍存在的残余偏差（#124 ① 未覆盖、已由 #139 承接）：loadW=1 时 RdOnly 的
    //    lastBTileStartAddr 累加模型与本行闭式在 nt 维仍有 Δ_nt = nt*(kT*C − R)
    //    （K 非 bKpb 整数倍且 nTiles≥2 时非零）。#124 ① 只归零了 kt 维分量。
    //  ▸ 结构对称（同文件内部一致性）：A 侧数据 aColOff=aKpb*aEb、A 侧 scale
    //    saColOff=aKpb/aMxBlk、B 侧 scale sbColOff=bKpb/bMxBlk —— 三处 kTile 步进一律用
    //    kPerBeat；唯 B 侧数据 kTile 步进退化用 totalN，丢了 elemsPerLane 因子
    //    （MX8 elemsPerLane=2，故 bKpb=2*totalN）。本行镜像 A 侧 aColOff 即可直接推出。
    //  ▸ 四源交叉核对（kTile 步进 = bKpb*bEb，MX 下 = 2*totalN*1 = 32）：旧 HW
    //    （672fb37^ HiveCoreDma MX loadW=1 k-switch = 2*totalN = 32）／#121 当时的 HW
    //    （colTileOffset = totalN*(bW/8) = 32，MX 区间恰好同值）／MxSpec 金锚
    //    （kSliceStep=32）／QuantSpec 金锚（kSliceStep=32）。
    //    ★ #124 ① 后第四源：HW colTileOffset = totalN*elemsPerLane(bFmt)*memElemBytes(bFmt)
    //      —— MX 下仍 = 32（四源继续一致），且 INT8/INT32 档由「HW 独错」变为四源同值。
    //  ▸ 物理不重叠：MX 转置 B 行 n = K 连续字节（bEb=1），一个 kTile = bKpb = 32 元素 = 32
    //    字节；旧值 totalN*bEb = 16 仅半个 kTile，kt≥1 起读会与前一 kTile 重叠
    //    （kt0=[0,32)、kt1=[16,48)），物理不可能。
    //  ▸ 巧合等价（长期隐形机制）：非 MX 下 bKpb == totalN（elemsPerLane=1），两公式数值
    //    恒等，故退化只在 MX（bKpb=32≠totalN=16）且 kTiles>1（kt≥1 才乘 bKTileOff）时暴露
    //    —— 这正是非 MX 与单 kTile 用例一直全绿、该错量长期无害的原因。
    //  实测吻合：DebugCase d3（MX K=64）exp=0x1003D0（由 16 算）vs hw=0x1003E0（由 32 算），
    //   差 +16 = 32−16，首现 dmaB beat#16（kt=1 块起点）；QuantSpec dualMX K=64（金锚 32）
    //   B=64 拍逐拍全绿、零 DMA-B 失配，仅 C 数据路失配（属 #122/#118，与本行无关）。
    //  不动项说明：loadW=0 分支（totalN*bRowOff）保留 —— loadW=0 恒非 MX（errCode4），
    //   bKpb==totalN，属 B5 领域且 LoadWSpec 逐拍硬对账全绿；bNTileOff(loadW=1)=totalN*bRowOff
    //   亦保留 —— 它是 n-tile（N 维）步进、非 kTile 步进，用 totalN 正确。
    //  【Phase 3 bits 口径】bKpb*bEb → bKpb*bEbits/8：字节对齐族 bEbits/8==bEb ⟹
    //   #121 修正值【逐值不变】（MX8: 32*8/8=32=bKpb*bEb），fp4 additive 得 64*4/8=32。
    val bKTileOff: Long = if (p.loadWMode) bKpb.toLong * bEbits / 8 else totalN.toLong * bRowOff

    val saRowOff: Long = (p.k / p.aMxBlk).toLong                // scaleA 行步进 = K/aBlk
    val saColOff: Long = (aKpb / p.aMxBlk).toLong               // scaleA kTile 步进
    val sbRowOff: Long = (p.k / p.bMxBlk).toLong                // scaleB n 向步进 = K/bBlk
    val sbColOff: Long = (bKpb / p.bMxBlk).toLong               // scaleB kTile 步进

    val beatsPerRow: Int = HiveSimCommon.cBeatsPerRow(p.cFmt, totalN, cDmaExtW)
    val expCAddrSeq: Seq[Long] =
      HiveSimCommon.cAddrSeqDense(C_BASE, p.m, p.n, totalN, p.cFmt, cDmaExtW)

    val expCScaleAddrSeq: Seq[Long] =
      if (isMxAny(p.cFmt)) {
        val rowOff = (p.n + p.cMxBlk - 1).toLong / p.cMxBlk
        val colOff = totalN.toLong / p.cMxBlk
        for (nt <- 0 until nTiles; mm <- 0 until p.m) yield SC_BASE + nt * colOff + mm * rowOff
      } else Seq.empty

    // ---- A 期望地址序列：nt 外 × kt 中 × m 内（与 RdOnly isA=true 同序） ----
    val expAAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      for (_ <- 0 until nTiles; kt <- 0 until aKTiles; mm <- 0 until p.m)
        buf += A_BASE + mm * aRowOff + kt * aColOff
      buf.toSeq
    }

    // ---- B 期望地址序列：nt 外 × kt 中 × 块内 totalN 行降序 ----
    val expBAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      for (r <- 0 until nTiles; kt <- 0 until bKTiles) {
        val blockStart = B_BASE + r * bNTileOff + (totalN - 1) * bRowOff + kt * bKTileOff
        for (s <- 0 until totalN) buf += blockStart - s * bRowOff
      }
      buf.toSeq
    }

    val expScaleAAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isMxAny(p.aFmt))
        for (_ <- 0 until nTiles; kt <- 0 until aKTiles; mm <- 0 until p.m)
          buf += SA_BASE + mm * saRowOff + kt * saColOff
      buf.toSeq
    }

    val expScaleBAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isMxAny(p.bFmt))
        for (r <- 0 until nTiles; kt <- 0 until bKTiles) {
          val blockStart = SB_BASE + r * (totalN * sbRowOff) + (totalN - 1) * sbRowOff + kt * sbColOff
          for (s <- 0 until totalN) buf += blockStart - s * sbRowOff
        }
      buf.toSeq
    }

    // ==========================================================================
    // 激励（固定种子，确定性）
    // ==========================================================================
    private val rng = new java.util.Random(p.seed)

    private def nextVal(f: DataFormat.Type): Double =
      if (isIntFmt(f)) (rng.nextInt(16) - 8).toDouble              // [-8,7]
      else if (isMxAny(f)) (rng.nextDouble() * 3.0 - 1.5)          // ~[-1.5,1.5]
      else (rng.nextDouble() * 4.0 - 2.0)                          // ~[-2,2]

    val aCode: Array[Array[Long]] = Array.tabulate(p.m, p.k)((_, _) => encodeElem(p.aFmt, nextVal(p.aFmt), p.rnd))
    val bCode: Array[Array[Long]] = Array.tabulate(p.k, p.n)((_, _) => encodeElem(p.bFmt, nextVal(p.bFmt), p.rnd))

    /** scale 指数 {-1,0,1}（确定性、含正负、跨块跨列变化以暴露路由错位） */
    def scaleExpA(mm: Int, j: Int): Int = ((mm + j + (p.seed % 3).toInt) % 3) - 1
    def scaleExpB(j: Int, nn: Int): Int = ((nn + 2 * j + (p.seed % 3).toInt) % 3) - 1

    val saEntries: Int = p.k / p.aMxBlk
    val sbEntries: Int = p.k / p.bMxBlk
    /** scaleA(mm)(j)，j = k 块下标（块大小 = aMxBlk） */
    val scaleA: Array[Array[Int]] =
      if (isMxAny(p.aFmt)) Array.tabulate(p.m, saEntries)((mm, j) => HiveSimCommon.mxScaleByte(scaleExpA(mm, j)))
      else Array.empty
    /** scaleBVal(nn)(j) —— 注意内存布局是 n 向为行（与硬件 n 向步进 K/bBlk 对应） */
    val scaleB: Array[Array[Int]] =
      if (isMxAny(p.bFmt)) Array.tabulate(p.n, sbEntries)((nn, j) => HiveSimCommon.mxScaleByte(scaleExpB(j, nn)))
      else Array.empty

    /** 硬件实际消费的 scale：A 每 kTile 一个（广播），索引 = kt*(aKpb/aBlk) */
    def effScaleA(mm: Int, k: Int): Int =
      if (!isMxAny(p.aFmt)) 127
      else scaleA(mm)((k / aKpb) * (aKpb / p.aMxBlk))
    def effScaleB(nn: Int, k: Int): Int =
      if (!isMxAny(p.bFmt)) 127
      else scaleB(nn)((k / bKpb) * (bKpb / p.bMxBlk))

    // 【Phase 3 域分离】输入解码走 decodeInElem：MXE2M1 → decodeMxE2M1Input（±0.5 一等
    //   公民值，subnormalFtz=false，与硬件 Fp32.mxE2M1ToFp32 逐位同源）；其余格式完全
    //   等价于原 decodeElem（非 fp4 用例 golden 数值零变化）。输出量化域（dequant/
    //   compare/printRows）仍用 decodeElem，两域不交叉。
    def aVal(mm: Int, k: Int): Double = decodeInElem(p.aFmt, aCode(mm)(k), effScaleA(mm, k))
    def bVal(k: Int, nn: Int): Double = decodeInElem(p.bFmt, bCode(k)(nn), effScaleB(nn, k))

    /** 软件 golden：C(mm)(nn) = Σ_k aVal*bVal（Double 精度，容差对拍基准） */
    def golden(mm: Int, nn: Int): Double = {
      var acc = 0.0
      var k = 0
      while (k < p.k) { acc += aVal(mm, k) * bVal(k, nn); k += 1 }
      acc
    }

    lazy val goldenC: Array[Array[Double]] = Array.tabulate(p.m, p.n)(golden)

    // ==========================================================================
    // 内存镜像构建
    // ==========================================================================
    def buildMemory(): ByteMemory = {
      val mem = new ByteMemory
      // 【Phase 3】A: row-major M×K。字节对齐族（aEbits>=8）走整字节 storeElem，地址
      //   表达式与改动前【逐字相同】（k*aEb == (k*aEbits)/8）⟹ 内存镜像逐字节不变；
      //   亚字节族（fp4，aEbits=4）走 nibble 打包 storeSubByte（byte=k/2、nibble=(k%2)*4）。
      for (mm <- 0 until p.m; k <- 0 until p.k) {
        if (aEbits >= 8) mem.storeElem(A_BASE + mm * aRowOff + k * aEb, aCode(mm)(k), aEb)
        else mem.storeSubByte(A_BASE + mm * aRowOff + (k.toLong * aEbits) / 8,
          aCode(mm)(k), ((k * aEbits) % 8).toInt, aEbits)
      }
      if (p.loadWMode) {   // B 转置：row-major N×K
        for (nn <- 0 until p.n; k <- 0 until p.k) {
          if (bEbits >= 8) mem.storeElem(B_BASE + nn * bRowOff + k * bEb, bCode(k)(nn), bEb)
          else mem.storeSubByte(B_BASE + nn * bRowOff + (k.toLong * bEbits) / 8,
            bCode(k)(nn), ((k * bEbits) % 8).toInt, bEbits)
        }
      } else {             // B 非转置：row-major K×N
        for (k <- 0 until p.k; nn <- 0 until p.n) {
          if (bEbits >= 8) mem.storeElem(B_BASE + k * bRowOff + nn * bEb, bCode(k)(nn), bEb)
          else mem.storeSubByte(B_BASE + k * bRowOff + (nn.toLong * bEbits) / 8,
            bCode(k)(nn), ((nn * bEbits) % 8).toInt, bEbits)
        }
      }
      if (isMxAny(p.aFmt))
        for (mm <- 0 until p.m; j <- 0 until saEntries)
          mem.storeByte(SA_BASE + mm * saRowOff + j, scaleA(mm)(j))
      if (isMxAny(p.bFmt))
        for (nn <- 0 until p.n; j <- 0 until sbEntries)
          mem.storeByte(SB_BASE + nn * sbRowOff + j, scaleB(nn)(j))
      mem
    }

    /** 按地址取 A/B beat（内存保真，绝不按 beat 序号发数） */
    def beatAtA(addr: Long): BigInt = mem.loadBits(addr, cfg.aExtW)
    def beatAtB(addr: Long): BigInt = mem.loadBits(addr, cfg.bExtW)
    def byteAt(addr: Long): BigInt = BigInt(mem.loadByte(addr) & 0xFF)

    private var mem: ByteMemory = new ByteMemory
    def prepare(): ByteMemory = { mem = buildMemory(); mem }
    /** 已镜像的有效字节数（prepare 之后调用） */
    def memByteCount: Int = mem.writtenBytes

    // ==========================================================================
    // 结果解码与对比
    // ==========================================================================

    /** MX 量化输出的反量化（码 + 每 8 元素组 1 字节 E8M0 scale） */
    def dequant(code: Int, scale: Int): Double = decodeElem(p.cFmt, code.toLong, scale)

    final case class CmpSummary(elems: Int, mismatch: Int, maxRelErr: Double,
                                maxAbsErr: Double, details: Seq[String])

    /** 硬件写回日志 → 逐元素与 golden 对比（数值容差；INT 输出走近似精确判据）。
      *
      * 【#128 FTZ 感知 → #129 收紧为 bit-exact】当 `ref` 非零且落在输出格式的 subnormal 区
      * （|ref| < minNormal(cFmt)，MX 量化在反量化域再乘块 scale 2^(s-127)），硬件会
      * flush-to-zero。#128 曾令 refEff=0.0 再走原容差判据，遗留【屏蔽窗口】：refEff=0 ⟹
      * relErr 退化为 absErr ⟹ fail=(absErr>tolRel)&&(absErr>tolAbs)，于是 hw 在 FTZ 区给出
      * 小非零值（量化 tolRel=0.5 时 0.01、FP16 tolRel=1e-2 时 6.1e-5~1e-2 之间）或符号错误
      * （-0.0 vs +0，absErr=0）均被【静默通过】—— #127 若症状落进 FTZ 区会被此工具藏掉。
      * #129 改为：FTZ 命中时【对 hw 原始位做 bit-exact 断言】——必须恰为保号零码
      * （ftzSignedZeroCode，符号位由格式位宽推导），任何非零码/符号不符即判失配。
      * 【阈值推导（minNormal / MX 侧 ×2^(s-127)）保持 #128 不动；tolRel/tolAbs 一字未改】，
      * 仅改「命中之后怎么判」。另加屏蔽面可观测性：打印被 FTZ 分支接管的元素计数。 */
    def compare(cWriteLog: Seq[(Long, BigInt)],
                scaleWriteLog: Seq[(Long, BigInt)]): CmpSummary = {
      val hwBits = HiveSimCommon.parseDenseCWriteLog(cWriteLog, p.cFmt, p.m, p.n, totalN, cDmaExtW)
      val quant = isMxAny(p.cFmt)
      val details = mutable.ArrayBuffer.empty[String]
      var mismatch = 0
      var maxRel = 0.0
      var maxAbs = 0.0
      var ftzTakenOver = 0   // #129 屏蔽面可观测性：被 FTZ bit-exact 分支接管的元素计数
      val tolRel = if (quant) 0.5 else if (p.cFmt == DataFormat.BF16) 2e-2 else 1e-2
      val tolAbs = if (quant) 1e-3 else 1e-6
      // 非 MX 浮点格式的 FTZ 阈值（真实值域，与 ref 同域）；INT 格式无 subnormal，不触发。
      val ftzThreshFlat: Double = if (isIntFmt(p.cFmt)) 0.0 else minNormal(p.cFmt)
      // 元素码位掩码（位宽由 cOutElemBits 推导，非硬编码；FTZ bit-exact 断言取 hw 原始位用）
      val elemMask: Long = (1L << HiveSimCommon.cOutElemBits(p.cFmt)) - 1
      for (mm <- 0 until p.m; nn <- 0 until p.n) {
        val ref = goldenC(mm)(nn)
        // MX 量化输出：本元素所属块的 E8M0 scale 字节（反量化与 FTZ 阈值缩放共用）
        val mxScale: Int =
          if (quant) {
            val nt = nn / totalN
            val scaleBeat = if (scaleWriteLog.size > nt * p.m + mm) scaleWriteLog(nt * p.m + mm)._2 else BigInt(0)
            val grp = (nn - nt * totalN) / p.cMxBlk
            ((scaleBeat >> (8 * grp)) & 0xFF).toInt
          } else 127
        val hw: Double =
          if (quant) dequant(hwBits(mm)(nn).toInt, mxScale)
          else decodeElem(p.cFmt, hwBits(mm)(nn), 127)
        // FTZ 阈值：MX = minNormal(cFmt)·2^(s-127)（镜像 swQuantElem 的 eTgt=e+bias-s<1
        //   ⟺ |v| < 2^(1-bias)·2^(s-127)）；非 MX = minNormal(cFmt)。【#128 推导，未改】
        val ftzThresh: Double =
          if (quant) minNormal(p.cFmt) * math.pow(2.0, (mxScale - 127).toDouble)
          else ftzThreshFlat
        // #129 收紧：FTZ 命中（ref 非零且落 subnormal 区）改为对 hw 原始位做 bit-exact
        //   保号零断言（不再令 refEff=0 走容差，堵住「hw 给小非零值 / 符号错」的屏蔽窗口）。
        //   命中判据（含 ref != 0.0）与阈值推导保持 #128 不动，仅改「命中之后怎么判」。
        val ftzHit = !isIntFmt(p.cFmt) && ref != 0.0 && math.abs(ref) < ftzThresh
        if (ftzHit) {
          ftzTakenOver += 1
          val hwCode = hwBits(mm)(nn) & elemMask
          val expCode = ftzSignedZeroCode(p.cFmt, ref < 0.0)
          if (hwCode != expCode) {
            mismatch += 1
            if (details.size < 32)
              details += f"C($mm,$nn) FTZ-bitexact-FAIL hw=0x${hwCode}%X exp=0x${expCode}%X(保号零) ref=$ref%.6g ftzThresh=$ftzThresh%.3e"
          }
        } else {
          val absErr = math.abs(hw - ref)
          val relErr = if (math.abs(ref) > 1e-9) absErr / math.abs(ref) else absErr
          if (relErr > maxRel) maxRel = relErr
          if (absErr > maxAbs) maxAbs = absErr
          val fail = if (isIntFmt(p.cFmt)) absErr > 0.5 else (relErr > tolRel && absErr > tolAbs)
          if (fail) {
            mismatch += 1
            if (details.size < 32)
              details += f"C($mm,$nn) hw=0x${hwBits(mm)(nn)}%X($hw%.6g) ref=$ref%.6g absErr=$absErr%.3e relErr=$relErr%.3e"
          }
        }
      }
      // #129 屏蔽面可观测性：本次比较有多少元素走了 FTZ bit-exact 路径（按输出格式给）。
      //   若某配置下该计数异常大，提示阈值推导可能有误或数据分布异常，须能被发现而非静默吞掉。
      println(s"[$TAG] FTZ-bitexact 接管计数: cFmt=${p.cFmt} 命中=$ftzTakenOver / 总元素=${p.m * p.n}" +
        s"（ftzThreshFlat=${"%.3e".format(ftzThreshFlat)}${if (quant) "；MX 侧按块 scale ×2^(s-127)" else ""}）")
      CmpSummary(p.m * p.n, mismatch, maxRel, maxAbs, details.toSeq)
    }

    /** 打印 hw/ref 前若干行，便于人工定位 */
    def printRows(cWriteLog: Seq[(Long, BigInt)], rows: Int): Unit = {
      val hwBits = HiveSimCommon.parseDenseCWriteLog(cWriteLog, p.cFmt, p.m, p.n, totalN, cDmaExtW)
      for (mm <- 0 until math.min(p.m, rows)) {
        val hwStr = (0 until math.min(p.n, 16)).map { nn =>
          val v = if (isIntFmt(p.cFmt)) sext(hwBits(mm)(nn), HiveSimCommon.cOutElemBits(p.cFmt)).toString
                  else f"${decodeElem(p.cFmt, hwBits(mm)(nn), 127)}%.3f"
          v
        }.mkString(" ")
        val refStr = (0 until math.min(p.n, 16)).map { nn =>
          if (isIntFmt(p.cFmt)) f"${goldenC(mm)(nn)}%.0f" else f"${goldenC(mm)(nn)}%.3f"
        }.mkString(" ")
        println(s"[$TAG] hw  row$mm: $hwStr")
        println(s"[$TAG] ref row$mm: $refStr")
      }
    }
  }

  // ==========================================================================
  // 波形定位（对 test-name mangle 规则鲁棒：先按 hint 过滤，否则取最新）
  // ==========================================================================

  /** 在 build/chiselsim/<suite> 下查找 trace.fst：优先路径含 hint 的，否则取最近修改的 */
  def findTrace(suite: String, hint: String): Option[String] = {
    val root = Paths.get("build/chiselsim", suite)
    if (!Files.isDirectory(root)) None
    else {
      val found = mutable.ArrayBuffer.empty[(String, Long)]
      var stream: java.util.stream.Stream[Path] = null
      try {
        stream = Files.walk(root)
        val it = stream.iterator()
        while (it.hasNext) {
          val q = it.next()
          if (Files.isRegularFile(q) && q.getFileName.toString == "trace.fst")
            found += ((q.toString.replace('\\', '/'), Files.getLastModifiedTime(q).toMillis))
        }
      } finally if (stream != null) stream.close()
      val preferred = found.filter(_._1.contains(hint))
      val pool = if (preferred.nonEmpty) preferred else found
      pool.sortBy(-_._2).headOption.map(_._1)
    }
  }

  /** 转存波形；源缺失时仅打印 NOTE（复用 HiveSimCommon.copyTrace 的既有语义） */
  def copyWave(suite: String, hint: String, target: String): Unit = {
    findTrace(suite, hint) match {
      case Some(src) =>
        val t = Paths.get(target)
        if (t.getParent != null) Files.createDirectories(t.getParent)
        HiveSimCommon.copyTrace(src, target, TAG)
      case None =>
        println(s"[$TAG] NOTE: 未在 build/chiselsim/$suite 下找到 trace.fst（请加 -DemitFst=1 运行）")
    }
  }
}
