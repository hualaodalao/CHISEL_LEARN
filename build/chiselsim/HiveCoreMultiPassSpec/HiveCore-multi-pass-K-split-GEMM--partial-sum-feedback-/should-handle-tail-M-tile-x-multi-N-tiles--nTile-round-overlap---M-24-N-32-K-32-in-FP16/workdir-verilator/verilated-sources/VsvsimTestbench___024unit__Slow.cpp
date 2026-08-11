// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"


VsvsimTestbench___024unit::VsvsimTestbench___024unit() = default;
VsvsimTestbench___024unit::~VsvsimTestbench___024unit() = default;

void VsvsimTestbench___024unit::ctor(VsvsimTestbench__Syms* symsp, const char* namep) {
    vlSymsp = symsp;
    vlNamep = strdup(Verilated::catName(vlSymsp->name(), namep));
    // Reset structure values
}

void VsvsimTestbench___024unit::__Vconfigure(bool first) {
    (void)first;  // Prevent unused variable warning
}

void VsvsimTestbench___024unit::dtor() {
    VL_DO_DANGLING(std::free(const_cast<char*>(vlNamep)), vlNamep);
}
