package loom.check

import loom.ir.Ir

/** Static gate aggregation shared by the CLI and the GUI API. */
object Gates {
  final case class Result(gates: Vector[(String, Boolean)], errors: List[String])

  def run(ir: Ir): Result = {
    val c1 = Checks.runC1(ir)
    val g1 = Checks.runG1(ir)
    val c2 = Checks.runC2(ir)
    val g4 = Checks.runG4(ir)
    val g6 = Checks.runG6(ir)
    val g7 = Checks.runG7(ir)
    val g8 = Checks.runG8(ir)
    val s1 = Checks.runS1(ir)
    val s2 = Checks.runS2(ir)
    val s3 = Checks.runS3(ir)
    Result(
      gates = Vector(
        "C0" -> true,
        "C1" -> c1.isEmpty,
        "G1" -> g1.isEmpty,
        "C2" -> c2.isEmpty,
        "G4" -> g4.isEmpty,
        "G6" -> g6.isEmpty,
        "G7" -> g7.isEmpty,
        "G8" -> g8.isEmpty,
        "S1" -> s1.isEmpty,
        "S2" -> s2.isEmpty,
        "S3" -> s3.isEmpty
      ),
      errors = c1 ++ g1 ++ c2 ++ g4 ++ g6 ++ g7 ++ g8 ++ s1 ++ s2 ++ s3
    )
  }
}
