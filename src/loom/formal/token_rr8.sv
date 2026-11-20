// Abstract 8-way round-robin arbiter for the P5 bounded-progress proof.
//
// One output port, one request bit per master, one-cycle service. A master
// that keeps requesting must see a grant within MAX_WAIT cycles, which is
// the liveness half of the token abstraction (the 8x8 safety model proves
// the corresponding point-to-point safety properties).
//
// Reset is abstracted by zero initial state.

module token_rr8 (
  input wire clk,
  input wire [7:0] req,
  output wire [7:0] gnt,
  output reg rsp
);

  localparam integer MAX_WAIT = 20;

  reg [2:0] ptr;
  reg [5:0] wait_cnt [0:7];
  reg busy;
  reg [2:0] src;

  reg [2:0] chosen;
  reg chosen_valid;
  integer k;
  reg [2:0] idx;

  initial begin
    ptr = 3'd0;
    busy = 1'b0;
    src = 3'd0;
    for (k = 0; k < 8; k = k + 1) wait_cnt[k] = 0;
  end

  always @* begin
    chosen = 3'd0;
    chosen_valid = 1'b0;
    for (k = 0; k < 8; k = k + 1) begin
      idx = (ptr + k) % 8;
      if (!chosen_valid && req[idx]) begin
        chosen = idx;
        chosen_valid = 1'b1;
      end
    end
  end

  assign gnt = (chosen_valid && !busy) ? (8'd1 << chosen) : 8'd0;

  integer i;
  always @(posedge clk) begin
    if (busy) begin
      rsp <= 1'b1;
      busy <= 1'b0;
    end else begin
      rsp <= 1'b0;
      if (chosen_valid) begin
        busy <= 1'b1;
        src <= chosen;
        ptr <= (chosen == 3'd7) ? 3'd0 : chosen + 3'd1;
      end
    end

    for (i = 0; i < 8; i = i + 1) begin
      if (gnt[i] || !req[i]) wait_cnt[i] <= 0;
      else if (wait_cnt[i] < 63) wait_cnt[i] <= wait_cnt[i] + 1'b1;
    end
  end

  integer a;
  always @(posedge clk) begin
    for (a = 0; a < 8; a = a + 1) begin
      // P5: a continuously requesting master is served within MAX_WAIT.
      assert(!req[a] || (wait_cnt[a] < MAX_WAIT));
    end
    // Grant is at most one-hot.
    assert((gnt & (gnt - 8'd1)) == 8'd0);
    // A grant only goes to a requesting master.
    assert((gnt & ~req) == 8'd0);
  end

endmodule
