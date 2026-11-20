// Abstract 8x8 token model for P1-P5 bounded formal checking (S8).
//
// One token per master, an 8x8 round-robin crossbar, and each output holds a
// request for two cycles before returning exactly one response. Data, widths
// and QoS classes are folded away; only the control state needed for the
// safety and bounded-progress properties is kept.
//
// Property mapping:
//   P1 RESP_TOTAL   every accepted request is answered within MAX_WAIT
//   P2 NO_ORPHAN    a response is only emitted for an outstanding request,
//                   and clearing pending makes it unique (no duplicates)
//   P3 SAFETY       a master never accepts a second request while one is
//                   outstanding; a port only accepts the grant it chose
//   ROUTE SAFE      a grant is only issued to a master whose dst equals the
//                   output port (P4 ORDER stays open until the ordered tag
//                   exists)
//   P5 PROGRESS     bounded progress is proved on the dedicated 8-way RR
//                   model (token_rr8.sv); this model only carries the
//                   safety half of the token abstraction.
//
// Reset is abstracted by zero initial state (`sby` bmc with -set-init-zero).

module token_8x8 (
  input wire clk,
  input wire [7:0] req_valid,
  input wire [2:0] req_dst0,
  input wire [2:0] req_dst1,
  input wire [2:0] req_dst2,
  input wire [2:0] req_dst3,
  input wire [2:0] req_dst4,
  input wire [2:0] req_dst5,
  input wire [2:0] req_dst6,
  input wire [2:0] req_dst7,
  output wire [7:0] req_ready,
  output reg  [7:0] rsp_valid
);

  localparam integer MAX_WAIT = 48;

  wire [2:0] dst_of [0:7];
  assign dst_of[0] = req_dst0;
  assign dst_of[1] = req_dst1;
  assign dst_of[2] = req_dst2;
  assign dst_of[3] = req_dst3;
  assign dst_of[4] = req_dst4;
  assign dst_of[5] = req_dst5;
  assign dst_of[6] = req_dst6;
  assign dst_of[7] = req_dst7;

  reg [7:0] pending;
  reg [5:0] age [0:7];
  reg [2:0] rr [0:7];

  reg [7:0] busy;
  reg [2:0] src [0:7];
  reg [1:0] delay [0:7];

  reg [2:0] chosen [0:7];
  reg [7:0] chosen_valid;
  integer j;
  integer k;
  integer idx;

  // Zero initial state for the formal run (no reset handshake).
  integer init_i;
  initial begin
    pending = 8'd0;
    busy = 8'd0;
    for (init_i = 0; init_i < 8; init_i = init_i + 1) begin
      age[init_i] = 0;
      rr[init_i] = 0;
      src[init_i] = 0;
      delay[init_i] = 0;
    end
  end

  always @* begin
    for (j = 0; j < 8; j = j + 1) begin
      chosen[j] = 3'd0;
      chosen_valid[j] = 1'b0;
      for (k = 0; k < 8; k = k + 1) begin
        idx = (rr[j] + k) % 8;
        if (!chosen_valid[j] && req_valid[idx] && !pending[idx] &&
            (dst_of[idx] == j[2:0])) begin
          chosen[j] = idx[2:0];
          chosen_valid[j] = 1'b1;
        end
      end
    end
  end

  genvar m;
  generate
    for (m = 0; m < 8; m = m + 1) begin : g_ready
      wire [2:0] d = dst_of[m];
      assign req_ready[m] = chosen_valid[d] && (chosen[d] == m[2:0]) && !busy[d];
    end
  endgenerate

  integer r;
  always @* begin
    rsp_valid = 8'd0;
    for (r = 0; r < 8; r = r + 1)
      if (busy[r] && delay[r] == 2'd0) rsp_valid[src[r]] = 1'b1;
  end

  integer i;
  always @(posedge clk) begin
    for (i = 0; i < 8; i = i + 1) begin
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

    for (i = 0; i < 8; i = i + 1) begin
      if (!busy[i]) begin
        if (chosen_valid[i]) begin
          busy[i] <= 1'b1;
          src[i] <= chosen[i];
          delay[i] <= 2'd2;
          rr[i] <= (chosen[i] == 3'd7) ? 3'd0 : chosen[i] + 3'd1;
        end
      end else if (delay[i] == 2'd0) begin
        busy[i] <= 1'b0;
      end else begin
        delay[i] <= delay[i] - 2'd1;
      end
    end
  end

  integer a;
  always @(posedge clk) begin
    for (a = 0; a < 8; a = a + 1) begin
      // P1: every accepted request is answered within MAX_WAIT.
      assert(!pending[a] || (age[a] < MAX_WAIT));
      // P2: no orphan response, and pending clears exactly once.
      assert(!rsp_valid[a] || pending[a]);
      // P3: a master never accepts a second request while pending, and a
      // master only advances when it is the grant its destination chose.
      assert(!(req_valid[a] && req_ready[a] && pending[a]));
      assert(!req_ready[a] || (chosen[dst_of[a]] == a[2:0]));
    end
    // P2: a response is only produced by a busy port holding that source.
    for (a = 0; a < 8; a = a + 1)
      if (busy[a] && delay[a] == 2'd0) assert(pending[src[a]]);
    // Route safety: every accepted grant matches the destination port.
    for (a = 0; a < 8; a = a + 1)
      if (chosen_valid[a]) assert(dst_of[chosen[a]] == a[2:0]);
  end

endmodule
