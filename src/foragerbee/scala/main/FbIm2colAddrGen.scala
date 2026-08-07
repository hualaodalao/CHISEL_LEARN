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
    val elemBytesLog2 = Input(UInt(2.W))
    val kernel       = Input(Vec(2, UInt(8.W)))        // (kH, kW)
    val stride       = Input(Vec(2, UInt(8.W)))        // (sH, sW)
    val pad          = Input(Vec(2, UInt(8.W)))        // (pH, pW)
    val dilation     = Input(Vec(2, UInt(8.W)))        // (dH, dW)
    val inShape      = Input(Vec(3, UInt(16.W)))       // (C, H, W)

    // 输出：当前 micro-row 信息
    val valid        = Output(Bool())                  // 当前 micro-row 信息有效
    val advance      = Input(Bool())                   // Engine 完成当前 micro-row，推进
    val srcAddr      = Output(UInt(addressWidth.W))    // 源起始地址（!isPad 时有效）
    val dstAddr      = Output(UInt(addressWidth.W))    // 目标地址（始终线性递增）
    val isPad        = Output(Bool())                  // 当前 micro-row 需写零
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
  val outH = RegInit(0.U(16.W))
  val outW = RegInit(0.U(16.W))
  val elemBytes = RegInit(0.U(4.W))

  val srcBaseReg = RegInit(0.U(addressWidth.W))
  val dstBaseReg = RegInit(0.U(addressWidth.W))

  // ==========================================================================
  // 4 层嵌套计数器：oh(0..outH-1), ow(0..outW-1), c(0..C-1), kh(0..kH-1)
  // ==========================================================================
  val cntKh = RegInit(0.U(8.W))
  val cntC  = RegInit(0.U(16.W))
  val cntOw = RegInit(0.U(16.W))
  val cntOh = RegInit(0.U(16.W))

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
    elemBytes := 1.U(4.W) << io.elemBytesLog2
    srcBaseReg := io.srcBase
    dstBaseReg := io.dstBase

    // 计算 outH = (H + 2*pH - dH*(kH-1) - 1) / sH + 1
    val hExt = io.inShape(1).pad(20)
    val pH2  = (io.pad(0).pad(20)) << 1
    val dhKm1 = io.dilation(0).pad(20) * (io.kernel(0).pad(20) - 1.U)
    val outHnum = hExt + pH2 - dhKm1 - 1.U
    outH := (outHnum / io.stride(0).pad(20))(15, 0) + 1.U

    // 计算 outW = (W + 2*pW - dW*(kW-1) - 1) / sW + 1
    val wExt = io.inShape(2).pad(20)
    val pW2  = (io.pad(1).pad(20)) << 1
    val dwKm1 = io.dilation(1).pad(20) * (io.kernel(1).pad(20) - 1.U)
    val outWnum = wExt + pW2 - dwKm1 - 1.U
    outW := (outWnum / io.stride(1).pad(20))(15, 0) + 1.U

    // 复位计数器和状态
    cntKh := 0.U
    cntC  := 0.U
    cntOw := 0.U
    cntOh := 0.U
    dstOffset := 0.U
    running := true.B
    doneReg := false.B
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

    // dstOffset 线性递增
    dstOffset := dstOffset + kW * elemBytes
  }

  // ==========================================================================
  // 地址与控制信号组合逻辑
  // ==========================================================================

  // ih 越界判断（无符号运算技巧）
  val ihRaw    = (cntOh * sH).pad(20) + (cntKh * dH).pad(20)  // oh*sH + kh*dH
  val ihNeg    = ihRaw < pH.pad(20)                             // 上方越界（ih "负"）
  val ihVal    = ihRaw - pH.pad(20)                             // 实际 ih（仅 !ihNeg 时有效）
  val ihOver   = ihVal >= H.pad(20)                             // 下方越界
  val ihInvalid = ihNeg || ihOver

  // iw 越界判断
  val iwRaw    = (cntOw * sW).pad(20)                           // ow*sW
  val iwNeg    = iwRaw < pW.pad(20)                             // 左越界（kw=0 时）
  val iwVal    = iwRaw - pW.pad(20)                             // 实际 iw_base
  val iwLast   = iwVal + ((kW - 1.U).pad(20)) * dW.pad(20)     // kw=kW-1 时的 iw
  val iwOver   = iwLast >= W.pad(20)                            // 右越界

  // isPad: ih 越界 → 整行 pad
  io.isPad := ihInvalid

  // burstable: dW==1 且 ih 有效 且整行 kw 在有效范围内（!iwNeg && !iwOver）
  io.burstable := !ihInvalid && (dW === 1.U) && !iwNeg && !iwOver

  // 源地址（仅 !isPad 时有效）：srcBase + (c*H*W + ih*W + iw_base) * elemBytes
  val flatIdx = (cntC.pad(addressWidth) * H.pad(addressWidth) * W.pad(addressWidth)) +
                (ihVal(15, 0).pad(addressWidth) * W.pad(addressWidth)) +
                iwVal(15, 0).pad(addressWidth)
  io.srcAddr := srcBaseReg + flatIdx * elemBytes.pad(addressWidth)

  // 目的地址（线性递增）
  io.dstAddr := dstBaseReg + dstOffset

  // micro-row 元素数 = kW
  io.microRowElems := kW

  // valid 与 done
  io.valid := running && !doneReg
  io.done  := doneReg
}
