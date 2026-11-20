// Abstract 4x4 token model for the P1/RESP_TOTAL formal spike.
//
// One token per master, a 4x4 round-robin crossbar, and a slave that holds a
// request for two cycles before returning exactly one response. Data and
// widths are folded away; the model only keeps the control state needed to
// prove that every accepted request gets one response and that the response
// arrives within a fixed latency bound (bounded liveness).
//
// Reset is abstracted by zero initial state (`sat -set-init-zero`).

module p1_token_4x4 (
  input wire clk,
  input wire [3:0] req_valid,
  input wire [1:0] req_dst0,
  input wire [1:0] req_dst1,
  input wire [1:0] req_dst2,
  input wire [1:0] req_dst3,
  output wire [3:0] req_ready,
  output reg  [3:0] rsp_valid
);

  localparam integer MAX_WAIT = 24;

  wire [1:0] dst_of [0:3];
  assign dst_of[0] = req_dst0;
  assign dst_of[1] = req_dst1;
  assign dst_of[2] = req_dst2;
  assign dst_of[3] = req_dst3;

  reg [3:0] pending;
  reg [5:0] age [0:3];
  reg [1:0] rr [0:3];

  reg [3:0] busy;
  reg [1:0] src [0:3];
  reg [1:0] delay [0:3];

  reg [1:0] chosen [0:3];
  reg [3:0] chosen_valid;
  integer j;
  integer k;
  integer idx;

  always @* begin
    for (j = 0; j < 4; j = j + 1) begin
      chosen[j] = 0;
      chosen_valid[j] = 0;
      for (k = 0; k < 4; k = k + 1) begin
        idx = (rr[j] + k) % 4;
        if (!chosen_valid[j] && req_valid[idx] && !pending[idx] &&
            (dst_of[idx] == j[1:0])) begin
          chosen[j] = idx[1:0];
          chosen_valid[j] = 1;
        end
      end
    end
  end

  genvar m;
  generate
    for (m = 0; m < 4; m = m + 1) begin : g_ready
      wire [1:0] d = dst_of[m];
      assign req_ready[m] = chosen_valid[d] && (chosen[d] == m[1:0]) && !busy[d];
    end
  endgenerate

  integer r;
  always @* begin
    rsp_valid = 0;
    for (r = 0; r < 4; r = r + 1)
      if (busy[r] && delay[r] == 0) rsp_valid[src[r]] = 1'b1;
  end

  integer i;
  always @(posedge clk) begin
    for (i = 0; i < 4; i = i + 1) begin
      if (req_valid[i] && req_ready[i]) begin
        pending[i] <= 1'b1;
        age[i] <= 0;
      end else if (rsp_valid[i]) begin
        pending[i] <= 1'b0;
        age[i] <= 0;
      end else if (pending[i]) begin
        if (age[i] < 63) age[i] <= age[i] + 1'b1;
      end
    end

    for (i = 0; i < 4; i = i + 1) begin
      if (!busy[i]) begin
        if (chosen_valid[i]) begin
          busy[i] <= 1'b1;
          src[i] <= chosen[i];
          delay[i] <= 2;
          rr[i] <= (chosen[i] == 2'd3) ? 2'd0 : chosen[i] + 2'd1;
        end
      end else if (delay[i] == 0) begin
        busy[i] <= 1'b0;
      end else begin
        delay[i] <= delay[i] - 1'b1;
      end
    end
  end

  integer a;
  always @(posedge clk) begin
    for (a = 0; a < 4; a = a + 1) begin
      // Bounded P1: every accepted request is answered within MAX_WAIT.
      assert(!pending[a] || (age[a] < MAX_WAIT));
      // No orphan and no duplicate responses: a response is only emitted
      // for an outstanding request, and clearing pending makes it unique.
      assert(!rsp_valid[a] || pending[a]);
      // A master never accepts a second request while one is outstanding.
      assert(!(req_valid[a] && req_ready[a] && pending[a]));
    end
    for (a = 0; a < 4; a = a + 1)
      if (busy[a] && delay[a] == 0) assert(pending[src[a]]);
  end

endmodule
