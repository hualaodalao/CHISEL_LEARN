/** ForagerBee im2col 地址/控制序列生成器。
  *
  * 独立的纯控制模块，不包含数据路径。根据 im2col 卷积展开参数，
  * 逐个输出每个 "micro-row"（一行 kW 个元素）的源地址、目的地址和控制信号。
  *
  * 4 层嵌套计数器遍历顺序（内→外）：kh → c → ow → oh
  * 输出矩阵行数 = outH × outW × C × kH，每行 kW 个元素。
  *
  * @param addressWidth 地址位宽（bit）
  */

import chisel3._
import chisel3.util._

class FbIm2colAddrGen(addressWidth: Int) extends Module {

  val io = IO(new Bundle {
    // 配置输入（Engine 锁存命令后驱动）
    val start        = Input(Bool())                   // 开始脉冲（一拍有效）
    val srcBase      = Input(UInt(addressWidth.W))
    val dstBase      = Input(UInt(addressWidth.W))
    // 读/写侧元素粒度分离（M2）：srcAddr 按源格式推进、dstAddr/dstOffset 按目的格式推进，
    // 使格式转换（srcFmt≠dstFmt）时源/目的地址步进各自自洽。INT4 亚字节已被引擎校验拒于
    // IM2COL 路径外，故两侧 ebLog2 恒为字节粒度。
    val srcElemBytesLog2 = Input(UInt(2.W))
    val dstElemBytesLog2 = Input(UInt(2.W))
    val kernel       = Input(Vec(2, UInt(8.W)))        // (kH, kW)
    val stride       = Input(Vec(2, UInt(8.W)))        // (sH, sW)
    val pad          = Input(Vec(2, UInt(8.W)))        // (pH, pW)
    val dilation     = Input(Vec(2, UInt(8.W)))        // (dH, dW)
    val inShape      = Input(Vec(3, UInt(16.W)))       // (C, H, W)
    val kw           = Input(UInt(8.W))               // fallback 当前选择的水平元素

    // 输出：当前 micro-row 信息
    val valid        = Output(Bool())                  // 当前 micro-row 信息有效
    val advance      = Input(Bool())                   // Engine 完成当前 micro-row，推进
    val srcAddr      = Output(UInt(addressWidth.W))    // kw=0 源地址；整行可 burst 时有效
    val elemSrcAddr  = Output(UInt(addressWidth.W))    // kw 源地址（!elemIsPad 时有效）
    val elemIsPad    = Output(Bool())                  // ih 或当前 iw 越界
    val dstAddr      = Output(UInt(addressWidth.W))    // 目标地址（始终线性递增）
    val isPad        = Output(Bool())                  // ih 越界，当前 micro-row 需写零
    val microRowElems = Output(UInt(8.W))              // micro-row 元素数 = kW
    val burstable    = Output(Bool())                  // 源端连续可 burst（dW==1 且整行不越界）
    val done         = Output(Bool())                  // 全部遍历完成
  })

  // ==========================================================================
  // 锁存配置寄存器
  // ==========================================================================
  val kH   = RegInit(0.U(8.W))
  val kW   = RegInit(0.U(8.W))
  val sH   = RegInit(0.U(8.W))
  val sW   = RegInit(0.U(8.W))
  val pH   = RegInit(0.U(8.W))
  val pW   = RegInit(0.U(8.W))
  val dH   = RegInit(0.U(8.W))
  val dW   = RegInit(0.U(8.W))
  val C    = RegInit(0.U(16.W))
  val H    = RegInit(0.U(16.W))
  val W    = RegInit(0.U(16.W))
  // 最大输出维度为 65535 + 2*255 = 66045；Engine 另行拒绝 >65535。
  val outH = RegInit(0.U(17.W))
  val outW = RegInit(0.U(17.W))
  val srcElemBytes = RegInit(0.U(4.W))
  val dstElemBytes = RegInit(0.U(4.W))

  val srcBaseReg = RegInit(0.U(addressWidth.W))
  val dstBaseReg = RegInit(0.U(addressWidth.W))

  // ==========================================================================
  // 4 层嵌套计数器：oh(0..outH-1), ow(0..outW-1), c(0..C-1), kh(0..kH-1)
  // ==========================================================================
  val cntKh = RegInit(0.U(8.W))
  val cntC  = RegInit(0.U(16.W))
  val cntOw = RegInit(0.U(17.W))
  val cntOh = RegInit(0.U(17.W))

  // ==========================================================================
  // 状态管理
  // ==========================================================================
  val running  = RegInit(false.B)
  val doneReg  = RegInit(false.B)

  // 目的地址偏移寄存器（线性递增）
  val dstOffset = RegInit(0.U(addressWidth.W))

