package loom

import java.nio.file.{Files, Paths}

import loom.ir.{Ir, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** MIG-11 (S6): the v1 NetworkParams routing assertion isolated from
  * GraphSpec together with loom/rtl/NetworkParams.scala. The CHI caliber
  * routing coverage lives in GraphSpec (FabricGraph.fromV2 / ChiGates) and
  * ChiNetworkSpec (BFS route tables).
  */
class GraphV1Spec extends AnyFlatSpec with Matchers {
  "NetworkParams (legacy v1)" should "route the first hop along X for an XY mesh" in {
    val mesh = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/testdata/v1/mesh_2x2_xy.json"))))
    val net = loom.rtl.NetworkParams.fromIr(mesh)
    val r00 = net.routers.indexWhere(_.name == "r00")
    val spec = net.routers(r00)
    // s0/s1 both attach to snet0 behind r11; r00 must move east first.
    spec.routeSlave.foreach { port =>
      val lid = spec.outLink(port)
      val to = net.links.find(_.id == lid).get.toRouter
      net.routers(to).name shouldBe "r10"
    }
  }
}