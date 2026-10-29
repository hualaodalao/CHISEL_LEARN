/** WorkerBee 水平 loadW 加载模式完整仿真用例骨架，当前零注册用例。
  *
  * 与垂直模式 WorkerBeeSimCase 同构的回归门禁，唯一差异：
  *   - reg5[28] = 1（loadWMode=1）：权重经 bPop → wbAIn 的 a 数据链水平加载
  *     （loadW 脉冲链移位 + 末拍 loadWInLock 广播锁存）
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
  * 寄存器布局：reg0=m、reg1=n|k<<16、reg2/3/4=a/b/cAddr、reg5=控制字（loadWMode 在
  *   bit28，完整位域见 WbDebugModel.controlWord5）、reg8=STATUS、reg9/0A/0B=三 scale 基址。
  * 新增 loadW 用例只能用受支持的输入格式：INT8 / INT4 / MXE4M3 / MXE5M2 / MXE2M1
  *   （MX 输入本就要求 loadWMode=1）。
  */

import chisel3._
import chisel3.simulator.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io._
import java.nio.file._

class WorkerBeeSimCaseLoadWSpec extends AnyFlatSpec with Matchers with ChiselSim with WbFstSim with WbStatusPollApi {

  // ========== 测试参数（与垂直 SimCase 一致，C 期望相同） ==========
  val M = 32
  val N = 32
  val K = 64

  val A_BASE: Long = 0x00000000L
  val B_BASE: Long = 0x00100000L
  val C_BASE: Long = 0x00200000L

  val simDir = "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/sim/loadw"

  // 工具方法（floatToFp16/fp16ToFloat/writeMatrixFloat/writeBigIntMatrixHex/
  // extractBits/fp32BitsToFloat）统一收敛至公共类 WbSimCommon（限定调用）

  behavior of "WorkerBee LoadW-mode GEMM Simulation"
}
