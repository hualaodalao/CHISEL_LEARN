/** ForagerBee Stream 总线接口定义。
  *
  * 4 条独立 Stream 通道替代 AXI4 五通道：
  *   - wr（写通道）：地址+数据合并，首拍(sof=1)带addr+len+data，后续拍只data，末拍eof=1
  *   - wrResp（写响应）：Slave 每 burst 返回一次确认
  *   - rd（读请求）：addr + burst 长度
  *   - rdData（读数据）：len+1 拍数据流，首拍 sof=1，末拍 eof=1
  *
  * 方向约定（与项目 IMasterSlave 一致）：
  *   - Bundle 一律按 master 视角声明方向；
  *   - master 驱动的通道直接声明 Stream（valid/payload 为 Output）；
  *   - slave 驱动的通道用 Flipped(Stream(...))（反转方向）。
  *   - 实例化时：`master(new FbStreamBus(...))` 保持原样；
  *     `slave(new FbStreamBus(...))` 整体翻转。
  */

import chisel3._
import chisel3.util._

/** 写通道 payload：地址+数据合并。
  * 首拍 sof=1 时 addr/len 有效；后续拍 sof=0，addr/len 被 slave 忽略。
  * 末拍 eof=1 标记突发结束。
  */
class FbWriteBeat(dataWidth: Int, addressWidth: Int) extends Bundle {
  val addr = UInt(addressWidth.W)
  val len  = UInt(8.W)
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
  val sof  = Bool()
  val eof  = Bool()
}

/** 写响应 payload */
class FbWriteResp extends Bundle {
  val err = Bool()
}

/** 读请求 payload */
class FbReadReq(addressWidth: Int) extends Bundle {
  val addr = UInt(addressWidth.W)
  val len  = UInt(8.W)
}

/** 读数据 payload */
class FbReadData(dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val sof  = Bool()
  val eof  = Bool()
  val err  = Bool()
}

/** ForagerBee Stream 总线（4 通道）。
  *
  * 按 master 视角声明：
  *   - wr, rd：master 驱动（Output valid/payload）
  *   - wrResp, rdData：slave 驱动（Flipped，即 Input valid/payload）
  *
  * @param dataWidth    数据位宽（bit）
  * @param addressWidth 地址位宽（bit）
  */
class FbStreamBus(dataWidth: Int, addressWidth: Int) extends Bundle with IMasterSlave {
  val wr     = Stream(new FbWriteBeat(dataWidth, addressWidth))
  val wrResp = Flipped(Stream(new FbWriteResp))
  val rd     = Stream(new FbReadReq(addressWidth))
  val rdData = Flipped(Stream(new FbReadData(dataWidth)))
}
