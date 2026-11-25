// CHI DVM broadcast/collect completeness.
//
// The MN broadcasts SnpDVMOp to N targets (sent mask) and collects SnpResp
// (collected mask). A target may only be collected after it was sent; Comp is
// produced only when all N targets are collected. Invariant: collected is
// always a subset of sent, and Comp implies full collection.
//
// Reset is abstracted by zero initial state.

module token_chi_dvm (
  input  wire clk,
  input  wire start,
  input  wire [3:0] send_mask,
  input  wire [3:0] coll_mask,
  output wire comp,
  output wire [3:0] sent,
  output wire [3:0] collected
);

  reg [3:0] s;
  reg [3:0] c;
  reg active;

  assign sent = s;
  assign collected = c;
  assign comp = active && (c == 4'hF);

  always @(posedge clk) begin
    if (start && !active) begin
      active <= 1'b1;
      s <= 4'h0;
      c <= 4'h0;
    end else if (active) begin
      s <= s | send_mask;
      c <= c | (coll_mask & s);
      if (c == 4'hF) active <= 1'b0;
    end
  end

  always @(posedge clk) begin
    assert((c & ~s) == 4'h0);        // collect only sent targets
    assert(!comp || (c == 4'hF));    // Comp implies all collected
    assume(!(start && active));
    assume((coll_mask & ~s) == 4'h0);
  end

endmodule