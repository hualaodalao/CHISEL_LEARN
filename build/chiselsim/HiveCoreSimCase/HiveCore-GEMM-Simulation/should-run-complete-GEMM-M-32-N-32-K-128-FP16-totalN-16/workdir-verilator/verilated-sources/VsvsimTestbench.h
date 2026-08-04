// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Primary model header
//
// This header should be included by all source files instantiating the design.
// The class here is then constructed to instantiate the design.
// See the Verilator manual for examples.

#ifndef VERILATED_VSVSIMTESTBENCH_H_
#define VERILATED_VSVSIMTESTBENCH_H_  // guard

#include "verilated.h"
#include "svdpi.h"

class VsvsimTestbench__Syms;
class VsvsimTestbench___024root;
class VerilatedVcdC;
class VsvsimTestbench_HiveCell;


// This class is the main interface to the Verilated model
class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench VL_NOT_FINAL : public VerilatedModel {
  private:
    // Symbol table holding complete model state (owned by this class)
    VsvsimTestbench__Syms* const vlSymsp;

  public:

    // CONSTEXPR CAPABILITIES
    // Verilated with --trace?
    static constexpr bool traceCapable = true;

    // PORTS
    // The application code writes and reads these signals to
    // propagate new values into/out from the Verilated model.

    // CELLS
    // Public to allow access to /* verilator public */ items.
    // Otherwise the application code can consider these internals.
    VsvsimTestbench_HiveCell* const __PVT__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_0;
    VsvsimTestbench_HiveCell* const __PVT__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_0_1;
    VsvsimTestbench_HiveCell* const __PVT__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_0;
    VsvsimTestbench_HiveCell* const __PVT__svsimTestbench__DOT__dut__DOT__hiveComb__DOT__arrays_1_1;

    // Root instance pointer to allow access to model internals,
    // including inlined /* verilator public_flat_* */ items.
    VsvsimTestbench___024root* const rootp;

    // CONSTRUCTORS
    /// Construct the model; called by application code
    /// If contextp is null, then the model will use the default global context
    /// If name is "", then makes a wrapper with a
    /// single model invisible with respect to DPI scope names.
    explicit VsvsimTestbench(VerilatedContext* contextp, const char* name = "TOP");
    explicit VsvsimTestbench(const char* name = "TOP");
    /// Destroy the model; called (often implicitly) by application code
    virtual ~VsvsimTestbench();
  private:
    VL_UNCOPYABLE(VsvsimTestbench);  ///< Copying not allowed

  public:
    // API METHODS
    /// Evaluate the model.  Application must call when inputs change.
    void eval() { eval_step(); eval_end_step(); }
    /// Evaluate when calling multiple units/models per time step.
    void eval_step();
    /// Evaluate at end of a timestep for tracing, when using eval_step().
    /// Application must call after all eval() and before time changes.
    void eval_end_step();
    /// Simulation complete, run final blocks.  Application must call on completion.
    void final();
    /// Are there scheduled events to handle?
    bool eventsPending();
    /// Returns time at next time slot. Aborts if !eventsPending()
    uint64_t nextTimeSlot();
    /// Trace signals in the model; called by application code
    void trace(VerilatedTraceBaseC* tfp, int levels, int options = 0) { contextp()->trace(tfp, levels, options); }
    /// Retrieve name of this model instance (as passed to constructor).
    const char* name() const;

