package loom

import java.nio.file.{Files, Paths}

import loom.check.Checks
import loom.graph.FabricGraph
import loom.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GraphSpec extends AnyFlatSpec with Matchers {
  private val base = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/examples/minimal_4x4.json"))))

  private def router(n: String, in: Int, out: Int): RouterSpec =
    RouterSpec(n, in, out, "core", "flat", 1)

  private def link(from: String, to: String): LinkSpec =
    LinkSpec(from, to, 128, "sync", 0, 2)

  private def irWith(
      routers: Seq[RouterSpec],
      links: Seq[LinkSpec],
      mnets: Seq[String] = Seq("mnet0"),
      snets: Seq[String] = Seq("snet0")
  ): Ir = base.copy(
    mnets = mnets.map(FabricNode(_, "core")),
    snets = snets.map(FabricNode(_, "core")),
    routers = routers,
    links = links
  )

  private def cycleOf(ir: Ir): Option[Seq[String]] =
    FabricGraph.fromIr(ir).findCycle

  "FabricGraph G1" should "accept the single flat router example" in {
    cycleOf(base) shouldBe None
    Checks.runG1(base) shouldBe List.empty
  }

  it should "cut each perimeter loop of a free 2x2 mesh with an auto dateline" in {
    val mesh = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/examples/mesh_2x2_xy.json"))))
    cycleOf(mesh) shouldBe None
    Checks.runG1(mesh) shouldBe List.empty
    FabricGraph.fromIr(mesh).autoDatelines shouldBe Seq.empty

    val free = mesh.copy(routers = mesh.routers.map(_.copy(routing = "any")))
    cycleOf(free) shouldBe None
    FabricGraph.fromIr(free).autoDatelines.toSet shouldBe Set("r00->r10", "r00->r01")
  }

  it should "route the first hop along X for an XY mesh" in {
    val mesh = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/examples/mesh_2x2_xy.json"))))
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

  it should "accept a three-router tree" in {
    val tree = irWith(
      Seq(router("r0", 4, 2), router("r1", 1, 2), router("r2", 1, 2)),
      Seq(
        link("mnet0", "r0"),
        link("r0", "r1"),
        link("r0", "r2"),
        link("r1", "snet0"),
        link("r2", "snet1")
      ),
      snets = Seq("snet0", "snet1")
    )
    cycleOf(tree) shouldBe None
  }

  it should "accept a four-router chain" in {
    val chain = irWith(
      Seq(router("r0", 1, 1), router("r1", 1, 1), router("r2", 1, 1), router("r3", 1, 1)),
      Seq(
        link("mnet0", "r0"),
        link("r0", "r1"),
        link("r1", "r2"),
        link("r2", "r3"),
        link("r3", "snet0")
      )
    )
    cycleOf(chain) shouldBe None
  }

  it should "accept a three-router ring with an automatic dateline" in {
    val ring = irWith(
      Seq(router("r0", 2, 1), router("r1", 1, 2), router("r2", 1, 1)),
      Seq(
        link("mnet0", "r0"),
        link("r0", "r1"),
        link("r1", "r2"),
        link("r2", "r0"),
        link("r1", "snet0")
      )
    )
    val g = FabricGraph.fromIr(ring)
    g.autoDatelines shouldBe Seq("r0->r1")
    g.datelines shouldBe Seq("r0->r1")
    Checks.runG1(ring) shouldBe List.empty
  }

  it should "honour an explicit dateline and reject a stray one" in {
    val ring = Ir.fromJson(Json.parse(
      Files.readString(Paths.get("src/loom/examples/ring_4.json"))))
    Checks.run(Json.parse(Files.readString(Paths.get("src/loom/examples/ring_4.json")))) shouldBe List.empty
    val g = FabricGraph.fromIr(ring)
    g.datelines shouldBe Seq("r0->r1")
    g.autoDatelines shouldBe Seq("r0->r1")

    val marked = ring.copy(links = ring.links.map { l =>
      if (l.from == "r2" && l.to == "r3") l.copy(dateline = true) else l
    })
    val gm = FabricGraph.fromIr(marked)
    gm.datelines shouldBe Seq("r2->r3")
    gm.autoDatelines shouldBe Seq.empty

    val stray = ring.copy(links = ring.links.map { l =>
      if (l.from == "mnet0") l.copy(dateline = true) else l
    })
    Checks.runIr(stray).exists(_.contains("dateline requires a router-to-router link")) shouldBe true
    Checks.runG1(stray).exists(_.contains("is not part of a ring")) shouldBe true
  }

  it should "cut both directions of a bidirectional ring" in {
    val ring = irWith(
      Seq(router("r0", 2, 2), router("r1", 2, 2), router("r2", 2, 2)),
      Seq(
        link("mnet0", "r0"),
        link("r0", "r1"),
        link("r1", "r2"),
        link("r2", "r0"),
        link("r1", "r0"),
        link("r2", "r1"),
        link("r0", "r2"),
        link("r1", "snet0")
      )
    )
    cycleOf(ring) shouldBe None
    Checks.runG1(ring) shouldBe List.empty
    FabricGraph.fromIr(ring).autoDatelines.toSet shouldBe Set("r0->r1", "r0->r2")
  }

  it should "reject a dense cyclic fabric that no dateline can break" in {
    val names = Seq("r0", "r1", "r2", "r3")
    val meshLinks = for {
      a <- names
      b <- names if a != b
    } yield link(a, b)
    val dense = irWith(
      names.map(n => router(n, 4, 4)),
      Seq(link("mnet0", "r0"), link("r1", "snet0")) ++ meshLinks
    )
    Checks.runG1(dense).exists(_.contains("not a ring")) shouldBe true
  }

  it should "reject a router self-link" in {
    val self = irWith(
      Seq(router("r0", 1, 2)),
      Seq(link("mnet0", "r0"), link("r0", "r0"), link("r0", "snet0"))
    )
    val cycle = cycleOf(self)
    cycle.isDefined shouldBe true
    cycle.get.mkString(" -> ") should include("r0->r0")
  }

  it should "render a stable graph report with the acyclic flag" in {
    val ok = Canonical.pretty(FabricGraph.fromIr(base).toJson)
    ok should include("\"acyclic\": true")
    val bad = Canonical.pretty(FabricGraph.fromIr(irWith(
      Seq(router("r0", 1, 2)),
      Seq(link("mnet0", "r0"), link("r0", "r0"), link("r0", "snet0"))
    )).toJson)
    bad should include("\"acyclic\": false")
  }
}
