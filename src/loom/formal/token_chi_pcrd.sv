// CHI P-Credit retry no-loss accounting.
//
// A request with AllowRetry is accepted while a slot is free, otherwise
// RetryAck is issued and a grant is owed. A release frees a slot and, if a
// grant is owed, issues PCrdGrant. Occupancy bounds are enforced structurally
// (accept only below depth, release only when occupied). Invariants: every
// request is accepted or retried (no loss); retried == granted + still-owed.
//
// Reset is abstracted by zero initial state.

module token_chi_pcrd (
  input  wire clk,
  input  wire req,
  input  wire release,
  output wire accept,
  output wire retry,
  output wire grant,
  output wire [3:0] occ
);

  localparam integer DEPTH = 4;

  reg [3:0] o;
  reg [7:0] pend;
  reg [7:0] total;
  reg [7:0] acc;
  reg [7:0] retr;
  reg [7:0] gr;

  assign occ = o;
  wire has_room = o < DEPTH;
  wire rel_ok = release && (o != 0);
  assign accept = req && has_room;
  assign retry  = req && !has_room;
  assign grant  = rel_ok && pend != 0;

  always @(posedge clk) begin
    o     <= o + accept - rel_ok;
    pend  <= pend + retry - grant;
    total <= total + req;
    acc   <= acc + accept;
    retr  <= retr + retry;
    gr    <= gr + grant;
  end

  always @(posedge clk) begin
    assert(o <= DEPTH);            // structural occupancy bound
    assert(acc + retr == total);   // no request lost
    assert(retr == gr + pend);     // retried = granted + owed
    assert(acc >= o);
    assume(!(release && o == 0));
  end

endmodule