package loom

import java.nio.file.{Files, Paths}

import loom.check.{Checks, ChecksV2}
import loom.chi.ChiGates
import loom.graph.FabricGraph
import loom.ir._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GraphSpec extends AnyFlatSpec with Matchers {
  private val base = Ir.fromJson(
    Json.parse(Files.readString(Paths.get("src/loom/testdata/v1/minimal_4x4.json"))))

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
      Files.readString(Paths.get("src/loom/testdata/v1/mesh_2x2_xy.json"))))
    cycleOf(mesh) shouldBe None
    Checks.runG1(mesh) shouldBe List.empty
    FabricGraph.fromIr(mesh).autoDatelines shouldBe Seq.empty

    val free = mesh.copy(routers = mesh.routers.map(_.copy(routing = "any")))
    cycleOf(free) shouldBe None
    FabricGraph.fromIr(free).autoDatelines.toSet shouldBe Set("r00->r10", "r00->r01")
  }

  // MIG-11 (S6): the v1 NetworkParams XY-routing assertion moved to
  // legacy/test/loom/GraphV1Spec.scala with loom/rtl/NetworkParams.scala.

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
      Files.readString(Paths.get("src/loom/testdata/v1/ring_4.json"))))
    Checks.run(Json.parse(Files.readString(Paths.get("src/loom/testdata/v1/ring_4.json")))) shouldBe List.empty
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

  // ---- loom/v2 CDG (S3: GEN-10/GEN-11, SEM-07/09/13, CHK-35–39) ----

  behavior of "FabricGraph.fromV2 / ChiGates.runV2 (GEN-10/GEN-11)"

  private val v2Base = IrV2.load(Paths.get("src/loom/examples/ring_4.json"))

  private def upgraded(name: String): IrV2 =
    Upgrade.v1ToV2(Ir.fromJson(Json.parse(
      Files.readString(Paths.get(s"src/loom/testdata/v1/$name.json"))))).ir

  it should "prove P-G1 ring_4 (v2) with dvmEnable=false: G1-SNP trivially true, no snp vertices (SEM-07)" in {
    val fg = FabricGraph.fromV2(v2Base)
    fg.errors shouldBe Nil
    fg.findCycle shouldBe None
    fg.datelines should contain("r3->r0") // explicit cut, forward direction
    fg.autoDatelines shouldBe Seq("r0->r3") // reverse cycle gets its own cut
    fg.channels.exists(_.cls == "snp") shouldBe false
    val r = ChiGates.runV2(v2Base)
    r.errors shouldBe Nil
    r.gates shouldBe Vector(
      "G1-REQ" -> true, "G1-RSP" -> true, "G1-DAT" -> true, "G1-SNP" -> true)
  }

  it should "expand both directions with four-tuple labels; ring links get h0/h1, others only h0 (SEM-13, AQ-02)" in {
    val fg = FabricGraph.fromV2(v2Base)
    fg.channels.map(_.id) should contain allOf(
      "r0->r1/req/h0", "r0->r1/req/h1", "r1->r0/dat/h1",
      "m0->r0/req/h0", "r0->m0/rsp/h0")
    // non-ring access links never carry a half-1 vertex
    fg.channels.exists(_.id == "m0->r0/req/h1") shouldBe false
    fg.channels.exists(_.id == "r0->m0/req/h1") shouldBe false
    // classes are independent: no cross-class dependency edges
    val clsOf = fg.channels.map(c => c.id -> c.cls).toMap
    fg.dependencies.foreach { case (f, tos) =>
      tos.foreach(t => clsOf(f) shouldBe clsOf(t))
    }
    // findCycle/toJson render the upgraded labels (GEN-11)
    val json = Canonical.pretty(fg.toJson)
    json should include("r0->r1/req/h0")
    json should include("\"class\": \"req\"")
    json should include("\"vc\": 1")
  }

  it should "prove all four classes when dvmEnable=true (P-G1, SEM-07/D11)" in {
    val v = v2Base.copy(
      chi = v2Base.chi.copy(dvmEnable = true),
      links = v2Base.links.map(l => l.copy(channels = l.channels :+ "snp")))
    val fg = FabricGraph.fromV2(v)
    fg.channels.exists(_.cls == "snp") shouldBe true
    fg.channels.map(_.id) should contain("r0->r1/snp/h1")
    fg.findCycle shouldBe None
    val r = ChiGates.runV2(v)
    r.errors shouldBe Nil
    r.gates shouldBe Vector(
      "G1-REQ" -> true, "G1-RSP" -> true, "G1-DAT" -> true, "G1-SNP" -> true)
  }

  it should "cut both direction cycles of a bidirectional ring (explicit + reverse autoDateline)" in {
    val v = upgraded("ring_3_bidir")
    val fg = FabricGraph.fromV2(v)
    fg.errors shouldBe Nil
    fg.findCycle shouldBe None
    fg.datelines.toSet shouldBe Set("r0->r1", "r0->r2")
    fg.autoDatelines shouldBe Seq("r0->r2")
    // every half transition is h0 -> h1 on a cut direction, never back
    val halfOf = fg.channels.map(c => c.id -> c.half).toMap
    fg.dependencies.foreach { case (f, tos) =>
      tos.foreach { t =>
        if (halfOf(f) != halfOf(t)) {
          halfOf(f) shouldBe 0
          halfOf(t) shouldBe 1
          f.stripSuffix("/h0") shouldBe t.stripSuffix("/h1")
          fg.datelines should contain(f.stripSuffix("/h0").split("/")(0))
        }
      }
    }
    ChiGates.runV2(v).errors shouldBe Nil
  }

  it should "reject a ring plus chord with CHK-38 (N-G1-a)" in {
    val v0 = v2Base
    val chord = v0.links.head.copy(from = "r0", to = "r2", dateline = false)
    val v = v0.copy(links = v0.links.map(l => l.copy(dateline = false)) :+ chord)
    val fg = FabricGraph.fromV2(v)
    fg.errors.exists(e =>
      e.startsWith("LM-G1: [CHK-38] CDG cycle: ") &&
      e.endsWith(" (not a ring; use a turn model or turn VCs)")) shouldBe true
    val r = ChiGates.runV2(v)
    r.gates.filter(_._1 != "G1-SNP").forall { case (_, ok) => !ok } shouldBe true
    r.gates.toMap.apply("G1-SNP") shouldBe true // not generated (dvmEnable=false)
  }

  it should "reject a router self-link with CHK-36 and per-class CHK-35 (N-G1-b)" in {
    val v = IrV2.fromJson(Json.parse(
      """{ "schema": "loom/v2", "meta": { "name": "self_link" },
        |  "clockDomains": [ { "id": "core", "freqMHz": 1000 } ],
        |  "chi": { "dvmEnable": false },
        |  "nodes": [
        |    { "id": "m0", "kind": "rn-i", "clock": "core", "attach": "r0" },
        |    { "id": "s0", "kind": "sn-f", "clock": "core", "attach": "r0" } ],
        |  "fabric": { "routers": [ { "name": "r0", "clock": "core" } ],
        |    "links": [ { "from": "m0", "to": "r0" }, { "from": "r0", "to": "r0" },
        |               { "from": "r0", "to": "s0" } ] } }""".stripMargin))
    val r = ChiGates.runV2(v)
    r.errors should contain("LM-G1: [CHK-36] self-link r0->r0 is not allowed")
    r.errors should contain(
      "LM-G1: [CHK-35] CDG cycle (req): r0->r0/req/h0 -> r0->r0/req/h0")
    r.errors should contain(
      "LM-G1: [CHK-35] CDG cycle (rsp): r0->r0/rsp/h0 -> r0->r0/rsp/h0")
    r.errors should contain(
      "LM-G1: [CHK-35] CDG cycle (dat): r0->r0/dat/h0 -> r0->r0/dat/h0")
    r.gates shouldBe Vector(
      "G1-REQ" -> false, "G1-RSP" -> false, "G1-DAT" -> false, "G1-SNP" -> true)
  }

  it should "reject a stray dateline on a chain link with CHK-37 (N-G1-c)" in {
    val v0 = upgraded("chain_4r")
    val v = v0.copy(links = v0.links.map(l =>
      if (l.from == "r1" && l.to == "r2") l.copy(dateline = true) else l))
    ChiGates.runV2(v).errors should contain(
      "LM-G1: [CHK-37] dateline link r1->r2 is not part of a ring")
    FabricGraph.fromV2(v).findCycle shouldBe None
  }

  it should "reject a dateline on a node access edge with CHK-37 (N-G1-c)" in {
    val v = v2Base.copy(links = v2Base.links.map(l =>
      if (l.from == "m0") l.copy(dateline = true) else l))
    ChiGates.runV2(v).errors should contain(
      "LM-G1: [CHK-37] dateline link m0->r0 is not part of a ring")
  }

  it should "accept the upgraded xy mesh via the turn model (SEM-09)" in {
    val v = upgraded("mesh_2x2_xy")
    val fg = FabricGraph.fromV2(v)
    fg.errors shouldBe Nil
    fg.findCycle shouldBe None
    fg.autoDatelines shouldBe Seq.empty
    ChiGates.runV2(v).errors shouldBe Nil
  }

  it should "keep G1-SNP trivially true with no snp vertices when dvmEnable=false despite snp channels (CHK-18/19 are C0's job)" in {
    val v = v2Base.copy(links = v2Base.links.map(l => l.copy(channels = l.channels :+ "snp")))
    ChecksV2.runC0(v).exists(_.contains("[CHK-19]")) shouldBe true
    val fg = FabricGraph.fromV2(v)
    fg.channels.exists(_.cls == "snp") shouldBe false
    val r = ChiGates.runV2(v)
    r.gates.toMap.apply("G1-SNP") shouldBe true
    r.errors shouldBe Nil
  }

  it should "wire G1 between C1 and C2 in ChecksV2.run (gate order per §6.1)" in {
    val r = ChecksV2.run(v2Base)
    r.errors shouldBe Nil
    r.gates.map(_._1) shouldBe Vector(
      "C0", "C1", "G1-REQ", "G1-RSP", "G1-DAT", "G1-SNP",
      "C2", "G4", "G6", "G7", "G8", "S1", "S2", "S3")
  }
}
