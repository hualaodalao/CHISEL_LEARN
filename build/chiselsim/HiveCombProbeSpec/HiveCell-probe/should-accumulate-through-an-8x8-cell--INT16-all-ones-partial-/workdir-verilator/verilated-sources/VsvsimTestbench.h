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
class VsvsimTestbench_HiveWorker;


// This class is the main interface to the Verilated model
class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench VL_NOT_FINAL : public VerilatedModel {
  private:
    // Symbol table holding complete model state (owned by this class)
    VsvsimTestbench__Syms* const vlSymsp;

  public:

    // CONSTEXPR CAPABILITIES
    // Verilated with --trace?
    static constexpr bool traceCapable = false;

    // PORTS
    // The application code writes and reads these signals to
    // propagate new values into/out from the Verilated model.

    // CELLS
    // Public to allow access to /* verilator public */ items.
    // Otherwise the application code can consider these internals.
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_0_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_1_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_2_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_3_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_4_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_5_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_6_7;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_0;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_1;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_2;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_3;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_4;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_5;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_6;
    VsvsimTestbench_HiveWorker* const __PVT__svsimTestbench__DOT__dut__DOT__pes_7_7;

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
    void eval() { eval_step(); }
    /// Evaluate when calling multiple units/models per time step.
    void eval_step();
    /// Evaluate at end of a timestep for tracing, when using eval_step().
    /// Application must call after all eval() and before time changes.
    void eval_end_step() {}
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
    static void getBitWidthImpl_io_aIn_0(int* value);
    static void getBitWidthImpl_io_aIn_1(int* value);
    static void getBitWidthImpl_io_aIn_2(int* value);
    static void getBitWidthImpl_io_aIn_3(int* value);
    static void getBitWidthImpl_io_aIn_4(int* value);
    static void getBitWidthImpl_io_aIn_5(int* value);
    static void getBitWidthImpl_io_aIn_6(int* value);
    static void getBitWidthImpl_io_aIn_7(int* value);
    static void getBitWidthImpl_io_aOut_0(int* value);
    static void getBitWidthImpl_io_aOut_1(int* value);
    static void getBitWidthImpl_io_aOut_2(int* value);
    static void getBitWidthImpl_io_aOut_3(int* value);
    static void getBitWidthImpl_io_aOut_4(int* value);
    static void getBitWidthImpl_io_aOut_5(int* value);
    static void getBitWidthImpl_io_aOut_6(int* value);
    static void getBitWidthImpl_io_aOut_7(int* value);
    static void getBitWidthImpl_io_cOut_0(int* value);
    static void getBitWidthImpl_io_cOut_1(int* value);
    static void getBitWidthImpl_io_cOut_2(int* value);
    static void getBitWidthImpl_io_cOut_3(int* value);
    static void getBitWidthImpl_io_cOut_4(int* value);
    static void getBitWidthImpl_io_cOut_5(int* value);
    static void getBitWidthImpl_io_cOut_6(int* value);
    static void getBitWidthImpl_io_cOut_7(int* value);
    static void getBitWidthImpl_io_clear(int* value);
    static void getBitWidthImpl_io_fmtIn(int* value);
    static void getBitWidthImpl_io_fmtOut(int* value);
    static void getBitWidthImpl_io_loadHIn(int* value);
    static void getBitWidthImpl_io_loadVIn(int* value);
    static void getBitWidthImpl_io_loadVLock(int* value);
    static void getBitWidthImpl_io_psumIn_0(int* value);
    static void getBitWidthImpl_io_psumIn_1(int* value);
    static void getBitWidthImpl_io_psumIn_2(int* value);
    static void getBitWidthImpl_io_psumIn_3(int* value);
    static void getBitWidthImpl_io_psumIn_4(int* value);
    static void getBitWidthImpl_io_psumIn_5(int* value);
    static void getBitWidthImpl_io_psumIn_6(int* value);
    static void getBitWidthImpl_io_psumIn_7(int* value);
    static void getBitWidthImpl_io_rndIn(int* value);
    static void getBitWidthImpl_io_rndOut(int* value);
    static void getBitWidthImpl_io_validIn(int* value);
    static void getBitWidthImpl_io_validOut_0(int* value);
    static void getBitWidthImpl_io_validOut_1(int* value);
    static void getBitWidthImpl_io_validOut_2(int* value);
    static void getBitWidthImpl_io_validOut_3(int* value);
    static void getBitWidthImpl_io_validOut_4(int* value);
    static void getBitWidthImpl_io_validOut_5(int* value);
    static void getBitWidthImpl_io_validOut_6(int* value);
    static void getBitWidthImpl_io_validOut_7(int* value);
    static void getBitWidthImpl_reset(int* value);
    static void getBitsImpl_clock(svBitVecVal* value_clock);
    static void getBitsImpl_io_aIn_0(svBitVecVal* value_io_aIn_0);
    static void getBitsImpl_io_aIn_1(svBitVecVal* value_io_aIn_1);
    static void getBitsImpl_io_aIn_2(svBitVecVal* value_io_aIn_2);
    static void getBitsImpl_io_aIn_3(svBitVecVal* value_io_aIn_3);
    static void getBitsImpl_io_aIn_4(svBitVecVal* value_io_aIn_4);
    static void getBitsImpl_io_aIn_5(svBitVecVal* value_io_aIn_5);
    static void getBitsImpl_io_aIn_6(svBitVecVal* value_io_aIn_6);
    static void getBitsImpl_io_aIn_7(svBitVecVal* value_io_aIn_7);
    static void getBitsImpl_io_aOut_0(svBitVecVal* value_io_aOut_0);
    static void getBitsImpl_io_aOut_1(svBitVecVal* value_io_aOut_1);
    static void getBitsImpl_io_aOut_2(svBitVecVal* value_io_aOut_2);
    static void getBitsImpl_io_aOut_3(svBitVecVal* value_io_aOut_3);
    static void getBitsImpl_io_aOut_4(svBitVecVal* value_io_aOut_4);
    static void getBitsImpl_io_aOut_5(svBitVecVal* value_io_aOut_5);
    static void getBitsImpl_io_aOut_6(svBitVecVal* value_io_aOut_6);
    static void getBitsImpl_io_aOut_7(svBitVecVal* value_io_aOut_7);
    static void getBitsImpl_io_cOut_0(svBitVecVal* value_io_cOut_0);
    static void getBitsImpl_io_cOut_1(svBitVecVal* value_io_cOut_1);
    static void getBitsImpl_io_cOut_2(svBitVecVal* value_io_cOut_2);
    static void getBitsImpl_io_cOut_3(svBitVecVal* value_io_cOut_3);
    static void getBitsImpl_io_cOut_4(svBitVecVal* value_io_cOut_4);
    static void getBitsImpl_io_cOut_5(svBitVecVal* value_io_cOut_5);
    static void getBitsImpl_io_cOut_6(svBitVecVal* value_io_cOut_6);
    static void getBitsImpl_io_cOut_7(svBitVecVal* value_io_cOut_7);
    static void getBitsImpl_io_clear(svBitVecVal* value_io_clear);
    static void getBitsImpl_io_fmtIn(svBitVecVal* value_io_fmtIn);
    static void getBitsImpl_io_fmtOut(svBitVecVal* value_io_fmtOut);
    static void getBitsImpl_io_loadHIn(svBitVecVal* value_io_loadHIn);
    static void getBitsImpl_io_loadVIn(svBitVecVal* value_io_loadVIn);
    static void getBitsImpl_io_loadVLock(svBitVecVal* value_io_loadVLock);
    static void getBitsImpl_io_psumIn_0(svBitVecVal* value_io_psumIn_0);
    static void getBitsImpl_io_psumIn_1(svBitVecVal* value_io_psumIn_1);
    static void getBitsImpl_io_psumIn_2(svBitVecVal* value_io_psumIn_2);
    static void getBitsImpl_io_psumIn_3(svBitVecVal* value_io_psumIn_3);
    static void getBitsImpl_io_psumIn_4(svBitVecVal* value_io_psumIn_4);
    static void getBitsImpl_io_psumIn_5(svBitVecVal* value_io_psumIn_5);
    static void getBitsImpl_io_psumIn_6(svBitVecVal* value_io_psumIn_6);
    static void getBitsImpl_io_psumIn_7(svBitVecVal* value_io_psumIn_7);
    static void getBitsImpl_io_rndIn(svBitVecVal* value_io_rndIn);
    static void getBitsImpl_io_rndOut(svBitVecVal* value_io_rndOut);
    static void getBitsImpl_io_validIn(svBitVecVal* value_io_validIn);
    static void getBitsImpl_io_validOut_0(svBitVecVal* value_io_validOut_0);
    static void getBitsImpl_io_validOut_1(svBitVecVal* value_io_validOut_1);
    static void getBitsImpl_io_validOut_2(svBitVecVal* value_io_validOut_2);
    static void getBitsImpl_io_validOut_3(svBitVecVal* value_io_validOut_3);
    static void getBitsImpl_io_validOut_4(svBitVecVal* value_io_validOut_4);
    static void getBitsImpl_io_validOut_5(svBitVecVal* value_io_validOut_5);
    static void getBitsImpl_io_validOut_6(svBitVecVal* value_io_validOut_6);
    static void getBitsImpl_io_validOut_7(svBitVecVal* value_io_validOut_7);
    static void getBitsImpl_reset(svBitVecVal* value_reset);
    static void setBitsImpl_clock(const svBitVecVal* value_clock);
    static void setBitsImpl_io_aIn_0(const svBitVecVal* value_io_aIn_0);
    static void setBitsImpl_io_aIn_1(const svBitVecVal* value_io_aIn_1);
    static void setBitsImpl_io_aIn_2(const svBitVecVal* value_io_aIn_2);
    static void setBitsImpl_io_aIn_3(const svBitVecVal* value_io_aIn_3);
    static void setBitsImpl_io_aIn_4(const svBitVecVal* value_io_aIn_4);
    static void setBitsImpl_io_aIn_5(const svBitVecVal* value_io_aIn_5);
    static void setBitsImpl_io_aIn_6(const svBitVecVal* value_io_aIn_6);
    static void setBitsImpl_io_aIn_7(const svBitVecVal* value_io_aIn_7);
    static void setBitsImpl_io_clear(const svBitVecVal* value_io_clear);
    static void setBitsImpl_io_fmtIn(const svBitVecVal* value_io_fmtIn);
    static void setBitsImpl_io_loadHIn(const svBitVecVal* value_io_loadHIn);
    static void setBitsImpl_io_loadVIn(const svBitVecVal* value_io_loadVIn);
    static void setBitsImpl_io_loadVLock(const svBitVecVal* value_io_loadVLock);
    static void setBitsImpl_io_psumIn_0(const svBitVecVal* value_io_psumIn_0);
    static void setBitsImpl_io_psumIn_1(const svBitVecVal* value_io_psumIn_1);
    static void setBitsImpl_io_psumIn_2(const svBitVecVal* value_io_psumIn_2);
    static void setBitsImpl_io_psumIn_3(const svBitVecVal* value_io_psumIn_3);
    static void setBitsImpl_io_psumIn_4(const svBitVecVal* value_io_psumIn_4);
    static void setBitsImpl_io_psumIn_5(const svBitVecVal* value_io_psumIn_5);
    static void setBitsImpl_io_psumIn_6(const svBitVecVal* value_io_psumIn_6);
    static void setBitsImpl_io_psumIn_7(const svBitVecVal* value_io_psumIn_7);
    static void setBitsImpl_io_rndIn(const svBitVecVal* value_io_rndIn);
    static void setBitsImpl_io_validIn(const svBitVecVal* value_io_validIn);
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
  private:
    // Internal functions - trace registration
    void traceBaseModel(VerilatedTraceBaseC* tfp, int levels, int options);
};

#endif  // guard
