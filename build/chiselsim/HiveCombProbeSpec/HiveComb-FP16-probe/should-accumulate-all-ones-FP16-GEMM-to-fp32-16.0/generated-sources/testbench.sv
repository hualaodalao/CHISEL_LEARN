module svsimTestbench;
  reg  [$bits(dut.clock)-1:0] clock = '0;
  reg  [$bits(dut.reset)-1:0] reset = '0;
  wire [$bits(dut.io_validOut_0)-1:0] io_validOut_0;
  wire [$bits(dut.io_validOut_1)-1:0] io_validOut_1;
  wire [$bits(dut.io_validOut_2)-1:0] io_validOut_2;
  wire [$bits(dut.io_validOut_3)-1:0] io_validOut_3;
  wire [$bits(dut.io_validOut_4)-1:0] io_validOut_4;
  wire [$bits(dut.io_validOut_5)-1:0] io_validOut_5;
  wire [$bits(dut.io_validOut_6)-1:0] io_validOut_6;
  wire [$bits(dut.io_validOut_7)-1:0] io_validOut_7;
  wire [$bits(dut.io_validOut_8)-1:0] io_validOut_8;
  wire [$bits(dut.io_validOut_9)-1:0] io_validOut_9;
  wire [$bits(dut.io_validOut_10)-1:0] io_validOut_10;
  wire [$bits(dut.io_validOut_11)-1:0] io_validOut_11;
  wire [$bits(dut.io_validOut_12)-1:0] io_validOut_12;
  wire [$bits(dut.io_validOut_13)-1:0] io_validOut_13;
  wire [$bits(dut.io_validOut_14)-1:0] io_validOut_14;
  wire [$bits(dut.io_validOut_15)-1:0] io_validOut_15;
  reg  [$bits(dut.io_loadVLock)-1:0] io_loadVLock = '0;
  reg  [$bits(dut.io_clear)-1:0] io_clear = '0;
  reg  [$bits(dut.io_rndIn)-1:0] io_rndIn = '0;
  reg  [$bits(dut.io_fmtIn)-1:0] io_fmtIn = '0;
  reg  [$bits(dut.io_validIn)-1:0] io_validIn = '0;
  reg  [$bits(dut.io_loadVIn)-1:0] io_loadVIn = '0;
  reg  [$bits(dut.io_loadHIn)-1:0] io_loadHIn = '0;
  wire [$bits(dut.io_cOut_0)-1:0] io_cOut_0;
  wire [$bits(dut.io_cOut_1)-1:0] io_cOut_1;
  wire [$bits(dut.io_cOut_2)-1:0] io_cOut_2;
  wire [$bits(dut.io_cOut_3)-1:0] io_cOut_3;
  wire [$bits(dut.io_cOut_4)-1:0] io_cOut_4;
  wire [$bits(dut.io_cOut_5)-1:0] io_cOut_5;
  wire [$bits(dut.io_cOut_6)-1:0] io_cOut_6;
  wire [$bits(dut.io_cOut_7)-1:0] io_cOut_7;
  wire [$bits(dut.io_cOut_8)-1:0] io_cOut_8;
  wire [$bits(dut.io_cOut_9)-1:0] io_cOut_9;
  wire [$bits(dut.io_cOut_10)-1:0] io_cOut_10;
  wire [$bits(dut.io_cOut_11)-1:0] io_cOut_11;
  wire [$bits(dut.io_cOut_12)-1:0] io_cOut_12;
  wire [$bits(dut.io_cOut_13)-1:0] io_cOut_13;
  wire [$bits(dut.io_cOut_14)-1:0] io_cOut_14;
  wire [$bits(dut.io_cOut_15)-1:0] io_cOut_15;
  reg  [$bits(dut.io_psumIn_0)-1:0] io_psumIn_0 = '0;
  reg  [$bits(dut.io_psumIn_1)-1:0] io_psumIn_1 = '0;
  reg  [$bits(dut.io_psumIn_2)-1:0] io_psumIn_2 = '0;
  reg  [$bits(dut.io_psumIn_3)-1:0] io_psumIn_3 = '0;
  reg  [$bits(dut.io_psumIn_4)-1:0] io_psumIn_4 = '0;
  reg  [$bits(dut.io_psumIn_5)-1:0] io_psumIn_5 = '0;
  reg  [$bits(dut.io_psumIn_6)-1:0] io_psumIn_6 = '0;
  reg  [$bits(dut.io_psumIn_7)-1:0] io_psumIn_7 = '0;
  reg  [$bits(dut.io_psumIn_8)-1:0] io_psumIn_8 = '0;
  reg  [$bits(dut.io_psumIn_9)-1:0] io_psumIn_9 = '0;
  reg  [$bits(dut.io_psumIn_10)-1:0] io_psumIn_10 = '0;
  reg  [$bits(dut.io_psumIn_11)-1:0] io_psumIn_11 = '0;
  reg  [$bits(dut.io_psumIn_12)-1:0] io_psumIn_12 = '0;
  reg  [$bits(dut.io_psumIn_13)-1:0] io_psumIn_13 = '0;
  reg  [$bits(dut.io_psumIn_14)-1:0] io_psumIn_14 = '0;
  reg  [$bits(dut.io_psumIn_15)-1:0] io_psumIn_15 = '0;
  wire [$bits(dut.io_aOut_0)-1:0] io_aOut_0;
  wire [$bits(dut.io_aOut_1)-1:0] io_aOut_1;
  wire [$bits(dut.io_aOut_2)-1:0] io_aOut_2;
  wire [$bits(dut.io_aOut_3)-1:0] io_aOut_3;
  wire [$bits(dut.io_aOut_4)-1:0] io_aOut_4;
  wire [$bits(dut.io_aOut_5)-1:0] io_aOut_5;
  wire [$bits(dut.io_aOut_6)-1:0] io_aOut_6;
  wire [$bits(dut.io_aOut_7)-1:0] io_aOut_7;
  wire [$bits(dut.io_aOut_8)-1:0] io_aOut_8;
  wire [$bits(dut.io_aOut_9)-1:0] io_aOut_9;
  wire [$bits(dut.io_aOut_10)-1:0] io_aOut_10;
  wire [$bits(dut.io_aOut_11)-1:0] io_aOut_11;
  wire [$bits(dut.io_aOut_12)-1:0] io_aOut_12;
  wire [$bits(dut.io_aOut_13)-1:0] io_aOut_13;
  wire [$bits(dut.io_aOut_14)-1:0] io_aOut_14;
  wire [$bits(dut.io_aOut_15)-1:0] io_aOut_15;
  reg  [$bits(dut.io_aIn_0)-1:0] io_aIn_0 = '0;
  reg  [$bits(dut.io_aIn_1)-1:0] io_aIn_1 = '0;
  reg  [$bits(dut.io_aIn_2)-1:0] io_aIn_2 = '0;
  reg  [$bits(dut.io_aIn_3)-1:0] io_aIn_3 = '0;
  reg  [$bits(dut.io_aIn_4)-1:0] io_aIn_4 = '0;
  reg  [$bits(dut.io_aIn_5)-1:0] io_aIn_5 = '0;
  reg  [$bits(dut.io_aIn_6)-1:0] io_aIn_6 = '0;
  reg  [$bits(dut.io_aIn_7)-1:0] io_aIn_7 = '0;
  reg  [$bits(dut.io_aIn_8)-1:0] io_aIn_8 = '0;
  reg  [$bits(dut.io_aIn_9)-1:0] io_aIn_9 = '0;
  reg  [$bits(dut.io_aIn_10)-1:0] io_aIn_10 = '0;
  reg  [$bits(dut.io_aIn_11)-1:0] io_aIn_11 = '0;
  reg  [$bits(dut.io_aIn_12)-1:0] io_aIn_12 = '0;
  reg  [$bits(dut.io_aIn_13)-1:0] io_aIn_13 = '0;
  reg  [$bits(dut.io_aIn_14)-1:0] io_aIn_14 = '0;
  reg  [$bits(dut.io_aIn_15)-1:0] io_aIn_15 = '0;

