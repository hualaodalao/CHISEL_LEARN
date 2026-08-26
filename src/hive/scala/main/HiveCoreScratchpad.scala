/** HiveCore 脉动阵列控制器的队列型缓冲系统。
  *
  * 包含三个独立的 FIFO 缓冲：
  *   - A buffer：激活数据（DMA0 写入，Executor 读出）
  *   - B buffer：权重数据（RdOnly DMA 自主扫描写入，Executor 读出，宽 totalN*bW）
  *   - C buffer：结果缓冲（Executor 写入，DMA1 写回/Executor partial sum 读出）
  */

import chisel3._
import chisel3.util._

class HiveCoreScratchpad(cfg: HiveCoreConfig) extends Module {
  val io = IO(new Bundle {
    // A buffer: DMA0写入, Executor读出
    val aPush = slave(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    val aPop  = master(Stream(UInt((cfg.totalN * cfg.aEffW).W)))
    val aOccupancy    = Output(UInt(log2Up(cfg.aBufferDepth + 1).W))
    val aAvailability = Output(UInt(log2Up(cfg.aBufferDepth + 1).W))

    // B buffer: RdOnly DMA写入(权重), Executor读出
    val bPush = slave(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val bPop  = master(Stream(UInt((cfg.totalN * cfg.bW).W)))
    val bOccupancy    = Output(UInt(log2Up(cfg.bBufferDepth + 1).W))
    val bAvailability = Output(UInt(log2Up(cfg.bBufferDepth + 1).W))

    // C buffer: Executor写入(结果), DMA1/Executor读出
    val cPush = slave(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cPop  = master(Stream(UInt((cfg.totalN * cfg.cEffW).W)))
    val cOccupancy    = Output(UInt(log2Up(cfg.cBufferDepth + 1).W))
    val cAvailability = Output(UInt(log2Up(cfg.cBufferDepth + 1).W))

    // flush 控制 (Cmd7 clean)
    val flushA = Input(Bool())
    val flushB = Input(Bool())
    val flushC = Input(Bool())

    // MX scale buffer（仅 hasMx 生成，非 MX 配置不暴露这些端口）：
    //   - scaleA：每激活行一个 8-bit E8M0 scale（随激活流动）
    //   - scaleB：每 K-块一个 totalN 向量（scaleRowW = totalN*8，权重驻留）
    val scaleAPush  = if (cfg.hasMx) Some(slave(Stream(UInt(8.W))))            else None
    val scaleAPop   = if (cfg.hasMx) Some(master(Stream(UInt(8.W))))           else None
    val scaleBPush  = if (cfg.hasMx) Some(slave(Stream(UInt(cfg.scaleRowW.W))))  else None
    val scaleBPop   = if (cfg.hasMx) Some(master(Stream(UInt(cfg.scaleRowW.W)))) else None
    val flushScaleA = if (cfg.hasMx) Some(Input(Bool()))                       else None
    val flushScaleB = if (cfg.hasMx) Some(Input(Bool()))                       else None
  })

  // ---- 实例化三个 StreamFifo ----
  val aFifo = Module(new StreamFifo(UInt((cfg.totalN * cfg.aEffW).W), cfg.aBufferDepth))
  val bFifo = Module(new StreamFifo(UInt((cfg.totalN * cfg.bW).W), cfg.bBufferDepth))
  val cFifo = Module(new StreamFifo(UInt((cfg.totalN * cfg.cEffW).W), cfg.cBufferDepth))

  // ---- A buffer 连接 ----
  aFifo.io.push.valid   := io.aPush.valid
  aFifo.io.push.payload := io.aPush.payload
  io.aPush.ready        := aFifo.io.push.ready
  io.aPop.valid         := aFifo.io.pop.valid
  io.aPop.payload       := aFifo.io.pop.payload
  aFifo.io.pop.ready    := io.aPop.ready
  aFifo.io.flush        := io.flushA
  io.aOccupancy         := aFifo.io.occupancy
  io.aAvailability      := aFifo.io.availability

  // ---- B buffer 连接（权重专用，RdOnly 写入 / Executor 读出）----
  bFifo.io.push.valid   := io.bPush.valid
  bFifo.io.push.payload := io.bPush.payload
  io.bPush.ready        := bFifo.io.push.ready
  io.bPop.valid         := bFifo.io.pop.valid
  io.bPop.payload       := bFifo.io.pop.payload
  bFifo.io.pop.ready    := io.bPop.ready
  bFifo.io.flush        := io.flushB
  io.bOccupancy         := bFifo.io.occupancy
  io.bAvailability      := bFifo.io.availability

  // ---- C buffer 连接 ----
  cFifo.io.push.valid   := io.cPush.valid
  cFifo.io.push.payload := io.cPush.payload
  io.cPush.ready        := cFifo.io.push.ready
  io.cPop.valid         := cFifo.io.pop.valid
  io.cPop.payload       := cFifo.io.pop.payload
  cFifo.io.pop.ready    := io.cPop.ready
  cFifo.io.flush        := io.flushC
  io.cOccupancy         := cFifo.io.occupancy
  io.cAvailability      := cFifo.io.availability

  // ---- Scale buffer 连接（仅 hasMx；非 MX 配置不生成 FIFO/接线）----
  if (cfg.hasMx) {
    val scaleAFifo = Module(new StreamFifo(UInt(8.W), cfg.scaleBufferDepth))
    val scaleBFifo = Module(new StreamFifo(UInt(cfg.scaleRowW.W), cfg.scaleBufferDepth))

    scaleAFifo.io.push.valid   := io.scaleAPush.get.valid
    scaleAFifo.io.push.payload := io.scaleAPush.get.payload
    io.scaleAPush.get.ready    := scaleAFifo.io.push.ready
    io.scaleAPop.get.valid     := scaleAFifo.io.pop.valid
    io.scaleAPop.get.payload   := scaleAFifo.io.pop.payload
    scaleAFifo.io.pop.ready     := io.scaleAPop.get.ready
    scaleAFifo.io.flush         := io.flushScaleA.get

    scaleBFifo.io.push.valid   := io.scaleBPush.get.valid
    scaleBFifo.io.push.payload := io.scaleBPush.get.payload
    io.scaleBPush.get.ready    := scaleBFifo.io.push.ready
    io.scaleBPop.get.valid     := scaleBFifo.io.pop.valid
    io.scaleBPop.get.payload   := scaleBFifo.io.pop.payload
    scaleBFifo.io.pop.ready     := io.scaleBPop.get.ready
    scaleBFifo.io.flush         := io.flushScaleB.get
  }
}
