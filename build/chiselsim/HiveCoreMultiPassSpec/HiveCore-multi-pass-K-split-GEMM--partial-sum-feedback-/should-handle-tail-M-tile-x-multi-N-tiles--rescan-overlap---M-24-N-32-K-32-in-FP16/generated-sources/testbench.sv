module svsimTestbench;
  reg  [$bits(dut.clock)-1:0] clock = '0;
  reg  [$bits(dut.reset)-1:0] reset = '0;
  wire [$bits(dut.io_status_cOccupancy)-1:0] io_status_cOccupancy;
  wire [$bits(dut.io_status_bOccupancy)-1:0] io_status_bOccupancy;
  wire [$bits(dut.io_status_aOccupancy)-1:0] io_status_aOccupancy;
  wire [$bits(dut.io_status_progress)-1:0] io_status_progress;
  wire [$bits(dut.io_status_err)-1:0] io_status_err;
  wire [$bits(dut.io_status_done)-1:0] io_status_done;
  wire [$bits(dut.io_status_busy)-1:0] io_status_busy;
  wire [$bits(dut.io_dma2Ext_cmd_payload_addr)-1:0] io_dma2Ext_cmd_payload_addr;
  reg  [$bits(dut.io_dma2Ext_cmd_ready)-1:0] io_dma2Ext_cmd_ready = '0;
  wire [$bits(dut.io_dma2Ext_cmd_valid)-1:0] io_dma2Ext_cmd_valid;
  reg  [$bits(dut.io_dma2Ext_rsp_payload_rsp)-1:0] io_dma2Ext_rsp_payload_rsp = '0;
  reg  [$bits(dut.io_dma2Ext_rsp_payload_data)-1:0] io_dma2Ext_rsp_payload_data = '0;
  wire [$bits(dut.io_dma2Ext_rsp_ready)-1:0] io_dma2Ext_rsp_ready;
  reg  [$bits(dut.io_dma2Ext_rsp_valid)-1:0] io_dma2Ext_rsp_valid = '0;
  wire [$bits(dut.io_dma1Ext_isWrite)-1:0] io_dma1Ext_isWrite;
  reg  [$bits(dut.io_dma1Ext_grant)-1:0] io_dma1Ext_grant = '0;
  wire [$bits(dut.io_dma1Ext_req)-1:0] io_dma1Ext_req;
  wire [$bits(dut.io_dma1Ext_len)-1:0] io_dma1Ext_len;
  wire [$bits(dut.io_dma1Ext_addr)-1:0] io_dma1Ext_addr;
  wire [$bits(dut.io_dma1Ext_writeData_payload)-1:0] io_dma1Ext_writeData_payload;
  reg  [$bits(dut.io_dma1Ext_writeData_ready)-1:0] io_dma1Ext_writeData_ready = '0;
  wire [$bits(dut.io_dma1Ext_writeData_valid)-1:0] io_dma1Ext_writeData_valid;
  reg  [$bits(dut.io_dma1Ext_readData_payload)-1:0] io_dma1Ext_readData_payload = '0;
  wire [$bits(dut.io_dma1Ext_readData_ready)-1:0] io_dma1Ext_readData_ready;
  reg  [$bits(dut.io_dma1Ext_readData_valid)-1:0] io_dma1Ext_readData_valid = '0;
  wire [$bits(dut.io_dma0Ext_cmd_payload_addr)-1:0] io_dma0Ext_cmd_payload_addr;
  reg  [$bits(dut.io_dma0Ext_cmd_ready)-1:0] io_dma0Ext_cmd_ready = '0;
  wire [$bits(dut.io_dma0Ext_cmd_valid)-1:0] io_dma0Ext_cmd_valid;
  reg  [$bits(dut.io_dma0Ext_rsp_payload_rsp)-1:0] io_dma0Ext_rsp_payload_rsp = '0;
  reg  [$bits(dut.io_dma0Ext_rsp_payload_data)-1:0] io_dma0Ext_rsp_payload_data = '0;
  wire [$bits(dut.io_dma0Ext_rsp_ready)-1:0] io_dma0Ext_rsp_ready;
  reg  [$bits(dut.io_dma0Ext_rsp_valid)-1:0] io_dma0Ext_rsp_valid = '0;
  wire [$bits(dut.io_resp_payload_err)-1:0] io_resp_payload_err;
  wire [$bits(dut.io_resp_payload_done)-1:0] io_resp_payload_done;
  wire [$bits(dut.io_resp_payload_data)-1:0] io_resp_payload_data;
  reg  [$bits(dut.io_resp_ready)-1:0] io_resp_ready = '0;
  wire [$bits(dut.io_resp_valid)-1:0] io_resp_valid;
  reg  [$bits(dut.io_cmd_payload_data1)-1:0] io_cmd_payload_data1 = '0;
  reg  [$bits(dut.io_cmd_payload_data0)-1:0] io_cmd_payload_data0 = '0;
  reg  [$bits(dut.io_cmd_payload_reg1Addr)-1:0] io_cmd_payload_reg1Addr = '0;
  reg  [$bits(dut.io_cmd_payload_reg0Addr)-1:0] io_cmd_payload_reg0Addr = '0;
  reg  [$bits(dut.io_cmd_payload_op)-1:0] io_cmd_payload_op = '0;
  wire [$bits(dut.io_cmd_ready)-1:0] io_cmd_ready;
  reg  [$bits(dut.io_cmd_valid)-1:0] io_cmd_valid = '0;

