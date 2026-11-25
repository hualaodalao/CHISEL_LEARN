/** WorkerBee 设备侧寄存器地址映射（Queen 通用 DSA 契约，queenHarewareInterface.md §13）。
  *
  * 配置区 `0x00–0x0B` 由 QueenDsaRegIf 的 shadow/live 双组承载（numCfgRegs=12），落在 QWR2 的
  *   6-bit regBase 窗口内可相邻配对写；状态区从 `0x40` 起（numStatusRegs=2：0x40 status、
  *   0x41 最近配置错误码）。中断/hwSignal/traceTag 走 QueenDsaRegIfAddr 的公共契约区
  *   （IntrRaw=0x60、IntrMask=0x68、HwSignalTarget=0x80、TraceTag=0xA0），不在本对象重复定义。
  *   启动由 QueenNativeOp.RUN 触发，无寄存器地址。
  */
object WorkerBeeRegAddr {
  val M = 0x00
  val NK = 0x01
  val A = 0x02
  val B = 0x03
  val C = 0x04
  val Control = 0x05
  val ScaleA = 0x06
  val ScaleB = 0x07
  val ScaleC = 0x08
  val Bias = 0x09
  val RqM = 0x0A
  val RqParam = 0x0B
  val Status = 0x40
  val ErrorCode = 0x41
}