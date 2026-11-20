package loom

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import loom.rtl._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LmRouterLinkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private type Link = LmRouterLink

  private def init(dut: Link): Unit = {
    dut.io.aClk.poke(false.B)
    dut.io.bClk.poke(false.B)
    dut.io.aReset.poke(true.B)
    dut.io.bReset.poke(true.B)

    dut.io.aRdReq.valid.poke(false.B)
    dut.io.aRdReq.bits.addr.poke(0.U)
    dut.io.aRdReq.bits.len.poke(0.U)
    dut.io.aRdReq.bits.src.poke(0.U)
    dut.io.aRdReq.bits.vc.poke(0.U)
    dut.io.bRdReq.ready.poke(true.B)

    dut.io.aWrData.valid.poke(false.B)
    dut.io.aWrData.bits.addr.poke(0.U)
    dut.io.aWrData.bits.data.poke(0.U)
    dut.io.aWrData.bits.strb.poke(0.U)
    dut.io.aWrData.bits.src.poke(0.U)
    dut.io.aWrData.bits.last.poke(true.B)
    dut.io.aWrData.bits.vc.poke(0.U)
    dut.io.bWrData.ready.poke(true.B)

    dut.io.bRdData.valid.poke(false.B)
    dut.io.bRdData.bits.data.poke(0.U)
    dut.io.bRdData.bits.src.poke(0.U)
    dut.io.bRdData.bits.last.poke(true.B)
    dut.io.bRdData.bits.err.poke(false.B)
    dut.io.bRdData.bits.vc.poke(0.U)
    dut.io.aRdData.ready.poke(true.B)

    dut.io.bWrResp.valid.poke(false.B)
    dut.io.bWrResp.bits.src.poke(0.U)
    dut.io.bWrResp.bits.err.poke(false.B)
    dut.io.bWrResp.bits.vc.poke(0.U)
    dut.io.aWrResp.ready.poke(true.B)

    dut.io.aRdDataFwd.valid.poke(false.B)
    dut.io.aRdDataFwd.bits.data.poke(0.U)
    dut.io.aRdDataFwd.bits.src.poke(0.U)
    dut.io.aRdDataFwd.bits.last.poke(true.B)
    dut.io.aRdDataFwd.bits.err.poke(false.B)
    dut.io.aRdDataFwd.bits.vc.poke(0.U)
    dut.io.bRdDataFwd.ready.poke(true.B)

    dut.io.aWrRespFwd.valid.poke(false.B)
    dut.io.aWrRespFwd.bits.src.poke(0.U)
    dut.io.aWrRespFwd.bits.err.poke(false.B)
    dut.io.aWrRespFwd.bits.vc.poke(0.U)
    dut.io.bWrRespFwd.ready.poke(true.B)

    dut.reset.poke(false.B)

    for (_ <- 0 until 3) {
      aEdge(dut); bEdge(dut)
    }
    dut.io.aReset.poke(false.B)
    dut.io.bReset.poke(false.B)
  }

  private def aEdge(dut: Link): Unit = {
    dut.io.aClk.poke(true.B)
    dut.clock.step()
    dut.io.aClk.poke(false.B)
    dut.clock.step()
  }

  private def bEdge(dut: Link): Unit = {
    dut.io.bClk.poke(true.B)
    dut.clock.step()
    dut.io.bClk.poke(false.B)
    dut.clock.step()
  }

  behavior of "LmRouterLink (async)"

  it should "carry 1000 write beats and 1000 write responses across two clock domains" in {
    val n = 1000
    simulate(new LmRouterLink(32, 32, 2, pipe = 0, isAsync = true, asyncDepth = 8)) { dut =>
      init(dut)

      var sent = 0
      var bSeen = 0
      var recv = 0
      var pending = Option.empty[BigInt]
      var guard = 0
      var loop = 0

      while (recv < n && guard < 40000) {
        // A domain: present a beat and consume a response.
        if (sent < n) {
          dut.io.aWrData.valid.poke(true.B)
          dut.io.aWrData.bits.data.poke(sent.U)
          dut.io.aWrData.bits.src.poke((sent % 4).U)
        } else {
          dut.io.aWrData.valid.poke(false.B)
        }

        val aReady = dut.io.aWrData.ready.peek().litValue == 1
        val aRspValid = dut.io.aWrResp.valid.peek().litValue == 1
        val aRspSrc = if (aRspValid) dut.io.aWrResp.bits.src.peek().litValue.toInt else 0
        aEdge(dut)
        if (aReady && sent < n) sent += 1
        if (aRspValid) {
          aRspSrc shouldBe (recv % 4)
          recv += 1
        }

        // B domain at half rate: consume a beat and return one response.
        if (loop % 2 == 0) {
          dut.io.bWrData.ready.poke(pending.isEmpty.B)
          val bValid = dut.io.bWrData.valid.peek().litValue == 1
          val bSrc = if (bValid) dut.io.bWrData.bits.src.peek().litValue else BigInt(0)
          val bData = if (bValid) dut.io.bWrData.bits.data.peek().litValue else BigInt(0)
          val bReady = dut.io.bWrData.ready.peek().litValue == 1

          val toSend = pending.orElse(if (bValid && bReady) Some(bSrc) else None)
          dut.io.bWrResp.valid.poke(toSend.nonEmpty.B)
          if (toSend.nonEmpty) dut.io.bWrResp.bits.src.poke(toSend.get.U)
          val bRspReady = dut.io.bWrResp.ready.peek().litValue == 1
          bEdge(dut)

          if (bValid && bReady) {
            bData shouldBe BigInt(bSeen)
            bSeen += 1
          }
          pending = if (toSend.nonEmpty && !bRspReady) toSend else None
        }

        loop += 1
        guard += 1
      }

      recv shouldBe n
      bSeen shouldBe n
      for (_ <- 0 until 10) { aEdge(dut); bEdge(dut) }
      dut.io.creditUsed.peek().litValue shouldBe 0
    }
  }

  it should "carry 1000 read requests and 1000 read data beats across two clock domains" in {
    val n = 1000
    simulate(new LmRouterLink(32, 32, 2, pipe = 0, isAsync = true, asyncDepth = 8)) { dut =>
      init(dut)

      var sent = 0
      var bSeen = 0
      var recv = 0
      var pending = Option.empty[BigInt]
      var guard = 0
      var loop = 0

      while (recv < n && guard < 40000) {
        if (sent < n) {
          dut.io.aRdReq.valid.poke(true.B)
          dut.io.aRdReq.bits.addr.poke(sent.U)
          dut.io.aRdReq.bits.src.poke((sent % 4).U)
        } else {
          dut.io.aRdReq.valid.poke(false.B)
        }

        val aReady = dut.io.aRdReq.ready.peek().litValue == 1
        val aRspValid = dut.io.aRdData.valid.peek().litValue == 1
        val aRspData = if (aRspValid) dut.io.aRdData.bits.data.peek().litValue else BigInt(0)
        aEdge(dut)
        if (aReady && sent < n) sent += 1
        if (aRspValid) {
          aRspData shouldBe BigInt(recv)
          recv += 1
        }

        if (loop % 2 == 0) {
          dut.io.bRdReq.ready.poke(pending.isEmpty.B)
          val bValid = dut.io.bRdReq.valid.peek().litValue == 1
          val bAddr = if (bValid) dut.io.bRdReq.bits.addr.peek().litValue else BigInt(0)
          val bReady = dut.io.bRdReq.ready.peek().litValue == 1

          val toSend = pending.orElse(if (bValid && bReady) Some(bAddr) else None)
          dut.io.bRdData.valid.poke(toSend.nonEmpty.B)
          if (toSend.nonEmpty) {
            dut.io.bRdData.bits.data.poke(toSend.get.U)
            dut.io.bRdData.bits.src.poke((toSend.get % 4).U)
          }
          val bRspReady = dut.io.bRdData.ready.peek().litValue == 1
          bEdge(dut)

          if (bValid && bReady) {
            bAddr shouldBe BigInt(bSeen)
            bSeen += 1
          }
          pending = if (toSend.nonEmpty && !bRspReady) toSend else None
        }

        loop += 1
        guard += 1
      }

      recv shouldBe n
      bSeen shouldBe n
      for (_ <- 0 until 10) { aEdge(dut); bEdge(dut) }
      dut.io.creditUsed.peek().litValue shouldBe 0
    }
  }
}
