/** 格式转换模块集（融合浮点乘法器的前级转换构建块）
  *
  * 7 个纯组合模块，输出精确匹配 FpMul.scala 乘法器的输入接口：
  *   · FpCvtFp16ToFp32   — fp16 → fp32，供 FpMulFp16Bf16.io.a/b
  *   · FpCvtBf16ToFp32   — bf16 → fp32，供 FpMulFp16Bf16.io.a/b
  *   · FpCvtMx8ToMid     — mxfp8(E4M3/E5M2) + E8M0 scale → sign/exp/mant(3)，供 FpMulMx8
  *   · FpCvtMx4ToMid     — mxfp4(E2M1) + E8M0 scale → sign/exp/mant(1)，供 FpMulMx4
  *   · FpCvtMx4ToMx8Mid  — mxfp4(E2M1) + E8M0 scale → sign/exp/mant(3)，供 FpMulMx8
  *   · FpCvtMx8ToFp32    — mxfp8(E4M3/E5M2) + E8M0 scale → 完整 fp32，供 FpMulFp16Bf16
  *   · FpCvtMx4ToFp32    — mxfp4(E2M1) + E8M0 scale → 完整 fp32，供 FpMulFp16Bf16
  *
  * 所有转换逻辑内联实现（不调用 Fp32 object），便于后续融合修改。
  * 指数 overflow/underflow/subnormal 行为与 Fp32.mxToFp32 逐位一致。
  */

import chisel3._
import chisel3.util._

// ============================================================================
// 1. FpCvtFp16ToFp32 — fp16 (IEEE 754 half: 1+5+10) → fp32
// ============================================================================

/** fp16 → fp32 纯组合转换模块
  *
  * 输入: in = 16-bit IEEE 754 half-precision {sign(1), exp(5), mant(10)}, bias=15
  * 输出: out = 32-bit fp32 {sign(1), exp(8), mant(23)}, bias=127
  *       → 直接对接 FpMulFp16Bf16.io.a 或 .io.b
  *
  * 简化处理（与 Fp32.fp16ToFp32 行为一致）：
  *   - e5=0 && m10=0 → ±0
  *   - e5=0 && m10≠0 → fp32 subnormal（exp=0, mant 左对齐）
  *   - e5≠0 → 正常偏置迁移: exp_fp32 = e5 + 112 (= e5 - 15 + 127)
  *   - 不特殊处理 fp16 Inf/NaN（e5=31 按正常偏置迁移，不做 fp32 Inf 映射）
  */
class FpCvtFp16ToFp32 extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(16.W))    // 原始 fp16
    val out = Output(UInt(32.W))   // fp32 → FpMulFp16Bf16.io.a/b
  })

  val s   = io.in(15)
  val e5  = io.in(14, 10)
  val m10 = io.in(9, 0)

  // 偏置迁移: fp16 bias=15 → fp32 bias=127, 差值=112
  // e5=0 时输出 exp=0（零/subnormal 统一）
  val e8 = Mux(e5 === 0.U, 0.U(8.W), (e5 +& 112.U)(7, 0))

  // 尾数左对齐: fp16 的 10-bit → fp32 的 23-bit（低 13 位补零）
  val m23 = Cat(m10, 0.U(13.W))

  // ±0 守护: e5=0 && m10=0 时确保输出干净 ±0（mantissa 全零）
  val isZero = (e5 === 0.U) && (m10 === 0.U)

  io.out := Mux(isZero, Cat(s, 0.U(31.W)), Cat(s, e8, m23))
}

// ============================================================================
// 2. FpCvtBf16ToFp32 — bf16 (1+8+7) → fp32
// ============================================================================

/** bf16 → fp32 纯组合转换模块
  *
  * 输入: in = 16-bit bfloat16 {sign(1), exp(8), mant(7)}, bias=127（与 fp32 同偏置）
  * 输出: out = 32-bit fp32 {sign(1), exp(8), mant(23)}
  *       → 直接对接 FpMulFp16Bf16.io.a 或 .io.b
  *
  * bf16 就是 fp32 的高 16 位截断，转换仅需低 16 位补零。
  */
