/** WbDebugModel — WorkerBeeDebugCase 的纯软件参考模型（不含任何 poke/peek）。
  *
  * 职责：参数解析 / 几何推演 / 字节内存镜像 / 软件 golden / 期望地址序列 /
  * 结果解码对比；仿真驱动与断言在 WorkerBeeDebugCase.scala。
  *
  * 外部存储建模为「字节地址 → 8-bit」镜像，DMA 请求拍按 req.addr 取 aExtW/bExtW 位
  * 返回，不按 beat 序号顺序发数（按序发数会掩盖供数序/地址公式错位）。期望地址序列
  * 由张量语义布局闭式推导，不镜像 WorkerBeeCalcConfig 的谓词链；两者不一致即为硬件
  * 缺陷（默认非致命收集，`-DstrictAddr=1` 转逐拍致命断言）。
  *
  * 内存布局约定：
  *   A       : row-major M×K，元素 memElemBytes(aFmt) 字节，基址 A_BASE
  *   B       : loadWMode=0 → row-major K×N；loadWMode=1 → row-major N×K（转置）
  *   scaleA  : row-major M×(K/aBlk) 字节（aBlk = 16<<aMxBlkSel：sel=0→16、sel=1→32）
  *   scaleB  : row-major N×(K/bBlk) 字节，byte = n*(K/bBlk) + kb（硬件
  *             scaleBRowAddressOffset = K/bBlk 为 n 向步进）
  *   C       : 密集布局，addr(C[i][j]) = base + j×cEb + i×(N×cEb)，块间跳转
  *             = nt×cColOff，复用 WbSimCommon.cAddrSeqDense。
  */

import chisel3._
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable

object WbDebugModel {

  // ========== 外部存储分区基址（互不重叠） ==========
  val A_BASE:  Long = 0x00000000L
  val B_BASE:  Long = 0x00100000L
  val C_BASE:  Long = 0x00200000L
  val SA_BASE: Long = 0x00300000L   // scaleA
  val SB_BASE: Long = 0x00400000L   // scaleB
  val SC_BASE: Long = 0x00500000L   // scaleC（量化输出写回）
  val BIAS_BASE: Long = 0x00600000L // bias 向量（nTiles*totalN 个 4B 字）
  val RQM_BASE: Long  = 0x00700000L // requant M 向量（nTiles*totalN 个 4B 字）

  val TAG = "DEBUG"

  // 格式谓词 / 几何

  def isMx8(f: DataFormat.Type): Boolean = f == DataFormat.MXE4M3 || f == DataFormat.MXE5M2
  def isMxAny(f: DataFormat.Type): Boolean = isMx8(f) || f == DataFormat.MXE2M1
  /** 非 MX 纯 FP8：元素编码与 MX8 同为 E4M3/E5M2，但无块 scale 字节 ⟹ isMxAny/isMx8
    * 均不含本族，scale 数组、expScale 地址序列、effScale（恒 127）自动走「无 scale」支路。 */
  def isFp8Fmt(f: DataFormat.Type): Boolean = f == DataFormat.FP8E4M3 || f == DataFormat.FP8E5M2
  def isIntFmt(f: DataFormat.Type): Boolean =
    f == DataFormat.INT32 || f == DataFormat.INT16 || f == DataFormat.INT8 || f == DataFormat.INT4
  def isFloatFmt(f: DataFormat.Type): Boolean =
    f == DataFormat.FP32 || f == DataFormat.FP16 || f == DataFormat.BF16

  /** 外部内存中每元素字节数（半字节族返回 0）。几何/内存镜像一律用 memElemBits 的
    * bits 口径；本函数供整字节 storeElem 与语义查询。 */
  def memElemBytes(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32 || f == DataFormat.INT32) 4
    else if (f == DataFormat.FP16 || f == DataFormat.BF16 || f == DataFormat.INT16) 2
    else if (f == DataFormat.INT8 || isMx8(f) || isFp8Fmt(f)) 1
    else 0   // MXE2M1 / INT4：半字节，bytes 口径为 0（bits 口径见 memElemBits=4）

  /** 每元素位宽（bits 口径几何的单一真源）。地址偏移一律用 X×memElemBits/8：字节对齐
    * 族与 X×memElemBytes 逐值相等，fp4 则得到正确的半字节地址（k×4/8=k/2）。 */
  def memElemBits(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32 || f == DataFormat.INT32) 32
    else if (f == DataFormat.FP16 || f == DataFormat.BF16 || f == DataFormat.INT16) 16
    else if (f == DataFormat.INT8 || isMx8(f) || isFp8Fmt(f)) 8
    else 4   // MXE2M1 / INT4：半字节

  /** 每 16-bit lane 打包的元素数：半字节族（INT4/MXE2M1）quad-lane=4、单字节族
    * （INT8/MX8/FP8）dual-lane=2、其余=1。 */
  def elemsPerLane(f: DataFormat.Type): Int =
    if (f == DataFormat.MXE2M1 || f == DataFormat.INT4) 4
    else if (isMx8(f) || f == DataFormat.INT8 || isFp8Fmt(f)) 2
    else 1

  /** 每拍推进的 K 元素数（= kTile 粒度）：INT8/MX8/FP8 = totalN*2、INT4/MXE2M1 = totalN*4、其余 = totalN */
  def kPerBeat(f: DataFormat.Type, totalN: Int): Int = totalN * elemsPerLane(f)

  /** FpAddNarrow 精度契约①常数 (n_lossy + 2^k - 1) 的上界代入值：n_lossy <= 活跃槽总数
    * 5（C/L0/L1/L2/L3）、k <= 3（D1 档右移最多 3 位）⟹ 5 + 2^3 - 1 = 12。由槽数与档位
    * 宽度推出的结构上界，非实测拟合。 */
  val NarrowLossSlack: Double = 12.0

  // FTZ（flush-to-zero）阈值：由输出格式推导，镜像硬件下溢冲零语义（保号，不产生目标
  // 格式次正规数）：fp32ToFp16Rnd 的 exp < 113（= fp32 编码下的 2^-14）、quantElem 的
  // eTgt < 1（eTgt = e + bias - s，s 为块 E8M0 scale）。阈值一律由格式参数推导。

  /** 浮点输出格式的指数域位宽。MX 族取自 MxFormatParams，FP16/BF16/FP32 取自 IEEE-754。 */
  def floatExpW(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32) 8
    else if (f == DataFormat.BF16) 8
    else if (f == DataFormat.FP16) 5
    else if (f == DataFormat.MXE4M3) MxFormatParams.E4M3.expW
    else if (f == DataFormat.MXE5M2) MxFormatParams.E5M2.expW
    else if (f == DataFormat.MXE2M1) MxFormatParams.E2M1.expW
    else throw new IllegalArgumentException(s"floatExpW: $f 非浮点输出格式，无 FTZ 阈值")

  /** 浮点输出格式的偏置 = 2^(expW-1) - 1（E4M3: 7、E5M2: 15、E2M1: 1）。 */
  def floatBias(f: DataFormat.Type): Int = (1 << (floatExpW(f) - 1)) - 1

  /** 最小正规格化数 = 2^(1 - bias)，FTZ 阈值的格式无关基：|v| 非零且低于此值 → 冲零保号。
    * FP16=2^-14、BF16/FP32=2^-126、MXE4M3=2^-6、MXE5M2=2^-14、MXE2M1=2^0。 */
  def minNormal(f: DataFormat.Type): Double = math.pow(2.0, 1.0 - floatBias(f))

  /** FTZ 命中时期望的保号零码（compare() bit-exact 判据的期望值），符号位位置
    * = cOutElemBits(f) - 1：负 ref → 置符号位、正 ref → 全零。 */
  def ftzSignedZeroCode(f: DataFormat.Type, refNeg: Boolean): Long = {
    val w = WbSimCommon.cOutElemBits(f)
    if (refNeg) (1L << (w - 1)) else 0L
  }

