/** WorkerBee 完整仿真用例（垂直加载模式）骨架，当前零注册用例。
  *
  * 验证 FSM 完整流程：寄存器配置 → EXECUTE → DMA 加载/存储 → 完成。
  * 外部存储器由测试 Memory Model 模拟（地址寻址）：
  *   - 软件侧按与硬件相同的地址公式逐 beat 推导期望地址（地址步进全部由几何参数
  *     推导）；dmaA/dmaB 响应时先读 cmd.addr 与期望对账（未知/不符地址直接报错），
  *     再供对应行数据，防止「按序供数掩盖地址公式 bug」
  *   - dmaC 逐 beat 校验写地址/len 后记录，C 矩阵重建同时依赖地址对账与严格 FIFO 顺序
  *
  * 寄存器布局：reg0=m、reg1=n|k<<16（读回 Cat(k,n)）、reg2/3/4=a/b/cAddr、
  *   reg5=控制字（含 loadWMode/三 MxBlk/三 addrMode/rnd/三 fmt，完整位域见
  *   WbDebugModel.controlWord5）、reg8=STATUS、reg9/0A/0B=三 scale 基址。
  * 新增用例只能用受支持的输入格式：INT8 / INT4 / MXE4M3 / MXE5M2 / MXE2M1
  *   （输出侧 FP32 / FP16 / BF16 / MX 族 / INT 族均可选）。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class WorkerBeeSimCase extends AnyFlatSpec with Matchers with ChiselSim with WbFstSim with WbStatusPollApi {

  // ========== 测试参数 ==========
  val M = 32
  val N = 32
  val K = 32

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim"

  // 工具方法（floatToFp16/fp16ToFloat/writeMatrix*/writeBigIntMatrixHex/
  // extractBits/fp32BitsToFloat）统一收敛至公共类 WbSimCommon（限定调用）

  behavior of "WorkerBee GEMM Simulation"
}
