import chisel3._
import chisel3.util._

/** SpinalHDL Stream 库的 Chisel 移植版。
  *
  * 与 SpinalHDL 的主要差异：
  *   - `Stream` 是标准 Chisel Bundle：`valid`/`payload` 为 Output，`ready` 为 Input
  *     （即按 master 视角声明，slave 端用 `slave(Stream(...))` = `Flipped`）。
  *   - `Flow` 已原生移植（见 Flow.scala）；`toFlow`/`asFlow` 返回 `Flow[T]`。
  *   - 与 `DecoupledIO` 可通过 `toDecoupled`/`Stream.fromDecoupled` 互转。
  *   - 未移植：跨时钟域 FIFO（StreamFifoCC/StreamCCByToggle）、formal 验证 API、
  *     StreamPacker/Unpacker、StreamWidthAdapter。
  *   - `Fragment` 仅移植数据结构本身（last + fragment），未移植 StreamFragment
  *     的插入/重组工具链。
  */

// ============================================================================
// Stream Bundle
// ============================================================================

object Stream {
  def apply[T <: Data](gen: T): Stream[T] = new Stream(gen)

  /** 从 chisel3.util.DecoupledIO 包装出 Stream 视图 */
  def fromDecoupled[T <: Data](dec: DecoupledIO[T]): Stream[T] = {
    val ret = Wire(Stream(chiselTypeOf(dec.bits)))
    ret.valid := dec.valid
    ret.payload := dec.bits
    dec.ready := ret.ready
    ret
  }
}

/** 带 ready/valid 双向握手的数据流，payload 在 valid && ready（fire）时被消费。 */
class Stream[T <: Data](gen: T) extends Bundle with IMasterSlave {
  val valid = Output(Bool())
  val ready = Input(Bool())
  val payload = Output(gen)

  /** payload 的 Chisel 类型（仅对已绑定硬件的 Stream 有效） */
  def payloadType: T = chiselTypeOf(payload)

  def fire: Bool = valid && ready
  def isStall: Bool = valid && !ready
  def isFree: Bool = !valid || ready
  /** 本次 fire 是新事务（上一拍未处于 stall） */
  def isNew: Bool = fire && !RegNext(isStall, false.B)

  // --------------------------------------------------------------------------
  // 连接运算符（语义与 SpinalHDL 一致）
  // --------------------------------------------------------------------------

  /** 直连：this（sink）<< that（source） */
  def <<(that: Stream[T]): Stream[T] = {
    this.valid := that.valid
    this.payload := that.payload
    that.ready := this.ready
    that
  }

  /** 直连：this（source）>> into（sink） */
  def >>(into: Stream[T]): Stream[T] = {
    into << this
    into
  }

  /** 经 m2sPipe 连接（切断 valid/payload 路径） */
  def <-<(that: Stream[T]): Stream[T] = { this << that.m2sPipe(); that }
  def >->(into: Stream[T]): Stream[T] = { into <-< this; into }

  /** 经 s2mPipe 连接（切断 ready 路径） */
  def </<(that: Stream[T]): Stream[T] = { this << that.s2mPipe(); that }
  def >/>(into: Stream[T]): Stream[T] = { into </< this; into }

  /** 经 s2mPipe + m2sPipe 连接（三条路径全部切断） */
  def <-/<(that: Stream[T]): Stream[T] = { this << that.s2mPipe().m2sPipe(); that }
  def >/->(into: Stream[T]): Stream[T] = { into <-/< this; into }

  // --------------------------------------------------------------------------
  // payload 变换
  // --------------------------------------------------------------------------

  /** 用给定硬件信号替换 payload，握手透传 */
  def translateWith[T2 <: Data](that: T2): Stream[T2] = {
    val next = Wire(Stream(chiselTypeOf(that)))
    next.valid := this.valid
    next.payload := that
    this.ready := next.ready
    next
  }