class FpCvtBf16ToFp32 extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(16.W))    // 原始 bf16
    val out = Output(UInt(32.W))   // fp32 → FpMulFp16Bf16.io.a/b
  })

  io.out := Cat(io.in, 0.U(16.W))
}

// ============================================================================
// 3. FpCvtMx8ToMid — mxfp8 (E4M3/E5M2) + E8M0 scale → FpMulMx8 中间表示
// ============================================================================

/** mxfp8 (E4M3 或 E5M2) + E8M0 scale → FpMulMx8 中间表示（sign/exp/mant(3)）
  *
  * 输入:
  *   in     = 8-bit MX 元素（E4M3: {s(1),e(4),m(3)} 或 E5M2: {s(1),e(5),m(2)}）
  *   scale  = 8-bit E8M0 scale（纯指数，bias=127）
  *   isE5M2 = 格式选择（true=E5M2, false=E4M3）
  * 输出:
  *   sign(1) → FpMulMx8.io.aSign/bSign
  *   exp(8)  → FpMulMx8.io.aExp/bExp（fp32 偏置域，已含 scale 加法与 bias 迁移）
  *   mant(3) → FpMulMx8.io.aMant/bMant（对应 fp32 mantissa[22:20]）
  *
  * 特殊值处理（与 Fp32.mxToFp32 subnormalFtz=true 路径逐位一致）：
  *   - zero (e=0,m=0)         → sign 保留, exp=0, mant=0
  *   - subnormal (e=0,m≠0)   → FTZ: sign 保留, exp=0, mant=0
  *   - InfNan                 → saturate: sign 保留, exp=254, mant=7
  *   - overflow (exp>254)     → saturate: sign 保留, exp=254, mant=7
  *   - underflow (exp<1)      → FTZ: sign 保留, exp=0, mant=0
  */
class FpCvtMx8ToMid extends Module {
  val io = IO(new Bundle {
    val in     = Input(UInt(8.W))   // 原始 8-bit MX 元素
    val scale  = Input(UInt(8.W))   // E8M0 scale
    val isE5M2 = Input(Bool())      // 格式选择: true=E5M2, false=E4M3
    // 消费有效：仅门控下方 inf/nan 断言，不入数据通路
    val consumeValid = Input(Bool())
    val sign   = Output(UInt(1.W))  // → FpMulMx8.io.aSign/bSign
    val exp    = Output(UInt(8.W))  // → FpMulMx8.io.aExp/bExp
    val mant   = Output(UInt(3.W))  // → FpMulMx8.io.aMant/bMant
  })

  // ========== E4M3 路径 (expW=4, mantW=3, bias=7, nanNeedMantAllOne=true) ==========
  val sE4M3  = io.in(7)
  val e4     = io.in(6, 3)          // 4-bit exponent
  val m3     = io.in(2, 0)          // 3-bit mantissa

  val isZeroE4M3 = (e4 === 0.U) && (m3 === 0.U)
  val isSubE4M3  = (e4 === 0.U) && (m3 =/= 0.U)
  // E4M3 NaN: exp 全 1 且 mant 全 1（即 e4=15, m3=7）
  val isInfNanE4M3 = e4.andR && m3.andR

  // exp_fp32 = e4 + scale - 7（有符号运算检测上下溢）
  val expSumE4M3    = e4 +& io.scale            // 9-bit UInt
  val expSignedE4M3 = expSumE4M3.zext - 7.S     // SInt
  val underflowE4M3 = expSignedE4M3 < 1.S
  val overflowE4M3  = expSignedE4M3 > 254.S