    /// DPI Export functions
    static void getBitWidthImpl_clock(int* value);
    static void getBitWidthImpl_io_cmd_payload_data0(int* value);
    static void getBitWidthImpl_io_cmd_payload_data1(int* value);
    static void getBitWidthImpl_io_cmd_payload_op(int* value);
    static void getBitWidthImpl_io_cmd_payload_regAddr(int* value);
    static void getBitWidthImpl_io_cmd_ready(int* value);
    static void getBitWidthImpl_io_cmd_valid(int* value);
    static void getBitWidthImpl_io_dma0Ext_addr(int* value);
    static void getBitWidthImpl_io_dma0Ext_grant(int* value);
    static void getBitWidthImpl_io_dma0Ext_isWrite(int* value);
    static void getBitWidthImpl_io_dma0Ext_len(int* value);
    static void getBitWidthImpl_io_dma0Ext_readData_payload(int* value);
    static void getBitWidthImpl_io_dma0Ext_readData_ready(int* value);
    static void getBitWidthImpl_io_dma0Ext_readData_valid(int* value);
    static void getBitWidthImpl_io_dma0Ext_req(int* value);
    static void getBitWidthImpl_io_dma0Ext_writeData_payload(int* value);
    static void getBitWidthImpl_io_dma0Ext_writeData_ready(int* value);
    static void getBitWidthImpl_io_dma0Ext_writeData_valid(int* value);
    static void getBitWidthImpl_io_dma1Ext_addr(int* value);
    static void getBitWidthImpl_io_dma1Ext_grant(int* value);
    static void getBitWidthImpl_io_dma1Ext_isWrite(int* value);
    static void getBitWidthImpl_io_dma1Ext_len(int* value);
    static void getBitWidthImpl_io_dma1Ext_readData_payload(int* value);
    static void getBitWidthImpl_io_dma1Ext_readData_ready(int* value);
    static void getBitWidthImpl_io_dma1Ext_readData_valid(int* value);
    static void getBitWidthImpl_io_dma1Ext_req(int* value);
    static void getBitWidthImpl_io_dma1Ext_writeData_payload(int* value);
    static void getBitWidthImpl_io_dma1Ext_writeData_ready(int* value);
    static void getBitWidthImpl_io_dma1Ext_writeData_valid(int* value);
    static void getBitWidthImpl_io_resp_payload_data(int* value);
    static void getBitWidthImpl_io_resp_payload_done(int* value);
    static void getBitWidthImpl_io_resp_payload_err(int* value);
    static void getBitWidthImpl_io_resp_ready(int* value);
    static void getBitWidthImpl_io_resp_valid(int* value);
    static void getBitWidthImpl_io_status_aOccupancy(int* value);
    static void getBitWidthImpl_io_status_busy(int* value);
    static void getBitWidthImpl_io_status_cOccupancy(int* value);
    static void getBitWidthImpl_io_status_done(int* value);
    static void getBitWidthImpl_io_status_err(int* value);
    static void getBitWidthImpl_io_status_progress(int* value);
    static void getBitWidthImpl_reset(int* value);
    static void getBitsImpl_clock(svBitVecVal* value_clock);
    static void getBitsImpl_io_cmd_payload_data0(svBitVecVal* value_io_cmd_payload_data0);
    static void getBitsImpl_io_cmd_payload_data1(svBitVecVal* value_io_cmd_payload_data1);
    static void getBitsImpl_io_cmd_payload_op(svBitVecVal* value_io_cmd_payload_op);
    static void getBitsImpl_io_cmd_payload_regAddr(svBitVecVal* value_io_cmd_payload_regAddr);
    static void getBitsImpl_io_cmd_ready(svBitVecVal* value_io_cmd_ready);
    static void getBitsImpl_io_cmd_valid(svBitVecVal* value_io_cmd_valid);
    static void getBitsImpl_io_dma0Ext_addr(svBitVecVal* value_io_dma0Ext_addr);
    static void getBitsImpl_io_dma0Ext_grant(svBitVecVal* value_io_dma0Ext_grant);
    static void getBitsImpl_io_dma0Ext_isWrite(svBitVecVal* value_io_dma0Ext_isWrite);
    static void getBitsImpl_io_dma0Ext_len(svBitVecVal* value_io_dma0Ext_len);
    static void getBitsImpl_io_dma0Ext_readData_payload(svBitVecVal* value_io_dma0Ext_readData_payload);
    static void getBitsImpl_io_dma0Ext_readData_ready(svBitVecVal* value_io_dma0Ext_readData_ready);
    static void getBitsImpl_io_dma0Ext_readData_valid(svBitVecVal* value_io_dma0Ext_readData_valid);
    static void getBitsImpl_io_dma0Ext_req(svBitVecVal* value_io_dma0Ext_req);
    static void getBitsImpl_io_dma0Ext_writeData_payload(svBitVecVal* value_io_dma0Ext_writeData_payload);
    static void getBitsImpl_io_dma0Ext_writeData_ready(svBitVecVal* value_io_dma0Ext_writeData_ready);
    static void getBitsImpl_io_dma0Ext_writeData_valid(svBitVecVal* value_io_dma0Ext_writeData_valid);
    static void getBitsImpl_io_dma1Ext_addr(svBitVecVal* value_io_dma1Ext_addr);
    static void getBitsImpl_io_dma1Ext_grant(svBitVecVal* value_io_dma1Ext_grant);
    static void getBitsImpl_io_dma1Ext_isWrite(svBitVecVal* value_io_dma1Ext_isWrite);
    static void getBitsImpl_io_dma1Ext_len(svBitVecVal* value_io_dma1Ext_len);
    static void getBitsImpl_io_dma1Ext_readData_payload(svBitVecVal* value_io_dma1Ext_readData_payload);
    static void getBitsImpl_io_dma1Ext_readData_ready(svBitVecVal* value_io_dma1Ext_readData_ready);
    static void getBitsImpl_io_dma1Ext_readData_valid(svBitVecVal* value_io_dma1Ext_readData_valid);
    static void getBitsImpl_io_dma1Ext_req(svBitVecVal* value_io_dma1Ext_req);
    static void getBitsImpl_io_dma1Ext_writeData_payload(svBitVecVal* value_io_dma1Ext_writeData_payload);
    static void getBitsImpl_io_dma1Ext_writeData_ready(svBitVecVal* value_io_dma1Ext_writeData_ready);
    static void getBitsImpl_io_dma1Ext_writeData_valid(svBitVecVal* value_io_dma1Ext_writeData_valid);
    static void getBitsImpl_io_resp_payload_data(svBitVecVal* value_io_resp_payload_data);
    static void getBitsImpl_io_resp_payload_done(svBitVecVal* value_io_resp_payload_done);
    static void getBitsImpl_io_resp_payload_err(svBitVecVal* value_io_resp_payload_err);
    static void getBitsImpl_io_resp_ready(svBitVecVal* value_io_resp_ready);
    static void getBitsImpl_io_resp_valid(svBitVecVal* value_io_resp_valid);
    static void getBitsImpl_io_status_aOccupancy(svBitVecVal* value_io_status_aOccupancy);
    static void getBitsImpl_io_status_busy(svBitVecVal* value_io_status_busy);
    static void getBitsImpl_io_status_cOccupancy(svBitVecVal* value_io_status_cOccupancy);
    static void getBitsImpl_io_status_done(svBitVecVal* value_io_status_done);
    static void getBitsImpl_io_status_err(svBitVecVal* value_io_status_err);
    static void getBitsImpl_io_status_progress(svBitVecVal* value_io_status_progress);
    static void getBitsImpl_reset(svBitVecVal* value_reset);
    static void setBitsImpl_clock(const svBitVecVal* value_clock);
    static void setBitsImpl_io_cmd_payload_data0(const svBitVecVal* value_io_cmd_payload_data0);
    static void setBitsImpl_io_cmd_payload_data1(const svBitVecVal* value_io_cmd_payload_data1);
    static void setBitsImpl_io_cmd_payload_op(const svBitVecVal* value_io_cmd_payload_op);
    static void setBitsImpl_io_cmd_payload_regAddr(const svBitVecVal* value_io_cmd_payload_regAddr);
    static void setBitsImpl_io_cmd_valid(const svBitVecVal* value_io_cmd_valid);
    static void setBitsImpl_io_dma0Ext_grant(const svBitVecVal* value_io_dma0Ext_grant);
    static void setBitsImpl_io_dma0Ext_readData_payload(const svBitVecVal* value_io_dma0Ext_readData_payload);
    static void setBitsImpl_io_dma0Ext_readData_valid(const svBitVecVal* value_io_dma0Ext_readData_valid);
    static void setBitsImpl_io_dma0Ext_writeData_ready(const svBitVecVal* value_io_dma0Ext_writeData_ready);
    static void setBitsImpl_io_dma1Ext_grant(const svBitVecVal* value_io_dma1Ext_grant);
    static void setBitsImpl_io_dma1Ext_readData_payload(const svBitVecVal* value_io_dma1Ext_readData_payload);
    static void setBitsImpl_io_dma1Ext_readData_valid(const svBitVecVal* value_io_dma1Ext_readData_valid);
    static void setBitsImpl_io_dma1Ext_writeData_ready(const svBitVecVal* value_io_dma1Ext_writeData_ready);
    static void setBitsImpl_io_resp_ready(const svBitVecVal* value_io_resp_ready);
    static void setBitsImpl_reset(const svBitVecVal* value_reset);
    static void simulation_disableTrace(int* success);
    static void simulation_enableTrace(int* success);
    static void simulation_initializeTrace(const char* traceFilePath);

    // Abstract methods from VerilatedModel
    const char* hierName() const override final;
    const char* modelName() const override final;
    unsigned threads() const override final;
    /// Prepare for cloning the model at the process level (e.g. fork in Linux)
    /// Release necessary resources. Called before cloning.
    void prepareClone() const;
    /// Re-init after cloning the model at the process level (e.g. fork in Linux)
    /// Re-allocate necessary resources. Called after cloning.
    void atClone() const;
    std::unique_ptr<VerilatedTraceConfig> traceConfig() const override final;
  private:
    // Internal functions - trace registration
    void traceBaseModel(VerilatedTraceBaseC* tfp, int levels, int options);
};

#endif  // guard
