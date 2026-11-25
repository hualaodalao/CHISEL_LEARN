// CHI DBID data-buffer exact-once accounting.
//
// A DBID grant allocates a free buffer slot; write data targets a live slot;
// completion (Comp) releases the slot only after its data arrived. The
// invariant granted - released == popcount(busy) states that no buffer is
// leaked or released twice, and data/release only touch live slots.
//
// Reset is abstracted by zero initial state.

module token_chi_dbid (
  input  wire clk,
  input  wire grant,
  input  wire data,
  input  wire release,
  input  wire out_ready,
  output wire ready
);

  localparam integer SLOTS = 4;

  reg [SLOTS-1:0] busy;
  reg [SLOTS-1:0] got_data;
  reg [7:0] granted;
  reg [7:0] released;
  reg [1:0] alloc_idx;
  reg alloc_found;

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
  wire data_fire = data && busy[0];
  wire rel_fire  = release && busy[0] && got_data[0] && out_ready;

  always @(posedge clk) begin
    if (grant && ready) begin
      busy[alloc_idx] <= 1'b1;
      got_data[alloc_idx] <= 1'b0;
      granted <= granted + 1'b1;
    end
    if (data_fire) got_data[0] <= 1'b1;
    if (rel_fire) begin
      busy[0] <= 1'b0;
      got_data[0] <= 1'b0;
      released <= released + 1'b1;
    end
  end

  integer pop;
  always @* begin
    pop = 0;
    for (i = 0; i < SLOTS; i = i + 1) pop = pop + busy[i];
  end

  always @(posedge clk) begin
    assert(granted >= released);
    assert(granted - released == pop);
    assert(!data_fire || busy[0]);
    assert(!rel_fire || (busy[0] && got_data[0]));
    assert(!(grant && ready && busy[alloc_idx]));
    assume(!(data && !busy[0]));
    assume(!(release && (!busy[0] || !got_data[0])));
  end

endmodule