HiveComb dut (
    .clock(clock),
    .reset(reset),
    .io_validOut_0(io_validOut_0),
    .io_validOut_1(io_validOut_1),
    .io_validOut_2(io_validOut_2),
    .io_validOut_3(io_validOut_3),
    .io_validOut_4(io_validOut_4),
    .io_validOut_5(io_validOut_5),
    .io_validOut_6(io_validOut_6),
    .io_validOut_7(io_validOut_7),
    .io_validOut_8(io_validOut_8),
    .io_validOut_9(io_validOut_9),
    .io_validOut_10(io_validOut_10),
    .io_validOut_11(io_validOut_11),
    .io_validOut_12(io_validOut_12),
    .io_validOut_13(io_validOut_13),
    .io_validOut_14(io_validOut_14),
    .io_validOut_15(io_validOut_15),
    .io_loadVLock(io_loadVLock),
    .io_clear(io_clear),
    .io_rndIn(io_rndIn),
    .io_fmtIn(io_fmtIn),
    .io_validIn(io_validIn),
    .io_loadVIn(io_loadVIn),
    .io_loadHIn(io_loadHIn),
    .io_cOut_0(io_cOut_0),
    .io_cOut_1(io_cOut_1),
    .io_cOut_2(io_cOut_2),
    .io_cOut_3(io_cOut_3),
    .io_cOut_4(io_cOut_4),
    .io_cOut_5(io_cOut_5),
    .io_cOut_6(io_cOut_6),
    .io_cOut_7(io_cOut_7),
    .io_cOut_8(io_cOut_8),
    .io_cOut_9(io_cOut_9),
    .io_cOut_10(io_cOut_10),
    .io_cOut_11(io_cOut_11),
    .io_cOut_12(io_cOut_12),
    .io_cOut_13(io_cOut_13),
    .io_cOut_14(io_cOut_14),
    .io_cOut_15(io_cOut_15),
    .io_psumIn_0(io_psumIn_0),
    .io_psumIn_1(io_psumIn_1),
    .io_psumIn_2(io_psumIn_2),
    .io_psumIn_3(io_psumIn_3),
    .io_psumIn_4(io_psumIn_4),
    .io_psumIn_5(io_psumIn_5),
    .io_psumIn_6(io_psumIn_6),
    .io_psumIn_7(io_psumIn_7),
    .io_psumIn_8(io_psumIn_8),
    .io_psumIn_9(io_psumIn_9),
    .io_psumIn_10(io_psumIn_10),
    .io_psumIn_11(io_psumIn_11),
    .io_psumIn_12(io_psumIn_12),
    .io_psumIn_13(io_psumIn_13),
    .io_psumIn_14(io_psumIn_14),
    .io_psumIn_15(io_psumIn_15),
    .io_aOut_0(io_aOut_0),
    .io_aOut_1(io_aOut_1),
    .io_aOut_2(io_aOut_2),
    .io_aOut_3(io_aOut_3),
    .io_aOut_4(io_aOut_4),
    .io_aOut_5(io_aOut_5),
    .io_aOut_6(io_aOut_6),
    .io_aOut_7(io_aOut_7),
    .io_aOut_8(io_aOut_8),
    .io_aOut_9(io_aOut_9),
    .io_aOut_10(io_aOut_10),
    .io_aOut_11(io_aOut_11),
    .io_aOut_12(io_aOut_12),
    .io_aOut_13(io_aOut_13),
    .io_aOut_14(io_aOut_14),
    .io_aOut_15(io_aOut_15),
    .io_aIn_0(io_aIn_0),
    .io_aIn_1(io_aIn_1),
    .io_aIn_2(io_aIn_2),
    .io_aIn_3(io_aIn_3),
    .io_aIn_4(io_aIn_4),
    .io_aIn_5(io_aIn_5),
    .io_aIn_6(io_aIn_6),
    .io_aIn_7(io_aIn_7),
    .io_aIn_8(io_aIn_8),
    .io_aIn_9(io_aIn_9),
    .io_aIn_10(io_aIn_10),
    .io_aIn_11(io_aIn_11),
    .io_aIn_12(io_aIn_12),
    .io_aIn_13(io_aIn_13),
    .io_aIn_14(io_aIn_14),
    .io_aIn_15(io_aIn_15)
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

  // Port 2: io_validOut_0
  export "DPI-C" function getBitWidthImpl_io_validOut_0;
  function void getBitWidthImpl_io_validOut_0;
    output int value;
    value = $bits(dut.io_validOut_0);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_0;
  function void getBitsImpl_io_validOut_0;
    output bit [$bits(dut.io_validOut_0)-1:0] value_io_validOut_0;
    value_io_validOut_0 = io_validOut_0;
  endfunction

  // Port 3: io_validOut_1
  export "DPI-C" function getBitWidthImpl_io_validOut_1;
  function void getBitWidthImpl_io_validOut_1;
    output int value;
    value = $bits(dut.io_validOut_1);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_1;
  function void getBitsImpl_io_validOut_1;
    output bit [$bits(dut.io_validOut_1)-1:0] value_io_validOut_1;
    value_io_validOut_1 = io_validOut_1;
  endfunction

  // Port 4: io_validOut_2
  export "DPI-C" function getBitWidthImpl_io_validOut_2;
  function void getBitWidthImpl_io_validOut_2;
    output int value;
    value = $bits(dut.io_validOut_2);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_2;
  function void getBitsImpl_io_validOut_2;
    output bit [$bits(dut.io_validOut_2)-1:0] value_io_validOut_2;
    value_io_validOut_2 = io_validOut_2;
  endfunction

  // Port 5: io_validOut_3
  export "DPI-C" function getBitWidthImpl_io_validOut_3;
  function void getBitWidthImpl_io_validOut_3;
    output int value;
    value = $bits(dut.io_validOut_3);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_3;
  function void getBitsImpl_io_validOut_3;
    output bit [$bits(dut.io_validOut_3)-1:0] value_io_validOut_3;
    value_io_validOut_3 = io_validOut_3;
  endfunction

  // Port 6: io_validOut_4
  export "DPI-C" function getBitWidthImpl_io_validOut_4;
  function void getBitWidthImpl_io_validOut_4;
    output int value;
    value = $bits(dut.io_validOut_4);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_4;
  function void getBitsImpl_io_validOut_4;
    output bit [$bits(dut.io_validOut_4)-1:0] value_io_validOut_4;
    value_io_validOut_4 = io_validOut_4;
  endfunction

  // Port 7: io_validOut_5
  export "DPI-C" function getBitWidthImpl_io_validOut_5;
  function void getBitWidthImpl_io_validOut_5;
    output int value;
    value = $bits(dut.io_validOut_5);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_5;
  function void getBitsImpl_io_validOut_5;
    output bit [$bits(dut.io_validOut_5)-1:0] value_io_validOut_5;
    value_io_validOut_5 = io_validOut_5;
  endfunction

  // Port 8: io_validOut_6
  export "DPI-C" function getBitWidthImpl_io_validOut_6;
  function void getBitWidthImpl_io_validOut_6;
    output int value;
    value = $bits(dut.io_validOut_6);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_6;
  function void getBitsImpl_io_validOut_6;
    output bit [$bits(dut.io_validOut_6)-1:0] value_io_validOut_6;
    value_io_validOut_6 = io_validOut_6;
  endfunction

  // Port 9: io_validOut_7
  export "DPI-C" function getBitWidthImpl_io_validOut_7;
  function void getBitWidthImpl_io_validOut_7;
    output int value;
    value = $bits(dut.io_validOut_7);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_7;
  function void getBitsImpl_io_validOut_7;
    output bit [$bits(dut.io_validOut_7)-1:0] value_io_validOut_7;
    value_io_validOut_7 = io_validOut_7;
  endfunction

  // Port a: io_validOut_8
  export "DPI-C" function getBitWidthImpl_io_validOut_8;
  function void getBitWidthImpl_io_validOut_8;
    output int value;
    value = $bits(dut.io_validOut_8);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_8;
  function void getBitsImpl_io_validOut_8;
    output bit [$bits(dut.io_validOut_8)-1:0] value_io_validOut_8;
    value_io_validOut_8 = io_validOut_8;
  endfunction

  // Port b: io_validOut_9
  export "DPI-C" function getBitWidthImpl_io_validOut_9;
  function void getBitWidthImpl_io_validOut_9;
    output int value;
    value = $bits(dut.io_validOut_9);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_9;
  function void getBitsImpl_io_validOut_9;
    output bit [$bits(dut.io_validOut_9)-1:0] value_io_validOut_9;
    value_io_validOut_9 = io_validOut_9;
  endfunction

  // Port c: io_validOut_10
  export "DPI-C" function getBitWidthImpl_io_validOut_10;
  function void getBitWidthImpl_io_validOut_10;
    output int value;
    value = $bits(dut.io_validOut_10);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_10;
  function void getBitsImpl_io_validOut_10;
    output bit [$bits(dut.io_validOut_10)-1:0] value_io_validOut_10;
    value_io_validOut_10 = io_validOut_10;
  endfunction

  // Port d: io_validOut_11
  export "DPI-C" function getBitWidthImpl_io_validOut_11;
  function void getBitWidthImpl_io_validOut_11;
    output int value;
    value = $bits(dut.io_validOut_11);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_11;
  function void getBitsImpl_io_validOut_11;
    output bit [$bits(dut.io_validOut_11)-1:0] value_io_validOut_11;
    value_io_validOut_11 = io_validOut_11;
  endfunction

  // Port e: io_validOut_12
  export "DPI-C" function getBitWidthImpl_io_validOut_12;
  function void getBitWidthImpl_io_validOut_12;
    output int value;
    value = $bits(dut.io_validOut_12);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_12;
  function void getBitsImpl_io_validOut_12;
    output bit [$bits(dut.io_validOut_12)-1:0] value_io_validOut_12;
    value_io_validOut_12 = io_validOut_12;
  endfunction

  // Port f: io_validOut_13
  export "DPI-C" function getBitWidthImpl_io_validOut_13;
  function void getBitWidthImpl_io_validOut_13;
    output int value;
    value = $bits(dut.io_validOut_13);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_13;
  function void getBitsImpl_io_validOut_13;
    output bit [$bits(dut.io_validOut_13)-1:0] value_io_validOut_13;
    value_io_validOut_13 = io_validOut_13;
  endfunction

  // Port 10: io_validOut_14
  export "DPI-C" function getBitWidthImpl_io_validOut_14;
  function void getBitWidthImpl_io_validOut_14;
    output int value;
    value = $bits(dut.io_validOut_14);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_14;
  function void getBitsImpl_io_validOut_14;
    output bit [$bits(dut.io_validOut_14)-1:0] value_io_validOut_14;
    value_io_validOut_14 = io_validOut_14;
  endfunction

  // Port 11: io_validOut_15
  export "DPI-C" function getBitWidthImpl_io_validOut_15;
  function void getBitWidthImpl_io_validOut_15;
    output int value;
    value = $bits(dut.io_validOut_15);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut_15;
  function void getBitsImpl_io_validOut_15;
    output bit [$bits(dut.io_validOut_15)-1:0] value_io_validOut_15;
    value_io_validOut_15 = io_validOut_15;
  endfunction

  // Port 12: io_loadVLock
  export "DPI-C" function getBitWidthImpl_io_loadVLock;
  function void getBitWidthImpl_io_loadVLock;
    output int value;
    value = $bits(dut.io_loadVLock);
  endfunction
  export "DPI-C" function setBitsImpl_io_loadVLock;
  function void setBitsImpl_io_loadVLock;
    input bit [$bits(dut.io_loadVLock)-1:0] value_io_loadVLock;
    io_loadVLock = value_io_loadVLock;
  endfunction
  export "DPI-C" function getBitsImpl_io_loadVLock;
  function void getBitsImpl_io_loadVLock;
    output bit [$bits(dut.io_loadVLock)-1:0] value_io_loadVLock;
    value_io_loadVLock = io_loadVLock;
  endfunction

  // Port 13: io_clear
  export "DPI-C" function getBitWidthImpl_io_clear;
  function void getBitWidthImpl_io_clear;
    output int value;
    value = $bits(dut.io_clear);
  endfunction
  export "DPI-C" function setBitsImpl_io_clear;
  function void setBitsImpl_io_clear;
    input bit [$bits(dut.io_clear)-1:0] value_io_clear;
    io_clear = value_io_clear;
  endfunction
  export "DPI-C" function getBitsImpl_io_clear;
  function void getBitsImpl_io_clear;
    output bit [$bits(dut.io_clear)-1:0] value_io_clear;
    value_io_clear = io_clear;
  endfunction

  // Port 14: io_rndIn
  export "DPI-C" function getBitWidthImpl_io_rndIn;
  function void getBitWidthImpl_io_rndIn;
    output int value;
    value = $bits(dut.io_rndIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_rndIn;
  function void setBitsImpl_io_rndIn;
    input bit [$bits(dut.io_rndIn)-1:0] value_io_rndIn;
    io_rndIn = value_io_rndIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_rndIn;
  function void getBitsImpl_io_rndIn;
    output bit [$bits(dut.io_rndIn)-1:0] value_io_rndIn;
    value_io_rndIn = io_rndIn;
  endfunction

  // Port 15: io_fmtIn
  export "DPI-C" function getBitWidthImpl_io_fmtIn;
  function void getBitWidthImpl_io_fmtIn;
    output int value;
    value = $bits(dut.io_fmtIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_fmtIn;
  function void setBitsImpl_io_fmtIn;
    input bit [$bits(dut.io_fmtIn)-1:0] value_io_fmtIn;
    io_fmtIn = value_io_fmtIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_fmtIn;
  function void getBitsImpl_io_fmtIn;
    output bit [$bits(dut.io_fmtIn)-1:0] value_io_fmtIn;
    value_io_fmtIn = io_fmtIn;
  endfunction

  // Port 16: io_validIn
  export "DPI-C" function getBitWidthImpl_io_validIn;
  function void getBitWidthImpl_io_validIn;
    output int value;
    value = $bits(dut.io_validIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_validIn;
  function void setBitsImpl_io_validIn;
    input bit [$bits(dut.io_validIn)-1:0] value_io_validIn;
    io_validIn = value_io_validIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_validIn;
  function void getBitsImpl_io_validIn;
    output bit [$bits(dut.io_validIn)-1:0] value_io_validIn;
    value_io_validIn = io_validIn;
  endfunction

  // Port 17: io_loadVIn
  export "DPI-C" function getBitWidthImpl_io_loadVIn;
  function void getBitWidthImpl_io_loadVIn;
    output int value;
    value = $bits(dut.io_loadVIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_loadVIn;
  function void setBitsImpl_io_loadVIn;
    input bit [$bits(dut.io_loadVIn)-1:0] value_io_loadVIn;
    io_loadVIn = value_io_loadVIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_loadVIn;
  function void getBitsImpl_io_loadVIn;
    output bit [$bits(dut.io_loadVIn)-1:0] value_io_loadVIn;
    value_io_loadVIn = io_loadVIn;
  endfunction

  // Port 18: io_loadHIn
  export "DPI-C" function getBitWidthImpl_io_loadHIn;
  function void getBitWidthImpl_io_loadHIn;
    output int value;
    value = $bits(dut.io_loadHIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_loadHIn;
  function void setBitsImpl_io_loadHIn;
    input bit [$bits(dut.io_loadHIn)-1:0] value_io_loadHIn;
    io_loadHIn = value_io_loadHIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_loadHIn;
  function void getBitsImpl_io_loadHIn;
    output bit [$bits(dut.io_loadHIn)-1:0] value_io_loadHIn;
    value_io_loadHIn = io_loadHIn;
  endfunction

  // Port 19: io_cOut_0
  export "DPI-C" function getBitWidthImpl_io_cOut_0;
  function void getBitWidthImpl_io_cOut_0;
    output int value;
    value = $bits(dut.io_cOut_0);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_0;
  function void getBitsImpl_io_cOut_0;
    output bit [$bits(dut.io_cOut_0)-1:0] value_io_cOut_0;
    value_io_cOut_0 = io_cOut_0;
  endfunction

  // Port 1a: io_cOut_1
  export "DPI-C" function getBitWidthImpl_io_cOut_1;
  function void getBitWidthImpl_io_cOut_1;
    output int value;
    value = $bits(dut.io_cOut_1);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_1;
  function void getBitsImpl_io_cOut_1;
    output bit [$bits(dut.io_cOut_1)-1:0] value_io_cOut_1;
    value_io_cOut_1 = io_cOut_1;
  endfunction

  // Port 1b: io_cOut_2
  export "DPI-C" function getBitWidthImpl_io_cOut_2;
  function void getBitWidthImpl_io_cOut_2;
    output int value;
    value = $bits(dut.io_cOut_2);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_2;
  function void getBitsImpl_io_cOut_2;
    output bit [$bits(dut.io_cOut_2)-1:0] value_io_cOut_2;
    value_io_cOut_2 = io_cOut_2;
  endfunction

  // Port 1c: io_cOut_3
  export "DPI-C" function getBitWidthImpl_io_cOut_3;
  function void getBitWidthImpl_io_cOut_3;
    output int value;
    value = $bits(dut.io_cOut_3);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_3;
  function void getBitsImpl_io_cOut_3;
    output bit [$bits(dut.io_cOut_3)-1:0] value_io_cOut_3;
    value_io_cOut_3 = io_cOut_3;
  endfunction

  // Port 1d: io_cOut_4
  export "DPI-C" function getBitWidthImpl_io_cOut_4;
  function void getBitWidthImpl_io_cOut_4;
    output int value;
    value = $bits(dut.io_cOut_4);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_4;
  function void getBitsImpl_io_cOut_4;
    output bit [$bits(dut.io_cOut_4)-1:0] value_io_cOut_4;
    value_io_cOut_4 = io_cOut_4;
  endfunction

  // Port 1e: io_cOut_5
  export "DPI-C" function getBitWidthImpl_io_cOut_5;
  function void getBitWidthImpl_io_cOut_5;
    output int value;
    value = $bits(dut.io_cOut_5);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_5;
  function void getBitsImpl_io_cOut_5;
    output bit [$bits(dut.io_cOut_5)-1:0] value_io_cOut_5;
    value_io_cOut_5 = io_cOut_5;
  endfunction

  // Port 1f: io_cOut_6
  export "DPI-C" function getBitWidthImpl_io_cOut_6;
  function void getBitWidthImpl_io_cOut_6;
    output int value;
    value = $bits(dut.io_cOut_6);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_6;
  function void getBitsImpl_io_cOut_6;
    output bit [$bits(dut.io_cOut_6)-1:0] value_io_cOut_6;
    value_io_cOut_6 = io_cOut_6;
  endfunction

  // Port 20: io_cOut_7
  export "DPI-C" function getBitWidthImpl_io_cOut_7;
  function void getBitWidthImpl_io_cOut_7;
    output int value;
    value = $bits(dut.io_cOut_7);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_7;
  function void getBitsImpl_io_cOut_7;
    output bit [$bits(dut.io_cOut_7)-1:0] value_io_cOut_7;
    value_io_cOut_7 = io_cOut_7;
  endfunction

  // Port 21: io_cOut_8
  export "DPI-C" function getBitWidthImpl_io_cOut_8;
  function void getBitWidthImpl_io_cOut_8;
    output int value;
    value = $bits(dut.io_cOut_8);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_8;
  function void getBitsImpl_io_cOut_8;
    output bit [$bits(dut.io_cOut_8)-1:0] value_io_cOut_8;
    value_io_cOut_8 = io_cOut_8;
  endfunction

  // Port 22: io_cOut_9
  export "DPI-C" function getBitWidthImpl_io_cOut_9;
  function void getBitWidthImpl_io_cOut_9;
    output int value;
    value = $bits(dut.io_cOut_9);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_9;
  function void getBitsImpl_io_cOut_9;
    output bit [$bits(dut.io_cOut_9)-1:0] value_io_cOut_9;
    value_io_cOut_9 = io_cOut_9;
  endfunction

  // Port 23: io_cOut_10
  export "DPI-C" function getBitWidthImpl_io_cOut_10;
  function void getBitWidthImpl_io_cOut_10;
    output int value;
    value = $bits(dut.io_cOut_10);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_10;
  function void getBitsImpl_io_cOut_10;
    output bit [$bits(dut.io_cOut_10)-1:0] value_io_cOut_10;
    value_io_cOut_10 = io_cOut_10;
  endfunction

  // Port 24: io_cOut_11
  export "DPI-C" function getBitWidthImpl_io_cOut_11;
  function void getBitWidthImpl_io_cOut_11;
    output int value;
    value = $bits(dut.io_cOut_11);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_11;
  function void getBitsImpl_io_cOut_11;
    output bit [$bits(dut.io_cOut_11)-1:0] value_io_cOut_11;
    value_io_cOut_11 = io_cOut_11;
  endfunction

  // Port 25: io_cOut_12
  export "DPI-C" function getBitWidthImpl_io_cOut_12;
  function void getBitWidthImpl_io_cOut_12;
    output int value;
    value = $bits(dut.io_cOut_12);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_12;
  function void getBitsImpl_io_cOut_12;
    output bit [$bits(dut.io_cOut_12)-1:0] value_io_cOut_12;
    value_io_cOut_12 = io_cOut_12;
  endfunction

  // Port 26: io_cOut_13
  export "DPI-C" function getBitWidthImpl_io_cOut_13;
  function void getBitWidthImpl_io_cOut_13;
    output int value;
    value = $bits(dut.io_cOut_13);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_13;
  function void getBitsImpl_io_cOut_13;
    output bit [$bits(dut.io_cOut_13)-1:0] value_io_cOut_13;
    value_io_cOut_13 = io_cOut_13;
  endfunction

  // Port 27: io_cOut_14
  export "DPI-C" function getBitWidthImpl_io_cOut_14;
  function void getBitWidthImpl_io_cOut_14;
    output int value;
    value = $bits(dut.io_cOut_14);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_14;
  function void getBitsImpl_io_cOut_14;
    output bit [$bits(dut.io_cOut_14)-1:0] value_io_cOut_14;
    value_io_cOut_14 = io_cOut_14;
  endfunction

  // Port 28: io_cOut_15
  export "DPI-C" function getBitWidthImpl_io_cOut_15;
  function void getBitWidthImpl_io_cOut_15;
    output int value;
    value = $bits(dut.io_cOut_15);
  endfunction
  export "DPI-C" function getBitsImpl_io_cOut_15;
  function void getBitsImpl_io_cOut_15;
    output bit [$bits(dut.io_cOut_15)-1:0] value_io_cOut_15;
    value_io_cOut_15 = io_cOut_15;
  endfunction

  // Port 29: io_psumIn_0
  export "DPI-C" function getBitWidthImpl_io_psumIn_0;
  function void getBitWidthImpl_io_psumIn_0;
    output int value;
    value = $bits(dut.io_psumIn_0);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_0;
  function void setBitsImpl_io_psumIn_0;
    input bit [$bits(dut.io_psumIn_0)-1:0] value_io_psumIn_0;
    io_psumIn_0 = value_io_psumIn_0;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_0;
  function void getBitsImpl_io_psumIn_0;
    output bit [$bits(dut.io_psumIn_0)-1:0] value_io_psumIn_0;
    value_io_psumIn_0 = io_psumIn_0;
  endfunction

  // Port 2a: io_psumIn_1
  export "DPI-C" function getBitWidthImpl_io_psumIn_1;
  function void getBitWidthImpl_io_psumIn_1;
    output int value;
    value = $bits(dut.io_psumIn_1);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_1;
  function void setBitsImpl_io_psumIn_1;
    input bit [$bits(dut.io_psumIn_1)-1:0] value_io_psumIn_1;
    io_psumIn_1 = value_io_psumIn_1;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_1;
  function void getBitsImpl_io_psumIn_1;
    output bit [$bits(dut.io_psumIn_1)-1:0] value_io_psumIn_1;
    value_io_psumIn_1 = io_psumIn_1;
  endfunction

  // Port 2b: io_psumIn_2
  export "DPI-C" function getBitWidthImpl_io_psumIn_2;
  function void getBitWidthImpl_io_psumIn_2;
    output int value;
    value = $bits(dut.io_psumIn_2);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_2;
  function void setBitsImpl_io_psumIn_2;
    input bit [$bits(dut.io_psumIn_2)-1:0] value_io_psumIn_2;
    io_psumIn_2 = value_io_psumIn_2;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_2;
  function void getBitsImpl_io_psumIn_2;
    output bit [$bits(dut.io_psumIn_2)-1:0] value_io_psumIn_2;
    value_io_psumIn_2 = io_psumIn_2;
  endfunction

  // Port 2c: io_psumIn_3
  export "DPI-C" function getBitWidthImpl_io_psumIn_3;
  function void getBitWidthImpl_io_psumIn_3;
    output int value;
    value = $bits(dut.io_psumIn_3);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_3;
  function void setBitsImpl_io_psumIn_3;
    input bit [$bits(dut.io_psumIn_3)-1:0] value_io_psumIn_3;
    io_psumIn_3 = value_io_psumIn_3;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_3;
  function void getBitsImpl_io_psumIn_3;
    output bit [$bits(dut.io_psumIn_3)-1:0] value_io_psumIn_3;
    value_io_psumIn_3 = io_psumIn_3;
  endfunction

  // Port 2d: io_psumIn_4
  export "DPI-C" function getBitWidthImpl_io_psumIn_4;
  function void getBitWidthImpl_io_psumIn_4;
    output int value;
    value = $bits(dut.io_psumIn_4);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_4;
  function void setBitsImpl_io_psumIn_4;
    input bit [$bits(dut.io_psumIn_4)-1:0] value_io_psumIn_4;
    io_psumIn_4 = value_io_psumIn_4;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_4;
  function void getBitsImpl_io_psumIn_4;
    output bit [$bits(dut.io_psumIn_4)-1:0] value_io_psumIn_4;
    value_io_psumIn_4 = io_psumIn_4;
  endfunction

  // Port 2e: io_psumIn_5
  export "DPI-C" function getBitWidthImpl_io_psumIn_5;
  function void getBitWidthImpl_io_psumIn_5;
    output int value;
    value = $bits(dut.io_psumIn_5);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_5;
  function void setBitsImpl_io_psumIn_5;
    input bit [$bits(dut.io_psumIn_5)-1:0] value_io_psumIn_5;
    io_psumIn_5 = value_io_psumIn_5;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_5;
  function void getBitsImpl_io_psumIn_5;
    output bit [$bits(dut.io_psumIn_5)-1:0] value_io_psumIn_5;
    value_io_psumIn_5 = io_psumIn_5;
  endfunction

  // Port 2f: io_psumIn_6
  export "DPI-C" function getBitWidthImpl_io_psumIn_6;
  function void getBitWidthImpl_io_psumIn_6;
    output int value;
    value = $bits(dut.io_psumIn_6);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_6;
  function void setBitsImpl_io_psumIn_6;
    input bit [$bits(dut.io_psumIn_6)-1:0] value_io_psumIn_6;
    io_psumIn_6 = value_io_psumIn_6;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_6;
  function void getBitsImpl_io_psumIn_6;
    output bit [$bits(dut.io_psumIn_6)-1:0] value_io_psumIn_6;
    value_io_psumIn_6 = io_psumIn_6;
  endfunction

  // Port 30: io_psumIn_7
  export "DPI-C" function getBitWidthImpl_io_psumIn_7;
  function void getBitWidthImpl_io_psumIn_7;
    output int value;
    value = $bits(dut.io_psumIn_7);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_7;
  function void setBitsImpl_io_psumIn_7;
    input bit [$bits(dut.io_psumIn_7)-1:0] value_io_psumIn_7;
    io_psumIn_7 = value_io_psumIn_7;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_7;
  function void getBitsImpl_io_psumIn_7;
    output bit [$bits(dut.io_psumIn_7)-1:0] value_io_psumIn_7;
    value_io_psumIn_7 = io_psumIn_7;
  endfunction

  // Port 31: io_psumIn_8
  export "DPI-C" function getBitWidthImpl_io_psumIn_8;
  function void getBitWidthImpl_io_psumIn_8;
    output int value;
    value = $bits(dut.io_psumIn_8);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_8;
  function void setBitsImpl_io_psumIn_8;
    input bit [$bits(dut.io_psumIn_8)-1:0] value_io_psumIn_8;
    io_psumIn_8 = value_io_psumIn_8;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_8;
  function void getBitsImpl_io_psumIn_8;
    output bit [$bits(dut.io_psumIn_8)-1:0] value_io_psumIn_8;
    value_io_psumIn_8 = io_psumIn_8;
  endfunction

  // Port 32: io_psumIn_9
  export "DPI-C" function getBitWidthImpl_io_psumIn_9;
  function void getBitWidthImpl_io_psumIn_9;
    output int value;
    value = $bits(dut.io_psumIn_9);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_9;
  function void setBitsImpl_io_psumIn_9;
    input bit [$bits(dut.io_psumIn_9)-1:0] value_io_psumIn_9;
    io_psumIn_9 = value_io_psumIn_9;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_9;
  function void getBitsImpl_io_psumIn_9;
    output bit [$bits(dut.io_psumIn_9)-1:0] value_io_psumIn_9;
    value_io_psumIn_9 = io_psumIn_9;
  endfunction

  // Port 33: io_psumIn_10
  export "DPI-C" function getBitWidthImpl_io_psumIn_10;
  function void getBitWidthImpl_io_psumIn_10;
    output int value;
    value = $bits(dut.io_psumIn_10);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_10;
  function void setBitsImpl_io_psumIn_10;
    input bit [$bits(dut.io_psumIn_10)-1:0] value_io_psumIn_10;
    io_psumIn_10 = value_io_psumIn_10;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_10;
  function void getBitsImpl_io_psumIn_10;
    output bit [$bits(dut.io_psumIn_10)-1:0] value_io_psumIn_10;
    value_io_psumIn_10 = io_psumIn_10;
  endfunction

  // Port 34: io_psumIn_11
  export "DPI-C" function getBitWidthImpl_io_psumIn_11;
  function void getBitWidthImpl_io_psumIn_11;
    output int value;
    value = $bits(dut.io_psumIn_11);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_11;
  function void setBitsImpl_io_psumIn_11;
    input bit [$bits(dut.io_psumIn_11)-1:0] value_io_psumIn_11;
    io_psumIn_11 = value_io_psumIn_11;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_11;
  function void getBitsImpl_io_psumIn_11;
    output bit [$bits(dut.io_psumIn_11)-1:0] value_io_psumIn_11;
    value_io_psumIn_11 = io_psumIn_11;
  endfunction

  // Port 35: io_psumIn_12
  export "DPI-C" function getBitWidthImpl_io_psumIn_12;
  function void getBitWidthImpl_io_psumIn_12;
    output int value;
    value = $bits(dut.io_psumIn_12);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_12;
  function void setBitsImpl_io_psumIn_12;
    input bit [$bits(dut.io_psumIn_12)-1:0] value_io_psumIn_12;
    io_psumIn_12 = value_io_psumIn_12;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_12;
  function void getBitsImpl_io_psumIn_12;
    output bit [$bits(dut.io_psumIn_12)-1:0] value_io_psumIn_12;
    value_io_psumIn_12 = io_psumIn_12;
  endfunction

  // Port 36: io_psumIn_13
  export "DPI-C" function getBitWidthImpl_io_psumIn_13;
  function void getBitWidthImpl_io_psumIn_13;
    output int value;
    value = $bits(dut.io_psumIn_13);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_13;
  function void setBitsImpl_io_psumIn_13;
    input bit [$bits(dut.io_psumIn_13)-1:0] value_io_psumIn_13;
    io_psumIn_13 = value_io_psumIn_13;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_13;
  function void getBitsImpl_io_psumIn_13;
    output bit [$bits(dut.io_psumIn_13)-1:0] value_io_psumIn_13;
    value_io_psumIn_13 = io_psumIn_13;
  endfunction

  // Port 37: io_psumIn_14
  export "DPI-C" function getBitWidthImpl_io_psumIn_14;
  function void getBitWidthImpl_io_psumIn_14;
    output int value;
    value = $bits(dut.io_psumIn_14);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_14;
  function void setBitsImpl_io_psumIn_14;
    input bit [$bits(dut.io_psumIn_14)-1:0] value_io_psumIn_14;
    io_psumIn_14 = value_io_psumIn_14;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_14;
  function void getBitsImpl_io_psumIn_14;
    output bit [$bits(dut.io_psumIn_14)-1:0] value_io_psumIn_14;
    value_io_psumIn_14 = io_psumIn_14;
  endfunction

  // Port 38: io_psumIn_15
  export "DPI-C" function getBitWidthImpl_io_psumIn_15;
  function void getBitWidthImpl_io_psumIn_15;
    output int value;
    value = $bits(dut.io_psumIn_15);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn_15;
  function void setBitsImpl_io_psumIn_15;
    input bit [$bits(dut.io_psumIn_15)-1:0] value_io_psumIn_15;
    io_psumIn_15 = value_io_psumIn_15;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn_15;
  function void getBitsImpl_io_psumIn_15;
    output bit [$bits(dut.io_psumIn_15)-1:0] value_io_psumIn_15;
    value_io_psumIn_15 = io_psumIn_15;
  endfunction

  // Port 39: io_aOut_0
  export "DPI-C" function getBitWidthImpl_io_aOut_0;
  function void getBitWidthImpl_io_aOut_0;
    output int value;
    value = $bits(dut.io_aOut_0);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_0;
  function void getBitsImpl_io_aOut_0;
    output bit [$bits(dut.io_aOut_0)-1:0] value_io_aOut_0;
    value_io_aOut_0 = io_aOut_0;
  endfunction

  // Port 3a: io_aOut_1
  export "DPI-C" function getBitWidthImpl_io_aOut_1;
  function void getBitWidthImpl_io_aOut_1;
    output int value;
    value = $bits(dut.io_aOut_1);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_1;
  function void getBitsImpl_io_aOut_1;
    output bit [$bits(dut.io_aOut_1)-1:0] value_io_aOut_1;
    value_io_aOut_1 = io_aOut_1;
  endfunction

  // Port 3b: io_aOut_2
  export "DPI-C" function getBitWidthImpl_io_aOut_2;
  function void getBitWidthImpl_io_aOut_2;
    output int value;
    value = $bits(dut.io_aOut_2);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_2;
  function void getBitsImpl_io_aOut_2;
    output bit [$bits(dut.io_aOut_2)-1:0] value_io_aOut_2;
    value_io_aOut_2 = io_aOut_2;
  endfunction

  // Port 3c: io_aOut_3
  export "DPI-C" function getBitWidthImpl_io_aOut_3;
  function void getBitWidthImpl_io_aOut_3;
    output int value;
    value = $bits(dut.io_aOut_3);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_3;
  function void getBitsImpl_io_aOut_3;
    output bit [$bits(dut.io_aOut_3)-1:0] value_io_aOut_3;
    value_io_aOut_3 = io_aOut_3;
  endfunction

  // Port 3d: io_aOut_4
  export "DPI-C" function getBitWidthImpl_io_aOut_4;
  function void getBitWidthImpl_io_aOut_4;
    output int value;
    value = $bits(dut.io_aOut_4);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_4;
  function void getBitsImpl_io_aOut_4;
    output bit [$bits(dut.io_aOut_4)-1:0] value_io_aOut_4;
    value_io_aOut_4 = io_aOut_4;
  endfunction

  // Port 3e: io_aOut_5
  export "DPI-C" function getBitWidthImpl_io_aOut_5;
  function void getBitWidthImpl_io_aOut_5;
    output int value;
    value = $bits(dut.io_aOut_5);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_5;
  function void getBitsImpl_io_aOut_5;
    output bit [$bits(dut.io_aOut_5)-1:0] value_io_aOut_5;
    value_io_aOut_5 = io_aOut_5;
  endfunction

  // Port 3f: io_aOut_6
  export "DPI-C" function getBitWidthImpl_io_aOut_6;
  function void getBitWidthImpl_io_aOut_6;
    output int value;
    value = $bits(dut.io_aOut_6);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_6;
  function void getBitsImpl_io_aOut_6;
    output bit [$bits(dut.io_aOut_6)-1:0] value_io_aOut_6;
    value_io_aOut_6 = io_aOut_6;
  endfunction

  // Port 40: io_aOut_7
  export "DPI-C" function getBitWidthImpl_io_aOut_7;
  function void getBitWidthImpl_io_aOut_7;
    output int value;
    value = $bits(dut.io_aOut_7);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_7;
  function void getBitsImpl_io_aOut_7;
    output bit [$bits(dut.io_aOut_7)-1:0] value_io_aOut_7;
    value_io_aOut_7 = io_aOut_7;
  endfunction

  // Port 41: io_aOut_8
  export "DPI-C" function getBitWidthImpl_io_aOut_8;
  function void getBitWidthImpl_io_aOut_8;
    output int value;
    value = $bits(dut.io_aOut_8);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_8;
  function void getBitsImpl_io_aOut_8;
    output bit [$bits(dut.io_aOut_8)-1:0] value_io_aOut_8;
    value_io_aOut_8 = io_aOut_8;
  endfunction

  // Port 42: io_aOut_9
  export "DPI-C" function getBitWidthImpl_io_aOut_9;
  function void getBitWidthImpl_io_aOut_9;
    output int value;
    value = $bits(dut.io_aOut_9);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_9;
  function void getBitsImpl_io_aOut_9;
    output bit [$bits(dut.io_aOut_9)-1:0] value_io_aOut_9;
    value_io_aOut_9 = io_aOut_9;
  endfunction

  // Port 43: io_aOut_10
  export "DPI-C" function getBitWidthImpl_io_aOut_10;
  function void getBitWidthImpl_io_aOut_10;
    output int value;
    value = $bits(dut.io_aOut_10);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_10;
  function void getBitsImpl_io_aOut_10;
    output bit [$bits(dut.io_aOut_10)-1:0] value_io_aOut_10;
    value_io_aOut_10 = io_aOut_10;
  endfunction

  // Port 44: io_aOut_11
  export "DPI-C" function getBitWidthImpl_io_aOut_11;
  function void getBitWidthImpl_io_aOut_11;
    output int value;
    value = $bits(dut.io_aOut_11);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_11;
  function void getBitsImpl_io_aOut_11;
    output bit [$bits(dut.io_aOut_11)-1:0] value_io_aOut_11;
    value_io_aOut_11 = io_aOut_11;
  endfunction

  // Port 45: io_aOut_12
  export "DPI-C" function getBitWidthImpl_io_aOut_12;
  function void getBitWidthImpl_io_aOut_12;
    output int value;
    value = $bits(dut.io_aOut_12);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_12;
  function void getBitsImpl_io_aOut_12;
    output bit [$bits(dut.io_aOut_12)-1:0] value_io_aOut_12;
    value_io_aOut_12 = io_aOut_12;
  endfunction

  // Port 46: io_aOut_13
  export "DPI-C" function getBitWidthImpl_io_aOut_13;
  function void getBitWidthImpl_io_aOut_13;
    output int value;
    value = $bits(dut.io_aOut_13);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_13;
  function void getBitsImpl_io_aOut_13;
    output bit [$bits(dut.io_aOut_13)-1:0] value_io_aOut_13;
    value_io_aOut_13 = io_aOut_13;
  endfunction

  // Port 47: io_aOut_14
  export "DPI-C" function getBitWidthImpl_io_aOut_14;
  function void getBitWidthImpl_io_aOut_14;
    output int value;
    value = $bits(dut.io_aOut_14);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_14;
  function void getBitsImpl_io_aOut_14;
    output bit [$bits(dut.io_aOut_14)-1:0] value_io_aOut_14;
    value_io_aOut_14 = io_aOut_14;
  endfunction

  // Port 48: io_aOut_15
  export "DPI-C" function getBitWidthImpl_io_aOut_15;
  function void getBitWidthImpl_io_aOut_15;
    output int value;
    value = $bits(dut.io_aOut_15);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut_15;
  function void getBitsImpl_io_aOut_15;
    output bit [$bits(dut.io_aOut_15)-1:0] value_io_aOut_15;
    value_io_aOut_15 = io_aOut_15;
  endfunction

  // Port 49: io_aIn_0
  export "DPI-C" function getBitWidthImpl_io_aIn_0;
  function void getBitWidthImpl_io_aIn_0;
    output int value;
    value = $bits(dut.io_aIn_0);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_0;
  function void setBitsImpl_io_aIn_0;
    input bit [$bits(dut.io_aIn_0)-1:0] value_io_aIn_0;
    io_aIn_0 = value_io_aIn_0;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_0;
  function void getBitsImpl_io_aIn_0;
    output bit [$bits(dut.io_aIn_0)-1:0] value_io_aIn_0;
    value_io_aIn_0 = io_aIn_0;
  endfunction

  // Port 4a: io_aIn_1
  export "DPI-C" function getBitWidthImpl_io_aIn_1;
  function void getBitWidthImpl_io_aIn_1;
    output int value;
    value = $bits(dut.io_aIn_1);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_1;
  function void setBitsImpl_io_aIn_1;
    input bit [$bits(dut.io_aIn_1)-1:0] value_io_aIn_1;
    io_aIn_1 = value_io_aIn_1;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_1;
  function void getBitsImpl_io_aIn_1;
    output bit [$bits(dut.io_aIn_1)-1:0] value_io_aIn_1;
    value_io_aIn_1 = io_aIn_1;
  endfunction

  // Port 4b: io_aIn_2
  export "DPI-C" function getBitWidthImpl_io_aIn_2;
  function void getBitWidthImpl_io_aIn_2;
    output int value;
    value = $bits(dut.io_aIn_2);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_2;
  function void setBitsImpl_io_aIn_2;
    input bit [$bits(dut.io_aIn_2)-1:0] value_io_aIn_2;
    io_aIn_2 = value_io_aIn_2;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_2;
  function void getBitsImpl_io_aIn_2;
    output bit [$bits(dut.io_aIn_2)-1:0] value_io_aIn_2;
    value_io_aIn_2 = io_aIn_2;
  endfunction

  // Port 4c: io_aIn_3
  export "DPI-C" function getBitWidthImpl_io_aIn_3;
  function void getBitWidthImpl_io_aIn_3;
    output int value;
    value = $bits(dut.io_aIn_3);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_3;
  function void setBitsImpl_io_aIn_3;
    input bit [$bits(dut.io_aIn_3)-1:0] value_io_aIn_3;
    io_aIn_3 = value_io_aIn_3;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_3;
  function void getBitsImpl_io_aIn_3;
    output bit [$bits(dut.io_aIn_3)-1:0] value_io_aIn_3;
    value_io_aIn_3 = io_aIn_3;
  endfunction

  // Port 4d: io_aIn_4
  export "DPI-C" function getBitWidthImpl_io_aIn_4;
  function void getBitWidthImpl_io_aIn_4;
    output int value;
    value = $bits(dut.io_aIn_4);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_4;
  function void setBitsImpl_io_aIn_4;
    input bit [$bits(dut.io_aIn_4)-1:0] value_io_aIn_4;
    io_aIn_4 = value_io_aIn_4;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_4;
  function void getBitsImpl_io_aIn_4;
    output bit [$bits(dut.io_aIn_4)-1:0] value_io_aIn_4;
    value_io_aIn_4 = io_aIn_4;
  endfunction

  // Port 4e: io_aIn_5
  export "DPI-C" function getBitWidthImpl_io_aIn_5;
  function void getBitWidthImpl_io_aIn_5;
    output int value;
    value = $bits(dut.io_aIn_5);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_5;
  function void setBitsImpl_io_aIn_5;
    input bit [$bits(dut.io_aIn_5)-1:0] value_io_aIn_5;
    io_aIn_5 = value_io_aIn_5;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_5;
  function void getBitsImpl_io_aIn_5;
    output bit [$bits(dut.io_aIn_5)-1:0] value_io_aIn_5;
    value_io_aIn_5 = io_aIn_5;
  endfunction

  // Port 4f: io_aIn_6
  export "DPI-C" function getBitWidthImpl_io_aIn_6;
  function void getBitWidthImpl_io_aIn_6;
    output int value;
    value = $bits(dut.io_aIn_6);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_6;
  function void setBitsImpl_io_aIn_6;
    input bit [$bits(dut.io_aIn_6)-1:0] value_io_aIn_6;
    io_aIn_6 = value_io_aIn_6;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_6;
  function void getBitsImpl_io_aIn_6;
    output bit [$bits(dut.io_aIn_6)-1:0] value_io_aIn_6;
    value_io_aIn_6 = io_aIn_6;
  endfunction

  // Port 50: io_aIn_7
  export "DPI-C" function getBitWidthImpl_io_aIn_7;
  function void getBitWidthImpl_io_aIn_7;
    output int value;
    value = $bits(dut.io_aIn_7);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_7;
  function void setBitsImpl_io_aIn_7;
    input bit [$bits(dut.io_aIn_7)-1:0] value_io_aIn_7;
    io_aIn_7 = value_io_aIn_7;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_7;
  function void getBitsImpl_io_aIn_7;
    output bit [$bits(dut.io_aIn_7)-1:0] value_io_aIn_7;
    value_io_aIn_7 = io_aIn_7;
  endfunction

  // Port 51: io_aIn_8
  export "DPI-C" function getBitWidthImpl_io_aIn_8;
  function void getBitWidthImpl_io_aIn_8;
    output int value;
    value = $bits(dut.io_aIn_8);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_8;
  function void setBitsImpl_io_aIn_8;
    input bit [$bits(dut.io_aIn_8)-1:0] value_io_aIn_8;
    io_aIn_8 = value_io_aIn_8;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_8;
  function void getBitsImpl_io_aIn_8;
    output bit [$bits(dut.io_aIn_8)-1:0] value_io_aIn_8;
    value_io_aIn_8 = io_aIn_8;
  endfunction

  // Port 52: io_aIn_9
  export "DPI-C" function getBitWidthImpl_io_aIn_9;
  function void getBitWidthImpl_io_aIn_9;
    output int value;
    value = $bits(dut.io_aIn_9);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_9;
  function void setBitsImpl_io_aIn_9;
    input bit [$bits(dut.io_aIn_9)-1:0] value_io_aIn_9;
    io_aIn_9 = value_io_aIn_9;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_9;
  function void getBitsImpl_io_aIn_9;
    output bit [$bits(dut.io_aIn_9)-1:0] value_io_aIn_9;
    value_io_aIn_9 = io_aIn_9;
  endfunction

  // Port 53: io_aIn_10
  export "DPI-C" function getBitWidthImpl_io_aIn_10;
  function void getBitWidthImpl_io_aIn_10;
    output int value;
    value = $bits(dut.io_aIn_10);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_10;
  function void setBitsImpl_io_aIn_10;
    input bit [$bits(dut.io_aIn_10)-1:0] value_io_aIn_10;
    io_aIn_10 = value_io_aIn_10;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_10;
  function void getBitsImpl_io_aIn_10;
    output bit [$bits(dut.io_aIn_10)-1:0] value_io_aIn_10;
    value_io_aIn_10 = io_aIn_10;
  endfunction

  // Port 54: io_aIn_11
  export "DPI-C" function getBitWidthImpl_io_aIn_11;
  function void getBitWidthImpl_io_aIn_11;
    output int value;
    value = $bits(dut.io_aIn_11);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_11;
  function void setBitsImpl_io_aIn_11;
    input bit [$bits(dut.io_aIn_11)-1:0] value_io_aIn_11;
    io_aIn_11 = value_io_aIn_11;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_11;
  function void getBitsImpl_io_aIn_11;
    output bit [$bits(dut.io_aIn_11)-1:0] value_io_aIn_11;
    value_io_aIn_11 = io_aIn_11;
  endfunction

  // Port 55: io_aIn_12
  export "DPI-C" function getBitWidthImpl_io_aIn_12;
  function void getBitWidthImpl_io_aIn_12;
    output int value;
    value = $bits(dut.io_aIn_12);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_12;
  function void setBitsImpl_io_aIn_12;
    input bit [$bits(dut.io_aIn_12)-1:0] value_io_aIn_12;
    io_aIn_12 = value_io_aIn_12;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_12;
  function void getBitsImpl_io_aIn_12;
    output bit [$bits(dut.io_aIn_12)-1:0] value_io_aIn_12;
    value_io_aIn_12 = io_aIn_12;
  endfunction

  // Port 56: io_aIn_13
  export "DPI-C" function getBitWidthImpl_io_aIn_13;
  function void getBitWidthImpl_io_aIn_13;
    output int value;
    value = $bits(dut.io_aIn_13);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_13;
  function void setBitsImpl_io_aIn_13;
    input bit [$bits(dut.io_aIn_13)-1:0] value_io_aIn_13;
    io_aIn_13 = value_io_aIn_13;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_13;
  function void getBitsImpl_io_aIn_13;
    output bit [$bits(dut.io_aIn_13)-1:0] value_io_aIn_13;
    value_io_aIn_13 = io_aIn_13;
  endfunction

  // Port 57: io_aIn_14
  export "DPI-C" function getBitWidthImpl_io_aIn_14;
  function void getBitWidthImpl_io_aIn_14;
    output int value;
    value = $bits(dut.io_aIn_14);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_14;
  function void setBitsImpl_io_aIn_14;
    input bit [$bits(dut.io_aIn_14)-1:0] value_io_aIn_14;
    io_aIn_14 = value_io_aIn_14;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_14;
  function void getBitsImpl_io_aIn_14;
    output bit [$bits(dut.io_aIn_14)-1:0] value_io_aIn_14;
    value_io_aIn_14 = io_aIn_14;
  endfunction

  // Port 58: io_aIn_15
  export "DPI-C" function getBitWidthImpl_io_aIn_15;
  function void getBitWidthImpl_io_aIn_15;
    output int value;
    value = $bits(dut.io_aIn_15);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn_15;
  function void setBitsImpl_io_aIn_15;
    input bit [$bits(dut.io_aIn_15)-1:0] value_io_aIn_15;
    io_aIn_15 = value_io_aIn_15;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn_15;
  function void getBitsImpl_io_aIn_15;
    output bit [$bits(dut.io_aIn_15)-1:0] value_io_aIn_15;
    value_io_aIn_15 = io_aIn_15;
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
