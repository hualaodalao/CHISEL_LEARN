import chisel3._
import chisel3.util._

/** SpinalHDL 多端口 RAM 库（NaxRiscv MultiportRam.scala）的 Chisel 移植版。
  *
  * 保留的核心内容（多写口 RAM 的三种经典实现 + 多读口复制）：
  *   - [[RamAsyncMwXor]] ：XOR banking，异步读
  *   - [[RamAsyncMwReg]] ：纯寄存器堆实现，异步读
  *   - [[RamAsyncMwMux]] ：bank + location 表（LVT），异步读
  *   - [[RamSyncMwXor]]  / [[RamSyncMwMux]] ：同步读版本
  *   - [[RamMr]]         ：单写多读（每个读口复制一份存储）
  *
  * 与 SpinalHDL 的差异：
  *   - `HardType[T]` → 直接传 Chisel 类型 `gen: T`；`Bits` → `UInt`。
  *   - Flow 的 payload 需显式写 `port.payload.xxx`（无 DataCarrier 隐式转换）。
  *   - 同步读用 `RegEnable(mem(addr), en)` 实现（SpinalHDL 的 Mem 可同时
  *     readAsync/readSync，Chisel 的 Mem 只有组合读，SyncReadMem 只有同步读；
  *     XOR/Mux 方案写口需要组合读旁路，故统一用 Mem + 读后打拍）。
  *   - `RamMr` 不做零初始化（Chisel 的 Mem 无 initBigInt 等价物）。
  *
  * 未移植（SpinalHDL 编译器内部 API，Chisel 无对应机制）：
  *   - MultiPortWritesSymplifier / MultiPortReadSymplifier 等 PhaseMemBlackboxing
  *     变换（编译期把多口 Mem 自动替换为上述组件）；Chisel 中请直接实例化组件。
  *   - MemReadDuringWrite* 冒险修补 Phase、SpinalTag、RamMwXorSynth 综合 Bench。
  */

// ============================================================================
// 端口 Bundle
// ============================================================================

/** 写命令：地址 + 数据（配合 Flow 使用，valid 即写使能） */
class MemWriteCmd[T <: Data](gen: T, depth: Int) extends Bundle {
  val address = UInt(log2Up(depth).W)
  val data = gen.cloneType
}

object MemWriteCmd {
  def apply[T <: Data](gen: T, depth: Int) = new MemWriteCmd(gen, depth)
}

/** 读端口（master 视角）：发出地址 cmd（Flow），收回数据 rsp */
class MemRead[T <: Data](gen: T, depth: Int) extends Bundle with IMasterSlave {
  val cmd = Flow(UInt(log2Up(depth).W))
  val rsp = Input(gen)
}

object MemRead {
  def apply[T <: Data](gen: T, depth: Int) = new MemRead(gen, depth)
}

/** 多写多读 RAM 的统一 IO */
class RamMwIo[T <: Data](gen: T, depth: Int, writePorts: Int, readPorts: Int) extends Bundle {
  val writes = Vec(writePorts, slave(Flow(MemWriteCmd(gen, depth))))
  val read = Vec(readPorts, slave(MemRead(gen, depth)))
}

// ============================================================================
// 多写口 RAM —— 异步读
// ============================================================================

/** XOR banking：每个写口一个 bank，写入 data ^ (其余 bank 同地址的值)，
  * 读出时把所有 bank XOR 起来还原。面积小，但写口需要读其余所有 bank。
  */
class RamAsyncMwXor[T <: Data](gen: T, depth: Int, writePorts: Int, readPorts: Int) extends Module {
  val io = IO(new RamMwIo(gen, depth, writePorts, readPorts))
  val rawBits = gen.getWidth
  val ram = List.fill(writePorts)(Mem(depth, UInt(rawBits.W)))

  for ((port, self) <- io.writes.zip(ram)) {
    val others = ram.filterNot(_ eq self).map(_(port.payload.address))
    val xored = (port.payload.data.asUInt +: others).reduce(_ ^ _)
    when(port.valid) { self(port.payload.address) := xored }
  }

  for (port <- io.read) {
    val xored = ram.map(_(port.cmd.payload)).reduce(_ ^ _)
    port.rsp := xored.asTypeOf(gen)
  }
}

/** 纯寄存器堆实现：depth 个寄存器，写口按声明顺序仲裁（后写者赢）。 */
class RamAsyncMwReg[T <: Data](gen: T, depth: Int, writePorts: Int, readPorts: Int) extends Module {
  val io = IO(new RamMwIo(gen, depth, writePorts, readPorts))
  val rawBits = gen.getWidth
  val ram = Reg(Vec(depth, UInt(rawBits.W)))

