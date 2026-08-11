// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VsvsimTestbench.h for the primary calling header

#ifndef VERILATED_VSVSIMTESTBENCH___024ROOT_H_
#define VERILATED_VSVSIMTESTBENCH___024ROOT_H_  // guard

#include "verilated.h"
class VsvsimTestbench_HiveCell;


class VsvsimTestbench__Syms;

class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench___024root final {
  public:
    // CELLS
    VsvsimTestbench_HiveCell* __PVT__svsimTestbench__DOT__dut__DOT__arrays_0_0;
    VsvsimTestbench_HiveCell* __PVT__svsimTestbench__DOT__dut__DOT__arrays_0_1;
    VsvsimTestbench_HiveCell* __PVT__svsimTestbench__DOT__dut__DOT__arrays_1_0;
    VsvsimTestbench_HiveCell* __PVT__svsimTestbench__DOT__dut__DOT__arrays_1_1;

    // DESIGN SPECIFIC STATE
    CData/*0:0*/ svsimTestbench__DOT__clock;
    CData/*0:0*/ svsimTestbench__DOT__reset;
    CData/*0:0*/ svsimTestbench__DOT__io_loadVLock;
    CData/*0:0*/ svsimTestbench__DOT__io_clear;
    CData/*2:0*/ svsimTestbench__DOT__io_rndIn;
    CData/*1:0*/ svsimTestbench__DOT__io_fmtIn;
    CData/*0:0*/ svsimTestbench__DOT__io_validIn;
    CData/*0:0*/ svsimTestbench__DOT__io_loadVIn;
    CData/*0:0*/ svsimTestbench__DOT__io_loadHIn;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r_1;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r_2;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r_3;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r_4;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r_5;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_r_6;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__validSkewedPerRow_1;
    CData/*0:0*/ __Vdpi_export_trigger;
    CData/*0:0*/ __Vtrigprevexpr___TOP__svsimTestbench__DOT__clock__0;
    CData/*0:0*/ __VactDidInit;
    CData/*0:0*/ __VactPhaseResult;
    CData/*0:0*/ __VnbaPhaseResult;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_0;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_1;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_2;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_3;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_4;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_5;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_6;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_7;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_8;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_9;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_10;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_11;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_12;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_13;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_14;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn_15;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_0;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_1;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_2;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_3;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_4;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_5;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_6;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_7;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_8;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_9;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_10;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_11;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_12;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_13;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_14;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn_15;
    IData/*31:0*/ svsimTestbench__DOT__simulationState;
    IData/*31:0*/ __Vtrigprevexpr___TOP__svsimTestbench__DOT__simulationState__0;
    IData/*31:0*/ __VactIterCount;
    VlUnpacked<QData/*63:0*/, 2> __VactTriggered;
    VlUnpacked<QData/*63:0*/, 2> __VnbaTriggered;

    // INTERNAL VARIABLES
    VsvsimTestbench__Syms* vlSymsp;
    const char* vlNamep;

    // CONSTRUCTORS
    VsvsimTestbench___024root(VsvsimTestbench__Syms* symsp, const char* namep);
    ~VsvsimTestbench___024root();
    VL_UNCOPYABLE(VsvsimTestbench___024root);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
