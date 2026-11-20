package loom

import java.nio.file.{Files, Paths}

import loom.ir.{Canonical, JArr, JBool, JNum, JObj, JStr, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LoomApiSpec extends AnyFlatSpec with Matchers {
  private val text = Files.readString(Paths.get("src/loom/examples/minimal_4x4.json"))

  private def fields(v: loom.ir.JValue): Map[String, loom.ir.JValue] = v match {
    case JObj(f) => f.toMap
    case other => fail(s"not an object: $other")
  }

  "LoomApi" should "round-trip the IR through its canonical form" in {
    val r = fields(LoomApi.evaluate(text))
    r("ok") shouldBe JBool(true)
    val canonical = r("canonical").asInstanceOf[JStr].value
    Canonical.render(Json.parse(canonical)) shouldBe Canonical.render(Json.parse(text))

    val again = fields(LoomApi.evaluate(canonical))
    again("hash") shouldBe r("hash")
    again("ok") shouldBe JBool(true)
  }

  it should "surface C0 errors without throwing" in {
    val bad = text.replaceFirst("\"id\"\\s*:\\s*\"s1\"", "\"id\": \"m0\"")
    val r = fields(LoomApi.evaluate(bad))
    r("ok") shouldBe JBool(false)
    r("errors") match {
      case JArr(xs) => xs.exists { case JStr(s) => s.contains("duplicate endpoint id"); case _ => false } shouldBe true
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

  it should "include gates, estimate and topology for a valid config" in {
    val r = fields(LoomApi.evaluate(text))
    val checks = fields(r("checks"))
    checks("ok") shouldBe JBool(true)
    fields(checks("gates")).apply("G1") shouldBe JBool(true)

    val estimate = fields(r("estimate"))
    val totals = fields(estimate("totals"))
    totals("crosspoints") shouldBe JNum(BigDecimal(16))

    val graph = fields(r("graph"))
    graph("acyclic") shouldBe JBool(true)
  }

  it should "expose the route-decode address map for slaves in order" in {
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
    html should include("editor")
    html should include("structure")
    val js = Files.readString(Paths.get("src/loom/gui/app.js"))
    js should include("/api/evaluate")
    js should include("/api/examples")
    js should include("/api/example?name=")
    js should include("add-partition")
    js should include("add-clock")
    js should include("/api/workspace")
    js should include("/api/draft")
    html should include("ws-dialog")
    Files.exists(Paths.get("src/loom/gui/style.css")) shouldBe true
  }
}