  // E4M3 输出（mant 直接就是 3 bit，无需左对齐）
  val signE4M3 = sE4M3
  val expE4M3  = Mux(isZeroE4M3 || isSubE4M3 || underflowE4M3, 0.U(8.W),
                 Mux(isInfNanE4M3 || overflowE4M3, 254.U(8.W),
                   expSignedE4M3.asUInt(7, 0)))
  val mantE4M3 = Mux(isZeroE4M3 || isSubE4M3 || underflowE4M3, 0.U(3.W),
                 Mux(isInfNanE4M3 || overflowE4M3, 7.U(3.W),
                   m3))

  // ========== E5M2 路径 (expW=5, mantW=2, bias=15, nanNeedMantAllOne=false) ==========
  val sE5M2  = io.in(7)
  val e5     = io.in(6, 2)          // 5-bit exponent
  val m2     = io.in(1, 0)          // 2-bit mantissa

  val isZeroE5M2 = (e5 === 0.U) && (m2 === 0.U)
  val isSubE5M2  = (e5 === 0.U) && (m2 =/= 0.U)
  // E5M2 Inf/NaN: exp 全 1（即 e5=31）
  val isInfNanE5M2 = e5.andR

  // exp_fp32 = e5 + scale - 15
  val expSumE5M2    = e5 +& io.scale            // 9-bit UInt
  val expSignedE5M2 = expSumE5M2.zext - 15.S    // SInt
  val underflowE5M2 = expSignedE5M2 < 1.S
  val overflowE5M2  = expSignedE5M2 > 254.S

  // E5M2 输出（mant 2-bit 左对齐到 3-bit: Cat(m2, 0)）
  val signE5M2 = sE5M2
  val expE5M2  = Mux(isZeroE5M2 || isSubE5M2 || underflowE5M2, 0.U(8.W),
                 Mux(isInfNanE5M2 || overflowE5M2, 254.U(8.W),
                   expSignedE5M2.asUInt(7, 0)))
  val mantE5M2 = Mux(isZeroE5M2 || isSubE5M2 || underflowE5M2, 0.U(3.W),
                 Mux(isInfNanE5M2 || overflowE5M2, 7.U(3.W),
                   Cat(m2, 0.U(1.W))))

  // ========== 输出选择 ==========
  io.sign := Mux(io.isE5M2, signE5M2, signE4M3)
  io.exp  := Mux(io.isE5M2, expE5M2, expE4M3)
  io.mant := Mux(io.isE5M2, mantE5M2, mantE4M3)

  // ========== inf/nan 守护（与 HiveCvtOpMx 同源；E2M1 不经本路径） ==========
  //   E5M2 exp 全1 → inf/nan；E4M3 exp 全1 且 mant 全1 → NaN
  val selInfNan = Mux(io.isE5M2, isInfNanE5M2, isInfNanE4M3)
  val consumeValidEff = WireDefault(false.B)
  consumeValidEff := io.consumeValid
  assert(!consumeValidEff || !selInfNan,
    "FpCvtMx8ToMid: MX element is inf/nan (saturated to fp32 max); indicates software-side anomaly")
}

// ============================================================================
// 4. FpCvtMx4ToMid — mxfp4 (E2M1) + E8M0 scale → FpMulMx4 中间表示
// ============================================================================

/** mxfp4 (E2M1) + E8M0 scale → FpMulMx4 中间表示（sign/exp/mant(1)）
  *
  * 输入:
  *   in    = 4-bit E2M1 元素 {s(1), e(2), m(1)}，bias=1
  *   scale = 8-bit E8M0 scale（bias=127）
  * 输出:
  *   sign(1) → FpMulMx4.io.aSign/bSign
  *   exp(8)  → FpMulMx4.io.aExp/bExp（fp32 偏置域）
  *   mant(1) → FpMulMx4.io.aMant/bMant（对应 fp32 mantissa[22]）
  *
  * E2M1 格式特性：
  *   - 无 Inf/NaN 编码（noInfNan=true）：码 0x7 = ±6.0 是最大有限值
  *   - subnormal 不 FTZ（subnormalFtz=false）：e=0,m=1 = ±0.5 是一等公民值
  *
  * 特殊值处理（与 Fp32.mxToFp32 subnormalFtz=false 路径逐位一致）：
  *   - zero (e=0,m=0)         → sign 保留, exp=0, mant=0
  *   - subnormal (e=0,m=1)    → sign 保留, exp=scale-1, mant=0
  *     推导：真值 = 0.5 × 2^(scale-127) = 1.0 × 2^(scale-128)
  *           → fp32: exp_field = scale-1, mantissa = 0（隐含 1.0）
  *           expSigned = e + scale - bias = 0 + scale - 1 = scale - 1（公式天然覆盖）
  *   - overflow (exp>254)     → saturate: sign 保留, exp=254, mant=1
  *   - underflow (exp<1)      → FTZ: sign 保留, exp=0, mant=0
  */
