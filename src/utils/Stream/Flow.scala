import chisel3._
import chisel3.util._

/** SpinalHDL Flow 库的 Chisel 移植版。
  *
  * Flow = 只有 valid + payload 的单向数据流（无 ready，不能反压）。
  * 与 Stream 一样按 master 视角声明方向：valid/payload 均为 Output，
  * slave 端用 `slave(Flow(...))` = `Flipped`。
  *
  * 未移植：FlowCCByToggle/FlowCCUnsafeByToggle 等跨时钟域组件、
  * Fragment 相关、formal 验证 API。
  */

object Flow {
  def apply[T <: Data](gen: T): Flow[T] = new Flow(gen)

  /** 从 chisel3.util.Valid 包装出 Flow 视图 */
  def fromValid[T <: Data](v: Valid[T]): Flow[T] = {
    val ret = Wire(Flow(chiselTypeOf(v.bits)))
    ret.valid := v.valid
    ret.payload := v.bits
    ret
  }
}

/** 无反压的单向数据流，payload 在 valid（fire）时有效。 */
class Flow[T <: Data](gen: T) extends Bundle with IMasterSlave {
  val valid = Output(Bool())
  val payload = Output(gen)

  /** payload 的 Chisel 类型（仅对已绑定硬件的 Flow 有效） */
  def payloadType: T = chiselTypeOf(payload)

  /** Flow 没有 ready，valid 即 fire */
  def fire: Bool = valid

  // --------------------------------------------------------------------------
  // 连接运算符（语义与 SpinalHDL 一致）
  // --------------------------------------------------------------------------

  /** 直连：this（sink）<< that（source） */
  def <<(that: Flow[T]): Flow[T] = {
    this.valid := that.valid
    this.payload := that.payload
    that
  }

  /** 直连：this（source）>> into（sink） */
  def >>(into: Flow[T]): Flow[T] = { into << this; into }

  /** 经 m2sPipe 连接（打一拍） */
  def <-<(that: Flow[T]): Flow[T] = { this << that.m2sPipe(); that }
  def >->(into: Flow[T]): Flow[T] = { into <-< this; into }

  /** 纯组合中转（不打拍） */
  def combStage(): Flow[T] = {
    val ret = Wire(Flow(payloadType))
    ret << this
    ret
  }

  // --------------------------------------------------------------------------
  // payload 变换
  // --------------------------------------------------------------------------

  /** 用给定硬件信号替换 payload，valid 透传 */
  def translateWith[T2 <: Data](that: T2): Flow[T2] = {
    val next = Wire(Flow(chiselTypeOf(that)))
    next.valid := this.valid
    next.payload := that
    next
  }

  /** 对 payload 做组合逻辑变换 */
  def map[T2 <: Data](translate: T => T2): Flow[T2] =
    translateWith(translate(this.payload))

  def ~[T2 <: Data](that: T2): Flow[T2] = translateWith(that)
  def ~~[T2 <: Data](translate: T => T2): Flow[T2] = map(translate)

  // --------------------------------------------------------------------------
  // 流控变换（Flow 无反压，丢弃即真丢弃）
  // --------------------------------------------------------------------------

  /** 仅保留 cond 为真的事务 */
  def takeWhen(cond: Bool): Flow[T] = {
    val next = Wire(Flow(payloadType))
    next.valid := this.valid && cond
    next.payload := this.payload
    next
  }

  /** cond 为真时丢弃事务 */
  def throwWhen(cond: Bool): Flow[T] = takeWhen(!cond)

  /** throwWhen 的语义化别名（推荐新设计使用） */
  def discardWhen(cond: Bool): Flow[T] = throwWhen(cond)

  // --------------------------------------------------------------------------
  // 流水线原语
  // --------------------------------------------------------------------------

  /** 打一拍 valid/payload。
    * @param holdPayload 仅在 valid 时锁存 payload（否则每拍跟随）
    * @param flush       为真时清掉寄存的 valid
    */
  def m2sPipe(holdPayload: Boolean = false, flush: Bool = null): Flow[T] = {
    val ret = Wire(Flow(payloadType))
    val rValid = RegNext(this.valid, false.B)
    val rData = Reg(payloadType)
    if (holdPayload) {
      when(this.valid) { rData := this.payload }
    } else {
      rData := this.payload
    }
    if (flush != null) {
      when(flush) { rValid := false.B }
    }
    ret.valid := rValid
    ret.payload := rData
    ret
  }

