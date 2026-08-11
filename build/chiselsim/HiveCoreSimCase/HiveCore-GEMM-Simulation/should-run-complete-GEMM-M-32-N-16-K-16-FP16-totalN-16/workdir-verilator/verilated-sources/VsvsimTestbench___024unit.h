// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VsvsimTestbench.h for the primary calling header

#ifndef VERILATED_VSVSIMTESTBENCH___024UNIT_H_
#define VERILATED_VSVSIMTESTBENCH___024UNIT_H_  // guard

#include "verilated.h"


class VsvsimTestbench__Syms;

class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench___024unit final {
  public:

    // INTERNAL VARIABLES
    VsvsimTestbench__Syms* vlSymsp;
    const char* vlNamep;

    // CONSTRUCTORS
    VsvsimTestbench___024unit();
    ~VsvsimTestbench___024unit();
    void ctor(VsvsimTestbench__Syms* symsp, const char* namep);
    void dtor();
    VL_UNCOPYABLE(VsvsimTestbench___024unit);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
