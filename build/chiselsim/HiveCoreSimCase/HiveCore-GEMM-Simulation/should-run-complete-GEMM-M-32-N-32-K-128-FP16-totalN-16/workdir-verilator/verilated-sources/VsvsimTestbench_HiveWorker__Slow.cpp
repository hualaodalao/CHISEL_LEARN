// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

void VsvsimTestbench_HiveWorker___ctor_var_reset(VsvsimTestbench_HiveWorker* vlSelf);

VsvsimTestbench_HiveWorker::VsvsimTestbench_HiveWorker() = default;
VsvsimTestbench_HiveWorker::~VsvsimTestbench_HiveWorker() = default;

void VsvsimTestbench_HiveWorker::ctor(VsvsimTestbench__Syms* symsp, const char* namep) {
    vlSymsp = symsp;
    vlNamep = strdup(Verilated::catName(vlSymsp->name(), namep));
    // Reset structure values
    VsvsimTestbench_HiveWorker___ctor_var_reset(this);
}

void VsvsimTestbench_HiveWorker::__Vconfigure(bool first) {
    (void)first;  // Prevent unused variable warning
}

void VsvsimTestbench_HiveWorker::dtor() {
    VL_DO_DANGLING(std::free(const_cast<char*>(vlNamep)), vlNamep);
}
