/** ForagerBee（采集蜂）—— 专用于 NPU 计算的 DMA 搬运模块（顶层）。
  *
  * 架构定位：
  *   蜂巢（Hive）SoC 中，HiveWorker 脉动阵列负责计算，ForagerBee 采集蜂负责
  *   在片上存储与 NPU 之间搬运张量数据：多维 stride 拷贝（COPY）与
  *   2D 分块转置（TRANSPOSE，服务于权重/激活布局变换）。
  *
  * 内部结构（命令层 → 调度层 → 执行层 → 总线层）：
  * {{{
  *   io.cmd（单入口 Stream(FbCmd)）
  *     → FbDispatcher：StreamFifo + 地址冲突检查（RAW/WAR/WAW）
  *         + 通道能力匹配（TRANSPOSE 仅派发到支持转置的通道）
  *         + 优先级编码选空闲通道
  *     → numPorts 个 FbEngine（按 channelTranspose 配置是否含转置缓冲）
  *     → io.bus(0..numPorts-1)：每引擎一个 FbStreamBus master
  *   各引擎 done 回执经 StreamArbiter(roundRobin) 汇到 io.done
  * }}}
  *
  * @param cfg 配置：numPorts(1..4)、queueDepth(≥1)、maxDims(1..4)、
  *            tileSize(≥2)、maxBurstLen(1..256)、dataWidth、addressWidth、
  *            channelTranspose（每通道是否支持转置）
  *
  * IO 端口语义：
  *   cmd       : 单命令入队口（slave Stream(FbCmd)），命令字段见 [[FbCmd]]
  *   occupancy : 命令队列当前占用深度（0..queueDepth）
  *   done      : 完成回执（master Stream(FbDone)：tag 原样返回、err）
  *   busy      : 任一引擎非空闲
  *   bus       : numPorts 个 FbStreamBus master 读写口
  */

import chisel3._
import chisel3.util._

class ForagerBee(cfg: ForagerBeeConfig = ForagerBeeConfig()) extends Module {
  require(cfg.numPorts >= 1 && cfg.numPorts <= 4, s"ForagerBee: numPorts(${cfg.numPorts}) 必须 ∈ [1,4]")
  require(cfg.queueDepth >= 1, s"ForagerBee: queueDepth(${cfg.queueDepth}) 必须 ≥ 1")
  require(cfg.maxDims >= 1 && cfg.maxDims <= 5, s"ForagerBee: maxDims(${cfg.maxDims}) 必须 ∈ [1,5]")

  val io = IO(new Bundle {

    /** 命令入队口（单入口） */
    val cmd = slave(Stream(new FbCmd(cfg)))

    /** 队列占用深度 */
    val occupancy = Output(UInt(log2Up(cfg.queueDepth + 1).W))

    /** 完成回执（tag/err） */
    val done = master(Stream(new FbDone(cfg)))

    /** 任一引擎非空闲 */
    val busy = Output(Bool())

    /** DMA 读写口（每引擎一个 FbStreamBus master） */
    val bus = Vec(cfg.numPorts, master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth)))

    /** 链式描述符独立读总线（仅 enableChaining 时存在） */
    val chainBus = if (cfg.enableChaining) Some(master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth))) else None
  })

  // --- 执行层：每通道一个引擎（按 channelTranspose/channelPermute 决定是否含转置缓冲及 PERMUTE 支持） ---
  val engines = (0 until cfg.numPorts).map { p =>
    Module(new FbEngine(cfg,
      supportsTranspose = cfg.resolvedChannelTranspose(p),
      supportsPermute = cfg.resolvedChannelPermute(p)
    ))
  }

  // --- 调度层：单队列 + 冲突检查 + 通道选择 ---
  val dispatcher = Module(new FbDispatcher(cfg))
  io.occupancy := dispatcher.io.occupancy

  // 连接调度器与引擎
  for (p <- 0 until cfg.numPorts) {
    engines(p).io.cmdIn << dispatcher.io.pop(p)
    dispatcher.io.chBusy(p) := engines(p).io.busy
    dispatcher.io.chDone(p) := engines(p).io.done.fire
    engines(p).io.bus <> io.bus(p)
  }

  // --- 完成回执汇流 ---
  val doneArbOut = StreamArbiterFactory().roundRobin.transactionLock.on(engines.map(_.io.done))

  // --- 链式描述符控制器集成 ---
  if (cfg.enableChaining) {
    val chainCtrl = Module(new FbChainController(cfg))
    // io.cmd → chainCtrl.cmdIn
    chainCtrl.io.cmdIn << io.cmd
    // chainCtrl.cmdOut → dispatcher
    dispatcher.io.push << chainCtrl.io.cmdOut
    // engines done → chainCtrl.doneIn
    chainCtrl.io.doneIn << doneArbOut
    // chainCtrl.doneOut → io.done
    io.done << chainCtrl.io.doneOut
    // chainBus 连接
    io.chainBus.get <> chainCtrl.io.chainBus
  } else {
    // 直连模式
    dispatcher.io.push << io.cmd
    io.done << doneArbOut
  }

  io.busy := VecInit(engines.map(_.io.busy)).asUInt.orR
}
