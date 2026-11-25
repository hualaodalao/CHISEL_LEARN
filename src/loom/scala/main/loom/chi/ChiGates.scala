package loom.chi

import loom.ir.Ir
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
}