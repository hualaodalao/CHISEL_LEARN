import chisel3._
import circt.stage.ChiselStage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object WorkerBeeGem5P0Elaborate {
  def main(args: Array[String]): Unit = {
    require(args.length == 1, "Expected a new output directory")
    val output = Paths.get(args(0)).toAbsolutePath.normalize()
    require(!Files.exists(output), "Output directory must not already exist")
    Files.createDirectories(output)
    val cfg = WorkerBeeConfig()
    ChiselStage.emitSystemVerilogFile(
      gen = withModulePrefix("Wb") { new WorkerBee(cfg) },
      args = Array("--target-dir", output.toString)
    )
    val formatNames = Seq(
      DataFormat.INT8 -> "INT8", DataFormat.INT4 -> "INT4",
      DataFormat.MXE4M3 -> "MXE4M3", DataFormat.MXE5M2 -> "MXE5M2",
      DataFormat.MXE2M1 -> "MXE2M1", DataFormat.FP8E4M3 -> "FP8E4M3",
      DataFormat.FP8E5M2 -> "FP8E5M2"
    ).filter { case (fmt, _) => cfg.supportedFmts.contains(fmt) }.map(_._2).sorted
    require(formatNames.size == cfg.supportedFmts.size, "Unsupported P0 input format")
    val formats = formatNames.map(name => "\"" + name + "\"").mkString(",")
    val profile = s"""{
      "parameters": {
        "arrayN": ${cfg.arrayN}, "clusterM": ${cfg.clusterM},
        "aW": ${cfg.aW}, "bW": ${cfg.bW}, "cW": ${cfg.cW},
        "aBufferDepth": ${cfg.aBufferDepth}, "bBufferDepth": ${cfg.bBufferDepth},
        "cBufferDepth": ${cfg.cBufferDepth}, "addrWidth": ${cfg.addrWidth},
        "scaleBufferDepth": ${cfg.scaleBufferDepth}, "cDmaExtW": ${cfg.cDmaExtW},
        "supportedFmts": [$formats]
      },
      "derived": {
        "totalN": ${cfg.totalN}, "aExtW": ${cfg.aExtW}, "bExtW": ${cfg.bExtW},
        "cEffW": ${cfg.cEffW}, "cExtW": ${cfg.cExtW},
        "scaleAW": ${cfg.scaleAW}, "scaleBW": ${cfg.scaleBW},
        "cScaleRowW": ${cfg.cScaleRowW}, "cBeatsPerRowMax": ${cfg.cBeatsPerRowMax},
        "mnkWidth": ${cfg.mnkWidth}
      }
    }"""
    Files.write(output.resolve("hardware.json"), profile.getBytes(StandardCharsets.UTF_8))
  }
}
