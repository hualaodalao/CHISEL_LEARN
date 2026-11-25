import chisel3._

/** 设备执行完毕回传给 Queen 的完成包，接口方向为 Flipped(Valid(...))：Queen 无条件接收。
  *
  * 每端点每拍至多一条；GEMM/DMA/VPU 可同拍各回一条，Queen 必须同拍全部吸收。
  * swTag 由设备在 start 当拍采样后原样带回，hwTag 由设备自行填写，两者供未来 trace 管理使用。
  */
class QueenDsaCompletion(val cfg: QueenConfig) extends Bundle {
    val swTag = UInt(cfg.xlen.W)
    val hwTag = UInt(cfg.xlen.W)
    val error = Bool()
    val code = UInt(cfg.faultCodeWidth.W)
    val hwSignal = new QueenHwSignal
}

/** TRACE_TAG 在 Queen 派发口被消费后就地吐出的记录，不下发给设备。hwTag 预留给未来 trace 管理。 */
class QueenTraceRecord(val cfg: QueenConfig) extends Bundle {
    val swTag = UInt(cfg.xlen.W)
    val hwTag = UInt(cfg.xlen.W)
}