package loom

import java.nio.file.{Files, Paths}

import chisel3._
import chisel3.util._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.ir.{Ir, Json}
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** V1.1-S2: the wrapper runs one partition per clock domain and bridges the
  * async cut with an LmAsyncLink in each direction. The driver below keeps
  * the 1 GHz domain at twice the rate of the 600 MHz domain by toggling the
  * two `domainClk` inputs on half steps of the simulator clock.
  */
class LmWrapperAsyncSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private val ir = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/examples/chain_split_async.json"))))
  private val net = NetworkParams.fromIr(ir)
  private val parts = ir.partitions.map(s => PartitionParams.fromIr(ir, net, s))

  // domainNames is sorted: 0 = core (1 GHz), 1 = slow (600 MHz) simulated 2:1.
  private class ClockDriver(dut: LmWrapperTop) {
    private var phase = 0
    private var lastCore = false
    private var lastSlow = false

    private def step(core: Boolean, slow: Boolean): (Boolean, Boolean) = {
      dut.io.domainClk.get(0).poke(core.B)
      dut.io.domainClk.get(1).poke(slow.B)
      dut.clock.step()
      val coreRise = core && !lastCore
      val slowRise = slow && !lastSlow
      lastCore = core
      lastSlow = slow
      phase += 1
      (coreRise, slowRise)
    }

    def lowPhase(): (Boolean, Boolean) = step(false, (phase / 2) % 2 == 0)
    def highPhase(): (Boolean, Boolean) = step(true, (phase / 2) % 2 == 0)

    def cycle(n: Int): Unit = for (_ <- 0 until n) { lowPhase(); highPhase() }
  }

  private def init(dut: LmWrapperTop, ck: ClockDriver): Unit = {
    dut.io.domainClk.get.length shouldBe 2
    for (i <- 0 until net.nMaster) {
      dut.io.master(i).valid.poke(false.B)
      dut.io.master(i).bits.write.poke(true.B)
      dut.io.master(i).bits.addr.poke(0.U)
      dut.io.master(i).bits.len.poke(0.U)
      dut.io.master(i).bits.src.poke(i.U)
      dut.io.master(i).bits.data.poke(0.U)
      dut.io.master(i).bits.strb.poke(0.U)
      dut.io.masterRsp(i).ready.poke(true.B)
    }
    for (j <- 0 until net.nSlave) {
      dut.io.slave(j).ready.poke(true.B)
      dut.io.slaveRsp(j).valid.poke(false.B)
      dut.io.slaveRsp(j).bits.src.poke(0.U)
      dut.io.slaveRsp(j).bits.data.poke(0.U)
      dut.io.slaveRsp(j).bits.err.poke(false.B)
    }
    for (d <- 0 until 2) {
      dut.io.domainClk.get(d).poke(false.B)
      dut.io.domainReset.get(d).poke(true.B)
    }
    dut.clock.step(4)
    ck.cycle(4)
    for (d <- 0 until 2) dut.io.domainReset.get(d).poke(false.B)
  }

  private def request(
      dut: LmWrapperTop,
      ck: ClockDriver,
      m: Int,
      addr: Long,
      data: Long,
      write: Boolean
  ): Unit = {
    dut.io.master(m).bits.write.poke(write.B)
    dut.io.master(m).bits.addr.poke(addr.U)
    dut.io.master(m).bits.src.poke(m.U)
    dut.io.master(m).bits.data.poke(data.U)
    dut.io.master(m).valid.poke(true.B)

    var accepted = false
    var guard = 0
    while (!accepted && guard < 400) {
      ck.lowPhase()
      accepted = dut.io.master(m).ready.peek().litValue == 1
      ck.highPhase()
      guard += 1
    }
    accepted shouldBe true
    dut.io.master(m).valid.poke(false.B)

    val j = ((addr >> 12) & 1).toInt
    var seen = false
    var addrSeen = BigInt(0)
    var dataSeen = BigInt(0)
    var srcSeen = BigInt(0)
    guard = 0
    while (!seen && guard < 400) {
      ck.lowPhase()
      if (dut.io.slave(j).valid.peek().litValue == 1) {
        seen = true
        addrSeen = dut.io.slave(j).bits.addr.peek().litValue
        dataSeen = dut.io.slave(j).bits.data.peek().litValue
        srcSeen = dut.io.slave(j).bits.src.peek().litValue
      }
      ck.highPhase()
      guard += 1
    }
    seen shouldBe true
    addrSeen shouldBe BigInt(addr)
    // A read carries no write data; a write beat carries its own payload.
    dataSeen shouldBe (if (write) BigInt(data) else BigInt(0))
    srcSeen shouldBe BigInt(m)

    // Let the request fire and allocate its response slot before returning.
    ck.cycle(4)

    dut.io.slaveRsp(j).bits.src.poke(m.U)
    dut.io.slaveRsp(j).bits.data.poke((data + 0x1000).U)
    dut.io.slaveRsp(j).valid.poke(true.B)
    // The response is captured on a slow rising edge; a rising edge can land
    // on either half step, so count both.
    var sent = false
    guard = 0
    while (!sent && guard < 400) {
      val (_, riseLow) = ck.lowPhase()
      val (_, riseHigh) = ck.highPhase()
      if (riseLow || riseHigh) sent = true
      guard += 1
    }
    sent shouldBe true
    dut.io.slaveRsp(j).valid.poke(false.B)

    var got = Option.empty[BigInt]
    guard = 0
    while (got.isEmpty && guard < 400) {
      ck.lowPhase()
      if (dut.io.masterRsp(m).valid.peek().litValue == 1)
        got = Some(dut.io.masterRsp(m).bits.data.peek().litValue)
      ck.highPhase()
      guard += 1
    }
    got shouldBe Some(if (write) BigInt(0) else BigInt(data + 0x1000))
  }

  behavior of "LmWrapperTop (async partitions)"

  it should "carry requests and responses across a 2:1 async boundary" in {
    val ops = Seq(
      (0, 0x0000L, 0x11L, true),
      (1, 0x1000L, 0x22L, false),
      (0, 0x1000L, 0x33L, true),
      (1, 0x2000L, 0x44L, false)
    )
    simulate(new LmWrapperTop(net, parts)) { dut =>
      val ck = new ClockDriver(dut)
      init(dut, ck)
      ops.foreach { case (m, addr, data, write) => request(dut, ck, m, addr, data, write) }
    }
  }
}
