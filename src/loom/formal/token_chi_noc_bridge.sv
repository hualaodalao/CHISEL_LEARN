// Abstract store&fwd inter-NoC bridge model (HW-11/W12/W13).
//
// The bridge is a terminating endpoint with two independent bounded buffers:
// a request buffer (from-NoC -> to-NoC) and a response buffer (to-NoC -> from).
// Single outstanding: a new request is accepted only while idle (busy=0) and
// the request buffer has space. The response for the in-flight transaction is
// generated internally, gated ONLY by response-buffer space (canRsp), never by
// the request buffer. Because the two credit pools are independent and the
// response path reserves its own capacity, a full request buffer can never
// block the response drain: there is no circular wait (deadlock freedom).
//
// Reset is abstracted by zero initial state. Bounds and the response-path
// independence are structural (gated in the update logic), not assume-dependent,
// so yosys SAT proves them for every interleaving.

module token_chi_noc_bridge (
  input  wire clk,
  input  wire reqPush,
  input  wire reqPop,
  input  wire rspPop,
  output wire canReq,
  output wire canRsp,
  output wire [7:0] reqUsed,
  output wire [7:0] rspUsed,
  output wire busy
);

  localparam integer DEPTH = 4;

  reg [7:0] reqCnt;
  reg [7:0] reqRel;
  reg [7:0] rspCnt;
  reg [7:0] rspRel;
  reg       busyR;
  reg       rspPending;

  assign reqUsed = reqCnt - reqRel;
  assign rspUsed = rspCnt - rspRel;
  assign busy    = busyR;
  assign canReq  = (reqUsed < DEPTH) && !busyR;
  assign canRsp  = rspUsed < DEPTH;

  wire reqAccept = reqPush && canReq;
  wire rspGen    = busyR && !rspPending && canRsp;
  wire rspDrain  = rspPop && (rspRel != rspCnt);
  wire reqDrain  = reqPop && (reqRel != reqCnt);

  always @(posedge clk) begin
    if (reqAccept) begin
      reqCnt <= reqCnt + 1'b1;
      busyR  <= 1'b1;
    end
    if (reqDrain)
      reqRel <= reqRel + 1'b1;
    if (rspGen) begin
      rspCnt     <= rspCnt + 1'b1;
      rspPending <= 1'b1;
    end
    if (rspDrain) begin
      rspRel <= rspRel + 1'b1;
      // Last response drained releases the single outstanding slot.
      if ((rspRel + 1'b1) == rspCnt) begin
        busyR      <= 1'b0;
        rspPending <= 1'b0;
      end
    end
  end

  always @(posedge clk) begin
    // Bounded buffers in both domains.
    assert(reqUsed <= DEPTH);
    assert(rspUsed <= DEPTH);
    // A pending response is genuinely buffered.
    assert(!rspPending || (rspUsed > 0));
    // Deadlock freedom: an in-flight transaction awaiting its response can
    // always generate it -- response capacity is independent of the request
    // buffer, so a full request buffer never blocks the response path.
    assert(!(busyR && !rspPending) || canRsp);
  end

endmodule