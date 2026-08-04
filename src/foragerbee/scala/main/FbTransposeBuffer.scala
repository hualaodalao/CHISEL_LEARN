/** ForagerBee 转置缓冲 —— tileSize × tileSize 寄存器阵列，行序写入、列序读出。
  *
  * 工作方式（每个 tile 两阶段，由引擎 FSM 驱动 start 与 tile 坐标）：
  *
  *   FILL（行序写入）：
  *     引擎把源行的 AXI R beat 流喂进 io.inBeat，每行 beatsPerRow 拍、共 tileRows 行。
  *     缓冲按元素粒度（1/2/4B）把 beat 拆成元素流，仅保留元素列下标落在
  *     [colStart, colStart + tileCols) 内的元素，写入阵列第 rowCnt 行。
  *     源行允许子 beat（行字节数非整 beat 倍数时按 ceil 读取，行尾无效元素
  *     由列下标范围天然过滤）——这是 TRANSPOSE 相对 COPY 放宽的对齐约束。
  *
  *   DRAIN（列序读出）：
  *     对每一列 c ∈ [0, tileCols)，把 tileRows 个元素 array(r)(c) 按小端序
  *     打包成字节流，并整体右移 outByteOffset 字节（转置输出行在目的 beat 内
  *     的起始偏移），切分为 beatsPerCol 个 beat 从 io.outBeat 流出，
  *     io.outStrb 给出每拍有效字节掩码（供引擎直接写 wstrb）。
  *
  * 元素槽位固定 32bit（硬件最大元素粒度），运行时 elemBytesLog2 选择 8/16/32bit。
  *
  * tile 参数（start 拉高当拍采样锁存）：
  *   tileRows    —— 本 tile 有效行数（≤ tileSize，处理余行）
  *   tileCols    —— 本 tile 有效列数（≤ tileSize，处理余列）
  *   colStart    —— tile 在源行内的起始元素列下标
  *   beatsPerRow —— 每条源行的 beat 数（ceil(行字节数 / beatBytes)）
  *   outByteOffset —— 转置输出字节流在首 beat 内的起始字节偏移（< beatBytes），
  *                    DRAIN 期间由引擎逐列动态驱动（dstStride(0) 可小于 beat）
  */

import chisel3._
import chisel3.util._

class FbTransposeBuffer(cfg: ForagerBeeConfig) extends Module {
  private val T = cfg.tileSize
  private val BB = cfg.beatBytes
  private val log2BB = log2Up(BB)

  val io = IO(new Bundle {

    /** start 脉冲：锁存 tile 参数并进入 FILL */
    val start = Input(Bool())

    /** 本 tile 有效行数（1..T） */
    val tileRows = Input(UInt(16.W))

    /** 本 tile 有效列数（1..T） */
    val tileCols = Input(UInt(16.W))

    /** tile 在源行内的起始元素列下标 */
    val colStart = Input(UInt(16.W))

    /** 每条源行的 beat 数 */
    val beatsPerRow = Input(UInt(20.W))

    /** 元素粒度 log2（0/1/2） */
    val elemBytesLog2 = Input(UInt(2.W))

    /** 转置输出字节流在首 beat 内的起始字节偏移（DRAIN 期间由引擎逐列动态驱动） */
    val outByteOffset = Input(UInt(log2BB.W))

    /** 源行 beat 流入（FILL 阶段） */
    val inBeat = slave(Stream(UInt(cfg.dataWidth.W)))

    /** 转置后 beat 流出（DRAIN 阶段） */
    val outBeat = master(Stream(UInt(cfg.dataWidth.W)))

    /** 当前输出 beat 的有效字节掩码（wstrb） */
    val outStrb = Output(UInt(BB.W))

    /** 每列输出的 beat 数（引擎据此发 AW 的 len 并计数 W beat） */
    val beatsPerCol = Output(UInt(8.W))

    /** 非空闲（FILL 或 DRAIN 进行中） */
    val busy = Output(Bool())
  })

  // --- FSM：IDLE → FILL → DRAIN → IDLE（显式二进制编码） ---
  val sBIDLE = 0.U(2.W)
  val sBFILL = 1.U(2.W)
  val sBDRAIN = 2.U(2.W)
  val state = RegInit(sBIDLE)

  // --- tile 参数锁存寄存器（全部显式复位，避免仿真初始 X 污染） ---
  val tileRowsR = RegInit(0.U(16.W))
  val tileColsR = RegInit(0.U(16.W))
  val colStartR = RegInit(0.U(16.W))
  val beatsPerRowR = RegInit(0.U(20.W))
  val ebLog2R = RegInit(0.U(2.W))

  // --- 运行时派生量 ---
  val eb = 1.U(3.W) << ebLog2R // 元素字节数（1/2/4）
  val epb = (BB.U >> ebLog2R) // 每 beat 元素数（满宽时）
  val elemMask = MuxLookup(ebLog2R, "hFFFFFFFF".U(32.W))(
    Seq(0.U -> "hFF".U(32.W), 1.U -> "hFFFF".U(32.W))
  )

