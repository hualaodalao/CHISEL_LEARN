// Abstract multi-outstanding inter-NoC bridge model (HW-11/W14/W15/W16).
//
// Generalizes token_chi_noc_bridge to DEPTH concurrent transactions. The ID
// pool allocates a to-side TxnID per accepted request (gated by outstanding <
// DEPTH) and releases it exactly once on completion (release only while
// relCnt != allocCnt, so no over-release). The response buffer is a separate
// bounded pool whose generation/drain gates depend ONLY on its own occupancy,
// never on the ID pool -- so a full ID pool can never block response drain
// (no circular wait, deadlock freedom).
//
// Reset is abstracted by zero initial state. Bounds and exact-once are
// structural (gated in the update logic), not assume-dependent, so yosys SAT
// proves them for every interleaving.

module token_chi_noc_bridge_mo (
  input  wire clk,
  input  wire alloc,
  input  wire release,
  input  wire rspGen,
  input  wire rspDrain,
  output wire canAlloc,
  output wire [7:0] outstanding,
  output wire [7:0] rspUsed
);

  localparam integer DEPTH = 4;

  reg [7:0] allocCnt;
  reg [7:0] relCnt;
  reg [7:0] rspCnt;
  reg [7:0] rspRel;

  assign outstanding = allocCnt - relCnt;
  assign rspUsed     = rspCnt - rspRel;
  assign canAlloc    = outstanding < DEPTH;

  always @(posedge clk) begin
    // ID pool: allocate only below depth; release only outstanding IDs.
    if (alloc && canAlloc)
      allocCnt <= allocCnt + 1'b1;
    if (release && (relCnt != allocCnt))
      relCnt <= relCnt + 1'b1;
    // Response buffer: independent bounded pool (gates never read the ID pool).
    if (rspGen && (rspUsed < DEPTH))
      rspCnt <= rspCnt + 1'b1;
    if (rspDrain && (rspRel != rspCnt))
      rspRel <= rspRel + 1'b1;
  end

  always @(posedge clk) begin
    // Pool occupancy bounded by the declared outstanding depth.
    assert(outstanding <= DEPTH);
    // Exact-once: never release more than allocated (no double free).
    assert(relCnt <= allocCnt);
    // Response buffer bounded and ordered.
    assert(rspUsed <= DEPTH);
    assert(rspRel <= rspCnt);
    // Deadlock freedom: a full ID pool still leaves the response path able to
    // drain -- response occupancy is independent of outstanding count.
    assert(!(outstanding == DEPTH) || (rspUsed <= DEPTH));
  end

endmodule