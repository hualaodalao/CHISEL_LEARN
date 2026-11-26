package loom

import loom.check.ChecksV2
import loom.gen.ChiAssemble
import loom.graph.FabricGraph
import loom.ir.{AddressMap, Canonical, Ir, IrV2, JArr, JBool, JObj, JStr, JValue, Json, Upgrade}
import loom.report.Reports

/** Pure request handlers behind `loom serve`; no filesystem or HTTP here so
  * the GUI's contract is testable in-process.
  *
  * S5 (GEN-14–GEN-17, MIG-10): evaluate accepts loom/v1 (auto-upgrade via
  * Upgrade.v1ToV2 with MIG-07 deprecation warnings) and loom/v2. Every v1
  * key the GUI reads is preserved (schema/ok/hash/errors/warnings/checks/
  * estimate/addressMap/graph/canonical, including the legacy addressMap
  * shape); the v2 keys (upgraded/irSchema/nodes/chi/channels, addressMap.sam)
  * are additive. hash and canonical follow the normalized v2 caliber
  * (GEN-06/GEN-15): hash = IrV2.hash, canonical = pretty normalized v2.
  */
object LoomApi {
  def evaluate(text: String): JValue = {
    try {
      val root = Json.parse(text)
      val schema = root match {
        case o: JObj => o.get("schema").collect { case JStr(s) => s }
        case _ => None
      }
      schema match {
        case Some("loom/v1") =>
          val ir = Ir.fromJson(root)
          val up = Upgrade.v1ToV2(ir)
          val explicit = AddressMap.declared(ir.endpoints.filter(_.role == "slave"))
          respond(up.ir, up.warnings.toList, upgraded = true, explicitSam = explicit)
        case Some("loom/v2") =>
          val v2 = IrV2.fromJson(root)
          val explicit = root.asInstanceOf[JObj].get("sam").isDefined
          respond(v2, Nil, upgraded = false, explicitSam = explicit)
        case other =>
          failure(List(
            s"C0: [CHK-01] schema must be 'loom/v2' (or 'loom/v1' for upgrade), got '${other.getOrElse("")}'"), "")
      }
    } catch {
      case e: Throwable => failure(List(s"parse: ${e.getMessage}"), "")
    }
  }

  def evaluateText(text: String): String = Canonical.pretty(evaluate(text)) + "\n"

  /** Success / gate-failure response. C0 failure short-circuits to the
    * failure object (GEN-15); other gate errors keep the full report set so
    * the GUI can still render gates, estimate and topology.
    */
  private def respond(
      v2: IrV2,
      upgradeWarnings: List[String],
      upgraded: Boolean,
      explicitSam: Boolean
  ): JValue = {
    val hash = IrV2.hash(v2)
    val warnings = upgradeWarnings ++ Reports.timingWarningsV2(v2)
    val res = ChecksV2.run(v2)
    val c0Failed = res.gates.headOption.exists { case (k, ok) => k == "C0" && !ok }
    if (c0Failed) failure(res.errors, hash, warnings)
    else {
      // chi_nodes.json caliber (GEN-17); fall back to the IR-only rendering
      // when elaboration is unavailable (gate failures must not throw).
      val params =
        try Some(ChiAssemble.networkParams(v2))
        catch { case _: Throwable => None }
      val nodesJ = params.fold(Reports.chiNodesJsonV2(v2))(Reports.chiNodesJsonV2(v2, _))
      // GEN-21 (UP-01): connectivity is the RN x SN reachability matrix from
      // the final route tables; empty when elaboration is unavailable.
      val connectivityJ = JArr(params.toSeq.flatMap { p =>
        ChiAssemble.connectivity(v2, p).map { row =>
          JObj(Vector(
            "from" -> JStr(row.from),
            "to" -> JStr(row.to),
            "reachable" -> JBool(row.reachable),
            "path" -> JArr(row.path.map(JStr(_)).toVector)))
        }
      }.toVector)
      val base = Vector(
        "schema" -> JStr("loom/api/v1"),
        "ok" -> JBool(res.errors.isEmpty),
        "hash" -> JStr(hash),
        "errors" -> JArr(res.errors.map(JStr(_)).toVector),
        "warnings" -> JArr(warnings.map(JStr(_)).toVector),
        "checks" -> Reports.checksJsonV2(v2, hash, res.errors, warnings, res.gates),
        "estimate" -> Reports.estimateJsonV2(v2, hash),
        "addressMap" -> Reports.addressMapJsonV2(v2, explicitSam),
        "graph" -> FabricGraph.fromV2(v2).toJson,
        "canonical" -> JStr(Canonical.pretty(IrV2.toJson(v2))),
        "upgraded" -> JBool(upgraded),
        "irSchema" -> JStr("loom/v2"),
        "nodes" -> nodesJ,
        "chi" -> Reports.chiSnapshotJson(v2),
        "channels" -> JArr(Reports.channelsV2(v2).map(JStr(_)).toVector),
        "connectivity" -> connectivityJ,
        // GEN-23/GEN-24 (wave4): additive qosGen/adapt declaration-caliber
        // rows, same caliber as the estimate keys; consumed by HW-10 (RN
        // token buckets) and HW-12 (GEN ChiNiAdapt wrapper).
        "qosGen" -> Reports.qosGenJsonV2(v2),
        "adapt" -> Reports.adaptJsonV2(v2)
      )
      // GEN-21: normalized routes echo, omitted when empty (§3.12 rendering
      // rule); additive only, every existing GUI key stays (MIG-10).
      val withRoutes =
        if (v2.routes.isEmpty) base
        else base :+ ("routes" -> JArr(v2.routes.map(IrV2.routeJson).toVector))
      // GEN-25 (UP-07): additive hierarchical keys, same caliber as the
      // estimate rows; omitted on flat configs (§3.15 rendering rule).
      JObj(if (v2.subsystems.isEmpty) withRoutes
        else withRoutes ++ Vector(
          "subsystems" -> Reports.subsystemsJsonV2(v2),
          "nocLinks" -> JArr(v2.nocLinks.map(IrV2.nocLinkJson).toVector),
          "gatesBySubsystem" -> Reports.gatesBySubsystemJsonV2(v2)))
    }
  }

  private def failure(errors: List[String], hash: String, warnings: List[String] = Nil): JValue =
    JObj(Vector(
      "schema" -> JStr("loom/api/v1"),
      "ok" -> JBool(false),
      "hash" -> JStr(hash),
      "errors" -> JArr(errors.map(JStr(_)).toVector),
      "warnings" -> JArr(warnings.map(JStr(_)).toVector)
    ))
}