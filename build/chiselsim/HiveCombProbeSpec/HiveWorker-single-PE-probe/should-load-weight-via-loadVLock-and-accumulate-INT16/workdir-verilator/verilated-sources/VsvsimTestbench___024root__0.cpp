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

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_rndOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_rndOut_TOP\n"); );
    // Body
    value = 3U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_rndOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*2:0*/ &value_io_rndOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_rndOut_TOP\n"); );
    // Body
    value_io_rndOut = vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__io_rndOut_REG;
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

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_fmtOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_fmtOut_TOP\n"); );
    // Body
    value = 2U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_fmtOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*1:0*/ &value_io_fmtOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_fmtOut_TOP\n"); );
    // Body
    value_io_fmtOut = vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__io_fmtOut_REG;
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

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_validOut_TOP\n"); );
    // Body
    value = 1U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, CData/*0:0*/ &value_io_validOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_validOut_TOP\n"); );
    // Body
    value_io_validOut = vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__io_validOut_REG;
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

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumOut_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumOut_TOP\n"); );
    // Body
    value_io_psumOut = vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__psumReg;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_psumIn_TOP\n"); );
    // Body
    value = 0x00000020U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ value_io_psumIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_psumIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_psumIn = value_io_psumIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value_io_psumIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_psumIn_TOP\n"); );
    // Body
    value_io_psumIn = vlSymsp->TOP.svsimTestbench__DOT__io_psumIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aOut_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aOut_TOP\n"); );
    // Body
    value_io_aOut = vlSymsp->TOP.svsimTestbench__DOT__dut__DOT__io_aOut_REG;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, IData/*31:0*/ &value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitWidthImpl_io_aIn_TOP\n"); );
    // Body
    value = 0x00000010U;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ value_io_aIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__setBitsImpl_io_aIn_TOP\n"); );
    // Body
    vlSymsp->TOP.__Vdpi_export_trigger = 1U;
    vlSymsp->TOP.svsimTestbench__DOT__io_aIn = value_io_aIn;
}

void VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_TOP(VsvsimTestbench__Syms* __restrict vlSymsp, SData/*15:0*/ &value_io_aIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root____Vdpiexp_svsimTestbench__DOT__getBitsImpl_io_aIn_TOP\n"); );
    // Body
    value_io_aIn = vlSymsp->TOP.svsimTestbench__DOT__io_aIn;
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
                                                                                "/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv", 0x00000127U);
        vlSelfRef.svsimTestbench__DOT__simulationState = 2U;
    }
}

