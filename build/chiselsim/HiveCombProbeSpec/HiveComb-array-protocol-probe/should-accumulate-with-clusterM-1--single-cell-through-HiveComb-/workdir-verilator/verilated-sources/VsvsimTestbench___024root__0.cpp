// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design implementation internals
// See VsvsimTestbench.h for the primary calling header

#include "VsvsimTestbench__pch.h"

extern "C" void initTestBenchScope();

void VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__initTestBenchScope_TOP(const VerilatedScope* __Vscopep, const char* __Vfilenamep, IData/*31:0*/ __Vlineno) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__initTestBenchScope_TOP\n"); );
    // Body
    Verilated::dpiContext(__Vscopep, __Vfilenamep, __Vlineno);
    initTestBenchScope();
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_clock_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_clock_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_clock_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_clock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_clock_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__clock = value_clock;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_clock_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_clock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_clock_TOP\n"); );
    // Body
    value_clock = vlSymsp->TOP.svsimTestbench__DOT__clock;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_reset_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_reset_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_reset_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_reset) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_reset_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__reset = value_reset;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_reset_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_reset) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_reset_TOP\n"); );
    // Body
    value_reset = vlSymsp->TOP.svsimTestbench__DOT__reset;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_0_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_0_TOP\n"); );
    // Body
    value_io_validOut_0 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_1_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_1_TOP\n"); );
    // Body
    value_io_validOut_1 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_2_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_2_TOP\n"); );
    // Body
    value_io_validOut_2 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_3_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_3_TOP\n"); );
    // Body
    value_io_validOut_3 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_4_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_4_TOP\n"); );
    // Body
    value_io_validOut_4 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_5_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_5_TOP\n"); );
    // Body
    value_io_validOut_5 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_6_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_6_TOP\n"); );
    // Body
    value_io_validOut_6 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_7_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_7_TOP\n"); );
    // Body
    value_io_validOut_7 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_7.__PVT__io_validOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_loadVLock_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_loadVLock_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_loadVLock_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadVLock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_loadVLock_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock 
        = value_io_loadVLock;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_loadVLock_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadVLock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_loadVLock_TOP\n"); );
    // Body
    value_io_loadVLock = vlSymsp->TOP.svsimTestbench__DOT__io_loadVLock;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_clear_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_clear_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_clear_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_clear) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_clear_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_clear = value_io_clear;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_clear_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_clear) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_clear_TOP\n"); );
    // Body
    value_io_clear = vlSymsp->TOP.svsimTestbench__DOT__io_clear;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_rndIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_rndIn_TOP\n"); );
    // Body
    value = 3U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_rndIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ value_io_rndIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_rndIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_rndIn = value_io_rndIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_rndIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ &value_io_rndIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_rndIn_TOP\n"); );
    // Body
    value_io_rndIn = vlSymsp->TOP.svsimTestbench__DOT__io_rndIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_fmtIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_fmtIn_TOP\n"); );
    // Body
    value = 2U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_fmtIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ value_io_fmtIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_fmtIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn = value_io_fmtIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_fmtIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ &value_io_fmtIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_fmtIn_TOP\n"); );
    // Body
    value_io_fmtIn = vlSymsp->TOP.svsimTestbench__DOT__io_fmtIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validIn_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_validIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_validIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_validIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_validIn = value_io_validIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validIn_TOP\n"); );
    // Body
    value_io_validIn = vlSymsp->TOP.svsimTestbench__DOT__io_validIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_loadVIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_loadVIn_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_loadVIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadVIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_loadVIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn = value_io_loadVIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_loadVIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadVIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_loadVIn_TOP\n"); );
    // Body
    value_io_loadVIn = vlSymsp->TOP.svsimTestbench__DOT__io_loadVIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_loadHIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_loadHIn_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_loadHIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ value_io_loadHIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_loadHIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn = value_io_loadHIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_loadHIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_loadHIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_loadHIn_TOP\n"); );
    // Body
    value_io_loadHIn = vlSymsp->TOP.svsimTestbench__DOT__io_loadHIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_0_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_0_TOP\n"); );
    // Body
    value_io_cOut_0 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_1_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_1_TOP\n"); );
    // Body
    value_io_cOut_1 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_2_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_2_TOP\n"); );
    // Body
    value_io_cOut_2 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_3_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_3_TOP\n"); );
    // Body
    value_io_cOut_3 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_4_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_4_TOP\n"); );
    // Body
    value_io_cOut_4 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_5_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_5_TOP\n"); );
    // Body
    value_io_cOut_5 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_6_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_6_TOP\n"); );
    // Body
    value_io_cOut_6 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_6.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_cOut_7_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_cOut_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_cOut_7_TOP\n"); );
    // Body
    value_io_cOut_7 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_7.__PVT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_0_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_0_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_0 = value_io_psumIn_0;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_0_TOP\n"); );
    // Body
    value_io_psumIn_0 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_0;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_1_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_1_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_1 = value_io_psumIn_1;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_1_TOP\n"); );
    // Body
    value_io_psumIn_1 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_1;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_2_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_2_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_2 = value_io_psumIn_2;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_2_TOP\n"); );
    // Body
    value_io_psumIn_2 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_2;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_3_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_3_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_3 = value_io_psumIn_3;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_3_TOP\n"); );
    // Body
    value_io_psumIn_3 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_3;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_4_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_4_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_4 = value_io_psumIn_4;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_4_TOP\n"); );
    // Body
    value_io_psumIn_4 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_4;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_5_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_5_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_5 = value_io_psumIn_5;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_5_TOP\n"); );
    // Body
    value_io_psumIn_5 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_5;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_6_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_6_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_6 = value_io_psumIn_6;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_6_TOP\n"); );
    // Body
    value_io_psumIn_6 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_6;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_7_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_7_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_7 = value_io_psumIn_7;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_7_TOP\n"); );
    // Body
    value_io_psumIn_7 = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn_7;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_0_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_0_TOP\n"); );
    // Body
    value_io_aOut_0 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_1_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_1_TOP\n"); );
    // Body
    value_io_aOut_1 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_2_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_2_TOP\n"); );
    // Body
    value_io_aOut_2 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_3_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_3_TOP\n"); );
    // Body
    value_io_aOut_3 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_4_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_4_TOP\n"); );
    // Body
    value_io_aOut_4 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_5_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_5_TOP\n"); );
    // Body
    value_io_aOut_5 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_6_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_6_TOP\n"); );
    // Body
    value_io_aOut_6 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_7_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_7_TOP\n"); );
    // Body
    value_io_aOut_7 = vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_7.__PVT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_0_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_0_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_0 = value_io_aIn_0;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_0_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_0_TOP\n"); );
    // Body
    value_io_aIn_0 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_0;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_1_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_1_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_1 = value_io_aIn_1;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_1_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_1_TOP\n"); );
    // Body
    value_io_aIn_1 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_1;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_2_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_2_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_2 = value_io_aIn_2;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_2_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_2_TOP\n"); );
    // Body
    value_io_aIn_2 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_2;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_3_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_3_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_3 = value_io_aIn_3;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_3_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_3_TOP\n"); );
    // Body
    value_io_aIn_3 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_3;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_4_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_4_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_4 = value_io_aIn_4;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_4_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_4_TOP\n"); );
    // Body
    value_io_aIn_4 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_4;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_5_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_5_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_5 = value_io_aIn_5;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_5_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_5_TOP\n"); );
    // Body
    value_io_aIn_5 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_5;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_6_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_6_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6 = value_io_aIn_6;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_6_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_6_TOP\n"); );
    // Body
    value_io_aIn_6 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_6;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_7_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_7_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7 = value_io_aIn_7;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_7_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_7_TOP\n"); );
    // Body
    value_io_aIn_7 = vlSymsp->TOP.svsimTestbench__DOT__io_aIn_7;
}