HiveCore dut (
    .clock(clock),
    .reset(reset),
    .io_status_cOccupancy(io_status_cOccupancy),
    .io_status_bOccupancy(io_status_bOccupancy),
    .io_status_aOccupancy(io_status_aOccupancy),
    .io_status_progress(io_status_progress),
    .io_status_err(io_status_err),
    .io_status_done(io_status_done),
    .io_status_busy(io_status_busy),
    .io_dma2Ext_cmd_payload_addr(io_dma2Ext_cmd_payload_addr),
    .io_dma2Ext_cmd_ready(io_dma2Ext_cmd_ready),
    .io_dma2Ext_cmd_valid(io_dma2Ext_cmd_valid),
    .io_dma2Ext_rsp_payload_rsp(io_dma2Ext_rsp_payload_rsp),
    .io_dma2Ext_rsp_payload_data(io_dma2Ext_rsp_payload_data),
    .io_dma2Ext_rsp_ready(io_dma2Ext_rsp_ready),
    .io_dma2Ext_rsp_valid(io_dma2Ext_rsp_valid),
    .io_dma1Ext_isWrite(io_dma1Ext_isWrite),
    .io_dma1Ext_grant(io_dma1Ext_grant),
    .io_dma1Ext_req(io_dma1Ext_req),
    .io_dma1Ext_len(io_dma1Ext_len),
    .io_dma1Ext_addr(io_dma1Ext_addr),
    .io_dma1Ext_writeData_payload(io_dma1Ext_writeData_payload),
    .io_dma1Ext_writeData_ready(io_dma1Ext_writeData_ready),
    .io_dma1Ext_writeData_valid(io_dma1Ext_writeData_valid),
    .io_dma1Ext_readData_payload(io_dma1Ext_readData_payload),
    .io_dma1Ext_readData_ready(io_dma1Ext_readData_ready),
    .io_dma1Ext_readData_valid(io_dma1Ext_readData_valid),
    .io_dma0Ext_cmd_payload_addr(io_dma0Ext_cmd_payload_addr),
    .io_dma0Ext_cmd_ready(io_dma0Ext_cmd_ready),
    .io_dma0Ext_cmd_valid(io_dma0Ext_cmd_valid),
    .io_dma0Ext_rsp_payload_rsp(io_dma0Ext_rsp_payload_rsp),
    .io_dma0Ext_rsp_payload_data(io_dma0Ext_rsp_payload_data),
    .io_dma0Ext_rsp_ready(io_dma0Ext_rsp_ready),
    .io_dma0Ext_rsp_valid(io_dma0Ext_rsp_valid),
    .io_resp_payload_err(io_resp_payload_err),
    .io_resp_payload_done(io_resp_payload_done),
    .io_resp_payload_data(io_resp_payload_data),
    .io_resp_ready(io_resp_ready),
    .io_resp_valid(io_resp_valid),
    .io_cmd_payload_data1(io_cmd_payload_data1),
    .io_cmd_payload_data0(io_cmd_payload_data0),
    .io_cmd_payload_reg1Addr(io_cmd_payload_reg1Addr),
    .io_cmd_payload_reg0Addr(io_cmd_payload_reg0Addr),
    .io_cmd_payload_op(io_cmd_payload_op),
    .io_cmd_ready(io_cmd_ready),
    .io_cmd_valid(io_cmd_valid)
);

  import "DPI-C" context function void initTestBenchScope();
  initial
    initTestBenchScope();
  // Port 0: clock
  export "DPI-C" function getBitWidthImpl_clock;
  function void getBitWidthImpl_clock;
    output int value;
    value = $bits(dut.clock);
  endfunction
  export "DPI-C" function setBitsImpl_clock;
  function void setBitsImpl_clock;
    input bit [$bits(dut.clock)-1:0] value_clock;
    clock = value_clock;
  endfunction
  export "DPI-C" function getBitsImpl_clock;
  function void getBitsImpl_clock;
    output bit [$bits(dut.clock)-1:0] value_clock;
    value_clock = clock;
  endfunction

  // Port 1: reset
  export "DPI-C" function getBitWidthImpl_reset;
  function void getBitWidthImpl_reset;
    output int value;
    value = $bits(dut.reset);
  endfunction
  export "DPI-C" function setBitsImpl_reset;
  function void setBitsImpl_reset;
    input bit [$bits(dut.reset)-1:0] value_reset;
    reset = value_reset;
  endfunction
  export "DPI-C" function getBitsImpl_reset;
  function void getBitsImpl_reset;
    output bit [$bits(dut.reset)-1:0] value_reset;
    value_reset = reset;
  endfunction

  // Port 2: io_status_cOccupancy
  export "DPI-C" function getBitWidthImpl_io_status_cOccupancy;
  function void getBitWidthImpl_io_status_cOccupancy;
    output int value;
    value = $bits(dut.io_status_cOccupancy);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_cOccupancy;
  function void getBitsImpl_io_status_cOccupancy;
    output bit [$bits(dut.io_status_cOccupancy)-1:0] value_io_status_cOccupancy;
    value_io_status_cOccupancy = io_status_cOccupancy;
  endfunction

  // Port 3: io_status_bOccupancy
  export "DPI-C" function getBitWidthImpl_io_status_bOccupancy;
  function void getBitWidthImpl_io_status_bOccupancy;
    output int value;
    value = $bits(dut.io_status_bOccupancy);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_bOccupancy;
  function void getBitsImpl_io_status_bOccupancy;
    output bit [$bits(dut.io_status_bOccupancy)-1:0] value_io_status_bOccupancy;
    value_io_status_bOccupancy = io_status_bOccupancy;
  endfunction

  // Port 4: io_status_aOccupancy
  export "DPI-C" function getBitWidthImpl_io_status_aOccupancy;
  function void getBitWidthImpl_io_status_aOccupancy;
    output int value;
    value = $bits(dut.io_status_aOccupancy);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_aOccupancy;
  function void getBitsImpl_io_status_aOccupancy;
    output bit [$bits(dut.io_status_aOccupancy)-1:0] value_io_status_aOccupancy;
    value_io_status_aOccupancy = io_status_aOccupancy;
  endfunction

  // Port 5: io_status_progress
  export "DPI-C" function getBitWidthImpl_io_status_progress;
  function void getBitWidthImpl_io_status_progress;
    output int value;
    value = $bits(dut.io_status_progress);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_progress;
  function void getBitsImpl_io_status_progress;
    output bit [$bits(dut.io_status_progress)-1:0] value_io_status_progress;
    value_io_status_progress = io_status_progress;
  endfunction

  // Port 6: io_status_err
  export "DPI-C" function getBitWidthImpl_io_status_err;
  function void getBitWidthImpl_io_status_err;
    output int value;
    value = $bits(dut.io_status_err);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_err;
  function void getBitsImpl_io_status_err;
    output bit [$bits(dut.io_status_err)-1:0] value_io_status_err;
    value_io_status_err = io_status_err;
  endfunction

  // Port 7: io_status_done
  export "DPI-C" function getBitWidthImpl_io_status_done;
  function void getBitWidthImpl_io_status_done;
    output int value;
    value = $bits(dut.io_status_done);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_done;
  function void getBitsImpl_io_status_done;
    output bit [$bits(dut.io_status_done)-1:0] value_io_status_done;
    value_io_status_done = io_status_done;
  endfunction

  // Port 8: io_status_busy
  export "DPI-C" function getBitWidthImpl_io_status_busy;
  function void getBitWidthImpl_io_status_busy;
    output int value;
    value = $bits(dut.io_status_busy);
  endfunction
  export "DPI-C" function getBitsImpl_io_status_busy;
  function void getBitsImpl_io_status_busy;
    output bit [$bits(dut.io_status_busy)-1:0] value_io_status_busy;
    value_io_status_busy = io_status_busy;
  endfunction

  // Port 9: io_dma2Ext_cmd_payload_addr
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_cmd_payload_addr;
  function void getBitWidthImpl_io_dma2Ext_cmd_payload_addr;
    output int value;
    value = $bits(dut.io_dma2Ext_cmd_payload_addr);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_cmd_payload_addr;
  function void getBitsImpl_io_dma2Ext_cmd_payload_addr;
    output bit [$bits(dut.io_dma2Ext_cmd_payload_addr)-1:0] value_io_dma2Ext_cmd_payload_addr;
    value_io_dma2Ext_cmd_payload_addr = io_dma2Ext_cmd_payload_addr;
  endfunction

  // Port a: io_dma2Ext_cmd_ready
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_cmd_ready;
  function void getBitWidthImpl_io_dma2Ext_cmd_ready;
    output int value;
    value = $bits(dut.io_dma2Ext_cmd_ready);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma2Ext_cmd_ready;
  function void setBitsImpl_io_dma2Ext_cmd_ready;
    input bit [$bits(dut.io_dma2Ext_cmd_ready)-1:0] value_io_dma2Ext_cmd_ready;
    io_dma2Ext_cmd_ready = value_io_dma2Ext_cmd_ready;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_cmd_ready;
  function void getBitsImpl_io_dma2Ext_cmd_ready;
    output bit [$bits(dut.io_dma2Ext_cmd_ready)-1:0] value_io_dma2Ext_cmd_ready;
    value_io_dma2Ext_cmd_ready = io_dma2Ext_cmd_ready;
  endfunction

  // Port b: io_dma2Ext_cmd_valid
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_cmd_valid;
  function void getBitWidthImpl_io_dma2Ext_cmd_valid;
    output int value;
    value = $bits(dut.io_dma2Ext_cmd_valid);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_cmd_valid;
  function void getBitsImpl_io_dma2Ext_cmd_valid;
    output bit [$bits(dut.io_dma2Ext_cmd_valid)-1:0] value_io_dma2Ext_cmd_valid;
    value_io_dma2Ext_cmd_valid = io_dma2Ext_cmd_valid;
  endfunction

  // Port c: io_dma2Ext_rsp_payload_rsp
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_rsp_payload_rsp;
  function void getBitWidthImpl_io_dma2Ext_rsp_payload_rsp;
    output int value;
    value = $bits(dut.io_dma2Ext_rsp_payload_rsp);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma2Ext_rsp_payload_rsp;
  function void setBitsImpl_io_dma2Ext_rsp_payload_rsp;
    input bit [$bits(dut.io_dma2Ext_rsp_payload_rsp)-1:0] value_io_dma2Ext_rsp_payload_rsp;
    io_dma2Ext_rsp_payload_rsp = value_io_dma2Ext_rsp_payload_rsp;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_rsp_payload_rsp;
  function void getBitsImpl_io_dma2Ext_rsp_payload_rsp;
    output bit [$bits(dut.io_dma2Ext_rsp_payload_rsp)-1:0] value_io_dma2Ext_rsp_payload_rsp;
    value_io_dma2Ext_rsp_payload_rsp = io_dma2Ext_rsp_payload_rsp;
  endfunction

  // Port d: io_dma2Ext_rsp_payload_data
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_rsp_payload_data;
  function void getBitWidthImpl_io_dma2Ext_rsp_payload_data;
    output int value;
    value = $bits(dut.io_dma2Ext_rsp_payload_data);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma2Ext_rsp_payload_data;
  function void setBitsImpl_io_dma2Ext_rsp_payload_data;
    input bit [$bits(dut.io_dma2Ext_rsp_payload_data)-1:0] value_io_dma2Ext_rsp_payload_data;
    io_dma2Ext_rsp_payload_data = value_io_dma2Ext_rsp_payload_data;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_rsp_payload_data;
  function void getBitsImpl_io_dma2Ext_rsp_payload_data;
    output bit [$bits(dut.io_dma2Ext_rsp_payload_data)-1:0] value_io_dma2Ext_rsp_payload_data;
    value_io_dma2Ext_rsp_payload_data = io_dma2Ext_rsp_payload_data;
  endfunction

  // Port e: io_dma2Ext_rsp_ready
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_rsp_ready;
  function void getBitWidthImpl_io_dma2Ext_rsp_ready;
    output int value;
    value = $bits(dut.io_dma2Ext_rsp_ready);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_rsp_ready;
  function void getBitsImpl_io_dma2Ext_rsp_ready;
    output bit [$bits(dut.io_dma2Ext_rsp_ready)-1:0] value_io_dma2Ext_rsp_ready;
    value_io_dma2Ext_rsp_ready = io_dma2Ext_rsp_ready;
  endfunction

  // Port f: io_dma2Ext_rsp_valid
  export "DPI-C" function getBitWidthImpl_io_dma2Ext_rsp_valid;
  function void getBitWidthImpl_io_dma2Ext_rsp_valid;
    output int value;
    value = $bits(dut.io_dma2Ext_rsp_valid);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma2Ext_rsp_valid;
  function void setBitsImpl_io_dma2Ext_rsp_valid;
    input bit [$bits(dut.io_dma2Ext_rsp_valid)-1:0] value_io_dma2Ext_rsp_valid;
    io_dma2Ext_rsp_valid = value_io_dma2Ext_rsp_valid;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma2Ext_rsp_valid;
  function void getBitsImpl_io_dma2Ext_rsp_valid;
    output bit [$bits(dut.io_dma2Ext_rsp_valid)-1:0] value_io_dma2Ext_rsp_valid;
    value_io_dma2Ext_rsp_valid = io_dma2Ext_rsp_valid;
  endfunction

  // Port 10: io_dma1Ext_isWrite
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_isWrite;
  function void getBitWidthImpl_io_dma1Ext_isWrite;
    output int value;
    value = $bits(dut.io_dma1Ext_isWrite);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_isWrite;
  function void getBitsImpl_io_dma1Ext_isWrite;
    output bit [$bits(dut.io_dma1Ext_isWrite)-1:0] value_io_dma1Ext_isWrite;
    value_io_dma1Ext_isWrite = io_dma1Ext_isWrite;
  endfunction

  // Port 11: io_dma1Ext_grant
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_grant;
  function void getBitWidthImpl_io_dma1Ext_grant;
    output int value;
    value = $bits(dut.io_dma1Ext_grant);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma1Ext_grant;
  function void setBitsImpl_io_dma1Ext_grant;
    input bit [$bits(dut.io_dma1Ext_grant)-1:0] value_io_dma1Ext_grant;
    io_dma1Ext_grant = value_io_dma1Ext_grant;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_grant;
  function void getBitsImpl_io_dma1Ext_grant;
    output bit [$bits(dut.io_dma1Ext_grant)-1:0] value_io_dma1Ext_grant;
    value_io_dma1Ext_grant = io_dma1Ext_grant;
  endfunction

  // Port 12: io_dma1Ext_req
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_req;
  function void getBitWidthImpl_io_dma1Ext_req;
    output int value;
    value = $bits(dut.io_dma1Ext_req);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_req;
  function void getBitsImpl_io_dma1Ext_req;
    output bit [$bits(dut.io_dma1Ext_req)-1:0] value_io_dma1Ext_req;
    value_io_dma1Ext_req = io_dma1Ext_req;
  endfunction

  // Port 13: io_dma1Ext_len
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_len;
  function void getBitWidthImpl_io_dma1Ext_len;
    output int value;
    value = $bits(dut.io_dma1Ext_len);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_len;
  function void getBitsImpl_io_dma1Ext_len;
    output bit [$bits(dut.io_dma1Ext_len)-1:0] value_io_dma1Ext_len;
    value_io_dma1Ext_len = io_dma1Ext_len;
  endfunction

  // Port 14: io_dma1Ext_addr
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_addr;
  function void getBitWidthImpl_io_dma1Ext_addr;
    output int value;
    value = $bits(dut.io_dma1Ext_addr);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_addr;
  function void getBitsImpl_io_dma1Ext_addr;
    output bit [$bits(dut.io_dma1Ext_addr)-1:0] value_io_dma1Ext_addr;
    value_io_dma1Ext_addr = io_dma1Ext_addr;
  endfunction

  // Port 15: io_dma1Ext_writeData_payload
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_writeData_payload;
  function void getBitWidthImpl_io_dma1Ext_writeData_payload;
    output int value;
    value = $bits(dut.io_dma1Ext_writeData_payload);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_writeData_payload;
  function void getBitsImpl_io_dma1Ext_writeData_payload;
    output bit [$bits(dut.io_dma1Ext_writeData_payload)-1:0] value_io_dma1Ext_writeData_payload;
    value_io_dma1Ext_writeData_payload = io_dma1Ext_writeData_payload;
  endfunction

  // Port 16: io_dma1Ext_writeData_ready
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_writeData_ready;
  function void getBitWidthImpl_io_dma1Ext_writeData_ready;
    output int value;
    value = $bits(dut.io_dma1Ext_writeData_ready);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma1Ext_writeData_ready;
  function void setBitsImpl_io_dma1Ext_writeData_ready;
    input bit [$bits(dut.io_dma1Ext_writeData_ready)-1:0] value_io_dma1Ext_writeData_ready;
    io_dma1Ext_writeData_ready = value_io_dma1Ext_writeData_ready;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_writeData_ready;
  function void getBitsImpl_io_dma1Ext_writeData_ready;
    output bit [$bits(dut.io_dma1Ext_writeData_ready)-1:0] value_io_dma1Ext_writeData_ready;
    value_io_dma1Ext_writeData_ready = io_dma1Ext_writeData_ready;
  endfunction

  // Port 17: io_dma1Ext_writeData_valid
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_writeData_valid;
  function void getBitWidthImpl_io_dma1Ext_writeData_valid;
    output int value;
    value = $bits(dut.io_dma1Ext_writeData_valid);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_writeData_valid;
  function void getBitsImpl_io_dma1Ext_writeData_valid;
    output bit [$bits(dut.io_dma1Ext_writeData_valid)-1:0] value_io_dma1Ext_writeData_valid;
    value_io_dma1Ext_writeData_valid = io_dma1Ext_writeData_valid;
  endfunction

  // Port 18: io_dma1Ext_readData_payload
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_readData_payload;
  function void getBitWidthImpl_io_dma1Ext_readData_payload;
    output int value;
    value = $bits(dut.io_dma1Ext_readData_payload);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma1Ext_readData_payload;
  function void setBitsImpl_io_dma1Ext_readData_payload;
    input bit [$bits(dut.io_dma1Ext_readData_payload)-1:0] value_io_dma1Ext_readData_payload;
    io_dma1Ext_readData_payload = value_io_dma1Ext_readData_payload;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_readData_payload;
  function void getBitsImpl_io_dma1Ext_readData_payload;
    output bit [$bits(dut.io_dma1Ext_readData_payload)-1:0] value_io_dma1Ext_readData_payload;
    value_io_dma1Ext_readData_payload = io_dma1Ext_readData_payload;
  endfunction

  // Port 19: io_dma1Ext_readData_ready
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_readData_ready;
  function void getBitWidthImpl_io_dma1Ext_readData_ready;
    output int value;
    value = $bits(dut.io_dma1Ext_readData_ready);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_readData_ready;
  function void getBitsImpl_io_dma1Ext_readData_ready;
    output bit [$bits(dut.io_dma1Ext_readData_ready)-1:0] value_io_dma1Ext_readData_ready;
    value_io_dma1Ext_readData_ready = io_dma1Ext_readData_ready;
  endfunction

  // Port 1a: io_dma1Ext_readData_valid
  export "DPI-C" function getBitWidthImpl_io_dma1Ext_readData_valid;
  function void getBitWidthImpl_io_dma1Ext_readData_valid;
    output int value;
    value = $bits(dut.io_dma1Ext_readData_valid);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma1Ext_readData_valid;
  function void setBitsImpl_io_dma1Ext_readData_valid;
    input bit [$bits(dut.io_dma1Ext_readData_valid)-1:0] value_io_dma1Ext_readData_valid;
    io_dma1Ext_readData_valid = value_io_dma1Ext_readData_valid;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma1Ext_readData_valid;
  function void getBitsImpl_io_dma1Ext_readData_valid;
    output bit [$bits(dut.io_dma1Ext_readData_valid)-1:0] value_io_dma1Ext_readData_valid;
    value_io_dma1Ext_readData_valid = io_dma1Ext_readData_valid;
  endfunction

  // Port 1b: io_dma0Ext_cmd_payload_addr
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_cmd_payload_addr;
  function void getBitWidthImpl_io_dma0Ext_cmd_payload_addr;
    output int value;
    value = $bits(dut.io_dma0Ext_cmd_payload_addr);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_cmd_payload_addr;
  function void getBitsImpl_io_dma0Ext_cmd_payload_addr;
    output bit [$bits(dut.io_dma0Ext_cmd_payload_addr)-1:0] value_io_dma0Ext_cmd_payload_addr;
    value_io_dma0Ext_cmd_payload_addr = io_dma0Ext_cmd_payload_addr;
  endfunction

  // Port 1c: io_dma0Ext_cmd_ready
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_cmd_ready;
  function void getBitWidthImpl_io_dma0Ext_cmd_ready;
    output int value;
    value = $bits(dut.io_dma0Ext_cmd_ready);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma0Ext_cmd_ready;
  function void setBitsImpl_io_dma0Ext_cmd_ready;
    input bit [$bits(dut.io_dma0Ext_cmd_ready)-1:0] value_io_dma0Ext_cmd_ready;
    io_dma0Ext_cmd_ready = value_io_dma0Ext_cmd_ready;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_cmd_ready;
  function void getBitsImpl_io_dma0Ext_cmd_ready;
    output bit [$bits(dut.io_dma0Ext_cmd_ready)-1:0] value_io_dma0Ext_cmd_ready;
    value_io_dma0Ext_cmd_ready = io_dma0Ext_cmd_ready;
  endfunction

  // Port 1d: io_dma0Ext_cmd_valid
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_cmd_valid;
  function void getBitWidthImpl_io_dma0Ext_cmd_valid;
    output int value;
    value = $bits(dut.io_dma0Ext_cmd_valid);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_cmd_valid;
  function void getBitsImpl_io_dma0Ext_cmd_valid;
    output bit [$bits(dut.io_dma0Ext_cmd_valid)-1:0] value_io_dma0Ext_cmd_valid;
    value_io_dma0Ext_cmd_valid = io_dma0Ext_cmd_valid;
  endfunction

  // Port 1e: io_dma0Ext_rsp_payload_rsp
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_rsp_payload_rsp;
  function void getBitWidthImpl_io_dma0Ext_rsp_payload_rsp;
    output int value;
    value = $bits(dut.io_dma0Ext_rsp_payload_rsp);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma0Ext_rsp_payload_rsp;
  function void setBitsImpl_io_dma0Ext_rsp_payload_rsp;
    input bit [$bits(dut.io_dma0Ext_rsp_payload_rsp)-1:0] value_io_dma0Ext_rsp_payload_rsp;
    io_dma0Ext_rsp_payload_rsp = value_io_dma0Ext_rsp_payload_rsp;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_rsp_payload_rsp;
  function void getBitsImpl_io_dma0Ext_rsp_payload_rsp;
    output bit [$bits(dut.io_dma0Ext_rsp_payload_rsp)-1:0] value_io_dma0Ext_rsp_payload_rsp;
    value_io_dma0Ext_rsp_payload_rsp = io_dma0Ext_rsp_payload_rsp;
  endfunction

  // Port 1f: io_dma0Ext_rsp_payload_data
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_rsp_payload_data;
  function void getBitWidthImpl_io_dma0Ext_rsp_payload_data;
    output int value;
    value = $bits(dut.io_dma0Ext_rsp_payload_data);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma0Ext_rsp_payload_data;
  function void setBitsImpl_io_dma0Ext_rsp_payload_data;
    input bit [$bits(dut.io_dma0Ext_rsp_payload_data)-1:0] value_io_dma0Ext_rsp_payload_data;
    io_dma0Ext_rsp_payload_data = value_io_dma0Ext_rsp_payload_data;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_rsp_payload_data;
  function void getBitsImpl_io_dma0Ext_rsp_payload_data;
    output bit [$bits(dut.io_dma0Ext_rsp_payload_data)-1:0] value_io_dma0Ext_rsp_payload_data;
    value_io_dma0Ext_rsp_payload_data = io_dma0Ext_rsp_payload_data;
  endfunction

  // Port 20: io_dma0Ext_rsp_ready
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_rsp_ready;
  function void getBitWidthImpl_io_dma0Ext_rsp_ready;
    output int value;
    value = $bits(dut.io_dma0Ext_rsp_ready);
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_rsp_ready;
  function void getBitsImpl_io_dma0Ext_rsp_ready;
    output bit [$bits(dut.io_dma0Ext_rsp_ready)-1:0] value_io_dma0Ext_rsp_ready;
    value_io_dma0Ext_rsp_ready = io_dma0Ext_rsp_ready;
  endfunction

  // Port 21: io_dma0Ext_rsp_valid
  export "DPI-C" function getBitWidthImpl_io_dma0Ext_rsp_valid;
  function void getBitWidthImpl_io_dma0Ext_rsp_valid;
    output int value;
    value = $bits(dut.io_dma0Ext_rsp_valid);
  endfunction
  export "DPI-C" function setBitsImpl_io_dma0Ext_rsp_valid;
  function void setBitsImpl_io_dma0Ext_rsp_valid;
    input bit [$bits(dut.io_dma0Ext_rsp_valid)-1:0] value_io_dma0Ext_rsp_valid;
    io_dma0Ext_rsp_valid = value_io_dma0Ext_rsp_valid;
  endfunction
  export "DPI-C" function getBitsImpl_io_dma0Ext_rsp_valid;
  function void getBitsImpl_io_dma0Ext_rsp_valid;
    output bit [$bits(dut.io_dma0Ext_rsp_valid)-1:0] value_io_dma0Ext_rsp_valid;
    value_io_dma0Ext_rsp_valid = io_dma0Ext_rsp_valid;
  endfunction

  // Port 22: io_resp_payload_err
  export "DPI-C" function getBitWidthImpl_io_resp_payload_err;
  function void getBitWidthImpl_io_resp_payload_err;
    output int value;
    value = $bits(dut.io_resp_payload_err);
  endfunction
  export "DPI-C" function getBitsImpl_io_resp_payload_err;
  function void getBitsImpl_io_resp_payload_err;
    output bit [$bits(dut.io_resp_payload_err)-1:0] value_io_resp_payload_err;
    value_io_resp_payload_err = io_resp_payload_err;
  endfunction

  // Port 23: io_resp_payload_done
  export "DPI-C" function getBitWidthImpl_io_resp_payload_done;
  function void getBitWidthImpl_io_resp_payload_done;
    output int value;
    value = $bits(dut.io_resp_payload_done);
  endfunction
  export "DPI-C" function getBitsImpl_io_resp_payload_done;
  function void getBitsImpl_io_resp_payload_done;
    output bit [$bits(dut.io_resp_payload_done)-1:0] value_io_resp_payload_done;
    value_io_resp_payload_done = io_resp_payload_done;
  endfunction

  // Port 24: io_resp_payload_data
  export "DPI-C" function getBitWidthImpl_io_resp_payload_data;
  function void getBitWidthImpl_io_resp_payload_data;
    output int value;
    value = $bits(dut.io_resp_payload_data);
  endfunction
  export "DPI-C" function getBitsImpl_io_resp_payload_data;
  function void getBitsImpl_io_resp_payload_data;
    output bit [$bits(dut.io_resp_payload_data)-1:0] value_io_resp_payload_data;
    value_io_resp_payload_data = io_resp_payload_data;
  endfunction

  // Port 25: io_resp_ready
  export "DPI-C" function getBitWidthImpl_io_resp_ready;
  function void getBitWidthImpl_io_resp_ready;
    output int value;
    value = $bits(dut.io_resp_ready);
  endfunction
  export "DPI-C" function setBitsImpl_io_resp_ready;
  function void setBitsImpl_io_resp_ready;
    input bit [$bits(dut.io_resp_ready)-1:0] value_io_resp_ready;
    io_resp_ready = value_io_resp_ready;
  endfunction
  export "DPI-C" function getBitsImpl_io_resp_ready;
  function void getBitsImpl_io_resp_ready;
    output bit [$bits(dut.io_resp_ready)-1:0] value_io_resp_ready;
    value_io_resp_ready = io_resp_ready;
  endfunction

  // Port 26: io_resp_valid
  export "DPI-C" function getBitWidthImpl_io_resp_valid;
  function void getBitWidthImpl_io_resp_valid;
    output int value;
    value = $bits(dut.io_resp_valid);
  endfunction
  export "DPI-C" function getBitsImpl_io_resp_valid;
  function void getBitsImpl_io_resp_valid;
    output bit [$bits(dut.io_resp_valid)-1:0] value_io_resp_valid;
    value_io_resp_valid = io_resp_valid;
  endfunction

  // Port 27: io_cmd_payload_data1
  export "DPI-C" function getBitWidthImpl_io_cmd_payload_data1;
  function void getBitWidthImpl_io_cmd_payload_data1;
    output int value;
    value = $bits(dut.io_cmd_payload_data1);
  endfunction
  export "DPI-C" function setBitsImpl_io_cmd_payload_data1;
  function void setBitsImpl_io_cmd_payload_data1;
    input bit [$bits(dut.io_cmd_payload_data1)-1:0] value_io_cmd_payload_data1;
    io_cmd_payload_data1 = value_io_cmd_payload_data1;
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_payload_data1;
  function void getBitsImpl_io_cmd_payload_data1;
    output bit [$bits(dut.io_cmd_payload_data1)-1:0] value_io_cmd_payload_data1;
    value_io_cmd_payload_data1 = io_cmd_payload_data1;
  endfunction

  // Port 28: io_cmd_payload_data0
  export "DPI-C" function getBitWidthImpl_io_cmd_payload_data0;
  function void getBitWidthImpl_io_cmd_payload_data0;
    output int value;
    value = $bits(dut.io_cmd_payload_data0);
  endfunction
  export "DPI-C" function setBitsImpl_io_cmd_payload_data0;
  function void setBitsImpl_io_cmd_payload_data0;
    input bit [$bits(dut.io_cmd_payload_data0)-1:0] value_io_cmd_payload_data0;
    io_cmd_payload_data0 = value_io_cmd_payload_data0;
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_payload_data0;
  function void getBitsImpl_io_cmd_payload_data0;
    output bit [$bits(dut.io_cmd_payload_data0)-1:0] value_io_cmd_payload_data0;
    value_io_cmd_payload_data0 = io_cmd_payload_data0;
  endfunction

  // Port 29: io_cmd_payload_reg1Addr
  export "DPI-C" function getBitWidthImpl_io_cmd_payload_reg1Addr;
  function void getBitWidthImpl_io_cmd_payload_reg1Addr;
    output int value;
    value = $bits(dut.io_cmd_payload_reg1Addr);
  endfunction
  export "DPI-C" function setBitsImpl_io_cmd_payload_reg1Addr;
  function void setBitsImpl_io_cmd_payload_reg1Addr;
    input bit [$bits(dut.io_cmd_payload_reg1Addr)-1:0] value_io_cmd_payload_reg1Addr;
    io_cmd_payload_reg1Addr = value_io_cmd_payload_reg1Addr;
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_payload_reg1Addr;
  function void getBitsImpl_io_cmd_payload_reg1Addr;
    output bit [$bits(dut.io_cmd_payload_reg1Addr)-1:0] value_io_cmd_payload_reg1Addr;
    value_io_cmd_payload_reg1Addr = io_cmd_payload_reg1Addr;
  endfunction

  // Port 2a: io_cmd_payload_reg0Addr
  export "DPI-C" function getBitWidthImpl_io_cmd_payload_reg0Addr;
  function void getBitWidthImpl_io_cmd_payload_reg0Addr;
    output int value;
    value = $bits(dut.io_cmd_payload_reg0Addr);
  endfunction
  export "DPI-C" function setBitsImpl_io_cmd_payload_reg0Addr;
  function void setBitsImpl_io_cmd_payload_reg0Addr;
    input bit [$bits(dut.io_cmd_payload_reg0Addr)-1:0] value_io_cmd_payload_reg0Addr;
    io_cmd_payload_reg0Addr = value_io_cmd_payload_reg0Addr;
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_payload_reg0Addr;
  function void getBitsImpl_io_cmd_payload_reg0Addr;
    output bit [$bits(dut.io_cmd_payload_reg0Addr)-1:0] value_io_cmd_payload_reg0Addr;
    value_io_cmd_payload_reg0Addr = io_cmd_payload_reg0Addr;
  endfunction

  // Port 2b: io_cmd_payload_op
  export "DPI-C" function getBitWidthImpl_io_cmd_payload_op;
  function void getBitWidthImpl_io_cmd_payload_op;
    output int value;
    value = $bits(dut.io_cmd_payload_op);
  endfunction
  export "DPI-C" function setBitsImpl_io_cmd_payload_op;
  function void setBitsImpl_io_cmd_payload_op;
    input bit [$bits(dut.io_cmd_payload_op)-1:0] value_io_cmd_payload_op;
    io_cmd_payload_op = value_io_cmd_payload_op;
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_payload_op;
  function void getBitsImpl_io_cmd_payload_op;
    output bit [$bits(dut.io_cmd_payload_op)-1:0] value_io_cmd_payload_op;
    value_io_cmd_payload_op = io_cmd_payload_op;
  endfunction

  // Port 2c: io_cmd_ready
  export "DPI-C" function getBitWidthImpl_io_cmd_ready;
  function void getBitWidthImpl_io_cmd_ready;
    output int value;
    value = $bits(dut.io_cmd_ready);
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_ready;
  function void getBitsImpl_io_cmd_ready;
    output bit [$bits(dut.io_cmd_ready)-1:0] value_io_cmd_ready;
    value_io_cmd_ready = io_cmd_ready;
  endfunction

  // Port 2d: io_cmd_valid
  export "DPI-C" function getBitWidthImpl_io_cmd_valid;
  function void getBitWidthImpl_io_cmd_valid;
    output int value;
    value = $bits(dut.io_cmd_valid);
  endfunction
  export "DPI-C" function setBitsImpl_io_cmd_valid;
  function void setBitsImpl_io_cmd_valid;
    input bit [$bits(dut.io_cmd_valid)-1:0] value_io_cmd_valid;
    io_cmd_valid = value_io_cmd_valid;
  endfunction
  export "DPI-C" function getBitsImpl_io_cmd_valid;
  function void getBitsImpl_io_cmd_valid;
    output bit [$bits(dut.io_cmd_valid)-1:0] value_io_cmd_valid;
    value_io_cmd_valid = io_cmd_valid;
  endfunction

  // Simulation
  import "DPI-C" context task simulation_body();
  enum {INIT, RUN, DONE} simulationState = INIT;
  initial
    simulationState = RUN;
  always @(simulationState) begin
    if (simulationState == RUN) begin
      simulation_body();
      simulationState = DONE;
    end
  end
  import "DPI-C" context task simulation_final();
  final
    simulation_final();
  `ifdef SVSIM_BACKEND_SUPPORTS_DELAY_IN_PUBLIC_FUNCTIONS
  export "DPI-C" task run_simulation;
  task run_simulation;
    input int timesteps;
    output int finish;
    #(timesteps*0.1);
    finish = 0;
  endtask
  `else
  import "DPI-C" function void run_simulation(input int timesteps, output int done);
  `endif

  // Tracing
  int traceSupported = 0;
  export "DPI-C" function simulation_initializeTrace;
  function void simulation_initializeTrace;
    input string traceFilePath;
    `ifdef SVSIM_ENABLE_FST_TRACING_SUPPORT
      $dumpfile({traceFilePath,".fst"});
      $dumpvars(0, dut);
      traceSupported = 1;
    `elsif SVSIM_ENABLE_VCD_TRACING_SUPPORT
      $dumpfile({traceFilePath,".vcd"});
      $dumpvars(0, dut);
      traceSupported = 1;
    `endif
    `ifdef SVSIM_ENABLE_VPD_TRACING_SUPPORT
      $vcdplusfile({traceFilePath,".vpd"});
      $dumpvars(0, dut);
      $vcdpluson(0, dut);
      traceSupported = 1;
    `endif
    `ifdef SVSIM_ENABLE_FSDB_TRACING_SUPPORT
      $fsdbDumpfile({traceFilePath,".fsdb"});
      $fsdbDumpvars(0, dut, "+all");
      traceSupported = 1;
    `endif
  endfunction
  export "DPI-C" function simulation_enableTrace;
  function void simulation_enableTrace;
    output int success;
    success = traceSupported;
    `ifdef SVSIM_ENABLE_VCD_TRACING_SUPPORT
    $dumpon;
    `elsif SVSIM_ENABLE_FST_TRACING_SUPPORT
    $dumpon;
    `elsif SVSIM_ENABLE_VPD_TRACING_SUPPORT
    $dumpon;
    `endif
    `ifdef SVSIM_ENABLE_FSDB_TRACING_SUPPORT
    $fsdbDumpon;
    `endif
  endfunction
  export "DPI-C" function simulation_disableTrace;
  function void simulation_disableTrace;
    output int success;
    success = traceSupported;
    `ifdef SVSIM_ENABLE_VCD_TRACING_SUPPORT
    $dumpoff;
    `elsif SVSIM_ENABLE_FST_TRACING_SUPPORT
    $dumpoff;
    `elsif SVSIM_ENABLE_VPD_TRACING_SUPPORT
    $dumpoff;
    `endif
    `ifdef SVSIM_ENABLE_FSDB_TRACING_SUPPORT
    $fsdbDumpoff;
    `endif
  endfunction

endmodule