class FpCvtMx4ToMid extends Module {
  val io = IO(new Bundle {
    val in    = Input(UInt(4.W))    // 原始 4-bit E2M1 元素
    val scale = Input(UInt(8.W))    // E8M0 scale
    val sign  = Output(UInt(1.W))   // → FpMulMx4.io.aSign/bSign
    val exp   = Output(UInt(8.W))   // → FpMulMx4.io.aExp/bExp
    val mant  = Output(UInt(1.W))   // → FpMulMx4.io.aMant/bMant
  })

  // E2M1 解包: {sign(1), exp(2), mant(1)}, bias=1
  val s  = io.in(3)
  val e2 = io.in(2, 1)
  val m1 = io.in(0)

  val isZero = (e2 === 0.U) && (m1 === 0.U)
  val isSub  = (e2 === 0.U) && (m1 =/= 0.U)   // e=0,m=1 → ±0.5（一等公民值）

  // exp_fp32 = e2 + scale - 1（bias=1）
  val expSum    = e2 +& io.scale              // 9-bit UInt
  val expSigned = expSum.zext - 1.S           // SInt（bias=1）
  val underflow = expSigned < 1.S
  val overflow  = expSigned > 254.S

  // E2M1 无 Inf/NaN 编码 → isInfNan = false（不需要检测）

  // 输出逻辑（与 Fp32.mxToFp32 subnormalFtz=false 路径逐位一致）：
  //   isZero      → ±0: exp=0, mant=0
  //   underflow   → ±0: exp=0, mant=0
  //   overflow    → saturate: exp=254, mant=1（fp32Max mantissa[22]=1）
  //   isSub       → fp32Sub: exp=expSigned(=scale-1), mant=0（隐含 1.0 表示 ±0.5 的值）
  //   normal      → exp=expSigned, mant=m1
  io.sign := s
  io.exp  := Mux(isZero || underflow, 0.U(8.W),
             Mux(overflow, 254.U(8.W),
               expSigned.asUInt(7, 0)))  // isSub 与 normal 共用同一 expSigned 公式
  io.mant := Mux(isZero || underflow, 0.U(1.W),
             Mux(overflow, 1.U(1.W),
               Mux(isSub, 0.U(1.W),     // subnormal: mant 强制为 0（隐含位 1.0 表示 0.5）
                 m1)))                   // normal: 直接传递 m1
}

// ============================================================================
// 5. FpCvtMx4ToMx8Mid — mxfp4 (E2M1) + E8M0 scale → FpMulMx8 中间表示
// ============================================================================

/** mxfp4 (E2M1) + E8M0 scale → FpMulMx8 中间表示（sign/exp/mant(3)）
  *
  * 输入:
  *   in    = 4-bit E2M1 元素 {s(1), e(2), m(1)}，bias=1
  *   scale = 8-bit E8M0 scale（bias=127）
  * 输出:
  *   sign(1) → FpMulMx8.io.aSign/bSign
  *   exp(8)  → FpMulMx8.io.aExp/bExp（fp32 偏置域）
  *   mant(3) → FpMulMx8.io.aMant/bMant（对应 fp32 mantissa[22:20]，E2M1 仅 bit22 有效）
  *
  * 与 FpCvtMx4ToMid 解码逻辑完全相同，区别仅在尾数输出宽度：
  *   mant(3) = Cat(m1, 0.U(2.W))（E2M1 的 1-bit 尾数左对齐到 3-bit）
  * 用途：当 E2M1 元素需送入 FpMulMx8（sigW=4 窄化阵列）而非 FpMulMx4 时使用。
  */