  /** 对 payload 做组合逻辑变换 */
  def map[T2 <: Data](translate: T => T2): Stream[T2] =
    translateWith(translate(this.payload))

  // --------------------------------------------------------------------------
  // 流控变换
  // --------------------------------------------------------------------------

  /** cond 为假时，valid/ready 均被截断（事务被扣住） */
  def continueWhen(cond: Bool): Stream[T] = {
    val next = Wire(Stream(payloadType))
    next.valid := this.valid && cond
    next.payload := this.payload
    this.ready := next.ready && cond
    next
  }

  /** cond 为真时丢弃事务（上游直接被消费，下游看不到） */
  def throwWhen(cond: Bool): Stream[T] = {
    val next = Wire(Stream(payloadType))
    next << this
    when(cond) {
      next.valid := false.B
      this.ready := true.B
    }
    next
  }

  /** 仅保留 cond 为真的事务 */
  def takeWhen(cond: Bool): Stream[T] = throwWhen(!cond)

  /** cond 为真时扣住事务 */
  def haltWhen(cond: Bool): Stream[T] = continueWhen(!cond)

  /** cond 为真时对下游隐藏 valid（上游照常 stall） */
  def clearValidWhen(cond: Bool): Stream[T] = {
    val next = Wire(Stream(payloadType))
    next.valid := this.valid && !cond
    next.payload := this.payload
    this.ready := next.ready
    next
  }

  // --------------------------------------------------------------------------
  // 流水线原语
  // --------------------------------------------------------------------------

  /** 纯组合中转（不打拍） */
  def combStage(): Stream[T] = {
    val ret = Wire(Stream(payloadType))
    ret << this
    ret
  }

  /** 打一拍 valid/payload（切断 master→slave 路径）。
    * @param collapsBubble 空拍时允许直接装载，消除气泡
    * @param flush         为真时清空寄存的事务
    * @param holdPayload   仅在 fire 时锁存 payload
    */
  def m2sPipe(collapsBubble: Boolean = true, flush: Bool = null, holdPayload: Boolean = false): Stream[T] = {
    val ret = Wire(Stream(payloadType))
    val rValid = RegInit(false.B)
    val rData = Reg(payloadType)

    this.ready := ret.ready
    if (collapsBubble) {
      when(!rValid) { this.ready := true.B }
    }
    when(this.ready) { rValid := this.valid }
    if (holdPayload) {
      when(this.fire) { rData := this.payload }
    } else {
      when(this.ready) { rData := this.payload }
    }
    if (flush != null) {
      when(flush) { rValid := false.B }
    }

    ret.valid := rValid
    ret.payload := rData
    ret
  }

  /** 打一拍 ready（切断 slave→master 路径），用一个跳过缓冲吸收反压。 */
  def s2mPipe(flush: Bool = null): Stream[T] = {
    val ret = Wire(Stream(payloadType))
    val rValidN = RegInit(true.B) // 缓冲为空
    val rData = Reg(payloadType)

    when(this.valid) { rValidN := false.B }
    when(ret.ready) { rValidN := true.B } // 同拍冲突时排空优先
    when(this.ready) { rData := this.payload }
    if (flush != null) {
      when(flush) { rValidN := true.B }
    }

    this.ready := rValidN
    ret.valid := this.valid || !rValidN
    ret.payload := Mux(rValidN, this.payload, rData)
    ret
  }

  /** 三条路径（valid/ready/payload）全部打拍，但带宽减半。 */
  def halfPipe(flush: Bool = null): Stream[T] = {
    val ret = Wire(Stream(payloadType))
    val rValid = RegInit(false.B)
    val rData = Reg(payloadType)

    when(this.valid) { rValid := true.B }
    when(ret.fire) { rValid := false.B } // 同拍冲突时排空优先
    when(this.ready) { rData := this.payload }
    if (flush != null) {
      when(flush) { rValid := false.B }
    }

    this.ready := !rValid
    ret.valid := rValid
    ret.payload := rData
    ret
  }

