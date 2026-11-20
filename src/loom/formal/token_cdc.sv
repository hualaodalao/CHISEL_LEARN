// Abstract CDC credit model for P7 (CDC_CREDIT) and the CDC half of G9.
//
// The allocation domain may send only while credits remain; the remote domain
// returns credits asynchronously (modelled as a nondeterministic return).
// The bound `used <= DEPTH` must hold for every interleaving.
//
// Reset is abstracted by zero initial state.

module token_cdc (
  input  wire clk,
  input  wire send,
  input  wire ret,
  output wire can_send,
  output wire [3:0] used
);

  localparam integer DEPTH = 4;

  reg [3:0] cnt;

  assign used = cnt;
  assign can_send = cnt < DEPTH;

  always @(posedge clk) begin
    if (send && can_send && !(ret && cnt != 0))
      cnt <= cnt + 1'b1;
    else if (ret && cnt != 0 && !(send && can_send))
      cnt <= cnt - 1'b1;
  end

  always @(posedge clk) begin
    // P7: credits never exceed the declared depth.
    assert(cnt <= DEPTH);
    // Environment contract: the sender honours credit and the return side
    // only returns outstanding credits.
    assume(!(send && !can_send));
    assume(!(ret && cnt == 0));
  end

endmodule
