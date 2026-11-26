package loom.chi

import chisel3._
import chisel3.util._
import loom.rtl.LmWire

/** NIU QoS bandwidth regulator (HW-10/SEM-18). A token bucket on the RN REQ
  * inject path: tokens accrue at `bytesPerCycle` (fixed-point, scale 2^10) and
  * cap at the burst capacity; each forwarded flit consumes one flit's bytes
  * (datWidth/8). fixed => saturation 0 => capacity = one flit (strict rate, no
  * burst); limiter/regulator => capacity = saturationBytes (peak + burst).
  * Backpressures (never drops) when tokens are insufficient, so the long-run
  * rate converges to the declared bytesPerCycle within the burst allowance.
  */
class ChiQosRegulator(c: ChiConfig, bytesPerCycle: Double, saturationBytes: Int) extends Module {
  private val perFlit = c.datWidth / 8
  private val scale = 1024
  private val rate = math.max(1L, math.round(bytesPerCycle * scale))
  private val capacityBytes = math.max(perFlit, saturationBytes)
  private val capFixed = capacityBytes.toLong * scale
  private val perFlitFixed = perFlit.toLong * scale
  private val w = math.max(8, log2Ceil(capFixed + rate + 1))

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new ChiReqFlit(c)))
    val out = Decoupled(new ChiReqFlit(c))
  })

  private val token = RegInit(0.U(w.W))
  private val added = token +& rate.U(w.W)
  private val capped = Mux(added > capFixed.U(w.W), capFixed.U(w.W), added)
  private val canSend = capped >= perFlitFixed.U

  io.out.valid := io.in.valid && canSend
  io.out.bits := io.in.bits
  io.in.ready := io.out.ready && canSend

  token := Mux(io.out.fire, capped - perFlitFixed.U(w.W), capped)
}