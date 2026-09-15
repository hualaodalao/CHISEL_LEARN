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

    /** 格式转换量化 scale（仅 enableConversion 时存在）。
      *
      * 全局供给：所有通道、所有命令共享同一组量化参数（不再是 [[FbCmd]] 字段），
      * 穿透路径 ForagerBee.io → FbEngine.io → FbFormatConverter.io.cfg_scale。
      *
      * 实际语义（与 [[FbFormatConverter]] 实现一致，**不是 IEEE-754 FP32**）：
      * 仅低 5 bit `cvtScale(4,0)` 被消费，作为 2 的幂移位量（q = round(v / 2^shift)）；
      * 高 27 bit 被忽略，集成方应置 0。故当前只支持 2 的幂 scale。
      * enableConversion=false 的 copy-only 构建不声明该端口（避免无用输入引脚）。 */
    val cvtScale = if (cfg.enableConversion) Some(Input(UInt(32.W))) else None

    /** 格式转换量化 zero point（仅 enableConversion 时存在）。
      *
      * 实际语义：仅低 8 bit `cvtZeroPoint(7,0)` 被消费，按**有符号整数**解释（−128..127）；
      * 高 24 bit 被忽略，集成方应置 0。 */
    val cvtZeroPoint = if (cfg.enableConversion) Some(Input(UInt(32.W))) else None
  })

  // --- 执行层：每通道一个引擎（按 channelTranspose/channelPermute/channelIm2col/channelScatterGather 决定是否含转置缓冲、PERMUTE、IM2COL 及 SG 支持） ---
  val engines = (0 until cfg.numPorts).map { p =>
    Module(new FbEngine(cfg,
      supportsTranspose = cfg.resolvedChannelTranspose(p),
      supportsPermute = cfg.resolvedChannelPermute(p),
      supportsIm2col = cfg.resolvedChannelIm2col(p),
      supportsScatterGather = cfg.resolvedChannelScatterGather(p)
    ))
  }

  // --- 全局量化参数分发：numPorts 个引擎共享同一组 cvtScale/cvtZeroPoint ---
  // 顶层端口与引擎侧端口同为 cfg.enableConversion 编译期门控，Option 真值一致，.get 静态安全
  if (cfg.enableConversion) {
    for (p <- 0 until cfg.numPorts) {
      engines(p).io.cvtScale.get     := io.cvtScale.get
      engines(p).io.cvtZeroPoint.get := io.cvtZeroPoint.get
    }
  }

  // --- 调度层：单队列 + 冲突检查 + 通道选择 ---
  val dispatcher = Module(new FbDispatcher(cfg))
  io.occupancy := dispatcher.io.occupancy

  // 连接调度器与引擎（总线连接按 enableSharedWritePorts 分流，见下）
  for (p <- 0 until cfg.numPorts) {
    engines(p).io.cmdIn << dispatcher.io.pop(p)
    dispatcher.io.chBusy(p) := engines(p).io.busy
    dispatcher.io.chDone(p) := engines(p).io.done.fire
  }

  // --- 总线连接：写端口共享仲裁 或 1:1 直连 ---
  if (cfg.enableSharedWritePorts) {
    // 读通道（rd/rdData）保持每引擎专用直连；
    // 写通道（wr/wrResp）经 FbWrPortArbiter 交织分配到物理写端口。
    val wrArb = Module(new FbWrPortArbiter(cfg))
    for (p <- 0 until cfg.numPorts) {
      io.bus(p).rd << engines(p).io.bus.rd
      engines(p).io.bus.rdData << io.bus(p).rdData
      wrArb.io.engWr(p) << engines(p).io.bus.wr
      engines(p).io.bus.wrResp << wrArb.io.engWrResp(p)
      io.bus(p).wr << wrArb.io.portWr(p)
      wrArb.io.portWrResp(p) << io.bus(p).wrResp
    }
  } else {
    for (p <- 0 until cfg.numPorts) {
      engines(p).io.bus <> io.bus(p)
    }
  }

  // --- 完成回执汇流 ---
  val doneArbOut = StreamArbiterFactory().roundRobin.transactionLock.on(engines.map(_.io.done))

  // --- 链式描述符控制器集成 ---
  val expander = Module(new FbTile2LinearExpander(cfg))

  if (cfg.enableChaining) {
    val chainCtrl = Module(new FbChainController(cfg))
    // io.cmd → chainCtrl.cmdIn
    chainCtrl.io.cmdIn << io.cmd
    // chainCtrl.cmdOut → expander → dispatcher
    expander.io.in << chainCtrl.io.cmdOut
    dispatcher.io.push << expander.io.out
    // engines done → chainCtrl.doneIn
    chainCtrl.io.doneIn << doneArbOut
    // chainCtrl.doneOut → io.done
    io.done << chainCtrl.io.doneOut
    // chainBus 连接
    io.chainBus.get <> chainCtrl.io.chainBus
  } else {
    // 直连模式：io.cmd → expander → dispatcher
    expander.io.in << io.cmd
    dispatcher.io.push << expander.io.out
    io.done << doneArbOut
  }

  io.busy := VecInit(engines.map(_.io.busy)).asUInt.orR
}
