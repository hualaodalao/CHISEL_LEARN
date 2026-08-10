// Verilated -*- C++ -*-
// DESCRIPTION: Verilator output: Implementation of DPI export functions.

#include "VsvsimTestbench.h"
#include "VsvsimTestbench__Syms.h"
#include "verilated_dpi.h"


void VsvsimTestbench::getBitWidthImpl_clock(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_clock\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_clock");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_clock_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_clock_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_clock(const svBitVecVal* value_clock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_clock\n"); );
    // Locals
    CData/*0:0*/ value_clock__Vcvt;
    value_clock__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_clock");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_clock_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_clock_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_clock__Vcvt, value_clock + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_clock__Vcvt);
}

void VsvsimTestbench::getBitsImpl_clock(svBitVecVal* value_clock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_clock\n"); );
    // Locals
    CData/*0:0*/ value_clock__Vcvt;
    value_clock__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_clock");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_clock_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_clock_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_clock__Vcvt);
    VL_SET_SVBV_I(1, value_clock, value_clock__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_reset(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_reset\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_reset");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_reset_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_reset_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_reset(const svBitVecVal* value_reset) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_reset\n"); );
    // Locals
    CData/*0:0*/ value_reset__Vcvt;
    value_reset__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_reset");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_reset_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_reset_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_reset__Vcvt, value_reset + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_reset__Vcvt);
}

