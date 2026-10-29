/** WbRequant — fused requant 数值模块（纯组合，totalN-lane 并行），gemmlowp
  * RoundingDivideByPOT 约定：y[i] = sat_fmt( round_half_up( acc[i] × M[i] >> shift ) + zp )。
  *   acc = INT32 累加槽二补码直读（bias/ReLU clamp 由 WbOutCvt 完成）；M[i] = per-channel
  *   int32 乘子（N 轴与 acc 同序）；shift = per-tensor uint6 ∈ [0,63]；zp = per-tensor int16；
  *   sat_fmt：INT8→[-128,127]、INT4→[-8,7] 保号钳位。
  *   恒等式 round_half_up(x>>s) = (x + 1<<(s-1)) >> s = ((x>>(s-1)) + 1) >> 1（s>=1）对全体
  *   整数（含负数，floor 语义一致）逐值成立；取右移拆解式以规避「动态左移位宽 = w + 2^shiftW」
  *   的 128-bit 爆炸。shift=0 时 shift-1 在 6-bit 域回绕成 63，但该臂被外层 Mux 弃选（死逻辑）。
  *   |acc|,|M| < 2^31 ⟹ |prod| < 2^62，64-bit 乘积无溢出；+1/>>1/+zp 全链 ≤ 66-bit。
  * 仅 cfg.hasInt 配置例化。打包契约：lane i 落 byte i（INT8，bits[8i+7:8i]）/ nibble i
  *   （INT4，bits[4i+3:4i]），lane0 在最低位，与 WbOutCvt packedInt8/packedInt4 的
  *   Cat(.reverse) 同序；输出高位零填充。
  */

import chisel3._
import chisel3.util._

class WbRequant(cfg: WorkerBeeConfig) extends Module {
  require(cfg.totalN >= 1, s"WbRequant: totalN(${cfg.totalN}) must be >= 1")

  val io = IO(new Bundle {
    val acc    = Input(Vec(cfg.totalN, UInt(32.W)))
    val m      = Input(Vec(cfg.totalN, UInt(32.W)))
    val shift  = Input(UInt(6.W))
    val zp     = Input(SInt(16.W))
    val isInt4 = Input(Bool())   // 1=INT4 / 0=INT8
    val out    = Output(UInt((cfg.totalN * 32).W))
  })

  private val laneVals: Seq[SInt] = (0 until cfg.totalN).map { i =>
    val prod   = io.acc(i).asSInt * io.m(i).asSInt
    val nudged = (prod >> (io.shift - 1.U)) + 1.S             // 右移拆解式 +1（shift>=1 臂）
    val qShift = nudged >> 1.U
    val q      = Mux(io.shift === 0.U, prod, qShift)          // shift=0：直通不移位
    val qz     = q + io.zp
    val lo     = Mux(io.isInt4, (-8).S, (-128).S)
    val hi     = Mux(io.isInt4, 7.S, 127.S)
    Mux(qz < lo, lo, Mux(qz > hi, hi, qz))
  }

  // 饱和钳位保证低 8/4 位即最终码字
  private val packed8 = Cat(laneVals.map(_(7, 0)).reverse)
  private val packed4 = Cat(laneVals.map(_(3, 0)).reverse)

  io.out := Mux(io.isInt4,
    Cat(0.U((cfg.totalN * 28).W), packed4),
    Cat(0.U((cfg.totalN * 24).W), packed8))
}
