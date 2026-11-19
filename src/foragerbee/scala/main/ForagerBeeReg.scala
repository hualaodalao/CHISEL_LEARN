import chisel3._
import chisel3.util._

/** 寄存器配置顶层：FbRegIf + ForagerBee。上游经 4 路寄存器命令下发配置（resp 1 路按 lane 序），
  * START 组装命令入队；引擎 done 由 RegIf 消费，驱动 credit 返还与 DONE/DONE_CNT 状态。
  * 原 ForagerBee cmd/done Stream 在内部连接，外部仅见寄存器命令/回执与总线。引擎与 Dispatcher 零改动。
  */
class ForagerBeeReg(cfg: ForagerBeeConfig = ForagerBeeConfig()) extends Module {
  val io = IO(new Bundle {
    val cmd  = Vec(4, slave(Stream(new FbRegCmd)))
    val resp = master(Stream(new FbRegResp))
    val bus  = Vec(cfg.numPorts, master(new FbStreamBus(cfg.dataWidth, cfg.addressWidth)))
  })

  private val fb = Module(new ForagerBee(cfg))
  private val regIf = Module(new FbRegIf(cfg))

  for (l <- 0 until 4) regIf.io.cmd(l) << io.cmd(l)
  io.resp << regIf.io.resp
  fb.io.cmd << regIf.io.fbCmd
  regIf.io.done << fb.io.done
  regIf.io.busy := fb.io.busy
  regIf.io.idle := fb.io.idle
  regIf.io.occupancy := fb.io.occupancy
  fb.io.bus <> io.bus
}
