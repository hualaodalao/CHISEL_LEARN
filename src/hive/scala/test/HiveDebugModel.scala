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
  *   scaleA  : row-major M×(K/aBlk) 字节（aBlk = 16<<aMxBlkSel；MXBlockSize 收窄后 sel=0→16、sel=1→32）
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

  /** 每 16-bit lane 打包的元素数：单字节族（INT8/MXE4M3/MXE5M2）dual-lane=2、
    * 半字节族（INT4/MXE2M1）quad-lane=4、其余=1。
    * 与硬件 DataFormat.elemsPerLane / elemsPerLaneLit / HiveCoreCalcConfig.elemsPerLaneOf 同源。
    * 【Phase 3 INT 生态同步】判据由「MXE2M1→4 / isMx8→2」泛化为 isHalfB/is1B 集合语义，
    * 使 INT4→4、INT8→2 与 MXE2M1→4、MX8→2 共用同一张打包表；对现有 MX/FP 格式取值逐字不变。 */
  def elemsPerLane(f: DataFormat.Type): Int =
    if (f == DataFormat.MXE2M1 || f == DataFormat.INT4) 4
    else if (isMx8(f) || f == DataFormat.INT8) 2
    else 1

  /** 每拍推进的 K 元素数（= kTile 粒度）：INT8/MX8 = totalN*2、INT4/MXE2M1 = totalN*4、其余 = totalN */
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
      testHint: String,
      // (#164) 新 reg5 的 bits[21]/[20]。放在参数表【末尾并带默认值】，故全部既有构造点
      //   （QuickSpec.runCase、IntSpec.runIntGemm、IntSpec.runIntErrCase，三处都用具名实参）
      //   零改动即可继续编译。
      // ★默认 false 的等价性论证（不是「大概没影响」，是可核实的三条）：
      //   ① 打包式里这两位是 `if (warmup) 1L<<21 else 0L` ⟹ 默认下恒为 0，与排布迁移前
      //      （reg6/reg7 里根本没有这两位）产出的控制字【逐位相同】；
      //   ② main 侧【无任何硬件消费者】：`grep -rn "warmup\|warmdown" src/hive/scala/main/`
      //      只命中 HiveInterface.scala 的字段声明(:148-149)、reg5 写接线(:163-164) 与
      //      读回 Cat(:193-194) 三类共 6 行，没有第七处 ⟹ 这两位不进任何计算/地址/errCode
      //      逻辑，硬件行为与「字段不存在」完全一致；
      //   ③ 金锚侧除 controlWord5 的打包外【不读】这两个字段（validate/hwErrCode/Plan 均
      //      未引用）⟹ 期望地址序列、期望码、归零判据全部不受影响。
      //   本轮按裁定【不新增用例】，加字段只为让金锚控制字与寄存器契约同形。
      warmup: Boolean = false,
      warmdown: Boolean = false,
      // 【P0-2 gather 非对齐用例】A/B 基址参数化（默认对齐基址 = 既有常量）。
      //   放在末尾带默认值 ⟹ 全部既有构造点零改动。gather 读使非对齐基址合法：
      //   DMA 发对齐多发读 + 内部移位重组，金锚序列/内存映像随基址平移。
      aBase: Long = A_BASE,
      bBase: Long = B_BASE) {

    // 【#144 blk 解码跟随 MXBlockSize 枚举收窄】枚举已由 {M8=0,M16=1,M32=2,M64=3}（bytes=8<<sel）
    //   收窄为 {M16=0,M32=1}，故 sel→bytes 的定义式相应改为 16<<sel：sel=0→16(M16)、sel=1→32(M32)。
    //   由张量语义（新枚举基档 M16=16 元素、每 +1 档翻倍）推导，与 main 侧 getBlockSize 的
    //   {M32→32, otherwise→16} 映射同源；旧 8<<sel 会把 sel=1 误解码为 16、sel=0 误解码为 8，
    //   导致金锚 scale 地址步进/量化分组与硬件失同步（QuickSpec 假红根因）。
    def aMxBlk: Int = 16 << aMxBlkSel
    def bMxBlk: Int = 16 << bMxBlkSel
    def cMxBlk: Int = 16 << cMxBlkSel

    /** (#164) 单一控制字 **reg5** —— 取代原 controlWord6(旧 regs6) + controlWord7(旧 regs7) 两条。
      *
      * ★金锚铁律：下列位域【由寄存器契约独立推导】，逐位取自 HiveInterface.scala 的
      *   `wregs` `is(5.U)` 分支（写侧，:158-172）并与 `rregs` `is(5.U)` 的 Cat（读侧，
      *   :189-197）交叉核对，**不是**以 RTL 读回值反推拟合。两侧已核实逐位镜像：
      *     [31:29] 保留 0     | [28]    loadWMode    | [27]    保留 0
      *     [26]    cMxBlk     | [25]    保留 0       | [24]    bMxBlk
      *     [23]    保留 0     | [22]    aMxBlk       | [21]    warmup
      *     [20]    warmdown   | [19]    保留 0       | [18:16] rnd
      *     [15]    保留 0     | [14]    cAddrMode    | [13]    bAddrMode
      *     [12]    aAddrMode  | [11:8]  cFmt         | [7:4]   bFmt      | [3:0] aFmt
      *   Cat 总宽核算：3+1+1+1+1+1+1+1+1+1+1+3+1+1+1+1+4+4+4 = **32** = `ret` 声明宽度 ✓
      *   （各枚举宽度：DataFormat 10 值→4-bit、RoundingMode 5 值→3-bit、AddrMode 2 值→1-bit、
      *     MXBlockSize {M16=Value(0.U),M32=Value(1.U)}→1-bit）
      *
      * ★迁移要点（旧 → 新）：
      *   · rnd：旧 regs7 bits[14:12]（<<12）→ 新 reg5 bits[18:16]（**<<16**）；
      *   · cFmt/bFmt/aFmt：【域内位置不变】(11:8)/(7:4)/(3:0)，只是宿主寄存器 regs7 → reg5；
      *   · loadWMode / 三个 MxBlk / 三个 addrMode：位号不变(28/26/24/22/14/13/12)，宿主 regs6 → reg5；
      *   · warmup(21) / warmdown(20)：新增，默认 0（等价性见上方字段注释）；
      *   · aStride/bStride/cStride：已被 main 侧【注释删除】（HiveInterface:130-132），
      *     故旧文档串里的 `strides(11,0)=0` 一并删除 —— bits[11:0] 现在整段是三个 4-bit fmt 域。
      * 注：三个 MxBlk 的掩码沿用 `& 0x3`（未收窄为 `& 0x1`）。对 sel∈{0,1}（MXBlockSize 收窄后
      *   的【全部】合法取值；QuickSpec.blkToSel 由枚举成员构造、MxSpec 有 require(blkSel 0..1)
      *   前置防呆）两种掩码产出【逐位相同】⟹ 零行为变化。sel>=2 在旧代码下同样是错的
      *   （金锚 aMxBlk=16<<2=64 不在枚举内），属既存遗留，本轮不扩大范围，已在报告登记。 */
    def controlWord5: Long =
      (if (loadWMode) 1L << 28 else 0L) |
        ((cMxBlkSel.toLong & 0x3) << 26) | ((bMxBlkSel.toLong & 0x3) << 24) |
        ((aMxBlkSel.toLong & 0x3) << 22) |
        (if (warmup)   1L << 21 else 0L) |
        (if (warmdown) 1L << 20 else 0L) |
        ((rnd.toLong & 0x7) << 16) |
        ((addrModeSel.toLong & 0x1) << 14) | ((addrModeSel.toLong & 0x1) << 13) |
        ((addrModeSel.toLong & 0x1) << 12) |
        ((cFmt.litValue.toLong & 0xF) << 8) |
        ((bFmt.litValue.toLong & 0xF) << 4) | (aFmt.litValue.toLong & 0xF)

    /** 生效参数一览（启动时打印） */
    def banner: String =
      s"M=$m N=$n K=$k aFmt=$aFmt bFmt=$bFmt cFmt=$cFmt loadWMode=${if (loadWMode) 1 else 0} " +
        s"aMxBlk=$aMxBlk bMxBlk=$bMxBlk cMxBlk=$cMxBlk addrMode=${if (addrModeSel == 0) "MAT" else "TILE"} " +
        s"rnd=$rnd seed=$seed strictAddr=$strictAddr maxCycles=$maxCycles " +
        f"reg5=0x${controlWord5}%08X"
      // (#164) 本行是【已打印到 stdout】的（QuickSpec.printHealth :294 `生效配置: ${p.banner}`）。
      //   按任务书只做 `regs6=0x… regs7=0x…` → `reg5=0x…` 的最小替换，【不】把 warmup/warmdown
      //   加进 banner：两者默认恒 0、本轮无用例置位，加进来只会给归一化基线多添一处待归因的
      //   文本差异。日后若注册 warmup/warmdown 用例，再按需把它们加回本行。
  }

  // ==========================================================================
  // 配置合法性预检（与 HiveCoreCalcConfig errCode 语义同源，用于分派负向路径）
  // ==========================================================================

  /** 【单一真源 · P0-2 去双源】HiveCoreCalcConfig errComb 裸块的权威软件镜像。
    * 原为 HiveCoreQuickSpec.hwErrCode（private），现提升为 HiveDebugModel 公共 def，令
    * precheckConfigErr 与 QuickSpec 体检共用同一份镜像，消灭"两份互不一致的 errCode 软件镜像"。
    *
    * 逐位镜像硬件 last-connect 真值序（后写覆盖前写，源码越靠后优先级越高），用【顺序 if +
    * 后写覆盖 e】编码该序（区别于旧 precheckConfigErr 的 else-if 链首最高）：
    *   3(MX4xMX8 mix) > 8(K not divisible) > 10(cAddr alignment) > 7(capacity, RTL-only)
    *     > 5 > 4 > 3(isAMx^isBMx) > 0.
    *   (#178 UPDATE) errCode 11 (int/non-int cross-boundary) inserted after code-8,
    *   before Fix-4. Full order: 3(Fix-4) > 11 > 8 > 10 > 7 > 5 > 4 > 3(xor) > 0.
    *   Note: code 6 (MXE2M1 output N odd) is commented out in RTL but still mirrored
    *   by this golden model -- known divergence (see #169 report).
    *   (#146) The former `7(fp4 blk!=64)` branch was DELETED (tautology after
    *   MXBlockSize narrowed to {16,32}). However code 7 is NOT free: the CAPACITY
    *   guard (m > cBufferDepth - totalN) still drives errCodeComb := 7.U and is LIVE
    *   in all three emitted netlists. New checks must be allocated from 12 upwards
    *   (11 is now LIVE: int/non-int cross-boundary, #177/#178)
    *   (see problem.md:93-94). Codes 1/2 stay unreachable and are never revived.
    * ▸ errCode 0/1/2：硬件把 a/b/cMxBlockSize 三违约【合并】为单一 errMXLargerThanKLoadNum→errCode 0，
    *   故 1/2 永不可达（QuantSpec「blk32 over totalN」旧期望 2 实得 0 的红项根因，#124）；本镜像
    *   合并为单一 Some(0)，与 RTL 一致。
    * ▸ hasMx4 gating: the mix code3 and code8 branches only elaborate in hardware
    *   when cfg.hasMx4=true; this mirror uses the same gate. (#146) The third
    *   hasMx4-gated branch (code7) has been deleted.
    * ▸ code8（P0-1）：fp4(E2M1) 要求 K 被 kPerBeat(=totalN×4=64) 整除，否则 Executor kTiles 地板除
    *   静默丢尾（K=96）或 16-bit 回绕（K<64）；硬件 errCode8 运行期拒绝，金锚镜像之。 */
  def hwErrCode(p: DebugParams, totalN: Int, hasMx4: Boolean = false,
                 cAddr: Long = C_BASE, cDmaExtW: Int = 256): Option[Int] = {
    def kLoadNum(f: DataFormat.Type): Int = if (isMx8(f) || f == DataFormat.INT8) totalN * 2 else totalN * 4  // 镜像 getKLoadNum（is1B 语义）
    val aMx = isMxAny(p.aFmt); val bMx = isMxAny(p.bFmt); var e = Option.empty[Int]
    if ((aMx && p.aMxBlk > kLoadNum(p.aFmt)) || (bMx && p.bMxBlk > kLoadNum(p.bFmt)) ||
        (isMxAny(p.cFmt) && p.cMxBlk > totalN)) e = Some(0)   // RTL 把三个 blk 违约合并成单一 errCode 0
    if (aMx != bMx) e = Some(3)
    if ((aMx || bMx) && !p.loadWMode) e = Some(4)
    if (p.aFmt == DataFormat.FP32 || p.bFmt == DataFormat.FP32) e = Some(5)
    if (p.cFmt == DataFormat.MXE2M1 && (p.n & 1) == 1) e = Some(6)
    // (#169/#170) errCode 10: cAddr not aligned to dmaBytes (= cDmaExtW/8).
    //   Derived independently from AXI byte-lane contract: wstrb[i] selects
    //   wdata[8i+7:8i]; a DMA write beat covers dmaBytes consecutive byte lanes
    //   starting at addr mod dmaBytes. If addr mod dmaBytes != 0, the shifted
    //   strb+data pair would cross a bus-word boundary, silently losing bytes.
    //   Guard: cAddr % dmaBytes == 0 (equivalently cAddr[log2Up(dmaBytes)-1:0] == 0).
    //   Priority: 8 > 10 > 7 > 5 > 4 > 3 > 0 (per #169 netlist-verified order).
    //   (#178 UPDATE) Full: 3(Fix-4) > 11 > 8 > 10 > 7 > 5 > 4 > 3(xor) > 0.
    //   In this last-wins chain, 10 is placed AFTER 6 and BEFORE 8.
    locally { val dmaBytes = cDmaExtW / 8
      if (dmaBytes > 0 && (cAddr % dmaBytes) != 0) e = Some(10) }
    // [P0-1] fp4(E2M1) K not divisible by kPerBeat -> code8. Placed BEFORE the
    //   mix code3 below, i.e. lower priority, matching the hardware
    //   last-connect order.
    // [Phase 3 INT 生态同步] errCode 8 守护扩展到 INT8/INT4：与硬件 HiveCoreCalcConfig
    //   的 if(cfg.hasInt) 块同构（INT8 kPerBeat=totalN*2、INT4 kPerBeat=totalN*4，均由
    //   kPerBeat(fmt,totalN) 单一真源给出）。MX 部分仍由 hasMx4 门控；INT 部分不设门控——
    //   aFmt 为 INT8/INT4 即隐含 cfg.hasInt（默认 supportedFmts 含二者），镜像与 RTL 一致。
    val aPackedInt = p.aFmt == DataFormat.INT8 || p.aFmt == DataFormat.INT4
    if (((hasMx4 && isMxAny(p.aFmt)) || aPackedInt) &&
        (p.k % kPerBeat(p.aFmt, totalN) != 0 || p.k == 0)) e = Some(8)
    // (#178) errCode 11: int/non-int cross-boundary guard (V5-only XOR).
    //   Derived independently from register contract semantics:
    //   cIsIntOut = cFmt is an integer output format (INT8/INT16/INT4/INT32)
    //   runIsInt  = aFmt is NOT float (FP32/FP16/BF16) AND NOT MX (E4M3/E5M2/E2M1)
    //               -> aFmt is integer or reserved encoding
    //   Guard: runIsInt XOR cIsIntOut -> errCode 11 (no cfg gating).
    //   Placed AFTER code-8, BEFORE Fix-4 code3: priority 3(Fix-4) > 11 > 8.
    val cIsIntOut = isIntFmt(p.cFmt)
    val runIsInt = !isFloatFmt(p.aFmt) && !isMxAny(p.aFmt)
    if (runIsInt != cIsIntOut) e = Some(11)
    // [Fix-4 mirror] MX4xMX8 mix (isMx4(aFmt) ^ isMx4(bFmt)) -> code 3, gated by hasMx4.
    if (hasMx4 && (p.aFmt == DataFormat.MXE2M1) != (p.bFmt == DataFormat.MXE2M1)) e = Some(3)
    // (#146) errCode 7 REMOVED from the mirror. The hardware block it mirrored
    //   (`isMx4(fmt) && getBlockSize(blkSel) =/= 64`) became a TAUTOLOGY once the
    //   MXBlockSize enum was narrowed to {sel=0 -> 16, sel=1 -> 32}: no encodable
    //   blocksize can ever equal 64, so every fp4 run was rejected at runtime,
    //   unconditionally. Because this mirror carried the same tautology, RTL and
    //   anchor agreed and no test ever went red -- fp4 was permanently unusable
    //   while looking covered. fp4 now supports blk in {16, 32} like fp8; code 8
    //   is the only fp4 geometry guard left. Code 7 is NOT free: the CAPACITY guard
    //   (m > cBufferDepth - totalN) still drives errCodeComb := 7.U and is LIVE in
    //   all three emitted netlists. New checks are allocated from 12 upwards
    //   (11 is now LIVE: int/non-int cross-boundary, #177/#178);
    //   (see problem.md:93-94); codes 1/2 are never revived.
    e
  }

  /** 返回 Some(errCode) 表示硬件会在 EXECUTE 拍直接置 config-err（不进计算）。
    * 【P0-2 去双源】改为委托权威单一真源 hwErrCode（原 else-if 分列 Some(0)/Some(1)/Some(2) 的
    *   历史口径已废弃——硬件把三 blk 违约合并为单一 errCode 0，且旧链缺 errCode6、优先级与 RTL
    *   不符，属 #124 已知偏差）。委托后 precheckConfigErr 与 QuickSpec 体检逐位一致。 */
  def precheckConfigErr(p: DebugParams, totalN: Int, hasMx4: Boolean = false,
                         cAddr: Long = C_BASE, cDmaExtW: Int = 256): Option[Int] =
    hwErrCode(p, totalN, hasMx4, cAddr, cDmaExtW)

  /** 运行前防呆：不支持的组合给清晰报错（harness 不得崩溃，故在 simulate 之前抛）
    * 【Fix-8】hasMx4 入参（= cfg.hasMx4）门控 MX4×MX8 混配 require：hasMx4=false 时 fp4 输入
    *   本身已被 Plan 的 cfg-aware 守卫（Fix-9）拦为"需 hasMx4"，此处不再重复拦混配（诊断更精确）；
    *   hasMx4=true 时才拦混配，与硬件 Fix-4 门控 when 同源。 */
  def validate(p: DebugParams, totalN: Int, hasMx4: Boolean = false): Unit = {
    require(p.m > 0 && p.n > 0 && p.k > 0, s"M/N/K 必须 > 0，当前 M=${p.m} N=${p.n} K=${p.k}")
    // 【Phase 3 INT 生态同步】解禁 INT4 输入：原两条 require(aFmt/bFmt != INT4) 已删除。
    //   INT4 现由 bits 口径几何（memElemBits=4）+ decodeInElem sext(_,4) + nibble buildMemory
    //   完整支持作为输入格式（quad-lane 打包=4），与 MXE2M1 同构。
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
    // (#146) fp4 no longer forces block=64. The hardware errCode 7 that this
    //   require used to mirror has been DELETED: MXBlockSize is a 2-level enum
    //   {sel=0 -> 16, sel=1 -> 32}, so "blk must be 64" was unsatisfiable and
    //   every fp4 case was rejected before it could ever run. fp4 now accepts
    //   blk in {16, 32} exactly like fp8, so the mirror guard becomes "blk must
    //   be one of the two values the register field can actually encode" (K
    //   divisibility is already required above). errCode 8 (K not divisible by
    //   kPerBeat) remains the only fp4-specific hardware geometry guard.
    if (p.aFmt == DataFormat.MXE2M1)
      require(p.aMxBlk == 16 || p.aMxBlk == 32,
        s"fp4(E2M1) aMxBlk must be 16 or 32 (MXBlockSize is a 2-level enum {M16, M32}), got ${p.aMxBlk}")
    if (p.bFmt == DataFormat.MXE2M1)
      require(p.bMxBlk == 16 || p.bMxBlk == 32,
        s"fp4(E2M1) bMxBlk must be 16 or 32 (MXBlockSize is a 2-level enum {M16, M32}), got ${p.bMxBlk}")
    if (isMxAny(p.cFmt)) {
      require(p.cMxBlk <= totalN && totalN % p.cMxBlk == 0,
        s"cMxBlk(${p.cMxBlk}) 必须 <= totalN($totalN) 且整除")
      if (p.cFmt == DataFormat.MXE2M1) require(p.n % 2 == 0, s"mxfp4 输出要求 N(${p.n}) 为偶数")
    }
    // 【#120 B2】addrMode=TILE 放宽：硬件全 RTL 零消费者（#123 审计确认 CalcConfig 与
    //   两个 DMA 一次都没引用 addrMode 位域；(#164) 宿主由旧 regs(6) bits[14:12] 迁到
    //   reg5 bits[14:12]，位号不变、依旧零消费者），设置 TILE 不会改变实际 DMA 寻址行为。
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
    //   无 supportedFmts/hasMx4 字段，模型无从感知 DUT 是否支持 fp4。若某个 cfg 显式传入不含
    //   MXE2M1 的 supportedFmts（hasMx4=false）却跑 aFmt/bFmt=mxfp4，会建 fp4 金锚却撞 hasMx4=false
    //   的 DUT（E2M1 落 E4M3 臂误码、fmtOk=false、psumReg 停摆）→ 从清晰 fail-fast 退化为
    //   不可诊断的静默失配。Plan 已持有 cfg，故在此早抛。DataFormat.isMx4 返回硬件 Bool，
    //   此处用纯 Scala ==。
    // (#155 MJ1) 订正：自本轮起 HiveCoreConfig 的【默认 supportedFmts 已含 MXE2M1】，且新增
    //   require(!hasMx8 || hasMx4) 使 hasMx4 ⟺ hasMx8 ⟹ 「默认集不含 fp4」不再是真陈述，
    //   本 require 在默认集下恒不触发；它现在的实际职责是拦住【显式传入 MX-only-fp8 子集】
    //   这类人为构造的 cfg（这类 cfg 已被 HiveCoreConfig 的新 require 在更早处拦下，故此
    //   require 退化为纵深防御的第二道）。下方 require 的消息原文【逐字保留不改】，以保持
    //   用户上一轮报错现场的可追溯性；其中「HiveCoreDebugCase 的 dbgCfg 用默认 supportedFmts
    //   不含 fp4」一句属历史陈述（该文件已在本仓 10 个 ` D` 已删测试之列，且默认集已变）。
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
    // 【Phase 3 INT 生态同步】kTiles 判据由 isMxAny 泛化为「打包格式」（含 INT8/INT4）：
    //   打包格式每拍推进 kPerBeat 个 K → kTiles = k / kPerBeat（地板除）；非打包格式 ceil(k/totalN)。
    //   与硬件 HiveCoreCalcConfig.kTile / HiveCoreExecutor.newKTiles 同源。
    val aKTiles: Int = if (isMxAny(p.aFmt) || p.aFmt == DataFormat.INT8 || p.aFmt == DataFormat.INT4) p.k / aKpb else (p.k + totalN - 1) / totalN
    val bKTiles: Int = if (isMxAny(p.bFmt) || p.bFmt == DataFormat.INT8 || p.bFmt == DataFormat.INT4) p.k / bKpb else (p.k + totalN - 1) / totalN
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

    // ---- 【P0-1/P0-2 gather 物理序列】读 DMA 对非对齐逻辑拍发【对齐多发读】：
    //   逻辑拍地址 L（portBytes 字节负载）的物理读序列由对齐契约独立推导——
    //   外部读按 portBytes 对齐取字，覆盖 [L, L+portBytes) 需读：
    //     off = L mod portBytes == 0 : [L]（快路径，单读）
    //     off != 0                   : [L-off, L-off+portBytes]（两个对齐字，DMA 内部移位重组）
    //   与 RTL 实现无关（契约级推导）；A/B portBytes=32（256-bit），scale portBytes=4（32-bit）。
    def gatherPhys(addr: Long, portBytes: Long): Seq[Long] = {
      val off = addr % portBytes
      if (off == 0) Seq(addr) else Seq(addr - off, addr - off + portBytes)
    }

    // ---- A 期望地址序列：nt 外 × kt 中 × m 内（与 RdOnly isA=true 同序）→ gather 物理序列 ----
    val expAAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      for (_ <- 0 until nTiles; kt <- 0 until aKTiles; mm <- 0 until p.m)
        buf += p.aBase + mm * aRowOff + kt * aColOff
      buf.toSeq.flatMap(gatherPhys(_, 32L))
    }

    // ---- B 期望地址序列：nt 外 × kt 中 × 块内 totalN 行降序 → gather 物理序列 ----
    val expBAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      for (r <- 0 until nTiles; kt <- 0 until bKTiles) {
        val blockStart = p.bBase + r * bNTileOff + (totalN - 1) * bRowOff + kt * bKTileOff
        for (s <- 0 until totalN) buf += blockStart - s * bRowOff
      }
      buf.toSeq.flatMap(gatherPhys(_, 32L))
    }

    val expScaleAAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isMxAny(p.aFmt))
        for (_ <- 0 until nTiles; kt <- 0 until aKTiles; mm <- 0 until p.m)
          buf += SA_BASE + mm * saRowOff + kt * saColOff
      buf.toSeq.flatMap(gatherPhys(_, 4L))
    }

    val expScaleBAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isMxAny(p.bFmt))
        for (r <- 0 until nTiles; kt <- 0 until bKTiles) {
          val blockStart = SB_BASE + r * (totalN * sbRowOff) + (totalN - 1) * sbRowOff + kt * sbColOff
          for (s <- 0 until totalN) buf += blockStart - s * sbRowOff
        }
      buf.toSeq.flatMap(gatherPhys(_, 4L))
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

    // ------------------------------------------------------------------------
    // (#146) effScaleA / effScaleB -- the E8M0 scale that the OCP MX definition
    //   assigns to one element. DERIVED FROM THE SPEC, NOT FITTED TO THE RTL.
    // ------------------------------------------------------------------------
    // SPEC: the K axis of every row is partitioned into contiguous blocks of
    //   aMxBlk elements; block j owns [j*aMxBlk, (j+1)*aMxBlk) and carries the
    //   single scale byte scaleA(mm)(j). Hence the scale of element (mm, k) is
    //     scaleA(mm)(k / aMxBlk)                       (integer division)
    //   which is total and in range because validate() requires K % aMxBlk == 0
    //   and scaleA has exactly saEntries = K/aMxBlk columns. Same for B with
    //   scaleB(nn)(k / bMxBlk) -- the B tensor is stored N-major but the block
    //   partition is along K either way.
    // OLD FORM (removed): scaleA(mm)((k/aKpb) * (aKpb/aMxBlk)), i.e. "the index of
    //   the FIRST block of the kTile containing k" = ONE scale per kTile,
    //   broadcast over the whole kTile. That is only correct under the invariant
    //   aMxBlk == aKpb ("1 kTile = 1 scale block = single pass"), which the
    //   deleted errCode 7 used to force for fp4 and which the fp8 blk=32 cases
    //   satisfy by construction.
    // EXACT DEGENERACY CONDITION: new == old for every k in [0, K)
    //   IFF  aMxBlk == aKpb.
    //   Proof. Write k = kt*aKpb + r with 0 <= r < aKpb. errCode 0 enforces
    //   aMxBlk <= kLoadNum == aKpb and both are powers of two, so aMxBlk divides
    //   aKpb; let q = aKpb/aMxBlk >= 1. Then old = kt*q and
    //   new = (kt*aKpb + r)/aMxBlk = kt*q + r/aMxBlk. Equality for all r requires
    //   r/aMxBlk == 0 for all r < aKpb, i.e. aKpb <= aMxBlk, i.e. q == 1.
    //   (The corner aMxBlk > aKpb, unreachable past errCode 0, would give q = 0
    //   hence old == 0 for all k while new grows -- unequal as well.)
    // IMPACT ON THE EXISTING SUITE (per-case, not hand-waved):
    //   fp8 (MXE4M3/MXE5M2), totalN=16 -> aKpb = bKpb = 32.
    //     blk=32: aMxBlk == aKpb -> old == new IDENTICALLY -> every existing
    //            blk=32 case keeps the exact same golden values. ZERO impact.
    //     blk=16: q = 2, old = 2*floor(k/32) visits only EVEN block indices
    //            (0, 2, 4, ...) and never the odd ones, i.e. the second half of
    //            each kTile was silently given the first half's scale. The ANCHOR
    //            was the wrong side; new = floor(k/16) is the spec value. This is
    //            what makes an end-to-end blk=16 case possible at all.
    //   fp4 (MXE2M1) -> aKpb = bKpb = 64. blk=32: q = 2, same even-block defect;
    //     blk=16: q = 4, old visits only multiples of 4. Both fixed. (No fp4 suite
    //     currently exists in the repo, so this is preventive.)
    // INDEPENDENT CROSS-CHECK against the datapath (recorded for audit only; the
    //   anchor above does NOT depend on it): lane i of kTile kt carries elements
    //   k = kt*kPerBeat + i*step .. +step-1 with step = elemsPerLane. Because
    //   blk >= step and blk divides kPerBeat, a lane never straddles a block
    //   boundary, so its block index is
    //     (kt*kPerBeat + i*step)/blk = kt*(kPerBeat/blk) + (i*step)/blk
    //                                = kt*(kPerBeat/blk) + idx
    //   where idx = (i*step)/blk is exactly the byte slot HiveCoreExecutor
    //   selects out of the scale payload, and the DMA word read at
    //   base + row*(K/blk) + kt*(kPerBeat/blk) puts block kt*(kPerBeat/blk)+j of
    //   that row into byte j. Byte idx therefore is block k/blk -- the spec value.
    def effScaleA(mm: Int, k: Int): Int =
      if (!isMxAny(p.aFmt)) 127
      else scaleA(mm)(k / p.aMxBlk)
    def effScaleB(nn: Int, k: Int): Int =
      if (!isMxAny(p.bFmt)) 127
      else scaleB(nn)(k / p.bMxBlk)

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

    // ========================================================================
    // (#151 F4) 独立期望码链：张量语义 → OCP MX 规范 → 期望 E8M0 scale + 期望元素码
    // ========================================================================
    // 动机：原 compare() 的 FTZ 判据是【幅值阈值启发式】|ref| < minNormal(cFmt)·2^(s-127)，
    //   它把「该冲刷成保号零」当成一个纯幅值条件。这在【不产出次正规】的格式（E4M3/E5M2/
    //   FP16/BF16）上恰好精确（underflow 由 eTgt<1 在舍入【之前】判定 ⟹ y'<minNormal 与
    //   模式无关），但在 E2M1 上【错 4 倍】：E2M1 minNormal=1.0，而 OCP 把 (e=0,m=1)=±0.5
    //   认定为一等公民，真正的归零区是模式相关的（RNE y'<=0.25 / RTZ y'<0.5 / RMM y'<0.25 /
    //   RUP-RDN 带符号只有严格零）。用 y'<1.0 当判据会把 (0.25S,0.5S) 这段【本该是 0.5S】
    //   的元素继续当保号零做硬断言 ⟹ 修好的 RTL 反而变红，或为过而排除比对制造静默盲区。
    // 手法：干掉阈值启发式，改由【金锚独立算出的期望码】决定 —— 期望码本身是保号零时才走
    //   bit-exact 断言。期望 scale 由 OCP amax 规则从期望 C 行独立算出（复用 swQuantBlock，
    //   与 HiveCoreSimCaseMxSpec.expectedQuantRow 同范式），【全程不读任何硬件写回值】。
    // ========================================================================

    /** (#151 F4) 累加精确性判定（纯张量语义，不读硬件）：fp64 顺序累加的【每一个乘积与
      * 每一个部分和】是否都能被 fp32 无损表示。若全程精确，则任何累加顺序、任何结合性、
      * 任何 >=32 位的中间精度都给出同一个数 ⟹ 金锚 fp64 值与硬件 fp40 累加树的值必然
      * 相同 ⟹ 该元素的期望码可做【逐位】断言（Tier A）。否则金锚与硬件的差是合法的
      * 累加舍入差，逐位断言就等于要求金锚去模拟 RTL 的累加树 —— 那正是「以 RTL 行为为
      * 拟合目标」，违反金锚铁律，故退回容差比对（Tier B）。
      * 典型：fp4(E2M1) 输入值集 ±{0,0.5,...,6} ⟹ 乘积是 0.25 的整数倍、|和| <= 64*36，
      *   仅需 14 bit ⟹ 全程 fp32 精确 ⟹ Tier A 覆盖全部元素。
      * 典型：E4M3×E5M2 值域达 448×57344 ⟹ 部分和一般不精确 ⟹ Tier B（行为与改前相同）。 */
    def goldenExact(mm: Int, nn: Int): Boolean = {
      def fp32Exact(v: Double): Boolean = v.toFloat.toDouble == v
      var acc = 0.0
      var k = 0
      var ok = true
      while (k < p.k) {
        val prod = aVal(mm, k) * bVal(k, nn)
        if (!fp32Exact(prod)) ok = false
        acc += prod
        if (!fp32Exact(acc)) ok = false
        k += 1
      }
      ok
    }

    lazy val goldenCExact: Array[Array[Boolean]] = Array.tabulate(p.m, p.n)(goldenExact)

    /** (#151 F4) 金锚 fp64 值 → fp32 bits → 40→32 规格化（与硬件两级舍入链【同链】）。 */
    def goldenBits(mm: Int, nn: Int): Long = {
      val fp32Bits = java.lang.Float.floatToRawIntBits(goldenC(mm)(nn).toFloat) & 0xFFFFFFFFL
      HiveSimCommon.swFpNormalize40to32(HiveSimCommon.fp32BitsTo40(fp32Bits))
    }

    lazy val goldenCBits: Array[Array[Long]] = Array.tabulate(p.m, p.n)(goldenBits)

    /** (#151 F4) 独立期望量化行：OCP amax 块 scale + 逐元素期望码（含 E2M1 次正规）。
      * nt = n-tile 序号；列 >= nEffTile 为 padding，不进 amax、码输出 0（swQuantBlock 内建）。 */
    def expQuantRow(nt: Int, mm: Int): (Seq[Int], Seq[Int]) = {
      val nEffTile = math.min(totalN, p.n - nt * totalN)
      val row = (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        if (gc < p.n) goldenCBits(mm)(gc) else 0L
      }
      HiveSimCommon.swQuantBlock(row, p.rnd, p.cFmt, p.cMxBlkSel, nEffTile)
    }

    /** 按 (nt*p.m + mm) 索引的期望量化行缓存（仅 MX 输出格式时构造）。 */
    lazy val expQuant: Array[(Seq[Int], Seq[Int])] =
      if (!isMxAny(p.cFmt)) Array.empty
      else Array.tabulate(nTiles * p.m)(i => expQuantRow(i / p.m, i % p.m))

    /** 元素 (mm,nn) 的期望 E8M0 scale 字节（OCP amax 独立算出，不读硬件）。 */
    def expScaleAt(mm: Int, nn: Int): Int = {
      val nt = nn / totalN
      expQuant(nt * p.m + mm)._2((nn - nt * totalN) / 8)
    }

    /** 元素 (mm,nn) 的期望元素码（OCP 量化独立算出，不读硬件）。 */
    def expCodeAt(mm: Int, nn: Int): Int = {
      val nt = nn / totalN
      expQuant(nt * p.m + mm)._1(nn - nt * totalN)
    }

    /** (#151 F4) MX 侧【模式相关归零区】解析判据 —— 直接由 OCP 网格与 swRoundUp/roundUp16
      * 的五模式定义推出，是 expCode 判零的【第二条独立路径】，两者逐元素交叉自证。
      *
      * 设 y' = |ref| / 2^(s-127)。因 y' = 1.f × 2^(eTgt-bias)，E2M1(bias=1) 的次正规区
      * 覆盖 eTgt ∈ {0,-1} ⟹ y' ∈ [0.25, 1.0)，网格 {0, 0.5, 1.0}：
      *   eTgt=-1 → y' ∈ [0.25,0.5)：keep=0, guard=1(原隐含位), sticky=(f!=0), lsb=0
      *   eTgt= 0 → y' ∈ [0.5,1.0) ：keep=1, guard=f 的最高位, sticky=其余, lsb=1
      * 逐模式代入 swRoundUp：
      *   RNE(0) guard&&(sticky||lsb)：eTgt=-1 时 up=sticky ⟹ y'=0.25(f=0,tie) up=0 → 0（偶）；
      *            y'∈(0.25,0.5) up=1 → 0.5 ⟹ 归零区 y' <= 0.25
      *   RTZ(1) 恒 false：eTgt=-1 → keep=0 → 0 ⟹ 归零区 y' < 0.5（整个 [0.25,0.5) 截断归 0）
      *   RDN(2) neg&&(guard||sticky)：负数 eTgt=-1 → up=1（guard=1）→ -0.5，连 tie 点 0.25 也进位
      *            ⟹ 负数归零区【只有严格零】；正数 up=0 → keep=0 → 0 ⟹ 正数归零区 y' < 0.5
      *   RUP(3) !neg&&(guard||sticky)：与 RDN 符号对偶 ⟹ 正数只有严格零；负数 y' < 0.5
      *   RMM(4) guard：eTgt=-1 → up=1（guard=1，不论 sticky）→ 0.5，tie 点 0.25 也进位（ties to
      *            max magnitude）⟹ 归零区 y' < 0.25（严格）
      * 【#151 F4-b 深下溢（eTgt ≤ -2 ⟹ y' < 0.25）的定向模式修正】上面 RDN/RUP 两行说的
      *   「只有严格零」需要 F4-b 臂才成立：IEEE-754 roundTowardNegative/Positive 是「取不超过/
      *   不低于 x 的最接近可表示值」，不是幅值截断，故对任意非零 x，只要定向把幅值推离零，
      *   结果就必须是最小非零可表示值 ±0.5。原 underflow 分支把 y'<0.25 一律冲刷成 ±0，等价
      *   于在该区间偷偷退化成 RTZ ⟹ 是存量缺陷（改前 E2M1 不产出任何次正规码，±0.5 恒为死码，
      *   缺陷不可见）。F4-b 臂在 RTL(HiveOutCvt.quantElem dirAway/deepSel/deepCode) 与金锚
      *   (swQuantElem 深下溢定向臂) 两侧对称补齐后，本表 RDN/RUP 两行才成立。
      *   RNE/RTZ/RMM 幅值对称 ⟹ y'<0.25 归零本来就是正确的，F4-b 臂不介入（dirAway=false）。
      * E4M3/E5M2（emitSub=false）：underflow 由 eTgt<1 在舍入【之前】判定 ⟹ 归零区
      *   y' < minNormal(fmt) = 2^(1-bias)，【与舍入模式无关】（不存在把次正规舍入进位到
      *   最小正规的路径）。这也说明原启发式在这两格式上本来就是精确的。 */
    def analyticZeroRegionMx(refMag: Double, scaleByte: Int, rnd: Int, neg: Boolean): Boolean = {
      if (refMag == 0.0) return true
      if (p.cFmt != DataFormat.MXE2M1) return refMag < minNormal(p.cFmt) * math.pow(2.0, (scaleByte - 127).toDouble)
      val yp = refMag / math.pow(2.0, (scaleByte - 127).toDouble)
      rnd match {
        case 0 => yp <= 0.25
        case 1 => yp < 0.5
        case 2 => if (neg) false else yp < 0.5
        case 3 => if (neg) yp < 0.5 else false
        case 4 => yp < 0.25
        case _ => throw new IllegalArgumentException(s"analyticZeroRegionMx: rnd=$rnd out of 0..4")
      }
    }

    // ==========================================================================
    // 内存镜像构建
    // ==========================================================================
    def buildMemory(): ByteMemory = {
      val mem = new ByteMemory
      // 【Phase 3】A: row-major M×K。字节对齐族（aEbits>=8）走整字节 storeElem，地址
      //   表达式与改动前【逐字相同】（k*aEb == (k*aEbits)/8）⟹ 内存镜像逐字节不变；
      //   亚字节族（fp4，aEbits=4）走 nibble 打包 storeSubByte（byte=k/2、nibble=(k%2)*4）。
      // 【P0-2】基址取 p.aBase/p.bBase（默认 = A_BASE/B_BASE；非对齐 gather 用例可偏移）。
      for (mm <- 0 until p.m; k <- 0 until p.k) {
        if (aEbits >= 8) mem.storeElem(p.aBase + mm * aRowOff + k * aEb, aCode(mm)(k), aEb)
        else mem.storeSubByte(p.aBase + mm * aRowOff + (k.toLong * aEbits) / 8,
          aCode(mm)(k), ((k * aEbits) % 8).toInt, aEbits)
      }
      if (p.loadWMode) {   // B 转置：row-major N×K
        for (nn <- 0 until p.n; k <- 0 until p.k) {
          if (bEbits >= 8) mem.storeElem(p.bBase + nn * bRowOff + k * bEb, bCode(k)(nn), bEb)
          else mem.storeSubByte(p.bBase + nn * bRowOff + (k.toLong * bEbits) / 8,
            bCode(k)(nn), ((k * bEbits) % 8).toInt, bEbits)
        }
      } else {             // B 非转置：row-major K×N
        for (k <- 0 until p.k; nn <- 0 until p.n) {
          if (bEbits >= 8) mem.storeElem(p.bBase + k * bRowOff + nn * bEb, bCode(k)(nn), bEb)
          else mem.storeSubByte(p.bBase + k * bRowOff + (nn.toLong * bEbits) / 8,
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
    // ------------------------------------------------------------------------
    // (#146) Scale-channel read accessor: a 32-bit LITTLE-ENDIAN word at `addr`.
    //   Replaces the former `byteAt(addr) = loadByte(addr) & 0xFF` (1 byte).
    // ------------------------------------------------------------------------
    // WHY: the scale read channels were widened to scaleAW = scaleBW =
    //   totalNx4/minBlockSize*8 = 32 bit (#145), and HiveCoreExecutor selects a
    //   per-lane byte out of that payload with
    //     idx = (lane*elemsPerLane)/blk ;  payload >> (idx*8) & 0xFF
    //   For blk = kPerBeat (fp8 blk=32, fp4 blk=64-if-it-existed) numScalesPerBeat
    //   = kPerBeat/blk = 1, so idx is identically 0 and only payload[7:0] is ever
    //   consumed -- one byte per beat was enough. For blk < kPerBeat (fp8 blk=16
    //   -> numScalesPerBeat = 2; fp4 blk=16 -> 4; fp4 blk=32 -> 2) the higher byte
    //   slots ARE consumed, so the model must place the neighbouring scale bytes of
    //   the same tensor row into those slots.
    // WHAT THE ADDRESS MEANS: the scale DMA issues a byte address
    //     base + row*(K/blk) + kt*(kPerBeat/blk)
    //   (HiveCoreCalcConfig scaleA/BRowAddressOffset = K/blk,
    //    scaleA/BColTileAddressOffset = kLoadNum/blk) and the external memory
    //   answers with 32 bits. A 32-bit read at byte address `a` returns
    //   mem[a], mem[a+1], mem[a+2], mem[a+3] with mem[a] in bits[7:0] -- exactly
    //   `loadBits(addr, 32)`, the same little-endian convention already used by
    //   beatAtA/beatAtB. Byte slot j therefore carries scale block
    //   kt*(kPerBeat/blk) + j of that row, which is what lane group
    //   idx = j needs.
    // PROOF THAT blk=32 (fp8) BEHAVIOUR IS UNCHANGED: with aBlk=bBlk=32 and fp8,
    //   kPerBeat = totalN*2 = 32, so numScalesPerBeat = 32/32 = 1 and
    //   idx = (lane*2)/32 = 0 for every lane in 0..totalN-1. The Executor thus
    //   reads ONLY payload[7:0] = mem[addr], which is byte-identical to the old
    //   `byteAt`. The three newly populated upper bytes are dead bits that no
    //   consumer selects, so every existing blk=32 case is bit-for-bit unaffected.
    //   (Same argument for any blk >= kPerBeat, i.e. whenever idx degenerates to 0.)
    // NOTE: the upper bytes can legitimately run past the end of the scale row
    //   (and even past the end of the tensor for the last kTile of the last row),
    //   because only the first numScalesPerBeat bytes are selected. loadByte
    //   returns 0 for unwritten addresses, so this is well defined here; on real
    //   hardware it is the "32-bit read may cross the tensor end" concern that
    //   src/hive/doc/problem.md registers under the #146 alignment contract.
    def wordAt(addr: Long): BigInt = mem.loadBits(addr, 32)

    private var mem: ByteMemory = new ByteMemory
    def prepare(): ByteMemory = { mem = buildMemory(); mem }
    /** 已镜像的有效字节数（prepare 之后调用） */
    def memByteCount: Int = mem.writtenBytes

    // ==========================================================================
    // 结果解码与对比
    // ==========================================================================

    /** MX 量化输出的反量化（码 + 每 8 元素组 1 字节 E8M0 scale） */
    def dequant(code: Int, scale: Int): Double = decodeElem(p.cFmt, code.toLong, scale)

    final case class CmpSummary(elems: Int, mismatch: Int, laneViolations: Int,
                                maxRelErr: Double, maxAbsErr: Double, details: Seq[String])

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
      *
      * 【#151 F4：MX 侧判据从「幅值阈值启发式」换成「金锚独立期望码」】#129 的命中判据
      * `|ref| < minNormal(cFmt)·2^(s-127)` 是一个纯幅值条件，它隐含假设「低于最小正规格化
      * 就一定冲刷成零」。该假设对【不产出次正规】的格式成立（E4M3/E5M2 的 underflow 由
      * eTgt<1 在舍入之前判定 ⟹ 归零区 y'<minNormal 且与舍入模式无关；FP16/BF16 同理），
      * 但对 E2M1 【错 4 倍】：OCP MX 把 (e=0,m=1)=±0.5 定义为一等公民，E2M1 的真实归零区
      * 是【模式相关】的（RNE y'<=0.25 / RTZ y'<0.5 / RMM y'<0.25 / RUP-RDN 带符号只有严格零，
      * 完整推导见 analyticZeroRegionMx 与 build/151 报告 §F4）。沿用 y'<1.0 会把 (0.25S,0.5S)
      * 这段【本该是 0.5S】的元素继续当保号零做硬断言 ⟹ 修好的 RTL 反而变红；或为过而把它们
      * 排除比对 ⟹ 制造新的静默盲区。故本轮：
      *   ① MX 侧判据改为 expCodeAt（由张量语义 + OCP amax 块 scale + swQuantElem 独立算出，
      *      【不读任何硬件写回值】）——期望码本身是保号零时才走 bit-exact 断言；
      *   ② 累加全程 fp32 精确的元素（goldenCExact）升级为【期望码逐位精确比对】(Tier A)；
      *      不精确的元素退回容差比对 (Tier B)，因为要求金锚逐位复现硬件 fp40 累加树就等于
      *      以 RTL 行为为拟合目标，违反金锚铁律；
      *   ③ 双路交叉自证：期望码判零 与 模式相关解析归零区 必须逐元素一致，不一致即报
      *      ZERO-CRITERION-CONTRADICTION（我对规范的理解或对金锚的实现有内部矛盾）；
      *   ④ 旧启发式命中数仍统计但【不参与判定】，供报告给出同批次前后对照；
      *   ⑤ 打印全码（8 幅值码 × 符号）发射计数表与 hw/exp/输入侧各自的未覆盖码集。
      * 【非 MX 分支（FP32/FP16/BF16/INT）与 tolRel/tolAbs 一字未改】。 */
    def compare(cWriteLog: Seq[HiveSimCommon.CWriteBeat],
                scaleWriteLog: Seq[(Long, BigInt)]): CmpSummary = {
      // 【#166 口径升级：strb 逐字节校验】入参由 (addr,data) 二元组升为 (addr,data,strb)
      //   三元组；解析走 Checked 变体，同时跑 lane 级审计（addr↔strb 双向对账、未选通
      //   lane 零填充核查、BEAT-COUNT）。违规逐条折入 mismatch 与 details ⟹ lane 级缺陷
      //   不再静默放行（旧口径下「strb 已移位、wdata 未同源移位」能恒真假绿，就是因为
      //   TB 与 RTL 同时假设数据在总线字低位）。
      //   期望拍数取金锚 expCAddrSeq.size（张量语义侧推导），不取硬件实收拍数。
      val parsed = HiveSimCommon.parseDenseCWriteLogChecked(
        cWriteLog, p.cFmt, p.m, p.n, totalN, cDmaExtW, expBeats = Some(expCAddrSeq.size),
        expElems = Some(p.m * p.n))
      val hwBits = parsed.matrix
      println(s"[$TAG] (#166 LANE) ${parsed.laneSummary}")
      val details = mutable.ArrayBuffer.empty[String]
      // lane 级违规排在 details 最前（定位时优先看到契约级错，再看元素级数值错）
      parsed.violations.take(16).foreach { v =>
        println(s"[$TAG]   [#166 LANE] $v")
        details += s"[#166 LANE] $v"
      }
      val quant = isMxAny(p.cFmt)
      // (#170-W7) mismatch counts ELEMENT-level failures only; laneViolations is separate.
      //   Previously mismatch started at parsed.violationCount, mixing contract message
      //   count (unitless) with element mismatch count (elements) -- the ratio 951/1024
      //   was semantically invalid and could exceed 1.
      val laneViolations = parsed.violationCount
      var mismatch = 0
      var maxRel = 0.0
      var maxAbs = 0.0
      var ftzTakenOver = 0   // #129→#151：被「期望码=保号零」bit-exact 分支接管的元素计数
      var tierA = 0          // (#151 F4) 期望码逐位精确比对（累加全程 fp32 精确）
      var tierB = 0          // (#151 F4) 容差比对（累加不精确 ⟹ 金锚与硬件之差是合法舍入差）
      var tierBZero = 0      // (#151 F4) Tier B 中走保号零 bit-exact 的元素数
      var legacyHeurHits = 0 // (#151 F4) 旧幅值阈值启发式命中数：仅供前后对照，【不参与任何判定】
      var crossCheckBad = 0  // (#151 F4) 期望码判零 vs 模式相关解析判零 的不一致数（内部矛盾探针）
      // (#151 F4) 两套【对照口径】的误差统计。主口径 maxRel/maxAbs 覆盖全部元素（含归零元素，
      //   故 E2M1 会取到 1.0 —— 期望码为 ±0 而 ref≠0 时 relErr 恒为 1.0，这是更诚实的统计，
      //   不是回归）。另给：
      //   ① legacy 口径 = 完全复刻 #129/#150 的排除集（|ref| < minNormal·2^(hwScale-127) 的元素
      //      不进误差统计）⟹ 该数字与 #150 报告可直接对照，用于证明「改动只发生在 y'<1.0 区」；
      //   ② nz 口径 = 只统计期望码非零的元素 ⟹ 反映可编码值域内的真实最坏量化误差。
      var maxRelLegacy = 0.0
      var maxAbsLegacy = 0.0
      var maxRelNZ = 0.0
      var maxAbsNZ = 0.0
      // (#151 F4) E2M1 边界覆盖计数器：把每个元素的 y' = |ref| / 2^(expScale-127) 归入 OCP
      //   网格的关键点/关键带，用来【如实界定】定向边界值是否真被随机激励打到。
      //   y' 用【精确】== 比较：ref 是 0.25 的整数倍（fp4 乘积和，fp64 下精确），除以 2 的
      //   整数幂在 IEEE-754 下无舍入 ⟹ yp 是精确值，不需要容差。
      var bndZero = 0; var bndOp0_025 = 0; var bndP025 = 0; var bndOp025_05 = 0; var bndP050 = 0
      var bndOp05_075 = 0; var bndP075 = 0; var bndOp075_1 = 0
      var bndOp1_125 = 0; var bndP125 = 0; var bndOther = 0; var bndP100 = 0
      // (#151 F4) 点级取证容器：key = 网格关键点/开区间标签，value = 去重后的
      //   (符号, y', hw 码, 金锚期望码) 记录集合。仅 MXE2M1 填充。
      val bndRec = mutable.LinkedHashMap.empty[String, mutable.LinkedHashSet[String]]
      val tolRel = if (quant) 0.5 else if (p.cFmt == DataFormat.BF16) 2e-2 else 1e-2
      val tolAbs = if (quant) 1e-3 else 1e-6
      // 非 MX 浮点格式的 FTZ 阈值（真实值域，与 ref 同域）；INT 格式无 subnormal，不触发。
      // (#151 F4)【本行与其服务的非 MX 分支保持一字未改】：FP16/BF16 走 fp32ToFp16Rnd /
      //   fp32ToBf16Rnd 独立通路，对 |x| < minNormal 一律 FTZ 保号零、【不做】「舍入到次正规」，
      //   故其归零区就是 y' < minNormal 且与舍入模式无关 —— 与 E2M1 的次正规网格本质不同。
      val ftzThreshFlat: Double = if (isIntFmt(p.cFmt)) 0.0 else minNormal(p.cFmt)
      // 元素码位掩码（位宽由 cOutElemBits 推导，非硬编码；FTZ bit-exact 断言取 hw 原始位用）
      val elemMask: Long = (1L << HiveSimCommon.cOutElemBits(p.cFmt)) - 1
      // (#151 F4) 全码发射计数所需的位宽/幅值域掩码
      val elemBits: Int = HiveSimCommon.cOutElemBits(p.cFmt)
      val magMask: Long = (1L << (elemBits - 1)) - 1
      // (#73) elemBits>=32 (FP32/INT32 output) guard: Scala Int shift is mod-32, so
      //   `1 << 32` wraps to `1 << 0` = 1, and a real 2^32-entry histogram is memory-infeasible
      //   anyway. The histograms are only READ inside the `if (quant)` reporting block below
      //   (MX formats => elemBits in {4,8}), so skipping the tally for wide formats leaves the
      //   per-element compare verdicts untouched and removes the ArrayIndexOutOfBoundsException.
      val histEnabled: Boolean = elemBits < 32
      val nCodes: Int = if (histEnabled) 1 << elemBits else 0
      val hwHist  = Array.fill(nCodes)(0)   // 硬件实发码计数
      val expHist = Array.fill(nCodes)(0)   // 金锚期望码计数（仅 MX 有意义）
      for (mm <- 0 until p.m; nn <- 0 until p.n) {
        val ref = goldenC(mm)(nn)
        // MX 量化输出：本元素所属块的 E8M0 scale 字节（【仅】用于反量化与旧启发式对照；
        //   (#151 F4) 判定不再依赖它 —— 判据换成金锚独立算出的 expScale/expCode）
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
        // (#151 F4) 误差统计在【所有】分支累积（主口径），另按 legacy / nz 两套对照口径分别累积
        val hwCode: Long = hwBits(mm)(nn) & elemMask
        if (histEnabled) hwHist(hwCode.toInt) += 1   // (#73) skip tally for elemBits>=32
        val absErr = math.abs(hw - ref)
        val relErr = if (math.abs(ref) > 1e-9) absErr / math.abs(ref) else absErr
        if (relErr > maxRel) maxRel = relErr
        if (absErr > maxAbs) maxAbs = absErr
        // legacy 命中集 = 改前被排除在误差统计之外的元素集合，表达式与 #129/#150 【逐字相同】：
        //   MX：|ref| < minNormal(cFmt)·2^(hwScale-127)；非 MX：|ref| < minNormal(cFmt)。
        val legacyHit: Boolean =
          if (quant) ref != 0.0 && math.abs(ref) < minNormal(p.cFmt) * math.pow(2.0, (mxScale - 127).toDouble)
          else !isIntFmt(p.cFmt) && ref != 0.0 && math.abs(ref) < ftzThreshFlat
        if (legacyHit) legacyHeurHits += 1
        else {
          if (relErr > maxRelLegacy) maxRelLegacy = relErr
          if (absErr > maxAbsLegacy) maxAbsLegacy = absErr
        }

        if (quant) {
          // ==================================================================
          // (#151 F4) MX 输出：判据 = 金锚独立算出的【期望码】，不再是幅值阈值
          // ==================================================================
          val eCode  = expCodeAt(mm, nn)
          val eScale = expScaleAt(mm, nn)
          expHist(eCode) += 1
          val expIsZero = (eCode.toLong & magMask) == 0L
          val expNeg    = ((eCode >>> (elemBits - 1)) & 1) == 1
          if (!expIsZero) {
            if (relErr > maxRelNZ) maxRelNZ = relErr
            if (absErr > maxAbsNZ) maxAbsNZ = absErr
          }
          // (#151 F4) E2M1 定向边界覆盖分类（y' = |ref|/2^(expScale-127)，精确比较）
          if (p.cFmt == DataFormat.MXE2M1) {
            val yp = math.abs(ref) / math.pow(2.0, (eScale - 127).toDouble)
            if (yp == 0.0) bndZero += 1
            else if (yp < 0.25) bndOp0_025 += 1
            else if (yp == 0.25) bndP025 += 1
            else if (yp > 0.25 && yp < 0.5) bndOp025_05 += 1
            else if (yp == 0.5) bndP050 += 1
            else if (yp > 0.5 && yp < 0.75) bndOp05_075 += 1
            else if (yp == 0.75) bndP075 += 1
            else if (yp > 0.75 && yp < 1.0) bndOp075_1 += 1
            else if (yp == 1.0) bndP100 += 1
            else if (yp > 1.0 && yp < 1.25) bndOp1_125 += 1
            else if (yp == 1.25) bndP125 += 1
            else bndOther += 1
            // (#151 F4) 点级取证：把每个【网格关键点】与每个【开区间】上实测到的
            //   (y', 符号, hw 码, 金锚期望码) 三元组收集起来打印 —— 这就是「RTL 新臂 vs
            //   金锚」定向边界值验证表的原始数据，逐舍入模式各跑一次即得整表。
            val sgn = if (ref < 0.0 || (ref == 0.0 && expNeg)) "-" else "+"
            val rec = f"$sgn yp=$yp%.6g hw=0x${hwCode.toInt}%X exp=0x$eCode%X"
            val keyOpt: Option[String] =
              if (yp == 0.0) Some("y'=0")
              else if (yp < 0.25) Some("band(0,0.25)")
              else if (yp == 0.25) Some("y'=0.25")
              else if (yp > 0.25 && yp < 0.5) Some("band(0.25,0.5)")
              else if (yp == 0.5) Some("y'=0.5")
              else if (yp > 0.5 && yp < 0.75) Some("band(0.5,0.75)")
              else if (yp == 0.75) Some("y'=0.75")
              else if (yp > 0.75 && yp < 1.0) Some("band(0.75,1.0)")
              else if (yp == 1.0) Some("y'=1.0")
              else if (yp > 1.0 && yp < 1.25) Some("band(1.0,1.25)")
              else if (yp == 1.25) Some("y'=1.25")
              else if (yp == 1.75) Some("y'=1.75")
              else if (yp == 2.5) Some("y'=2.5")
              else if (yp == 3.5) Some("y'=3.5")
              else if (yp == 5.0) Some("y'=5.0")
              else if (yp == 6.0) Some("y'=6.0")
              else None
            keyOpt.foreach { k =>
              val st = bndRec.getOrElseUpdate(k, mutable.LinkedHashSet.empty[String])
              // 开区间上 y' 取值很多，只保留 (符号,hw,exp) 的去重集合（丢弃具体 y'）以免刷屏；
              //   关键点上 y' 唯一，保留完整记录。
              st += (if (k.startsWith("band")) rec.replaceFirst("yp=[^ ]+ ", "") else rec)
            }
          }
          // 双路交叉自证：①期望码幅值域是否为 0 ②OCP 网格 + 五模式解析归零区。
          //   两条独立路径必须逐元素一致；不一致 = 我对规范的理解或对金锚的实现有内部矛盾。
          // (#155 Ryan N1) 第三路径的符号实参从 expNeg（金锚期望码的符号位）改为 ref < 0.0
          //   （fp64 参考值的符号）。理由：解析归零区本意是【不依赖金锚实现】的独立判据，
          //   喂 expNeg 等于让第三路径在符号维度上与第二路径共享同一个来源，一旦金锚把符号
          //   算错，两条路径会同错同绿。改喂 ref 的符号后，符号维度回到张量语义（真值）侧，
          //   独立性提高。数值等价性：定向模式下 RDN 恒产 -0、RUP 恒产 +0 时 expNeg 与
          //   ref<0.0 会不同，但那正是本探针要抓的情形（交叉自证不一致即失败）。
          //   注：下一行 selfZero 仍用 expNeg —— 它比的是「金锚期望码 == 金锚保号零码」这条
          //   金锚内部自洽性，属第二路径的自检，不在本条裁定的射程内，逐字不动。
          val anaZero = analyticZeroRegionMx(math.abs(ref), eScale, p.rnd, ref < 0.0)
          val selfZero = !expIsZero || eCode.toLong == ftzSignedZeroCode(p.cFmt, expNeg)
          if (anaZero != expIsZero || !selfZero) {
            crossCheckBad += 1
            // (#151 F4) 矛盾【即失败】——给这条自证探针装上牙齿。理由：两条独立路径不一致，
            //   要么是我对 OCP/IEEE 归零语义的理解错了，要么是金锚实现错了，两者都必须红；
            //   只打印不失败等于探针是装饰品。实测这条牙齿真的咬到了东西：加 F4-b 定向深
            //   下溢臂【之前】的 RDN 跑，本计数器 = 54（全是 y'<0.25 的负元素，RTL 与金锚
            //   同时把非零负深下溢冲刷成 -0，而 IEEE roundTowardNegative 要求 -0.5）——
            //   注意那时 hw 直方图与 exp 直方图【逐码相同】，纯 hw-vs-golden 比对完全看不出来，
            //   正是这条独立解析路径把共模错误暴露出来的。
            //   计数口径说明：同一元素若同时触发本条与下面的 CODE-BITEXACT-FAIL，mismatch 会
            //   +2（>元素总数）。这只可能出现在【已经红】的跑里，不影响绿/红判定，如实记录。
            mismatch += 1
            if (details.size < 32)
              details += f"C($mm,$nn) ZERO-CRITERION-CONTRADICTION expCode=0x$eCode%X " +
                f"isZero=$expIsZero analytic=$anaZero selfConsistent=$selfZero " +
                f"ref=$ref%.6g expScale=$eScale hwScale=$mxScale rnd=${p.rnd}"
          }
          if (goldenCExact(mm)(nn)) {
            // ---- Tier A：累加全程 fp32 精确 ⟹ 期望码可做【逐位】断言 ----
            tierA += 1
            if (expIsZero) ftzTakenOver += 1
            if (hwCode.toInt != eCode) {
              mismatch += 1
              if (details.size < 32)
                details += f"C($mm,$nn) CODE-BITEXACT-FAIL hw=0x$hwCode%X exp=0x$eCode%X " +
                  f"ref=$ref%.6g hwVal=$hw%.6g expScale=$eScale hwScale=$mxScale " +
                  f"absErr=$absErr%.3e relErr=$relErr%.3e"
            }
          } else {
            // ---- Tier B：累加不精确 ⟹ 逐位断言等于要求金锚模拟 RTL 累加树（违反金锚铁律），
            //      退回容差；但【归零判定】仍由期望码给出，不再用幅值阈值。 ----
            tierB += 1
            if (expIsZero) {
              tierBZero += 1
              ftzTakenOver += 1
              val expZeroCode = ftzSignedZeroCode(p.cFmt, expNeg)
              if (hwCode != expZeroCode) {
                mismatch += 1
                if (details.size < 32)
                  details += f"C($mm,$nn) FTZ-bitexact-FAIL hw=0x$hwCode%X exp=0x$expZeroCode%X " +
                    f"(signed-zero) ref=$ref%.6g expScale=$eScale hwScale=$mxScale rnd=${p.rnd}"
              }
            } else {
              // quant ⟹ !isIntFmt(p.cFmt)，故改前 `if (isIntFmt) absErr>0.5 else …` 恒取 else 臂
              val fail = relErr > tolRel && absErr > tolAbs
              if (fail) {
                mismatch += 1
                if (details.size < 32)
                  details += f"C($mm,$nn) hw=0x${hwBits(mm)(nn)}%X($hw%.6g) ref=$ref%.6g absErr=$absErr%.3e relErr=$relErr%.3e"
              }
            }
          }
        } else {
          // ==================================================================
          // 非 MX 输出（FP32/FP16/BF16/INT）：以下判定与改前【逐字相同】
          // ==================================================================
          // (#151 F4) 与上方 legacyHit 同式（非 MX 分支的判据【逐字未变】，只是提到循环体
          //   前部以便 legacy 对照口径共用同一个命中集）。
          val ftzHit = legacyHit
          if (ftzHit) {
            ftzTakenOver += 1
            val expCode = ftzSignedZeroCode(p.cFmt, ref < 0.0)
            if (hwCode != expCode) {
              mismatch += 1
              if (details.size < 32)
                // (#155 Nit n2) "(保号零)" ASCII 化为 "(signed-zero)"，与上方 MX 分支同一条
                //   失败打印的措辞统一（该分支早已用 "(signed-zero)"）。理由：firtool 对
                //   非 ASCII 断言/失败消息会发射失败，全仓失败消息一律 ASCII 是硬约束。
                details += f"C($mm,$nn) FTZ-bitexact-FAIL hw=0x${hwCode}%X exp=0x${expCode}%X(signed-zero) ref=$ref%.6g ftzThresh=$ftzThreshFlat%.3e"
            }
          } else {
            val fail = if (isIntFmt(p.cFmt)) absErr > 0.5 else (relErr > tolRel && absErr > tolAbs)
            if (fail) {
              mismatch += 1
              if (details.size < 32)
                details += f"C($mm,$nn) hw=0x${hwBits(mm)(nn)}%X($hw%.6g) ref=$ref%.6g absErr=$absErr%.3e relErr=$relErr%.3e"
            }
          }
        }
      }
      // ======================================================================
      // (#151 F3) C 侧块 scale 字节【值】硬断言 —— 逐块比对，任何一块不符即红
      // ======================================================================
      // 改前状态：compare() 只【读】硬件写回的 scale 字节（上方 mxScale）用来反量化与做旧
      //   启发式对照，从未把它的【值】与任何独立期望值比过 ⟹ scale 通路只要「自洽」（硬件
      //   写的 scale 与硬件写的码互相匹配）就能过，哪怕整条 amax→E8M0 编码链算错了也看不
      //   出来。元素码那一侧本轮（F4）已换成期望码逐位比对，但期望码是用【金锚独立算出的】
      //   expScale 量化的 ⟹ scale 值本身仍是盲区，本节补上。
      // 【期望值来源：严格不读硬件】
      //   张量语义 aVal/bVal（decodeInElem + effScaleA/B）→ fp64 顺序累加 goldenC → fp32
      //     → swFpNormalize40to32（与硬件两级舍入链同链）→ swQuantBlock：按声明块大小分组
      //     求 amax（padding 列掩 0 不进 amax）→ swBlockScale：OCP E8M0 规则
      //     s = eEff + bias - maxCodeExp，钳到 [1,254]，amax==0 → 哨兵 0，amax==inf → 254。
      //   全程没有任何一步读 cWriteLog / scaleWriteLog。
      // 【beat 内字节数与「字节 ↔ 块」映射：由声明的 C-scale 内存布局契约独立确定】
      //   本文件上方的 scale 地址序列 = SC_BASE + nt*colOff + mm*rowOff，其中
      //     colOff = totalN / cMxBlk   （每拍前进的字节数 = 本 tile 内的块数）
      //     rowOff = ceil(n / cMxBlk)  （每行字节数 = 全行的块数）
      //   该地址序列被 MxSpec 逐拍硬对账（DMA-CSCALE addr）、且属既有禁动区 ⟹ 一个 beat
      //   承载的 scale 字节数就是 colOff = totalN/cMxBlk，第 j 字节 ↔ 本 tile 内第 j 个
      //   cMxBlk 元素块。这与 swQuantBlock 的「每 8 元素 1 字节」中间表示【粒度不同】：
      //   后者是金锚内部粒度，块 j 对应它的第 j*(cMxBlk/8) 个字节。二者必须一致，故下面
      //   额外断言「同一块内所有 8 元素组的金锚 scale 字节相等」（否则该映射不成立）。
      //   注（如实留痕，不判定）：HiveOutCvt.scaleWire 按【每 8 元素 1 字节】生成 totalN/8
      //   字节，而 io.scaleOut 位宽 = HiveCoreConfig.cScaleRowW = (totalN/minBlockSize)*8
      //   = totalN/16 字节（minBlockSize=16）⟹ cMxBlk=16 时两者相等、无截断；cMxBlk=32
      //   时物理 beat 宽度(totalN/16) 是契约前进量(totalN/32) 的 2 倍，多出的高字节会落进
      //   下一 tile 的地址窗 —— 属潜在契约分歧，但当前 cfg 族 totalN 恒为 16 ⟹ cMxBlk=32
      //   被上方 require(cMxBlk <= totalN) 拦掉、不可达。两个宽度都打印出来供人工核对。
      var scaleBlkChecked = 0     // 比对过的 scale 块（= beat 内字节）总数 = nTiles*m*(totalN/cMxBlk)
      var scaleBad = 0            // 值不符的块数（每个都 mismatch += 1 ⟹ 红）
      var scaleBeatCountBad = 0   // scale beat 数与期望拍数不符（结构性失败）
      var scaleBlkInconsist = 0   // 同一块内金锚各 8 元素组 scale 字节不等（映射前提被破坏）
      val scaleExpHist = Array.fill(256)(0)
      val scaleHwHist  = Array.fill(256)(0)
      var scaleExpBeats = 0
      var scaleBytesPerBeat = 0       // 契约：colOff = totalN/cMxBlk
      var scalePhysBytesPerBeat = 0   // 物理：cScaleRowW/8 = totalN/minBlockSize(=16)
      if (quant) {
        scaleBytesPerBeat     = totalN / p.cMxBlk
        scalePhysBytesPerBeat = totalN / 16
        scaleExpBeats = nTiles * p.m
        if (scaleWriteLog.size != scaleExpBeats) {
          scaleBeatCountBad += 1
          mismatch += 1
          details += f"SCALE-BEAT-COUNT-FAIL exp=$scaleExpBeats got=${scaleWriteLog.size} " +
            f"(nTiles=$nTiles m=${p.m})"
        }
        val per8 = p.cMxBlk / 8                       // 一个块内的 8 元素组数
        for (nt <- 0 until nTiles; mm <- 0 until p.m) {
          val idx = nt * p.m + mm
          val (sAddr, sBeat) =
            if (idx < scaleWriteLog.size) scaleWriteLog(idx) else (-1L, BigInt(0))
          val expScales = expQuant(idx)._2            // 长度 = totalN/8（金锚 8 元素组粒度）
          for (j <- 0 until scaleBytesPerBeat) {
            val base8 = j * per8
            val exp = expScales(base8)
            // 映射前提自检：同一块内 per8 个金锚字节必须全等（块 amax 的定义要求）
            for (t <- 1 until per8) if (expScales(base8 + t) != exp) scaleBlkInconsist += 1
            val got = ((sBeat >> (8 * j)) & 0xFF).toInt
            scaleBlkChecked += 1
            scaleExpHist(exp) += 1
            scaleHwHist(got) += 1
            if (got != exp) {
              scaleBad += 1
              mismatch += 1
              // ASCII only；精确到 n-tile / 行 / 块索引 / 块首元素全局列 / beat 地址 / 期望与实收字节值
              if (details.size < 64)
                details += f"SCALE-BYTE-FAIL tile=$nt row=$mm blk=$j " +
                  f"elem0=${nt * totalN + j * p.cMxBlk} beatAddr=0x$sAddr%X " +
                  f"exp=0x$exp%02X got=0x$got%02X (blkElems=${p.cMxBlk} blkSel=${p.cMxBlkSel} " +
                  f"totalN=$totalN bytesPerBeat=$scaleBytesPerBeat)"
            }
          }
        }
        if (scaleBlkInconsist > 0) {
          mismatch += 1
          details += f"SCALE-BLK-INTERNAL-INCONSISTENT count=$scaleBlkInconsist " +
            f"(golden per-8 scale bytes disagree inside one blk=${(p.cMxBlk)}; the " +
            f"byte-to-block mapping used by this assertion is ill-defined)"
        }
      }
      // #129 屏蔽面可观测性（#151 F4：MX 侧判据已换成期望码，计数含义随之精确化）。
      println(s"[$TAG] FTZ-bitexact 接管计数: cFmt=${p.cFmt} 命中=$ftzTakenOver / 总元素=${p.m * p.n}" +
        s"（ftzThreshFlat=${"%.3e".format(ftzThreshFlat)}${if (quant) "；MX 侧按块 scale ×2^(s-127)" else ""}）")
      if (quant) {
        // (#151 F4) 判定分层与双路交叉自证结果
        println(s"[$TAG] (#151 F4) 判定分层: cFmt=${p.cFmt} rnd=${p.rnd} cMxBlk=${p.cMxBlk} " +
          s"TierA(期望码逐位)=${tierA} TierB(容差)=${tierB}（其中保号零 bit-exact=${tierBZero}） " +
          s"归零判据命中=${ftzTakenOver} 旧幅值阈值启发式命中=${legacyHeurHits}（仅对照，不判定） " +
          s"双路交叉自证不一致=${crossCheckBad}")
        // (#151 F3) C 侧 scale 字节【值】硬断言汇总。期望值来自 swQuantBlock/_2（OCP amax
        //   规则独立算出，全程不读 cWriteLog/scaleWriteLog）；实收值来自 scaleWriteLog 的
        //   beat 位切片。任何一字节不符都已 mismatch += 1 ⟹ 用例变红。
        println(s"[$TAG] (#151 F3) C-scale 硬断言: beat 期望=$scaleExpBeats 实收=${scaleWriteLog.size}" +
          s"（结构不符=$scaleBeatCountBad） beat 内字节 契约=$scaleBytesPerBeat 物理=$scalePhysBytesPerBeat" +
          s" 逐块比对=$scaleBlkChecked 值不符=$scaleBad 块内金锚不一致=$scaleBlkInconsist")
        // scale 字节直方图：只列非零项（256 桶太宽）。exp 与 hw 两侧必须逐桶相同，
        //   这也是「scale 通路不只是自洽、而且与规范一致」的可观测证据。
        def sHist(name: String, h: Array[Int]): String = {
          val nz = h.zipWithIndex.filter(_._1 > 0)
          s"$name: nonzero=${nz.size}/256 " + (if (nz.isEmpty) "(none)"
            else nz.map { case (v, c) => f"0x$c%02X=$v" }.mkString(" "))
        }
        println(s"[$TAG]   ${sHist("exp scaleBytes", scaleExpHist)}")
        println(s"[$TAG]   ${sHist("hw  scaleBytes", scaleHwHist)}")
        val sUncExp = (0 until 256).filter(c => scaleExpHist(c) == 0)
        val sUncHw  = (0 until 256).filter(c => scaleHwHist(c) == 0)
        println(s"[$TAG]   exp uncovered=${sUncExp.size} bytes  hw uncovered=${sUncHw.size} bytes" +
          s"（exp 侧未覆盖即本轮激励未打到的 E8M0 scale 码，与 §8 F5 同类，如实呈现）")
        // (#151 F4) 三套误差口径。主口径含归零元素（E2M1 期望码=±0 而 ref≠0 ⟹ relErr=1.0，
        //   这是统计口径变诚实，不是回归）；legacy 口径复刻 #129/#150 的排除集，用于证明
        //   「本轮改动只发生在 y'<minNormal 区」；nz 口径只统计期望码非零的元素。
        println(f"[$TAG] (#151 F4) 误差口径: 主(全元素) maxRel=$maxRel%.3e maxAbs=$maxAbs%.3e | " +
          f"legacy(#129 排除集, 命中=$legacyHeurHits) maxRel=$maxRelLegacy%.3e maxAbs=$maxAbsLegacy%.3e | " +
          f"nz(期望码非零) maxRel=$maxRelNZ%.3e maxAbs=$maxAbsNZ%.3e")
        // (#151 F4) E2M1 定向边界覆盖（y' = |ref|/2^(expScale-127) 的关键点/关键带命中数）。
        //   计数为 0 的点即【未被本次随机激励打到】，报告须如实写明，不得含糊成「已覆盖」。
        if (p.cFmt == DataFormat.MXE2M1)
          println(s"[$TAG] (#151 F4) E2M1 边界覆盖: y'=0:$bndZero  (0,0.25):$bndOp0_025  =0.25:$bndP025  (0.25,0.5):$bndOp025_05" +
            s"  =0.5:$bndP050  (0.5,0.75):$bndOp05_075  =0.75:$bndP075  (0.75,1.0):$bndOp075_1" +
            s"  =1.0:$bndP100  (1.0,1.25):$bndOp1_125  =1.25:$bndP125  其它:$bndOther")
        // (#151 F4) 定向边界值验证表原始数据（RTL 新臂 vs 金锚期望码），按网格升序打印。
        //   每个舍入模式各跑一次即得该模式整列；hw 与 exp 不一致时上面已计 mismatch。
        //   注：order 里的 "y'=0.3" 是【故意列进去让它落到「未被激励」】的——E2M1 激励下
        //   ref = Σ a·b 是 0.25 的整数倍、块 scale 是 2 的整数幂 ⟹ y' 必为二进有理数
        //   （dyadic），而 0.3 = 3/10 非 dyadic ⟹ 精确 y'=0.3 在 fp4 激励下【不可达】。
        //   开区间 (0.25,0.5) 由 band(0.25,0.5) 的实际命中数覆盖，如实呈现而非伪造。
        if (p.cFmt == DataFormat.MXE2M1) {
          val order = Seq("y'=0", "band(0,0.25)", "y'=0.25", "y'=0.3", "band(0.25,0.5)",
            "y'=0.5", "band(0.5,0.75)",
            "y'=0.75", "band(0.75,1.0)", "y'=1.0", "band(1.0,1.25)", "y'=1.25",
            "y'=1.75", "y'=2.5", "y'=3.5", "y'=5.0", "y'=6.0")
          println(s"[$TAG] (#151 F4) 定向边界值取证 rnd=${p.rnd}（hw=RTL 新臂实测，exp=金锚独立期望）")
          order.foreach { k =>
            bndRec.get(k).foreach { st =>
              println(s"[$TAG]   $k -> ${st.toSeq.sorted.mkString(" | ")}")
            }
          }
          val notHit = order.filterNot(bndRec.contains)
          if (notHit.nonEmpty) println(s"[$TAG]   未被激励: ${notHit.mkString(" ")}")
        }
        // (#151 F4) 全码发射计数表：E2M1 elemBits=4 ⟹ 16 码全列；E4M3/E5M2 elemBits=8 ⟹ 只列非零项
        def histLine(name: String, h: Array[Int]): String =
          if (nCodes <= 16) s"$name: " + (0 until nCodes).map(c => f"0x$c%X=${h(c)}").mkString(" ")
          else {
            val nz = h.zipWithIndex.filter(_._1 > 0)
            s"$name: nonzero=${nz.size}/$nCodes " + nz.map { case (v, c) => f"0x$c%02X=$v" }.mkString(" ")
          }
        def uncoveredLine(name: String, h: Array[Int]): String = {
          val z = (0 until nCodes).filter(c => h(c) == 0)
          val body =
            if (nCodes <= 16) if (z.isEmpty) "(none)" else z.map(c => f"0x$c%X").mkString(" ")
            else if (z.isEmpty) "(none)"
            else s"${z.size} codes (first: ${z.take(8).map(c => f"0x$c%02X").mkString(" ")})"
          s"$name uncovered: $body"
        }
        println(s"[$TAG] (#151 F4) 输出侧码覆盖率 elemBits=$elemBits")
        println(s"[$TAG]   ${histLine("hw  emitted", hwHist)}")
        println(s"[$TAG]   ${histLine("exp golden ", expHist)}")
        println(s"[$TAG]   ${uncoveredLine("hw ", hwHist)}")
        println(s"[$TAG]   ${uncoveredLine("exp", expHist)}")
        // (#151 F4) 输入侧码覆盖率：本轮改的是【输出域】次正规；输入域 E2M1 的 ±0.5 本来就由
        //   decodeMxE2M1Input（subnormalFtz=false）支持。列出实测覆盖以界定未覆盖码集。
        def inHist(eb: Int, rows: Int, cols: Int, get: (Int, Int) => Long): Array[Int] = {
          val h = Array.fill(1 << eb)(0)
          val msk = (1 << eb) - 1
          for (i <- 0 until rows; j <- 0 until cols) h((get(i, j).toInt) & msk) += 1
          h
        }
        def inHistLine(name: String, eb: Int, h: Array[Int]): String = {
          val n = 1 << eb
          val body =
            if (n <= 16) (0 until n).map(c => f"0x$c%X=${h(c)}").mkString(" ")
            else h.zipWithIndex.filter(_._1 > 0).map { case (v, c) => f"0x$c%02X=$v" }.mkString(" ")
          val z = (0 until n).filter(c => h(c) == 0)
          val unc = if (n <= 16) z.map(c => f"0x$c%X").mkString(" ") else s"${z.size} codes"
          s"$name elemBits=$eb $body | uncovered: ${if (unc.isEmpty) "(none)" else unc}"
        }
        if (isMxAny(p.aFmt)) {
          val ha = inHist(aEbits, p.m, p.k, (i, j) => aCode(i)(j))
          println(s"[$TAG]   ${inHistLine("in A", aEbits, ha)}")
        }
        if (isMxAny(p.bFmt)) {
          val hb = inHist(bEbits, p.k, p.n, (i, j) => bCode(i)(j))
          println(s"[$TAG]   ${inHistLine("in B", bEbits, hb)}")
        }
      }
      CmpSummary(p.m * p.n, mismatch, laneViolations, maxRel, maxAbs, details.toSeq)
    }

    /** 打印 hw/ref 前若干行，便于人工定位（【#166】入参同步升为三元组口径） */
    def printRows(cWriteLog: Seq[HiveSimCommon.CWriteBeat], rows: Int): Unit = {
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
