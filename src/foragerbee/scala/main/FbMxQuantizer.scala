/** ForagerBee MXFP8 量化器 —— 将 FP32/FP16/BF16 源数据按块量化为
  * MXFP8（E4M3/E5M2）元素 + 每块一个 E8M0 共享 scale。
  *
  * 算法（对每个 block = 连续 blockSize 个源元素）：
  *   阶段 1（sCOLLECT）：逐拍收集源数据存入 block 缓冲，同时逐元素提取
  *     真指数（true_exp = biased_exp - src_bias）做 running-max 归约。
  *     零/次正规/NaN 不贡献 max；inf 视为最大指数（127）参与归约。
  *   阶段 2（sQUANT）：块满时锁存 blockScale = 127 + max_exp（E8M0 编码，
  *     全零块 → scale=0），随后逐元素回读并量化：
  *       new_exp_biased = true_exp - max_exp + dst_bias
  *     尾数截取到目标位宽（E4M3:3-bit / E5M2:2-bit）+ RNE 舍入；
  *     下溢/上溢判定均在 RNE 进位之后（用 newExpF）：下溢 → flush-to-zero；
  *     上溢/inf → 饱和到格式最大正常数；
  *     NaN → E4M3 输出 NaN 编码（0x7F|sign），E5M2 饱和到最大正常数。
  *   阶段 3（sDRAIN）：等待 outData 残余 beat 与 outScale 全部握手完成，
  *     复位内部状态回到收集态。
  *
  * 特殊值语义（与 Fp32.mxToFp32 反量化路径对称）：
  *   - 零 / 次正规输入        → 输出 ±0（次正规先归一化为 fp32 次正规后统一按零处理）
  *   - 下溢（RNE 进位后 new_exp ≤ 0）→ flush-to-zero（保号）；进位后 new_exp=1 则输出最小正规数
  *   - E4M3 最大正常数        → exp=1110, mant=111（448），exp=1111 保留给 NaN
  *   - E5M2 最大正常数        → exp=11110, mant=11（57344）
  *
  * 量化器自己管理输入消耗节奏与输出打包，不使用 FbFormatConverter 的 Rate Adapter。
  *
  * @param dataWidth 数据位宽（bit），必须为 2 的幂且 ≥ 32
  */

import chisel3._
import chisel3.util._

class FbMxQuantizer(dataWidth: Int) extends Module {
  require(dataWidth >= 32 && (dataWidth & (dataWidth - 1)) == 0,
    s"FbMxQuantizer: dataWidth($dataWidth) 必须为 2 的幂且 ≥ 32")

  val io = IO(new Bundle {
    /** 输入：来自 dataQueue 的源数据（FP32/FP16/BF16 元素打包） */
    val in = Flipped(Decoupled(UInt(dataWidth.W)))
    /** 输出1：量化后的 FP8 数据（打包为 dataWidth 宽） */
    val outData = Decoupled(UInt(dataWidth.W))
    /** 输出2：E8M0 scale 字节（每 block 一个） */
    val outScale = Decoupled(UInt(8.W))
    /** 配置：源数据格式（[[FbDataFmt]]：FP32/FP16/BF16） */
    val cfg_srcFmt = Input(FbDataFmt())
    /** 配置：目标格式（[[FbDataFmt]]：MXE4M3/MXE5M2） */
    val cfg_dstFmt = Input(FbDataFmt())
    /** 配置：块大小（实际值 16/32/64 个元素） */
    val cfg_blockSize = Input(UInt(7.W))
    /** 复位内部所有状态（命令结束/出错时拉高） */
    val flush = Input(Bool())
  })

  // 格式编码统一引用 FbDataFmt ChiselEnum（4-bit），本文件不再私藏 FMT_* 常量

  // --- 派生常量 ---
  private val bytesPerBeat = dataWidth / 8          // 输出每 beat 字节数（= 8-bit 元素数）
  private val maxSlots     = dataWidth / 16         // 每 beat 最多元素槽数（FP16/BF16 粒度）
  private val bufBeats     = math.max(1, (64 * 32) / dataWidth) // block 缓冲深度（按最大块 64×FP32 计）
  private val epbLogMax    = log2Ceil(dataWidth)    // elemsPerBeat 的 log2 上限

  /** block 缓冲索引（截断到 Vec 期望位宽，避免 W004 告警） */
  private def bufIdx(x: UInt): UInt = if (bufBeats == 1) 0.U else x(log2Ceil(bufBeats) - 1, 0)

  // ==========================================================================
  // 转换辅助函数（纯组合、位级）
  // ==========================================================================