  /** m2sPipe 的别名 */
  def stage(): Flow[T] = m2sPipe()

  /** 延迟给定拍数 */
  def delay(cycleCount: Int): Flow[T] = {
    require(cycleCount >= 0)
    if (cycleCount == 0) this else stage().delay(cycleCount - 1)
  }

  // --------------------------------------------------------------------------
  // 与寄存器 / Stream / Valid 的互转
  // --------------------------------------------------------------------------

  /** fire 时把 payload 锁存进寄存器（无复位值） */
  def toReg(): T = RegEnable(this.payload, this.fire)

  /** fire 时把 payload 锁存进寄存器，带复位值 */
  def toReg(init: T): T = RegEnable(this.payload, init, this.fire)

  /** 转为 Stream。下游反压时事务直接丢失（Flow 无法阻塞上游）。 */
  def toStream: Stream[T] = toStream(null)

  /** 转为 Stream，overflow 指示因下游反压而丢失事务 */
  def toStream(overflow: Bool): Stream[T] = {
    val ret = Wire(Stream(payloadType))
    ret.valid := this.valid
    ret.payload := this.payload
    if (overflow != null) overflow := ret.valid && !ret.ready
    ret
  }

  /** 转为 Stream 并经 FIFO 缓冲，占用达到 overflowOccupancyAt 时报 overflow */
  def toStream(overflow: Bool, fifoSize: Int, overflowOccupancyAt: Int): Stream[T] = {
    val (ret, occupancy) = queueWithOccupancy(fifoSize)
    overflow := occupancy >= overflowOccupancyAt.U
    ret
  }

  /** 经 FIFO 缓冲（满时丢弃），返回 (pop 流, 占用数) */
  def queueWithOccupancy(size: Int): (Stream[T], UInt) = {
    val fifo = Module(new StreamFifo(payloadType, size))
    fifo.io.flush := false.B
    fifo.io.push << this.toStream
    (fifo.io.pop, fifo.io.occupancy)
  }

  /** 经 FIFO 缓冲（满时丢弃），返回 (pop 流, 剩余空位数) */
  def queueWithAvailability(size: Int): (Stream[T], UInt) = {
    val fifo = Module(new StreamFifo(payloadType, size))
    fifo.io.flush := false.B
    fifo.io.push << this.toStream
    (fifo.io.pop, fifo.io.availability)
  }

  /** 转为 chisel3.util.Valid 视图 */
  def asValid: Valid[T] = {
    val ret = Wire(Valid(payloadType))
    ret.valid := this.valid
    ret.bits := this.payload
    ret
  }

  // --------------------------------------------------------------------------
  // 驱动辅助
  // --------------------------------------------------------------------------

  /** 发起一次事务 */
  def push(that: T): Unit = {
    valid := true.B
    payload := that
  }

  /** 默认驱动（valid=0，payload 给定值），可被 when 中的 push 覆盖 */
  def default(that: T): Unit = {
    valid := false.B
    payload := that
  }

  /** 驱动为空闲（valid=0，payload 无关） */
  def setIdle(): this.type = {
    valid := false.B
    payload := DontCare
    this
  }

  /** Flow 无 ready，freeRun 是空操作（保留以兼容 Stream 风格代码） */
  def freeRun(): this.type = this
}

// ============================================================================
// 辅助对象
// ============================================================================

/** 恒 valid 的 Flow，payload 为给定信号 */
object ValidFlow {
  def apply[T <: Data](payload: T): Flow[T] = {
    val flow = Wire(Flow(chiselTypeOf(payload)))
    flow.push(payload)
    flow
  }
}

/** Flow 寄存器：valid 复位为 0，payload 不复位 */
object RegFlow {
  def apply[T <: Data](dataType: T): Flow[T] = {
    val init = Wire(Flow(dataType))
    init.valid := false.B
    init.payload := DontCare
    RegInit(init)
  }
}

/** 多入合一（要求输入 valid 为 one-hot，即调用方保证互斥） */
object FlowArbiter {
  def apply[T <: Data](inputs: Seq[Flow[T]]): Flow[T] = {
    val output = Wire(Flow(inputs.head.payloadType))
    output.valid := inputs.map(_.valid).reduce(_ || _)
    output.payload := Mux1H(inputs.map(f => f.valid -> f.payload))
    output
  }

  def apply[T <: Data](inputs: Seq[Flow[T]], output: Flow[T]): Unit = {
    output << apply(inputs)
  }
}