  // --- tileSize × tileSize 元素寄存器阵列（每槽位 32bit） ---
  val array = Reg(Vec(T, Vec(T, UInt(32.W))))

  // --- 游标寄存器（全部显式复位） ---
  val beatCnt = RegInit(0.U(20.W)) // FILL：当前行内 beat 计数
  val rowCnt = RegInit(0.U(16.W)) // FILL：当前行号
  val colCnt = RegInit(0.U(16.W)) // DRAIN：当前列号
  val beatCntOut = RegInit(0.U(8.W)) // DRAIN：当前列内 beat 计数

  // --- 每列输出 beat 数：ceil((outByteOffset + tileRows×eb) / BB) ---
  val totalOutBytes = io.outByteOffset +& tileRowsR * eb
  val beatsPerColV = (totalOutBytes + (BB - 1).U) >> log2BB
  io.beatsPerCol := beatsPerColV

  // --- 默认握手 ---
  io.inBeat.ready := state === sBFILL
  io.outBeat.valid := false.B
  io.outBeat.payload := DontCare
  io.outStrb := 0.U
  io.busy := state =/= sBIDLE

  // --- start：锁存参数进入 FILL ---
  when(io.start) {
    state := sBFILL
    tileRowsR := io.tileRows
    tileColsR := io.tileCols
    colStartR := io.colStart
    beatsPerRowR := io.beatsPerRow
    ebLog2R := io.elemBytesLog2
    beatCnt := 0.U
    rowCnt := 0.U
  }

  // ==========================================================================
  // FILL：行序写入（元素级提取 + 列范围过滤）
  // ==========================================================================
  when(state === sBFILL && io.inBeat.fire) {
    val elemBase = beatCnt * epb // 本 beat 首个元素的行内列下标
    for (s <- 0 until BB) { // 按最大元素数（eb=1 时每 beat BB 个）展开
      val elemIdx = elemBase +& s.U // 行内元素列下标
      val laneBits = (((s.U * eb) % BB.U) * 8.U)(log2BB + 2, 0) // 元素在 beat 内的位偏移
      val elem = (io.inBeat.payload >> laneBits)(31, 0) & elemMask
      val inRange = s.U < epb &&
        elemIdx >= colStartR &&
        elemIdx < colStartR + tileColsR
      when(inRange) {
        // 运行时 colIdx < tileCols ≤ T，截取低位即可（消除宽索引 warning）
        val colIdx = (elemIdx - colStartR)(log2Up(T) - 1, 0)
        for (r <- 0 until T) {
          when(rowCnt === r.U) { array(r)(colIdx) := elem }
        }
      }
    }
    // 行/块推进
    when(beatCnt + 1.U === beatsPerRowR) {
      beatCnt := 0.U
      when(rowCnt + 1.U === tileRowsR) {
        state := sBDRAIN
        colCnt := 0.U
        beatCntOut := 0.U
      }.otherwise {
        rowCnt := rowCnt + 1.U
      }
    }.otherwise {
      beatCnt := beatCnt + 1.U
    }
  }

  // ==========================================================================
  // DRAIN：列序读出（打包 + 字节偏移 + strb 掩码）
  // ==========================================================================

  // 打包宽度上限：outByteOffset 最多 BB-1 字节 + T 行 × 4B 元素
  private val maxOutBytes = BB - 1 + T * 4
  private val maxOutBeats = (maxOutBytes + BB - 1) / BB
  private val packW = maxOutBeats * BB * 8

  val packed = Wire(UInt(packW.W))
  var packAcc = 0.U(packW.W)
  for (r <- 0 until T) {
    val elem = array(r)(colCnt(log2Up(T) - 1, 0)) & elemMask
    val bytePos = io.outByteOffset +& (r.U * eb) // 该元素在字节流中的位置
    val shiftBits = (bytePos * 8.U)(10, 0)
    val shifted = Wire(UInt(packW.W))
    shifted := (elem << shiftBits)(packW - 1, 0)
    packAcc = Mux(r.U < tileRowsR, packAcc | shifted, packAcc)
  }
  packed := packAcc

  when(state === sBDRAIN) {
    io.outBeat.valid := true.B
    io.outBeat.payload := (packed >> (beatCntOut * (BB * 8).U))(cfg.dataWidth - 1, 0)
    // strb：本 beat 内落在 [outByteOffset, outByteOffset + tileRows×eb) 的字节有效
    val startB = io.outByteOffset
    val endB = io.outByteOffset +& tileRowsR * eb
    io.outStrb := VecInit((0 until BB).map { l =>
      val bi = beatCntOut * BB.U + l.U
      bi >= startB && bi < endB
    }).asUInt
    when(io.outBeat.fire) {
      when(beatCntOut + 1.U === beatsPerColV) {
        beatCntOut := 0.U
        when(colCnt + 1.U === tileColsR) {
          state := sBIDLE // 本 tile 排空完毕
        }.otherwise {
          colCnt := colCnt + 1.U
        }
      }.otherwise {
        beatCntOut := beatCntOut + 1.U
      }
    }
  }
}
