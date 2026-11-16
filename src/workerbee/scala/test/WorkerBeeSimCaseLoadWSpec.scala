/** WorkerBee 水平 loadW 加载模式（loadWMode=1）WS 专用回归门禁。
  *
  * 存在理由：rank4（WS 权重双缓冲，方案 A′）将大改 mode 1 的权重装载路，需要一套
  *   **直接**覆盖该路的用例，而不是只靠 MX 用例间接命中。原骨架零注册用例。
  *
  * 权威布局映射（勿按直觉写反，详见 CalcConfig:499-523 / DmaRdOnly:37-47）：
  *   - loadWMode=1：B 按 **N×K 转置**存储（行=n、列=k）⟹ bRowOff = K × bEb，
  *     每 beat（totalN×bW = 256 bit）承载一个 N-column（固定 n、16 个 k）⟹ **连续取数沿 K 轴**。
  *   - loadWMode=0：B 按 K×N 行主序 ⟹ bRowOff = N × bEb，连续取数沿 N 轴（本 suite 不覆盖）。
  *   - bColTileOff 是**运行期量** = totalN × elemsPerLane(bFmt) × memElemBits/8
  *     （MX8=32 / MX4=32 / INT8=16），语义为 **k-tile 步进**。
  *     ⚠️ 原骨架注释写的 `totalN*K*(bW/8)=2048` 是**错的**（n-tile 周期语义），
  *     CalcConfig:535 明确警告勿用该式；本 suite 一律走 WbDebugModel 的同源闭式。
  *
  * 覆盖缺口针对性：
  *   ① INT8/INT4 显式 mode 1 —— QuickSpec:757 默认 `if(isMxAny) 1 else 0`，
  *      故非 MX 用例默认走 mode 0，INT 族的 mode 1 此前无直接覆盖。
  *   ② 多 kTile + 多 nTile + 大 M —— kTile 间 partial-sum 经 C buffer 回灌
  *      （Executor:296 的 cPush 连续推、:494/:520 的 needPartialSum 读回），
  *      是 A′ 预装载窗口与波前排空的主战场。
  *   ③ MX blk16/blk32 两档 —— scaleB demand 与 rank2b 同字复用 F 值不同。
  *   ④ 混配 stretch（MX W4A8 / INT W4A8）+ 五通道周期反压。
  *
  * 金锚由 WbDebugModel 从**张量语义**独立推导（C = A·B），禁止以 RTL 行为为拟合目标。
  * 测试名不含 loadW/osM1/bpPeriod ⟹ 每个用例必须带唯一 caseTag 防撞车。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file._
import scala.collection.mutable

class WorkerBeeSimCaseLoadWSpec extends WorkerBeeCaseSpec {

  override protected def TAG: String = "LOADW"

  behavior of "WorkerBee LoadW-mode GEMM Simulation"

  // ══════════ ① INT 族显式 mode 1（真实覆盖缺口：非 MX 默认走 mode 0）══════════
  // K=128：INT8 kPerBeat=32 ⟹ 4 kTile；N=32 ⟹ 2 nTile。多 kTile 触发 C 回灌。
  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.INT8, bFmt = DataFormat.INT8, cFmt = DataFormat.INT32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw1_int8_ws.fst", caseTag = " [LW1 INT8 WS mode1 multi-kTile]")

  // INT4 kPerBeat=64 ⟹ 2 kTile；quad-lane 打包 + mode 1 水平装载。
  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.INT4, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw2_int4_ws.fst", caseTag = " [LW2 INT4 WS mode1 quad-lane]")

  // ══════════ ② MX8 两档 blk（scaleB demand 2 / 1，rank2b F=2 / F=4）══════════
  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw3_mx8_ws_blk16.fst", caseTag = " [LW3 MX8 WS blk16]")

  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
    aBlk = 32, bBlk = 32, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw4_mx8_ws_blk32.fst", caseTag = " [LW4 MX8 WS blk32]")

  // ══════════ ③ MX4 两档 blk（kPerBeat=64 ⟹ 2 kTile；demand 4 / 2）══════════
  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.MXE2M1, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw5_mx4_ws_blk16.fst", caseTag = " [LW5 MX4 WS blk16]")

  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.MXE2M1, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
    aBlk = 32, bBlk = 32, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw6_mx4_ws_blk32.fst", caseTag = " [LW6 MX4 WS blk32]")

  // ══════════ ④ 混配 stretch（B 侧 4-bit 展开，pair 同字复用）══════════
  // MX W4A8：K%64==0 且 bAddr%32==0 由 errCode 8 守护；128 满足。
  runCase(m = 2, n = 32, k = 128, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw7_mx_w4a8_ws.fst", caseTag = " [LW7 MX W4A8 WS stretch pair]")

  // INT W4A8：同 stretch 路但无 scale 通道。
  runCase(m = 2, n = 32, k = 128, aFmt = DataFormat.INT8, bFmt = DataFormat.INT4, cFmt = DataFormat.INT32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw8_int_w4a8_ws.fst", caseTag = " [LW8 INT W4A8 WS stretch pair]")

  // ══════════ ⑤ 大 M + 多 nTile + 多 kTile（A′ 预装载窗口 m+17 的主战场）══════════
  // N=64 ⟹ 4 nTile；K=256 MX8 ⟹ 8 kTile；M=8/16 覆盖 rank4 最值钱区间与增益拐点。
  runCase(m = 8, n = 64, k = 256, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw9_mx8_ws_m8.fst", caseTag = " [LW9 MX8 WS M8 N64 K256]")

  runCase(m = 16, n = 64, k = 256, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false,
    wave = "sim/loadw/lw10_mx8_ws_m16.fst", caseTag = " [LW10 MX8 WS M16 N64 K256]")

  // ══════════ ⑥ 五通道周期反压（供数减速下预蓄门控与逐拍节奏）══════════
  runCase(m = 8, n = 64, k = 256, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE4M3, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false, bpPeriod = 5,
    wave = "sim/loadw/lw11_mx8_ws_m8_bp5.fst", caseTag = " [LW11 MX8 WS M8 bp5]")

  runCase(m = 4, n = 32, k = 128, aFmt = DataFormat.MXE4M3, bFmt = DataFormat.MXE2M1, cFmt = DataFormat.FP32,
    aBlk = 16, bBlk = 16, cBlk = 16, addrMode = AddrMode.MAT, loadW = Some(1), osM1 = false, bpPeriod = 7,
    wave = "sim/loadw/lw12_mx_w4a8_ws_bp7.fst", caseTag = " [LW12 MX W4A8 WS bp7]")
}