extern "C" int simulation_body();

void VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_body_TOP(const VerilatedScope* __Vscopep, const char* __Vfilenamep, IData/*31:0*/ __Vlineno) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_body_TOP\n"); );
    // Body
    Verilated::dpiContext(__Vscopep, __Vfilenamep, __Vlineno);
    simulation_body();
}

extern "C" int simulation_final();

void VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_final_TOP(const VerilatedScope* __Vscopep, const char* __Vfilenamep, IData/*31:0*/ __Vlineno) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_final_TOP\n"); );
    // Body
    Verilated::dpiContext(__Vscopep, __Vfilenamep, __Vlineno);
    simulation_final();
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__simulation_initializeTrace_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, std::string traceFilePath) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__simulation_initializeTrace_TOP\n"); );
    // Body
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__simulation_enableTrace_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &success) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__simulation_enableTrace_TOP\n"); );
    // Body
    success = 0U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__simulation_disableTrace_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &success) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__simulation_disableTrace_TOP\n"); );
    // Body
    success = 0U;
}

bool VsvsimTestbench___024root___trigger_anySet__ico(const VlUnpacked<QData/*63:0*/, 1> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___trigger_anySet__ico\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        if (in[n]) {
            return (1U);
        }
        n = ((IData)(1U) + n);
    } while ((1U > n));
    return (0U);
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VsvsimTestbench___024root___dump_triggers__ico(const VlUnpacked<QData/*63:0*/, 1> &triggers, const std::string &tag);
#endif  // VL_DEBUG
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__0(VsvsimTestbench_HiveWorker* vlSelf);