  /** FP16 (16 bits) → FP32 (32 bits)（次正规保持为 fp32 次正规，量化时按零冲刷） */
  private def fp16ToFp32(x: UInt): UInt = {
    val sign = x(15)
    val exp  = x(14, 10) // 5 bits, bias=15
    val man  = x(9, 0)   // 10 bits
    val newExp = Mux(exp === 0.U, 0.U(8.W),
                 Mux(exp === 31.U, 255.U(8.W),
                 (exp +& 112.U)(7, 0))) // rebias: +127-15=+112
    val newMan = Cat(man, 0.U(13.W))
    Mux(exp === 0.U && man === 0.U, Cat(sign, 0.U(31.W)),
        Cat(sign, newExp, newMan))
  }

  /** 单个元素槽的真指数提取（收集阶段逐槽调用）。
    * 返回 (是否贡献到 max, 真指数)：
    *   - 零/次正规/NaN 不贡献；inf 贡献 127（视为最大指数）。 */
  private def slotTrueExp(raw16: UInt, raw32: UInt, isEven: Boolean): (Bool, SInt) = {
    // FP16: sign(1)+exp(5)+mant(10), bias=15
    val e16 = raw16(14, 10); val m16 = raw16(9, 0)
    val c16 = (e16 =/= 0.U) && (e16 =/= 31.U || m16 === 0.U)
    val x16 = Mux(e16 === 31.U, 127.S(9.W), e16.zext - 15.S)
    // BF16: sign(1)+exp(8)+mant(7), bias=127
    val eBf = raw16(14, 7); val mBf = raw16(6, 0)
    val cBf = (eBf =/= 0.U) && (eBf =/= 255.U || mBf === 0.U)
    val xBf = Mux(eBf === 255.U, 127.S(9.W), eBf.zext - 127.S)
    // FP32: sign(1)+exp(8)+mant(23), bias=127（仅偶数槽有效）
    val e32 = raw32(30, 23); val m32 = raw32(22, 0)
    val c32 = (e32 =/= 0.U) && (e32 =/= 255.U || m32 === 0.U)
    val x32 = Mux(e32 === 255.U, 127.S(9.W), e32.zext - 127.S)

    val contrib = Mux(io.cfg_srcFmt === FbDataFmt.FP16, c16,
                  Mux(io.cfg_srcFmt === FbDataFmt.BF16, cBf, c32 && isEven.B))
    val expv    = Mux(io.cfg_srcFmt === FbDataFmt.FP16, x16,
                  Mux(io.cfg_srcFmt === FbDataFmt.BF16, xBf, x32))
    (contrib, expv)
  }

  /** 单元素量化：fp32 表示 + 块内最大真指数 → 8-bit MX 元素。
    * dstE4=true → E4M3（bias=7, mant=3），否则 E5M2（bias=15, mant=2）。 */
  private def quantizeElem(f32: UInt, maxExp: SInt, dstE4: Bool): UInt = {
    val sign = f32(31)
    val exp  = f32(30, 23)
    val man  = f32(22, 0)

    val isZeroSub = exp === 0.U               // 零/次正规 → ±0
    val isInfNan  = exp === 255.U
    val isNan     = isInfNan && man =/= 0.U

    // 缩放后指数：new_exp = true_exp - max_exp + dst_bias
    val trueExp = exp.zext - 127.S            // 9-bit 有符号
    val dstBias = Mux(dstE4, 7.S(9.W), 15.S(9.W))
    val newExpS = trueExp - maxExp + dstBias

    // --- 尾数截取 + RNE 舍入（两种目标格式各算一路，最后按目标选择）---
    // E4M3: 保留 man(22,20)，guard=man(19)，sticky=man(18,0)
    val mt4 = man(22, 20)
    val up4 = man(19) && (man(18, 0).orR || mt4(0))
    val mr4 = mt4 +& up4                      // 4-bit，bit3=进位
    val c4  = mr4(3)
    // E5M2: 保留 man(22,21)，guard=man(20)，sticky=man(19,0)
    val mt5 = man(22, 21)
    val up5 = man(20) && (man(19, 0).orR || mt5(0))
    val mr5 = mt5 +& up5                      // 3-bit，bit2=进位
    val c5  = mr5(2)

    val carry   = Mux(dstE4, c4, c5)
    // 进位必须以 +1 加到指数上：Bool.zext 零扩展为 SInt（true→+1）。
    // 切勿用 carry.asSInt——Bool.asSInt 是 1-bit 有符号数，true 会被解释为 −1，
    // 导致舍入进位反而使指数减 1（newExpF=newExpS−1），进位跨越下溢边界时误冲刷、
    // 且正常进位时指数字段少 1，量化结果整体偏小。
    val newExpF = newExpS + carry.zext

    // --- 边界判定（underflow/overflow/normByte 三者一致使用 RNE 进位后的 newExpF）---
    // underflow 必须用进位后指数：newExpS=0 且尾数 RNE 进位时 newExpF=1，正确结果是目标格式的
    // 最小正规数（E4M3: exp=1,mant=0 = 2^-6；E5M2: exp=1,mant=0 = 2^-14），若按 newExpS 判定
    // 会被误冲刷成保号零。同时该判据保证走 normByte 时 newExpF ≥ 1 恒为正，位选不会截断出错码。
    val underflow = newExpF <= 0.S                                    // 下溢 → FTZ
    val overflow  = newExpF > Mux(dstE4, 14.S(9.W), 30.S(9.W))        // 上溢 → 饱和
    val satByte   = Mux(dstE4, Cat(sign, "b1110".U(4.W), "b111".U(3.W)),   // E4M3 max=448
                             Cat(sign, "b11110".U(5.W), "b11".U(2.W)))     // E5M2 max=57344
    val nanByteE4 = Cat(sign, "b1111".U(4.W), "b111".U(3.W))          // E4M3 NaN（0x7F|sign<<7）
    val zeroByte  = Cat(sign, 0.U(7.W))
    val normByte  = Mux(dstE4,
      Cat(sign, newExpF(3, 0).asUInt, Mux(c4, 0.U(3.W), mr4(2, 0))),
      Cat(sign, newExpF(4, 0).asUInt, Mux(c5, 0.U(2.W), mr5(1, 0))))

    Mux(isZeroSub, zeroByte,
    Mux(isNan, Mux(dstE4, nanByteE4, satByte),
    Mux(isInfNan || overflow, satByte,
    Mux(underflow, zeroByte, normByte))))
  }