  /** 仅切断 valid 路径（payload 组合透传） */
  def validPipe(): Stream[T] = {
    val ret = Wire(Stream(payloadType))
    val rValid = RegInit(false.B)
    when(this.valid) { rValid := true.B }
    when(ret.fire) { rValid := false.B }
    ret.valid := rValid
    ret.payload := this.payload
    this.ready := ret.fire
    ret
  }

  /** m2sPipe 的别名 */
  def stage(): Stream[T] = m2sPipe()

  /** 按 StreamPipe 策略打拍 */
  def pipelined(pipe: StreamPipe): Stream[T] = pipe(this)

  // --------------------------------------------------------------------------
  // 队列
  // --------------------------------------------------------------------------

  /** 经深度为 size 的 FIFO 缓冲 */
  def queue(size: Int): Stream[T] = {
    val fifo = Module(new StreamFifo(payloadType, size))
    fifo.io.flush := false.B
    fifo.io.push << this
    fifo.io.pop
  }

  // --------------------------------------------------------------------------
  // 类型互转 / 杂项
  // --------------------------------------------------------------------------

  /** 只取 valid/payload 视图（不驱动 ready） */
  def asFlow: Flow[T] = {
    val ret = Wire(Flow(payloadType))
    ret.valid := this.valid
    ret.payload := this.payload
    ret
  }

  /** 转为 Flow（丢弃反压），ready 恒为真 */
  def toFlow: Flow[T] = {
    freeRun()
    asFlow
  }

  /** 转为 chisel3.util.DecoupledIO 视图 */
  def toDecoupled: DecoupledIO[T] = {
    val ret = Wire(Decoupled(payloadType))
    ret.valid := this.valid
    ret.bits := this.payload
    this.ready := ret.ready
    ret
  }

  /** 仅接管握手（valid/ready），payload 由调用者另行赋值 */
  def arbitrationFrom[T2 <: Data](that: Stream[T2]): Unit = {
    this.valid := that.valid
    that.ready := this.ready
  }

  /** ready 恒为真 */
  def freeRun(): this.type = {
    this.ready := true.B
    this
  }

  /** 驱动为空闲（valid=0，payload 无关） */
  def setIdle(): this.type = {
    this.valid := false.B
    this.payload := DontCare
    this
  }

  /** 驱动为阻塞（ready=0） */
  def setBlocked(): this.type = {
    this.ready := false.B
    this
  }
}

// ============================================================================
// StreamPipe 打拍策略
// ============================================================================

trait StreamPipe {
  def apply[T <: Data](m: Stream[T]): Stream[T]
}

object StreamPipe {
  val NONE = new StreamPipe { override def apply[T <: Data](m: Stream[T]) = m.combStage() }
  val M2S = new StreamPipe { override def apply[T <: Data](m: Stream[T]) = m.m2sPipe() }
  val S2M = new StreamPipe { override def apply[T <: Data](m: Stream[T]) = m.s2mPipe() }
  val FULL = new StreamPipe { override def apply[T <: Data](m: Stream[T]) = m.s2mPipe().m2sPipe() }
  val HALF = new StreamPipe { override def apply[T <: Data](m: Stream[T]) = m.halfPipe() }
}

// ============================================================================
// 辅助类型
// ============================================================================

/** 空 payload（对应 SpinalHDL 的 NoData） */
class NoData extends Bundle

/** 纯握手事件流（对应 SpinalHDL 的 Event） */
object Event {
  def apply(): Stream[NoData] = Stream(new NoData)
}

/** 二元组 payload（对应 SpinalHDL 的 TupleBundle2） */
class TupleBundle2[T1 <: Data, T2 <: Data](payload1: T1, payload2: T2) extends Bundle {
  val _1 = payload1.cloneType
  val _2 = payload2.cloneType
}

/** 带 last 标记的分片 payload（对应 SpinalHDL 的 Fragment） */
class Fragment[T <: Data](gen: T) extends Bundle {
  val last = Bool()
  val fragment = gen.cloneType
}

