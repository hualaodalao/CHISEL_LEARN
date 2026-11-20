package loom.report

/** Generates a compilable SystemVerilog testbench skeleton from the port list
  * that firtool wrote into the emitted top module. Stimulus is left as a TODO;
  * every input port is tied to zero after reset so the skeleton simulates.
  */
object TbGen {
  final case class Port(name: String, width: Int, isInput: Boolean)

  /** Parse the flattened port list of `module <top>( ... );`. */
  def parsePorts(sv: String, top: String): Seq[Port] = {
    val marker = s"module $top("
    val start = sv.indexOf(marker)
    require(start >= 0, s"module $top not found")
    val body = sv.substring(start + marker.length)
    val end = body.indexOf("\n);")
    require(end >= 0, s"module $top has no port terminator")
    val lines = body.substring(0, end).split("\n").dropWhile(_.trim.isEmpty)

    var dir: Option[Boolean] = None // true = input
    var width = 1
    lines.toSeq.flatMap { raw =>
      val line = {
        val c = raw.indexOf("//")
        (if (c >= 0) raw.substring(0, c) else raw).trim.stripSuffix(",").trim
      }
      if (line.isEmpty) None
      else {
        val tokens = line.split("\\s+").toList
        val head = tokens.headOption.getOrElse("")
        val hasDir = head == "input" || head == "output" || head == "inout"
        if (hasDir) {
          dir = Some(head == "input")
          width = 1
        }
        val rest = if (hasDir) tokens.tail else tokens
        rest.find(_.startsWith("[")).foreach { w =>
          val inner = w.stripPrefix("[").stripSuffix("]")
          val Vector(hi, lo) = inner.split(":").toVector.map(_.trim.toInt)
          width = hi - lo + 1
        }
        val name = rest.lastOption.getOrElse("")
        dir.map(d => Port(name, width, d))
      }
    }
  }

  def testbench(top: String, sv: String): String = {
    val ports = parsePorts(sv, top)
    require(ports.exists(_.name == "clock"), s"$top has no clock port")
    require(ports.exists(_.name == "reset"), s"$top has no reset port")

    val decls = ports.map { p =>
      val rng = if (p.width > 1) s"[${p.width - 1}:0] " else ""
      s"  logic $rng${p.name};"
    }
    val drives = ports.filter(p => p.isInput && p.name != "clock" && p.name != "reset").map { p =>
      s"    ${p.name} = '0;"
    }
    val conns = ports.map(p => s"    .${p.name}(${p.name})")

    s"""// Loom testbench skeleton for $top.
       |// Generated from the emitted port list; deterministic for a given RTL.
       |// Defaults: all inputs zero after reset, clock 100 MHz (10 ns period).
       |module ${top}_tb;
       |${decls.mkString("\n")}
       |
       |  $top dut (
       |${conns.mkString(",\n")}
       |  );
       |
       |  initial begin
       |    clock = 1'b0;
       |    forever #5 clock = ~clock;
       |  end
       |
       |  initial begin
       |    reset = 1'b1;
       |${drives.mkString("\n")}
       |    repeat (4) @(posedge clock);
       |    reset = 1'b0;
       |    // TODO: drive master requests here and model slave responses.
       |    repeat (100) @(posedge clock);
       |    $$finish;
       |  end
       |endmodule
       |""".stripMargin
  }
}
