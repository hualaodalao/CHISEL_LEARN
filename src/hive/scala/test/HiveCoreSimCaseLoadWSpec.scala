/** HiveCore 水平 loadW 加载模式完整仿真用例：【#66 起本 suite 无注册用例，详见下方移除记录】
  *
  * ══════════════════════════════════════════════════════════════════════════
  * 【#66 移除记录 —— FP16/BF16 已从 HiveCore 支持格式集中砍除，本 suite 唯一用例作废】
  *
  * ■ 裁定与配置变更（用户裁定，本文件无权改动 HiveCoreConfig）
  *   HiveCoreConfig.supportedFmts 默认集已由
  *     {FP16, BF16, INT16, INT8, MXE4M3, MXE5M2, MXE2M1}
  *   收窄为
  *     {INT8, INT4, MXE4M3, MXE5M2, MXE2M1}
  *   ⟹ hasFp = contains(FP16) || contains(BF16) = 【false】。
  *
  * ■ 为什么是【静默】算错（实测签名）
  *   hasFp=false ⟹ 浮点通路不 elaborate ⟹ HiveWorkUnit 的 baseSel 退化为
  *   (false,false) => io.cReg ⟹ FP16 输入用例输出全零，STATUS.err 不置位、
  *   EXECUTE 正常 done，只在末尾数值对账断言炸。
  *   本 suite 唯一用例的输入格式取证：原 :65-66 激励经 HiveSimCommon.floatToFp16 编码，
  *   原 :257 `val fmt = (1L << 0) | (1L << 4) | (1L << 8)` 写入 regs(7)
  *   ⟹ aFmt=FP16(1) | bFmt=FP16(1) | cFmt=FP16(1)，输入输出均为 FP16。
  *   移除前实测：`*** FAILED *** 1024 was not equal to 0 (:477)`，
  *   Numerical check vs H0: 1024 elems, mismatches=1024, maxRelErr=1.00e+00
  *   （即 hw 全零、1024 个元素全失配）。
  *
  * ■ 已移除的用例（行号为移除前版本，即当前 HEAD）
  *   :47-478  it should "run loadW-mode GEMM M=32 N=32 K=64 FP16 totalN=16"
  *            （全内联测试体，含 :177-178 cAddrSeqDense/cBeatsPerRow 的 outFmt=FP16、
  *              :415 parseDenseCWriteLog 的 outFmt=FP16、:430 golden 经 swFp32ToFp16、
  *              :470-474 copyTrace 的 FST 转存）
  *   本文件没有私有 runner（唯一用例是内联的），故无连带删除项。
  *
  * ■ 附带发现（随用例一并消失，此处留痕备查）
  *   原 :253-254 注释写「regs(7) = 0（aFmt=FP16、rnd=RNE 均为缺省 0）」，与紧邻的
  *   :257 实际代码 `fmt = (1L<<0)|(1L<<4)|(1L<<8)` 自相矛盾：#97 把 DataFormat
  *   重编号为 FP32=0/FP16=1/BF16=2/… 之后 FP16 已是 1 而非 0，代码当时改对了、
  *   注释没跟上。该过时注释属被移除用例的一部分，未单独修（无存续价值）。
  *
  * ■ 刻意保留未动的部分
  *   · 类骨架 / imports / 下方 vals（M、N、K、三个 BASE、simDir）/ behavior of：
  *     suite 身份与将来重写用例的起点，不含 FP16 语义。
  *   · 下方原有 loadW 架构文档（regs(6)(28)=1、B 按 N×K 转置、bRowOff/bColTileOff、
  *     blockStart 闭式、块内逐 beat -bRowOff 降序）：【与数据格式无关】，按仍支持
  *     格式（INT8/INT4/MX 族）重写 loadW 用例时逐条仍适用，故原样保留。
  *   · HiveSimCommon 的 FP16/BF16 位转换 helper：公共基础设施，仍被
  *     HiveCoreSimCaseMxSpec（MX 输入 → FP16 输出，2/2 绿）与 HiveDebugModel 使用。
  *
  * ■ ★ 关键区分：被砍的是 FP16/BF16 作【输入/计算】格式，不是作【输出】格式
  *   16-bit 输出走 HiveOutCvt/FpCvt 的 40→32→16 两级舍入链，不依赖 hasFp，仍可用。
  *
  * ■ INT16 排查（同批裁定：Hive 不再支持 INT16）
  *   本 suite 从未注册过 INT16 用例（移除前 grep 全文 0 命中 INT16）⟹ 无红、无需移除。
  *
  * ■ 恢复方式（本文件移除前与 HEAD 逐字一致、未被任何 WIP 修改，可完整取回）
  *   git show HEAD:src/hive/scala/test/HiveCoreSimCaseLoadWSpec.scala
  * ══════════════════════════════════════════════════════════════════════════
  *
  * 以下为原有 suite 文档（loadW 模式机制，与格式无关，原样保留）：
  *
  * 与垂直模式 HiveCoreSimCase 完全同构的回归门禁，唯一差异：
  *   - regs(6)(28) = 1（loadWMode=1，新 REG_CONTROL 布局）：权重经 bPop → hiveAIn 的 a 数据链
  *     水平加载（loadW 脉冲链移位 + 末拍 loadWInLock 广播锁存）
  *   - 内存中 B 矩阵按 N×K 转置存储（行=n、列=k）：
  *       bRowOff     = K * (bW/8)            = 128（K=64）
  *       bColTileOff = totalN * K * (bW/8)   = 2048（K=64）
  *   - dmaB 期望地址闭式（两套跳转机制分别建模，不可压成单变量递增）：
  *       blockStart(r, kt) = B_BASE + r*bColTileOff + (totalN-1)*bRowOff
  *                           + kt*totalN*(bW/8)
  *     块内逐 beat -bRowOff（降序，为水平加载对角线落位服务）
  *   - 落位映射与垂直模式一致：PE(x,y).wReg = B[kTile*totalN+x][nTile*totalN+y]
  *     → C 期望与垂直用例完全相同（H0: C=A·B 零 mismatch）
  *
  * 既有 spec（HiveCoreSimCase/MultiPass/Probe/HiveCoreSpec/SystolicArray*）
  * 一律零改动——垂直模式行为 bit-exact 由它们回归守护。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class HiveCoreSimCaseLoadWSpec extends AnyFlatSpec with Matchers with ChiselSim with HiveFstSim with HiveStatusPollApi {

  // ========== 测试参数（与垂直 SimCase 一致，C 期望相同；保留供将来重写用例复用） ==========
  val M = 32
  val N = 32
  val K = 64

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/loadw"

  // 工具方法（floatToFp16/fp16ToFloat/writeMatrixFloat/writeBigIntMatrixHex/
  // extractBits/fp32BitsToFloat）统一收敛至公共类 HiveSimCommon（限定调用）

  // ========== 主测试 ==========
  // 【#66】此处原有唯一一条 FP16 输入的 it should 注册（:47-478，全内联）已移除，
  //   本 suite 当前注册 0 条用例（ScalaTest 下为正常 completed、非 aborted）。
  //   移除理由、红集证据（1024 was not equal to 0）、原行号与恢复方式见文件头
  //   「#66 移除记录」。要新增 loadW 用例请只用仍受支持的输入格式：
  //   INT8 / INT4 / MXE4M3 / MXE5M2 / MXE2M1（注意 MX 输入本就要求 loadWMode=1）。

  behavior of "HiveCore LoadW-mode GEMM Simulation"
}