object Fragment {
  def apply[T <: Data](gen: T): Fragment[T] = new Fragment(gen)
}

/** one-hot 掩码工具 */
object OHMasking {

  /** 保留最低位的 1 */
  def first(input: UInt): UInt = PriorityEncoderOH(input)

  /** 轮询仲裁掩码：从 ohPriority 指示的位置开始，环形查找第一个请求。
    * 经典双倍向量减法实现。
    */
  def roundRobin(requests: UInt, ohPriority: UInt): UInt = {
    val width = requests.getWidth
    val doubleRequests = Cat(requests, requests)
    val doubleGrants = doubleRequests & ~(doubleRequests - ohPriority)
    doubleGrants(width * 2 - 1, width) | doubleGrants(width - 1, 0)
  }
}

// ============================================================================
// StreamFifo（基于 chisel3.util.Queue）
// ============================================================================

class StreamFifo[T <: Data](dataType: T, val depth: Int) extends Module {
  require(depth > 0, "StreamFifo depth must be > 0")
  val io = IO(new Bundle {
    val push = slave(Stream(dataType))
    val pop = master(Stream(dataType))
    val flush = Input(Bool())
    val occupancy = Output(UInt(log2Up(depth + 1).W))
    val availability = Output(UInt(log2Up(depth + 1).W))
  })

  val queue = Module(new Queue(chiselTypeOf(io.push.payload), depth, hasFlush = true))
  queue.io.flush.get := io.flush
  queue.io.enq.valid := io.push.valid
  queue.io.enq.bits := io.push.payload
  io.push.ready := queue.io.enq.ready
  io.pop.valid := queue.io.deq.valid
  io.pop.payload := queue.io.deq.bits
  queue.io.deq.ready := io.pop.ready
  io.occupancy := queue.io.count
  io.availability := depth.U - queue.io.count
}

// ============================================================================
// StreamArbiter
// ============================================================================

/** 多入单出仲裁器。仲裁策略与锁策略通过函数注入（与 SpinalHDL 相同的架构）。 */
class StreamArbiter[T <: Data](dataType: T, val portCount: Int)(
    arbitrationLogic: StreamArbiter[T] => Unit,
    lockLogic: StreamArbiter[T] => Unit
) extends Module {
  require(portCount > 0)
  val io = IO(new Bundle {
    val inputs = Vec(portCount, slave(Stream(dataType)))
    val output = master(Stream(dataType))
    val chosen = Output(UInt(log2Up(portCount).W))
    val chosenOH = Output(UInt(portCount.W))
  })

  val locked = RegInit(false.B)
  val maskProposal = Wire(Vec(portCount, Bool()))
  val maskLocked = RegInit(VecInit(true.B +: Seq.fill(portCount - 1)(false.B)))
  val maskRouted = Mux(locked, maskLocked, maskProposal)

  when(io.output.valid) { maskLocked := maskRouted }

  io.output.valid := io.inputs.zip(maskRouted).map { case (in, m) => in.valid && m }.reduce(_ || _)
  io.output.payload := Mux1H(maskRouted, io.inputs.map(_.payload))
  for ((input, routed) <- io.inputs.zip(maskRouted)) {
    input.ready := routed && io.output.ready
  }
  io.chosenOH := maskRouted.asUInt
  io.chosen := OHToUInt(io.chosenOH)

  arbitrationLogic(this)
  lockLogic(this)
}

object StreamArbiter {

  /** 仲裁策略：决定 maskProposal */
  object Arbitration {

    /** 低编号优先（可能饿死高编号） */
    def lowerFirst(core: StreamArbiter[_ <: Data]): Unit = {
      val requests = VecInit(core.io.inputs.map(_.valid)).asUInt
      core.maskProposal := VecInit(OHMasking.first(requests).asBools)
    }

