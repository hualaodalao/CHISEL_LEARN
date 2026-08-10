// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Design internal header
// See VsvsimTestbench.h for the primary calling header

#ifndef VERILATED_VSVSIMTESTBENCH_HIVEWORKER_H_
#define VERILATED_VSVSIMTESTBENCH_HIVEWORKER_H_  // guard

#include "verilated.h"


class VsvsimTestbench__Syms;

class alignas(VL_CACHE_LINE_BYTES) VsvsimTestbench_HiveWorker final {
  public:

    // DESIGN SPECIFIC STATE
    CData/*0:0*/ clock;
    CData/*0:0*/ reset;
    CData/*0:0*/ io_loadHIn;
    CData/*0:0*/ io_loadVLock;
    CData/*0:0*/ io_loadVIn;
    CData/*0:0*/ io_validIn;
    CData/*0:0*/ io_validOut;
    CData/*1:0*/ io_fmtIn;
    CData/*1:0*/ io_fmtOut;
    CData/*2:0*/ io_rndIn;
    CData/*2:0*/ io_rndOut;
    CData/*0:0*/ io_clear;
    CData/*1:0*/ __PVT__fmtReg;
    CData/*2:0*/ __PVT__rndReg;
    CData/*0:0*/ __PVT__io_validOut_REG;
    CData/*1:0*/ __PVT__io_fmtOut_REG;
    CData/*2:0*/ __PVT__io_rndOut_REG;
    CData/*0:0*/ __PVT__unnamedblk1__DOT___product_fpCond_T_1;
    CData/*0:0*/ __PVT__unnamedblk1__DOT___product_fpCond_T;
    CData/*0:0*/ __PVT__unnamedblk1__DOT___psumReg_isInt_T_1;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aFp32_isZero_T;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bFp32_isZero_T;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_sOut;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_guardBit;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_mRounded_roundUp_T_1;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_0;
    CData/*0:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_shiftA_T;
    CData/*4:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_leadingZero;
    CData/*7:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_eOut_T_3;
    CData/*7:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___GEN_1;
    SData/*15:0*/ io_aIn;
    SData/*15:0*/ io_aOut;
    SData/*15:0*/ __PVT__wReg;
    SData/*15:0*/ __PVT__io_aOut_REG;
    SData/*15:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_aInt_T_5;
    SData/*15:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_bInt_T_5;
    SData/*8:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___product_fpProd_eAdj_T_2;
    IData/*31:0*/ io_psumIn;
    IData/*31:0*/ io_psumOut;
    IData/*31:0*/ __PVT__psumReg;
    IData/*31:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_aFp32;
    IData/*31:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_bFp32;
    IData/*22:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mNorm;
    IData/*31:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product;
    IData/*24:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA25;
    IData/*24:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB25;
    IData/*25:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mA_signed;
    IData/*25:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mB_signed;
    IData/*26:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mSum;
    IData/*25:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__psumReg_fpResult_mAbs;
    QData/*47:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT__product_fpProd_mProd;
    QData/*56:0*/ __PVT__unnamedblk1__DOT__unnamedblk2__DOT___psumReg_fpResult_guardBit_T;

    // INTERNAL VARIABLES
    VsvsimTestbench__Syms* vlSymsp;
    const char* vlNamep;

    // CONSTRUCTORS
    VsvsimTestbench_HiveWorker();
    ~VsvsimTestbench_HiveWorker();
    void ctor(VsvsimTestbench__Syms* symsp, const char* namep);
    void dtor();
    VL_UNCOPYABLE(VsvsimTestbench_HiveWorker);

    // INTERNAL METHODS
    void __Vconfigure(bool first);
};


#endif  // guard
