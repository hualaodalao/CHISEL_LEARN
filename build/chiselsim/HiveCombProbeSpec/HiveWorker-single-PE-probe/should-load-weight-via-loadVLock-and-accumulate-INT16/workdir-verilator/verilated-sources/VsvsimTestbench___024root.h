// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VsvsimTestbench.h for the primary calling header

#ifndef VERILATED_VSVSIMTESTBENCH___024ROOT_H_
#define VERILATED_VSVSIMTESTBENCH___024ROOT_H_  // guard

#include "verilated.h"


class VsvsimTestbench__Syms;

class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench___024root final {
  public:

    // DESIGN SPECIFIC STATE
    CData/*0:0*/ svsimTestbench__DOT__clock;
    CData/*0:0*/ svsimTestbench__DOT__reset;
    CData/*0:0*/ svsimTestbench__DOT__io_clear;
    CData/*2:0*/ svsimTestbench__DOT__io_rndIn;
    CData/*1:0*/ svsimTestbench__DOT__io_fmtIn;
    CData/*0:0*/ svsimTestbench__DOT__io_validIn;
    CData/*0:0*/ svsimTestbench__DOT__io_loadVIn;
    CData/*0:0*/ svsimTestbench__DOT__io_loadVLock;
    CData/*0:0*/ svsimTestbench__DOT__io_loadHIn;
    CData/*1:0*/ svsimTestbench__DOT__dut__DOT__fmtReg;
    CData/*2:0*/ svsimTestbench__DOT__dut__DOT__rndReg;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__io_validOut_REG;
    CData/*1:0*/ svsimTestbench__DOT__dut__DOT__io_fmtOut_REG;
    CData/*2:0*/ svsimTestbench__DOT__dut__DOT__io_rndOut_REG;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T_1;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___product_fpCond_T;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT___psumReg_isInt_T_1;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0;
    CData/*0:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T;
    CData/*4:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
    CData/*7:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3;
    CData/*7:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1;
    CData/*0:0*/ __Vdpi_export_trigger;
    CData/*0:0*/ __Vtrigprevexpr___TOP__svsimTestbench__DOT__clock__0;
    CData/*0:0*/ __VactDidInit;
    CData/*0:0*/ __VactPhaseResult;
    CData/*0:0*/ __VnbaPhaseResult;
    SData/*15:0*/ svsimTestbench__DOT__io_aIn;
    SData/*15:0*/ svsimTestbench__DOT__dut__DOT__wReg;
    SData/*15:0*/ svsimTestbench__DOT__dut__DOT__io_aOut_REG;
    SData/*15:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5;
    SData/*15:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5;
    SData/*8:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2;
    IData/*31:0*/ svsimTestbench__DOT__io_psumIn;
    IData/*31:0*/ svsimTestbench__DOT__simulationState;
    IData/*31:0*/ svsimTestbench__DOT__dut__DOT__psumReg;
    IData/*31:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32;
    IData/*31:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32;
    IData/*22:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm;
    IData/*31:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product;
    IData/*24:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25;
    IData/*24:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25;
    IData/*25:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed;
    IData/*25:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed;
    IData/*26:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum;
    IData/*25:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs;
    IData/*31:0*/ __Vtrigprevexpr___TOP__svsimTestbench__DOT__simulationState__0;
    IData/*31:0*/ __VactIterCount;
    QData/*47:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd;
    QData/*56:0*/ svsimTestbench__DOT__dut__DOT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T;
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