class FpCvtMx4ToMx8Mid extends Module {
  val io = IO(new Bundle {
    val in    = Input(UInt(4.W))    // 原始 4-bit E2M1 元素
    val scale = Input(UInt(8.W))    // E8M0 scale
    val sign  = Output(UInt(1.W))   // → FpMulMx8.io.aSign/bSign
    val exp   = Output(UInt(8.W))   // → FpMulMx8.io.aExp/bExp
    val mant  = Output(UInt(3.W))   // → FpMulMx8.io.aMant/bMant
  })

  // E2M1 解包: {sign(1), exp(2), mant(1)}, bias=1
  val s  = io.in(3)
  val e2 = io.in(2, 1)
  val m1 = io.in(0)

  val isZero = (e2 === 0.U) && (m1 === 0.U)
  val isSub  = (e2 === 0.U) && (m1 =/= 0.U)

  // exp_fp32 = e2 + scale - 1（bias=1）
  val expSum    = e2 +& io.scale              // 9-bit UInt
  val expSigned = expSum.zext - 1.S           // SInt
  val underflow = expSigned < 1.S
  val overflow  = expSigned > 254.S

  // 输出逻辑（与 FpCvtMx4ToMid 同源，尾数扩展到 3-bit）
  io.sign := s
  io.exp  := Mux(isZero || underflow, 0.U(8.W),
             Mux(overflow, 254.U(8.W),
               expSigned.asUInt(7, 0)))
  io.mant := Mux(isZero || underflow, 0.U(3.W),
             Mux(overflow, 7.U(3.W),          // fp32Max mantissa[22:20] = 0b111
               Mux(isSub, 0.U(3.W),           // subnormal: mant=0（隐含位 1.0）
                 Cat(m1, 0.U(2.W)))))         // normal: m1 左对齐到 3-bit
}

// ============================================================================
// 6. FpCvtMx8ToFp32 — mxfp8 (E4M3/E5M2) + E8M0 scale → 完整 fp32
// ============================================================================

/** mxfp8 (E4M3 或 E5M2) + E8M0 scale → 完整 32-bit fp32
  *
  * 用途：当 fp8 元素需复用满宽乘法器 FpMulFp16Bf16（输入为 fp32）时，
  *       通过本模块将 MX 元素 + scale 一步转换为标准 fp32。
  *
  * 输入:
  *   in     = 8-bit MX 元素（E4M3: {s(1),e(4),m(3)} 或 E5M2: {s(1),e(5),m(2)}）
  *   scale  = 8-bit E8M0 scale（纯指数，bias=127）
  *   isE5M2 = 格式选择（true=E5M2, false=E4M3）
  * 输出:
  *   out = 32-bit fp32 → 直接对接 FpMulFp16Bf16.io.a 或 .io.b
  *
  * 特殊值处理（与 Fp32.mxToFp32 subnormalFtz=true 路径逐位一致）：
  *   - zero (e=0,m=0)         → ±0
  *   - subnormal (e=0,m≠0)   → FTZ: ±0
  *   - InfNan                 → fp32Max: Cat(s, 0xFE, 0x7FFFFF)
  *   - overflow (exp>254)     → fp32Max
  *   - underflow (exp<1)      → ±0
  */
class FpCvtMx8ToFp32 extends Module {
  val io = IO(new Bundle {
    val in     = Input(UInt(8.W))   // 原始 8-bit MX 元素 (E4M3 或 E5M2)
    val scale  = Input(UInt(8.W))   // E8M0 scale
    val isE5M2 = Input(Bool())      // 格式选择: true=E5M2, false=E4M3
    // 消费有效：仅门控下方 inf/nan 断言，不入数据通路
    val consumeValid = Input(Bool())
    val out    = Output(UInt(32.W)) // 完整 fp32 → FpMulFp16Bf16.io.a/b
  })