  // 参数解析（大小写不敏感；非法值给清晰报错）

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
      s"[WbDebugModel] 参数 -D$key=$raw 非法：合法取值 = $legal（大小写不敏感）")

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

  // 参数集合

  /** 一次调试运行的全部生效参数。所有字段均可由 -D 覆盖（见 WorkerBeeDebugCase 顶部默认块）。 */
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
      // reg5 bits[21]/[20]；main 侧无硬件消费者（只有字段声明、reg5 写接线与读回 Cat），
      //   不进计算/地址/errCode 逻辑。
      warmup: Boolean = false,
      warmdown: Boolean = false,
      // A/B 基址参数化（默认对齐基址 = 既有常量）。gather 读使非对齐基址合法：
      //   DMA 发对齐多发读 + 内部移位重组，金锚序列/内存映像随基址平移。
      aBase: Long = A_BASE,
      bBase: Long = B_BASE,
      // fused bias：reg5[30] / reg9。biasOn=true 时 C(mm,nn) = bias(nn) + Σ_k aVal*bVal；
      //   bias 向量共 nTiles*totalN 个 4B 字（第 nt 个 N-tile 的 16 个字供 pass-0 psum
      //   基底注入）。biasBase 须 4B 对齐（硬件 errCode12 拦截）。
      biasOn: Boolean = false,
      biasBase: Long = BIAS_BASE,
      // fused requant：reg5[29]=requantOn、reg5[15]=reluOn、reg10=rqMBase、
      //   reg11=[31:16]zp+[5:0]shift。输出链 = sat_fmt(round_half_up(acc×M[nn] >> shift) + zp)，
      //   acc 已含 bias 基底与 ReLU clamp（reluOn=true 且 INT 输出时负槽置 0）；M 为
      //   per-channel int32（N 轴，nTiles*totalN 个 4B 字），shift/zp 为 per-tensor 标量。
      //   合法输出档仅 INT8/INT4（硬件 errCode13 拦截）。
      requantOn: Boolean = false,
      reluOn: Boolean = false,
      rqMBase: Long = RQM_BASE,
      rqShift: Int = 0,
      rqZp: Int = 0) {

    // MXBlockSize 枚举 = {M16=0, M32=1}，bytes = 16<<sel：基档 M16=16 元素、每 +1 档翻倍。
    def aMxBlk: Int = 16 << aMxBlkSel
    def bMxBlk: Int = 16 << bMxBlkSel
    def cMxBlk: Int = 16 << cMxBlkSel

    /** 单一控制字 reg5，位域由寄存器契约独立推导：
      *     [31]    保留 0     | [30]    biasOn      | [29]    requantOn
      *     [28]    loadWMode  | [27]    保留 0
      *     [26]    cMxBlk     | [25]    保留 0       | [24]    bMxBlk
      *     [23]    保留 0     | [22]    aMxBlk       | [21]    warmup
      *     [20]    warmdown   | [19]    保留 0       | [18:16] rnd
      *     [15]    reluOn     | [14]    cAddrMode    | [13]    bAddrMode
      *     [12]    aAddrMode  | [11:8]  cFmt         | [7:4]   bFmt      | [3:0] aFmt
      *   枚举宽度：DataFormat 10 值→4-bit、RoundingMode 5 值→3-bit、AddrMode 2 值→1-bit、
      *     MXBlockSize {M16, M32}→1-bit；Cat 总宽 = 32。
      * 三个 MxBlk 的掩码 `& 0x3` 对 sel∈{0,1}（全部合法取值）与 `& 0x1` 产出逐位相同。 */
    def controlWord5: Long =
      (if (biasOn) 1L << 30 else 0L) |
      (if (requantOn) 1L << 29 else 0L) |
      (if (loadWMode) 1L << 28 else 0L) |
        ((cMxBlkSel.toLong & 0x3) << 26) | ((bMxBlkSel.toLong & 0x3) << 24) |
        ((aMxBlkSel.toLong & 0x3) << 22) |
        (if (warmup)   1L << 21 else 0L) |
        (if (warmdown) 1L << 20 else 0L) |
        ((rnd.toLong & 0x7) << 16) |
        (if (reluOn)   1L << 15 else 0L) |
        ((addrModeSel.toLong & 0x1) << 14) | ((addrModeSel.toLong & 0x1) << 13) |
        ((addrModeSel.toLong & 0x1) << 12) |
        ((cFmt.litValue.toLong & 0xF) << 8) |
        ((bFmt.litValue.toLong & 0xF) << 4) | (aFmt.litValue.toLong & 0xF)

    /** RQ_PARAM：[31:16]zp（int16 二补码）| [15:6]保留0 | [5:0]shift，按寄存器契约打包。 */
    def controlWord14: Long =
      ((rqZp.toLong & 0xFFFFL) << 16) | (rqShift.toLong & 0x3FL)

    /** 生效参数一览（启动时打印） */
    def banner: String =
      s"M=$m N=$n K=$k aFmt=$aFmt bFmt=$bFmt cFmt=$cFmt loadWMode=${if (loadWMode) 1 else 0} " +
        s"aMxBlk=$aMxBlk bMxBlk=$bMxBlk cMxBlk=$cMxBlk addrMode=${if (addrModeSel == 0) "MAT" else "TILE"} " +
        s"rnd=$rnd seed=$seed strictAddr=$strictAddr maxCycles=$maxCycles " +
        f"reg5=0x${controlWord5}%08X" +
        // bias / requant 状态只在启用时进 banner
        (if (biasOn) f" biasOn=1 biasBase=0x${biasBase}%08X" else "") +
        (if (requantOn) f" requantOn=1 reluOn=${if (reluOn) 1 else 0} rqMBase=0x${rqMBase}%08X shift=$rqShift zp=$rqZp" else "")
  }

  // 配置合法性预检（与 WorkerBeeCalcConfig errCode 语义同源，用于分派负向路径）

  /** WorkerBeeCalcConfig errComb 裸块的软件镜像（单一真源，precheckConfigErr 与
    * QuickSpec 体检共用）。用顺序 if + 后写覆盖编码硬件 last-connect 优先级：
    *   3(反向混配) > 3(FP8/BF16 异或) > 11 > 8 > 13 > 12 > 10 > 7 > 5 > 4 > 3(isAMx^isBMx) > 0。
    * errCode 0：硬件把 a/b/cMxBlockSize 三违约合并为 errMXLargerThanKLoadNum，1/2 不可达。
    * code 6（MXE2M1 输出 N 为奇数）在 RTL 中被注释掉，本金锚仍镜像它 —— 已知偏差。
    * code 7 = 容量超限（m > cBufferDepth - totalN），该臂为 RTL-only，本镜像不含它。
    * hasMx4 门控：mix code3 与 MX code8 两臂只在 cfg.hasMx4=true 时例化，本镜像同门控。 */
  def hwErrCode(p: DebugParams, totalN: Int, hasMx4: Boolean = false,
                 cAddr: Long = C_BASE, cDmaExtW: Int = 256): Option[Int] = {
    def kLoadNum(f: DataFormat.Type): Int = if (isMx8(f) || f == DataFormat.INT8 || isFp8Fmt(f)) totalN * 2 else totalN * 4  // 镜像 getKLoadNum（is1B 语义）
    val aMx = isMxAny(p.aFmt); val bMx = isMxAny(p.bFmt); var e = Option.empty[Int]
    if ((aMx && p.aMxBlk > kLoadNum(p.aFmt)) || (bMx && p.bMxBlk > kLoadNum(p.bFmt)) ||
        (isMxAny(p.cFmt) && p.cMxBlk > totalN)) e = Some(0)   // RTL 把三个 blk 违约合并成单一 errCode 0
    if (aMx != bMx) e = Some(3)
    if ((aMx || bMx) && !p.loadWMode) e = Some(4)
    // errCode 4: 纯 FP8 强制 loadWMode=1（阵列侧 remap 走 MX8 数据通路，继承 MX8 run-mode 约束）。
    if (isFp8Fmt(p.aFmt) && !p.loadWMode) e = Some(4)
    // errCode 4 不含 BF16 臂：BF16 两种装载模式均合法（无输入 scale、非打包，权重两条装载
    //   路径位透明；loadW=0 的 K×N 行主序 B 几何对 2 字节元素自洽，bRowOff=n×2）。
    if (p.aFmt == DataFormat.FP32 || p.bFmt == DataFormat.FP32) e = Some(5)
    if (p.cFmt == DataFormat.MXE2M1 && (p.n & 1) == 1) e = Some(6)
    // errCode 10: cAddr 须按 dmaBytes(=cDmaExtW/8) 对齐。wstrb[i] 选通 wdata[8i+7:8i]，
    //   一个写拍覆盖从 addr mod dmaBytes 起的 dmaBytes 条连续字节巷；非对齐时移位后的
    //   strb+data 跨总线字边界、静默丢字节。
    locally { val dmaBytes = cDmaExtW / 8
      if (dmaBytes > 0 && (cAddr % dmaBytes) != 0) e = Some(10) }
    // errCode 12: biasOn && biasAddr%4!=0。bias 走 dmaScaleA 端口逐字（4B）读，
    //   biasReqAddr = biasAddr + nt*totalN*4 + i*4 ⟹ biasAddr[1:0]≠0 时每次读跨界半个字。
    if (p.biasOn && p.biasBase % 4L != 0) e = Some(12)
    // errCode 13: ① requantOn && cFmt ∉ {INT8,INT4}（requant 链只定义两档饱和语义）；
    //   ② requantOn && rqMBase%4!=0（M 向量复用 dmaScaleA 32b 端口逐字读，非 4B 对齐跨界）。
    if (p.requantOn && !(p.cFmt == DataFormat.INT8 || p.cFmt == DataFormat.INT4)) e = Some(13)
    if (p.requantOn && p.rqMBase % 4L != 0) e = Some(13)
    // errCode 8: 打包族（fp4/INT8/INT4）K 须被 kPerBeat 整除，否则 kTiles 地板除静默丢尾或
    //   16-bit 回绕。MX 部分由 hasMx4 门控；INT 部分不设门控。
    val aPackedInt = p.aFmt == DataFormat.INT8 || p.aFmt == DataFormat.INT4
    if (((hasMx4 && isMxAny(p.aFmt)) || aPackedInt) &&
        (p.k % kPerBeat(p.aFmt, totalN) != 0 || p.k == 0)) e = Some(8)
    // errCode 8: 纯 FP8 K 整除守护（kPerBeat=totalN×2）。
    if (isFp8Fmt(p.aFmt) && (p.k % kPerBeat(p.aFmt, totalN) != 0 || p.k == 0)) e = Some(8)
    // errCode 8: MX 混配（a=fp8 × b=fp4）stretch 单读契约，两条守护由张量布局推导：
    //   ① K%64：窗口粒度 32，但 K%64==32（kTile 奇数）会使末窗口 32B 单读越张量界 16B；
    //   ② bAddr%32：半字拍片契约 L mod 32 ∈ {0,16} 与读严格落界内（portBytes=32）。
    if (hasMx4 && (p.aFmt == DataFormat.MXE4M3 || p.aFmt == DataFormat.MXE5M2) &&
        p.bFmt == DataFormat.MXE2M1 &&
        (p.k % kPerBeat(DataFormat.MXE2M1, totalN) != 0 || p.k == 0 ||
         p.bBase % 32L != 0)) e = Some(8)
    // errCode 8: INT 混配（a=INT8 × b=INT4）同款 stretch 单读契约，三条守护：
    //   ⓪ !loadWMode（stretch 半字切片取转置布局一行内连续 32 个 K 元素，K×N 行主序下
    //      几何不可达；纯 INT 两种布局均合法）；① K%64；② bAddr%32。
    if (p.aFmt == DataFormat.INT8 && p.bFmt == DataFormat.INT4 &&
        (!p.loadWMode ||
         p.k % kPerBeat(DataFormat.INT4, totalN) != 0 || p.k == 0 ||
         p.bBase % 32L != 0)) e = Some(8)
    // errCode 8: BF16 K 几何守护（kPerBeat=totalN×1）。BF16 非打包 ⟹ kTiles 走 ceil(K/totalN)
    //   而非地板除 ⟹ ① K%totalN≠0 时多算一个部分填充的尾 kTile、超界 K 元素被真实累加进
    //   结果；② K==0 时 kTiles 回绕 65535 跑飞。
    if (p.aFmt == DataFormat.BF16 &&
        (p.k % kPerBeat(DataFormat.BF16, totalN) != 0 || p.k == 0)) e = Some(8)
    // errCode 11: 整数/非整数跨界守护（XOR，无门控）。cIsIntOut = cFmt 属整数输出族；
    //   runIsInt = aFmt 既非浮点、非 MX、也非纯 FP8（FP8 属 fp 累加族）。
    val cIsIntOut = isIntFmt(p.cFmt)
    val runIsInt = !isFloatFmt(p.aFmt) && !isMxAny(p.aFmt) && !isFp8Fmt(p.aFmt)
    if (runIsInt != cIsIntOut) e = Some(11)
    // errCode 3：只拒反向 MX 混配（A=MX4 × B=MX8，lane 选择子只看 aFmt，反向混配静默算错）；
    //   正向 W4A8（A=MX8 × B=MX4）经 B DMA stretch 合法化。
    if (hasMx4 && p.aFmt == DataFormat.MXE2M1 && p.bFmt != DataFormat.MXE2M1) e = Some(3)
    // errCode 3：反向 INT 混配（A=INT4 × B=INT8）。K 窗口 = aKLoadNum=64 元素，B 每行窗口
    //   切片 = 64 个 INT8 = 64B，是 B 拍宽（totalN*bW/8=32B）的两倍，几何不可达。
    if (p.aFmt == DataFormat.INT4 && p.bFmt == DataFormat.INT8) e = Some(3)
    // errCode 3 链尾两臂（最高优先级）：① isFp8Fmt(aFmt) XOR isFp8Fmt(bFmt)（remap 只改
    //   FP8 码点，异侧格式解码落错支路；FP8×MX8 由 aMx!=bMx 臂覆盖）；② isFp8Fmt(cFmt)
    //   （FP8 输出编码无通路，合法输出 = fp32 或 MX 量化）。
    if (isFp8Fmt(p.aFmt) != isFp8Fmt(p.bFmt) || isFp8Fmt(p.cFmt)) e = Some(3)
    // errCode 3 链尾 BF16 单边异或：补 BF16×INT —— isMxAny(BF16)=false ⟹ aMx!=bMx 臂不
    //   触发，errCode 11 的 runIsInt 对 BF16 取 false，BF16 的 code8/code4 只看 aFmt。
    //   BF16×MX 由 aMx!=bMx 臂、BF16×FP8 由上方 FP8 臂覆盖。cFmt=BF16 是合法输出编码。
    if ((p.aFmt == DataFormat.BF16) != (p.bFmt == DataFormat.BF16)) e = Some(3)
    e
  }

  /** 返回 Some(errCode) 表示硬件会在 EXECUTE 拍直接置 config-err（不进计算）。 */
  def precheckConfigErr(p: DebugParams, totalN: Int, hasMx4: Boolean = false,
                         cAddr: Long = C_BASE, cDmaExtW: Int = 256): Option[Int] =
    hwErrCode(p, totalN, hasMx4, cAddr, cDmaExtW)

  /** 运行前防呆：不支持的组合给清晰报错（harness 不得崩溃，故在 simulate 之前抛）。
    * hasMx4 入参门控 MX4×MX8 混配 require：hasMx4=false 时 fp4 输入已被 Plan 的 cfg-aware
    * 守卫拦截；hasMx4=true 时才拦混配。 */
  def validate(p: DebugParams, totalN: Int, hasMx4: Boolean = false): Unit = {
    require(p.m > 0 && p.n > 0 && p.k > 0, s"M/N/K 必须 > 0，当前 M=${p.m} N=${p.n} K=${p.k}")
    require(p.rnd >= 0 && p.rnd <= 4, s"rnd=${p.rnd} 越界（0..4）")
    if (isMxAny(p.aFmt) || isMxAny(p.bFmt)) {
      // MX8×MX4 混配时两侧各自有整除要求（MX8 kpb=32、fp4 源节拍 kpb=64），取 math.max
      //   即同时约束两侧；单侧 MX 时 = 该侧 kPerBeat。
      val kpb = if (isMxAny(p.aFmt) && isMxAny(p.bFmt))
        math.max(kPerBeat(p.aFmt, totalN), kPerBeat(p.bFmt, totalN))
      else kPerBeat(if (isMxAny(p.aFmt)) p.aFmt else p.bFmt, totalN)
      require(p.k % kpb == 0,
        s"MX 运行要求 K(${p.k}) 能被 kPerBeat=$kpb 整除（一个 kTile = 一个 scale 块 = 单 pass）")
      require(p.loadWMode, "MX 运行必须 loadWMode=1（B 转置存储，硬件 errCode 4 拦截）")
      // W4A8 混配 stretch 单读契约：bAddr 须 32B 对齐（硬件 errCode8 镜像）。
      if (isMx8(p.aFmt) && p.bFmt == DataFormat.MXE2M1)
        require(p.bBase % 32 == 0,
          s"W4A8 混配要求 bAddr 32B 对齐（stretch 半字拍片单读契约，硬件 errCode 8），当前 bBase=0x${p.bBase.toHexString}")
    }
    if (isMxAny(p.aFmt)) require(p.k % p.aMxBlk == 0, s"K(${p.k}) 必须被 aMxBlk(${p.aMxBlk}) 整除")
    if (isMxAny(p.bFmt)) require(p.k % p.bMxBlk == 0, s"K(${p.k}) 必须被 bMxBlk(${p.bMxBlk}) 整除")
    // 纯 FP8 防呆（镜像硬件 errCode 8/4/3）：K 被 kPerBeat=totalN×2 整除、loadWMode=1、两侧同为 FP8。
    if (isFp8Fmt(p.aFmt) || isFp8Fmt(p.bFmt)) {
      require(p.k % kPerBeat(p.aFmt, totalN) == 0,
        s"纯 FP8 运行要求 K(${p.k}) 被 kPerBeat=${kPerBeat(p.aFmt, totalN)} 整除（硬件 errCode 8）")
      require(p.loadWMode, "纯 FP8 运行必须 loadWMode=1（阵列侧走 MX8 remap 数据通路，硬件 errCode 4 拦截）")
      require(isFp8Fmt(p.aFmt) == isFp8Fmt(p.bFmt),
        s"FP8 跨族混配不支持（硬件 errCode 3 拦截），当前 aFmt=${p.aFmt} bFmt=${p.bFmt}")
    }
    // INT 混配（a=INT8 × b=INT4）stretch 单读契约（与 MX W4A8 同款）：K 被 INT4 源字粒度
    //   kPerBeat=totalN*4 整除、bAddr 32B 对齐。
    if (p.aFmt == DataFormat.INT8 && p.bFmt == DataFormat.INT4) {
      require(p.loadWMode,
        s"INT W4A8 混配要求 loadWMode=1（stretch 半字切片取转置布局一行内连续 32 个 K 元素，" +
          s"loadW=0 的 K×N 行主序几何不可达，硬件 errCode 8）；纯 INT8/INT4 两种布局均合法")
      require(p.k % kPerBeat(DataFormat.INT4, totalN) == 0,
        s"INT W4A8 混配要求 K(${p.k}) 被 kPerBeat=${kPerBeat(DataFormat.INT4, totalN)} 整除（B 侧 INT4 源字粒度，硬件 errCode 8）")
      require(p.bBase % 32 == 0,
        s"INT W4A8 混配要求 bAddr 32B 对齐（stretch 半字拍片单读契约，硬件 errCode 8），当前 bBase=0x${p.bBase.toHexString}")
    }
    // BF16 防呆：K 被 kPerBeat=totalN×1 整除 —— BF16 非打包、kTiles 走 ceil，K%totalN≠0 会
    //   多算一个部分填充尾 tile 并累加超界元素。两种装载模式均合法，B 布局按 loadWMode 分支
    //   （loadW=1 → N×K 转置，loadW=0 → K×N 行主序，bRowOff=(if loadW then k else n)×bEb）。
    if (p.aFmt == DataFormat.BF16 || p.bFmt == DataFormat.BF16) {
      require(p.aFmt == p.bFmt,
        s"BF16 只支持 BF16×BF16（跨族混配硬件 errCode 3 拦截：BF16×INT 由链尾 BF16 异或臂、" +
          s"BF16×MX 由裸 isAMx^isBMx 臂、BF16×FP8 由 FP8 异配臂），当前 aFmt=${p.aFmt} bFmt=${p.bFmt}")
      require(p.k % kPerBeat(DataFormat.BF16, totalN) == 0,
        s"BF16 运行要求 K(${p.k}) 被 kPerBeat=${kPerBeat(DataFormat.BF16, totalN)} 整除（硬件 errCode 8）")
    }
    // MX 混配守护（硬件 errCode3 同源）：正向 MX8(A)×MX4(B) 合法——走 B DMA stretch 路
    //   （16B fp4 半字拍片 32B 对齐单读 + 顶层 LUT 把 E2M1 nibble 精确加宽成 E4M3 码字，
    //   阵列按 MX8×MX8 跑），由 errCode8 的 K%64 与 bAddr%32 守护；反向 MX4(A)×MX8(B) 拦截
    //   （lane 粒度选择子只看 aFmt）。E4M3×E5M2 两侧 (==MXE2M1) 均 false ⟹ 合法。
    if (hasMx4)
      require(!(isMxAny(p.aFmt) && isMxAny(p.bFmt)) ||
              (p.aFmt == DataFormat.MXE2M1) == (p.bFmt == DataFormat.MXE2M1) ||
              ((p.aFmt == DataFormat.MXE4M3 || p.aFmt == DataFormat.MXE5M2) && p.bFmt == DataFormat.MXE2M1),
        s"MX mixed format unsupported: reverse mix (A=MX4 x B=MX8) is rejected by HW errCode 3; " +
        s"a/b must be both fp4(E2M1), both fp8(E4M3/E5M2), or forward mix A=fp8 x B=fp4 (W4A8), " +
        s"current aFmt=${p.aFmt} bFmt=${p.bFmt}")
    // fp4 blk 必须是寄存器字段能编码的两个值之一（MXBlockSize 是 2 级枚举 {M16, M32}）。
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
    // bias 向量 4B 对齐防呆（硬件 errCode12 拦截）。
    if (p.biasOn)
      require(p.biasBase % 4 == 0,
        s"biasOn 要求 biasBase 4B 对齐（dmaScaleA 端口逐字读，硬件 errCode 12），当前 biasBase=0x${p.biasBase.toHexString}")
    // requant 参数防呆（硬件 errCode13 拦截）。
    if (p.requantOn) {
      require(p.cFmt == DataFormat.INT8 || p.cFmt == DataFormat.INT4,
        s"requantOn 要求 cFmt 为 INT8/INT4（requant 链仅两档饱和语义，硬件 errCode 13），当前 cFmt=${p.cFmt}")
      require(p.rqMBase % 4 == 0,
        s"requantOn 要求 rqMBase 4B 对齐（dmaScaleA 端口逐字读，硬件 errCode 13），当前 rqMBase=0x${p.rqMBase.toHexString}")
      // requant 只定义在纯整数输入链上：errCode13 把 cFmt 钉到 INT8/INT4，errCode11
      //   （runIsInt XOR cIsIntOut）随即强制输入侧同为整数族；金锚 requant 链（Long 整数
      //   算术）也仅在 acc 为精确整数时有定义。
      require(!isFloatFmt(p.aFmt) && !isMxAny(p.aFmt) && !isFloatFmt(p.bFmt) && !isMxAny(p.bFmt),
        s"requantOn 要求 A/B 输入均为整数族（errCode11 XOR 约束 + requant 整数累加链定义域），当前 aFmt=${p.aFmt} bFmt=${p.bFmt}")
    }
    require(p.rqShift >= 0 && p.rqShift <= 63, s"rqShift=${p.rqShift} 越界（6-bit，0..63）")
    require(p.rqZp >= -32768 && p.rqZp <= 32767, s"rqZp=${p.rqZp} 越界（int16）")
    // addrMode 位域（reg5 bits[14:12]）在硬件侧零消费者（CalcConfig 与两个 DMA 均不引用）。
    //   内存镜像、golden 计算、六路期望地址序列全部按 MAT 语义生成，不受 addrMode 取值影响。
  }

  // 字节内存镜像

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

    /** 写入一个亚字节元素（elemBits<8）到 byteAddr 的 bitOff 位处；读-改-写保留邻 nibble。
      * fp4(E2M1) 布局：元素 k 落 byte=k/2、nibble=k%2（低 nibble = 偶 k，小端 nibble 序）。 */
    def storeSubByte(byteAddr: Long, bits: Long, bitOff: Int, elemBits: Int): Unit = {
      val mask = (1 << elemBits) - 1
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

  // 元素编解码

  private def sext(bits: Long, w: Int): Long = {
    val v = bits & ((1L << w) - 1)
    if (((v >>> (w - 1)) & 1L) == 1L) v - (1L << w) else v
  }

  /** 位模式 → Double（MX 族需传对应块 scale 字节） */
  def decodeElem(f: DataFormat.Type, bits: Long, scaleByte: Int): Double =
    if (f == DataFormat.FP32) WbSimCommon.fp32BitsToFloat(bits & 0xFFFFFFFFL).toDouble
    else if (f == DataFormat.FP16) WbSimCommon.fp16ToFloat((bits & 0xFFFFL).toInt).toDouble
    else if (f == DataFormat.BF16) WbSimCommon.fp32BitsToFloat((bits & 0xFFFFL) << 16).toDouble
    else if (f == DataFormat.INT32) sext(bits, 32).toDouble
    else if (f == DataFormat.INT16) sext(bits, 16).toDouble
    else if (f == DataFormat.INT8) sext(bits, 8).toDouble
    else if (f == DataFormat.INT4) sext(bits, 4).toDouble
    else if (f == DataFormat.MXE4M3) WbSimCommon.mxE4M3ToFloat((bits & 0xFFL).toInt, scaleByte)
    else if (f == DataFormat.MXE5M2) WbSimCommon.mxE5M2ToFloat((bits & 0xFFL).toInt, scaleByte)
    // 纯 FP8 输入解码：元素码 = 裸 E4M3/E5M2，scale 恒 127 ⟹ 等价无 scale 纯解码。
    else if (f == DataFormat.FP8E4M3) WbSimCommon.mxE4M3ToFloat((bits & 0xFFL).toInt, scaleByte)
    else if (f == DataFormat.FP8E5M2) WbSimCommon.mxE5M2ToFloat((bits & 0xFFL).toInt, scaleByte)
    else if (f == DataFormat.MXE2M1) {
      // 输出量化域专用解码。e==0 → 0.0 建模硬件输出侧 FTZ：E2M1 minNormal=2^(1-bias)=1.0，
      //   任何 |v|<1.0（含 e=0/m=1 的 ±0.5）被 flush，硬件输出码流中不存在 ±0.5。
      //   fp4 输入解码走 decodeMxE2M1Input（±0.5 是一等公民值），两域不可混用。
      val c = (bits & 0xFL).toInt
      val sg = (c >> 3) & 1
      val e = (c >> 1) & 0x3
      val mm = c & 1
      val v = if (e == 0 || scaleByte == 0) 0.0
              else (1.0 + mm.toDouble / 2) * math.pow(2, e - 1) * math.pow(2, scaleByte - 127)
      if (sg == 1) -v else v
    } else throw new IllegalArgumentException(s"decodeElem: 未知格式 $f")

  /** 输入解码域：MXFP4(E2M1) 元素 + 块 scale → Double。E2M1 参数 expW=2/mantW=1/bias=1、
    * noInfNan=true、subnormalFtz=false ⟹ e=0/m=1 解出 ±0.5×2^(scale-127)。 */
  def decodeMxE2M1Input(bits: Long, scaleByte: Int): Double =
    WbSimCommon.mxE2M1ToFloat((bits & 0xFL).toInt, scaleByte)

  /** 输入解码域统一入口：位模式 → Double（A/B 激励解码，golden 累加消费）。MXE2M1 走
    * decodeMxE2M1Input，其余委托 decodeElem；输出量化域（按 cFmt）用 decodeElem。 */
  def decodeInElem(f: DataFormat.Type, bits: Long, scaleByte: Int): Double =
    if (f == DataFormat.MXE2M1) decodeMxE2M1Input(bits, scaleByte)
    else decodeElem(f, bits, scaleByte)

  /** Double → 位模式（按格式量化；rnd 用于 bf16/fp16 舍入模式） */
  def encodeElem(f: DataFormat.Type, v: Double, rnd: Int): Long =
    if (f == DataFormat.FP32) (java.lang.Float.floatToRawIntBits(v.toFloat) & 0xFFFFFFFFL)
    else if (f == DataFormat.FP16) (WbSimCommon.floatToFp16(v.toFloat) & 0xFFFFL)
    else if (f == DataFormat.BF16)
      (WbSimCommon.swFp32ToBf16(java.lang.Float.floatToRawIntBits(v.toFloat) & 0xFFFFFFFFL, rnd) & 0xFFFFL)
    else if (f == DataFormat.INT32) (math.rint(v).toLong & 0xFFFFFFFFL)
    else if (f == DataFormat.INT16) (math.rint(v).toLong & 0xFFFFL)
    else if (f == DataFormat.INT8) (math.rint(v).toLong & 0xFFL)
    else if (f == DataFormat.INT4) (math.rint(v).toLong & 0xFL)
    else if (f == DataFormat.MXE4M3) (WbSimCommon.floatToMxE4M3(v) & 0xFFL)
    else if (f == DataFormat.MXE5M2) (WbSimCommon.floatToMxE5M2(v) & 0xFFL)
    // 纯 FP8 与 MX8 元素码同一函数（无 scale 参与）：per-tensor scale 由软件在量化
    //   pre-pass 中折进权重，硬件侧恒等（127）。
    else if (f == DataFormat.FP8E4M3) (WbSimCommon.floatToMxE4M3(v) & 0xFFL)
    else if (f == DataFormat.FP8E5M2) (WbSimCommon.floatToMxE5M2(v) & 0xFFL)
    else if (f == DataFormat.MXE2M1) encodeMxE2M1(v)
    else throw new IllegalArgumentException(s"encodeElem: 格式 $f 不支持作为输入编码")

  /** 输入编码域：float → MXFP4(E2M1) 元素码（scale-free 内蕴值，scale=127）。在 16 个合法
    * 4-bit 码中暴力挑选输入解码值最接近 v 者，用 decodeMxE2M1Input（subnormalFtz=false）⟹
    * ±0.5 可被正确编码（WbSimCommon.floatToMx 默认 subnormalFtz=true 会把它 flush）。
    * 相等误差偏好较小码（严格 < 才替换）⟹ 确定性。仅低 4 位有效。 */
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
      val norm = WbSimCommon.swFpNormalize40to32(
        WbSimCommon.fp32BitsTo40(java.lang.Float.floatToRawIntBits(golden.toFloat) & 0xFFFFFFFFL))
      if (cFmt == DataFormat.FP32) norm & 0xFFFFFFFFL
      else if (cFmt == DataFormat.FP16) WbSimCommon.swFp32ToFp16(norm, rnd).toLong & 0xFFFFL
      else if (cFmt == DataFormat.BF16) WbSimCommon.swFp32ToBf16(norm, rnd).toLong & 0xFFFFL
      else 0L   // MX 量化输出走 swQuantBlock 路径，不用本函数
    }
  }

  // 计划：几何 / 内存镜像 / 期望地址 / golden

  final class Plan(val p: DebugParams, val cfg: WorkerBeeConfig) {
    val totalN: Int = cfg.totalN
    val cDmaExtW: Int = cfg.cDmaExtW

    // cfg-aware 守卫：跑 mxfp4 需 cfg.hasMx4，否则 E2M1 落 E4M3 臂、fmtOk=false、
    //   psumReg 停摆，静默失配不可诊断。
    if (p.aFmt == DataFormat.MXE2M1 || p.bFmt == DataFormat.MXE2M1)
      require(cfg.hasMx4,
        s"aFmt/bFmt=mxfp4(E2M1) 需硬件 supportedFmts 含 MXE2M1（当前 cfg.hasMx4=${cfg.hasMx4}）；" +
        s"WorkerBeeDebugCase 的 dbgCfg 用默认 supportedFmts 不含 fp4 → 请改用 mxe4m3/mxe5m2，" +
        s"或换用 hasMx4=true 的 cfgMxFp4 配置（aFmt=${p.aFmt} bFmt=${p.bFmt}）")

    // cfg-aware 守卫：FP8 输入需 cfg.hasFp8=true。
    if (isFp8Fmt(p.aFmt) || isFp8Fmt(p.bFmt))
      require(cfg.hasFp8,
        s"aFmt/bFmt=纯FP8(FP8E4M3/FP8E5M2) 需硬件 supportedFmts 含 FP8（当前 cfg.hasFp8=${cfg.hasFp8}）" +
        s"（aFmt=${p.aFmt} bFmt=${p.bFmt}）")

    val aEb: Int = memElemBytes(p.aFmt)
    val bEb: Int = memElemBytes(p.bFmt)
    // bits 口径是几何单一真源（aEb/bEb 供整字节 storeElem 快路径）：地址偏移一律用
    //   X*aEbits/8；字节对齐族与 X*aEb 逐值恒等，fp4(aEbits=4) 得正确半字节地址
    //   （k*4/8=k/2、aKpb*4/8=32），不因 aEb=0 退化。
    val aEbits: Int = memElemBits(p.aFmt)
    val bEbits: Int = memElemBits(p.bFmt)
    val aKpb: Int = kPerBeat(p.aFmt, totalN)      // A 每拍 K 元素数
    val bKpb: Int = kPerBeat(p.bFmt, totalN)
    // kTiles 判据按「打包格式」（elemsPerLane>1：MX / INT8 / INT4 / FP8）：每拍推进
    //   kPerBeat 个 K ⟹ kTiles = k / kPerBeat（地板除，errCode 8 已强制 K 整除）；
    //   非打包格式 ceil(k/totalN)。
    val aKTiles: Int = if (isMxAny(p.aFmt) || p.aFmt == DataFormat.INT8 || p.aFmt == DataFormat.INT4 || isFp8Fmt(p.aFmt)) p.k / aKpb else (p.k + totalN - 1) / totalN
    val bKTiles: Int = if (isMxAny(p.bFmt) || p.bFmt == DataFormat.INT8 || p.bFmt == DataFormat.INT4 || isFp8Fmt(p.bFmt)) p.k / bKpb else (p.k + totalN - 1) / totalN
    val nTiles: Int = (p.n + totalN - 1) / totalN

    // ---- 期望地址偏移（张量语义推导，不镜像 WorkerBeeCalcConfig 谓词链） ----
    val aRowOff: Long = p.k.toLong * aEbits / 8                 // A 行步进
    val aColOff: Long = aKpb.toLong * aEbits / 8                // A kTile 步进
    val bRowOff: Long = (if (p.loadWMode) p.k else p.n).toLong * bEbits / 8
    val bNTileOff: Long = if (p.loadWMode) totalN.toLong * bRowOff else totalN.toLong * bEbits / 8
    // B 的 kTile 步进（loadW=1）= bKpb*bEbits/8，镜像 A 侧 aColOff：三处 kTile 步进
    //   （aColOff、saColOff=aKpb/aMxBlk、sbColOff=bKpb/bMxBlk）一律用 kPerBeat。
    //   物理不重叠即可定值：MX 转置 B 行 n = K 连续字节（bEb=1），一个 kTile = bKpb = 32
    //   元素 = 32 字节；若用 totalN*bEb = 16 只有半个 kTile，kt≥1 起读会与前一 kTile
    //   重叠（kt0=[0,32)、kt1=[16,48)），物理不可能。非 MX 下 bKpb == totalN，两式数值
    //   恒等，差异只在 MX 且 kTiles>1 时显现；硬件 colTileOffset =
    //   totalN*elemsPerLane(fmt)*memElemBytes(fmt) 与本式全格式恒等。
    //   loadW=0 分支 totalN*bRowOff：loadW=0 恒非 MX（errCode 4），bKpb==totalN；
    //   bNTileOff(loadW=1)=totalN*bRowOff 是 n-tile（N 维）步进而非 kTile 步进。
    //   残余偏差：loadW=1 时 RdOnly 的 lastBTileStartAddr 累加模型在 nt 维有
    //   Δ_nt = nt*(kT*C − R)（K 非 bKpb 整数倍且 nTiles≥2 时非零）。
    val bKTileOff: Long = if (p.loadWMode) bKpb.toLong * bEbits / 8 else totalN.toLong * bRowOff

    val saRowOff: Long = (p.k / p.aMxBlk).toLong                // scaleA 行步进 = K/aBlk
    val saColOff: Long = (aKpb / p.aMxBlk).toLong               // scaleA kTile 步进
    val sbRowOff: Long = (p.k / p.bMxBlk).toLong                // scaleB n 向步进 = K/bBlk
    val sbColOff: Long = (bKpb / p.bMxBlk).toLong               // scaleB kTile 步进

    val beatsPerRow: Int = WbSimCommon.cBeatsPerRow(p.cFmt, totalN, cDmaExtW)
    val expCAddrSeq: Seq[Long] =
      WbSimCommon.cAddrSeqDense(C_BASE, p.m, p.n, totalN, p.cFmt, cDmaExtW)

    val expCScaleAddrSeq: Seq[Long] =
      if (isMxAny(p.cFmt)) {
        val rowOff = (p.n + p.cMxBlk - 1).toLong / p.cMxBlk
        val colOff = totalN.toLong / p.cMxBlk
        for (nt <- 0 until nTiles; mm <- 0 until p.m) yield SC_BASE + nt * colOff + mm * rowOff
      } else Seq.empty

    // ---- gather 物理序列：读 DMA 对非对齐逻辑拍发对齐多发读。覆盖 [L, L+portBytes) 需读：
    //   off = L mod portBytes == 0 → [L]（单读）；off != 0 → [L-off, L-off+portBytes]
    //   （两个对齐字，内部移位重组）。A/B portBytes=32（256-bit），scale portBytes=4。
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
    // W4A8 混配（A=MX8/INT8 × B=MX4/INT4）走阵列窗口口径：窗口数 = K/aKpb，窗口 w 覆盖
    //   行内字节 [16w,16w+16)（32 个 4-bit = 一个阵列 K 窗口的一行），块内 totalN 行降序。
    //   物理读 = stretch 单对齐读 [L&~31, +32)（半字片由 DMA 内半字选择，非 gather 两读），
    //   该单读契约由 errCode 8 的 K%64 且 bAddr%32 保证末拍不越界。纯格式走 else 臂。
    val isW4A8 = isMx8(p.aFmt) && p.bFmt == DataFormat.MXE2M1
    // INT 混配与 MX W4A8 几何同构（aKpb=32 窗口、bKTileOffMix=16B、stretch 单对齐读）故共用
    //   混配臂；但 INT 族无 scale 通路 ⟹ expScaleBAddrSeq 的混配臂仅 isW4A8 命中。
    val isIntW4A8 = p.aFmt == DataFormat.INT8 && p.bFmt == DataFormat.INT4
    val expBAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isW4A8 || isIntW4A8) {
        val bKTileOffMix = aKpb.toLong * bEbits / 8                 // 混配 k 窗口步进 = 16B
        for (r <- 0 until nTiles; w <- 0 until (p.k / aKpb)) {
          val blockStart = p.bBase + r * bNTileOff + (totalN - 1) * bRowOff + w * bKTileOffMix
          for (s <- 0 until totalN) buf += blockStart - s * bRowOff
        }
        buf.toSeq.map(addr => addr - addr % 32L)
      } else {
        for (r <- 0 until nTiles; kt <- 0 until bKTiles) {
          val blockStart = p.bBase + r * bNTileOff + (totalN - 1) * bRowOff + kt * bKTileOff
          for (s <- 0 until totalN) buf += blockStart - s * bRowOff
        }
        buf.toSeq.flatMap(gatherPhys(_, 32L))
      }
    }

    val expScaleAAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isMxAny(p.aFmt))
        for (_ <- 0 until nTiles; kt <- 0 until aKTiles; mm <- 0 until p.m)
          buf += SA_BASE + mm * saRowOff + kt * saColOff
      buf.toSeq.flatMap(gatherPhys(_, 4L))
    }

    // bias 期望读地址序列：每个 N-tile 一次、tile 内 totalN 个连续 4B 字（i = nt*totalN + lane）。
    //   不走 gatherPhys：bias 取数是顶层 FSM 直接驱动 dmaScaleA 端口的裸地址，对齐性由
    //   errCode 12 保证。两流共用同一端口 ⟹ TB 须按地址域拆流对账。
    val expBiasAddrSeq: Seq[Long] =
      if (p.biasOn)
        for (nt <- 0 until nTiles; i <- 0 until totalN)
          yield p.biasBase + (nt.toLong * totalN + i.toLong) * 4
      else Seq.empty

    // rqM 期望读地址序列：与 bias 同式（同样不走 gatherPhys），
    //   rqMReqAddr = rqMBase + nt*totalN*4 + i*4，对齐性由 errCode 13 保证。
    val expRqMAddrSeq: Seq[Long] =
      if (p.requantOn)
        for (nt <- 0 until nTiles; i <- 0 until totalN)
          yield p.rqMBase + (nt.toLong * totalN + i.toLong) * 4
      else Seq.empty

    // 混配 scaleB 期望序列按阵列窗口口径：窗口数 = K/aKpb，窗口 w 的 32-K 块 = blk16 的
    //   {2w,2w+1} / blk32 的 {w} ⟹ kTile 步进 = aKpb/bMxBlk（blk16→2B、blk32→1B），
    //   块内 totalN 行降序，非对齐交 gather。纯格式用 sbColOff = bKpb/blk。
    val expScaleBAddrSeq: Seq[Long] = {
      val buf = mutable.ArrayBuffer.empty[Long]
      if (isW4A8) {
        val sbColOffMix = (aKpb / p.bMxBlk).toLong
        for (r <- 0 until nTiles; w <- 0 until (p.k / aKpb)) {
          val blockStart = SB_BASE + r * (totalN * sbRowOff) + (totalN - 1) * sbRowOff + w * sbColOffMix
          for (s <- 0 until totalN) buf += blockStart - s * sbRowOff
        }
      } else if (isMxAny(p.bFmt))
        for (r <- 0 until nTiles; kt <- 0 until bKTiles) {
          val blockStart = SB_BASE + r * (totalN * sbRowOff) + (totalN - 1) * sbRowOff + kt * sbColOff
          for (s <- 0 until totalN) buf += blockStart - s * sbRowOff
        }
      buf.toSeq.flatMap(gatherPhys(_, 4L))
    }

    // 激励（固定种子，确定性）
    private val rng = new java.util.Random(p.seed)

    private def nextVal(f: DataFormat.Type): Double =
      if (isIntFmt(f)) (rng.nextInt(16) - 8).toDouble              // [-8,7]
      else if (isMxAny(f)) (rng.nextDouble() * 3.0 - 1.5)          // ~[-1.5,1.5]
      else (rng.nextDouble() * 4.0 - 2.0)                          // ~[-2,2]

    val aCode: Array[Array[Long]] = Array.tabulate(p.m, p.k)((_, _) => encodeElem(p.aFmt, nextVal(p.aFmt), p.rnd))
    val bCode: Array[Array[Long]] = Array.tabulate(p.k, p.n)((_, _) => encodeElem(p.bFmt, nextVal(p.bFmt), p.rnd))

    // fused bias 向量激励与张量语义值。独立 rng 实例（种子异或盐值）使扰动只进 bias。
    // 长度 nTiles*totalN 个 4B 字：第 nt 个 tile 的 totalN 个字 = 输出列 nt*totalN+i 的基底。
    // 值域 (rng.nextInt(33)-16)/8 栅格 = ±2.0 步进 0.125 的二进有理数：fp32 精确可表示、
    //   |v|>=0.125 或 0，永不落 subnormal ⟹ goldenExact 的 fp32 精确性判定链成立，且硬件
    //   fp32→lazy40 转换无损往返。INT 运行（runIsInt，errCode 11 保证 cFmt 为整数族）存
    //   INT32 补码小整数，对应硬件符号扩展到 40bit psum 注入。
    private val biasRng = new java.util.Random(p.seed ^ 0x5AB1A5L)
    private val biasRunIsInt = !isFloatFmt(p.aFmt) && !isMxAny(p.aFmt) && !isFp8Fmt(p.aFmt)
    /** biasCode(i)：第 i 个 bias 字的 4B 位模式（i = nt*totalN + lane），fp32 bits 或 INT32 补码 */
    val biasCode: Array[Long] =
      if (!p.biasOn) Array.empty
      else Array.tabulate(nTiles * totalN) { _ =>
        val v = biasRng.nextInt(33) - 16
        if (biasRunIsInt) v.toLong
        else (java.lang.Float.floatToRawIntBits(v.toFloat / 8.0f).toLong & 0xFFFFFFFFL)
      }
    /** biasVal(nn)：输出列 nn 的基底张量语义值（golden 累加初值） */
    def biasVal(nn: Int): Double =
      if (!p.biasOn) 0.0
      else if (biasRunIsInt) biasCode(nn).toDouble
      else java.lang.Float.intBitsToFloat(biasCode(nn).toInt)

    // fused requant 的 rqM per-channel 乘子向量激励。独立 rng 实例使扰动只进 rqM。
    // 长度 nTiles*totalN 个 4B 字（i = nt*totalN + lane，与 bias 同构布局）。
    // 值域定向覆盖饱和角点（M 为有符号 int32）：
    //   lane%6==0 → 0x40000000（2^30 巨乘子）：|acc|>=1 时任何 shift<=17 全饱和，覆盖
    //      sat_hi(+127/+7) 与负积 sat_lo(-128/-8) 两侧角点；
    //   lane%6==1 → 0（零乘子）：y ≡ clamp(zp)，覆盖纯 zp 路径；
    //   lane%6==2 → 1..251 小正乘子：小 shift(1..8) 商落值域内，覆盖 round-half-up 边界；
    //   lane%6==3 → 0x30000000：另一巨乘子 lane（跨 tile 下标不同，防与零臂偶合）；
    //   lane%6==4 → (1..15)<<20 中乘子：中移位档（shift 10..25）混饱和/在域；
    //   lane%6==5 → reluOn 时取小正乘子，否则小负乘子：等价式 f(max(acc,0))=max(f(acc),f(0))
    //      依赖 M>=0 单调，负乘子会把 ReLU 后的非负 acc 映成负商；非 ReLU 用例覆盖符号乘法。
    private val rqMRng = new java.util.Random(p.seed ^ 0x2E9A17L)
    /** rqMCode(i)：第 i 个 rqM 4B 字的位模式（有符号 int32 二补码，i = nt*totalN + lane） */
    val rqMCode: Array[Long] =
      if (!p.requantOn) Array.empty
      else Array.tabulate(nTiles * totalN) { i =>
        (i % totalN) % 6 match {
          case 0 => 0x40000000L
          case 1 => 0L
          case 2 => (rqMRng.nextInt(251) + 1).toLong
          case 3 => 0x30000000L
          case 4 => (rqMRng.nextInt(15) + 1).toLong << 20
          case _ => if (p.reluOn) (rqMRng.nextInt(251) + 1).toLong
                    else -(rqMRng.nextInt(251) + 1).toLong
        }
      }
    /** rqMVal(nn)：输出列 nn 的有符号 int32 乘子（硬件侧存 UInt(32) 原始字后 asSInt） */
    def rqMVal(nn: Int): Long = (rqMCode(nn) << 32) >> 32

    /** scale 指数 {-1,0,1}（确定性、含正负、跨块跨列变化以暴露路由错位） */
    def scaleExpA(mm: Int, j: Int): Int = ((mm + j + (p.seed % 3).toInt) % 3) - 1
    def scaleExpB(j: Int, nn: Int): Int = ((nn + 2 * j + (p.seed % 3).toInt) % 3) - 1

    val saEntries: Int = p.k / p.aMxBlk
    val sbEntries: Int = p.k / p.bMxBlk
    /** scaleA(mm)(j)，j = k 块下标（块大小 = aMxBlk） */
    val scaleA: Array[Array[Int]] =
      if (isMxAny(p.aFmt)) Array.tabulate(p.m, saEntries)((mm, j) => WbSimCommon.mxScaleByte(scaleExpA(mm, j)))
      else Array.empty
    /** scaleBVal(nn)(j) —— 注意内存布局是 n 向为行（与硬件 n 向步进 K/bBlk 对应） */
    val scaleB: Array[Array[Int]] =
      if (isMxAny(p.bFmt)) Array.tabulate(p.n, sbEntries)((nn, j) => WbSimCommon.mxScaleByte(scaleExpB(j, nn)))
      else Array.empty

    // effScaleA / effScaleB：OCP MX 定义赋予单个元素的 E8M0 scale。每行的 K 轴划分为
    //   aMxBlk 个元素的连续块，块 j 覆盖 [j*aMxBlk, (j+1)*aMxBlk) 并携带唯一 scale 字节
    //   ⟹ 元素 (mm, k) 的 scale = scaleA(mm)(k / aMxBlk)；该式全域且在界内（validate 要求
    //   K % aMxBlk == 0，scaleA 恰有 saEntries = K/aMxBlk 列）。B 侧同理 scaleB(nn)(k/bMxBlk)：
    //   B 张量 N-major 存储，但块划分始终沿 K 轴。
    //   与数据路交叉核对：kTile kt 的 lane i 承载 k = kt*kPerBeat + i*step .. +step-1
    //   （step = elemsPerLane）。因 blk >= step 且 blk 整除 kPerBeat，lane 永不跨块边界，
    //   其块下标 = kt*(kPerBeat/blk) + (i*step)/blk 即 Executor 选的字节槽；DMA 在
    //   base + row*(K/blk) + kt*(kPerBeat/blk) 读到的字把块 kt*(kPerBeat/blk)+j 放进字节 j。
    def effScaleA(mm: Int, k: Int): Int =
      if (!isMxAny(p.aFmt)) 127
      else scaleA(mm)(k / p.aMxBlk)
    def effScaleB(nn: Int, k: Int): Int =
      if (!isMxAny(p.bFmt)) 127
      else scaleB(nn)(k / p.bMxBlk)

    // 输入解码走 decodeInElem（MXE2M1 → decodeMxE2M1Input，±0.5 不 flush）；
    //   输出量化域（dequant/compare/printRows）用 decodeElem，两域不交叉。
    def aVal(mm: Int, k: Int): Double = decodeInElem(p.aFmt, aCode(mm)(k), effScaleA(mm, k))
    def bVal(k: Int, nn: Int): Double = decodeInElem(p.bFmt, bCode(k)(nn), effScaleB(nn, k))

    /** 软件 golden：C(mm)(nn) = bias(nn) + Σ_k aVal*bVal（Double 精度，容差对拍基准）。
      * biasOn 时累加初值 = biasVal(nn)（张量语义 C = bias + A·B，硬件侧为 pass-0 psum 基底注入）。 */
    def golden(mm: Int, nn: Int): Double = {
      var acc = if (p.biasOn) biasVal(nn) else 0.0
      var k = 0
      while (k < p.k) { acc += aVal(mm, k) * bVal(k, nn); k += 1 }
      acc
    }

    lazy val goldenC: Array[Array[Double]] = {
      val raw = Array.tabulate(p.m, p.n)(golden)
      // ReLU 消费域 = INT 族输出（fp/MX 输出对 reluOn 是零消费者位）⟹ 门控 = reluOn &&
      //   isIntFmt(cFmt)。INT 输入运行的累加器是精确整数 ⟹ Double 域 max(·,0) 与硬件
      //   32-bit 槽 clamp 等价（errCode 11 XOR 保证 INT 输出 ⟺ INT 输入）。
      val reluActive = p.reluOn && isIntFmt(p.cFmt)
      if (!p.requantOn && !reluActive) raw
      else Array.tabulate(p.m, p.n) { (mm, nn) =>
        val v = raw(mm)(nn)
        if (!p.requantOn) { if (reluActive && v < 0.0) 0.0 else v }
        else {
          // requantOn：golden 即量化后输出整数 y = sat_fmt(round_half_up(relu(acc)
          //   × M[nn] >> shift) + zp)。acc 为纯 INT 链精确整数累加器（require 兜底）；
          //   链序：reluOn → 负槽置 0 → ×M[nn]（Long 符号乘）→ shift>0 时 +1<<(shift-1)
          //   后算术右移 1（round-half-up 右移拆解式，shift=0 直通）→ +zp → 饱和
          //   INT8[-128,127]/INT4[-8,7]。此后 compare 走非 MX 分支（absErr>0.5）逐位断言。
          require(v == math.floor(v) && math.abs(v) < (1L << 31).toDouble,
            s"requant golden: 累加器须为 int32 域内精确整数（纯 INT 输入链），C($mm,$nn)=$v")
          var acc = v.toLong
          if (p.reluOn && acc < 0L) acc = 0L
          val prod = acc * rqMVal(nn)
          val q = if (p.rqShift > 0) ((prod >> (p.rqShift - 1)) + 1L) >> 1 else prod
          val qz = q + p.rqZp.toLong
          val lo = if (p.cFmt == DataFormat.INT4) -8L else -128L
          val hi = if (p.cFmt == DataFormat.INT4) 7L else 127L
          (if (qz < lo) lo else if (qz > hi) hi else qz).toDouble
        }
      }
    }

    // 独立期望码链：张量语义 → OCP MX 规范 → 期望 E8M0 scale + 期望元素码。
    //   FTZ 判据不用幅值阈值启发式（|ref| < minNormal(cFmt)·2^(s-127)）：该式在不产出
    //   次正规的格式（E4M3/E5M2/FP16/BF16）上精确，但在 E2M1 上错 4 倍 —— E2M1
    //   minNormal=1.0，而 OCP 把 (e=0,m=1)=±0.5 认定为一等公民，真正的归零区是模式相关的
    //   （RNE y'<=0.25 / RTZ y'<0.5 / RMM y'<0.25 / RUP-RDN 带符号只有严格零）。故改由
    //   独立算出的期望码决定：期望码本身是保号零时才走 bit-exact 断言。期望 scale 由 OCP
    //   amax 规则从期望 C 行独立算出（复用 swQuantBlock），不读任何硬件写回值。

    /** 累加精确性判定（纯张量语义）：fp64 顺序累加的每一个乘积与每一个部分和是否都能被
      * fp32 无损表示。若全程精确，则任何累加顺序、任何结合性、任何 >=32 位的中间精度都
      * 给出同一个数 ⟹ 金锚值与硬件 fp40 累加树的值必然相同 ⟹ 该元素的期望码可做逐位断言
      * （Tier A）；否则金锚与硬件的差是合法的累加舍入差，退回容差比对（Tier B）。
      * fp4(E2M1) 输入值集 ±{0,0.5,...,6} ⟹ 乘积是 0.25 的整数倍、|和| <= 64*36，仅需
      *   14 bit ⟹ 全程 fp32 精确 ⟹ Tier A 覆盖全部元素。
      * E4M3×E5M2 值域达 448×57344 ⟹ 部分和一般不精确 ⟹ Tier B。
      * biasOn 时累加链起点换成 biasVal(nn)，初值也须 fp32 精确（激励栅格已保证）。 */
    def goldenExact(mm: Int, nn: Int): Boolean = {
      def fp32Exact(v: Double): Boolean = v.toFloat.toDouble == v
      var acc = if (p.biasOn) biasVal(nn) else 0.0
      var k = 0
      var ok = true
      if (p.biasOn && !fp32Exact(acc)) ok = false
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

    // FpAddNarrow 精度契约①的绝对界判据（compare 的相消域兜底）。
    // 累加器 FpAddNarrow（useNarrowAdd 默认 true）的精度契约只有三段：
    //   ① |diff| <= (n_lossy + 2^k - 1) x 2^(E-126-W)    —— 无条件，含相消域
    //   ② relErr <= 2^-(W-1)                             —— 仅良态域（|exact| 足够大）
    //   ③ 相消域（catastrophic cancellation）任意有限 W 都没有相对界，只能由①兜底
    // compare 的 tolAbs=1e-6 比本累加器自身的绝对误差地板低约 2.5 个数量级（BF16 K=64
    //   全矩阵 maxAbsErr ~3.7e-4）⟹ 承担不了契约①的兜底，相消域元素（|ref| 远小于累加
    //   规模）会以「绝对误差正常、相对误差被条件数放大」的形态变红。
    // 判据推导（纯张量语义）：
    //   · 累加规模上界 S(mm,nn) = |bias(nn)| + Σ_k |aVal x bVal|，由三角不等式是任何累加
    //     顺序下部分和幅值的上界，与累加树形状、lazy-40 的 expField 推进方式无关。契约里
    //     2^(E-126) 是 lazy-40 窗口锚定的最大幅值，必有 2^(E-126) <= 2^ceil(log2 S)。
    //   · 常数 (n_lossy + 2^k - 1)：n_lossy <= 5（C/L0/L1/L2/L3）、k <= 3 ⟹ 5 + 8 - 1 = 12。
    //   · 窗口宽度取 FpAddNarrow.DefaultW（不写字面量，改默认 W 时判据自动跟随）。
    // 严格性：bound ≈ S x 12 x 2^-19 ≈ S x 2.3e-5。良态元素 bound/|ref| ≈ 2.3e-5，比
    //   tolRel(1e-2) 严 400 倍 ⟹ 良态域判定逐元素不变；真实布线/格式 bug 的误差是 O(S)，
    //   照样红；唯一被它改变判定的是 |ref| << S 的相消域 —— 即契约③声明无相对界的那个域，
    //   故本项数学上自限于相消域。
    // 边界：仅用于非 MX、非 INT 的浮点输出判据。INT 输出走精确整数域（absErr > 0.5 逐位
    //   判）、无窗口损失，不得被本项放宽；MX 量化输出另有独立期望码分层（Tier A / Tier B）。
    /** 该输出元素的累加规模上界 S = |bias| + Σ_k |a x b|（纯张量量，任何累加顺序的部分和上界）。 */
    def accumScaleBound(mm: Int, nn: Int): Double = {
      var s = if (p.biasOn) math.abs(biasVal(nn)) else 0.0
      var k = 0
      while (k < p.k) { s += math.abs(aVal(mm, k) * bVal(k, nn)); k += 1 }
      s
    }

    lazy val accumScaleC: Array[Array[Double]] = Array.tabulate(p.m, p.n)(accumScaleBound)

    /** 契约①绝对界：(n_lossy + 2^k - 1) x 窗口LSB，窗口LSB = 2^ceil(log2 S) x 2^-W。 */
    def narrowAbsBound(mm: Int, nn: Int): Double = {
      val s = accumScaleC(mm)(nn)
      if (!(s > 0.0)) 0.0
      else WbDebugModel.NarrowLossSlack * math.pow(2.0, math.ceil(math.log(s) / math.log(2.0))) *
        math.pow(2.0, -FpAddNarrow.DefaultW.toDouble)
    }

    /** 金锚 fp64 值 → fp32 bits → 40→32 规格化（与硬件两级舍入链同链）。 */
    def goldenBits(mm: Int, nn: Int): Long = {
      val fp32Bits = java.lang.Float.floatToRawIntBits(goldenC(mm)(nn).toFloat) & 0xFFFFFFFFL
      WbSimCommon.swFpNormalize40to32(WbSimCommon.fp32BitsTo40(fp32Bits))
    }

    lazy val goldenCBits: Array[Array[Long]] = Array.tabulate(p.m, p.n)(goldenBits)

    /** 独立期望量化行：OCP amax 块 scale + 逐元素期望码（含 E2M1 次正规）。
      * nt = n-tile 序号；列 >= nEffTile 为 padding，不进 amax、码输出 0（swQuantBlock 内建）。 */
    def expQuantRow(nt: Int, mm: Int): (Seq[Int], Seq[Int]) = {
      val nEffTile = math.min(totalN, p.n - nt * totalN)
      val row = (0 until totalN).map { lc =>
        val gc = nt * totalN + lc
        if (gc < p.n) goldenCBits(mm)(gc) else 0L
      }
      WbSimCommon.swQuantBlock(row, p.rnd, p.cFmt, p.cMxBlkSel, nEffTile)
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

    /** MX 侧模式相关归零区解析判据 —— 由 OCP 网格与 swRoundUp 的五模式定义推出，是
      * expCode 判零的第二条独立路径，两者逐元素交叉自证。
      *
      * 设 y' = |ref| / 2^(s-127)。因 y' = 1.f × 2^(eTgt-bias)，E2M1(bias=1) 的次正规区
      * 覆盖 eTgt ∈ {0,-1} ⟹ y' ∈ [0.25, 1.0)，网格 {0, 0.5, 1.0}：
      *   eTgt=-1 → y' ∈ [0.25,0.5)：keep=0, guard=1(隐含位), sticky=(f!=0), lsb=0
      *   eTgt= 0 → y' ∈ [0.5,1.0) ：keep=1, guard=f 的最高位, sticky=其余, lsb=1
      * 逐模式代入 swRoundUp：
      *   RNE(0) guard&&(sticky||lsb)：eTgt=-1 时 up=sticky ⟹ y'=0.25(tie) up=0 → 0（偶）；
      *            y'∈(0.25,0.5) up=1 → 0.5 ⟹ 归零区 y' <= 0.25
      *   RTZ(1) 恒 false：eTgt=-1 → keep=0 → 0 ⟹ 归零区 y' < 0.5
      *   RDN(2) neg&&(guard||sticky)：负数 eTgt=-1 → up=1 → -0.5，tie 点 0.25 也进位
      *            ⟹ 负数归零区只有严格零；正数 up=0 → keep=0 ⟹ 正数归零区 y' < 0.5
      *   RUP(3) !neg&&(guard||sticky)：与 RDN 符号对偶 ⟹ 正数只有严格零；负数 y' < 0.5
      *   RMM(4) guard：eTgt=-1 → up=1（不论 sticky）→ 0.5，tie 点 0.25 也进位 ⟹ y' < 0.25
      * 深下溢（eTgt <= -2 ⟹ y' < 0.25）下 RDN/RUP 的「只有严格零」依赖定向臂：
      *   roundTowardNegative/Positive 取不超过/不低于 x 的最接近可表示值，不是幅值截断，
      *   故对任意非零 x，定向把幅值推离零时结果必须是最小非零可表示值 ±0.5。
      *   RNE/RTZ/RMM 幅值对称 ⟹ y'<0.25 归零，定向臂不介入。
      * E4M3/E5M2（emitSub=false）：underflow 由 eTgt<1 在舍入之前判定 ⟹ 归零区
      *   y' < minNormal(fmt) = 2^(1-bias)，与舍入模式无关。 */
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

    // 内存镜像构建
    def buildMemory(): ByteMemory = {
      val mem = new ByteMemory
      // A: row-major M×K。字节对齐族（aEbits>=8）走整字节 storeElem；亚字节族（fp4，
      //   aEbits=4）走 nibble 打包 storeSubByte（byte=k/2、nibble=(k%2)*4）。
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
      // bias 向量镜像：nTiles*totalN 个 4B 字，地址 = biasBase + i*4（i = nt*totalN + lane）。
      if (p.biasOn)
        for (i <- 0 until nTiles * totalN)
          mem.storeElem(p.biasBase + i.toLong * 4, biasCode(i), 4)
      // rqM 向量镜像：地址 = rqMBase + i*4；负 M 以 int32 二补码低 32 位落盘。
      if (p.requantOn)
        for (i <- 0 until nTiles * totalN)
          mem.storeElem(p.rqMBase + i.toLong * 4, rqMCode(i), 4)
      mem
    }

    /** 按地址取 A/B beat（内存保真，绝不按 beat 序号发数） */
    def beatAtA(addr: Long): BigInt = mem.loadBits(addr, cfg.aExtW)
    def beatAtB(addr: Long): BigInt = mem.loadBits(addr, cfg.bExtW)
    // scale 通道读访问器：addr 处的 32-bit 小端字（scaleAW = scaleBW =
    //   totalN*4/minBlockSize*8 = 32 bit）。Executor 按 idx = (lane*elemsPerLane)/blk 选
    //   一字节：payload >> (idx*8) & 0xFF。blk >= kPerBeat 时 idx 恒 0 只消费 payload[7:0]；
    //   blk < kPerBeat（fp8 blk=16 → 2；fp4 blk=16 → 4、blk=32 → 2）时高字节槽也被消费。
    //   地址语义：scale DMA 发字节地址 base + row*(K/blk) + kt*(kPerBeat/blk)，外部存储答
    //   32 位；小端 ⟹ 字节槽 j 承载该行的 scale 块 kt*(kPerBeat/blk) + j，正是 lane 组
    //   idx = j 需要的那一块。高字节可能越过 scale 行末（只有前 numScalesPerBeat 字节被
    //   选中），loadByte 对未写地址返回 0，故定义良好。
    def wordAt(addr: Long): BigInt = mem.loadBits(addr, 32)

    private var mem: ByteMemory = new ByteMemory
    def prepare(): ByteMemory = { mem = buildMemory(); mem }
    /** 已镜像的有效字节数（prepare 之后调用） */
    def memByteCount: Int = mem.writtenBytes

    // 结果解码与对比

    /** MX 量化输出的反量化（码 + 每 8 元素组 1 字节 E8M0 scale） */
    def dequant(code: Int, scale: Int): Double = decodeElem(p.cFmt, code.toLong, scale)

    final case class CmpSummary(elems: Int, mismatch: Int, laneViolations: Int,
                                maxRelErr: Double, maxAbsErr: Double, details: Seq[String])

    /** 硬件写回日志 → 逐元素与 golden 对比（数值容差；INT 输出走近似精确判据）。
      *
      * FTZ 感知 bit-exact：当 `ref` 非零且落在输出格式的 subnormal 区
      * （|ref| < minNormal(cFmt)，MX 量化在反量化域再乘块 scale 2^(s-127)），硬件
      * flush-to-zero。此时对 hw 原始位做 bit-exact 断言 —— 必须恰为保号零码
      * （ftzSignedZeroCode）。不能令 refEff=0.0 再走容差判据：refEff=0 ⟹ relErr 退化为
      * absErr ⟹ fail=(absErr>tolRel)&&(absErr>tolAbs)，于是 hw 在 FTZ 区给出小非零值或
      * 符号错误（-0.0 vs +0，absErr=0）都会被静默通过，形成屏蔽窗口。
      *
      * MX 侧判据用独立期望码，而非幅值阈值启发式 `|ref| < minNormal(cFmt)·2^(s-127)`：
      * 后者对不产出次正规的格式成立（E4M3/E5M2/FP16/BF16 的 underflow 由 eTgt<1 在舍入
      * 之前判定，与舍入模式无关），但对 E2M1 错 4 倍：OCP MX 把 (e=0,m=1)=±0.5 定义为
      * 一等公民，E2M1 的真实归零区是模式相关的（见 analyticZeroRegionMx）。故：
      *   ① MX 侧判据 = expCodeAt（张量语义 + OCP amax 块 scale + swQuantElem 独立算出，
      *      不读任何硬件写回值）——期望码本身是保号零时才走 bit-exact 断言；
      *   ② 累加全程 fp32 精确的元素（goldenCExact）走期望码逐位精确比对（Tier A），
      *      不精确的元素退回容差比对（Tier B）；
      *   ③ 双路交叉自证：期望码判零 与 模式相关解析归零区 必须逐元素一致，不一致即报
      *      ZERO-CRITERION-CONTRADICTION；
      *   ④ 幅值阈值启发式命中数仍统计但不参与判定，仅供口径对照；
      *   ⑤ 打印全码（8 幅值码 × 符号）发射计数表与 hw/exp/输入侧各自的未覆盖码集。 */
    def compare(cWriteLog: Seq[WbSimCommon.CWriteBeat],
                scaleWriteLog: Seq[(Long, BigInt)]): CmpSummary = {
      // strb 逐字节校验：解析走 Checked 变体，同时跑 lane 级审计（addr↔strb 双向对账、
      //   未选通 lane 零填充核查、BEAT-COUNT），违规逐条折入 mismatch 与 details。
      //   期望拍数取金锚 expCAddrSeq.size（张量语义侧推导），不取硬件实收拍数。
      val parsed = WbSimCommon.parseDenseCWriteLogChecked(
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
      // mismatch 只计元素级失配，laneViolations 单列（契约消息数与元素数不同量纲）。
      val laneViolations = parsed.violationCount
      var mismatch = 0
      var maxRel = 0.0
      var maxAbs = 0.0
      var ftzTakenOver = 0   // 被「期望码=保号零」bit-exact 分支接管的元素计数
      var tierA = 0          // 期望码逐位精确比对（累加全程 fp32 精确）
      var tierB = 0          // 容差比对（累加不精确 ⟹ 金锚与硬件之差是合法舍入差）
      var tierBZero = 0      // Tier B 中走保号零 bit-exact 的元素数
      var legacyHeurHits = 0 // 幅值阈值启发式命中数：仅供口径对照，不参与任何判定
      var crossCheckBad = 0  // 期望码判零 vs 模式相关解析判零 的不一致数（内部矛盾探针）
      // 两套对照口径的误差统计。主口径 maxRel/maxAbs 覆盖全部元素（含归零元素，故 E2M1
      //   会取到 1.0 —— 期望码为 ±0 而 ref≠0 时 relErr 恒为 1.0）。另给：① legacy 口径 =
      //   幅值阈值排除集（|ref| < minNormal·2^(hwScale-127) 不进统计），界定差异只发生在
      //   y'<1.0 区；② nz 口径 = 只统计期望码非零的元素 ⟹ 值域内真实最坏量化误差。
      var maxRelLegacy = 0.0
      var maxAbsLegacy = 0.0
      var maxRelNZ = 0.0
      var maxAbsNZ = 0.0
      // E2M1 边界覆盖计数器：把每个元素的 y' = |ref| / 2^(expScale-127) 归入 OCP 网格的
      //   关键点/关键带。y' 用精确 == 比较：ref 是 0.25 的整数倍（fp4 乘积和，fp64 下
      //   精确），除以 2 的整数幂在 IEEE-754 下无舍入。
      var bndZero = 0; var bndOp0_025 = 0; var bndP025 = 0; var bndOp025_05 = 0; var bndP050 = 0
      var bndOp05_075 = 0; var bndP075 = 0; var bndOp075_1 = 0
      var bndOp1_125 = 0; var bndP125 = 0; var bndOther = 0; var bndP100 = 0
      // 点级记录容器：key = 网格关键点/开区间标签，value = 去重后的 (符号, y', hw 码,
      //   期望码) 记录集合。仅 MXE2M1 填充。
      val bndRec = mutable.LinkedHashMap.empty[String, mutable.LinkedHashSet[String]]
      val tolRel = if (quant) 0.5 else if (p.cFmt == DataFormat.BF16) 2e-2 else 1e-2
      val tolAbs = if (quant) 1e-3 else 1e-6
      // 非 MX 浮点格式的 FTZ 阈值（真实值域，与 ref 同域）；INT 格式无 subnormal，不触发。
      //   FP16/BF16 走 fp32ToFp16Rnd / fp32ToBf16Rnd 独立通路，对 |x| < minNormal 一律 FTZ
      //   保号零、不做「舍入到次正规」⟹ 归零区就是 y' < minNormal 且与舍入模式无关。
      val ftzThreshFlat: Double = if (isIntFmt(p.cFmt)) 0.0 else minNormal(p.cFmt)
      // 元素码位掩码（位宽由 cOutElemBits 推导；FTZ bit-exact 断言取 hw 原始位用）
      val elemMask: Long = (1L << WbSimCommon.cOutElemBits(p.cFmt)) - 1
      // 全码发射计数所需的位宽/幅值域掩码
      val elemBits: Int = WbSimCommon.cOutElemBits(p.cFmt)
      val magMask: Long = (1L << (elemBits - 1)) - 1
      // elemBits>=32（FP32/INT32 输出）守护：Scala Int 移位按 mod-32，`1 << 32` 回绕成 1，
      //   且 2^32 项直方图内存上不可行。直方图只在下方 `if (quant)` 报告块里被读
      //   （MX 格式 elemBits ∈ {4,8}），故宽格式跳过统计不影响逐元素判定。
      val histEnabled: Boolean = elemBits < 32
      val nCodes: Int = if (histEnabled) 1 << elemBits else 0
      val hwHist  = Array.fill(nCodes)(0)   // 硬件实发码计数
      val expHist = Array.fill(nCodes)(0)   // 金锚期望码计数（仅 MX 有意义）
      for (mm <- 0 until p.m; nn <- 0 until p.n) {
        val ref = goldenC(mm)(nn)
        // MX 量化输出：本元素所属块的 E8M0 scale 字节（仅用于反量化与旧启发式对照，判定
        //   换成独立算出的 expScale/expCode）
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
        // 误差统计在所有分支累积（主口径），另按 legacy / nz 两套对照口径分别累积
        val hwCode: Long = hwBits(mm)(nn) & elemMask
        if (histEnabled) hwHist(hwCode.toInt) += 1
        val absErr = math.abs(hw - ref)
        val relErr = if (math.abs(ref) > 1e-9) absErr / math.abs(ref) else absErr
        if (relErr > maxRel) maxRel = relErr
        if (absErr > maxAbs) maxAbs = absErr
        // legacy 命中集 = 幅值阈值口径排除在误差统计外的元素集合：
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
          // MX 输出：判据 = 金锚独立算出的期望码
          val eCode  = expCodeAt(mm, nn)
          val eScale = expScaleAt(mm, nn)
          expHist(eCode) += 1
          val expIsZero = (eCode.toLong & magMask) == 0L
          val expNeg    = ((eCode >>> (elemBits - 1)) & 1) == 1
          if (!expIsZero) {
            if (relErr > maxRelNZ) maxRelNZ = relErr
            if (absErr > maxAbsNZ) maxAbsNZ = absErr
          }
          // E2M1 定向边界覆盖分类（y' = |ref|/2^(expScale-127)，精确比较）
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
            // 点级记录：收集每个网格关键点与每个开区间上实测到的 (y', 符号, hw 码, 期望码)。
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
              // 开区间上 y' 取值很多，只保留 (符号,hw,exp) 去重集合；关键点上 y' 唯一，保留完整记录。
              st += (if (k.startsWith("band")) rec.replaceFirst("yp=[^ ]+ ", "") else rec)
            }
          }
          // 双路交叉自证：①期望码幅值域是否为 0 ②OCP 网格 + 五模式解析归零区，两条独立
          //   路径必须逐元素一致。解析路径的符号实参取 ref < 0.0（fp64 参考值符号）而非
          //   expNeg：喂 expNeg 会使两路径共享符号来源、金锚符号算错时同错同绿。selfZero
          //   仍用 expNeg —— 它比的是「期望码 == 保号零码」这条金锚内部自洽性。
          val anaZero = analyticZeroRegionMx(math.abs(ref), eScale, p.rnd, ref < 0.0)
          val selfZero = !expIsZero || eCode.toLong == ftzSignedZeroCode(p.cFmt, expNeg)
          if (anaZero != expIsZero || !selfZero) {
            crossCheckBad += 1
            // 矛盾即失败：纯 hw-vs-golden 比对看不出共模错误（hw 与 exp 直方图可逐码相同），
            //   只有这条独立解析路径能暴露。
            mismatch += 1
            if (details.size < 32)
              details += f"C($mm,$nn) ZERO-CRITERION-CONTRADICTION expCode=0x$eCode%X " +
                f"isZero=$expIsZero analytic=$anaZero selfConsistent=$selfZero " +
                f"ref=$ref%.6g expScale=$eScale hwScale=$mxScale rnd=${p.rnd}"
          }
          if (goldenCExact(mm)(nn)) {
            // Tier A：累加全程 fp32 精确 ⟹ 期望码可做逐位断言
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
            // Tier B：累加不精确 ⟹ 退回容差；但归零判定仍由期望码给出，不用幅值阈值。
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
              // quant ⟹ !isIntFmt(p.cFmt)，故恒走浮点容差臂
              val fail = relErr > tolRel && absErr > tolAbs
              if (fail) {
                mismatch += 1
                if (details.size < 32)
                  details += f"C($mm,$nn) hw=0x${hwBits(mm)(nn)}%X($hw%.6g) ref=$ref%.6g absErr=$absErr%.3e relErr=$relErr%.3e"
              }
            }
          }
        } else {
          // 非 MX 输出（FP32/FP16/BF16/INT）。ftzHit 与 legacyHit 同式（共用同一命中集）。
          val ftzHit = legacyHit
          if (ftzHit) {
            ftzTakenOver += 1
            val expCode = ftzSignedZeroCode(p.cFmt, ref < 0.0)
            if (hwCode != expCode) {
              mismatch += 1
              if (details.size < 32)
                details += f"C($mm,$nn) FTZ-bitexact-FAIL hw=0x${hwCode}%X exp=0x${expCode}%X(signed-zero) ref=$ref%.6g ftzThresh=$ftzThreshFlat%.3e"
            }
          } else {
            // 浮点输出判据 = 相对界 AND 绝对地板 AND FpAddNarrow 契约①绝对界。第三项是
            //   相消域兜底（契约②的相对界只在良态域成立）。bound ≈ S x 2.3e-5 ⟹ 良态域下
            //   比 tolRel 严 400 倍、永不是决定票，只有 |ref| << S 的相消域元素会被它改判。
            //   INT 输出不走本项：精确整数域无窗口损失，仍是 absErr > 0.5 逐位判。
            val narrowBound = if (isIntFmt(p.cFmt)) 0.0 else narrowAbsBound(mm, nn)
            val fail =
              if (isIntFmt(p.cFmt)) absErr > 0.5
              else (relErr > tolRel && absErr > tolAbs && absErr > narrowBound)
            if (fail) {
              mismatch += 1
              if (details.size < 32)
                details += f"C($mm,$nn) hw=0x${hwBits(mm)(nn)}%X($hw%.6g) ref=$ref%.6g absErr=$absErr%.3e relErr=$relErr%.3e narrowBound=$narrowBound%.3e accScale=${accumScaleC(mm)(nn)}%.6g"
            }
          }
        }
      }
      // C 侧块 scale 字节值硬断言 —— 逐块比对，任何一块不符即红。
      //   不比 scale 值时，scale 通路只要自洽（硬件写的 scale 与硬件写的码互相匹配）就能过，
      //   哪怕整条 amax→E8M0 编码链算错也看不出来。
      // 期望值来源（不读硬件）：张量语义 aVal/bVal（decodeInElem + effScaleA/B）→ fp64 顺序
      //   累加 goldenC → fp32 → swFpNormalize40to32（与硬件两级舍入链同链）→ swQuantBlock
      //   （按声明块大小分组求 amax，padding 列掩 0 不进 amax）→ swBlockScale（OCP E8M0 规则
      //   s = eEff + bias - maxCodeExp，钳到 [1,254]，amax==0 → 哨兵 0，amax==inf → 254）。
      // beat 内字节数与「字节 ↔ 块」映射由 C-scale 内存布局契约确定：scale 地址序列 =
      //   SC_BASE + nt*colOff + mm*rowOff，其中 colOff = totalN / cMxBlk（每拍前进字节数
      //   = 本 tile 内的块数）、rowOff = ceil(n / cMxBlk)（每行字节数 = 全行块数）⟹ 一个
      //   beat 承载 colOff = totalN/cMxBlk 个 scale 字节，第 j 字节 ↔ 本 tile 内第 j 个
      //   cMxBlk 元素块。swQuantBlock 的「每 8 元素 1 字节」是金锚内部粒度，块 j 对应其第
      //   j*(cMxBlk/8) 个字节；二者必须一致，故下面额外断言「同一块内所有 8 元素组的金锚
      //   scale 字节相等」。
      //   宽度对照：WbOutCvt.scaleWire 按每 8 元素 1 字节生成 totalN/8 字节，而 io.scaleOut
      //   位宽 = cScaleRowW = (totalN/minBlockSize)*8 = totalN/16 字节（minBlockSize=16）⟹
      //   cMxBlk=16 时两者相等无截断；cMxBlk=32 时物理 beat 宽度(totalN/16) 是契约前进量
      //   (totalN/32) 的 2 倍，多出的高字节会落进下一 tile 的地址窗 —— 当前 cfg 族 totalN
      //   恒为 16 ⟹ cMxBlk=32 被 require(cMxBlk <= totalN) 拦掉、不可达。两个宽度都打印。
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
      // FTZ 屏蔽面可观测性
      println(s"[$TAG] FTZ-bitexact 接管计数: cFmt=${p.cFmt} 命中=$ftzTakenOver / 总元素=${p.m * p.n}" +
        s"（ftzThreshFlat=${"%.3e".format(ftzThreshFlat)}${if (quant) "；MX 侧按块 scale ×2^(s-127)" else ""}）")
      if (quant) {
        // 判定分层与双路交叉自证结果
        println(s"[$TAG] (#151 F4) 判定分层: cFmt=${p.cFmt} rnd=${p.rnd} cMxBlk=${p.cMxBlk} " +
          s"TierA(期望码逐位)=${tierA} TierB(容差)=${tierB}（其中保号零 bit-exact=${tierBZero}） " +
          s"归零判据命中=${ftzTakenOver} 旧幅值阈值启发式命中=${legacyHeurHits}（仅对照，不判定） " +
          s"双路交叉自证不一致=${crossCheckBad}")
        // C 侧 scale 字节值硬断言汇总。期望值来自 swQuantBlock/_2（OCP amax 规则独立算出），
        //   实收值来自 scaleWriteLog 的 beat 位切片；任何一字节不符都已 mismatch += 1。
        println(s"[$TAG] (#151 F3) C-scale 硬断言: beat 期望=$scaleExpBeats 实收=${scaleWriteLog.size}" +
          s"（结构不符=$scaleBeatCountBad） beat 内字节 契约=$scaleBytesPerBeat 物理=$scalePhysBytesPerBeat" +
          s" 逐块比对=$scaleBlkChecked 值不符=$scaleBad 块内金锚不一致=$scaleBlkInconsist")
        // scale 字节直方图：只列非零项（256 桶太宽）。exp 与 hw 两侧必须逐桶相同。
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
        // 三套误差口径：主口径含归零元素（E2M1 期望码=±0 而 ref≠0 ⟹ relErr=1.0）；
        //   legacy 口径为幅值阈值排除集；nz 口径只统计期望码非零的元素。
        println(f"[$TAG] (#151 F4) 误差口径: 主(全元素) maxRel=$maxRel%.3e maxAbs=$maxAbs%.3e | " +
          f"legacy(#129 排除集, 命中=$legacyHeurHits) maxRel=$maxRelLegacy%.3e maxAbs=$maxAbsLegacy%.3e | " +
          f"nz(期望码非零) maxRel=$maxRelNZ%.3e maxAbs=$maxAbsNZ%.3e")
        // E2M1 定向边界覆盖（y' = |ref|/2^(expScale-127) 的关键点/关键带命中数）。
        //   计数为 0 的点即未被本次随机激励打到。
        if (p.cFmt == DataFormat.MXE2M1)
          println(s"[$TAG] (#151 F4) E2M1 边界覆盖: y'=0:$bndZero  (0,0.25):$bndOp0_025  =0.25:$bndP025  (0.25,0.5):$bndOp025_05" +
            s"  =0.5:$bndP050  (0.5,0.75):$bndOp05_075  =0.75:$bndP075  (0.75,1.0):$bndOp075_1" +
            s"  =1.0:$bndP100  (1.0,1.25):$bndOp1_125  =1.25:$bndP125  其它:$bndOther")
        // 定向边界值验证表原始数据（hw 实测 vs 金锚期望码），按网格升序打印；
        //   每个舍入模式各跑一次即得该模式整列。order 里的 y'=0.3 必然落到「未被激励」：
        //   E2M1 激励下 ref = Σ a·b 是 0.25 的整数倍、块 scale 是 2 的整数幂 ⟹ y' 必为
        //   二进有理数，而 0.3 = 3/10 非 dyadic ⟹ 精确 y'=0.3 在 fp4 激励下不可达。
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
        // 全码发射计数表：elemBits=4 时 16 码全列；elemBits=8 时只列非零项
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
        // 输入侧码覆盖率：输入域 E2M1 的 ±0.5 由 decodeMxE2M1Input（subnormalFtz=false）
        //   支持，这里列出实测覆盖以界定未覆盖码集。
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
        if (isMxAny(p.aFmt) || isFp8Fmt(p.aFmt)) {
          val ha = inHist(aEbits, p.m, p.k, (i, j) => aCode(i)(j))
          println(s"[$TAG]   ${inHistLine("in A", aEbits, ha)}")
        }
        if (isMxAny(p.bFmt) || isFp8Fmt(p.bFmt)) {
          val hb = inHist(bEbits, p.k, p.n, (i, j) => bCode(i)(j))
          println(s"[$TAG]   ${inHistLine("in B", bEbits, hb)}")
        }
      }
      CmpSummary(p.m * p.n, mismatch, laneViolations, maxRel, maxAbs, details.toSeq)
    }

    /** 打印 hw/ref 前若干行，便于人工定位 */
    def printRows(cWriteLog: Seq[WbSimCommon.CWriteBeat], rows: Int): Unit = {
      val hwBits = WbSimCommon.parseDenseCWriteLog(cWriteLog, p.cFmt, p.m, p.n, totalN, cDmaExtW)
      for (mm <- 0 until math.min(p.m, rows)) {
        val hwStr = (0 until math.min(p.n, 16)).map { nn =>
          val v = if (isIntFmt(p.cFmt)) sext(hwBits(mm)(nn), WbSimCommon.cOutElemBits(p.cFmt)).toString
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

  // 波形定位（对 test-name mangle 规则鲁棒：先按 hint 过滤，否则取最新）

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

  /** 转存波形；源缺失时仅打印 NOTE */
  def copyWave(suite: String, hint: String, target: String): Unit = {
    findTrace(suite, hint) match {
      case Some(src) =>
        val t = Paths.get(target)
        if (t.getParent != null) Files.createDirectories(t.getParent)
        WbSimCommon.copyTrace(src, target, TAG)
      case None =>
        println(s"[$TAG] NOTE: 未在 build/chiselsim/$suite 下找到 trace.fst（请加 -DemitFst=1 运行）")
    }
  }
}
