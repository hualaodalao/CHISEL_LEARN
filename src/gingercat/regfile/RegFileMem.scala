import chisel3._
import chisel3.util._

/** NaxRiscv RegFileMem 的 Chisel 移植版。
  *
  * Implement a register file with multiple read/write ports and different
  * topology to implement those ports:
  *   - infered && !regBasedRam ：单个 Mem/SyncReadMem（写口多于 1 时依赖工具推断）
  *   - infered && regBasedRam  ：纯寄存器堆（one-hot 写仲裁）
  *   - !infered                ：LVT 方案（RamAsyncMwMux / RamSyncMwMux，见 compat）
  *
  * 与 SpinalHDL 的差异：
  *   - `generate` 条件区域 → Scala if。
  *   - 同步读用 `SyncReadMem`；`maskReadDuringWrite` 无编译期 Phase 支持，
  *     读写冲突行为为 undefined（与原版 mask 成 X 的效果一致，仅保留参数）。
  *   - `asyncReadBySyncReadRevertedClk`（反相时钟同步读模拟异步读）未移植，
  *     必须为 false。
  *   - `Verilator.public` / `randBoot` 仿真辅助已删除。
  *
  * headZero 目前未使用（与原版一致，为 x0 恒零预留）。
  */
class RegFileMem(rfpp: RegFilePortParam,
                 readsParameter: scala.collection.Seq[RegFileReadParameter],
                 writesParameter: scala.collection.Seq[RegFileWriteParameter],
                 headZero: Boolean,
                 syncRead: Boolean,
                 dualPortRam: Boolean,
                 regBasedRam: Boolean,
                 asyncReadBySyncReadRevertedClk: Boolean = false,
                 maskReadDuringWrite: Boolean = true) extends Module {
  import rfpp._

  require(!asyncReadBySyncReadRevertedClk, "asyncReadBySyncReadRevertedClk not ported")
  require(readsParameter.forall(!_.withReady))
  require(writesParameter.forall(!_.withReady))

  val io = IO(RegFileIo(rfpp, readsParameter, writesParameter))

  val depth = 1 << addressWidth

  // 统一转成 RamMwIo 视图，各种拓扑实现都挂在 conv 上
  val conv = Wire(new RamMwIo(UInt(dataWidth.W), depth, writesParameter.size, readsParameter.size))
  for ((to, from) <- conv.writes.zip(io.writes)) {
    to.valid := from.valid
    to.payload.address := from.address
    to.payload.data := from.data
  }
  for ((to, from) <- conv.read.zip(io.reads)) {
    to.cmd.valid := from.valid
    to.cmd.payload := from.address
    from.data := to.rsp
  }

  val infered = writesParameter.size == 1 || !dualPortRam

  // 拓扑一：单个存储阵列（多写口时依赖综合工具推断）
  val asMem = if (infered && !regBasedRam) {
    if (!syncRead) {
      val ram = Mem(depth, UInt(dataWidth.W))
      for (w <- conv.writes) {
        when(w.valid) { ram(w.payload.address) := w.payload.data }
      }
      for (r <- conv.read) {
        r.rsp := ram(r.cmd.payload)
      }
    } else {
      val ram = SyncReadMem(depth, UInt(dataWidth.W))
      for (w <- conv.writes) {
        when(w.valid) { ram(w.payload.address) := w.payload.data }
      }
      for (r <- conv.read) {
        r.rsp := ram.read(r.cmd.payload, r.cmd.valid)
      }
    }
  }

  // 拓扑二：纯寄存器堆（one-hot 写仲裁，仅支持异步读）
  val asReg = if (infered && regBasedRam) {
    require(!syncRead)
    val ram = Reg(Vec(depth, UInt(dataWidth.W)))

    for (addr <- 0 until depth) {
      val oh = conv.writes.map(p => p.valid && p.payload.address === addr.U)
      when(oh.reduce(_ || _)) {
        ram(addr) := Mux1H(oh, conv.writes.map(_.payload.data))
      }
    }

    for (r <- conv.read) {
      r.rsp := ram(r.cmd.payload)
    }
  }

  // 拓扑三/四：真多写口，LVT（bank + location 表）方案
  val asAsyncDp = if (!infered && !syncRead) {
    val logic = Module(new RamAsyncMwMux(UInt(dataWidth.W), depth, writesParameter.size, readsParameter.size))
    logic.io <> conv
  }

  val asSyncDp = if (!infered && syncRead) {
    val logic = Module(new RamSyncMwMux(UInt(dataWidth.W), depth, writesParameter.size, readsParameter.size))
    logic.io <> conv
  }
}