bool VsvsimTestbench___024root___eval_phase__ico(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_phase__ico\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VicoExecute;
    // Body
    {
        // Inlined CFunc: _eval_triggers_vec__ico
        vlSelfRef.__VicoTriggered[0U] = ((0xfffffffffffffffdULL 
                                          & vlSelfRef.__VicoTriggered[0U]) 
                                         | ((QData)((IData)(vlSelfRef.__Vdpi_export_trigger)) 
                                            << 1U));
        vlSelfRef.__Vdpi_export_trigger = 0U;
    }
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VsvsimTestbench___024root___dump_triggers__ico(vlSelfRef.__VicoTriggered, "ico"s);
    }
#endif
    __VicoExecute = VsvsimTestbench___024root___trigger_anySet__ico(vlSelfRef.__VicoTriggered);
    if (__VicoExecute) {
        {
            // Inlined CFunc: _eval_ico
            if ((2ULL & vlSelfRef.__VicoTriggered[0U])) {
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6));
                VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7));
            }
        }
    }
    return (__VicoExecute);
}

bool VsvsimTestbench___024root___trigger_anySet__act(const VlUnpacked<QData/*63:0*/, 2> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___trigger_anySet__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        if (in[n]) {
            return (1U);
        }
        n = ((IData)(1U) + n);
    } while ((2U > n));
    return (0U);
}

void VsvsimTestbench___024root___act_sequent__TOP__0(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___act_sequent__TOP__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((1U == vlSelfRef.svsimTestbench__DOT__simulationState)) {
        VsvsimTestbench___024root____Vdpiimwrap_svsimTestbench__DOT__simulation_body_TOP(
                                                                                (vlSymsp->__Vscopep_svsimTestbench), 
                                                                                "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveComb-array-protocol-probe/should-accumulate-with-clusterM-1--single-cell-through-HiveComb-/workdir-verilator/../generated-sources/testbench.sv", 0x0000033bU);
        vlSelfRef.svsimTestbench__DOT__simulationState = 2U;
    }
}

void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_7__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6__0(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_0__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__1(VsvsimTestbench_HiveWorker* vlSelf);
void VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__1(VsvsimTestbench_HiveWorker* vlSelf);

void VsvsimTestbench___024root___eval_nba(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_nba\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    if ((2ULL & vlSelfRef.__VnbaTriggered[0U])) {
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_7_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_6_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_5_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_4_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_3_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_2_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_1_0));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1));
        VsvsimTestbench_HiveWorker___nba_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__1((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0));
    }
    if ((1ULL & vlSelfRef.__VnbaTriggered[1U])) {
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_7));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_0));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_1));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_2));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_3));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_4));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_5));
        VsvsimTestbench_HiveWorker___ico_sequent__TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6__0((&vlSymsp->TOP__svsimTestbench__DOT__dut__DOT__arrays_0_0__DOT__pes_0_6));
    }
}

void VsvsimTestbench___024root___trigger_orInto__act_vec_vec(VlUnpacked<QData/*63:0*/, 2> &out, const VlUnpacked<QData/*63:0*/, 2> &in) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___trigger_orInto__act_vec_vec\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        out[n] = (out[n] | in[n]);
        n = ((IData)(1U) + n);
    } while ((1U >= n));
}

#ifdef VL_DEBUG
VL_ATTR_COLD void VsvsimTestbench___024root___dump_triggers__act(const VlUnpacked<QData/*63:0*/, 2> &triggers, const std::string &tag);
#endif  // VL_DEBUG

bool VsvsimTestbench___024root___eval_phase__act(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_phase__act\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VactExecute;
    // Body
    {
        // Inlined CFunc: _eval_triggers_vec__act
        vlSelfRef.__VactTriggered[1U] = ((0xfffffffffffffffeULL 
                                          & vlSelfRef.__VactTriggered[1U]) 
                                         | (IData)((IData)(vlSelfRef.__Vdpi_export_trigger)));
        vlSelfRef.__Vdpi_export_trigger = 0U;
        vlSelfRef.__VactTriggered[0U] = (QData)((IData)(
                                                        ((((IData)(vlSelfRef.svsimTestbench__DOT__clock) 
                                                           & (~ (IData)(vlSelfRef.__Vtrigprevexpr___TOP__svsimTestbench__DOT__clock__0))) 
                                                          << 1U) 
                                                         | (vlSelfRef.svsimTestbench__DOT__simulationState 
                                                            != vlSelfRef.__Vtrigprevexpr___TOP__svsimTestbench__DOT__simulationState__0))));
        vlSelfRef.__Vtrigprevexpr___TOP__svsimTestbench__DOT__simulationState__0 
            = vlSelfRef.svsimTestbench__DOT__simulationState;
        vlSelfRef.__Vtrigprevexpr___TOP__svsimTestbench__DOT__clock__0 
            = vlSelfRef.svsimTestbench__DOT__clock;
        if (VL_UNLIKELY(((1U & (~ (IData)(vlSelfRef.__VactDidInit)))))) {
            vlSelfRef.__VactDidInit = 1U;
            vlSelfRef.__VactTriggered[0U] = (1ULL | vlSelfRef.__VactTriggered[0U]);
        }
    }
#ifdef VL_DEBUG
    if (VL_UNLIKELY(vlSymsp->_vm_contextp__->debug())) {
        VsvsimTestbench___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
    }
#endif
    VsvsimTestbench___024root___trigger_orInto__act_vec_vec(vlSelfRef.__VnbaTriggered, vlSelfRef.__VactTriggered);
    __VactExecute = VsvsimTestbench___024root___trigger_anySet__act(vlSelfRef.__VactTriggered);
    if (__VactExecute) {
        {
            // Inlined CFunc: _eval_act
            if ((1ULL & vlSelfRef.__VactTriggered[0U])) {
                VsvsimTestbench___024root___act_sequent__TOP__0(vlSelf);
            }
        }
    }
    return (__VactExecute);
}