  // 公共常量
  val fp32Zero = Cat(io.in(7), 0.U(31.W))                                // ±0（sign 保留）
  val fp32Max  = Cat(io.in(7), 0xFE.U(8.W), ((1 << 23) - 1).U(23.W))     // saturate: exp=254, mant=0x7FFFFF

  // ========== E4M3 路径 (expW=4, mantW=3, bias=7, nanNeedMantAllOne=true, subnormalFtz=true) ==========
  val sE4M3 = io.in(7)
  val e4    = io.in(6, 3)           // 4-bit exponent
  val m3    = io.in(2, 0)           // 3-bit mantissa

  val isZeroE4M3   = (e4 === 0.U) && (m3 === 0.U)
  val isSubE4M3    = (e4 === 0.U) && (m3 =/= 0.U)
  // E4M3 NaN: exp 全 1 且 mant 全 1（e4=15, m3=7）
  val isInfNanE4M3 = e4.andR && m3.andR

  // 尾数左对齐到 fp32 的 23-bit
  val m23E4M3 = Cat(m3, 0.U(20.W))

  // exp_fp32 = e4 + scale - 7（bias=7）
  val expSumE4M3    = e4 +& io.scale             // 9-bit UInt
  val expSignedE4M3 = expSumE4M3.zext - 7.S      // SInt
  val underflowE4M3 = expSignedE4M3 < 1.S
  val overflowE4M3  = expSignedE4M3 > 254.S

  val fp32NormalE4M3 = Cat(sE4M3, expSignedE4M3.asUInt(7, 0), m23E4M3)

  // E4M3 输出（subnormalFtz=true: zero/sub → ±0）
  val outE4M3 = Mux(isZeroE4M3 || isSubE4M3, fp32Zero,
                Mux(isInfNanE4M3 || overflowE4M3, fp32Max,
                  Mux(underflowE4M3, fp32Zero, fp32NormalE4M3)))

  // ========== E5M2 路径 (expW=5, mantW=2, bias=15, nanNeedMantAllOne=false, subnormalFtz=true) ==========
  val sE5M2 = io.in(7)
  val e5    = io.in(6, 2)           // 5-bit exponent
  val m2    = io.in(1, 0)           // 2-bit mantissa

  val isZeroE5M2   = (e5 === 0.U) && (m2 === 0.U)
  val isSubE5M2    = (e5 === 0.U) && (m2 =/= 0.U)
  // E5M2 Inf/NaN: exp 全 1（e5=31）
  val isInfNanE5M2 = e5.andR

  // 尾数左对齐到 fp32 的 23-bit
  val m23E5M2 = Cat(m2, 0.U(21.W))

  // exp_fp32 = e5 + scale - 15（bias=15）
  val expSumE5M2    = e5 +& io.scale             // 9-bit UInt
  val expSignedE5M2 = expSumE5M2.zext - 15.S     // SInt
  val underflowE5M2 = expSignedE5M2 < 1.S
  val overflowE5M2  = expSignedE5M2 > 254.S

  val fp32NormalE5M2 = Cat(sE5M2, expSignedE5M2.asUInt(7, 0), m23E5M2)

  // E5M2 输出（subnormalFtz=true: zero/sub → ±0）
  val outE5M2 = Mux(isZeroE5M2 || isSubE5M2, fp32Zero,
                Mux(isInfNanE5M2 || overflowE5M2, fp32Max,
                  Mux(underflowE5M2, fp32Zero, fp32NormalE5M2)))

  // ========== 输出选择 ==========
  io.out := Mux(io.isE5M2, outE5M2, outE4M3)

  // ========== inf/nan 守护（与 HiveCvtOpMx 同源；E2M1 不经本路径） ==========
  val selInfNan = Mux(io.isE5M2, isInfNanE5M2, isInfNanE4M3)
  val consumeValidEff = WireDefault(false.B)
  consumeValidEff := io.consumeValid
  assert(!consumeValidEff || !selInfNan,
    "FpCvtMx8ToFp32: MX element is inf/nan (saturated to fp32 max); indicates software-side anomaly")
}

