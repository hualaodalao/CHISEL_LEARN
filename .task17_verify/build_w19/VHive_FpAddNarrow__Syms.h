// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Symbol table internal header
//
// Internal details; most calling programs do not need this header,
// unless using verilator public meta comments.

#ifndef VERILATED_VHIVE_FPADDNARROW__SYMS_H_
#define VERILATED_VHIVE_FPADDNARROW__SYMS_H_  // guard

#include "verilated.h"

// INCLUDE MODEL CLASS

#include "VHive_FpAddNarrow.h"

// INCLUDE MODULE CLASSES
#include "VHive_FpAddNarrow___024root.h"

// SYMS CLASS (contains all model state)
class alignas(VL_CACHE_LINE_BYTES) VHive_FpAddNarrow__Syms final : public VerilatedSyms {
  public:
    // INTERNAL STATE
    VHive_FpAddNarrow* const __Vm_modelp;
    VlDeleter __Vm_deleter;
    bool __Vm_didInit = false;

    // MODULE INSTANCE STATE
    VHive_FpAddNarrow___024root    TOP;

    // CONSTRUCTORS
    VHive_FpAddNarrow__Syms(VerilatedContext* contextp, const char* namep, VHive_FpAddNarrow* modelp);
    ~VHive_FpAddNarrow__Syms();

    // METHODS
    const char* name() const { return TOP.vlNamep; }
};

#endif  // guard
