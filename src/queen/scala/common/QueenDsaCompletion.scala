import chisel3._

/** 设备执行完毕回传给 Queen 的完成包，接口方向为 Flipped(Valid(...))：Queen 无条件接收。
  *
  * 每端点每拍至多一条；两端点可同拍各回一条，Queen 必须同拍全部吸收。
  * pc/cmdTag/swTag 由设备在 start 当拍采样后原样带回，供未来 Queen 侧统一 trace 管理使用。
  */
class QueenDsaCompletion(val cfg: QueenConfig) extends Bundle {
    val pc = UInt(cfg.pcWidth.W)
    val cmdTag = UInt(8.W)
    val swTag = UInt(cfg.xlen.W)
    val error = Bool()
    val code = UInt(cfg.faultCodeWidth.W)
    val hwSignal = new QueenHwSignal
}