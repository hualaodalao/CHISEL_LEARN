/** 可重构 MAC 单元：根据 fmt 选择整数或浮点乘累加路径
  *
  * 仅为 supportedFmts 中声明的格式生成对应硬件，节省面积。
  * BF16 / FP16 共用同一个 Fp32 乘法器（仅输入转换不同）。
  * INT8 / INT16 共用同一个 16×16 有符号乘法器（INT8 符号扩展到 16 位）。
  * 输入：aW-bit a, bW-bit b（格式由 fmt 决定）
  * 输出：cW-bit 乘积（整数为有符号积，浮点为 fp32 积）
  * 累加：cW-bit cReg（整数为有符号和，浮点为 fp32 和）
  * 舍入：rnd 控制浮点运算的舍入模式（脉动传入）
  */

import chisel3._
import chisel3.util._

object MacUnit {

  private val AllFmts = Set(DataFormat.FP16, DataFormat.BF16, DataFormat.INT16, DataFormat.INT8)

  /** 计算 a × b 的 cW-bit 结果
    * 仅为 supportedFmts 中的格式生成乘法器硬件，其余格式输出 0（don't-care，PE 层不会累加）
    * BF16/FP16 共用一个 Fp32.mul（输入转换由 Mux 选择）
    * INT8/INT16 共用一个 16×16 有符号乘法器（INT8 符号扩展）
    */
  def multiply(a: UInt, b: UInt, fmt: DataFormat.Type, aW: Int, bW: Int, cW: Int,
               supportedFmts: Set[DataFormat.Type] = AllFmts,
               rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {

    val candidates = scala.collection.mutable.ListBuffer[(Bool, UInt)]()

    // --- 整数路径（INT8 / INT16 共用一个 16×16 有符号乘法器） ---
    val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
    if (hasInt) {
      val a16W = math.min(16, aW)
      val b16W = math.min(16, bW)
      val aLow = if (a16W < 16) Cat(0.U((16 - a16W).W), a(a16W - 1, 0)) else a(15, 0)
      val bLow = if (b16W < 16) Cat(0.U((16 - b16W).W), b(b16W - 1, 0)) else b(15, 0)

      // INT8: 取低 8 位符号扩展到 16 位；INT16: 直接使用 16 位
      val aInt = Mux(fmt === DataFormat.INT8,
        Cat(Fill(8, aLow(7)), aLow(7, 0)), aLow).asSInt
      val bInt = Mux(fmt === DataFormat.INT8,
        Cat(Fill(8, bLow(7)), bLow(7, 0)), bLow).asSInt

      // 共用一个 16×16 乘法器
      val intProd = (aInt * bInt).asUInt

      val intCond = (supportedFmts.contains(DataFormat.INT8), supportedFmts.contains(DataFormat.INT16)) match {
        case (true, true)   => fmt === DataFormat.INT8 || fmt === DataFormat.INT16
        case (true, false)  => fmt === DataFormat.INT8
        case (false, true)  => fmt === DataFormat.INT16
        case _              => false.B
      }
      candidates += ((intCond, intProd))
    }

    // --- 浮点路径（BF16 / FP16 共用一个 Fp32 乘法器） ---
    val hasFp = supportedFmts.contains(DataFormat.BF16) || supportedFmts.contains(DataFormat.FP16)
    if (hasFp) {
      val aFpW = math.min(16, aW)
      val bFpW = math.min(16, bW)
      val aPad = if (aFpW < 16) Cat(0.U((16 - aFpW).W), a(aFpW - 1, 0)) else a(15, 0)
      val bPad = if (bFpW < 16) Cat(0.U((16 - bFpW).W), b(bFpW - 1, 0)) else b(15, 0)

      // 输入转换：根据 fmt 选择 bf16→fp32 或 fp16→fp32
      val aFp32 = Mux(fmt === DataFormat.BF16, Fp32.bf16ToFp32(aPad), Fp32.fp16ToFp32(aPad))
      val bFp32 = Mux(fmt === DataFormat.BF16, Fp32.bf16ToFp32(bPad), Fp32.fp16ToFp32(bPad))

      // 共用一个 Fp32 乘法器
      val fpProd = Fp32.mul(aFp32, bFp32, rnd)

      // 条件：BF16 或 FP16（取决于哪些被支持）
      val fpCond = (supportedFmts.contains(DataFormat.BF16), supportedFmts.contains(DataFormat.FP16)) match {
        case (true, true)   => fmt === DataFormat.BF16 || fmt === DataFormat.FP16
        case (true, false)  => fmt === DataFormat.BF16
        case (false, true)  => fmt === DataFormat.FP16
        case _              => false.B
      }
      candidates += ((fpCond, fpProd))
    }

    // 构建 Mux 树（不支持的格式 → 0，PE 层不会使用）
    val raw = buildMuxTree(candidates.toList, 0.U(32.W))
    fitToWidth(raw, cW, fmt, supportedFmts)
  }

  /** 累加：cReg + product（整数加法 / fp32 加法），结果 cW 位
    * 仅为 supportedFmts 中的格式生成对应加法路径
    * 整数路径：有符号加法后截取 cW 位
    * 浮点路径：对低 32 位做 Fp32.add 后零扩展到 cW（cW<32 时该路径不可达）
    */
  def accumulate(cReg: UInt, product: UInt, fmt: DataFormat.Type, cW: Int,
                 supportedFmts: Set[DataFormat.Type] = AllFmts,
                 rnd: RoundingMode.Type = RoundingMode.RNE): UInt = {
    val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
    val hasFp  = supportedFmts.contains(DataFormat.BF16) || supportedFmts.contains(DataFormat.FP16)

    def intAdd: UInt = {
      val sum = (cReg.asSInt + product.asSInt).asUInt
      sum(cW - 1, 0)
    }

    def fpAdd: UInt = {
      val fpResult = Fp32.add(cReg(31, 0), product(31, 0), rnd)
      if (cW == 32) fpResult
      else if (cW > 32) Cat(0.U((cW - 32).W), fpResult)
      else fpResult(cW - 1, 0) // 不可达（require 已拦截）
    }

    (hasInt, hasFp) match {
      case (true, false)  => intAdd
      case (false, true)  => fpAdd
      case (true, true)   =>
        val isInt = fmt === DataFormat.INT8 || fmt === DataFormat.INT16
        Mux(isInt, intAdd, fpAdd)
      case (false, false) => cReg
    }
  }

  /** 从 (condition, value) 列表构建 Mux 树，default 为兜底值 */
  private def buildMuxTree(cases: List[(Bool, UInt)], default: UInt): UInt = {
    cases match {
      case Nil           => default
      case (c, v) :: Nil => Mux(c, v, default)
      case (c, v) :: rest => Mux(c, v, buildMuxTree(rest, default))
    }
  }

  /** 将乘积适配到 cW 位宽 */
  private def fitToWidth(x: UInt, cW: Int, fmt: DataFormat.Type, supportedFmts: Set[DataFormat.Type]): UInt = {
    if (cW >= 32) {
      val x32 = if (x.getWidth < 32) x else x(31, 0)
      if (cW == 32) {
        x32
      } else {
        val hasInt = supportedFmts.contains(DataFormat.INT8) || supportedFmts.contains(DataFormat.INT16)
        val signBit = x32(x32.getWidth - 1)
        if (hasInt) {
          val isInt = fmt === DataFormat.INT8 || fmt === DataFormat.INT16
          Mux(isInt, Cat(Fill(cW - 32, signBit), x32), Cat(0.U((cW - 32).W), x32))
        } else {
          Cat(0.U((cW - 32).W), x32)
        }
      }
    } else {
      x(cW - 1, 0)
    }
  }
}