void VsvsimTestbench::getBitsImpl_reset(svBitVecVal* value_reset) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_reset\n"); );
    // Locals
    CData/*0:0*/ value_reset__Vcvt;
    value_reset__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_reset");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_reset_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_reset_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_reset__Vcvt);
    VL_SET_SVBV_I(1, value_reset, value_reset__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_rndOut(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_rndOut\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_rndOut");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_rndOut_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_rndOut_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_rndOut(svBitVecVal* value_io_rndOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_rndOut\n"); );
    // Locals
    CData/*2:0*/ value_io_rndOut__Vcvt;
    value_io_rndOut__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_rndOut");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_rndOut_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_rndOut_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_rndOut__Vcvt);
    VL_SET_SVBV_I(3, value_io_rndOut, value_io_rndOut__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_fmtOut(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_fmtOut\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_fmtOut");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtOut_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtOut_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_fmtOut(svBitVecVal* value_io_fmtOut) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_fmtOut\n"); );
    // Locals
    CData/*1:0*/ value_io_fmtOut__Vcvt;
    value_io_fmtOut__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_fmtOut");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_fmtOut_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_fmtOut_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_fmtOut__Vcvt);
    VL_SET_SVBV_I(2, value_io_fmtOut, value_io_fmtOut__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_0(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_0\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_0(svBitVecVal* value_io_validOut_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_0\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_0__Vcvt;
    value_io_validOut_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_0__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_0, value_io_validOut_0__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_1(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_1\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_1(svBitVecVal* value_io_validOut_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_1\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_1__Vcvt;
    value_io_validOut_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_1__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_1, value_io_validOut_1__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_2(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_2\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_2(svBitVecVal* value_io_validOut_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_2\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_2__Vcvt;
    value_io_validOut_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_2__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_2, value_io_validOut_2__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_3(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_3\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_3(svBitVecVal* value_io_validOut_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_3\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_3__Vcvt;
    value_io_validOut_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_3__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_3, value_io_validOut_3__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_4(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_4\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_4(svBitVecVal* value_io_validOut_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_4\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_4__Vcvt;
    value_io_validOut_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_4__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_4, value_io_validOut_4__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_5(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_5\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_5(svBitVecVal* value_io_validOut_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_5\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_5__Vcvt;
    value_io_validOut_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_5__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_5, value_io_validOut_5__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_6(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_6\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_6(svBitVecVal* value_io_validOut_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_6\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_6__Vcvt;
    value_io_validOut_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_6__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_6, value_io_validOut_6__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validOut_7(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validOut_7\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validOut_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validOut_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_validOut_7(svBitVecVal* value_io_validOut_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validOut_7\n"); );
    // Locals
    CData/*0:0*/ value_io_validOut_7__Vcvt;
    value_io_validOut_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validOut_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validOut_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validOut_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validOut_7__Vcvt);
    VL_SET_SVBV_I(1, value_io_validOut_7, value_io_validOut_7__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_loadVIn(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_loadVIn\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_loadVIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_loadVIn(const svBitVecVal* value_io_loadVIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_loadVIn\n"); );
    // Locals
    CData/*0:0*/ value_io_loadVIn__Vcvt;
    value_io_loadVIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_loadVIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_loadVIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_loadVIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_loadVIn__Vcvt, value_io_loadVIn + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_loadVIn__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_loadVIn(svBitVecVal* value_io_loadVIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_loadVIn\n"); );
    // Locals
    CData/*0:0*/ value_io_loadVIn__Vcvt;
    value_io_loadVIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_loadVIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_loadVIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_loadVIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_loadVIn__Vcvt);
    VL_SET_SVBV_I(1, value_io_loadVIn, value_io_loadVIn__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_loadHIn(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_loadHIn\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_loadHIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_loadHIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_loadHIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_loadHIn(const svBitVecVal* value_io_loadHIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_loadHIn\n"); );
    // Locals
    CData/*0:0*/ value_io_loadHIn__Vcvt;
    value_io_loadHIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_loadHIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_loadHIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_loadHIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_loadHIn__Vcvt, value_io_loadHIn + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_loadHIn__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_loadHIn(svBitVecVal* value_io_loadHIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_loadHIn\n"); );
    // Locals
    CData/*0:0*/ value_io_loadHIn__Vcvt;
    value_io_loadHIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_loadHIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_loadHIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_loadHIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_loadHIn__Vcvt);
    VL_SET_SVBV_I(1, value_io_loadHIn, value_io_loadHIn__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_rndIn(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_rndIn\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_rndIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_rndIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_rndIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_rndIn(const svBitVecVal* value_io_rndIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_rndIn\n"); );
    // Locals
    CData/*2:0*/ value_io_rndIn__Vcvt;
    value_io_rndIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_rndIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_rndIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_rndIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(3, value_io_rndIn__Vcvt, value_io_rndIn + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_rndIn__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_rndIn(svBitVecVal* value_io_rndIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_rndIn\n"); );
    // Locals
    CData/*2:0*/ value_io_rndIn__Vcvt;
    value_io_rndIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_rndIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_rndIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_rndIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_rndIn__Vcvt);
    VL_SET_SVBV_I(3, value_io_rndIn, value_io_rndIn__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_fmtIn(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_fmtIn\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_fmtIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_fmtIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_fmtIn(const svBitVecVal* value_io_fmtIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_fmtIn\n"); );
    // Locals
    CData/*1:0*/ value_io_fmtIn__Vcvt;
    value_io_fmtIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_fmtIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_fmtIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_fmtIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(2, value_io_fmtIn__Vcvt, value_io_fmtIn + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_fmtIn__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_fmtIn(svBitVecVal* value_io_fmtIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_fmtIn\n"); );
    // Locals
    CData/*1:0*/ value_io_fmtIn__Vcvt;
    value_io_fmtIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_fmtIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_fmtIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_fmtIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_fmtIn__Vcvt);
    VL_SET_SVBV_I(2, value_io_fmtIn, value_io_fmtIn__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_validIn(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_validIn\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_validIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_validIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_validIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_validIn(const svBitVecVal* value_io_validIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_validIn\n"); );
    // Locals
    CData/*0:0*/ value_io_validIn__Vcvt;
    value_io_validIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_validIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_validIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_validIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_validIn__Vcvt, value_io_validIn + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validIn__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_validIn(svBitVecVal* value_io_validIn) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_validIn\n"); );
    // Locals
    CData/*0:0*/ value_io_validIn__Vcvt;
    value_io_validIn__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_validIn");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_validIn_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_validIn_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_validIn__Vcvt);
    VL_SET_SVBV_I(1, value_io_validIn, value_io_validIn__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_loadVLock(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_loadVLock\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_loadVLock");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVLock_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_loadVLock_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_loadVLock(const svBitVecVal* value_io_loadVLock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_loadVLock\n"); );
    // Locals
    CData/*0:0*/ value_io_loadVLock__Vcvt;
    value_io_loadVLock__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_loadVLock");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_loadVLock_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_loadVLock_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_loadVLock__Vcvt, value_io_loadVLock + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_loadVLock__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_loadVLock(svBitVecVal* value_io_loadVLock) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_loadVLock\n"); );
    // Locals
    CData/*0:0*/ value_io_loadVLock__Vcvt;
    value_io_loadVLock__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_loadVLock");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_loadVLock_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_loadVLock_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_loadVLock__Vcvt);
    VL_SET_SVBV_I(1, value_io_loadVLock, value_io_loadVLock__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_clear(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_clear\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_clear");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_clear_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_clear_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_clear(const svBitVecVal* value_io_clear) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_clear\n"); );
    // Locals
    CData/*0:0*/ value_io_clear__Vcvt;
    value_io_clear__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_clear");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_clear_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_clear_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_C_SVBV(1, value_io_clear__Vcvt, value_io_clear + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_clear__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_clear(svBitVecVal* value_io_clear) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_clear\n"); );
    // Locals
    CData/*0:0*/ value_io_clear__Vcvt;
    value_io_clear__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_clear");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_clear_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_clear_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_clear__Vcvt);
    VL_SET_SVBV_I(1, value_io_clear, value_io_clear__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_0(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_0\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_0(svBitVecVal* value_io_cOut_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_0\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_0__Vcvt;
    value_io_cOut_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_0__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_0, value_io_cOut_0__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_1(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_1\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_1(svBitVecVal* value_io_cOut_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_1\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_1__Vcvt;
    value_io_cOut_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_1__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_1, value_io_cOut_1__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_2(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_2\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_2(svBitVecVal* value_io_cOut_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_2\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_2__Vcvt;
    value_io_cOut_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_2__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_2, value_io_cOut_2__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_3(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_3\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_3(svBitVecVal* value_io_cOut_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_3\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_3__Vcvt;
    value_io_cOut_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_3__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_3, value_io_cOut_3__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_4(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_4\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_4(svBitVecVal* value_io_cOut_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_4\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_4__Vcvt;
    value_io_cOut_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_4__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_4, value_io_cOut_4__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_5(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_5\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_5(svBitVecVal* value_io_cOut_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_5\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_5__Vcvt;
    value_io_cOut_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_5__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_5, value_io_cOut_5__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_6(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_6\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_6(svBitVecVal* value_io_cOut_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_6\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_6__Vcvt;
    value_io_cOut_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_6__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_6, value_io_cOut_6__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_cOut_7(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_cOut_7\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_cOut_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_cOut_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_cOut_7(svBitVecVal* value_io_cOut_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_cOut_7\n"); );
    // Locals
    IData/*31:0*/ value_io_cOut_7__Vcvt;
    value_io_cOut_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_cOut_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_cOut_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_cOut_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_cOut_7__Vcvt);
    VL_SET_SVBV_I(32, value_io_cOut_7, value_io_cOut_7__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_0(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_0\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_0(const svBitVecVal* value_io_psumIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_0\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_0__Vcvt;
    value_io_psumIn_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_0__Vcvt, value_io_psumIn_0 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_0__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_0(svBitVecVal* value_io_psumIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_0\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_0__Vcvt;
    value_io_psumIn_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_0__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_0, value_io_psumIn_0__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_1(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_1\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_1(const svBitVecVal* value_io_psumIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_1\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_1__Vcvt;
    value_io_psumIn_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_1__Vcvt, value_io_psumIn_1 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_1__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_1(svBitVecVal* value_io_psumIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_1\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_1__Vcvt;
    value_io_psumIn_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_1__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_1, value_io_psumIn_1__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_2(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_2\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_2(const svBitVecVal* value_io_psumIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_2\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_2__Vcvt;
    value_io_psumIn_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_2__Vcvt, value_io_psumIn_2 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_2__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_2(svBitVecVal* value_io_psumIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_2\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_2__Vcvt;
    value_io_psumIn_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_2__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_2, value_io_psumIn_2__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_3(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_3\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_3(const svBitVecVal* value_io_psumIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_3\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_3__Vcvt;
    value_io_psumIn_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_3__Vcvt, value_io_psumIn_3 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_3__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_3(svBitVecVal* value_io_psumIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_3\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_3__Vcvt;
    value_io_psumIn_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_3__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_3, value_io_psumIn_3__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_4(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_4\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_4(const svBitVecVal* value_io_psumIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_4\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_4__Vcvt;
    value_io_psumIn_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_4__Vcvt, value_io_psumIn_4 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_4__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_4(svBitVecVal* value_io_psumIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_4\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_4__Vcvt;
    value_io_psumIn_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_4__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_4, value_io_psumIn_4__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_5(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_5\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_5(const svBitVecVal* value_io_psumIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_5\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_5__Vcvt;
    value_io_psumIn_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_5__Vcvt, value_io_psumIn_5 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_5__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_5(svBitVecVal* value_io_psumIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_5\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_5__Vcvt;
    value_io_psumIn_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_5__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_5, value_io_psumIn_5__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_6(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_6\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_6(const svBitVecVal* value_io_psumIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_6\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_6__Vcvt;
    value_io_psumIn_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_6__Vcvt, value_io_psumIn_6 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_6__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_6(svBitVecVal* value_io_psumIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_6\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_6__Vcvt;
    value_io_psumIn_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_6__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_6, value_io_psumIn_6__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_psumIn_7(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_psumIn_7\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_psumIn_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_psumIn_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_psumIn_7(const svBitVecVal* value_io_psumIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_psumIn_7\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_7__Vcvt;
    value_io_psumIn_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_psumIn_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_psumIn_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_I_SVBV(32, value_io_psumIn_7__Vcvt, value_io_psumIn_7 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_7__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_psumIn_7(svBitVecVal* value_io_psumIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_psumIn_7\n"); );
    // Locals
    IData/*31:0*/ value_io_psumIn_7__Vcvt;
    value_io_psumIn_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_psumIn_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_psumIn_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_psumIn_7__Vcvt);
    VL_SET_SVBV_I(32, value_io_psumIn_7, value_io_psumIn_7__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_0(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_0\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_0(svBitVecVal* value_io_aOut_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_0\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_0__Vcvt;
    value_io_aOut_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_0__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_0, value_io_aOut_0__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_1(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_1\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_1(svBitVecVal* value_io_aOut_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_1\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_1__Vcvt;
    value_io_aOut_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_1__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_1, value_io_aOut_1__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_2(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_2\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_2(svBitVecVal* value_io_aOut_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_2\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_2__Vcvt;
    value_io_aOut_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_2__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_2, value_io_aOut_2__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_3(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_3\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_3(svBitVecVal* value_io_aOut_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_3\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_3__Vcvt;
    value_io_aOut_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_3__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_3, value_io_aOut_3__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_4(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_4\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_4(svBitVecVal* value_io_aOut_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_4\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_4__Vcvt;
    value_io_aOut_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_4__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_4, value_io_aOut_4__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_5(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_5\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_5(svBitVecVal* value_io_aOut_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_5\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_5__Vcvt;
    value_io_aOut_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_5__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_5, value_io_aOut_5__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_6(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_6\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_6(svBitVecVal* value_io_aOut_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_6\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_6__Vcvt;
    value_io_aOut_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_6__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_6, value_io_aOut_6__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aOut_7(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aOut_7\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aOut_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aOut_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::getBitsImpl_io_aOut_7(svBitVecVal* value_io_aOut_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aOut_7\n"); );
    // Locals
    SData/*15:0*/ value_io_aOut_7__Vcvt;
    value_io_aOut_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aOut_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aOut_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aOut_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aOut_7__Vcvt);
    VL_SET_SVBV_I(16, value_io_aOut_7, value_io_aOut_7__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_0(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_0\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_0(const svBitVecVal* value_io_aIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_0\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_0__Vcvt;
    value_io_aIn_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_0__Vcvt, value_io_aIn_0 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_0__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_0(svBitVecVal* value_io_aIn_0) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_0\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_0__Vcvt;
    value_io_aIn_0__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_0");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_0_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_0_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_0__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_0, value_io_aIn_0__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_1(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_1\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_1(const svBitVecVal* value_io_aIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_1\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_1__Vcvt;
    value_io_aIn_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_1__Vcvt, value_io_aIn_1 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_1__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_1(svBitVecVal* value_io_aIn_1) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_1\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_1__Vcvt;
    value_io_aIn_1__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_1");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_1_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_1_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_1__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_1, value_io_aIn_1__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_2(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_2\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_2(const svBitVecVal* value_io_aIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_2\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_2__Vcvt;
    value_io_aIn_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_2__Vcvt, value_io_aIn_2 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_2__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_2(svBitVecVal* value_io_aIn_2) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_2\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_2__Vcvt;
    value_io_aIn_2__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_2");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_2_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_2_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_2__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_2, value_io_aIn_2__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_3(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_3\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_3(const svBitVecVal* value_io_aIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_3\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_3__Vcvt;
    value_io_aIn_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_3__Vcvt, value_io_aIn_3 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_3__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_3(svBitVecVal* value_io_aIn_3) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_3\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_3__Vcvt;
    value_io_aIn_3__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_3");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_3_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_3_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_3__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_3, value_io_aIn_3__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_4(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_4\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_4(const svBitVecVal* value_io_aIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_4\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_4__Vcvt;
    value_io_aIn_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_4__Vcvt, value_io_aIn_4 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_4__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_4(svBitVecVal* value_io_aIn_4) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_4\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_4__Vcvt;
    value_io_aIn_4__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_4");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_4_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_4_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_4__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_4, value_io_aIn_4__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_5(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_5\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_5(const svBitVecVal* value_io_aIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_5\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_5__Vcvt;
    value_io_aIn_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_5__Vcvt, value_io_aIn_5 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_5__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_5(svBitVecVal* value_io_aIn_5) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_5\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_5__Vcvt;
    value_io_aIn_5__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_5");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_5_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_5_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_5__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_5, value_io_aIn_5__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_6(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_6\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_6(const svBitVecVal* value_io_aIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_6\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_6__Vcvt;
    value_io_aIn_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_6__Vcvt, value_io_aIn_6 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_6__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_6(svBitVecVal* value_io_aIn_6) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_6\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_6__Vcvt;
    value_io_aIn_6__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_6");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_6_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_6_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_6__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_6, value_io_aIn_6__Vcvt);
}

void VsvsimTestbench::getBitWidthImpl_io_aIn_7(int* value) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitWidthImpl_io_aIn_7\n"); );
    // Locals
    IData/*31:0*/ value__Vcvt;
    value__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitWidthImpl_io_aIn_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitWidthImpl_io_aIn_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value__Vcvt);
    *value = value__Vcvt;
}

void VsvsimTestbench::setBitsImpl_io_aIn_7(const svBitVecVal* value_io_aIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::setBitsImpl_io_aIn_7\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_7__Vcvt;
    value_io_aIn_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("setBitsImpl_io_aIn_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_setBitsImpl_io_aIn_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_setBitsImpl_io_aIn_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    VL_SET_S_SVBV(16, value_io_aIn_7__Vcvt, value_io_aIn_7 + 0);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_7__Vcvt);
}

void VsvsimTestbench::getBitsImpl_io_aIn_7(svBitVecVal* value_io_aIn_7) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::getBitsImpl_io_aIn_7\n"); );
    // Locals
    SData/*15:0*/ value_io_aIn_7__Vcvt;
    value_io_aIn_7__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("getBitsImpl_io_aIn_7");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_getBitsImpl_io_aIn_7_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_getBitsImpl_io_aIn_7_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), value_io_aIn_7__Vcvt);
    VL_SET_SVBV_I(16, value_io_aIn_7, value_io_aIn_7__Vcvt);
}

void VsvsimTestbench::simulation_initializeTrace(const char* traceFilePath) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::simulation_initializeTrace\n"); );
    // Locals
    static thread_local std::string traceFilePath__Vcvt;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("simulation_initializeTrace");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_simulation_initializeTrace_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_simulation_initializeTrace_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    traceFilePath__Vcvt = VL_CVT_N_CSTR(traceFilePath);
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), traceFilePath__Vcvt);
}

void VsvsimTestbench::simulation_enableTrace(int* success) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::simulation_enableTrace\n"); );
    // Locals
    IData/*31:0*/ success__Vcvt;
    success__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("simulation_enableTrace");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_simulation_enableTrace_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_simulation_enableTrace_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), success__Vcvt);
    *success = success__Vcvt;
}

void VsvsimTestbench::simulation_disableTrace(int* success) {
    VL_DEBUG_IF(VL_DBG_MSGF("+    VsvsimTestbench___024root::simulation_disableTrace\n"); );
    // Locals
    IData/*31:0*/ success__Vcvt;
    success__Vcvt = 0;
    // Body
    static int __Vfuncnum = -1;
    if (VL_UNLIKELY(__Vfuncnum == -1)) {
        __Vfuncnum = Verilated::exportFuncNum("simulation_disableTrace");
    }
    const VerilatedScope* const __Vscopep = Verilated::dpiScope();
    VsvsimTestbench__Vcb_simulation_disableTrace_t __Vcb = reinterpret_cast<VsvsimTestbench__Vcb_simulation_disableTrace_t>(VerilatedScope::exportFind(__Vscopep, __Vfuncnum));
    (*__Vcb)((VsvsimTestbench__Syms*)(__Vscopep->symsp()), success__Vcvt);
    *success = success__Vcvt;
}