  // ==========================================================================
  // FSM 状态与寄存器
  // ==========================================================================

  private val sCOLLECT = 0.U(2.W)
  private val sQUANT   = 1.U(2.W)
  private val sDRAIN   = 2.U(2.W)

  val state = RegInit(sCOLLECT)

  val blockBuf   = Reg(Vec(bufBeats, UInt(dataWidth.W)))  // block 原始数据缓冲
  val beatCnt    = RegInit(0.U(math.max(1, log2Ceil(bufBeats)).W)) // 已收集 beat 数
  val elemCnt    = RegInit(0.U(8.W))                      // 已收集元素数
  val maxExp     = RegInit((-128).S(9.W))                 // 块内最大真指数（running max）
  val elemIdx    = RegInit(0.U(7.W))                      // 量化阶段当前元素索引
  val outBeatReg = RegInit(0.U(dataWidth.W))              // 输出 beat 累积寄存器
  val outDataValid = RegInit(false.B)                     // outData 有待发送 beat
  val scaleValid = RegInit(false.B)                       // outScale 有待发送字节
  val scaleReg   = RegInit(0.U(8.W))                      // E8M0 scale 锁存

  // --- 源格式相关派生量 ---
  val isFp32Src    = io.cfg_srcFmt === FbDataFmt.FP32
  val elemsPerBeat = Mux(isFp32Src, (dataWidth / 32).U(9.W), (dataWidth / 16).U(9.W))
  val dstE4        = io.cfg_dstFmt === FbDataFmt.MXE4M3

  // --- 输出通道默认连接 ---
  io.outData.valid  := outDataValid
  io.outData.bits   := outBeatReg
  io.outScale.valid := scaleValid
  io.outScale.bits  := scaleReg
  io.in.ready       := state === sCOLLECT

  // ==========================================================================
  // sCOLLECT：收集数据 + max-exp 归约
  // ==========================================================================

  // 本拍有效元素数（块尾可能不足一整拍）
  val remaining = io.cfg_blockSize - elemCnt(6, 0)
  val take      = Mux(elemsPerBeat < remaining, elemsPerBeat(6, 0), remaining)

  // 逐槽真指数归约树（每槽 16-bit 粒度；FP32 时仅偶数槽有效）
  val slotContrib = Wire(Vec(maxSlots, Bool()))
  val slotExp     = Wire(Vec(maxSlots, SInt(9.W)))
  for (i <- 0 until maxSlots) {
    val raw16 = io.in.bits((i + 1) * 16 - 1, i * 16)
    val raw32 = io.in.bits((i / 2 + 1) * 32 - 1, (i / 2) * 32)
    val (c, x) = slotTrueExp(raw16, raw32, i % 2 == 0)
    slotContrib(i) := c && Mux(isFp32Src, (i / 2).U < take, i.U < take)
    slotExp(i)     := x
  }
  val anyContrib = slotContrib.asUInt.orR
  val beatMax    = slotContrib.zip(slotExp).map { case (c, x) =>
    Mux(c, x, (-128).S(9.W))
  }.reduce((a, b) => Mux(a > b, a, b))

