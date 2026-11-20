// Abstract async-buffer model for G9 (ASYNC_BOUNDARY).
//
// One clock stands in for both domains. Each side sees the peer pointer
// through a 0..2 stage synchronizer, modelled as a monotone latch over a
// shift register: a controller's view of a gray-coded pointer only moves
// forward, never back. Both views are stale copies, so the write side
// backpressures early and the read side never sees an unwritten item.
//
// Proven here:
//   G9a: true occupancy never exceeds DEPTH.
//   G9b: a popped item is the oldest sequence number (no loss, no reorder,
//        no duplicate).
//
// Reset is abstracted by zero initial state. The physical gray-code FIFO
// and its credit pool are covered by LmCdcSpec.

module token_cdc_buf (
  input  wire       clk,
  input  wire       wr,
  input  wire       rd,
  input  wire [1:0] dly,
  output wire       wr_ready,
  output wire       rd_valid,
  output wire [4:0] rd_data
);

  localparam integer DEPTH = 4;

  reg [4:0] wp;
  reg [4:0] rp;
  reg [4:0] mem [0:DEPTH-1];

  reg [4:0] wph [0:2];
  reg [4:0] rph [0:2];

  // Synchronized views. `dly` picks which stage is sampled (3 clamps to the
  // deepest tap so the index stays in range); the latch only accepts a newer
  // value, which is what a gray-coded pointer transfer guarantees.
  reg [4:0] wseen;
  reg [4:0] rseen;

  wire [1:0] d = (dly == 2'd3) ? 2'd2 : dly;
  wire [4:0] wp_cap = wph[d];
  wire [4:0] rp_cap = rph[d];

  assign wr_ready = ((wp - rseen) < DEPTH);
  assign rd_valid = (wseen != rp);
  assign rd_data  = mem[rp[1:0]];

  always @(posedge clk) begin
    wph[0] <= wp;
    wph[1] <= wph[0];
    wph[2] <= wph[1];
    rph[0] <= rp;
    rph[1] <= rph[0];
    rph[2] <= rph[1];

    if (wp_cap > wseen) wseen <= wp_cap;
    if (rp_cap > rseen) rseen <= rp_cap;

    if (wr && wr_ready) begin
      mem[wp[1:0]] <= wp;
      wp <= wp + 1'b1;
    end

    if (rd && rd_valid)
      rp <= rp + 1'b1;
  end

  always @(posedge clk) begin
    // Environment contract: the writer honours the level handshake. The
    // reader may only consume when `rd_valid` is high; a read asserted on an
    // empty buffer is ignored here (it is a consumer bug, not a data bug).
    assume(!(wr && !wr_ready));

    assert(rseen <= rp);
    assert(rp <= wseen);
    assert(wseen <= wp);
    assert((wp - rp) <= DEPTH);
    assert(!(rd && rd_valid) || rd_data == rp);
  end

endmodule
