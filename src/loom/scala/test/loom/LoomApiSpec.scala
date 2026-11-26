package loom

import java.nio.file.{Files, Paths}

import loom.ir.{Ir, IrV2, JArr, JBool, JNum, JObj, JStr, Json, Upgrade}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LoomApiSpec extends AnyFlatSpec with Matchers {
  private val text = Files.readString(Paths.get("src/loom/testdata/v1/minimal_4x4.json"))
  private val v2Text = Files.readString(Paths.get("src/loom/examples/ring_4.json"))

  private def fields(v: loom.ir.JValue): Map[String, loom.ir.JValue] = v match {
    case JObj(f) => f.toMap
    case other => fail(s"not an object: $other")
  }

  private def strs(v: loom.ir.JValue): Seq[String] = v match {
    case JArr(xs) => xs.collect { case JStr(s) => s }
    case other => fail(s"not a string array: $other")
  }

  // S5 migration: evaluate now answers in the v2 caliber (GEN-14/GEN-15).
  // canonical is the normalized loom/v2 text and hash = IrV2.hash (GEN-06,
  // OQ-07 ruling: one-time baseline invalidation), so the legacy round-trip
  // asserts against the upgraded object instead of the raw v1 root.
  "LoomApi" should "return the normalized v2 canonical with a stable hash (GEN-15/GEN-06)" in {
    val r = fields(LoomApi.evaluate(text))
    r("ok") shouldBe JBool(true)
    r("upgraded") shouldBe JBool(true)
    val canonical = r("canonical").asInstanceOf[JStr].value
    fields(Json.parse(canonical))("schema") shouldBe JStr("loom/v2")

    val again = fields(LoomApi.evaluate(canonical))
    again("hash") shouldBe r("hash")
    again("ok") shouldBe JBool(true)
    again("upgraded") shouldBe JBool(false)
  }

  it should "surface C0 errors without throwing (CHK-04 via the v2 gates)" in {
    val bad = text.replaceFirst("\"id\"\\s*:\\s*\"s1\"", "\"id\": \"m0\"")
    val r = fields(LoomApi.evaluate(bad))
    r("ok") shouldBe JBool(false)
    r("errors") match {
      case JArr(xs) => xs.exists { case JStr(s) => s.contains("[CHK-04]") && s.contains("duplicate"); case _ => false } shouldBe true
      case other => fail(s"unexpected errors: $other")
    }
  }

  it should "report a parse failure as a normal response" in {
    val r = fields(LoomApi.evaluate("{"))
    r("ok") shouldBe JBool(false)
    r("errors") match {
      case JArr(xs) => xs.exists { case JStr(s) => s.startsWith("parse:"); case _ => false } shouldBe true
      case other => fail(s"unexpected errors: $other")
    }
  }

  it should "include gates, estimate and topology for a valid config (GEN-17)" in {
    val r = fields(LoomApi.evaluate(text))
    val checks = fields(r("checks"))
    checks("ok") shouldBe JBool(true)
    val gates = fields(checks("gates"))
    // GEN-11: the G1 key set is the four channel classes; G7 stays true.
    gates("G1-REQ") shouldBe JBool(true)
    gates("G1-RSP") shouldBe JBool(true)
    gates("G1-DAT") shouldBe JBool(true)
    gates("G1-SNP") shouldBe JBool(true)
    gates("G7") shouldBe JBool(true)

    val estimate = fields(r("estimate"))
    val totals = fields(estimate("totals"))
    // minimal_4x4 upgrades to 8 nodes on one router: derived 8x8 ports.
    totals("crosspoints") shouldBe JNum(BigDecimal(64))
    totals("datWidth") shouldBe JNum(BigDecimal(256))

    val graph = fields(r("graph"))
    graph("acyclic") shouldBe JBool(true)
  }

  it should "expose the route-decode address map for slaves in order (legacy keys, MIG-10)" in {
    val r = fields(LoomApi.evaluate(text))
    val map = fields(r("addressMap"))
    map("routeShift") shouldBe JNum(BigDecimal(12))
    map("stride") shouldBe JNum(BigDecimal(4096))
    map("slaves") match {
      case JArr(xs) =>
        xs.length shouldBe 4
        val first = fields(xs.head)
        first("id") shouldBe JStr("s0")
        first("base") shouldBe JStr("0x0")
        val last = fields(xs.last)
        last("id") shouldBe JStr("s3")
        last("base") shouldBe JStr("0x3000")
      case other => fail(s"unexpected slaves: $other")
    }
  }

  it should "ship a zero-build GUI wired to the evaluate API" in {
    val html = Files.readString(Paths.get("src/loom/gui/index.html"))
    html should include("app.js")
    html should include("type=\"module\"")
    html should include("editor")
    html should include("structure")
    val js = Files.readString(Paths.get("src/loom/gui/app.js"))
    js should include("add-partition")
    js should include("add-clock")
    js should include("./model.mjs")
    js should include("./api.mjs")
    // G7: the network layer lives in api.mjs (ES module split, still zero-build).
    val api = Files.readString(Paths.get("src/loom/gui/api.mjs"))
    api should include("/api/evaluate")
    api should include("/api/examples")
    api should include("/api/example?name=")
    api should include("/api/workspace")
    api should include("/api/draft")
    html should include("ws-dialog")
    Files.exists(Paths.get("src/loom/gui/style.css")) shouldBe true
    Files.exists(Paths.get("src/loom/gui/model.mjs")) shouldBe true
  }

  behavior of "LoomApi (loom/v2 caliber, GEN-14-GEN-17)"

  it should "accept loom/v2 input and expose nodes/chi/channels/irSchema (GEN-15)" in {
    val r = fields(LoomApi.evaluate(v2Text))
    r("ok") shouldBe JBool(true)
    r("upgraded") shouldBe JBool(false)
    r("irSchema") shouldBe JStr("loom/v2")
    r("hash") shouldBe JStr(IrV2.hash(IrV2.load(Paths.get("src/loom/examples/ring_4.json"))))

    // nodes = chi_nodes.json caliber: rn/sn tables with kind/nodeId/attach.
    val nodes = fields(r("nodes"))
    val rn0 = fields(nodes("rn").asInstanceOf[JArr].items.head)
    rn0("id") shouldBe JStr("m0")
    rn0("kind") shouldBe JStr("rn-i")
    rn0("nodeId") shouldBe JNum(BigDecimal(0))
    rn0("attach") shouldBe JStr("r0")
    val sn0 = fields(nodes("sn").asInstanceOf[JArr].items.head)
    sn0("nodeId") shouldBe JNum(BigDecimal(2))
    nodes("snpEnable") shouldBe JBool(false)

    // chi snapshot with the ENC-01/05 derived quantities.
    val chi = fields(r("chi"))
    chi("datWidth") shouldBe JNum(BigDecimal(256))
    chi("beWidth") shouldBe JNum(BigDecimal(32))
    chi("flitCount64B") shouldBe JNum(BigDecimal(2))
    chi("maxNodes") shouldBe JNum(BigDecimal(128))
    chi("lcreditDepth") shouldBe JNum(BigDecimal(4))

    // SEM-07/D11: dvmEnable=false => no snp in the generated class list.
    strs(r("channels")) shouldBe Seq("req", "rsp", "dat")
    strs(r("warnings")) shouldBe empty
  }

  it should "carry estimate datWidth/lcreditDepth/flowControl/channels (GEN-17 estimate row)" in {
    val r = fields(LoomApi.evaluate(v2Text))
    val estimate = fields(r("estimate"))
    fields(estimate("totals"))("datWidth") shouldBe JNum(BigDecimal(256))
    fields(estimate("totals"))("flitCount64B") shouldBe JNum(BigDecimal(2))
    val fc = fields(estimate("flowControl"))
    fc("lcreditDepth") shouldBe JNum(BigDecimal(4))
    fc("txnIdSpace") shouldBe JNum(BigDecimal(256))
    strs(estimate("channels")) shouldBe Seq("req", "rsp", "dat")
    val link0 = fields(estimate("links").asInstanceOf[JArr].items.head)
    strs(link0("channels")) should contain("req")
    link0("lcreditDepth") shouldBe JNum(BigDecimal(4))
  }

  it should "render the addressMap as the SAM with tgtNode/tgtId (GEN-15 addressMap row)" in {
    val r = fields(LoomApi.evaluate(v2Text))
    val map = fields(r("addressMap"))
    map("explicit") shouldBe JBool(true)
    val sam = map("sam").asInstanceOf[JArr].items.map(fields)
    sam.length shouldBe 2
    sam.head("base") shouldBe JNum(BigDecimal(0))
    sam.head("size") shouldBe JNum(BigDecimal(4096))
    sam.head("tgtNode") shouldBe JStr("s0")
    sam.head("tgtId") shouldBe JNum(BigDecimal(2))
    sam(1)("tgtNode") shouldBe JStr("s1")
    sam(1)("tgtId") shouldBe JNum(BigDecimal(3))
    // legacy shape stays derivable (MIG-10): slaves[] mirrors the windows.
    val slave0 = fields(map("slaves").asInstanceOf[JArr].items.head)
    slave0("id") shouldBe JStr("s0")
    slave0("base") shouldBe JStr("0x0")
    slave0("end") shouldBe JStr("0xfff")
  }

  it should "carry the fixed MIG-07 deprecation warning for v1 input (GEN-14/MIG-07)" in {
    val r = fields(LoomApi.evaluate(text))
    strs(r("warnings")) should contain(Upgrade.SchemaWarning)
  }

  it should "hash the upgraded normalized v2 object, not the raw root (GEN-06)" in {
    val upgraded = Upgrade.v1ToV2(Ir.fromJson(Json.parse(text))).ir
    fields(LoomApi.evaluate(text))("hash") shouldBe JStr(IrV2.hash(upgraded))
  }

  it should "keep every GUI-consumed v1 key on a v1 example (MIG-10/GEN-16 additive-only)" in {
    val r = fields(LoomApi.evaluate(text))
    // Top-level keys read by gui/app.js (evaluate/showGates/showEstimate/
    // showAddressMap/showTopology/canonical button).
    Seq("schema", "ok", "hash", "errors", "warnings", "checks", "estimate",
      "addressMap", "graph", "canonical").foreach(k => r.contains(k) shouldBe true)
    r("schema") shouldBe JStr("loom/api/v1")
    fields(r("checks")).contains("gates") shouldBe true
    val estimate = fields(r("estimate"))
    estimate.contains("totals") shouldBe true
    estimate.contains("assumptions") shouldBe true
    val map = fields(r("addressMap"))
    Seq("explicit", "routeShift", "stride", "slaves").foreach(k => map.contains(k) shouldBe true)
    map("slaves").asInstanceOf[JArr].items.foreach { s =>
      Seq("id", "index", "base", "size", "end").foreach(k => fields(s).contains(k) shouldBe true)
    }
    val graph = fields(r("graph"))
    Seq("nodes", "channels", "cdg", "datelines", "autoDatelines", "acyclic")
      .foreach(k => graph.contains(k) shouldBe true)
    graph("channels").asInstanceOf[JArr].items.foreach { c =>
      fields(c).contains("from") shouldBe true
      fields(c).contains("to") shouldBe true
    }
  }

  it should "return only the failure keys on a v2 C0 error (GEN-15, CHK-13)" in {
    val bad = v2Text.replaceFirst("\"datWidth\": 256", "\"datWidth\": 64")
    val r = fields(LoomApi.evaluate(bad))
    r("ok") shouldBe JBool(false)
    r.keySet shouldBe Set("schema", "ok", "hash", "errors", "warnings")
    strs(r("errors")).exists(_.contains("[CHK-13]")) shouldBe true
    r("hash").asInstanceOf[JStr].value should not be empty
  }

  // ---- UP-01 additive routes/connectivity keys (GEN-21, MIG-10) ----

  behavior of "LoomApi routes/connectivity (GEN-21, UP-01)"

  it should "expose connectivity and keep every existing key on a routes-free config" in {
    val r = fields(LoomApi.evaluate(v2Text))
    r.contains("routes") shouldBe false // §3.12: omitted when empty
    val conn = r("connectivity").asInstanceOf[JArr].items.map(fields)
    conn.length shouldBe 4
    conn.foreach { c =>
      Seq("from", "to", "reachable", "path").foreach(k => c.contains(k) shouldBe true)
      c("reachable") shouldBe JBool(true)
    }
    val m0s0 = conn.find(c => c("from") == JStr("m0") && c("to") == JStr("s0")).get
    strs(m0s0("path")) shouldBe Seq("r0", "r1", "r2")
    // additive only: every pre-UP-01 key stays (MIG-10)
    Seq("schema", "ok", "hash", "errors", "warnings", "checks", "estimate",
      "addressMap", "graph", "canonical", "upgraded", "irSchema", "nodes", "chi",
      "channels").foreach(k => r.contains(k) shouldBe true)
    r("hash") shouldBe JStr(IrV2.hash(IrV2.load(Paths.get("src/loom/examples/ring_4.json"))))
  }

  it should "echo normalized routes and reroute connectivity for an explicit path" in {
    val withRoutes = v2Text.replaceFirst("\"meta\"",
      "\"routes\": [ { \"from\": \"m0\", \"to\": \"s0\", \"path\": [\"r0\", \"r3\", \"r2\"] } ], \"meta\"")
    val r = fields(LoomApi.evaluate(withRoutes))
    r("ok") shouldBe JBool(true)
    val routes = r("routes").asInstanceOf[JArr].items.map(fields)
    routes.length shouldBe 1
    routes.head("from") shouldBe JStr("m0")
    routes.head("to") shouldBe JStr("s0")
    strs(routes.head("path")) shouldBe Seq("r0", "r3", "r2")
    routes.head("forbid") shouldBe JBool(false) // normalized default echo
    val conn = r("connectivity").asInstanceOf[JArr].items.map(fields)
    val m0s0 = conn.find(c => c("from") == JStr("m0") && c("to") == JStr("s0")).get
    strs(m0s0("path")) shouldBe Seq("r0", "r3", "r2")
    // canonical carries the routes block; the hash moves off the empty baseline
    r("canonical").asInstanceOf[JStr].value should include("\"routes\"")
    r("hash").asInstanceOf[JStr].value should not be
      IrV2.hash(IrV2.load(Paths.get("src/loom/examples/ring_4.json")))
  }

  // CHK-51 retired (ir_gen_spec v1.2.0, SEM-17): a reachable forbid is legal
  // (executed via per-source SAM + NDERR); connectivity reports it unreachable.
  it should "accept a reachable forbid and report it unreachable in connectivity (v1.2.0)" in {
    val cfg = v2Text.replaceFirst("\"meta\"",
      "\"routes\": [ { \"from\": \"m0\", \"to\": \"s0\", \"forbid\": true } ], \"meta\"")
    val r = fields(LoomApi.evaluate(cfg))
    r("ok") shouldBe JBool(true)
    strs(r("errors")) shouldBe Nil
    r.contains("connectivity") shouldBe true
    r.contains("routes") shouldBe true
    val forbid = r("routes").asInstanceOf[JArr].items.map(fields).head
    forbid("forbid") shouldBe JBool(true)
    forbid.contains("path") shouldBe false
    val conn = r("connectivity").asInstanceOf[JArr].items.map(fields)
    val m0s0 = conn.find(c => c("from") == JStr("m0") && c("to") == JStr("s0")).get
    m0s0("reachable") shouldBe JBool(false)
    strs(m0s0("path")) shouldBe Nil
    // every other pair keeps its BFS reachability
    conn.filterNot(c => c("from") == JStr("m0") && c("to") == JStr("s0"))
      .foreach(c => c("reachable") shouldBe JBool(true))
  }

  // ---- S8 additive qosGen/adapt keys (GEN-23/GEN-24, UP-02/UP-08) ----

  behavior of "LoomApi qosGen/adapt (GEN-23/GEN-24, S8)"

  it should "expose empty qosGen and the SEM-19 mismatch list additively on the baseline" in {
    val r = fields(LoomApi.evaluate(v2Text))
    r("ok") shouldBe JBool(true)
    // no qosGen declared: empty array, key still present (additive contract)
    r("qosGen").asInstanceOf[JArr].items shouldBe empty
    // ring_4 nodes are 128-bit on a 256-bit DAT fabric: all four mismatch,
    // frozen at the IR-79 default depth 4 (assembly caliber, not rendered IR)
    val adapt = r("adapt").asInstanceOf[JArr].items.map(fields)
    adapt.length shouldBe 4
    adapt.foreach { a =>
      Seq("node", "dataWidth", "datWidth", "clock", "routerClock", "fifoDepth", "mismatch")
        .foreach(k => a.contains(k) shouldBe true)
      a("dataWidth") shouldBe JNum(BigDecimal(128))
      a("datWidth") shouldBe JNum(BigDecimal(256))
      a("fifoDepth") shouldBe JNum(BigDecimal(4))
      a("mismatch") shouldBe JBool(true)
    }
    // estimate carries the same two keys with identical content
    val estimate = fields(r("estimate"))
    estimate("qosGen") shouldBe r("qosGen")
    estimate("adapt") shouldBe r("adapt")
    // additive only: every pre-S8 key stays and the hash baseline is unchanged
    Seq("schema", "ok", "hash", "errors", "warnings", "checks", "estimate",
      "addressMap", "graph", "canonical", "upgraded", "irSchema", "nodes", "chi",
      "channels", "connectivity").foreach(k => r.contains(k) shouldBe true)
    r("hash") shouldBe JStr(IrV2.hash(IrV2.load(Paths.get("src/loom/examples/ring_4.json"))))
    r("canonical").asInstanceOf[JStr].value should not include "qosGen"
    r("canonical").asInstanceOf[JStr].value should not include "adaptFifoDepth"
  }

  it should "echo declared qosGen/adaptFifoDepth with declaration-only assumptions (HW-10/HW-12)" in {
    val cfg = v2Text
      .replaceFirst("\"attach\": \"r0\"",
        "\"attach\": \"r0\", \"qosGen\": { \"kind\": \"limiter\", \"bandwidth\": 1000, \"saturation\": 4096 }")
      .replaceFirst("\"attach\": \"r1\"", "\"attach\": \"r1\", \"adaptFifoDepth\": 8")
    val r = fields(LoomApi.evaluate(cfg))
    r("ok") shouldBe JBool(true)
    val qos = r("qosGen").asInstanceOf[JArr].items.map(fields)
    qos.length shouldBe 1
    qos.head("node") shouldBe JStr("m0")
    qos.head("kind") shouldBe JStr("limiter")
    qos.head("bandwidth") shouldBe JNum(BigDecimal(1000))
    qos.head("saturation") shouldBe JNum(BigDecimal(4096))
    val m1 = r("adapt").asInstanceOf[JArr].items.map(fields)
      .find(a => a("node") == JStr("m1")).get
    m1("fifoDepth") shouldBe JNum(BigDecimal(8))
    m1("mismatch") shouldBe JBool(true) // width mismatch stays (128 vs 256)
    val assumptions = strs(fields(r("estimate"))("assumptions"))
    assumptions.exists(s => s.contains("HW-10") && s.contains("no RTL effect")) shouldBe true
    assumptions.exists(s => s.contains("HW-12") && s.contains("no RTL effect")) shouldBe true
    // the declaration is a real IR change: hash moves off the empty baseline
    r("hash").asInstanceOf[JStr].value should not be
      IrV2.hash(IrV2.load(Paths.get("src/loom/examples/ring_4.json")))
  }

  it should "keep adapt empty on a fully matched fabric without declarations" in {
    val v0 = IrV2.load(Paths.get("src/loom/examples/ring_4.json"))
    val matched = v0.copy(nodes = v0.nodes.map(_.copy(dataWidth = 256)))
    val r = fields(LoomApi.evaluate(loom.ir.Canonical.pretty(IrV2.toJson(matched))))
    r("ok") shouldBe JBool(true)
    r("qosGen").asInstanceOf[JArr].items shouldBe empty
    r("adapt").asInstanceOf[JArr].items shouldBe empty
  }
}