void VsvsimTestbench___024root___nba_sequent__TOP__0(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___nba_sequent__TOP__0\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Body
    vlSelfRef.svsimTestbench__DOT__dut__DOT__io_validOut_REG 
        = ((1U & (~ (IData)(vlSelfRef.svsimTestbench__DOT__reset))) 
           && (IData)(vlSelfRef.svsimTestbench__DOT__io_validIn));
    if (vlSelfRef.svsimTestbench__DOT__reset) {
        vlSelfRef.svsimTestbench__DOT__dut__DOT__io_fmtOut_REG = 3U;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__io_aOut_REG = 0U;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__io_rndOut_REG = 0U;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__psumReg = 0U;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg = 0U;
    } else {
        vlSelfRef.svsimTestbench__DOT__dut__DOT__io_fmtOut_REG 
            = vlSelfRef.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__io_aOut_REG 
            = vlSelfRef.svsimTestbench__DOT__io_aIn;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__io_rndOut_REG 
            = vlSelfRef.svsimTestbench__DOT__io_rndIn;
        if (vlSelfRef.svsimTestbench__DOT__io_clear) {
            vlSelfRef.svsimTestbench__DOT__dut__DOT__psumReg = 0U;
        } else if (vlSelfRef.svsimTestbench__DOT__io_loadVIn) {
            vlSelfRef.svsimTestbench__DOT__dut__DOT__psumReg 
                = vlSelfRef.svsimTestbench__DOT__io_psumIn;
        } else {
            vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T_1 
                = (0U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg));
            vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T 
                = (1U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg));
            vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___psumReg_isInt_T_1 
                = (2U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg));
            if (((IData)(vlSelfRef.svsimTestbench__DOT__io_validIn) 
                 & ((((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T_1) 
                      | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T)) 
                     | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___psumReg_isInt_T_1)) 
                    | (3U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg))))) {
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.svsimTestbench__DOT__io_aIn) 
                                             >> 0x0aU)));
                if ((3U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg))) {
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.svsimTestbench__DOT__io_aIn) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.svsimTestbench__DOT__io_aIn)));
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = ((0x0000ff00U & ((- (IData)(
                                                      (1U 
                                                       & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg) 
                                                          >> 7U)))) 
                                           << 8U)) 
                           | (0x000000ffU & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg)));
                } else {
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5 
                        = vlSelfRef.svsimTestbench__DOT__io_aIn;
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5 
                        = vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg;
                }
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T 
                    = (0U == (0x0000001fU & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg) 
                                             >> 0x0aU)));
                if (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T) {
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((IData)(vlSelfRef.svsimTestbench__DOT__io_aIn) 
                           << 0x00000010U);
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg) 
                           << 0x00000010U);
                } else {
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.svsimTestbench__DOT__io_aIn) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.svsimTestbench__DOT__io_aIn))))
                               ? 0U : ((((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.svsimTestbench__DOT__io_aIn) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.svsimTestbench__DOT__io_aIn) 
                                             << 0x0000000dU)))));
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                        = ((0x80000000U & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg) 
                                           << 0x00000010U)) 
                           | (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T) 
                               & (0U == (0x000003ffU 
                                         & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg))))
                               ? 0U : ((((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T)
                                          ? 0U : (0x000000ffU 
                                                  & ((IData)(0x70U) 
                                                     + 
                                                     (0x0000001fU 
                                                      & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg) 
                                                         >> 0x0aU))))) 
                                        << 0x00000017U) 
                                       | (0x007fe000U 
                                          & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg) 
                                             << 0x0000000dU)))));
                }
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut 
                    = ((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                        ^ vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32) 
                       >> 0x1fU);
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                    = (0x0000ffffffffffffULL & ((0x0000000000800000ULL 
                                                 | (QData)((IData)(
                                                                   (0x007fffffU 
                                                                    & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                                * (0x0000000000800000ULL 
                                                   | (QData)((IData)(
                                                                     (0x007fffffU 
                                                                      & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32))))));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2 
                    = (0x000001ffU & (((0x000000ffU 
                                        & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32 
                                           >> 0x17U)) 
                                       + (0x000000ffU 
                                          & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32 
                                             >> 0x17U))) 
                                      + (1U & (IData)(
                                                      (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                       >> 0x2fU)))));
                if ((1U & (IData)((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                   >> 0x2fU)))) {
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x18U)));
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x17U)));
                } else {
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm 
                        = (0x007fffffU & (IData)((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                                  >> 0x17U)));
                    vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit 
                        = (1U & (IData)((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd 
                                         >> 0x16U)));
                }
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1 
                    = (2U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__rndReg));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN 
                    = (3U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__rndReg));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0 
                    = (1U != (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__rndReg));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                    = (((3U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg)) 
                        | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? ((((- (IData)((1U & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5) 
                                               >> 0x0fU)))) 
                             << 0x00000010U) | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5)) 
                           * (((- (IData)((1U & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5) 
                                                 >> 0x0fU)))) 
                               << 0x00000010U) | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5)))
                        : ((1U & (((~ ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T) 
                                       | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T_1))) 
                                   | (IData)((0U == 
                                              (0x7fffffffU 
                                               & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32)))) 
                                  | (IData)((0U == 
                                             (0x7fffffffU 
                                              & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32)))))
                            ? 0U : (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                     << 0x0000001fU) 
                                    | ((((0x007eU < (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2))
                                          ? (0x000000ffU 
                                             & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2) 
                                                - (IData)(0x7fU)))
                                          : 0U) << 0x00000017U) 
                                       | (0x007fffffU 
                                          & (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                              & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                  ? 
                                                 ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut) 
                                                  & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))
                                                  : 
                                                 (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                   | (~ (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut))) 
                                                  & (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit))))
                                              ? ((IData)(1U) 
                                                 + vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm)
                                              : vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm))))));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T 
                    = ((0x000000ffU & (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                       >> 0x17U)) >= 
                       (0x000000ffU & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x17U)));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                          << 1U))) 
                       >> ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                                    >> 0x17U)))));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25 
                    = ((0x01000000U | (0x00fffffeU 
                                       & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                          << 1U))) 
                       >> (((0x000000ffU & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                            >> 0x17U)) 
                            >= (0x000000ffU & (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                               >> 0x17U)))
                            ? 0U : (0x0000001fU & (
                                                   (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                                    >> 0x17U) 
                                                   - 
                                                   (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                                    >> 0x17U)))));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                    = (0x03ffffffU & ((vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25)
                                       : vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                    = (0x03ffffffU & ((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                       >> 0x1fU) ? 
                                      (- vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25)
                                       : vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                    = (0x07ffffffU & (((0x04000000U 
                                        & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed 
                                           << 1U)) 
                                       | vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed) 
                                      + ((0x04000000U 
                                          & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed 
                                             << 1U)) 
                                         | vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed)));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                    = (0x03ffffffU & ((0x04000000U 
                                       & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       ? (- vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum)
                                       : vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero 
                    = ((0x02000000U & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                        ? 0U : ((0x01000000U & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                 ? 1U : ((0x00800000U 
                                          & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                          ? 2U : ((0x00400000U 
                                                   & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                   ? 3U
                                                   : 
                                                  ((0x00200000U 
                                                    & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                    ? 4U
                                                    : 
                                                   ((0x00100000U 
                                                     & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                     ? 5U
                                                     : 
                                                    ((0x00080000U 
                                                      & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                      ? 6U
                                                      : 
                                                     ((0x00040000U 
                                                       & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                       ? 7U
                                                       : 
                                                      ((0x00020000U 
                                                        & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                        ? 8U
                                                        : 
                                                       ((0x00010000U 
                                                         & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                         ? 9U
                                                         : 
                                                        ((0x00008000U 
                                                          & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                          ? 0x0aU
                                                          : 
                                                         ((0x00004000U 
                                                           & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                           ? 0x0bU
                                                           : 
                                                          ((0x00002000U 
                                                            & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                            ? 0x0cU
                                                            : 
                                                           ((0x00001000U 
                                                             & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                             ? 0x0dU
                                                             : 
                                                            ((0x00000800U 
                                                              & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                              ? 0x0eU
                                                              : 
                                                             ((0x00000400U 
                                                               & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                               ? 0x0fU
                                                               : 
                                                              ((0x00000200U 
                                                                & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                ? 0x10U
                                                                : 
                                                               ((0x00000100U 
                                                                 & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                 ? 0x11U
                                                                 : 
                                                                ((0x00000080U 
                                                                  & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                  ? 0x12U
                                                                  : 
                                                                 ((0x00000040U 
                                                                   & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                   ? 0x13U
                                                                   : 
                                                                  ((0x00000020U 
                                                                    & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                    ? 0x14U
                                                                    : 
                                                                   ((0x00000010U 
                                                                     & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                     ? 0x15U
                                                                     : 
                                                                    ((8U 
                                                                      & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                      ? 0x16U
                                                                      : 
                                                                     ((4U 
                                                                       & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                                                       ? 0x17U
                                                                       : 
                                                                      (0x00000018U 
                                                                       | (1U 
                                                                          & (~ 
                                                                             (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs 
                                                                              >> 1U))))))))))))))))))))))))))));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                    = (0x01ffffffffffffffULL & ((QData)((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)) 
                                                << (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero)));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3 
                    = (0x000000ffU & ((IData)(1U) + 
                                      ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T)
                                        ? (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                                           >> 0x17U)
                                        : (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product 
                                           >> 0x17U))));
                vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1 
                    = vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
                vlSelfRef.svsimTestbench__DOT__dut__DOT__psumReg 
                    = (((3U == (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg)) 
                        | (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___psumReg_isInt_T_1))
                        ? (vlSelfRef.svsimTestbench__DOT__io_psumIn 
                           + vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product)
                        : ((IData)((0U == (0x7fffffffU 
                                           & vlSelfRef.svsimTestbench__DOT__io_psumIn)))
                            ? vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product
                            : ((IData)((0U == (0x7fffffffU 
                                               & vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product)))
                                ? vlSelfRef.svsimTestbench__DOT__io_psumIn
                                : ((0U == vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs)
                                    ? 0U : ((0x80000000U 
                                             & (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                << 5U)) 
                                            | (((((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                  > (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1))
                                                  ? 
                                                 (0x000000ffU 
                                                  & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3) 
                                                     - (IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1)))
                                                  : 0U) 
                                                << 0x00000017U) 
                                               | (0x007fffffU 
                                                  & (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0) 
                                                      & ((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1)
                                                          ? 
                                                         ((vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                           >> 0x1aU) 
                                                          & (IData)(
                                                                    (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                     >> 1U)))
                                                          : 
                                                         (((IData)(vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN) 
                                                           | (~ 
                                                              (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum 
                                                               >> 0x1aU))) 
                                                          & (IData)(
                                                                    (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                     >> 1U)))))
                                                      ? 
                                                     ((IData)(1U) 
                                                      + (IData)(
                                                                (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                 >> 2U)))
                                                      : (IData)(
                                                                (vlSelfRef.svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T 
                                                                 >> 2U))))))))));
            }
        }
        if (vlSelfRef.svsimTestbench__DOT__io_loadVLock) {
            vlSelfRef.svsimTestbench__DOT__dut__DOT__wReg 
                = (0x0000ffffU & vlSelfRef.svsimTestbench__DOT__io_psumIn);
        }
    }
    if (vlSelfRef.svsimTestbench__DOT__io_loadHIn) {
        vlSelfRef.svsimTestbench__DOT__dut__DOT__fmtReg 
            = vlSelfRef.svsimTestbench__DOT__io_fmtIn;
        vlSelfRef.svsimTestbench__DOT__dut__DOT__rndReg 
            = vlSelfRef.svsimTestbench__DOT__io_rndIn;
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
        {
            // Inlined CFunc: _eval_nba
            if ((2ULL & vlSelfRef.__VnbaTriggered[0U])) {
                VsvsimTestbench___024root___nba_sequent__TOP__0(vlSelf);
            }
        }
        VsvsimTestbench___024root___trigger_clear__act(vlSelfRef.__VnbaTriggered);
    }
    return (__VnbaExecute);
}

void VsvsimTestbench___024root___eval(VsvsimTestbench___024root* vlSelf) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root___eval\n"); );
    VsvsimTestbench__Syms* const __restrict vlSymsp VL_ATTR_UNUSED = vlSelf->vlSymsp;
    auto& vlSelfRef = std::ref(*vlSelf).get();
    // Locals
    IData/*31:0*/ __VnbaIterCount;
    // Body
    __VnbaIterCount = 0U;
    do {
        if (VL_UNLIKELY(((0x00002710U < __VnbaIterCount)))) {
#ifdef VL_DEBUG
            VsvsimTestbench___024root___dump_triggers__act(vlSelfRef.__VnbaTriggered, "nba"s);
#endif
            VL_FATAL_MT("/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv", 1, "", "DIDNOTCONVERGE: NBA region did not converge after '--converge-limit' of 10000 tries");
        }
        __VnbaIterCount = ((IData)(1U) + __VnbaIterCount);
        vlSelfRef.__VactIterCount = 0U;
        do {
            if (VL_UNLIKELY(((0x00002710U < vlSelfRef.__VactIterCount)))) {
#ifdef VL_DEBUG
                VsvsimTestbench___024root___dump_triggers__act(vlSelfRef.__VactTriggered, "act"s);
#endif
                VL_FATAL_MT("/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/chiselsim/HiveCombProbeSpec/HiveWorker-single-PE-probe/should-load-weight-via-loadVLock-and-accumulate-INT16/workdir-verilator/../generated-sources/testbench.sv", 1, "", "DIDNOTCONVERGE: Active region did not converge after '--converge-limit' of 10000 tries");
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
