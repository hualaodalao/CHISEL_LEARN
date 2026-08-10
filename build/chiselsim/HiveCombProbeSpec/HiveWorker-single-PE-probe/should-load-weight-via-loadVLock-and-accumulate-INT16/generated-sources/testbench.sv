module svsimTestbench;
  reg  [$bits(dut.clock)-1:0] clock = '0;
  reg  [$bits(dut.reset)-1:0] reset = '0;
  reg  [$bits(dut.io_clear)-1:0] io_clear = '0;
  wire [$bits(dut.io_rndOut)-1:0] io_rndOut;
  reg  [$bits(dut.io_rndIn)-1:0] io_rndIn = '0;
  wire [$bits(dut.io_fmtOut)-1:0] io_fmtOut;
  reg  [$bits(dut.io_fmtIn)-1:0] io_fmtIn = '0;
  wire [$bits(dut.io_validOut)-1:0] io_validOut;
  reg  [$bits(dut.io_validIn)-1:0] io_validIn = '0;
  reg  [$bits(dut.io_loadVIn)-1:0] io_loadVIn = '0;
  reg  [$bits(dut.io_loadVLock)-1:0] io_loadVLock = '0;
  reg  [$bits(dut.io_loadHIn)-1:0] io_loadHIn = '0;
  wire [$bits(dut.io_psumOut)-1:0] io_psumOut;
  reg  [$bits(dut.io_psumIn)-1:0] io_psumIn = '0;
  wire [$bits(dut.io_aOut)-1:0] io_aOut;
  reg  [$bits(dut.io_aIn)-1:0] io_aIn = '0;

HiveWorker dut (
    .clock(clock),
    .reset(reset),
    .io_clear(io_clear),
    .io_rndOut(io_rndOut),
    .io_rndIn(io_rndIn),
    .io_fmtOut(io_fmtOut),
    .io_fmtIn(io_fmtIn),
    .io_validOut(io_validOut),
    .io_validIn(io_validIn),
    .io_loadVIn(io_loadVIn),
    .io_loadVLock(io_loadVLock),
    .io_loadHIn(io_loadHIn),
    .io_psumOut(io_psumOut),
    .io_psumIn(io_psumIn),
    .io_aOut(io_aOut),
    .io_aIn(io_aIn)
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

  // Port 2: io_clear
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

  // Port 3: io_rndOut
  export "DPI-C" function getBitWidthImpl_io_rndOut;
  function void getBitWidthImpl_io_rndOut;
    output int value;
    value = $bits(dut.io_rndOut);
  endfunction
  export "DPI-C" function getBitsImpl_io_rndOut;
  function void getBitsImpl_io_rndOut;
    output bit [$bits(dut.io_rndOut)-1:0] value_io_rndOut;
    value_io_rndOut = io_rndOut;
  endfunction

  // Port 4: io_rndIn
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

  // Port 5: io_fmtOut
  export "DPI-C" function getBitWidthImpl_io_fmtOut;
  function void getBitWidthImpl_io_fmtOut;
    output int value;
    value = $bits(dut.io_fmtOut);
  endfunction
  export "DPI-C" function getBitsImpl_io_fmtOut;
  function void getBitsImpl_io_fmtOut;
    output bit [$bits(dut.io_fmtOut)-1:0] value_io_fmtOut;
    value_io_fmtOut = io_fmtOut;
  endfunction

  // Port 6: io_fmtIn
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

  // Port 7: io_validOut
  export "DPI-C" function getBitWidthImpl_io_validOut;
  function void getBitWidthImpl_io_validOut;
    output int value;
    value = $bits(dut.io_validOut);
  endfunction
  export "DPI-C" function getBitsImpl_io_validOut;
  function void getBitsImpl_io_validOut;
    output bit [$bits(dut.io_validOut)-1:0] value_io_validOut;
    value_io_validOut = io_validOut;
  endfunction

  // Port 8: io_validIn
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

  // Port 9: io_loadVIn
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

  // Port a: io_loadVLock
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

  // Port b: io_loadHIn
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

  // Port c: io_psumOut
  export "DPI-C" function getBitWidthImpl_io_psumOut;
  function void getBitWidthImpl_io_psumOut;
    output int value;
    value = $bits(dut.io_psumOut);
  endfunction
  export "DPI-C" function getBitsImpl_io_psumOut;
  function void getBitsImpl_io_psumOut;
    output bit [$bits(dut.io_psumOut)-1:0] value_io_psumOut;
    value_io_psumOut = io_psumOut;
  endfunction

  // Port d: io_psumIn
  export "DPI-C" function getBitWidthImpl_io_psumIn;
  function void getBitWidthImpl_io_psumIn;
    output int value;
    value = $bits(dut.io_psumIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_psumIn;
  function void setBitsImpl_io_psumIn;
    input bit [$bits(dut.io_psumIn)-1:0] value_io_psumIn;
    io_psumIn = value_io_psumIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_psumIn;
  function void getBitsImpl_io_psumIn;
    output bit [$bits(dut.io_psumIn)-1:0] value_io_psumIn;
    value_io_psumIn = io_psumIn;
  endfunction

  // Port e: io_aOut
  export "DPI-C" function getBitWidthImpl_io_aOut;
  function void getBitWidthImpl_io_aOut;
    output int value;
    value = $bits(dut.io_aOut);
  endfunction
  export "DPI-C" function getBitsImpl_io_aOut;
  function void getBitsImpl_io_aOut;
    output bit [$bits(dut.io_aOut)-1:0] value_io_aOut;
    value_io_aOut = io_aOut;
  endfunction

  // Port f: io_aIn
  export "DPI-C" function getBitWidthImpl_io_aIn;
  function void getBitWidthImpl_io_aIn;
    output int value;
    value = $bits(dut.io_aIn);
  endfunction
  export "DPI-C" function setBitsImpl_io_aIn;
  function void setBitsImpl_io_aIn;
    input bit [$bits(dut.io_aIn)-1:0] value_io_aIn;
    io_aIn = value_io_aIn;
  endfunction
  export "DPI-C" function getBitsImpl_io_aIn;
  function void getBitsImpl_io_aIn;
    output bit [$bits(dut.io_aIn)-1:0] value_io_aIn;
    value_io_aIn = io_aIn;
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
