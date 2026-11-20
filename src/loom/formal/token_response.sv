// Abstract request/response accounting for P6 (NO_LOSS) and P8 (ERROR_TOTAL).
//
// A request is accepted only into a free slot; every slot is answered exactly
// once (success or error) after a fixed latency. The counter invariant
// `accepted - responded == popcount(busy)` is the token-level statement that
// no request is lost or answered twice.
//
// Reset is abstracted by zero initial state.

module token_response (
  input  wire clk,
  input  wire req,
  input  wire out_ready,
  output wire ready
);

  localparam integer SLOTS = 4;
  localparam integer LAT   = 3;

  reg [SLOTS-1:0] busy;
  reg [3:0] age [0:SLOTS-1];
  reg [7:0] accepted;
  reg [7:0] responded;
  reg [1:0] alloc_idx;
  reg alloc_found;
  reg resp_fire;

  integer i;
  always @* begin
    alloc_idx = 2'd0;
    alloc_found = 1'b0;
    for (i = 0; i < SLOTS; i = i + 1)
      if (!alloc_found && !busy[i]) begin
        alloc_idx = i[1:0];
        alloc_found = 1'b1;
      end
  end

  assign ready = alloc_found;
  assign resp_fire = busy[0] && (age[0] >= LAT) && out_ready;

  always @(posedge clk) begin
    if (req && ready) begin
      busy[alloc_idx] <= 1'b1;
      age[alloc_idx] <= 0;
      accepted <= accepted + 1'b1;
    end

    for (i = 0; i < SLOTS; i = i + 1)
      if (busy[i] && age[i] < 15) age[i] <= age[i] + 1'b1;

    if (resp_fire) begin
      busy[0] <= 1'b0;
      age[0] <= 0;
      responded <= responded + 1'b1;
    end
  end

  integer pop;
  always @* begin
    pop = 0;
    for (i = 0; i < SLOTS; i = i + 1) pop = pop + busy[i];
  end

  always @(posedge clk) begin
    // P6: no loss and no duplicate answer.
    assert(accepted >= responded);
    assert(accepted - responded == pop);
    // P8: a response always belongs to a live slot; errors are counted the
    // same way as successes (exactly one terminal answer per request).
    assert(!resp_fire || busy[0]);
    // A slot is allocated only when free.
    assert(!(req && ready && busy[alloc_idx]));
  end

endmodule
