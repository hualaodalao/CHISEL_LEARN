package loom

import loom.check.{Checks, Gates}
import loom.graph.FabricGraph
import loom.ir.{Canonical, Ir, JArr, JBool, JObj, JStr, JValue, Json}
import loom.report.Reports

/** Pure request handlers behind `loom serve`; no filesystem or HTTP here so
  * the GUI's contract is testable in-process.
  */
object LoomApi {
  def evaluate(text: String): JValue = {
    try {
      val root = Json.parse(text)
      val hash = Canonical.hash(root)
      val c0 = Checks.run(root)
      if (c0.nonEmpty) failure(c0, hash)
      else {
        val ir = Ir.fromJson(root)
        val g = Gates.run(ir)
        val warnings = Reports.timingWarnings(ir)
        JObj(Vector(
          "schema" -> JStr("loom/api/v1"),
          "ok" -> JBool(g.errors.isEmpty),
          "hash" -> JStr(hash),
          "errors" -> JArr(g.errors.map(JStr(_)).toVector),
          "warnings" -> JArr(warnings.map(JStr(_)).toVector),
          "checks" -> Reports.checksJson(ir, hash, g.errors, warnings, g.gates),
          "estimate" -> Reports.estimateJson(ir, hash),
          "addressMap" -> Reports.addressMapJson(ir),
          "graph" -> FabricGraph.fromIr(ir).toJson,
          "canonical" -> JStr(Canonical.pretty(root))
        ))
      }
    } catch {
      case e: Throwable => failure(List(s"parse: ${e.getMessage}"), "")
    }
  }

  def evaluateText(text: String): String = Canonical.pretty(evaluate(text)) + "\n"

  private def failure(errors: List[String], hash: String): JValue =
    JObj(Vector(
      "schema" -> JStr("loom/api/v1"),
      "ok" -> JBool(false),
      "hash" -> JStr(hash),
      "errors" -> JArr(errors.map(JStr(_)).toVector),
      "warnings" -> JArr(Vector.empty)
    ))
}