  when(state === sCOLLECT) {
    when(io.in.fire) {
      blockBuf(bufIdx(beatCnt)) := io.in.bits
      beatCnt := beatCnt + 1.U
      elemCnt := elemCnt + take
      when(anyContrib && beatMax > maxExp) {
        maxExp := beatMax
      }
      when(elemCnt + take === io.cfg_blockSize) {
        // 块满：锁存 E8M0 scale（全零块 → scale=0），进入量化阶段
        val finalMax = Mux(anyContrib && beatMax > maxExp, beatMax, maxExp)
        val sumExp   = finalMax + 127.S
        scaleReg   := Mux(sumExp <= 0.S, 0.U(8.W), sumExp(7, 0).asUInt)
        scaleValid := true.B
        elemIdx    := 0.U
        outBeatReg := 0.U
        state      := sQUANT
      }
    }
  }

  // ==========================================================================
  // sQUANT：逐元素回读、量化、打包输出
  // ==========================================================================

  when(state === sQUANT) {
    io.in.ready := false.B

    // 从 block 缓冲中提取当前元素（转成 fp32 表示）
    val epbLog  = Mux(isFp32Src, (epbLogMax - 5).U, (epbLogMax - 4).U)
    val beatIdx = elemIdx >> epbLog
    val pos     = elemIdx - (beatIdx << epbLog)
    val word    = blockBuf(bufIdx(beatIdx))
    val rawElem = word >> (pos << Mux(isFp32Src, 5.U(3.W), 4.U(3.W)))
    val f32Elem = Mux(isFp32Src, rawElem(31, 0),
                  Mux(io.cfg_srcFmt === FbDataFmt.FP16, fp16ToFp32(rawElem(15, 0)),
                      Cat(rawElem(15, 0), 0.U(16.W))))  // BF16：低 16 位补零

    val qByte = quantizeElem(f32Elem, maxExp, dstE4)

    // 打包：字节按序填入输出 beat
    val bytePos  = elemIdx(log2Ceil(bytesPerBeat) - 1, 0)
    val lastElem = elemIdx === io.cfg_blockSize - 1.U
    val beatDone = bytePos === (bytesPerBeat - 1).U || lastElem

    // 输出 beat 挂起且下游未接收时暂停量化
    val stall = outDataValid && !io.outData.fire
    val baseBeat = Mux(io.outData.fire, 0.U(dataWidth.W), outBeatReg)

    when(!stall) {
      outBeatReg := baseBeat | (qByte.pad(dataWidth) << (bytePos << 3.U))(dataWidth - 1, 0)
      when(beatDone) {
        outDataValid := true.B
      }
      when(lastElem) {
        elemIdx := 0.U
        state   := sDRAIN
      }.otherwise {
        elemIdx := elemIdx + 1.U
      }
    }
    when(io.outData.fire) {
      outDataValid := false.B
    }
    // scale 字节必须与 outData 一样做“单拍握手即撤销”：block 填满时 scaleValid 置真，
    // 而下游（FbEngine sWRITE）在整个量化阶段恒置 outScale.ready=true。若仅在 sDRAIN 清零，
    // 则整个 sQUANT 阶段 outScale 每拍都 fire，同一 scale 被反复累积、索引回绕后覆盖前序 block，
    // 最终只保留最后一个 block 的 scale。此处在 fire 当拍即撤销 valid，保证每 block 只发一次。
    when(io.outScale.fire) {
      scaleValid := false.B
    }
  }

  // ==========================================================================
  // sDRAIN：等待残余 outData beat 与 outScale 全部发出，复位回收集态
  // ==========================================================================

  when(state === sDRAIN) {
    io.in.ready := false.B
    val dataDone  = !outDataValid || io.outData.fire
    val scaleDone = !scaleValid || io.outScale.fire
    when(io.outData.fire) {
      outDataValid := false.B
      outBeatReg   := 0.U
    }
    when(io.outScale.fire) {
      scaleValid := false.B
    }
    when(dataDone && scaleDone) {
      elemCnt := 0.U
      beatCnt := 0.U
      maxExp  := (-128).S(9.W)
      state   := sCOLLECT
    }
  }

  // ==========================================================================
  // flush：复位全部内部状态
  // ==========================================================================

  when(io.flush) {
    state        := sCOLLECT
    elemCnt      := 0.U
    beatCnt      := 0.U
    elemIdx      := 0.U
    maxExp       := (-128).S(9.W)
    outDataValid := false.B
    scaleValid   := false.B
    outBeatReg   := 0.U
  }
}