    /** 轮询（公平） */
    def roundRobin(core: StreamArbiter[_ <: Data]): Unit = {
      val requests = VecInit(core.io.inputs.map(_.valid)).asUInt
      // 优先级 = 上次授权位置的下一个（maskLocked 循环左移一位）
      val ohPriority = VecInit(core.maskLocked.last +: core.maskLocked.init).asUInt
      core.maskProposal := VecInit(OHMasking.roundRobin(requests, ohPriority).asBools)
    }

    /** 严格按 0,1,2,... 顺序轮转 */
    def sequentialOrder(core: StreamArbiter[_ <: Data]): Unit = {
      val counter = RegInit(0.U(log2Up(core.portCount).W))
      for (i <- 0 until core.portCount) {
        core.maskProposal(i) := counter === i.U
      }
      when(core.io.output.fire) {
        counter := Mux(counter === (core.portCount - 1).U, 0.U, counter + 1.U)
      }
    }
  }

  /** 锁策略：决定授权保持多久 */
  object Lock {

    /** 不锁定，每拍重新仲裁（payload 可能在 stall 中切换，需下游容忍） */
    def none(core: StreamArbiter[_ <: Data]): Unit = {
      core.locked := false.B
    }

    /** 事务锁：valid 拉高即锁定，直到 fire 才释放（保证事务原子性） */
    def transactionLock(core: StreamArbiter[_ <: Data]): Unit = {
      when(core.io.output.valid) { core.locked := true.B }
      when(core.io.output.fire) { core.locked := false.B }
    }
  }
}

/** 仲裁器工厂：`StreamArbiterFactory().roundRobin.transactionLock.on(inputs)` */
class StreamArbiterFactory {
  var arbitrationLogic: StreamArbiter[_ <: Data] => Unit = StreamArbiter.Arbitration.lowerFirst
  var lockLogic: StreamArbiter[_ <: Data] => Unit = StreamArbiter.Lock.transactionLock

  def build[T <: Data](dataType: T, portCount: Int): StreamArbiter[T] =
    new StreamArbiter(dataType, portCount)(arbitrationLogic, lockLogic)

  def on[T <: Data](inputs: Seq[Stream[T]]): Stream[T] = {
    val arbiter = Module(build(inputs.head.payloadType, inputs.size))
    arbiter.io.inputs.zip(inputs).foreach { case (port, input) => port << input }
    arbiter.io.output
  }

  def onArgs[T <: Data](inputs: Stream[T]*): Stream[T] = on(inputs)

  def lowerFirst: this.type = { arbitrationLogic = StreamArbiter.Arbitration.lowerFirst; this }
  def roundRobin: this.type = { arbitrationLogic = StreamArbiter.Arbitration.roundRobin; this }
  def sequentialOrder: this.type = { arbitrationLogic = StreamArbiter.Arbitration.sequentialOrder; this }
  def noLock: this.type = { lockLogic = StreamArbiter.Lock.none; this }
  def transactionLock: this.type = { lockLogic = StreamArbiter.Lock.transactionLock; this }
}

object StreamArbiterFactory {
  def apply(): StreamArbiterFactory = new StreamArbiterFactory()

  // 支持 SpinalHDL 风格的直接调用：`StreamArbiterFactory.roundRobin.onArgs(...)`
  def lowerFirst: StreamArbiterFactory = apply().lowerFirst
  def roundRobin: StreamArbiterFactory = apply().roundRobin
  def sequentialOrder: StreamArbiterFactory = apply().sequentialOrder
}

// ============================================================================
// Mux / Demux / Fork / Join
// ============================================================================

/** 多选一：select 选中的输入接通到输出 */
object StreamMux {
  def apply[T <: Data](select: UInt, inputs: Seq[Stream[T]]): Stream[T] = {
    val ret = Wire(Stream(inputs.head.payloadType))
    ret.valid := VecInit(inputs.map(_.valid))(select)
    ret.payload := VecInit(inputs.map(_.payload))(select)
    for ((input, index) <- inputs.zipWithIndex) {
      input.ready := select === index.U && ret.ready
    }
    ret
  }
}

