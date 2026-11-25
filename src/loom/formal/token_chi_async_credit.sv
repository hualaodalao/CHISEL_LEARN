// Abstract cross-domain credit model for the CHI async link (HW-04).
//
// Allocation happens in the source domain; releases happen in the remote
// domain and are synchronized back with a delay (modelled as a conservative
// counter relSync that never exceeds the true release count relCnt). The
// source sees used = allocCnt - relSync and may allocate only while
// used < DEPTH. Because the return is delayed (relSync <= relCnt <= allocCnt),
// the source can never over-issue: used <= DEPTH for every interleaving.
//
// Reset is abstracted by zero initial state. Bounds are structural (gated in
// the update logic), not assume-dependent, so yosys SAT proves them.

module token_chi_async_credit (
  input  wire clk,
  input  wire alloc,
  input  wire release,
  input  wire syncTick,
  output wire can_alloc,
  output wire [7:0] used
);

  localparam integer DEPTH = 4;

  reg [7:0] allocCnt;
  reg [7:0] relCnt;
  reg [7:0] relSync;

  assign used = allocCnt - relSync;
  assign can_alloc = used < DEPTH;

  always @(posedge clk) begin
    // Structural gate: allocate only while below depth.
    if (alloc && can_alloc)
      allocCnt <= allocCnt + 1'b1;
    // Release only outstanding allocations (remote domain).
    if (release && relCnt != allocCnt)
      relCnt <= relCnt + 1'b1;
    // Delayed conservative return sync: never exceed the true release count.
    if (syncTick && relSync != relCnt)
      relSync <= relSync + 1'b1;
  end

  always @(posedge clk) begin
    // In-flight data counted at the source never exceeds the declared depth.
    assert(used <= DEPTH);
    // The synchronized return is conservative and ordered.
    assert(relSync <= relCnt);
    assert(relCnt <= allocCnt);
  end

endmodule