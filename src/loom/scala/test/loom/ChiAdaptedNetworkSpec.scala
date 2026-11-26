package loom

import java.nio.file.Paths

import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest._
import chisel3.util.DecoupledIO
import loom.chi._
import loom.gen.{ChiAdaptedNetworkTop, ChiAssemble}
import loom.ir.IrV2
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** S9/HW-12 (§9.3 GEN instantiation duty): the GEN-owned adaptation wrapper
  * must carry DAT bit-exact through the fabric across a 128<->256 node/fabric
  * width mismatch, with DataID (16B units) and BE lanes realigned at the
  * ChiNiAdapt boundaries. RN nodes stay 128-bit; SN nodes are 256-bit so the
  * fabric-side flit mapping (packed data, first-beat DataID, concatenated BE)
  * is directly observable at the SN port.
  */
class ChiAdaptedNetworkSpec extends AnyFlatSpec with Matchers with ChiselSim {
  implicit val plainVerilator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default
  )

  private def chain: IrV2 = IrV2.load(Paths.get("src/loom/examples/chain_4r.json"))

  /** 128-bit RN nodes on a 256-bit fabric; SN nodes matched at 256. */
  private def params: ChiNetworkParams = {
    val v = chain
    ChiAssemble.networkParams(v.copy(nodes = v.nodes.map(n =>
      if (n.kind.startsWith("sn")) n.copy(dataWidth = 256) else n)))
  }

  private def rnOf(dut: ChiAdaptedNetworkTop, i: Int): ChiRnPort =
    dut.io.rn(i).asInstanceOf[ChiRnPort]
  private def snOf(dut: ChiAdaptedNetworkTop, j: Int): ChiSnPort =
    dut.io.sn(j).asInstanceOf[ChiSnPort]

  private def init(dut: ChiAdaptedNetworkTop, p: ChiNetworkParams): Unit = {
    for (i <- 0 until p.nRn) {
      val rn = rnOf(dut, i)
      rn.req.valid.poke(false.B)
      rn.rsp.ready.poke(true.B); rn.dat.ready.poke(true.B); rn.snp.ready.poke(true.B)
      rn.txRsp.valid.poke(false.B); rn.txDat.valid.poke(false.B)
    }
    for (j <- 0 until p.nSn) {
      val sn = snOf(dut, j)
      sn.req.ready.poke(true.B)
      sn.rsp.valid.poke(false.B); sn.dat.valid.poke(false.B)
      sn.rxDat.ready.poke(true.B)
    }
    dut.reset.poke(true.B); dut.clock.step(2); dut.reset.poke(false.B)
  }

  /** Hold valid until the beat fires (ready sampled high), then drop valid. */
  private def send(dut: ChiAdaptedNetworkTop, port: DecoupledIO[ChiDatFlit])(drive: => Unit): Unit = {
    drive
    port.valid.poke(true.B)
    var n = 0
    while (!port.ready.peek().litToBoolean && n < 400) { dut.clock.step(); n += 1 }
    port.ready.expect(true.B)
    dut.clock.step()
    port.valid.poke(false.B)
  }

  private def await(dut: ChiAdaptedNetworkTop, port: DecoupledIO[ChiDatFlit]): Unit = {
    var n = 0
    while (!port.valid.peek().litToBoolean && n < 400) { dut.clock.step(); n += 1 }
    port.valid.expect(true.B)
  }

  private val beatData: Seq[BigInt] = Seq(
    BigInt("112233445566778899aabbccddeeff00", 16),
    BigInt("0fedcba987654321123456789abcdef0", 16),
    BigInt("aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbb", 16),
    BigInt("5555555555555555cccccccccccccccc", 16))
  private val beatBe: Seq[Int] = Seq(0xFFFF, 0x00FF, 0xF0F0, 0x0F0F)

  "ChiAdaptedNetworkTop" should "upsize RN 128-bit beats into 256-bit fabric flits with DataID/BE alignment (HW-12)" in {
    val p = params
    ChiAdaptedNetworkTop.needed(p) shouldBe true
    val sn0 = p.sn.head.nodeId
    simulate(new ChiAdaptedNetworkTop(p)) { dut =>
      init(dut, p)
      val tx = rnOf(dut, 0).txDat // 128-bit node bus
      val rx = snOf(dut, 0).rxDat // 256-bit matched SN bus = fabric flit view
      for (k <- 0 until 4) {
        send(dut, tx) {
          tx.bits.data.poke(beatData(k).U)
          tx.bits.be.poke(beatBe(k).U)
          tx.bits.dataId.poke(k.U) // 16B units: 0,1,2,3
          tx.bits.txnId.poke(7.U)
          tx.bits.srcId.poke(0.U)
          tx.bits.tgtId.poke(sn0.U)
          tx.bits.opcode.poke(ChiOp.DatNonCopyBackWrData.U)
        }
      }
      // two 256-bit flits: first-beat DataID (0 then 2), low-first packed
      // data, concatenated BE lanes
      for (f <- 0 until 2) {
        await(dut, rx)
        rx.bits.data.expect(((beatData(2 * f + 1) << 128) | beatData(2 * f)).U)
        rx.bits.dataId.expect((2 * f).U)
        rx.bits.be.expect(((beatBe(2 * f + 1) << 16) | beatBe(2 * f)).U)
        rx.bits.txnId.expect(7.U)
        rx.bits.opcode.expect(ChiOp.DatNonCopyBackWrData.U)
        dut.clock.step()
      }
    }
  }

  it should "downsize 256-bit SN read flits into 128-bit RN beats low-first (HW-12)" in {
    val p = params
    simulate(new ChiAdaptedNetworkTop(p)) { dut =>
      init(dut, p)
      val dat = snOf(dut, 0).dat // 256-bit matched SN bus
      val rx = rnOf(dut, 0).dat // 128-bit node bus
      for (f <- 0 until 2) {
        send(dut, dat) {
          dat.bits.data.poke(((beatData(2 * f + 1) << 128) | beatData(2 * f)).U)
          dat.bits.be.poke(((beatBe(2 * f + 1) << 16) | beatBe(2 * f)).U)
          dat.bits.dataId.poke((2 * f).U)
          dat.bits.txnId.poke(9.U)
          dat.bits.srcId.poke(p.sn.head.nodeId.U)
          dat.bits.tgtId.poke(0.U)
          dat.bits.opcode.poke(ChiOp.DatCompData.U)
        }
      }
      // four 128-bit beats: DataID advances by 1 (16B units), BE lane k
      for (k <- 0 until 4) {
        await(dut, rx)
        rx.bits.data.expect(beatData(k).U)
        rx.bits.dataId.expect(k.U)
        rx.bits.be.expect(beatBe(k).U)
        rx.bits.txnId.expect(9.U)
        rx.bits.opcode.expect(ChiOp.DatCompData.U)
        dut.clock.step()
      }
    }
  }

  it should "pass matched-width DAT through unchanged (no gearbox)" in {
    val v = chain
    val p = ChiAssemble.networkParams(v.copy(nodes = v.nodes.map(_.copy(dataWidth = 256))))
    ChiAdaptedNetworkTop.needed(p) shouldBe false
    simulate(new ChiAdaptedNetworkTop(p)) { dut =>
      init(dut, p)
      val dat = snOf(dut, 0).dat
      val rx = rnOf(dut, 0).dat
      send(dut, dat) {
        dat.bits.data.poke(((beatData(1) << 128) | beatData(0)).U)
        dat.bits.dataId.poke(0.U)
        dat.bits.be.poke(0xFFFFFFFFL.U)
        dat.bits.txnId.poke(11.U)
        dat.bits.tgtId.poke(0.U)
        dat.bits.opcode.poke(ChiOp.DatCompData.U)
      }
      await(dut, rx)
      rx.bits.data.expect(((beatData(1) << 128) | beatData(0)).U)
      rx.bits.dataId.expect(0.U)
      rx.bits.be.expect(0xFFFFFFFFL.U)
      rx.bits.txnId.expect(11.U)
    }
  }
}