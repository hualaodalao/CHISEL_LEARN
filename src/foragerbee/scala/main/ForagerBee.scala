import chisel3._
import chisel3.util._

class ForagerBee(cfg: ForagerBeeConfig = ForagerBeeConfig()) extends Module {
  val io = IO(new Bundle {
    val cmd = slave(Stream(new FbCmd(cfg)))
    val occupancy = Output(UInt(log2Up(cfg.queueDepth + 1).W))
    val done = master(Stream(new FbDone(cfg)))
    val busy = Output(Bool())
    val idle = Output(Bool())
    val bus = Vec(cfg.numPorts, master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth)))
  })

  val engines = (0 until cfg.numPorts).map { p =>
    Module(new FbEngine(cfg,
      supportsTranspose = cfg.resolvedChannelTranspose(p),
      supportsPermute = cfg.resolvedChannelPermute(p),
      supportsIm2col = cfg.resolvedChannelIm2col(p),
      supportsScatterGather = cfg.resolvedChannelScatterGather(p)
    ))
  }
  val dispatcher = Module(new FbDispatcher(cfg))
  val expander = Module(new FbTile2LinearExpander(cfg))
  expander.io.in << io.cmd
  dispatcher.io.push << expander.io.out
  io.occupancy := dispatcher.io.occupancy

  for (p <- 0 until cfg.numPorts) {
    engines(p).io.cmdIn << dispatcher.io.pop(p)
    dispatcher.io.chBusy(p) := engines(p).io.busy
    dispatcher.io.chDone(p) := engines(p).io.done.fire
  }

  if (cfg.enableSharedWritePorts) {
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

  io.done << StreamArbiterFactory().roundRobin.transactionLock.on(engines.map(_.io.done))
  io.busy := VecInit(engines.map(_.io.busy)).asUInt.orR
  io.idle := !io.busy && io.occupancy === 0.U
}