  for (port <- io.writes) {
    when(port.fire) {
      ram(port.payload.address) := port.payload.data.asUInt
    }
  }

  for (port <- io.read) {
    port.rsp := ram(port.cmd.payload).asTypeOf(gen)
  }
}

/** LVT（Live Value Table）方案：每个写口一个 bank，另用一张
  * [[RamAsyncMwReg]] location 表记录每个地址最后被哪个 bank 写过，
  * 读出时按 location 选 bank。
  */
class RamAsyncMwMux[T <: Data](gen: T, depth: Int, writePorts: Int, readPorts: Int) extends Module {
  val io = IO(new RamMwIo(gen, depth, writePorts, readPorts))
  val rawBits = gen.getWidth
  val ram = List.fill(writePorts)(Mem(depth, UInt(rawBits.W)))

  val location = Module(new RamAsyncMwReg(
    gen = UInt(log2Up(writePorts).W),
    depth = depth,
    writePorts = writePorts,
    readPorts = readPorts
  ))

  for (((port, loc), i) <- io.writes.zip(location.io.writes).zipWithIndex) {
    when(port.valid) { ram(i)(port.payload.address) := port.payload.data.asUInt }
    loc.valid := port.valid
    loc.payload.address := port.payload.address
    loc.payload.data := i.U
  }

  for ((port, loc) <- io.read.zip(location.io.read)) {
    loc.cmd.valid := port.cmd.valid
    loc.cmd.payload := port.cmd.payload

    val values = ram.map(_(port.cmd.payload))
    port.rsp := VecInit(values)(loc.rsp).asTypeOf(gen)
  }
}

// ============================================================================
// 多写口 RAM —— 同步读（cmd.valid 为读使能，rsp 晚一拍）
// ============================================================================

/** [[RamAsyncMwXor]] 的同步读版本 */
class RamSyncMwXor[T <: Data](gen: T, depth: Int, writePorts: Int, readPorts: Int) extends Module {
  val io = IO(new RamMwIo(gen, depth, writePorts, readPorts))
  val rawBits = gen.getWidth
  val ram = List.fill(writePorts)(Mem(depth, UInt(rawBits.W)))

  for ((port, self) <- io.writes.zip(ram)) {
    val others = ram.filterNot(_ eq self).map(_(port.payload.address))
    val xored = (port.payload.data.asUInt +: others).reduce(_ ^ _)
    when(port.valid) { self(port.payload.address) := xored }
  }

  for (port <- io.read) {
    val values = ram.map(m => RegEnable(m(port.cmd.payload), port.cmd.valid))
    port.rsp := values.reduce(_ ^ _).asTypeOf(gen)
  }
}

/** [[RamAsyncMwMux]] 的同步读版本：数据读出打一拍，location 表用
  * 打拍后的地址异步查询，与数据在同一拍对齐。
  */
class RamSyncMwMux[T <: Data](gen: T, depth: Int, writePorts: Int, readPorts: Int) extends Module {
  val io = IO(new RamMwIo(gen, depth, writePorts, readPorts))
  val rawBits = gen.getWidth
  val ram = List.fill(writePorts)(Mem(depth, UInt(rawBits.W)))

  val location = Module(new RamAsyncMwReg(
    gen = UInt(log2Up(writePorts).W),
    depth = depth,
    writePorts = writePorts,
    readPorts = readPorts
  ))

  for (((port, loc), i) <- io.writes.zip(location.io.writes).zipWithIndex) {
    when(port.valid) { ram(i)(port.payload.address) := port.payload.data.asUInt }
    loc.valid := port.valid
    loc.payload.address := port.payload.address
    loc.payload.data := i.U
  }

  for ((port, loc) <- io.read.zip(location.io.read)) {
    val values = ram.map(m => RegEnable(m(port.cmd.payload), port.cmd.fire))
    val addressReg = RegEnable(port.cmd.payload, port.cmd.fire)

    loc.cmd.valid := true.B
    loc.cmd.payload := addressReg
    port.rsp := VecInit(values)(loc.rsp).asTypeOf(gen)
  }
}

// ============================================================================
// 单写多读 RAM
// ============================================================================

/** 单写多读：每个读口复制一份完整存储，写入广播到所有副本。 */
class RamMr[T <: Data](gen: T, depth: Int, readPorts: Int) extends Module {
  val io = IO(new Bundle {
    val write = slave(Flow(MemWriteCmd(gen, depth)))
    val read = Vec(readPorts, slave(MemRead(gen, depth)))
  })

  for (port <- io.read) {
    val ram = Mem(depth, gen)
    when(io.write.valid) {
      ram(io.write.payload.address) := io.write.payload.data
    }
    port.rsp := ram(port.cmd.payload)
  }
}
