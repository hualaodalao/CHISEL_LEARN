// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VHive_FpAddNarrow.h for the primary calling header

#ifndef VERILATED_VHIVE_FPADDNARROW___024ROOT_H_
#define VERILATED_VHIVE_FPADDNARROW___024ROOT_H_  // guard

#include "verilated.h"


class VHive_FpAddNarrow__Syms;

class alignas(VL_CACHE_LINE_BYTES) VHive_FpAddNarrow___024root final {
  public:

    // DESIGN SPECIFIC STATE
    VL_IN8(io_is4x,0,0);
    VL_IN8(io_is2x,0,0);
    CData/*0:0*/ Hive_FpAddNarrow__DOT__valid_1;
    CData/*0:0*/ Hive_FpAddNarrow__DOT__valid_2;
    CData/*0:0*/ Hive_FpAddNarrow__DOT__valid_3;
    CData/*0:0*/ __VdfgRegularize_hebeb780c_0_0;
    CData/*0:0*/ __VdfgRegularize_hebeb780c_0_1;
    CData/*0:0*/ __VstlFirstIteration;
    CData/*0:0*/ __VstlPhaseResult;
    CData/*0:0*/ __Vtrigprevexpr___TOP__io_is4x__0;
    CData/*0:0*/ __Vtrigprevexpr___TOP__io_is2x__0;
    CData/*0:0*/ __VicoDidInit;
    CData/*0:0*/ __VicoPhaseResult;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__eC;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__e0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__e1;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__e2;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__e3;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__eMaxC0;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__eMax12;
    SData/*8:0*/ Hive_FpAddNarrow__DOT__eMaxA;
    VL_IN(io_prodFp16,31,0);
    VL_IN(io_prodFp8,31,0);
    VL_IN(io_prodFp4_0,31,0);
    VL_IN(io_prodFp4_1,31,0);
    IData/*31:0*/ __Vtrigprevexpr___TOP__io_prodFp16__0;
    IData/*31:0*/ __Vtrigprevexpr___TOP__io_prodFp8__0;
    IData/*31:0*/ __Vtrigprevexpr___TOP__io_prodFp4_0__0;
    IData/*31:0*/ __Vtrigprevexpr___TOP__io_prodFp4_1__0;
    VL_IN64(io_cReg,39,0);
    VL_OUT64(io_sum,39,0);
    QData/*39:0*/ __Vtrigprevexpr___TOP__io_cReg__0;
    VlUnpacked<QData/*63:0*/, 1> __VstlTriggered;
    VlUnpacked<QData/*63:0*/, 2> __VicoTriggered;

    // INTERNAL VARIABLES
    VHive_FpAddNarrow__Syms* vlSymsp;
    const char* vlNamep;

    // CONSTRUCTORS
    VHive_FpAddNarrow___024root(VHive_FpAddNarrow__Syms* symsp, const char* namep);
    ~VHive_FpAddNarrow___024root();
    VL_UNCOPYABLE(VHive_FpAddNarrow___024root);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
