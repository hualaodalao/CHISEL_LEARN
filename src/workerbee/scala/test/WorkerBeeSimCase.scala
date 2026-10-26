/** WorkerBee 完整仿真用例（垂直加载模式）：【#66 起本 suite 无注册用例，详见下方移除记录】
  *
  * ══════════════════════════════════════════════════════════════════════════
  * 【#66 移除记录 —— FP16/BF16 已从 WorkerBee 支持格式集中砍除，本 suite 9 条用例全部作废】
  *
  * ■ 裁定与配置变更（用户裁定，本文件无权改动 WorkerBeeConfig）
  *   WorkerBeeConfig.supportedFmts 默认集已由
  *     {FP16, BF16, INT16, INT8, MXE4M3, MXE5M2, MXE2M1}
  *   收窄为
  *     {INT8, INT4, MXE4M3, MXE5M2, MXE2M1}
  *   ⟹ 派生谓词 hasFp = contains(FP16) || contains(BF16) = 【false】
  *   （同批 INT16 亦被移出，见下方「INT16 排查」小节。）
  *
  * ■ 为什么这些用例是【静默】算错而不是报错（实测，非推测）
  *   hasFp=false 时整条浮点通路不 elaborate，WbWorkUnit 的 baseSel 退化为
  *   (false, false) => io.cReg ⟹ 以 FP16 为【输入/计算格式】的用例输出累加器
  *   输入而非乘加结果，实际表现为 C 全零；STATUS.err 不置位、EXECUTE 正常 done，
  *   只在最后的数值对账断言上炸。本 suite 原有 9 条用例的 aFmt/bFmt 全部为 FP16
  *   （主用例与缩位族 regs(7)=0x11 → aFmt=FP16(1)|bFmt=FP16(1)；outFmt 族
  *   controlWord 的 aFmt=bFmt=FP16(1)），故 9/9 全红，签名逐条一致：
  *     · hw=0.000000 / ref≠0 / maxRelErr=1.00e+00 / mismatches=1024 或 991（共 1024）
  *     · strict anchor C(0,0) bits mismatch: 0 was not equal to 31743
  *   （991 而非 1024 的两条是 bf16 crafted 矩阵中本就有 33 个期望为 0 的
  *    饱和/FTZ/全零行锚点被 hw=0「蒙对」，不代表部分通路正常。）
  *   移除前实测基线：`testOnly *WorkerBeeSimCase*` → Total 12 tests,
  *   succeeded 2（全是 MxSpec）, failed 10（本 suite 9 + LoadWSpec 1）。
  *
  * ■ 已移除的 9 条用例（行号为移除前版本，即当前 HEAD）
  *   :40   "run complete GEMM M=32 N=16 K=16 FP16 totalN=16"   （内联测试体 :40-587）
  *   :600  "run shrunk C writeback E8 2beats per row"           → runShrunkGemm(cDmaExtW=256)
  *   :607  "run shrunk C writeback E4 4beats per row"           → runShrunkGemm(cDmaExtW=128)
  *   :935  "run fp16 out GEMM RNE full width"                   → runOutFmtGemm(FP16, rnd=0, 256)
  *   :942  "run fp16 out GEMM RTZ full width"                   → runOutFmtGemm(FP16, rnd=1, 256)
  *   :948  "run bf16 out GEMM RNE full width"                   → runOutFmtGemm(BF16, rnd=0, 256)
  *   :954  "run fp16 out GEMM RNE shrunk E8"                    → runOutFmtGemm(FP16, rnd=0, 128)
  *   :963  "run fp16 out GEMM RUP full width"                   → runOutFmtGemm(FP16, rnd=3, 256)
  *   :971  "run bf16 out GEMM RNE shrunk E8"                    → runOutFmtGemm(BF16, rnd=0, 128)
  *   连带删除两个【只】被上述用例调用、且内部硬编码 FP16 输入的私有 runner：
  *     :614-916   private def runShrunkGemm(...)  （:638-639 floatToFp16 激励、:766 regs(7)=0x11）
  *     :980-1327  private def runOutFmtGemm(...)  （:982 require(outFmt==FP16||BF16)、
  *                                                  :1020-1021 floatToFp16 激励、
  *                                                  :1143-1144 aFmt=bFmt=FP16(1)）
  *   这两段不是「被其它仍支持格式用例共享的 helper」——共享 helper 全在 WbSimCommon
  *   与 WbDebugModel，本文件内的 runner 是 FP16 用例的实现体，随用例一并作废。
  *
  * ■ 刻意保留未动的部分
  *   · 类骨架 / imports / 下方 vals（M、N、K、三个 BASE、simDir）/ behavior of：
  *     它们是 suite 身份与将来按仍支持格式（INT8/INT4/MXE4M3/MXE5M2/MXE2M1）重写
  *     用例的起点，本身不含任何 FP16 语义。
  *   · WbSimCommon 中的 FP16/BF16 位转换 helper（floatToFp16 / fp16ToFloat /
  *     swFp32ToFp16 / swFp32ToBf16 / fp32BitsToFloat 等）：公共基础设施，仍被
  *     WorkerBeeSimCaseMxSpec（MX 输入 → FP16 输出，2/2 绿）与 WbDebugModel 金锚
  *     （decodeInElem / encodeElem / 输出 cvt 建模）使用，删除会连累其它编译。
  *
  * ■ ★ 关键区分：被砍的是 FP16/BF16 作【输入/计算】格式，不是作【输出】格式
  *   FP16/BF16 作输出走 WbOutCvt/FpCvt 的 40→32→16 两级舍入链，【不依赖 hasFp】，
  *   故仍然可用且实测为绿：WorkerBeeSimCaseMxSpec（outFmt=FP16）2/2 绿、
  *   WorkerBeeQuickSpec（MXE2M1×MXE2M1 → cFmt=BF16）1/1 绿。这两个 suite 一条用例
  *   都没删。将来若要恢复 16-bit 输出的端到端覆盖，请在 MX/INT 输入的 suite 里挂
  *   outFmt=FP16/BF16，而不是恢复本文件的 FP16 输入用例。
  *
  * ■ INT16 排查（同批裁定：WorkerBee 不再支持 INT16）
  *   本 suite 从未注册过 INT16 用例（移除前 grep 全文 0 命中 INT16）⟹ 无红、无需移除。
  *   测试基线里 INT16 只出现在 WbDebugModel/WbSimCommon 的模型与位宽表代码中。
  *
  * ■ 恢复方式（本文件移除前与 HEAD 逐字一致、未被任何 WIP 修改，可完整取回）
  *   git show HEAD:src/workerbee/scala/test/WorkerBeeSimCase.scala
  * ══════════════════════════════════════════════════════════════════════════
  *
  * 以下为原有 suite 文档：所述 FSM 流程与 DMA 地址对账机制【与数据格式无关】，
  * 按仍支持格式重写用例时仍然逐条适用，故原样保留。
  *
  * ★(#164) 寄存器布局已重排，重写用例时【不要】直接拷上方历史文本里的寄存器号：
  *   旧 reg1=n / reg2=k 已合并为 reg1 = n|k<<16（读回 Cat(k,n)）；旧 reg3/4/5=a/b/cAddr
  *   已下移为 reg2/3/4；旧 reg6（loadWMode/三 MxBlk/三 addrMode/三 stride）+ 旧 reg7
  *   （rnd/三 fmt）已合并为单一 reg5；旧 reg6/reg7 槽位已删除（main 侧 wregs/rregs 无
  *   is(6)/is(7) 分支 ⟹ 写为 no-op、读回 0）。reg0=m、reg8=STATUS、reg9/0A/0B=三 scale
  *   基址不变。新 reg5 完整位域见 WbDebugModel.controlWord5 的契约注释。
  *   aStride/bStride/cStride 三个字段已被 main 侧注释删除（WbInterface.scala）。
  *   ★本 suite 当前【零活跃注册用例】（已核实，见 #164 报告）⟹ 无活代码需要适配，
  *   本段仅为防止日后重写时沿用旧排布。
  *
  * 验证 FSM 完整流程：寄存器配置 → EXECUTE → DMA 加载/存储 → 完成
  * 外部存储器由测试 Memory Model 模拟（地址寻址）：
  *   - 软件侧按与硬件相同的地址公式（(#164) stride 寄存器已被 main 侧注释删除，
  *     旧文本「stride 寄存器仅 4bit，本用例退化为 0」已失效；地址步进全部由
  *     几何参数推导）逐 beat 推导期望地址；dmaA/dmaB 响应时先读 cmd.addr 与
  *     期望对账（未知/不符地址直接报错），再供对应行数据，防止
  *     「按序供数掩盖地址公式 bug」
  *   - dmaC 逐 beat 校验写地址/len 后记录，C 矩阵重建同时依赖
  *     地址对账与严格 FIFO 顺序
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class WorkerBeeSimCase extends AnyFlatSpec with Matchers with ChiselSim with WbFstSim with WbStatusPollApi {

  // ========== 测试参数（保留：与数据格式无关，供将来重写用例复用） ==========
  val M = 32
  val N = 32
  val K = 32

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim"

  // 工具方法（floatToFp16/fp16ToFloat/writeMatrix*/writeBigIntMatrixHex/
  // extractBits/fp32BitsToFloat）统一收敛至公共类 WbSimCommon（限定调用）

  // ========== 主测试 ==========
  // 【#66】此处原有 9 条 FP16 输入的 it should 注册（含缩位写回族与 outFmt 族）已
  //   全部移除，本 suite 当前注册 0 条用例（ScalaTest 下为正常 completed、非 aborted）。
  //   移除理由、红集证据、原行号与恢复方式见文件头「#66 移除记录」。
  //   要新增用例请只用仍受支持的输入格式：INT8 / INT4 / MXE4M3 / MXE5M2 / MXE2M1
  //   （输出侧 FP32 / FP16 / BF16 / MX 族 / INT 族仍可选）。

  behavior of "WorkerBee GEMM Simulation"
}
