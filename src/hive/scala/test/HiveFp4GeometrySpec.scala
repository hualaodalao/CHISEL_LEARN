/** HiveFp4GeometrySpec — Phase 3 fp4(E2M1) 地址/几何 bits 口径一致性 + 现有格式非回归自证。
  *
  * ═══════════════════════════════════════════════════════════════════════════
  * 本文件锁死的是什么
  * ═══════════════════════════════════════════════════════════════════════════
  * Phase 3 让「地址/几何」正确供给 fp4 quad-lane：memElemBits/elemsPerLane/colTile/
  * getKLoadNum/kTile 对 fp4 走 bits 口径（additive），对现有格式【逐值不变】。本 spec
  * 从四个层面正向验证（张量语义推导，非 RTL 拟合）：
  *
  *   A. 软件几何恒等性：字节对齐族 memElemBits == memElemBytes×8；bits 口径 colTile
  *      == bytes 口径 colTile（现有格式恒等）；fp4 得 memElemBits=4 / elemsPerLane=4 /
  *      kPerBeat=totalN×4 / colTile=16×4×4/8=32。
  *   B. decode 双域分离：MXE2M1【输入】域 decodeMxE2M1Input 产出 ±0.5（e=0/m=1 不 flush，
  *      对齐硬件 Fp32.mxE2M1ToFp32）；【输出量化】域 decodeElem 仍 flush（e==0→0.0）；
  *      decodeInElem 对非 MXE2M1 格式与 decodeElem 逐值相同（非回归）。
  *   C. 硬件 elaboration smoke：hasMx4=true 配置下 HiveCoreCalcConfig（gated errCode7 +
  *      bits colTile）与 HiveCoreExecutor2（gated kTile /elemsPerLane）均能 elaborate
  *      成良构 CHIRRTL（证明 additive 分支硬件well-formed；hasMx4=false 基线的
  *      normalized-diff=0 由 build/p3_diff.sh 独立复核）。
  *   D. 硬件功能 sim：HiveCoreCalcConfig(hasMx4=true) 对 fp4 实算 aColTile=32 / kTile=1 /
  *      aRow=k/2；block≠64 触发 errCode7；MX8 在同配置下 colTile/kTile/errCode 逐值不变。
  *
  * 运行：CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  *        sbt -J-Xmx6G "testOnly HiveFp4GeometrySpec"
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import _root_.circt.stage.ChiselStage

class HiveFp4GeometrySpec extends AnyFlatSpec with Matchers with ChiselSim {

  import HiveDebugModel.{isMx8, memElemBytes, memElemBits, elemsPerLane, kPerBeat,
    decodeElem, decodeInElem, decodeMxE2M1Input}
  import DataFormat.{FP32, FP16, BF16, INT8, INT16, INT32, MXE4M3, MXE5M2, MXE2M1, INT4}

  private val totalN = 16

  /** hasMx4=true 配置（supportedFmts 含 MXE2M1）；几何/缓冲参数对齐 cfgMx，仅追加 fp4。 */
  private def cfgFp4 = HiveCoreConfig(
    arrayN = 8, clusterM = 2,            // totalN = 16
    aW = 16, bW = 16, cW = 40,
    supportedFmts = Set(DataFormat.MXE4M3, DataFormat.MXE5M2, DataFormat.MXE2M1),
    aBufferDepth = 2048, bBufferDepth = 2048, cBufferDepth = 2048, scaleBufferDepth = 256
  )

  // ==========================================================================
  // A. 软件几何恒等性
  // ==========================================================================
  behavior of "Phase3 fp4 geometry (software)"

  it should "keep memElemBits == memElemBytes*8 for byte-aligned formats; fp4=4bits/0bytes" in {
    for (f <- Seq(FP16, BF16, INT16, INT8, INT32, MXE4M3, MXE5M2))
      memElemBits(f) shouldBe memElemBytes(f) * 8
    memElemBits(MXE2M1) shouldBe 4
    memElemBytes(MXE2M1) shouldBe 0
    memElemBits(INT4) shouldBe 4
  }

  it should "give elemsPerLane fp4=4, MX8=2, others=1 (additive; existing formats unchanged)" in {
    elemsPerLane(MXE2M1) shouldBe 4
    elemsPerLane(MXE4M3) shouldBe 2
    elemsPerLane(MXE5M2) shouldBe 2
    for (f <- Seq(FP16, BF16, INT16, INT8, INT32)) elemsPerLane(f) shouldBe 1
  }

  it should "give fp4 kPerBeat=totalN*4, colTile bits口径 fp4=32, and bits==bytes identity for byte-aligned" in {
    kPerBeat(MXE2M1, totalN) shouldBe totalN * 4      // = 64 K/pass
    // fp4 colTile = totalN × elemsPerLane × memElemBits / 8 = 16×4×4/8 = 32
    (totalN * elemsPerLane(MXE2M1) * memElemBits(MXE2M1) / 8) shouldBe 32
    // 字节对齐族：bits 口径 == bytes 口径（现有格式恒等，保 normalized-diff/金锚非回归）
    for (f <- Seq(FP16, BF16, INT16, INT8, INT32, MXE4M3, MXE5M2)) {
      val bitsColTile  = totalN * elemsPerLane(f) * memElemBits(f) / 8
      val bytesColTile = totalN * elemsPerLane(f) * memElemBytes(f)
      bitsColTile shouldBe bytesColTile
    }
    // 现有格式 colTile 期望值（与 #124 ① 恒等性证明逐一对齐）
    (totalN * elemsPerLane(MXE4M3) * memElemBytes(MXE4M3)) shouldBe 32   // MX8=32
    (totalN * elemsPerLane(FP16)   * memElemBytes(FP16))   shouldBe 32   // FP16=32
    (totalN * elemsPerLane(INT8)   * memElemBytes(INT8))   shouldBe 16   // INT8=16
    (totalN * elemsPerLane(INT32)  * memElemBytes(INT32))  shouldBe 64   // INT32=64
  }

  it should "mirror getKLoadNum/kTile: fp4->totalN*4 (K=64->1 kTile), MX8->totalN*2" in {
    // getKLoadNum 软件镜像（硬件：isMx8 ? totalN*2 : totalN*4；fp4 落 else）
    def kLoad(f: DataFormat.Type): Int = if (isMx8(f)) totalN * 2 else totalN * 4
    kLoad(MXE2M1) shouldBe totalN * 4    // = 64（巧合正确，非 isMx8）
    kLoad(MXE4M3) shouldBe totalN * 2    // = 32
    kLoad(FP16)   shouldBe totalN * 4
    // kTile 软件镜像（MX：k / kLoad）—— 与 Executor /elemsPerLane 泛化同口径
    (64 / kLoad(MXE2M1)) shouldBe 1      // fp4 K=64 → 1 kTile（= 1 pass = 1 scale 块）
    (32 / kLoad(MXE4M3)) shouldBe 1      // MX8 K=32 → 1 kTile
    // Executor kTile = k/(totalN×elemsPerLane) 等价性
    (64 / (totalN * elemsPerLane(MXE2M1))) shouldBe 1
    (32 / (totalN * elemsPerLane(MXE4M3))) shouldBe 1
  }

  // ==========================================================================
  // B. decode 双域分离
  // ==========================================================================
  it should "separate MXE2M1 input domain (±0.5 kept) from output domain (flushed)" in {
    // 输入域：e=0/m=1 = ±0.5 一等公民值，不 flush（与硬件 Fp32.mxE2M1ToFp32 同源）
    decodeMxE2M1Input(0x1L, 127) shouldBe 0.5
    decodeMxE2M1Input(0x9L, 127) shouldBe -0.5
    // 全幅值域 {0,±0.5,±1,±1.5,±2,±3,±4,±6}（scale=127）
    (0 until 8).map(c => decodeMxE2M1Input(c.toLong, 127)) shouldBe
      Seq(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0)
    // 输出量化域：decodeElem 仍 flush e==0→0.0（建模硬件 E2M1 minNormal=1.0 的 FTZ，勿改）
    decodeElem(MXE2M1, 0x1L, 127) shouldBe 0.0
    // decodeInElem 对 MXE2M1 路由到输入域（±0.5），与输出域 flush 显式区分
    decodeInElem(MXE2M1, 0x1L, 127) shouldBe 0.5
  }

  it should "keep decodeInElem == decodeElem for all non-MXE2M1 formats (non-regression)" in {
    val samples = Seq(
      (FP16, 0x3C00L, 127), (BF16, 0x3F80L, 127), (INT8, 0xFFL, 127),
      (INT16, 0xFFFFL, 127), (INT32, 0xFFFFFFFFL, 127),
      (MXE4M3, 0x7EL, 127), (MXE5M2, 0x7BL, 127), (MXE4M3, 0x33L, 120), (MXE5M2, 0x00L, 255))
    for ((f, b, s) <- samples) decodeInElem(f, b, s) shouldBe decodeElem(f, b, s)
  }

  // ==========================================================================
  // C. 硬件 elaboration smoke（hasMx4=true additive 分支良构）
  // ==========================================================================
  it should "elaborate hasMx4=true CalcConfig & Executor gated fp4 branches (CHIRRTL smoke)" in {
    val cc = ChiselStage.emitCHIRRTL(new HiveCoreCalcConfig(cfgFp4))
    cc.length should be > 0
    val ex = ChiselStage.emitCHIRRTL(new HiveCoreExecutor2(cfgFp4))
    ex.length should be > 0
  }

  // ==========================================================================
  // D. 硬件功能 sim：CalcConfig fp4 几何 + errCode7 + MX8 非回归
  // ==========================================================================
  it should "compute fp4 colTile=32/kTile=1/aRow=k/2, errCode7 on block!=64, MX8 unchanged" in {
    simulate(new HiveCoreCalcConfig(cfgFp4)) { dut =>
      dut.reset.poke(true.B)
      dut.io.startIn.poke(false.B)
      dut.clock.step()
      dut.reset.poke(false.B)

      // 一次 EXECUTE：poke regFile → startIn 脉冲 → sC0 计算 → sC1 锁存（calcConfig/err 为 Reg）
      def run(aFmt: DataFormat.Type, bFmt: DataFormat.Type, cFmt: DataFormat.Type,
              k: Int, loadW: Boolean,
              aBlk: MXBlockSize.Type, bBlk: MXBlockSize.Type): Unit = {
        dut.io.regFile.m.poke(32.U)
        dut.io.regFile.n.poke(32.U)
        dut.io.regFile.k.poke(k.U)
        dut.io.regFile.aFmt.poke(aFmt)
        dut.io.regFile.bFmt.poke(bFmt)
        dut.io.regFile.cFmt.poke(cFmt)
        dut.io.regFile.loadWMode.poke(loadW.B)
        dut.io.regFile.aMxBlockSize.poke(aBlk)
        dut.io.regFile.bMxBlockSize.poke(bBlk)
        dut.io.regFile.cMxBlockSize.poke(MXBlockSize.M8)
        dut.io.startIn.poke(true.B);  dut.clock.step()   // sIDLE/sDONE → sC0
        dut.io.startIn.poke(false.B); dut.clock.step()   // sC0 → sC1（锁存）
      }
      def toDone(): Unit = { dut.io.startIn.poke(false.B); dut.clock.step() }  // sC1 → sDONE

      // --- case 1: fp4 合法（block=64）→ colTile=32, kTile=1, aRow=k/2=32, 无错 ---
      run(MXE2M1, MXE2M1, FP32, 64, true, MXBlockSize.M64, MXBlockSize.M64)
      dut.io.calcConfig.aColTileAddressOffset.peek().litValue shouldBe 32   // 16×4×4/8
      dut.io.calcConfig.bColTileAddressOffset.peek().litValue shouldBe 32
      dut.io.calcConfig.kTile.peek().litValue shouldBe 1                    // 64/(16×4)
      dut.io.calcConfig.aRowAddressOffset.peek().litValue shouldBe 32       // 半字节档 k/2
      dut.io.errCode.peek().litValue shouldBe 0
      dut.io.err.peek().litValue shouldBe 0
      toDone()

      // --- case 2: fp4 block=32（≠64）→ errCode 7（v1 强制单 pass 单 scale 块）---
      run(MXE2M1, MXE2M1, FP32, 64, true, MXBlockSize.M32, MXBlockSize.M32)
      dut.io.errCode.peek().litValue shouldBe 7
      dut.io.err.peek().litValue shouldBe 1
      toDone()

      // --- case 3: MX8 在 hasMx4=true 同配置下逐值不变（bits 口径对字节对齐恒等）---
      run(MXE4M3, MXE4M3, FP32, 32, true, MXBlockSize.M32, MXBlockSize.M32)
      dut.io.calcConfig.aColTileAddressOffset.peek().litValue shouldBe 32   // 16×2×8/8=32
      dut.io.calcConfig.bColTileAddressOffset.peek().litValue shouldBe 32
      dut.io.calcConfig.kTile.peek().litValue shouldBe 1                    // 32/(16×2)
      dut.io.errCode.peek().litValue shouldBe 0
      dut.io.err.peek().litValue shouldBe 0
      toDone()

      // --- case 4: FP16 非 MX 在 hasMx4=true 同配置下 colTile=32（else 支不受 fp4 影响）---
      run(FP16, FP16, FP32, 32, false, MXBlockSize.M8, MXBlockSize.M8)
      dut.io.calcConfig.aColTileAddressOffset.peek().litValue shouldBe 32   // 16×1×16/8=32
      dut.io.calcConfig.kTile.peek().litValue shouldBe 2                    // ceil(32/16)
      dut.io.errCode.peek().litValue shouldBe 0
      toDone()

      // --- case 5【Fix-11】MX4×MX8 混配（MXE2M1 × MXE4M3）→ errCode 3（运行期可诊断守护）---
      //   钉死 Fix-4 新增的 isMx4(aFmt)^isMx4(bFmt) 守护：运行期选择子只看 aFmt、b 侧解码接
      //   bFmt，v1 混配会静默算错，故报 errCode3（暂复用 code3，W4A8 真支持属 v2）。
      //   aBlk=64（避开更高优先级的 errCode7）、bBlk=32（MX8 合法，bKLoadNum=32，不触 errCode0）；
      //   loadW=1（不触 errCode4）、cFmt=FP32（不触 errCode6）⟹ 唯一命中新 errCode3。
      run(MXE2M1, MXE4M3, FP32, 64, true, MXBlockSize.M64, MXBlockSize.M32)
      dut.io.errCode.peek().litValue shouldBe 3
      dut.io.err.peek().litValue shouldBe 1
      toDone()

      // --- case 6【Fix-12】优先级重叠：MXE2M1 + blk=32(≠64) + loadWMode=0 → errCode 7 ---
      //   fp4×fp4 场景（不涉及混配）：同时命中 errCode7（fp4 blk≠64）与 errCode4（MX & loadW=0）；
      //   硬件 last-connect 后写覆盖前写、errCode7 在裸块最末 ⟹ 真值优先级 7 > 4，最终报 7。
      //   钉死金锚方向（precheckConfigErr / hwErrCode 均已把 errCode7 置于最高优先级）。
      run(MXE2M1, MXE2M1, FP32, 64, false, MXBlockSize.M32, MXBlockSize.M32)
      dut.io.errCode.peek().litValue shouldBe 7
      dut.io.err.peek().litValue shouldBe 1
      toDone()
    }
  }
}