/** 一分多路由：仅 select 选中的输出可见事务 */
object StreamDemux {
  def apply[T <: Data](input: Stream[T], select: UInt, portCount: Int): Vec[Stream[T]] = {
    val outputs = Wire(Vec(portCount, Stream(input.payloadType)))
    for (i <- 0 until portCount) {
      outputs(i).valid := input.valid && select === i.U
      outputs(i).payload := input.payload
    }
    input.ready := VecInit(outputs.map(_.ready))(select)
    outputs
  }
}

/** 一入多出复制：每路输出都拿到一份事务，各自握手（非同步模式下互不阻塞）。 */
class StreamFork[T <: Data](dataType: T, val portCount: Int, synchronous: Boolean = false) extends Module {
  val io = IO(new Bundle {
    val input = slave(Stream(dataType))
    val outputs = Vec(portCount, master(Stream(dataType)))
  })

  if (synchronous) {
    // 同步模式：所有输出 ready 齐了才 fire（valid 依赖 ready，需下游容忍）
    io.input.ready := io.outputs.map(_.ready).reduce(_ && _)
    for (output <- io.outputs) {
      output.valid := io.input.valid && io.input.ready
      output.payload := io.input.payload
    }
  } else {
    // 异步模式：linkEnable 记录每路是否还未取走当前事务
    val linkEnable = RegInit(VecInit(Seq.fill(portCount)(true.B)))
    io.input.ready := true.B
    for (i <- 0 until portCount) {
      when(!io.outputs(i).ready && linkEnable(i)) { io.input.ready := false.B }
    }
    for (i <- 0 until portCount) {
      io.outputs(i).valid := io.input.valid && linkEnable(i)
      io.outputs(i).payload := io.input.payload
      when(io.outputs(i).fire) { linkEnable(i) := false.B }
    }
    // 所有输出都取走后，进入下一个事务
    when(io.input.ready) {
      linkEnable.foreach(_ := true.B)
    }
  }
}

object StreamFork {
  def apply[T <: Data](input: Stream[T], portCount: Int, synchronous: Boolean = false): Vec[Stream[T]] = {
    val fork = Module(new StreamFork(input.payloadType, portCount, synchronous))
    fork.io.input << input
    fork.io.outputs
  }
}

object StreamFork2 {
  def apply[T <: Data](input: Stream[T], synchronous: Boolean = false): (Stream[T], Stream[T]) = {
    val outputs = StreamFork(input, 2, synchronous)
    (outputs(0), outputs(1))
  }
}

/** 多入合一：所有输入 valid 齐了才 fire，payload 拼合。 */
object StreamJoin {

  /** 合并两条流为 TupleBundle2 */
  def apply[T1 <: Data, T2 <: Data](source1: Stream[T1], source2: Stream[T2]): Stream[TupleBundle2[T1, T2]] = {
    val combined = Wire(Stream(new TupleBundle2(source1.payloadType, source2.payloadType)))
    combined.valid := source1.valid && source2.valid
    source1.ready := combined.fire
    source2.ready := combined.fire
    combined.payload._1 := source1.payload
    combined.payload._2 := source2.payload
    combined
  }

  /** 合并同类型流为 Vec */
  def vec[T <: Data](sources: Seq[Stream[T]]): Stream[Vec[T]] = {
    val combined = Wire(Stream(Vec(sources.size, sources.head.payloadType)))
    combined.valid := sources.map(_.valid).reduce(_ && _)
    sources.foreach(_.ready := combined.fire)
    combined.payload.zip(sources).foreach { case (p, s) => p := s.payload }
    combined
  }

  /** 仅同步握手，丢弃 payload */
  def arg(sources: Stream[_ <: Data]*): Stream[NoData] = {
    val event = Wire(Stream(new NoData))
    event.valid := sources.map(_.valid).reduce(_ && _)
    sources.foreach(_.ready := event.fire)
    event
  }
}
