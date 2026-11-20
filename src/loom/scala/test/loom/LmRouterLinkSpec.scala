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
    dut.io.aReq.valid.poke(false.B)
    dut.io.aReq.bits.write.poke(true.B)
    dut.io.aReq.bits.addr.poke(0.U)
    dut.io.aReq.bits.len.poke(0.U)
    dut.io.aReq.bits.src.poke(0.U)
    dut.io.aReq.bits.data.poke(0.U)
    dut.io.aReq.bits.vc.poke(0.U)
    dut.io.bReq.ready.poke(true.B)
    dut.io.bRsp.valid.poke(false.B)
    dut.io.bRsp.bits.src.poke(0.U)
    dut.io.bRsp.bits.data.poke(0.U)
    dut.io.bRsp.bits.err.poke(false.B)
    dut.io.aRsp.ready.poke(true.B)
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

  it should "carry 1000 requests and responses across two clock domains" in {
    val n = 1000
    simulate(new LmRouterLink(32, 32, 2, 2, pipe = 0, isAsync = true, asyncDepth = 8)) { dut =>
      init(dut)

      var sent = 0
      var bSeen = 0
      var recv = 0
      var pendingRsp = Option.empty[(Int, BigInt)]
      var guard = 0
      var loop = 0

      while (recv < n && guard < 30000) {
        // A domain: present a request and consume a response.
        if (sent < n) {
          dut.io.aReq.valid.poke(true.B)
          dut.io.aReq.bits.src.poke((sent % 4).U)
          dut.io.aReq.bits.data.poke(sent.U)
        } else {
          dut.io.aReq.valid.poke(false.B)
        }

        val aReady = dut.io.aReq.ready.peek().litValue == 1
        val aRspValid = dut.io.aRsp.valid.peek().litValue == 1
        val aRspData = if (aRspValid) dut.io.aRsp.bits.data.peek().litValue else BigInt(0)
        aEdge(dut)
        if (aReady && sent < n) sent += 1
        if (aRspValid) {
          aRspData shouldBe BigInt(recv)
          recv += 1
        }

        // B domain at half rate: consume a request and return one response.
        if (loop % 2 == 0) {
          // Do not consume a new request while a response is still pending.
          dut.io.bReq.ready.poke(pendingRsp.isEmpty.B)
          val bReqValid = dut.io.bReq.valid.peek().litValue == 1
          val bReqSrc = if (bReqValid) dut.io.bReq.bits.src.peek().litValue.toInt else 0
          val bReqData = if (bReqValid) dut.io.bReq.bits.data.peek().litValue else BigInt(0)
          val bReqReady = dut.io.bReq.ready.peek().litValue == 1

          val toSend = pendingRsp.orElse {
            if (bReqValid && bReqReady) Some((bReqSrc, bReqData)) else None
          }
          dut.io.bRsp.valid.poke(toSend.nonEmpty.B)
          if (toSend.nonEmpty) {
            dut.io.bRsp.bits.src.poke(toSend.get._1.U)
            dut.io.bRsp.bits.data.poke(toSend.get._2.U)
          }
          val bRspReady = dut.io.bRsp.ready.peek().litValue == 1
          bEdge(dut)

          if (bReqValid && bReqReady) {
            bReqData shouldBe BigInt(bSeen)
            bSeen += 1
          }
          pendingRsp = if (toSend.nonEmpty && !bRspReady) toSend else None
        }

        loop += 1
        guard += 1
      }

      // Drain responses.
      var drain = 0
      while (recv < n && drain < 5000) {
        dut.io.aReq.valid.poke(false.B)
        val aRspValid = dut.io.aRsp.valid.peek().litValue == 1
        val aRspData = if (aRspValid) dut.io.aRsp.bits.data.peek().litValue else BigInt(0)
        aEdge(dut)
        if (aRspValid) {
          aRspData shouldBe BigInt(recv)
          recv += 1
        }
        if (drain % 2 == 0) bEdge(dut)
        drain += 1
      }

      recv shouldBe n
      bSeen shouldBe n
      for (_ <- 0 until 10) { aEdge(dut); bEdge(dut) }
      dut.io.creditUsed.peek().litValue shouldBe 0
    }
  }
}
