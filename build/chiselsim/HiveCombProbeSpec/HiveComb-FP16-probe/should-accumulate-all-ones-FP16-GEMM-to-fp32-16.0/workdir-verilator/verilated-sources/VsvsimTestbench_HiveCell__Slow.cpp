// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

void VsvsimTestbench_HiveCell___ctor_var_reset(VsvsimTestbench_HiveCell* vlSelf);

VsvsimTestbench_HiveCell::VsvsimTestbench_HiveCell() = default;
VsvsimTestbench_HiveCell::~VsvsimTestbench_HiveCell() = default;

void VsvsimTestbench_HiveCell::ctor(VsvsimTestbench__Syms* symsp, const char* namep) {
    vlSymsp = symsp;
    vlNamep = strdup(Verilated::catName(vlSymsp->name(), namep));
    // Reset structure values
    VsvsimTestbench_HiveCell___ctor_var_reset(this);
}

void VsvsimTestbench_HiveCell::__Vconfigure(bool first) {
    (void)first;  // Prevent unused variable warning
}

void VsvsimTestbench_HiveCell::dtor() {
    VL_DO_DANGLING(std::free(const_cast<char*>(vlNamep)), vlNamep);
}
