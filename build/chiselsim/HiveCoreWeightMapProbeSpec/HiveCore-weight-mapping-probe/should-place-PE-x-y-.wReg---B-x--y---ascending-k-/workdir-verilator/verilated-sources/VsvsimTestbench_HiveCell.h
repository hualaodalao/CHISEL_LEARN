// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VsvsimTestbench.h for the primary calling header

#ifndef VERILATED_VSVSIMTESTBENCH_HIVECELL_H_
#define VERILATED_VSVSIMTESTBENCH_HIVECELL_H_  // guard

#include "verilated.h"
class VsvsimTestbench_HiveWorker;


class VsvsimTestbench__Syms;

class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench_HiveCell final {
  public:
    // CELLS
    VsvsimTestbench_HiveWorker* __PVT__pes_0_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_0_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_1_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_2_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_3_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_4_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_5_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_6_7;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_0;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_1;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_2;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_3;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_4;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_5;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_6;
    VsvsimTestbench_HiveWorker* __PVT__pes_7_7;

    // DESIGN SPECIFIC STATE
    CData/*0:0*/ clock;
    CData/*0:0*/ reset;
    CData/*0:0*/ io_clear;
    CData/*0:0*/ io_loadVLock;
    CData/*0:0*/ io_validIn;
    CData/*1:0*/ io_fmtIn;
    CData/*2:0*/ io_rndIn;
    CData/*0:0*/ io_loadHIn;
    CData/*0:0*/ io_loadVIn;
    CData/*0:0*/ io_validOut_0;
    CData/*0:0*/ io_validOut_1;
    CData/*0:0*/ io_validOut_2;
    CData/*0:0*/ io_validOut_3;
    CData/*0:0*/ io_validOut_4;
    CData/*0:0*/ io_validOut_5;
    CData/*0:0*/ io_validOut_6;
    CData/*0:0*/ io_validOut_7;
    CData/*1:0*/ io_fmtOut;
    CData/*2:0*/ io_rndOut;
    SData/*15:0*/ io_aIn_0;
    SData/*15:0*/ io_aIn_1;
    SData/*15:0*/ io_aIn_2;
    SData/*15:0*/ io_aIn_3;
    SData/*15:0*/ io_aIn_4;
    SData/*15:0*/ io_aIn_5;
    SData/*15:0*/ io_aIn_6;
    SData/*15:0*/ io_aIn_7;
    SData/*15:0*/ io_aOut_0;
    SData/*15:0*/ io_aOut_1;
    SData/*15:0*/ io_aOut_2;
    SData/*15:0*/ io_aOut_3;
    SData/*15:0*/ io_aOut_4;
    SData/*15:0*/ io_aOut_5;
    SData/*15:0*/ io_aOut_6;
    SData/*15:0*/ io_aOut_7;
    IData/*31:0*/ io_psumIn_0;
    IData/*31:0*/ io_psumIn_1;
    IData/*31:0*/ io_psumIn_2;
    IData/*31:0*/ io_psumIn_3;
    IData/*31:0*/ io_psumIn_4;
    IData/*31:0*/ io_psumIn_5;
    IData/*31:0*/ io_psumIn_6;
    IData/*31:0*/ io_psumIn_7;
    IData/*31:0*/ io_cOut_0;
    IData/*31:0*/ io_cOut_1;
    IData/*31:0*/ io_cOut_2;
    IData/*31:0*/ io_cOut_3;
    IData/*31:0*/ io_cOut_4;
    IData/*31:0*/ io_cOut_5;
    IData/*31:0*/ io_cOut_6;
    IData/*31:0*/ io_cOut_7;

    // INTERNAL VARIABLES
    VsvsimTestbench__Syms* vlSymsp;
    const char* vlNamep;

    // CONSTRUCTORS
    VsvsimTestbench_HiveCell();
    ~VsvsimTestbench_HiveCell();
    void ctor(VsvsimTestbench__Syms* symsp, const char* namep);
    void dtor();
    VL_UNCOPYABLE(VsvsimTestbench_HiveCell);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
