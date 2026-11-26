package loom.chi

import loom.ir.{Ir, IrV2}
import loom.graph.FabricGraph

/** CHI static gates. G1 reuses the topology CDG (SCC + dateline cuts) from
  * FabricGraph; the four channel classes share the same routing topology, so
  * the deadlock proof is per-class over that CDG. Following D11 ("prove only
  * what is generated"), SNP is纳入 only when DVM is generated; otherwise it
  * is not instantiated and its gate is trivially satisfied.
  */
object ChiGates {
  final case class Result(gates: Vector[(String, Boolean)], errors: List[String])

  def run(ir: Ir, snpEnable: Boolean): Result = {
    val fg = FabricGraph.fromIr(ir)
    val g1ok = fg.findCycle.isEmpty && fg.errors.isEmpty
    val base = Vector("G1-REQ" -> g1ok, "G1-RSP" -> g1ok, "G1-DAT" -> g1ok)
    val snp = if (snpEnable) Vector("G1-SNP" -> g1ok) else Vector("G1-SNP" -> true)
    Result(base ++ snp, fg.errors.toList)
  }

  /** G1 sole entry for loom/v2 (GEN-11, S3): per-class proof over the
    * four-tuple CDG vertices of FabricGraph.fromV2. Keys are fixed
    * G1-REQ/G1-RSP/G1-DAT/G1-SNP; a class passes iff its own subgraph is
    * acyclic after the dateline cuts and there are no structural errors
    * (CHK-36/37/38). snpEnable=false ⇒ G1-SNP trivially true (D11/SEM-07).
    */
  def runV2(v2: IrV2): Result = {
    val fg = FabricGraph.fromV2(v2)
    val snpEnable = v2.chi.dvmEnable
    val structural = fg.errors.toList
    val errs = scala.collection.mutable.ListBuffer.empty[String]
    errs ++= structural

    // CHK-35: per-class deterministic DFS after the cuts.
    def classOk(cls: String): Boolean = {
      val cycle = fg.findCycleOfClass(cls)
      cycle.foreach(path =>
        errs += s"LM-G1: [CHK-35] CDG cycle ($cls): ${path.mkString(" -> ")}")
      cycle.isEmpty && structural.isEmpty
    }

    val base = Vector(
      "G1-REQ" -> classOk("req"),
      "G1-RSP" -> classOk("rsp"),
      "G1-DAT" -> classOk("dat"))

    // CHK-39: SNP vertex presence must match dvmEnable (cross-check against
    // link.channels). User-level snp/dvmEnable conflicts are C0 CHK-18/19;
    // this guards the generation inclusion itself.
    val declaredSnp = v2.links.exists(_.channels.contains("snp"))
    val snpVertices = fg.channels.exists(_.cls == "snp")
    val inclusionOk = snpVertices == (snpEnable && declaredSnp)
    if (!inclusionOk)
      errs += s"LM-G1: [CHK-39] SNP proof inclusion does not match dvmEnable=$snpEnable"
    val snpOk = (if (snpEnable) classOk("snp") else true) && inclusionOk

    Result(base :+ ("G1-SNP" -> snpOk), errs.toList)
  }
}