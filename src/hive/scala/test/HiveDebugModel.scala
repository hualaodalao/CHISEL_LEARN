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
  *             共用唯一推演源，与 HiveCoreDmaWrOnly 三角累积闭式同源）
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

  /** 外部内存中每元素字节数（半字节族返回 0，调用方需拒绝） */
  def memElemBytes(f: DataFormat.Type): Int =
    if (f == DataFormat.FP32 || f == DataFormat.INT32) 4
    else if (f == DataFormat.FP16 || f == DataFormat.BF16 || f == DataFormat.INT16) 2
    else if (f == DataFormat.INT8 || isMx8(f)) 1
    else 0   // MXE2M1 / INT4：半字节，本调试模型不作为输入格式支持

  /** 每 16-bit lane 打包的元素数：MXFP8 SIMD 双元素/拍 = 2，其余 = 1 */
  def elemsPerLane(f: DataFormat.Type): Int = if (isMx8(f)) 2 else 1

  /** 每拍推进的 K 元素数（= kTile 粒度）：MX8 = totalN*2，其余 = totalN */
  def kPerBeat(f: DataFormat.Type, totalN: Int): Int = totalN * elemsPerLane(f)

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

    /** 控制字 regs(7)：rnd(13,12) | cFmt(11,8) | bFmt(7,4) | aFmt(3,0) */
    def controlWord7: Long =
      ((rnd.toLong & 0x3) << 12) | ((cFmt.litValue.toLong & 0xF) << 8) |
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

  /** 返回 Some(errCode) 表示硬件会在 EXECUTE 拍直接置 config-err（不进计算） */
  def precheckConfigErr(p: DebugParams, totalN: Int): Option[Int] = {
    val aMx = isMxAny(p.aFmt)
    val bMx = isMxAny(p.bFmt)
    val aKLoad = if (isMx8(p.aFmt)) totalN * 2 else totalN * 4
    val bKLoad = if (isMx8(p.bFmt)) totalN * 2 else totalN * 4
    if (aMx && p.aMxBlk > aKLoad) Some(0)
    else if (bMx && p.bMxBlk > bKLoad) Some(1)
    else if (isMxAny(p.cFmt) && p.cMxBlk > totalN) Some(2)
    else if (aMx != bMx) Some(3)
    else if ((aMx || bMx) && !p.loadWMode) Some(4)
    else if (p.aFmt == DataFormat.FP32 || p.bFmt == DataFormat.FP32) Some(5)
    else None
  }

  /** 运行前防呆：不支持的组合给清晰报错（harness 不得崩溃，故在 simulate 之前抛） */
  def validate(p: DebugParams, totalN: Int): Unit = {
    require(p.m > 0 && p.n > 0 && p.k > 0, s"M/N/K 必须 > 0，当前 M=${p.m} N=${p.n} K=${p.k}")
    require(memElemBytes(p.aFmt) > 0,
      s"aFmt=${p.aFmt} 为半字节格式，调试模型不支持作为输入（请改用 mxe4m3/mxe5m2/fp16/bf16/int8/int16）")
    require(memElemBytes(p.bFmt) > 0,
      s"bFmt=${p.bFmt} 为半字节格式，调试模型不支持作为输入（请改用 mxe4m3/mxe5m2/fp16/bf16/int8/int16）")
    require(p.rnd >= 0 && p.rnd <= 4, s"rnd=${p.rnd} 越界（0..4）")
    if (isMxAny(p.aFmt) || isMxAny(p.bFmt)) {
      require(p.k % (totalN * 2) == 0,
        s"MX 运行要求 K(${p.k}) 能被 kPerBeat=${totalN * 2} 整除（一个 kTile = 一个 32-K scale 块 = 单 pass）")
      require(p.loadWMode, "MX 运行必须 loadWMode=1（B 转置存储，硬件 errCode 4 拦截）")
    }
    if (isMxAny(p.aFmt)) require(p.k % p.aMxBlk == 0, s"K(${p.k}) 必须被 aMxBlk(${p.aMxBlk}) 整除")
    if (isMxAny(p.bFmt)) require(p.k % p.bMxBlk == 0, s"K(${p.k}) 必须被 bMxBlk(${p.bMxBlk}) 整除")
    if (isMxAny(p.cFmt)) {
      require(p.cMxBlk <= totalN && totalN % p.cMxBlk == 0,
        s"cMxBlk(${p.cMxBlk}) 必须 <= totalN($totalN) 且整除")
      if (p.cFmt == DataFormat.MXE2M1) require(p.n % 2 == 0, s"mxfp4 输出要求 N(${p.n}) 为偶数")
    }
    require(p.addrModeSel == 0,
      "addrMode=TILE 当前无硬件消费者（regs(6) bits(14,12) 已写入但 RTL 未接线），" +
        "调试模型仅支持 MAT；如需 TILE 请先在 HiveCoreCalcConfig 落地该分支")
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
      val c = (bits & 0xFL).toInt
      val sg = (c >> 3) & 1
      val e = (c >> 1) & 0x3
      val mm = c & 1
      val v = if (e == 0 || scaleByte == 0) 0.0
              else (1.0 + mm.toDouble / 2) * math.pow(2, e - 1) * math.pow(2, scaleByte - 127)
      if (sg == 1) -v else v
    } else throw new IllegalArgumentException(s"decodeElem: 未知格式 $f")

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
    else throw new IllegalArgumentException(s"encodeElem: 格式 $f 不支持作为输入编码")

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

    val aEb: Int = memElemBytes(p.aFmt)
    val bEb: Int = memElemBytes(p.bFmt)
    val aKpb: Int = kPerBeat(p.aFmt, totalN)      // A 每拍 K 元素数
    val bKpb: Int = kPerBeat(p.bFmt, totalN)
    val aKTiles: Int = if (isMxAny(p.aFmt)) p.k / aKpb else (p.k + totalN - 1) / totalN
    val bKTiles: Int = if (isMxAny(p.bFmt)) p.k / bKpb else (p.k + totalN - 1) / totalN
    val nTiles: Int = (p.n + totalN - 1) / totalN

    // ---- 期望地址偏移（张量语义推导，不镜像 HiveCoreCalcConfig 谓词链） ----
    val aRowOff: Long = p.k.toLong * aEb                       // A 行步进
    val aColOff: Long = aKpb.toLong * aEb                      // A kTile 步进
    val bRowOff: Long = (if (p.loadWMode) p.k else p.n).toLong * bEb
    val bNTileOff: Long = if (p.loadWMode) totalN.toLong * bRowOff else totalN.toLong * bEb
    val bKTileOff: Long = if (p.loadWMode) totalN.toLong * bEb else totalN.toLong * bRowOff

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

    def aVal(mm: Int, k: Int): Double = decodeElem(p.aFmt, aCode(mm)(k), effScaleA(mm, k))
    def bVal(k: Int, nn: Int): Double = decodeElem(p.bFmt, bCode(k)(nn), effScaleB(nn, k))

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
      for (mm <- 0 until p.m; k <- 0 until p.k)
        mem.storeElem(A_BASE + mm * aRowOff + k * aEb, aCode(mm)(k), aEb)
      if (p.loadWMode) {   // B 转置：row-major N×K
        for (nn <- 0 until p.n; k <- 0 until p.k)
          mem.storeElem(B_BASE + nn * bRowOff + k * bEb, bCode(k)(nn), bEb)
      } else {             // B 非转置：row-major K×N
        for (k <- 0 until p.k; nn <- 0 until p.n)
          mem.storeElem(B_BASE + k * bRowOff + nn * bEb, bCode(k)(nn), bEb)
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

    /** 硬件写回日志 → 逐元素与 golden 对比（数值容差；INT 输出走近似精确判据） */
    def compare(cWriteLog: Seq[(Long, BigInt)],
                scaleWriteLog: Seq[(Long, BigInt)]): CmpSummary = {
      val hwBits = HiveSimCommon.parseDenseCWriteLog(cWriteLog, p.cFmt, p.m, p.n, totalN, cDmaExtW)
      val quant = isMxAny(p.cFmt)
      val details = mutable.ArrayBuffer.empty[String]
      var mismatch = 0
      var maxRel = 0.0
      var maxAbs = 0.0
      val tolRel = if (quant) 0.5 else if (p.cFmt == DataFormat.BF16) 2e-2 else 1e-2
      val tolAbs = if (quant) 1e-3 else 1e-6
      for (mm <- 0 until p.m; nn <- 0 until p.n) {
        val ref = goldenC(mm)(nn)
        val hw: Double =
          if (quant) {
            val nt = nn / totalN
            val scaleBeat = if (scaleWriteLog.size > nt * p.m + mm) scaleWriteLog(nt * p.m + mm)._2 else BigInt(0)
            val grp = (nn - nt * totalN) / p.cMxBlk
            val s = ((scaleBeat >> (8 * grp)) & 0xFF).toInt
            dequant(hwBits(mm)(nn).toInt, s)
          } else decodeElem(p.cFmt, hwBits(mm)(nn), 127)
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
