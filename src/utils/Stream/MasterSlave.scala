import chisel3._

/** SpinalHDL 风格的 master/slave 端口声明，适配 Chisel。
  *
  * 与 SpinalHDL 的区别：
  *   - SpinalHDL 的 `IMasterSlave` 是在硬件生成后调用 `asMaster()`/`asSlave()`
  *     动态指定方向；Chisel 的方向在 Bundle 定义时就用 `Output`/`Input` 固定，
  *     反向视角统一用 `Flipped` 得到。
  *   - 因此这里的约定是：**Bundle 一律按 master 视角声明方向**
  *     （即 master 驱动的信号写 `Output`，master 接收的信号写 `Input`）。
  *   - SpinalHDL 的 "variadic" 语法（先声明后 `master(a, b)` 改方向）无法支持，
  *     因为 Chisel 中 `Flipped` 必须在 `IO(...)` 构造时应用。
  *
  * 用法（与 SpinalHDL 基本一致）：
  * {{{
  * class MyBus extends Bundle with IMasterSlave {
  *   val req  = Output(Bool())   // master -> slave
  *   val ack  = Input(Bool())    // slave  -> master
  * }
  *
  * val io = IO(new Bundle {
  *   val up   = slave(new MyBus)    // 等价于 Flipped(new MyBus)
  *   val down = master(new MyBus)   // 原样
  *   // spaceful 语法同样可用：
  *   val up2  = slave port new MyBus
  * })
  * }}}
  */

/** 标记 trait：表明该 Bundle 遵循 master/slave 拓扑，
  * 且其中的方向是按 master 视角声明的。
  *
  * 仅作文档/约束用途，不参与方向推导（方向由 Bundle 内的
  * `Output`/`Input` 与外层 `Flipped` 决定）。
  */
trait IMasterSlave { this: Bundle => }

/** master/slave 声明的公共语法，见 [[master]] 与 [[slave]]。 */
sealed trait MS {

  /** 定义如何对端口应用方向（master 原样 / slave 翻转） */
  protected def applyIt[T <: Data](that: T): T

  /** 带括号语法：`val bus = master(new MyBus)` */
  def apply[T <: Data](that: T): T = applyIt(that)

  /** spaceful 语法：`val bus = master port new MyBus` */
  def port[T <: Data](that: T): T = applyIt(that)

  /** SpinalHDL 短语法：`slave Stream (UInt(8.W))` 等价于 `slave(Stream(UInt(8.W)))` */
  def Stream[T <: Data](gen: T): Stream[T] = applyIt(new Stream(gen))

  /** SpinalHDL 短语法：`slave Flow (UInt(8.W))` 等价于 `slave(Flow(UInt(8.W)))` */
  def Flow[T <: Data](gen: T): Flow[T] = applyIt(new Flow(gen))
}

/** 按 master 视角声明端口（方向原样保留）。
  * {{{ val down = master(Stream(UInt(8.W))) }}}
  */
object master extends MS {
  override protected def applyIt[T <: Data](that: T): T = that
}

/** 按 slave 视角声明端口（方向整体翻转，等价于 `Flipped`）。
  * {{{ val up = slave(Stream(UInt(8.W))) }}}
  */
object slave extends MS {
  override protected def applyIt[T <: Data](that: T): T = Flipped(that)
}
