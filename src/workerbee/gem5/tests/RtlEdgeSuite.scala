import chisel3._
import chisel3.simulator.HasSimulator
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

abstract class RtlEdgeSuite extends AnyFlatSpec with Matchers with ChiselSim with BeforeAndAfterAll {
  protected val cfg = WorkerBeeConfig()
  protected val output = Paths.get(sys.props("workerbee.calibration.output"))
  protected implicit val calibrationSimulator: HasSimulator = HasSimulator.simulators.verilator(
    verilatorSettings = svsim.verilator.Backend.CompilationSettings.default.withParallelism(
      Some(svsim.verilator.Backend.CompilationSettings.Parallelism.Uniform.default.withNum(2))))
  override def buildDir: Path = output.resolve("work")
  private lazy val trace = Files.newBufferedWriter(output.resolve(s"$suiteName.jsonl"),
    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
  private var traceOpened = false

  protected def sample(caseId: String, cycle: Int, signals: (String, Data)*): Map[String, BigInt] = {
    val values = signals.map { case (name, signal) => name -> signal.peek().litValue }.toMap
    val encoded = values.toSeq.sortBy(_._1).map { case (name, value) => s"\"$name\":$value" }.mkString(",")
    traceOpened = true
    trace.write(s"{\"case_id\":\"$caseId\",\"cycle\":$cycle,\"phase\":\"pre_edge\",\"signals\":{$encoded}}\n")
    trace.flush()
    values
  }

  protected def clearInputs(data: Data): Unit = data match {
    case b: Bool => b.poke(false.B)
    case s: SInt => s.poke(0.S)
    case u: UInt => u.poke(0.U)
    case e: EnumType => e.poke(BigInt(0))
    case v: Vec[_] => v.foreach(clearInputs)
    case r: Record => r.elements.values.foreach(clearInputs)
    case other => throw new IllegalArgumentException(s"Unsupported input: $other")
  }

  override protected def afterAll(): Unit = {
    try { if (traceOpened) trace.close() }
    finally { super.afterAll() }
  }
}
