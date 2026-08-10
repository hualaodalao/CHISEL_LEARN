// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Symbol table internal header
//
// Internal details; most calling programs do not need this header,
// unless using verilator public meta comments.

#ifndef VERILATED_VSVSIMTESTBENCH__SYMS_H_
#define VERILATED_VSVSIMTESTBENCH__SYMS_H_  // guard

#include "verilated.h"

// INCLUDE MODEL CLASS

#include "VsvsimTestbench.h"

// INCLUDE MODULE CLASSES
#include "VsvsimTestbench___024root.h"

// DPI TYPES for DPI Export callbacks (Internal use)
using VsvsimTestbench__Vcb_getBitWidthImpl_clock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_clear_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_loadHIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVLock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_psumOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_rndIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_rndOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitWidthImpl_reset_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value);
using VsvsimTestbench__Vcb_getBitsImpl_clock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_clock);
using VsvsimTestbench__Vcb_getBitsImpl_io_aIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_aOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut);
using VsvsimTestbench__Vcb_getBitsImpl_io_clear_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_clear);
using VsvsimTestbench__Vcb_getBitsImpl_io_fmtIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ &value_io_fmtIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_fmtOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ &value_io_fmtOut);
using VsvsimTestbench__Vcb_getBitsImpl_io_loadHIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadHIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_loadVIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadVIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_loadVLock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadVLock);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_psumOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumOut);
using VsvsimTestbench__Vcb_getBitsImpl_io_rndIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ &value_io_rndIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_rndOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ &value_io_rndOut);
using VsvsimTestbench__Vcb_getBitsImpl_io_validIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validIn);
using VsvsimTestbench__Vcb_getBitsImpl_io_validOut_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut);
using VsvsimTestbench__Vcb_getBitsImpl_reset_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_reset);
using VsvsimTestbench__Vcb_setBitsImpl_clock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_clock);
using VsvsimTestbench__Vcb_setBitsImpl_io_aIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_clear_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_clear);
using VsvsimTestbench__Vcb_setBitsImpl_io_fmtIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ value_io_fmtIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_loadHIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadHIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_loadVIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadVIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_loadVLock_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadVLock);
using VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_rndIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ value_io_rndIn);
using VsvsimTestbench__Vcb_setBitsImpl_io_validIn_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_validIn);
using VsvsimTestbench__Vcb_setBitsImpl_reset_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_reset);
using VsvsimTestbench__Vcb_simulation_disableTrace_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &success);
using VsvsimTestbench__Vcb_simulation_enableTrace_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &success);
using VsvsimTestbench__Vcb_simulation_initializeTrace_t = void (*) (VsvsimTestbench__Syms* __restrict vlSymsp, std::string traceFilePath);

// SYMS CLASS (contains all model state)
class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench__Syms final : public VerilatedSyms {
  public:
    // INTERNAL STATE
    VsvsimTestbench* const __Vm_modelp;
    VlDeleter __Vm_deleter;
    bool __Vm_didInit = false;

    // MODULE INSTANCE STATE
    VsvsimTestbench___024root      TOP;

    // SCOPE NAMES
    VerilatedScope* __Vscopep_svsimTestbench;

    // CONSTRUCTORS
    VsvsimTestbench__Syms(VerilatedContext* contextp, const char* namep, VsvsimTestbench* modelp);
    ~VsvsimTestbench__Syms();

    // METHODS
    const char* name() const { return TOP.vlNamep; }
};

#endif  // guard