  // ==========================================================================
  // 启动逻辑：start 脉冲时计算并锁存参数
  // ==========================================================================
  when(io.start) {
    kH := io.kernel(0)
    kW := io.kernel(1)
    sH := io.stride(0)
    sW := io.stride(1)
    pH := io.pad(0)
    pW := io.pad(1)
    dH := io.dilation(0)
    dW := io.dilation(1)
    C  := io.inShape(0)
    H  := io.inShape(1)
    W  := io.inShape(2)
    srcElemBytes := 1.U(4.W) << io.srcElemBytesLog2
    dstElemBytes := 1.U(4.W) << io.dstElemBytesLog2
    srcBaseReg := io.srcBase
    dstBaseReg := io.dstBase

    // out = (input + 2*pad - (dilation*(kernel-1) + 1)) / stride + 1
    // Engine 校验几何参数；本模块仍保护减法和除法，非法 start 立即完成。
    val hKm1 = Mux(io.kernel(0) =/= 0.U, io.kernel(0) - 1.U, 0.U)
    val wKm1 = Mux(io.kernel(1) =/= 0.U, io.kernel(1) - 1.U, 0.U)
    val hEffective = (io.dilation(0) * hKm1) +& 1.U
    val wEffective = (io.dilation(1) * wKm1) +& 1.U
    val hPadded = io.inShape(1) +& (io.pad(0) << 1)
    val wPadded = io.inShape(2) +& (io.pad(1) << 1)
    val geometryValid = (io.inShape(0) =/= 0.U) &&
      (io.inShape(1) =/= 0.U) && (io.inShape(2) =/= 0.U) &&
      (io.kernel(0) =/= 0.U) && (io.kernel(1) =/= 0.U) &&
      (io.stride(0) =/= 0.U) && (io.stride(1) =/= 0.U) &&
      (io.dilation(0) =/= 0.U) && (io.dilation(1) =/= 0.U) &&
      (hPadded >= hEffective) && (wPadded >= wEffective)
    val outHnum = Mux(geometryValid, hPadded, hEffective) - hEffective
    val outWnum = Mux(geometryValid, wPadded, wEffective) - wEffective
    val safeSH = Mux(io.stride(0) =/= 0.U, io.stride(0), 1.U)
    val safeSW = Mux(io.stride(1) =/= 0.U, io.stride(1), 1.U)
    outH := Mux(geometryValid, outHnum / safeSH + 1.U, 0.U)
    outW := Mux(geometryValid, outWnum / safeSW + 1.U, 0.U)

    // 复位计数器和状态
    cntKh := 0.U
    cntC  := 0.U
    cntOw := 0.U
    cntOh := 0.U
    dstOffset := 0.U
    running := geometryValid
    doneReg := !geometryValid
  }

  // ==========================================================================
  // 计数器进位链（advance 时推进）
  // ==========================================================================
  when(running && io.advance && !doneReg) {
    val khWrap = cntKh === (kH - 1.U)
    val cWrap  = cntC === (C - 1.U)
    val owWrap = cntOw === (outW - 1.U)
    val ohWrap = cntOh === (outH - 1.U)

    // kh 递增 / wrap
    when(khWrap) {
      cntKh := 0.U
      // c 递增 / wrap
      when(cWrap) {
        cntC := 0.U
        // ow 递增 / wrap
        when(owWrap) {
          cntOw := 0.U
          // oh 递增 / wrap → done
          when(ohWrap) {
            doneReg := true.B
            running := false.B
          }.otherwise {
            cntOh := cntOh + 1.U
          }
        }.otherwise {
          cntOw := cntOw + 1.U
        }
      }.otherwise {
        cntC := cntC + 1.U
      }
    }.otherwise {
      cntKh := cntKh + 1.U
    }

    // dstOffset 线性递增（按目的格式粒度，M2）
    dstOffset := dstOffset + kW * dstElemBytes
  }

  // ==========================================================================
  // 地址与控制信号组合逻辑
  // ==========================================================================

  // ih = oh*sH - pH + kh*dH；完整乘积/进位和符号位保留到最终地址。
  val ihRaw    = (cntOh * sH) +& (cntKh * dH)
  val ihVal    = ihRaw.zext - pH.zext
  val ihNeg    = ihVal < 0.S
  val ihOver   = ihVal >= H.zext
  val ihInvalid = ihNeg || ihOver

  // kw=0 的起点和 kw=kW-1 的终点，用于整行 burst 判定。
  val iwRaw    = cntOw * sW
  val iwVal    = iwRaw.zext - pW.zext
  val iwNeg    = iwVal < 0.S
  val kwLast   = Mux(kW =/= 0.U, kW - 1.U, 0.U)
  val iwLast   = iwVal +& (kwLast * dW).zext
  val iwOver   = iwLast >= W.zext

  // fallback 按 io.kw 选择元素，不推进 micro-row 计数器。
  val elemIwRaw = iwRaw +& (io.kw * dW)
  val elemIwVal = elemIwRaw.zext - pW.zext
  val elemIwInvalid = (elemIwVal < 0.S) || (elemIwVal >= W.zext)
  io.elemIsPad := ihInvalid || elemIwInvalid

  // isPad: ih 越界 → 整行 pad
  io.isPad := ihInvalid

  // burstable: dW==1 且 ih 有效 且整行 kw 在有效范围内（!iwNeg && !iwOver）
  io.burstable := !ihInvalid && (dW === 1.U) && !iwNeg && !iwOver

  // 源地址：srcBase + (c*H*W + ih*W + iw) * srcElemBytes。
  // 仅最终 IO 截取 addressWidth，负坐标不会在中间被无符号截断。
  val srcRowIdx = (cntC * H * W).zext +& (ihVal * W.zext)
  val flatIdx = srcRowIdx +& iwVal
  val elemFlatIdx = srcRowIdx +& elemIwVal
  io.srcAddr := (srcBaseReg.zext +& flatIdx * srcElemBytes.zext).asUInt
  io.elemSrcAddr := (srcBaseReg.zext +& elemFlatIdx * srcElemBytes.zext).asUInt

  // 目的地址（线性递增）
  io.dstAddr := dstBaseReg + dstOffset

  // micro-row 元素数 = kW
  io.microRowElems := kW

  // valid 与 done
  io.valid := running && !doneReg
  io.done  := doneReg
}