// ============================================================================
// 7. FpCvtMx4ToFp32 — mxfp4 (E2M1) + E8M0 scale → 完整 fp32
// ============================================================================

/** mxfp4 (E2M1) + E8M0 scale → 完整 32-bit fp32
  *
  * 用途：当 fp4 元素需复用满宽乘法器 FpMulFp16Bf16（输入为 fp32）时，
  *       通过本模块将 MX 元素 + scale 一步转换为标准 fp32。
  *
  * 输入:
  *   in    = 4-bit E2M1 元素 {s(1), e(2), m(1)}，bias=1
  *   scale = 8-bit E8M0 scale（bias=127）
  * 输出:
  *   out = 32-bit fp32 → 直接对接 FpMulFp16Bf16.io.a 或 .io.b
  *
  * E2M1 格式特性：
  *   - 无 Inf/NaN 编码（noInfNan=true）：码 0x7 = ±6.0 是最大有限值
  *   - subnormal 不 FTZ（subnormalFtz=false）：e=0,m=1 = ±0.5 是一等公民值
  *
  * 特殊值处理（与 Fp32.mxToFp32 noInfNan=true, subnormalFtz=false 路径逐位一致）：
  *   - zero (e=0,m=0)         → ±0
  *   - subnormal (e=0,m=1)    → fp32Sub: Cat(s, expSigned(7,0), 0.U(23.W))
  *     推导：真值 = 0.5 × 2^(scale-127) = 1.0 × 2^(scale-128)
  *           expSigned = 0 + scale - 1 = scale - 1（公式天然覆盖）
  *   - overflow (exp>254)     → fp32Max
  *   - underflow (exp<1)      → ±0
  */
class FpCvtMx4ToFp32 extends Module {
  val io = IO(new Bundle {
    val in    = Input(UInt(4.W))    // 原始 4-bit E2M1 元素
    val scale = Input(UInt(8.W))    // E8M0 scale
    val out   = Output(UInt(32.W))  // 完整 fp32 → FpMulFp16Bf16.io.a/b
  })

  // E2M1 解包: {sign(1), exp(2), mant(1)}, bias=1
  val s  = io.in(3)
  val e2 = io.in(2, 1)
  val m1 = io.in(0)

  val isZero = (e2 === 0.U) && (m1 === 0.U)
  val isSub  = (e2 === 0.U) && (m1 =/= 0.U)   // e=0,m=1 → ±0.5（一等公民值）

  // E2M1 无 Inf/NaN 编码 → isInfNan 恒 false，不需要检测

  // 尾数左对齐到 fp32 的 23-bit
  val m23 = Cat(m1, 0.U(22.W))

  // exp_fp32 = e2 + scale - 1（bias=1）
  val expSum    = e2 +& io.scale              // 9-bit UInt（2-bit +& 8-bit → max 9-bit）
  val expSigned = expSum.zext - 1.S           // SInt
  val underflow = expSigned < 1.S
  val overflow  = expSigned > 254.S

  // 输出常量
  val fp32Zero   = Cat(s, 0.U(31.W))                                // ±0
  val fp32Max    = Cat(s, 0xFE.U(8.W), ((1 << 23) - 1).U(23.W))     // saturate
  val fp32Normal = Cat(s, expSigned.asUInt(7, 0), m23)              // 正常规格化
  // subnormal 解码: 隐含位 1.0 + mant=0, exp=expSigned(=scale-1)
  val fp32Sub    = Cat(s, expSigned.asUInt(7, 0), 0.U(23.W))

  // 输出逻辑（与 Fp32.mxToFp32 subnormalFtz=false 路径逐位一致）
  io.out := Mux(isZero, fp32Zero,
             Mux(overflow, fp32Max,
               Mux(underflow, fp32Zero,
                 Mux(isSub, fp32Sub, fp32Normal))))
}
