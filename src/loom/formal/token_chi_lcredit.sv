// CHI link-layer L-Credit conservation.
//
// The sender holds available credits; the receiver grants one per cycle on
// lcrdv, the sender consumes one per flit on send. The bound cnt <= DEPTH is
// enforced structurally in the update (grant only below depth, send only with
// a credit), so it holds for every interleaving.
//
// Reset is abstracted by zero initial state.

module token_chi_lcredit (
  input  wire clk,
  input  wire send,
  input  wire lcrdv,
  output wire can_send,
  output wire [3:0] cnt
);

  localparam integer DEPTH = 4;

  reg [3:0] c;
  assign cnt = c;
  assign can_send = c != 0;
  wire grant_ok = c < DEPTH;

  always @(posedge clk) begin
    if (lcrdv && grant_ok && !(send && can_send))
      c <= c + 1'b1;
    else if (send && can_send && !(lcrdv && grant_ok))
      c <= c - 1'b1;
  end

  always @(posedge clk) begin
    // Credits never exceed the receiver buffer depth (structural bound).
    assert(c <= DEPTH);
    // Environment contract: send honours credit; grant honours capacity.
    assume(!(send && !can_send));
    assume(!(lcrdv && !grant_ok));
  end

endmodule