void VsvsimTestbench___024root___trigger_clear__act(VlUnpacked<QData/*63:0*/, 2> &out) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___trigger_clear__act\n"); );
    // Locals
    IData/*31:0*/ n;
    // Body
    n = 0U;
    do {
        out[n] = 0ULL;
        n = ((IData)(1U) + n);
    } while ((2U > n));
}

bool VsvsimTestbench___024root___eval_phase__nba(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_phase__nba\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    CData/*0:0*/ __VnbaExecute;
    // Body
    __VnbaExecute = VsvsimTestbench___024root___trigger_anySet__act(vlSelfRef.__VnbaTriggered);
    if (__VnbaExecute) {
        VsvsimTestbench___024root___eval_nba(vlSelf);
        VsvsimTestbench___024root___trigger_clear__act(vlSelfRef.__VnbaTriggered);
    }
    return (__VnbaExecute);
}

void VsvsimTestbench___024root___eval(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VicoIterCount;
    IData/*31:0*/ __VnbaIterCount;
    // Body
    __VicoIterCount = 0U;
    do {
        if (VL_UNLIKELY(((0x00002710U < __VicoIterCount)))) {
#ifdef VL_DEBUG
            VsvsimTestbench___024root___dump_triggers__ico(vlSelfRef.__VicoTriggered, "ico"s);
#endif
            VL_FATAL_MT("/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveComb-array-protocol-probe/should-accumulate-with-clusterM-1--single-cell-through-HiveComb-/workdir-verilator/../generated-sources/testbench.sv", 1, "", "DIDNOTCONVERGE: Input combinational region did not converge after '--converge-limit' of 10000 tries");
        }
        __VicoIterCount = ((IData)(1U) + __VicoIterCount);
        vlSelfRef.__VicoPhaseResult = VsvsimTestbench___024root___eval_phase__ico(vlSelf);
    } while (vlSelfRef.__VicoPhaseResult);
    __VnbaIterCount = 0U;
    do {
        if (VL_UNLIKELY(((0x00002710U < __VnbaIterCount)))) {
#ifdef VL_DEBUG
            VsvsimTestbench___024root___dump_triggers__act(vlSelfRef.__VnbaTriggered, "nba"s);
#endif
            VL_FATAL_MT("/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveComb-array-protocol-probe/should-accumulate-with-clusterM-1--single-cell-through-HiveComb-/workdir-verilator/../generated-sources/testbench.sv", 1, "", "DIDNOTCONVERGE: NBA region did not converge after '--converge-limit' of 10000 tries");
        }
        __VnbaIterCount = ((IData)(1U) + __VnbaIterCount);
        vlSelfRef.__VactIterCount = 0U;
        do {
            if (VL_UNLIKELY(((0x00002710U < vlSelfRef.__VactIterCount)))) {
#ifdef VL_DEBUG
                VsvsimTestbench___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
#endif
                VL_FATAL_MT("/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveComb-array-protocol-probe/should-accumulate-with-clusterM-1--single-cell-through-HiveComb-/workdir-verilator/../generated-sources/testbench.sv", 1, "", "DIDNOTCONVERGE: Active region did not converge after '--converge-limit' of 10000 tries");
            }
            vlSelfRef.__VactIterCount = ((IData)(1U) 
                                         + vlSelfRef.__VactIterCount);
            vlSelfRef.__VactPhaseResult = VsvsimTestbench___024root___eval_phase__act(vlSelf);
        } while (vlSelfRef.__VactPhaseResult);
        vlSelfRef.__VnbaPhaseResult = VsvsimTestbench___024root___eval_phase__nba(vlSelf);
    } while (vlSelfRef.__VnbaPhaseResult);
}

#ifdef VL_DEBUG
void VsvsimTestbench___024root___eval_debug_assertions(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval_debug_assertions\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
}
#endif  // VL_DEBUG
