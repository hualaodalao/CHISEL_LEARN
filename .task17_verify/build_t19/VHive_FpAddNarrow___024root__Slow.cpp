// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VHive_FpAddNarrow.h for the primary calling header

#include "VHive_FpAddNarrow__pch.h"

void VHive_FpAddNarrow___024root___ctor_var_reset(VHive_FpAddNarrow___024root* vlSelf);

VHive_FpAddNarrow___024root::VHive_FpAddNarrow___024root(VHive_FpAddNarrow__Syms* symsp, const char* namep)
 {
    vlSymsp = symsp;
    vlNamep = strdup(namep);
    // Reset structure values
    VHive_FpAddNarrow___024root___ctor_var_reset(this);
}

void VHive_FpAddNarrow___024root::__Vconfigure(bool first) {
    (void)first;  // Prevent unused variable warning
}

VHive_FpAddNarrow___024root::~VHive_FpAddNarrow___024root() {
    VL_DO_DANGLING(std::free(const_cast<char*>(vlNamep)), vlNamep);
}
