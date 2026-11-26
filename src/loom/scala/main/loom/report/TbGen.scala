package loom.report

import loom.chi.{ChiOp, ChiWidths}
import loom.ir.ChiBlockV2

/** Generates a compilable SystemVerilog testbench skeleton from the port list
  * that firtool wrote into the emitted top module. Stimulus is left as a TODO;
  * every input port is tied to zero after reset so the skeleton simulates.
  *
  * `testbench` is the legacy v1 skeleton (kept for legacy tests until S6);
  * `testbenchV2` is the CHI caliber (S5, GEN-17 tb row): ReadNoSnp /
  * WriteNoSnpFull transaction skeletons with TxnID/NodeID fields and DAT flit
  * counts derived from chi.datWidth (ENC-01).
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

  /** Shared skeleton frame: declarations, zeroed input drives and DUT
    * connections derived from the emitted port list.
    */
  private def portFrame(top: String, sv: String): (String, String, String) = {
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
    (decls.mkString("\n"), drives.mkString("\n"), conns.mkString(",\n"))
  }

  def testbench(top: String, sv: String): String = {
    val (decls, drives, conns) = portFrame(top, sv)

    s"""// Loom testbench skeleton for $top.
       |// Generated from the emitted port list; deterministic for a given RTL.
       |// Defaults: all inputs zero after reset, clock 100 MHz (10 ns period).
       |module ${top}_tb;
       |$decls
       |
       |  $top dut (
       |$conns
       |  );
       |
       |  initial begin
       |    clock = 1'b0;
       |    forever #5 clock = ~clock;
       |  end
       |
       |  initial begin
       |    reset = 1'b1;
       |$drives
       |    repeat (4) @(posedge clock);
       |    reset = 1'b0;
       |    // TODO: drive master requests here and model slave responses.
       |    repeat (100) @(posedge clock);
       |    $$finish;
       |  end
       |endmodule
       |""".stripMargin
  }

  /** CHI stimulus skeleton (S5, GEN-17 tb row). Transaction flows follow the
    * protocol spec: ReadNoSnp = REQ -> CompData DAT flits; WriteNoSnpFull =
    * REQ -> DBIDResp -> NonCopyBackWrData DAT flits (TxnID=DBID) -> Comp.
    * Flit counts come from chi.datWidth via ChiWidths.flitCount (ENC-01:
    * 128->4, 256->2, 512->1); TxnID/DBID share chi.txnIdWidth (ENC-04) and
    * SrcID/TgtID use chi.nodeIdWidth (ENC-05). Opcode localparams mirror the
    * Loom-internal ChiOp encodings. Bodies are TODO skeletons that only
    * advance cycles, so the testbench compiles and simulates as-is.
    */
  def testbenchV2(top: String, sv: String, chi: ChiBlockV2): String = {
    val (decls, drives, conns) = portFrame(top, sv)
    val flits = ChiWidths.flitCount(64, chi.datWidth)
    val be = ChiWidths.beWidth(chi.datWidth)

    s"""// Loom CHI testbench skeleton for $top (loom/v2, GEN-17 tb row).
       |// Generated from the emitted port list; deterministic for a given RTL.
       |// Defaults: all inputs zero after reset, clock 100 MHz (10 ns period).
       |// Stimulus: CHI ReadNoSnp / WriteNoSnpFull transaction skeletons.
       |module ${top}_tb;
       |$decls
       |
       |  $top dut (
       |$conns
       |  );
       |
       |  // CHI configuration snapshot (IR-35/36/37/38) and ENC-01 derivations.
       |  localparam int DAT_WIDTH = ${chi.datWidth};
       |  localparam int BE_WIDTH = $be;
       |  localparam int FLITS_64B = $flits;
       |  localparam int TXN_ID_WIDTH = ${chi.txnIdWidth};
       |  localparam int NODE_ID_WIDTH = ${chi.nodeIdWidth};
       |  localparam int ADDR_WIDTH = ${chi.addrWidth};
       |
       |  // Loom-internal opcode encodings (chi/ChiDefs.scala ChiOp).
       |  localparam logic [6:0] OP_READ_NO_SNP = 7'h${ChiOp.ReadNoSnp.toHexString};
       |  localparam logic [6:0] OP_WRITE_NO_SNP_FULL = 7'h${ChiOp.WriteNoSnpFull.toHexString};
       |  localparam logic [4:0] OP_DBID_RESP = 5'h${ChiOp.RspDBIDResp.toHexString};
       |  localparam logic [4:0] OP_COMP = 5'h${ChiOp.RspComp.toHexString};
       |  localparam logic [3:0] OP_COMP_DATA = 4'h${ChiOp.DatCompData.toHexString};
       |  localparam logic [3:0] OP_NON_COPY_BACK_WR_DATA = 4'h${ChiOp.DatNonCopyBackWrData.toHexString};
       |
       |  // ReadNoSnp: REQ(ReadNoSnp, TxnID, SrcID, TgtID=SAM(addr), Addr, Size=6)
       |  //   -> DAT(CompData) x FLITS_64B; DataID locates the 16B chunk (ENC-02).
       |  task automatic chi_read_no_snp(
       |      input logic [TXN_ID_WIDTH-1:0] txn_id,
       |      input logic [NODE_ID_WIDTH-1:0] src_id,
       |      input logic [NODE_ID_WIDTH-1:0] tgt_id,
       |      input logic [ADDR_WIDTH-1:0] addr);
       |    // TODO: drive the REQ flit (OP_READ_NO_SNP) on the RN REQ channel.
       |    @(posedge clock);
       |    for (int f = 0; f < FLITS_64B; f++) begin
       |      // TODO: sample CompData DAT flit f (OP_COMP_DATA, DataID per ENC-02).
       |      @(posedge clock);
       |    end
       |  endtask
       |
       |  // WriteNoSnpFull: REQ(WriteNoSnpFull, TxnID, SrcID, TgtID, Addr, Size=6)
       |  //   -> RSP(DBIDResp, DBID) -> DAT(NonCopyBackWrData, TxnID=DBID)
       |  //   x FLITS_64B (BE per ENC-03) -> RSP(Comp, RespErr per ENC-07).
       |  task automatic chi_write_no_snp_full(
       |      input logic [TXN_ID_WIDTH-1:0] txn_id,
       |      input logic [NODE_ID_WIDTH-1:0] src_id,
       |      input logic [NODE_ID_WIDTH-1:0] tgt_id,
       |      input logic [ADDR_WIDTH-1:0] addr);
       |    // TODO: drive the REQ flit (OP_WRITE_NO_SNP_FULL).
       |    @(posedge clock);
       |    // TODO: wait for OP_DBID_RESP; the data flits echo TxnID=DBID.
       |    @(posedge clock);
       |    for (int f = 0; f < FLITS_64B; f++) begin
       |      // TODO: drive NonCopyBackWrData flit f (OP_NON_COPY_BACK_WR_DATA).
       |      @(posedge clock);
       |    end
       |    // TODO: wait for OP_COMP (RespErr=00 OK).
       |    @(posedge clock);
       |  endtask
       |
       |  initial begin
       |    clock = 1'b0;
       |    forever #5 clock = ~clock;
       |  end
       |
       |  initial begin
       |    reset = 1'b1;
       |$drives
       |    repeat (4) @(posedge clock);
       |    reset = 1'b0;
       |    // CHI stimulus skeletons: TxnID/NodeID/address are placeholders
       |    // until the RN/SN port wiring is filled in per chi_nodes.json.
       |    chi_read_no_snp('0, '0, '0, '0);
       |    chi_write_no_snp_full('0, '0, '0, '0);
       |    repeat (100) @(posedge clock);
       |    $$finish;
       |  end
       |endmodule
       |""".stripMargin
  }
}
