/** WbRequant — 【P0 epilogue-2】fused requant 独立数值模块（纯组合，totalN-lane 并行）。
  *
  * 计算链（TFLite 端侧后量化范式，gemmlowp RoundingDivideByPOT 约定）：
  *   y[i] = sat_fmt( round_half_up( acc[i] × M[i] >> shift ) + zp )
  *   - acc：INT32 累加槽二补码直读（可能已含 epilogue-1 bias 基底、ReLU clamp——
  *     两者均在调用方 WbOutCvt 的 int32Seq 源级完成，本模块不做 clamp）；
  *   - M[i]：per-channel int32 乘子（N 轴向量，随 N-tile 由 Executor rqMRegs 供给）；
  *   - shift：per-tensor uint6 算术右移量 [0,63]；
  *   - zp：per-tensor int16 零点；
  *   - round_half_up：shift>0 时先加 1<<(shift-1) 再算术右移（tie 向 +∞）；
  *     shift=0 时既不加偏也不移位；
  *   - sat_fmt：【仅 requant 模式饱和】INT8→[-128,127]、INT4→[-8,7]，保号钳位
  *     （非 requant 整数族的「截断绕回、不饱和」裁定不变，那些路径不经本模块）。
  *
  * 恒等式留痕：round_half_up(x>>s) = (x + 1<<(s-1)) >> s = ((x>>(s-1)) + 1) >> 1（s>=1），
  *   对全体整数（含负数，floor 语义一致）逐值成立。实现取右移拆解式：动态右移不扩宽，
  *   规避「动态左移位宽 = w + 2^shiftW」的 128-bit 爆炸；shift=0 时 shift-1 在 6-bit
  *   域回绕成 63，但该臂整体被外层 Mux 弃选，死逻辑无害。
  *
  * 数值域：|acc|,|M| < 2^31 ⟹ |prod| < 2^62，64-bit 乘积无溢出；+1/>>1/+zp 全链
  *   ≤ 66-bit，饱和钳位后落回 8/4-bit。
  *
  * 例化约束：仅 cfg.hasInt 配置例化（见 WbOutCvt）；模块本身无编译期格式门控。
  * 打包契约：lane i 落 byte i（INT8，bits[8i+7:8i]）/ nibble i（INT4，bits[4i+3:4i]），
  *   lane0 在最低位——与 WbOutCvt packedInt8/packedInt4 的 Cat(.reverse) 同序。
  */

import chisel3._
import chisel3.util._

class WbRequant(cfg: WorkerBeeConfig) extends Module {
  require(cfg.totalN >= 1, s"WbRequant: totalN(${cfg.totalN}) must be >= 1")

  val io = IO(new Bundle {
    /** INT32 累加槽（二补码位型；低列号在低 lane） */
    val acc    = Input(Vec(cfg.totalN, UInt(32.W)))
    /** per-channel 乘子 M（int32 二补码位型，N 轴与 acc 同序） */
    val m      = Input(Vec(cfg.totalN, UInt(32.W)))
    /** per-tensor 算术右移量 [0,63] */
    val shift  = Input(UInt(6.W))
    /** per-tensor 零点 */
    val zp     = Input(SInt(16.W))
    /** 输出档位：1=INT4 饱和 [-8,7] / 0=INT8 饱和 [-128,127] */
    val isInt4 = Input(Bool())
    /** 密集行（INT8：低 totalN×8 有效；INT4：低 totalN×4 有效；高位零填充） */
    val out    = Output(UInt((cfg.totalN * 32).W))
  })

  // per-lane 数值链：符号乘 → half-up 算术右移 → +zp → 保号饱和
  private val laneVals: Seq[SInt] = (0 until cfg.totalN).map { i =>
    val prod   = io.acc(i).asSInt * io.m(i).asSInt            // SInt(64)
    val nudged = (prod >> (io.shift - 1.U)) + 1.S             // 右移拆解式 +1（shift>=1 臂）
    val qShift = nudged >> 1.U
    val q      = Mux(io.shift === 0.U, prod, qShift)          // shift=0：直通不移位
    val qz     = q + io.zp
    val lo     = Mux(io.isInt4, (-8).S, (-128).S)
    val hi     = Mux(io.isInt4, 7.S, 127.S)
    Mux(qz < lo, lo, Mux(qz > hi, hi, qz))
  }

  // 饱和钳位保证低 8/4 位即最终码字；打包序 = packedInt8/packedInt4 同序（lane0 最低）
  private val packed8 = Cat(laneVals.map(_(7, 0)).reverse)    // totalN×8 bit
  private val packed4 = Cat(laneVals.map(_(3, 0)).reverse)    // totalN×4 bit

  io.out := Mux(io.isInt4,
    Cat(0.U((cfg.totalN * 28).W), packed4),
    Cat(0.U((cfg.totalN * 24).W), packed8))
}
